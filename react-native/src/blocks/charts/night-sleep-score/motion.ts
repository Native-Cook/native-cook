export type NightSleepScoreStage = 'awake' | 'rem' | 'core' | 'deep';
export type NightSleepScoreCadence = 'stopped' | 'animation';
export type NightSleepScoreSegment = Readonly<{ stage: NightSleepScoreStage; minutes: number }>;
/** One sampled frame. `night` darkens the sleep card into a sky (0 = the pin's light card), `reveal` is how much of the
 * aurora hypnogram and the night ring is drawn, `meteor` is a shooting star's progress (-1 when none). */
export type NightSleepScoreFrame = Readonly<{
  cursorProgress: number; selectionPresence: number; segmentIndex: number | null; isRest: boolean;
  night: number; reveal: number; meteor: number;
}>;
export type NightSleepScoreSettle = Readonly<{ from: number; to: number; startedAt: number }>;
export type NightSleepScoreScrubSnapshot = Readonly<{
  startedAt: number; selectedMinute: number | null; lastTouch: number | null;
  settle: NightSleepScoreSettle | null; quietFadeStarted: number | null;
}>;
export type NightSleepScoreState = Readonly<{
  autoPlay: boolean; quiet: boolean; active: boolean; visible: boolean; selectedMinute: number | null;
  fingerDown: boolean; appeared: boolean; origin: number; pausedAt: number | null; lastTouch: number | null;
  settle: NightSleepScoreSettle | null; quietFadeStarted: number | null; scrubSnapshot: NightSleepScoreScrubSnapshot | null;
}>;

export const NIGHT_SLEEP_SCORE_DATA = Object.freeze({
  windowStartMinute: 23 * 60 + 18, windowMinutes: 446, score: 84, readiness: 7,
  stages: ['awake', 'rem', 'core', 'deep'] as const,
  segments: [
    { stage: 'awake', minutes: 4 }, { stage: 'core', minutes: 12 }, { stage: 'deep', minutes: 7 }, { stage: 'core', minutes: 18 },
    { stage: 'deep', minutes: 12 }, { stage: 'core', minutes: 48 }, { stage: 'rem', minutes: 7 }, { stage: 'core', minutes: 7 },
    { stage: 'rem', minutes: 5 }, { stage: 'core', minutes: 24 }, { stage: 'awake', minutes: 2 }, { stage: 'core', minutes: 78 },
    { stage: 'deep', minutes: 9 }, { stage: 'core', minutes: 7 }, { stage: 'deep', minutes: 8 }, { stage: 'core', minutes: 10 },
    { stage: 'rem', minutes: 19 }, { stage: 'core', minutes: 64 }, { stage: 'awake', minutes: 2 }, { stage: 'core', minutes: 12 },
    { stage: 'rem', minutes: 32 }, { stage: 'core', minutes: 14 }, { stage: 'awake', minutes: 3 }, { stage: 'core', minutes: 18 },
    { stage: 'awake', minutes: 2 }, { stage: 'core', minutes: 21 }, { stage: 'awake', minutes: 1 },
  ] as const satisfies readonly NightSleepScoreSegment[],
});
export const NIGHT_SLEEP_SCORE_LOOP = 9;

export function clampNightSleepScore(value: number): number { 'worklet'; return Math.min(1, Math.max(0, value)); }

export function easeOutCubicNightSleepScore(value: number): number { 'worklet'; const x = clampNightSleepScore(value); return 1 - (1 - x) ** 3; }

export function easeInOutNightSleepScore(value: number): number { 'worklet'; const x = clampNightSleepScore(value); return x < .5 ? 4 * x ** 3 : 1 - ((-2 * x + 2) ** 3) / 2; }

export function segmentIndexAtNightSleepScore(minute: number): number {
  'worklet';
  const bounded = Math.min(NIGHT_SLEEP_SCORE_DATA.windowMinutes - .000001, Math.max(0, minute));
  let edge = 0;
  for (let i = 0; i < NIGHT_SLEEP_SCORE_DATA.segments.length; i += 1) { edge += NIGHT_SLEEP_SCORE_DATA.segments[i].minutes; if (bounded < edge) return i; }
  return NIGHT_SLEEP_SCORE_DATA.segments.length - 1;
}

export function segmentStartNightSleepScore(index: number): number {
  'worklet'; let sum = 0; const end = Math.max(0, Math.min(index, NIGHT_SLEEP_SCORE_DATA.segments.length));
  for (let i = 0; i < end; i += 1) sum += NIGHT_SLEEP_SCORE_DATA.segments[i].minutes;
  return sum;
}

