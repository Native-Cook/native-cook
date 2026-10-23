/**
 * Vitals Chip Morph — the timeline and the morph. Every value on screen is a pure function of a
 * "blend" (lo, hi, u): the screens lo < hi and the progress u from lo (0) to hi (1), plus the heart's
 * beat. The autoPlay loop maps its loop time t to a blend; a touch drives the same blend with a
 * closed-form spring, so the loop and the finger show the same effect.
 *
 * Screens: 0 Readiness (gauge, the rest frame), 1 Fitness (rings), 2 Heart.
 *
 * The hero is ONE curve of N points that every screen shares, sampled from the bottom clockwise:
 *   Readiness  an open arc (167.5° → 12.5°), stroked 43 pt          t = 0 left end, 0.5 apex, 1 right end
 *   Fitness    the outer ring, a closed circle stroked 29 pt        t = 0 bottom, 0.5 top
 *   Heart      the heart's silhouette, filled, no stroke           t = 0 tip, 0.5 cusp
 * so the arc's two caps slide down and meet to close the ring, the ring fills in to become the heart,
 * and the heart opens at its tip back into the arch. Point i of one shape always becomes point i of
 * the next; no point crosses another.
 *
 * Loop (LOOP = 9.6 s):
 *   0.00–1.50  rest = pin 11a (Readiness)
 *   1.50–1.66  the Fitness chip is pressed (0.97)
 *   1.60–2.90  spring to Fitness: the arc's caps slide down and meet (u 0–0.62, done at 2.13 s), the
 *              progress sweeps on to 375/500 with the dot as its comet head, two inner rings unspool
 *              inward one after the other
 *   2.90–3.90  Fitness holds
 *   3.90–4.25  a finger drags the hero left (u 0 → 0.34, from rest to the release speed)
 *   4.25–5.10  release with the finger's velocity: the ring fills in and becomes the heart
 *   5.10–6.95  Heart holds and beats at 62 BPM (beats at 5.2 and 6.17)
 *   6.95–8.40  idle return to Readiness: the heart opens at its tip into the arch; the dot glides to 7
 *   8.85–9.60  rest (the spring's last 0.2 % is cut at 8.85 s)
 */

import { HEART_OUTLINE } from './heartOutline';
import { colors, layout } from './theme';

export const LOOP = 9.6;
export const N = 180;
/** After the last touch, the screen waits this long, then returns to Readiness and the loop restarts. */
export const RESUME_AFTER = 2;
export const BEAT = 60 / 62;

export const T = {
  pressFitness: 1.5,
  pressEnd: 1.66,
  toFitness: 1.6,
  dragStart: 3.9,
  dragEnd: 4.25,
  dragTo: 0.34,
  firstBeat: 5.2,
  beatsEnd: 6.95,
  returnStart: 6.95,
  restAt: 8.85,
} as const;

/** Springs as natural frequency ω (rad/s) and damping ratio ζ, solved in closed form. */
export const SPRINGS = {
  /** A chip tap: critically damped, ω 4 — the arc has closed (u 0.62) at 0.53 s, 1 % left at 1.66 s. */
  tap: { omega: 4, zeta: 1 },
  /** A released drag: ζ 0.9 (a hint of overshoot), ω 6, carrying the finger's velocity. */
  release: { omega: 6, zeta: 0.9 },
  /** The idle return to rest: critically damped, ω 4.5 — 1 % left at 1.47 s. */
  idle: { omega: 4.5, zeta: 1 },
  /** Reduced motion: a 200 ms fade instead of the morph. */
  reducedFade: 0.2,
} as const;

/** The drag in the loop: u per second when the simulated finger lets go. */
export const DRAG_RELEASE_VELOCITY = 1.4;
/** A real drag: the width of a full transition in ref px, the commit thresholds. */
/** rubber: the hero follows 55 % of an over-drag, falling off past `rubberFalloff` ref px. */
export const DRAG = { span: 760, commitDistance: 0.5, commitVelocity: 1.1, rubber: 0.55, rubberFalloff: 240 } as const;

// ─── helpers (declared before every worklet that calls them) ──────────────────────────────────

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

/** cubic-bezier(0.77, 0, 0.175, 1) is the on-screen move; a cheaper symmetric cubic stands in for it. */
export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

