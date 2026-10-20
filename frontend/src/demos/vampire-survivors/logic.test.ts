import { describe, expect, it } from 'vitest';
import type { DemoInput } from '../types';
import { SURVIVE_SECONDS, createSurvivors, stepSurvivors, type SurvivorsState } from './logic';

const NONE: DemoInput = { left: false, right: false, up: false, down: false, action: false, secondary: false };

/** Estado sin spawns ni ataque inmediatos, para probar una sola regla cada vez. */
function quiet(overrides: Partial<SurvivorsState> = {}): SurvivorsState {
  return { ...createSurvivors(1), spawnCd: 100, attackCd: 100, nextWave: 1000, ...overrides };
}

describe('supervivencia estilo Vampire Survivors', () => {
  it('aguantar 60 s gana', () => {
    let s: SurvivorsState = { ...createSurvivors(7), hp: 10_000 };
    for (let i = 0; i < 60 / 0.05 + 5 && !s.finished; i++) s = stepSurvivors(s, 0.05, NONE);
    expect(s.elapsed).toBeGreaterThanOrEqual(SURVIVE_SECONDS);
    expect(s.finished).toBe(true);
    expect(s.won).toBe(true);
  });

  it('tocar un enemigo quita vida con invulnerabilidad breve', () => {
    const base = quiet();
    let s = { ...base, enemies: [{ x: base.player.x, y: base.player.y, fast: false }] };
    s = stepSurvivors(s, 0.016, NONE);
    s = stepSurvivors(s, 0.016, NONE);
    expect(s.hp).toBe(base.hp - 1);
    // pasada la invulnerabilidad vuelve a hacer daño
    s = { ...s, invuln: 0 };
    expect(stepSurvivors(s, 0.016, NONE).hp).toBe(base.hp - 2);
  });

  it('con 0 de vida pierde', () => {
    const base = quiet({ hp: 1 });
    const s = stepSurvivors({ ...base, enemies: [{ x: base.player.x, y: base.player.y, fast: false }] }, 0.016, NONE);
    expect(s.hp).toBe(0);
    expect(s.finished).toBe(true);
    expect(s.won).toBe(false);
  });

  it('el ataque automático elimina al enemigo más cercano dentro del radio', () => {
    const base = quiet({ attackCd: 0 });
    const s = stepSurvivors(
      { ...base, enemies: [{ x: base.player.x + 40, y: base.player.y, fast: false }, { x: base.player.x + 300, y: base.player.y, fast: false }] },
      0.016, NONE,
    );
    expect(s.kills).toBe(1);
    expect(s.enemies).toHaveLength(1);
    expect(s.enemies[0].x).toBeGreaterThan(base.player.x + 200);
  });

  it('el ataque no alcanza a un enemigo lejano', () => {
    const base = quiet({ attackCd: 0 });
    const s = stepSurvivors({ ...base, enemies: [{ x: base.player.x + 250, y: base.player.y, fast: false }] }, 0.016, NONE);
    expect(s.kills).toBe(0);
  });

  it('aparecen más enemigos con el tiempo', () => {
    let s: SurvivorsState = { ...createSurvivors(3), hp: 1_000_000 };
    const spawnedAt: Record<number, number> = {};
    for (let i = 1; i <= 1200; i++) {
      s = stepSurvivors(s, 0.05, NONE);
      if (i % 200 === 0) spawnedAt[i / 20] = s.spawned; // en segundos: 10, 20, ... 60
    }
    const first10 = spawnedAt[10];
    const last10 = spawnedAt[60] - spawnedAt[50];
    expect(last10).toBeGreaterThan(first10);
  });

  it('el jugador se mueve con las teclas y no sale de la arena', () => {
    let s = quiet();
    for (let i = 0; i < 200; i++) s = stepSurvivors(s, 0.05, { ...NONE, left: true, up: true });
    expect(s.player.x).toBeGreaterThanOrEqual(0);
    expect(s.player.y).toBeGreaterThanOrEqual(0);
    expect(s.player.x).toBeLessThan(20);
  });

  it('con la misma semilla la partida es idéntica', () => {
    const run = (seed: number) => {
      let s = createSurvivors(seed);
      for (let i = 0; i < 100; i++) s = stepSurvivors(s, 0.05, NONE);
      return s.enemies.map((e) => [Math.round(e.x), Math.round(e.y)]);
    };
    expect(run(5)).toEqual(run(5));
  });
});
