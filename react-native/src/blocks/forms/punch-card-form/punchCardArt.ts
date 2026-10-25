import {
  BlendMode,
  BlurStyle,
  ClipOp,
  matchFont,
  PaintStyle,
  type SkCanvas,
  type SkFont,
  type SkImage,
  type SkPicture,
  Skia,
  TileMode,
} from '@shopify/react-native-skia';
import { Platform } from 'react-native';

import {
  CANVAS_H,
  CARD_H,
  CARD_W,
  CARD_Y,
  cardLeft,
  colX,
  COLS,
  DESIGN_W,
  FIELDS,
  hash,
  HOLE_H,
  HOLE_W,
  holeMask,
  MARGIN_TOP,
  REGISTER_FROM,
  ROW_PITCH,
  rowY,
  ROWS,
  SLOT_Y,
  STATION_X,
} from './punchCardMotion';

/** Noise is coloured; this makes it grey so grain reads as texture, not as confetti. */
const GRAY = [0.33, 0.34, 0.33, 0, 0, 0.33, 0.34, 0.33, 0, 0, 0.33, 0.34, 0.33, 0, 0, 0, 0, 0, 1, 0];

// Everything the Punch Card Form draws. The card, the bed, the reader and the punch head are
// drawn once on the JS thread (an image and three pictures); `drawScene` runs on the UI thread
// every frame and only places them and draws the holes, the printed letters and the chads.

const MONO = Platform.select({ ios: 'Menlo', default: 'monospace' });
/** Room around the card in its image, for its shadows. */
export const CARD_PAD = 18;
const BAKE = 2;

export type Fonts = { digit: SkFont; print: SkFont; small: SkFont; label: SkFont };

export function makeFonts(): Fonts {
  return {
    digit: matchFont({ fontFamily: MONO, fontSize: 6.2, fontWeight: '500' }),
    print: matchFont({ fontFamily: MONO, fontSize: 10, fontWeight: '700' }),
    small: matchFont({ fontFamily: MONO, fontSize: 4.6, fontWeight: '500' }),
    label: matchFont({ fontFamily: MONO, fontSize: 7, fontWeight: '700' }),
  };
}

function color(hex: string) {
  return Skia.Color(hex);
}

function linear(x0: number, y0: number, x1: number, y1: number, colors: string[], pos?: number[]) {
  return Skia.Shader.MakeLinearGradient({ x: x0, y: y0 }, { x: x1, y: y1 }, colors.map(color), pos ?? null, TileMode.Clamp);
}

/** Text centred on x, measured glyph by glyph (whole-point rounding of measureText drifts). */
function centred(c: SkCanvas, text: string, x: number, y: number, font: SkFont, hex: string, alpha = 1) {
  const ids = font.getGlyphIDs(text);
  const widths = font.getGlyphWidths(ids);
  let w = 0;
  for (const v of widths) w += v;
  const p = Skia.Paint();
  p.setColor(color(hex));
  p.setAlphaf(alpha);
  c.drawText(text, x - w / 2, y, p, font);
}

