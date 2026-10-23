/**
 * The upgrade's simulation (fixed step on the UI thread): the knob's ratchet with coasting, the pile's jostle,
 * the dispense → reward → collect timeline and the confetti. A finger, the autoplay and the buttons share `input`.
 * Worklet helpers come before their callers.
 */
import { COINS, CONFETTI, CRANK, HAPTIC, KNOB, RESUME_MS, STEP, UP_LOOP_MS, UP_SCRIPT, UP_T } from './motion';
import { REPLICA_ART as A } from './theme';

export const PHASE = { ready: 0, dispensing: 1, reward: 2, collecting: 3 } as const;

export type Geo = { s: number; kx: number; ky: number; door: { x: number; y: number } };

export function makeGeo(width: number): Geo {
  const s = width / 402;
  const c = A.knob.size / 2;
  return { s, kx: (A.knob.x + c) * s, ky: (A.knob.y + c) * s, door: { x: A.exit.x * s, y: A.exit.y * s } };
}

export type Input =
  | { type: 'down'; x: number; y: number; script?: boolean }
  | { type: 'move'; x: number; y: number }
  | { type: 'up' }
  | { type: 'command'; action: 'turn' | 'collect' };

export type Crank = {
  acc: number; clock: number; phase: number; phaseT: number;
  angle: number; turned: number; detent: number; w: number; wob: number; wobV: number; back: number; strain: number; press: number;
  grab: boolean; scripted: boolean; prevA: number; lastT: number;
  jx: number; jxv: number; jy: number; jyv: number;
  coins: number; bump: number;
  px: number[]; py: number[]; pvx: number[]; pvy: number[]; prot: number[]; pcol: number[];
  demoOn: boolean; demoT: number; quiet: number; autoTurn: number;
  reduced: boolean; events: number[];
};

export function initialCrank(reduced: boolean, autoPlay: boolean): Crank {
  'worklet';
  return {
    acc: 0, clock: 0, phase: PHASE.ready, phaseT: 0,
    angle: 0, turned: 0, detent: 0, w: 0, wob: 0, wobV: 0, back: 0, strain: 0, press: 0,
    grab: false, scripted: false, prevA: 0, lastT: 0,
    jx: 0, jxv: 0, jy: 0, jyv: 0, coins: COINS.from, bump: 0,
    px: [], py: [], pvx: [], pvy: [], prot: [], pcol: [],
    demoOn: autoPlay, demoT: 0, quiet: 0, autoTurn: -1, reduced, events: [],
  };
}

function emit(c: Crank, e: number) {
  'worklet';
  if (!c.scripted && !c.demoOn && c.autoTurn < 0) c.events.push(e);
}

function setPhase(c: Crank, p: number) {
  'worklet';
  c.phase = p;
  c.phaseT = 0;
}

/** Seeded burst from the door: fruit-coloured bits, up and out. */
function burst(c: Crank, g: Geo) {
  'worklet';
  c.px = []; c.py = []; c.pvx = []; c.pvy = []; c.prot = []; c.pcol = [];
  for (let i = 0; i < CONFETTI.count; i++) {
    const r1 = Math.sin(i * 12.9898) * 43758.5453;
    const r2 = Math.sin(i * 78.233) * 12345.678;
    const u1 = r1 - Math.floor(r1);
    const u2 = r2 - Math.floor(r2);
    const a = -Math.PI / 2 + (u1 - 0.5) * CONFETTI.spread * 2;
    const v = CONFETTI.speed * (0.55 + 0.45 * u2) * g.s;
    c.px.push(g.door.x); c.py.push(g.door.y);
    c.pvx.push(Math.cos(a) * v); c.pvy.push(Math.sin(a) * v);
    c.prot.push(u2 * 6); c.pcol.push(i % 5);
  }
}

