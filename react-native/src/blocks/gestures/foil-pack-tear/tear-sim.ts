/** Deterministic, serialisable motion. Public transitions copy their input; no native APIs. */
import { CARD, FRAME, PACK, PEEK_SINK, REST_POSE, TEAR, type TearPose } from './tear-contract';
import { HAPTIC, MOTION } from './tear-motion-constants';

export type TearPhase = 'sealed' | 'tearing' | 'opened' | 'peek' | 'revealing' | 'revealed' | 'collecting';
export type TearInput =
  | { type: 'down'; x: number; y: number; silent?: boolean }
  | { type: 'move'; x: number; y: number; vx: number; vy: number }
  | { type: 'up'; vx: number; vy: number; cancelled?: boolean }
  | { type: 'activate' };
type Hold = 'none' | 'tear' | 'look' | 'card' | 'tilt';

export function clamp(value: number, low = 0, high = 1): number {
  'worklet';
  return Math.max(low, Math.min(high, value));
}

function flatLine(): number[] {
  'worklet';
  const line: number[] = [];
  for (let i = 0; i < TEAR.columns; i++) line.push(0);
  return line;
}

function restPose(): TearPose {
  'worklet';
  // The brief requires the hidden card to be absent, including the first decoded frame.
  return { ...REST_POSE, tearLine: flatLine(), cardOpacity: 0 };
}

function toward(value: number, target: number, amount: number): number {
  'worklet';
  return value + clamp(target - value, -amount, amount);
}

/** Exact unit-mass underdamped spring. Callees precede their capturing worklets. */
export function spring(position: number, velocity: number, target: number, stiffness: number, damping: number, dt: number) {
  'worklet';
  const decay = damping / 2;
  const frequency = Math.sqrt(stiffness - decay * decay);
  const delta = position - target;
  const envelope = Math.exp(-decay * dt);
  const cosine = Math.cos(frequency * dt);
  const sine = Math.sin(frequency * dt);
  return {
    position: target + envelope * (delta * cosine + (velocity + decay * delta) * sine / frequency),
    velocity: envelope * (velocity * cosine - (decay * velocity + stiffness * delta) * sine / frequency),
  };
}

function idlePose(s: TearState, dt: number) {
  'worklet';
  const p = s.pose;
  const resting = !s.down && (s.phase === 'sealed' || s.phase === 'revealed');
  s.idle = toward(s.idle, resting ? 1 : 0, dt / (resting ? 0.5 : 0.15));
  const slow = 2 * Math.PI * s.clock / MOTION.idleSlow;
  const fast = 2 * Math.PI * s.clock / MOTION.idleFast;
  p.swayY = s.reduced ? 0 : s.idle * 0.055 * Math.sin(slow);
  if (!s.reduced && (s.stageKick !== 0 || s.stageVelocity !== 0)) {
    const kick = spring(s.stageKick, s.stageVelocity, 0, MOTION.stageKick.stiffness, MOTION.stageKick.damping, dt);
    s.stageKick = kick.position;
    s.stageVelocity = kick.velocity;
  }
  p.swayX = s.reduced ? 0 : s.idle * 0.025 * Math.sin(fast + 0.8) + s.stageKick;
  const finger = s.down && s.hold !== 'none';
  const peekLamp = s.phase === 'peek' ? 0.3 * s.peekLife * Math.sin(2 * Math.PI * s.age / 2.6) : 0;
  const lx = finger ? clamp((s.fingerX - 201) / 130, -1, 1) : s.reduced ? 0 : s.idle * 0.25 * Math.sin(slow + 2) + peekLamp;
  const ly = finger ? clamp((s.fingerY - 364) / 200, -1, 1) : s.reduced ? 0 : s.idle * 0.15 * Math.sin(fast);
  if (s.phase !== 'revealing') {
    const blend = 1 - Math.exp(-dt / MOTION.lightTau);
    p.lightX = !finger && s.idle === 1 ? lx : p.lightX + (lx - p.lightX) * blend;
    p.lightY = !finger && s.idle === 1 ? ly : p.lightY + (ly - p.lightY) * blend;
  }
  const calmGoal = s.phase === 'sealed' && !s.down ? 1 : 0;
  p.calm = toward(p.calm, calmGoal, dt / (calmGoal ? 0.6 : 0.15));
}

