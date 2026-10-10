import { describe, expect, it } from 'vitest';
import { detectLocale } from './locale';

describe('detectLocale', () => {
  it('toma las dos primeras letras del idioma del navegador', () => {
    expect(detectLocale('es-ES')).toBe('es');
    expect(detectLocale('en-US')).toBe('en');
    expect(detectLocale('it')).toBe('it');
    expect(detectLocale('EN-gb')).toBe('en');
  });

  it('usa español si el idioma no está soportado o no hay idioma', () => {
    expect(detectLocale('fr-FR')).toBe('es');
    expect(detectLocale('')).toBe('es');
  });
});
