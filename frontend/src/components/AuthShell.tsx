import type { ReactNode } from 'react';

/** Card centrata delle schermate dell'account (login, registrazione, verifica email). */
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
