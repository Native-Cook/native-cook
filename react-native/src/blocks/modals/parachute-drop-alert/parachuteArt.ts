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
  type SkPath,
  Skia,
  StrokeCap,
  StrokeJoin,
  TileMode,
} from '@shopify/react-native-skia';

import { type Canopy, GORES, type Geo, type Pose } from './parachuteMotion';

/*
 Parachute Drop Alert — the drawing. Two canvases: the app under the text (sky with clouds and
 rooftops, the sheet, its cards, route and tab-bar icons: baked once, never redrawn) and the alert
 layer over it (scrim, suspension lines, the canopies and the card, redrawn each frame from the
 pose; transparent wherever it draws nothing). A canopy is seven gores between elliptical
 meridians with a scalloped hem, coral and cream, shaded across the dome; collapsing, its height
 falls, its hem sags in folds and its lines go slack.
*/

export type Fonts = { title: SkFont; body: SkFont; button: SkFont };
export type Layout = Geo & {
  top: number;
  skyH: number;
  sheetY: number;
  card1Y: number;
  card1H: number;
  sectionY: number;
  rowsY: number;
  rows: number;
  tabY: number;
  /** Button rects in card space (from the card's top left): later, track. */
  later: number[];
  track: number[];
};

const CORAL = '#EE6B4D';
const INK = '#1D2B3D';

export function makeLayout(w: number, h: number, top: number, bottom: number): Layout {
  const skyH = top + 250;
  const sheetY = skyH - 24;
  const card1Y = sheetY + 22;
  const card1H = 176;
  const sectionY = card1Y + card1H + 22;
  const rowsY = sectionY + 30;
  const tabY = h - Math.max(bottom, 8) - 56;
  const rows = Math.max(0, Math.min(3, Math.floor((tabY - 12 - rowsY) / 78)));
  const cardW = Math.min(w - 40, 360);
  const cardH = 172;
  const canW = cardW * 0.74;
  const pad = 20;
  const bw = (cardW - pad * 2 - 10) / 2;
  return {
    w,
    h,
    cardW,
    cardH,
    landX: w / 2,
    landY: skyH + 44,
    canW,
    canH: canW * 0.42,
    lines: 104,
    top,
    skyH,
    sheetY,
    card1Y,
    card1H,
    sectionY,
    rowsY,
    rows,
    tabY,
    later: [pad, cardH - 62, bw, 44],
    track: [pad + bw + 10, cardH - 62, bw, 44],
  };
}

function paint(color: string, alpha = 1) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  if (alpha < 1) p.setAlphaf(alpha);
  return p;
}

function stroke(color: string, width: number, alpha = 1) {
  'worklet';
  const p = paint(color, alpha);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  p.setStrokeCap(StrokeCap.Round);
  p.setStrokeJoin(StrokeJoin.Round);
  return p;
}

function colors(list: string[]) {
  'worklet';
  const out = [];
  for (let i = 0; i < list.length; i += 1) out.push(Skia.Color(list[i]));
  return out;
}

function linear(x0: number, y0: number, x1: number, y1: number, list: string[], pos: number[] | null = null) {
  'worklet';
  return Skia.Shader.MakeLinearGradient(Skia.Point(x0, y0), Skia.Point(x1, y1), colors(list), pos, TileMode.Clamp);
}

function blurred(color: string, alpha: number, sigma: number) {
  'worklet';
  const p = paint(color, alpha);
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma, true));
  return p;
}

function textWidth(font: SkFont, text: string) {
  const ws = font.getGlyphWidths(font.getGlyphIDs(text));
  let sum = 0;
  for (let i = 0; i < ws.length; i += 1) sum += ws[i];
  return sum;
}

