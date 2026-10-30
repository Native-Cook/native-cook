// Lucky Task Hub — drawing, every frame, in the pin's pixels (see ltk-theme.ts).

import { ClipOp, type SkCanvas, type SkFont, type SkImage, type SkTypeface, PaintStyle, Skia, StrokeCap, StrokeJoin, TileMode } from '@shopify/react-native-skia';

import { BILL_GAP, BILL_FLIGHT, BILLS, easeOut, type Frame, LOOP, mix, smooth } from './ltk-motion';
import { BALANCE, C, COPY, DY, FLIPS, fillAt, S, STEPS, UI } from './ltk-theme';

export type Faces = { black: SkTypeface; bold: SkTypeface; semi: SkTypeface; medium: SkTypeface };
export type Res = {
  atlas: SkImage;
  f: {
    header: SkFont;
    title: SkFont;
    go: SkFont;
    small: SkFont;
    timer: SkFont;
    label: SkFont;
    h2: SkFont;
    count: SkFont;
    countSmall: SkFont;
    body: SkFont;
    reward: SkFont;
    balance: SkFont;
    dollar: SkFont;
  };
};

export function makeRes(f: Faces, atlas: SkImage): Res {
  return {
    atlas,
    f: {
      header: Skia.Font(f.black, 46),
      title: Skia.Font(f.black, 40),
      go: Skia.Font(f.black, 27),
      small: Skia.Font(f.semi, 20),
      timer: Skia.Font(f.semi, 21),
      label: Skia.Font(f.bold, 19),
      h2: Skia.Font(f.bold, 31),
      count: Skia.Font(f.black, 62),
      countSmall: Skia.Font(f.black, 38),
      body: Skia.Font(f.medium, 23),
      reward: Skia.Font(f.bold, 27),
      balance: Skia.Font(f.bold, 29),
      dollar: Skia.Font(f.black, 30),
    },
  };
}

export type Geo = { w: number; h: number; s: number };

const TAU = Math.PI * 2;
// atlas cells (256 px): trophies, cash, cards, chest, games, bill
const CELL = 256;
const A = {
  trophy: [0, 1, 2],
  cash: 3,
  peace: 4,
  fist: 5,
  back: 6,
  chest: 7,
  games: [8, 9, 10],
  bill: 11,
};

