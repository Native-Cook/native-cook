import {
  CURVES, dropBottom, heightAt, HIT, LOOP, ORB_R, PEAK_NODE, PERIODS,
  PLOT, poolSurfaceY, restFrame, SAMPLES, SPLASH, xOf, yOf,
} from './peak-contract';
import type { Frame } from './peak-contract';

export const FIXED_DT = 1 / 240;
const TAU = Math.PI * 2;
const REST = [restFrame(0), restFrame(1), restFrame(2)];
const IMPACT = 1.6;
const HIT_STOP = 0.05;
// Refresh choreography uses active time; the hit-stop shifts these beats together.
const IGNITION = 1.88;
const COUNT_START = IGNITION - 0.04;
const STATUS_RETURN = IGNITION + 0.15;
// Fixed offsets and launch velocities make every replay throw the same liquid beads.
const SPLASH_LAUNCH = [
  [-14, -120, 260], [-9, -78, 365], [-4, -38, 430], [2, 24, 405],
  [9, 87, 305], [14, 120, 280], [-7, -57, 420], [6, 53, 385],
] as const;
export type Action = 'refresh' | 'period';
type Mode = 'rest' | 'opening' | 'refresh' | 'morph' | 'fade';
type SpringKey = 'orbU' | 'orbR' | 'orbGain' | 'cardScale' | 'tiltX' | 'tiltY' | 'poolLevel' |
  'poolHumpX' | 'poolHump' | 'poolSlope' | 'poolGain' | 'beam' | 'numberScale' | 'pillPress' | 'refreshPress';
const SPRING_KEYS: readonly SpringKey[] = [
  'orbU', 'orbR', 'orbGain', 'cardScale', 'tiltX', 'tiltY', 'poolLevel', 'poolHumpX',
  'poolHump', 'poolSlope', 'poolGain', 'beam', 'numberScale', 'pillPress', 'refreshPress',
];
type Body = { x: number; v: number };
export type PeakState = {
  f: Frame;
  bodies: Record<SpringKey, Body>;
  mode: Mode;
  age: number;
  time: number;
  reduced: boolean;
  idleLife: number;
  from: Frame;
  fromU: number;
  catchFromU: number;
  node: number;
  orbU: number;
  nodes: number[];
  nodeAge: number[];
  nodeVelocity: number[];
  nodeActive: boolean[];
  nodeSide: number[];
  arc: number[];
  arcPeak: number;
  queue: Action | null;
  queueUser: boolean;
  userRun: boolean;
  impactDone: boolean;
  ignitionDone: boolean;
  arrivalDone: boolean;
  impactClock: number;
  beamAge: number;
  beamPower: number;
  stirAge: number;
  stirPower: number;
  stirDuration: number;
  scrubStir: number;
  splashAge: number;
  splashPending: number;
  splashSpeed: number;
  splashVX: number[];
  splashVY: number[];
  fadePeriod: number;
  fadeSwapped: boolean;
  spinFrom: number;
  spinTarget: number;
  flashAge: number;
  flashPower: number;
  glintAge: number;
  glintPower: number;
  ringAge: number;
  ringPower: number;
  finger: boolean;
  scrubbing: boolean;
  fingerUser: boolean;
  fingerAge: number;
  fingerU: number;
  fingerVelocity: number;
  releaseAge: number;
  touchFrom: number;
  touchOn: boolean;
  pressPill: boolean;
  pressRefresh: boolean;
  pillAge: number;
  refreshAge: number;
  pillFrom: number;
  refreshFrom: number;
  settling: boolean;
  settleAge: number;
  revision: number;
  announcements: number;
  light: number;
  selection: number;
  medium: number;
  heavy: number;
  impacts: number;
  arrivals: number;
  autoPlay: boolean;
  paused: boolean;
  contact: boolean;
  restWait: number;
  returning: boolean;
  loop: number;
  cycles: number;
  demoEvent: number;
  closing: number;
};

// Limit a scalar without allocating an intermediate object.
export function clamp(value: number, low = 0, high = 1): number {
  'worklet';
  return Math.max(low, Math.min(high, value));
}

// Evaluate a cubic easing by solving its x coordinate with bounded bisection.
function bezier(t: number, x1: number, y1: number, x2: number, y2: number): number {
  'worklet';
  if (t <= 0) return 0;
  if (t >= 1) return 1;
  let lo = 0, hi = 1, u = t;
  for (let i = 0; i < 18; i++) {
    const inv = 1 - u;
    const x = 3 * inv * inv * u * x1 + 3 * inv * u * u * x2 + u * u * u;
    if (x < t) lo = u;
    else hi = u;
    u = (lo + hi) * 0.5;
  }
  const inv = 1 - u;
  return 3 * inv * inv * u * y1 + 3 * inv * u * u * y2 + u * u * u;
}

// Use the specified fast-answer easing for entrances, fades and value rolls.
export function easeOut(t: number): number {
  'worklet';
  return bezier(t, 0.23, 1, 0.32, 1);
}

// Use the specified cinematic easing for travel between on-screen positions.
export function easeMove(t: number): number {
  'worklet';
  return bezier(t, 0.77, 0, 0.175, 1);
}

// Copy every array as well as the frame so the renderer observes a fresh snapshot.
function copyFrame(f: Frame): Frame {
  'worklet';
  return {
    ...f, ys: f.ys.slice(), nodeX: f.nodeX.slice(), nodeY: f.nodeY.slice(),
    nodeLit: f.nodeLit.slice(), nodeScale: f.nodeScale.slice(),
    drop: f.drop.slice(), nodeBottom: f.nodeBottom.slice(),
    splashX: f.splashX.slice(), splashY: f.splashY.slice(), splashR: f.splashR.slice(),
  };
}

// Integrate a damped spring analytically, including critical damping and initial velocity.
function spring(b: Body, target: number, stiffness: number, damping: number, dt: number): number {
  'worklet';
  const offset = b.x - target, decay = damping / 2;
  const discriminant = stiffness - decay * decay;
  const attenuation = Math.exp(-decay * dt);
  if (discriminant > 0.00001) {
    const omega = Math.sqrt(discriminant), co = Math.cos(omega * dt), si = Math.sin(omega * dt);
    const c = (b.v + decay * offset) / omega;
    b.x = target + attenuation * (offset * co + c * si);
    b.v = attenuation * (b.v * co - (decay * c + omega * offset) * si);
  } else if (discriminant < -0.00001) {
    const root = Math.sqrt(-discriminant), r1 = -decay + root, r2 = -decay - root;
    const a = (b.v - r2 * offset) / (r1 - r2), c = offset - a;
    b.x = target + a * Math.exp(r1 * dt) + c * Math.exp(r2 * dt);
    b.v = a * r1 * Math.exp(r1 * dt) + c * r2 * Math.exp(r2 * dt);
  } else {
    const c = b.v + decay * offset;
    b.x = target + attenuation * (offset + c * dt);
    b.v = attenuation * (b.v - decay * c * dt);
  }
  return b.x;
}

