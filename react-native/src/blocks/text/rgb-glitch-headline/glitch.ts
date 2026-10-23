import { Skia, type SkFont, type SkPath } from '@shopify/react-native-skia';

/** Letter spacing, in ems. */
const TRACKING = 0.06;
/** Baseline to baseline, in ems. */
const LEADING = 1.02;
const MAX_SIZE = 78;
const UNIT = 100;

export type Block = {
  path: SkPath;
  /** Top of the first line's capitals and the last baseline. */
  top: number;
  bottom: number;
  capHeight: number;
  centreX: number;
  centreY: number;
};

/**
 * The headline as one path, laid out for a screen width: centred lines of glyphs placed by their
 * advances plus a fixed tracking, so every stack lays it out alike. `font` is the face at 100 pt.
 */
export function layoutBlock(font: SkFont, lines: string[], width: number, centreY: number): Block {
  const shaped = lines.map((text) => {
    const path = Skia.Path.Make();
    let x = 0;
    for (const ch of text) {
      const glyph = Skia.Path.MakeFromText(ch, x, 0, font);
      if (glyph) path.addPath(glyph);
      x += font.getGlyphWidths(font.getGlyphIDs(ch))[0] + TRACKING * UNIT;
    }
    return { path, width: Math.max(x - TRACKING * UNIT, 1) };
  });
  const widest = Math.max(...shaped.map((s) => s.width));
  const size = Math.min(((width - 48) / widest) * UNIT, MAX_SIZE);
  const scale = size / UNIT;
  const capHeight = (Skia.Path.MakeFromText('H', 0, 0, font)?.computeTightBounds().height ?? 70) * scale;
  const step = size * LEADING;
  const height = capHeight + step * (lines.length - 1);
  const top = centreY - height / 2;

  const path = Skia.Path.Make();
  shaped.forEach((line, index) => {
    const baseline = top + capHeight + step * index;
    const x = (width - line.width * scale) / 2;
    path.addPath(line.path, Skia.Matrix().translate(x, baseline).scale(scale, scale));
  });
  return { path, top, bottom: top + height, capHeight, centreX: width / 2, centreY };
}

export type Band = { y0: number; y1: number; dx: number; hidden: boolean };
export type Strip = Band;

/**
 * One stepped frame of interference: horizontal bands of the headline knocked sideways, a red and a
 * cyan copy split apart, sometimes a shear, a band gone, and thin noise lines across.
 */
export type Frame = {
  bands: Band[];
  /** How far the red copy sits right of the white one (the cyan one as far left). */
  split: number;
  /** The whole block's sideways jolt. */
  jolt: number;
  /** Shear of the whole block (tan of the angle; positive leans the tops right). */
  shear: number;
  /** Noise lines: y and opacity. */
  noise: [number, number][];
};

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

/** The frame for one step of a burst. `amount` 0…1 scales every offset. */
export function makeFrame(seed: number, step: number, amount: number, top: number, bottom: number, cap: number): Frame {
  'worklet';
  const r = (k: number) => random((Math.imul(seed, 7919) + Math.imul(step, 131) + k) | 0);
  const height = bottom - top;
  const bands: Band[] = [];
  const count = 2 + Math.floor(r(0) * 4);
  for (let i = 0; i < count; i++) {
    const centre = top + height * r(10 + i * 4);
    const h = cap * (0.06 + 0.5 * r(11 + i * 4));
    const dx = (r(12 + i * 4) < 0.5 ? -1 : 1) * cap * (0.08 + 0.42 * r(13 + i * 4)) * amount;
    bands.push({ y0: centre - h / 2, y1: centre + h / 2, dx, hidden: false });
  }
  if (r(30) < 0.3 * amount) bands[0].hidden = true;
  const noise: [number, number][] = [];
  const lines = Math.floor(r(36) * 3 * amount);
  for (let j = 0; j < lines; j++) noise.push([top - cap * 0.4 + (height + cap * 0.8) * r(40 + j), 0.15 + 0.25 * r(44 + j)]);
  return {
    bands,
    split: (r(32) < 0.5 ? -1 : 1) * cap * (0.04 + 0.1 * r(31)) * amount,
    shear: r(33) < 0.22 * amount ? Math.tan((10 * Math.PI) / 180) : 0,
    jolt: cap * 0.06 * (r(35) * 2 - 1) * amount,
    noise,
  };
}

/** The block cut into horizontal strips at every band edge; a strip takes the offset of the last band that covers it. */
export function strips(frame: Frame, top: number, bottom: number): Strip[] {
  'worklet';
  const edges = [top, bottom];
  for (const b of frame.bands) {
    edges.push(Math.min(Math.max(b.y0, top), bottom));
    edges.push(Math.min(Math.max(b.y1, top), bottom));
  }
  edges.sort((a, b) => a - b);
  const out: Strip[] = [];
  for (let i = 0; i < edges.length - 1; i++) {
    if (edges[i + 1] - edges[i] <= 0.01) continue;
    const mid = (edges[i] + edges[i + 1]) / 2;
    let dx = 0;
    let hidden = false;
    for (const b of frame.bands) {
      if (mid >= b.y0 && mid < b.y1) {
        dx = b.dx;
        hidden = b.hidden;
      }
    }
    out.push({ y0: edges[i], y1: edges[i + 1], dx, hidden });
  }
  return out;
}
