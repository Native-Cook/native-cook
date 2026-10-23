// Chrome Prize Wheel — deterministic rigid contact and motion state; also executable in Node.
import { PEGS, POINTER, PROFILE, PHASE, PRIZES, JACKPOT_INDEX, SEGMENT_ANGLE, SEGMENTS, type WheelMotion } from './wheel-contract';
import { DEMO, demoDrag, demoSteps } from './wheel-demo';
import { H, MOTION, PHYSICS, TAU, type Physics } from './wheel-motion-constants';

export type WheelSim = {
  theta: number; omega: number; swing: number; swingVelocity: number;
  swayA: number; swayB: number; swayVelocityA: number; swayVelocityB: number;
  contact: number; lastStrike: number; side: number; steps: number; accumulator: number;
  strikes: number; strikeStrength: number; travel: number; still: number;
  dissipated: number; numericalLoss: number;
};
export type Cam = { active: boolean; beta: number; gamma: number; g: number; slope: number; curvature: number };

export function clamp(value: number, low: number, high: number): number {
  'worklet';
  return Math.max(low, Math.min(high, value));
}
export function wrap(angle: number): number {
  'worklet';
  return angle - TAU * Math.floor((angle + Math.PI) / TAU);
}
/** Contract-equivalent lookup retained for the motion API; its imported constants are initialized. */
export function winningWedge(theta: number): number {
  'worklet';
  let angle = (POINTER.angle - theta) % TAU;
  if (angle < 0) angle += TAU;
  return Math.floor((angle + SEGMENT_ANGLE / 2) / SEGMENT_ANGLE) % SEGMENTS;
}
export function initialSim(theta = 0, omega = 0): WheelSim {
  'worklet';
  return { theta, omega, swing: 0, swingVelocity: 0, swayA: 0, swayB: 0,
    swayVelocityA: 0, swayVelocityB: 0, contact: -1, lastStrike: 1e9, side: 0, steps: 0,
    accumulator: 0, strikes: 0, strikeStrength: 0, travel: 0, still: 0,
    dissipated: 0, numericalLoss: 0 };
}

/** Offset of the tangent axis. At the edge/tip seam the edge foot is exactly the apex. */
export function contactGamma(d: number): number {
  'worklet';
  const l = POINTER.length;
  const b = POINTER.halfBase;
  const r = PEGS.size;
  if (d > l + r) return -1;
  const s = Math.sqrt(l * l + b * b);
  const seam = Math.sqrt(l * l + r * r + 2 * l * r * b / s);
  if (d <= seam) return Math.asin(clamp((b * l / s + r) / d, -1, 1)) - Math.atan2(b, l);
  return Math.acos(clamp((l * l + d * d - r * r) / (2 * l * d), -1, 1));
}
export function camPosition(theta: number, peg: number, side: number): Cam {
  'worklet';
  const a = theta + PEGS.phase + peg * PEGS.spacing;
  const x = POINTER.pivot + PEGS.radius * Math.cos(a);
  const y = PEGS.radius * Math.sin(a);
  const gamma = contactGamma(Math.sqrt(x * x + y * y));
  const beta = Math.atan2(y, x);
  return { active: gamma >= 0, beta, gamma, g: beta + side * Math.max(0, gamma), slope: 0, curvature: 0 };
}
export function camAt(theta: number, peg: number, side: number): Cam {
  'worklet';
  const c = camPosition(theta, peg, side);
  if (!c.active) return c;
  const e = PHYSICS.derivativeStep;
  const before = camPosition(theta - e, peg, side);
  const after = camPosition(theta + e, peg, side);
  if (!before.active || !after.active) return { ...c, active: false };
  c.slope = (after.g - before.g) / (2 * e);
  c.curvature = (after.g - 2 * c.g + before.g) / (e * e);
  return c;
}
export function simEnergy(s: WheelSim, physics?: Physics): number {
  'worklet';
  // Module defaults belong in the body so the Worklets plugin captures them in __closure.
  const p = physics ?? PHYSICS;
  return 0.5 * (s.omega * s.omega + p.inertia * s.swingVelocity * s.swingVelocity + p.stiffness * s.swing * s.swing);
}

/** Implicit midpoint is an exact energy balance for a linear damped spring. */
export function oscillator(x: number, v: number, k: number, c: number, dt: number, force = 0): [number, number] {
  'worklet';
  const vn = (v * (1 - c * dt / 2 - k * dt * dt / 4) - k * dt * x + dt * force)
    / (1 + c * dt / 2 + k * dt * dt / 4);
  return [x + dt * (v + vn) / 2, vn];
}

