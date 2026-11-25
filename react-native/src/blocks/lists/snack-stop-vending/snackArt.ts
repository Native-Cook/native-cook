import { BlurStyle, ClipOp, FilterMode, MipmapMode, PaintStyle, Skia, StrokeCap, TileMode } from '@shopify/react-native-skia';
import type { SkCanvas, SkFont, SkImage, SkPicture } from '@shopify/react-native-skia';

import { COLS, DRINKS, KEYS, ROWS, canIndex, slotCode } from './snackMotion';
import type { Frame } from './snackMotion';

/*
 Snack Stop Vending — drawing. Design space is 393 × 852 pt. On another screen the layout is centred
 (x + ox) and vertical positions scale by height / 852 (`fy`); the machine keeps its size and moves as
 one group anchored at its top. "Machine px" are pixels of the 930 × 1382 artwork (machine.webp is it
 at half size): X = 16.5 + (x − 45)·K, Y = 110 + (y − 105)·K, K = 360 / 930. Blur radii of the
 reference are in pixels of its 3× canvas, hence sigmas of r / 3 pt (and r / 6 for shadowBlur).
*/

export const MACHINE = require('./assets/machine.webp');
export const CANS = [
  require('./assets/can0.webp'),
  require('./assets/can1.webp'),
  require('./assets/can2.webp'),
  require('./assets/can3.webp'),
  require('./assets/can4.webp'),
];
export const TITAN = require('./assets/TitanOne-Regular.ttf');
export const ONEST = require('./assets/Onest-ExtraBold.ttf');
export const INTER_MEDIUM = require('./assets/Inter-Medium.ttf');
export const INTER_SEMIBOLD = require('./assets/Inter-SemiBold.ttf');
export const MONO = require('./assets/JetBrainsMono-Medium.ttf');

const K = 360 / 930;
const COIL_X = [191, 306, 419, 530, 642];
const SHELF_Y = [416, 605, 791, 974];
const KEY_X = [766, 832, 897];
const KEY_Y = [497, 567, 638, 710];
/** Can size on screen. */
export const CAN_W = 92 * K;
export const CAN_H = ((92 * 159) / 96) * K;
/** The tray opening (machine px 135–712 × 1150–1335). */
const TRAY = [135, 1150, 712, 1335];

export type Fonts = {
  title: SkFont;
  sub: SkFont;
  name: SkFont;
  line: SkFont;
  pill: SkFont;
  lcd: SkFont;
  key: SkFont;
};

export type Layout = {
  ox: number;
  fy: number;
  /** Vertical offset of the machine group. */
  gy: number;
  width: number;
  height: number;
};

export function makeLayout(width: number, height: number): Layout {
  const fy = height / 852;
  return { ox: (width - 393) / 2, fy, gy: 110 * (fy - 1), width, height };
}

/** A machine pixel on screen. */
export function mx(L: Layout, x: number) {
  'worklet';
  return L.ox + 16.5 + (x - 45) * K;
}

export function my(L: Layout, y: number) {
  'worklet';
  return L.gy + 110 + (y - 105) * K;
}

/** A slot's can: bottom-centre on its coil. */
export function slotPoint(L: Layout, slot: number): [number, number] {
  'worklet';
  return [mx(L, COIL_X[slot % COLS]), my(L, SHELF_Y[Math.floor(slot / COLS)])];
}

export function keyPoint(L: Layout, key: number): [number, number] {
  'worklet';
  return [mx(L, KEY_X[key % 3]), my(L, KEY_Y[Math.floor(key / 3)])];
}

export function trayRect(L: Layout): [number, number, number, number] {
  'worklet';
  return [mx(L, TRAY[0]), my(L, TRAY[1]), mx(L, TRAY[2]), my(L, TRAY[3])];
}

