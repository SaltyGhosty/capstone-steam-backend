import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { getRefreshToken, getToken } from '@/api/client';
import { mockFetch, page } from '@/test/http';
import { demoUser, renderRoutes } from '@/test/render';
import { Login, Register } from './Auth';

const emptyCart = { items: [], itemCount: 0, total: 0 };

describe('login y registro', () => {
  it('inicia sesión, guarda el token y vuelve a la página de la que venía', async () => {
    const fetchMock = mockFetch({
      'POST /api/auth/login': { body: { accessToken: 'jwt-demo', tokenType: 'Bearer', expiresIn: 900, refreshToken: 'refresh-demo', user: demoUser } },
      'GET /api/cart': { body: emptyCart },
      'GET /api/wishlist': { body: page([]) },
    });
    const { path } = renderRoutes({ '/login': <Login />, '/': <p>Inicio</p> }, '/login');

    await userEvent.type(screen.getByLabelText('Usuario o email'), 'demo_player');
    await userEvent.type(screen.getByLabelText('Contraseña'), 'password123');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    await waitFor(() => expect(path()).toBe('/'));
    expect(getToken()).toBe('jwt-demo');
    expect(getRefreshToken()).toBe('refresh-demo');
    const body = JSON.parse(String(fetchMock.mock.calls[0]![1]!.body));
    expect(body).toEqual({ login: 'demo_player', password: 'password123' });
  });

  it('muestra el error del backend si las credenciales no valen', async () => {
    mockFetch({ 'POST /api/auth/login': { status: 401, body: { detail: 'Credenciales incorrectas' } } });
    renderRoutes({ '/login': <Login /> }, '/login');

    await userEvent.type(screen.getByLabelText('Usuario o email'), 'demo_player');
    await userEvent.type(screen.getByLabelText('Contraseña'), 'mala');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Credenciales incorrectas');
    expect(getToken()).toBeNull();
  });

  it('pinta los errores de validación junto a cada campo del registro', async () => {
    const fetchMock = mockFetch({
      'POST /api/auth/register': {
        status: 400,
        body: { detail: 'Datos no válidos', errors: { username: "solo letras, números, '_', '.' y '-'" } },
      },
    });
    renderRoutes({ '/register': <Register /> }, '/register');

    await userEvent.click(screen.getByRole('radio', { name: 'Publico juegos' }));
    await userEvent.type(screen.getByLabelText('Usuario'), 'mal nombre');
    await userEvent.type(screen.getByLabelText('Email'), 'dev@test.com');
    await userEvent.type(screen.getByLabelText(/^Contraseña/), 'password123');
    await userEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }));

    expect(await screen.findByText("solo letras, números, '_', '.' y '-'")).toBeInTheDocument();
    expect(screen.getByLabelText(/Nombre del estudio/)).toBeInTheDocument();
    // los correos salen en el idioma elegido con las banderas (aquí, español)
    expect(JSON.parse(String(fetchMock.mock.calls[0]![1]!.body)).locale).toBe('es');
  });
});

