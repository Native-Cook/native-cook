/**
 * For You Insights — every motion value, as pure functions of time.
 *
 * The autoPlay loop is `pose(t)` for t in [0, LOOP): t = 0 and t = LOOP are the pin's rest frame.
 * Touch drives the same functions (stream, open/close geometry, layer parallax, drift), so the
 * Swift and Kotlin ports evaluate the same closed forms value for value.
 */

export const LOOP = 9;
/** Seconds after the last touch before the reader closes and the loop restarts at rest. */
export const RESUME_AFTER = 2;

/** Loop timeline, seconds. */
export const T = {
  dimStart: 0.5,
  dimEnd: 0.8,
  streamStart: 0.8,
  streamEnd: 2.3,
  caretIn: 0.08,
  caretOutStart: 2.3,
  caretOutEnd: 2.6,
  shimmerStart: 2.25,
  shimmerEnd: 2.9,
  pressStart: 2.95,
  pressDuration: 0.12,
  openStart: 3.07,
  openDuration: 0.85,
  closeStart: 6.35,
  closeDuration: 0.7,
} as const;

/** Closed-form springs: response (s) and damping ratio, Apple's two designer parameters. */
export const SPRING = {
  open: { response: 0.5, damping: 0.82 },
  close: { response: 0.42, damping: 1 },
  /** Reanimated equivalents for touch (duration in ms ≈ settle time). */
  touchOpen: { duration: 650, dampingRatio: 0.82 },
  touchClose: { duration: 560, dampingRatio: 1 },
  tiltBack: { duration: 500, dampingRatio: 0.7 },
  dragBack: { duration: 420, dampingRatio: 0.8 },
  lens: { duration: 460, dampingRatio: 0.72 },
} as const;

export const PRESS_SCALE = 0.97;
export const PRESS_IN_MS = 120;

/** The tip regenerates: the old text dims to a ghost, then streams back in chunks. */
export const STREAM = {
  ghost: 0.16,
  tokens: 18,
  /** Share of each token spent moving (the rest holds), so the head advances in chunks. */
  tokenMove: 0.72,
  /** Length of the tinted head (pt): ink → pink → violet at the writing edge. */
  head: 56,
  caretWidth: 2.5,
  caretInset: 4,
  shimmerWidth: 140,
  shimmerSkew: 18,
} as const;

/** Depth reader: lift, tilt and layer parallax. Angles in degrees. */
export const DEPTH = {
  perspective: 900,
  liftScale: 0.05,
  flightTiltX: 9,
  flightTiltY: -11,
  driftPeriod: 2.6,
  driftTiltY: 7,
  driftTiltX: 4,
  driftRampIn: 0.55,
  driftRampOut: 0.6,
  /** Layer shift per unit depth at a full tilt, as a share of the card width. */
  parallax: 0.05,
  /** Extra scale per unit depth when the layers separate. */
  depthScale: 0.03,
  /** Upward lift per unit depth at full separation (pt). */
  depthLift: 5,
  /** The stack is drawn this much larger in the reader so a shifted layer never shows an edge. */
  overscan: 0.1,
  heroShadowLevel: 0.35,
  shadowOpacity: 0.26,
  shadowOffsetMax: 20,
  sheenOpacity: 0.22,
  touchTiltY: 12,
  touchTiltX: 9,
  touchTiltRange: 140,
} as const;

/** Depth of each layer, back to front (negative = behind the card plane). */
export const LAYER_DEPTHS = {
  morning: [-1, -0.1, 1],
  food: [-1, 0.25, 1],
  run: [-1, -0.15, 1],
} as const;

/** Reader choreography as sub-ranges of the open progress g. */
export const READER = {
  backdrop: [0.08, 0.7],
  feedScale: 0.94,
  rowTitleOut: [0, 0.3],
  rowTitleRise: 10,
  titleIn: [0.42, 0.95],
  titleRise: 28,
  /** The reader title grows from this scale as it lands (a blur here cost a frame per frame on iOS). */
  titleScaleFrom: 0.96,
  /** Body block i fades in over [bodyIn0 + i·stagger, + bodyLength]; the last one ends by g = 0.98. */
  bodyIn: [0.45, 0.73],
  bodyStagger: 0.05,
  bodyRise: 22,
  closeIn: [0.7, 1],
  closeScaleFrom: 0.86,
  tabOut: [0, 0.55],
  tabDrop: 120,
  /** Past this g the bar is off screen: opacity 0 so the glass stops re-rendering. */
  tabHidden: 0.6,
  /** The row's own image stays visible until the actor has drawn a few frames over it. */
  handOff: 0.03,
} as const;

