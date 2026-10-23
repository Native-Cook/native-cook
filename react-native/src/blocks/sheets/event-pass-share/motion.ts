export const LOOP = 8;
/**
 * Autoplay timeline (seconds in the 8 s loop). 0–0.8 and 6.9–8 rest (24 %); the foil rock is
 * the hero, then the turn to the QR pass and back, then a small nod that catches the glint.
 */
export const DEMO = {
  foilStart: 0.8,
  foilEnd: 3.0,
  /** Peak of sin(2πu)·sin(πu) is ±0.77, so ±8.5° about Y. */
  foilTiltY: 11,
  foilTiltX: -5,
  turnBackStart: 3.3,
  turnDuration: 0.75,
  turnFrontStart: 5.4,
  nodStart: 6.15,
  nodEnd: 6.9,
  nodTiltY: -4.5,
  nodTiltX: 1.5,
} as const;
export const PHASE_FRONT = 0;
export const PHASE_DRAGGING = 1;
export const PHASE_SETTLING = 2;
export const PHASE_TURNING_BACK = 3;
export const PHASE_BACK = 4;
export const PHASE_TURNING_FRONT = 5;
export const CLOCK_STOPPED = 0;
export const CLOCK_ANIMATION = 1;

export type EventPassPhase = 0 | 1 | 2 | 3 | 4 | 5;
export type ClockCadence = 0 | 1;

export type EventPassFrame = {
  phase: EventPassPhase;
  flipAngle: number;
  tiltX: number;
  tiltY: number;
  pressScale: number;
  frontOpacity: number;
  backOpacity: number;
  quietCrossfade: boolean;
  copyConfirmed: boolean;
};

export type EventPassMotionState = {
  autoPlay: boolean;
  quiet: boolean;
  foreground: boolean;
  visible: boolean;
  sharePresented: boolean;
  suspendedAt: number;
  demoOrigin: number;
  interruptedAt: number;
  resumeDemoAfterFront: boolean;
  pendingDemoRestart: boolean;
  phase: EventPassPhase;
  dragging: boolean;
  angle: number;
  tiltX: number;
  tiltY: number;
  angleTransitionActive: boolean;
  angleFrom: number;
  angleTo: number;
  angleStarted: number;
  angleDuration: number;
  interruptedTurnTarget: number;
  tiltSpringActive: boolean;
  springX: number;
  springY: number;
  springVelocityX: number;
  springVelocityY: number;
  springStarted: number;
  touchStarted: number;
  touchDown: boolean;
  interactionMaxDistance: number;
  pressReleaseAt: number;
  pressReleaseDepth: number;
  quietBack: boolean;
  quietMix: number;
  quietFrom: number;
  quietTo: number;
  quietStarted: number;
  copyConfirmedUntil: number;
};

export type EventPassSample = {
  state: EventPassMotionState;
  frame: EventPassFrame;
  cadence: ClockCadence;
};

export type LogicalClockSample = {
  clock: number;
  realSample: number;
};

const FLIP_DURATION = 0.75;
const QUIET_FADE_DURATION = 0.15;
const PRESS_DURATION = 0.09;
const SETTLE_DURATION = 0.7;
const RESUME_DELAY = 2;
const COPY_CONFIRMATION_DURATION = 1.5;

/**
 * The front face (it holds the foil's Skia canvas) never goes fully transparent while the pass shows
 * its back: at opacity 0 the canvas drops its surface and re-creating it on the turn back stalled the
 * UI thread for ~1 s once per loop. 0.004 is below one 8-bit step, behind the back face.
 */
export const FOIL_FACE_MIN_OPACITY = 0.004;

/**
 * Drag the sheet down by its grabber band to dismiss it: it follows the finger down, rubber-bands up,
 * and closes past a quarter of the screen or on a flick; otherwise it springs back.
 */
export const SHEET_DISMISS = {
  activeOffset: 8, // pt before the pan claims the touch
  commitShare: 0.25, // of the screen height
  commitVelocity: 800, // pt/s downwards
  rubber: 0.55, // share of an upward pull that moves the sheet…
  rubberFalloff: 120, // …falling off over this many pt
  spring: { duration: 300, dampingRatio: 1, overshootClamping: true },
  back: { duration: 400, dampingRatio: 0.8 },
  overshoot: 72, // pt below the screen when closed
} as const;

/** Rubber band for a pull of `over` pt past an edge (worklet). */
export function rubberBand(over: number): number {
  'worklet';
  return (over * SHEET_DISMISS.rubber) / (1 + over / SHEET_DISMISS.rubberFalloff);
}

