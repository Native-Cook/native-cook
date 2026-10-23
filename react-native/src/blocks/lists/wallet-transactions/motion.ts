import { Easing } from 'react-native-reanimated';

// Motion for a still pin. One clock drives everything (CRAFT.md §2): each value is a pure function
// of the loop time, so the loop is exact, it starts and ends on the approved rest frame, and the
// Swift and Kotlin ports can match it frame for frame.

export const LOOP_MS = 9000;
export const RESUME_AFTER_MS = 3500;
export const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);

// ---- hero: the Invest tile is held, lifts off the sheet and its holdings fan out ----
export const LIFT = {
  pressAt: 1200, pressInMs: 120, pressOutMs: 160, pressScale: 0.97, // anticipation
  upAt: 1320, up: { omega: 17, zeta: 0.72 }, // the tile has mass: a 3 % overshoot
  downAt: 3000, down: { omega: 15.7, zeta: 1 }, // let go: settles without a bounce
  scale: 0.04, rise: 5, // pt
  tilt: 1.6, // degrees the tile leans towards the finger (touch) / the scripted finger
  drift: 3, // pt it drifts towards the finger
  scriptTilt: { x: 0.35, y: -0.2 }, // where the scripted finger rests on the tile (-1..1)
  shadow: { opacity: 0.2, dy: [2, 12], spread: [0.9, 1] }, // contact shadow grows with the lift
} as const;
export const FAN = {
  at: 1450, stagger: 45, // ms between the four holdings
  out: { omega: 20, zeta: 0.68 },
  backAt: 2900, backStagger: 25, back: { omega: 18, zeta: 1 },
  step: 27, rise: 3, // the holdings spread from 21.9 to 27 pt apart and rise 3 pt
} as const;

// ---- supporting: the pin's own filter chips ----
export const CHIP_SPRING = { duration: 340, dampingRatio: 0.86 } as const; // the highlight has a little mass
export const CHIP_OMEGA = 18.5; // the same spring as a function of time (response 0.34 s)
export const ROW_FADE_MS = 180; // data the reader is reading never slides; it cross-fades
export const ROW_STAGGER_MS = 30; // the rows follow each other, top first
export const PRESS = { scale: 0.97, spring: { duration: 220, dampingRatio: 0.9 } } as const;

/** Autoplay chips: the chip picked at each time (ms from the start of the loop). */
export const SCRIPT: readonly { at: number; chip: number }[] = [
  { at: 0, chip: 0 },
  { at: 3900, chip: 1 },
  { at: 5500, chip: 2 },
  { at: 7100, chip: 0 },
];

// ---- pure functions of time ----
function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}
/** A spring released at `at` from 0 towards 1 (0 before it). */
export function springStep(t: number, at: number, omega: number, zeta: number) {
  'worklet';
  const s = (t - at) / 1000;
  if (s <= 0) return 0;
  if (zeta >= 1) return 1 - (1 + omega * s) * Math.exp(-omega * s);
  const wd = omega * Math.sqrt(1 - zeta * zeta);
  return 1 - Math.exp(-zeta * omega * s) * (Math.cos(wd * s) + ((zeta * omega) / wd) * Math.sin(wd * s));
}
function easeOut(u: number) {
  'worklet';
  const x = clamp01(u);
  return 1 - (1 - x) * (1 - x) * (1 - x);
}

export function liftAt(t: number) {
  'worklet';
  return springStep(t, LIFT.upAt, LIFT.up.omega, LIFT.up.zeta) - springStep(t, LIFT.downAt, LIFT.down.omega, LIFT.down.zeta);
}
export function pressAt(t: number) {
  'worklet';
  return easeOut((t - LIFT.pressAt) / LIFT.pressInMs) * (1 - easeOut((t - LIFT.upAt) / LIFT.pressOutMs));
}
export function fanAt(t: number, k: number) {
  'worklet';
  return (
    springStep(t, FAN.at + k * FAN.stagger, FAN.out.omega, FAN.out.zeta) -
    springStep(t, FAN.backAt + (3 - k) * FAN.backStagger, FAN.back.omega, FAN.back.zeta)
  );
}
/** The highlight's position: each pick springs it on from where it was. */
export function chipPosAt(t: number) {
  'worklet';
  let p = SCRIPT[0].chip;
  for (let i = 1; i < SCRIPT.length; i++) {
    p += (SCRIPT[i].chip - SCRIPT[i - 1].chip) * springStep(t, SCRIPT[i].at, CHIP_OMEGA, 0.86);
  }
  return p;
}
/** The rows' and chip labels' cross-fade value; row `r` follows `r` staggers later. */
export function chipFadeAt(t: number, r: number) {
  'worklet';
  let p = SCRIPT[0].chip;
  for (let i = 1; i < SCRIPT.length; i++) {
    p += (SCRIPT[i].chip - SCRIPT[i - 1].chip) * clamp01((t - SCRIPT[i].at - r * ROW_STAGGER_MS) / ROW_FADE_MS);
  }
  return p;
}
