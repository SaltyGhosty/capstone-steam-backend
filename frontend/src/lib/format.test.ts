import { describe, expect, it } from 'vitest';
import { formatMoney, formatPlaytime, genreLabel, initials, wideImage } from './format';

// Intl usa espacios no separables en es-ES; se normalizan para comparar
const plain = (s: string) => s.replace(/\s/g, ' ');

describe('format', () => {
  it('formatea precios en euros y muestra "Gratis" para 0', () => {
    expect(plain(formatMoney(19.99))).toBe('19,99 €');
    expect(plain(formatMoney(1234.5))).toBe('1234,50 €');
    expect(formatMoney(0)).toBe('Gratis');
  });

  it('formatea el tiempo jugado', () => {
    expect(formatPlaytime(0)).toBe('Sin jugar todavía');
    expect(formatPlaytime(45)).toBe('45 min jugados');
    expect(formatPlaytime(90)).toBe('1,5 h jugadas');
    expect(formatPlaytime(750)).toBe('13 h jugadas');
  });

  it('pide las portadas de Unsplash y picsum en apaisado y deja el resto igual', () => {
    expect(wideImage('https://images.unsplash.com/photo-1?auto=format&fit=crop&w=900&h=1200&q=80'))
      .toBe('https://images.unsplash.com/photo-1?auto=format&fit=crop&w=1800&h=800&q=80');
    expect(wideImage('https://picsum.photos/seed/neon-drift/600/800')).toBe('https://picsum.photos/seed/neon-drift/1800/800');
    expect(wideImage('https://cdn.example.com/cover.jpg')).toBe('https://cdn.example.com/cover.jpg');
    expect(wideImage(null)).toBe('');
  });

  it('saca iniciales de nombres y usernames', () => {
    expect(initials('Demo Player')).toBe('DP');
    expect(initials('reviewer_ana')).toBe('RA');
    expect(initials('admin')).toBe('A');
  });

  it('une los géneros o indica que no tiene', () => {
    expect(genreLabel([{ name: 'Action' }, { name: 'RPG' }])).toBe('Action · RPG');
    expect(genreLabel([])).toBe('Sin género');
  });
});
