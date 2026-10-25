import {
  BlendMode,
  BlurStyle,
  ClipOp,
  matchFont,
  PaintStyle,
  type SkCanvas,
  type SkFont,
  type SkImage,
  type SkPicture,
  Skia,
  TileMode,
} from '@shopify/react-native-skia';
import { Platform } from 'react-native';

import { CARD, INK_BOX, PAD, RING, TOOL, type Pose } from './postcardMotion';

/** Noise is coloured; this makes it grey so grain reads as texture, not as confetti. */
const GRAY = [0.33, 0.34, 0.33, 0, 0, 0.33, 0.34, 0.33, 0, 0, 0.33, 0.34, 0.33, 0, 0, 0, 0, 0, 1, 0];

// Static art, made once on the JS thread: the desk with its ink pad (a picture), the postmark's
// rubber face as a mask (an image the ink shader reads) and the handstamp seen from above (an
// image the overlay places every frame). `drawTool` runs on the UI thread.

const SANS = Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' });
const BAKE = 2;

function color(hex: string) {
  return Skia.Color(hex);
}

function linear(x0: number, y0: number, x1: number, y1: number, colors: string[], pos?: number[]) {
  return Skia.Shader.MakeLinearGradient({ x: x0, y: y0 }, { x: x1, y: y1 }, colors.map(color), pos ?? null, TileMode.Clamp);
}

function radial(x: number, y: number, r: number, colors: string[], pos?: number[]) {
  return Skia.Shader.MakeRadialGradient({ x, y }, r, colors.map(color), pos ?? null, TileMode.Clamp);
}

function blurred(hex: string, alpha: number, sigma: number) {
  const p = Skia.Paint();
  p.setColor(color(hex));
  p.setAlphaf(alpha);
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma, true));
  return p;
}

/** Glyphs set on a circle: centred on `mid` (radians, screen angles), upright for the reader. */
function ringText(c: SkCanvas, text: string, cx: number, cy: number, r: number, mid: number, font: SkFont, paint: ReturnType<typeof Skia.Paint>, bottom: boolean) {
  const ids = font.getGlyphIDs(text);
  const widths = font.getGlyphWidths(ids);
  const track = 1.4;
  let total = 0;
  for (const w of widths) total += w + track;
  total -= track;
  // Arc length to angle; the top arc runs clockwise, the bottom one counter-clockwise.
  const dir = bottom ? -1 : 1;
  let a = mid - (dir * total) / 2 / r;
  for (let i = 0; i < text.length; i += 1) {
    const w = widths[i];
    const at = a + (dir * w) / 2 / r;
    c.save();
    c.translate(cx + Math.cos(at) * r, cy + Math.sin(at) * r);
    c.rotate(((at + (bottom ? -Math.PI / 2 : Math.PI / 2)) * 180) / Math.PI, 0, 0);
    c.drawText(text[i], -w / 2, bottom ? font.getSize() * 0.36 : font.getSize() * 0.36, paint, font);
    c.restore();
    a += (dir * (w + track)) / r;
  }
}

