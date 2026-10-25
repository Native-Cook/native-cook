/*
 Thermostat Frost Dial — geometry, the autoPlay script and the touch simulation (worklets).

 LOOP = 9 s (set point T in °C):
   0.0 – 1.3   rest at 21.0°.
   1.3 – 3.4   turned colder to 16.0° (ease-in-out); frost follows 0.25 s behind, growing from
               the rim inward; the wall cools to blue.
   3.4 – 4.4   hold at 16°, frost at its fullest.
   4.4 – 6.8   turned warmer to 25.0°: the frost melts into beads (4.8 – 5.6), the wall glows
               amber and the air above the dial shimmers; the beads dry off 6.4 – 7.8.
   6.8 – 8.0   back to 21.0°.
   8.0 – 9.0   rest.
 The ring turns 13.5° per degree (270° for 10 – 30°). The big number shows T rounded to 0.5°,
 its digits rolling between steps.
*/

export const LOOP = 9;
export const REST = 21;
export const MIN_T = 10;
export const MAX_T = 30;
/** The room's own temperature: ticks between it and the set point are lit. */
export const ROOM = 20.5;
export const IDLE_BEFORE_BACK = 2;
export const BACK_FOR = 0.9;
export const MODE_AUTO = 0;
export const MODE_LIVE = 1;
export const MODE_BACK = 2;

const START = (135 * Math.PI) / 180;
const ARC = (270 * Math.PI) / 180;

export type DialGeo = { w: number; h: number; cx: number; cy: number; ro: number; ri: number };

export const HEADER_H = 112;
export const BOTTOM_H = 214;

export function makeDialGeo(w: number, h: number, insetTop: number, insetBottom: number): DialGeo {
  'worklet';
  const top = insetTop + HEADER_H + 20;
  const bottom = h - Math.max(insetBottom, 12) - BOTTOM_H - 16;
  const ro = Math.min(w * 0.44, 176, (bottom - top) / 2);
  return { w, h, cx: w / 2, cy: (top + bottom) / 2, ro, ri: ro * 0.77 };
}

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

/** Set point angle on screen (rad, y down; 10° at 135°, 30° at 405°). */
export function angleOf(T: number) {
  'worklet';
  return START + ((T - MIN_T) / (MAX_T - MIN_T)) * ARC;
}

/** The scripted set point at loop time t. */
export function scriptT(t: number) {
  'worklet';
  return (
    REST -
    5 * easeInOut(seg(t, 1.3, 3.4)) +
    9 * easeInOut(seg(t, 4.4, 6.8)) -
    4 * easeInOut(seg(t, 6.8, 8.0))
  );
}

export function coldOf(T: number) {
  'worklet';
  return clamp01((REST - T) / 5);
}
export function warmOf(T: number) {
  'worklet';
  return clamp01((T - REST) / 4);
}

export type DialFrame = { T: number; frost: number; wet: number; phase: number };

export function scriptFrame(t: number, f: DialFrame) {
  'worklet';
  f.T = scriptT(t);
  // Frost grows a beat behind the dial and melts as it warms.
  f.frost = coldOf(scriptT(Math.max(0, t - 0.25)));
  f.wet = easeInOut(seg(t, 4.8, 5.6)) * (1 - easeInOut(seg(t, 6.4, 7.8)));
  f.phase = t / LOOP;
}

export type DialSim = {
  mode: number;
  t: number;
  now: number;
  last: number;
  back: number;
  frame: DialFrame;
  /** Set point velocity (°/s) for the release spring, and whether a finger holds the ring. */
  v: number;
  held: boolean;
  /** The finger's last angle around the centre. */
  grab: number;
  /** The raw value the finger asks for (before rubber-banding). */
  raw: number;
  turnAt: number;
  from: number[];
};

export function makeSim(): DialSim {
  'worklet';
  return {
    mode: MODE_AUTO,
    t: 0,
    now: 0,
    last: 0,
    back: 0,
    frame: { T: REST, frost: 0, wet: 0, phase: 0 },
    v: 0,
    held: false,
    grab: 0,
    raw: REST,
    turnAt: 0,
    from: [REST, 0, 0, 0],
  };
}

/** Past the ends the ring resists: the overshoot is squashed toward ~4°. */
function rubber(raw: number) {
  'worklet';
  if (raw > MAX_T) {
    const o = raw - MAX_T;
    return MAX_T + o / (1 + o / 4);
  }
  if (raw < MIN_T) {
    const o = MIN_T - raw;
    return MIN_T - o / (1 + o / 4);
  }
  return raw;
}

export function simGrab(s: DialSim, angle: number) {
  'worklet';
  s.mode = MODE_LIVE;
  s.held = true;
  s.grab = angle;
  s.raw = s.frame.T;
  s.v = 0;
  s.turnAt = s.now;
  s.last = s.now;
}