// Keep the spring's position and velocity coherent when direct manipulation takes ownership.
function put(b: Body, value: number, velocity = 0): void {
  'worklet';
  b.x = value;
  b.v = velocity;
}

// Find the nearest of the four current node positions.
function nearest(s: PeakState, u: number): number {
  'worklet';
  let best = 0;
  for (let i = 1; i < 4; i++) if (Math.abs(s.nodes[i] - u) < Math.abs(s.nodes[best] - u)) best = i;
  return best;
}

// Measure relative chart height against the current period's peak.
function ratio(s: PeakState, u: number): number {
  'worklet';
  return clamp(heightAt(s.f.ys, u) / Math.max(0.001, heightAt(s.f.ys, s.nodes[PEAK_NODE])));
}

// Put the orb and nodes on the actual sampled curve, including during a morph.
function place(s: PeakState): void {
  'worklet';
  const f = s.f;
  f.orbX = xOf(s.orbU);
  f.orbY = yOf(heightAt(f.ys, s.orbU));
  for (let i = 0; i < 4; i++) {
    f.nodeX[i] = xOf(s.nodes[i]);
    f.nodeY[i] = yOf(heightAt(f.ys, s.nodes[i]));
  }
}

// Precompute screen-space arc lengths once per refresh rather than per display frame.
function buildArc(s: PeakState): void {
  'worklet';
  s.arc[0] = 0;
  for (let i = 1; i < SAMPLES; i++) {
    const dy = yOf(s.f.ys[i]) - yOf(s.f.ys[i - 1]);
    const dx = (PLOT.right - PLOT.left) / (SAMPLES - 1);
    s.arc[i] = s.arc[i - 1] + Math.sqrt(dx * dx + dy * dy);
  }
  const at = s.nodes[PEAK_NODE] * (SAMPLES - 1), i = Math.floor(at);
  s.arcPeak = s.arc[i] + (s.arc[i + 1] - s.arc[i]) * (at - i);
}

// Invert the arc table to keep the drawing head's on-screen speed continuous.
function arcU(s: PeakState, progress: number): number {
  'worklet';
  const goal = s.arcPeak * clamp(progress);
  for (let i = 1; i < SAMPLES; i++) {
    if (s.arc[i] >= goal) return (i - 1 + (goal - s.arc[i - 1]) / Math.max(0.001, s.arc[i] - s.arc[i - 1])) / (SAMPLES - 1);
  }
  return s.nodes[PEAK_NODE];
}

// Replace the state by a canonical resting picture without calling a JS-only contract function.
function home(s: PeakState, period: number): void {
  'worklet';
  const presented = s.f.presented;
  s.f = copyFrame(REST[period]);
  s.f.presented = presented;
  s.orbU = PERIODS[period].nodes[PEAK_NODE];
  s.node = PEAK_NODE;
  s.nodes = PERIODS[period].nodes.slice();
  for (let i = 0; i < SPRING_KEYS.length; i++) {
    const key = SPRING_KEYS[i];
    put(s.bodies[key], key === 'orbU' ? s.orbU : s.f[key]);
  }
  for (let i = 0; i < 4; i++) {
    s.nodeAge[i] = 10;
    s.nodeVelocity[i] = 0;
    s.nodeActive[i] = true;
  }
  s.mode = 'rest';
  s.idleLife = 1;
  s.age = 0;
  s.settling = false;
  s.flashAge = s.glintAge = s.ringAge = 10;
  s.flashPower = s.glintPower = s.ringPower = 0;
  s.beamAge = s.stirAge = s.splashAge = 10;
  s.beamPower = s.stirPower = s.scrubStir = s.splashPending = 0;
  for (let i = 0; i < SPLASH; i++) s.splashVX[i] = s.splashVY[i] = 0;
  s.finger = s.scrubbing = false;
  s.touchOn = false;
  s.releaseAge = 10;
}

// Construct serializable state; this is also the public Node simulation entry point.
export function createState(autoPlay = false, reduced = false): PeakState {
  'worklet';
  const f = copyFrame(REST[0]);
  const bodies = {} as Record<SpringKey, Body>;
  for (let i = 0; i < SPRING_KEYS.length; i++) {
    const key = SPRING_KEYS[i];
    bodies[key] = { x: key === 'orbU' ? PERIODS[0].nodes[PEAK_NODE] : f[key], v: 0 };
  }
  const s: PeakState = {
    f, bodies, mode: 'rest', age: 0, time: 0, reduced, idleLife: autoPlay || reduced ? 1 : 0, from: copyFrame(f), fromU: 0, catchFromU: 0,
    node: PEAK_NODE, orbU: PERIODS[0].nodes[PEAK_NODE], nodes: PERIODS[0].nodes.slice(),
    nodeAge: [10, 10, 10, 10], nodeVelocity: [0, 0, 0, 0], nodeActive: [true, true, true, true], nodeSide: [0, 0, 0, 0],
    arc: [], arcPeak: 1, queue: null, queueUser: false, userRun: false,
    impactDone: false, ignitionDone: false, arrivalDone: false, fadePeriod: 0, fadeSwapped: false,
    impactClock: 0, beamAge: 10, beamPower: 0, stirAge: 10, stirPower: 0, stirDuration: 1.2, scrubStir: 0,
    splashAge: 10, splashPending: 0, splashSpeed: 1,
    splashVX: [0, 0, 0, 0, 0, 0, 0, 0], splashVY: [0, 0, 0, 0, 0, 0, 0, 0],
    spinFrom: 0, spinTarget: 0, flashAge: 10, flashPower: 0, glintAge: 10, glintPower: 0,
    ringAge: 10, ringPower: 0, finger: false, scrubbing: false, fingerUser: false,
    fingerAge: 0, fingerU: 0, fingerVelocity: 0, releaseAge: 10,
    touchFrom: 0, touchOn: false, pressPill: false, pressRefresh: false,
    pillAge: 0, refreshAge: 0, pillFrom: 0, refreshFrom: 0, settling: false, settleAge: 0,
    revision: 0, announcements: 0, light: 0, selection: 0, medium: 0, heavy: 0,
    impacts: 0, arrivals: 0, autoPlay, paused: false, contact: false, restWait: 0,
    returning: false, loop: 0, cycles: 0, demoEvent: 0, closing: 0,
  };
  if (!autoPlay && !reduced) {
    s.mode = 'opening';
    f.lit = 0;
    f.orbGain = 0;
    f.orbR = 7;
    s.orbU = 0;
    f.poolLevel = 0.08;
    f.poolHump = 0;
    f.poolGain = 0.7;
    f.beam = f.beamReach = 0;
    put(bodies.beam, 0);
    f.value = 0;
    f.numberAlpha = 0.35;
    f.status = f.statusPrev = 1;
    for (let i = 0; i < 4; i++) f.nodeLit[i] = f.drop[i] = 0;
    put(bodies.orbU, 0);
    put(bodies.orbR, 7);
    put(bodies.poolLevel, 0.08);
    put(bodies.poolHump, 0);
    put(bodies.poolGain, 0.7);
    place(s);
  }
  return s;
}

