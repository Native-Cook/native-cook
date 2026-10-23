import { Skia, type SkCanvas, TileMode } from '@shopify/react-native-skia';

/*
 The numbers behind Filmstrip Arc Scrubber (a port of FilmstripArcScrubberModel.swift; the numbers
 are the same): the one-minute "video" (a night drive drawn as a function of its time), the
 filmstrip's geometry, the layout and the autoPlay timeline. Every function can run on the UI thread.
 */

type RGB = [number, number, number];

/** What the screen shows at a moment. */
export type Shown = {
  /** The video's time, in seconds (0 – 60). */
  time: number;
  /** How far the filmstrip has lifted out of the bar (0 – 1). */
  lift: number;
  /** The selected frame flying up into the video (0 – 1, 0 when none) and its time. */
  fly: number;
  flyTime: number;
  /** How fast the time is being scrubbed (seconds per second), for the motion blur. */
  speed: number;
  playing: boolean;
};

export const DURATION = 60;
/** One frame of the strip every 2.5 s of video. */
export const FRAME_STEP = 2.5;
export const LOOP = 10;
/** Where the loop starts and ends: 0:18 into the video, playing. */
export const REST_TIME = 18;

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
  const c1 = 1.5;
  const c3 = c1 + 1;
  return 1 + c3 * Math.pow(u - 1, 3) + c1 * Math.pow(u - 1, 2);
}
export function wrap(t: number) {
  'worklet';
  let x = t % DURATION;
  if (x < 0) x += DURATION;
  return x;
}

// MARK: autoPlay

/** The video's time at a moment of the loop: plays 0:18 → 0:20, is held and dragged forward ten frames into the night (0:45), back to 0:15, released at 7.0 s and plays on to 0:18 at 10 s. */
function timeAt(t: number) {
  'worklet';
  if (t < 2) return REST_TIME + t;
  if (t < 2.7) return 20;
  if (t < 4.5) return 20 + 25 * easeInOut((t - 2.7) / 1.8);
  if (t < 4.6) return 45;
  if (t < 6.6) return 45 - 30 * easeInOut((t - 4.6) / 2.0);
  if (t < 7) return 15;
  return 15 + (t - 7);
}

/**
 * autoPlay (LOOP = 10 s):
 *   0.0 – 2.0   playing from 0:18
 *   2.0         a finger presses the thumb: playback holds, the strip lifts (0.5 s, ease out back)
 *   2.7 – 4.5   dragged forward to 0:45 (night);  4.6 – 6.6 back to 0:15 (easeInOut)
 *   7.0         released: the 0:15 frame flies up into the video (0.6 s) and playback resumes
 *   7.2 – 7.7   the strip folds back into the bar
 *   10.0        0:18 again, playing: the same frame as 0
 */
export function shownAtLoop(time0: number): Shown {
  'worklet';
  let t = time0 % LOOP;
  if (t < 0) t += LOOP;
  const lift = t >= 2 && t < 7.2 ? easeOutBack((t - 2) / 0.5) : t >= 7.2 ? 1 - easeInOut((t - 7.2) / 0.5) : 0;
  const speed = (timeAt(t + 0.02) - timeAt(Math.max(0, t - 0.02))) / 0.04;
  const fly = t >= 7 && t < 7.6 ? easeInOut((t - 7) / 0.6) : 0;
  return { time: timeAt(t), lift, fly, flyTime: 15, speed, playing: t < 2 || t >= 7 };
}

// MARK: Layout

export type Layout = { w: number; h: number; top: number; bottom: number };
export type Box = { x: number; y: number; w: number; h: number };

export function video(l: Layout): Box {
  'worklet';
  return { x: 12, y: l.top + 8, w: l.w - 24, h: l.h * 0.6 - l.top - 8 };
}
export function titleY(l: Layout) {
  'worklet';
  return l.h * 0.6 + 20;
}
export function barY(l: Layout) {
  'worklet';
  return l.h * 0.785;
}
export const BAR_X = 24;
export function barW(l: Layout) {
  'worklet';
  return l.w - 48;
}
export function transportY(l: Layout) {
  'worklet';
  return l.h - Math.max(l.bottom, 16) - 50;
}
/** The strip's centre line when lifted. */
export function stripY(l: Layout) {
  'worklet';
  return barY(l) - 98;
}

// MARK: The film strip

/** A strip cell: the film (64 × 82) with sprocket holes, its picture 56 × 60 at (4, 11). */
export const CELL_W = 64;
export const CELL_H = 82;
export const PIC_X = 4;
export const PIC_Y = 11;
export const PIC_W = 56;
export const PIC_H = 60;
/** The strip wraps a cylinder of this radius (points), seen in perspective (focal 520). */
export const RADIUS = 230;
export const FOCAL = 520;

// MARK: The night drive

/** A small integer hash in 0…1, the same in every language. */
function hash(a: number, b: number, c: number) {
  'worklet';
  let x = (Math.imul(a, 73856093) ^ Math.imul(b, 19349663) ^ Math.imul(c, 83492791)) >>> 0;
  x = (x ^ (x >>> 13)) >>> 0;
  x = Math.imul(x, 1274126177) >>> 0;
  x = (x ^ (x >>> 16)) >>> 0;
  return (x & 0xffff) / 65535;
}

