export const LOOP = 10;
export const ANCHORS = [0.8, 3.85, 6.35, 8.55];
export const TITLES = ['Hang out on\nyour phone', 'Humanise\nconnection', 'Build your\nown space', 'Memories\nthat last'];
export const BODIES = [
  'Realtime social widgets with friends,\nbecause not everything requires a DM.',
  'Stay close through simple, subtle\nmoments — no pressure, just presence.',
  "It's yours. Each widget adds a new way\nto interact. Play, share, organise.",
  'Each day is summarised in a scrap book\nstyle memory. No effort required.',
];
export type Motion = { now: number; epoch: number; manual: number; dragging: boolean; origin: number; released: number; from: number; to: number };
export function normalized(time: number) { 'worklet'; return ((time % LOOP) + LOOP) % LOOP; }
export function segment(t: number, a: number, b: number) { 'worklet'; return Math.min(1, Math.max(0, (t - a) / (b - a))); }
export function ease(p: number) { 'worklet'; return p * p * (3 - 2 * p); }
export function out(p: number) { 'worklet'; return 1 - Math.pow(1 - p, 3); }
export function mix(a: number, b: number, p: number) { 'worklet'; return a + (b - a) * p; }
export function spring(time: number) {
  'worklet'; if (time >= 0.65) return 1;
  const t = Math.max(0, time); return 1 - Math.exp(-9 * t) * (Math.cos(15 * t) + 0.6 * Math.sin(15 * t));
}
export function key(time: number, keys: number[][]) {
  'worklet'; if (time <= keys[0][0]) return keys[0][1];
  for (let i = 1; i < keys.length; i++) {
    if (time <= keys[i][0]) return mix(keys[i - 1][1], keys[i][1], ease(segment(time, keys[i - 1][0], keys[i][0])));
  }
  return keys[keys.length - 1][1];
}
export function pages(t: number) {
  'worklet'; const cuts = [[1.7, 2, 0, 1], [4.1, 4.4, 1, 2], [7, 7.3, 2, 3], [9.45, 9.85, 3, 0]];
  let current = 0;
  for (let i = 0; i < cuts.length; i++) {
    const [start, end, from, to] = cuts[i];
    if (t < start) return { from: current, to: current, p: 1, active: current };
    if (t < end) return { from, to, p: out(segment(t, start, end)), active: to };
    current = to;
  }
  return { from: current, to: current, p: 1, active: current };
}
export function shownTime(m: Motion, now: number, autoPlay: boolean, reduced: boolean, renderTime?: number) {
  'worklet';
  if (renderTime !== undefined) return normalized(renderTime);
  if (reduced) return m.manual >= 0 ? m.manual : 8.55;
  if (m.manual >= 0) {
    if (m.dragging) return m.manual;
    const age = Math.max(0, now - m.released);
    if (age < 0.6) return mix(m.from, m.to, spring(age));
    if (age < 2) return m.to;
    if (age < 2.55) return mix(m.to, 0, ease(segment(age, 2, 2.55)));
    return autoPlay ? normalized(age - 2.55) : 0;
  }
  return autoPlay ? normalized(now - m.epoch) : 0;
}
