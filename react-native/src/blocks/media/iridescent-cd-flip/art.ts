import { BlurStyle, ClipOp, FillType, type SkCanvas, type SkFont, Skia, TileMode } from '@shopify/react-native-skia';

import { hash } from './motion';

/** The disc's two faces, the case art and the HUD frame, drawn in code. Ported from IridescentCdFlipArt.swift. */

export const BACKGROUND = '#060607';
const STREAKS = [0x3aa8ff, 0x5fd8ff, 0xe8283c, 0xff4b3a, 0xf4e3b5, 0x3be39a];
const WARM = [0xff6a2b, 0xe8283c, 0xffb46b, 0xff6a2b, 0xe8283c, 0xff8a3d, 0xc21f3a];
const HUE = [0xff7ad9, 0x9b7bff, 0x5bc8ff, 0x5bffc8, 0xf6ff7a, 0xff9a6b];

export function hexA(hex: number, a = 1) {
  'worklet';
  return Float32Array.of(((hex >> 16) & 0xff) / 255, ((hex >> 8) & 0xff) / 255, (hex & 0xff) / 255, a);
}

export function white(a: number) {
  'worklet';
  return Float32Array.of(1, 1, 1, a);
}

/** Text with extra space between letters, its line box's vertical centre (0), top (1) or bottom (2) at y; right-aligned when right. */
export function monoText(canvas: SkCanvas, s: string, x: number, y: number, font: SkFont, color: Float32Array, tracking: number, right: boolean, anchorY: number) {
  'worklet';
  const widths = font.getGlyphWidths(font.getGlyphIDs(s));
  let total = 0;
  for (let i = 0; i < widths.length; i++) total += widths[i] + tracking;
  const m = font.getMetrics();
  const baseline = anchorY === 0 ? y - (m.ascent + m.descent) / 2 : anchorY === 1 ? y - m.ascent : y - m.descent;
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(color);
  let cx = right ? x - total : x;
  for (let i = 0; i < s.length; i++) {
    canvas.drawText(s[i], cx, baseline, paint, font);
    cx += (widths[i] ?? 0) + tracking;
  }
}

/**
 * The printed art's light streaks inside the square (x, y, s): broad warm bands across the lower
 * third and a fan of short cold streaks at the right, slightly blurred.
 */
export function drawStreaks(canvas: SkCanvas, x: number, y: number, s: number) {
  'worklet';
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, Math.max(0.6, s * 0.006), true));
  for (let k = 0; k < 7; k++) {
    const yy = y + s * (0.71 + k * 0.034);
    const hgt = s * (0.012 + 0.008 * (k % 3));
    const c = WARM[k];
    paint.setShader(Skia.Shader.MakeLinearGradient({ x, y: yy }, { x: x + s, y: yy }, [hexA(c, 0.75), hexA(c), hexA(c, 0.9)], null, TileMode.Clamp));
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x - 4, yy, s + 8, hgt), hgt / 2, hgt / 2), paint);
  }
  for (let k = 0; k < 6; k++) {
    const yy = y + s * (0.2 + k * 0.075);
    const len = s * (0.18 + 0.1 * ((k * 7) % 3));
    const hgt = s * (0.016 + 0.006 * (k % 2));
    const c = STREAKS[k];
    paint.setShader(
      Skia.Shader.MakeLinearGradient({ x: x + s - len, y: yy }, { x: x + s, y: yy }, [hexA(c, 0), hexA(c, 0.95), white(0.9)], null, TileMode.Clamp),
    );
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x + s - len, yy, len + 4, hgt), hgt / 2, hgt / 2), paint);
  }
}

