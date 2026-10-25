// Darkroom Tray Develop — the loop's script, as pure worklets.
//
// LOOP = 9 s:
//   0.0 – 1.2  rest: a fresh sheet under the developer showing a faint latent image, the liquid
//              swaying gently (twice a loop), the safelight red.
//   1.2 – 5.0  rocking: the tray tips left and right (period 1.25 s, eased in and out over 0.4 s),
//              a wave runs across, the image comes up shadows first (development 0.6 – 4.9);
//              the timer runs 0:00 → 1:30.
//   5.0 – 5.8  stop bath: the liquid turns faintly amber (5.0 – 5.25) and clears into the fixer.
//   5.3        the FIXED stamp lands (scale 1.3 → 1 with a small overshoot, 0.25 s).
//   5.6 – 5.7  the room light flickers on (white), holds to 6.4, and goes back to red (0.15 s).
//   6.6 – 7.7  the print slides out to the right (ease-in-out); 6.9 – 8.1 a fresh sheet slides in
//              from the left (ease-out), stirring a small wave; the timer winds back 7.0 – 7.8;
//              7.1 – 7.9 the fresh sheet's latent image appears while it slides in.
//   8.1 – 9.0  rest. The frame at 9 s is the frame at 0 s: the old print is out of the tray and
//              the fresh sheet is where the blank one was.

export const LOOP = 9;
/** Seconds of development the timer shows by the end of the rocking. */
export const DEV_SECONDS = 90;
export const ROCK_PERIOD = 1.25;
/** The faint latent image a sheet shows within 0.6 s of landing in the developer. */
export const LATENT = 0.14;
/** Whole number of wave crests a loop, so the wave phase repeats. */
const WAVES = 7;

export type Layout = { tray: [number, number, number, number]; sheet: [number, number, number, number] };

export function makeLayout(width: number, height: number, insetTop: number, insetBottom: number): Layout {
  const top = insetTop + 118;
  const bottom = height - insetBottom - 16 - 128 - 18;
  const tw = width - 32;
  const th = Math.max(260, bottom - top);
  const sw = Math.min(tw - 76, (th - 90) * 0.8);
  return { tray: [16, top, tw, th], sheet: [width / 2, top + th / 2, sw, sw * 1.25] };
}

export function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeInOut(x: number) {
  'worklet';
  const c = clamp01(x);
  return c < 0.5 ? 4 * c * c * c : 1 - Math.pow(-2 * c + 2, 3) / 2;
}

export function easeOut(x: number) {
  'worklet';
  const c = clamp01(x);
  return 1 - Math.pow(1 - c, 3);
}

export type Frame = {
  dev: number;
  /** The fresh sheet's development (it starts as soon as it lands). */
  devB: number;
  slideA: number;
  slideB: number;
  rock: number;
  wave: number;
  wphase: number;
  light: number;
  stopBath: number;
  /** The stamp's landing (0 … 1, see stampScale) and its exit (0 … 1). */
  stamp: number;
  stampOut: number;
  /** Development seconds shown by the timer. */
  secs: number;
  /** 0 Develop, 1 Stop, 2 Fix (back to Develop for the fresh sheet). */
  stage: number;
};

export function makeFrame(): Frame {
  'worklet';
  return { dev: LATENT, devB: 0, slideA: 0, slideB: -1000, rock: 0, wave: 0, wphase: 0, light: 0, stopBath: 0, stamp: 0, stampOut: 0, secs: 0, stage: 0 };
}

/** The rocking envelope: eased in at 1.2 – 1.6 and out at 4.6 – 5.0. */
function rockEnvelope(t: number) {
  'worklet';
  return easeInOut(seg(t, 1.2, 1.6)) * (1 - easeInOut(seg(t, 4.6, 5.0)));
}

/** The loop at time t, for a tray `width` pt wide; `leave` is the development of the print that leaves. */
export function loopFrame(t: number, width: number, leave: number, f: Frame) {
  'worklet';
  const env = rockEnvelope(t);
  // The rocking, plus a gentle idle sway (twice a loop) so the liquid is never still.
  f.rock = 0.75 * env * Math.sin((2 * Math.PI * (t - 1.2)) / ROCK_PERIOD) + 0.06 * Math.sin((2 * Math.PI * 2 * t) / LOOP);
  f.wphase = (2 * Math.PI * WAVES * t) / LOOP;
  const stir = Math.sin(Math.PI * seg(t, 6.6, 8.3));
  f.wave = 0.12 + 0.68 * env + 0.35 * stir;
  const d = seg(t, 0.6, 4.9);
  f.dev = t < 6.4 ? LATENT + (1 - LATENT) * Math.pow(d, 1.25) : leave;
  // The fresh sheet starts to show its latent image as soon as it is under the developer.
  f.devB = LATENT * easeOut(seg(t, 7.1, 7.9));
  // The timer runs during the rocking and winds back to 0:00 while the sheets change.
  f.secs = DEV_SECONDS * seg(t, 1.2, 5.0) * (1 - easeInOut(seg(t, 7.0, 7.8)));
  f.stopBath = easeOut(seg(t, 5.0, 5.25)) * (1 - easeInOut(seg(t, 5.45, 5.8)));
  // The stamp lands with a small overshoot, then leaves when the light goes back to red.
  f.stamp = seg(t, 5.3, 5.55);
  f.stampOut = easeInOut(seg(t, 6.4, 6.65));
  // The room light: a fluorescent flicker on, then off in 0.15 s.
  let light = 0;
  if (t >= 5.6 && t < 5.64) light = 0.75;
  else if (t >= 5.64 && t < 5.67) light = 0.2;
  else if (t >= 5.67 && t < 6.4) light = easeOut(seg(t, 5.67, 5.75));
  else if (t >= 6.4) light = 1 - easeOut(seg(t, 6.4, 6.55));
  f.light = light;
  const out = easeInOut(seg(t, 6.6, 7.7));
  const inn = easeOut(seg(t, 6.9, 8.1));
  f.slideA = out * width * 1.05;
  f.slideB = -(1 - inn) * width * 1.05;
  if (t < 6.6) f.slideB = -width * 1.05;
  f.stage = t < 5.0 ? 0 : t < 5.45 ? 1 : t < 6.6 ? 2 : 0;
}

/** The stamp's scale while it lands: 1.3 down to 0.94 (ease-out), back up to 1. */
export function stampScale(s: number) {
  'worklet';
  return s < 0.7 ? 1.3 - 0.36 * easeOut(s / 0.7) : 0.94 + 0.06 * easeOut((s - 0.7) / 0.3);
}
