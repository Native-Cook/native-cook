export const LOOP = 8;
export type Point = { x: number; y: number };
export type Pose = { yaw: number; pitch: number; roll: number };
export type Frame = { pose: Pose; angularVelocity: Pose; explicitViewB: number | null; collectionMix: number };
export type Support = { q0: number; dq0: number; k: number };
export type SupportSample = { latent: number; latentVelocity: number; angle: number; velocity: number };

export function clamp(x: number, low = 0, high = 1) { 'worklet'; return Math.min(high, Math.max(low, x)); }
export function smooth(x: number) { 'worklet'; const q = clamp(x); return q * q * (3 - 2 * q); }
export function smoothRate(x: number) { 'worklet'; return x > 0 && x < 1 ? 6 * x * (1 - x) : 0; }
export function zeroPose(): Pose { 'worklet'; return { yaw: 0, pitch: 0, roll: 0 }; }
export function restPose(): Pose { 'worklet'; return { yaw: -9, pitch: 3, roll: -2 }; }
export function add(a: Pose, b: Pose): Pose { 'worklet'; return { yaw: a.yaw + b.yaw, pitch: a.pitch + b.pitch, roll: a.roll + b.roll }; }
export function sub(a: Pose, b: Pose): Pose { 'worklet'; return { yaw: a.yaw - b.yaw, pitch: a.pitch - b.pitch, roll: a.roll - b.roll }; }
export function mul(a: Pose, b: number): Pose { 'worklet'; return { yaw: a.yaw * b, pitch: a.pitch * b, roll: a.roll * b }; }
function bezier(t: number, a: number, b: number) { 'worklet'; const s = 1 - t; return 3 * s * s * t * a + 3 * s * t * t * b + t * t * t; }
function bezierRate(t: number, a: number, b: number) { 'worklet'; return 3 * (1 - t) ** 2 * a + 6 * (1 - t) * t * (b - a) + 3 * t * t * (1 - b); }
export function curve(x: number, x1: number, y1: number, x2: number, y2: number): [number, number] {
  'worklet';
  if (x <= 0) return [0, y1 / x1];
  if (x >= 1) return [1, (1 - y2) / (1 - x2)];
  let lo = 0, hi = 1;
  for (let i = 0; i < 26; i++) { const m = (lo + hi) / 2; if (bezier(m, x1, x2) < x) lo = m; else hi = m; }
  const t = (lo + hi) / 2;
  return [bezier(t, y1, y2), bezierRate(t, y1, y2) / bezierRate(t, x1, x2)];
}
export function ease(x: number) { 'worklet'; return curve(x, 0.2, 0.8, 0.2, 1)[0]; }
export function io(x: number) { 'worklet'; return curve(x, 0.65, 0, 0.35, 1)[0]; }
export function rotation(pose: Pose) {
  'worklet';
  const y = pose.yaw * Math.PI / 180, p = pose.pitch * Math.PI / 180, r = pose.roll * Math.PI / 180;
  const cy = Math.cos(y), sy = Math.sin(y), cp = Math.cos(p), sp = Math.sin(p), cr = Math.cos(r), sr = Math.sin(r);
  return [cr * cy - sr * sp * sy, -sr * cp, cr * sy + sr * sp * cy,
    sr * cy + cr * sp * sy, cr * cp, sr * sy - cr * sp * cy, -cp * sy, sp, cp * cy];
}
export function bow(point: Point) { 'worklet'; return 3.5 * (1 - (2 * point.x / 333) ** 2) * (1 - (2 * point.y / 211) ** 2); }
export function project(local: Point, pose: Pose, depth: number | null = null): Point {
  'worklet';
  const m = rotation(pose), z = depth ?? bow(local);
  const x = m[0] * local.x + m[1] * local.y + m[2] * z;
  const y = m[3] * local.x + m[4] * local.y + m[5] * z;
  const worldZ = m[6] * local.x + m[7] * local.y + m[8] * z, scale = 800 / (800 - worldZ);
  return { x: 196.5 + x * scale, y: 366.5 + y * scale };
}
export function corners(pose: Pose) {
  'worklet';
  return [project({ x: -166.5, y: -105.5 }, pose, 0), project({ x: 166.5, y: -105.5 }, pose, 0),
    project({ x: 166.5, y: 105.5 }, pose, 0), project({ x: -166.5, y: 105.5 }, pose, 0)];
}
export function valid(pose: Pose) {
  'worklet';
  const points = corners(pose);
  for (const p of points) if (!Number.isFinite(p.x) || !Number.isFinite(p.y) || p.x < 21.5 || p.x > 371.5 || p.y < 242.5 || p.y > 507.5) return false;
  return true;
}
export function constrain(desired: Pose): Pose {
  'worklet';
  if (valid(desired)) return desired;
  let low = 0, high = 1;
  const rest = restPose(), delta = sub(desired, rest);
  for (let i = 0; i < 12; i++) { const q = (low + high) / 2; if (valid(add(rest, mul(delta, q)))) low = q; else high = q; }
  return add(rest, mul(delta, low));
}
export function actualVelocity(desired: Pose, velocity: Pose): Pose {
  'worklet';
  if (valid(desired)) return velocity;
  const h = 0.0001;
  return mul(sub(constrain(add(desired, mul(velocity, h))), constrain(sub(desired, mul(velocity, h)))), 1 / (2 * h));
}
export function inverseHit(screen: Point, pose: Pose): Point | null {
  'worklet';
  const m = rotation(pose), px = (screen.x - 196.5) / 800, py = (screen.y - 366.5) / 800;
  const c = [m[6] * 800, m[7] * 800, m[8] * 800];
  const d = [m[0] * px + m[3] * py - m[6], m[1] * px + m[4] * py - m[7], m[2] * px + m[5] * py - m[8]];
  if (Math.abs(d[2]) <= 0.0001) return null;
  const plane = -c[2] / d[2]; let t = plane;
  for (let i = 0; i < 6; i++) {
    const x = c[0] + t * d[0], y = c[1] + t * d[1], z = c[2] + t * d[2];
    const a = 1 - (2 * x / 333) ** 2, b = 1 - (2 * y / 211) ** 2, surface = 3.5 * a * b;
    const bx = -28 * x / (333 * 333) * b, by = -28 * y / (211 * 211) * a, derivative = d[2] - bx * d[0] - by * d[1];
    if (Math.abs(derivative) <= 0.0001) return null;
    t = Math.min(plane + 8, Math.max(plane - 8, t - (z - surface) / derivative));
  }
  const point = { x: c[0] + t * d[0], y: c[1] + t * d[1] }, qx = Math.abs(point.x) - 160.5, qy = Math.abs(point.y) - 99.5;
  const distance = Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - 6;
  return t > 0 && distance <= 0 ? point : null;
}
export function ridgeWeight(index: number, value: Frame) {
  'worklet';
  if (value.explicitViewB !== null) return clamp(value.explicitViewB);
  const u = (index + 0.5) * 1.45 - 166.5;
  return smooth((value.pose.yaw + 0.035 * u + 0.8 * Math.sin(2 * Math.PI * u / 333) + 0.7) / 1.4);
}
export function meanViewB(value: Frame) {
  'worklet';
  if (value.explicitViewB !== null) return clamp(value.explicitViewB);
  let sum = 0; for (let i = 0; i < 230; i++) sum += ridgeWeight(i, value);
  return sum / 230;
}
export function zeroSupport(): Support { 'worklet'; return { q0: 0, dq0: 0, k: 0 }; }
function forced(s: Support, a: number): [number, number] {
  'worklet';
  const omega = 2 * Math.PI / 0.4, c = 0.78 * omega, beta = omega * Math.sqrt(1 - 0.78 * 0.78), w2 = omega * omega, A = 0.12;
  const b3 = 2 * s.k / (A * A * A), b2 = -3 * s.k / (A * A) - 6 * c * b3 / w2;
  const b1 = -(4 * c * b2 + 6 * b3) / w2, b0 = s.k - (2 * c * b1 + 2 * b2) / w2;
  const C = s.q0 - b0, D = (s.dq0 - b1 + c * C) / beta, e = Math.exp(-c * a), co = Math.cos(beta * a), si = Math.sin(beta * a);
  return [((b3 * a + b2) * a + b1) * a + b0 + e * (C * co + D * si),
    (3 * b3 * a + 2 * b2) * a + b1 + e * ((-c * C + beta * D) * co + (-c * D - beta * C) * si)];
}
export function supportSample(s: Support, age: number): SupportSample {
  'worklet';
  if (age >= 0.60) return { latent: 0, latentVelocity: 0, angle: 0, velocity: 0 };
  let q: number, v: number;
  if (age <= 0) { q = s.q0; v = s.dq0; }
  else if (age <= 0.12) [q, v] = forced(s, age);
  else {
    const start = forced(s, 0.12), a = age - 0.12, omega = 2 * Math.PI / 0.4, c = 0.78 * omega;
    const beta = omega * Math.sqrt(1 - 0.78 * 0.78), C = start[0], D = (start[1] + c * C) / beta, e = Math.exp(-c * a), co = Math.cos(beta * a), si = Math.sin(beta * a);
    q = e * (C * co + D * si); v = e * ((-c * C + beta * D) * co + (-c * D - beta * C) * si);
  }
  const u = clamp((age - 0.40) / 0.20), envelope = 1 - u * u * (3 - 2 * u), rate = age > 0.40 ? -6 * u * (1 - u) / 0.20 : 0;
  const latent = q * envelope, derivative = v * envelope + q * rate, t = Math.tanh(latent);
  return { latent, latentVelocity: derivative, angle: 2 * t, velocity: 2 * (1 - t * t) * derivative };
}
export function retarget(s: Support, velocity: number, elapsed: number): Support {
  'worklet'; const current = supportSample(s, elapsed);
  return { q0: current.latent, dq0: current.latentVelocity, k: clamp(velocity, -1200, 1200) / 600 };
}
export function frame(desired: Pose, velocity: Pose = { yaw: 0, pitch: 0, roll: 0 }, explicitViewB: number | null = null, collectionMix = 0): Frame {
  'worklet'; return { pose: constrain(desired), angularVelocity: actualVelocity(desired, velocity), explicitViewB, collectionMix };
}
export function rest(): Frame { 'worklet'; return { pose: restPose(), angularVelocity: zeroPose(), explicitViewB: null, collectionMix: 0 }; }
export function finiteSpring(start: number, velocity: number, target: number, time: number, response: number, damping: number, end: number, tailStart: number): [number, number] {
  'worklet';
  if (time <= 0) return [start, velocity]; if (time >= end) return [target, 0];
  const omega = 2 * Math.PI / response, c = damping * omega, beta = omega * Math.sqrt(1 - damping * damping), C = start - target, D = (velocity + c * C) / beta;
  const e = Math.exp(-c * time), co = Math.cos(beta * time), si = Math.sin(beta * time), raw = e * (C * co + D * si);
  const v = e * ((-c * C + beta * D) * co + (-c * D - beta * C) * si), u = clamp((time - tailStart) / (end - tailStart));
  const g = 1 - smooth(u), dg = -smoothRate(u) / (end - tailStart);
  return [target + raw * g, v * g + raw * dg];
}
function segment(t: number, a: number, b: number, start: Pose, end: Pose, anticipation = false): Frame {
  'worklet'; const q = anticipation ? curve((t - a) / (b - a), 0.2, 0.8, 0.2, 1) : curve((t - a) / (b - a), 0.65, 0, 0.35, 1);
  return frame(add(start, mul(sub(end, start), q[0])), mul(sub(end, start), q[1] / (b - a)));
}
export function authored(time: number): Frame {
  'worklet'; const t = Math.max(0, time) % LOOP;
  if (t <= 1.10 || t >= 7.26) return rest();
  const anticipated = { yaw: -10.5, pitch: 3, roll: -2 }, b = { yaw: 14, pitch: -4, roll: 2 }, a = { yaw: -14, pitch: 6, roll: -3 };
  if (t < 1.23) return segment(t, 1.10, 1.23, restPose(), anticipated, true);
  if (t < 2.80) return segment(t, 1.23, 2.80, anticipated, b);
  if (t < 3.38) return frame(b);
  if (t < 5.16) return segment(t, 3.38, 5.16, b, a);
  if (t < 5.70) return frame(a);
  if (t < 7.08) return segment(t, 5.70, 7.08, a, { yaw: -9.35, pitch: 3, roll: -2 });
  const s = finiteSpring(-9.35, 0, -9, t - 7.08, 0.22, 0.86, 0.18, 0);
  return frame({ yaw: s[0], pitch: 3, roll: -2 }, { yaw: s[1], pitch: 0, roll: 0 });
}
export function rubber(x: number, limit: number) { 'worklet'; if (Math.abs(x) <= limit) return x; const over = Math.abs(x) - limit; return (x < 0 ? -1 : 1) * (limit + over * 0.35 / (1 + over / 8)); }
export function rubberRate(x: number, limit: number) { 'worklet'; return Math.abs(x) <= limit ? 1 : 0.35 / (1 + (Math.abs(x) - limit) / 8) ** 2; }
function dragAxis(value: number, delta: number, limit: number) { 'worklet'; return rubber(value + delta, limit) + (value - rubber(value, limit)) * (1 - smooth(Math.abs(delta) / 8)); }
function dragAxisRate(value: number, delta: number, limit: number) { 'worklet'; const direction = delta === 0 ? 0 : (delta < 0 ? -1 : 1); return rubberRate(value + delta, limit) - (value - rubber(value, limit)) * smoothRate(Math.abs(delta) / 8) * direction / 8; }
function dragDesired(base: Frame, translation: Point, yawExtra: number, pitchExtra: number): Pose {
  'worklet'; return { yaw: dragAxis(base.pose.yaw, 0.13 * translation.x, 18) + yawExtra,
    pitch: dragAxis(base.pose.pitch, -0.08 * translation.y, 10) + pitchExtra, roll: dragAxis(base.pose.roll, 0.02 * translation.x, 4) };
}
export function dragged(base: Frame, translation: Point, yawSupport: Support, pitchSupport: Support, supportAge: number): Frame {
  'worklet'; const sy = supportSample(yawSupport, supportAge), sp = supportSample(pitchSupport, supportAge);
  return frame(dragDesired(base, translation, sy.angle, sp.angle), { yaw: sy.velocity, pitch: sp.velocity, roll: 0 }, base.explicitViewB, base.collectionMix);
}
export function releaseVelocity(base: Frame, translation: Point, pointer: Point, candidateAge: number, inherited: Pose | null, yawSupport: Support, pitchSupport: Support, supportAge: number): Pose {
  'worklet'; const age = 1 - smooth(candidateAge / 0.12), sy = supportSample(yawSupport, supportAge), sp = supportSample(pitchSupport, supportAge);
  const user = mul(inherited ?? { yaw: 0.13 * clamp(pointer.x, -1200, 1200) * dragAxisRate(base.pose.yaw, 0.13 * translation.x, 18),
    pitch: -0.08 * clamp(pointer.y, -1200, 1200) * dragAxisRate(base.pose.pitch, -0.08 * translation.y, 10),
    roll: 0.02 * clamp(pointer.x, -1200, 1200) * dragAxisRate(base.pose.roll, 0.02 * translation.x, 4) }, age);
  return actualVelocity(dragDesired(base, translation, sy.angle, sp.angle), add(user, { yaw: sy.velocity, pitch: sp.velocity, roll: 0 }));
}
export function targetView(value: Frame, pointer: Point) {
  'worklet'; const requested = add(value.pose, { yaw: 0.13 * clamp(pointer.x, -1200, 1200) * 0.08,
    pitch: -0.08 * clamp(pointer.y, -1200, 1200) * 0.08, roll: 0.02 * clamp(pointer.x, -1200, 1200) * 0.08 });
  const p = constrain(requested); return p.yaw > 0 ? 1 : p.yaw < 0 ? 0 : meanViewB(value) > 0.5 ? 1 : 0;
}
export function released(from: Frame, view: number, elapsed: number): Frame {
  'worklet'; if (elapsed <= 0) return from;
  const target = { yaw: view === 0 ? -12 : 12, pitch: 3, roll: -2 };
  if (elapsed >= 0.75) return frame(target, zeroPose(), from.explicitViewB, from.collectionMix);
  const y = finiteSpring(from.pose.yaw, from.angularVelocity.yaw, target.yaw, elapsed, 0.45, 0.82, 0.75, 0.55);
  const p = finiteSpring(from.pose.pitch, from.angularVelocity.pitch, target.pitch, elapsed, 0.45, 0.82, 0.75, 0.55);
  const r = finiteSpring(from.pose.roll, from.angularVelocity.roll, target.roll, elapsed, 0.45, 0.82, 0.75, 0.55);
  return frame({ yaw: y[0], pitch: p[0], roll: r[0] }, { yaw: y[1], pitch: p[1], roll: r[1] }, from.explicitViewB, from.collectionMix);
}
export function cancelled(from: Frame, base: Frame, elapsed: number): Frame {
  'worklet'; if (elapsed <= 0) return from; if (elapsed >= 0.24) return { ...base, angularVelocity: zeroPose() };
  const u = elapsed / 0.24, q = smooth(u), rate = smoothRate(u) / 0.24, delta = sub(base.pose, from.pose);
  return frame(add(from.pose, mul(delta, q)), mul(delta, rate), from.explicitViewB, from.collectionMix);
}
export function recovering(from: Frame, elapsed: number): Frame {
  'worklet'; if (elapsed <= 0) return from; if (elapsed >= 0.65) return rest();
  const q = curve(elapsed / 0.65, 0.65, 0, 0.35, 1), delta = sub(restPose(), from.pose);
  return frame(add(from.pose, mul(delta, q[0])), mul(delta, q[1] / 0.65), from.explicitViewB === null ? null : from.explicitViewB * (1 - q[0]), from.collectionMix * (1 - q[0]));
}
export function collecting(from: Frame, elapsed: number): Frame {
  'worklet'; if (elapsed <= 0) return from; return { ...from, angularVelocity: zeroPose(), collectionMix: from.collectionMix + (1 - from.collectionMix) * ease(elapsed / 0.18) };
}
export function reduced(from: Frame, view: number, elapsed: number): Frame {
  'worklet'; const q = ease(elapsed / 0.18);
  return { pose: zeroPose(), angularVelocity: zeroPose(), explicitViewB: (from.explicitViewB ?? 0) + (view - (from.explicitViewB ?? 0)) * q, collectionMix: from.collectionMix };
}
