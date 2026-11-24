// All the motion of the Spring Reorder Checklist. Positions are in slots (one row = 1) unless named otherwise; the film
// is a pure function of loop time, so the neighbours' springs are closed-form from the moment the held row passes them.

export const LOOP = 9;

export const MODE_AUTO = 0;
export const MODE_MANUAL = 1;
export const MODE_RETURN = 2;

export const IDLE_S = 2;
export const RETURN_S = 0.75;

export const ROW_H = 64;
export const LONG_PRESS_MS = 260;
export const LIFT_SCALE = 1.035;
export const EDGE_ZONE = 88; // pt from the list's edge where auto-scroll starts
export const EDGE_SPEED = 620; // pt/s at the very edge

export const FILM_FROM = 1;
export const FILM_TO = 9;
export const DROP_PAST = 0.18; // the film's finger lets go this far past the slot

// response 0.34 s, damping ratio 0.74
export const PART_SPRING = { mass: 1, stiffness: 341.5, damping: 27.35 };
// response 0.42 s, damping ratio 0.56: the small overshoot of a dropped row
export const DROP_SPRING = { mass: 1, stiffness: 223.8, damping: 16.76 };
export const LIFT_SPRING = { mass: 1, stiffness: 420, damping: 26 };

export const T = {
  pressA: 0.6,
  dragA: 0.85,
  dropA: 2.55,
  tapA: 3.25,
  drawA: 3.35,
  drawAEnd: 3.85,
  pressB: 4.4,
  dragB: 4.65,
  dropB: 6.35,
  tapB: 7.0,
  drawB: 7.1,
  drawBEnd: 7.6,
};

export function clamp(v: number, a: number, b: number) {
  'worklet';
  return Math.min(Math.max(v, a), b);
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp((t - a) / (b - a), 0, 1);
}

export function mix(a: number, b: number, k: number) {
  'worklet';
  return a + (b - a) * k;
}

export function smooth(k: number) {
  'worklet';
  return k * k * (3 - 2 * k);
}

export function easeOutCubic(k: number) {
  'worklet';
  return 1 - Math.pow(1 - k, 3);
}

export function easeInOutCubic(k: number) {
  'worklet';
  return k < 0.5 ? 4 * k * k * k : 1 - Math.pow(-2 * k + 2, 3) / 2;
}

function easeInOutCubicInverse(p: number) {
  'worklet';
  return p < 0.5 ? Math.cbrt(p / 4) : 1 - Math.cbrt(2 * (1 - p)) / 2;
}

export function win(t: number, a: number, b: number, c: number, d: number) {
  'worklet';
  return smooth(seg(t, a, b)) * (1 - smooth(seg(t, c, d)));
}

/** A damped spring released at rest from `from`, s seconds ago. */
export function springAt(s: number, from: number, to: number, response: number, ratio: number) {
  'worklet';
  if (s <= 0) return from;
  const w = (2 * Math.PI) / response;
  const zw = ratio * w;
  const wd = w * Math.sqrt(1 - ratio * ratio);
  const a = from - to;
  return to + Math.exp(-zw * s) * (a * Math.cos(wd * s) + ((zw * a) / wd) * Math.sin(wd * s));
}

/** The held row's position in the film, in slots. */
export function filmHeroSlot(t: number, from: number, to: number) {
  'worklet';
  if (t < T.dragA) return from;
  if (t < T.dropA) return mix(from, to + DROP_PAST, easeInOutCubic(seg(t, T.dragA, T.dropA)));
  if (t < T.dragB) return springAt(t - T.dropA, to + DROP_PAST, to, 0.42, 0.56);
  if (t < T.dropB) return mix(to, from - DROP_PAST, easeInOutCubic(seg(t, T.dragB, T.dropB)));
  if (t < T.tapB + 0.6) return springAt(t - T.dropB, from - DROP_PAST, from, 0.42, 0.56);
  return from;
}

function partStep(s: number) {
  'worklet';
  if (s <= 0) return 0;
  if (s >= 1.1) return 1;
  return springAt(s, 0, 1, 0.34, 0.74);
}

/** Any row's position in the film, in slots: a row between the two ends steps up as the held row passes it and back. */
export function filmRowSlot(slot: number, t: number, from: number, to: number) {
  'worklet';
  if (slot === from) return filmHeroSlot(t, from, to);
  if (slot < from || slot > to) return slot;
  const crossing = slot - 0.5;
  const forward = (crossing - from) / (to + DROP_PAST - from);
  const back = (to - crossing) / (to - from + DROP_PAST);
  const t1 = mix(T.dragA, T.dropA, easeInOutCubicInverse(forward));
  const t2 = mix(T.dragB, T.dropB, easeInOutCubicInverse(back));
  return slot - partStep(t - t1) + partStep(t - t2);
}

export function filmLift(t: number) {
  'worklet';
  if (t < T.pressA) return 0;
  if (t < T.dragA) return easeOutCubic(seg(t, T.pressA, T.dragA));
  if (t < T.dropA) return 1;
  if (t < T.pressB) return springAt(t - T.dropA, 1, 0, 0.36, 0.6);
  if (t < T.dragB) return easeOutCubic(seg(t, T.pressB, T.dragB));
  if (t < T.dropB) return 1;
  if (t < T.tapB + 0.6) return springAt(t - T.dropB, 1, 0, 0.36, 0.6);
  return 0;
}

export function filmCheck(t: number) {
  'worklet';
  return easeOutCubic(seg(t, T.drawA, T.drawAEnd)) * (1 - easeInOutCubic(seg(t, T.drawB, T.drawBEnd)));
}

export function filmScroll(t: number, distance: number) {
  'worklet';
  const down = easeInOutCubic(seg(t, T.dragA + 0.3, T.dropA - 0.1));
  const up = easeInOutCubic(seg(t, T.dragB + 0.3, T.dropB - 0.1));
  return distance * (down - up);
}

export function filmGhost(t: number) {
  'worklet';
  return win(t, 0.4, 0.6, 2.55, 2.75) + win(t, 3.05, 3.25, 3.45, 3.65) + win(t, 4.2, 4.4, 6.35, 6.55) + win(t, 6.8, 7.0, 7.2, 7.4);
}

/** 1 while the ghost finger is on the check circle, 0 on the row's grip. */
export function filmGhostOnCheck(t: number) {
  'worklet';
  return (t >= 2.9 && t < 3.9) || t >= 6.7 ? 1 : 0;
}

/** The ghost finger pressing for the two taps. */
export function filmGhostTap(t: number) {
  'worklet';
  return win(t, T.tapA - 0.04, T.tapA + 0.06, T.tapA + 0.14, T.tapA + 0.26) + win(t, T.tapB - 0.04, T.tapB + 0.06, T.tapB + 0.14, T.tapB + 0.26);
}

/** Where a row is after moving `from` to `to`: the order table the film leaves when a finger takes over. */
export function moveOrder(order: readonly number[], id: number, toSlot: number) {
  'worklet';
  const fromSlot = order[id];
  const next = order.slice();
  if (fromSlot === toSlot) return next;
  for (let i = 0; i < next.length; i += 1) {
    const s = next[i];
    if (i === id) next[i] = toSlot;
    else if (fromSlot < toSlot && s > fromSlot && s <= toSlot) next[i] = s - 1;
    else if (fromSlot > toSlot && s >= toSlot && s < fromSlot) next[i] = s + 1;
  }
  return next;
}
