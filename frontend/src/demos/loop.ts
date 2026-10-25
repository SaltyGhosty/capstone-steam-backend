import { useEffect, useRef, type RefObject } from 'react';
import type { DemoInput } from './types';

/** Tetto del passo di tempo: tornando da un'altra scheda, `dt` può essere di secondi e attraverserebbe i muri. */
export const MAX_DT = 0.05;

export function clampDt(seconds: number): number {
  return Math.min(Math.max(seconds, 0), MAX_DT);
}

/**
  * Game loop con requestAnimationFrame. Con `paused` (o allo smontaggio) si cancella il frame
  * pendente, quindi non resta nessun rAF orfano. Alla ripresa, il primo `dt` è 0.
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

/** I pulsanti e i campi conservano Spazio/Invio per attivarsi da tastiera. */
function keepsActivationKey(e: KeyboardEvent): boolean {
  const el = e.target;
  return (e.key === ' ' || e.key === 'Enter') && el instanceof HTMLElement && ['BUTTON', 'INPUT', 'A'].includes(el.tagName);
}

/** Scrivendo in un campo (ricerca, chat): le lettere sono sue, non del gioco. */
function isTyping(e: KeyboardEvent): boolean {
  const el = e.target;
  return el instanceof HTMLElement && (['INPUT', 'TEXTAREA', 'SELECT'].includes(el.tagName) || el.isContentEditable);
}

/**
  * Se è un tasto di gioco (frecce, WASD, Spazio, X, Shift), evita lo scroll e interrompe la propagazione
  * perché non arrivi alle scorciatoie globali. Con Ctrl/Meta/Alt non tocca nulla (Ctrl K della ricerca).
  * Restituisce il campo del `DemoInput` che gli corrisponde, o null se non l'ha gestito.
 */
export function guardGameKey(e: KeyboardEvent): keyof DemoInput | null {
  const field = KEY_MAP[e.key];
  if (!field || e.ctrlKey || e.metaKey || e.altKey || keepsActivationKey(e) || isTyping(e)) return null;
  e.preventDefault();
  e.stopPropagation();
  return field;
}

/** Installa `guardGameKey` nella fase di capture di window finché il componente è montato. */
export function useGameKeyGuard(): void {
  useEffect(() => {
    window.addEventListener('keydown', guardGameKey, true);
    return () => window.removeEventListener('keydown', guardGameKey, true);
  }, []);
}

/** Stato della tastiera per i minigiochi canvas. Quando si perde il focus si rilasciano tutti i tasti. */
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
