/**
 * One fixed-step simulation on the UI thread: a nickel ball chain hanging from the pull switch under a pendant, the
 * switch itself, the pendant's pendulum, the filament's heat and the theme front. A real finger and the autoplay's
 * scripted finger both go through `input`, so the demo shows exactly what a touch does.
 *
 * The chain is position-based dynamics: one verlet particle per ball, gravity, air damping, links that never
 * lengthen (and may shorten a little, the balls sliding on the wire), and a bend limit, so it hangs, swings and
 * buckles like the real thing. A finger can take the pull or any ball. The switch has 9 pt of travel: pulled past
 * 7 pt it clicks, the cam lets 2.5 pt back (the recoil) and a shiver runs up the chain; past the hard stop the
 * chain gives at most 3 pt more. Let go, and a spring draws the chain back in; it swings out and settles.
 *
 * Worklets: every helper is declared above the function that calls it.
 */
import { CHAIN, EXPOSE, LAMP, STAGE_WIDTH, SWITCH_BODY } from './theme';
import {
  EASE_IN_OUT, EASE_OUT, FILAMENT, FRONT, HAPTIC, LOOP_MS, MAX_STEPS, NIGHT_REST_AT, RESUME_MS, ROPE, SCRIPT, STEP, SWING,
  SWITCH, TAP_PULL,
} from './motion';

export const BALLS = CHAIN.links + 1; // particle 0 is the chain's end inside the switch

export type Geometry = {
  w: number; h: number; s: number;
  pivotX: number; pivotY: number; arm: number; // the cord's pivot (ceiling) and its length to the cap top
  seg: number; length: number; // ball pitch and the chain's length, in screen points
};

export function makeGeometry(w: number, h: number): Geometry {
  const s = w / STAGE_WIDTH;
  const arm = LAMP.cord * s;
  return {
    w, h, s, pivotX: LAMP.x * s, pivotY: LAMP.capTop * s - arm, arm,
    seg: CHAIN.pitch * s, length: CHAIN.links * CHAIN.pitch * s,
  };
}

export type Input =
  | { type: 'down'; x: number; y: number; script?: boolean }
  | { type: 'move'; x: number; y: number; vx: number; vy: number; script?: boolean }
  | { type: 'up'; vx: number; vy: number; cancelled?: boolean; script?: boolean }
  | { type: 'tap'; x: number; y: number }
  | { type: 'toggle' };

export type Sim = {
  acc: number; clock: number;
  px: number[]; py: number[]; ox: number[]; oy: number[];
  e: number; ev: number; armed: boolean; // switch travel, its speed, ready to click
  grab: number; gi: number; scripted: boolean; // 0 none, 1 chain (ball gi), 2 shade
  fx: number; fy: number; gdx: number; gdy: number;
  lean: number; pitch: number; // the links' length now: the chain gives a little at the end of the travel
  light: number; heat: number; adapt: number; onT: number; // adapt: the eye's exposure; onT: time since switched on
  front: number; fFrom: number; fTo: number; fT: number; fDur: number; fOn: boolean;
  th: number; thv: number; grabAngle: number;
  demoOn: boolean; demoT: number; quiet: number;
  tapOn: boolean; tapT: number; tapX: number; tapY: number; // the tap's tug starts where the pull is (offset from rest)
  reduced: boolean; events: number[];
  restX: number[]; restY: number[];
};

export function clamp(v: number, lo = 0, hi = 1) {
  'worklet';
  return v < lo ? lo : v > hi ? hi : v;
}

/** CSS cubic-bezier(x1, y1, x2, y2) at progress u. */
export function bezier(c: readonly number[], u: number) {
  'worklet';
  if (u <= 0) return 0;
  if (u >= 1) return 1;
  const cx = 3 * c[0];
  const bx = 3 * (c[2] - c[0]) - cx;
  const ax = 1 - cx - bx;
  const cy = 3 * c[1];
  const by = 3 * (c[3] - c[1]) - cy;
  const ay = 1 - cy - by;
  let t = u;
  for (let i = 0; i < 8; i++) {
    const x = ((ax * t + bx) * t + cx) * t - u;
    const dx = (3 * ax * t + 2 * bx) * t + cx;
    if (Math.abs(x) < 1e-5 || Math.abs(dx) < 1e-6) break;
    t = clamp(t - x / dx);
  }
  return ((ay * t + by) * t + cy) * t;
}