export function initialTear(reduced = false) {
  'worklet';
  const state = {
    pose: restPose(), phase: 'sealed' as TearPhase, age: 1, clock: 0, reduced,
    hold: 'none' as Hold, down: false, downAt: 0, downX: 0, downY: 0,
    fingerX: 0, fingerY: 0, vx: 0, vy: 0, travel: 0, grabDy: 0,
    catching: false, advanced: false, silent: false, coast: 0, autoTear: false,
    lineEnd: 0, lineX: 0, lineY: 0,
    idle: 1, calmAge: 1, peekHit: false, returning: false, pullStart: CARD.peekY as number,
    peekSettled: false, peekLife: 0, stageKick: 0, stageVelocity: 0,
    packVX: 0, packVY: 0, packVR: 0, packVS: 0, mouthV: 0,
    cardVY: 0, cardVS: 0, flipV: 0, tiltVX: 0, tiltVY: 0,
    stripVX: 0, stripVY: 0, openAge: -1, faceAge: -1,
    revealX: CARD.insideX as number, revealY: CARD.peekY as number,
    revealTiltX: 0, revealTiltY: 0,
    collectY: CARD.revealY as number, collectScale: CARD.revealScale as number,
    pressAge: 0, tiltHomeAge: 1, newPack: false, events: [] as number[],
  };
  idlePose(state, 0);
  return state;
}
export type TearState = ReturnType<typeof initialTear>;

export function copyTear(state: TearState): TearState {
  'worklet';
  return { ...state, pose: { ...state.pose, tearLine: state.pose.tearLine.slice() }, events: [] };
}

function emit(s: TearState, event: number) {
  'worklet';
  if (!s.silent) s.events.push(event);
}

/** Solve the x coordinate of a cubic Bezier; no callback can be lifted out of this worklet. */
export function bezier(t: number, x1: number, y1: number, x2: number, y2: number): number {
  'worklet';
  if (t <= 0) return 0;
  if (t >= 1) return 1;
  let low = 0;
  let high = 1;
  let u = t;
  for (let i = 0; i < 18; i++) {
    const one = 1 - u;
    const x = 3 * one * one * u * x1 + 3 * one * u * u * x2 + u * u * u;
    if (x < t) low = u;
    else high = u;
    u = (low + high) / 2;
  }
  const one = 1 - u;
  return 3 * one * one * u * y1 + 3 * one * u * u * y2 + u * u * u;
}

function ease(t: number): number {
  'worklet';
  return bezier(clamp(t), 0.23, 1, 0.32, 1);
}
function hero(t: number): number {
  'worklet';
  return bezier(clamp(t), 0.77, 0, 0.175, 1);
}
function phase(s: TearState, next: TearPhase) {
  'worklet';
  s.phase = next;
  s.age = 0;
}

function openPack(s: TearState) {
  'worklet';
  const p = s.pose;
  p.tearFront = PACK.width;
  p.stripFree = 1;
  s.stripVX = clamp(s.vx * 0.5, 40, 320);
  s.stripVY = -260;
  p.burst = s.reduced ? 0.4 : 1;
  s.openAge = 0;
  s.coast = 0;
  s.autoTear = false;
  s.hold = 'none';
  phase(s, 'opened');
  emit(s, HAPTIC.open);
}

function advanceFront(s: TearState, goal: number, localY: number) {
  'worklet';
  const p = s.pose;
  const before = p.tearFront;
  const next = clamp(Math.max(before, goal), 0, PACK.width);
  if (next <= before) return;
  const wanted = TEAR.wander * Math.tanh((localY - TEAR.y - s.grabDy) / TEAR.wander);
  // Store actual column crossings, interpolating input height across the traversed segment.
  const end = clamp(wanted, s.lineEnd - 0.8 * (next - before), s.lineEnd + 0.8 * (next - before));
  for (let i = 0; i < TEAR.columns; i++) {
    const column = i * TEAR.step;
    if (column > before && column <= next) {
      const value = s.lineEnd + (end - s.lineEnd) * (column - before) / (next - before);
      p.tearLine[i] = clamp(value, p.tearLine[i - 1] - 0.8 * TEAR.step, p.tearLine[i - 1] + 0.8 * TEAR.step);
    } else if (column > next) p.tearLine[i] = end;
  }
  s.lineEnd = end;
  s.lineX = next;
  s.lineY = localY;
  p.tearFront = next;
  if (!s.advanced) {
    s.advanced = true;
    emit(s, HAPTIC.grab);
  }
  for (let tooth = Math.floor(before / MOTION.tearTick) + 1; tooth <= Math.floor(next / MOTION.tearTick); tooth++) emit(s, HAPTIC.tooth);
  if (next > 6) p.leak = 0.15 + 0.85 * next / PACK.width;
  if (next >= PACK.width - 2) openPack(s);
}

