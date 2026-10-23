/**
 * Fixed-step UI-thread simulation: the knob's ratchet, a pile of gumballs (position-based circle collisions
 * with springs back to their settled slots) and the capsule's timeline. Finger, script and buttons share `input`.
 */
import { BALL, BALLS, MACHINE, STAGE_WIDTH } from './theme';
import { COLLECT, CRANK, DISPENSE, HAPTIC, LOOP_MS, MAX_STEPS, PILE, RESUME_MS, SCRIPT, STEP } from './motion';

export const PHASE = { ready: 0, dispensing: 1, reward: 2, collecting: 3 } as const;

export type Geometry = { w: number; h: number; s: number; oy: number; cx: number; knobX: number; knobY: number };

export function makeGeometry(w: number, h: number): Geometry {
  const s = w / STAGE_WIDTH;
  const oy = Math.max(0, h - 874 * s) * MACHINE.extraY;
  return { w, h, s, oy, cx: w / 2, knobX: w / 2, knobY: oy + MACHINE.plateY * s };
}

/** A machine y (points on the 874 pt layout) on this screen. */
export function my(g: Geometry, y: number) {
  'worklet';
  return g.oy + y * g.s;
}

export type Input =
  | { type: 'down'; x: number; y: number; script?: boolean }
  | { type: 'move'; x: number; y: number }
  | { type: 'up' }
  | { type: 'command'; action: 'turn' | 'collect'; script?: boolean };

export type Sim = {
  acc: number; clock: number;
  bx: number[]; by: number[]; bvx: number[]; bvy: number[]; br: number[]; hx: number[]; hy: number[];
  crank: number; turned: number; back: number; wob: number; wobV: number; detent: number; press: number;
  grab: boolean; scripted: boolean; prevA: number;
  phase: number; phaseT: number;
  bag: number; bagV: number;
  demoOn: boolean; demoT: number; quiet: number; autoTurn: number;
  reduced: boolean; events: number[];
};

function lcg(seed: number) {
  'worklet';
  return (seed * 1103515245 + 12345) % 2147483648;
}

function solvePile(s: Sim, g: Geometry, px: number[], py: number[]) {
  'worklet';
  const n = s.br.length;
  const cy = my(g, MACHINE.globeY);
  const rin = (MACHINE.globeR - MACHINE.glass) * g.s;
  const floor = my(g, MACHINE.collarY);
  for (let k = 0; k < PILE.passes; k++) {
    for (let i = 0; i < n; i++) {
      for (let j = i + 1; j < n; j++) {
        const dx = px[j] - px[i];
        const dy = py[j] - py[i];
        const d = Math.sqrt(dx * dx + dy * dy) || 1e-6;
        const min = s.br[i] + s.br[j];
        if (d >= min) continue;
        const c = (min - d) / d / 2;
        px[i] -= dx * c;
        py[i] -= dy * c;
        px[j] += dx * c;
        py[j] += dy * c;
      }
    }
    for (let i = 0; i < n; i++) {
      const dx = px[i] - g.cx;
      const dy = py[i] - cy;
      const d = Math.sqrt(dx * dx + dy * dy) || 1e-6;
      const lim = rin - s.br[i];
      if (d > lim) {
        px[i] = g.cx + (dx / d) * lim;
        py[i] = cy + (dy / d) * lim;
      }
      if (py[i] > floor - s.br[i]) py[i] = floor - s.br[i];
    }
  }
}

/** Steps the pile: springs to home (or gravity while settling), damping, then constraints on predicted positions. */
function stepPile(s: Sim, g: Geometry, settling: boolean) {
  'worklet';
  const dt = STEP;
  const n = s.br.length;
  const px: number[] = [];
  const py: number[] = [];
  const damp = Math.exp(-PILE.damping * dt);
  for (let i = 0; i < n; i++) {
    if (settling) s.bvy[i] += PILE.settleGravity * g.s * dt;
    else {
      s.bvx[i] += -PILE.home * (s.bx[i] - s.hx[i]) * dt;
      s.bvy[i] += -PILE.home * (s.by[i] - s.hy[i]) * dt;
    }
    s.bvx[i] *= damp;
    s.bvy[i] *= damp;
    px.push(s.bx[i] + s.bvx[i] * dt);
    py.push(s.by[i] + s.bvy[i] * dt);
  }
  solvePile(s, g, px, py);
  for (let i = 0; i < n; i++) {
    s.bvx[i] = (px[i] - s.bx[i]) / dt;
    s.bvy[i] = (py[i] - s.by[i]) / dt;
    s.bx[i] = px[i];
    s.by[i] = py[i];
  }
}

