import { COUNT, STEPS } from './jigsawGeometry';

/*
 Jigsaw Steps Welcome — the autoPlay timeline (LOOP = 10 s), pure functions of t.

   0.0 – 1.4   rest: twelve pieces scattered under the board, tilted up to ±26°.
   1.4 – 2.8   step 1, the corners: four pieces fly home one after another (0.2 s apart, 0.75 s
               each): lifted (scale up to 1.07, a 28 pt arc, the shadow growing and softening),
               ease-in-out across, the tilt unwinding with a 12 % overshoot, a 3 % squash as each
               lands, and a light running round its edges (0.35 s).
   3.2 – 4.6   step 2, the outer middles.          5.0 – 6.4   step 3, the middle row.
   6.4 – 7.2   the cut lines fade out and the photo is whole.
   7.0 – 8.2   the photo comes alive: a slow push-in (Ken Burns, 1 → 1.06) and a warm gleam.
   8.05 – 8.25 the whole photo hands back to the pieces, and 8.2 – 9.6 they scatter back to the
               pile (0.06 s apart, 0.7 s each, a lower arc).
   9.6 – 10    rest. The text above the button rolls to each step as it begins.
 */

export const LOOP = 10;
export const STEP_AT = [1.4, 3.2, 5.0];
export const FLIGHT = 0.75;
export const STAGGER = 0.2;
const SEAMS: [number, number] = [6.4, 7.2];
const BURNS: [number, number] = [7.0, 8.2];
const HANDBACK: [number, number] = [8.05, 8.25];
const SCATTER_AT = 8.2;
const SCATTER_GAP = 0.06;
const SCATTER_LEN = 0.7;
const ARC = 28;

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function smoothstep(a: number, b: number, x: number) {
  'worklet';
  const u = clamp01((x - a) / (b - a));
  return u * u * (3 - 2 * u);
}

export function easeInOutCubic(u: number) {
  'worklet';
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}

export function easeOutBack(u: number, c1: number) {
  'worklet';
  const c3 = c1 + 1;
  return 1 + c3 * Math.pow(u - 1, 3) + c1 * Math.pow(u - 1, 2);
}

/** When piece i starts its flight home: [step, order in step, start time]. */
export function flightOf(i: number): number[] {
  'worklet';
  for (let k = 0; k < STEPS.length; k += 1) {
    const j = STEPS[k].indexOf(i);
    if (j >= 0) return [k, j, STEP_AT[k] + j * STAGGER];
  }
  return [0, 0, 0];
}

/** The order pieces were placed in (0…11), so the last placed leaves first. */
export function placeOrder(i: number) {
  'worklet';
  const f = flightOf(i);
  return f[0] * 4 + f[1];
}

/**
 * Piece i at loop time t: [x, y, rotation, scale, lift 0…1, edge light 0…1, groove 0…1].
 * home / scatter are the piece's [x, y] and [x, y, rot].
 */
export function piecePose(i: number, t: number, home: number[], scatter: number[]): number[] {
  'worklet';
  const f = flightOf(i);
  const start = f[2];
  const u = seg(t, start, start + FLIGHT);
  const back = SCATTER_AT + (COUNT - 1 - placeOrder(i)) * SCATTER_GAP;
  const v = seg(t, back, back + SCATTER_LEN);
  const e = easeInOutCubic(u) * (1 - easeInOutCubic(v));
  const x = scatter[0] + (home[0] - scatter[0]) * e;
  let y = scatter[1] + (home[1] - scatter[1]) * e;
  y -= ARC * Math.sin(Math.PI * u) + ARC * 0.7 * Math.sin(Math.PI * v);
  const rot = scatter[2] * (1 - easeOutBack(u, 1.7)) + scatter[2] * easeInOutCubic(v);
  const lift = Math.sin(Math.PI * u) + Math.sin(Math.PI * v);
  const landed = start + FLIGHT;
  const squash = t > landed ? 0.03 * Math.sin(Math.PI * seg(t, landed, landed + 0.18)) : 0;
  const scale = 1 + 0.07 * lift - squash;
  const flash = t >= landed && t < landed + 0.35 ? 1 - seg(t, landed, landed + 0.35) : 0;
  const groove = u >= 1 && v <= 0 ? 1 - smoothstep(SEAMS[0], SEAMS[1], t) : 0;
  return [x, y, rot, scale, lift, flash, groove];
}

/** The whole photo laid over the finished board: [opacity, Ken Burns scale, gleam position 0…1]. */
export function wholePose(t: number): number[] {
  'worklet';
  const a = smoothstep(SEAMS[0], SEAMS[1], t) * (1 - seg(t, HANDBACK[0], HANDBACK[1]));
  const k = easeInOutCubic(seg(t, BURNS[0], BURNS[1]));
  return [a, 1 + 0.06 * k, seg(t, 7.1, 8.0)];
}

/** The text's roll position: 0 before the first step, k while step k plays, back to 0 at the end. */
export function textRoll(t: number) {
  'worklet';
  let r = 0;
  for (let k = 0; k < STEP_AT.length; k += 1) r += smoothstep(STEP_AT[k], STEP_AT[k] + 0.45, t);
  return r - STEP_AT.length * smoothstep(SCATTER_AT, SCATTER_AT + 0.55, t);
}

/** Which piece flies in overlay slot j (drawn above everything) at t, or -1. */
export function slotPiece(j: number, t: number) {
  'worklet';
  for (let k = 0; k < STEPS.length; k += 1) {
    const a = STEP_AT[k] + j * STAGGER;
    if (t >= a && t < a + FLIGHT + 0.3) return STEPS[k][j];
  }
  return -1;
}
