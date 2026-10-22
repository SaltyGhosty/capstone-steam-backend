import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import { ApiError } from '@/api/client';
import { userApi } from '@/api/endpoints';
import type { Locale } from '@/api/types';
import Icon from '@/components/Icon';
import { FieldError, PageHeader } from '@/components/ui';
import { useStore } from '@/context/StoreContext';
import { useI18n } from '@/i18n';
import { trapTab } from '@/lib/dialog';
import { formatDate, initials } from '@/lib/format';
import { LOCALES } from '@/lib/locale';

export default function Settings() {
  const { user, setUser, logout, notify, notifyError } = useStore();
  const { t } = useI18n();
  const [displayName, setDisplayName] = useState(user?.displayName ?? '');
  const [avatarUrl, setAvatarUrl] = useState(user?.avatarUrl ?? '');
  const [locale, setLocale] = useState<Locale>(user?.locale ?? 'es');
  const [profilePublic, setProfilePublic] = useState(user?.profilePublic ?? false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  // Baja de la cuenta: diálogo con contraseña; al terminar muestra la fecha y cierra la sesión
  const [deleting, setDeleting] = useState(false);

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
        profilePublic,
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
          <h3>{t('settings.profileSection')}</h3>
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
          <label className="check-field">
            <input type="checkbox" checked={profilePublic} onChange={(e) => setProfilePublic(e.target.checked)} />
            <span>{t('settings.publicProfile')} <em>{t('settings.publicProfileHelp')}</em></span>
          </label>
          <div className="form-actions">
            <button className="primary small" disabled={busy}>{busy ? t('common.saving') : t('common.saveChanges')}</button>
          </div>
        </form>
      </div>
      <section className="panel form danger-zone">
        <h3>{t('settings.deleteSection')}</h3>
        <p>{t('settings.deleteIntro')}</p>
        <div className="form-actions">
          <button type="button" className="secondary danger" onClick={() => setDeleting(true)}>{t('settings.deleteOpen')}</button>
        </div>
      </section>
      {deleting && (
        <DeleteAccountDialog developer={user.roles.includes('ROLE_DEVELOPER')} onClose={() => setDeleting(false)} />
      )}
    </>
  );
}

/**
 * Diálogo de la baja, como el de las demos: el foco entra y se queda dentro (Tab da la vuelta), Escape lo cierra y
 * al cerrarse el foco vuelve al botón que lo abrió. Con la baja ya pedida solo se sale con «Entendido», que cierra
 * la sesión.
 */
function DeleteAccountDialog({ developer, onClose }: { developer: boolean; onClose: () => void }) {
  const { logout } = useStore();
  const { t } = useI18n();
  const titleId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);
  const doneRef = useRef<HTMLButtonElement>(null);
  const opener = useRef<Element | null>(document.activeElement);
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [deletionDate, setDeletionDate] = useState<string | null>(null);

  // Escape cierra, salvo cuando la baja ya está pedida
  useEffect(() => {
    if (deletionDate) return;
    const onKey = (e: globalThis.KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [deletionDate, onClose]);

  // Al abrir, el foco entra en el diálogo (la contraseña ya lo tiene por autoFocus). Al cerrar, vuelve al botón.
  useEffect(() => {
    if (!dialogRef.current?.contains(document.activeElement)) dialogRef.current?.focus();
    const from = opener.current;
    const keepFocusInside = (e: FocusEvent) => {
      if (dialogRef.current && e.target instanceof Node && !dialogRef.current.contains(e.target)) dialogRef.current.focus();
    };
    document.addEventListener('focusin', keepFocusInside);
    return () => {
      document.removeEventListener('focusin', keepFocusInside);
      if (from instanceof HTMLElement && from.isConnected) from.focus();
    };
  }, []);

  // El formulario desaparece al terminar: el foco pasa a «Entendido»
  useEffect(() => {
    if (deletionDate) doneRef.current?.focus();
  }, [deletionDate]);

  const confirm = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      const res = await userApi.requestDeletion(password);
      setPassword('');
      setDeletionDate(formatDate(res.deletionScheduledAt));
    } catch (err) {
      // El mensaje del backend ya viene traducido (contraseña mala, último admin…)
      setError(err instanceof ApiError ? err.message : t('common.somethingWrong'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="demo-backdrop">
      <div className="demo-dialog" role="dialog" aria-modal="true" aria-labelledby={titleId} ref={dialogRef} tabIndex={-1} onKeyDown={trapTab}>
        <h2 id={titleId}>{t('settings.deleteSection')}</h2>
        {deletionDate ? (
          <>
            <p>{t('settings.deleteDone', { date: deletionDate })}</p>
            <div className="form-actions">
              <button type="button" className="primary small" ref={doneRef} onClick={logout}>{t('settings.deleteDoneButton')}</button>
            </div>
          </>
        ) : (
          <form className="form" onSubmit={confirm}>
            <p>{t('settings.deleteIntro')}</p>
            {developer && <p>{t('settings.deleteStudio')}</p>}
            <label className="field">
              <span>{t('settings.deletePassword')}</span>
              <input type="password" autoComplete="current-password" required autoFocus value={password} onChange={(e) => setPassword(e.target.value)} />
            </label>
            {error && <p className="form-error" role="alert">{error}</p>}
            <div className="form-actions">
              <button type="button" className="secondary" onClick={onClose}>{t('common.cancel')}</button>
              <button className="primary small danger-button" disabled={busy}>{t('settings.deleteConfirm')}</button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}
