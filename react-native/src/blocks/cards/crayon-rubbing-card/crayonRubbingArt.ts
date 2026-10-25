import { BlendMode, BlurStyle, matchFont, PaintStyle, type SkCanvas, type SkFont, type SkImage, Skia, StrokeCap, StrokeJoin, TileMode } from '@shopify/react-native-skia';
import { Platform } from 'react-native';

import { CELL, GX, GY, PAPER } from './crayonRubbingMotion';

// The plaque's hidden relief, baked once on the JS thread into a height image (white = raised):
// a double border with corner rosettes, a heron among reeds in front of a low sun, rippled
// water, and a two-line inscription. The shader never shows this image directly: the paper over
// it only betrays it as a faint emboss until the crayon finds it.

const SERIF = Platform.select({ ios: 'Georgia', default: 'serif' });
export const RELIEF_BAKE = 2;

function white(alpha = 1) {
  const p = Skia.Paint();
  p.setColor(Skia.Color('#FFFFFF'));
  p.setAlphaf(alpha);
  return p;
}

function stroke(width: number, alpha = 1) {
  const p = white(alpha);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  p.setStrokeCap(StrokeCap.Round);
  p.setStrokeJoin(StrokeJoin.Round);
  return p;
}

function centredText(c: SkCanvas, text: string, x: number, y: number, size: number, track: number, alpha: number) {
  const font = matchFont({ fontFamily: SERIF, fontSize: size, fontWeight: '700' });
  const ids = font.getGlyphIDs(text);
  const widths = font.getGlyphWidths(ids);
  let total = 0;
  for (const w of widths) total += w + track;
  total -= track;
  let at = x - total / 2;
  const p = white(alpha);
  for (let i = 0; i < text.length; i += 1) {
    c.drawText(text[i], at, y, p, font);
    at += widths[i] + track;
  }
}

/** The heron: body, S-curved neck, head with crest and beak, folded wing, legs. Paper points. */
function heron(c: SkCanvas) {
  const body = Skia.PathBuilder.Make()
    .moveTo(146, 168)
    .cubicTo(170, 160, 204, 172, 226, 206)
    .cubicTo(232, 216, 236, 226, 244, 236)
    .cubicTo(226, 232, 214, 230, 204, 236)
    .cubicTo(186, 246, 160, 240, 148, 222)
    .cubicTo(138, 206, 136, 182, 146, 168)
    .close()
    .build();
  c.drawPath(body, white(0.95));
  // The neck: a thick S from the chest up to the head.
  const neck = Skia.PathBuilder.Make().moveTo(150, 176).cubicTo(126, 160, 152, 132, 140, 116).cubicTo(132, 104, 118, 100, 120, 90).build();
  c.drawPath(neck, stroke(11, 0.95));
  c.drawCircle(122, 88, 8.5, white(0.97));
  // Beak, long and sharp, pointing left and a little down.
  const beak = Skia.PathBuilder.Make().moveTo(116, 84).lineTo(70, 96).lineTo(116, 93).close().build();
  c.drawPath(beak, white(1));
  // Crest plume trailing back.
  const crest = Skia.PathBuilder.Make().moveTo(126, 82).cubicTo(140, 76, 150, 78, 160, 74).build();
  c.drawPath(crest, stroke(2.4, 0.9));
  // The folded wing: raised a step higher than the body, with three feather lines cut into it.
  const wing = Skia.PathBuilder.Make()
    .moveTo(162, 178)
    .cubicTo(188, 176, 214, 196, 236, 230)
    .cubicTo(210, 222, 182, 222, 164, 208)
    .cubicTo(154, 198, 154, 186, 162, 178)
    .close()
    .build();
  c.drawPath(wing, white(1));
  for (let i = 0; i < 3; i += 1) {
    const cut = Skia.PathBuilder.Make()
      .moveTo(172 + i * 14, 190 + i * 6)
      .cubicTo(186 + i * 14, 198 + i * 6, 200 + i * 14, 210 + i * 6, 212 + i * 10, 222)
      .build();
    const p = stroke(1.6, 1);
    p.setColor(Skia.Color('#8A8A8A'));
    c.drawPath(cut, p);
  }
  // Eye: a small dimple.
  const eye = Skia.Paint();
  eye.setColor(Skia.Color('#9A9A9A'));
  c.drawCircle(124, 86, 1.8, eye);
  // Legs with a knee, and the feet spread on the water.
  const legs = Skia.PathBuilder.Make()
    .moveTo(184, 238)
    .lineTo(180, 272)
    .lineTo(184, 304)
    .moveTo(198, 236)
    .lineTo(204, 270)
    .lineTo(200, 304)
    .moveTo(172, 306)
    .lineTo(186, 304)
    .lineTo(196, 308)
    .moveTo(192, 306)
    .lineTo(202, 304)
    .lineTo(214, 308)
    .build();
  c.drawPath(legs, stroke(3.2, 0.92));
}

