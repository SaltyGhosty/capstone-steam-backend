// Lógica pura del minijuego de plataformas (sin canvas): estado + step(dt, input).
import type { DemoInput } from '../types';

export interface Rect { x: number; y: number; w: number; h: number }

export interface Room {
  spawn: { x: number; y: number };
  solids: Rect[];
  /** Solo la última sala tiene cumbre */
  goal?: Rect;
}

export interface CelestePlayer {
  x: number;
  y: number;
  vx: number;
  vy: number;
  onGround: boolean;
  canDash: boolean;
  /** Segundos que quedan de impulso */
  dashT: number;
  dashDx: number;
  dashDy: number;
  /** Margen para saltar justo después de dejar el borde */
  coyote: number;
  facing: 1 | -1;
}

export interface CelesteState {
  room: 0 | 1 | 2;
  deaths: number;
  elapsed: number;
  finished: boolean;
  player: CelestePlayer;
}

export const WIDTH = 640;
export const HEIGHT = 360;
export const PLAYER_W = 12;
export const PLAYER_H = 16;

const GRAVITY = 1400;
const MAX_FALL = 560;
const MOVE_SPEED = 150;
const GROUND_ACCEL = 1500;
const AIR_ACCEL = 900;
const JUMP_SPEED = 460; // sube unos 75 px
const COYOTE = 0.08;
const DASH_SPEED = 380;
const DASH_TIME = 0.14; // unos 53 px
const FALL_LIMIT = HEIGHT + 20;
const EXIT_TOP = -8;

const plat = (x: number, y: number, w: number, h = 12): Rect => ({ x, y, w, h });
const START = { x: 20, y: 284 };

/** Tres pantallas de 640×360. Un salto sube ~75 px y recorre ~99 px; con el impulso se suman ~53 px. */
export const CELESTE_ROOMS: Room[] = [
  {
    // Sala 0: solo saltos, en zigzag hacia arriba; se sale por el borde superior
    spawn: START,
    solids: [
      plat(0, 300, 100, 60), plat(140, 268, 70), plat(250, 236, 70), plat(360, 204, 70),
      plat(250, 172, 70), plat(140, 140, 70), plat(30, 108, 70), plat(30, 62, 70),
    ],
  },
  {
    // Sala 1: un hueco que pide impulso y una salida alta que también
    spawn: START,
    solids: [
      plat(0, 300, 90, 60), plat(220, 300, 80, 60), plat(350, 262, 70), plat(470, 226, 70),
      plat(570, 190, 60), plat(430, 150, 70), plat(300, 100, 60),
    ],
  },
  {
    // Sala 2: la cumbre, con tres saltos largos que se ayudan con el impulso
    spawn: START,
    solids: [
      plat(0, 300, 80, 60), plat(200, 290, 60), plat(330, 250, 50), plat(470, 230, 50),
      plat(560, 170, 60), plat(400, 120, 50), plat(260, 80, 50), plat(100, 50, 50),
    ],
    goal: { x: 110, y: 26, w: 14, h: 24 },
  },
];

function freshPlayer(room: Room): CelestePlayer {
  return {
    x: room.spawn.x, y: room.spawn.y, vx: 0, vy: 0, onGround: false, canDash: true,
    dashT: 0, dashDx: 1, dashDy: 0, coyote: 0, facing: 1,
  };
}

export function createCeleste(): CelesteState {
  return { room: 0, deaths: 0, elapsed: 0, finished: false, player: freshPlayer(CELESTE_ROOMS[0]) };
}

const hits = (p: { x: number; y: number }, r: Rect) =>
  p.x < r.x + r.w && p.x + PLAYER_W > r.x && p.y < r.y + r.h && p.y + PLAYER_H > r.y;

/** Mueve en pasos de 4 px como máximo: ni con un dt grande se atraviesa una plataforma. */
function moveAxis(p: CelestePlayer, axis: 'x' | 'y', delta: number, solids: Rect[]): boolean {
  const steps = Math.max(1, Math.ceil(Math.abs(delta) / 4));
  const each = delta / steps;
  for (let i = 0; i < steps; i++) {
    p[axis] += each;
    const hit = solids.find((r) => hits(p, r));
    if (hit) {
      if (axis === 'x') p.x = each > 0 ? hit.x - PLAYER_W : hit.x + hit.w;
      else p.y = each > 0 ? hit.y - PLAYER_H : hit.y + hit.h;
      return true;
    }
  }
  return false;
}

const approach = (value: number, target: number, amount: number) =>
  value < target ? Math.min(value + amount, target) : Math.max(value - amount, target);

export function stepCeleste(state: CelesteState, dt: number, input: DemoInput): CelesteState {
  if (state.finished) return state;
  const room = CELESTE_ROOMS[state.room];
  const p: CelestePlayer = { ...state.player };
  const dir = (input.right ? 1 : 0) - (input.left ? 1 : 0);
  if (dir !== 0) p.facing = dir > 0 ? 1 : -1;

  p.coyote = p.onGround ? COYOTE : Math.max(0, p.coyote - dt);

  // Impulso: una vez por salto, solo en el aire
  if (input.secondary && p.canDash && !p.onGround && p.dashT <= 0) {
    let dx = dir;
    let dy = (input.down ? 1 : 0) - (input.up ? 1 : 0);
    if (dx === 0 && dy === 0) dx = p.facing;
    const len = Math.hypot(dx, dy);
    p.dashDx = dx / len;
    p.dashDy = dy / len;
    p.dashT = DASH_TIME;
    p.canDash = false;
    p.vx = p.dashDx * DASH_SPEED;
    p.vy = p.dashDy * DASH_SPEED;
  }

  if (p.dashT > 0) {
    p.dashT -= dt;
    if (p.dashT <= 0) {
      p.dashT = 0;
      p.vx = p.dashDx * MOVE_SPEED;
      p.vy = p.dashDy < 0 ? -140 : p.dashDy > 0 ? 100 : 0;
    }
  } else {
    p.vx = approach(p.vx, dir * MOVE_SPEED, (p.onGround ? GROUND_ACCEL : AIR_ACCEL) * dt);
    if (input.action && p.coyote > 0) {
      p.vy = -JUMP_SPEED;
      p.coyote = 0;
      p.onGround = false;
    }
    // Soltar el salto lo corta: se controla la altura
    const gravity = p.vy < 0 && !input.action ? GRAVITY * 2 : GRAVITY;
    p.vy = Math.min(p.vy + gravity * dt, MAX_FALL);
  }

  moveAxis(p, 'x', p.vx * dt, room.solids);
  if (p.x < 0) { p.x = 0; p.vx = 0; }
  if (p.x > WIDTH - PLAYER_W) { p.x = WIDTH - PLAYER_W; p.vx = 0; }
  const vyBefore = p.vy;
  const landed = moveAxis(p, 'y', p.vy * dt, room.solids);
  if (landed) p.vy = 0;
  p.onGround = landed && vyBefore > 0;
  if (p.onGround) p.canDash = true;
  if (state.room === 2 && p.y < -30) { p.y = -30; p.vy = 0; }

  const next: CelesteState = { ...state, elapsed: state.elapsed + dt, player: p };

  if (p.y > FALL_LIMIT) {
    return { ...next, deaths: state.deaths + 1, player: freshPlayer(room) };
  }
  if (p.y < EXIT_TOP && state.room < 2) {
    const nextRoom = (state.room + 1) as 1 | 2;
    return { ...next, room: nextRoom, player: freshPlayer(CELESTE_ROOMS[nextRoom]) };
  }
  if (room.goal && hits(p, room.goal)) return { ...next, finished: true };
  return next;
}
