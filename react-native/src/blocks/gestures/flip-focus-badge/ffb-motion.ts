// Flip Focus Badge — every motion value, as pure functions of the loop time `t` (so the loop, a pinned frame and the
// offline harness agree) and the constants the finger uses. Seconds, degrees, design pt.
//
// The loop (LOOP 8.1 s: the concept's 8.5 s with the rewind cut from 1.6 to 1.2 s):
//   0 – 0.5     rest (= the pin: "10", ring at its pin pose, satellite at 24 min, line at 40 %)
//   0.5 – 0.6   anticipation: the badge presses to 1.03 (touch-down)
//   0.6 – 1.5   the badge turns to 70° under a finger; reflections slide across the numerals, the ring lags
//   1.5 – 1.9   hold at 70° (the gasp frame, 1.7 s): deep perspective, the ring in front, the back coming into view
//   1.9 – 2.4   it lands on its back: the session starts, the hint cross-slides to "focusing…"
//   2.4 – 4.0   the session runs (shown fast): the clock 00:00 → 36:00, the satellite 40 % → 100 %, the line fills
//   4.0 – 5.0   flip back (the session lands): the numeral presses flat into the glass over the first half-turn, is exchanged at edge-on
//               (10 → 11) and punches out again (depth 0.04 → 1, overshoot 1.2), "+" then "hr" pop 40 / 80 ms later
//   4.05 – 4.7  the hour is reached with the exchange: the line's highlight pass, the total rolls to 11h 00m (right to
//               left) while the line wipes to 0 and its labels → 11h/12h, so badge, total and line agree from 4.2 s
//   4.8 – 5.8   hold on 11 hr / 11h 00m
//   5.8 – 7.0   rewind: a quick reverse turn; 11 → 10 behind the plate, the line and total return to the pin's
//   7.0 – 8.1   rest (identical to frame 0)
// Reduced motion: no loop, the rest frame, 200 ms crossfades for state changes (the component handles it).

export const LOOP = 8.1;
export const POSTER_T = 1.7; // the gasp frame
export const REST_T = 0;

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}
export function mix(a: number, b: number, k: number) {
  'worklet';
  return a + (b - a) * k;
}
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
export function smooth01(x: number) {
  'worklet';
  const k = clamp01(x);
  return k * k * (3 - 2 * k);
}
export function easeInOut(x: number) {
  'worklet';
  const k = clamp01(x);
  return k < 0.5 ? 4 * k * k * k : 1 - Math.pow(-2 * k + 2, 3) / 2;
}
export function easeOut(x: number, power: number) {
  'worklet';
  return 1 - Math.pow(1 - clamp01(x), power);
}
/** step response of a spring (response in seconds = 2π/ω, damping ratio ζ), 0 → 1; analytic, so frames are exact */
export function springAt(t: number, response: number, zeta: number) {
  'worklet';
  if (t <= 0) return 0;
  const w = (2 * Math.PI) / response;
  if (zeta >= 1) return 1 - Math.exp(-w * t) * (1 + w * t);
  const wd = w * Math.sqrt(1 - zeta * zeta);
  return 1 - Math.exp(-zeta * w * t) * (Math.cos(wd * t) + ((zeta * w) / wd) * Math.sin(wd * t));
}

/** The named springs this block uses (DESIGN-SYSTEM §8 names where one fits; raw numbers where the concept's character needs them). */
export const SPRING = {
  snap: { damping: 30.5, stiffness: 322.3, mass: 1 }, // press feedback
  glide: { damping: 22.1, stiffness: 157.9, mass: 1 }, // the 600 ms ease back to rest
  settle: { damping: 14, stiffness: 110, mass: 1.2 }, // to the nearer face (concept)
  tilt: { damping: 16, stiffness: 140, mass: 1 }, // pitch back to 0 (Pro layer)
  rise: { damping: 11, stiffness: 160, mass: 1 }, // the numeral punches out: overshoot ≈ 1.19 (Pro layer: damping 9 gave 1.3)
  pop: { damping: 19, stiffness: 195, mass: 1 }, // "+" and "hr" arriving
} as const;
/** the same springs as (response, ζ) for the analytic timeline */
export const RESP = {
  press: { response: 0.35, zeta: 0.85 },
  land: { response: 0.6, zeta: 0.8 },
  flip: { response: 0.7, zeta: 0.82 },
  rise: { response: 0.497, zeta: 0.435 }, // k 160, c 11
  pop: { response: 0.45, zeta: 0.68 },
} as const;

