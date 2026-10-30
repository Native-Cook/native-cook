// Sun UV Buddy — drawing, every frame, in the 402 × 874 reference space (hanging from the top).

import { ClipOp, PaintStyle, type SkCanvas, type SkFont, type SkTypeface, Skia, StrokeCap, TileMode } from '@shopify/react-native-skia';

import { barValue, LOOP, mix, smooth, uvAt } from './suv-motion';
import { C, COPY, HOURS, UI, words } from './suv-theme';

export type Faces = { regular: SkTypeface; medium: SkTypeface; semi: SkTypeface; round: SkTypeface };
export type Res = { f: { title: SkFont; place: SkFont; chip: SkFont; head: SkFont; badge: SkFont; line: SkFont; bar: SkFont; hour: SkFont; learn: SkFont; zed: SkFont } };

export function makeRes(f: Faces): Res {
  return {
    f: {
      title: Skia.Font(f.semi, UI.title.size),
      place: Skia.Font(f.regular, UI.place.size),
      chip: Skia.Font(f.medium, 15),
      head: Skia.Font(f.round, UI.headline.size),
      badge: Skia.Font(f.semi, 14),
      line: Skia.Font(f.regular, UI.line.size),
      bar: Skia.Font(f.medium, 15),
      hour: Skia.Font(f.regular, 13.5),
      learn: Skia.Font(f.round, 22),
      zed: Skia.Font(f.round, 13),
    },
  };
}

export type Geo = { w: number; h: number; s: number; dy: number };
export type Live = { amb: number; hour: number; hourV: number; from: number; to: number; tau: number; size: number; squish: number; dayPress: number };

const TAU = Math.PI * 2;

