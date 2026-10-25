import {
  BlurStyle,
  ClipOp,
  PaintStyle,
  Skia,
  StrokeCap,
  TileMode,
  vec,
  type SkCanvas,
  type SkFont,
  type SkPicture,
} from '@shopify/react-native-skia';

import { BARS, hash } from './typewriterSignupMotion';

/*
 Typewriter Sign Up — static artwork, recorded once into SkPictures, and the geometry they share.

 Front view of a desk typewriter: a cream sheet stands up out of the carriage, the black rubber
 platen and its chrome knobs move with the carriage, a chrome paper bail rides over the sheet,
 and under the platen the typebar basket shows its fan of nine bars around the chrome segment.
 The deck's enamel front hides their pivots. Everything is drawn in points.
*/

// Sheet, in its own coordinates (origin at its top-left corner).
export const PAPER_W = 300;
export const PAPER_H = 340;
/** Baseline of the first typed line, and the line feed. */
export const TOP_PAD = 76;
export const LINE_H = 34;
/** Where typed text starts on the sheet (after the printed labels). */
export const TEXT_X = 108;
const LABELS = ['NAME', 'EMAIL', 'PASSWORD'];

export const PLATEN_R = 22;
/** How far the platen reaches past each side of the sheet. */
const PLATEN_OVER = 44;

export const COLORS = {
  desk: ['#2b211b', '#1c1613', '#110d0b'],
  paper: ['#f6efe0', '#f0e6d1', '#e7dbc2'],
  ink: '#2b2420',
  inkSoft: 'rgba(43,36,32,0.55)',
  cream: '#f1e8d8',
  creamSoft: 'rgba(241,232,216,0.62)',
  enamel: ['#24302a', '#17201c', '#0d1310'],
  chrome: ['#5c6166', '#e9edf0', '#9aa1a8', '#3d4247'],
  brass: '#cfae6d',
};

/**
 The machine is laid out in its own points and drawn scaled by `scale` about the screen's
 top-left corner: W, H, cx and every y below are machine points (screen = machine × scale).
 `screenW`, `screenH`, `headerTop` and `formTop` are screen points.
*/
export type Geo = {
  W: number;
  H: number;
  cx: number;
  scale: number;
  screenW: number;
  screenH: number;
  /** Where the fields start (screen points). */
  formTop: number;
  /** The typing line's baseline (the strike point's y), the platen's centre, the rail. */
  sy: number;
  platenY: number;
  railY: number;
  /** Centre of the typebar segment, and the top of the deck's front panel. */
  segX: number;
  segY: number;
  deckY: number;
  /** Bottom of the typewriter (the form starts below). */
  machineBottom: number;
  headerTop: number;
  compact: boolean;
};

/** Height of the fields and the button under the machine, by layout (screen points). */
export const FORM_H = { regular: 3 * 56 + 2 * 8 + 16 + 52, compact: 3 * 52 + 2 * 8 + 16 + 48 };
/** The machine below the typing line, and the sheet above it once all three lines are typed (machine points). */
const BELOW_LINE = 8 + PLATEN_R * 2 + 4 + 82 + 58;
const SHEET_RISE = TOP_PAD + 2 * LINE_H;
const MAX_SCALE = 1.25;

export function makeGeo(W: number, H: number, insetTop: number, insetBottom: number): Geo {
  const compact = H < 740;
  let scale = 1;
  let headerTop: number;
  let sy: number;
  if (compact) {
    // Small screens: the machine at its own size right under the header, the fields after it.
    headerTop = insetTop + 18;
    sy = headerTop + 64 + 10 + 96;
  } else {
    // The button sits just above the home indicator; the machine takes all the room between the
    // fields and the header, scaled so even the fully typed sheet stays below the subtitle.
    headerTop = insetTop + 12;
    const sheetLimit = headerTop + 84 + 10;
    const machineBottom = H - insetBottom - 16 - FORM_H.regular - 16;
    scale = Math.max(1, Math.min(MAX_SCALE, (machineBottom - sheetLimit) / (SHEET_RISE + BELOW_LINE)));
    sy = machineBottom / scale - BELOW_LINE;
  }
  const platenY = sy + 8 + PLATEN_R;
  const railY = platenY + PLATEN_R + 4;
  const segY = railY + 82;
  const deckY = segY - 6;
  const machineBottom = segY + 58;
  const mw = W / scale;
  return {
    W: mw, H: H / scale, cx: mw / 2, scale, screenW: W, screenH: H,
    formTop: machineBottom * scale + 16,
    sy, platenY, railY, segX: mw / 2, segY, deckY, machineBottom, headerTop, compact,
  };
}

