import { Easing } from 'react-native-reanimated';

/** One loop of the autoPlay, in seconds. */
export const LOOP = 7;
// The autoPlay's timeline, in seconds from the start of a loop: "+" goes down and comes up (which
// opens the bubbles), one photo is picked and let go, then "+" is pressed again (which closes them).
export const OPEN_PRESS = 0.8;
export const PRESS_FOR = 0.12;
export const PICK_AT = 3.2;
export const UNPICK_AT = 4.2;
export const CLOSE_PRESS = 4.8;
/** The bubble the autoPlay picks: the second one. */
export const AUTO_PICK = 1;
/** Seconds after the last touch before the autoPlay takes over again. */
export const USER_HOLD = 3;

export type Spring = { mass: number; stiffness: number; damping: number };
// Where a bubble is along its path (a little past the end, then back), and how big it is
// (from a fifth of its size to about 1.09 and back to 1).
export const TRAVEL: Spring = { mass: 1, stiffness: 170, damping: 18 };
export const GROW: Spring = { mass: 1, stiffness: 220, damping: 17 };
// The "+" turning into an "×" and back.
export const TURN: Spring = { mass: 1, stiffness: 260, damping: 22 };
// A picked bubble swelling and its check popping in.
export const POP: Spring = { mass: 1, stiffness: 320, damping: 17 };
// The "+" coming back up after a press.
export const RELEASE: Spring = { mass: 1, stiffness: 380, damping: 20 };

/** A bubble is born at this share of its size, behind the "+". */
export const BORN_SCALE = 0.2;
/** Seconds a new bubble takes to become opaque, so it never pops in. */
export const BORN_FADE = 0.08;
/** Seconds after its birth over which a bubble eases into its float. */
export const FLOAT_IN = 0.8;
/** How far a path bows to the left of the straight line, as a share of its length. */
export const CURVE = 0.18;
/** Seconds each bubble takes to fall back into the "+". */
export const FALL = 0.35;
/** Seconds the "+" takes to go down when pressed, and how far it goes. */
export const PRESS_IN = 0.1;
export const PRESS_SCALE = 0.92;
/** The give of the "+" when a bubble lands in it: down, then back up. */
export const PULSE_IN = 0.07;
export const PULSE_OUT = 0.28;
/** A picked bubble's size, and how long it takes to shrink back when let go. */
export const PICK_SCALE = 1.06;
export const UNPICK_FOR = 0.22;
export const TURN_ANGLE = 45;
/** Reduced motion: every change is a fade of this length, in place. */
export const FADE = 0.2;

export const EASE_OUT = Easing.bezierFn(0.23, 1, 0.32, 1);
/** A falling bubble starts slowly and drops into the "+" fast. */
export const SLIDE = Easing.bezierFn(0.55, 0.085, 0.68, 0.53);
/** It keeps most of its size until it is well on its way. */
export const SHRINK = Easing.bezierFn(0.5, 0, 0.75, 0.6);

/** Where a bubble rests and how it floats, at cluster scale 1. */
export type Slot = {
  /** Its centre at rest, from the centre of the "+": x to the right, y up, in points. */
  x: number;
  y: number;
  size: number;
  /** Seconds after the "+" is released that it is born, and that it starts to fall. */
  openDelay: number;
  closeDelay: number;
  /** Its float: points each way and seconds a period on each axis, and where it starts (radians). */
  floatX: number;
  floatY: number;
  periodX: number;
  periodY: number;
  phase: number;
  /** How far the "+" gives when it lands in it. */
  dip: number;
};

