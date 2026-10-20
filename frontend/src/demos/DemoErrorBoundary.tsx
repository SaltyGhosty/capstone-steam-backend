import { Component, type ReactNode } from 'react';
import { t } from '@/i18n';

/**
 * Si el minijuego no se carga (sin red, chunk viejo tras un despliegue) o falla al dibujar, se avisa
 * dentro del diálogo en vez de dejar la aplicación en blanco.
 */
export class DemoErrorBoundary extends Component<{ onClose: () => void; children: ReactNode }, { failed: boolean }> {
  state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  render() {
    if (!this.state.failed) return this.props.children;
    return (
      <div className="demo-end" role="alert">
        <h3>{t('demos.loadError')}</h3>
        <div className="demo-actions">
          <button type="button" className="secondary" onClick={this.props.onClose} autoFocus>{t('demos.close')}</button>
        </div>
      </div>
    );
  }
}
