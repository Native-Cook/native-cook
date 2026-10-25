/*
 Tablecloth Pull Trick — the autoPlay timeline (LOOP = 9 s) as one pose per time.

   0.0 – 1.4   rest: the cloth lies on the table, its hem hanging over the front edge in soft folds,
               swaying a little (whole-loop sines).
   1.4 – 1.75  grab: an anticipation tug (the hem pulled 10 pt, eased back to 2 pt).
   1.75 – 2.3  the yank (0.55 s): the pull runs as u^1.6 until the whole cloth is over the edge; a
               26 pt wave travels through it toward the front, the hanging part whips down and
               toward the viewer, ghost copies and speed streaks trail it. As its back edge passes
               under each dish, that dish hops 8–12 pt (its shadow stays on the table) and then
               jiggles: offsets, spins and glass leans from a damped 5 Hz sine, still 1.4 s later.
   2.6 – 5.0   a "Clean pull" badge; the counter rolls 3 → 4 (2.6 – 2.9).
   5.0 – 7.4   the cloth is thrown back on: it billows down from above the table (from 480 pt,
               ease-in-out, landing at 6.4 s; while it is above the dishes it is drawn over them,
               fading to under them as it lands) with air under its middle, the hem swings down over the
               edge (6.1 – 6.7) with a small overshoot, the air deflates with a settle (to 7.4);
               the dishes ease home (5.2 – 7.0); the counter rolls back (7.4 – 7.7).
   7.4 – 9.0   rest.
 */

export const LOOP = 9;
/** Table depth (world pt), the hem's drop below the edge, and the cloth's whole length. */
export const DEPTH = 320;
export const DROP = 92;
export const CLOTH_LEN = DEPTH + DROP;
/** Pull at which the whole cloth has left the screen; the yank's length (s). */
export const OUT = CLOTH_LEN + 300;
export const YANK = 0.55;
/** Length of a throw-back (s). */
export const THROW_BACK = 2.4;

export type ClothPose = {
  /** Pull (pt), its speed (pt/s), ripple amplitude (pt), stream of the hanging part (0 … 1). */
  d: number;
  speed: number;
  wave: number;
  fling: number;
  /** Throw-back: height above the table, shift toward the back, air under the middle, hem angle (1 = hanging). */
  lift: number;
  back: number;
  air: number;
  hang: number;
  /** Common dish drift toward the viewer (pt); the yank's age (s, < 0 = none), where it started, strength. */
  slide: number;
  yankAge: number;
  yankFrom: number;
  jig: number;
  /** Common glass lean (degrees), counter value (rolls), badge (0 … 1), loop time for idle sways. */
  tip: number;
  count: number;
  badge: number;
  time: number;
};

export function clamp(v: number, a: number, b: number) {
  'worklet';
  return Math.min(Math.max(v, a), b);
}

export function mix(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp((t - a) / (b - a), 0, 1);
}

export function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function hash(i: number) {
  'worklet';
  const s = Math.sin(i * 12.9898 + 78.233) * 43758.5453;
  return s - Math.floor(s);
}

/** A bell 0 → 1 → 0 over [a, b]. */
function bell(t: number, a: number, b: number) {
  'worklet';
  return Math.sin(Math.PI * seg(t, a, b));
}

/** A damped sine after s ≥ 0, windowed to exactly zero at `span`. */
function ring(s: number, decay: number, w: number, span: number) {
  'worklet';
  if (s <= 0 || s >= span) return 0;
  return Math.exp(-decay * s) * Math.sin(w * s) * Math.pow(1 - s / span, 2);
}

/** The pull during a yank that started at `from`, `age` seconds ago: [d, speed]. */
export function yankPull(from: number, age: number) {
  'worklet';
  const u = clamp(age / YANK, 0, 1);
  const d = from + (OUT - from) * Math.pow(u, 1.6);
  const speed = u > 0 && u < 1 ? ((OUT - from) * 1.6 * Math.pow(u, 0.6)) / YANK : 0;
  return [d, speed];
}