// Report whether all user-visible transitions and release springs have finished.
export function isRest(s: PeakState): boolean {
  'worklet';
  return s.mode === 'rest' && !s.finger && !s.settling && !s.pressPill && !s.pressRefresh && s.queue === null;
}

// Pause only the script; the live simulation continues to finish any transition.
export function contact(s: PeakState, down: boolean): void {
  'worklet';
  s.contact = down;
  s.restWait = 0;
  if (down) {
    const wasPaused = s.paused;
    s.paused = true;
    s.returning = false;
    s.closing = 0;
    // A real finger takes over from an interrupted scripted finger or control press.
    if (s.finger && !s.fingerUser) {
      s.node = nearest(s, s.orbU);
      s.finger = s.scrubbing = false;
      s.settling = true;
      s.settleAge = 0;
      s.touchFrom = s.f.touchA;
      s.releaseAge = 0;
      s.touchOn = false;
    }
    if (!wasPaused) s.pressPill = s.pressRefresh = false;
  }
}

// Give either control its 120 ms compression and a light user-only haptic.
export function press(s: PeakState, action: Action, down: boolean, user: boolean): void {
  'worklet';
  if (user && down) contact(s, true);
  if (action === 'period') {
    s.pressPill = down;
    s.pillAge = 0;
    s.pillFrom = s.f.pillPress;
  } else {
    s.pressRefresh = down;
    s.refreshAge = 0;
    s.refreshFrom = s.f.refreshPress;
  }
  if (user && down) s.light++;
  if (user && !down) contact(s, false);
}

// Start a refresh from the current picture, or a spatially delayed string morph.
function begin(s: PeakState, action: Action, user: boolean): void {
  'worklet';
  s.from = copyFrame(s.f);
  s.fromU = s.orbU;
  s.age = 0;
  s.userRun = user;
  s.impactDone = s.ignitionDone = s.arrivalDone = false;
  s.settling = false;
  s.revision++;
  if (s.reduced) {
    s.mode = 'fade';
    s.fadePeriod = action === 'period' ? (s.f.period + 1) % PERIODS.length : s.f.period;
    s.fadeSwapped = false;
    return;
  }
  if (action === 'refresh') {
    s.beamAge = 10;
    s.beamPower = 0;
    s.mode = 'refresh';
    s.f.statusPrev = s.f.status;
    s.f.status = 1;
    s.f.statusMix = 0;
    s.spinFrom = s.f.spin;
    s.node = PEAK_NODE;
    buildArc(s);
    for (let i = 0; i < 4; i++) {
      s.nodeActive[i] = false;
      s.nodeAge[i] = -1;
    }
  } else {
    s.mode = 'morph';
    s.f.periodPrev = s.f.period;
    s.f.period = (s.f.period + 1) % PERIODS.length;
    s.f.periodMix = 0;
  }
}

// Queue at most one action during a transition; later taps replace that pending action.
export function request(s: PeakState, action: Action, user: boolean): void {
  'worklet';
  if (user) {
    s.paused = true;
    s.restWait = 0;
  }
  if (s.mode !== 'rest' || s.finger) {
    s.queue = action;
    s.queueUser = user;
    return;
  }
  begin(s, action, user);
}

// Start a bead's velocity-driven pop without introducing a scale discontinuity.
function pop(s: PeakState, node: number, amount: number): void {
  'worklet';
  s.nodeVelocity[node] += amount * 34;
}

// Start a ring and flare, preserving a stable centre until the ring is invisible.
function flare(s: PeakState, power: number, glint: number, ring: number): void {
  'worklet';
  s.flashAge = s.glintAge = 0;
  s.flashPower = power;
  s.glintPower = glint;
  if (ring > 0) {
    s.ringAge = 0;
    s.ringPower = ring;
    s.f.ringX = s.f.orbX;
    s.f.ringY = s.f.orbY;
  }
}

// Start direct manipulation only when the filament is fully illuminated and at rest.
export function fingerDown(s: PeakState, x: number, y: number, user: boolean): void {
  'worklet';
  if (user) contact(s, true);
  s.finger = true;
  s.fingerUser = user;
  s.scrubbing = s.mode === 'rest' && s.f.lit >= 0.9999 && y >= HIT.plot.y && y <= HIT.plot.y + HIT.plot.h;
  s.fingerAge = 0;
  s.catchFromU = s.orbU;
  s.fingerU = clamp((x - PLOT.left) / (PLOT.right - PLOT.left), 0.03, 0.97);
  s.fingerVelocity = 0;
  for (let i = 0; i < 4; i++) {
    const gap = s.fingerU - s.nodes[i];
    s.nodeSide[i] = gap > 0.000001 ? 1 : gap < -0.000001 ? -1 : 0;
  }
  s.f.touchX = x;
  s.f.touchY = y;
  s.touchFrom = s.f.touchA;
  s.touchOn = true;
  s.releaseAge = 0;
  if (s.scrubbing) s.settling = false;
  if (!s.reduced && !s.scrubbing) s.bodies.poolSlope.v += clamp((x - 201) / 173, -1, 1) * 1.8;
}