export function initialSim(g: Geometry, reduced: boolean, autoPlay: boolean): Sim {
  'worklet';
  const s: Sim = {
    acc: 0, clock: 0, bx: [], by: [], bvx: [], bvy: [], br: [], hx: [], hy: [],
    crank: 0, turned: 0, back: 0, wob: 0, wobV: 0, detent: 0, press: 0,
    grab: false, scripted: false, prevA: 0,
    phase: PHASE.ready, phaseT: 0, bag: 0, bagV: 0,
    demoOn: autoPlay, demoT: 0, quiet: 0, autoTurn: -1,
    reduced, events: [],
  };
  // Drop the balls from a seeded grid and let them settle; that pile is home for good.
  let seed = BALL.seed;
  const cy = my(g, MACHINE.globeY);
  for (let i = 0; i < BALL.count; i++) {
    seed = lcg(seed);
    const r = (BALL.rMin + (BALL.rMax - BALL.rMin) * (seed / 2147483648)) * g.s;
    const col = i % 5;
    const row = Math.floor(i / 5);
    s.br.push(r);
    s.bx.push(g.cx + (col - 2) * 46 * g.s + (row % 2) * 12 * g.s);
    s.by.push(cy - 90 * g.s + row * 44 * g.s);
    s.bvx.push(0);
    s.bvy.push(0);
  }
  for (let k = 0; k < PILE.settleSteps; k++) stepPile(s, g, true);
  for (let i = 0; i < BALL.count; i++) {
    s.hx.push(s.bx[i]);
    s.hy.push(s.by[i]);
    s.bvx[i] = 0;
    s.bvy[i] = 0;
  }
  return s;
}

export function ballColor(i: number) {
  'worklet';
  return BALLS[(i * 3) % BALLS.length];
}

export function clamp(v: number, lo = 0, hi = 1) {
  'worklet';
  return v < lo ? lo : v > hi ? hi : v;
}

function emit(s: Sim, e: number) {
  'worklet';
  if (!s.scripted && !s.demoOn && s.autoTurn < 0) s.events.push(e);
}

function setPhase(s: Sim, phase: number) {
  'worklet';
  s.phase = phase;
  s.phaseT = 0;
}

function kickPile(s: Sim, g: Geometry, dir: number) {
  'worklet';
  if (s.reduced) return;
  const cy = my(g, MACHINE.globeY);
  for (let i = 0; i < s.br.length; i++) {
    const low = s.by[i] > cy;
    const k = PILE.kick * g.s * (low ? 1 : PILE.tremble);
    // A swirl around the globe's centre plus a little lift, alternating direction each click.
    const dx = s.bx[i] - g.cx;
    const dy = s.by[i] - cy;
    const d = Math.sqrt(dx * dx + dy * dy) || 1;
    s.bvx[i] += (-dy / d) * k * dir;
    s.bvy[i] += (dx / d) * k * dir - k * 0.4;
  }
}

