import { useState } from 'react';
import { Link } from 'react-router';
import type { GameDetail } from '@/api/types';
import { catalogApi } from '@/api/endpoints';
import Icon from '@/components/Icon';
import { Empty, Loading, PageHeader } from '@/components/ui';
import { DemoPlayer } from '@/demos/DemoPlayer';
import { DEMOS } from '@/demos/registry';
import { useI18n } from '@/i18n';
import { cardImage } from '@/lib/format';
import { useAsync } from '@/lib/useAsync';

/** Risolve ogni demo rispetto al catalogo: se il gioco non è pubblicato (404) o fallisce, la sua card non appare. */
async function loadPublished(): Promise<Map<string, GameDetail>> {
  const results = await Promise.allSettled(DEMOS.map((d) => catalogApi.get(d.slug)));
  const found = new Map<string, GameDetail>();
  results.forEach((r, i) => { if (r.status === 'fulfilled') found.set(DEMOS[i].slug, r.value); });
  return found;
}

export default function Demos() {
  const { t } = useI18n();
  const games = useAsync(loadPublished, []);
  const [playing, setPlaying] = useState<GameDetail | null>(null);

  return (
    <section className="section">
      <PageHeader eyebrow={t('demos.eyebrow')} title={t('demos.title')} />
      {games.loading && !games.data && <Loading />}
      {games.data && games.data.size === 0 && (
        <Empty icon="play" title={t('demos.empty')}>
          <Link className="primary small" to="/browse">{t('common.browseGames')}</Link>
        </Empty>
      )}
      <div className="demo-grid">
        {DEMOS.map((def) => {
          const game = games.data?.get(def.slug);
          if (!game) return null;
          return (
            <article key={def.slug} className="demo-card">
              <div className="demo-cover">
                {game.coverUrl ? <img src={cardImage(game.coverUrl)} alt="" loading="lazy" /> : <div className="cover-fallback">{game.title}</div>}
              </div>
              <div className="demo-card-body">
                <h3>{game.title}</h3>
                <p>{t(def.mechanicKey)}</p>
                <small>{t(def.controlsKey)}</small>
                <button type="button" className="primary small" onClick={() => setPlaying(game)}>
                  <Icon name="play" size={16} /> {t('demos.play')}
                </button>
              </div>
            </article>
          );
        })}
      </div>
      {playing && <DemoPlayer game={playing} onClose={() => setPlaying(null)} />}
    </section>
  );
}
