import {
  BlendMode,
  BlurStyle,
  ClipOp,
  FilterMode,
  MipmapMode,
  PaintStyle,
  type SkCanvas,
  type SkFont,
  type SkImage,
  type SkPaint,
  Skia,
  StrokeCap,
  TileMode,
} from '@shopify/react-native-skia';

import { type KeyState, type Pose, RING_DELAY, RING_LIFE, clamp01, easeOut, hammerLift, keyGlow, stringRing } from './pianoKeyMotion';

/*
 Piano Key Button — the drawing. One Skia canvas: a baked background (lacquer, the lesson card,
 the fallboard rail, the hammer recess and the stave) and a baked overlay (the felt strip and the
 black keys), with the parts that move drawn between and over them every frame: hammers and
 strings in the recess, the three ivory keys, their labels, glows, ring waves and the stave's notes.
*/

export const KEY_COLORS = ['#F2B35B', '#5FC9C1', '#EC8796'];
export const KEY_LABELS = [
  { title: 'Warm up', sub: '5 min', note: 'C' },
  { title: 'Lesson 14', sub: 'Triads', note: 'E' },
  { title: 'Free play', sub: 'Any key', note: 'G' },
];
const INK = '#3A2C20';
const INK_SOFT = '#8C7660';

export type Fonts = { title: SkFont; sub: SkFont; note: SkFont; meter: SkFont };

export type Scene = {
  w: number;
  h: number;
  cardX: number;
  cardY: number;
  cardW: number;
  cardH: number;
  /** Stave: left, right, y of the bottom line, line spacing. */
  staffL: number;
  staffR: number;
  staffY: number;
  staffGap: number;
  recessTop: number;
  feltY: number;
  feltH: number;
  keyTop: number;
  keyBottom: number;
  keyX: number[];
  keyW: number;
  gap: number;
};

export function makeScene(w: number, h: number, top: number, bottom: number): Scene {
  const cardX = 16;
  const cardY = top + 108;
  const cardW = w - 32;
  const cardH = Math.round(Math.min(116, Math.max(96, h * 0.13)));
  const recessTop = cardY + cardH + 30;
  const feltY = recessTop + 36;
  const feltH = 16;
  const keyTop = feltY + feltH;
  const keyBottom = h - bottom - 54;
  const gap = 5;
  const keyW = (w - 28 - gap * 2) / 3;
  const keyX = [14, 14 + keyW + gap, 14 + 2 * (keyW + gap)];
  const staffR = cardX + cardW - 20;
  const staffL = staffR - Math.min(150, cardW * 0.42);
  const staffGap = 7;
  const staffY = cardY + cardH / 2 + staffGap * 2 + 2;
  return { w, h, cardX, cardY, cardW, cardH, staffL, staffR, staffY, staffGap, recessTop, feltY, feltH, keyTop, keyBottom, keyX, keyW, gap };
}

/** Draws a baked image with linear filtering (it moves, rotates and lands between pixels). */
function drawImg(c: SkCanvas, img: SkImage, src: ReturnType<typeof Skia.XYWHRect>, dst: ReturnType<typeof Skia.XYWHRect>, p: SkPaint) {
  'worklet';
  c.drawImageRectOptions(img, src, dst, FilterMode.Linear, MipmapMode.None, p);
}

function paint(color: string, alpha = 1) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  if (alpha < 1) p.setAlphaf(alpha);
  return p;
}

function linear(x0: number, y0: number, x1: number, y1: number, colors: string[], pos: number[] | null = null) {
  'worklet';
  return Skia.Shader.MakeLinearGradient(Skia.Point(x0, y0), Skia.Point(x1, y1), colors.map((c) => Skia.Color(c)), pos, TileMode.Clamp);
}

function radial(x: number, y: number, r: number, colors: string[], pos: number[] | null = null) {
  'worklet';
  return Skia.Shader.MakeRadialGradient(Skia.Point(x, y), r, colors.map((c) => Skia.Color(c)), pos, TileMode.Clamp);
}

