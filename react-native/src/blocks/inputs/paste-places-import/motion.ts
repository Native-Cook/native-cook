/**
 * Paste Places Import — the timeline. Every value on screen is a pure function of the loop time
 * `t` (seconds, 0 ≤ t < LOOP): nothing is accumulated between frames, so t = LOOP equals t = 0.
 *
 *   0.00–0.80  rest = the pin (empty text area, start import disabled)
 *   0.80–0.90  "paste" is pressed (scripted press in autoPlay)
 *   0.90–1.95  the app icons fan apart and settle; the note streams in; the counter rolls; start import fills
 *   2.20–2.33  start import is pressed
 *   2.40–3.30  a scan line sweeps the note: the city is underlined (and the tip with it), each place gets a
 *              marker stroke and lifts off the paper as a chip that hovers over its word
 *   3.50–4.60  HERO: the text area folds shut like an accordion (four panels, perspective, light on the
 *              panels) under the hovering chips and unfolds again as a small paper map of the city
 *   4.02–5.30  each chip flies to its place on the map and drops as a pin (gravity, two bounces, squash,
 *              contact shadow, a dot on the map); the button reads "found N places", N rolling per landing.
 *              The first chip lands at 4.57 (FLY.start + dropAfter + DROP.fall), while the paper's last flex
 *              is pinned flat (4.44–4.60), so shadows, dots and route (drawn only under 3°) are already on
 *   5.00–5.70  a route draws through the five places
 *   5.70–7.20  hold = found
 *   7.20–8.72  × : the route retracts, the pins pop off, the map folds shut and unfolds as the empty text area
 *   8.72–10.0  rest
 */

export const LOOP = 10;

/** Stable states a tap can stop at (loop times). REST is also LOOP. */
export const FILLED_AT = 2.05;
export const FOUND_AT = 5.75;
export const RESET_AT = 7.32;
export const REST_AGAIN = 8.72;
/** A tap on paste / start / × jumps to these times (the press itself is the finger's). */
export const PASTE_AT = 0.9;
export const START_AT = 2.4;
/** After the last touch, autoPlay waits this long and carries on from where it is. */
export const RESUME_AFTER = 2000;
/** Reduced motion: state changes are a fade out and in of this many ms each. */
export const REDUCED_FADE = 100;

/** Scripted presses (autoPlay only): [down at, up at]. */
export const PRESSES = {
  paste: [0.8, 0.9],
  start: [2.2, 2.33],
  close: [7.2, 7.32],
} as const;
export const PRESS = {
  down: 0.1,
  up: 0.14,
  scale: 0.97,
  closeScale: 0.86,
  closeDim: 0.45,
  inMs: 110,
  outMs: 160,
} as const;

/** Icons fan apart when the note is pasted (out, then back). Degrees and pt at 402 pt wide. */
export const FAN = {
  out: [0.9, 1.22],
  back: [1.3, 1.8],
  notes: { rot: -8, x: -9, y: -3 },
  doc: { rot: 8, x: 9, y: -2 },
  chat: { rot: 0, x: 0, y: -6, scale: 1.04 },
} as const;

export const NOTE_IN = {
  placeholderOut: [0.92, 1.04],
  start: 0.98,
  stagger: 0.07,
  dur: 0.34,
  rise: 8, // pt
} as const;

export const COUNT_IN = { start: 1.05, dur: 0.8, stagger: 0.08 } as const;
export const COUNT_OUT = { start: 7.72, dur: 0.5, stagger: 0.05 } as const;

/** The start button fills left to right; on reset it empties right to left. */
export const FILL = { in: [1.5, 1.95], out: [7.5, 7.9] } as const;

/** Scan line: from above the first line to below the last one, constant speed. */
export const SCAN = { start: 2.4, dur: 0.9, fade: 0.1 } as const;

/** Per detection, relative to the moment the scan line passes the word's line. */
export const DETECT = {
  highlight: 0.22, // marker stroke draws left → right
  lift: 0.12, // the chip leaves the paper this long after the scan passes
  wordDim: 0.5, // the place's word once its chip has left (still readable)
  cityLine: 0.25,
  tipLine: [0.05, 0.42],
} as const;