/** Advance the ratchet by a forward angle; detents every 90°, a full turn dispenses. */
function advance(c: Crank, d: number) {
  'worklet';
  c.angle += d;
  c.turned += d;
  const k = Math.floor(c.turned / CRANK.detent + 1e-6);
  if (k > c.detent) {
    c.detent = k;
    if (!c.reduced) {
      c.wobV += CRANK.overshoot.kick;
      // The pile jostles: alternate sideways, always a little up.
      c.jxv += (k % 2 === 0 ? 1 : -1) * 90;
      c.jyv -= 60;
    }
    emit(c, HAPTIC.detent);
  }
  if (c.turned >= CRANK.turn - 1e-6) {
    c.turned = 0;
    c.detent = 0;
    c.w = 0;
    c.grab = false;
    c.angle = Math.round(c.angle / CRANK.turn) * CRANK.turn; // whole turns: the loop seam stays exact
    setPhase(c, PHASE.dispensing);
    emit(c, HAPTIC.dispense);
  }
}

export function input(c: Crank, g: Geo, ev: Input) {
  'worklet';
  if (ev.type === 'command') {
    c.demoOn = false;
    c.quiet = 0;
    if (ev.action === 'turn' && c.phase === PHASE.ready) c.autoTurn = 0;
    if (ev.action === 'collect' && c.phase === PHASE.reward) {
      setPhase(c, PHASE.collecting);
      emit(c, HAPTIC.collect);
    }
    return;
  }
  if (ev.type === 'down') {
    c.scripted = ev.script === true;
    if (!c.scripted) {
      c.demoOn = false;
      c.autoTurn = -1;
      c.quiet = 0;
    }
    if (c.phase !== PHASE.ready) return;
    if (Math.hypot(ev.x - g.kx, ev.y - g.ky) < KNOB.grab * g.s) {
      c.grab = true;
      c.w = 0; // catching a coasting knob stops it
      c.prevA = Math.atan2(ev.y - g.ky, ev.x - g.kx);
      c.lastT = c.clock;
      emit(c, HAPTIC.grab);
    }
    return;
  }
  if (ev.type === 'move') {
    if (!c.grab) return;
    const a = Math.atan2(ev.y - g.ky, ev.x - g.kx);
    let d = a - c.prevA;
    if (d > Math.PI) d -= 2 * Math.PI;
    if (d < -Math.PI) d += 2 * Math.PI;
    c.prevA = a;
    const dt = Math.max(1 / 240, c.clock - c.lastT);
    c.lastT = c.clock;
    if (d > 0 && c.strain >= 0) {
      c.w = c.w * 0.6 + (d / dt) * 0.4; // angular velocity, smoothed, for the hand-off
      advance(c, d);
    } else if (d > 0) {
      c.strain = Math.min(0, c.strain + d);
      c.back = -KNOB.backMax * (1 - Math.exp(c.strain / KNOB.backMax));
    }
    else {
      const was = c.back;
      // Backwards strains with rubber-band resistance, never a hard stop.
      c.strain = Math.min(0, c.strain + d);
      c.back = -KNOB.backMax * (1 - Math.exp(c.strain / KNOB.backMax));
      if (was > -0.02 && c.back <= -0.02) emit(c, HAPTIC.ratchet);
    }
    return;
  }
  if (c.grab) {
    c.grab = false;
    c.w = Math.min(KNOB.maxSpin, Math.max(0, c.w)); // the knob keeps turning with the finger's velocity
    // A flick past half a turn commits: spin on fast enough to finish.
    if (c.turned > Math.PI && c.w > KNOB.commitSpin) c.w = Math.max(c.w, (CRANK.turn - c.turned) / KNOB.finish + KNOB.friction);
  }
  c.back = 0;
  c.strain = 0;
  c.scripted = false;
}

function runTurn(c: Crank, g: Geo, t0: number, t1: number, a: number) {
  'worklet';
  const b = a + (UP_SCRIPT.turn[1] - UP_SCRIPT.turn[0]);
  const at = (t: number) => {
    'worklet';
    const u = Math.min(1, Math.max(0, (t - a) / (b - a)));
    const e = (1 - Math.cos(Math.PI * u)) / 2;
    const ang = -Math.PI / 2 + UP_SCRIPT.angle * e;
    return { x: g.kx + Math.cos(ang) * UP_SCRIPT.radius * g.s, y: g.ky + Math.sin(ang) * UP_SCRIPT.radius * g.s };
  };
  if (t0 <= a && a < t1) {
    const p = at(a);
    input(c, g, { type: 'down', x: p.x, y: p.y, script: true });
  }
  if (t1 > a && t0 < b && c.grab) {
    const p = at(Math.min(t1, b));
    input(c, g, { type: 'move', x: p.x, y: p.y });
  }
  if (t0 < b && b <= t1) input(c, g, { type: 'up' });
}