/** One 1/480 s step. Mutates only its caller-owned serialisable state. A driven angle is kinematic. */
export function stepWheel(s: WheelSim, drivenTheta?: number, physics?: Physics): void {
  'worklet';
  const p = physics ?? PHYSICS;
  const oldTheta = s.theta;
  const oldOmega = s.omega;
  const oldSwing = s.swing;
  const oldEnergy = simEnergy(s, p);
  s.strikeStrength = 0;
  const driven = drivenTheta !== undefined;
  let attached = false;
  if (s.contact >= 0) {
    const cam = camAt(s.theta, s.contact, s.side);
    if (cam.active) {
      const springTorque = -p.stiffness * cam.g * cam.slope;
      // Stiction can hold a free wheel, never a bent flapper: it must roll off the peg.
      const friction = Math.min(p.coulomb, Math.abs(springTorque) * p.contactFrictionFraction);
      const torque = springTorque - p.damping * cam.slope * cam.slope * s.omega
        - p.inertia * cam.slope * cam.curvature * s.omega * s.omega
        - (Math.abs(s.omega) > 0.000001 ? Math.sign(s.omega) * friction : 0) - p.viscous * s.omega;
      const acceleration = torque / (1 + p.inertia * cam.slope * cam.slope);
      const reaction = p.inertia * (cam.slope * acceleration + cam.curvature * s.omega * s.omega)
        + p.damping * cam.slope * s.omega + p.stiffness * cam.g;
      if (driven || s.side * reaction >= 0) {
        const predictedOmega = clamp(s.omega + acceleration * H, -p.speedCap, p.speedCap);
        const nextTheta = driven ? drivenTheta : s.theta + (s.omega + predictedOmega) * H / 2;
        const next = camAt(nextTheta, s.contact, s.side);
        if (next.active) {
          s.theta = nextTheta;
          s.omega = driven ? (nextTheta - oldTheta) / H : predictedOmega;
          s.swing = next.g;
          s.swingVelocity = next.slope * s.omega;
          attached = true;
        }
      }
    }
    if (!attached) s.contact = -1;
  }
  if (!attached) {
    const speed = Math.max(0, (Math.abs(s.omega) - p.coulomb * H) / (1 + p.viscous * H));
    s.omega = driven ? (drivenTheta - s.theta) / H : Math.sign(s.omega) * speed;
    s.theta = driven ? drivenTheta : s.theta + (oldOmega + s.omega) * H / 2;
    const free = oscillator(s.swing, s.swingVelocity, p.stiffness / p.inertia, p.damping / p.inertia, H);
    s.swing = free[0];
    s.swingVelocity = free[1];
    // Only the closest peg can reach the pointer. Retain its unwrapped id through complete turns.
    const pegId = Math.round((Math.PI - s.theta - PEGS.phase) / PEGS.spacing);
    const peg = ((pegId % PEGS.count) + PEGS.count) % PEGS.count;
    const c = camPosition(s.theta, peg, 0);
    if (c.active && Math.abs(s.swing - c.beta) < c.gamma) {
      const prev = camPosition(oldTheta, peg, 0);
      const side = oldSwing < prev.beta ? -1 : 1;
      const boundary = camAt(s.theta, peg, side);
      if (boundary.active) {
        const relative = s.swingVelocity - boundary.slope * s.omega;
        const impulse = -(1 + p.restitution) * relative / (1 / p.inertia + (driven ? 0 : boundary.slope * boundary.slope));
        s.swingVelocity += impulse / p.inertia;
        if (!driven) s.omega -= impulse * boundary.slope;
        s.swing = boundary.g;
        // Low approach speed sticks; a fast restitution bounce separates until its next impact.
        s.contact = Math.abs(relative) < 2 || s.lastStrike === pegId ? peg : -1;
        s.side = side;
        if (s.contact >= 0) s.swingVelocity = boundary.slope * s.omega;
        if (s.lastStrike !== pegId) {
          s.strikes += 1;
          s.strikeStrength = Math.abs(impulse);
          s.lastStrike = pegId;
        }
      }
    }
  }
  s.omega = clamp(s.omega, -p.speedCap, p.speedCap);
  // Discrete constraint projection must never manufacture energy. Correct only integration excess;
  // record it separately so proofs can expose numerical dissipation instead of hiding it as friction.
  if (!driven) {
    const energy = simEnergy(s, p);
    if (energy > oldEnergy) {
      const potential = 0.5 * p.stiffness * s.swing * s.swing;
      const kinetic = energy - potential;
      if (potential <= oldEnergy && kinetic > 0) {
        const ratio = Math.sqrt(Math.max(0, (oldEnergy - potential) / kinetic));
        s.omega *= ratio;
        s.swingVelocity *= ratio;
      } else {
        // Turn at a sub-step energy barrier. The old pose is already nonpenetrating.
        s.theta = oldTheta;
        s.swing = oldSwing;
        s.omega = -oldOmega * p.restitution;
        s.swingVelocity = s.contact >= 0 ? camAt(s.theta, s.contact, s.side).slope * s.omega : 0;
      }
      s.numericalLoss += Math.max(0, energy - simEnergy(s, p));
    }
    s.dissipated += Math.max(0, oldEnergy - simEnergy(s, p));
  }
  const acceleration = (s.omega - oldOmega) / H;
  const a = oscillator(s.swayA, s.swayVelocityA, p.swayFrequencyA ** 2, 2 * p.swayDampingA * p.swayFrequencyA, H, p.swayGainA * acceleration * p.swayFrequencyA ** 2);
  const b = oscillator(s.swayB, s.swayVelocityB, p.swayFrequencyB ** 2, 2 * p.swayDampingB * p.swayFrequencyB, H, p.swayGainB * acceleration * p.swayFrequencyB ** 2);
  s.swayA = clamp(a[0], -p.swayLimit, p.swayLimit);
  s.swayB = clamp(b[0], -p.swayLimit, p.swayLimit);
  s.swayVelocityA = s.swayA === a[0] ? a[1] : 0;
  s.swayVelocityB = s.swayB === b[0] ? b[1] : 0;
  s.travel += Math.abs(s.theta - oldTheta);
  s.steps += 1;
  const still = Math.abs(s.omega) < p.stillSpeed && Math.abs(s.swing) < p.stillSwing
    && Math.abs(s.swingVelocity) < p.stillPointerSpeed && s.contact < 0;
  s.still = still ? s.still + H : 0;
}

