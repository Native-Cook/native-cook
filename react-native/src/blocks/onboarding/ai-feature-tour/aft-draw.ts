// AI Feature Tour — drawing, every frame, in the pin's pixels (see aft-theme.ts). Every icon is
// drawn in code and given its own little act.

import { BlurStyle, ClipOp, PaintStyle, type SkCanvas, type SkFont, type SkTypeface, Skia, StrokeCap, StrokeJoin, TileMode } from '@shopify/react-native-skia';

import { bump, clamp01, easeOut, flight, type Frame, mix, smooth, T } from './aft-motion';
import { C, COPY, PIN_W, S, UI } from './aft-theme';

export type Faces = { regular: SkTypeface; medium: SkTypeface; semi: SkTypeface; bold: SkTypeface };
export type Res = { f: { title: SkFont; row: SkFont; sub: SkFont; button: SkFont } };

export function makeRes(f: Faces): Res {
  return { f: { title: Skia.Font(f.bold, 42), row: Skia.Font(f.semi, 30), sub: Skia.Font(f.regular, 27), button: Skia.Font(f.semi, 34) } };
}

export type Geo = { w: number; h: number; s: number };

const TAU = Math.PI * 2;

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
  p.setStrokeCap(StrokeCap.Round);
  p.setStrokeJoin(StrokeJoin.Round);
  return p;
}
function blurred(h: string, a: number, sigma: number) {
  'worklet';
  const p = paint(h, a);
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma, true));
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
/** A white rounded tile with a soft drop shadow (the icons' common base). */
function tile(c: SkCanvas, x: number, y: number, w: number, h: number, r: number, lift = 0) {
  'worklet';
  c.drawRRect(rrect(x + 2, y + 8 + 4 * lift, w - 4, h, r), blurred(C.tileShade, 0.35 + 0.15 * lift, 9 + 4 * lift));
  c.drawRRect(rrect(x, y, w, h, r), paint(C.tile));
  c.drawRRect(rrect(x, y, w, h, r), stroke(C.tileEdge, 1.5));
}
function check(c: SkCanvas, x: number, y: number, r: number, color: string, k: number, filled: boolean) {
  'worklet';
  if (filled) c.drawCircle(x, y, r, paint(color));
  else c.drawCircle(x, y, r, stroke(color, 2.4));
  if (k <= 0) return;
  const a = { x: x - r * 0.42, y: y + r * 0.02 };
  const b = { x: x - r * 0.1, y: y + r * 0.34 };
  const e = { x: x + r * 0.46, y: y - r * 0.32 };
  const l1 = Math.hypot(b.x - a.x, b.y - a.y);
  const l2 = Math.hypot(e.x - b.x, e.y - b.y);
  const d = clamp01(k) * (l1 + l2);
  const p = Skia.Path.Make();
  p.moveTo(a.x, a.y);
  if (d <= l1) p.lineTo(mix(a.x, b.x, d / l1), mix(a.y, b.y, d / l1));
  else {
    p.lineTo(b.x, b.y);
    p.lineTo(mix(b.x, e.x, (d - l1) / l2), mix(b.y, e.y, (d - l1) / l2));
  }
  c.drawPath(p, stroke(filled ? '#FFFFFF' : color, r * 0.28));
}
function runner(c: SkCanvas, x: number, y: number, s: number, color: string) {
  'worklet';
  const p = stroke(color, 2.6 * s);
  c.drawCircle(x + 3 * s, y - 9 * s, 2.6 * s, paint(color));
  c.drawLine(x + 1 * s, y - 5 * s, x - 2 * s, y + 3 * s, p);
  c.drawLine(x - 2 * s, y + 3 * s, x - 7 * s, y + 9 * s, p);
  c.drawLine(x - 2 * s, y + 3 * s, x + 4 * s, y + 6 * s, p);
  c.drawLine(x + 4 * s, y + 6 * s, x + 4 * s, y + 11 * s, p);
  c.drawLine(x, y - 3 * s, x - 6 * s, y - 1 * s, p);
  c.drawLine(x, y - 3 * s, x + 6 * s, y, p);
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

// ------------------------------------------------------------------ the icons (each centred on 0,0)

const BLUE = [2, 4, 6, 8, 10, 12, 15, 17, 19, 22, 24, 26];

function calendar(c: SkCanvas, p: number) {
  'worklet';
  tile(c, -57, -50, 114, 108, 18, p >= 0 ? bump(p, 0, 1) : 0);
  c.save();
  c.clipRRect(rrect(-57, -50, 114, 108, 18), ClipOp.Intersect, true);
  c.drawRect(Skia.XYWHRect(-57, -50, 114, 26), paint(C.calTop));
  c.restore();
  // the binder rings
  for (const x of [-38, 38]) {
    c.drawRRect(rrect(x - 3, -60, 6, 20, 3), paint('#F3F5F6'));
    c.drawRRect(rrect(x - 3, -60, 6, 20, 3), stroke(C.calRing, 2));
  }
  // dots: a check-in hops across the blue days, each popping as it lands
  for (let k = 0; k < 28; k++) {
    const r = Math.floor(k / 7);
    const q = k % 7;
    const x = -42 + q * 14;
    const y = -10 + r * 14;
    const blue = BLUE.indexOf(k);
    let s = 1;
    if (blue >= 0 && p >= 0) s = 1 + 0.9 * bump(p, 0.08 + blue * 0.06, 0.22);
    c.drawCircle(x, y, 3.2 * s, paint(blue >= 0 ? C.dotBlue : C.dotGrey));
    if (blue >= 0 && p >= 0) {
      const ring = bump(p, 0.1 + blue * 0.06, 0.3);
      if (ring > 0.01) c.drawCircle(x, y, 3.2 + 7 * ring, stroke(C.dotBlue, 1.6, 0.6 * ring));
    }
  }
}

function trend(c: SkCanvas, p: number) {
  'worklet';
  tile(c, -57, -57, 114, 114, 20, p >= 0 ? bump(p, 0, 1) : 0);
  for (let k = 1; k < 4; k++) c.drawLine(-57 + k * 28, -55, -57 + k * 28, 55, stroke(C.grid, 1.5));
  for (let k = 1; k < 4; k++) c.drawLine(-55, -57 + k * 28, 55, -57 + k * 28, stroke(C.grid, 1.5));
  const a = { x: -40, y: -28 };
  const b = { x: 12, y: 28 };
  const e = { x: 42, y: -12 };
  // erased fast, then drawn again with the dot riding its tip; the dashed forecast follows
  let k = 1;
  let dash = 1;
  if (p >= 0) {
    k = p < 0.1 ? 1 - p / 0.1 : easeOut((p - 0.12) / 0.45);
    dash = p < 0.1 ? 1 - p / 0.1 : smooth((p - 0.6) / 0.25);
  }
  const tip = { x: mix(a.x, b.x, k), y: mix(a.y, b.y, k) };
  c.drawLine(a.x, a.y, tip.x, tip.y, stroke(C.greenLine, 7));
  c.drawCircle(a.x, a.y, 7, paint('#FFFFFF'));
  c.drawCircle(a.x, a.y, 7, stroke(C.green, 4));
  if (dash > 0.01) {
    for (let i = 0; i < 4; i++) {
      const u0 = i / 4;
      const u1 = u0 + 0.12;
      if (u0 > dash) break;
      c.drawLine(mix(b.x, e.x, u0), mix(b.y, e.y, u0), mix(b.x, e.x, Math.min(u1, dash)), mix(b.y, e.y, Math.min(u1, dash)), stroke(C.checkGrey, 4));
    }
    c.drawCircle(e.x, e.y, 7 * (p >= 0 ? 1 + 0.3 * bump(p, 0.8, 0.2) : 1), paint('#FFFFFF', dash));
    c.drawCircle(e.x, e.y, 7, stroke(C.checkGrey, 4, dash));
  }
  c.drawCircle(tip.x, tip.y, 11, paint(C.green));
  c.drawCircle(tip.x, tip.y, 11, stroke('#FFFFFF', 3));
}

function templates(c: SkCanvas, p: number) {
  'worklet';
  // three cards; the middle one (the chosen template) slides out, its check redraws, then back
  const out = p >= 0 ? bump(p, 0.05, 0.9) : 0;
  const ck = p >= 0 ? (p < 0.35 ? 1 - smooth(p / 0.15) : smooth((p - 0.45) / 0.3)) : 1;
  const rows = [
    { y: -42, a: 0.85, icon: 0, dx: -4 * out, check: 'grey' },
    { y: 42, a: 0.6, icon: 2, dx: -4 * out, check: 'ring' },
  ];
  for (const r of rows) {
    c.save();
    c.translate(r.dx, r.y + (r.y < 0 ? -6 : 6) * out);
    c.drawRRect(rrect(-56, -16, 112, 32, 10), blurred(C.tileShade, 0.25 * r.a, 5));
    c.drawRRect(rrect(-56, -16, 112, 32, 10), paint('#FFFFFF', r.a));
    c.drawRRect(rrect(-56, -16, 112, 32, 10), stroke(C.tileEdge, 1.2, r.a));
    if (r.icon === 0) {
      c.drawCircle(-38, -2, 3, paint('#9AA2AA', r.a));
      c.drawLine(-44, 4, -32, 4, stroke('#9AA2AA', 2.2, r.a));
    } else c.drawLine(-44, 2, -32, -3, stroke('#B0B6BD', 2.2, r.a));
    c.drawRRect(rrect(-20, -6, 40, 5, 2.5), paint(C.line, r.a));
    c.drawRRect(rrect(-20, 3, 28, 4, 2), paint(C.line, r.a));
    if (r.check === 'grey') check(c, 40, 0, 9, '#9AA2AA', 1, true);
    else check(c, 40, 0, 9, C.checkGrey, 1, false);
    c.restore();
  }
  c.save();
  c.translate(30 * out, 0);
  c.scale(1 + 0.06 * out, 1 + 0.06 * out);
  c.drawRRect(rrect(-66, -20, 132, 40, 12), blurred(C.tileShade, 0.45, 8));
  c.drawRRect(rrect(-66, -20, 132, 40, 12), paint('#FFFFFF'));
  c.drawRRect(rrect(-66, -20, 132, 40, 12), stroke(C.tileEdge, 1.2));
  runner(c, -46, 0, 1, '#1E1E22');
  c.drawRRect(rrect(-26, -7, 46, 6, 3), paint(C.line));
  c.drawRRect(rrect(-26, 4, 32, 5, 2.5), paint(C.line));
  check(c, 46, 0, 11, C.check, ck, true);
  c.restore();
}

function avocado(c: SkCanvas, p: number) {
  'worklet';
  // the photo behind slides out a little, as if the next one were coming
  const next = p >= 0 ? bump(p, 0.55, 0.45) : 0;
  c.save();
  c.translate(20 + 14 * next, 28 - 4 * next);
  c.rotate(4 + 6 * next, 0, 0);
  tile(c, -38, -38, 76, 76, 14);
  c.drawCircle(18, 4, 9, paint('#E9C9A8'));
  c.drawCircle(15, 1, 4, paint('#C49A70'));
  c.restore();
  c.save();
  c.translate(-12, -10);
  c.rotate(-2, 0, 0);
  tile(c, -48, -46, 96, 96, 16, p >= 0 ? bump(p, 0, 1) : 0);
  // the halved avocado
  c.save();
  c.rotate(-28, 0, 0);
  const skin = Skia.Path.Make();
  skin.addOval(Skia.XYWHRect(-26, -34, 52, 66));
  c.drawPath(skin, paint(C.avoSkin));
  c.drawOval(Skia.XYWHRect(-21, -28, 42, 56), paint(C.avoFlesh));
  const fl = Skia.Paint();
  fl.setAntiAlias(true);
  fl.setShader(Skia.Shader.MakeRadialGradient({ x: 0, y: 6 }, 24, [col(C.avoInner), col(C.avoFlesh)], null, TileMode.Clamp));
  c.drawOval(Skia.XYWHRect(-18, -24, 36, 50), fl);
  const pit = Skia.Paint();
  pit.setAntiAlias(true);
  pit.setShader(Skia.Shader.MakeRadialGradient({ x: -4, y: 2 }, 14, [col('#C9783A'), col(C.pit)], null, TileMode.Clamp));
  c.drawCircle(0, 7, 12, pit);
  c.drawCircle(-4, 3, 3.5, paint('#FFFFFF', 0.5));
  c.restore();
  // a scan line sweeps down the photo, and sparkles mark what it found
  if (p >= 0) {
    const sy = mix(-46, 50, easeOut((p - 0.08) / 0.5));
    const sa = bump(p, 0.06, 0.56);
    if (sa > 0.01) {
      c.save();
      c.clipRRect(rrect(-48, -46, 96, 96, 16), ClipOp.Intersect, true);
      const g = Skia.Paint();
      g.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: sy - 26 }, { x: 0, y: sy }, [col('#8E7BFF', 0), col('#8E7BFF', 0.28 * sa)], null, TileMode.Clamp));
      c.drawRect(Skia.XYWHRect(-48, sy - 26, 96, 26), g);
      c.drawLine(-48, sy, 48, sy, stroke('#7C68FF', 2.5, sa));
      c.restore();
    }
    for (let k = 0; k < 3; k++) {
      const s = bump(p, 0.55 + k * 0.08, 0.35);
      if (s > 0.01) star4(c, [-38, 40, 30][k], [-40, -30, 40][k], 9 * s, paint('#8E7BFF', s));
    }
  }
  c.restore();
}

