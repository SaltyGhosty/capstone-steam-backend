import type { ComponentType } from 'react';
import type { MessageKey } from '@/i18n/messages';

/** Stato della tastiera che legge ogni minigioco (il loop lo consulta a ogni frame). */
export interface DemoInput {
  left: boolean;
  right: boolean;
  up: boolean;
  down: boolean;
  /** Spazio */
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
