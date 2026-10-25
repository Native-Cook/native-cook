/**
 * Tree Ring Chart — data and the autoPlay script. Every value on screen is a closed-form function of
 * the loop time t (seconds), so the frame at t = LOOP equals the frame at t = 0.
 *
 * Every helper is a module-level named worklet declared above its callers (UI-thread safe).
 */

export const LOOP = 9;

/** One ring per year of saving; a ring's width is that year's savings. */
export const YEARS = [2019, 2020, 2021, 2022, 2023, 2024, 2025];
export const AMOUNTS = [3120, 4860, 6240, 5480, 8420, 7150, 9380];
export const RING_COUNT = 7;
export const TOTAL = AMOUNTS.reduce((a, b) => a + b, 0); // 44,650

/** The ring selected at rest (the poster frame): 2021. */
export const REST_SEL = 2;

// Script (seconds).
const SHRINK_AT = 1.4; // rings shrink back to the pith, outermost first, 0.08 s apart, 0.5 s each
const SHRINK_STAGGER = 0.08;
const SHRINK_FOR = 0.5;
const GROW_AT = 2.5; // then regrow year by year until 6.4 (fully shrunk for 0.12 s only)
const GROW_STEP = 3.9 / 7; // 0.557 s between rings
const GROW_FOR = 0.62; // each ring arrives with a back-out swell (about 7 % past its width)
const PICK_AT = 6.4; // the selection travels back from 2025 to 2021
const PICK_FOR = 1.0;

export function clamp01(x: number) {
  'worklet';
  return Math.min(Math.max(x, 0), 1);
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeInOut(x: number) {
  'worklet';
  const t = clamp01(x);
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

export function easeOut(x: number) {
  'worklet';
  const t = clamp01(x);
  return 1 - Math.pow(1 - t, 3);
}

/** Back-out: rises past 1 (about 7 %) and settles at exactly 1. */
export function backOut(x: number) {
  'worklet';
  const t = clamp01(x);
  const c = 1.2;
  return 1 + (c + 1) * Math.pow(t - 1, 3) + c * Math.pow(t - 1, 2);
}

/** The pose the shader and the labels read: ring growth (0 = not there, 1 = full width), the
 * selected ring (continuous, so the glow and the labels travel between rings) and the glow amount. */
export type TreePose = { g: number[]; sel: number; glow: number };

export function loopPose(t: number): TreePose {
  'worklet';
  const g: number[] = [];
  for (let i = 0; i < RING_COUNT; i++) {
    if (t < GROW_AT) {
      const a = SHRINK_AT + (RING_COUNT - 1 - i) * SHRINK_STAGGER;
      g.push(1 - easeInOut(seg(t, a, a + SHRINK_FOR)));
    } else {
      const a = GROW_AT + i * GROW_STEP;
      g.push(backOut(seg(t, a, a + GROW_FOR)));
    }
  }
  let sel = REST_SEL;
  if (t >= SHRINK_AT && t < GROW_AT) {
    sel = REST_SEL * (1 - easeInOut(seg(t, SHRINK_AT, SHRINK_AT + 0.8)));
  } else if (t >= GROW_AT && t < PICK_AT) {
    sel = 0;
    for (let i = 1; i < RING_COUNT; i++) {
      const a = GROW_AT + i * GROW_STEP + 0.05;
      sel += easeInOut(seg(t, a, a + 0.32));
    }
  } else if (t >= PICK_AT) {
    sel = RING_COUNT - 1 - (RING_COUNT - 1 - REST_SEL) * easeInOut(seg(t, PICK_AT, PICK_AT + PICK_FOR));
  }
  // Full glow at rest; it dims while the rings shrink and regrow, and comes back as 2021 is picked.
  const dim = easeInOut(seg(t, SHRINK_AT, SHRINK_AT + 0.4)) * (1 - easeInOut(seg(t, PICK_AT, PICK_AT + 0.5)));
  return { g, sel, glow: 1 - 0.45 * dim };
}

/** The finger's pose: s = how far out the slice has grown (0 = pith only, 7 = all rings). The ring
 * the finger is in swells a little while it grows. */
export function touchPose(s: number, sel: number): TreePose {
  'worklet';
  const g: number[] = [];
  for (let i = 0; i < RING_COUNT; i++) {
    const u = clamp01(s - i);
    g.push(u * (1 + 0.07 * Math.sin(Math.PI * u)));
  }
  return { g, sel, glow: 1 };
}

/** How grown the slice is, as the finger's s (used when a finger takes over the loop). */
export function grownOf(p: TreePose) {
  'worklet';
  let s = 0;
  for (let i = 0; i < RING_COUNT; i++) s += clamp01(p.g[i]);
  return s;
}

/** The savings shown by the rolling total: the grown share of every ring. */
export function totalOf(p: TreePose) {
  'worklet';
  let v = 0;
  for (let i = 0; i < RING_COUNT; i++) v += AMOUNTS[i] * clamp01(p.g[i]);
  return v;
}

/** Ring geometry: the slice's wood radius `wood`, the pith radius and every ring's outer radius. */
export function ringRadii(p: TreePose, pith: number, wood: number): number[] {
  'worklet';
  const out: number[] = [];
  let r = pith;
  for (let i = 0; i < RING_COUNT; i++) {
    r += ((wood - pith) * AMOUNTS[i] * Math.max(0, p.g[i])) / TOTAL;
    out.push(r);
  }
  return out;
}

/** Full-grown outer radius of each ring (for the guide circles and for mapping the finger). */
export function fullRadii(pith: number, wood: number): number[] {
  'worklet';
  const out: number[] = [];
  let r = pith;
  for (let i = 0; i < RING_COUNT; i++) {
    r += ((wood - pith) * AMOUNTS[i]) / TOTAL;
    out.push(r);
  }
  return out;
}

/** The finger's distance from the pith, as s (0 at the pith, i + fraction inside ring i). */
export function sOfRadius(r: number, pith: number, wood: number) {
  'worklet';
  const full = fullRadii(pith, wood);
  let prev = pith;
  for (let i = 0; i < RING_COUNT; i++) {
    if (r < full[i]) return i + clamp01((r - prev) / (full[i] - prev));
    prev = full[i];
  }
  // Past the bark: rubber-band a little beyond 7.
  const over = (r - full[RING_COUNT - 1]) / 40;
  return RING_COUNT + (0.25 * over) / (1 + over);
}

/** Per-year copy for the rolling labels. */
export function money(v: number) {
  return '$' + Math.round(v).toLocaleString('en-US');
}

export const LABELS = YEARS.map((y, i) => `${y} · ${money(AMOUNTS[i])}`);
export const DELTAS = YEARS.map((_, i) => {
  if (i === 0) return 'First ring · the account opens';
  const d = Math.round(((AMOUNTS[i] - AMOUNTS[i - 1]) / AMOUNTS[i - 1]) * 100);
  return `${d >= 0 ? '+' : '−'}${Math.abs(d)}% on ${YEARS[i - 1]}`;
});