/** The static layer, recorded once: background gradient, the machine's shadow, the machine. */
export function recordBase(L: Layout, machine: SkImage): SkPicture {
  const recorder = Skia.PictureRecorder();
  const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, L.width, L.height));
  const bg = Skia.Paint();
  bg.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: 0, y: 0 },
      { x: 0, y: L.height },
      [Skia.Color('#FFE2CF'), Skia.Color('#FFC9AE')],
      null,
      TileMode.Clamp,
    ),
  );
  canvas.drawRect(Skia.XYWHRect(0, 0, L.width, L.height), bg);
  const shadow = Skia.Paint();
  shadow.setAntiAlias(true);
  shadow.setColor(Skia.Color('rgba(150,40,20,0.25)'));
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 14 / 3, true));
  canvas.drawOval(Skia.XYWHRect(L.ox + 196.5 - 170, L.gy + 652 - 14, 340, 28), shadow);
  canvas.drawImageRectOptions(
    machine,
    Skia.XYWHRect(0, 0, machine.width(), machine.height()),
    Skia.XYWHRect(L.ox + 16.5, L.gy + 110, 930 * K, 1382 * K),
    FilterMode.Linear,
    MipmapMode.Linear,
    null,
  );
  return recorder.finishRecordingAsPicture();
}

/** A can, bottom-centred at (x, y), scaled and turned about that point. */
function drawCan(canvas: SkCanvas, image: SkImage | null, x: number, y: number, s: number, rot: number, alpha: number) {
  'worklet';
  if (!image || alpha <= 0) return;
  const paint = Skia.Paint();
  paint.setAlphaf(alpha);
  canvas.save();
  canvas.translate(x, y);
  if (rot !== 0) canvas.rotate((rot * 180) / Math.PI, 0, 0);
  canvas.drawImageRectOptions(
    image,
    Skia.XYWHRect(0, 0, image.width(), image.height()),
    Skia.XYWHRect((-CAN_W * s) / 2, -CAN_H * s, CAN_W * s, CAN_H * s),
    FilterMode.Linear,
    MipmapMode.Linear,
    paint,
  );
  canvas.restore();
}

/** The coil's front turns over a can: two arcs, 8 and 17 pt above its bottom. */
function drawCoilFront(canvas: SkCanvas, x: number, y: number) {
  'worklet';
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setStyle(PaintStyle.Stroke);
  paint.setStrokeWidth(1.3);
  paint.setColor(Skia.Color('rgba(225,228,232,0.85)'));
  const rx = CAN_W * 0.66;
  for (let k = 0; k < 2; k++) {
    const cy = y - 8 - k * 9;
    const path = Skia.Path.Make();
    path.addArc(Skia.XYWHRect(x - rx, cy - 4.5, rx * 2, 9), (0.15 * 180) / Math.PI, ((Math.PI - 0.3) * 180) / Math.PI);
    canvas.drawPath(path, paint);
  }
}

/** Where the dispensed can lies in the tray: 40 pt left of its slot, kept inside the opening. */
export function trayX(L: Layout, slot: number) {
  'worklet';
  const [sx] = slotPoint(L, slot);
  const [x0, , x1] = trayRect(L);
  return Math.min(Math.max(sx - 40, x0 + 4), x1 - CAN_H * 1.12 - 4);
}