export function input(s: Sim, g: Geometry, ev: Input) {
  'worklet';
  if (ev.type === 'command') {
    if (!ev.script) {
      s.demoOn = false;
      s.quiet = 0;
    }
    if (ev.action === 'turn' && s.phase === PHASE.ready && !s.grab) s.autoTurn = 0;
    if (ev.action === 'collect' && s.phase === PHASE.reward) {
      setPhase(s, PHASE.collecting);
      if (!ev.script) s.events.push(HAPTIC.collect);
    }
    return;
  }
  if (ev.type === 'down') {
    s.scripted = ev.script === true;
    if (!s.scripted) {
      s.demoOn = false;
      s.autoTurn = -1;
      s.quiet = 0;
    }
    if (s.phase !== PHASE.ready) return;
    if (Math.hypot(ev.x - g.knobX, ev.y - g.knobY) < CRANK.grab * g.s) {
      s.grab = true;
      s.prevA = Math.atan2(ev.y - g.knobY, ev.x - g.knobX);
      s.back = 0;
      emit(s, HAPTIC.grab);
    }
    return;
  }
  if (ev.type === 'move') {
    if (!s.grab) return;
    const a = Math.atan2(ev.y - g.knobY, ev.x - g.knobX);
    let d = a - s.prevA;
    if (d > Math.PI) d -= 2 * Math.PI;
    if (d < -Math.PI) d += 2 * Math.PI;
    s.prevA = a;
    // Ratchet: forward turns the knob; backward only strains it a few degrees.
    if (d > 0 && s.back >= 0) {
      s.crank += d;
      s.turned += d;
    } else if (d > 0) {
      s.back = Math.min(0, s.back + d);
    } else {
      const was = s.back;
      s.back = Math.max(-CRANK.back * 3, s.back + d);
      if (was > -0.02 && s.back <= -0.02) emit(s, HAPTIC.ratchet);
    }
    const k = Math.floor(s.turned / CRANK.detent + 1e-6);
    if (k > s.detent) {
      s.detent = k;
      s.wobV += CRANK.overshoot.kick;
      kickPile(s, g, k % 2 === 0 ? 1 : -1);
      emit(s, HAPTIC.detent);
    }
    if (s.turned >= CRANK.turn - 1e-6) {
      s.turned = 0;
      s.detent = 0;
      // Whole turns only, so the knob's slot is level again and the loop seam is exact.
      s.crank = Math.round(s.crank / CRANK.turn) * CRANK.turn;
      s.grab = false;
      s.back = 0;
      setPhase(s, PHASE.dispensing);
      emit(s, HAPTIC.dispense);
    }
    return;
  }
  s.grab = false;
  s.back = 0;
  s.scripted = false;
}

function runTurn(s: Sim, g: Geometry, t0: number, t1: number, start: number) {
  'worklet';
  const a = SCRIPT.turn[0] - SCRIPT.turn[0] + start;
  const b = a + (SCRIPT.turn[1] - SCRIPT.turn[0]);
  const at = (t: number) => {
    'worklet';
    const u = clamp((t - a) / (b - a));
    const e = (1 - Math.cos(Math.PI * u)) / 2;
    const ang = -Math.PI / 2 + SCRIPT.turnAngle * e;
    return { x: g.knobX + Math.cos(ang) * SCRIPT.turnRadius * g.s, y: g.knobY + Math.sin(ang) * SCRIPT.turnRadius * g.s };
  };
  if (t0 <= a && a < t1) {
    const p = at(a);
    input(s, g, { type: 'down', x: p.x, y: p.y, script: true });
  }
  if (t1 > a && t0 < b && s.grab) {
    const p = at(Math.min(t1, b));
    input(s, g, { type: 'move', x: p.x, y: p.y });
  }
  if (t0 < b && b <= t1) input(s, g, { type: 'up' });
}

function fixedStep(s: Sim, g: Geometry, autoPlay: boolean) {
  'worklet';
  const dt = STEP;
  if (s.demoOn) {
    const t0 = s.demoT;
    let t1 = t0 + dt;
    runTurn(s, g, t0, t1, SCRIPT.turn[0]);
    if (t0 <= SCRIPT.collectAt && SCRIPT.collectAt < t1) input(s, g, { type: 'command', action: 'collect', script: true });
    if (t1 * 1000 >= LOOP_MS) {
      t1 -= LOOP_MS / 1000;
      // The pile has settled into its slots; snap the last fraction so the video loops exactly.
      for (let i = 0; i < s.br.length; i++) {
        s.bx[i] = s.hx[i];
        s.by[i] = s.hy[i];
        s.bvx[i] = 0;
        s.bvy[i] = 0;
      }
      s.crank = 0;
    }
    s.demoT = t1;
  } else if (s.autoTurn >= 0) {
    runTurn(s, g, s.autoTurn, s.autoTurn + dt, 0);
    s.autoTurn += dt;
    if (s.autoTurn > SCRIPT.turn[1] - SCRIPT.turn[0] + 0.1) s.autoTurn = -1;
  } else if (autoPlay && !s.grab) {
    s.quiet += dt;
    if (s.quiet * 1000 >= RESUME_MS && (s.phase === PHASE.ready || s.phase === PHASE.reward)) {
      s.demoOn = true;
      s.demoT = s.phase === PHASE.reward ? 5 : 0;
    }
  }

  stepPile(s, g, false);

  // Knob wobble after each click, and the press scale.
  s.wobV += (-CRANK.overshoot.stiffness * s.wob - CRANK.overshoot.damping * s.wobV) * dt;
  s.wob += s.wobV * dt;
  if (s.reduced) {
    s.wob = 0;
    s.wobV = 0;
  }
  s.press += ((s.grab ? 1 : 0) - s.press) * (1 - Math.exp(-18 * dt));
  if (!s.grab) s.back *= Math.exp(-14 * dt);

  // Timelines.
  s.phaseT += dt;
  if (s.phase === PHASE.dispensing && s.phaseT >= DISPENSE.done) {
    setPhase(s, PHASE.reward);
    emit(s, HAPTIC.open);
  }
  if (s.phase === PHASE.collecting && s.phaseT >= COLLECT.duration) {
    setPhase(s, PHASE.ready);
    s.bagV += COLLECT.bagBounce.kick * 60;
  }
  s.bagV += (-COLLECT.bagBounce.stiffness * s.bag - COLLECT.bagBounce.damping * s.bagV) * dt;
  s.bag += s.bagV * dt;
  s.clock += dt;
}