function rubber(over: number, limit: number) {
  'worklet';
  return limit * (1 - Math.exp(-over / limit));
}

function easeInOutSine(u: number) {
  'worklet';
  return (1 - Math.cos(Math.PI * u)) / 2;
}

/** A point of the pendant's local frame (origin at the cap top, y along the cord) in screen points. */
export function lampPoint(g: Geometry, angle: number, lx: number, ly: number) {
  'worklet';
  const c = Math.cos(angle);
  const sn = Math.sin(angle);
  const y = g.arm + ly * g.s;
  const x = lx * g.s;
  return { x: g.pivotX + x * c + y * sn, y: g.pivotY - x * sn + y * c };
}

/** The foot of the switch housing, where the chain leaves it with no travel. */
export function switchFoot(g: Geometry, angle: number) {
  'worklet';
  return lampPoint(g, angle, SWITCH_BODY.x, SWITCH_BODY.top + SWITCH_BODY.height);
}

function inverseMass(s: Sim, i: number) {
  'worklet';
  if (i === 0) return 0;
  if (s.grab === 1 && i === s.gi) return 0;
  return i === BALLS - 1 ? ROPE.pullWeight : 1;
}

/** Long-range attachment: no ball is farther from a fixed ball than the chain between them, so it never stretches. */
function tether(s: Sim, g: Geometry, from: number) {
  'worklet';
  const fx = s.px[from];
  const fy = s.py[from];
  for (let i = 0; i < BALLS; i++) {
    if (i === from || inverseMass(s, i) === 0) continue;
    const max = Math.abs(i - from) * s.pitch;
    const dx = s.px[i] - fx;
    const dy = s.py[i] - fy;
    const d = Math.sqrt(dx * dx + dy * dy);
    if (d <= max) continue;
    s.px[i] = fx + (dx / d) * max;
    s.py[i] = fy + (dy / d) * max;
  }
}

function solveChain(s: Sim, g: Geometry) {
  'worklet';
  const n = BALLS;
  const seg = s.pitch;
  const short = g.seg * ROPE.slack;
  const bend = g.seg * ROPE.bend;
  for (let k = 0; k < ROPE.passes; k++) {
    for (let i = 0; i < n - 1; i++) {
      const j = i + 1;
      const dx = s.px[j] - s.px[i];
      const dy = s.py[j] - s.py[i];
      const d = Math.sqrt(dx * dx + dy * dy) || 1e-6;
      let want = d;
      if (d > seg) want = seg;
      else if (d < short) want = short;
      else continue;
      const wi = inverseMass(s, i);
      const wj = inverseMass(s, j);
      const sum = wi + wj;
      if (sum === 0) continue;
      const corr = (d - want) / d / sum;
      s.px[i] += dx * corr * wi;
      s.py[i] += dy * corr * wi;
      s.px[j] -= dx * corr * wj;
      s.py[j] -= dy * corr * wj;
    }
    for (let i = 0; i < n - 2; i++) {
      const j = i + 2;
      const dx = s.px[j] - s.px[i];
      const dy = s.py[j] - s.py[i];
      const d = Math.sqrt(dx * dx + dy * dy) || 1e-6;
      if (d >= bend) continue;
      const wi = inverseMass(s, i);
      const wj = inverseMass(s, j);
      const sum = wi + wj;
      if (sum === 0) continue;
      const corr = ((d - bend) / d / sum) * 0.5;
      s.px[i] += dx * corr * wi;
      s.py[i] += dy * corr * wi;
      s.px[j] -= dx * corr * wj;
      s.py[j] -= dy * corr * wj;
    }
    tether(s, g, 0);
    if (s.grab === 1) tether(s, g, s.gi);
  }
}

