/**
 * Find Friends Invite — the motion. Every value on screen is a pure function of three effect
 * clocks (seconds), and in autoPlay those clocks are a pure function of the loop time
 * `t` (0 ≤ t < LOOP), so t = LOOP renders exactly like t = 0 (the pin's resting screen).
 *
 *   0.00–0.50  rest
 *   0.50–0.77  "invite them" presses in to 0.97 and is released
 *   0.77–1.73  the button folds itself into a paper plane: label out and corners square up,
 *              the left half folds over the right (book fold), the two long corners fold back
 *              behind it and leave a dart, which folds along its keel; the wings pop open
 *   1.73–3.63  the dart launches on a curve with one loop and leaves past the top-right corner,
 *              banking through the turns, its shadow on the card, a contrail behind it; its wake
 *              rocks the cup
 *   2.97–3.97  a new sheet appears where the dart was folded and unfolds into the button
 *   4.10–4.37  "check contacts" presses in and is released; the black button fills (progress)
 *   4.37–4.80  the four avatars melt into one liquid blob (metaball shader), their faces sink
 *   4.72–5.80  five contact droplets fly in from the screen edges and are absorbed by the blob
 *   5.90–6.85  the fill completes and the blob splits back into the four avatars, faces return
 *   6.90–7.80  rest
 *
 * Touch drives the same clocks: tap "invite them", "check contacts" or the cup. 2 s after the
 * last touch has played out, autoPlay starts again from t = 0.
 */

export const LOOP = 7.8;
/** After the last touch-driven effect has finished, autoPlay waits this long (ms). */
export const RESUME_AFTER = 2000;

/* ---------- easing (the shared vocabulary) ---------- */

/** cubic-bezier evaluator (x1, y1, x2, y2), worklet-safe. */
export function bezier(x1: number, y1: number, x2: number, y2: number, x: number) {
  'worklet';
  if (x <= 0) return 0;
  if (x >= 1) return 1;
  let lo = 0;
  let hi = 1;
  let u = x;
  for (let i = 0; i < 24; i++) {
    u = (lo + hi) / 2;
    const xu = 3 * (1 - u) * (1 - u) * u * x1 + 3 * (1 - u) * u * u * x2 + u * u * u;
    if (xu < x) lo = u;
    else hi = u;
  }
  return 3 * (1 - u) * (1 - u) * u * y1 + 3 * (1 - u) * u * u * y2 + u * u * u;
}

export const EASE_OUT = [0.23, 1, 0.32, 1] as const;
export const EASE_IN_OUT = [0.77, 0, 0.175, 1] as const;
/** A paper fold: the sheet leaves the table gently, flops over fast and lands softly. */
export const EASE_FOLD = [0.6, 0, 0.3, 1] as const;
/** A thrown plane: fast off the hand, hangs through the loop, speeds away (slope 2.33 at both ends). */
export const EASE_HANG = [0.3, 0.7, 0.7, 0.3] as const;

export function ease(c: readonly [number, number, number, number], x: number) {
  'worklet';
  return bezier(c[0], c[1], c[2], c[3], x);
}

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

/** Underdamped step response 0 → 1 (closed form of a spring released at rest). */
export function springStep(tau: number, omega: number, zeta: number) {
  'worklet';
  if (tau <= 0) return 0;
  if (zeta * omega * tau > 9) return 1; // e^(−9) ≈ 1e-4: snap exactly to rest
  const wd = omega * Math.sqrt(1 - zeta * zeta);
  const e = Math.exp(-zeta * omega * tau);
  return 1 - e * (Math.cos(wd * tau) + (zeta / Math.sqrt(1 - zeta * zeta)) * Math.sin(wd * tau));
}

/** Damped sine kick, windowed so it is exactly 0 from `end` on. */
export function kick(tau: number, period: number, decay: number, fadeFrom: number, end: number) {
  'worklet';
  if (tau <= 0 || tau >= end) return 0;
  const w = 1 - seg(tau, fadeFrom, end);
  return Math.exp(-tau / decay) * Math.sin((2 * Math.PI * tau) / period) * w;
}

/* ---------- invite: press, fold, flight, re-form ---------- */

/** Spring back after a release, closed form of withSpring({ duration: 400, dampingRatio: 0.8 }). */
export const SPRING_UI = { omega: 15.7, zeta: 0.8 } as const;

export const INVITE = {
  pressIn: 0.12,
  release: 0.27, // the finger lifts: the fold starts
  pressScale: 0.97,
} as const;

