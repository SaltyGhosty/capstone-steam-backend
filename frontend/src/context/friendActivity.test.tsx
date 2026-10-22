import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setToken } from '@/api/client';
import Layout from '@/components/Layout';
import { mockFetch, page } from '@/test/http';
import { demoUser, renderRoutes } from '@/test/render';

interface Activity {
  incomingRequests: { id: number; username: string; displayName: string | null; createdAt: string }[];
  unread: { username: string; displayName: string | null; count: number; lastMessageAt: string }[];
  total: number;
}

const empty: Activity = { incomingRequests: [], unread: [], total: 0 };
const request = (id: number, username: string) => ({ id, username, displayName: username, createdAt: '2026-10-22T10:00:00Z' });
const unread = (username: string, count: number) => ({ username, displayName: username, count, lastMessageAt: '2026-10-22T10:00:00Z' });
const activityOf = (incomingRequests: Activity['incomingRequests'], unreadList: Activity['unread']): Activity => ({
  incomingRequests, unread: unreadList, total: incomingRequests.length + unreadList.reduce((n, u) => n + u.count, 0),
});

/** Monta el Layout con una actividad que el test puede cambiar entre consultas. */
function setup(roles: string[], initial: Activity) {
  const state = { activity: initial };
  const fetchMock = mockFetch({
    'GET /api/users/me': { body: { ...demoUser, roles } },
    'GET /api/cart': { body: { items: [], itemCount: 0, total: 0 } },
    'GET /api/wishlist': { body: page([]) },
    'GET /api/friends/activity': () => ({ body: state.activity }),
  });
  const view = renderRoutes({ '/': <Layout />, '/friends': <p>pantalla amigos</p> }, '/');
  const calls = () => fetchMock.mock.calls.filter(([url]) => String(url).includes('/api/friends/activity')).length;
  return { state, view, calls };
}

// el <output> del router también tiene role=status: el aviso se busca por su clase
const queryToast = () => document.querySelector<HTMLElement>('.toast');
const findToast = () => waitFor(() => { const el = queryToast(); if (!el) throw new Error('sin aviso'); return el; });
const tick = (ms: number) => act(() => vi.advanceTimersByTimeAsync(ms));

describe('actividad de amigos', () => {
  beforeEach(() => {
    setToken('jwt-demo');
    vi.useFakeTimers({ shouldAdvanceTime: true });
  });
  afterEach(() => vi.useRealTimers());

  it('muestra el número de solicitudes más mensajes en Amigos (lateral, móvil y avatar)', async () => {
    setup(['ROLE_USER'], activityOf([request(1, 'ana')], [unread('bob', 2)]));
    const side = await screen.findByRole('navigation', { name: 'Navegación principal' });
    expect(await within(side).findByRole('link', { name: /Amigos\s*3/ })).toBeInTheDocument();
    const mobile = screen.getByRole('navigation', { name: 'Navegación móvil' });
    expect(within(mobile).getByRole('link', { name: /Amigos\s*3/ })).toHaveAttribute('href', '/friends');
    await userEvent.click(screen.getByRole('button', { name: /Demo Player/ }));
    expect(screen.getByRole('menuitem', { name: /Amigos\s*3/ })).toBeInTheDocument();
  });

  it('no pinta el número con 0 y pinta 99+', async () => {
    const { state } = setup(['ROLE_USER'], empty);
    const side = await screen.findByRole('navigation', { name: 'Navegación principal' });
    await tick(0);
    expect(within(side).getByRole('link', { name: 'Amigos' })).toBeInTheDocument();
    expect(within(screen.getByRole('navigation', { name: 'Navegación móvil' })).getByRole('link', { name: /Amigos/ })).toHaveTextContent(/^\s*Amigos$/);
    state.activity = activityOf([], [unread('bob', 150)]);
    await tick(30_000);
    expect(await within(side).findByRole('link', { name: /Amigos\s*99\+/ })).toBeInTheDocument();
  });

  it('la primera consulta no avisa y la siguiente con un mensaje nuevo sí, con enlace a la conversación', async () => {
    const { state } = setup(['ROLE_USER'], activityOf([], [unread('bob', 1)]));
    await screen.findByRole('navigation', { name: 'Navegación principal' });
    await tick(0);
    expect(queryToast()).toBeNull();

    state.activity = activityOf([], [unread('bob', 2)]);
    await tick(30_000);
    const toast = await findToast();
    expect(toast).toHaveTextContent('Nuevo mensaje de bob');
    const link = within(toast).getByRole('link', { name: 'Ver' });
    expect(link).toHaveAttribute('href', '/friends?chat=bob');

    // con acción dura 6 s, no 2,8
    await tick(3000);
    expect(queryToast()).not.toBeNull();
    await tick(3100);
    expect(queryToast()).toBeNull();
  });

  it('una solicitud nueva avisa con enlace a Amigos y varias novedades dan el resumen', async () => {
    const { state } = setup(['ROLE_USER'], empty);
    await screen.findByRole('navigation', { name: 'Navegación principal' });
    await tick(0);
    state.activity = activityOf([request(5, 'carla')], []);
    await tick(30_000);
    expect(await findToast()).toHaveTextContent('carla te ha enviado una solicitud de amistad');
    expect(within(queryToast()!).getByRole('link', { name: 'Ver' })).toHaveAttribute('href', '/friends');

    state.activity = activityOf([request(5, 'carla'), request(6, 'dani')], [unread('bob', 3)]);
    await tick(30_000);
    expect(await screen.findByText('Tienes 1 solicitud y 3 mensajes nuevos')).toBeInTheDocument();
  });

  it('dos consultas iguales no repiten el aviso', async () => {
    const { state } = setup(['ROLE_USER'], empty);
    await screen.findByRole('navigation', { name: 'Navegación principal' });
    await tick(0);
    state.activity = activityOf([], [unread('bob', 1)]);
    await tick(30_000);
    expect(await findToast()).toBeInTheDocument();
    await tick(6500);
    expect(queryToast()).toBeNull();
    await tick(30_000);
    expect(queryToast()).toBeNull();
  });

  it('consulta también al volver a la pestaña', async () => {
    const { calls } = setup(['ROLE_USER'], empty);
    await screen.findByRole('navigation', { name: 'Navegación principal' });
    await tick(0);
    const before = calls();
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'visible' });
    await act(async () => { document.dispatchEvent(new Event('visibilitychange')); });
    expect(calls()).toBe(before + 1);
  });

  it.each([['ROLE_DEVELOPER'], ['ROLE_ADMIN']])('no consulta la actividad para un estudio ni sin sesión (%s)', async (role) => {
    const { calls } = setup(['ROLE_USER', role], empty);
    await screen.findByRole('button', { name: /Demo Player/ });
    await tick(65_000);
    expect(calls()).toBe(0);
  });

  it('sin sesión no consulta la actividad', async () => {
    setToken(null);
    const { calls } = setup(['ROLE_USER'], empty);
    await tick(65_000);
    expect(calls()).toBe(0);
  });
});