/** Verlet: the chain's end rides the switch in the swinging housing, every free ball falls; then the constraints. */
function chainStep(s: Sim, g: Geometry) {
  'worklet';
  const foot = switchFoot(g, s.th);
  const ax = foot.x + Math.sin(s.th) * s.e;
  const ay = foot.y + Math.cos(s.th) * s.e;
  s.ox[0] = s.px[0];
  s.oy[0] = s.py[0];
  s.px[0] = ax;
  s.py[0] = ay;
  const gdt = ROPE.gravity * g.s * STEP * STEP;
  for (let i = 1; i < BALLS; i++) {
    if (inverseMass(s, i) === 0) continue;
    const vx = (s.px[i] - s.ox[i]) * ROPE.damping;
    const vy = (s.py[i] - s.oy[i]) * ROPE.damping;
    s.ox[i] = s.px[i];
    s.oy[i] = s.py[i];
    s.px[i] += vx;
    s.py[i] += vy + gdt;
  }
  solveChain(s, g);
}

export function initialSim(g: Geometry, reduced: boolean, autoPlay: boolean): Sim {
  'worklet';
  const foot = switchFoot(g, 0);
  const px: number[] = [];
  const py: number[] = [];
  for (let i = 0; i < BALLS; i++) {
    px.push(foot.x);
    py.push(foot.y + g.seg * i);
  }
  const s: Sim = {
    acc: 0, clock: 0,
    px, py, ox: px.slice(), oy: py.slice(),
    e: 0, ev: 0, armed: true,
    grab: 0, gi: BALLS - 1, scripted: false, fx: 0, fy: 0, gdx: 0, gdy: 0,
    lean: 0, pitch: g.seg,
    light: 1, heat: 1, adapt: 1, onT: 99,
    front: 1, fFrom: 1, fTo: 1, fT: 1, fDur: 1, fOn: true,
    th: 0, thv: 0, grabAngle: 0,
    demoOn: autoPlay, demoT: 0, quiet: 0,
    tapOn: false, tapT: 0, tapX: 0, tapY: 0,
    reduced, events: [], restX: [], restY: [],
  };
  // Let the chain settle once, and keep that pose as the rest the loop returns to.
  for (let i = 0; i < ROPE.settleSteps; i++) chainStep(s, g);
  s.restX = s.px.slice();
  s.restY = s.py.slice();
  s.ox = s.px.slice();
  s.oy = s.py.slice();
  return s;
}

function startFront(s: Sim, to: number) {
  'worklet';
  s.fFrom = s.front;
  s.fTo = to;
  s.fOn = to === 1;
  const span = Math.abs(to - s.front);
  if (s.reduced) {
    s.fDur = Math.max(0.05, FRONT.reduced * span);
    s.fT = 0;
  } else {
    s.fDur = Math.max(0.05, (to === 1 ? FRONT.on : FRONT.off) * span);
    s.fT = to === 1 ? -FRONT.onDelay : 0;
  }
}

/** The click: the lamp toggles, the cam gives, a shiver runs up the chain and the shade takes a small kick. */
function click(s: Sim, g: Geometry) {
  'worklet';
  s.light = 1 - s.light;
  s.armed = false;
  if (s.light === 1) s.onT = 0;
  startFront(s, s.light);
  if (!s.reduced) {
    s.thv += SWING.kick;
    // A sideways impulse, largest mid-chain, alternating ball by ball fades: the chain shivers, then hangs true.
    const n = BALLS - 1;
    for (let i = 1; i <= n; i++) {
      if (inverseMass(s, i) === 0) continue;
      const k = Math.sin((Math.PI * i) / n) * SWITCH.kick * g.s;
      s.ox[i] -= k;
      s.oy[i] += k * 0.3;
    }
  }
  // The autoplay clicks silently; a finger, a tap on the pull or the accessibility action clicks with one haptic.
  if (!s.scripted || s.tapOn) s.events.push(HAPTIC.click);
}

/** The teardrop's centre: half a pull below the last ball. */
export function pullCentre(s: Sim, g: Geometry) {
  'worklet';
  const n = BALLS - 1;
  return { x: s.px[n], y: s.py[n] + (CHAIN.pullHeight * g.s) / 2 };
}

/** The pull's centre at rest, where the scripted fingers' offsets start. */
function restCentre(s: Sim, g: Geometry) {
  'worklet';
  const n = BALLS - 1;
  return { x: s.restX[n], y: s.restY[n] + (CHAIN.pullHeight * g.s) / 2 };
}

/** The tap's tug (and the accessibility action's) takes the pull where it is, swinging or not, and ends taut below. */
function startTap(s: Sim, g: Geometry) {
  'worklet';
  const pc = pullCentre(s, g);
  const rc = restCentre(s, g);
  s.tapOn = true;
  s.tapT = 0;
  s.tapX = (pc.x - rc.x) / g.s;
  s.tapY = (pc.y - rc.y) / g.s;
}

