export const LOOP = 8;
export const FLAVORS = ['Strawberry', 'Blueberry', 'Orange', 'Kiwi', 'Apricot'];
export const TOPS = ['#EE91B0', '#A8B8F1', '#FFE690', '#DAF78B', '#F5D5BE'];
export const BOTTOMS = ['#D26388', '#6F83D1', '#F2B751', '#B7DF60', '#E2A18F'];
export type Motion = { now: number; epoch: number; from: number | null; target: number; eventTime: number; duration: number; spring: boolean; moving: boolean; lastTouch: number; held: boolean; panOrigin: number; id: number; reducedSelection: number };

export function clamp(x: number, a = 0, b = 1) { 'worklet'; return Math.min(b, Math.max(a, x)); }
export function smooth(x: number) { 'worklet'; const u = clamp(x); return u * u * (3 - 2 * u); }
export function wrap(index: number) { 'worklet'; return (index % 5 + 5) % 5; }
export function rounded(x: number) { 'worklet'; return x < 0 ? -Math.round(-x) : Math.round(x); }
export function automatic(seconds: number) {
  'worklet'; const t = Math.max(0, seconds) % LOOP, slot = Math.floor(t / 1.6); return slot + smooth((t - slot * 1.6 - 1) / 0.6);
}
export function spring(progress: number) {
  'worklet'; if (progress >= 1) return 1;
  const u = Math.max(0, progress), a = 5.15, b = 3.59, end = 1 - Math.exp(-a) * (Math.cos(b) + a / b * Math.sin(b));
  return (1 - Math.exp(-a * u) * (Math.cos(b * u) + a / b * Math.sin(b * u))) / end;
}
export function tabX(index: number) { 'worklet'; return [63, 196, 315, 420, 505][wrap(index)] + Math.floor(index / 5) * 575; }
export function tabWidth(index: number) { 'worklet'; return [106, 92, 78, 58, 70][wrap(index)]; }
export function tabShift(q: number) { 'worklet'; const index = Math.floor(q), fraction = q - index; return tabX(index) + (tabX(index + 1) - tabX(index)) * fraction - 63; }
export function panel(copy: number, q: number) {
  'worklet'; const d = copy - q, a = Math.min(1, Math.abs(d)), x = 51 + (d >= 0 ? 304 : 120) * d, width = 287 - 197 * a;
  return { x, y: 294 + 32 * a, width, height: 405 - 63 * a, radius: 40 - 6 * a, artScale: 1 - 0.69 * a, artX: x + width / 2, artY: 438 + 50 * a, z: 1 - a,
    opacity: copy === -1 ? 0 : copy === 4 ? 1 - smooth((q - 4.82) / 0.18) : 1 };
}
export function initialMotion(now = 0, id = 0): Motion {
  'worklet'; return { now, epoch: now, from: null, target: 0, eventTime: now, duration: 0, spring: false, moving: false, lastTouch: now, held: false, panOrigin: 0, id, reducedSelection: 0 };
}
export function selection(m: Motion, autoPlay: boolean, reduced: boolean, previewTime?: number) {
  'worklet'; if (reduced) return m.reducedSelection;
  if (m.from !== null) {
    if (m.held) return m.target;
    const raw = m.duration === 0 ? 1 : (m.now - m.eventTime) / m.duration, progress = m.spring ? spring(raw) : smooth(raw);
    const current = m.from + (m.target - m.from) * progress, recovery = autoPlay ? smooth((m.now - m.lastTouch - 2) / 0.6) : 0;
    const rest = rounded(m.target / 5) * 5; return current + (rest - current) * recovery;
  }
  return automatic(previewTime ?? (autoPlay && m.epoch > 0 ? m.now - m.epoch : 0));
}
export function interact(m: Motion, target: number, now: number, autoPlay: boolean, reduced: boolean, previewTime?: number, duration = 0.6, isSpring = false): Motion {
  'worklet'; const current = selection({ ...m, now }, autoPlay, reduced, previewTime), wrapped = current >= 5 - 0.00000001;
  const from = wrapped ? current - 5 : current, destination = wrapped && target >= 5 ? target - 5 : target;
  return { ...m, now, held: false, from, target: destination, eventTime: now, duration, spring: isSpring, moving: duration > 0, lastTouch: now, id: m.id + 1,
    reducedSelection: reduced ? wrap(rounded(destination)) : m.reducedSelection };
}
