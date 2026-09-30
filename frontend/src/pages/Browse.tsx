// Catálogo con los filtros de la Criteria API. Todo el estado vive en la URL (?q=&genres=&…),
// así que se puede compartir el enlace o volver atrás sin perder la búsqueda.
import { useSearchParams } from 'react-router';
import { catalogApi } from '@/api/endpoints';
import type { GameSort } from '@/api/types';
import GameCard from '@/components/GameCard';
import { Empty, ErrorState, Loading, PageHeader, Pagination } from '@/components/ui';
import { useAsync } from '@/lib/useAsync';

const SORTS: { value: GameSort; label: string }[] = [
  { value: 'newest', label: 'Más recientes' },
  { value: 'best_selling', label: 'Más vendidos' },
  { value: 'top_rated', label: 'Mejor valorados' },
  { value: 'price_asc', label: 'Precio: menor a mayor' },
  { value: 'price_desc', label: 'Precio: mayor a menor' },
  { value: 'title', label: 'Título (A-Z)' },
];

const PAGE_SIZE = 12;

export default function Browse() {
  const [params, setParams] = useSearchParams();
  const q = params.get('q') ?? '';
  const genres = params.get('genres')?.split(',').filter(Boolean) ?? [];
  const maxPrice = params.get('maxPrice') ?? '';
  const minRating = params.get('minRating') ?? '';
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
      minRating: minRating ? Number(minRating) : undefined,
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

  const hasFilters = Boolean(q || genres.length || maxPrice || minRating || onSale);
  const data = results.data;

  return (
    <>
      <PageHeader eyebrow="CATÁLOGO" title={q ? `Resultados para “${q}”` : 'Explorar juegos'}>
        <label className="select">
          <span>Ordenar</span>
          <select value={sort} onChange={(e) => update({ sort: e.target.value })}>
            {SORTS.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
          </select>
        </label>
      </PageHeader>

      <div className="browse">
        <aside className="filters" aria-label="Filtros">
          <div className="filter-group">
            <h4>Géneros</h4>
            <div className="chips">
              {genresRes.data?.map((g) => (
                <button key={g.id} className={genres.includes(g.slug) ? 'chip active' : 'chip'}
                  aria-pressed={genres.includes(g.slug)} onClick={() => toggleGenre(g.slug)}>
                  {g.name}
                </button>
              ))}
            </div>
          </div>
          <div className="filter-group">
            <h4>Precio máximo</h4>
            <div className="chips">
              {['5', '10', '20', '40'].map((v) => (
                <button key={v} className={maxPrice === v ? 'chip active' : 'chip'} aria-pressed={maxPrice === v}
                  onClick={() => update({ maxPrice: maxPrice === v ? null : v })}>
                  Hasta {v} €
                </button>
              ))}
              <button className={maxPrice === '0' ? 'chip active' : 'chip'} aria-pressed={maxPrice === '0'}
                onClick={() => update({ maxPrice: maxPrice === '0' ? null : '0' })}>Gratis</button>
            </div>
          </div>
          <div className="filter-group">
            <h4>Valoración mínima</h4>
            <div className="chips">
              {['3', '4', '4.5'].map((v) => (
                <button key={v} className={minRating === v ? 'chip active' : 'chip'} aria-pressed={minRating === v}
                  onClick={() => update({ minRating: minRating === v ? null : v })}>
                  ★ {v.replace('.', ',')}+
                </button>
              ))}
            </div>
          </div>
          <label className="check">
            <input type="checkbox" checked={onSale} onChange={(e) => update({ onSale: e.target.checked ? 'true' : null })} />
            Solo juegos en oferta
          </label>
          {hasFilters && (
            <button className="secondary small full" onClick={() => setParams(sort !== 'newest' ? { sort } : {})}>
              Quitar filtros
            </button>
          )}
        </aside>

        <section className="results">
          {results.loading && !data && <Loading />}
          {results.error ? <ErrorState error={results.error} onRetry={results.reload} /> : null}
          {data && (
            <>
              <p className="result-count">{data.totalElements} {data.totalElements === 1 ? 'juego' : 'juegos'}</p>
              {data.content.length === 0 ? (
                <Empty icon="search" title="No hay juegos con estos filtros">
                  <p>Prueba con otra búsqueda o quita algún filtro.</p>
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
