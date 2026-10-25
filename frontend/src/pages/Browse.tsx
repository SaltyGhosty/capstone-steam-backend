// Catalogo con i filtri della Criteria API. Tutto lo stato vive nell'URL (?q=&genres=&…),
// così si può condividere il link o tornare indietro senza perdere la ricerca.
import { useSearchParams } from 'react-router';
import { catalogApi } from '@/api/endpoints';
import type { GameSort } from '@/api/types';
import GameCard from '@/components/GameCard';
import { Empty, ErrorState, Loading, PageHeader, Pagination } from '@/components/ui';
import { useI18n } from '@/i18n';
import { genreName } from '@/lib/format';
import { useAsync } from '@/lib/useAsync';

const SORTS: GameSort[] = ['newest', 'best_selling', 'top_rated', 'price_asc', 'price_desc', 'title'];

const PAGE_SIZE = 12;

export default function Browse() {
  const [params, setParams] = useSearchParams();
  const { t } = useI18n();
  const q = params.get('q') ?? '';
  const genres = params.get('genres')?.split(',').filter(Boolean) ?? [];
  const maxPrice = params.get('maxPrice') ?? '';
  const minPositive = params.get('minPositivePercent') ?? '';
  const onSale = params.get('onSale') === 'true';
  const sort = (params.get('sort') as GameSort) || 'newest';
  const page = Number(params.get('page') ?? 0);

  const genresRes = useAsync(() => catalogApi.genres(), []);
  const key = params.toString();
  const results = useAsync(
    () => catalogApi.search({
      q: q || undefined,
      genres,
      maxPrice: maxPrice ? Number(maxPrice) : undefined,
      minPositivePercent: minPositive ? Number(minPositive) : undefined,
      onSale: onSale || undefined,
      sort,
      page,
      size: PAGE_SIZE,
    }),
    [key],
  );

  const update = (changes: Record<string, string | null>, keepPage = false) => {
    const next = new URLSearchParams(params);
    for (const [k, v] of Object.entries(changes)) {
      if (v === null || v === '') next.delete(k);
      else next.set(k, v);
    }
    if (!keepPage) next.delete('page');
    setParams(next);
  };

  const toggleGenre = (slug: string) => {
    const next = genres.includes(slug) ? genres.filter((g) => g !== slug) : [...genres, slug];
    update({ genres: next.join(',') || null });
  };

  const hasFilters = Boolean(q || genres.length || maxPrice || minPositive || onSale);
  const data = results.data;

  return (
    <>
      <PageHeader eyebrow={t('browse.eyebrow')} title={q ? t('browse.resultsFor', { q }) : t('browse.title')}>
        <label className="select">
          <span>{t('browse.sortBy')}</span>
          <select value={sort} onChange={(e) => update({ sort: e.target.value })}>
            {SORTS.map((s) => <option key={s} value={s}>{t(`browse.sort.${s}`)}</option>)}
          </select>
        </label>
      </PageHeader>

      <div className="browse">
        <aside className="filters" aria-label={t('browse.filters')}>
          <div className="filter-group">
            <h4>{t('browse.genres')}</h4>
            <div className="chips">
              {genresRes.data?.map((g) => (
                <button key={g.id} className={genres.includes(g.slug) ? 'chip active' : 'chip'}
                  aria-pressed={genres.includes(g.slug)} onClick={() => toggleGenre(g.slug)}>
                  {genreName(g)}
                </button>
              ))}
            </div>
          </div>
          <div className="filter-group">
            <h4>{t('browse.maxPrice')}</h4>
            <div className="chips">
              {['5', '10', '20', '40'].map((v) => (
                <button key={v} className={maxPrice === v ? 'chip active' : 'chip'} aria-pressed={maxPrice === v}
                  onClick={() => update({ maxPrice: maxPrice === v ? null : v })}>
                  {t('browse.upTo', { v })}
                </button>
              ))}
              <button className={maxPrice === '0' ? 'chip active' : 'chip'} aria-pressed={maxPrice === '0'}
                onClick={() => update({ maxPrice: maxPrice === '0' ? null : '0' })}>{t('common.free')}</button>
            </div>
          </div>
          <div className="filter-group">
            <h4>{t('browse.minPositive')}</h4>
            <div className="chips">
              {['70', '80', '95'].map((v) => (
                <button key={v} className={minPositive === v ? 'chip active' : 'chip'} aria-pressed={minPositive === v}
                  onClick={() => update({ minPositivePercent: minPositive === v ? null : v })}>
                  {t('browse.minPositiveChip', { percent: v })}
                </button>
              ))}
            </div>
          </div>
          <label className="check">
            <input type="checkbox" checked={onSale} onChange={(e) => update({ onSale: e.target.checked ? 'true' : null })} />
            {t('browse.onlySale')}
          </label>
          {hasFilters && (
            <button className="secondary small full" onClick={() => setParams(sort !== 'newest' ? { sort } : {})}>
              {t('browse.clear')}
            </button>
          )}
        </aside>

        <section className="results">
          {results.loading && !data && <Loading />}
          {results.error ? <ErrorState error={results.error} onRetry={results.reload} /> : null}
          {data && (
            <>
              <p className="result-count">{t('common.games', { count: data.totalElements })}</p>
              {data.content.length === 0 ? (
                <Empty icon="search" title={t('browse.empty')}>
                  <p>{t('browse.emptyHint')}</p>
                </Empty>
              ) : (
                <div className={results.loading ? 'game-grid three is-loading' : 'game-grid three'}>
                  {data.content.map((game) => <GameCard key={game.id} game={game} />)}
                </div>
              )}
              <Pagination page={data.page} totalPages={data.totalPages}
                onChange={(p) => update({ page: p ? String(p) : null }, true)} />
            </>
          )}
        </section>
      </div>
    </>
  );
}