/** The postmark's rubber face: a date ring "LISBOA · 24 OCT" and five wavy bars, white on clear. */
export function bakeMask(): SkImage {
  const w = INK_BOX.w;
  const h = INK_BOX.h;
  const surface = Skia.Surface.Make(Math.ceil(w * BAKE), Math.ceil(h * BAKE))!;
  const c = surface.getCanvas();
  c.scale(BAKE, BAKE);
  const ink = Skia.Paint();
  ink.setColor(color('#FFFFFF'));
  const stroke = Skia.Paint();
  stroke.setColor(color('#FFFFFF'));
  stroke.setStyle(PaintStyle.Stroke);
  stroke.setStrokeWidth(2.2);
  c.drawCircle(RING.x, RING.y, RING.r, stroke);
  stroke.setStrokeWidth(1.5);
  c.drawCircle(RING.x, RING.y, RING.inner, stroke);
  const ringFont = matchFont({ fontFamily: SANS, fontSize: 8.4, fontWeight: '700' });
  const mid = (RING.r + RING.inner) / 2;
  ringText(c, 'LISBOA', RING.x, RING.y, mid, -Math.PI / 2, ringFont, ink, false);
  ringText(c, '24 · OCT · 26', RING.x, RING.y, mid, Math.PI / 2, ringFont, ink, true);
  // Two little stars between the words, drawn (the font has no star).
  for (const side of [-1, 1]) {
    const sx = RING.x + side * mid;
    const sy = RING.y;
    const star = Skia.PathBuilder.Make();
    for (let k = 0; k < 10; k += 1) {
      const rr = k % 2 === 0 ? 2.6 : 1.1;
      const a = -Math.PI / 2 + (k * Math.PI) / 5;
      if (k === 0) star.moveTo(sx + Math.cos(a) * rr, sy + Math.sin(a) * rr);
      else star.lineTo(sx + Math.cos(a) * rr, sy + Math.sin(a) * rr);
    }
    star.close();
    c.drawPath(star.build(), ink);
  }
  const hourFont = matchFont({ fontFamily: SANS, fontSize: 10, fontWeight: '700' });
  const ids = hourFont.getGlyphIDs('13·40');
  let tw = 0;
  for (const g of hourFont.getGlyphWidths(ids)) tw += g;
  c.drawText('13·40', RING.x - tw / 2, RING.y + 3.6, ink, hourFont);
  // Five wavy cancellation bars, starting just outside the ring.
  const bars = Skia.Paint();
  bars.setColor(color('#FFFFFF'));
  bars.setStyle(PaintStyle.Stroke);
  bars.setStrokeWidth(2.3);
  for (let i = 0; i < 5; i += 1) {
    const y0 = RING.y + (i - 2) * 9.4;
    const reach = Math.sqrt(Math.max(0, RING.r * RING.r - (y0 - RING.y) * (y0 - RING.y)));
    const x0 = RING.x + reach + 4;
    const path = Skia.PathBuilder.Make();
    for (let x = x0; x <= w - 4; x += 1.5) {
      const y = y0 + Math.sin(((x - x0) / 18) * Math.PI * 2) * 2.6;
      if (x === x0) path.moveTo(x, y);
      else path.lineTo(x, y);
    }
    c.drawPath(path.build(), bars);
  }
  const image = surface.makeImageSnapshot();
  surface.dispose();
  return image;
}

/** The handstamp from above: a rubber-faced block on a walnut mount, a turned knob, its edges lit. */
export function bakeTool(): SkImage {
  const pad = 6;
  const w = TOOL.w + pad * 2;
  const h = TOOL.h + pad * 2;
  const surface = Skia.Surface.Make(Math.ceil(w * BAKE), Math.ceil(h * BAKE))!;
  const c = surface.getCanvas();
  c.scale(BAKE, BAKE);
  c.translate(pad, pad);
  // The rubber shows as a dark rim around the mount.
  const rubber = Skia.RRectXY(Skia.XYWHRect(0, 0, TOOL.w, TOOL.h), 12, 12);
  const r = Skia.Paint();
  r.setShader(linear(0, 0, 0, TOOL.h, ['#2B2E3A', '#1B1D26', '#11131A']));
  c.drawRRect(rubber, r);
  const mount = Skia.RRectXY(Skia.XYWHRect(4, 3, TOOL.w - 8, TOOL.h - 8), 10, 10);
  const wood = Skia.Paint();
  wood.setShader(linear(0, 0, TOOL.w, TOOL.h, ['#9A6740', '#7A4D2D', '#5E3A21', '#7C5132'], [0, 0.4, 0.75, 1]));
  c.drawRRect(mount, wood);
  c.save();
  c.clipRRect(mount, ClipOp.Intersect, true);
  const grain = Skia.Paint();
  grain.setShader(Skia.Shader.MakeFractalNoise(0.006, 0.28, 3, 17, 0, 0));
  grain.setColorFilter(Skia.ColorFilter.MakeMatrix(GRAY));
  grain.setBlendMode(BlendMode.Overlay);
  grain.setAlphaf(0.7);
  c.drawRect(Skia.XYWHRect(0, 0, TOOL.w, TOOL.h), grain);
  const lit = Skia.Paint();
  lit.setShader(linear(0, 3, 0, 14, ['rgba(255,226,190,0.45)', 'rgba(255,226,190,0)']));
  c.drawRect(Skia.XYWHRect(0, 3, TOOL.w, 11), lit);
  c.restore();
  // A brass date plate on the mount.
  const plate = Skia.Paint();
  plate.setShader(linear(12, TOOL.h - 24, 52, TOOL.h - 12, ['#F1D592', '#B98A3E', '#E3C27A']));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(12, TOOL.h - 26, 36, 12), 2, 2), plate);
  // The turned knob: shadow, body, rings and a highlight.
  const kx = TOOL.w / 2 + 6;
  const ky = TOOL.h / 2 - 2;
  c.drawCircle(kx + 2, ky + 4, TOOL.knob, blurred('#000000', 0.5, 5));
  const knob = Skia.Paint();
  knob.setShader(radial(kx - 9, ky - 11, TOOL.knob * 1.5, ['#E3B07A', '#A86D3E', '#6E4223', '#4A2B16'], [0, 0.35, 0.75, 1]));
  c.drawCircle(kx, ky, TOOL.knob, knob);
  const ring = Skia.Paint();
  ring.setStyle(PaintStyle.Stroke);
  ring.setStrokeWidth(1);
  ring.setColor(color('#3A2212'));
  ring.setAlphaf(0.45);
  for (const rr of [TOOL.knob * 0.42, TOOL.knob * 0.7, TOOL.knob - 1.5]) c.drawCircle(kx, ky, rr, ring);
  const spec = Skia.Paint();
  spec.setShader(radial(kx - 10, ky - 12, 12, ['rgba(255,240,215,0.75)', 'rgba(255,240,215,0)']));
  c.drawCircle(kx - 10, ky - 12, 12, spec);
  const image = surface.makeImageSnapshot();
  surface.dispose();
  return image;
}

