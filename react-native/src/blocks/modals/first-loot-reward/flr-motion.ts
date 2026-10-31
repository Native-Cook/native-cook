// First Loot Reward — the loop's timeline and the chest's states. Pure functions of time.
// Worklet rule: helpers sit ABOVE their callers.

// The loop is the reference recording's cycle: the reveal from black (0–1.8 s, its timings), the
// finished screen resting, then a short fade back to black so the loop closes.
export const LOOP = 4;
/** Loop time at which the reveal (the reference recording's t = 0) starts. */
export const INTRO = 0;
/** The fade to black at the end of the loop. */
export const FADE = [3.55, 3.92] as const;

export const clamp01 = (v: number) => {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
};
export const seg = (t: number, a: number, b: number) => {
  'worklet';
  return clamp01((t - a) / (b - a));
};
export const mix = (a: number, b: number, k: number) => {
  'worklet';
  return a + (b - a) * k;
};
export const smooth = (k: number) => {
  'worklet';
  const x = clamp01(k);
  return x * x * (3 - 2 * x);
};
export const easeOut = (k: number) => {
  'worklet';
  const x = clamp01(k);
  return 1 - (1 - x) * (1 - x) * (1 - x);
};
export const easeIn = (k: number) => {
  'worklet';
  const x = clamp01(k);
  return x * x * x;
};
export const easeInOut = (k: number) => {
  'worklet';
  const x = clamp01(k);
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
};
export const easeOutBack = (k: number, c1 = 1.70158) => {
  'worklet';
  const x = clamp01(k);
  const c3 = c1 + 1;
  return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
};
/** A damped spring step response 0 → 1 (`zeta` damping ratio, `f` cycles per second). */
export const springTo = (s: number, f = 2.4, zeta = 0.55) => {
  'worklet';
  if (s <= 0) return 0;
  const w = 2 * Math.PI * f;
  const wd = w * Math.sqrt(1 - zeta * zeta);
  return 1 - Math.exp(-zeta * w * s) * (Math.cos(wd * s) + ((zeta * w) / wd) * Math.sin(wd * s));
};
export const hash = (i: number) => {
  'worklet';
  const s = Math.sin(i * 12.9898 + 78.233) * 43758.5453;
  return s - Math.floor(s);
};

/** Seconds since the reveal began (negative before it, large once it has settled). */
export const introU = (t: number) => {
  'worklet';
  return t - INTRO;
};

/** Presence 0…1 of an element: leaves during [out, out + dur], comes back `inAt` s into the reveal. */
export const presence = (t: number, out: number, dur: number, inAt: number, inDur: number) => {
  'worklet';
  void out;
  void dur;
  return easeOut(seg(t - INTRO, inAt, inAt + inDur));
};

// The reveal, measured from the reference at 30 fps (seconds): a thin ring at 0, fading 0.12–0.24;
// the hexagon fades in 0.05–0.23 while it grows 0.6 → 1.03 (0.3 s) → 1; sparkles pop from 0.45;
// the chest gathers 0.5–0.75, its lid flies open at 0.75 in a bloom and a travelling ring, motes
// rise, the lid comes down 1.43–1.63; the title fades up 6 pt 1.07–1.45, the line 1.2–1.5, the card
// 1.33–1.63, Keep playing 1.47–1.67 and View loot 1.55–1.8 rise 10 pt.
export const R = {
  ring: [0.12, 0.24] as const,
  grow: [0.05, 0.34] as const,
  fade: [0.035, 0.22] as const,
  sparkle: 0.45,
  gather: 0.48,
  open: 0.73,
  shut: 1.43,
  title: 1.07,
  sub: 1.2,
  card: 1.33,
  keep: 1.47,
  view: 1.55,
  shimmer: [99, 99] as const,
} as const;

/** Kept for the copy's exit timings (unused: the loop fades to black instead). */
export const X = {
  buttons: 9,
  card: 9,
  sub: 9,
  title: 9,
  badge: 9,
  dur: 0.27,
  scene: [9, 9.1] as const,
} as const;

export type Chest = {
  gather: number; // 0…1 anticipation squash
  lid: number; // 0 shut … 1 open (overshoots)
  light: number; // light pouring out of the open chest
  since: number; // seconds since it burst open (-1 never)
  bump: number; // squash when the lid lands
};

/** The chest at `a` seconds into its own sequence (gather 0–0.25, open 0.25, shut 0.93–1.13). */
export const chestAt = (a: number): Chest => {
  'worklet';
  if (a < 0) return { gather: 0, lid: 0, light: 0, since: -1, bump: 0 };
  const g = a < 0.25 ? smooth(a / 0.25) : 1 - smooth((a - 0.25) / 0.06);
  const o = a - 0.25;
  let lid = 0;
  if (o >= 0) lid = easeOut(seg(o, 0, 0.07)) * (1 - smooth(seg(o, 0.68, 0.88)));
  const land = o - 0.88;
  const bump = land >= 0 && land < 0.3 ? Math.sin(Math.min(land / 0.1, 1) * Math.PI) * Math.exp(-land * 8) * 0.6 : 0;
  const light = o >= 0 ? smooth(o / 0.05) * (1 - smooth(seg(o, 0.55, 0.88))) : 0;
  return { gather: g, lid, light, since: o, bump };
};
