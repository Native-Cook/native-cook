import { EVENT, M } from './medal-motion-constants';

export type Point = { x: number; y: number };
export type MedalMoment = { forged: boolean; worn: boolean; day: 0 | 1 | 2; engraved: boolean };
export type MotionConfig = {
  reduced: boolean;
  stage: { centre: Point; half: number };
  pivot: Point;
  engravePath?: number[][];
};
export type Turn = 'rest' | 'drag' | 'coast' | 'settle' | 'coin' | 'wobble';
export type MotionState = ReturnType<typeof initialMotion>;
export type MotionAction =
  | { type: 'touch'; x: number; y: number }
  | { type: 'move'; x: number; y: number; dx: number; dy: number }
  | { type: 'release'; vx: number; vy: number; cancelled?: boolean }
  | { type: 'day'; delta: 1 | -1 }
  | { type: 'textures'; revision: number }
  | { type: 'enter' | 'flip' | 'lap' | 'wear' | 'reset' | 'forge' | 'flick' | 'drift' };

export function initialMotion() {
  'worklet';
  return {
    forged: false, wearing: false, engraved: false, day: 0 as 0 | 1 | 2,
    mode: 'rest' as Turn, clock: 0, base: 0, yaw: 0, shown: 0, velocity: 0, spin: 0,
    pitch: M.pitch as number, roll: 0, target: 0,
    stiffness: M.settle.stiffness as number, damping: M.settle.damping as number,
    sway: 0, swayPhase: 0, idle: 0, hand: false, silent: false,
    down: false, blankTouch: false, downAt: 0, travel: 0, dx: 0, dy: 0,
    fingerX: 0, fingerY: 0, pressed: 0, pressFrom: 0, pressTarget: 0, pressTime: 0,
    fade: 1, fadeTime: -1,
    charge: 0, chargeVelocity: 0, hold: false, holdTime: 0, holdStart: 0, forgeTicks: 0,
    heat: 0, stamp: 0, struck: M.unstruck as number, recoil: 0, recoilVelocity: 0,
    sweep: M.sweep.to as number, sweepTime: M.sweep.duration as number,
    wobbleTime: 0, wobblePhase: 0, wobbleFrequency: 0, lastTick: M.unstruck as number,
    lightX: 0, lightY: 0, lightVX: 0, lightVY: 0,
    worn: 0, wearStart: 0, wearTime: 0, caught: false,
    theta: 0, thetaVelocity: 0, pullStart: 0, drop: 0, dropVelocity: 0,
    offsetX: 0, offsetY: 0, hangX: 0, hangVelocity: 0, nudge: 0, nudgeVelocity: 0,
    engrave: 0, engraveTime: 0, engraveLine: -1, cutting: false,
    morph: 0, morphTime: 0, morphPhase: 'idle' as 'idle' | 'prepare' | 'running' | 'copy' | 'commit',
    from: 0, to: 0, revision: 0, events: 0,
  };
}

export function clamp(value: number, low: number, high: number) {
  'worklet';
  return Math.max(low, Math.min(high, value));
}

export function nearestFace(angle: number) {
  'worklet';
  return Math.round(angle / Math.PI) * Math.PI;
}

function easeOut(value: number) {
  'worklet';
  return 1 - (1 - value) ** 3;
}

function easeInOut(value: number) {
  'worklet';
  return value < 0.5 ? 2 * value * value : 1 - (-2 * value + 2) ** 2 / 2;
}

// Exact damped oscillator integration keeps a free pendulum's energy decreasing at either frame rate.
export function oscillator(position: number, velocity: number, target: number, k: number, c: number, dt: number) {
  'worklet';
  const x = position - target;
  const decay = c / 2;
  const w = Math.sqrt(Math.max(M.epsilon, k - decay * decay));
  const envelope = Math.exp(-decay * dt);
  const cos = Math.cos(w * dt);
  const sin = Math.sin(w * dt);
  return {
    position: target + envelope * (x * cos + (velocity + decay * x) * sin / w),
    velocity: envelope * (velocity * cos - (decay * velocity + k * x) * sin / w),
  };
}

function critical(position: number, velocity: number, target: number, dt: number) {
  'worklet';
  const x = position - target;
  const b = velocity + M.light.rate * x;
  const decay = Math.exp(-M.light.rate * dt);
  return { position: target + (x + b * dt) * decay, velocity: (velocity - M.light.rate * b * dt) * decay };
}

