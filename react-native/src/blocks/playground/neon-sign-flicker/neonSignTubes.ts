/**
 * The bent glass of the sign: every tube as a smooth curve, defined by hand.
 *
 * The script is not set from a font. Each letter is one or two tubes, each tube a list of knots
 * (in x-height units: baseline at y = 0, the top of the x-height at y = -1, y down) that a
 * centripetal Catmull-Rom spline runs through, the way a glass bender heats and bends a tube
 * through a few points. The letters are then slanted and placed on the sign. The cocktail glass
 * is a polyline with rounded bends, since tubes cannot take a sharp corner.
 *
 * Everything here is plain numbers, so it ports as is: a tube becomes a list of cubic segments
 * `[x0, y0, c1x, c1y, c2x, c2y, x1, y1]` in sign points.
 */

/** A knot, in x-height units. */
type Knot = [number, number];

/** One letter or icon part: its tubes, how far the pen moves after it, and which word it is in. */
type GlyphDef = { tubes: Knot[][]; advance: number };

// Script slant: a knot one x-height up moves this far right.
const SLANT = 0.24;
// The size of one x-height unit on the sign, in pt.
export const UNIT = 41;

// Lowercase script, monoline, each letter starting near the baseline on the left and leaving
// on the right towards the next one.
const GLYPHS: Record<string, GlyphDef> = {
  o: {
    tubes: [
      [
        [0.62, -0.98],
        [0.3, -0.98],
        [0.07, -0.72],
        [0.04, -0.3],
        [0.24, -0.02],
        [0.52, -0.05],
        [0.72, -0.36],
        [0.71, -0.76],
        [0.6, -0.96],
        [0.49, -0.93],
        [0.56, -0.83],
        [0.78, -0.84],
        [1.0, -0.92],
      ],
    ],
    advance: 1.02,
  },
  p: {
    tubes: [
      [
        [0.0, -0.9],
        [0.13, -1.03],
        [0.22, -0.9],
        [0.18, -0.3],
        [0.1, 0.36],
        [0.04, 0.8],
      ],
      [
        [0.19, -0.6],
        [0.38, -0.92],
        [0.64, -1.0],
        [0.84, -0.78],
        [0.8, -0.34],
        [0.54, -0.04],
        [0.3, -0.06],
        [0.2, -0.2],
      ],
    ],
    advance: 0.98,
  },
  e: {
    tubes: [
      [
        [0.0, -0.22],
        [0.3, -0.42],
        [0.56, -0.66],
        [0.58, -0.92],
        [0.38, -1.0],
        [0.14, -0.82],
        [0.06, -0.42],
        [0.2, -0.06],
        [0.48, 0.0],
        [0.76, -0.2],
      ],
    ],
    advance: 0.82,
  },
  n: {
    tubes: [
      [
        [0.0, -0.2],
        [0.1, -0.62],
        [0.2, -0.98],
        [0.28, -0.92],
        [0.26, -0.5],
        [0.22, -0.04],
        [0.29, -0.55],
        [0.48, -0.92],
        [0.7, -0.98],
        [0.84, -0.76],
        [0.84, -0.3],
        [0.9, -0.04],
        [1.06, 0.0],
        [1.2, -0.16],
      ],
    ],
    advance: 1.22,
  },
  l: {
    tubes: [
      [
        [0.0, -0.18],
        [0.3, -0.62],
        [0.56, -1.2],
        [0.62, -1.66],
        [0.5, -1.9],
        [0.34, -1.76],
        [0.26, -1.2],
        [0.22, -0.5],
        [0.28, -0.06],
        [0.5, 0.0],
        [0.7, -0.2],
      ],
    ],
    advance: 0.72,
  },
  a: {
    tubes: [
      [
        [0.0, -0.22],
        [0.3, -0.64],
        [0.64, -0.9],
        [0.46, -1.0],
        [0.2, -0.86],
        [0.06, -0.46],
        [0.14, -0.06],
        [0.38, -0.02],
        [0.6, -0.32],
        [0.72, -0.72],
        [0.8, -1.0],
        [0.74, -0.56],
        [0.72, -0.12],
        [0.86, 0.0],
        [1.06, -0.18],
      ],
    ],
    advance: 1.1,
  },
  t: {
    tubes: [
      [
        [0.0, -0.2],
        [0.22, -0.7],
        [0.4, -1.42],
        [0.47, -1.5],
        [0.43, -1.0],
        [0.35, -0.4],
        [0.39, -0.04],
        [0.58, 0.0],
        [0.78, -0.2],
      ],
      [
        [0.12, -0.98],
        [0.44, -1.04],
        [0.82, -1.0],
      ],
    ],
    advance: 0.8,
  },
};

