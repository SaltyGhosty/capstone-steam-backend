import { describe, expect, it } from 'vitest';
import type { DemoInput } from '../types';
import { FIELD_H, FIELD_W, MATCH_SECONDS, createMatch, stepMatch, type MatchState } from './logic';

const NONE: DemoInput = { left: false, right: false, up: false, down: false, action: false, secondary: false };

/** Partido con los coches quietos y lejos del balón, para probar solo el balón. */
function ballOnly(ball: Partial<MatchState['ball']>): MatchState {
  const s = createMatch();
  return {
    ...s,
    player: { ...s.player, x: 60, y: 40 },
    ai: { ...s.ai, x: 580, y: 320, speed: 0 },
    ball: { ...s.ball, ...ball },
  };
}

describe('fútbol 2D estilo Rocket League', () => {
  it('el balón en la portería derecha suma un gol al jugador y saca de centro', () => {
    const s = stepMatch(ballOnly({ x: FIELD_W - 2, y: FIELD_H / 2, vx: 400, vy: 0 }), 0.05, NONE);
    expect(s.score).toEqual({ player: 1, ai: 0 });
    expect(s.ball.x).toBe(FIELD_W / 2);
    expect(s.ball.y).toBe(FIELD_H / 2);
    expect(s.ball.vx).toBe(0);
    expect(s.player.x).toBeLessThan(FIELD_W / 2);
  });

  it('el balón en la portería izquierda suma un gol a la IA', () => {
    const s = stepMatch(ballOnly({ x: 2, y: FIELD_H / 2, vx: -400, vy: 0 }), 0.05, NONE);
    expect(s.score).toEqual({ player: 0, ai: 1 });
  });

  it('el balón rebota en las paredes', () => {
    const top = stepMatch(ballOnly({ x: 300, y: 5, vx: 0, vy: -200 }), 0.016, NONE);
    expect(top.ball.vy).toBeGreaterThan(0);
    const side = stepMatch(ballOnly({ x: FIELD_W - 3, y: 30, vx: 300, vy: 0 }), 0.016, NONE);
    expect(side.score.player).toBe(0);
    expect(side.ball.vx).toBeLessThan(0);
  });

  it('el coche empuja el balón al chocar', () => {
    const s = createMatch();
    const hit = stepMatch(
      { ...s, player: { ...s.player, x: 300, y: 180, angle: 0, speed: 200 }, ball: { ...s.ball, x: 318, y: 180, vx: 0, vy: 0 } },
      0.016, NONE,
    );
    expect(hit.ball.vx).toBeGreaterThan(150);
  });

  it('la IA se mueve hacia el balón', () => {
    let s: MatchState = { ...createMatch(), ball: { x: 200, y: 100, vx: 0, vy: 0 } };
    const before = Math.hypot(s.ai.x - 200, s.ai.y - 100);
    for (let i = 0; i < 40; i++) s = stepMatch(s, 0.05, NONE);
    expect(Math.hypot(s.ai.x - s.ball.x, s.ai.y - s.ball.y)).toBeLessThan(before - 40);
  });

  it('acelerar y girar mueven el coche del jugador; el turbo lo hace más rápido', () => {
    const run = (turbo: boolean) => {
      let s = createMatch();
      for (let i = 0; i < 12; i++) s = stepMatch(s, 0.05, { ...NONE, up: true, action: turbo });
      return s.player.speed;
    };
    expect(run(false)).toBeGreaterThan(100);
    expect(run(true)).toBeGreaterThan(run(false));
    let s = createMatch();
    for (let i = 0; i < 10; i++) s = stepMatch(s, 0.05, { ...NONE, up: true, right: true });
    expect(s.player.angle).toBeGreaterThan(0.3);
  });

  it('el turbo solo se gasta mientras se acelera', () => {
    let idle = createMatch();
    let driving = createMatch();
    for (let i = 0; i < 10; i++) {
      idle = stepMatch(idle, 0.05, { ...NONE, action: true });
      driving = stepMatch(driving, 0.05, { ...NONE, up: true, action: true });
    }
    expect(idle.boost).toBe(100);
    expect(driving.boost).toBeLessThan(100);
  });

  it('a los 60 s termina', () => {
    let s = createMatch();
    for (let i = 0; i < MATCH_SECONDS / 0.05 + 5 && !s.finished; i++) s = stepMatch(s, 0.05, NONE);
    expect(s.finished).toBe(true);
    expect(s.elapsed).toBeGreaterThanOrEqual(MATCH_SECONDS);
  });
});