/** 0 → 1 → 0 bump over [a, b]. */
export function bump(t: number, a: number, b: number) {
  'worklet';
  const p = seg(t, a, b);
  return p <= 0 || p >= 1 ? 0 : Math.sin(Math.PI * p);
}

/** Closed-form damped spring from x0 (velocity v0, units/s) towards 1, τ seconds after release. */
export function spring(tau: number, x0: number, v0: number, omega: number, zeta: number) {
  'worklet';
  if (tau <= 0) return x0;
  const a = 1 - x0;
  if (zeta >= 1) {
    return 1 - Math.exp(-omega * tau) * (a + (a * omega - v0) * tau);
  }
  const wd = omega * Math.sqrt(1 - zeta * zeta);
  const decay = Math.exp(-zeta * omega * tau);
  return 1 - decay * (a * Math.cos(wd * tau) + ((a * zeta * omega - v0) / wd) * Math.sin(wd * tau));
}

/** Velocity of the same spring (units/s). */
export function springVelocity(tau: number, x0: number, v0: number, omega: number, zeta: number) {
  'worklet';
  const h = 1 / 240;
  return (spring(tau + h, x0, v0, omega, zeta) - spring(tau, x0, v0, omega, zeta)) / h;
}

/** The sibling block's 62 BPM beat (pulse-heart-card): a 6 % systole and a 2.5 % echo. */
function pulseValue(phase: number, from: number, to: number) {
  'worklet';
  if (phase < from || phase > to) return 0;
  const s = Math.sin((Math.PI * (phase - from)) / (to - from));
  return s * s;
}

export function pulse(phase: number) {
  'worklet';
  return 0.06 * pulseValue(phase, 0.08, 0.23) + 0.025 * pulseValue(phase, 0.31, 0.45);
}

// ─── the three shapes, sampled once ───────────────────────────────────────────────────────────

function sampleArc(): number[] {
  const { cx, cy, r, from, to } = layout.gauge;
  const out: number[] = [];
  for (let i = 0; i < N; i++) {
    const a = ((from + (to - from) * (i / (N - 1))) * Math.PI) / 180;
    out.push(cx + r * Math.cos(a), cy - r * Math.sin(a));
  }
  return out;
}

function sampleCircle(): number[] {
  const { cx, cy, r } = layout.rings;
  const out: number[] = [];
  for (let i = 0; i < N; i++) {
    // t = 0 at the bottom, clockwise on screen (bottom → left → top → right).
    const a = ((270 - 360 * (i / N)) * Math.PI) / 180;
    out.push(cx + r[0] * Math.cos(a), cy - r[0] * Math.sin(a));
  }
  return out;
}

/** The arc is open (its last point is its right end); the circle and heart are closed. */
export const ARC = sampleArc();
export const CIRCLE = sampleCircle();
export const HEART = HEART_OUTLINE as number[];
export const SHAPES = [ARC, CIRCLE, HEART];
/** Where on its own curve each shape's parameter t lives: the arc spans i / (N − 1), closed ones i / N. */
export const CLOSED = [0, 1, 1];

// ─── the blend ────────────────────────────────────────────────────────────────────────────────

/** Which of the three choreographies a pair uses. */
export function pairOf(lo: number, hi: number) {
  'worklet';
  if (lo === hi) return -1;
  if (lo === 0 && hi === 1) return 0; // arc ↔ ring
  if (lo === 1 && hi === 2) return 1; // ring ↔ heart
  return 2; // arc ↔ heart
}

/** Weight of screen k in the blend (0..1). */
export function weight(k: number, lo: number, hi: number, u: number) {
  'worklet';
  if (lo === hi) return lo === k ? 1 : 0;
  if (k === lo) return 1 - u;
  if (k === hi) return u;
  return 0;
}

/** Shape progress: how far the curve's points have travelled from lo's shape to hi's. */
export function shapeProgress(pair: number, u: number) {
  'worklet';
  if (pair === 0) return easeInOut(seg(u, 0, 0.62));
  if (pair === 1) return easeInOut(seg(u, 0.06, 0.88));
  if (pair === 2) return easeInOut(seg(u, 0.08, 0.9));
  return 0;
}