/** Times are seconds after the release. Angles in degrees. */
export const FOLD = {
  /** Label and plane icon leave the button (opacity 1 → 0, scale 1 → 0.96). */
  labelOut: 0.1,
  labelScale: 0.96,
  /** The pill squares up into a sheet of paper. */
  radiusDur: 0.16,
  paperRadius: 3,
  /** Skia draws the sheet from here; the React Native button hides a little later (no gap). */
  skiaOn: 0.1,
  rnOff: 0.15,
  /** Left half over the right, about the vertical centre crease. */
  book: [0.16, 0.5] as const,
  /** The two long corners fold back behind the sheet (mountain folds): a triangle is left.
   *  They turn edge-on at 90° and are tucked away there (fading over the last 20°). */
  flapTop: [0.5, 0.7] as const,
  flapBot: [0.54, 0.74] as const,
  flapAngle: 90,
  flapFade: 20,
  /** Keel: both halves fold up toward the viewer, then the wings pop open on a spring. */
  keelClose: [0.72, 0.88] as const,
  keelAngle: 64,
  wingAngle: 24,
  wingSpring: { omega: 15, zeta: 0.42 },
  launch: 0.96,
} as const;

export const FLIGHT = {
  duration: 1.9,
  /** The dart shrinks from the sheet's size to its flying size, and turns to its heading. */
  scale: 0.46,
  scaleDur: 0.42,
  yawDur: 0.32,
  bankIn: 0.25,
  /** Height above the card (pt, toward the viewer) at mid-flight: perspective and shadow. */
  lift: 60,
  /** Camera distance for the perspective (pt); it looks at the middle of the loop. */
  camera: 1000,
  /** Bank (rad) per unit of curvature (rad / pt), clamped. */
  bankGain: 30,
  /** Where the camera looks (pin pt): the centre of the loop. */
  cameraAt: [312, 222] as const,
  bankMax: 0.62,
  /** Catmull-Rom path through these points (pin pt, the dart's centre). The first point is the
   *  folded dart's centre on the pin's 402 pt screen; other widths shift the whole path. */
  points: [
    [281, 341.2],
    [325, 326],
    [360, 292],
    [368, 246],
    [350, 200],
    [314, 176],
    [277, 190],
    [264, 226],
    [282, 256],
    [320, 262],
    [360, 238],
    [398, 186],
    [445, 105],
    [505, 15],
    [560, -60],
  ] as readonly (readonly [number, number])[],
  samplesPerSegment: 24,
} as const;

/** A new sheet appears where the dart was folded and unfolds into the button. */
export const REFORM = {
  appear: 2.2, // the dart is on its way out: the loop has had the screen to itself
  appearDur: 0.2,
  appearScale: 0.96,
  unfold: 2.38,
  spring: { omega: 11, zeta: 0.86 },
  rnOn: 2.92,
  skiaOff: 2.98,
  radiusBack: [2.92, 3.16] as const,
  labelIn: [2.96, 3.2] as const,
} as const;

export const CONTRAIL = {
  samples: 36,
  length: 230, // pt of path behind the dart
  headGap: 0.85, // × the dart's half-length (scaled): the trail starts at its tail
  widthHead: 1.4,
  widthTail: 11,
  wobble: 3.2, // pt, grows toward the tail
  opacityHead: 0.38,
  fadeIn: 0.15,
  fadeOut: 0.45,
  blur: 1.6,
} as const;

/** Light for the paper: from the upper left, in front. Shadow falls down-right on the card. */
export const LIGHT = {
  dir: [-0.35, -0.55, 0.76] as const,
  dark: 0.7, // black overlay per unit of lost light
  bright: 0.5, // white overlay per unit of extra light
  shadowDir: [0.16, 0.28] as const, // shadow offset per pt of height
  shadowOpacity: 0.13,
  shadowBlur: 2.5,
  shadowBlurPerPt: 0.05,
} as const;

/** End of the invite clock (s from the press): contrail gone, button re-formed. */
export const INVITE_END = INVITE.release + FOLD.launch + FLIGHT.duration + CONTRAIL.fadeOut + 0.05;

/* ---------- check contacts: press, fill, goo ---------- */

export const SCAN = {
  pressIn: 0.12,
  release: 0.27,
  fillStart: 0.3,
  fill: 1.45, // linear: a progress bar; its end is the "match" beat
  fadeDur: 0.25,
  end: 2.8,
  pressScale: 0.97,
  fillOpacity: 0.18,
} as const;

