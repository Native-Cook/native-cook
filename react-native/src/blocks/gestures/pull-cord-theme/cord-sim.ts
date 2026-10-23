/**
 * One fixed-step simulation on the UI thread: a verlet cord on a spring switch, the hanging bulb's pendulum,
 * the theme reveal and the light inside the glass. A real finger and the autoplay's scripted finger both go
 * through `input`, so the demo shows exactly what a touch does.
 */
import { CORD, FIT, REPLICA_ART, STAGE_WIDTH } from './theme';
import {
  BULB_MOTION, FEEL, HAPTIC, IGNITE, LEVEL, LIGHT_REST_AT, LOOP_MS, MAX_STEPS, REPLICA_SWING_PHASE, RESUME_MS, REVEAL, ROPE, SCRIPT,
  STEP, SWITCH, TAP, TAP_PULL,
} from './motion';

export type Geometry = {
  w: number; h: number; s: number;
  pivotX: number; pivotY: number; socketTop: number;
  anchorX: number; anchorY: number; restY: number; length: number; seg: number;
};

export function makeGeometry(w: number, h: number): Geometry {
  const s = w / STAGE_WIDTH;
  // The bulb sits exactly where the accepted replica put it (the source framed by ref.py fit).
  const k = FIT.k * s;
  const socketTop = REPLICA_ART.socketTopSrc * k;
  const anchorY = CORD.top * s;
  const restY = h * CORD.restYRatio;
  const length = restY - anchorY;
  return {
    w, h, s, pivotX: REPLICA_ART.centreSrc * k + FIT.cropX * s, pivotY: socketTop - REPLICA_ART.wire * s, socketTop,
    anchorX: w * CORD.xRatio, anchorY, restY, length, seg: length / (CORD.points - 1),
  };
}

export type Input =
  | { type: 'down'; x: number; y: number; script?: boolean }
  | { type: 'move'; x: number; y: number; vx: number; vy: number }
  | { type: 'up'; vx: number; vy: number; cancelled?: boolean }
  | { type: 'toggle' };

export type Sim = {
  acc: number; clock: number;
  px: number[]; py: number[]; ox: number[]; oy: number[];
  e: number; ev: number; armed: boolean;
  grab: number; scripted: boolean; fx: number; fy: number; gdx: number; gdy: number; vx: number; vy: number;
  downX: number; downY: number; downT: number; moved: number;
  light: number; rv: number; rFrom: number; rTo: number; rT: number; rDur: number;
  level: number; ignT: number; commitT: number; tickAt: number;
  th: number; thv: number; grabAngle: number;
  demoOn: boolean; demoT: number; quiet: number;
  tapOn: boolean; tapT: number;
  reduced: boolean; events: number[];
  restX: number[]; restY: number[];
};

function solveRope(s: Sim, g: Geometry) {
  'worklet';
  const n = CORD.points;
  for (let k = 0; k < ROPE.passes; k++) {
    for (let i = 0; i < n - 1; i++) {
      const j = i + 1;
      const dx = s.px[j] - s.px[i];
      const dy = s.py[j] - s.py[i];
      const d = Math.sqrt(dx * dx + dy * dy) || 1e-6;
      if (d <= g.seg) continue; // a cord only resists stretching
      const wi = i === 0 ? 0 : 1;
      const wj = j === n - 1 ? (s.grab === 1 ? 0 : ROPE.beadWeight) : 1;
      const sum = wi + wj;
      if (sum === 0) continue;
      const corr = (d - g.seg) / d / sum;
      s.px[i] += dx * corr * wi;
      s.py[i] += dy * corr * wi;
      s.px[j] -= dx * corr * wj;
      s.py[j] -= dy * corr * wj;
    }
    // A little bending stiffness: two links apart never closer than 1.6 links, so the cord never kinks.
    for (let i = 1; i < n - 2; i++) {
      const j = i + 2;
      const dx = s.px[j] - s.px[i];
      const dy = s.py[j] - s.py[i];
      const d = Math.sqrt(dx * dx + dy * dy) || 1e-6;
      const min = 1.6 * g.seg;
      if (d >= min) continue;
      const wj = j === n - 1 ? (s.grab === 1 ? 0 : ROPE.beadWeight) : 1;
      const corr = ((d - min) / d / (1 + wj)) * 0.5;
      s.px[i] += dx * corr;
      s.py[i] += dy * corr;
      s.px[j] -= dx * corr * wj;
      s.py[j] -= dy * corr * wj;
    }
  }
}