function grain(alpha: number, seed: number) {
  const p = Skia.Paint();
  p.setShader(Skia.Shader.MakeFractalNoise(0.9, 0.9, 2, seed, 0, 0));
  p.setColorFilter(Skia.ColorFilter.MakeMatrix([0.33, 0.33, 0.33, 0, 0, 0.33, 0.33, 0.33, 0, 0, 0.33, 0.33, 0.33, 0, 0, 0, 0, 0, 0, alpha]));
  p.setBlendMode(BlendMode.Overlay);
  return p;
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

function cloud(c: SkCanvas, x: number, y: number, s: number, a: number) {
  const puffs = [-0.9, 0.1, 0.32, -0.3, -0.25, 0.42, 0.25, -0.38, 0.5, 0.8, 0.05, 0.3];
  for (let i = 0; i < puffs.length; i += 3) c.drawCircle(x + puffs[i] * 60 * s, y + puffs[i + 1] * 30 * s, puffs[i + 2] * 70 * s, blurred('#FFFFFF', a, 6 * s));
  c.drawOval(Skia.XYWHRect(x - 70 * s, y + 4 * s, 140 * s, 22 * s), blurred('#FFFFFF', a * 0.9, 6 * s));
}

/** A parcel: a little box in cream lines. */
function parcel(c: SkCanvas, x: number, y: number, s: number, color: string) {
  const line = stroke(color, 1.6 * s);
  const p = Skia.PathBuilder.Make()
    .moveTo(x, y - 9 * s)
    .lineTo(x + 10 * s, y - 4 * s)
    .lineTo(x + 10 * s, y + 7 * s)
    .lineTo(x, y + 12 * s)
    .lineTo(x - 10 * s, y + 7 * s)
    .lineTo(x - 10 * s, y - 4 * s)
    .close()
    .moveTo(x - 10 * s, y - 4 * s)
    .lineTo(x, y + 1 * s)
    .lineTo(x + 10 * s, y - 4 * s)
    .moveTo(x, y + 1 * s)
    .lineTo(x, y + 12 * s)
    .moveTo(x - 5 * s, y - 6.5 * s)
    .lineTo(x + 5 * s, y - 1.5 * s)
    .build();
  c.drawPath(p, line);
}

function tile(c: SkCanvas, x: number, y: number, size: number, from: string, to: string) {
  const rr = Skia.RRectXY(Skia.XYWHRect(x, y, size, size), size * 0.3, size * 0.3);
  const p = Skia.Paint();
  p.setShader(linear(x, y, x + size, y + size, [from, to]));
  c.drawRRect(rr, p);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x + 0.5, y + 0.5, size - 1, size - 1), size * 0.3, size * 0.3), stroke('#FFFFFF', 1, 0.25));
}

function cardBox(c: SkCanvas, x: number, y: number, w: number, h: number, r: number) {
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y + 6, w, h), r, r), blurred('#5B4A3A', 0.12, 10));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y + 1, w, h), r, r), blurred('#5B4A3A', 0.12, 1.5));
  const p = Skia.Paint();
  p.setShader(linear(x, y, x, y + h, ['#FFFDFA', '#FBF8F3']));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r), p);
}

