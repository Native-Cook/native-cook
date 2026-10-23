import { F } from './beam-theme';
import { beginDrag, bezier, command, drag, endDrag, type BeamState } from './beam-sim';
import type { FormAction } from './beam-form';

export const DEMO_PERIOD = 22;
export type DemoEvent = { at: number; action: FormAction | { type: 'press'; index: number; down: boolean } };
export type DemoState = { enabled: boolean; active: boolean; begun: boolean; time: number; cursor: number; idle: number; fingers: number; focused: boolean; resuming: boolean; scrubbing: boolean; queue: DemoEvent[] };
const events: DemoEvent[] = [];
function add(at: number, action: DemoEvent['action']) { events.push({ at, action }); }
function type(at: number, field: number, value: string) {
  for (let i = 1; i <= value.length; i++) add(at + (i - 1) * 0.07, { type: 'edit', field, value: value.slice(0, i) });
}
function tap(at: number, type: 'continue' | 'delivery') {
  const index = type === 'continue' ? F.CTA_PRESS : F.SEG_PRESS;
  add(at, { type: 'press', index, down: true });
  add(at + (type === 'continue' ? 0.12 : 0.1), { type: 'press', index, down: false });
  add(at + (type === 'continue' ? 0.12 : 0.1), type === 'continue' ? { type } : { type, express: true });
}
add(0.5, { type: 'focus', field: 0 }); type(0.7, 0, 'mira@northlane.co'); tap(2.2, 'continue');
add(3.4, { type: 'focus', field: 1 }); type(3.5, 1, '18 Alder Lane');
add(4.55, { type: 'focus', field: 2 }); type(4.65, 2, 'Portland');
add(5.35, { type: 'focus', field: 3 }); type(5.45, 3, '97209');
add(6, { type: 'focus', field: -1 }); tap(6.3, 'continue');
add(7.5, { type: 'focus', field: 4 }); type(7.6, 4, '4242424242424241');
add(8.85, { type: 'focus', field: 5 }); type(8.95, 5, '0832');
add(9.35, { type: 'focus', field: 6 }); type(9.45, 6, '314');
add(9.8, { type: 'focus', field: -1 }); tap(10, 'continue');
add(11.3, { type: 'edit', field: 4, value: '424242424242424' });
add(11.5, { type: 'edit', field: 4, value: '4242424242424242' });
add(11.75, { type: 'focus', field: -1 }); tap(11.9, 'continue');
tap(15.1, 'delivery'); tap(15.7, 'continue'); tap(17.3, 'continue'); tap(20.6, 'continue');
export const DEMO_EVENTS: readonly DemoEvent[] = events.sort((a, b) => a.at - b.at);

/** Computes the scripted rail finger, including its small upward release velocity. */
export function demoDrag(t: number): { down: boolean; displacement: number; velocity: number } {
  'worklet';
  const local = Math.max(0, Math.min(0.7, t - 13.6));
  if (local < 0.6) {
    const u = local / 0.6;
    const value = -1.975 * bezier(u, 0.23, 1, 0.32, 1);
    const before = -1.975 * bezier(Math.max(0, u - 0.0001), 0.23, 1, 0.32, 1);
    return { down: t >= 13.6 && t < 14.3, displacement: value, velocity: (value - before) / 0.00006 };
  }
  return { down: t >= 13.6 && t < 14.3, displacement: -1.975 - 0.25 * (local - 0.6), velocity: -0.25 };
}
/** Creates the independent script transport; disabled transports never restart user data. */
export function createDemo(enabled: boolean): DemoState {
  'worklet'; return { enabled, active: enabled, begun: false, time: 0, cursor: 0, idle: 0, fingers: 0, focused: false, resuming: false, scrubbing: false, queue: [] };
}
/** Pauses the script on real contact and cancels any scripted press without committing it. */
export function touchDemo(d: DemoState, s: BeamState, down: boolean) {
  'worklet';
  d.fingers = Math.max(0, d.fingers + (down ? 1 : -1)); d.idle = 0;
  if (!d.enabled || !down) return;
  if (d.active) {
    d.active = false; d.queue = [];
    command(s, 'press', F.CTA_PRESS, 0); command(s, 'press', F.SEG_PRESS, 0);
    if (d.scrubbing) { endDrag(s, s.frame[F.HEAD_VEL]); d.scrubbing = false; }
  }
  s.silent = false;
}
/** Blocks automatic resumption while any real text input owns the keyboard. */
export function focusDemo(d: DemoState, focused: boolean) { 'worklet'; d.focused = focused; d.idle = 0; }
/** Restarts the script at zero only after the reset sweep has completed. */
export function restartedDemo(d: DemoState) {
  'worklet';
  if (!d.resuming) return;
  d.resuming = false; d.active = true; d.begun = true; d.time = 0; d.cursor = 0; d.idle = 0;
}
/** Advances the script using exactly the same substep clock as the physical simulation. */
export function stepDemo(d: DemoState, s: BeamState, dt: number) {
  'worklet';
  if (!d.enabled || !s.ready || s.frame[F.PRESENT] < 1) return;
  if (!d.active) {
    if (d.fingers === 0 && !d.focused && !d.resuming) {
      d.idle += dt;
      if (d.idle >= 3.5) { d.resuming = true; command(s, 'restart', 0, 0, true); }
    }
    return;
  }
  if (!d.begun) { d.begun = true; return; }
  d.time += dt;
  if (d.time >= DEMO_PERIOD - 1e-8) { d.time -= DEMO_PERIOD; d.cursor = 0; }
  s.silent = true;
  while (d.cursor < DEMO_EVENTS.length && DEMO_EVENTS[d.cursor].at <= d.time + 1e-8) {
    d.queue.push(DEMO_EVENTS[d.cursor]); d.cursor++;
  }
  const finger = demoDrag(d.time);
  if (finger.down && !d.scrubbing) { beginDrag(s); d.scrubbing = true; }
  if (finger.down) drag(s, finger.displacement, finger.velocity);
  if (!finger.down && d.scrubbing) { drag(s, finger.displacement, finger.velocity); endDrag(s, finger.velocity); d.scrubbing = false; }
}
