# Pokerole Sheets per Android

App Android nativa per gestire le schede Pokerole nel nuovo progetto Supabase.

## Funzioni

### Integrità schede e immagini custom — v1.0

La versione **1.0**, versionCode **21**, usa revisioni ottimistiche e RPC atomiche
per salvare allenatori e Pokémon. In caso di conflitto mantiene aperte le modifiche
locali e offre `Azioni → Ricarica dal server`; non esegue merge automatici.
`pokemon.team_slot` è l'ordine autorevole della squadra: cattura e riordino sono
atomici, mentre `sheet_data.pokemon_team` resta una copia compatibile aggiornata dal
server. Il DM può scegliere, vedere in anteprima e caricare una foto già durante la
creazione di un oggetto custom, oltre a sostituirla o ripristinarla in seguito.

La convenzione delle versioni applicative è `1.0 → 1.1 → … → 1.9 → 2.0`; il
`versionCode` cresce sempre. Non è Semantic Versioning.

### Skills Trainer — v0.11.2

Corretto solo il riquadro Skills degli allenatori, con queste 16 voci nell'ordine:

- Fight: Brawl, Throw, Evasion, Weapon.
- Survival: Alert, Athletic, Nature, Stealth.
- Social: Empathy, Etiquette, Intimidate, Perform.
- Knowledge: Crafts, Lore, Medicine, Science.

Channel, Clash e Charm non sono visibili negli allenatori, ma gli eventuali valori
legacy restano nei JSONB. Weapon mantiene la chiave `skills.fight.weapons`: nessuna
conversione o perdita dei dati salvati. Skills Pokémon, attributi, strumenti,
formule e tutte le altre sezioni rimangono invariati. Nessuna query Supabase nuova
per questa correzione; generare APK **0.11.2**, versionCode **20**, con la stessa firma.

### Associazioni degli strumenti standard — query 12

Eseguire `supabase/migrations/12_seed_standard_item_parameters.sql` **dopo la query 11**,
poi premere **Aggiorna oggetti e borsa** nell'app su ciascun dispositivo.
Non occorre rigenerare l'APK se è già installata la versione 0.11.0 o successiva.
Lo strumento deve essere equipaggiato tramite catalogo, non come testo libero.

Sono stati verificati i 236 oggetti del seed community già presente nel repository.
Queste sono le 10 associazioni esplicite ai parametri attualmente evidenziabili:

| Strumento | Parametri | Condizione / limite |
| --- | --- | --- |
| Choice Scarf | Initiative | Reaction e Power delle mosse restano separati |
| Eviolite | Physical Defense, Special Defense | Stadio evolutivo richiesto dall'effetto |
| Iron Ball | Dexterity | È una riduzione, non un aumento |
| Light Ball | Strength, Special | Pikachu |
| Lucky Punch | Strength | Chansey; High Critical resta separato |
| Power Increasers | Dexterity | Solo la riduzione certa: il DM deve aggiungere il parametro scelto per +2 |
| Quick Claw | Initiative | Come da effetto |
| Thick Club | Strength | Cubone / Marowak, secondo l'effetto |
| Throat Spray | Special | Dopo una mossa Sound-Based |
| Weakness Policy | Strength, Special | Dopo un colpo Super-Effective subito |

L'azzurro resta un promemoria del parametro interessato, sia per bonus sia per malus:
non significa che la condizione sia stata verificata, né modifica valori o dadi.
I bonus a Damage/Accuracy/Power delle mosse non diventano bonus a Strength/Special:
ad esempio Twisted Spoon, Choice Band, Choice Specs e Wide Lens non ricevono una
falsa associazione a un attributo. Cura degli HP attuali non significa aumento di
HP maximum. Consumabili, protezioni, effetti generici o a scelta non ricevono
associazioni permanenti arbitrarie. Nel seed attuale non ci sono associazioni
standard esplicite a Social Attributes o Skills: il DM può sempre aggiungerle.

La query non crea oggetti e non tocca schede, borse, immagini o formule. Compila solo
gli array originali vuoti degli strumenti standard con testi ancora uguali al seed.
Preserva associazioni preesistenti e ogni override DM, anche la lista vuota;
salta gli effetti/descrizioni personalizzati per evitare promemoria incoerenti.
Il risultato indica per ogni strumento se è disponibile o richiede una verifica DM.
Incrementa la revisione solo sulle righe aggiornate ed è ripetibile senza altri
incrementi. **Ripristina originale** del DM torna alle associazioni standard importate.

### Skills Pokémon — v0.11.1

La scheda Pokémon mostra soltanto:

- Fight: Brawl, Channel, Clash, Evasion.
- Survival: Alert, Athletic, Nature, Stealth.
- Social: Charm, Etiquette, Intimidate, Perform.

Throw, Weapons, Empathy e il gruppo Knowledge non sono più visibili nei Pokémon.
Le Skills degli allenatori restano invariate. I vecchi valori delle Skills nascoste
sono conservati nei JSONB durante il salvataggio. Nessuna nuova query Supabase è
necessaria per questa correzione; generare APK **0.11.1**, versionCode **19**.

### Dadi, ferite e parametri degli strumenti — v0.11.0

Prima di distribuire l'APK, eseguire **11_item_affected_parameters.sql** nel SQL Editor
del progetto Android, dopo le migrazioni 06–10 già installate. Non serve reimportare
il catalogo. Lo script aggiunge due piccoli array a `catalog_items` e due RPC;
non modifica schede, immagini, borse, storico o tabelle della web app originale.
Poi generare l'APK **0.11.0**, versionCode **18**, con la firma usata finora.

- Toccare una mossa (o **Dadi · Accuracy / Damage / Clash**) nella scheda Pokémon.
  Il box usa i valori correnti della bozza, comprese le modifiche non ancora salvate.