/** The chip hovers over its word: rise (pt) and grow, closed-form spring. */
export const HOVER = { rise: 8, grow: 0.04, pill: 0.14, spring: { omega: 13, zeta: 0.72 } } as const;

/**
 * The fold. `close` folds the text area shut (ease-in-out to `maxDeg`), `open` unfolds it with a spring that
 * overshoots past flat (the paper flexes) and is pinned to exactly flat over the last `settle` s.
 * The note face shows until the fold is shut, the map face after; the reset does the same back.
 */
export const FOLD = {
  panels: 4,
  maxDeg: 84,
  perspective: 640, // pt at 402 pt wide
  close: [3.5, 3.88],
  open: [3.88, 4.6],
  reclose: [7.72, 8.08],
  reopen: [8.08, 8.72],
  settle: 0.16,
  spring: { omega: 11, zeta: 0.55 },
  /** Panel light: a panel facing down darkens by up to `down`, one facing up by `up`. */
  shade: { down: 0.28, up: 0.06 },
  /** Even panels turn their bottom edge towards the viewer (+1) or away (−1). */
  dir: 1,
  /** The paper's shadow on the page while it is folded: offset (pt), blur (pt), opacity at full fold. */
  shadow: { y: 6, blur: 10, opacity: 0.16 },
  /** Room around the text area for the folding paper (perspective widens near edges). */
  margin: 28,
} as const;

/** Chips fly from their words to the map (spring + arc), then drop the last `DROP.height` pt as pins. */
export const FLY = { start: 4.02, stagger: 0.09, spring: { omega: 9.5, zeta: 0.78 }, arc: 34, grow: 0.06, tilt: 4, dropAfter: 0.38 } as const;
/** Gravity fall of `fall` s, then bounces of the given heights (fractions of the fall), squash on each impact. */
export const DROP = { height: 24, fall: 0.17, bounces: [0.12, 0.03], squash: { x: 0.07, y: 0.12, decay: 0.06 } } as const;
/** Route line draws after the last landing; retracts first on reset. */
export const ROUTE = { draw: [5.0, 5.7], retract: [7.32, 7.56] } as const;
/** Reset: each pin pops off its spot. */
export const POP = { start: 7.34, stagger: 0.04, dur: 0.2, rise: 6, shrink: 0.2 } as const;
/** Map dot and contact shadow. */
export const DOT = { in: 0.2, shadowAir: 0.05, shadowGround: 0.22, shadowRest: 0.14 } as const;

/** Button label "start import" → "found N places" at the first landing. */
export const LABEL = { out: 0.12, gap: 0.06, in: 0.18, shift: 6, backAt: 7.36 } as const;
export const COUNT_ROLL = 0.24;

export const RESET = { tipLine: [7.4, 7.6] } as const;

/** Stretches where nothing moves (the clock skips publishing there, so nothing redraws during the holds). */
export const QUIET: readonly (readonly [number, number])[] = [
  [0, 0.79],
  [5.72, 7.19],
  [8.74, LOOP],
];

export function quietAt(t: number) {
  'worklet';
  for (let i = 0; i < QUIET.length; i++) if (t >= QUIET[i][0] && t <= QUIET[i][1]) return i;
  return -1;
}

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function lerp(a: number, b: number, p: number) {
  'worklet';
  return a + (b - a) * p;
}

/** Solve the curve's x before sampling y; shared by all native ports. */
function bezier(p: number, x1: number, y1: number, x2: number, y2: number) {
  'worklet';
  const x = clamp01(p);
  if (x === 0 || x === 1) return x;
  let lo = 0;
  let hi = 1;
  for (let i = 0; i < 18; i++) {
    const m = (lo + hi) / 2;
    const u = 1 - m;
    const at = 3 * u * u * m * x1 + 3 * u * m * m * x2 + m * m * m;
    if (at < x) lo = m;
    else hi = m;
  }
  const m = (lo + hi) / 2;
  const u = 1 - m;
  return 3 * u * u * m * y1 + 3 * u * m * m * y2 + m * m * m;
}

