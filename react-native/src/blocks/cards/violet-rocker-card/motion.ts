export const LOOP = 10;
export const EVENTS = [1, 2.2, 3.25, 4.45, 5.45, 6.65, 7.65, 8.85];
export type Point = { x: number; y: number };
export type Frame = {
  n: number; c: number; scale: number; originX: number; originY: number;
  snap: number; time: number | null; flat: boolean; cameraZ: number | null; cameraX: number | null;
};
export type Motion = {
  now: number; epoch: number; manual: boolean; held: boolean; dragged: boolean;
  camera: Frame; originalOn: boolean; heldNight: number; releaseFrom: number; releaseTarget: number;
  releaseVelocity: number; releaseDate: number; lastTouch: number; moving: boolean; reducedNight: number;
};
export type Projection = { a: number; b: number; d: number; e: number; g: number; h: number; tx: number; ty: number; w: number; originX: number; originY: number };

export function clamp(x: number, lo = 0, hi = 1) { 'worklet'; return Math.min(hi, Math.max(lo, x)); }
export function smooth(x: number) { 'worklet'; const u = clamp(x); return u * u * (3 - 2 * u); }
export function seg(t: number, a: number, b: number) { 'worklet'; return smooth((t - a) / (b - a)); }
export function rest(): Frame {
  'worklet';
  return { n: 0, c: 0, scale: 1.223, originX: 187, originY: 431, snap: 0, time: null, flat: false, cameraZ: null, cameraX: null };
}
export function rock(f: Frame) { 'worklet'; return f.flat ? 0 : -9 + 18 * f.n + f.snap; }
export function automatic(time: number): Frame {
  'worklet';
  const t = Math.max(0, time) % LOOP;
  let n = 0, extra = 0;
  for (let i = 0; i < EVENTS.length; i++) {
    const start = EVENTS[i], direction = i % 2 === 0 ? 1 : -1;
    n += direction * seg(t, start - 0.09, start + 0.09);
    const u = clamp((t - (start - 0.09)) / ((start + 0.09) - (start - 0.09)));
    extra += direction * 1.2 * 16 * u * u * (1 - u) * (1 - u) * Math.sin(2 * Math.PI * u);
  }
  const f = { ...rest(), n, snap: extra, time: t };
  if (t < 4.45) return f;
  if (t < 6.65) return { ...f, scale: 2.7, originX: -40, originY: 350, cameraZ: 90, cameraX: 20 };
  const c = 1 - seg(t, 9.1, 9.6);
  return { ...f, c, scale: 1.223 + 0.577 * c, originX: 187 - 13 * c, originY: 431 - 5 * c };
}
export function spring(age: number, from: number, to: number, velocity: number) {
  'worklet';
  if (age >= 0.64) return to;
  const t = Math.max(0, age), omega = 2 * Math.PI / 0.32, zeta = 0.78;
  const damp = omega * zeta, wd = omega * Math.sqrt(1 - zeta * zeta), delta = from - to;
  const x = Math.exp(-damp * t) * (delta * Math.cos(wd * t) + (velocity + damp * delta) / wd * Math.sin(wd * t));
  return clamp(to + x * (1 - seg(t, 0.48, 0.64)), -1 / 15, 16 / 15);
}
export function projection(f: Frame, angle: number, depth: number): Projection {
  'worklet';
  const az = (f.cameraZ ?? 55 * (1 - f.c)) * Math.PI / 180;
  const ac = (f.cameraX ?? 50.2 * (1 - f.c)) * Math.PI / 180;
  const ar = angle * Math.PI / 180;
  const cz = Math.cos(az), sz = Math.sin(az), cc = Math.cos(ac), sc = Math.sin(ac), cr = Math.cos(ar), sr = Math.sin(ar);
  const a = f.scale * cz, b = -f.scale * sz * cr;
  const d = f.scale * cc * sz, e = f.scale * (cc * cz * cr + sc * sr);
  const g = -sc * sz / 900, h = -(sc * cz * cr - cc * sr) / 900;
  return { a, b, d, e, g, h, tx: -50 * a - 130 * b, ty: -50 * d - 130 * e - f.scale * sc * depth,
    w: 1 - cc * depth / 900 - 50 * g - 130 * h, originX: f.originX, originY: f.originY };
}
export function point(p: Projection, local: Point): Point {
  'worklet';
  const u = local.x + 50, v = local.y + 130, divisor = p.g * u + p.h * v + p.w;
  return { x: p.originX + (p.a * u + p.b * v + p.tx) / divisor, y: p.originY + (p.d * u + p.e * v + p.ty) / divisor };
}
export function local(p: Projection, screen: Point): Point | null {
  'worklet';
  const x = screen.x - p.originX, y = screen.y - p.originY;
  const aa = p.a - x * p.g, bb = p.b - x * p.h, dd = p.d - y * p.g, ee = p.e - y * p.h;
  const xx = x * p.w - p.tx, yy = y * p.w - p.ty, determinant = aa * ee - bb * dd;
  if (Math.abs(determinant) <= 0.000001) return null;
  return { x: (xx * ee - bb * yy) / determinant - 50, y: (aa * yy - xx * dd) / determinant - 130 };
}
export function matrix(p: Projection): number[] {
  'worklet';
  // Translate after perspective division, matching Swift's projectionEffect().offset().
  return [p.a + p.originX * p.g, p.b + p.originX * p.h, p.tx + p.originX * p.w,
    p.d + p.originY * p.g, p.e + p.originY * p.h, p.ty + p.originY * p.w, p.g, p.h, p.w];
}
export function contains(p: Point | null) {
  'worklet';
  if (!p) return false;
  const x = Math.abs(p.x), y = Math.abs(p.y);
  return x <= 50 && y <= 130 && (y <= 80 || x * x + (y - 80) * (y - 80) <= 2500);
}
export function dot(f: Frame, index: number) {
  'worklet';
  const base = index === 18 ? 0.25 : 0.04, n = clamp(f.n);
  if (f.time === null) return base + (1 - base) * n;
  let active = 0;
  for (let i = 0; i < EVENTS.length; i++) {
    const delay = 0.008 + (18 - index) * 0.004;
    active += (i % 2 === 0 ? 1 : -1) * seg(f.time, EVENTS[i] - 0.09 + delay, EVENTS[i] - 0.09 + delay + 0.1);
  }
  return base + (1 - base) * active;
}
export function initialMotion(now = 0): Motion {
  'worklet';
  return { now, epoch: -1, manual: false, held: false, dragged: false, camera: rest(), originalOn: false,
    heldNight: 0, releaseFrom: 0, releaseTarget: 0, releaseVelocity: 0, releaseDate: now, lastTouch: now,
    moving: false, reducedNight: 1 };
}
export function sample(m: Motion, autoPlay: boolean, reduced: boolean, previewTime?: number): Frame {
  'worklet';
  if (reduced) return { ...rest(), n: m.reducedNight, c: 1, scale: 1.8, originX: 174, originY: 426, flat: true };
  if (m.manual) {
    const age = m.now - m.releaseDate;
    const n = m.held ? m.heldNight : spring(age, m.releaseFrom, m.releaseTarget, m.releaseVelocity);
    const recovery = m.held || !autoPlay ? 0 : smooth((m.now - m.lastTouch - 2) / 0.6);
    const f = m.camera;
    return { ...rest(), n: n * (1 - recovery), c: f.c * (1 - recovery),
      scale: f.scale + (1.223 - f.scale) * recovery, originX: f.originX + (187 - f.originX) * recovery,
      originY: f.originY + (431 - f.originY) * recovery,
      snap: f.snap * (m.held ? 1 : 1 - seg(age, 0, 0.32)) * (1 - recovery),
      cameraZ: (f.cameraZ ?? 55 * (1 - f.c)) + (55 - (f.cameraZ ?? 55 * (1 - f.c))) * recovery,
      cameraX: (f.cameraX ?? 50.2 * (1 - f.c)) + (50.2 - (f.cameraX ?? 50.2 * (1 - f.c))) * recovery };
  }
  return automatic(previewTime ?? (autoPlay && m.epoch >= 0 ? m.now - m.epoch : 0));
}
export function select(m: Motion, target: number, now: number, autoPlay: boolean, reduced: boolean): Motion {
  'worklet';
  if (reduced) return { ...m, now, reducedNight: target };
  const f = sample({ ...m, now }, autoPlay, false);
  return { ...m, now, manual: true, held: false, camera: f, releaseFrom: f.n, releaseTarget: target,
    releaseVelocity: 0, releaseDate: now, lastTouch: now, moving: true };
}
export function begin(m: Motion, screen: Point, now: number, autoPlay: boolean): Motion | null {
  'worklet';
  const f = sample({ ...m, now }, autoPlay, false);
  if (!contains(local(projection(f, rock(f), 18), screen))) return null;
  return { ...m, now, manual: true, held: true, dragged: false, camera: f,
    originalOn: f.n >= 0.5, heldNight: f.n, moving: true, lastTouch: now };
}
export function finish(m: Motion, target: number, velocity: number, now: number): Motion {
  'worklet';
  if (!m.held) return m;
  return { ...m, now, releaseFrom: m.heldNight, releaseTarget: target, releaseVelocity: clamp(velocity, -1.8, 1.8),
    held: false, releaseDate: now, lastTouch: now, moving: true };
}
