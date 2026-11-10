// Glass Aperture Camera — the loop. Helpers first: a worklet captures only what is declared above it.
//
// fnum is the f-number (1.4 … 16): the smaller, the wider the iris opens and the bigger the bokeh.
// iris 0 … 1 shows the glass iris over the viewfinder while it is being set. focus 0 … 1 is the
// depth in focus (0 near, 1 far). snap 0 … 1 is the shutter: the blades close and open again; shot is
// the s since the shutter was pressed (−1 none), and shotAt(shot) throws the photo into the friends it is
// sent to on springs.

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
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
/** f-numbers on a log scale: 0 → 1.4, 1 → 16 */
export function fAt(u: number) {
  'worklet';
  return 1.4 * Math.pow(16 / 1.4, clamp01(u));
}
export function uOf(f: number) {
  'worklet';
  return Math.log(f / 1.4) / Math.log(16 / 1.4);
}

/** the displacement from `to` of a damped spring let go at `from` with velocity v0 (units/s), t s ago */
export function springAt(t: number, from: number, to: number, v0: number, zeta: number, omega: number) {
  'worklet';
  if (t <= 0) return from;
  const x0 = from - to;
  const wd = omega * Math.sqrt(1 - zeta * zeta);
  const b = (v0 + zeta * omega * x0) / wd;
  return to + Math.exp(-zeta * omega * t) * (x0 * Math.cos(wd * t) + b * Math.sin(wd * t));
}

/**
 * The shot's flight, s after the shutter. Once the blades have opened (SHOT.go) the frame is tossed:
 * up a little first, then down on a spring into the footer, where it lands a touch past the line and settles. It shrinks on a stiffer spring, tilts with
 * its own sideways wobble, stretches along its speed, and fades into the friends once it has landed;
 * the footer gives under it (bump). y is the share of the way down (0 the viewfinder, 1 the footer).
 */
export const SHOT = {
  go: 0.3,
  y: { v0: -3.5, zeta: 0.66, omega: 8 },
  k: { to: 0.14, zeta: 0.82, omega: 11 },
  rot: { to: -6, v0: 28, zeta: 0.5, omega: 11 },
  end: 1.5,
} as const;
function shotY(f: number) {
  'worklet';
  return springAt(f, 0, 1, SHOT.y.v0, SHOT.y.zeta, SHOT.y.omega);
}
/** s into the flight the shot first reaches the footer: the landing (a tap of haptics, the footer gives) */
export const LAND = (() => {
  let f = 0;
  while (f < 2 && shotY(f) < 1) f += 0.001;
  return f;
})();
export type Shot = { y: number; k: number; rot: number; stretch: number; alpha: number; bump: number };
export function shotAt(s: number): Shot {
  'worklet';
  if (s < 0) return { y: 0, k: 1, rot: 0, stretch: 0, alpha: 0, bump: 0 };
  const f = s - SHOT.go;
  if (f <= 0) return { y: 0, k: 1, rot: 0, stretch: 0, alpha: 0, bump: 0 };
  const y = shotY(f);
  const vy = (shotY(f + 0.004) - shotY(Math.max(0, f - 0.004))) / (f > 0.004 ? 0.008 : 0.004 + f);
  const k = springAt(f, 1, SHOT.k.to, 0, SHOT.k.zeta, SHOT.k.omega);
  const rot = springAt(f, 0, SHOT.rot.to, SHOT.rot.v0, SHOT.rot.zeta, SHOT.rot.omega);
  // the footer gives under the landing: an impulse on a stiff, quickly damped spring
  const tau = f - LAND;
  const bump = tau > 0 ? Math.exp(-7 * tau) * Math.sin(26 * tau) : 0;
  const alpha = 1 - seg(f, LAND + 0.05, LAND + 0.4);
  return { y, k, rot, stretch: Math.max(-1, Math.min(1, vy / 6)), alpha, bump };
}

export const LOOP = 10;
/** the depth the camera rests focused on: the van */
export const NEAR = 0.2;
export const FAR = 0.92;

/* ---------------------------------------------------------------- the loop (10 s)
 0.6  two fingers on the viewfinder: the iris shows     0.9 opens to ƒ1.4     2.2 closes to ƒ11
 3.8  back to ƒ2.8, the iris goes                       5.4 a tap on the far lights: they come sharp
 7.0  focus back on the van                             7.8 the shutter: the blades snap shut and open
 8.1  the photo is tossed into the friends it goes to and lands (8.5)   9.6 the footer reads as before   10 = 0 */
