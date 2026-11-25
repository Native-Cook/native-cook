import { FilterMode, MipmapMode, PaintStyle, Skia, TileMode } from '@shopify/react-native-skia';
import type { SkCanvas, SkFont, SkImage } from '@shopify/react-native-skia';

import { STATUS } from './yoyoMotion';
import type { Frame } from './yoyoMotion';

/*
 Yo-yo Pull Refresh — drawing. Design space is 393 × 852 pt. On another screen the layout is centred
 (x + ox) and vertical positions scale by height / 852 (`fy`); the list offset d and the yo-yo's
 travel are in pt.
*/

export const YOYO = require('./assets/yoyo.webp');
export const INTER_DISPLAY_BOLD = require('./assets/InterDisplay-Bold.ttf');
export const INTER_MEDIUM = require('./assets/Inter-Medium.ttf');
export const INTER_SEMIBOLD = require('./assets/Inter-SemiBold.ttf');

export const TITLE_SIZE = 40;

export const CARDS: [string, string, string][] = [
  ['#2B6BFF', 'Weekend plans', 'Ines · 2 min'],
  ['#FF5C8A', 'Your order shipped', 'Parcel 7 · 1 h'],
  ['#36D39A', 'New release notes', 'Studio · 3 h'],
  ['#FFB21E', 'Lunch?', 'Kai · 5 h'],
  ['#9B6BFF', 'Flight check-in', 'Wren Air · 6 h'],
  ['#FF7A3D', 'Rent reminder', 'Home · 1 d'],
  ['#3DC8FF', 'Photos from Sunday', 'Mira · 2 d'],
];
export const CARD_X = 20;
export const CARD_W = 353;
export const CARD_H = 88;
export const CARD_TOP = 170;
export const CARD_STEP = 100;
/** Room under the last card when the list is scrolled to its end. */
const LIST_PAD = 24;

/** The yo-yo's string line and its size. */
const YOYO_X = 260;
const YOYO_SIZE = 140;
const BLUR_COPIES = 7;

export type Fonts = {
  title: SkFont;
  status: SkFont;
  cardTitle: SkFont;
  cardMeta: SkFont;
};

export type Layout = {
  ox: number;
  fy: number;
  width: number;
  height: number;
  /** How far the list can scroll up before it rubber-bands. */
  overflow: number;
};

export function makeLayout(width: number, height: number): Layout {
  const fy = height / 852;
  const bottom = (CARD_TOP + CARD_STEP * (CARDS.length - 1)) * fy + CARD_H + LIST_PAD;
  return { ox: (width - 393) / 2, fy, width, height, overflow: Math.max(0, bottom - height) };
}

export function cardTop(L: Layout, i: number) {
  'worklet';
  return (CARD_TOP + CARD_STEP * i) * L.fy;
}

/** Text with tracking, from glyph widths (no rounding per glyph). */
export function drawTracked(
  canvas: SkCanvas,
  font: SkFont,
  text: string,
  x: number,
  y: number,
  tracking: number,
  color: string,
) {
  'worklet';
  const ids = font.getGlyphIDs(text);
  const widths = font.getGlyphWidths(ids);
  const positions = [];
  let cx = 0;
  for (let i = 0; i < ids.length; i++) {
    positions.push({ x: cx, y: 0 });
    cx += widths[i] + tracking;
  }
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(Skia.Color(color));
  canvas.drawGlyphs(ids, positions, x, y, font, paint);
}

/** Title and the status line, which cross-fades: the old line fades out, then the new one in. */
export function drawHeader(canvas: SkCanvas, L: Layout, fonts: Fonts, frame: Frame) {
  'worklet';
  drawTracked(canvas, fonts.title, 'Inbox', L.ox + 28, 98 * L.fy, -1, '#FFFFFF');
  const [from, to, u] = frame.status;
  const text = u < 0.5 ? STATUS[from] : STATUS[to];
  const alpha = u < 0.5 ? 1 - u * 2 : (u - 0.5) * 2;
  if (alpha <= 0) return;
  drawTracked(canvas, fonts.status, text, L.ox + 30, 124 * L.fy, 0, `rgba(123,127,146,${alpha})`);
}