export function clamp(value: number, lower = 0, upper = 1): number {
  'worklet';
  return Math.min(upper, Math.max(lower, value));
}

export function lerp(from: number, to: number, progress: number): number {
  'worklet';
  return from + (to - from) * clamp(progress);
}

export function advanceLogicalClock(clock: number, previousRealSample: number, realSample: number, foreground: boolean): LogicalClockSample {
  'worklet';
  return {
    clock: foreground && previousRealSample >= 0 ? clock + Math.max(0, realSample - previousRealSample) : clock,
    realSample,
  };
}

function bezier(t: number, p1: number, p2: number): number {
  'worklet';
  const inverse = 1 - t;
  return 3 * inverse * inverse * t * p1 + 3 * inverse * t * t * p2 + t * t * t;
}

export function cubicBezier(progress: number, x1: number, y1: number, x2: number, y2: number): number {
  'worklet';
  const x = clamp(progress);
  if (x === 0 || x === 1) return x;
  let low = 0;
  let high = 1;
  for (let index = 0; index < 18; index += 1) {
    const t = (low + high) * 0.5;
    if (bezier(t, x1, x2) < x) low = t;
    else high = t;
  }
  return bezier((low + high) * 0.5, y1, y2);
}

export function strongOut(progress: number): number {
  'worklet';
  return cubicBezier(progress, 0.23, 1, 0.32, 1);
}

export function strongInOut(progress: number): number {
  'worklet';
  return cubicBezier(progress, 0.77, 0, 0.175, 1);
}

export function springValue(elapsed: number, position: number, velocity: number, omega = 18, damping = 0.82): number {
  'worklet';
  if (elapsed <= 0) return position;
  const damped = omega * Math.sqrt(1 - damping * damping);
  const coefficient = (velocity + damping * omega * position) / damped;
  return Math.exp(-damping * omega * elapsed) *
    (position * Math.cos(damped * elapsed) + coefficient * Math.sin(damped * elapsed));
}

function running(state: EventPassMotionState): boolean {
  'worklet';
  return state.foreground && state.visible && !state.sharePresented;
}

function angleValue(state: EventPassMotionState, now: number): number {
  'worklet';
  if (!state.angleTransitionActive) return state.angle;
  const p = strongInOut((now - state.angleStarted) / Math.max(0.001, state.angleDuration));
  return lerp(state.angleFrom, state.angleTo, p);
}

function springTilt(state: EventPassMotionState, now: number): { x: number; y: number } {
  'worklet';
  if (!state.tiltSpringActive) return { x: state.tiltX, y: state.tiltY };
  const elapsed = Math.max(0, now - state.springStarted);
  return {
    x: springValue(elapsed, state.springX, state.springVelocityX),
    y: springValue(elapsed, state.springY, state.springVelocityY),
  };
}

function quietMixValue(state: EventPassMotionState, now: number): number {
  'worklet';
  if (state.quietStarted < 0) return state.quietMix;
  return lerp(state.quietFrom, state.quietTo, (now - state.quietStarted) / QUIET_FADE_DURATION);
}

function pressScale(state: EventPassMotionState, now: number): number {
  'worklet';
  if (state.touchDown && state.touchStarted >= 0) {
    return 1 - 0.03 * strongOut((now - state.touchStarted) / PRESS_DURATION);
  }
  if (state.pressReleaseAt >= 0) {
    const depth = Math.max(0, springValue(now - state.pressReleaseAt, state.pressReleaseDepth, 0, 17, 0.82));
    return 1 - depth;
  }
  return 1;
}

function startTurnInPlace(state: EventPassMotionState, target: number, now: number): void {
  'worklet';
  const current = angleValue(state, now);
  state.angle = current;
  const distance = Math.abs(target - current);
  if (distance <= 0.001) {
    state.angle = target;
    state.angleTransitionActive = false;
    return;
  }
  state.angleTransitionActive = true;
  state.angleFrom = current;
  state.angleTo = target;
  state.angleStarted = now;
  state.angleDuration = Math.max(0.12, FLIP_DURATION * distance / 180);
}

function releasePressInPlace(state: EventPassMotionState, now: number): void {
  'worklet';
  const currentScale = pressScale(state, now);
  state.touchDown = false;
  state.touchStarted = -1;
  state.pressReleaseDepth = Math.max(0, 1 - currentScale);
  state.pressReleaseAt = now;
}

