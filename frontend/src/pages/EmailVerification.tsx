// Verificación de email: /check-email (avisa de que el correo va de camino) y /verify-email (el enlace).
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { ApiError } from '@/api/client';
import { authApi } from '@/api/endpoints';
import type { User } from '@/api/types';
import AuthShell from '@/components/AuthShell';
import { useStore } from '@/context/StoreContext';
import { useI18n } from '@/i18n';

/**
 * «Reenviar correo». Con el email ya conocido solo muestra el botón; sin él, pide el email antes.
 * El backend responde siempre 204 (no dice si la cuenta existe), así que el aviso es genérico.
 */
export function ResendVerification({ email }: { email?: string }) {
  const { t } = useI18n();
  const [typed, setTyped] = useState('');
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState('');
  const target = (email ?? typed).trim();

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!target) return;
    setBusy(true);
    setError('');
    setSent(false);
    try {
      await authApi.resendVerification(target);
      setSent(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.somethingWrong'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <form onSubmit={submit} className="form">
      {email === undefined && (
        <label className="field">
          <span>{t('verify.emailLabel')}</span>
          <input type="email" autoComplete="email" required value={typed} onChange={(e) => setTyped(e.target.value)} />
        </label>
      )}
      {sent && <p className="hint" role="status">{t('verify.resent')}</p>}
      {error && <p className="form-error" role="alert">{error}</p>}
      <button className="secondary full" disabled={busy || !target}>{t('verify.resend')}</button>
    </form>
  );
}

/** Tras registrarse: «Revisa tu correo». El email viaja en la query (?email=). */
export function CheckEmail() {
  const { t } = useI18n();
  const [params] = useSearchParams();
  const email = params.get('email')?.trim() || undefined;

  return (
    <AuthShell eyebrow={t('verify.eyebrow')} title={t('verify.checkTitle')}
      footer={<Link to="/register">{t('verify.wrongEmail')}</Link>}>
      {email && <p>{t('verify.checkText', { email })}</p>}
      <ResendVerification email={email} />
    </AuthShell>
  );
}

/** El enlace del correo: canjea el token, inicia la sesión y lleva a la tienda (o al panel del estudio). */
export function VerifyEmail() {
  const { verifyEmail, notify } = useStore();
  const { t } = useI18n();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const token = params.get('token');
  // null = verificando; 'invalid' = el backend dijo 400 (usado, caducado o inexistente); otro texto = fallo
  // distinto (red, 5xx, 429, usuario ya cogido, cuenta desactivada), que se muestra tal cual con «Reintentar»
  const [failure, setFailure] = useState<{ kind: 'invalid' } | { kind: 'other'; message: string } | null>(
    token ? null : { kind: 'invalid' },
  );
  const [attemptNo, setAttemptNo] = useState(0);
  // La promesa se guarda (con su token) para que el doble montaje de React en desarrollo (StrictMode) no gaste
  // el enlace dos veces: la segunda petición daría «no válido» justo después de iniciar sesión.
  const attempt = useRef<{ token: string; no: number; promise: Promise<User> } | null>(null);

  useEffect(() => {
    if (!token) return;
    // el token no se queda en la barra de direcciones ni en el historial
    window.history.replaceState(window.history.state, '', window.location.pathname);
    if (attempt.current?.token !== token || attempt.current.no !== attemptNo) {
      attempt.current = { token, no: attemptNo, promise: verifyEmail(token) };
    }
    let active = true;
    attempt.current.promise
      .then((user) => {
        if (!active) return;
        notify(t('verify.done'));
        navigate(user.roles.includes('ROLE_DEVELOPER') ? '/developer' : '/', { replace: true });
      })
      .catch((err: unknown) => {
        if (!active) return;
        if (err instanceof ApiError && err.status === 400) setFailure({ kind: 'invalid' });
        else setFailure({ kind: 'other', message: err instanceof ApiError ? err.message : t('common.somethingWrong') });
      });
    return () => {
      active = false;
    };
  }, [token, attemptNo, verifyEmail, notify, navigate, t]);

  const retry = () => {
    setFailure(null);
    setAttemptNo((n) => n + 1);
  };

  if (failure?.kind === 'invalid') {
    return (
      <AuthShell eyebrow={t('verify.eyebrow')} title={t('verify.invalid')}
        footer={<Link to="/login">{t('auth.loginTitle')}</Link>}>
        <ResendVerification />
      </AuthShell>
    );
  }
  if (failure?.kind === 'other') {
    return (
      <AuthShell eyebrow={t('verify.eyebrow')} title={t('verify.failedTitle')}
        footer={<Link to="/login">{t('auth.loginTitle')}</Link>}>
        <p className="form-error" role="alert">{failure.message}</p>
        <button className="secondary full" onClick={retry}>{t('common.retry')}</button>
      </AuthShell>
    );
  }
  return (
    <AuthShell eyebrow={t('verify.eyebrow')} title={t('verify.verifying')} />
  );
}