function reeds(c: SkCanvas) {
  const stems: [number, number, number][] = [
    [246, 304, 150],
    [258, 304, 132],
    [270, 306, 170],
    [284, 306, 196],
    [54, 306, 188],
    [66, 306, 214],
  ];
  for (const [x, y0, top] of stems) {
    const bend = (x - 160) * 0.08;
    const stem = Skia.PathBuilder.Make().moveTo(x, y0).quadTo(x + bend, (y0 + top) / 2, x + bend * 1.6, top).build();
    c.drawPath(stem, stroke(2.2, 0.85));
    // A seed head at the top of the tall ones, and a blade halfway.
    if (top < 180) {
      c.drawPath(
        Skia.PathBuilder.Make().addOval(Skia.XYWHRect(x + bend * 1.6 - 3.2, top - 20, 6.4, 22)).build(),
        white(0.9),
      );
    }
    const blade = Skia.PathBuilder.Make()
      .moveTo(x + bend * 0.5, y0 - 40)
      .quadTo(x + bend * 0.5 + (x > 160 ? 18 : -18), y0 - 70, x + bend * 0.5 + (x > 160 ? 30 : -30), y0 - 96)
      .build();
    c.drawPath(blade, stroke(2.6, 0.8));
  }
}

/** Draws the relief's shapes at full height (they are blurred afterwards into sloped edges). */
function reliefShapes(c: SkCanvas) {
  const w = PAPER.w;
  const h = PAPER.h;
  // Double border and rosettes at the corners.
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(16, 16, w - 32, h - 32), 6, 6), stroke(4, 0.95));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(25, 25, w - 50, h - 50), 3, 3), stroke(1.6, 0.85));
  for (const [x, y] of [
    [25, 25],
    [w - 25, 25],
    [25, h - 25],
    [w - 25, h - 25],
  ]) {
    c.drawCircle(x, y, 7, white(1));
    for (let k = 0; k < 8; k += 1) {
      const a = (k * Math.PI) / 4;
      c.drawCircle(x + Math.cos(a) * 10.5, y + Math.sin(a) * 10.5, 2.4, white(0.9));
    }
  }
  // A low sun behind the heron: a ring and its rays, lower than the bird.
  c.drawCircle(206, 128, 46, stroke(3, 0.6));
  for (let k = 0; k < 14; k += 1) {
    const a = Math.PI + (k * Math.PI) / 13;
    const r0 = 54;
    const r1 = 70 + (k % 2) * 10;
    c.drawLine(206 + Math.cos(a) * r0, 128 + Math.sin(a) * r0, 206 + Math.cos(a) * r1, 128 + Math.sin(a) * r1, stroke(2, 0.5));
  }
  reeds(c);
  heron(c);
  // Water: rippled lines under the bird.
  for (let i = 0; i < 4; i += 1) {
    const y = 314 + i * 9;
    const path = Skia.PathBuilder.Make();
    const x0 = 44 + i * 10;
    const x1 = w - 44 - i * 10;
    for (let x = x0; x <= x1; x += 3) {
      const yy = y + Math.sin((x / 22) * Math.PI * 2 + i) * 1.8;
      if (x === x0) path.moveTo(x, yy);
      else path.lineTo(x, yy);
    }
    c.drawPath(path.build(), stroke(2, 0.7 - i * 0.1));
  }
  // The inscription.
  centredText(c, 'THE RIVER KEEPS', w / 2, 372, 19, 2.4, 1);
  centredText(c, 'ITS OWN TIME', w / 2, 394, 19, 2.4, 1);
}

