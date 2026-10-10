// Idioma de los correos (bienvenida, recibo, pago rechazado). La interfaz web sigue solo en español.
import type { Locale } from '@/api/types';

export const LOCALES: { value: Locale; label: string }[] = [
  { value: 'es', label: 'Español' },
  { value: 'en', label: 'English' },
  { value: 'it', label: 'Italiano' },
];

const isLocale = (value: string): value is Locale => LOCALES.some((l) => l.value === value);

/** Deduce el idioma de una etiqueta como "en-US" (por defecto, la del navegador). Si no es es/en/it → 'es'. */
export function detectLocale(language: string | undefined = typeof navigator === 'undefined' ? undefined : navigator.language): Locale {
  const code = (language ?? '').slice(0, 2).toLowerCase();
  return isLocale(code) ? code : 'es';
}