/** The face of the disc (radius r) in the square (0, 0, 2r, 2r), seen at turn angle (degrees, for the light). */
export function drawFace(canvas: SkCanvas, r: number, angle: number, back: boolean, font: SkFont) {
  'worklet';
  const a = (angle * Math.PI) / 180;
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  canvas.save();
  const clip = Skia.Path.Make();
  clip.addCircle(r, r, r);
  canvas.clipPath(clip, ClipOp.Intersect, true);
  if (back) {
    paint.setShader(Skia.Shader.MakeRadialGradient({ x: r, y: r }, r, [hexA(0xe3e5ea), hexA(0xa9acb4), hexA(0xd5d7dd)], [0, 0.6, 1], TileMode.Clamp));
    canvas.drawCircle(r, r, r, paint);
    // The diffraction rainbow: two hue cycles around the disc, swept round as it turns.
    const ring = Skia.Path.Make();
    ring.addCircle(r, r, r);
    ring.addCircle(r, r, r * 0.33);
    ring.setFillType(FillType.EvenOdd);
    const colors: Float32Array[] = [];
    const pos: number[] = [];
    for (let k = 0; k < 12; k++) {
      colors.push(hexA(HUE[k % 6], 0.7));
      pos.push(k / 12);
      colors.push(hexA(HUE[k % 6], 0.08));
      pos.push((k + 0.55) / 12);
    }
    colors.push(hexA(HUE[0], 0.7));
    pos.push(1);
    paint.setShader(Skia.Shader.MakeSweepGradient(r, r, colors, pos, TileMode.Clamp));
    canvas.save();
    canvas.rotate(2 * angle, r, r);
    canvas.drawPath(ring, paint);
    canvas.restore();
    paint.setShader(null);
    paint.setStyle(1);
    paint.setStrokeWidth(0.6);
    for (let k = 0; k < 18; k++) {
      paint.setColor(Float32Array.of(0, 0, 0, k % 3 === 0 ? 0.07 : 0.035));
      canvas.drawCircle(r, r, r * (0.36 + (0.62 * k) / 17), paint);
    }
    paint.setStyle(0);
  } else {
    paint.setShader(Skia.Shader.MakeRadialGradient({ x: r, y: r }, r, [hexA(0x17171b), hexA(0x08080a)], null, TileMode.Clamp));
    canvas.drawCircle(r, r, r, paint);
    paint.setShader(null);
    drawStreaks(canvas, 0, 0, 2 * r);
    monoText(canvas, 'MIRROR INDEX', r * 0.24, r * 0.62, font, hexA(0xd8b25a), 1.5, false, 0);
    paint.setStyle(1);
    paint.setStrokeWidth(1);
    paint.setColor(hexA(0xe8283c, 0.8));
    canvas.drawCircle(r, r, r * 0.34, paint);
    paint.setColor(hexA(0x3be39a, 0.7));
    canvas.drawCircle(r, r, r * 0.355, paint);
    paint.setStyle(0);
  }
  // The light: a soft diagonal band that slides across as the disc turns.
  const u = 0.5 + 0.6 * Math.sin(a);
  paint.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: 2 * r * (u - 0.35), y: 2 * r * 0.2 },
      { x: 2 * r * (u + 0.35), y: 2 * r * 0.8 },
      [white(0), white(back ? 0.38 : 0.16), white(0)],
      [0, 0.5, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawCircle(r, r, r, paint);
  // Hub: a clear plastic ring, a grey clamping ring, the hole.
  const hub = back ? [hexA(0xedeef2, 0.9), hexA(0xb9bcc4, 0.9)] : [hexA(0x8c8f96, 0.9), hexA(0x4a4c52, 0.9)];
  paint.setShader(Skia.Shader.MakeRadialGradient({ x: r, y: r }, r * 0.31, [hub[0], hub[0], hub[1]], [0, 0.258, 1], TileMode.Clamp));
  canvas.drawCircle(r, r, r * 0.31, paint);
  paint.setShader(null);
  const line = Skia.Paint();
  line.setAntiAlias(true);
  line.setStyle(1);
  line.setStrokeWidth(1);
  line.setColor(white(0.35));
  canvas.drawCircle(r, r, r * 0.31, line);
  line.setStrokeWidth(1.2);
  line.setColor(white(back ? 0.5 : 0.25));
  canvas.drawCircle(r, r, r * 0.2, line);
  paint.setColor(hexA(0x060607));
  canvas.drawCircle(r, r, r * 0.075, paint);
  line.setStrokeWidth(0.8);
  line.setColor(white(0.2));
  canvas.drawCircle(r, r, r * 0.075, line);
  // The rim.
  line.setStrokeWidth(1.5);
  line.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: 2 * r, y: 2 * r }, [white(0.55), white(0.12), white(0.4)], null, TileMode.Clamp));
  canvas.drawCircle(r, r, r - 0.75, line);
  canvas.restore();
}

/** The chamfered HUD frame: corners cut at the top right and bottom left. */
export function framePath(x: number, y: number, s: number, cut: number) {
  'worklet';
  const p = Skia.Path.Make();
  p.moveTo(x, y);
  p.lineTo(x + s - cut, y);
  p.lineTo(x + s, y + cut);
  p.lineTo(x + s, y + s);
  p.lineTo(x + cut, y + s);
  p.lineTo(x, y + s - cut);
  p.close();
  return p;
}

/** Film grain: fixed specks over the black, recorded once. */
export function makeGrain(w: number, h: number) {
  const recorder = Skia.PictureRecorder();
  const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, w, h));
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  for (let i = 0; i < 700; i++) {
    const x = hash(i, 3) * w;
    const y = hash(i, 4) * h;
    const s = 0.6 + 1.2 * hash(i, 5);
    paint.setColor(white(0.03 + 0.07 * hash(i, 6)));
    canvas.drawCircle(x + s / 2, y + s / 2, s / 2, paint);
  }
  return recorder.finishRecordingAsPicture();
}
