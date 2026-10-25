/*
 Film Negative Card — the autoPlay timeline and the small easing helpers it is built from.

 Every value is a pure function of the loop time t (seconds), so the frame at t = LOOP is the
 frame at t = 0.

   0.0 – 1.3   rest: the strip lies half over the top of the light table, the lower part of
               frame 7 already glowing as a positive, the rest of the roll dimmed on the desk.
   1.3 – 3.0   it slides fully onto the light (a 4 % dip back first, a 3 % overshoot at the end),
               lifting off the desk on the way (bigger, softer shadow). The light's edge sweeps
               across the frame and turns the negative into the positive as it goes.
   3.0 – 5.0   hold on the light. The loupe fades in over the face (3.05 – 3.45, ease-out),
               wanders a little (one whole sine), and fades out (4.6 – 4.95).
   5.0 – 6.8   it slides back to its resting place, the positive sweeping back into the negative.
   6.8 – 8.0   rest.
 */

export const LOOP = 8;
/** Where the strip rests, between the desk (0) and fully on the light (1). */
export const REST_ON = 0.45;

const SLIDE_ON: [number, number] = [1.3, 3.0];
const SLIDE_OFF: [number, number] = [5.0, 6.8];
const LOUPE_IN: [number, number] = [3.05, 3.45];
const LOUPE_OUT: [number, number] = [4.6, 4.95];
const LOUPE_WANDER: [number, number] = [3.0, 5.0];
/** How far the loupe wanders from the face, in pt. */
const WANDER_X = 26;
const WANDER_Y = 9;
/** Overshoot strength of the slide (0.75 ≈ 3–4 % past the light, and the same dip before it). */
const BACK = 0.75;

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

/** 0 before a, 1 after b, linear between. */
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeOutCubic(u: number) {
  'worklet';
  const v = 1 - u;
  return 1 - v * v * v;
}

export function easeInOutCubic(u: number) {
  'worklet';
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}

/** Ease in-out with a small dip before the move (anticipation) and a small overshoot after it. */
export function easeInOutBack(u: number, c1: number) {
  'worklet';
  const c2 = c1 * 1.525;
  return u < 0.5
    ? (Math.pow(2 * u, 2) * ((c2 + 1) * 2 * u - c2)) / 2
    : (Math.pow(2 * u - 2, 2) * ((c2 + 1) * (u * 2 - 2) + c2) + 2) / 2;
}

export function smoothstep(a: number, b: number, x: number) {
  'worklet';
  const u = clamp01((x - a) / (b - a));
  return u * u * (3 - 2 * u);
}

export type FilmPose = {
  /** 0 = on the desk, 1 = on the light; rests at REST_ON (may pass 1 or dip briefly). */
  on: number;
  /** 0 = flat on a surface, 1 = held up mid-move. */
  lift: number;
  /** Loupe opacity 0…1 and its offset from the face in pt. */
  loupe: number;
  loupeDx: number;
  loupeDy: number;
};

export function filmPose(t: number): FilmPose {
  'worklet';
  const u1 = seg(t, SLIDE_ON[0], SLIDE_ON[1]);
  const u2 = seg(t, SLIDE_OFF[0], SLIDE_OFF[1]);
  const on = REST_ON + (1 - REST_ON) * (easeInOutBack(u1, BACK) - easeInOutBack(u2, BACK));
  const lift = Math.sin(Math.PI * u1) + Math.sin(Math.PI * u2);
  const a = easeOutCubic(seg(t, LOUPE_IN[0], LOUPE_IN[1])) * (1 - easeInOutCubic(seg(t, LOUPE_OUT[0], LOUPE_OUT[1])));
  const w = seg(t, LOUPE_WANDER[0], LOUPE_WANDER[1]);
  return {
    on,
    lift,
    loupe: a,
    loupeDx: WANDER_X * Math.sin(2 * Math.PI * w),
    loupeDy: WANDER_Y * Math.sin(4 * Math.PI * w),
  };
}

/** Pulled past a limit, the strip follows less and less (rubber band). */
export function rubber(x: number, lo: number, hi: number) {
  'worklet';
  if (x < lo) {
    const o = lo - x;
    return lo - o / (1 + o / 60);
  }
  if (x > hi) {
    const o = x - hi;
    return hi + o / (1 + o / 60);
  }
  return x;
}
