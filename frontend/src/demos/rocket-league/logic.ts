// Lógica pura del partido 2D (sin canvas): estado + step(dt, input).
import type { DemoInput } from '../types';

export const MATCH_SECONDS = 60;
export const FIELD_W = 640;
export const FIELD_H = 360;
export const GOAL_TOP = 120;
export const GOAL_BOTTOM = 240;
export const CAR_RADIUS = 14;
export const BALL_RADIUS = 10;

const KICKOFF_FREEZE = 0.9;
const MAX_SPEED = 210;
const TURBO_SPEED = 330;
const ACCEL = 330;
const TURBO_ACCEL = 620;
const BRAKE = 520;
const REVERSE_SPEED = 90;
const COAST_DRAG = 1.5;
const TURN_RATE = 3;
const BOOST_DRAIN = 40;
const BOOST_REGEN = 14;
const BALL_DRAG = 0.5;
const BALL_BOUNCE = 0.8;
const BALL_MAX = 520;
const AI_SPEED = 125;
const AI_WOBBLE = 0.15;
const AI_DISTRACT_EVERY = 5;
const AI_DISTRACT_FOR = 0.8;

export interface Car { x: number; y: number; angle: number; speed: number }
export interface MatchState {
  score: { player: number; ai: number };
  elapsed: number;
  ball: { x: number; y: number; vx: number; vy: number };
  player: Car;
  ai: Car;
  /** Turbo del jugador, de 0 a 100 */
  boost: number;
  /** Segundos que quedan de parón tras un gol */
  freeze: number;
  finished: boolean;
}

const kickoffPlayer = (): Car => ({ x: 120, y: FIELD_H / 2, angle: 0, speed: 0 });
const kickoffAi = (): Car => ({ x: FIELD_W - 120, y: FIELD_H / 2, angle: Math.PI, speed: 0 });
const kickoffBall = () => ({ x: FIELD_W / 2, y: FIELD_H / 2, vx: 0, vy: 0 });

export function createMatch(): MatchState {
  return {
    score: { player: 0, ai: 0 }, elapsed: 0, ball: kickoffBall(), player: kickoffPlayer(), ai: kickoffAi(),
    boost: 100, freeze: 0, finished: false,
  };
}

const wrap = (a: number) => Math.atan2(Math.sin(a), Math.cos(a));

function drive(car: Car, dt: number, throttle: number, steer: number, turbo: boolean, topSpeed = MAX_SPEED): Car {
  let { speed } = car;
  if (throttle > 0) speed += (turbo ? TURBO_ACCEL : ACCEL) * dt;
  else if (throttle < 0) speed -= (speed > 0 ? BRAKE : ACCEL * 0.6) * dt;
  else speed *= Math.exp(-COAST_DRAG * dt);
  speed = Math.min(turbo ? TURBO_SPEED : topSpeed, Math.max(-REVERSE_SPEED, speed));
  // Sin velocidad el coche apenas gira; al ir marcha atrás gira al revés
  const grip = Math.min(1, Math.abs(speed) / 80) * Math.sign(speed || 1);
  const angle = wrap(car.angle + steer * TURN_RATE * grip * dt);
  let x = car.x + Math.cos(angle) * speed * dt;
  let y = car.y + Math.sin(angle) * speed * dt;
  // Las paredes frenan al coche
  const minX = CAR_RADIUS, maxX = FIELD_W - CAR_RADIUS, minY = CAR_RADIUS, maxY = FIELD_H - CAR_RADIUS;
  if (x < minX || x > maxX || y < minY || y > maxY) {
    x = Math.min(maxX, Math.max(minX, x));
    y = Math.min(maxY, Math.max(minY, y));
    speed *= 0.6;
  }
  return { x, y, angle, speed };
}

/** La IA se coloca detrás del balón (en línea con la portería que ataca) y empuja. */
function aiInput(ai: Car, ball: MatchState['ball'], elapsed: number) {
  const goalX = 0;
  const goalY = FIELD_H / 2;
  const gx = ball.x - goalX;
  const gy = ball.y - goalY;
  const gl = Math.hypot(gx, gy) || 1;
  const behind = { x: ball.x + (gx / gl) * 26, y: ball.y + (gy / gl) * 26 };
  const toBall = Math.hypot(ball.x - ai.x, ball.y - ai.y);
  // Cerca del balón va a por él; si no, a su punto de ataque
  const target = toBall < 60 ? ball : behind;
  // Puntería imperfecta: el rumbo oscila, y cada pocos segundos se despista un momento
  const wobble = toBall < 60 ? 0 : AI_WOBBLE * Math.sin(elapsed * 1.9);
  const diff = wrap(Math.atan2(target.y - ai.y, target.x - ai.x) - ai.angle + wobble);
  const distracted = elapsed % AI_DISTRACT_EVERY < AI_DISTRACT_FOR;
  return { steer: Math.abs(diff) < 0.05 ? 0 : Math.sign(diff), throttle: !distracted && Math.abs(diff) < 1.3 ? 1 : 0 };
}

