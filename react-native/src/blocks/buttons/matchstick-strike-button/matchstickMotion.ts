/*
 Matchstick Strike Button — timing and the match's physics. Every frame of the self-playing loop
 is a pure function of the loop time t; under a finger a small simulation (Sim) is stepped on the
 UI thread and read through the same Pose.

 LOOP = 8 s:
   0.0 – 1.3   rest (the poster: a fresh match lying above the box, "Strike to start" printed in it)
   1.3 – 1.7   the match lifts and moves to the right end of the striker (it pulls back 8 pt at 1.62)
   1.7 – 2.2   strike: the head drags right → left across the strip, sparks fly back and fall
   2.2 – 2.6   flare: a fireball of 1.8× size, a sulphur puff, then a steady teardrop flame
   2.6 – 2.9   the match swings down under the CTA      2.9 – 4.4  it glides under the letters,
               which ignite left → right as the flame passes (white flash, then a gold glow)
   4.4 – 5.8   hold: the flame flickers, the whole screen warms
   5.8 – 6.15  a gust: the flame leans hard and goes out; smoke curls up from 5.95
   6.0 – 6.9   the letters cool back to print       6.8 – 7.2  the burnt match goes back to rest
   7.2 – 7.6   it drops behind the box                7.6 – 8.0  a fresh one rises from the box
*/

export const LOOP = 8;
export const IDLE_BEFORE_BACK = 2;
export const BACK_FOR = 0.9;
/** A swipe on the strip lights the match above this speed (pt/s) once it has run this far (pt). */
export const STRIKE_SPEED = 650;
export const STRIKE_RUN = 56;
export const MAX_SPARKS = 32;

export type Pt = { x: number; y: number };

/** Layout the motion needs, in screen points. */
export type Geo = {
  rest: Pt;
  strikeFrom: Pt;
  strikeTo: Pt;
  glideFrom: Pt;
  glideTo: Pt;
  strikerTop: number;
  strikerBottom: number;
  strikerLeft: number;
  strikerRight: number;
  boxTop: number;
  /** Letter centres (x) and the CTA's baseline. */
  letters: number[];
  baseline: number;
  matchLength: number;
};

export type Match = {
  x: number;
  y: number;
  /** Direction from the head to the tail, degrees (0 = tail to the right). */
  angle: number;
  /** 0 lying on the table … 1 held up (bigger, softer shadow). */
  lift: number;
  /** 0 fresh red head … 1 charred head. */
  burnt: number;
  /** How far the stick has charred below the head, 0 … 1. */
  char: number;
  alpha: number;
  /** 1 = drawn over the box, 0 = behind it. */
  front: number;
};

export type Pose = {
  match: Match;
  /** A fresh match fading in at rest (only while easing back after a touch). */
  spare: number;
  /** Flame: size 0 … 1, flare 0 … 1, lean -1 … 1. */
  lit: number;
  flare: number;
  lean: number;
  /** Warm light over the scene. */
  light: number;
  /** Live sparks, 6 numbers each: x, y, vx, vy, age, life. */
  sparks: number[];
  /** Smoke: x, y, age, strength (the sulphur puff, then the blown-out curl). */
  puff: number[];
  curl: number[];
  /** Heat per letter: 0 printed, 1 glowing gold, above 1 the white flash of catching. */
  heat: number[];
  /** A scrape on the strip: x from, x to, darkness. */
  scrape: number[];
  /** Flicker phases (radians). */
  phase: number;
};

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeOut(x: number) {
  'worklet';
  const u = 1 - clamp01(x);
  return 1 - u * u * u;
}

export function easeInOut(x: number) {
  'worklet';
  const v = clamp01(x);
  return v < 0.5 ? 4 * v * v * v : 1 - Math.pow(-2 * v + 2, 3) / 2;
}

export function mix(a: number, b: number, k: number) {
  'worklet';
  return a + (b - a) * k;
}

export function hash(n: number) {
  'worklet';
  const s = Math.sin(n * 12.9898 + 4.1414) * 43758.5453;
  return s - Math.floor(s);
}

