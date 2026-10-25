// Lingue dell'interfaccia e delle email (benvenuto, ricevuta, pagamento rifiutato).
import type { Locale } from '@/api/types';

export const LOCALES: { value: Locale; label: string }[] = [
  { value: 'es', label: 'Español' },
  { value: 'it', label: 'Italiano' },
  { value: 'en', label: 'English' },
];

const isLocale = (value: string): value is Locale => LOCALES.some((l) => l.value === value);

/** Deduce la lingua da un tag come "en-US" (di default, quello del browser). Se non è es/en/it → 'es'. */
export function detectLocale(language: string | undefined = typeof navigator === 'undefined' ? undefined : navigator.language): Locale {
  const code = (language ?? '').slice(0, 2).toLowerCase();
  return isLocale(code) ? code : 'es';
}
