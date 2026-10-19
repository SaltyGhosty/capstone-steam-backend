# Oleada 2 — pasada de arreglos tras la revisión final

> **Para agentes:** valen las mismas reglas que en `2026-10-19-oleada-1-arreglos.md`: TDD por hallazgo cuando haya algo que probar, un commit por hallazgo, sin co-author. Haz **solo tu sección**.

## C · Catálogo y seed (solo `scripts/`)

No hay tests de backend para el seed. Su «test» es el validador `scripts/check-demo-data.mjs`, más una ejecución contra H2:

```
$env:SERVER_PORT="8097"; $env:JWT_SECRET=<el de src/test/resources/application-test.yml>; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=test" "-Dspring-boot.run.useTestClasspath=true"
```

Lanza el seed dos veces. La segunda tiene que dar 0 cambios. Para el backend al terminar.

1. **La reseña a mano se busca por `displayName`** (`seed-demo.mjs`, bucle `data.reviews`, unas líneas 176-184). Si otra cuenta tiene el mismo nombre, el PUT da 403 y el seed se para.
   - `account()` también devuelve `user.id`. Guarda `playerIds[username]` y busca la reseña con `x.author.id === playerIds[r.player]`.
   - Comprueba en `ReviewResponse` cómo se llama el id del autor.
   - **Prueba:** con H2, crea una cuenta extra con `displayName` «Ana» que reseñe Hollow Knight y relanza el seed. No falla, y la reseña de `reviewer_ana` no se toca si no cambia.
2. **Un pago rechazado aborta el seed** (`buy()` y la sección de reseñadores).
   - Si el pedido acaba `FAILED` (402), reintenta `buy()` hasta 3 veces. Cada intento crea un pedido nuevo.
   - Si los 3 fallan, avisa por consola y sigue con el siguiente reseñador.
   - Las compras de `failedPayments`, que fallan a propósito, no se tocan.
   - En la cabecera del script, recomienda sembrar con `PAYMENT_FAILURE_RATE=0`.
3. **Los juegos que no están `PUBLISHED` en la BD rompen la compra y la lista de reseñas** (por ejemplo, uno retirado por un admin).
   - Guarda `game.status` en el bucle de juegos.
   - Sáltalos, con aviso, al repartir los reseñadores y en el bucle de reseñas a mano.
4. **Rendimiento en los relanzamientos.** Hoy el 82 % de las llamadas son 409, y contra Render serían 7-11 minutos.
   - Antes de la sección de reseñadores, lee una vez `GET /api/games/{slug}/reviews?size=100` por juego publicado. Pagina si `totalPages > 1`.
   - Con eso construye un `Set` de `authorId:gameId` y salta los POST que ya existen.
   - Usa ese mismo índice en el bucle de reseñas a mano (punto 1), sin depender de `size=100`.
   - Opcional: en `account()`, intenta primero el login y registra solo si da 401.
   - **Prueba:** cuenta las llamadas del relanzamiento y anota el número en el informe.
5. **Textos repetidos o incoherentes** (`demo-reviews.mjs` y el banco de frases).
   - Con el mismo PRNG, evita repetir un texto que ya salió en el mismo juego.
   - No uses frases de precio o compra cuando `price === 0`: márcalas en el banco, p. ej. `{ text, aboutPrice: true }` o una lista aparte.
   - Añade más frases si hacen falta para que haya variedad.
   - El validador comprueba que no hay textos repetidos en el mismo juego y que ningún juego gratis lleva frases de precio.
6. **El validador se queda corto** (`check-demo-data.mjs`).
   - Cuenta los borradores aparte: «60 juegos, 59 publicados + 1 borrador». Exige ≥5 por género **solo entre los publicados**. Si algún género no llega, añade un juego publicado real.
   - Prueba `reviewText` en los 3 idiomas y con los 2 votos.
   - Usa `reviewers.perLanguage` en vez del 20 fijo.
   - Comprueba que `reviewCount` ≤ el nº total de reseñadores.
7. **Descripciones antiguas copiadas de Steam.** Algunas de las 23 originales son casi literales del `short_description` de Steam: Baldur's Gate 3, Civilization VI, INSIDE, Celeste…
   - Reescribe con palabras propias, en es/en/it, las que se parezcan.
   - Para medirlo, compara 4-gramas con `appdetails` y deja todas por debajo de 0,2. Anota en el informe la cifra de las que cambies.
8. **El reseñador «DriftKing»** es el apodo de un piloto real. Cámbialo por un nombre inventado.

## D · Chatbot

(Se rellena con la revisión de D.)