function updateInteractionInPlace(state: EventPassMotionState, horizontal: number, vertical: number): void {
  'worklet';
  if (!state.dragging) return;
  state.interactionMaxDistance = Math.max(state.interactionMaxDistance, Math.hypot(horizontal, vertical));
  state.tiltY = clamp(horizontal / 269 * 10, -10, 10);
  state.tiltX = clamp(-vertical / 381 * 8, -8, 8);
}

function cancelInteractionInPlace(state: EventPassMotionState, now: number): void {
  'worklet';
  if (!state.dragging && !state.touchDown) return;
  state.dragging = false;
  releasePressInPlace(state, now);
  state.interactionMaxDistance = 0;
  state.interruptedAt = now;
  state.tiltSpringActive = true;
  state.springX = state.tiltX;
  state.springY = state.tiltY;
  state.springVelocityX = 0;
  state.springVelocityY = 0;
  state.springStarted = now;
  state.interruptedTurnTarget = -1;
  startTurnInPlace(state, state.angle >= 90 ? 180 : 0, now);
  state.phase = PHASE_SETTLING;
}

function updateSuspensionInPlace(state: EventPassMotionState, now: number): void {
  'worklet';
  if (!running(state)) {
    if (state.dragging || state.touchDown) cancelInteractionInPlace(state, now);
    if (state.suspendedAt < 0) state.suspendedAt = now;
    return;
  }
  if (state.suspendedAt < 0) return;
  const pause = Math.max(0, now - state.suspendedAt);
  state.suspendedAt = -1;
  if (state.demoOrigin >= 0) state.demoOrigin += pause;
  if (state.interruptedAt >= 0) state.interruptedAt += pause;
  if (state.touchStarted >= 0) state.touchStarted += pause;
  if (state.pressReleaseAt >= 0) state.pressReleaseAt += pause;
  if (state.quietStarted >= 0) state.quietStarted += pause;
  if (state.copyConfirmedUntil >= 0) state.copyConfirmedUntil += pause;
  if (state.angleTransitionActive) state.angleStarted += pause;
  if (state.tiltSpringActive) state.springStarted += pause;
  if (state.pendingDemoRestart && state.autoPlay && !state.quiet) {
    state.pendingDemoRestart = false;
    if (state.phase === PHASE_FRONT && Math.abs(state.angle) < 0.001 && !state.dragging &&
        !state.angleTransitionActive && !state.tiltSpringActive) {
      state.interruptedAt = -1;
      state.demoOrigin = now;
    } else {
      state.demoOrigin = -1;
      state.interruptedAt = now;
    }
  }
}

function toggleFaceInPlace(state: EventPassMotionState, now: number, quiet: boolean): void {
  'worklet';
  state.interruptedAt = now;
  state.demoOrigin = -1;
  state.resumeDemoAfterFront = false;
  if (quiet) {
    state.quiet = true;
    const current = quietMixValue(state, now);
    state.quietBack = !state.quietBack;
    state.quietFrom = current;
    state.quietTo = state.quietBack ? 1 : 0;
    state.quietStarted = now;
    state.angle = state.quietBack ? 180 : 0;
    state.phase = state.quietBack ? PHASE_BACK : PHASE_FRONT;
    return;
  }
  const current = angleValue(state, now);
  state.angle = current;
  let target: number;
  if (state.interruptedTurnTarget >= 0) target = state.interruptedTurnTarget >= 90 ? 0 : 180;
  else if (state.angleTransitionActive) target = state.angleTo >= 90 ? 0 : 180;
  else target = current >= 90 ? 0 : 180;
  state.interruptedTurnTarget = -1;
  startTurnInPlace(state, target, now);
  state.phase = target === 180 ? PHASE_TURNING_BACK : PHASE_TURNING_FRONT;
}