- Accuracy: formula del catalogo, non sempre Dexterity. Le alternative separate da
  `/` sono mostrate distintamente: scegliere quella prevista dalla mossa.
- Damage ordinario: attributo indicato + Power + 1 STAB se il tipo coincide.
  Inserire Physical/Special Defense del bersaglio per vedere il pool dopo la difesa
  (minimo 1 dado). L'opzione **Questo tiro ignora le difese** è manuale: alcuni
  effetti ignorano solo le difese del contraccolpo/danno secondario, non dell'attacco.
- Clash: Strength + Clash per Physical, Special + Clash per Special; nessun pool
  per Support. Il box ricorda le restrizioni, ma non conosce la mossa avversaria.
- Master/Champion: +2 dadi quando il tiro comprende una Skill, non su Damage.
- Formule variabili, mosse copiate e danni speciali non risolti mostrano un avviso di
  calcolo manuale e l'effetto originale, senza inventare un valore.
- Promemoria 1 Will per Basic/Minor/Complete Heal e gli altri effetti curativi che
  specificano quel costo. Non spende Will né cura automaticamente; effetti come
  Pollen Puff dipendono dal bersaglio scelto. Non tutte le cure richiedono Will.
- Ferite, su allenatori e Pokémon: HP <= metà del massimo arrotondata per difetto,
  -1 successo; a 1 HP, -2 successi senza sommare le penalità. A 0 HP, Fainted.
  Un personaggio a HP pieni non riceve penalità. Le prove con Vitality/Will sono esenti.
  L'avviso si aggiorna anche prima del salvataggio e sparisce recuperando abbastanza HP.

Riferimenti: Corebook 3.0, pp. 31 (Master), 54–61 (Accuracy, STAB e Damage),
62 (ferite), 70–71 (Clash), 78 (cure). Critical Hit, effetti condizionali, Ability,
strumenti e altri modificatori non già inseriti nelle statistiche restano manuali.
Low Accuracy e ferite rimuovono successi, non modificano i dadi mostrati.

Il DM trova **Parametri influenzati** sia in **Crea oggetto custom**, sia in
**Modifica nel catalogo**. Può selezionare più Attributes, Social Attributes, Skills
e riferimenti rapidi (HP/Will massimi, difese, Initiative, Evasion).
Le selezioni evidenziano in azzurro i parametri del Pokémon che ha equipaggiato
l'oggetto come Held Item o come accessorio; viene mostrato anche il nome della fonte.
Più oggetti possono evidenziare lo stesso parametro. Togliendo l'oggetto, la sua
evidenziazione scompare. Le condizioni di attivazione dell'effetto restano manuali.

Si tratta solo di promemoria visivi: nessun bonus/malus viene applicato ai valori.
Gli oggetti soltanto in borsa e i vecchi strumenti scritti come testo libero non
attivano evidenziazioni. Per questi ultimi scegliere la voce del catalogo nella
sezione equipaggiamento. Le vecchie voci del catalogo partono senza parametri:
il DM deve selezionarli, poiché i testi degli effetti non vengono interpretati automaticamente.
La personalizzazione dell'effetto del singolo esemplare in borsa non modifica questi
metadati condivisi. Aggiornare il catalogo oggetti sugli altri dispositivi per ricevere
le scelte del DM. **Ripristina originale** ripristina anche i parametri iniziali.

Verifiche locali: compilazione Kotlin/Compose, suite JUnit e test PostgreSQL PGlite
`supabase/tests/item_parameters.test.mjs`. Non sostituiscono il collaudo sull'emulatore.
Checklist: Confusion su Hatenna; Tackle con attributi alternativi; Recover/Life Dew;
HP 9→4→1→0; equipaggiare/rimuovere un oggetto che influenza Strength e Cool;
accessorio che influenza lo stesso Cool; aggiornamento del catalogo da un player.

### Immagini oggetti dall'app — v0.10.3

Eseguire **10_edit_custom_item_images.sql**, dopo lo script 09 già installato, quindi
generare l'APK 0.10.3 (versionCode 17) con la stessa firma. La lettura di `custom_image_path`
funziona per tutti gli oggetti: immagine personalizzata dal bucket pubblico,
poi sprite GitHub, infine segnaposto se anche quello non è disponibile. Percorsi
esterni, traversal e protocolli non HTTPS vengono ignorati. Non caricare dati riservati.

Dal catalogo **Oggetti**, il DM apre un oggetto e usa:

- **Carica immagine** (tutti gli oggetti): galleria, anteprima e conferma. Il file
  viene ridimensionato a massimo 1024 px, convertito in WebP e limitato a 2 MiB.
- **Cambia sprite dal catalogo** (solo oggetti custom): riusa l'immagine di un altro
  oggetto, compreso il suo eventuale percorso custom, senza copiarne gli effetti.
- **Ripristina sprite originale** (oggetti standard): rimuove il percorso custom.
- **Ripristina immagine iniziale** (oggetti custom): torna all'immagine registrata
  alla creazione, che può anche essere una foto custom o nessuna immagine.

Queste azioni sono mostrate fuori dalla modalità di modifica dei testi, per evitare
di perdere modifiche non salvate a nome/effetti. Per un nuovo oggetto custom, crearlo
prima, poi aprirne i dettagli per caricare una foto. La scelta iniziale dell'icona dal
catalogo conserva ora anche le immagini custom della sorgente.

La conferma salva immediatamente sul catalogo, senza Salva scheda; si riflette in
catalogo, borsa, equipaggiamento e accessori collegati. Gli altri dispositivi ricevono
le modifiche con **Aggiorna oggetti e borsa**. Non vengono modificati quantità, effetti
o statistiche. Solo il DM è autorizzato: il controllo è nel database, non solo nei pulsanti.
Le revisioni intercettano modifiche concorrenti, comprese quelle ai percorsi fatte dal dashboard.

