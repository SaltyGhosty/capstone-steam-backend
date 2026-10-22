# Notificaciones de amistad y chat entre amigos

Fecha: 2026-10-22. Estado: diseño pendiente de aprobación.

## Para qué

El usuario se dio cuenta probando la web:
- **No hay ningún aviso** al recibir una solicitud de amistad: solo se ve entrando en la página de Amigos.
- **No hay chat entre amigos**: el único chat es el del asistente de recomendaciones.

Quiere enterarse de lo que pasa con sus amigos y poder escribirles, sin salir de la tienda.

## Decidido por el usuario

- **Notificación = un número en «Amigos»** (le pareció bien la propuesta y la completó así).
- **Chat de texto uno a uno, solo entre amigos, dentro de la página de Amigos.**
- **Si dejan de ser amigos, la conversación se queda guardada.** Solo se puede leer; si vuelven a ser amigos, se puede seguir escribiendo.

## Decisiones de Claude (el usuario las vio en el resumen y no las cambió)

- El número de «Amigos» = **solicitudes recibidas pendientes + mensajes sin leer**. Sale en el menú lateral, en el menú del móvil y en el elemento «Amigos» del menú del avatar.
- **Aviso emergente** (el `toast` que ya existe) cuando, desde la última consulta, llega una solicitud nueva («Ana te ha enviado una solicitud de amistad») o un mensaje nuevo de alguien («Nuevo mensaje de Ana»). Al pulsarlo se va a Amigos (con esa conversación abierta si es un mensaje). La primera consulta después de entrar no lanza avisos: solo pone el número.
- **Actualización por consulta periódica, no en tiempo real**: cada 30 s y cada vez que la pestaña vuelve a estar visible (`visibilitychange`). Con una conversación abierta, sus mensajes cada 4 s. Solo con sesión iniciada y para cuentas de **jugador** (estudios y admins no tienen amigos).
- **Historial guardado**. Los mensajes recibidos de una persona se marcan como leídos al abrir su conversación.
- **Solo texto**, de 1 a 1.000 caracteres (sin contar espacios de los extremos). Sin imágenes, archivos, «escribiendo…» ni confirmación de lectura visible para el otro.
- **Límite de envío**: 30 mensajes por minuto y usuario, en memoria (mismo patrón que `ChatRateLimiter`/`SlidingWindowLimiter`) → 429.
- **Cuenta eliminada** (anonimización, D55): se borran **todas las conversaciones en las que participa** (mensajes enviados y recibidos). Es coherente con borrar sus reseñas y amistades.
- **Cuenta en pausa** (14 días): no se le puede escribir; sus conversaciones siguen ahí por si recupera la cuenta.

## Comportamiento

### Quién puede qué

| Situación | Leer la conversación | Escribir |
|---|---|---|
| Amigos (ACCEPTED) | Sí | Sí |
| Dejaron de ser amigos | Sí (los dos participantes) | No → 403 `chat.notFriends` |
| Nunca fueron amigos y no hay mensajes | Lista vacía | No → 403 `chat.notFriends` |
| El otro está en pausa, desactivado o eliminado | Sí, si hay historial (salvo eliminado: ya no hay historial) | No → 404 como el perfil |
| Uno de los dos no es jugador (estudio/admin) | — | No → 403 `friend.playersOnly` (ya existe) |

Nadie puede leer una conversación de la que no es participante: las rutas siempre son «mi conversación con `{username}`», nunca un id de conversación ajeno.

### Contador y avisos

- Una consulta ligera devuelve las solicitudes recibidas pendientes (cuántas y de quién) y los mensajes sin leer agrupados por remitente (cuántos y fecha del último).
- El frontend guarda la respuesta anterior. Si aparece una solicitud con un id nuevo o crece el número de no leídos de alguien, lanza el aviso correspondiente (como mucho uno por remitente y consulta).
- Aceptar o rechazar una solicitud y abrir una conversación actualizan el número sin esperar a la siguiente consulta.

### Decisiones tomadas tras la revisión del diseño (de Claude)

