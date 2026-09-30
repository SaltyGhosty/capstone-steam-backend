const money = new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR' });
const date = new Intl.DateTimeFormat('es-ES', { day: 'numeric', month: 'short', year: 'numeric' });
const dateTime = new Intl.DateTimeFormat('es-ES', { dateStyle: 'medium', timeStyle: 'short' });

export const formatMoney = (value: number) => (value === 0 ? 'Gratis' : money.format(value));
export const formatDate = (iso: string | null | undefined) => (iso ? date.format(new Date(iso)) : '—');
export const formatDateTime = (iso: string | null | undefined) => (iso ? dateTime.format(new Date(iso)) : '—');

export function formatPlaytime(minutes: number) {
  if (minutes === 0) return 'Sin jugar todavía';
  if (minutes < 60) return `${minutes} min jugados`;
  const hours = minutes / 60;
  return `${hours < 10 ? hours.toFixed(1).replace('.', ',') : Math.round(hours)} h jugadas`;
}

export function initials(name: string) {
  return name
    .split(/[\s_.-]+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]!.toUpperCase())
    .join('');
}

// Las portadas se guardan en vertical. Para el hero y las ofertas se pide la versión apaisada
// cuando el proveedor recorta por URL (Unsplash con w/h/fit=crop, picsum con /ancho/alto);
// cualquier otra URL se usa tal cual y la recorta el CSS (background-size: cover).
export function wideImage(url: string | null | undefined, width = 1800, height = 800) {
  if (!url) return '';
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

export const genreLabel = (genres: { name: string }[]) =>
  genres.map((g) => g.name).join(' · ') || 'Sin género';

export const ORDER_STATUS: Record<string, string> = {
  PENDING: 'Pendiente',
  PAID: 'Pagado',
  FAILED: 'Rechazado',
  CANCELLED: 'Cancelado',
};

export const GAME_STATUS: Record<string, string> = {
  DRAFT: 'Borrador',
  PUBLISHED: 'Publicado',
  DELISTED: 'Retirado',
};