function reveal(s: TearState) {
  'worklet';
  s.revealX = s.pose.cardX;
  s.revealY = s.pose.cardY;
  s.revealTiltX = s.pose.cardTiltX;
  s.revealTiltY = s.pose.cardTiltY;
  s.packVY = -60;
  s.faceAge = -1;
  s.hold = 'none';
  phase(s, 'revealing');
  emit(s, HAPTIC.reveal);
}
function face(s: TearState) {
  'worklet';
  if (s.faceAge >= 0) return;
  s.faceAge = 0;
  s.pose.sparkleAge = s.reduced ? -1 : 0;
  if (!s.reduced) {
    s.cardVS += 0.9;
    s.stageKick += 0.03;
    s.pose.swayX += 0.03;
  }
  emit(s, HAPTIC.face);
}
function collect(s: TearState) {
  'worklet';
  s.collectY = s.pose.cardY;
  s.collectScale = s.pose.cardScale;
  s.newPack = false;
  s.hold = 'none';
  if (!s.reduced) {
    s.stageKick -= 0.02;
    s.pose.swayX -= 0.02;
  }
  phase(s, 'collecting');
  emit(s, HAPTIC.collect);
}

/** Input kernel shared by the native gesture, accessibility and demo. Mutates an owned copy. */
export function inputTear(s: TearState, input: TearInput) {
  'worklet';
  const p = s.pose;
  if (input.type === 'activate') {
    s.silent = false;
    if (s.phase === 'sealed' || s.phase === 'tearing') {
      s.autoTear = true;
      s.grabDy = -s.lineEnd;
      s.advanced = false;
      phase(s, 'tearing');
    } else if (s.phase === 'peek') reveal(s);
    else if (s.phase === 'revealed') collect(s);
    return;
  }
  if (input.type === 'down') {
    s.down = true;
    s.downAt = s.clock;
    s.downX = s.fingerX = input.x;
    s.downY = s.fingerY = input.y;
    s.vx = s.vy = s.travel = 0;
    s.silent = input.silent ?? false;
    s.advanced = false;
    s.autoTear = false;
    s.hold = 'none';
    const frontX = PACK.x + p.tearFront;
    if ((s.phase === 'sealed' || s.phase === 'tearing') && input.y >= PACK.y - 24 && input.y <= PACK.y + 96
      && input.x >= frontX - 44 && input.x <= frontX + 64) {
      s.hold = 'tear';
      s.coast = 0;
      s.grabDy = input.y - PACK.y - TEAR.y - s.lineEnd;
      s.catching = input.x - PACK.x > p.tearFront + 0.01;
      phase(s, 'tearing');
    } else if (s.phase === 'peek' && Math.abs(input.x - p.cardX) <= CARD.width / 2 + 12
      && input.y >= p.cardY - CARD.height / 2 - 12
      && input.y <= Math.min(p.cardY + CARD.height / 2, PACK.y + TEAR.y + p.packY) + 12) {
      s.hold = 'card';
      s.pullStart = p.cardY;
      s.returning = true;
      s.cardVY = 0;
    } else if (s.phase === 'revealed' && Math.abs(input.x - p.cardX) <= CARD.width * p.cardScale / 2 + 12
      && Math.abs(input.y - p.cardY) <= CARD.height * p.cardScale / 2 + 12) {
      s.hold = 'tilt';
      s.pressAge = 0;
      s.tiltVX = s.tiltVY = 0;
    } else if (input.x >= FRAME.x && input.x <= FRAME.x + FRAME.width
      && input.y >= FRAME.y && input.y <= FRAME.y + FRAME.height) s.hold = 'look';
    return;
  }
  if (!s.down) return;
  if (input.type === 'move') {
    s.fingerX = input.x;
    s.fingerY = input.y;
    s.vx = input.vx;
    s.vy = input.vy;
    const dx = input.x - s.downX;
    const dy = input.y - s.downY;
    s.travel = Math.max(s.travel, Math.hypot(dx, dy));
    if (s.hold === 'tear' && !s.catching) advanceFront(s, input.x - PACK.x, input.y - PACK.y);
    else if (s.hold === 'card') {
      const proposed = s.pullStart + dy;
      p.cardY = proposed < CARD.peekY - 48 ? CARD.peekY - 48 - 24 * Math.tanh((CARD.peekY - 48 - proposed) / 24)
        : proposed > CARD.peekY ? CARD.peekY + 10 * Math.tanh((proposed - CARD.peekY) / 10) : proposed;
    } else if (s.hold === 'tilt' && !s.reduced) {
      p.cardTiltY = clamp(dx / 110, -0.32, 0.32);
      p.cardTiltX = clamp(-dy / 140, -0.26, 0.26);
    }
    return;
  }
  s.down = false;
  s.vx = input.cancelled ? 0 : input.vx;
  s.vy = input.cancelled ? 0 : input.vy;
  const tap = !input.cancelled && s.travel < MOTION.tapTravel && s.clock - s.downAt < MOTION.tapTime;
  if (s.hold === 'tear') {
    s.coast = !input.cancelled && s.vx > 900 && p.tearFront > PACK.width * 0.6 ? s.vx : 0;
  } else if (s.hold === 'card') {
    if (!input.cancelled && (CARD.peekY - p.cardY > 24 || s.vy < -500 || tap)) reveal(s);
    else s.cardVY = s.vy;
  } else if (s.hold === 'tilt') {
    s.tiltHomeAge = 0;
    s.tiltVX = clamp(-s.vy / 140, -3, 3);
    s.tiltVY = clamp(s.vx / 110, -3, 3);
    if (tap) collect(s);
  }
  s.hold = 'none';
}

