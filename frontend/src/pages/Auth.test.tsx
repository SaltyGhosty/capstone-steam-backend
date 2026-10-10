import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { getToken } from '@/api/client';
import { mockFetch, page } from '@/test/http';
import { demoUser, renderRoutes } from '@/test/render';
import { Login, Register } from './Auth';

const emptyCart = { items: [], itemCount: 0, total: 0 };

describe('login y registro', () => {
  it('inicia sesión, guarda el token y vuelve a la página de la que venía', async () => {
    const fetchMock = mockFetch({
      'POST /api/auth/login': { body: { accessToken: 'jwt-demo', tokenType: 'Bearer', expiresIn: 7200, user: demoUser } },
      'GET /api/cart': { body: emptyCart },
      'GET /api/wishlist': { body: page([]) },
    });
    const { path } = renderRoutes({ '/login': <Login />, '/': <p>Inicio</p> }, '/login');

    await userEvent.type(screen.getByLabelText('Usuario o email'), 'demo_player');
    await userEvent.type(screen.getByLabelText('Contraseña'), 'password123');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    await waitFor(() => expect(path()).toBe('/'));
    expect(getToken()).toBe('jwt-demo');
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
    // el idioma de los correos se deduce del navegador (jsdom: en-US), sin campo visible
    expect(JSON.parse(String(fetchMock.mock.calls[0]![1]!.body)).locale).toBe('en');
  });
});
