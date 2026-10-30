// Cashback Pick Five — drawing, every frame, in the pin's pixels (see cpf-theme.ts).

import { BlurStyle, ClipOp, PaintStyle, type SkCanvas, type SkFont, type SkTypeface, Skia, StrokeCap, StrokeJoin, TileMode } from '@shopify/react-native-skia';

import { clamp01, easeInOut, easeOut, FLY, type Frame, LOOP, mix, NEVER, smooth, spring } from './cpf-motion';
import { C, CATS, COPY, MAX_PICKS, PIN_W, S, UI } from './cpf-theme';

export type Faces = { regular: SkTypeface; medium: SkTypeface; semi: SkTypeface; bold: SkTypeface; heavy: SkTypeface };
export type Res = { f: { brand: SkFont; title: SkFont; row: SkFont; sub: SkFont; banner: SkFont; chip: SkFont; section: SkFont; button: SkFont; connect: SkFont; fine: SkFont; glyph: SkFont } };

export function makeRes(f: Faces): Res {
  return {
    f: {
      brand: Skia.Font(f.heavy, 33),
      title: Skia.Font(f.bold, 44),
      row: Skia.Font(f.regular, 29),
      sub: Skia.Font(f.regular, 21),
      banner: Skia.Font(f.regular, 30),
      chip: Skia.Font(f.regular, 21),
      section: Skia.Font(f.medium, 30),
      button: Skia.Font(f.medium, 32),
      connect: Skia.Font(f.regular, 25),
      fine: Skia.Font(f.regular, 17),
      glyph: Skia.Font(f.bold, 26),
    },
  };
}

export type Geo = { w: number; h: number; s: number };

const TAU = Math.PI * 2;

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
function star4(c: SkCanvas, x: number, y: number, r: number, p: ReturnType<typeof Skia.Paint>) {
  'worklet';
  const sp = Skia.Path.Make();
  sp.moveTo(x, y - r);
  sp.quadTo(x, y, x + r, y);
  sp.quadTo(x, y, x, y + r);
  sp.quadTo(x, y, x - r, y);
  sp.quadTo(x, y, x, y - r);
  sp.close();
  c.drawPath(sp, p);
}
function plusMark(c: SkCanvas, x: number, y: number, r: number) {
  'worklet';
  const g = Skia.Paint();
  g.setAntiAlias(true);
  g.setShader(Skia.Shader.MakeLinearGradient({ x: x - r, y: y - r }, { x: x + r, y: y + r }, [col('#FF7EB3'), col('#B04CFF'), col('#6A3BFF')], [0, 0.55, 1], TileMode.Clamp));
  c.drawCircle(x, y, r, g);
  const w = stroke('#FFFFFF', r * 0.26);
  c.save();
  c.translate(x, y);
  c.rotate(-18, 0, 0);
  c.drawLine(-r * 0.45, 0, r * 0.45, 0, w);
  c.drawLine(0, -r * 0.45, 0, r * 0.45, w);
  c.restore();
}

// ------------------------------------------------------------------ category icons (drawn)