function col(h: string, a = 1) {
  'worklet';
  const n = parseInt(h.slice(1), 16);
  return Float32Array.of(((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255, a);
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
  p.setStrokeJoin(StrokeJoin.Round);
  p.setStrokeCap(StrokeCap.Round);
  return p;
}
function rrect(x: number, y: number, w: number, h: number, r: number) {
  'worklet';
  return Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r);
}
function sprite(c: SkCanvas, r: Res, cell: number, cx: number, cy: number, size: number, alpha = 1) {
  'worklet';
  const sx = (cell % 4) * CELL;
  const sy = Math.floor(cell / 4) * CELL;
  const p = Skia.Paint();
  p.setAlphaf(alpha);
  c.drawImageRectOptions(r.atlas, Skia.XYWHRect(sx, sy, CELL, CELL), Skia.XYWHRect(cx - size / 2, cy - size / 2, size, size), 1, 1, p);
}
/** Text with its left edge (ax 0), centre (0.5) or right edge (1) at x, its x-height centred on cy. */
function text(c: SkCanvas, s: string, x: number, cy: number, font: SkFont, p: ReturnType<typeof Skia.Paint>, ax = 0) {
  'worklet';
  const w = font.measureText(s).width;
  c.drawText(s, x - w * ax, cy + font.getSize() * 0.36, p, font);
  return w;
}
/** A heavy italic title with a white outline, as in the pin. */
function title(c: SkCanvas, s: string, x: number, cy: number, font: SkFont, ink: string) {
  'worklet';
  c.save();
  c.translate(x, cy);
  c.skew(-0.16, 0);
  text(c, s, 0, 0, font, stroke('#FFFFFF', 7));
  text(c, s, 0, 0, font, paint(ink));
  c.restore();
}
function withCommas(n: number) {
  'worklet';
  const s = String(n);
  let out = '';
  for (let i = 0; i < s.length; i++) {
    out += s[i];
    const left = s.length - 1 - i;
    if (left > 0 && left % 3 === 0) out += ',';
  }
  return out;
}
/** A number whose changing digits roll like an odometer (value is a float between integers). */
function rolling(c: SkCanvas, v: number, x: number, cy: number, font: SkFont, p: ReturnType<typeof Skia.Paint>, ax: number, commas: boolean) {
  'worklet';
  const n0 = Math.floor(v + 1e-6);
  const k = Math.min(1, Math.max(0, v - n0));
  const a = commas ? withCommas(n0) : String(n0);
  const b = commas ? withCommas(n0 + 1) : String(n0 + 1);
  if (k < 0.001 || a.length !== b.length) {
    return text(c, k > 0.5 ? b : a, x, cy, font, p, ax);
  }
  const w = font.measureText(a).width;
  const x0 = x - w * ax;
  const H = font.getSize() * 1.05;
  const e = smooth(k);
  let px = x0;
  for (let i = 0; i < a.length; i++) {
    const ca = a[i];
    const cb = b[i];
    const cw = font.measureText(ca).width;
    if (ca === cb) text(c, ca, px, cy, font, p);
    else {
      c.save();
      c.clipRect(Skia.XYWHRect(px - 2, cy - H * 0.55, cw + 4, H * 1.1), ClipOp.Intersect, true);
      text(c, ca, px, cy - e * H, font, p);
      text(c, cb, px, cy + (1 - e) * H, font, p);
      c.restore();
    }
    px += cw;
  }
  return w;
}
function goButton(c: SkCanvas, r: Res, g: { x: number; y: number; w: number; h: number }, press: number, amb: number) {
  'worklet';
  c.save();
  const s = 1 - 0.04 * press;
  c.translate(g.x + g.w / 2, g.y + g.h / 2);
  c.scale(s, s);
  c.drawRRect(rrect(-g.w / 2, -g.h / 2, g.w, g.h, g.h / 2), paint(C.black));
  c.save();
  c.translate(-g.w / 2 + 14, 0);
  c.skew(-0.16, 0);
  text(c, COPY.go, 0, -1, r.f.go, paint(C.white));
  c.restore();
  // the little ramp arrow, nudging every 3 s
  const nudge = 4 * Math.max(0, Math.sin((TAU * 3 * amb) / LOOP)) ** 6;
  const ap = Skia.Path.Make();
  const ax = g.w / 2 - 34 + nudge;
  ap.moveTo(ax, 5);
  ap.lineTo(ax + 18, 5);
  ap.lineTo(ax + 10, -4);
  ap.close();
  c.drawPath(ap, paint(C.white));
  c.restore();
}
function star4(c: SkCanvas, x: number, y: number, rr: number, p: ReturnType<typeof Skia.Paint>) {
  'worklet';
  const sp = Skia.Path.Make();
  sp.moveTo(x, y - rr);
  sp.quadTo(x, y, x + rr, y);
  sp.quadTo(x, y, x, y + rr);
  sp.quadTo(x, y, x - rr, y);
  sp.quadTo(x, y, x, y - rr);
  sp.close();
  c.drawPath(sp, p);
}

// ------------------------------------------------------------------ cards

function drawRanking(c: SkCanvas, r: Res, f: Frame) {
  'worklet';
  const R = UI.ranking;
  // the red card behind, tilted
  c.save();
  c.translate(R.back.x + R.back.w / 2, R.back.y + R.back.h / 2);
  c.rotate(R.back.rot, 0, 0);
  const bp = Skia.Paint();
  bp.setAntiAlias(true);
  bp.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: -R.back.h / 2 }, { x: 0, y: R.back.h / 2 }, [col(C.orange), col(C.redCard)], null, TileMode.Clamp));
  c.drawRRect(rrect(-R.back.w / 2, -R.back.h / 2, R.back.w, R.back.h, R.back.r), bp);
  c.restore();
  const k = R.card;
  const yp = Skia.Paint();
  yp.setAntiAlias(true);
  yp.setShader(Skia.Shader.MakeLinearGradient({ x: k.x, y: k.y }, { x: k.x, y: k.y + k.h }, [col('#FFE13A'), col(C.yellow)], null, TileMode.Clamp));
  c.drawRRect(rrect(k.x, k.y, k.w, k.h, k.r), yp);
  // its faint square rings, as in the pin
  c.save();
  c.clipRRect(rrect(k.x, k.y, k.w, k.h, k.r), ClipOp.Intersect, true);
  for (let i = 1; i <= 3; i++) {
    const d = 70 * i;
    c.save();
    c.translate(k.x + k.w * 0.55, k.y + k.h * 0.55);
    c.rotate(12, 0, 0);
    c.drawRRect(rrect(-d, -d, d * 2, d * 2, 22), stroke('#FFEA70', 14, 0.7));
    c.restore();
  }
  c.restore();
  title(c, COPY.ranking, R.title.x, R.title.y, r.f.title, C.ink2);
  // timer pill
  const tm = R.timer;
  c.drawRRect(rrect(tm.x, tm.y, tm.w, tm.h, tm.h / 2), paint('#FFF6D2'));
  c.drawCircle(tm.x + 17, tm.y + tm.h / 2, 8, stroke(C.ink, 2.2));
  c.drawLine(tm.x + 17, tm.y + tm.h / 2, tm.x + 17, tm.y + tm.h / 2 - 5, stroke(C.ink, 2));
  c.drawLine(tm.x + 17, tm.y + tm.h / 2, tm.x + 21, tm.y + tm.h / 2 + 2, stroke(C.ink, 2));
  text(c, COPY.timer, tm.x + 32, tm.y + tm.h / 2, r.f.timer, paint(C.ink));
  // the panel
  const pn = R.panel;
  const pp = Skia.Paint();
  pp.setAntiAlias(true);
  pp.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: pn.y }, { x: 0, y: pn.y + pn.h }, [col('#FFFBE6'), col('#FFEFA0')], null, TileMode.Clamp));
  c.drawRRect(rrect(pn.x, pn.y, pn.w, pn.h, pn.r), pp);
  c.drawRRect(rrect(pn.x, pn.y, pn.w, pn.h, pn.r), stroke('#FFFFFF', 5));
  c.save();
  c.clipRRect(rrect(pn.x, pn.y, pn.w, pn.h, pn.r), ClipOp.Intersect, true);
  // podium
  const pd = R.podium;
  c.drawOval(Skia.XYWHRect(pd.x - pd.rx, pd.y - pd.ry + 10, pd.rx * 2, pd.ry * 2), paint('#F4C21C'));
  c.drawOval(Skia.XYWHRect(pd.x - pd.rx, pd.y - pd.ry, pd.rx * 2, pd.ry * 2), paint(C.podium));
  c.drawOval(Skia.XYWHRect(pd.x - pd.rx * 0.8, pd.y - pd.ry * 0.7, pd.rx * 1.6, pd.ry * 1.4), stroke('#FFF2A6', 3, 0.8));
  c.restore();
  // the trophy: a hop with a squash on take-off and landing, the tier swapping at the top
  const tr = R.trophy;
  let ty = 0;
  let sx = 1;
  let sy = 1;
  let tilt = 0;
  if (f.hop >= 0) {
    const h = f.hop;
    const air = Math.max(0, Math.min(1, (h - 0.08) / 0.62));
    ty = -58 * Math.sin(Math.PI * air);
    const crouch = Math.max(0, Math.sin(Math.PI * Math.min(1, h / 0.12))) * (h < 0.12 ? 1 : 0);
    const land = h > 0.7 ? Math.exp(-(h - 0.7) * 7) * Math.sin((h - 0.7) * 22) : 0;
    sy = 1 - 0.12 * crouch - 0.1 * land + 0.06 * Math.sin(Math.PI * air);
    sx = 1 + 0.1 * crouch + 0.08 * land - 0.03 * Math.sin(Math.PI * air);
    tilt = 10 * Math.sin(TAU * air) * (1 - air);
  }
  // shadow on the podium
  const shK = 1 + ty / 120;
  c.drawOval(Skia.XYWHRect(pd.x - 44 * shK, pd.y - 8 * shK, 88 * shK, 16 * shK), paint('#C99A00', 0.35 * shK));
  c.save();
  c.translate(tr.x, pd.y + 2 + ty);
  c.rotate(tilt, 0, 0);
  c.scale(sx, sy);
  const base = -tr.size * 0.47;
  if (f.tierK < 1) sprite(c, r, A.trophy[f.tierFrom], 0, base, tr.size, 1);
  if (f.tierK > 0) {
    // the new tier arrives behind a flash
    sprite(c, r, A.trophy[f.tierTo], 0, base, tr.size, f.tierK);
  }
  c.restore();
  // sparkles burst from the trophy at the top of the hop
  if (f.hop >= 0.3 && f.hop < 1.2) {
    const q = (f.hop - 0.3) / 0.9;
    for (let i = 0; i < 8; i++) {
      const a = (i / 8) * TAU + 0.4;
      const d = 30 + 90 * easeOut(q);
      const sp = paint(i % 2 === 0 ? '#FFFFFF' : '#FFF07A', 1 - q);
      star4(c, tr.x + Math.cos(a) * d, pd.y - 80 - 58 + 40 * q + Math.sin(a) * d * 0.8, 9 * (1 - q * 0.6), sp);
    }
  }
  // the tier's label, swapped with a crossfade
  const lb = R.label;
  c.drawRRect(rrect(lb.x - lb.w / 2, lb.y - lb.h / 2, lb.w, lb.h, lb.h / 2), paint('#FFFFFF'));
  c.drawRRect(rrect(lb.x - lb.w / 2, lb.y - lb.h / 2, lb.w, lb.h, lb.h / 2), stroke('#F4C21C', 3));
  for (let i = 0; i < 2; i++) {
    const tier = i === 0 ? f.tierFrom : f.tierTo;
    const same = f.tierTo === f.tierFrom;
    const a = i === 0 ? (same ? 1 : 1 - f.tierK) : f.tierK;
    if (a < 0.01 || (i === 1 && same)) continue;
    c.save();
    c.translate(lb.x, lb.y + (same ? 0 : i === 0 ? -8 * f.tierK : 8 * (1 - f.tierK)));
    c.skew(-0.16, 0);
    text(c, COPY.tiers[tier], 0, 0, r.f.label, paint(C.ink, a), 0.5);
    c.restore();
  }
  // the mascot: two big eyes with stars, a red nose and a grin; it blinks and watches the trophy
  const m = R.mascot;
  const blink = Math.max(0, 1 - Math.abs(f.amb - 1.9) / 0.08) + Math.max(0, 1 - Math.abs(f.amb - 6.2) / 0.08);
  const look = f.hop >= 0 ? -Math.sin(Math.PI * Math.min(1, f.hop / 0.8)) * 5 : 0;
  for (let e = 0; e < 2; e++) {
    const ex = m.x - 16 + e * 32;
    const ey = m.y - 18;
    c.save();
    c.translate(ex, ey);
    c.rotate(e === 0 ? -14 : 12, 0, 0);
    c.scale(1, Math.max(0.08, 1 - blink));
    c.drawOval(Skia.XYWHRect(-17, -21, 34, 42), paint(C.black));
    star4(c, 2, -4 + look, 11, paint('#FFE84A'));
    c.restore();
  }
  c.drawCircle(m.x, m.y + 4, 11, paint('#FF3B2F'));
  c.drawCircle(m.x - 3, m.y, 3.5, paint('#FFFFFF', 0.8));
  const mouth = Skia.Path.Make();
  mouth.moveTo(m.x - 48, m.y + 22);
  mouth.quadTo(m.x - 26, m.y + 40, m.x - 4, m.y + 26);
  c.drawPath(mouth, stroke(C.black, 4));
  // the up arrow: pops on a promotion, otherwise breathes
  const ar = R.arrow;
  const pop = f.hop >= 0 ? Math.exp(-Math.max(0, f.hop - 0.4) * 5) * Math.sin(Math.min(Math.PI, Math.max(0, f.hop - 0.4) * 9)) : 0;
  c.save();
  c.translate(ar.x, ar.y - 10 * pop);
  c.scale(1 + 0.2 * pop, 1 + 0.2 * pop);
  const arc = Skia.Path.Make();
  arc.moveTo(-26, 30);
  arc.quadTo(4, 34, 8, -24);
  c.drawPath(arc, stroke(C.black, 9));
  const head = Skia.Path.Make();
  head.moveTo(8, -46);
  head.lineTo(26, -18);
  head.lineTo(-10, -20);
  head.close();
  c.drawPath(head, paint(C.black));
  c.restore();
  goButton(c, r, R.go, f.press === 0 ? f.pressK : 0, f.amb);
}