/** One lit piece of the sign: a letter, a part of the glass, or a swash. */
export type NeonPiece = {
  /** The word (or icon) it belongs to: 0 = "open", 1 = "late", 2 = the glass. */
  word: number;
  /** Index into the colour table of the sign. */
  tone: number;
  /** Cubic segments per tube, in sign points (origin at the sign's centre). */
  tubes: number[][];
  /** Its box, in sign points: x, y, width, height. */
  box: [number, number, number, number];
  /** Order in which it lights within its word, from 0. */
  order: number;
  /**
   * Where the glass is painted black because the tube turns through the panel to its electrode:
   * [start of first tube, end of first tube]. A word's letters run on into each other, so only
   * its first and last letter have one.
   */
  ends: [boolean, boolean];
};

/** The colours: index 0 rose, 1 ice cyan, 2 amber, 3 lime (the olive). */
export const TONES = 4;

/** Centripetal Catmull-Rom (alpha 0.5) through the knots, as cubic segments. */
export function splineThrough(points: Knot[]): number[] {
  const out: number[] = [];
  const n = points.length;
  const at = (i: number) => points[Math.max(0, Math.min(n - 1, i))];
  for (let i = 0; i < n - 1; i += 1) {
    const p0 = at(i - 1);
    const p1 = at(i);
    const p2 = at(i + 1);
    const p3 = at(i + 2);
    const d1 = Math.max(1e-4, Math.sqrt(Math.hypot(p1[0] - p0[0], p1[1] - p0[1])));
    const d2 = Math.max(1e-4, Math.sqrt(Math.hypot(p2[0] - p1[0], p2[1] - p1[1])));
    const d3 = Math.max(1e-4, Math.sqrt(Math.hypot(p3[0] - p2[0], p3[1] - p2[1])));
    // At the ends the missing neighbour is the knot itself: the curve leaves along the chord.
    const first = i === 0;
    const last = i === n - 2;
    let c1x: number;
    let c1y: number;
    let c2x: number;
    let c2y: number;
    if (first) {
      c1x = p1[0] + (p2[0] - p1[0]) / 3;
      c1y = p1[1] + (p2[1] - p1[1]) / 3;
    } else {
      const a = d1 * d1;
      const b = d2 * d2;
      const k = 2 * a + 3 * d1 * d2 + b;
      const m = 3 * d1 * (d1 + d2);
      c1x = (a * p2[0] - b * p0[0] + k * p1[0]) / m;
      c1y = (a * p2[1] - b * p0[1] + k * p1[1]) / m;
    }
    if (last) {
      c2x = p2[0] - (p2[0] - p1[0]) / 3;
      c2y = p2[1] - (p2[1] - p1[1]) / 3;
    } else {
      const a = d3 * d3;
      const b = d2 * d2;
      const k = 2 * a + 3 * d3 * d2 + b;
      const m = 3 * d3 * (d3 + d2);
      c2x = (a * p1[0] - b * p3[0] + k * p2[0]) / m;
      c2y = (a * p1[1] - b * p3[1] + k * p2[1]) / m;
    }
    out.push(p1[0], p1[1], c1x, c1y, c2x, c2y, p2[0], p2[1]);
  }
  return out;
}

/** A polyline whose corners are bent round with radius `r` (same units), as cubic segments. */
export function bentPolyline(points: Knot[], r: number): number[] {
  const out: number[] = [];
  const line = (ax: number, ay: number, bx: number, by: number) => {
    out.push(ax, ay, ax + (bx - ax) / 3, ay + (by - ay) / 3, ax + (2 * (bx - ax)) / 3, ay + (2 * (by - ay)) / 3, bx, by);
  };
  let x = points[0][0];
  let y = points[0][1];
  for (let i = 1; i < points.length; i += 1) {
    const [cx, cy] = points[i];
    if (i === points.length - 1) {
      line(x, y, cx, cy);
      break;
    }
    const [nx, ny] = points[i + 1];
    const inLen = Math.hypot(cx - x, cy - y);
    const outLen = Math.hypot(nx - cx, ny - cy);
    const t = Math.min(r, inLen / 2, outLen / 2);
    const ax = cx - ((cx - x) / inLen) * t;
    const ay = cy - ((cy - y) / inLen) * t;
    const bx = cx + ((nx - cx) / outLen) * t;
    const by = cy + ((ny - cy) / outLen) * t;
    line(x, y, ax, ay);
    // A quarter-ish bend: a cubic pulled towards the corner (0.55 of the way, close to a circle).
    out.push(ax, ay, ax + (cx - ax) * 0.55, ay + (cy - ay) * 0.55, bx + (cx - bx) * 0.55, by + (cy - by) * 0.55, bx, by);
    x = bx;
    y = by;
  }
  return out;
}

