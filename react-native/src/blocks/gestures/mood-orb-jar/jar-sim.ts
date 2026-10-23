/**
 * Fixed-step UI-thread simulation of the jar: a pile of orbs that keep their slots, one orb thrown on a solved arc
 * that lands with real collisions, the lid's hinge and the undo swipe. A finger and the autoplay share `input`.
 * Worklet helpers are defined before their callers (a worklet captures what exists when it is created).
 */
import { LAYOUT, MOODS, PILE_MOODS, REPLICA, REPLICA_CUT, REPLICA_PILE, STAGE_WIDTH } from './theme';
import { AIM, GHOST, HAPTIC, HOP, LID, LOOP_MS, MAX_STEPS, PILE, RESUME_MS, RIPPLE, SCRIPT, SOFT, STEP, THROW, TILT, UNDO } from './motion';

export const ORB = { off: 0, pile: 1, settling: 2, flying: 3, held: 4, leaving: 5, returning: 6 } as const;
export const EVENT = { added: 100, removed: 200 } as const;
/** Orbs in the pile: the source's 18, where its frame shows them. */
export const PILE_N = REPLICA_PILE.length;
export const POOL = PILE_N + 4;

export type Geometry = {
  w: number; h: number; s: number; top: number; cx: number;
  mouthY: number; floor: number; left: number; right: number; shoulderEnd: number; mouthHalf: number; corner: number;
  slotY: number; slotX: number[]; rowY: number; rowH: number; lidX: number; lidY: number;
};

export function makeGeometry(w: number, h: number, safeTop: number): Geometry {
  const s = w / STAGE_WIDTH;
  const top = safeTop + LAYOUT.header * s;
  const cx = w / 2;
  const jw = LAYOUT.jarWidth * s;
  const mouthY = top + LAYOUT.jarTop * s;
  const slotX: number[] = [];
  for (let i = 0; i < MOODS.length; i++) slotX.push(cx + (LAYOUT.slotDx + (i - (MOODS.length - 1) / 2) * LAYOUT.slotGap) * s);
  return {
    w, h, s, top, cx, mouthY,
    floor: mouthY + (LAYOUT.jarHeight - LAYOUT.base) * s,
    left: cx - jw / 2 + LAYOUT.wall * s,
    right: cx + jw / 2 - LAYOUT.wall * s,
    shoulderEnd: mouthY + LAYOUT.shoulder * s,
    mouthHalf: LAYOUT.mouthHalf * s,
    corner: LAYOUT.corner * s,
    slotY: top + (LAYOUT.pickerTop + LAYOUT.pickerOrbY) * s,
    slotX,
    rowY: top + LAYOUT.entriesTop * s,
    rowH: LAYOUT.entryHeight * s,
    lidX: cx - (LAYOUT.lidWidth * s) / 2,
    lidY: mouthY - (LAYOUT.lidHeight - LAYOUT.lidOverlap) * s,
  };
}

export type Input =
  | { type: 'down'; x: number; y: number; script?: boolean }
  | { type: 'move'; x: number; y: number; vx: number; vy: number }
  | { type: 'up'; vx: number; vy: number }
  | { type: 'command'; action: 'add' | 'undo'; mood?: number };

export type Sim = {
  acc: number; clock: number;
  x: number[]; y: number[]; vx: number[]; vy: number[]; hx: number[]; hy: number[];
  mood: number[]; state: number[]; restT: number[]; t: number[]; spin: number[]; fade: number[];
  fromX: number[]; fromY: number[];
  held: number; gdx: number; gdy: number; downX: number; downY: number; downT: number; moved: number;
  swiping: boolean; rowX: number; rowV: number; lastAdded: number; swipeX0: number;
  lid: number; lidV: number; lidHold: number; slotScale: number[];
  scripted: boolean; demoOn: boolean; demoT: number; quiet: number;
  reduced: boolean; events: number[];
  sq: number[]; sqv: number[]; pvy: number[]; tilt: number;
  ghostT: number; ghostX: number; ghostY: number; ghostMood: number; growT: number;
  rippleT: number; rippleX: number; rippleY: number; rippleMood: number; hopAt: number;
  cut: number; // replica: 1 after the source's cut to its fuller jar
};