export function input(s: Sim, g: Geometry, ev: Input) {
  'worklet';
  if (ev.type === 'toggle') {
    s.demoOn = false;
    s.quiet = 0;
    startTap(s, g);
    return;
  }
  // A tap (its own recognizer: a pan that never moves fails, so it cannot tell a tap) on the pull plays the tug.
  if (ev.type === 'tap') {
    const pc = pullCentre(s, g);
    const hit = (CHAIN.hit * g.s) / 2;
    if (Math.abs(ev.x - pc.x) < hit && Math.abs(ev.y - pc.y) < hit) {
      s.demoOn = false;
      s.quiet = 0;
      startTap(s, g);
    }
    return;
  }
  // Once the script holds the pull (a tap's tug), the finger that tapped has nothing to say: its late lift, which can
  // arrive a frame after the tug has started, must not let go of the scripted grab.
  if ((ev.type === 'move' || ev.type === 'up') && s.scripted && ev.script !== true) return;
  if (ev.type === 'down') {
    s.scripted = ev.script === true;
    if (!s.scripted) {
      s.demoOn = false;
      s.tapOn = false;
      s.quiet = 0;
    }
    const n = BALLS - 1;
    const pc = pullCentre(s, g);
    const hit = (CHAIN.hit * g.s) / 2;
    const bulb = lampPoint(g, s.th, 0, LAMP.bulbY);
    s.grab = 0;
    if (Math.abs(ev.x - pc.x) < hit && Math.abs(ev.y - pc.y) < hit) {
      s.grab = 1;
      s.gi = n;
    } else {
      // Any ball of the chain within half the touch target.
      let best = hit * 0.8;
      for (let i = 2; i < n; i++) {
        const d = Math.hypot(ev.x - s.px[i], ev.y - s.py[i]);
        if (d < best) {
          best = d;
          s.grab = 1;
          s.gi = i;
        }
      }
    }
    if (s.grab === 1) {
      s.gdx = s.px[s.gi] - ev.x;
      s.gdy = s.py[s.gi] - ev.y;
    } else if (Math.hypot(ev.x - bulb.x, ev.y - bulb.y) < SWING.grabRadius * g.s) {
      s.grab = 2;
      s.gdx = 0;
      s.gdy = 0;
      s.grabAngle = Math.atan2(ev.x - g.pivotX, ev.y - g.pivotY) - s.th;
    }
    s.fx = ev.x + s.gdx;
    s.fy = ev.y + s.gdy;
    return;
  }
  if (ev.type === 'move') {
    if (s.grab === 0) return;
    s.fx = ev.x + s.gdx;
    s.fy = ev.y + s.gdy;
    return;
  }
  // up
  if (s.grab === 1) {
    const i = s.gi;
    const lim = ROPE.releaseSpeed * g.s;
    const vx = clamp(ev.vx, -lim, lim);
    const vy = clamp(ev.vy, -lim, lim);
    s.grab = 0;
    // The finger's velocity goes to the ball it held: the chain swings out from there.
    s.ox[i] = s.px[i] - vx * STEP;
    s.oy[i] = s.py[i] - vy * STEP;
    if (s.armed && !ev.cancelled && vy > SWITCH.flickSpeed * g.s && s.e > SWITCH.flickMin * g.s) click(s, g);
  } else if (s.grab === 2) {
    s.thv = clamp(ev.vx / (g.arm + LAMP.bulbY * g.s), -SWING.maxThrow, SWING.maxThrow);
    s.grab = 0;
  }
  s.scripted = false;
}

/**
 * Runs one scripted finger track; offsets are from the pull's centre at rest. (fromX, fromY) replaces the first
 * segment's start: where a tap found the pull.
 */
