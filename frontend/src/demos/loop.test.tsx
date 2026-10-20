// Bucle de juego: sin requestAnimationFrame huérfano al pausar o desmontar.
import { renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MAX_DT, useGameLoop } from './loop';

function stubRaf() {
  const pending = new Map<number, FrameRequestCallback>();
  let next = 1;
  vi.stubGlobal('requestAnimationFrame', (cb: FrameRequestCallback) => { pending.set(next, cb); return next++; });
  vi.stubGlobal('cancelAnimationFrame', (id: number) => { pending.delete(id); });
  const tick = (now: number) => {
    const [id, cb] = [...pending.entries()][0];
    pending.delete(id);
    cb(now);
  };
  return { pending, tick };
}

describe('useGameLoop', () => {
  it('limita dt y no deja fotogramas pendientes al pausar ni al desmontar', () => {
    const raf = stubRaf();
    const dts: number[] = [];
    const { rerender, unmount } = renderHook(({ paused }) => useGameLoop((dt) => dts.push(dt), () => {}, paused), { initialProps: { paused: false } });

    raf.tick(0);
    raf.tick(10_000); // vuelta de otra pestaña: 10 s
    expect(dts).toEqual([0, MAX_DT]);

    rerender({ paused: true });
    expect(raf.pending.size).toBe(0);

    rerender({ paused: false });
    expect(raf.pending.size).toBe(1);
    unmount();
    expect(raf.pending.size).toBe(0);
  });
});
