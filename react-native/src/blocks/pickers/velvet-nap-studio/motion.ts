export const LOOP = 8;
export type Point = { x: number; y: number };
export type Field = { id: number; bytes: number[] };
export type Frame = { time: number; authorWeight: number; fields: Field[]; weights: number[]; strength: number };
export const EMPTY: Field = { id: -1, bytes: Array.from({ length: 16384 }, (_, i) => i % 4 === 1 ? 128 : 0) };
export function rest(): Frame { 'worklet'; return { time: 0, authorWeight: 1, fields: [], weights: [], strength: 1 }; }
export function clamp(x: number, lo = 0, hi = 1) { 'worklet'; return Math.min(hi, Math.max(lo, x)); }
export function smooth(x: number) { 'worklet'; const u = clamp(x); return u * u * (3 - 2 * u); }
export function segment(t: number, a: number, b: number) { 'worklet'; return smooth((t - a) / (b - a)); }
export function inverseSmooth(value: number) { 'worklet'; return .5 - Math.sin(Math.asin(1 - 2 * clamp(value)) / 3); }
export function wrap(angle: number) { 'worklet'; return Math.atan2(Math.sin(angle), Math.cos(angle)); }
export function turn(from: number, to: number, mix: number) { 'worklet'; return from + wrap(to - from) * clamp(mix); }
function hash(value: number) { const x = Math.sin((value + 42) * 12.9898) * 43758.5453; return x - Math.floor(x); }
function bezier(t: number): Point { const u = 1 - t; return { x: u * u * u * 50 + 3 * u * u * t * 106 + 3 * u * t * t * 173 + t * t * t * 244, y: u * u * u * 203 + 3 * u * u * t * 190 + 3 * u * t * t * 78 + t * t * t * 65 }; }
function tangent(t: number): Point { const u = 1 - t, x = 3 * u * u * 56 + 6 * u * t * 67 + 3 * t * t * 71, y = 3 * u * u * -13 + 6 * u * t * -112 + 3 * t * t * -13, length = Math.hypot(x, y); return { x: x / length, y: y / length }; }
const PATH = (() => {
  const points: { point: Point; arc: number }[] = [{ point: bezier(0), arc: 0 }]; let length = 0;
  for (let i = 1; i <= 128; i++) { const point = bezier(i / 128), old = points[i - 1].point; length += Math.hypot(point.x - old.x, point.y - old.y); points.push({ point, arc: length }); }
  return points.map(p => ({ point: p.point, arc: p.arc / length }));
})();
export const PATH_LENGTH = PATH.slice(1).reduce((sum, item, i) => sum + Math.hypot(item.point.x - PATH[i].point.x, item.point.y - PATH[i].point.y), 0);
function nearest(p: Point) {
  let best = Number.MAX_VALUE, arc = 0, theta = 0;
  for (let i = 1; i < PATH.length; i++) {
    const a = PATH[i - 1].point, b = PATH[i].point, dx = b.x - a.x, dy = b.y - a.y, h = clamp(((p.x - a.x) * dx + (p.y - a.y) * dy) / (dx * dx + dy * dy)), d = Math.hypot(p.x - a.x - dx * h, p.y - a.y - dy * h);
    if (d < best) { best = d; arc = PATH[i - 1].arc + (PATH[i].arc - PATH[i - 1].arc) * h; theta = Math.atan2(dy, dx); }
  }
  if (arc <= 0) { const a = PATH[0].point, t = tangent(0), along = (p.x - a.x) * t.x + (p.y - a.y) * t.y; if (along < 0) arc = along / PATH_LENGTH; }
  else if (arc >= 1) { const a = PATH[PATH.length - 1].point, t = tangent(1), along = (p.x - a.x) * t.x + (p.y - a.y) * t.y; if (along > 0) arc = 1 + along / PATH_LENGTH; }
  return [arc, best, theta];
}
function fanDelay(p: Point) { let best = Number.MAX_VALUE, delay = 0; for (let k = 0; k < 21; k++) { const d = Math.hypot(p.x - (22 + 263 * hash(2 * k)), p.y - (36 + 194 * hash(2 * k + 1))); if (d < best) { best = d; delay = k % 5 * .04; } } return delay; }
// Double CPU stamping matches frozen Swift. The renderer uses exported Float32 bits separately.
export const GRID = Array.from({ length: 4096 }, (_, i) => { const p = { x: (i % 64) * 307 / 63, y: Math.floor(i / 64) * 274 / 63 }; return [...nearest(p), fanDelay(p)]; });
export function brightness(angle: number) { 'worklet'; return 1.03 + .45 * clamp((Math.cos(angle) * .8 - Math.sin(angle) * .6) / .85, -1, 1); }
export function authored(time: number): Frame { 'worklet'; return { ...rest(), time: Math.max(0, time) % LOOP }; }
export function authoredSample(p: number[], time: number) {
  'worklet'; const [s, distance, angle, delay] = p, t = time % LOOP; if (t <= 1 || t >= 7.55) return 1;
  const taper = smooth((s * PATH_LENGTH + 16) / 16) * smooth(((1 - s) * PATH_LENGTH + 16) / 16), band = (1 - segment(distance, 20, 33)) * taper, reverseBand = (1 - segment(distance, 21, 34)) * taper;
  const forwardLead = Math.sqrt(Math.max(0, 33 * 33 - distance * distance)) / PATH_LENGTH, reverseLead = Math.sqrt(Math.max(0, 34 * 34 - distance * distance)) / PATH_LENGTH;
  const fa = 1 + 1.76 * inverseSmooth(s - forwardLead), ra = 3 + 1.76 * inverseSmooth(1 - s - reverseLead), forward = segment(t, fa, fa + .09), backward = segment(t, ra, ra + .09), relaxation = 1 - segment(t, 5.75 + delay, 7.55);
  const bright = (brightness(angle) - 1) * band * forward, dark = (brightness(angle + Math.PI) - 1) * reverseBand * backward;
  return 1 + (bright * (1 - backward) + dark) * relaxation;
}
export function fieldValue(field: Field, index: number) { 'worklet'; const n = field.bytes[index * 4] | field.bytes[index * 4 + 1] << 8; return n >= 32768 ? 1 + (n - 32768) / 32767 : 1 - (32768 - n) / 32768; }
function fieldAngle(field: Field, index: number) { 'worklet'; return field.bytes[index * 4 + 2] / 255 * 2 * Math.PI - Math.PI; }
function encode(value: number) { 'worklet'; const v = clamp(value, 0, 2); return Math.round(v >= 1 ? 32768 + (v - 1) * 32767 : 32768 - (1 - v) * 32768); }
export function value(frame: Frame, index: number) {
  'worklet'; let delta = (authoredSample(GRID[index], frame.time) - 1) * frame.authorWeight;
  for (let i = 0; i < frame.fields.length; i++) delta += (fieldValue(frame.fields[i], index) - 1) * frame.weights[i]; return 1 + delta * frame.strength;
}
export function baked(frame: Frame): Field {
  'worklet'; const bytes = Array<number>(16384).fill(0);
  for (let i = 0; i < 4096; i++) { const n = encode(value(frame, i)); bytes[i * 4] = n & 255; bytes[i * 4 + 1] = n >>> 8; bytes[i * 4 + 3] = 255; } return { id: 0, bytes };
}
export function manualBaked(frame: Frame) { 'worklet'; return baked({ ...frame, authorWeight: 0 }); }
export function coalesced(frame: Frame): Frame {
  'worklet'; if (frame.fields.length < 3) return frame;
  const manual = { ...rest(), authorWeight: 0, fields: frame.fields, weights: frame.weights, strength: frame.strength };
  return { ...rest(), time: frame.time, authorWeight: frame.authorWeight * frame.strength, fields: [baked(manual)], weights: [1] };
}
export function distance(p: Point, a: Point, b: Point) { 'worklet'; const dx = b.x - a.x, dy = b.y - a.y, length = dx * dx + dy * dy, u = length > 0 ? clamp(((p.x - a.x) * dx + (p.y - a.y) * dy) / length) : 0; return Math.hypot(p.x - a.x - dx * u, p.y - a.y - dy * u); }
export function stamped(field: Field, from: Point, to: Point, angle: number, width: number, baseline: Frame = rest()): Field {
  'worklet'; const bytes = field.bytes.slice();
  for (let i = 0; i < 4096; i++) {
    const p = { x: i % 64 * 307 / 63, y: Math.floor(i / 64) * 274 / 63 }, d = distance(p, from, to), r = width / 2, influence = 1 - segment(d, Math.max(0, r - 7), r + 4); if (influence <= 0) continue;
    const authoredDelta = (authoredSample(GRID[i], baseline.time) - 1) * baseline.authorWeight * baseline.strength, current = fieldValue(field, i) + authoredDelta, ratio = fieldValue(field, i) + (brightness(angle) - current) * influence, n = encode(ratio);
    bytes[i * 4] = n & 255; bytes[i * 4 + 1] = n >>> 8; const theta = turn(fieldAngle(field, i), angle, influence);
    bytes[i * 4 + 2] = Math.round(clamp((wrap(theta) + Math.PI) / (2 * Math.PI)) * 255); bytes[i * 4 + 3] = Math.round(Math.max(bytes[i * 4 + 3] / 255, influence) * 255);
  } return { id: 0, bytes };
}
export function mixed(from: Frame, to: Field, amount: number): Frame {
  'worklet'; const u = clamp(amount); if (u === 0) return from;
  if (u === 1) return { ...rest(), time: from.time, authorWeight: from.authorWeight * from.strength, fields: [to], weights: [1] };
  return { ...from, authorWeight: from.authorWeight * from.strength, weights: [...from.weights.map(w => w * (1 - u) * from.strength), u], fields: [...from.fields, to], strength: 1 };
}
export function recovering(frame: Frame, elapsed: number): Frame { 'worklet'; return elapsed >= .6 ? rest() : { ...frame, strength: frame.strength * (1 - smooth(elapsed / .6)) }; }