function clipboard(c: SkCanvas, p: number) {
  'worklet';
  tile(c, -48, -56, 96, 118, 12, p >= 0 ? bump(p, 0, 1) : 0);
  c.drawRRect(rrect(-26, -70, 52, 22, 9), paint('#C8CBD0'));
  c.drawCircle(0, -66, 5, paint('#E8EAED'));
  // three lines, each redrawn in turn with its mark
  for (let k = 0; k < 3; k++) {
    const y = -20 + k * 30;
    let draw = 1;
    if (p >= 0) draw = p < 0.12 ? 1 - p / 0.12 : easeOut((p - (0.18 + k * 0.2)) / 0.22);
    const a = clamp01(draw * 1.5);
    if (k === 0) runner(c, -28, y, 0.75, C.runner);
    else check(c, -28, y, 7, '#B4BAC1', draw, false);
    c.drawRRect(rrect(-12, y - 6, 44 * draw, 4.5, 2), paint('#C4C9CF', a));
    c.drawRRect(rrect(-12, y + 3, 32 * draw, 4, 2), paint('#D9DDE1', a));
  }
}

function magnifier(c: SkCanvas) {
  'worklet';
  c.drawCircle(-4, -6, 26, stroke('#C9CCD3', 9));
  c.drawLine(15, 13, 34, 32, stroke('#C9CCD3', 11));
}

