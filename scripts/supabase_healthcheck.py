"""Read-only Supabase RPC check. Standard library only; never log remote bodies."""

import json
import os
import re
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.request import HTTPRedirectHandler, Request, build_opener


class HealthcheckError(Exception):
    """Only fixed, non-sensitive diagnostic messages belong in this exception."""


class NoRedirects(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # Never forward an API key to a redirect destination.
        return None


def configuration(environ):
    url = environ.get("SUPABASE_URL", "").strip().rstrip("/")
    key = environ.get("SUPABASE_PUBLISHABLE_KEY", "").strip()
    if not re.fullmatch(r"https://[a-z0-9]{20}\.supabase\.co", url):
        raise HealthcheckError("Configura SUPABASE_URL con l'URL HTTPS del progetto Supabase (non la dashboard).")
    if not re.fullmatch(r"sb_publishable_[A-Za-z0-9_-]+", key):
        raise HealthcheckError("Configura SUPABASE_PUBLISHABLE_KEY: serve una chiave sb_publishable_, non password, JWT o service_role.")
    return url, key


def check(url, key, *, opener=None, sleep=time.sleep):
    opener = opener or build_opener(NoRedirects())
    request = Request(
        url + "/rest/v1/rpc/pokerole_healthcheck",
        method="GET",
        headers={"apikey": key, "Accept": "application/json", "Cache-Control": "no-cache"},
    )
    for attempt in range(3):
        try:
            with opener.open(request, timeout=20) as response:
                if response.status != 200:
                    raise HealthcheckError("Risposta HTTP inattesa dal controllo database.")
                body = response.read(1025)
                if len(body) > 1024:
                    raise HealthcheckError("Risposta del controllo troppo grande: verificare la funzione SQL.")
                if response.headers.get_content_type() != "application/json":
                    raise HealthcheckError("Il controllo non ha restituito JSON.")
                try:
                    result = json.loads(body)
                except (ValueError, UnicodeError):
                    raise HealthcheckError("Risposta JSON non valida.") from None
                if result is not True:
                    raise HealthcheckError("Il database non ha restituito il valore atteso true.")
                return
        except HTTPError as error:
            status = error.code
            error.close()
            if status in (401, 403):
                raise HealthcheckError("HTTP 401/403: verificare chiave, progetto e permesso EXECUTE sulla sola funzione di controllo.") from None
            if status == 404:
                raise HealthcheckError("HTTP 404: verificare URL, Data API e installazione di pokerole_healthcheck.") from None
            if status not in (408, 429) and not 500 <= status <= 599:
                raise HealthcheckError("Richiesta rifiutata o reindirizzata; nessuna credenziale inoltrata.") from None
            reason = "Errore HTTP temporaneo dopo tre tentativi. Controllare lo stato del progetto."
        except (URLError, OSError):
            reason = "Connessione HTTPS non riuscita dopo tre tentativi. Controllare rete e stato del progetto."
        if attempt < 2:
            sleep((2, 5)[attempt])
    raise HealthcheckError(reason)


def main():
    try:
        check(*configuration(os.environ))
    except HealthcheckError as error:
        print(f"::error::{error}", file=sys.stderr)
        return 1
    except Exception:
        # Avoid a traceback exposing request details in public Actions logs.
        print("::error::Errore locale inatteso nel controllo; nessun dettaglio remoto pubblicato.", file=sys.stderr)
        return 1
    print("OK: Data API e database rispondono. Nessuna scheda letta o modificata.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
