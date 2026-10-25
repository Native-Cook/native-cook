/*
 Magnetic Letters Name — the door under a finger, on the UI thread. Each magnet keeps one flight
 record (kind, from, to, start, duration); its pose at any time comes from the curves in
 magneticLettersMotion.ts, so a flight can be interrupted by another from wherever it is.
 Every function takes the block's context and the time `at` (seconds on the frame clock).
*/
import type { Layout } from './magneticLettersArt';
import {
  DROP, FLY, KIND_DROP, KIND_HELD, KIND_LAND, KIND_REST, KIND_SNAP, POP, type Pose,
  autoPhase, dropPose, easeOutCubic, landPose, snapPose,
} from './magneticLettersMotion';

/** A shifted letter slides along the line in this long. */
const SHIFT = 0.28;

export type Ctx = {
  L: Layout;
  /** Per magnet: its letter's index in SCRIPT, or -1. */
  scriptOf: number[];
  /** The loop's slots for SCRIPT's letters. */
  autoSlots: Pose[];
  maxFit: number;
  reduced: boolean;
};

export type Sim = {
  kind: number[];
  from: Pose[];
  to: Pose[];
  start: number[];
  dur: number[];
  /** Where each magnet rests on the heap (moved by a drag; reset when the loop takes over). */
  home: Pose[];
  /** The poses drawn this frame. */
  poses: Pose[];
  /** The magnets on the name line, left to right. */
  name: number[];
  held: number;
  heldX: number;
  heldY: number;
  grabX: number;
  grabY: number;
  vx: number;
  ready: number;
  idleDrawn: boolean;
};

export function makeSim(L: Layout): Sim {
  const n = L.magnets.length;
  const pose = (p: Pose) => ({ ...p });
  return {
    kind: L.magnets.map(() => KIND_REST),
    from: L.magnets.map((m) => pose(m.home)),
    to: L.magnets.map((m) => pose(m.home)),
    start: new Array(n).fill(0),
    dur: new Array(n).fill(1),
    home: L.magnets.map((m) => pose(m.home)),
    poses: L.magnets.map((m) => pose(m.home)),
    name: [],
    held: -1,
    heldX: 0,
    heldY: 0,
    grabX: 0,
    grabY: 0,
    vx: 0,
    ready: 0,
    idleDrawn: false,
  };
}

/** Magnet i in the loop at loop time t. */
export function autoPose(c: Ctx, i: number, t: number, out: Pose) {
  'worklet';
  const home = c.L.magnets[i].home;
  const k = c.scriptOf[i];
  if (k >= 0) {
    const p = autoPhase(k, t);
    if (p.phase === 1) return snapPose(home, c.autoSlots[k], p.since, FLY, out);
    if (p.phase === 2) return landPose(c.autoSlots[k], home, p.since, POP, out);
  }
  out.x = home.x; out.y = home.y; out.rot = home.rot; out.lift = 0; out.scale = 1;
  return out;
}

/** Magnet i under touch at time `at`; returns whether it is still moving. */
export function simPose(c: Ctx, s: Sim, i: number, at: number, out: Pose) {
  'worklet';
  const k = s.kind[i];
  const u = at - s.start[i];
  if (k === KIND_HELD) {
    const lift = c.reduced ? 1 : easeOutCubic(u / 0.12);
    out.x = s.heldX + s.grabX;
    out.y = s.heldY + s.grabY;
    out.rot = s.from[i].rot + Math.max(-0.3, Math.min(0.3, s.vx * 0.0005));
    out.lift = Math.max(s.from[i].lift, lift);
    out.scale = 1 + 0.06 * out.lift;
    return true;
  }
  if (k === KIND_SNAP) snapPose(s.from[i], s.to[i], u, s.dur[i], out);
  else if (k === KIND_LAND) landPose(s.from[i], s.to[i], u, s.dur[i], out);
  else if (k === KIND_DROP) dropPose(s.from[i], u, out);
  else {
    const p = s.to[i];
    out.x = p.x; out.y = p.y; out.rot = p.rot; out.lift = 0; out.scale = 1;
    return false;
  }
  return u < s.dur[i] + 0.7;
}

