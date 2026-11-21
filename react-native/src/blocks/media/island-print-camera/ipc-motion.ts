// Island Print Camera: the autoplay timeline (Fable spec §4) as a pure function of the loop time, so every frame of
// the loop is reproducible and the three stacks can match it frame for frame. Times are in seconds.

import { SPRING } from './ipc-theme';

export const LOOP = 11;

/** 0 before a, 1 after b. */
export const seg = (t: number, a: number, b: number) => {
  'worklet';
  return Math.min(Math.max((t - a) / (b - a), 0), 1);
};
export const clamp01 = (v: number) => {
  'worklet';
  return Math.min(Math.max(v, 0), 1);
};
export const mix = (a: number, b: number, k: number) => {
  'worklet';
  return a + (b - a) * k;
};
export const easeOut = (k: number) => {
  'worklet';
  return 1 - (1 - k) * (1 - k) * (1 - k);
};
export const easeIn = (k: number) => {
  'worklet';
  return k * k * k;
};
export const easeInQuad = (k: number) => {
  'worklet';
  return k * k;
};
export const easeInOut = (k: number) => {
  'worklet';
  return k < 0.5 ? 4 * k * k * k : 1 - Math.pow(-2 * k + 2, 3) / 2;
};

/**
 * A damped spring (mass 1) released at time 0 from `from` towards `to` with initial velocity v0 (units/s): its
 * position at time t. Closed form, so the loop stays a pure function of time.
 */
export const springAt = (t: number, from: number, to: number, v0: number, d: number, k: number) => {
  'worklet';
  if (t <= 0) return from;
  const x0 = from - to;
  const w0 = Math.sqrt(k);
  const z = d / (2 * w0);
  if (z < 1) {
    const wd = w0 * Math.sqrt(1 - z * z);
    const e = Math.exp(-z * w0 * t);
    return to + e * (x0 * Math.cos(wd * t) + ((v0 + z * w0 * x0) / wd) * Math.sin(wd * t));
  }
  if (z === 1) {
    const e = Math.exp(-w0 * t);
    return to + e * (x0 + (v0 + w0 * x0) * t);
  }
  const s = Math.sqrt(z * z - 1);
  const r1 = -w0 * (z - s);
  const r2 = -w0 * (z + s);
  const c2 = (v0 - r1 * x0) / (r2 - r1);
  const c1 = x0 - c2;
  return to + c1 * Math.exp(r1 * t) + c2 * Math.exp(r2 * t);
};

/** Everything the screen draws, for one moment. */
export type Frame = {
  p: number; // header open 0..1
  slot: number; // island widened into the print slot 0..1
  antic: number; // the pull's anticipation 0..1 (panel dips, island widens)
  slotDip: number; // motor start: slot's lower edge dips (pt)
  rise: [number, number, number]; // LED, shutter, flip: height 0..1 (overshoot allowed)
  flood: [number, number, number]; // colour flood 0..1
  depth: number; // shutter press 0..1
  exposure: number;
  blur: number; // live view blur (pt)
  focus: number; // focus-breathing scale
  glint: number; // glint progress 0..1 (0 = hidden)
  black: number; // shutter closed 0/1
  flash: number; // white over the lens 0..1
  bloom: number; // white bloom round the body
  dim: number; // control brightness factor
  led: number; // 0 off (olive), 1 focus lock (lime), 2 busy (red)
  ledHalo: number;
  f: number; // print feed 0..1
  printOut: number; // 0 while feeding, 1 once dropped (clip off)
  printTop: number; // print top edge (pt)
  printRot: number; // rotateZ (deg)
  printScaleY: number;
  printTilt: number; // rotateX (deg) during the feed
  d: number; // development 0..1
  sheen: number; // sheen sweep 0..1 (0 = hidden)
  empty: number; // empty-state alpha
  printShadow: number; // 0 rest (e2) .. 1 lifted (e4)
};

/** Rise of dome i for header progress p: [p0, p1] range, the last 15 % pops past 1 by up to 5 %. */
const domeRise = (p: number, a: number, b: number) => {
  'worklet';
  const k = seg(p, a, b);
  // a soft overshoot near the top of the range, back to exactly 1 at k = 1
  const over = k > 0.85 ? Math.sin(((k - 0.85) / 0.15) * Math.PI) * 0.05 : 0;
  return easeOut(k) + over;
};