// Follow the finger and fire crossings only after its first contact.
export function fingerMove(s: PeakState, x: number, y: number, velocityX: number): void {
  'worklet';
  if (!s.finger) return;
  s.f.touchX = x;
  s.f.touchY = y;
  if (!s.scrubbing) return;
  const u = clamp((x - PLOT.left) / (PLOT.right - PLOT.left), 0.03, 0.97);
  for (let i = 0; i < 4; i++) {
    const gap = u - s.nodes[i];
    const side = gap > 0.000001 ? 1 : gap < -0.000001 ? -1 : 0;
    if (s.nodeSide[i] !== 0 && side !== s.nodeSide[i]) {
      if (!s.reduced) pop(s, i, 0.35);
      if (s.fingerUser) s.selection++;
      if (i === PEAK_NODE) {
        if (!s.reduced) flare(s, 0.35, 0.3, 0);
        if (s.fingerUser) s.medium++;
      }
    }
    s.nodeSide[i] = side;
  }
  s.fingerU = u;
  s.fingerVelocity = velocityX / (PLOT.right - PLOT.left);
}

// Release with the actual finger velocity; reduced motion preserves the exact chosen position.
export function fingerUp(s: PeakState, velocityX: number, user: boolean): void {
  'worklet';
  if (!s.finger) return;
  if (s.scrubbing) {
    s.node = nearest(s, s.fingerU);
    if (s.reduced) {
      s.orbU = s.fingerU;
      put(s.bodies.orbU, s.orbU);
    } else put(s.bodies.orbU, s.orbU, velocityX / (PLOT.right - PLOT.left));
    s.settling = !s.reduced;
    s.settleAge = 0;
    s.revision++;
    if (user) s.announcements++;
  }
  s.finger = s.scrubbing = false;
  s.touchOn = false;
  s.touchFrom = s.f.touchA;
  s.releaseAge = 0;
  if (user) contact(s, false);
}

// Move accessibly to a neighbouring bead using the same released-orb spring.
export function adjust(s: PeakState, direction: number): void {
  'worklet';
  contact(s, true);
  contact(s, false);
  if (s.mode !== 'rest') return;
  s.node = clamp((s.settling ? s.node : nearest(s, s.orbU)) + direction, 0, 3);
  s.settling = !s.reduced;
  s.settleAge = 0;
  if (s.reduced) s.orbU = s.nodes[s.node];
  put(s.bodies.orbU, s.orbU);
  s.selection++;
  s.revision++;
  s.announcements++;
}

// Bring an interrupted demo home through the same period actions and orb-release spring.
export function returnHome(s: PeakState): void {
  'worklet';
  if (!isRest(s)) return;
  s.returning = true;
  if (s.f.period !== 0) request(s, 'period', false);
  else if (Math.abs(s.orbU - s.nodes[PEAK_NODE]) > 0.00001) {
    s.node = PEAK_NODE;
    s.settling = true;
    s.settleAge = 0;
  }
}

// Restart a demo cycle with all visible values and latent velocities canonicalized.
export function resetLoop(s: PeakState): void {
  'worklet';
  home(s, 0);
  s.loop = 0;
  s.time = 0;
  s.demoEvent = 0;
  s.closing = 0;
  s.paused = s.returning = false;
  s.restWait = 0;
  s.pressPill = s.pressRefresh = false;
  s.queue = null;
  s.revision++;
}

// Remove remaining physical offsets and velocities continuously over the closing half-second.
export function closeLoop(s: PeakState, progress: number): void {
  'worklet';
  // Match the demo's floating-point seam tolerance before publishing its final frame.
  if (progress >= 1 - 0.000000001) progress = 1;
  const previous = easeOut(s.closing), next = easeOut(progress);
  const remaining = Math.max(0.000000001, 1 - previous);
  const keep = clamp((1 - next) / remaining);
  s.closing = progress;
  const rest = REST[0];
  for (let i = 0; i < SPRING_KEYS.length; i++) {
    const key = SPRING_KEYS[i], b = s.bodies[key];
    const target = key === 'orbU' ? PERIODS[0].nodes[PEAK_NODE] : rest[key];
    b.x = target + (b.x - target) * keep;
    b.v *= keep;
  }
  for (let i = 0; i < 4; i++) s.nodeVelocity[i] *= keep;
  s.fingerVelocity *= keep;
}

// Produce a spatial spring response with zeta .55 and its first crossing at 260 ms.
function pluck(t: number): number {
  'worklet';
  if (t <= 0) return 0;
  const zeta = 0.55, omega = 9.93, wd = omega * Math.sqrt(1 - zeta * zeta);
  const raw = 1 - Math.exp(-zeta * omega * t) * (Math.cos(wd * t) + zeta / Math.sqrt(1 - zeta * zeta) * Math.sin(wd * t));
  // Remove the tiny second ring smoothly, after the one intentional overshoot.
  return raw + (1 - raw) * easeOut((t - 0.6) / 0.14);
}

// Find the visible liquid surface, including the renderer's tiny periodic resting tide.
function liquidSurface(s: PeakState, x: number): number {
  'worklet';
  const hump = clamp(s.f.poolHump + 0.05 * Math.sin(TAU * s.time / LOOP * 3) * s.idleLife);
  return poolSurfaceY(s.f.poolLevel, hump, s.f.poolHumpX, s.f.poolSlope, x);
}

