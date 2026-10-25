/*
 A monoline piping alphabet: every letter is the path a decorator's nozzle follows, written as
 strokes in font units (x-height 1, baseline 0, y down; capitals 1.45 tall). Commands:
   M x y · L x y · Q cx cy x y · C c1x c1y c2x c2y x y · A cx cy rx ry startDeg sweepDeg
 Each M starts a new stroke (the nozzle lifts in between). The layout samples the strokes into
 polylines in pt, slants them like hand lettering and adds a tiny seeded wobble, so the UI thread
 only reads flat number arrays.
 */

const G: Record<string, [number, string]> = {
  a: [1.1, 'A 0.46 -0.5 0.44 0.5 -25 -335 M 0.93 -1 L 0.93 -0.12 Q 0.95 0.02 1.08 -0.04'],
  b: [1.0, 'M 0.08 -1.5 L 0.08 0 M 0.08 -0.5 A 0.52 -0.5 0.44 0.5 180 359'],
  c: [0.95, 'A 0.5 -0.5 0.46 0.5 -40 -280'],
  d: [1.05, 'A 0.45 -0.5 0.44 0.5 0 359 M 0.92 -1.5 L 0.92 0'],
  e: [1.0, 'M 0.08 -0.5 L 0.93 -0.5 A 0.5 -0.5 0.44 0.5 0 -315'],
  f: [0.8, 'M 0.85 -1.38 C 0.72 -1.55 0.36 -1.52 0.36 -1.18 L 0.36 0 M 0.05 -0.98 L 0.74 -0.98'],
  g: [1.05, 'A 0.45 -0.5 0.43 0.5 0 359 M 0.9 -1 L 0.9 0.22 C 0.9 0.62 0.3 0.66 0.08 0.36'],
  h: [1.0, 'M 0.08 -1.5 L 0.08 0 M 0.08 -0.62 C 0.18 -1.06 0.86 -1.1 0.86 -0.56 L 0.86 0'],
  i: [0.36, 'M 0.16 -1 L 0.16 0 M 0.16 -1.36 L 0.165 -1.35'],
  j: [0.6, 'M 0.46 -1 L 0.46 0.24 C 0.46 0.62 0.06 0.62 -0.04 0.4 M 0.46 -1.36 L 0.465 -1.35'],
  k: [0.88, 'M 0.08 -1.5 L 0.08 0 M 0.78 -1 L 0.1 -0.42 M 0.32 -0.6 L 0.82 0'],
  l: [0.4, 'M 0.16 -1.5 L 0.16 -0.14 Q 0.18 0.02 0.36 -0.03'],
  m: [1.38, 'M 0.07 -1 L 0.07 0 M 0.07 -0.64 C 0.14 -1.06 0.68 -1.1 0.68 -0.6 L 0.68 0 M 0.68 -0.6 C 0.74 -1.06 1.28 -1.1 1.28 -0.6 L 1.28 0'],
  n: [0.96, 'M 0.08 -1 L 0.08 0 M 0.08 -0.6 C 0.17 -1.06 0.86 -1.1 0.86 -0.55 L 0.86 0'],
  o: [1.0, 'A 0.5 -0.5 0.46 0.5 -90 359'],
  p: [1.0, 'M 0.08 -1 L 0.08 0.5 M 0.08 -0.5 A 0.52 -0.5 0.44 0.5 180 359'],
  q: [1.0, 'A 0.45 -0.5 0.43 0.5 0 359 M 0.9 -1 L 0.9 0.5'],
  r: [0.75, 'M 0.08 -1 L 0.08 0 M 0.08 -0.55 C 0.17 -0.96 0.46 -1.06 0.72 -0.9'],
  s: [0.92, 'M 0.8 -0.88 C 0.65 -1.06 0.1 -1.05 0.12 -0.75 C 0.14 -0.48 0.82 -0.55 0.84 -0.25 C 0.86 0.05 0.25 0.07 0.07 -0.12'],
  t: [0.75, 'M 0.36 -1.35 L 0.36 -0.15 Q 0.38 0.02 0.66 -0.05 M 0.05 -0.98 L 0.7 -0.98'],
  u: [0.96, 'M 0.08 -1 L 0.08 -0.4 C 0.08 0.08 0.86 0.08 0.86 -0.4 M 0.86 -1 L 0.86 0'],
  v: [0.9, 'M 0.02 -1 L 0.45 0 L 0.88 -1'],
  w: [1.32, 'M 0.02 -1 L 0.33 0 L 0.66 -0.74 L 0.98 0 L 1.3 -1'],
  x: [0.86, 'M 0.06 -1 L 0.8 0 M 0.8 -1 L 0.06 0'],
  y: [0.9, 'M 0.04 -1 L 0.47 0 M 0.88 -1 L 0.36 0.34 C 0.27 0.56 0.1 0.56 0.0 0.48'],
  z: [0.92, 'M 0.08 -1 L 0.82 -1 L 0.08 0 L 0.86 0'],
  A: [1.12, 'M 0 0 L 0.56 -1.45 L 1.12 0 M 0.22 -0.5 L 0.9 -0.5'],
  B: [0.98, 'M 0.08 0 L 0.08 -1.45 C 1.0 -1.5 1.0 -0.76 0.08 -0.76 C 1.12 -0.78 1.12 0.05 0.08 0'],
  C: [1.25, 'A 0.68 -0.725 0.62 0.725 -40 -280'],
  D: [1.15, 'M 0.08 0 L 0.08 -1.45 C 1.36 -1.45 1.36 0 0.08 0'],
  E: [0.95, 'M 0.86 -1.45 L 0.08 -1.45 L 0.08 0 L 0.88 0 M 0.08 -0.75 L 0.7 -0.75'],
  F: [0.9, 'M 0.86 -1.45 L 0.08 -1.45 L 0.08 0 M 0.08 -0.75 L 0.7 -0.75'],
  G: [1.38, 'A 0.7 -0.725 0.62 0.725 -40 -300 L 1.3 -0.72 L 0.86 -0.72'],
  H: [1.1, 'M 0.08 -1.45 L 0.08 0 M 1.02 -1.45 L 1.02 0 M 0.08 -0.75 L 1.02 -0.75'],
  I: [0.36, 'M 0.16 -1.45 L 0.16 0'],
  J: [0.86, 'M 0.72 -1.45 L 0.72 -0.36 C 0.72 0.1 0.06 0.12 0.03 -0.3'],
  K: [1.02, 'M 0.08 -1.45 L 0.08 0 M 0.96 -1.45 L 0.1 -0.6 M 0.38 -0.86 L 1.0 0'],
  L: [0.9, 'M 0.08 -1.45 L 0.08 0 L 0.86 0'],
  M: [1.4, 'M 0.05 0 L 0.12 -1.45 L 0.7 -0.4 L 1.28 -1.45 L 1.35 0'],
  N: [1.14, 'M 0.08 0 L 0.08 -1.45 L 1.04 0 L 1.04 -1.45'],
  O: [1.45, 'A 0.72 -0.725 0.66 0.725 -90 359'],
  P: [0.96, 'M 0.08 0 L 0.08 -1.45 C 1.12 -1.5 1.12 -0.64 0.08 -0.68'],
  Q: [1.45, 'A 0.72 -0.725 0.66 0.725 -90 359 M 0.86 -0.34 L 1.4 0.1'],
  R: [1.02, 'M 0.08 0 L 0.08 -1.45 C 1.12 -1.5 1.12 -0.64 0.08 -0.68 M 0.44 -0.68 L 0.98 0'],
  S: [1.1, 'M 1.0 -1.25 C 0.85 -1.5 0.12 -1.55 0.12 -1.08 C 0.12 -0.68 1.05 -0.8 1.05 -0.35 C 1.05 0.08 0.25 0.1 0.06 -0.18'],
  T: [1.02, 'M 0.01 -1.45 L 1.01 -1.45 M 0.51 -1.45 L 0.51 0'],
  U: [1.12, 'M 0.08 -1.45 L 0.08 -0.5 C 0.08 0.16 1.04 0.16 1.04 -0.5 L 1.04 -1.45'],
  V: [1.1, 'M 0 -1.45 L 0.55 0 L 1.1 -1.45'],
  W: [1.5, 'M 0 -1.45 L 0.38 0 L 0.75 -1.05 L 1.12 0 L 1.5 -1.45'],
  X: [1.04, 'M 0.03 -1.45 L 1.01 0 M 1.01 -1.45 L 0.03 0'],
  Y: [1.04, 'M 0 -1.45 L 0.52 -0.7 L 1.04 -1.45 M 0.52 -0.7 L 0.52 0'],
  Z: [1.08, 'M 0.05 -1.45 L 1.02 -1.45 L 0.05 0 L 1.03 0'],
  '0': [1.0, 'A 0.5 -0.72 0.44 0.72 -90 359'],
  '1': [0.62, 'M 0.08 -1.2 L 0.42 -1.45 L 0.42 0'],
  '2': [0.98, 'M 0.1 -1.15 C 0.2 -1.55 0.92 -1.55 0.9 -1.08 C 0.88 -0.7 0.3 -0.42 0.06 0 L 0.94 0'],
  '3': [0.95, 'M 0.12 -1.3 C 0.4 -1.6 0.94 -1.45 0.86 -1.1 C 0.8 -0.85 0.5 -0.78 0.36 -0.78 C 1.08 -0.74 1.0 0.1 0.36 0.02 C 0.2 0 0.1 -0.08 0.06 -0.16'],
  '4': [1.0, 'M 0.72 0 L 0.72 -1.45 L 0.04 -0.42 L 0.98 -0.42'],
  '5': [0.95, 'M 0.86 -1.45 L 0.2 -1.45 L 0.13 -0.82 C 0.6 -1.02 0.98 -0.78 0.92 -0.4 C 0.86 0.08 0.25 0.1 0.06 -0.16'],
  '6': [0.98, 'M 0.82 -1.38 C 0.42 -1.62 0.06 -1.1 0.08 -0.52 A 0.5 -0.44 0.42 0.44 180 359'],
  '7': [0.95, 'M 0.06 -1.45 L 0.92 -1.45 L 0.34 0'],
  '8': [0.98, 'A 0.49 -1.1 0.34 0.35 90 359 M 0.49 -0.75 A 0.49 -0.375 0.42 0.375 -90 359'],
  '9': [0.98, 'A 0.48 -1.0 0.42 0.44 0 359 M 0.9 -1.0 C 0.9 -0.3 0.6 0.08 0.14 -0.06'],
  '.': [0.32, 'M 0.14 -0.06 L 0.145 -0.05'],
  ',': [0.32, 'M 0.18 -0.06 L 0.08 0.22'],
  '!': [0.34, 'M 0.16 -1.45 L 0.16 -0.42 M 0.16 -0.06 L 0.165 -0.05'],
  '?': [0.85, 'M 0.08 -1.18 C 0.2 -1.6 0.86 -1.56 0.82 -1.1 C 0.8 -0.78 0.4 -0.76 0.4 -0.38 M 0.4 -0.06 L 0.405 -0.05'],
  "'": [0.3, 'M 0.15 -1.45 L 0.13 -1.08'],
  '-': [0.62, 'M 0.08 -0.5 L 0.54 -0.5'],
  '&': [1.1, 'M 1.05 0 L 0.28 -0.9 C 0.1 -1.16 0.24 -1.45 0.48 -1.45 C 0.72 -1.45 0.82 -1.16 0.6 -0.96 L 0.2 -0.62 C -0.06 -0.36 0.1 0.04 0.44 0.02 C 0.7 0 0.86 -0.2 0.96 -0.5'],
  '♥': [1.1, 'M 0.55 0 C 0.0 -0.4 0.0 -0.95 0.3 -1.0 C 0.45 -1.02 0.55 -0.9 0.55 -0.78 C 0.55 -0.9 0.65 -1.02 0.8 -1.0 C 1.1 -0.95 1.1 -0.4 0.55 0'],
};

