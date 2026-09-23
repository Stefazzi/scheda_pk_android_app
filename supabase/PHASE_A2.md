# Phase A2 — revisioni, salvataggi atomici, squadra e immagini oggetto

Phase A2 è stata applicata al progetto Supabase live `raisslpznoscphdxluwa` il
2026-09-23. La versione Android associata è `versionName 1.0`, `versionCode 21`.
Non sono stati aggiornati Gradle, AGP, Kotlin, SDK o dipendenze.

## Stato iniziale e precondizioni

Il checkout conteneva le modifiche Phase A1 non registrate nel commit corrente,
ma la migration A1 risultava applicata sul database. Sono state conservate senza
replay o rollback. Prima di A2 il live conteneva 35 allenatori, 36 Pokémon, zero
slot null, fuori intervallo o duplicati, zero colonne `revision` e nessun JSON
`pokemon_team` malformato. I salvataggi Android erano update diretti; la cattura
sceglieva lo slot con lettura e scrittura separate; l'app riscriveva anche il JSON
della squadra. L'infrastruttura immagini oggetto e le relative policy DM esistevano
già ed è stata riutilizzata.

## Migrazione e oggetti database

La migration applicata è
`migrations/20260923212131_phase_a2_revision_atomic_team_integrity.sql`, che
corrisponde alla versione registrata nella cronologia live.
Il follow-up `migrations/20260923213815_phase_a2_trainer_team_rpc_guard.sql`
impedisce a una bozza allenatore aperta prima di un riordino di riscrivere la copia
JSON: la RPC rigenera sempre `pokemon_team` dagli slot relazionali.
Ha aggiunto `trainers.revision`, `pokemon.revision`, l'indice univoco parziale
`pokemon_trainer_active_team_slot_key`, i trigger di inizializzazione e sync e le
RPC `save_trainer_sheet`, `save_pokemon_sheet`, `reorder_pokemon_team`. Ha inoltre
aggiornato `backup_sheet_row`, `set_updated_at` e `release_pokemon`.

Le RPC sono `SECURITY INVOKER`, richiedono un JWT autenticato e lasciano le RLS
come autorità. Un update riesce solo con la revisione attesa e restituisce la riga
con la revisione nuova; una revisione obsoleta produce SQLSTATE `40001`. Le
scritture dirette legacy restano concesse e incrementano comunque la revisione.
Questa scelta mantiene il vecchio APK funzionante, ma un client legacy che non
invia la revisione non può ricevere la protezione ottimistica completa.

`pokemon.team_slot` è la rappresentazione autorevole. La cattura blocca la riga
allenatore e assegna il primo slot libero 1–6 nella stessa transazione; a squadra
piena conserva la precedente semantica `NULL`. Il riordino accetta l'intero elenco
attivo, verifica che non sia cambiato e scambia gli slot in una sola transazione
senza violazioni univoche intermedie. Pokémon senza slot non vengono reinterpretati.

Il trigger Pokémon aggiorna `trainers.sheet_data.pokemon_team` come copia legacy.
Questa scrittura tecnica non incrementa la revisione allenatore e non crea una
versione storica. La fase transitoria del riordino non crea storico né revisioni;
la modifica finale incrementa una sola volta ogni Pokémon realmente spostato.
Due schede esistenti hanno ancora una forma legacy a tre elementi, con valori
coerenti: non sono state riscritte in massa e passeranno a sei slot al primo cambio
relazionale della squadra.

## Android e immagini custom

DTO, stato e repository trasportano `revision`. I salvataggi usano le RPC atomiche;
la selezione client dello slot e il doppio aggiornamento del JSON sono stati
rimossi. Dopo un conflitto le modifiche locali restano visibili e l'utente può
scegliere esplicitamente `Azioni → Ricarica dal server`. La scheda allenatore mostra
la squadra relazionale e la riordina con frecce tramite una singola RPC.