/** The app under the alert (everything but its text). */
export function bakeApp(g: Layout, scale: number) {
  return bake(g.w, g.h, scale, (c) => {
    const { w, h } = g;
    const sky = Skia.Paint();
    sky.setShader(linear(0, 0, 0, g.skyH, ['#8FB8DD', '#B4CFE7', '#D8E2EC', '#F2DCCB'], [0, 0.4, 0.72, 1]));
    c.drawRect(Skia.XYWHRect(0, 0, w, g.skyH + 10), sky);
    c.drawCircle(w * 0.82, g.top + 40, 90, blurred('#FFF4E0', 0.45, 40));
    cloud(c, w * 0.18, g.top + 118, 0.8, 0.75);
    cloud(c, w * 0.86, g.top + 168, 0.6, 0.6);
    cloud(c, w * 0.52, g.top + 214, 0.45, 0.5);
    // Rooftops on the horizon, two layers.
    const roofs = (yb: number, seed: number, color: string, hgt: number) => {
      const b = Skia.PathBuilder.Make().moveTo(0, yb);
      let x = 0;
      let i = 0;
      while (x < w) {
        const bw = 26 + 30 * Math.abs(Math.sin(seed + i * 1.7));
        const bh = hgt * (0.45 + 0.55 * Math.abs(Math.sin(seed * 2 + i * 2.3)));
        b.lineTo(x, yb - bh);
        if (i % 3 === 1) {
          b.lineTo(x + bw * 0.5, yb - bh - 12);
          b.lineTo(x + bw, yb - bh);
        } else {
          if (i % 2 === 0) {
            b.lineTo(x + bw * 0.25, yb - bh);
            b.lineTo(x + bw * 0.25, yb - bh - 9);
            b.lineTo(x + bw * 0.35, yb - bh - 9);
            b.lineTo(x + bw * 0.35, yb - bh);
          }
          b.lineTo(x + bw, yb - bh);
        }
        x += bw;
        i += 1;
      }
      b.lineTo(w, yb).close();
      c.drawPath(b.build(), paint(color));
    };
    roofs(g.skyH + 4, 1.3, '#BCCBDA', 54);
    roofs(g.skyH + 4, 4.1, '#A5B7C9', 34);
    c.drawRect(Skia.XYWHRect(0, 0, w, g.skyH + 10), grain(0.05, 3));
    // The sheet.
    const sheet = Skia.Paint();
    sheet.setShader(linear(0, g.sheetY, 0, h, ['#F7F3EE', '#F3EEE7', '#EEE8E0']));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(0, g.sheetY, w, h - g.sheetY + 40), 28, 28), sheet);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(w / 2 - 18, g.sheetY + 8, 36, 5), 2.5, 2.5), paint('#1D2B3D', 0.14));
    // Card 1: the parcel on its way, with its route.
    const x = 16;
    const cw = w - 32;
    cardBox(c, x, g.card1Y, cw, g.card1H, 22);
    tile(c, x + 16, g.card1Y + 16, 44, '#F58B66', '#E2553C');
    parcel(c, x + 38, g.card1Y + 37, 1, '#FFF3EA');
    const ry = g.card1Y + 104;
    const x0 = x + 30;
    const x1 = x + cw - 30;
    c.drawLine(x0, ry, x1, ry, stroke('#1D2B3D', 3, 0.1));
    const done = x0 + (x1 - x0) * 0.78;
    const prog = Skia.Paint();
    prog.setStyle(PaintStyle.Stroke);
    prog.setStrokeWidth(3);
    prog.setStrokeCap(StrokeCap.Round);
    prog.setShader(linear(x0, 0, done, 0, ['#F6A07E', CORAL]));
    c.drawLine(x0, ry, done, ry, prog);
    for (let k = 0; k < 4; k += 1) {
      const sx = x0 + ((x1 - x0) * k) / 3;
      const reached = sx <= done;
      c.drawCircle(sx, ry, 7, paint(reached ? CORAL : '#E4DDD3'));
      c.drawCircle(sx, ry, 3, paint('#FFFDFA'));
    }
    // The van, just past "Out for delivery".
    c.drawCircle(done, ry, 12, blurred(CORAL, 0.35, 5));
    c.drawCircle(done, ry, 10, paint('#FFFDFA'));
    c.drawCircle(done, ry, 10, stroke(CORAL, 2));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(done - 5.5, ry - 3.5, 8, 6), 1.2, 1.2), paint(CORAL));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(done + 2, ry - 1.5, 3.5, 4), 1, 1), paint(CORAL));
    c.drawLine(x + 16, g.card1Y + 140, x + cw - 16, g.card1Y + 140, stroke('#1D2B3D', 1, 0.07));
    // Rows.
    for (let r = 0; r < g.rows; r += 1) {
      const y = g.rowsY + r * 78;
      cardBox(c, x, y, cw, 68, 20);
      tile(c, x + 14, y + 12, 44, r % 2 === 0 ? '#9EC3E3' : '#E9C37A', r % 2 === 0 ? '#6E9DC9' : '#D79E45');
      parcel(c, x + 36, y + 33, 0.9, '#FFFBF4');
    }
    // Tab bar.
    const tb = Skia.Paint();
    tb.setShader(linear(0, g.tabY, 0, h, ['#FBF8F4', '#F6F2EC']));
    c.drawRect(Skia.XYWHRect(0, g.tabY, w, h - g.tabY), tb);
    c.drawLine(0, g.tabY, w, g.tabY, stroke('#1D2B3D', 1, 0.08));
    for (let k = 0; k < 4; k += 1) {
      const cx = (w * (k + 0.5)) / 4;
      const cy = g.tabY + 18;
      const col = k === 1 ? CORAL : '#8A93A0';
      const s = stroke(col, 1.8);
      if (k === 0) {
        c.drawPath(Skia.PathBuilder.Make().moveTo(cx - 8, cy - 1).lineTo(cx, cy - 8).lineTo(cx + 8, cy - 1).moveTo(cx - 6, cy - 3).lineTo(cx - 6, cy + 7).lineTo(cx + 6, cy + 7).lineTo(cx + 6, cy - 3).build(), s);
      } else if (k === 1) {
        c.drawPath(Skia.PathBuilder.Make().moveTo(cx, cy + 8).cubicTo(cx - 11, cy - 2, cx - 7, cy - 9, cx, cy - 9).cubicTo(cx + 7, cy - 9, cx + 11, cy - 2, cx, cy + 8).build(), s);
        c.drawCircle(cx, cy - 2, 2.6, s);
      } else if (k === 2) {
        parcel(c, cx, cy - 1, 0.75, col);
      } else {
        c.drawCircle(cx, cy - 4, 4, s);
        c.drawPath(Skia.PathBuilder.Make().moveTo(cx - 8, cy + 8).quadTo(cx, cy - 2, cx + 8, cy + 8).build(), s);
      }
    }
  });
}

