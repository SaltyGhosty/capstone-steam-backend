import type { ReactNode } from 'react';

/** Tarjeta centrada de las pantallas de cuenta (login, registro, verificación de email). */
export default function AuthShell({ eyebrow, title, children, footer }: { eyebrow: string; title: string; children?: ReactNode; footer?: ReactNode }) {
  return (
    <div className="auth">
      <div className="auth-card panel">
        <span className="brand-mark"><span /></span>
        <p className="eyebrow">{eyebrow}</p>
        <h2>{title}</h2>
        {children}
        {footer && <p className="auth-footer">{footer}</p>}
      </div>
    </div>
  );
}