export function stepSim(s: Sim, g: Geometry, frameDt: number, autoPlay: boolean) {
  'worklet';
  // Events from input() and from the steps both wait here until publish() hands them to JS and clears them.
  s.acc = Math.min(s.acc + frameDt, STEP * MAX_STEPS);
  while (s.acc >= STEP) {
    fixedStep(s, g, autoPlay);
    s.acc -= STEP;
  }
}

/** Capsule pose from the phase clock: position, scale, spin, the shell's split, and the card's rise. */
export function capsulePose(s: Sim, g: Geometry) {
  'worklet';
  const doorTop = my(g, MACHINE.doorTop);
  const doorBottom = my(g, MACHINE.doorTop + MACHINE.doorHeight);
  const r = 20 * g.s;
  const rest = { x: g.cx, y: my(g, 726), scale: 1.7, spin: 0, split: 1, card: 1, visible: 1, inDoor: 0 };
  if (s.phase === PHASE.reward) return rest;
  if (s.phase === PHASE.collecting) return { ...rest, card: 1, split: 1 };
  if (s.phase !== PHASE.dispensing) return { ...rest, visible: 0, card: 0, split: 0 };
  const t = s.phaseT;
  if (s.reduced) {
    const f = clamp(t / 0.2);
    return { ...rest, visible: f, split: t > DISPENSE.split ? 1 : 0, card: clamp((t - DISPENSE.split) / 0.2) };
  }
  const floorY = doorBottom - r - 4 * g.s;
  if (t < DISPENSE.drop + DISPENSE.bounce) {
    // Falls into the door window with gravity, then one small bounce.
    let y: number;
    if (t < DISPENSE.drop) {
      const u = t / DISPENSE.drop;
      y = doorTop - r + (floorY - doorTop + r) * u * u;
    } else {
      const u = (t - DISPENSE.drop) / DISPENSE.bounce;
      y = floorY - 10 * g.s * Math.sin(Math.PI * u);
    }
    return { x: g.cx, y, scale: 1, spin: 0, split: 0, card: 0, visible: 1, inDoor: 1 };
  }
  if (t < DISPENSE.rollStart) return { x: g.cx, y: floorY, scale: 1, spin: 0, split: 0, card: 0, visible: 1, inDoor: 1 };
  const u = clamp((t - DISPENSE.rollStart) / DISPENSE.roll);
  const e = 1 - Math.pow(1 - u, 3);
  const y = floorY + (rest.y - floorY) * e;
  const scale = 1 + (rest.scale - 1) * e;
  // Rolling toward the viewer: it turns by the distance over its radius.
  // Rolling toward the viewer: exactly one turn, so the white half is on top when it splits.
  const spin = 2 * Math.PI * e;
  let wob = 0;
  if (t > DISPENSE.wobbleStart && t < DISPENSE.split) {
    const w = (t - DISPENSE.wobbleStart) / DISPENSE.wobble;
    wob = 0.12 * Math.sin(w * Math.PI * 4) * (1 - w);
  }
  const split = clamp((t - DISPENSE.split) / DISPENSE.splitLength);
  const card = clamp((t - DISPENSE.split - 0.08) / DISPENSE.cardRise);
  return { x: g.cx, y, scale, spin: spin + wob, split, card, visible: 1, inDoor: u < 0.25 ? 1 : 0 };
}
