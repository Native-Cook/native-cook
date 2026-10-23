export const TAU = Math.PI * 2;
export function clamp(n: number, lo = 0, hi = 1) { 'worklet'; return Math.max(lo, Math.min(hi, n)); }
export function lerp(a: number, b: number, t: number) { 'worklet'; return a + (b - a) * t; }

/** Solve the timing curve's x coordinate; a polynomial in t alone is not a CSS bezier. */
export function bezier(p: number, x1: number, y1: number, x2: number, y2: number) {
  'worklet';
  if (p <= 0) return 0;
  if (p >= 1) return 1;
  let lo = 0; let hi = 1; let t = p;
  for (let i = 0; i < 12; i++) {
    const q = 1 - t;
    const x = 3 * q * q * t * x1 + 3 * q * t * t * x2 + t * t * t;
    if (x < p) lo = t; else hi = t;
    t = (lo + hi) / 2;
  }
  return 3 * (1 - t) * (1 - t) * t * y1 + 3 * (1 - t) * t * t * y2 + t * t * t;
}
export function ease(p: number) { 'worklet'; return bezier(p, 0.23, 1, 0.32, 1); }
export type Spring = { x: number; v: number };

/** Exact damped-oscillator solution: the same motion at 60 Hz and 120 Hz. */
export function spring(s: Spring, target: number, dt: number, stiffness: number, damping: number): Spring {
  'worklet';
  const a = damping / 2;
  const w = Math.sqrt(Math.max(0.001, stiffness - a * a));
  const delta = s.x - target;
  const b = (s.v + a * delta) / w;
  const attenuation = Math.exp(-a * dt);
  const sn = Math.sin(w * dt); const cs = Math.cos(w * dt);
  const x = target + attenuation * (delta * cs + b * sn);
  const v = attenuation * ((b * w - a * delta) * cs - (delta * w + a * b) * sn);
  return Math.abs(x - target) < 0.00005 && Math.abs(v) < 0.0001 ? { x: target, v: 0 } : { x, v };
}
export function springAt(seconds: number, stiffness: number, damping: number, velocity = 0) {
  'worklet'; return spring({ x: 0, v: velocity }, 1, Math.max(0, seconds), stiffness, damping).x;
}
/** Remove the spring's last subpixel tail without a discontinuity at the handoff. */
export function settleTail(seconds: number, start: number, end: number) {
  'worklet';
  const p = clamp((seconds - start) / (end - start));
  return 1 - p * p * (3 - 2 * p);
}
export function flightSpring(seconds: number, stiffness: number, damping: number, start: number, end: number) {
  'worklet';
  return 1 + (springAt(seconds, stiffness, damping) - 1) * settleTail(seconds, start, end);
}
export function launchTurn(seconds: number) {
  'worklet';
  // Wind up the turn through the flight; preserve the spring's overshoot after arrival.
  const clock = seconds < 0.95 ? 0.95 * Math.pow(Math.max(0, seconds) / 0.95, 1.9) : 0.95 + (seconds - 0.95) * 1.9;
  return TAU * (1 + (springAt(clock, 17.76, 5.233) - 1) * settleTail(seconds, 1.35, 1.6));
}
export type Spin = { x: number; v: number; target: number; friction: number; detent: boolean; snapped: boolean };
export function flickSpin(x: number, velocity: number, front: boolean): Spin {
  'worklet';
  const interval = front ? TAU : Math.PI;
  const target = Math.round((x + velocity / 3.8) / interval) * interval;
  // Coast almost to the chosen face, leaving only a small magnetic pull at the end.
  const friction = velocity * (target - x) > 0 ? clamp(velocity / (target - x), 1.5, 6) : 3.8;
  return { x, v: velocity, target, friction, detent: false, snapped: false };
}
export function advanceSpin(s: Spin, dt: number): Spin {
  'worklet';
  let next = { ...s };
  // Fixed upper step bounds make the friction-to-detent boundary refresh-rate independent.
  const steps = Math.max(1, Math.ceil(dt * 240));
  const h = dt / steps;
  for (let i = 0; i < steps; i++) {
    if (!next.detent && Math.abs(next.v) > 1.8) {
      const decay = Math.exp(-next.friction * h);
      next.x += next.v * (1 - decay) / next.friction;
      next.v *= decay;
    } else {
      next.detent = true;
      const moved = spring(next, next.target, h, 85, 13);
      next.x = moved.x; next.v = moved.v;
      if (Math.abs(next.x - next.target) < 0.012 && Math.abs(next.v) < 0.07) {
        next.x = next.target; next.v = 0; next.snapped = true;
      }
    }
  }
  return next;
}
export function scrollCurve(p: number) { 'worklet'; return (1 - Math.exp(-5.2 * clamp(p))) / (1 - Math.exp(-5.2)); }
export function resistDrag(y: number) { 'worklet'; return y < 0 ? y * 0.12 : y <= 160 ? y : 160 + (y - 160) * 0.28; }