function carHitsBall(car: Car, ball: MatchState['ball']): { car: Car; ball: MatchState['ball'] } {
  const dx = ball.x - car.x;
  const dy = ball.y - car.y;
  const dist = Math.hypot(dx, dy);
  const reach = CAR_RADIUS + BALL_RADIUS;
  if (dist >= reach || dist === 0) return { car, ball };
  const nx = dx / dist;
  const ny = dy / dist;
  const cvx = Math.cos(car.angle) * car.speed;
  const cvy = Math.sin(car.angle) * car.speed;
  const rel = (cvx - ball.vx) * nx + (cvy - ball.vy) * ny;
  let vx = ball.vx;
  let vy = ball.vy;
  if (rel > 0) {
    vx += nx * rel * 1.5;
    vy += ny * rel * 1.5;
  }
  const speed = Math.hypot(vx, vy);
  if (speed > BALL_MAX) { vx = (vx / speed) * BALL_MAX; vy = (vy / speed) * BALL_MAX; }
  return {
    car: rel > 0 ? { ...car, speed: car.speed * 0.92 } : car,
    ball: { x: car.x + nx * reach, y: car.y + ny * reach, vx, vy },
  };
}

export function stepMatch(state: MatchState, dt: number, input: DemoInput): MatchState {
  if (state.finished) return state;
  const elapsed = state.elapsed + dt;
  const finished = elapsed >= MATCH_SECONDS;

  // Parón tras un gol: nadie se mueve, pero el reloj sigue
  if (state.freeze > 0) return { ...state, elapsed, finished, freeze: Math.max(0, state.freeze - dt) };

  const turbo = input.action && input.up && state.boost > 0;
  const boost = turbo ? Math.max(0, state.boost - BOOST_DRAIN * dt) : Math.min(100, state.boost + BOOST_REGEN * dt);
  let player = drive(state.player, dt, (input.up ? 1 : 0) - (input.down ? 1 : 0), (input.right ? 1 : 0) - (input.left ? 1 : 0), turbo);
  const control = aiInput(state.ai, state.ball, elapsed);
  let ai = drive(state.ai, dt, control.throttle, control.steer, false, AI_SPEED);

  let ball = {
    x: state.ball.x + state.ball.vx * dt,
    y: state.ball.y + state.ball.vy * dt,
    vx: state.ball.vx * Math.exp(-BALL_DRAG * dt),
    vy: state.ball.vy * Math.exp(-BALL_DRAG * dt),
  };
  ({ car: player, ball } = carHitsBall(player, ball));
  ({ car: ai, ball } = carHitsBall(ai, ball));

  // Paredes: rebotan, salvo por la boca de las porterías
  if (ball.y < BALL_RADIUS) { ball.y = BALL_RADIUS; ball.vy = Math.abs(ball.vy) * BALL_BOUNCE; }
  if (ball.y > FIELD_H - BALL_RADIUS) { ball.y = FIELD_H - BALL_RADIUS; ball.vy = -Math.abs(ball.vy) * BALL_BOUNCE; }
  const inMouth = ball.y > GOAL_TOP && ball.y < GOAL_BOTTOM;
  if (!inMouth) {
    if (ball.x < BALL_RADIUS) { ball.x = BALL_RADIUS; ball.vx = Math.abs(ball.vx) * BALL_BOUNCE; }
    if (ball.x > FIELD_W - BALL_RADIUS) { ball.x = FIELD_W - BALL_RADIUS; ball.vx = -Math.abs(ball.vx) * BALL_BOUNCE; }
  }

  const goalForPlayer = inMouth && ball.x > FIELD_W;
  const goalForAi = inMouth && ball.x < 0;
  if (goalForPlayer || goalForAi) {
    return {
      ...state, elapsed, finished, boost,
      score: { player: state.score.player + (goalForPlayer ? 1 : 0), ai: state.score.ai + (goalForAi ? 1 : 0) },
      ball: kickoffBall(), player: kickoffPlayer(), ai: kickoffAi(), freeze: KICKOFF_FREEZE,
    };
  }
  return { ...state, elapsed, finished, boost, ball, player, ai };
}
