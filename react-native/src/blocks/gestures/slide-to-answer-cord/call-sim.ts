/**
 * Fixed-step UI-thread simulation: a coiled cord as a mass-spring chain, the handset as a rigid body hanging from
 * its end, and the call's states. The finger and the autoplay's scripted finger share `input`.
 */
import { CORD, HANDSET, REPLICA, STAGE_WIDTH } from './theme';
import { CHAIN, DRAG, FLICK, HAPTIC, LOOP_MS, MAX_STEPS, PREROLL_LOOPS, REPLICA_CORD, REPLICA_SWING, RESUME_MS, RING, SCRIPT, STATE, STEP, TICK, TILT } from './motion';

export const PHASE = { ringing: 0, connected: 1, declined: 2, ended: 3 } as const;

export type Geometry = { w: number; h: number; s: number; anchorX: number; anchorY: number; restY: number; baseLength: number };

export function makeGeometry(w: number, h: number): Geometry {
  const s = w / STAGE_WIDTH;
  const anchorY = CORD.anchorY * s;
  const restY = h * CORD.restRatio;
  // The natural (unstretched) length; the handset's weight stretches it to reach restY.
  const baseLength = restY - anchorY - CHAIN.hangStretch * s;
  return { w, h, s, anchorX: w / 2 + CORD.replicaAnchorDx * s, anchorY, restY, baseLength };
}

export type Input =
  | { type: 'down'; x: number; y: number; script?: boolean }
  | { type: 'move'; x: number; y: number; vx: number; vy: number }
  | { type: 'up'; vx: number; vy: number }
  | { type: 'command'; action: 'answer' | 'decline' | 'end' };

export type Sim = {
  acc: number; clock: number;
  px: number[]; py: number[]; vx: number[]; vy: number[];
  th: number; tw: number; ax: number; ay: number;
  natLen: number; natTarget: number;
  phase: number; phaseT: number; callT: number;
  wConn: number; wOff: number;
  grab: boolean; scripted: boolean; lx: number; ly: number; downX: number; downY: number; fx: number; fy: number; fvx: number; fvy: number;
  armed: number; tickAt: number;
  demoOn: boolean; demoT: number; quiet: number; demoX: number; demoY: number;
  reduced: boolean; events: number[];
  wSrc: number; // shipped: how far the handset is on the source's swing (1 at rest, 0 in the finger or a call)
  look: number; // the same, eased both ways for what is drawn (the traced takes, the hue field), so nothing pops
};

export function initialSim(g: Geometry, reduced: boolean, autoPlay: boolean): Sim {
  'worklet';
  const n = CORD.links;
  const px: number[] = [];
  const py: number[] = [];
  const vx: number[] = [];
  const vy: number[] = [];
  for (let i = 0; i <= n; i++) {
    px.push(g.anchorX);
    py.push(g.anchorY + ((g.restY - g.anchorY) * i) / n);
    vx.push(0);
    vy.push(0);
  }
  return {
    acc: 0, clock: 0, px, py, vx, vy, th: 0, tw: 0, ax: 0, ay: 0,
    natLen: g.baseLength, natTarget: g.baseLength,
    phase: PHASE.ringing, phaseT: 0, callT: 0, wConn: 0, wOff: 0,
    grab: false, scripted: false, lx: 0, ly: 0, downX: 0, downY: 0, fx: 0, fy: 0, fvx: 0, fvy: 0,
    armed: 0, tickAt: 0,
    demoOn: autoPlay, demoT: 0, quiet: 0, demoX: 0, demoY: 0,
    reduced, events: [], wSrc: reduced ? 0 : 1, look: reduced ? 0 : 1,
  };
}

export function clamp(v: number, lo = 0, hi = 1) {
  'worklet';
  return v < lo ? lo : v > hi ? hi : v;
}

function rubber(over: number, limit: number) {
  'worklet';
  return limit * (1 - Math.exp(-over / limit));
}


/** Rotation that turns the handset's local frame so its centre of mass hangs along angle `th` (0 = straight down). */
export function handsetRotation(th: number) {
  'worklet';
  return Math.atan2(HANDSET.comX, HANDSET.comY) - th + HANDSET.restTilt;
}