/** The blank card: manila stock with a cut corner, printed digits, field rules and labels, grain. */
export function bakeCard(fonts: Fonts): SkImage {
  const W = CARD_W + CARD_PAD * 2;
  const H = CARD_H + CARD_PAD * 2;
  const surface = Skia.Surface.Make(Math.ceil(W * BAKE), Math.ceil(H * BAKE))!;
  const c = surface.getCanvas();
  c.scale(BAKE, BAKE);
  c.translate(CARD_PAD, CARD_PAD);
  const cut = 16;
  const outline = Skia.PathBuilder.Make()
    .moveTo(cut, 0)
    .lineTo(CARD_W - 4, 0)
    .quadTo(CARD_W, 0, CARD_W, 4)
    .lineTo(CARD_W, CARD_H - 4)
    .quadTo(CARD_W, CARD_H, CARD_W - 4, CARD_H)
    .lineTo(4, CARD_H)
    .quadTo(0, CARD_H, 0, CARD_H - 4)
    .lineTo(0, cut * 0.9)
    .close()
    .build();
  // Two shadows: a soft one the card casts on the bed and a tight contact one.
  const soft = Skia.Paint();
  soft.setColor(color('#000000'));
  soft.setAlphaf(0.4);
  soft.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 9, true));
  c.save();
  c.translate(0, 6);
  c.drawPath(outline, soft);
  c.restore();
  const tight = Skia.Paint();
  tight.setColor(color('#000000'));
  tight.setAlphaf(0.45);
  tight.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 1.5, true));
  c.save();
  c.translate(0, 1);
  c.drawPath(outline, tight);
  c.restore();

  c.save();
  c.clipPath(outline, ClipOp.Intersect, true);
  const stock = Skia.Paint();
  stock.setShader(linear(0, 0, CARD_W * 0.3, CARD_H, ['#EADCB6', '#E2D0A4', '#D5BF8F'], [0, 0.55, 1]));
  c.drawRect(Skia.XYWHRect(0, 0, CARD_W, CARD_H), stock);
  // Paper grain: fine noise plus long faint fibres.
  const grain = Skia.Paint();
  grain.setShader(Skia.Shader.MakeFractalNoise(0.9, 0.9, 2, 5, 0, 0));
  grain.setColorFilter(Skia.ColorFilter.MakeMatrix(GRAY));
  grain.setBlendMode(BlendMode.Multiply);
  grain.setAlphaf(0.1);
  c.drawRect(Skia.XYWHRect(0, 0, CARD_W, CARD_H), grain);
  const fibre = Skia.Paint();
  fibre.setShader(Skia.Shader.MakeFractalNoise(0.02, 0.6, 2, 9, 0, 0));
  fibre.setColorFilter(Skia.ColorFilter.MakeMatrix(GRAY));
  fibre.setBlendMode(BlendMode.SoftLight);
  fibre.setAlphaf(0.35);
  c.drawRect(Skia.XYWHRect(0, 0, CARD_W, CARD_H), fibre);
  // A printed band along the bottom, and field rules down the card.
  const band = Skia.Paint();
  band.setColor(color('#B4553F'));
  band.setAlphaf(0.14);
  c.drawRect(Skia.XYWHRect(0, CARD_H - 10, CARD_W, 10), band);
  const rule = Skia.Paint();
  rule.setColor(color('#9A4433'));
  rule.setAlphaf(0.55);
  rule.setStrokeWidth(0.7);
  for (const f of FIELDS) {
    for (const col of [f.first, f.first + f.size]) {
      const x = colX(col) - 4.2;
      c.drawLine(x, MARGIN_TOP - 4, x, CARD_H - 10, rule);
    }
    centred(c, f.label, (colX(f.first) + colX(f.first + f.size - 1)) / 2, CARD_H - 2.6, fonts.label, '#8A3B2C', 0.85);
  }
  centred(c, 'ANALYTICAL SOCIETY · MEMBER CARD · FORM 29-A', colX(57), CARD_H - 2.6, fonts.label, '#8A3B2C', 0.7);
  // The digits printed in rows 0–9 of every column, and the column numbers under rows 0 and 9.
  for (let col = 1; col <= COLS; col += 1) {
    const x = colX(col);
    for (let d = 0; d <= 9; d += 1) centred(c, String(d), x, rowY(d + 2) + 2.2, fonts.digit, '#5C3A2A', 0.62);
    centred(c, String(col), x, rowY(2) + 9.6, fonts.small, '#5C3A2A', 0.5);
    centred(c, String(col), x, rowY(11) + 9.6, fonts.small, '#5C3A2A', 0.5);
  }
  // The top edge's lit lip and a darker bottom edge.
  const lip = Skia.Paint();
  lip.setColor(color('#FFFFFF'));
  lip.setAlphaf(0.5);
  c.drawLine(cut, 0.6, CARD_W, 0.6, lip);
  c.restore();
  const edge = Skia.Paint();
  edge.setStyle(PaintStyle.Stroke);
  edge.setStrokeWidth(0.8);
  edge.setColor(color('#8C7449'));
  edge.setAlphaf(0.6);
  c.drawPath(outline, edge);
  const image = surface.makeImageSnapshot();
  surface.dispose();
  return image;
}

