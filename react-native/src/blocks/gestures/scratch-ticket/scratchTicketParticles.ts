import { Skia, type SkPath } from '@shopify/react-native-skia';

/**
 * Foil flakes and confetti, simulated on the UI thread. A system is a flat array of numbers, one
 * record of STRIDE numbers per piece, so a frame copies plain numbers and never allocates objects:
 *   x, y, vx, vy (pt, pt/s), angle, spin (rad, rad/s), size (pt), age, life (s), colour, flip seed.
 */
export const STRIDE = 11;

/** Moves every piece one step: drag, gravity, spin; drops the ones that are spent or off screen. */
export function stepPieces(list: number[], dt: number, gravity: number, drag: number, floor: number): number[] {
  'worklet';
  if (list.length === 0) return list;
  const next: number[] = [];
  const keep = Math.exp(-drag * dt);
  for (let i = 0; i < list.length; i += STRIDE) {
    const age = list[i + 7] + dt;
    if (age >= list[i + 8]) continue;
    const vx = list[i + 2] * keep;
    const vy = list[i + 3] * keep + gravity * dt;
    const x = list[i] + vx * dt;
    const y = list[i + 1] + vy * dt;
    if (y > floor) continue;
    next.push(x, y, vx, vy, list[i + 4] + list[i + 5] * dt, list[i + 5], list[i + 6], age, list[i + 8], list[i + 9], list[i + 10]);
  }
  return next;
}

/** Adds `count` foil flakes torn off around (x, y), thrown a little along the finger's motion. */
export function addFlakes(list: number[], count: number, x: number, y: number, vx: number, vy: number, spread: number): number[] {
  'worklet';
  const next = list.slice();
  for (let k = 0; k < count; k += 1) {
    const a = Math.random() * Math.PI * 2;
    const d = Math.sqrt(Math.random()) * spread;
    next.push(
      x + Math.cos(a) * d,
      y + Math.sin(a) * d,
      vx * 0.12 + (Math.random() - 0.5) * 140,
      vy * 0.12 - 40 - Math.random() * 160,
      Math.random() * Math.PI * 2,
      (Math.random() - 0.5) * 14,
      2 + Math.random() * 3.5,
      0,
      0.9 + Math.random() * 0.7,
      0,
      Math.random() * 6,
    );
  }
  return next;
}

/** A burst of confetti from a rectangle's centre, fanned upwards. */
export function addConfetti(list: number[], count: number, x: number, y: number, width: number, colours: number): number[] {
  'worklet';
  const next = list.slice();
  for (let k = 0; k < count; k += 1) {
    const a = -Math.PI / 2 + (Math.random() - 0.5) * 2.1;
    const speed = 420 + Math.random() * 720;
    next.push(
      x + (Math.random() - 0.5) * width * 0.6,
      y + (Math.random() - 0.5) * 30,
      Math.cos(a) * speed,
      Math.sin(a) * speed,
      Math.random() * Math.PI * 2,
      (Math.random() - 0.5) * 9,
      6 + Math.random() * 5,
      0,
      1.5 + Math.random() * 0.9,
      k % colours,
      Math.random() * 6,
    );
  }
  return next;
}

/**
 * One path holding every piece of a colour (−1: all of them) as a small turning quad. A piece
 * narrows as it flips over (cos of its flip phase) and shrinks away over its last quarter.
 */
export function piecesPath(list: number[], colour: number, aspect: number): SkPath {
  'worklet';
  const path = Skia.PathBuilder.Make();
  for (let i = 0; i < list.length; i += STRIDE) {
    if (colour >= 0 && list[i + 9] !== colour) continue;
    const t = list[i + 7] / list[i + 8];
    const fade = t > 0.75 ? (1 - t) / 0.25 : 1;
    const size = list[i + 6] * fade;
    const angle = list[i + 4];
    const w = size * (0.2 + 0.8 * Math.abs(Math.cos(list[i + 10] + list[i + 7] * 7)));
    const h = size * aspect;
    const c = Math.cos(angle);
    const s = Math.sin(angle);
    const x = list[i];
    const y = list[i + 1];
    const ax = (c * w) / 2;
    const ay = (s * w) / 2;
    const bx = (-s * h) / 2;
    const by = (c * h) / 2;
    path.moveTo(x - ax - bx, y - ay - by);
    path.lineTo(x + ax - bx, y + ay - by);
    path.lineTo(x + ax + bx, y + ay + by);
    path.lineTo(x - ax + bx, y - ay + by);
    path.close();
  }
  return path.build();
}