Il flusso DM di creazione oggetto usa il Photo Picker, valida/decodifica l'immagine,
la ridimensiona a massimo 1024 px, la converte in WebP entro 2 MB, mostra
l'anteprima, crea prima metadati validi e poi carica/associa l'immagine. Se l'upload
o l'associazione falliscono, l'oggetto resta valido e modificabile. I nuovi file
usano `oggetti/<item-id>/<uuid>.webp`. Dopo sostituzione o reset, la cancellazione
best-effort riguarda solo un vecchio file dentro la cartella proprietaria; percorsi
legacy o condivisi non vengono eliminati. Il bucket, `custom_image_path`, le RPC e
le policy esistenti non sono stati duplicati o indeboliti.

## Verifiche automatizzate e sicurezza

La suite PGlite completa passa: inventario, sprite, editing/storage immagini,
parametri, seed standard, Phase A1 e `tests/phase_a2_revision_team.test.mjs`.
Quest'ultimo copre revisioni, stale write, owner/DM/anon, assegnazione slot,
riordino, unicità, sync legacy, storico, release e rollback. I test live hanno
confermato visibilità player limitata, negazione del riordino altrui e negazione
delle immagini al player; le operazioni DM sono state provate in transazione con
rollback. Le revisioni live sono rimaste tutte a 1.

La compilazione Gradle non è stata eseguibile nell'ambiente Codex perché Windows
ha negato l'avvio del JDK di Android Studio prima dell'esecuzione di Gradle. Va
quindi eseguita in Android Studio insieme alla checklist nuovo APK. Il login Google
OAuth reale di un DM resta **NON VERIFICATO**.

Gli advisor post-deploy riportano avvisi già esistenti su funzioni legacy
`SECURITY DEFINER`, password leaked protection, init-plan RLS e alcuni indici/FK;
A2 non aggiunge funzioni `SECURITY DEFINER` né nuove policy permissive.

## Checklist vecchio APK

- Login player e DM; apertura delle schede autorizzate.
- Modifica e salvataggio di allenatore e Pokémon.
- Cattura, rilascio e aggiornamento squadra.
- Catalogo, borsa, quantità, note e immagini esistenti.
- Verifica che un player non legga o modifichi dati altrui.
- Controllo che ogni update legacy incrementi `revision` senza bloccare l'APK.

## Checklist nuovo APK 1.0 (21)

- Compilazione/test unitari, installazione e login player/DM.
- Funzioni allenatore, Pokémon, inventario e catalogo esistenti.
- Salvataggio revision-aware e aggiornamento della revisione locale.
- Conflitto stale deliberato: nessuna sovrascrittura; modifiche locali conservate;
  conferma e uso di `Ricarica dal server`.
- Due catture ravvicinate: slot distinti; squadra piena mantiene Pokémon senza slot.
- Riordino con frecce, reload su un secondo dispositivo e JSON legacy coerente.
- Rilascio Pokémon e liberazione slot.
- Creazione oggetto DM con scelta immagine, anteprima, upload e reload.
- Sostituzione e reset; fallimento upload; MIME non immagine; file corrotto o oltre
  2 MB; metadati oggetto ancora validi dopo l'errore.
- Player: upload, sostituzione e reset immagine negati dal backend.

## Rollback e limiti

Prima del rollback distribuire nuovamente un client pre-A2. Applicare poi il
contenuto di `rollbacks/20260923213815_phase_a2_trainer_team_rpc_guard.sql` e poi
di `rollbacks/20260923212131_phase_a2_revision_atomic_team_integrity.sql` come
nuove migration compensative: rimuovono trigger/RPC/indice/colonne A2 e
ripristina le funzioni precedenti. Non riscrive schede, Pokémon, inventario o file.
Non cancellare la voce A2 dalla cronologia live.

Restano deliberatamente rimandati: rimozione dei write legacy, eliminazione o
normalizzazione generale dei JSON, migrazione dei Pokémon boxed, cleanup globale
dei vecchi file Storage, correzione degli advisor preesistenti e ogni lavoro AI,
RAG o chatbot.