Ogni caricamento usa un nome nuovo `oggetti/<uuid>.webp` per evitare cache obsolete.
I file precedenti non vengono cancellati perché possono essere condivisi con altri
oggetti o servire per il ripristino. In caso di upload riuscito ma associazione fallita
o incerta, viene mostrato il percorso caricato: aggiornare il catalogo prima di riprovare.
Un file non associato può restare nel bucket; nessuna cancellazione automatica dopo
un errore di rete. Un amministratore può ripulire manualmente solo file non referenziati
né da `custom_image_path` né da `original_data.CustomImagePath` di altri oggetti.

Compilazione Kotlin e test locali non sostituiscono il collaudo su telefono: verificare
con account DM/player caricamento, lettura dall'altro account, cambio sprite e fallback
per URL inesistente dopo l'installazione degli script. L'agente non ha modificato il DB online.

### Preparazione Supabase per le immagini custom degli oggetti

Eseguire `supabase/migrations/09_configure_item_image_storage.sql` nel SQL Editor del
progetto Android, dopo 06 già installato. Non rieseguire importazioni legacy.
Lo script crea/configura il bucket **pubblico** `pokerole-items` (PNG, JPEG e WebP,
massimo 2 MiB per file) e aggiunge `catalog_items.custom_image_path`, nullable.
Non modifica `pokerole-media`, schede, inventario, effetti, icone originali o percorsi
custom già compilati. Se il bucket omonimo contiene già file privati, l'operazione
viene interrotta: verificare quei file prima di esporli pubblicamente.

Dal dashboard Supabase:

1. Aprire **Storage → pokerole-items** e creare la cartella `oggetti`.
2. Caricare, per esempio, `amuleto-v1.png`.
3. Nel **Table Editor → catalog_items**, trovare l'oggetto e inserire
   `oggetti/amuleto-v1.png` in `custom_image_path`. Non incollare un URL, il nome del
   bucket o un'immagine Base64. Usare nomi senza spazi, con lettere, numeri, trattini
   e underscore; estensioni `.png`, `.jpg`, `.jpeg`, `.webp`.
4. Per togliere la personalizzazione impostare `NULL`, non una stringa vuota.

Le immagini sono accessibili a chiunque ne conosca l'URL: non usare questo bucket
per materiale riservato. Dal client autenticato solo il DM può caricare, elencare,
modificare o cancellare file. Policy restrittive proteggono questo bucket anche in
presenza di policy permissive generiche, senza cambiare l'accesso agli altri bucket.
I membri amministratori del progetto Supabase possono gestirlo dal dashboard.
Le autorizzazioni di scrittura diretta su `catalog_items` restano invariate: per ora
il percorso si modifica dal Table/SQL Editor, non dal client Android.

**L'APK 0.10.2 non usa `custom_image_path`; il supporto è disponibile dalla 0.10.3.**
Lo script 09 prepara solo bucket, tabella e permessi; vedere sopra per lo script 10
e la gestione dall'app. Per sostituire manualmente un file,
preferire un nome nuovo (`amuleto-v2.png`) e aggiornare il percorso per evitare cache
obsolete. Non sono state eseguite modifiche sul database remoto dall'agente.

Il test `supabase/tests/item_image_storage.test.mjs` verifica schema, ripetibilità,
permessi e percorsi su PostgreSQL locale. Non simula l'HTTP Storage/CDN: download
pubblico e limiti MIME/dimensione vanno verificati sul servizio Supabase dopo l'installazione.

### Equipaggiamento Pokémon — v0.10.2

Due spazi separati nella scheda Pokémon:

- **Held Item**: un solo strumento. Scelta dal catalogo con sprite e descrizione,
  sostituzione, rimozione o nome manuale. Il precedente testo `quick_references.held_item`
  viene conservato e non viene associato automaticamente a un oggetto omonimo.
- **Accessories & Ribbons**: elenco indipendente per accessori, costumi e fiocchi,
  ciascuno con nome e note; collegamento facoltativo al catalogo, modifica e rimozione.

**Salva scheda** salva entrambi nel JSONB del Pokémon, con il normale storico della
scheda (diversamente dagli slot borsa). Nessuna nuova tabella. Il collegamento al
catalogo mostra gli effetti correnti del DM; gli accessori possono conservare un nome
individuale, come il nome di una gara. Nessun bonus, consumo o trasferimento dalla
borsa è automatico: equipaggiare/rimuovere modifica solo la scheda Pokémon.
Le categorie non sono bloccanti, così sono selezionabili anche gli oggetti custom;
la corretta classificazione degli accessori rimane al gruppo.