export function catIcon(c: SkCanvas, r0: Res, i: number, x: number, y: number, r: number) {
  'worklet';
  const cat = CATS[i];
  c.drawCircle(x, y, r, paint(cat.color));
  const k = r / 32;
  const w = paint('#FFFFFF');
  const ws = stroke('#FFFFFF', 3.4 * k);
  c.save();
  c.translate(x, y);
  c.scale(k, k);
  if (i === 0) {
    c.drawRRect(rrect(-11, -15, 22, 30, 5), stroke('#FFFFFF', 3));
    text(c, '%', 0, 1, r0.f.glyph, paint('#7A3F8E'), 0.5);
  } else if (i === 1) {
    c.drawLine(-7, -14, -7, 14, ws);
    c.drawLine(-11, -14, -11, -4, stroke('#FFFFFF', 2.4));
    c.drawLine(-3, -14, -3, -4, stroke('#FFFFFF', 2.4));
    const kn = Skia.Path.Make();
    kn.moveTo(7, 14);
    kn.lineTo(7, -14);
    kn.quadTo(14, -8, 12, 2);
    kn.lineTo(7, 2);
    c.drawPath(kn, w);
    c.drawLine(7, 0, 7, 14, ws);
  } else if (i === 2) {
    const ap = Skia.Path.Make();
    ap.moveTo(0, -6);
    ap.cubicTo(-16, -14, -18, 8, -6, 14);
    ap.quadTo(0, 11, 6, 14);
    ap.cubicTo(18, 8, 16, -14, 0, -6);
    ap.close();
    c.drawPath(ap, w);
    c.drawLine(1, -7, 4, -15, stroke('#FFFFFF', 2.6));
  } else if (i === 3) {
    const t = Skia.Path.Make();
    t.moveTo(-6, -13);
    t.quadTo(0, -9, 6, -13);
    t.lineTo(15, -7);
    t.lineTo(11, -1);
    t.lineTo(8, -3);
    t.lineTo(8, 13);
    t.lineTo(-8, 13);
    t.lineTo(-8, -3);
    t.lineTo(-11, -1);
    t.lineTo(-15, -7);
    t.close();
    c.drawPath(t, w);
  } else if (i === 4) {
    c.save();
    const cl = Skia.Path.Make();
    cl.addCircle(0, 0, 32);
    c.clipPath(cl, ClipOp.Intersect, true);
    const tri = Skia.Path.Make();
    tri.moveTo(-32, 32);
    tri.lineTo(32, 32);
    tri.lineTo(-32, -2);
    tri.close();
    c.drawPath(tri, paint('#16161A'));
    for (let q = 0; q < 3; q++) c.drawRect(Skia.XYWHRect(-26 + q * 12, 18, 6, 6), paint('#FFD60A'));
    c.restore();
  } else if (i === 5) {
    c.drawRRect(rrect(-14, -6, 28, 18, 3), paint('#FF7A1A'));
    const cl = Skia.Path.Make();
    cl.moveTo(-14, -9);
    cl.lineTo(14, -15);
    cl.lineTo(15, -10);
    cl.lineTo(-13, -4);
    cl.close();
    c.drawPath(cl, paint('#FF7A1A'));
    c.drawLine(-6, -10, -2, -6, stroke('#16161A', 2));
    c.drawLine(3, -12, 7, -8, stroke('#16161A', 2));
  } else if (i === 6) {
    c.drawRRect(rrect(-4.5, -14, 9, 28, 2), w);
    c.drawRRect(rrect(-14, -4.5, 28, 9, 2), w);
  } else if (i === 7) {
    c.drawRRect(rrect(-6, -2, 12, 16, 2), w);
    const lp = Skia.Path.Make();
    lp.moveTo(-5, -2);
    lp.lineTo(-5, -10);
    lp.lineTo(5, -16);
    lp.lineTo(5, -2);
    lp.close();
    c.drawPath(lp, w);
  } else {
    const d = Skia.Path.Make();
    d.moveTo(0, -15);
    d.cubicTo(6, -6, 12, 0, 12, 6);
    d.cubicTo(12, 13, 6, 16, 0, 16);
    d.cubicTo(-6, 16, -12, 13, -12, 6);
    d.cubicTo(-12, 0, -6, -6, 0, -15);
    d.close();
    c.drawPath(d, w);
  }
  c.restore();
}

