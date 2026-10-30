// Cabin Climate Car — drawing, every frame, in the pin's pixels (see ccc-theme.ts).

import { BlurStyle, ClipOp, PaintStyle, type SkCanvas, type SkFont, type SkImage, type SkTypeface, Skia, StrokeCap, StrokeJoin, TileMode } from '@shopify/react-native-skia';

import { clamp01, type Frame, LOOP, mix, ROLL, smooth } from './ccc-motion';
import { C, COPY, PIN_W, S, TEMP, UI } from './ccc-theme';

export type Faces = { regular: SkTypeface; medium: SkTypeface; semi: SkTypeface; bold: SkTypeface };
export type Res = { car: SkImage; f: { pill: SkFont; title: SkFont; temp: SkFont; sign: SkFont; label: SkFont; row: SkFont; chip: SkFont; tab: SkFont } };

export function makeRes(f: Faces, car: SkImage): Res {
  return {
    car,
    f: {
      pill: Skia.Font(f.semi, 30),
      title: Skia.Font(f.medium, 30),
      temp: Skia.Font(f.semi, 116),
      sign: Skia.Font(f.regular, 54),
      label: Skia.Font(f.medium, 28),
      row: Skia.Font(f.regular, 29),
      chip: Skia.Font(f.medium, 27),
      tab: Skia.Font(f.medium, 21),
    },
  };
}

export type Geo = { w: number; h: number; s: number };


function col(h: string, a = 1) {
  'worklet';
  const n = parseInt(h.slice(1), 16);
  return Float32Array.of(((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255, a);
}
function mixCol(a: string, b: string, k: number, alpha = 1) {
  'worklet';
  const x = col(a);
  const y = col(b);
  return Float32Array.of(mix(x[0], y[0], k), mix(x[1], y[1], k), mix(x[2], y[2], k), alpha);
}
function paint(h: string, a = 1) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(col(h, a));
  return p;
}
function stroke(h: string, w: number, a = 1) {
  'worklet';
  const p = paint(h, a);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(w);
  p.setStrokeCap(StrokeCap.Round);
  p.setStrokeJoin(StrokeJoin.Round);
  return p;
}
function rrect(x: number, y: number, w: number, h: number, r: number) {
  'worklet';
  return Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r);
}
function text(c: SkCanvas, s: string, x: number, cy: number, font: SkFont, p: ReturnType<typeof Skia.Paint>, ax = 0) {
  'worklet';
  const w = font.measureText(s).width;
  c.drawText(s, x - w * ax, cy + font.getSize() * 0.36, p, font);
  return w;
}
/** The air's colour for a temperature: blue when cool, teal at 70, orange when warm. */
function airColor(heat: number, a: number) {
  'worklet';
  const k = (heat - TEMP.rest) / 10;
  if (k < 0) return mixCol(C.mild, C.cold, clamp01(-k * 2.2), a);
  return mixCol(C.mild, C.warm, clamp01(k * 2.2), a);
}
/** A card's white surface with a soft shadow; with `press` > 0 the caller's content presses with it (call c.restore() after). */
function card(c: SkCanvas, x: number, y: number, w: number, h: number, r: number, press: number) {
  'worklet';
  const s = 1 - 0.04 * press;
  c.save();
  c.translate(x + w / 2, y + h / 2);
  c.scale(s, s);
  c.translate(-(x + w / 2), -(y + h / 2));
  const sh = paint('#8A93A8', 0.12);
  sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 14, true));
  c.drawRRect(rrect(x + 4, y + 10, w - 8, h, r), sh);
  c.drawRRect(rrect(x, y, w, h, r), paint(C.card));
}

// ------------------------------------------------------------------ the car and its air

/** Paths the cabin air follows over the car (in the pin's pixels): in at the windscreen, round the cabin, out at the back. */
const FLOWS = [
  [
    [455, 268],
    [400, 236],
    [320, 238],
    [265, 268],
    [235, 300],
  ],
  [
    [470, 285],
    [410, 262],
    [340, 262],
    [280, 286],
    [250, 318],
  ],
  [
    [445, 300],
    [395, 288],
    [330, 290],
    [285, 308],
    [262, 330],
  ],
];