// Launch on the visible surface, integrate gravity, and let the rising pool swallow beads.
function splashStep(s: PeakState, dt: number): void {
  'worklet';
  const f = s.f;
  if (s.splashPending > 0) {
    s.splashAge = 0;
    for (let i = 0; i < SPLASH; i++) {
      const launch = SPLASH_LAUNCH[i];
      if (i < s.splashPending) {
        f.splashX[i] = clamp(f.orbX + launch[0], 49, 353);
        f.splashY[i] = liquidSurface(s, f.splashX[i]);
        f.splashR[i] = 5;
        s.splashVX[i] = launch[1] * s.splashSpeed;
        s.splashVY[i] = -launch[2] * s.splashSpeed;
      } else {
        f.splashX[i] = f.splashY[i] = f.splashR[i] = 0;
        s.splashVX[i] = s.splashVY[i] = 0;
      }
    }
    s.splashPending = 0;
    return;
  }
  s.splashAge += dt;
  for (let i = 0; i < SPLASH; i++) {
    if (f.splashR[i] === 0) continue;
    f.splashX[i] += s.splashVX[i] * dt;
    f.splashY[i] += s.splashVY[i] * dt + 0.5 * 700 * dt * dt;
    s.splashVY[i] += 700 * dt;
    const flight = Math.min(1.1, 2 * SPLASH_LAUNCH[i][2] * s.splashSpeed / 700);
    f.splashR[i] = 5 - 2.8 * clamp(s.splashAge / flight);
    // Contain the whole bead even when a morph arrives at a non-peak node near an edge.
    f.splashX[i] = clamp(f.splashX[i], 44 + f.splashR[i], 358 - f.splashR[i]);
    if (f.splashY[i] < 500 + f.splashR[i]) {
      f.splashY[i] = 500 + f.splashR[i];
      s.splashVY[i] = Math.max(0, s.splashVY[i]);
    }
    if (s.splashAge >= 1.1 - 0.000000001 || f.splashY[i] >= liquidSurface(s, f.splashX[i]) - 0.0000001) {
      f.splashX[i] = f.splashY[i] = f.splashR[i] = 0;
      s.splashVX[i] = s.splashVY[i] = 0;
    }
  }
}

// Keep arrival pulses alive after morph ownership ends and smooth finger-driven agitation.
function liquidStep(s: PeakState, dt: number, orbSpeed: number): void {
  'worklet';
  const f = s.f;
  if (s.reduced) {
    f.beam = 0.22;
    f.beamReach = 1;
    f.poolStir = 0;
    return;
  }
  if (s.mode === 'rest' || s.mode === 'morph') {
    const pulse = s.beamAge < 0.7 ? 0.6 * s.beamPower * Math.sin(Math.PI * clamp(s.beamAge / 0.7)) : 0;
    const target = s.scrubbing ? 0.6 : 0.22;
    let base = spring(s.bodies.beam, target, 900, 60, dt);
    if (Math.abs(base - target) < 0.00001 && Math.abs(s.bodies.beam.v) < 0.0001) {
      put(s.bodies.beam, target);
      base = target;
    }
    f.beam = clamp(base + pulse);
    f.beamReach = 1;
  } else put(s.bodies.beam, f.beam);
  const stirring = s.scrubbing ? clamp(Math.max(Math.abs(orbSpeed) / 500, Math.abs(f.poolSlope) * 4)) : 0;
  s.scrubStir += (stirring - s.scrubStir) * (1 - Math.exp(-8 * dt));
  if (stirring === 0 && s.scrubStir < 0.00001) s.scrubStir = 0;
  f.poolStir = clamp(Math.max(s.scrubStir, s.stirPower * (1 - easeOut(s.stirAge / s.stirDuration))));
  splashStep(s, dt);
}

