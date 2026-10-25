import { BlendMode, BlurStyle, matchFont, Skia, type SkCanvas, type SkFont, type SkImage, type SkPaint, type SkPath } from '@shopify/react-native-skia';
import { Platform } from 'react-native';

import { CRACK_POINTS, CRACKS } from './waxSealMotion';
import { PAPER_SKSL } from './waxSealShader';

/*
 Static art, made once per layout on the JS thread: the envelope's four layers baked into images
 (back with its liner, the pocket front, the flap's outside and its lined inside), the card
 recorded into a picture, the seal's monogram mask, and the pieces' outlines. Per frame only
 transforms and uniforms change; the cracks are rebuilt from fixed points.
 */

export const SERIF = Platform.select({ ios: 'Georgia', default: 'serif' });
export const SEAL_PAD = 10;

export type Layout = {
  width: number;
  height: number;
  headerTop: number;
  env: { x: number; y: number; w: number; h: number; flap: number };
  seal: { x: number; y: number; r: number };
  card: { w: number; h: number; x: number; insideTop: number; clearTop: number; finalTop: number };
  chipsTop: number;
  hintTop: number;
};

export function makeLayout(width: number, height: number, top: number, bottom: number): Layout {
  const w = Math.min(width - 32, 380);
  const h = Math.round(w * 0.7);
  const cardH = h - 14;
  const hintTop = height - Math.max(bottom, 16) - 34;
  // The envelope sits low, with room above it for the card to rise clear and the detail chips
  // and the hint below it.
  const y = Math.round(Math.max(top + 74 + cardH, Math.min(height * 0.5, hintTop - 92 - h)));
  const flap = Math.round(h * 0.6);
  const x = (width - w) / 2;
  const cw = w - 26;
  return {
    width,
    height,
    headerTop: top + 22,
    env: { x, y, w, h, flap },
    seal: { x: width / 2, y: y + flap - 6, r: Math.round(Math.min(42, w * 0.115)) },
    card: {
      w: cw,
      h: cardH,
      x: (width - cw) / 2,
      insideTop: y + 8,
      clearTop: y - cardH - 10,
      finalTop: Math.min(y + 6 - cardH / 2, y - flap + 8),
    },
    chipsTop: y + h + 28,
    hintTop: Math.max(hintTop, y + h + 68),
  };
}

const paperEffect = Skia.RuntimeEffect.Make(PAPER_SKSL);

function paperPaint(tint: [number, number, number], liner: boolean) {
  const p = Skia.Paint();
  p.setAntiAlias(true);
  if (paperEffect) p.setShader(paperEffect.makeShader([tint[0], tint[1], tint[2], liner ? 1 : 0]));
  else p.setColor(Skia.Color(`rgb(${tint.map((v) => Math.round(v * 255)).join(',')})`));
  return p;
}

const CREAM: [number, number, number] = [0.93, 0.886, 0.81];
const CREAM_FRONT: [number, number, number] = [0.953, 0.914, 0.845];
const CREAM_FLAP: [number, number, number] = [0.972, 0.94, 0.88];
const LINER: [number, number, number] = [0.43, 0.09, 0.11];

/** The flap's outline from the envelope's top edge (local coordinates, hinge at y = 0). */
export function flapPath(w: number, fh: number, x = 0, y = 0) {
  const p = Skia.PathBuilder.Make();
  p.moveTo(x, y);
  p.lineTo(x + w, y);
  p.lineTo(x + w / 2 + 16, y + fh - 8);
  p.quadTo(x + w / 2, y + fh + 2, x + w / 2 - 16, y + fh - 8);
  p.close();
  return p.build();
}

function frontPath(w: number, h: number) {
  const p = Skia.PathBuilder.Make();
  p.moveTo(0, 2);
  p.lineTo(w / 2, h * 0.5);
  p.lineTo(w, 2);
  p.lineTo(w, h - 10);
  p.quadTo(w, h, w - 10, h);
  p.lineTo(10, h);
  p.quadTo(0, h, 0, h - 10);
  p.close();
  return p.build();
}

