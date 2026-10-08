# Manuale utente di Nova

Nova è l'assistente PokéRole dell'app Android. Risponde in italiano, usa i dati
visibili al tuo account e consulta il regolamento, i cataloghi e gli strumenti di
calcolo del progetto.

## Come fare una buona domanda

Indica nomi e valori necessari. Se parli di un Pokémon della tua scheda, usa il suo
soprannome o il nome esatto. Se mancano informazioni essenziali, Nova chiede un
chiarimento invece di inventarle.

Esempi:

- `Come funziona la Paralisi?`
- `Mostrami le informazioni di Hatenna.`
- `Che cosa fa la mossa Confusion?`
- `Che effetto ha l'Ability Healer?`
- `Quanti dadi tira la mia Hatenna con Confusion contro Ekans?`

## Regole e condizioni

Puoi chiedere spiegazioni su regole, procedure, condizioni e situazioni di gioco:

- `Come funziona un Critical Hit?`
- `Che penalità dà essere feriti?`
- `Come si risolve un Clash?`
- `Cosa succede quando un Pokémon è paralizzato?`
- `Come funzionano STAB, resistenze e immunità?`

Nova cerca evidence nel regolamento. Se la risposta non è supportata dai dati
disponibili, lo dichiara.

## Pokémon, evoluzioni e learnset

Per un Pokémon del catalogo Nova può mostrare identità Pokédex, tipi, Base HP,
Attributes base e massimi, Ability, dati fisici, descrizione, evoluzioni e mosse per
Rank.

Esempi:

- `Mostrami Hatenna.`
- `Quali sono i tipi e le Ability di Pikachu?`
- `In cosa evolve Hatenna?`
- `Confronta Hatenna e Hattrem.`
- `Quali mosse impara Ekans al Rank Rookie?`

Le informazioni descrivono il catalogo: non evolvono né modificano una scheda.

## Mosse, Ability e strumenti

Puoi consultare una mossa specifica, un'Ability o uno strumento.

Esempi:

- `Spiegami Confusion.`
- `Quali sono Accuracy, Damage, Target ed effetti di Thunder Wave?`
- `Che cosa fa l'Ability Static?`
- `A cosa serve Eviolite?`
- `Che mosse conosce il mio Pokémon chiamato Nova?`

Gli effetti testuali vengono riportati, ma non sono sempre traducibili in un bonus
numerico automatico.

## Profilo, allenatori e Pokémon personali

Nova può leggere solo dati autorizzati dalle policy RLS di Supabase:

- profilo utente corrente;
- propri allenatori e roster;
- allenatori visibili al DM;
- riepilogo, inventario e Pokémon di un allenatore identificato;
- HP, difese, mosse e Ability di un Pokémon visibile.

Esempi:

- `Chi sono?`
- `Quali Pokémon possiedo?`
- `Mostrami i miei allenatori.`
- `Quali mosse conosce la mia Hatenna?`
- `Quanti HP ha attualmente il mio Pokémon?`

Nova non può aggirare le RLS. Un record inesistente e un record non autorizzato
possono produrre la stessa risposta, così non vengono rivelati dati privati.

## Calcoli di gioco

Nova dispone di strumenti per:

- pool azione: Attribute + Skill;
- HP massimi e Will;
- Physical Defense e Special Defense;
- Initiative e tiro relativo;
- difficoltà delle azioni;
- esito di un tiro e Critical Failure;
- Critical Hit normale o High Critical;
- pool e danno di una mossa;
- penalità da ferite;
- conteggio dei successi ottenuti con D6 già tirati.

Esempi:

- `Quanti dadi tiro con Dexterity 3 e Alert 2?`
- `Con 3 successi contro difficoltà 2, il tiro riesce?`
- `È Critical Hit con 5 successi e difficoltà 2?`
- `Qual è la penalità con 3 HP attuali su 8 massimi?`
- `Conta i successi di questi dadi: 2, 4, 5, 1, 6.`

## Tiro dadi

Nova può tirare da 1 a 100 D6. Ogni risultato 4, 5 o 6 vale un successo.

Esempi:

- `Tira 6 dadi.`
- `Lancia 10 D6 e dimmi quanti successi ottengo.`

Nova mostra tutti i risultati e il totale dei successi. Ogni richiesta produce un
nuovo tiro.

## Calcolo dei danni

Se indichi il soprannome di un tuo Pokémon, Nova usa la sua scheda. Se indichi una
specie generica, usa i valori base del catalogo. Per il bersaglio usa la scheda solo
quando è visibile e chiaramente identificata; altrimenti usa la difesa standard del
catalogo.

Il calcolo considera:

- attributo previsto dalla mossa;
- Power;
- Physical Defense o Special Defense del bersaglio;
- STAB;
- debolezza, resistenza e immunità di tipo.

Esempi:

- `Quanti dadi tira la mia Hatenna con Confusion contro Ekans?`
- `Calcola Confusion di Hatenna contro un Ekans generico.`
- `Il mio Pokémon chiamato Luna usa Ember contro Bulbasaur: calcola pool e modificatore finale.`

Nova separa il pool prima del tiro dal modificatore di efficacia applicato al danno
finale. Ability, strumenti, meteo, cambi temporanei, Critical Hit, Set Damage, Varied
Damage ed effetti speciali possono richiedere controllo manuale del DM.

## Domande successive e contesto del combattimento

L'app invia a Nova le ultime sei battute della chat. Puoi quindi proseguire:

1. `La mia Hatenna combatte contro Ekans.`
2. `Usa Confusion: quanti dadi tiro?`
3. `Ora tira quei dadi.`

Il contesto è locale e temporaneo. Non modifica le schede, non salva uno stato di
combattimento nel database e può andare perso chiudendo l'app o ricreando la sessione.
Ripeti nomi e valori se Nova non identifica più i soggetti.

## Feedback sulle risposte

Dopo una risposta puoi scegliere **Utile** o **Non utile**. Il server registra solo
conteggi aggregati associati all'identificativo tecnico della risposta. Non salva il
testo della conversazione nel feedback.

## Cosa Nova non fa

Nova lavora in sola lettura sui dati di gioco. Non può:

- creare, modificare, salvare, evolvere o eliminare schede;
- assegnare Pokémon o cambiare inventari;
- spendere Will, applicare danni o aggiornare HP;
- vedere dati esclusi dalle RLS;
- inventare identificativi, statistiche o regole mancanti;
- garantire automaticamente ogni eccezione descritta nel testo di Ability, mosse o strumenti.

Per modificare dati usa le normali schermate dell'app. Per casi ambigui, effetti
speciali o conflitti tra fonti decide il DM.
