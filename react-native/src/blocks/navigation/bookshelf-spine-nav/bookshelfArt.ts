import { BlendMode, BlurStyle, ClipOp, PaintStyle, type SkCanvas, type SkPicture, Skia, TileMode } from '@shopify/react-native-skia';

import { BOARD_Y, DESIGN_W } from './bookshelfMotion';

/** Noise is coloured; this makes it grey so grain reads as texture, not as confetti. */
const GRAY = [0.33, 0.34, 0.33, 0, 0, 0.33, 0.34, 0.33, 0, 0, 0.33, 0.34, 0.33, 0, 0, 0, 0, 0, 1, 0];

// The room and the shelf, recorded once on the JS thread into one picture behind every view.

const SHELF_TOP = 462;
const BOARD_H = 30;

function color(hex: string) {
  return Skia.Color(hex);
}

function linear(x0: number, y0: number, x1: number, y1: number, colors: string[], pos?: number[]) {
  return Skia.Shader.MakeLinearGradient({ x: x0, y: y0 }, { x: x1, y: y1 }, colors.map(color), pos ?? null, TileMode.Clamp);
}

function grain(c: SkCanvas, x: number, y: number, w: number, h: number, fx: number, fy: number, alpha: number, seed: number) {
  const g = Skia.Paint();
  g.setShader(Skia.Shader.MakeFractalNoise(fx, fy, 3, seed, 0, 0));
  g.setColorFilter(Skia.ColorFilter.MakeMatrix(GRAY));
  g.setBlendMode(BlendMode.Overlay);
  g.setAlphaf(alpha);
  c.drawRect(Skia.XYWHRect(x, y, w, h), g);
}

function blurPaint(hex: string, alpha: number, sigma: number) {
  const p = Skia.Paint();
  p.setColor(color(hex));
  p.setAlphaf(alpha);
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma, true));
  return p;
}