// Evolve a refresh's timed causal chain; springs are shared with the live interactions.
function refreshStep(s: PeakState, dt: number): void {
  'worklet';
  const f = s.f, a = s.age >= IMPACT ? Math.max(IMPACT, s.age - HIT_STOP) : s.age, p = PERIODS[f.period], b = s.bodies;
  const power = p.value / 300;
  // Brighten on the impact frame so the entire 50 ms freeze holds the energized tube.
  f.tube = 1 + (a >= IMPACT ? 0.8 * power * (1 - easeOut((a - IMPACT) / 0.6)) : 0);
  f.beam = a < IMPACT ? s.from.beam * (1 - easeOut(a / 0.15)) : 1 - 0.78 * easeOut((a - IMPACT) / 1.4);
  f.beamReach = a < IMPACT ? s.from.beamReach * (1 - easeOut(a / 0.15)) : easeOut((a - IMPACT) / 0.24);
  if (a < 0.42) {
    const travel = easeMove(a / 0.42);
    f.lit = s.from.lit * (1 - travel);
    s.orbU = Math.min(s.fromU, f.lit);
    f.orbR = s.from.orbR + (7 - s.from.orbR) * travel;
    f.orbGain = (s.from.orbGain + (0.8 - s.from.orbGain) * travel) * (1 - easeOut((a - 0.34) / 0.08));
    f.value = s.from.value * (1 - easeOut(a / 0.42));
    f.numberAlpha = s.from.numberAlpha + (0.35 - s.from.numberAlpha) * easeOut(a / 0.24);
    for (let i = 0; i < 4; i++) {
      if (s.nodeAge[i] < 0 && f.lit <= s.nodes[i]) s.nodeAge[i] = 0;
      if (s.nodeAge[i] >= 0) {
        s.nodeAge[i] += dt;
        f.nodeLit[i] = s.from.nodeLit[i] * (1 - easeOut(s.nodeAge[i] / 0.12));
        f.drop[i] = s.from.drop[i] * (1 - easeOut(s.nodeAge[i] / 0.2));
      }
    }
  } else if (a < 0.7) {
    f.lit = 0;
    s.orbU = 0;
    f.orbR = 7;
    f.orbGain = f.value = 0;
    f.numberAlpha = 0.35;
    for (let i = 0; i < 4; i++) {
      if (s.nodeAge[i] >= 0) s.nodeAge[i] += dt;
      f.nodeLit[i] = 0;
      f.drop[i] = s.from.drop[i] * (1 - easeOut(s.nodeAge[i] / 0.2));
    }
  } else if (a < 1.6) {
    const run = clamp((a - 0.7) / 0.9);
    f.lit = arcU(s, 0.3 * run + 0.7 * run * run);
    s.orbU = f.lit;
    f.orbR = 7 + easeOut((a - 0.7) / 0.12) + 2 * run;
    f.orbGain = 1.2 * easeOut((a - 0.7) / 0.12);
    f.tip = easeOut((a - 0.7) / 0.12);
  } else {
    if (!s.impactDone) {
      s.impactDone = true;
      s.impacts++;
      s.impactClock = s.autoPlay && !s.paused ? s.loop + dt : s.time;
      s.orbU = s.nodes[PEAK_NODE];
      place(s);
      flare(s, 1, 0.6 * power, 1);
      put(b.orbR, 10, 247);
      b.cardScale.v = 0.39;
      b.tiltX.v = -1.1;
      if (s.userRun) s.heavy++;
    }
    s.orbU = s.nodes[PEAK_NODE];
    f.lit = s.nodes[PEAK_NODE] + (1 - s.nodes[PEAK_NODE]) * easeOut((a - 1.6) / 0.38);
    f.tip = 1 - easeOut((a - 1.88) / 0.1);
    f.orbR = spring(b.orbR, ORB_R, 320, 14, dt);
    f.orbGain = 1.2 + 0.8 * easeOut((a - 1.6) / 0.035) - easeOut((a - 1.635) / 0.565);
  }
  if (a >= 0.7) {
    for (let i = 0; i < 4; i++) {
      if (!s.nodeActive[i] && f.lit >= s.nodes[i] - 0.000001) {
        s.nodeActive[i] = true;
        s.nodeAge[i] = 0;
        pop(s, i, 0.5);
      }
      if (s.nodeActive[i]) {
        s.nodeAge[i] += dt;
        f.nodeLit[i] = easeOut(s.nodeAge[i] / 0.12);
        f.drop[i] = easeOut(s.nodeAge[i] / (i === PEAK_NODE ? 0.26 : 0.32));
      } else f.nodeLit[i] = f.drop[i] = 0;
    }
  }
  if (a >= IGNITION && !s.ignitionDone) {
    s.ignitionDone = true;
    b.poolSlope.v += 3.5;
    s.stirAge = 0;
    s.stirPower = 1;
    s.stirDuration = 1.2;
    s.splashPending = Math.round(SPLASH * Math.max(p.value / 300, 0.4));
    s.splashSpeed = 0.6 + 0.4 * p.value / 300;
    put(b.numberScale, 0.96, 1.25 * power);
  }
  const ignited = a >= IGNITION;
  f.poolLevel = spring(b.poolLevel, ignited ? p.level : 0.08, ignited ? 60 : 180, ignited ? 9 : 27, dt);
  f.poolHump = spring(b.poolHump, ignited ? 0.35 + 0.6 * (1 - easeOut((a - 2.05) / 0.55)) : 0, 100, 15, dt);
  f.poolGain = spring(b.poolGain, ignited ? 1 + 0.35 * (1 - easeOut((a - 2.08) / 0.5)) : a > 1.4 ? 0.55 : 0.7, 180, 25, dt);
  f.cardScale = spring(b.cardScale, a >= 1.4 && a < 1.6 ? 0.992 : 1, a < 1.6 ? 240 : 140, a < 1.6 ? 26 : 14, dt);
  f.tiltX = spring(b.tiltX, 0, 180, 20, dt);
  if (a >= COUNT_START) {
    const roll = clamp((a - COUNT_START) / 1.1);
    f.value = p.value * easeOut(roll);
    f.numberAlpha = 0.35 + 0.65 * easeOut((a - COUNT_START) / 0.26);
    f.numberGlow = Math.sin(Math.PI * roll);
  }
  f.numberScale = ignited ? spring(b.numberScale, 1, 260, 16, Math.min(dt, a - IGNITION)) : 1 - 0.04 * easeOut((a - 0.7) / 0.9);
  if (a < STATUS_RETURN) {
    f.statusMix = easeOut(a / 0.24);
    f.spin = s.spinFrom + a * TAU / 0.8;
  } else {
    if (f.status !== 0) {
      f.statusPrev = 1;
      f.status = 0;
      s.spinTarget = Math.ceil((s.spinFrom + STATUS_RETURN * TAU / 0.8) / TAU) * TAU;
      s.revision++;
    }
    f.statusMix = easeOut((a - STATUS_RETURN) / 0.24);
    const atStop = s.spinFrom + STATUS_RETURN * TAU / 0.8;
    f.spin = atStop + (s.spinTarget - atStop) * easeOut((a - STATUS_RETURN) / 0.4);
  }
  if (a >= 3.4) {
    s.mode = 'rest';
    f.statusPrev = 0;
    f.statusMix = 1;
    s.revision++;
    put(b.orbU, s.orbU);
    // Residual physical motion carries into rest instead of snapping at this boundary.
    s.settling = true;
    s.settleAge = 1.25;
  }
}

// Send the pluck left to right while the node anchors and their light shafts glide.
function morphStep(s: PeakState, dt: number): void {
  'worklet';
  const f = s.f, a = s.age, p = PERIODS[f.period], move = easeMove(a / 0.6);
  for (let i = 0; i < SAMPLES; i++) f.ys[i] = clamp(s.from.ys[i] + (CURVES[f.period][i] - s.from.ys[i]) * pluck(a - 0.26 * i / (SAMPLES - 1)));
  for (let i = 0; i < 4; i++) {
    const oldU = (s.from.nodeX[i] - PLOT.left) / (PLOT.right - PLOT.left);
    s.nodes[i] = oldU + (p.nodes[i] - oldU) * move;
    f.nodeBottom[i] = s.from.nodeBottom[i] + (dropBottom(xOf(p.nodes[i]), i === PEAK_NODE) - s.from.nodeBottom[i]) * move;
  }
  s.orbU = s.fromU + (p.nodes[s.node] - s.fromU) * move;
  put(s.bodies.orbU, s.orbU);
  f.periodMix = easeOut(a / 0.42);
  f.chevron = a < 0.52 ? TAU * easeMove(a / 0.52) : 0;
  const newRatio = clamp(heightAt(CURVES[f.period], p.nodes[s.node]) / heightAt(CURVES[f.period], p.nodes[PEAK_NODE]));
  f.value = s.from.value + (p.value * newRatio - s.from.value) * easeOut(a / 0.7);
  f.numberGlow = 0.5 * Math.sin(Math.PI * clamp(a / 0.7));
  f.poolLevel = spring(s.bodies.poolLevel, p.level * (0.22 + 0.78 * newRatio), 60, 9, dt);
  f.poolHump = spring(s.bodies.poolHump, 0.35, 90, 9, dt);
  if (a >= 0.6 && !s.arrivalDone) {
    s.arrivalDone = true;
    s.arrivals++;
    const power = p.value / 300;
    s.beamAge = s.stirAge = 0;
    s.beamPower = power;
    s.stirPower = 0.5;
    s.stirDuration = 0.8;
    if (f.period === 0) {
      s.splashPending = 5;
      s.splashSpeed = 0.7;
    }
    place(s);
    flare(s, 0.25 * power, 0.4 * power, f.period === 0 ? 0.5 : 0);
    s.bodies.orbR.v += 150 * power;
    if (f.period === 0 && s.userRun) s.medium++;
    s.revision++;
  }
  f.orbR = spring(s.bodies.orbR, s.node === PEAK_NODE ? ORB_R : 7.5 + 3.5 * newRatio, 320, 18, dt);
  f.orbGain = spring(s.bodies.orbGain, 1, 180, 25, dt);
  f.poolGain = spring(s.bodies.poolGain, f.period === 0 && a >= 0.6 ? 1 + 0.25 * Math.sin(Math.PI * clamp((a - 0.6) / 0.7)) : 1, 180, 25, dt);
  if (a >= 1.05) {
    s.mode = 'rest';
    f.periodPrev = f.period;
    f.periodMix = 1;
    s.settling = true;
    s.settleAge = 1.2;
    s.revision++;
  }
}

