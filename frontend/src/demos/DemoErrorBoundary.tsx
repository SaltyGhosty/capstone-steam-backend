import { Component, type ReactNode } from 'react';
import { t } from '@/i18n';

/**
  * Se il minigioco non si carica (senza rete, chunk vecchio dopo un deploy) o fallisce nel disegno, si avvisa
  * dentro il dialog invece di lasciare l'applicazione bianca.
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
