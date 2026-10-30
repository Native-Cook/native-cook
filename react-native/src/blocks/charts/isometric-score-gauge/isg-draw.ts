// Isometric Score Gauge — drawing, every frame, in the 402 × 874 reference space.

import {
  BlurStyle,
  ClipOp,
  FilterMode,
  MipmapMode,
  PaintStyle,
  processTransform3d,
  type SkCanvas,
  type SkFont,
  type SkImage,
  type SkTypeface,
  Skia,
  StrokeCap,
  StrokeJoin,
  TileMode,
} from '@shopify/react-native-skia';

import { columnPose, type Frame, LOOP, mix, smooth } from './isg-motion';
import { C, COLUMNS, COPY, GAUGE, REF_W, UI } from './isg-theme';

export type Faces = { serif: SkTypeface; display: SkTypeface; medium: SkTypeface; semi: SkTypeface };
export type Res = { icons: SkImage; f: { logo: SkFont; num: SkFont; label: SkFont; caption: SkFont; card: SkFont }; digitW: number };

export function makeRes(f: Faces, icons: SkImage): Res {
  const num = Skia.Font(f.display, GAUGE.numberSize);
  return {
    icons,
    f: { logo: Skia.Font(f.serif, UI.logo.size), num, label: Skia.Font(f.medium, 11), caption: Skia.Font(f.semi, 9.6), card: Skia.Font(f.serif, 15.5) },
    digitW: num.measureText('8').width,
  };
}

export type Geo = { w: number; h: number; s: number; dy: number };
export type Live = { amb: number };

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
function textC(c: SkCanvas, s: string, x: number, cy: number, font: SkFont, p: ReturnType<typeof Skia.Paint>, track = 0) {
  'worklet';
  if (track === 0) {
    const w = font.measureText(s).width;
    c.drawText(s, x - w / 2, cy + font.getSize() * 0.36, p, font);
    return;
  }
  // letter-spaced: lay the glyphs out one by one
  const ids = font.getGlyphIDs(s);
  const ws = font.getGlyphWidths(ids);
  let total = 0;
  for (let i = 0; i < ws.length; i++) total += ws[i] + (i < ws.length - 1 ? track * font.getSize() : 0);
  let x0 = x - total / 2;
  const pos = [];
  for (let i = 0; i < ws.length; i++) {
    pos.push({ x: x0, y: 0 });
    x0 += ws[i] + track * font.getSize();
  }
  c.drawGlyphs(ids, pos, 0, cy + font.getSize() * 0.36, font, p);
}

/** Gauge angle (degrees, screen space) for a score. */
function angleOf(s: number) {
  'worklet';
  return GAUGE.start + ((Math.max(GAUGE.low, Math.min(GAUGE.high, s)) - GAUGE.low) / (GAUGE.high - GAUGE.low)) * GAUGE.sweep;
}

/** The score in chrome digits that roll like an odometer. */
function drawNumber(c: SkCanvas, r: Res, f: Frame, amb: number) {
  'worklet';
  const v = f.score;
  const size = GAUGE.numberSize;
  const dw = r.digitW * 0.98;
  const x0 = GAUGE.cx - (dw * 3) / 2;
  const cy = GAUGE.numberY;
  const capH = size * 0.72;
  const delta = f.score - f.base;
  const tint = delta >= 0 ? col(C.up) : col(C.down);
  const k = Math.min(1, Math.abs(delta) / 20);
  for (let d = 0; d < 3; d++) {
    const place = Math.pow(10, 2 - d);
    const whole = v / place;
    // only the lowest digit turns continuously; higher ones turn as the one below passes 9
    let frac = 0;
    if (d === 2) frac = whole - Math.floor(whole);
    else {
      const below = (v % place) / place;
      frac = Math.max(0, (below - 0.9) * 10);
    }
    const digit = Math.floor(whole) % 10;
    const next = (digit + 1) % 10;
    const x = x0 + d * dw;
    c.save();
    c.clipRect(Skia.XYWHRect(x - 4, cy - capH / 2 - 6, dw + 8, capH + 12), ClipOp.Intersect, true);
    for (let j = 0; j < 2; j++) {
      const ch = String(j === 0 ? digit : next);
      const y = cy + capH / 2 - frac * (capH + 14) + j * (capH + 14);
      // chrome: a dark bevel under a bright vertical gradient, faintly tinted by where the score is heading
      const under = paint('#2A2A30');
      c.drawText(ch, x + 1.5, y + 2.5, under, r.f.num);
      const p = Skia.Paint();
      p.setAntiAlias(true);
      p.setShader(
        Skia.Shader.MakeLinearGradient(
          { x: 0, y: y - capH },
          { x: 0, y },
          [col('#FFFFFF'), Float32Array.of(mix(0.62, tint[0], 0.35 * k), mix(0.62, tint[1], 0.35 * k), mix(0.66, tint[2], 0.35 * k), 1), col('#E6E6EA'), col('#8C8C93')],
          [0, 0.45, 0.62, 1],
          TileMode.Clamp,
        ),
      );
      c.drawText(ch, x, y, p, r.f.num);
      // a glint runs across the chrome twice a loop
      const q = ((amb % (LOOP / 2)) / (LOOP / 2)) * 1.6 - 0.3;
      const gx = GAUGE.cx - dw * 2 + q * dw * 4;
      if (q > -0.25 && q < 1.25) {
        const sh = Skia.Paint();
        sh.setAntiAlias(true);
        sh.setShader(Skia.Shader.MakeLinearGradient({ x: gx - 26, y: y - capH }, { x: gx + 26, y }, [col('#FFFFFF', 0), col('#FFFFFF', 0.85), col('#FFFFFF', 0)], [0.3, 0.5, 0.7], TileMode.Clamp));
        c.drawText(ch, x, y, sh, r.f.num);
      }
    }
    c.restore();
  }
}