/** World position of a local handset point. */
export function handsetPoint(s: Sim, g: Geometry, x: number, y: number, extra = 0) {
  'worklet';
  const r = handsetRotation(s.th) + extra;
  const c = Math.cos(r);
  const sn = Math.sin(r);
  const n = CORD.links;
  return { x: s.px[n] + (x * c - y * sn) * g.s, y: s.py[n] + (x * sn + y * c) * g.s };
}

function setPhase(s: Sim, g: Geometry, phase: number) {
  'worklet';
  s.phase = phase;
  s.phaseT = 0;
  if (phase === PHASE.connected) {
    s.natTarget = g.baseLength - STATE.pickUp * g.s;
    s.callT = 0;
  } else if (phase === PHASE.ringing) {
    s.natTarget = g.baseLength;
  } else {
    s.natTarget = g.baseLength + STATE.drop * g.s;
  }
  if (!s.scripted && phase !== PHASE.ringing) s.events.push(phase === PHASE.connected ? HAPTIC.answer : HAPTIC.decline);
}

/** Signed vertical drag (negative = lifted), rubber-banded beyond the reach. */
export function dragDistance(s: Sim, g: Geometry) {
  'worklet';
  const raw = s.fy - s.downY;
  const reach = DRAG.reach * g.s;
  const a = Math.abs(raw);
  return Math.sign(raw) * (a > reach ? reach + rubber(a - reach, reach * 0.4) : a);
}

export function input(s: Sim, g: Geometry, ev: Input) {
  'worklet';
  if (ev.type === 'command') {
    s.demoOn = false;
    s.quiet = 0;
    s.scripted = false;
    if (ev.action === 'answer' && s.phase === PHASE.ringing) setPhase(s, g, PHASE.connected);
    else if (ev.action === 'decline' && s.phase === PHASE.ringing) setPhase(s, g, PHASE.declined);
    else if (ev.action === 'end' && s.phase === PHASE.connected) setPhase(s, g, PHASE.ended);
    return;
  }
  if (ev.type === 'down') {
    s.scripted = ev.script === true;
    if (!s.scripted) {
      s.demoOn = false;
      s.quiet = 0;
    }
    // The grab point in the handset's local frame, so it hangs from the finger where it was touched.
    const n = CORD.links;
    const r = -handsetRotation(s.th);
    const dx = (ev.x - s.px[n]) / g.s;
    const dy = (ev.y - s.py[n]) / g.s;
    s.lx = dx * Math.cos(r) - dy * Math.sin(r);
    s.ly = dx * Math.sin(r) + dy * Math.cos(r);
    s.grab = true;
    s.downX = ev.x;
    s.downY = ev.y;
    s.fx = ev.x;
    s.fy = ev.y;
    s.fvx = 0;
    s.fvy = 0;
    s.armed = 0;
    if (!s.scripted) s.events.push(HAPTIC.grab);
    return;
  }
  if (ev.type === 'move') {
    if (!s.grab) return;
    s.fx = ev.x;
    s.fy = ev.y;
    s.fvx = ev.vx;
    s.fvy = ev.vy;
    return;
  }
  if (!s.grab) return;
  s.grab = false;
  const n = CORD.links;
  const lim = DRAG.releaseSpeed;
  s.vx[n] = clamp(ev.vx, -lim, lim);
  s.vy[n] = clamp(ev.vy, -lim, lim);
  const dy = dragDistance(s, g);
  // Velocity OR distance: a quick flick past FLICK.min also commits.
  const up = dy <= -DRAG.answer * g.s || (ev.vy < -FLICK.speed * g.s && dy < -FLICK.min * g.s);
  const down = dy >= DRAG.decline * g.s || (ev.vy > FLICK.speed * g.s && dy > FLICK.min * g.s);
  if (s.phase === PHASE.ringing && up) setPhase(s, g, PHASE.connected);
  else if (s.phase === PHASE.ringing && down) setPhase(s, g, PHASE.declined);
  else if (s.phase === PHASE.connected && down) setPhase(s, g, PHASE.ended);
  s.scripted = false;
}