/** Strong ease-out: cubic-bezier(0.23, 1, 0.32, 1). */
export function easeOut(p: number) {
  'worklet';
  return bezier(p, 0.23, 1, 0.32, 1);
}

/** On-screen movement: cubic-bezier(0.77, 0, 0.175, 1). */
export function easeInOut(p: number) {
  'worklet';
  return bezier(p, 0.77, 0, 0.175, 1);
}

/** A damped spring from 0 to 1, `tau` seconds after it starts (closed form, so ports match value for value). */
export function spring(tau: number, s: { omega: number; zeta: number }) {
  'worklet';
  if (tau <= 0) return 0;
  const { omega, zeta } = s;
  const wd = omega * Math.sqrt(1 - zeta * zeta);
  const env = Math.exp(-zeta * omega * tau);
  const v = 1 - env * (Math.cos(wd * tau) + ((zeta * omega) / wd) * Math.sin(wd * tau));
  return env < 1e-4 ? 1 : v;
}

/** Press amount 0..1 for a scripted press [down, up]. */
export function pressAt(t: number, at: readonly [number, number]) {
  'worklet';
  if (t < at[0] || t > at[1] + PRESS.up) return 0;
  if (t < at[1]) return easeOut(seg(t, at[0], at[0] + PRESS.down));
  return 1 - easeOut(seg(t, at[1], at[1] + PRESS.up));
}

export function fanAt(t: number) {
  'worklet';
  return easeOut(seg(t, FAN.out[0], FAN.out[1])) - easeInOut(seg(t, FAN.back[0], FAN.back[1]));
}

/** Note line i: 0 hidden … 1 in place. The note leaves folded inside the paper, so after the reset it is 0. */
export function noteLineAt(t: number, i: number) {
  'worklet';
  if (t >= RESET_AT) return 0;
  return easeOut(seg(t, NOTE_IN.start + i * NOTE_IN.stagger, NOTE_IN.start + i * NOTE_IN.stagger + NOTE_IN.dur));
}

export function placeholderAt(t: number) {
  'worklet';
  if (t >= RESET_AT) return 1;
  return 1 - seg(t, NOTE_IN.placeholderOut[0], NOTE_IN.placeholderOut[1]);
}

/** Counter master 0..1 (0 = "0", 1 = the note's length). */
export function countAt(t: number) {
  'worklet';
  if (t < COUNT_OUT.start) return seg(t, COUNT_IN.start, COUNT_IN.start + COUNT_IN.dur);
  return 1 - seg(t, COUNT_OUT.start, COUNT_OUT.start + COUNT_OUT.dur);
}

/** Slot k of n (0 = leftmost) of a roller driven by master m over `span` seconds: 0..1 eased. */
export function slotAt(m: number, k: number, n: number, span: number, stagger: number) {
  'worklet';
  const fromRight = n - 1 - k;
  const d = 1 - ((n - 1) * stagger) / span;
  return easeOut(clamp01((m - (fromRight * stagger) / span) / d));
}

export function fillAt(t: number) {
  'worklet';
  if (t < FILL.out[0]) return easeInOut(seg(t, FILL.in[0], FILL.in[1]));
  return 1 - easeInOut(seg(t, FILL.out[0], FILL.out[1]));
}

export function scanAt(t: number) {
  'worklet';
  const p = seg(t, SCAN.start, SCAN.start + SCAN.dur);
  const o = Math.min(seg(t, SCAN.start, SCAN.start + SCAN.fade), 1 - seg(t, SCAN.start + SCAN.dur - SCAN.fade, SCAN.start + SCAN.dur));
  return { p, o };
}

export function passAt(f: number) {
  'worklet';
  return SCAN.start + f * SCAN.dur;
}

export function highlightAt(t: number, pass: number) {
  'worklet';
  return easeOut(seg(t, pass, pass + DETECT.highlight));
}

