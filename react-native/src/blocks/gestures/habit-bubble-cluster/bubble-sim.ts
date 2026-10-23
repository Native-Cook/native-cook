/**
 * The cluster as a tiny physics world, run as worklets on the UI thread. Every bubble springs towards a
 * home; overlapping bubbles are pushed apart by mass (r²); a dragged bubble is kinematic and shoves the rest.
 * Homes come from a deterministic packing, so the same radii always give the same cluster.
 */
import { BURST, HOLD, IDLE, LOOP_MS, PHYSICS, QA_HOLD, SCATTER, STAGGER, WAVE } from './motion';
import { LAYOUT, PERSONAL, QUIET, SOCIAL, type Habit } from './theme';

export const ENTITIES: (Habit & { set: number; quiet?: boolean })[] = [
  ...SOCIAL.map((h) => ({ ...h, set: 0 })),
  { id: 'quiet', name: QUIET.title, icon: 'moon-outline' as const, hue: 'red' as const, r: QUIET.r, x: QUIET.x, y: QUIET.y, set: 0, quiet: true },
  ...PERSONAL.map((h) => ({ ...h, set: 1 })),
];
export const COUNT = ENTITIES.length;
export const QUIET_INDEX = SOCIAL.length;

export type Sim = {
  x: number[]; y: number[]; vx: number[]; vy: number[];
  r: number[]; rv: number[]; rTarget: number[];
  hx: number[]; hy: number[];
  done: number[]; doneTarget: number[];
  flash: number[];
  tab: number;
  // The bubble shown on its own in the detail view (-1: none).
  focus: number;
  // Touch: -1 none; mode 0 press, 1 charge, 2 drag.
  held: number; mode: number; pressMs: number; charge: number; ticks: number; ring: number;
  fx: number; fy: number; ox: number; oy: number; sx: number; sy: number;
  burst: number; burstMs: number;
  // Staggered radius changes (set switch): the target each bubble takes at `pendingAt` on the sim clock.
  pendingR: number[]; pendingAt: number[];
  lastTick: number;
  clock: number; remainder: number;
  events: number[];
};

export const EVENT = { tick: 1, done: 2, undo: 3, grab: 4, count: 5 } as const;

function baseRadius(i: number, tab: number, done: boolean): number {
  'worklet';
  const e = ENTITIES[i];
  if (e.set !== tab) return 0;
  return done ? e.r * HOLD.doneScale : e.r;
}

/** Packs the active bubbles: gravity to the centre plus full separation, from the measured seeds. */
export function packHomes(s: Sim): void {
  'worklet';
  const x: number[] = [];
  const y: number[] = [];
  for (let i = 0; i < COUNT; i++) {
    x.push(ENTITIES[i].x);
    y.push(ENTITIES[i].y);
  }
  let active = 0;
  for (let i = 0; i < COUNT; i++) if (s.rTarget[i] > 0 && !ENTITIES[i].quiet) active += 1;
  // With every bubble at its designed size the measured layout is the packing (pixel-exact rest state);
  // relaxation only runs once a radius has changed. A lone bubble also stays where the design puts it.
  let changed = false;
  for (let i = 0; i < COUNT; i++) if (s.rTarget[i] > 0 && Math.abs(s.rTarget[i] - ENTITIES[i].r) > 0.5) changed = true;
  const iterations = active > 1 && changed ? PHYSICS.relaxIterations : 0;
  for (let it = 0; it < iterations; it++) {
    for (let i = 0; i < COUNT; i++) {
      if (s.rTarget[i] <= 0 || ENTITIES[i].quiet) continue;
      x[i] -= x[i] * PHYSICS.relaxGravity;
      y[i] -= y[i] * PHYSICS.relaxGravity;
    }
    for (let i = 0; i < COUNT; i++) {
      const ri = s.rTarget[i];
      if (ri <= 0) continue;
      for (let j = i + 1; j < COUNT; j++) {
        const rj = s.rTarget[j];
        if (rj <= 0) continue;
        const dx = x[j] - x[i];
        const dy = y[j] - y[i];
        const d = Math.max(0.001, Math.hypot(dx, dy));
        const min = ri + rj + LAYOUT.gap;
        if (d >= min) continue;
        // The quiet dial stays put; otherwise heavier bubbles move less.
        const wi = ENTITIES[i].quiet ? 0 : rj * rj / (ri * ri + rj * rj);
        const wj = ENTITIES[j].quiet ? 0 : ri * ri / (ri * ri + rj * rj);
        const sum = wi + wj || 1;
        const push = (min - d) / d;
        x[i] -= dx * push * (wi / sum);
        y[i] -= dy * push * (wi / sum);
        x[j] += dx * push * (wj / sum);
        y[j] += dy * push * (wj / sum);
      }
    }
  }
  for (let i = 0; i < COUNT; i++) {
    s.hx[i] = x[i];
    s.hy[i] = y[i];
  }
}