export function clockTimeNightSleepScore(minute: number): string {
  'worklet'; const offset = Math.floor(Math.min(NIGHT_SLEEP_SCORE_DATA.windowMinutes, Math.max(0, minute)));
  const clock = (NIGHT_SLEEP_SCORE_DATA.windowStartMinute + offset) % 1440;
  const h = Math.floor(clock / 60).toString().padStart(2, '0'); const m = (clock % 60).toString().padStart(2, '0');
  return `${h}:${m}`;
}

export function rangeNightSleepScore(index: number): string {
  'worklet'; const start = segmentStartNightSleepScore(index);
  return `${clockTimeNightSleepScore(start)}–${clockTimeNightSleepScore(start + NIGHT_SLEEP_SCORE_DATA.segments[index].minutes)} overnight window`;
}

/** Autoplay timeline in seconds of the 9 s loop. Rest 0–1.0 and 8.05–9.0 (22 %) is the pin's light card. */
export const NIGHT_SLEEP_SCORE_TIMELINE = Object.freeze({
  duskStart: 1.0, duskEnd: 2.05, // the card darkens into a night sky
  presenceStart: 1.55, presenceEnd: 1.9, // playhead, readout and legend highlight appear
  sweepStart: 1.75, sweepEnd: 6.35, sweepRamp: .16, // playhead crosses 23:18 → 06:44; ramp = share of the sweep spent accelerating
  meteorStart: 3.3, meteorEnd: 4.0, // one shooting star in the deep of the night
  // Dawn brightens into the light card. It overlaps the playhead's last, slow 0.1 s so the end of the night never stalls.
  dayStart: 6.25, dayEnd: 8.05,
  presenceOutStart: 6.45, presenceOutEnd: 6.9,
});

/** C1-smooth 0→1 (zero velocity at both ends, peak speed 1.5× the average): the night front's wipes. */
export function smoothStepNightSleepScore(value: number): number {
  'worklet'; const x = clampNightSleepScore(value); return x * x * (3 - 2 * x);
}

/** C2-smooth 0→1 (zero velocity and acceleration at both ends). */
export function smootherStepNightSleepScore(value: number): number {
  'worklet'; const x = clampNightSleepScore(value); return x * x * x * (x * (x * 6 - 15) + 10);
}

/** ∫ smoothstep from 0 to x (= x³ − x⁴/2; 0.5 at x = 1). */
function rampIntegralNightSleepScore(x: number): number { 'worklet'; return x * x * x - x * x * x * x / 2; }

/** Playhead position for a sweep fraction: velocity eases in and out over `ramp` (smoothstep velocity), constant between. */
export function sweepNightSleepScore(value: number): number {
  'worklet'; const ramp = NIGHT_SLEEP_SCORE_TIMELINE.sweepRamp; const u = clampNightSleepScore(value); const peak = 1 / (1 - ramp);
  if (u < ramp) return peak * ramp * rampIntegralNightSleepScore(u / ramp);
  if (u > 1 - ramp) return 1 - peak * ramp * rampIntegralNightSleepScore((1 - u) / ramp);
  return peak * (u - ramp / 2);
}

export function frameNightSleepScore(elapsed: number): NightSleepScoreFrame {
  'worklet'; const remainder = elapsed % NIGHT_SLEEP_SCORE_LOOP; const t = remainder < 0 ? remainder + NIGHT_SLEEP_SCORE_LOOP : remainder;
  const T = NIGHT_SLEEP_SCORE_TIMELINE;
  if (t < T.duskStart || t >= T.dayEnd) return { cursorProgress: 0, selectionPresence: 0, segmentIndex: null, isRest: true, night: 0, reveal: 0, meteor: -1 };
  const night = t < T.dayStart ? smoothStepNightSleepScore((t - T.duskStart) / (T.duskEnd - T.duskStart)) : 1 - smoothStepNightSleepScore((t - T.dayStart) / (T.dayEnd - T.dayStart));
  const presence = t < T.presenceOutStart ? smootherStepNightSleepScore((t - T.presenceStart) / (T.presenceEnd - T.presenceStart)) : 1 - smootherStepNightSleepScore((t - T.presenceOutStart) / (T.presenceOutEnd - T.presenceOutStart));
  const progress = sweepNightSleepScore((t - T.sweepStart) / (T.sweepEnd - T.sweepStart));
  const meteor = t >= T.meteorStart && t < T.meteorEnd ? (t - T.meteorStart) / (T.meteorEnd - T.meteorStart) : -1;
  const segmentIndex = presence > 0 ? segmentIndexAtNightSleepScore(progress * NIGHT_SLEEP_SCORE_DATA.windowMinutes) : null;
  return { cursorProgress: progress, selectionPresence: presence, segmentIndex, isRest: false, night, reveal: progress, meteor };
}

