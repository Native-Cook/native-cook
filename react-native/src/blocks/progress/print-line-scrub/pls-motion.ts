// Print Line Scrub — every motion value. One loop time drives the autoplay (`lookAt`), a finger drives the same
// values live; the physics on release are closed-form springs, so the loop and a real release are the same maths.
//
//   t (s)      phase
//   0.0–0.4    rest: the pin's frame, the line creeps (invisible), a warm pulse through the frost every 1.4 s
//   0.4–0.65   press: the capsule ducks under the line, the gizmo stretches, the label becomes "Layer n / 480"
//   0.5–1.5    drag down to the hips
//   1.5–2.3    hold, tilt to 3.5° (the gasp at 1.6 s: the cut is exposed just above the capsule's rim; yaw has been rising since 1.0)
//   2.3–3.6    drag up through the waist to the shoulders, the numbers race through the head
//   3.6        release: the plane catches up to the live line on { 20, 90, 1.2 } with the release velocity
//   3.6–4.6    catch-up (the band widens with speed 14 → 40 pt), yaw home on { 16, 120, 1 }
//   4.3–4.7    the capsule covers the seam again on { 18, 260, 1 }; 4.4–5.1 the dots overshoot, re-pack, flash
//   4.6–6.2    the last layers cool (thermal memory), dots re-packed
//   6.2–7.6    rest

import { VOLUME } from './pls-volume';

export const LOOP = 7.6;
export const LAYERS = 480;
/** the live line, world height (the pin's seam at screen y 470 of a 690 → 190 axis). */
export const H_LIVE = 0.44;
/** resting percentage at the live line: round(layerAt(H_LIVE) / LAYERS * 100) = round(211 / 480 * 100) */
export const PCT_LIVE = 44;
export const H_MIN = 0;
export const H_MAX = 1;

export const SPRING = {
  catchUp: { damping: 20, stiffness: 90, mass: 1.2 },
  yaw: { damping: 16, stiffness: 120, mass: 1 },
  duck: { damping: 18, stiffness: 260, mass: 1 },
  touch: { damping: 22, stiffness: 380, mass: 1 },
  arrow: { damping: 16, stiffness: 300, mass: 0.8 },
  pill: { damping: 12, stiffness: 300, mass: 1 },
} as const;

export const GESTURE = {
  duckPt: 62, // the capsule drops this far under the line while held, so the cut rides ~10 pt above its top rim
  yawPerPt: 0.03, // degrees per pt of horizontal drag
  yawMax: 4, // degrees
  rubberPt: 6, // the bounce past either end of the range
  rubberSlack: 120,
  idleToLoop: 2, // s after the last touch before the loop eases back in
  tickEvery: 10, // layers per selection haptic
  releaseVelocityScale: 1,
} as const;

export const HEAT = {
  gain: 1.6, // heat at release = |h − live| × gain
  tau: 0.5, // s, × (1 + |v| / 1200)
  tauV: 1200,
  bandMin: 14,
  bandMax: 40,
  speedFull: 1400, // pt/s of plane speed at which the band is at its widest
} as const;

export const BEAD_PERIOD = 1.4; // s per lap while held (a warm pulse at rest)

export const DOTS = {
  followDamping: 14,
  followStiffness: 170,
  massBase: 0.6,
  massStep: 0.02,
  delayStep: 0.01, // s per dot
  tailMax: 22, // pt
  overshoot: 5, // pt
  flashLength: 0.12, // s
  flashStagger: 0.02, // s
};

// ---- helpers (worklets: declared above their callers) ----------------------------------------------------------------

export function clamp01(x: number): number {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}
export function mix(a: number, b: number, t: number): number {
  'worklet';
  return a + (b - a) * t;
}
export function seg(t: number, a: number, b: number): number {
  'worklet';
  return clamp01((t - a) / (b - a));
}
export function easeInOut(x: number): number {
  'worklet';
  const k = clamp01(x);
  return k * k * (3 - 2 * k);
}
export function easeOutCubic(x: number): number {
  'worklet';
  const k = 1 - clamp01(x);
  return 1 - k * k * k;
}
/** past the ends the position gives way: limit + over / (1 + over / slack) */
export function rubber(over: number, slack: number): number {
  'worklet';
  return over / (1 + over / slack);
}

/**
 * Displacement of a damped spring from its target after `t` seconds, released at displacement x0 with velocity v0.
 * (Same maths as Reanimated's withSpring: m x'' + c x' + k x = 0.)
 */
