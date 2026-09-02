# Pokerole Sheets per Android

App Android nativa per gestire le schede Pokerole nel nuovo progetto Supabase.

## Funzioni

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
