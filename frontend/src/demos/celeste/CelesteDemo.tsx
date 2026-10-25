import { useRef } from 'react';
import { useGameLoop, useDemoKeys } from '../loop';
import type { DemoGameProps } from '../types';
import { CELESTE_ROOMS, HEIGHT, PLAYER_H, PLAYER_W, WIDTH, createCeleste, stepCeleste, type CelesteState } from './logic';

const LIME = '#b7ff3c';
const ICE = '#7ec8ff';

function draw(ctx: CanvasRenderingContext2D, s: CelesteState) {
  const room = CELESTE_ROOMS[s.room];
  const bg = ctx.createLinearGradient(0, 0, 0, HEIGHT);
  bg.addColorStop(0, '#13202d');
  bg.addColorStop(1, '#080b10');
  ctx.fillStyle = bg;
  ctx.fillRect(0, 0, WIDTH, HEIGHT);

  // Montagne sullo sfondo, più alte quanto più in alto sei nel percorso
  ctx.fillStyle = '#0e151d';
  ctx.beginPath();
  ctx.moveTo(0, HEIGHT);
  for (let x = 0; x <= WIDTH; x += 80) ctx.lineTo(x, HEIGHT - 70 - ((x * 37 + s.room * 53) % 90));
  ctx.lineTo(WIDTH, HEIGHT);
  ctx.fill();

  for (const r of room.solids) {
    ctx.fillStyle = '#1a2029';
    ctx.fillRect(r.x, r.y, r.w, r.h);
    ctx.fillStyle = LIME;
    ctx.fillRect(r.x, r.y, r.w, 3);
  }

  if (room.goal) {
    const g = room.goal;
    ctx.fillStyle = '#e9edf2';
    ctx.fillRect(g.x, g.y, 2, g.h);
    ctx.fillStyle = LIME;
    ctx.beginPath();
    ctx.moveTo(g.x + 2, g.y);
    ctx.lineTo(g.x + g.w + 6, g.y + 6);
    ctx.lineTo(g.x + 2, g.y + 12);
    ctx.fill();
  }

  // Giocatore: lime con lo scatto pronto, azzurro ghiaccio quando è stato usato
  const p = s.player;
  ctx.fillStyle = p.canDash ? LIME : ICE;
  ctx.fillRect(p.x, p.y, PLAYER_W, PLAYER_H);
  ctx.fillStyle = '#080b10';
  ctx.fillRect(p.x + (p.facing > 0 ? 7 : 2), p.y + 4, 3, 3);
  if (p.dashT > 0) {
    ctx.fillStyle = 'rgba(126,200,255,.35)';
    ctx.fillRect(p.x - p.dashDx * 10, p.y - p.dashDy * 10, PLAYER_W, PLAYER_H);
  }

  ctx.fillStyle = '#e9edf2';
  ctx.font = '12px "Space Grotesk", sans-serif';
  ctx.fillText(`${s.room + 1}/3`, 12, 20);
  ctx.fillText(`${s.elapsed.toFixed(1)} s`, 12, 36);
  ctx.fillText(`× ${s.deaths}`, 12, 52);
}

export default function CelesteDemo({ paused, onFinish }: DemoGameProps) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const state = useRef(createCeleste());
  const keys = useDemoKeys();
  const finishRef = useRef(onFinish);
  finishRef.current = onFinish;
  const done = useRef(false);

  useGameLoop(
    (dt) => {
      state.current = stepCeleste(state.current, dt, keys.current);
      if (state.current.finished && !done.current) {
        done.current = true;
        finishRef.current({
          won: true,
          messageKey: 'demos.celeste.summit',
          values: { time: state.current.elapsed.toFixed(1), deaths: state.current.deaths },
        });
      }
    },
    () => {
      const ctx = canvas.current?.getContext('2d');
      if (ctx) draw(ctx, state.current);
    },
    paused,
  );

  return <canvas ref={canvas} className="demo-canvas" width={WIDTH} height={HEIGHT} aria-label="Celeste · Demo" />;
}
