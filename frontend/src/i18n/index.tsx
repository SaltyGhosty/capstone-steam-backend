// Idioma de la interfaz (es/en/it). Se elige con las banderas de la cabecera, también sin sesión,
// y se recuerda en localStorage. El idioma actual vive además en una variable del módulo para que
// los helpers que no son componentes (format.ts, avisos del StoreContext) traduzcan igual.
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import type { Locale } from '@/api/types';
import { detectLocale } from '@/lib/locale';
import { MESSAGES, type MessageKey } from './messages';

type PluralKey = MessageKey extends infer K ? (K extends `${infer B}_one` ? B : never) : never;
export type TranslationKey = MessageKey | PluralKey;
type Vars = Record<string, string | number>;

const LANG_KEY = 'arcade.lang';
const INTL_LOCALE: Record<Locale, string> = { es: 'es-ES', en: 'en-GB', it: 'it-IT' };

let current: Locale = 'es';

export const getLang = () => current;
/** Fija el idioma sin pasar por React (lo usan los tests para volver al español). */
export const setActiveLang = (lang: Locale) => {
  current = lang;
};
export const intlLocale = () => INTL_LOCALE[current];

/** Traduce una clave al idioma actual. Con {count}, elige la variante _one u _other si existe. */
export function t(key: TranslationKey, vars?: Vars): string {
  const dict = MESSAGES[current] as Record<string, string>;
  let text = dict[key];
  if (text === undefined && vars?.count !== undefined) {
    text = dict[`${key}_${Number(vars.count) === 1 ? 'one' : 'other'}`];
  }
  text ??= key;
  return vars ? text.replace(/\{(\w+)\}/g, (match, name: string) => (name in vars ? String(vars[name]) : match)) : text;
}

function readStored(): Locale | null {
  try {
    const value = localStorage.getItem(LANG_KEY);
    return value === 'es' || value === 'en' || value === 'it' ? value : null;
  } catch {
    return null;
  }
}

interface I18nValue {
  lang: Locale;
  setLang: (lang: Locale) => void;
  t: typeof t;
}

const I18nContext = createContext<I18nValue | null>(null);

export function I18nProvider({ children, initial }: { children: ReactNode; initial?: Locale }) {
  const [lang, setLangState] = useState<Locale>(() => initial ?? readStored() ?? detectLocale());
  current = lang;

  useEffect(() => {
    document.documentElement.lang = lang;
  }, [lang]);

  const setLang = useCallback((next: Locale) => {
    current = next;
    setLangState(next);
    try {
      localStorage.setItem(LANG_KEY, next);
    } catch {
      // almacenamiento bloqueado: el idioma dura lo que la pestaña
    }
  }, []);

  // t cambia de identidad con el idioma para que los componentes memorizados se actualicen
  const value = useMemo<I18nValue>(() => ({ lang, setLang, t: (key, vars) => t(key, vars) }), [lang, setLang]);
  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n() {
  const ctx = useContext(I18nContext);
  if (!ctx) throw new Error('useI18n debe usarse dentro de <I18nProvider>');
  return ctx;
}
