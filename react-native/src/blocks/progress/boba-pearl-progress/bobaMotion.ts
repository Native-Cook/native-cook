/*
 Boba Pearl Progress — layout, the pearls' packing and the order's timeline. A cup's content is a
 pure function of its content time `tc` (pearls dropped, tea level, foam, seal, straw); the loop
 adds the shake and the cup swap. Every frame is a function of the loop time t.

 autoPlay timeline (LOOP = 10 s):
   0.0 – 1.4   rest: an empty clear cup, "Brewing" at 0 %.
   1.4 – 2.8   40 tapioca pearls drop in, bottom row first, three scoops; each falls under gravity
               and bounces once (damped |sin|, 90 ms decay) into its packed place.
   2.6 – 5.0   milk tea pours in (ease-in-out cubic), surface waving; brown-sugar streaks climb.
   5.0 – 6.4   "Shaking": the cup rocks ±4° about its base (4 swings, sine envelope), the tea
               sloshes 70 ms behind and overshoots, pearls jiggle; foam rises on top.
   6.4 – 7.0   "Sealing": the film drops onto the rim (ease-out) and is pressed (squash 8 %).
   7.05 – 7.45 the straw drops in and punches the film.
   7.0 – 8.2   "Ready" at 100 %.
   8.2 – 9.0   the cup slides out to the left (ease-in-out cubic); 8.85 – 9.6 an empty cup slides in
               from the right with a little overshoot; the label returns to "Brewing", 0 %.
   9.6 – 10.0  rest.
 */

export const LOOP = 10;
export const PEARLS = 40;
const G = 2200;

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeOutCubic(x: number) {
  'worklet';
  const u = 1 - x;
  return 1 - u * u * u;
}

export function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

/** Ease-out with a small overshoot (about 4 %). */
export function easeOutBack(x: number) {
  'worklet';
  const c1 = 1.1;
  const c3 = c1 + 1;
  const u = x - 1;
  return 1 + c3 * u * u * u + c1 * u * u;
}

function hash(i: number) {
  const s = Math.sin(i * 12.9898 + 4.1) * 43758.5453;
  return s - Math.floor(s);
}

export type Geo = {
  w: number;
  h: number;
  u: number;
  cx: number;
  /** Cup: rim y, base y, half widths at rim and base, rim ellipse radius. */
  top: number;
  base: number;
  rimW: number;
  baseW: number;
  rimRy: number;
  baseRy: number;
  cupH: number;
  maxLevel: number;
  pearlR: number;
  percentY: number;
  stepsY: number;
  buttonY: number;
};

export function makeGeo(w: number, h: number, top: number, bottom: number): Geo {
  const u = Math.min(w / 393, (h - top - bottom) / 759);
  const cupTop = top + 104 + 86 * u;
  const cupH = 300 * u;
  return {
    w,
    h,
    u,
    cx: w / 2,
    top: cupTop,
    base: cupTop + cupH,
    rimW: 96 * u,
    baseW: 70 * u,
    rimRy: 13 * u,
    baseRy: 9 * u,
    cupH,
    maxLevel: cupH * 0.8,
    pearlR: 8.4 * u,
    percentY: cupTop + cupH + 36 * u,
    stepsY: cupTop + cupH + 104 * u,
    buttonY: h - bottom - 16 - 52,
  };
}

/** Half width of the cup's inside at height y above the base. */
export function halfWidth(g: Geo, y: number) {
  'worklet';
  return g.baseW + ((g.rimW - g.baseW) * y) / g.cupH - 3 * g.u;
}

/** A pearl's resting place (cup frame: x from the axis, y up from the base), size and layer. */
export type Pearl = { x: number; y: number; r: number; back: number; drop: number; fromX: number };

/**
 * Packs the pearls in rows from the base up: a back layer (smaller, higher, darker) and a front
 * layer, with seeded jitter. Then orders the drops bottom first, in three scoops.
 */
export function packPearls(g: Geo): Pearl[] {
  const out: Pearl[] = [];
  for (const back of [1, 0]) {
    const r = g.pearlR * (back ? 0.86 : 1);
    const want = back ? 18 : 22;
    let row = 0;
    let placed = 0;
    while (placed < want && row < 8) {
      const y = r + row * r * 1.72 + (back ? 3 * g.u : 0);
      const half = halfWidth(g, y) - r;
      const count = Math.max(1, Math.floor((half * 2) / (r * 2.02)) + 1);
      const step = count > 1 ? (half * 2) / (count - 1) : 0;
      const shift = (row % 2 === 1 ? step * 0.5 : 0) + (back ? step * 0.25 : 0);
      for (let i = 0; i < count && placed < want; i += 1) {
        const k = out.length;
        let x = -half + i * step + shift;
        if (x > half) x -= step;
        x += (hash(k) - 0.5) * r * 0.35;
        out.push({ x, y: y + (hash(k + 50) - 0.5) * r * 0.25, r: r * (0.94 + hash(k + 90) * 0.12), back, drop: 0, fromX: 0 });
        placed += 1;
      }
      row += 1;
    }
  }
  const order = out.map((_, i) => i).sort((a, b) => out[a].y - out[b].y);
  order.forEach((idx, rank) => {
    const scoop = Math.floor((rank * 3) / out.length);
    out[idx].drop = 1.4 + scoop * 0.42 + (rank % Math.ceil(out.length / 3)) * 0.022;
    out[idx].fromX = (hash(idx + 7) - 0.5) * 34 * g.u;
  });
  return out;
}

