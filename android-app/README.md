# Pokerole Sheets per Android

App Android nativa per gestire le schede Pokerole nel nuovo progetto Supabase.

## Funzioni

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