/** Monochrome grain: fractal noise turned grey, laid over with `overlay` at `alpha`. */
function grain(alpha: number, fx: number, fy: number, seed: number) {
  const p = Skia.Paint();
  p.setShader(Skia.Shader.MakeFractalNoise(fx, fy, 2, seed, 0, 0));
  p.setColorFilter(Skia.ColorFilter.MakeMatrix([1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, alpha]));
  p.setBlendMode(BlendMode.Overlay);
  return p;
}

function blurred(color: string, alpha: number, sigma: number) {
  'worklet';
  const p = paint(color, alpha);
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma, true));
  return p;
}

function textWidth(font: SkFont, text: string) {
  'worklet';
  const widths = font.getGlyphWidths(font.getGlyphIDs(text));
  let sum = 0;
  for (let i = 0; i < widths.length; i += 1) sum += widths[i];
  return sum;
}

function bake(w: number, h: number, scale: number, draw: (c: SkCanvas) => void): SkImage | null {
  const surface = Skia.Surface.Make(Math.ceil(w * scale), Math.ceil(h * scale));
  if (!surface) return null;
  const c = surface.getCanvas();
  c.scale(scale, scale);
  draw(c);
  surface.flush();
  return surface.makeImageSnapshot();
}

/** Lacquer, card, fallboard and recess: everything behind the moving parts. */
export function bakeBackground(s: Scene, fonts: Fonts, scale: number) {
  return bake(s.w, s.h, scale, (c) => {
    const { w, h } = s;
    // Ebony lacquer with a warm pool of light where the keys are, a vignette and grain.
    const bg = Skia.Paint();
    bg.setShader(linear(0, 0, 0, h, ['#1E1612', '#18110E', '#130E0C', '#0F0B0A'], [0, 0.35, 0.75, 1]));
    c.drawRect(Skia.XYWHRect(0, 0, w, h), bg);
    const warm = Skia.Paint();
    warm.setShader(radial(w * 0.5, s.keyTop + 40, h * 0.6, ['rgba(120,74,38,0.30)', 'rgba(70,40,22,0.12)', 'rgba(0,0,0,0)'], [0, 0.45, 1]));
    c.drawRect(Skia.XYWHRect(0, 0, w, h), warm);
    const vig = Skia.Paint();
    vig.setShader(radial(w * 0.5, h * 0.48, h * 0.75, ['rgba(0,0,0,0)', 'rgba(4,2,1,0.18)', 'rgba(4,2,1,0.55)'], [0.45, 0.78, 1]));
    c.drawRect(Skia.XYWHRect(0, 0, w, h), vig);
    c.drawRect(Skia.XYWHRect(0, 0, w, h), grain(0.07, 0.9, 0.9, 3));

    // The lesson card: two shadows, a three-stop body, a hairline highlight on top.
    const card = Skia.RRectXY(Skia.XYWHRect(s.cardX, s.cardY, s.cardW, s.cardH), 20, 20);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(s.cardX, s.cardY + 14, s.cardW, s.cardH), 20, 20), blurred('#000000', 0.42, 16));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(s.cardX, s.cardY + 1.5, s.cardW, s.cardH), 20, 20), blurred('#000000', 0.5, 1.5));
    const body = Skia.Paint();
    body.setAntiAlias(true);
    body.setShader(linear(0, s.cardY, 0, s.cardY + s.cardH, ['#33271F', '#2A2019', '#231A15']));
    c.drawRRect(card, body);
    c.save();
    c.clipRRect(card, ClipOp.Intersect, true);
    c.drawRect(Skia.XYWHRect(s.cardX, s.cardY, s.cardW, s.cardH), grain(0.08, 1.1, 1.1, 7));
    c.restore();
    const edge = paint('#FFE7C8', 0.12);
    edge.setStyle(PaintStyle.Stroke);
    edge.setStrokeWidth(1);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(s.cardX + 0.5, s.cardY + 0.5, s.cardW - 1, s.cardH - 1), 19.5, 19.5), edge);

    // The stave: five lines and a common-time sign.
    const line = paint('#E9D9C2', 0.32);
    line.setStrokeWidth(1);
    for (let i = 0; i < 5; i += 1) {
      const y = s.staffY - i * s.staffGap;
      c.drawLine(s.staffL, y, s.staffR, y, line);
    }
    const bar = paint('#E9D9C2', 0.32);
    bar.setStrokeWidth(1.2);
    c.drawLine(s.staffR, s.staffY, s.staffR, s.staffY - 4 * s.staffGap, bar);
    const meter = paint('#E9D9C2', 0.5);
    c.drawText('4', s.staffL + 2, s.staffY - 2 * s.staffGap - 1, meter, fonts.meter);
    c.drawText('4', s.staffL + 2, s.staffY + 0.5, meter, fonts.meter);

    // Fallboard rail: lacquer with a gold pinstripe, then the dark recess with the hammers' rail.
    const railY = s.recessTop - 14;
    const rail = Skia.Paint();
    rail.setShader(linear(0, railY, 0, s.recessTop, ['#2B211C', '#1A1311', '#100C0A']));
    c.drawRect(Skia.XYWHRect(0, railY, w, 14), rail);
    c.drawLine(0, railY + 0.5, w, railY + 0.5, paint('#FFDDB0', 0.16));
    const pin = paint('#C9A15E', 0.55);
    pin.setStrokeWidth(1);
    c.drawLine(10, railY + 7, w - 10, railY + 7, pin);
    const recess = Skia.Paint();
    recess.setShader(linear(0, s.recessTop, 0, s.feltY, ['#080605', '#140E0B', '#1E1611']));
    c.drawRect(Skia.XYWHRect(0, s.recessTop, w, s.feltY - s.recessTop), recess);
    c.drawRect(Skia.XYWHRect(0, s.recessTop, w, 8), blurred('#000000', 0.7, 4));
    // A brass rail the strings run over, across the back of the recess.
    const brass = Skia.Paint();
    brass.setShader(linear(0, s.recessTop + 3, 0, s.recessTop + 8, ['#7C5B2E', '#D8B678', '#6A4B24']));
    c.drawRect(Skia.XYWHRect(0, s.recessTop + 3, w, 5), brass);

    // The keybed floor in the gaps, and the keyslip under the keys.
    const bed = Skia.Paint();
    bed.setShader(linear(0, s.keyTop, 0, s.keyBottom, ['#0C0908', '#130E0C', '#0E0A09']));
    c.drawRect(Skia.XYWHRect(0, s.keyTop, w, s.keyBottom - s.keyTop + 8), bed);
    const slipY = s.keyBottom + 8;
    const slip = Skia.Paint();
    slip.setShader(linear(0, slipY, 0, slipY + 14, ['#2E231D', '#1C1512', '#140F0D']));
    c.drawRect(Skia.XYWHRect(0, slipY, w, 14), slip);
    c.drawLine(0, slipY + 0.5, w, slipY + 0.5, paint('#FFE2BC', 0.14));
  });
}

