export const LOOP = 8;
export type Vec3 = [number, number, number];
export type Pose = { p: number; rotation: Vec3; lagRotation: Vec3; lift: number; hem: number; anticipation: number; light: number; lightPosition: number; crownElapsed: number; topologySpeed: number };
export type Action = { stamp: number; from: Pose; target: number; velocity: number; kick: number; saving: boolean };
export type Motion = { now: number; epoch: number; dragging: boolean; captured: Pose; finger: Pose; action: Action | null; pattern: number; yarn: number; material: number[]; materialStarted: number; materialRows: number; saved: boolean; saveStarted: number; lastDetent: number; lastHaptic: number };
export const YARNS = ['#DEDCD3', '#889A8C', '#7D8C92'];
export function clamp(x: number, lo = 0, hi = 1) { 'worklet'; return Math.max(lo, Math.min(hi, x)); }
export function smooth(x: number) { 'worklet'; x = clamp(x); return x * x * (3 - 2 * x); }
export function hash(i: number) { 'worklet'; const n = Math.sin(i * 12.9898 + 4.1414) * 43758.5453; return n - Math.floor(n); }
export function bezier(x: number, x1: number, y1: number, x2: number, y2: number) {
  'worklet'; if (x <= 0) return 0; if (x >= 1) return 1;
  let lo = 0, hi = 1;
  for (let i = 0; i < 18; i++) { const u = (lo + hi) / 2, a = 1 - u, value = 3 * a * a * u * x1 + 3 * a * u * u * x2 + u * u * u; if (value < x) lo = u; else hi = u; }
  const u = (lo + hi) / 2, a = 1 - u; return 3 * a * a * u * y1 + 3 * a * u * u * y2 + u * u * u;
}
export function io(x: number) { 'worklet'; return bezier(x, .65, 0, .35, 1); }
export function entrance(x: number) { 'worklet'; return bezier(x, .2, .8, .2, 1); }
export function base(p: number): Pose {
  'worklet'; p = clamp(p); const r: Vec3 = [30 - 22 * p, -10 + 16 * p, -7 + 4 * p];
  return { p, rotation: r, lagRotation: [...r], lift: 18 * Math.sin(Math.PI * p), hem: 0, anticipation: 0, light: 0, lightPosition: 8.3, crownElapsed: 0, topologySpeed: 1 };
}
export function finished(): Pose { 'worklet'; return { ...base(1), crownElapsed: 10 }; }
export function spring(from: number, to: number, velocity: number, time: number, response: number, damping: number) {
  'worklet'; const w = 2 * Math.PI / response, a = damping * w, b = w * Math.sqrt(1 - damping * damping), d = from - to;
  return to + Math.exp(-a * time) * (d * Math.cos(b * time) + (velocity + a * d) / b * Math.sin(b * time));
}
export function decay(time: number, response: number, damping: number) { 'worklet'; return spring(1, 0, 0, time, response, damping); }
function progress(t: number) { 'worklet'; if (t < 1.23) return 0; if (t < 3.95) return io((t - 1.23) / 2.72); if (t < 5.2) return 1; return 1 - io((t - 5.2) / 1.9); }
function orientation(t: number): Vec3 { 'worklet'; const q = t < 5.2 ? io((t - 1.23) / 2.82) : 1 - io((t - 5.2) / 1.9); return [30 - 22 * q, -10 + 16 * q, -7 + 4 * q]; }
export function authored(time: number): Pose {
  'worklet'; const t = Math.max(0, time) % LOOP, p = base(progress(t)); p.rotation = orientation(t); p.lagRotation = orientation(Math.max(0, t - .07)); p.lift = 0;
  if (t >= 1.23 && t < 3.95) p.lift = 18 * entrance((t - 1.23) / .32);
  if (t >= 3.95 && t < 4.45) p.lift = 18 * decay(t - 3.95, .45, .76) * (1 - smooth((t - 4.30) / .15));
  if (t >= 5.2 && t < 7.1) p.lift = 18 * Math.sin(Math.PI * p.p);
  if (t >= 1.1 && t < 1.23) { p.anticipation = Math.sin(Math.PI * smooth((t - 1.1) / .13)); p.hem = -4 * p.anticipation; }
  if (t >= 6.85 && t < 7.1) p.hem = 2 * io((t - 6.85) / .25);
  if (t >= 7.1 && t < 7.3) p.hem = 2 * decay(t - 7.1, .25, .82) * (1 - smooth((t - 7.2) / .1));
  if (t >= 4.15 && t < 4.9) { const q = (t - 4.15) / .75; p.light = .14 * Math.sin(Math.PI * q); p.lightPosition = 8.3 + 376.4 * smooth(q); }
  p.crownElapsed = t >= 3.95 && t < 5.2 ? t - 3.95 : 0; p.topologySpeed = t < 5.2 ? 1.9 / 2.72 : 1;
  return p;
}
export function moved(from: Pose, p: number): Pose {
  'worklet'; const old = base(from.p), next = base(p);
  for (let i = 0; i < 3; i++) { next.rotation[i] += from.rotation[i] - old.rotation[i]; next.lagRotation[i] += from.lagRotation[i] - old.lagRotation[i]; }
  return { ...next, lift: next.lift + from.lift - old.lift, hem: from.hem, anticipation: from.anticipation, light: from.light, lightPosition: from.lightPosition, crownElapsed: from.crownElapsed, topologySpeed: from.topologySpeed };
}
export function actionPose(a: Action, now: number): Pose {
  'worklet'; const age = Math.max(0, now - a.stamp); if (age >= 2.65) return base(0); if (age >= 2) return base(a.target * (1 - io((age - 2) / .65)));
  const p = a.saving ? a.from.p + (a.target - a.from.p) * entrance(age / .48) : spring(a.from.p, a.target, a.velocity, age, .36, .82), r = base(clamp(p)), old = base(a.from.p), d = decay(age, .44, .7);
  for (let i = 0; i < 3; i++) { r.rotation[i] += (a.from.rotation[i] - old.rotation[i]) * d; r.lagRotation[i] += (a.from.lagRotation[i] - old.lagRotation[i]) * d; }
  r.lift += (a.from.lift - old.lift) * d + a.kick * Math.exp(-10 * age) * Math.sin(16 * age); r.hem = a.from.hem * decay(age, .25, .82); r.anticipation = a.from.anticipation * Math.exp(-14 * age); r.light = a.from.light * Math.exp(-8 * age); r.lightPosition = a.from.lightPosition; r.crownElapsed = a.from.crownElapsed + age; r.topologySpeed = a.from.topologySpeed; return r;
}
export function drive(m: Motion, autoPlay: boolean, reduced: boolean, previewTime?: number): Pose {
  'worklet'; if (reduced) return finished(); if (previewTime !== undefined) return authored(Math.max(0, previewTime)); if (m.dragging) return m.finger; if (m.action) return actionPose(m.action, m.now); return autoPlay ? authored(Math.max(0, m.now - m.epoch)) : base(0);
}
export function resetAmount(m: Motion, reduced: boolean, previewTime?: number) { 'worklet'; return reduced || previewTime !== undefined || m.dragging || !m.action ? 0 : io((m.now - m.action.stamp - 2) / .65); }
export function canonicalTime(p: number) {
  'worklet'; if (p <= 0) return 0; if (p >= 1) return 1.9; let lo = 0, hi = 1;
  for (let i = 0; i < 18; i++) { const u = (lo + hi) / 2; if (smooth(u) < p) lo = u; else hi = u; }
  const u = (lo + hi) / 2, a = 1 - u; return 1.9 * (3 * a * a * u * .65 + 3 * a * u * u * .35 + u * u * u);
}
export function columnDelay(column: number) { 'worklet'; return .0025 * (column % 4) + .004 * (2 * hash(column) - 1); }
export function columnRows(p: number, canonical: number, column: number) { 'worklet'; if (p <= 0) return 3; if (p >= 1) return 48; return 3 + 45 * io((canonical - columnDelay(column)) / 1.9); }
export function solidMaterial(pattern: number, yarn: number) { 'worklet'; const a: number[] = []; for (let row = 0; row < 48; row++) for (let c = 0; c < 6; c++) a.push(c === pattern || c === yarn + 3 ? 1 : 0); return a; }
export function materialMix(row: number, age: number, rows: number, reduced: boolean) { 'worklet'; if (age < 0) return 0; if (reduced) return smooth(age / .18); const lag = clamp((rows - 1 - row) / Math.max(1, rows - 1)) * .22; return smooth((age - lag) / .16); }
export function freezeMaterial(m: Motion, reduced: boolean, previewTime?: number): number[] {
  'worklet'; const age = m.materialStarted >= 0 ? Math.max(0, m.now - m.materialStarted) : -1, reset = resetAmount(m, reduced, previewTime), result: number[] = [];
  for (let row = 0; row < 48; row++) { const wave = materialMix(row, age, m.materialRows, reduced); for (let c = 0; c < 6; c++) { const target = c === m.pattern || c === m.yarn + 3 ? 1 : 0, value = m.material[row * 6 + c] * (1 - wave) + target * wave; result.push(value * (1 - reset) + (c === 0 || c === 3 ? reset : 0)); } } return result;
}
export function rotationMatrix(r: Vec3) {
  'worklet'; const [x, y, z] = r.map(n => n * Math.PI / 180), cx = Math.cos(x), sx = Math.sin(x), cy = Math.cos(y), sy = Math.sin(y), cz = Math.cos(z), sz = Math.sin(z);
  return [cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx, sz * cy, sz * sy * sx + cz * cx, sz * sy * cx - cz * sx, -sy, cy * sx, cy * cx];
}