export function springDisp(t: number, x0: number, v0: number, damping: number, stiffness: number, mass: number): number {
  'worklet';
  if (t <= 0) return x0;
  const w0 = Math.sqrt(stiffness / mass);
  const z = damping / (2 * Math.sqrt(stiffness * mass));
  if (z < 0.9999) {
    const wd = w0 * Math.sqrt(1 - z * z);
    return Math.exp(-z * w0 * t) * (x0 * Math.cos(wd * t) + ((v0 + z * w0 * x0) / wd) * Math.sin(wd * t));
  }
  if (z > 1.0001) {
    const s = w0 * Math.sqrt(z * z - 1);
    const r1 = -z * w0 + s;
    const r2 = -z * w0 - s;
    const c2 = (v0 - r1 * x0) / (r2 - r1);
    const c1 = x0 - c2;
    return c1 * Math.exp(r1 * t) + c2 * Math.exp(r2 * t);
  }
  return Math.exp(-w0 * t) * (x0 + (v0 + w0 * x0) * t);
}

/** velocity of the spring above (central difference, enough for a hand-off) */
export function springVel(t: number, x0: number, v0: number, damping: number, stiffness: number, mass: number): number {
  'worklet';
  const e = 0.004;
  return (
    (springDisp(t + e, x0, v0, damping, stiffness, mass) - springDisp(Math.max(t - e, 0), x0, v0, damping, stiffness, mass)) /
    (t < e ? t + e : 2 * e)
  );
}

// ---- the figure's volume table -----------------------------------------------------------------------------------------

const N = VOLUME.length - 1;

/** fraction of the material below world height h (0…1), from the baked table */
export function volumeBelow(h: number): number {
  'worklet';
  const k = clamp01(h) * N;
  const i = Math.min(Math.floor(k), N - 1);
  return mix(VOLUME[i], VOLUME[i + 1], k - i);
}

export function layerAt(h: number): number {
  'worklet';
  return Math.round(clamp01(h) * LAYERS);
}

/**
 * Progress at cut height h: the layer's share of the job, so the percentage always equals round(layer / LAYERS * 100)
 * and never contradicts "Layer n / 480". The dotted bar and the ground tint follow the same value. (Before the
 * 2026-11-16 gate audit this was the calibrated VOLUME curve with the pin's 84% live line.)
 */
export function percentAt(h: number): number {
  'worklet';
  return (layerAt(h) / LAYERS) * 100;
}

// ---- the autoplay timeline ----------------------------------------------------------------------------------------------

export const T = {
  press: 0.4,
  pressEnd: 0.65,
  dragDown0: 0.5,
  dragDown1: 1.5,
  hold1: 2.3,
  dragUp1: 3.6,
  release: 3.6,
  duckBack: 4.3, // the capsule covers the seam again (release + 0.7)
  flash: 4.55,
  cool: 6.2,
  H_KNEES: 0.3, // each thigh and arm has its own silhouette-derived section
  H_SHOULDERS: 0.6,
  YAW_HOLD: 3.5, // degrees
  YAW_UP: 1,
} as const;

export type Look = {
  h: number; // cut height
  duck: number; // pt the capsule sits below the line, 0…GESTURE.duckPt
  yaw: number; // radians
  heat: number; // 0…1
  band: number; // pt
  press: number; // 0…1: touch-down state (lifted, label "Layer n")
  bead: number; // bead azimuth (rad)
  speed: number; // pt/s of the line (for the dots' tail, the band)
  flash: number; // 0…1 progress of the arrival flash (or −1)
  over: number; // dots overshoot, pt (signed)
  held: number; // 0…1: label crossfade
};

export const REST_LOOK: Look = {
  h: H_LIVE,
  duck: 0,
  yaw: 0,
  heat: 0,
  band: HEAT.bandMin,
  press: 0,
  bead: 0,
  speed: 0,
  flash: -1,
  over: 0,
  held: 0,
};

function pathH(t: number): number {
  'worklet';
  // the finger's path of the line before release, as a function of loop time
  if (t < T.dragDown0) return H_LIVE;
  if (t < T.dragDown1) return mix(H_LIVE, T.H_KNEES, easeInOut(seg(t, T.dragDown0, T.dragDown1)));
  if (t < T.hold1) return T.H_KNEES;
  return mix(T.H_KNEES, T.H_SHOULDERS, easeInOut(seg(t, T.hold1, T.dragUp1)));
}

