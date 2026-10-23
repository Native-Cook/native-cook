import { Easing } from 'react-native-reanimated';

// The loop: rest on the first film, pick it up and throw it aside, the second film is set down,
// hold, then the same turn back. Every value below is a pure function of the loop time, so the
// Swift and Kotlin ports can match it frame for frame. Times in seconds.
export const LOOP_MS = 7000;
export const LOOP = LOOP_MS / 1000;

export const TURNS = [
  { lift: 0.8, page: 1.1, down: 2.05, stars: 2.5, from: 0, to: 1 },
  { lift: 3.8, page: 4.1, down: 5.05, stars: 5.5, from: 1, to: 0 },
] as const;

export const LIFT = { scale: 0.1, radius: 34, shadow: 0.55, shadowY: 18, shadowBlur: 36, pickMs: 450 };
export const THROW = { x: 1.15, tilt: 7, drop: 36, shrink: 0.04, artLag: 0.3 }; // x and artLag in screen widths, drop in pt
export const WAIT = { scale: 0.14, dim: 0.55 }; // the card underneath: 0.86 of the lifted size, dimmed
export const ROOM = { blur: 40, dim: 0.55 }; // the blurred art the lifted card floats over
export const SHEEN = { alpha: 0.22, width: 0.55, slant: 20, travel: 1.6 };
export const STAR = { peak: 0.14, stagger: 0.055, each: 0.22 };
export const STARS_TOTAL = STAR.each + STAR.stagger * 4;
export const BOB_MS = 1750; // the chevron breathes four times a loop

export const PAGE_SPRING = { response: 0.62, damping: 0.86 };
export const DOWN_SPRING = { response: 0.5, damping: 0.78 };
export const PICK_SPRING = { response: 0.35, damping: 1 };
export const IDLE_RESUME_MS = 3500;
export const FLICK = 0.11; // px/ms

const easeOut = Easing.bezierFn(0.23, 1, 0.32, 1);
const easeInOut = Easing.bezierFn(0.45, 0, 0.55, 1);

export function seg(t: number, a: number, b: number) {
  'worklet';
  return Math.min(1, Math.max(0, (t - a) / (b - a)));
}

/** Step response of a damped spring released from rest at τ = 0 (0 before, → 1 after). */
export function springStep(tau: number, response: number, damping: number) {
  'worklet';
  if (tau <= 0) return 0;
  if (tau > response * 4) return 1;
  const w = (2 * Math.PI) / response;
  if (damping >= 1) return 1 - Math.exp(-w * tau) * (1 + w * tau);
  const wd = w * Math.sqrt(1 - damping * damping);
  return 1 - Math.exp(-damping * w * tau) * (Math.cos(wd * tau) + ((damping * w) / wd) * Math.sin(wd * tau));
}

export type Pose = { page: number; lift: number; swell0: number; swell1: number };

/** Where everything is at loop time t (seconds). */
export function poseAt(t: number): Pose {
  'worklet';
  let page = 0;
  let lift = 0;
  const swell = [0, 0];
  for (const turn of TURNS) {
    const p = springStep(t - turn.page, PAGE_SPRING.response, PAGE_SPRING.damping);
    page += (turn.to - turn.from) * p;
    const up = easeOut(seg(t, turn.lift, turn.lift + LIFT.pickMs / 1000));
    const down = springStep(t - turn.down, DOWN_SPRING.response, DOWN_SPRING.damping);
    lift += up * (1 - down);
    const s = seg(t, turn.stars, turn.stars + STARS_TOTAL);
    if (s > 0 && s < 1) swell[turn.to] = s;
  }
  return { page, lift, swell0: swell[0], swell1: swell[1] };
}

/** Scale of star k while its film's stars swell (s: 0 → 1 over STARS_TOTAL). */
export function starScale(s: number, k: number) {
  'worklet';
  if (s <= 0 || s >= 1) return 1;
  const local = seg(s * STARS_TOTAL, STAR.stagger * k, STAR.stagger * k + STAR.each);
  return 1 + STAR.peak * Math.sin(Math.PI * easeInOut(local));
}

export function bobAt(ms: number) {
  'worklet';
  return -3 * Math.sin(((ms % BOB_MS) / BOB_MS) * Math.PI) ** 2;
}
