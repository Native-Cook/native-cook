// The deck as one fixed-step simulation, shared by the fingers and the autoplay script.
// Pure worklets on plain objects, so the same code runs on the UI thread and in the dry run that times the loop.
import { CHASSIS, HAPTIC, KEY, LIGHT, LOOP_MS, PAGER, SCRIPT, SCRUB, STEP, TRANSPORT } from './motion';
import { LAYOUT } from './theme';

export const KEYS = 5;
export const SLOTS = 3;
const SECTOR = Math.PI / 3; // a hub has six teeth: it looks the same every 60°

export type Sim = {
  // Pager, in cassette widths (one screen width each).
  p: number;
  pv: number;
  target: number;
  dragging: boolean;
  dragStart: number;
  // Keys.
  d: number[];
  dv: number[];
  held: number[]; // 1 while a finger (or the script) holds the key
  armed: number[]; // 1 while the key can click at the bottom
  lift: number[]; // 1 when the finger has left but the stroke has not reached the bottom yet (a quick tap still clicks)
  latched: boolean;
  // Transport and reels.
  v: number;
  w: number[];
  ang: number[];
  k: number[];
  prog: number[];
  // Lights.
  led: number;
  lit: number[];
  // Scrubbing a reel by hand.
  scrubbing: boolean;
  scrubV: number;
  tooth: number;
  // Chassis dip and cassette rattle (pt).
  dip: number;
  dipV: number;
  rattle: number;
  rattleV: number;
  clock: number;
  // The slot that last seated in the tray (−1 while none is), and when the last scrub tick fired.
  seated: number;
  tickAt: number;
  // Reduce Motion cross-fade after a track change.
  fade: number;
  reduced: boolean;
  // Clock.
  acc: number;
  step: number; // steps into the autoplay loop
  mode: number; // 0 autoplay, 1 fingers, 2 returning to rest before the script resumes
  idle: number; // seconds since the last touch
  events: number[];
};

const PROG0 = [0.32, 0.58, 0.18];

export function makeSim(): Sim {
  'worklet';
  return {
    p: 0, pv: 0, target: 0, dragging: false, dragStart: 0,
    d: [0, 0, 0, 0, 0], dv: [0, 0, 0, 0, 0], held: [0, 0, 0, 0, 0], armed: [1, 1, 1, 1, 1], lift: [0, 0, 0, 0, 0], latched: false,
    v: 0, w: [0, 0, 0], ang: [0, 0, 0, 0, 0, 0], k: [1, 1, 1, 1, 1, 1], prog: [PROG0[0], PROG0[1], PROG0[2]],
    led: 0, lit: [0, 0, 0, 0, 0], fade: 1, reduced: false,
    scrubbing: false, scrubV: 0, tooth: 0, dip: 0, dipV: 0, rattle: 0, rattleV: 0, clock: 0, seated: 0, tickAt: -1,
    acc: 0, step: 0, mode: 0, idle: 0, events: [],
  };
}

// Puts every moving part back at rest, keeping the reels' angles (the loop's seam).
export function resetToRest(s: Sim) {
  'worklet';
  s.p = 0; s.pv = 0; s.target = 0; s.dragging = false; s.dragStart = 0;
  for (let i = 0; i < KEYS; i++) { s.d[i] = 0; s.dv[i] = 0; s.held[i] = 0; s.armed[i] = 1; s.lift[i] = 0; s.lit[i] = 0; }
  s.latched = false; s.v = 0; s.led = 0; s.fade = 1;
  s.scrubbing = false; s.scrubV = 0; s.dip = 0; s.dipV = 0; s.rattle = 0; s.rattleV = 0; s.clock = 0; s.seated = 0; s.tickAt = -1;
  for (let j = 0; j < SLOTS; j++) { s.w[j] = 0; s.prog[j] = PROG0[j]; }
  for (let i = 0; i < SLOTS * 2; i++) s.ang[i] = s.ang[i] % (Math.PI * 2);
}

export function tapeRadius(prog: number) {
  'worklet';
  const r = LAYOUT.tapePack;
  return r.min + (r.max - r.min) * prog;
}

// The slot whose cassette sits in the deck, or −1 while none is close enough.
export function loadedSlot(s: Sim) {
  'worklet';
  const n = Math.round(s.p);
  if (Math.abs(s.p - n) > PAGER.loadedWithin) return -1;
  return ((n % SLOTS) + SLOTS) % SLOTS;
}

// x of a slot in cassette widths, wrapped into [−1, 2) so three cassettes make an endless strip.
export function slotX(j: number, p: number) {
  'worklet';
  const x = (((j - p + 1) % SLOTS) + SLOTS) % SLOTS;
  return x - 1;
}

function act(s: Sim, i: number) {
  'worklet';
  if (i === 0 && !s.latched) { s.latched = true; s.events.push(HAPTIC.latch); return; }
  if (i === 1 && s.latched) { s.latched = false; s.events.push(HAPTIC.release); }
  if (i === 2) {
    s.target = Math.round(s.target) + 1;
    if (s.reduced) { s.p = s.target; s.pv = 0; s.fade = 0; }
  }
  s.events.push(HAPTIC.click);
}

