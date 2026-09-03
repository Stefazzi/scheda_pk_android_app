# Web app originale — archivio legacy

Questa cartella raccoglie la versione web da cui deriva l'app Android, originariamente
presente nella radice del repository. I file sono stati spostati senza modificarne
il contenuto o eliminare le vecchie schede.

- `index.html`: pagina iniziale originale.
- `Tomino.html`: scheda HTML originale.
- `characters/`: schede e modelli degli allenatori.
- `pokemon/`: schede e modelli dei Pokémon.
- `js/`, `css/`, `img/`: script, stili e immagini.
- `json/`: cataloghi, dati e riferimenti usati dalla web app.

Il progetto corrente si trova in [`../android-app/`](../android-app/README.md);
il suo database e i test si trovano in [`../supabase/`](../supabase/).
Questa cartella non partecipa alla compilazione dell'APK.

## Consultazione e collegamenti

La struttura relativa è rimasta invariata, quindi i riferimenti interni fra i file
spostati mantengono la stessa destinazione. Per consultare la versione web con un
server HTTP locale, usare questa cartella come directory pubblicata e aprire
`index.html`; alcune funzioni richiedono il caricamento di JSON e servizi esterni.

Non sono stati modificati gli URL assoluti presenti nei file: la pagina iniziale
contiene ancora collegamenti al sito originale `pagliaa.github.io/schedapk`.
Eventuali collegamenti o funzioni già non disponibili nella versione originale
non vengono riparati da questo spostamento.

Questo è un archivio del codice, **non una sandbox del database**: il JavaScript
mantiene i riferimenti al backend originale. Consultarne la configurazione prima
di usare azioni di salvataggio; non è stato eseguito alcun test di scrittura remoto.

Se il fork viene pubblicato con GitHub Pages dalla radice del repository, la pagina
iniziale si trova ora sotto `legacy-web/index.html`. Non è stato aggiunto un redirect
nella radice né modificata la configurazione di pubblicazione su GitHub.

Origine: [Pagliaa/schedapk](https://github.com/Pagliaa/schedapk).
