import { Easing } from 'react-native-reanimated';

// Motion added to the still pin's own elements: the front card is pulled out of the leather pocket and
// dropped back in, the pocket gives under it, and the eye button hides the balance digit by digit.

export const PULL = {
  lift: 44, // how far the front card rises out of the pocket (its top stays under the title)
  backLift: 7, // the card behind is dragged up a little by friction
  tilt: -1.8, // degrees, the card leans as it leaves the pocket
  limit: 62, // rubber-band limit for a finger drag
  tapLift: 38,
} as const;

// Spring forms from the motion rules ({duration, dampingRatio}); a bounce only where a drop carries momentum.
export const SPRING = {
  pull: { duration: 400, dampingRatio: 1 }, // settle
  drop: { duration: 400, dampingRatio: 0.8 }, // snap back home after a drag or a lift
  squish: { duration: 400, dampingRatio: 0.8 },
} as const;

export const PRESS = { scale: 0.97, in: 120 } as const;
export const SWAP_TURN = 250;

export const SQUISH = { scaleY: 0.982, scaleX: 1.008 } as const;

export const HIDE = { charStagger: 30, charDuration: 200, rise: 7 } as const;

export const FADE_MS = 180;
export const LOOP_MS = 6000;
export const RESUME_AFTER_MS = 3500;


/** The three curves of the motion rules, as Reanimated easings and as worklet functions for staggers. */
export const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
export const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);

export function rubber(d: number, limit: number) {
  'worklet';
  const s = Math.sign(d);
  const a = Math.abs(d);
  return s * limit * (1 - 1 / (a / limit + 1));
}

/** Cubic bezier as a pure function (Newton on x), so every stack evaluates the same curve. */
export function bezier(x1: number, y1: number, x2: number, y2: number, x: number) {
  'worklet';
  if (x <= 0) return 0;
  if (x >= 1) return 1;
  let t = x;
  for (let k = 0; k < 8; k++) {
    const cx = 3 * x1 * t * (1 - t) * (1 - t) + 3 * x2 * t * t * (1 - t) + t * t * t - x;
    const dx = 3 * x1 * (1 - t) * (1 - 3 * t) + 3 * x2 * t * (2 - 3 * t) + 3 * t * t;
    if (Math.abs(dx) < 1e-6) break;
    t = Math.min(1, Math.max(0, t - cx / dx));
  }
  return 3 * y1 * t * (1 - t) * (1 - t) + 3 * y2 * t * t * (1 - t) + t * t * t;
}

/**
 * Damped spring in closed form: displacement from the target at `tau` seconds, starting at `x0` with
 * velocity `v0` (units/s). `response` is the period of the undamped spring in seconds.
 */
export function springAt(tau: number, x0: number, v0: number, response: number, damping: number) {
  'worklet';
  if (tau <= 0) return x0;
  const w = (2 * Math.PI) / response;
  const e = Math.exp(-damping * w * tau);
  if (damping >= 1) return e * (x0 + (v0 + w * x0) * tau);
  const wd = w * Math.sqrt(1 - damping * damping);
  return e * (x0 * Math.cos(wd * tau) + ((v0 + damping * w * x0) / wd) * Math.sin(wd * tau));
}

/** The autoplay loop on one clock (ms). Values and phases: design.md → Motion design. */
export const LOOP = {
  antic: 400, draw: 600, swayFrom: 900, swayTo: 3700, fall: 3800, impact: 4160,
  lift: 44, dip: 4, lean: -2.2, sway: 7, backShare: 0.12, backLag: 60,
  drawSpring: { response: 0.55, damping: 0.8 },
  impactV: -160, impactSpring: { response: 0.42, damping: 0.55 },
  squishV: 30, squishSpring: { response: 0.3, damping: 0.5 },
  hideAt: 1500, showAt: 4000, // Reduced Motion only
} as const;

export const TURN = { perDx: 1 / 6, max: 10, perspective: 800 } as const; // a sideways drag turns the card
export const LIGHT = { sheen: 0.32, sheenTravel: 0.45, shadow: 0.32 } as const;

function liftAt(t: number): number {
  'worklet';
  const L = LOOP;
  if (t < L.antic) return 0;
  if (t < L.draw) return -L.dip * bezier(0.77, 0, 0.175, 1, (t - L.antic) / (L.draw - L.antic));
  const drawn = (u: number) => L.lift + springAt((u - L.draw) / 1000, -L.dip - L.lift, 0, L.drawSpring.response, L.drawSpring.damping);
  if (t < L.fall) return drawn(t);
  if (t < L.impact) {
    const p = (t - L.fall) / (L.impact - L.fall);
    return drawn(L.fall) * (1 - p * p); // free fall
  }
  return springAt((t - L.impact) / 1000, 0, L.impactV, L.impactSpring.response, L.impactSpring.damping);
}

export type Pose = { lift: number; back: number; lean: number; turn: number; squish: number; hidden: number };

/** Every animated value of the loop at `t` ms; t = 0 and t = LOOP_MS are the pin's rest frame. */
export function pose(tIn: number, reduced: boolean): Pose {
  'worklet';
  const L = LOOP;
  const t = ((tIn % LOOP_MS) + LOOP_MS) % LOOP_MS;
  if (reduced) {
    const fade = (a: number) => Math.min(1, Math.max(0, (t - a) / FADE_MS));
    return { lift: 0, back: 0, lean: 0, turn: 0, squish: 0, hidden: fade(L.hideAt) - fade(L.showAt) };
  }
  const lift = liftAt(t);
  const turn = t > L.swayFrom && t < L.swayTo
    ? L.sway * Math.sin((2 * Math.PI * (t - L.swayFrom)) / (L.swayTo - L.swayFrom)) : 0;
  let squish = 0;
  if (t < L.draw) squish = 0.3 * Math.max(0, -lift / L.dip);
  else if (t > L.impact) {
    squish = Math.max(-0.4, springAt((t - L.impact) / 1000, 0, L.squishV, L.squishSpring.response, L.squishSpring.damping));
  }
  return {
    lift,
    back: L.backShare * liftAt(Math.max(0, t - L.backLag)),
    lean: L.lean * Math.min(1.1, Math.max(0, lift) / L.lift),
    turn,
    squish,
    hidden: 0,
  };
}
