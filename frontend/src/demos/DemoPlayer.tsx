import { Suspense, lazy, useCallback, useEffect, useId, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router';
import { libraryApi } from '@/api/endpoints';
import type { GameDetail, GameSummary } from '@/api/types';
import Icon from '@/components/Icon';
import { Loading } from '@/components/ui';
import { useStore } from '@/context/StoreContext';
import { useI18n } from '@/i18n';
import { useAsync } from '@/lib/useAsync';
import { useGameKeyGuard } from './loop';
import { demoFor } from './registry';
import type { DemoResult } from './types';

function isTouchDevice(): boolean {
  return typeof window.matchMedia === 'function' && window.matchMedia('(pointer: coarse)').matches;
}

/**
 * Diálogo a pantalla grande con la demo de un juego. Se pausa solo cuando la ventana pierde el foco
 * o la pestaña se oculta, y sigue pausado hasta que el jugador pulsa «Continuar».
 */
export function DemoPlayer({ game, onClose }: { game: GameSummary | GameDetail; onClose: () => void }) {
  const { t } = useI18n();
  const { user, addToCart } = useStore();
  const def = demoFor(game.slug);
  const titleId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);
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
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('blur', pause);
    document.addEventListener('visibilitychange', onVisibility);
    window.addEventListener('keydown', onKey);
    return () => {
      window.removeEventListener('blur', pause);
      document.removeEventListener('visibilitychange', onVisibility);
      window.removeEventListener('keydown', onKey);
    };
  }, [onClose]);

  // Mientras está abierto, la página de fondo no se desplaza
  useEffect(() => {
    const previous = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    dialogRef.current?.focus();
    return () => { document.body.style.overflow = previous; };
  }, []);

  const ownership = useAsync(
    () => (user && result ? libraryApi.status(game.id) : Promise.resolve(null)),
    [user?.id, game.id, result !== null],
  );

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
      <div className="demo-dialog" role="dialog" aria-modal="true" aria-labelledby={titleId} ref={dialogRef} tabIndex={-1}>
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
          {def.needsKeyboard && touch ? (
            <p className="demo-notice">{t('demos.desktopOnly')}</p>
          ) : result ? (
            <div className="demo-end">
              <p className="eyebrow">{result.won ? t('demos.won') : t('demos.lost')}</p>
              <h3>{t(result.messageKey, result.values)}</h3>
              {!isMine && <p className="demo-liked">{t('demos.liked')}</p>}
              <div className="demo-actions">
                {isMine ? null : own?.owned ? (
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
                  <button type="button" className="primary" onClick={() => setPaused(false)}>{t('demos.resume')}</button>
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
