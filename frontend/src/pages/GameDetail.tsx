import { useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router';
import { ApiError } from '@/api/client';
import { catalogApi, libraryApi, reviewApi } from '@/api/endpoints';
import type { GameDetail as Game, Ownership, Review } from '@/api/types';
import Icon from '@/components/Icon';
import ReviewScoreBadge from '@/components/ReviewScoreBadge';
import { ErrorState, FieldError, Loading, Pagination, Price } from '@/components/ui';
import { useStore } from '@/context/StoreContext';
import { getLang, useI18n } from '@/i18n';
import { formatDate, formatDateTime, genreLabel, genreName, initials, wideImage } from '@/lib/format';
import { LOCALES } from '@/lib/locale';
import { useAsync } from '@/lib/useAsync';

export default function GameDetail() {
  const { slug = '' } = useParams();
  const { user, addToCart, toggleWishlist, wishlistIds } = useStore();
  const { t } = useI18n();
  const game = useAsync(() => catalogApi.get(slug), [slug]);
  const ownership = useAsync<Ownership | null>(
    () => (user && game.data ? libraryApi.status(game.data.id) : Promise.resolve(null)),
    [user?.id, game.data?.id],
  );

  if (game.loading && !game.data) return <Loading />;
  if (game.error || !game.data) {
    const notFound = game.error instanceof ApiError && game.error.status === 404;
    return notFound
      ? <div className="state error"><p>{t('game.notFound')}</p><Link className="secondary small" to="/browse">{t('game.backCatalog')}</Link></div>
      : <ErrorState error={game.error} onRetry={game.reload} />;
  }

  const g = game.data;
  const own = ownership.data;
  const isMine = user?.id === g.developer.id;
  const wished = wishlistIds.has(g.id);

  const onAdd = async () => {
    if (await addToCart(g.id, g.title)) ownership.setData(own ? { ...own, inCart: true } : own);
  };

  return (
    <>
      <section className="hero detail-hero" style={{ backgroundImage: `linear-gradient(90deg, rgba(8,12,18,.98) 0%, rgba(8,12,18,.8) 50%, rgba(8,12,18,.2) 100%), url("${wideImage(g.coverUrl)}")` }}>
        <div className="hero-copy">
          <p className="eyebrow">{g.developer.displayName.toUpperCase()} · {formatDate(g.releaseDate)}</p>
          <h1>{g.title.toUpperCase()}</h1>
          <div className="detail-meta">
            <ReviewScoreBadge game={g} />
          </div>
          <div className="hero-tags">
            {g.genres.map((genre) => <Link key={genre.id} to={`/browse?genres=${genre.slug}`}>{genreName(genre)}</Link>)}
          </div>
          <div className="buy-box">
            <Price price={g.price} finalPrice={g.finalPrice} discountPercent={g.discountPercent} />
            {g.priceSyncedAt && <small className="hint">{t('steam.priceSynced', { date: formatDateTime(g.priceSyncedAt) })}</small>}
            <div className="hero-actions">
              {isMine ? (
                <Link className="secondary" to={`/developer/games/${g.id}`}><Icon name="edit" size={18} /> {t('game.yoursEdit')}</Link>
              ) : own?.owned ? (
                <Link className="primary" to="/library"><Icon name="library" size={18} /> {t('game.inLibrary')}</Link>
              ) : own?.inCart ? (
                <Link className="primary" to="/cart"><Icon name="cart" size={18} /> {t('game.inCart')}</Link>
              ) : (
                <button className="primary" onClick={onAdd}><Icon name="cart" size={18} /> {t('game.addCart')}</button>
              )}
              {!isMine && !own?.owned && (
                <button className="secondary" onClick={() => toggleWishlist(g.id, g.title)}>
                  <Icon name="heart" size={18} /> {wished ? t('common.inWishlist') : t('common.addToWishlist')}
                </button>
              )}
            </div>
          </div>
        </div>
        {g.coverUrl && <img className="detail-cover" src={g.coverUrl} alt={t('game.coverOf', { title: g.title })} />}
      </section>

      <div className="detail-grid">
        <section className="section">
          <div className="section-heading"><div><p className="eyebrow">{t('game.aboutEyebrow')}</p><h2>{t('game.description')}</h2></div></div>
          <div className="panel prose">{g.description || t('game.noDescription')}</div>
        </section>
        <aside className="section">
          <div className="section-heading"><div><p className="eyebrow">{t('game.sheetEyebrow')}</p><h2>{t('game.details')}</h2></div></div>
          <dl className="panel facts">
            <dt>{t('game.studio')}</dt><dd>{g.developer.displayName}</dd>
            <dt>{t('game.release')}</dt><dd>{formatDate(g.releaseDate)}</dd>
            <dt>{t('game.genres')}</dt><dd>{g.genres.length ? genreLabel(g.genres) : '—'}</dd>
            <dt>{t('game.sold')}</dt><dd>{g.salesCount}</dd>
          </dl>
        </aside>
      </div>

      <Reviews game={g} ownership={own} onChanged={() => { game.reload(); ownership.reload(); }} />
    </>
  );
}

function Reviews({ game, ownership, onChanged }: { game: Game; ownership: Ownership | null; onChanged: () => void }) {
  const { user, hasRole, notify, notifyError } = useStore();
  const { t, lang } = useI18n();
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<Review | null>(null);
  // Por defecto, las reseñas en el idioma de la interfaz; la puntuación de arriba cuenta todas
  const [allLanguages, setAllLanguages] = useState(false);
  const reviews = useAsync(
    () => catalogApi.reviews(game.slug, page, 6, allLanguages ? undefined : lang),
    [game.slug, page, allLanguages, lang, game.reviewCount, game.positivePercent],
  );
  const languageName = LOCALES.find((l) => l.value === lang)?.label ?? lang;
  const pickScope = (all: boolean) => { setAllLanguages(all); setPage(0); };

  const canWrite = ownership?.owned && !ownership.reviewed;

  const remove = async (review: Review) => {
    if (!window.confirm(t('review.confirmDelete'))) return;
    try {
      await reviewApi.remove(review.id);
      notify(t('review.deleted'));
      onChanged();
      reviews.reload();
    } catch (e) {
      notifyError(e);
    }
  };

  return (
    <section className="section">
      <div className="section-heading">
        <div><p className="eyebrow">{t('review.eyebrow')}</p><h2>{t('review.title')}</h2></div>
      </div>

      {canWrite && <ReviewForm gameId={game.id} onSaved={onChanged} />}
      {user && ownership && !ownership.owned && user.id !== game.developer.id && (
        <p className="hint">{t('review.ownersOnly')}</p>
      )}
      {!user && <p className="hint"><Link to="/login">{t('nav.login')}</Link> {t('review.loginToReview')}</p>}

      <div className="segmented review-languages">
        <button type="button" className={!allLanguages ? 'active' : ''} aria-pressed={!allLanguages} onClick={() => pickScope(false)}>
          {t('review.mineLanguage', { language: languageName })}
        </button>
        <button type="button" className={allLanguages ? 'active' : ''} aria-pressed={allLanguages} onClick={() => pickScope(true)}>
          {t('review.allLanguages')}
        </button>
      </div>

      {reviews.loading && !reviews.data && <Loading />}
      {reviews.error ? <ErrorState error={reviews.error} onRetry={reviews.reload} /> : null}
      {reviews.data && reviews.data.content.length === 0 && (
        !allLanguages && game.reviewCount > 0 ? (
          <p className="hint">
            {t('review.noneInLanguage')}{' '}
            <button type="button" className="secondary small" onClick={() => pickScope(true)}>{t('review.allLanguages')}</button>
          </p>
        ) : <p className="hint">{t('review.none')}</p>
      )}
      <div className="review-list">
        {reviews.data?.content.map((r) =>
          editing?.id === r.id ? (
            <ReviewForm key={r.id} gameId={game.id} review={r}
              onSaved={() => { setEditing(null); onChanged(); reviews.reload(); }} onCancel={() => setEditing(null)} />
          ) : (
            <article key={r.id} className="review">
              <header>
                <span className="avatar small">{initials(r.author.displayName)}</span>
                <div>
                  <b>{r.author.displayName}</b>
                  <small>{formatDate(r.createdAt)}{r.updatedAt !== r.createdAt ? t('review.edited') : ''}</small>
                </div>
                <span className="review-lang" title={LOCALES.find((l) => l.value === r.language)?.label}>{r.language.toUpperCase()}</span>
                <span className={r.recommended ? 'verdict yes' : 'verdict no'}>
                  <Icon name="thumb" size={14} /> {r.recommended ? t('review.recommended') : t('review.notRecommended')}
                </span>
              </header>
              {r.body && <p>{r.body}</p>}
              {(user?.id === r.author.id || hasRole('ROLE_ADMIN')) && (
                <div className="review-actions">
                  {user?.id === r.author.id && <button onClick={() => setEditing(r)}><Icon name="edit" size={14} /> {t('common.edit')}</button>}
                  <button onClick={() => remove(r)}><Icon name="trash" size={14} /> {t('common.delete')}</button>
                </div>
              )}
            </article>
          ),
        )}
      </div>
      {reviews.data && <Pagination page={reviews.data.page} totalPages={reviews.data.totalPages} onChange={setPage} />}
    </section>
  );
}

function ReviewForm({ gameId, review, onSaved, onCancel }: { gameId: number; review?: Review; onSaved: () => void; onCancel?: () => void }) {
  const { notify, notifyError } = useStore();
  const { t } = useI18n();
  // Sin veredicto elegido de partida: hay que decidir 👍 o 👎 antes de publicar
  const [recommended, setRecommended] = useState<boolean | null>(review?.recommended ?? null);
  const [body, setBody] = useState(review?.body ?? '');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (recommended === null) {
      setErrors({ recommended: t('review.pickVerdict') });
      return;
    }
    setSaving(true);
    setErrors({});
    try {
      const payload = { gameId, recommended, body: body.trim() || undefined, language: getLang() };
      if (review) await reviewApi.update(review.id, payload);
      else await reviewApi.create(payload);
      notify(review ? t('review.updated') : t('review.thanks'));
      onSaved();
    } catch (err) {
      if (err instanceof ApiError) setErrors(err.fieldErrors);
      notifyError(err);
    } finally {
      setSaving(false);
    }
  };

  return (
    <form className="panel review-form" onSubmit={submit}>
      <h3>{review ? t('review.editYours') : t('review.write')}</h3>
      <div className="segmented">
        <button type="button" className={recommended === true ? 'active' : ''} aria-pressed={recommended === true} onClick={() => setRecommended(true)}>{t('review.recommend')}</button>
        <button type="button" className={recommended === false ? 'active' : ''} aria-pressed={recommended === false} onClick={() => setRecommended(false)}>{t('review.notRecommend')}</button>
      </div>
      <FieldError errors={errors} name="recommended" />
      <textarea value={body} onChange={(e) => setBody(e.target.value)} maxLength={5000} rows={4}
        placeholder={t('review.placeholder')} aria-label={t('review.textLabel')} />
      <FieldError errors={errors} name="body" />
      <div className="form-actions">
        {onCancel && <button type="button" className="secondary small" onClick={onCancel}>{t('common.cancel')}</button>}
        <button className="primary small" disabled={saving}>{saving ? t('common.saving') : review ? t('common.saveChanges') : t('review.publish')}</button>
      </div>
    </form>
  );
}
