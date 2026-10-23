/*
 The numbers behind Dot Matrix Camera Pad (a port of DotMatrixCameraPadModel.swift; the numbers
 are the same): the controls' state, the autoPlay timeline, the layout and the night street the
 viewfinder shows. Every function can run on the UI thread.
 */

/** Everything the screen shows at a moment. */
export type State = {
  /** Exposure compensation in stops. */
  ev: number;
  /** White balance in kelvin. */
  warmth: number;
  /** Left module: the A/M knob (0 = A, 1 = M) and the wheel-to-pad morph (0 = wheel, 1 = pad). */
  knob: number;
  morph: number;
  /** Right module's A/M knob. */
  knobR: number;
  /** The pad's hot spot (0…1, x = shutter from slow to fast, y = ISO from low to high) and its glow. */
  px: number;
  py: number;
  glow: number;
  /** Viewfinder zoom (0.72, 1, 1.7 for the three lenses). */
  zoom: number;
  /** Shutter press (0…1), the capture blink (0…1) and the gallery thumbnail's scale. */
  press: number;
  flash: number;
  pop: number;
};

export const LOOP = 10;
export const SHUTTER_LABELS = ['1/4', '1/8', '1/15', '1/30', '1/60', '1/125', '1/250', '1/500', '1/1000', '1/2000', '1/4000'];
export const ISO_LABELS = ['100', '200', '400', '800', '1600', '3200', '6400', '12800'];
export const COLS = 11;
export const ROWS = 8;
/** 1/60 s and ISO 400. */
export const REST_X = 0.4;
export const REST_Y = 2 / 7;
export const LENS_LABELS = ['13', '24', '77'];
export const LENS_ZOOMS = [0.72, 1, 1.7];

export function rest(): State {
  'worklet';
  return { ev: 0, warmth: 5200, knob: 0, morph: 0, knobR: 0, px: REST_X, py: REST_Y, glow: 0, zoom: 1, press: 0, flash: 0, pop: 1 };
}

export function mix(a: State, b: State, u: number): State {
  'worklet';
  const l = (x: number, y: number) => x + (y - x) * u;
  return {
    ev: l(a.ev, b.ev),
    warmth: l(a.warmth, b.warmth),
    knob: l(a.knob, b.knob),
    morph: l(a.morph, b.morph),
    knobR: l(a.knobR, b.knobR),
    px: l(a.px, b.px),
    py: l(a.py, b.py),
    glow: l(a.glow, b.glow),
    zoom: l(a.zoom, b.zoom),
    press: l(a.press, b.press),
    flash: l(a.flash, b.flash),
    pop: l(a.pop, b.pop),
  };
}

/** Shutter time in seconds along the pad's x (log-spaced: 1/4 s at 0, 1/4000 s at 1). */
export function shutterAt(x: number) {
  'worklet';
  return 0.25 * Math.pow(2, -x * 10);
}
export function isoAt(y: number) {
  'worklet';
  return 100 * Math.pow(2, y * 7);
}
/** The picture's brightness in stops relative to the resting settings. */
export function exposure(s: State) {
  'worklet';
  return s.ev + Math.log2(isoAt(s.py) / 400) + Math.log2(shutterAt(s.px) * 60);
}

export function evLabel(ev: number) {
  'worklet';
  const r = Math.round(ev * 10) / 10;
  return (r < 0 ? '-' : '+') + Math.abs(r).toFixed(1);
}

// MARK: Easing

export function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
export function smoothstep(a: number, b: number, x: number) {
  'worklet';
  const t = clamp01((x - a) / (b - a));
  return t * t * (3 - 2 * t);
}
export function easeInOut(x: number) {
  'worklet';
  const u = clamp01(x);
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}
export function easeOutBack(x: number) {
  'worklet';
  const u = clamp01(x);
  const c1 = 1.4;
  const c3 = c1 + 1;
  return 1 + c3 * Math.pow(u - 1, 3) + c1 * Math.pow(u - 1, 2);
}
function seg(t: number, a: number, b: number) {
  'worklet';
  return easeInOut((t - a) / (b - a));
}

// MARK: autoPlay

/** The finger's path across the pad: [time, x, y], smoothed between keys. */
const PATH = [
  [3.4, REST_X, REST_Y],
  [4.6, 0.1, 0],
  [5.0, 0.1, 0],
  [6.4, 0.9, 6 / 7],
  [7.2, REST_X, REST_Y],
];

