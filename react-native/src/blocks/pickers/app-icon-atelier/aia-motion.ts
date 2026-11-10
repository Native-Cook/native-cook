// App Icon Atelier — the loop. Helpers first: a worklet captures only what is declared above it.

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}
export function easeOut(x: number) {
  'worklet';
  const u = 1 - x;
  return 1 - u * u * u;
}
/** ease-in that leaves at speed 1 (x²(2 − x)): a drag that is still moving when the finger lifts */
export function easeInFling(x: number) {
  'worklet';
  return x * x * (2 - x);
}
/** a spring-like arrival: overshoots ~4 % and settles, f(0) = 0, f(1) = 1 */
export function arrive(x: number) {
  'worklet';
  if (x <= 0) return 0;
  if (x >= 1) return 1;
  return 1 - Math.exp(-5.2 * x) * Math.cos(6.4 * x) * (1 - x);
}

export const LOOP = 10;
/** the front sits just outside the pebble on either side when nothing is changing */
export const FRONT_END = 1.45;
/** the pebble at rest: turned to show its right side and its bottom, tipped clockwise like the pin */
export const REST = { yaw: -0.42, pitch: -0.26, roll: -0.12 } as const;
/** an app icon stands straight */
export const SLOT_POSE = { yaw: -0.06, pitch: -0.05, roll: 0 } as const;

/** Scripted moments (s): drag Lilac → Pearl, tap Amber, hold to preview, tap Lilac. */
export const T = {
  dragIn: 0.95,
  dragOut: 2.0,
  coast: 3.11, // the front finishes on its own after the finger lifts
  chip1: 3.35,
  sweep1: [3.45, 4.35],
  hold: 4.95,
  fly: [5.3, 5.95],
  release: 6.9,
  back: [6.95, 7.65],
  chip2: 8.25,
  sweep2: [8.35, 9.25],
} as const;

export type Frame = {
  a: number; // glass index behind the front
  b: number; // glass index ahead of it
  dir: number; // +1: the new glass comes in from the right, −1 from the left
  front: number; // in pebble units, −FRONT_END … FRONT_END
  sel: number; // the selection ring, as a continuous glass index
  shown: number; // the glass whose name is shown
  lean: number; // extra turn from the drag, rad
  bob: number; // float, in pebble units (y up)
  press: number; // 0…1, the pebble pressed by a finger
  peek: number; // 0 on its pedestal … 1 in its Home Screen slot (may overshoot)
  land: number; // squash of a landing, −…+ (x grows, y shrinks)
  finger: number; // 0…1 alpha of the scripted finger
  fingerAt: number; // 0 hero (fingerX in pebble units), 1 chip (fingerX = chip index)
  fingerX: number;
  fingerPress: number;
};

/** progress of a sweep from its front value */
export function sweepProgress(front: number, dir: number) {
  'worklet';
  return clamp01(dir > 0 ? (FRONT_END - front) / (2 * FRONT_END) : (front + FRONT_END) / (2 * FRONT_END));
}

function tapPress(t: number, at: number) {
  'worklet';
  return easeOut(seg(t, at - 0.02, at + 0.08)) * (1 - easeOut(seg(t, at + 0.12, at + 0.26)));
}

