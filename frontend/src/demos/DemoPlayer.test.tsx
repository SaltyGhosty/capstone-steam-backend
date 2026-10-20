// Reproductor de demos: pausa, teclado, pantalla final y aviso en móvil.
import { act, fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { setToken } from '@/api/client';
import type { GameDetail } from '@/api/types';
import { mockFetch, page } from '@/test/http';
import { demoUser, renderRoutes } from '@/test/render';
import { DemoPlayer } from './DemoPlayer';
import { MAX_DT, clampDt } from './loop';

// Registro de prueba: un «juego» que solo expone si está en pausa y puede terminar la partida
vi.mock('./registry', async () => {
  const { createElement } = await import('react');
  const Stub = ({ paused, onFinish }: { paused: boolean; onFinish: (r: { won: boolean; messageKey: string }) => void }) =>
    createElement('div', { 'data-testid': 'stub', 'data-paused': String(paused) },
      createElement('canvas'),
      createElement('button', { onClick: () => onFinish({ won: true, messageKey: 'demos.finished' }) }, 'terminar-stub'));
  return {
    DEMOS: [],
    demoFor: () => ({
      slug: 'celeste', mechanicKey: 'demos.celeste.mechanic', controlsKey: 'demos.celeste.controls', needsKeyboard: true,
      load: async () => ({ default: Stub }),
    }),
  };
});

const game = {
  id: 5, title: 'Celeste', slug: 'celeste', description: '', descriptions: { es: '', en: null, it: null },
  coverUrl: null, steamAppId: null, priceSyncedAt: null, price: 19.99, discountPercent: 0, finalPrice: 19.99,
  releaseDate: '2018-01-25', status: 'PUBLISHED', reviewCount: 0, positivePercent: 0, reviewScore: 'NONE',
  salesCount: 0, genres: [], developer: { id: 2, displayName: 'Matt' },
  createdAt: '2026-01-01T10:00:00Z', updatedAt: '2026-01-01T10:00:00Z', version: 0,
} as unknown as GameDetail;

function setup(extra = {}, loggedIn = false) {
  if (loggedIn) setToken('jwt-demo');
  return mockFetch({
    'GET /api/users/me': { body: demoUser },
    'GET /api/wishlist': { body: page([]) },
    'GET /api/cart': { body: { items: [], itemCount: 0, total: 0 } },
    ...extra,
  });
}

const open = (onClose = vi.fn()) => renderRoutes({ '/': <DemoPlayer game={game} onClose={onClose} /> }, '/');

afterEach(() => {
  document.body.style.overflow = '';
});

describe('clampDt', () => {
  it('limita los saltos grandes de tiempo y respeta los normales', () => {
    expect(clampDt(5)).toBe(MAX_DT);
    expect(clampDt(0.016)).toBe(0.016);
    expect(clampDt(-1)).toBe(0);
  });
});

describe('DemoPlayer', () => {
  it('renderiza el diálogo con título «Celeste · Demo» y lo cierra con Esc', async () => {
    setup();
    const onClose = vi.fn();
    open(onClose);
    expect(await screen.findByRole('dialog', { name: 'Celeste · Demo' })).toBeInTheDocument();
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(onClose).toHaveBeenCalled();
  });

  it('pausa el juego cuando la ventana pierde el foco y sigue pausado al volver', async () => {
    setup();
    open();
    const stub = await screen.findByTestId('stub');
    expect(stub).toHaveAttribute('data-paused', 'false');
    act(() => { fireEvent.blur(window); });
    expect(screen.getByTestId('stub')).toHaveAttribute('data-paused', 'true');
    act(() => { fireEvent.focus(window); });
    expect(screen.getByTestId('stub')).toHaveAttribute('data-paused', 'true');
    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));
    expect(screen.getByTestId('stub')).toHaveAttribute('data-paused', 'false');
  });

  it('las flechas y el Espacio no hacen scroll', async () => {
    setup();
    open();
    const dialog = await screen.findByRole('dialog');
    expect(fireEvent.keyDown(dialog, { key: ' ' })).toBe(false);
    expect(fireEvent.keyDown(dialog, { key: 'ArrowDown' })).toBe(false);
  });

  it('las teclas de juego no llegan a los atajos globales, pero Ctrl K no se toca', async () => {
    setup();
    open();
    const dialog = await screen.findByRole('dialog');
    const global = vi.fn();
    window.addEventListener('keydown', global);
    fireEvent.keyDown(dialog, { key: 'ArrowLeft' });
    expect(global).not.toHaveBeenCalled();
    fireEvent.keyDown(dialog, { key: 'k', ctrlKey: true });
    expect(global).toHaveBeenCalledTimes(1);
    window.removeEventListener('keydown', global);
  });

  it('pantalla final con Añadir al carrito', async () => {
    const fetchMock = setup({ 'PUT /api/cart/items/5': { body: { items: [], itemCount: 1, total: 0 } } }, true);
    open();
    await userEvent.click(await screen.findByRole('button', { name: 'terminar-stub' }));
    expect(screen.getByText('¡Demo completada!')).toBeInTheDocument();
    await userEvent.click(await screen.findByRole('button', { name: /Añadir al carrito/ }));
    await waitFor(() => expect(fetchMock.mock.calls.some(([u, i]) => String(u) === '/api/cart/items/5' && i?.method === 'PUT')).toBe(true));
  });

  it('pantalla final sin Añadir al carrito si ya lo tienes', async () => {
    setup({ 'GET /api/library/status/5': { body: { gameId: 5, owned: true, inCart: false, inWishlist: false, reviewed: false } } }, true);
    open();
    await userEvent.click(await screen.findByRole('button', { name: 'terminar-stub' }));
    const link = await screen.findByRole('link', { name: /Ya lo tienes/ });
    expect(link).toHaveAttribute('href', '/library');
    expect(screen.queryByRole('button', { name: /Añadir al carrito/ })).not.toBeInTheDocument();
  });

  it('en móvil una demo de teclado muestra el aviso', async () => {
    vi.stubGlobal('matchMedia', (query: string) => ({
      matches: query.includes('pointer: coarse'), media: query, addEventListener() {}, removeEventListener() {},
    }));
    setup();
    open();
    expect(await screen.findByText('Demo disponible en ordenador: necesita teclado')).toBeInTheDocument();
    expect(document.querySelector('canvas')).toBeNull();
  });
});
