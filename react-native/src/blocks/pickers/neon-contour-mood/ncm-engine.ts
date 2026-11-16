import type { SharedValue } from 'react-native-reanimated';
import {
  clamp,
  computeLabel,
  fillTimeline,
  GESTURE,
  HOLD,
  holdProfile,
  LABEL_SETTLE,
  labelFromTable,
  labelSettled,
  MAXEV,
  MAXL,
  mix,
  pushEvent,
  settleLabel,
  T,
  type Look,
} from './ncm-motion';
import { packField, type FieldUniforms, type MoodPack } from './ncm-shaders';

// The per-frame engine. Every function is a worklet that mutates one set of preallocated arrays in place and then tells the
// shared value (`modify`) that it changed; nothing here allocates per frame and nothing closes over anything.

/** Indices of the scalar state array. */
export const ST = {
  evN: 0,
  idx: 1,
  evT: 2,
  labSettled: 3,
  hFrom: 4,
  hTarget: 5,
  hT0: 6,
  dir: 7,
  speed: 8,
  heldPrev: 9,
  haveHist: 10,
  lastTT: 11,
  lastPlay: 12,
  endIdx: 13,
  lastLeanX: 14,
  lastLeanY: 15,
  lastSqueeze: 16,
  lastPulse: 17,
  size: 18,
} as const;
export const FRAME = { changed: 1, end: 2 } as const;

export type Rig = {
  mood: SharedValue<number>;
  look: SharedValue<Look>;
  lab: SharedValue<number[]>;
  field: SharedValue<FieldUniforms>;
  ev: SharedValue<number[]>;
  st: SharedValue<number[]>;
  hist: SharedValue<number[]>;
  snap: SharedValue<number[]>;
  /** index a release is heading for (-1 when none): the frame that finds it at rest reports FRAME.end */
  endTarget: SharedValue<number>;
};
/** Everything a frame needs that does not change from frame to frame (a plain object, copied to the UI runtime once per render). */
export type Cfg = {
  n: number;
  last: number;
  echoMs: number;
  rings: number;
  spacing: number;
  breathing: boolean;
  reduced: boolean;
  cx: number;
  cy: number;
  heroS: number;
  cutoff: number;
  fade: number;
  p3: number;
  ground: number[];
  core: number[];
  pack: MoodPack;
  widths: number[];
  lens: number[];
  table: number[];
};
export function newState(idx: number) {
  const st = new Array<number>(ST.size).fill(0);
  st[ST.idx] = idx;
  st[ST.labSettled] = 0;
  st[ST.lastPulse] = -1;
  return st;
}
export function newSnap() {
  return new Array<number>(MAXL).fill(0);
}
/** Identity modifier: the arrays were changed in place, this only notifies the shared value's listeners. */
export function touch<V>(v: V) {
  'worklet';
  return v;
}

/** Scalar uniforms and the per-ring packing, written from the current look. */
export function fillField(u: FieldUniforms, look: Look, c: Cfg, leanX: number, leanY: number, squeeze: number, pulse: number) {
  'worklet';
  u.center[0] = c.cx;
  u.center[1] = c.cy;
  u.scale = c.heroS;
  u.cutoff = c.cutoff;
  u.fade = c.fade;
  u.rings = c.rings;
  u.p3 = c.p3;
  for (let i = 0; i < 3; i++) {
    u.ground[i] = c.ground[i];
    u.coreCol[i] = c.core[i];
  }
  u.wake = c.reduced ? 0 : look.wake;
  u.breath = c.breathing && !c.reduced ? look.breath : 0;
  u.radiate = look.radiate * Math.max(0, look.time - T.flickEnd) * 26;
  u.spacing = c.spacing + squeeze;
  u.lean[0] = leanX;
  u.lean[1] = leanY;
  u.pulse = pulse;
  packField(u, look.echoes, c.pack);
}
/** Label frame time: recomputed while a switch is still running, once more when it has finished, then left alone. */
function labelBusy(st: number[], now: number) {
  'worklet';
  return now - st[ST.evT] < LABEL_SETTLE;
}