function runScript(s: Sim, g: Geometry, t0: number, t1: number) {
  'worklet';
  if (REPLICA) return; // the source has no finger: it only swings
  for (let i = 0; i < SCRIPT.length; i++) {
    const seg = SCRIPT[i];
    const a = seg[0];
    const b = seg[1];
    if (seg[6] === 1 && t0 <= a && a < t1) {
      const p = handsetPoint(s, g, HANDSET.comX, HANDSET.comY);
      s.demoX = p.x;
      s.demoY = p.y;
      input(s, g, { type: 'down', x: p.x, y: p.y, script: true });
    }
    if (t1 > a && t0 < b && s.grab && s.scripted) {
      const u = clamp((Math.min(t1, b) - a) / (b - a));
      const e = (1 - Math.cos(Math.PI * u)) / 2;
      const x = s.demoX + (seg[2] + (seg[4] - seg[2]) * e) * g.s;
      const y = s.demoY + (seg[3] + (seg[5] - seg[3]) * e) * g.s;
      input(s, g, { type: 'move', x, y, vx: 0, vy: 0 });
    }
    if (seg[7] === 1 && t0 < b && b <= t1 && s.scripted) input(s, g, { type: 'up', vx: 0, vy: 0 });
  }
}

function series(c: readonly number[], t: number) {
  'worklet';
  const w = (2 * Math.PI * t) / REPLICA_SWING.period;
  let v = c[0];
  for (let k = 1; k <= 4; k++) v += c[2 * k - 1] * Math.cos(k * w) + c[2 * k] * Math.sin(k * w);
  return v;
}

/** Replica: puts the handset where the source's frame has it at time t (the cord's end and the handset's turn). */
/** Replica: the source cord's centre line x (px) at height y (px) and swing time t, from the measured frames. */
function sourceCordX(y: number, t: number) {
  'worklet';
  const C = REPLICA_CORD;
  const f = ((t / 0.25) % 16 + 16) % 16;
  const f0 = Math.floor(f);
  const f1 = (f0 + 1) % 16;
  const fu = f - f0;
  const last = C.y.length - 1;
  const yy = Math.max(C.y[0], Math.min(C.y[last], y));
  let j = 0;
  while (j < last - 1 && yy > C.y[j + 1]) j++;
  const u = (yy - C.y[j]) / (C.y[j + 1] - C.y[j]);
  const a = C.x[f0][j] + (C.x[f0][j + 1] - C.x[f0][j]) * u;
  const b = C.x[f1][j] + (C.x[f1][j + 1] - C.x[f1][j]) * u;
  return a + (b - a) * fu;
}

function pinToSource(s: Sim, g: Geometry, t: number, dt: number, w = 1) {
  'worklet';
  const n = CORD.links;
  const F = REPLICA_SWING.fit;
  const k = g.w / 1206; // screen px → pt
  const leanDeg = series(REPLICA_SWING.lean, t);
  const r = ((leanDeg - F.leanAt0) / F.leanPerRad) * (Math.PI / 180); // the handset's drawn rotation
  const cx = series(REPLICA_SWING.x, t) * k;
  const cy = series(REPLICA_SWING.y, t) * k;
  // The cord's end is the attach point; the traced handset is drawn back from it by `attach`.
  const lx = F.centroid[0] * k - REPLICA_SWING.attach[0] * g.s;
  const ly = F.centroid[1] * k - REPLICA_SWING.attach[1] * g.s;
  const ox = lx * Math.cos(r) - ly * Math.sin(r);
  const oy = lx * Math.sin(r) + ly * Math.cos(r);
  const nx = cx - ox;
  const ny = cy - oy;
  // w = 1 puts everything exactly where the source has it; less pulls the simulated cord and handset toward it.
  const ex = s.px[n] + (nx - s.px[n]) * w;
  const ey = s.py[n] + (ny - s.py[n]) * w;
  s.vx[n] = s.vx[n] * (1 - w) + ((ex - s.px[n]) / dt) * w;
  s.vy[n] = s.vy[n] * (1 - w) + ((ey - s.py[n]) / dt) * w;
  s.px[n] = ex;
  s.py[n] = ey;
  const thT = Math.atan2(HANDSET.comX, HANDSET.comY) + HANDSET.restTilt - r;
  s.th += (thT - s.th) * w;
  s.tw *= 1 - w;
  // The cord follows the source's centre line between the screen's measured heights, straight to the anchor above
  // them and eased into the cord's end below them.
  const C = REPLICA_CORD;
  const yTop = C.y[0] * k;
  const yBot = C.y[C.y.length - 1] * k;
  for (let i = 1; i < n; i++) {
    const y = g.anchorY + ((ey - g.anchorY) * i) / n;
    let x: number;
    if (y < yTop) {
      const xt = sourceCordX(C.y[0], t) * k;
      x = g.anchorX + ((xt - g.anchorX) * (y - g.anchorY)) / (yTop - g.anchorY);
    } else if (y > yBot) {
      const xb = sourceCordX(C.y[C.y.length - 1], t) * k;
      x = xb + ((ex - xb) * (y - yBot)) / Math.max(1, ey - yBot);
    } else {
      x = sourceCordX(y / k, t) * k;
    }
    s.vx[i] *= 1 - w;
    s.vy[i] *= 1 - w;
    s.px[i] += (x - s.px[i]) * w;
    s.py[i] += (y - s.py[i]) * w;
  }
}