/** the loop's state at loop time t (a pure function: first and last frames are the pin's) */
export function lookAt(tIn: number): Look {
  'worklet';
  const t = ((tIn % LOOP) + LOOP) % LOOP;
  const speedPt = (h0: number, h1: number, dt: number) => (Math.abs(h1 - h0) / dt) * 500;
  let h = H_LIVE;
  let speed = 0;
  let duck = 0;
  let press = 0;
  let yaw = 0;
  let heat = 0;
  let flash = -1;
  let over = 0;
  let held = 0;
  const duckMax = GESTURE.duckPt;
  if (t < T.release) {
    h = pathH(t);
    speed = speedPt(pathH(t - 0.01), pathH(t + 0.01), 0.02);
    const p = seg(t, T.press, T.pressEnd);
    press = 1 - Math.pow(1 - p, 3);
    // the duck is a spring: anticipation then settle
    duck = duckMax * (1 + springDisp(Math.max(t - T.press, 0), -1, 0, SPRING.duck.damping, SPRING.duck.stiffness, SPRING.duck.mass));
    if (t < T.press) duck = 0;
    held = seg(t, T.press, T.press + 0.2);
    const yawT = t < T.hold1 ? T.YAW_HOLD : mix(T.YAW_HOLD, T.YAW_UP, easeInOut(seg(t, T.hold1, T.dragUp1)));
    yaw = (yawT * (1 + springDisp(Math.max(t - 1.0, 0), -1, 0, SPRING.yaw.damping, SPRING.yaw.stiffness, SPRING.yaw.mass)) * Math.PI) / 180;
    if (t < 1.0) yaw = 0;
    // the first half second of the drag winds the heat up a little: playing with it warms the print
    heat = clamp01(Math.abs(h - H_LIVE) * HEAT.gain + 0.12 * press);
  } else {
    const tr = t - T.release;
    const h0 = pathH(T.release - 0.001);
    const v0 = (pathH(T.release - 0.0005) - pathH(T.release - 0.0015)) / 0.001; // world units / s at release
    const disp = springDisp(tr, h0 - H_LIVE, v0, SPRING.catchUp.damping, SPRING.catchUp.stiffness, SPRING.catchUp.mass);
    h = H_LIVE + disp;
    const vel = springVel(tr, h0 - H_LIVE, v0, SPRING.catchUp.damping, SPRING.catchUp.stiffness, SPRING.catchUp.mass);
    speed = Math.abs(vel) * 500;
    held = 1 - seg(t, T.duckBack + 0.1, T.duckBack + 0.35);
    press = 1 - seg(t, T.release, T.release + 0.4);
    // capsule covers the seam again after the line has arrived
    const td = Math.max(t - T.duckBack, 0);
    duck =
      t < T.duckBack ? duckMax : Math.max(duckMax * springDisp(td, 1, 0, SPRING.duck.damping, SPRING.duck.stiffness, SPRING.duck.mass), -4);
    const yawTr = springDisp(tr, (T.YAW_UP * Math.PI) / 180, 0, SPRING.yaw.damping, SPRING.yaw.stiffness, SPRING.yaw.mass);
    yaw = yawTr;
    // thermal memory: the release leaves the top layers glowing, decays over τ
    const heat0 = clamp01(Math.abs(h0 - H_LIVE) * HEAT.gain + 0.12);
    const tau = HEAT.tau * (1 + Math.abs(v0 * 500) / HEAT.tauV);
    heat = heat0 * Math.exp(-tr / tau) * (1 - seg(t, T.cool, 7.0));
    // the dots: overshoot and flash when the line has arrived
    const fl = (t - T.flash) / 0.6;
    if (fl >= 0 && fl <= 1) flash = fl;
    over =
      t < T.flash
        ? 0
        : 5 * seg(t, T.flash, T.flash + 0.05) * Math.exp(-(t - T.flash) * 7) * Math.cos((t - T.flash) * 24) * (t < 5.3 ? 1 : 0);
  }
  const bandSpeed = clamp01(speed / HEAT.speedFull);
  const band = mix(HEAT.bandMin, HEAT.bandMax, Math.max(clamp01(heat), bandSpeed));
  // the bead runs while held and during the return; at rest a faint pulse sits at the same angle
  const bead = (t / (LOOP / 5)) * Math.PI * 2; // five laps per loop, so the seam is exact
  return { h, duck, yaw, heat, band, press, bead, speed, flash, over, held };
}