export function startFlight(c: Ctx, s: Sim, i: number, kind: number, to: Pose, at: number, dur: number) {
  'worklet';
  const cur = s.poses[i];
  s.from[i] = { x: cur.x, y: cur.y, rot: cur.rot, lift: cur.lift, scale: cur.scale };
  s.to[i] = { x: to.x, y: to.y, rot: to.rot, lift: 0, scale: 1 };
  s.kind[i] = kind;
  s.start[i] = at;
  s.dur[i] = c.reduced ? 0.001 : dur;
}

export function slotPose(c: Ctx, s: Sim, k: number): Pose {
  'worklet';
  let x = c.L.lineX0;
  for (let j = 0; j < k; j++) x += c.L.magnets[s.name[j]].w + c.L.gap;
  return { x: x + c.L.magnets[s.name[k]].w / 2, y: c.L.letterY, rot: 0, lift: 0, scale: 1 };
}

/** Slides every letter from position k on to its slot (after an insert or a removal). */
export function relayout(c: Ctx, s: Sim, k: number, at: number) {
  'worklet';
  for (let j = k; j < s.name.length; j++) {
    const m = s.name[j];
    if (m !== s.held) startFlight(c, s, m, KIND_SNAP, slotPose(c, s, j), at, SHIFT);
  }
}

export function nameText(c: Ctx, s: Sim) {
  'worklet';
  let out = '';
  for (let j = 0; j < s.name.length; j++) out += c.L.magnets[s.name[j]].letter;
  return out;
}

/** Whether one more magnet of width w still fits on the line. */
export function fits(c: Ctx, s: Sim, w: number) {
  'worklet';
  if (s.name.length >= c.maxFit) return false;
  let x = c.L.lineX0;
  for (let j = 0; j < s.name.length; j++) x += c.L.magnets[s.name[j]].w + c.L.gap;
  return x + w <= c.L.lineX1;
}

export function typeMagnet(c: Ctx, s: Sim, i: number, at: number) {
  'worklet';
  if (!fits(c, s, c.L.magnets[i].w) || s.name.indexOf(i) >= 0) return;
  s.name.push(i);
  startFlight(c, s, i, KIND_SNAP, slotPose(c, s, s.name.length - 1), at, FLY);
}

export function removeAt(c: Ctx, s: Sim, k: number, land: boolean, at: number) {
  'worklet';
  const m = s.name[k];
  s.name.splice(k, 1);
  if (land) startFlight(c, s, m, KIND_LAND, s.home[m], at, POP);
  relayout(c, s, k, at);
}

export function typeLetter(c: Ctx, s: Sim, ch: string, at: number) {
  'worklet';
  for (let i = 0; i < c.L.magnets.length; i++) {
    if (c.L.magnets[i].letter === ch && s.name.indexOf(i) < 0 && s.held !== i) {
      typeMagnet(c, s, i, at);
      return;
    }
  }
}

/** The topmost magnet under a point, or -1. */
export function hit(c: Ctx, s: Sim, x: number, y: number) {
  'worklet';
  let best = -1;
  let bestKey = -1;
  for (let i = 0; i < c.L.magnets.length; i++) {
    const p = s.poses[i];
    const dx = x - p.x;
    const dy = y - p.y;
    const r = c.L.magnets[i].r + 6;
    if (dx * dx + dy * dy < r * r) {
      const key = p.lift * 10 + p.y / 10000 + 1;
      if (key > bestKey) { bestKey = key; best = i; }
    }
  }
  return best;
}

