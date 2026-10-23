/*
 Light Table Loupe — the data and the timeline (a port of LightTableLoupeModel.swift; the numbers are the
 same). All functions are worklets.

 Twelve 35 mm slides, each a transparency drawn as gradient art: a sky gradient, an optional sun (a disc
 with a glow three times its radius) and up to two ridges, each a band filled from
 y = base + amp · sin(2π f u + phase) (u = 0 … 1 across the window, y as a fraction of its height) down
 to the bottom. All numbers are fractions of the slide's window.

 autoPlay timeline (LOOP = 9 s); the loupe parks at the lower right of the table:
   0.0–0.6   rest: the loupe parked, "Drag the loupe over a slide".
   0.6–1.6   the loupe lifts and glides (ease in-out, on an arc bowed 18 % of the way sideways) to slide 4;
             the hint fades 0.6–0.75.
   1.6       it lands: the view under the glass pulls focus (blur 6 → 0 in 0.25 s) and the caption fades in.
   2.4–3.2   on to slide 6 (focus at 3.2). 4.0–4.8 on to slide 8 (focus at 4.8).
   5.0       a tap: the loupe dips 4 % (0.25 s) and a red grease-pencil circle is drawn round slide 8 in
             0.4 s, the slide shifting 3 pt and tilting 1.5° (ease-out-back 0.3 s); "1 MARKED" fades in.
   6.0–7.0   the loupe glides back to its place; the caption fades and the hint returns 7.0–7.25.
   7.6–8.2   the mark is wiped off (fades out) and the slide settles back.
   8.2–9.0   rest, the same frame as t = 0.
 All the while the table light breathes ±1.5 % (two cycles a loop) and a soft sheen crosses the glass once
 a loop (left to right, 1.6–4.6 s). The viewer projects the slide under the loupe with the caption's
 fade, its lamp flickering ±1 % eighteen times a loop.
 */

export const LOOP = 9;
export const AURORA = 6;
export const TAP_AT = 5;
export const MARKED = 7;

export type Ridge = { base: number; amp: number; freq: number; phase: number; color: string };
export type Slide = {
  title: string;
  top: string;
  bottom: string;
  sun: { x: number; y: number; r: number; color: string; glow: number };
  ridges: Ridge[];
};

function ridge(base: number, amp: number, freq: number, phase: number, color: string): Ridge {
  return { base, amp, freq, phase, color };
}

