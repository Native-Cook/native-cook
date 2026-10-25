/*
 Slot Reel List — the loop as pure functions of t. A reel's position is an offset in rows (row k of
 its list sits on the payline when the offset is k, mod the list's length); the lever is an angle.

 LOOP = 9 s: rest 0–1.3 · lever pulled 1.3–1.9 (down 0.35 s, released at 1.9, springs back) ·
 spin 1.9–4.8 (reels stop at 3.6, 4.1, 4.6, each landing with a clunk) · win sweep 4.8–5.8 ·
 hold 5.8–7.0 · reels roll on to their resting rows 7.0–8.4 (1 s each, 0.15 s apart) · rest.
*/

export const LOOP = 9;
/** Dishes per reel. */
export const ROWS = 8;
/** The rows on the payline at rest, and the loop's winning combination. */
export const REST_ROWS = [2, 5, 1];
export const WIN_ROWS = [0, 0, 0];
/** Whole turns of the list each reel makes before stopping (later reels spin longer). */
const TURNS = [3, 4, 5];
export const PULL_AT = 1.3;
export const PULL_DOWN = 0.35;
export const SPIN_AT = 1.9;
export const STOPS = [3.6, 4.1, 4.6];
export const SWEEP_AT = 4.8;
export const SWEEP_FOR = 1.0;
export const RETURN_AT = 7.0;
export const RETURN_FOR = 1.0;
export const RETURN_STAGGER = 0.15;
/** The lever's travel: from upright (0) to pointing down past the viewer. */
export const LEVER_MAX = Math.PI * 0.82;

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}

export function easeInOutCubic(v: number) {
  'worklet';
  const x = clamp01(v);
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function easeOutCubic(v: number) {
  'worklet';
  return 1 - Math.pow(1 - clamp01(v), 3);
}

export function hash(n: number) {
  'worklet';
  const v = Math.sin(n * 127.1 + 311.7) * 43758.5453;
  return v - Math.floor(v);
}

/** Positive modulo. */
export function wrap(v: number, n: number) {
  'worklet';
  return v - n * Math.floor(v / n);
}

/*
 One spin: rows travelled after `tau` seconds of a spin of `dur` seconds covering `dist` rows.
 The speed ramps up over the first 15 %, cruises, and falls over the last 40 % to 18 % of its peak,
 so the reel still carries speed when the stop catches it: then it overshoots and settles on a
 damped spring (ω 26 rad/s, ζ 0.3) — the clunk of a reel landing.
*/
const A = 0.15;
const D = 0.4;
const E = 0.18;
const S1 = 1 - A / 2 - ((1 - E) * D) / 2;
const OMEGA = 26;
const ZETA = 0.3;

export function spinRows(tau: number, dur: number, dist: number) {
  'worklet';
  if (tau <= 0) return 0;
  if (tau < dur) {
    const u = tau / dur;
    let s;
    if (u < A) s = (u * u) / (2 * A);
    else if (u < 1 - D) s = A / 2 + (u - A);
    else {
      const w = u - (1 - D);
      s = A / 2 + (1 - D - A) + w - ((1 - E) * w * w) / (2 * D);
    }
    return (dist * s) / S1;
  }
  const ve = (dist * E) / (S1 * dur);
  const k = tau - dur;
  const wd = OMEGA * Math.sqrt(1 - ZETA * ZETA);
  return dist + (ve / wd) * Math.exp(-ZETA * OMEGA * k) * Math.sin(wd * k);
}

/** Rows a reel travels to go from row `from` to row `to`, after `turns` whole turns. */
export function spinDistance(from: number, to: number, turns: number) {
  'worklet';
  return turns * ROWS + wrap(to - from, ROWS);
}

/** Reel c's offset in the loop. */
export function autoOffset(c: number, t: number) {
  'worklet';
  const start = SPIN_AT + 0.06 * c;
  const dist = spinDistance(REST_ROWS[c], WIN_ROWS[c], TURNS[c]);
  let o = REST_ROWS[c] + spinRows(t - start, STOPS[c] - start, dist);
  const back = RETURN_AT + RETURN_STAGGER * c;
  if (t > back) o += wrap(REST_ROWS[c] - WIN_ROWS[c], ROWS) * easeInOutCubic((t - back) / RETURN_FOR);
  return o;
}

/** A released lever: a playful spring back to upright (ζ 0.42), from angle a0. */
export function leverSpring(k: number, a0: number) {
  'worklet';
  const w = 15;
  const z = 0.42;
  const wd = w * Math.sqrt(1 - z * z);
  const env = Math.exp(-z * w * k);
  return a0 * env * (Math.cos(wd * k) + ((z * w) / wd) * Math.sin(wd * k));
}

/** The lever in the loop: pulled down (ease-in-out), let go, and springing back past upright. */
export function autoLever(t: number) {
  'worklet';
  if (t < PULL_AT) return 0;
  if (t < PULL_AT + PULL_DOWN) return LEVER_MAX * easeInOutCubic((t - PULL_AT) / PULL_DOWN);
  if (t < SPIN_AT) return LEVER_MAX;
  return leverSpring(t - SPIN_AT, LEVER_MAX);
}

/** The win: payline glow (up at 4.8, down after 7.0) and the light band's progress (4.8–5.8). */
export function autoWin(t: number) {
  'worklet';
  const glow = easeOutCubic((t - SWEEP_AT) / 0.2) * (1 - easeOutCubic((t - RETURN_AT) / 0.3));
  const sweep = t >= SWEEP_AT && t <= SWEEP_AT + SWEEP_FOR ? easeInOutCubic((t - SWEEP_AT) / SWEEP_FOR) : -1;
  return { glow, sweep };
}
