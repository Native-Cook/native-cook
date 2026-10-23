export const LOOP = 8;
export const QUICK_TAP_SECONDS = 0.16;
export const HOLD_SECONDS = 0.65;
export const MORPH_SECONDS = 0.8;
export const RETURN_SECONDS = 0.75;
export const REDUCED_FADE_SECONDS = 0.15;
export const GRASS_SECONDS = 8 * 60 * 60;
export const QUIET_SECONDS = 24 * 60 * 60;
export const CAROUSEL_SPACING = 249;
export const CAROUSEL_SETTLE_SECONDS = 0.65;

export const PHASE_IDLE = 0;
export const PHASE_PRESSING = 1;
export const PHASE_MORPHING = 2;
export const PHASE_ACTIVE = 3;
export const PHASE_RETURNING = 4;

export const CLOCK_STOPPED = 0;
export const CLOCK_ANIMATION = 1;
export const CLOCK_SECONDS = 2;

const CURVE_LINEAR = 0;
const CURVE_IN_OUT = 1;
const CURVE_OUT = 2;

export type GrassPhase = 0 | 1 | 2 | 3 | 4;
export type ClockCadence = 0 | 1 | 2;

export type RectFrame = {
  x: number;
  y: number;
  width: number;
  height: number;
  radius: number;
};

export type GrassFrame = {
  phase: GrassPhase;
  selectedCard: number;
  aperture: RectFrame;
  morph: number;
  pressProgress: number;
  chipScale: number;
  imageParallax: number;
  secondCardOffset: number;
  secondCardOpacity: number;
  copyOpacity: number;
  timerOpacity: number;
  ringOpacity: number;
  ringProgress: number;
  landingOpacity: number;
  remainingSeconds: number;
  activationSequence: number;
  carouselOffset: number;
  neighborCardX: number;
  selectionSequence: number;
};

export type MotionState = {
  autoPlay: boolean;
  quiet: boolean;
  phase: GrassPhase;
  selectedCard: number;
  fingerDown: boolean;
  activationSequence: number;
  selectionSequence: number;
  transitionFrom: number;
  transitionTo: number;
  transitionStarted: number;
  transitionDuration: number;
  transitionCurve: number;
  holdStarted: number;
  releaseStarted: number;
  releaseProgress: number;
  activeStarted: number;
  activeStartedValid: boolean;
  demoOrigin: number;
  interruptedAt: number;
  resumeDemoAfterReturn: boolean;
  carouselOffset: number;
  carouselDragging: boolean;
  carouselSpringActive: boolean;
  carouselSpringFrom: number;
  carouselSpringTo: number;
  carouselSpringVelocity: number;
  carouselSpringStarted: number;
};

export type SampleResult = {
  state: MotionState;
  frame: GrassFrame;
  cadence: ClockCadence;
};

export type LogicalClockSample = {
  clock: number;
  realSample: number;
};

const RESTING_CARD: RectFrame = { x: 14, y: 0, width: 232, height: 327, radius: 36 };
const TIMER_APERTURE: RectFrame = { x: 50, y: 50, width: 302, height: 302, radius: 151 };

export function clamp01(value: number): number {
  'worklet';
  return Math.min(1, Math.max(0, value));
}

export function clamp(value: number, lower: number, upper: number): number {
  'worklet';
  return Math.min(upper, Math.max(lower, value));
}