export function clamp(v: number, lo = 0, hi = 1) {
  'worklet';
  return v < lo ? lo : v > hi ? hi : v;
}

function lcg(seed: number) {
  'worklet';
  return (seed * 1103515245 + 12345) % 2147483648;
}

function easeInOut(u: number) {
  'worklet';
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}

function inJar(st: number) {
  'worklet';
  return st === ORB.pile || st === ORB.settling;
}

/** Keeps a point inside the jar: floor, round bottom corners, straight walls, shoulders narrowing to the mouth. */
function bound(g: Geometry, px: number[], py: number[], i: number, r: number) {
  'worklet';
  if (py[i] < g.mouthY + r * 0.5) return;
  if (py[i] > g.floor - r) py[i] = g.floor - r;
  let half = (g.right - g.left) / 2;
  if (py[i] < g.shoulderEnd) {
    const u = clamp((py[i] - g.mouthY) / (g.shoulderEnd - g.mouthY));
    half = g.mouthHalf + (half - g.mouthHalf) * (u * u * (3 - 2 * u));
  }
  const lo = g.cx - half + r;
  const hi = g.cx + half - r;
  if (px[i] < lo) px[i] = lo;
  if (px[i] > hi) px[i] = hi;
  const cy = g.floor - g.corner;
  if (py[i] > cy) {
    const cxs = px[i] < g.cx ? g.left + g.corner : g.right - g.corner;
    if ((px[i] < g.cx && px[i] < cxs) || (px[i] >= g.cx && px[i] > cxs)) {
      const dx = px[i] - cxs;
      const dy = py[i] - cy;
      const d = Math.sqrt(dx * dx + dy * dy) || 1e-6;
      const lim = g.corner - r;
      if (d > lim) {
        px[i] = cxs + (dx / d) * lim;
        py[i] = cy + (dy / d) * lim;
      }
    }
  }
}

/** One step of the orbs in the jar: springs (pile) or gravity (landing), then collisions on predicted positions. */
function stepJar(s: Sim, g: Geometry, settleAll: boolean) {
  'worklet';
  const dt = STEP;
  // Glass beads do not overlap: they pack at their drawn radius, less a hair for the antialiased rim.
  const r = LAYOUT.orb * g.s * 0.98;
  const px: number[] = [];
  const py: number[] = [];
  const damp = Math.exp(-PILE.damping * dt);
  for (let i = 0; i < POOL; i++) {
    if (inJar(s.state[i])) {
      if (s.state[i] === ORB.settling || settleAll) s.vy[i] += THROW.gravity * g.s * dt;
      else {
        // Phone tilt shifts every slot a little: the pile sloshes.
        s.vx[i] += -PILE.home * (s.x[i] - s.hx[i] - s.tilt * TILT.slosh * g.s) * dt;
        s.vy[i] += -PILE.home * (s.y[i] - s.hy[i]) * dt;
      }
      s.vx[i] *= damp;
      s.vy[i] *= damp;
    }
    px.push(s.x[i] + (inJar(s.state[i]) ? s.vx[i] * dt : 0));
    py.push(s.y[i] + (inJar(s.state[i]) ? s.vy[i] * dt : 0));
  }
  let impact = 0;
  for (let k = 0; k < PILE.passes; k++) {
    for (let i = 0; i < POOL; i++) {
      if (!inJar(s.state[i])) continue;
      for (let j = i + 1; j < POOL; j++) {
        if (!inJar(s.state[j])) continue;
        const dx = px[j] - px[i];
        const dy = py[j] - py[i];
        const d = Math.sqrt(dx * dx + dy * dy) || 1e-6;
        if (d >= r * 2) continue;
        const c = (r * 2 - d) / d / 2;
        px[i] -= dx * c;
        py[i] -= dy * c;
        px[j] += dx * c;
        py[j] += dy * c;
        if (k === 0 && (s.state[i] === ORB.settling || s.state[j] === ORB.settling)) impact = Math.max(impact, r * 2 - d);
      }
    }
    for (let i = 0; i < POOL; i++) if (inJar(s.state[i])) bound(g, px, py, i, r);
  }
  for (let i = 0; i < POOL; i++) {
    if (!inJar(s.state[i])) continue;
    s.vx[i] = (px[i] - s.x[i]) / dt;
    s.vy[i] = (py[i] - s.y[i]) / dt;
    s.x[i] = px[i];
    s.y[i] = py[i];
  }
  return impact;
}