/** The height image of the relief, at RELIEF_BAKE pixels per point: black flat, white raised. */
export function bakeRelief(): SkImage {
  const W = Math.ceil(PAPER.w * RELIEF_BAKE);
  const H = Math.ceil(PAPER.h * RELIEF_BAKE);
  const surface = Skia.Surface.Make(W, H)!;
  const c = surface.getCanvas();
  c.clear(Skia.Color('#000000'));
  c.scale(RELIEF_BAKE, RELIEF_BAKE);
  // The shapes, softened: a blurred layer under a sharper one gives bevelled edges.
  const soft = Skia.Paint();
  soft.setImageFilter(Skia.ImageFilter.MakeBlur(1.6, 1.6, TileMode.Decal, null));
  c.saveLayer(soft);
  reliefShapes(c);
  c.restore();
  const image = surface.makeImageSnapshot();
  surface.dispose();
  return image;
}

/** How much raised relief each grid cell holds (0 … 1), and their sum, for the reveal %. */
export function reliefWeights(relief: SkImage) {
  const weights = new Array(GX * GY).fill(0);
  const pixels = relief.readPixels();
  const W = relief.width();
  const H = relief.height();
  if (!pixels) return { weights: weights.fill(1), total: GX * GY };
  const step = CELL * RELIEF_BAKE;
  for (let gy = 0; gy < GY; gy += 1) {
    for (let gx = 0; gx < GX; gx += 1) {
      let raised = 0;
      let n = 0;
      for (let y = gy * step; y < Math.min(H, (gy + 1) * step); y += 2) {
        for (let x = gx * step; x < Math.min(W, (gx + 1) * step); x += 2) {
          const v = pixels[(y * W + x) * 4];
          const level = pixels instanceof Float32Array ? v : v / 255;
          if (level > 0.35) raised += 1;
          n += 1;
        }
      }
      weights[gy * GX + gx] = n > 0 ? raised / n : 0;
    }
  }
  let total = 0;
  for (const w of weights) total += w;
  return { weights, total };
}

/** A soft blur paint (for shadows drawn while the crayon moves). */
export function blurPaint(hex: string, alpha: number, sigma: number) {
  const p = Skia.Paint();
  p.setColor(Skia.Color(hex));
  p.setAlphaf(alpha);
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma, true));
  return p;
}

