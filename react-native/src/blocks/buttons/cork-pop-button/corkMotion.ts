/*
 Cork Pop Button — the motion. A frame is a pure function of a few clocks: the cork's state before
 the pop (inch, rock, how loose the cage is), the time since the pop, and the time since the
 bottle swap began. The loop fills them from the timeline; a finger fills them from the
 component's simulation, so both share one drawing.

 autoPlay (LOOP = 9 s):
   0.0 – 1.2   rest: the corked bottle, "Twist the cork up".
   1.2 – 3.2   three twists (rock ±5°, cage key spins 3 turns), each inching the cork up 4.5 pt (ease-out,
               0.25 s); strain shake 2.9 – 3.2.
   3.2         pop: cork and cage fly up with a spin (1700 pt/s, g 1400), a CO2 puff (1.1 s).
   3.2 – 4.6   foam gushes from the lip (metaball shader, 12 balls) and runs down the neck; the order
               card lifts (ease-out back, 0.4 s) with a gold sheen; the line rolls to "Cheers".
   4.4 – 8.7   gold bubbles rise across the screen and pop (each 1.6 – 2.2 s).
   4.6 – 6.2   the foam settles to a small crown.
   6.4 – 8.0   the swap: the open bottle sinks (0.7 s), a corked one rises (0.8 s, ease-out back); the card
               settles, the line rolls back. 8.0 – 9.0 rest; 9.0 is the same frame as 0.0.
*/

export const LOOP = 9;
export const POP_AT = 3.2;
export const SWAP_AT = 6.4;
export const SWAP_FOR = 1.6;
export const BUBBLES = 56;
export const BALLS = 16;
/** Bubbles are gone this long after the pop. */
export const BUBBLES_DONE = 5.7;

export type Geo = {
  w: number;
  h: number;
  cx: number;
  /** Top of the glass lip, the neck's half width there, the neck's length, the shoulder's end. */
  lipY: number;
  neck: number;
  neckLen: number;
  body: number;
  /** The cork's mushroom: half width, height (above the lip). */
  corkW: number;
  corkH: number;
  /** The order card. */
  cardX: number;
  cardY: number;
  cardW: number;
  cardH: number;
};

/** What a frame needs, before and after the pop. -1 for a clock that has not started. */
export type Clocks = {
  inch: number;
  rock: number;
  loose: number;
  shake: number;
  finger: number;
  popE: number;
  swapE: number;
  swapFor: number;
};

export type Pose = {
  /** The bottle on screen (0 = the open one, 1 = the fresh one) and its drop (pt). */
  fresh: number;
  drop: number;
  corked: number;
  inch: number;
  rock: number;
  loose: number;
  finger: number;
  /** Flying cork: x, y, rotation, or empty. */
  cork: number[];
  /** Foam balls x, y, r (12) relative to the screen; and the foam's bounds. */
  balls: number[];
  bounds: number[];
  /** CO2 puff: x, y, r, alpha per wisp. */
  fog: number[];
  /** Bubbles: x, y, r, alpha, ring (0 = whole, >0 = popping). */
  bubbles: number[];
  lift: number;
  sheen: number;
  label: number;
};

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
  const k = 1 - clamp01(x);
  return 1 - k * k * k;
}

export function easeInOutCubic(x: number) {
  'worklet';
  const k = clamp01(x);
  return k < 0.5 ? 4 * k * k * k : 1 - Math.pow(-2 * k + 2, 3) / 2;
}

export function easeOutBack(x: number, c = 1.7) {
  'worklet';
  const k = clamp01(x) - 1;
  return 1 + (c + 1) * k * k * k + c * k * k;
}

export function hash(i: number) {
  'worklet';
  const s = Math.sin(i * 12.9898 + 78.233) * 43758.5453;
  return s - Math.floor(s);
}

