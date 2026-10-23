/** Where the page sits on screen for a progress p (0 = full page, 1 = card). Shared by the page view and the Skia rim. */
import { LAYOUT } from './theme';

export type Stage = { width: number; height: number; insetTop: number };

export function mix(a: number, b: number, t: number): number {
  'worklet';
  return a + (b - a) * t;
}

export function clamp01(v: number): number {
  'worklet';
  return Math.min(1, Math.max(0, v));
}

/** The card is smaller while it thinks and grows a little when the summary arrives (lift 0 → 1), as in the source. */
export function cardWidth(stage: Stage, lift = 1): number {
  'worklet';
  return stage.width * mix(LAYOUT.thinkWidthRatio, LAYOUT.cardWidthRatio, lift);
}

export function cardHeight(lift = 1): number {
  'worklet';
  return mix(LAYOUT.thinkHeight, LAYOUT.cardHeight, lift);
}

/** Card scale: the page shrinks uniformly until it is as wide as the card. */
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

/** `breath` (a small fraction) swells the card about its centre while it thinks. */
export function pageFrame(stage: Stage, p: number, lift: number, driftX: number, driftY: number, breath = 0): PageFrame {
  'worklet';
  const sc = cardScale(stage, lift);
  const scale = mix(1, sc, p) * (1 + breath);
  const top = mix(LAYOUT.thinkTop, LAYOUT.summaryTop, lift);
  const clipHeight = Math.max(1, mix(stage.height, cardHeight(lift) / sc, p));
  const grow = mix(1, sc, p) * breath;
  const x = mix(0, (stage.width - cardWidth(stage, lift)) / 2, p) + driftX - (stage.width * grow) / 2;
  const y = mix(0, top, p) + driftY - (clipHeight * grow) / 2;
  // Corners round quickly as the page lifts (device radius), then tighten to the card's radius.
  const radius = clamp01(Math.abs(p) / 0.06) * mix(LAYOUT.deviceRadius, LAYOUT.cardRadius, clamp01(p));
  return { x, y, scale, clipHeight, width: stage.width * scale, height: clipHeight * scale, radius, trim: topTrim(stage) * clamp01(p) };
}

/** Card rect at rest in either position (thinking: lift 0, summary: lift 1). */
/** The thinking card's breathing: a slow sine on the light clock, only while it thinks. */
export function breathing(think: number, clock: number, amplitude: number, period: number): number {
  'worklet';
  return think * amplitude * Math.sin((clock / period) * Math.PI * 2);
}

export function cardTop(lift: number): number {
  'worklet';
  return mix(LAYOUT.thinkTop, LAYOUT.summaryTop, lift);
}