export function cityLineAt(t: number, cityPass: number) {
  'worklet';
  return easeOut(seg(t, cityPass, cityPass + DETECT.cityLine));
}

export function tipLineAt(t: number, cityPass: number) {
  'worklet';
  if (t >= RESET.tipLine[0]) return 1 - easeOut(seg(t, RESET.tipLine[0], RESET.tipLine[1]));
  return easeOut(seg(t, cityPass + DETECT.tipLine[0], cityPass + DETECT.tipLine[1]));
}

/** The place's word in the note: dims once its chip has lifted off. */
export function wordAt(t: number, pass: number) {
  'worklet';
  const l = pass + DETECT.lift;
  return lerp(1, DETECT.wordDim, seg(t, l, l + 0.12));
}

/* ───────────── fold ───────────── */

function foldPhase(t: number, close: readonly [number, number], open: readonly [number, number]) {
  'worklet';
  if (t < close[0] || t >= open[1]) return 0;
  if (t < close[1]) return FOLD.maxDeg * easeInOut(seg(t, close[0], close[1]));
  const s = spring(t - open[0], FOLD.spring);
  return FOLD.maxDeg * (1 - s) * (1 - seg(t, open[1] - FOLD.settle, open[1]));
}

/** Fold angle in degrees (0 flat … maxDeg shut; a little negative while the paper flexes after opening). */
export function foldAt(t: number) {
  'worklet';
  return t < 6 ? foldPhase(t, FOLD.close, FOLD.open) : foldPhase(t, FOLD.reclose, FOLD.reopen);
}

/** The paper panels replace the text area from the first fold to the end of the reset. */
export function panelsAt(t: number) {
  'worklet';
  return t >= FOLD.close[0] && t < FOLD.reopen[1];
}

/** The map is printed on the paper from the moment the first fold is shut until the reset fold is shut. */
export function mapFaceAt(t: number) {
  'worklet';
  return t >= FOLD.close[1] && t < FOLD.reclose[1];
}

/**
 * Panel i of n under fold angle `deg`, accordion hinged at the top edge of the text area: its translateY (pt)
 * and rotateX (deg), with the perspective P (pt) applied about the panel's own centre. Even panels turn by
 * +deg, odd by −deg, so shared edges sit at the same depth and stay joined under perspective.
 */
export function panelAt(i: number, deg: number, hp: number, P: number) {
  'worklet';
  const th = (deg * Math.PI) / 180;
  const sn = Math.sin(th) * FOLD.dir;
  const cs = Math.cos(th);
  const sNear = P / (P - (hp / 2) * sn); // edges coming towards the viewer
  const sFar = P / (P + (hp / 2) * sn);
  let c = (hp / 2) * cs * sFar;
  for (let j = 0; j < i; j++) c += hp * cs * (j % 2 === 0 ? sNear : sFar);
  const sign = i % 2 === 0 ? 1 : -1;
  const facing = sign * sn; // > 0: the panel faces down (darker)
  // Squared, so the light fades out well before the paper is flat and the spring's last flex leaves no banding.
  const f2 = facing * facing;
  return { ty: c - (i + 0.5) * hp, rot: sign * deg, shade: facing > 0 ? f2 * FOLD.shade.down : f2 * FOLD.shade.up };
}

/**
 * The 3×3 projective matrix (Skia row-major: scaleX, skewX, transX, skewY, scaleY, transY, persp0, persp1, persp2)
 * that draws panel i: content x and y_l (y measured from the panel's centre) land on the screen as the panel turned
 * by its angle about its centre line under perspective P, centred at (cx, cy). Ports reproduce it as a 3D transform.
 */
export function panelMatrix(i: number, deg: number, cx: number, cy: number, w: number, P: number) {
  'worklet';
  const th = (deg * Math.PI) / 180;
  const k = ((i % 2 === 0 ? 1 : -1) * Math.sin(th) * FOLD.dir) / P;
  return [1, -cx * k, cx - w / 2, 0, Math.cos(th) - cy * k, cy, 0, -k, 1];
}

