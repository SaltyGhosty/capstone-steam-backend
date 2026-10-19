#!/usr/bin/env node
// Carga el catálogo demo (juegos reales de Steam) a través de la API, como lo haría un usuario:
// estudios que publican sus juegos, jugadores que compran, juegan, reseñan y guardan en la wishlist.
//
//   node scripts/seed-demo.mjs                              # contra http://localhost:8081
//   node scripts/seed-demo.mjs --api https://mi-api.onrender.com
//
// Se puede repetir: lo que ya existe (usuarios, juegos, compras, reseñas…) se salta.
// Los datos están en scripts/demo-data.json. Las portadas se enlazan desde el CDN de Steam y cada juego
// queda enlazado por su AppID: el backend sincroniza después precio y descuento con la tienda de Steam.
import { readFile } from 'node:fs/promises';
import { assignReviews, gameReviewTexts } from './demo-reviews.mjs';

const apiArg = process.argv.indexOf('--api');
const API = (apiArg > 0 ? process.argv[apiArg + 1] : 'http://localhost:8081').replace(/\/$/, '');
const data = JSON.parse(await readFile(new URL('./demo-data.json', import.meta.url), 'utf8'));
const VALID_CARD = '4242424242424242';
const DECLINED_CARD = '4242424242420000';

const coverUrl = (appId) => `https://cdn.cloudflare.steamstatic.com/steam/apps/${appId}/library_600x900_2x.jpg`;

class HttpError extends Error {
  constructor(status, detail) { super(`${status} ${detail}`); this.status = status; }
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
  let current = renewed.get(token) ?? token;
  let res = await send(current);
  if (res.status === 401 && current && !path.startsWith('/api/auth/') && sessionUser.has(current)) {
    const login = await call('POST', '/api/auth/login', { body: { login: sessionUser.get(current), password: data.password } });
    sessionUser.set(login.accessToken, sessionUser.get(current));
    renewed.set(token, login.accessToken);
    current = login.accessToken;
    res = await send(current);
  }
  const text = await res.text();
  const json = text ? JSON.parse(text) : null;
  if (!res.ok && !accept.includes(res.status)) throw new HttpError(res.status, json?.detail ?? json?.title ?? text);
  return json;
}

/** Registra la cuenta o, si ya existe, inicia sesión. Devuelve el token. */
async function account(username, displayName, accountType) {
  try {
    const res = await call('POST', '/api/auth/register', { body: {
      username, displayName, accountType, password: data.password, email: `${username}@demo.capstone-steam.local`,
    } });
    sessionUser.set(res.accessToken, username);
    return { token: res.accessToken, created: true, id: res.user.id };
  } catch (e) {
    if (e.status !== 409) throw e;
    const res = await call('POST', '/api/auth/login', { body: { login: username, password: data.password } });
    sessionUser.set(res.accessToken, username);
    return { token: res.accessToken, created: false, id: res.user.id };
  }
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
const gameStatus = {}; // key -> estado real en la BD (un admin pudo retirar un juego)
for (const g of data.games) {
  const token = studioTokens[g.studio];
  const own = (await call('GET', '/api/developer/games?size=100', { token })).content;
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

for (const [player, keys] of Object.entries(data.purchases)) {
  const token = playerTokens[player];
  const pending = [];
  for (const key of keys) if (live(key) && !(await owned(token, key))) pending.push(key);
  if (pending.length) {
    // de dos en dos, para que cada jugador tenga un historial con varios pedidos
    for (let i = 0; i < pending.length; i += 2) await buy(token, pending.slice(i, i + 2), VALID_CARD);
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

for (const r of data.reviews) {
  if (!live(r.game)) continue;
  try {
    await call('POST', '/api/reviews', { token: playerTokens[r.player], body: {
      gameId: gameIds[r.game], recommended: r.recommended, body: r.body, language: r.language,
    } });
    count.reviews++;
  } catch (e) {
    if (e.status !== 409) throw e;
    // ya la había escrito: se actualiza si cambió el texto, el idioma o el voto (p. ej. al traducir los datos demo)
    const existing = (await call('GET', `/api/games/${gameSlugs[r.game]}/reviews?size=100`)).content
      .find((x) => x.author.id === playerIds[r.player]);
    if (existing && (existing.body !== r.body || existing.language !== r.language || existing.recommended !== r.recommended)) {
      await call('PUT', `/api/reviews/${existing.id}`, { token: playerTokens[r.player], body: {
        gameId: gameIds[r.game], recommended: r.recommended, body: r.body, language: r.language,
      } });
      count.reviews++;
    }
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
  const { token, created } = await account(r.username, r.displayName, 'PLAYER');
  if (created) count.reviewers++;
  await call('PATCH', '/api/users/me', { token, body: { locale: r.lang } });
  const assigned = plan[r.username] ?? [];

  const library = new Set((await call('GET', '/api/library?size=100', { token })).content.map((e) => e.gameId));
  const pending = assigned.filter((a) => !library.has(gameIds[a.game.key]));
  let bought = 0;
  if (pending.length) {
    const status = await buy(token, pending.map((a) => a.game.key), VALID_CARD);
    if (status !== 'PAID') throw new Error(`${r.username}: el pedido quedó ${status}`);
    bought = pending.length;
  }

  let reviewed = 0;
  for (const { game, recommended, text } of assigned) {
    try {
      await call('POST', '/api/reviews', { token, body: {
        gameId: gameIds[game.key], recommended, language: r.lang,
        body: text,
      } });
      reviewed++;
    } catch (e) {
      if (e.status !== 409) throw e; // ya existe: las generadas no se reescriben
    }
  }
  count.generated += reviewed;
  if (bought || reviewed) log(`${r.username}: compra ${bought}, reseña ${reviewed}`);
}

console.log(`Listo: ${count.studios} estudios, ${count.games} juegos, ${count.orders} pedidos y ${count.reviews} reseñas nuevos.`);
console.log(`Reseñadores: ${count.reviewers} cuentas nuevas de ${reviewers.length}, ${count.generated} reseñas generadas.`);
console.log(`Cuentas demo (contraseña "${data.password}"): demo_player (jugador), team_cherry (estudio con un borrador).`);
