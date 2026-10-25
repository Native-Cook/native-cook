/**
 * The signature script and the loop's timeline. The stylus position, the knobs and the drawn line
 * are pure functions of the loop time t; the frame at t = LOOP is the frame at 0.
 */

export const LOOP = 10;

// The poster is the finished signature: it rests, is shaken clear, and is drawn again.
export const SHAKE_START = 1.2;
const SWEEP_START = 1.4;
export const SWEEP_END = 2.4;
const SHAKE_END = 2.6;
const DRAW_START = 2.9;
const DRAW_END = 8.6;
/** Points of travel per full turn of a knob. */
export const PT_PER_TURN = 120;

export function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

/** Where the stylus rests, as a share of the screen. */
export const REST = [0.16, 0.24];

/**
 * The signature in screen shares: a stair-stepped "J" (the bar, the stem, the hook) and an
 * underline flourish with a wave. Each entry is [u, v, stairs]: stairs = 1 turns the move into
 * the alternating little steps two knobs make when turned in turns.
 */
const WAYPOINTS: [number, number, number][] = [
  [0.16, 0.24, 0],
  [0.5, 0.24, 0],
  [0.36, 0.24, 0],
  [0.36, 0.6, 0],
  [0.33, 0.68, 1],
  [0.26, 0.72, 1],
  [0.19, 0.69, 1],
  [0.15, 0.62, 1],
  [0.15, 0.8, 0],
  [0.55, 0.8, 0],
  [0.62, 0.67, 1],
  [0.69, 0.8, 1],
  [0.76, 0.67, 1],
  [0.83, 0.8, 1],
  [0.88, 0.8, 0],
]

/** The signature as points in pt (x, y interleaved) inside the screen rect, with their running length. */
export function signature(x: number, y: number, w: number, h: number) {
  const pts: number[] = [x + WAYPOINTS[0][0] * w, y + WAYPOINTS[0][1] * h];
  const step = 5;
  for (let k = 1; k < WAYPOINTS.length; k += 1) {
    const [u, v, stairs] = WAYPOINTS[k];
    const tx = x + u * w;
    const ty = y + v * h;
    if (!stairs) {
      pts.push(tx, ty);
      continue;
    }
    let cx = pts[pts.length - 2];
    let cy = pts[pts.length - 1];
    const n = Math.max(1, Math.round(Math.max(Math.abs(tx - cx), Math.abs(ty - cy)) / step));
    const dx = (tx - cx) / n;
    const dy = (ty - cy) / n;
    for (let i = 0; i < n; i += 1) {
      cx += dx;
      pts.push(cx, cy);
      cy += dy;
      pts.push(cx, cy);
    }
  }
  const run: number[] = [0];
  for (let i = 2; i < pts.length; i += 2) run.push(run[run.length - 1] + Math.hypot(pts[i] - pts[i - 2], pts[i + 1] - pts[i - 1]));
  return { pts, run, total: run[run.length - 1] };
}

export type Etch = {
  /** Length of the line drawn so far (pt), the stylus position. */
  drawn: number;
  sx: number;
  sy: number;
  /** Front of the powder resettling (y, pt; very negative when none) and its glow 0…1. */
  sweep: number;
  band: number;
  /** Shake of the toy (rad). */
  wobble: number;
  /** 0…1: the signature is there (enables the confirm button). */
  signed: number;
  t: number;
};

/** The point at running length d along the signature. */
export function pointAt(pts: number[], run: number[], d: number) {
  'worklet';
  const n = run.length;
  if (d <= 0) return [pts[0], pts[1]];
  if (d >= run[n - 1]) return [pts[2 * n - 2], pts[2 * n - 1]];
  let k = 1;
  while (k < n - 1 && run[k] < d) k += 1;
  const f = (d - run[k - 1]) / Math.max(0.0001, run[k] - run[k - 1]);
  return [pts[2 * k - 2] + (pts[2 * k] - pts[2 * k - 2]) * f, pts[2 * k - 1] + (pts[2 * k + 1] - pts[2 * k - 1]) * f];
}

/** A shake: a quick decaying wobble, `tau` seconds in. */
export function shake(tau: number, len: number) {
  'worklet';
  if (tau <= 0 || tau >= len) return 0;
  return 0.045 * Math.exp((-3.2 * tau) / len) * Math.sin(2 * Math.PI * 5.5 * tau) * Math.sin((Math.PI * tau) / len);
}

/** The whole loop for a screen of top `top` and height `h`. */
export function etchAt(t: number, pts: number[], run: number[], total: number, top: number, h: number): Etch {
  'worklet';
  // The knobs turn with a gentle start and stop; the pen keeps its pace in between.
  const u = seg(t, DRAW_START, DRAW_END);
  const pace = u < 0.08 ? (u * u) / 0.16 : u > 0.92 ? 1 - ((1 - u) * (1 - u)) / 0.16 : u;
  // Whole until the sweep has passed, then nothing until the pen sets off again.
  const drawn = t < SWEEP_END ? total : total * clamp01(pace);
  const at = pointAt(pts, run, drawn);
  // While the powder resettles, the stylus is wound back from the signature's end to its start.
  const home = easeInOut(seg(t, SWEEP_START, SWEEP_END));
  const end = pointAt(pts, run, total);
  const sweeping = t >= SWEEP_START && t < SWEEP_END;
  return {
    drawn: t >= SWEEP_END && t < DRAW_START ? 0 : drawn,
    sx: t < SWEEP_END ? end[0] + (pts[0] - end[0]) * home : at[0],
    sy: t < SWEEP_END ? end[1] + (pts[1] - end[1]) * home : at[1],
    sweep: sweeping ? top - 30 + (h + 60) * home : -1e4,
    band: sweeping ? Math.sin(Math.PI * home) : 0,
    wobble: shake(t - SHAKE_START, SHAKE_END - SHAKE_START),
    signed: 1 - seg(t, SWEEP_START, SWEEP_START + 0.3) + seg(t, DRAW_START + 0.3, DRAW_START + 0.6),
    t,
  };
}
