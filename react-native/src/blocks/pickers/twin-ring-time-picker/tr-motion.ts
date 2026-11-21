// Twin Ring Time Picker: tokens and the 9 s autoplay timeline (Fable spec §4) as pure functions of loop time.

export const LOOP = 9;

export const C = {
  page: '#EEEDEB',
  disc: '#E2E2E2',
  ring: '#D4D4D4',
  ringHeld: '#CACACA',
  track: '#D6D6D6',
  ink: '#0B0B0B',
  k1: '#A8A8A8',
  k2: '#C9C9C9',
  k3: '#DDDDDD',
  seam: '#C6C6C6',
  pinion: '#F5B400',
  pinionDot: '#C98F00',
  tick: '#B8B8B8',
  rimFlash: '#BEBEBE',
  pageWarm: '#F3ECDF',
  pinionPm: '#F29A1E',
  pinionTop: '#FFD24A',
  cta: '#C93C00',
  ctaPressed: '#B23500',
  ctaInk: '#FFFBF8',
} as const;

/** Geometry on the 402 × 874 pt canvas. */
export const G = {
  wheelY: 495,
  leftCx: -44,
  rightCx: 446,
  ringR: 226, // centre line of the 38 pt band (207 … 245)
  ringW: 38,
  hourR: 155,
  minR: 150,
  step: 18, // degrees per value
  open: 36,
  pinionY: 442,
  colonY: [489, 506],
  ampmX: 340,
  ampmY: 487,
  cta: { x: 27, y: 751, w: 349, h: 64, r: 22 },
  back: { x: 23, y: 67, d: 44 },
  track: { x: 111, y: 83, w: 180, h: 12 },
  fill: [41, 64],
  title: 162,
} as const;

export const SPRING = {
  detent: { d: 40.4, k: 503.5 },
  pop: { d: 19, k: 195 },
  weighty: { d: 15.1, k: 93.4 },
  snap: { d: 30.5, k: 322.3 },
  glide: { d: 22.1, k: 157.9 },
} as const;

export const seg = (t: number, a: number, b: number) => {
  'worklet';
  return Math.min(Math.max((t - a) / (b - a), 0), 1);
};
export const mix = (a: number, b: number, k: number) => {
  'worklet';
  return a + (b - a) * k;
};
export const easeIn = (k: number) => {
  'worklet';
  return k * k * k;
};
export const easeOut = (k: number) => {
  'worklet';
  return 1 - (1 - k) * (1 - k) * (1 - k);
};
export const easeInOut = (k: number) => {
  'worklet';
  return k < 0.5 ? 4 * k * k * k : 1 - Math.pow(-2 * k + 2, 3) / 2;
};
/** emphasized: fast out of the start, long settle */
export const emph = (k: number) => {
  'worklet';
  return 1 - Math.pow(1 - k, 4);
};

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
  const e = Math.exp(-w0 * t);
  return to + e * (x0 + (v0 + w0 * x0) * t);
};

/** The minute flick: a decaying spin from 5.85 to 12 over 1.4 s, hesitating into each detent (cogging). */
const flick = (s: number) => {
  'worklet';
  const D = 1.4;
  const a = 3.0;
  const k = Math.min(s, D);
  const raw = 5.85 + 6.15 * ((1 - Math.exp(-a * k)) / (1 - Math.exp(-a * D)));
  // cogging: slow into each whole value while still fast, gone at the end
  const speedK = Math.exp(-a * k);
  return raw - (0.22 / (2 * Math.PI)) * Math.sin(2 * Math.PI * raw) * speedK;
};

export type TrFrame = { h: number; m: number; open: number; fill: number; cta: number; pm: number; kick: number; flash: number; hSpeed: number; mSpeed: number };

const hAt = (t: number) => {
  'worklet';
  if (t < 1.85) return 8;
  if (t < 3.0) return springAt(t - 1.85, 8, 9, 0, SPRING.weighty.d, SPRING.weighty.k);
  if (t < 3.7) return mix(9, 11, easeInOut(seg(t, 3.0, 3.7)));
  if (t < 3.85) return 11;
  if (t < 4.55) return mix(11, 13, easeInOut(seg(t, 3.85, 4.55)));
  if (t < 7.15) return 13;
  if (t < 8.45) return mix(13, 8, easeInOut(seg(t, 7.15, 8.45)));
  return 8;
};

const mAt = (t: number) => {
  'worklet';
  if (t < 0.6) return 6;
  if (t < 0.7) return mix(6, 5.85, easeIn(seg(t, 0.6, 0.7)));
  if (t < 2.1) return flick(t - 0.7);
  if (t < 7.0) return 12;
  if (t < 8.3) return mix(12, 6, easeInOut(seg(t, 7.0, 8.3)));
  return 6;
};

export const trFrameAt = (tIn: number): TrFrame => {
  'worklet';
  const t = ((tIn % LOOP) + LOOP) % LOOP;
  const h = hAt(t);
  const m = mAt(t);
  const dt = 1 / 120;
  const hSpeed = Math.abs(hAt(Math.min(t + dt, LOOP - 0.001)) - h) / dt;
  const mSpeed = Math.abs(mAt(Math.min(t + dt, LOOP - 0.001)) - m) / dt;
  let open = 0;
  if (t >= 5.4 && t < 7.0) open = Math.min(springAt(t - 5.4, 0, 1, 0, SPRING.glide.d, SPRING.glide.k), 1.04);
  else if (t >= 7.0 && t < 8.3) open = 1 - easeInOut(seg(t, 7.0, 8.3));
  const fill = t < 5.6 ? 0 : t < 6.3 ? easeOut(seg(t, 5.6, 6.3)) : t < 7.0 ? 1 : t < 8.3 ? 1 - easeInOut(seg(t, 7.0, 8.3)) : 0;
  const cta = t >= 5.3 && t < 5.4 ? 1 - 0.03 * easeIn(seg(t, 5.3, 5.4)) : t >= 5.4 && t < 5.8 ? springAt(t - 5.4, 0.97, 1, 0, SPRING.pop.d, SPRING.pop.k) : 1;
  const kick = t >= 1.85 && t < 2.3 ? Math.sin(Math.PI * seg(t, 1.85, 2.25)) : 0;
  // the carry's mesh flash on both rims: 120 ms
  const flash = t >= 1.85 && t < 1.97 ? 1 - seg(t, 1.85, 1.97) : 0;
  return { h, m, open, fill, cta, pm: 0, kick, flash, hSpeed, mSpeed };
};
