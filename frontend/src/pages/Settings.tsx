import { useState, type FormEvent } from 'react';
import { ApiError } from '@/api/client';
import { userApi } from '@/api/endpoints';
import type { Locale } from '@/api/types';
import Icon from '@/components/Icon';
import { FieldError, PageHeader } from '@/components/ui';
import { useStore } from '@/context/StoreContext';
import { formatDate, initials } from '@/lib/format';
import { LOCALES } from '@/lib/locale';

export default function Settings() {
  const { user, setUser, logout, notify, notifyError } = useStore();
  const [displayName, setDisplayName] = useState(user?.displayName ?? '');
  const [avatarUrl, setAvatarUrl] = useState(user?.avatarUrl ?? '');
  const [locale, setLocale] = useState<Locale>(user?.locale ?? 'es');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  if (!user) return null;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setErrors({});
    try {
      // null = no se modifica; por eso solo se envía lo que tiene contenido
      const updated = await userApi.update({
        displayName: displayName.trim() || undefined,
        avatarUrl: avatarUrl.trim() || undefined,
        locale,
      });
      setUser(updated);
      notify('Perfil actualizado');
    } catch (err) {
      if (err instanceof ApiError) setErrors(err.fieldErrors);
      notifyError(err);
    } finally {
      setBusy(false);
    }
  };

  const name = user.displayName || user.username;

  return (
    <>
      <PageHeader eyebrow="TU CUENTA" title="Ajustes" />
      <div className="settings-grid">
        <aside className="panel profile-card">
          {user.avatarUrl ? <img className="avatar big" src={user.avatarUrl} alt="" /> : <span className="avatar big">{initials(name)}</span>}
          <h3>{name}</h3>
          <p>@{user.username}</p>
          <div className="hero-tags">{user.roles.map((r) => <span key={r}>{r.replace('ROLE_', '')}</span>)}</div>
          <dl className="facts">
            <dt>Email</dt><dd>{user.email}</dd>
            <dt>Miembro desde</dt><dd>{formatDate(user.createdAt)}</dd>
          </dl>
          <button className="secondary full" onClick={logout}><Icon name="logout" size={16} /> Cerrar sesión</button>
        </aside>
        <form className="panel form" onSubmit={submit}>
          <h3>Perfil público</h3>
          <label className="field">
            <span>Nombre visible</span>
            <input maxLength={100} value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
            <FieldError errors={errors} name="displayName" />
          </label>
          <label className="field">
            <span>URL del avatar</span>
            <input type="url" maxLength={500} value={avatarUrl} onChange={(e) => setAvatarUrl(e.target.value)} placeholder="https://…" />
            <FieldError errors={errors} name="avatarUrl" />
          </label>
          <label className="field">
            <span>Idioma de los correos <em>(bienvenida, recibos y pagos rechazados)</em></span>
            <select value={locale} onChange={(e) => setLocale(e.target.value as Locale)}>
              {LOCALES.map((l) => <option key={l.value} value={l.value}>{l.label}</option>)}
            </select>
            <FieldError errors={errors} name="locale" />
          </label>
          <div className="form-actions">
            <button className="primary small" disabled={busy}>{busy ? 'Guardando…' : 'Guardar cambios'}</button>
          </div>
        </form>
      </div>
    </>
  );
}
