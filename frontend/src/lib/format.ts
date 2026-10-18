import { intlLocale, t } from '@/i18n';

// Los formateadores de Intl se crean una vez por idioma
const cache = new Map<string, { money: Intl.NumberFormat; date: Intl.DateTimeFormat; dateTime: Intl.DateTimeFormat }>();
function formatters() {
  const locale = intlLocale();
  let f = cache.get(locale);
  if (!f) {
    f = {
      money: new Intl.NumberFormat(locale, { style: 'currency', currency: 'EUR' }),
      date: new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'short', year: 'numeric' }),
      dateTime: new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short' }),
    };
    cache.set(locale, f);
  }
  return f;
}

export const formatMoney = (value: number) => (value === 0 ? t('common.free') : formatters().money.format(value));
export const formatDate = (iso: string | null | undefined) => (iso ? formatters().date.format(new Date(iso)) : '—');
export const formatDateTime = (iso: string | null | undefined) => (iso ? formatters().dateTime.format(new Date(iso)) : '—');

export function formatPlaytime(minutes: number) {
  if (minutes === 0) return t('format.notPlayed');
  if (minutes < 60) return t('format.minPlayed', { n: minutes });
  const hours = minutes / 60;
  const shown = hours < 10 ? new Intl.NumberFormat(intlLocale(), { maximumFractionDigits: 1, minimumFractionDigits: 1 }).format(hours) : Math.round(hours);
  return t('format.hoursPlayed', { n: shown });
}

export function initials(name: string) {
  return name
    .split(/[\s_.-]+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]!.toUpperCase())
    .join('');
}

// Las portadas se guardan en vertical. Para el hero y las ofertas se pide la versión apaisada:
// en Steam, la imagen "library_hero" del mismo juego; en Unsplash y picsum, un recorte por URL.
// Cualquier otra URL se usa tal cual y la recorta el CSS (background-size: cover).
export function wideImage(url: string | null | undefined, width = 1800, height = 800) {
  if (!url) return '';
  const steam = url.match(/^(https:\/\/[^/]*steamstatic\.com\/steam\/apps\/\d+)\/library_600x900(_2x)?\.jpg/);
  if (steam) return `${steam[1]}/library_hero.jpg`;
  if (url.startsWith('https://images.unsplash.com/')) {
    const wide = new URL(url);
    wide.searchParams.set('w', String(width));
    wide.searchParams.set('h', String(height));
    wide.searchParams.set('fit', 'crop');
    return wide.toString();
  }
  const picsum = url.match(/^(https:\/\/picsum\.photos\/seed\/[^/]+)\/\d+\/\d+/);
  return picsum ? `${picsum[1]}/${width}/${height}` : url;
}

// Tarjetas y líneas del carrito son apaisadas (≈1,6:1): en Steam hay una cápsula con ese formato.
export function cardImage(url: string | null | undefined) {
  if (!url) return '';
  const steam = url.match(/^(https:\/\/[^/]*steamstatic\.com\/steam\/apps\/\d+)\/library_600x900(_2x)?\.jpg/);
  return steam ? `${steam[1]}/capsule_616x353.jpg` : url;
}

/** Los géneros de serie se traducen por su slug; los que cree un admin se muestran con su nombre. */
export function genreName(genre: { name: string; slug?: string }) {
  const key = `genre.${genre.slug}`;
  const translated = t(key as Parameters<typeof t>[0]);
  return genre.slug && translated !== key ? translated : genre.name;
}

export const genreLabel = (genres: { name: string; slug?: string }[]) =>
  genres.map(genreName).join(' · ') || t('common.noGenre');

export const orderStatus = (status: string) => t(`status.${status}` as Parameters<typeof t>[0]);
export const gameStatus = (status: string) => t(`gameStatus.${status}` as Parameters<typeof t>[0]);
