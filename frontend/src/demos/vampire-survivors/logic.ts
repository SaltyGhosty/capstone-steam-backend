// Lógica pura del minijuego de supervivencia (sin canvas): estado + step(dt, input).
import type { DemoInput } from '../types';

export const SURVIVE_SECONDS = 60;
export const ARENA_W = 640;
export const ARENA_H = 360;
export const PLAYER_RADIUS = 8;
export const ENEMY_RADIUS = 8;
/** Alcance del ataque automático */
export const ATTACK_RADIUS = 95;

const PLAYER_SPEED = 140;
const START_HP = 5;
const INVULN_SECONDS = 1;
const WAVE_EVERY = 15;
const WAVE_SIZE = 8;

export interface Enemy { x: number; y: number; fast: boolean }

export interface SurvivorsState {
  elapsed: number;
  hp: number;
  kills: number;
  /** Cuántos enemigos han aparecido en total (para medir cómo crece la densidad) */
  spawned: number;
  enemies: Enemy[];
  player: { x: number; y: number };
  invuln: number;
  attackCd: number;
  spawnCd: number;
  /** Segundo de la próxima oleada en anillo */
  nextWave: number;
  /** Último golpe del ataque, para dibujar el destello */
  flash: { x: number; y: number; t: number } | null;
  rng: number;
  finished: boolean;
  won: boolean;
}

/** Generador con semilla (mulberry32): cada llamada devuelve el valor y el estado siguiente. */
function random(seed: number): [number, number] {
  const next = (seed + 0x6d2b79f5) | 0;
  let t = next;
  t = Math.imul(t ^ (t >>> 15), t | 1);
  t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
  return [((t ^ (t >>> 14)) >>> 0) / 4294967296, next];
}

export function createSurvivors(seed: number): SurvivorsState {
  return {
    elapsed: 0, hp: START_HP, kills: 0, spawned: 0, enemies: [],
    player: { x: ARENA_W / 2, y: ARENA_H / 2 },
    invuln: 0, attackCd: 0.4, spawnCd: 0.8, nextWave: WAVE_EVERY, flash: null,
    rng: seed | 0, finished: false, won: false,
  };
}

/** Segundos entre apariciones: de 1 s al principio a ~0,24 s al final */
export const spawnInterval = (elapsed: number) => Math.max(0.24, 1 - elapsed * 0.0125);
const enemySpeed = (e: Enemy, elapsed: number) => (40 + elapsed * 0.5) * (e.fast ? 1.5 : 1);
/** El ataque se acelera con cada baja */
const attackCooldown = (kills: number) => Math.max(0.18, 0.55 - kills * 0.007);

export function stepSurvivors(state: SurvivorsState, dt: number, input: DemoInput): SurvivorsState {
  if (state.finished) return state;
  let rng = state.rng;
  const rand = () => {
    const [value, next] = random(rng);
    rng = next;
    return value;
  };

  const elapsed = state.elapsed + dt;
  let { hp, kills, spawned, nextWave } = state;
  let invuln = Math.max(0, state.invuln - dt);
  let attackCd = state.attackCd - dt;
  let spawnCd = state.spawnCd - dt;
  let flash = state.flash && state.flash.t - dt > 0 ? { ...state.flash, t: state.flash.t - dt } : null;

  // Movimiento del jugador (la diagonal no es más rápida)
  let dx = (input.right ? 1 : 0) - (input.left ? 1 : 0);
  let dy = (input.down ? 1 : 0) - (input.up ? 1 : 0);
  const len = Math.hypot(dx, dy);
  if (len > 0) { dx /= len; dy /= len; }
  const player = {
    x: Math.min(ARENA_W - PLAYER_RADIUS, Math.max(PLAYER_RADIUS, state.player.x + dx * PLAYER_SPEED * dt)),
    y: Math.min(ARENA_H - PLAYER_RADIUS, Math.max(PLAYER_RADIUS, state.player.y + dy * PLAYER_SPEED * dt)),
  };

  let enemies: Enemy[] = state.enemies.map((e) => ({ ...e }));

  // Apariciones por los bordes, cada vez más seguidas, y una oleada en anillo cada 15 s
  while (spawnCd <= 0) {
    spawnCd += spawnInterval(elapsed);
    const side = Math.floor(rand() * 4);
    const along = rand();
    const margin = 12;
    const pos = [
      { x: along * ARENA_W, y: -margin },
      { x: ARENA_W + margin, y: along * ARENA_H },
      { x: along * ARENA_W, y: ARENA_H + margin },
      { x: -margin, y: along * ARENA_H },
    ][side];
    enemies.push({ ...pos, fast: elapsed > 30 && rand() < 0.25 });
    spawned++;
  }
  if (elapsed >= nextWave) {
    nextWave += WAVE_EVERY;
    const offset = rand() * Math.PI * 2;
    for (let i = 0; i < WAVE_SIZE; i++) {
      const a = offset + (i / WAVE_SIZE) * Math.PI * 2;
      enemies.push({ x: player.x + Math.cos(a) * 280, y: player.y + Math.sin(a) * 280, fast: false });
      spawned++;
    }
  }

  // Los enemigos persiguen al jugador
  for (const e of enemies) {
    const ex = player.x - e.x;
    const ey = player.y - e.y;
    const dist = Math.hypot(ex, ey);
    if (dist > 0.001) {
      const step = Math.min(dist, enemySpeed(e, elapsed) * dt);
      e.x += (ex / dist) * step;
      e.y += (ey / dist) * step;
    }
  }

  // Ataque automático: elimina al enemigo más cercano si está dentro del radio
  if (attackCd <= 0) {
    let best = -1;
    let bestDist = ATTACK_RADIUS;
    enemies.forEach((e, i) => {
      const d = Math.hypot(e.x - player.x, e.y - player.y);
      if (d <= bestDist) { best = i; bestDist = d; }
    });
    if (best >= 0) {
      flash = { x: enemies[best].x, y: enemies[best].y, t: 0.12 };
      enemies = enemies.filter((_, i) => i !== best);
      kills++;
      attackCd = attackCooldown(kills);
    } else {
      attackCd = 0.05; // sin objetivo: vuelve a mirar enseguida
    }
  }

  // Contacto: 1 de vida y un segundo de invulnerabilidad
  if (invuln <= 0 && enemies.some((e) => Math.hypot(e.x - player.x, e.y - player.y) < PLAYER_RADIUS + ENEMY_RADIUS)) {
    hp--;
    invuln = INVULN_SECONDS;
  }

  const lost = hp <= 0;
  const won = !lost && elapsed >= SURVIVE_SECONDS;
  return {
    elapsed, hp: Math.max(0, hp), kills, spawned, enemies, player, invuln, attackCd, spawnCd, nextWave, flash, rng,
    finished: lost || won, won,
  };
}
