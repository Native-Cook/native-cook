import { Easing } from 'react-native-reanimated';

// Motion on the still pin's own card: a finger picks it up, it rises toward the viewer and tilts under the
// finger, the reeded glass refracts and a specular streak runs across the flutes, its shadow falls on the
// buttons below, and it drops back with its own momentum. Values and phases: design.md → Motion design.

export const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
export const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);

export const TILT = { max: 6, refraction: 0.06, followIn: 120 } as const; // degrees; flute offset per degree share
export const LIFT = { scale: 0.025, press: -0.6, pressMs: 180 } as const; // scale = 1 + LIFT.scale * lift
export const PICKUP = { response: 0.5, damping: 0.75 } as const;
export const RELEASE = { response: 0.45, damping: 0.7 } as const;
export const LIGHT = { spec: 0.35, specTravel: 0.4, shadow: 0.55, shadowDrop: 4, dotStretch: 4 } as const;
export const PRESS = { scale: 0.97 } as const;

export const LOOP_MS = 7000;
export const RESUME_AFTER_MS = 3500;

/** The autoplay loop on one clock (ms). */
export const LOOP = {
  touch: 600, pickup: 780, traceFrom: 800, traceTo: 5200,
  path: { x: 0.34, y: 0.22 }, // the finger's figure-eight, in card widths/heights from the centre
  glowIn: 1000, glowOut: 3000, glowMs: 600, glow: 0.25, // Reduced Motion only
} as const;

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

/** Damped spring in closed form: displacement from the target at `tau` s, from `x0` with velocity `v0`/s. */
export function springAt(tau: number, x0: number, v0: number, response: number, damping: number) {
  'worklet';
  if (tau <= 0) return x0;
  const w = (2 * Math.PI) / response;
  const e = Math.exp(-damping * w * tau);
  if (damping >= 1) return e * (x0 + (v0 + w * x0) * tau);
  const wd = w * Math.sqrt(1 - damping * damping);
  return e * (x0 * Math.cos(wd * tau) + ((v0 + damping * w * x0) / wd) * Math.sin(wd * tau));
}

/** Where the scripted finger is (card-centred, -0.5..0.5) while it rests on the card. */
function finger(t: number): [number, number] {
  'worklet';
  const L = LOOP;
  if (t < L.traceFrom) {
    const k = bezier(0.23, 1, 0.32, 1, (t - L.touch) / LIFT.pressMs);
    return [-L.path.x * k, 0];
  }
  const u = (Math.min(t, L.traceTo) - L.traceFrom) / (L.traceTo - L.traceFrom);
  return [-L.path.x * Math.cos(2 * Math.PI * u), -L.path.y * Math.sin(4 * Math.PI * u)];
}

/** Tilt (deg) under a finger at (fx, fy): the card dips where it is pressed. */
function tiltOf(fx: number, fy: number): [number, number] {
  'worklet';
  return [-fy * 2 * TILT.max, fx * 2 * TILT.max];
}

function liftHeld(t: number) {
  'worklet';
  if (t < LOOP.pickup) return LIFT.press * bezier(0.23, 1, 0.32, 1, (t - LOOP.touch) / LIFT.pressMs);
  return 1 + springAt((t - LOOP.pickup) / 1000, LIFT.press - 1, 0, PICKUP.response, PICKUP.damping);
}

export type Pose = { rx: number; ry: number; lift: number; glow: number };

/** Every animated value of the loop at `t` ms; t = 0 and t = LOOP_MS are the pin's rest frame. */
export function pose(tIn: number, reduced: boolean): Pose {
  'worklet';
  const L = LOOP;
  const t = ((tIn % LOOP_MS) + LOOP_MS) % LOOP_MS;
  if (reduced) {
    const ramp = (a: number) => Math.min(1, Math.max(0, (t - a) / L.glowMs));
    return { rx: 0, ry: 0, lift: 0, glow: L.glow * (bezier(0.77, 0, 0.175, 1, ramp(L.glowIn)) - bezier(0.77, 0, 0.175, 1, ramp(L.glowOut))) };
  }
  if (t < L.touch) return { rx: 0, ry: 0, lift: 0, glow: 0 };
  if (t <= L.traceTo) {
    const [fx, fy] = finger(t);
    const [rx, ry] = tiltOf(fx, fy);
    return { rx, ry, lift: liftHeld(t), glow: 0 };
  }
  // Released: tilt and height spring home from where they were, with the finger's last velocity.
  const tau = (t - L.traceTo) / 1000;
  const [ax, ay] = tiltOf(...finger(L.traceTo));
  const [bx, by] = tiltOf(...finger(L.traceTo - 1));
  const lift0 = liftHeld(L.traceTo);
  return {
    rx: springAt(tau, ax, (ax - bx) * 1000, RELEASE.response, RELEASE.damping),
    ry: springAt(tau, ay, (ay - by) * 1000, RELEASE.response, RELEASE.damping),
    lift: springAt(tau, lift0, 0, RELEASE.response, RELEASE.damping),
    glow: 0,
  };
}
