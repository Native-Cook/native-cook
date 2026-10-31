// Clay Prompt — the generation as a function of one number, and the autoPlay loop. Helpers first:
// worklets capture what is above them.

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
export function smooth(a: number, b: number, x: number) {
  'worklet';
  const u = clamp01((x - a) / (b - a));
  return u * u * (3 - 2 * u);
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
/** A soft spring's step response: 0 → 1 with one overshoot, flat at the start. */
export function springStep(x: number) {
  'worklet';
  if (x <= 0) return 0;
  return 1 - Math.exp(-6 * x) * (Math.cos(10 * x) + 0.6 * Math.sin(10 * x));
}
function backOut(x: number) {
  'worklet';
  const c = 1.45;
  const u = x - 1;
  return 1 + (c + 1) * u * u * u + c * u * u;
}

/* ------------------------------------------------------------ the generation, g = 0 … 1 */
// 0 → 0.42 the clay lifts off the composer and gathers in the card · 0.2 → 0.5 it melts into one
// blob · 0.5 → 0.8 the blob opens the picture and the clay comes back out, now the scene · → 1 settles.
export type Look = { lift: number; spread: number; k: number; pull: number; blobR: number; reveal: number; night: number };
export const MEET = 40; // size of each piece when they meet, pt

export function look(g: number): Look {
  'worklet';
  const lift = easeInOut(seg(g, 0, 0.42));
  const spread = g <= 0.5 ? 0 : backOut(seg(g, 0.5, 0.86));
  const k = 2 + 30 * smooth(0.12, 0.4, g) * (1 - smooth(0.52, 0.8, g));
  const pull = smooth(0.22, 0.46, g) * (1 - smooth(0.5, 0.64, g));
  const swell = 1 + 0.1 * Math.sin(Math.PI * seg(g, 0.4, 0.52));
  const blobR = 62 * smooth(0.25, 0.46, g) * (1 - smooth(0.5, 0.6, g)) * swell;
  const reveal = easeOut(seg(g, 0.5, 0.8));
  const night = smooth(0.55, 0.9, g);
  return { lift, spread, k, pull, blobR, reveal, night };
}

export type Pose = { x: number; y: number; size: number; squash: number };

/** Where one piece of clay is: on the composer (slot), in the blob (centre), or in the scene (comp). */
export function poseAt(g: number, slotX: number, slotY: number, slotSize: number, compX: number, compY: number, compSize: number, cx: number, cy: number): Pose {
  'worklet';
  const l = look(g);
  if (g <= 0.5) {
    const a = l.lift;
    return {
      x: slotX + (cx - slotX) * a,
      y: slotY + (cy - slotY) * a - 70 * Math.sin(Math.PI * a),
      size: slotSize + (MEET - slotSize) * a,
      squash: -0.12 * Math.sin(Math.PI * a) * (1 - a),
    };
  }
  const b = l.spread;
  return {
    x: cx + (compX - cx) * b,
    y: cy + (compY - cy) * b,
    size: MEET + (compSize - MEET) * b,
    squash: 0.08 * Math.sin(Math.PI * Math.min(1, b)) * (1 - seg(g, 0.75, 1)),
  };
}

/** The scene's composition inside the card, by kind; the cat sits on the sofa when there is one. */
export function composed(kind: number, hasSofa: boolean, cx: number, cy: number, cw: number, ch: number): [number, number, number] {
  'worklet';
  if (kind === 1) return [cx + 0.52 * cw, cy + 0.74 * ch, 0.27 * cw];
  if (kind === 0) return hasSofa ? [cx + 0.42 * cw, cy + 0.6 * ch, 0.14 * cw] : [cx + 0.46 * cw, cy + 0.72 * ch, 0.16 * cw];
  if (kind === 2) return [cx + 0.72 * cw, cy + 0.25 * ch, 0.125 * cw];
  return [cx + 0.14 * cw, cy + 0.74 * ch, 0.115 * cw];
}

/** x of slot i when n pieces sit on the composer, centred. */
export function slotX(i: number, n: number, w: number, gap: number) {
  'worklet';
  return w / 2 + (i - (n - 1) / 2) * gap;
}

/* ------------------------------------------------------------ autoPlay */
export const LOOP = 11;
export const CHAR = 0.085; // s per typed character
export const T = {
  type0: 0.4,
  tap: 3.3, // Generate
  gen0: 3.42,
  gen: 2.4, // s for g 0 → 1
  drag0: 7.2,
  drag1: 7.9,
  back: 0.7,
  clear0: 9.8,
  clear1: 10.5,
} as const;
// the nouns of the prompt, the character after which each is complete
export const WORD_ENDS = [5, 14, 30] as const; // cat, sofa, moon
export const PROMPT_KINDS = [0, 1, 2] as const;

export type Frame = {
  chars: number;
  pops: [number, number, number];
  g: number;
  clear: number; // 0 … 1, the screen wiping back to empty
  press: number; // Generate's press
  finger: number;
  fingerAt: number; // 0 Generate, 1 the card
  dragY: number; // the scripted finger's drag on the card, pt
};

export function popTime(i: number) {
  'worklet';
  return T.type0 + WORD_ENDS[i] * CHAR + 0.12;
}

export function frameAt(t: number): Frame {
  'worklet';
  const chars = Math.max(0, Math.min(30, Math.floor((t - T.type0) / CHAR) + 1));
  const pops: [number, number, number] = [0, 0, 0];
  for (let i = 0; i < 3; i++) pops[i] = springStep((t - popTime(i)) / 0.6);
  let g = seg(t, T.gen0, T.gen0 + T.gen);
  // the finger pulls the picture back down half way, and lets go
  const pullBack = 0.42 * easeInOut(seg(t, T.drag0, T.drag1));
  const release = t > T.drag1 ? springStep((t - T.drag1) / T.back) : 0;
  g -= pullBack * (1 - release);
  // hidden behind the wipe, the generation rewinds for the next loop
  g *= 1 - smooth(10.5, 10.95, t);
  const clear = easeInOut(seg(t, T.clear0, T.clear1));
  const press = easeOut(seg(t, T.tap, T.tap + 0.08)) * (1 - easeOut(seg(t, T.tap + 0.12, T.tap + 0.3)));
  let finger = 0;
  let fingerAt = 0;
  if (t < T.tap + 0.5) {
    finger = easeOut(seg(t, T.tap - 0.18, T.tap)) * (1 - easeOut(seg(t, T.tap + 0.14, T.tap + 0.32)));
  } else {
    fingerAt = 1;
    finger = easeOut(seg(t, T.drag0 - 0.18, T.drag0)) * (1 - easeOut(seg(t, T.drag1, T.drag1 + 0.18)));
  }
  const dragY = 130 * easeInOut(seg(t, T.drag0, T.drag1));
  return { chars, pops, g, clear, press, finger, fingerAt, dragY };
}
