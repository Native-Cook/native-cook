import { Easing } from 'react-native-reanimated';

// The pin is a still: the motion is ours, on the elements it shows (the carousel and the list → detail morph).

export const EASE_OUT = [0.23, 1, 0.32, 1] as const;
export const EASE_IN_OUT = [0.77, 0, 0.175, 1] as const;
export const easeOut = Easing.bezier(...EASE_OUT);
export const easeInOut = Easing.bezier(...EASE_IN_OUT);

/** Finger-driven snap of the carousel. */
export const SNAP = { duration: 400, dampingRatio: 0.8 };
/** Release projection (s) and rubber band past the ends. */
export const PROJECT_S = 0.2;
export const RUBBER = 0.3;

/** List → detail: `detail` runs linearly over these ms; each element takes its own window of it. */
export const OPEN_MS = 900;
export const CLOSE_MS = 600;
export const WINDOWS = {
  earth: [0, 0.62] as const,
  list: [0, 0.78] as const,
  backdrop: [0.08, 0.82] as const,
  back: [0.3, 0.6] as const,
  numeral: [0.36, 0.7] as const,
  name: [0.44, 0.78] as const,
  desc: [0.52, 0.86] as const,
  video: [0.6, 0.94] as const,
  rocket: [0.55, 1] as const,
};
/** Staggered elements rise this far and fade in. */
export const RISE = 12;
export const ROCKET_FROM = { x: -70, y: 90, rotate: -18 };

export const PRESS_SCALE = 0.97;
export const PRESS_IN_MS = 120;
export const PRESS_OUT_MS = 150;
export const REDUCED_FADE_MS = 200;
export const RESUME_AFTER_MS = 3500;

/** Atmosphere: halo diameter (x planet), where the colour starts fading, opacity at that point. */
export const GLOW = { scale: 1.25, inner: '0.68', opacity: 0.25 };

/** Parallax of the pin's sky layers per card of carousel travel (pt), and planet roll (deg per card). */
export const PARALLAX = { stars: 22, dots: 40, pad: 60 };
export const ROLL_DEG = 24;

/** Ambient float in the detail view. */
export const FLOAT = { periodMs: 4000, earth: 3, rocket: 5 };

/** Autoplay (ms). */
export const SCRIPT = {
  toVenus: [1200, 1800] as const,
  toEarth: [2600, 3200] as const,
  press: 3600,
  open: 3720,
  back: 8300,
  close: 8420,
};
export const LOOP_MS = 10200;

/**
 * Motion design (still pin): the planets have mass. The cards page on EASE_IN_OUT (light UI), the planets ride a
 * critically-damped-but-loose spring behind them, so they lag, overshoot a little and settle, rolling as they go.
 * One integrator serves autoplay and touch alike, so the loop and the finger feel the same.
 */
export const ORBIT_SPRING = { stiffness: 220, damping: 22 }; // per card; ζ 0.74, ~3 % overshoot, settles in ~0.4 s
/** Atmosphere swells with the planet's lag behind its card (scale at full lag), the orbit ring turns per card. */
export const SWELL = { gain: 3, max: 0.16 };
export const RING_DEG = -9;
/**
 * Card handover: travel direction from the planets' lag (gain), z lift of the incoming card, the outgoing card's
 * recede scale and shade at the crossing, the incoming card's lift, planet lead (incoming) and extra trail
 * (outgoing) as multiples of the lag, and the outgoing copy's fade (starts at 0.15 cover, gone by 0.4,
 * about 100 ms at full speed).
 */
export const HANDOVER = { dirGain: 8, zLift: 6, recede: 0.88, shade: 0.28, lift: 0.03, lead: 0.8, trail: 0.6, fadeFrom: 0.15, fadeSpan: 0.25 };
/** A step longer than this (ms) is a hitch; the spring takes several small steps instead. */
export const MAX_STEP_MS = 8;