/** The alert card, drawn at the origin. */
export function bakeCard(g: Layout, f: Fonts, scale: number) {
  return bake(g.cardW, g.cardH, scale, (c) => {
    const w = g.cardW;
    const h = g.cardH;
    const face = Skia.Paint();
    face.setShader(linear(0, 0, 0, h, ['#FFFEFB', '#FBF7F1', '#F5EFE6']));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(0, 0, w, h), 26, 26), face);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(0.5, 0.5, w - 1, h - 1), 25.5, 25.5), stroke('#FFFFFF', 1, 0.9));
    c.save();
    c.clipRRect(Skia.RRectXY(Skia.XYWHRect(0, 0, w, h), 26, 26), ClipOp.Intersect, true);
    c.drawRect(Skia.XYWHRect(0, 0, w, h), grain(0.05, 9));
    c.restore();
    tile(c, 20, 20, 48, '#F58B66', '#E2553C');
    parcel(c, 44, 43, 1.1, '#FFF3EA');
    c.drawText('Your parcel is 4 min away', 82, 40, paint(INK), f.title);
    c.drawText('Leo is on Linden Street now.', 82, 62, paint('#5C6776'), f.body);
    c.drawText('Door code 2417 is ready.', 82, 81, paint('#5C6776'), f.body);
    const [lx, ly, lw, lh] = g.later;
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(lx, ly, lw, lh), lh / 2, lh / 2), paint('#EEE7DD'));
    const later = 'Later';
    c.drawText(later, lx + lw / 2 - textWidth(f.button, later) / 2, ly + 28, paint(INK), f.button);
    const [tx, ty, tw, th] = g.track;
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(tx, ty + 2, tw, th), th / 2, th / 2), blurred('#C8452C', 0.35, 4));
    const tp = Skia.Paint();
    tp.setShader(linear(0, ty, 0, ty + th, ['#F48563', CORAL, '#DC5236']));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(tx, ty, tw, th), th / 2, th / 2), tp);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(tx + 0.5, ty + 0.5, tw - 1, th - 1), th / 2, th / 2), stroke('#FFFFFF', 1, 0.25));
    const track = 'Track';
    c.drawText(track, tx + tw / 2 - textWidth(f.button, track) / 2, ty + 28, paint('#FFF8F2'), f.button);
  });
}

/** Meridian point s (0 apex .. 1 hem) of gore edge e (0..GORES) of a canopy, in its own frame (hem centre at 0). */
function meridian(c: Canopy, e: number, s: number, out: number[]) {
  'worklet';
  const phi = (e / GORES - 0.5) * Math.PI;
  const k = c.collapse;
  out[0] = (c.w / 2) * Math.sin(phi) * Math.sin((s * Math.PI) / 2) * (1 + 0.1 * k * Math.sin(e * 2.1 + s * 3));
  // Collapsing: the hem drops in folds and the crown sinks below it.
  const sag = k * (10 + 14 * Math.abs(Math.sin(e * 1.9 + 0.6))) * s;
  out[1] = -c.h * Math.cos((s * Math.PI) / 2) + sag;
}