export function initialSim(): Sim {
  'worklet';
  const s: Sim = {
    x: [], y: [], vx: [], vy: [], r: [], rv: [], rTarget: [], hx: [], hy: [], done: [], doneTarget: [], flash: [],
    tab: 0, focus: -1, held: -1, mode: 0, pressMs: 0, charge: 0, ticks: 0, ring: -1, fx: 0, fy: 0, ox: 0, oy: 0, sx: 0, sy: 0,
    burst: -1, burstMs: 0, clock: 0, remainder: 0, events: [], pendingR: [], pendingAt: [], lastTick: -1e9,
  };
  for (let i = 0; i < COUNT; i++) {
    const r = baseRadius(i, 0, false);
    s.x.push(ENTITIES[i].x); s.y.push(ENTITIES[i].y); s.vx.push(0); s.vy.push(0);
    s.r.push(r); s.rv.push(0); s.rTarget.push(r); s.hx.push(0); s.hy.push(0);
    s.done.push(0); s.doneTarget.push(0); s.flash.push(-1); s.pendingR.push(-1); s.pendingAt.push(0);
  }
  packHomes(s);
  for (let i = 0; i < COUNT; i++) {
    s.x[i] = s.hx[i];
    s.y[i] = s.hy[i];
  }
  return s;
}

export function setDone(s: Sim, i: number, done: boolean): void {
  'worklet';
  s.doneTarget[i] = done ? 1 : 0;
  s.rTarget[i] = baseRadius(i, s.tab, done);
  if (done) {
    s.flash[i] = 0;
    s.burst = i;
    s.burstMs = 0;
    // A shock wave: every neighbour is kicked outward, harder the closer it is (the cluster ripples, then repacks).
    for (let k = 0; k < COUNT; k++) {
      if (k === i || s.rTarget[k] <= 0 || ENTITIES[k].quiet) continue;
      const dx = s.x[k] - s.x[i];
      const dy = s.y[k] - s.y[i];
      const d = Math.max(1, Math.hypot(dx, dy));
      const kick = WAVE.speed * Math.exp(-d / WAVE.reach);
      s.vx[k] += (dx / d) * kick;
      s.vy[k] += (dy / d) * kick;
    }
  }
  packHomes(s);
  s.events.push(done ? EVENT.done : EVENT.undo, EVENT.count);
}

export function setTab(s: Sim, tab: number): void {
  'worklet';
  if (s.tab === tab) return;
  s.tab = tab;
  // Re-form with intent: bubbles leave and arrive in order of distance from the centre, 30 ms apart per 60 pt.
  for (let i = 0; i < COUNT; i++) {
    const target = baseRadius(i, tab, s.doneTarget[i] > 0.5);
    const delay = (Math.hypot(ENTITIES[i].x, ENTITIES[i].y) / STAGGER.per) * STAGGER.stepMs;
    s.pendingR[i] = target;
    s.pendingAt[i] = s.clock + delay;
  }
  // Homes are packed for the new sizes; the sizes themselves change when each bubble's turn comes.
  const before = s.rTarget.slice();
  for (let i = 0; i < COUNT; i++) s.rTarget[i] = s.pendingR[i];
  packHomes(s);
  for (let i = 0; i < COUNT; i++) s.rTarget[i] = before[i];
  s.events.push(EVENT.count);
}

/** Detail view: the tapped bubble moves to (x, y) and grows to r; the rest leave. */
export function openFocus(s: Sim, i: number, x: number, y: number, r: number): void {
  'worklet';
  s.focus = i;
  // Shared element: the tapped bubble flies up and grows; the others scatter outward from it as they shrink away.
  for (let k = 0; k < COUNT; k++) {
    s.rTarget[k] = k === i ? r : 0;
    if (k === i) continue;
    const dx = s.x[k] - s.x[i];
    const dy = s.y[k] - s.y[i];
    const d = Math.max(1, Math.hypot(dx, dy));
    s.hx[k] = s.x[k] + (dx / d) * SCATTER.distance;
    s.hy[k] = s.y[k] + (dy / d) * SCATTER.distance;
  }
  s.hx[i] = x;
  s.hy[i] = y;
}

export function closeFocus(s: Sim): void {
  'worklet';
  s.focus = -1;
  for (let k = 0; k < COUNT; k++) s.rTarget[k] = baseRadius(k, s.tab, s.doneTarget[k] > 0.5);
  packHomes(s);
}