export function advanceLogicalClock(
  clock: number,
  previousRealSample: number,
  realSample: number,
  foreground: boolean,
): LogicalClockSample {
  'worklet';
  return {
    clock: foreground && previousRealSample >= 0
      ? clock + Math.max(0, realSample - previousRealSample)
      : clock,
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
  const x = clamp01(progress);
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

export function springBack(elapsed: number, initial: number): number {
  'worklet';
  if (elapsed <= 0) return initial;
  const omega = 15.7;
  const damping = 0.8;
  const damped = omega * Math.sqrt(1 - damping * damping);
  return Math.max(0, initial * Math.exp(-damping * omega * elapsed) *
    (Math.cos(damped * elapsed) + damping * omega / damped * Math.sin(damped * elapsed)));
}

function curveValue(kind: number, progress: number): number {
  'worklet';
  if (kind === CURVE_IN_OUT) return strongInOut(progress);
  if (kind === CURVE_OUT) return strongOut(progress);
  return clamp01(progress);
}

function transitionValue(state: MotionState, now: number): number {
  'worklet';
  const progress = clamp01((now - state.transitionStarted) / state.transitionDuration);
  return clamp01(state.transitionFrom + (state.transitionTo - state.transitionFrom) * curveValue(state.transitionCurve, progress));
}

function transitionFinished(state: MotionState, now: number): boolean {
  'worklet';
  return now - state.transitionStarted >= state.transitionDuration;
}

function setTransition(state: MotionState, from: number, to: number, started: number, duration: number, curve: number): void {
  'worklet';
  state.transitionFrom = from;
  state.transitionTo = to;
  state.transitionStarted = started;
  state.transitionDuration = Math.max(0.001, duration);
  state.transitionCurve = curve;
}

function interpolateRect(from: RectFrame, to: RectFrame, progress: number): RectFrame {
  'worklet';
  const p = clamp01(progress);
  return {
    x: from.x + (to.x - from.x) * p,
    y: from.y + (to.y - from.y) * p,
    width: from.width + (to.width - from.width) * p,
    height: from.height + (to.height - from.height) * p,
    radius: from.radius + (to.radius - from.radius) * p,
  };
}

function timerVisibility(morph: number): number {
  'worklet';
  return clamp01((morph - 0.6) / 0.4);
}

function durationForCard(selectedCard: number): number {
  'worklet';
  return selectedCard === 0 ? GRASS_SECONDS : QUIET_SECONDS;
}

function makeFrame(
  phase: GrassPhase,
  selectedCard: number,
  morph: number,
  press: number,
  timerVisible: number,
  elapsed: number,
  landing: number,
  activationSequence: number,
  carouselOffset: number,
  selectionSequence: number,
): GrassFrame {
  'worklet';
  const p = clamp01(morph);
  const duration = durationForCard(selectedCard);
  const remainingSeconds = Math.max(0, duration - Math.floor(elapsed));
  const remainingFraction = Math.max(0, 1 - elapsed / duration);
  return {
    phase,
    selectedCard,
    aperture: interpolateRect(RESTING_CARD, TIMER_APERTURE, p),
    morph: p,
    pressProgress: clamp01(press),
    chipScale: 0.97 + 0.03 * (1 - clamp01(press)),
    imageParallax: -4 * clamp01(press),
    secondCardOffset: 160 * p,
    secondCardOpacity: 1 - p,
    copyOpacity: (1 - clamp01(p / 0.4)) * (1 - timerVisible),
    timerOpacity: timerVisible,
    ringOpacity: p === 0 ? 0 : timerVisible,
    ringProgress: p === 0 ? 0 : remainingFraction,
    landingOpacity: landing,
    remainingSeconds,
    activationSequence,
    carouselOffset,
    neighborCardX: (selectedCard === 0 ? 263 : -235) + carouselOffset + (selectedCard === 0 ? 1 : -1) * 160 * p,
    selectionSequence,
  };
}

export function demoFrame(elapsed: number, selectedCard = 0): GrassFrame {
  'worklet';
  const remainder = elapsed % LOOP;
  const time = remainder >= 0 ? remainder : remainder + LOOP;
  let press = 0;
  let morph = 0;
  let phase: GrassPhase = PHASE_IDLE;
  let elapsedActive = 0;
  if (time < 1.6) {
    // Rest.
  } else if (time < 1.72) {
    phase = PHASE_PRESSING;
  } else if (time < 2.25) {
    press = (time - 1.72) / 0.53;
    phase = PHASE_PRESSING;
  } else if (time < 3.05) {
    press = 1;
    morph = strongInOut((time - 2.25) / 0.8);
    phase = PHASE_MORPHING;
  } else if (time < 6.45) {
    press = 1;
    morph = 1;
    phase = PHASE_ACTIVE;
    elapsedActive = time - 3.05;
  } else if (time < 7.2) {
    morph = 1 - strongOut((time - 6.45) / 0.75);
    phase = PHASE_RETURNING;
    elapsedActive = 3.4;
  }
  const anticipation = time >= 1.6 && time < 1.72 ? strongOut((time - 1.6) / 0.12) : 0;
  const frame = makeFrame(
    phase,
    selectedCard,
    morph,
    press,
    timerVisibility(morph),
    elapsedActive,
    phase === PHASE_ACTIVE ? Math.max(0, 1 - elapsedActive / 0.18) : 0,
    0,
    0,
    0,
  );
  frame.chipScale = 1 - anticipation * 0.03;
  return frame;
}

function carouselSpringValue(state: MotionState, now: number): number {
  'worklet';
  const elapsed = Math.max(0, now - state.carouselSpringStarted);
  const omega = 15.7;
  const damping = 0.8;
  const damped = omega * Math.sqrt(1 - damping * damping);
  const displacement = state.carouselSpringFrom - state.carouselSpringTo;
  const sineCoefficient = (state.carouselSpringVelocity + damping * omega * displacement) / damped;
  return state.carouselSpringTo + Math.exp(-damping * omega * elapsed) *
    (displacement * Math.cos(damped * elapsed) + sineCoefficient * Math.sin(damped * elapsed));
}

function carouselSpringVelocity(state: MotionState, now: number): number {
  'worklet';
  const elapsed = Math.max(0, now - state.carouselSpringStarted);
  const omega = 15.7;
  const damping = 0.8;
  const damped = omega * Math.sqrt(1 - damping * damping);
  const decay = damping * omega;
  const displacement = state.carouselSpringFrom - state.carouselSpringTo;
  const sineCoefficient = (state.carouselSpringVelocity + decay * displacement) / damped;
  const cosine = Math.cos(damped * elapsed);
  const sine = Math.sin(damped * elapsed);
  return Math.exp(-decay * elapsed) *
    (-decay * (displacement * cosine + sineCoefficient * sine) +
      (-displacement * damped * sine + sineCoefficient * damped * cosine));
}

function rubberBand(value: number): number {
  'worklet';
  const magnitude = Math.abs(value);
  const bent = magnitude * 0.42 / (1 + magnitude / 120);
  return value < 0 ? -bent : bent;
}

function carouselValue(state: MotionState, now: number): number {
  'worklet';
  if (!state.carouselSpringActive) return state.carouselOffset;
  const value = carouselSpringValue(state, now);
  const crossedLead = state.selectedCard === 0
    ? state.carouselSpringTo < 0 && value <= -CAROUSEL_SPACING
    : state.carouselSpringTo > 0 && value >= CAROUSEL_SPACING;
  if (crossedLead) {
    const shifted = value + (state.selectedCard === 0 ? CAROUSEL_SPACING : -CAROUSEL_SPACING);
    const velocity = carouselSpringVelocity(state, now);
    state.selectedCard = state.selectedCard === 0 ? 1 : 0;
    state.selectionSequence += 1;
    state.carouselOffset = shifted;
    state.carouselSpringFrom = shifted;
    state.carouselSpringTo = 0;
    state.carouselSpringVelocity = velocity;
    state.carouselSpringStarted = now;
    return shifted;
  }
  if (now - state.carouselSpringStarted >= CAROUSEL_SETTLE_SECONDS) {
    if (Math.abs(state.carouselSpringTo) > 0.5) {
      state.selectedCard = state.selectedCard === 0 ? 1 : 0;
      state.selectionSequence += 1;
    }
    state.carouselOffset = 0;
    state.carouselSpringActive = false;
    return 0;
  }
  state.carouselOffset = value;
  return value;
}

function pressProgress(state: MotionState, now: number): number {
  'worklet';
  if (state.holdStarted >= 0) return clamp01((now - state.holdStarted) / HOLD_SECONDS);
  if (state.releaseStarted >= 0) {
    const value = springBack(now - state.releaseStarted, state.releaseProgress);
    if (now - state.releaseStarted > 0.8) state.releaseStarted = -1;
    return value;
  }
  return 0;
}

function presentationMorph(state: MotionState, now: number): number {
  'worklet';
  if (state.autoPlay && !state.quiet && state.demoOrigin >= 0 && state.interruptedAt < 0 && state.phase === PHASE_IDLE) {
    return demoFrame(now - state.demoOrigin, state.selectedCard).morph;
  }
  return transitionValue(state, now);
}

function carouselIsAvailable(state: MotionState, now: number): boolean {
  'worklet';
  if (state.phase !== PHASE_IDLE) return false;
  if (state.autoPlay && !state.quiet && state.demoOrigin >= 0 && state.interruptedAt < 0) {
    const frame = demoFrame(now - state.demoOrigin, state.selectedCard);
    return frame.phase === PHASE_IDLE && frame.morph <= 0.001;
  }
  return transitionValue(state, now) <= 0.001;
}

function interruptDemo(state: MotionState, now: number): void {
  'worklet';
  state.demoOrigin = -1;
  state.interruptedAt = now;
  state.resumeDemoAfterReturn = false;
}

function activateInPlace(state: MotionState, now: number, reducedMotion: boolean): boolean {
  'worklet';
  if (state.phase === PHASE_ACTIVE || state.phase === PHASE_MORPHING) return false;
  const current = presentationMorph(state, now);
  state.fingerDown = false;
  interruptDemo(state, now);
  state.holdStarted = -1;
  state.releaseStarted = -1;
  setTransition(
    state,
    current,
    1,
    now,
    reducedMotion ? REDUCED_FADE_SECONDS : Math.max(0.12, (1 - current) * MORPH_SECONDS),
    reducedMotion ? CURVE_LINEAR : CURVE_IN_OUT,
  );
  state.activeStarted = now + state.transitionDuration;
  state.activeStartedValid = true;
  state.phase = reducedMotion ? PHASE_ACTIVE : PHASE_MORPHING;
  state.activationSequence += 1;
  return true;
}

function cancelPressInPlace(state: MotionState, now: number): void {
  'worklet';
  state.fingerDown = false;
  if (state.phase !== PHASE_PRESSING) return;
  const progress = pressProgress(state, now);
  const current = transitionValue(state, now);
  state.holdStarted = -1;
  state.releaseProgress = progress;
  state.releaseStarted = now;
  setTransition(state, current, 0, now, Math.max(0.12, current * RETURN_SECONDS), CURVE_OUT);
  state.phase = current > 0.001 ? PHASE_RETURNING : PHASE_IDLE;
}

function commitHoldInPlace(state: MotionState, now: number): void {
  'worklet';
  if (state.phase !== PHASE_PRESSING) return;
  const held = state.fingerDown;
  activateInPlace(state, now, false);
  state.fingerDown = held;
}

function cancelCarouselInPlace(state: MotionState, now: number): void {
  'worklet';
  if (!state.carouselDragging) return;
  state.carouselDragging = false;
  state.interruptedAt = now;
  state.carouselSpringActive = true;
  state.carouselSpringFrom = state.carouselOffset;
  state.carouselSpringTo = 0;
  state.carouselSpringVelocity = 0;
  state.carouselSpringStarted = now;
}

function beginAutomaticReturn(state: MotionState, now: number): void {
  'worklet';
  const current = transitionValue(state, now);
  state.activeStarted = -1;
  state.activeStartedValid = false;
  state.interruptedAt = -1;
  state.demoOrigin = -1;
  state.resumeDemoAfterReturn = true;
  setTransition(state, current, 0, now, Math.max(0.12, current * RETURN_SECONDS), CURVE_OUT);
  state.phase = PHASE_RETURNING;
}

function cadenceFor(state: MotionState, now: number, reducedMotion: boolean): ClockCadence {
  'worklet';
  if (reducedMotion && state.phase === PHASE_PRESSING) return CLOCK_STOPPED;
  if (state.carouselDragging || state.carouselSpringActive) return CLOCK_ANIMATION;
  const demoRunning = state.autoPlay && !reducedMotion && !state.quiet && state.phase === PHASE_IDLE &&
    (state.demoOrigin >= 0 || state.interruptedAt >= 0);
  if (demoRunning) return CLOCK_ANIMATION;
  const demoWaiting = state.autoPlay && !reducedMotion && !state.quiet && state.interruptedAt >= 0;
  if (demoWaiting) return CLOCK_ANIMATION;
  if (state.phase === PHASE_PRESSING || state.phase === PHASE_MORPHING || state.phase === PHASE_RETURNING ||
      state.fingerDown || state.releaseStarted >= 0 || !transitionFinished(state, now)) return CLOCK_ANIMATION;
  if (state.phase === PHASE_ACTIVE) {
    if (state.activeStartedValid && now - state.activeStarted < 0.18) return CLOCK_ANIMATION;
    const elapsed = state.activeStartedValid ? Math.max(0, now - state.activeStarted) : 0;
    return elapsed < durationForCard(state.selectedCard) ? CLOCK_SECONDS : CLOCK_STOPPED;
  }
  return CLOCK_STOPPED;
}

export function createMotionState(now: number, autoPlay: boolean, quiet: boolean): MotionState {
  'worklet';
  return {
    autoPlay,
    quiet,
    phase: PHASE_IDLE,
    selectedCard: 0,
    fingerDown: false,
    activationSequence: 0,
    selectionSequence: 0,
    transitionFrom: 0,
    transitionTo: 0,
    transitionStarted: now,
    transitionDuration: 0.001,
    transitionCurve: CURVE_LINEAR,
    holdStarted: -1,
    releaseStarted: -1,
    releaseProgress: 0,
    activeStarted: -1,
    activeStartedValid: false,
    demoOrigin: autoPlay && !quiet ? now : -1,
    interruptedAt: -1,
    resumeDemoAfterReturn: false,
    carouselOffset: 0,
    carouselDragging: false,
    carouselSpringActive: false,
    carouselSpringFrom: 0,
    carouselSpringTo: 0,
    carouselSpringVelocity: 0,
    carouselSpringStarted: now,
  };
}

export function setAutoPlay(state: MotionState, enabled: boolean, now: number): MotionState {
  'worklet';
  const next = { ...state, autoPlay: enabled };
  if (next.phase !== PHASE_IDLE || next.fingerDown || next.quiet) {
    if (!enabled) {
      next.demoOrigin = -1;
      next.resumeDemoAfterReturn = false;
    } else if (!next.quiet && (next.phase === PHASE_MORPHING || next.phase === PHASE_ACTIVE || next.phase === PHASE_RETURNING)) {
      next.interruptedAt = now;
    }
    return next;
  }
  next.demoOrigin = enabled ? now : -1;
  next.interruptedAt = -1;
  return next;
}

export function setQuiet(state: MotionState, quiet: boolean, now: number): MotionState {
  'worklet';
  if (state.quiet === quiet) return state;
  const next = { ...state };
  if (quiet) {
    if (next.fingerDown || next.phase === PHASE_PRESSING) cancelPressInPlace(next, now);
    if (next.carouselDragging) cancelCarouselInPlace(next, now);
    next.releaseStarted = -1;
    next.releaseProgress = 0;
    next.demoOrigin = -1;
    next.interruptedAt = -1;
    next.resumeDemoAfterReturn = false;
    const current = transitionValue(next, now);
    if (next.phase === PHASE_MORPHING) {
      setTransition(next, current, 1, now, REDUCED_FADE_SECONDS, CURVE_LINEAR);
      next.activeStarted = now + REDUCED_FADE_SECONDS;
      next.activeStartedValid = true;
    } else if (next.phase === PHASE_RETURNING) {
      setTransition(next, current, 0, now, REDUCED_FADE_SECONDS, CURVE_LINEAR);
    }
  } else if (next.autoPlay) {
    if (next.phase === PHASE_IDLE) {
      next.demoOrigin = now;
      next.interruptedAt = -1;
    } else if (next.phase === PHASE_MORPHING || next.phase === PHASE_ACTIVE || next.phase === PHASE_RETURNING) {
      next.interruptedAt = now;
    }
  }
  next.quiet = quiet;
  return next;
}

export function beginPress(state: MotionState, now: number): MotionState {
  'worklet';
  if (state.fingerDown) return state;
  const next = { ...state };
  const current = presentationMorph(next, now);
  next.fingerDown = true;
  interruptDemo(next, now);
  if (next.phase !== PHASE_IDLE && next.phase !== PHASE_RETURNING) {
    next.fingerDown = false;
    return next;
  }
  setTransition(next, current, current, now, 0.001, CURVE_LINEAR);
  next.holdStarted = now;
  next.releaseStarted = -1;
  next.phase = PHASE_PRESSING;
  return next;
}

export function endPress(state: MotionState, now: number, reducedMotion: boolean): MotionState {
  'worklet';
  if (!state.fingerDown) return state;
  const next = { ...state, fingerDown: false };
  if (reducedMotion) {
    if (next.phase === PHASE_PRESSING) activateInPlace(next, now, true);
    return next;
  }
  if (next.phase !== PHASE_PRESSING || next.holdStarted < 0) {
    if (next.autoPlay && (next.phase === PHASE_MORPHING || next.phase === PHASE_ACTIVE)) next.interruptedAt = now;
    return next;
  }
  const elapsed = Math.max(0, now - next.holdStarted);
  if (elapsed <= QUICK_TAP_SECONDS) activateInPlace(next, now, false);
  else if (elapsed >= HOLD_SECONDS) commitHoldInPlace(next, now);
  else cancelPressInPlace(next, now);
  return next;
}

export function cancelPress(state: MotionState, now: number): MotionState {
  'worklet';
  const next = { ...state };
  cancelPressInPlace(next, now);
  return next;
}

export function activate(state: MotionState, now: number, reducedMotion: boolean): MotionState {
  'worklet';
  const next = { ...state };
  activateInPlace(next, now, reducedMotion);
  return next;
}

export function stop(state: MotionState, now: number, reducedMotion: boolean): MotionState {
  'worklet';
  const next = { ...state };
  const current = presentationMorph(next, now);
  const visibleDemo = next.autoPlay && !next.quiet && next.phase === PHASE_IDLE &&
    next.demoOrigin >= 0 && next.interruptedAt < 0 && current > 0.001;
  if (next.phase !== PHASE_ACTIVE && next.phase !== PHASE_MORPHING && !visibleDemo) return state;
  next.fingerDown = false;
  interruptDemo(next, now);
  next.activeStarted = -1;
  next.activeStartedValid = false;
  setTransition(next, current, 0, now, reducedMotion ? REDUCED_FADE_SECONDS : Math.max(0.12, current * RETURN_SECONDS), reducedMotion ? CURVE_LINEAR : CURVE_OUT);
  next.phase = PHASE_RETURNING;
  return next;
}

export function beginCarousel(state: MotionState, now: number): MotionState {
  'worklet';
  if (!carouselIsAvailable(state, now) || state.fingerDown) return state;
  const next = { ...state };
  next.carouselOffset = carouselValue(next, now);
  next.carouselSpringActive = false;
  next.carouselDragging = true;
  interruptDemo(next, now);
  return next;
}

export function updateCarousel(state: MotionState, translation: number): MotionState {
  'worklet';
  if (!state.carouselDragging) return state;
  const next = { ...state };
  if (next.selectedCard === 0) {
    if (translation > 0) next.carouselOffset = rubberBand(translation);
    else if (translation < -CAROUSEL_SPACING) next.carouselOffset = -CAROUSEL_SPACING + rubberBand(translation + CAROUSEL_SPACING);
    else next.carouselOffset = translation;
  } else {
    if (translation < 0) next.carouselOffset = rubberBand(translation);
    else if (translation > CAROUSEL_SPACING) next.carouselOffset = CAROUSEL_SPACING + rubberBand(translation - CAROUSEL_SPACING);
    else next.carouselOffset = translation;
  }
  return next;
}

export function endCarousel(state: MotionState, translation: number, predictedTranslation: number, now: number): MotionState {
  'worklet';
  if (!state.carouselDragging) return state;
  const next = updateCarousel(state, translation);
  next.carouselDragging = false;
  next.interruptedAt = now;
  const commits = next.selectedCard === 0
    ? predictedTranslation < -CAROUSEL_SPACING * 0.45 || translation < -CAROUSEL_SPACING * 0.5
    : predictedTranslation > CAROUSEL_SPACING * 0.45 || translation > CAROUSEL_SPACING * 0.5;
  next.carouselSpringActive = true;
  next.carouselSpringFrom = next.carouselOffset;
  next.carouselSpringTo = commits ? (next.selectedCard === 0 ? -CAROUSEL_SPACING : CAROUSEL_SPACING) : 0;
  next.carouselSpringVelocity = clamp((predictedTranslation - translation) / 0.2, -1_600, 1_600);
  next.carouselSpringStarted = now;
  return next;
}

export function cancelCarousel(state: MotionState, now: number): MotionState {
  'worklet';
  const next = { ...state };
  cancelCarouselInPlace(next, now);
  return next;
}

export function suspendMotion(state: MotionState, now: number): MotionState {
  'worklet';
  let next = state;
  if (next.fingerDown || next.phase === PHASE_PRESSING) next = cancelPress(next, now);
  if (next.carouselDragging) next = cancelCarousel(next, now);
  return next;
}

export function resumeAfterBackground(state: MotionState, backgroundSeconds: number): MotionState {
  'worklet';
  if (state.phase !== PHASE_ACTIVE || !state.activeStartedValid) return state;
  return { ...state, activeStarted: state.activeStarted - Math.max(0, backgroundSeconds) };
}

export function sampleMotion(source: MotionState, now: number, reducedMotion: boolean): SampleResult {
  'worklet';
  const state = { ...source };
  const currentCarouselOffset = carouselValue(state, now);
  const resumeDue = !state.fingerDown && !state.carouselDragging && state.interruptedAt >= 0 && now - state.interruptedAt >= 2;
  if (state.autoPlay && !reducedMotion && !state.quiet && resumeDue) {
    if (state.phase === PHASE_IDLE) {
      state.demoOrigin = now;
      state.interruptedAt = -1;
    } else if (state.phase === PHASE_MORPHING || state.phase === PHASE_ACTIVE) {
      beginAutomaticReturn(state, now);
    }
  }
  if (state.autoPlay && !reducedMotion && !state.quiet && state.demoOrigin >= 0 && state.interruptedAt < 0 && state.phase === PHASE_IDLE) {
    return { state, frame: demoFrame(now - state.demoOrigin, state.selectedCard), cadence: CLOCK_ANIMATION };
  }
  if (state.phase === PHASE_PRESSING && !reducedMotion && !state.quiet && pressProgress(state, now) >= 1) commitHoldInPlace(state, now);
  const morph = transitionValue(state, now);
  if (state.phase === PHASE_MORPHING && morph >= 0.999) state.phase = PHASE_ACTIVE;
  if (state.phase === PHASE_RETURNING && morph <= 0.001) {
    state.phase = PHASE_IDLE;
    if (state.resumeDemoAfterReturn && state.autoPlay && !reducedMotion && !state.quiet) {
      state.resumeDemoAfterReturn = false;
      state.demoOrigin = now;
      state.interruptedAt = -1;
      return { state, frame: demoFrame(0, state.selectedCard), cadence: CLOCK_ANIMATION };
    }
  }
  const press = pressProgress(state, now);
  const elapsed = state.activeStartedValid ? Math.max(0, now - state.activeStarted) : 0;
  const frame = makeFrame(
    state.phase,
    state.selectedCard,
    reducedMotion ? 0 : morph,
    reducedMotion ? 0 : press,
    reducedMotion ? morph : timerVisibility(morph),
    elapsed,
    state.phase === PHASE_ACTIVE ? Math.max(0, 1 - elapsed / 0.18) : 0,
    state.activationSequence,
    currentCarouselOffset,
    state.selectionSequence,
  );
  return { state, frame, cadence: cadenceFor(state, now, reducedMotion) };
}
