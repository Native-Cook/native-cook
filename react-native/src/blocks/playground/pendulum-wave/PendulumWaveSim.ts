// Fifteen pendulums tuned so that in one cycle of CYCLE seconds the longest swings N times and
// each shorter one swings once more than its neighbour. Released together they drift out of
// phase into travelling waves, snakes and apparent chaos, and come back into line exactly at the
// end of the cycle. Plain arithmetic on a plain object, stepped on the UI thread, so it ports to
// Swift and Kotlin line for line.
//
// World units: the table is y = 0, y is up, the row of pendulums runs along z, and they swing
// along x. A pendulum's state is its bob's sideways offset x and its speed; a free pendulum is a
// harmonic oscillator advanced exactly (a rotation in phase space by ω·dt), so the phases do not
// drift however long it runs. While the comb holds them the bobs are pulled onto it by a stiff
// spring, stepped in fixed substeps.

/** Seconds the autoPlay script takes before it starts over (declared first: worklets below
 * capture it when they are created). */
export const LOOP = 36;
/** Pendulums in the row. */
export const COUNT = 15;
/** Seconds after which every pendulum is back in line. */
export const CYCLE = 30;
/** Swings of the longest pendulum in one cycle; pendulum k makes BASE_SWINGS + k. */
const BASE_SWINGS = 24;
/** The beam's height, the longest pendulum's length, the spacing along the row (world units). */
export const BEAM_Y = 12;
export const LONGEST = 10;
export const SPACING = 1;
/** Half the spread of each pendulum's V of string along the beam. */
export const V_HALF = 0.42;
/** The bob's radius. */
export const BOB_R = 0.36;
/** The furthest the comb takes the bobs aside, and its soft give past that. */
export const AMPLITUDE_MAX = 2;
const GIVE = 0.6;
// The comb's grip: a stiff spring (1/s²) and damping (1/s).
const GRIP_K = 520;
const GRIP_DAMP = 41;
const SUBSTEP = 1 / 240;
export const MAX_FRAME = 1 / 30;
/** Light trails: one sample every TRAIL_STEP s, TRAIL samples long. */
export const TRAIL = 24;
const TRAIL_STEP = 1 / 60;

/** The comb fades in over COMB_IN s and away over COMB_OUT s. */
const COMB_IN = 0.22;
const COMB_OUT = 0.35;

export const EVENT_GRAB = 1;
export const EVENT_RELEASE = 2;

export type PendulumSim = {
  x: number[];
  v: number[];
  /** Angular frequency (rad/s) and length of each pendulum. */
  w: number[];
  length: number[];
  /** Trail ring buffer: TRAIL past x per pendulum, newest at `trailHead`. */
  trail: number[][];
  trailHead: number;
  trailClock: number;
  /** The comb: held or not, where it is, how visible. */
  held: boolean;
  combX: number;
  combShown: number;
  /** Seconds since the last release, for the cycle readout (-1 before the first). */
  sinceRelease: number;
  time: number;
  events: number;
  eventsByUser: boolean;
};

export function createPendulums(): PendulumSim {
  'worklet';
  const x: number[] = [];
  const v: number[] = [];
  const w: number[] = [];
  const length: number[] = [];
  const trail: number[][] = [];
  for (let k = 0; k < COUNT; k += 1) {
    const swings = BASE_SWINGS + k;
    x.push(0);
    v.push(0);
    w.push((2 * Math.PI * swings) / CYCLE);
    // Length goes as the period squared: L = g / ω², scaled so the longest is LONGEST.
    length.push(LONGEST * (BASE_SWINGS / swings) * (BASE_SWINGS / swings));
    const t: number[] = [];
    for (let i = 0; i < TRAIL; i += 1) t.push(0);
    trail.push(t);
  }
  return {
    x,
    v,
    w,
    length,
    trail,
    trailHead: 0,
    trailClock: 0,
    held: false,
    combX: 0,
    combShown: 0,
    sinceRelease: -1,
    time: 0,
    events: 0,
    eventsByUser: false,
  };
}

