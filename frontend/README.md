# ARCADE: frontend di Capstone Steam

Negozio di videogiochi in React 19 + Vite 8 + Tailwind CSS v4 + React Router 7. Parte dal design di Figma Make *Video-Game-Website* e consuma l'API Spring Boot di questo stesso repository.

## Avvio del progetto

1. Avvia il backend sulla porta 8081. Consulta il [README principale](../README.md).
2. Installa le dipendenze e avvia il frontend:

   ```bash
   cd frontend
   npm install
   npm run dev        # http://localhost:5173
   ```

   In sviluppo, le richieste a `/api` passano dal proxy di Vite e arrivano a `http://localhost:8081`. Per usare un altro URL: `API_TARGET=http://localhost:8080 npm run dev`.

3. `npm run build` controlla i tipi con `tsc` e genera `dist/`. Se il frontend viene servito da un altro dominio, definisci `VITE_API_URL` (vedi `.env.example`) e aggiungi quell'origine a `CORS_ALLOWED_ORIGINS` nel backend.

**Utenti demo** (dopo `node scripts/seed-demo.mjs` nella radice del repository; password `password123`):

| Utente | Ruolo | Cosa ha |
|---|---|---|
| `demo_player` | Giocatore | Giochi con ore giocate, wishlist, un gioco nel carrello e un pagamento rifiutato |
| `rev_es_01`… `rev_en_01`… `rev_it_01`… | Giocatore | 60 recensori (20 per lingua) che hanno acquistato e recensito nella propria lingua |
| `team_cherry` | Developer | Hollow Knight pubblicato e *Silksong* come bozza |
| `admin` | Amministratore | Password: l'`ADMIN_PASSWORD` del `.env` del backend |

**Pagamento simulato:** una carta che termina con `0000` viene sempre rifiutata.

Le copertine della demo sono quelle di Steam: `cardImage()` e `wideImage()` (in `lib/format.ts`) scelgono la capsula orizzontale per le schede e l'immagine panoramica per l'hero. I giochi con una copertina caricata su Cloudinary usano quell'URL così com'è.

**Funzioni opzionali:** il frontend non contiene chiavi. Il caricamento delle copertine (`CoverUpload`) si nasconde da solo se `GET /api/uploads/covers/status` risponde `enabled: false`, e la chat dell'assistente mostra «L’assistente non è disponibile in questo momento» se il backend risponde 503 (senza `OPENROUTER_API_KEY`). Vedi le variabili nel [README principale](../README.md#2-configurazione).

## Struttura

```
src/
├── api/          client.ts (fetch + JWT + rinnovo con refresh token + ProblemDetail), endpoints.ts (una funzione per endpoint), types.ts (DTO)
├── context/      StoreContext: sessione, carrello, id della wishlist e avvisi (toast)
├── components/   Layout (barra laterale + barra superiore del design), GameCard, ReviewScoreBadge (etichetta e % di recensioni), AssistantChat (chat dell'assistente), CoverUpload (caricamento copertina), UserAvatar, Icon, ui (stati, prezzo, RequireAuth)
├── demos/        DemoPlayer, registry (per slug), loop (ciclo e tastiera) e un minigioco per cartella
├── lib/          format (€, date es-ES, tempo di gioco), useAsync
├── pages/        Discover, Browse, GameDetail, Cart, Orders, Collection (libreria e wishlist), Friends, Profile, Auth, Settings, Developer, Admin
├── index.css     CSS originale del design di Figma Make
└── app.css       stili delle pagine nuove, con la stessa palette
```

## Pagine

| Rotta | Accesso | Cosa fa |
|---|---|---|
| `/` | Pubblico | Hero con i più apprezzati (per % di recensioni positive), più venduti, offerte e novità |
| `/browse` | Pubblico | Catalogo con i filtri della Criteria API (testo, generi, prezzo, % minima di recensioni positive, offerte, ordinamento e paginazione); i filtri si salvano nell'URL |
| `/games/:slug` | Pubblico | Scheda del gioco con i pulsanti in base a `/library/status`, l'etichetta e la % di recensioni positive e l'elenco di recensioni 👍/👎 (creare, modificare ed eliminare), filtrato per la lingua dell'interfaccia con un controllo «Tutte le lingue» |
| `/demos` | Pubblico | Demo giocabili di quattro giochi del catalogo (vedi «Demo») |
| `/login`, `/register` | Pubblico | Registrazione come giocatore o developer. Creare l'account non avvia la sessione: porta a `/check-email`. Un login con l'email non verificata offre «Reinvia email» |
| `/check-email?email=` | Pubblico | «Controlla la tua posta»: avvisa che il link è in arrivo, con «Reinvia email» e «Email sbagliata? Crea di nuovo l’account» |
| `/verify-email?token=` | Pubblico | Il link dell'email: chiede di quale account si tratta (anteprima, che non lo consuma), mostra «Conferma l’account «utente»» e chiede la password di quella registrazione; con essa verifica l'email, avvia la sessione e porta a `/` (o a `/developer` se è uno studio). Una password errata si può ritentare (5 errori annullano il link); un link usato o scaduto permette di richiederne un altro. Rimuove il token dall'URL |
| `/cart` | Utente | Carrello; «Procedi al pagamento» crea l'ordine PENDING |
| `/orders`, `/orders/:id` | Utente | Storico e pagamento (200 → PAID, 402 → FAILED) |
| `/library`, `/wishlist` | Utente | Collezioni dell'utente; «Gioca» simula una sessione da 30 a 90 minuti (`POST /library/{id}/play`) |
| `/friends` | Giocatore | Ricerca (da 2 caratteri), richieste ricevute e inviate ed elenco degli amici. Nascosta a developer e admin |
| `/users/:username` | Utente | Profilo di un giocatore: nome, avatar e pulsante di amicizia in base alla relazione. La libreria con le ore giocate e gli amici compaiono solo se il profilo è pubblico, se siete amici o se è il tuo |
| `/settings` | Utente | Profilo (`PATCH /users/me`), interruttore «Profilo pubblico» e lingua delle email (es, en, it) |
| `/developer` | DEVELOPER | I suoi giochi: pubblicare, ritirare e applicare uno sconto in blocco |
| `/developer/games/:id` | DEVELOPER | Modulo del gioco (`new` per uno nuovo): creare o modificare con `version` per il lock ottimistico, descrizione in es/en/it, importare da Steam tramite AppID e caricare la copertina come file (JPEG, PNG o WebP fino a 5 MB, se il server ha Cloudinary) |
| `/admin` | ADMIN | Moderazione dei giochi (può anche caricare copertine), ruoli e attivazione degli utenti, regalare giochi e CRUD dei generi |

