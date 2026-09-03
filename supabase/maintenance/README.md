# Controllo esterno Supabase

Il workflow `Supabase healthcheck` controlla il percorso GitHub -> HTTPS -> Data API
-> PostgreSQL tre volte al giorno: **05:23, 13:23 e 21:23 UTC**.
In Italia: 06:23/14:23/22:23 in inverno, 07:23/15:23/23:23 in estate.
Non dipende dal PC acceso e non richiede un nuovo APK.

## Attivazione (una volta)

1. Nel SQL Editor del progetto Android `raisslpznoscphdxluwa`, eseguire solo
   [`healthcheck_setup.sql`](healthcheck_setup.sql). Il risultato finale deve essere `true`.
   Questo e' uno script di installazione manuale versionato, non una migrazione
   applicata automaticamente dalla CLI. Non eseguire `supabase db push` su tutta
   la cartella delle vecchie migrazioni e non rilanciare gli import.
2. Pubblicare con commit e push i nuovi file sul branch predefinito del **proprio fork**.
3. Nel proprio repository GitHub aprire **Settings -> Secrets and variables -> Actions**:
   - scheda **Variables**, creare `SUPABASE_URL` con
     `https://raisslpznoscphdxluwa.supabase.co`;
   - scheda **Secrets**, creare `SUPABASE_PUBLISHABLE_KEY` con la chiave
     `sb_publishable_...` del medesimo progetto. Non usare `sb_secret_`, `service_role`,
     token personali, access token Google o password del database.
4. Aprire **Actions**. Essendo un fork, potrebbe essere necessario abilitare i workflow.
   Selezionare **Supabase healthcheck -> Run workflow** sul branch predefinito.
5. Controllare che il primo run sia verde e mostri `OK: Data API e database rispondono`.
   Il controllo non e' verificato sul servizio reale fino a questo passaggio.
6. Abilitare nelle proprie preferenze GitHub le notifiche Actions per i workflow falliti.
   Il workflow segnala i guasti come run falliti; non invia email autonomamente.

## Sicurezza e limiti

- La funzione pubblicamente richiamabile restituisce solo il booleano `true`.
  Esegue SQL in PostgreSQL, ma non legge cataloghi, schede, utenti o storico.
  E' `SECURITY INVOKER`, non concede accesso a tabelle e non cambia le policy esistenti.
- La chiave publishable non e' un account DM. E' inserita tra i Secrets per non
  copiarla nel codice/log. Nessuna intestazione Authorization o credenziale privilegiata.
- Solo GET via HTTPS, nessun redirect, timeout di 20 secondi, massimo tre tentativi
  per problemi temporanei. Corpi di risposta e dettagli degli errori remoti non sono stampati.
- Un HTTP 200 generico non basta: deve arrivare JSON con il booleano `true`.
  Il test verifica la connettivita', non login Google, RLS delle schede, integrita'
  dei cataloghi o retention. La retention 5 trainer / 2 pokemon resta gestita dai trigger.
- Non e' un backup e non riattiva un progetto gia' sospeso. Conservare esportazioni
  private di dati e immagini: non caricare i backup negli artifact di un repo pubblico.
- **Non garantisce l'assenza di sospensioni nel piano Free.** Supabase valuta l'attivita'
  sul database; non promette una soglia precisa per questo tipo di controllo.
  Monitorare anche le email di Supabase; se sospeso, usare Resume project dalla dashboard.
- GitHub puo' ritardare/saltare le esecuzioni. Nei repository pubblici disattiva le
  schedulazioni dopo 60 giorni senza attivita' nel repository: controllare periodicamente
  Actions e riabilitare il workflow se necessario. Non genera commit artificiali.
- Usa un runner Linux standard (gratuito nei repo pubblici; quota inclusa per quelli
  privati), senza artifact, cache o servizi a pagamento. Nessun costo aggiunto su Supabase.

## Diagnosi

- Configurazione mancante: controllare nome e scheda corretti (URL in Variables, chiave in Secrets).
- HTTP 401/403: chiave sb_publishable del progetto corretto e permesso EXECUTE
  sulla sola funzione `pokerole_healthcheck`. Non aprire le tabelle a `anon`.
- HTTP 404: controllare URL, Data API attiva e funzione installata nello schema `public`
  esposto dall'app. Dopo l'installazione attendere l'aggiornamento della cache e riprovare.
- Timeout/5xx: controllare stato Supabase, eventuale sospensione e avviare nuovamente il workflow.
- Risposta diversa da `true`: non considerarla un successo; verificare la funzione.

## Disattivazione e test locali

In Actions selezionare il workflow e **Disable workflow** per fermare i controlli.
Non occorre toccare schede o database. Dopo averlo disabilitato, la sola funzione puo'
essere rimossa con `drop function public.pokerole_healthcheck();` se non piu' desiderata.

I test dello script non accedono alla rete e non richiedono credenziali:

```sh
python3 -B -m unittest discover -s scripts -p 'test_supabase_healthcheck.py' -v
```

## Riferimenti

- [Supabase: project pausing](https://supabase.com/docs/guides/platform/free-project-pausing)
- [Supabase: funzioni e permessi](https://supabase.com/docs/guides/database/functions)
- [GitHub: schedulazioni e limite di inattivita'](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#schedule)
- [GitHub: costi Actions](https://docs.github.com/en/billing/concepts/product-billing/github-actions)