export function initialSim(g: Geometry, reduced: boolean, autoPlay: boolean): Sim {
  'worklet';
  const s: Sim = {
    acc: 0, clock: 0, x: [], y: [], vx: [], vy: [], hx: [], hy: [], mood: [], state: [], restT: [], t: [], spin: [], fade: [],
    fromX: [], fromY: [],
    held: -1, gdx: 0, gdy: 0, downX: 0, downY: 0, downT: 0, moved: 0,
    swiping: false, rowX: 0, rowV: 0, lastAdded: -1, swipeX0: 0,
    lid: 0, lidV: 0, lidHold: 0, slotScale: [1, 1, 1, 1, 1],
    scripted: false, demoOn: autoPlay, demoT: 0, quiet: 0, reduced, events: [],
    sq: [], sqv: [], pvy: [], tilt: 0,
    ghostT: -1, ghostX: 0, ghostY: 0, ghostMood: 0, growT: -1, rippleT: -1, rippleX: 0, rippleY: 0, rippleMood: 0, hopAt: 0, cut: 0,
  };
  // A seeded pile, dropped and settled once; that pose is its home.
  let seed = PILE.seed;
  const r = LAYOUT.orb * g.s;
  for (let i = 0; i < POOL; i++) {
    seed = lcg(seed);
    const on = i < PILE_N;
    const col = i % 5;
    const row = Math.floor(i / 5);
    s.x.push(g.cx + (col - 2) * r * 2.2 + (row % 2) * r * 0.6);
    s.y.push(g.floor - r - row * r * 2.4 - 20 * g.s);
    s.vx.push(0);
    s.vy.push(0);
    s.hx.push(0);
    s.hy.push(0);
    // The reference's jar is mostly awful/bad purples and blues, a few neutral, good and great.
    s.mood.push(PILE_MOODS[i % PILE_MOODS.length]);
    s.state.push(on ? ORB.settling : ORB.off);
    s.restT.push(0);
    s.t.push(0);
    s.spin.push(0);
    s.fade.push(on ? 1 : 0);
    s.fromX.push(0);
    s.fromY.push(0);
    s.sq.push(0);
    s.sqv.push(0);
    s.pvy.push(0);
  }
  // Dropped and settled under gravity: the pile is stacked by the physics, not placed.
  for (let k = 0; k < PILE.settleSteps; k++) stepJar(s, g, true);
  // Then let the pile find its own rest on its springs: a gravity pose can leave two beads a hair inside each other,
  // and the collisions would push one off its slot on the first frame (a 1 pt pop at every loop seam). Each round
  // takes where it settled as the new home, until nothing moves.
  for (let round = 0; round < 4; round++) {
    for (let i = 0; i < PILE_N; i++) {
      s.state[i] = ORB.pile;
      s.hx[i] = s.x[i];
      s.hy[i] = s.y[i];
      s.vx[i] = 0;
      s.vy[i] = 0;
    }
    for (let k = 0; k < 240; k++) stepJar(s, g, false);
  }
  for (let i = 0; i < PILE_N; i++) {
    s.hx[i] = s.x[i];
    s.hy[i] = s.y[i];
    s.vx[i] = 0;
    s.vy[i] = 0;
  }
  return s;
}

