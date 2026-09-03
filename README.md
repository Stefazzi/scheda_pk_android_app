# Pokérole Sheets

Applicazione Android per gestire le schede di allenatori e Pokémon durante una campagna Pokérole.

Il repository deriva dal progetto web originale [Pagliaa/schedapk](https://github.com/Pagliaa/schedapk). La vecchia web app è conservata in [`legacy-web/`](legacy-web/README.md), separata dal progetto Android e dai suoi script Supabase.

Per lavorare sull'app attuale aprire [`android-app/`](android-app/README.md) in Android Studio. La cartella `legacy-web` non è necessaria per compilare l'APK.

## Funzionalità

- Accesso con account Google tramite Supabase Auth.
- Ruoli separati per Dungeon Master e giocatori.
- Il DM può visualizzare e modificare tutte le schede.
- Ogni giocatore può accedere solamente al proprio allenatore e ai relativi Pokémon.
- Associazione iniziale del giocatore tramite codice fornito dal DM.
- Creazione e cattura di nuovi Pokémon.
- Catalogo community Pokérole 3.0 da Supabase, con cache locale: specie/forme, valori base, limiti e mosse per Rank.
- Pokédex consultabile con ricerca, filtro per tipo e dettagli di specie, evoluzioni e mosse.
- Ricerca delle schede per il DM e Pokémon ordinati per allenatore, poi per nome.
- Cattura con ricerca della specie, Rank manuale (predefinito Starter) e scelta delle mosse.
- Per le catture del DM, selezione dell'allenatore dalle schede Supabase; per i player, associazione automatica al proprio allenatore.
- Evoluzione guidata dalla scheda Pokémon, con scelta della destinazione e conservazione dei dati individuali.
- Caratteristiche in inglese e limiti distinti per allenatori, Pokémon, Social Attributes e Skills.
- Gestione della squadra e degli slot disponibili.
- Modifica delle schede direttamente dall’app.
- Reset rapido di HP e Will ai rispettivi valori massimi; Physical Defense e Special Defense restano indipendenti e invariate.
- Conservazione dei dati completi in formato JSONB.
- Backup delle versioni precedenti delle schede.
- Sprite automatici dei Pokémon.
- Colori delle mosse basati sul relativo tipo.
- Box dei dadi Accuracy / Damage / Clash dalla scheda Pokémon e promemoria Will per gli effetti curativi previsti.
- Avvisi ferite a metà HP e a 1 HP: penalità ai successi, non ai dadi.
- Parametri influenzati dagli strumenti/accessori selezionabili dal DM, evidenziati in azzurro sulla scheda.
- Ritratto personalizzato dell’allenatore caricato dalla galleria.
- Immagini private protette tramite Supabase Storage.
- Calcoli automatici di HP/Will, difese, Initiative ed Evasion con le formule della campagna.
- Ricerca Ability/Nature, flag Shiny/Alpha, sprite regionali e mega e sezioni richiudibili.
- Menu Azioni e rilascio Pokémon con conferma, storico e aggiornamento della squadra.
- Catalogo oggetti con ricerca, sprite disponibili e regole modificabili dal DM.
- Creazione di oggetti custom del DM e personalizzazione dei singoli slot della borsa.
- Inventario separato per allenatore, con quantità e note, senza conversioni automatiche dei testi legacy.

## Tecnologie

- Kotlin
- Jetpack Compose
- Supabase Authentication
- PostgreSQL e Row Level Security
- Supabase Storage
- Google OAuth
- PokéAPI per gli sprite dei Pokémon

L’applicazione richiede Android 8.0 o successivo.

## Installazione dell’APK

L’ultima versione dell’app può essere scaricata dalla sezione **Releases** del repository.

Per installarla potrebbe essere necessario autorizzare temporaneamente l’installazione di applicazioni provenienti da fonti esterne sul dispositivo Android.

Gli aggiornamenti devono essere firmati con la stessa chiave utilizzata per la prima release.

## Configurazione per lo sviluppo

1. Clonare il repository.
2. Aprire la cartella `android-app` con Android Studio.
3. Copiare `android-app/local.properties.example` come `android-app/local.properties`.
4. Inserire nel file:

```properties
SUPABASE_URL=https://PROJECT_REF.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_...
```

5. Sincronizzare il progetto con Gradle.
6. Avviare l’app su un emulatore o dispositivo Android.

Non inserire mai nel repository:

- password del database;
- chiavi `service_role` o secret key;
- password del keystore;
- file `.jks` o `.keystore`;
- file `local.properties`.

La chiave pubblica di Supabase non permette di superare le policy RLS configurate nel database.

## Configurazione del database

Gli script SQL necessari si trovano in `supabase/migrations`:

1. `01_create_android_schema.sql`
2. `02_prepare_legacy_import.sql`
3. `03_populate_selected_sheets.sql`
4. `04_configure_portrait_storage.sql`
5. `05_sheet_actions.sql` (v0.9.0: Libera Pokémon e immagini alternative Pokémon)
6. `06_item_catalog_inventory.sql` (v0.10.0: oggetti, inventario, permessi e funzioni DM/player)
7. `07_seed_item_catalog.sql` (v0.10.0: 236 oggetti community con sprite disponibili)
8. `08_add_item_sprites.sql` (v0.10.2: 8 immagini mancanti, senza modificare gli effetti)
9. `09_configure_item_image_storage.sql` (bucket pubblico dedicato e campo `custom_image_path`)
10. `10_edit_custom_item_images.sql` (v0.10.3: caricamento immagini DM, cambio sprite degli oggetti custom e revisioni)
11. `11_item_affected_parameters.sql` (v0.11.0: parametri influenzati dagli oggetti, modificabili solo dal DM)
12. `12_seed_standard_item_parameters.sql` (associazioni iniziali per 10 strumenti standard; nessun nuovo APK richiesto dalla v0.11.0)

Per aggiornare dalla v0.9.0, eseguire nel SQL Editor solo **06, poi 07**.
Se non è ancora installato lo script 05, installarlo per le precedenti azioni Pokémon.
Non ripetere gli import legacy. Gli script 06/07 non modificano le schede o lo storico
esistenti; ripetere 07 non sovrascrive gli oggetti o le personalizzazioni già presenti.
L'inventario nuovo è separato dai JSONB e **non è incluso in `sheet_versions`**:
un ripristino della scheda non ripristina gli slot nuovi. Dettagli in `android-app/README.md`.

Gli script creano le tabelle dell’app Android, configurano autenticazione e permessi, importano le schede selezionate e preparano il bucket privato `pokerole-media`.

Per il catalogo della versione Android 0.7.0 devono essere già importate anche le tabelle
`catalog_sources`, `catalog_ranks`, `catalog_pokemon`, `catalog_moves`, `catalog_learnsets`
del pacchetto SQL Pokérole 3.0 preparato separatamente. Il catalogo completo non è incorporato
nel repository: l'app lo scarica dopo il login e conserva l'ultima copia valida sul dispositivo.
Vedere `android-app/README.md` per utilizzo, limiti ed evoluzioni.

Prima di eseguire gli script in un nuovo progetto Supabase è consigliato controllarne il contenuto e creare un backup dei dati esistenti.

## Struttura del repository

```text
android-app/           Applicazione Android nativa
supabase/              Database e test dell'app Android
  migrations/          Schema, policy e configurazione Supabase
  tests/               Test locali delle query e dei permessi
legacy-web/            Archivio della vecchia web app
  index.html           Pagina iniziale originale
  Tomino.html          Scheda HTML originale
  characters/          Schede HTML legacy degli allenatori
  pokemon/             Schede HTML legacy dei Pokémon
  js/, css/, img/      Script, stili e immagini originali
  json/                Cataloghi e dati originali
```

Le risorse `android-app/app/src/main/assets/legacy/` restano nel progetto Android:
non sono un archivio da rimuovere, ma dati ancora letti dall'app.

Lo spostamento della web app conserva i file e la loro struttura interna, senza
cambiare collegamenti esterni o configurazione del vecchio backend. Se questa
copia viene pubblicata con GitHub Pages dalla radice del repository, il suo ingresso
è ora `legacy-web/index.html`: i vecchi URL nella radice non vengono reindirizzati.
Il progetto GitHub originale dell'amico non viene modificato.

## Sicurezza e ruoli

L’accesso ai dati è controllato tramite le policy Row Level Security di Supabase:

- il DM può gestire tutte le schede;
- un giocatore può accedere solamente al proprio allenatore;
- i Pokémon sono visibili e modificabili in base al proprietario dell’allenatore;
- i ritratti vengono conservati in un bucket privato e mostrati tramite URL firmati temporanei.

## Stato del progetto

Versione del codice: **0.11.2**, versionCode **20** (generare e collaudare il nuovo APK prima della distribuzione).

Il progetto è attualmente destinato all’utilizzo privato della campagna ed è ancora in fase di sviluppo e collaudo.
