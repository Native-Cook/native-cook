import { BlendMode, ClipOp, FillType, type SkCanvas, type SkFont, type SkPicture, Skia, TileMode, matchFont } from '@shopify/react-native-skia';
import { Platform } from 'react-native';

/** Ten invented albums with covers drawn in code, and the record that slides out of a sleeve. Ported from RecordStackDeckArt.swift. */

export type Album = { title: string; artist: string; glow: number };

export const ALBUMS: Album[] = [
  { title: 'Afterglow Arcade', artist: 'Mina Sol', glow: 0xff5a36 },
  { title: 'Blue Static', artist: 'The Low Hours', glow: 0x2747ff },
  { title: 'Lime Hours', artist: 'Kite Club', glow: 0xb6f23a },
  { title: 'Velvet Exit', artist: 'Ora Vance', glow: 0xc23bff },
  { title: 'Salt & Cedar', artist: 'Fen Harlow', glow: 0xe8b98a },
  { title: 'Night Swim', artist: 'Coral Hum', glow: 0x1fb5c9 },
  { title: 'Chroma Rings', artist: 'Halcyon Vale', glow: 0x8a7cff },
  { title: 'Pink Noise', artist: 'Dot Mercer', glow: 0xff7eb6 },
  { title: 'Hour Twenty-Four', artist: 'Sunday Static', glow: 0xf5c431 },
  { title: 'White Room Tapes', artist: 'Lune Archive', glow: 0xdadad6 },
];

const FAMILY = Platform.select({ ios: '.AppleSystemUIFont', default: 'sans-serif' });

export function hexA(hex: number, a = 1) {
  'worklet';
  return Float32Array.of(((hex >> 16) & 0xff) / 255, ((hex >> 8) & 0xff) / 255, (hex & 0xff) / 255, a);
}

function white(a: number) {
  'worklet';
  return Float32Array.of(1, 1, 1, a);
}

function fill(color: Float32Array) {
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(color);
  return p;
}

function stroke(color: Float32Array, width: number) {
  const p = fill(color);
  p.setStyle(1);
  p.setStrokeWidth(width);
  return p;
}

/** Text whose line box's top-left (or centre) is at (x, y), as SwiftUI places it, tracked −4 %. */
function type(canvas: SkCanvas, s: string, x: number, y: number, font: SkFont, color: Float32Array, center = false) {
  const size = font.getSize();
  const tracking = -0.04 * size;
  const widths = font.getGlyphWidths(font.getGlyphIDs(s));
  let total = tracking * (widths.length - 1);
  for (const wd of widths) total += wd;
  const m = font.getMetrics();
  const baseline = center ? y - (m.ascent + m.descent) / 2 : y - m.ascent;
  let cx = center ? x - total / 2 : x;
  const paint = fill(color);
  const chars = Array.from(s);
  for (let i = 0; i < chars.length; i++) {
    canvas.drawText(chars[i], cx, baseline, paint, font);
    cx += (widths[i] ?? 0) + tracking;
  }
}