export function actTear(state: TearState, input: TearInput): TearState {
  'worklet';
  const s = copyTear(state);
  inputTear(s, input);
  return s;
}

function freeStrip(s: TearState, dt: number) {
  'worklet';
  if (s.openAge < 0 || s.phase === 'collecting' || s.phase === 'sealed') return;
  const p = s.pose;
  s.openAge += dt;
  if (s.reduced) p.stripOpacity = 1 - clamp(s.openAge / 0.15);
  else if (p.stripOpacity > 0) {
    p.stripX += s.stripVX * dt;
    p.stripY += s.stripVY * dt + 1300 * dt * dt;
    s.stripVY += 2600 * dt;
    p.stripRot += 9 * dt;
    p.stripOpacity = 1 - clamp((p.stripY - 380) / 140);
  }
  p.burst = (s.reduced ? 0.4 : 1) * Math.exp(-s.openAge / 0.18);
  if (p.burst < 0.0001) p.burst = 0;
}

function tug(s: TearState, dt: number) {
  'worklet';
  const p = s.pose;
  const pulling = s.hold === 'tear';
  const progress = p.tearFront / PACK.width;
  const hanging = s.phase === 'tearing';
  const x = pulling ? 7 * Math.tanh(s.vx / 600) + 4 * progress : hanging ? 2 * progress : 0;
  const rotation = pulling ? 0.045 * Math.tanh(s.vx / 500) + 0.015 * progress : hanging ? 0.008 * progress : 0;
  if (s.reduced) {
    p.packX = 0;
    p.packRot = 0;
    return;
  }
  const offset = spring(p.packX, s.packVX, x, MOTION.tug.stiffness, MOTION.tug.damping, dt);
  const turn = spring(p.packRot, s.packVR, rotation, MOTION.tug.stiffness, MOTION.tug.damping, dt);
  p.packX = offset.position;
  s.packVX = offset.velocity;
  p.packRot = turn.position;
  s.packVR = turn.velocity;
}