function drawFlip(c: SkCanvas, r: Res, f: Frame) {
  'worklet';
  const F = UI.flip;
  const k = F.card;
  const pp = Skia.Paint();
  pp.setAntiAlias(true);
  pp.setShader(Skia.Shader.MakeLinearGradient({ x: k.x, y: k.y }, { x: k.x + k.w, y: k.y + k.h }, [col('#F59BE0'), col(C.pink), col(C.pinkDeep)], [0, 0.5, 1], TileMode.Clamp));
  c.drawRRect(rrect(k.x, k.y, k.w, k.h, k.r), pp);
  c.save();
  c.clipRRect(rrect(k.x, k.y, k.w, k.h, k.r), ClipOp.Intersect, true);
  // soft swirls in the background, as in the pin
  const sw = stroke('#FFB4EC', 22, 0.35);
  const p1 = Skia.Path.Make();
  p1.moveTo(k.x + 120, k.y + 200);
  p1.cubicTo(k.x + 160, k.y + 60, k.x + 280, k.y + 150, k.x + 330, k.y + 20);
  c.drawPath(p1, sw);
  c.drawCircle(k.x + 70, k.y + 10, 46, sw);
  // the back card of the deck
  const bk = F.back;
  c.save();
  c.translate(bk.x, bk.y);
  c.rotate(bk.rot, 0, 0);
  sprite(c, r, A.back, 0, 0, bk.size * 1.2);
  c.restore();
  // the front card turns over (a full turn: its back shows half way), lifting as it turns
  const fr = F.front;
  const ang = f.flipAng;
  const turn = (ang * Math.PI) / 180;
  const lift = Math.sin(turn / 2);
  const face = ang < 90 ? A.peace + f.flipFrom : ang > 270 ? A.peace + f.flipTo : A.back;
  c.save();
  c.translate(fr.x - 6 * lift, fr.y - 16 * lift);
  c.rotate(fr.rot - 8 * lift, 0, 0);
  const sx = Math.max(0.02, Math.abs(Math.cos(turn)));
  c.scale(sx * (1 + 0.12 * lift), 1 + 0.12 * lift);
  if (lift > 0.02) c.drawRRect(rrect(-fr.size * 0.42, -fr.size * 0.4 + 14 * lift, fr.size * 0.84, fr.size * 0.86, 16), paint('#7A1F66', 0.25 * lift));
  sprite(c, r, face, 0, 0, fr.size * 1.3);
  c.restore();
  c.restore();
  title(c, COPY.flip, F.title.x, F.title.y, r.f.title, C.ink2);
  const rp = paint(C.white, 0.92);
  const w0 = text(c, 'Remaining:', F.remaining.x, F.remaining.y, r.f.small, rp) + 6;
  const w1 = rolling(c, f.remaining, F.remaining.x + w0, F.remaining.y, r.f.small, rp, 0, false);
  text(c, `/${FLIPS}`, F.remaining.x + w0 + w1, F.remaining.y, r.f.small, rp);
  goButton(c, r, F.go, f.press === 1 ? f.pressK : 0, f.amb);
  // the fire badge in the corner, flickering
  const fi = F.fire;
  c.drawCircle(fi.x, fi.y, fi.r, paint('#FFFFFF'));
  const fl = 1 + 0.07 * Math.sin((TAU * 6 * f.amb) / LOOP) + 0.04 * Math.sin((TAU * 13 * f.amb) / LOOP);
  c.save();
  c.translate(fi.x, fi.y + 9);
  c.scale(1, fl);
  const fp = Skia.Path.Make();
  fp.moveTo(0, 0);
  fp.cubicTo(-14, 0, -15, -14, -8, -22);
  fp.cubicTo(-7, -14, -2, -14, -2, -18);
  fp.cubicTo(-2, -24, 2, -28, 5, -30);
  fp.cubicTo(4, -22, 14, -18, 13, -8);
  fp.cubicTo(13, -3, 8, 0, 0, 0);
  fp.close();
  c.drawPath(fp, paint('#FF3D2E'));
  c.drawCircle(1, -7, 4, paint('#FFFFFF', 0.85));
  c.restore();
}

