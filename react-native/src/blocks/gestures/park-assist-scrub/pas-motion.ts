// Park Assist Scrub — the car's path into the bay, the gap to the wall, and the autoPlay loop, as pure
// functions. Helpers first: worklets capture what is above them.

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

// the reversing path (metres): from the aisle, rear first, into the bay
const P0x = 2.7, P0y = 5.1, P1x = 1.3, P1y = 4.7, P2x = 0, P2y = 2.6, P3x = 0, P3y = -0.8;
export const WALL_Y = -3.3;
export const HALF_LENGTH = 2.2;

export type Pose = { x: number; y: number; heading: number };
export function poseAt(s: number): Pose {
  'worklet';
  const t = clamp01(s);
  const u = 1 - t;
  const x = u * u * u * P0x + 3 * u * u * t * P1x + 3 * u * t * t * P2x + t * t * t * P3x;
  const y = u * u * u * P0y + 3 * u * u * t * P1y + 3 * u * t * t * P2y + t * t * t * P3y;
  const dx = 3 * u * u * (P1x - P0x) + 6 * u * t * (P2x - P1x) + 3 * t * t * (P3x - P2x);
  const dy = 3 * u * u * (P1y - P0y) + 6 * u * t * (P2y - P1y) + 3 * t * t * (P3y - P2y);
  // reversing: the nose points away from the way it moves
  return { x, y, heading: Math.atan2(dx, -dy) };
}
/** The front wheels' turn: how fast the heading changes along the path. */
export function steerAt(s: number) {
  'worklet';
  const a = poseAt(s - 0.01).heading;
  const b = poseAt(s + 0.01).heading;
  return Math.max(-0.5, Math.min(0.5, (b - a) / 0.02 * 0.35));
}
/** The gap between the rear bumper and the wall (m). */
export function gapAt(s: number) {
  'worklet';
  const p = poseAt(s);
  const rearY = p.y - Math.cos(p.heading) * HALF_LENGTH - Math.abs(Math.sin(p.heading)) * 0.9;
  return Math.max(0, rearY - WALL_Y);
}

export const LOOP = 10;
export const T = {
  hold1: 0.8,
  go1: 0.95,
  release1: 3.5,
  hold2: 4.3,
  go2: 4.4,
  parked: 6.6,
  release2: 6.7,
  flash: 6.9,
  out: [8.0, 9.8] as const,
} as const;
const MID = 0.6;

export function sAt(t: number) {
  'worklet';
  if (t < T.go1) return 0;
  // the first hold: speeds up, rolls at a steady speed, stops 0.25 s after the finger lifts
  if (t < T.release1 + 0.25) {
    const run = T.release1 + 0.25 - T.go1;
    const u = (t - T.go1) / run;
    // ease in over the first 15 %, out over the last 10 %
    const a = 0.15, b = 0.1;
    const area = 1 - a / 2 - b / 2;
    let d;
    if (u < a) d = (u * u) / (2 * a);
    else if (u < 1 - b) d = a / 2 + (u - a);
    else d = a / 2 + (1 - a - b) + (b - (1 - u) * (1 - u) / b) / 2;
    return (MID * d) / area;
  }
  if (t < T.go2) return MID;
  if (t < T.out[0]) return MID + (1 - MID) * easeOut(seg(t, T.go2, T.parked));
  return 1 - easeInOut(seg(t, T.out[0], T.out[1]));
}

export type Frame = {
  s: number;
  assist: number; // the arcs and the readout
  brake: number;
  hazard: number;
  parked: number; // the parked state's words
  holding: number; // the hold button pressed
  out: number; // driving out
  finger: number;
};

export function frameAt(t: number): Frame {
  'worklet';
  const s = sAt(t);
  const assist = smooth(T.go1 - 0.1, T.go1 + 0.2, t) * (1 - smooth(T.out[0], T.out[0] + 0.3, t));
  const brake = smooth(T.release1 + 0.15, T.release1 + 0.3, t) * (1 - smooth(T.hold2, T.hold2 + 0.1, t)) + smooth(T.parked, T.parked + 0.15, t) * (1 - smooth(T.out[0] - 0.2, T.out[0], t));
  const blink = (a: number) => Math.sin(Math.PI * seg(t, a, a + 0.28));
  const hazard = blink(T.flash) + blink(T.flash + 0.35);
  const parked = smooth(T.parked, T.parked + 0.3, t) * (1 - smooth(T.out[0], T.out[0] + 0.3, t));
  const press = (a: number, b: number) => easeOut(seg(t, a, a + 0.1)) * (1 - easeOut(seg(t, b, b + 0.15)));
  const holding = press(T.hold1, T.release1) + press(T.hold2, T.release2);
  const near = (a: number, b: number) => easeOut(seg(t, a - 0.25, a)) * (1 - easeOut(seg(t, b, b + 0.2)));
  return {
    s,
    assist,
    brake: clamp01(brake),
    hazard,
    parked,
    holding,
    out: smooth(T.out[0], T.out[0] + 0.2, t) * (1 - smooth(T.out[1] - 0.2, T.out[1], t)),
    finger: clamp01(near(T.hold1, T.release1) + near(T.hold2, T.release2)),
  };
}