/** Where the head is during the loop (no lean or flame, just the path). */
export function headAt(t: number, g: Geo): Pt & { angle: number; lift: number } {
  'worklet';
  if (t < 1.3) return { x: g.rest.x, y: g.rest.y, angle: 0, lift: 0 };
  if (t < 1.7) {
    // Lift to the strip's right end, with a short pull-back before the strike (anticipation).
    const k = easeInOut(seg(t, 1.3, 1.62));
    const back = Math.sin(Math.PI * seg(t, 1.56, 1.7)) * 8;
    return {
      x: mix(g.rest.x, g.strikeFrom.x, k) + back,
      y: mix(g.rest.y, g.strikeFrom.y, k) - Math.sin(Math.PI * k) * 26,
      angle: mix(0, -22, k),
      lift: k,
    };
  }
  if (t < 2.2) {
    const k = easeInOut(seg(t, 1.7, 2.2));
    return { x: mix(g.strikeFrom.x, g.strikeTo.x, k), y: g.strikeFrom.y + Math.sin(Math.PI * k) * 1.5, angle: -22, lift: 1 };
  }
  if (t < 2.6) {
    // The flare pulls the match up off the strip.
    const k = easeOut(seg(t, 2.2, 2.6));
    return { x: g.strikeTo.x, y: g.strikeTo.y - 14 * k, angle: -22 - 6 * k, lift: 1 };
  }
  if (t < 2.9) {
    const k = easeInOut(seg(t, 2.6, 2.9));
    return { x: mix(g.strikeTo.x, g.glideFrom.x, k), y: mix(g.strikeTo.y - 14, g.glideFrom.y, k), angle: mix(-28, 28, k), lift: 1 };
  }
  if (t < 6.8) {
    const k = easeInOut(seg(t, 2.9, 4.4));
    return { x: mix(g.glideFrom.x, g.glideTo.x, k), y: g.glideFrom.y - Math.sin(Math.PI * k) * 4, angle: 28, lift: 1 };
  }
  if (t < 7.2) {
    const k = easeInOut(seg(t, 6.8, 7.2));
    return { x: mix(g.glideTo.x, g.rest.x, k), y: mix(g.glideTo.y, g.rest.y, k) - Math.sin(Math.PI * k) * 30, angle: mix(28, 0, k), lift: 1 - k };
  }
  if (t < 7.6) {
    const k = easeInOut(seg(t, 7.2, 7.6));
    return { x: g.rest.x, y: g.rest.y + 74 * k, angle: 0, lift: 0 };
  }
  const k = easeOut(seg(t, 7.6, 8.0));
  return { x: g.rest.x, y: g.rest.y + 74 * (1 - k), angle: 0, lift: 0 };
}

/** When each letter catches in the loop: the head passes under its centre. */
export function igniteTimes(g: Geo): number[] {
  const out: number[] = [];
  for (const x of g.letters) {
    let lo = 2.9;
    let hi = 4.4;
    for (let i = 0; i < 24; i += 1) {
      const m = (lo + hi) / 2;
      if (headAt(m, g).x < x - 4) lo = m;
      else hi = m;
    }
    out.push(hi);
  }
  return out;
}

/** A letter's heat from the moment it caught (and, when cooling, the moment it started to). */
export function heatOf(age: number, coolAge: number) {
  'worklet';
  if (age < 0) return 0;
  const h = easeOut(age / 0.22) + Math.exp(-age * 7) * 0.6 * clamp01(age / 0.04);
  return coolAge < 0 ? h : h * (1 - easeInOut(coolAge / 0.8));
}

const SPARKS_AUTO = 26;

/** The sparks the loop's strike throws: each a pure function of t and its index. */
function loopSparks(t: number, g: Geo, out: number[]) {
  'worklet';
  if (t < 1.7 || t > 3.0) return;
  for (let i = 0; i < SPARKS_AUTO; i += 1) {
    const born = 1.78 + (0.4 * i) / SPARKS_AUTO;
    const life = 0.28 + 0.3 * hash(i * 3.1);
    const age = t - born;
    if (age < 0 || age > life) continue;
    const at = headAt(born, g);
    // Thrown back against the stroke (to the right) and up, then falling.
    const ang = (-150 + 120 * hash(i + 7.7)) * (Math.PI / 180);
    const sp = 140 + 300 * hash(i * 1.9 + 2.2);
    out.push(at.x - 6, at.y, -Math.cos(ang) * sp, Math.sin(ang) * sp, age, life);
  }
}

/** The pose of the self-playing loop at loop time t. */
export function loopPose(t: number, g: Geo, ignite: number[]): Pose {
  'worklet';
  const h = headAt(t, g);
  // Lean against the motion: a finite difference of the path (it is smooth).
  const h2 = headAt(Math.max(0, t - 0.04), g);
  let lean = Math.max(-1, Math.min(1, -(h.x - h2.x) / 0.04 / 520));
  const flare = t > 2.12 && t < 2.8 ? Math.exp(-Math.pow((t - 2.26) / 0.13, 2)) : 0;
  let lit = easeOut(seg(t, 2.14, 2.5));
  const out = seg(t, 5.8, 6.15);
  lit *= 1 - out * out;
  lean = lean * (1 - out) - out * 0.9;
  if (t >= 6.15) lit = 0;
  const heat: number[] = [];
  for (let i = 0; i < ignite.length; i += 1) heat.push(heatOf(t - ignite[i], t - (6.0 + 0.03 * i)));
  const sparks: number[] = [];
  loopSparks(t, g, sparks);
  const burnt = t < 7.6 ? easeOut(seg(t, 2.2, 2.7)) : 0;
  const char = t < 7.6 ? seg(t, 2.6, 6.0) : 0;
  return {
    match: { x: h.x, y: h.y, angle: h.angle, lift: h.lift, burnt, char, alpha: 1, front: t >= 7.2 && t < 8 ? 0 : 1 },
    spare: 0,
    lit,
    flare,
    lean,
    light: lit * 0.85 + flare * 0.9,
    sparks,
    puff: [g.strikeTo.x - 2, g.strikeTo.y - 6, t - 2.2, t > 2.2 ? 1 - seg(t, 2.6, 3.8) : 0],
    curl: [g.glideTo.x, g.glideTo.y - 8, t - 5.95, t > 5.95 ? 1 - seg(t, 7.0, 7.8) : 0],
    heat,
    scrape: [g.strikeTo.x, g.strikeFrom.x, t > 1.75 && t < 7.6 ? 0.45 * seg(t, 1.75, 2.2) * (1 - seg(t, 6.4, 7.6)) : 0],
    phase: (2 * Math.PI * t) / LOOP,
  };
}

