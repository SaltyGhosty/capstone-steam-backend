# Notificaciones de amistad y chat entre amigos: plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** que el jugador vea un número en «Amigos» con las solicitudes recibidas y los mensajes sin leer, reciba un aviso cuando llega algo nuevo y pueda chatear uno a uno con sus amigos. El historial se guarda aunque dejen de ser amigos.

**Architecture:**
- Una tabla `friend_messages` (V14) en el paquete `friend`, con su `FriendMessage`, su repositorio y un `ChatService`.
- Tres endpoints de mensajes y dos de consulta (`activity` y `conversations`).
- El frontend consulta `activity` cada 30 s y al volver a la pestaña, desde `StoreContext`, y pinta el número en `Layout`.
- El chat vive en `Friends.tsx`.

**Tech Stack:** Spring Boot 4.1 / Java 25, JPA + Flyway (H2 en tests, PostgreSQL), JUnit 5 + MockMvc, React 19 + Vitest + Testing Library, Postman/Newman.

**Spec:** `docs/superpowers/specs/2026-10-22-notificaciones-y-chat-design.md`. Léelo entero antes de empezar: este plan no repite el porqué.

## Global Constraints

- Sin dependencias nuevas, ni en Maven ni en npm.
- **Migración V14** en `src/main/resources/db/migration/common/`, con SQL portable (H2 y PostgreSQL). El CHECK de la pareja va como en V11 (`ck_friendships_pair`).
- Mensajes de 1 a 1.000 caracteres, sin contar los espacios de los extremos (se guarda el texto recortado).
- Límite de envío: **30 mensajes por minuto y usuario**, en memoria → 429 `chat.rateLimited`.
- Lista de conversaciones: **50 como máximo**. Página de mensajes: por defecto **30**, máximo **100**, por cursor `before` (id).
- Consulta periódica de `activity`: **30 s**, más cada `visibilitychange` a visible. Con un chat abierto, sus mensajes cada **4 s**. Solo para jugadores con sesión.
- Orden de los errores al enviar:
  1. 400 validación;
  2. 403 `friend.playersOnly` si **yo** no soy jugador;
  3. 404 si el otro no es un jugador visible (`FriendService.isPlayer` falso o no existe);
  4. 403 `chat.notFriends`;
  5. 429 `chat.rateLimited`.
- Las cuentas que no pasan `FriendService.isPlayer` no cuentan en el número ni salen en `activity` ni en `conversations` (tampoco sus solicitudes).
- Textos nuevos del backend en `messages.properties`, `_en` y `_it`. Textos del frontend en `i18n/messages.ts` (es/en/it, con las mismas claves).
- Se comprueba con:
  - `.\mvnw.cmd -q -B clean verify` (exit 0, JaCoCo ≥ 80 %). Una vez también con `JAVA_TOOL_OPTIONS=-Duser.language=en`, como el CI.
  - `cd frontend; npx vitest run; npx tsc -b; npm run build`.
- Commits en español, uno por función, **sin `Co-Authored-By`**.

## Review Focus

1. **Leer una conversación ajena**: A, B y C son jugadores. C pide `/api/friends/A/messages` y solo ve sus propios mensajes con A (ninguno), nunca los de A con B. Test en la Task 2.
2. **Mensajes con espacios o saltos de línea**: «   » da 400. «hola\n\nadiós» se guarda con los saltos y el frontend los muestra (`white-space: pre-wrap`). Test en las Tasks 2 y 5.
3. **Marcar como leído solo lo recibido**: si A abre la conversación, sus propios mensajes enviados a B siguen sin leer para B. Test en la Task 2.
4. **El número no baja de 0 ni se repite el aviso**: dos consultas seguidas con los mismos datos no lanzan un segundo aviso. Al cerrar e iniciar sesión, la primera consulta no avisa. Test en la Task 4.
5. **HTML en el mensaje**: `<b>hola</b>` se muestra como texto, nunca como HTML. React ya lo escapa, pero no se debe usar `dangerouslySetInnerHTML`. Test en la Task 5.

---

### Task 1: V14, `FriendMessage` y repositorio

**Files:**
- Create: `src/main/resources/db/migration/common/V14__friend_messages.sql`, `src/main/java/com/capstone/steam/friend/FriendMessage.java`, `src/main/java/com/capstone/steam/friend/FriendMessageRepository.java`
- Test: `src/test/java/com/capstone/steam/FlywayMigrationTest.java` (y el recuento de migraciones: 15 aplicadas, versión actual `"14"`)

