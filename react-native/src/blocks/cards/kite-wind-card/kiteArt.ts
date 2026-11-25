import { BlurStyle, FilterMode, MipmapMode, PaintStyle, Skia, TileMode } from '@shopify/react-native-skia';
import type { SkCanvas, SkFont, SkImage } from '@shopify/react-native-skia';

import { HOURS, WIND, hourLabel, windAt } from './kiteMotion';
import type { Frame } from './kiteMotion';

/*
 Kite Wind Card — drawing. Design space is 393 × 852 pt. On another screen the layout is centred
 (x + ox) and vertical positions scale by height / 852 (`fy`); the hour card keeps its inner layout.
 Shadows follow the reference render, whose blur and offsets are pixels of its 3× canvas:
 kite σ 2 pt, 8/3 pt straight down; card σ 13/3 pt, 8/3 pt down.
*/

export const KITE = require('./assets/kite.webp');
export const CLOUDS = require('./assets/clouds.webp');
export const INTER_DISPLAY_BOLD = require('./assets/InterDisplay-Bold.ttf');
export const INTER_MEDIUM = require('./assets/Inter-Medium.ttf');
export const INTER_SEMIBOLD = require('./assets/Inter-SemiBold.ttf');

const KITE_W = 118;
const KITE_H = 161.2;
const CARD_TOP = 694;
const BAR_X0 = 40;
const BAR_DX = 31.4;

export type Fonts = {
  eyebrow: SkFont;
  number: SkFont;
  unit: SkFont;
  note: SkFont;
  today: SkFont;
  cardValue: SkFont;
  hour: SkFont;
};

export type Layout = {
  ox: number;
  fy: number;
  width: number;
  height: number;
};

export function makeLayout(width: number, height: number): Layout {
  return { ox: (width - 393) / 2, fy: height / 852, width, height };
}

/** The card's top on screen; the card's inner layout is in pt from it. */
export function cardTop(L: Layout) {
  'worklet';
  return CARD_TOP * L.fy;
}

/** Text with tracking, from glyph widths; align 0 left, 0.5 centre, 1 right. */
export function drawText(
  canvas: SkCanvas,
  font: SkFont,
  text: string,
  x: number,
  y: number,
  color: string,
  tracking = 0,
  align = 0,
) {
  'worklet';
  const ids = font.getGlyphIDs(text);
  const widths = font.getGlyphWidths(ids);
  let w = 0;
  for (let i = 0; i < widths.length; i++) w += widths[i] + (i < widths.length - 1 ? tracking : 0);
  const positions = [];
  let cx = -w * align;
  for (let i = 0; i < ids.length; i++) {
    positions.push({ x: cx, y: 0 });
    cx += widths[i] + tracking;
  }
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(Skia.Color(color));
  canvas.drawGlyphs(ids, positions, x, y, font, paint);
}

export function drawSky(canvas: SkCanvas, L: Layout) {
  'worklet';
  const paint = Skia.Paint();
  paint.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: 0, y: 0 },
      { x: 0, y: L.height },
      [Skia.Color('#1E8EF0'), Skia.Color('#3FB0FF'), Skia.Color('#8FD3FF')],
      [0, 0.6, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawRect(Skia.XYWHRect(0, 0, L.width, L.height), paint);
}

/** The kite's pose: centre, rotation (rad) and the sway strength, from the wind and the clock. */
export function kitePose(L: Layout, frame: Frame): [number, number, number] {
  'worklet';
  const wn = (windAt(frame.p) - 10) / 16;
  const ph = (2 * Math.PI * frame.phaseT) / 8;
  const s = 0.25 + 0.75 * wn;
  const kx = L.ox + 300 + 10 * s * Math.sin(ph * 3) + 4 * Math.sin(ph * 7) + frame.pullX;
  const ky = (196 - 18 * wn + 8 * s * Math.sin(ph * 5 + 1) + 3 * Math.cos(ph * 4)) * L.fy + frame.pullY;
  const rot = ((14 + 6 * s * Math.sin(ph * 3 + 0.6) + 2 * Math.sin(ph * 8)) * Math.PI) / 180;
  return [kx, ky, rot];
}

/** A point in the kite's frame on screen. */
function kitePoint(kx: number, ky: number, rot: number, x: number, y: number): [number, number] {
  'worklet';
  return [kx + x * Math.cos(rot) - y * Math.sin(rot), ky + x * Math.sin(rot) + y * Math.cos(rot)];
}