/** The bed the card slides on: dark machine enamel with brushed grain and two steel guides. */
export function recordBed(): SkPicture {
  const rec = Skia.PictureRecorder();
  const c = rec.beginRecording(Skia.XYWHRect(0, 0, DESIGN_W, CANVAS_H));
  const bed = Skia.PathBuilder.Make().addRRect(Skia.RRectXY(Skia.XYWHRect(6, 6, DESIGN_W - 12, SLOT_Y + 4), 18, 18)).build();
  const shadow = Skia.Paint();
  shadow.setColor(color('#000000'));
  shadow.setAlphaf(0.55);
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 10, true));
  c.drawPath(bed, shadow);
  const enamel = Skia.Paint();
  enamel.setShader(linear(0, 6, 0, SLOT_Y, ['#3A423C', '#2C332E', '#232925'], [0, 0.5, 1]));
  c.drawPath(bed, enamel);
  c.save();
  c.clipPath(bed, ClipOp.Intersect, true);
  const brush = Skia.Paint();
  brush.setShader(Skia.Shader.MakeFractalNoise(0.003, 0.5, 3, 12, 0, 0));
  brush.setColorFilter(Skia.ColorFilter.MakeMatrix(GRAY));
  brush.setBlendMode(BlendMode.Overlay);
  brush.setAlphaf(0.4);
  c.drawRect(Skia.XYWHRect(0, 0, DESIGN_W, CANVAS_H), brush);
  // A warm work light falls on the bed around the punch.
  const lamp = Skia.Paint();
  lamp.setShader(Skia.Shader.MakeRadialGradient({ x: STATION_X + 40, y: CARD_Y + CARD_H * 0.45 }, 260, [color('rgba(255,214,150,0.16)'), color('rgba(255,214,150,0)')], null, TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(0, 0, DESIGN_W, CANVAS_H), lamp);
  c.restore();
  const rim = Skia.Paint();
  rim.setStyle(PaintStyle.Stroke);
  rim.setStrokeWidth(1);
  rim.setColor(color('#9FB0A4'));
  rim.setAlphaf(0.18);
  c.drawPath(bed, rim);
  // Steel guides above and below the card's path.
  for (const y of [CARD_Y - 9, CARD_Y + CARD_H + 2]) {
    const g = Skia.Paint();
    g.setShader(linear(0, y, 0, y + 7, ['#C9D0CC', '#7E8884', '#4A524E']));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(14, y, DESIGN_W - 28, 7), 3.5, 3.5), g);
  }
  return rec.finishRecordingAsPicture();
}