const FEED_A = 4.85;
const FEED_B = 6.65;

/** The feed: 137 pt/s with a ±12 % ripple at ~8 Hz (14 whole cycles, so it lands exactly on 1). */
export const feedAt = (t: number) => {
  'worklet';
  const u = seg(t, FEED_A, FEED_B);
  return u + (0.12 / (2 * Math.PI * 14)) * Math.sin(2 * Math.PI * 14 * u);
};

/** The header progress p at loop time t. */
export const pAt = (t: number) => {
  'worklet';
  if (t < 0.7) return 0;
  if (t < 1.45) return 0.46 * easeInOut(seg(t, 0.7, 1.45));
  if (t < 1.7) return 0.46;
  if (t < 4.0) return Math.min(springAt(t - 1.7, 0.46, 1, 2.4, SPRING.glide.d, SPRING.glide.k), 1.02);
  if (t < 4.7) return 1 - easeInOut(seg(t, 4.0, 4.7));
  return 0;
};

export const frameAt = (tIn: number): Frame => {
  'worklet';
  const t = ((tIn % LOOP) + LOOP) % LOOP;
  const p = pAt(t);
  // the slot: on while the camera closes until the print has left it
  const slotOn = t >= 4.0 && t < 6.7 ? 1 : 0;
  const slot = t < 4.0 ? 0 : t < 6.7 ? easeInOut(seg(t, 4.0, 4.7)) : Math.max(0, springAt(t - 6.7, 1, 0, 0, SPRING.snap.d, SPRING.snap.k));
  const antic = easeOut(seg(t, 0.6, 0.7)) * (1 - seg(p, 0, 0.15)) * (t < 2 ? 1 : 0);
  const slotDip = slotOn ? 2 * easeOut(seg(t, 4.7, 4.85)) * (1 - seg(t, 6.6, 6.7)) + (t > 4.7 && t < 4.85 ? 0.5 * Math.sin(t * 2 * Math.PI * 30) : 0) : 0;

  // domes: rise with p while opening; on the close they deflate first (p 0.95 -> 0.45 over the first part)
  let pd = p;
  if (t >= 4.0 && t < 4.7) pd = Math.max(0, (p - 0.45) / 0.55);
  const rise: [number, number, number] = [domeRise(pd, 0.45, 0.85), domeRise(pd, 0.5, 0.9), domeRise(pd, 0.55, 0.95)];
  // colour floods over the last 40 % of each range
  const flood: [number, number, number] = [seg(pd, 0.69, 0.85), seg(pd, 0.74, 0.9), seg(pd, 0.79, 0.95)];

  // shutter: half-press, focus, fire, pop back
  let depth = 0;
  if (t >= 2.9 && t < 3.5) depth = Math.min(springAt(t - 2.9, 0, 0.45, 0, SPRING.snap.d, SPRING.snap.k), 0.5);
  else if (t >= 3.5 && t < 3.62) depth = mix(0.45, 1, easeIn(seg(t, 3.5, 3.58)));
  else if (t >= 3.62 && t < 4.0) depth = Math.max(0, springAt(t - 3.62, 1, 0, 0, SPRING.pop.d, SPRING.pop.k));

  // live view: auto-exposure overshoot, blur racking to focus, focus breathing
  // the live view comes up while the body is still opening, so the lens is never a dead black hole
  const exposure = t < 0.9 ? 0 : t < 1.9 ? mix(0, 1.15, easeOut(seg(t, 0.9, 1.9))) : t < 2.3 ? mix(1.15, 1, easeInOut(seg(t, 1.9, 2.3))) : 1;
  const blur = t < 1.5 ? 12 : t < 2.3 ? mix(12, 1.5, easeOut(seg(t, 1.5, 2.3))) : t < 2.9 ? 1.5 : t < 3.4 ? mix(1.5, 0, easeInOut(seg(t, 2.9, 3.4))) : 0;
  const fk = seg(t, 2.9, 3.4);
  const focus = 1 + 0.03 * Math.sin(Math.PI * easeInOut(fk));
  const glint = t >= 1.9 && t < 2.5 ? easeInOut(seg(t, 1.9, 2.5)) : 0;
  const black = t >= 3.5 && t < 3.56 ? 1 : 0;
  const flash = t >= 3.56 && t < 3.8 ? 0.9 * (1 - easeOut(seg(t, 3.56, 3.8))) : 0;
  const bloom = t >= 3.56 && t < 3.8 ? 0.25 * (1 - easeOut(seg(t, 3.56, 3.8))) : 0;
  const dim = t >= 3.56 && t < 4.1 ? 1 - 0.18 * Math.sin(Math.PI * seg(t, 3.56, 4.1)) : 1;
  const led = t >= 3.5 && t < 7.1 ? 2 : t >= 3.4 && t < 3.5 ? 1 : 0;
  const ledHalo = led === 1 ? 0.18 : led === 2 ? 0.18 * seg(t, 3.5, 3.65) * (1 - seg(t, 7.0, 7.1)) : 0;

  // the print
  const f = t < FEED_A ? 0 : t < FEED_B ? feedAt(t) : 1;
  let printTop = 48 - 247 + 247 * f;
  let printRot = 0;
  let printScaleY = 1;
  let printOut = 0;
  let printShadow = 0;
  if (t >= 6.65 && t < 6.91) {
    printTop = mix(48, 152, easeInQuad(seg(t, 6.65, 6.91)));
    printOut = 1;
    printShadow = 1;
  } else if (t >= 6.91 && t < 9.2) {
    printTop = 152;
    printOut = 1;
    const s = t - 6.91;
    printScaleY = Math.min(1, springAt(s, 0.985, 1, 0, SPRING.pop.d, SPRING.pop.k));
    printRot = springAt(s, 2.5, 0, -40, SPRING.pop.d, SPRING.pop.k);
    printShadow = 1 - seg(t, 6.91, 7.3);
  } else if (t >= 9.2 && t < 9.8) {
    const k = easeIn(seg(t, 9.2, 9.8));
    printTop = mix(152, 890, k);
    printRot = 6 * k;
    printOut = 1;
  } else if (t >= 9.8) {
    printTop = 890;
    printOut = 1;
  }
  const printTilt = t < FEED_B ? 38 * (1 - f) : 0;
  // development runs evenly from 5.9 s to 8.8 s: the print lands (6.91 s) still a cyan-grey ghost (d 0.35)
  const d = t < 5.9 ? 0 : t < 8.8 ? seg(t, 5.9, 8.8) : t < 9.8 ? 1 : 0;
  const sheen = t >= 8.6 && t < 9.1 ? easeInOut(seg(t, 8.6, 9.1)) : 0;
  const empty = t < 6.65 ? 1 : t < 6.9 ? 1 - seg(t, 6.65, 6.9) : t < 9.5 ? 0 : easeOut(seg(t, 9.5, 10.0));

  return {
    p, slot, antic, slotDip, rise, flood, depth, exposure, blur, focus, glint, black, flash, bloom, dim, led, ledHalo,
    f, printOut, printTop, printRot, printScaleY, printTilt, d, sheen, empty, printShadow,
  };
};

/**
 * The frame under the finger (no sequence running): the header follows p, the shutter its press depth, focus locks
 * while the shutter is half-pressed.
 */
export const liveFrameAt = (p: number, depth: number, focusLock: number): Frame => {
  'worklet';
  const f0 = frameAt(10.5);
  const q = Math.min(Math.max(p, 0), 1.02);
  const open = seg(q, 0.35, 1);
  return {
    ...f0,
    p: q,
    rise: [domeRise(q, 0.45, 0.85), domeRise(q, 0.5, 0.9), domeRise(q, 0.55, 0.95)],
    flood: [seg(q, 0.69, 0.85), seg(q, 0.74, 0.9), seg(q, 0.79, 0.95)],
    depth,
    exposure: open,
    blur: mix(mix(12, 1.5, open), 0, focusLock),
    focus: 1 + 0.03 * Math.sin(Math.PI * Math.min(focusLock, 1)),
    led: focusLock > 0.95 ? 1 : 0,
    ledHalo: focusLock > 0.95 ? 0.18 : 0,
  };
};