// ---- the timeline's times ----------------------------------------------------------------------------------------------
export const T = {
  press: 0.5,
  turn0: 0.6,
  turn1: 1.5,
  hold1: 1.9,
  land: 1.9,
  sess0: 2.4,
  sess1: 4.0,
  back: 4.0,
  rev0: 5.8,
  rev1: 7.0,
  hintIn0: 2.0,
  hintIn1: 2.3,
  hintOut0: 4.15,
  hintOut1: 4.45,
} as const;
export const YAW_PEEK = 70; // degrees at the gasp
const PEEK_DRIFT = 3; // degrees of slow drift during the hold

/** time since T.back at which the flip-back spring crosses 90° of its 180° (the front's edge-on instant) */
function crossTime(response: number, zeta: number, level: number) {
  let lo = 0;
  let hi = 2;
  for (let i = 0; i < 40; i++) {
    const mid = (lo + hi) / 2;
    if (springAt(mid, response, zeta) < level) lo = mid;
    else hi = mid;
  }
  return (lo + hi) / 2;
}
export const T_SWAP1 = T.back + crossTime(RESP.flip.response, RESP.flip.zeta, 0.5); // 10 → 11 at yaw 270°
/** the hour lands with the numeral: the total, the line's labels and the line itself change from the exchange on */
export const T_HOUR = T_SWAP1 - 0.12; // the total starts as the numeral presses flat, so it lands as 11 punches out
const T_WIPE = T_HOUR + 0.24; // after the line's highlight pass
const HOUR_ROLL = 0.5; // the total's roll, s
export const T_SWAP2 = T.rev0 + 0.6; // 11 → 10 in the reverse turn at yaw 180° (the back face: nothing to see)

/** the plate's yaw, continuous (0 → 70 → 180 → 360 → 0) */
export function plateYawAt(t: number) {
  'worklet';
  if (t < T.turn0) return 0;
  if (t < T.turn1) return YAW_PEEK * easeOut((t - T.turn0) / (T.turn1 - T.turn0), 2.2);
  if (t < T.land) return YAW_PEEK + PEEK_DRIFT * smooth01((t - T.turn1) / (T.land - T.turn1));
  if (t < T.back) return YAW_PEEK + PEEK_DRIFT + (180 - YAW_PEEK - PEEK_DRIFT) * springAt(t - T.land, RESP.land.response, RESP.land.zeta);
  if (t < T.rev0) return 180 + 180 * springAt(t - T.back, RESP.flip.response, RESP.flip.zeta);
  if (t < T.rev1) return 360 * (1 - easeInOut((t - T.rev0) / (T.rev1 - T.rev0)));
  return 0;
}

// ---- the ring: a viscous joint between plate and ring, simulated once (second order, ζ 0.5, 1.2 Hz) ------------------------
export const JOINT = { wn: 7.5, zeta: 0.5 };
export const JOINT_LAG = 45; // degrees: the soft limit of the lag
/** one semi-implicit step of the joint: the ring chases the plate with a lag and keeps turning a little when the plate stops */
export function jointStep(theta: number, omega: number, target: number, dt: number): [number, number] {
  'worklet';
  const a = JOINT.wn * JOINT.wn * (target - theta) - 2 * JOINT.zeta * JOINT.wn * omega;
  const w2 = omega + a * dt;
  return [theta + w2 * dt, w2];
}
const RING_N = Math.round(LOOP * 120);
const RING_TABLE: number[] = (() => {
  let th = 0;
  let om = 0;
  const out: number[] = [];
  const dt = 1 / 120;
  for (let loop = 0; loop < 4; loop++) {
    for (let i = 0; i <= RING_N; i++) {
      const time = i * dt;
      const [a, b] = jointStep(th, om, plateYawAt(Math.min(time, LOOP)), dt);
      th = a;
      om = b;
      if (loop === 3) out.push(th);
    }
  }
  // make the loop's last sample equal its first (the residual after 1.6 s of rest is a few hundredths of a degree)
  const err = out[RING_N] - out[0];
  const o0 = out[0];
  for (let i = 0; i <= RING_N; i++) out[i] -= o0 + (err * i) / RING_N; // and the ring's rest is exactly the plate's
  return out;
})();
/** the joint never lets the ring fall further than ~45° behind (a soft limit), whatever the plate's speed */
export function limitLag(plate: number, ring: number) {
  'worklet';
  const lag = plate - ring;
  return plate - JOINT_LAG * Math.tanh(lag / JOINT_LAG);
}
export function ringYawAt(t: number) {
  'worklet';
  const x = (Math.min(Math.max(t, 0), LOOP) / LOOP) * RING_N;
  const i = Math.min(Math.floor(x), RING_N - 1);
  const ring = RING_TABLE[i] + (RING_TABLE[i + 1] - RING_TABLE[i]) * (x - i);
  return limitLag(plateYawAt(t), ring);
}