// Update all channels at a bounded substep with no closures or array callbacks.
function integrate(s: PeakState, dt: number): void {
  'worklet';
  s.time += dt;
  s.age += dt;
  if (s.mode === 'refresh') {
    if (Math.abs(s.age - IMPACT) < 0.000000001) s.age = IMPACT;
    if (Math.abs(s.age - IMPACT - HIT_STOP) < 0.000000001) s.age = IMPACT + HIT_STOP;
    if (Math.abs(s.age - IGNITION - HIT_STOP) < 0.000000001) s.age = IGNITION + HIT_STOP;
  } else if (s.mode === 'morph' && Math.abs(s.age - 0.6) < 0.000000001) s.age = 0.6;
  if (s.mode === 'refresh' && s.impactDone && s.age <= IMPACT + HIT_STOP + 0.000000001) return;
  const previousOrbX = s.f.orbX;
  const idleTarget = s.mode === 'rest' && !s.finger ? 1 : 0;
  s.idleLife += (idleTarget - s.idleLife) * (1 - Math.exp(-18 * dt));
  s.fingerAge += dt;
  s.releaseAge += dt;
  s.pillAge += dt;
  s.refreshAge += dt;
  s.flashAge += dt;
  s.glintAge += dt;
  s.ringAge += dt;
  s.beamAge += dt;
  s.stirAge += dt;
  const f = s.f, b = s.bodies;
  if (s.mode === 'opening') {
    f.spin += dt * TAU / 0.8;
    if (s.age >= 0.35) {
      begin(s, 'refresh', false);
      s.age = 0.42;
      s.spinFrom = f.spin - 0.42 * TAU / 0.8;
      for (let i = 0; i < 4; i++) s.nodeAge[i] = 1;
    }
  }
  if (s.mode === 'fade') {
    if (s.age < 0.12) f.presented = 1 - easeOut(s.age / 0.12);
    else {
      if (!s.fadeSwapped) {
        const age = s.age;
        home(s, s.fadePeriod);
        s.mode = 'fade';
        s.age = age;
        s.fadeSwapped = true;
        s.revision++;
      }
      s.f.presented = easeOut((s.age - 0.12) / 0.16);
      if (s.age >= 0.28) {
        s.mode = 'rest';
        s.f.presented = 1;
        s.revision++;
      }
    }
  } else if (s.mode === 'refresh') refreshStep(s, dt);
  else if (s.mode === 'morph') morphStep(s, dt);
  else if (s.mode === 'rest') {
    if (s.scrubbing) {
      const catchup = s.reduced ? 1 : easeOut(s.fingerAge / 0.09);
      s.orbU = s.catchFromU + (s.fingerU - s.catchFromU) * catchup;
      put(b.orbU, s.orbU, s.fingerVelocity);
    } else if (!s.reduced) {
      s.orbU = clamp(spring(b.orbU, s.nodes[s.node], 260, 22, dt), 0.03, 0.97);
      if (s.orbU !== b.orbU.x) put(b.orbU, s.orbU);
    }
    const height = ratio(s, s.orbU), p = PERIODS[f.period];
    f.value = p.value * height;
    const level = p.level * (0.22 + 0.78 * height);
    f.poolLevel = s.reduced ? level : spring(b.poolLevel, level, 120, 16, dt);
    f.poolHump = s.reduced ? (s.scrubbing ? 0.75 : 0.35) : spring(b.poolHump, s.scrubbing ? 0.75 : 0.35, 90, 9, dt);
    f.poolGain = s.reduced ? 1 : spring(b.poolGain, 1, 160, 22, dt);
    const radius = s.node === PEAK_NODE && !s.scrubbing && !s.settling ? ORB_R : 7.5 + 3.5 * height;
    f.orbR = s.reduced || s.scrubbing ? radius : spring(b.orbR, radius, 320, 14, dt);
    if (s.scrubbing) put(b.orbR, f.orbR);
    f.orbGain = s.reduced ? 1 : s.scrubbing ? 0.8 + 0.4 * height : spring(b.orbGain, 1, 180, 24, dt);
    if (s.scrubbing) put(b.orbGain, f.orbGain);
    f.numberAlpha = 1;
    f.numberScale += (1 - f.numberScale) * (1 - Math.exp(-18 * dt));
    f.numberGlow = 0;
    if (s.settling) {
      s.settleAge += dt;
      if (s.settleAge >= 1.3 && Math.abs(b.orbU.v) < 0.001 && Math.abs(s.orbU - s.nodes[s.node]) < 0.0001) {
        s.settling = false;
        s.revision++;
      }
    }
  }
  if (s.mode !== 'fade') {
    if (s.mode !== 'rest') put(b.orbGain, f.orbGain);
    place(s);
    if (s.mode !== 'refresh') {
      f.cardScale = s.reduced ? 1 : spring(b.cardScale, s.finger ? 0.985 : 1, s.finger ? 300 : 140, s.finger ? 28 : 14, dt);
      f.tiltX = s.reduced ? 0 : spring(b.tiltX, s.finger ? clamp((416 - f.touchY) / 240, -1, 1) * 0.06 : 0, s.finger ? 180 : 140, s.finger ? 20 : 14, dt);
    }
    f.tiltY = s.reduced ? 0 : spring(b.tiltY, s.finger ? clamp((f.touchX - 201) / 173, -1, 1) * 0.1 : 0, s.finger ? 180 : 140, s.finger ? 20 : 14, dt);
    f.poolHumpX = s.reduced ? f.orbX : spring(b.poolHumpX, f.orbX, 90, 9, dt);
    f.poolSlope = s.reduced ? 0 : spring(b.poolSlope, s.scrubbing ? -clamp(s.fingerVelocity * 346 / 900, -1, 1) * 0.25 : 0, 110, 10, dt);
    // Gesture velocity remains valid between input events; per-substep differences would dilute it at 60 Hz.
    const orbSpeed = s.scrubbing && s.fingerAge >= 0.09 ? s.fingerVelocity * (PLOT.right - PLOT.left) : (f.orbX - previousOrbX) / Math.max(dt, 0.000000001);
    liquidStep(s, dt, orbSpeed);
    f.touchA = s.touchOn ? s.touchFrom + (1 - s.touchFrom) * easeOut(s.fingerAge / 0.12) : s.touchFrom * (1 - easeOut(s.releaseAge / 0.22));
    if (!s.touchOn) {
      f.touchX += (201 - f.touchX) * (1 - Math.exp(-18 * dt));
      f.touchY += (416 - f.touchY) * (1 - Math.exp(-18 * dt));
    }
    f.pillPress = s.pressPill ? s.pillFrom + (1 - s.pillFrom) * easeOut(s.pillAge / 0.12) : spring(b.pillPress, 0, 280, 23, dt);
    f.refreshPress = s.pressRefresh ? s.refreshFrom + (1 - s.refreshFrom) * easeOut(s.refreshAge / 0.12) : spring(b.refreshPress, 0, 280, 23, dt);
    if (s.pressPill) put(b.pillPress, f.pillPress);
    if (s.pressRefresh) put(b.refreshPress, f.refreshPress);
    f.pillPress = clamp(f.pillPress);
    f.refreshPress = clamp(f.refreshPress);
    for (let i = 0; i < 4; i++) {
      // Semi-implicit Euler at <= 1/240 s: a bead pop is a small local spring.
      s.nodeVelocity[i] += ((1 - f.nodeScale[i]) * 420 - 20 * s.nodeVelocity[i]) * dt;
      f.nodeScale[i] += s.nodeVelocity[i] * dt;
    }
    f.flash = s.reduced ? 0 : s.flashPower * (1 - easeOut(s.flashAge / (s.flashPower === 1 ? 0.42 : 0.3)));
    f.rimGlint = s.reduced ? 0 : s.glintPower * easeOut(s.glintAge / 0.3) * (1 - easeOut((s.glintAge - 0.3) / 0.5));
    if (s.mode === 'refresh' && s.impactDone) f.rimGlint = Math.max(f.rimGlint, 0.8 * PERIODS[f.period].value / 300 * (1 - easeOut(s.glintAge / 0.25)));
    if (s.mode === 'refresh' && s.age < 0.7) f.rimGlint += 0.5 * easeOut((s.age - 0.34) / 0.08) * (1 - easeOut((s.age - 0.42) / 0.28));
    f.ringA = s.reduced ? 0 : s.ringPower * (1 - easeOut((s.ringAge - 0.288) / 0.432));
    f.ringR = s.ringPower > 0 ? 300 * easeOut(s.ringAge / 0.72) * (1 - easeOut((s.ringAge - 0.72) / 0.3)) : 0;
    if (s.ringAge > 0.72) {
      f.ringX += (f.orbX - f.ringX) * (1 - Math.exp(-18 * dt));
      f.ringY += (f.orbY - f.ringY) * (1 - Math.exp(-18 * dt));
    }
  }
  if (isRest(s) && s.autoPlay && s.paused && !s.contact) s.restWait += dt;
  else s.restWait = 0;
  if (s.mode === 'rest' && !s.finger && !s.settling && s.queue !== null) {
    const action = s.queue, user = s.queueUser;
    s.queue = null;
    begin(s, action, user);
  }
}