/** Carry fractional time, never drop steps: the display rate cannot change the landing angle. */
export function advanceWheel(s: WheelSim, elapsed: number, physics?: Physics): void {
  'worklet';
  const p = physics ?? PHYSICS;
  s.accumulator += Math.max(0, elapsed);
  const steps = Math.floor((s.accumulator + 1e-12) / H);
  s.accumulator -= steps * H;
  for (let i = 0; i < steps; i++) stepWheel(s, undefined, p);
}

// The same serialisable controller is used by the hook and the offline whole-loop proofs.
// These imports contain only data/worklets; wheel-contract's Reanimated import is type-only.
export type WheelValues = { [K in keyof WheelMotion]: number };
type ValueKey = keyof WheelValues;
type Tween = { key: ValueKey; from: number; to: number; elapsed: number; duration: number; k: number; c: number; velocity: number; travel: boolean };
export type SceneConfig = { reduced: boolean; autoPlay: boolean; cx: number; cy: number; radius: number; buttonX: number; buttonY: number };
export type WheelScene = {
  sim: WheelSim; values: WheelValues; tweens: Tween[]; accumulator: number; steps: number;
  phaseStep: number; releaseSpeed: number; releaseTravel: number; targetTheta: number;
  tiltTargetX: number; tiltTargetY: number; tiltBaseX: number; tiltBaseY: number; tiltVX: number; tiltVY: number;
  touching: boolean; heroOriginX: number; heroOriginY: number; held: boolean; hubHeld: boolean; heroHeld: boolean; fingerAngle: number; trackedRate: number; moveStep: number;
  events: number; lastHaptic: number; demoStage: number; demoStep: number; demoStartTheta: number;
  takeover: boolean; lastTouch: number; homeStep: number; homeFrom: number; homeTo: number;
  reducedPick: number; reducedStep: number; loops: number; loopStep: number; lastPeriod: number;
};
export const MOTION_EVENT = { light: 1, rigid: 2, medium: 4, success: 8 } as const;