function stepPeek(s: TearState, dt: number) {
  'worklet';
  const p = s.pose;
  p.dust = clamp(s.age / 0.4);
  p.tabGlow = 0.75 + (s.reduced ? 0 : 0.1 * Math.sin(2 * Math.PI * s.age / 1.2));
  p.leak += (0.8 - p.leak) * (1 - Math.exp(-dt / 0.12));
  if (s.reduced) {
    p.packY = PEEK_SINK;
    if (s.hold !== 'card') p.cardY = CARD.peekY;
    p.cardOpacity = clamp(s.age / 0.2);
  } else {
    p.cardOpacity = 1;
    const sink = spring(p.packY, s.packVY, PEEK_SINK, MOTION.sink.stiffness, MOTION.sink.damping, dt);
    p.packY = sink.position;
    s.packVY = sink.velocity;
    if (s.hold !== 'card') {
      const config = s.returning ? MOTION.pullReturn : MOTION.rise;
      const rise = spring(p.cardY, s.cardVY, CARD.peekY, config.stiffness, config.damping, dt);
      p.cardY = rise.position;
      s.cardVY = rise.velocity;
    }
    if (!s.peekSettled && !s.down && Math.abs(p.cardY - CARD.peekY) < 0.5 && Math.abs(s.cardVY) < 3) s.peekSettled = true;
    const alive = s.peekSettled && !s.down;
    s.peekLife = toward(s.peekLife, alive ? 1 : 0, dt / (alive ? MOTION.peekFadeIn : MOTION.peekFadeOut));
    p.cardTiltY = s.peekLife * 0.06 * Math.sin(2 * Math.PI * s.age / 2.6);
    p.cardTiltX = s.peekLife * 0.03 * Math.sin(2 * Math.PI * s.age / 3.4 + 1);
  }
  if (!s.peekHit && (s.reduced ? s.age >= 0.2 : p.cardY <= CARD.peekY)) {
    s.peekHit = true;
    emit(s, HAPTIC.peek);
  }
}

function stepReveal(s: TearState, dt: number) {
  'worklet';
  const p = s.pose;
  const t = s.age;
  p.leak = 0.8 * (1 - clamp(t / 0.25));
  if (s.reduced) {
    p.cardOpacity = t < 0.16 ? 1 - clamp(t / 0.16) : clamp((t - 0.16) / 0.2);
    p.packOpacity = 1 - clamp(t / 0.2);
    if (t >= 0.16) {
      p.cardX = CARD.insideX;
      p.cardY = CARD.revealY;
      p.cardScale = CARD.revealScale;
      p.cardFlip = Math.PI;
      face(s);
    }
    if (t >= 0.36) phase(s, 'revealed');
    return;
  }
  const extractedY = CARD.peekY - MOTION.extractLift;
  const progress = hero((t - MOTION.extractTime) / MOTION.revealTravel);
  p.cardX = s.revealX + (CARD.insideX - s.revealX) * progress;
  p.cardY = t < MOTION.extractTime ? s.revealY + (extractedY - s.revealY) * ease(t / MOTION.extractTime)
    : extractedY + (CARD.revealY - extractedY) * progress;
  const peekFade = 1 - clamp(t / MOTION.peekFadeOut);
  p.cardTiltY = s.revealTiltY * peekFade;
  p.cardTiltX = s.revealTiltX * peekFade - 0.1 * ease(t / 0.08) * (1 - ease((t - MOTION.extractTime) / 0.22));
  p.packY += s.packVY * dt + 1100 * dt * dt;
  s.packVY += 2200 * dt;
  p.packRot = -0.22 * ease(t / 0.6);
  p.packOpacity = Math.min(1 - clamp((p.packY - 120) / 140),
    1 - clamp((t - MOTION.extractTime) / MOTION.revealPackFade));
  // The card keeps its size until its foot is clear of the mouth; it grows with the turn.
  if (t >= MOTION.flipAt) {
    const scale = spring(p.cardScale, s.cardVS, CARD.revealScale, MOTION.scale.stiffness, MOTION.scale.damping, dt);
    p.cardScale = scale.position;
    s.cardVS = scale.velocity;
  }
  if (t >= MOTION.flipAt) {
    const turn = spring(p.cardFlip, s.flipV, Math.PI, MOTION.flip.stiffness, MOTION.flip.damping, dt);
    p.cardFlip = turn.position;
    s.flipV = turn.velocity;
    if (p.cardFlip >= Math.PI * 0.92) face(s);
  }
  p.lightX = -0.4 + 0.7 * clamp(p.cardFlip / Math.PI);
  if (t >= MOTION.revealDeadline - 1e-9 || (t > MOTION.extractTime + MOTION.revealTravel
    && Math.abs(p.cardFlip - Math.PI) < 0.005 && Math.abs(s.flipV) < 0.015)) {
    p.cardFlip = Math.PI;
    p.cardScale = CARD.revealScale;
    p.cardY = CARD.revealY;
    s.tiltHomeAge = 1;
    phase(s, 'revealed');
  }
}