function col(h: string, a = 1) {
  'worklet';
  if (h.startsWith('rgba')) {
    const n = h.slice(5, -1).split(',').map((v) => parseFloat(v));
    return Float32Array.of(n[0] / 255, n[1] / 255, n[2] / 255, n[3] * a);
  }
  const n = parseInt(h.slice(1), 16);
  return Float32Array.of(((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255, a);
}
function mixCol(a: Float32Array, b: Float32Array, k: number) {
  'worklet';
  return Float32Array.of(mix(a[0], b[0], k), mix(a[1], b[1], k), mix(a[2], b[2], k), mix(a[3], b[3], k));
}
function paint(h: string, a = 1) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(col(h, a));
  return p;
}
function textC(c: SkCanvas, s: string, x: number, cy: number, font: SkFont, p: ReturnType<typeof Skia.Paint>, ax = 0.5) {
  'worklet';
  const w = font.measureText(s).width;
  c.drawText(s, x - w * ax, cy + font.getSize() * 0.36, p, font);
}
function barX(i: number) {
  'worklet';
  return UI.bars.x0 + i * (UI.bars.w + UI.bars.gap);
}
function heightFor(v: number) {
  'worklet';
  const H = UI.bars.h;
  const i = Math.max(0, Math.min(H.length - 2, Math.floor(v)));
  return mix(H[i], H[i + 1], Math.max(0, Math.min(1, v - i)));
}
function barColor(v: number) {
  'worklet';
  const low = col(C.barLow);
  const mid = col(C.barMid);
  const high = col(C.barHigh);
  if (v < 2.5) return low;
  if (v < 3) return mixCol(low, mid, (v - 2.5) * 2);
  if (v < 5.5) return mid;
  return mixCol(mid, high, Math.min(1, (v - 5.5) * 2));
}

/** The sun: a scalloped jelly whose size, squash and face follow the UV. */
function drawSun(c: SkCanvas, r0: Res, live: Live, uv: number) {
  'worklet';
  const S = UI.sun;
  const r = S.r * live.size;
  // squash from the size spring's speed (stretching as it grows) and from a tap
  const squash = 0.06 * Math.sin(Math.PI * Math.min(1, live.squish / 0.5)) * Math.exp(-live.squish * 3) * (live.squish >= 0 ? 1 : 0);
  const breathe = 0.012 * Math.sin((TAU * 2 * live.amb) / LOOP);
  const sx = 1 + squash + breathe;
  const sy = 1 - squash - breathe * 0.6;
  // rays behind a strong sun
  const rays = smooth(uv - 3.4);
  if (rays > 0.01) {
    const rp = Skia.Paint();
    rp.setAntiAlias(true);
    rp.setStrokeCap(StrokeCap.Round);
    rp.setStrokeWidth(7);
    rp.setColor(col(C.sun, 0.55 * rays));
    const spin = (360 * live.amb) / LOOP / 3;
    for (let k = 0; k < 12; k++) {
      const a = ((k * 30 + spin) * Math.PI) / 180;
      const r0 = r * 1.12;
      const r1 = r * (1.3 + 0.06 * Math.sin((TAU * 2 * live.amb) / LOOP + k));
      c.drawLine(S.x + Math.cos(a) * r0, S.y + Math.sin(a) * r0, S.x + Math.cos(a) * r1, S.y + Math.sin(a) * r1, rp);
    }
  }
  // a warm glow
  const glow = Skia.Paint();
  glow.setShader(Skia.Shader.MakeRadialGradient({ x: S.x, y: S.y }, r * 1.6, [col('#FFE97A', 0.22 * smooth(uv / 3) + 0.4 * smooth(uv - 4)), col('#FFE97A', 0)], null, TileMode.Clamp));
  c.drawCircle(S.x, S.y, r * 1.6, glow);
  c.save();
  c.translate(S.x, S.y + r * 0.9);
  c.scale(sx, sy);
  c.translate(0, -r * 0.9);
  // the scalloped body, its bumps rippling slowly round
  const body = Skia.Path.Make();
  const n = 120;
  const ph = (TAU * live.amb) / LOOP;
  for (let k = 0; k <= n; k++) {
    const a = (k / n) * TAU;
    const rr = r * (1 + 0.05 * Math.cos(14 * a + ph) + 0.01 * Math.sin(3 * a - ph * 2));
    const x = Math.cos(a) * rr;
    const y = Math.sin(a) * rr;
    if (k === 0) body.moveTo(x, y);
    else body.lineTo(x, y);
  }
  body.close();
  const bp = Skia.Paint();
  bp.setAntiAlias(true);
  bp.setColor(col(C.sun)); // flat, as in the pin
  c.drawPath(body, bp);
  // its arm on a little deck chair, as in the pin
  const arm = paint(C.sun);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(r * 0.42, r * 0.62, r * 0.78, r * 0.19), 3, 3), arm);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(r * 1.06, r * 0.24, r * 0.15, r * 0.55), 3, 3), arm);
  // the face, on the right: sleepy at night, smiling by day, shades in strong sun
  const ink = paint(C.ink);
  const fx = r * 0.38;
  const fy = -r * 0.16;
  const sleepy = 1 - smooth(uv / 0.8);
  const shades = smooth(uv - 4.2);
  const eh = mix(7.5, 1.6, sleepy);
  for (let e = 0; e < 2; e++) {
    // the eyes look toward the hour the stem points at
    const look = Math.max(-1, Math.min(1, (barX(0) + UI.bars.w / 2 + live.hour * (UI.bars.w + UI.bars.gap) - UI.sun.x) / 160));
    const ex = fx + (e === 0 ? -6.5 : 6.5) + 2.6 * look;
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(ex - 2.4, fy - eh / 2, 4.8, eh), 1.6, 1.6), ink);
  }
  // mouth: a small smile that opens wider as the UV climbs
  const open = smooth((uv - 1) / 2);
  const mp = Skia.Path.Make();
  mp.moveTo(fx - 4, fy + 9);
  mp.quadTo(fx, fy + 9 + 4 + 3 * open, fx + 4, fy + 9);
  mp.close();
  c.drawPath(mp, ink);
  if (shades > 0.01) {
    // sunglasses slide down onto the eyes
    const drop = (1 - shades) * -26;
    c.save();
    c.translate(0, drop);
    const g = paint('#17181C', shades);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(fx - 15, fy - 6, 13, 10), 4, 4), g);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(fx + 2, fy - 6, 13, 10), 4, 4), g);
    const br = Skia.Paint();
    br.setAntiAlias(true);
    br.setStyle(PaintStyle.Stroke);
    br.setStrokeWidth(2);
    br.setColor(col('#17181C', shades));
    c.drawLine(fx - 2, fy - 3, fx + 2, fy - 3, br);
    c.drawLine(fx - 15, fy - 3, fx - 24, fy - 6, br);
    const gl = paint('#FFFFFF', 0.35 * shades);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(fx - 12, fy - 4.5, 4, 2.2), 1, 1), gl);
    c.restore();
  }
  // a little "z" when it nods off
  if (sleepy > 0.3) {
    const zp = paint(C.ink, (sleepy - 0.3) * 0.8);
    c.drawText('z', fx + 18, fy - 14 - 4 * Math.sin((TAU * 3 * live.amb) / LOOP), zp, r0.f.zed);
  }
  c.restore();
}

