export const LOOP = 16 / 3;
const TRACKS = require('./assets/motion.json') as number[][][];

export type Frame = {
  q: number[]; qVel: number[]; handBX: number; handBY: number; handAngle: number; handAlpha: number;
  grip: number; gatherX: number; gatherY: number; reliefAmplitude: number; fanWidth: number; tailY: number; scalarVel: number[];
};
type TargetEvent = { time: number; target: Frame; expanding: boolean; scalarTarget: number[] };
export type Drive = { initial: Frame; origin: number; events: TargetEvent[] };
export type Motion = { now: number; epoch: number; id: number; drive: Drive | null; lastTouch: number; held: boolean; hero: boolean; panStart: Frame; detent: boolean; inputSettled: boolean; searchSnapshot: Frame | null };

export function clamp(x: number, a = 0, b = 1) { 'worklet'; return Math.min(b, Math.max(a, x)); }
export function mix(a: number, b: number, u: number) { 'worklet'; return a + (b - a) * u; }
export function smooth(x: number) { 'worklet'; const u = clamp(x); return u * u * (3 - 2 * u); }
export function smoother(x: number) { 'worklet'; const u = clamp(x); return u * u * u * (10 + u * (-15 + 6 * u)); }
export function smootherSlope(x: number) { 'worklet'; const u = clamp(x); return 30 * u * u * (1 - u) * (1 - u); }
export function softClamp(x: number) {
  'worklet'; const u = clamp(x), edge = 0.15;
  const end = (t: number) => { 'worklet'; return t * t * t * (6 + t * (-8 + 3 * t)); };
  if (u < edge) return edge * end(u / edge);
  if (u > 1 - edge) return 1 - edge * end((1 - u) / edge);
  return u;
}
export function rotated(x: number, y: number, degrees: number): [number, number] {
  'worklet'; const a = degrees * Math.PI / 180; return [x * Math.cos(a) - y * Math.sin(a), x * Math.sin(a) + y * Math.cos(a)];
}
function oscillator(x: number, v: number, target: number, time: number, z: number, w: number): [number, number] {
  'worklet'; if (time <= 0) return [x, v];
  const a = z * w, d = w * Math.sqrt(1 - z * z), b = (v + a * (x - target)) / d;
  const c = Math.cos(d * time), s = Math.sin(d * time), e = Math.exp(-a * time), f = (x - target) * c + b * s;
  return [target + e * f, e * (-a * f - (x - target) * d * s + b * d * c)];
}
function hermite(x: number, v: number, y: number, w: number, elapsed: number, duration: number): [number, number] {
  'worklet'; const u = clamp(elapsed / duration), u2 = u * u, u3 = u2 * u;
  return [(2 * u3 - 3 * u2 + 1) * x + (u3 - 2 * u2 + u) * duration * v + (-2 * u3 + 3 * u2) * y + (u3 - u2) * duration * w,
    ((6 * u2 - 6 * u) * x + (3 * u2 - 4 * u + 1) * duration * v + (-6 * u2 + 6 * u) * y + (3 * u2 - 2 * u) * duration * w) / duration];
}
export function rubber(q: number) {
  'worklet'; if (q >= 0 && q <= 1) return q;
  const edge = q < 0 ? 0 : 1, over = (q - edge) * 148;
  return edge + 0.55 * over / (1 + Math.abs(over) / 120) / 148;
}
function values(f: Frame) {
  'worklet'; return [f.handBX, f.handBY, f.handAngle, f.handAlpha, f.grip, f.gatherX, f.gatherY, f.reliefAmplitude, f.fanWidth, f.tailY];
}
function setValues(f: Frame, v: number[]) {
  'worklet'; f.handBX = v[0]; f.handBY = v[1]; f.handAngle = v[2]; f.handAlpha = v[3]; f.grip = v[4];
  f.gatherX = v[5]; f.gatherY = v[6]; f.reliefAmplitude = v[7]; f.fanWidth = v[8]; f.tailY = v[9];
}
export function rest(): Frame {
  'worklet'; return { q: [0, 0, 0, 0, 0, 0], qVel: [0, 0, 0, 0, 0, 0], handBX: 198, handBY: -90, handAngle: 0,
    handAlpha: 0, grip: 0, gatherX: 190, gatherY: -90, reliefAmplitude: 0, fanWidth: 0, tailY: 120, scalarVel: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0] };
}
export function open(q: number, angle = 0): Frame {
  'worklet'; const f = rest(); f.q = [q, q, q, q, q, q]; f.handBX = 204; f.handBY = -90 + 286 * q; f.handAngle = angle;
  f.handAlpha = smoother((f.handBY - 20) / 26); const r = rotated(4, 16, angle);
  f.gatherX = f.handBX - r[0]; f.gatherY = f.handBY - r[1]; f.tailY = 263; return f;
}
export function mixed(a: Frame, b: Frame, u: number): Frame {
  'worklet'; const f = rest(), av = values(a), bv = values(b);
  f.q = a.q.map((v, i) => mix(v, b.q[i], u)); f.qVel = a.qVel.map((v, i) => mix(v, b.qVel[i], u));
  setValues(f, av.map((v, i) => mix(v, bv[i], u))); f.scalarVel = a.scalarVel.map((v, i) => mix(v, b.scalarVel[i], u)); return f;
}
function bridged(a: Frame, b: Frame, elapsed: number, duration: number): Frame {
  'worklet'; const f = rest(), av = values(a), bv = values(b), v = [...av];
  for (let i = 0; i < 6; i++) { const p = hermite(a.q[i], a.qVel[i], b.q[i], b.qVel[i], elapsed, duration); f.q[i] = p[0]; f.qVel[i] = p[1]; }
  for (let i = 0; i < 10; i++) { const p = hermite(av[i], a.scalarVel[i], bv[i], b.scalarVel[i], elapsed, duration); v[i] = p[0]; f.scalarVel[i] = p[1]; }
  setValues(f, v); return f;
}
function track(keys: number[][], t: number): [number, number] {
  'worklet'; if (keys.length < 2) return [0, 0];
  if (t <= keys[0][0]) return [keys[0][1], keys[0][2]];
  const last = keys[keys.length - 1]; if (t >= last[0]) return [last[1], last[2]];
  let lo = 0, hi = keys.length - 1;
  while (hi - lo > 1) { const m = Math.floor((lo + hi) / 2); if (keys[m][0] <= t) lo = m; else hi = m; }
  const a = keys[lo], b = keys[hi]; return hermite(a[1], a[2], b[1], b[2], t - a[0], b[0] - a[0]);
}
function pchip(points: number[][]) {
  const out = points.map(p => [p[0], p[1], 0]);
  for (let i = 1; i < points.length - 1; i++) {
    const h0 = points[i][0] - points[i - 1][0], h1 = points[i + 1][0] - points[i][0];
    const a = (points[i][1] - points[i - 1][1]) / h0, b = (points[i + 1][1] - points[i][1]) / h1;
    if (a * b > 0) { const w0 = 2 * h1 + h0, w1 = h1 + 2 * h0; out[i][2] = (w0 + w1) / (w0 / a + w1 / b); }
  }
  return out;
}
const TAIL = pchip([[0, 263], [0.8, 263], [0.9, 260], [1, 255], [1.1, 251], [1.2, 253], [1.3, 255], [1.4, 205], [1.5, 160], [1.6, 120], [3.4, 120], [3.8, 263], [LOOP, 263]]);
export function automatic(seconds: number): Frame {
  'worklet'; const t = Math.max(0, seconds) % LOOP; if (TRACKS.length !== 13) return rest();
  const s = TRACKS.map(keys => track(keys, t)), f = rest();
  f.q = [s[0][0], s[1][0], s[2][0], s[3][0], s[4][0], s[4][0]];
  f.qVel = [s[0][1], s[1][1], s[2][1], s[3][1], s[4][1], s[4][1]];
  f.handBX = s[5][0]; f.handBY = s[6][0]; f.handAngle = s[7][0]; f.grip = s[8][0]; f.gatherX = s[9][0]; f.gatherY = s[10][0]; f.reliefAmplitude = s[11][0]; f.fanWidth = s[12][0];
  f.scalarVel = [s[5][1], s[6][1], s[7][1], 0, s[8][1], s[9][1], s[10][1], s[11][1], s[12][1], 0];
  const u = clamp((f.grip - 0.85) / 0.15), lock = smoother(u), lockV = smootherSlope(u) * f.scalarVel[4] / 0.15;
  const r = rotated(4, 16, f.handAngle), angleV = f.scalarVel[2] * Math.PI / 180;
  const bx = f.gatherX + r[0], by = f.gatherY + r[1], bxV = f.scalarVel[5] - r[1] * angleV, byV = f.scalarVel[6] + r[0] * angleV;
  f.scalarVel[0] = (1 - lock) * f.scalarVel[0] + lock * bxV + lockV * (bx - f.handBX);
  f.scalarVel[1] = (1 - lock) * f.scalarVel[1] + lock * byV + lockV * (by - f.handBY);
  f.handBX = mix(f.handBX, bx, lock); f.handBY = mix(f.handBY, by, lock);
  const visibility = clamp((f.handBY - 20) / 26); f.handAlpha = smoother(visibility);
  f.scalarVel[3] = smootherSlope(visibility) / 26 * f.scalarVel[1];
  const reach = track(TAIL, (t + 2.2) % LOOP); f.tailY = reach[0]; f.scalarVel[9] = reach[1]; return f;
}
function source(time: number) { 'worklet'; let native = (time - 2.2) % LOOP; if (native < 0) native += LOOP; return automatic(native); }
function recoveryPhase(origin: Frame) { 'worklet'; return 0.55 + 0.25 * clamp(origin.grip); }
export function recoveryDuration(origin: Frame) { 'worklet'; return 0.2 + 2.2 - recoveryPhase(origin); }
function recovery(elapsed: number, origin: Frame): Frame {
  'worklet'; const phase = recoveryPhase(origin), first = source(phase); if (elapsed >= recoveryDuration(origin)) return automatic(0);
  const mapped = (time: number): Frame => {
    'worklet'; const f = source(time);
    for (let i = 0; i < 6; i++) { f.q[i] *= origin.q[i]; f.qVel[i] *= origin.q[i]; }
    const amplitude = clamp(Math.max(origin.q[0], origin.reliefAmplitude / 24), 0, 1.2); f.reliefAmplitude *= amplitude; f.scalarVel[7] *= amplitude;
    const u = clamp((time - 1.6) / 0.6), fade = 1 - u * u * u * (10 + u * (-15 + 6 * u));
    const fadeV = u > 0 && u < 1 ? -30 * u * u * (1 - u) * (1 - u) / 0.6 : 0;
    const dx = origin.handBX - first.handBX, dy = Math.min(0, origin.handBY - first.handBY);
    f.handBX += dx * fade; f.handBY += dy * fade; f.gatherX += dx * fade; f.gatherY += dy * fade;
    f.scalarVel[0] += dx * fadeV; f.scalarVel[1] += dy * fadeV; f.scalarVel[5] += dx * fadeV; f.scalarVel[6] += dy * fadeV;
    const startVisibility = smoother((Math.min(origin.handBY, first.handBY) - 20) / 26), uVisibility = clamp((f.handBY - 20) / 26);
    const visibility = smoother(uVisibility), visibilityV = smootherSlope(uVisibility) / 26 * f.scalarVel[1];
    const ratio = startVisibility > 0.00001 ? clamp(visibility / startVisibility) : 0;
    f.handAlpha = origin.handAlpha * smoother(ratio); f.scalarVel[3] = ratio > 0 && ratio < 1 ? origin.handAlpha * smootherSlope(ratio) * visibilityV / startVisibility : 0; return f;
  };
  return elapsed < 0.2 ? bridged(origin, mapped(phase), elapsed, 0.2) : mapped(phase + elapsed - 0.2);
}
export function driveFrame(drive: Drive, now: number): Frame {
  'worklet'; const f = rest(), initialValues = values(drive.initial), resultValues = [...initialValues];
  for (let i = 0; i < 16; i++) {
    const band = i < 6, j = band ? i : i - 6;
    let x = band ? drive.initial.q[j] : initialValues[j], v = band ? drive.initial.qVel[j] : drive.initial.scalarVel[j];
    let target = x, date = drive.origin, z = 0.69, omega = 5;
    for (const event of drive.events) {
      const delay = band ? Math.min(j, 4) * (event.expanding ? 1 / 30 : 2 / 30) : 0, arrival = event.time + delay;
      if (arrival > now) break;
      const p = oscillator(x, v, target, Math.max(0, arrival - date), z, omega); x = p[0]; v = p[1]; date = arrival;
      target = band ? event.target.q[j] : event.scalarTarget[j]; z = event.expanding ? 0.69 : 0.64; omega = event.expanding ? 5 : 7.1;
    }
    const p = oscillator(x, v, target, Math.max(0, now - date), z, omega);
    if (band) { f.q[j] = p[0]; f.qVel[j] = p[1]; } else { resultValues[j] = p[0]; f.scalarVel[j] = p[1]; }
  }
  setValues(f, resultValues); f.q[5] = f.q[4]; f.qVel[5] = f.qVel[4]; return f;
}
export function append(drive: Drive, target: Frame, time: number, expanding: boolean): Drive {
  'worklet'; const base: Drive = drive.events.length >= 96 ? { initial: driveFrame(drive, time), origin: time, events: [] } : drive;
  return { ...base, events: [...base.events, { time, target, expanding, scalarTarget: values(target) }] };
}
export function newDrive(initial: Frame, now: number): Drive { 'worklet'; return { initial, origin: now, events: [] }; }
export function initialMotion(now = 0, id = 0): Motion {
  'worklet'; return { now, epoch: now, id, drive: null, lastTouch: now, held: false, hero: false, panStart: rest(), detent: false, inputSettled: true, searchSnapshot: null };
}
export function sample(m: Motion, autoPlay: boolean, reduced: boolean, searching: boolean, previewTime?: number): Frame {
  'worklet'; if (reduced) return rest(); if (previewTime !== undefined) return automatic(previewTime); if (m.searchSnapshot) return m.searchSnapshot;
  if (!m.drive) return automatic(autoPlay && m.epoch > 0 ? m.now - m.epoch : 0);
  const current = driveFrame(m.drive, m.now); if (m.held || searching) return current;
  const idle = m.now - m.lastTouch - 2; if (idle <= 0) return current;
  const origin = driveFrame(m.drive, m.lastTouch + 2), duration = recoveryDuration(origin);
  return idle < duration ? recovery(idle, origin) : automatic(autoPlay ? idle - duration : 0);
}
export function gridClipTop(f: Frame) {
  'worklet'; const desired = Math.min(381, 389 + 148 * Math.min(0, f.q[4])); return Math.max(desired, Math.min(381, 379 + 148 * f.q[3]));
}
