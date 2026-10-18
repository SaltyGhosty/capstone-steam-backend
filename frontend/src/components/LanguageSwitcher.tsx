// Banderas para cambiar el idioma de la interfaz. Son SVG porque Windows no pinta los emojis de banderas.
import type { ReactNode } from 'react';
import type { Locale } from '@/api/types';
import { useI18n } from '@/i18n';
import { LOCALES } from '@/lib/locale';

const FLAGS: Record<Locale, ReactNode> = {
  es: (
    <svg viewBox="0 0 3 2" aria-hidden="true">
      <rect width="3" height="2" fill="#c60b1e" />
      <rect y="0.5" width="3" height="1" fill="#ffc400" />
    </svg>
  ),
  it: (
    <svg viewBox="0 0 3 2" aria-hidden="true">
      <rect width="1" height="2" fill="#009246" />
      <rect x="1" width="1" height="2" fill="#fff" />
      <rect x="2" width="1" height="2" fill="#ce2b37" />
    </svg>
  ),
  en: (
    <svg viewBox="0 0 60 30" aria-hidden="true">
      <clipPath id="flag-uk"><path d="M30,15 h30 v15 z v15 h-30 z h-30 v-15 z v-15 h30 z" /></clipPath>
      <path d="M0,0 v30 h60 v-30 z" fill="#012169" />
      <path d="M0,0 L60,30 M60,0 L0,30" stroke="#fff" strokeWidth="6" />
      <path d="M0,0 L60,30 M60,0 L0,30" clipPath="url(#flag-uk)" stroke="#c8102e" strokeWidth="4" />
      <path d="M30,0 v30 M0,15 h60" stroke="#fff" strokeWidth="10" />
      <path d="M30,0 v30 M0,15 h60" stroke="#c8102e" strokeWidth="6" />
    </svg>
  ),
};

export default function LanguageSwitcher() {
  const { lang, setLang, t } = useI18n();
  return (
    <div className="lang-switch" role="group" aria-label={t('lang.label')}>
      {LOCALES.map((l) => (
        <button key={l.value} type="button" className={lang === l.value ? 'active' : ''}
          aria-pressed={lang === l.value} aria-label={l.label} title={l.label} onClick={() => setLang(l.value)}>
          {FLAGS[l.value]}
        </button>
      ))}
    </div>
  );
}
