/** Where the page sits on screen for a progress p (0 = full page, 1 = card). Shared by the page view, the chips and the Skia rim. */
import { CURVE, PINCH } from './motion';
import { LAYOUT } from './theme';

export type Stage = { width: number; height: number; insetTop: number };

/** One measured line of a key sentence, in the sentence's own frame. */
export type KeyLine = { text: string; x: number; y: number; width: number; height: number };
/**
 * A key sentence of the article, in the scroll content's frame: its lines, its box, and how many characters of its
 * first line are the bold lead-in (0 for none).
 */
export type KeyGeom = { x: number; y: number; width: number; height: number; lines: KeyLine[]; leadIn: number };

export function mix(a: number, b: number, t: number): number {
  'worklet';
  return a + (b - a) * t;
}

export function clamp01(v: number): number {
  'worklet';
  return Math.min(1, Math.max(0, v));
}

/** 0 → 1 between a and b. */
export function span(v: number, a: number, b: number): number {
  'worklet';
  return clamp01((v - a) / (b - a));
}

function bez(s: number, a: number, b: number): number {
  'worklet';
  const r = 1 - s;
  return 3 * a * s * r * r + 3 * b * s * s * r + s * s * s;
}

/** CSS cubic-bezier(x1, y1, x2, y2) at t, solved on the UI thread (Newton, then bisection as a fallback). */
export function cubic(x1: number, y1: number, x2: number, y2: number, t: number): number {
  'worklet';
  if (t <= 0) return 0;
  if (t >= 1) return 1;
  let s = t;
  for (let i = 0; i < 6; i++) {
    const x = bez(s, x1, x2) - t;
    if (Math.abs(x) < 1e-5) return bez(s, y1, y2);
    const r = 1 - s;
    const dx = 3 * x1 * r * r + 6 * (x2 - x1) * s * r + 3 * (1 - x2) * s * s;
    if (Math.abs(dx) < 1e-6) break;
    s -= x / dx;
  }
  let lo = 0;
  let hi = 1;
  s = t;
  for (let i = 0; i < 14; i++) {
    if (bez(s, x1, x2) < t) lo = s;
    else hi = s;
    s = (lo + hi) / 2;
  }
  return bez(s, y1, y2);
}

/** Smoothstep: zero velocity at both ends, a gentle 1.5× peak (for motion derived from another motion's progress). */
export function smooth(t: number): number {
  'worklet';
  const u = clamp01(t);
  return u * u * (3 - 2 * u);
}

export function easeOut(t: number): number {
  'worklet';
  return cubic(CURVE.out[0], CURVE.out[1], CURVE.out[2], CURVE.out[3], t);
}

export function easeInOut(t: number): number {
  'worklet';
  return cubic(CURVE.inOut[0], CURVE.inOut[1], CURVE.inOut[2], CURVE.inOut[3], t);
}

export function easeSheet(t: number): number {
  'worklet';
  return cubic(CURVE.sheet[0], CURVE.sheet[1], CURVE.sheet[2], CURVE.sheet[3], t);
}

/** The card is smaller while the summary is made and grows a little when it arrives (lift 0 → 1), as in the source. */
export function cardWidth(stage: Stage, lift = 1): number {
  'worklet';
  return stage.width * mix(LAYOUT.thinkWidthRatio, LAYOUT.cardWidthRatio, lift);
}

export function cardHeight(lift = 1): number {
  'worklet';
  return mix(LAYOUT.thinkHeight, LAYOUT.cardHeight, lift);
}

export function cardScale(stage: Stage, lift = 1): number {
  'worklet';
  return cardWidth(stage, lift) / stage.width;
}

/** The page's top margin hidden in the card, in page points. */
export function topTrim(stage: Stage): number {
  'worklet';
  return Math.max(0, stage.insetTop - LAYOUT.cardTopTrim);
}

export type PageFrame = { x: number; y: number; scale: number; clipHeight: number; width: number; height: number; radius: number; trim: number };

/** `breath` (a small fraction) swells the page about its centre while it waits. */
export function pageFrame(stage: Stage, p: number, lift: number, driftX: number, driftY: number, breath = 0): PageFrame {
  'worklet';
  const sc = cardScale(stage, lift);
  const scale = mix(1, sc, p) * (1 + breath);
  const top = mix(LAYOUT.thinkTop, LAYOUT.summaryTop, lift);
  // The crop runs late, so the article stays whole while the fingers pick its sentences.
  const crop = p > 0 ? Math.pow(Math.min(p, 1.2), PINCH.cropPow) : p;
  const clipHeight = Math.max(1, mix(stage.height, cardHeight(lift) / sc, crop));
  const grow = mix(1, sc, p) * breath;
  const x = mix(0, (stage.width - cardWidth(stage, lift)) / 2, p) + driftX - (stage.width * grow) / 2;
  const y = mix(0, top, p) + driftY - (clipHeight * grow) / 2;
  // Corners round quickly as the page lifts (device radius), then tighten to the card's radius. Past the full page
  // (p < 0, the rubber band) they stay square: a page larger than the screen must not show the field at its corners.
  const radius = clamp01(p / 0.06) * mix(LAYOUT.deviceRadius, LAYOUT.cardRadius, clamp01(p));
  return { x, y, scale, clipHeight, width: stage.width * scale, height: clipHeight * scale, radius, trim: topTrim(stage) * clamp01(p) };
}

/** The waiting page's breathing: a slow sine on the light clock, only while the summary is made. */
export function breathing(think: number, clock: number, amplitude: number, period: number): number {
  'worklet';
  return think * amplitude * Math.sin((clock / period) * Math.PI * 2);
}

export function cardTop(lift: number): number {
  'worklet';
  return mix(LAYOUT.thinkTop, LAYOUT.summaryTop, lift);
}
