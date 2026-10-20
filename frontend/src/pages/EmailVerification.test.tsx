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

  it('/verify-email?token=abc inicia sesión', async () => {
    const fetchMock = mockFetch({ 'POST /api/auth/verify-email': { body: session() }, ...sessionRoutes });
    const { path } = renderRoutes({ '/verify-email': <VerifyEmail />, '/': <ToastProbe /> }, '/verify-email?token=abc');

    await waitFor(() => expect(path()).toBe('/'));
    expect(JSON.parse(String(fetchMock.mock.calls[0]![1]!.body))).toEqual({ token: 'abc' });
    expect(getToken()).toBe('jwt-verificado');
    expect(getRefreshToken()).toBe('refresh-verificado');
    expect(screen.getByTestId('toast')).toHaveTextContent('¡Email verificado! Bienvenido a ARCADE.');
  });

  it('un estudio verificado va a /developer', async () => {
    mockFetch({ 'POST /api/auth/verify-email': { body: session(['ROLE_USER', 'ROLE_DEVELOPER']) }, ...sessionRoutes });
    const { path } = renderRoutes({ '/verify-email': <VerifyEmail />, '/developer': <p>Panel</p> }, '/verify-email?token=abc');

    await waitFor(() => expect(path()).toBe('/developer'));
  });

  it('/verify-email llama una sola vez aunque se monte dos veces', async () => {
    const fetchMock = mockFetch({ 'POST /api/auth/verify-email': { body: session() }, ...sessionRoutes });
    const { path } = renderRoutes({ '/verify-email': <VerifyEmail />, '/': <p>Inicio</p> }, '/verify-email?token=abc', { strict: true });

    await waitFor(() => expect(path()).toBe('/'));
    const verifyCalls = fetchMock.mock.calls.filter(([url]) => String(url) === '/api/auth/verify-email');
    expect(verifyCalls).toHaveLength(1);
  });

  it('/verify-email con enlace inválido ofrece reenviar', async () => {
    const fetchMock = mockFetch({
      'POST /api/auth/verify-email': { status: 400, body: { detail: 'El enlace no es válido o ha caducado.', code: 'auth.verificationInvalid' } },
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

  it('un fallo que no es 400 muestra su mensaje y deja reintentar sin llamarlo enlace inválido', async () => {
    let calls = 0;
    const fetchMock = mockFetch({
      'POST /api/auth/verify-email': () => (++calls === 1
        ? { status: 503, body: { detail: 'Servicio no disponible, inténtalo más tarde' } }
        : { body: session() }),
      ...sessionRoutes,
    });
    const { path } = renderRoutes({ '/verify-email': <VerifyEmail />, '/': <p>Inicio</p> }, '/verify-email?token=abc');

    expect(await screen.findByText('Servicio no disponible, inténtalo más tarde')).toBeInTheDocument();
    expect(screen.queryByText('El enlace no es válido o ha caducado.')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Tu email')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }));

    await waitFor(() => expect(path()).toBe('/'));
    expect(fetchMock.mock.calls.filter(([url]) => String(url) === '/api/auth/verify-email')).toHaveLength(2);
  });

  it('un 409 (usuario ya cogido) tampoco se muestra como enlace inválido', async () => {
    mockFetch({ 'POST /api/auth/verify-email': { status: 409, body: { detail: 'El nombre de usuario ya está en uso' } } });
    renderRoutes({ '/verify-email': <VerifyEmail /> }, '/verify-email?token=abc');

    expect(await screen.findByText('El nombre de usuario ya está en uso')).toBeInTheDocument();
    expect(screen.queryByText('El enlace no es válido o ha caducado.')).not.toBeInTheDocument();
  });

  it('quita el token de la barra de direcciones', async () => {
    const replaceState = vi.spyOn(window.history, 'replaceState');
    mockFetch({ 'POST /api/auth/verify-email': { body: session() }, ...sessionRoutes });
    const { path } = renderRoutes({ '/verify-email': <VerifyEmail />, '/': <p>Inicio</p> }, '/verify-email?token=abc');

    await waitFor(() => expect(path()).toBe('/'));
    expect(replaceState).toHaveBeenCalled();
    expect(String(replaceState.mock.calls[0]![2])).not.toContain('token');
    replaceState.mockRestore();
  });

  it('si cambia el token se canjea el nuevo', async () => {
    const fetchMock = mockFetch({
      'POST /api/auth/verify-email': (init) => (JSON.parse(String(init.body)).token === 'viejo'
        ? { status: 400, body: { detail: 'El enlace no es válido o ha caducado.' } }
        : { body: session() }),
      ...sessionRoutes,
    });
    function GoToNew() {
      const navigate = useNavigate();
      return <button onClick={() => navigate('/verify-email?token=nuevo')}>ir</button>;
    }
    const { path } = renderRoutes({ '/verify-email': <><VerifyEmail /><GoToNew /></>, '/': <p>Inicio</p> }, '/verify-email?token=viejo');

    await screen.findByText('El enlace no es válido o ha caducado.');
    await userEvent.click(screen.getByRole('button', { name: 'ir' }));

    await waitFor(() => expect(path()).toBe('/'));
    const tokens = fetchMock.mock.calls.filter(([url]) => String(url) === '/api/auth/verify-email')
      .map(([, init]) => JSON.parse(String(init!.body)).token);
    expect(tokens).toEqual(['viejo', 'nuevo']);
  });

  it('/verify-email sin token muestra enlace inválido sin llamar a la API', () => {
    const fetchMock = mockFetch({});
    renderRoutes({ '/verify-email': <VerifyEmail /> }, '/verify-email');

    expect(screen.getByText('El enlace no es válido o ha caducado.')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