function sound(s: MotionState, event: number) {
  'worklet';
  if (!s.silent) s.events |= event;
}

function absorb(s: MotionState) {
  'worklet';
  s.base = s.yaw;
  s.sway = 0;
  s.swayPhase = 0;
  s.idle = 0;
}

function spring(s: MotionState, goal: number, k: number, c: number, kick: number, hand: boolean) {
  'worklet';
  absorb(s);
  s.target = goal;
  s.stiffness = k;
  s.damping = c;
  s.velocity += kick;
  s.hand = hand;
  s.mode = 'settle';
}

function land(s: MotionState, reduced: boolean, coin = false) {
  'worklet';
  s.base = s.target;
  s.velocity = 0;
  s.mode = 'rest';
  s.idle = 0;
  s.wobbleFrequency = 0;
  if (!reduced && s.heat < M.sweep.heatLimit) s.sweepTime = 0;
  if (coin) sound(s, EVENT.rigid);
  else if (s.hand) sound(s, EVENT.land);
  s.hand = false;
}

function strike(s: MotionState, reduced: boolean) {
  'worklet';
  s.forged = true;
  s.hold = false;
  s.charge = 0;
  s.chargeVelocity = 0;
  s.struck = s.clock;
  s.recoil = reduced ? 0 : M.strike.pitch;
  s.recoilVelocity = 0;
  s.events |= EVENT.changed;
  sound(s, EVENT.success | EVENT.heavy);
  if (!s.down) s.mode = 'rest';
}

function flip(s: MotionState, config: MotionConfig, hand: boolean, drift = false) {
  'worklet';
  if (!s.forged) return;
  const goal = nearestFace(s.base) + Math.PI;
  if (config.reduced) {
    absorb(s);
    s.target = goal;
    s.velocity = 0;
    s.fadeTime = 0;
    s.mode = 'rest';
    if (hand) sound(s, EVENT.land);
  } else {
    spring(s, goal, drift ? M.drift.stiffness : M.settle.stiffness,
      drift ? M.drift.damping : M.settle.damping, drift ? 0 : M.flipKick, hand);
    if (s.caught) s.thetaVelocity += M.pendulum.flip;
  }
}

function changeDay(s: MotionState, delta: 1 | -1, config: MotionConfig) {
  'worklet';
  if (!s.forged || s.morphPhase !== 'idle') return;
  const day = clamp(s.day + delta, 0, M.lastDay);
  if (day === s.day) {
    if (!config.reduced) s.nudgeVelocity = -delta * M.nudge.points * Math.sqrt(M.nudge.stiffness);
    sound(s, EVENT.warning);
    return;
  }
  s.from = s.day;
  s.to = day;
  s.morph = 0;
  s.morphTime = 0;
  s.morphPhase = 'prepare';
  s.revision++;
  s.events |= EVENT.textures;
  sound(s, EVENT.soft);
}

function releaseTurn(s: MotionState, vx: number, config: MotionConfig) {
  'worklet';
  const velocity = clamp(vx * M.dragRatio, -M.maxVelocity, M.maxVelocity);
  s.velocity = velocity;
  s.hand = true;
  if (config.reduced) {
    s.target = nearestFace(s.base + velocity * M.coast.lookahead);
    s.velocity = 0;
    s.fadeTime = 0;
    s.mode = 'rest';
  } else if (Math.abs(velocity) > M.coin.threshold) {
    s.mode = 'coin';
  } else if (Math.abs(velocity) < M.coast.handoff) {
    spring(s, nearestFace(s.base + velocity * M.coast.lookahead), M.settle.stiffness, M.settle.damping, 0, true);
  } else {
    s.mode = 'coast';
  }
}

export function pendulumLength(config: MotionConfig) {
  'worklet';
  return Math.max(M.pendulum.minLength, config.stage.centre.y - config.pivot.y);
}

/** Reserve the tilted slab's projected width as well as its centre travel. */
export function pendulumLimit(config: MotionConfig) {
  'worklet';
  const reach = Math.max(0, config.stage.centre.x - config.stage.half * M.aspect - 24);
  return Math.min(M.pull.limit, Math.asin(clamp(reach /
    (pendulumLength(config) + config.stage.half + M.wear.drop), 0, 1)));
}