/** The line to the lower left, sagging less in stronger wind; then the tail of bows; then the kite. */
export function drawKite(canvas: SkCanvas, L: Layout, kite: SkImage | null, frame: Frame) {
  'worklet';
  const wn = (windAt(frame.p) - 10) / 16;
  const ph = (2 * Math.PI * frame.phaseT) / 8;
  const [kx, ky, rot] = kitePose(L, frame);
  const bridle = kitePoint(kx, ky, rot, 0, 8);
  const bottom = kitePoint(kx, ky, rot, 0, KITE_H / 2 - 2);

  const ax = L.ox - 30;
  const ay = 820 * L.fy;
  const sag = 60 * (1 - wn) + 18;
  const line = Skia.Path.Make();
  line.moveTo(bridle[0], bridle[1]);
  line.quadTo((bridle[0] + ax) / 2 + sag * 0.6, (bridle[1] + ay) / 2 + sag, ax, ay);
  const linePaint = Skia.Paint();
  linePaint.setAntiAlias(true);
  linePaint.setStyle(PaintStyle.Stroke);
  linePaint.setStrokeWidth(1);
  linePaint.setColor(Skia.Color('rgba(255,255,255,0.85)'));
  canvas.drawPath(line, linePaint);

  const xs = [bottom[0]];
  const ys = [bottom[1]];
  const ang = rot + Math.PI / 2 - 0.15 + 0.25 * (1 - wn);
  for (let i = 1; i <= 10; i++) {
    const d = i * 21;
    const wv = (7 + 12 * wn) * Math.sin(ph * 5 - i * 0.75) * (i / 10 + 0.3);
    xs.push(bottom[0] + Math.cos(ang) * d + Math.cos(ang + Math.PI / 2) * wv);
    ys.push(bottom[1] + Math.sin(ang) * d + Math.sin(ang + Math.PI / 2) * wv);
  }
  const tail = Skia.Path.Make();
  tail.moveTo(xs[0], ys[0]);
  for (let i = 1; i < xs.length; i++) tail.lineTo(xs[i], ys[i]);
  const tailPaint = Skia.Paint();
  tailPaint.setAntiAlias(true);
  tailPaint.setStyle(PaintStyle.Stroke);
  tailPaint.setStrokeWidth(1.4);
  tailPaint.setColor(Skia.Color('rgba(255,255,255,0.9)'));
  canvas.drawPath(tail, tailPaint);

  const bow = Skia.Path.Make();
  bow.moveTo(0, 0);
  bow.lineTo(-9, -5.5);
  bow.lineTo(-9, 5.5);
  bow.close();
  bow.moveTo(0, 0);
  bow.lineTo(9, -5.5);
  bow.lineTo(9, 5.5);
  bow.close();
  const bowPaint = Skia.Paint();
  bowPaint.setAntiAlias(true);
  for (let i = 1; i < xs.length; i++) {
    const a = Math.atan2(ys[i] - ys[i - 1], xs[i] - xs[i - 1]) + Math.PI / 2 + 0.4 * Math.sin(ph * 5 - i);
    bowPaint.setColor(Skia.Color(i % 2 ? '#FFD21E' : '#FFB800'));
    canvas.save();
    canvas.translate(xs[i], ys[i]);
    canvas.rotate((a * 180) / Math.PI, 0, 0);
    canvas.drawPath(bow, bowPaint);
    canvas.restore();
  }

  if (!kite) return;
  const paint = Skia.Paint();
  // The shadow falls straight down on screen: undo the kite's rotation for its offset.
  const off = 8 / 3;
  paint.setImageFilter(
    Skia.ImageFilter.MakeDropShadow(off * Math.sin(rot), off * Math.cos(rot), 2, 2, Skia.Color('rgba(0,60,140,0.25)'), null),
  );
  canvas.save();
  canvas.translate(kx, ky);
  canvas.rotate((rot * 180) / Math.PI, 0, 0);
  canvas.drawImageRectOptions(
    kite,
    Skia.XYWHRect(0, 0, kite.width(), kite.height()),
    Skia.XYWHRect(-KITE_W / 2, -KITE_H / 2, KITE_W, KITE_H),
    FilterMode.Linear,
    MipmapMode.Linear,
    paint,
  );
  canvas.restore();
}