/** The finger turns the ring: the change of its angle around the centre turns the set point. */
export function simTurn(s: DialSim, angle: number) {
  'worklet';
  const dt = s.now - s.turnAt;
  s.turnAt = s.now;
  let d = angle - s.grab;
  if (d > Math.PI) d -= 2 * Math.PI;
  if (d < -Math.PI) d += 2 * Math.PI;
  s.grab = angle;
  const dT = (d / ARC) * (MAX_T - MIN_T);
  s.raw += dT;
  const before = s.frame.T;
  s.frame.T = rubber(s.raw);
  if (dt > 0) s.v = s.v * 0.6 + ((s.frame.T - before) / dt) * 0.4;
  s.last = s.now;
}

export function simRelease(s: DialSim) {
  'worklet';
  s.held = false;
  s.last = s.now;
}

/** One frame; returns true when the loop restarts. */
export function simStep(s: DialSim, dt: number, auto: boolean, reduced: boolean) {
  'worklet';
  s.now += dt;
  const f = s.frame;
  if (s.mode === MODE_AUTO) {
    if (auto) s.t = (s.t + dt) % LOOP;
    scriptFrame(reduced ? 0 : s.t, f);
    return false;
  }
  f.phase += dt / LOOP;
  if (s.mode === MODE_LIVE) {
    if (!s.held) {
      // Release: a spring carries the finger's speed and settles on the nearest half degree.
      const target = Math.min(MAX_T, Math.max(MIN_T, Math.round((f.T + s.v * 0.12) * 2) / 2));
      const w = 14;
      const z = 0.8;
      const a = w * w * (target - f.T) - 2 * z * w * s.v;
      s.v += a * dt;
      f.T += s.v * dt;
      if (Math.abs(target - f.T) < 0.002 && Math.abs(s.v) < 0.02) {
        f.T = target;
        s.v = 0;
      }
    }
    // Frost creeps in over ~0.6 s and melts faster; what melts becomes beads that dry off.
    const cold = coldOf(f.T);
    const before = f.frost;
    f.frost += (cold - f.frost) * Math.min(1, dt * (cold > f.frost ? 2.2 : 3.5));
    const melted = Math.max(0, before - f.frost);
    f.wet = Math.min(1, f.wet + melted * 1.6);
    f.wet = Math.max(0, f.wet - dt * (0.12 + 0.45 * warmOf(f.T)));
    if (auto && !s.held && s.now - s.last > IDLE_BEFORE_BACK) {
      s.mode = MODE_BACK;
      s.back = 0;
      s.from[0] = f.T;
      s.from[1] = f.frost;
      s.from[2] = f.wet;
      s.from[3] = f.phase;
    }
    return false;
  }
  s.back = Math.min(1, s.back + dt / BACK_FOR);
  const k = easeInOut(s.back);
  f.T = s.from[0] + (REST - s.from[0]) * k;
  f.frost = s.from[1] * (1 - k);
  f.wet = s.from[2] * (1 - k);
  f.phase = s.from[3] + (Math.ceil(s.from[3]) - s.from[3]) * k;
  if (s.back >= 1) {
    s.mode = MODE_AUTO;
    s.t = 0;
    scriptFrame(0, f);
    return true;
  }
  return false;
}

export function dialUniforms(f: DialFrame, g: DialGeo) {
  'worklet';
  const T = Math.min(MAX_T + 1.5, Math.max(MIN_T - 1.5, f.T));
  const heating = T >= ROOM ? 1 : 0;
  return {
    size: [Math.max(1, g.w), Math.max(1, g.h)],
    dial: [g.cx, g.cy, g.ro, g.ri],
    ring: [angleOf(T) - angleOf(REST), angleOf(T), f.frost, f.wet],
    env: [easeInOut(coldOf(T)), easeInOut(warmOf(T)), f.phase - Math.floor(f.phase), 0],
    lit: [angleOf(ROOM), angleOf(Math.min(MAX_T, Math.max(MIN_T, Math.round(T * 2) / 2))), heating, 0],
  };
}

/**
 The rolling digits of T rounded to 0.5°: wheel positions for the tens, the ones and the half
 (0 = ".0", 1 = ".5"). Between steps a column rolls over the middle 60 % of the step, so it rests
 exactly on a whole digit whenever T holds.
*/
export function digitWheels(T: number) {
  'worklet';
  const k = Math.min(MAX_T, Math.max(MIN_T, T)) * 2;
  const base = Math.floor(k);
  const fr = k - base;
  const s = base + Math.min(1, Math.max(0, (fr - 0.2) / 0.6));
  const sm = base + (s - base) * (s - base) * (3 - 2 * (s - base));
  const half = sm - 2 * Math.floor(sm / 2);
  const u = sm / 2;
  const ones = Math.floor(u) + Math.min(1, Math.max(0, (u - Math.floor(u) - 0.5) * 2));
  const tens = Math.floor(ones / 10) + Math.min(1, Math.max(0, ones - Math.floor(ones / 10) * 10 - 9));
  return [tens, ones - Math.floor(ones / 10) * 10, half];
}