export function initialValues(): WheelValues {
  'worklet';
  return { theta: 0, omega: 0, tiltX: 0, tiltY: 0, envTurn: 0, swayA: 0, swayB: 0,
    winner: -1, lift: 0, glow: 0, winnerPrize: 1, winnerPrizeScale: 1, dim: 0, comet: 0, hubPress: 0,
    phase: PHASE.idle, swing: 0, tick: 0, strikes: 0, wheelScale: 1, wheelOpacity: 1, swirl: 0,
    hero: 0, heroSquash: 0, heroX: 0, heroY: 0, heroTilt: 0, rays: 0, burst: -1, burstPower: 0.6,
    badge: 0, caption: 0, count: 0, button: 0, buttonPress: 0, collect: 0, copySwap: 0, hint: 1,
    hintNudge: 0, touchX: 0, touchY: 0, touchOn: 0 };
}
export function initialScene(): WheelScene {
  'worklet';
  return { sim: initialSim(), values: initialValues(), tweens: [], accumulator: 0, steps: 0,
    phaseStep: 0, releaseSpeed: 0, releaseTravel: 0, targetTheta: 0,
    tiltTargetX: 0, tiltTargetY: 0, tiltBaseX: 0, tiltBaseY: 0, tiltVX: 0, tiltVY: 0,
    touching: false, heroOriginX: 0, heroOriginY: 0, held: false, hubHeld: false, heroHeld: false, fingerAngle: 0, trackedRate: 0, moveStep: 0,
    events: 0, lastHaptic: -100, demoStage: 0, demoStep: 0, demoStartTheta: 0,
    takeover: false, lastTouch: 0, homeStep: -1, homeFrom: 0, homeTo: 0,
    reducedPick: -1, reducedStep: -1, loops: 0, loopStep: 0, lastPeriod: 0 };
}
/** Shared-value snapshots are immutable; clone before the frame or a gesture mutates the controller. */
export function cloneScene(s: WheelScene): WheelScene {
  'worklet';
  const tweens: Tween[] = [];
  for (let i = 0; i < s.tweens.length; i++) tweens.push({ ...s.tweens[i] });
  return { ...s, values: { ...s.values }, sim: { ...s.sim }, tweens };
}
export function cubic(t: number, curve: readonly number[]): number {
  'worklet';
  const x = clamp(t, 0, 1);
  let lo = 0;
  let hi = 1;
  let u = x;
  for (let i = 0; i < 16; i++) {
    u = (lo + hi) / 2;
    const v = 1 - u;
    const bx = 3 * v * v * u * curve[0] + 3 * v * u * u * curve[2] + u * u * u;
    if (bx < x) lo = u; else hi = u;
  }
  return x <= 0 ? 0 : x >= 1 ? 1 : 3 * (1 - u) * (1 - u) * u * curve[1] + 3 * (1 - u) * u * u * curve[3] + u * u * u;
}
export function animateValue(s: WheelScene, key: ValueKey, to: number, duration: number, spring?: readonly number[], travel = false): void {
  'worklet';
  let velocity = 0;
  for (let i = s.tweens.length - 1; i >= 0; i--) {
    if (s.tweens[i].key === key) { velocity = s.tweens[i].velocity; s.tweens.splice(i, 1); }
  }
  s.tweens.push({ key, from: s.values[key], to, elapsed: 0, duration, k: spring?.[0] ?? 0, c: spring?.[1] ?? 0, velocity, travel });
}
export function setValue(s: WheelScene, key: ValueKey, to: number): void {
  'worklet';
  for (let i = s.tweens.length - 1; i >= 0; i--) if (s.tweens[i].key === key) s.tweens.splice(i, 1);
  s.values[key] = to;
}
export function stepTweens(s: WheelScene): void {
  'worklet';
  for (let i = s.tweens.length - 1; i >= 0; i--) {
    const a = s.tweens[i];
    a.elapsed += H;
    if (a.k > 0) {
      const result = oscillator(s.values[a.key] - a.to, a.velocity, a.k, a.c, H);
      s.values[a.key] = a.to + result[0];
      a.velocity = result[1];
      if (Math.abs(result[0]) < 0.00001 && Math.abs(result[1]) < 0.0001) { s.values[a.key] = a.to; s.tweens.splice(i, 1); }
    } else {
      s.values[a.key] = a.from + (a.to - a.from) * cubic(a.elapsed / a.duration, a.travel ? MOTION.travel : MOTION.out);
      if (a.elapsed + 1e-12 >= a.duration) { s.values[a.key] = a.to; s.tweens.splice(i, 1); }
    }
  }
}
export function phaseTo(s: WheelScene, phase: number): void {
  'worklet';
  s.values.phase = phase;
  s.phaseStep = s.steps;
}
export function startReveal(s: WheelScene, reduced: boolean): void {
  'worklet';
  phaseTo(s, PHASE.reveal);
  // A user's long spin may outlast the resume delay. Give the revealed reward its
  // own inspection window instead of collecting it on the next simulation step.
  if (s.takeover) s.lastTouch = s.steps;
  s.values.winner = winningWedge(s.sim.theta);
  const v = s.values;
  if (reduced) {
    animateValue(s, 'lift', 1, MOTION.reducedReveal);
    animateValue(s, 'glow', 1, MOTION.reducedReveal);
    animateValue(s, 'dim', 1, MOTION.reducedReveal);
    animateValue(s, 'hero', 1, MOTION.reducedReveal);
    animateValue(s, 'caption', 1, MOTION.reducedReveal);
    animateValue(s, 'button', 1, MOTION.reducedReveal);
    animateValue(s, 'copySwap', 1, MOTION.reducedReveal);
    v.winnerPrize = 0; v.count = PRIZES[v.winner].count; v.badge = 1; v.rays = 0.6;
    v.burst = -1; v.heroSquash = 0; s.events |= MOTION_EVENT.success;
    return;
  }
  animateValue(s, 'lift', 1, 0, MOTION.liftSpring);
  animateValue(s, 'glow', 1, MOTION.reveal.glow);
  animateValue(s, 'comet', 1, MOTION.reveal.comet);
  animateValue(s, 'dim', 1, MOTION.reveal.dim);
  animateValue(s, 'wheelScale', MOTION.revealScale, 0, MOTION.wheelSpring);
  animateValue(s, 'copySwap', 1, MOTION.reveal.copy);
  animateValue(s, 'heroSquash', -0.5, MOTION.reveal.crouch);
}
export function startCollect(s: WheelScene, reduced: boolean): void {
  'worklet';
  if (s.values.phase !== PHASE.reveal) return;
  phaseTo(s, PHASE.collecting);
  s.heroHeld = false;
  s.events |= MOTION_EVENT.light;
  s.values.burst = -1;
  if (reduced) {
    animateValue(s, 'hero', 0, MOTION.reducedCollect);
    animateValue(s, 'lift', 0, MOTION.reducedCollect);
    animateValue(s, 'glow', 0, MOTION.reducedCollect);
    animateValue(s, 'dim', 0, MOTION.reducedCollect);
    animateValue(s, 'caption', 0, MOTION.reducedCollect);
    animateValue(s, 'button', 0, MOTION.reducedCollect);
    animateValue(s, 'rays', 0, MOTION.reducedCollect);
    animateValue(s, 'copySwap', 0, MOTION.reducedCollect);
    animateValue(s, 'buttonPress', 0, MOTION.reducedCollect);
    return;
  }
  animateValue(s, 'buttonPress', 0, 0, MOTION.swallowSpring);
  animateValue(s, 'collect', 1, MOTION.collecting.flight, undefined, true);
  animateValue(s, 'caption', 0, MOTION.collecting.caption);
  animateValue(s, 'rays', 0, MOTION.collecting.rays);
  animateValue(s, 'copySwap', 0, MOTION.collecting.copy);
  animateValue(s, 'heroX', 0, 0, MOTION.toySpring);
  animateValue(s, 'heroY', 0, 0, MOTION.toySpring);
  animateValue(s, 'heroTilt', 0, 0, MOTION.toySpring);
}
export function reducedSpin(s: WheelScene, random: number): void {
  'worklet';
  const current = winningWedge(s.sim.theta);
  s.reducedPick = (current + 1 + Math.floor(clamp(random, 0, 0.999999) * (PRIZES.length - 1))) % PRIZES.length;
  s.reducedStep = s.steps;
  s.sim.omega = 0; s.held = false; s.hubHeld = false;
  phaseTo(s, PHASE.spinning);
  animateValue(s, 'wheelOpacity', 0, MOTION.reducedFade);
}
export function releaseWheel(s: WheelScene, omega: number): void {
  'worklet';
  s.held = false; s.hubHeld = false;
  s.tiltTargetX = 0; s.tiltTargetY = 0;
  const launchChange = clamp(omega, -PHYSICS.speedCap, PHYSICS.speedCap) - s.sim.omega;
  s.sim.swayVelocityA += PHYSICS.swayGainA * PHYSICS.swayFrequencyA ** 2 * launchChange;
  s.sim.swayVelocityB += PHYSICS.swayGainB * PHYSICS.swayFrequencyB ** 2 * launchChange;
  s.sim.omega = clamp(omega, -PHYSICS.speedCap, PHYSICS.speedCap);
  s.values.winner = -1;
  s.sim.still = 0;
  s.releaseSpeed = Math.abs(s.sim.omega);
  s.releaseTravel = s.sim.travel;
  phaseTo(s, PHASE.spinning);
  animateValue(s, 'wheelScale', 1, 0, MOTION.holdSpring);
  animateValue(s, 'hubPress', 0, 0, MOTION.hubSpring);
  animateValue(s, 'hint', MOTION.hintSpinning, MOTION.hintDuration);
}
/** Real input pauses the script at its current pose. Held touches never time out. */
export function takeOver(s: WheelScene): void {
  'worklet';
  if (!s.takeover && s.held) releaseWheel(s, s.sim.omega);
  s.takeover = true; s.lastTouch = s.steps; s.homeStep = -1;
  animateValue(s, 'touchOn', 0, MOTION.fingerFade);
}
export function tickScene(s: WheelScene, before: number): void {
  'worklet';
  if (s.sim.strikes > before) {
    s.values.strikes += s.sim.strikes - before;
    s.values.tick = 1;
    if ((s.steps - s.lastHaptic) * H >= MOTION.hapticGap) {
      s.events |= Math.abs(s.sim.omega) < 4 ? MOTION_EVENT.rigid : MOTION_EVENT.light;
      s.lastHaptic = s.steps;
    }
  }
}
export function stepReveal(s: WheelScene, reduced: boolean): void {
  'worklet';
  const step = s.steps - s.phaseStep;
  const r = MOTION.reveal;
  const c = MOTION.collecting;
  const v = s.values;
  if (v.phase === PHASE.reveal && !reduced) {
    if (step === demoSteps(r.launchAt)) {
      v.winnerPrize = 0;
      animateValue(s, 'hero', 1, 0, MOTION.heroSpring);
      animateValue(s, 'heroSquash', 0.6, r.stretch);
      animateValue(s, 'rays', 1, r.rays);
      v.burst = 0;
      v.burstPower = v.winner === JACKPOT_INDEX ? 1 : Math.min(0.85, 0.6 + 0.2 * PRIZES[v.winner].count / 15);
      s.events |= MOTION_EVENT.success;
    }
    if (step === demoSteps(r.launchAt + r.stretch)) animateValue(s, 'heroSquash', 0, 0, MOTION.squashSpring);
    if (step === demoSteps(r.badgeAt)) animateValue(s, 'badge', 1, r.badge, undefined, true);
    if (step === demoSteps(r.captionAt)) {
      animateValue(s, 'caption', 1, r.caption);
      const n = PRIZES[v.winner].count;
      animateValue(s, 'count', n, Math.min(r.countCap, r.countBase + r.countPerPrize * n));
    }
    if (step === demoSteps(r.buttonAt)) animateValue(s, 'button', 1, r.button, undefined, true);
  }
  if (v.phase === PHASE.collecting) {
    if (!reduced && step === demoSteps(c.sinkAt)) {
      animateValue(s, 'lift', 0, 0, MOTION.sinkSpring);
      animateValue(s, 'glow', 0, c.glow);
      animateValue(s, 'dim', 0, c.dim);
      animateValue(s, 'wheelScale', 1, 0, MOTION.wheelSpring);
    }
    if (!reduced && step === demoSteps(c.swallowAt)) animateValue(s, 'buttonPress', -1, c.swallow);
    if (!reduced && step === demoSteps(c.swallowAt + c.swallow)) animateValue(s, 'buttonPress', 0, 0, MOTION.swallowSpring);
    if (step === demoSteps(reduced ? MOTION.reducedCollect : c.restockAt)) {
      setValue(s, 'hero', 0); setValue(s, 'collect', 0); setValue(s, 'badge', 0); setValue(s, 'count', 0);
      v.winnerPrize = 1;
      if (!reduced) { setValue(s, 'winnerPrizeScale', 1.28); animateValue(s, 'winnerPrizeScale', 1, 0, MOTION.restockSpring); }
    }
    if (!reduced && step === demoSteps(c.buttonAt)) animateValue(s, 'button', 0, c.button, undefined, true);
    if (step >= demoSteps(reduced ? MOTION.reducedCollect : c.done)) {
      v.winner = -1; v.comet = 0;
      phaseTo(s, PHASE.idle);
      animateValue(s, 'hint', 1, MOTION.hintDuration);
    }
  }
}