- **El aviso**:
  - Hoy el `toast` tiene un solo hueco, dura 2,8 s y no se puede pulsar. Se amplía con una **acción opcional** (texto + enlace), y los avisos con acción duran **6 s**.
  - Se lanza **un solo aviso por consulta**. Si llegan varias cosas a la vez, es un resumen: «Tienes 2 solicitudes y 3 mensajes nuevos» → Amigos. Si es una sola, el aviso concreto.
  - Los avisos que ya existen no cambian.
- **Cuentas no visibles** (en pausa, desactivadas o eliminadas; es decir, las que hoy no pasan `FriendService.isPlayer`):
  - **No cuentan** en el número.
  - **No salen** en `activity` ni en `conversations`, y sus solicitudes tampoco.
  - Es coherente con «desaparece de búsquedas y perfiles», y así nadie se queda con un número que no puede limpiar. Si la cuenta se recupera, sus mensajes vuelven a aparecer.
  - El filtro va dentro de las dos consultas agregadas (join con `users`).
- **Orden de los errores al enviar** (no revela cuentas en pausa):
  1. 400: texto vacío o demasiado largo.
  2. 403 `friend.playersOnly`: si **yo** no soy jugador.
  3. 404: si el otro no es un jugador visible (no existe, está en pausa, desactivado, eliminado, o es un estudio o un admin).
  4. 403 `chat.notFriends`: si no sois amigos.
  5. 429: límite de envío.
- **Varias pestañas**: cada pestaña lanza sus avisos; se acepta. Al iniciar o cerrar sesión se reinicia la «primera consulta», que no avisa.
- **Lista de conversaciones**: como mucho las **50 más recientes**, sin paginar.
- **Carrera envío / dejar de ser amigos**: la amistad se comprueba en la misma transacción, justo antes de insertar. Si alguien deja de ser amigo en ese mismo milisegundo y el mensaje entra, se acepta; no se añade bloqueo ni test de concurrencia.

## Modelo de datos — migración V14 (`common/`, SQL portable)

Tabla `friend_messages`:
- `id` BIGINT identidad
- `sender_id` → `users(id)` ON DELETE CASCADE
- `recipient_id` → `users(id)` ON DELETE CASCADE
- `user_low_id`, `user_high_id`: la pareja ordenada, como en `friendships`, para leer una conversación con un índice. CHECK de que la pareja corresponde a remitente y destinatario, como `ck_friendships_pair` (V11)
- `body` VARCHAR(1000) NOT NULL, con CHECK de longitud mayor que 0
- `created_at` TIMESTAMP WITH TIME ZONE NOT NULL
- `read_at` TIMESTAMP WITH TIME ZONE NULL

Índices:
- `(user_low_id, user_high_id, created_at)`: para leer la conversación;
- `(recipient_id, read_at)`: para contar los no leídos.

Que la conversación quede guardada al dejar de ser amigos se cumple solo: los mensajes no dependen de la fila de `friendships`.

## API (todo con sesión, jugadores)

| Endpoint | Comportamiento |
|---|---|
| `GET /api/friends/activity` | `{ incomingRequests: [{id, username, displayName, createdAt}], unread: [{username, displayName, count, lastMessageAt}], total }`, donde `total` = solicitudes + mensajes sin leer. Es la consulta de los 30 s: tiene que ser barata (dos consultas agregadas). |
| `GET /api/friends/conversations` | Todas mis conversaciones con algún mensaje, más los amigos actuales sin mensajes: `[{username, displayName, avatarUrl, friend: bool, unread, lastMessage, lastMessageAt}]`, ordenadas por el último mensaje. Permite abrir las de ex-amigos (solo lectura). |
| `GET /api/friends/{username}/messages?before={id}&size={n}` | Mensajes de mi conversación con esa persona, de más nuevo a más viejo, paginados por cursor (`before` = id). `size` por defecto 30, máximo 100. Cada mensaje: `{id, mine: bool, body, createdAt}`. Si el otro usuario no existe o está eliminado → 404. |
| `POST /api/friends/{username}/messages` `{body}` | 201 con el mensaje. Errores: 400 si `body` está vacío o pasa de 1.000; 403 `chat.notFriends` si no sois amigos; 404 si el otro está en pausa, desactivado o no existe; 429 por encima de 30 por minuto. |
| `POST /api/friends/{username}/messages/read` | Marca como leídos los mensajes que **esa persona me envió**, con un UPDATE atomico. Es idempotente y devuelve 204. |
| `GET /api/friends` | Ya existe. Cada amigo suma `unreadMessages`. |

