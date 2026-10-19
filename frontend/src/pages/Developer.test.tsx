import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import type { GameDetail } from '@/api/types';
import { mockFetch } from '@/test/http';
import { renderRoutes } from '@/test/render';
import { DeveloperGameForm } from './Developer';

const game: GameDetail = {
  id: 1,
  title: 'Neon Drift',
  slug: 'neon-drift',
  description: 'Carreras',
  descriptions: { es: 'Carreras', en: null, it: null },
  coverUrl: null,
  steamAppId: null,
  priceSyncedAt: null,
  price: 9.99,
  discountPercent: 0,
  finalPrice: 9.99,
  releaseDate: null,
  status: 'DRAFT',
  averageRating: 0,
  ratingCount: 0,
  salesCount: 0,
  genres: [],
  developer: { id: 7, displayName: 'Maddy' },
  createdAt: '2026-10-01T10:00:00Z',
  updatedAt: '2026-10-01T10:00:00Z',
  version: 3,
};

const COVER = 'https://res.cloudinary.com/demo/image/upload/arcade/covers/game-1.png';

describe('subir portada', () => {
  it('sube el archivo elegido como FormData con el campo file y recarga la portada', async () => {
    const fetchMock = mockFetch({
      'GET /api/developer/games/1': { body: game },
      'GET /api/genres': { body: [] },
      'GET /api/uploads/covers/status': { body: { enabled: true } },
      'POST /api/developer/games/1/cover': { body: { ...game, coverUrl: COVER, version: 4 } },
    });
    renderRoutes({ '/developer/games/:id': <DeveloperGameForm /> }, '/developer/games/1');

    const input = await screen.findByLabelText('Subir portada');
    const file = new File([new Uint8Array([0x89, 0x50, 0x4e, 0x47])], 'portada.png', { type: 'image/png' });
    await userEvent.upload(input, file);

    const isUpload = ([url, init]: unknown[]) =>
      String(url).endsWith('/api/developer/games/1/cover') && (init as RequestInit | undefined)?.method === 'POST';
    await waitFor(() => expect(fetchMock.mock.calls.some(isUpload)).toBe(true));
    const body = fetchMock.mock.calls.find(isUpload)![1]!.body as FormData;
    expect(body).toBeInstanceOf(FormData);
    expect((body.get('file') as File).name).toBe('portada.png');
    await waitFor(() => expect(screen.getByLabelText('URL de la portada')).toHaveValue(COVER));
    expect(screen.getByText(/v4/)).toBeInTheDocument();
  });

  it('un archivo de más de 5 MB muestra el error sin llamar al servidor', async () => {
    const fetchMock = mockFetch({
      'GET /api/developer/games/1': { body: game },
      'GET /api/genres': { body: [] },
      'GET /api/uploads/covers/status': { body: { enabled: true } },
    });
    renderRoutes({ '/developer/games/:id': <DeveloperGameForm /> }, '/developer/games/1');

    const input = await screen.findByLabelText('Subir portada');
    const big = new File([new Uint8Array(6 * 1024 * 1024)], 'grande.png', { type: 'image/png' });
    await userEvent.upload(input, big);

    expect(await screen.findByText('La portada no puede pesar más de 5 MB')).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([, init]) => (init as RequestInit | undefined)?.method === 'POST')).toBe(false);
  });

  it('sin Cloudinary no aparece el botón', async () => {
    mockFetch({
      'GET /api/developer/games/1': { body: game },
      'GET /api/genres': { body: [] },
      'GET /api/uploads/covers/status': { body: { enabled: false } },
    });
    renderRoutes({ '/developer/games/:id': <DeveloperGameForm /> }, '/developer/games/1');

    await screen.findByLabelText('URL de la portada');
    expect(screen.queryByLabelText('Subir portada')).not.toBeInTheDocument();
  });

  it('en un juego nuevo pide guardarlo antes de subir la portada', async () => {
    mockFetch({
      'GET /api/genres': { body: [] },
      'GET /api/uploads/covers/status': { body: { enabled: true } },
    });
    renderRoutes({ '/developer/games/:id': <DeveloperGameForm /> }, '/developer/games/new');

    expect(await screen.findByText('Guarda el juego antes de subir una portada')).toBeInTheDocument();
    expect(screen.queryByLabelText('Subir portada')).not.toBeInTheDocument();
  });
});