/** The wall, the shelf's back panel, its uprights and the board the books stand on. */
export function recordRoom(w: number, h: number, ox: number, oy: number, k: number): SkPicture {
  const rec = Skia.PictureRecorder();
  const c = rec.beginRecording(Skia.XYWHRect(0, 0, w, h));
  // A warm plaster wall with fine grain.
  const wall = Skia.Paint();
  wall.setShader(linear(0, 0, 0, h, ['#231B16', '#1B1511', '#120E0B'], [0, 0.5, 1]));
  c.drawRect(Skia.XYWHRect(0, 0, w, h), wall);
  const fine = Skia.Paint();
  fine.setShader(Skia.Shader.MakeFractalNoise(0.8, 0.8, 2, 4, 0, 0));
  fine.setColorFilter(Skia.ColorFilter.MakeMatrix(GRAY));
  fine.setBlendMode(BlendMode.SoftLight);
  fine.setAlphaf(0.22);
  c.drawRect(Skia.XYWHRect(0, 0, w, h), fine);
  // A lamp somewhere above the page: a broad warm glow.
  const lamp = Skia.Paint();
  lamp.setShader(Skia.Shader.MakeRadialGradient({ x: w * 0.62, y: oy + 140 * k }, w * 0.95, [color('rgba(255,196,130,0.16)'), color('rgba(255,180,110,0.05)'), color('rgba(255,180,110,0)')], [0, 0.5, 1], TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(0, 0, w, h), lamp);

  c.save();
  c.translate(ox, oy);
  c.scale(k, k);
  const left = 4;
  const right = DESIGN_W - 4;
  // Back panel: dark oak, in shadow under the shelf above.
  c.drawRect(Skia.XYWHRect(left - 6, SHELF_TOP - 4, right - left + 12, BOARD_Y + BOARD_H - SHELF_TOP + 22), blurPaint('#000000', 0.55, 12));
  const panel = Skia.Paint();
  panel.setShader(linear(0, SHELF_TOP, 0, BOARD_Y, ['#140D08', '#24170E', '#2D1D12'], [0, 0.45, 1]));
  c.drawRect(Skia.XYWHRect(left, SHELF_TOP, right - left, BOARD_Y - SHELF_TOP), panel);
  grain(c, left, SHELF_TOP, right - left, BOARD_Y - SHELF_TOP, 0.005, 0.18, 0.5, 9);
  // Light falls on the back panel from the room: brighter in the middle, dark at the top.
  const fall = Skia.Paint();
  fall.setShader(Skia.Shader.MakeRadialGradient({ x: DESIGN_W * 0.55, y: BOARD_Y - 20 }, 260, [color('rgba(255,190,120,0.12)'), color('rgba(255,190,120,0)')], null, TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(left, SHELF_TOP, right - left, BOARD_Y - SHELF_TOP), fall);
  const under = Skia.Paint();
  under.setShader(linear(0, SHELF_TOP, 0, SHELF_TOP + 70, ['rgba(0,0,0,0.75)', 'rgba(0,0,0,0.3)', 'rgba(0,0,0,0)']));
  c.drawRect(Skia.XYWHRect(left, SHELF_TOP, right - left, 70), under);
  // The shelf above, seen as its lit front edge.
  const above = Skia.Paint();
  above.setShader(linear(0, SHELF_TOP - 16, 0, SHELF_TOP, ['#6B4A2F', '#553823', '#3A2617']));
  c.drawRect(Skia.XYWHRect(left - 4, SHELF_TOP - 16, right - left + 8, 16), above);
  grain(c, left - 4, SHELF_TOP - 16, right - left + 8, 16, 0.004, 0.3, 0.6, 17);
  const aboveLit = Skia.Paint();
  aboveLit.setColor(color('#E2B17E'));
  aboveLit.setAlphaf(0.35);
  c.drawRect(Skia.XYWHRect(left - 4, SHELF_TOP - 16, right - left + 8, 1), aboveLit);

  // The board: a thick front lip with its top lit, and the books' contact shadow on it.
  c.drawRect(Skia.XYWHRect(left - 8, BOARD_Y + 6, right - left + 16, BOARD_H + 4), blurPaint('#000000', 0.6, 8));
  const board = Skia.Paint();
  board.setShader(linear(0, BOARD_Y, 0, BOARD_Y + BOARD_H, ['#9A6C45', '#7A5233', '#5A3B23', '#40291A'], [0, 0.18, 0.7, 1]));
  c.drawRect(Skia.XYWHRect(left - 8, BOARD_Y, right - left + 16, BOARD_H), board);
  grain(c, left - 8, BOARD_Y, right - left + 16, BOARD_H, 0.004, 0.35, 0.7, 23);
  const lip = Skia.Paint();
  lip.setColor(color('#F2C796'));
  lip.setAlphaf(0.45);
  c.drawRect(Skia.XYWHRect(left - 8, BOARD_Y + 5, right - left + 16, 1), lip);
  const top = Skia.Paint();
  top.setShader(linear(0, BOARD_Y, 0, BOARD_Y + 5, ['#C08A5A', '#8E6240']));
  c.drawRect(Skia.XYWHRect(left - 8, BOARD_Y, right - left + 16, 5), top);
  const contact = Skia.Paint();
  contact.setShader(linear(0, BOARD_Y - 2, 0, BOARD_Y + 4, ['rgba(10,5,2,0)', 'rgba(10,5,2,0.55)', 'rgba(10,5,2,0)']));
  c.drawRect(Skia.XYWHRect(left, BOARD_Y - 2, right - left, 6), contact);
  // Uprights at both ends.
  for (const x of [left - 10, right]) {
    const up = Skia.Paint();
    up.setShader(linear(x, 0, x + 10, 0, ['#4A3220', '#6E4B30', '#3A2717']));
    c.drawRect(Skia.XYWHRect(x, SHELF_TOP - 16, 10, BOARD_Y + BOARD_H - SHELF_TOP + 16), up);
  }
  const edge = Skia.Paint();
  edge.setStyle(PaintStyle.Stroke);
  edge.setStrokeWidth(1);
  edge.setColor(color('#000000'));
  edge.setAlphaf(0.4);
  c.save();
  c.clipRect(Skia.XYWHRect(left - 8, BOARD_Y, right - left + 16, BOARD_H), ClipOp.Intersect, true);
  c.drawLine(left - 8, BOARD_Y + BOARD_H - 0.5, right + 8, BOARD_Y + BOARD_H - 0.5, edge);
  c.restore();
  c.restore();

  // Vignette.
  const v = Skia.Paint();
  v.setShader(Skia.Shader.MakeRadialGradient({ x: w / 2, y: h * 0.45 }, h * 0.75, [color('rgba(0,0,0,0)'), color('rgba(0,0,0,0)'), color('rgba(6,3,2,0.6)')], [0, 0.55, 1], TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(0, 0, w, h), v);
  return rec.finishRecordingAsPicture();
}
