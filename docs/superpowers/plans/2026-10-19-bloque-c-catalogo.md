# Bloque C — Catálogo a ~60 juegos con reseñas de demo: plan de implementación

> **Para agentes:** lee primero `docs/superpowers/plans/2026-10-19-oleada-1-comun.md` y `2026-10-19-oleada-2-comun.md`. Pasos con casillas `- [ ]`.

**Objetivo:** que la demo tenga **60 juegos** reales de Steam que cubran los 12 géneros (≥5 por género) y que bastantes juegos muestren su etiqueta de Steam. Hay ~60 cuentas de reseñadores nuevas que compran por el checkout simulado y reseñan en es/en/it. Todo pasa por el API.

**Spec:** `docs/superpowers/specs/2026-10-19-cierre-y-extras-design.md` §4 «Bloque C». **Sin migración. Sin cambios de backend ni de frontend:** solo `scripts/`.

## Decisiones ya tomadas

- **Los juegos nuevos se crean igual que los 23 de hoy** (`POST`/`PUT /api/developer/games` con los datos de `demo-data.json`), no con el endpoint de importación de Steam.
  - **Decisión de Opus, que cambia la spec:** así el seed es determinista, idempotente y no depende de que Steam responda durante la demo.
  - Portada: el CDN de Steam por AppID, como hoy. Precio y descuento: los de `demo-data.json`, que después la sincronización de Steam ajusta por `steamAppId`.
- **Descripciones** en es/en/it, de 1-2 frases, **escritas con palabras propias** (sin copiar el texto de la tienda de Steam). Mismo tono que las 23 existentes.
- **Géneros:** solo los 12 slugs que existen: `action adventure horror indie platformer puzzle racing rpg shooter simulation sports strategy`. Al final, ≥5 juegos por género.
  - Hoy faltan: simulation +1, shooter +1, strategy +2, puzzle +3, racing +3, horror +3, sports +4. El resto, hasta 60, variado.
- **Estudios:** una cuenta DEVELOPER por estudio real, como hoy (username en snake_case, `displayName` = nombre del estudio). Si un estudio ya existe en `studios`, se reutiliza.
- **Proporción de reseñas positivas:** sale de la proporción real en Steam.
  - Para cada juego, consulta una vez `https://store.steampowered.com/appreviews/<appid>?json=1&language=all&purchase_type=all&num_per_page=0` (público, sin clave) y lee `query_summary.total_positive` / `total_reviews`.
  - Guarda el % redondeado en `demo-data.json`. El seed **no** llama a Steam.
  - Si para algún juego no responde, pon un % verosímil y márcalo en el informe.
- **Cuántas reseñas por juego (`count`):** entre 10 y 60.
  - Unos 15 juegos muy conocidos con 50-60, para que salga «Muy positivas» cuando su % lo permita. El resto, entre 10 y 49.
  - Tiene que haber **al menos un juego con «Variadas»** (40-69 %) y **al menos uno con «Mayormente negativas» o peor** (<40 %). Elige juegos reales con esa nota en Steam, por ejemplo *Cities: Skylines II* o *Battlefield 2042*, y comprueba su % real.
- **Reseñadores:**
  - 60 cuentas PLAYER: `rev_es_01`…`rev_es_20`, `rev_en_01`…`rev_en_20` y `rev_it_01`…`rev_it_20`.
  - Cada una con su `displayName`: un nombre corto e inventado, propio de su idioma; nada de personas reales.
  - Su `locale` (`PATCH /api/users/me` con `{ "locale": "es" | "en" | "it" }`) es el idioma de sus reseñas.
  - Email `@demo.capstone-steam.local`, como el resto: no sale correo real.
- **Reparto determinista**, sin `Math.random`. Un PRNG con semilla (p. ej. `mulberry32` sobre un hash del `key` del juego) elige qué `count` reseñadores reseñan cada juego.
  - Los primeros `round(count × % / 100)` votan 👍 y el resto 👎.
  - Relanzar el seed da exactamente el mismo reparto.
- **Textos:**
  - Banco de frases cortas en `demo-data.json`, `reviewPhrases.{es,en,it}.{positive,negative}`, con ≥15 frases genéricas por idioma y voto: escritas a mano, sin copiar reseñas de Steam.
  - Cada reseña une 1-2 frases elegidas con el mismo PRNG.
  - Opcional: 1-3 frases específicas por juego en `games[].phrases`.
- **Flujo por reseñador** (uno tras otro, para que su token de 15 min no caduque):
  1. login o registro;
  2. locale;
  3. compra en **un solo pedido** de los juegos asignados que aún no tenga (`DELETE /api/cart`, `PUT /api/cart/items/{id}` por juego, `POST /api/orders` y pagar con la tarjeta válida);
  4. una reseña por juego. Un `409` significa que ya existe y se salta: las reseñas generadas no se reescriben.
- **Lo que ya existe** (`players`, `purchases`, `reviews` a mano, wishlists, carritos, pago fallido) se queda igual.
- **Token caducado:** `call()` reintenta una vez con login nuevo si recibe `401` en una ruta que no es `/api/auth/*`. Así los estudios y jugadores fijos tampoco fallan si el seed tarda más de 15 min.

## Archivos