export const T = {
  irisIn: [0.6, 0.9],
  open: [0.9, 1.8],
  close: [2.2, 3.4],
  rest: [3.8, 4.4],
  irisOut: [4.5, 5.0],
  far: [5.4, 6.0],
  near: [7.0, 7.6],
  snap: [7.8, 8.15],
  shot: [8.1, 8.9],
  sent: [8.52, 9.6], // from the shot's landing (7.8 + SHOT.go + LAND)
} as const;

export type Frame = {
  fnum: number;
  iris: number;
  focus: number;
  snap: number;
  shot: number; // s since the shutter (−1 none)
  sent: number; // the footer's "sent" line, 0 … 1
  ring: number; // the focus ring after a tap, s since (−1 none)
  finger: number;
  fingerAt: number; // 0 pinch, 1 tap on the far lights, 2 shutter
  spread: number; // the pinch's spread, pt
  press: number;
};

export function frameAt(t: number): Frame {
  'worklet';
  const u0 = uOf(2.8);
  const u11 = uOf(11);
  let u = u0;
  if (t >= T.open[0] && t < T.close[0]) u = u0 * (1 - easeInOut(seg(t, T.open[0], T.open[1])));
  else if (t >= T.close[0] && t < T.rest[0]) u = u11 * easeInOut(seg(t, T.close[0], T.close[1]));
  else if (t >= T.rest[0]) u = u11 + (u0 - u11) * easeInOut(seg(t, T.rest[0], T.rest[1]));
  const iris = easeOut(seg(t, T.irisIn[0], T.irisIn[1])) * (1 - easeInOut(seg(t, T.irisOut[0], T.irisOut[1])));
  const focus = NEAR + (FAR - NEAR) * (easeInOut(seg(t, T.far[0], T.far[1])) - easeInOut(seg(t, T.near[0], T.near[1])));
  const a = seg(t, T.snap[0], T.snap[1]);
  const snap = a < 0.35 ? easeInOut(a / 0.35) : 1 - easeOut((a - 0.35) / 0.65);
  const shot = t >= T.snap[0] && t < T.snap[0] + SHOT.end ? t - T.snap[0] : -1;
  const sent = easeOut(seg(t, T.sent[0], T.sent[0] + 0.25)) * (1 - easeInOut(seg(t, T.sent[1] - 0.25, T.sent[1])));
  const ring = t >= T.far[0] && t < T.far[0] + 1.2 ? t - T.far[0] : t >= T.near[0] && t < T.near[0] + 1.2 ? t - T.near[0] : -1;
  let finger = 0;
  let fingerAt = 0;
  let press = 0;
  const pinch = easeOut(seg(t, T.irisIn[0] - 0.2, T.irisIn[0])) * (1 - easeOut(seg(t, T.rest[1], T.rest[1] + 0.2)));
  if (pinch > finger) {
    finger = pinch;
    press = pinch;
  }
  const tapF = easeOut(seg(t, T.far[0] - 0.18, T.far[0] - 0.03)) * (1 - easeOut(seg(t, T.far[0] + 0.1, T.far[0] + 0.26)));
  if (tapF > finger) {
    finger = tapF;
    fingerAt = 1;
    press = tapF;
  }
  const tapN = easeOut(seg(t, T.near[0] - 0.18, T.near[0] - 0.03)) * (1 - easeOut(seg(t, T.near[0] + 0.1, T.near[0] + 0.26)));
  if (tapN > finger) {
    finger = tapN;
    fingerAt = 3;
    press = tapN;
  }
  const sh = easeOut(seg(t, T.snap[0] - 0.18, T.snap[0] - 0.03)) * (1 - easeOut(seg(t, T.snap[0] + 0.1, T.snap[0] + 0.26)));
  if (sh > finger) {
    finger = sh;
    fingerAt = 2;
    press = sh;
  }
  // the pinch's spread follows the aperture: wide open, fingers apart
  const spread = 60 + 90 * (1 - u / u11);
  return { fnum: fAt(u), iris, focus, snap, shot, sent, ring, finger, fingerAt, spread, press };
}
