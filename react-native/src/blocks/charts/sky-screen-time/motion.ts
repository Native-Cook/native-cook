export const SKY_SCREEN_TIME_LOOP = 8;
export const SKY_SCREEN_TIME_FOCUS_DURATION = 25 * 60;

export interface SkyScreenTimeHour {
  readonly productive: number;
  readonly neutral: number;
  readonly distracted: number;
  readonly pickups: number;
  readonly notifications: number;
  readonly minutes: number;
  readonly quality: number;
}

export interface SkyScreenTimeFrame {
  readonly hour: number;
  readonly selection: number;
  readonly quality: number;
  readonly storm: number;
  readonly focusSeconds: number;
  readonly selectedIndex: number | null;
  readonly title: 'Productive' | 'Balanced' | 'Distracted';
  readonly titleOpacities: readonly [number, number, number];
}

export interface SkyScreenTimeMotionState {
  readonly day: number;
  readonly autoPlay: boolean;
  readonly quiet: boolean;
  readonly active: boolean;
  readonly appeared: boolean;
  readonly origin: number;
  readonly pausedAt: number | null;
  readonly selectedHour: number | null;
  readonly fingerDown: boolean;
  readonly interruptedAt: number | null;
  readonly returnStarted: number | null;
  readonly returnFrame: SkyScreenTimeFrame;
  readonly resetRequested: boolean;
  readonly focusStarted: number | null;
}

export type SkyScreenTimeCadence = 'stopped' | 'seconds' | 'animation';

const totals = [3, 0, 13, 25, 0, 0, 0, 17, 16, 3, 0, 1.5, 6.5, 0.5, 2.8, 0, 4.1, 5, 1.5, 19, 0.5, 19.6, 0, 0];
const pickups = [1, 0, 4, 7, 0, 0, 0, 5, 5, 1, 0, 1, 2, 0, 1, 0, 1, 2, 0, 5, 0, 5, 0, 0];
const notifications = [1, 0, 2, 3, 0, 0, 0, 3, 2, 0, 0, 1, 1, 0, 2, 0, 3, 1, 0, 2, 0, 3, 0, 0];

export function skyScreenTimeClamp(value: number, lower: number, upper: number): number {
  'worklet';
  return Math.min(upper, Math.max(lower, value));
}

export function skyScreenTimeSmooth(value: number): number {
  'worklet';
  const t = skyScreenTimeClamp(value, 0, 1);
  return t * t * (3 - 2 * t);
}

export function skyScreenTimeHour(productive: number, neutral: number, distracted: number, hourPickups: number, hourNotifications: number): SkyScreenTimeHour {
  'worklet';
  const minutes = productive + neutral + distracted;
  return { productive, neutral, distracted, pickups: hourPickups, notifications: hourNotifications, minutes, quality: minutes > 0 ? productive / minutes : 0.9 };
}

export function skyScreenTimeHours(day: number): SkyScreenTimeHour[] {
  'worklet';
  const result: SkyScreenTimeHour[] = [];
  for (let index = 0; index < 24; index += 1) {
    const total = totals[index];
    const neutral = index === 2 ? 3.5 : index === 7 ? 3.4 : 0;
    const distracted = index === 14 || index === 16 ? total : 0;
    if (day !== 1) {
      result.push(skyScreenTimeHour(total - neutral - distracted, neutral, distracted, pickups[index], notifications[index]));
    } else {
      const scaled = total * 1.2;
      result.push(skyScreenTimeHour(scaled * 0.62, scaled * 0.13, scaled * 0.25, pickups[index] + (total > 5 ? 1 : 0), notifications[index]));
    }
  }
  return result;
}

export function skyScreenTimeTotal(day: number): SkyScreenTimeHour {
  'worklet';
  const values = skyScreenTimeHours(day);
  let productive = 0;
  let neutral = 0;
  let distracted = 0;
  let hourPickups = 0;
  let hourNotifications = 0;
  for (let index = 0; index < values.length; index += 1) {
    const item = values[index];
    productive += item.productive;
    neutral += item.neutral;
    distracted += item.distracted;
    hourPickups += item.pickups;
    hourNotifications += item.notifications;
  }
  return skyScreenTimeHour(productive, neutral, distracted, hourPickups, hourNotifications);
}

