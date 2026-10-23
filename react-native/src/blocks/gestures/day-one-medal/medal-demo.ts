import type { MotionState } from './medal-sim';

export type DemoEvent = 'landed' | 'reset' | 'arrived' | 'struck' | 'cooled' | 'back' | 'front' | 'engraved'
  | 'caught' | 'unworn' | 'milestone' | 'closed';
export type DemoActions = Record<'reset' | 'open' | 'forge' | 'turn' | 'flick' | 'wear' | 'next' | 'close', () => void>;
export type DemoClock = { after: (ms: number, fn: () => void) => unknown; cancel: (id: unknown) => void };
const nativeClock: DemoClock = {
  after: (ms, fn) => setTimeout(fn, ms),
  cancel: id => clearTimeout(id as ReturnType<typeof setTimeout>),
};

/** Each timeout is local to a completed step. A missing event aborts the loop;
 * it never advances the story past a picture that has not happened. */
export function createMedalDemo(actions: DemoActions, clock: DemoClock = nativeClock) {
  const steps: { action?: keyof DemoActions; event?: DemoEvent; dwell?: number }[] = [
    { action: 'reset', event: 'reset' }, { action: 'open', event: 'arrived', dwell: 650 },
    { action: 'forge', event: 'struck' }, { event: 'cooled', dwell: 1000 },
    { action: 'turn', event: 'back' }, { event: 'engraved', dwell: 700 },
    { action: 'turn', event: 'front', dwell: 900 }, { action: 'flick', event: 'landed', dwell: 700 },
    { action: 'wear', event: 'caught', dwell: 1400 }, { action: 'flick', dwell: 3000 },
    { action: 'wear', event: 'unworn', dwell: 600 },
    { action: 'next', event: 'milestone', dwell: 1400 },
    { action: 'next', event: 'milestone', dwell: 1600 },
    { action: 'close', event: 'closed', dwell: 900 },
  ];
  const seen = new Set<DemoEvent>();
  let index = -1;
  let timer: unknown;
  let waiting: DemoEvent | undefined;
  let running = false;
  const cancelTimer = () => { if (timer !== undefined) clock.cancel(timer); timer = undefined; };
  const stop = () => { running = false; waiting = undefined; cancelTimer(); };
  const complete = () => {
    cancelTimer();
    waiting = undefined;
    timer = clock.after(steps[index].dwell ?? 0, advance);
  };
  const advance = () => {
    if (!running) return;
    index = (index + 1) % steps.length;
    const step = steps[index];
    waiting = step.event;
    // Watchdog is a safe fallback: close and stop instead of skipping forge/landing.
    // A throttled renderer may take much longer in wall time while its picture catches up.
    timer = clock.after(60000, () => { stop(); actions.close(); });
    if (step.action) { seen.clear(); actions[step.action](); }
    if (!step.event || seen.has(step.event)) complete();
  };
  return {
    start() { stop(); running = true; index = -1; advance(); },
    stop,
    signal(event: DemoEvent) { if (running) { seen.add(event); if (waiting === event) complete(); } },
  };
}

export const COMPLETION_EVENTS: DemoEvent[] = ['struck', 'cooled', 'landed', 'back', 'front', 'engraved', 'caught', 'unworn', 'milestone'];

/** Shared by the UI publisher and the headless event/delay proof. */
export function demoCompletions(previous: MotionState, next: MotionState) {
  'worklet';
  let events = 0;
  if (next.struck >= 0 && next.struck !== previous.struck) events |= 1;
  if (next.forged && next.heat === 0 && previous.heat > 0) events |= 2;
  if (next.mode === 'rest' && previous.mode !== 'rest') {
    events |= 4;
    events |= Math.abs(Math.round(next.base / Math.PI) % 2) === 1 ? 8 : 16;
  }
  if (next.engraved && !previous.engraved) events |= 32;
  if (next.caught && !previous.caught) events |= 64;
  if (next.worn === 0 && previous.worn > 0) events |= 128;
  if (next.morphPhase === 'idle' && previous.morphPhase === 'commit') events |= 256;
  return events;
}

export function pictureCanAdvance(gpuDriving: boolean, clock: number, presented: number) {
  'worklet';
  return !gpuDriving || clock <= presented + 1 / 120;
}