function snapshot(width: number, height: number, scale: number, draw: (c: SkCanvas) => void): SkImage | null {
  const surface = Skia.Surface.MakeOffscreen(Math.ceil(width * scale), Math.ceil(height * scale));
  if (!surface) return null;
  const c = surface.getCanvas();
  c.clear(Skia.Color('transparent'));
  c.scale(scale, scale);
  draw(c);
  surface.flush();
  const image = surface.makeImageSnapshot();
  return image.makeNonTextureImage() ?? image;
}

export type EnvelopeArt = { back: SkImage | null; front: SkImage | null; flapOut: SkImage | null; flapIn: SkImage | null };

/** The envelope's layers, each sized to the envelope (flaps to the flap) at `scale` px per pt. */
export function bakeEnvelope(l: Layout, scale: number): EnvelopeArt {
  const { w, h, flap } = l.env;
  const back = snapshot(w, h, scale, (c) => {
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(0, 0, w, h), 10, 10), paperPaint(CREAM, false));
    c.drawRect(Skia.XYWHRect(5, 4, w - 10, h * 0.66), paperPaint(LINER, true));
    // Shade at the top inside, where the flap's hinge hides the light.
    const shade = Skia.Paint();
    shade.setShader(
      Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: 0, y: h * 0.4 }, [Skia.Color('rgba(20,4,6,0.55)'), Skia.Color('rgba(20,4,6,0)')], null, 0),
    );
    c.drawRect(Skia.XYWHRect(5, 4, w - 10, h * 0.4), shade);
  });
  const front = snapshot(w, h, scale, (c) => {
    const outline = frontPath(w, h);
    c.drawPath(outline, paperPaint(CREAM_FRONT, false));
    // The bottom flap's edges: a fold casting a hair of shadow, with light below it.
    const foldB = Skia.PathBuilder.Make();
    foldB.moveTo(6, h - 3);
    foldB.quadTo(w * 0.3, h * 0.7, w / 2, h * 0.64);
    foldB.quadTo(w * 0.7, h * 0.7, w - 6, h - 3);
    const fold = foldB.build();
    const dark = Skia.Paint();
    dark.setAntiAlias(true);
    dark.setStyle(1);
    dark.setStrokeWidth(2.2);
    dark.setColor(Skia.Color('rgba(90,60,30,0.14)'));
    dark.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 1.4, true));
    c.save();
    c.translate(0, -1.2);
    c.drawPath(fold, dark);
    c.restore();
    const light = Skia.Paint();
    light.setAntiAlias(true);
    light.setStyle(1);
    light.setStrokeWidth(0.8);
    light.setColor(Skia.Color('rgba(255,252,244,0.75)'));
    c.drawPath(fold, light);
    // The V's edge: a lit paper edge over the dark inside.
    const lipB = Skia.PathBuilder.Make();
    lipB.moveTo(0, 2);
    lipB.lineTo(w / 2, h * 0.5);
    lipB.lineTo(w, 2);
    const lip = lipB.build();
    const edge = Skia.Paint();
    edge.setAntiAlias(true);
    edge.setStyle(1);
    edge.setStrokeWidth(1);
    edge.setColor(Skia.Color('rgba(255,250,240,0.9)'));
    c.drawPath(lip, edge);
  });
  const flapOut = snapshot(w, flap + 4, scale, (c) => {
    const path = flapPath(w, flap);
    c.drawPath(path, paperPaint(CREAM_FLAP, false));
    // Lighter toward the tip (it bulges a little), a lit edge along its lower sides.
    const sheen = Skia.Paint();
    sheen.setAntiAlias(true);
    sheen.setShader(
      Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: 0, y: flap }, [Skia.Color('rgba(120,90,50,0.12)'), Skia.Color('rgba(120,90,50,0.02)'), Skia.Color('rgba(255,255,250,0.12)')], [0, 0.35, 1], 0),
    );
    c.drawPath(path, sheen);
    const edge = Skia.Paint();
    edge.setAntiAlias(true);
    edge.setStyle(1);
    edge.setStrokeWidth(1);
    edge.setColor(Skia.Color('rgba(255,253,246,0.9)'));
    c.drawPath(path, edge);
  });
  const flapIn = snapshot(w, flap + 4, scale, (c) => {
    const path = flapPath(w, flap);
    c.drawPath(path, paperPaint(CREAM, false));
    c.save();
    c.translate(w * 0.025, 4);
    c.scale(0.95, 0.9);
    c.drawPath(flapPath(w, flap), paperPaint(LINER, true));
    c.restore();
  });
  return { back, front, flapOut, flapIn };
}

