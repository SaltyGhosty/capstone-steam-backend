# Capstone Steam

Negozio di videogiochi ispirato a Steam: un'API REST in Spring Boot e un frontend in React ([`frontend/`](frontend/)) realizzato a partire da un design di Figma Make. Offre catalogo con ricerca avanzata, pannello per sviluppatori, carrello, wishlist, libreria, recensioni in stile Steam (👍/👎), amici e profilo, un assistente che consiglia giochi e acquisti con pagamento simulato. Le email di conferma vengono inviate in modo asincrono.

È il progetto finale (capstone) del corso Full Stack di **Epicode**.

| | |
|---|---|
| **Backend** | Java 25 · Spring Boot 4.1 · Spring Security + JWT · Spring Data JPA / Hibernate 7 · Flyway · PostgreSQL · Spring Mail (Gmail in locale, API di Brevo in produzione) · Cloudinary (copertine) · OpenRouter (assistente) · JUnit 5 + Mockito + H2 · Maven |
| **Frontend** | React 19 · Vite 8 · Tailwind CSS v4 · React Router 7 · TypeScript · Vitest + Testing Library |
| **Test** | Backend: 518 test, con JaCoCo che richiede almeno l'80 % di righe coperte · Frontend: 204 test in 26 file · CI su GitHub Actions |
| **API** | Swagger UI su `/swagger-ui.html` · 104 richieste nella [collezione Postman](postman/Capstone-Steam.postman_collection.json) |

---