**Interfaces:**
- Produces:
  - `FriendMessage(User sender, User recipient, String body)`: calcula `userLowId`/`userHighId` como `Friendship`. Getters `getId()`, `getSender()`, `getRecipient()`, `getBody()`, `getCreatedAt()` (`@CreationTimestamp`) y `getReadAt()`.
  - En `FriendMessageRepository`:
    - `List<FriendMessage> findConversation(Long low, Long high, Long beforeId /* null = las últimas */, Pageable page)`: orden por id descendente.
    - `int markRead(Long recipientId, Long senderId, Instant now)`: UPDATE atómico de los que tienen `read_at` null. Lleva `@Modifying(flushAutomatically = true, clearAutomatically = true)`.
    - `List<UnreadCount> countUnreadBySender(Long recipientId)`: solo remitentes visibles (join con `users` y el mismo filtro que `isPlayer`: activo, sin baja ni eliminado, email verificado, sin rol DEVELOPER ni ADMIN). Devuelve una proyección `(Long senderId, long count, Instant lastAt)`.
    - `int deleteAllOf(Long userId)`: como `sender` o como `recipient`. Mismo `@Modifying`.
- Tabla: la de la sección «Modelo de datos» del spec, con `ck_friend_messages_pair`, `ck_friend_messages_body` (`length(body) > 0`) y los dos índices.

- [ ] **Step 1: test que falla.** `v14CreatesFriendMessagesWithItsChecks`:
  - inserta un mensaje válido entre dos usuarios existentes;
  - un `body` vacío falla por el CHECK;
  - una pareja que no corresponde al remitente y al destinatario falla por el CHECK;
  - actualiza el recuento a 15 aplicadas y la versión `"14"`.
- [ ] **Step 2:** `.\mvnw.cmd -q -B test -Dtest=FlywayMigrationTest` → FAIL.
- [ ] **Step 3:** migración, entidad y repositorio.
- [ ] **Step 4:** el mismo test → PASS. Añade un test de repositorio en `RepositoryIntegrationTest`: `countUnreadBySender` ignora a un remitente desactivado y `markRead` solo toca lo recibido.
- [ ] **Step 5: commit** «Chat: tabla de mensajes entre amigos (V14)».

### Task 2: Enviar, leer y marcar como leído

**Files:**
- Create: `src/main/java/com/capstone/steam/friend/ChatService.java`, `ChatController.java` (o los métodos en `FriendController`, a tu elección si encaja mejor), `ChatRateLimiter`, reutilizando `auth/SlidingWindowLimiter`. Nombre: `friend/MessageRateLimiter.java`. DTOs `friend/dto/MessageResponse.java` (`Long id, boolean mine, String body, Instant createdAt`) y `friend/dto/SendMessageRequest.java` (`@NotBlank @Size(max = 1000) String body`).
- Modify: `user/AccountAnonymizer.java` (borra los mensajes con `deleteAllOf`, junto a las amistades) y `messages*.properties` (`chat.notFriends`, `chat.rateLimited`).
- Test: `src/test/java/com/capstone/steam/friend/ChatIntegrationTest.java` y un caso en `AccountAnonymizationIntegrationTest`.

**Interfaces:**
- Consumes: Task 1, `FriendService.isPlayer(User)` (pasa a ser package-visible si hace falta), `FriendshipRepository.findByPair`.
- Produces:
  - `ChatService.send(Long userId, String username, String body)` → `MessageResponse`.
  - `ChatService.messages(Long userId, String username, Long before, int size)` → `List<MessageResponse>`.
  - `ChatService.markRead(Long userId, String username)` → `void`.
  - Endpoints:
    - `POST /api/friends/{username}/messages` → 201;
    - `GET /api/friends/{username}/messages?before=&size=` → 200;
    - `POST /api/friends/{username}/messages/read` → 204.
- Textos:
  - `chat.notFriends`: es «Solo puedes escribir a tus amigos.», en «You can only message your friends.», it «Puoi scrivere solo ai tuoi amici.».
  - `chat.rateLimited`: es «Estás enviando demasiados mensajes. Espera un momento.», en «You're sending too many messages. Wait a moment.», it «Stai inviando troppi messaggi. Aspetta un momento.».

