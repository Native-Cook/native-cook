// Crumple to Delete: the crumple model, timings and the autoplay script.
// The paper is a grid of vertices; crumpling maps each vertex from its place on the flat sheet to a
// place on a lumpy ball, with seeded wrinkles in between. Everything is a function of the crumple
// amount and of the time since an event, so the loop and the finger use the same curves.

export const LOOP = 9;
export const GX = 13; // vertices across
export const GY = 16; // vertices down
export const BALL = 46; // the ball's radius, pt

export const AFTER_RELEASE = {
  crush: { duration: 0.42, ratio: 0.78 }, // the rest of the crumple, after the finger lets go
  toss: [0.28, 0.9], // the throw into the bin
  sink: [0.9, 1.25], // dropping into it
  toast: 1.0,
};
export const AFTER_UNDO = {
  rise: [0, 0.22],
  fly: [0.18, 0.72],
  open: { start: 0.62, duration: 0.8, ratio: 0.62 }, // it opens out with a little spring
};
export const DRAG_FULL = 300; // pt of drag that crumples the whole sheet

/** Autoplay events, seconds into the loop. */
export const S = { grab: [1.0, 2.55], release: 2.55, undo: 6.0, rest: 8.0 } as const;

export function seg(t: number, a: number, b: number) {
  'worklet';
  return Math.min(Math.max((t - a) / (b - a), 0), 1);
}

export function smooth(a: number, b: number, x: number) {
  'worklet';
  const t = Math.min(Math.max((x - a) / (b - a), 0), 1);
  return t * t * (3 - 2 * t);
}

export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

export function springStep(t: number, duration: number, ratio: number) {
  'worklet';
  if (t <= 0) return 0;
  const w = (2 * Math.PI) / duration;
  if (ratio >= 1) return 1 - Math.exp(-w * t) * (1 + w * t);
  const wd = w * Math.sqrt(1 - ratio * ratio);
  return 1 - Math.exp(-ratio * w * t) * (Math.cos(wd * t) + ((ratio * w) / wd) * Math.sin(wd * t));
}

export function springTo(x: number, v: number, target: number, duration: number, ratio: number, h: number) {
  'worklet';
  const w = (2 * Math.PI) / duration;
  const nv = v + (w * w * (target - x) - 2 * ratio * w * v) * h;
  return [x + nv * h, nv];
}

export function hash(i: number) {
  'worklet';
  const s = Math.sin(i * 12.9898 + 78.233) * 43758.5453;
  return s - Math.floor(s);
}

/**
 * The sheet's vertices at crumple c (0 flat, 1 a ball), turned by `spin`, scaled by `sc`, centred at (cx, cy).
 * Returns positions (pairs) and a brightness per vertex for the creases.
 */
export function crumple(c: number, spin: number, sc: number, cx: number, cy: number, nw: number, nh: number) {
  'worklet';
  const pos: number[] = [];
  const light: number[] = [];
  const e = c * c * (3 - 2 * c);
  const wr = Math.sin(Math.PI * Math.min(1, c * 1.25)); // wrinkles peak mid-way, settle into the ball
  const cs = Math.cos(spin);
  const sn = Math.sin(spin);
  for (let j = 0; j < GY; j++) {
    for (let i = 0; i < GX; i++) {
      const id = j * GX + i;
      const u = (i / (GX - 1)) * 2 - 1;
      const v = (j / (GY - 1)) * 2 - 1;
      const r1 = hash(id) * 2 - 1;
      const r2 = hash(id + 91.7) * 2 - 1;
      const r3 = hash(id + 33.1);
      const x0 = (u * nw) / 2;
      const y0 = (v * nh) / 2;
      // the ball: the square folded onto a lumpy disc, edges tucked in under the middle
      const du = u * Math.sqrt(1 - (v * v) / 2);
      const dv = v * Math.sqrt(1 - (u * u) / 2);
      const edge = Math.max(Math.abs(u), Math.abs(v));
      const rb = BALL * (0.72 + 0.28 * r3) * (1 - 0.18 * edge * edge);
      const bx = du * rb + r1 * 5;
      const by = dv * rb + r2 * 5;
      // the flat sheet drawn in toward its middle as it is gathered
      const g = 1 - 0.45 * e;
      let x = x0 * g + (bx - x0 * g) * Math.pow(e, 1.4) + r1 * 13 * wr;
      let y = y0 * g + (by - y0 * g) * Math.pow(e, 1.4) + r2 * 13 * wr;
      x *= sc;
      y *= sc;
      pos.push(cx + x * cs - y * sn, cy + x * sn + y * cs);
      // creases: facets catch the window light from the upper left or fall into shade
      const facing = 0.5 + 0.5 * (r1 * 0.7 - r2 * 0.7);
      light.push(1 - c * (0.62 - 0.6 * facing) - c * 0.16 * edge);
    }
  }
  return { pos, light };
}