/** Fan geometry: pivot radius, rest tip radius and each bar's angle (screen radians, up = −π/2). */
export const SEG_R0 = 30;
export const SEG_R1 = 66;
export function barAngle(i: number) {
  'worklet';
  return Math.PI + ((i + 0.5) / BARS) * Math.PI;
}

/** Typebar i at extension e (0 rest … 1 at the platen): [pivot x, pivot y, tip x, tip y]. */
export function barTip(g: Geo, i: number, e: number) {
  'worklet';
  const a0 = barAngle(i);
  const px = g.segX + Math.cos(a0) * SEG_R0;
  const py = g.segY + Math.sin(a0) * SEG_R0;
  const l0 = SEG_R1 - SEG_R0;
  const tx = g.cx - px;
  const ty = g.sy - 5 - py;
  let a1 = Math.atan2(ty, tx);
  while (a1 - a0 > Math.PI) a1 -= 2 * Math.PI;
  while (a1 - a0 < -Math.PI) a1 += 2 * Math.PI;
  const a = a0 + (a1 - a0) * e;
  const len = l0 + (Math.hypot(tx, ty) - l0) * e;
  return [px, py, px + Math.cos(a) * len, py + Math.sin(a) * len];
}

function paint(color: string, alpha = 1) {
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  p.setAlphaf(alpha);
  return p;
}

function linear(x0: number, y0: number, x1: number, y1: number, colors: string[], pos?: number[]) {
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(Skia.Shader.MakeLinearGradient(vec(x0, y0), vec(x1, y1), colors.map((c) => Skia.Color(c)), pos ?? null, TileMode.Clamp));
  return p;
}

function shadow(c: SkCanvas, rect: { x: number; y: number; w: number; h: number; r: number }, blur: number, dy: number, alpha: number) {
  const p = paint('#000000', alpha);
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, blur, true));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(rect.x, rect.y + dy, rect.w, rect.h), rect.r, rect.r), p);
}

function record(w: number, h: number, draw: (c: SkCanvas) => void): SkPicture {
  const rec = Skia.PictureRecorder();
  const c = rec.beginRecording(Skia.XYWHRect(-60, -60, w + 120, h + 120));
  draw(c);
  return rec.finishRecordingAsPicture();
}

