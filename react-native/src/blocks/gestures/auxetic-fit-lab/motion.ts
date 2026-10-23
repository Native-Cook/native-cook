export const LOOP = 8;
export const EDGE = 49.5;
const RAD = Math.PI / 180, OMEGA = 2 * Math.PI / .40, ALPHA = .8 * OMEGA, BETA = .6 * OMEGA;
export type Side = 'left' | 'right';
export type Detent = 4 | 23 | 42;
export type Housing = { offsetX: number; offsetVelocityX: number; scale: number; scaleVelocity: number };
export type Frame = { angle: number; angularVelocity: number; left: Housing; right: Housing; arrowGain: number; arrowGainVelocity: number };
type Rates = { leftOffset: number; rightOffset: number; leftScale: number; rightScale: number; arrows: number };
export type Input = { frame: Frame; requestedWidth: number; requestedWidthVelocity: number; actualAngularVelocity: number | null; rates: Rates };
export type ReleaseVelocity = { kind: 'inherited'; frame: Frame; heldElapsed: number } | { kind: 'user'; width: number; angular: number | null; sampleAge: number };
type Spring = { start: number; target: number; velocity: number; critical: boolean };
export type Release = { target: Detent | null; acceptedAngularVelocity: number; from: Frame; angle: Spring; leftOffset: Spring; rightOffset: Spring; leftScale: Spring; rightScale: Spring; arrows: Spring };
type Sample = { value: number; velocity: number };
export type Rect = { x: number; y: number; width: number; height: number };
export type Point = { x: number; y: number };