/** Autoplay and the dev pin: a pure function of loop time. */
export function playFrame(rig: Rig, c: Cfg, tt: number) {
  'worklet';
  const look = rig.look.get();
  const st = rig.st.get();
  fillTimeline(look, tt, c.echoMs, c.n);
  const lab = rig.lab.get();
  labelFromTable(lab, c.table, look.time, c.widths, c.lens, rig.snap.get(), st[ST.idx]);
  st[ST.lastTT] = look.time;
  st[ST.lastPlay] = 1;
  st[ST.haveHist] = 0;
  st[ST.idx] = lab[1];
  fillField(rig.field.get(), look, c, 0, 0, 0, -1);
  rig.look.modify(touch);
  rig.lab.modify(touch);
  rig.field.modify(touch);
}
/** True when every value of `a` is within `tol` of `v`. */
export function nearAll(a: number[], v: number, tol: number) {
  'worklet';
  for (let i = 0; i < a.length; i++) if (Math.abs(a[i] - v) > tol) return false;
  return true;
}
/** A field driven by a bare progress value (ContourField): the rings follow it on their history filters, nothing else. */
export function followFrame(u: FieldUniforms, look: Look, hist: number[], target: number, c: Cfg, dt: number) {
  'worklet';
  const m = clamp(target, 0, c.last);
  const safeDt = dt < 0.001 ? 0.001 : dt;
  const lo = Math.max(0, m - 1.6);
  const hi = Math.min(c.last, m + 1.6);
  let changed = false;
  for (let k = 0; k < 7; k++) {
    let h = m;
    if (k > 0 && !c.reduced) {
      const tau = (k * c.echoMs) / 1000;
      const v = hist[k] + (m - hist[k]) * (1 - Math.exp(-safeDt / (tau < 0.001 ? 0.001 : tau)));
      h = v < lo ? lo : v > hi ? hi : v;
    }
    hist[k] = h;
    if (Math.abs(h - look.echoes[k]) > 0.0001) changed = true;
    look.echoes[k] = h;
  }
  const wake = c.reduced ? 0 : Math.max(clamp(Math.abs(m - look.mood) / safeDt / 10), look.wake * Math.exp(-safeDt / GESTURE.wakeTau));
  if (Math.abs(wake - look.wake) > 0.0001 || Math.abs(m - look.mood) > 0.00001) changed = true;
  look.mood = m;
  look.wake = wake;
  look.breath = 0;
  look.radiate = 0;
  look.time = 0;
  const refill = u.center[0] !== c.cx || u.center[1] !== c.cy || u.rings !== c.rings || u.scale !== c.heroS;
  if (changed || refill) fillField(u, look, c, 0, 0, 0, -1);
  return changed || refill;
}
/** Dev hook: hold every ring at one mood. */
export function explicitFrame(rig: Rig, c: Cfg, m: number) {
  'worklet';
  const look = rig.look.get();
  const st = rig.st.get();
  look.mood = m;
  for (let k = 0; k < 7; k++) look.echoes[k] = m;
  look.wake = 0;
  look.breath = 0;
  look.radiate = 0;
  look.time = 0;
  const idx = Math.round(clamp(m, 0, c.last));
  st[ST.idx] = idx;
  st[ST.lastPlay] = 0;
  st[ST.haveHist] = 0;
  labelSettled(rig.lab.get(), idx, c.widths, c.lens);
  fillField(rig.field.get(), look, c, 0, 0, 0, -1);
  rig.look.modify(touch);
  rig.lab.modify(touch);
  rig.field.modify(touch);
}
/** Takes over from autoplay without a jump: the displayed rings become the history, running switches carry over. */
function handoff(rig: Rig, c: Cfg, now: number) {
  'worklet';
  const look = rig.look.get();
  const st = rig.st.get();
  const hist = rig.hist.get();
  const ev = rig.ev.get();
  for (let k = 0; k < 7; k++) hist[k] = look.echoes[k];
  st[ST.hFrom] = 0;
  st[ST.hTarget] = 0;
  st[ST.hT0] = now;
  st[ST.dir] = 0;
  st[ST.speed] = 0;
  st[ST.haveHist] = 1;
  if (st[ST.lastPlay] === 1) {
    // the last few switches of the loop, rebased onto the frame clock
    const tt = st[ST.lastTT];
    const table = c.table;
    let count = 0;
    while (count < table.length / 3 && table[count * 3] <= tt) count++;
    const from = Math.max(0, count - 4);
    let n = 0;
    for (let e = from; e < count && n < MAXEV; e++) {
      ev[n * 3] = now - (tt - table[e * 3]);
      ev[n * 3 + 1] = table[e * 3 + 1];
      ev[n * 3 + 2] = table[e * 3 + 2];
      n++;
    }
    st[ST.evN] = n;
    st[ST.evT] = n > 0 ? ev[(n - 1) * 3] : -99;
    st[ST.idx] = rig.lab.get()[1];
    st[ST.labSettled] = 0;
    st[ST.lastPlay] = 0;
  }
}
/**
 * One frame of the finger-driven picker. Returns FRAME.changed when the drawing needs the new look and FRAME.end when a
 * release has come to rest (ST.endIdx holds the index).
 */
