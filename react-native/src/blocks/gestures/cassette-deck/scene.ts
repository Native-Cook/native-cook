/**
 * The deck in one Skia picture. What never moves is drawn once at mount into two images: behind the tape (the brushed
 * faceplate and its engraving, the well, the cassette's shell and handwritten label, the counter's and meters' frames)
 * and in front of it (the window's clear plastic and the smoked door's reflections). A frame draws the reels, the
 * counter's wheels, the meters, the keys and the line under them.
 */
import {
  BlurStyle, ClipOp, PaintStyle, Skia, StrokeCap, TileMode, type SkCanvas, type SkFont, type SkImage, type SkPaint,
  type SkTypeface,
} from '@shopify/react-native-skia';

import { KEY_W, MODE, counterAt, keyX, packLeft, packRight, reelAngles, type Model } from './model';
import { METER, TAPE } from './motion';
import { COLOR, COPY, KEYS, LAYOUT, STAGE, TYPE, type Layout, type TypeRole } from './theme';

export type Faces = { regular: SkTypeface; medium: SkTypeface; semibold: SkTypeface; hand: SkTypeface };
export type Fonts = Record<TypeRole, SkFont>;
export type SceneCtx = { lay: Layout; fonts: Fonts; back: SkImage; front: SkImage; px: number };

// ——— Painting helpers ———

function paintOf(color: string, alpha = 1): SkPaint {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  if (alpha < 1) p.setAlphaf(p.getAlphaf() * Math.max(alpha, 0));
  return p;
}

function strokeOf(color: string, w: number, alpha = 1): SkPaint {
  'worklet';
  const p = paintOf(color, alpha);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(w);
  p.setStrokeCap(StrokeCap.Round);
  return p;
}

function linear(x0: number, y0: number, x1: number, y1: number, colors: string[], pos: number[] | null): SkPaint {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(Skia.Shader.MakeLinearGradient({ x: x0, y: y0 }, { x: x1, y: y1 }, colors.map((c) => Skia.Color(c)), pos, TileMode.Clamp));
  return p;
}

function radial(x: number, y: number, r: number, colors: string[], pos: number[] | null): SkPaint {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(Skia.Shader.MakeRadialGradient({ x, y }, r, colors.map((c) => Skia.Color(c)), pos, TileMode.Clamp));
  return p;
}

function blurred(p: SkPaint, sigma: number): SkPaint {
  'worklet';
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma, true));
  return p;
}

function rrect(x: number, y: number, w: number, h: number, r: number) {
  'worklet';
  return Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r);
}

function advance(font: SkFont, s: string): number {
  'worklet';
  if (!s) return 0;
  const ws = font.getGlyphWidths(font.getGlyphIDs(s));
  let w = 0;
  for (let i = 0; i < ws.length; i++) w += ws[i];
  return w;
}

function text(c: SkCanvas, s: string, x: number, y: number, font: SkFont, color: string, alpha = 1, align = 0) {
  'worklet';
  if (alpha <= 0.004 || !s) return;
  const w = advance(font, s);
  const left = align === 0 ? x : align === 1 ? x - w / 2 : x - w;
  c.drawText(s, left, y, paintOf(color, alpha), font);
}

/** Engraved into the aluminium: dark letters with a hairline of light under them. */
function engraved(c: SkCanvas, s: string, x: number, y: number, font: SkFont, track: number, align = 0, color: string = COLOR.engrave) {
  let w = 0;
  for (let i = 0; i < s.length; i++) w += advance(font, s[i]) + (i < s.length - 1 ? track : 0);
  let cx = align === 0 ? x : align === 1 ? x - w / 2 : x - w;
  for (let i = 0; i < s.length; i++) {
    text(c, s[i], cx, y + 0.7, font, '#FFFFFF', 0.7);
    text(c, s[i], cx, y, font, color);
    cx += advance(font, s[i]) + track;
  }
}

// ——— Images made once ———

function surface(w: number, h: number) {
  return Skia.Surface.MakeOffscreen(w, h) ?? Skia.Surface.Make(w, h);
}

