/**
 * Sticky Section Headers — everything is a function of one number, the list's scroll offset `s` (content pt).
 * The autoplay loop only scripts `s` (scrollAt); the sticky header, its pill, the rolling total and the month
 * index all read `s`, so a real scroll drives exactly what the loop shows.
 */
import type { Section } from './ledgerData';

/** Fixed row geometry (pt): every section's top in the content is known without measuring. */
export const HEADER_H = 56;
export const ROW_H = 68;
export const FOOTER_H = 420;

/** Everything the worklets need about the data, as plain arrays (built once per data set). */
export type Geometry = {
  /** Content y of each section header, plus the end of the last section. */
  tops: number[];
  /** Per section: running "spent" after 0, 1, 2, … rows (cents), flattened; RUN_OFFSETS index into it. */
  running: number[];
  runOffsets: number[];
  rowCounts: number[];
  sectionMonth: number[];
  /** Content y where each month's first section starts. */
  monthTops: number[];
  stop1: number;
  stop2: number;
  contentH: number;
};

export function spentOf(cents: number) {
  return cents < 0 ? -cents : 0;
}

export function buildGeometry(sections: Section[], months: number, listTopH: number): Geometry {
  const tops: number[] = [];
  const running: number[] = [];
  const runOffsets: number[] = [];
  let y = listTopH;
  for (const s of sections) {
    tops.push(y);
    y += HEADER_H + s.txns.length * ROW_H;
    runOffsets.push(running.length);
    let acc = 0;
    running.push(0);
    for (const t of s.txns) {
      acc += spentOf(t.cents);
      running.push(acc);
    }
  }
  tops.push(y);
  const monthTops: number[] = [];
  for (let m = 0; m < months; m += 1) {
    const i = sections.findIndex((s) => s.month === m);
    monthTops.push(i < 0 ? y : tops[i]);
  }
  const at = (i: number) => tops[Math.min(i, sections.length - 1)] + HEADER_H + ROW_H * 1.6;
  return {
    tops,
    running,
    runOffsets,
    rowCounts: sections.map((s) => s.txns.length),
    sectionMonth: sections.map((s) => s.month),
    monthTops,
    stop1: at(1),
    stop2: at(4),
    contentH: y + FOOTER_H,
  };
}

/** Loop length (s). */
export const LOOP = 10;
/** Script: rest, glide 1, hold, glide 2, hold, eased return, rest. */
export const GLIDE1 = [1.2, 3.6] as const;
export const GLIDE2 = [4.4, 6.4] as const;
export const BACK = [7.0, 8.8] as const;
/** Scroll stops (Geometry.stop1/stop2): 1.6 rows into the 2nd section, then into the 5th (3 pushes on the way). */

/** Stuck header → pill. */
export const SHRINK = 52;
export const PILL_TOP = 8;
export const PILL_H = 36;
export const PILL_W = 236;
/** A row adds to the running total while its middle passes this far under the pill. */
export const ROLL_LINE = PILL_TOP + PILL_H;
export const ROLL_SPAN = 30;
/** Touch: idle before returning to the top, and how long the return takes. */
export const IDLE = 2;
export const RETURN_LEN = 1.2;

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function easeOutCubic(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

export function mix(a: number, b: number, u: number) {
  'worklet';
  return a + (b - a) * u;
}

/** The scripted scroll offset at loop time t. */
export function scrollAt(t: number, g: Geometry) {
  'worklet';
  const g1 = easeInOutCubic(seg(t, GLIDE1[0], GLIDE1[1]));
  const g2 = easeInOutCubic(seg(t, GLIDE2[0], GLIDE2[1]));
  const back = easeInOutCubic(seg(t, BACK[0], BACK[1]));
  const y = g.stop1 * g1 + (g.stop2 - g.stop1) * g2;
  return y * (1 - back);
}

/** Ghost finger for the scripted scroll: [opacity, y as a fraction of the list height]. */
export function ghostAt(t: number) {
  'worklet';
  const glides = [GLIDE1[0], GLIDE1[1], GLIDE2[0], GLIDE2[1], BACK[0], BACK[1]];
  for (let i = 0; i < 6; i += 2) {
    const a = glides[i];
    const b = glides[i + 1];
    if (t > a - 0.3 && t < b + 0.3) {
      const show = easeOutCubic(seg(t, a - 0.3, a)) * (1 - easeOutCubic(seg(t, b - 0.15, b + 0.3)));
      const u = easeInOutCubic(seg(t, a, b));
      const up = i < 4;
      return [show, up ? mix(0.78, 0.3, u) : mix(0.3, 0.78, u)];
    }
  }
  return [0, 0.78];
}

/** Index of the section whose header has reached the top (−1 above the first). */
export function sectionAt(s: number, g: Geometry) {
  'worklet';
  let k = -1;
  for (let i = 0; i < g.tops.length - 1; i += 1) {
    if (g.tops[i] <= s) k = i;
  }
  return k;
}

/** 0 = full header, 1 = pill. */
export function pillAmount(s: number, k: number, g: Geometry) {
  'worklet';
  if (k < 0) return 0;
  return easeInOutCubic(clamp01((s - g.tops[k]) / SHRINK));
}

/** Vertical offset of the stuck header: 0 while stuck, negative while the next header pushes it up. */
export function pushOffset(s: number, k: number, p: number, g: Geometry) {
  'worklet';
  if (k < 0) return 0;
  const occupied = mix(HEADER_H, PILL_TOP + PILL_H + 8, p);
  return Math.min(0, g.tops[k + 1] - s - occupied);
}

/**
 * The running total of section k at offset s: [from cents, to cents, u]. Each row adds its amount while its middle
 * slides under the pill, eased; only one row is ever mid-way (ROLL_SPAN < ROW_H).
 */
export function runningAt(s: number, k: number, g: Geometry) {
  'worklet';
  if (k < 0) return [0, 0, 0];
  const base = g.runOffsets[k];
  const n = g.rowCounts[k];
  const line = s + ROLL_LINE;
  let done = 0;
  for (let j = 0; j < n; j += 1) {
    const mid = g.tops[k] + HEADER_H + j * ROW_H + ROW_H * 0.5;
    const u = clamp01((line - mid) / ROLL_SPAN);
    if (u >= 1) {
      done = j + 1;
    } else if (u > 0) {
      return [g.running[base + j], g.running[base + j + 1], easeInOutCubic(u)];
    }
  }
  return [g.running[base + done], g.running[base + done], 0];
}

/**
 * Position of one odometer wheel (0..19, two strips of 0–9) for a place value in cents.
 * Rolls forward through the digits between, like a counter, and back when scrolling back.
 */
export function wheelAt(from: number, to: number, u: number, place: number) {
  'worklet';
  const d0 = Math.floor(from / place) % 10;
  let d1 = Math.floor(to / place) % 10;
  if (to > from && d1 < d0) d1 += 10;
  return mix(d0, d1, u);
}

/** Month index position (fractional between two months while their boundary passes the top). */
export function monthAt(s: number, g: Geometry) {
  'worklet';
  const k = sectionAt(s, g);
  if (k < 0) return 0;
  const m = g.sectionMonth[k];
  if (m + 1 >= g.monthTops.length) return m;
  const next = g.monthTops[m + 1];
  return m + easeInOutCubic(clamp01((s - (next - HEADER_H * 2)) / (HEADER_H * 2)));
}