/** Where the overlay image sits (it only covers what it draws). */
export function overlayRect(s: Scene) {
  'worklet';
  return { x: 0, y: s.feltY - 4, w: s.w, h: s.keyBottom - s.feltY + 34 };
}

/** Felt strip and black keys: what sits in front of the hammers and over the ivory keys. */
export function bakeOverlay(s: Scene, scale: number) {
  const r = overlayRect(s);
  return bake(r.w, r.h, scale, (c) => {
    c.translate(-r.x, -r.y);
    const { w } = s;
    // Burgundy felt with a fuzzy nap and a soft shadow it throws onto the keys.
    const felt = Skia.Paint();
    felt.setShader(linear(0, s.feltY, 0, s.feltY + s.feltH, ['#7A2433', '#5E1A27', '#45121D']));
    c.drawRect(Skia.XYWHRect(0, s.feltY, w, s.feltH), felt);
    c.save();
    c.clipRect(Skia.XYWHRect(0, s.feltY, w, s.feltH), ClipOp.Intersect, false);
    c.drawRect(Skia.XYWHRect(0, s.feltY, w, s.feltH), grain(0.32, 1.6, 0.9, 11));
    c.restore();
    c.drawLine(0, s.feltY + 0.5, w, s.feltY + 0.5, paint('#FFB3BE', 0.22));
    // Black keys sit in the gaps, over the upper half of the ivory keys.
    const bw = Math.min(30, s.keyW * 0.26);
    const bh = (s.keyBottom - s.keyTop) * 0.52;
    for (let i = 0; i < 2; i += 1) {
      const cx = s.keyX[i + 1] - s.gap / 2;
      const x = cx - bw / 2;
      const y = s.keyTop - 2;
      // Two shadows on the ivory: tight contact and soft ambient, both falling down-screen.
      c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x - 3, y + 10, bw + 6, bh + 6), 6, 6), blurred('#1A0E06', 0.38, 9));
      c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x - 0.5, y, bw + 1, bh + 2), 4, 4), blurred('#000000', 0.6, 1.5));
      const body = Skia.Paint();
      body.setAntiAlias(true);
      body.setShader(linear(x, 0, x + bw, 0, ['#18130F', '#2C2520', '#1F1A16', '#110E0C'], [0, 0.3, 0.7, 1]));
      c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y, bw, bh), 4, 4), body);
      // The sloped front where a finger lands, catching a little light.
      const front = Skia.Paint();
      front.setAntiAlias(true);
      front.setShader(linear(0, y + bh - 18, 0, y + bh, ['#2A231E', '#3D342D', '#231D19']));
      c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x + 2, y + bh - 18, bw - 4, 16), 3, 3), front);
      // Gloss: a long specular strip down the left of the top face.
      const gloss = Skia.Paint();
      gloss.setAntiAlias(true);
      gloss.setShader(linear(0, y, 0, y + bh - 20, ['rgba(255,236,214,0.20)', 'rgba(255,236,214,0.07)', 'rgba(255,236,214,0)']));
      c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x + bw * 0.22, y + 4, bw * 0.16, bh - 26), 2, 2), gloss);
    }
  });
}

