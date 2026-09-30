// Panel de administración: moderación de juegos, usuarios (roles, activación, regalos) y géneros.
import { useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router';
import { adminApi, catalogApi } from '@/api/endpoints';
import type { GameStatus, Genre, Role, User } from '@/api/types';
import Icon from '@/components/Icon';
import { ErrorState, Loading, PageHeader } from '@/components/ui';
import { useStore } from '@/context/StoreContext';
import { formatDate, formatMoney, GAME_STATUS } from '@/lib/format';
import { useAsync } from '@/lib/useAsync';

const TABS = [
  { id: 'games', label: 'Juegos' },
  { id: 'users', label: 'Usuarios' },
  { id: 'genres', label: 'Géneros' },
] as const;

export default function Admin() {
  const [params, setParams] = useSearchParams();
  const tab = params.get('tab') ?? 'games';

  return (
    <>
      <PageHeader eyebrow="ADMINISTRACIÓN" title="Moderación" />
      <div className="tabs" role="tablist">
        {TABS.map((t) => (
          <button key={t.id} role="tab" aria-selected={tab === t.id} className={tab === t.id ? 'active' : ''}
            onClick={() => setParams({ tab: t.id })}>{t.label}</button>
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
  const [status, setStatus] = useState<GameStatus | ''>('');
  const games = useAsync(() => adminApi.games(status || undefined, 0, 100), [status]);

  const moderate = async (id: number, title: string, next: GameStatus) => {
    try {
      await adminApi.moderate(id, next);
      notify(next === 'DELISTED' ? `${title} retirado de la tienda` : `${title} vuelve a estar publicado`);
      games.reload();
    } catch (e) {
      notifyError(e);
    }
  };

  return (
    <>
      <div className="chips toolbar">
        {(['', 'PUBLISHED', 'DRAFT', 'DELISTED'] as const).map((s) => (
          <button key={s} className={status === s ? 'chip active' : 'chip'} onClick={() => setStatus(s)}>{s ? GAME_STATUS[s] : 'Todos'}</button>
        ))}
      </div>
      {games.loading && !games.data && <Loading />}
      {games.error ? <ErrorState error={games.error} onRetry={games.reload} /> : null}
      {games.data && (
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th>Juego</th><th>Estudio</th><th>Estado</th><th>Precio</th><th>Ventas</th><th /></tr></thead>
            <tbody>
              {games.data.content.map((g) => (
                <tr key={g.id}>
                  <td><div className="game-cell">
                    {g.coverUrl && <img src={g.coverUrl} alt="" />}
                    {g.status === 'PUBLISHED' ? <Link to={`/games/${g.slug}`}>{g.title}</Link> : <span>{g.title}</span>}
                  </div></td>
                  <td>{g.developer.displayName}</td>
                  <td><span className={`badge ${g.status.toLowerCase()}`}>{GAME_STATUS[g.status]}</span></td>
                  <td>{formatMoney(g.finalPrice)}</td>
                  <td>{g.salesCount}</td>
                  <td className="row-actions">
                    {g.status === 'PUBLISHED' && <button className="secondary small danger" onClick={() => moderate(g.id, g.title, 'DELISTED')}>Retirar</button>}
                    {g.status === 'DELISTED' && <button className="secondary small" onClick={() => moderate(g.id, g.title, 'PUBLISHED')}>Republicar</button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {games.data.content.length === 0 && <p className="hint">No hay juegos con este estado.</p>}
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
  const users = useAsync(() => adminApi.users(0, 100), []);
  const games = useAsync(() => catalogApi.search({ sort: 'title', size: 100 }), []);
  const [gift, setGift] = useState<Record<number, string>>({});

  const replace = (updated: User) =>
    users.setData((prev) => prev && { ...prev, content: prev.content.map((u) => (u.id === updated.id ? updated : u)) });

  const toggleRole = async (u: User, role: Role) => {
    const roles = u.roles.includes(role) ? u.roles.filter((r) => r !== role) : [...u.roles, role];
    if (!roles.length) return notifyError(new Error('Un usuario necesita al menos un rol'));
    try {
      replace(await adminApi.setRoles(u.id, roles));
      notify(`Roles de ${u.username} actualizados (se aplican cuando renueve su sesión)`);
    } catch (e) {
      notifyError(e);
    }
  };

  const toggleEnabled = async (u: User) => {
    try {
      replace(await adminApi.setEnabled(u.id, !u.enabled));
      notify(u.enabled ? `${u.username} desactivado` : `${u.username} reactivado`);
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
      notify(`${entry.title} regalado a ${u.username}`);
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
        <thead><tr><th>Usuario</th><th>Alta</th><th>Roles</th><th>Estado</th><th>Regalar juego</th></tr></thead>
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
                  title={u.enabled ? 'Desactivar cuenta' : 'Reactivar cuenta'}>
                  {u.enabled ? 'Activo' : 'Desactivado'}
                </button>
              </td>
              <td>
                <form className="inline-form" onSubmit={(e) => grant(e, u)}>
                  <select value={gift[u.id] ?? ''} onChange={(e) => setGift((g) => ({ ...g, [u.id]: e.target.value }))} aria-label={`Juego para ${u.username}`}>
                    <option value="">Elige…</option>
                    {games.data?.content.map((g) => <option key={g.id} value={g.id}>{g.title}</option>)}
                  </select>
                  <button className="icon-button" disabled={!gift[u.id]} aria-label="Regalar"><Icon name="gift" size={16} /></button>
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
  const genres = useAsync(() => catalogApi.genres(), []);
  const [name, setName] = useState('');
  const [editing, setEditing] = useState<Genre | null>(null);

  const create = async (e: FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;
    try {
      await adminApi.createGenre(name.trim());
      notify(`Género ${name.trim()} creado`);
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
      notify('Género renombrado');
      setEditing(null);
      genres.reload();
    } catch (err) {
      notifyError(err);
    }
  };

  const remove = async (g: Genre) => {
    if (!window.confirm(`¿Borrar el género ${g.name}?`)) return;
    try {
      await adminApi.deleteGenre(g.id);
      notify('Género borrado');
      genres.reload();
    } catch (err) {
      notifyError(err);
    }
  };

  return (
    <>
      <form className="inline-form toolbar" onSubmit={create}>
        <input value={name} onChange={(e) => setName(e.target.value)} maxLength={50} placeholder="Nuevo género" aria-label="Nombre del nuevo género" />
        <button className="primary small" disabled={!name.trim()}><Icon name="plus" size={15} /> Añadir</button>
      </form>
      {genres.loading && !genres.data && <Loading />}
      {genres.error ? <ErrorState error={genres.error} onRetry={genres.reload} /> : null}
      <div className="genre-list">
        {genres.data?.map((g) =>
          editing?.id === g.id ? (
            <form key={g.id} className="panel genre-row" onSubmit={save}>
              <input autoFocus value={editing.name} maxLength={50} onChange={(e) => setEditing({ ...editing, name: e.target.value })} aria-label="Nombre del género" />
              <button className="primary small">Guardar</button>
              <button type="button" className="secondary small" onClick={() => setEditing(null)}>Cancelar</button>
            </form>
          ) : (
            <div key={g.id} className="panel genre-row">
              <div><b>{g.name}</b><small className="muted"> /{g.slug}</small></div>
              <button className="icon-button" onClick={() => setEditing(g)} aria-label={`Renombrar ${g.name}`}><Icon name="edit" size={16} /></button>
              <button className="icon-button danger" onClick={() => remove(g)} aria-label={`Borrar ${g.name}`}><Icon name="trash" size={16} /></button>
            </div>
          ),
        )}
      </div>
    </>
  );
}
