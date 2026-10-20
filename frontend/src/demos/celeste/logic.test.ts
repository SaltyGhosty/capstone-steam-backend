import { describe, expect, it } from 'vitest';
import { clampDt } from '../loop';
import type { DemoInput } from '../types';
import { CELESTE_ROOMS, createCeleste, stepCeleste, type CelesteState } from './logic';

const NONE: DemoInput = { left: false, right: false, up: false, down: false, action: false, secondary: false };
const press = (k: Partial<DemoInput>): DemoInput => ({ ...NONE, ...k });

/** Deja caer al jugador sobre la plataforma de salida. */
function settled(): CelesteState {
  let s = createCeleste();
  for (let i = 0; i < 10; i++) s = stepCeleste(s, 1 / 60, NONE);
  return s;
}

/** Estado en el aire, lejos de cualquier plataforma. */
function airborne(): CelesteState {
  const s = settled();
  return { ...s, player: { ...s.player, x: 600, y: 30, vx: 0, vy: 0, onGround: false, coyote: 0, canDash: true } };
}

describe('plataformas estilo Celeste', () => {
  it('salta solo desde el suelo', () => {
    const ground = settled();
    expect(ground.player.onGround).toBe(true);
    expect(stepCeleste(ground, 1 / 60, press({ action: true })).player.vy).toBeLessThan(0);

    const air = airborne();
    expect(stepCeleste(air, 1 / 60, press({ action: true })).player.vy).toBeGreaterThanOrEqual(0);
  });

  it('el dash solo se usa una vez en el aire y se recarga al aterrizar', () => {
    let s = airborne();
    s = stepCeleste(s, 1 / 60, press({ secondary: true, right: true }));
    expect(s.player.canDash).toBe(false);
    expect(s.player.dashT).toBeGreaterThan(0);
    expect(s.player.vx).toBeGreaterThan(300);

    // un segundo dash en el aire no hace nada (cuando acaba el primero)
    s = { ...s, player: { ...s.player, dashT: 0, vx: 0, vy: 0 } };
    const again = stepCeleste(s, 1 / 60, press({ secondary: true, right: true }));
    expect(again.player.dashT).toBe(0);

    // al aterrizar se recarga
    const ground = settled();
    const used = { ...ground, player: { ...ground.player, canDash: false } };
    expect(stepCeleste(used, 1 / 60, NONE).player.canDash).toBe(true);
  });

  it('caer por debajo de la sala la reinicia y suma una muerte', () => {
    const s = airborne();
    const fell = { ...s, player: { ...s.player, y: 395, vy: 500 } };
    const next = stepCeleste(fell, 0.05, NONE);
    expect(next.deaths).toBe(1);
    expect(next.room).toBe(0);
    expect(next.player.x).toBe(CELESTE_ROOMS[0].spawn.x);
    expect(next.player.y).toBe(CELESTE_ROOMS[0].spawn.y);
  });

  it('salir por arriba de la sala 0 pasa a la sala 1', () => {
    const s = airborne();
    const up = { ...s, player: { ...s.player, y: -9, vy: -200 } };
    const next = stepCeleste(up, 1 / 60, NONE);
    expect(next.room).toBe(1);
    expect(next.player.x).toBe(CELESTE_ROOMS[1].spawn.x);
    expect(next.deaths).toBe(0);
  });

  it('llegar a la cumbre de la sala 2 termina la partida', () => {
    const goal = CELESTE_ROOMS[2].goal!;
    const s = airborne();
    const atGoal = { ...s, room: 2 as const, player: { ...s.player, x: goal.x, y: goal.y, vx: 0, vy: 0 } };
    const next = stepCeleste(atGoal, 1 / 60, NONE);
    expect(next.finished).toBe(true);
    // una vez terminada, el tiempo no sigue corriendo
    expect(stepCeleste(next, 1, NONE).elapsed).toBe(next.elapsed);
  });

  it('no atraviesa un suelo con dt grande', () => {
    let s = airborne();
    const start = CELESTE_ROOMS[0].solids[0];
    s = { ...s, player: { ...s.player, x: start.x + 20, y: start.y - 120, vy: 900 } };
    for (let i = 0; i < 20; i++) s = stepCeleste(s, clampDt(5), NONE);
    expect(s.player.y).toBeLessThanOrEqual(start.y - 16);
    expect(s.deaths).toBe(0);
    expect(s.player.onGround).toBe(true);
  });

  it('cada sala tiene una salida alcanzable: saltos y huecos dentro del alcance', () => {
    // Un salto sube unos 75 px y recorre unos 99 px; con dash se suman unos 53 px
    const JUMP_UP = 75;
    const REACH = 99 + 53;
    for (const room of CELESTE_ROOMS) {
      const sorted = [...room.solids].sort((a, b) => b.y - a.y); // de abajo arriba
      for (let i = 1; i < sorted.length; i++) {
        const lower = sorted[i - 1];
        const upper = sorted[i];
        expect(lower.y - upper.y).toBeLessThanOrEqual(JUMP_UP + 53);
        const gap = Math.max(upper.x - (lower.x + lower.w), lower.x - (upper.x + upper.w), 0);
        expect(gap).toBeLessThanOrEqual(REACH);
      }
    }
  });
});