function snapshot(s: NonNullable<ReturnType<typeof surface>>): SkImage {
  s.flush();
  const img = s.makeImageSnapshot();
  return img.makeNonTextureImage() ?? img;
}

function hash(n: number): number {
  const s = Math.sin(n * 127.1 + 311.7) * 43758.5453;
  return s - Math.floor(s);
}

/** Brushed aluminium: a soft vertical falloff from the light above, and thousands of fine horizontal strokes. */
function brushed(c: SkCanvas, x: number, y: number, w: number, h: number, seed: number) {
  c.drawRect(Skia.XYWHRect(x, y, w, h), linear(0, y, 0, y + h, [COLOR.plateLight, COLOR.plate, COLOR.plateDeep], [0, 0.45, 1]));
  for (let k = 0; k < h * 3.2; k++) {
    const yy = y + hash(k + seed) * h;
    const x0 = x + hash(k * 3 + seed) * w * 0.6 - 20;
    const len = 60 + hash(k * 7 + seed) * w;
    const light = hash(k * 11 + seed) > 0.5;
    const p = paintOf(light ? '#FFFFFF' : '#5A5F66', 0.05 + hash(k * 13 + seed) * (light ? 0.12 : 0.06));
    c.drawRect(Skia.XYWHRect(x0, yy, len, 0.35), p);
  }
}

function screw(c: SkCanvas, x: number, y: number, r: number, a: number) {
  c.drawCircle(x, y, r, radial(x - r * 0.3, y - r * 0.3, r * 1.6, ['#8C9096', '#54585E', '#2E3135'], [0, 0.6, 1]));
  const sl = strokeOf('#1B1D20', 1.1);
  c.drawLine(x - Math.cos(a) * r * 0.7, y - Math.sin(a) * r * 0.7, x + Math.cos(a) * r * 0.7, y + Math.sin(a) * r * 0.7, sl);
  c.drawLine(x - Math.sin(a) * r * 0.7, y + Math.cos(a) * r * 0.7, x + Math.sin(a) * r * 0.7, y - Math.cos(a) * r * 0.7, sl);
}

function drawPlate(c: SkCanvas, fonts: Fonts) {
  brushed(c, -200, -200, STAGE.w + 400, STAGE.h + 600, 1);
  engraved(c, COPY.brand, LAYOUT.edge, LAYOUT.brandBase, fonts.brand, 5);
  engraved(c, COPY.model, LAYOUT.edge, LAYOUT.modelBase, fonts.model, 1.6, 0, COLOR.engraveMute);
  // The power lamp: amber behind a lens, on.
  const { x, y } = LAYOUT.power;
  c.drawCircle(x, y, 9, radial(x, y, 9, ['rgba(255,138,42,0.35)', 'rgba(255,138,42,0)'], null));
  c.drawCircle(x, y, 4, radial(x - 1, y - 1, 5, ['#FFE2B8', COLOR.led, '#B4520E'], [0, 0.5, 1]));
  c.drawCircle(x, y, 5, strokeOf('#5A5F66', 1, 0.6));
  engraved(c, 'POWER', x, y + 22, fonts.small, 1.4, 1, COLOR.engraveMute);
}

function drawWell(c: SkCanvas) {
  const w = LAYOUT.well;
  // A bevel cut into the plate: light on its lower lip, dark on its upper.
  c.drawRRect(rrect(w.x - 2, w.y - 2, w.w + 4, w.h + 4, w.r + 2), linear(0, w.y, 0, w.y + w.h, ['#7D8288', '#F4F5F6'], null));
  c.drawRRect(rrect(w.x, w.y, w.w, w.h, w.r), paintOf(COLOR.well));
  c.save();
  c.clipRRect(rrect(w.x, w.y, w.w, w.h, w.r), ClipOp.Intersect, true);
  c.drawRect(Skia.XYWHRect(w.x, w.y, w.w, 30), blurred(paintOf('#000000', 0.7), 10));
  c.restore();
}