export type Hero = {
  /** Shape progress along the pair. */
  sh: number;
  /** Main curve: stroke width (ref px), fill opacity, colour mix 0 → 1 between lo's and hi's colour. */
  width: number;
  fill: number;
  colourMix: number;
  /** Progress arc on the main curve: from t0 to t1 (t1 may pass 1 on a closed curve), width, opacity. */
  t0: number;
  t1: number;
  accentWidth: number;
  accentOpacity: number;
  /** 0 = the Readiness segment's light blue, 1 = the Move ring's red gradient. */
  accentRed: number;
  /** The gauge's dot: opacity and diameter; it rides t1. */
  dotOpacity: number;
  dotD: number;
  /** Inner rings: radius scale (1 = in place), opacity, drawn progress (0..target). */
  ringR0: number;
  ringR1: number;
  ringO0: number;
  ringO1: number;
  ringP0: number;
  ringP1: number;
  arrows: number;
  /** The rendered heart, its halo and the flat silhouette's colour. */
  heartImage: number;
  halo: number;
  /** Readiness number: opacity and odometer position (0..7). */
  sevenOpacity: number;
  sevenRoll: number;
  /** "62" digits and "BPM": appear 0..1 each. */
  bpm0: number;
  bpm1: number;
  bpmUnit: number;
};

const SEG = layout.segment;
const RINGS = layout.rings;

/** Everything the hero draws, for the pair (lo, hi) at progress u. */
export function heroFrame(lo: number, hi: number, u: number): Hero {
  'worklet';
  const pair = pairOf(lo, hi);
  // The settled screen: use the pair that ends there at its end point.
  let p = pair;
  let v = u;
  if (pair < 0) {
    p = lo === 0 ? 0 : lo === 1 ? 0 : 1;
    v = lo === 0 ? 0 : 1;
  }
  const sh = shapeProgress(p, v);
  const h: Hero = {
    sh,
    width: 0, fill: 0, colourMix: 0,
    t0: SEG.t0, t1: SEG.t1, accentWidth: layout.gauge.stroke, accentOpacity: 1, accentRed: 0,
    dotOpacity: 1, dotD: layout.dot.d,
    ringR0: 1, ringR1: 1, ringO0: 0, ringO1: 0, ringP0: 0, ringP1: 0, arrows: 0,
    heartImage: 0, halo: 0,
    sevenOpacity: 0, sevenRoll: 7, bpm0: 0, bpm1: 0, bpmUnit: 0,
  };
  const ringStroke = RINGS.stroke;
  if (p === 0) {
    // Readiness ↔ Fitness: the arc closes into the Move ring; the segment sweeps into its progress.
    h.width = lerp(layout.gauge.stroke, ringStroke, sh);
    h.colourMix = easeInOut(seg(v, 0.08, 0.45));
    // The head passes the ring's bottom (t = 1) at v ≈ 0.61, just as the arc's gap has closed.
    const lead = easeInOut(seg(v, 0.2, 1));
    h.t0 = lerp(SEG.t0, 0.5, easeOut(seg(v, 0, 0.5)));
    h.t1 = lerp(SEG.t1, 0.5 + RINGS.progress[0], lead);
    h.accentWidth = h.width;
    h.accentRed = easeInOut(seg(v, 0.05, 0.38));
    // The dot rides the head as a comet, then becomes the round end cap.
    h.dotOpacity = 1 - seg(v, 0.5, 0.8);
    h.dotD = lerp(layout.dot.d, ringStroke * 0.62, seg(v, 0.1, 0.6));
    for (let k = 0; k < 2; k++) {
      const d = 0.1 * k;
      const e = easeOut(seg(v, 0.32 + d, 0.78 + d));
      const radius = lerp(1, RINGS.r[k + 1] / RINGS.r[0], e);
      const o = easeOut(seg(v, 0.3 + d, 0.42 + d));
      const prog = RINGS.progress[k + 1] * easeInOut(seg(v, 0.5 + d, 1));
      if (k === 0) {
        h.ringR0 = radius; h.ringO0 = o; h.ringP0 = prog;
      } else {
        h.ringR1 = radius; h.ringO1 = o; h.ringP1 = prog;
      }
    }
    h.arrows = seg(v, 0.82, 1);
    h.sevenOpacity = 1 - seg(v, 0, 0.22);
    h.sevenRoll = 7;
  } else if (p === 1) {
    // Fitness ↔ Heart: the ring fills in and becomes the heart; the inner rings sink into it.
    h.width = lerp(ringStroke, 0, seg(v, 0.08, 0.86));
    // The ring floods red first, then fills in: the flat heart is already the render's red when it lands.
    h.fill = easeInOut(seg(v, 0.14, 0.62));
    h.colourMix = easeInOut(seg(v, 0.04, 0.42));
    const shrink = easeInOut(seg(v, 0, 0.45));
    h.t0 = 0.5;
    h.t1 = lerp(0.5 + RINGS.progress[0], 0.5, shrink);
    h.accentWidth = h.width;
    h.accentOpacity = 1 - seg(v, 0.22, 0.45);
    h.accentRed = 1;
    h.dotOpacity = 0;
    for (let k = 0; k < 2; k++) {
      const d = 0.08 * k;
      const e = easeInOut(seg(v, d, 0.5 + d));
      const base = RINGS.r[k + 1] / RINGS.r[0];
      const radius = lerp(base, base * 0.45, e);
      const o = 1 - seg(v, 0.12 + d, 0.42 + d);
      if (k === 0) {
        h.ringR0 = radius; h.ringO0 = o; h.ringP0 = RINGS.progress[1];
      } else {
        h.ringR1 = radius; h.ringO1 = o; h.ringP1 = RINGS.progress[2];
      }
    }
    h.arrows = 1 - seg(v, 0, 0.18);
    h.heartImage = seg(v, 0.62, 0.95);
    h.halo = seg(v, 0.5, 1);
    h.bpm0 = easeOut(seg(v, 0.6, 0.9));
    h.bpm1 = easeOut(seg(v, 0.65, 0.95));
    h.bpmUnit = seg(v, 0.75, 1);
  } else {
    // Readiness ↔ Heart: the heart opens at its tip into the arch; arriving, the dot glides to 7.
    h.width = lerp(layout.gauge.stroke, 0, seg(v, 0.15, 0.85));
    // Opening, the heart empties fast: the arch is hollow before it turns grey.
    h.fill = easeInOut(seg(v, 0.5, 0.88));
    h.colourMix = seg(v, 0.2, 0.7);
    const back = 1 - v; // progress towards Readiness
    const glide = easeOut(seg(back, 0.55, 0.97));
    h.t0 = SEG.t0;
    h.t1 = lerp(SEG.t0 + 0.004, SEG.t1, glide);
    h.accentWidth = h.width;
    h.accentOpacity = seg(back, 0.5, 0.6);
    h.dotOpacity = seg(back, 0.5, 0.62);
    h.heartImage = seg(v, 0.7, 0.95);
    h.halo = seg(v, 0.55, 1);
    h.sevenOpacity = seg(back, 0.5, 0.62);
    h.sevenRoll = 7 * glide;
    h.bpm0 = easeOut(seg(v, 0.62, 0.9));
    h.bpm1 = easeOut(seg(v, 0.67, 0.95));
    h.bpmUnit = seg(v, 0.75, 1);
  }
  return h;
}

