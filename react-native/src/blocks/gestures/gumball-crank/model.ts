/**
 * The machine as one plain object, stepped once a frame on the UI thread: the handle and its ratchet, the pile of
 * gumballs (circles kept apart and inside the globe, each pulled back to its place in the pile), the capsule's way out
 * and the prize. The finger and the demo script call the same worklets.
 *
 * Stage coordinates: points on the 402 × 778 stage (theme.ts); angles in radians, 0 with the handle up, clockwise.
 *
 * Every helper is declared above the worklets that call it: worklets capture what exists when they are created.
 */
import { CRANK, COLLECT, DEMO, DISPENSE, EASE_IN_OUT, EASE_OUT, HAPTIC, SPRINGS, TIMING, type Spring } from './motion';
import { BALLS, LAYOUT, STAGE, WALLET } from './theme';

export const PHASE = { ready: 0, dispensing: 1, reward: 2, collecting: 3 } as const;

const TAU = Math.PI * 2;
const Q = TAU / CRANK.detents;

export type Model = {
  // Handle
  angle: number; // drawn angle, 0..TAU (negative while straining back)
  base: number; // the last detent the pawl holds, as an angle
  detent: number;
  goal: number; // where the finger (unwrapped) asks the handle to be
  wob: number; // a detent's kick, sprung back to 0
  wobV: number;
  grab: boolean;
  lastA: number; // the finger's last angle around the plate
  // Gumballs: position, velocity, radius, colour, place in the pile
  bx: number[];
  by: number[];
  bvx: number[];
  bvy: number[];
  br: number[];
  bc: number[];
  hx: number[];
  hy: number[];
  // Prize
  phase: number;
  t: number; // seconds into the phase
  done: boolean; // today's treat collected
  scrim: number;
  card: number; // the card's rise, sprung 0..1
  cardV: number;
  press: number; // Collect under a finger
  pressing: boolean;
  wallet: number; // the balance shown
  walletPrev: number; // the balance it is changing from, cross-faded out over walletMix
  walletMix: number;
  walletMixD: number; // how long that change takes (s)
  walletBump: number;
  walletBumpV: number;
  hint: number; // 0 turn, 1 busy, 2 done: shown as a cross-fade from hintFrom
  hintFrom: number;
  hintT: number;
  // Demo
  ghostX: number;
  ghostY: number;
  ghostO: number;
  ghostOT: number;
  ghostP: number;
  ghostPT: number;
  scriptDown: boolean;
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

function bezier(x1: number, y1: number, x2: number, y2: number, u: number): number {
  'worklet';
  const k = clamp(u, 0, 1);
  const cx = 3 * x1;
  const bx = 3 * (x2 - x1) - cx;
  const ax = 1 - cx - bx;
  const cy = 3 * y1;
  const by = 3 * (y2 - y1) - cy;
  const ay = 1 - cy - by;
  let t = k;
  for (let i = 0; i < 8; i++) {
    const x = ((ax * t + bx) * t + cx) * t - k;
    const dx = (3 * ax * t + 2 * bx) * t + cx;
    if (Math.abs(x) < 1e-5 || Math.abs(dx) < 1e-6) break;
    t -= x / dx;
  }
  t = clamp(t, 0, 1);
  return ((ay * t + by) * t + cy) * t;
}

export function easeOut(u: number): number {
  'worklet';
  return bezier(EASE_OUT[0], EASE_OUT[1], EASE_OUT[2], EASE_OUT[3], u);
}

export function easeInOut(u: number): number {
  'worklet';
  return bezier(EASE_IN_OUT[0], EASE_IN_OUT[1], EASE_IN_OUT[2], EASE_IN_OUT[3], u);
}

/** 0..1 across [a, b]. */
export function span(t: number, a: number, b: number): number {
  'worklet';
  return clamp((t - a) / (b - a), 0, 1);
}

export function springStep(x: number, v: number, target: number, dt: number, s: Spring): [number, number] {
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
  if (Math.abs(px - target) < 0.0005 && Math.abs(pv) < 0.005) return [target, 0];
  return [px, pv];
}

function toward(x: number, goal: number, dt: number, d: number): number {
  'worklet';
  if (d <= 0) return goal;
  const step = dt / d;
  return goal > x ? Math.min(goal, x + step) : Math.max(goal, x - step);
}

/** A seeded hash in [0, 1). */
function hash(n: number): number {
  'worklet';
  const s = Math.sin(n * 127.1 + 311.7) * 43758.5453;
  return s - Math.floor(s);
}

// ——— Geometry ———

export const GLOBE = { x: STAGE.w / 2, y: LAYOUT.globe.y, r: LAYOUT.globe.r - LAYOUT.globe.glass };
export const PLATE = { x: STAGE.w / 2, y: LAYOUT.plate.y };
/** The pile never rises above this line inside the globe, nor sinks below its floor. */
const FLOOR = LAYOUT.collar.y - 6;

/** The handle's grip at `a`. */
export function gripAt(a: number): [number, number] {
  'worklet';
  return [PLATE.x + LAYOUT.arm * Math.sin(a), PLATE.y - LAYOUT.arm * Math.cos(a)];
}

/** The pile, settled once under gravity: the balls' places. Deterministic, so every mount shows the same pile. */
function settlePile(): { x: number[]; y: number[]; r: number[]; c: number[] } {
  const n = 23;
  const x: number[] = [];
  const y: number[] = [];
  const r: number[] = [];
  const c: number[] = [];
  const vx: number[] = [];
  const vy: number[] = [];
  for (let i = 0; i < n; i++) {
    r.push(15.5 + hash(i + 1) * 4);
    c.push(Math.floor(hash(i * 7 + 3) * BALLS.length));
    x.push(GLOBE.x - 80 + (i % 7) * 27 + hash(i + 9) * 4);
    y.push(GLOBE.y + 10 + Math.floor(i / 7) * 26);
    vx.push(0);
    vy.push(0);
  }
  const h = 1 / 240;
  for (let k = 0; k < 1600; k++) {
    for (let i = 0; i < n; i++) {
      vy[i] += CRANK.gravity * h;
      vx[i] *= 0.985;
      vy[i] *= 0.985;
      x[i] += vx[i] * h;
      y[i] += vy[i] * h;
    }
    for (let pass = 0; pass < 3; pass++) {
      for (let i = 0; i < n; i++) {
        for (let j = i + 1; j < n; j++) {
          const dx = x[j] - x[i];
          const dy = y[j] - y[i];
          const d = Math.sqrt(dx * dx + dy * dy) || 1e-6;
          const min = r[i] + r[j];
          if (d >= min) continue;
          const push = (min - d) / d / 2;
          x[i] -= dx * push;
          y[i] -= dy * push;
          x[j] += dx * push;
          y[j] += dy * push;
        }
      }
      for (let i = 0; i < n; i++) {
        const dx = x[i] - GLOBE.x;
        const dy = y[i] - GLOBE.y;
        const d = Math.sqrt(dx * dx + dy * dy) || 1e-6;
        const lim = GLOBE.r - r[i];
        if (d > lim) {
          x[i] = GLOBE.x + (dx / d) * lim;
          y[i] = GLOBE.y + (dy / d) * lim;
          // The glass only stops the speed into it: a ball slides down the curve, it does not stick.
          const vn = (vx[i] * dx + vy[i] * dy) / d;
          if (vn > 0) {
            vx[i] -= (vn * dx) / d;
            vy[i] -= (vn * dy) / d;
          }
        }
        if (y[i] > FLOOR - r[i]) {
          y[i] = FLOOR - r[i];
          vy[i] = 0;
        }
      }
    }
  }
  return { x, y, r, c };
}

export function createModel(reduced: boolean): Model {
  const pile = settlePile();
  const n = pile.x.length;
  const zeros = () => new Array<number>(n).fill(0);
  return {
    angle: 0, base: 0, detent: 0, goal: 0, wob: 0, wobV: 0, grab: false, lastA: 0,
    bx: pile.x.slice(), by: pile.y.slice(), bvx: zeros(), bvy: zeros(), br: pile.r, bc: pile.c, hx: pile.x, hy: pile.y,
    phase: PHASE.ready, t: 0, done: false, scrim: 0, card: 0, cardV: 0, press: 0, pressing: false,
    wallet: WALLET.before, walletPrev: WALLET.before, walletMix: 1, walletMixD: 0.15, walletBump: 0, walletBumpV: 0, hint: 0, hintFrom: 0, hintT: 1,
    ghostX: 0, ghostY: 0, ghostO: 0, ghostOT: 0, ghostP: 0, ghostPT: 0, scriptDown: false,
    reduced, silent: false, events: [], version: 0, moving: true,
  };
}

export function cloneModel(m: Model): Model {
  'worklet';
  return {
    ...m,
    bx: m.bx.slice(), by: m.by.slice(), bvx: m.bvx.slice(), bvy: m.bvy.slice(), events: m.events.slice(),
  };
}

function emit(m: Model, h: number) {
  'worklet';
  if (!m.silent) m.events.push(h);
}

function setWallet(m: Model, v: number, d: number) {
  'worklet';
  m.walletPrev = m.wallet;
  m.wallet = v;
  m.walletMix = 0;
  m.walletMixD = d;
}

function setHint(m: Model, h: number) {
  'worklet';
  if (m.hint === h) return;
  m.hintFrom = m.hint;
  m.hint = h;
  m.hintT = 0;
}

// ——— Prize geometry (shared with the scene) ———

export const CARD = {
  w: LAYOUT.card.w,
  h: LAYOUT.card.h,
  x: (STAGE.w - LAYOUT.card.w) / 2,
  y: LAYOUT.tray.y - LAYOUT.card.bottomGap - LAYOUT.card.h,
};
export const BUTTON = { x: CARD.x + 24, y: CARD.y + CARD.h - 24 - 52, w: CARD.w - 48, h: 52 };
export const WALLET_COIN = { x: STAGE.w - LAYOUT.edge - 92 + 12 + 9, y: LAYOUT.wallet.y + LAYOUT.wallet.h / 2 };

/** The capsule at time t of the dispensing: centre, scale, rotation, how far the halves have parted, visibility. */
export function capsulePose(m: Model): { x: number; y: number; s: number; rot: number; split: number; a: number } {
  'worklet';
  const doorY = LAYOUT.door.top + LAYOUT.door.h / 2 + 4;
  if (m.phase === PHASE.ready) return { x: STAGE.w / 2, y: LAYOUT.tray.y, s: 1, rot: 0, split: 0, a: 0 };
  const t = m.phase === PHASE.dispensing ? m.t : 99;
  let fade = 1;
  if (m.phase === PHASE.collecting) fade = 1 - span(m.t, COLLECT.capsule[0], COLLECT.capsule[1]);
  if (m.reduced) {
    const a = span(t, DISPENSE.roll[0], DISPENSE.roll[1]) * fade;
    return { x: STAGE.w / 2, y: LAYOUT.tray.y, s: 1, rot: 0, split: span(t, DISPENSE.split[0], DISPENSE.split[1]), a };
  }
  if (t < DISPENSE.roll[0]) return { x: STAGE.w / 2, y: doorY, s: 0.6, rot: 0, split: 0, a: 0 };
  const u = span(t, DISPENSE.roll[0], DISPENSE.roll[1]);
  // It drops out of the door onto the table and rolls toward you: down the screen, growing, turning as it goes.
  const e = easeOut(u);
  const y = doorY + (LAYOUT.tray.y - doorY) * e;
  const s = 0.6 + 0.4 * e;
  let rot = -2.6 * (1 - e);
  // It rocks to a stop on its round side.
  const w = span(t, DISPENSE.wobble[0], DISPENSE.wobble[1] + 0.4);
  if (t >= DISPENSE.wobble[0]) rot = 0.16 * Math.sin(w * Math.PI * 3) * (1 - w);
  const split = span(t, DISPENSE.split[0], DISPENSE.split[1]);
  return { x: STAGE.w / 2, y, s, rot, split: easeOut(split), a: Math.min(1, u * 6) * fade };
}

// ——— Actions ———

/** The finger is down at (x, y) on the stage: the handle takes it if it is near the plate. */
export function grabAt(m: Model, x: number, y: number): boolean {
  'worklet';
  const dx = x - PLATE.x;
  const dy = y - PLATE.y;
  if (dx * dx + dy * dy > CRANK.grab * CRANK.grab) return false;
  if (m.phase !== PHASE.ready) return false;
  m.grab = true;
  m.lastA = Math.atan2(dx, -dy);
  m.goal = m.angle;
  m.moving = true;
  return true;
}

function kickPile(m: Model, k: number) {
  'worklet';
  if (m.reduced) return;
  for (let i = 0; i < m.bx.length; i++) {
    // The balls low in the globe sit on the dispensing wheel: they are shoved hardest.
    const low = clamp((m.hy[i] - GLOBE.y) / GLOBE.r, 0, 1);
    const kick = CRANK.kick * (0.3 + 0.7 * low);
    const a = hash(i * 13 + k * 7) * TAU;
    m.bvx[i] += Math.cos(a) * kick * 0.6;
    m.bvy[i] -= (0.5 + hash(i + k * 3) * 0.6) * kick;
  }
}

function dispense(m: Model) {
  'worklet';
  m.phase = PHASE.dispensing;
  m.t = 0;
  m.grab = false;
  setHint(m, 1);
  m.version++;
}

/** The finger moved to (x, y): the handle follows forward; backwards it only strains against the pawl. */
export function turnTo(m: Model, x: number, y: number) {
  'worklet';
  if (!m.grab) return;
  const a = Math.atan2(x - PLATE.x, -(y - PLATE.y));
  let d = a - m.lastA;
  if (d > Math.PI) d -= TAU;
  if (d < -Math.PI) d += TAU;
  m.lastA = a;
  m.goal += d;
  const back = m.goal - m.base;
  if (back < 0) {
    // Against the pawl: a little give, more and more resistance.
    const was = m.angle;
    m.angle = m.base - CRANK.back * (1 - Math.exp(back / CRANK.back));
    if (was >= m.base && m.angle < m.base - 0.05) emit(m, HAPTIC.strain);
  } else m.angle = Math.min(m.goal, TAU);
  while (m.detent < CRANK.detents && m.angle >= (m.detent + 1) * Q) {
    m.detent++;
    m.base = m.detent * Q;
    m.wobV += m.reduced ? 0 : 4;
    kickPile(m, m.detent);
    emit(m, HAPTIC.tick);
  }
  if (m.detent === CRANK.detents) dispense(m);
  m.moving = true;
}

/** Let go: the pawl catches the handle at the detent it last passed. */
export function release(m: Model) {
  'worklet';
  if (!m.grab) return;
  m.grab = false;
  m.goal = m.base;
  m.moving = true;
}

/** Collect pressed (down) and released (up, on the button). */
export function pressCollect(m: Model, down: boolean) {
  'worklet';
  if (m.phase !== PHASE.reward) return;
  m.pressing = down;
  m.moving = true;
}

export function collect(m: Model) {
  'worklet';
  if (m.phase !== PHASE.reward) return;
  m.pressing = false;
  m.phase = PHASE.collecting;
  m.t = 0;
  m.version++;
  emit(m, HAPTIC.collect);
}

/** Back to the start: the balance as it was and the hint to turn. The autoplay's last beat. */
export function resetDay(m: Model) {
  'worklet';
  m.done = false;
  setHint(m, 0);
  m.version++;
  m.moving = true;
}

/** Everything back to how the loop starts: no prize out, the handle up, the balance and the hint as they were. */
export function toRest(m: Model) {
  'worklet';
  m.grab = false;
  m.pressing = false;
  m.phase = PHASE.ready;
  m.t = 0;
  m.angle = 0;
  m.base = 0;
  m.goal = 0;
  m.detent = 0;
  if (m.wallet !== WALLET.before) setWallet(m, WALLET.before, TIMING.walletBack);
  resetDay(m);
}

// ——— Frame ———

function stepPile(m: Model, dt: number) {
  'worklet';
  const n = m.bx.length;
  const s: Spring = SPRINGS.pile;
  const w = (2 * Math.PI) / (s.duration / 1000);
  const k = w * w;
  const c = 2 * s.dampingRatio * w;
  let busy = false;
  const steps = Math.max(1, Math.ceil(dt / (1 / 240)));
  const h = dt / steps;
  for (let st = 0; st < steps; st++) {
    for (let i = 0; i < n; i++) {
      m.bvx[i] += (-k * (m.bx[i] - m.hx[i]) - c * m.bvx[i]) * h;
      m.bvy[i] += (-k * (m.by[i] - m.hy[i]) - c * m.bvy[i]) * h;
      m.bx[i] += m.bvx[i] * h;
      m.by[i] += m.bvy[i] * h;
    }
    for (let i = 0; i < n; i++) {
      for (let j = i + 1; j < n; j++) {
        const dx = m.bx[j] - m.bx[i];
        const dy = m.by[j] - m.by[i];
        const d = Math.sqrt(dx * dx + dy * dy) || 1e-6;
        const min = m.br[i] + m.br[j];
        if (d >= min) continue;
        const push = (min - d) / d / 2;
        m.bx[i] -= dx * push;
        m.by[i] -= dy * push;
        m.bx[j] += dx * push;
        m.by[j] += dy * push;
      }
    }
    for (let i = 0; i < n; i++) {
      const dx = m.bx[i] - GLOBE.x;
      const dy = m.by[i] - GLOBE.y;
      const d = Math.sqrt(dx * dx + dy * dy) || 1e-6;
      const lim = GLOBE.r - m.br[i];
      if (d > lim) {
        m.bx[i] = GLOBE.x + (dx / d) * lim;
        m.by[i] = GLOBE.y + (dy / d) * lim;
        // Off the glass: the speed into it is lost.
        const nx = dx / d;
        const ny = dy / d;
        const vn = m.bvx[i] * nx + m.bvy[i] * ny;
        if (vn > 0) {
          m.bvx[i] -= 1.4 * vn * nx;
          m.bvy[i] -= 1.4 * vn * ny;
        }
      }
      if (m.by[i] > FLOOR - m.br[i]) {
        m.by[i] = FLOOR - m.br[i];
        if (m.bvy[i] > 0) m.bvy[i] *= -0.3;
      }
    }
  }
  for (let i = 0; i < n; i++) {
    if (Math.abs(m.bx[i] - m.hx[i]) > 0.05 || Math.abs(m.by[i] - m.hy[i]) > 0.05 || Math.abs(m.bvx[i]) > 0.5 || Math.abs(m.bvy[i]) > 0.5) busy = true;
    else {
      m.bx[i] = m.hx[i];
      m.by[i] = m.hy[i];
      m.bvx[i] = 0;
      m.bvy[i] = 0;
    }
  }
  return busy;
}

export function step(m: Model, dt: number) {
  'worklet';
  const R = m.reduced;
  let busy = stepPile(m, dt);

  // The handle: held by the finger, or sprung to the detent the pawl holds; a click kicks it a hair past and back.
  if (!m.grab && m.phase === PHASE.ready && m.angle !== m.base) {
    m.angle += (m.base - m.angle) * Math.min(1, dt * (R ? 30 : 14));
    if (Math.abs(m.angle - m.base) < 0.002) m.angle = m.base;
    busy = true;
  }
  const [wb, wv] = springStep(m.wob, m.wobV, 0, dt, SPRINGS.detent);
  m.wob = wb;
  m.wobV = wv;
  if (wv !== 0) busy = true;

  // The prize.
  if (m.phase !== PHASE.ready) {
    m.t += dt;
    busy = true;
  }
  if (m.phase === PHASE.dispensing) {
    if (m.t >= DISPENSE.drop && m.t - dt < DISPENSE.drop) emit(m, HAPTIC.drop);
    if (m.t >= DISPENSE.split[0] && m.t - dt < DISPENSE.split[0]) emit(m, HAPTIC.open);
    if (m.t >= DISPENSE.ready) {
      m.phase = PHASE.reward;
      m.t = 0;
      // The handle has gone all the way round: it is at its start again.
      m.angle = 0;
      m.base = 0;
      m.goal = 0;
      m.detent = 0;
      m.version++;
    }
  } else if (m.phase === PHASE.collecting) {
    // Each coin lands in the wallet: the balance ticks up and the pill catches it.
    for (let i = 0; i < COLLECT.coins; i++) {
      const land = i * COLLECT.stagger + COLLECT.fly;
      if (m.t >= land && m.t - dt < land) {
        setWallet(m, Math.min(WALLET.before + WALLET.prize, m.wallet + WALLET.prize / COLLECT.coins), 0.15);
        m.walletBumpV += R ? 0 : 1.6;
      }
    }
    if (m.t >= COLLECT.done) {
      m.phase = PHASE.ready;
      m.t = 0;
      m.done = true;
      setHint(m, 2);
      m.version++;
    }
  }
  const cardGoal = m.phase === PHASE.reward || (m.phase === PHASE.dispensing && m.t >= DISPENSE.card) ? 1 : 0;
  if (m.phase !== PHASE.collecting) {
    const [c, cv] = springStep(m.card, m.cardV, cardGoal, dt, R ? SPRINGS.reduced : SPRINGS.card);
    m.card = c;
    m.cardV = cv;
    if (cv !== 0) busy = true;
  }
  const scrimGoal = cardGoal > 0 || m.phase === PHASE.collecting && m.t < COLLECT.card[1] ? 1 : 0;
  m.scrim = toward(m.scrim, scrimGoal, dt, scrimGoal ? TIMING.scrimIn : TIMING.scrimOut);
  m.press = toward(m.press, m.pressing ? 1 : 0, dt, TIMING.press);
  const [bb, bv] = springStep(m.walletBump, m.walletBumpV, 0, dt, SPRINGS.wallet);
  m.walletBump = bb;
  m.walletBumpV = bv;
  m.hintT = Math.min(1, m.hintT + dt / 0.25);
  m.walletMix = Math.min(1, m.walletMix + dt / m.walletMixD);
  m.ghostO = toward(m.ghostO, m.ghostOT, dt, m.ghostOT ? TIMING.fingerIn : TIMING.fingerOut);
  m.ghostP = toward(m.ghostP, m.ghostPT, dt, TIMING.fingerIn);
  m.moving = busy || bv !== 0 || m.scrim !== scrimGoal || m.press !== (m.pressing ? 1 : 0) || m.hintT < 1 || m.walletMix < 1
    || m.ghostO !== m.ghostOT || m.ghostP !== m.ghostPT || m.grab;
}

// ——— Demo ———

/** The scripted finger from t0 to t1 seconds into the loop. */
export function script(m: Model, t0: number, t1: number) {
  'worklet';
  const cross = (at: number) => t0 < at && t1 >= at;
  const [d0, d1] = DEMO.turn;
  if (cross(DEMO.down)) {
    const [gx, gy] = gripAt(m.angle);
    m.ghostX = gx;
    m.ghostY = gy;
    m.ghostOT = 1;
    m.ghostPT = 1;
    grabAt(m, gx, gy);
    m.scriptDown = true;
  }
  if (m.scriptDown && t1 > d0 && t1 <= d1 + 0.05) {
    // A quarter turn at a time, each one eased, the way a hand works a stiff handle.
    const u = span(t1, d0, d1) * CRANK.detents;
    const q = Math.floor(Math.min(u, CRANK.detents - 0.0001));
    const a = (q + easeInOut(u - q)) * Q + 0.02;
    const [gx, gy] = gripAt(a);
    m.ghostX = gx;
    m.ghostY = gy;
    turnTo(m, gx, gy);
  }
  if (cross(DEMO.up)) {
    m.scriptDown = false;
    release(m);
    m.ghostOT = 0;
    m.ghostPT = 0;
  }
  const tapDown = DEMO.tap - 0.25;
  if (cross(tapDown - 0.2)) {
    m.ghostX = BUTTON.x + BUTTON.w * 0.6;
    m.ghostY = BUTTON.y + BUTTON.h / 2;
    m.ghostOT = 1;
  }
  if (cross(tapDown)) {
    m.ghostPT = 1;
    pressCollect(m, true);
  }
  if (cross(DEMO.tap)) {
    collect(m);
    m.ghostPT = 0;
    m.ghostOT = 0;
  }
  if (cross(DEMO.walletBack)) {
    // The demo's day starts over: the balance and the hint go back to how the loop began.
    setWallet(m, WALLET.before, TIMING.walletBack);
    resetDay(m);
  }
}