/** Script stages: rest, touch/drag, spin+admire, collect, rest, touch/drag, spin+admire, collect, tail. */
export function stepDemo(s: WheelScene, config: SceneConfig): void {
  'worklet';
  const stage = s.demoStage;
  const elapsed = s.steps - s.demoStep;
  const second = stage >= 5;
  const throwSpec = second ? DEMO.second : DEMO.first;
  const v = s.values;
  if (stage === 0 || stage === 4 || stage === 8) {
    const wait = stage === 0 ? DEMO.rest : stage === 4 ? DEMO.between : DEMO.tail;
    if (elapsed < demoSteps(wait)) return;
    if (stage === 8) {
      s.lastPeriod = s.steps - s.loopStep; s.loopStep = s.steps; s.loops += 1;
      s.sim = initialSim(); s.targetTheta = 0;
      const strikes = v.strikes;
      s.values = initialValues(); s.values.strikes = strikes; s.tweens = [];
      s.tiltBaseX = 0; s.tiltBaseY = 0; s.tiltVX = 0; s.tiltVY = 0;
      s.demoStage = 0; s.demoStep = s.steps;
      return;
    }
    s.demoStage = stage === 0 ? 1 : 5; s.demoStep = s.steps;
    s.demoStartTheta = s.sim.theta;
    s.sim = initialSim(s.sim.theta);
    s.targetTheta = s.sim.theta;
    const spec = stage === 0 ? DEMO.first : DEMO.second;
    v.touchX = config.cx + spec.x * config.radius;
    v.touchY = config.cy + spec.y * config.radius;
    animateValue(s, 'touchOn', 1, MOTION.fingerFade);
    s.held = true; phaseTo(s, PHASE.held);
    animateValue(s, 'wheelScale', MOTION.heldScale, 0, MOTION.holdSpring);
  } else if (stage === 1 || stage === 5) {
    const dragStep = elapsed - demoSteps(DEMO.fingerWait);
    if (dragStep > 0) {
      const angle = demoDrag(dragStep, throwSpec.duration, throwSpec.drag);
      s.targetTheta = s.demoStartTheta + angle;
      const c = Math.cos(angle); const n = Math.sin(angle);
      const x = throwSpec.x * c - throwSpec.y * n;
      const y = throwSpec.x * n + throwSpec.y * c;
      v.touchX = config.cx + x * config.radius; v.touchY = config.cy + y * config.radius;
      s.tiltTargetX = MOTION.tiltGain * x; s.tiltTargetY = MOTION.tiltGain * y;
      // The final driven step is integrated before release, as it is in the launch search.
      if (dragStep > demoSteps(throwSpec.duration)) {
        releaseWheel(s, throwSpec.omega);
        animateValue(s, 'touchOn', 0, MOTION.fingerFade);
        s.demoStage = stage + 1; s.demoStep = s.steps;
      }
    }
  } else if (stage === 2 || stage === 6) {
    if (v.phase === PHASE.reveal) {
      const age = s.steps - s.phaseStep;
      if (age === demoSteps(throwSpec.admire - MOTION.fingerFade)) {
        v.touchX = config.buttonX; v.touchY = config.buttonY;
        animateValue(s, 'touchOn', 1, MOTION.fingerFade);
      }
      if (age === demoSteps(throwSpec.admire)) animateValue(s, 'buttonPress', 1, MOTION.collecting.press);
      if (age >= demoSteps(throwSpec.admire + DEMO.press)) {
        startCollect(s, false); animateValue(s, 'touchOn', 0, MOTION.fingerFade);
        s.demoStage = stage + 1; s.demoStep = s.steps;
      }
    }
  } else if ((stage === 3 || stage === 7) && v.phase === PHASE.idle) {
    s.demoStage = stage + 1; s.demoStep = s.steps;
  }
}
export function stepScene(s: WheelScene, config: SceneConfig): void {
  'worklet';
  s.steps += 1;
  if (config.autoPlay && !config.reduced && !s.takeover) stepDemo(s, config);
  if (config.autoPlay && !config.reduced && s.takeover && !s.touching && !s.held && !s.hubHeld && !s.heroHeld
    && (s.steps - s.lastTouch) * H >= MOTION.takeoverDelay) {
    if (s.values.phase === PHASE.reveal) startCollect(s, false);
    if (s.values.phase === PHASE.idle && s.homeStep < 0) {
      s.homeStep = s.steps; s.homeFrom = s.sim.theta; s.homeTo = Math.round(s.sim.theta / TAU) * TAU;
    }
  }
  const previousStrikes = s.sim.strikes;
  if (s.homeStep >= 0) {
    const progress = (s.steps - s.homeStep) * H / MOTION.homeDuration;
    stepWheel(s.sim, s.homeFrom + (s.homeTo - s.homeFrom) * cubic(progress, MOTION.travel));
    if (progress >= 1) {
      // Keep the flapper and gondolas ringing after the last homing contact.
      s.sim.theta = 0; s.sim.omega = 0; s.sim.contact = -1;
      s.targetTheta = 0; s.homeStep = -1; s.takeover = false;
      s.demoStage = 0; s.demoStep = s.steps; s.loopStep = s.steps;
    }
  } else if (s.held && !config.reduced) stepWheel(s.sim, s.targetTheta);
  else if (!config.reduced && (s.values.phase === PHASE.spinning || s.values.phase === PHASE.landed)) {
    stepWheel(s.sim);
    const counted = s.releaseSpeed >= PHYSICS.countedSpeed && s.sim.travel - s.releaseTravel >= PHYSICS.countedTravel;
    if (s.sim.still > 0 && s.values.phase === PHASE.spinning && counted) {
      s.values.winner = winningWedge(s.sim.theta); phaseTo(s, PHASE.landed);
    }
    if (s.sim.still > PHYSICS.stillBeat) {
      if (counted) startReveal(s, false);
      else {
        phaseTo(s, PHASE.idle); animateValue(s, 'hint', 1, MOTION.hintDuration);
        s.values.hintNudge = 1;
      }
    } else if (s.sim.still === 0 && s.values.phase === PHASE.landed) phaseTo(s, PHASE.spinning);
  }
  else if (!config.reduced && !s.hubHeld) stepWheel(s.sim);
  tickScene(s, previousStrikes);
  if (config.reduced && s.reducedStep >= 0) {
    const elapsed = s.steps - s.reducedStep;
    if (elapsed === demoSteps(MOTION.reducedFade)) {
      s.sim = initialSim(POINTER.angle - s.reducedPick * SEGMENT_ANGLE);
      animateValue(s, 'wheelOpacity', 1, MOTION.reducedFade);
    }
    if (elapsed >= demoSteps(2 * MOTION.reducedFade)) { s.reducedStep = -1; startReveal(s, true); }
  }
  stepReveal(s, config.reduced);
  stepTweens(s);
  const v = s.values;
  v.theta = s.sim.theta; v.omega = s.sim.omega; v.swing = s.sim.swing;
  v.swayA = config.reduced ? 0 : clamp(s.sim.swayA + PHYSICS.swayLean * s.sim.omega, -PHYSICS.swayLimit, PHYSICS.swayLimit);
  v.swayB = config.reduced ? 0 : s.sim.swayB;
  v.tick *= Math.exp(-H / MOTION.tickDecay);
  v.swirl += H * (MOTION.swirlGain * v.omega - MOTION.swirlDrag * v.swirl);
  const tx = oscillator(s.tiltBaseX - s.tiltTargetX, s.tiltVX, MOTION.holdSpring[0], MOTION.holdSpring[1], H);
  const ty = oscillator(s.tiltBaseY - s.tiltTargetY, s.tiltVY, MOTION.holdSpring[0], MOTION.holdSpring[1], H);
  s.tiltBaseX = tx[0] + s.tiltTargetX; s.tiltVX = tx[1];
  s.tiltBaseY = ty[0] + s.tiltTargetY; s.tiltVY = ty[1];
  const wobble = config.reduced ? 0 : MOTION.wobbleBase + MOTION.wobbleSpeed * Math.min(Math.abs(v.omega) / 20, 1);
  v.tiltX = s.tiltBaseX + wobble * Math.cos(v.theta + MOTION.wobblePhase);
  v.tiltY = s.tiltBaseY + wobble * Math.sin(v.theta + MOTION.wobblePhase);
  // Quiet continuous drift prevents a light discontinuity on return to idle. The demo's
  // final rest fades that drift to zero so its next reference pose has identical lighting.
  const closing = config.autoPlay && !s.takeover && s.demoStage === 8
    ? 1 - cubic((s.steps - s.demoStep) * H / DEMO.tail, MOTION.out) : 1;
  v.envTurn = config.reduced ? 0 : closing * MOTION.environmentAmount
    * Math.sin(TAU * (s.steps - s.loopStep) * H / MOTION.environmentPeriod);
  if (v.burst >= 0) v.burst += H;
  if (v.phase === PHASE.idle && v.hintNudge !== 0) {
    const t = (s.steps - s.phaseStep) * H;
    v.hintNudge = t >= MOTION.nudgeDuration ? 0 : Math.cos(TAU * 2 * t / MOTION.nudgeDuration) * (1 - t / MOTION.nudgeDuration);
  }
}
export function advanceScene(s: WheelScene, elapsed: number, config: SceneConfig): void {
  'worklet';
  s.events = 0;
  s.accumulator += Math.max(0, elapsed);
  const count = Math.floor((s.accumulator + 1e-12) / H);
  s.accumulator -= count * H;
  for (let i = 0; i < count; i++) stepScene(s, config);
}