function runScript(
  s: Sim, g: Geometry, track: readonly (readonly number[])[], t0: number, t1: number, fromX: number, fromY: number,
) {
  'worklet';
  const rc = restCentre(s, g);
  for (let i = 0; i < track.length; i++) {
    const seg = track[i];
    const a = seg[0];
    const b = seg[1];
    const sx = i === 0 ? fromX : seg[2];
    const sy = i === 0 ? fromY : seg[3];
    if (seg[6] === 1 && t0 <= a && a < t1) input(s, g, { type: 'down', x: rc.x + sx * g.s, y: rc.y + sy * g.s, script: true });
    if (t1 > a && t0 < b) {
      const u = easeInOutSine(clamp((Math.min(t1, b) - a) / (b - a)));
      const x = rc.x + (sx + (seg[4] - sx) * u) * g.s;
      const y = rc.y + (sy + (seg[5] - sy) * u) * g.s;
      if (s.scripted && s.grab !== 0) input(s, g, { type: 'move', x, y, vx: 0, vy: 0, script: true });
    }
    if (seg[7] === 1 && t0 < b && b <= t1 && s.scripted) input(s, g, { type: 'up', vx: 0, vy: 0, script: true });
  }
}

/** At the loop seam everything has settled; snapping the last fraction of a point keeps the video loop exact. */
function snapRestIfStill(s: Sim, g: Geometry) {
  'worklet';
  let worst = Math.abs(s.e);
  for (let i = 0; i < BALLS; i++) {
    worst = Math.max(worst, Math.abs(s.px[i] - s.restX[i]), Math.abs(s.py[i] - s.restY[i]));
  }
  if (worst > ROPE.snapRest * g.s || s.grab !== 0) return;
  for (let i = 0; i < BALLS; i++) {
    s.px[i] = s.restX[i];
    s.py[i] = s.restY[i];
    s.ox[i] = s.px[i];
    s.oy[i] = s.py[i];
  }
  s.e = 0;
  s.ev = 0;
  s.th = 0;
  s.thv = 0;
  s.lean = 0;
  s.heat = s.light;
  s.adapt = s.light;
  s.front = s.light;
  s.fT = s.fDur;
}

/** Reduce Motion: no swing; the chain hangs straight from wherever the switch is. */
function hangStraight(s: Sim, g: Geometry) {
  'worklet';
  const foot = switchFoot(g, 0);
  for (let i = 0; i < BALLS; i++) {
    s.px[i] = foot.x;
    s.py[i] = foot.y + s.e + g.seg * i;
    s.ox[i] = s.px[i];
    s.oy[i] = s.py[i];
  }
}

/** The finger on ball gi: where that ball goes, how far the switch travels, how much the chain leans. */
function follow(s: Sim, g: Geometry) {
  'worklet';
  const foot = switchFoot(g, s.th);
  const reach = s.gi * g.seg; // chain between the switch and the held ball
  let dx = s.fx - foot.x;
  let dy = s.fy - foot.y;
  // Lifting slackens the chain; past a point it rubber-bands.
  const top = reach * (1 - ROPE.lift);
  if (dy < top) dy = top - rubber(top - dy, 16 * g.s);
  const dist = Math.sqrt(dx * dx + dy * dy) || 1e-6;
  const over = dist - reach;
  let hold = 0;
  let give = 0;
  if (over > 0) {
    const travel = SWITCH.travel * g.s;
    hold = Math.min(over, travel);
    if (over > travel) give = rubber(over - travel, SWITCH.stretch * g.s);
    if (!s.armed) hold = Math.max(0, hold - SWITCH.give * g.s);
    s.lean = dx / dist;
  } else {
    s.lean = 0;
  }
  const r = over > 0 ? reach + hold + give : dist;
  // The give is shared by every link between the switch and the finger, not taken up by one.
  s.pitch = g.seg + give / Math.max(1, s.gi);
  const i = s.gi;
  s.ox[i] = s.px[i];
  s.oy[i] = s.py[i];
  s.px[i] = foot.x + (dx / dist) * r;
  s.py[i] = foot.y + (dy / dist) * r;
  return hold;
}