/** Draws album i's cover in the square (0, 0, s, s). */
function drawCover(canvas: SkCanvas, i: number, s: number) {
  const font = (size: number, weight: '700' | '800' | '900') => matchFont({ fontFamily: FAMILY, fontSize: size, fontWeight: weight });
  const full = Skia.XYWHRect(0, 0, s, s);
  canvas.save();
  canvas.clipRect(full, ClipOp.Intersect, true);
  switch (i) {
    case 0: {
      // Afterglow Arcade: a striped sun setting into the bottom edge.
      canvas.drawRect(full, fill(hexA(0xff5a36)));
      canvas.save();
      const sun = Skia.Path.Make();
      sun.addCircle(s * 0.5, s * 0.86, s * 0.42);
      canvas.clipPath(sun, ClipOp.Intersect, true);
      canvas.drawRect(full, fill(hexA(0xffd23f)));
      for (let k = 0; k < 6; k++) canvas.drawRect(Skia.XYWHRect(0, s * (0.6 + k * 0.07), s, s * (0.012 + k * 0.006)), fill(hexA(0xff5a36)));
      canvas.restore();
      type(canvas, 'AFTERGLOW', s * 0.07, s * 0.07, font(s * 0.1, '900'), hexA(0x3a0d05));
      break;
    }
    case 1: {
      // Blue Static: a dot screen with one orange dot out of line.
      canvas.drawRect(full, fill(hexA(0x1f3fff)));
      const dot = fill(white(0.85));
      for (let row = 0; row < 9; row++) {
        for (let col = 0; col < 9; col++) {
          canvas.drawCircle(s * (0.1 + col * 0.1), s * (0.1 + row * 0.1), s * (0.012 + 0.012 * ((row + col) % 3)), dot);
        }
      }
      canvas.drawCircle(s * 0.72, s * 0.34, s * 0.07, fill(hexA(0xff8a2a)));
      break;
    }
    case 2: {
      // Lime Hours: lowercase type, very large.
      canvas.drawRect(full, fill(hexA(0xb6f23a)));
      type(canvas, 'lime', s * 0.5, s * 0.56, font(s * 0.42, '900'), hexA(0x111111), true);
      type(canvas, 'hours', s * 0.08, s * 0.86, font(s * 0.07, '700'), hexA(0x111111));
      break;
    }
    case 3: {
      // Velvet Exit: a glowing pink orb on plum.
      canvas.drawRect(full, fill(hexA(0x2a0f3d)));
      const orb = Skia.Paint();
      orb.setShader(
        Skia.Shader.MakeRadialGradient({ x: s * 0.42, y: s * 0.46 }, s * 0.42, [hexA(0xff9ad5), hexA(0xc23bff), hexA(0xc23bff, 0)], null, TileMode.Clamp),
      );
      canvas.drawRect(full, orb);
      canvas.drawCircle(s * 0.42, s * 0.46, s * 0.3, stroke(white(0.5), Math.max(1, s * 0.006)));
      type(canvas, 'VELVET EXIT', s * 0.07, s * 0.86, font(s * 0.06, '800'), hexA(0xf6d8ff));
      break;
    }
    case 4: {
      // Salt & Cedar: three diagonal bands on cream.
      canvas.drawRect(full, fill(hexA(0xefe6d2)));
      [0x1e6b66, 0x7a4a2b, 0xc8553d].forEach((hex, k) => {
        const o = s * (0.15 + k * 0.22);
        const band = Skia.Path.Make();
        band.moveTo(o, s);
        band.lineTo(o + s * 0.14, s);
        band.lineTo(o + s * 0.64, 0);
        band.lineTo(o + s * 0.5, 0);
        band.close();
        canvas.drawPath(band, fill(hexA(hex)));
      });
      break;
    }
    case 5: {
      // Night Swim: wavy lines under a moon.
      const sea = Skia.Paint();
      sea.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: 0, y: s }, [hexA(0x0b4a5e), hexA(0x041c26)], null, TileMode.Clamp));
      canvas.drawRect(full, sea);
      canvas.drawCircle(s * 0.72, s * 0.24, s * 0.08, fill(hexA(0xe6fbff)));
      for (let k = 0; k < 7; k++) {
        const y0 = s * (0.48 + k * 0.07);
        const wave = Skia.Path.Make();
        wave.moveTo(0, y0);
        for (let j = 0; j <= 24; j++) {
          const x = (s * j) / 24;
          wave.lineTo(x, y0 + Math.sin((x / s) * 4 * Math.PI + k) * s * 0.018);
        }
        canvas.drawPath(wave, stroke(hexA(0x5fe1ee, 0.9 - k * 0.1), Math.max(1, s * 0.01)));
      }
      break;
    }
    case 6: {
      // Chroma Rings: three offset rings, like a misregistered print.
      canvas.drawRect(full, fill(hexA(0x0d0d10)));
      [0xff3b5c, 0x3bff9e, 0x3b7cff].forEach((hex, k) => {
        const dx = (k - 1) * s * 0.045;
        const p = stroke(hexA(hex), s * 0.05);
        p.setBlendMode(BlendMode.Screen);
        canvas.drawCircle(s * 0.5 + dx, s * 0.5 - dx * 0.4, s * 0.26, p);
      });
      break;
    }
    case 7: {
      // Pink Noise: a checkerboard floor under a pink sky.
      canvas.drawRect(full, fill(hexA(0xff7eb6)));
      const cell = s / 8;
      const ink = fill(hexA(0x16161a));
      for (let row = 0; row < 4; row++) {
        for (let col = 0; col < 8; col++) if ((row + col) % 2 === 0) canvas.drawRect(Skia.XYWHRect(col * cell, s * 0.5 + row * cell, cell, cell), ink);
      }
      type(canvas, 'pink noise', s * 0.07, s * 0.08, font(s * 0.09, '800'), hexA(0x16161a));
      break;
    }
    case 8: {
      // Hour Twenty-Four: big numerals.
      canvas.drawRect(full, fill(hexA(0xf5c431)));
      type(canvas, '24', s * 0.52, s * 0.5, font(s * 0.62, '900'), hexA(0x14110a), true);
      canvas.drawRect(Skia.XYWHRect(s * 0.07, s * 0.86, s * 0.3, s * 0.02), fill(hexA(0x14110a)));
      break;
    }
    default: {
      // White Room Tapes: one thin circle and a red dot.
      canvas.drawRect(full, fill(hexA(0xf4f4f2)));
      canvas.drawCircle(s * 0.5, s * 0.48, s * 0.28, stroke(hexA(0x16161a), Math.max(1, s * 0.006)));
      canvas.drawCircle(s * 0.7, s * 0.3, s * 0.035, fill(hexA(0xe53935)));
    }
  }
  // The sleeve's printed edge and a faint sheen.
  const sheen = Skia.Paint();
  sheen.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: s, y: s }, [white(0.14), white(0), Float32Array.of(0, 0, 0, 0.12)], null, TileMode.Clamp));
  canvas.drawRect(full, sheen);
  canvas.drawRect(Skia.XYWHRect(0.5, 0.5, s - 1, s - 1), stroke(Float32Array.of(0, 0, 0, 0.25), 1));
  canvas.restore();
}