export function drawCards(canvas: SkCanvas, L: Layout, fonts: Fonts, d: number) {
  'worklet';
  const card = Skia.Paint();
  card.setAntiAlias(true);
  card.setColor(Skia.Color('#121318'));
  const icon = Skia.Paint();
  icon.setAntiAlias(true);
  for (let i = 0; i < CARDS.length; i++) {
    const top = cardTop(L, i) + d;
    if (top > L.height || top + CARD_H < 0) continue;
    const x = L.ox + CARD_X;
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, top, CARD_W, CARD_H), 24, 24), card);
    icon.setColor(Skia.Color(CARDS[i][0]));
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x + 18, top + 18, 52, 52), 16, 16), icon);
    drawTracked(canvas, fonts.cardTitle, CARDS[i][1], x + 86, top + 40, 0, '#F1F2F7');
    drawTracked(canvas, fonts.cardMeta, CARDS[i][2], x + 86, top + 64, 0, '#7B7F92');
  }
}

/**
 The yo-yo: a blue glow that pulses with the spin, the string from the top edge, the image turned
 by its angle with a rotational blur (seven copies over the angle it turns in 0.8 / 30 s, averaged),
 and a sheen that does not turn.
*/
export function drawYoyo(canvas: SkCanvas, L: Layout, image: SkImage | null, frame: Frame) {
  'worklet';
  const x = L.ox + YOYO_X;
  const y = frame.y * L.fy;
  if (y <= -60 * L.fy || frame.alpha <= 0) return;
  const layered = frame.alpha < 1;
  if (layered) {
    const layer = Skia.Paint();
    layer.setAlphaf(frame.alpha);
    canvas.saveLayer(layer);
  }

  const p = (frame.spin / 5) * (0.75 + 0.25 * Math.sin(frame.pulse * 2 * Math.PI * 2));
  const glow = Skia.Paint();
  glow.setShader(
    Skia.Shader.MakeRadialGradient(
      { x, y },
      120,
      [Skia.Color(`rgba(46,107,255,${0.38 * p})`), Skia.Color('rgba(46,107,255,0)')],
      [10 / 120, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawRect(Skia.XYWHRect(x - 125, y - 125, 250, 250), glow);

  const string = Skia.Paint();
  string.setAntiAlias(true);
  string.setStyle(PaintStyle.Stroke);
  string.setStrokeWidth(1.5);
  string.setColor(Skia.Color('rgba(242,242,242,0.85)'));
  canvas.drawLine(x, -2, x, y, string);

  if (image) {
    const angle = frame.turns * 2 * Math.PI;
    const span = ((frame.spin * 2 * Math.PI) / 30) * 0.8;
    const src = Skia.XYWHRect(0, 0, image.width(), image.height());
    const dst = Skia.XYWHRect(-YOYO_SIZE / 2, -YOYO_SIZE / 2, YOYO_SIZE, YOYO_SIZE);
    const paint = Skia.Paint();
    paint.setAntiAlias(true);
    for (let k = 0; k < BLUR_COPIES; k++) {
      const a = angle + span * (k / (BLUR_COPIES - 1) - 0.5);
      paint.setAlphaf(1 / (k + 1));
      canvas.save();
      canvas.translate(x, y);
      canvas.rotate((a * 180) / Math.PI, 0, 0);
      canvas.drawImageRectOptions(image, src, dst, FilterMode.Linear, MipmapMode.Linear, paint);
      canvas.restore();
    }
  }

  const sheen = Skia.Paint();
  sheen.setAntiAlias(true);
  sheen.setShader(
    Skia.Shader.MakeRadialGradient(
      { x: x - 26, y: y - 28 },
      52,
      [Skia.Color('rgba(255,255,255,0.28)'), Skia.Color('rgba(255,255,255,0)')],
      [2 / 52, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawCircle(x, y, 69, sheen);
  if (layered) canvas.restore();
}

/** The demo finger on the list in the loop. */
export function drawFinger(canvas: SkCanvas, L: Layout, frame: Frame) {
  'worklet';
  if (frame.finger <= 0) return;
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(Skia.Color(`rgba(255,255,255,${0.3 * frame.finger})`));
  canvas.drawCircle(L.ox + YOYO_X, 320 * L.fy + frame.d, 24, paint);
}