/** The light over the minute: golden hour, dusk, night, deep night (sky top, horizon, far hills, near hills). */
const KEY_TIMES = [0, 22, 40, 60];
const KEYS: RGB[][] = [
  [
    [0.98, 0.6, 0.36],
    [1.0, 0.86, 0.58],
    [0.62, 0.36, 0.4],
    [0.34, 0.18, 0.26],
  ],
  [
    [0.42, 0.2, 0.52],
    [1.0, 0.5, 0.42],
    [0.36, 0.18, 0.36],
    [0.18, 0.09, 0.2],
  ],
  [
    [0.08, 0.08, 0.24],
    [0.36, 0.22, 0.46],
    [0.14, 0.1, 0.24],
    [0.07, 0.05, 0.13],
  ],
  [
    [0.02, 0.03, 0.1],
    [0.12, 0.12, 0.28],
    [0.07, 0.07, 0.15],
    [0.03, 0.03, 0.07],
  ],
];

function mix(a: RGB, b: RGB, t: number): RGB {
  'worklet';
  return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];
}

function palette(t: number): RGB[] {
  'worklet';
  for (let k = 0; k < KEYS.length - 1; k++) {
    if (t <= KEY_TIMES[k + 1]) {
      const u = smoothstep(KEY_TIMES[k], KEY_TIMES[k + 1], t);
      return [0, 1, 2, 3].map((i) => mix(KEYS[k][i], KEYS[k + 1][i], u));
    }
  }
  return KEYS[KEYS.length - 1];
}

export function col(c: RGB | number[], a = 1) {
  'worklet';
  const f = (x: number) => Math.min(1, Math.max(0, x));
  return Float32Array.of(f(c[0]), f(c[1]), f(c[2]), f(a));
}

const HORIZON = 0.56;

function glow(canvas: SkCanvas, x: number, y: number, r: number, c: number[], a: number) {
  'worklet';
  const p = Skia.Paint();
  p.setShader(Skia.Shader.MakeRadialGradient({ x, y }, r, [col(c, a), col(c, 0)], null, TileMode.Clamp));
  canvas.drawCircle(x, y, r, p);
}