/** Copies the loop's state at loop time t into the record, so a touch continues from it. */
export function takeOver(c: Ctx, s: Sim, t: number, at: number) {
  'worklet';
  s.name = [];
  s.held = -1;
  for (let i = 0; i < c.L.magnets.length; i++) {
    const home = c.L.magnets[i].home;
    s.home[i] = { ...home };
    s.kind[i] = KIND_REST;
    s.to[i] = { ...home };
    const k = c.scriptOf[i];
    if (k < 0) continue;
    const p = autoPhase(k, t);
    if (p.phase === 1) {
      s.kind[i] = KIND_SNAP; s.from[i] = { ...home }; s.to[i] = { ...c.autoSlots[k] };
      s.start[i] = at - p.since; s.dur[i] = FLY;
    } else if (p.phase === 2 && p.since < POP + 0.7) {
      s.kind[i] = KIND_LAND; s.from[i] = { ...c.autoSlots[k] }; s.to[i] = { ...home };
      s.start[i] = at - p.since; s.dur[i] = POP;
    }
  }
  // The line holds the script letters that have left the heap and not yet popped, in order.
  for (let k = 0; k < c.L.script.length; k++) if (autoPhase(k, t).phase === 1) s.name.push(c.L.script[k]);
}

/** Picks up magnet i under the finger at (x, y); a letter taken off the line closes the gap. */
export function grab(c: Ctx, s: Sim, i: number, x: number, y: number, at: number) {
  'worklet';
  const k = s.name.indexOf(i);
  if (k >= 0) {
    s.name.splice(k, 1);
    relayout(c, s, k, at);
  }
  const cur = s.poses[i];
  s.from[i] = { ...cur };
  s.kind[i] = KIND_HELD;
  s.start[i] = at;
  s.held = i;
  s.heldX = x; s.heldY = y; s.grabX = cur.x - x; s.grabY = cur.y - y; s.vx = 0;
}

/** Lets go of the held magnet: onto the line where it was dropped, or back down onto the steel. */
export function drop(c: Ctx, s: Sim, at: number) {
  'worklet';
  const i = s.held;
  if (i < 0) return false;
  s.held = -1;
  const p = s.poses[i];
  const L = c.L;
  if (Math.abs(p.y - L.letterY) < 56 && fits(c, s, L.magnets[i].w)) {
    let k = 0;
    while (k < s.name.length && slotPose(c, s, k).x < p.x) k++;
    s.name.splice(k, 0, i);
    startFlight(c, s, i, KIND_SNAP, slotPose(c, s, k), at, FLY * 0.8);
    relayout(c, s, k + 1, at);
    return true;
  }
  const x = Math.min(L.W - 40, Math.max(24, p.x));
  const y = Math.min(L.pile.y1, Math.max(L.lineY + 30, p.y));
  s.home[i] = { x, y, rot: p.rot, lift: 0, scale: 1 };
  s.from[i] = { x, y, rot: p.rot, lift: p.lift, scale: p.scale };
  s.to[i] = { ...s.home[i] };
  s.kind[i] = KIND_DROP;
  s.start[i] = at;
  s.dur[i] = DROP;
  return false;
}

/** The line empties and every magnet away from its home flies back, staggered (done in 0.85 s). */
export function sendHome(c: Ctx, s: Sim, at: number) {
  'worklet';
  s.name = [];
  s.held = -1;
  let j = 0;
  for (let n = 0; n < c.L.order.length; n++) {
    const i = c.L.order[n];
    const home = c.L.magnets[i].home;
    const p = s.poses[i];
    s.home[i] = { ...home };
    const far = Math.abs(p.x - home.x) + Math.abs(p.y - home.y) + Math.abs(p.rot - home.rot) * 20 + p.lift * 20 > 0.5;
    if (far) {
      startFlight(c, s, i, KIND_LAND, home, at + Math.min(0.4, j * 0.04), POP);
      j++;
    } else {
      s.kind[i] = KIND_REST;
      s.to[i] = { ...home };
    }
  }
}