export const TOOL_PAD = 6;
export { BAKE as TOOL_BAKE };

/** Draws the handstamp at its pose: two shadows that spread as it rises, then the block itself. */
export function drawTool(c: SkCanvas, tool: SkImage, p: Pose) {
  'worklet';
  const lift = p.h;
  const scale = (1 + 0.1 * lift) * (1 - 0.025 * p.squash);
  const w = (TOOL.w + TOOL_PAD * 2) * scale;
  const h = (TOOL.h + TOOL_PAD * 2) * scale;
  // Ambient shadow: further away and softer the higher the tool is.
  const amb = Skia.Paint();
  amb.setColor(Skia.Color('#05070C'));
  amb.setAlphaf(0.42 - 0.16 * lift);
  amb.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 5 + 14 * lift, false));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(p.x - (TOOL.w * scale) / 2 + 6 + 10 * lift, p.y - (TOOL.h * scale) / 2 + 6 + 22 * lift, TOOL.w * scale, TOOL.h * scale), 14, 14), amb);
  // Contact shadow: tight while it rests, gone once it is up.
  const contact = Skia.Paint();
  contact.setColor(Skia.Color('#05070C'));
  contact.setAlphaf(0.5 * (1 - Math.min(1, lift * 3)));
  contact.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2, false));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(p.x - TOOL.w / 2, p.y - TOOL.h / 2 + 2, TOOL.w, TOOL.h), 12, 12), contact);
  const src = Skia.XYWHRect(0, 0, tool.width(), tool.height());
  c.drawImageRect(tool, src, Skia.XYWHRect(p.x - w / 2, p.y - h / 2, w, h), Skia.Paint());
}