function fixedStep(s: Sim, g: Geometry, autoPlay: boolean) {
  'worklet';
  const dt = STEP;
  const loop = LOOP_MS / 1000;

  // Scripted fingers first, through the same kernel as a touch.
  if (s.demoOn) {
    const t0 = s.demoT;
    let t1 = t0 + dt;
    runScript(s, g, SCRIPT, t0, t1, SCRIPT[0][2], SCRIPT[0][3]);
    if (t1 >= loop) {
      t1 -= loop;
      snapRestIfStill(s, g);
    }
    s.demoT = t1;
  } else if (s.tapOn) {
    const t0 = s.tapT;
    const t1 = t0 + dt;
    runScript(s, g, TAP_PULL, t0, t1, s.tapX, s.tapY);
    s.tapT = t1;
    if (t1 > TAP_PULL[TAP_PULL.length - 1][1] + dt) s.tapOn = false;
  } else if (autoPlay && s.grab === 0) {
    s.quiet += dt;
    if (s.quiet * 1000 >= RESUME_MS) {
      s.demoOn = true;
      s.demoT = s.light === 0 ? NIGHT_REST_AT : 0;
    }
  }

  // The switch: the finger drives it while the chain is taut; otherwise its spring draws the chain back in.
  if (s.grab === 1) {
    const prev = s.e;
    s.e = follow(s, g);
    s.ev = (s.e - prev) / dt;
  } else {
    s.lean = 0;
    s.pitch = g.seg;
    const w = (2 * Math.PI) / SWITCH.spring.duration; // response → natural frequency: k = w²
    s.ev += (-w * w * s.e - 2 * SWITCH.spring.dampingRatio * w * s.ev) * dt;
    s.e += s.ev * dt;
    if (s.e < 0 || s.reduced) {
      s.e = 0;
      s.ev = 0;
    }
  }
  if (s.e < SWITCH.rearm * g.s) s.armed = true;
  if (s.armed && s.e >= SWITCH.detent * g.s) click(s, g);

  if (s.reduced && s.grab !== 1) hangStraight(s, g);
  else chainStep(s, g);

  // Filament heat (first order), and the theme front.
  const tau = s.reduced ? FILAMENT.reduced : s.light === 1 ? FILAMENT.warmUp : FILAMENT.coolDown;
  s.heat += (s.light - s.heat) * (1 - Math.exp(-dt / tau));
  s.adapt += (s.light - s.adapt) * (1 - Math.exp(-dt / (s.reduced ? FILAMENT.reduced : EXPOSE.adaptTime)));
  s.onT += dt;
  if (s.fT < s.fDur) {
    s.fT = Math.min(s.fDur, s.fT + dt);
    const u = clamp(s.fT / s.fDur);
    // On, and Reduce Motion's cross-fade, ease out; off is darkness moving across the page, ease-in-out.
    const eased = bezier(s.fOn || s.reduced ? EASE_OUT : EASE_IN_OUT, u);
    s.front = s.fFrom + (s.fTo - s.fFrom) * eased;
  }

  // The pendant: a damped pendulum; a taut chain pulled sideways leans on it, a finger can hold it.
  if (s.reduced) {
    s.th = 0;
    s.thv = 0;
  } else if (s.grab === 2) {
    const want = Math.atan2(s.fx - g.pivotX, s.fy - g.pivotY) - s.grabAngle;
    let th = want;
    const m = SWING.maxAngle;
    if (Math.abs(th) > m) th = Math.sign(th) * (m + rubber(Math.abs(th) - m, m * 0.4));
    s.thv = (th - s.th) / dt;
    s.th = th;
  } else {
    const w0 = (2 * Math.PI) / SWING.period;
    const rest = SWING.couple * s.lean;
    s.thv += (-w0 * w0 * (s.th - rest) - 2 * SWING.dampingRatio * w0 * s.thv) * dt;
    s.th += s.thv * dt;
  }
  s.clock += dt;
}

/** The filament's settling flicker just after it is switched on: a multiplier on its brightness. */
export function flicker(s: Sim) {
  'worklet';
  if (s.reduced || s.onT >= FILAMENT.flicker) return 1;
  const t = s.onT;
  const n = 0.5 + 0.5 * Math.sin(t * 97) * Math.sin(t * 41 + 1.3);
  return 1 - FILAMENT.flickerDepth * n * (1 - t / FILAMENT.flicker);
}

export function stepSim(s: Sim, g: Geometry, frameDt: number, autoPlay: boolean) {
  'worklet';
  // Events from input() and from the steps wait here until the component hands them to JS and clears them.
  s.acc = Math.min(s.acc + frameDt, STEP * MAX_STEPS);
  while (s.acc >= STEP) {
    fixedStep(s, g, autoPlay);
    s.acc -= STEP;
  }
}