/** Which bubble is under (x, y), cluster coordinates. The quiet dial never takes touches. */
export function hitTest(s: Sim, x: number, y: number): number {
  'worklet';
  let best = -1;
  let bestD = Infinity;
  for (let i = 0; i < COUNT; i++) {
    if (ENTITIES[i].quiet || s.rTarget[i] <= 0) continue;
    const d = Math.hypot(x - s.x[i], y - s.y[i]);
    // Small bubbles get a 44 pt touch target.
    if (d < Math.max(s.r[i], 22) && d < bestD) { best = i; bestD = d; }
  }
  return best;
}

export function touchDown(s: Sim, x: number, y: number): void {
  'worklet';
  if (s.focus >= 0) {
    s.held = -1;
    return;
  }
  const i = hitTest(s, x, y);
  s.held = i;
  s.mode = 0;
  s.pressMs = 0;
  s.charge = 0;
  s.ticks = 0;
  s.fx = x; s.fy = y; s.sx = x; s.sy = y;
  if (i >= 0) {
    s.ox = s.x[i] - x;
    s.oy = s.y[i] - y;
    s.events.push(EVENT.grab);
  }
}

export function touchMove(s: Sim, x: number, y: number): void {
  'worklet';
  s.fx = x;
  s.fy = y;
  if (s.held < 0 || s.mode === 2) return;
  if (Math.hypot(x - s.sx, y - s.sy) > HOLD.moveTolerance) s.mode = 2;
}

/** Returns the bubble a short, still tap landed on (to open its detail), or -1. */
export function touchUp(s: Sim, vx: number, vy: number, reduced: boolean): number {
  'worklet';
  const i = s.held;
  s.held = -1;
  if (i < 0) return -1;
  if (s.mode === 2) {
    if (!reduced) {
      const v = Math.hypot(vx, vy);
      const k = v > PHYSICS.maxFling ? PHYSICS.maxFling / v : 1;
      s.vx[i] = vx * k;
      s.vy[i] = vy * k;
    }
    return -1;
  }
  if (s.pressMs < HOLD.tapMs && s.doneTarget[i] > 0.5) {
    setDone(s, i, false);
    return -1;
  }
  return s.pressMs < HOLD.tapMs ? i : -1;
}

function approach(v: number, target: number, rate: number): number {
  'worklet';
  return v + (target - v) * Math.min(1, rate);
}

