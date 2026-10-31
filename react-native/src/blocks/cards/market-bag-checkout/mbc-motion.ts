// Market Bag Checkout: items, timings, the bag's physics and the autoplay script.
// The bag's stretch and bounce are closed-form sums over the moments items land in it, so the loop and
// the finger share the same physics and the loop repeats exactly.

export const LOOP = 10;

export const ITEMS = [
  { key: 'avocado', name: 'Hass avocado', meta: '1 pc · ripe today', price: 1.8, weight: 0.22, slot: [-0.3, 0.62], size: 66, rot: -0.3 },
  { key: 'milk', name: 'Whole milk', meta: '500 ml · glass bottle', price: 2.4, weight: 0.62, slot: [0.24, 0.5], size: 78, rot: 0.06 },
  { key: 'lemons', name: 'Lemons', meta: '2 pc · unwaxed', price: 1.1, weight: 0.24, slot: [-0.12, 0.74], size: 70, rot: 0.15 },
  { key: 'tomatoes', name: 'Vine tomatoes', meta: '3 pc · on the vine', price: 2.9, weight: 0.36, slot: [0.12, 0.3], size: 72, rot: -0.1 },
  { key: 'baguette', name: 'Country baguette', meta: '1 · baked at 6 am', price: 3.2, weight: 0.26, slot: [-0.18, 0.06], size: 112, rot: -0.55 },
] as const;

export const TOTAL = 11.4;

export const FLY = 0.55; // seconds from a row into the bag
export const HOLD = 1.25; // seconds of holding to pay
export const PAID = { cinch: [0, 0.35], hop: [0.3, 0.85] } as const;
export const BOUNCE = { duration: 0.42, ratio: 0.32 }; // the bag's bounce when something lands in it
export const STRETCH = 34; // pt the bag stretches down with all of it in

/** Autoplay events. */
export const S = {
  pack0: 1.0, // the first item leaves its row
  packGap: 0.32,
  hold: [3.0, 4.25], // the thumb on Hold to pay
  unpack: 8.55, // everything back to the list for the next loop
  unpackGap: 0.12,
  rest: 9.5,
} as const;

export function seg(t: number, a: number, b: number) {
  'worklet';
  return Math.min(Math.max((t - a) / (b - a), 0), 1);
}

export function smooth(a: number, b: number, x: number) {
  'worklet';
  const t = Math.min(Math.max((x - a) / (b - a), 0), 1);
  return t * t * (3 - 2 * t);
}

export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

export function springStep(t: number, duration: number, ratio: number) {
  'worklet';
  if (t <= 0) return 0;
  const w = (2 * Math.PI) / duration;
  const wd = w * Math.sqrt(1 - ratio * ratio);
  return 1 - Math.exp(-ratio * w * t) * (Math.cos(wd * t) + ((ratio * w) / wd) * Math.sin(wd * t));
}

/** The bag's stretch (0..1 of STRETCH) given when each item landed (or -1), at time now. */
export function bagStretch(land: number[], leave: number[], now: number) {
  'worklet';
  let s = 0;
  let total = 0;
  for (let i = 0; i < ITEMS.length; i++) total += ITEMS[i].weight;
  for (let i = 0; i < land.length; i++) {
    if (land[i] < 0 || now < land[i]) continue;
    const out = leave[i] >= 0 && now > leave[i] ? springStep(now - leave[i], BOUNCE.duration * 1.3, 0.55) : 0;
    s += (ITEMS[i].weight / total) * (springStep(now - land[i], BOUNCE.duration, BOUNCE.ratio) - out);
  }
  return s;
}

export type Pose = {
  fly: number[]; // per item: 0 in its row, 1 in the bag (flight in between)
  dir: number[]; // per item: 1 packing, -1 unpacking
  land: number[]; // per item: when it landed (bag time), or -1
  leave: number[]; // per item: when it was taken back out, or -1
  hold: number; // 0..1 how far the hold has gone
  paid: number; // time since paid, or -1
  reset: number; // 0..1 the paid state fading back to the hold button
};

/** The whole autoplay pose at loop time t. */
export function script(t: number): Pose {
  'worklet';
  const fly: number[] = [];
  const dir: number[] = [];
  const land: number[] = [];
  const leave: number[] = [];
  const rest = t >= S.rest;
  for (let i = 0; i < ITEMS.length; i++) {
    const t0 = S.pack0 + i * S.packGap;
    const t1 = S.unpack + (ITEMS.length - 1 - i) * S.unpackGap;
    if (rest) {
      fly.push(0);
      dir.push(1);
      land.push(-1);
      leave.push(-1);
    } else if (t < t1) {
      fly.push(easeInOut(seg(t, t0, t0 + FLY)));
      dir.push(1);
      land.push(t >= t0 + FLY ? t0 + FLY : -1);
      leave.push(-1);
    } else {
      fly.push(1 - easeInOut(seg(t, t1, t1 + FLY * 0.8)));
      dir.push(-1);
      land.push(t0 + FLY);
      leave.push(t1);
    }
  }
  const reset = seg(t, S.unpack, S.unpack + 0.35);
  const hold = rest ? 0 : t < S.unpack ? smooth(S.hold[0], S.hold[1], t) : 1 - reset;
  const paid = !rest && t >= S.hold[1] ? t - S.hold[1] : -1;
  return { fly, dir, land, leave, hold, paid, reset: rest ? 0 : reset };
}
