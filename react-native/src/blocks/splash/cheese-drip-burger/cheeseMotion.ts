/*
 Timing and the cheese model of Cheese Drip Burger, ported from CheeseDripBurgerMotion.swift. Every
 value is a pure function of the loop time `t` (0 ..< LOOP) and, for touch, of the clock; nothing
 accumulates per frame. Helpers are declared above their callers (worklets), and the touch state is
 a plain object replaced by pure functions.
 */

export const LOOP = 8;

export function clamp(v: number, a: number, b: number) {
  'worklet';
  return Math.min(Math.max(v, a), b);
}

export function clamp01(v: number) {
  'worklet';
  return Math.min(Math.max(v, 0), 1);
}

export function mod(a: number, b: number) {
  'worklet';
  const r = a % b;
  return r < 0 ? r + b : r;
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeOutCubic(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

export function easeInCubic(x: number) {
  'worklet';
  return x * x * x;
}

export function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function lerp(a: number, b: number, f: number) {
  'worklet';
  return a + (b - a) * f;
}

export function hash(i: number) {
  'worklet';
  const s = Math.sin(i * 12.9898 + 78.233) * 43758.5453;
  return s - Math.floor(s);
}

// Cheese layout (burger space: points inside the 372 pt image square, origin top-left).

export const IMAGE_SIZE = 372;

export type Drip = { u: number; w: number; rest: number; peak: number };
export type Layer = { cx: number; half: number; y: number; slope: number; sag: number; band: number; drips: Drip[] };

/** Index 0: the lower slice (between the patties); 1: the upper slice (under the lettuce). */
export const LAYERS: Layer[] = [
  {
    cx: 173, half: 132, y: 261, slope: 0.06, sag: 1.5, band: 9,
    drips: [
      { u: -0.98, w: 20, rest: 16, peak: 44 },
      { u: -0.66, w: 30, rest: 22, peak: 80 },
      { u: -0.36, w: 22, rest: 10, peak: 24 },
      { u: -0.06, w: 34, rest: 26, peak: 70 },
      { u: 0.26, w: 24, rest: 14, peak: 56 },
      { u: 0.52, w: 30, rest: 20, peak: 80 },
      { u: 0.76, w: 20, rest: 10, peak: 24 },
      { u: 0.98, w: 20, rest: 15, peak: 50 },
    ],
  },
  {
    cx: 196, half: 114, y: 218, slope: -0.026, sag: 1.5, band: 8,
    drips: [
      { u: -0.97, w: 18, rest: 12, peak: 40 },
      { u: -0.6, w: 28, rest: 18, peak: 56 },
      { u: -0.25, w: 20, rest: 9, peak: 34 },
      { u: 0.08, w: 30, rest: 20, peak: 58 },
      { u: 0.42, w: 22, rest: 11, peak: 40 },
      { u: 0.72, w: 26, rest: 16, peak: 54 },
      { u: 0.98, w: 18, rest: 12, peak: 38 },
    ],
  },
];

/** The slice's front edge at x: a line measured on the photo, with a slight bow. */
export function edge(layer: Layer, x: number) {
  'worklet';
  const u = (x - layer.cx) / layer.half;
  return layer.y + layer.slope * (x - layer.cx) + layer.sag * (1 - u * u);
}

export const PUDDLE_Y = 358;
export const PUDDLE_CX = 187;

/** Melt of one drip (0 rest … 1 peak): builds 1.6 → 4.3 s, holds, sets back 5.0 → 7.0 s. */
export function meltLoop(t: number, delay: number) {
  'worklet';
  const up = easeInOutCubic(seg(t, 1.6 + delay, 4.3 + delay * 0.5));
  const down = easeInOutCubic(seg(t, 5.0 + delay * 0.6, 7.0));
  return up * (1 - down);
}

export function dripDelay(layer: number, drip: number) {
  'worklet';
  return hash(layer * 17 + drip + 3) * 0.35;
}

export const ROWS = 3;

export function rowLitLoop(t: number, i: number) {
  'worklet';
  const up = easeOutCubic(seg(t, 1.2 + 0.09 * i, 1.75 + 0.09 * i));
  const down = easeInOutCubic(seg(t, 3.5 + 0.07 * i, 4.3 + 0.07 * i));
  return up * (1 - down);
}

export function rowSlide(t: number, i: number) {
  'worklet';
  const up = easeOutCubic(seg(t, 1.2 + 0.09 * i, 1.75 + 0.09 * i));
  return (1 - up) * 70 * (i % 2 === 0 ? -1 : 1);
}

export type DropEvent = { layer: number; drip: number; t0: number };

export const LOOP_EVENTS: DropEvent[] = [
  { layer: 0, drip: 2, t0: 4.15 },
  { layer: 0, drip: 6, t0: 4.6 },
];

export const PINCH_LEAD = 0.6;
export const EVENT_TAIL = 1.4;
export const GRAVITY = 1100;
export const FALL_TIME = 0.42;
export const FALL_FROM = -560;

/** Burger offset (pt) and scale (x, y) at τ s after a tap: [y, sx, sy]. */
export function dropIn(tau: number): number[] {
  'worklet';
  if (tau < 0 || tau > 2.5) return [0, 1, 1];
  if (tau < FALL_TIME) {
    const f = tau / FALL_TIME;
    return [FALL_FROM * (1 - f * f), 1 - 0.04 * f, 1 + 0.05 * f];
  }
  const s = Math.exp(-6.9 * (tau - FALL_TIME)) * Math.cos(10.5 * (tau - FALL_TIME));
  return [0, 1 + 0.09 * s, 1 - 0.12 * s];
}

export function jiggle(tau: number) {
  'worklet';
  const d = tau - FALL_TIME;
  if (d < 0 || d > 2.0) return 0;
  return 16 * Math.exp(-4.5 * d) * Math.sin(13 * d);
}

export type DripShape = { x: number; y0: number; w: number; length: number; r: number; neck: number };
export type DropShape = { x: number; y: number; rx: number; ry: number };
export type Bump = { layer: number; x: number; amp: number }; // layer 2 = the puddle
export type ActiveEvent = { layer: number; drip: number; dt: number };
export type CheeseFrame = { drips: DripShape[][]; drops: DropShape[]; bumps: Bump[]; melt: number };

export function dripLength(d: Drip, m: number, ambient: number, seed: number) {
  'worklet';
  const k = 1 + Math.floor(hash(seed) * 3);
  const wob = 1.2 * Math.sin((2 * Math.PI * k * ambient) / LOOP + seed * 6.1) * (0.4 + m);
  return d.rest + (d.peak - d.rest) * m + wob;
}

/** The whole cheese at one moment. */
export function cheeseFrame(melts: number[][], events: ActiveEvent[], ambient: number, jig: number): CheeseFrame {
  'worklet';
  let sum = 0;
  let n = 0;
  const drips: DripShape[][] = [];
  for (let li = 0; li < LAYERS.length; li++) {
    const layer = LAYERS[li];
    const shapes: DripShape[] = [];
    for (let di = 0; di < layer.drips.length; di++) {
      const d = layer.drips[di];
      const m = melts[li][di];
      sum += m;
      n += 1;
      const seed = li * 31 + di;
      const x = layer.cx + d.u * layer.half;
      let length = dripLength(d, m, ambient, seed) + jig * (0.5 + hash(seed + 9));
      let r = d.w * 0.5 * (0.8 + 0.12 * m);
      let neck = 0.35 * m;
      for (let e = 0; e < events.length; e++) {
        const ev = events[e];
        if (ev.layer !== li || ev.drip !== di) continue;
        if (ev.dt < 0) {
          const q = easeInCubic(clamp01((ev.dt + PINCH_LEAD) / PINCH_LEAD));
          length += 18 * q;
          r *= 1 + 0.25 * q;
          neck = lerp(neck, 0.92, q);
        } else {
          const back = 0.5 * Math.exp(-4.5 * ev.dt) * Math.cos(8 * ev.dt);
          length *= 1 - Math.max(0, back);
          neck = lerp(neck, 0.5, Math.exp(-6 * ev.dt));
        }
      }
      shapes.push({ x, y0: edge(layer, x), w: d.w, length: Math.max(length, 3), r, neck });
    }
    drips.push(shapes);
  }
  const drops: DropShape[] = [];
  const bumps: Bump[] = [];
  for (let e = 0; e < events.length; e++) {
    const ev = events[e];
    const dt = ev.dt;
    if (dt < 0) continue;
    const layer = LAYERS[ev.layer];
    const d = layer.drips[ev.drip];
    const m = melts[ev.layer][ev.drip];
    const seed = ev.layer * 31 + ev.drip;
    const x = layer.cx + d.u * layer.half;
    const r0 = d.w * 0.5 * (0.8 + 0.12 * m) * 1.18;
    const start = edge(layer, x) + dripLength(d, m, ambient, seed) + 18;
    let target = PUDDLE_Y + 2;
    let targetLayer = 2;
    if (ev.layer === 1) {
      const below = LAYERS[0];
      target = edge(below, x) + below.band + 4;
      targetLayer = 0;
    }
    const v0 = 20;
    const dist = Math.max(0, target - r0 - start);
    const dtLand = (-v0 + Math.sqrt(v0 * v0 + 2 * GRAVITY * dist)) / GRAVITY;
    if (dt < dtLand) {
      const y = start + v0 * dt + 0.5 * GRAVITY * dt * dt;
      const v = v0 + GRAVITY * dt;
      const st = Math.min(v / 1100, 0.45);
      drops.push({ x, y, rx: r0 / Math.sqrt(1 + st), ry: r0 * (1 + st) });
    } else {
      const dl = dt - dtLand;
      const sink = easeOutCubic(clamp01(dl / 0.2));
      if (sink < 1) {
        const rr = r0 * (1 - sink);
        drops.push({ x, y: target - r0 + sink * r0 * 0.6, rx: rr * (1 + 0.6 * sink), ry: rr });
      }
      const env = dl < 0.08 ? dl / 0.08 : Math.exp(-(dl - 0.08) * 3.2);
      bumps.push({ layer: targetLayer, x, amp: 6 * env * (ev.layer === 0 ? 1.3 : 1) });
    }
  }
  return { drips, drops, bumps, melt: n > 0 ? sum / n : 0 };
}

// Touch: hold to melt more, release to let it set, tap to drop the burger in again.

export const HEAT_MAX = 1.35;
export const IDLE = 2;
const HOLD_ORDER = [
  [0, 2], [0, 5], [0, 6], [0, 1], [0, 4], [0, 0], [0, 7], [0, 3],
];

export type Touch = {
  touching: boolean;
  everTouched: boolean;
  pressClock: number;
  releaseClock: number;
  carry: number[][];
  released: number[][];
  rowCarry: number[];
  events: DropEvent[];
  nextDrop: number;
  dropCursor: number;
  dropInClock: number;
};

function zeros(): number[][] {
  'worklet';
  const out: number[][] = [];
  for (let li = 0; li < LAYERS.length; li++) {
    const row: number[] = [];
    for (let di = 0; di < LAYERS[li].drips.length; di++) row.push(0);
    out.push(row);
  }
  return out;
}

export function initialTouch(): Touch {
  'worklet';
  return {
    touching: false,
    everTouched: false,
    pressClock: -100,
    releaseClock: -100,
    carry: zeros(),
    released: zeros(),
    rowCarry: [0, 0, 0],
    events: [],
    nextDrop: 0,
    dropCursor: 0,
    dropInClock: -100,
  };
}

/** Loop time, or -1 while the loop waits; it resumes at 0 two seconds after the last touch. */
export function loopTime(s: Touch, clock: number, autoPlay: boolean) {
  'worklet';
  if (!autoPlay) return -1;
  if (!s.everTouched) return mod(clock, LOOP);
  if (s.touching || clock < s.releaseClock + IDLE) return -1;
  return mod(clock - (s.releaseClock + IDLE), LOOP);
}

export function meltsAt(s: Touch, clock: number, t: number): number[][] {
  'worklet';
  const h = s.touching ? HEAT_MAX * (1 - Math.exp(-0.8 * (clock - s.pressClock))) : 0;
  const decay = Math.exp(-1.1 * Math.max(0, clock - s.releaseClock));
  const out: number[][] = [];
  for (let li = 0; li < LAYERS.length; li++) {
    const row: number[] = [];
    for (let di = 0; di < LAYERS[li].drips.length; di++) {
      const loopM = t >= 0 ? meltLoop(t, dripDelay(li, di)) : 0;
      row.push(s.touching ? Math.max(s.carry[li][di], h) : Math.max(loopM, s.released[li][di] * decay));
    }
    out.push(row);
  }
  return out;
}

export function rowLitAt(s: Touch, clock: number, t: number, i: number) {
  'worklet';
  const loopL = t >= 0 ? rowLitLoop(t, i) : 0;
  return Math.max(loopL, s.rowCarry[i] * Math.exp(-2.5 * Math.max(0, clock - s.pressClock)));
}

export function activeEvents(s: Touch, clock: number, t: number): ActiveEvent[] {
  'worklet';
  const out: ActiveEvent[] = [];
  if (t >= 0) {
    for (let i = 0; i < LOOP_EVENTS.length; i++) {
      const e = LOOP_EVENTS[i];
      const dt = t - e.t0;
      if (dt > -PINCH_LEAD && dt < EVENT_TAIL) out.push({ layer: e.layer, drip: e.drip, dt });
    }
  }
  for (let i = 0; i < s.events.length; i++) {
    const e = s.events[i];
    const dt = clock - e.t0;
    if (dt > -PINCH_LEAD && dt < EVENT_TAIL) out.push({ layer: e.layer, drip: e.drip, dt });
  }
  return out;
}

/** Lets the next drop go while the finger holds; `scheduled` is true when one was just added. */
export function updateTouch(s: Touch, clock: number): { next: Touch; scheduled: boolean } {
  'worklet';
  if (!s.touching || clock - s.pressClock <= 0.9 || clock < s.nextDrop - PINCH_LEAD) {
    return { next: s, scheduled: false };
  }
  const events: DropEvent[] = [];
  for (let i = 0; i < s.events.length; i++) if (clock - s.events[i].t0 <= EVENT_TAIL) events.push(s.events[i]);
  const pick = HOLD_ORDER[s.dropCursor % HOLD_ORDER.length];
  events.push({ layer: pick[0], drip: pick[1], t0: s.nextDrop });
  return { next: { ...s, events, nextDrop: s.nextDrop + 0.85, dropCursor: s.dropCursor + 1 }, scheduled: true };
}

export function beginTouch(s: Touch, clock: number, autoPlay: boolean): Touch {
  'worklet';
  if (s.touching) return s;
  const t = loopTime(s, clock, autoPlay);
  const carry = meltsAt(s, clock, t);
  const rowCarry = [rowLitAt(s, clock, t, 0), rowLitAt(s, clock, t, 1), rowLitAt(s, clock, t, 2)];
  const events: DropEvent[] = [];
  for (let i = 0; i < s.events.length; i++) if (clock - s.events[i].t0 <= EVENT_TAIL) events.push(s.events[i]);
  if (t >= 0) {
    for (let i = 0; i < LOOP_EVENTS.length; i++) {
      const e = LOOP_EVENTS[i];
      if (t - e.t0 > -PINCH_LEAD && t - e.t0 < EVENT_TAIL) events.push({ layer: e.layer, drip: e.drip, t0: clock + (e.t0 - t) });
    }
  }
  return { ...s, carry, rowCarry, events, touching: true, everTouched: true, pressClock: clock, nextDrop: clock + 1.5 };
}

export function endTouch(s: Touch, clock: number): Touch {
  'worklet';
  if (!s.touching) return s;
  const released = meltsAt(s, clock, -1);
  return { ...s, released, touching: false, releaseClock: clock };
}

export function dropInTouch(s: Touch, clock: number): Touch {
  'worklet';
  return { ...s, dropInClock: clock };
}

/** Clock time after which nothing moves any more (outside autoPlay). */
export function restsAfter(s: Touch) {
  'worklet';
  let last = 0;
  for (let i = 0; i < s.events.length; i++) last = Math.max(last, s.events[i].t0);
  return Math.max(s.releaseClock + 6, s.dropInClock + 2.6, last + EVENT_TAIL);
}
