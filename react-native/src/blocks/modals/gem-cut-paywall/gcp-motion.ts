// Gem Cut Paywall — the loop and the gem's geometry for the glints. Helpers first: worklets capture
// what is above them.

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

export const LOOP = 10;
export const IDLE_SPIN = 0.3; // rad/s
export const PITCH = 0.45;
/** Scripted moments: the finger drags Free → Pro, flicks the gem, taps Plus, taps Free. */
export const T = {
  drag0: 0.5,
  drag1: 2.3,
  flick0: 3.4,
  flick1: 3.72,
  coast1: 9.2,
  plus: 5.6,
  free: 7.4,
  tap: 0.7, // a tapped segment's glide, s
} as const;
// the flick adds exactly what makes the loop's turn a whole revolution
const FLICK = 2 * Math.PI - IDLE_SPIN * LOOP;

export type Frame = {
  cut: number; // 0 Free … 0.5 Plus … 1 Pro
  yaw: number;
  pitch: number;
  spark: number;
  finger: number; // 0…1, the scripted finger on the screen
  fingerOn: number; // 0 plan thumb, 1 gem, 2 Plus segment, 3 Free segment
  press: number; // a tap's press, 0…1
};

function pulse(t: number, at: number, amp: number) {
  'worklet';
  if (t < at - 0.05) return 0;
  return amp * easeOut(seg(t, at - 0.05, at + 0.08)) * Math.exp(-2.6 * Math.max(0, t - at - 0.08));
}

export function frameAt(t: number): Frame {
  'worklet';
  let cut = easeInOut(seg(t, T.drag0, T.drag1));
  cut -= 0.5 * easeInOut(seg(t, T.plus + 0.08, T.plus + 0.08 + T.tap));
  cut -= 0.5 * easeInOut(seg(t, T.free + 0.08, T.free + 0.08 + T.tap));
  // the flick: the finger speeds the gem up, then it coasts back to the idle turn
  const k = seg(t, T.flick0, T.coast1);
  const lead = 0.06; // the finger's grab (speed builds), as a share of the coast
  const x = (k - lead) / (1 - lead);
  const g = k < lead ? (0.5 * k * k) / lead : 0.5 * lead + ((1 - lead) * (1 - Math.pow(1 - x, 4))) / 4;
  const gEnd = 0.5 * lead + (1 - lead) / 4;
  const yaw = IDLE_SPIN * t + FLICK * (g / gEnd);
  const pitch = PITCH + 0.1 * Math.sin(Math.PI * seg(t, T.flick0, T.flick0 + 1.6));
  // a flare as each plan lands, strongest for Pro; nothing left by the end of the loop
  let spark = pulse(t, T.drag1, 1) + pulse(t, T.plus + 0.08 + T.tap, 0.45) + pulse(t, T.free + 0.08 + T.tap, 0.18);
  spark *= 1 - seg(t, 9.4, 10);
  // the finger
  let finger = 0;
  let fingerOn = 0;
  let press = 0;
  if (t < T.drag1 + 0.4) {
    finger = easeOut(seg(t, T.drag0 - 0.18, T.drag0)) * (1 - easeOut(seg(t, T.drag1 + 0.05, T.drag1 + 0.25)));
    fingerOn = 0;
  } else if (t < T.flick1 + 0.5) {
    finger = easeOut(seg(t, T.flick0 - 0.15, T.flick0)) * (1 - easeOut(seg(t, T.flick1, T.flick1 + 0.18)));
    fingerOn = 1;
  } else {
    const at = t < T.free - 0.3 ? T.plus : T.free;
    fingerOn = at === T.plus ? 2 : 3;
    finger = easeOut(seg(t, at - 0.15, at)) * (1 - easeOut(seg(t, at + 0.12, at + 0.3)));
    press = easeOut(seg(t, at, at + 0.08)) * (1 - easeOut(seg(t, at + 0.12, at + 0.3)));
  }
  return { cut, yaw, pitch, spark, finger, fingerOn, press };
}

/** Where the scripted finger is during the flick, as a share of the gem radius (x right). */
export function flickOffset(t: number) {
  'worklet';
  return -0.35 + 0.9 * easeInOut(seg(t, T.flick0, T.flick1));
}

/* ------------------------------------------------------------ glints */
// The gem's 8 bezels and its table, as (point, normal) in the gem's object space (girdle radius 1,
// y up). Matches the shader's proportions: crown 34.5°, table at y 0.316, girdle half thickness 0.02.
const CA = 0.8241;
const SA = 0.5664;
const GLINT_COUNT = 9;
export const GLINTS = GLINT_COUNT;

function rotX(x: number, y: number, z: number, a: number) {
  'worklet';
  const c = Math.cos(a);
  const s = Math.sin(a);
  return [x, c * y - s * z, s * y + c * z];
}
function rotY(x: number, y: number, z: number, a: number) {
  'worklet';
  const c = Math.cos(a);
  const s = Math.sin(a);
  return [c * x + s * z, y, -s * x + c * z];
}

/**
 * Screen position (as a share of the gem radius from its centre, y down) and strength of each glint:
 * a facet glints when it mirrors the overhead box or the ring light into the camera. Flat arrays
 * [x0, y0, a0, x1, y1, a1, …].
 */
export function glints(yaw: number, pitch: number, cut: number, spark: number): number[] {
  'worklet';
  const out: number[] = [];
  const on = cut * cut;
  for (let i = 0; i < GLINT_COUNT; i++) {
    let px: number;
    let py: number;
    let pz: number;
    let nx: number;
    let ny: number;
    let nz: number;
    if (i < 8) {
      const a = (i * Math.PI) / 4;
      const ux = Math.cos(a);
      const uz = Math.sin(a);
      px = 0.8 * ux;
      py = 0.17;
      pz = 0.8 * uz;
      nx = SA * ux;
      ny = CA;
      nz = SA * uz;
    } else {
      px = 0.18;
      py = 0.316;
      pz = 0.1;
      nx = 0;
      ny = 1;
      nz = 0;
    }
    // object → world: turn, then tilt; the object's centre sits 0.28 below the world origin
    let p = rotY(px, py + 0.28, pz, yaw);
    p = rotX(p[0], p[1], p[2], pitch);
    let n = rotY(nx, ny, nz, yaw);
    n = rotX(n[0], n[1], n[2], pitch);
    // camera at z = 6 looking down −z; mirror the view ray about the facet
    const d = -2 * -n[2];
    const rx = d * n[0];
    const ry = d * n[1];
    const rz = -1 + d * n[2];
    const top = Math.max(0, rx * 0 + ry * 0.949 + rz * 0.316);
    const ring = Math.max(0, rx * 0.139 + ry * 0.324 + rz * 0.936);
    let a = Math.pow(top, 24) + Math.pow(ring, 60);
    a = Math.min(1, a * (1 + 2 * spark)) * on * (n[2] > 0.05 ? 1 : 0);
    const k = 6 / (6 - p[2]);
    out.push(p[0] * k, -p[1] * k, a);
  }
  return out;
}
