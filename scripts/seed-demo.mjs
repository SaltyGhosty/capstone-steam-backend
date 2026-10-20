#!/usr/bin/env node
// Carga el catálogo demo (juegos reales de Steam) a través de la API, como lo haría un usuario:
// estudios que publican sus juegos, jugadores que compran, juegan, reseñan y guardan en la wishlist.
//
//   node scripts/seed-demo.mjs --admin-password <pwd>                       # contra http://localhost:8081
//   node scripts/seed-demo.mjs --admin-password <pwd> --api https://mi-api.onrender.com
//
// La API exige verificar el email de las cuentas nuevas: el seed las verifica con la cuenta de admin
// (POST /api/admin/users/{id}/verify-email). Su contraseña va en --admin-password o en la variable
// ADMIN_PASSWORD; el usuario, por defecto «admin», en --admin-login. Sin contraseña de admin el seed
// solo puede continuar si la API tiene la verificación desactivada.
//
// Se puede repetir: lo que ya existe (usuarios, juegos, compras, reseñas…) se salta.
// Para sembrar un backend con rechazo aleatorio de pagos, conviene arrancarlo con PAYMENT_FAILURE_RATE=0:
// aun así, un pago rechazado se reintenta hasta 3 veces. Las compras de «failedPayments» fallan a propósito.
// Los datos están en scripts/demo-data.json. Las portadas se enlazan desde el CDN de Steam y cada juego
// queda enlazado por su AppID: el backend sincroniza después precio y descuento con la tienda de Steam.
import { readFile } from 'node:fs/promises';
import { assignReviews, gameReviewTexts } from './demo-reviews.mjs';

const apiArg = process.argv.indexOf('--api');
const API = (apiArg > 0 ? process.argv[apiArg + 1] : 'http://localhost:8081').replace(/\/$/, '');
const argValue = (name) => { const i = process.argv.indexOf(name); return i > 0 ? process.argv[i + 1] : undefined; };
const ADMIN_LOGIN = argValue('--admin-login') ?? 'admin';
const ADMIN_PASSWORD = argValue('--admin-password') ?? process.env.ADMIN_PASSWORD;
const data = JSON.parse(await readFile(new URL('./demo-data.json', import.meta.url), 'utf8'));
const VALID_CARD = '4242424242424242';
const DECLINED_CARD = '4242424242420000';

const coverUrl = (appId) => `https://cdn.cloudflare.steamstatic.com/steam/apps/${appId}/library_600x900_2x.jpg`;

class HttpError extends Error {
  constructor(status, detail, code) { super(`${status} ${detail}`); this.status = status; this.code = code; }
}

// El token de acceso dura 15 min. Si caduca a mitad del seed se hace login de nuevo con la misma cuenta.
const sessionUser = new Map(); // token -> username
const renewed = new Map(); // token caducado -> token vigente