- [ ] **Step 1: tests que fallan** (`ChatIntegrationTest`, con jugadores registrados como en `FriendIntegrationTest`):
  - `friendsCanWriteAndReadEachOther`: 201, y el otro ve `mine=false`.
  - `notFriendsGet403NotFriends`.
  - `exFriendsCanReadButNotWrite`: dejan de ser amigos, GET 200 con el historial y POST 403.
  - `writingAgainAfterBecomingFriendsAgain`: 201.
  - `nobodyReadsAnotherConversation` (Review Focus 1).
  - `blankOrTooLongBodyIs400` (Review Focus 2, junto con el salto de línea guardado tal cual).
  - `markReadOnlyTouchesReceivedMessages` (Review Focus 3).
  - `cursorPaginationReturnsOlderMessages`: 35 mensajes → 30, y con `before` los 5 restantes.
  - `pausedRecipientIs404AndDeveloperSenderIs403PlayersOnly`.
  - `thirtyOneMessagesInAMinuteGive429`.
  - En `AccountAnonymizationIntegrationTest`: `anonymizationDeletesTheConversations`.
- [ ] **Step 2:** ejecutarlos → FAIL.
- [ ] **Step 3:** implementar. La amistad se comprueba en la misma transacción, justo antes de insertar.
- [ ] **Step 4:** tests → PASS, y `FriendIntegrationTest` sigue en verde.
- [ ] **Step 5: commit** «Chat: enviar, leer y marcar como leído entre amigos».

### Task 3: `activity`, `conversations` y no leídos por amigo

**Files:**
- Create: `friend/dto/FriendActivityResponse.java` y `friend/dto/ConversationResponse.java`.
- Modify: `FriendService` / `ChatService`, `FriendController` y `friend/dto/FriendResponse.java` (añade `int unreadMessages`).
- Test: `ChatIntegrationTest` (o `FriendActivityIntegrationTest`).

**Interfaces:**
- Produces:
  - `GET /api/friends/activity` → `FriendActivityResponse(List<IncomingRequest> incomingRequests, List<UnreadFrom> unread, int total)`.
    - `IncomingRequest(Long id, String username, String displayName, Instant createdAt)`.
    - `UnreadFrom(String username, String displayName, long count, Instant lastMessageAt)`.
  - `GET /api/friends/conversations` → `List<ConversationResponse(String username, String displayName, String avatarUrl, boolean friend, long unread, String lastMessage, Instant lastMessageAt)>`.
    - Primero las que tienen mensajes, por `lastMessageAt` descendente; después los amigos sin mensajes, por nombre.
    - Como mucho 50. Solo usuarios que pasan `isPlayer`.
  - `FriendResponse.unreadMessages`.

- [ ] **Step 1: tests que fallan.**
  - `activityCountsIncomingRequestsAndUnreadMessages`: `total` = solicitudes + no leídos.
  - `activityIgnoresPausedOrDisabledSenders`.
  - `conversationsIncludeExFriendsReadOnlyAndFriendsWithoutMessages`.
  - `conversationsAreCappedAtFifty`: puede ir con datos insertados por el repositorio.
  - `friendListCarriesUnreadMessages`.
- [ ] **Step 2:** ejecutarlos → FAIL.
- [ ] **Step 3:** implementar. `activity` hace exactamente dos consultas (solicitudes recibidas visibles y `countUnreadBySender`) más la carga de nombres.
- [ ] **Step 4:** tests → PASS. Después, `clean verify` completo una vez con `JAVA_TOOL_OPTIONS=-Duser.language=en`.
- [ ] **Step 5: commit** «Amigos: actividad pendiente y lista de conversaciones».

### Task 4: Frontend: número en «Amigos» y avisos

**Files:**
- Modify: `frontend/src/api/types.ts`, `api/endpoints.ts`, `context/StoreContext.tsx`, `components/Layout.tsx` (y el componente que pinta el toast, si está aparte), `app.css` (el estilo del número del menú móvil y del avatar, si hace falta) e `i18n/messages.ts`.
- Test: `frontend/src/components/Layout.test.tsx` y un test nuevo `frontend/src/context/friendActivity.test.tsx` (o dentro del de Layout).

**Interfaces:**
- Consumes: `GET /api/friends/activity` (Task 3).
- Produces:
  - `friendsApi.activity()` → `Promise<FriendActivity>`.
  - `friendsApi.conversations()`, `friendsApi.messages(username, before?)`, `friendsApi.sendMessage(username, body)` y `friendsApi.markRead(username)`, que se usan en la Task 5.
  - `StoreContext` expone:
    - `friendBadge: number`;
    - `refreshFriendActivity(): Promise<void>`;
    - el toast ampliado: `notify(message, kind?, action?: { label: string; to: string })`. Los avisos con acción duran **6000 ms**; los demás siguen igual.