/** One ivory key's outline: square under the felt, rounded at the front, narrowing as it pivots. */
function keyPath(x: number, top: number, w: number, bottom: number, inset: number) {
  'worklet';
  const r = 7;
  const b = Skia.PathBuilder.Make();
  b.moveTo(x, top);
  b.lineTo(x + w, top);
  b.lineTo(x + w - inset, bottom - r);
  b.quadTo(x + w - inset, bottom, x + w - inset - r, bottom);
  b.lineTo(x + inset + r, bottom);
  b.quadTo(x + inset, bottom, x + inset, bottom - r);
  b.close();
  return b.build();
}

function drawKey(c: SkCanvas, s: Scene, i: number, k: KeyState, fonts: Fonts, grainPaint: SkPaint) {
  'worklet';
  const p = Math.max(-0.1, Math.min(1.15, k.depth));
  const x = s.keyX[i];
  const w = s.keyW;
  // Pivoting down 6° around the far edge: the near end drops away, so it foreshortens and narrows.
  const bottom = s.keyBottom - 5 * p;
  const inset = 3.2 * p;
  const lip = 7 - 4 * p;
  const glow = keyGlow(k);

  // Ambient shadow on the keyslip, smaller and tighter as the key goes down.
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x + 4 + inset, bottom + lip - 2, w - 8 - inset * 2, 10 - 4 * p), 5, 5), blurred('#000000', 0.55 - 0.15 * p, 6 - 2 * p));
  // Front lip (the key's front face), darker ivory.
  const lipPaint = Skia.Paint();
  lipPaint.setAntiAlias(true);
  lipPaint.setShader(linear(0, bottom - 8, 0, bottom + lip, ['#D8C8AB', '#C9B693', '#AE9A78']));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x + inset, bottom - 8, w - inset * 2, 8 + lip), 7, 7), lipPaint);

  const path = keyPath(x, s.keyTop, w, bottom, inset);
  const body = Skia.Paint();
  body.setAntiAlias(true);
  body.setShader(linear(0, s.keyTop, 0, bottom, ['#F7F0DF', '#F2E8D3', '#ECE0C8', '#E3D5B8'], [0, 0.4, 0.8, 1]));
  c.drawPath(path, body);
  c.save();
  c.clipPath(path, ClipOp.Intersect, true);
  // Ivory grain, side shading (a highlight on the left edge, the right edge in shade).
  c.drawRect(Skia.XYWHRect(x, s.keyTop, w, bottom - s.keyTop), grainPaint);
  const sides = Skia.Paint();
  sides.setShader(
    linear(x, 0, x + w, 0, ['rgba(255,255,250,0.55)', 'rgba(255,255,250,0)', 'rgba(80,56,30,0)', 'rgba(80,56,30,0.16)'], [0, 0.08, 0.86, 1]),
  );
  c.drawRect(Skia.XYWHRect(x, s.keyTop, w, bottom - s.keyTop), sides);
  // Going down it leaves the light: darker toward the front, and the neighbours shade its sides.
  if (p > 0.01) {
    const dip = Skia.Paint();
    dip.setShader(linear(0, s.keyTop, 0, bottom, ['rgba(60,38,18,0)', `rgba(60,38,18,${(0.11 * p).toFixed(3)})`]));
    c.drawRect(Skia.XYWHRect(x, s.keyTop, w, bottom - s.keyTop), dip);
    const walls = Skia.Paint();
    const a = (0.16 * p).toFixed(3);
    walls.setShader(linear(x, 0, x + w, 0, [`rgba(40,24,10,${a})`, 'rgba(40,24,10,0)', 'rgba(40,24,10,0)', `rgba(40,24,10,${a})`], [0, 0.1, 0.9, 1]));
    c.drawRect(Skia.XYWHRect(x, s.keyTop, w, bottom - s.keyTop), walls);
  }
  // The felt's shadow across the top tightens as the key's far end lifts toward it.
  const fs = Skia.Paint();
  fs.setShader(linear(0, s.keyTop, 0, s.keyTop + 16 - 7 * p, [`rgba(40,10,14,${(0.42 + 0.12 * p).toFixed(3)})`, 'rgba(40,10,14,0)']));
  c.drawRect(Skia.XYWHRect(x, s.keyTop, w, 18), fs);
  // A warm wash of the key's colour while it sounds.
  if (glow > 0.005) {
    const wash = Skia.Paint();
    wash.setShader(radial(x + w / 2, bottom - 60, w * 1.1, [KEY_COLORS[i], 'rgba(0,0,0,0)']));
    wash.setAlphaf(clamp01(glow * 0.55));
    wash.setBlendMode(BlendMode.Multiply);
    c.drawRect(Skia.XYWHRect(x, s.keyTop, w, bottom - s.keyTop), wash);
  }
  // Labels roll: the CTA leaves upward and fades, the note name comes up from below.
  const cx = x + w / 2;
  const base = bottom - 44;
  const win = Skia.XYWHRect(x + 4, base - 50, w - 8, 76);
  c.clipRect(win, ClipOp.Intersect, true);
  const r = clamp01(k.label);
  const scaleY = 1 - 0.02 * p;
  if (r < 0.999) {
    const up = -30 * r;
    const t = KEY_LABELS[i];
    const a = clamp01(1 - r * 1.7);
    const tp = paint(INK, a);
    c.drawText(t.title, cx - textWidth(fonts.title, t.title) / 2, base - 8 + up, tp, fonts.title);
    const sp = paint(INK_SOFT, a);
    c.drawText(t.sub, cx - textWidth(fonts.sub, t.sub) / 2, base + 12 + up, sp, fonts.sub);
  }
  if (r > 0.001) {
    const n = KEY_LABELS[i].note;
    const np = paint(INK, clamp01(r * 1.7 - 0.7));
    c.save();
    c.translate(cx, base + 8 + 44 * (1 - r));
    c.scale(1, scaleY);
    c.drawText(n, -textWidth(fonts.note, n) / 2, 0, np, fonts.note);
    c.restore();
  }
  c.restore();
  // A small marker of the key's colour above the label.
  const dot = paint(KEY_COLORS[i], 0.9);
  c.drawCircle(cx, base - 64, 3.5, dot);
}

