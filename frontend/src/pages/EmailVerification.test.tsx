import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useNavigate } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { getRefreshToken, getToken } from '@/api/client';
import { useStore } from '@/context/StoreContext';
import { mockFetch, page } from '@/test/http';
import { demoUser, renderRoutes } from '@/test/render';
import { Register } from './Auth';
import { CheckEmail, VerifyEmail } from './EmailVerification';

const emptyCart = { items: [], itemCount: 0, total: 0 };

const session = (roles: string[] = ['ROLE_USER']) => ({
  accessToken: 'jwt-verificado', tokenType: 'Bearer', expiresIn: 900, refreshToken: 'refresh-verificado',
  user: { ...demoUser, roles },
});

/** Pinta el aviso (toast) actual: el Layout no está en estos tests. */
function ToastProbe() {
  const { toast } = useStore();
  return <output data-testid="toast">{toast?.message}</output>;
}

const sessionRoutes = { 'GET /api/cart': { body: emptyCart }, 'GET /api/wishlist': { body: page([]) } };

describe('verificación de email', () => {
  it('registro con verificación lleva a /check-email', async () => {
    const fetchMock = mockFetch({
      'POST /api/auth/register': { status: 201, body: { id: 9, email: 'ana@test.com', verificationRequired: true } },
    });
    const { path } = renderRoutes({ '/register': <Register />, '/check-email': <CheckEmail /> }, '/register');

    await userEvent.type(screen.getByLabelText('Usuario'), 'ana');
    await userEvent.type(screen.getByLabelText('Email'), 'ana@test.com');
    await userEvent.type(screen.getByLabelText(/^Contraseña/), 'password123');
    await userEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }));

    await waitFor(() => expect(path()).toBe('/check-email'));
    expect(screen.getByText(/Te hemos enviado un enlace a ana@test.com/)).toBeInTheDocument();
    expect(getToken()).toBeNull(); // sin sesión hasta pulsar el enlace
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('reenviar desde /check-email', async () => {
    const fetchMock = mockFetch({ 'POST /api/auth/resend-verification': { status: 204 } });
    renderRoutes({ '/check-email': <CheckEmail /> }, '/check-email?email=ana%40test.com');

    await userEvent.click(screen.getByRole('button', { name: 'Reenviar correo' }));

    expect(await screen.findByText(/Si la cuenta está pendiente, te hemos enviado otro enlace/)).toBeInTheDocument();
    expect(JSON.parse(String(fetchMock.mock.calls[0]![1]!.body))).toEqual({ email: 'ana@test.com' });
  });

  it('/check-email ofrece volver a crear la cuenta si el email estaba mal', () => {
    mockFetch({});
    renderRoutes({ '/check-email': <CheckEmail /> }, '/check-email?email=ana%40test.com');

    expect(screen.getByRole('link', { name: /Crea la cuenta de nuevo/ })).toHaveAttribute('href', '/register');
  });

  const preview = { 'POST /api/auth/verify-email/preview': { body: { username: 'ana' } } };

  async function fillPasswordAndSubmit(password: string) {
    await userEvent.type(await screen.findByLabelText('Contraseña'), password);
    await userEvent.click(screen.getByRole('button', { name: 'Verificar y entrar' }));
  }

  it('/verify-email?token=abc muestra la cuenta y, con la contraseña, inicia sesión', async () => {
    const fetchMock = mockFetch({ ...preview, 'POST /api/auth/verify-email': { body: session() }, ...sessionRoutes });
    const { path } = renderRoutes({ '/verify-email': <VerifyEmail />, '/': <ToastProbe /> }, '/verify-email?token=abc');

    expect(await screen.findByText('Confirma la cuenta «ana»')).toBeInTheDocument();
    // al montar solo se pide la vista previa: el enlace no se gasta hasta enviar la contraseña
    expect(fetchMock.mock.calls.filter(([url]) => String(url) === '/api/auth/verify-email')).toHaveLength(0);
    await fillPasswordAndSubmit('password123');

    await waitFor(() => expect(path()).toBe('/'));
    const call = fetchMock.mock.calls.find(([url]) => String(url) === '/api/auth/verify-email')!;
    expect(JSON.parse(String(call[1]!.body))).toEqual({ token: 'abc', password: 'password123' });
    expect(getToken()).toBe('jwt-verificado');
    expect(getRefreshToken()).toBe('refresh-verificado');
    expect(screen.getByTestId('toast')).toHaveTextContent('¡Email verificado! Bienvenido a ARCADE.');
  });

  it('un estudio verificado va a /developer', async () => {
    mockFetch({ ...preview, 'POST /api/auth/verify-email': { body: session(['ROLE_USER', 'ROLE_DEVELOPER']) }, ...sessionRoutes });
    const { path } = renderRoutes({ '/verify-email': <VerifyEmail />, '/developer': <p>Panel</p> }, '/verify-email?token=abc');

    await fillPasswordAndSubmit('password123');

    await waitFor(() => expect(path()).toBe('/developer'));
  });

  it('con StrictMode la vista previa no gasta el enlace ni se pide dos veces', async () => {
    const fetchMock = mockFetch({ ...preview });
    renderRoutes({ '/verify-email': <VerifyEmail /> }, '/verify-email?token=abc', { strict: true });

    await screen.findByText('Confirma la cuenta «ana»');
    expect(fetchMock.mock.calls.filter(([url]) => String(url) === '/api/auth/verify-email/preview')).toHaveLength(1);
    expect(fetchMock.mock.calls.filter(([url]) => String(url) === '/api/auth/verify-email')).toHaveLength(0);
  });

  it('una contraseña que no es la del registro muestra el error bajo el campo y deja reintentar', async () => {
    let calls = 0;
    mockFetch({
      ...preview,
      'POST /api/auth/verify-email': () => (++calls === 1
        ? { status: 400, body: { detail: 'La contraseña no coincide con la de este registro.', code: 'auth.verificationPasswordInvalid' } }
        : { body: session() }),
      ...sessionRoutes,
    });
    const { path } = renderRoutes({ '/verify-email': <VerifyEmail />, '/': <p>Inicio</p> }, '/verify-email?token=abc');

    await fillPasswordAndSubmit('equivocada1');
    expect(await screen.findByText('La contraseña no coincide con la de este registro.')).toBeInTheDocument();
    expect(path()).toBe('/verify-email');
    expect(screen.queryByText('El enlace no es válido o ha caducado.')).not.toBeInTheDocument();

    await userEvent.clear(screen.getByLabelText('Contraseña'));
    await userEvent.type(screen.getByLabelText('Contraseña'), 'password123');
    await userEvent.click(screen.getByRole('button', { name: 'Verificar y entrar' }));
    await waitFor(() => expect(path()).toBe('/'));
  });

  it('un enlace que se anula al enviar (400 auth.verificationInvalid) pasa a enlace inválido con reenvío', async () => {
    mockFetch({
      ...preview,
      'POST /api/auth/verify-email': { status: 400, body: { detail: 'El enlace no es válido o ha caducado.', code: 'auth.verificationInvalid' } },
    });
    renderRoutes({ '/verify-email': <VerifyEmail /> }, '/verify-email?token=abc');

    await fillPasswordAndSubmit('equivocada1');

    expect(await screen.findByText('El enlace no es válido o ha caducado.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reenviar correo' })).toBeInTheDocument();
  });

  it('/verify-email con enlace inválido (la vista previa da 400) ofrece reenviar', async () => {
    const fetchMock = mockFetch({
      'POST /api/auth/verify-email/preview': { status: 400, body: { detail: 'El enlace no es válido o ha caducado.', code: 'auth.verificationInvalid' } },
      'POST /api/auth/resend-verification': { status: 204 },
    });
    renderRoutes({ '/verify-email': <VerifyEmail /> }, '/verify-email?token=viejo');

    expect(await screen.findByText('El enlace no es válido o ha caducado.')).toBeInTheDocument();
    expect(getToken()).toBeNull();
    await userEvent.type(screen.getByLabelText('Tu email'), 'ana@test.com');
    await userEvent.click(screen.getByRole('button', { name: 'Reenviar correo' }));

    expect(await screen.findByText(/Si la cuenta está pendiente/)).toBeInTheDocument();
    const resend = fetchMock.mock.calls.find(([url]) => String(url) === '/api/auth/resend-verification')!;
    expect(JSON.parse(String(resend[1]!.body))).toEqual({ email: 'ana@test.com' });
  });

  it('un fallo de la vista previa que no es 400 muestra su mensaje y deja reintentar', async () => {
    let calls = 0;
    const fetchMock = mockFetch({
      'POST /api/auth/verify-email/preview': () => (++calls === 1
        ? { status: 503, body: { detail: 'Servicio no disponible, inténtalo más tarde' } }
        : { body: { username: 'ana' } }),
    });
    renderRoutes({ '/verify-email': <VerifyEmail /> }, '/verify-email?token=abc');

    expect(await screen.findByText('Servicio no disponible, inténtalo más tarde')).toBeInTheDocument();
    expect(screen.queryByText('El enlace no es válido o ha caducado.')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('Confirma la cuenta «ana»')).toBeInTheDocument();
    expect(fetchMock.mock.calls.filter(([url]) => String(url) === '/api/auth/verify-email/preview')).toHaveLength(2);
  });

  it('un 409 al verificar (usuario ya cogido) se muestra bajo el campo sin llamarlo enlace inválido', async () => {
    mockFetch({ ...preview, 'POST /api/auth/verify-email': { status: 409, body: { detail: 'El nombre de usuario ya está en uso' } } });
    renderRoutes({ '/verify-email': <VerifyEmail /> }, '/verify-email?token=abc');

    await fillPasswordAndSubmit('password123');

    expect(await screen.findByText('El nombre de usuario ya está en uso')).toBeInTheDocument();
    expect(screen.queryByText('El enlace no es válido o ha caducado.')).not.toBeInTheDocument();
  });

  it('quita el token de la barra de direcciones', async () => {
    const replaceState = vi.spyOn(window.history, 'replaceState');
    mockFetch({ ...preview });
    renderRoutes({ '/verify-email': <VerifyEmail /> }, '/verify-email?token=abc');

    await screen.findByText('Confirma la cuenta «ana»');
    expect(replaceState).toHaveBeenCalled();
    expect(String(replaceState.mock.calls[0]![2])).not.toContain('token');
    replaceState.mockRestore();
  });

  it('si cambia el token se pide la vista previa del nuevo', async () => {
    const fetchMock = mockFetch({
      'POST /api/auth/verify-email/preview': (init) => (JSON.parse(String(init.body)).token === 'viejo'
        ? { status: 400, body: { detail: 'El enlace no es válido o ha caducado.' } }
        : { body: { username: 'nueva' } }),
    });
    function GoToNew() {
      const navigate = useNavigate();
      return <button onClick={() => navigate('/verify-email?token=nuevo')}>ir</button>;
    }
    renderRoutes({ '/verify-email': <><VerifyEmail /><GoToNew /></> }, '/verify-email?token=viejo');

    await screen.findByText('El enlace no es válido o ha caducado.');
    await userEvent.click(screen.getByRole('button', { name: 'ir' }));

    expect(await screen.findByText('Confirma la cuenta «nueva»')).toBeInTheDocument();
    const tokens = fetchMock.mock.calls.map(([, init]) => JSON.parse(String(init!.body)).token);
    expect(tokens).toEqual(['viejo', 'nuevo']);
  });

  it('/verify-email sin token muestra enlace inválido sin llamar a la API', () => {
    const fetchMock = mockFetch({});
    renderRoutes({ '/verify-email': <VerifyEmail /> }, '/verify-email');

    expect(screen.getByText('El enlace no es válido o ha caducado.')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
