import { useRef } from 'react';
import { useGameLoop, useDemoKeys } from '../loop';
import type { DemoGameProps } from '../types';
import {
  ARENA_H, ARENA_W, ATTACK_RADIUS, ENEMY_RADIUS, PLAYER_RADIUS, SURVIVE_SECONDS, createSurvivors, stepSurvivors,
  type SurvivorsState,
} from './logic';

const LIME = '#b7ff3c';
const CORAL = '#ff8a7a';

function draw(ctx: CanvasRenderingContext2D, s: SurvivorsState) {
  ctx.fillStyle = '#0b0f15';
  ctx.fillRect(0, 0, ARENA_W, ARENA_H);
  // Griglia tenue perché si noti il movimento
  ctx.strokeStyle = '#141b24';
  ctx.lineWidth = 1;
  ctx.beginPath();
  for (let x = 0; x <= ARENA_W; x += 40) { ctx.moveTo(x, 0); ctx.lineTo(x, ARENA_H); }
  for (let y = 0; y <= ARENA_H; y += 40) { ctx.moveTo(0, y); ctx.lineTo(ARENA_W, y); }
  ctx.stroke();

  // Raggio dell'attacco
  ctx.strokeStyle = 'rgba(183,255,60,.14)';
  ctx.beginPath();
  ctx.arc(s.player.x, s.player.y, ATTACK_RADIUS, 0, Math.PI * 2);
  ctx.stroke();

  if (s.flash) {
    ctx.strokeStyle = LIME;
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(s.player.x, s.player.y);
    ctx.lineTo(s.flash.x, s.flash.y);
    ctx.stroke();
    ctx.lineWidth = 1;
  }

  for (const e of s.enemies) {
    ctx.fillStyle = e.fast ? '#ffb347' : CORAL;
    ctx.beginPath();
    ctx.arc(e.x, e.y, ENEMY_RADIUS, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = '#080b10';
    ctx.fillRect(e.x - 4, e.y - 2, 2, 2);
    ctx.fillRect(e.x + 2, e.y - 2, 2, 2);
  }

  // Giocatore: lampeggia mentre è invulnerabile
  if (s.invuln <= 0 || Math.floor(s.elapsed * 12) % 2 === 0) {
    ctx.fillStyle = LIME;
    ctx.beginPath();
    ctx.arc(s.player.x, s.player.y, PLAYER_RADIUS, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = '#080b10';
    ctx.fillRect(s.player.x - 3, s.player.y - 2, 2, 3);
    ctx.fillRect(s.player.x + 1, s.player.y - 2, 2, 3);
  }

  // Barra del tempo, vite e uccisioni
  ctx.fillStyle = '#1a2029';
  ctx.fillRect(0, 0, ARENA_W, 6);
  ctx.fillStyle = LIME;
  ctx.fillRect(0, 0, (ARENA_W * Math.min(s.elapsed, SURVIVE_SECONDS)) / SURVIVE_SECONDS, 6);
  ctx.fillStyle = '#e9edf2';
  ctx.font = '12px "Space Grotesk", sans-serif';
  ctx.fillText(`${Math.max(0, Math.ceil(SURVIVE_SECONDS - s.elapsed))} s`, 12, 24);
  ctx.fillText(`✕ ${s.kills}`, 12, 40);
  for (let i = 0; i < s.hp; i++) {
    ctx.fillStyle = CORAL;
    ctx.fillRect(ARENA_W - 20 - i * 16, 14, 11, 11);
  }
}

export default function VampireSurvivorsDemo({ paused, onFinish }: DemoGameProps) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const state = useRef<SurvivorsState>(createSurvivors(Math.floor(Math.random() * 2 ** 31)));
  const keys = useDemoKeys();
  const finishRef = useRef(onFinish);
  finishRef.current = onFinish;
  const done = useRef(false);

  useGameLoop(
    (dt) => {
      state.current = stepSurvivors(state.current, dt, keys.current);
      const s = state.current;
      if (s.finished && !done.current) {
        done.current = true;
        finishRef.current(s.won
          ? { won: true, messageKey: 'demos.vampire.survived', values: { kills: s.kills } }
          : { won: false, messageKey: 'demos.vampire.defeated', values: { time: Math.floor(s.elapsed) } });
      }
    },
    () => {
      const ctx = canvas.current?.getContext('2d');
      if (ctx) draw(ctx, state.current);
    },
    paused,
  );

  return <canvas ref={canvas} className="demo-canvas" width={ARENA_W} height={ARENA_H} aria-label="Vampire Survivors · Demo" />;
}
