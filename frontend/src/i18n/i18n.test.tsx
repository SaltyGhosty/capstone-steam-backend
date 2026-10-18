import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import LanguageSwitcher from '@/components/LanguageSwitcher';
import { Login } from '@/pages/Auth';
import { api } from '@/api/client';
import { genreName } from '@/lib/format';
import { mockFetch } from '@/test/http';
import { renderRoutes } from '@/test/render';
import { MESSAGES } from './messages';
import { t } from '.';

describe('i18n', () => {
  it('los tres idiomas tienen las mismas claves', () => {
    const keys = Object.keys(MESSAGES.es).sort();
    expect(Object.keys(MESSAGES.en).sort()).toEqual(keys);
    expect(Object.keys(MESSAGES.it).sort()).toEqual(keys);
  });

  it('sustituye variables y elige singular o plural con count', () => {
    expect(t('toast.addedToCart', { title: 'Hades' })).toBe('Hades añadido al carrito');
    expect(t('common.games', { count: 1 })).toBe('1 juego');
    expect(t('common.games', { count: 3 })).toBe('3 juegos');
  });

  it('las banderas cambian el idioma sin sesión y lo recuerdan', async () => {
    renderRoutes({ '/login': <><LanguageSwitcher /><Login /></> }, '/login');
    expect(screen.getByRole('heading', { name: 'Inicia sesión' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Italiano' }));
    expect(screen.getByRole('heading', { name: 'Accedi' })).toBeInTheDocument();
    expect(screen.getByLabelText('Utente o email')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Italiano' })).toHaveAttribute('aria-pressed', 'true');
    expect(localStorage.getItem('arcade.lang')).toBe('it');

    await userEvent.click(screen.getByRole('button', { name: 'English' }));
    expect(screen.getByRole('heading', { name: 'Log in' })).toBeInTheDocument();
    expect(document.documentElement.lang).toBe('en');
  });

  it('traduce los géneros de serie y deja tal cual los creados por un admin', () => {
    expect(genreName({ name: 'Action', slug: 'action' })).toBe('Acción');
    expect(genreName({ name: 'Metroidvania', slug: 'metroidvania' })).toBe('Metroidvania');
  });

  it('pide a la API las respuestas en el idioma de la interfaz', async () => {
    const fetchMock = mockFetch({ 'GET /api/genres': { body: [] } });
    renderRoutes({ '/': <LanguageSwitcher /> }, '/');
    await userEvent.click(screen.getByRole('button', { name: 'Italiano' }));
    await api('/api/genres');
    expect((fetchMock.mock.calls[0]![1]!.headers as Record<string, string>)['Accept-Language']).toBe('it');
  });
});