/** Verlet: the anchor rides the switch, everything else falls; then the cord's constraints. */
function ropeStep(s: Sim, g: Geometry) {
  'worklet';
  const n = CORD.points;
  s.px[0] = g.anchorX;
  s.py[0] = g.anchorY + s.e;
  s.ox[0] = s.px[0];
  s.oy[0] = s.py[0];
  const gdt = ROPE.gravity * g.s * STEP * STEP;
  const last = s.grab === 1 ? n - 1 : n;
  for (let i = 1; i < last; i++) {
    const vx = (s.px[i] - s.ox[i]) * ROPE.damping;
    const vy = (s.py[i] - s.oy[i]) * ROPE.damping;
    s.ox[i] = s.px[i];
    s.oy[i] = s.py[i];
    s.px[i] += vx;
    s.py[i] += vy + gdt;
  }
  solveRope(s, g);
}

export function initialSim(g: Geometry, reduced: boolean, autoPlay: boolean): Sim {
  'worklet';
  const px: number[] = [];
  const py: number[] = [];
  for (let i = 0; i < CORD.points; i++) {
    px.push(g.anchorX);
    py.push(g.anchorY + g.seg * i);
  }
  const s: Sim = {
    acc: 0, clock: 0,
    px, py, ox: px.slice(), oy: py.slice(),
    e: 0, ev: 0, armed: true,
    grab: 0, scripted: false, fx: 0, fy: 0, gdx: 0, gdy: 0, vx: 0, vy: 0,
    downX: 0, downY: 0, downT: 0, moved: 0,
    light: 0, rv: 0, rFrom: 0, rTo: 0, rT: 1, rDur: 1,
    level: 0, ignT: -1, commitT: 99, tickAt: 0,
    th: 0, thv: 0, grabAngle: 0,
    demoOn: autoPlay, demoT: 0, quiet: 0,
    tapOn: false, tapT: 0,
    reduced, events: [], restX: [], restY: [],
  };
  // Let the cord settle under gravity once, and keep that pose as the rest the loop returns to.
  for (let i = 0; i < ROPE.settleSteps; i++) ropeStep(s, g);
  s.restX = s.px.slice();
  s.restY = s.py.slice();
  s.ox = s.px.slice();
  s.oy = s.py.slice();
  return s;
}

export function clamp(v: number, lo = 0, hi = 1) {
  'worklet';
  return v < lo ? lo : v > hi ? hi : v;
}

function rubber(over: number, limit: number) {
  'worklet';
  return limit * (1 - Math.exp(-over / limit));
}

function easeInOutSine(u: number) {
  'worklet';
  return (1 - Math.cos(Math.PI * u)) / 2;
}

export function revealEase(u: number, toLight: boolean) {
  'worklet';
  if (toLight) return 1 - Math.pow(1 - u, 5);
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}


/** Pivot to the glass centre. */
export function glassArm(g: Geometry) {
  'worklet';
  return g.socketTop - g.pivotY + REPLICA_ART.glassCenterSrc * FIT.k * g.s;
}

/** The bulb's angle about its pivot: the source's slow swing plus whatever a finger gave it. */
export function bulbAngle(s: Sim, g: Geometry) {
  'worklet';
  // The source's swing (the replica's amplitude, period and phase), plus whatever a finger gave it.
  const amp = s.reduced ? 0 : REPLICA_ART.swingSrc * FIT.k * g.s;
  return (amp / glassArm(g)) * Math.sin((2 * Math.PI * s.clock) / BULB_MOTION.period + REPLICA_SWING_PHASE) + s.th;
}

/** World position of the glass centre, where the light (and the reveal) comes from. */
export function glassCenter(s: Sim, g: Geometry) {
  'worklet';
  const a = bulbAngle(s, g);
  const r = glassArm(g);
  return { x: g.pivotX + Math.sin(a) * r, y: g.pivotY + Math.cos(a) * r };
}