/** The museum table behind everything: warm grey felt with grain, a pool of gallery light, a vignette. */
export function recordRoom(w: number, h: number, ox: number, oy: number, k: number) {
  const rec = Skia.PictureRecorder();
  const c = rec.beginRecording(Skia.XYWHRect(0, 0, w, h));
  const base = Skia.Paint();
  base.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: w * 0.3, y: h }, ['#2E2925', '#24201C', '#191613'].map((v) => Skia.Color(v)), [0, 0.5, 1], TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(0, 0, w, h), base);
  const felt = Skia.Paint();
  felt.setShader(Skia.Shader.MakeFractalNoise(0.7, 0.7, 3, 8, 0, 0));
  felt.setColorFilter(Skia.ColorFilter.MakeMatrix([0.33, 0.34, 0.33, 0, 0, 0.33, 0.34, 0.33, 0, 0, 0.33, 0.34, 0.33, 0, 0, 0, 0, 0, 1, 0]));
  felt.setBlendMode(BlendMode.SoftLight);
  felt.setAlphaf(0.3);
  c.drawRect(Skia.XYWHRect(0, 0, w, h), felt);
  const light = Skia.Paint();
  light.setShader(
    Skia.Shader.MakeRadialGradient({ x: ox + 196 * k, y: oy + 330 * k }, 380 * k, ['rgba(255,226,180,0.16)', 'rgba(255,226,180,0.05)', 'rgba(255,226,180,0)'].map((v) => Skia.Color(v)), [0, 0.5, 1], TileMode.Clamp),
  );
  c.drawRect(Skia.XYWHRect(0, 0, w, h), light);
  const v = Skia.Paint();
  v.setShader(Skia.Shader.MakeRadialGradient({ x: w / 2, y: h * 0.45 }, h * 0.75, ['rgba(0,0,0,0)', 'rgba(0,0,0,0)', 'rgba(6,4,3,0.6)'].map((x) => Skia.Color(x)), [0, 0.55, 1], TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(0, 0, w, h), v);
  return rec.finishRecordingAsPicture();
}

/**
 * The one crayon, drawn on the UI thread into the frame's picture: its shadow (tight while it
 * presses, wider and softer when lifted), the wax stick with a worn tip and a sheen, and the paper
 * wrapper with its name. (x, y) is the tip in canvas points; the stick runs along +x, turned by
 * `angle` degrees.
 */
export function drawCrayon(c: SkCanvas, x: number, y: number, angle: number, contact: number, tones: string[], name: string, font: SkFont) {
  'worklet';
  c.save();
  c.translate(x, y);
  c.rotate(angle, 0, 0);
  const up = 1 - contact;
  const shadow = Skia.Paint();
  shadow.setColor(Skia.Color('#0A0806'));
  shadow.setAlphaf(0.5 - 0.18 * up);
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3 + 3 * up, false));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(4 + 7 * up, -9 + 3 + 12 * up, 92, 18), 5, 5), shadow);
  const body = Skia.Paint();
  body.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: -9 }, { x: 0, y: 9 }, [Skia.Color(tones[0]), Skia.Color(tones[1]), Skia.Color(tones[2])], [0, 0.5, 1], TileMode.Clamp));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(8, -9, 88, 18), 4, 4), body);
  const tip = Skia.Paint();
  tip.setColor(Skia.Color(tones[1]));
  c.drawPath(Skia.PathBuilder.Make().moveTo(10, -9).lineTo(1, -3).quadTo(-1, 0, 1, 3).lineTo(10, 9).close().build(), tip);
  const facet = Skia.Paint();
  facet.setColor(Skia.Color('rgba(255,255,255,0.22)'));
  c.drawPath(Skia.PathBuilder.Make().moveTo(10, -9).lineTo(1, -3).lineTo(3, -3).lineTo(11, -7).close().build(), facet);
  const wrap = Skia.Paint();
  wrap.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: -9.6 }, { x: 0, y: 9.6 }, [Skia.Color('#FBF4E2'), Skia.Color('#EADCBE'), Skia.Color('#CDBB98')], [0, 0.55, 1], TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(32, -9.6, 56, 19.2), wrap);
  const band = Skia.Paint();
  band.setColor(Skia.Color(tones[1]));
  c.drawRect(Skia.XYWHRect(32, -6.6, 56, 1.6), band);
  c.drawRect(Skia.XYWHRect(32, 5, 56, 1.6), band);
  const label = Skia.Paint();
  label.setColor(Skia.Color(tones[2]));
  c.drawText(name, 39, 2.4, label, font);
  const sheen = Skia.Paint();
  sheen.setColor(Skia.Color('rgba(255,255,255,0.3)'));
  c.drawRect(Skia.XYWHRect(12, -5.5, 82, 1.2), sheen);
  c.restore();
}