export function demoFrame(elapsed: number): EventPassFrame {
  'worklet';
  const remainder = elapsed % LOOP;
  const time = remainder >= 0 ? remainder : remainder + LOOP;
  let angle = 0;
  let tiltX = 0;
  let tiltY = 0;
  let phase: EventPassPhase = PHASE_FRONT;
  if (time >= DEMO.foilStart && time < DEMO.foilEnd) {
    // The pass rocks right then left under the light: the foil runs through its colours twice.
    // Both factors vanish at the ends, so the rock leaves and returns to rest with zero speed.
    const u = (time - DEMO.foilStart) / (DEMO.foilEnd - DEMO.foilStart);
    const bell = Math.sin(Math.PI * u);
    tiltY = DEMO.foilTiltY * Math.sin(2 * Math.PI * u) * bell;
    tiltX = DEMO.foilTiltX * bell * bell;
  } else if (time >= DEMO.turnBackStart && time < DEMO.turnBackStart + DEMO.turnDuration) {
    angle = 180 * strongInOut((time - DEMO.turnBackStart) / DEMO.turnDuration);
    phase = PHASE_TURNING_BACK;
  } else if (time >= DEMO.turnBackStart + DEMO.turnDuration && time < DEMO.turnFrontStart) {
    angle = 180;
    phase = PHASE_BACK;
  } else if (time >= DEMO.turnFrontStart && time < DEMO.turnFrontStart + DEMO.turnDuration) {
    angle = 180 * (1 - strongInOut((time - DEMO.turnFrontStart) / DEMO.turnDuration));
    phase = PHASE_TURNING_FRONT;
  } else if (time >= DEMO.nodStart && time < DEMO.nodEnd) {
    // A last small nod catches the glint once more before the rest.
    const bell = Math.sin(Math.PI * (time - DEMO.nodStart) / (DEMO.nodEnd - DEMO.nodStart));
    tiltY = DEMO.nodTiltY * bell * bell;
    tiltX = DEMO.nodTiltX * bell * bell;
  }
  const front = angle < 90 ? 1 : 0;
  return {
    phase,
    flipAngle: angle,
    tiltX,
    tiltY,
    pressScale: 1,
    frontOpacity: front,
    backOpacity: 1 - front,
    quietCrossfade: false,
    copyConfirmed: false,
  };
}

function advanceStateInPlace(state: EventPassMotionState, now: number): void {
  'worklet';
  if (state.copyConfirmedUntil >= 0 && now >= state.copyConfirmedUntil) state.copyConfirmedUntil = -1;
  if (state.quietStarted >= 0 && now - state.quietStarted >= QUIET_FADE_DURATION) {
    state.quietMix = state.quietTo;
    state.quietStarted = -1;
  }
  if (state.tiltSpringActive) {
    const settled = springTilt(state, now);
    state.tiltX = settled.x;
    state.tiltY = settled.y;
    if (now - state.springStarted >= SETTLE_DURATION) {
      state.tiltX = 0;
      state.tiltY = 0;
      state.tiltSpringActive = false;
    }
  }
  if (state.angleTransitionActive) {
    state.angle = angleValue(state, now);
    if (now - state.angleStarted >= state.angleDuration) {
      state.angle = state.angleTo;
      state.angleTransitionActive = false;
    }
  }
  if (state.phase === PHASE_SETTLING && !state.tiltSpringActive && !state.angleTransitionActive) {
    state.phase = state.angle >= 90 ? PHASE_BACK : PHASE_FRONT;
  } else if (state.phase === PHASE_TURNING_BACK && !state.angleTransitionActive) {
    state.phase = PHASE_BACK;
  } else if (state.phase === PHASE_TURNING_FRONT && !state.angleTransitionActive) {
    state.phase = PHASE_FRONT;
    if (state.resumeDemoAfterFront && state.autoPlay && !state.quiet) {
      state.resumeDemoAfterFront = false;
      state.interruptedAt = -1;
      state.demoOrigin = now;
    }
  }
  if (state.autoPlay && !state.quiet && !state.dragging && state.interruptedAt >= 0 && now - state.interruptedAt >= RESUME_DELAY) {
    if (state.phase === PHASE_FRONT && state.angle < 0.001 && !state.tiltSpringActive && !state.angleTransitionActive) {
      state.interruptedAt = -1;
      state.demoOrigin = now;
    } else if (!state.angleTransitionActive) {
      state.resumeDemoAfterFront = true;
      startTurnInPlace(state, 0, now);
      state.phase = PHASE_TURNING_FRONT;
    }
  }
}

function makeFrame(state: EventPassMotionState, now: number): EventPassFrame {
  'worklet';
  if (state.autoPlay && !state.quiet && state.demoOrigin >= 0 && state.interruptedAt < 0 && state.phase === PHASE_FRONT) {
    const demo = demoFrame(now - state.demoOrigin);
    demo.copyConfirmed = state.copyConfirmedUntil >= 0 && now < state.copyConfirmedUntil;
    return demo;
  }
  const currentAngle = angleValue(state, now);
  const currentTilt = springTilt(state, now);
  const mix = quietMixValue(state, now);
  const frontOpacity = state.quiet ? 1 - mix : currentAngle < 90 ? 1 : 0;
  const backOpacity = 1 - frontOpacity;
  return {
    phase: state.phase,
    flipAngle: state.quiet ? (state.quietBack ? 180 : 0) : currentAngle,
    tiltX: state.quiet ? 0 : currentTilt.x,
    tiltY: state.quiet ? 0 : currentTilt.y,
    pressScale: pressScale(state, now),
    frontOpacity,
    backOpacity,
    quietCrossfade: state.quiet,
    copyConfirmed: state.copyConfirmedUntil >= 0 && now < state.copyConfirmedUntil,
  };
}