export function drawScene(c: SkCanvas, r: Res, g: Geo, live: Live) {
  'worklet';
  c.drawColor(col(C.page));
  c.save();
  c.scale(g.s, g.s);
  const uv = uvAt(live.hour, live.from, live.to, live.tau);
  // the map peeking above the sheet
  c.drawRect(Skia.XYWHRect(0, -20, 402, UI.sheet.y + 40), paint(C.map));
  const road = Skia.Paint();
  road.setAntiAlias(true);
  road.setStyle(PaintStyle.Stroke);
  road.setStrokeWidth(1.2);
  road.setColor(col('#FFFFFF', 0.6));
  c.drawLine(40, -10, 120, 70, road);
  c.drawLine(230, -10, 210, 70, road);
  c.drawLine(300, 20, 402, 0, road);
  // the yellow sheet; its sky warms with the UV and cools toward evening
  const warm = smooth(uv / 3);
  const hot = smooth(uv - 4);
  const top = mixCol(mixCol(col('#EEF2FA'), col(C.cardTop), warm), col('#FFF1C2'), hot);
  const bot = mixCol(mixCol(col('#DCE5F4'), col(C.cardBottom), warm), col('#FFD27A'), hot);
  const sp = Skia.Paint();
  sp.setAntiAlias(true);
  sp.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: UI.sheet.y }, { x: 0, y: UI.sheet.bottom }, [top, bot], null, TileMode.Clamp));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(0, UI.sheet.y, 402, UI.sheet.bottom - UI.sheet.y), UI.sheet.r, UI.sheet.r), sp);
  textC(c, COPY.title, 201, UI.title.y, r.f.title, paint(C.ink));
  textC(c, COPY.place, 201, UI.place.y, r.f.place, paint(C.grey));
  c.drawCircle(UI.close.x, UI.close.y, UI.close.r, paint('#1E1F24', 0.06));
  const xp = Skia.Paint();
  xp.setAntiAlias(true);
  xp.setStyle(PaintStyle.Stroke);
  xp.setStrokeWidth(1.9);
  xp.setStrokeCap(StrokeCap.Round);
  xp.setColor(col(C.ink));
  c.drawLine(UI.close.x - 6, UI.close.y - 6, UI.close.x + 6, UI.close.y + 6, xp);
  c.drawLine(UI.close.x + 6, UI.close.y - 6, UI.close.x - 6, UI.close.y + 6, xp);
  // day chips: the chosen one is dark; each has a dot in the colour of its highest UV
  const ch = UI.chips;
  for (let i = 0; i < 5; i++) {
    const on = i === live.to ? 1 : 0;
    const s = on ? 1 - 0.04 * live.dayPress : 1;
    c.save();
    c.translate(ch.x[i] + ch.w[i] / 2, ch.y);
    c.scale(s, s);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-ch.w[i] / 2, -ch.h / 2, ch.w[i], ch.h), ch.h / 2, ch.h / 2), paint(on ? C.chipOn : C.chip));
    const tw = r.f.chip.measureText(COPY.days[i]).width;
    const x0 = -(tw + 12) / 2;
    c.drawCircle(x0 + 3.5, 0, 3.5, paint(i === 4 ? C.green : C.yellow));
    c.drawText(COPY.days[i], x0 + 12, r.f.chip.getSize() * 0.36, paint(on ? '#FFFFFF' : C.ink), r.f.chip);
    c.restore();
  }
  drawSun(c, r, live, uv);
  // the words for the nearest hour, dimming while the stem is between hours
  const near = Math.max(0, Math.min(10, Math.round(live.hour)));
  const nuv = Math.round(uvAt(near, live.from, live.to, live.tau));
  const between = Math.abs(live.hour - near);
  const wa = 1 - 0.75 * smooth(between * 2.2);
  const wd = words(nuv);
  const head = `${near === 3 ? 'From' : 'At'} ${HOURS[near]}:00 it will be ${wd.level}`;
  // between hours the words go soft (blur and dim) and come back sharp on the hour
  const soft = smooth(between * 2.2);
  const blurred = soft > 0.02;
  if (blurred) {
    const lp = Skia.Paint();
    lp.setImageFilter(Skia.ImageFilter.MakeBlur(3.2 * soft, 3.2 * soft, TileMode.Decal, null));
    c.saveLayer(lp, Skia.XYWHRect(0, UI.headline.y - 26, 402, UI.line.y[1] - UI.headline.y + 40));
  }
  const hw = r.f.head.measureText(head).width;
  const hk = Math.min(1, 366 / hw);
  c.save();
  c.translate(201, UI.headline.y);
  c.scale(hk, hk);
  textC(c, head, 0, 0, r.f.head, paint(C.ink, wa));
  c.restore();
  const b = UI.badge;
  const bc = barColor(nuv);
  const bp = Skia.Paint();
  bp.setAntiAlias(true);
  bp.setColor(Float32Array.of(bc[0], bc[1], bc[2], wa));
  c.drawCircle(b.x, b.y, b.r, bp);
  textC(c, String(nuv), b.x, b.y, r.f.badge, paint(C.ink, wa));
  for (let i = 0; i < 2; i++) textC(c, wd.line[i], 201, UI.line.y[i], r.f.line, paint(C.ink, wa));
  if (blurred) c.restore();
  // sunscreen and a cap appear when the sun asks for them
  const props = smooth((uv - 1.6) / 1.2);
  if (props > 0.01) {
    const ip = paint(C.ink, props);
    c.save();
    c.translate(182, UI.icons.y);
    c.scale(1.25 * (0.7 + 0.3 * props), 1.25 * (0.7 + 0.3 * props));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-6, -10, 12, 20), 2.5, 2.5), ip);
    c.drawRect(Skia.XYWHRect(-4, -12.5, 8, 3), ip);
    const star = paint(C.yellow, props);
    c.drawCircle(0, -1, 2.6, star);
    c.restore();
    c.save();
    c.translate(222, UI.icons.y + 1);
    c.scale(1.25 * (0.7 + 0.3 * props), 1.25 * (0.7 + 0.3 * props));
    const cap = Skia.Path.Make();
    cap.moveTo(-12, 4);
    cap.cubicTo(-12, -8, 6, -10, 8, 2);
    cap.lineTo(14, 4);
    cap.lineTo(12, 7);
    cap.lineTo(-12, 7);
    cap.close();
    c.drawPath(cap, ip);
    c.restore();
  }
  // the stem from the sheet down to the chosen hour; it bends when the hour is moving
  const hx = barX(0) + UI.bars.w / 2 + live.hour * (UI.bars.w + UI.bars.gap);
  const vTop = barValue(Math.max(0, Math.min(10, Math.round(live.hour))), live.from, live.to, live.tau);
  const by = UI.bars.bottom - heightFor(vTop);
  const stem = Skia.Path.Make();
  const bend = Math.max(-26, Math.min(26, -live.hourV * 9));
  stem.moveTo(hx, UI.sheet.bottom - 4);
  stem.quadTo(hx + bend, (UI.sheet.bottom + by) / 2, hx, by);
  const st = Skia.Paint();
  st.setAntiAlias(true);
  st.setStyle(PaintStyle.Stroke);
  st.setStrokeWidth(2);
  st.setColor(col(C.sunDeep));
  c.drawPath(stem, st);
  c.drawCircle(hx, UI.sheet.bottom - 2, 5, paint(C.cardBottom));
  // the hours
  for (let i = 0; i < 11; i++) {
    const v = barValue(i, live.from, live.to, live.tau);
    // bars jump a little as the moving stem passes over them
    const near = Math.exp(-((i - live.hour) * (i - live.hour)) / 0.35);
    const hh = heightFor(v) + 7 * near * Math.min(1, Math.abs(live.hourV) / 3);
    const x = barX(i);
    const y = UI.bars.bottom - hh;
    const cc = barColor(v);
    const p = Skia.Paint();
    p.setAntiAlias(true);
    p.setColor(cc);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y, UI.bars.w, hh + 6), 6, 6), p);
    const label = Math.round(v);
    textC(c, String(label), x + UI.bars.w / 2, UI.bars.bottom - 17, r.f.bar, paint(label === 0 ? '#9A9CA4' : C.ink));
    textC(c, HOURS[i], x + UI.bars.w / 2, UI.bars.labelY, r.f.hour, paint(C.grey));
  }
  c.save();
  c.clipRect(Skia.XYWHRect(0, UI.bars.bottom, 402, 8), ClipOp.Intersect, true);
  c.drawRect(Skia.XYWHRect(0, UI.bars.bottom, 402, 8), paint(C.page));
  c.restore();
  // what the UV index means (below the fold)
  const card = Skia.Paint();
  card.setAntiAlias(true);
  card.setColor(Float32Array.of(1, 1, 1, 1));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(0, 820, 402, 200), 26, 26), card);
  c.drawText(COPY.learn, 22, UI.learn.y + 8, paint(C.ink), r.f.learn);
  c.restore();
}
