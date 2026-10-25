import { BlendMode, BlurStyle, matchFont, Skia, type SkCanvas, type SkFont, type SkImage, type SkPaint } from '@shopify/react-native-skia';
import { Platform } from 'react-native';

import { PAPER_SKSL } from './foilStampShader';

/*
 Static art, made once per layout on the JS thread: the paper background (a shader baked into an
 image) and the seal's mask (the monogram drawn crisp in red and blurred in green). Per frame the
 screen only changes uniforms and transforms.
 */

export const SERIF = Platform.select({ ios: 'Georgia', default: 'serif' });

/** Margin (pt) round the seal inside its mask, so the blur and the dip have room. */
export const MASK_PAD = 14;

export type Layout = {
  width: number;
  height: number;
  /** Header text tops. */
  headerTop: number;
  card: { x: number; y: number; w: number; h: number };
  seal: { x: number; y: number; r: number };
  rowsTop: number;
  button: { x: number; y: number; r: number };
  hintTop: number;
};

const HEADER = 84;
const ROWS = 124;
const BUTTON_R = 52;
const BUTTON_BLOCK = BUTTON_R * 2 + 30;

export function makeLayout(width: number, height: number, top: number, bottom: number): Layout {
  const headerTop = top + 14;
  const bottomEdge = height - Math.max(bottom, 12) - 10;
  const room = bottomEdge - (headerTop + HEADER);
  // Gaps: card, rows and the stamp share the room; the card takes what is left, within limits.
  const gap = room > 560 ? 22 : 14;
  const cardH = Math.max(196, Math.min(330, room - ROWS - BUTTON_BLOCK - gap * 3));
  const spare = Math.max(0, room - cardH - ROWS - BUTTON_BLOCK - gap * 3);
  const cardY = headerTop + HEADER + gap + spare * 0.35;
  const cardW = Math.min(width - 48, 400);
  const card = { x: (width - cardW) / 2, y: cardY, w: cardW, h: cardH };
  const r = Math.min(70, cardH * 0.28);
  const rowsTop = cardY + cardH + gap + spare * 0.2;
  const buttonY = rowsTop + ROWS + gap + spare * 0.3 + BUTTON_R;
  return {
    width,
    height,
    headerTop,
    card,
    seal: { x: width / 2, y: cardY + cardH * 0.47, r },
    rowsTop,
    button: { x: width / 2, y: buttonY, r: BUTTON_R },
    hintTop: buttonY + BUTTON_R + 12,
  };
}

const paperEffect = Skia.RuntimeEffect.Make(PAPER_SKSL);

/** The table and the card, baked into one image at `scale` pixels per point. */
export function bakePaper(l: Layout, scale: number): SkImage | null {
  if (!paperEffect) return null;
  const surface = Skia.Surface.MakeOffscreen(Math.ceil(l.width * scale), Math.ceil(l.height * scale));
  if (!surface) return null;
  const canvas = surface.getCanvas();
  canvas.scale(scale, scale);
  const paint = Skia.Paint();
  paint.setShader(
    paperEffect.makeShader([l.width, l.height, l.card.x, l.card.y, l.card.w, l.card.h, 22]),
  );
  canvas.drawRect(Skia.XYWHRect(0, 0, l.width, l.height), paint);
  surface.flush();
  const image = surface.makeImageSnapshot();
  return image.makeNonTextureImage() ?? image;
}

function textWidth(font: SkFont, text: string) {
  const ids = font.getGlyphIDs(text);
  return font.getGlyphWidths(ids).reduce((a, b) => a + b, 0);
}

/** The monogram, centred on (0, 0): a rope border, a beaded ring, a thin ring, "E" & "V". */
function drawMonogram(c: SkCanvas, paint: SkPaint, R: number, letters: SkFont, amp: SkFont) {
  const stroke = paint.copy();
  stroke.setStyle(1);
  stroke.setStrokeWidth(R * 0.045);
  c.drawCircle(0, 0, R * 0.94, stroke);
  stroke.setStrokeWidth(R * 0.016);
  c.drawCircle(0, 0, R * 0.7, stroke);
  // Beads between the two rings.
  const beads = 36;
  for (let i = 0; i < beads; i += 1) {
    const a = (i / beads) * Math.PI * 2;
    c.drawCircle(Math.cos(a) * R * 0.82, Math.sin(a) * R * 0.82, R * 0.028, paint);
  }
  // Two small four-point stars above and below the letters.
  for (const y of [-R * 0.5, R * 0.52]) {
    const s = R * 0.07;
    const star = Skia.PathBuilder.Make();
    star.moveTo(0, y - s);
    star.quadTo(s * 0.18, y - s * 0.18, s, y);
    star.quadTo(s * 0.18, y + s * 0.18, 0, y + s);
    star.quadTo(-s * 0.18, y + s * 0.18, -s, y);
    star.quadTo(-s * 0.18, y - s * 0.18, 0, y - s);
    star.close();
    c.drawPath(star.build(), paint);
  }
  const e = 'E';
  const v = 'V';
  const ew = textWidth(letters, e);
  const vw = textWidth(letters, v);
  const aw = textWidth(amp, '&');
  const base = R * 0.2;
  const gap = R * 0.02;
  const total = ew + aw + vw + gap * 2;
  let x = -total / 2;
  c.drawText(e, x, base, paint, letters);
  x += ew + gap;
  c.drawText('&', x, base - R * 0.04, paint, amp);
  x += aw + gap;
  c.drawText(v, x, base, paint, letters);
}

/** The seal's mask: red = crisp monogram, green = blurred (the height map). Square, R + pad. */
export function buildSealMask(R: number, scale: number): SkImage | null {
  const side = Math.ceil((R + MASK_PAD) * 2 * scale);
  const surface = Skia.Surface.MakeOffscreen(side, side);
  if (!surface) return null;
  const letters = matchFont({ fontFamily: SERIF, fontSize: R * 0.6, fontWeight: 'bold' });
  const amp = matchFont({ fontFamily: SERIF, fontSize: R * 0.34, fontStyle: 'italic' });
  const c = surface.getCanvas();
  c.clear(Skia.Color('black'));
  c.save();
  c.scale(scale, scale);
  c.translate(R + MASK_PAD, R + MASK_PAD);
  const crisp = Skia.Paint();
  crisp.setAntiAlias(true);
  crisp.setColor(Skia.Color('#ff0000'));
  drawMonogram(c, crisp, R, letters, amp);
  // The blurred copy goes into a layer added on top, so it fills green without touching red.
  const layer = Skia.Paint();
  layer.setBlendMode(BlendMode.Plus);
  c.saveLayer(layer);
  const soft = Skia.Paint();
  soft.setAntiAlias(true);
  soft.setColor(Skia.Color('#00ff00'));
  soft.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 1.5, true));
  drawMonogram(c, soft, R, letters, amp);
  c.restore();
  c.restore();
  surface.flush();
  const image = surface.makeImageSnapshot();
  return image.makeNonTextureImage() ?? image;
}

/** A check mark centred on (0, 0), about 22 pt wide, drawn as one stroke left to right. */
export function checkPath(cx: number, cy: number) {
  const p = Skia.PathBuilder.Make();
  p.moveTo(cx - 10, cy + 0.5);
  p.cubicTo(cx - 7, cy + 3, cx - 5, cy + 5.5, cx - 3.5, cy + 7.5);
  p.cubicTo(cx + 1, cy + 0.5, cx + 5.5, cy - 5, cx + 11, cy - 9);
  return p.build();
}