Per le immagini eseguire **08_add_item_sprites.sql** dopo 06/07, poi aggiornare gli
oggetti in app. Aggiunge 8 collegamenti della [community Pokérole](https://github.com/Pokerole-Software-Development/Pokerole-Data/tree/abbe22a7e42853c95d6602b97bb0034833b7c7bc/images/ItemSprites):
big/small camping tent, leek, mountain bike, pokedex, regional map, pokemon repel,
umbrella. I due tipi di tenda condividono un disegno; per l'ombrello si riutilizza la
grafica Utility Umbrella, NON le sue regole dei videogiochi. Sono immagini/sprite di
stili diversi, non tutte pixel art. Il totale del catalogo iniziale passa a **206/236**;
per i 30 restanti resta il segnaposto. La fonte segnala che alcune categorie e oggetti
esclusivi del gioco non hanno una grafica assegnata.

Lo script è ripetibile, riempie solo `sprite_path` mancanti e lascia invariati effetti,
personalizzazioni e immagini già configurate. I nuovi URL sono vincolati al repository
community e a un commit preciso. Non serve caricare immagini nel bucket Supabase.
Per usare immagini nuove e nuovi riquadri generare APK **0.10.2**, versionCode **16**.

### Borsa compatta — v0.10.1

- Ogni oggetto è una riga cliccabile con sprite, nome e quantità, senza tendina.
  Il tocco apre descrizione, effetto, note e azioni; modifica/personalizzazione
  rimangono disponibili nel dettaglio.
- Potion, Super Potion e Hyper Potion hanno lo stesso aspetto, ereditano descrizioni
  ed effetti del catalogo (anche modificati dal DM) e mantengono le quantità dei tre
  contatori già presenti. Senza catalogo vengono mostrati nome/sprite di base e un
  avviso; non si inventano effetti. **Applica quantità**, poi **Salva scheda**.
- **Usa** nel dettaglio di uno slot consuma una unità e salva subito su Supabase,
  mantenendo le personalizzazioni sulle unità rimaste. Consumare l'ultima unità
  registra uno slot vuoto, senza far riapparire il testo legacy sottostante.
  Per un vecchio testo non ancora verificato, modificare e salvare prima lo slot.
  Nell'editor, **Usa** modifica invece la bozza e richiede **Salva slot**.
  Sulle tre pozioni, **Usa** scala la quantità e richiede **Salva scheda**; a zero
  questi tre riquadri fissi restano visibili. Nessuna cura viene applicata automaticamente.
- **Rimuovi vecchi oggetti**, con conferma, svuota SOLO i testi legacy A/B della
  scheda aperta, anche quelli coperti da nuovi slot. Non tocca i tre contatori,
  `trainer_inventory`, catalogo o altre schede. Serve **Salva scheda** per confermare
  la pulizia sul database. Prima è annullabile uscendo senza salvare; dopo il salvataggio
  il precedente JSON è soggetto alla normale conservazione dello storico allenatore.

Nessuna nuova migrazione SQL rispetto a 0.10.0: devono essere già installati 06/07.
Generare l'APK 0.10.1, versionCode 15, con la stessa firma. I contatori rapidi e gli
slot rimangono indipendenti e non vengono sommati automaticamente.

### Oggetti e borsa — v0.10.0

Su un database Android già configurato eseguire, nell'ordine, nel SQL Editor:

1. `supabase/migrations/06_item_catalog_inventory.sql`
2. `supabase/migrations/07_seed_item_catalog.sql`

Non ripetere gli import delle schede legacy. Il primo script crea `catalog_items`,
`trainer_inventory`, policy e funzioni; il secondo inserisce 236 oggetti mancanti
senza sovrascrivere righe esistenti. Le tabelle preesistenti e i JSON delle schede
non vengono modificati. Rigenerare poi l'APK 0.10.0, versionCode 14, con la stessa firma.

#### Catalogo condiviso e oggetti custom

- Scheda **Oggetti** nella home: ricerca per nome/categoria, descrizione, effetto,
  prezzo e icona. I nomi e i riferimenti del catalogo rimangono in inglese.
- Il DM può usare **Modifica nel catalogo** e **Salva per tutti** per cambiare nome,
  descrizione, effetto e prezzo: le borse collegate ereditano i nuovi valori.
- **Ripristina originale** rimuove queste modifiche, conservando i dati di partenza.
- **Crea oggetto custom**, solo DM: nome, categoria, descrizione, effetto, prezzo
  facoltativo e icona riutilizzata da un oggetto del catalogo. La scelta dell'icona
  non copia statistiche o effetti. Nessuna immagine viene caricata per questa funzione.
  Il nuovo oggetto è condiviso e selezionabile nelle borse come gli altri; per un
  oggetto custom il ripristino ritorna ai dati inseriti alla creazione.
- Gli aggiornamenti arrivano agli altri dispositivi al caricamento o con
  **Aggiorna oggetti e borsa**, non in tempo reale. Le modifiche richiedono internet;
  non è prevista una coda offline per l'inventario.

#### Slot e personalizzazione di un esemplare

Nella **Borsa** dell'allenatore, **Aggiungi oggetto** usa il primo dei 30 slot liberi.
**Cerca nel catalogo** collega l'oggetto scelto allo slot; quantità (0–9999) e note
sono modificabili dal proprietario o dal DM. **Salva slot** salva subito SOLO lo
slot, indipendentemente da **Salva scheda** e dalle altre modifiche non salvate.
Salvare prima una nuova scheda allenatore. I dati legacy non vengono interpretati
automaticamente: il testo originale resta visibile e la quantità iniziale 1 va verificata.
Se il testo legacy contiene quantità nel nome, correggerle esplicitamente.

**Personalizza questo esemplare**, solo DM, conserva nome, descrizione ed effetto
personalizzati sullo slot. Questi tre campi hanno precedenza sul catalogo anche dopo
aggiornamenti globali. Togliere la spunta e salvare ripristina l'ereditarietà dal
catalogo corrente. Una modifica di quantità/note da parte del player conserva le
personalizzazioni DM; scegliere un oggetto differente le rimuove.

La personalizzazione vale per **tutte le unità dello slot**: per un solo esemplare
speciale, usare uno slot separato con quantità 1. Gli oggetti a testo libero non
hanno uno sprite automatico. Gli effetti sono consultabili, ma non curano né
modificano automaticamente le statistiche.

Da 0.10.1 **Usa** sostituisce il pulsante Svuota: consumando l'ultima unità registra uno slot
vuoto senza cancellare il vecchio testo nei JSON. Il nuovo
record ha precedenza, quindi il vecchio oggetto non ricompare. I contatori rapidi
Pozioni/Super Pozioni/Iper Pozioni restano indipendenti e vengono salvati con la
scheda: non vengono sommati o convertiti automaticamente negli slot.

#### Permessi, conflitti e storico

Il catalogo è leggibile dagli utenti autenticati. Solo il DM crea oggetti o ne
modifica gli effetti. La borsa è leggibile/modificabile dal proprietario del trainer
o dal DM. Le scritture dirette alle due tabelle sono negate ai client: le funzioni
`create_catalog_item`, `update_catalog_item` e `save_inventory_slot` verificano
autenticazione, ruolo, proprietario e input sul server. Le revisioni impediscono
di sovrascrivere silenziosamente modifiche simultanee: in caso di conflitto chiudere
l'editor, aggiornare e riaprire. Una ripetizione identica della richiesta di creazione
non genera un secondo oggetto.

**Limite importante:** `sheet_versions` continua a salvare le schede, non il nuovo
catalogo o inventario. Ripristinare una vecchia scheda lascia invariati gli slot nuovi.
Per conservarne una copia esportare separatamente `catalog_items` e `trainer_inventory`.
Il ripristino dell'originale del catalogo non è uno storico delle modifiche.

#### Fonti e sprite

236 oggetti da [Pokérole Data community](https://github.com/Pokerole-Software-Development/Pokerole-Data)
3.0, commit `abbe22a7e42853c95d6602b97bb0034833b7c7bc`. Non è una trascrizione
verificata di ogni oggetto del manuale: descrizioni e metadati degli effetti sono
riportati dalla fonte e restano controllabili/modificabili dal DM. `original_data`
conserva il JSON completo della fonte.

198 oggetti hanno un collegamento verificato agli [sprite PokéAPI](https://github.com/PokeAPI/sprites/tree/master/sprites/items);
per gli altri 38 o in caso di errore di caricamento compare un segnaposto. Gli sprite
vengono scaricati dal repository pubblico e non occupano spazio su Supabase Storage.
Gli oggetti custom possono riutilizzare queste icone; non vengono inventati URL per
gli oggetti senza corrispondenza.

#### Verifiche per lo sviluppo

I test Kotlin includono `ItemInventoryTest` (precedenza personalizzazioni, conversione
non distruttiva degli slot legacy, input, serializzazione e URL sprite).
In `supabase/tests`, eseguire `npm install` e `npm test` per i test SQL isolati con
PostgreSQL locale PGlite: non leggono credenziali e non si collegano al database reale.
Verificano permessi DM/player/anon, isolamento tra allenatori, creazione custom,
retry senza duplicati, conflitti di revisione, reset e conservazione dei dati legacy.
Il collaudo su dispositivo deve includere due account, scelta oggetti, salvataggio
slot, aggiornamento dall'altro account e creazione/personalizzazione DM.

### Schede compatte e azioni — v0.9.0

**Prima di usare Libera e le immagini Pokémon**, eseguire nel SQL Editor del progetto
Android `supabase/migrations/05_sheet_actions.sql`, dopo gli script 01 e 04 già installati.
Lo script aggiunge una funzione e policy Storage: all'installazione non elimina o
modifica schede, snapshot o dati del catalogo. Non rieseguire l'importazione legacy.

#### Calcoli confermati per la campagna

Opzione 2 del Corebook 3.0, pp. 26–28, 31, 56, 70:

| Campo | Valore base |
|---|---|
| HP maximum | Base HP + Vitality (+2 se Pokémon Alpha) |
| Will maximum | Insight + 3 |
| Physical Defense | Vitality |
| Special Defense | Insight |
| Initiative | Dexterity + Alert, senza il tiro 1d6 della battaglia |
| Evasion | Dexterity + skill Evasion, numero di dadi del tiro |

Gli allenatori hanno Base HP 4. Master/Champion applicano +3 a HP, Will, alle due
difese e Initiative; il bonus +2 dadi ai tiri con una Skill viene incluso in Evasion.
I bonus non aumentano i punti nelle Skills e non si sommano nuovamente a ogni calcolo.
La variante HP basata su Insight non è attiva per questa campagna.

I valori si ricalcolano quando si cambiano Attributes, Alert, Evasion, Rank, Base HP
o Alpha, durante precompilazione/evoluzione oppure con **Azioni → Calcola massimali e
valori base**. La sola apertura della scheda non cambia i dati precedenti. Il calcolo
non modifica HP current o Will current, neppure quando superano il nuovo massimo:
controllare eventuali valori fuori scala con il DM. Per curare usare **Reset HP / Will**.
Modificatori di combattimento, strumenti e Abilities non sono applicati automaticamente;
i campi restano modificabili manualmente e il ricalcolo ripristina i valori base.

Attributes e Social Attributes delle nuove schede manuali partono da 0, che è anche
il minimo selezionabile. I massimi continuano a dipendere da specie e Rank. La
precompilazione delle catture conserva invece gli Attributes base reali della specie;
non azzera i valori del catalogo. Non vengono azzerate le schede già salvate.

#### Interfaccia e riferimenti

- Menu **Azioni** sempre sopra la scheda: Salva, Calcola, Reset, Evolvi, Aggiorna catalogo, Carica immagine alternativa e Libera Pokémon. I comandi non applicabili sono disabilitati.
- Ricerca compatta a 13 sp (etichette a 12 sp), senza ridurre i controlli tattili sotto le dimensioni Material; rispetta l'ingrandimento testo del dispositivo.
- Ability: ricerca locale per nome, prima le abilità della specie; una sola scelta con effetto e descrizione. Nature: selezione ricercabile con descrizione, parole chiave e configurazione. I dati legacy sconosciuti restano visibili, senza sostituzione automatica.
- Nature, note/appunti, borsa, suggerimenti mosse, informazioni del catalogo e spiegazione delle formule si possono espandere/collassare.
- **Shiny** cambia solo lo sprite automatico; **Alpha** aggiunge +2 HP maximum. Entrambi persistono in `sheet_data.pokerole` e restano dopo l'evoluzione. Nessuna nuova colonna necessaria.
- Sprite regionali e mega risolti attraverso gli ID delle forme, non il solo numero nazionale. Si provano esclusivamente immagini verificate della stessa forma e colorazione; se non disponibili, appare un segnaposto. Le immagini personalizzate hanno precedenza su Shiny e sulle evoluzioni.
- L'immagine alternativa dalla galleria funziona anche per i Pokémon già salvati. Viene compressa WebP (massimo 2 MB), salvata nel bucket privato esistente e autorizzata per DM/proprietario. Prima della galleria una conferma avvisa che l'upload salva anche le modifiche alla scheda e sovrascrive il ritratto precedente. **Usa sprite automatico** cambia la bozza e richiede Salva; non cancella il file Storage.

I 305 riferimenti Ability e le 25 Nature sono inclusi nell'APK dalla stessa fonte
community [Pokérole Data v3.0](https://github.com/Pokerole-Software-Development/Pokerole-Data),
commit `abbe22a7e42853c95d6602b97bb0034833b7c7bc`; non sono descrizioni generate.
Non richiedono nuove tabelle Supabase. Gli indici degli sprite derivano da
[PokéAPI](https://github.com/PokeAPI/pokeapi/blob/master/data/v2/csv/pokemon.csv) e dal
[repository sprite](https://github.com/PokeAPI/sprites). Il file `sprites.json`
registra l'albero verificato. L'indice non risolve le due forme di Poltchageist,
le due di Sinistcha e Rotom Dex: per queste non viene mostrata un'altra forma.

#### Libera Pokémon

Richiede conferma esplicita con nome e allenatore. La funzione `release_pokemon`
controlla ID Pokémon, ID allenatore e autorizzazione DM/proprietario, elimina solo
quella riga e aggiorna gli slot nel JSON dell'allenatore nella stessa transazione.
Un errore annulla entrambe le modifiche. Le altre schede restano invariate.
Il trigger di storico già installato conserva lo snapshot di cancellazione,
soggetto al limite esistente di 2 versioni Pokémon e 5 allenatore. Non è un cestino
illimitato e il ripristino resta manuale. L'eventuale file immagine non viene eliminato
dal bucket: rimuoverlo potrà essere una successiva pulizia amministrativa.

#### Verifica manuale prima di distribuire

1. Installare lo script 05 e generare l'APK aggiornato da Android Studio.
2. Aprire una scheda vecchia: deve conservare i valori; usare Calcola e poi Salva.
3. Cambiare Vitality/Insight/Dexterity/Alert/Evasion, Rank e Alpha: verificare le formule senza cura automatica. Attivare/disattivare Alpha più volte non deve accumulare HP.
4. Cercare Ability/Nature, salvare e riaprire; provare Shiny e una forma regionale/mega.
5. Su un Pokémon di prova, annullare Libera e verificare che rimanga; confermare poi con DM/proprietario e verificare lista, squadra e snapshot. Non provare su una scheda importante senza copia di sicurezza.
6. Caricare un ritratto Pokémon come proprietario e DM; un player diverso non deve poter leggere o modificare la scheda/immagine.

### Pokédex, ricerca e catture DM — v0.8.0

- La pagina **Pokédex** consulta il catalogo già importato su Supabase: ricerca per nome, numero, forma e tipo, filtro Type e tutte le specie/forme scaricate, senza il precedente limite di 200 risultati.
- Ogni voce mostra sprite, descrizione e categoria disponibili, dimensioni, Attributes base/massimo, Base HP, Abilities, evoluzioni e mosse con dettagli e filtro per Rank. I nomi delle caratteristiche restano in inglese.
- Aprire un'evoluzione nel Pokédex serve soltanto a consultarne la voce: non modifica né evolve i Pokémon della campagna. Le forme mostrano ancora lo sprite nazionale di base.
- Il DM ha una ricerca nelle liste delle schede: nome/squadra per allenatori, soprannome/specie/allenatore/numero per Pokémon. Maiuscole e accenti non influiscono sulla ricerca; più parole possono corrispondere a campi diversi.
- La lista Pokémon è ordinata prima per allenatore e poi per soprannome, oppure specie se il soprannome manca. L'ordine resta invariato nei risultati filtrati.
- Durante una **nuova cattura del DM**, scegliere l'allenatore nell'elenco ricercabile delle schede caricate dalla tabella `trainers` di Supabase. L'associazione usa l'UUID, non il nome digitato. La scelta può essere cambiata fino al salvataggio senza cancellare i dati del Pokémon; precompilare la specie conserva l'allenatore scelto.
- Il salvataggio della cattura DM è bloccato senza un allenatore valido. Se l'elenco è vuoto, tornare alle schede e aggiornarle oppure creare prima l'allenatore. Un allenatore rimosso o non più accessibile viene segnalato al salvataggio, senza ripiego su un omonimo. Per i player resta l'associazione automatica al proprio allenatore.

Nessuna nuova tabella, query SQL o modifica delle policy è necessaria. Il Pokédex usa
lo stesso download autenticato e la stessa cache delle catture: ultima copia valida
in caso di errore, oppure le quattro specie locali se non esiste ancora una copia
completa. **Aggiorna catalogo** scarica nuovamente il catalogo da Supabase.

### Difese Pokémon — v0.7.4

Anche nella scheda Pokémon i campi si chiamano **Physical Defense** e **Special Defense**.
Per entrambe le tipologie di scheda il reset ripristina solo HP e Will, lasciando
invariate le due difese. Nessuna modifica ai valori salvati o alle chiavi JSON legacy.

### Difese allenatore — v0.7.3

Nella scheda allenatore i due campi sono ora **Physical Defense** e **Special Defense**.
I valori e le chiavi JSON legacy rimangono invariati; non è necessaria una migrazione.
Il reset dell'allenatore ripristina solo HP e Will, senza copiare una difesa nell'altra.
Etichette e comportamento del reset dei Pokémon restano invariati.

### Caricamento ritratti — v0.7.2

Corretto l'errore `Impossibile leggere l'immagine` nella lettura preliminare delle
dimensioni: `BitmapFactory` con `inJustDecodeBounds=true` restituisce normalmente
`null`, anche per immagini valide. Il controllo ora distingue un file non apribile
da questo risultato previsto, chiude gli stream dopo entrambe le letture e mantiene
la verifica di formato/dimensioni prima della compressione WebP.

Il caricamento resta disponibile per gli allenatori già salvati, con limite di 2 MB
dopo la compressione. Non cambiano bucket, policy, dati delle schede o permessi DM.
Per verificare: aggiornare l'APK, aprire una scheda allenatore salvata, scegliere il
ritratto dalla galleria, attendere la conferma e riaprire la scheda. Il controllo
end-to-end su Android e Supabase è distinto dai test locali di gestione degli stream.

### Compatibilità schede legacy — v0.7.1

Corretto il riconoscimento dei numeri Pokédex scritti dalla web app come `# 856` o
`# 061` (spazio dopo `#`). Queste schede ora accedono ai riferimenti del catalogo,
ai suggerimenti e al tasto Evolvi senza dover ricreare il Pokémon. Il confronto
normalizza prefisso, spazi esterni e zeri iniziali, ma non modifica il JSON salvato.
Specie/numero in conflitto e numeri non validi continuano a non essere associati.

### Catalogo completo ed evoluzioni — v0.7.0

- Lettura autenticata dalle tabelle `catalog_sources`, `catalog_pokemon`, `catalog_moves` e `catalog_learnsets`, già popolate dal pacchetto SQL dedicato. I rank numerici 1–8 corrispondono a Starter, Rookie, Standard, Advanced, Expert, Ace, Master, Champion.
- Download paginato con ordinamento stabile, controllo conteggi/riferimenti e cache atomica separata per progetto Supabase. Nessuna scrittura nelle tabelle del catalogo. Una copia incompleta non sostituisce quella precedente.
- **Aggiorna catalogo**, nella scheda Pokémon e nella pagina Catalogo, mostra l'avanzamento e permette di riprovare. Se il download non riesce si usa l'ultima copia locale valida; se manca, restano le quattro specie del catalogo di emergenza.
- Lo snapshot importato contiene 1.199 specie/forme utilizzabili e 894 mosse; Egg è escluso dalla selezione delle catture. La fonte è il dataset community Pokerole-Software-Development/Pokerole-Data v3.0, commit `abbe22a7e42853c95d6602b97bb0034833b7c7bc`. Non è una revisione manuale integrale del Corebook.
- La cattura permette di cercare nome, numero o forma. Il Rank predefinito è Starter, rimane manuale e non cambia selezionando una specie. Dopo la precompilazione, i punti del Rank restano da distribuire.
- Suggerimenti delle mosse per rank uguale o inferiore, senza doppioni nella lista. I riquadri delle mosse riconosciute mostrano Power (anche variabile), Accuracy, Damage, Target, categoria ed effetti in inglese. Le mosse inserite manualmente e quelle di rank superiore già salvate non vengono rimosse.
- La regola Any Move di Mew viene segnalata: le scelte extra restano manuali con il DM. Non vengono assegnate automaticamente tutte le mosse. Sketch e altre ripetizioni della fonte restano nei riferimenti, ma la scelta mostra ogni mossa una volta.
- Corretto anche nel catalogo locale di emergenza il massimo Dexterity di Hatenna: **3**, non 2. La correzione del riferimento non altera automaticamente il punteggio della scheda.

#### Tasto Evolvi

1. Aprire una scheda Pokémon già salvata e attendere il catalogo completo.
2. Premere **Evolvi** nella sezione Identità. Il pulsante è disabilitato se la specie/forma non è riconosciuta o non ha destinazioni indicate nel catalogo.
3. Scegliere la destinazione e, quando sono disponibili più abilità, l'Ability della nuova specie. Le condizioni presenti nella fonte sono promemoria: oggetti, scambi e requisiti non vengono verificati o consumati automaticamente.
4. **Applica alla bozza** aggiorna specie, numero, forma, tipi, dimensioni, abilità e Base HP. Ricalcola i massimi HP/Will, ma non cura. Se cambia Ability, i vecchi testi dell'abilità sono svuotati per non mostrare effetti errati.
5. Controllare Attributes e limiti con il DM, poi premere **Salva scheda**. La stessa riga Pokémon viene aggiornata, senza creare un secondo individuo; lo storico segue le policy già installate sul server.

Restano conservati proprietario, allenatore, slot, soprannome, Rank, Attributes attuali, Skills, Social Attributes, mosse conosciute, Nature, Happiness/Loyalty, note, strumenti e immagine personalizzata. Nessun punto viene distribuito o corretto automaticamente, anche quando i nuovi limiti differiscono. Se non c'è un'immagine personalizzata, lo sprite segue il nuovo numero Pokédex; le forme condividono ancora lo sprite nazionale di base.

Prima della conferma si può annullare senza modifiche. Dopo aver applicato alla bozza, uscire senza salvare lascia invariata la scheda nel database. Non viene eseguita alcuna evoluzione automaticamente in base al Rank. Le destinazioni mancanti o con un nome non risolvibile nel dataset non vengono indovinate.

La versione 0.7.0 non richiede nuove colonne nelle schede. Richiede invece l'importazione del catalogo SQL descritta sopra; le impostazioni URL/chiave pubblica e Google login rimangono quelle esistenti.

### Schede e cattura — funzionalità introdotte in v0.6.0

- Attributes, Social Attributes e Skills sono mostrati in inglese.
- Gli allenatori non mostrano Special e hanno limite 5; Champion consente +2.
- I Pokémon supportano fino a 12 punti. Per le specie verificate si usa il limite specifico dell'attributo (più l'eventuale bonus Champion), mostrando separatamente il valore base.
- Social Attributes: 1–5. Skills: 0 fino al limite del Rank selezionato. I valori legacy fuori scala sono segnalati e conservati, non corretti automaticamente.
- Il catalogo `app/src/main/assets/pokerole/species-v3.json` contiene Hatenna, Shinx, Wimpod e Poliwhirl, forma Standard, verificati sul Corebook 3.0 (pp. 385, 257, 361, 154). Include tipi, Base HP, dimensioni, Abilities, attributi base/limiti e mosse per Rank.
- Durante una nuova cattura, scegliere Species e Rank, quindi confermare **Precompila scheda**. I punti aggiuntivi del Rank non sono distribuiti automaticamente. Le mosse sono suggerimenti da scegliere fino a Insight + 3; tutor, TM e deroghe del DM restano manuali.
- Happiness e Loyalty iniziano a 2, da adattare alle condizioni della cattura. Le Abilities riportano quelle elencate nel manuale, senza inventare descrizioni.
- **Calcola massimi HP / Will** usa Base HP + Vitality e Insight + 3, con il bonus +3 da Master in poi. Modifica solo i massimi e non cura il personaggio. La scelta delle varianti HP/Defense di p. 27 rimane al DM; Defense non viene ricalcolata.
- Nessuna scheda salvata può essere reinizializzata dal catalogo. Le specie/forme non coperte rimangono in modalità manuale, senza attribuire loro dati di altre forme.
- Rank, Form e Base HP vengono conservati nel campo JSONB `sheet_data`, nella sezione aggiuntiva `pokerole`. Nessuna migrazione SQL richiesta; sezioni legacy e campi sconosciuti vengono preservati.

Il PDF del manuale non è incluso nel repository. Gli sprite rimangono separati dai valori di gioco Pokérole: le statistiche dei videogiochi non sono usate come sostituti.

### Funzioni generali

- Accesso Google separato per DM e player, con ruolo verificato nel database.
- Il DM vede e modifica tutte le schede.
- Ogni player vede e modifica solamente il proprio allenatore e i relativi Pokémon.
- Associazione iniziale dell'allenatore tramite codice generato dal DM.
- Lettura e salvataggio nelle tabelle `user_profiles`, `trainers` e `pokemon`.
- Backup automatici gestiti in Supabase dalla tabella `sheet_versions`.
- Cattura di nuovi Pokémon e inserimento nel primo slot squadra disponibile.
- Modifica completa dei dati conservando il JSON legacy in `sheet_data`.
- Reset di PS, Difesa e Volontà ai rispettivi valori massimi.
- Catalogo Pokémon locale con sprite caricati dal repository pubblico di PokéAPI.
- Ritratto dell'allenatore o immagine alternativa del Pokémon tramite URL salvato nella scheda.
- Selezione del ritratto dell'allenatore direttamente dalla galleria, compressione WebP e upload privato.
- Riquadri delle mosse colorati automaticamente in base al tipo presente nei JSON locali.

Gli sprite vengono ricavati dal numero Pokédex. Se una scheda Pokémon contiene un URL immagine,
questo ha precedenza sullo sprite automatico.

## Immagini allenatori

1. Crea in Supabase Storage un bucket privato chiamato `pokerole-media`.
2. Esegui nel SQL Editor `supabase/migrations/04_configure_portrait_storage.sql`.
3. Apri una scheda allenatore già salvata e premi **Scegli immagine dalla galleria**.

L'app ridimensiona l'immagine a un massimo di 1024 px, la converte in WebP e la salva come
`trainers/<trainer_uuid>/portrait.webp`. Il database conserva soltanto il riferimento privato;
la visualizzazione usa un URL firmato temporaneo. Il proprietario può modificare il proprio
ritratto e il DM può gestire quelli di tutti gli allenatori.

## Configurazione locale

1. Apri `android-app` in Android Studio.
2. Crea o aggiorna `local.properties` con URL e chiave pubblica del NUOVO progetto:

   ```properties
   SUPABASE_URL=https://PROJECT_REF.supabase.co
   SUPABASE_PUBLISHABLE_KEY=sb_publishable_...
   ```

3. Non inserire mai la password del database, una secret key o la `service_role` nell'app.
4. Sincronizza Gradle e avvia l'app su Android 8.0 o successivo.

## Configurazione Google OAuth

1. Nel progetto Supabase apri **Authentication > Sign In / Providers > Google**.
2. Copia il Callback URL mostrato da Supabase.
3. In Google Auth Platform crea un client OAuth di tipo **Web application**.
4. Inserisci il Callback URL di Supabase tra gli **Authorized redirect URIs**.
5. Copia Client ID e Client Secret nel provider Google di Supabase e abilitalo.
6. In **Authentication > URL Configuration > Redirect URLs** aggiungi:

   ```text
   pokerolesheets://login-callback
   ```

## Primo DM

Il master deve effettuare almeno un login Google. In seguito, dal SQL Editor, esegui:

```sql
update public.user_profiles
set role = 'dm'
where user_id = (
    select id
    from auth.users
    where lower(email) = lower('EMAIL_GOOGLE_DEL_MASTER')
);
```

Dopo la promozione deve uscire dall'app e rientrare tramite **Login DM**.

## Associazione dei player

1. Il DM apre un allenatore non ancora assegnato e imposta un codice di almeno 6 caratteri.
2. Il player accede tramite **Login Player**.
3. Seleziona il proprio allenatore, inserisce il codice e conferma.
4. Da quel momento le policy RLS gli mostrano soltanto la propria scheda e i suoi Pokémon.
