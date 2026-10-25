/**
 * Railway Switch Nav — the trips and the loop. A "trip" is everything one lever pull does: the lever
 * travels, two switches throw, the signals change, the route lights up, the train runs from one
 * section's platform to another's, and the page changes. Every value is a closed-form function of
 * the time since the trip began, so the loop (three trips) is exact and a finger's trip uses the
 * same code.
 *
 * Every helper is a module-level named worklet declared above its callers.
 */

export const LOOP = 9;
export const SECTIONS = ['Home', 'Trips', 'Tickets', 'Account'];
export const COUNT = 4;

/** A trip: [start (s), lever pull (s), run (s), from, to]. */
export const TRIPS = [
  [1.2, 0.8, 1.4, 0, 2],
  [4.4, 0.6, 1.2, 2, 1],
  [6.8, 0.6, 1.0, 1, 0],
];
/** A finger's trip: the same shape, a little quicker. */
export const TOUCH_PULL = 0.5;
export const TOUCH_RUN = 1.1;
/** The page change starts this far into the run and lasts PAGE_FOR. */
const PAGE_AT = 0.71;
const PAGE_FOR = 0.6;

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

/** A lever's travel: a short anticipation back (4 %), then the pull with a 5 % overshoot that
 * settles into the notch. */
export function leverCurve(x: number) {
  'worklet';
  const t = clamp01(x);
  if (t < 0.15) return -0.04 * Math.sin((t / 0.15) * Math.PI);
  const u = (t - 0.15) / 0.85;
  const c = 1.7;
  return 1 + (c + 1) * Math.pow(u - 1, 3) + c * Math.pow(u - 1, 2);
}

export type RailPose = {
  levers: number[];
  /** Switch blades, -1 (set left) … 0 (closed) … 1 (set right). */
  blades: number[];
  /** Green share of each section's signal. */
  signals: number[];
  from: number;
  to: number;
  /** The route's light (0..1) and the train's progress along it (0..1). */
  route: number;
  run: number;
  /** The page change (0 = from's page, 1 = to's page). */
  page: number;
};

export function tripPose(tau: number, pull: number, run: number, from: number, to: number, pullFrom: number): RailPose {
  'worklet';
  const dir = to > from ? 1 : -1;
  const levers: number[] = [];
  const blades: number[] = [];
  const signals: number[] = [];
  const depart = pull;
  const arrive = pull + run;
  const throwIn = easeInOut(seg(tau, pull * 0.55, pull * 0.55 + 0.3));
  const release = easeInOut(seg(tau, arrive + 0.15, arrive + 0.5));
  const green = easeInOut(seg(tau, pull * 0.8, pull * 0.8 + 0.18));
  for (let k = 0; k < COUNT; k++) {
    let lever = 0;
    let blade = 0;
    let signal = 0;
    if (k === to) {
      lever = pullFrom + (1 - pullFrom) * leverCurve(tau / pull);
      blade = -dir * throwIn * (1 - release);
      signal = green;
    } else if (k === from) {
      lever = 1 - easeInOut(seg(tau, 0.1, 0.1 + pull * 0.8));
      blade = dir * throwIn * (1 - release);
      signal = 1 - green;
    }
    levers.push(lever);
    blades.push(blade);
    signals.push(signal);
  }
  return {
    levers,
    blades,
    signals,
    from,
    to,
    route: easeOut(seg(tau, pull * 0.55, pull)) * (1 - easeInOut(seg(tau, arrive, arrive + 0.45))),
    run: easeInOut(seg(tau, depart, arrive)),
    page: easeInOut(seg(tau, depart + run * PAGE_AT, depart + run * PAGE_AT + PAGE_FOR)),
  };
}

export function loopPose(t: number): RailPose {
  'worklet';
  // The latest trip that has begun; before the first one the last trip is long finished (Home).
  let k = TRIPS.length - 1;
  let tau = t + LOOP - TRIPS[k][0];
  for (let i = 0; i < TRIPS.length; i++) {
    if (t >= TRIPS[i][0]) {
      k = i;
      tau = t - TRIPS[i][0];
    }
  }
  const trip = TRIPS[k];
  return tripPose(tau, trip[1], trip[2], trip[3], trip[4], 0);
}

/** The pose while the train stands at a section's platform. */
export function restPose(at: number): RailPose {
  'worklet';
  const levers: number[] = [];
  const blades: number[] = [];
  const signals: number[] = [];
  for (let k = 0; k < COUNT; k++) {
    levers.push(k === at ? 1 : 0);
    blades.push(0);
    signals.push(k === at ? 1 : 0);
  }
  return { levers, blades, signals, from: at, to: at, route: 0, run: 1, page: 1 };
}

/** How long a trip lasts until everything has settled. */
export function tripLength(pull: number, run: number) {
  'worklet';
  return Math.max(pull + run + 0.6, pull + run * PAGE_AT + PAGE_FOR);
}

/** The loop's current trip and the time since it began: [trip index, tau]. */
export function loopTrip(t: number): number[] {
  'worklet';
  let k = TRIPS.length - 1;
  let tau = t + LOOP - TRIPS[k][0];
  for (let i = 0; i < TRIPS.length; i++) {
    if (t >= TRIPS[i][0]) {
      k = i;
      tau = t - TRIPS[i][0];
    }
  }
  return [k, tau];
}
