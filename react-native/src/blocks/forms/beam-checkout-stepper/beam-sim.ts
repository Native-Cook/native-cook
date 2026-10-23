import { F, FIELDS, frameAtRest, panelHeight } from './beam-theme';

const H = 1 / 240;
export type SimEvent = { type: 'settled' | 'cleared' | 'restarted' | 'newOrder' | 'paid' | 'haptic'; value: number; silent: boolean };
type Lane = { index: number; at: number; duration: number; target: number; from: number; started: boolean; kind: number; omega: number; zeta: number };
export type BeamState = {
  frame: number[]; velocity: number[]; lanes: Lane[]; events: SimEvent[];
  time: number; remainder: number; step: number; furthest: number; express: boolean;
  filled: boolean[]; focusField: number; errorField: number; still: boolean; ready: boolean;
  mode: string; started: number; arrival: number; target: number; origin: number;
  flags: number; silent: boolean; dragging: boolean; grab: number; deliveryGrab: number;
  paidAt: number; newOrderSent: boolean; presses: number[];
  labelMix: number; labelStarted: number; pendingPanel: number; panelSwapAt: number;
};

/** Solves a unit cubic Bézier by monotone bisection, including its flat endpoints. */
export function bezier(t: number, x1: number, y1: number, x2: number, y2: number): number {
  'worklet';
  if (t <= 0) return 0;
  if (t >= 1) return 1;
  let lo = 0; let hi = 1; let u = t;
  for (let i = 0; i < 18; i++) {
    const q = 1 - u;
    const x = 3 * q * q * u * x1 + 3 * q * u * u * x2 + u * u * u;
    if (x < t) lo = u; else hi = u;
    u = (lo + hi) / 2;
  }
  const q = 1 - u;
  return 3 * q * q * u * y1 + 3 * q * u * u * y2 + u * u * u;
}
function clamp(v: number, lo: number, hi: number) { 'worklet'; return Math.max(lo, Math.min(hi, v)); }
function ease(t: number, kind: number) {
  'worklet';
  const u = clamp(t, 0, 1);
  return kind === 1 ? bezier(u, 0.23, 1, 0.32, 1) : kind === 2 ? bezier(u, 0.77, 0, 0.175, 1) : u;
}
function emit(s: BeamState, type: SimEvent['type'], value: number) {
  'worklet'; s.events.push({ type, value, silent: s.silent });
}
function cancel(s: BeamState, index: number) {
  'worklet';
  for (let i = s.lanes.length - 1; i >= 0; i--) if (s.lanes[i].index === index) s.lanes.splice(i, 1);
}
function lane(s: BeamState, index: number, target: number, duration: number, delay: number, kind: number, omega: number, zeta: number) {
  'worklet';
  if (delay === 0) {
    cancel(s, index);
    if (s.frame[index] === target && Math.abs(s.velocity[index]) < 0.00001) return;
  }
  s.lanes.push({ index, target, duration, at: s.time + delay, from: s.frame[index], started: false, kind, omega, zeta });
}
function tween(s: BeamState, i: number, target: number, duration: number, delay = 0, curve = 1) {
  'worklet';
  const fade = s.still && ((i >= F.NODE_ON && i < F.NODE_FLASH) || (i >= F.ROW_ACTIVE && i < F.ROW_PRESS) || i === F.CTA_SPLIT || i === F.CTA_DONE);
  lane(s, i, target, fade ? 0.2 : duration, delay, fade ? 0 : curve, 0, 0);
}
function spring(s: BeamState, i: number, target: number, omega: number, zeta: number, delay = 0) {
  'worklet'; lane(s, i, target, s.still ? 0.2 : 1.5, delay, s.still ? 0 : 3, omega, zeta);
}
function impulse(s: BeamState, i: number, value: number) { 'worklet'; cancel(s, i); s.frame[i] = value; s.velocity[i] = 0; }
function fadeFocus(s: BeamState) {
  'worklet'; s.focusField = -1; tween(s, F.FOCUS_A, 0, 0.16);
  for (let i = 0; i < 7; i++) tween(s, F.FIELD_FLOAT + i, s.filled[i] ? 1 : 0, 0.18);
}
function clearMajor(s: BeamState) {
  'worklet';
  for (let i = s.lanes.length - 1; i >= 0; i--) {
    const n = s.lanes[i].index;
    if (n < F.FIELD_FLOAT && n !== F.PRESENT && !(n >= F.FOCUS_X && n <= F.FOCUS_A) && n !== F.SEG_LEAD && n !== F.SEG_TRAIL) s.lanes.splice(i, 1);
  }
  s.flags = 0; s.started = s.time; s.origin = s.step; s.pendingPanel = -1;
  tween(s, F.DRAG, 0, 0.12); tween(s, F.TONE, 0, 0.2);
  for (let i = 0; i < 5; i++) tween(s, F.NODE_SHAKE + i, 0, 0.15);
}
function departure(s: BeamState, from: number) {
  'worklet';
  const completed = from < s.furthest || s.mode === 'advance' || s.mode === 'pay';
  tween(s, F.NODE_DONE + from, completed ? 1 : 0, 0.22);
  tween(s, F.ROW_DONE + from, completed ? 1 : 0, 0.36);
  tween(s, F.NODE_ACTIVE + from, 0, 0.36);
  tween(s, F.ROW_ACTIVE + from, 0, 0.36);
  tween(s, F.ROW_RISE + from, 0, 0.36);
  tween(s, F.ROW_SWEEP + from, 0, 0.36);
  fadeFocus(s);
}
function panel(s: BeamState, target: number, arrival: number, delay: number) {
  'worklet';
  // Metadata changes only at a command boundary; the physical height always retargets in place.
  const previousTo = s.frame[F.PANEL_TO];
  const alreadyTransitioning = s.frame[F.PANEL_MIX] < 0.999 && previousTo !== s.frame[F.PANEL_FROM];
  if (!alreadyTransitioning) {
    s.frame[F.PANEL_FROM] = s.step; s.frame[F.PANEL_TO] = target; s.frame[F.PANEL_MIX] = 0;
  } else {
    // First dim the existing layer. Rebind the two content slots only in their dark interval.
    s.pendingPanel = target; s.panelSwapAt = s.time + 0.12;
  }
  s.errorField = -1;
  tween(s, F.ERR, 0, 0.16);
  for (let i = 0; i < 7; i++) tween(s, F.FIELD_ERR + i, 0, 0.16);
  if (s.still) {
    tween(s, F.PANEL_MIX, 0.4, 0.12, delay, 0);
    tween(s, F.PANEL_H, panelHeight(target, 0), H, delay + 0.12, 0);
    tween(s, F.PANEL_MIX, 1, 0.18, Math.max(delay + 0.12, arrival), 0);
  } else {
    if (alreadyTransitioning) {
      tween(s, F.PANEL_MIX, s.frame[F.PANEL_MIX] >= 0.4 ? 0.4 : 0.3, 0.12, 0, 0);
      tween(s, F.PANEL_MIX, 0.4, Math.max(0.02, arrival - 0.12), 0.12, 0);
    } else {
      tween(s, F.PANEL_MIX, 0.3, 0.16, delay + 0.06, 0);
      tween(s, F.PANEL_MIX, 0.4, Math.max(0.02, arrival - delay - 0.22), delay + 0.22, 0);
    }
    tween(s, F.PANEL_H, panelHeight(target, 0), 0.5, delay + 0.3, 2);
    tween(s, F.PANEL_MIX, 1, 0.38, arrival, 0);
  }
}
function ignition(s: BeamState, target: number, strength: number, haptic: number) {
  'worklet';
  if (!s.still) {
    impulse(s, F.NODE_FLASH + target, strength);
    impulse(s, F.BURST, s.mode === 'pay' ? 2.6 : strength >= 1 ? 2 : 1.5);
    impulse(s, F.HEAD_GLOW, s.mode === 'advance' ? 1.6 : 1.2);
  }
  spring(s, F.NODE_ON + target, 1, 26, 0.55);
  tween(s, F.NODE_ACTIVE + target, s.mode === 'pay' ? 0 : 1, s.still ? 0.2 : 0.18, 0, s.still ? 0 : 1);
  tween(s, F.NODE_DONE + target, 0, 0.22);
  tween(s, F.ROW_DONE + target, 0, 0.22);
  tween(s, F.ROW_ACTIVE + target, s.mode === 'pay' ? 0 : 1, 0.24);
  spring(s, F.ROW_RISE + target, 1, 22, 0.5);
  tween(s, F.ROW_SWEEP + target, s.mode === 'pay' ? 0 : 1, 0.42);
  emit(s, 'haptic', haptic);
}
/** Constructs plain serializable state; no runtime or native objects enter the simulation. */
export function createState(still = false): BeamState {
  'worklet';
  const frame = frameAtRest(0, {}); frame[F.PRESENT] = 0;
  const velocity: number[] = []; const presses: number[] = []; const filled: boolean[] = [];
  for (let i = 0; i < F.SIZE; i++) { velocity.push(0); presses.push(0); }
  for (let i = 0; i < 7; i++) filled.push(false);
  return { frame, velocity, presses, filled, lanes: [], events: [], time: 0, remainder: 0, step: 0, furthest: 0, express: false, focusField: -1, errorField: -1, still, ready: false, mode: 'idle', started: 0, arrival: 0, target: 0, origin: 0, flags: 0, silent: false, dragging: false, grab: 0, deliveryGrab: 0, paidAt: -1, newOrderSent: false, labelMix: 1, labelStarted: -1, pendingPanel: -1, panelSwapAt: 0 };
}
/** Starts the presence fade only after the picture and fonts are ready. */
export function present(s: BeamState) { 'worklet'; if (s.ready) return; s.ready = true; tween(s, F.PRESENT, 1, 0.3); }
/** Starts the CTA copy cross-fade on the simulation clock. */
export function changeLabel(s: BeamState) { 'worklet'; s.labelStarted = s.time; s.labelMix = 0; }
/** Moves one step forward with the 420 ms light flight and delayed ignition. */
export function advance(s: BeamState) {
  'worklet';
  if (s.step >= 3) return;
  clearMajor(s); s.mode = 'advance'; s.target = s.step + 1; s.arrival = 0.54;
  s.furthest = Math.max(s.furthest, s.target); departure(s, s.step);
  if (!s.still) { tween(s, F.HEAD, s.target, 0.42, 0.12, 2); tween(s, F.HEAD_GLOW, 1, 0.08, 0.12); }
  else tween(s, F.HEAD, s.target, H, 0.12, 0);
  panel(s, s.target, 0.54, 0);
  spring(s, F.CTA_SPLIT, 1, 18, 0.8, 0.3);
}
/** Navigates reached steps, pinging every node crossed by a forward whip. */
export function goTo(s: BeamState, target: number) {
  'worklet';
  if (s.step === 4 || s.mode === 'pay' || s.paidAt >= 0 || target < 0 || target > s.furthest) return;
  clearMajor(s); s.mode = 'go'; s.target = Math.round(target);
  s.arrival = Math.min(0.64, 0.32 + 0.11 * Math.abs(target - s.frame[F.HEAD]));
  departure(s, s.step);
  tween(s, F.HEAD, target, s.still ? H : s.arrival, s.still ? 0.12 : 0, 2);
  if (!s.still) tween(s, F.HEAD_GLOW, 1, 0.08);
  panel(s, target, s.arrival, 0);
  spring(s, F.CTA_SPLIT, target > 0 ? 1 : 0, 18, 0.8);
}
/** Glides the single focus light and floats the corresponding field label. */
export function focus(s: BeamState, field: number) {
  'worklet';
  s.focusField = field;
  for (let i = 0; i < 7; i++) tween(s, F.FIELD_FLOAT + i, s.filled[i] || i === field ? 1 : 0, 0.18);
  if (field < 0) { tween(s, F.FOCUS_A, 0, 0.16); return; }
  const r = FIELDS[field]; const rect = [r.x, r.y, r.w, 56];
  for (let i = 0; i < 4; i++) {
    if (s.frame[F.FOCUS_A] < 0.001) impulse(s, F.FOCUS_X + i, rect[i]);
    else spring(s, F.FOCUS_X + i, rect[i], 26, 0.82);
  }
  tween(s, F.FOCUS_A, 1, 0.14);
}
/** Keeps floating labels up for filled fields after focus leaves. */
export function setFilled(s: BeamState, field: number, filled: boolean) {
  'worklet'; s.filled[field] = filled; tween(s, F.FIELD_FLOAT + field, filled || s.focusField === field ? 1 : 0, 0.18);
}
/** Sends the beam into a red recoil and exposes the first invalid field. */
export function reject(s: BeamState, field: number) {
  'worklet';
  clearMajor(s); s.mode = 'reject'; s.target = s.step; s.errorField = field; s.arrival = 0.22;
  if (s.frame[F.PANEL_MIX] < 1) {
    s.pendingPanel = s.step; s.panelSwapAt = s.time + 0.12;
    tween(s, F.PANEL_MIX, 0.4, 0.12, 0, 0);
    tween(s, F.PANEL_MIX, 1, 0.18, 0.12, 0);
  }
  if (!s.still) { tween(s, F.HEAD, s.step + 0.4, 0.22); tween(s, F.HEAD_GLOW, 1, 0.08); }
  tween(s, F.TONE, 1, 0.11, 0.11);
}
/** Clears the red field and smoothly removes its message space. */
export function clearError(s: BeamState) {
  'worklet'; s.errorField = -1;
  for (let i = 0; i < 7; i++) tween(s, F.FIELD_ERR + i, 0, 0.16);
  tween(s, F.ERR, 0, 0.3, 0, 2); tween(s, F.PANEL_H, panelHeight(s.step, 0), 0.3, 0, 2);
}
/** Starts the contracting lens, slow charge, and returning success wave. */
export function pay(s: BeamState) {
  'worklet';
  if (s.step !== 3 || s.mode === 'pay' || s.paidAt >= 0) return;
  clearMajor(s); s.mode = 'pay'; s.target = 4; s.arrival = 1.2; s.furthest = 4;
  departure(s, 3); tween(s, F.CTA_SPLIT, 0, 0.38, 0, 2);
  if (!s.still) {
    tween(s, F.CTA_LENS, 1, 0.38, 0, 2); tween(s, F.HEAD, 4, 0.9, 0.3, 2);
    tween(s, F.HEAD_GLOW, 1.3, 0.9, 0.3); tween(s, F.BURST, 1.4, 0.9, 0.3);
  } else tween(s, F.HEAD, 4, H, 1.2, 0);
}
/** Runs the reversible return sweep and clears the form only after old content vanishes. */
export function restart(s: BeamState) {
  'worklet';
  clearMajor(s); s.mode = 'restart'; s.target = 0; s.arrival = 0.9; s.paidAt = -1;
  s.newOrderSent = false; s.furthest = 0; s.express = false; s.errorField = -1;
  fadeFocus(s); tween(s, F.WARM, 0, 0.5); tween(s, F.CTA_DONE, 0, 0.3);
  tween(s, F.CTA_LENS, 0, 0.3); tween(s, F.CTA_SPLIT, 0, 0.5);
  tween(s, F.SURGE, 0, 0.2); tween(s, F.SEG_LEAD, 0, 0.3); tween(s, F.SEG_TRAIL, 0, 0.3);
  tween(s, F.ERR, 0, 0.16);
  for (let i = 0; i < 7; i++) { s.filled[i] = false; tween(s, F.FIELD_FLOAT + i, 0, 0.18); tween(s, F.FIELD_ERR + i, 0, 0.16); }
  for (let i = 0; i < 5; i++) {
    tween(s, F.NODE_ACTIVE + i, 0, 0.2); tween(s, F.ROW_ACTIVE + i, 0, 0.2);
    tween(s, F.ROW_RISE + i, 0, 0.2); tween(s, F.ROW_SWEEP + i, 0, 0.2);
  }
  if (!s.still) tween(s, F.HEAD_GLOW, 0.8, 0.08);
  tween(s, F.HEAD, 0, s.still ? H : 0.9, s.still ? 0.42 : 0, 2);
  if (s.frame[F.PANEL_MIX] < 1) {
    s.pendingPanel = 0; s.panelSwapAt = s.time + 0.12;
    tween(s, F.PANEL_MIX, 0.4, 0.12, 0, 0);
  } else {
    s.frame[F.PANEL_FROM] = s.step; s.frame[F.PANEL_TO] = 0; s.frame[F.PANEL_MIX] = 0;
  }
  tween(s, F.PANEL_MIX, 1, 0.5, 0.3, 0);
  tween(s, F.PANEL_H, panelHeight(0, 0), s.still ? H : 0.5, s.still ? 0.5 : 0.3, 2);
}
/** Sets one material press target without creating a separate animation clock. */
export function press(s: BeamState, index: number, down: boolean) { 'worklet'; s.presses[index] = down ? 1 : 0; }
/** Selects delivery with independent leading and trailing spring edges. */
export function setDelivery(s: BeamState, express: boolean) {
  'worklet';
  if (s.express !== express) emit(s, 'haptic', 0);
  s.express = express; spring(s, F.SEG_LEAD, express ? 1 : 0, 30, 0.82); spring(s, F.SEG_TRAIL, express ? 1 : 0, 18, 0.74);
}
/** Applies asymptotic resistance outside the reached rail or delivery track. */
export function rubber(value: number, lo: number, hi: number, band: number): number {
  'worklet';
  if (value < lo) return lo - band * (1 - Math.exp((value - lo) / band));
  if (value > hi) return hi + band * (1 - Math.exp((hi - value) / band));
  return value;
}
/** Grabs the current head without moving it to the touch's absolute location. */
export function beginDrag(s: BeamState) {
  'worklet';
  if (s.step === 4 || s.mode === 'pay' || s.paidAt >= 0 || s.furthest === 0) return;
  clearMajor(s); s.mode = 'drag'; s.dragging = true; s.grab = s.frame[F.HEAD];
  tween(s, F.DRAG, 1, 0.12); tween(s, F.NODE_ACTIVE + s.step, 0.3, 0.12);
  if (!s.still) tween(s, F.HEAD_GLOW, 1, 0.08);
}
function pingCrossings(s: BeamState, before: number, after: number, flash: number) {
  'worklet';
  if (s.still) return;
  for (let i = 0; i < 5; i++) {
    if ((before < i && after >= i) || (before > i && after <= i)) {
      impulse(s, F.NODE_FLASH + i, flash);
      if (s.dragging) emit(s, 'haptic', 0);
    }
  }
}
/** Tracks a relative finger displacement exactly and retains its measured velocity. */
export function drag(s: BeamState, displacement: number, velocity: number) {
  'worklet';
  if (!s.dragging) return;
  const previous = s.frame[F.HEAD]; s.frame[F.HEAD] = rubber(s.grab + displacement, 0, s.furthest, 0.18);
  s.velocity[F.HEAD] = velocity; s.frame[F.HEAD_VEL] = velocity;
  pingCrossings(s, previous, s.frame[F.HEAD], 0.4);
}
/** Releases into the nearest velocity-projected node with no loss of finger momentum. */
export function endDrag(s: BeamState, velocity: number) {
  'worklet';
  if (!s.dragging) return;
  s.dragging = false; s.mode = 'release'; s.started = s.time; s.flags = 0;
  s.target = clamp(Math.round(s.frame[F.HEAD] + 0.12 * velocity), 0, s.furthest);
  s.velocity[F.HEAD] = velocity; s.arrival = 0.4;
  if (s.still) tween(s, F.HEAD, s.target, H, 0, 0);
  else spring(s, F.HEAD, s.target, 22, 0.72);
  tween(s, F.DRAG, 0, 0.12);
  // The panel remains frozen throughout finger contact, then joins the release.
  if (s.target !== s.step) { departure(s, s.step); panel(s, s.target, s.arrival, 0); }
  spring(s, F.CTA_SPLIT, s.target > 0 ? 1 : 0, 18, 0.8);
}
/** Moves the delivery thumb from a normalized finger position. */
export function dragDelivery(s: BeamState, position: number) {
  'worklet'; cancel(s, F.SEG_LEAD); s.frame[F.SEG_LEAD] = rubber(position, 0, 1, 0.08);
  spring(s, F.SEG_TRAIL, s.frame[F.SEG_LEAD], 18, 0.74);
}
/** Commits a delivery drag using its position and release velocity. */
export function endDelivery(s: BeamState, velocity: number): boolean {
  'worklet'; const express = s.frame[F.SEG_LEAD] + 0.1 * velocity > 0.5; setDelivery(s, express); return express;
}
function integrateLanes(s: BeamState) {
  'worklet';
  for (let i = 0; i < s.lanes.length; i++) {
    const l = s.lanes[i]; if (s.time + 1e-9 < l.at) continue;
    if (!l.started) {
      l.started = true; l.from = s.frame[l.index];
      // A delayed lane supersedes only earlier lanes for this property.
      for (let j = i - 1; j >= 0; j--) if (s.lanes[j].index === l.index) { s.lanes.splice(j, 1); i--; }
    }
    const previous = s.frame[l.index];
    const elapsed = Math.max(0, s.time - l.at);
    let finished = false;
    if (l.kind === 3) {
      const displacement = previous - l.target;
      const a = -l.omega * l.omega * displacement - 2 * l.zeta * l.omega * s.velocity[l.index];
      s.velocity[l.index] += a * H;
      s.frame[l.index] += s.velocity[l.index] * H;
      finished = (Math.abs(s.frame[l.index] - l.target) < 0.00001 && Math.abs(s.velocity[l.index]) < 0.0001) || elapsed >= l.duration;
    } else {
      s.frame[l.index] = l.from + (l.target - l.from) * ease(elapsed / l.duration, l.kind);
      s.velocity[l.index] = (s.frame[l.index] - previous) / H;
      finished = elapsed + 1e-9 >= l.duration;
    }
    if (finished) { s.frame[l.index] = l.target; s.velocity[l.index] = 0; s.lanes.splice(i, 1); i--; }
  }
}
function restTarget(s: BeamState): number[] {
  'worklet'; return frameAtRest(s.step, { furthest: s.furthest, express: s.express, filled: s.filled, errorField: s.errorField, focusField: s.focusField });
}
function settle(s: BeamState) {
  'worklet';
  s.step = s.target; if (s.errorField < 0) emit(s, 'settled', s.step);
  if (s.mode === 'restart') { emit(s, 'restarted', 0); s.mode = 'idle'; }
  else s.mode = 'settling';
  const rest = restTarget(s);
  for (let i = 0; i < 5; i++) {
    tween(s, F.NODE_DONE + i, rest[F.NODE_DONE + i], 0.2);
    tween(s, F.ROW_DONE + i, rest[F.ROW_DONE + i], 0.2);
    tween(s, F.NODE_ON + i, rest[F.NODE_ON + i], 0.2);
    tween(s, F.NODE_ACTIVE + i, rest[F.NODE_ACTIVE + i], 0.2);
    tween(s, F.ROW_ACTIVE + i, rest[F.ROW_ACTIVE + i], 0.2);
    tween(s, F.ROW_RISE + i, rest[F.ROW_RISE + i], 0.2);
    tween(s, F.ROW_SWEEP + i, rest[F.ROW_SWEEP + i], 0.2);
  }
}
function advanceMode(s: BeamState) {
  'worklet';
  const t = s.time - s.started;
  if (s.mode === 'advance' || s.mode === 'go' || s.mode === 'release') {
    if (!(s.flags & 1) && t + 1e-9 >= s.arrival) {
      s.flags |= 1; ignition(s, s.target, s.mode === 'advance' ? 1 : 0.55, s.target > s.origin ? 2 : 1);
    }
    if ((s.flags & 1) && s.frame[F.PANEL_MIX] >= 1 && t >= s.arrival + (s.still ? 0.18 : 0.38) - 1e-9) settle(s);
  } else if (s.mode === 'reject') {
    if (!(s.flags & 1) && t >= 0.22 - 1e-9) {
      s.flags |= 1;
      if (!s.still) { impulse(s, F.BURST, 1.5); spring(s, F.HEAD, s.step, 24, 0.6); }
      tween(s, F.FIELD_ERR + s.errorField, 1, 0.16); tween(s, F.ERR, 1, 0.3);
      tween(s, F.PANEL_H, panelHeight(s.step, 1), 0.3); focus(s, s.errorField); emit(s, 'haptic', 3);
      tween(s, F.TONE, 0, 0.5, s.still ? 1 : 0.68);
    }
    if (!s.still && t >= 0.48 && t <= 0.84) {
      const q = t - 0.48; s.frame[F.NODE_SHAKE + s.step] = 7 * Math.sin(2 * Math.PI * q / 0.12) * (1 - q / 0.36);
    }
    if (t >= 0.84) s.frame[F.NODE_SHAKE + s.step] = 0;
    if (t >= 1.75) { s.mode = 'settling'; settle(s); }
  } else if (s.mode === 'pay') {
    if (!(s.flags & 1) && t >= 1.2 - 1e-9) {
      s.flags |= 1; s.paidAt = s.time; ignition(s, 4, 1.2, 4); emit(s, 'paid', 4);
      tween(s, F.WARM, 1, 0.5); tween(s, F.CTA_DONE, 1, 0.24);
      if (!s.still) { tween(s, F.SURGE, 1, 0.52, 0, 0); spring(s, F.CTA_LENS, 0, 16, 0.62, 0.12); }
      tween(s, F.NODE_DONE + 4, 1, 0.22, 0.1); tween(s, F.ROW_DONE + 4, 1, 0.22, 0.1);
    }
    if (!(s.flags & 2) && t >= 1.5 - 1e-9) {
      s.flags |= 2; s.frame[F.PANEL_FROM] = 3; s.frame[F.PANEL_TO] = 4; s.frame[F.PANEL_MIX] = 0;
      tween(s, F.PANEL_MIX, 1, 0.5, 0, 0); tween(s, F.PANEL_H, panelHeight(4, 0), s.still ? H : 0.5, s.still ? 0.15 : 0, 2);
    }
    if (!s.still && t >= 1.2 && t <= 1.75) {
      for (let i = 0; i < 4; i++) {
        const bit = 1 << (i + 2);
        if (!(s.flags & bit) && s.frame[F.SURGE] >= (4 - i) / 4 - 1e-9) { s.flags |= bit; impulse(s, F.NODE_FLASH + i, 0.8); }
      }
    }
    if (t >= 2 - 1e-9 && s.frame[F.PANEL_MIX] === 1) settle(s);
  } else if (s.mode === 'restart') {
    if (!(s.flags & 1) && t >= 0.3 && s.frame[F.PANEL_MIX] >= 0.3) { s.flags |= 1; emit(s, 'cleared', 0); }
    for (let i = 1; i < 5; i++) {
      const bit = 1 << (i + 1);
      if (!(s.flags & bit) && s.frame[F.HEAD] <= i) {
        s.flags |= bit; tween(s, F.NODE_ON + i, 0, 0.2); tween(s, F.NODE_DONE + i, 0, 0.2); tween(s, F.ROW_DONE + i, 0, 0.2);
      }
    }
    if (!(s.flags & 2) && t >= 0.9 - 1e-9) {
      s.flags |= 2; ignition(s, 0, 0.5, 1); tween(s, F.NODE_DONE, 0, 0.2); tween(s, F.ROW_DONE, 0, 0.2);
      // Quiet the reset glow before the next loop's first frame.
      tween(s, F.NODE_FLASH, 0, 0.26); tween(s, F.HEAD_GLOW, 0, 0.26); tween(s, F.BURST, 1, 0.26);
      tween(s, F.ROW_RISE, 1, 0.26); tween(s, F.ROW_SWEEP, 1, 0.26); tween(s, F.NODE_ON, 1, 0.26);
    }
    if (t >= 1.17 - 1e-9) settle(s);
  }
}
function substep(s: BeamState) {
  'worklet';
  s.time += H;
  const previousHead = s.frame[F.HEAD];
  integrateLanes(s);
  if (s.pendingPanel >= 0 && s.time >= s.panelSwapAt - 1e-9) {
    s.frame[F.PANEL_FROM] = s.step; s.frame[F.PANEL_TO] = s.pendingPanel; s.pendingPanel = -1;
  }
  advanceMode(s);
  const f = s.frame;
  if ((s.mode === 'go' || s.mode === 'release') && !(s.flags & 1)) pingCrossings(s, previousHead, f[F.HEAD], 0.6);
  // All active spring/tween lanes own their value; decay owns only unclaimed impulses.
  let glowOwned = false; let burstOwned = false;
  for (let i = 0; i < s.lanes.length; i++) {
    if (s.lanes[i].index === F.HEAD_GLOW) glowOwned = true;
    if (s.lanes[i].index === F.BURST) burstOwned = true;
  }
  const travelling = (s.mode === 'advance' || s.mode === 'go' || s.mode === 'release' || s.mode === 'pay' || s.mode === 'restart') && !(s.flags & (s.mode === 'restart' ? 2 : 1));
  if (!glowOwned && !travelling && !s.dragging) f[F.HEAD_GLOW] *= Math.exp(-H / 0.16);
  if (!burstOwned) f[F.BURST] = 1 + (f[F.BURST] - 1) * Math.exp(-H / (s.paidAt >= 0 ? 0.5 : s.mode === 'reject' ? 0.3 : 0.35));
  for (let i = 0; i < 5; i++) {
    let owned = false;
    for (let j = 0; j < s.lanes.length; j++) if (s.lanes[j].index === F.NODE_FLASH + i) owned = true;
    if (!owned) f[F.NODE_FLASH + i] *= Math.exp(-H / 0.22);
    if (s.mode === 'drag' || s.mode === 'go' || s.mode === 'release') {
      // A narrow smooth illumination front avoids discontinuous full-node jumps on crossing.
      const ember = i < s.furthest ? 0.4 : 0;
      const on = ember + (1 - ember) * clamp((f[F.HEAD] - i + 0.18) / 0.18, 0, 1);
      if (!(s.flags & 1) || s.dragging) f[F.NODE_ON + i] = on;
    }
  }
  const pressIndices = [F.CTA_PRESS, F.BACK_PRESS, F.SEG_PRESS, F.NODE_PRESS, F.NODE_PRESS + 1, F.NODE_PRESS + 2, F.NODE_PRESS + 3, F.NODE_PRESS + 4, F.ROW_PRESS, F.ROW_PRESS + 1, F.ROW_PRESS + 2, F.ROW_PRESS + 3, F.ROW_PRESS + 4];
  for (let i = 0; i < pressIndices.length; i++) {
    const n = pressIndices[i]; const target = s.presses[n];
    f[n] += (target - f[n]) * (1 - Math.exp(-H / (target ? 0.045 : 0.11)));
    if (Math.abs(target - f[n]) < 1e-5) f[n] = target;
  }
  f[F.CTA_SPLIT] = clamp(f[F.CTA_SPLIT], 0, 1);
  f[F.HEAD_VEL] = s.velocity[F.HEAD];
  if (f[F.CTA_LENS] > 0 && f[F.CTA_DONE] < 0.99999) f[F.CTA_SPIN] += H * Math.PI * 4;
  if (s.paidAt >= 0 && !s.newOrderSent && s.time - s.paidAt >= 2.6) { s.newOrderSent = true; emit(s, 'newOrder', 0); }
  if (s.labelStarted >= 0) s.labelMix = clamp((s.time - s.labelStarted) / 0.26, 0, 1);
  if (s.still) {
    f[F.HEAD_GLOW] = 0; f[F.BURST] = 1; f[F.SURGE] = 0; f[F.CTA_LENS] = 0; f[F.HEAD_VEL] = 0;
    for (let i = 0; i < 5; i++) { f[F.NODE_FLASH + i] = 0; f[F.NODE_SHAKE + i] = 0; }
  }
  if (s.mode === 'idle' || s.mode === 'settling') {
    if (f[F.PANEL_MIX] === 1) { f[F.PANEL_FROM] = s.step; f[F.PANEL_TO] = s.step; }
    if (f[F.FOCUS_A] === 0) { for (let i = F.FOCUS_X; i <= F.FOCUS_H; i++) f[i] = 0; }
    if (Math.abs(f[F.CTA_LENS]) < 1e-5) f[F.CTA_SPIN] = 0;
    if (s.step === 4 && f[F.SURGE] === 1) f[F.SURGE] = 0;
    if (s.lanes.length === 0) {
      const rest = restTarget(s);
      for (let i = 0; i < F.SIZE; i++) if (Math.abs(f[i] - rest[i]) < 0.001) { f[i] = rest[i]; s.velocity[i] = 0; }
      s.mode = 'idle';
    }
  }
}
/** Advances deterministic fixed 240 Hz substeps, carrying fractional frame time. */
export function step(s: BeamState, dt: number) {
  'worklet';
  if (!s.ready) return;
  s.remainder += Math.max(0, Math.min(dt, 1 / 30));
  while (s.remainder + 1e-10 >= H) { s.remainder -= H; substep(s); }
}
/** Copies the contract frame for publication to the picture. */
export function publish(s: BeamState): number[] {
  'worklet'; const out: number[] = []; for (let i = 0; i < F.SIZE; i++) out.push(s.frame[i]); return out;
}
/** Routes serializable form commands to the motion engine on either Node or the UI thread. */
export function command(s: BeamState, type: string, a = 0, b = 0, silent = false) {
  'worklet';
  s.silent = silent;
  if (type === 'advance') advance(s);
  else if (type === 'go') goTo(s, a);
  else if (type === 'reject') reject(s, a);
  else if (type === 'clearError') clearError(s);
  else if (type === 'pay') pay(s);
  else if (type === 'restart') restart(s);
  else if (type === 'focus') focus(s, a);
  else if (type === 'filled') setFilled(s, a, b === 1);
  else if (type === 'delivery') setDelivery(s, a === 1);
  else if (type === 'press') press(s, a, b === 1);
  else if (type === 'label') changeLabel(s);
  else if (type === 'ready') present(s);
}
