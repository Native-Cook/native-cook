// Timeline and card poses for the Card Catalog List. Everything here runs on the UI thread, so
// every helper is a named worklet declared above the functions that call it.

/** Loop period in seconds. */
export const LOOP = 9;

/** The card the demo thumbs to (Gaskell). */
export const TARGET = 14;

// Pose of a card relative to the reading line, in degrees and points.
/** How far a standing card behind the front one leans back. */
export const BACK_LEAN = 15;
/** How far a thumbed card falls forward over the drawer's lip. */
export const FORWARD_LEAN = 104;
/** Each card further back stands this much higher on screen (the drawer seen from above). */
export const STEP_UP = 13;
/** Cards further than this behind the front card fade out; further than VISIBLE_FRONT in front, too. */
export const VISIBLE_BACK = 7;
export const VISIBLE_FRONT = 4;
/** Drag distance that moves the drawer by one card, in points. */
export const PT_PER_CARD = 58;

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function easeOutCubic(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

/**
 * Where the drawer stands at loop time t, in cards (0 = first card at the front).
 * Rest 0–1.4 s; thumb forward 1.4–4.6 s, speeding up then slowing onto Gaskell with a small
 * overshoot that the next card's lean catches; hold 4.6–5.8 s; thumb back 5.8–7.6 s; rest.
 */
export function autoPosition(t: number) {
  'worklet';
  const forward = easeInOutCubic(seg(t, 1.4, 4.35));
  // A quarter-card past the target and back: the thumb lets the cards settle.
  const settle = Math.sin(Math.PI * seg(t, 4.1, 4.8)) * 0.24 * (1 - seg(t, 4.1, 4.8));
  const back = easeInOutCubic(seg(t, 5.8, 7.6));
  return TARGET * forward * (1 - back) + settle;
}

export type CardPose = { rot: number; ty: number; scale: number; shade: number; opacity: number; back: number };

/**
 * The pose of a card that is d cards behind the reading line (d < 0: already thumbed, lying
 * forward over the lip). `lean` adds a velocity-driven extra lean to the standing cards, so a fast
 * thumb fans them back and they settle when it stops.
 */
export function cardPose(d: number, lean: number): CardPose {
  'worklet';
  if (d >= 0) {
    const first = clamp01(d);
    const rot = (BACK_LEAN + lean) * first + 1.2 * Math.min(Math.max(d - 1, 0), 6);
    const ty = -STEP_UP * d;
    const scale = 1 - 0.012 * Math.min(d, VISIBLE_BACK + 1);
    const shade = Math.min(0.07 * d, 0.45);
    const opacity = 1 - clamp01(d - (VISIBLE_BACK - 1));
    return { rot, ty, scale, shade, opacity, back: 0 };
  }
  const e = clamp01(-d);
  // The card tips slowly at first, then drops over the lip: an ease-in-out on the angle.
  const rot = -FORWARD_LEAN * easeInOutCubic(e) - 4 * Math.min(-d - 1 < 0 ? 0 : -d - 1, 3);
  const opacity = 1 - clamp01(-d - (VISIBLE_FRONT - 1));
  // Past the vertical the reader sees the card's back.
  const back = rot < -90 ? 1 : 0;
  return { rot, ty: 0, scale: 1, shade: 0.12 * e, opacity, back };
}

/** Soft limits: past either end the drawer gives way slowly. */
export function rubberBand(s: number, max: number) {
  'worklet';
  if (s < 0) return -(-s / (1 + -s / 0.9)) * 0.55;
  if (s > max) {
    const over = s - max;
    return max + (over / (1 + over / 0.9)) * 0.55;
  }
  return s;
}