function drawCassette(c: SkCanvas, fonts: Fonts) {
  const k = LAYOUT.cassette;
  // The shell: charcoal plastic, a soft highlight along its top edge, a shadow under it in the well.
  c.drawRRect(rrect(k.x + 2, k.y + 6, k.w, k.h, k.r), blurred(paintOf('#000000', 0.6), 6));
  c.drawRRect(rrect(k.x, k.y, k.w, k.h, k.r), linear(0, k.y, 0, k.y + k.h, [COLOR.shellLight, COLOR.shell, '#1E1F22'], [0, 0.4, 1]));
  c.drawRRect(rrect(k.x + 0.5, k.y + 0.5, k.w - 1, k.h - 1, k.r), strokeOf('#FFFFFF', 1, 0.12));
  // The head opening at the bottom: a raised trapezoid with five holes, the tape running across behind them.
  const by = k.y + k.h - 42;
  const trap = Skia.PathBuilder.Make()
    .moveTo(k.x + 62, by)
    .lineTo(k.x + k.w - 62, by)
    .lineTo(k.x + k.w - 40, k.y + k.h)
    .lineTo(k.x + 40, k.y + k.h)
    .close()
    .build();
  c.drawPath(trap, linear(0, by, 0, k.y + k.h, ['#36373B', '#232427'], null));
  c.drawLine(k.x + 62, by + 0.5, k.x + k.w - 62, by + 0.5, strokeOf('#FFFFFF', 1, 0.14));
  const holes = [[k.x + 86, 22], [k.x + 128, 18], [k.x + k.w / 2 - 15, 30], [k.x + k.w - 146, 18], [k.x + k.w - 108, 22]];
  for (const [hx, hw] of holes) {
    c.drawRRect(rrect(hx, by + 14, hw, 20, 3), paintOf('#0B0B0C'));
    c.drawRect(Skia.XYWHRect(hx, by + 26, hw, 3), paintOf(COLOR.tape));
    c.drawRect(Skia.XYWHRect(hx, by + 26, hw, 0.8), paintOf(COLOR.tapeLight, 0.8));
  }
  for (const [sx, sy] of [[k.x + 12, k.y + 12], [k.x + k.w - 12, k.y + 12], [k.x + 12, k.y + k.h - 12], [k.x + k.w - 12, k.y + k.h - 12], [k.x + k.w / 2, k.y + k.h - 10]]) {
    screw(c, sx, sy, 3.4, hash(sx + sy) * 2);
  }

  // The label: cream paper, a red band through the middle (the window cuts it), ruled lines, written by hand.
  const l = LAYOUT.label;
  c.drawRRect(rrect(l.x, l.y, l.w, l.h, l.r), linear(l.x, l.y, l.x + l.w, l.y + l.h, ['#F6EEDC', COLOR.label, '#E9DEC5'], [0, 0.5, 1]));
  const lines = paintOf(COLOR.labelLine);
  for (let i = 0; i < 3; i++) c.drawRect(Skia.XYWHRect(l.x + 44, l.y + 18 + i * 12, l.w - 58, 0.6), lines);
  c.drawRect(Skia.XYWHRect(l.x, l.y + l.h - 22, l.w, 22), paintOf(COLOR.labelBand));
  c.drawRect(Skia.XYWHRect(l.x, l.y + l.h - 22, l.w, 1), paintOf('#FFFFFF', 0.35));
  // Side A: a letter in a circle at the left, then the title and a note in pen.
  c.drawCircle(l.x + 22, l.y + 30, 13, strokeOf(COLOR.pen, 1.4, 0.85));
  text(c, COPY.side, l.x + 22, l.y + 39, fonts.hand, COLOR.pen, 0.9, 1);
  c.save();
  c.rotate(-1.6, l.x + 50, l.y + 40);
  text(c, COPY.title, l.x + 46, l.y + 34, fonts.hand, COLOR.pen, 0.92);
  text(c, COPY.sub, l.x + 52, l.y + 54, fonts.handSmall, COLOR.pen, 0.8);
  c.restore();
  // Where the window goes, the label is cut away; behind it, the dark inside of the cassette.
  const w = LAYOUT.window;
  c.drawRRect(rrect(w.x - 3, w.y - 3, w.w + 6, w.h + 6, w.r + 3), paintOf('#1A1B1D'));
  c.drawRRect(rrect(w.x, w.y, w.w, w.h, w.r), paintOf('#0D0D0E'));
}