function drawHammers(c: SkCanvas, s: Scene, pose: Pose) {
  'worklet';
  for (let i = 0; i < pose.keys.length; i += 1) {
    const k = pose.keys[i];
    const cx = s.keyX[i] + s.keyW / 2;
    // Strings: a unison of three per key, brightening and thickening while they ring.
    const ring = stringRing(k);
    const sp = paint('#D9B97F', 0.28 + 0.6 * ring);
    sp.setStrokeWidth(0.8 + 1.2 * ring);
    sp.setStrokeCap(StrokeCap.Round);
    for (let j = -1; j <= 1; j += 1) {
      const x = cx + j * 5;
      c.drawLine(x, s.recessTop + 4, x, s.feltY, sp);
    }
    if (ring > 0.02) {
      const g = blurred(KEY_COLORS[i], 0.35 * ring, 5);
      c.drawRect(Skia.XYWHRect(cx - 9, s.recessTop + 4, 18, s.feltY - s.recessTop - 4), g);
    }
    // The hammer: walnut molding with a cream felt head, flicking up toward the strings.
    const lift = hammerLift(k.downAge);
    const hw = 40;
    const hh = 26;
    const y = s.feltY - 16 - 14 * lift;
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx - hw / 2, y + 6, hw, hh), 9, 9), blurred('#000000', 0.55, 4 + 2 * lift));
    const wood = Skia.Paint();
    wood.setAntiAlias(true);
    wood.setShader(linear(cx - hw / 2, 0, cx + hw / 2, 0, ['#4A3121', '#7A5236', '#3C281B']));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx - hw / 2, y, hw, hh), 9, 9), wood);
    const head = Skia.Paint();
    head.setAntiAlias(true);
    head.setShader(linear(0, y, 0, y + 12, ['#F1E6D2', '#DCCDB3', '#BBA88A']));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx - hw / 2 + 3, y + 1, hw - 6, 12), 6, 6), head);
  }
}