function catmull(pts: number[][], u: number) {
  'worklet';
  const n = pts.length - 1;
  const x = Math.min(n - 1e-4, Math.max(0, u * n));
  const i = Math.floor(x);
  const t = x - i;
  const p0 = pts[Math.max(0, i - 1)];
  const p1 = pts[i];
  const p2 = pts[i + 1];
  const p3 = pts[Math.min(n, i + 2)];
  const t2 = t * t;
  const t3 = t2 * t;
  const q = (a: number, b: number, cc: number, d: number) => 0.5 * (2 * b + (-a + cc) * t + (2 * a - 5 * b + 4 * cc - d) * t2 + (-a + 3 * b - 3 * cc + d) * t3);
  return { x: q(p0[0], p1[0], p2[0], p3[0]), y: q(p0[1], p1[1], p2[1], p3[1]) };
}

function drawCar(c: SkCanvas, r: Res, f: Frame) {
  'worklet';
  const U = UI.car;
  const iw = U.w;
  const ih = (iw * r.car.height()) / r.car.width();
  c.drawImageRect(r.car, Skia.XYWHRect(0, 0, r.car.width(), r.car.height()), Skia.XYWHRect(U.x - iw / 2, U.y - ih / 2, iw, ih), paint('#FFFFFF'));
  // a glow in the cabin in the air's colour
  const g = Skia.Paint();
  const gx = U.x + ((350 - 365) * U.w) / 660;
  const gy = U.y + ((270 - 318) * U.w) / 660;
  g.setShader(Skia.Shader.MakeRadialGradient({ x: gx, y: gy }, 115, [airColor(f.heat, 0.32), airColor(f.heat, 0)], null, TileMode.Clamp));
  c.drawOval(Skia.XYWHRect(gx - 115, gy - 68, 230, 136), g);
  // streams of air over the cabin: streaks running along the paths
  const strength = 0.55 + 0.45 * clamp01(Math.abs(f.heat - f.temp) + Math.abs(f.temp - TEMP.rest) / 6);
  for (let k = 0; k < FLOWS.length; k++) {
    for (let j = 0; j < 4; j++) {
      const u0 = ((f.amb / LOOP) * 6 + j / 4 + k * 0.13) % 1;
      const p = Skia.Path.Make();
      for (let s = 0; s <= 8; s++) {
        const u = u0 - 0.16 + (s / 8) * 0.16;
        if (u < 0) continue;
        const q0 = catmull(FLOWS[k], u);
        // the paths were laid out for the car at (365, 318), 660 px wide
        const q = { x: U.x + ((q0.x - 365) * U.w) / 660, y: U.y + ((q0.y - 318) * U.w) / 660 };
        if (p.countPoints() === 0) p.moveTo(q.x, q.y);
        else p.lineTo(q.x, q.y);
      }
      const a = Math.sin(Math.PI * u0) * 0.75 * strength;
      if (p.countPoints() > 1) c.drawPath(p, (() => {
        const sp = Skia.Paint();
        sp.setAntiAlias(true);
        sp.setStyle(PaintStyle.Stroke);
        sp.setStrokeWidth(4);
        sp.setStrokeCap(StrokeCap.Round);
        sp.setColor(airColor(f.heat, a));
        return sp;
      })());
    }
  }
}

function drawPill(c: SkCanvas, r: Res, f: Frame) {
  'worklet';
  const P = UI.pill;
  const s = f.card === 5 ? 1 - 0.04 * f.cardK : 1;
  c.save();
  c.translate(P.x, P.y);
  c.scale(s, s);
  c.drawRRect(rrect(-P.w / 2, -P.h / 2, P.w, P.h, P.h / 2), paint(C.pill));
  c.drawCircle(-P.w / 2 + 34, 0, 23, stroke(C.teal, 3));
  text(c, COPY.initials, -P.w / 2 + 34, 0, r.f.pill, paint(C.teal), 0.5);
  text(c, COPY.edit, -P.w / 2 + 70, 0, r.f.pill, paint('#FFFFFF'));
  const cx = P.w / 2 - 26;
  c.drawLine(cx - 4, -7, cx + 3, 0, stroke('#FFFFFF', 2.8));
  c.drawLine(cx - 4, 7, cx + 3, 0, stroke('#FFFFFF', 2.8));
  c.restore();
}