/** Pull-down on the hero: rubber band, then dismiss by distance or by flick. */
export const DISMISS = {
  resistance: 0.55,
  softness: 420,
  distance: 120,
  velocity: 900,
  scaleLoss: 0.12,
} as const;

// ——— helpers (all worklets so the UI runtime can call them) ———

export function clamp01(v: number): number {
  'worklet';
  return Math.min(1, Math.max(0, v));
}

export function seg(t: number, a: number, b: number): number {
  'worklet';
  return clamp01((t - a) / Math.max(0.0001, b - a));
}

export function lerp(a: number, b: number, p: number): number {
  'worklet';
  return a + (b - a) * p;
}

export function smooth(p: number): number {
  'worklet';
  const x = clamp01(p);
  return x * x * (3 - 2 * x);
}

function bez(t: number, p1: number, p2: number): number {
  'worklet';
  const u = 1 - t;
  return 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t;
}

function cubic(p: number, x1: number, y1: number, x2: number, y2: number): number {
  'worklet';
  const x = clamp01(p);
  if (x === 0 || x === 1) return x;
  let lo = 0;
  let hi = 1;
  for (let i = 0; i < 20; i += 1) {
    const mid = (lo + hi) / 2;
    if (bez(mid, x1, x2) < x) lo = mid;
    else hi = mid;
  }
  return bez((lo + hi) / 2, y1, y2);
}

/** Strong ease-out for UI entering: cubic-bezier(0.23, 1, 0.32, 1). */
export function easeOut(p: number): number {
  'worklet';
  return cubic(p, 0.23, 1, 0.32, 1);
}

/** On-screen movement: cubic-bezier(0.77, 0, 0.175, 1). */
export function easeInOut(p: number): number {
  'worklet';
  return cubic(p, 0.77, 0, 0.175, 1);
}

/** Closed-form spring from 0 to 1 after `tau` seconds. */
export function spring(tau: number, s: { response: number; damping: number }): number {
  'worklet';
  if (tau <= 0) return 0;
  const w = (2 * Math.PI) / s.response;
  const z = s.damping;
  if (z >= 1) return 1 - Math.exp(-w * tau) * (1 + w * tau);
  const wd = w * Math.sqrt(1 - z * z);
  return 1 - Math.exp(-z * w * tau) * (Math.cos(wd * tau) + ((z * w) / wd) * Math.sin(wd * tau));
}

// ——— the tip stream ———

/** Stream progress 0…1 for `u` = share of the stream window elapsed, advancing token by token. */
export function streamProgress(u: number): number {
  'worklet';
  const x = clamp01(u);
  if (x >= 1) return 1;
  const n = STREAM.tokens;
  const k = Math.floor(x * n);
  const f = x * n - k;
  return (k + smooth(f / STREAM.tokenMove)) / n;
}

/**
 * Head position (pt from the line's left) of each line. Lines start when the previous head
 * reaches its line's end, so the tinted tail of one line fades out while the next begins.
 * `widths` are the measured line widths. Past the end every head sits at width + head (all ink).
 */
export function streamHeads(progress: number, widths: readonly number[]): number[] {
  'worklet';
  let total = STREAM.head;
  for (let i = 0; i < widths.length; i += 1) total += widths[i];
  const at = clamp01(progress) * total;
  const heads: number[] = [];
  let start = 0;
  for (let i = 0; i < widths.length; i += 1) {
    heads.push(Math.min(widths[i] + STREAM.head, Math.max(0, at - start)));
    start += widths[i];
  }
  return heads;
}