function drawGauge(c: SkCanvas, r: Res, f: Frame, live: Live) {
  'worklet';
  const g = GAUGE;
  // a dark disc the gauge sits in
  const disc = Skia.Paint();
  disc.setShader(Skia.Shader.MakeRadialGradient({ x: g.cx, y: g.cy - 10 }, g.r + 30, [col('#1A1A1E'), col(C.disc), col(C.bg, 0)], [0, 0.82, 1], TileMode.Clamp));
  c.drawCircle(g.cx, g.cy - 10, g.r + 30, disc);
  const box = Skia.XYWHRect(g.cx - g.r, g.cy - g.r, g.r * 2, g.r * 2);
  const track = Skia.Paint();
  track.setAntiAlias(true);
  track.setStyle(PaintStyle.Stroke);
  track.setStrokeWidth(g.width);
  track.setStrokeCap(StrokeCap.Round);
  track.setColor(col(C.track));
  const tp = Skia.Path.Make();
  tp.addArc(box, g.start, g.sweep);
  c.drawPath(tp, track);
  // the score's arc: red to green round the dial, glowing, its tip breathing
  const end = angleOf(f.score);
  const fill = Skia.Path.Make();
  fill.addArc(box, g.start, end - g.start);
  const stops = [col('#E8344B'), col('#F2552F'), col('#F59A2A'), col('#E9D34A'), col('#7FD957'), col('#2FCB72'), col('#2FCB72')];
  const pos = [0, 0.12, 0.3, 0.48, 0.66, 0.78, 1];
  // the sweep gradient starts at 0°, so rotate it to begin where the dial does
  const m = Skia.Matrix();
  m.translate(g.cx, g.cy);
  // the gradient begins a little before the dial so the round cap at 300 is red, not the far end's green
  const lead = 12;
  m.rotate(((g.start - lead) * Math.PI) / 180);
  m.translate(-g.cx, -g.cy);
  const sh = Skia.Shader.MakeSweepGradient(g.cx, g.cy, stops, pos.map((p) => (lead + p * g.sweep) / 360), TileMode.Clamp, m);
  const glow = Skia.Paint();
  glow.setAntiAlias(true);
  glow.setStyle(PaintStyle.Stroke);
  glow.setStrokeWidth(g.width * 3.4);
  glow.setStrokeCap(StrokeCap.Round);
  glow.setShader(sh);
  glow.setAlphaf(0.35 + 0.08 * Math.sin((2 * Math.PI * 2 * live.amb) / LOOP));
  glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 9, true));
  c.drawPath(fill, glow);
  const ap = Skia.Paint();
  ap.setAntiAlias(true);
  ap.setStyle(PaintStyle.Stroke);
  ap.setStrokeWidth(g.width);
  ap.setStrokeCap(StrokeCap.Round);
  ap.setShader(sh);
  c.drawPath(fill, ap);
  const a = (end * Math.PI) / 180;
  c.drawCircle(g.cx + Math.cos(a) * g.r, g.cy + Math.sin(a) * g.r, g.width * 0.42, paint('#FFFFFF', 0.85));
  textC(c, COPY.low, g.labels.x[0], g.labels.y, r.f.label, paint(C.grey));
  textC(c, COPY.high, g.labels.x[1], g.labels.y, r.f.label, paint(C.grey));
  drawNumber(c, r, f, live.amb);
}