function emit(s: Sim, e: number) {
  'worklet';
  if (!s.scripted && !s.demoOn) s.events.push(e);
}

function freeOrb(s: Sim) {
  'worklet';
  for (let i = PILE_N; i < POOL; i++) if (s.state[i] === ORB.off) return i;
  return -1;
}

/** Launch on an arc solved to peak above the mouth and drop into it, blended with the finger's own velocity. */
function launch(s: Sim, g: Geometry, i: number, ux: number, uy: number) {
  'worklet';
  const gr = THROW.gravity * g.s;
  const apex = Math.min(s.y[i], g.mouthY) - THROW.apex * g.s;
  const vy0 = -Math.sqrt(2 * gr * Math.max(1, s.y[i] - apex));
  const tUp = -vy0 / gr;
  const tDown = Math.sqrt((2 * (g.mouthY + 40 * g.s - apex)) / gr);
  const vx0 = (g.cx - s.x[i]) / (tUp + tDown);
  const k = THROW.solved;
  const lim = THROW.maxSpeed * g.s;
  s.vx[i] = clamp(vx0 * k + ux * (1 - k) * 0.3, -lim, lim);
  s.vy[i] = vy0 * k + clamp(uy, -lim, 0) * (1 - k) * 0.25;
  // The finger's share must not miss the mouth: re-aim horizontally for the time actually flown.
  const t = (-s.vy[i] + Math.sqrt(s.vy[i] * s.vy[i] + 2 * gr * Math.max(0, g.mouthY + 40 * g.s - s.y[i]))) / gr;
  s.vx[i] = (g.cx - s.x[i]) / Math.max(0.2, t);
  s.state[i] = ORB.flying;
  s.spin[i] = 0;
  s.slotScale[s.mood[i]] = 0;
  emit(s, HAPTIC.throw);
}

/** Reduce Motion: the orb appears at the slot physics would give it, without the flight. */
function placeInstantly(s: Sim, g: Geometry, i: number) {
  'worklet';
  s.x[i] = g.cx;
  s.y[i] = g.mouthY + 30 * g.s;
  s.vx[i] = 0;
  s.vy[i] = 0;
  s.state[i] = ORB.settling;
  for (let k = 0; k < 240; k++) stepJar(s, g, false);
  s.state[i] = ORB.pile;
  s.hx[i] = s.x[i];
  s.hy[i] = s.y[i];
  s.fade[i] = 0;
  s.slotScale[s.mood[i]] = 0;
}

function added(s: Sim, i: number) {
  'worklet';
  s.lastAdded = i;
  s.events.push(EVENT.added + s.mood[i]);
  // Shared element: a copy of the orb flies to the new entry's avatar.
  if (!s.reduced) {
    s.ghostT = 0;
    s.ghostX = s.x[i];
    s.ghostY = s.y[i];
    s.ghostMood = s.mood[i];
    s.growT = -2; // the new card waits for the orb to arrive
  }
}

function undo(s: Sim) {
  'worklet';
  const i = s.lastAdded;
  if (i < 0 || s.state[i] !== ORB.pile) return;
  s.state[i] = ORB.leaving;
  s.t[i] = 0;
  s.fromX[i] = s.x[i];
  s.fromY[i] = s.y[i];
  s.lastAdded = -1;
  s.events.push(EVENT.removed);
  emit(s, HAPTIC.undo);
}