export function pendulumEnergy(s: MotionState, config: MotionConfig) {
  'worklet';
  return (s.thetaVelocity ** 2 + M.pendulum.gravity / pendulumLength(config) * s.theta ** 2) / 2;
}

export function actMotion(state: MotionState, action: MotionAction, config: MotionConfig): MotionState {
  'worklet';
  const s = { ...state, events: 0 };
  if (action.type === 'reset') {
    const fresh = initialMotion();
    fresh.clock = s.clock;
    fresh.revision = s.revision + 1;
    fresh.events = EVENT.changed | EVENT.textures;
    return fresh;
  }
  if (action.type === 'textures') {
    if (action.revision !== s.revision) return s;
    if (s.morphPhase === 'prepare' || s.morphPhase === 'copy') s.morphPhase = 'running';
    else if (s.morphPhase === 'commit') {
      s.morph = 0;
      s.morphPhase = 'idle';
    }
    return s;
  }
  if (action.type === 'touch') {
    absorb(s);
    s.mode = 'drag';
    s.velocity = 0;
    s.wobbleFrequency = 0;
    s.fadeTime = -1;
    s.fade = 1;
    s.down = true;
    s.blankTouch = !s.forged;
    s.downAt = s.clock;
    s.travel = s.dx = s.dy = 0;
    s.fingerX = action.x;
    s.fingerY = action.y;
    s.pullStart = s.theta;
    s.thetaVelocity = 0;
    s.hand = true;
    s.silent = false;
    s.hold = !s.forged;
    s.holdTime = 0;
    s.holdStart = s.charge;
    s.forgeTicks = 0;
  } else if (action.type === 'move') {
    if (!s.down) return s;
    s.fingerX = action.x;
    s.fingerY = action.y;
    s.dx += action.dx;
    s.dy += action.dy;
    s.travel += Math.abs(action.dx) + Math.abs(action.dy);
    if (s.blankTouch) {
      if (s.travel >= M.tap.travel) s.hold = false;
    } else if (s.caught && !config.reduced) {
      const angle = s.pullStart + s.dx / pendulumLength(config);
      const limit = pendulumLimit(config);
      s.theta = limit * Math.tanh(angle / Math.max(M.epsilon, limit));
    } else if (!s.wearing && !config.reduced) {
      // Sideways input has exactly the original ratio and no arbitration delay.
      s.base += action.dx * M.dragRatio;
      s.yaw = s.base;
    }
  } else if (action.type === 'release') {
    if (!s.down) return s;
    s.down = false;
    s.hold = false;
    if (s.blankTouch) {
      if (!s.forged && !action.cancelled && s.charge >= M.forge.release) strike(s, config.reduced);
      s.mode = 'rest';
      s.hand = false;
    } else {
      const vertical = Math.abs(s.dy) > Math.abs(s.dx)
        || (Math.abs(action.vy) > M.swipe.speed && Math.abs(action.vy) > Math.abs(action.vx));
      const swipe = vertical && (Math.abs(s.dy) > M.swipe.travel || Math.abs(action.vy) > M.swipe.speed);
      if (!action.cancelled && swipe) {
        changeDay(s, (Math.abs(s.dy) > M.swipe.travel ? s.dy : action.vy) < 0 ? 1 : -1, config);
        releaseTurn(s, 0, config);
      } else if (!action.cancelled && s.travel < M.tap.travel && s.clock - s.downAt < M.tap.time) {
        flip(s, config, true);
      } else if (s.wearing) {
        if (!config.reduced && s.caught && !action.cancelled) {
          const frequency = Math.sqrt(M.pendulum.gravity / pendulumLength(config));
          const limit = pendulumLimit(config);
          const remaining = frequency * Math.sqrt(Math.max(0, limit * limit - s.theta * s.theta));
          s.thetaVelocity = clamp(action.vx / pendulumLength(config), -remaining, remaining);
        }
        releaseTurn(s, 0, config);
      } else releaseTurn(s, action.cancelled ? 0 : action.vx, config);
    }
  } else if (action.type === 'day') {
    s.silent = false;
    changeDay(s, action.delta, config);
  } else if (action.type === 'wear') {
    if (!s.forged) return s;
    s.silent = false;
    s.wearing = !s.wearing;
    s.wearStart = s.worn;
    s.wearTime = 0;
    s.events |= EVENT.changed;
    if (!s.wearing) s.caught = false;
  } else if (action.type === 'forge') {
    if (s.forged || s.down) return s;
    absorb(s);
    s.mode = 'rest';
    s.silent = true;
    s.hold = true;
    s.holdTime = 0;
    s.holdStart = s.charge;
  } else if (action.type === 'flick') {
    if (!s.forged || s.down) return s;
    s.silent = true;
    absorb(s);
    // The same flick feeds the pendulum once the ribbon is carrying the medal's weight.
    if (s.caught) {
      if (!config.reduced) s.thetaVelocity += M.pendulum.kick * 2;
    } else releaseTurn(s, M.coin.demo / M.dragRatio, config);
  } else if (action.type === 'drift') {
    if (s.down) return s;
    s.silent = true;
    flip(s, config, false, true);
  } else if (action.type === 'flip' || action.type === 'lap') {
    if (!s.forged || s.down) return s;
    s.silent = false;
    if (action.type === 'flip') flip(s, config, true);
    else if (config.reduced) {
      absorb(s);
      s.target = nearestFace(s.base);
      s.velocity = 0;
      s.fadeTime = 0;
      s.mode = 'rest';
      sound(s, EVENT.land);
    } else spring(s, nearestFace(s.base) + 2 * Math.PI, M.lap.stiffness, M.lap.damping, M.lap.kick, true);
  } else if (action.type === 'enter') {
    // Reopening cannot resume a hold whose native recognizer was removed while dismissing.
    s.down = s.hold = s.blankTouch = false;
    s.charge = s.chargeVelocity = 0;
    s.fade = 1;
    s.fadeTime = -1;
    s.silent = false;
    absorb(s);
    s.base = s.yaw = s.shown = s.target = 0;
    s.velocity = s.spin = s.roll = 0;
    s.pitch = M.pitch;
    s.mode = 'rest';
    s.idle = 0;
  }
  return s;
}

