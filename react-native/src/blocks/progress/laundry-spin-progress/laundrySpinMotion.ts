/*
 Laundry Spin Progress — the wash cycle's script and the clothes' motion, as pure functions of the
 loop time (frame at t = LOOP equals frame at 0). Every helper is a module-level named worklet
 declared above its callers.

 A scene is [drum angle, speed rev/s, tumble, plaster, water level, suds, fog, slosh]:
   tumble   0 = the clothes lie in a pile at the bottom … 1 = they tumble (ride up the drum wall
            on the right, let go, fall across to the bottom left: one cycle per drum turn)
   plaster  1 = pressed flat against the wall by the spin, turning with the drum
   level    the water's height as a share of the drum's diameter
   fog      condensation on the inside of the glass (hides the reset)

 autoPlay script (LOOP = 10 s):
   0.0 – 1.6   rest: water calm, clothes settled, a few suds.
   1.6 – 4.2   wash: the drum starts (0.8 rev/s), the clothes tumble, suds build.
   4.2 – 5.6   drain (to 12 %) and rinse (back to 32 %, slower, fewer suds).
   5.6 – 8.0   spin: the water drains, the drum speeds up to 11 rev/s (6.0 – 7.0), the clothes
               plaster to the wall (motion blur), then it slows to a stop (7.4 – 8.0).
   8.0 – 8.6   done: the clothes drop off the wall into a pile.
   8.6 – 10.0  the glass fogs up (8.6 – 9.2), the water and suds come back behind it, the fog
               clears (9.4 – 10.0) onto the rest frame.
 The drum turns a whole number of times per loop, so its holes and lifters match at the seam.
 */

export const LOOP = 10;
export const S_ANGLE = 0;
export const S_SPEED = 1;
export const S_TUMBLE = 2;
export const S_PLASTER = 3;
export const S_LEVEL = 4;
export const S_SUDS = 5;
export const S_FOG = 6;
export const S_SLOSH = 7;
export const GARMENTS = 6;

/** Drum speed keyframes [t, rev/s], linear between them. */
const SPEED_KEYS = [
  [0, 0],
  [1.6, 0],
  [2.0, 0.8],
  [4.0, 0.8],
  [4.3, 0.4],
  [5.6, 0.5],
  [6.0, 0.6],
  [7.0, 11],
  [7.4, 11],
  [8.0, 0],
  [10, 0],
];

export function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeOut(x: number) {
  'worklet';
  const u = 1 - x;
  return 1 - u * u * u;
}

export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function hash(i: number) {
  'worklet';
  const s = Math.sin(i * 12.9898 + 78.233) * 43758.5453;
  return s - Math.floor(s);
}

export function speedAt(t: number) {
  'worklet';
  for (let i = 0; i < SPEED_KEYS.length - 1; i += 1) {
    const a = SPEED_KEYS[i];
    const b = SPEED_KEYS[i + 1];
    if (t <= b[0]) return a[1] + ((b[1] - a[1]) * (t - a[0])) / (b[0] - a[0]);
  }
  return 0;
}

/** Revolutions turned by time t (the integral of the speed keyframes). */
function revsRaw(t: number) {
  'worklet';
  let sum = 0;
  for (let i = 0; i < SPEED_KEYS.length - 1; i += 1) {
    const a = SPEED_KEYS[i];
    const b = SPEED_KEYS[i + 1];
    if (t <= a[0]) break;
    const e = Math.min(t, b[0]);
    const vE = a[1] + ((b[1] - a[1]) * (e - a[0])) / (b[0] - a[0]);
    sum += ((a[1] + vE) / 2) * (e - a[0]);
  }
  return sum;
}

/** The drum's angle (radians) at t: the raw integral scaled to a whole number of turns. */
export function angleAt(t: number) {
  'worklet';
  const total = revsRaw(LOOP);
  const whole = Math.round(total);
  return (revsRaw(t) * whole * Math.PI * 2) / total;
}

export function restScene(): number[] {
  'worklet';
  return [0, 0, 0, 0, 0.38, 0.15, 0, 0];
}

export function sceneAt(t: number): number[] {
  'worklet';
  const s = restScene();
  const v = speedAt(t);
  s[S_ANGLE] = angleAt(t);
  s[S_SPEED] = v;
  const wash = easeInOut(seg(t, 1.6, 2.2)) * (1 - easeInOut(seg(t, 4.2, 4.6)));
  const rinse = easeInOut(seg(t, 4.7, 5.1)) * (1 - easeInOut(seg(t, 5.6, 6.0)));
  s[S_TUMBLE] = Math.max(wash, rinse * 0.8);
  s[S_PLASTER] = easeInOut(seg(t, 6.0, 6.7)) * (1 - easeOut(seg(t, 7.9, 8.4)));
  let level = 0.38;
  if (t >= 4.2) level = 0.38 - 0.26 * easeInOut(seg(t, 4.2, 4.7)) + 0.2 * easeInOut(seg(t, 4.8, 5.4));
  if (t >= 5.6) level = 0.32 * (1 - easeInOut(seg(t, 5.6, 6.1)));
  if (t >= 8.8) level = 0.38 * easeInOut(seg(t, 8.8, 9.4));
  s[S_LEVEL] = level;
  let suds = 0.15 + 0.85 * easeOut(seg(t, 1.8, 4.0));
  if (t >= 4.2) suds = 1 - 0.7 * easeInOut(seg(t, 4.2, 5.2));
  if (t >= 5.6) suds = 0.3 * (1 - easeInOut(seg(t, 5.6, 6.2)));
  if (t >= 8.8) suds = 0.15 * easeInOut(seg(t, 8.8, 9.4));
  s[S_SUDS] = suds;
  s[S_FOG] = easeOut(seg(t, 8.6, 9.2)) * (1 - easeInOut(seg(t, 9.4, 10)));
  s[S_SLOSH] = Math.min(1, v / 0.8) * (1 - s[S_PLASTER]);
  return s;
}

