import { useState, type FormEvent } from 'react';
import { ApiError } from '@/api/client';
import { userApi } from '@/api/endpoints';
import type { Locale } from '@/api/types';
import Icon from '@/components/Icon';
import { FieldError, PageHeader } from '@/components/ui';
import { useStore } from '@/context/StoreContext';
import { useI18n } from '@/i18n';
import { formatDate, initials } from '@/lib/format';
import { LOCALES } from '@/lib/locale';

export default function Settings() {
  const { user, setUser, logout, notify, notifyError } = useStore();
  const { t } = useI18n();
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
      notify(t('settings.updated'));
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
      <PageHeader eyebrow={t('settings.eyebrow')} title={t('nav.settings')} />
      <div className="settings-grid">
        <aside className="panel profile-card">
          {user.avatarUrl ? <img className="avatar big" src={user.avatarUrl} alt="" /> : <span className="avatar big">{initials(name)}</span>}
          <h3>{name}</h3>
          <p>@{user.username}</p>
          <div className="hero-tags">{user.roles.map((r) => <span key={r}>{r.replace('ROLE_', '')}</span>)}</div>
          <dl className="facts">
            <dt>{t('common.email')}</dt><dd>{user.email}</dd>
            <dt>{t('settings.memberSince')}</dt><dd>{formatDate(user.createdAt)}</dd>
          </dl>
          <button className="secondary full" onClick={logout}><Icon name="logout" size={16} /> {t('nav.logout')}</button>
        </aside>
        <form className="panel form" onSubmit={submit}>
          <h3>{t('settings.publicProfile')}</h3>
          <label className="field">
            <span>{t('auth.displayName')}</span>
            <input maxLength={100} value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
            <FieldError errors={errors} name="displayName" />
          </label>
          <label className="field">
            <span>{t('settings.avatarUrl')}</span>
            <input type="url" maxLength={500} value={avatarUrl} onChange={(e) => setAvatarUrl(e.target.value)} placeholder="https://…" />
            <FieldError errors={errors} name="avatarUrl" />
          </label>
          <label className="field">
            <span>{t('settings.emailLang')} <em>{t('settings.emailLangHint')}</em></span>
            <select value={locale} onChange={(e) => setLocale(e.target.value as Locale)}>
              {LOCALES.map((l) => <option key={l.value} value={l.value}>{l.label}</option>)}
            </select>
            <FieldError errors={errors} name="locale" />
          </label>
          <div className="form-actions">
            <button className="primary small" disabled={busy}>{busy ? t('common.saving') : t('common.saveChanges')}</button>
          </div>
        </form>
      </div>
    </>
  );
}
