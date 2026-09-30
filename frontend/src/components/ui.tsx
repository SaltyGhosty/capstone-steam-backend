// Piezas pequeñas reutilizadas por las páginas.
import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';
import { ApiError } from '@/api/client';
import type { Role } from '@/api/types';
import { useStore } from '@/context/StoreContext';
import { formatMoney } from '@/lib/format';
import Icon from './Icon';

export function Price({ price, finalPrice, discountPercent }: { price: number; finalPrice: number; discountPercent: number }) {
  if (discountPercent > 0) {
    return (
      <span className="price">
        <span className="price-cut">−{discountPercent}%</span>
        <s>{formatMoney(price)}</s>
        <b>{formatMoney(finalPrice)}</b>
      </span>
    );
  }
  return <span className="price"><b>{formatMoney(finalPrice)}</b></span>;
}

export function Stars({ value, size = 14 }: { value: number; size?: number }) {
  return (
    <span className="stars" aria-label={`${value} de 5`}>
      {[1, 2, 3, 4, 5].map((n) => (
        <span key={n} className={n <= Math.round(value) ? 'on' : ''}><Icon name="star" size={size} /></span>
      ))}
    </span>
  );
}

export function PageHeader({ eyebrow, title, children }: { eyebrow: string; title: string; children?: ReactNode }) {
  return (
    <div className="section-heading page-heading">
      <div><p className="eyebrow">{eyebrow}</p><h2>{title}</h2></div>
      {children}
    </div>
  );
}

export function Loading({ label = 'Cargando…' }: { label?: string }) {
  return <div className="state"><span className="spinner" />{label}</div>;
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const message = error instanceof ApiError ? error.message : 'No se pudo cargar el contenido.';
  return (
    <div className="state error">
      <p>{message}</p>
      {onRetry && <button className="secondary small" onClick={onRetry}>Reintentar</button>}
    </div>
  );
}

export function Empty({ icon, title, children }: { icon: Parameters<typeof Icon>[0]['name']; title: string; children?: ReactNode }) {
  return (
    <div className="empty">
      <div className="promo-icon"><Icon name={icon} size={24} /></div>
      <h3>{title}</h3>
      {children}
    </div>
  );
}

export function Pagination({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (p: number) => void }) {
  if (totalPages <= 1) return null;
  return (
    <div className="pagination">
      <button disabled={page === 0} onClick={() => onChange(page - 1)} aria-label="Página anterior">‹</button>
      <span>{page + 1} / {totalPages}</span>
      <button disabled={page >= totalPages - 1} onClick={() => onChange(page + 1)} aria-label="Página siguiente">›</button>
    </div>
  );
}

/** Protege una ruta: sin sesión → login; sin el rol pedido → inicio. */
export function RequireAuth({ role, children }: { role?: Role; children: ReactNode }) {
  const { user, booting, hasRole } = useStore();
  const location = useLocation();
  if (booting) return <Loading />;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  if (role && !hasRole(role)) return <Navigate to="/" replace />;
  return <>{children}</>;
}

export function FieldError({ errors, name }: { errors: Record<string, string>; name: string }) {
  return errors[name] ? <small className="field-error">{errors[name]}</small> : null;
}