function fixedStep(s: Sim, g: Geometry, autoPlay: boolean) {
  'worklet';
  const dt = STEP;
  const n = CORD.links;
  if (s.demoOn) {
    const t0 = s.demoT;
    let t1 = t0 + dt;
    runScript(s, g, t0, t1);
    if (t1 * 1000 >= LOOP_MS) t1 -= LOOP_MS / 1000;
    s.demoT = t1;
  } else if (autoPlay && !s.grab) {
    s.quiet += dt;
    if (s.quiet * 1000 >= RESUME_MS && (s.phase === PHASE.ringing || s.phase === PHASE.connected)) {
      s.demoOn = true;
      s.demoT = s.phase === PHASE.connected ? 5 : 0;
    }
  }

  // State timers and eased values.
  s.phaseT += dt;
  if (s.phase === PHASE.connected) s.callT += dt;
  if ((s.phase === PHASE.declined || s.phase === PHASE.ended) && s.phaseT >= STATE.resetAfter) setPhase(s, g, PHASE.ringing);
  s.natLen += (s.natTarget - s.natLen) * (1 - Math.exp(-STATE.lengthEase * dt));
  const k = s.reduced ? 1 : 1 - Math.exp(-STATE.colorEase * dt);
  s.wConn += ((s.phase === PHASE.connected ? 1 : 0) - s.wConn) * k;
  s.wOff += ((s.phase === PHASE.declined || s.phase === PHASE.ended ? 1 : 0) - s.wOff) * k;

  // Threshold ticks while dragging.
  if (s.grab) {
    const d = dragDistance(s, g);
    const want = d <= -DRAG.answer * g.s && s.phase === PHASE.ringing ? -1 : d >= DRAG.decline * g.s ? 1 : 0;
    if (want !== s.armed) {
      s.armed = want;
      if (want !== 0 && !s.scripted) s.events.push(HAPTIC.grab);
    }
    // A selection tick every TICK pt the coil stretches or bunches.
    const k = Math.floor(Math.abs(d) / (TICK * g.s));
    if (k > s.tickAt && !s.scripted) s.events.push(HAPTIC.tick);
    s.tickAt = k;
  } else {
    s.tickAt = 0;
  }

  // Mass-spring chain. Springs: K_total = M g / stretch, split over n links in series.
  const gs = CHAIN.gravity * g.s;
  const kLink = ((CHAIN.handsetMass * gs) / (CHAIN.hangStretch * g.s)) * n;
  const rest = s.natLen / n;
  const fx: number[] = [];
  const fy: number[] = [];
  for (let i = 0; i <= n; i++) {
    const m = i === n ? CHAIN.handsetMass : CHAIN.linkMass;
    fx.push(0);
    fy.push(gs * m);
  }
  for (let i = 0; i < n; i++) {
    const dx = s.px[i + 1] - s.px[i];
    const dy = s.py[i + 1] - s.py[i];
    const d = Math.sqrt(dx * dx + dy * dy) || 1e-6;
    // A coil resists compression far less than stretching.
    const stretch = d - rest;
    const f = kLink * (stretch > 0 ? stretch : stretch * 0.25);
    const ux = dx / d;
    const uy = dy / d;
    fx[i] += f * ux;
    fy[i] += f * uy;
    fx[i + 1] -= f * ux;
    fy[i + 1] -= f * uy;
  }
  // Bending stiffness: each inner particle is pulled toward the midpoint of its neighbours.
  for (let i = 1; i < n; i++) {
    fx[i] += CHAIN.straighten * CHAIN.linkMass * ((s.px[i - 1] + s.px[i + 1]) / 2 - s.px[i]);
    fy[i] += CHAIN.straighten * CHAIN.linkMass * ((s.py[i - 1] + s.py[i + 1]) / 2 - s.py[i]);
  }
  // The phone swings while it rings, driven at the source's 4 s period.
  if (s.phase === PHASE.ringing && !s.reduced) {
    fx[n] += CHAIN.idleDrive * g.s * Math.sin((2 * Math.PI * s.clock) / CHAIN.drivePeriod);
  }
  const prevX = s.px[n];
  const prevVX = s.vx[n];
  for (let i = 1; i <= n; i++) {
    if (i === n && s.grab) continue;
    const m = i === n ? CHAIN.handsetMass : CHAIN.linkMass;
    const damp = Math.exp(-(i === n ? CHAIN.handsetDamping : CHAIN.damping) * dt);
    s.vx[i] = (s.vx[i] + (fx[i] / m) * dt) * damp;
    s.vy[i] = (s.vy[i] + (fy[i] / m) * dt) * damp;
    s.px[i] += s.vx[i] * dt;
    s.py[i] += s.vy[i] * dt;
  }
  if (s.grab) {
    // The finger holds the handset at the grab point; its drag is rubber-banded vertically.
    const d = dragDistance(s, g);
    const r = handsetRotation(s.th);
    const ox = (s.lx * Math.cos(r) - s.ly * Math.sin(r)) * g.s;
    const oy = (s.lx * Math.sin(r) + s.ly * Math.cos(r)) * g.s;
    const nx = s.fx - ox;
    const ny = s.downY + d - oy;
    s.vx[n] = (nx - s.px[n]) / dt;
    s.vy[n] = (ny - s.py[n]) / dt;
    s.px[n] = nx;
    s.py[n] = ny;
  }

  if (REPLICA && !s.grab) {
    pinToSource(s, g, s.clock + dt, dt);
    s.ax = s.px[n] - prevX;
    s.clock += dt;
    return;
  }

  // Handset tilt follows the last link, lagging the attach point's sideways acceleration.
  const lx = s.px[n] - s.px[n - 1];
  const ly = s.py[n] - s.py[n - 1];
  const target = Math.atan2(lx, ly);
  const accel = (s.vx[n] - prevVX) / dt;
  const com = Math.hypot(HANDSET.comX, HANDSET.comY) * g.s;
  s.tw += (-TILT.stiffness * (s.th - target) - TILT.damping * s.tw - (accel / com) * 0.6) * dt;
  s.th += s.tw * dt;
  // Shipped: while it rings untouched the handset swings as the source films it (its measured swing), and a grab hands it
  // to the physics at once; after a call it eases back onto the source's swing.
  if (!REPLICA) {
    s.wSrc = s.phase === PHASE.ringing && !s.grab && !s.reduced ? s.wSrc + (1 - s.wSrc) * (1 - Math.exp(-dt / CHAIN.toSource)) : 0;
    if (s.wSrc > 1e-3) pinToSource(s, g, s.clock + dt, dt, s.wSrc);
    s.look += (s.wSrc - s.look) * (1 - Math.exp(-dt / CHAIN.lookEase));
  }
  s.ax = s.px[n] - prevX;
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

/** Runs the autoplay for a few loops before the first frame, so the swing is already in its steady loop. */
export function preroll(s: Sim, g: Geometry) {
  'worklet';
  const steps = Math.round((PREROLL_LOOPS * LOOP_MS) / 1000 / STEP);
  for (let i = 0; i < steps; i++) fixedStep(s, g, true);
  s.events = [];
}

/** Ringing buzz (radians) and the neon pulse's progress, from the time spent ringing. */
export function ringState(s: Sim) {
  'worklet';
  if (REPLICA) return { buzz: 0, pulse: -1 };
  if (s.phase !== PHASE.ringing || s.grab || s.reduced) return { buzz: 0, pulse: -1 };
  // On the clock, so every 12 s loop rings alike; offset so the swing's source poses (0–2 s of each 4) stay clear.
  const t = (((s.clock - RING.offset) % RING.every) + RING.every) % RING.every;
  let buzz = 0;
  for (let i = 0; i < RING.buzz.length; i++) {
    const u = (t - RING.buzz[i]) / RING.buzzLength;
    if (u >= 0 && u <= 1) buzz += Math.sin(Math.PI * u) * RING.buzzAngle * Math.sin(2 * Math.PI * RING.buzzHz * t);
  }
  return { buzz, pulse: t < RING.pulse ? t / RING.pulse : -1 };
}