async function call(method, path, { token, body, accept = [] } = {}) {
  const send = (tk) => fetch(API + path, {
    method,
    headers: {
      ...(body !== undefined && { 'Content-Type': 'application/json' }),
      ...(tk && { Authorization: `Bearer ${tk}` }),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  // La API limita por IP (20 por minuto) el registro y la verificación: ante un 429 de /api/auth se espera y se repite
  const sendPatiently = async (tk) => {
    for (let waited = 0; ; waited++) {
      const r = await send(tk);
      if (r.status !== 429 || !path.startsWith('/api/auth/') || waited >= 40) return r;
      if (waited === 0) console.log('  … límite de peticiones por IP: espero a que se libere');
      await new Promise((resolve) => setTimeout(resolve, 15000));
    }
  };
  let current = renewed.get(token) ?? token;
  let res = await sendPatiently(current);
  if (res.status === 401 && current && !path.startsWith('/api/auth/') && sessionUser.has(current)) {
    const login = await call('POST', '/api/auth/login', { body: { login: sessionUser.get(current), password: data.password } });
    sessionUser.set(login.accessToken, sessionUser.get(current));
    renewed.set(token, login.accessToken);
    current = login.accessToken;
    res = await sendPatiently(current);
  }
  const text = await res.text();
  const json = text ? JSON.parse(text) : null;
  if (!res.ok && !accept.includes(res.status)) throw new HttpError(res.status, json?.detail ?? json?.title ?? text, json?.code);
  return json;
}

// Token del admin para verificar cuentas: se pide una vez y se renueva antes de que caduque (15 min).
let adminSession = null;
async function adminToken() {
  if (!ADMIN_PASSWORD) {
    console.error('Falta --admin-password: la API exige verificar el email');
    process.exit(1);
  }
  if (!adminSession || Date.now() - adminSession.at > 10 * 60 * 1000) {
    const res = await call('POST', '/api/auth/login', { body: { login: ADMIN_LOGIN, password: ADMIN_PASSWORD } });
    adminSession = { token: res.accessToken, at: Date.now() };
  }
  return adminSession.token;
}

/**
 * Inicia sesión y, si la cuenta no existe (401) o está pendiente de verificar (403 auth.emailNotVerified),
 * la registra y, si la API pide verificar el email, la verifica con el admin. Devuelve token, id y el usuario.
 */
async function account(username, displayName, accountType) {
  const login = () => call('POST', '/api/auth/login', { body: { login: username, password: data.password } });
  try {
    const res = await login();
    sessionUser.set(res.accessToken, username);
    return { token: res.accessToken, created: false, id: res.user.id, user: res.user };
  } catch (e) {
    if (e.status !== 401 && !(e.status === 403 && e.code === 'auth.emailNotVerified')) throw e;
  }
  const reg = await call('POST', '/api/auth/register', { body: {
    username, displayName, accountType, password: data.password, email: `${username}@demo.capstone-steam.local`,
  } });
  if (reg.verificationRequired) {
    await call('POST', `/api/admin/users/${reg.id}/verify-email`, { token: await adminToken() });
  }
  const res = reg.verificationRequired ? await login() : reg;
  sessionUser.set(res.accessToken, username);
  return { token: res.accessToken, created: true, id: res.user.id, user: res.user };
}

const count = { studios: 0, games: 0, orders: 0, reviews: 0, reviewers: 0, generated: 0 };
const log = (msg) => console.log(`  ${msg}`);

// ---------------------------------------------------------------- estudios y juegos
console.log(`Sembrando ${API}`);
const genres = Object.fromEntries((await call('GET', '/api/genres')).map((g) => [g.slug, g.id]));
const studioTokens = {};
for (const s of data.studios) {
  const { token, created } = await account(s.username, s.displayName, 'DEVELOPER');
  studioTokens[s.username] = token;
  if (created) count.studios++;
}

const gameIds = {};
const gameSlugs = {};
const ownGames = {}; // estudio -> sus juegos (una sola lectura por estudio)
const gameStatus = {}; // key -> estado real en la BD (un admin pudo retirar un juego)
for (const g of data.games) {
  const token = studioTokens[g.studio];
  ownGames[g.studio] ??= (await call('GET', '/api/developer/games?size=100', { token })).content;
  const own = ownGames[g.studio];
  let game = own.find((x) => x.title === g.title);
  if (!game) {
    const missing = g.genres.filter((slug) => !genres[slug]);
    if (missing.length) throw new Error(`Géneros inexistentes en ${g.title}: ${missing.join(', ')}`);
    game = await call('POST', '/api/developer/games', { token, body: {
      title: g.title, description: g.description, descriptionEn: g.descriptionEn, descriptionIt: g.descriptionIt,
      price: g.price, discountPercent: g.discountPercent ?? 0,
      releaseDate: g.releaseDate, coverUrl: coverUrl(g.steamAppId), steamAppId: g.steamAppId,
      genreIds: g.genres.map((slug) => genres[slug]),
    } });
    own.push(game);
    count.games++;
    log(`+ ${g.title}${g.draft ? ' (borrador)' : ''}`);
  }
  else if (!game.steamAppId || (!game.descriptions?.en && g.descriptionEn)) {
    // juego cargado antes de las traducciones o del enlace con Steam: se completan sin tocar el resto
    game = await call('PUT', `/api/developer/games/${game.id}`, { token, body: {
      title: game.title, description: game.descriptions?.es ?? g.description,
      descriptionEn: game.descriptions?.en ?? g.descriptionEn, descriptionIt: game.descriptions?.it ?? g.descriptionIt, price: game.price, discountPercent: game.discountPercent,
      releaseDate: game.releaseDate, coverUrl: game.coverUrl, steamAppId: g.steamAppId,
      genreIds: game.genres.map((x) => x.id), version: game.version,
    } });
    log(`~ ${g.title}: traducciones y AppID de Steam`);
  }
  if (!g.draft && game.status === 'DRAFT') {
    const published = await call('POST', `/api/developer/games/${game.id}/publish`, { token });
    game = { ...game, status: published?.status ?? 'PUBLISHED' };
  }
  gameStatus[g.key] = game.status;
  if (!g.draft && game.status !== 'PUBLISHED') console.warn(`  ! ${g.title}: está ${game.status} en la BD, se salta al comprar y reseñar`);
  gameIds[g.key] = game.id;
  gameSlugs[g.key] = game.slug;
}

// ---------------------------------------------------------------- jugadores
const playerTokens = {};
const playerIds = {}; // username -> id de usuario: el displayName no es único, el id sí
for (const p of data.players) {
  const acc = await account(p.username, p.displayName, 'PLAYER');
  playerTokens[p.username] = acc.token;
  playerIds[p.username] = acc.id;
}

/** Solo se puede comprar, reseñar o guardar un juego publicado. */
const live = (key) => gameStatus[key] === 'PUBLISHED';

async function owned(token, key) {
  return (await call('GET', `/api/library/status/${gameIds[key]}`, { token })).owned;
}

/** Carrito → pedido → pago. Devuelve el estado final del pedido. */
async function buy(token, keys, card) {
  await call('DELETE', '/api/cart', { token });
  for (const key of keys) await call('PUT', `/api/cart/items/${gameIds[key]}`, { token });
  const order = await call('POST', '/api/orders', { token });
  const paid = await call('POST', `/api/orders/${order.id}/pay`, { token, body: { method: 'CREDIT_CARD', cardNumber: card }, accept: [402] });
  count.orders++;
  return paid.status;
}

/**
 * Compra de verdad: si el pago se rechaza (PAYMENT_FAILURE_RATE > 0) repite hasta 3 veces, con un pedido
 * nuevo cada vez. Devuelve false si los 3 intentos fallan.
 */
async function buyPaid(token, keys, who) {
  for (let attempt = 1; attempt <= 3; attempt++) {
    const status = await buy(token, keys, VALID_CARD);
    if (status === 'PAID') return true;
    console.warn(`  ! ${who}: pedido ${status} (intento ${attempt} de 3)`);
  }
  return false;
}

for (const [player, keys] of Object.entries(data.purchases)) {
  const token = playerTokens[player];
  const pending = [];
  for (const key of keys) if (live(key) && !(await owned(token, key))) pending.push(key);
  if (pending.length) {
    // de dos en dos, para que cada jugador tenga un historial con varios pedidos
    for (let i = 0; i < pending.length; i += 2) await buyPaid(token, pending.slice(i, i + 2), player);
    log(`${player} compra ${pending.length} juegos`);
  }
}

for (const [player, keys] of Object.entries(data.failedPayments)) {
  const token = playerTokens[player];
  const orders = (await call('GET', '/api/orders?size=100', { token })).content;
  if (orders.some((o) => o.status === 'FAILED') || !keys.every(live)) continue;
  const status = await buy(token, keys, DECLINED_CARD);
  await call('DELETE', '/api/cart', { token });
  log(`${player}: pago rechazado (${status})`);
}

for (const [player, minutesByGame] of Object.entries(data.playtime)) {
  const token = playerTokens[player];
  const library = (await call('GET', '/api/library?size=100', { token })).content;
  for (const [key, minutes] of Object.entries(minutesByGame)) {
    const entry = library.find((e) => e.gameId === gameIds[key]);
    if (!entry || entry.playtimeMinutes > 0) continue;
    // el endpoint admite como mucho 1440 min por sesión
    for (let left = minutes; left > 0; left -= 1440) {
      await call('POST', `/api/library/${gameIds[key]}/play`, { token, body: { minutes: Math.min(left, 1440) } });
    }
  }
}

// Índice de las reseñas que ya hay (una lectura por juego publicado, paginada): evita los POST que darían 409.
const reviewIndex = new Map(); // `${idAutor}:${idJuego}` -> reseña
const reviewKey = (userId, gameId) => `${userId}:${gameId}`;
for (const g of data.games) {
  if (!live(g.key)) continue;
  for (let page = 0, pages = 1; page < pages; page++) {
    const res = await call('GET', `/api/games/${gameSlugs[g.key]}/reviews?size=100&page=${page}`);
    pages = res.totalPages;
    for (const x of res.content) reviewIndex.set(reviewKey(x.author.id, x.gameId), x);
  }
}

for (const r of data.reviews) {
  if (!live(r.game)) continue;
  const body = { gameId: gameIds[r.game], recommended: r.recommended, body: r.body, language: r.language };
  const key = reviewKey(playerIds[r.player], gameIds[r.game]);
  const existing = reviewIndex.get(key);
  if (!existing) {
    reviewIndex.set(key, await call('POST', '/api/reviews', { token: playerTokens[r.player], body }));
    count.reviews++;
  } else if (existing.body !== r.body || existing.language !== r.language || existing.recommended !== r.recommended) {
    // ya la había escrito: se actualiza si cambió el texto, el idioma o el voto (p. ej. al traducir los datos demo)
    reviewIndex.set(key, await call('PUT', `/api/reviews/${existing.id}`, { token: playerTokens[r.player], body }));
    count.reviews++;
  }
}

for (const [player, keys] of Object.entries(data.wishlists)) {
  for (const key of keys.filter(live)) await call('PUT', `/api/wishlist/${gameIds[key]}`, { token: playerTokens[player] });
}
for (const [player, keys] of Object.entries(data.carts)) {
  for (const key of keys.filter(live)) await call('PUT', `/api/cart/items/${gameIds[key]}`, { token: playerTokens[player] });
}

// ---------------------------------------------------------------- reseñadores
// 60 cuentas que compran por el checkout simulado y reseñan en su idioma. El reparto es determinista
// (demo-reviews.mjs), así que relanzar el seed no duplica nada. Se hace de uno en uno por el token de 15 min.
const { prefix, perLanguage, names } = data.reviewers;
const reviewers = ['es', 'en', 'it'].flatMap((lang) => Array.from({ length: perLanguage }, (_, i) => ({
  lang, username: `${prefix}_${lang}_${String(i + 1).padStart(2, '0')}`, displayName: names[lang][i],
})));
const usernames = reviewers.map((r) => r.username);
const published = data.games.filter((g) => !g.draft && live(g.key)); // ni un borrador ni uno retirado se pueden comprar
const plan = {}; // username -> [{ game, recommended }]
for (const g of published) {
  // los textos se reparten juego a juego para que no se repita ninguno dentro del mismo juego
  const langOf = (u) => u.split('_')[1];
  for (const { username, recommended, text } of gameReviewTexts(data.reviewPhrases, g, assignReviews(g, usernames), langOf)) {
    (plan[username] ??= []).push({ game: g, recommended, text });
  }
}

for (const r of reviewers) {
  const { token, created, id, user } = await account(r.username, r.displayName, 'PLAYER');
  if (created) count.reviewers++;
  if (user.locale !== r.lang) await call('PATCH', '/api/users/me', { token, body: { locale: r.lang } });
  const missing = (plan[r.username] ?? []).filter((a) => !reviewIndex.has(reviewKey(id, gameIds[a.game.key])));
  if (!missing.length) continue; // relanzamiento: ya está todo

  const library = new Set((await call('GET', '/api/library?size=100', { token })).content.map((e) => e.gameId));
  const pending = missing.filter((a) => !library.has(gameIds[a.game.key]));
  let bought = 0;
  if (pending.length) {
    if (!(await buyPaid(token, pending.map((a) => a.game.key), r.username))) {
      console.warn(`  ! ${r.username}: los 3 pagos fallaron, se pasa al siguiente reseñador`);
      continue;
    }
    bought = pending.length;
  }

  for (const { game, recommended, text } of missing) {
    const review = await call('POST', '/api/reviews', { token, body: {
      gameId: gameIds[game.key], recommended, language: r.lang, body: text,
    } });
    reviewIndex.set(reviewKey(id, gameIds[game.key]), review);
  }
  count.generated += missing.length;
  log(`${r.username}: compra ${bought}, reseña ${missing.length}`);
}

console.log(`Listo: ${count.studios} estudios, ${count.games} juegos, ${count.orders} pedidos y ${count.reviews} reseñas nuevos.`);
console.log(`Reseñadores: ${count.reviewers} cuentas nuevas de ${reviewers.length}, ${count.generated} reseñas generadas.`);
console.log(`Cuentas demo (contraseña "${data.password}"): demo_player (jugador), team_cherry (estudio con un borrador).`);