/** Night falls from the top of the card and day returns from its bottom edge (the horizon): local darkness at a card height
 * `v` (0 top, 1 bottom) for the card's overall `night`. `width` is the soft front's height as a share of the card. */
export const NIGHT_SLEEP_SCORE_FRONT = Object.freeze({ width: .6 });
export function nightAtNightSleepScore(night: number, v: number): number {
  'worklet'; const w = NIGHT_SLEEP_SCORE_FRONT.width; return smootherStepNightSleepScore((night * (1 + w) - v) / w);
}

/** The readout's summary fades out over the first part of the selection presence and the detail in over the last part. */
export const NIGHT_SLEEP_SCORE_READOUT_SWAP = .45;

/** Tab bar lens: a spring slide that stretches 12 % on the way; press 0.97 in 120 ms, back in 100 ms. */
export const NIGHT_SLEEP_SCORE_TAB_MOTION = Object.freeze({
  lens: { duration: 400, dampingRatio: .8 }, stretch: 1.12, stretchMs: 120, settle: { duration: 300, dampingRatio: .7 },
  press: { scale: .97, inMs: 120, outMs: 100 },
});

/** Critically damped spring step (exact for any dt), used to ease the sky toward a touch-driven target. */
export const NIGHT_SLEEP_SCORE_CHASE = Object.freeze({ night: 9, reveal: 6.5, phase: 11, lockDistance: .002, lockVelocity: .05 });
export function chaseNightSleepScore(value: number, velocity: number, target: number, dt: number, omega: number): [number, number] {
  'worklet'; const delta = value - target; const decay = Math.exp(-omega * dt); const k = velocity + omega * delta;
  return [target + (delta + k * dt) * decay, (velocity - omega * k * dt) * decay];
}

export function selectedFrameNightSleepScore(minute: number, secondsSinceTouch: number | null, quiet: boolean, holdDuration: number | null): NightSleepScoreFrame {
  'worklet';
  const presence = quiet ? 1 : holdDuration !== null && secondsSinceTouch !== null && secondsSinceTouch > holdDuration
    ? 1 - easeInOutNightSleepScore((secondsSinceTouch - holdDuration) / .25) : 1;
  return { cursorProgress: clampNightSleepScore(minute / NIGHT_SLEEP_SCORE_DATA.windowMinutes), selectionPresence: presence,
    segmentIndex: segmentIndexAtNightSleepScore(minute), isRest: presence === 0, night: presence, reveal: 1, meteor: -1 };
}

export function initialNightSleepScoreState(): NightSleepScoreState {
  'worklet'; return { autoPlay: false, quiet: false, active: true, visible: true, selectedMinute: null, fingerDown: false,
    appeared: false, origin: 0, pausedAt: null, lastTouch: null, settle: null, quietFadeStarted: null, scrubSnapshot: null };
}

export function cancelScrubNightSleepScore(state: NightSleepScoreState, at: number): NightSleepScoreState {
  'worklet'; if (!state.fingerDown) return state; const snapshot = state.scrubSnapshot; const held = snapshot ? Math.max(0, (state.pausedAt ?? at) - snapshot.startedAt) : 0;
  return { ...state, fingerDown: false, selectedMinute: snapshot?.selectedMinute ?? null, lastTouch: snapshot?.lastTouch === null || snapshot?.lastTouch === undefined ? null : snapshot.lastTouch + held,
    settle: snapshot?.settle ? { ...snapshot.settle, startedAt: snapshot.settle.startedAt + held } : null,
    quietFadeStarted: snapshot?.quietFadeStarted === null || snapshot?.quietFadeStarted === undefined ? null : snapshot.quietFadeStarted + held,
    origin: snapshot?.selectedMinute == null ? state.origin + held : state.origin, scrubSnapshot: null };
}

export function setSuspensionNightSleepScore(state: NightSleepScoreState, at: number): NightSleepScoreState {
  'worklet'; const running = state.active && state.visible;
  if (!running) { let next = cancelScrubNightSleepScore(state, state.pausedAt ?? at); return next.pausedAt === null ? { ...next, pausedAt: at } : next; }
  if (state.pausedAt === null) return state; const pause = Math.max(0, at - state.pausedAt);
  return { ...state, pausedAt: null, origin: state.origin + pause, lastTouch: state.lastTouch === null ? null : state.lastTouch + pause,
    quietFadeStarted: state.quietFadeStarted === null ? null : state.quietFadeStarted + pause,
    settle: state.settle ? { ...state.settle, startedAt: state.settle.startedAt + pause } : null };
}