/** Draws the video at time t into a rect; `lite` leaves out the stars and the lamp posts (strip thumbnails). */
export function drawScene(canvas: SkCanvas, r: Box, t: number, lite: boolean) {
  'worklet';
  const w = r.w;
  const h = r.h;
  const X = (u: number) => r.x + u * w;
  const Y = (v: number) => r.y + v * h;
  const pal = palette(t);
  const vh = HORIZON;
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  // Sky and ground.
  paint.setColor(col(pal[3]));
  canvas.drawRect(Skia.XYWHRect(r.x, r.y, w, h), paint);
  const sky = Skia.Paint();
  sky.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: r.y }, { x: 0, y: Y(vh) }, [col(pal[0]), col(pal[1])], null, TileMode.Clamp));
  canvas.drawRect(Skia.XYWHRect(r.x, r.y, w, vh * h + 1), sky);
  // Stars come out as it darkens.
  const starA = smoothstep(26, 44, t);
  if (!lite && starA > 0.01) {
    for (let i = 0; i < 36; i++) {
      const tw = 0.75 + 0.25 * Math.sin(t * 3 + i);
      const s = 1.2 + 0.8 * hash(i, 2, 5);
      paint.setColor(Float32Array.of(1, 1, 1, Math.min(1, starA * (0.5 + 0.5 * hash(i, 4, 5)) * tw)));
      canvas.drawCircle(X(hash(i, 1, 5)), Y(0.48 * hash(i, 3, 5)), s / 2, paint);
    }
  }
  // The sun sets by 0:30; the moon rises after 0:34.
  const sunA = 1 - smoothstep(20, 34, t);
  if (sunA > 0.01) {
    const sx = X(0.62);
    const sy = Y(0.3 + (0.34 * t) / 30);
    glow(canvas, sx, sy, 0.36 * w, [1, 0.75, 0.45], 0.8 * sunA);
    paint.setColor(Float32Array.of(1, 0.92, 0.72, 1));
    canvas.drawCircle(sx, sy, 0.07 * w, paint);
  }
  if (t > 34) {
    const mx = X(0.24);
    const my = Y(0.62 - 0.4 * smoothstep(34, 60, t));
    const mr = 0.045 * w;
    glow(canvas, mx, my, mr * 3, [1, 1, 1], 0.22);
    paint.setColor(Float32Array.of(0.96, 0.94, 0.86, 1));
    canvas.drawCircle(mx, my, mr, paint);
  }
  // Far mountains, then near hills, drifting past at two speeds.
  const step = lite ? 0.1 : 0.05;
  const ridge = (base: number, a1: number, f1: number, a2: number, f2: number, shift: number, phase: number) => {
    const p = Skia.Path.Make();
    p.moveTo(X(-0.05), Y(1.01));
    for (let u = -0.05; u <= 1.05 + 1e-9; u += step) {
      p.lineTo(X(u), Y(base - a1 * Math.sin((u + shift) * f1 + phase) - a2 * Math.sin((u + shift) * f2 + phase * 2 + 1)));
    }
    p.lineTo(X(1.05), Y(1.01));
    p.close();
    return p;
  };
  paint.setColor(col(pal[2]));
  canvas.drawPath(ridge(vh - 0.1, 0.06, 7.1, 0.04, 13.7, t * 0.004, 0), paint);
  paint.setColor(col(pal[3]));
  canvas.drawPath(ridge(vh - 0.02, 0.035, 5.3, 0.02, 11, t * 0.012, 2), paint);
  // The road to the vanishing point.
  const road = Skia.Path.Make();
  road.moveTo(X(0.49), Y(vh));
  road.lineTo(X(0.51), Y(vh));
  road.lineTo(X(1.15), Y(1));
  road.lineTo(X(-0.15), Y(1));
  road.close();
  paint.setColor(col(mix(pal[3], [0.1, 0.09, 0.12], 0.6)));
  canvas.drawPath(road, paint);
  const edge = Skia.Paint();
  edge.setAntiAlias(true);
  edge.setColor(Float32Array.of(0.85, 0.8, 0.7, 0.4));
  edge.setStrokeWidth(lite ? 0.8 : 1.5);
  canvas.drawLine(X(0.49), Y(vh), X(-0.15), Y(1), edge);
  canvas.drawLine(X(0.51), Y(vh), X(1.15), Y(1), edge);
  // Lane dashes rushing towards the camera.
  for (let k = 0; k < 8; k++) {
    let z = (k / 8 + t * 0.35) % 1;
    if (z < 0) z += 1;
    const d0 = Math.pow(z, 2.2);
    const d1 = Math.pow(Math.min(1, z + 0.045), 2.2);
    const v0 = vh + (1 - vh) * d0;
    const v1 = vh + (1 - vh) * d1;
    const w0 = 0.002 + 0.016 * d0;
    const w1 = 0.002 + 0.016 * d1;
    const dash = Skia.Path.Make();
    dash.moveTo(X(0.5 - w0), Y(v0));
    dash.lineTo(X(0.5 + w0), Y(v0));
    dash.lineTo(X(0.5 + w1), Y(v1));
    dash.lineTo(X(0.5 - w1), Y(v1));
    dash.close();
    paint.setColor(Float32Array.of(0.95, 0.88, 0.6, 0.85 * smoothstep(0, 0.15, z)));
    canvas.drawPath(dash, paint);
  }
  // Lamp posts along the right verge, lit as it gets dark.
  const lampA = smoothstep(16, 30, t);
  if (!lite) {
    const pole = Skia.Paint();
    pole.setAntiAlias(true);
    for (let k = 0; k < 5; k++) {
      let z = (k / 5 + t * 0.18) % 1;
      if (z < 0) z += 1;
      const d = Math.pow(z, 2.2);
      const bx = X(0.5 + 0.08 + 0.62 * d);
      const by = Y(vh + (1 - vh) * d);
      const ty = by - (0.02 + 0.5 * d) * h;
      pole.setColor(Float32Array.of(0.06, 0.05, 0.08, smoothstep(0, 0.1, z)));
      pole.setStrokeWidth(0.6 + 3 * d);
      canvas.drawLine(bx, by, bx, ty, pole);
      if (lampA > 0.01) glow(canvas, bx, ty, (0.01 + 0.08 * d) * w, [1, 0.8, 0.5], 0.85 * lampA * smoothstep(0, 0.1, z));
    }
  }
  // A car's tail lights ahead.
  const carA = 0.25 + 0.75 * smoothstep(10, 26, t);
  const cv = vh + 0.04 + 0.02 * Math.sin(t * 0.4);
  for (const dx of [-0.014, 0.014]) {
    glow(canvas, X(0.5 + dx), Y(cv), 0.022 * w, [1, 0.15, 0.1], 0.9 * carA);
    paint.setColor(Float32Array.of(1, 0.5, 0.45, carA));
    canvas.drawCircle(X(0.5 + dx), Y(cv), 1.2, paint);
  }
  // Vignette.
  const inner = Math.min(w, h) * 0.35;
  const outer = Math.max(w, h) * 0.75;
  const vig = Skia.Paint();
  vig.setShader(Skia.Shader.MakeRadialGradient({ x: X(0.5), y: Y(0.5) }, outer, [Float32Array.of(0, 0, 0, 0), Float32Array.of(0, 0, 0, 0.4)], [inner / outer, 1], TileMode.Clamp));
  canvas.drawRect(Skia.XYWHRect(r.x, r.y, w, h), vig);
}

export function clock(t: number) {
  'worklet';
  const s = Math.floor(t);
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

/** Timecode with frames at 24 per second: 00:18:04. */
export function timecode(t: number) {
  'worklet';
  const s = Math.floor(t);
  const two = (n: number) => String(n).padStart(2, '0');
  return `${two(Math.floor(s / 60))}:${two(s % 60)}:${two(Math.floor((t - s) * 24))}`;
}
