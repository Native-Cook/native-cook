import { PaintStyle, type SkCanvas, Skia, TileMode, vec } from '@shopify/react-native-skia';

import {
  BACKGROUND,
  CAPSULE,
  DIAMOND,
  DIAMOND_FACETS,
  EXTRA,
  EXTRAS,
  GLYPHS,
  GLYPH_SCALE,
  GLYPH_WHITE,
  HERO,
  HERO_RING,
  IDLE,
  OUTLINE,
  SPARK,
  SPARKLE,
  SPARKS,
  SPARK_SHAPE,
  TOKEN,
  TOKENS,
  bob,
  breathe,
  pulse,
} from './scenes';

const WHITE = 0xffffff;
const BLACK = 0x000000;

function rgb(color: number, alpha: number) {
  'worklet';
  return Float32Array.of(((color >> 16) & 0xff) / 255, ((color >> 8) & 0xff) / 255, (color & 0xff) / 255, alpha);
}

/** A rounded rectangle round (x, y), filled with a gradient from `a` to `b`: down at tilt 0, across at 1. */
function block(canvas: SkCanvas, x: number, y: number, w: number, h: number, radius: number, alpha: number, a: number, b: number, tilt: number) {
  'worklet';
  if (alpha <= 0.003 || w <= 0.5 || h <= 0.5) return;
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  const dx = (Math.sin((tilt * Math.PI) / 2) * w) / 2;
  const dy = (Math.cos((tilt * Math.PI) / 2) * h) / 2;
  paint.setShader(Skia.Shader.MakeLinearGradient(vec(x - dx, y - dy), vec(x + dx, y + dy), [rgb(a, 1), rgb(b, 1)], null, TileMode.Clamp));
  paint.setAlphaf(alpha);
  const r = Math.min(radius, w / 2, h / 2);
  canvas.drawRRect({ rect: { x: x - w / 2, y: y - h / 2, width: w, height: h }, rx: r, ry: r }, paint);
}

/** A polygon whose points start at `first` in `points`, drawn round (x, y) at `scale`. */
function polygon(canvas: SkCanvas, points: number[], first: number, x: number, y: number, scale: number, color: number, alpha: number) {
  'worklet';
  const path = Skia.Path.Make();
  for (let i = first; i + 1 < points.length; i += 2) {
    if (i === first) path.moveTo(x + points[i] * scale, y + points[i + 1] * scale);
    else path.lineTo(x + points[i] * scale, y + points[i + 1] * scale);
  }
  path.close();
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(rgb(color, alpha));
  canvas.drawPath(path, paint);
}

function glyph(canvas: SkCanvas, index: number, x: number, y: number, scale: number, color: number, alpha: number) {
  'worklet';
  if (alpha <= 0.003) return;
  const parts = GLYPHS[index];
  for (let p = 0; p < parts.length; p++) {
    const part = parts[p];
    if (part[0] === 0) {
      polygon(canvas, part, 1, x, y, scale, color, alpha);
      continue;
    }
    const paint = Skia.Paint();
    paint.setAntiAlias(true);
    paint.setColor(rgb(color, alpha));
    if (part[0] === 2) canvas.drawCircle(x + part[1] * scale, y + part[2] * scale, part[3] * scale, paint);
    else {
      const rect = { x: x + part[1] * scale, y: y + part[2] * scale, width: part[3] * scale, height: part[4] * scale };
      canvas.drawRRect({ rect, rx: part[5] * scale, ry: part[5] * scale }, paint);
    }
  }
}

/**
 * Draws the cast round (cx, cy). `read(k, i)` gives field `i` of piece `k`; `veil` multiplies
 * every opacity; `clock` is the time for the bobbing, the breathing and the sparkles (null: none).
 */