/** The twist phase of the loop (1.2 – 3.2). */
export function autoClocks(t: number): Clocks {
  'worklet';
  if (t >= POP_AT) {
    return { inch: 0, rock: 0, loose: 0, shake: 0, finger: 0, popE: t - POP_AT, swapE: t >= SWAP_AT ? t - SWAP_AT : -1, swapFor: SWAP_FOR };
  }
  let inch = 0;
  let rock = 0;
  const starts = [1.35, 1.95, 2.55];
  for (let k = 0; k < 3; k += 1) {
    inch += 4.5 * easeOutCubic(seg(t, starts[k] + 0.3, starts[k] + 0.55));
    // Each twist: a turn one way (anticipation), then the other, while the cork rises.
    const u = seg(t, starts[k], starts[k] + 0.55);
    rock += 5 * Math.sin(Math.PI * 2 * u) * (u > 0 && u < 1 ? 1 : 0);
  }
  const loose = easeInOutCubic(seg(t, 1.3, 2.6));
  const shake = seg(t, 2.9, 3.05) * 0.8 * Math.sin(2 * Math.PI * 41 * t);
  const finger = easeOutCubic(seg(t, 1.1, 1.3)) * (1 - seg(t, 3.12, 3.2));
  return { inch, rock, loose, shake, finger, popE: -1, swapE: -1, swapFor: SWAP_FOR };
}

/** Where foam ball j is e seconds after the pop, relative to the lip's centre (dx, dy, r). */
function foamBall(g: Geo, j: number, e: number, out: number[]) {
  'worklet';
  const settle = 1 - 0.82 * easeInOutCubic(seg(e, 1.4, 3.0));
  if (j === 0) {
    // The crown sitting on the lip.
    out[0] = 0;
    out[1] = -g.neck * 0.25;
    out[2] = g.neck * (0.2 + 0.9 * easeOutBack(seg(e, 0, 0.22))) * (0.55 + 0.45 * settle) * (e < 0 ? 0 : 1);
    return;
  }
  if (j <= 9) {
    // Gushers: thrown up at different speeds so they stack into one column, then slow, fall back
    // and run down the neck's sides.
    const start = 0.025 * j;
    const tau = Math.max(0, e - start);
    const v = 230 + 52 * j + 40 * hash(j + 3);
    const grav = 900;
    const tPeak = v / grav;
    const side = j % 2 === 0 ? 1 : -1;
    const fall = tPeak * 1.75;
    let dy;
    let dx;
    if (tau < fall) {
      dy = -(v * tau - 0.5 * grav * tau * tau);
      dx = side * (4 + 16 * hash(j + 9)) * tau * 2;
    } else {
      // Down the neck, slowing like cream.
      const k = 1 - Math.exp(-(tau - fall) / 0.7);
      const y0 = -(v * fall - 0.5 * grav * fall * fall);
      dy = y0 + k * (g.neckLen * (0.2 + 0.4 * hash(j + 5)) - y0);
      dx = side * (4 + 16 * hash(j + 9)) * fall * 2 * (1 - k) + side * (g.neck + 3) * k;
    }
    out[0] = dx;
    out[1] = dy;
    const grow = easeOutCubic(tau / 0.2);
    const late = j > 6 ? 0.8 : 1;
    out[2] = (e < start ? 0 : 1) * g.neck * (0.52 + 0.3 * hash(j + 1)) * late * (0.45 + 0.55 * grow) * settle;
    return;
  }
  // Drips over the lip, running down both sides.
  const start = 0.3 + 0.1 * (j - 10);
  const tau = e - start;
  const side = j % 2 === 0 ? 1 : -1;
  const k = 1 - Math.exp(-Math.max(0, tau) / 0.9);
  out[0] = side * (g.neck + 2);
  out[1] = -2 + k * g.neckLen * (0.3 + 0.35 * hash(j + 21));
  out[2] = tau < 0 ? 0 : g.neck * (0.3 + 0.12 * hash(j + 2)) * easeOutCubic(tau / 0.3) * settle;
}