function drawTemp(c: SkCanvas, r: Res, f: Frame) {
  'worklet';
  const T = UI.temp;
  card(c, T.x, T.y, T.w, T.h, T.r, 0);
  // the card's foot glows with the air's colour
  c.save();
  c.clipRRect(rrect(T.x, T.y, T.w, T.h, T.r), ClipOp.Intersect, true);
  const g = Skia.Paint();
  g.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: T.y + T.h * 0.45 }, { x: 0, y: T.y + T.h }, [airColor(f.heat, 0), airColor(f.heat, 0.16)], null, TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(T.x, T.y, T.w, T.h), g);
  c.restore();
  const cx = T.x + T.w / 2;
  // + and −, each pressing in (0.96) under the finger
  for (const [sign, y, k] of [
    ['+', T.plus, f.plusK],
    ['−', T.minus, f.minusK],
  ] as [string, number, number][]) {
    if (k > 0.01) c.drawCircle(cx, y, 46 * (0.8 + 0.2 * k), paint('#EEF1F6', k));
    c.save();
    c.translate(cx, y);
    c.scale(1 - 0.04 * k, 1 - 0.04 * k);
    text(c, sign, 0, -4, r.f.sign, paint(C.ink), 0.5);
    c.restore();
  }
  // the degrees: the changing digit rolls (up when warmer, down when cooler)
  const roll = smooth(f.tempAt / ROLL);
  const a = String(f.from);
  const b = String(f.temp);
  const deg = '°';
  const fnt = r.f.temp;
  const w = fnt.measureText(b).width + fnt.measureText(deg).width * 0.85;
  const x0 = cx - w / 2;
  const up = f.temp > f.from ? 1 : -1;
  const H = 120;
  c.save();
  c.clipRect(Skia.XYWHRect(T.x, T.value - 80, T.w, 160), ClipOp.Intersect, true);
  let px = x0;
  for (let i = 0; i < b.length; i++) {
    const ca = a.length === b.length ? a[i] : '';
    const cb = b[i];
    const cw = fnt.measureText(cb).width;
    if (roll >= 1 || ca === cb) text(c, cb, px, T.value, fnt, paint(C.ink));
    else {
      text(c, ca, px, T.value - up * H * roll, fnt, paint(C.ink, 1 - roll));
      text(c, cb, px, T.value + up * H * (1 - roll), fnt, paint(C.ink, roll));
    }
    px += cw;
  }
  text(c, deg, px - 4, T.value, fnt, paint(C.ink));
  c.restore();
  c.restore();
}

/** A car seat from the side (back to the left), drawn. */
function seat(c: SkCanvas, x0: number, y0: number) {
  'worklet';
  // drawn at 1.1× about its foot, to the pin's seat size (about 100 px from back to front)
  c.save();
  c.translate(x0, y0 + 16);
  c.scale(1.1, 1.1);
  c.translate(-x0, -(y0 + 16));
  const x = x0;
  const y = y0;
  const p = Skia.Path.Make();
  p.moveTo(x - 30, y - 64);
  p.cubicTo(x - 40, y - 66, x - 46, y - 56, x - 42, y - 44);
  p.lineTo(x - 30, y + 4);
  p.cubicTo(x - 26, y + 14, x - 18, y + 16, x - 8, y + 16);
  p.lineTo(x + 36, y + 16);
  p.cubicTo(x + 46, y + 16, x + 48, y + 2, x + 38, y - 2);
  p.lineTo(x - 6, y - 6);
  p.lineTo(x - 18, y - 56);
  p.cubicTo(x - 20, y - 62, x - 24, y - 64, x - 30, y - 64);
  p.close();
  const sp = Skia.Paint();
  sp.setAntiAlias(true);
  sp.setShader(Skia.Shader.MakeLinearGradient({ x: x - 40, y: y - 60 }, { x: x + 40, y: y + 16 }, [col(C.seatHi), col(C.seat)], null, TileMode.Clamp));
  c.drawPath(p, sp);
  c.drawRRect(rrect(x - 36, y - 80, 14, 18, 6), paint(C.seat));
  c.drawOval(Skia.XYWHRect(x - 46, y + 30, 92, 8), paint('#AEB4C2', 0.35));
  c.restore();
}