export function engravingAt(time: number) {
  'worklet';
  const cutTotal = M.engrave.duration - (M.lines.length - 1) * M.engrave.pause;
  let weight = 0;
  for (const line of M.lines) weight += line[1] - line[0];
  let start = 0;
  for (let i = 0; i < M.lines.length; i++) {
    const [low, high] = M.lines[i];
    const duration = cutTotal * (high - low) / weight;
    if (time < start + duration) {
      return { progress: low + (high - low) * clamp((time - start) / duration, 0, 1), line: i, cutting: true };
    }
    if (time < start + duration + M.engrave.pause && i < M.lines.length - 1) {
      return { progress: high, line: i, cutting: false };
    }
    start += duration + M.engrave.pause;
  }
  return { progress: 1, line: M.lines.length - 1, cutting: false };
}

function stepTurn(s: MotionState, dt: number, reduced: boolean) {
  'worklet';
  const before = s.base;
  if (s.mode === 'coast' || s.mode === 'coin') {
    const coin = s.mode === 'coin';
    s.velocity *= Math.exp(-dt / (coin ? M.coin.tau : M.coast.tau));
    s.base += s.velocity * dt;
    if (Math.abs(s.velocity) < (coin ? M.coin.handoff : M.coast.handoff)) {
      s.target = nearestFace(s.base + (coin ? 0 : s.velocity * M.coast.lookahead));
      s.stiffness = M.settle.stiffness;
      s.damping = M.settle.damping;
      s.mode = coin ? 'wobble' : 'settle';
      s.wobbleTime = 0;
      s.wobblePhase = 0;
    }
  } else if (s.mode === 'settle' || s.mode === 'wobble') {
    // These are the reference's semi-implicit Euler turn equations and settling tolerances.
    s.velocity += (-s.stiffness * (s.base - s.target) - s.damping * s.velocity) * dt;
    s.base += s.velocity * dt;
    if (s.mode === 'settle' && Math.abs(s.velocity) < M.settle.speed
      && Math.abs(s.base - s.target) < M.settle.distance) land(s, reduced);
  }
  if (s.hand && Math.floor((s.base + Math.PI / 2) / Math.PI) !== Math.floor((before + Math.PI / 2) / Math.PI)
    && s.clock - s.lastTick > M.edgeTick) {
    s.lastTick = s.clock;
    sound(s, EVENT.tick);
  }
  if (s.mode === 'rest' && !reduced && !s.hold) {
    s.idle += dt;
    const goal = s.idle > M.sway.delay ? 1 : 0;
    s.sway += (goal - s.sway) * (1 - Math.exp(-dt / M.sway.rise));
    s.swayPhase += dt * 2 * Math.PI / M.sway.period;
  }
  s.yaw = s.base + s.sway * M.sway.amplitude * Math.sin(s.swayPhase);
  s.pitch = M.pitch + (reduced ? 0 : s.sway * M.sway.tip * Math.cos(s.swayPhase * 2));
  s.roll = 0;
  if (s.mode === 'wobble') {
    s.wobbleTime += dt;
    const progress = clamp(s.wobbleTime / M.wobble.duration, 0, 1);
    const previous = s.wobblePhase;
    s.wobbleFrequency = M.wobble.low + (M.wobble.high - M.wobble.low) * progress;
    s.wobblePhase += 2 * Math.PI * s.wobbleFrequency * dt;
    const taper = 1 - easeInOut(clamp((progress - M.wobble.taper) / (1 - M.wobble.taper), 0, 1));
    const onset = easeInOut(clamp(s.wobbleTime / M.wobble.rise, 0, 1));
    const amplitude = onset * M.wobble.amplitude * (M.wobble.low / s.wobbleFrequency) ** 2 * taper;
    s.pitch += amplitude * Math.sin(s.wobblePhase);
    s.roll += amplitude * Math.cos(s.wobblePhase);
    if (Math.floor((s.wobblePhase + Math.PI / 2) / Math.PI)
      !== Math.floor((previous + Math.PI / 2) / Math.PI) && s.clock - s.lastTick >= M.wobble.tick) {
      s.lastTick = s.clock;
      sound(s, EVENT.tick);
    }
    if (progress >= 1) {
      land(s, reduced, true);
      s.yaw = s.base;
      s.pitch = M.pitch;
      s.roll = 0;
    }
  }
}

