/*
 Sundial Time Picker — the sun, the dial's geometry and the loop's script, as pure functions of
 the loop time (frame at t = LOOP equals frame at 0). Every helper is a module-level named
 worklet declared above its callers.

 Time τ is in hours (7 … 17). The sun follows an equinox day at latitude 40° N: hour angle
 h = (τ − 12) · 15°, direction (east, north, up) = (−sin h, −sin φ cos h, cos φ cos h). The dial
 is a horizontal sundial: north is up on screen, the gnomon's style rises from the centre O along
 the noon line at the latitude's angle, so its shadow edge falls on the hour line
 atan(sin φ · tan h) — the hour lines carved into the stone are where the shadow really lands.

 autoPlay script (LOOP = 9 s):
   0.0 – 1.4   rest at 10:00.
   1.2 – 1.4   a finger lands on the sun.
   1.4 – 4.4   drag to 15:30 (ease-in-out; the shadow swings and shortens, then lengthens; the
               light warms towards the afternoon); the readout rolls through every quarter.
   4.4         snap: 15:30 exactly with a small overshoot that settles by 4.7.
   4.4 – 5.6   hold.
   5.6 – 7.6   back to 10:00 (ease-in-out), the finger lifts at 7.6.
   7.6 – 9.0   rest.
 */

export const LOOP = 9;
export const T_MIN = 7;
export const T_MAX = 17;
export const REST_TIME = 10;
const LAT = (40 * Math.PI) / 180;
export const SIN_LAT = Math.sin(LAT);
export const COS_LAT = Math.cos(LAT);

export type DialGeo = { w: number; h: number; unit: number; cx: number; cy: number; R: number; arcX: number; arcY: number; gnomon: number };

export function makeGeo(w: number, h: number, top: number): DialGeo {
  'worklet';
  const unit = Math.max(0.74, Math.min(1.15, Math.min(w / 393, h / 852)));
  const R = Math.min(150 * unit, w * 0.38);
  const cy = top + 380 * unit;
  return { w, h, unit, cx: w / 2, cy, R, arcX: Math.min(w / 2 - 22, R + 30 * unit), arcY: R + 50 * unit, gnomon: R * 0.45 };
}

export function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

/** The chosen time (hours) at loop time t. */
export function timeAt(t: number) {
  'worklet';
  if (t < 1.4) return REST_TIME;
  if (t < 4.4) return REST_TIME + (15.5 - REST_TIME) * easeInOut(seg(t, 1.4, 4.4));
  if (t < 5.6) {
    // the snap: a 6-minute overshoot that dies away
    const u = seg(t, 4.4, 4.7);
    return 15.5 + 0.1 * Math.sin(Math.PI * u) * (1 - u);
  }
  if (t < 7.6) return 15.5 + (REST_TIME - 15.5) * easeInOut(seg(t, 5.6, 7.6));
  return REST_TIME;
}

/** The finger on the sun at loop time t (alpha). */
export function fingerAt(t: number) {
  'worklet';
  if (t >= 1.2 && t < 4.65) return seg(t, 1.2, 1.4) * (1 - seg(t, 4.45, 4.65));
  if (t >= 5.4 && t < 7.8) return seg(t, 5.4, 5.6) * (1 - seg(t, 7.6, 7.8));
  return 0;
}

/** The sun's direction [east, north, up] at time τ. */
export function sunDir(tau: number): [number, number, number] {
  'worklet';
  const h = ((tau - 12) * 15 * Math.PI) / 180;
  return [-Math.sin(h), -SIN_LAT * Math.cos(h), COS_LAT * Math.cos(h)];
}

/** The sun handle on the sky arc (screen point) at time τ. */
export function sunOnArc(g: DialGeo, tau: number): [number, number] {
  'worklet';
  const b = Math.PI * (1 - (tau - 6) / 12);
  return [g.cx + g.arcX * Math.cos(b), g.cy - g.arcY * Math.sin(b)];
}

/** The time a finger at (x, y) asks for: the sun's place on the arc above the dial, or the
 direction of the shadow on the dial itself. */
export function timeFromFinger(g: DialGeo, x: number, y: number, onDial: boolean) {
  'worklet';
  if (onDial) {
    // the shadow points at the finger: its angle from north (clockwise) is the hour line
    const a = Math.atan2(x - g.cx, g.cy - y);
    const clamped = Math.max(-1.45, Math.min(1.45, a));
    return 12 + (Math.atan(Math.tan(clamped) / SIN_LAT) * 12) / Math.PI;
  }
  const b = Math.atan2((g.cy - y) / g.arcY, (x - g.cx) / g.arcX);
  const bb = b < -Math.PI / 2 ? Math.PI : Math.max(0, b);
  return 6 + 12 * (1 - bb / Math.PI);
}

/** Keeps a dragged time inside 7:00 … 17:00 with a rubber band. */
export function rubberTime(tau: number) {
  'worklet';
  if (tau > T_MAX) return T_MAX + (tau - T_MAX) / (1 + (tau - T_MAX) / 0.4);
  if (tau < T_MIN) return T_MIN - (T_MIN - tau) / (1 + (T_MIN - tau) / 0.4);
  return tau;
}

/** The quarter hour a time shows as (and snaps to). */
export function quarterOf(tau: number) {
  'worklet';
  return Math.min(T_MAX, Math.max(T_MIN, Math.round(tau * 4) / 4));
}

/** The shader's uniforms for a time and a finger [x, y, alpha]. */
export function uniformsOf(g: DialGeo, tau: number, finger: number[]) {
  'worklet';
  const s = sunDir(tau);
  const [sx, sy] = sunOnArc(g, tau);
  return {
    size: [g.w, g.h],
    dial: [g.cx, g.cy, g.R, g.unit],
    sun: [s[0], s[1], s[2], tau],
    handle: [sx, sy, g.gnomon, 0],
    finger: [finger[0], finger[1], finger[2], 0],
  };
}
