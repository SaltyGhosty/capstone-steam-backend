import { useState, type FormEvent, type ReactNode } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router';
import { ApiError } from '@/api/client';
import type { AccountType } from '@/api/types';
import { FieldError } from '@/components/ui';
import { useStore } from '@/context/StoreContext';
import { detectLocale } from '@/lib/locale';

function AuthShell({ eyebrow, title, children, footer }: { eyebrow: string; title: string; children: ReactNode; footer: ReactNode }) {
  return (
    <div className="auth">
      <div className="auth-card panel">
        <span className="brand-mark"><span /></span>
        <p className="eyebrow">{eyebrow}</p>
        <h2>{title}</h2>
        {children}
        <p className="auth-footer">{footer}</p>
      </div>
    </div>
  );
}

function useRedirectTarget() {
  const location = useLocation();
  return (location.state as { from?: string } | null)?.from ?? '/';
}

export function Login() {
  const { user, login, notify } = useStore();
  const navigate = useNavigate();
  const target = useRedirectTarget();
  const [form, setForm] = useState({ login: '', password: '' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to={target} replace />;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      await login(form.login.trim(), form.password);
      notify('¡Bienvenido de nuevo!');
      navigate(target, { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'No se pudo iniciar sesión');
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthShell eyebrow="BIENVENIDO DE NUEVO" title="Inicia sesión"
      footer={<>¿No tienes cuenta? <Link to="/register" state={{ from: target }}>Crea una</Link></>}>
      <form onSubmit={submit} className="form">
        <label className="field">
          <span>Usuario o email</span>
          <input autoComplete="username" required value={form.login} onChange={(e) => setForm({ ...form, login: e.target.value })} />
        </label>
        <label className="field">
          <span>Contraseña</span>
          <input type="password" autoComplete="current-password" required value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} />
        </label>
        {error && <p className="form-error" role="alert">{error}</p>}
        <button className="primary full" disabled={busy}>{busy ? 'Entrando…' : 'Entrar'}</button>
      </form>
    </AuthShell>
  );
}

export function Register() {
  const { user, register, notify } = useStore();
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
      // El idioma de los correos se deduce del navegador; se puede cambiar luego en Ajustes
      await register({ ...form, displayName: form.displayName.trim() || undefined, locale: detectLocale() });
      notify('¡Cuenta creada! Te hemos enviado un correo de bienvenida.');
      navigate(form.accountType === 'DEVELOPER' ? '/developer' : target, { replace: true });
    } catch (err) {
      if (err instanceof ApiError) {
        setErrors(err.fieldErrors);
        if (!Object.keys(err.fieldErrors).length) setError(err.message);
      } else {
        setError('No se pudo crear la cuenta');
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthShell eyebrow="ÚNETE A ARCADE" title="Crea tu cuenta"
      footer={<>¿Ya tienes cuenta? <Link to="/login" state={{ from: target }}>Inicia sesión</Link></>}>
      <form onSubmit={submit} className="form">
        <div className="segmented" role="radiogroup" aria-label="Tipo de cuenta">
          <button type="button" role="radio" aria-checked={form.accountType === 'PLAYER'} className={form.accountType === 'PLAYER' ? 'active' : ''}
            onClick={() => setForm({ ...form, accountType: 'PLAYER' })}>Quiero jugar</button>
          <button type="button" role="radio" aria-checked={form.accountType === 'DEVELOPER'} className={form.accountType === 'DEVELOPER' ? 'active' : ''}
            onClick={() => setForm({ ...form, accountType: 'DEVELOPER' })}>Publico juegos</button>
        </div>
        <label className="field">
          <span>Usuario</span>
          <input autoComplete="username" required minLength={3} maxLength={50} value={form.username} onChange={set('username')} />
          <FieldError errors={errors} name="username" />
        </label>
        <label className="field">
          <span>Email</span>
          <input type="email" autoComplete="email" required value={form.email} onChange={set('email')} />
          <FieldError errors={errors} name="email" />
        </label>
        <label className="field">
          <span>{form.accountType === 'DEVELOPER' ? 'Nombre del estudio' : 'Nombre visible'} <em>(opcional)</em></span>
          <input maxLength={100} value={form.displayName} onChange={set('displayName')} />
          <FieldError errors={errors} name="displayName" />
        </label>
        <label className="field">
          <span>Contraseña</span>
          <input type="password" autoComplete="new-password" required minLength={8} maxLength={72} value={form.password} onChange={set('password')} />
          <FieldError errors={errors} name="password" />
          <small className="hint">Mínimo 8 caracteres.</small>
        </label>
        {error && <p className="form-error" role="alert">{error}</p>}
        <button className="primary full" disabled={busy}>{busy ? 'Creando cuenta…' : 'Crear cuenta'}</button>
      </form>
    </AuthShell>
  );
}
