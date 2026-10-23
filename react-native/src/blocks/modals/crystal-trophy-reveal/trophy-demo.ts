// A 15-second performance on the simulation clock; every beat uses the finger's command API.
import {
  close, dragTo, initialState, pressShare, release, reveal, stepSimulation, tapAt, touchDown, travel,
  type TrophyState,
} from './trophy-timeline';

export const DEMO_PERIOD = 15;
const BEATS = [0.7, 2.1, 2.8, 7.4, 8.1, 9.3, 9.6, 12, 12.8, 12.95, 15] as const;

function demoBeat(s: TrophyState, t: number): void {
  'worklet';
  if (t === 0.7) close(s);
  if (t === 2.8) reveal(s);
  if (t === 7.4) touchDown(s, 0.20, 2.05);
  if (t === 9.6) release(s, 1500);
  if (t === 12) tapAt(s, -0.48, 2.15);
  if (t === 12.8) pressShare(s, true);
  if (t === 12.95) pressShare(s, false, true);
}
function demoDrag(s: TrophyState, t: number, width: number): void {
  'worklet';
  if (t < 7.4 || t >= 9.6) return;
  let angle = 0;
  if (t < 8.1) angle = 0.52 * travel((t - 7.4) / 0.7);
  else if (t < 9.3) angle = 0.52 - 1.04 * travel((t - 8.1) / 1.0);
  else angle = -0.52 + 1.04 * travel((t - 9.3) / 0.3);
  dragTo(s, angle / 0.0062, 0, 0.20 + angle * 0.55, 2.05 + angle * 0.19, width);
}

/** Constant simulated period; stalls slow wall time instead of skipping the hero moment. */
export function advanceTrophy(previous: TrophyState, elapsed: number, width: number): TrophyState {
  'worklet';
  let s = { ...previous, events: previous.events };
  let remaining = Math.min(Math.max(elapsed, 0), 1 / 30);
  // At most four 120 Hz steps plus one split at a scripted event boundary.
  for (let step = 0; step < 8; step++) {
    if (remaining < 1e-9) break;
    let dt = Math.min(remaining, 1 / 120);
    const scripted = s.auto && !s.reduced && !s.paused;
    let beat = -1;
    if (scripted) {
      for (let i = 0; i < BEATS.length; i++) {
        const gap = BEATS[i] - s.demoTime;
        if (gap > 1e-8 && gap <= dt + 1e-8) { dt = gap; beat = BEATS[i]; break; }
      }
    }
    stepSimulation(s, dt);
    remaining -= dt;
    if (scripted) {
      s.demoTime += dt;
      demoDrag(s, s.demoTime, width);
      if (beat >= 0) demoBeat(s, beat);
      if (s.demoTime >= DEMO_PERIOD - 1e-8) {
        const clock = s.clock; const events = s.events;
        s = initialState(true, false); s.clock = clock; s.lastInput = clock; s.events = events;
      }
    }
    if (s.auto && s.paused && !s.realDown && s.clock - s.lastInput >= 3.5) {
      if (!s.recovering) {
        s.recovering = true; s.down = false; s.spin = false; s.releaseAge = 0; s.releaseYaw = s.yaw;
        if (s.phase === 'closed') reveal(s);
      }
      if (s.phase === 'closed') reveal(s);
      if (s.phase === 'open' && Math.abs(s.yaw) < 0.002 && s.flash === 0 && s.copied === 0 && s.touch === 0) {
        const clock = s.clock; const events = s.events;
        s = initialState(true, s.reduced); s.clock = clock; s.lastInput = clock; s.events = events;
      }
    }
  }
  return s;
}