export function input(s: Sim, g: Geometry, ev: Input) {
  'worklet';
  if (ev.type === 'command') {
    s.demoOn = false;
    s.quiet = 0;
    if (ev.action === 'undo') undo(s);
    else if (ev.mood !== undefined) {
      const i = freeOrb(s);
      if (i < 0) return;
      s.mood[i] = ev.mood;
      s.x[i] = g.slotX[ev.mood];
      s.y[i] = g.slotY;
      s.fade[i] = 1;
      if (s.reduced) placeInstantly(s, g, i);
      else launch(s, g, i, 0, 0);
      if (s.reduced) added(s, i);
    }
    return;
  }
  if (ev.type === 'down') {
    s.scripted = ev.script === true;
    if (!s.scripted) {
      s.demoOn = false;
      s.quiet = 0;
    }
    s.downX = ev.x;
    s.downY = ev.y;
    s.downT = s.clock;
    s.moved = 0;
    for (let m = 0; m < MOODS.length; m++) {
      if (Math.hypot(ev.x - g.slotX[m], ev.y - g.slotY) < 30 * g.s && s.slotScale[m] > 0.9) {
        const i = freeOrb(s);
        if (i < 0) return;
        s.held = i;
        s.mood[i] = m;
        s.state[i] = ORB.held;
        s.x[i] = g.slotX[m];
        s.y[i] = g.slotY;
        s.fade[i] = 1;
        s.gdx = s.x[i] - ev.x;
        s.gdy = s.y[i] - ev.y;
        s.slotScale[m] = 0;
        emit(s, HAPTIC.grab);
        return;
      }
    }
    if (s.lastAdded >= 0 && ev.y > g.rowY && ev.y < g.rowY + g.rowH) {
      s.swiping = true;
      s.swipeX0 = ev.x;
    }
    return;
  }
  if (ev.type === 'move') {
    s.moved = Math.max(s.moved, Math.hypot(ev.x - s.downX, ev.y - s.downY));
    if (s.held >= 0) {
      s.x[s.held] = ev.x + s.gdx;
      s.y[s.held] = ev.y + s.gdy;
    } else if (s.swiping) {
      const d = Math.min(0, ev.x - s.swipeX0);
      s.rowX = d > -140 * g.s ? d : -140 * g.s - (-(d + 140 * g.s)) * 0.3;
    }
    return;
  }
  // up
  if (s.held >= 0) {
    const i = s.held;
    s.held = -1;
    const tap = s.moved < 8 * g.s && s.clock - s.downT < 0.25;
    if (ev.vy < -THROW.minFlick * g.s || tap) {
      if (s.reduced) {
        placeInstantly(s, g, i);
        added(s, i);
      } else launch(s, g, i, ev.vx, ev.vy);
    } else {
      s.state[i] = ORB.returning;
      s.fromX[i] = s.x[i];
      s.fromY[i] = s.y[i];
      s.t[i] = 0;
    }
  } else if (s.swiping) {
    s.swiping = false;
    if (s.rowX < -UNDO.threshold * g.s) undo(s);
  }
  s.scripted = false;
}

/** Replica: as in the source, an orb drops straight into the jar from above while the lid tips open. */
function dropIn(s: Sim, g: Geometry, mood: number) {
  'worklet';
  const i = freeOrb(s);
  if (i < 0) return;
  s.mood[i] = mood;
  s.x[i] = g.cx + 12 * g.s;
  s.y[i] = g.mouthY - 70 * g.s;
  s.vx[i] = 0;
  s.vy[i] = 0;
  s.fade[i] = 1;
  s.spin[i] = 0;
  s.state[i] = ORB.flying;
}