function checkbox(c: SkCanvas, x: number, y: number, on: number, at: number, now: number, nope: number) {
  'worklet';
  const pop = at > NEVER ? Math.sin(Math.PI * clamp01((now - at) / 0.3)) * (1 - clamp01((now - at) / 0.3)) : 0;
  const s = 1 + 0.18 * pop;
  const shake = nope > 0 ? 7 * Math.sin(nope * 40) * Math.exp(-nope * 6) : 0;
  c.save();
  c.translate(x + shake, y);
  c.scale(s, s);
  const bp = Skia.Paint();
  bp.setAntiAlias(true);
  bp.setColor(mixCol(C.box, C.boxOn, on));
  c.drawRRect(rrect(-24, -24, 48, 48, 10), bp);
  if (nope > 0 && nope < 0.5) c.drawRRect(rrect(-24, -24, 48, 48, 10), stroke('#FF5A5A', 3, 1 - nope * 2));
  if (on > 0.02) {
    const k = clamp01(on * 1.2);
    const a = { x: -9, y: 0 };
    const b = { x: -2, y: 8 };
    const e = { x: 10, y: -8 };
    const l1 = Math.hypot(b.x - a.x, b.y - a.y);
    const l2 = Math.hypot(e.x - b.x, e.y - b.y);
    const d = k * (l1 + l2);
    const p = Skia.Path.Make();
    p.moveTo(a.x, a.y);
    if (d <= l1) p.lineTo(mix(a.x, b.x, d / l1), mix(a.y, b.y, d / l1));
    else {
      p.lineTo(b.x, b.y);
      p.lineTo(mix(b.x, e.x, (d - l1) / l2), mix(b.y, e.y, (d - l1) / l2));
    }
    c.drawPath(p, stroke('#FFFFFF', 3.6));
  }
  c.restore();
}

// ------------------------------------------------------------------ layout helpers

/** A row's centre on screen at a scroll. */
export function rowY(i: number, scroll: number) {
  'worklet';
  return UI.list.top + i * UI.list.pitch - scroll;
}
export function panelK(f: Frame) {
  'worklet';
  const s = f.s;
  if (s.panelAt <= NEVER) return 0;
  const k = spring(f.now - s.panelAt, 0.5, 0.86);
  return s.panelUp ? k : 1 - k;
}
export function slotPos(i: number, k: number) {
  'worklet';
  const B = UI.panel.button;
  const by = mix(UI.disabled.y + UI.disabled.h / 2, B.y + B.h / 2, k);
  return { x: B.x + B.w - 48 - (MAX_PICKS - 1 - i) * 52, y: by };
}