function arrival(s: TearState, dt: number) {
  'worklet';
  if (s.faceAge < 0 || s.phase === 'collecting') return;
  const p = s.pose;
  s.faceAge += dt;
  const t = s.faceAge;
  p.frameHeat = clamp(t / (s.reduced ? 0.2 : 0.42));
  p.frameRing = s.reduced ? 1 : ease(t / 0.52);
  p.tabGlow = t < 0.9 ? 1 : 0.85;
  p.sparkleAge = s.reduced || t > 1.6 ? -1 : t;
  p.sheen = s.reduced ? -0.3 : -0.3 + 1.6 * clamp((t - 0.08) / 0.7);
}

function stepRevealed(s: TearState, dt: number) {
  'worklet';
  const p = s.pose;
  s.tiltHomeAge += dt;
  const fast = 2 * Math.PI * s.clock / MOTION.idleFast;
  const slow = 2 * Math.PI * s.clock / MOTION.idleSlow;
  p.cardY = CARD.revealY + (s.reduced ? 0 : s.idle * 3 * Math.sin(fast));
  if (s.hold === 'tilt') {
    s.pressAge += dt;
    p.cardScale = CARD.revealScale * (1 - 0.03 * ease(s.pressAge / 0.12));
  } else if (s.reduced) {
    p.cardTiltX = p.cardTiltY = 0;
    p.cardScale = CARD.revealScale;
  } else {
    const tx = s.idle * 0.05 * Math.sin(fast + 1);
    const ty = s.idle * 0.07 * Math.sin(slow);
    const x = spring(p.cardTiltX, s.tiltVX, tx, MOTION.tilt.stiffness, MOTION.tilt.damping, dt);
    const y = spring(p.cardTiltY, s.tiltVY, ty, MOTION.tilt.stiffness, MOTION.tilt.damping, dt);
    p.cardTiltX = x.position;
    p.cardTiltY = y.position;
    s.tiltVX = x.velocity;
    s.tiltVY = y.velocity;
    const scale = spring(p.cardScale, s.cardVS, CARD.revealScale, MOTION.scale.stiffness, MOTION.scale.damping, dt);
    p.cardScale = scale.position;
    s.cardVS = scale.velocity;
  }
}

function resetPack(s: TearState) {
  'worklet';
  const p = s.pose;
  p.tearFront = 0;
  p.tearLine = flatLine();
  s.lineEnd = 0;
  p.stripFree = p.stripX = p.stripY = p.stripRot = 0;
  p.stripOpacity = 1;
  p.mouth = p.leak = p.burst = 0;
  s.openAge = -1;
  p.packY = s.reduced ? 0 : 72;
  p.packScale = s.reduced ? 1 : 0.94;
  p.packRot = p.packX = 0;
  p.packOpacity = 0;
  s.packVY = s.packVS = s.packVX = s.packVR = 0;
  s.newPack = true;
}

function resetCard(s: TearState) {
  'worklet';
  const p = s.pose;
  p.cardX = CARD.insideX;
  p.cardY = CARD.insideY;
  p.cardScale = 1;
  p.cardFlip = p.cardTiltX = p.cardTiltY = p.cardOpacity = 0;
  p.sheen = -0.3;
  p.sparkleAge = -1;
}