function drawReadout(c: SkCanvas, fonts: Fonts) {
  const r = LAYOUT.readout;
  const cn = LAYOUT.counter;
  engraved(c, COPY.counter, cn.x, r.y - 10, fonts.small, 1.4, 0, COLOR.engraveMute);
  c.drawRRect(rrect(cn.x - 1.5, r.y - 1.5, 100 + 3, r.h + 3, 9), linear(0, r.y, 0, r.y + r.h, ['#80858B', '#F2F3F4'], null));
  c.drawRRect(rrect(cn.x, r.y, 100, r.h, 8), paintOf(COLOR.counter));
  // The memory lamp beside the counter.
  engraved(c, COPY.memo, cn.x + 116, r.y + 52, fonts.small, 1.4, 0, COLOR.engraveMute);
  const m = LAYOUT.meters;
  const marks = ['-20', '-10', '-5', '0', '+3'];
  for (let i = 0; i < marks.length; i++) {
    const seg = [0, 4, 7, 9, 11][i];
    text(c, marks[i], m.x + 22 + seg * ((m.w - 22) / METER.segments) + 6, r.y - 10, fonts.small, COLOR.engraveMute, 1, 1);
  }
  c.drawRRect(rrect(m.x - 1.5, r.y - 1.5, m.w + 3, r.h + 3, 9), linear(0, r.y, 0, r.y + r.h, ['#80858B', '#F2F3F4'], null));
  c.drawRRect(rrect(m.x, r.y, m.w, r.h, 8), paintOf(COLOR.counter));
  text(c, COPY.left, m.x + 10, r.y + 26, fonts.small, '#9AA0A6');
  text(c, COPY.right, m.x + 10, r.y + 50, fonts.small, '#9AA0A6');
}

function drawKeySlots(c: SkCanvas, fonts: Fonts) {
  const k = LAYOUT.keys;
  // The slot the keys come out of: a dark gap with light on its lower lip.
  c.drawRRect(rrect(LAYOUT.edge - 4, k.y - 4, STAGE.w - 2 * LAYOUT.edge + 8, k.h + 8, 12), paintOf('#16171A'));
  c.drawRRect(rrect(LAYOUT.edge - 6, k.y - 6, STAGE.w - 2 * LAYOUT.edge + 12, k.h + 12, 14), strokeOf('#FFFFFF', 1, 0.5));
  const names = ['REW', 'PLAY', 'FF', 'STOP', 'PAUSE'];
  for (let i = 0; i < 5; i++) engraved(c, names[i], keyX(i) + KEY_W / 2, k.y - 14, fonts.small, 1.4, 1, COLOR.engraveMute);
}

function drawGlass(c: SkCanvas) {
  // The window's clear plastic: a frame with a scale on it, and a hairline of light along its top.
  const w = LAYOUT.window;
  c.drawRRect(rrect(w.x + 0.5, w.y + 0.5, w.w - 1, w.h - 1, w.r), strokeOf('#FFFFFF', 1, 0.28));
  for (let i = 0; i <= 10; i++) {
    const x = w.x + 40 + i * ((w.w - 80) / 10);
    c.drawLine(x, w.y + 3, x, w.y + (i % 5 === 0 ? 9 : 6), strokeOf('#FFFFFF', 0.7, 0.35));
  }
  // The smoked acrylic door over the well: a tint and two soft diagonal reflections of the room's light.
  const d = LAYOUT.well;
  c.save();
  c.clipRRect(rrect(d.x, d.y, d.w, d.h, d.r), ClipOp.Intersect, true);
  c.drawRect(Skia.XYWHRect(d.x, d.y, d.w, d.h), paintOf('#2A2018', 0.16));
  const streak = (x0: number, wdt: number, a: number) => {
    const p = Skia.PathBuilder.Make()
      .moveTo(x0, d.y - 10)
      .lineTo(x0 + wdt, d.y - 10)
      .lineTo(x0 + wdt - 150, d.y + d.h + 10)
      .lineTo(x0 - 150, d.y + d.h + 10)
      .close()
      .build();
    c.drawPath(p, blurred(paintOf('#FFFFFF', a), 6));
  };
  streak(d.x + 150, 70, 0.07);
  streak(d.x + 250, 16, 0.08);
  c.drawRect(Skia.XYWHRect(d.x + 10, d.y + 1, d.w - 20, 1), paintOf('#FFFFFF', 0.35));
  c.restore();
  c.drawRRect(rrect(d.x + 0.5, d.y + 0.5, d.w - 1, d.h - 1, d.r), strokeOf('#FFFFFF', 1, 0.16));
}

