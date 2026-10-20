import { useEffect, useRef, type RefObject } from 'react';
import type { DemoInput } from './types';

/** Tope del paso de tiempo: tras volver de otra pestaña, `dt` puede ser de segundos y atravesaría paredes. */
export const MAX_DT = 0.05;

export function clampDt(seconds: number): number {
  return Math.min(Math.max(seconds, 0), MAX_DT);
}

/**
 * Bucle de juego con requestAnimationFrame. Con `paused` (o al desmontar) se cancela el fotograma
 * pendiente, así que no queda ningún rAF huérfano. Al reanudar, el primer `dt` es 0.
 */
export function useGameLoop(step: (dt: number) => void, draw: () => void, paused: boolean): void {
  const stepRef = useRef(step);
  const drawRef = useRef(draw);
  stepRef.current = step;
  drawRef.current = draw;

  useEffect(() => {
    if (paused) {
      drawRef.current();
      return;
    }
    let frame = 0;
    let last: number | null = null;
    const tick = (now: number) => {
      const dt = last === null ? 0 : clampDt((now - last) / 1000);
      last = now;
      stepRef.current(dt);
      drawRef.current();
      frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [paused]);
}

const EMPTY: DemoInput = { left: false, right: false, up: false, down: false, action: false, secondary: false };

const KEY_MAP: Record<string, keyof DemoInput> = {
  ArrowLeft: 'left', a: 'left', A: 'left',
  ArrowRight: 'right', d: 'right', D: 'right',
  ArrowUp: 'up', w: 'up', W: 'up',
  ArrowDown: 'down', s: 'down', S: 'down',
  ' ': 'action',
  x: 'secondary', X: 'secondary', Shift: 'secondary',
};

/** Los botones y campos conservan Espacio/Intro para activarse con el teclado. */
function keepsActivationKey(e: KeyboardEvent): boolean {
  const el = e.target;
  return (e.key === ' ' || e.key === 'Enter') && el instanceof HTMLElement && ['BUTTON', 'INPUT', 'A'].includes(el.tagName);
}

/** Escribiendo en un campo (buscador, chat): las letras son suyas, no del juego. */
function isTyping(e: KeyboardEvent): boolean {
  const el = e.target;
  return el instanceof HTMLElement && (['INPUT', 'TEXTAREA', 'SELECT'].includes(el.tagName) || el.isContentEditable);
}

/**
 * Si es una tecla de juego (flechas, WASD, Espacio, X, Shift), evita el scroll y corta la propagación
 * para que no llegue a atajos globales. Con Ctrl/Meta/Alt no toca nada (Ctrl K del buscador).
 * Devuelve el campo del `DemoInput` que le corresponde, o null si no la ha tratado.
 */
export function guardGameKey(e: KeyboardEvent): keyof DemoInput | null {
  const field = KEY_MAP[e.key];
  if (!field || e.ctrlKey || e.metaKey || e.altKey || keepsActivationKey(e) || isTyping(e)) return null;
  e.preventDefault();
  e.stopPropagation();
  return field;
}

/** Instala `guardGameKey` en la fase de captura de window mientras el componente está montado. */
export function useGameKeyGuard(): void {
  useEffect(() => {
    window.addEventListener('keydown', guardGameKey, true);
    return () => window.removeEventListener('keydown', guardGameKey, true);
  }, []);
}

/** Estado del teclado para los minijuegos de canvas. Al perder el foco se sueltan todas las teclas. */
export function useDemoKeys(): RefObject<DemoInput> {
  const input = useRef<DemoInput>({ ...EMPTY });

  useEffect(() => {
    const onKey = (down: boolean) => (e: KeyboardEvent) => {
      const field = guardGameKey(e);
      if (field) input.current[field] = down;
    };
    const keydown = onKey(true);
    const keyup = onKey(false);
    const release = () => { input.current = { ...EMPTY }; };
    window.addEventListener('keydown', keydown, true);
    window.addEventListener('keyup', keyup, true);
    window.addEventListener('blur', release);
    return () => {
      window.removeEventListener('keydown', keydown, true);
      window.removeEventListener('keyup', keyup, true);
      window.removeEventListener('blur', release);
    };
  }, []);

  return input;
}