## Indice
1. [Funzionalità](#funzionalità)
2. [Requisiti tecnici e dove sono soddisfatti](#requisiti-tecnici-e-dove-sono-soddisfatti)
3. [Avvio del progetto](#avvio-del-progetto)
4. [Frontend](#frontend)
5. [Architettura](#architettura)
6. [Database (Flyway da V1 a V15)](#database-flyway-da-v1-a-v15)
7. [API](#api)
8. [Test](#test)
9. [Deploy](#deploy)
10. [Decisioni tecniche](#decisioni-tecniche)

---

## Funzionalità

**Tre ruoli.** Nessuno può registrarsi come ADMIN: il primo viene creato all'avvio e i successivi li nomina un admin.

| Ruolo | Può… |
|---|---|
| **Giocatore** (`ROLE_USER`) | Cercare nel catalogo, gestire wishlist e carrello, acquistare, vedere la propria libreria, recensire i giochi che possiede (👍/👎), stringere amicizia con altri giocatori e chiedere consigli all'assistente |
| **Developer** (`ROLE_DEVELOPER`) | Tutto quanto sopra, più creare, modificare, pubblicare e ritirare **i propri** giochi (con la copertina incollata come URL o caricata come file) e applicare sconti in blocco |
| **Admin** (`ROLE_ADMIN`) | Moderare il catalogo (ritirare giochi), gestire i generi e gli utenti (ruoli e attivazione), regalare giochi ed eliminare recensioni |

**Flusso di acquisto:**

1. Si aggiungono giochi al carrello.
2. `POST /api/orders` crea un ordine **PENDING** con i prezzi congelati in quel momento.
3. `POST /api/orders/{id}/pay`:
   - Se il pagamento viene approvato: l'ordine passa a **PAID**, i giochi entrano nella libreria, le vendite aumentano, carrello e wishlist vengono svuotati e viene inviata un'email di ricevuta con la copertina e il prezzo pagato di ciascun gioco, più il totale.
   - Se viene rifiutato: l'ordine passa a **FAILED** (HTTP 402) e viene inviata un'email di avviso.

I pagamenti sono **simulati**: una carta che termina con `0000` viene sempre rifiutata, così si può mostrare il caso di errore.

**Email nella lingua dell'utente.** Le tre email (benvenuto, ricevuta e pagamento rifiutato) arrivano in spagnolo, inglese o italiano, in base alla lingua salvata nel profilo (`locale`).
- Alla registrazione, il frontend invia la lingua del browser (se non è `es`, `en` né `it`, usa `es`).
- In *Impostazioni → Profilo pubblico*, il selettore «Lingua delle email» permette di cambiarla.
- Gli importi restano in euro, con il formato di ciascuna lingua.
- Anche l'interfaccia web è nelle tre lingue: si sceglie con le bandiere nell'intestazione e viene ricordata nel browser.

**Recensioni in stile Steam.** Una recensione è 👍 Consigliato o 👎 Non consigliato, più un testo facoltativo; le stelle non ci sono più.
- Ogni recensione salva la propria lingua (`es`, `en` o `it`): quella dell'interfaccia al momento della scrittura oppure, se non arriva, la lingua dell'autore.
- Il gioco mostra la **percentuale di recensioni positive** (arrotondata per difetto) e un'etichetta con le regole di Steam (`reviewScore`): «Molto positive», «Perlopiù positive», «Nella media»… Con meno di 10 recensioni non c'è etichetta (`NONE`) e si conta solo il numero. Le etichette «Estremamente…» richiedono 500 recensioni, quindi nella demo non compaiono.
- Il punteggio conta le recensioni di tutte le lingue, come Steam. L'elenco nella scheda viene filtrato per la lingua dell'interfaccia, con un controllo «Tutte le lingue» (`GET /api/games/{slug}/reviews?language=`).
- Il catalogo filtra con `minPositivePercent` e ordina i «più apprezzati» in base a quella percentuale; i giochi con meno di 10 recensioni non superano il filtro e finiscono in fondo.

**Amici e profilo.** Partecipano solo gli account di **giocatore** (senza ruolo DEVELOPER né ADMIN) e attivi.
- Si cerca per nome (da 2 a 50 caratteri), si invia una richiesta e l'altro la accetta o la rifiuta. Si può annullare una richiesta inviata ed eliminare un amico. Se A chiede a B e B aveva già chiesto ad A, vengono accettate entrambe.
- Il profilo (`/users/:username`) mostra sempre nome, avatar e data di iscrizione. La libreria (con le ore giocate) e l'elenco degli amici sono visibili solo se il profilo è **pubblico**, se chi guarda è **amico** o se è il proprietario stesso. Il profilo è privato per impostazione predefinita (`profilePublic`, in *Impostazioni*). Non si vedono mai email, ordini, carrello né wishlist. La regola viene applicata nel backend.

**Notifiche e chat tra amici.** Un numero accanto a «Amici» (menu laterale, menu mobile e menu dell'avatar) somma le **richieste di amicizia ricevute** e i **messaggi non letti**; oltre 99 mostra «99+».
- Quando arriva qualcosa di nuovo mentre si usa il sito compare **un solo avviso** cliccabile (concreto, oppure un riepilogo se arrivano più cose insieme) che porta alla richiesta o alla conversazione. La prima consultazione dopo l'accesso non avvisa.
- Non è in tempo reale: il frontend interroga `GET /api/friends/activity` **ogni 30 secondi** e ogni volta che si torna alla scheda (con una conversazione aperta, i suoi messaggi ogni 4 secondi). Con il piano gratuito di Render è più semplice e affidabile di una connessione permanente. Solo i giocatori consultano.
- **Chat di testo uno a uno, solo tra amici**, dentro la pagina Amici: da 1 a 1.000 caratteri, senza immagini né «sta scrivendo…». Si salva la cronologia; i messaggi ricevuti si segnano come letti quando si apre la conversazione. Massimo **30 messaggi al minuto** per utente (429 `chat.rateLimited`).
- **Se non sono più amici**, la conversazione resta e si può **leggere**, ma non scrivere (403 `chat.notFriends`); se tornano amici si può riprendere. Gli account in pausa, disattivati o non giocatori non contano nel numero e non compaiono nell'elenco.
- Chi cancella l'account (anonimizzazione) perde **tutte le sue conversazioni**, come le recensioni.
- Ordine degli errori nell'invio: 400 testo vuoto o oltre 1.000 caratteri · 400 messaggio a se stessi · 403 `friend.playersOnly` · 404 · 403 `chat.notFriends` · 429.

**Verifica dell'email.** Creare l'account non avvia la sessione: arriva **una sola email** («Benvenuto su ARCADE! Conferma la tua email», nella lingua scelta) con un link a `/verify-email?token=…`.
- Cliccandolo l'account risulta verificato e accede subito con la sua sessione. Senza verifica, il login risponde **403** `auth.emailNotVerified` (purché la password sia corretta; con una password errata resta 401, così non si rivela quali account esistono).
- Il link è **monouso** e scade dopo 24 h. Nel database viene salvato solo il suo SHA-256 e il token in chiaro non viene mai scritto nel log con l'invio email attivo (senza `MAIL_USERNAME`, in sviluppo, il link compare nel log per poterlo provare).
- **Ogni link porta i dati della propria registrazione** (utente, password cifrata, nome, lingua e tipo di account) e **chiede la password di quella registrazione**. La pagina `/verify-email` chiede di quale account è il link (`POST /api/auth/verify-email/preview`, che non lo consuma), mostra «Conferma l'account «utente»» e chiede la password; con essa i dati del link vengono applicati all'account e gli altri link smettono di valere. Così nessuno può appropriarsi di un account registrando prima la sua email, nemmeno con lo stesso utente: senza la password della registrazione, il suo link non vale. Una password errata non consuma il link (400 `auth.verificationPasswordInvalid`), ma dopo 5 errori il link viene annullato.
- «Reinvia email» (`POST /api/auth/resend-verification`) risponde sempre 204, che l'account esista o no. Registrazione e reinvio condividono un massimo di **3 email all'ora per indirizzo** (poi 429; la registrazione consuma quota solo quando viene accettata). Il reinvio manda un'email per **ogni registrazione in sospeso** di quell'indirizzo (fino a 3, ciascuna con il nome del proprio account) e consuma una sola unità del limite.
- Registrarsi con l'email di un account **non verificato** risponde 201 come una nuova registrazione ma **non tocca l'account**: emette soltanto un altro link con i dati di quella richiesta. Un nome utente occupato da un account non verificato viene liberato se quell'account ha più di 24 h, non ha dati (ordini, libreria, recensioni, giochi, amicizie né sessioni) e non ha ruoli diversi da USER/DEVELOPER; altrimenti 409. Se al momento della verifica il nome utente del link è stato preso da un altro account, 409 e il link resta inutilizzato. Un account verificato non viene mai toccato.
- Un account disattivato da un admin che verifica la propria email risulta verificato ma riceve 403 e non gli viene emessa alcuna sessione. Registrazione e verifica leggono la riga dell'utente con lock pessimistico.
- Un admin può verificare un account a mano (`POST /api/admin/users/{id}/verify-email`): lo usano il seed e Postman. Se l'email ha più registrazioni richieste distinte (contano anche i link annullati o scaduti) risponde 409 `auth.verificationAmbiguous` e non verifica nulla; con una sola applica quella.
- **Limite per IP** di 60 richieste al minuto (gli IPv6 sono raggruppati per prefisso /64; i 429 includono l'intestazione `Retry-After`) su `/register`, `/resend-verification`, `/verify-email` e `/verify-email/preview`. I 5 tentativi di password sono **per registrazione** (utente + password), non per link: un link di reinvio eredita il massimo di errori e gli errori si contano su tutti i link della registrazione, e una registrazione il cui link è stato annullato per 5 errori non viene reinviata (bisogna registrarsi di nuovo).
- Le email ritentano fino a 3 volte gli errori di rete (connessione all'SMTP, oppure 429 e 5xx dell'API di Brevo).
- **In produzione le email passano dall'API HTTP di Brevo** (`BREVO_API_KEY`): dal 26 settembre 2025 Render free blocca il traffico in uscita verso le porte SMTP (25, 465 e 587), quindi Gmail SMTP da Render non si collega mai. In locale, senza quella chiave, si continua a usare Gmail SMTP.

**Cancellare l'account.** Un giocatore o uno studio può cancellare il proprio account dalle Impostazioni (serve la password) e un admin può cancellare quello di chiunque dal pannello. Non si cancella la riga: si **anonimizza**.
- **14 giorni di pausa** (`ACCOUNT_DELETION_GRACE`): l'account non può accedere (403 `auth.accountPendingDeletion` o `auth.accountDeletionByAdmin`, con `deletionScheduledAt`), le sessioni si chiudono subito e il profilo pubblico sparisce dalle ricerche. I giochi di uno studio restano in vendita durante la pausa.
- Se l'ha chiesta **l'utente**, può recuperare l'account accedendo entro il termine («Recupera il mio account», `POST /api/auth/recover-account`). Se l'ha chiesta **un admin**, solo un admin può annullarla.
- Scaduto il termine, un'attività programmata (una volta al giorno e anche all'avvio, `ACCOUNT_DELETION_CRON`) anonimizza l'account: nome utente ed email diventano `deleted_<id>`, si perdono password, nome, avatar, recensioni, amicizie, carrello, wishlist e token. Si **conservano** ordini e libreria (obbligo fiscale in Italia, senza dati che identifichino la persona). I giochi pubblicati di uno studio passano a DELISTED e quelli in bozza si eliminano.
- **Resta sempre almeno un admin attivo**: la regola vale per la cancellazione, per il cambio di ruoli e per la disattivazione, con blocco pessimistico delle righe degli admin e un nuovo conteggio.
- Chi possiede un gioco ritirato (DELISTED) lo vede ancora nella libreria e può aprirne la scheda (come su Steam), con l'avviso «Non è più in vendita»; lo stesso vale per lo studio e per l'admin. Per gli altri la scheda dà 404. Se lo studio è stato cancellato, compare «Studio eliminato» e il gioco non si può ripubblicare (409 `game.developerDeleted`).
- Quattro email (es/en/it, solo testo): cancellazione programmata dall'utente, programmata da un admin, annullata e account eliminato.

**Sessione con refresh token.** Il token di accesso dura **15 minuti** e il refresh token **7 giorni**.
- Il login e la verifica dell'email restituiscono entrambi (la registrazione, solo se la verifica è disattivata). `POST /api/auth/refresh` scambia il refresh con una nuova coppia (**rotazione**: il precedente viene revocato) e `POST /api/auth/logout` chiude la sessione.
- Nel database viene salvato solo lo SHA-256 del refresh token. Se ne arriva uno già usato (possibile furto), si revoca **tutta la sua famiglia**, cioè tutti i token concatenati a partire dallo stesso login.
- Disattivare un utente o cambiargli i ruoli revoca i suoi refresh token.
- Il frontend rinnova la sessione solo in caso di 401 (una sola volta, e più richieste simultanee condividono lo stesso refresh).

**Copertine su Cloudinary.** Il developer proprietario del gioco e l'admin possono **caricare** la copertina come file (`POST /api/developer/games/{id}/cover` e `POST /api/admin/games/{id}/cover`, multipart con il campo `file`).
- Solo JPEG, PNG o WebP, riconosciuti dai byte del file (non dall'estensione) e fino a 5 MB.
- Viene caricata su Cloudinary e si salva la `secure_url` (https) in `coverUrl`. Incollare un URL continua a funzionare.
- Senza `CLOUDINARY_URL` il caricamento si **disattiva da solo**: `GET /api/uploads/covers/status` risponde `{"enabled": false}`, il frontend nasconde il pulsante e il resto dell'app funziona uguale.

**Assistente di consigli.** Un pulsante flottante apre una chat: il giocatore scrive degli aggettivi («cupo, difficile, con una bella storia») e l'assistente risponde con un breve testo e fino a 4 schede di giochi **del catalogo**.
- `POST /api/recommendations/chat` (con sessione) chiama [OpenRouter](https://openrouter.ai) dal backend: la chiave non arriva mai al browser. Gli id inventati dal modello che non esistono o non sono pubblicati vengono scartati.
- Limite di 20 messaggi all'ora per utente, in memoria. Messaggi fino a 500 caratteri e un massimo di 10 per richiesta.
- Senza `OPENROUTER_API_KEY` (o senza rete o senza quota) risponde **503** con un ProblemDetail e la chat mostra «L’assistente non è disponibile in questo momento». Il negozio non si rompe.
- Il modello predefinito è uno `:free` di OpenRouter. Questi modelli richiedono di **attivare un'opzione di privacy** nell'account OpenRouter (quella che consente di usare i modelli gratuiti); senza di essa, anche l'assistente risponderà 503.

## Requisiti tecnici e dove sono soddisfatti

| Requisito | Implementazione |
|---|---|
| Spring Security + JWT stateless | [SecurityConfig](src/main/java/com/capstone/steam/security/SecurityConfig.java), [JwtService](src/main/java/com/capstone/steam/security/JwtService.java) (HS256, jjwt 0.13) e [JwtAuthFilter](src/main/java/com/capstone/steam/security/JwtAuthFilter.java) |
| **Criteria API** per i filtri del catalogo | [GameCriteriaRepository](src/main/java/com/capstone/steam/game/search/GameCriteriaRepository.java): 9 filtri opzionali e 6 ordinamenti, con una subquery per i generi e paginazione nel database |
| **UPDATE atomici scritti a mano** | [GameRepository](src/main/java/com/capstone/steam/game/GameRepository.java) (`incrementSalesCount`, `applyReviewDelta`, `bulkDiscountForDeveloper`), [OrderRepository](src/main/java/com/capstone/steam/order/OrderRepository.java) (`markPaid`, `closePending`) e [LibraryRepository](src/main/java/com/capstone/steam/library/LibraryRepository.java) (`addPlaytime`) |
| Flyway da V1 a V4 (e da V5 a V15) | [db/migration](src/main/resources/db/migration) (vedi [sotto](#database-flyway-da-v1-a-v15)) |
| Spring Mail + Gmail SMTP | [EmailService](src/main/java/com/capstone/steam/notification/EmailService.java), [EmailTemplates](src/main/java/com/capstone/steam/notification/EmailTemplates.java) e [MailTexts](src/main/java/com/capstone/steam/notification/MailTexts.java) (testi in es, en e it) |
| Spring Events **AFTER_COMMIT** + `@Async` con **pool dedicato** | [MailListener](src/main/java/com/capstone/steam/notification/MailListener.java) e [AsyncConfig](src/main/java/com/capstone/steam/config/AsyncConfig.java) (`mailExecutor`, thread `mail-*`) |
| JUnit 5 + Mockito + H2 | [src/test](src/test/java/com/capstone/steam): test unitari, di repository, di integrazione HTTP e di concorrenza |
| PostgreSQL + Docker Compose | [docker-compose.yml](docker-compose.yml) (o un PostgreSQL locale) |
| Frontend | [frontend/](frontend/): React che consuma tutta l'API ([dettagli](frontend/README.md)) |

## Avvio del progetto

### Requisiti
- **JDK 25.** Se hai un altro JDK come predefinito, punta `JAVA_HOME` al 25 prima di usare `mvnw`.
- **PostgreSQL 15 o superiore**, locale o con Docker. Non serve installare Maven: si usa `mvnw`.

### 1. Database
```bash
# Opzione A: PostgreSQL locale
psql -U postgres -c "CREATE DATABASE capstone_steam;"

# Opzione B: Docker (espone la porta 5433 per non andare in conflitto con un Postgres locale)
docker compose up -d
```

### 2. Configurazione
```bash
cp .env.example .env    # e compila i valori
```

| Variabile | Obbligatoria | Descrizione |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | ✅ | Connessione a PostgreSQL (con Docker: `jdbc:postgresql://localhost:5433/capstone_steam`) |
| `JWT_SECRET` | ✅ | Chiave in Base64 di almeno 32 byte. L'app non parte senza |
| `JWT_EXPIRATION` | Opzionale | Durata del token di accesso, in formato ISO-8601 (predefinita `PT15M`, 15 minuti) |
| `JWT_REFRESH_EXPIRATION` | Opzionale | Durata del refresh token (predefinita `P7D`, 7 giorni) |
| `ADMIN_USERNAME`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` | Consigliata | Se `ADMIN_PASSWORD` ha un valore, l'admin iniziale viene creato all'avvio |
| `MAIL_USERNAME`, `MAIL_APP_PASSWORD` | Opzionale | Account Gmail e [password per le app](https://myaccount.google.com/apppasswords). Se sono vuote, le email vengono solo scritte nel log |
| `MAIL_FROM_NAME` | Opzionale | Nome con cui arrivano le email e marchio che vi compare (predefinito `ARCADE`) |
| `BREVO_API_KEY` | In produzione | Chiave dell'API di [Brevo](https://www.brevo.com) (piano gratuito, 300 email al giorno). Se c'è, le email escono via HTTPS e non via SMTP: è necessaria su Render free, che blocca le porte SMTP. Il mittente (`MAIL_FROM` o `MAIL_USERNAME`) deve essere verificato in Brevo |
| `MAIL_TIMEOUT` | Opzionale | Millisecondi di attesa per connessione, lettura e scrittura con l'SMTP (predefinito `15000`). Gli errori di connessione vengono ritentati fino a 3 volte (pause di 2 s e 6 s) |
| `EMAIL_VERIFICATION_REQUIRED` | Opzionale | Predefinito `true`: creare un account invia un'email con un link e non si può accedere finché non lo si clicca. Con `false` gli account nascono verificati e la registrazione restituisce la sessione (lo usano solo i test) |
| `AUTH_IP_LIMIT_PER_MINUTE` | Opzionale | Richieste al minuto per IP a registrazione, reinvio e verifica dell'email (predefinito `60`; poi 429 `auth.tooManyRequests` con `Retry-After`). In produzione (`prod`) l'IP reale proviene da `X-Forwarded-For` (`server.forward-headers-strategy: native`) |
| `EMAIL_VERIFICATION_TTL` | Opzionale | Validità del link di verifica, in formato ISO-8601 (predefinita `P1D`, 24 ore) |
| `ACCOUNT_DELETION_GRACE` | Opzionale | Pausa prima di anonimizzare un account di cui è stata chiesta la cancellazione, in formato ISO-8601 (predefinita `P14D`, 14 giorni) |
| `ACCOUNT_DELETION_CRON` | Opzionale | Espressione cron (6 campi) dell'attività giornaliera che anonimizza le cancellazioni scadute (predefinita `0 15 4 * * *`, ogni giorno alle 04:15). Parte anche all'avvio, perché su Render free il server dorme e il cron potrebbe non scattare |
| `FRONTEND_URL` | Opzionale | URL del negozio per il link di verifica e il logo delle email (le altre email non hanno pulsanti). Se manca, si usa la prima origine di `CORS_ALLOWED_ORIGINS` |
| `SERVER_PORT` | Opzionale | Predefinita `PORT` (la imposta Render) oppure 8080 (`.env.example` usa 8081) |
| `PAYMENT_FAILURE_RATE` | Opzionale | Probabilità di rifiuto casuale nelle demo (da 0.0 a 1.0, predefinita 0) |
| `STEAM_SYNC_ENABLED` | Opzionale | Sincronizza prezzo e sconto con l'API pubblica di Steam all'avvio e ogni 6 h (predefinito `true`); l'admin può anche lanciarla a mano |
| `STEAM_SYNC_CRON` | Opzionale | Frequenza della sincronizzazione con Steam (cron di Spring, predefinita `0 0 */6 * * *`) |
| `CORS_ALLOWED_ORIGINS` | Opzionale | URL del frontend (predefinito `http://localhost:5173`) |
| `CLOUDINARY_URL` | Opzionale | `cloudinary://<api_key>:<api_secret>@<cloud_name>`, da un account gratuito di Cloudinary. Se è vuota, il caricamento delle copertine si disattiva da solo (si possono comunque incollare URL)  La API key richiede il permesso di caricamento (azione *create*); una chiave con permessi limitati dà 403 |
| `OPENROUTER_API_KEY` | Opzionale | Chiave da [openrouter.ai/keys](https://openrouter.ai/keys). Se è vuota, l'assistente si disattiva da solo e risponde 503 senza fare alcuna chiamata di rete |
| `OPENROUTER_MODEL` | Opzionale | Modello dell'assistente: uno o più separati da virgole, che OpenRouter prova in ordine se uno è saturo (predefinito `minimax/minimax-m3.1-flash:free,google/gemma-4-31b-it:free`). I modelli `:free` richiedono di attivare un'opzione di privacy nell'account OpenRouter |
| `app.openrouter.global-per-hour` / `cooldown` | In `application.yml` | Tetto di messaggi all'ora tra tutti gli utenti (200) e pausa dopo un 429 di OpenRouter (5 min), per non esaurire la quota gratuita |

### 3. Avviare
```bash
./mvnw spring-boot:run          # Windows: .\mvnw.cmd spring-boot:run
```
Flyway crea lo schema all'avvio e Hibernate lo convalida (`ddl-auto=validate`). Per verificare che funzioni: `GET http://localhost:8081/actuator/health` deve restituire `{"status":"UP"}`.

### 4. Provare
Con l'app avviata, **Swagger UI** è su `http://localhost:8081/swagger-ui.html`: il pulsante *Authorize* accetta l'`accessToken` del login.

Puoi anche importare [`postman/Capstone-Steam.postman_collection.json`](postman/Capstone-Steam.postman_collection.json) ed eseguirla con il **Collection Runner**. È un flusso completo: registrazione, refresh e logout, pubblicazione di un gioco, acquisto, recensione, amici e profilo, stato del caricamento delle copertine, assistente (accetta 200 o 503 a seconda che ci sia o meno la chiave di OpenRouter) e, con un account di prova creato dalla collezione, tutta la cancellazione dell'account (richiesta, accesso in pausa, recupero, cancellazione e annullamento da parte dell'admin). I token e gli id si salvano da soli. Per la cartella *Admin*, inserisci la tua `ADMIN_PASSWORD` nella variabile `adminPassword`.

## Frontend

```bash
cd frontend
npm install
npm run dev      # http://localhost:5173 (il backend deve essere sulla 8081)
```

ARCADE è il negozio completo: home con i giochi in evidenza, catalogo con tutti i filtri della Criteria API, scheda del gioco con recensioni 👍/👎 (percentuale, etichetta e filtro per lingua), carrello e pagamento simulato, ordini, libreria (con sessioni di gioco simulate), wishlist, amici e profilo pubblico, impostazioni, la chat dell'assistente, pannello dello studio per i developer (con caricamento delle copertine) e pannello di moderazione per gli admin. Tutto è spiegato in [frontend/README.md](frontend/README.md).

**Utenti demo** (dopo aver eseguito `node scripts/seed-demo.mjs`): `demo_player` (giocatore con libreria, wishlist, carrello e un pagamento rifiutato), gli account degli studi, come `team_cherry` (ha una bozza da pubblicare), e 60 recensori (da `rev_es_01` a `rev_es_20`, e lo stesso con `en` e `it`), tutti con la password `password123`; e `admin` / l'`ADMIN_PASSWORD` del tuo `.env`.

## Architettura

Il codice è organizzato **per funzionalità** e ognuna segue i livelli `Controller → Service (@Transactional) → Repository`. I controller restituiscono solo DTO (`record`), mai entità.

```
com.capstone.steam
├── auth/          registrazione, login, refresh token (RefreshTokenService) e logout
├── security/      JWT, filtro, SecurityConfig e risposte 401/403
├── user/          profilo, amministrazione degli utenti e admin iniziale
├── genre/         generi
├── game/          CRUD del developer, moderazione dell'admin e catalogo pubblico
│   └── search/    Criteria API (GameCriteriaRepository, GameSearchCriteria, GameSort)
├── cart/  wishlist/  library/  review/
├── friend/        amicizie, ricerca di giocatori e profilo pubblico
├── cover/         caricamento delle copertine (CoverService, CloudinaryCoverStorage)
├── recommendation/ assistente di consigli (OpenRouterChatModel, ChatRateLimiter)
├── steamstore/    importazione di giochi e sincronizzazione dei prezzi con lo store di Steam
├── order/         ordini, checkout e pagamenti
│   └── payment/   PaymentGateway + SimulatedPaymentGateway
├── notification/  eventi, MailListener, EmailService, EmailTemplates e MailTexts
├── config/        AsyncConfig (pool mailExecutor) e OpenApiConfig (Swagger)
└── common/        GlobalExceptionHandler (ProblemDetail), eccezioni, PageResponse e Slugs
```

**Flusso di un pagamento approvato:**
```
POST /api/orders/{id}/pay
 └─ OrderService.pay()                               ── una transazione ──
     ├─ PaymentGateway.charge()                          (simulato)
     ├─ OrderRepository.markPaid()   UPDATE … WHERE status='PENDING'   → 0 righe = 409
     ├─ libreria (INSERT) · GameRepository.incrementSalesCount()       (UPDATE atomico)
     ├─ si svuotano carrello e wishlist
     └─ publishEvent(OrderPaidEvent)
                                                      ── COMMIT ──
 MailListener.onOrderPaid()   @TransactionalEventListener(AFTER_COMMIT)
                              @Async("mailExecutor")  → thread mail-N → Brevo (HTTPS) o Gmail SMTP
```
La risposta HTTP non attende l'SMTP. Se la transazione fa rollback, non viene inviata alcuna email. E se l'invio dell'email fallisce, l'errore viene scritto nel log senza incidere sull'acquisto.

**La ricevuta di acquisto** elenca ogni gioco con una miniatura della copertina (64×36), il titolo e il prezzo pagato con lo sconto (`OrderItem.unitPrice`), e sotto il totale. `OrderPaidEvent` porta con sé queste righe già risolte: vengono costruite dentro la transazione di `pay()`, perché il listener gira dopo il commit e su un altro thread, dove `Game` (LAZY) non si può più caricare. La copertina viene collegata solo se l'URL inizia con `http://` o `https://` (`EmailTemplates.safeImageUrl`); altrimenti resta una cella vuota della stessa larghezza. Tutto il testo viene sottoposto a escape con `HtmlUtils`.

**La lingua delle email** deriva da `users.locale`. I testi di ciascuna lingua sono nel record `MailTexts` (una costante per lingua), così il compilatore impone che nessuna resti senza traduzione. I tre eventi portano il `locale`, letto dall'utente dentro la transazione. Se manca o non viene riconosciuto, l'email esce in spagnolo.

## Database (Flyway da V1 a V15)

| Migrazione | Contenuto |
|---|---|
| **V1** `base_schema` | `roles` (con dati iniziali), `users`, `user_roles`, `genres` (12 iniziali), `games` (prezzo, sconto, stato, contatori e `version`) e `game_genres` |
| **V2** `storefront` | `library_entries`, `cart_items`, `wishlist_items` e `reviews` (con `UNIQUE(user, game)` in tutte) |
| **V3** `orders_payments` | `orders` (PENDING, PAID, FAILED o CANCELLED), `order_items` (prezzo congelato), `payments` e `library_entries.order_id` |
| **V4** `catalog_indexes` | Indici del catalogo. Su PostgreSQL: **trigrammi `pg_trgm` (GIN)** su `lower(title)` per `LIKE '%testo%'` e un indice parziale per i «più venduti» |
| **V5** `user_locale` | `users.locale` (lingua delle email): `es` per impostazione predefinita, con un `CHECK` che ammette solo `es`, `en` o `it` |
| **V6** `game_description_translations` | `games.description_en` e `description_it`: la descrizione in inglese e italiano (quella originale è in spagnolo e fa da riserva) |
| **V7** `steam_link` | `games.steam_app_id` (univoco) e `price_synced_at`: collegamento con lo store di Steam per sincronizzare prezzo e sconto |
| **V8** `steam_style_reviews` | `reviews.language` (`es`, `en` o `it`), eliminati `reviews.rating` e `games.rating_sum`; `rating_count` diventa `review_count` e si aggiunge `positive_count`. Ricalcola i contatori dalle recensioni esistenti |
| **V9** `friendships` | `users.profile_public` (falso per impostazione predefinita) e la tabella `friendships`: una riga per coppia in qualunque direzione (`UNIQUE(user_low_id, user_high_id)`), con stato `PENDING` o `ACCEPTED` |
| **V10** `refresh_tokens` | `refresh_tokens`: SHA-256 del token (mai il token), famiglia, scadenza, data di revoca e sostituto |
| **V11** `friendships_pair_check` | `CHECK ck_friendships_pair`: la coppia `(user_low_id, user_high_id)` deve corrispondere a richiedente e destinatario |
| **V14** `friend_messages` | `friend_messages`: mittente, destinatario, la coppia ordinata (come `friendships`, con un `CHECK`), testo da 1 a 1.000 caratteri e `read_at`, con indici per la conversazione e per i non letti. I messaggi non dipendono dalla riga di `friendships`, quindi restano se finisce l'amicizia |
| **V15** `friend_messages_indexes` | Indici di `friend_messages`: `(user_low_id, user_high_id, id)` per la paginazione e per l'ultimo messaggio di ogni conversazione, e `sender_id` per l'anonimizzazione |
| **V13** `account_deletion` | `users.deletion_scheduled_at` (quando l'account verrà anonimizzato), `deletion_requested_by` (`SELF` o `ADMIN`, con `CHECK`) e `deleted_at` (account già anonimizzato), più un indice su `deletion_scheduled_at` per l'attività programmata. Gli account esistenti restano senza cancellazione in sospeso |
| **V12.1** `username_lower_unique` | Indice univoco su `lower(username)`: il nome utente è univoco senza distinguere maiuscole e minuscole anche nel database. PostgreSQL usa un indice per espressione; H2 (test) una colonna generata `username_lower` con un `UNIQUE`, perché H2 non supporta gli indici per espressione |
| **V12** `email_verification` | `users.email_verified` (gli account già esistenti restano verificati) e la tabella `email_verification_tokens`: SHA-256 del link (mai il token), scadenza, data di utilizzo, i dati della registrazione che lo ha generato (utente, password cifrata, nome, lingua e tipo di account) e il contatore delle password errate |

La traccia del corso richiede V1 a V4; V5 a V15 sono state aggiunte in seguito (lingua delle email, traduzioni, Steam, recensioni in stile Steam, amici, refresh token, integrità della coppia di amici, verifica dell'email, cancellazione dell'account e messaggi tra amici con i loro indici). V1 a V3 e V5 a V15 sono SQL portabile; V4 e V12.1 hanno una variante per motore. V4 ha due versioni ([postgresql](src/main/resources/db/migration/postgresql) e [h2](src/main/resources/db/migration/h2)), perché H2 non supporta gli indici a trigrammi né quelli parziali. Flyway sceglie quella adatta con `locations: …/common, …/{vendor}`.

Le regole di integrità sono garantite anche dal database, non solo dal codice Java: `CHECK` (prezzo ≥ 0, sconto da 0 a 100, `positive_count <= review_count`, lingua e stati validi), `UNIQUE` e chiavi esterne con `ON DELETE` pensato caso per caso.

## API

L'autenticazione è `Authorization: Bearer <accessToken>`. Il token si ottiene alla registrazione o al login e dura 15 minuti; con il refresh token (7 giorni) si rinnova senza effettuare di nuovo l'accesso.

| Metodo | Percorso | Accesso | Descrizione |
|---|---|---|---|
| POST | `/api/auth/register` | pubblico | Registrazione (`accountType`: PLAYER o DEVELOPER; `locale` facoltativo: `es`, `en` o `it`, predefinito `es`) → 201 con `{id, email, verificationRequired: true}` e un'email con il link di verifica (senza sessione). Con `EMAIL_VERIFICATION_REQUIRED=false` restituisce `accessToken` e `refreshToken`. 400 se il nome utente inizia per `deleted_` o l'email finisce con `@deleted.invalid` (identificatori riservati agli account anonimizzati) |
| POST | `/api/auth/verify-email/preview` | pubblico | `{token}` → `{username}` se il link è ancora valido (non lo consuma); 400 se è usato, scaduto o inesistente |
| POST | `/api/auth/verify-email` | pubblico | `{token, password}` (la password di quella registrazione) → verifica l'email e restituisce `accessToken` e `refreshToken`. 400 se il link non vale (`auth.verificationInvalid`) o la password non è quella della registrazione (`auth.verificationPasswordInvalid`, 5 errori annullano il link); 409 se il nome utente del link è stato preso da un altro account; 403 se l'account è in pausa per la cancellazione (l'email resta verificata, ma senza sessione) |
| POST | `/api/auth/resend-verification` | pubblico | `{email}` → sempre 204 (non rivela se l'account esiste). 429 oltre le 3 email all'ora per indirizzo (registrazione inclusa) |
| POST | `/api/auth/login` | pubblico | Login con username **o** email → `accessToken` e `refreshToken`. 403 con `code: auth.emailNotVerified` se la password è corretta ma manca la verifica dell'email. Con la cancellazione in sospeso e la password corretta risponde 403 `auth.accountPendingDeletion` (l'ha chiesta l'utente: si può recuperare) o `auth.accountDeletionByAdmin`, entrambi con `deletionScheduledAt` |
| POST | `/api/auth/recover-account` | pubblico | `{login, password}` → annulla la cancellazione e restituisce una sessione (`AuthResponse`), solo se l'ha chiesta l'utente stesso. 403 `auth.accountDeletionByAdmin` se l'ha chiesta un admin; 200 con sessione, come il login, se non c'è nessuna cancellazione in sospeso; 401 come il login solo se la password è errata o l'account non esiste |
| POST | `/api/auth/refresh` | pubblico | `{refreshToken}` → nuova coppia (rotazione). 401 se è scaduto, revocato o già usato (in tal caso si revoca la sua famiglia), e anche se l'account è disattivato, in pausa per la cancellazione o già eliminato |
| POST | `/api/auth/logout` | pubblico | `{refreshToken}` → 204; revoca la sessione (idempotente) |
| GET / PATCH | `/api/users/me` | utente | Vedere e modificare il profilo (inclusi `locale`, la lingua delle email, e `profilePublic`) |
| POST | `/api/users/me/deletion` | utente | `{password}` → 200 `{deletionScheduledAt}`: mette in pausa il proprio account per 14 giorni e chiude le sessioni; poi viene anonimizzato. 400 `user.deletionWrongPassword` se la password è errata; 409 `user.lastAdmin` se è l'ultimo admin attivo |
| GET | `/api/users/search?q=` | utente | Cerca giocatori per nome (da 2 a 50 caratteri) |
| GET | `/api/users/{username}/profile` | utente | Profilo di un giocatore; libreria e amici solo se è pubblico, amico o il proprio |
| GET | `/api/friends` · `/api/friends/requests` | utente | I miei amici (con `unreadMessages`) · richieste ricevute (`incoming`) e inviate (`outgoing`) |
| GET | `/api/friends/activity` | utente | `{incomingRequests, unread, total}`: richieste ricevute e messaggi non letti per mittente; `total` è il numero di «Amici». Solo account visibili; per chi non è un giocatore (studio o admin) risponde 200 con tutto vuoto |
| GET | `/api/friends/conversations` | utente | Le mie conversazioni (al massimo 50): prima quelle con messaggi, poi gli amici senza messaggi; `friend: false` indica un ex amico (sola lettura); per chi non è un giocatore la lista è vuota |
| GET / POST | `/api/friends/{username}/messages` | giocatore | Leggere (`?before=<id>&size=` da 1 a 100, predefinito 30, dal più recente) e inviare `{body}` → 201. 400 testo vuoto, troppo lungo o a se stessi; 403 `chat.notFriends`; 404 se l'altro non è un giocatore visibile; 429 oltre i 30 al minuto. La lettura funziona anche con ex amici e account in pausa, ma solo se c'è uno storico: senza messaggi un non giocatore dà 404 come un utente inesistente |
| POST | `/api/friends/{username}/messages/read` | giocatore | Segna come letti i messaggi **ricevuti** da quella persona (204, idempotente) |
| POST | `/api/friends/requests` | giocatore | `{username}` → 201, richiesta PENDING (409 se esiste già o sono già amici) |
| POST | `/api/friends/requests/{id}/accept` · `/reject` | giocatore | Accettare (200) o rifiutare (204) una richiesta ricevuta |
| DELETE | `/api/friends/requests/{id}` · `/api/friends/{username}` | utente | Annullare una richiesta inviata · eliminare un amico (204) |
| GET | `/api/genres` | pubblico | Generi |
| GET | `/api/games` | pubblico | Catalogo con filtri (vedi sotto) |
| GET | `/api/games/{slug}` | pubblico | Scheda del gioco. Se è DELISTED la vedono solo chi lo possiede, il suo studio e l'admin (gli altri ricevono 404) |
| GET | `/api/games/{slug}/reviews` | pubblico | Recensioni paginate; `?language=es\|en\|it` è facoltativo (senza, compaiono tutte) |
| GET / POST | `/api/developer/games` | developer | I miei giochi / creare (DRAFT) |
| GET / PUT / DELETE | `/api/developer/games/{id}` | developer (proprietario) | Vedere, modificare (con `version` → 409 se non aggiornata) ed eliminare (solo bozze senza vendite) |
| POST | `/api/developer/games/{id}/publish` · `/unpublish` | developer (proprietario) | DRAFT ↔ PUBLISHED |
| POST | `/api/developer/games/discount` | developer | Sconto in blocco sui giochi pubblicati |
| POST | `/api/developer/games/{id}/cover` · `/api/admin/games/{id}/cover` | developer (proprietario) · admin | Caricare la copertina (multipart, campo `file`; JPEG, PNG o WebP fino a 5 MB) → il gioco con il suo `coverUrl`. 400 se non è un'immagine valida, 413 se pesa di più, 503 se Cloudinary non è attivo |
| GET | `/api/uploads/covers/status` | utente | `{"enabled": true\|false}`: indica se il caricamento delle copertine è attivo |
| POST | `/api/recommendations/chat` | utente | Assistente: `{messages:[{role,content}]}` (massimo 10; l'ultimo, dell'utente) → `{reply, games}`. 429 oltre i 20 messaggi all'ora, 503 se l'assistente non è disponibile |
| POST | `/api/developer/games/import` | developer | Importare un gioco dallo store di Steam tramite AppID |
| GET / DELETE | `/api/cart` | utente | Vedere e svuotare il carrello |
| PUT / DELETE | `/api/cart/items/{gameId}` | utente | Aggiungere e rimuovere (idempotenti) |
| GET | `/api/wishlist` · PUT / DELETE `/api/wishlist/{gameId}` | utente | Wishlist |
| GET | `/api/library` | utente | La mia libreria |
| GET | `/api/library/status/{gameId}` | utente | Ce l'ho? È nel carrello o nella wishlist? L'ho recensito? |
| POST | `/api/library/{gameId}/play` | utente | Sessione di gioco simulata: aggiunge `minutes` (da 1 a 1440) al tempo di gioco. 404 se non lo possiedi |
| POST | `/api/orders` | utente | Checkout: carrello → ordine PENDING |
| POST | `/api/orders/{id}/pay` | utente | Pagare → 200 PAID · **402** FAILED · 409 se è già stato elaborato |
| POST | `/api/orders/{id}/cancel` | utente | Annullare un ordine in sospeso |
| GET | `/api/orders` · `/api/orders/{id}` | utente | Storico e dettaglio |
| POST | `/api/reviews` · PUT / DELETE `/api/reviews/{id}` | utente | Recensire un gioco della mia libreria: `{gameId, recommended, body?, language?}` (autore; anche l'admin può eliminare) |
| GET | `/api/admin/users` | admin | Utenti paginati; `?q=` filtra per utente o email (senza distinguere maiuscole e minuscole) |
| PUT | `/api/admin/users/{id}/roles` | admin | Cambiare i ruoli |
| PATCH | `/api/admin/users/{id}/enabled` | admin | Attivare o disattivare un account |
| DELETE | `/api/admin/users/{id}` | admin | Programma la cancellazione dell'account (stessi 14 giorni, ma solo un admin può annullarla) → 200 `UserResponse` con `deletionScheduledAt` e `deletionRequestedBy`. 400 `user.cannotDeleteSelf` su se stesso; 409 `user.lastAdmin` se resterebbe senza admin attivi |
| POST | `/api/admin/users/{id}/deletion/cancel` | admin | Annulla qualsiasi cancellazione in sospeso (idempotente) → 200 `UserResponse` |
| POST | `/api/admin/users/{id}/verify-email` | admin | Dare per verificata l'email dell'account, applicando i dati del suo ultimo link (idempotente) |
| POST | `/api/admin/users/{userId}/library/{gameId}` | admin | Regalare un gioco |
| POST / PUT / DELETE | `/api/admin/genres[/{id}]` | admin | CRUD dei generi |
| GET | `/api/admin/games?status=` | admin | Giochi per stato |
| PATCH | `/api/admin/games/{id}/status` | admin | Ritirare (DELISTED) o ripubblicare |
| POST | `/api/admin/steam/sync` | admin | Sincronizzare subito prezzi e sconti con Steam |

**Ricerca nel catalogo**: tutti i filtri sono opzionali e combinabili.
```
GET /api/games?q=hollow&genres=indie,platformer&minPrice=5&maxPrice=20&onSale=true
              &minPositivePercent=80&releasedAfter=2017-01-01&sort=price_asc&page=0&size=20
```
Valori di `sort`: `newest` (predefinito), `price_asc`, `price_desc`, `top_rated` (per percentuale di recensioni positive), `best_selling` e `title`. I filtri di prezzo usano il prezzo scontato. `minPositivePercent` (da 0 a 100) esclude i giochi con meno di 10 recensioni. Ogni gioco include `reviewCount`, `positivePercent` e `reviewScore`.

**Lingua delle email (`locale`)**: accetta solo `es`, `en` o `it`, in minuscolo. Qualsiasi altro valore risponde 400 con `errors.locale`. Senza `locale`, la registrazione usa `es` e il `PATCH` non lo modifica. Compare sempre nell'utente della risposta (`UserResponse`, anche dentro la risposta di login e registrazione) e non è incluso nel JWT.

**Errori**: seguono tutti il formato standard ProblemDetail (RFC 9457), con i testi nella lingua della richiesta (`Accept-Language`: `it`, `en` o `es`).
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "La richiesta contiene campi non validi",
  "instance": "/api/auth/register",
  "errors": { "email": "must be a well-formed email address" }
}
```

## Test

```bash
./mvnw test      # 518 test (H2 in memoria, non serve PostgreSQL)
./mvnw verify    # + report di copertura in target/site/jacoco/index.html (fallisce se scende sotto l'80 % di righe)
```

| Tipo | Esempi | Cosa verificano |
|---|---|---|
| Unitari (Mockito) | `JwtServiceTest`, `AuthServiceTest`, `OrderServiceTest`, `ReviewServiceTest`, `CartServiceTest` | Regole di business isolate: token manomessi o scaduti, delta delle recensioni, pagamento approvato o rifiutato |
| Repository (`@DataJpaTest` + Flyway reale) | `GameCriteriaRepositoryTest`, `RepositoryIntegrationTest` | Filtri e ordinamenti della Criteria API, UPDATE atomici e vincoli del database |
| Integrazione HTTP (MockMvc) | `AuthIntegrationTest`, `CatalogIntegrationTest`, `CheckoutIntegrationTest`… | Flussi completi con JWT reale, 401/403/404/409/402 e il formato ProblemDetail |
| Email asincrona | `MailEventIntegrationTest`, `EmailServiceTest` | L'email parte **dopo il commit**, su un thread `mail-*`, e **non** parte se c'è rollback. I template fanno l'escape dell'HTML, la ricevuta collega solo copertine `https`, nel log l'email del destinatario compare mascherata (`l***@gmail.com`) e ogni email esce nella lingua dell'utente (es, en o it, con lo spagnolo se manca) |
| Concorrenza | `AtomicUpdateConcurrencyTest`, `RefreshTokenConcurrencyTest` | Thread reali: nessuna vendita va persa, **esattamente 1** pagamento si aggiudica l'ordine e, tra più refresh simultanei con lo stesso token, **solo uno** riceve nuovi token |
| Nuove funzioni | `ReviewScoreTest`, `FriendIntegrationTest`, `RefreshTokenIntegrationTest`, `CloudinaryCoverStorageTest`, `CoverIntegrationTest`, `RecommendationServiceTest` | Etichette delle recensioni con le regole di Steam, visibilità del profilo, rotazione e riutilizzo dei refresh token, validazione dell'immagine dai byte e limite di dimensione (Cloudinary simulato con `MockRestServiceServer`) e l'assistente con il client di OpenRouter simulato: nessun test chiama la rete |

I test usano H2 in modalità PostgreSQL **con le stesse migrazioni Flyway** della produzione; non c'è `ddl-auto=create`.

**Frontend** (`cd frontend && npm test`): 204 test in 26 file, con Vitest e Testing Library. Coprono il client HTTP (token, errori ProblemDetail, 401 e 402, e il rinnovo della sessione con refresh token), i formattatori, il rilevamento della lingua del browser e i flussi di login, registrazione, verifica dell'email, checkout (ordine → pagamento approvato o rifiutato), impostazioni, la scheda del gioco (recensioni 👍/👎 e filtro per lingua), amici e profilo, il caricamento delle copertine, la chat dell'assistente e le quattro demo giocabili sulle pagine reali con `fetch` simulato, oltre alla traduzione dell'interfaccia (stesse chiavi nelle tre lingue, bandiere e `Accept-Language`).

**CI**: [`.github/workflows/ci.yml`](.github/workflows/ci.yml) esegue `./mvnw verify` e i test e la build del frontend a ogni push su `main`.

## Deploy

Il repository include tutto il necessario; mancano solo gli account.

**Rilasciato su Render:** il frontend su https://capstone-steam-web.onrender.com e l'API su https://capstone-steam-api.onrender.com ([Swagger](https://capstone-steam-api.onrender.com/swagger-ui.html)). Il frontend è un *static site* di Render invece di Vercel: build `cd frontend && npm ci && npm run build`, cartella `frontend/dist`, `VITE_API_URL` con l'URL dell'API. Per le rotte di React Router c'è una regola *Rewrite* `/*` → `/index.html` nel pannello di Render; inoltre, il `postbuild` copia `index.html` come `404.html` nel caso la regola manchi.

1. **Tutto su Render con il Blueprint.** Vai su *New → Blueprint* e scegli questo repository: [`render.yaml`](render.yaml) crea a Francoforte il database, l'API (a partire dal [`Dockerfile`](Dockerfile)) e il frontend come *static site* con la regola *Rewrite*. `JWT_SECRET` viene generato da solo. Vengono richiesti `ADMIN_PASSWORD` e, facoltativamente, `MAIL_USERNAME`, `MAIL_APP_PASSWORD`, `MAIL_FROM`, `CLOUDINARY_URL` e `OPENROUTER_API_KEY` (senza queste ultime due, il caricamento delle copertine e l'assistente restano disattivati). Se Render assegna altri nomi ai servizi, adatta `CORS_ALLOWED_ORIGINS` e `VITE_API_URL`.
2. Carica il catalogo demo nel nuovo database: `node scripts/seed-demo.mjs --admin-password <ADMIN_PASSWORD> --api https://<tu-api>.onrender.com`.

Render consente **un solo PostgreSQL gratuito per account**, e scade dopo 30 giorni. Come alternativa per il frontend vale ancora **Vercel**: *Root Directory* = `frontend` (il [`vercel.json`](frontend/vercel.json) configura già build e rotte), `VITE_API_URL` con l'URL dell'API e quell'URL di Vercel in `CORS_ALLOWED_ORIGINS`.

Nel piano gratuito di Render, il backend va in stop dopo 15 minuti senza traffico e la prima richiesta impiega circa 1 minuto a svegliarlo. Per ridurlo, il `Dockerfile` genera durante la build dell'immagine una **cache AOT di Java 25** (`app.aot`, con un avvio di addestramento che non tocca il DB) e parte con `-Xms128m -Xmx384m -XX:+UseSerialGC -XX:TieredStopAtLevel=1`. Su Render l'avvio è sceso da ~200 s a **~59 s** (in locale, da ~5,5 s a ~2,5 s).

### Alternativa: ospitarlo sul proprio PC

[`deploy/local`](deploy/local) pubblica il negozio da questo computer, senza pagare un hosting: **Caddy** serve il sito compilato e inoltra `/api` al backend, e un **tunnel di Cloudflare** lo pubblica sul tuo dominio con HTTPS senza aprire porte sul router. Usa un database dedicato (`arcade`), separato da quello di sviluppo.

```
browser ──HTTPS──▶ Cloudflare ──tunnel──▶ cloudflared ──▶ Caddy :8088 ─┬─ web (frontend/dist)
                                                                       └─ /api ──▶ Spring Boot :8090 ──▶ PostgreSQL (arcade)
```

1. Copia `deploy/local/host.env.example` come `host.env` e inserisci il tuo dominio Cloudflare in `PUBLIC_HOST` (ad es. `arcade.tudominio.com`). I segreti restano nel `.env` della radice.
2. Una sola volta: `deploy\local\setup-host.cmd`. Installa Caddy e cloudflared (winget), crea il database e il tunnel, e punta il dominio a questo PC (apre il browser per accedere a Cloudflare).
3. Avvio: `deploy\local\start-arcade.cmd -Build -Seed` la prima volta; poi basta `start-arcade.cmd` (`-Build` solo dopo aver cambiato il codice). Per provare senza dominio, `-NoTunnel` lo lascia su `http://localhost:8088`.
4. Arresto: `deploy\local\stop-arcade.cmd`. I log restano in `deploy/local/run/logs`.

Il sito è online solo finché il PC è acceso e lo script è in esecuzione.

## Dati demo e crediti

[`scripts/seed-demo.mjs`](scripts/seed-demo.mjs) carica, **tramite l'API**, un catalogo di 60 giochi reali di Steam (59 pubblicati e *Hollow Knight: Silksong* come bozza, con almeno 5 per genere e la descrizione in es, en e it), 49 studi, 6 giocatori con i loro acquisti (uno con il pagamento rifiutato), wishlist e tempo di gioco, e **60 recensori** (20 per lingua) che acquistano con il checkout simulato e recensiscono nella propria lingua, con una distribuzione dei voti simile a quella di Steam: circa 2.100 recensioni, in modo che i giochi abbiano percentuale ed etichetta. Le «Estremamente…» richiedono 500 recensioni e non compaiono. I testi provengono da un archivio proprio di frasi brevi per lingua e voto; non viene copiata alcuna recensione reale. I dati sono in [`scripts/demo-data.json`](scripts/demo-data.json), si convalidano con `node scripts/check-demo-data.mjs` e lo script si può eseguire più volte senza duplicare nulla:

```bash
node scripts/seed-demo.mjs --admin-password <ADMIN_PASSWORD>                                   # contro http://localhost:8081
node scripts/seed-demo.mjs --admin-password <ADMIN_PASSWORD> --api https://tu-api.onrender.com # contro il deploy
```

Il seed attende (quanto indica `Retry-After`) e riprova se l'API risponde 429 per il limite per IP (contro la produzione richiede qualche minuto; non serve toccare le variabili di Render); altri 429 vengono trattati come errore. Convalida l'account admin **prima di registrare il primo account**, per non lasciare account non verificati, e se rilanci il seed dopo un'esecuzione interrotta verifica con l'admin gli account rimasti in sospeso (non li registra di nuovo). L'API richiede di verificare l'email dei nuovi account, quindi il seed li verifica con l'account admin (`POST /api/admin/users/{id}/verify-email`). La password va in `--admin-password` o nella variabile `ADMIN_PASSWORD` (l'utente, predefinito `admin`, in `--admin-login`). Senza di essa, il seed si ferma con un messaggio che chiede `--admin-password` prima di registrare il primo nuovo account (presuppone che l'API richieda la verifica dell'email, come in dev e prod; solo i test la disattivano). Verifica a mano solo account la cui email è quella del seed. In un database già popolato, rilanciarlo non crea nulla e non richiede la password.

Le copertine, le capsule e le immagini di intestazione sono collegate dalla CDN di Steam (`cdn.cloudflare.steamstatic.com`), sia nel negozio sia nell'email di ricevuta. I nomi, le immagini e i marchi appartengono ai rispettivi studi ed editori; sono usati solo come dati dimostrativi in questo progetto didattico. I testi delle recensioni sono propri; le descrizioni dei giochi nuovi provengono dallo store di Steam.

## Decisioni tecniche

La motivazione di ciascuna decisione (da D1 a D51) è in [handoff.md](handoff.md) (in spagnolo). Queste sono le più rilevanti:

- **Contatori protetti.** `sales_count`, `review_count` e `positive_count` sono di sola lettura per JPA (`insertable/updatable = false`) e cambiano solo con UPDATE atomici. Così, un developer che modifica il proprio gioco non sovrascrive mai una vendita o una recensione concorrente.
- **Macchina a stati dell'ordine in SQL.** `WHERE status = 'PENDING'` garantisce che un ordine venga pagato una volta sola, anche con un doppio clic o richieste concorrenti. Inoltre, `UNIQUE(user_id, game_id)` impedisce di avere lo stesso gioco due volte nella libreria.
- **Pagamento rifiutato = commit.** L'ordine FAILED e il tentativo di pagamento vengono salvati per tenerne traccia; il controller risponde 402 senza lanciare un'eccezione.
- **Criteria senza trappole.**
  - Il filtro dei generi usa una subquery `IN`, senza join né `DISTINCT`.
  - I prezzi e le percentuali di recensioni positive si confrontano senza dividere, quindi non c'è mai una divisione per zero.
  - I NULL vanno in fondo con un `CASE` portabile.
  - Si ordina sempre anche per `id` perché la paginazione sia stabile.
- **Lock ottimistico esposto al client.** Il `PUT` di un gioco accetta `version`; se non è aggiornata, risponde 409 invece di sovrascrivere le modifiche di un'altra scheda.
- **Refresh token con rotazione e famiglie.** Si salva solo l'hash; la revoca è un UPDATE atomico condizionale (`revokeIfActive`), quindi tra più refresh simultanei con lo stesso token ne vince uno solo. Riutilizzare un token revoca la sua famiglia.
- **Recensioni senza contatori sballati.** Modificare o eliminare una recensione la legge con lock pessimistico (`findByIdForUpdate`), in modo che il voto da cui deriva il delta sia quello bloccato.
- **Nessuna fuga di informazioni.** Un ordine altrui restituisce 404 (non 403), gli elenchi usano un ordinamento fisso sul server e i dati dell'utente vengono sottoposti a escape nelle email HTML.

### Limitazioni note
- La registrazione con l'email di un account non verificato risponde 201 con l'`id` di quell'account (non con uno nuovo), quindi l'`id` dell'account in sospeso è visibile a chi conosce l'email. Non è un dato sensibile, e il seed e Postman ne hanno bisogno per verificare con l'admin.
- Disattivare un account o cambiargli i ruoli interrompe subito il suo token di accesso, senza interrogare il database a ogni richiesta: il backend conserva un indicatore in memoria. Tale indicatore si perde al riavvio (il token torna a valere fino a 15 minuti) e non è condiviso tra più istanze.
- Il refresh token è salvato in `localStorage`, come quello di accesso, quindi un XSS potrebbe leggerlo. Un cookie `httpOnly` sarebbe più sicuro, ma tra domini diversi (Vercel e Render) complica CORS e SameSite.
- La pulizia giornaliera di `refresh_tokens` elimina solo intere famiglie già scadute; con più istanze girerebbe su tutte (è idempotente).
- Il limite di messaggi dell'assistente vive in memoria: si perde al riavvio e non è condiviso tra più istanze del backend.
- Le etichette «Estremamente positive» ed «Estremamente negative» richiedono 500 recensioni; la demo non ci arriva e non compaiono.
- Il pagamento blocca l'ordine (`SELECT … FOR UPDATE`) mentre chiama il gateway, quindi due pagamenti simultanei non addebitano due volte. Con un provider reale servirebbe comunque una idempotency key per i nuovi tentativi dopo un timeout di rete.
- La copertina della ricevuta viene caricata dall'URL della copertina del gioco. Le copertine **caricate** vanno su Cloudinary (https), un archivio proprio, e non rivelano più l'apertura dell'email a un server esterno. Le copertine **incollate come URL** restano come prima: quel server può sapere quando l'email viene aperta (come un pixel di tracciamento), esattamente come nel negozio web. Gmail lo attenua perché serve le immagini tramite il proprio proxy.