// ---- the look: everything the screen shows, as a function of t ---------------------------------------------------------
export type Look = {
  yaw: number;
  pitch: number;
  ringYaw: number;
  depth: number;
  value: number;
  clock: number; // session seconds on the back
  sat: number; // satellite lap 0 … 1
  fill: number; // the progress line 0 … 1
  hint: number; // 0 "flip your phone…", 1 "focusing…"
  sheetK: number; // 0 "10h 24m" … 1 "11h 00m"
  labelK: number; // 0 "10h … 11h" … 1 "11h … 12h"
  glintPlus: number;
  glintHead: number;
  glintSat: number;
  press: number; // badge press scale
  popPlus: number;
  popHr: number;
  env: number; // the studio's sway, radians
  sweep: number; // the progress line's highlight pass, 0 … 1 (−1 = off)
  tPhase: number; // loop time (for the component)
};

export const REST_FILL = 0.4; // 24 of 60 minutes
export const SESSION_SECS = 2160; // the session shown: 36 minutes, 24 → 60 min of the hour

/** distance covered by a move at constant rate with `r`-long ramps at both ends, 0 → 1 over k = 0 … 1 */
function trapezoid(k: number, r: number) {
  'worklet';
  const x = clamp01(k);
  const total = 1 - r;
  if (x < r) return (x * x) / (2 * r) / total;
  if (x <= 1 - r) return (x - r / 2) / total;
  const y = x - (1 - r);
  return (1 - 1.5 * r + y - (y * y) / (2 * r)) / total;
}

function pulse(t: number, c: number, w: number) {
  'worklet';
  const x = (t - c) / w;
  return Math.exp(-x * x);
}