/** The reader across the bottom: a slot, an enamel front with a vent, a plate and four lamp bezels. */
export function recordReader(fonts: Fonts): SkPicture {
  const rec = Skia.PictureRecorder();
  const top = SLOT_Y;
  const h = CANVAS_H - top - 4;
  const c = rec.beginRecording(Skia.XYWHRect(0, 0, DESIGN_W, CANVAS_H));
  const body = Skia.RRectXY(Skia.XYWHRect(4, top, DESIGN_W - 8, h), 16, 16);
  const shadow = Skia.Paint();
  shadow.setColor(color('#000000'));
  shadow.setAlphaf(0.5);
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 8, true));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(4, top - 4, DESIGN_W - 8, h), 16, 16), shadow);
  const front = Skia.Paint();
  front.setShader(linear(0, top, 0, top + h, ['#58625B', '#434C46', '#2F3632'], [0, 0.35, 1]));
  c.drawRRect(body, front);
  c.save();
  c.clipRRect(body, ClipOp.Intersect, true);
  const brush = Skia.Paint();
  brush.setShader(Skia.Shader.MakeFractalNoise(0.003, 0.6, 3, 31, 0, 0));
  brush.setColorFilter(Skia.ColorFilter.MakeMatrix(GRAY));
  brush.setBlendMode(BlendMode.Overlay);
  brush.setAlphaf(0.45);
  c.drawRect(Skia.XYWHRect(0, top, DESIGN_W, h), brush);
  // The slot: a dark mouth with a lit lower lip.
  const slot = Skia.Paint();
  slot.setShader(linear(0, top, 0, top + 12, ['#050605', '#141815', '#2A302C']));
  c.drawRect(Skia.XYWHRect(4, top, DESIGN_W - 8, 10), slot);
  const lip = Skia.Paint();
  lip.setColor(color('#D7E0DA'));
  lip.setAlphaf(0.35);
  c.drawRect(Skia.XYWHRect(4, top + 10, DESIGN_W - 8, 1), lip);
  // Vent slots on the right.
  const vent = Skia.Paint();
  vent.setColor(color('#141815'));
  vent.setAlphaf(0.85);
  for (let i = 0; i < 6; i += 1) c.drawRRect(Skia.RRectXY(Skia.XYWHRect(DESIGN_W - 120 + i * 16, top + 34, 8, 40), 4, 4), vent);
  c.restore();
  // A brass maker's plate.
  const plate = Skia.Paint();
  plate.setShader(linear(24, top + 30, 170, top + 70, ['#E9CF8A', '#B98E45', '#7E5A24', '#C9A35C'], [0, 0.4, 0.75, 1]));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(24, top + 30, 148, 30), 4, 4), plate);
  centred(c, 'CARD READER · MODEL 2', 98, top + 48, fonts.label, '#3A2810', 0.9);
  // Bezels for the lamps (lit per frame).
  for (let i = 0; i < 4; i += 1) {
    const x = 36 + i * 30;
    const bz = Skia.Paint();
    bz.setShader(Skia.Shader.MakeRadialGradient({ x: x - 1, y: top + 79 }, 9, [color('#D6DDD8'), color('#6D7671'), color('#262B28')], [0, 0.6, 1], TileMode.Clamp));
    c.drawCircle(x, top + 80, 7.5, bz);
    const well = Skia.Paint();
    well.setColor(color('#101311'));
    c.drawCircle(x, top + 80, 5, well);
  }
  return rec.finishRecordingAsPicture();
}

/** The punch head above the station: a steel die block with a red column pointer. */
export function recordHead(): SkPicture {
  const rec = Skia.PictureRecorder();
  const c = rec.beginRecording(Skia.XYWHRect(-40, -40, 80, 80));
  const shadow = Skia.Paint();
  shadow.setColor(color('#000000'));
  shadow.setAlphaf(0.55);
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 4, true));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-17, -22, 34, 24), 5, 5), shadow);
  const block = Skia.Paint();
  block.setShader(linear(-17, 0, 17, 0, ['#5E6763', '#D5DCD8', '#9AA39F', '#4C5450'], [0, 0.35, 0.65, 1]));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-17, -26, 34, 24), 5, 5), block);
  const hi = Skia.Paint();
  hi.setColor(color('#FFFFFF'));
  hi.setAlphaf(0.5);
  c.drawLine(-13, -25.4, 13, -25.4, hi);
  const pointer = Skia.Paint();
  pointer.setColor(color('#D8483A'));
  c.drawPath(Skia.PathBuilder.Make().moveTo(-4, -4).lineTo(4, -4).lineTo(0, 1).close().build(), pointer);
  return rec.finishRecordingAsPicture();
}

/** Everything the frame needs, from the clock or the finger. */
export type Scene = {
  /** Character code per column (index col - 1, 0 for none) and when it was punched, on `now`'s clock. */
  chars: number[];
  times: number[];
  now: number;
  /** Column under the punch (fractional while moving). */
  col: number;
  /** 0 … 1: the typed card going down into the reader; 1 … 0: the next card registering from the right. */
  feed: number;
  register: number;
  /** The reader's lamps running (0 … 1) and the loop time for their pattern. */
  busy: number;
  t: number;
  /** Opacity of the typed card's holes and letters (fades when the drawer returns to rest). */
  ink: number;
};

/** Height of a quick punch stroke: down in 30 ms, back up by 90 ms. */
function strokeAt(dt: number) {
  'worklet';
  if (dt < 0 || dt > 0.09) return 0;
  return dt < 0.03 ? dt / 0.03 : 1 - (dt - 0.03) / 0.06;
}