Textos nuevos de error en `messages*.properties` (es/en/it): `chat.notFriends`, `chat.rateLimited`.

## Frontend

- **`StoreContext`**: guarda `friendActivity` y la consulta cada 30 s y al volver a la pestaña, solo para jugadores con sesión. Expone `friendBadge` (el `total`) y `refreshFriendActivity()`. Compara con la consulta anterior para lanzar los avisos.
- **`Layout`**: el número en «Amigos» del menú lateral (`SideLink` ya admite `count`), del menú del móvil y del menú del avatar. Con 0 no se pinta. Si pasa de 99, «99+».
- **`Friends.tsx`**: un panel de chat.
  - Una lista de conversaciones, con el número de no leídos de cada una; los amigos sin mensajes salen también.
  - Al elegir una, sus mensajes: los míos a la derecha y los suyos a la izquierda, con la hora. Al abrirla se baja al último mensaje y se marcan como leídos.
  - Un cuadro de texto con contador de caracteres. Enter envía y Mayús+Enter hace salto de línea.
  - Con un ex-amigo: aviso «Ya no sois amigos: solo puedes leer esta conversación» y el cuadro desactivado.
  - En el móvil se ve la lista **o** la conversación, con un botón «Volver».
  - La conversación abierta se puede enlazar con la URL (`/friends?chat=ana`), que es adonde lleva el aviso.
- Todos los textos nuevos en es/en/it.

## Tests

- **Backend**:
  - escribir a un amigo (201) y a un no amigo (403);
  - leer después de dejar de ser amigos;
  - escribir después de volver a ser amigos;
  - nadie lee una conversación ajena;
  - `activity` con solicitudes y no leídos;
  - marcar como leído baja el contador y solo afecta a los mensajes recibidos;
  - paginación por cursor;
  - 400 por longitud;
  - 429 del límite de envío;
  - en pausa → 404;
  - la anonimización borra las conversaciones;
  - test de V14 en `FlywayMigrationTest` (los CHECK).
- **Frontend**:
  - el número en el menú (lateral, móvil y avatar);
  - el aviso al llegar algo nuevo y que no salga en la primera consulta;
  - el chat: enviar, conversación de solo lectura con un ex-amigo, marcar como leído y la vista del móvil.
- **Postman**: enviar un mensaje entre los dos jugadores de la colección, leerlo, comprobar `activity` y marcarlo como leído.

## No objetivos

- Tiempo real (WebSocket o SSE) e indicador «escribiendo…».
- Campanita con una lista de notificaciones, avisos por correo y notificaciones del navegador o push.
- Chats de grupo, imágenes, archivos y emojis personalizados.
- Editar o borrar mensajes y bloquear a otro usuario.
- Avisar de que alguien **aceptó** tu solicitud: solo se avisa de las solicitudes recibidas y de los mensajes.

## Anti-objetivos

- Que alguien lea una conversación de la que no es parte.
- Que se pueda escribir a quien no es amigo, o usar el chat para hacer spam.
- Que la consulta periódica cargue el servidor: tiene que ser barata, y solo se hace con sesión de jugador.
- Que los avisos se repitan o salten al entrar en la web con mensajes ya vistos.

## Ejecución (preferencias del usuario)

- Plan con `superpowers:writing-plans` en `docs/superpowers/plans/2026-10-22-notificaciones-y-chat.md`. Opus planifica y revisa; los subagentes Sonnet construyen en un worktree.
- Commits por función en español, **sin Co-Authored-By**. Hay que comprobar también el **CI de GitHub** después de subir.
- Al cerrar: README **en italiano**, `handoff.md` (D56), Postman, Obsidian y el tablero. Antes de subirlo, preguntar al usuario.