describe('verificación de email en login y registro', () => {
  const notVerified = {
    status: 403,
    body: { detail: 'Tu email no está verificado. Revisa tu correo o pide otro enlace.', code: 'auth.emailNotVerified' },
  };

  it('login de cuenta sin verificar ofrece reenviar con el email escrito', async () => {
    const fetchMock = mockFetch({
      'POST /api/auth/login': notVerified,
      'POST /api/auth/resend-verification': { status: 204 },
    });
    renderRoutes({ '/login': <Login /> }, '/login');

    await userEvent.type(screen.getByLabelText('Usuario o email'), 'ana@test.com');
    await userEvent.type(screen.getByLabelText('Contraseña'), 'password123');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(await screen.findByText('Tu email no está verificado.')).toBeInTheDocument();
    expect(screen.queryByLabelText('Tu email')).not.toBeInTheDocument(); // ya escribió un email
    await userEvent.click(screen.getByRole('button', { name: 'Reenviar correo' }));

    expect(await screen.findByText(/Si la cuenta está pendiente/)).toBeInTheDocument();
    const resend = fetchMock.mock.calls.find(([url]) => String(url) === '/api/auth/resend-verification')!;
    expect(JSON.parse(String(resend[1]!.body))).toEqual({ email: 'ana@test.com' });
    expect(getToken()).toBeNull();
  });

  it('si escribió un usuario, el reenvío pide el email', async () => {
    const fetchMock = mockFetch({
      'POST /api/auth/login': notVerified,
      'POST /api/auth/resend-verification': { status: 204 },
    });
    renderRoutes({ '/login': <Login /> }, '/login');

    await userEvent.type(screen.getByLabelText('Usuario o email'), 'ana');
    await userEvent.type(screen.getByLabelText('Contraseña'), 'password123');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(await screen.findByText('Tu email no está verificado.')).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Tu email'), 'ana@test.com');
    await userEvent.click(screen.getByRole('button', { name: 'Reenviar correo' }));

    await screen.findByText(/Si la cuenta está pendiente/);
    const resend = fetchMock.mock.calls.find(([url]) => String(url) === '/api/auth/resend-verification')!;
    expect(JSON.parse(String(resend[1]!.body))).toEqual({ email: 'ana@test.com' });
  });

  it('una contraseña mala (401) no ofrece reenviar', async () => {
    mockFetch({ 'POST /api/auth/login': { status: 401, body: { detail: 'Usuario o contraseña incorrectos' } } });
    renderRoutes({ '/login': <Login /> }, '/login');

    await userEvent.type(screen.getByLabelText('Usuario o email'), 'ana');
    await userEvent.type(screen.getByLabelText('Contraseña'), 'mala');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Usuario o contraseña incorrectos');
    expect(screen.queryByRole('button', { name: 'Reenviar correo' })).not.toBeInTheDocument();
  });

  it('registro sin verificación (respuesta con tokens) inicia sesión como hoy', async () => {
    mockFetch({
      'POST /api/auth/register': { status: 201, body: { accessToken: 'jwt-demo', tokenType: 'Bearer', expiresIn: 900, refreshToken: 'refresh-demo', user: demoUser } },
      'GET /api/cart': { body: emptyCart },
      'GET /api/wishlist': { body: page([]) },
    });
    const { path } = renderRoutes({ '/register': <Register />, '/': <p>Inicio</p> }, '/register');

    await userEvent.type(screen.getByLabelText('Usuario'), 'demo_player');
    await userEvent.type(screen.getByLabelText('Email'), 'demo@test.com');
    await userEvent.type(screen.getByLabelText(/^Contraseña/), 'password123');
    await userEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }));

    await waitFor(() => expect(path()).toBe('/'));
    expect(getToken()).toBe('jwt-demo');
    expect(getRefreshToken()).toBe('refresh-demo');
  });
});

describe('login con la cuenta en pausa', () => {
  const pending = (code: string, detail: string) => ({
    status: 403,
    body: { detail, code, deletionScheduledAt: '2026-11-05T04:15:00Z' },
  });

  it('cuenta en pausa ofrece Recuperar mi cuenta y entra', async () => {
    const fetchMock = mockFetch({
      'POST /api/auth/login': pending('auth.accountPendingDeletion', 'Cuenta en pausa'),
      'POST /api/auth/recover-account': { body: { accessToken: 'jwt-demo', tokenType: 'Bearer', expiresIn: 900, refreshToken: 'refresh-demo', user: demoUser } },
      'GET /api/cart': { body: emptyCart },
      'GET /api/wishlist': { body: page([]) },
    });
    const { path } = renderRoutes({ '/login': <Login />, '/': <p>Inicio</p> }, '/login');

    await userEvent.type(screen.getByLabelText('Usuario o email'), 'demo_player');
    await userEvent.type(screen.getByLabelText('Contraseña'), 'password123');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(await screen.findByText(/Tu cuenta se eliminará el .*2026/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Recuperar mi cuenta' }));

    await waitFor(() => expect(path()).toBe('/'));
    expect(getToken()).toBe('jwt-demo');
    const recover = fetchMock.mock.calls.find(([url]) => String(url) === '/api/auth/recover-account')!;
    expect(JSON.parse(String(recover[1]!.body))).toEqual({ login: 'demo_player', password: 'password123' });
  });

  it('baja del admin no ofrece recuperar', async () => {
    mockFetch({ 'POST /api/auth/login': pending('auth.accountDeletionByAdmin', 'Baja del admin') });
    renderRoutes({ '/login': <Login /> }, '/login');

    await userEvent.type(screen.getByLabelText('Usuario o email'), 'demo_player');
    await userEvent.type(screen.getByLabelText('Contraseña'), 'password123');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(await screen.findByText(/Un administrador ha programado la eliminación de tu cuenta para el .*2026/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Recuperar mi cuenta' })).not.toBeInTheDocument();
    expect(getToken()).toBeNull();
  });
});