export function resetNightSleepScore(state: NightSleepScoreState, at: number): NightSleepScoreState {
  'worklet'; return { ...state, selectedMinute: null, fingerDown: false, lastTouch: null, settle: null, quietFadeStarted: null, scrubSnapshot: null, origin: state.pausedAt ?? at };
}

export function selectSegmentNightSleepScore(state: NightSleepScoreState, index: number, at: number): NightSleepScoreState {
  'worklet'; const time = state.pausedAt ?? at;
  return { ...state, settle: null, selectedMinute: segmentStartNightSleepScore(index), fingerDown: false, scrubSnapshot: null,
    lastTouch: state.autoPlay ? time : null, quietFadeStarted: state.quiet ? time : null };
}

export function selectStageNightSleepScore(state: NightSleepScoreState, stage: NightSleepScoreStage, at: number): NightSleepScoreState {
  'worklet'; let first = -1; let next = -1; let current = state.selectedMinute === null ? null : segmentIndexAtNightSleepScore(state.selectedMinute);
  for (let i = 0; i < NIGHT_SLEEP_SCORE_DATA.segments.length; i += 1) if (NIGHT_SLEEP_SCORE_DATA.segments[i].stage === stage) { if (first < 0) first = i; if (current !== null && i > current && next < 0) next = i; }
  if (first < 0) return state; const index = current !== null && NIGHT_SLEEP_SCORE_DATA.segments[current].stage === stage ? (next < 0 ? first : next) : first;
  return selectSegmentNightSleepScore(state, index, at);
}

export function adjustStageNightSleepScore(state: NightSleepScoreState, forward: boolean, at: number): NightSleepScoreState {
  'worklet'; let currentStage: NightSleepScoreStage | null = state.selectedMinute === null ? null : NIGHT_SLEEP_SCORE_DATA.segments[segmentIndexAtNightSleepScore(state.selectedMinute)].stage;
  let current = currentStage === null ? (forward ? -1 : NIGHT_SLEEP_SCORE_DATA.stages.length) : NIGHT_SLEEP_SCORE_DATA.stages.indexOf(currentStage);
  const next = (current + (forward ? 1 : -1) + NIGHT_SLEEP_SCORE_DATA.stages.length) % NIGHT_SLEEP_SCORE_DATA.stages.length;
  return selectStageNightSleepScore(state, NIGHT_SLEEP_SCORE_DATA.stages[next], at);
}

export function updateScrubNightSleepScore(state: NightSleepScoreState, minute: number): NightSleepScoreState {
  'worklet'; return state.fingerDown ? { ...state, selectedMinute: Math.min(NIGHT_SLEEP_SCORE_DATA.windowMinutes - .000001, Math.max(0, minute)) } : state;
}

export function beginScrubNightSleepScore(state: NightSleepScoreState, minute: number, at: number): NightSleepScoreState {
  'worklet'; if (state.fingerDown) return updateScrubNightSleepScore(state, minute);
  const time = state.pausedAt ?? at; const snapshot: NightSleepScoreScrubSnapshot = { startedAt: time, selectedMinute: state.selectedMinute,
    lastTouch: state.lastTouch, settle: state.settle, quietFadeStarted: state.quietFadeStarted };
  return { ...state, scrubSnapshot: snapshot, settle: null, quietFadeStarted: state.quiet ? time : null,
    selectedMinute: Math.min(NIGHT_SLEEP_SCORE_DATA.windowMinutes - .000001, Math.max(0, minute)), fingerDown: true, lastTouch: null };
}

export function endScrubNightSleepScore(state: NightSleepScoreState, at: number): NightSleepScoreState {
  'worklet'; if (!state.fingerDown || state.selectedMinute === null) return state; const time = state.pausedAt ?? at; const minute = state.selectedMinute;
  let next: NightSleepScoreState = { ...state, fingerDown: false, scrubSnapshot: null, lastTouch: time };
  if (state.quiet) return { ...next, settle: null, quietFadeStarted: state.quietFadeStarted ?? time };
  const index = segmentIndexAtNightSleepScore(minute); const midpoint = segmentStartNightSleepScore(index) + NIGHT_SLEEP_SCORE_DATA.segments[index].minutes / 2;
  return Math.abs(midpoint - minute) > .01 ? { ...next, settle: { from: minute, to: midpoint, startedAt: time }, selectedMinute: midpoint } : next;
}