/** Desk, lamp glow, vignette, grain and the header copy (the sheet may fly over it). */
export function makeBackground(g: Geo, title: SkFont, small: SkFont, kicker: SkFont): SkPicture {
  return record(g.screenW, g.screenH, (c) => {
    c.drawRect(Skia.XYWHRect(0, 0, g.screenW, g.screenH), linear(0, 0, 0, g.screenH, COLORS.desk, [0, 0.55, 1]));
    const lamp = Skia.Paint();
    lamp.setShader(
      Skia.Shader.MakeRadialGradient(vec(g.screenW / 2, g.sy * g.scale - 40), g.screenW * 0.9, [Skia.Color('rgba(255,214,160,0.13)'), Skia.Color('rgba(255,214,160,0.04)'), Skia.Color('rgba(0,0,0,0)')], [0, 0.5, 1], TileMode.Clamp),
    );
    c.drawRect(Skia.XYWHRect(0, 0, g.screenW, g.screenH), lamp);
    const vig = Skia.Paint();
    vig.setShader(
      Skia.Shader.MakeRadialGradient(vec(g.screenW / 2, g.screenH * 0.45), g.screenH * 0.75, [Skia.Color('rgba(0,0,0,0)'), Skia.Color('rgba(0,0,0,0.18)'), Skia.Color('rgba(0,0,0,0.55)')], [0.45, 0.75, 1], TileMode.Clamp),
    );
    c.drawRect(Skia.XYWHRect(0, 0, g.screenW, g.screenH), vig);
    // Wood grain: long faint strokes, seeded.
    const grain = paint('#f3d9b8', 0.035);
    grain.setStyle(PaintStyle.Stroke);
    grain.setStrokeWidth(1);
    for (let i = 0; i < 46; i += 1) {
      const y = hash(i * 3.1) * g.screenH;
      const p = Skia.PathBuilder.Make()
        .moveTo(-10, y)
        .cubicTo(g.screenW * 0.3, y + (hash(i + 7) - 0.5) * 30, g.screenW * 0.7, y + (hash(i + 11) - 0.5) * 30, g.screenW + 10, y + (hash(i + 13) - 0.5) * 20)
        .build();
      c.drawPath(p, grain);
    }
    const dots = paint('#ffffff', 0.025);
    for (let i = 0; i < 700; i += 1) c.drawCircle(hash(i * 1.7) * g.screenW, hash(i * 2.3 + 1) * g.screenH, 0.6, dots);

    c.drawText('INKWELL', 24, g.headerTop + 12, paint(COLORS.brass), kicker);
    c.drawText('Start your first draft', 24, g.headerTop + 46, paint(COLORS.cream), title);
    if (!g.compact) c.drawText('Three lines and the page is yours.', 24, g.headerTop + 74, paint(COLORS.creamSoft), small);
  });
}

/** The sheet, in sheet coordinates: cream stock, fibres, faint rules, a margin and the printed labels. */
export function makePaper(label: SkFont, header: SkFont): SkPicture {
  return record(PAPER_W, PAPER_H, (c) => {
    const r = Skia.XYWHRect(0, 0, PAPER_W, PAPER_H);
    c.drawRect(r, linear(0, 0, PAPER_W, PAPER_H, COLORS.paper, [0, 0.5, 1]));
    // Edge darkening and a cooler lower half where the platen shades it.
    c.drawRect(r, linear(0, 0, PAPER_W, 0, ['rgba(120,96,60,0.10)', 'rgba(120,96,60,0)', 'rgba(120,96,60,0)', 'rgba(120,96,60,0.12)'], [0, 0.08, 0.92, 1]));
    c.drawRect(r, linear(0, 0, 0, PAPER_H, ['rgba(255,255,255,0.35)', 'rgba(255,255,255,0)', 'rgba(90,70,40,0.10)'], [0, 0.2, 1]));
    const fibre = paint('#8a6f4a', 0.06);
    fibre.setStyle(PaintStyle.Stroke);
    fibre.setStrokeWidth(0.6);
    fibre.setStrokeCap(StrokeCap.Round);
    for (let i = 0; i < 260; i += 1) {
      const x = hash(i * 5.3) * PAPER_W;
      const y = hash(i * 7.9 + 2) * PAPER_H;
      const a = hash(i * 1.3) * Math.PI;
      const l = 2 + hash(i * 3.7) * 6;
      c.drawLine(x, y, x + Math.cos(a) * l, y + Math.sin(a) * l, fibre);
    }
    const speck = paint('#6b5536', 0.08);
    for (let i = 0; i < 500; i += 1) c.drawCircle(hash(i * 2.1 + 9) * PAPER_W, hash(i * 4.4 + 3) * PAPER_H, 0.45, speck);

    // Printed head of the form.
    const printed = paint('#7c6a55', 0.9);
    c.drawText('INKWELL  ·  NEW WRITER', 24, 38, printed, header);
    c.drawText('DRAFT 0001', PAPER_W - 24 - header.getTextWidth('DRAFT 0001'), 38, printed, header);
    c.drawRect(Skia.XYWHRect(24, 48, PAPER_W - 48, 0.8), paint('#7c6a55', 0.5));
    // Margin and rules.
    c.drawRect(Skia.XYWHRect(TEXT_X - 10, 56, 0.8, PAPER_H - 70), paint('#c0504a', 0.28));
    for (let l = 0; l < 7; l += 1) {
      const y = TOP_PAD + l * LINE_H + 6;
      c.drawRect(Skia.XYWHRect(20, y, PAPER_W - 40, 0.7), paint('#5b7a9a', 0.13));
    }
    LABELS.forEach((t, l) => c.drawText(t, 24, TOP_PAD + l * LINE_H - 1, paint('#8d7b66'), label));
  });
}