/** Times are seconds after the press of "check contacts". */
export const GOO = {
  skiaOn: 0.27,
  rnOff: 0.31,
  /** Faces sink into the liquid (order: green, purple, pink, grey). */
  sink: { start: 0.3, stagger: 0.04, dur: 0.24, scale: 0.5 },
  /** Union of circles (0) → metaball sum (1). */
  gooIn: [0.32, 0.7] as const,
  /** The avatars are pulled toward the cluster's centre on a soft spring (liquid jiggle). */
  pull: { start: 0.36, amount: 0.7, omega: 9, zeta: 0.55, shrink: 0.1 },
  /** Merged-blob wobble while it is together. */
  wobble: { amp: 3.4, freq: 2.6, on: [0.55, 0.85] as const, off: [1.7, 1.9] as const },
  /** Contact droplets: side (−1 left, 1 right), y offset from the blob centre, radius, colour, start. */
  drops: [
    { side: -1, dy: -22, r: 9, color: '#FFD59A', start: 0.62 },
    { side: 1, dy: 18, r: 11, color: '#A9D8FF', start: 0.74 },
    { side: -1, dy: 26, r: 8, color: '#ADEEA9', start: 0.86 },
    { side: 1, dy: -28, r: 7, color: '#F298B9', start: 0.98 },
    { side: -1, dy: 4, r: 10, color: '#CCC0FB', start: 1.1 },
  ],
  /**
   * Each droplet carries a contact's face, clipped to the droplet (the four heads, mirrored so a
   * droplet never repeats a resting avatar on its own colour). The face scales with the droplet and
   * fades over this part of its (eased) path, as the droplet necks into the blob.
   */
  dropFaces: {
    heads: ['h1', 'h3', 'h2', 'h5', 'h1'] as const,
    mirror: [true, true, true, true, false] as const,
    fade: [0.62, 0.86] as const,
  },
  dropDur: 0.56,
  dropArc: 24, // pt of sideways bow on the way in
  dropAbsorb: 0.8, // from this part of its path the droplet shrinks into the blob
  dropOffscreen: 24,
  /** Each absorbed droplet sends a swell through the blob. */
  absorbKick: { amp: 0.07, period: 0.3, decay: 0.16, fadeFrom: 0.45, end: 0.6 },
  /** The blob splits back into the avatars: an underdamped spring through their rest places. */
  split: { start: 1.8, omega: 11, zeta: 0.5 },
  gooOut: [1.86, 2.2] as const,
  faceIn: { start: 1.92, stagger: 0.05, dur: 0.2, from: 0.6, omega: 14, zeta: 0.6 },
  rnOn: 2.68,
  skiaOff: 2.74,
  /** Shader band: avatar band ± this (pt). */
  bandPad: 60,
  /** Order in which faces sink / return, by avatar index (pink, green, purple, grey). */
  rank: [2, 0, 1, 3],
} as const;

/* ---------- the cup ---------- */

export const CUP = {
  end: 1.6,
  /** Sway: angle = amp · e^(−τ/decay) · sin(2π τ / period); the cup leans away from the plane. */
  amp: -7, // deg
  shiftX: -2.5, // pt, the same curve slightly delayed: the cup is pushed, then rocks
  shiftLag: 0.05,
  period: 0.55,
  decay: 0.35,
  fadeFrom: 1.25, // window → exactly 0 at `end`
  /** A tap on the cup: a smaller sway; a new tap is taken only after the first swing. */
  tapAmp: 5,
  tapRearm: 0.45,
  /** The cup's centre (pin pt): the gust starts when the dart passes closest to it. */
  centre: [200, 209] as const,
} as const;

/** Reduced motion: no loop, no travel; taps answer with opacity only (≤ 200 ms). */
export const REDUCED = {
  planeDip: 0.35, // the slot plane dips to this opacity and back
  inviteEnd: 0.4,
  scanEnd: 0.4,
} as const;

/* ---------- the flight path, sampled once by arc length ---------- */