export type Pose = { x: number; y: number; c: number; spin: number; sc: number; sink: number; wobble: number; toast: number; lift: number };

export type Geo = { hx: number; hy: number; bx: number; by: number };

/** The ball's flight from where it was let go into the bin (and back out on undo). */
export function tossPos(q: number, x0: number, y0: number, x1: number, y1: number) {
  'worklet';
  const apex = Math.min(y0, y1) - 120;
  const x = x0 + (x1 - x0) * q;
  // a parabola through the apex, like a real throw (gravity eases it in at the top)
  const a = (1 - q) * (1 - q) * y0 + 2 * (1 - q) * q * apex + q * q * y1;
  return [x, a];
}

/** The whole autoplay pose at loop time t. */
export function script(t: number, g: Geo): Pose {
  'worklet';
  const rest = { x: g.hx, y: g.hy, c: 0, spin: 0, sc: 1, sink: 0, wobble: 0, toast: 0, lift: 0 };
  if (t < S.grab[0] || t >= S.rest) return rest;
  if (t < S.release) {
    const u = easeInOut(seg(t, S.grab[0], S.grab[1]));
    const dy = 210 * u;
    return { x: g.hx + 18 * u, y: g.hy + dy, c: Math.min(1, dy / DRAG_FULL), spin: 0.12 * u, sc: 1, sink: 0, wobble: 0, toast: 0, lift: smooth(S.grab[0], S.grab[0] + 0.2, t) };
  }
  const x0 = g.hx + 18;
  const y0 = g.hy + 210;
  const c0 = 210 / DRAG_FULL;
  const tr = t - S.release;
  const tu = t - S.undo;
  if (tu < 0) {
    const c = c0 + (1 - c0) * springStep(tr, AFTER_RELEASE.crush.duration, AFTER_RELEASE.crush.ratio);
    const q = easeInOut(seg(tr, AFTER_RELEASE.toss[0], AFTER_RELEASE.toss[1]));
    const p = tossPos(q, x0, y0, g.bx, g.by);
    const sink = easeInOut(seg(tr, AFTER_RELEASE.sink[0], AFTER_RELEASE.sink[1]));
    const land = tr - AFTER_RELEASE.toss[1];
    const wobble = land > 0 ? Math.exp(-land * 5) * Math.sin(land * 26) : 0;
    const toast = smooth(AFTER_RELEASE.toast, AFTER_RELEASE.toast + 0.3, tr);
    return { x: p[0], y: p[1], c, spin: 0.12 + 7 * q, sc: 1 - 0.18 * q, sink, wobble, toast, lift: 1 - smooth(0, 0.3, tr) };
  }
  // undo: up out of the bin, back over to the stack, and opened out flat
  const q = easeInOut(seg(tu, AFTER_UNDO.fly[0], AFTER_UNDO.fly[1]));
  const p = tossPos(q, g.bx, g.by, g.hx, g.hy);
  const sink = 1 - easeOut(seg(tu, AFTER_UNDO.rise[0], AFTER_UNDO.rise[1]));
  const open = springStep(tu - AFTER_UNDO.open.start, AFTER_UNDO.open.duration, AFTER_UNDO.open.ratio);
  const toast = 1 - smooth(0, 0.25, tu);
  return { x: p[0], y: p[1], c: Math.max(0, 1 - open), spin: (0.12 + 7) * (1 - q) * (1 - smooth(0, 1, open)), sc: 0.82 + 0.18 * q, sink, wobble: 0, toast, lift: 0 };
}
