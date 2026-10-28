import {
  BlurStyle,
  matchFont,
  PaintStyle,
  Skia,
  type SkCanvas,
  type SkColor,
  type SkFont,
  type SkPaint,
  type SkPath,
  type SkPicture,
} from '@shopify/react-native-skia';
import { Platform } from 'react-native';

import { COIN_HEX, INGOT_HEX, MARKS, METALS, type Rgb, rgb, scaleRgb, STYLES, TICKERS } from './ingotCoinModel';

/*
 Ingot Coin Switch — drawing helpers and the embossed marks (a port of IngotCoinSwitchArt.swift).

 An ingot's top face carries "FINE", the metal's name, "999.9", "NET WT" and "1000 g"; a coin's faces
 carry a groove ring, a big letter and the invented ticker. Every line is stamped three times: a light
 edge up-left, a dark edge down-right and the letters in a deeper shade of the metal, so they read as
 raised. The marks are recorded once into pictures in the face's own units (centred on 0) and drawn
 through the face's projection.
*/

export const INGOT_W = 70;
export const INGOT_H = 190;
export const COIN_W = 136;

export function hex(v: number, a: number): SkColor {
  'worklet';
  return Float32Array.of(((v >> 16) & 0xff) / 255, ((v >> 8) & 0xff) / 255, (v & 0xff) / 255, a);
}

export function rgba(c: Rgb, a: number): SkColor {
  'worklet';
  return Float32Array.of(c.r, c.g, c.b, a);
}

export function fill(color: SkColor): SkPaint {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(color);
  return p;
}

export function stroke(color: SkColor, width: number): SkPaint {
  'worklet';
  const p = fill(color);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  return p;
}

/** SwiftUI's blur radius as a Skia sigma (Compose uses 1.5 × the radius as Android's blur radius). */
function sigma(r: number) {
  'worklet';
  return r * 1.5 * 0.57735 + 0.5;
}

/** A soft (blurred) fill of a path, offset by dy. */
export function blurred(c: SkCanvas, path: SkPath, color: SkColor, radius: number, dy: number) {
  'worklet';
  const p = fill(color);
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma(radius), true));
  c.save();
  c.translate(0, dy);
  c.drawPath(path, p);
  c.restore();
}

export function polygon(pts: number[]): SkPath {
  'worklet';
  const b = Skia.PathBuilder.Make();
  b.moveTo(pts[0], pts[1]);
  for (let i = 2; i < pts.length; i += 2) b.lineTo(pts[i], pts[i + 1]);
  b.close();
  return b.detach();
}

export function roundRect(x: number, y: number, w: number, h: number, r: number): SkPath {
  'worklet';
  return Skia.PathBuilder.Make().addRRect(Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r)).detach();
}

export function oval(x: number, y: number, w: number, h: number): SkPath {
  'worklet';
  return Skia.PathBuilder.Make().addOval(Skia.XYWHRect(x, y, w, h)).detach();
}

/** Width of a line of text, from its glyphs' own widths (not rounded per glyph). */
export function glyphWidth(font: SkFont, s: string, tracking: number) {
  'worklet';
  const widths = font.getGlyphWidths(font.getGlyphIDs(s));
  let w = 0;
  for (let i = 0; i < widths.length; i++) w += widths[i];
  return w + tracking * Math.max(0, widths.length - 1);
}

/** Draws a line of text vertically centred on y: centred on x, or starting at x (anchor −1). */
export function label(c: SkCanvas, s: string, font: SkFont, x: number, y: number, color: SkColor, anchor: number, tracking: number) {
  'worklet';
  const ids = font.getGlyphIDs(s);
  const widths = font.getGlyphWidths(ids);
  const positions = [];
  let cursor = 0;
  for (let i = 0; i < ids.length; i++) {
    positions.push({ x: cursor, y: 0 });
    cursor += widths[i] + tracking;
  }
  const w = cursor - tracking;
  const m = font.getMetrics();
  const x0 = anchor < 0 ? x : x - w / 2;
  c.drawGlyphs(ids, positions, x0, y - (m.ascent + m.descent) / 2, font, fill(color));
}

const FAMILY = Platform.select({ ios: 'System', default: 'sans-serif' });

type Weight = '400' | '600' | '700' | '800' | '900';

export function font(size: number, weight: Weight): SkFont {
  return matchFont({ fontFamily: FAMILY, fontSize: size, fontWeight: weight });
}

/** One line of embossed text centred on (x, y), shrunk to fit `maxWidth`. */
function stamp(c: SkCanvas, s: string, size: number, weight: Weight, kern: number, x: number, y: number, maxWidth: number, ink: SkColor) {
  let f = font(size, weight);
  let k = kern;
  const w = glyphWidth(f, s, kern);
  if (w > maxWidth) {
    const scaled = (size * maxWidth) / w;
    f = font(scaled, weight);
    k = (kern * scaled) / size;
  }
  const e = Math.max(0.5, size / 28);
  label(c, s, f, x - e, y - e, hex(0xffffff, 0.6), 0, k);
  label(c, s, f, x + e, y + e * 1.2, hex(0x000000, 0.28), 0, k);
  label(c, s, f, x, y, ink, 0, k);
}

function record(w: number, h: number, body: (c: SkCanvas) => void): SkPicture {
  const recorder = Skia.PictureRecorder();
  const c = recorder.beginRecording(Skia.XYWHRect(-w / 2, -h / 2, w, h));
  body(c);
  return recorder.finishRecordingAsPicture();
}

function ingotMark(style: number): SkPicture {
  const ink = rgbaOf(scaleRgb(rgb(INGOT_HEX[style]), 0.72));
  return record(INGOT_W, INGOT_H, (c) => {
    // Centred on the face: y down, the face's centre at 0.
    stamp(c, 'FINE', 7, '700', 1.2, 0, -58, 50, ink);
    stamp(c, METALS[style].toUpperCase(), 15, '900', 0.2, 0, -42, 52, ink);
    stamp(c, '999.9', 7.5, '600', 0.6, 0, -27, 50, ink);
    stamp(c, 'NET WT', 6.5, '700', 0.8, 0, 48, 50, ink);
    stamp(c, '1000 g', 7.5, '600', 0.4, 0, 59, 50, ink);
  });
}

function coinMark(style: number): SkPicture {
  const ink = rgbaOf(scaleRgb(rgb(COIN_HEX[style]), 0.7));
  return record(COIN_W, COIN_W, (c) => {
    // The groove: a dark ring with a light ring just inside it.
    const r = 58;
    c.drawCircle(0, 0, r, stroke(hex(0x000000, 0.18), 1.3));
    c.drawCircle(0, 0, r - 1.3, stroke(hex(0xffffff, 0.55), 1));
    stamp(c, MARKS[style], 62, '900', 0, 0, -4, 90, ink);
    stamp(c, TICKERS[style], 9, '700', 1.6, 0, 38, 70, ink);
  });
}

function rgbaOf(c: Rgb): SkColor {
  return Float32Array.of(c.r, c.g, c.b, 1);
}

export type Marks = { ingot: SkPicture[]; coin: SkPicture[] };

export function makeMarks(): Marks {
  const ingot: SkPicture[] = [];
  const coin: SkPicture[] = [];
  for (let i = 0; i < STYLES; i++) {
    ingot.push(ingotMark(i));
    coin.push(coinMark(i));
  }
  return { ingot, coin };
}