// Born big one first, then the mushroom, the flower, the burst and the tiny one; they fall back in
// the order big, tiny, mushroom, flower, burst.
export const SLOTS: Slot[] = [
  {
    x: 53,
    y: 126,
    size: 165,
    openDelay: 0,
    closeDelay: 0,
    floatX: 1,
    floatY: 3,
    periodX: 3.9,
    periodY: 3.4,
    phase: 0,
    dip: 0.08,
  },
  {
    x: -81,
    y: 157,
    size: 95,
    openDelay: 0.12,
    closeDelay: 0.45,
    floatX: 1.5,
    floatY: 3,
    periodX: 3.3,
    periodY: 2.8,
    phase: 1.9,
    dip: 0.06,
  },
  {
    x: -22,
    y: 232,
    size: 76,
    openDelay: 0.06,
    closeDelay: 0.3,
    floatX: 1.2,
    floatY: 3,
    periodX: 3.6,
    periodY: 3.1,
    phase: 4,
    dip: 0.05,
  },
  {
    x: -60,
    y: 66,
    size: 76,
    openDelay: 0.18,
    closeDelay: 0.6,
    floatX: 1.5,
    floatY: 2.5,
    periodX: 3,
    periodY: 2.6,
    phase: 2.7,
    dip: 0.05,
  },
  {
    x: 36,
    y: 230,
    size: 25,
    openDelay: 0.24,
    closeDelay: 0.15,
    floatX: 4,
    floatY: 5,
    periodX: 3.4,
    periodY: 3,
    phase: 5.2,
    dip: 0.03,
  },
];

/** The cluster's width and height above the centre of the "+" at scale 1, float included. */
export const CLUSTER_WIDTH = 272;
export const CLUSTER_HEIGHT = 274;
/** Seconds from the "+" closing the cluster until everything is still. */
export const SETTLE = 0.6 + FALL + PULSE_IN + PULSE_OUT;

/** Everything that happened, as times on the clock in seconds; picks are 1 (on) or 0 per bubble. */
export type Events = {
  openAt: number;
  closeAt: number;
  downAt: number;
  upAt: number;
  pickOn: number[];
  pickAt: number[];
};

export const NEVER = -1e6;

/** A slot's place on screen: its centre at rest, the bend of its path, and the centre of the "+". */
export type Place = {
  restX: number;
  restY: number;
  ctrlX: number;
  ctrlY: number;
  bx: number;
  by: number;
};

export function clamp01(value: number) {
  'worklet';
  return Math.min(1, Math.max(0, value));
}

/** A spring let go from 0 towards 1 at rest, `t` seconds later (the closed form, so every stack agrees). */
export function spring(t: number, s: Spring) {
  'worklet';
  if (t <= 0) return 0;
  const w0 = Math.sqrt(s.stiffness / s.mass);
  const zeta = s.damping / (2 * Math.sqrt(s.stiffness * s.mass));
  if (zeta >= 1) return 1 - Math.exp(-w0 * t) * (1 + w0 * t);
  const wd = w0 * Math.sqrt(1 - zeta * zeta);
  return 1 - Math.exp(-zeta * w0 * t) * (Math.cos(wd * t) + ((zeta * w0) / wd) * Math.sin(wd * t));
}

function smoothstep(x: number) {
  'worklet';
  const t = clamp01(x);
  return t * t * (3 - 2 * t);
}

/** The time of an event at `offset` in a loop: this loop's if it has happened, else the last loop's. */
function last(base: number, tau: number, offset: number) {
  'worklet';
  return tau >= offset ? base + offset : base + offset - LOOP;
}

/** The events of the autoPlay at `now` for a loop that started at `start`. */
export function autoEvents(now: number, start: number, count: number): Events {
  'worklet';
  const tau = (now - start) % LOOP;
  const base = now - tau;
  const opened = last(base, tau, OPEN_PRESS + PRESS_FOR);
  const closed = last(base, tau, CLOSE_PRESS + PRESS_FOR);
  const picked = last(base, tau, PICK_AT);
  const unpicked = last(base, tau, UNPICK_AT);
  const pickOn: number[] = [];
  const pickAt: number[] = [];
  for (let i = 0; i < count; i++) {
    pickOn.push(i === AUTO_PICK && picked > unpicked ? 1 : 0);
    pickAt.push(i === AUTO_PICK ? Math.max(picked, unpicked) : NEVER);
  }
  return {
    openAt: opened,
    closeAt: closed,
    downAt: Math.max(last(base, tau, OPEN_PRESS), last(base, tau, CLOSE_PRESS)),
    upAt: Math.max(opened, closed),
    pickOn,
    pickAt,
  };
}