/** The cloud bank drifting ±8 pt over the loop. */
export function drawClouds(canvas: SkCanvas, L: Layout, clouds: SkImage | null, frame: Frame) {
  'worklet';
  if (!clouds) return;
  const ph = (2 * Math.PI * frame.phaseT) / 8;
  canvas.drawImageRectOptions(
    clouds,
    Skia.XYWHRect(0, 0, clouds.width(), clouds.height()),
    Skia.XYWHRect(L.ox - 50 + 8 * Math.sin(ph), 590 * L.fy, 491, 221.8),
    FilterMode.Linear,
    MipmapMode.Linear,
    null,
  );
}

/** Eyebrow, the big number, the unit line and the note. */
export function drawHeadline(canvas: SkCanvas, L: Layout, fonts: Fonts, frame: Frame) {
  'worklet';
  const w = windAt(frame.p);
  drawText(canvas, fonts.eyebrow, 'HARBOUR PARK · NE WIND', L.ox + 30, 92 * L.fy, '#DFF1FF', 4);
  drawText(canvas, fonts.number, String(Math.round(w)), L.ox + 26, 214 * L.fy, '#FFFFFF', -7);
  drawText(canvas, fonts.unit, `km/h · gusts ${Math.round(w * 1.4)}`, L.ox + 30, 250 * L.fy, '#FFFFFF');
  drawText(
    canvas,
    fonts.note,
    w < 24 ? 'Good kite weather until 6 pm.' : 'Strong gusts: hold on tight.',
    L.ox + 30,
    280 * L.fy,
    '#E6F4FF',
  );
}

/** The hour card: today's selected hour, the bars, the scrubber, the hour labels and the demo finger. */
export function drawCard(canvas: SkCanvas, L: Layout, fonts: Fonts, frame: Frame) {
  'worklet';
  const top = cardTop(L);
  const y = (v: number) => top + (v - CARD_TOP);
  const shadow = Skia.Paint();
  shadow.setAntiAlias(true);
  shadow.setColor(Skia.Color('rgba(0,60,140,0.18)'));
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 13 / 3, true));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(L.ox + 20, top + 8 / 3, 353, 128), 26, 26), shadow);
  const card = Skia.Paint();
  card.setAntiAlias(true);
  card.setColor(Skia.Color('#F6FBFF'));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(L.ox + 20, top, 353, 128), 26, 26), card);

  const p = frame.p;
  const w = windAt(p);
  drawText(canvas, fonts.today, 'Today', L.ox + 40, y(724), '#0E3A66');
  const hour = HOURS[Math.min(9, Math.max(0, Math.round(p)))];
  drawText(canvas, fonts.cardValue, `${hourLabel(hour)} · ${Math.round(w)} km/h`, L.ox + 353, y(724), '#5D86AE', 0, 1);

  const bar = Skia.Paint();
  bar.setAntiAlias(true);
  for (let i = 0; i < WIND.length; i++) {
    const h = 8 + WIND[i] * 1.5;
    bar.setColor(Skia.Color(Math.abs(i - p) < 0.5 ? '#1E8EF0' : '#CFE6FA'));
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(L.ox + BAR_X0 + i * BAR_DX + 6, y(792) - h, 12, h), 4, 4), bar);
  }
  const sx = L.ox + BAR_X0 + p * BAR_DX + 12;
  const scrub = Skia.Paint();
  scrub.setAntiAlias(true);
  scrub.setColor(Skia.Color('#1E8EF0'));
  scrub.setStyle(PaintStyle.Stroke);
  scrub.setStrokeWidth(2);
  canvas.drawLine(sx, y(742), sx, y(796), scrub);
  scrub.setStyle(PaintStyle.Fill);
  canvas.drawCircle(sx, y(742), 5, scrub);
  const marks = [0, 3, 6, 9];
  for (let k = 0; k < marks.length; k++) {
    const i = marks[k];
    drawText(canvas, fonts.hour, hourLabel(HOURS[i]), L.ox + BAR_X0 + i * BAR_DX + 12, y(812), '#5D86AE', 0, 0.5);
  }
  if (frame.finger > 0) {
    const f = Skia.Paint();
    f.setAntiAlias(true);
    f.setColor(Skia.Color(`rgba(14,58,102,${0.2 * frame.finger})`));
    canvas.drawCircle(sx, y(768), 22, f);
  }
}

/** Hit tests (screen points). */
export function cardHit(L: Layout, x: number, y: number) {
  'worklet';
  const top = cardTop(L);
  return x >= L.ox + 20 && x <= L.ox + 373 && y >= top && y <= top + 128;
}

export function kiteHit(L: Layout, frame: Frame, x: number, y: number) {
  'worklet';
  const [kx, ky] = kitePose(L, frame);
  return Math.hypot(x - kx, y - ky) <= 80;
}