function startReveal(s: Sim, to: number) {
  'worklet';
  s.rFrom = s.rv;
  s.rTo = to;
  s.rT = 0;
  const full = s.reduced ? REVEAL.reducedFade : to === 1 ? REVEAL.toLight : REVEAL.toDark;
  s.rDur = Math.max(0.05, full * Math.abs(to - s.rv));
}

function toggle(s: Sim) {
  'worklet';
  s.light = 1 - s.light;
  s.armed = false;
  // The scrub preview already showed part of the reveal: continue from it, no jump.
  if (!s.reduced) s.rv = s.light === 1 ? Math.max(s.rv, FEEL.preview) : Math.min(s.rv, 1 - FEEL.preview);
  startReveal(s, s.light);
  if (s.light === 0 && !s.reduced) s.ignT = 0;
  s.commitT = 0;
  // The switch's snap tugs the bulb into a swing (the cord hangs right of it).
  if (!s.reduced) s.thv += FEEL.kick;
  if (!s.scripted) s.events.push(HAPTIC.detent);
}

export function input(s: Sim, g: Geometry, ev: Input) {
  'worklet';
  if (ev.type === 'toggle') {
    s.demoOn = false;
    s.quiet = 0;
    s.tapOn = true;
    s.tapT = 0;
    return;
  }
  if (ev.type === 'down') {
    s.scripted = ev.script === true;
    if (!s.scripted) {
      s.demoOn = false;
      s.tapOn = false;
      s.quiet = 0;
    }
    const n = CORD.points - 1;
    const bx = s.px[n];
    const by = s.py[n] + (CORD.beadHeight * g.s) / 2;
    const hit = (CORD.hit * g.s) / 2 + 8;
    const c = glassCenter(s, g);
    if (Math.abs(ev.x - bx) < hit && Math.abs(ev.y - by) < hit + (CORD.beadHeight * g.s) / 2) {
      s.grab = 1;
      s.gdx = s.px[n] - ev.x;
      s.gdy = s.py[n] - ev.y;
      if (!s.scripted) s.events.push(HAPTIC.grab);
    } else if (Math.hypot(ev.x - c.x, ev.y - c.y) < REPLICA_ART.grabRadius * g.s) {
      s.grab = 2;
      s.grabAngle = Math.atan2(ev.x - g.pivotX, ev.y - g.pivotY) - bulbAngle(s, g);
    } else {
      s.grab = 0;
    }
    s.fx = ev.x + s.gdx;
    s.fy = ev.y + s.gdy;
    s.downX = ev.x;
    s.downY = ev.y;
    s.downT = s.clock;
    s.moved = 0;
    s.vx = 0;
    s.vy = 0;
    return;
  }
  if (ev.type === 'move') {
    if (s.grab === 0) return;
    s.moved = Math.max(s.moved, Math.hypot(ev.x - s.downX, ev.y - s.downY));
    s.fx = ev.x + s.gdx;
    s.fy = ev.y + s.gdy;
    s.vx = ev.vx;
    s.vy = ev.vy;
    return;
  }
  // up
  if (s.grab === 1) {
    const n = CORD.points - 1;
    const isTap = !s.scripted && !ev.cancelled && s.moved < TAP.slop && s.clock - s.downT < TAP.time;
    const lim = ROPE.releaseSpeed;
    const vx = clamp(ev.vx, -lim, lim);
    const vy = clamp(ev.vy, -lim, lim);
    s.ox[n] = s.px[n] - vx * STEP;
    s.oy[n] = s.py[n] - vy * STEP;
    s.grab = 0;
    // A fast downward flick commits even short of the detent.
    if (s.armed && vy > FEEL.flickSpeed * g.s && s.e > FEEL.flickMin * g.s) toggle(s);
    if (isTap) {
      s.tapOn = true;
      s.tapT = 0;
    }
  } else if (s.grab === 2) {
    const arm = glassArm(g);
    s.thv = clamp(ev.vx / arm, -BULB_MOTION.maxThrow, BULB_MOTION.maxThrow);
    s.grab = 0;
  }
  s.scripted = false;
}