function relax(x: number, to: number, tau: number, dt: number) {
  'worklet';
  return x + (to - x) * (1 - Math.exp(-dt / tau));
}

export function stepSim(s: Sim, dt: number, live: boolean) {
  'worklet';
  // Keys.
  for (let i = 0; i < KEYS; i++) {
    if (s.lift[i] && s.armed[i] === 0) { s.held[i] = 0; s.lift[i] = 0; }
    const down = s.held[i] > 0;
    const goal = down ? 1 : i === 0 && s.latched ? KEY.latchDepth : 0;
    const stiff = down ? KEY.pressStiffness : KEY.returnStiffness;
    const damp = down ? KEY.pressDamping : KEY.returnDamping;
    s.dv[i] += (stiff * (goal - s.d[i]) - damp * s.dv[i]) * dt;
    s.d[i] += s.dv[i] * dt;
    if (s.d[i] >= 1) {
      s.d[i] = 1;
      if (s.dv[i] > 0) {
        if (s.armed[i] && down) { s.armed[i] = 0; act(s, i); }
        // The impact pushes the chassis down and shakes the cassette in its tray.
        if (!s.reduced) {
          const hit = s.dv[i] * LAYOUT.keys.travel;
          s.dipV += hit * CHASSIS.dipImpulse * CHASSIS.dipStiffness * 0.05;
          s.rattleV += hit * CHASSIS.rattleImpulse * CHASSIS.rattleStiffness * 0.05;
        }
        s.dv[i] = -s.dv[i] * KEY.bottomRestitution;
      }
    }
    if (s.d[i] <= 0) {
      s.d[i] = 0;
      if (s.dv[i] < 0) s.dv[i] = -s.dv[i] * KEY.topRestitution;
    }
    if (!down && s.d[i] < KEY.rearm) s.armed[i] = 1;
  }

  // Transport: winding while rewind / fast-forward is held at the bottom, else play speed while latched.
  const rew = s.held[3] > 0 && s.armed[3] === 0;
  const ff = s.held[4] > 0 && s.armed[4] === 0;
  const goal = ff ? TRANSPORT.wind : rew ? -TRANSPORT.wind : s.latched ? 1 : 0;
  const speeding = Math.abs(goal) > Math.abs(s.v) && goal * s.v >= 0;
  if (s.scrubbing) s.v = s.scrubV;
  else if (speeding) s.v = relax(s.v, goal, TRANSPORT.spinUpTau, dt);
  else {
    const dec = (TRANSPORT.brake + TRANSPORT.friction * Math.abs(s.v)) * dt;
    const gap = goal - s.v;
    s.v += Math.abs(gap) <= dec ? gap : Math.sign(gap) * dec;
  }

  // Reels: the loaded cassette's hubs follow the capstan, ejected ones coast down. ω = v / r.
  const loaded = loadedSlot(s);
  // A cassette that slides into the tray seats with a small clack: the rattle spring is kicked by its arrival speed.
  if (loaded !== s.seated) {
    if (loaded >= 0 && !s.reduced) s.rattleV += Math.min(1, Math.abs(s.pv)) * CHASSIS.seatKick;
    s.seated = loaded;
  }
  for (let j = 0; j < SLOTS; j++) {
    if (j === loaded) s.w[j] = relax(s.w[j], s.v, TRANSPORT.engageTau, dt);
    else {
      const dec = (TRANSPORT.brake + TRANSPORT.friction * Math.abs(s.w[j])) * dt;
      s.w[j] = Math.abs(s.w[j]) <= dec ? 0 : s.w[j] - Math.sign(s.w[j]) * dec;
    }
    if (s.reduced) continue;
    const rl = tapeRadius(1 - s.prog[j]);
    const rr = tapeRadius(s.prog[j]);
    const base = s.w[j] * TRANSPORT.baseOmega * TRANSPORT.refRadius;
    s.ang[j * 2] += (base / rl) * dt * s.k[j * 2];
    s.ang[j * 2 + 1] += (base / rr) * dt * s.k[j * 2 + 1];
    if (live) s.prog[j] = Math.max(0, Math.min(1, s.prog[j] + s.w[j] * TRANSPORT.progressRate * dt));
  }

  // Pager spring (the finger owns it while dragging).
  if (!s.dragging) {
    s.pv += (PAGER.stiffness * (s.target - s.p) - PAGER.damping * s.pv) * dt;
    s.p += s.pv * dt;
  }
  if (s.target >= SLOTS && s.p >= SLOTS - 1) { s.target -= SLOTS; s.p -= SLOTS; s.dragStart -= SLOTS; }
  if (s.target <= -SLOTS && s.p <= 1 - SLOTS) { s.target += SLOTS; s.p += SLOTS; s.dragStart += SLOTS; }

  // A tick per hub tooth while the finger turns the reel.
  if (loaded >= 0) {
    const tooth = Math.floor(s.ang[loaded * 2] / SECTOR);
    // Rate-limited to the Taptic Engine's useful rate: past it, ticks blur into a buzz.
    if (tooth !== s.tooth) {
      if (s.scrubbing && s.clock - s.tickAt >= SCRUB.tickMinGap) { s.events.push(HAPTIC.tick); s.tickAt = s.clock; }
      s.tooth = tooth;
    }
  }

  // Chassis dip and cassette rattle: two springs excited by key impacts.
  s.dipV += (-CHASSIS.dipStiffness * s.dip - CHASSIS.dipDamping * s.dipV) * dt;
  s.dip += s.dipV * dt;
  s.rattleV += (-CHASSIS.rattleStiffness * s.rattle - CHASSIS.rattleDamping * s.rattleV) * dt;
  s.rattle += s.rattleV * dt;
  s.clock += dt;

  // Lights.
  s.led = relax(s.led, s.latched || Math.abs(s.v) > 0.05 ? 1 : 0, LIGHT.ledTau, dt);
  for (let i = 0; i < KEYS; i++) {
    const on = i === 0 ? s.latched : s.d[i] > 0.5 && s.armed[i] === 0;
    s.lit[i] = relax(s.lit[i], on ? 1 : 0, on ? LIGHT.glyphOnTau : LIGHT.glyphOffTau, dt);
  }
  s.fade = Math.min(1, s.fade + dt / 0.18);
}

