// Verificación de email: /check-email (avisa de que el correo va de camino) y /verify-email (el enlace).
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { ApiError } from '@/api/client';
import { authApi } from '@/api/endpoints';
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

/**
 * El enlace del correo. Primero pregunta de qué cuenta es (vista previa, que no gasta el enlace) y pide la
 * contraseña de ese registro; con ella canjea el enlace, inicia la sesión y lleva a la tienda (o al panel del
 * estudio). Pedir la contraseña evita que alguien que registró el mismo email antes se quede con la cuenta.
 */
export function VerifyEmail() {
  const { verifyEmail, notify } = useStore();
  const { t } = useI18n();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const token = params.get('token');
  type Stage =
    | { kind: 'loading' }
    | { kind: 'invalid' }
    | { kind: 'error'; message: string }
    | { kind: 'ready'; username: string };
  const [stage, setStage] = useState<Stage>(token ? { kind: 'loading' } : { kind: 'invalid' });
  const [attemptNo, setAttemptNo] = useState(0);
  const [password, setPassword] = useState('');
  const [passwordError, setPasswordError] = useState('');
  const [busy, setBusy] = useState(false);
  // La vista previa es idempotente, pero se guarda la promesa para no repetirla con el doble montaje de StrictMode
  const preview = useRef<{ token: string; no: number; promise: Promise<{ username: string }> } | null>(null);

  useEffect(() => {
    if (!token) return;
    // el token no se queda en la barra de direcciones ni en el historial
    window.history.replaceState(window.history.state, '', window.location.pathname);
    if (preview.current?.token !== token || preview.current.no !== attemptNo) {
      preview.current = { token, no: attemptNo, promise: authApi.previewVerification(token) };
    }
    let active = true;
    preview.current.promise
      .then((res) => {
        if (active) setStage({ kind: 'ready', username: res.username });
      })
      .catch((err: unknown) => {
        if (!active) return;
        if (err instanceof ApiError && err.status === 400) setStage({ kind: 'invalid' });
        else setStage({ kind: 'error', message: err instanceof ApiError ? err.message : t('common.somethingWrong') });
      });
    return () => {
      active = false;
    };
  }, [token, attemptNo, t]);

  const retry = () => {
    setStage({ kind: 'loading' });
    setAttemptNo((n) => n + 1);
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!token) return;
    setBusy(true);
    setPasswordError('');
    try {
      const user = await verifyEmail(token, password);
      notify(t('verify.done'));
      navigate(user.roles.includes('ROLE_DEVELOPER') ? '/developer' : '/', { replace: true });
    } catch (err) {
      if (err instanceof ApiError && err.status === 400 && err.code !== 'auth.verificationPasswordInvalid') {
        setStage({ kind: 'invalid' }); // el enlace se gastó, caducó o se anuló por demasiados intentos
      } else {
        // contraseña que no es la de este registro (se puede reintentar) u otro fallo (409, 403, red...)
        setPasswordError(err instanceof ApiError ? err.message : t('common.somethingWrong'));
      }
    } finally {
      setBusy(false);
    }
  };

  if (stage.kind === 'invalid') {
    return (
      <AuthShell eyebrow={t('verify.eyebrow')} title={t('verify.invalid')}
        footer={<Link to="/login">{t('auth.loginTitle')}</Link>}>
        <ResendVerification />
      </AuthShell>
    );
  }
  if (stage.kind === 'error') {
    return (
      <AuthShell eyebrow={t('verify.eyebrow')} title={t('verify.failedTitle')}
        footer={<Link to="/login">{t('auth.loginTitle')}</Link>}>
        <p className="form-error" role="alert">{stage.message}</p>
        <button className="secondary full" onClick={retry}>{t('common.retry')}</button>
      </AuthShell>
    );
  }
  if (stage.kind === 'ready') {
    return (
      <AuthShell eyebrow={t('verify.eyebrow')} title={t('verify.confirmAccount', { user: stage.username })}>
        <form onSubmit={submit} className="form">
          <p className="hint">{t('verify.passwordHint')}</p>
          <label className="field">
            <span>{t('common.password')}</span>
            <input type="password" autoComplete="current-password" required maxLength={100} value={password}
              onChange={(e) => setPassword(e.target.value)} />
          </label>
          {passwordError && <p className="form-error" role="alert">{passwordError}</p>}
          <button className="primary full" disabled={busy || !password}>
            {busy ? t('verify.submitting') : t('verify.submit')}
          </button>
        </form>
      </AuthShell>
    );
  }
  return (
    <AuthShell eyebrow={t('verify.eyebrow')} title={t('verify.verifying')} />
  );
}