export function stateAt(time: number): State {
  'worklet';
  let t = time % LOOP;
  if (t < 0) t += LOOP;
  const s = rest();
  s.ev = 0.6 * seg(t, 0.6, 1.8) - 0.6 * seg(t, 8.2, 9.0);
  s.knob = seg(t, 2.3, 2.55) - seg(t, 7.5, 7.75);
  s.morph = seg(t, 2.4, 3.0) - seg(t, 7.6, 8.2);
  s.glow = seg(t, 3.1, 3.4) - seg(t, 7.2, 7.6);
  for (let i = 0; i < PATH.length - 1; i++) {
    const a = PATH[i];
    const b = PATH[i + 1];
    if (t >= a[0] && t < b[0]) {
      const u = easeInOut((t - a[0]) / (b[0] - a[0]));
      // The long diagonal bows a little, like a thumb's arc.
      const bow = i === 2 ? 0.12 * Math.sin(Math.PI * u) : 0;
      s.px = clamp01(a[1] + (b[1] - a[1]) * u + bow);
      s.py = clamp01(a[2] + (b[2] - a[2]) * u - bow);
    }
  }
  // The shot at 9.2 s.
  const d = t - 9.2;
  if (d >= 0 && d < 0.25) s.press = Math.sin((Math.PI * d) / 0.25);
  if (d >= 0.04 && d < 0.4) s.flash = 0.85 * Math.pow(1 - (d - 0.04) / 0.36, 2);
  if (d >= 0.05 && d < 0.5) s.pop = 0.82 + 0.18 * easeOutBack((d - 0.05) / 0.4);
  return s;
}

// MARK: Layout

export type Box = { x: number; y: number; w: number; h: number };
export const MODULE_HEIGHT = 86;

export type Layout = { w: number; h: number; top: number; bottom: number };

export function viewfinder(l: Layout): Box {
  'worklet';
  return { x: 16, y: l.top + 44, w: l.w - 32, h: l.h * 0.62 - l.top - 44 };
}
function modulesY(l: Layout) {
  'worklet';
  const vf = viewfinder(l);
  return vf.y + vf.h + 70;
}
/** The left capsule grows from a 48 pt wheel to a 120 pt pad; each module (capsule, 10 pt gap, 26 pt switch) stays centred in its half. */
export function leftCapsule(l: Layout, morph: number): Box {
  'worklet';
  const cw = 48 + 72 * clamp01(morph);
  return { x: l.w / 4 - (cw + 36) / 2, y: modulesY(l), w: cw, h: MODULE_HEIGHT };
}
export function leftToggle(l: Layout, morph: number): Box {
  'worklet';
  const c = leftCapsule(l, morph);
  return { x: c.x + c.w + 10, y: modulesY(l) + 14, w: 26, h: 58 };
}
export function rightCapsule(l: Layout): Box {
  'worklet';
  return { x: l.w * 0.75 - 42, y: modulesY(l), w: 48, h: MODULE_HEIGHT };
}
export function rightToggle(l: Layout): Box {
  'worklet';
  const c = rightCapsule(l);
  return { x: c.x + c.w + 10, y: modulesY(l) + 14, w: 26, h: 58 };
}
export function readoutY(l: Layout) {
  'worklet';
  const vf = viewfinder(l);
  return vf.y + vf.h + 22;
}
export function shutterCenter(l: Layout) {
  'worklet';
  return { x: l.w / 2, y: l.h - Math.max(l.bottom, 16) - 24 - 32 };
}
export function gallery(l: Layout): Box {
  'worklet';
  const c = shutterCenter(l);
  return { x: 24, y: c.y - 24, w: 48, h: 48 };
}
export function chevron(l: Layout): Box {
  'worklet';
  const c = shutterCenter(l);
  return { x: l.w - 24 - 44, y: c.y - 22, w: 44, h: 44 };
}
export function chipsY(l: Layout) {
  'worklet';
  const vf = viewfinder(l);
  return vf.y + vf.h - 38;
}
export function chipX(l: Layout, i: number) {
  'worklet';
  return l.w / 2 + (i - 1.5) * 58;
}

// MARK: The night street

/** A small integer hash in 0…1, the same in every language. */
export function hash(a: number, b: number, c: number) {
  'worklet';
  let x = (Math.imul(a, 73856093) ^ Math.imul(b, 19349663) ^ Math.imul(c, 83492791)) >>> 0;
  x = (x ^ (x >>> 13)) >>> 0;
  x = Math.imul(x, 1274126177) >>> 0;
  x = (x ^ (x >>> 16)) >>> 0;
  return (x & 0xffff) / 65535;
}

/** Building silhouettes: left edge, width, height above the horizon (fractions of the frame). */
export const BUILDINGS = [
  [-0.02, 0.13, 0.3],
  [0.1, 0.1, 0.44],
  [0.19, 0.14, 0.26],
  [0.32, 0.09, 0.52],
  [0.4, 0.16, 0.34],
  [0.55, 0.11, 0.47],
  [0.65, 0.15, 0.3],
  [0.79, 0.1, 0.41],
  [0.88, 0.15, 0.28],
];
export const HORIZON = 0.66;
export const LAMPS = [
  [0.17, 0.56],
  [0.52, 0.53],
  [0.86, 0.57],
];
/** Cars: lane (0 = far, heading right with headlights; 1 = near, heading left), crossings per loop, phase. */
export const CARS = [
  [0, 2, 0.0],
  [0, 3, 0.5],
  [1, 2, 0.25],
  [1, 3, 0.8],
];
export const LANE_Y = [0.765, 0.855];