function stepForge(s: MotionState, dt: number, reduced: boolean) {
  'worklet';
  if (s.hold && !s.forged) {
    s.holdTime += dt;
    const progress = clamp(s.holdTime / M.forge.duration, 0, 1);
    s.charge = s.holdStart + (1 - s.holdStart) * (1 - (1 - progress) ** M.forge.power);
    s.forgeTicks += dt * (M.forgeTick.low + (M.forgeTick.high - M.forgeTick.low) * s.charge);
    if (s.forgeTicks >= 1) {
      s.forgeTicks %= 1;
      sound(s, EVENT.tick);
    }
    if (s.holdTime + M.epsilon >= M.forge.duration) strike(s, reduced);
  } else if (!s.forged) {
    const cool = oscillator(s.charge, s.chargeVelocity, 0, M.forge.stiffness, M.forge.damping, dt);
    s.charge = Math.max(0, cool.position);
    s.chargeVelocity = s.charge === 0 ? 0 : cool.velocity;
  }
  const age = s.clock - s.struck;
  if (s.forged) {
    s.stamp = reduced ? 1 : easeOut(clamp(age / M.strike.stamp, 0, 1));
    s.heat = reduced ? 1 - clamp(age / M.reduced.strike, 0, 1)
      : age < M.strike.rise ? 1 - (1 - age / M.strike.rise) ** 2
        : (1 - clamp((age - M.strike.rise) / M.strike.cool, 0, 1)) ** 3;
  }
  const recoil = oscillator(s.recoil, s.recoilVelocity, 0, M.strike.stiffness, M.strike.damping, dt);
  s.recoil = reduced ? 0 : recoil.position;
  s.recoilVelocity = reduced ? 0 : recoil.velocity;
  s.pitch += s.recoil;
}

