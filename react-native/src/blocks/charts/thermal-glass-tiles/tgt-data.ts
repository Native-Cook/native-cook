import { clamp, mix } from './tgt-motion';
export type RunSample = { distance: number; elapsed: number; bpm: number; pace: number; contact: number; oscillation: number };
/** Original plausible 29.6 km dataset, 296 intervals; no network or personal data. */
export const RUN: RunSample[] = Array.from({ length: 297 }, (_, i) => {
  const p = i / 296;
  const hill = Math.exp(-Math.pow((p - 0.62) / 0.12, 2));
  return {
    distance: p * 29.6,
    elapsed: p * 10061,
    bpm: 118 + 23 * p + 40 * hill,
    pace: i === 296 ? 376 : 376 - 96 * Math.exp(-Math.pow((p - 0.69) / 0.17, 2)),
    contact: 270 + 24 * Math.exp(-Math.pow((p - 0.39) / 0.11, 2)) - 19 * hill,
    oscillation: 10 + 1.7 * Math.exp(-Math.pow((p - 0.76) / 0.09, 2)) - 0.5 * hill,
  };
});
export function sampleAt(data: RunSample[], p: number): RunSample {
  'worklet';
  if (!data.length) return { distance: 0, elapsed: 0, bpm: 118, pace: 376, contact: 270, oscillation: 10 };
  const at = clamp(p) * (data.length - 1),
    i = Math.floor(at),
    f = at - i;
  const a = data[i],
    b = data[Math.min(data.length - 1, i + 1)];
  return {
    distance: mix(a.distance, b.distance, f),
    elapsed: mix(a.elapsed, b.elapsed, f),
    bpm: mix(a.bpm, b.bpm, f),
    pace: mix(a.pace, b.pace, f),
    contact: mix(a.contact, b.contact, f),
    oscillation: mix(a.oscillation, b.oscillation, f),
  };
}
export function effortAt(sample: RunSample) {
  'worklet';
  return clamp((sample.bpm - 118) / 58);
}
export function timeText(seconds: number) {
  'worklet';
  const sec = Math.max(0, Math.round(seconds));
  return `${Math.floor(sec / 60)}:${String(sec % 60).padStart(2, '0')}`;
}
/** A different route, redrawn in the source's proportions. */
export const ROUTE =
  'M 214 245 C 204 240 211 259 195 261 C 182 258 181 249 169 253 C 155 257 158 278 149 292 C 143 300 130 305 132 317 C 135 332 144 339 155 331 C 168 325 178 332 194 327 L 203 323 L 215 328 L 229 335 L 265 263 L 233 245 C 229 255 223 252 214 245 Z';
export type RoutePoint = { x: number; y: number };
export function routePoint(points: RoutePoint[], p: number): RoutePoint {
  'worklet';
  if (!points.length) return { x: 214, y: 245 };
  const at = clamp(p) * (points.length - 1),
    i = Math.floor(at),
    f = at - i;
  const a = points[i],
    b = points[Math.min(points.length - 1, i + 1)];
  return { x: mix(a.x, b.x, f), y: mix(a.y, b.y, f) };
}
export function nearestRoute(points: RoutePoint[], x: number, y: number, p: number, all = false) {
  'worklet';
  let best = p,
    d = 1e10;
  const lo = all ? 0 : Math.max(0, Math.floor((p - 0.12) * (points.length - 1)));
  const hi = all ? points.length - 1 : Math.min(points.length - 1, Math.ceil((p + 0.12) * (points.length - 1)));
  for (let i = lo; i <= hi; i++) {
    const pt = points[i],
      dd = (pt.x - x) ** 2 + (pt.y - y) ** 2;
    if (dd < d) {
      d = dd;
      best = i / (points.length - 1);
    }
  }
  return { p: best, distance: Math.sqrt(d) };
}