function runDemo(s: Sim, g: Geometry, t0: number, t1: number) {
  'worklet';
  if (REPLICA) {
    if (t0 <= SCRIPT.pick[0] && SCRIPT.pick[0] < t1) dropIn(s, g, 1);
    // The source cuts to a fuller jar (traced as its own plate, the dropped orb in it) with the new entry listed.
    if (t0 <= REPLICA_CUT && REPLICA_CUT < t1) {
      for (let i = PILE_N; i < POOL; i++) {
        if (s.state[i] === ORB.off) continue;
        if (s.lastAdded !== i) added(s, i);
        s.ghostT = -1;
        s.growT = -1;
        s.fade[i] = 0;
      }
    }
    s.cut = t1 >= REPLICA_CUT && t1 < SCRIPT.swipe[1] ? 1 : 0;
    if (t0 <= SCRIPT.swipe[1] && SCRIPT.swipe[1] < t1) undo(s);
    return;
  }
  const p = SCRIPT.pick;
  const sx = g.slotX[SCRIPT.mood];
  if (t0 <= p[0] && p[0] < t1) input(s, g, { type: 'down', x: sx, y: g.slotY, script: true });
  if (t1 > p[0] && t0 < p[1] && s.held >= 0) {
    const u = clamp((Math.min(t1, p[1]) - p[0]) / (p[1] - p[0]));
    const e = u * u;
    input(s, g, { type: 'move', x: sx + SCRIPT.pickBy[0] * g.s * e, y: g.slotY + SCRIPT.pickBy[1] * g.s * e, vx: 0, vy: 0 });
  }
  if (t0 < p[1] && p[1] <= t1 && s.held >= 0) {
    input(s, g, { type: 'up', vx: SCRIPT.release[0] * g.s, vy: SCRIPT.release[1] * g.s });
  }
  const w = SCRIPT.swipe;
  const rowCy = g.rowY + g.rowH / 2;
  if (t0 <= w[0] && w[0] < t1) input(s, g, { type: 'down', x: g.w * 0.72, y: rowCy, script: true });
  if (t1 > w[0] && t0 < w[1] && s.swiping) {
    const u = easeInOut(clamp((Math.min(t1, w[1]) - w[0]) / (w[1] - w[0])));
    input(s, g, { type: 'move', x: g.w * 0.72 + SCRIPT.swipeBy * g.s * u, y: rowCy, vx: 0, vy: 0 });
  }
  if (t0 < w[1] && w[1] <= t1 && s.swiping) input(s, g, { type: 'up', vx: 0, vy: 0 });
}

