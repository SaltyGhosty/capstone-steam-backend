import type { ComponentType } from 'react';
import type { MessageKey } from '@/i18n/messages';

/** Estado del teclado que lee cada minijuego (el bucle lo consulta en cada fotograma). */
export interface DemoInput {
  left: boolean;
  right: boolean;
  up: boolean;
  down: boolean;
  /** Espacio */
  action: boolean;
  /** X o Shift */
  secondary: boolean;
}

export interface DemoResult {
  won: boolean;
  messageKey: MessageKey;
  values?: Record<string, string | number>;
}

export interface DemoGameProps {
  paused: boolean;
  onFinish: (result: DemoResult) => void;
}

export interface DemoDefinition {
  slug: 'celeste' | 'vampire-survivors' | 'rocket-league' | 'balatro';
  mechanicKey: MessageKey;
  controlsKey: MessageKey;
  needsKeyboard: boolean;
  load: () => Promise<{ default: ComponentType<DemoGameProps> }>;
}