function cadenceFor(state: EventPassMotionState, now: number): ClockCadence {
  'worklet';
  if (!running(state)) return CLOCK_STOPPED;
  if (state.autoPlay && !state.quiet && (state.demoOrigin >= 0 || state.interruptedAt >= 0)) return CLOCK_ANIMATION;
  if (state.dragging || state.touchDown || state.angleTransitionActive || state.tiltSpringActive || state.quietStarted >= 0) return CLOCK_ANIMATION;
  if (state.copyConfirmedUntil >= 0 && now < state.copyConfirmedUntil) return CLOCK_ANIMATION;
  return CLOCK_STOPPED;
}

export function sampleMotion(source: EventPassMotionState, now: number): EventPassSample {
  'worklet';
  const state = { ...source };
  if (state.suspendedAt < 0) advanceStateInPlace(state, now);
  const sampleTime = state.suspendedAt >= 0 ? state.suspendedAt : now;
  const frame = makeFrame(state, sampleTime);
  if (state.pressReleaseAt >= 0 && sampleTime - state.pressReleaseAt >= 0.55) state.pressReleaseAt = -1;
  return { state, frame, cadence: cadenceFor(state, sampleTime) };
}

export function createMotionState(now: number, autoPlay: boolean, quiet: boolean): EventPassMotionState {
  'worklet';
  return {
    autoPlay,
    quiet,
    foreground: true,
    visible: true,
    sharePresented: false,
    suspendedAt: -1,
    demoOrigin: autoPlay && !quiet ? now : -1,
    interruptedAt: -1,
    resumeDemoAfterFront: false,
    pendingDemoRestart: false,
    phase: PHASE_FRONT,
    dragging: false,
    angle: 0,
    tiltX: 0,
    tiltY: 0,
    angleTransitionActive: false,
    angleFrom: 0,
    angleTo: 0,
    angleStarted: now,
    angleDuration: 0.001,
    interruptedTurnTarget: -1,
    tiltSpringActive: false,
    springX: 0,
    springY: 0,
    springVelocityX: 0,
    springVelocityY: 0,
    springStarted: now,
    touchStarted: -1,
    touchDown: false,
    interactionMaxDistance: 0,
    pressReleaseAt: -1,
    pressReleaseDepth: 0,
    quietBack: false,
    quietMix: 0,
    quietFrom: 0,
    quietTo: 0,
    quietStarted: -1,
    copyConfirmedUntil: -1,
  };
}

export function setAutoPlay(source: EventPassMotionState, enabled: boolean, now: number): EventPassMotionState {
  'worklet';
  if (source.autoPlay === enabled) return source;
  const state = { ...source, autoPlay: enabled };
  if (!enabled) {
    state.demoOrigin = -1;
    state.interruptedAt = -1;
    state.resumeDemoAfterFront = false;
    state.pendingDemoRestart = false;
  } else if (!state.quiet) {
    if (running(state)) {
      if (state.phase === PHASE_FRONT && Math.abs(state.angle) < 0.001 && !state.dragging) state.demoOrigin = now;
      else state.interruptedAt = now;
    } else state.pendingDemoRestart = true;
  }
  return state;
}

export function setQuiet(source: EventPassMotionState, quiet: boolean, now: number): EventPassMotionState {
  'worklet';
  if (source.quiet === quiet) return source;
  const sampled = sampleMotion(source, now);
  const state = { ...sampled.state, quiet };
  const current = sampled.frame;
  state.demoOrigin = -1;
  state.interruptedAt = -1;
  state.resumeDemoAfterFront = false;
  state.pendingDemoRestart = false;
  state.touchDown = false;
  state.dragging = false;
  state.interactionMaxDistance = 0;
  state.tiltSpringActive = false;
  state.tiltX = 0;
  state.tiltY = 0;
  state.angleTransitionActive = false;
  if (quiet) {
    state.quietBack = current.flipAngle >= 90 || current.backOpacity > current.frontOpacity;
    state.quietMix = state.quietBack ? 1 : 0;
    state.angle = state.quietBack ? 180 : 0;
    state.phase = state.quietBack ? PHASE_BACK : PHASE_FRONT;
  } else {
    state.angle = state.quietBack ? 180 : 0;
    state.phase = state.quietBack ? PHASE_BACK : PHASE_FRONT;
    state.quietMix = state.quietBack ? 1 : 0;
    if (state.autoPlay) {
      if (running(state)) state.interruptedAt = now;
      else state.pendingDemoRestart = true;
    }
  }
  return state;
}