function fixedStep(s: Sim, g: Geometry, autoPlay: boolean) {
  'worklet';
  const dt = STEP;
  if (s.demoOn) {
    const t0 = s.demoT;
    let t1 = t0 + dt;
    runDemo(s, g, t0, t1);
    if (t1 * 1000 >= LOOP_MS) {
      t1 -= LOOP_MS / 1000;
      for (let i = 0; i < POOL; i++) {
        if (s.state[i] === ORB.pile && i < PILE_N) {
          s.x[i] = s.hx[i];
          s.y[i] = s.hy[i];
          s.vx[i] = 0;
          s.vy[i] = 0;
        }
      }
    }
    s.demoT = t1;
  } else if (autoPlay && s.held < 0 && !s.swiping) {
    s.quiet += dt;
    let busy = false;
    for (let i = PILE_N; i < POOL; i++) if (s.state[i] !== ORB.off) busy = true;
    if (s.quiet * 1000 >= RESUME_MS && !busy) {
      s.demoOn = true;
      s.demoT = 0;
    }
  }

  // Flight, return and departure are kinematic; landing and the pile are physics.
  const r = LAYOUT.orb * g.s;
  let anyAir = false;
  for (let i = 0; i < POOL; i++) {
    const st = s.state[i];
    if (st === ORB.flying) {
      anyAir = true;
      s.vy[i] += THROW.gravity * g.s * dt;
      s.x[i] += s.vx[i] * dt;
      s.y[i] += s.vy[i] * dt;
      s.spin[i] += THROW.spin * dt * Math.sign(s.vx[i] || 1);
      if (s.vy[i] > 0 && s.y[i] > g.mouthY + r && Math.abs(s.x[i] - g.cx) < g.mouthHalf) {
        s.state[i] = ORB.settling;
        s.restT[i] = 0;
        s.t[i] = -1; // not landed yet: the first impact gives one haptic
      }
      if (s.y[i] > g.h + r) {
        s.state[i] = ORB.off;
        s.slotScale[s.mood[i]] = 0;
      }
    } else if (st === ORB.returning || st === ORB.leaving) {
      anyAir = anyAir || st === ORB.leaving;
      s.t[i] += dt;
      const tx = g.slotX[s.mood[i]];
      if (st === ORB.returning) {
        const u = easeInOut(clamp(s.t[i] / 0.3));
        s.x[i] = s.fromX[i] + (tx - s.fromX[i]) * u;
        s.y[i] = s.fromY[i] + (g.slotY - s.fromY[i]) * u;
        if (u >= 1) {
          s.state[i] = ORB.off;
          s.slotScale[s.mood[i]] = 1;
        }
      } else if (s.reduced) {
        s.fade[i] = 1 - clamp(s.t[i] / 0.2);
        if (s.t[i] >= 0.2) s.state[i] = ORB.off;
      } else if (s.t[i] < UNDO.rise) {
        // Straight up out of the jar, then an arc back to its slot in the picker.
        const u = easeInOut(s.t[i] / UNDO.rise);
        s.x[i] = s.fromX[i] + (g.cx - s.fromX[i]) * u;
        s.y[i] = s.fromY[i] + (g.mouthY - 50 * g.s - s.fromY[i]) * u;
      } else {
        const u = easeInOut(clamp((s.t[i] - UNDO.rise) / UNDO.fly));
        const ax = g.cx;
        const ay = g.mouthY - 50 * g.s;
        s.x[i] = ax + (tx - ax) * u;
        s.y[i] = ay + (g.slotY - ay) * u - Math.sin(Math.PI * u) * 60 * g.s;
        if (u >= 1) s.state[i] = ORB.off;
      }
    }
  }

  for (let i = 0; i < POOL; i++) s.pvy[i] = s.vy[i];
  stepJar(s, g, false);
  for (let i = 0; i < POOL; i++) {
    if (!inJar(s.state[i])) continue;
    // Soft bodies: a sudden stop squashes the orb, the spring overshoots into a stretch.
    const dv = s.pvy[i] - s.vy[i];
    if (dv > SOFT.trigger * g.s && !s.reduced) {
      s.sqv[i] += Math.min(SOFT.max, dv * SOFT.perSpeed / g.s) * 30;
      if (s.state[i] === ORB.settling && s.t[i] < 0) {
        s.t[i] = 0;
        s.sq[i] = Math.max(s.sq[i], SOFT.landing);
        emit(s, dv > SOFT.hard * g.s ? HAPTIC.landHard : dv > SOFT.soft * g.s ? HAPTIC.land : HAPTIC.landSoft);
        s.rippleT = 0;
        s.rippleX = s.x[i];
        s.rippleY = s.y[i];
        s.rippleMood = s.mood[i];
      }
    }
    s.sqv[i] += (-SOFT.stiffness * s.sq[i] - SOFT.damping * s.sqv[i]) * dt;
    s.sq[i] = Math.max(-SOFT.max, Math.min(SOFT.max, s.sq[i] + s.sqv[i] * dt));
  }
  // Ambient: every HOP.every s one orb near the top of the pile hops a little.
  // On the loop's own time (HOP.every divides the loop, offset so no hop is under way when the loop starts), so every
  // loop of the autoplay is the same.
  const lt = s.demoOn ? s.demoT : s.clock;
  const hop = Math.floor((lt + HOP.phase) / HOP.every);
  if (hop !== s.hopAt) {
    s.hopAt = hop;
    const k = (hop * 7) % PILE_N;
    // (None as the loop starts, so it begins at rest.)
    if (hop !== 0 && lt < HOP.last && !s.reduced && s.state[k] === ORB.pile) s.vy[k] -= HOP.speed * g.s;
  }
  if (s.ghostT >= 0) {
    s.ghostT += dt;
    if (s.ghostT > GHOST.duration) {
      s.ghostT = -1;
      s.growT = 0; // container morph: the card grows out of the avatar
    }
  }
  if (s.growT >= 0) s.growT += dt;
  if (s.rippleT >= 0) {
    s.rippleT += dt;
    if (s.rippleT > RIPPLE.duration) s.rippleT = -1;
  }
  for (let i = 0; i < POOL; i++) {
    if (s.state[i] !== ORB.settling) continue;
    const v = Math.hypot(s.vx[i], s.vy[i]);
    s.restT[i] = v < PILE.restSpeed * g.s ? s.restT[i] + dt : 0;
    if (s.restT[i] >= PILE.restTime) {
      s.state[i] = ORB.pile;
      s.hx[i] = s.x[i];
      s.hy[i] = s.y[i];
      added(s, i);
    }
    if (s.y[i] < g.shoulderEnd) anyAir = true;
  }
  for (let i = 0; i < POOL; i++) {
    // (In the replica's cut the dropped orb is part of the traced plate, so it stays hidden.)
    const hidden = s.state[i] === ORB.off || s.state[i] === ORB.leaving || (s.cut === 1 && i >= PILE_N);
    s.fade[i] = hidden && s.cut === 1 && i >= PILE_N ? 0 : Math.min(1, s.fade[i] + (hidden ? 0 : dt / 0.2));
  }
  for (let m = 0; m < MOODS.length; m++) {
    let out = false;
    for (let i = PILE_N; i < POOL; i++) if (s.mood[i] === m && (s.state[i] === ORB.held || s.state[i] === ORB.returning)) out = true;
    if (!out && s.slotScale[m] < 1) s.slotScale[m] = Math.min(1, s.slotScale[m] + dt / THROW.regrow);
  }

  // Lid: hinged on its left edge, open while an orb is in the air near the mouth.
  // The lid stays lifted a moment after the orb lands, as in the source.
  s.lidHold = anyAir ? LID.hold : Math.max(0, s.lidHold - dt);
  const want = s.lidHold > 0 && !s.reduced ? LID.open : 0;
  s.lidV += (-LID.stiffness * (s.lid - want) - LID.damping * s.lidV) * dt;
  s.lid += s.lidV * dt;
  // Row: springs back unless a finger holds it.
  if (!s.swiping) {
    s.rowV += (-UNDO.rowSpring.stiffness * s.rowX - UNDO.rowSpring.damping * s.rowV) * dt;
    s.rowX += s.rowV * dt;
  }
  s.clock += dt;
}