function drawCards(c: SkCanvas, r: Res, f: Frame, amb: number) {
  'worklet';
  const G = UI.grid;
  for (let i = 0; i < COLUMNS.length; i++) {
    const pose = columnPose(i, f.scroll);
    if (pose.x < -90 || pose.x > REF_W + 90) continue;
    for (let row = 0; row < 2; row++) {
      const sc = COLUMNS[i][row];
      const idx = i * 2 + row;
      const lift = idx === f.sel ? f.selW : 0;
      const y = G.rows[row] + G.h / 2;
      c.save();
      c.translate(pose.x, y - 6 * lift);
      c.concat(processTransform3d([{ perspective: 520 }, { rotateY: (pose.ang * Math.PI) / 180 }]));
      const k = pose.k * (1 + 0.04 * lift);
      c.scale(k, k);
      const rr = Skia.RRectXY(Skia.XYWHRect(-G.w / 2, -G.h / 2, G.w, G.h), 9, 9);
      // the chosen card glows the colour of where it would take the score
      if (lift > 0.01) {
        const gl = Skia.Paint();
        gl.setAntiAlias(true);
        gl.setColor(col(sc.delta >= 0 ? C.up : C.down, 0.45 * lift));
        gl.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 14, true));
        c.drawRRect(rr, gl);
      }
      c.drawRRect(rr, paint(C.card));
      c.save();
      c.clipRRect(rr, ClipOp.Intersect, true);
      const glow = Skia.Paint();
      glow.setShader(
        Skia.Shader.MakeLinearGradient({ x: 0, y: G.h * 0.05 }, { x: 0, y: G.h / 2 }, [col(C.glow, 0), col(sc.icon === 4 ? '#24493A' : sc.icon === 5 ? '#4A3426' : C.glow, 0.85)], null, TileMode.Clamp),
      );
      c.drawRect(Skia.XYWHRect(-G.w / 2, -G.h / 2, G.w, G.h), glow);
      const ic = sc.icon;
      const sw = r.icons.width() / 3;
      const s = G.w * 0.82;
      c.drawImageRectOptions(
        r.icons,
        Skia.XYWHRect((ic % 3) * sw, Math.floor(ic / 3) * sw, sw, sw),
        // the chosen card's object floats while it is chosen
        Skia.XYWHRect(-s / 2 + 8, G.h / 2 - s + 12 - 4 * lift - 5 * lift * (0.5 + 0.5 * Math.sin((Math.PI * 2 * 4 * amb) / LOOP)), s, s),
        FilterMode.Linear,
        MipmapMode.Linear,
        null,
      );
      c.restore();
      const edge = Skia.Paint();
      edge.setAntiAlias(true);
      edge.setStyle(PaintStyle.Stroke);
      edge.setStrokeWidth(1);
      edge.setColor(lift > 0.01 ? col(sc.delta >= 0 ? C.up : C.down, 0.4 + 0.5 * lift) : col(C.cardEdge));
      c.drawRRect(rr, edge);
      const tpaint = paint(C.cardText, 1 - 0.35 * smooth(Math.abs(pose.ang) / 62));
      for (let l = 0; l < sc.lines.length; l++) textC(c, sc.lines[l], 0, -G.h / 2 + 34 + l * 22, r.f.card, tpaint);
      c.restore();
    }
  }
}

export function drawScene(c: SkCanvas, r: Res, g: Geo, f: Frame, live: Live) {
  'worklet';
  c.drawColor(col(C.bg));
  c.save();
  c.translate(0, g.dy);
  c.scale(g.s, g.s);
  // back arrow and the invented logo
  const st = Skia.Paint();
  st.setAntiAlias(true);
  st.setStyle(PaintStyle.Stroke);
  st.setStrokeWidth(1.8);
  st.setStrokeCap(StrokeCap.Round);
  st.setStrokeJoin(StrokeJoin.Round);
  st.setColor(col(C.ink));
  const b = UI.back;
  c.drawLine(b.x - 14, b.y, b.x + 14, b.y, st);
  c.drawLine(b.x - 14, b.y, b.x - 7, b.y - 7, st);
  c.drawLine(b.x - 14, b.y, b.x - 7, b.y + 7, st);
  const lp = Skia.Paint();
  lp.setAntiAlias(true);
  lp.setShader(Skia.Shader.MakeLinearGradient({ x: 140, y: 0 }, { x: 262, y: 0 }, [col('#F2F2F2'), col('#B8B8BE')], null, TileMode.Clamp));
  textC(c, COPY.logo, UI.logo.x, UI.logo.y, r.f.logo, lp);
  drawGauge(c, r, f, live);
  textC(c, COPY.caption, REF_W / 2, UI.captionY, r.f.caption, paint(C.caption), 0.12);
  drawCards(c, r, f, live.amb);
  c.restore();
}