export function frameAt(t: number): Frame {
  'worklet';
  const TAU = Math.PI * 2;
  let a = 0;
  let b = 1;
  let dir = 1;
  let front = FRONT_END;
  let lean = 0;
  let finger = 0;
  let fingerAt = 0;
  let fingerX = 0;
  let fingerPress = 0;
  let press = 0;
  let peek = 0;
  let land = 0;

  if (t < T.coast) {
    // drag Lilac → Pearl: the front follows the finger, then coasts out at the finger's speed
    a = 0;
    b = 1;
    const fx = 0.85 - 1.7 * easeInFling(seg(t, T.dragIn, T.dragOut));
    if (t < T.dragOut) {
      front = FRONT_END + (fx - FRONT_END) * easeOut(seg(t, T.dragIn, T.dragIn + 0.2));
    } else {
      // speed at release = 1.7 / 1.05 per s; easeOut(0.6) over 1.11 s leaves at the same speed
      front = -0.85 - (FRONT_END - 0.85) * easeOut(seg(t, T.dragOut, T.coast));
    }
    lean = -0.16 * Math.sin(Math.PI * seg(t, T.dragIn, T.dragOut)) + 0.05 * Math.sin(Math.PI * seg(t, T.dragOut, T.dragOut + 0.8));
    finger = easeOut(seg(t, T.dragIn - 0.15, T.dragIn)) * (1 - easeOut(seg(t, T.dragOut, T.dragOut + 0.2)));
    fingerAt = 0;
    fingerX = fx;
    press = easeOut(seg(t, T.dragIn - 0.05, T.dragIn + 0.1)) * (1 - easeOut(seg(t, T.dragOut - 0.05, T.dragOut + 0.15)));
    fingerPress = press;
  } else if (t < T.sweep1[1]) {
    // tap Amber: the glass pours through on its own
    a = 1;
    b = 3;
    front = FRONT_END - 2 * FRONT_END * easeInOut(seg(t, T.sweep1[0], T.sweep1[1]));
    finger = easeOut(seg(t, T.chip1 - 0.15, T.chip1 - 0.02)) * (1 - easeOut(seg(t, T.chip1 + 0.14, T.chip1 + 0.3)));
    fingerAt = 1;
    fingerX = 3;
    fingerPress = tapPress(t, T.chip1);
  } else if (t < T.chip2 - 0.2) {
    // hold to preview: the pebble flies into its Home Screen slot and back on release
    a = 3;
    b = 3;
    front = FRONT_END;
    press = easeOut(seg(t, T.hold, T.hold + 0.3)) * (1 - easeOut(seg(t, T.fly[0], T.fly[0] + 0.25)));
    peek = arrive(seg(t, T.fly[0], T.fly[1])) * (1 - arrive(seg(t, T.back[0], T.back[1])));
    land = 0.07 * Math.sin(Math.PI * seg(t, T.fly[1] - 0.12, T.fly[1] + 0.28)) * (1 - seg(t, T.fly[1] - 0.12, T.fly[1] + 0.28));
    land += 0.05 * Math.sin(Math.PI * seg(t, T.back[1] - 0.14, T.back[1] + 0.26)) * (1 - seg(t, T.back[1] - 0.14, T.back[1] + 0.26));
    finger = easeOut(seg(t, T.hold - 0.15, T.hold)) * (1 - easeOut(seg(t, T.release, T.release + 0.16)));
    fingerAt = 0;
    fingerX = 0;
    fingerPress = easeOut(seg(t, T.hold, T.hold + 0.12)) * (1 - easeOut(seg(t, T.release, T.release + 0.12)));
  } else {
    // tap Lilac: the original pours back in from the left
    a = 3;
    b = 0;
    dir = -1;
    front = -FRONT_END + 2 * FRONT_END * easeInOut(seg(t, T.sweep2[0], T.sweep2[1]));
    if (t >= T.sweep2[1]) {
      a = 0;
      b = 1;
      dir = 1;
      front = FRONT_END;
    }
    finger = easeOut(seg(t, T.chip2 - 0.15, T.chip2 - 0.02)) * (1 - easeOut(seg(t, T.chip2 + 0.14, T.chip2 + 0.3)));
    fingerAt = 1;
    fingerX = 0;
    fingerPress = tapPress(t, T.chip2);
  }
  const p = easeInOut(sweepProgress(front, dir));
  const sel = a + (b - a) * p;
  const shown = sweepProgress(front, dir) > 0.5 ? b : a;
  // a slow float that repeats once per loop (and twice for the sway), zero at t = 0
  const bob = 0.025 * Math.sin((TAU * t) / LOOP) * (1 - peek);
  lean += 0.04 * Math.sin((2 * TAU * t) / LOOP);
  return { a, b, dir, front, sel, shown, lean, bob, press, peek, land, finger, fingerAt, fingerX, fingerPress };
}

/** the meniscus line shows only while the front is crossing the pebble */
export function meniscus(front: number) {
  'worklet';
  const k = Math.abs(front);
  return 1 - clamp01((k - 1.0) / 0.4);
}