function stepWear(s: MotionState, dt: number, config: MotionConfig) {
  'worklet';
  s.wearTime += dt;
  const duration = config.reduced ? M.reduced.wear : s.wearing ? M.wear.in : M.wear.out;
  s.worn = s.wearStart + ((s.wearing ? 1 : 0) - s.wearStart) * clamp(s.wearTime / duration, 0, 1);
  if (s.wearing && !s.caught && s.worn >= M.wear.catch) {
    s.caught = true;
    if (!config.reduced) s.thetaVelocity += M.pendulum.kick;
    s.pullStart = s.theta - s.dx / pendulumLength(config);
  }
  const drop = oscillator(s.drop, s.dropVelocity, s.caught ? M.wear.drop : 0,
    M.wear.stiffness, M.wear.damping, dt);
  s.drop = config.reduced ? 0 : drop.position;
  s.dropVelocity = config.reduced ? 0 : drop.velocity;
  if (config.reduced) s.theta = s.thetaVelocity = 0;
  else if (!s.caught || !s.down) {
    const swing = oscillator(s.theta, s.thetaVelocity, 0,
      s.caught ? M.pendulum.gravity / pendulumLength(config) : M.wear.stiffness,
      s.caught ? M.pendulum.damping : M.wear.damping, dt);
    const limit = pendulumLimit(config);
    s.theta = clamp(swing.position, -limit, limit);
    s.thetaVelocity = Math.abs(swing.position) > limit ? 0 : swing.velocity;
  }
  const length = pendulumLength(config) + s.drop;
  const hang = oscillator(s.hangX, s.hangVelocity, s.caught ? config.pivot.x - config.stage.centre.x : 0,
    M.wear.stiffness, M.wear.damping, dt);
  s.hangX = config.reduced ? 0 : hang.position;
  s.hangVelocity = config.reduced ? 0 : hang.velocity;
  // Positive roll tips the top towards the pivot as the centre swings screen-right.
  s.offsetX = s.hangX + length * Math.sin(s.theta);
  s.offsetY = s.drop + length * (Math.cos(s.theta) - 1);
  s.roll += s.theta;
  const nudge = oscillator(s.nudge, s.nudgeVelocity, 0, M.nudge.stiffness, M.nudge.damping, dt);
  s.nudge = nudge.position;
  s.nudgeVelocity = nudge.velocity;
  s.offsetY += config.reduced ? 0 : s.nudge;
  if (!config.reduced && s.charge > 0) {
    const fast = s.clock * 2 * Math.PI * M.tremble.fast;
    const slow = s.clock * 2 * Math.PI * M.tremble.slow;
    const shake = (1 - M.tremble.blend) * Math.sin(fast) + M.tremble.blend * Math.sin(slow);
    const shakeY = Math.cos(fast) * Math.sin(slow);
    const magnitude = Math.max(1, Math.hypot(shake, shakeY));
    s.offsetX += M.tremble.points * s.charge * shake / magnitude;
    s.offsetY += M.tremble.points * s.charge * shakeY / magnitude;
    s.roll += M.tremble.roll * s.charge * shake;
  }
}

function stepEngrave(s: MotionState, dt: number, reduced: boolean) {
  'worklet';
  s.cutting = false;
  const back = Math.abs(Math.round(s.base / Math.PI) % 2) === 1;
  if (!s.forged || s.engraved || !back || s.mode !== 'rest' || s.fadeTime >= 0) return;
  s.engraveTime += dt;
  const cut = engravingAt(s.engraveTime);
  s.engrave = reduced ? clamp(s.engraveTime / M.reduced.engrave, 0, 1) : cut.progress;
  s.cutting = !reduced && cut.cutting;
  if (s.engraveLine !== cut.line) {
    s.engraveLine = cut.line;
    sound(s, EVENT.tick);
  }
  if (s.engraveTime + M.epsilon >= (reduced ? M.reduced.engrave : M.engrave.duration)) {
    s.engrave = 1;
    s.engraved = true;
    s.cutting = false;
    s.events |= EVENT.changed;
  }
}

function stepMorph(s: MotionState, dt: number, reduced: boolean) {
  'worklet';
  if (s.morphPhase !== 'running') return;
  s.morphTime += dt;
  const progress = clamp(s.morphTime / (reduced ? M.reduced.morph : M.morph.duration), 0, 1);
  s.morph = reduced ? (progress < 0.5 ? 0 : 1) : easeInOut(progress);
  if (reduced) s.fade = Math.abs(2 * progress - 1);
  if (s.day !== s.to && s.morph >= 0.5) {
    s.morph = reduced ? 1 : 0.5;
    if (reduced) s.fade = 0;
    s.morphTime = (reduced ? M.reduced.morph : M.morph.duration) * 0.5;
    s.day = s.to as 0 | 1 | 2;
    s.morphPhase = 'copy';
    s.revision++;
    s.events |= EVENT.changed | EVENT.textures;
  }
  if (!reduced) s.heat = Math.max(s.heat, M.morph.heat * Math.sin(Math.PI * progress));
  if (progress + M.epsilon >= 1) {
    s.morph = 1;
    s.day = s.to as 0 | 1 | 2;
    s.from = s.to;
    s.morphPhase = 'commit';
    s.revision++;
    s.events |= EVENT.textures | EVENT.changed;
    sound(s, EVENT.success);
  }
}