function stepCollect(s: TearState, dt: number) {
  'worklet';
  const p = s.pose;
  const t = s.age;
  const departureEnd = s.reduced ? MOTION.newPackAt : 0.54;
  if (t < departureEnd) {
    if (s.reduced) p.cardOpacity = 1 - clamp(t / 0.2);
    else {
      const travel = hero((t - 0.08) / 0.46);
      p.cardY = s.collectY + (-260 - s.collectY) * travel;
      p.cardScale = t < 0.08 ? s.collectScale + (CARD.revealScale * 1.02 - s.collectScale) * ease(t / 0.08)
        : CARD.revealScale * (1.02 - 0.3 * travel);
      p.cardTiltX = 0.5 * travel;
      p.cardOpacity = 1 - clamp((t - 0.4) / 0.14);
      p.sheen = -0.3 + 1.6 * clamp(t / 0.38);
    }
  } else resetCard(s);
  // The incoming wrapper overlaps the outgoing card's last 160 ms; never truncate its fade.
  if (!s.newPack && t >= MOTION.newPackAt) resetPack(s);
  p.frameHeat = 1 - clamp((t - 0.2) / 0.6);
  p.tabGlow = 0.85 - 0.4 * clamp((t - 0.2) / 0.6);
  p.dust = 1 - clamp((t - 0.2) / 0.4);
  if (p.frameHeat === 0) p.frameRing = 0;
  if (s.newPack) {
    const age = t - MOTION.newPackAt;
    p.packOpacity = clamp(age / 0.22);
    if (!s.reduced) {
      const y = spring(p.packY, s.packVY, 0, MOTION.landing.stiffness, MOTION.landing.damping, dt);
      const scale = spring(p.packScale, s.packVS, 1, MOTION.landing.stiffness, MOTION.landing.damping, dt);
      p.packY = y.position;
      s.packVY = y.velocity;
      p.packScale = scale.position;
      s.packVS = scale.velocity;
    }
    if (t >= 1.1 && (s.reduced || (Math.abs(p.packY) < 0.1 && Math.abs(s.packVY) < 1))) {
      const clock = s.clock;
      const silent = s.silent;
      const events = s.events;
      const fresh = initialTear(s.reduced);
      Object.assign(s, fresh);
      s.clock = clock;
      s.age = 0;
      s.silent = silent;
      s.events = events;
      s.pose.calm = 0;
      s.idle = 0;
      emit(s, HAPTIC.land);
    }
  }
}

/** Fixed-step kernel. Caller owns the state; demo feeds its finger before every step. */
export function tickTear(s: TearState, dt: number) {
  'worklet';
  s.clock += dt;
  s.age += dt;
  const p = s.pose;
  idlePose(s, dt);
  if (s.phase === 'sealed' || s.phase === 'tearing') {
    p.cardOpacity = 0;
    tug(s, dt);
    if (s.autoTear) {
      s.vx = 420;
      advanceFront(s, p.tearFront + 420 * dt, TEAR.y);
    } else if (s.hold === 'tear' && s.catching) {
      const goal = clamp(s.fingerX - PACK.x, p.tearFront, PACK.width);
      advanceFront(s, Math.min(goal, p.tearFront + MOTION.catchSpeed * dt), s.fingerY - PACK.y);
      if (p.tearFront >= goal - 0.001) s.catching = false;
    } else if (s.coast > 0.1) {
      const decay = Math.exp(-dt / MOTION.coastTau);
      s.vx = s.coast;
      advanceFront(s, p.tearFront + s.coast * MOTION.coastTau * (1 - decay), s.lineY);
      s.coast *= decay;
    } else if (!s.down && p.tearFront > 6) p.leak = 0.15 + 0.85 * p.tearFront / PACK.width
      + (s.reduced ? 0 : 0.05 * Math.sin(2 * Math.PI * s.clock / 1.6));
  }
  freeStrip(s, dt);
  if (s.phase === 'opened' || s.phase === 'peek') {
    tug(s, dt);
    if (s.reduced) p.mouth = clamp(s.openAge / 0.2);
    else {
      const mouth = spring(p.mouth, s.mouthV, 1, MOTION.mouth.stiffness, MOTION.mouth.damping, dt);
      p.mouth = mouth.position;
      s.mouthV = mouth.velocity;
    }
    if (s.phase === 'opened' && s.openAge >= MOTION.peekDelay - 1e-9) phase(s, 'peek');
    if (s.phase === 'peek') stepPeek(s, dt);
  } else if (s.phase === 'revealing') stepReveal(s, dt);
  else if (s.phase === 'revealed') stepRevealed(s, dt);
  else if (s.phase === 'collecting') stepCollect(s, dt);
  arrival(s, dt);
}

export function stepTear(state: TearState, elapsed: number): TearState {
  'worklet';
  const s = copyTear(state);
  let remaining = clamp(elapsed, 0, MOTION.maxElapsed);
  while (remaining > 1e-9) {
    const dt = Math.min(remaining, MOTION.step);
    tickTear(s, dt);
    remaining -= dt;
  }
  return s;
}