function canopyPath(c: Canopy) {
  'worklet';
  const b = Skia.PathBuilder.Make();
  const m = [0, 0];
  for (let s = 0; s <= 8; s += 1) {
    meridian(c, 0, s / 8, m);
    if (s === 0) b.moveTo(m[0], m[1]);
    else b.lineTo(m[0], m[1]);
  }
  for (let e = 1; e <= GORES; e += 1) {
    const a = [0, 0];
    const z = [0, 0];
    meridian(c, e - 1, 1, a);
    meridian(c, e, 1, z);
    b.quadTo((a[0] + z[0]) / 2, (a[1] + z[1]) / 2 - c.h * 0.09 * (1 - c.collapse * 0.5), z[0], z[1]);
  }
  for (let s = 8; s >= 0; s -= 1) {
    meridian(c, GORES, s / 8, m);
    b.lineTo(m[0], m[1]);
  }
  b.close();
  return b.build();
}

function gorePath(c: Canopy, e: number): SkPath {
  'worklet';
  const b = Skia.PathBuilder.Make();
  const m = [0, 0];
  for (let s = 0; s <= 8; s += 1) {
    meridian(c, e, s / 8, m);
    if (s === 0) b.moveTo(m[0], m[1]);
    else b.lineTo(m[0], m[1]);
  }
  const z = [0, 0];
  meridian(c, e + 1, 1, z);
  b.quadTo((m[0] + z[0]) / 2, (m[1] + z[1]) / 2 - c.h * 0.09 * (1 - c.collapse * 0.5), z[0], z[1]);
  for (let s = 8; s >= 0; s -= 1) {
    meridian(c, e + 1, s / 8, m);
    b.lineTo(m[0], m[1]);
  }
  b.close();
  return b.build();
}

/** Where a canopy's lines meet the card (two clips at the card's top corners), in screen space. */
function attach(g: Layout, p: Pose, side: number, out: number[]) {
  'worklet';
  const lx = side * (g.cardW / 2 - 34);
  const ly = -g.cardH / 2;
  const cs = Math.cos(p.cardRot);
  const sn = Math.sin(p.cardRot);
  out[0] = p.cardX + lx * cs - ly * sn;
  out[1] = p.cardY + lx * sn + ly * cs;
}