/** Writes the hero's N points (screen ref px) for the blend into `out`. */
export function heroPoints(lo: number, hi: number, sh: number, out: number[]) {
  'worklet';
  const a = SHAPES[lo];
  const b = SHAPES[hi];
  for (let i = 0; i < 2 * N; i++) out[i] = a[i] + (b[i] - a[i]) * sh;
  return out;
}

/** Main curve colour per screen. */
export const MAIN_COLOUR = [colors.track, colors.moveTrack, colors.heartFlat] as const;

// ─── the autoPlay loop ────────────────────────────────────────────────────────────────────────

export type Blend = { lo: number; hi: number; u: number; beat: number; press: number; pressChip: number; target: number };

/**
 * The loop's finger: it starts at rest and lets go at the release spring's starting speed
 * (u = a p² + b p³ with u(1) = dragTo and u'(1) = DRAG_RELEASE_VELOCITY × the drag's length), so
 * neither the grab nor the release jumps in speed — the liquid's wobble follows that speed.
 */
export function dragCurve(p: number) {
  'worklet';
  const end = DRAG_RELEASE_VELOCITY * (T.dragEnd - T.dragStart);
  const b = end - 2 * T.dragTo;
  const a = T.dragTo - b;
  return a * p * p + b * p * p * p;
}

