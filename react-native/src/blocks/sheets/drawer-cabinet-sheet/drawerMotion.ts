/*
 Drawer Cabinet Sheet — the drawer's travel and the things rattling in it.

 The drawer's opening (pt pulled out of the cabinet) is a pure function of the loop time. Every item
 rides in its compartment on a spring along the drawer's travel (and a little turn): when the
 drawer accelerates the item lags, hits the divider's slack, bounces and settles. Under autoPlay
 the items' response to the scripted travel is integrated once at mount into a table (240 samples a
 second over one loop, starting and ending at rest), so each frame is a lookup — deterministic, and
 the frame at t = LOOP is the frame at 0. Under touch the same spring is stepped live with the real
 drawer's acceleration.

 LOOP = 8 s: rest 0–1.2 (closed) · pulled to half 1.2–2.2 · to full 2.8–3.6 · the blue pen picked
 4.0–5.0 (lifts 0.25 s, flies 0.6 s, lands) · soft close 5.6–7.0 (fast, then the damper eases it
 home over the last few centimetres) · the desk clears 7.0–7.5 · rest.
*/

export const LOOP = 8;
export const PICK_AT = 4.0;
export const LIFT_FOR = 0.25;
export const FLY_FOR = 0.6;
export const CLOSE_AT = 5.6;
export const CLOSE_FOR = 1.4;
export const CLEAR_AT = 7.0;
/** Samples per second of the autoPlay table. */
export const RATE = 240;

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}

export function easeInOutCubic(v: number) {
  'worklet';
  const x = clamp01(v);
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function easeOutCubic(v: number) {
  'worklet';
  return 1 - Math.pow(1 - clamp01(v), 3);
}

/** A finger's pull settling into a detent: a spring response (ω rad/s, ζ) from 0 to 1. */
function settle(tau: number, w: number, z: number) {
  'worklet';
  if (tau <= 0) return 0;
  const wd = w * Math.sqrt(1 - z * z);
  return 1 - Math.exp(-z * w * tau) * (Math.cos(wd * tau) + ((z * w) / wd) * Math.sin(wd * tau));
}

/** Soft close: pushed at speed, then the damper takes the last stretch slowly (ease-out quint). */
export function softClose(u: number) {
  'worklet';
  return 1 - Math.pow(1 - clamp01(u), 5);
}

/** The drawer's opening in the loop, for detents `half` and `full`. */
export function autoOpen(t: number, half: number, full: number) {
  'worklet';
  if (t < 1.2) return 0;
  if (t < 2.8) return half * settle(t - 1.2, 8, 0.72);
  if (t < CLOSE_AT) return half + (full - half) * settle(t - 2.8, 9, 0.68);
  return full * (1 - softClose((t - CLOSE_AT) / CLOSE_FOR));
}

export type ItemSpring = {
  /** Room to move along the drawer's travel either way (pt) and how hard it bounces off a divider. */
  slack: number;
  bounce: number;
  /** Spring of the felt / friction holding it near its place (ω rad/s, ζ), and how much it turns. */
  w: number;
  z: number;
  turn: number;
};

/**
 One step of an item's spring along the travel: `a` is the drawer's acceleration (pt/s², + = moving
 out faster, toward the top of the screen); the item lags behind it (+y in the drawer = toward the
 back). state = [y, vy].
*/
export function stepItem(s: ItemSpring, state: number[], a: number, dt: number) {
  'worklet';
  const acc = -s.w * s.w * state[0] - 2 * s.z * s.w * state[1] + a * 0.9;
  state[1] += acc * dt;
  state[0] += state[1] * dt;
  if (state[0] > s.slack) {
    state[0] = s.slack;
    if (state[1] > 0) state[1] = -state[1] * s.bounce;
  } else if (state[0] < -s.slack) {
    state[0] = -s.slack;
    if (state[1] < 0) state[1] = -state[1] * s.bounce;
  }
}

/** The autoPlay table: per item, y and turn at RATE samples a second over one loop. */
export function buildTable(springs: ItemSpring[], half: number, full: number) {
  const n = Math.round(LOOP * RATE);
  const dt = 1 / RATE;
  const ys: number[][] = springs.map(() => new Array(n + 1).fill(0));
  const states = springs.map(() => [0, 0]);
  // Drawer acceleration by central differences of the scripted opening (+ = outward).
  const open = (t: number) => autoOpen(Math.max(0, Math.min(LOOP, t)), half, full);
  for (let i = 0; i <= n; i++) {
    const t = i * dt;
    const a = (open(t + dt) - 2 * open(t) + open(t - dt)) / (dt * dt);
    springs.forEach((s, k) => {
      stepItem(s, states[k], a, dt);
      ys[k][i] = states[k][0];
    });
  }
  // The table must close the loop exactly: ease any last residue to 0 over the final second.
  for (let k = 0; k < springs.length; k++) {
    for (let i = n - RATE; i <= n; i++) ys[k][i] *= 1 - easeInOutCubic((i - (n - RATE)) / RATE);
  }
  return ys;
}

/** An item's lag at loop time t from the table (linear between samples). */
export function tableAt(ys: number[], t: number) {
  'worklet';
  const f = clamp01(t / LOOP) * (ys.length - 1);
  const i = Math.floor(f);
  const j = Math.min(ys.length - 1, i + 1);
  return ys[i] + (ys[j] - ys[i]) * (f - i);
}

/**
 A pick: from its place in the drawer the item lifts (0.25 s, eases toward the camera), flies in
 an arc to its place on the desk (0.6 s, ease-in-out) and lands with a small bounce. Writes
 [x, y, rot, lift, scale] into out; `u` is seconds since the pick began.
*/
export function pickPose(fx: number, fy: number, frot: number, tx: number, ty: number, trot: number, u: number, out: number[]) {
  'worklet';
  if (u <= 0) {
    out[0] = fx; out[1] = fy; out[2] = frot; out[3] = 0; out[4] = 1;
    return out;
  }
  if (u < LIFT_FOR) {
    const k = easeOutCubic(u / LIFT_FOR);
    out[0] = fx; out[1] = fy - 6 * k; out[2] = frot * (1 - 0.4 * k); out[3] = k; out[4] = 1 + 0.1 * k;
    return out;
  }
  const v = (u - LIFT_FOR) / FLY_FOR;
  if (v < 1) {
    const s = easeInOutCubic(v);
    const arc = 60 * Math.sin(Math.PI * s);
    out[0] = fx + (tx - fx) * s;
    out[1] = fy - 6 + (ty - fy + 6) * s - arc;
    out[2] = frot * 0.6 * (1 - s) + trot * s + 0.12 * Math.sin(Math.PI * s);
    out[3] = 1 - 0.5 * Math.max(0, s - 0.6) / 0.4;
    out[4] = 1 + 0.1 * out[3];
    return out;
  }
  const b = u - LIFT_FOR - FLY_FOR;
  const bounce = b < 0.5 ? Math.exp(-b * 10) * Math.sin(b * 34) : 0;
  const settleLift = Math.max(0, 0.5 * (1 - b / 0.12));
  out[0] = tx; out[1] = ty; out[2] = trot; out[3] = settleLift; out[4] = 1 + 0.05 * settleLift - 0.03 * bounce;
  return out;
}
