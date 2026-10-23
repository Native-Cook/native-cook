/**
 * The deck as one plain object, stepped once a frame on the UI thread: five keys on springs (play, fast forward and
 * rewind latch down; stop and pause act and come back up), the capstan motor's speed, the tape's position, and the
 * level meters. The finger and the demo script call the same worklets.
 *
 * Every helper is declared above the worklets that call it: worklets capture what exists when they are created.
 */
import { DEMO, HAPTIC, METER, SPRINGS, TAPE, type Spring } from './motion';
import { KEY, LAYOUT, STAGE } from './theme';

export const MODE = { stop: 0, play: 1, ff: 2, rew: 3, pause: 4 } as const;

export type Model = {
  u: number; // tape position
  v: number; // tape speed (u per second)
  mode: number;
  held: number; // the key under a finger, or -1
  d: number[]; // each key's depth, 0 up .. 1 bottomed
  dv: number[];
  t: number; // music time: advances only while the tape plays
  lvL: number; // meter levels 0..1
  lvR: number;
  memo: boolean; // the counter's memory is stopping a rewind at 000
  ghostX: number;
  ghostY: number;
  ghostO: number;
  ghostOT: number;
  reduced: boolean;
  silent: boolean;
  events: number[];
  version: number;
  moving: boolean;
};

// ——— Numbers ———

export function clamp(x: number, lo: number, hi: number): number {
  'worklet';
  return x < lo ? lo : x > hi ? hi : x;
}

function springStep(x: number, v: number, target: number, dt: number, s: Spring): [number, number] {
  'worklet';
  const w = (2 * Math.PI) / (s.duration / 1000);
  const k = w * w;
  const c = 2 * s.dampingRatio * w;
  const n = Math.max(1, Math.ceil(dt / 0.004));
  const h = dt / n;
  let px = x;
  let pv = v;
  for (let i = 0; i < n; i++) {
    pv += (-k * (px - target) - c * pv) * h;
    px += pv * h;
  }
  if (Math.abs(px - target) < 0.0008 && Math.abs(pv) < 0.01) return [target, 0];
  return [px, pv];
}

// ——— The tape ———

const K = LAYOUT.pack.max * LAYOUT.pack.max - LAYOUT.pack.min * LAYOUT.pack.min;

/** The pack on the right (take-up) reel at u, and on the left (supply) reel. */
export function packRight(u: number): number {
  'worklet';
  return Math.sqrt(LAYOUT.pack.min * LAYOUT.pack.min + u * K);
}

export function packLeft(u: number): number {
  'worklet';
  return Math.sqrt(LAYOUT.pack.min * LAYOUT.pack.min + (1 - u) * K);
}

/**
 * The reels' angles at u: the tape leaves the left pack and joins the right one at the same speed, so each reel turns
 * by (tape length) / (its pack's radius). Integrated in closed form; both reels turn the same way.
 */
export function reelAngles(u: number): [number, number] {
  'worklet';
  const L = TAPE.length;
  const right = (2 * L * (packRight(u) - packRight(0))) / K;
  const left = (2 * L * (packLeft(0) - packLeft(u))) / K;
  return [left, right];
}

/** The counter: turns of the take-up reel since 000, as a real counter counts them (it can go below 000). */
export function counterAt(u: number): number {
  'worklet';
  return (reelAngles(u)[1] - reelAngles(TAPE.start)[1]) / (Math.PI * 2);
}

// ——— Model ———

export function createModel(reduced: boolean): Model {
  return {
    u: TAPE.start, v: 0, mode: MODE.stop, held: -1, d: [0, 0, 0, 0, 0], dv: [0, 0, 0, 0, 0], t: 0, lvL: 0, lvR: 0,
    memo: false, ghostX: 0, ghostY: 0, ghostO: 0, ghostOT: 0, reduced, silent: false, events: [], version: 0, moving: true,
  };
}

export function cloneModel(m: Model): Model {
  'worklet';
  return { ...m, d: m.d.slice(), dv: m.dv.slice(), events: m.events.slice() };
}

function emit(m: Model, h: number) {
  'worklet';
  if (!m.silent) m.events.push(h);
}

// ——— Keys ———

export const KEY_W = (STAGE.w - 2 * LAYOUT.edge - 4 * LAYOUT.keys.gap) / 5;

export function keyX(i: number): number {
  'worklet';
  return LAYOUT.edge + i * (KEY_W + LAYOUT.keys.gap);
}

/** The key under (x, y) on the stage, or -1. */
export function keyAt(x: number, y: number): number {
  'worklet';
  if (y < LAYOUT.keys.y - 8 || y > LAYOUT.keys.y + LAYOUT.keys.h + 8) return -1;
  for (let i = 0; i < 5; i++) if (x >= keyX(i) - 4 && x <= keyX(i) + KEY_W + 4) return i;
  return -1;
}

/** Which keys are latched down in a mode. */
function latchedIn(mode: number, i: number): boolean {
  'worklet';
  if (i === KEY.play) return mode === MODE.play || mode === MODE.pause;
  if (i === KEY.pause) return mode === MODE.pause;
  if (i === KEY.ff) return mode === MODE.ff;
  if (i === KEY.rew) return mode === MODE.rew;
  return false;
}