export type WheelAction =
  | { type: 'down' | 'move'; x: number; y: number }
  | { type: 'release'; x: number; y: number; vx: number; vy: number; cancelled: boolean; random: number }
  | { type: 'hub'; random: number }
  | { type: 'heroDown' | 'heroRelease' | 'pressIn' | 'pressOut' | 'collect' }
  | { type: 'heroMove'; x: number; y: number; vx: number };

export function actWheel(s: WheelScene, action: WheelAction, config: SceneConfig): void {
  'worklet';
  s.events = 0;
  const v = s.values;
  if (action.type === 'release') { s.touching = false; s.lastTouch = s.steps; }
  if (action.type === 'down') {
    s.touching = true;
    takeOver(s);
    if (v.phase !== PHASE.idle && v.phase !== PHASE.spinning && v.phase !== PHASE.landed && v.phase !== PHASE.held) return;
    const d = Math.sqrt(action.x * action.x + action.y * action.y);
    if (d > config.radius) return;
    s.hubHeld = d < config.radius * PROFILE.hub * MOTION.hubHitScale;
    s.held = !s.hubHeld;
    v.winner = -1;
    s.targetTheta = s.sim.theta;
    s.fingerAngle = Math.atan2(action.y, action.x);
    s.trackedRate = 0; s.moveStep = s.steps;
    s.sim.omega = 0;
    phaseTo(s, PHASE.held);
    if (s.hubHeld) animateValue(s, 'hubPress', 1, MOTION.hubPress);
    else if (!config.reduced) {
      animateValue(s, 'wheelScale', MOTION.heldScale, 0, MOTION.holdSpring);
      s.tiltTargetX = MOTION.tiltGain * action.x / config.radius;
      s.tiltTargetY = MOTION.tiltGain * action.y / config.radius;
    }
  } else if (action.type === 'move' && s.held) {
    s.lastTouch = s.steps;
    if (config.reduced) return;
    const angle = Math.atan2(action.y, action.x);
    const delta = wrap(angle - s.fingerAngle);
    s.trackedRate += MOTION.trackedBlend * (delta / (Math.max(1, s.steps - s.moveStep) * H) - s.trackedRate);
    s.targetTheta += delta; s.fingerAngle = angle; s.moveStep = s.steps;
    s.tiltTargetX = MOTION.tiltGain * clamp(action.x / config.radius, -1, 1);
    s.tiltTargetY = MOTION.tiltGain * clamp(action.y / config.radius, -1, 1);
  } else if (action.type === 'release' && (s.held || s.hubHeld)) {
    s.lastTouch = s.steps;
    if (action.cancelled) {
      s.held = false; s.hubHeld = false; s.tiltTargetX = 0; s.tiltTargetY = 0;
      if (config.reduced) phaseTo(s, PHASE.idle); else releaseWheel(s, 0);
      animateValue(s, 'hubPress', 0, config.reduced ? MOTION.reducedReveal : 0, config.reduced ? undefined : MOTION.hubSpring);
      return;
    }
    if (config.reduced) { reducedSpin(s, action.random); animateValue(s, 'hubPress', 0, MOTION.reducedReveal); return; }
    if (s.held && s.targetTheta !== s.sim.theta) {
      const before = s.sim.strikes;
      stepWheel(s.sim, s.targetTheta); tickScene(s, before);
    }
    if (s.hubHeld) {
      s.events |= MOTION_EVENT.medium;
      releaseWheel(s, MOTION.hubSpeed + (action.random * 2 - 1) * MOTION.hubVariation);
    } else {
      const d2 = Math.max(config.radius * config.radius * 0.01, action.x * action.x + action.y * action.y);
      const cross = (action.x * action.vy - action.y * action.vx) / d2;
      releaseWheel(s, MOTION.velocityBlend * cross + (1 - MOTION.velocityBlend) * s.trackedRate);
    }
  } else if (action.type === 'hub') {
    takeOver(s);
    if (v.phase !== PHASE.idle && v.phase !== PHASE.spinning && v.phase !== PHASE.landed) return;
    if (config.reduced) reducedSpin(s, action.random);
    else { s.events |= MOTION_EVENT.medium; releaseWheel(s, MOTION.hubSpeed + (action.random * 2 - 1) * MOTION.hubVariation); }
  } else if (action.type === 'heroDown') {
    s.touching = true;
    takeOver(s);
    if (v.phase === PHASE.reveal && v.hero > 0.9 && !config.reduced) {
      s.heroHeld = true; s.heroOriginX = v.heroX; s.heroOriginY = v.heroY;
      setValue(s, 'heroX', v.heroX); setValue(s, 'heroY', v.heroY); setValue(s, 'heroTilt', v.heroTilt);
    }
  } else if (action.type === 'heroMove' && s.heroHeld) {
    s.lastTouch = s.steps;
    v.heroX = s.heroOriginX + action.x * MOTION.heroPull; v.heroY = s.heroOriginY + action.y * MOTION.heroPull;
    v.heroTilt = clamp(action.vx * MOTION.heroTiltGain, -MOTION.heroTiltCap, MOTION.heroTiltCap);
  } else if (action.type === 'heroRelease') {
    s.touching = false; s.heroHeld = false; s.lastTouch = s.steps;
    if (!config.reduced) {
      animateValue(s, 'heroX', 0, 0, MOTION.toySpring);
      animateValue(s, 'heroY', 0, 0, MOTION.toySpring);
      animateValue(s, 'heroTilt', 0, 0, MOTION.toySpring);
    }
  } else if (action.type === 'pressIn') {
    s.touching = true;
    takeOver(s);
    if (v.phase === PHASE.reveal) animateValue(s, 'buttonPress', 1, MOTION.collecting.press);
  } else if (action.type === 'pressOut') {
    s.touching = false;
    s.lastTouch = s.steps;
    animateValue(s, 'buttonPress', 0, config.reduced ? MOTION.reducedReveal : 0, config.reduced ? undefined : MOTION.swallowSpring);
  } else if (action.type === 'collect') {
    s.touching = false;
    takeOver(s); startCollect(s, config.reduced);
  }
}
