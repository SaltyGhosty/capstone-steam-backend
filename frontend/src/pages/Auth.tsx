import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router';
import { ApiError } from '@/api/client';
import type { AccountType } from '@/api/types';
import AuthShell from '@/components/AuthShell';
import { FieldError } from '@/components/ui';
import { useStore } from '@/context/StoreContext';
import { useI18n } from '@/i18n';
import { ResendVerification } from './EmailVerification';

function useRedirectTarget() {
  const location = useLocation();
  return (location.state as { from?: string } | null)?.from ?? '/';
}

export function Login() {
  const { user, login, notify } = useStore();
  const { t } = useI18n();
  const navigate = useNavigate();
  const target = useRedirectTarget();
  const [form, setForm] = useState({ login: '', password: '' });
  const [error, setError] = useState('');
  // La cuenta existe pero falta pulsar el enlace del correo: se ofrece reenviarlo
  const [unverified, setUnverified] = useState(false);
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to={target} replace />;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    setUnverified(false);
    try {
      await login(form.login.trim(), form.password);
      notify(t('auth.welcomeBack'));
      navigate(target, { replace: true });
    } catch (err) {
      if (err instanceof ApiError && err.code === 'auth.emailNotVerified') {
        setUnverified(true);
      } else {
        setError(err instanceof ApiError ? err.message : t('auth.loginFailed'));
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthShell eyebrow={t('auth.loginEyebrow')} title={t('auth.loginTitle')}
      footer={<>{t('auth.noAccount')} <Link to="/register" state={{ from: target }}>{t('auth.createOne')}</Link></>}>
      <form onSubmit={submit} className="form">
        <label className="field">
          <span>{t('auth.userOrEmail')}</span>
          <input autoComplete="username" required value={form.login} onChange={(e) => setForm({ ...form, login: e.target.value })} />
        </label>
        <label className="field">
          <span>{t('common.password')}</span>
          <input type="password" autoComplete="current-password" required value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} />
        </label>
        {error && <p className="form-error" role="alert">{error}</p>}
        <button className="primary full" disabled={busy}>{busy ? t('auth.entering') : t('auth.enter')}</button>
      </form>
      {unverified && (
        <>
          <p className="form-error" role="alert">{t('verify.notVerified')}</p>
          {/* si escribió un email se usa tal cual; si escribió un usuario, el reenvío pide el email */}
          <ResendVerification email={form.login.includes('@') ? form.login.trim() : undefined} />
        </>
      )}
    </AuthShell>
  );
}

export function Register() {
  const { user, register, notify } = useStore();
  const { lang, t } = useI18n();
  const navigate = useNavigate();
  const target = useRedirectTarget();
  const [form, setForm] = useState({ username: '', email: '', password: '', displayName: '', accountType: 'PLAYER' as AccountType });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to={target} replace />;

  const set = (field: keyof typeof form) => (e: { target: { value: string } }) => setForm({ ...form, [field]: e.target.value });

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setErrors({});
    setError('');
    try {
      // Los correos salen en el idioma elegido con las banderas; se puede cambiar luego en Ajustes
      const result = await register({ ...form, displayName: form.displayName.trim() || undefined, locale: lang });
      notify(t('auth.created'));
      // Con verificación de email no hay sesión: primero hay que pulsar el enlace del correo
      if (result.verificationRequired) {
        navigate(`/check-email?email=${encodeURIComponent(result.email)}`, { replace: true });
        return;
      }
      navigate(form.accountType === 'DEVELOPER' ? '/developer' : target, { replace: true });
    } catch (err) {
      if (err instanceof ApiError) {
        setErrors(err.fieldErrors);
        if (!Object.keys(err.fieldErrors).length) setError(err.message);
      } else {
        setError(t('auth.registerFailed'));
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthShell eyebrow={t('auth.registerEyebrow')} title={t('auth.registerTitle')}
      footer={<>{t('auth.haveAccount')} <Link to="/login" state={{ from: target }}>{t('auth.loginTitle')}</Link></>}>
      <form onSubmit={submit} className="form">
        <div className="segmented" role="radiogroup" aria-label={t('auth.accountType')}>
          <button type="button" role="radio" aria-checked={form.accountType === 'PLAYER'} className={form.accountType === 'PLAYER' ? 'active' : ''}
            onClick={() => setForm({ ...form, accountType: 'PLAYER' })}>{t('auth.wantPlay')}</button>
          <button type="button" role="radio" aria-checked={form.accountType === 'DEVELOPER'} className={form.accountType === 'DEVELOPER' ? 'active' : ''}
            onClick={() => setForm({ ...form, accountType: 'DEVELOPER' })}>{t('auth.publish')}</button>
        </div>
        <label className="field">
          <span>{t('common.username')}</span>
          <input autoComplete="username" required minLength={3} maxLength={50} value={form.username} onChange={set('username')} />
          <FieldError errors={errors} name="username" />
        </label>
        <label className="field">
          <span>{t('common.email')}</span>
          <input type="email" autoComplete="email" required value={form.email} onChange={set('email')} />
          <FieldError errors={errors} name="email" />
        </label>
        <label className="field">
          <span>{form.accountType === 'DEVELOPER' ? t('auth.studioName') : t('auth.displayName')} <em>{t('common.optional')}</em></span>
          <input maxLength={100} value={form.displayName} onChange={set('displayName')} />
          <FieldError errors={errors} name="displayName" />
        </label>
        <label className="field">
          <span>{t('common.password')}</span>
          <input type="password" autoComplete="new-password" required minLength={8} maxLength={72} value={form.password} onChange={set('password')} />
          <FieldError errors={errors} name="password" />
          <small className="hint">{t('auth.minChars')}</small>
        </label>
        {error && <p className="form-error" role="alert">{error}</p>}
        <button className="primary full" disabled={busy}>{busy ? t('auth.creating') : t('nav.register')}</button>
      </form>
    </AuthShell>
  );
}
