import type { DemoDefinition } from './types';

/** Giochi del catalogo che hanno una demo, per slug. Ogni minigioco si carica quando si apre. */
export const DEMOS: DemoDefinition[] = [
  {
    slug: 'celeste',
    mechanicKey: 'demos.celeste.mechanic',
    controlsKey: 'demos.celeste.controls',
    needsKeyboard: true,
    load: () => import('./celeste/CelesteDemo'),
  },
  {
    slug: 'vampire-survivors',
    mechanicKey: 'demos.vampire.mechanic',
    controlsKey: 'demos.vampire.controls',
    needsKeyboard: true,
    load: () => import('./vampire-survivors/VampireSurvivorsDemo'),
  },
  {
    slug: 'rocket-league',
    mechanicKey: 'demos.rocket.mechanic',
    controlsKey: 'demos.rocket.controls',
    needsKeyboard: true,
    load: () => import('./rocket-league/RocketLeagueDemo'),
  },
  {
    slug: 'balatro',
    mechanicKey: 'demos.balatro.mechanic',
    controlsKey: 'demos.balatro.controls',
    needsKeyboard: false,
    load: () => import('./balatro/BalatroDemo'),
  },
];

export function demoFor(slug: string): DemoDefinition | undefined {
  return DEMOS.find((d) => d.slug === slug);
}
