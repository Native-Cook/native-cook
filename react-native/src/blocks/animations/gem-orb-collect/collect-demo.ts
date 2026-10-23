// The demo drives the real input path on fixed simulation ticks; no JS timers or tick allocations.
import { TIMING, type Layout } from './gem-contract';
import { clamp, cloneCollect, emptyInput, resetCollect, STEP, stepCollect, type CollectInput, type CollectState } from './collect-sim';

export function demoInput(time: number, dt: number, reduced = false, input = emptyInput(reduced)): CollectInput {
  'worklet';
  const end = time + dt, epsilon = 1e-8;
  input.reduced = reduced; input.silent = true;
  // The longer stationary hold still releases after the twentieth launch (4.15 s).
  input.gemDown = (end >= 1.2 - epsilon && end < 4.6 - epsilon) || (!reduced && end >= 7.2 - epsilon && end < 8.9 - epsilon);
  const right = 120 * (clamp((end - 7.2) / 0.6) - clamp((time - 7.2) / 0.6));
  const left = 190 * (clamp((end - 8.2) / 0.7) - clamp((time - 8.2) / 0.7));
  // The reduced demo stays still; actual user drags remain direct manipulation.
  input.dragX = reduced ? 0 : right - left; input.dragY = 0;
  input.releaseVX = !reduced && time < 8.9 - epsilon && end >= 8.9 - epsilon ? -190 / 0.7 : 0;
  input.releaseVY = 0; input.press = false; input.tap = false; input.collect = false; input.holdMoved = false;
  input.barDown = end >= 9.6 - epsilon && end < 9.75 - epsilon;
  input.replay = time < 9.75 - epsilon && end >= 9.75 - epsilon;
  return input;
}
export function initialDemo(rest?: CollectState) {
  'worklet'; return { tick: 0, accumulator: 0, paused: false, quiet: 0, recovering: false, silent: true, input: emptyInput(), rest: rest ? cloneCollect(rest) : undefined };
}
export type DemoState = ReturnType<typeof initialDemo>;
export function pauseDemo(demo: DemoState): DemoState {
  'worklet'; demo.paused = true; demo.quiet = 0; demo.recovering = false; return demo;
}
/** Both arguments retain their identity. The hook consumes events once per display frame. */
export function stepDemo(s: CollectState, d: DemoState, dt: number, l: Layout, reduced: boolean, real: CollectInput, touching: boolean): CollectState {
  'worklet';
  // Offline callers may initialize lazily; the live hook supplies canonical rest at mount.
  if (!d.rest) d.rest = cloneCollect(s);
  d.silent = true;
  if (d.paused) {
    d.quiet = touching ? 0 : d.quiet + clamp(dt, 0, 0.05);
    if (!touching && d.quiet >= TIMING.resumeAfter) {
      if (s.phase !== 'before') {
        d.recovering = true;
        const input = d.input;
        input.gemDown = false; input.dragX = 0; input.dragY = 0; input.releaseVX = 0; input.releaseVY = 0;
        input.barDown = false; input.replay = s.phase !== 'rewind'; input.reduced = reduced; input.silent = true;
        return stepCollect(s, dt, input, l);
      }
      resetCollect(s, d.rest); d.tick = 0; d.accumulator = 0; d.paused = false; d.recovering = false;
      return s;
    }
    d.silent = d.recovering;
    return stepCollect(s, dt, real, l);
  }
  d.accumulator += clamp(dt, 0, 0.05);
  const steps = Math.floor((d.accumulator + 1e-9) / STEP);
  let events = 0;
  for (let i = 0; i < steps; i++) {
    stepCollect(s, STEP, demoInput(d.tick * STEP, STEP, reduced, d.input), l); events |= s.events; d.tick++;
    d.accumulator -= STEP;
    if (d.tick >= Math.round(TIMING.loop / STEP)) { resetCollect(s, d.rest); d.tick = 0; }
  }
  if (d.accumulator < 1e-9) d.accumulator = 0;
  s.events = events;
  return s;
}