export function stepMotion(state: MotionState, seconds: number, config: MotionConfig): MotionState {
  'worklet';
  const s = { ...state, events: 0 };
  const elapsed = clamp(seconds, 0, M.maxFrame);
  const shown = s.shown;
  let remaining = elapsed;
  while (remaining > M.epsilon) {
    const dt = Math.min(M.step, remaining);
    remaining -= dt;
    s.clock += dt;
    stepTurn(s, dt, config.reduced);
    stepForge(s, dt, config.reduced);
    stepWear(s, dt, config);
    stepEngrave(s, dt, config.reduced);
    stepMorph(s, dt, config.reduced);
    if (s.fadeTime >= 0) {
      s.fadeTime += dt;
      if (s.fadeTime < M.reduced.out) s.fade = 1 - s.fadeTime / M.reduced.out;
      else {
        s.base = s.yaw = s.target;
        s.fade = clamp((s.fadeTime - M.reduced.out) / M.reduced.in, 0, 1);
        if (s.fade >= 1) s.fadeTime = -1;
      }
    }
    const pressTarget = s.down || s.hold ? 1 : 0;
    if (pressTarget !== s.pressTarget) {
      s.pressFrom = s.pressed;
      s.pressTarget = pressTarget;
      s.pressTime = 0;
    }
    s.pressTime += dt;
    const pressProgress = clamp(s.pressTime / (pressTarget ? M.press.in : M.press.out), 0, 1);
    s.pressed = s.pressFrom + (pressTarget - s.pressFrom) * easeInOut(pressProgress);
    let lx = s.down ? (s.fingerX - config.stage.centre.x - s.offsetX) / M.light.reach : 0;
    let ly = s.down ? (config.stage.centre.y + s.offsetY - s.fingerY) / M.light.reach : 0;
    const magnitude = Math.max(1, Math.hypot(lx, ly));
    lx /= magnitude;
    ly /= magnitude;
    const lightX = critical(s.lightX, s.lightVX, lx, dt);
    const lightY = critical(s.lightY, s.lightVY, ly, dt);
    s.lightX = lightX.position;
    s.lightY = lightY.position;
    s.lightVX = lightX.velocity;
    s.lightVY = lightY.velocity;
    s.sweepTime = Math.min(M.sweep.duration, s.sweepTime + dt);
    s.sweep = M.sweep.from + (M.sweep.to - M.sweep.from) * easeInOut(s.sweepTime / M.sweep.duration);
  }
  // Finger deltas arrive once per display frame, not once per physics substep.
  // Measuring here preserves the reference's reported drag speed at both refresh rates.
  if (elapsed > 0) {
    s.spin += ((s.yaw - shown) / elapsed - s.spin) * (1 - Math.exp(-elapsed / M.spinTau));
    s.shown = s.yaw;
  }
  return s;
}

export function medalMoment(s: MotionState): MedalMoment {
  'worklet';
  return { forged: s.forged, worn: s.wearing, day: s.day, engraved: s.engraved };
}

export function sparkSpot(s: MotionState, config: MotionConfig) {
  'worklet';
  let u = 0.5;
  let v = M.forgeSpot.foot + (M.forgeSpot.head - M.forgeSpot.foot) * s.charge;
  const path = config.engravePath;
  if (s.cutting && path && path.length > 0) {
    let next = 0;
    while (next < path.length - 1 && path[next][0] < s.engrave) next++;
    const a = path[Math.max(0, next - 1)];
    const b = path[next];
    const t = clamp((s.engrave - a[0]) / Math.max(M.epsilon, b[0] - a[0]), 0, 1);
    u = a[1] + (b[1] - a[1]) * t;
    v = a[2] + (b[2] - a[2]) * t;
  }
  const on = s.cutting ? M.engrave.spitting : s.charge;
  // The supplied back path is already viewer-oriented; a second mirror would write backwards.
  return {
    x: config.stage.centre.x + s.offsetX + (u - 0.5) * 2 * config.stage.half * M.aspect,
    y: config.stage.centre.y + s.offsetY + (v - 0.5) * 2 * config.stage.half,
    on: s.cutting && (!path || path.length === 0) ? 0 : on,
  };
}