/** Each cover recorded once as a picture of side s. */
export function makeCovers(s: number): SkPicture[] {
  const out: SkPicture[] = [];
  for (let i = 0; i < ALBUMS.length; i++) {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, s, s));
    drawCover(canvas, i, s);
    out.push(recorder.finishRecordingAsPicture());
  }
  return out;
}

/** The record: black vinyl with grooves, a gloss fixed to the light, and the cover (a picture of side s) as its label. */
export function drawVinyl(canvas: SkCanvas, cover: SkPicture, s: number, cx: number, cy: number, r: number, angle: number) {
  'worklet';
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(hexA(0x0b0b0d));
  canvas.drawCircle(cx, cy, r, paint);
  const label = r * 0.36;
  const groove = Skia.Paint();
  groove.setAntiAlias(true);
  groove.setStyle(1);
  groove.setStrokeWidth(0.7);
  for (let k = 0; k < 30; k++) {
    groove.setColor(white(k % 2 === 0 ? 0.04 : 0.07));
    canvas.drawCircle(cx, cy, label + 6 + ((r - label - 10) * k) / 29, groove);
  }
  groove.setStrokeWidth(1.5);
  groove.setColor(white(0.14));
  canvas.drawCircle(cx, cy, r - 0.75, groove);
  const ring = Skia.Path.Make();
  ring.addCircle(cx, cy, r - 2);
  ring.addCircle(cx, cy, label + 2);
  ring.setFillType(FillType.EvenOdd);
  const sheen = Skia.Paint();
  sheen.setAntiAlias(true);
  sheen.setShader(
    Skia.Shader.MakeSweepGradient(cx, cy, [white(0), white(0.14), white(0), white(0), white(0.1), white(0), white(0)], [0, 0.08, 0.18, 0.5, 0.58, 0.68, 1], TileMode.Clamp),
  );
  canvas.save();
  canvas.rotate(-60, cx, cy);
  canvas.drawPath(ring, sheen);
  canvas.restore();
  canvas.save();
  canvas.translate(cx, cy);
  canvas.rotate(angle, 0, 0);
  const clip = Skia.Path.Make();
  clip.addCircle(0, 0, label);
  canvas.clipPath(clip, ClipOp.Intersect, true);
  canvas.translate(-label, -label);
  canvas.scale((label * 2) / s, (label * 2) / s);
  canvas.drawPicture(cover);
  canvas.restore();
  paint.setColor(hexA(0xd8d8dc));
  canvas.drawCircle(cx, cy, 3.5, paint);
  paint.setColor(hexA(0x222226));
  canvas.drawCircle(cx, cy, 1.4, paint);
}