const SPACE = 0.55;
const TRACK = 0.26;
const SLANT = 0.16;

/** A sampled stroke: points (x, y flat, pt) and running length at each point. */
export type Stroke = { pts: number[]; len: number[]; glyph: number };
export type Lettering = {
  strokes: Stroke[];
  /** Per glyph: its first stroke, stroke count, and ink length. */
  firstStroke: number[];
  strokeCount: number[];
  glyphLength: number[];
  /** Glyph origins (pt), used to glide letters when the layout changes. */
  originX: number[];
  originY: number[];
  total: number;
  size: number;
};

function hash(n: number) {
  const s = Math.sin(n * 12.9898) * 43758.5453;
  return s - Math.floor(s);
}

function tokens(src: string) {
  return src.trim().split(/\s+/);
}

/** Samples a glyph into strokes of points in font units. */
function sampleGlyph(src: string): number[][] {
  const t = tokens(src);
  const strokes: number[][] = [];
  let cur: number[] = [];
  let x = 0;
  let y = 0;
  const push = (px: number, py: number) => {
    cur.push(px, py);
    x = px;
    y = py;
  };
  let i = 0;
  const num = () => parseFloat(t[i++]);
  while (i < t.length) {
    const cmd = t[i++];
    if (cmd === 'M') {
      if (cur.length > 2) strokes.push(cur);
      cur = [];
      push(num(), num());
    } else if (cmd === 'L') {
      const nx = num();
      const ny = num();
      const steps = Math.max(1, Math.ceil(Math.hypot(nx - x, ny - y) / 0.06));
      const x0 = x;
      const y0 = y;
      for (let k = 1; k <= steps; k += 1) push(x0 + ((nx - x0) * k) / steps, y0 + ((ny - y0) * k) / steps);
    } else if (cmd === 'Q') {
      const cx = num();
      const cy = num();
      const nx = num();
      const ny = num();
      const x0 = x;
      const y0 = y;
      for (let k = 1; k <= 16; k += 1) {
        const u = k / 16;
        push((1 - u) * (1 - u) * x0 + 2 * u * (1 - u) * cx + u * u * nx, (1 - u) * (1 - u) * y0 + 2 * u * (1 - u) * cy + u * u * ny);
      }
    } else if (cmd === 'C') {
      const c1x = num();
      const c1y = num();
      const c2x = num();
      const c2y = num();
      const nx = num();
      const ny = num();
      const x0 = x;
      const y0 = y;
      for (let k = 1; k <= 24; k += 1) {
        const u = k / 24;
        const v = 1 - u;
        push(v * v * v * x0 + 3 * v * v * u * c1x + 3 * v * u * u * c2x + u * u * u * nx, v * v * v * y0 + 3 * v * v * u * c1y + 3 * v * u * u * c2y + u * u * u * ny);
      }
    } else if (cmd === 'A') {
      const cx = num();
      const cy = num();
      const rx = num();
      const ry = num();
      const a0 = (num() * Math.PI) / 180;
      const sw = (num() * Math.PI) / 180;
      const steps = Math.ceil(Math.abs(sw) / 0.12);
      const sx = cx + rx * Math.cos(a0);
      const sy = cy + ry * Math.sin(a0);
      if (cur.length === 0) push(sx, sy);
      else {
        // An arc after a line continues from where the line ended.
        push(sx, sy);
      }
      for (let k = 1; k <= steps; k += 1) {
        const a = a0 + (sw * k) / steps;
        push(cx + rx * Math.cos(a), cy + ry * Math.sin(a));
      }
    }
  }
  if (cur.length > 2) strokes.push(cur);
  // A one-point dot (the i's) becomes a very short stroke, which pipes as a round blob.
  return strokes;
}