function drawIcon(c: SkCanvas, i: number, p: number) {
  'worklet';
  if (i === 0) calendar(c, p);
  else if (i === 1) trend(c, p);
  else if (i === 2) templates(c, p);
  else if (i === 3) avocado(c, p);
  else clipboard(c, p);
}

export function drawScene(c: SkCanvas, r: Res, g: Geo, f: Frame) {
  'worklet';
  c.save();
  c.scale(g.s, g.s);
  c.scale(S, S);
  const H = g.h / g.s / S;
  const bg = Skia.Paint();
  bg.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: 0, y: 520 }, [col(C.bgTop), col(C.bgMid), col(C.bg)], [0, 0.45, 1], TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(0, 0, PIN_W, H), bg);
  const glow = Skia.Paint();
  glow.setShader(Skia.Shader.MakeRadialGradient({ x: 360, y: 1250 }, 520, [col(C.glow, 0.9), col(C.glow, 0)], null, TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(0, 600, PIN_W, H - 600), glow);
  text(c, COPY.title, UI.title.x, UI.title.y, r.f.title, paint(C.title), 0.5);
  const R = UI.rows;
  const B = UI.button;
  const bc = { x: B.x + B.w / 2, y: B.y + B.h / 2 };
  for (let i = 0; i < COPY.rows.length; i++) {
    const ty = R.y0 + i * R.pitch;
    const iy = ty + 42;
    // the hidden sixth row fades under the button
    const fade = i === 5 ? 0.35 : 1;
    const tw = text(c, COPY.rows[i].title, R.textX, ty, r.f.row, paint(C.rowTitle, fade));
    // while its icon acts, a violet light runs along the row's title
    const act0 = i < 5 ? f.act[i] : -1;
    if (act0 >= 0) {
      const gx = R.textX - 80 + (tw + 160) * smooth(act0 / 0.8);
      const sp = Skia.Paint();
      sp.setAntiAlias(true);
      sp.setShader(Skia.Shader.MakeLinearGradient({ x: gx - 70, y: 0 }, { x: gx + 70, y: 0 }, [col('#7C68FF', 0), col('#7C68FF', 0.95), col('#7C68FF', 0)], null, TileMode.Clamp));
      text(c, COPY.rows[i].title, R.textX, ty, r.f.row, sp);
    }
    for (let l = 0; l < 2; l++) text(c, COPY.rows[i].sub[l], R.textX, ty + R.sub[l], r.f.sub, paint(C.sub, fade));
    if (i === 5) {
      c.save();
      c.translate(R.iconX + 20, iy);
      magnifier(c);
      c.restore();
      continue;
    }
    // an icon at home, or on its way into the button and back
    let x = R.iconX;
    let y = iy;
    let s = 1;
    let rot = 0;
    let a = 1;
    if (f.fly >= 0) {
      const fl = flight(i, f.fly);
      if (fl.gone) continue;
      if (fl.back > 0) {
        s = mix(0.55, 1, fl.back);
        a = clamp01(fl.back * 1.6);
      } else if (fl.out > 0) {
        const e = fl.out;
        const cx = R.iconX + 260;
        const cy = mix(iy, bc.y, 0.2) - 120;
        x = (1 - e) * (1 - e) * R.iconX + 2 * (1 - e) * e * cx + e * e * bc.x;
        y = (1 - e) * (1 - e) * iy + 2 * (1 - e) * e * cy + e * e * bc.y;
        s = mix(1, 0.25, e);
        rot = 40 * e * (i % 2 === 0 ? 1 : -1);
        a = 1 - smooth((e - 0.75) / 0.25);
      }
    }
    const act = f.act[i];
    if (act >= 0) s *= 1 + 0.05 * bump(act, 0, 1);
    c.save();
    c.translate(x, y);
    c.rotate(rot, 0, 0);
    c.scale(s, s);
    if (a < 0.999) {
      const lp = Skia.Paint();
      lp.setAlphaf(a);
      c.saveLayer(lp);
      drawIcon(c, i, act);
      c.restore();
    } else drawIcon(c, i, act);
    c.restore();
  }
  // the button, with a soft fade above it over the hidden row
  const fadeP = Skia.Paint();
  fadeP.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: B.y - 70 }, { x: 0, y: B.y + 30 }, [col(C.bg, 0), col(C.bg, 0.9)], null, TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(0, B.y - 70, PIN_W, H - B.y + 70), fadeP);
  const sp = f.fly >= 0 ? f.fly - (T.sparkle - T.press) : -1;
  const glowK = sp >= 0 ? bump(sp, 0, 0.9) : 0;
  const s = 1 - 0.04 * f.press + 0.02 * glowK;
  c.save();
  c.translate(bc.x, bc.y);
  c.scale(s, s);
  if (glowK > 0.01) c.drawRRect(rrect(-B.w / 2 - 6, -B.h / 2 - 6, B.w + 12, B.h + 12, B.h / 2 + 6), blurred(C.sparkle, 0.7 * glowK, 18));
  c.drawRRect(rrect(-B.w / 2, -B.h / 2, B.w, B.h, B.h / 2), paint(C.button));
  const tw = r.f.button.measureText(COPY.button).width;
  const nudge = 6 * glowK;
  text(c, COPY.button, -(tw + 50) / 2, 0, r.f.button, paint('#FFFFFF'));
  const ax = -(tw + 50) / 2 + tw + 18 + nudge;
  c.drawLine(ax, 0, ax + 32, 0, stroke('#FFFFFF', 3.6));
  c.drawLine(ax + 20, -12, ax + 32, 0, stroke('#FFFFFF', 3.6));
  c.drawLine(ax + 20, 12, ax + 32, 0, stroke('#FFFFFF', 3.6));
  c.restore();
  // the icons become one sparkle in the button
  if (sp >= 0 && sp < 1.1) {
    const k = easeOut(sp / 0.35) * (1 - smooth((sp - 0.55) / 0.5));
    if (k > 0.01) {
      c.save();
      c.translate(bc.x, bc.y - 2);
      c.rotate(90 * sp, 0, 0);
      star4(c, 0, 0, 46 * k, paint(C.sparkle, 0.95 * k));
      star4(c, 0, 0, 24 * k, paint('#FFFFFF', k));
      c.restore();
      for (let i = 0; i < 8; i++) {
        const ang = (i / 8) * TAU + 0.3;
        const d = 40 + 120 * easeOut(sp / 0.8);
        star4(c, bc.x + Math.cos(ang) * d * 1.6, bc.y + Math.sin(ang) * d * 0.55, 8 * (1 - smooth(sp / 0.9)), paint(i % 2 ? '#FFFFFF' : C.sparkle, 1 - smooth(sp / 0.9)));
      }
    }
  }
  c.restore();
}
