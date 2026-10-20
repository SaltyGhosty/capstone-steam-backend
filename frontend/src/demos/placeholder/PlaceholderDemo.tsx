import type { DemoGameProps } from '../types';

/** Provisional: se sustituye por cada minijuego real. */
export default function PlaceholderDemo({ paused, onFinish }: DemoGameProps) {
  return (
    <div data-paused={String(paused)}>
      <button type="button" className="primary" onClick={() => onFinish({ won: true, messageKey: 'demos.finished' })}>OK</button>
    </div>
  );
}