function drawWheel(c: SkCanvas, x: number, y: number, rad: number, ang: number, blur: number) {
  'worklet';
  c.save();
  c.translate(x, y);
  c.rotate(ang, 0, 0);
  const a = col(C.wheelA);
  const b = col(C.wheelB);
  const avg = Float32Array.of((a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2, 1);
  for (let i = 0; i < 8; i++) {
    const cc = i % 2 === 0 ? a : b;
    const p = Skia.Paint();
    p.setAntiAlias(true);
    p.setColor(Float32Array.of(mix(cc[0], avg[0], blur), mix(cc[1], avg[1], blur), mix(cc[2], avg[2], blur), 1));
    const sp = Skia.Path.Make();
    sp.moveTo(0, 0);
    sp.arcToOval(Skia.XYWHRect(-rad, -rad, rad * 2, rad * 2), i * 45, 45, false);
    sp.close();
    c.drawPath(sp, p);
  }
  for (let i = 0; i < 8; i++) {
    const t = (i * 45 * Math.PI) / 180;
    c.drawLine(0, 0, Math.cos(t) * rad, Math.sin(t) * rad, stroke('#FFFFFF', 3, 0.7 * (1 - blur)));
  }
  c.restore();
  c.drawCircle(x, y, rad, stroke('#FFFFFF', 7));
  c.drawCircle(x, y, rad + 4, stroke(C.greenDeep, 3));
  c.drawCircle(x, y, rad * 0.32, paint('#FFFFFF'));
  c.drawCircle(x, y, rad * 0.32, stroke(C.wheelB, 6));
  c.drawCircle(x, y, rad * 0.12, paint(C.wheelB));
  // the pointer at the top: it flicks back each time a slice's edge passes under it
  const phase = (((ang % 45) + 45) % 45) / 45;
  const flick = 16 * Math.pow(1 - phase, 3) * Math.min(1, blur * 3);
  c.save();
  c.rotate(-flick, x, y - rad - 8);
  const pt = Skia.Path.Make();
  pt.moveTo(x - 9, y - rad - 8);
  pt.lineTo(x + 9, y - rad - 8);
  pt.lineTo(x, y - rad + 10);
  pt.close();
  c.drawPath(pt, paint('#FF3B2F'));
  c.drawPath(pt, stroke(C.black, 2.5));
  c.restore();
}

function drawSpin(c: SkCanvas, r: Res, f: Frame) {
  'worklet';
  const P = UI.spin;
  const k = P.card;
  const gp = Skia.Paint();
  gp.setAntiAlias(true);
  gp.setShader(Skia.Shader.MakeLinearGradient({ x: k.x, y: k.y }, { x: k.x, y: k.y + k.h }, [col('#8EE33A'), col(C.green), col(C.greenDeep)], [0, 0.6, 1], TileMode.Clamp));
  c.drawRRect(rrect(k.x, k.y, k.w, k.h, k.r), gp);
  c.save();
  c.clipRRect(rrect(k.x, k.y, k.w, k.h, k.r), ClipOp.Intersect, true);
  c.drawCircle(k.x + 60, k.y + k.h + 10, 90, stroke('#A6F05A', 20, 0.4));
  const wh = P.wheel;
  drawWheel(c, wh.x, wh.y, wh.r, f.wheel, Math.min(1, f.wheelSpeed * 1.3));
  // the flash when it lands
  if (f.pop > 0.01) c.drawRRect(rrect(k.x, k.y, k.w, k.h, k.r), paint('#FFFFFF', 0.35 * f.pop));
  c.restore();
  title(c, COPY.spin, P.title.x, P.title.y, r.f.title, C.ink2);
  const rp = paint(C.white, 0.92);
  text(c, 'Remaining: 0/1', P.remaining.x, P.remaining.y, r.f.small, rp);
  goButton(c, r, P.go, f.press === 2 ? f.pressK : 0, f.amb);
  // the $ $ eyes above the wheel: they widen while it spins and pop when it lands
  const ey = P.eyes;
  const wide = 1 + 0.25 * f.wheelSpeed + 0.25 * f.pop;
  for (let e = 0; e < 2; e++) {
    const ex = ey.x - 16 + e * 32;
    c.save();
    c.translate(ex, ey.y);
    c.scale(wide, wide);
    c.drawOval(Skia.XYWHRect(-15, -19, 30, 38), paint('#FFFFFF'));
    c.drawOval(Skia.XYWHRect(-15, -19, 30, 38), stroke(C.black, 3.5));
    c.save();
    c.rotate(e === 0 ? -8 : 8, 0, 0);
    // the $ pupils are dragged round with the wheel while it spins
    text(c, '$', 4 * f.wheelSpeed, 0, r.f.dollar, paint(C.black), 0.5);
    c.restore();
    c.restore();
  }
  c.drawCircle(ey.x, ey.y + 18, 9, paint('#FF3B2F'));
  c.drawCircle(ey.x - 2.5, ey.y + 15, 3, paint('#FFFFFF', 0.8));
}

function drawProgress(c: SkCanvas, r: Res, f: Frame) {
  'worklet';
  const N = UI.newbie;
  text(c, COPY.newbie, N.titleX, N.titleY, r.f.h2, paint(C.ink));
  const tm = N.timer;
  c.drawRRect(rrect(tm.x, tm.y, tm.w, tm.h, tm.h / 2), paint(C.timerBg));
  const bolt = Skia.Path.Make();
  const bx = tm.x + 6;
  const by = tm.y - 4;
  bolt.moveTo(bx + 16, by);
  bolt.lineTo(bx + 4, by + 22);
  bolt.lineTo(bx + 14, by + 22);
  bolt.lineTo(bx + 8, by + 42);
  bolt.lineTo(bx + 26, by + 16);
  bolt.lineTo(bx + 16, by + 16);
  bolt.lineTo(bx + 22, by);
  bolt.close();
  c.drawPath(bolt, paint(C.bolt));
  text(c, COPY.newbieTimer, tm.x + tm.w - 12, tm.y + tm.h / 2, r.f.label, paint(C.ink), 1);
  const P = UI.progress;
  const k = P.card;
  c.drawRRect(rrect(k.x, k.y + 6, k.w, k.h, k.r), paint(C.cardEdge));
  c.drawRRect(rrect(k.x, k.y, k.w, k.h, k.r), paint(C.cardBg));
  c.drawRRect(rrect(k.x, k.y, k.w, k.h, k.r), stroke(C.cardEdge, 3));
  // 3/14, its first number rolling
  c.save();
  c.translate(P.count.x, P.count.y);
  c.skew(-0.14, 0);
  const w0 = rolling(c, f.prog, 0, 0, r.f.count, paint(C.ink), 0, false);
  text(c, `/${STEPS}`, w0 + 2, 6, r.f.countSmall, paint(C.ink));
  c.restore();
  // the bar: a track, a striped fill that marches, and a star riding its end
  const B = P.bar;
  c.drawRRect(rrect(B.x, B.y, B.w, B.h, B.h / 2), paint(C.line));
  const fw = Math.max(B.h, fillAt(f.prog) * B.w);
  c.save();
  c.clipRRect(rrect(B.x, B.y, fw, B.h, B.h / 2), ClipOp.Intersect, true);
  c.drawRect(Skia.XYWHRect(B.x, B.y, fw, B.h), paint('#FFC21F'));
  const march = ((f.amb / LOOP) * 12 * 36) % 36;
  for (let i = -2; i < fw / 18 + 2; i++) {
    const x = B.x + i * 36 + march;
    const sp = Skia.Path.Make();
    sp.moveTo(x, B.y + B.h);
    sp.lineTo(x + 14, B.y);
    sp.lineTo(x + 28, B.y);
    sp.lineTo(x + 14, B.y + B.h);
    sp.close();
    c.drawPath(sp, paint('#FF4A2E'));
  }
  c.restore();
  const sx = B.x + fw - 4;
  const spark = 1 + 0.12 * Math.sin((TAU * 4 * f.amb) / LOOP);
  star4(c, sx, B.y + B.h / 2, 21 * spark, paint('#FF3B2F'));
  star4(c, sx, B.y + B.h / 2, 21 * spark, stroke('#FFFFFF', 2.5));
  // a step done: a ring of sparks at the star
  const stepK = f.prog - Math.floor(f.prog);
  if (stepK > 0.6 && stepK < 0.999) {
    const q = (stepK - 0.6) / 0.4;
    for (let i = 0; i < 6; i++) {
      const a = (i / 6) * TAU;
      star4(c, sx + Math.cos(a) * (14 + 30 * q), B.y + B.h / 2 + Math.sin(a) * (14 + 30 * q), 7 * (1 - q), paint('#FFB000', 1 - q));
    }
  }
  // the first reward: a coin with a gift
  const nd = P.node;
  c.drawCircle(nd.x, nd.y, nd.r + 3, paint(C.cardEdge));
  c.drawCircle(nd.x, nd.y, nd.r, paint('#FFFFFF'));
  c.drawRRect(rrect(nd.x - 12, nd.y - 6, 24, 18, 3), paint('#20B15A'));
  c.drawRRect(rrect(nd.x - 14, nd.y - 11, 28, 7, 2), paint('#20B15A'));
  c.drawRect(Skia.XYWHRect(nd.x - 2, nd.y - 11, 4, 23), paint('#FFFFFF'));
  text(c, COPY.node, nd.x, P.nodeLabel.y, r.f.label, paint(C.ink), 0.5);
  sprite(c, r, A.cash, P.goal.x, P.goal.y, 72);
  text(c, COPY.goal, P.goal.x, P.nodeLabel.y, r.f.label, paint(C.ink), 0.5);
  const ar = P.arrow;
  c.drawRRect(rrect(ar.x, ar.y, ar.w + 30, ar.h, ar.h / 2), paint(C.black));
  const ch = Skia.Path.Make();
  ch.moveTo(ar.x + 18, ar.y + ar.h / 2);
  ch.lineTo(ar.x + 34, ar.y + ar.h / 2);
  ch.moveTo(ar.x + 28, ar.y + ar.h / 2 - 7);
  ch.lineTo(ar.x + 35, ar.y + ar.h / 2);
  ch.lineTo(ar.x + 28, ar.y + ar.h / 2 + 7);
  c.drawPath(ch, stroke('#FFFFFF', 3.5));
}

function drawOffers(c: SkCanvas, r: Res) {
  'worklet';
  const O = UI.offers;
  text(c, COPY.offers, 32, O.titleY, r.f.h2, paint(C.ink));
  text(c, COPY.seeAll, 688, O.titleY, r.f.body, paint(C.ink), 1);
  for (let i = 0; i < 4; i++) {
    const x = O.cards[i];
    c.drawRRect(rrect(x, O.y + 6, O.w, O.h, 30), paint(C.cardEdge));
    c.drawRRect(rrect(x, O.y, O.w, O.h, 30), paint(C.cardBg));
    c.drawRRect(rrect(x, O.y, O.w, O.h, 30), stroke(C.cardEdge, 3));
    if (i === 3) continue;
    const g = COPY.games[i];
    const ix = x + O.w / 2;
    const iy = O.y + 32;
    c.drawRRect(rrect(ix - O.icon / 2 - 5, iy - O.icon / 2 - 5, O.icon + 10, O.icon + 10, 24), paint('#FFFFFF'));
    c.save();
    c.clipRRect(rrect(ix - O.icon / 2, iy - O.icon / 2, O.icon, O.icon, 20), ClipOp.Intersect, true);
    sprite(c, r, A.games[i], ix, iy, O.icon);
    c.restore();
    text(c, g.name, ix, O.y + 108, r.f.body, paint(C.ink2), 0.5);
    c.drawRRect(rrect(x + 14, O.y + 132, O.w - 28, 46, 23), paint(C.pill));
    sprite(c, r, A.cash, x + 42, O.y + 155, 46);
    text(c, g.reward, x + O.w / 2 + 18, O.y + 155, r.f.reward, paint(C.ink), 0.5);
  }
}

function drawInProgress(c: SkCanvas, r: Res, f: Frame) {
  'worklet';
  const P = UI.inProgress;
  const b = P.bubble;
  c.drawRRect(rrect(b.x, b.y, b.w, b.h, b.h / 2), paint('#FFFFFF'));
  c.drawRRect(rrect(b.x, b.y, b.w, b.h, b.h / 2), stroke(C.cardEdge, 3));
  const tp = paint(C.ink2);
  const w0 = text(c, COPY.progress[0], b.x + 26, b.y + b.h / 2, r.f.body, tp);
  const w1 = text(c, COPY.progress[1], b.x + 26 + w0 + 6, b.y + b.h / 2, r.f.reward, paint(C.ink));
  text(c, COPY.progress[2], b.x + 26 + w0 + w1 + 6, b.y + b.h / 2, r.f.body, tp);
  const av = P.avatar;
  c.drawCircle(av.x, av.y, av.r + 4, paint(C.cardEdge));
  c.drawCircle(av.x, av.y, av.r, paint('#FFFFFF'));
  drawWheel(c, av.x, av.y, av.r * 0.62, -f.wheel * 0.5, 0);
  // the chest, wiggling now and then
  const ch = P.chest;
  const cp = Skia.Paint();
  cp.setAntiAlias(true);
  cp.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: ch.y }, { x: 0, y: ch.y + ch.h }, [col('#FFE46A'), col('#FFC21F')], null, TileMode.Clamp));
  c.drawRRect(rrect(ch.x, ch.y, ch.w + 30, ch.h, 24), cp);
  c.drawRRect(rrect(ch.x, ch.y, ch.w + 30, ch.h, 24), stroke('#F2A500', 3));
  const wig = f.chest >= 0 ? Math.sin(f.chest * 30) * Math.exp(-f.chest * 4) * 12 : 0;
  const hopY = f.chest >= 0 ? -10 * Math.sin(Math.PI * Math.min(1, f.chest / 0.35)) : 0;
  c.save();
  c.translate(ch.x + ch.w / 2, ch.y + 34 + hopY);
  c.rotate(wig, 0, 0);
  sprite(c, r, A.chest, 0, 0, 74);
  c.restore();
  text(c, COPY.chest, ch.x + ch.w / 2, ch.y + ch.h - 18, r.f.label, paint(C.ink), 0.5);
}

