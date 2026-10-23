import { Easing } from 'react-native-reanimated';

// Autoplay (7 s): the forward button moves the flow on, a flick throws it one more, a flick throws
// it back, and rewind brings Ember Lights home. Every value is a pure function of the loop time, so
// the Swift and Kotlin ports can match it frame for frame. Times in seconds.
export const LOOP_MS = 7000;
export const LOOP = LOOP_MS / 1000;
export const START_FLOW = 2;

export type Step = { at: number; to: number; response: number; damping: number; v0: number; press?: 'next' | 'prev' };
export const STEPS: Step[] = [
  { at: 0.9, to: 3, response: 0.55, damping: 0.8, v0: 2, press: 'next' },
  { at: 2.3, to: 4, response: 0.62, damping: 0.7, v0: 6 },
  { at: 3.75, to: 3, response: 0.62, damping: 0.72, v0: 6 },
  { at: 5.491, to: 2, response: 0.55, damping: 0.8, v0: 3, press: 'prev' }, // crosses the half-way at 5.6 s
];
export const IDLE_RESUME_MS = 3500;

export const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
export const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
export const FLOW_SPRING = { duration: 400, dampingRatio: 0.8 }; // snap after a drag (+ velocity)
export const SETTLE = { duration: 400, dampingRatio: 1 };
export const PRESS = { ms: 120, scale: 0.97, dim: 0.6 };
export const FLICK_V = 0.11; // px/ms
export const PROJECT_S = 0.15;
export const TEXT_SWAP = { inMs: 180, outMs: 120 };
// Light on the turning glass: a band whose place follows the card's angle and whose strength is how
// far the card is between two slots (so a cover at rest never shines).
export const SHEEN = { alpha: 0.4, width: 0.46, slant: 18, span: 40 };
export const PLAYHEAD = { from: 0.1, perLoop: 0.6 };

/** Remaining fraction of a spring step (1 → 0) released at τ = 0 with a push v0 (steps per second). */
export function springRemain(tau: number, response: number, damping: number, v0: number) {
  'worklet';
  if (tau <= 0) return 1;
  if (tau > response * 5) return 0;
  const w = (2 * Math.PI) / response;
  if (damping >= 1) return Math.exp(-w * tau) * (1 + (w - v0) * tau);
  const wd = w * Math.sqrt(1 - damping * damping);
  return Math.exp(-damping * w * tau) * (Math.cos(wd * tau) + ((damping * w - v0) / wd) * Math.sin(wd * tau));
}

/** The flow (front album, fractional) at loop time t. */
export function flowAt(t: number) {
  'worklet';
  let f = START_FLOW;
  let from = START_FLOW;
  for (const s of STEPS) {
    if (t > s.at) f += (s.to - from) * (1 - springRemain(t - s.at, s.response, s.damping, s.v0 / Math.abs(s.to - from)));
    from = s.to;
  }
  return f;
}

/** Press of the forward / rewind button at loop time t (0 → 1 → 0 over 240 ms). */
export function pressAt(t: number, which: 'next' | 'prev') {
  'worklet';
  for (const s of STEPS) {
    if (s.press !== which) continue;
    const u = (t - s.at + PRESS.ms / 1000) / (PRESS.ms / 1000);
    if (u > 0 && u < 2) return u < 1 ? u : 2 - u;
  }
  return 0;
}

// When the front album changes during the loop (the flow crosses a half-way point), read once.
export const CHANGES: number[] = (() => {
  const out: number[] = [];
  let prev = Math.round(flowAt(0));
  for (let ms = 1; ms < LOOP_MS; ms++) {
    const c = Math.round(flowAt(ms / 1000));
    if (c !== prev) out.push(ms / 1000);
    prev = c;
  }
  return out;
})();

/** Playhead fraction at loop time t: restarts with each new album and runs on through the loop's end. */
export function playheadAt(t: number, changes: number[]) {
  'worklet';
  let last = changes[changes.length - 1] - LOOP;
  for (const c of changes) if (c <= t) last = c;
  return Math.min(1, PLAYHEAD.from + PLAYHEAD.perLoop * ((t - last) / LOOP));
}
