import { heightAt, LOOP, PERIODS, xOf, yOf } from './peak-contract';
import {
  closeLoop, easeMove, fingerDown, fingerMove, fingerUp, FIXED_DT, isRest, press,
  request, resetLoop, returnHome, step,
} from './peak-sim';
import type { PeakState } from './peak-sim';

// Only these discrete actions belong to the demo; their implementation is the live interaction API.
export const DEMO_ACTIONS = [
  { at: 0.7, action: 'refresh-in' },
  { at: 0.82, action: 'refresh-out' },
  { at: 4.6, action: 'finger-down' },
  { at: 7.45, action: 'finger-up' },
  { at: 8, action: 'period-in' },
  { at: 8.12, action: 'period-out' },
  { at: 9.4, action: 'period-in' },
  { at: 9.52, action: 'period-out' },
  { at: 10.8, action: 'period-in' },
  { at: 10.92, action: 'period-out' },
] as const;

// Return the demo finger's curve parameter; position changes use the same cinematic easing.
function scriptedU(t: number): number {
  'worklet';
  const n = PERIODS[0].nodes;
  if (t < 4.75) return n[2];
  if (t < 5.55) return n[2] + (n[1] - n[2]) * easeMove((t - 4.75) / 0.8);
  if (t < 5.7) return n[1];
  if (t < 6.75) return n[1] + (n[3] - n[1]) * easeMove((t - 5.7) / 1.05);
  if (t < 7.35) return n[3] + (n[2] - n[3]) * easeMove((t - 6.75) / 0.6);
  return n[2];
}

// Deliver a table entry without adding an animation path unique to the demo.
function dispatch(s: PeakState, index: number): void {
  'worklet';
  const action = DEMO_ACTIONS[index].action;
  if (action === 'refresh-in') press(s, 'refresh', true, false);
  else if (action === 'refresh-out') {
    press(s, 'refresh', false, false);
    request(s, 'refresh', false);
  } else if (action === 'period-in') press(s, 'period', true, false);
  else if (action === 'period-out') {
    press(s, 'period', false, false);
    request(s, 'period', false);
  } else if (action === 'finger-down') {
    const u = PERIODS[0].nodes[2];
    fingerDown(s, xOf(u), yOf(heightAt(s.f.ys, u)), false);
  } else fingerUp(s, 0, false);
}

// Advance to exact table and loop boundaries even when a display frame spans one of them.
export function stepDemo(s: PeakState, dt: number): void {
  'worklet';
  let remaining = Math.max(0, Math.min(0.05, dt));
  if (s.reduced || !s.autoPlay) {
    step(s, remaining);
    return;
  }
  if (s.paused) {
    step(s, remaining);
    if ((s.restWait >= 3.5 || s.returning) && isRest(s) && !s.contact) {
      returnHome(s);
      if (isRest(s) && s.f.period === 0 && Math.abs(s.orbU - PERIODS[0].nodes[2]) < 0.00001) resetLoop(s);
    }
    return;
  }
  while (remaining > 0.000000001) {
    if (s.demoEvent < DEMO_ACTIONS.length && s.loop >= DEMO_ACTIONS[s.demoEvent].at - 0.000000001) {
      dispatch(s, s.demoEvent);
      s.demoEvent++;
    }
    const next = s.demoEvent < DEMO_ACTIONS.length ? DEMO_ACTIONS[s.demoEvent].at : LOOP;
    const h = Math.min(FIXED_DT, remaining, Math.max(0.000000001, next - s.loop), LOOP - s.loop);
    const nextClock = s.loop + h;
    if (s.finger && !s.fingerUser) {
      const u = scriptedU(nextClock);
      const velocity = (xOf(u) - xOf(scriptedU(s.loop))) / Math.max(h, 0.000000001);
      fingerMove(s, xOf(u), yOf(heightAt(s.f.ys, u)), velocity);
    }
    step(s, h);
    s.loop = nextClock;
    if (s.loop >= 12.5) closeLoop(s, Math.min(1, (s.loop - 12.5) / 0.5));
    remaining -= h;
    if (s.loop >= LOOP - 0.000000001) {
      s.cycles++;
      resetLoop(s);
    }
  }
  // An action exactly at the frame endpoint belongs to this frame, not the next one.
  if (s.demoEvent < DEMO_ACTIONS.length && s.loop >= DEMO_ACTIONS[s.demoEvent].at - 0.000000001) {
    dispatch(s, s.demoEvent);
    s.demoEvent++;
  }
}
