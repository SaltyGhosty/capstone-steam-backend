// Interfaz del minijuego de cartas: selección, jugada en vivo y fin de partida.
import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { I18nProvider } from '@/i18n';
import BalatroDemo from './BalatroDemo';

const renderGame = (onFinish = vi.fn(), paused = false) =>
  render(<I18nProvider initial="es"><BalatroDemo paused={paused} onFinish={onFinish} /></I18nProvider>);

const cards = () => screen.getAllByRole('button').filter((b) => b.hasAttribute('aria-pressed'));
const stat = (label: string) => screen.getByText(label).parentElement!.querySelector('b')!.textContent;

afterEach(() => vi.useRealTimers());

describe('demo de cartas', () => {
  it('reparte 8 cartas y limita la selección a 5', async () => {
    renderGame();
    expect(cards()).toHaveLength(8);
    for (const card of cards().slice(0, 6)) await userEvent.click(card);
    expect(cards().filter((c) => c.getAttribute('aria-pressed') === 'true')).toHaveLength(5);
  });

  it('muestra la jugada elegida y al jugar gasta una mano y suma puntos', async () => {
    renderGame();
    expect(stat('Manos')).toBe('5');
    await userEvent.click(cards()[0]);
    expect(screen.getByText(/× 1/)).toBeInTheDocument(); // carta alta: fichas × 1
    await userEvent.click(screen.getByRole('button', { name: 'Jugar mano' }));
    expect(stat('Manos')).toBe('4');
    expect(Number(stat('Puntos'))).toBeGreaterThan(0);
    expect(cards()).toHaveLength(8);
  });

  it('descartar gasta un descarte y no una mano; sin descartes el botón se desactiva', async () => {
    renderGame();
    for (let i = 0; i < 2; i++) {
      await userEvent.click(cards()[0]);
      await userEvent.click(screen.getByRole('button', { name: 'Descartar' }));
    }
    expect(stat('Manos')).toBe('5');
    expect(stat('Descartes')).toBe('0');
    await userEvent.click(cards()[0]);
    expect(screen.getByRole('button', { name: 'Descartar' })).toBeDisabled();
  });

  it('al quedarse sin manos termina la partida con el resultado', async () => {
    const onFinish = vi.fn();
    renderGame(onFinish);
    vi.useFakeTimers({ shouldAdvanceTime: true });
    for (let i = 0; i < 5; i++) {
      await userEvent.click(cards()[7]); // la carta más baja
      await userEvent.click(screen.getByRole('button', { name: 'Jugar mano' }));
    }
    expect(onFinish).not.toHaveBeenCalled();
    await act(async () => { await vi.advanceTimersByTimeAsync(1500); });
    expect(onFinish).toHaveBeenCalledTimes(1);
    expect(onFinish.mock.calls[0][0]).toMatchObject({ won: false, messageKey: 'demos.balatro.lost' });
  });

  it('el retardo final respeta la pausa', async () => {
    const onFinish = vi.fn();
    const ui = (paused: boolean) => <I18nProvider initial="es"><BalatroDemo paused={paused} onFinish={onFinish} /></I18nProvider>;
    const { rerender } = render(ui(false));
    vi.useFakeTimers({ shouldAdvanceTime: true });
    for (let i = 0; i < 5; i++) {
      await userEvent.click(cards()[7]);
      await userEvent.click(screen.getByRole('button', { name: 'Jugar mano' }));
    }
    rerender(ui(true));
    await act(async () => { await vi.advanceTimersByTimeAsync(3000); });
    expect(onFinish).not.toHaveBeenCalled();
    rerender(ui(false));
    await act(async () => { await vi.advanceTimersByTimeAsync(1500); });
    expect(onFinish).toHaveBeenCalledTimes(1);
  });

  it('en pausa no se puede jugar', () => {
    renderGame(vi.fn(), true);
    expect(cards().every((c) => (c as HTMLButtonElement).disabled)).toBe(true);
  });
});