export function makePose(c: Clocks, g: Geo): Pose {
  'worklet';
  const H = g.h - g.lipY + g.corkH + 40;
  let fresh = 0;
  let drop = 0;
  let corked = 1;
  if (c.popE >= 0) corked = 0;
  if (c.swapE >= 0) {
    const sink = c.swapFor * 0.44;
    if (c.swapE < sink) {
      drop = H * easeInOutCubic(c.swapE / sink);
    } else {
      fresh = 1;
      corked = 1;
      drop = H * (1 - easeOutBack((c.swapE - sink) / (c.swapFor * 0.5), 1.2));
    }
  }
  const lipX = g.cx;
  const lipY = g.lipY + drop;
  const cork: number[] = [];
  const balls: number[] = [];
  const fog: number[] = [];
  const bubbles: number[] = [];
  const bounds = [0, 0, 0, 0];
  const e = c.popE;
  if (e >= 0 && e < 0.8) {
    const y = g.lipY - g.corkH * 0.5 - 18 - 1700 * e + 0.5 * 1400 * e * e;
    if (y > -80) cork.push(lipX + 90 * e, y, 820 * e);
  }
  if (e >= 0 && fresh === 0) {
    const tmp = [0, 0, 0];
    let x0 = 1e9;
    let y0 = 1e9;
    let x1 = -1e9;
    let y1 = -1e9;
    for (let j = 0; j < BALLS; j += 1) {
      foamBall(g, j, e, tmp);
      const x = lipX + tmp[0];
      const y = lipY + tmp[1];
      balls.push(x, y, tmp[2]);
      if (tmp[2] > 0.5) {
        x0 = Math.min(x0, x - tmp[2] * 2.2);
        y0 = Math.min(y0, y - tmp[2] * 2.2);
        x1 = Math.max(x1, x + tmp[2] * 2.2);
        y1 = Math.max(y1, y + tmp[2] * 2.2);
      }
    }
    if (x1 > x0) {
      bounds[0] = x0;
      bounds[1] = y0;
      bounds[2] = x1 - x0;
      bounds[3] = y1 - y0;
    }
    if (e < 1.1) {
      for (let k = 0; k < 7; k += 1) {
        const u = seg(e, 0.02 * k, 1.1);
        const a = -Math.PI / 2 + (hash(k + 40) - 0.5) * 2.2;
        const d = g.neck * (0.6 + 2.4 * easeOutCubic(u)) * (0.6 + 0.6 * hash(k + 44));
        fog.push(lipX + Math.cos(a) * d * 1.3, lipY - 10 + Math.sin(a) * d - 40 * u, g.neck * (0.5 + 1.5 * easeOutCubic(u)), 0.55 * (1 - u) * seg(e, 0.02 * k, 0.02 * k + 0.06));
      }
    }
  }
  if (e >= 0) {
    for (let b = 0; b < BUBBLES; b += 1) {
      const start = 1.0 + 2.6 * hash(b + 70);
      const life = 1.5 + 0.55 * hash(b + 73);
      const tau = e - start;
      if (tau < 0 || tau > life) continue;
      const u = tau / life;
      const r = 4 + 11 * Math.pow(hash(b + 77), 1.5);
      const spread = (hash(b + 79) - 0.5) * g.w * 1.05;
      const x = lipX + spread * easeOutCubic(u * 1.4) + 9 * Math.sin(2 * Math.PI * (1.2 * tau + hash(b + 81)));
      const y = g.lipY - 30 - (220 + 220 * hash(b + 83)) * tau - 60 * tau * tau;
      const ring = seg(tau, life - 0.14, life);
      bubbles.push(x, y, r * (0.55 + 0.45 * easeOutCubic(tau / 0.25)) * (1 + 0.6 * ring), 1 - ring, ring);
    }
  }
  const sw = c.swapE;
  const lift = (e >= 0 ? easeOutBack(seg(e, 0, 0.4)) : 0) * (1 - (sw >= 0 ? easeInOutCubic(seg(sw, c.swapFor * 0.12, c.swapFor * 0.5)) : 0));
  const label = (e >= 0 ? easeInOutCubic(seg(e, 0.1, 0.45)) : 0) * (1 - (sw >= 0 ? easeInOutCubic(seg(sw, c.swapFor * 0.62, c.swapFor * 0.84)) : 0));
  return {
    fresh,
    drop,
    corked,
    inch: fresh ? 0 : c.inch + c.shake,
    rock: fresh ? 0 : c.rock,
    loose: fresh ? 0 : c.loose,
    finger: fresh ? 0 : c.finger,
    cork,
    balls,
    bounds,
    fog,
    bubbles,
    lift,
    sheen: e >= 0 ? seg(e, 0.1, 1.0) : 0,
    label,
  };
}
