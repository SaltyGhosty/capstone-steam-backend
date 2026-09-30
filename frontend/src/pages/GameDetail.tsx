import { useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router';
import { ApiError } from '@/api/client';
import { catalogApi, libraryApi, reviewApi } from '@/api/endpoints';
import type { GameDetail as Game, Ownership, Review } from '@/api/types';
import Icon from '@/components/Icon';
import { ErrorState, FieldError, Loading, Pagination, Price, Stars } from '@/components/ui';
import { useStore } from '@/context/StoreContext';
import { formatDate, initials, wideImage } from '@/lib/format';
import { useAsync } from '@/lib/useAsync';

export default function GameDetail() {
  const { slug = '' } = useParams();
  const { user, addToCart, toggleWishlist, wishlistIds } = useStore();
  const game = useAsync(() => catalogApi.get(slug), [slug]);
  const ownership = useAsync<Ownership | null>(
    () => (user && game.data ? libraryApi.status(game.data.id) : Promise.resolve(null)),
    [user?.id, game.data?.id],
  );

  if (game.loading && !game.data) return <Loading />;
  if (game.error || !game.data) {
    const notFound = game.error instanceof ApiError && game.error.status === 404;
    return notFound
      ? <div className="state error"><p>Este juego no existe o ya no está a la venta.</p><Link className="secondary small" to="/browse">Volver al catálogo</Link></div>
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
            {g.ratingCount > 0
              ? <><Stars value={g.averageRating} /> <span>{g.averageRating.toFixed(1)} · {g.ratingCount} {g.ratingCount === 1 ? 'reseña' : 'reseñas'}</span></>
              : <span>Sin reseñas todavía</span>}
          </div>
          <div className="hero-tags">
            {g.genres.map((genre) => <Link key={genre.id} to={`/browse?genres=${genre.slug}`}>{genre.name}</Link>)}
          </div>
          <div className="buy-box">
            <Price price={g.price} finalPrice={g.finalPrice} discountPercent={g.discountPercent} />
            <div className="hero-actions">
              {isMine ? (
                <Link className="secondary" to={`/developer/games/${g.id}`}><Icon name="edit" size={18} /> Es tu juego · Editar</Link>
              ) : own?.owned ? (
                <Link className="primary" to="/library"><Icon name="library" size={18} /> En tu biblioteca</Link>
              ) : own?.inCart ? (
                <Link className="primary" to="/cart"><Icon name="cart" size={18} /> En el carrito · Ir a pagar</Link>
              ) : (
                <button className="primary" onClick={onAdd}><Icon name="cart" size={18} /> Añadir al carrito</button>
              )}
              {!isMine && !own?.owned && (
                <button className="secondary" onClick={() => toggleWishlist(g.id, g.title)}>
                  <Icon name="heart" size={18} /> {wished ? 'En tu wishlist' : 'Añadir a la wishlist'}
                </button>
              )}
            </div>
          </div>
        </div>
        {g.coverUrl && <img className="detail-cover" src={g.coverUrl} alt={`Portada de ${g.title}`} />}
      </section>

      <div className="detail-grid">
        <section className="section">
          <div className="section-heading"><div><p className="eyebrow">SOBRE EL JUEGO</p><h2>Descripción</h2></div></div>
          <div className="panel prose">{g.description || 'El estudio todavía no ha escrito una descripción.'}</div>
        </section>
        <aside className="section">
          <div className="section-heading"><div><p className="eyebrow">FICHA</p><h2>Detalles</h2></div></div>
          <dl className="panel facts">
            <dt>Estudio</dt><dd>{g.developer.displayName}</dd>
            <dt>Lanzamiento</dt><dd>{formatDate(g.releaseDate)}</dd>
            <dt>Géneros</dt><dd>{g.genres.map((x) => x.name).join(', ') || '—'}</dd>
            <dt>Copias vendidas</dt><dd>{g.salesCount}</dd>
          </dl>
        </aside>
      </div>

      <Reviews game={g} ownership={own} onChanged={() => { game.reload(); ownership.reload(); }} />
    </>
  );
}

function Reviews({ game, ownership, onChanged }: { game: Game; ownership: Ownership | null; onChanged: () => void }) {
  const { user, hasRole, notify, notifyError } = useStore();
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<Review | null>(null);
  const reviews = useAsync(() => catalogApi.reviews(game.slug, page, 6), [game.slug, page, game.ratingCount, game.averageRating]);

  const canWrite = ownership?.owned && !ownership.reviewed;

  const remove = async (review: Review) => {
    if (!window.confirm('¿Borrar esta reseña?')) return;
    try {
      await reviewApi.remove(review.id);
      notify('Reseña borrada');
      onChanged();
    } catch (e) {
      notifyError(e);
    }
  };

  return (
    <section className="section">
      <div className="section-heading">
        <div><p className="eyebrow">COMUNIDAD</p><h2>Reseñas</h2></div>
      </div>

      {canWrite && <ReviewForm gameId={game.id} onSaved={onChanged} />}
      {user && ownership && !ownership.owned && user.id !== game.developer.id && (
        <p className="hint">Solo pueden reseñar quienes tienen el juego en su biblioteca.</p>
      )}
      {!user && <p className="hint"><Link to="/login">Inicia sesión</Link> para comprar y reseñar este juego.</p>}

      {reviews.loading && !reviews.data && <Loading />}
      {reviews.error ? <ErrorState error={reviews.error} onRetry={reviews.reload} /> : null}
      {reviews.data && reviews.data.content.length === 0 && <p className="hint">Nadie ha reseñado este juego todavía.</p>}
      <div className="review-list">
        {reviews.data?.content.map((r) =>
          editing?.id === r.id ? (
            <ReviewForm key={r.id} gameId={game.id} review={r}
              onSaved={() => { setEditing(null); onChanged(); }} onCancel={() => setEditing(null)} />
          ) : (
            <article key={r.id} className="review">
              <header>
                <span className="avatar small">{initials(r.author.displayName)}</span>
                <div>
                  <b>{r.author.displayName}</b>
                  <small>{formatDate(r.createdAt)}{r.updatedAt !== r.createdAt ? ' · editada' : ''}</small>
                </div>
                <span className={r.recommended ? 'verdict yes' : 'verdict no'}>
                  <Icon name="thumb" size={14} /> {r.recommended ? 'Recomendado' : 'No recomendado'}
                </span>
              </header>
              <Stars value={r.rating} size={13} />
              {r.body && <p>{r.body}</p>}
              {(user?.id === r.author.id || hasRole('ROLE_ADMIN')) && (
                <div className="review-actions">
                  {user?.id === r.author.id && <button onClick={() => setEditing(r)}><Icon name="edit" size={14} /> Editar</button>}
                  <button onClick={() => remove(r)}><Icon name="trash" size={14} /> Borrar</button>
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
  const [rating, setRating] = useState(review?.rating ?? 0);
  const [recommended, setRecommended] = useState(review?.recommended ?? true);
  const [body, setBody] = useState(review?.body ?? '');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!rating) {
      setErrors({ rating: 'Elige una puntuación de 1 a 5' });
      return;
    }
    setSaving(true);
    setErrors({});
    try {
      const payload = { gameId, rating, recommended, body: body.trim() || undefined };
      if (review) await reviewApi.update(review.id, payload);
      else await reviewApi.create(payload);
      notify(review ? 'Reseña actualizada' : '¡Gracias por tu reseña!');
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
      <h3>{review ? 'Editar tu reseña' : 'Escribe tu reseña'}</h3>
      <div className="rating-input" role="radiogroup" aria-label="Puntuación">
        {[1, 2, 3, 4, 5].map((n) => (
          <button type="button" key={n} role="radio" aria-checked={rating === n} aria-label={`${n} estrellas`}
            className={n <= rating ? 'on' : ''} onClick={() => setRating(n)}>
            <Icon name="star" size={22} />
          </button>
        ))}
      </div>
      <FieldError errors={errors} name="rating" />
      <div className="segmented">
        <button type="button" className={recommended ? 'active' : ''} onClick={() => setRecommended(true)}>Lo recomiendo</button>
        <button type="button" className={!recommended ? 'active' : ''} onClick={() => setRecommended(false)}>No lo recomiendo</button>
      </div>
      <textarea value={body} onChange={(e) => setBody(e.target.value)} maxLength={5000} rows={4}
        placeholder="¿Qué te ha parecido? (opcional)" aria-label="Texto de la reseña" />
      <FieldError errors={errors} name="body" />
      <div className="form-actions">
        {onCancel && <button type="button" className="secondary small" onClick={onCancel}>Cancelar</button>}
        <button className="primary small" disabled={saving}>{saving ? 'Guardando…' : review ? 'Guardar cambios' : 'Publicar reseña'}</button>
      </div>
    </form>
  );
}