/** The loop: loop time t (0 ≤ t < LOOP) → blend. Every value is a pure function of t. */
export function loopBlend(t: number): Blend {
  'worklet';
  const press = bump(t, T.pressFitness, T.pressEnd);
  if (t < T.toFitness) return { lo: 0, hi: 1, u: 0, beat: 0, press, pressChip: 3, target: 1 };
  if (t < T.dragStart) {
    const u = clamp01(spring(t - T.toFitness, 0, 0, SPRINGS.tap.omega, SPRINGS.tap.zeta));
    return { lo: 0, hi: 1, u, beat: 0, press, pressChip: 3, target: 1 };
  }
  if (t < T.dragEnd) {
    const u = dragCurve(seg(t, T.dragStart, T.dragEnd));
    return { lo: 1, hi: 2, u, beat: 0, press: 0, pressChip: -1, target: 2 };
  }
  if (t < T.returnStart) {
    const u = clamp01(spring(t - T.dragEnd, T.dragTo, DRAG_RELEASE_VELOCITY, SPRINGS.release.omega, SPRINGS.release.zeta));
    const beat = t >= T.firstBeat && t < T.beatsEnd ? pulse(((t - T.firstBeat) / BEAT) % 1) : 0;
    return { lo: 1, hi: 2, u, beat, press: 0, pressChip: -1, target: 2 };
  }
  // Return: pair (0, 2) with u running 1 → 0.
  const back = t >= T.restAt ? 1 : clamp01(spring(t - T.returnStart, 0, 0, SPRINGS.idle.omega, SPRINGS.idle.zeta));
  return { lo: 0, hi: 2, u: 1 - back, beat: 0, press: 0, pressChip: -1, target: 0 };
}

// ─── the liquid material (round 2) ────────────────────────────────────────────────────────────

/**
 * How liquid the hero is. Settled screens are solid (the rest frame stays the replica); a morph
 * melts the shape within its first `melt` of progress and lets it set again over its last `set`
 * (the spring's slow tail, ~1.1 s on a tap), so the shape reads as liquid while it travels.
 * The wobble follows the shape's speed; ripples follow the heartbeat.
 */
export const LIQUID = {
  melt: 0.14,
  set: 0.3,
  /** u per second that gives a full wobble. */
  wobbleSpeed: 1.6,
  /** Seconds after a beat starts (phase 0) that its ripple leaves the centre: the systole's peak. */
  rippleAt: 0.1 * (60 / 62),
  /** A drag's measured speed is smoothed per frame (manual only; the loop is exact). */
  speedSmoothing: 0.25,
  /** A heart left on screen keeps beating; after this many beats its ripples drop to `quietRipple`. */
  loudBeats: 3,
  quietRipple: 0.3,
} as const;

/** 0 = solid, 1 = liquid, for a blend. */
export function liquidAmount(lo: number, hi: number, u: number) {
  'worklet';
  if (lo === hi) return 0;
  return easeInOut(seg(u, 0, LIQUID.melt)) * easeInOut(seg(1 - u, 0, LIQUID.set));
}

export function wobbleAmount(speed: number) {
  'worklet';
  return clamp01(Math.abs(speed) / LIQUID.wobbleSpeed);
}

/**
 * Ripple age (s since this beat's wave left the centre) and strength, from the beat count (beats
 * since the heart settled, fractional; −1 = not beating). The first beats ripple fully, later ones
 * softly, so a heart left on screen does not keep shouting.
 */
export function rippleOf(count: number) {
  'worklet';
  if (count < 0) return { age: 0, amp: 0 };
  const index = Math.floor(count);
  const age = (count - index) * BEAT - LIQUID.rippleAt;
  if (age < 0) return { age: 0, amp: 0 };
  return { age, amp: index < LIQUID.loudBeats ? 1 : LIQUID.quietRipple };
}

/** The loop's beat count at loop time t (−1 when the heart is not beating). */
export function loopBeatCount(t: number) {
  'worklet';
  if (t < T.firstBeat || t >= T.beatsEnd) return -1;
  return (t - T.firstBeat) / BEAT;
}

/** The loop's shape speed at loop time t (u per second), exact: a difference of the pure loop. */
export function loopSpeed(t: number) {
  'worklet';
  const h = 1 / 120;
  if (t < h) return 0;
  const a = loopBlend(t - h);
  const b = loopBlend(t);
  if (a.lo !== b.lo || a.hi !== b.hi) return 0;
  return (b.u - a.u) / h;
}
