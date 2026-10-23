// Timings stepped frame by frame from the pin's video (30 fps), then rounded to the nearest 10 ms.

export const PRESS_LIFT = 63; // the held card sinks this far to make room for "Drag to send"
export const OPEN_DRAG = 36; // an upward drag past this (or a flick) sends the card into the island
export const FLICK_VELOCITY = 650;

export const DURATION = {
  press: 450, // card sinks while the tab grows (0.80 s → 1.25 s in the video)
  arm: 280, // tab and hint
  fall: 285, // card drops out under the growing sheet at a near-constant ~3,000 pt/s (pin 9.15 s → 9.43 s)
  open: 250, // the bloom's time table (geometry.ts): 9.15 s → 9.40 s
  openDelay: 0, // the bloom leaves the tab on the same frame the card lets go
  close: 290, // the fold's time table (geometry.ts): 10.41 s → 10.70 s
  returnDelay: 0, // the card starts back up as the sheet starts to fold
  tabOut: 250, // the tab and the hint go once the card has passed the island
  tabFade: 100,
  release: 360, // card let go without sending
  fade: 200, // Reduce Motion cross-fades
} as const;

/**
 * The card's way back, measured on the pin (lift in pt, ms after the sheet starts to fold): it shoots up from
 * below the screen at ~3,000 pt/s and brakes, then a spring takes it from 14 pt at -750 pt/s, overshoots by
 * 24 pt and settles (spring fitted to 10.73-11.10 s, rms 1.3 pt).
 */
export const RETURN_T = [0, 57, 187, 220, 253, 287, 320];
export const RETURN_LIFT = [800, 620, 264, 196, 97, 37, 14];
export const RETURN_MS = 320;
export const SETTLE_SPRING = { mass: 1, stiffness: 120, damping: 15, velocity: -750 } as const;

/** Share of the measured approach covered at u (0..1), for use as a timing easing. */
export function easeReturn(u: number) {
  'worklet';
  const t = u * RETURN_MS;
  let lift = RETURN_LIFT[RETURN_LIFT.length - 1];
  for (let i = 1; i < RETURN_T.length; i++) {
    if (t <= RETURN_T[i]) {
      const k = (t - RETURN_T[i - 1]) / (RETURN_T[i] - RETURN_T[i - 1]);
      lift = RETURN_LIFT[i - 1] + (RETURN_LIFT[i] - RETURN_LIFT[i - 1]) * k;
      break;
    }
  }
  return (RETURN_LIFT[0] - lift) / (RETURN_LIFT[0] - RETURN_LIFT[RETURN_LIFT.length - 1]);
}

/** Closed form of SETTLE_SPRING from 14 pt at -750 pt/s, t in ms. */
export function springSettle(t: number) {
  'worklet';
  const { mass, stiffness, damping, velocity } = SETTLE_SPRING;
  const x0 = RETURN_LIFT[RETURN_LIFT.length - 1];
  const w = Math.sqrt(stiffness / mass);
  const z = damping / (2 * Math.sqrt(stiffness * mass));
  const wd = w * Math.sqrt(1 - z * z);
  const s = t / 1000;
  return Math.exp(-z * w * s) * (x0 * Math.cos(wd * s) + ((velocity + z * w * x0) / wd) * Math.sin(wd * s));
}

/** "Drag to send" trails the card as it comes back (pin: 40 pt above the returning card instead of 117). */
export const HINT_LAG = 25; // ms, scripted path
export const HINT_FOLLOW = { stiffness: 900, damping: 60 } as const; // touch path

export const LOOP_MS = 10000;
export const RESUME_AFTER_MS = 3500;

/** Autoplay script, one clock (ms from the loop's start). */
export const SCRIPT = {
  press: 1500,
  open: 3300,
  close: 7300,
} as const;

// Curves picked against the video: the card sinks and rises with an ease-out, falls away accelerating,
// the island blooms fast and lands softly, and folds back with an ease-in-out.
export function easeOutCubic(u: number) {
  'worklet';
  const v = 1 - u;
  return 1 - v * v * v;
}

export function easeInOutCubic(u: number) {
  'worklet';
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}

/** The fall: constant speed, as measured (113, 197, 312, 407 pt of lift on consecutive frames). */
export function easeFall(u: number) {
  'worklet';
  return u;
}


function seg(t: number, start: number, duration: number) {
  'worklet';
  return Math.min(1, Math.max(0, (t - start) / duration));
}

export type Pose = { lift: number; arm: number; sheet: number };

/**
 * The whole loop as a pure function of time, so the autoplay is deterministic and seamless:
 * rest → press (card sinks, island grows a tab) → hold → send (card drops, island blooms into the sheet)
 * → sheet → swipe up (sheet folds back into the island, card rises) → rest.
 */
export function scriptPose(t: number, offscreen: number): Pose {
  'worklet';
  const { press, open, close } = SCRIPT;
  let lift = 0;
  let arm = 0;
  let sheet = 0;
  if (t >= press) {
    lift = PRESS_LIFT * easeOutCubic(seg(t, press, DURATION.press));
    arm = easeOutCubic(seg(t, press, DURATION.arm));
  }
  if (t >= open) {
    lift = PRESS_LIFT + (offscreen - PRESS_LIFT) * easeFall(seg(t, open, DURATION.fall));
    sheet = seg(t, open + DURATION.openDelay, DURATION.open); // linear: the shape is a time table
  }
  if (t >= close) {
    sheet = 1 - seg(t, close, DURATION.close);
    const back = close + DURATION.returnDelay;
    const r = t - back;
    lift = r < 0 ? offscreen
      : r < RETURN_MS ? RETURN_LIFT[0] - (RETURN_LIFT[0] - RETURN_LIFT[RETURN_LIFT.length - 1]) * easeReturn(r / RETURN_MS)
        : springSettle(r - RETURN_MS);
    arm = 1 - easeInOutCubic(seg(t, close + DURATION.tabOut, DURATION.tabFade));
  }
  return { lift, arm, sheet };
}

/** Reduce Motion: the same script, but nothing travels; the hint and the sheet cross-fade. */
export function reducedPose(t: number): Pose & { fade: number } {
  'worklet';
  const { press, open, close } = SCRIPT;
  const hint = seg(t, press, DURATION.fade) * (1 - seg(t, close, DURATION.fade));
  const fade = seg(t, open, DURATION.fade) * (1 - seg(t, close, DURATION.fade));
  return { lift: 0, arm: hint, sheet: 0, fade };
}

/** Rubber band for the card: follows the finger 1:1 for small moves, then resists. */
export function rubber(d: number, limit: number) {
  'worklet';
  const s = Math.sign(d);
  const a = Math.abs(d);
  return s * limit * (1 - 1 / (a / limit + 1));
}