export function skyScreenTimeQuality(hour: number, day: number): number {
  'worklet';
  const values = skyScreenTimeHours(day);
  const position = skyScreenTimeClamp(hour, 0, 23);
  const left = Math.floor(position);
  const right = Math.min(23, left + 1);
  return values[left].quality + (values[right].quality - values[left].quality) * (position - left);
}

export function skyScreenTimeTimeLabel(index: number): string {
  'worklet';
  const hour = index % 12 === 0 ? 12 : index % 12;
  return `${hour} ${index < 12 ? 'AM' : 'PM'}`;
}

export function skyScreenTimeDuration(minutes: number): string {
  'worklet';
  const value = minutes >= 0 ? Math.floor(minutes + 0.5) : Math.ceil(minutes - 0.5);
  return value >= 60 ? `${Math.floor(value / 60)}h${String(value % 60).padStart(2, '0')}` : `${value}m`;
}

function skyScreenTimeStorm(quality: number): number {
  'worklet';
  return skyScreenTimeClamp((0.9 - quality) / 0.9, 0, 1);
}

function skyScreenTimeFade(value: number): number {
  'worklet';
  const t = skyScreenTimeClamp(value, 0, 1);
  return t * t * (3 - 2 * t);
}

export function skyScreenTimeTitleOpacities(quality: number): readonly [number, number, number] {
  'worklet';
  const productive = skyScreenTimeFade((quality - 0.71) / 0.08);
  const distracted = 1 - skyScreenTimeFade((quality - 0.41) / 0.08);
  return [productive, 1 - productive - distracted, distracted];
}

function skyScreenTimeMakeFrame(hour = 3, selection = 0, quality = 0.9, storm = 0, focusSeconds = 0): SkyScreenTimeFrame {
  'worklet';
  return {
    hour, selection, quality, storm, focusSeconds,
    selectedIndex: selection >= 0.5 ? skyScreenTimeClamp(Math.floor(hour + 0.5), 0, 23) : null,
    title: quality >= 0.75 ? 'Productive' : quality >= 0.45 ? 'Balanced' : 'Distracted',
    titleOpacities: skyScreenTimeTitleOpacities(quality),
  };
}

function skyScreenTimeCopyFrame(frame: SkyScreenTimeFrame): SkyScreenTimeFrame {
  'worklet';
  return { ...frame, titleOpacities: skyScreenTimeTitleOpacities(frame.quality) };
}

export function skyScreenTimeDemoFrame(at: number, day = 0): SkyScreenTimeFrame {
  'worklet';
  const t = Math.max(0, at) % SKY_SCREEN_TIME_LOOP;
  const daily = skyScreenTimeTotal(day).quality;
  let hour = 3;
  let alpha = 0;
  if (t >= 1.3 && t < 1.7) alpha = skyScreenTimeSmooth((t - 1.3) / 0.4);
  else if (t >= 1.7 && t < 3.3) { alpha = 1; hour = 3 + 11 * skyScreenTimeSmooth((t - 1.7) / 1.6); }
  else if (t >= 3.3 && t < 4.4) { alpha = 1; hour = 14; }
  else if (t >= 4.4 && t < 5.9) { alpha = 1; hour = 14 + 7 * skyScreenTimeSmooth((t - 4.4) / 1.5); }
  else if (t >= 5.9 && t < 6.6) { hour = 21; alpha = 1 - skyScreenTimeSmooth((t - 5.9) / 0.7); }
  const ratio = daily + (skyScreenTimeQuality(hour, day) - daily) * alpha;
  return skyScreenTimeMakeFrame(hour, alpha, ratio, skyScreenTimeStorm(ratio));
}

