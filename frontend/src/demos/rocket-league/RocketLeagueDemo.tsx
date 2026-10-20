import { useRef } from 'react';
import { t } from '@/i18n';
import { useGameLoop, useDemoKeys } from '../loop';
import type { DemoGameProps } from '../types';
import {
  BALL_RADIUS, CAR_RADIUS, FIELD_H, FIELD_W, GOAL_BOTTOM, GOAL_TOP, MATCH_SECONDS, createMatch, stepMatch,
  type Car, type MatchState,
} from './logic';

const LIME = '#b7ff3c';
const CORAL = '#ff8a7a';

function drawCar(ctx: CanvasRenderingContext2D, car: Car, color: string) {
  ctx.save();
  ctx.translate(car.x, car.y);
  ctx.rotate(car.angle);
  ctx.fillStyle = color;
  ctx.fillRect(-CAR_RADIUS, -CAR_RADIUS * 0.65, CAR_RADIUS * 2, CAR_RADIUS * 1.3);
  ctx.fillStyle = '#080b10';
  ctx.fillRect(CAR_RADIUS * 0.1, -CAR_RADIUS * 0.45, CAR_RADIUS * 0.55, CAR_RADIUS * 0.9); // parabrisas: marca el morro
  ctx.restore();
}

function draw(ctx: CanvasRenderingContext2D, s: MatchState) {
  ctx.fillStyle = '#0c1612';
  ctx.fillRect(0, 0, FIELD_W, FIELD_H);

  ctx.strokeStyle = 'rgba(183,255,60,.22)';
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.moveTo(FIELD_W / 2, 0);
  ctx.lineTo(FIELD_W / 2, FIELD_H);
  ctx.stroke();
  ctx.beginPath();
  ctx.arc(FIELD_W / 2, FIELD_H / 2, 46, 0, Math.PI * 2);
  ctx.stroke();

  // Porterías: la tuya (lima) a la derecha, la rival (coral) a la izquierda
  ctx.fillStyle = 'rgba(255,138,122,.35)';
  ctx.fillRect(0, GOAL_TOP, 8, GOAL_BOTTOM - GOAL_TOP);
  ctx.fillStyle = 'rgba(183,255,60,.35)';
  ctx.fillRect(FIELD_W - 8, GOAL_TOP, 8, GOAL_BOTTOM - GOAL_TOP);

  drawCar(ctx, s.ai, CORAL);
  drawCar(ctx, s.player, LIME);

  ctx.fillStyle = '#f3f5f7';
  ctx.beginPath();
  ctx.arc(s.ball.x, s.ball.y, BALL_RADIUS, 0, Math.PI * 2);
  ctx.fill();
  ctx.strokeStyle = '#8d95a1';
  ctx.lineWidth = 1;
  ctx.stroke();

  // Marcador, tiempo y turbo
  ctx.fillStyle = '#e9edf2';
  ctx.font = 'bold 22px "Space Grotesk", sans-serif';
  ctx.textAlign = 'center';
  ctx.fillText(`${s.score.player}  -  ${s.score.ai}`, FIELD_W / 2, 30);
  ctx.font = '12px "Space Grotesk", sans-serif';
  ctx.fillText(`${Math.max(0, Math.ceil(MATCH_SECONDS - s.elapsed))} s`, FIELD_W / 2, 48);
  if (s.freeze > 0) {
    ctx.font = 'bold 34px "Space Grotesk", sans-serif';
    ctx.fillStyle = LIME;
    ctx.fillText(t('demos.rocket.goal'), FIELD_W / 2, FIELD_H / 2 - 70);
  }
  ctx.textAlign = 'left';
  ctx.fillStyle = '#1a2029';
  ctx.fillRect(12, FIELD_H - 18, 100, 8);
  ctx.fillStyle = LIME;
  ctx.fillRect(12, FIELD_H - 18, s.boost, 8);
}

export default function RocketLeagueDemo({ paused, onFinish }: DemoGameProps) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const state = useRef(createMatch());
  const keys = useDemoKeys();
  const finishRef = useRef(onFinish);
  finishRef.current = onFinish;
  const done = useRef(false);

  useGameLoop(
    (dt) => {
      state.current = stepMatch(state.current, dt, keys.current);
      const s = state.current;
      if (s.finished && !done.current) {
        done.current = true;
        finishRef.current({
          won: s.score.player > s.score.ai,
          messageKey: 'demos.rocket.result',
          values: { player: s.score.player, ai: s.score.ai },
        });
      }
    },
    () => {
      const ctx = canvas.current?.getContext('2d');
      if (ctx) draw(ctx, state.current);
    },
    paused,
  );

  return <canvas ref={canvas} className="demo-canvas" width={FIELD_W} height={FIELD_H} aria-label="Rocket League · Demo" />;
}