// Advance the single live simulation by clamped display time, using bounded physical substeps.
export function step(s: PeakState, dt: number): void {
  'worklet';
  let remaining = clamp(dt, 0, 0.05);
  while (remaining > 0.000000001) {
    let slice = Math.min(FIXED_DT, remaining);
    // Exact event boundaries prevent display-rate-dependent hit-stop and splash launches.
    if (s.mode === 'refresh') {
      const boundary = !s.impactDone ? IMPACT : s.age < IMPACT + HIT_STOP - 0.000000001 ? IMPACT + HIT_STOP : !s.ignitionDone ? IGNITION + HIT_STOP : 0;
      if (boundary > s.age + 0.000000001) slice = Math.min(slice, boundary - s.age);
    } else if (s.mode === 'morph' && !s.arrivalDone && s.age < 0.6 - 0.000000001) slice = Math.min(slice, 0.6 - s.age);
    integrate(s, slice);
    remaining -= slice;
  }
}

// Publish a complete immutable picture, with periodic idle life and an exact seam at thirteen seconds.
export function toFrame(s: PeakState): Frame {
  'worklet';
  const f = copyFrame(s.f);
  f.t = s.reduced ? 0 : s.autoPlay && !s.paused ? s.loop : s.time;
  if (!s.reduced && s.mode === 'refresh' && s.impactDone && s.age <= IMPACT + HIT_STOP + 0.000000001) f.t = s.impactClock;
  if (!s.reduced) {
    const phase = TAU * f.t / LOOP;
    // A continuous envelope carries idle life into and out of transitions without brightness steps.
    const weight = s.idleLife;
    f.tube += 0.04 * Math.sin(phase * 2) * weight;
    f.poolHump = clamp(f.poolHump + 0.05 * Math.sin(phase * 3) * weight);
    f.orbGain = clamp(f.orbGain + 0.06 * Math.sin(phase) * weight, 0, 2);
  }
  if (s.closing > 0) {
    const rest = REST[0], blend = easeOut(s.closing);
    const fields = Object.keys(rest) as (keyof Frame)[];
    for (let i = 0; i < fields.length; i++) {
      const key = fields[i], target = rest[key], current = f[key];
      if (typeof target === 'number' && typeof current === 'number') {
        if (key !== 't' && key !== 'presented') {
          // Completed rotations are the same orientation; never visibly unwind a stopped glyph.
          const source = key === 'spin' || key === 'chevron' ? current - TAU * Math.round(current / TAU) : current;
          (f[key] as number) = source + (target - source) * blend;
        }
      } else if (Array.isArray(target) && Array.isArray(current)) {
        for (let j = 0; j < current.length; j++) current[j] += (target[j] - current[j]) * blend;
      }
    }
    if (s.closing >= 1) {
      f.t = 0;
      f.presented = 1;
    }
  }
  return f;
}
