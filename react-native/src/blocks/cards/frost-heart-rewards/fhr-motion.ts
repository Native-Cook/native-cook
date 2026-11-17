/** Atelier: every pose is sampled from seconds, so a pinned frame and a loop agree. */
export const LOOP = 10;
/** Only 25 and 50 are claimed by the demonstration; the other three remain touchable. */
export const CLAIMS = [1000, 1000, 1000, 0.6, 0.95] as const;
export const VALUES = [10, 15, 20, 25, 50] as const;
export const HEART = { x: 175, y: 278 } as const;
export function clamp(x: number, a = 0, b = 1) { 'worklet'; return Math.max(a, Math.min(b, x)); }
export function seg(t: number, a: number, b: number) { 'worklet'; return clamp((t - a) / (b - a)); }
/** Invert Bezier x with bounded bisection; custom easing is also deterministic in captures. */
function bezier(x: number, x1: number, y1: number, x2: number, y2: number) {
  'worklet';
  x = clamp(x);
  if (x === 0 || x === 1) return x;
  let lo = 0, hi = 1, u = x;
  for (let i = 0; i < 12; i++) {
    const v = 1 - u, bx = 3 * v * v * u * x1 + 3 * v * u * u * x2 + u * u * u;
    if (bx < x) lo = u; else hi = u;
    u = (lo + hi) * 0.5;
  }
  return 3 * (1 - u) * (1 - u) * u * y1 + 3 * (1 - u) * u * u * y2 + u * u * u;
}
export function ease(x: number) { 'worklet'; return bezier(x, 0.77, 0, 0.175, 1); }
export function out(x: number) { 'worklet'; return bezier(x, 0.23, 1, 0.32, 1); }
/** A bounded ±3° crown breath; continuous at both ends of the quiet hold. */
export function microTurn(t: number) {
  'worklet';
  const u = seg(t, 7.4, 8.9);
  return Math.PI / 60 * Math.sin(u * Math.PI * 2) * ease(seg(u, 0, 0.12)) * (1 - ease(seg(u, 0.88, 1)));
}
export function hash(i: number) { 'worklet'; const n = Math.sin(i * 127.1 + 311.7) * 43758.5453; return n - Math.floor(n); }
export function bump(age: number, speed = 18) { 'worklet'; return age < 0 ? 0 : Math.sin(age * speed) * Math.exp(-age * 10); }
/** 0.82 damping ratio: a small, single magnetic overshoot, followed by a clean lock. */
export function magnetic(age: number) {
  'worklet';
  if (age <= 0) return 1;
  return Math.exp(-11.5 * age) * (Math.cos(8 * age) + 11.5 / 8 * Math.sin(8 * age));
}
export type Scene = {
  t: number; charge: number; frost: number; crack: number; broken: number; gem: number;
  hue: number; flip: number; flash: number; pulse: number; squeeze: number; shake: number;
  rays: number; angle: number; reset: number; balance: number; balanceFrom: number;
  balanceTo: number; numberFade: number; remaining: number; level: number; wink: number;
  spotlight: number; push: number; cut: number; display: number; turn: number; polish: number; polishX: number; breath: number; breathX: number; breathY: number; core: number;
};
export function sceneAt(seconds: number): Scene {
  'worklet';
  const loop = ((seconds % LOOP) + LOOP) % LOOP;
  const t = Math.min(loop, 8.9);
  let balance = 1650;
  for (let i = 3; i < 5; i++) balance += VALUES[i] * out(seg(t - CLAIMS[i] - 0.65, 0, 0.1));
  const cut = ease(seg(t, 2.4, 3.6));
  const display = t < 5.4 ? out(seg(t, 3.9, 5.05)) : magnetic(t - 5.4);
  const hue = ease(seg(t, 6.4, 7.4));
  return {
    t, charge: clamp((balance - 1650) / 75), frost: 0, crack: 0,
    broken: t >= 3.9 && t < 6.4 ? Math.max(0, display) : 0,
    gem: cut, hue, flip: ease(seg(t, 6.65, 7.25)), flash: 0, pulse: 0,
    squeeze: 0, shake: 0, rays: 0, angle: 0,
    reset: ease(seg(loop, 8.9, 10)), balance, balanceFrom: balance, balanceTo: balance,
    numberFade: 1, remaining: Math.max(0, 65 - (balance - 1650)), level: t >= 6.95 ? 4 : 3,
    wink: 0, spotlight: ease(seg(t, 1.7, 2.4)), push: 0,
    cut, display: t >= 3.9 && t < 6.4 ? display : 0,
    turn: 0, polish: 0, polishX: 0,
    breath: ease(seg(t, 1.75, 1.98)) * (1 - ease(seg(t, 2.06, 2.3))), breathX: 133, breathY: 258,
    core: Array.from({ length: 12 }, (_, i) => out(seg(t, 2.48 + i * 0.105, 2.60 + i * 0.105))).reduce((a, b) => a + b, 0) / 12,
  };
}