export type Pose = { x: number; y: number; scale: number; opacity: number };

/** Where a bubble is, how big and how opaque, at `now`. */
export function pose(slot: Slot, place: Place, now: number, openAt: number, closeAt: number, still: boolean): Pose {
  'worklet';
  const hidden = { x: place.bx, y: place.by, scale: 0, opacity: 0 };
  const closing = closeAt >= openAt;
  if (closing && now - closeAt >= (still ? FADE : slot.closeDelay + FALL)) return hidden;
  const since = now - openAt;
  let x = place.restX;
  let y = place.restY;
  let scale = 1;
  let opacity = 1;
  if (still) {
    opacity = clamp01(since / FADE);
  } else {
    const t = since - slot.openDelay;
    if (t <= 0) return hidden;
    // Along a quadratic curve from the "+" to its place, a little past it and back.
    const u = spring(t, TRAVEL);
    const v = 1 - u;
    x = v * v * place.bx + 2 * v * u * place.ctrlX + u * u * place.restX;
    y = v * v * place.by + 2 * v * u * place.ctrlY + u * u * place.restY;
    const float = smoothstep(t / FLOAT_IN);
    x += float * slot.floatX * Math.sin((2 * Math.PI * since) / slot.periodX + slot.phase);
    y += float * slot.floatY * Math.sin((2 * Math.PI * since) / slot.periodY + slot.phase);
    scale = BORN_SCALE + (1 - BORN_SCALE) * spring(t, GROW);
    opacity = clamp01(t / BORN_FADE);
  }
  if (closing) {
    if (still) {
      opacity *= 1 - clamp01((now - closeAt) / FADE);
    } else {
      const c = clamp01((now - closeAt - slot.closeDelay) / FALL);
      const k = SLIDE(c);
      x += (place.bx - x) * k;
      y += (place.by - y) * k;
      scale *= 1 - SHRINK(c);
    }
  }
  return { x, y, scale, opacity };
}

/** How picked a bubble looks: 0 to 1, with an overshoot when it is picked. */
export function pickAmount(on: number, at: number, now: number, still: boolean) {
  'worklet';
  const t = now - at;
  if (on) return still ? clamp01(t / FADE) : spring(t, POP);
  return 1 - (still ? clamp01(t / FADE) : EASE_OUT(clamp01(t / UNPICK_FOR)));
}

/** The size of the "+": down while pressed, springing back, and giving as each bubble lands. */
export function buttonScale(e: Events, now: number, still: boolean) {
  'worklet';
  let press = 0;
  if (e.downAt > e.upAt) press = EASE_OUT(clamp01((now - e.downAt) / PRESS_IN));
  else press = EASE_OUT(clamp01((e.upAt - e.downAt) / PRESS_IN)) * (1 - spring(now - e.upAt, RELEASE));
  let dip = 0;
  if (!still && e.closeAt >= e.openAt) {
    for (let i = 0; i < SLOTS.length; i++) {
      const d = now - e.closeAt - SLOTS[i].closeDelay - FALL;
      if (d < 0 || d > PULSE_IN + PULSE_OUT) continue;
      const amount = d < PULSE_IN ? EASE_OUT(d / PULSE_IN) : 1 - SHRINK((d - PULSE_IN) / PULSE_OUT);
      dip = Math.max(dip, amount * SLOTS[i].dip);
    }
  }
  return (1 - (1 - PRESS_SCALE) * press) * (1 - dip);
}

/** The turn of the "+", in degrees: 45 while open. */
export function plusTurn(e: Events, now: number, still: boolean) {
  'worklet';
  if (e.openAt > e.closeAt) return still ? TURN_ANGLE : TURN_ANGLE * spring(now - e.openAt, TURN);
  return still ? 0 : TURN_ANGLE * (1 - spring(now - e.closeAt, TURN));
}