export function cadenceNightSleepScore(state: NightSleepScoreState, at: number): NightSleepScoreCadence {
  'worklet'; if (!state.active || !state.visible) return 'stopped'; const time = state.pausedAt ?? at;
  if (state.fingerDown) { if (!state.quiet) return 'animation'; if (state.quietFadeStarted !== null && time - state.quietFadeStarted < .12) return 'animation'; return 'stopped'; }
  if (state.settle !== null) return 'animation';
  if (state.quiet && state.quietFadeStarted !== null && state.selectedMinute !== null && time - state.quietFadeStarted < .12) return 'animation';
  return state.autoPlay && !state.quiet ? 'animation' : 'stopped';
}

export function frameControllerNightSleepScore(state: NightSleepScoreState, at: number): { state: NightSleepScoreState; frame: NightSleepScoreFrame } {
  'worklet'; const time = state.pausedAt ?? at; const target = state.selectedMinute;
  if (target !== null) {
    let minute = target; let nextState = state;
    if (state.settle !== null) { const s = state.settle; const elapsed = Math.min(1, Math.max(0, time - s.startedAt)); const mass = .8; const damping = 34; const stiffness = 460;
      const decay = damping / (2 * mass); const angular = Math.sqrt(stiffness / mass - decay * decay); const offset = s.from - s.to;
      minute = s.to + Math.exp(-decay * elapsed) * (offset * Math.cos(angular * elapsed) + decay * offset / angular * Math.sin(angular * elapsed));
      if (time - s.startedAt >= .8) nextState = { ...nextState, settle: null };
    }
    let presence: number;
    if (state.quiet) presence = easeOutCubicNightSleepScore((time - (state.quietFadeStarted ?? time - .12)) / .12);
    else if (state.autoPlay && !state.fingerDown && state.lastTouch !== null && time - state.lastTouch > 2) {
      presence = 1 - easeInOutNightSleepScore((time - state.lastTouch - 2) / .25);
      if (presence <= .000001) { const origin = state.lastTouch + 2.25; nextState = { ...nextState, selectedMinute: null, lastTouch: null, settle: null, origin };
        return { state: nextState, frame: frameNightSleepScore(Math.max(0, time - origin)) }; }
    } else presence = 1;
    return { state: nextState, frame: { cursorProgress: clampNightSleepScore(minute / NIGHT_SLEEP_SCORE_DATA.windowMinutes), selectionPresence: presence, segmentIndex: segmentIndexAtNightSleepScore(minute), isRest: presence === 0, night: presence, reveal: 1, meteor: -1 } };
  }
  if (state.autoPlay && !state.quiet && state.visible) return { state, frame: frameNightSleepScore(Math.max(0, time - state.origin)) };
  return { state, frame: { cursorProgress: 0, selectionPresence: 0, segmentIndex: null, isRest: true, night: 0, reveal: 0, meteor: -1 } };
}

export function appearNightSleepScore(state: NightSleepScoreState, autoPlay: boolean, quiet: boolean, at: number): NightSleepScoreState {
  'worklet'; if (state.appeared) return state; return { ...state, appeared: true, autoPlay, quiet, origin: state.pausedAt ?? at };
}

export function setAutoPlayNightSleepScore(state: NightSleepScoreState, enabled: boolean, at: number): NightSleepScoreState {
  'worklet'; const time = state.pausedAt ?? at;
  return enabled ? { ...state, autoPlay: true, ...(state.selectedMinute === null ? { origin: time } : { lastTouch: time }) } : { ...state, autoPlay: false, lastTouch: null };
}

export function setQuietNightSleepScore(state: NightSleepScoreState, enabled: boolean, at: number): NightSleepScoreState {
  'worklet'; if (state.quiet === enabled) return state; let next = cancelScrubNightSleepScore(state, state.pausedAt ?? at);
  const time = next.pausedAt ?? at; next = { ...next, quiet: enabled, settle: null };
  return next.selectedMinute !== null ? { ...next, quietFadeStarted: time, lastTouch: next.autoPlay ? time : null } : { ...next, quietFadeStarted: null, origin: time };
}

export function setActiveNightSleepScore(state: NightSleepScoreState, enabled: boolean, at: number): NightSleepScoreState {
  'worklet'; return state.active === enabled ? state : setSuspensionNightSleepScore({ ...state, active: enabled }, at);
}

export function setVisibleNightSleepScore(state: NightSleepScoreState, enabled: boolean, at: number, clearSelection = false): NightSleepScoreState {
  'worklet'; if (state.visible === enabled && !clearSelection) return state; let next = clearSelection ? resetNightSleepScore(state, at) : state;
  next = { ...next, visible: enabled }; return setSuspensionNightSleepScore(next, at);
}