/** A key goes down under the finger: the deck acts as it bottoms out, the way a mechanical deck does. */
export function pressKey(m: Model, i: number) {
  'worklet';
  if (i < 0) return;
  m.held = i;
  const was = m.mode;
  if (i === KEY.play) {
    if (m.u < TAPE.end) m.mode = MODE.play;
  } else if (i === KEY.ff) {
    if (m.u < TAPE.end) m.mode = MODE.ff;
    m.memo = false;
  } else if (i === KEY.rew) {
    m.mode = MODE.rew;
    // The counter's memory: a rewind from past 000 stops there.
    m.memo = m.u > TAPE.start + 0.0005;
  } else if (i === KEY.stop) {
    m.mode = MODE.stop;
    m.memo = false;
  } else if (i === KEY.pause) {
    if (m.mode === MODE.play) m.mode = MODE.pause;
    else if (m.mode === MODE.pause) m.mode = MODE.play;
  }
  emit(m, m.mode !== was && (m.mode === MODE.play || m.mode === MODE.ff || m.mode === MODE.rew) ? HAPTIC.latch : HAPTIC.key);
  m.version++;
  m.moving = true;
}

export function releaseKey(m: Model) {
  'worklet';
  m.held = -1;
  m.moving = true;
}

// ——— Frame ———

/** A deterministic music level for the meters: a beat, a phrase and some grain, different in each channel. */
function level(t: number, ch: number): number {
  'worklet';
  const beat = Math.pow(Math.max(0, Math.sin(t * Math.PI * 2 * 1.9 + ch * 0.4)), 6);
  const phrase = 0.5 + 0.22 * Math.sin(t * 0.9 + ch * 1.3) + 0.12 * Math.sin(t * 2.7 + ch);
  const grain = 0.08 * Math.sin(t * 23.0 + ch * 2.1) * Math.sin(t * 7.3);
  return clamp(phrase + 0.32 * beat + grain, 0, 1);
}

export function step(m: Model, dt: number) {
  'worklet';
  const R = m.reduced;
  let busy = false;
  // The motor: the tape's speed heads for the mode's, with the motor's inertia.
  const target = m.mode === MODE.play ? TAPE.play : m.mode === MODE.ff ? TAPE.wind : m.mode === MODE.rew ? -TAPE.wind : 0;
  const k = R ? 1 : 1 - Math.exp(-dt / TAPE.motor);
  m.v += (target - m.v) * k;
  if (Math.abs(m.v - target) < 1e-5) m.v = target;
  m.u += m.v * dt;
  // The ends of the tape, and the memory stop at 000.
  if (m.memo && m.u <= TAPE.start) {
    m.u = TAPE.start;
    m.v = 0;
    m.mode = MODE.stop;
    m.memo = false;
    emit(m, HAPTIC.memo);
    m.version++;
  }
  if (m.u <= 0.02 && m.v < 0) {
    m.u = 0.02;
    m.v = 0;
    m.mode = MODE.stop;
    m.version++;
  }
  if (m.u >= TAPE.end && m.v > 0) {
    m.u = TAPE.end;
    m.v = 0;
    m.mode = MODE.stop;
    m.version++;
  }
  if (m.v !== 0 || m.v !== target) busy = true;

  // The keys: down under the finger, held by a latch, or sprung back up.
  for (let i = 0; i < 5; i++) {
    const goal = i === m.held ? 1 : latchedIn(m.mode, i) ? TAPE.latch : 0;
    const s = R ? SPRINGS.reduced : goal > m.d[i] ? SPRINGS.down : SPRINGS.up;
    const [d, v] = springStep(m.d[i], m.dv[i], goal, dt, s);
    m.d[i] = Math.max(0, Math.min(1.02, d));
    m.dv[i] = v;
    if (v !== 0) busy = true;
  }

  // The meters follow the music while the tape plays at speed, and fall back when it stops.
  const playing = m.mode === MODE.play && Math.abs(m.v - TAPE.play) < TAPE.play * 0.5;
  if (playing) m.t += dt;
  const gl = playing ? level(m.t, 0) : 0;
  const gr = playing ? level(m.t, 1) : 0;
  const a = (x: number, g: number) => x + (g - x) * (1 - Math.exp(-dt / (g > x ? METER.attack : METER.release)));
  m.lvL = a(m.lvL, gl);
  m.lvR = a(m.lvR, gr);
  if (m.lvL < 0.002 && !playing) m.lvL = 0;
  if (m.lvR < 0.002 && !playing) m.lvR = 0;
  if (m.lvL > 0 || m.lvR > 0) busy = true;

  m.ghostO += (m.ghostOT - m.ghostO) * Math.min(1, dt / 0.08);
  if (Math.abs(m.ghostO - m.ghostOT) < 0.01) m.ghostO = m.ghostOT;
  m.moving = busy || m.held >= 0 || m.ghostO !== m.ghostOT;
}

/** The deck at rest: stopped at 000, every key up, the meters down. */
export function toRest(m: Model) {
  'worklet';
  m.mode = MODE.stop;
  m.held = -1;
  m.memo = false;
  m.v = 0;
  m.u = TAPE.start;
  m.t = 0;
  m.version++;
  m.moving = true;
}

// ——— Demo ———

export function script(m: Model, t0: number, t1: number) {
  'worklet';
  const cross = (at: number) => t0 < at && t1 >= at;
  for (let p = 0; p < DEMO.presses.length; p++) {
    const [i, down, up] = DEMO.presses[p];
    const x = keyX(i) + KEY_W / 2;
    const y = LAYOUT.keys.y + LAYOUT.keys.top + 40;
    if (cross(down - 0.18)) {
      m.ghostX = x;
      m.ghostY = y;
      m.ghostOT = 1;
    }
    if (cross(down)) pressKey(m, i);
    if (cross(up)) {
      releaseKey(m);
      m.ghostOT = 0;
    }
  }
}