export function skyScreenTimeInitialState(): SkyScreenTimeMotionState {
  'worklet';
  return {
    day: 0, autoPlay: false, quiet: false, active: true, appeared: false, origin: 0, pausedAt: null,
    selectedHour: null, fingerDown: false, interruptedAt: null, returnStarted: null,
    returnFrame: skyScreenTimeMakeFrame(), resetRequested: false, focusStarted: null,
  };
}

function skyScreenTimeLogicalTime(state: SkyScreenTimeMotionState, at: number): number {
  'worklet';
  return state.pausedAt ?? at;
}

function skyScreenTimeClearSelection(state: SkyScreenTimeMotionState, at: number): SkyScreenTimeMotionState {
  'worklet';
  return { ...state, selectedHour: null, interruptedAt: null, returnStarted: null, fingerDown: false, resetRequested: false, origin: skyScreenTimeLogicalTime(state, at) };
}

export function skyScreenTimeAppear(state: SkyScreenTimeMotionState, autoPlay: boolean, quiet: boolean, at: number): SkyScreenTimeMotionState {
  'worklet';
  if (state.appeared) return state;
  return { ...state, appeared: true, autoPlay, quiet, origin: skyScreenTimeLogicalTime(state, at) };
}

export function skyScreenTimeSetAutoPlay(state: SkyScreenTimeMotionState, enabled: boolean, at: number): SkyScreenTimeMotionState {
  'worklet';
  const now = skyScreenTimeLogicalTime(state, at);
  return { ...state, autoPlay: enabled, origin: now, returnStarted: !enabled && !state.resetRequested ? null : state.returnStarted, interruptedAt: state.selectedHour !== null ? now : state.interruptedAt };
}

export function skyScreenTimeSetQuiet(state: SkyScreenTimeMotionState, enabled: boolean, at: number): SkyScreenTimeMotionState {
  'worklet';
  if (state.quiet === enabled) return state;
  let next = state;
  if (enabled && state.resetRequested) next = skyScreenTimeClearSelection(next, at);
  const now = skyScreenTimeLogicalTime(next, at);
  return { ...next, quiet: enabled, returnStarted: null, resetRequested: false, origin: now, interruptedAt: next.selectedHour !== null ? now : next.interruptedAt };
}

export function skyScreenTimeSetActive(state: SkyScreenTimeMotionState, enabled: boolean, at: number): SkyScreenTimeMotionState {
  'worklet';
  if (state.active === enabled) return state;
  if (enabled) {
    const pause = Math.max(0, at - (state.pausedAt ?? at));
    return { ...state, active: true, origin: state.origin + pause, interruptedAt: state.interruptedAt === null ? null : state.interruptedAt + pause, returnStarted: state.returnStarted === null ? null : state.returnStarted + pause, pausedAt: null };
  }
  return { ...state, active: false, pausedAt: at, fingerDown: false, interruptedAt: state.fingerDown && state.selectedHour !== null ? at : state.interruptedAt };
}

export function skyScreenTimeScrub(state: SkyScreenTimeMotionState, hour: number): SkyScreenTimeMotionState {
  'worklet';
  return { ...state, selectedHour: skyScreenTimeClamp(hour, 0, 23) };
}

export function skyScreenTimeEndScrub(state: SkyScreenTimeMotionState, at: number): SkyScreenTimeMotionState {
  'worklet';
  return { ...state, fingerDown: false, interruptedAt: skyScreenTimeLogicalTime(state, at) };
}

export function skyScreenTimeChooseDay(state: SkyScreenTimeMotionState, index: number, at: number): SkyScreenTimeMotionState {
  'worklet';
  return skyScreenTimeClearSelection({ ...state, day: skyScreenTimeClamp(index, 0, 1) }, at);
}

export function skyScreenTimeRemainingFocus(state: SkyScreenTimeMotionState, at: number): number {
  'worklet';
  if (state.focusStarted === null) return 0;
  return Math.max(0, SKY_SCREEN_TIME_FOCUS_DURATION - Math.floor(Math.max(0, at - state.focusStarted)));
}

