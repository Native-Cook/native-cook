import { Skia, type SkContourMeasure, type SkFont, type SkPath } from '@shopify/react-native-skia';

/** One word of the sign: its text and the colours its tube burns in once lit. */
export type NeonWord = {
  text: string;
  /** The fill runs from `start` (left) to `end` (right). */
  start: string;
  end: string;
  /** The halo around the letters and the light it throws on the wall. */
  glow: string;
};

export const SAMPLE_WORDS: NeonWord[] = [
  { text: 'Afterglow', start: '#ffc35c', end: '#ff4a2b', glow: '#ff6a2c' },
  { text: 'Midnight', start: '#3a6dff', end: '#40d2ff', glow: '#2f7bff' },
  { text: 'Daydream', start: '#ff5ce0', end: '#ff3c6c', glow: '#ff3bb4' },
];

export type Spark = {
  /** The tube (0 mint, 1 rose) whose head throws it, and how far round the contour that head is then. */
  layer: number;
  at: number;
  ox: number;
  oy: number;
  vx: number;
  vy: number;
  life: number;
  length: number;
  width: number;
  white: boolean;
};

export type Contour = {
  measure: SkContourMeasure;
  length: number;
  /** Where along the word the contour starts, 0 (left) to 1 (right): later contours start later. */
  delay: number;
  sparks: Spark[];
};

export type WordShape = {
  fill: SkPath;
  contours: Contour[];
  /** The word's box before it is tilted; `left` and `right` are its mid-left and mid-right tilted. */
  box: { x: number; y: number; width: number; height: number };
  left: { x: number; y: number };
  right: { x: number; y: number };
};

/** The words are laid out at this size, then all scaled by the same factor to fit the screen. */
export const UNIT = 100;
/** The sign leans up to the right. */
export const TILT = (-8 * Math.PI) / 180;

/** A 32-bit integer hash mapped to 0..<1; the same in every stack. */
export function random(n: number) {
  'worklet';
  let x = n >>> 0;
  x ^= x >>> 16;
  x = Math.imul(x, 0x7feb352d);
  x ^= x >>> 15;
  x = Math.imul(x, 0x846ca68b);
  x ^= x >>> 16;
  return (x >>> 0) / 4294967296;
}

/** Three sparks per tube head on each contour, from a seeded hash so every loop throws the same ones. */
function sparks(measure: SkContourMeasure, seed: number): Spark[] {
  const length = measure.length();
  const out: Spark[] = [];
  for (let k = 0; k < 6; k++) {
    const r = (salt: number) => random(seed * 13 + k * 7 + salt);
    const at = (Math.floor(k / 2) + 0.15 + 0.7 * r(1)) / 3;
    const [origin] = measure.getPosTan(at * length);
    const angle = r(2) * 2 * Math.PI;
    const speed = 26 + 90 * r(3);
    out.push({
      layer: k % 2,
      at,
      ox: origin.x,
      oy: origin.y,
      vx: Math.cos(angle) * speed,
      vy: Math.sin(angle) * speed - 30,
      life: 0.32 + 0.3 * r(4),
      length: 2 + 6 * r(5),
      width: 1.4 + 1.2 * r(6),
      white: r(8) < 0.6,
    });
  }
  return out;
}

/**
 * The outlines of the sign's words laid out for a screen: every contour of every glyph (the tubes
 * trace these), the filled word, and the sparks thrown from the pen heads. Glyphs are placed by their
 * advances, with no shaping, so every stack lays the word out alike.
 */
export function layoutWords(font: SkFont, texts: string[], width: number, height: number): WordShape[] {
  const outlines = texts.map((text) => Skia.Path.MakeFromText(text, 0, 0, font) ?? Skia.Path.Make());
  const bounds = outlines.map((path) => path.computeTightBounds());

  // One scale for every word, so the widest one (tilted) fits between 28 pt margins.
  const angle = Math.abs(TILT);
  const room = width - 56;
  const widest = Math.max(...bounds.map((b) => b.width * Math.cos(angle) + b.height * Math.sin(angle)), 1);
  const scale = Math.min(room / widest, 1.9);
  const cx = width / 2;
  const cy = height * 0.45;
  const cos = Math.cos(TILT);
  const sin = Math.sin(TILT);

  return outlines.map((outline, index) => {
    const b = bounds[index];
    const midX = b.x + b.width / 2;
    const midY = b.y + b.height / 2;
    // p → centre + rotate(tilt) · scale · (p − mid)
    const a = scale * cos;
    const c = -scale * sin;
    const bb = scale * sin;
    const d = scale * cos;
    const matrix = [a, c, cx - (a * midX + c * midY), bb, d, cy - (bb * midX + d * midY), 0, 0, 1];
    const fill = outline.copy().transform(matrix);

    const contours: Contour[] = [];
    const rawIter = Skia.ContourMeasureIter(outline, false, 1);
    const iter = Skia.ContourMeasureIter(fill, false, 1);
    let count = 0;
    for (let raw = rawIter.next(), measure = iter.next(); raw && measure; raw = rawIter.next(), measure = iter.next()) {
      const rawLength = raw.length();
      if (rawLength <= 0.5) continue;
      const rawBounds = raw.getSegment(0, rawLength, true).computeTightBounds();
      const delay = (rawBounds.x - b.x) / Math.max(b.width, 1);
      contours.push({ measure, length: measure.length(), delay, sparks: sparks(measure, index * 997 + count * 31) });
      count++;
    }

    const box = { x: cx - (b.width * scale) / 2, y: cy - (b.height * scale) / 2, width: b.width * scale, height: b.height * scale };
    const tilt = (x: number, y: number) => ({ x: cx + cos * (x - cx) - sin * (y - cy), y: cy + sin * (x - cx) + cos * (y - cy) });
    return { fill, contours, box, left: tilt(box.x, cy), right: tilt(box.x + box.width, cy) };
  });
}