export function stepSim(s: Sim, ms: number, reduced: boolean): void {
  'worklet';
  s.remainder += Math.min(ms, PHYSICS.maxFrameMs);
  const dt = PHYSICS.step;
  const dms = dt * 1000;
  while (s.remainder >= dms - 1e-6) {
    s.remainder -= dms;
    s.clock += dms;
    // The hold: a short press first, then the ring charges; completion at 100 %.
    if (s.held >= 0 && s.mode !== 2) {
      s.pressMs += dms;
      if (s.pressMs >= HOLD.delayMs && s.doneTarget[s.held] < 0.5) {
        s.mode = 1;
        s.ring = s.held;
        s.charge = Math.min(1, s.charge + dms / HOLD.chargeMs);
        const tick = s.charge >= HOLD.ticks[1] ? 2 : s.charge >= HOLD.ticks[0] ? 1 : 0;
        if (tick > s.ticks) { s.ticks = tick; s.events.push(EVENT.tick); }
        if (s.charge >= 1) {
          setDone(s, s.held, true);
          s.held = -1;
          s.mode = 0;
        }
      }
    } else if (s.charge > 0 && (s.held < 0 || s.mode === 2)) {
      s.charge = s.charge >= 1 ? 0 : Math.max(0, s.charge - dms / HOLD.drainMs);
    }
    for (let i = 0; i < COUNT; i++) {
      if (s.pendingR[i] >= 0 && s.clock >= s.pendingAt[i]) {
        s.rTarget[i] = s.pendingR[i];
        s.pendingR[i] = -1;
      }
    }
    for (let i = 0; i < COUNT; i++) {
      // Radius: springs (or eases under Reduce Motion) towards its target; a held bubble squeezes in.
      const squeeze = i === s.held && s.mode !== 2 && !reduced ? HOLD.squeeze : 1;
      // Periods divide the autoplay loop, so every loop of the preview breathes identically.
      const breathe = reduced || QA_HOLD !== null ? 0 : Math.sin((s.clock / (LOOP_MS / (IDLE.cyclesMin + (i % 3)))) * Math.PI * 2 + i) * IDLE.breathe;
      const rt = s.rTarget[i] * squeeze * (1 + breathe);
      if (reduced) {
        s.r[i] = approach(s.r[i], rt, dt * 18);
        s.rv[i] = 0;
      } else {
        s.rv[i] += (PHYSICS.radiusStiffness * (rt - s.r[i]) - PHYSICS.radiusDamping * s.rv[i]) * dt;
        s.r[i] = Math.max(0, s.r[i] + s.rv[i] * dt);
      }
      s.done[i] = approach(s.done[i], s.doneTarget[i], dt * (reduced ? 14 : 9));
      if (s.flash[i] >= 0) s.flash[i] = s.flash[i] + dms > BURST.flashMs ? -1 : s.flash[i] + dms;
      if (i === s.held && s.mode === 2) {
        const nx = s.fx + s.ox;
        const ny = s.fy + s.oy;
        s.vx[i] = (nx - s.x[i]) / dt;
        s.vy[i] = (ny - s.y[i]) / dt;
        s.x[i] = nx;
        s.y[i] = ny;
        continue;
      }
      if (reduced) {
        s.x[i] = approach(s.x[i], s.hx[i], dt * 18);
        s.y[i] = approach(s.y[i], s.hy[i], dt * 18);
        s.vx[i] = 0;
        s.vy[i] = 0;
        continue;
      }
      s.vx[i] += (PHYSICS.stiffness * (s.hx[i] - s.x[i]) - PHYSICS.damping * s.vx[i]) * dt;
      s.vy[i] += (PHYSICS.stiffness * (s.hy[i] - s.y[i]) - PHYSICS.damping * s.vy[i]) * dt;
      s.x[i] += s.vx[i] * dt;
      s.y[i] += s.vy[i] * dt;
    }
    // Collisions: push overlapping bubbles apart; the dragged one does not yield.
    for (let i = 0; i < COUNT; i++) {
      const ri = s.r[i];
      if (ri <= 0.5) continue;
      for (let j = i + 1; j < COUNT; j++) {
        const rj = s.r[j];
        if (rj <= 0.5) continue;
        const dx = s.x[j] - s.x[i];
        const dy = s.y[j] - s.y[i];
        const d = Math.max(0.001, Math.hypot(dx, dy));
        // The designed layout lets some bubbles touch closer than radius + gap: never push a pair apart
        // further than its designed spacing while both are at their designed size.
        const designed = Math.abs(s.rTarget[i] - ENTITIES[i].r) < 0.5 && Math.abs(s.rTarget[j] - ENTITIES[j].r) < 0.5
          ? Math.hypot(ENTITIES[j].x - ENTITIES[i].x, ENTITIES[j].y - ENTITIES[i].y) : Infinity;
        const min = Math.min(ri + rj + LAYOUT.gap, designed);
        if (d >= min) continue;
        const fixedI = (i === s.held && s.mode === 2) || ENTITIES[i].quiet;
        const fixedJ = (j === s.held && s.mode === 2) || ENTITIES[j].quiet;
        if (fixedI && fixedJ) continue;
        const wi = fixedI ? 0 : fixedJ ? 1 : rj * rj / (ri * ri + rj * rj);
        const wj = fixedJ ? 0 : fixedI ? 1 : ri * ri / (ri * ri + rj * rj);
        const push = ((min - d) / d) * PHYSICS.separation;
        s.x[i] -= dx * push * wi; s.y[i] -= dy * push * wi;
        s.x[j] += dx * push * wj; s.y[j] += dy * push * wj;
        // Hand some of the shove to the velocity so a fling carries into its neighbours.
        s.vx[i] -= (dx * push * wi) / dt * 0.25; s.vy[i] -= (dy * push * wi) / dt * 0.25;
        s.vx[j] += (dx * push * wj) / dt * 0.25; s.vy[j] += (dy * push * wj) / dt * 0.25;
      }
    }
    if (s.burst >= 0) {
      s.burstMs += dms;
      if (s.burstMs > BURST.sparkMs) s.burst = -1;
    }
  }
}

export function doneCount(s: Sim): { done: number; total: number } {
  'worklet';
  let done = 0;
  let total = 0;
  for (let i = 0; i < COUNT; i++) {
    if (ENTITIES[i].set !== s.tab || ENTITIES[i].quiet) continue;
    total += 1;
    if (s.doneTarget[i] > 0.5) done += 1;
  }
  return { done, total };
}

/** Deep copy for publishing through a shared value. */
export function copySim(s: Sim): Sim {
  'worklet';
  return {
    ...s,
    x: s.x.slice(), y: s.y.slice(), vx: s.vx.slice(), vy: s.vy.slice(), r: s.r.slice(), rv: s.rv.slice(),
    rTarget: s.rTarget.slice(), hx: s.hx.slice(), hy: s.hy.slice(), done: s.done.slice(), doneTarget: s.doneTarget.slice(),
    flash: s.flash.slice(), events: [],
  };
}
