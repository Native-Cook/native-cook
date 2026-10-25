import { BlendMode, BlurStyle, matchFont, Skia, type SkFont, type SkImage } from '@shopify/react-native-skia';
import { Platform } from 'react-native';

import { MAX_GLYPHS } from './letterpressMotion';
import { PAPER_SKSL } from './letterpressShader';

/*
 Layout and static art, made once per screen size on the JS thread. The headline is measured at
 runtime with the font's own glyph widths (getGlyphWidths), so every letter's box, block and
 deboss line up on any device; its mask (crisp in red, blurred in green) is drawn once.
 */

export const SERIF = Platform.select({ ios: 'Georgia', default: 'serif' });
export const HEADLINE = ['Made by', 'hand.'];
export const SECOND = ['Twelve prints on 600 gsm cotton,', 'numbered and signed by the press.'];
const MARGIN = 28;
// The edition's spec list under the second line: three rows.
export const SPECS_GAP = 30;
export const SPECS_H = 3 * 38;
const PAD = 14;

export type Glyph = { ch: string; x: number; baseline: number; w: number };

export type Layout = {
  width: number;
  height: number;
  chromeTop: number;
  kickerTop: number;
  size: number;
  glyphs: Glyph[];
  /** The headline's region (its mask and the deboss overlay cover it). */
  region: { x: number; y: number; w: number; h: number };
  /** Each glyph's box, relative to the region: [x0, y0, x1, y1]. */
  boxes: number[][];
  /** Where line 1's boxes end and line 2's begin, relative to the region. */
  split: number;
  second: { x: number; baselines: number[]; size: number; width: number };
  specsTop: number;
  detailsTop: number;
  buttonTop: number;
  hintTop: number;
};

function widths(font: SkFont, text: string) {
  return font.getGlyphWidths(font.getGlyphIDs(text));
}

export function headlineFont(size: number) {
  return matchFont({ fontFamily: SERIF, fontSize: size, fontWeight: 'bold' });
}

export function secondFont(size: number) {
  return matchFont({ fontFamily: SERIF, fontSize: size, fontStyle: 'italic' });
}

export function makeLayout(width: number, height: number, top: number, bottom: number): Layout {
  const chromeTop = top + 12;
  const hintTop = height - Math.max(bottom, 12) - 22;
  const buttonTop = hintTop - 14 - 56;
  const detailsTop = buttonTop - 18 - 22;
  // Fit the headline to the width (measured at 100 pt, then scaled).
  const probe = headlineFont(100);
  const widest = Math.max(...HEADLINE.map((line) => widths(probe, line).reduce((a, b) => a + b, 0)));
  const size = Math.floor(Math.min(92, ((width - MARGIN * 2) / widest) * 100));
  const font = headlineFont(size);
  const secondSize = 16;
  const sfont = secondFont(secondSize);
  // The whole text block, centred between the chrome and the details.
  const blockH = 30 + size * 0.72 + size * 1.0 + 46 + 24 + 8 + SPECS_GAP + SPECS_H;
  const room0 = chromeTop + 48;
  const room1 = detailsTop - 24;
  const kickerTop = Math.max(room0, room0 + (room1 - room0 - blockH) / 2);
  const b1 = kickerTop + 30 + size * 0.72;
  const b2 = b1 + size * 1.0;
  const glyphs: Glyph[] = [];
  HEADLINE.forEach((line, row) => {
    const ws = widths(font, line);
    let x = MARGIN;
    for (let i = 0; i < line.length; i += 1) {
      if (line[i] !== ' ') glyphs.push({ ch: line[i], x, baseline: row === 0 ? b1 : b2, w: ws[i] });
      x += ws[i];
    }
  });
  const region = { x: MARGIN - PAD, y: b1 - size * 0.8 - PAD, w: width - (MARGIN - PAD) * 2, h: 0 };
  region.h = b2 + size * 0.24 + PAD - region.y;
  // Boxes of the two lines meet halfway between line 1's descenders and line 2's ascenders, so a
  // blurred edge always takes its own line's depth.
  const split = (b1 + size * 0.22 + b2 - size * 0.76) / 2;
  const boxes = glyphs.slice(0, MAX_GLYPHS).map((g) => [
    g.x - region.x,
    (g.baseline === b1 ? region.y : split) - region.y,
    g.x + g.w - region.x,
    (g.baseline === b1 ? split : region.y + region.h) - region.y,
  ]);
  const s1 = b2 + 46;
  const secondWidth = Math.max(...SECOND.map((l) => widths(sfont, l).reduce((a, b) => a + b, 0)));
  return {
    width,
    height,
    chromeTop,
    kickerTop,
    size,
    glyphs,
    region,
    boxes,
    split: split - region.y,
    second: { x: MARGIN, baselines: [s1, s1 + 24], size: secondSize, width: secondWidth },
    specsTop: s1 + 24 + 8 + SPECS_GAP,
    detailsTop,
    buttonTop,
    hintTop,
  };
}

const paperEffect = Skia.RuntimeEffect.Make(PAPER_SKSL);

/** The paper, baked into one image at `scale` pixels per point. */
export function bakePaper(l: Layout, scale: number): SkImage | null {
  if (!paperEffect) return null;
  const surface = Skia.Surface.MakeOffscreen(Math.ceil(l.width * scale), Math.ceil(l.height * scale));
  if (!surface) return null;
  const canvas = surface.getCanvas();
  canvas.scale(scale, scale);
  const paint = Skia.Paint();
  paint.setShader(paperEffect.makeShader([l.width, l.height]));
  canvas.drawRect(Skia.XYWHRect(0, 0, l.width, l.height), paint);
  surface.flush();
  const image = surface.makeImageSnapshot();
  return image.makeNonTextureImage() ?? image;
}

/** The headline's mask over its region: red = crisp letters, green / blue = line 1 / 2 blurred (heights). */
export function buildMask(l: Layout, scale: number): SkImage | null {
  const { region } = l;
  const surface = Skia.Surface.MakeOffscreen(Math.ceil(region.w * scale), Math.ceil(region.h * scale));
  if (!surface) return null;
  const font = headlineFont(l.size);
  const c = surface.getCanvas();
  c.clear(Skia.Color('black'));
  c.scale(scale, scale);
  c.translate(-region.x, -region.y);
  const crisp = Skia.Paint();
  crisp.setAntiAlias(true);
  crisp.setColor(Skia.Color('#ff0000'));
  for (const g of l.glyphs) c.drawText(g.ch, g.x, g.baseline, crisp, font);
  const layer = Skia.Paint();
  layer.setBlendMode(BlendMode.Plus);
  c.saveLayer(layer);
  // Each line's blur in its own channel (green, blue), so a blurred edge reaching into the other
  // line never borrows that line's depth.
  const first = l.glyphs[0]?.baseline ?? 0;
  const soft = Skia.Paint();
  soft.setAntiAlias(true);
  soft.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 1.3, true));
  for (const g of l.glyphs) {
    soft.setColor(Skia.Color(g.baseline === first ? '#00ff00' : '#0000ff'));
    c.drawText(g.ch, g.x, g.baseline, soft, font);
  }
  c.restore();
  surface.flush();
  const image = surface.makeImageSnapshot();
  return image.makeNonTextureImage() ?? image;
}