function drawCasual(c: SkCanvas, r: Res) {
  'worklet';
  const P = UI.casual;
  text(c, COPY.casual, 32, P.titleY, r.f.h2, paint(C.ink));
  const w = P.row;
  c.drawRRect(rrect(w.x, w.y + 6, w.w, w.h, 30), paint(C.cardEdge));
  c.drawRRect(rrect(w.x, w.y, w.w, w.h, 30), paint(C.cardBg));
  c.save();
  c.clipRRect(rrect(w.x + 22, w.y + 20, 80, 80, 18), ClipOp.Intersect, true);
  sprite(c, r, A.games[2], w.x + 62, w.y + 60, 80);
  c.restore();
  text(c, COPY.row, w.x + 124, w.y + 46, r.f.body, paint(C.ink2));
  c.drawRRect(rrect(w.x + 124, w.y + 70, 120, 34, 17), paint(C.pill));
  sprite(c, r, A.cash, w.x + 146, w.y + 87, 34);
  text(c, COPY.rowReward, w.x + 168, w.y + 87, r.f.label, paint(C.ink));
}

/** The bills flying from the wheel to the balance, each along its own arc. */
function drawBills(c: SkCanvas, r: Res, f: Frame) {
  'worklet';
  if (f.billsT < 0) return;
  const W = UI.spin.wheel;
  const to = { x: UI.header.balance.x + 34, y: UI.header.balance.y + UI.header.balance.h / 2 };
  for (let i = BILLS - 1; i >= 0; i--) {
    const q = (f.billsT - i * BILL_GAP) / BILL_FLIGHT;
    if (q <= 0 || q >= 1) continue;
    const e = 1 - Math.pow(1 - q, 2.2);
    const cx = W.x + 120 + i * 18;
    const cy = W.y - 260 - i * 30;
    const x = (1 - e) * (1 - e) * W.x + 2 * (1 - e) * e * cx + e * e * to.x;
    const y = (1 - e) * (1 - e) * (W.y - 20) + 2 * (1 - e) * e * cy + e * e * to.y;
    const size = mix(88, 44, e) * (1 + 0.25 * Math.sin(Math.PI * Math.min(1, q * 3)));
    // a short trail of light behind each bill
    for (let k = 1; k <= 3; k++) {
      const qe = Math.max(0, e - k * 0.05);
      const tx = (1 - qe) * (1 - qe) * W.x + 2 * (1 - qe) * qe * cx + qe * qe * to.x;
      const ty = (1 - qe) * (1 - qe) * (W.y - 20) + 2 * (1 - qe) * qe * cy + qe * qe * to.y;
      c.drawCircle(tx, ty, 9 - k * 2, paint('#FFFFFF', 0.5 - k * 0.12));
    }
    c.save();
    c.translate(x, y);
    c.rotate(-25 + 340 * e * (i % 2 === 0 ? 1 : -1) * 0.35, 0, 0);
    sprite(c, r, A.bill, 0, 0, size);
    c.restore();
  }
}