function width(font: SkFont, text: string) {
  return font.getGlyphWidths(font.getGlyphIDs(text)).reduce((a, b) => a + b, 0);
}

/** Text centred at x, with extra tracking between the letters. */
function centred(c: SkCanvas, text: string, x: number, y: number, font: SkFont, paint: SkPaint, tracking = 0) {
  if (tracking === 0) {
    c.drawText(text, x - width(font, text) / 2, y, paint, font);
    return;
  }
  const ids = font.getGlyphIDs(text);
  const ws = font.getGlyphWidths(ids);
  const total = ws.reduce((a, b) => a + b, 0) + tracking * (ws.length - 1);
  let cx = x - total / 2;
  for (let i = 0; i < text.length; i += 1) {
    c.drawText(text[i], cx, y, paint, font);
    cx += ws[i] + tracking;
  }
}

/** The invitation card, in its own coordinates (0, 0 … w, h). The RSVP buttons are views. */
export function recordCard(w: number, h: number) {
  const rec = Skia.PictureRecorder();
  const c = rec.beginRecording(Skia.XYWHRect(0, 0, w, h));
  const fill = Skia.Paint();
  fill.setAntiAlias(true);
  fill.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: 0, y: 0 },
      { x: w, y: h },
      [Skia.Color('#fdf8ef'), Skia.Color('#f8f0e2'), Skia.Color('#f3e8d6')],
      [0, 0.55, 1],
      0,
    ),
  );
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(0, 0, w, h), 8, 8), fill);
  const gold = Skia.Paint();
  gold.setAntiAlias(true);
  gold.setStyle(1);
  gold.setStrokeWidth(0.8);
  gold.setColor(Skia.Color('#b48f58'));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(8, 8, w - 16, h - 16), 4, 4), gold);
  const ink = Skia.Paint();
  ink.setAntiAlias(true);
  ink.setColor(Skia.Color('#4a2a22'));
  const red = Skia.Paint();
  red.setAntiAlias(true);
  red.setColor(Skia.Color('#7c1a20'));
  const kicker = matchFont({ fontFamily: SERIF, fontSize: 8.5, fontWeight: 'bold' });
  const title = matchFont({ fontFamily: SERIF, fontSize: Math.min(31, w * 0.095), fontStyle: 'italic', fontWeight: 'bold' });
  const body = matchFont({ fontFamily: SERIF, fontSize: 12.5 });
  const strong = matchFont({ fontFamily: SERIF, fontSize: 12.5, fontWeight: 'bold' });
  const mid = w / 2;
  centred(c, 'TOGETHER WITH THEIR FAMILIES', mid, 30, kicker, ink, 1.6);
  centred(c, 'Lena & Theo', mid, 66, title, red);
  centred(c, 'invite you to celebrate their wedding', mid, 87, body, ink);
  // A small ornament: two lines and a diamond.
  gold.setStrokeWidth(0.9);
  c.drawLine(mid - 46, 101, mid - 9, 101, gold);
  c.drawLine(mid + 9, 101, mid + 46, 101, gold);
  const diamond = Skia.PathBuilder.Make();
  diamond.moveTo(mid, 97);
  diamond.lineTo(mid + 4, 101);
  diamond.lineTo(mid, 105);
  diamond.lineTo(mid - 4, 101);
  diamond.close();
  const goldFill = Skia.Paint();
  goldFill.setAntiAlias(true);
  goldFill.setColor(Skia.Color('#b48f58'));
  c.drawPath(diamond.build(), goldFill);
  centred(c, 'Saturday, 14 June 2027 · 4 pm', mid, 124, strong, ink);
  centred(c, 'Villa Amara · Lake Orta', mid, 142, body, ink);
  return rec.finishRecordingAsPicture();
}

/**
 * The card as an image (drawn under a moving transform, an image follows it reliably; a picture
 * node did not on device).
 */