export function makeScene(lay: Layout, faces: Faces, px: number): SceneCtx | null {
  const fonts = {} as Fonts;
  for (const role of Object.keys(TYPE) as TypeRole[]) fonts[role] = Skia.Font(faces[TYPE[role].font as keyof Faces], TYPE[role].size);
  const W = Math.ceil(STAGE.w * px);
  const H = Math.ceil(STAGE.h * px);
  const sb = surface(W, H);
  const sf = surface(W, H);
  if (!sb || !sf) return null;
  const cb = sb.getCanvas();
  cb.scale(px, px);
  drawPlate(cb, fonts);
  drawWell(cb);
  drawCassette(cb, fonts);
  drawReadout(cb, fonts);
  drawKeySlots(cb, fonts);
  const cf = sf.getCanvas();
  cf.scale(px, px);
  drawGlass(cf);
  return { lay, fonts, back: snapshot(sb), front: snapshot(sf), px };
}

// ——— The frame ———

function image(c: SkCanvas, img: SkImage, px: number) {
  'worklet';
  c.save();
  c.scale(1 / px, 1 / px);
  c.drawImage(img, 0, 0);
  c.restore();
}

/** A reel: its tape pack (dark brown, a sheen ring where the light hits the wound edge) and its white hub turning. */
function drawReel(c: SkCanvas, x: number, y: number, pack: number, a: number) {
  'worklet';
  c.drawCircle(x, y, pack, radial(x, y, pack, [COLOR.tape, COLOR.tape, '#2A170E'], [0, 0.85, 1]));
  // The wound layers catch the light as faint rings.
  for (let r = LAYOUT.hub + 4; r < pack - 1; r += 3.2) c.drawCircle(x, y, r, strokeOf(COLOR.tapeLight, 0.5, 0.18));
  c.drawCircle(x, y, pack - 0.6, strokeOf(COLOR.tapeLight, 1, 0.5));
  const H = LAYOUT.hub;
  c.drawCircle(x, y, H, radial(x - 3, y - 4, H * 1.6, ['#FFFFFF', COLOR.hub, '#BDB8AE'], [0, 0.5, 1]));
  c.drawCircle(x, y, H * 0.55, paintOf('#0D0D0E'));
  // The drive spline: six teeth turning with the reel.
  for (let i = 0; i < 6; i++) {
    const t = a + (i * Math.PI) / 3;
    const p = Skia.PathBuilder.Make()
      .moveTo(x + Math.cos(t - 0.16) * H * 0.55, y + Math.sin(t - 0.16) * H * 0.55)
      .lineTo(x + Math.cos(t) * H * 0.3, y + Math.sin(t) * H * 0.3)
      .lineTo(x + Math.cos(t + 0.16) * H * 0.55, y + Math.sin(t + 0.16) * H * 0.55)
      .close()
      .build();
    c.drawPath(p, paintOf(COLOR.hub));
  }
}

function drawTape(c: SkCanvas, m: Model) {
  'worklet';
  const w = LAYOUT.window;
  const [al, ar] = reelAngles(m.u);
  c.save();
  c.clipRRect(rrect(w.x, w.y, w.w, w.h, w.r), ClipOp.Intersect, true);
  const [l, r] = LAYOUT.reels;
  drawReel(c, l.x, l.y, packLeft(m.u), al);
  drawReel(c, r.x, r.y, packRight(m.u), ar);
  c.restore();
}

