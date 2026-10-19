// Panel del estudio: sus juegos (todos los estados), alta/edición, publicar y descuento masivo.
import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { ApiError } from '@/api/client';
import { catalogApi, developerApi } from '@/api/endpoints';
import type { GameDetail } from '@/api/types';
import Icon from '@/components/Icon';
import { Empty, ErrorState, FieldError, Loading, PageHeader } from '@/components/ui';
import { useStore } from '@/context/StoreContext';
import { useI18n } from '@/i18n';
import { formatMoney, gameStatus, genreName } from '@/lib/format';
import { useAsync } from '@/lib/useAsync';

export function DeveloperGames() {
  const { notify, notifyError } = useStore();
  const { t } = useI18n();
  const games = useAsync(() => developerApi.list(0, 100), []);
  const [discount, setDiscount] = useState('');
  const [appId, setAppId] = useState('');
  const [importing, setImporting] = useState(false);
  const navigate = useNavigate();

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
    if (!Number.isInteger(pct) || pct < 0 || pct > 100) return notifyError(new ApiError(400, t('dev.discountRange')));
    try {
      const res = await developerApi.discountAll(pct);
      notify(t('dev.discountApplied', { pct, count: res.updated }));
      setDiscount('');
      games.reload();
    } catch (err) {
      notifyError(err);
    }
  };

  const importGame = async (e: FormEvent) => {
    e.preventDefault();
    setImporting(true);
    try {
      const game = await developerApi.importFromSteam(Number(appId));
      notify(t('steam.imported', { title: game.title }));
      navigate(`/developer/games/${game.id}`);
    } catch (err) {
      notifyError(err);
    } finally {
      setImporting(false);
    }
  };

  const list = games.data?.content ?? [];
  const published = list.filter((g) => g.status === 'PUBLISHED');
  const sales = list.reduce((sum, g) => sum + g.salesCount, 0);

  return (
    <>
      <PageHeader eyebrow={t('dev.eyebrow')} title={t('nav.myGames')}>
        <div className="header-actions">
          <form className="inline-form" onSubmit={importGame}>
            <input type="number" min={1} value={appId} onChange={(e) => setAppId(e.target.value)} placeholder="AppID"
              aria-label={t('steam.importLabel')} />
            <button className="secondary small" disabled={!appId || importing}>
              {importing ? t('steam.importing') : t('steam.import')}
            </button>
          </form>
          <Link className="primary small" to="/developer/games/new"><Icon name="plus" size={16} /> {t('dev.newGame')}</Link>
        </div>
      </PageHeader>

      <div className="stats">
        <div className="panel stat"><small>{t('dev.games')}</small><b>{games.data ? list.length : '—'}</b></div>
        <div className="panel stat"><small>{t('dev.published')}</small><b>{games.data ? published.length : '—'}</b></div>
        <div className="panel stat"><small>{t('game.sold')}</small><b>{games.data ? sales : '—'}</b></div>
        <form className="panel stat discount" onSubmit={applyDiscount}>
          <small>{t('dev.discountAll')}</small>
          <div>
            <input type="number" min={0} max={100} value={discount} onChange={(e) => setDiscount(e.target.value)} placeholder="%" aria-label={t('dev.discountPct')} />
            <button className="secondary small" disabled={discount === ''}>{t('dev.apply')}</button>
          </div>
        </form>
      </div>

      {games.loading && !games.data && <Loading />}
      {games.error ? <ErrorState error={games.error} onRetry={games.reload} /> : null}
      {games.data && list.length === 0 && (
        <Empty icon="code" title={t('dev.empty')}>
          <Link className="primary small" to="/developer/games/new">{t('dev.createFirst')}</Link>
        </Empty>
      )}
      {list.length > 0 && (
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th>{t('dev.game')}</th><th>{t('orders.status')}</th><th>{t('dev.price')}</th><th>{t('dev.sales')}</th><th>{t('dev.rating')}</th><th /></tr></thead>
            <tbody>
              {list.map((g) => (
                <tr key={g.id}>
                  <td><div className="game-cell">
                    {g.coverUrl && <img src={g.coverUrl} alt="" />}
                    {g.status === 'PUBLISHED' ? <Link to={`/games/${g.slug}`}>{g.title}</Link> : <span>{g.title}</span>}
                  </div></td>
                  <td><span className={`badge ${g.status.toLowerCase()}`}>{gameStatus(g.status)}</span></td>
                  <td>{formatMoney(g.finalPrice)}{g.discountPercent > 0 && <small className="muted"> (−{g.discountPercent}%)</small>}</td>
                  <td>{g.salesCount}</td>
                  <td>{g.reviewCount ? t('dev.positiveSummary', { percent: g.positivePercent, count: g.reviewCount }) : '—'}</td>
                  <td className="row-actions">
                    <Link className="icon-button" to={`/developer/games/${g.id}`} aria-label={t('dev.editTitle', { title: g.title })}><Icon name="edit" size={16} /></Link>
                    {g.status === 'DRAFT' && <button className="secondary small" onClick={() => act(() => developerApi.publish(g.id), t('dev.publishedToast', { title: g.title }))}>{t('dev.publish')}</button>}
                    {g.status === 'PUBLISHED' && <button className="secondary small" onClick={() => act(() => developerApi.unpublish(g.id), t('dev.toDraft', { title: g.title }))}>{t('dev.unpublish')}</button>}
                    {g.status === 'DRAFT' && g.salesCount === 0 && (
                      <button className="icon-button danger" aria-label={t('dev.deleteTitle', { title: g.title })}
                        onClick={() => window.confirm(t('dev.confirmDelete', { title: g.title })) && act(() => developerApi.remove(g.id), t('dev.deleted'))}>
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
      <p className="hint">{t('dev.hint')}</p>
    </>
  );
}

export function DeveloperGameForm() {
  const { id } = useParams();
  const isNew = !id || id === 'new';
  const navigate = useNavigate();
  const { notify, notifyError } = useStore();
  const { t } = useI18n();
  const genres = useAsync(() => catalogApi.genres(), []);
  const existing = useAsync<GameDetail | null>(() => (isNew ? Promise.resolve(null) : developerApi.get(Number(id))), [id]);

  if (existing.loading && !existing.data && !isNew) return <Loading />;
  if (existing.error) return <ErrorState error={existing.error} onRetry={existing.reload} />;

  return (
    <GameForm key={existing.data?.version ?? 'new'} game={existing.data} genres={genres.data ?? []}
      onSaved={(g) => { notify(isNew ? t('dev.createdDraft') : t('dev.saved')); navigate(isNew ? `/developer/games/${g.id}` : '/developer'); }}
      onConflict={() => { notifyError(new ApiError(409, t('dev.conflict'))); existing.reload(); }} />
  );
}

function GameForm({ game, genres, onSaved, onConflict }: {
  game: GameDetail | null;
  genres: { id: number; name: string; slug: string }[];
  onSaved: (g: GameDetail) => void;
  onConflict: () => void;
}) {
  const { notifyError } = useStore();
  const { t } = useI18n();
  const [form, setForm] = useState({
    title: game?.title ?? '',
    description: game?.descriptions.es ?? '',
    descriptionEn: game?.descriptions.en ?? '',
    descriptionIt: game?.descriptions.it ?? '',
    price: game ? String(game.price) : '',
    discountPercent: game ? String(game.discountPercent) : '0',
    releaseDate: game?.releaseDate ?? '',
    coverUrl: game?.coverUrl ?? '',
    steamAppId: game?.steamAppId ? String(game.steamAppId) : '',
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
      descriptionEn: form.descriptionEn.trim() || null,
      descriptionIt: form.descriptionIt.trim() || null,
      price: Number(form.price),
      discountPercent: Number(form.discountPercent || 0),
      releaseDate: form.releaseDate || null,
      coverUrl: form.coverUrl.trim() || null,
      steamAppId: form.steamAppId ? Number(form.steamAppId) : null,
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
      <PageHeader eyebrow={game ? `${gameStatus(game.status).toUpperCase()} · v${game.version}` : t('dev.eyebrow')} title={game ? t('dev.editTitle', { title: game.title }) : t('dev.newGame')}>
        <Link className="view-all" to="/developer">{t('dev.back')} <Icon name="chevron" size={14} /></Link>
      </PageHeader>
      <form className="game-form" onSubmit={submit}>
        <div className="panel form">
          <label className="field">
            <span>{t('dev.title')}</span>
            <input required maxLength={150} value={form.title} onChange={set('title')} />
            <FieldError errors={errors} name="title" />
          </label>
          <label className="field">
            <span>{t('dev.description')}</span>
            <textarea rows={7} maxLength={10000} value={form.description} onChange={set('description')} />
            <FieldError errors={errors} name="description" />
          </label>
          <label className="field">
            <span>{t('dev.descriptionEn')} <em>{t('common.optional')}</em></span>
            <textarea rows={4} maxLength={10000} value={form.descriptionEn} onChange={set('descriptionEn')} />
            <FieldError errors={errors} name="descriptionEn" />
          </label>
          <label className="field">
            <span>{t('dev.descriptionIt')} <em>{t('common.optional')}</em></span>
            <textarea rows={4} maxLength={10000} value={form.descriptionIt} onChange={set('descriptionIt')} />
            <FieldError errors={errors} name="descriptionIt" />
            <small className="hint">{t('dev.descriptionHint')}</small>
          </label>
          <div className="field-row">
            <label className="field">
              <span>{t('dev.priceEur')}</span>
              <input type="number" required min={0} step="0.01" value={form.price} onChange={set('price')} />
              <FieldError errors={errors} name="price" />
            </label>
            <label className="field">
              <span>{t('dev.discount')}</span>
              <input type="number" min={0} max={100} value={form.discountPercent} onChange={set('discountPercent')} />
              <FieldError errors={errors} name="discountPercent" />
            </label>
            <label className="field">
              <span>{t('dev.release')}</span>
              <input type="date" value={form.releaseDate} onChange={set('releaseDate')} />
            </label>
          </div>
          <label className="field">
            <span>{t('dev.coverUrl')}</span>
            <input type="url" maxLength={500} value={form.coverUrl} onChange={set('coverUrl')} placeholder="https://…" />
            <FieldError errors={errors} name="coverUrl" />
          </label>
          <label className="field">
            <span>{t('steam.appId')} <em>{t('common.optional')}</em></span>
            <input type="number" min={1} value={form.steamAppId} onChange={set('steamAppId')} />
            <FieldError errors={errors} name="steamAppId" />
            <small className="hint">{t('steam.appIdHint')}</small>
          </label>
          <div className="field">
            <span>{t('dev.genres')}</span>
            <div className="chips">
              {genres.map((g) => (
                <button type="button" key={g.id} className={genreIds.includes(g.id) ? 'chip active' : 'chip'}
                  aria-pressed={genreIds.includes(g.id)} onClick={() => toggleGenre(g.id)}>{genreName(g)}</button>
              ))}
            </div>
          </div>
          <div className="form-actions">
            <button className="primary small" disabled={busy}>{busy ? t('common.saving') : game ? t('common.saveChanges') : t('dev.createDraft')}</button>
          </div>
        </div>
        <aside className="panel cover-preview">
          <small>{t('dev.coverPreview')}</small>
          {form.coverUrl ? <img src={form.coverUrl} alt="" /> : <div className="cover-fallback">{form.title || t('dev.noCover')}</div>}
        </aside>
      </form>
    </>
  );
}