- Avisos:
  - Uno por consulta.
  - Con una sola novedad, el concreto:
    - «{name} te ha enviado una solicitud de amistad» → `/friends`;
    - «Nuevo mensaje de {name}» → `/friends?chat={username}`.
  - Con varias, el resumen «Tienes {requests} solicitudes y {messages} mensajes nuevos» → `/friends`, con plurales según el `count`.
  - La primera consulta tras iniciar sesión no avisa.
- Número: en `SideLink` (que ya admite `count`), en el `NavLink` móvil de Amigos y en el menú del avatar. Con 0 no se pinta; por encima de 99, «99+».

- [ ] **Step 1: tests que fallan.**
  - `muestra el número de solicitudes más mensajes en Amigos (lateral, móvil y avatar)`.
  - `no pinta el número con 0 y pinta 99+`.
  - `la primera consulta no avisa y la siguiente con un mensaje nuevo sí, con enlace a la conversación`.
  - `dos consultas iguales no repiten el aviso` (Review Focus 4).
  - `no consulta la actividad para un estudio ni sin sesión`.
  - Usa los temporizadores falsos de Vitest para los 30 s.
- [ ] **Step 2:** `npx vitest run src/components/Layout.test.tsx` (y el nuevo) → FAIL.
- [ ] **Step 3:** implementar, con las claves en es/en/it.
- [ ] **Step 4:** `npx vitest run` y `npx tsc -b` → verde.
- [ ] **Step 5: commit** «Frontend: número de amigos y avisos de solicitudes y mensajes».

### Task 5: Frontend: el chat en Amigos

**Files:**
- Modify: `frontend/src/pages/Friends.tsx` (o un componente nuevo `components/FriendChat.tsx` que use Friends), `app.css` e `i18n/messages.ts`.
- Test: `frontend/src/pages/Friends.test.tsx`.

**Interfaces:**
- Consumes: las funciones de `friendsApi` de la Task 4 y `refreshFriendActivity()`.
- Comportamiento: el de la sección «Frontend» del spec.
  - Lista de conversaciones con su número de no leídos.
  - Conversación con los míos a la derecha, la hora de cada mensaje y bajada automática al último.
  - Al abrir, `markRead` y después `refreshFriendActivity()`.
  - Enter envía y Mayús+Enter hace salto de línea. Contador `n/1000`.
  - Con un ex-amigo, aviso de solo lectura y el cuadro desactivado.
  - Consulta de la conversación abierta cada 4 s. Enlace `/friends?chat=username`.
  - En el móvil (≤ 720 px), la lista **o** la conversación, con «Volver».
  - El texto con `white-space: pre-wrap` y sin HTML.

- [ ] **Step 1: tests que fallan.**
  - `abre la conversación del enlace ?chat= y marca como leído`.
  - `envía con Enter y añade el mensaje`.
  - `Mayús+Enter no envía`.
  - `un ex-amigo se lee pero no se puede escribir`.
  - `muestra <b>hola</b> como texto` (Review Focus 5).
  - `cargar más antiguos usa before`.
- [ ] **Step 2:** `npx vitest run src/pages/Friends.test.tsx` → FAIL.
- [ ] **Step 3:** implementar.
- [ ] **Step 4:** `npx vitest run`, `npx tsc -b` y `npm run build` → verde.
- [ ] **Step 5: commit** «Frontend: chat entre amigos».

### Task 6: Postman y documentación

**Files:**
- Modify: `postman/Capstone-Steam.postman_collection.json`, `README.md` (en **italiano**), `frontend/README.md` (en italiano), `handoff.md` (D56 y la fila de progreso) y `.env.example` (sin cambios si no hay variables nuevas).

- [ ] **Step 1: Postman.** En la carpeta de amigos, con los dos jugadores de la colección, una vez son amigos:
  - el jugador 1 envía un mensaje → 201;
  - el jugador 2 consulta `activity` → `total ≥ 1`;
  - el jugador 2 lee la conversación → contiene el mensaje;
  - `read` → 204;
  - `activity` sin no leídos de ese amigo;
  - un mensaje vacío → 400.
- [ ] **Step 2: Newman** contra el jar del worktree en el puerto 8081 (perfil dev; `ADMIN_PASSWORD` del `.env` sin imprimirla; para solo ese proceso y no toques el 8080) → 0 fallos.
- [ ] **Step 3: documentación.**
  - En el README italiano: los endpoints nuevos, una sección breve «Notifiche e chat» y las cifras de tests y de Postman.
  - En `handoff.md`: D56, con por qué es una consulta periódica y no tiempo real, y que el historial se guarda.
- [ ] **Step 4: commit** «Postman y documentación: notificaciones y chat».
