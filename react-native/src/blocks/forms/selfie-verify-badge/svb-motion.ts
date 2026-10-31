// Selfie Verify Badge — tokens, layout and motion. Helpers first: worklets capture what is above them.

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}
export function easeOut(x: number) {
  'worklet';
  const u = 1 - x;
  return 1 - u * u * u;
}
export function mix(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}
export function springTo(t: number, response: number, zeta: number) {
  'worklet';
  if (t <= 0) return 0;
  const w = (2 * Math.PI) / response;
  const wd = w * Math.sqrt(1 - zeta * zeta);
  return 1 - Math.exp(-zeta * w * t) * (Math.cos(wd * t) + ((zeta * w) / wd) * Math.sin(wd * t));
}

export const COLOR = {
  dark: '#121212',
  light: '#FFFFFF',
  title: '#F5F5F5',
  body: '#9A9A9A',
  ink: '#141414',
  inkBody: '#6B6B70',
  lime: '#A6F23D',
  limeInk: '#16210A',
  button: '#2A2A2A',
  bracket: '#E8E8E8',
} as const;

export const FONT = { regular: 'SvbInter-Regular', semibold: 'SvbInter-SemiBold', bold: 'SvbInter-Bold' } as const;

export type Layout = {
  w: number;
  h: number;
  titleTop: number;
  frameX: number;
  frameY: number;
  frame: number; // half size of the face frame
  hintTop: number;
  shutterY: number;
  shutter: number;
  cover: number; // half size the cushion needs to cover the screen
};

export function makeLayout(w: number, h: number, top: number, bottom: number): Layout {
  const frame = Math.min(122, w * 0.3);
  const frameY = top + 120 + frame + 30;
  return {
    w,
    h,
    titleTop: top + 34,
    frameX: w / 2,
    frameY,
    frame,
    hintTop: frameY + frame + 40,
    shutterY: h - bottom - 74,
    shutter: 72,
    cover: Math.hypot(w, h) * 0.8,
  };
}

/**
 * The verification as one number: 0 scanning a face … 1 verified.
 * the face mesh spins and gathers into the centre 0–0.3 (gone by 0.36), the brackets close 0–0.12,
 * the frame becomes the scalloped outline 0.06–0.3, the cushion inflates slowly 0.2–0.55 and grows
 * over the screen 0.45–0.72, the badge flips in 0.55–0.9, the check draws 0.86–1.
 */
export function verified(L: Layout, k: number) {
  'worklet';
  const shape = easeInOut(seg(k, 0.06, 0.3));
  const puff = easeInOut(seg(k, 0.2, 0.55));
  const grow = easeInOut(seg(k, 0.45, 0.72));
  const size = mix(mix(L.frame, L.frame * 0.82, shape), L.cover, grow);
  const flipIn = seg(k, 0.55, 0.9);
  const badge = 56 * springTo(flipIn * 0.44, 0.42, 0.55);
  const yaw = (1 - easeOut(flipIn)) * Math.PI * 3;
  return {
    brackets: 1 - easeOut(seg(k, 0.0, 0.12)),
    collapse: easeInOut(seg(k, 0.0, 0.3)),
    bust: 1 - easeInOut(seg(k, 0.24, 0.36)),
    shape,
    puff,
    size,
    badge,
    yaw,
    check: easeInOut(seg(k, 0.86, 1)),
    light: easeInOut(seg(k, 0.55, 0.7)), // copy and status bar turn dark-on-light
  };
}

/* ---------------------------------------------------------------- loop */

export const LOOP = 9.6;
export const T = { scan0: 0.8, scan1: 1.7, detect: 1.7, shutter: 2.2, go0: 2.35, go1: 5.6, back0: 7.6, back1: 8.9 } as const;

export function scanAt(t: number) {
  'worklet';
  // the line sweeps down twice over the face, then rests off
  if (t < T.scan0 || t > T.back0) return -9;
  if (t >= T.scan1) return -9;
  const u = seg(t, T.scan0, T.scan1);
  return mix(-0.52, 0.58, easeInOut(u < 0.5 ? u * 2 : 2 - u * 2));
}
export function detectedAt(t: number) {
  'worklet';
  return easeOut(seg(t, T.detect, T.detect + 0.25)) * (1 - easeOut(seg(t, T.back1, T.back1 + 0.3)));
}
export function linkAt(t: number) {
  'worklet';
  if (t < T.go0) return 0;
  if (t < T.back0) return seg(t, T.go0, T.go1);
  return 1 - easeInOut(seg(t, T.back0, T.back1));
}
/** The camera flash, only on the way in. */
export function flashAt(t: number) {
  'worklet';
  const u = seg(t, T.go0 - 0.02, T.go0 + 0.2);
  return u > 0 && u < 1 ? Math.sin(u * Math.PI) * (1 - u) : 0;
}
export function shutterAt(t: number) {
  'worklet';
  return easeOut(seg(t, T.shutter, T.shutter + 0.1)) * (1 - easeOut(seg(t, T.shutter + 0.14, T.shutter + 0.34)));
}