/**
 * The carriage, in carriage coordinates: x = 0 at the sheet's left edge, y as on the canvas.
 * Black rubber platen with a soft highlight, chrome end knobs, and the paper bail with two
 * small rubber wheels over the sheet.
 */
export function makeCarriage(g: Geo): SkPicture {
  return record(PAPER_W + PLATEN_OVER * 2, g.H, (c) => {
    const x0 = -PLATEN_OVER;
    const w = PAPER_W + PLATEN_OVER * 2;
    const top = g.platenY - PLATEN_R;
    shadow(c, { x: x0, y: top, w, h: PLATEN_R * 2, r: PLATEN_R }, 10, 10, 0.45);
    c.drawRRect(
      Skia.RRectXY(Skia.XYWHRect(x0, top, w, PLATEN_R * 2), 6, 6),
      linear(0, top, 0, top + PLATEN_R * 2, ['#3d3833', '#57514b', '#25211e', '#110f0d', '#070605'], [0, 0.22, 0.5, 0.82, 1]),
    );
    c.drawRect(Skia.XYWHRect(x0 + 6, top + 6, w - 12, 1.2), paint('#ffffff', 0.14));
    // Knobs.
    for (const kx of [x0 - 16, x0 + w + 2]) {
      shadow(c, { x: kx, y: top - 8, w: 14, h: PLATEN_R * 2 + 16, r: 5 }, 4, 3, 0.4);
      c.drawRRect(
        Skia.RRectXY(Skia.XYWHRect(kx, top - 8, 14, PLATEN_R * 2 + 16), 5, 5),
        linear(kx, 0, kx + 14, 0, COLORS.chrome, [0, 0.35, 0.65, 1]),
      );
      for (let i = 0; i < 9; i += 1) c.drawRect(Skia.XYWHRect(kx + 1, top - 4 + i * 5.6, 12, 0.8), paint('#202326', 0.35));
    }
    // Paper bail.
    const by = g.sy - 52;
    c.drawRect(Skia.XYWHRect(x0 + 8, by + 3, w - 16, 3), paint('#000000', 0.18));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x0 + 8, by, w - 16, 3.2), 1.6, 1.6), linear(0, by, 0, by + 3.2, ['#f2f4f6', '#9aa0a6', '#4a4f54']));
    for (const wx of [70, PAPER_W - 70]) {
      shadow(c, { x: wx - 7, y: by - 3, w: 14, h: 10, r: 4 }, 2, 2, 0.35);
      c.drawRRect(Skia.RRectXY(Skia.XYWHRect(wx - 7, by - 3, 14, 10), 4, 4), linear(0, by - 3, 0, by + 7, ['#4a443f', '#1d1a18', '#0b0a09']));
    }
  });
}