export function interactiveFrame(
  rig: Rig,
  c: Cfg,
  now: number,
  dt: number,
  held: boolean,
  press: number,
  returning: boolean,
  leanX: number,
  leanY: number,
  squeeze: number,
  pulseT0: number,
) {
  'worklet';
  const st = rig.st.get();
  if (st[ST.haveHist] !== 1) handoff(rig, c, now);
  const look = rig.look.get();
  const hist = rig.hist.get();
  const prevM = look.mood;
  const m = clamp(rig.mood.get(), 0, c.last);
  const safeDt = dt < 0.001 ? 0.001 : dt;
  let flags = 0;
  let changed = false;

  // touch-down starts a fresh gesture: no nesting until the finger has travelled
  if (held && st[ST.heldPrev] !== 1) st[ST.dir] = 0;
  st[ST.heldPrev] = held ? 1 : 0;
  const inst = Math.abs(m - prevM) / safeDt;
  st[ST.speed] = mix(st[ST.speed], inst, 1 - Math.exp(-safeDt / GESTURE.speedTau));
  if (inst > HOLD.slow && m !== prevM) st[ST.dir] = m > prevM ? 1 : -1;
  const wasHolding = st[ST.hTarget] === 1;
  const wantHold = !c.reduced && held && st[ST.dir] !== 0 && (wasHolding ? st[ST.speed] < HOLD.slow * 1.5 : st[ST.speed] < HOLD.slow);
  if (wantHold !== wasHolding) {
    st[ST.hFrom] = holdProfile(st[ST.hFrom], st[ST.hTarget], now - st[ST.hT0]);
    st[ST.hTarget] = wantHold ? 1 : 0;
    st[ST.hT0] = now;
  }
  const hFrom = st[ST.hFrom];
  const hTarget = st[ST.hTarget];
  const hAge = now - st[ST.hT0];
  const dir = st[ST.dir];
  const lo = Math.max(0, m - 1.6);
  const hi = Math.min(c.last, m + 1.6);
  const echoes = look.echoes;
  for (let k = 0; k < 7; k++) {
    let h = m;
    if (k > 0 && !c.reduced) {
      const tau = returning ? 0.08 : (k * c.echoMs) / 1000;
      const v = hist[k] + (m - hist[k]) * (1 - Math.exp(-safeDt / (tau < 0.001 ? 0.001 : tau)));
      h = v < lo ? lo : v > hi ? hi : v;
    }
    hist[k] = h;
    let e = h;
    if (k > 0 && !c.reduced) {
      const nested = clamp(m - dir * k * HOLD.gap, 0, c.last);
      e = h + (nested - h) * holdProfile(hFrom, hTarget, hAge - k * HOLD.ringDelay);
    }
    if (Math.abs(e - echoes[k]) > 0.0001) changed = true;
    echoes[k] = e;
  }
  const holdNow = holdProfile(hFrom, hTarget, hAge);
  const speed = Math.abs(m - prevM) / safeDt;
  const wake = c.reduced ? 0 : Math.max(clamp(speed / 10), look.wake * Math.exp(-safeDt / GESTURE.wakeTau));
  const breath = c.breathing && !c.reduced ? 1.2 * Math.sin(now * Math.PI) * holdNow : 0;
  if (
    Math.abs(m - prevM) > 0.00001 ||
    Math.abs(wake - look.wake) > 0.0001 ||
    Math.abs(press - look.press) > 0.0001 ||
    Math.abs(breath - look.breath) > 0.0001
  )
    changed = true;
  look.mood = m;
  look.wake = wake;
  look.press = press;
  look.breath = breath;
  look.radiate = 0;
  look.time = 0;
  const pulse = pulseT0 >= 0 && now - pulseT0 < 0.5 ? now - pulseT0 : -1;
  if (leanX !== st[ST.lastLeanX] || leanY !== st[ST.lastLeanY] || squeeze !== st[ST.lastSqueeze] || pulse !== st[ST.lastPulse])
    changed = true;
  st[ST.lastLeanX] = leanX;
  st[ST.lastLeanY] = leanY;
  st[ST.lastSqueeze] = squeeze;
  st[ST.lastPulse] = pulse;
  if (holdNow > 0.0001 && holdNow < 0.9999) changed = true;

  // one label, one word, one tag
  const idx = settleLabel(st[ST.idx], m, c.last);
  const ev = rig.ev.get();
  let labChanged = false;
  if (idx !== st[ST.idx]) {
    if (c.reduced) {
      labelSettled(rig.lab.get(), idx, c.widths, c.lens);
    } else {
      st[ST.evN] = pushEvent(ev, st[ST.evN], now, st[ST.idx], idx);
      st[ST.evT] = now;
      st[ST.labSettled] = 0;
    }
    st[ST.idx] = idx;
    labChanged = true;
  }
  if (!c.reduced) {
    if (labelBusy(st, now)) {
      computeLabel(rig.lab.get(), ev, st[ST.evN], now, st[ST.idx], c.widths, c.lens, rig.snap.get());
      labChanged = true;
    } else if (st[ST.labSettled] !== 1) {
      computeLabel(rig.lab.get(), ev, st[ST.evN], st[ST.evT] + 99, st[ST.idx], c.widths, c.lens, rig.snap.get());
      st[ST.labSettled] = 1;
      labChanged = true;
    }
  }
  if (labChanged) rig.lab.modify(touch);

  // a release that has come to rest
  const target = rig.endTarget.get();
  if (target >= 0 && !held && Math.abs(m - target) < 0.01 && st[ST.speed] < 0.05) {
    st[ST.endIdx] = target;
    rig.endTarget.set(-1);
    flags |= FRAME.end;
  }
  if (changed) {
    fillField(rig.field.get(), look, c, leanX, leanY, squeeze, pulse);
    rig.look.modify(touch);
    rig.field.modify(touch);
    flags |= FRAME.changed;
  }
  return flags;
}