/** Where each garment lies in the pile at rest (drum units: centre 0, radius 1, y down). */
const PILE = [
  [-0.46, 0.56, 0.5],
  [0.06, 0.66, -0.3],
  [0.5, 0.5, 1.0],
  [-0.2, 0.36, 1.7],
  [0.3, 0.28, -1.2],
  [-0.04, 0.12, 2.5],
];

/**
 Garment i for a scene: [x, y (drum units), rotation, blur (radians of smear), stretch].
 Tumbling: ride on the wall from the bottom left (−110°) up the right side to 30° above the
 horizontal, let go, fall back across along a parabola (one cycle per drum turn, each garment at
 its own phase).
 */
export function garmentAt(i: number, s: number[]): number[] {
  'worklet';
  const pile = PILE[i];
  const A = s[S_ANGLE];
  const phase = (((A / (Math.PI * 2) + hash(i + 3) * 0.9 + i * 0.17) % 1) + 1) % 1;
  const ride = 0.58;
  let tx = 0;
  let ty = 0;
  let stretch = 0;
  const r = 0.6 - 0.06 * hash(i + 9);
  const a0 = (-110 * Math.PI) / 180;
  const a1 = (30 * Math.PI) / 180;
  if (phase < ride) {
    const a = a0 + (a1 - a0) * (phase / ride);
    tx = Math.cos(a) * r;
    ty = -Math.sin(a) * r;
  } else {
    const f = (phase - ride) / (1 - ride);
    const x0 = Math.cos(a1) * r;
    const y0 = -Math.sin(a1) * r;
    const x1 = Math.cos(a0) * r;
    const y1 = -Math.sin(a0) * r;
    tx = x0 + (x1 - x0) * f;
    ty = y0 + (y1 - y0) * f * f - 0.25 * Math.sin(Math.PI * f) * (1 - f);
    stretch = Math.sin(Math.PI * f) * 0.35;
  }
  // a pile garment sits under the water's surface a little lower than a tumbling one
  const tw = s[S_TUMBLE];
  let x = pile[0] + (tx - pile[0]) * tw;
  let y = pile[1] + (ty - pile[1]) * tw;
  let rot = pile[2] + phase * Math.PI * 2 * 0.6 * tw;
  // pressed against the wall by the spin, evenly round it, turning with the drum
  const pa = A + (i / GARMENTS) * Math.PI * 2 + 0.3 * hash(i + 5);
  const pw = s[S_PLASTER];
  x += (Math.cos(pa) * 0.74 - x) * pw;
  y += (-Math.sin(pa) * 0.74 - y) * pw;
  rot += (-pa + Math.PI / 2 - rot) * pw;
  const blur = Math.min(0.9, s[S_SPEED] * 0.09) * pw;
  return [x, y, rot, blur, stretch * tw * (1 - pw)];
}

/** Phase of the cycle shown at loop time t (index into PHASES), the time left and the progress. */
export const PHASES = ['Ready to wash', 'Washing', 'Rinsing', 'Spinning', 'Done'];

export function phaseAt(t: number) {
  'worklet';
  if (t < 1.6) return 0;
  if (t < 4.2) return 1;
  if (t < 5.6) return 2;
  if (t < 8.0) return 3;
  if (t < 9.4) return 4;
  return 0;
}

/** Progress through the cycle (0 … 1) at loop time t; it runs back to 0 behind the fog. */
export function progressAt(t: number) {
  'worklet';
  if (t < 1.6) return 0;
  if (t < 8.0) {
    // wash, rinse and spin each fill their quarter of the bar; done fills the last
    if (t < 4.2) return 0.25 * seg(t, 1.6, 4.2);
    if (t < 5.6) return 0.25 + 0.25 * seg(t, 4.2, 5.6);
    return 0.5 + 0.25 * seg(t, 5.6, 8.0);
  }
  if (t < 9.0) return 0.75 + 0.25 * easeOut(seg(t, 8.0, 8.5));
  return 1 - easeInOut(seg(t, 9.0, 9.6));
}

/** Minutes left in a 42-minute cycle at a progress. */
export function minutesLeft(p: number) {
  'worklet';
  return Math.round(42 * (1 - p));
}