/** The desk: indigo linen with a weave, a spare card under the postcard, and the ink pad in its tin. */
export function recordDesk(width: number, height: number, ox: number, oy: number, k: number): SkPicture {
  const rec = Skia.PictureRecorder();
  const c = rec.beginRecording(Skia.XYWHRect(0, 0, width, height));
  const base = Skia.Paint();
  base.setShader(linear(0, 0, width * 0.4, height, ['#1E3150', '#18263F', '#101A2C'], [0, 0.5, 1]));
  c.drawRect(Skia.XYWHRect(0, 0, width, height), base);
  // Linen: a fine crossed weave and soft slubs.
  const weave = Skia.Paint();
  weave.setShader(Skia.Shader.MakeFractalNoise(0.9, 0.08, 2, 3, 0, 0));
  weave.setColorFilter(Skia.ColorFilter.MakeMatrix(GRAY));
  weave.setBlendMode(BlendMode.SoftLight);
  weave.setAlphaf(0.35);
  c.drawRect(Skia.XYWHRect(0, 0, width, height), weave);
  const weft = Skia.Paint();
  weft.setShader(Skia.Shader.MakeFractalNoise(0.08, 0.9, 2, 5, 0, 0));
  weft.setColorFilter(Skia.ColorFilter.MakeMatrix(GRAY));
  weft.setBlendMode(BlendMode.SoftLight);
  weft.setAlphaf(0.35);
  c.drawRect(Skia.XYWHRect(0, 0, width, height), weft);
  const lamp = Skia.Paint();
  lamp.setShader(radial(width * 0.62, oy + 200 * k, width * 1.0, ['rgba(255,214,160,0.14)', 'rgba(255,214,160,0.04)', 'rgba(255,214,160,0)'], [0, 0.5, 1]));
  c.drawRect(Skia.XYWHRect(0, 0, width, height), lamp);

  c.save();
  c.translate(ox, oy);
  c.scale(k, k);
  // A spare card peeking out under the postcard, turned a little.
  c.save();
  c.translate(CARD.x + CARD.w / 2, CARD.y + CARD.h / 2 + 10);
  c.rotate(-3.2, 0, 0);
  const spare = Skia.RRectXY(Skia.XYWHRect(-CARD.w / 2, -CARD.h / 2, CARD.w, CARD.h), 6, 6);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-CARD.w / 2 + 2, -CARD.h / 2 + 8, CARD.w, CARD.h), 8, 8), blurred('#000000', 0.45, 10));
  const spareFill = Skia.Paint();
  spareFill.setShader(linear(0, -CARD.h / 2, 0, CARD.h / 2, ['#E7DCC4', '#DCCDB0']));
  c.drawRRect(spare, spareFill);
  c.restore();

  // The ink pad: a tin rim, a felt pad soaked in ultramarine, a sheen.
  const { x, y, r } = PAD;
  // The lid, set aside under the tin.
  c.drawCircle(x + 64, y + 52, r - 2, blurred('#000000', 0.5, 9));
  const lid = Skia.Paint();
  lid.setShader(radial(x + 44, y + 30, r * 1.3, ['#D9DEE6', '#8E97A6', '#566072'], [0, 0.55, 1]));
  c.drawCircle(x + 62, y + 48, r - 4, lid);
  const lidRing = Skia.Paint();
  lidRing.setStyle(PaintStyle.Stroke);
  lidRing.setStrokeWidth(1.2);
  lidRing.setColor(color('#FFFFFF'));
  lidRing.setAlphaf(0.35);
  c.drawCircle(x + 62, y + 48, r - 12, lidRing);
  c.drawCircle(x + 3, y + 8, r + 2, blurred('#000000', 0.55, 10));
  c.drawCircle(x, y + 1.5, r + 1, blurred('#000000', 0.6, 1.5));
  const rim = Skia.Paint();
  rim.setShader(Skia.Shader.MakeSweepGradient(x, y, ['#C8CFDA', '#6E7889', '#E8ECF2', '#596374', '#C8CFDA'].map(color), null, TileMode.Clamp));
  c.drawCircle(x, y, r, rim);
  const felt = Skia.Paint();
  felt.setShader(radial(x - 14, y - 16, r * 1.1, ['#34479A', '#22306E', '#151D45'], [0, 0.55, 1]));
  c.drawCircle(x, y, r - 7, felt);
  c.save();
  c.clipPath(Skia.PathBuilder.Make().addCircle(x, y, r - 7).build(), ClipOp.Intersect, true);
  const fibres = Skia.Paint();
  fibres.setShader(Skia.Shader.MakeFractalNoise(0.7, 0.7, 3, 41, 0, 0));
  fibres.setColorFilter(Skia.ColorFilter.MakeMatrix(GRAY));
  fibres.setBlendMode(BlendMode.Overlay);
  fibres.setAlphaf(0.55);
  c.drawRect(Skia.XYWHRect(x - r, y - r, r * 2, r * 2), fibres);
  const wet = Skia.Paint();
  wet.setShader(radial(x - 18, y - 22, 26, ['rgba(190,205,255,0.35)', 'rgba(190,205,255,0)']));
  c.drawCircle(x - 18, y - 22, 26, wet);
  const inner = Skia.Paint();
  inner.setStyle(PaintStyle.Stroke);
  inner.setStrokeWidth(6);
  inner.setColor(color('#000000'));
  inner.setAlphaf(0.45);
  inner.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3, false));
  c.drawCircle(x, y, r - 7, inner);
  c.restore();
  c.restore();

  const v = Skia.Paint();
  v.setShader(radial(width / 2, height * 0.45, height * 0.75, ['rgba(0,0,0,0)', 'rgba(0,0,0,0)', 'rgba(3,6,14,0.6)'], [0, 0.55, 1]));
  c.drawRect(Skia.XYWHRect(0, 0, width, height), v);
  return rec.finishRecordingAsPicture();
}
