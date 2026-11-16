// The readout's round-dot numerals. Every glyph is 7 rows of dots on the digits' lattice; a dot sits on one of two
// interleaved sub-lattices — whole steps (x2 even) or half steps (x2 odd) from the glyph's centre — so a 0 can be the
// pin's ring of 14 dots (top pair at ±½ step, then ±1½, then ±2). x2 is the dot's x in half steps, −4 … 4.
//
// A row is a 9-bit mask (bit x2 + 4). The shader reads two glyphs at once (the one leaving and the one arriving) as
// `leaving * 512 + arriving` per row, one float per row, so a cell-by-cell re-form needs no texture.

type Rows = readonly (readonly number[])[];

const SHAPES: Record<string, Rows> = {
  // the pin's 0 (measured: 14 dots, 5.85 pt apart)
  '0': [
    [-1, 1],
    [-3, 3],
    [-4, 4],
    [-4, 4],
    [-4, 4],
    [-3, 3],
    [-1, 1],
  ],
  '1': [[1], [-1, 1], [-3, 1], [1], [1], [1], [1]],
  '2': [[-1, 1], [-3, 3], [3], [1], [-1], [-3], [-4, -2, 0, 2, 4]],
  '3': [[-1, 1], [-3, 3], [3], [-1, 1], [3], [-3, 3], [-1, 1]],
  '4': [[2], [0, 2], [-2, 2], [-4, 2], [-4, -2, 0, 2, 4], [2], [2]],
  '5': [[-2, 0, 2, 4], [-4], [-4], [-3, -1, 1], [4], [-3, 3], [-1, 1]],
  '6': [[0, 2], [-2], [-4], [-4, -2, 0, 2], [-4, 4], [-3, 3], [-1, 1]],
  '7': [[-4, -2, 0, 2, 4], [4], [2], [1], [-1], [-1], [-1]],
  '8': [
    [-1, 1],
    [-3, 3],
    [-3, 3],
    [-1, 1],
    [-3, 3],
    [-3, 3],
    [-1, 1],
  ],
  '9': [[-1, 1], [-3, 3], [-4, 4], [-2, 0, 2, 4], [4], [2], [-2, 0]],
  // the pin's decimal point: a 2 × 2 square on the baseline
  '.': [[], [], [], [], [], [-1, 1], [-1, 1]],
  ' ': [[], [], [], [], [], [], []],
};

const ORDER = '0123456789. ';

function maskOf(row: readonly number[]) {
  let m = 0;
  for (const x2 of row) m += 2 ** (x2 + 4);
  return m;
}

/** Flat table: glyph g (index into ORDER) row r at `GLYPH_MASKS[g * 7 + r]`. */
export const GLYPH_MASKS: number[] = ORDER.split('').flatMap((ch) => SHAPES[ch].map(maskOf));
/** Advance from one glyph's centre to the next, in digit-lattice steps (the pin: 4.3 steps digit → point, 4.35 point → digit). */
export const ADVANCE = { digit: 5.4, point: 3.2 } as const;
export const SLOTS = 4;

/** Index of a character in the glyph table (unknown characters are blank). */
export function glyphIndex(code: number) {
  'worklet';
  if (code >= 48 && code <= 57) return code - 48;
  if (code === 46) return 10;
  return 11;
}

/**
 * Pack the readout for the shader: for each of 4 slots the 7 row codes (`leaving * 512 + arriving`), plus each slot's centre in
 * digit steps and whether it is the point. Right-aligned: the last character is the last slot; shorter strings leave the
 * first slots blank. `out` is [A0 B0 A1 B1 A2 B2 A3 B3] as 4 floats each (B's 4th is spare), then x (4), kind (4).
 */
export function packReadout(from: string, to: string, out: number[]) {
  'worklet';
  const n = to.length;
  const m = from.length;
  // centres, right to left from the text's own centre
  let width = 0;
  const adv: number[] = [];
  for (let i = 0; i < n; i++) {
    const a = to.charCodeAt(i) === 46 ? ADVANCE.point : ADVANCE.digit;
    adv.push(a);
    width += a;
  }
  // centre of slot i = start + (running advance) ; consecutive glyph centres are `advance of the left glyph` / 2 + `of the right` / 2
  let acc = 0;
  const cx: number[] = [];
  for (let i = 0; i < n; i++) {
    cx.push(acc + adv[i] / 2);
    acc += adv[i];
  }
  // the pin spaces digit → point 4.3 and point → digit 4.35 (centres), i.e. a point is narrower than a digit
  const mid = width / 2;
  for (let s = 0; s < SLOTS; s++) {
    const ti = s - (SLOTS - n); // index into `to` for slot s (right aligned)
    const fi = s - (SLOTS - m);
    const nowIdx = ti >= 0 ? glyphIndex(to.charCodeAt(ti)) : 11;
    const oldIdx = fi >= 0 ? glyphIndex(from.charCodeAt(fi)) : 11;
    for (let r = 0; r < 7; r++) {
      const code = GLYPH_MASKS[oldIdx * 7 + r] * 512 + GLYPH_MASKS[nowIdx * 7 + r];
      out[s * 8 + r] = code;
    }
    out[32 + s] = ti >= 0 ? cx[ti] - mid : 0;
    out[36 + s] = ti >= 0 && to.charCodeAt(ti) === 46 ? 1 : 0;
  }
  return out;
}