function drawCanopy(cv: SkCanvas, g: Layout, p: Pose, c: Canopy) {
  'worklet';
  if (c.alpha <= 0.001) return;
  // Lines first: from each hem point to the nearer clip (or trailing free once released).
  const m = [0, 0];
  const a = [0, 0];
  const line = stroke('#2E3A4A', 1, 0.6 * c.alpha);
  const cs = Math.cos(c.tilt);
  const sn = Math.sin(c.tilt);
  for (let e = 0; e <= GORES; e += 1) {
    meridian(c, e, 1, m);
    const hx = c.x + m[0] * cs - m[1] * sn;
    const hy = c.y + m[0] * sn + m[1] * cs;
    if (c.attached === 1) attach(g, p, e < GORES / 2 ? -1 : 1, a);
    else {
      a[0] = c.x + (e / GORES - 0.5) * 30 - 40;
      a[1] = c.y + g.lines * 0.55;
    }
    const mx = (hx + a[0]) / 2 + c.slack * 18 * (e / GORES - 0.5);
    const my = (hy + a[1]) / 2 + c.slack * 30;
    cv.drawPath(Skia.PathBuilder.Make().moveTo(hx, hy).quadTo(mx, my, a[0], a[1]).build(), line);
  }
  cv.save();
  cv.translate(c.x, c.y);
  cv.rotate((c.tilt * 180) / Math.PI, 0, 0);
  const outline = canopyPath(c);
  const lay = Skia.Paint();
  lay.setAlphaf(c.alpha);
  cv.saveLayer(lay);
  for (let e = 0; e < GORES; e += 1) cv.drawPath(gorePath(c, e), paint(e % 2 === 0 ? CORAL : '#FBEFE2'));
  // Shade across the dome: lit from the upper left, darker at the far edge and the hem.
  cv.save();
  cv.clipPath(outline, ClipOp.Intersect, true);
  const sh = Skia.Paint();
  sh.setShader(linear(-c.w / 2, 0, c.w / 2, 0, ['rgba(255,255,255,0.22)', 'rgba(255,255,255,0)', 'rgba(60,30,30,0.1)', 'rgba(60,30,30,0.32)'], [0, 0.35, 0.7, 1]));
  cv.drawRect(Skia.XYWHRect(-c.w, -c.h * 1.4, c.w * 2, c.h * 1.8), sh);
  const hem = Skia.Paint();
  hem.setShader(linear(0, -c.h, 0, 10, ['rgba(255,255,255,0.1)', 'rgba(0,0,0,0)', 'rgba(70,30,20,0.25)'], [0, 0.6, 1]));
  cv.drawRect(Skia.XYWHRect(-c.w, -c.h * 1.4, c.w * 2, c.h * 1.8), hem);
  cv.restore();
  for (let e = 1; e < GORES; e += 1) {
    const b = Skia.PathBuilder.Make();
    for (let s = 0; s <= 8; s += 1) {
      meridian(c, e, s / 8, m);
      if (s === 0) b.moveTo(m[0], m[1]);
      else b.lineTo(m[0], m[1]);
    }
    cv.drawPath(b.build(), stroke('#7A3426', 0.8, 0.35));
  }
  cv.drawPath(outline, stroke('#7A3426', 1, 0.3));
  // The vent at the crown.
  meridian(c, GORES / 2, 0.04, m);
  cv.drawOval(Skia.XYWHRect(m[0] - c.w * 0.05, m[1] - 2, c.w * 0.1, 5), paint('#3A2420', 0.55 * (1 - c.collapse)));
  cv.restore();
  cv.restore();
}

export function drawAlert(c: SkCanvas, g: Layout, card: SkImage, p: Pose, pressed: number) {
  'worklet';
  if (p.fade <= 0.001) return;
  const faded = p.fade < 0.999;
  if (faded) {
    const lp = Skia.Paint();
    lp.setAlphaf(p.fade);
    c.saveLayer(lp);
  }
  if (p.scrim > 0.002) c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), paint('#1A2433', p.scrim));
  if (p.shown === 0) {
    if (faded) c.restore();
    return;
  }
  // Canopies behind the card first (a draped one lies over its top edge, so it comes after).
  for (let i = 0; i < p.canopies.length; i += 1) if (p.canopies[i].collapse < 0.35) drawCanopy(c, g, p, p.canopies[i]);
  c.save();
  c.translate(p.cardX, p.cardY);
  c.rotate((p.cardRot * 180) / Math.PI, 0, 0);
  c.translate(0, g.cardH / 2);
  c.scale(p.sx, p.sy);
  c.translate(-g.cardW / 2, -g.cardH);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(4, 22, g.cardW - 8, g.cardH), 26, 26), blurred('#0E1726', 0.3, 22));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(0, 2, g.cardW, g.cardH), 26, 26), blurred('#0E1726', 0.22, 2.5));
  c.drawImageRectOptions(card, Skia.XYWHRect(0, 0, card.width(), card.height()), Skia.XYWHRect(0, 0, g.cardW, g.cardH), FilterMode.Linear, MipmapMode.None, Skia.Paint());
  if (pressed >= 0) {
    const r = pressed === 0 ? g.later : g.track;
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(r[0], r[1], r[2], r[3]), r[3] / 2, r[3] / 2), paint('#1D2B3D', 0.1));
  }
  // The two clips the lines hook into.
  for (const s of [-1, 1]) {
    const x = g.cardW / 2 + s * (g.cardW / 2 - 34);
    c.drawCircle(x, 0, 4.5, paint('#C9CFD6'));
    c.drawCircle(x, 0, 4.5, stroke('#6E7884', 1));
  }
  c.restore();
  for (let i = 0; i < p.canopies.length; i += 1) if (p.canopies[i].collapse >= 0.35) drawCanopy(c, g, p, p.canopies[i]);
  if (faded) c.restore();
}