/** Runs one scripted finger track; offsets are from the bead's rest position. */
function runScript(s: Sim, g: Geometry, track: readonly (readonly number[])[], t0: number, t1: number) {
  'worklet';
  const bx = g.anchorX;
  const by = g.restY;
  for (let i = 0; i < track.length; i++) {
    const seg = track[i];
    const a = seg[0];
    const b = seg[1];
    if (seg[6] === 1 && t0 <= a && a < t1) input(s, g, { type: 'down', x: bx + seg[2] * g.s, y: by + seg[3] * g.s, script: true });
    if (t1 > a && t0 < b) {
      const u = easeInOutSine(clamp((Math.min(t1, b) - a) / (b - a)));
      const x = bx + (seg[2] + (seg[4] - seg[2]) * u) * g.s;
      const y = by + (seg[3] + (seg[5] - seg[3]) * u) * g.s;
      if (s.scripted && s.grab !== 0) input(s, g, { type: 'move', x, y, vx: 0, vy: 0 });
    }
    if (seg[7] === 1 && t0 < b && b <= t1 && s.scripted) input(s, g, { type: 'up', vx: 0, vy: 0 });
  }
}


/** At the loop seam the rope has settled; snapping the last fraction of a point keeps the video loop exact. */
function snapRestIfStill(s: Sim, g: Geometry) {
  'worklet';
  let worst = Math.abs(s.e);
  for (let i = 0; i < CORD.points; i++) {
    worst = Math.max(worst, Math.abs(s.px[i] - s.restX[i]), Math.abs(s.py[i] - s.restY[i]));
  }
  if (worst > ROPE.snapRest * g.s || s.grab !== 0) return;
  for (let i = 0; i < CORD.points; i++) {
    s.px[i] = s.restX[i];
    s.py[i] = s.restY[i];
    s.ox[i] = s.px[i];
    s.oy[i] = s.py[i];
  }
  s.e = 0;
  s.ev = 0;
  s.th = 0;
  s.thv = 0;
  s.level = s.light;
}