export function paintStage(canvas: SkCanvas, read: (k: number, i: number) => number, veil: number, clock: number | null, cx: number, cy: number) {
  'worklet';
  if (veil <= 0.003) return;
  const bobbing = clock === null ? 0 : read(IDLE, 0);
  const breathing = clock === null ? 0 : read(IDLE, 1);
  const time = clock ?? 0;

  // The capsule, which is the beam in the last scene.
  block(
    canvas, cx + read(CAPSULE, 0), cy + read(CAPSULE, 1), read(CAPSULE, 2), read(CAPSULE, 3), read(CAPSULE, 4), read(CAPSULE, 5) * veil,
    read(CAPSULE, 6), read(CAPSULE, 7), read(CAPSULE, 8),
  );

  // The diamond is in front of the beam and behind the wallet it drops into.
  const diamond = read(DIAMOND, 5) * veil;
  if (diamond > 0.003) {
    for (let f = 0; f < DIAMOND_FACETS.length; f++) {
      polygon(canvas, DIAMOND_FACETS[f], 1, cx + read(DIAMOND, 0), cy + read(DIAMOND, 1), read(DIAMOND, 2) / 2, DIAMOND_FACETS[f][0], diamond);
    }
  }

  for (let n = 0; n < EXTRAS + TOKENS; n++) {
    // The small discs first: the badges lie over them.
    const k = n < EXTRAS ? EXTRA + n : TOKEN + n - EXTRAS;
    const x = read(k, 0);
    const y = read(k, 1) - bob(k, bobbing, time);
    const size = breathe(x, y, breathing, time);
    const w = read(k, 2) * size;
    const h = read(k, 3) * size;
    const alpha = read(k, 5) * veil;
    block(canvas, cx + x, cy + y, w, h, read(k, 4) * size, alpha, read(k, 6), read(k, 7), read(k, 8));
    if (k >= TOKEN && k < TOKEN + TOKENS) {
      const index = k - TOKEN;
      glyph(canvas, index, cx + x, cy + y, Math.min(w, h) * GLYPH_SCALE, GLYPH_WHITE[index] ? WHITE : BLACK, alpha * read(k, 9));
    }
  }

  // The thin line inside the wallet.
  const outline = read(OUTLINE, 5) * veil;
  if (outline > 0.003) {
    const pen = Skia.Paint();
    pen.setAntiAlias(true);
    pen.setStyle(PaintStyle.Stroke);
    pen.setStrokeWidth(1.5);
    pen.setColor(rgb(BACKGROUND, outline));
    const w = read(OUTLINE, 2);
    const h = read(OUTLINE, 3);
    const rect = { x: cx + read(OUTLINE, 0) - w / 2, y: cy + read(OUTLINE, 1) - h / 2, width: w, height: h };
    canvas.drawRRect({ rect, rx: read(OUTLINE, 4), ry: read(OUTLINE, 4) }, pen);
  }

  // The hero badge: a blue ring, a white disc and a black rounded square, in the proportions 78 : 52 : 18.
  const hero = read(HERO, 5) * veil;
  if (hero > 0.003) {
    const x = cx + read(HERO, 0);
    const y = cy + read(HERO, 1);
    const unit = read(HERO, 2) / 78;
    const ring = Skia.Paint();
    ring.setAntiAlias(true);
    ring.setStyle(PaintStyle.Stroke);
    ring.setStrokeWidth(6 * unit);
    ring.setColor(rgb(HERO_RING, hero));
    canvas.drawCircle(x, y, 36 * unit, ring);
    const disc = Skia.Paint();
    disc.setAntiAlias(true);
    disc.setColor(rgb(WHITE, hero));
    canvas.drawCircle(x, y, 26 * unit, disc);
    const square = Skia.Paint();
    square.setAntiAlias(true);
    square.setColor(rgb(BLACK, hero));
    canvas.drawRRect({ rect: { x: x - 9 * unit, y: y - 9 * unit, width: 18 * unit, height: 18 * unit }, rx: 4 * unit, ry: 4 * unit }, square);
  }

  for (let j = 0; j < SPARKS; j++) {
    const alpha = read(SPARK + j, 5) * veil;
    if (alpha <= 0.003) continue;
    const size = (read(SPARK + j, 2) / 2) * (clock === null ? 1 : pulse(j, time));
    polygon(canvas, SPARK_SHAPE, 0, cx + read(SPARK + j, 0), cy + read(SPARK + j, 1), size, SPARKLE, alpha);
  }
}
