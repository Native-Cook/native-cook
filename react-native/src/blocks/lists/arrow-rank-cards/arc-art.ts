import { PaintStyle, Skia, type SkFont, type SkImage } from '@shopify/react-native-skia';
import { C, DECK, G } from './arc-theme';
import type { ArcFrame } from './arc-motion';

export const textWidth = (font: SkFont, text: string) => {
  const glyphs = font.getGlyphIDs(text);
  return font.getGlyphWidths(glyphs).reduce((a, b) => a + b, 0);
};

/** Draw once after the real font loads. Every strip samples the same, unbroken coordinate space. */
export function makeFace(index: number, titleFont: SkFont, artistFont: SkFont, chipFont: SkFont): SkImage {
  const face = DECK[index];
  const surface = Skia.Surface.MakeOffscreen(G.panelW * 3, G.panelH * 3);
  if (!surface) throw new Error('Arrow Rank Cards: panel surface unavailable');
  const c = surface.getCanvas();
  c.scale(3, 3);
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(face.panel));
  c.drawRect(Skia.XYWHRect(0, 0, G.panelW, G.panelH), p);
  p.setColor(Skia.Color(C.ink));
  // Inter Tight's true cap height is 1490/2048 em; baselines retain the measured line tops.
  c.drawText(face.title[0], 11, 16 + 34 * 1490 / 2048, p, titleFont);
  c.drawText(face.title[1], 11, 53 + 34 * 1490 / 2048, p, titleFont);
  p.setColor(Skia.Color(face.muted));
  c.drawText(face.artist, 11, 94 + 15 * 1490 / 2048, p, artistFont);
  face.chips.forEach((chip, j) => {
    const x = 42 + j * 71;
    p.setColor(Skia.Color(chip.color));
    p.setStyle(PaintStyle.Fill);
    c.drawCircle(x, 153, 31, p);
    p.setStyle(PaintStyle.Stroke);
    p.setStrokeWidth(2);
    p.setColor(Skia.Color('#FFFFFF59'));
    c.drawCircle(x, 153, 30, p);
    p.setStyle(PaintStyle.Fill);
    p.setColor(Skia.Color(C.ink));
    c.drawText(chip.label, x - textWidth(chipFont, chip.label) / 2, 153 + 30 * 1490 / 4096, p, chipFont);
  });
  surface.flush();
  const snapshot = surface.makeImageSnapshot();
  surface.dispose();
  return snapshot;
}

/**
 * A road-sign turn arrow, always 22 pt thick: straight tail, a 28 pt-radius bend, straight run into the head.
 * The head's heading leads the tail's, so a turn passes through the "turn ahead" sign shape.
 */
export const arrowPath = (f: ArcFrame) => {
  'worklet';
  const head = (f.head + f.shake) * Math.PI / 180;
  // The tail never trails the head by more than 75°: past that the bend curls into a hook, not a sign.
  const lag = Math.max(-75, Math.min(75, f.head - f.tail)) * Math.PI / 180;
  const tail = head - lag;
  const hx = Math.cos(head), hy = Math.sin(head), tx = Math.cos(tail), ty = Math.sin(tail);
  const d = lag, s = d < 0 ? -1 : 1, R = 28;
  // Pulled back along its heading so the stretched arrow (+36 pt) keeps 14 pt clear of every panel edge.
  const back = 22 * Math.max(0, -hx) + 32 * Math.max(0, hy) - 14 * Math.max(0, -hy);
  const px = 73 - back * hx, py = 313 - back * hy;
  const arc = R * Math.abs(d);
  const shaft = 97.443651 + f.stretch;
  const straight = Math.max(0, shaft - arc);
  const lt = straight * 0.5, lh = straight - lt;
  const ax = px - 57 * tx + f.beckon * tx, ay = py - 57 * ty + f.beckon * ty;
  const e1x = ax + lt * tx, e1y = ay + lt * ty;
  const ox = e1x - s * R * ty, oy = e1y + s * R * tx;
  const e2x = ox + s * R * hy, e2y = oy - s * R * hx;
  const bx = e2x + lh * hx, by = e2y + lh * hy;
  const k = 4 / 3 * Math.tan(Math.abs(d) / 4) * R;
  const b = Skia.PathBuilder.Make();
  b.moveTo(ax, ay);
  b.lineTo(e1x, e1y);
  if (arc > 0.01) b.cubicTo(e1x + k * tx, e1y + k * ty, e2x - k * hx, e2y - k * hy, e2x, e2y);
  b.lineTo(bx, by);
  b.moveTo(bx - 37.221826 * hx + 37.221826 * hy, by - 37.221826 * hy - 37.221826 * hx);
  b.lineTo(bx, by);
  b.lineTo(bx - 37.221826 * hx - 37.221826 * hy, by - 37.221826 * hy + 37.221826 * hx);
  return b.detach();
};