export function lookAt(tRaw: number): Look {
  'worklet';
  const t = Math.min(Math.max(tRaw, 0), LOOP);
  const yaw = plateYawAt(t);
  // trackball: the badge leans back a little during the hold so the numerals' tops catch the light
  const pitch = t > 1.0 && t < 2.5 ? 10 * Math.pow(Math.sin((Math.PI * (t - 1.0)) / 1.5), 2) : 0;
  // depth: press flat over the first half-turn back, punch out after the exchange
  let depth = 1;
  if (t >= T.back && t < T_SWAP1) depth = 1 - 0.96 * easeInOut((t - T.back) / (T_SWAP1 - T.back));
  else if (t >= T_SWAP1 && t < T.rev0) depth = 1 - 0.96 * (1 - springAt(t - T_SWAP1, RESP.rise.response, RESP.rise.zeta));
  const value = t >= T_SWAP1 && t < T_SWAP2 ? 11 : 10;
  // the session: the clock, the satellite and the line move together
  const runK = trapezoid(seg(t, T.sess0, T.sess1), 0.1); // a constant-rate orbit with soft ends
  const clock = t >= T.back + 0.45 ? 0 : SESSION_SECS * runK;
  let fill = REST_FILL + (1 - REST_FILL) * runK;
  // the line restarts from 0 once the hour is reached, and refills to the pin's 40 % in the rewind
  if (t >= T_WIPE && t < T_WIPE + 0.4) fill = 1 - easeInOut((t - T_WIPE) / 0.4);
  else if (t >= T_WIPE + 0.4 && t < T.rev0 + 0.2) fill = 0;
  else if (t >= T.rev0 + 0.2) fill = REST_FILL * easeInOut((t - T.rev0 - 0.2) / 0.9);
  // the satellite keeps going round: at the hour it starts the next lap from the same place (1 ≡ 0), then rewinds forward
  const sat = t < T_WIPE ? fill : t < T.rev0 + 0.2 ? 0 : fill;
  const hint = Math.max(smooth01((t - T.hintIn0) / (T.hintIn1 - T.hintIn0)) - smooth01((t - T.hintOut0) / (T.hintOut1 - T.hintOut0)), 0);
  // the sheet's total and the line's labels
  const sheetFwd = easeInOut((t - T_HOUR) / HOUR_ROLL);
  const sheetRev = easeInOut((t - (T.rev0 + 0.1)) / 1.0);
  const sheetK = t < T.rev0 + 0.1 ? sheetFwd : 1 - sheetRev;
  const labelK = t < T.rev0 + 0.1 ? smooth01((t - T_WIPE) / 0.4) : 1 - smooth01((t - (T.rev0 + 0.1)) / 0.6);
  // sparkles catch the light in the order the reflection reaches them; the satellite glints at the lap, the "+" on landing
  const glintPlus = Math.max(pulse(t, 0.86, 0.07), pulse(t, 4.7, 0.07));
  const glintHead = Math.max(pulse(t, 1.08, 0.07), pulse(t, 4.78, 0.07));
  const glintSat = Math.max(pulse(t, 1.27, 0.07), pulse(t, 4.04, 0.06), pulse(t, 4.86, 0.07));
  // the press (touch-down), released as the badge lands
  const press =
    1 + 0.03 * (springAt(t - T.press, RESP.press.response, RESP.press.zeta) - springAt(t - 2.0, RESP.press.response, RESP.press.zeta));
  const popPlus = t < T_SWAP1 || t > T.rev0 ? 1 : 0.6 + 0.4 * springAt(t - T_SWAP1 - 0.04, RESP.pop.response, RESP.pop.zeta);
  const popHr = t < T_SWAP1 || t > T.rev0 ? 1 : 0.6 + 0.4 * springAt(t - T_SWAP1 - 0.08, RESP.pop.response, RESP.pop.zeta);
  // the studio sways like a hand (whole cycle per loop)
  const env = ((5 * Math.PI) / 180) * Math.sin((2 * Math.PI * t) / LOOP);
  // the line's highlight pass (240 ms) as the new numeral lands, just before the hour's wipe
  const sweepT = (t - T_HOUR) / 0.24;
  const sweep = sweepT >= 0 && sweepT <= 1 ? sweepT : -1;
  return {
    yaw,
    pitch,
    ringYaw: ringYawAt(t),
    depth,
    value,
    clock,
    sat,
    fill,
    hint,
    sheetK,
    labelK,
    glintPlus,
    glintHead,
    glintSat,
    press,
    popPlus,
    popHr,
    env,
    sweep,
    tPhase: t,
  };
}

export const REST: Look = lookAt(0);

/** the sheet's three changing digits roll right to left, 90 ms apart; slot 0 = minutes ones, 1 = minutes tens, 2 = hours ones */
export function rollAt(sheetK: number, slot: number) {
  'worklet';
  const own = 0.6;
  const stag = (1 - own) / 2;
  return easeInOut((sheetK - slot * stag * 0.9) / own);
}

// ---- the finger -----------------------------------------------------------------------------------------------------
export const GESTURE = {
  yawPerPt: 0.6, // degrees per pt of horizontal drag, 1:1
  pitchPerPt: 0.35,
  pitchMax: 28,
  pitchRubber: 0.3, // past the limit the badge follows at 30 %
  project: 0.18, // s: release projection τ (DESIGN-SYSTEM §8: 0.10 – 0.20)
  flickTurns: 3, // at most three half-turn pairs more
  startBeyond: 90, // past this at release = start (or, from the back, stop)
  holdMs: 220, // long-press before the numeral presses into the plate
  holdDepth: 0.04,
  catchMs: 60,
  idleResume: 2.0, // s after the last touch before the loop eases back to rest
  resumeMs: 600,
  minSession: 60, // s: a shorter session is a look, not a start
  edgeWindow: 4, // degrees either side of edge-on in which a numeral exchange is allowed
} as const;

/** rubber band: the value follows at `k` once it passes `limit` */
export function rubber(v: number, limit: number, k: number) {
  'worklet';
  const a = Math.abs(v);
  if (a <= limit) return v;
  const over = a - limit;
  return Math.sign(v) * (limit + over * k);
}