export const SLIDES: Slide[] = [
  { title: 'Harbor at dusk', top: '#1B2A6B', bottom: '#F7814A', sun: { x: 0.7, y: 0.6, r: 0.1, color: '#FFD27A', glow: 0.55 }, ridges: [ridge(0.7, 0.03, 1.5, 0.4, '#2A1E4A'), ridge(0.86, 0.01, 4.0, 1.0, '#182040')] },
  { title: 'Wheat in June', top: '#3F9BEA', bottom: '#CFE9FF', sun: { x: 0.2, y: 0.25, r: 0.08, color: '#FFF6C8', glow: 0.6 }, ridges: [ridge(0.62, 0.05, 0.8, 0.2, '#E9B949'), ridge(0.8, 0.03, 1.6, 2.0, '#C98A2B')] },
  { title: 'Open sea', top: '#0E4C92', bottom: '#7FD1F0', sun: { x: 0.5, y: 0.3, r: 0.06, color: '#FFFFFF', glow: 0.5 }, ridges: [ridge(0.55, 0.006, 6.0, 0.0, '#0A6FA8'), ridge(0.78, 0.02, 3.0, 1.2, '#04507F')] },
  { title: 'Night street', top: '#0B0820', bottom: '#40206A', sun: { x: 0.78, y: 0.22, r: 0.06, color: '#F5E9C8', glow: 0.45 }, ridges: [ridge(0.6, 0.08, 3.2, 0.6, '#120C2E'), ridge(0.84, 0.03, 5.0, 2.2, '#E0457B')] },
  { title: 'Desert road', top: '#F2A65A', bottom: '#FFE1A8', sun: { x: 0.5, y: 0.5, r: 0.14, color: '#FFF1D0', glow: 0.6 }, ridges: [ridge(0.64, 0.04, 1.1, 1.4, '#C4622D'), ridge(0.84, 0.02, 2.0, 0.3, '#8A3A1C')] },
  { title: 'Glacier lake', top: '#5FB2E6', bottom: '#E6F6FF', sun: { x: 0.25, y: 0.2, r: 0.05, color: '#FFFFFF', glow: 0.5 }, ridges: [ridge(0.48, 0.12, 1.8, 0.9, '#6F8FB0'), ridge(0.72, 0.0, 1.0, 0.0, '#2BB3B8')] },
  { title: 'Northern lights', top: '#031426', bottom: '#0E3B4A', sun: { x: 0.4, y: 0.32, r: 0, color: '#38F2A0', glow: 0 }, ridges: [ridge(0.78, 0.05, 2.4, 0.7, '#020A12')] },
  { title: 'Pink beach', top: '#FF8FA8', bottom: '#FFE0C2', sun: { x: 0.7, y: 0.38, r: 0.09, color: '#FFFFFF', glow: 0.55 }, ridges: [ridge(0.64, 0.01, 3.0, 0.0, '#4FC6D8'), ridge(0.8, 0.02, 1.2, 0.8, '#FFD6B0')] },
  { title: 'Alpine snow', top: '#2F6FD6', bottom: '#A9D2FF', sun: { x: 0.8, y: 0.2, r: 0.05, color: '#FFFFFF', glow: 0.5 }, ridges: [ridge(0.55, 0.15, 2.2, 0.3, '#F4F8FF'), ridge(0.78, 0.06, 3.0, 1.9, '#1F5A3A')] },
  { title: 'Tulip rows', top: '#6EC3FF', bottom: '#E8F7FF', sun: { x: 0.15, y: 0.2, r: 0.06, color: '#FFFBE0', glow: 0.55 }, ridges: [ridge(0.6, 0.02, 1.0, 0.5, '#FF3D6E'), ridge(0.78, 0.02, 1.3, 2.4, '#FFB703')] },
  { title: 'City at dusk', top: '#2E1A55', bottom: '#FF6F61', sun: { x: 0.3, y: 0.66, r: 0.11, color: '#FFC27A', glow: 0.6 }, ridges: [ridge(0.7, 0.1, 4.5, 0.2, '#1D1236'), ridge(0.9, 0.01, 1.0, 0.0, '#120A24')] },
  { title: 'Fern forest', top: '#0F3D2E', bottom: '#7BC47F', sun: { x: 0.6, y: 0.15, r: 0.08, color: '#E6FFB0', glow: 0.7 }, ridges: [ridge(0.5, 0.1, 3.0, 1.1, '#0B2E21'), ridge(0.78, 0.06, 5.0, 0.4, '#145C3A')] },
];

/** Stops: −1 is the parking place, otherwise a slide index. */
const GLIDES = [
  { from: -1, to: 3, start: 0.6, end: 1.6, bow: 0.18 },
  { from: 3, to: 5, start: 2.4, end: 3.2, bow: -0.18 },
  { from: 5, to: 7, start: 4.0, end: 4.8, bow: 0.18 },
  { from: 7, to: -1, start: 6.0, end: 7.0, bow: -0.18 },
];

export type Mark = { draw: number; alpha: number; tilt: number };

/** What the screen shows at one moment. Points are screen points. */
export type Frame = {
  x: number;
  y: number;
  lift: number;
  press: number;
  focus: number;
  focusAt: number;
  blur: number;
  caption: number;
  hint: number;
  markDraw: number;
  markAlpha: number;
  markTilt: number;
  /** Marks by slide index. */
  marks: Record<number, Mark>;
  markCount: number;
  counter: number;
  pulse: number;
  sheen: number;
  /** The viewer's lamp flicker, −1 … 1 (18 cycles a loop). */
  flicker: number;
};

export function emptyFrame(): Frame {
  'worklet';
  return {
    x: 0, y: 0, lift: 0, press: 0, focus: -1, focusAt: -10, blur: 0, caption: 0, hint: 1,
    markDraw: 0, markAlpha: 0, markTilt: 0, marks: {}, markCount: 0, counter: 0, pulse: 0, sheen: -1, flicker: 0,
  };
}