/**
 The cans: the 19 others in their slots, then the dispensed one (forward, tipping, falling in front
 of the lower rows, clipped to the tray once inside, the flap's veil over it), the refill, and every
 coil front over its can.
*/
export function drawCans(canvas: SkCanvas, L: Layout, cans: (SkImage | null)[], frame: Frame) {
  'worklet';
  const sel = frame.slot;
  for (let slot = 0; slot < ROWS * COLS; slot++) {
    if (slot === sel && frame.can.kind !== 0) continue;
    const [x, y] = slotPoint(L, slot);
    drawCan(canvas, cans[canIndex(slot)], x, y, 1, 0, 1);
    if (slot !== sel) drawCoilFront(canvas, x, y);
  }
  if (sel < 0) return;
  const [sx, sy] = slotPoint(L, sel);
  const image = cans[canIndex(sel)];
  const c = frame.can;
  const [tx0, ty0, tx1, ty1] = trayRect(L);
  const floor = ty1 - 6;
  if (c.kind === 2) drawCan(canvas, image, sx, sy, c.scale, 0, c.alpha);
  if (c.kind === 1) {
    const x = sx + (trayX(L, sel) - sx) * c.fall;
    let y = sy + (floor - sy) * c.drop;
    if (c.fall >= 1) y = floor - c.bounce + c.taken;
    canvas.save();
    if (c.clip) canvas.clipRect(Skia.XYWHRect(tx0, ty0 + 2, tx1 - tx0, ty1 - ty0 - 2), ClipOp.Intersect, true);
    drawCan(canvas, image, x, y, c.scale, c.rot, c.alpha);
    canvas.restore();
  }
  if (frame.trayAlpha > 0) {
    canvas.save();
    canvas.clipRect(Skia.XYWHRect(tx0, ty0 + 2, tx1 - tx0, ty1 - ty0 - 2), ClipOp.Intersect, true);
    drawCan(canvas, image, trayX(L, sel), floor, 1.12, Math.PI / 2, frame.trayAlpha);
    canvas.restore();
  }
  if (c.veil || frame.trayAlpha > 0) {
    const veil = Skia.Paint();
    veil.setColor(Skia.Color('rgba(255,255,255,0.10)'));
    canvas.drawRect(Skia.XYWHRect(tx0, ty0, tx1 - tx0, (ty1 - ty0) * 0.55), veil);
  }
  drawCoilFront(canvas, sx, sy);
  if (frame.glint >= 0) {
    const u = frame.glint;
    const paint = Skia.Paint();
    paint.setAntiAlias(true);
    paint.setStyle(PaintStyle.Stroke);
    paint.setStrokeWidth(2);
    paint.setColor(Skia.Color(`rgba(255,255,255,${0.9 * Math.sin(u * Math.PI)})`));
    const r = CAN_W * 0.7;
    const path = Skia.Path.Make();
    path.addArc(Skia.XYWHRect(sx - r, sy - 12 - r, r * 2, r * 2), (u * Math.PI * 4 * 180) / Math.PI, (1.1 * 180) / Math.PI);
    canvas.drawPath(path, paint);
  }
}

/** Text from glyph widths (no rounding per glyph); align 0 left, 0.5 centre. */
export function drawText(canvas: SkCanvas, font: SkFont, text: string, x: number, y: number, color: string, align = 0) {
  'worklet';
  const ids = font.getGlyphIDs(text);
  const widths = font.getGlyphWidths(ids);
  let w = 0;
  for (let i = 0; i < widths.length; i++) w += widths[i];
  const positions = [];
  let cx = -w * align;
  for (let i = 0; i < ids.length; i++) {
    positions.push({ x: cx, y: 0 });
    cx += widths[i];
  }
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(Skia.Color(color));
  canvas.drawGlyphs(ids, positions, x, y, font, paint);
}

/** Keypad: the press flashes, the printed labels (✕ drawn as two strokes), the LCD, the demo finger. */
export function drawPanel(canvas: SkCanvas, L: Layout, fonts: Fonts, frame: Frame) {
  'worklet';
  const flashes: [number, number][] = [
    [frame.flashA, frame.flashAU],
    [frame.flashB, frame.flashBU],
  ];
  for (let i = 0; i < flashes.length; i++) {
    const [key, u] = flashes[i];
    if (key < 0 || u < 0) continue;
    const [kx, ky] = keyPoint(L, key);
    const paint = Skia.Paint();
    paint.setAntiAlias(true);
    paint.setColor(Skia.Color(`rgba(124,255,138,${0.55 * Math.sin(u * Math.PI)})`));
    canvas.drawCircle(kx, ky, 10, paint);
  }
  const ink = 'rgba(122,74,58,0.85)';
  for (let key = 0; key < KEYS.length; key++) {
    const [kx, ky] = keyPoint(L, key);
    if (KEYS[key] === '✕') {
      const paint = Skia.Paint();
      paint.setAntiAlias(true);
      paint.setStyle(PaintStyle.Stroke);
      paint.setStrokeWidth(1.2);
      paint.setStrokeCap(StrokeCap.Round);
      paint.setColor(Skia.Color(ink));
      const h = 2.8;
      canvas.drawLine(kx - h, ky - h, kx + h, ky + h, paint);
      canvas.drawLine(kx - h, ky + h, kx + h, ky - h, paint);
    } else {
      drawText(canvas, fonts.key, KEYS[key], kx, ky + 3.2, ink, 0.5);
    }
  }
  if (frame.lcd.length > 0) {
    drawText(canvas, fonts.lcd, frame.lcd, mx(L, 837) + frame.lcdShake, my(L, 346) + 5, '#7CFF8A', 0.5);
  }
  if (frame.finger > 0) {
    const [kx, ky] = keyPoint(L, frame.fingerKey);
    const paint = Skia.Paint();
    paint.setAntiAlias(true);
    paint.setColor(Skia.Color(`rgba(40,20,10,${0.22 * frame.finger})`));
    canvas.drawCircle(kx, ky, 20, paint);
  }
}

