import { Suspense, lazy, useCallback, useEffect, useId, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router';
import { libraryApi } from '@/api/endpoints';
import type { GameDetail, GameSummary } from '@/api/types';
import Icon from '@/components/Icon';
import { Loading } from '@/components/ui';
import { useStore } from '@/context/StoreContext';
import { useI18n } from '@/i18n';
import { trapTab } from '@/lib/dialog';
import { useAsync } from '@/lib/useAsync';
import { DemoErrorBoundary } from './DemoErrorBoundary';
import { useGameKeyGuard } from './loop';
import { demoFor } from './registry';
import type { DemoResult } from './types';

function isTouchDevice(): boolean {
  return typeof window.matchMedia === 'function' && window.matchMedia('(pointer: coarse)').matches;
}

/**
 * Diálogo a pantalla grande con la demo de un juego. Se pausa solo cuando la ventana pierde el foco
 * o la pestaña se oculta, y sigue pausado hasta que el jugador pulsa «Continuar». El foco se queda
 * dentro mientras está abierto y vuelve a donde estaba al cerrarlo.
 */
export function DemoPlayer({ game, onClose }: { game: GameSummary | GameDetail; onClose: () => void }) {
  const { t } = useI18n();
  const { user, booting, addToCart } = useStore();
  const def = demoFor(game.slug);
  const titleId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);
  const actionsRef = useRef<HTMLDivElement>(null);
  const resumeRef = useRef<HTMLButtonElement>(null);
  const opener = useRef<Element | null>(document.activeElement);
  const [result, setResult] = useState<DemoResult | null>(null);
  const [round, setRound] = useState(0);
  const [paused, setPaused] = useState(false);
  const [added, setAdded] = useState(false);
  const touch = isTouchDevice();

  // El componente del minijuego se crea una vez por juego (no por render) para no volver a montarlo
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const Game = useMemo(() => (def ? lazy(def.load) : null), [game.slug]);

  useGameKeyGuard();

  useEffect(() => {
    const pause = () => setPaused(true);
    const onVisibility = () => { if (document.hidden) setPaused(true); };
    const onKey = (e: globalThis.KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('blur', pause);
    document.addEventListener('visibilitychange', onVisibility);
    window.addEventListener('keydown', onKey);
    return () => {
      window.removeEventListener('blur', pause);
      document.removeEventListener('visibilitychange', onVisibility);
      window.removeEventListener('keydown', onKey);
    };
  }, [onClose]);

  // Al abrir: la página de fondo no se desplaza y el foco entra en el diálogo. Al cerrar, vuelve al botón que lo abrió.
  useEffect(() => {
    const previous = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    dialogRef.current?.focus();
    const from = opener.current;
    const keepFocusInside = (e: FocusEvent) => {
      if (dialogRef.current && e.target instanceof Node && !dialogRef.current.contains(e.target)) dialogRef.current.focus();
    };
    document.addEventListener('focusin', keepFocusInside);
    return () => {
      document.body.style.overflow = previous;
      document.removeEventListener('focusin', keepFocusInside);
      if (from instanceof HTMLElement && from.isConnected) from.focus();
    };
  }, []);

  // Se consulta al abrir (no al terminar) para que la pantalla final sepa ya qué ofrecer
  const ownership = useAsync(
    () => (user ? libraryApi.status(game.id) : Promise.resolve(null)),
    [user?.id, game.id],
  );
  const statusReady = !booting && !ownership.loading && (!user || ownership.data !== null || ownership.error !== null);

  // «Continuar» recibe el foco al pausar; al reanudar, el foco vuelve al diálogo
  useEffect(() => {
    if (paused) resumeRef.current?.focus();
    else if (document.activeElement === document.body) dialogRef.current?.focus();
  }, [paused]);

  // En la pantalla final, el foco va a la acción principal en cuanto aparece
  useEffect(() => {
    if (result && statusReady) actionsRef.current?.querySelector<HTMLElement>('a, button')?.focus();
  }, [result, statusReady]);

  const finish = useCallback((r: DemoResult) => setResult(r), []);
  const playAgain = () => { setResult(null); setPaused(false); setAdded(false); setRound((n) => n + 1); };

  const isMine = 'developer' in game && user?.id === game.developer.id;
  const own = ownership.data;
  const inCart = added || own?.inCart;

  const onAdd = async () => {
    if (await addToCart(game.id, game.title)) setAdded(true);
  };

  if (!def || !Game) return null;

  return (
    <div className="demo-backdrop">
      <div className="demo-dialog" role="dialog" aria-modal="true" aria-labelledby={titleId} ref={dialogRef} tabIndex={-1} onKeyDown={trapTab}>
        <header>
          <div>
            <h2 id={titleId}>{t('demos.playerTitle', { title: game.title })}</h2>
            <p>{t(def.controlsKey)}</p>
          </div>
          <button type="button" className="icon-button" onClick={onClose} aria-label={t('demos.close')}>
            <Icon name="close" size={16} />
          </button>
        </header>

        <div className="demo-stage">
          <DemoErrorBoundary onClose={onClose}>
            {def.needsKeyboard && touch ? (
              <p className="demo-notice">{t('demos.desktopOnly')}</p>
            ) : result ? (
              <div className="demo-end">
                <p className="eyebrow">{result.won ? t('demos.won') : t('demos.lost')}</p>
                <h3>{t(result.messageKey, result.values)}</h3>
                {!isMine && statusReady && <p className="demo-liked">{t('demos.liked')}</p>}
                <div className="demo-actions" ref={actionsRef}>
                  {!statusReady ? null : isMine ? null : own?.owned ? (
                    <Link className="primary" to="/library"><Icon name="library" size={18} /> {t('demos.owned')}</Link>
                  ) : inCart ? (
                    <Link className="primary" to="/cart"><Icon name="cart" size={18} /> {t('demos.inCart')}</Link>
                  ) : (
                    <button type="button" className="primary" onClick={onAdd}><Icon name="cart" size={18} /> {t('game.addCart')}</button>
                  )}
                  <button type="button" className="secondary" onClick={playAgain}>{t('demos.playAgain')}</button>
                  <button type="button" className="secondary" onClick={onClose}>{t('demos.close')}</button>
                </div>
              </div>
            ) : (
              <>
                <Suspense fallback={<Loading />}>
                  <Game key={round} paused={paused} onFinish={finish} />
                </Suspense>
                {paused && (
                  <div className="demo-paused">
                    <p>{t('demos.paused')}</p>
                    <button type="button" className="primary" ref={resumeRef} onClick={() => setPaused(false)}>{t('demos.resume')}</button>
                  </div>
                )}
              </>
            )}
          </DemoErrorBoundary>
        </div>
      </div>
    </div>
  );
}