function fixedStep(s: Sim, g: Geometry, autoPlay: boolean) {
  'worklet';
  const dt = STEP;
  const n = CORD.points;
  const loop = LOOP_MS / 1000;

  // Scripted fingers first, through the same kernel as a touch.
  if (s.demoOn) {
    const t0 = s.demoT;
    let t1 = t0 + dt;
    runScript(s, g, SCRIPT, t0, t1);
    if (t1 >= loop) {
      t1 -= loop;
      snapRestIfStill(s, g);
    }
    s.demoT = t1;
  } else if (s.tapOn) {
    const t0 = s.tapT;
    const t1 = t0 + dt;
    runScript(s, g, TAP_PULL, t0, t1);
    s.tapT = t1;
    if (t1 > TAP_PULL[TAP_PULL.length - 1][1] + dt) s.tapOn = false;
  } else if (autoPlay && s.grab === 0) {
    s.quiet += dt;
    if (s.quiet * 1000 >= RESUME_MS) {
      s.demoOn = true;
      s.demoT = s.light === 1 ? LIGHT_REST_AT : 0;
    }
  }

  // The bead follows the finger; the cord drags the switch down with a rubber band.
  const prevE = s.e;
  let pullTarget = 0;
  if (s.grab === 1) {
    let dx = s.fx - g.anchorX;
    const side = ROPE.sideReach * g.s;
    if (Math.abs(dx) > side) dx = Math.sign(dx) * (side + rubber(Math.abs(dx) - side, side * 0.5));
    let dy = s.fy - g.anchorY;
    const lift = g.length - ROPE.liftReach * g.s;
    if (dy < lift) dy = lift - rubber(lift - dy, 24 * g.s);
    const dist = Math.sqrt(dx * dx + dy * dy) || 1e-6;
    const over = dist - g.length;
    let reach = dist;
    if (over > 0) {
      // Free travel first, then the switch's spring stiffens into a rubber band.
      const lin = SWITCH.linear * g.s;
      pullTarget = over < lin ? over : lin + rubber(over - lin, (SWITCH.travel - SWITCH.linear) * g.s);
      reach = g.length + pullTarget;
    }
    s.ox[n - 1] = s.px[n - 1];
    s.oy[n - 1] = s.py[n - 1];
    s.px[n - 1] = g.anchorX + (dx / dist) * reach;
    s.py[n - 1] = g.anchorY + (dy / dist) * reach;
  }
  if (s.grab === 1 && pullTarget >= s.e) {
    s.e = pullTarget;
    s.ev = (s.e - prevE) / dt;
  } else {
    const c = s.reduced ? SWITCH.reducedDamping : SWITCH.damping;
    s.ev += (-SWITCH.stiffness * s.e - c * s.ev) * dt;
    s.e += s.ev * dt;
    if (s.e < pullTarget) {
      s.e = pullTarget;
      s.ev = 0;
    }
    if (s.e < 0) {
      if (s.ev < -260 && !s.scripted && s.grab === 0 && !s.demoOn && !s.tapOn) s.events.push(HAPTIC.home);
      s.e = 0;
      s.ev = 0;
    }
  }

  ropeStep(s, g);

  // The detent.
  if (s.e < SWITCH.rearm * g.s) s.armed = true;
  if (s.armed && s.e >= SWITCH.detent * g.s) toggle(s);
  // A tick every few points of pull while the finger holds the cord.
  if (s.grab === 1) {
    const k = Math.floor(s.e / (FEEL.tick * g.s));
    if (k !== s.tickAt) {
      if (k > s.tickAt && !s.scripted) s.events.push(HAPTIC.tick);
      s.tickAt = k;
    }
  } else s.tickAt = 0;
  s.commitT += dt;

  // Reveal, liquid light and ignition.
  if (s.rT < s.rDur) {
    s.rT = Math.min(s.rDur, s.rT + dt);
    const u = s.rT / s.rDur;
    const eased = s.reduced ? u : revealEase(u, s.rTo === 1);
    s.rv = s.rFrom + (s.rTo - s.rFrom) * eased;
  }
  const pull = clamp(s.e / (SWITCH.detent * g.s));
  const target = s.armed ? (s.light === 1 ? 1 - pull : pull) : s.light;
  const rate = s.armed ? LEVEL.follow : LEVEL.fill;
  s.level += (target - s.level) * (1 - Math.exp(-rate * dt));
  if (s.ignT >= 0) {
    s.ignT += dt;
    if (s.ignT > IGNITE.times[IGNITE.times.length - 1]) s.ignT = -1;
  }

  // The bulb: a damped pendulum on top of the idle swing; a finger can hold it.
  const w0 = (2 * Math.PI) / BULB_MOTION.period;
  if (s.grab === 2) {
    const want = Math.atan2(s.fx - s.gdx - g.pivotX, s.fy - s.gdy - g.pivotY) - s.grabAngle;
    const idle = bulbAngle(s, g) - s.th;
    let th = want - idle;
    const m = BULB_MOTION.maxAngle;
    if (Math.abs(th) > m) th = Math.sign(th) * (m + rubber(Math.abs(th) - m, m * 0.4));
    s.thv = (th - s.th) / dt;
    s.th = th;
  } else {
    s.thv += (-w0 * w0 * s.th - 2 * BULB_MOTION.damping * w0 * s.thv) * dt;
    s.th += s.thv * dt;
  }
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

export function igniteValue(s: Sim) {
  'worklet';
  if (s.ignT < 0) return 1;
  const ts = IGNITE.times;
  const vs = IGNITE.values;
  for (let i = 0; i < ts.length - 1; i++) {
    if (s.ignT <= ts[i + 1]) {
      const u = (s.ignT - ts[i]) / (ts[i + 1] - ts[i]);
      return vs[i] + (vs[i + 1] - vs[i]) * u;
    }
  }
  return 1;
}

/** Radius that covers the whole screen from the glass centre. */
export function revealReach(c: { x: number; y: number }, g: Geometry) {
  'worklet';
  const dx = Math.max(c.x, g.w - c.x);
  const dy = Math.max(c.y, g.h - c.y);
  return Math.sqrt(dx * dx + dy * dy) + REVEAL.margin * g.s;
}