function drawRings(c: SkCanvas, s: Scene, pose: Pose) {
  'worklet';
  for (let i = 0; i < pose.keys.length; i += 1) {
    const k = pose.keys[i];
    if (k.downAge > RING_LIFE + RING_DELAY * 2) continue;
    const cx = s.keyX[i] + s.keyW / 2;
    const cy = s.keyTop + (s.keyBottom - s.keyTop) * 0.62;
    for (let j = 0; j < 3; j += 1) {
      const age = k.downAge - j * RING_DELAY;
      if (age <= 0 || age >= RING_LIFE) continue;
      const u = age / RING_LIFE;
      const radius = 22 + s.w * 0.85 * easeOut(u);
      const fade = Math.pow(1 - u, 1.6) * (1 - j * 0.22);
      const glow = blurred(KEY_COLORS[i], 0.5 * fade, 7);
      glow.setStyle(PaintStyle.Stroke);
      glow.setStrokeWidth(10);
      glow.setBlendMode(BlendMode.Screen);
      c.drawCircle(cx, cy, radius, glow);
      const line = paint(KEY_COLORS[i], 0.95 * fade);
      line.setStyle(PaintStyle.Stroke);
      line.setStrokeWidth(3 - 2 * u);
      line.setBlendMode(BlendMode.Screen);
      c.drawCircle(cx, cy, radius, line);
    }
  }
}