export function clamp(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
export function smoothstep(a: number, b: number, x: number) {
  'worklet';
  const t = clamp((x - a) / (b - a));
  return t * t * (3 - 2 * t);
}
export function easeInOut(x: number) {
  'worklet';
  const t = clamp(x);
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}
export function easeOutBack(x: number) {
  'worklet';
  const t = clamp(x);
  const c1 = 1.70158;
  const c3 = c1 + 1;
  return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
}

/** Where along an arc from a to b, bowed sideways by `bow` of its length. */
export function arc(ax: number, ay: number, bx: number, by: number, u: number, bow: number) {
  'worklet';
  const e = easeInOut(u);
  const mx = (ax + bx) / 2;
  const my = (ay + by) / 2;
  const cx = mx - (by - ay) * bow;
  const cy = my + (bx - ax) * bow;
  return {
    x: (1 - e) * (1 - e) * ax + 2 * (1 - e) * e * cx + e * e * bx,
    y: (1 - e) * (1 - e) * ay + 2 * (1 - e) * e * cy + e * e * by,
  };
}

/** What plays at loop time t, given the parking place and the slides' centres. */
export function autoFrame(t: number, parkX: number, parkY: number, centers: number[][]): Frame {
  'worklet';
  const f = emptyFrame();
  const at = (stop: number) => (stop < 0 ? [parkX, parkY] : centers[stop]);
  f.x = parkX;
  f.y = parkY;
  for (let i = 0; i < GLIDES.length; i++) {
    const g = GLIDES[i];
    if (t < g.start) {
      const p = at(g.from);
      f.x = p[0];
      f.y = p[1];
      break;
    }
    if (t < g.end) {
      const u = (t - g.start) / (g.end - g.start);
      const a = at(g.from);
      const b = at(g.to);
      const p = arc(a[0], a[1], b[0], b[1], u, g.bow);
      f.x = p.x;
      f.y = p.y;
      f.lift = Math.sin(Math.PI * u);
      break;
    }
    const p = at(g.to);
    f.x = p[0];
    f.y = p[1];
    if (g.to >= 0) {
      f.focus = g.to;
      f.focusAt = g.end;
      const leave = i + 1 < GLIDES.length ? GLIDES[i + 1].start : LOOP;
      f.caption = smoothstep(g.end, g.end + 0.25, t) * (1 - smoothstep(leave, leave + 0.15, t));
    } else {
      f.focus = -1;
    }
  }
  if (f.focus < 0) f.focusAt = -10;
  if (f.focus >= 0 && t >= f.focusAt) f.blur = 6 * (1 - smoothstep(f.focusAt, f.focusAt + 0.25, t));
  f.hint = 1 - smoothstep(0.6, 0.75, t) + smoothstep(7.0, 7.25, t);
  if (t >= TAP_AT && t < TAP_AT + 0.25) f.press = Math.sin((Math.PI * (t - TAP_AT)) / 0.25);
  if (t >= TAP_AT) {
    f.markDraw = clamp((t - TAP_AT) / 0.4);
    f.markTilt = easeOutBack((t - TAP_AT) / 0.3) * (1 - easeInOut((t - 7.6) / 0.6));
    f.markAlpha = 1 - smoothstep(7.6, 8.2, t);
  }
  f.counter = smoothstep(TAP_AT, TAP_AT + 0.3, t) * (1 - smoothstep(7.6, 8.2, t));
  f.pulse = Math.sin((2 * Math.PI * 2 * t) / LOOP);
  f.sheen = t >= 1.6 && t < 4.6 ? (t - 1.6) / 3.0 : -1;
  f.flicker = Math.sin((2 * Math.PI * 18 * t) / LOOP);
  return f;
}

/** Seeded noise: the same numbers as the Swift and Kotlin versions (32-bit integer hash). */
export function hash(i: number, seed: number) {
  'worklet';
  let x = (Math.imul(i, 374761393) + Math.imul(seed, 668265263)) | 0;
  x = Math.imul(x ^ (x >>> 13), 1274126177);
  x ^= x >>> 16;
  return (x >>> 0) / 4294967296;
}