export function bakeCard(w: number, h: number, scale: number): SkImage | null {
  const picture = recordCard(w, h);
  return snapshot(w, h, scale, (c) => c.drawPicture(picture));
}

/** The monogram's mask (green = blurred height), centred in a square of side 2 (R + SEAL_PAD). */
export function buildSealMask(R: number, scale: number): SkImage | null {
  const side = (R + SEAL_PAD) * 2;
  const letters = matchFont({ fontFamily: SERIF, fontSize: R * 0.64, fontWeight: 'bold' });
  const amp = matchFont({ fontFamily: SERIF, fontSize: R * 0.4, fontStyle: 'italic' });
  return snapshot(side, side, scale, (c) => {
    c.clear(Skia.Color('black'));
    c.translate(R + SEAL_PAD, R + SEAL_PAD);
    const layer = Skia.Paint();
    layer.setBlendMode(BlendMode.Plus);
    c.saveLayer(layer);
    const p = Skia.Paint();
    p.setAntiAlias(true);
    p.setColor(Skia.Color('#00ff00'));
    p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 1.2, true));
    const ring = p.copy();
    ring.setStyle(1);
    ring.setStrokeWidth(R * 0.035);
    c.drawCircle(0, 0, R * 0.6, ring);
    const lw = width(letters, 'L');
    const tw = width(letters, 'T');
    const aw = width(amp, '&');
    const gap = R * 0.02;
    let x = -(lw + aw + tw + gap * 2) / 2;
    const base = R * 0.2;
    c.drawText('L', x, base, p, letters);
    x += lw + gap;
    c.drawText('&', x, base - R * 0.02, p, amp);
    x += aw + gap;
    c.drawText('T', x, base, p, letters);
    c.restore();
  });
}

/** Piece i's outline (seal-centred): from the centre out along crack i, round, back along i + 1. */
export function wedgePath(cracks: number[][], i: number, R: number): SkPath {
  const a = cracks[i];
  const b = cracks[(i + 1) % CRACKS];
  const p = Skia.PathBuilder.Make();
  p.moveTo(a[0], a[1]);
  for (let j = 1; j < CRACK_POINTS; j += 1) p.lineTo(a[j * 2], a[j * 2 + 1]);
  const a0 = Math.atan2(a[a.length - 1], a[a.length - 2]);
  let a1 = Math.atan2(b[b.length - 1], b[b.length - 2]);
  if (a1 < a0) a1 += Math.PI * 2;
  for (let k = 1; k < 8; k += 1) {
    const ang = a0 + ((a1 - a0) * k) / 8;
    p.lineTo(Math.cos(ang) * R * 1.5, Math.sin(ang) * R * 1.5);
  }
  for (let j = CRACK_POINTS - 1; j >= 1; j -= 1) p.lineTo(b[j * 2], b[j * 2 + 1]);
  p.close();
  return p.build();
}

/** The cracks grown to `amount` (each starts 40 ms after the last), at the seal's centre (x, y). */
export function crackPath(cracks: number[][], amount: number, x: number, y: number) {
  'worklet';
  const b = Skia.PathBuilder.Make();
  if (amount <= 0.001) return b.build();
  for (let i = 0; i < CRACKS; i += 1) {
    const own = Math.min(1, Math.max(0, (amount - i * 0.05) / (1 - (CRACKS - 1) * 0.05)));
    if (own <= 0) continue;
    // The crack reaches only to the wax's edge (about R).
    const reach = own * (CRACK_POINTS - 1) * (1 / 1.3) * 0.9;
    b.moveTo(x + cracks[i][0], y + cracks[i][1]);
    for (let j = 1; j < CRACK_POINTS; j += 1) {
      const f = Math.min(1, reach - (j - 1));
      if (f <= 0) break;
      const px = cracks[i][(j - 1) * 2] + (cracks[i][j * 2] - cracks[i][(j - 1) * 2]) * f;
      const py = cracks[i][(j - 1) * 2 + 1] + (cracks[i][j * 2 + 1] - cracks[i][(j - 1) * 2 + 1]) * f;
      b.lineTo(x + px, y + py);
      if (f < 1) break;
    }
  }
  return b.build();
}
