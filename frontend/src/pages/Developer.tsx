// Panel del estudio: sus juegos (todos los estados), alta/edición, publicar y descuento masivo.
import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { ApiError } from '@/api/client';
import { catalogApi, developerApi } from '@/api/endpoints';
import type { GameDetail } from '@/api/types';
import Icon from '@/components/Icon';
import { Empty, ErrorState, FieldError, Loading, PageHeader } from '@/components/ui';
import { useStore } from '@/context/StoreContext';
import { formatMoney, GAME_STATUS } from '@/lib/format';
import { useAsync } from '@/lib/useAsync';

export function DeveloperGames() {
  const { notify, notifyError } = useStore();
  const games = useAsync(() => developerApi.list(0, 100), []);
  const [discount, setDiscount] = useState('');

  const act = async (fn: () => Promise<unknown>, message: string) => {
    try {
      await fn();
      notify(message);
      games.reload();
    } catch (e) {
      notifyError(e);
    }
  };

  const applyDiscount = async (e: FormEvent) => {
    e.preventDefault();
    const pct = Number(discount);
    if (!Number.isInteger(pct) || pct < 0 || pct > 100) return notifyError(new ApiError(400, 'El descuento va de 0 a 100'));
    try {
      const res = await developerApi.discountAll(pct);
      notify(`Descuento del ${pct}% aplicado a ${res.updated} juegos`);
      setDiscount('');
      games.reload();
    } catch (err) {
      notifyError(err);
    }
  };

  const list = games.data?.content ?? [];
  const published = list.filter((g) => g.status === 'PUBLISHED');
  const sales = list.reduce((sum, g) => sum + g.salesCount, 0);

  return (
    <>
      <PageHeader eyebrow="TU ESTUDIO" title="Mis juegos">
        <Link className="primary small" to="/developer/games/new"><Icon name="plus" size={16} /> Nuevo juego</Link>
      </PageHeader>

      <div className="stats">
        <div className="panel stat"><small>Juegos</small><b>{games.data ? list.length : '—'}</b></div>
        <div className="panel stat"><small>Publicados</small><b>{games.data ? published.length : '—'}</b></div>
        <div className="panel stat"><small>Copias vendidas</small><b>{games.data ? sales : '—'}</b></div>
        <form className="panel stat discount" onSubmit={applyDiscount}>
          <small>Descuento a todos los publicados</small>
          <div>
            <input type="number" min={0} max={100} value={discount} onChange={(e) => setDiscount(e.target.value)} placeholder="%" aria-label="Porcentaje de descuento" />
            <button className="secondary small" disabled={discount === ''}>Aplicar</button>
          </div>
        </form>
      </div>

      {games.loading && !games.data && <Loading />}
      {games.error ? <ErrorState error={games.error} onRetry={games.reload} /> : null}
      {games.data && list.length === 0 && (
        <Empty icon="code" title="Todavía no has creado ningún juego">
          <Link className="primary small" to="/developer/games/new">Crear el primero</Link>
        </Empty>
      )}
      {list.length > 0 && (
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th>Juego</th><th>Estado</th><th>Precio</th><th>Ventas</th><th>Valoración</th><th /></tr></thead>
            <tbody>
              {list.map((g) => (
                <tr key={g.id}>
                  <td><div className="game-cell">
                    {g.coverUrl && <img src={g.coverUrl} alt="" />}
                    {g.status === 'PUBLISHED' ? <Link to={`/games/${g.slug}`}>{g.title}</Link> : <span>{g.title}</span>}
                  </div></td>
                  <td><span className={`badge ${g.status.toLowerCase()}`}>{GAME_STATUS[g.status]}</span></td>
                  <td>{formatMoney(g.finalPrice)}{g.discountPercent > 0 && <small className="muted"> (−{g.discountPercent}%)</small>}</td>
                  <td>{g.salesCount}</td>
                  <td>{g.ratingCount ? `${g.averageRating.toFixed(1)} ★ (${g.ratingCount})` : '—'}</td>
                  <td className="row-actions">
                    <Link className="icon-button" to={`/developer/games/${g.id}`} aria-label={`Editar ${g.title}`}><Icon name="edit" size={16} /></Link>
                    {g.status === 'DRAFT' && <button className="secondary small" onClick={() => act(() => developerApi.publish(g.id), `${g.title} publicado`)}>Publicar</button>}
                    {g.status === 'PUBLISHED' && <button className="secondary small" onClick={() => act(() => developerApi.unpublish(g.id), `${g.title} pasa a borrador`)}>Despublicar</button>}
                    {g.status === 'DRAFT' && g.salesCount === 0 && (
                      <button className="icon-button danger" aria-label={`Borrar ${g.title}`}
                        onClick={() => window.confirm(`¿Borrar ${g.title}?`) && act(() => developerApi.remove(g.id), 'Juego borrado')}>
                        <Icon name="trash" size={16} />
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <p className="hint">Para publicar un juego necesita al menos un género. Solo se pueden borrar borradores sin ventas; un juego retirado solo lo puede volver a publicar un administrador.</p>
    </>
  );
}

export function DeveloperGameForm() {
  const { id } = useParams();
  const isNew = !id || id === 'new';
  const navigate = useNavigate();
  const { notify, notifyError } = useStore();
  const genres = useAsync(() => catalogApi.genres(), []);
  const existing = useAsync<GameDetail | null>(() => (isNew ? Promise.resolve(null) : developerApi.get(Number(id))), [id]);

  if (existing.loading && !existing.data && !isNew) return <Loading />;
  if (existing.error) return <ErrorState error={existing.error} onRetry={existing.reload} />;

  return (
    <GameForm key={existing.data?.version ?? 'new'} game={existing.data} genres={genres.data ?? []}
      onSaved={(g) => { notify(isNew ? 'Juego creado como borrador' : 'Cambios guardados'); navigate(isNew ? `/developer/games/${g.id}` : '/developer'); }}
      onConflict={() => { notifyError(new ApiError(409, 'Alguien ha modificado este juego mientras editabas. Se han recargado los datos.')); existing.reload(); }} />
  );
}

function GameForm({ game, genres, onSaved, onConflict }: {
  game: GameDetail | null;
  genres: { id: number; name: string }[];
  onSaved: (g: GameDetail) => void;
  onConflict: () => void;
}) {
  const { notifyError } = useStore();
  const [form, setForm] = useState({
    title: game?.title ?? '',
    description: game?.description ?? '',
    price: game ? String(game.price) : '',
    discountPercent: game ? String(game.discountPercent) : '0',
    releaseDate: game?.releaseDate ?? '',
    coverUrl: game?.coverUrl ?? '',
  });
  const [genreIds, setGenreIds] = useState<number[]>(game?.genres.map((g) => g.id) ?? []);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  const set = (field: keyof typeof form) => (e: { target: { value: string } }) => setForm({ ...form, [field]: e.target.value });
  const toggleGenre = (gid: number) => setGenreIds((ids) => (ids.includes(gid) ? ids.filter((x) => x !== gid) : [...ids, gid]));

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setErrors({});
    const body = {
      title: form.title.trim(),
      description: form.description.trim() || null,
      price: Number(form.price),
      discountPercent: Number(form.discountPercent || 0),
      releaseDate: form.releaseDate || null,
      coverUrl: form.coverUrl.trim() || null,
      genreIds,
      version: game?.version ?? null,
    };
    try {
      onSaved(game ? await developerApi.update(game.id, body) : await developerApi.create(body));
    } catch (err) {
      if (err instanceof ApiError && err.status === 409 && game) onConflict();
      else {
        if (err instanceof ApiError) setErrors(err.fieldErrors);
        notifyError(err);
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <PageHeader eyebrow={game ? `${GAME_STATUS[game.status].toUpperCase()} · v${game.version}` : 'TU ESTUDIO'} title={game ? `Editar ${game.title}` : 'Nuevo juego'}>
        <Link className="view-all" to="/developer">Volver a mis juegos <Icon name="chevron" size={14} /></Link>
      </PageHeader>
      <form className="game-form" onSubmit={submit}>
        <div className="panel form">
          <label className="field">
            <span>Título</span>
            <input required maxLength={150} value={form.title} onChange={set('title')} />
            <FieldError errors={errors} name="title" />
          </label>
          <label className="field">
            <span>Descripción</span>
            <textarea rows={7} maxLength={10000} value={form.description} onChange={set('description')} />
            <FieldError errors={errors} name="description" />
          </label>
          <div className="field-row">
            <label className="field">
              <span>Precio (€)</span>
              <input type="number" required min={0} step="0.01" value={form.price} onChange={set('price')} />
              <FieldError errors={errors} name="price" />
            </label>
            <label className="field">
              <span>Descuento (%)</span>
              <input type="number" min={0} max={100} value={form.discountPercent} onChange={set('discountPercent')} />
              <FieldError errors={errors} name="discountPercent" />
            </label>
            <label className="field">
              <span>Lanzamiento</span>
              <input type="date" value={form.releaseDate} onChange={set('releaseDate')} />
            </label>
          </div>
          <label className="field">
            <span>URL de la portada</span>
            <input type="url" maxLength={500} value={form.coverUrl} onChange={set('coverUrl')} placeholder="https://…" />
            <FieldError errors={errors} name="coverUrl" />
          </label>
          <div className="field">
            <span>Géneros</span>
            <div className="chips">
              {genres.map((g) => (
                <button type="button" key={g.id} className={genreIds.includes(g.id) ? 'chip active' : 'chip'}
                  aria-pressed={genreIds.includes(g.id)} onClick={() => toggleGenre(g.id)}>{g.name}</button>
              ))}
            </div>
          </div>
          <div className="form-actions">
            <button className="primary small" disabled={busy}>{busy ? 'Guardando…' : game ? 'Guardar cambios' : 'Crear borrador'}</button>
          </div>
        </div>
        <aside className="panel cover-preview">
          <small>Vista previa de la portada</small>
          {form.coverUrl ? <img src={form.coverUrl} alt="" /> : <div className="cover-fallback">{form.title || 'Sin portada'}</div>}
        </aside>
      </form>
    </>
  );
}