/** The row position of pendulum k. */
export function rowZ(k: number) {
  'worklet';
  return (k - (COUNT - 1) / 2) * SPACING;
}

/** A sideways offset limited to AMPLITUDE_MAX with a soft give. */
export function rubber(x: number) {
  'worklet';
  const a = Math.abs(x);
  if (a <= AMPLITUDE_MAX) return x;
  return Math.sign(x) * (AMPLITUDE_MAX + GIVE * (1 - Math.exp(-(a - AMPLITUDE_MAX) / GIVE)));
}

export function combGrab(s: PendulumSim, x: number, byUser: boolean) {
  'worklet';
  s.held = true;
  s.combX = x;
  s.events |= EVENT_GRAB;
  if (byUser) s.eventsByUser = true;
}

export function combMove(s: PendulumSim, x: number) {
  'worklet';
  if (s.held) s.combX = rubber(x);
}

/** Lets the bobs go; `timed` starts the cycle readout (a comb set down at rest does not). */
export function combRelease(s: PendulumSim, byUser: boolean, timed: boolean) {
  'worklet';
  if (!s.held) return;
  s.held = false;
  s.sinceRelease = timed ? 0 : -1;
  s.events |= EVENT_RELEASE;
  if (byUser) s.eventsByUser = true;
}

export function pendulumStep(s: PendulumSim, frame: number) {
  'worklet';
  const dt = Math.min(frame, MAX_FRAME);
  s.time += dt;
  if (s.sinceRelease >= 0 && !s.held) s.sinceRelease += dt;
  if (s.held) {
    s.combShown = Math.min(1, s.combShown + dt / COMB_IN);
    const n = Math.max(1, Math.ceil(dt / SUBSTEP - 1e-6));
    const h = dt / n;
    for (let j = 0; j < n; j += 1) {
      for (let k = 0; k < COUNT; k += 1) {
        const a = GRIP_K * (s.combX - s.x[k]) - GRIP_DAMP * s.v[k];
        s.v[k] += a * h;
        s.x[k] += s.v[k] * h;
      }
    }
  } else {
    s.combShown = Math.max(0, s.combShown - dt / COMB_OUT);
    // Free: each pendulum turns through ω·dt in phase space, exactly.
    for (let k = 0; k < COUNT; k += 1) {
      const w = s.w[k];
      const c = Math.cos(w * dt);
      const sn = Math.sin(w * dt);
      const x = s.x[k];
      const u = s.v[k] / w;
      s.x[k] = x * c + u * sn;
      s.v[k] = (u * c - x * sn) * w;
    }
  }
  // Trails, sampled on a fixed clock.
  s.trailClock += dt;
  while (s.trailClock >= TRAIL_STEP) {
    s.trailClock -= TRAIL_STEP;
    s.trailHead = (s.trailHead + 1) % TRAIL;
    for (let k = 0; k < COUNT; k += 1) s.trail[k][s.trailHead] = s.x[k];
  }
}

/** The bob of pendulum k at sideways offset x: [x, y, z] in the world. */
export function bobAt(s: PendulumSim, k: number, x: number) {
  'worklet';
  const L = s.length[k];
  const off = Math.max(-L * 0.95, Math.min(L * 0.95, x));
  return [off, BEAM_Y - Math.sqrt(L * L - off * off), rowZ(k)];
}

// ---------------------------------------------------------------------------------------------
// Views and the camera. A view is an orbit around the scene's middle: yaw (degrees around the
// vertical, 0 = looking down the row from its end), pitch (degrees above the horizon), distance.