/** Height above the base and x of pearl p at content time tc (−1 height: not dropped yet). */
export function pearlAt(g: Geo, p: Pearl, tc: number): [number, number] {
  'worklet';
  if (tc < p.drop) return [p.fromX, -1];
  const startY = g.cupH + 46 * g.u;
  const fall = startY - p.y;
  const tFall = Math.sqrt((2 * fall) / G);
  const tau = tc - p.drop;
  if (tau < tFall) {
    const k = tau / tFall;
    return [p.fromX + (p.x - p.fromX) * easeOutCubic(k), startY - 0.5 * G * tau * tau];
  }
  const after = tau - tFall;
  const bounce = 9 * g.u * Math.exp(-after / 0.09) * Math.abs(Math.sin(after * 24));
  return [p.x, p.y + bounce];
}

export function level(g: Geo, tc: number) {
  'worklet';
  return g.maxLevel * easeInOutCubic(seg(tc, 2.6, 5.0));
}

/** The pour: [top of the stream reached (0 … 1), tail fallen (0 … 1)]. */
export function pour(tc: number): [number, number] {
  'worklet';
  return [seg(tc, 2.5, 2.68), seg(tc, 4.82, 5.02)];
}

export function foam(tc: number) {
  'worklet';
  return easeOutCubic(seg(tc, 5.3, 6.3));
}

/** The seal: [drop (0 above … 1 on the rim), squash (0 … 1 … 0)]. */
export function seal(tc: number): [number, number] {
  'worklet';
  return [easeOutCubic(seg(tc, 6.4, 6.75)), Math.sin(Math.PI * seg(tc, 6.72, 7.0))];
}

/** The straw: [how far it has come down (0 … 1), the film's pop ring (0 … 1)]. */
export function straw(tc: number): [number, number] {
  'worklet';
  return [easeOutBack(seg(tc, 7.05, 7.45)), seg(tc, 7.38, 7.75)];
}

export function percent(tc: number) {
  'worklet';
  return (
    18 * seg(tc, 1.4, 2.8) + 42 * seg(tc, 2.8, 5.0) + 22 * seg(tc, 5.0, 6.4) + 13 * seg(tc, 6.4, 7.0) + 5 * seg(tc, 7.0, 7.2)
  );
}

export const STEPS = ['Brewing', 'Shaking', 'Sealing', 'Ready'];
export const STATUS = ['Brewing your tea', 'Shaking it up', 'Sealing your cup', 'Ready for pickup'];
export const ETA = ['About 2 min', 'About 1 min', 'Almost there', 'Counter 2'];

export function stepOf(tc: number) {
  'worklet';
  return tc < 5.0 ? 0 : tc < 6.4 ? 1 : tc < 7.0 ? 2 : 3;
}

/** Cup rotation (radians) of a shake that started `s` seconds ago (1.4 s long). */
export function shakeRot(s: number) {
  'worklet';
  if (s <= 0 || s >= 1.4) return 0;
  const env = Math.pow(Math.sin((Math.PI * s) / 1.4), 0.6);
  return ((4 * Math.PI) / 180) * Math.sin((2 * Math.PI * 4 * s) / 1.4) * env;
}

export function shakeEnv(s: number) {
  'worklet';
  if (s <= 0 || s >= 1.4) return 0;
  return Math.sin((Math.PI * s) / 1.4);
}

/** One cup to draw. */
export type Cup = {
  dx: number;
  rot: number;
  tc: number;
  /** Surface tilt in the cup's frame, pearl roll (pt), shake envelope and phase. */
  tilt: number;
  roll: number;
  shake: number;
  phase: number;
};

export type Scene = {
  cups: Cup[];
  percent: number;
  /** Step shown, the previous one and the cross-fade between them. */
  step: number;
  prevStep: number;
  stepFade: number;
  /** Progress value cross-fade during the swap: [value before, value after, fade]. */
  pct: [number, number, number];
  /** The ambient time for waves and the stream. */
  clock: number;
};

export function loopScene(g: Geo, t: number, ambient: number): Scene {
  'worklet';
  const cups: Cup[] = [];
  const s = t - 5.0;
  const rot = shakeRot(s);
  const lagRot = shakeRot(s - 0.07);
  if (t < 9.0) {
    const tc = Math.min(t, 8.2);
    cups.push({
      dx: -g.w * 0.8 * easeInOutCubic(seg(t, 8.2, 9.0)),
      rot,
      tc,
      tilt: -rot + 1.5 * lagRot,
      roll: 0,
      shake: shakeEnv(s),
      phase: s,
    });
  }
  if (t > 8.85) cups.push({ dx: g.w * 0.8 * (1 - easeOutBack(seg(t, 8.85, 9.6))), rot: 0, tc: 0, tilt: 0, roll: 0, shake: 0, phase: 0 });
  const swapped = t >= 8.9;
  const tcLabel = swapped ? 0 : Math.min(t, 8.2);
  const step = stepOf(tcLabel);
  const changes = [5.0, 6.4, 7.0, 8.9];
  let last = -10;
  for (const c of changes) if (t >= c) last = c;
  const prevStep = last === 8.9 ? 3 : last === 5.0 ? 0 : last === 6.4 ? 1 : last === 7.0 ? 2 : 0;
  return {
    cups,
    percent: percent(tcLabel),
    step,
    prevStep,
    stepFade: last < 0 ? 1 : clamp01((t - last) / 0.3),
    pct: [100, 0, easeInOutCubic(seg(t, 8.8, 9.1))],
    clock: ambient,
  };
}