/** The machine behind the bars: rail, enamel body with the basket opening, the chrome segment, the type guide. */
export function makeBodyBack(g: Geo): SkPicture {
  return record(g.W, g.H, (c) => {
    const left = 10;
    const w = g.W - 20;
    const top = g.railY + 7;
    shadow(c, { x: left, y: top, w, h: g.machineBottom - top, r: 26 }, 26, 18, 0.5);
    shadow(c, { x: left, y: top, w, h: g.machineBottom - top, r: 26 }, 3, 2, 0.4);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(left, top, w, g.machineBottom - top), 26, 26), linear(0, top, 0, g.machineBottom, COLORS.enamel, [0, 0.5, 1]));
    // Basket opening.
    const open = Skia.Path.Oval(Skia.XYWHRect(g.segX - 120, g.segY - 72, 240, 144));
    c.save();
    c.clipRect(Skia.XYWHRect(0, 0, g.W, g.segY), ClipOp.Intersect, true);
    c.drawPath(open, linear(0, g.segY - 72, 0, g.segY, ['#050404', '#0d0b0a', '#171311']));
    const rim = paint('#9aa1a8', 0.35);
    rim.setStyle(PaintStyle.Stroke);
    rim.setStrokeWidth(1);
    c.drawPath(open, rim);
    c.restore();
    // Chrome segment the bars pivot on.
    const seg = Skia.Paint();
    seg.setAntiAlias(true);
    seg.setStyle(PaintStyle.Stroke);
    seg.setStrokeWidth(6);
    seg.setShader(Skia.Shader.MakeLinearGradient(vec(g.segX - 30, 0), vec(g.segX + 30, 0), COLORS.chrome.map((x) => Skia.Color(x)), [0, 0.4, 0.7, 1], TileMode.Clamp));
    c.drawArc(Skia.XYWHRect(g.segX - SEG_R0, g.segY - SEG_R0, SEG_R0 * 2, SEG_R0 * 2), 180, 180, false, seg);
    // Rail.
    c.drawRect(Skia.XYWHRect(0, g.railY + 2, g.W, 7), paint('#000000', 0.35));
    c.drawRect(Skia.XYWHRect(0, g.railY, g.W, 6), linear(0, g.railY, 0, g.railY + 6, ['#f0f2f4', '#a3a9af', '#50555a']));
    // Type guide: a small chrome plate with a V notch under the typing point.
    const gy = g.sy + 3;
    const guide = Skia.PathBuilder.Make()
      .moveTo(g.cx - 15, gy + 2)
      .lineTo(g.cx - 3, gy + 2)
      .lineTo(g.cx, gy + 7)
      .lineTo(g.cx + 3, gy + 2)
      .lineTo(g.cx + 15, gy + 2)
      .lineTo(g.cx + 12, gy + 14)
      .lineTo(g.cx - 12, gy + 14)
      .close()
      .build();
    c.drawPath(guide, linear(0, gy, 0, gy + 14, ['#f5f7f8', '#a9afb5', '#5a5f64']));
  });
}

/** The deck's front panel over the bar pivots, with its nameplate. */
export function makeDeck(g: Geo, plate: SkFont): SkPicture {
  return record(g.W, g.H, (c) => {
    const left = 10;
    const w = g.W - 20;
    const top = g.deckY;
    const h = g.machineBottom - top;
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(left, top, w, h), 22, 22), linear(0, top, 0, top + h, ['#2c3a33', '#1b2621', '#101713'], [0, 0.4, 1]));
    c.drawRect(Skia.XYWHRect(left + 22, top + 0.5, w - 44, 1), paint('#ffffff', 0.16));
    c.drawRect(Skia.XYWHRect(left + 18, top + 7, w - 36, 1.6), linear(0, 0, g.W, 0, ['rgba(207,174,109,0)', 'rgba(207,174,109,0.7)', 'rgba(207,174,109,0)']));
    const label = 'INKWELL  No. 4';
    const tw = plate.getTextWidth(label);
    const py = top + 24;
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(g.cx - tw / 2 - 12, py - 4, tw + 24, 20), 4, 4), linear(0, py - 4, 0, py + 16, ['#e6cf98', '#b48a45', '#7d5b26']));
    c.drawText(label, g.cx - tw / 2, py + 10, paint('#3a2a14'), plate);
  });
}