export function stepSim(s: Sim, g: Geometry, frameDt: number, autoPlay: boolean, uncapped = false) {
  'worklet';
  // Events from input() and from the steps both wait here until publish() hands them to JS and clears them.
  s.acc = uncapped ? s.acc + frameDt : Math.min(s.acc + frameDt, STEP * MAX_STEPS);
  while (s.acc >= STEP) {
    fixedStep(s, g, autoPlay);
    s.acc -= STEP;
  }
}

/** The solved launch from where a held orb is now, sampled as an aim arc: x, y pairs. */
export function aimArc(s: Sim, g: Geometry) {
  'worklet';
  const out: number[] = [];
  if (s.held < 0) return out;
  const i = s.held;
  const gr = THROW.gravity * g.s;
  const apex = Math.min(s.y[i], g.mouthY) - THROW.apex * g.s;
  const vy = -Math.sqrt(2 * gr * Math.max(1, s.y[i] - apex));
  const t = (-vy + Math.sqrt(vy * vy + 2 * gr * Math.max(0, g.mouthY + 40 * g.s - s.y[i]))) / gr;
  const vx = (g.cx - s.x[i]) / Math.max(0.2, t);
  for (let k = 1; k <= AIM.points; k++) {
    const tt = (t * k) / AIM.points;
    out.push(s.x[i] + vx * tt, s.y[i] + vy * tt + 0.5 * gr * tt * tt);
  }
  return out;
}