export function drawScene(c: SkCanvas, r: Res, g: Geo, f: Frame) {
  'worklet';
  const s = f.s;
  const now = f.now;
  c.save();
  c.scale(g.s, g.s);
  c.scale(S, S);
  const H = g.h / g.s / S;
  c.drawColor(col(C.bg));
  const sc = f.scroll;
  // ---- the page (scrolls under the bar)
  c.save();
  c.clipRect(Skia.XYWHRect(0, 222, PIN_W, H), ClipOp.Intersect, true);
  c.translate(0, -sc);
  for (let l = 0; l < 2; l++) text(c, COPY.title[l], UI.title.x, UI.title.y[l], r.f.title, paint(C.ink));
  // the banner, with stars that twinkle (and a burst now and then)
  const Bn = UI.banner;
  const bp = Skia.Paint();
  bp.setAntiAlias(true);
  bp.setShader(Skia.Shader.MakeLinearGradient({ x: Bn.x, y: 0 }, { x: Bn.x + Bn.w, y: 0 }, [col(C.bannerA), col(C.bannerB)], null, TileMode.Clamp));
  c.drawRRect(rrect(Bn.x, Bn.y, Bn.w, Bn.h, 30), bp);
  plusMark(c, 91, Bn.y + 67, 33);
  const bw = text(c, COPY.banner, 150, Bn.y + 50, r.f.banner, paint(C.ink));
  c.drawCircle(150 + bw + 18, Bn.y + 50, 10, stroke(C.grey, 2));
  const cw = r.f.chip.measureText(COPY.bannerSub).width;
  c.drawRRect(rrect(146, Bn.y + 70, cw + 16, 32, 10), paint(C.chip));
  text(c, COPY.bannerSub, 154, Bn.y + 86, r.f.chip, paint('#5C5470'));
  // the banner's box sits on the list's box column (x 630 … 678)
  c.drawRRect(rrect(630, Bn.y + 43, 48, 48, 10), paint('#E7E2F6'));
  const tw = (TAU * f.amb) / LOOP;
  const stars = [
    [520, Bn.y + 24, 0],
    [600, Bn.y + 104, 1.7],
    [460, Bn.y + 112, 3.1],
    [640, Bn.y + 22, 4.4],
    [570, Bn.y + 58, 5.6],
  ];
  for (let i = 0; i < stars.length; i++) {
    const tk = Math.max(0, Math.sin(tw * 2 + stars[i][2]));
    const burst = f.sparkle >= 0 ? Math.sin(Math.PI * clamp01((f.sparkle - i * 0.08) / 0.7)) : 0;
    const a = 0.35 * tk * tk + burst;
    if (a > 0.02) star4(c, stars[i][0], stars[i][1], 6 + 8 * burst + 2 * tk, paint(C.star, Math.min(1, a)));
  }
  // section
  const sw = text(c, COPY.section, 44, UI.section.y, r.f.section, paint(C.ink));
  c.drawRRect(rrect(44 + sw + 14, UI.section.y - 17, 34, 34, 8), paint(C.pay));
  text(c, 'O', 44 + sw + 31, UI.section.y, r.f.glyph, paint('#FFFFFF'), 0.5);
  text(c, COPY.pay, 44 + sw + 58, UI.section.y, r.f.section, paint(C.ink));
  // the list
  const L = UI.list;
  c.drawRRect(rrect(L.x, L.y, L.w, CATS.length * L.pitch + 40, 30), paint(C.card));
  for (let i = 0; i < CATS.length; i++) {
    const cy = L.top + i * L.pitch;
    const cat = CATS[i];
    const ty = cat.sub ? cy - 12 : cy;
    catIcon(c, r, i, L.iconX, cy, L.iconR);
    const pw = text(c, `${cat.pct} ${cat.name}`, L.textX, ty, r.f.row, paint(C.ink));
    c.drawCircle(L.textX + pw + 18, ty, 10, stroke('#B8BCC8', 2));
    text(c, '?', L.textX + pw + 18, ty, r.f.sub, paint('#B8BCC8'), 0.5);
    if (cat.sub) text(c, cat.sub, L.textX, cy + 20, r.f.sub, paint(C.grey));
    const on = s.checked[i] ? smooth((now - s.checkAt[i]) / 0.18) : 1 - smooth((now - s.checkAt[i]) / 0.18);
    const nope = s.nopeRow === i ? now - s.nopeAt : -1;
    checkbox(c, L.boxX, cy, s.checkAt[i] <= NEVER ? (s.checked[i] ? 1 : 0) : on, s.checkAt[i], now, nope);
  }
  c.restore();
  // ---- the bar
  const back = stroke(C.ink, 3.6);
  c.drawLine(UI.back.x - 14, UI.back.y, UI.back.x + 14, UI.back.y, back);
  c.drawLine(UI.back.x - 14, UI.back.y, UI.back.x - 4, UI.back.y - 10, back);
  c.drawLine(UI.back.x - 14, UI.back.y, UI.back.x - 4, UI.back.y + 10, back);
  const brandW = r.f.brand.measureText(COPY.brand).width;
  plusMark(c, UI.logo.x - brandW / 2 - 8, UI.logo.y, 20);
  text(c, COPY.brand, UI.logo.x - brandW / 2 + 18, UI.logo.y, r.f.brand, paint(C.ink));
  // ---- the bottom: a disabled button, or the panel with the tray in its button
  const k = panelK(f);
  const D = UI.disabled;
  const P = UI.panel;
  const B = P.button;
  if (k < 0.999) {
    c.drawRect(Skia.XYWHRect(0, D.y - 40, PIN_W, H), paint(C.bg, 1 - k));
  }
  if (k > 0.001) {
    const top = mix(H + 40, P.y, k);
    const sh = paint('#2A2F45', 0.12);
    sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 18, true));
    c.drawRRect(rrect(0, top, PIN_W, H - top + 60, 36), sh);
    c.drawRRect(rrect(0, top, PIN_W, H - top + 60, 36), paint(C.panel));
    const ca = smooth((k - 0.4) / 0.6);
    // the bolt, the line and the switch
    const bolt = Skia.Path.Make();
    const bx = 64;
    const by = P.toggle + (1 - k) * 60;
    bolt.moveTo(bx + 4, by - 18);
    bolt.lineTo(bx - 8, by + 3);
    bolt.lineTo(bx, by + 3);
    bolt.lineTo(bx - 4, by + 18);
    bolt.lineTo(bx + 8, by - 3);
    bolt.lineTo(bx, by - 3);
    bolt.close();
    c.drawPath(bolt, paint(C.purple, ca));
    for (let l = 0; l < 2; l++) text(c, COPY.connect[l], 104, by - 14 + l * 30, r.f.connect, paint(C.ink, ca));
    c.drawRRect(rrect(612, by - 23, 82, 46, 23), paint(C.purple, ca));
    c.drawCircle(671, by, 18, paint('#FFFFFF', ca));
    for (let l = 0; l < 2; l++) text(c, COPY.fine[l], PIN_W / 2, P.fine[l] + (1 - k) * 60, r.f.fine, paint(C.fine, ca), 0.5);
  }
  // the button: grey "Choose" until something is picked, then black with the tray on its right
  const bx0 = mix(D.x, B.x, k);
  const by0 = mix(D.y, B.y, k);
  const nopeT = s.nopeAt > NEVER ? now - s.nopeAt : -1;
  const shake = nopeT >= 0 ? 11 * Math.sin(nopeT * 42) * Math.exp(-nopeT * 6) : 0;
  const full = s.picks.length === MAX_PICKS ? 1 : 0;
  c.save();
  c.translate(shake, 0);
  const btn = Skia.Paint();
  btn.setAntiAlias(true);
  btn.setColor(mixCol(C.disabled, C.button, k));
  c.drawRRect(rrect(bx0, by0, B.w, B.h, 30), btn);
  // a sheen runs across when the tray is full
  if (full === 1 && s.countAt > NEVER && now - s.countAt < 1.2) {
    const q = (now - s.countAt) / 1.2;
    c.save();
    c.clipRRect(rrect(bx0, by0, B.w, B.h, 30), ClipOp.Intersect, true);
    const gx = mix(bx0 - 120, bx0 + B.w + 120, easeInOut(q));
    const gp = Skia.Paint();
    gp.setShader(Skia.Shader.MakeLinearGradient({ x: gx - 90, y: 0 }, { x: gx + 90, y: 0 }, [col('#FFFFFF', 0), col('#B48CFF', 0.35), col('#FFFFFF', 0)], null, TileMode.Clamp));
    c.drawRect(Skia.XYWHRect(gx - 90, by0, 180, B.h), gp);
    c.restore();
  }
  const bcy = by0 + B.h / 2;
  if (k < 0.5) text(c, COPY.choose, bx0 + B.w / 2, bcy, r.f.button, paint(C.disabledText, 1 - k * 2), 0.5);
  if (k > 0.2) {
    // "Choose N of 5": N rolls
    const n = s.picks.length;
    const roll = s.countAt > NEVER ? smooth((now - s.countAt) / 0.28) : 1;
    const from = s.countFrom;
    const tp = paint('#FFFFFF', smooth((k - 0.2) / 0.5));
    const lead = 'Choose';
    const tail = `of ${MAX_PICKS}`;
    const gap = r.f.button.measureText(' ').width || r.f.button.getSize() * 0.28;
    const lw = r.f.button.measureText(lead).width + gap;
    const nw = r.f.button.measureText('0').width;
    const tw2 = r.f.button.measureText(tail).width + gap;
    const tx = bx0 + 200 - (lw + nw + tw2) / 2;
    text(c, lead, tx, bcy, r.f.button, tp);
    c.save();
    c.clipRect(Skia.XYWHRect(tx + lw - 2, bcy - 26, nw + 4, 52), ClipOp.Intersect, true);
    const dir = n > from ? 1 : -1;
    if (roll < 1 && n !== from) {
      text(c, String(Math.max(0, from)), tx + lw, bcy - dir * 40 * roll, r.f.button, tp);
      text(c, String(n), tx + lw, bcy + dir * 40 * (1 - roll), r.f.button, tp);
    } else text(c, String(Math.max(1, n)), tx + lw, bcy, r.f.button, tp);
    c.restore();
    text(c, tail, tx + lw + nw + gap, bcy, r.f.button, tp);
    // the tray: five slots, the picked icons sitting in them (sliding when one leaves)
    for (let i = 0; i < MAX_PICKS; i++) {
      const sp = slotPos(i, k);
      c.drawCircle(sp.x, sp.y, 21, stroke('#FFFFFF', 2, 0.28 * smooth((k - 0.3) / 0.7)));
    }
    for (let q = 0; q < s.picks.length; q++) {
      const row = s.picks[q];
      // still on its way in?
      let flying = false;
      for (let j = 0; j < s.flights.length; j++) if (s.flights[j].row === row && s.flights[j].dir === 1 && now - s.flights[j].at < FLY) flying = true;
      if (flying) continue;
      const slide = s.slotAt[row] > NEVER ? spring(now - s.slotAt[row], 0.4, 0.75) : 1;
      const fromP = slotPos(s.slotFrom[row], k);
      const to = slotPos(q, k);
      // landing squash
      let land = 0;
      for (let j = 0; j < s.flights.length; j++) if (s.flights[j].row === row && s.flights[j].dir === 1) land = now - s.flights[j].at - FLY;
      const sq = land >= 0 && land < 0.35 ? Math.sin(Math.PI * (land / 0.35)) * (1 - land / 0.35) : 0;
      // the slot flashes a ring as it catches the icon
      if (land >= 0 && land < 0.45) {
        const lq = land / 0.45;
        c.drawCircle(to.x, to.y, 21 + 14 * easeOut(lq), stroke('#B48CFF', 3 * (1 - lq), 0.9 * (1 - lq)));
      }
      c.save();
      c.translate(mix(fromP.x, to.x, slide), mix(fromP.y, to.y, slide));
      c.scale(1 + 0.25 * sq, 1 - 0.18 * sq);
      catIcon(c, r, row, 0, 0, 19);
      c.restore();
    }
  }
  c.restore();
  // ---- icons in flight between their rows and the tray
  for (let j = 0; j < s.flights.length; j++) {
    const fl = s.flights[j];
    const p = (now - fl.at) / FLY;
    if (p < 0 || p >= 1) continue;
    const e = easeInOut(p);
    const rowP = { x: UI.list.iconX, y: rowY(fl.row, sc) };
    const slot = slotPos(fl.dir === 1 ? fl.to : fl.from, k);
    const a = fl.dir === 1 ? rowP : slot;
    const b = fl.dir === 1 ? slot : rowP;
    const cx = (a.x + b.x) / 2;
    const cy = Math.min(a.y, b.y) - 240;
    const x = (1 - e) * (1 - e) * a.x + 2 * (1 - e) * e * cx + e * e * b.x;
    const y = (1 - e) * (1 - e) * a.y + 2 * (1 - e) * e * cy + e * e * b.y;
    // a fading trail of the icon's own colour behind it
    for (let g = 3; g >= 1; g--) {
      const eg = easeInOut(Math.max(0, p - g * 0.055));
      const gx = (1 - eg) * (1 - eg) * a.x + 2 * (1 - eg) * eg * cx + eg * eg * b.x;
      const gy = (1 - eg) * (1 - eg) * a.y + 2 * (1 - eg) * eg * cy + eg * eg * b.y;
      const gr = (fl.dir === 1 ? mix(32, 19, eg) : mix(19, 32, eg)) * (1 - g * 0.12);
      c.drawCircle(gx, gy, gr, paint(CATS[fl.row].color, 0.16 * (4 - g) * Math.sin(Math.PI * p)));
    }
    const rr = fl.dir === 1 ? mix(32, 19, e) : mix(19, 32, e);
    const lift = Math.sin(Math.PI * p);
    c.save();
    c.translate(x, y);
    c.rotate(fl.dir * 360 * easeOut(p) * 0.5, 0, 0);
    c.scale(1 + 0.25 * lift, 1 + 0.25 * lift);
    const shp = paint('#2A2F45', 0.18 * lift);
    shp.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 10, true));
    c.drawCircle(0, 14 * lift, rr, shp);
    catIcon(c, r, fl.row, 0, 0, rr);
    c.restore();
  }
  c.restore();
}