/** Three wheels of a mechanical counter: each digit rolls on as the one below it passes 9. */
function drawCounter(c: SkCanvas, ctx: SceneCtx, m: Model) {
  'worklet';
  const { fonts } = ctx;
  const r = LAYOUT.readout;
  const x0 = LAYOUT.counter.x + 8;
  const cw = 28;
  let v = counterAt(m.u);
  v = ((v % 1000) + 1000) % 1000;
  c.save();
  c.clipRRect(rrect(LAYOUT.counter.x, r.y, 100, r.h, 8), ClipOp.Intersect, true);
  for (let k = 0; k < 3; k++) {
    const place = Math.pow(10, 2 - k);
    const raw = v / place;
    const digit = Math.floor(raw) % 10;
    // Only the moment the wheel below it passes 9 to 0 moves a wheel; the last wheel rolls all the time.
    const below = (v % place) / place;
    const roll = k === 2 ? raw - Math.floor(raw) : below > 0.9 ? (below - 0.9) * 10 : 0;
    const cx = x0 + k * cw + cw / 2 - 2;
    const cy = r.y + r.h / 2 + 9;
    const step = 34;
    c.drawRRect(rrect(cx - 12, r.y + 8, 24, r.h - 16, 3), paintOf('#1B1C1E'));
    c.save();
    c.clipRect(Skia.XYWHRect(cx - 12, r.y + 8, 24, r.h - 16), ClipOp.Intersect, true);
    text(c, String(digit), cx, cy - roll * step, fonts.digit, COLOR.digit, 1, 1);
    text(c, String((digit + 1) % 10), cx, cy + (1 - roll) * step, fonts.digit, COLOR.digit, 1, 1);
    c.restore();
  }
  // The wheels are cylinders: dark at top and bottom.
  c.drawRect(Skia.XYWHRect(LAYOUT.counter.x, r.y, 100, r.h), linear(0, r.y, 0, r.y + r.h, ['rgba(0,0,0,0.85)', 'rgba(0,0,0,0)', 'rgba(0,0,0,0)', 'rgba(0,0,0,0.85)'], [0, 0.3, 0.7, 1]));
  c.restore();
  const memoOn = m.memo;
  c.drawCircle(LAYOUT.counter.x + 122, r.y + 30, 4, radial(LAYOUT.counter.x + 121, r.y + 29, 5, memoOn ? ['#FFE2B8', COLOR.led, '#B4520E'] : ['#5A4334', COLOR.ledOff, '#21170F'], [0, 0.5, 1]));
  if (memoOn) c.drawCircle(LAYOUT.counter.x + 122, r.y + 30, 10, radial(LAYOUT.counter.x + 122, r.y + 30, 10, ['rgba(255,138,42,0.4)', 'rgba(255,138,42,0)'], null));
}

function drawMeters(c: SkCanvas, m: Model) {
  'worklet';
  const r = LAYOUT.readout;
  const mt = LAYOUT.meters;
  const n = METER.segments;
  const x0 = mt.x + 22;
  const sw = (mt.w - 22 - 10) / n;
  for (let row = 0; row < 2; row++) {
    const lv = row === 0 ? m.lvL : m.lvR;
    const y = r.y + (row === 0 ? 16 : 40);
    for (let i = 0; i < n; i++) {
      const on = lv * n > i + 0.3;
      const col = i >= n - 1 ? COLOR.meterPeak : i >= n - 3 ? COLOR.meterHot : COLOR.meterOn;
      c.drawRRect(rrect(x0 + i * sw, y, sw - 3, 12, 2), paintOf(on ? col : COLOR.meterOff));
      if (on) c.drawRRect(rrect(x0 + i * sw - 2, y - 2, sw + 1, 16, 4), blurred(paintOf(col, 0.35), 3));
    }
  }
}