/** The finger's simulation, mutated in place on the UI thread. */
export type Sim = {
  x: number;
  y: number;
  angle: number;
  lift: number;
  burnt: number;
  litAt: number;
  outAt: number;
  lean: number;
  vx: number;
  run: number;
  sparks: number[];
  scrape: number[];
  ignite: number[];
  cool: number;
  backAt: number;
  back: Match | null;
  phase: number;
  /** Where the match caught (the sulphur puff rises from there). */
  px: number;
  py: number;
};

export function makeSim(letters: number): Sim {
  'worklet';
  const ignite: number[] = [];
  for (let i = 0; i < letters; i += 1) ignite.push(-1);
  return { x: 0, y: 0, angle: 0, lift: 0, burnt: 0, litAt: -1, outAt: -1, lean: 0, vx: 0, run: 0, sparks: [], scrape: [0, 0, -99], ignite, cool: -1, backAt: -1, back: null, phase: 0, px: 0, py: 0 };
}

/** The pose under (or after) a finger at clock time `now`. */
export function simPose(s: Sim, now: number, g: Geo): Pose {
  'worklet';
  let lit = 0;
  let flare = 0;
  if (s.litAt >= 0) {
    const a = now - s.litAt;
    lit = easeOut(a / 0.36);
    flare = Math.exp(-Math.pow((a - 0.12) / 0.13, 2));
  }
  let lean = s.lean;
  if (s.outAt >= 0) {
    const o = seg(now, s.outAt, s.outAt + 0.3);
    lit *= 1 - o * o;
    lean = lean * (1 - o) - 0.9 * o;
    flare = 0;
  }
  const heat: number[] = [];
  for (let i = 0; i < s.ignite.length; i += 1) heat.push(s.ignite[i] < 0 ? 0 : heatOf(now - s.ignite[i], s.cool < 0 ? -1 : now - s.cool));
  const sparks: number[] = [];
  for (let i = 0; i + 5 < s.sparks.length; i += 6) {
    const age = now - s.sparks[i + 4];
    if (age >= 0 && age < s.sparks[i + 5]) sparks.push(s.sparks[i], s.sparks[i + 1], s.sparks[i + 2], s.sparks[i + 3], age, s.sparks[i + 5]);
  }
  let match: Match = { x: s.x, y: s.y, angle: s.angle, lift: s.lift, burnt: s.burnt, char: s.litAt >= 0 ? clamp01((now - s.litAt) / 3.4) : 0, alpha: 1, front: 1 };
  let spare = 0;
  if (s.backAt >= 0 && s.back) {
    // Easing back: the used match drifts toward rest and fades, a fresh one fades in there.
    const k = seg(now, s.backAt, s.backAt + BACK_FOR);
    const m = easeInOut(seg(now, s.backAt + 0.2, s.backAt + 0.7));
    match = { ...s.back, x: mix(s.back.x, g.rest.x, m), y: mix(s.back.y, g.rest.y, m), angle: mix(s.back.angle, 0, m), lift: s.back.lift * (1 - m), alpha: 1 - m };
    spare = easeInOut(seg(k, 0.5, 1));
  }
  const sa = s.litAt >= 0 ? now - s.litAt : -1;
  const oa = s.outAt >= 0 ? now - s.outAt : -1;
  return {
    match,
    spare,
    lit,
    flare,
    lean,
    light: lit * 0.85 + flare * 0.9,
    sparks,
    puff: [s.px, s.py, sa, sa >= 0 ? 1 - seg(sa, 0.4, 1.6) : 0],
    curl: [s.back ? s.back.x : match.x, (s.back ? s.back.y : match.y) - 8, oa, oa >= 0 ? 1 - seg(oa, 0.9, 1.6) : 0],
    heat,
    scrape: [s.scrape[0], s.scrape[1], s.scrape[2] < -50 ? 0 : 0.55 * (1 - seg(now, s.scrape[2] + 0.5, s.scrape[2] + 3))],
    phase: s.phase,
  };
}