function drawHeader(c: SkCanvas, r: Res, f: Frame) {
  'worklet';
  const H = UI.header;
  text(c, COPY.header, 32, H.titleY, r.f.header, paint(C.ink));
  const b = H.balance;
  const s = 1 + 0.08 * f.bump;
  c.save();
  c.translate(b.x + b.w / 2, b.y + b.h / 2);
  c.scale(s, s);
  c.drawRRect(rrect(-b.w / 2, -b.h / 2 + 4, b.w, b.h, b.h / 2), paint(C.cardEdge));
  c.drawRRect(rrect(-b.w / 2, -b.h / 2, b.w, b.h, b.h / 2), paint('#FFFFFF'));
  if (f.bump > 0.01) c.drawRRect(rrect(-b.w / 2, -b.h / 2, b.w, b.h, b.h / 2), stroke(C.cash, 4, f.bump));
  sprite(c, r, A.cash, -b.w / 2 + 34, 0, 56);
  const bp = paint(C.ink);
  if (f.balFade > 0.001 && f.balFade < 0.999) {
    // the loop's way back: the start value fades in over the won one
    const a = Skia.Paint();
    a.setAlphaf(1 - f.balFade);
    c.saveLayer(a);
    rolling(c, f.balance, b.w / 2 - 20, 0, r.f.balance, bp, 1, true);
    c.restore();
    const z = Skia.Paint();
    z.setAlphaf(f.balFade);
    c.saveLayer(z);
    text(c, withCommas(BALANCE), b.w / 2 - 20, 0, r.f.balance, bp, 1);
    c.restore();
  } else if (f.balFade >= 0.999) text(c, withCommas(BALANCE), b.w / 2 - 20, 0, r.f.balance, bp, 1);
  else rolling(c, f.balance, b.w / 2 - 20, 0, r.f.balance, bp, 1, true);
  c.restore();
}

export function drawScene(c: SkCanvas, r: Res, g: Geo, f: Frame) {
  'worklet';
  c.drawColor(col(C.page));
  c.save();
  c.scale(g.s, g.s);
  c.translate(0, DY);
  c.scale(S, S);
  const bg = Skia.Paint();
  bg.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: -DY / S }, { x: 0, y: 520 }, [col(C.bgTop), col(C.bgMid), col(C.page)], [0, 0.6, 1], TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(0, -DY / S, 720, 520 + DY / S), bg);
  drawHeader(c, r, f);
  drawRanking(c, r, f);
  drawFlip(c, r, f);
  drawSpin(c, r, f);
  drawProgress(c, r, f);
  drawOffers(c, r);
  drawInProgress(c, r, f);
  drawCasual(c, r);
  drawBills(c, r, f);
  c.restore();
}