/** The keys: aluminium caps that go down into the slot; a latched one rests low with its front face nearly gone. */
function drawKeys(c: SkCanvas, m: Model) {
  'worklet';
  const k = LAYOUT.keys;
  for (let i = 0; i < 5; i++) {
    const d = m.d[i];
    const x = keyX(i);
    const sink = 12 * d;
    const front = k.top * (1 - 0.75 * d);
    const top = k.y + sink;
    const faceH = k.h - k.top - 4;
    // Shadow in the slot, shorter as the key sinks.
    c.drawRRect(rrect(x + 2, top + 6, KEY_W - 4, faceH + front, 8), blurred(paintOf('#000000', 0.55 - 0.3 * d), 5));
    // The front face: darker, lit from above.
    c.drawRRect(rrect(x, top + faceH - 8, KEY_W, front + 8, 8), linear(0, top + faceH, 0, top + faceH + front, [COLOR.keyFront, COLOR.keyDeep], null));
    // The top face: brushed aluminium, a little darker as it goes down out of the light.
    c.drawRRect(rrect(x, top, KEY_W, faceH, 8), linear(0, top, 0, top + faceH, [COLOR.keyTop, '#CDD1D5'], null));
    c.drawRRect(rrect(x, top, KEY_W, faceH, 8), paintOf('#000000', 0.12 * d));
    for (let s = 0; s < 14; s++) c.drawRect(Skia.XYWHRect(x + 6, top + 6 + s * ((faceH - 12) / 14), KEY_W - 12, 0.4), paintOf('#FFFFFF', 0.25));
    c.drawRRect(rrect(x + 0.5, top + 0.5, KEY_W - 1, faceH - 1, 8), strokeOf('#FFFFFF', 1, 0.8));
    // The symbol, engraved.
    const cx = x + KEY_W / 2;
    const cy = top + faceH / 2;
    const ink = i === 1 ? COLOR.playMark : COLOR.engrave;
    const tri = (tx: number, dir: number) =>
      Skia.PathBuilder.Make().moveTo(tx, cy - 7).lineTo(tx + 10 * dir, cy).lineTo(tx, cy + 7).close().build();
    const sym = paintOf(ink);
    const kind = KEYS[i];
    if (kind === 'play') c.drawPath(tri(cx - 5, 1), sym);
    else if (kind === 'ff') {
      c.drawPath(tri(cx - 10, 1), sym);
      c.drawPath(tri(cx, 1), sym);
    } else if (kind === 'rew') {
      c.drawPath(tri(cx + 10, -1), sym);
      c.drawPath(tri(cx, -1), sym);
    } else if (kind === 'stop') c.drawRect(Skia.XYWHRect(cx - 6.5, cy - 6.5, 13, 13), sym);
    else {
      c.drawRect(Skia.XYWHRect(cx - 7, cy - 7, 4.5, 14), sym);
      c.drawRect(Skia.XYWHRect(cx + 2.5, cy - 7, 4.5, 14), sym);
    }
  }
}

function drawHint(c: SkCanvas, ctx: SceneCtx, m: Model) {
  'worklet';
  const { fonts } = ctx;
  let h = COPY.hint[0];
  if (m.mode === MODE.play) h = COPY.hint[1];
  else if (m.mode === MODE.ff) h = COPY.hint[2];
  else if (m.mode === MODE.rew) h = m.memo ? COPY.hint[3] : 'Rewinding';
  else if (m.mode === MODE.pause) h = COPY.hint[4];
  else if (m.u > TAPE.start + 0.001) h = 'Stopped';
  text(c, h, STAGE.w / 2, LAYOUT.hintBase, fonts.hint, COLOR.engrave, 0.9, 1);
}

export function drawScene(c: SkCanvas, ctx: SceneCtx, m: Model) {
  'worklet';
  const { lay } = ctx;
  c.drawColor(Skia.Color(COLOR.plate));
  c.save();
  c.translate(lay.ox, lay.oy);
  c.scale(lay.s, lay.s);
  image(c, ctx.back, ctx.px);
  drawTape(c, m);
  image(c, ctx.front, ctx.px);
  drawCounter(c, ctx, m);
  drawMeters(c, m);
  drawKeys(c, m);
  drawHint(c, ctx, m);
  if (m.ghostO > 0.01) {
    c.drawCircle(m.ghostX, m.ghostY, LAYOUT.finger / 2, paintOf(COLOR.finger, m.ghostO));
    c.drawCircle(m.ghostX, m.ghostY, LAYOUT.finger / 2 - 0.5, strokeOf(COLOR.fingerEdge, 1, m.ghostO));
  }
  c.restore();
}