/** Title, the card with the chosen drink and the price pill. */
export function drawCard(canvas: SkCanvas, L: Layout, fonts: Fonts, frame: Frame) {
  'worklet';
  drawText(canvas, fonts.title, 'Snack Stop', L.ox + 28, 82 * L.fy, '#C4142B');
  drawText(canvas, fonts.sub, 'Lobby · Level 2', L.ox + 29, 102 * L.fy, '#8A5A48');

  const top = 716 * L.fy;
  const shadow = Skia.Paint();
  shadow.setAntiAlias(true);
  shadow.setColor(Skia.Color('rgba(160,40,20,0.18)'));
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 30 / 6, true));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(L.ox + 20, top + 10 / 3, 353, 108), 28, 28), shadow);
  const card = Skia.Paint();
  card.setAntiAlias(true);
  card.setColor(Skia.Color('#FFFFFF'));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(L.ox + 20, top, 353, 108), 28, 28), card);

  const chosen = frame.card === 1 && frame.cardSlot >= 0;
  const drink = chosen ? DRINKS[canIndex(frame.cardSlot)] : null;
  drawText(canvas, fonts.name, drink ? drink[0] : 'Pick a snack', L.ox + 42, top + 40, '#2B1A14');
  drawText(
    canvas,
    fonts.line,
    drink ? `${drink[1]} · ${slotCode(frame.cardSlot)}` : 'Tap a code on the keypad',
    L.ox + 42,
    top + 66,
    '#9A7A6A',
  );

  const pillTop = 736 * L.fy;
  const pill = Skia.Paint();
  pill.setAntiAlias(true);
  pill.setColor(Skia.Color(frame.pill === 2 ? '#2E9E55' : frame.pill === 1 ? '#C4142B' : '#E8D8CF'));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(L.ox + 245, pillTop, 110, 66), 33, 33), pill);
  const label = frame.pill === 2 ? 'Enjoy!' : frame.pill === 1 && drink ? drink[2] : '—';
  drawText(canvas, fonts.pill, label, L.ox + 300, pillTop + 41, frame.pill === 0 ? '#9A7A6A' : '#FFFFFF', 0.5);
}

/** Hit tests, in screen points. */
export function keyAt(L: Layout, x: number, y: number) {
  'worklet';
  for (let key = 0; key < KEYS.length; key++) {
    const [kx, ky] = keyPoint(L, key);
    if (Math.hypot(x - kx, y - ky) <= 13) return key;
  }
  return -1;
}

export function slotAt(L: Layout, x: number, y: number) {
  'worklet';
  for (let slot = 0; slot < ROWS * COLS; slot++) {
    const [sx, sy] = slotPoint(L, slot);
    if (Math.abs(x - sx) <= CAN_W / 2 + 4 && y <= sy + 4 && y >= sy - CAN_H - 4) return slot;
  }
  return -1;
}

export function trayHit(L: Layout, x: number, y: number) {
  'worklet';
  const [x0, y0, x1, y1] = trayRect(L);
  return x >= x0 && x <= x1 && y >= y0 && y <= y1;
}