export function clamp(value: number, lower: number, upper: number) { 'worklet'; return Math.min(upper, Math.max(lower, value)); }
function sign(value: number) { 'worklet'; return value === 0 ? 0 : value > 0 ? 1 : -1; }
function smooth(value: number): Sample { 'worklet'; const u = clamp(value, 0, 1); return { value: u * u * (3 - 2 * u), velocity: value <= 0 || value >= 1 ? 0 : 6 * u * (1 - u) }; }
function coordinate(u: number, a: number, b: number) { 'worklet'; const v = 1 - u; return 3 * v * v * u * a + 3 * v * u * u * b + u * u * u; }
function derivative(u: number, a: number, b: number) { 'worklet'; return 3 * (1 - u) * (1 - u) * a + 6 * (1 - u) * u * (b - a) + 3 * u * u * (1 - b); }
function curve(value: number, x1: number, y1: number, x2: number, y2: number): Sample {
  'worklet'; const x = clamp(value, 0, 1); if (x === 0) return { value: 0, velocity: y1 / x1 }; if (x === 1) return { value: 1, velocity: (1 - y2) / (1 - x2) };
  let lo = 0, hi = 1; for (let i = 0; i < 30; i++) { const mid = (lo + hi) / 2; if (coordinate(mid, x1, x2) < x) lo = mid; else hi = mid; }
  const u = (lo + hi) / 2; return { value: coordinate(u, y1, y2), velocity: derivative(u, y1, y2) / derivative(u, x1, x2) };
}
export function width(angle: number) { 'worklet'; const a = angle * RAD; return 4 * EDGE * (Math.cos(a) + Math.sin(a)); }
export function jacobian(angle: number) { 'worklet'; const a = angle * RAD; return 4 * EDGE * (Math.cos(a) - Math.sin(a)) * RAD; }
export function angleForWidth(value: number) { 'worklet'; if (value <= width(4)) return 4; if (value >= width(44)) return 44; return (Math.PI / 4 - Math.acos(clamp(value / (4 * EDGE * Math.sqrt(2)), -1, 1))) / RAD; }
export function metrics(angle: number) { 'worklet'; const d = width(angle) / 4, d0 = width(4) / 4; return { extensionPercent: 100 * (d / d0 - 1), openAreaPercent: 100 * (1 - EDGE * EDGE / (d * d)) }; }
function restHousing(): Housing { 'worklet'; return { offsetX: 0, offsetVelocityX: 0, scale: 1, scaleVelocity: 0 }; }
export function staticFrame(angle: number): Frame { 'worklet'; return { angle: clamp(angle, 4, 42), angularVelocity: 0, left: restHousing(), right: restHousing(), arrowGain: 1, arrowGainVelocity: 0 }; }
export function rest() { 'worklet'; return staticFrame(4); }
export function frozen(frame: Frame): Frame { 'worklet'; return { ...frame, angularVelocity: 0, left: { ...frame.left, offsetVelocityX: 0, scaleVelocity: 0 }, right: { ...frame.right, offsetVelocityX: 0, scaleVelocity: 0 }, arrowGainVelocity: 0 }; }
export function authored(time: number): Frame {
  'worklet'; const t = Math.max(0, time) % LOOP, frame = rest();
  if (t >= 1.24 && t < 3.90) { const q = curve((t - 1.24) / 2.66, .65, 0, .35, 1); frame.angle = 4 + 38 * q.value; frame.angularVelocity = 38 * q.velocity / 2.66; }
  else if (t >= 3.90 && t < 4.25) {
    const u = (t - 3.90) / .35, k = 2 * Math.PI * .82, peak = Math.atan(1 / .82) / Math.PI, norm = Math.pow(Math.sin(Math.PI * peak), 2) * Math.exp(-k * peak), s = Math.sin(Math.PI * u), e = Math.exp(-k * u);
    frame.angle = 42 + s * s * e / norm; frame.angularVelocity = e * (Math.PI * Math.sin(2 * Math.PI * u) - k * s * s) / (norm * .35);
  } else if (t >= 4.25 && t < 5.15) frame.angle = 42;
  else if (t >= 5.15 && t < 7.10) { const q = curve((t - 5.15) / 1.95, .65, 0, .35, 1); frame.angle = 42 - 38 * q.value; frame.angularVelocity = -38 * q.velocity / 1.95; }
  let offset = 0, offsetVelocity = 0, scale = 1, scaleVelocity = 0;
  if (t >= 1.10 && t < 1.24) { const q = curve((t - 1.10) / .14, .2, .8, .2, 1); offset = 2 * q.value; offsetVelocity = 2 * q.velocity / .14; scale = 1 - .06 * q.value; scaleVelocity = -.06 * q.velocity / .14; }
  else if (t >= 1.24 && t < 1.46) { const q = smooth((t - 1.24) / .22); offset = 2 * (1 - q.value); offsetVelocity = -2 * q.velocity / .22; scale = .94 + .06 * q.value; scaleVelocity = .06 * q.velocity / .22; }
  else if (t >= 7 && t < 7.10) { const q = smooth((t - 7) / .10); offset = q.value; offsetVelocity = q.velocity / .10; scale = 1 - .02 * q.value; scaleVelocity = -.02 * q.velocity / .10; }
  else if (t >= 7.10 && t < 7.28) { const q = curve((t - 7.10) / .18, .2, .8, .2, 1); offset = 1 - q.value; offsetVelocity = -q.velocity / .18; scale = .98 + .02 * q.value; scaleVelocity = .02 * q.velocity / .18; }
  frame.left = { offsetX: offset, offsetVelocityX: offsetVelocity, scale, scaleVelocity }; frame.right = { offsetX: -offset, offsetVelocityX: -offsetVelocity, scale, scaleVelocity };
  const a = smooth((t - 1.24) / .22), b = smooth((t - 3.90) / .35); frame.arrowGain = 1 + .12 * a.value * (1 - b.value); frame.arrowGainVelocity = .12 * (a.velocity / .22 * (1 - b.value) - a.value * b.velocity / .35); return frame;
}
export function handles(frame: Frame) {
  'worklet'; const d = width(frame.angle) / 4, leftCenter = { x: 196.5 - (2 * d + 20) + frame.left.offsetX, y: 399 }, rightCenter = { x: 196.5 + (2 * d + 20) + frame.right.offsetX, y: 399 };
  return { leftCenter, rightCenter, leftHit: { x: clamp(leftCenter.x - 22, 14, 335), y: 369, width: 44, height: 60 }, rightHit: { x: clamp(rightCenter.x - 22, 14, 335), y: 369, width: 44, height: 60 }, pinchHit: { x: 196.5 - 2 * d - 12, y: 399 - 1.5 * d - 12, width: 4 * d + 24, height: 3 * d + 24 } };
}
export function begin(frame: Frame): Input { 'worklet'; return { frame: frozen(frame), requestedWidth: width(frame.angle), requestedWidthVelocity: 0, actualAngularVelocity: null, rates: { leftOffset: 0, rightOffset: 0, leftScale: 0, rightScale: 0, arrows: 0 } }; }
function inputHousing(from: Housing, direction: number, active: boolean, q: Sample, qWidth: number, excess: number, excessWidth: number, velocity: number) {
  'worklet'; const residual = from.offsetX * (1 - q.value), residualWidth = -from.offsetX * qWidth, over = active ? direction * excess : 0, overWidth = active ? direction * excessWidth : 0, rubber = .45 * over / (1 + .45 * Math.abs(over) / 16), rubberWidth = .45 * overWidth / Math.pow(1 + .45 * Math.abs(over) / 16, 2), room = 1 - Math.abs(residual) / 16, roomWidth = -sign(residual) * residualWidth / 16, slope = residualWidth + rubberWidth * room + rubber * roomWidth, scaleSlope = -(from.scale - 1) * qWidth;
  return { housing: { offsetX: residual + rubber * room, offsetVelocityX: slope * velocity, scale: 1 + (from.scale - 1) * (1 - q.value), scaleVelocity: scaleSlope * velocity }, offsetRate: slope, scaleRate: scaleSlope };
}
function input(capture: Frame, requested: number, velocity: number, leftRubber: boolean, rightRubber: boolean): Input {
  'worklet'; const lower = width(4), upper = width(44), physical = clamp(requested, lower, upper), angle = requested === width(capture.angle) ? capture.angle : angleForWidth(physical), moving = requested >= lower && requested <= upper && !(requested === lower && velocity < 0) && !(requested === upper && velocity > 0), physicalVelocity = moving ? velocity : 0, delta = requested - width(capture.angle), q = smooth(Math.abs(delta) / 24), qWidth = q.velocity * sign(delta) / 24, excess = (requested - physical) / 2, excessWidth = (1 - (moving ? 1 : 0)) / 2;
  const left = inputHousing(capture.left, -1, leftRubber, q, qWidth, excess, excessWidth, velocity), right = inputHousing(capture.right, 1, rightRubber, q, qWidth, excess, excessWidth, velocity), arrows = (1.12 - capture.arrowGain) * qWidth, angular = physicalVelocity / jacobian(angle);
  return { frame: { angle, angularVelocity: angular, left: left.housing, right: right.housing, arrowGain: capture.arrowGain + (1.12 - capture.arrowGain) * q.value, arrowGainVelocity: arrows * velocity }, requestedWidth: requested, requestedWidthVelocity: velocity, actualAngularVelocity: angular, rates: { leftOffset: left.offsetRate, rightOffset: right.offsetRate, leftScale: left.scaleRate, rightScale: right.scaleRate, arrows } };
}
export function drag(capture: Frame, side: Side, translationX: number, velocityX: number) { 'worklet'; const direction = side === 'right' ? 1 : -1; return input(capture, width(capture.angle) + direction * 2 * translationX, direction * 2 * velocityX, side === 'left', side === 'right'); }
export function pinch(capture: Frame, scale: number, scaleVelocity: number) { 'worklet'; return input(capture, width(capture.angle) * scale, width(capture.angle) * scaleVelocity, true, true); }
export function nearestDetent(angle: number, velocity: number, previous: Detent): Detent {
  'worklet'; const detents: Detent[] = [4, 23, 42]; let distance = Infinity; for (const d of detents) distance = Math.min(distance, Math.abs(d - angle)); const ties: Detent[] = []; for (const d of detents) if (Math.abs(Math.abs(d - angle) - distance) <= 1e-10) ties.push(d); if (velocity > 0) return ties[ties.length - 1] ?? 4; if (velocity < 0) return ties[0] ?? 4; return ties.includes(previous) ? previous : ties[0] ?? 4;
}
function rawSpring(spring: Spring, time: number): Sample {
  'worklet'; const t = Math.max(0, time), d = spring.start - spring.target;
  if (spring.critical) { const c = spring.velocity + OMEGA * d, e = Math.exp(-OMEGA * t); return { value: spring.target + e * (d + c * t), velocity: e * (spring.velocity - OMEGA * c * t) }; }
  const b = (spring.velocity + ALPHA * d) / BETA, e = Math.exp(-ALPHA * t), c = Math.cos(BETA * t), s = Math.sin(BETA * t); return { value: spring.target + e * (d * c + b * s), velocity: e * (spring.velocity * c + (-ALPHA * b - BETA * d) * s) };
}
function sampleSpring(spring: Spring, time: number): Sample {
  'worklet'; if (time <= 0) return { value: spring.start, velocity: spring.velocity }; if (time >= 1.20) return { value: spring.target, velocity: 0 }; const s = rawSpring(spring, time); if (time <= .80) return s; const u = (time - .80) / .40, envelope = 1 - u * u * (3 - 2 * u), derivative = -6 * u * (1 - u) / .40; return { value: spring.target + envelope * (s.value - spring.target), velocity: envelope * s.velocity + derivative * (s.value - spring.target) };
}
function isFeasible(spring: Spring, lower: number, upper: number) {
  'worklet'; const d = spring.start - spring.target, b = (spring.velocity + ALPHA * d) / BETA, c = -ALPHA * b - BETA * d, phase = Math.atan2(-spring.velocity, c), times = [0, 1.20]; for (let k = -2; k <= 8; k++) { const t = (phase + k * Math.PI) / BETA; if (t > 0 && t < 1.20) times.push(t); } for (const t of times) { const value = rawSpring(spring, t).value; if (value < lower - 1e-12 || value > upper + 1e-12) return false; } return true;
}
function bounded(start: number, target: number, velocity: number, lower: number, upper: number): Spring {
  'worklet'; const candidate = { start, target, velocity, critical: false }; if (isFeasible(candidate, lower, upper)) return candidate; let lo = 0, hi = 1; for (let i = 0; i < 40; i++) { const mid = (lo + hi) / 2, spring = { start, target, velocity: velocity * mid, critical: false }; if (isFeasible(spring, lower, upper)) lo = mid; else hi = mid; } return { start, target, velocity: velocity * lo, critical: false };
}
function releasePlan(from: Frame, target: Detent | null, angle: Spring): Release {
  'worklet'; const leftOffset = bounded(from.left.offsetX, 0, from.left.offsetVelocityX, -16, 16), rightOffset = bounded(from.right.offsetX, 0, from.right.offsetVelocityX, -16, 16), leftScale = { start: from.left.scale, target: 1, velocity: from.left.scaleVelocity, critical: false }, rightScale = { start: from.right.scale, target: 1, velocity: from.right.scaleVelocity, critical: false }, gainVelocity = clamp(from.arrowGainVelocity, -OMEGA * (from.arrowGain - 1), OMEGA * (1.12 - from.arrowGain)); return { target, acceptedAngularVelocity: angle.velocity, from, angle, leftOffset, rightOffset, leftScale, rightScale, arrows: { start: from.arrowGain, target: 1, velocity: gainVelocity, critical: true } };
}
export function makeRelease(input: Input, velocity: ReleaseVelocity, previous: Detent, cancelled: boolean): Release {
  'worklet'; const from: Frame = { ...input.frame, left: { ...input.frame.left }, right: { ...input.frame.right } }; let widthVelocity = 0, angularVelocity = 0;
  if (velocity.kind === 'inherited') {
    const a = 1 - smooth(velocity.heldElapsed / .12).value, capture = velocity.frame; angularVelocity = capture.angularVelocity * a; widthVelocity = jacobian(from.angle) * angularVelocity; from.left.offsetVelocityX = capture.left.offsetVelocityX * a; from.right.offsetVelocityX = capture.right.offsetVelocityX * a; from.left.scaleVelocity = capture.left.scaleVelocity * a; from.right.scaleVelocity = capture.right.scaleVelocity * a; from.arrowGainVelocity = capture.arrowGainVelocity * a;
  } else {
    const a = 1 - smooth(velocity.sampleAge / .12).value; widthVelocity = clamp(velocity.width * a, -600, 600); angularVelocity = (velocity.angular ?? velocity.width / jacobian(from.angle)) * a; from.left.offsetVelocityX = input.rates.leftOffset * widthVelocity; from.right.offsetVelocityX = input.rates.rightOffset * widthVelocity; from.left.scaleVelocity = input.rates.leftScale * widthVelocity; from.right.scaleVelocity = input.rates.rightScale * widthVelocity; from.arrowGainVelocity = input.rates.arrows * widthVelocity;
  }
  widthVelocity = clamp(widthVelocity, -600, 600); angularVelocity = clamp(angularVelocity, -50, 50); if ((from.angle <= 4 && angularVelocity < 0) || (from.angle >= 44 && angularVelocity > 0)) angularVelocity = 0;
  if (cancelled) { from.angularVelocity = 0; return releasePlan(from, null, { start: from.angle, target: from.angle, velocity: 0, critical: true }); }
  const predicted = angleForWidth(width(from.angle) + .12 * widthVelocity), target = nearestDetent(predicted, widthVelocity, previous); let spring: Spring;
  if (target === 4) spring = { start: from.angle, target: 4, velocity: clamp(angularVelocity, Math.max(-50, -OMEGA * (from.angle - 4)), Math.min(50, OMEGA * (44 - from.angle))), critical: true };
  else spring = bounded(from.angle, target, angularVelocity, 4, 44);
  from.angularVelocity = spring.velocity; return releasePlan(from, target, spring);
}
function relaxed(from: Housing, q: number, rate: number): Housing { 'worklet'; return { offsetX: from.offsetX * (1 - q), offsetVelocityX: -from.offsetX * rate, scale: from.scale + (1 - from.scale) * q, scaleVelocity: (1 - from.scale) * rate }; }
export function released(release: Release, elapsed: number): Frame {
  'worklet'; if (release.target === null) { const q = smooth(elapsed / .22), f = release.from; return { ...f, angularVelocity: 0, left: relaxed(f.left, q.value, q.velocity / .22), right: relaxed(f.right, q.value, q.velocity / .22), arrowGain: f.arrowGain + (1 - f.arrowGain) * q.value, arrowGainVelocity: (1 - f.arrowGain) * q.velocity / .22 }; }
  const a = sampleSpring(release.angle, elapsed), lo = sampleSpring(release.leftOffset, elapsed), ro = sampleSpring(release.rightOffset, elapsed), ls = sampleSpring(release.leftScale, elapsed), rs = sampleSpring(release.rightScale, elapsed), gain = sampleSpring(release.arrows, elapsed); return { angle: a.value, angularVelocity: a.velocity, left: { offsetX: lo.value, offsetVelocityX: lo.velocity, scale: ls.value, scaleVelocity: ls.velocity }, right: { offsetX: ro.value, offsetVelocityX: ro.velocity, scale: rs.value, scaleVelocity: rs.velocity }, arrowGain: gain.value, arrowGainVelocity: gain.velocity };
}
export function recovering(from: Frame, elapsed: number): Frame {
  'worklet'; if (elapsed <= 0) return from; if (elapsed >= .60) return rest(); const q = curve(elapsed / .60, .65, 0, .35, 1), rate = q.velocity / .60; return { angle: from.angle + (4 - from.angle) * q.value, angularVelocity: (4 - from.angle) * rate, left: relaxed(from.left, q.value, rate), right: relaxed(from.right, q.value, rate), arrowGain: from.arrowGain + (1 - from.arrowGain) * q.value, arrowGainVelocity: (1 - from.arrowGain) * rate };
}

export type Owner = 'left' | 'right' | 'pinch';
export type Session = { owner: Owner; generation: number; stamp: number; capture: Frame; anchor: number; coordinate: number; input: Input; lastMovement: number | null; movementInput: Input | null };
export type Event = { stamp: number; held: Frame; release: Release | null };
export type Motion = { now: number; epoch: number; visible: boolean; generation: number; session: Session | null; blocked: number; tokens: Record<Owner, number | null>; event: Event | null; saved: boolean; reducedAngle: number; quietSave: number | null; previousDetent: Detent; hapticZone: Detent; lastTick: number };
export function drive(m: Motion, autoPlay: boolean, reduced: boolean, previewTime?: number): Frame {
  'worklet'; if (reduced) return staticFrame(m.reducedAngle); if (previewTime !== undefined) return authored(Math.max(0, previewTime)); if (m.session) return frozen(m.session.input.frame); if (m.event) { const age = Math.max(0, m.now - m.event.stamp), held = m.event.release ? released(m.event.release, Math.min(age, 2)) : m.event.held; return age < 2 ? held : recovering(held, age - 2); } return autoPlay ? authored(Math.max(0, m.now - m.epoch)) : rest();
}