export function setForeground(source: EventPassMotionState, foreground: boolean, now: number): EventPassMotionState {
  'worklet';
  if (source.foreground === foreground) return source;
  const state = { ...source, foreground };
  updateSuspensionInPlace(state, now);
  return state;
}

export function setVisible(source: EventPassMotionState, visible: boolean, now: number): EventPassMotionState {
  'worklet';
  if (source.visible === visible) return source;
  const state = { ...source, visible };
  updateSuspensionInPlace(state, now);
  return state;
}

export function setSharePresented(source: EventPassMotionState, presented: boolean, now: number): EventPassMotionState {
  'worklet';
  if (source.sharePresented === presented) return source;
  const state = { ...source, sharePresented: presented };
  updateSuspensionInPlace(state, now);
  return state;
}

export function beginInteraction(source: EventPassMotionState, now: number): EventPassMotionState {
  'worklet';
  if (!running(source)) return source;
  const sampled = sampleMotion(source, now);
  const state = { ...sampled.state };
  state.angle = sampled.frame.flipAngle;
  state.tiltX = sampled.frame.tiltX;
  state.tiltY = sampled.frame.tiltY;
  state.demoOrigin = -1;
  state.interruptedAt = -1;
  state.resumeDemoAfterFront = false;
  state.interruptedTurnTarget = state.angleTransitionActive ? state.angleTo : -1;
  state.angleTransitionActive = false;
  state.tiltSpringActive = false;
  state.dragging = true;
  state.touchDown = true;
  state.touchStarted = now;
  state.pressReleaseAt = -1;
  state.interactionMaxDistance = 0;
  state.phase = PHASE_DRAGGING;
  return state;
}

export function updateInteraction(source: EventPassMotionState, horizontal: number, vertical: number): EventPassMotionState {
  'worklet';
  if (!source.dragging) return source;
  const state = { ...source };
  updateInteractionInPlace(state, horizontal, vertical);
  return state;
}

export function endInteraction(
  source: EventPassMotionState,
  horizontal: number,
  vertical: number,
  predictedHorizontal: number,
  predictedVertical: number,
  now: number,
  quiet: boolean,
): EventPassMotionState {
  'worklet';
  if (!source.dragging) return source;
  const state = { ...source };
  updateInteractionInPlace(state, horizontal, vertical);
  state.dragging = false;
  releasePressInPlace(state, now);
  state.interruptedAt = now;
  const wasTap = state.interactionMaxDistance < 8;
  state.interactionMaxDistance = 0;
  if (wasTap) {
    state.tiltX = 0;
    state.tiltY = 0;
    toggleFaceInPlace(state, now, quiet);
    return state;
  }
  state.interruptedTurnTarget = -1;
  state.tiltSpringActive = true;
  state.springX = state.tiltX;
  state.springY = state.tiltY;
  state.springVelocityX = clamp((-(predictedVertical - vertical) / 0.2) / 381 * 8, -80, 80);
  state.springVelocityY = clamp(((predictedHorizontal - horizontal) / 0.2) / 269 * 10, -100, 100);
  state.springStarted = now;
  startTurnInPlace(state, state.angle >= 90 ? 180 : 0, now);
  state.phase = PHASE_SETTLING;
  return state;
}

export function cancelInteraction(source: EventPassMotionState, now: number): EventPassMotionState {
  'worklet';
  const state = { ...source };
  cancelInteractionInPlace(state, now);
  return state;
}

export function toggleFace(source: EventPassMotionState, now: number, quiet: boolean): EventPassMotionState {
  'worklet';
  const state = { ...source };
  toggleFaceInPlace(state, now, quiet);
  return state;
}

export function confirmCopy(source: EventPassMotionState, now: number): EventPassMotionState {
  'worklet';
  return { ...source, copyConfirmedUntil: now + COPY_CONFIRMATION_DURATION };
}