## Demo

Minigiochi propri (senza sprite, musica né nomi dei giochi reali) ispirati a quattro giochi del catalogo. Si aprono da `/demos` e dal pulsante «Prova la demo» della scheda del gioco (`GameDetail`), ed entrambi usano lo stesso player (`demos/DemoPlayer.tsx`). Chiunque può giocare, anche senza sessione. **Non c'è backend e non si salva nulla**: né punteggi, né `localStorage`. Al termine la schermata finale propone «Aggiungi al carrello» (oppure «Ce l’hai già» / «Nel carrello · Vai al pagamento», in base a `/library/status`).

| Slug | Gioco | Controlli | Fine |
|---|---|---|---|
| `celeste` | Platform: tre schermate fino alla vetta | ← → muoversi · Spazio saltare · X spinta in aria | Raggiungere la vetta (tempo e cadute) |
| `vampire-survivors` | Sopravvivenza: l'attacco è automatico | Frecce o WASD | Resistere 60 s o perdere tutte le 6 vite |
| `rocket-league` | Calcio 2D contro un'auto avversaria | ↑ accelerare · ↓ frenare · ← → sterzare · Spazio turbo | Partita di 60 s |
| `balatro` | Carte: poker a punti (obiettivo 300) | Mouse, tastiera o tocco | Superare l'obiettivo o restare senza mani (5 mani, 2 scarti) |

- Una demo compare solo se `GET /api/games/{slug}` risponde (gioco pubblicato). La registrazione per slug è in `demos/registry.ts`; ogni minigioco viene caricato con `React.lazy` all'apertura.
- Ogni minigioco separa la logica pura (`logic.ts`: stato + `step(dt, input)`) dal disegno su `<canvas>` (`*Demo.tsx`). Balatro usa pulsanti del DOM.
- `demos/loop.ts`: ciclo con `requestAnimationFrame` che viene annullato alla pausa o allo smontaggio, `dt` limitato a 0,05 s e lettura della tastiera (frecce, WASD, Spazio, X) senza scroll né scorciatoie globali.
- Va in pausa da sola quando la finestra o la scheda perde il focus e resta in pausa finché non si preme «Continua». Esc chiude.
- Su mobile (`pointer: coarse`) Balatro si gioca con i tocchi e le altre tre mostrano «Demo disponibile su computer: serve la tastiera».

**Lingua dell'interfaccia:** spagnolo, inglese o italiano, con le bandiere nell'intestazione (`LanguageSwitcher`), anche senza sessione. Viene ricordata in `localStorage` (`arcade.lang`); la prima volta si usa la lingua del browser, o lo spagnolo se non è una delle tre. I testi sono in `src/i18n/messages.ts` e l'API riceve la stessa lingua in `Accept-Language`.

**Assistente.** Con la sessione, un pulsante flottante apre la chat (`AssistantChat`): testo più fino a 4 schede di giochi che aprono la loro scheda. La conversazione vive in memoria e si perde al ricaricamento; si inviano al massimo gli ultimi 10 messaggi. Un 503 mostra «L’assistente non è disponibile in questo momento» e un 429, l'avviso del limite (20 messaggi all'ora).

**Sessione.** Il token di accesso (15 min) e il refresh token (7 giorni) si salvano in `localStorage` (`arcade.refresh` è la chiave del secondo). Se l'API risponde 401 con un token salvato, `api/client.ts` chiede **un solo** refresh (più richieste simultanee condividono lo stesso), salva i due nuovi token e ripete la richiesta. Se il backend rifiuta il refresh (400 o 401), elimina i due token, chiude la sessione e apre il login; un errore temporaneo (5xx, 429 o rete) non la cancella. Salvare il refresh in `localStorage` lascia una limitazione nota rispetto a XSS (vedi il [README principale](../README.md#limitazioni-note)).

## Deploy

`vercel.json` prepara il deploy su Vercel (*Root Directory* = `frontend`). Definisci `VITE_API_URL` con l'URL pubblico del backend e aggiungi l'URL di Vercel a `CORS_ALLOWED_ORIGINS` nel backend. I passaggi completi sono nel [README principale](../README.md#deploy).
