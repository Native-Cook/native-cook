// The loop as pure functions of time. Every stack reads these numbers.
export const LOOP = 9;
export const RESUME_AFTER = 2;

export const MORPH_A = [0.95, 2.35] as const; // original -> Shorter
export const SCRUB_BACK = [3.55, 4.35] as const; // the ghost drags the bar back to the original
export const TONE_TO_WARMER = [4.55, 4.8] as const; // chip indicator moves (text is on the original here)
export const MORPH_B = [4.85, 6.25] as const; // original -> Warmer
export const MORPH_BACK = [7.05, 7.85] as const; // Warmer -> original
export const TONE_TO_FIRST = [7.95, 8.2] as const;

export const GLYPH = {
  spread: 0.34, // share of the morph used by the reading-order stagger
  letterLag: 0.008, // delay added per letter inside a word
  arcMin: 5,
  arcMax: 22,
  arcPerPoint: 0.09,
  outEnd: 0.34, // a removed glyph is gone this far into its window
  outStagger: 0.25,
  drop: 10,
  inStart: 0.52,
  inStagger: 0.2,
  inLength: 0.28,
  rise: 7,
} as const;

export const SPRING = { stiffness: 170, damping: 22.2 } as const; // response ~0.48 s, damping ratio 0.85

export function seg(t: number, a: number, b: number) {
  'worklet';
  return Math.min(Math.max((t - a) / (b - a), 0), 1);
}

export function mix(a: number, b: number, k: number) {
  'worklet';
  return a + (b - a) * k;
}

export function easeInOutCubic(k: number) {
  'worklet';
  return k < 0.5 ? 4 * k * k * k : 1 - (-2 * k + 2) ** 3 / 2;
}

export function easeInOutSine(k: number) {
  'worklet';
  return -(Math.cos(Math.PI * k) - 1) / 2;
}

export function easeOutCubic(k: number) {
  'worklet';
  return 1 - (1 - k) ** 3;
}

/** Morph amount 0 (original) to 1 (rewrite). Linear in the morphs: each glyph applies its own ease. */
export function progressAt(t: number) {
  'worklet';
  if (t < SCRUB_BACK[0]) return seg(t, MORPH_A[0], MORPH_A[1]);
  if (t < MORPH_B[0]) return 1 - easeInOutSine(seg(t, SCRUB_BACK[0], SCRUB_BACK[1]));
  if (t < MORPH_BACK[0]) return seg(t, MORPH_B[0], MORPH_B[1]);
  return 1 - seg(t, MORPH_BACK[0], MORPH_BACK[1]);
}

/** Which rewrite the text is morphing to. It only changes while the morph is at 0. */
export function toneAt(t: number) {
  'worklet';
  return t >= TONE_TO_WARMER[0] && t < TONE_TO_FIRST[0] ? 1 : 0;
}

export function toneIndicatorAt(t: number) {
  'worklet';
  return easeInOutCubic(seg(t, TONE_TO_WARMER[0], TONE_TO_WARMER[1])) * (1 - easeInOutCubic(seg(t, TONE_TO_FIRST[0], TONE_TO_FIRST[1])));
}

export function ghostOpacityAt(t: number) {
  'worklet';
  const chipA = seg(t, 0.6, 0.78) * (1 - seg(t, 1.05, 1.25));
  const bar = seg(t, 3.15, 3.38) * (1 - seg(t, 4.3, 4.48));
  const chipB = seg(t, 4.5, 4.66) * (1 - seg(t, 4.95, 5.15));
  return Math.max(chipA, bar, chipB);
}

/** 0 = hovering, 1 = pressed. */
export function ghostPressAt(t: number) {
  'worklet';
  const chipA = seg(t, 0.82, 0.92) * (1 - seg(t, 0.98, 1.1));
  const bar = seg(t, 3.4, 3.52) * (1 - seg(t, 4.3, 4.42));
  const chipB = seg(t, 4.7, 4.8) * (1 - seg(t, 4.86, 4.98));
  return Math.max(chipA, bar, chipB);
}

/** The bar past its ends gives way like a rubber band. */
export function rubber(value: number) {
  'worklet';
  if (value < 0) return value / (1 - value * 3) * 0.4;
  if (value > 1) {
    const over = value - 1;
    return 1 + (over / (1 + over * 3)) * 0.4;
  }
  return value;
}