/** Seconds into a yank (from `from`) when the cloth's back edge passes under table depth y. */
export function passTime(y: number, from: number) {
  'worklet';
  const need = clamp((DEPTH - y + 24 - from) / (OUT - from), 0, 1);
  return YANK * Math.pow(need, 1 / 1.6);
}

/** The cloth thrown back on, r seconds into THROW_BACK. */
export function throwBack(r: number) {
  'worklet';
  // Falls in, quickest in the middle, cushioned by the air under it at the end.
  const fall = easeInOut(seg(r, 0, 1.4));
  const air = 42 * (0.55 + 0.45 * Math.sin(Math.PI * seg(r, 0, 0.9))) * (1 - seg(r, 1.0, 1.55)) + 10 * ring(r - 1.4, 4, 11, 1.0);
  // In the air the front edge curls down like a canopy; it swings fully down over the table's edge on landing.
  const hang = 0.38 + 0.62 * easeOut(seg(r, 1.1, 1.7)) + 0.1 * ring(r - 1.7, 5, 12, 0.7);
  return { lift: 480 * (1 - fall), back: 150 * (1 - fall), air, hang, wave: 16 * Math.sin(Math.PI * seg(r, 0, 1.4)) };
}

export function restPose(time: number, count: number): ClothPose {
  'worklet';
  return { d: 0, speed: 0, wave: 0, fling: 0, lift: 0, back: 0, air: 0, hang: 1, slide: 0, yankAge: -1, yankFrom: 0, jig: 0, tip: 0, count, badge: 0, time };
}

export function autoPose(t: number): ClothPose {
  'worklet';
  const p = restPose(t, 3);
  if (t >= 1.4 && t < 1.75) p.d = 10 * easeOut(seg(t, 1.4, 1.62)) - 8 * easeInOut(seg(t, 1.62, 1.75));
  else if (t >= 1.75 && t < 5.0) {
    const y = yankPull(2, t - 1.75);
    p.d = y[0];
    p.speed = y[1];
  } else if (t >= 5.0 && t < 5.0 + THROW_BACK) {
    const b = throwBack(t - 5.0);
    p.lift = b.lift;
    p.back = b.back;
    p.air = b.air;
    p.hang = b.hang;
    p.wave = b.wave;
  }
  if (t >= 1.75 && t < 2.6) {
    p.wave = 26 * bell(t, 1.78, 2.45);
    p.fling = 0.85 * bell(t, 1.85, 2.6);
  }
  if (t >= 1.75 && t < 4.0) {
    p.yankAge = t - 1.75;
    p.yankFrom = 2;
    p.jig = 1;
  }
  p.slide = 4 * easeOut(seg(t, 1.8, 2.2)) * (1 - easeInOut(seg(t, 5.2, 7.0)));
  p.count = 3 + easeInOut(seg(t, 2.6, 2.9)) - easeInOut(seg(t, 7.4, 7.7));
  p.badge = easeOut(seg(t, 2.6, 2.9)) * (1 - easeInOut(seg(t, 4.6, 5.0)));
  return p;
}

/** A dish's yank response at table depth y: [dx, dy, spin (rad), lean (deg), hop (pt)]. */
export function jiggle(p: ClothPose, i: number, y: number) {
  'worklet';
  if (p.yankAge < 0 || p.jig <= 0) return [0, 0, 0, 0, 0];
  const a = p.yankAge - passTime(y, p.yankFrom);
  if (a <= 0 || a >= 1.4) return [0, 0, 0, 0, 0];
  const h1 = hash(i + 1);
  const h2 = hash(i + 7);
  const env = Math.exp(-3.2 * a) * Math.pow(1 - a / 1.4, 2) * p.jig;
  const w = 2 * Math.PI * (4.6 + h1 * 1.2) * a;
  const hop = (8 + 4 * h2) * Math.sin(Math.PI * clamp(a / 0.22, 0, 1)) * Math.min(p.jig, 1.4);
  return [2.4 * (h1 - 0.5) * env * Math.sin(w + h2 * 6), -3 * h2 * env * Math.abs(Math.sin(w * 0.5)), 0.12 * (h2 - 0.5) * env * Math.sin(w * 0.8 + 1), 9 * (0.6 + h1 * 0.4) * env * Math.sin(w + h2 * 3), hop];
}