function drawNotes(c: SkCanvas, s: Scene, pose: Pose) {
  'worklet';
  const g = s.staffGap;
  const x0 = s.staffL + 30;
  const step = (s.staffR - 14 - x0) / 3;
  // Steps above the bottom line (E4 = 0): C4 = -2, E4 = 0, G4 = 2; the chord stacks C4 and G4.
  const heads: number[][] = [[-2], [0], [2], [-2, 2]];
  const colors = [KEY_COLORS[0], KEY_COLORS[1], KEY_COLORS[2], KEY_COLORS[0]];
  for (let n = 0; n < 4; n += 1) {
    const x = x0 + step * n;
    const lit = clamp01(pose.notes[n]);
    for (let m = 0; m < heads[n].length; m += 1) {
      const st = heads[n][m];
      const y = s.staffY - (st * g) / 2;
      if (st === -2) {
        const ledger = paint('#E9D9C2', 0.45);
        ledger.setStrokeWidth(1);
        c.drawLine(x - 8, y, x + 8, y, ledger);
      }
      const col = n === 3 && st === 2 ? KEY_COLORS[2] : colors[n];
      if (lit > 0.01) c.drawCircle(x, y, 10, blurred(col, 0.55 * lit, 6));
      c.save();
      c.rotate(-20, x, y);
      const head = paint(lit > 0.5 ? col : '#E9D9C2', 0.5 + 0.5 * lit);
      c.drawOval(Skia.XYWHRect(x - 5, y - 3.6, 10, 7.2), head);
      c.restore();
    }
    const topStep = heads[n][heads[n].length - 1];
    const stem = paint(lit > 0.5 ? colors[n] : '#E9D9C2', 0.5 + 0.5 * lit);
    stem.setStrokeWidth(1.2);
    const sy = s.staffY - (topStep * g) / 2;
    c.drawLine(x + 4.6, sy - 1, x + 4.6, sy - 24, stem);
  }
}

export type Layers = { background: SkImage; overlay: SkImage };

/** One frame: the baked layers with the moving parts between and over them. */
export function drawFrame(c: SkCanvas, s: Scene, layers: Layers, pose: Pose, fonts: Fonts, grainPaint: SkPaint) {
  'worklet';
  const full = Skia.XYWHRect(0, 0, s.w, s.h);
  const ip = Skia.Paint();
  drawImg(c, layers.background, Skia.XYWHRect(0, 0, layers.background.width(), layers.background.height()), full, ip);
  drawNotes(c, s, pose);
  c.save();
  c.clipRect(Skia.XYWHRect(0, s.recessTop, s.w, s.feltY - s.recessTop), ClipOp.Intersect, true);
  drawHammers(c, s, pose);
  c.restore();
  for (let i = 0; i < pose.keys.length; i += 1) drawKey(c, s, i, pose.keys[i], fonts, grainPaint);
  const overlayAt = overlayRect(s);
  drawImg(c, layers.overlay, Skia.XYWHRect(0, 0, layers.overlay.width(), layers.overlay.height()), Skia.XYWHRect(overlayAt.x, overlayAt.y, overlayAt.w, overlayAt.h), ip);
  drawRings(c, s, pose);
}

/** The ivory grain, made once: long faint streaks along the key. */
export function makeIvoryGrain() {
  return grain(0.1, 0.9, 0.04, 5);
}