/* ───────────── chips → pins ───────────── */

export function hoverAt(t: number, pass: number) {
  'worklet';
  return spring(t - pass - DETECT.lift, HOVER.spring);
}

export function chipPillAt(t: number, pass: number) {
  'worklet';
  const l = pass + DETECT.lift;
  return seg(t, l, l + HOVER.pill);
}

const flyStart = (i: number) => {
  'worklet';
  return FLY.start + i * FLY.stagger;
};

export function flyAt(t: number, i: number) {
  'worklet';
  return spring(t - flyStart(i), FLY.spring);
}

/** Time chip i first touches the map. */
export function impactAt(i: number) {
  'worklet';
  return flyStart(i) + FLY.dropAfter + DROP.fall;
}

/** Drop of chip i: height h (1 = DROP.height above the map, 0 on it) and the squash impulse of the impacts. */
export function dropAt(t: number, i: number) {
  'worklet';
  const tau = t - flyStart(i) - FLY.dropAfter;
  if (tau <= 0) return { h: 1, imp: 0 };
  const T = DROP.fall;
  let h = 0;
  let at = T; // time of the next impact
  let imp = 0;
  if (tau < T) h = 1 - (tau / T) * (tau / T);
  else imp += Math.exp(-(tau - T) / DROP.squash.decay);
  for (let b = 0; b < DROP.bounces.length; b++) {
    const peak = DROP.bounces[b];
    const dur = 2 * T * Math.sqrt(peak);
    if (tau >= at && tau < at + dur) {
      const u = (tau - at) / dur;
      h = 4 * peak * u * (1 - u);
    }
    at += dur;
    if (tau >= at) imp += Math.sqrt(peak) * Math.exp(-(tau - at) / DROP.squash.decay);
  }
  return { h, imp };
}

export function popAt(t: number, i: number) {
  'worklet';
  const a = POP.start + i * POP.stagger;
  return easeOut(seg(t, a, a + POP.dur));
}

/** The dot on the map under chip i: 0..1. */
export function dotAt(t: number, i: number) {
  'worklet';
  if (t >= POP.start) return 1 - popAt(t, i);
  const a = impactAt(i);
  return easeOut(seg(t, a, a + DOT.in));
}

/** Contact shadow under chip i: opacity (0 while it is high in the air). */
export function contactAt(t: number, i: number) {
  'worklet';
  const start = flyStart(i) + FLY.dropAfter;
  if (t < start) return 0;
  const { h } = dropAt(t, i);
  const landed = seg(t, impactAt(i) + 0.3, impactAt(i) + 0.6);
  const v = lerp(DOT.shadowAir, DOT.shadowGround, 1 - h);
  return lerp(v, DOT.shadowRest, landed) * (1 - popAt(t, i));
}

/** N of "found N places" (continuous); each landing rolls it on. */
export function foundAt(t: number, n: number) {
  'worklet';
  if (t >= LABEL.backAt) return n;
  let v = 0;
  for (let i = 0; i < n; i++) v += easeOut(seg(t, impactAt(i), impactAt(i) + COUNT_ROLL));
  return v;
}

/** Label morph, sequential: [old label 0..1 gone, new label 0..1 in]; rest [0, 0], found [1, 1]. */
export function labelAt(t: number) {
  'worklet';
  const go = (a: number) => [easeOut(seg(t, a, a + LABEL.out)), easeOut(seg(t, a + LABEL.out + LABEL.gap, a + LABEL.out + LABEL.gap + LABEL.in))];
  if (t >= LABEL.backAt) {
    const [oldOut, newIn] = go(LABEL.backAt);
    return [1 - newIn, 1 - oldOut];
  }
  return go(impactAt(0));
}

export function routeAt(t: number) {
  'worklet';
  if (t < ROUTE.retract[0]) return easeInOut(seg(t, ROUTE.draw[0], ROUTE.draw[1]));
  return 1 - easeInOut(seg(t, ROUTE.retract[0], ROUTE.retract[1]));
}
