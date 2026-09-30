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

// Las portadas demo son de picsum en vertical (600x800); para el hero pedimos la misma
// semilla en apaisado. Cualquier otra URL se usa tal cual.
export function wideImage(url: string | null | undefined, width = 1800, height = 800) {
  if (!url) return '';
  const match = url.match(/^(https:\/\/picsum\.photos\/seed\/[^/]+)\/\d+\/\d+/);
  return match ? `${match[1]}/${width}/${height}` : url;
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
