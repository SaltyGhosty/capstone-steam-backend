// Lingua dell'interfaccia (es/en/it). Si sceglie con le bandiere dell'header, anche senza sessione,
// e viene ricordata in localStorage. La lingua corrente vive anche in una variabile del modulo perché
// gli helper che non sono componenti (format.ts, avvisi dello StoreContext) traducano allo stesso modo.
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
/** Imposta la lingua senza passare da React (la usano i test per tornare allo spagnolo). */
export const setActiveLang = (lang: Locale) => {
  current = lang;
};
export const intlLocale = () => INTL_LOCALE[current];

/** Traduce una chiave nella lingua corrente. Con {count}, sceglie la variante _one o _other se esiste. */
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
      // storage bloccato: la lingua dura quanto la scheda
    }
  }, []);

  // t cambia identità con la lingua così i componenti memoizzati si aggiornano
  const value = useMemo<I18nValue>(() => ({ lang, setLang, t: (key, vars) => t(key, vars) }), [lang, setLang]);
  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n() {
  const ctx = useContext(I18nContext);
  if (!ctx) throw new Error('useI18n debe usarse dentro de <I18nProvider>');
  return ctx;
}