function transformed(segments: number[], scale: number, dx: number, dy: number, slant: number): number[] {
  const out = new Array<number>(segments.length);
  for (let i = 0; i < segments.length; i += 2) {
    const x = segments[i];
    const y = segments[i + 1];
    out[i] = (x - y * slant) * scale + dx;
    out[i + 1] = y * scale + dy;
  }
  return out;
}

function boxOf(tubes: number[][]): [number, number, number, number] {
  let x0 = Infinity;
  let y0 = Infinity;
  let x1 = -Infinity;
  let y1 = -Infinity;
  for (const t of tubes) {
    for (let i = 0; i < t.length; i += 2) {
      x0 = Math.min(x0, t[i]);
      x1 = Math.max(x1, t[i]);
      y0 = Math.min(y0, t[i + 1]);
      y1 = Math.max(y1, t[i + 1]);
    }
  }
  return [x0, y0, x1 - x0, y1 - y0];
}

/** Sets a word in script from `x`, `y` (baseline, in units); returns its pieces. */
function setWord(text: string, word: number, tone: number, x: number, y: number): NeonPiece[] {
  const pieces: NeonPiece[] = [];
  let pen = x;
  for (let i = 0; i < text.length; i += 1) {
    const g = GLYPHS[text[i]];
    const tubes = g.tubes.map((knots) => transformed(splineThrough(knots), UNIT, pen * UNIT, y * UNIT, SLANT));
    pieces.push({ word, tone, tubes, box: boxOf(tubes), order: i, ends: [i === 0, i === text.length - 1] });
    // Letters overlap a little, so each one's lead-in meets the last one's exit.
    pen += g.advance - 0.04;
  }
  return pieces;
}

/** The cocktail glass and its olive, centred on `x`, `y` (units). */
function setGlass(word: number, x: number, y: number): NeonPiece[] {
  const s = UNIT;
  const place = (segments: number[]) => transformed(segments, s, x * s, y * s, 0);
  const bowl = place(
    bentPolyline(
      [
        [-0.56, 1.02],
        [0, 0.95],
        [0, 0.06],
        [1.02, -1.08],
        [-1.02, -1.08],
        [-0.04, 0.02],
      ],
      0.16,
    ),
  );
  const foot = place(
    bentPolyline(
      [
        [0.06, 0.95],
        [0.58, 1.02],
      ],
      0.1,
    ),
  );
  // The liquid's surface, a slow wave across the bowl.
  const drink = place(
    splineThrough([
      [-0.7, -0.74],
      [-0.36, -0.68],
      [0, -0.76],
      [0.34, -0.7],
      [0.68, -0.76],
    ]),
  );
  const olive = place(
    splineThrough([
      [-0.2, -0.82],
      [-0.42, -0.74],
      [-0.46, -0.52],
      [-0.28, -0.4],
      [-0.08, -0.48],
      [-0.06, -0.7],
      [-0.2, -0.82],
    ]),
  );
  const pick = place(
    bentPolyline(
      [
        [0.42, -1.55],
        [-0.02, -0.86],
      ],
      0.1,
    ),
  );
  return [
    { word, tone: 2, tubes: [bowl, foot], box: boxOf([bowl, foot]), order: 0, ends: [true, true] },
    { word, tone: 2, tubes: [drink], box: boxOf([drink]), order: 1, ends: [true, true] },
    { word, tone: 3, tubes: [olive, pick], box: boxOf([olive, pick]), order: 2, ends: [false, false] },
  ];
}

/** The whole sign, centred on its own origin: "open" over "late", the glass to the right. */
export function buildSign(): { pieces: NeonPiece[]; width: number; height: number } {
  const swash = transformed(
    splineThrough([
      [1.3, 3.06],
      [2.2, 2.92],
      [3.3, 3.04],
      [4.4, 2.9],
      [5.3, 2.98],
    ]),
    UNIT,
    0,
    0,
    0,
  );
  const pieces = [
    ...setWord('open', 0, 0, 0, 0),
    ...setWord('late', 1, 1, 1.5, 2.45),
    ...setGlass(2, 5.35, -0.5),
  ];
  pieces.push({ word: 1, tone: 1, tubes: [swash], box: boxOf([swash]), order: 4, ends: [true, true] });
  // Centre everything on the origin.
  const all = boxOf(pieces.flatMap((p) => p.tubes));
  const cx = all[0] + all[2] / 2;
  const cy = all[1] + all[3] / 2;
  for (const p of pieces) {
    p.tubes = p.tubes.map((t) => t.map((v, i) => (i % 2 === 0 ? v - cx : v - cy)));
    p.box = boxOf(p.tubes);
  }
  return { pieces, width: all[2], height: all[3] };
}