- **Modificar:** `scripts/demo-data.json`, `scripts/seed-demo.mjs`
- **Crear:** `scripts/check-demo-data.mjs`, el validador del JSON, que **es el test de este bloque**.

## Interfaces

- **`demo-data.json`**:
  - **`games[]`:** gana `steamPositivePercent: number` (0-100), `reviewCount: number` (10-60) y, opcionalmente, `phrases: { es?: string[], en?: string[], it?: string[] }`.
  - **`reviewers`** (raíz): `{ prefix: "rev", perLanguage: 20, names: { es: string[20], en: string[20], it: string[20] } }`.
  - **`reviewPhrases`** (raíz): `{ es: { positive: string[], negative: string[] }, en: …, it: … }`.
- **`node scripts/check-demo-data.mjs`** sale con código 1 y lista los errores si alguna regla falla.
  - **Juegos:** exactamente 60, con `key` y `steamAppId` únicos; todos los géneros existen y hay ≥5 juegos de cada uno.
  - **Datos de cada juego:** tres descripciones no vacías; su estudio existe en `studios`; `steamPositivePercent` entre 0 y 100; `reviewCount` entre 10 y 60.
  - **Variedad:** ≥10 juegos con `reviewCount ≥ 50` y `steamPositivePercent ≥ 80`; ≥1 juego entre 40 y 69 %; ≥1 por debajo de 40 %.
  - **Reseñadores y frases:** 20 nombres únicos por idioma y ≥15 frases por idioma y voto.
  - **Integridad:** `purchases`, `reviews`, `wishlists`, `carts`, `playtime` y `failedPayments` solo nombran juegos y jugadores que existen.
- **Función pura `assignReviews(game, reviewerUsernames) → [{ username, recommended }]`**, exportada desde un módulo pequeño, `scripts/demo-reviews.mjs`, para que el validador la pruebe.

---

### Tarea 1: validador y reparto (test primero)

- [ ] **Crear `scripts/check-demo-data.mjs`** con todas las reglas de arriba, más estas comprobaciones de `assignReviews`, sobre cada juego:
  - devuelve `reviewCount` usuarios distintos;
  - el nº de positivas es `round(count × % / 100)`;
  - dos llamadas dan el mismo resultado.
- [ ] Ejecútalo sobre el JSON de hoy: **FALLA** (23 juegos, faltan campos). Apunta la salida.
- [ ] **Crear `scripts/demo-reviews.mjs`** con el PRNG, `assignReviews` y `reviewText(game, lang, recommended, seed)`, todo puro.
- [ ] Commit `Datos demo: validador y reparto determinista de reseñas`.

### Tarea 2: los 60 juegos

- [ ] **Añadir a `demo-data.json`** los 37 juegos y sus estudios. Rellenar `steamPositivePercent` (consultando Steam, ver arriba) y `reviewCount` para los 60. Añadir `reviewers` y `reviewPhrases`.
- [ ] **Comprobar cada `steamAppId` nuevo:** `https://store.steampowered.com/api/appdetails?appids=<id>` tiene que dar `success: true` y el título correcto. Nada de AppIDs inventados.
- [ ] `node scripts/check-demo-data.mjs` → OK.
- [ ] Commit `Datos demo: catálogo de 60 juegos y frases de reseñas`.

### Tarea 3: el seed

- [ ] **`seed-demo.mjs`:**
  - el reintento por 401 en `call()`;
  - una sección «reseñadores» al final, con el flujo de arriba;
  - registro por consola: `rev_es_07: compra 23, reseña 23`;
  - en el resumen final, el nº de reseñadores y de reseñas generadas.
- [ ] `node --check scripts/seed-demo.mjs` y `node scripts/check-demo-data.mjs` → OK.
- [ ] **Prueba de verdad sin tocar PostgreSQL** (si se puede):
  - arranca el backend con H2 en un puerto libre, p. ej. `$env:SERVER_PORT="8095"; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=test" "-Dspring-boot.run.useTestClasspath=true"`;
  - si arranca, ejecuta `node scripts/seed-demo.mjs --api http://localhost:8095` **dos veces**;
  - la segunda no debe crear nada nuevo (0 pedidos y 0 reseñas nuevas);
  - con `GET /api/games?sort=TOP_RATED&size=60`, apunta cuántos juegos salen con cada `reviewScore`;
  - para el backend al terminar.

  Si con H2 no arranca, no lo fuerces: dilo en el informe y la sesión principal lo probará.
- [ ] Commit `Datos demo: 60 reseñadores que compran y reseñan en tres idiomas`.

## Foco de revisión

1. **Idempotencia:** relanzar no duplica pedidos, reseñas ni cuentas, y no rompe con lo que ya hay en la BD demo (reseñas a mano de las mismas cuentas fijas).
2. **Token de 15 min:** un seed de varios minutos no falla con 401 a mitad.
3. **Sin datos reales de personas:** nombres inventados y frases propias; nada copiado de reseñas de Steam.
4. **Etiquetas:** con el reparto final salen al menos «Muy positivas», «Mayormente positivas», «Variadas» y una negativa. Ningún juego queda por debajo de 10 reseñas.
5. **AppIDs reales,** con el título que dice el JSON.