const CACHE: Record<string, [number, number[][]]> = {};
function glyph(ch: string): [number, number[][]] | null {
  if (CACHE[ch]) return CACHE[ch];
  const def = G[ch] ?? G[ch.toLowerCase()] ?? null;
  if (!def) return null;
  const out: [number, number[][]] = [def[0], sampleGlyph(def[1])];
  CACHE[ch] = out;
  return out;
}

function lineWidth(chars: string[]) {
  let w = 0;
  chars.forEach((ch, i) => {
    if (ch === ' ') w += SPACE;
    else w += (glyph(ch)?.[0] ?? SPACE) + (i < chars.length - 1 ? TRACK : 0);
  });
  return w;
}

export const MAX_LETTERS = 14;

/**
 * Lays the message out on the cake: one line, or two split at the space nearest the middle,
 * fitted into a box `boxW` × `boxH` pt centred on the cake (cake-local coordinates).
 */
export function layoutMessage(text: string, boxW: number, boxH: number): Lettering {
  const chars = Array.from(text).slice(0, MAX_LETTERS);
  let lines: string[][] = [chars];
  const spaces = chars.map((c, i) => (c === ' ' ? i : -1)).filter((i) => i > 0);
  if (chars.length > 7 && spaces.length > 0) {
    let best = spaces[0];
    for (const s of spaces) if (Math.abs(s - chars.length / 2) < Math.abs(best - chars.length / 2)) best = s;
    lines = [chars.slice(0, best), chars.slice(best + 1)];
  }
  const widest = Math.max(0.01, ...lines.map(lineWidth));
  // x-height in pt: as large as the box allows, capped so short names don't get huge.
  const rows = lines.length;
  const size = Math.min(boxW / (widest + SLANT * 1.5), boxH / (rows * 1.5 + (rows - 1) * 0.85 + 0.4), 56);
  const out: Lettering = { strokes: [], firstStroke: [], strokeCount: [], glyphLength: [], originX: [], originY: [], total: 0, size };
  const lineGap = 2.3 * size;
  const blockH = (rows - 1) * lineGap;
  let gi = 0;
  let seedBase = 0;
  lines.forEach((line, li) => {
    const w = lineWidth(line) * size;
    let x = -w / 2;
    // Baselines sit so the x-height of the block is centred.
    const base = -blockH / 2 + li * lineGap + size * 0.5;
    line.forEach((ch, ci) => {
      const g = ch === ' ' ? null : glyph(ch);
      out.firstStroke.push(out.strokes.length);
      out.originX.push(x);
      out.originY.push(base);
      let ink = 0;
      if (g) {
        for (const src of g[1]) {
          const pts: number[] = [];
          const len: number[] = [];
          let run = 0;
          for (let k = 0; k < src.length; k += 2) {
            const fx = src[k];
            const fy = src[k + 1];
            // Slant like hand lettering, then a slow seeded wobble across the stroke.
            const px = x + (fx - fy * SLANT) * size;
            const py = base + fy * size;
            const wob = (Math.sin(k * 0.21 + seedBase * 1.7) + 0.6 * Math.sin(k * 0.53 + hash(seedBase) * 6)) * size * 0.012;
            const qx = px + wob;
            const qy = py - wob * 0.6;
            if (pts.length >= 2) run += Math.hypot(qx - pts[pts.length - 2], qy - pts[pts.length - 1]);
            pts.push(qx, qy);
            len.push(run);
          }
          seedBase += 1;
          out.strokes.push({ pts, len, glyph: gi });
          ink += Math.max(run, 0.5);
        }
        x += (g[0] + TRACK) * size;
      } else x += (ch === ' ' ? SPACE : SPACE + TRACK) * size;
      out.strokeCount.push(out.strokes.length - out.firstStroke[gi]);
      out.glyphLength.push(ink);
      out.total += ink;
      gi += 1;
      void ci;
    });
  });
  return out;
}

/**
 * A piping schedule for scripted lettering: per stroke the fraction of the window at which it
 * starts and ends, the nozzle travelling between strokes (lifted) in proportion to the distance.
 */
export function pipeSchedule(L: Lettering): { t0: number[]; t1: number[] } {
  const ink = L.strokes.map((s) => s.len[s.len.length - 1] ?? 0);
  const travel: number[] = [];
  for (let i = 0; i < L.strokes.length; i += 1) {
    if (i === 0) travel.push(0);
    else {
      const a = L.strokes[i - 1].pts;
      const b = L.strokes[i].pts;
      travel.push(0.35 * Math.hypot(b[0] - a[a.length - 2], b[1] - a[a.length - 1]) + 6);
    }
  }
  let total = 0;
  for (let i = 0; i < ink.length; i += 1) total += ink[i] + travel[i];
  const t0: number[] = [];
  const t1: number[] = [];
  let acc = 0;
  for (let i = 0; i < ink.length; i += 1) {
    acc += travel[i];
    t0.push(acc / total);
    acc += ink[i];
    t1.push(acc / total);
  }
  return { t0, t1 };
}