export const VIEW_THREE_QUARTER = 0;
export const VIEW_TOP = 1;
export const VIEW_SIDE = 2;
export const VIEW_NAMES = ['3/4', 'Top', 'Side'];
const VIEWS = [
  { yaw: 36, pitch: 20, dist: 34 },
  { yaw: 0, pitch: 76, dist: 29 },
  { yaw: 20, pitch: 6, dist: 31 },
];
/** In the 3/4 view the camera sways round by ORBIT degrees over each LOOP. */
const ORBIT = 16;
/** A change of view takes MORPH s on an ease-in-out curve. */
export const MORPH = 1.5;

export type Camera = { yaw: number; pitch: number; dist: number };

/** Where view `index` puts the camera at loop time t. */
export function viewCamera(index: number, t: number): Camera {
  'worklet';
  const v = VIEWS[index];
  const sway = index === VIEW_THREE_QUARTER ? ORBIT * Math.sin((2 * Math.PI * t) / LOOP) : 0;
  return { yaw: v.yaw + sway, pitch: v.pitch, dist: v.dist };
}

function easeInOut(p: number) {
  'worklet';
  const t = Math.min(1, Math.max(0, p));
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

/** The camera partway (`p`, 0…1) from one view to another, eased; a morph arcs out a little. */
export function blendCamera(a: Camera, b: Camera, p: number): Camera {
  'worklet';
  const e = easeInOut(p);
  const lift = Math.sin(Math.PI * e) * 3;
  return {
    yaw: a.yaw + (b.yaw - a.yaw) * e,
    pitch: a.pitch + (b.pitch - a.pitch) * e,
    dist: a.dist + (b.dist - a.dist) * e + lift,
  };
}

// ---------------------------------------------------------------------------------------------
// autoPlay: the comb pulls the bobs aside and lets go, the camera visits each view while one
// whole cycle plays, and when the bobs come back into line the comb catches them and brings
// them home.

const AMPLITUDE = 1.7;
const GRAB_AT = 0.6;
const PULLED_AT = 2.2;
const RELEASE_AT = 2.4;
const CATCH_AT = RELEASE_AT + CYCLE;
const HOME_AT = CATCH_AT + 2.1;
const SET_DOWN_AT = HOME_AT + 0.4;
/** [time, view]: the view the camera turns to at each time. */
export const VIEW_SCRIPT: [number, number][] = [
  [9.5, VIEW_TOP],
  [17.5, VIEW_SIDE],
  [25.5, VIEW_THREE_QUARTER],
];

/** True when the loop's clock went past `at` between `before` and `now` (wrapping at LOOP). */
export function passed(before: number, now: number, at: number) {
  'worklet';
  return now >= before ? before < at && now >= at : at > before || at <= now;
}

/** Drives the comb from the loop's clock; returns the view the script wants now. */
export function pendulumScript(s: PendulumSim, before: number, now: number) {
  'worklet';
  if (passed(before, now, GRAB_AT)) combGrab(s, 0, false);
  if (now >= GRAB_AT && now < RELEASE_AT && s.held) {
    combMove(s, AMPLITUDE * easeInOut((now - GRAB_AT) / (PULLED_AT - GRAB_AT)));
  }
  if (passed(before, now, RELEASE_AT)) combRelease(s, false, true);
  // The bobs are back in line at CATCH_AT: the comb meets them there.
  if (passed(before, now, CATCH_AT)) {
    combGrab(s, AMPLITUDE, false);
    s.sinceRelease = -1;
  }
  if (now >= CATCH_AT && s.held) combMove(s, AMPLITUDE * (1 - easeInOut((now - CATCH_AT) / (HOME_AT - CATCH_AT))));
  // Home and still: the comb is lifted away, so the loop starts from bare rest.
  if (passed(before, now, SET_DOWN_AT)) combRelease(s, false, false);
  let view = VIEW_THREE_QUARTER;
  for (let i = 0; i < VIEW_SCRIPT.length; i += 1) if (now >= VIEW_SCRIPT[i][0]) view = VIEW_SCRIPT[i][1];
  return view;
}
