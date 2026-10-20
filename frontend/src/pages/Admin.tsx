// Panel de administración: moderación de juegos, usuarios (roles, activación, regalos) y géneros.
import { useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router';
import { adminApi, catalogApi } from '@/api/endpoints';
import type { GameStatus, Genre, Role, User } from '@/api/types';
import CoverUpload, { useCoverUploadEnabled } from '@/components/CoverUpload';
import Icon from '@/components/Icon';
import { ErrorState, Loading, PageHeader } from '@/components/ui';
import { useStore } from '@/context/StoreContext';
import { useI18n } from '@/i18n';
import { formatDate, formatMoney, gameStatus } from '@/lib/format';
import { useAsync } from '@/lib/useAsync';

const TABS = ['games', 'users', 'genres'] as const;

export default function Admin() {
  const [params, setParams] = useSearchParams();
  const tab = params.get('tab') ?? 'games';
  const { t } = useI18n();

  return (
    <>
      <PageHeader eyebrow={t('admin.eyebrow')} title={t('nav.moderation')} />
      <div className="tabs" role="tablist">
        {TABS.map((id) => (
          <button key={id} role="tab" aria-selected={tab === id} className={tab === id ? 'active' : ''}
            onClick={() => setParams({ tab: id })}>{t(`admin.tab.${id}`)}</button>
        ))}
      </div>
      {tab === 'games' && <GamesTab />}
      {tab === 'users' && <UsersTab />}
      {tab === 'genres' && <GenresTab />}
    </>
  );
}

function GamesTab() {
  const { notify, notifyError } = useStore();
  const { t } = useI18n();
  const [status, setStatus] = useState<GameStatus | ''>('');
  const games = useAsync(() => adminApi.games(status || undefined, 0, 100), [status]);
  const [syncing, setSyncing] = useState(false);
  const coverUploadEnabled = useCoverUploadEnabled();

  const syncSteam = async () => {
    setSyncing(true);
    try {
      const r = await adminApi.syncSteam();
      notify(t('steam.synced', { checked: r.checked, updated: r.updated, sales: r.newSales, notified: r.notified }));
      games.reload();
    } catch (e) {
      notifyError(e);
    } finally {
      setSyncing(false);
    }
  };

  const moderate = async (id: number, title: string, next: GameStatus) => {
    try {
      await adminApi.moderate(id, next);
      notify(t(next === 'DELISTED' ? 'admin.delisted' : 'admin.republished', { title }));
      games.reload();
    } catch (e) {
      notifyError(e);
    }
  };

  return (
    <>
      <div className="chips toolbar">
        {(['', 'PUBLISHED', 'DRAFT', 'DELISTED'] as const).map((s) => (
          <button key={s} className={status === s ? 'chip active' : 'chip'} onClick={() => setStatus(s)}>{s ? gameStatus(s) : t('admin.all')}</button>
        ))}
        <button className="secondary small" onClick={syncSteam} disabled={syncing}>
          {syncing ? t('steam.syncing') : t('steam.sync')}
        </button>
      </div>
      {games.loading && !games.data && <Loading />}
      {games.error ? <ErrorState error={games.error} onRetry={games.reload} /> : null}
      {games.data && (
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th>{t('dev.game')}</th><th>{t('admin.studio')}</th><th>{t('admin.status')}</th><th>{t('dev.price')}</th><th>{t('dev.sales')}</th><th /></tr></thead>
            <tbody>
              {games.data.content.map((g) => (
                <tr key={g.id}>
                  <td><div className="game-cell">
                    {g.coverUrl && <img src={g.coverUrl} alt="" />}
                    {g.status === 'PUBLISHED' ? <Link to={`/games/${g.slug}`}>{g.title}</Link> : <span>{g.title}</span>}
                  </div></td>
                  <td>{g.developer.displayName}</td>
                  <td><span className={`badge ${g.status.toLowerCase()}`}>{gameStatus(g.status)}</span></td>
                  <td>{formatMoney(g.finalPrice)}</td>
                  <td>{g.salesCount}</td>
                  <td className="row-actions">
                    {coverUploadEnabled && (
                      <CoverUpload upload={(file) => adminApi.uploadCover(g.id, file)}
                        onUploaded={(updated) => games.setData((page) => page && {
                          ...page, content: page.content.map((x) => (x.id === updated.id ? updated : x)),
                        })} />
                    )}
                    {g.status === 'PUBLISHED' && <button className="secondary small danger" onClick={() => moderate(g.id, g.title, 'DELISTED')}>{t('admin.delist')}</button>}
                    {g.status === 'DELISTED' && <button className="secondary small" onClick={() => moderate(g.id, g.title, 'PUBLISHED')}>{t('admin.republish')}</button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {games.data.content.length === 0 && <p className="hint">{t('admin.noGames')}</p>}
        </div>
      )}
    </>
  );
}

const ROLES: { value: Role; label: string }[] = [
  { value: 'ROLE_USER', label: 'User' },
  { value: 'ROLE_DEVELOPER', label: 'Developer' },
  { value: 'ROLE_ADMIN', label: 'Admin' },
];

function UsersTab() {
  const { user: me, notify, notifyError } = useStore();
  const { t } = useI18n();
  const users = useAsync(() => adminApi.users(0, 100), []);
  const games = useAsync(() => catalogApi.search({ sort: 'title', size: 100 }), []);
  const [gift, setGift] = useState<Record<number, string>>({});

  const replace = (updated: User) =>
    users.setData((prev) => prev && { ...prev, content: prev.content.map((u) => (u.id === updated.id ? updated : u)) });

  const toggleRole = async (u: User, role: Role) => {
    const roles = u.roles.includes(role) ? u.roles.filter((r) => r !== role) : [...u.roles, role];
    if (!roles.length) return notifyError(new Error(t('admin.needRole')));
    try {
      replace(await adminApi.setRoles(u.id, roles));
      notify(t('admin.rolesUpdated', { user: u.username }));
    } catch (e) {
      notifyError(e);
    }
  };

  const toggleEnabled = async (u: User) => {
    try {
      replace(await adminApi.setEnabled(u.id, !u.enabled));
      notify(t(u.enabled ? 'admin.disabled' : 'admin.enabled', { user: u.username }));
    } catch (e) {
      notifyError(e);
    }
  };

  const verifyEmail = async (u: User) => {
    try {
      replace(await adminApi.verifyEmail(u.id));
      notify(t('admin.verified', { user: u.username }));
    } catch (e) {
      notifyError(e);
    }
  };

  const grant = async (e: FormEvent, u: User) => {
    e.preventDefault();
    const gameId = Number(gift[u.id]);
    if (!gameId) return;
    try {
      const entry = await adminApi.grantGame(u.id, gameId);
      notify(t('admin.gifted', { title: entry.title, user: u.username }));
      setGift((g) => ({ ...g, [u.id]: '' }));
    } catch (err) {
      notifyError(err);
    }
  };

  if (users.loading && !users.data) return <Loading />;
  if (users.error || !users.data) return <ErrorState error={users.error} onRetry={users.reload} />;

  return (
    <div className="table-wrap">
      <table className="table">
        <thead><tr><th>{t('admin.user')}</th><th>{t('admin.joined')}</th><th>{t('admin.roles')}</th><th>{t('admin.status')}</th><th>{t('admin.giftGame')}</th></tr></thead>
        <tbody>
          {users.data.content.map((u) => (
            <tr key={u.id}>
              <td><b>{u.displayName || u.username}</b><br /><small className="muted">@{u.username} · {u.email}</small></td>
              <td>{formatDate(u.createdAt)}</td>
              <td>
                <div className="chips">
                  {ROLES.map((r) => (
                    <button key={r.value} className={u.roles.includes(r.value) ? 'chip active' : 'chip'} aria-pressed={u.roles.includes(r.value)}
                      disabled={u.id === me?.id} onClick={() => toggleRole(u, r.value)}>{r.label}</button>
                  ))}
                </div>
              </td>
              <td>
                <button className={u.enabled ? 'badge paid' : 'badge failed'} disabled={u.id === me?.id} onClick={() => toggleEnabled(u)}
                  title={u.enabled ? t('admin.disable') : t('admin.enable')}>
                  {u.enabled ? t('admin.active') : t('admin.inactive')}
                </button>
                {!u.emailVerified && (
                  <div className="chips">
                    <span className="badge pending">{t('admin.unverified')}</span>
                    <button className="chip" onClick={() => verifyEmail(u)}>{t('admin.verify')}</button>
                  </div>
                )}
              </td>
              <td>
                <form className="inline-form" onSubmit={(e) => grant(e, u)}>
                  <select value={gift[u.id] ?? ''} onChange={(e) => setGift((g) => ({ ...g, [u.id]: e.target.value }))} aria-label={t('admin.gameFor', { user: u.username })}>
                    <option value="">{t('admin.choose')}</option>
                    {games.data?.content.map((g) => <option key={g.id} value={g.id}>{g.title}</option>)}
                  </select>
                  <button className="icon-button" disabled={!gift[u.id]} aria-label={t('admin.gift')}><Icon name="gift" size={16} /></button>
                </form>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function GenresTab() {
  const { notify, notifyError } = useStore();
  const { t } = useI18n();
  const genres = useAsync(() => catalogApi.genres(), []);
  const [name, setName] = useState('');
  const [editing, setEditing] = useState<Genre | null>(null);

  const create = async (e: FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;
    try {
      await adminApi.createGenre(name.trim());
      notify(t('admin.genreCreated', { name: name.trim() }));
      setName('');
      genres.reload();
    } catch (err) {
      notifyError(err);
    }
  };

  const save = async (e: FormEvent) => {
    e.preventDefault();
    if (!editing) return;
    try {
      await adminApi.updateGenre(editing.id, editing.name.trim());
      notify(t('admin.genreRenamed'));
      setEditing(null);
      genres.reload();
    } catch (err) {
      notifyError(err);
    }
  };

  const remove = async (g: Genre) => {
    if (!window.confirm(t('admin.confirmDeleteGenre', { name: g.name }))) return;
    try {
      await adminApi.deleteGenre(g.id);
      notify(t('admin.genreDeleted'));
      genres.reload();
    } catch (err) {
      notifyError(err);
    }
  };

  return (
    <>
      <form className="inline-form toolbar" onSubmit={create}>
        <input value={name} onChange={(e) => setName(e.target.value)} maxLength={50} placeholder={t('admin.newGenre')} aria-label={t('admin.newGenreName')} />
        <button className="primary small" disabled={!name.trim()}><Icon name="plus" size={15} /> {t('admin.add')}</button>
      </form>
      {genres.loading && !genres.data && <Loading />}
      {genres.error ? <ErrorState error={genres.error} onRetry={genres.reload} /> : null}
      <div className="genre-list">
        {genres.data?.map((g) =>
          editing?.id === g.id ? (
            <form key={g.id} className="panel genre-row" onSubmit={save}>
              <input autoFocus value={editing.name} maxLength={50} onChange={(e) => setEditing({ ...editing, name: e.target.value })} aria-label={t('admin.genreName')} />
              <button className="primary small">{t('common.save')}</button>
              <button type="button" className="secondary small" onClick={() => setEditing(null)}>{t('common.cancel')}</button>
            </form>
          ) : (
            <div key={g.id} className="panel genre-row">
              <div><b>{g.name}</b><small className="muted"> /{g.slug}</small></div>
              <button className="icon-button" onClick={() => setEditing(g)} aria-label={t('admin.rename', { name: g.name })}><Icon name="edit" size={16} /></button>
              <button className="icon-button danger" onClick={() => remove(g)} aria-label={t('admin.deleteName', { name: g.name })}><Icon name="trash" size={16} /></button>
            </div>
          ),
        )}
      </div>
    </>
  );
}