/** One chad's place and turn `age` seconds after it was punched out of (col, row). */
function chadPose(col: number, row: number, age: number) {
  'worklet';
  const id = col * 16 + row;
  const vx = (hash(id) - 0.5) * 90;
  const vy = -70 - hash(id + 7) * 90;
  const spin = (hash(id + 13) - 0.5) * 26;
  const g = 1500;
  const x = STATION_X + vx * age;
  const y = CARD_Y + rowY(row) + vy * age + 0.5 * g * age * age;
  return [x, y, spin * age, Math.cos((5 + hash(id + 3) * 9) * age)];
}

export function drawScene(c: SkCanvas, s: Scene, card: SkImage, bed: SkPicture, reader: SkPicture, head: SkPicture, font: SkFont) {
  'worklet';
  c.drawPicture(bed);
  const scale = 1 / BAKE;
  const srcW = card.width();
  const srcH = card.height();
  const imgPaint = Skia.Paint();
  // The next card, registering from the right (under the typed one while that one leaves).
  if (s.register < 1) {
    const x = cardLeft(1) + s.register * REGISTER_FROM;
    c.drawImageRect(card, Skia.XYWHRect(0, 0, srcW, srcH), Skia.XYWHRect(x - CARD_PAD, CARD_Y - CARD_PAD, srcW * scale, srcH * scale), imgPaint);
  }
  const showTyped = s.feed < 1 && s.register > 0.02;
  if (showTyped) {
    const x0 = cardLeft(s.col);
    const y0 = CARD_Y + s.feed * (CARD_H + 24);
    c.save();
    c.clipRect(Skia.XYWHRect(0, 0, DESIGN_W, SLOT_Y + 6), ClipOp.Intersect, true);
    c.drawImageRect(card, Skia.XYWHRect(0, 0, srcW, srcH), Skia.XYWHRect(x0 - CARD_PAD, y0 - CARD_PAD, srcW * scale, srcH * scale), imgPaint);
    // Holes: light from the lamp under the bed comes through them.
    const holes = Skia.PathBuilder.Make();
    const glyph = Skia.Paint();
    glyph.setColor(Skia.Color('#2A2620'));
    let any = false;
    for (let i = 0; i < COLS; i += 1) {
      const code = s.chars[i];
      if (code === 0) continue;
      const age = s.now - s.times[i];
      if (age < 0) continue;
      const cx = x0 + colX(i + 1);
      if (cx < -10 || cx > DESIGN_W + 10) continue;
      const mask = holeMask(code);
      if (age > 0.025) {
        for (let r = 0; r < ROWS; r += 1) {
          if ((mask & (1 << r)) === 0) continue;
          const cy = y0 + rowY(r);
          holes.addRRect(Skia.RRectXY(Skia.XYWHRect(cx - HOLE_W / 2, cy - HOLE_H / 2, HOLE_W, HOLE_H), 1.1, 1.1));
          any = true;
        }
      }
      // The character prints along the top edge as the column is punched.
      glyph.setAlphaf(Math.min(1, age / 0.08) * s.ink);
      c.drawText(String.fromCharCode(code), cx - 3, y0 + MARGIN_TOP - 12 + (1 - Math.min(1, age / 0.08)) * -2, glyph, font);
    }
    if (any) {
      const path = holes.build();
      const glow = Skia.Paint();
      glow.setColor(Skia.Color('#FFC864'));
      glow.setAlphaf(0.7 * s.ink);
      glow.setBlendMode(BlendMode.Plus);
      glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 5, false));
      const light = Skia.Paint();
      light.setShader(
        Skia.Shader.MakeLinearGradient({ x: 0, y: y0 }, { x: 0, y: y0 + CARD_H }, [Skia.Color('#FFFBEA'), Skia.Color('#FFEBB8'), Skia.Color('#FFD27E')], [0, 0.5, 1], TileMode.Clamp),
      );
      light.setAlphaf(s.ink);
      c.drawPath(path, light);
      // The torn fibres of the hole's edge, a shade darker than the stock, and its thickness on top.
      const edge = Skia.Paint();
      edge.setStyle(PaintStyle.Stroke);
      edge.setStrokeWidth(1);
      edge.setColor(Skia.Color('#7A6238'));
      edge.setAlphaf(0.9 * s.ink);
      c.drawPath(path, glow);
      c.drawPath(path, edge);
    }
    c.restore();
  }
  // Column pointer and head.
  let strike = 0;
  for (let i = 0; i < COLS; i += 1) if (s.chars[i] !== 0) strike = Math.max(strike, strokeAt(s.now - s.times[i]));
  const pointer = Skia.Paint();
  pointer.setColor(Skia.Color('#E0533F'));
  pointer.setAlphaf(0.35);
  c.drawRect(Skia.XYWHRect(STATION_X - 0.5, CARD_Y + MARGIN_TOP - 6, 1, ROWS * ROW_PITCH + 6), pointer);
  c.save();
  c.translate(STATION_X, CARD_Y - 2 + strike * 3);
  c.drawPicture(head);
  c.restore();
  // Chads, tumbling out of the station and into the reader's bin.
  const chads = Skia.PathBuilder.Make();
  let n = 0;
  for (let i = 0; i < COLS; i += 1) {
    const code = s.chars[i];
    if (code === 0) continue;
    const age = s.now - s.times[i];
    if (age < 0.02 || age > 0.9) continue;
    const mask = holeMask(code);
    for (let r = 0; r < ROWS; r += 1) {
      if ((mask & (1 << r)) === 0) continue;
      const p = chadPose(i + 1, r, age - 0.02);
      if (p[1] > SLOT_Y + 8) continue;
      const ca = Math.cos(p[2]);
      const sa = Math.sin(p[2]);
      const hw = (HOLE_W / 2) * Math.max(0.25, Math.abs(p[3]));
      const hh = HOLE_H / 2;
      chads.moveTo(p[0] + -hw * ca - -hh * sa, p[1] + -hw * sa + -hh * ca);
      chads.lineTo(p[0] + hw * ca - -hh * sa, p[1] + hw * sa + -hh * ca);
      chads.lineTo(p[0] + hw * ca - hh * sa, p[1] + hw * sa + hh * ca);
      chads.lineTo(p[0] + -hw * ca - hh * sa, p[1] + -hw * sa + hh * ca);
      chads.close();
      n += 1;
    }
  }
  if (n > 0) {
    const path = chads.build();
    const shadow = Skia.Paint();
    shadow.setColor(Skia.Color('#000000'));
    shadow.setAlphaf(0.35);
    shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 1.5, false));
    c.save();
    c.translate(1.5, 3);
    c.drawPath(path, shadow);
    c.restore();
    const fill = Skia.Paint();
    fill.setColor(Skia.Color('#E6D4A8'));
    c.drawPath(path, fill);
    const rim = Skia.Paint();
    rim.setStyle(PaintStyle.Stroke);
    rim.setStrokeWidth(0.6);
    rim.setColor(Skia.Color('#A08A5C'));
    c.drawPath(path, rim);
  }
  // The reader shakes a quarter point while it runs (a 30 Hz whirr: 270 cycles per loop).
  c.save();
  c.translate(0, s.busy * 0.35 * Math.sin((2 * Math.PI * 270 * s.t) / 9));
  c.drawPicture(reader);
  c.restore();
  // Lamps: ready (green) at rest; while reading they chase, amber and green.
  for (let i = 0; i < 4; i += 1) {
    const x = 36 + i * 30;
    const y = SLOT_Y + 80;
    const chase = 0.5 + 0.5 * Math.sin((2 * Math.PI * 18 * s.t) / 9 - i * 1.4);
    const idleOn = i === 0 ? 1 : 0.08;
    const on = idleOn * (1 - s.busy) + s.busy * (i % 2 === 0 ? chase : 1 - chase);
    const hue = i === 0 || i === 2 ? '#7CFF9A' : '#FFC25A';
    const lamp = Skia.Paint();
    lamp.setColor(Skia.Color(hue));
    lamp.setAlphaf(0.15 + 0.85 * on);
    c.drawCircle(x, y, 4.2, lamp);
    const halo = Skia.Paint();
    halo.setColor(Skia.Color(hue));
    halo.setAlphaf(0.55 * on);
    halo.setBlendMode(BlendMode.Plus);
    halo.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 6, false));
    c.drawCircle(x, y, 7, halo);
  }
}
