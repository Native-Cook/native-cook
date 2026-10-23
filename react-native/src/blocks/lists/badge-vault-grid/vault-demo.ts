import { act, type Action } from './vault-actions';
import { type VaultLayout } from './vault-layout';
import { scrollCurve } from './vault-motion';
import { type Engine, type HeroVisual } from './vault-state';

export const DEMO_PERIOD = 18000;
export const DEMO_EVENTS = [
  { at: 400, action: 'band', index: -1 },
  { at: 1300, action: 'press', index: 4 },
  { at: 1420, action: 'open', index: 4 },
  { at: 3700, action: 'flick', index: 1 },
  { at: 5100, action: 'close', index: -1 },
  { at: 6000, action: 'open', index: 5 },
  { at: 6900, action: 'scroll-end', index: -1 },
  { at: 8700, action: 'pill-in', index: -1 },
  { at: 8820, action: 'log', index: -1 },
  { at: 11100, action: 'press', index: 14 },
  { at: 11220, action: 'open', index: 14 },
  { at: 12900, action: 'slow-turn', index: -1 },
  { at: 14000, action: 'flick', index: 1 },
  { at: 14900, action: 'close', index: -1 },
  { at: 15700, action: 'scroll-top', index: -1 },
  { at: 17300, action: 'reset', index: -1 },
] as const;
export function startScroll(e: Engine, from: number, to: number, duration: number) {
  'worklet';
  e.demo.scrollAt = e.now; e.demo.scrollFrom = from; e.demo.scrollTo = to; e.demo.scrollDuration = duration;
}
export function demoAction(e: Engine, event: typeof DEMO_EVENTS[number], l: VaultLayout, scrollY: number, hero: HeroVisual) {
  'worklet';
  if (event.action === 'band') { e.bandAt = e.now; return; }
  if (event.action === 'scroll-end' || event.action === 'scroll-top') {
    startScroll(e, scrollY, event.action === 'scroll-end' ? l.scrollEnd : 0, 1500); return;
  }
  if (event.action === 'open') act(e, 'release', event.index, 0, 0, l, scrollY, hero, false);
  if (event.action === 'log') act(e, 'pill-out', -1, 0, 0, l, scrollY, hero, false);
  act(e, event.action as Action, event.index, event.action === 'flick' ? 13 : 0.3,
    event.action === 'close' ? 900 : 0.25, l, scrollY, hero, false);
}
/** The remainder survives a stalled frame, including one spanning a loop boundary. */
export function advanceDemo(e: Engine, dtMs: number, ready: boolean, l: VaultLayout, scrollY: number, hero: HeroVisual) {
  'worklet';
  const d = e.demo;
  if (!d.enabled || !ready) return;
  if (d.readyAt < 0) d.readyAt = e.now;
  if (d.touchAt >= 0) {
    if (d.touching || e.now - d.touchAt < 3500) return;
    if (d.returningAt < 0) {
      d.returningAt = e.now;
      act(e, 'close', -1, 0, 0, l, scrollY, hero, false);
      startScroll(e, scrollY, 0, 600);
    }
    if (e.now - d.returningAt < 850) return;
    act(e, 'reset', -1, 0, 0, l, scrollY, hero, false);
    d.touchAt = -1; d.returningAt = -1; d.time = 0; d.cursor = 0; d.started = true;
    return;
  }
  if (!d.started) {
    if (e.now - d.readyAt < 300) return;
    d.started = true; d.time = e.now - d.readyAt - 300;
  } else d.time += dtMs;
  const now = e.now;
  while (true) {
    while (d.cursor < DEMO_EVENTS.length && DEMO_EVENTS[d.cursor].at <= d.time) {
      const event = DEMO_EVENTS[d.cursor];
      e.now = now - (d.time - event.at);
      demoAction(e, event, l, scrollY, hero);
      d.cursor++;
    }
    if (d.time < DEMO_PERIOD) break;
    d.time -= DEMO_PERIOD; d.cursor = 0;
  }
  e.now = now;
}
export function demoScroll(e: Engine): number | null {
  'worklet';
  const d = e.demo;
  if (d.scrollAt < 0) return null;
  const p = (e.now - d.scrollAt) / d.scrollDuration;
  const y = d.scrollFrom + (d.scrollTo - d.scrollFrom) * scrollCurve(p);
  if (p >= 1) d.scrollAt = -1;
  return y;
}
