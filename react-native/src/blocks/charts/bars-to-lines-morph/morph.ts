import { Skia, type SkPath } from '@shopify/react-native-skia';

import type { Plot } from './vitals';

export const HOURS = 24;

// A switch, in seconds. A: the old chart folds up (bars shrink into dots, one after another;
// lines retract into their dots). B: the dots travel to the new chart, the second set a beat
// behind. C: the new chart unfolds (dots stretch into bars, or lines draw between the dots).
export const COLLAPSE = 0.25;
export const STEP = 0.008;
export const TRAVEL = 0.45;
export const SECOND = 0.04;
export const GROW = 0.25;
export const DRAW = 0.45;
export const LABEL_IN = 0.2;
export const LABEL_OUT = 0.15;
export const A_END = COLLAPSE + (HOURS - 1) * STEP;
export const B_END = A_END + TRAVEL + SECOND;
export const C_END = B_END + Math.max(GROW + (HOURS - 1) * STEP, DRAW);
export const TOTAL = C_END + LABEL_IN;
// Reduced motion fades the old chart out and the new one in, each this long.
export const FADE = 0.2;

export type Look = { barMax: number; barShare: number; barRadius: number; dot: number; gap: number };

function clamp01(value: number) {
  'worklet';
  return Math.min(1, Math.max(0, value));
}

export function ease(value: number) {
  'worklet';
  const u = clamp01(value);
  return u * u * (3 - 2 * u);
}

function lerp(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

/** A reading's height on the canvas under a plot's axis. */
export function yOf(value: number, plot: Plot, height: number) {
  'worklet';
  const y = height * (1 - (value - plot.min) / (plot.max - plot.min));
  return Math.min(height + 40, Math.max(-40, y));
}

function addBar(path: SkPath, x: number, centre: number, half: number, width: number, radius: number) {
  'worklet';
  path.addRRect(Skia.RRectXY(Skia.XYWHRect(x - width / 2, centre - half, width, half * 2), radius, radius));
}

function addLine(path: SkPath, values: number[], plot: Plot, pitch: number, height: number) {
  'worklet';
  for (let i = 0; i < HOURS; i++) {
    const x = (i + 0.5) * pitch;
    const y = yOf(values[i], plot, height);
    if (i === 0) path.moveTo(x, y);
    else path.lineTo(x, y);
  }
}

/**
 * Everything the chart draws at a time into a switch from plot `from` to plot `to`: the bars,
 * the two sets of dots, the two lines and how much of them is drawn, the red limits' heights,
 * how far the colours have gone from the old chart to the new, and which chart the labels and
 * the lines belong to.
 */
export function scene(p: number, from: Plot, to: Plot, width: number, height: number, look: Look, still: boolean) {
  'worklet';
  const pitch = width / HOURS;
  const bw = Math.min(look.barMax, pitch * look.barShare);
  const bars = Skia.Path.Make();
  const dots1 = Skia.Path.Make();
  const dots2 = Skia.Path.Make();
  const line1 = Skia.Path.Make();
  const line2 = Skia.Path.Make();
  let trim = 1;
  let mix = 1;
  let opacity = 1;
  let lineCount = to.lines;
  let plot = to;
  let stage = 3;
  if (still) {
    // The old chart at rest fades out, then the new one at rest fades in.
    plot = p < FADE ? from : to;
    opacity = p < FADE ? 1 - p / FADE : clamp01((p - FADE) / FADE);
    mix = p < FADE ? 0 : 1;
  } else if (p < A_END) {
    plot = from;
    stage = 1;
    mix = 0;
  } else if (p < B_END) stage = 2;

  if (stage === 1 || stage === 3) {
    // One chart, folding (stage 1) or unfolding (stage 3), or at rest.
    lineCount = plot.lines;
    for (let i = 0; i < HOURS; i++) {
      const x = (i + 0.5) * pitch;
      const ya = yOf(plot.a[i], plot, height);
      const yb = yOf(plot.b[i], plot, height);
      if (plot.bars) {
        const fold = still ? 0 : stage === 1 ? ease((p - i * STEP) / COLLAPSE) : 1 - ease((p - B_END - i * STEP) / GROW);
        const centre = (ya + yb) / 2;
        const half = Math.max(bw / 2, Math.abs(ya - yb) / 2);
        addBar(bars, x, centre, lerp(half, bw / 2, fold), bw, lerp(look.barRadius, bw / 2, fold));
      } else {
        dots1.addCircle(x, ya, look.dot);
        if (plot.lines === 2) dots2.addCircle(x, yb, look.dot);
      }
    }
    if (!plot.bars) {
      addLine(line1, plot.a, plot, pitch, height);
      if (plot.lines === 2) addLine(line2, plot.b, plot, pitch, height);
      if (!still) trim = stage === 1 ? 1 - ease(p / A_END) : ease((p - B_END) / DRAW);
    }
  } else {
    // Dots on their way from the old chart to the new one.
    const e1 = ease((p - A_END) / TRAVEL);
    const e2 = ease((p - A_END - SECOND) / TRAVEL);
    mix = e1;
    trim = 0;
    const r1 = lerp(from.bars ? bw / 2 : look.dot, to.bars ? bw / 2 : look.dot, e1);
    const r2 = lerp(from.bars ? bw / 2 : look.dot, to.bars ? bw / 2 : look.dot, e2);
    for (let i = 0; i < HOURS; i++) {
      const x = (i + 0.5) * pitch;
      const fa = yOf(from.a[i], from, height);
      const fb = yOf(from.b[i], from, height);
      const ta = yOf(to.a[i], to, height);
      const tb = yOf(to.b[i], to, height);
      // A bar is one dot at its centre; a line chart has a dot per line.
      const f1 = from.bars ? (fa + fb) / 2 : fa;
      const f2 = from.bars ? (fa + fb) / 2 : fb;
      const t1 = to.bars ? (ta + tb) / 2 : ta;
      const t2 = to.bars ? (ta + tb) / 2 : tb;
      dots1.addCircle(x, lerp(f1, t1, e1), r1);
      dots2.addCircle(x, lerp(f2, t2, e2), r2);
    }
  }

  const travel = stage === 2 ? ease((p - A_END) / TRAVEL) : stage === 1 ? 0 : 1;
  const above = lerp(yOf(from.above, from, height), yOf(to.above, to, height), still ? mix : travel);
  const below = lerp(yOf(from.below, from, height), yOf(to.below, to, height), still ? mix : travel);
  return { bars, dots1, dots2, line1, line2, trim, mix, opacity, lineCount, above, below, stage };
}

/** The red and the plain parts of the canvas, cut at the red limits, `gap` points apart. */
export function limitClips(above: number, below: number, width: number, height: number, gap: number) {
  'worklet';
  const red = Skia.Path.Make();
  const plain = Skia.Path.Make();
  if (above - gap > 0) red.addRect(Skia.XYWHRect(0, -50, width, above - gap + 50));
  if (below + gap < height + 50) red.addRect(Skia.XYWHRect(0, below + gap, width, height + 50 - below - gap));
  const top = Math.max(-50, above + gap);
  const bottom = Math.min(height + 50, below - gap);
  if (bottom > top) plain.addRect(Skia.XYWHRect(0, top, width, bottom - top));
  return [red, plain];
}