function buildPath() {
  const P = FLIGHT.points;
  const n = P.length;
  const at = (i: number) => {
    if (i < 0) return [2 * P[0][0] - P[1][0], 2 * P[0][1] - P[1][1]];
    if (i >= n) return [2 * P[n - 1][0] - P[n - 2][0], 2 * P[n - 1][1] - P[n - 2][1]];
    return P[i] as readonly number[];
  };
  const xs: number[] = [];
  const ys: number[] = [];
  for (let i = 0; i < n - 1; i++) {
    const p0 = at(i - 1);
    const p1 = at(i);
    const p2 = at(i + 1);
    const p3 = at(i + 2);
    for (let k = 0; k < FLIGHT.samplesPerSegment; k++) {
      const u = k / FLIGHT.samplesPerSegment;
      const u2 = u * u;
      const u3 = u2 * u;
      for (let c = 0; c < 2; c++) {
        const v =
          0.5 *
          (2 * p1[c] +
            (-p0[c] + p2[c]) * u +
            (2 * p0[c] - 5 * p1[c] + 4 * p2[c] - p3[c]) * u2 +
            (-p0[c] + 3 * p1[c] - 3 * p2[c] + p3[c]) * u3);
        (c === 0 ? xs : ys).push(v);
      }
    }
  }
  xs.push(P[n - 1][0]);
  ys.push(P[n - 1][1]);
  const m = xs.length;
  const ss = [0];
  for (let i = 1; i < m; i++) ss.push(ss[i - 1] + Math.hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]));
  // Unwrapped heading (rad) per sample, then bank from the smoothed curvature.
  const hs: number[] = [];
  for (let i = 0; i < m; i++) {
    const a = Math.max(0, i - 1);
    const b = Math.min(m - 1, i + 1);
    let h = Math.atan2(ys[b] - ys[a], xs[b] - xs[a]);
    if (i > 0) {
      while (h - hs[i - 1] > Math.PI) h -= 2 * Math.PI;
      while (h - hs[i - 1] < -Math.PI) h += 2 * Math.PI;
    }
    hs.push(h);
  }
  const raw: number[] = [];
  for (let i = 0; i < m; i++) {
    const a = Math.max(0, i - 2);
    const b = Math.min(m - 1, i + 2);
    const ds = ss[b] - ss[a];
    raw.push(ds > 0 ? (hs[b] - hs[a]) / ds : 0);
  }
  const bank: number[] = [];
  const R = 8; // box smoothing over ±8 samples
  for (let i = 0; i < m; i++) {
    let acc = 0;
    let cnt = 0;
    for (let j = Math.max(0, i - R); j <= Math.min(m - 1, i + R); j++) {
      acc += raw[j];
      cnt++;
    }
    const k = (acc / cnt) * FLIGHT.bankGain;
    bank.push(Math.max(-FLIGHT.bankMax, Math.min(FLIGHT.bankMax, k)));
  }
  // The dart starts flat: bank eases in over the first samples anyway (FLIGHT.bankIn).
  return { xs, ys, ss, hs, bank, length: ss[m - 1] };
}

export const PATH = buildPath();

/** Position (pin pt), unwrapped heading (rad) and bank (rad) at arc distance s. */
export function pathAt(xs: number[], ys: number[], ss: number[], hs: number[], bank: number[], s: number) {
  'worklet';
  const n = ss.length;
  if (s <= 0) s = 0;
  if (s >= ss[n - 1]) s = ss[n - 1];
  let lo = 0;
  let hi = n - 1;
  while (hi - lo > 1) {
    const mid = (lo + hi) >> 1;
    if (ss[mid] <= s) lo = mid;
    else hi = mid;
  }
  const f = ss[hi] === ss[lo] ? 0 : (s - ss[lo]) / (ss[hi] - ss[lo]);
  return {
    x: xs[lo] + (xs[hi] - xs[lo]) * f,
    y: ys[lo] + (ys[hi] - ys[lo]) * f,
    h: hs[lo] + (hs[hi] - hs[lo]) * f,
    b: bank[lo] + (bank[hi] - bank[lo]) * f,
  };
}

/** Flight time (s after launch) at which the dart passes closest to the cup: the gust. */
function gustTime() {
  let best = 0;
  let bestD = Infinity;
  for (let i = 0; i < PATH.ss.length; i++) {
    const d = Math.hypot(PATH.xs[i] - CUP.centre[0], PATH.ys[i] - CUP.centre[1]);
    if (d < bestD) {
      bestD = d;
      best = PATH.ss[i] / PATH.length;
    }
  }
  let lo = 0;
  let hi = 1;
  for (let i = 0; i < 40; i++) {
    const mid = (lo + hi) / 2;
    if (ease(EASE_HANG, mid) < best) lo = mid;
    else hi = mid;
  }
  return lo * FLIGHT.duration;
}

/** The plane passes the cup this long after the press of "invite them". */
export const GUST = INVITE.release + FOLD.launch + gustTime();

/** Where each effect starts on the loop (s). */
/**
 * GPU warm-up: for the first few frames after mount the overlay draws one pose of the paper (t = 2.5 s
 * after the release: the dart in flight with its shadow and contrail while the new sheet unfolds) and
 * one of the contacts (σ = 1.0: the blob with droplets carrying faces) at an alpha of 1/255, so every
 * shader, blur layer and image texture is compiled and uploaded before the loop needs it. Without it
 * the first loop stalled 27–82 ms at each first use (Expo Go, iOS simulator).
 */
export const WARM = { frames: 3, alpha: 1 / 255, inv: 0.27 + 2.5, scan: 1.0 } as const;

export const START = {
  invite: 0.5,
  cup: 0.5 + GUST,
  scan: 4.1,
} as const;