function drawLegroom(c: SkCanvas, r: Res, f: Frame) {
  'worklet';
  const L = UI.legroom;
  card(c, L.x, L.y, L.w, L.h, L.r, f.card === 3 ? f.cardK : 0);
  // extra rear legroom: the front seat slides forward, the rear one back; the bars show the room
  const k = f.leg;
  const shift = 16 * k;
  const bars = [mix(1, 0.6, k), mix(1, 1.45, k)];
  for (let i = 0; i < 2; i++) {
    const sx = L.seats[i] + (i === 0 ? -shift : shift) * 0.4;
    seat(c, sx, L.seatY);
    // set warm, the seats heat up: a red glow on the cushion and the back
    const warm = clamp01((f.heat - 71) / 3);
    if (warm > 0.01) {
      const hp = Skia.Paint();
      hp.setShader(Skia.Shader.MakeRadialGradient({ x: sx + 14, y: L.seatY + 6 }, 40, [col('#FF5A3C', 0.55 * warm), col('#FF5A3C', 0)], null, TileMode.Clamp));
      c.drawOval(Skia.XYWHRect(sx - 26, L.seatY - 14, 80, 40), hp);
      const bp2 = Skia.Paint();
      bp2.setShader(Skia.Shader.MakeRadialGradient({ x: sx - 30, y: L.seatY - 34 }, 30, [col('#FF5A3C', 0.45 * warm), col('#FF5A3C', 0)], null, TileMode.Clamp));
      c.drawOval(Skia.XYWHRect(sx - 54, L.seatY - 70, 48, 72), bp2);
    }
    const bh = 40 * bars[i];
    // the bar stands 59 px right of its seat (pin: bars at x 504 and 618)
    c.drawRRect(rrect(sx + 59, L.seatY + 20 - bh, 13, bh, 6.5), paint(C.green));
  }
  // the label crossfades
  const lk = smooth((f.legAt - 0.05) / 0.28);
  const on = f.legOn ? 1 : 0;
  const prev = COPY.legroom[1 - on];
  const next = COPY.legroom[on];
  const cx = L.x + L.w / 2;
  if (lk >= 1) text(c, next, cx, L.label, r.f.label, paint(C.mute), 0.5);
  else {
    text(c, prev, cx, L.label - 10 * lk, r.f.label, paint(C.mute, 1 - lk), 0.5);
    text(c, next, cx, L.label + 10 * (1 - lk), r.f.label, paint(C.mute, lk), 0.5);
  }
  c.restore();
}

function fanIcon(c: SkCanvas, x: number, y: number, ang: number) {
  'worklet';
  c.save();
  c.translate(x, y);
  c.rotate(ang, 0, 0);
  for (let i = 0; i < 4; i++) {
    c.save();
    c.rotate(i * 90, 0, 0);
    const b = Skia.Path.Make();
    b.moveTo(0, -4);
    b.cubicTo(-4, -16, 4, -32, 16, -30);
    b.cubicTo(24, -28, 22, -16, 8, -8);
    c.drawPath(b, stroke(C.ink, 3.6));
    c.restore();
  }
  c.restore();
}

function drawFan(c: SkCanvas, r: Res, f: Frame) {
  'worklet';
  const F = UI.fan;
  card(c, F.x, F.y, F.w, F.h, F.r, f.card === 4 ? f.cardK : 0);
  const speed = [6, 3, 12][f.fan];
  fanIcon(c, F.x + F.w / 2, F.icon, (360 * speed * f.amb) / LOOP);
  const lk = smooth(f.fanAt / 0.25);
  text(c, COPY.fan[f.fan], F.x + F.w / 2, F.label + 8 * (1 - lk), r.f.label, paint(C.mute, lk), 0.5);
  c.restore();
}

function drawBelow(c: SkCanvas, r: Res) {
  'worklet';
  text(c, COPY.prefs, UI.cabin.x, UI.prefs.y, r.f.title, paint(C.label));
  const A = UI.announce;
  card(c, A.x, A.y, A.w, A.h, A.r, 0);
  text(c, COPY.announce, A.x + 44, A.y + A.h / 2, r.f.row, paint(C.ink));
  text(c, COPY.all, A.x + A.w - 44, A.y + A.h / 2, r.f.row, paint(C.blue), 1);
  c.restore();
  const B = UI.beta;
  card(c, B.x, B.y, B.w, B.h, B.r, 0);
  const cw = r.f.chip.measureText(COPY.beta).width;
  c.drawRRect(rrect(B.x + 44, B.y + 34, cw + 26, 50, 10), paint(C.chip));
  text(c, COPY.beta, B.x + 57, B.y + 59, r.f.chip, paint(C.ink));
  text(c, COPY.betaRow, B.x + 44, B.y + 128, r.f.row, paint(C.ink, 0.5));
  c.restore();
}