export function dragTo(s: Sim, p: number, vp: number, done: boolean) {
  'worklet';
  if (!done) {
    if (!s.dragging) { s.dragging = true; s.dragStart = Math.round(s.p); }
    s.p = p; s.pv = vp;
    return;
  }
  s.dragging = false;
  s.pv = vp;
  const projected = s.p + vp * PAGER.momentum;
  const t = Math.max(s.dragStart - 1, Math.min(s.dragStart + 1, Math.round(projected)));
  if (t !== s.dragStart) s.events.push(HAPTIC.commit);
  s.target = t;
  if (s.reduced) { s.p = t; s.pv = 0; if (t !== s.dragStart) s.fade = 0; }
}

// A finger on a key. Lifting before the cap reaches the bottom finishes the stroke first, so a quick tap clicks.
export function pressKey(s: Sim, i: number, down: boolean) {
  'worklet';
  if (down) { s.held[i] = 1; s.lift[i] = 0; return; }
  if (s.armed[i] === 0 || s.d[i] >= 1) { s.held[i] = 0; s.lift[i] = 0; } else s.lift[i] = 1;
}

// The finger turning a reel: ω (rad/s) converted to tape speed at that reel's pack radius.
export function scrubTo(s: Sim, omega: number, radius: number, active: boolean) {
  'worklet';
  s.scrubbing = active;
  const v = (omega * radius) / (TRANSPORT.baseOmega * TRANSPORT.refRadius);
  s.scrubV = Math.max(-SCRUB.maxSpeed, Math.min(SCRUB.maxSpeed, v));
  if (!active) s.v = s.scrubV;
}

function within(t: number, w: number[]) {
  'worklet';
  return t >= w[0] && t < w[1] ? 1 : 0;
}

// The scripted finger: the same inputs a hand gives.
export function scriptInput(s: Sim, t: number) {
  'worklet';
  s.held[0] = within(t, SCRIPT.play);
  s.held[1] = within(t, SCRIPT.pause);
  s.held[2] = within(t, SCRIPT.next);
  s.held[3] = 0;
  s.held[4] = within(t, SCRIPT.ff);
  const g = SCRIPT.drag;
  const u = (t - g.start) / g.time;
  if (u >= 0 && u < 1) {
    const p = 1 - g.dist * Math.pow(u, g.power);
    const vp = (-g.dist * g.power * Math.pow(u, g.power - 1)) / g.time;
    dragTo(s, p, vp, false);
  } else if (s.dragging && u >= 1) {
    dragTo(s, 1 - g.dist, (-g.dist * g.power) / g.time, true);
  }
}

// Advances the autoplay one fixed step; wraps at the loop's end.
export function stepAuto(s: Sim) {
  'worklet';
  const n = Math.round(LOOP_MS / 1000 / STEP);
  if (s.step >= n) { resetToRest(s); s.step = 0; }
  scriptInput(s, s.step * STEP);
  stepSim(s, STEP, false);
  s.step += 1;
}

// Runs one loop of the script ahead of time and scales each reel's turn so it ends on a whole tooth (60°):
// the reels then look the same at the seam and the preview video loops without a jump.
export function loopScales(): number[] {
  const s = makeSim();
  const n = Math.round(LOOP_MS / 1000 / STEP);
  for (let i = 0; i < n; i++) stepAuto(s);
  const k: number[] = [];
  for (let i = 0; i < SLOTS * 2; i++) {
    const a = s.ang[i];
    k.push(a < 0.01 ? 1 : Math.max(1, Math.round(a / SECTOR)) * SECTOR / a);
  }
  return k;
}