function step(c: Crank, g: Geo, autoPlay: boolean) {
  'worklet';
  const dt = STEP;
  if (c.demoOn) {
    const t0 = c.demoT;
    let t1 = t0 + dt;
    runTurn(c, g, t0, t1, UP_SCRIPT.turn[0]);
    if (t0 <= UP_SCRIPT.collectAt && UP_SCRIPT.collectAt < t1) input(c, g, { type: 'command', action: 'collect' });
    c.demoOn = true; // the scripted collect must not stop the demo
    if (t1 * 1000 >= UP_LOOP_MS) {
      t1 -= UP_LOOP_MS / 1000;
      c.coins = COINS.from; // the loop starts from the same balance
    }
    c.demoT = t1;
  } else if (c.autoTurn >= 0) {
    runTurn(c, g, c.autoTurn, c.autoTurn + dt, 0);
    c.autoTurn += dt;
    if (c.autoTurn > UP_SCRIPT.turn[1] - UP_SCRIPT.turn[0] + 0.1) c.autoTurn = -1;
  } else if (autoPlay && !c.grab) {
    c.quiet += dt;
    if (c.quiet * 1000 >= RESUME_MS && c.phase === PHASE.ready && c.w === 0) {
      c.demoOn = true;
      c.demoT = 0;
    }
  }
  // Coasting knob: friction, and it can still finish the turn.
  if (!c.grab && c.w > 0 && c.phase === PHASE.ready) {
    advance(c, c.w * dt);
    c.w = Math.max(0, c.w - KNOB.friction * c.w * dt);
    if (c.w < KNOB.catchSpin) c.w = 0; // the pawl catches
  }
  c.wobV += (-CRANK.overshoot.stiffness * c.wob - CRANK.overshoot.damping * c.wobV) * dt;
  c.wob += c.wobV * dt;
  if (!c.grab) c.back *= Math.exp(-14 * dt);
  c.press += ((c.grab ? 1 : 0) - c.press) * (1 - Math.exp(-18 * dt));
  // Pile jostle springs.
  c.jxv += (-160 * c.jx - 9 * c.jxv) * dt;
  c.jx += c.jxv * dt;
  c.jyv += (-160 * c.jy - 9 * c.jyv) * dt;
  c.jy += c.jyv * dt;
  // Timeline.
  const before = c.phaseT;
  c.phaseT += dt;
  if (c.phase === PHASE.dispensing) {
    if (before < UP_T.confetti && c.phaseT >= UP_T.confetti && !c.reduced) burst(c, g);
    if (c.phaseT >= UP_T.done) {
      setPhase(c, PHASE.reward);
      c.coins -= COINS.add; // hidden under the scrim (see COINS)
      emit(c, HAPTIC.open);
    }
  } else if (c.phase === PHASE.collecting && c.phaseT >= UP_T.collect + UP_T.back) {
    setPhase(c, PHASE.ready);
  }
  if (c.phase === PHASE.collecting && before < UP_T.collect && c.phaseT >= UP_T.collect) {
    c.coins += COINS.add;
    c.bump = 1;
  }
  c.bump = Math.max(0, c.bump - dt * 3);
  for (let i = 0; i < c.px.length; i++) {
    c.pvy[i] += CONFETTI.gravity * g.s * dt;
    c.px[i] += c.pvx[i] * dt;
    c.py[i] += c.pvy[i] * dt;
    c.prot[i] += (i % 2 === 0 ? 1 : -1) * CONFETTI.spin * dt;
  }
  if (c.px.length && c.phase !== PHASE.dispensing && c.phaseT > 0.5) {
    c.px = []; c.py = []; c.pvx = []; c.pvy = []; c.prot = []; c.pcol = [];
  }
  c.clock += dt;
}

export function stepCrank(c: Crank, g: Geo, frameDt: number, autoPlay: boolean) {
  'worklet';
  c.acc = Math.min(c.acc + frameDt, STEP * 30);
  while (c.acc >= STEP) {
    step(c, g, autoPlay);
    c.acc -= STEP;
  }
}