/** Tip pose for a stream that began `elapsed` s ago (the dim starts at elapsed 0). */
export function tipPose(elapsed: number) {
  'worklet';
  const dimLen = T.dimEnd - T.dimStart;
  const streamLen = T.streamEnd - T.streamStart;
  const e = elapsed;
  if (e < 0 || e >= T.shimmerEnd - T.dimStart) {
    return { ghost: 1, stream: 1, caret: 0, shimmer: -1, active: false };
  }
  const streaming = e >= dimLen;
  const ghost = e >= T.streamEnd - T.dimStart ? 1 : lerp(1, STREAM.ghost, easeInOut(e / dimLen));
  const stream = streaming ? streamProgress((e - dimLen) / streamLen) : 0;
  const caretIn = seg(e, dimLen - T.caretIn, dimLen);
  const caretOut = 1 - seg(e, T.caretOutStart - T.dimStart, T.caretOutEnd - T.dimStart);
  const shimmerRaw = seg(e, T.shimmerStart - T.dimStart, T.shimmerEnd - T.dimStart);
  return {
    ghost,
    stream: streaming ? stream : 0,
    caret: smooth(caretIn) * smooth(caretOut),
    shimmer: shimmerRaw > 0 && shimmerRaw < 1 ? easeInOut(shimmerRaw) : -1,
    active: true,
  };
}

// ——— the depth reader ———

/** Open progress g (0 row … 1 reader) for the loop, with the press before it. */
export function openPose(t: number) {
  'worklet';
  const press = t >= T.pressStart && t < T.openStart
    ? easeOut(seg(t, T.pressStart, T.pressStart + T.pressDuration))
    : 0;
  let g = 0;
  let opening = true;
  if (t >= T.closeStart + T.closeDuration) g = 0;
  else if (t >= T.closeStart) {
    g = 1 - spring(t - T.closeStart, SPRING.close);
    opening = false;
  } else if (t >= T.openStart + T.openDuration) g = 1;
  else if (t >= T.openStart) g = spring(t - T.openStart, SPRING.open);
  const holdStart = T.openStart + T.openDuration * 0.55;
  const hold = T.closeStart - holdStart;
  const tau = t - holdStart;
  const drift = tau > 0 && tau < hold
    ? smooth(tau / DEPTH.driftRampIn) * (1 - smooth((tau - (hold - DEPTH.driftRampOut)) / DEPTH.driftRampOut))
    : 0;
  return { press, g: Math.max(0, g), opening, driftTime: Math.max(0, tau), drift };
}

/** Lift above the page during the flight: 0 at the row, a peak mid-flight, a resting level in the reader. */
export function liftOf(g: number): number {
  'worklet';
  const x = clamp01(g);
  return Math.sin(Math.PI * Math.min(1, x)) * (1 - DEPTH.heroShadowLevel) + x * DEPTH.heroShadowLevel;
}

/** Card tilt (degrees) from the flight plus the reader's slow orbit (a Lissajous figure). */
export function tiltOf(g: number, driftTime: number, drift: number) {
  'worklet';
  const flight = Math.sin(Math.PI * clamp01(g));
  const phase = (2 * Math.PI * driftTime) / DEPTH.driftPeriod;
  return {
    x: flight * DEPTH.flightTiltX + Math.sin(phase * 2) * DEPTH.driftTiltX * drift,
    y: flight * DEPTH.flightTiltY + Math.sin(phase) * DEPTH.driftTiltY * drift,
  };
}

/** How far the layers have separated (0 = one flat picture, the row's). */
export function separationOf(g: number): number {
  'worklet';
  return smooth(seg(g, 0.12, 0.85));
}

/** Shift and scale of one layer, given its depth, the separation and the tilt. */
export function layerShift(depth: number, sep: number, tiltX: number, tiltY: number, width: number) {
  'worklet';
  const ny = tiltY / DEPTH.driftTiltY;
  const nx = tiltX / DEPTH.driftTiltX;
  return {
    dx: -depth * ny * width * DEPTH.parallax * 0.5 * sep,
    dy: depth * nx * width * DEPTH.parallax * 0.35 * sep - depth * DEPTH.depthLift * sep,
    scale: 1 + depth * DEPTH.depthScale * sep,
  };
}

/** Rubber-banded pull (pt) for a finger travel of `dy` pt. */
export function rubber(dy: number): number {
  'worklet';
  const d = Math.max(0, dy);
  return (d * DISMISS.resistance) / (1 + d / DISMISS.softness);
}

/** One frame of the loop. */
export function pose(t: number) {
  'worklet';
  const time = ((t % LOOP) + LOOP) % LOOP;
  return { tip: tipPose(time - T.dimStart), open: openPose(time) };
}