function tabIcon(c: SkCanvas, i: number, x: number, y: number) {
  'worklet';
  const p = stroke(C.ink, 3.2);
  if (i === 0) {
    c.drawCircle(x - 10, y - 9, 6, p);
    c.drawCircle(x + 10, y + 9, 6, p);
    const s = Skia.Path.Make();
    s.moveTo(x - 4, y - 9);
    s.lineTo(x + 10, y - 9);
    s.quadTo(x + 18, y - 9, x + 18, y - 1);
    s.quadTo(x + 18, y + 4, x + 4, y + 4);
    s.lineTo(x - 10, y + 4);
    c.drawPath(s, p);
  } else if (i === 1) {
    c.drawRRect(rrect(x - 18, y - 8, 36, 20, 6), paint(C.ink));
    const roof = Skia.Path.Make();
    roof.moveTo(x - 13, y - 8);
    roof.lineTo(x - 8, y - 18);
    roof.lineTo(x + 8, y - 18);
    roof.lineTo(x + 13, y - 8);
    roof.close();
    c.drawPath(roof, paint(C.ink));
    c.drawCircle(x - 9, y + 2, 3.2, paint('#FFFFFF'));
    c.drawCircle(x + 9, y + 2, 3.2, paint('#FFFFFF'));
    c.drawRRect(rrect(x - 16, y + 10, 7, 7, 2), paint(C.ink));
    c.drawRRect(rrect(x + 9, y + 10, 7, 7, 2), paint(C.ink));
  } else if (i === 2) {
    const b = Skia.Path.Make();
    b.addCircle(x, y - 2, 16);
    c.drawPath(b, p);
    c.drawLine(x - 7, y - 6, x + 7, y - 6, stroke(C.ink, 2.6));
    c.drawLine(x - 7, y + 1, x + 4, y + 1, stroke(C.ink, 2.6));
  } else {
    c.drawCircle(x, y - 9, 8, p);
    const b = Skia.Path.Make();
    b.moveTo(x - 16, y + 16);
    b.cubicTo(x - 14, y + 2, x + 14, y + 2, x + 16, y + 16);
    c.drawPath(b, p);
  }
}

function drawTabs(c: SkCanvas, r: Res) {
  'worklet';
  const T = UI.tab;
  const sh = paint('#8A93A8', 0.16);
  sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 18, true));
  c.drawRRect(rrect(T.x + 6, T.y + 10, T.w - 12, T.h, T.h / 2), sh);
  c.drawRRect(rrect(T.x, T.y, T.w, T.h, T.h / 2), paint('#FFFFFF', 0.92));
  // the selected tab's bubble: its radius is the bar's minus its 8 of inset
  c.drawRRect(rrect(T.xs[1] - 84, T.y + 8, 168, T.h - 16, T.h / 2 - 8), paint(C.tabSel));
  for (let i = 0; i < 4; i++) {
    tabIcon(c, i, T.xs[i], T.y + 38);
    text(c, COPY.tabs[i], T.xs[i], T.y + 76, r.f.tab, paint(C.ink), 0.5);
  }
}

export function drawScene(c: SkCanvas, r: Res, g: Geo, f: Frame) {
  'worklet';
  c.save();
  c.scale(g.s, g.s);
  c.scale(S, S);
  const H = g.h / g.s / S;
  const bg = Skia.Paint();
  bg.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: 0, y: 560 }, [col(C.bgTop), col(C.bg)], null, TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(0, 0, PIN_W, H), bg);
  // a halo behind the car in the air's colour
  const halo = Skia.Paint();
  halo.setShader(Skia.Shader.MakeRadialGradient({ x: UI.car.x, y: UI.car.y + 20 }, 300, [airColor(f.heat, 0.14), airColor(f.heat, 0)], null, TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(0, 60, PIN_W, 520), halo);
  drawPill(c, r, f);
  drawCar(c, r, f);
  text(c, COPY.cabin, UI.cabin.x, UI.cabin.y, r.f.title, paint(C.label));
  drawTemp(c, r, f);
  drawLegroom(c, r, f);
  drawFan(c, r, f);
  drawBelow(c, r);
  drawTabs(c, r);
  c.restore();
}