export function skyScreenTimeTick(state: SkyScreenTimeMotionState, at: number): { readonly state: SkyScreenTimeMotionState; readonly frame: SkyScreenTimeFrame } {
  'worklet';
  const now = skyScreenTimeLogicalTime(state, at);
  let next = state;
  let result: SkyScreenTimeFrame;
  if (state.selectedHour !== null) {
    const ratio = skyScreenTimeQuality(state.selectedHour, state.day);
    result = skyScreenTimeMakeFrame(state.selectedHour, 1, ratio, skyScreenTimeStorm(ratio));
    const idleDeadline = state.interruptedAt === null ? null : state.interruptedAt + 2;
    if (state.resetRequested || (state.autoPlay && !state.quiet && !state.fingerDown && now >= (idleDeadline ?? Number.POSITIVE_INFINITY))) {
      const returnStarted = state.returnStarted ?? idleDeadline ?? now;
      const returnFrame = state.returnStarted === null ? result : state.returnFrame;
      if (state.returnStarted === null) next = { ...state, returnStarted, returnFrame };
      const t = skyScreenTimeSmooth((now - returnStarted) / 0.5);
      const selection = returnFrame.selection * (1 - t);
      const quality = returnFrame.quality + (skyScreenTimeTotal(state.day).quality - returnFrame.quality) * t;
      result = skyScreenTimeMakeFrame(state.selectedHour, selection, quality, skyScreenTimeStorm(quality));
      if (t >= 1) { next = skyScreenTimeClearSelection(next, at); result = skyScreenTimeDemoFrame(0, state.day); }
    }
  } else if (state.autoPlay && !state.quiet) {
    result = skyScreenTimeDemoFrame(Math.max(0, now - state.origin), state.day);
  } else {
    const ratio = skyScreenTimeTotal(state.day).quality;
    result = skyScreenTimeMakeFrame(3, 0, ratio, skyScreenTimeStorm(ratio));
  }
  if (state.quiet) result = { ...result, storm: 0 };
  result = { ...result, focusSeconds: skyScreenTimeRemainingFocus(state, at) };
  return { state: next, frame: skyScreenTimeCopyFrame(result) };
}

export function skyScreenTimeFrame(state: SkyScreenTimeMotionState, at: number): SkyScreenTimeFrame {
  'worklet';
  return skyScreenTimeTick(state, at).frame;
}

export function skyScreenTimeReset(state: SkyScreenTimeMotionState, at: number): SkyScreenTimeMotionState {
  'worklet';
  const current = skyScreenTimeFrame(state, at);
  if (state.quiet || current.selection <= 0.001) return skyScreenTimeClearSelection(state, at);
  return { ...state, selectedHour: current.hour, fingerDown: false, returnFrame: current, returnStarted: skyScreenTimeLogicalTime(state, at), resetRequested: true };
}

export function skyScreenTimeToggleFocus(state: SkyScreenTimeMotionState, at: number): SkyScreenTimeMotionState {
  'worklet';
  return { ...state, focusStarted: skyScreenTimeRemainingFocus(state, at) > 0 ? null : at };
}

export function skyScreenTimeBeginScrub(state: SkyScreenTimeMotionState, at: number): SkyScreenTimeMotionState {
  'worklet';
  const current = skyScreenTimeFrame(state, at);
  return { ...state, selectedHour: current.hour, fingerDown: true, interruptedAt: null, returnStarted: null, resetRequested: false };
}

export function skyScreenTimeCadence(state: SkyScreenTimeMotionState, at: number): SkyScreenTimeCadence {
  'worklet';
  if (!state.active) return 'stopped';
  if (state.resetRequested && !state.quiet) return 'animation';
  if (state.autoPlay && !state.quiet && !state.fingerDown) return 'animation';
  return skyScreenTimeRemainingFocus(state, at) > 0 ? 'seconds' : 'stopped';
}
