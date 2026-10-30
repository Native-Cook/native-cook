// Blacklight Flight Passport — drawing. The same passport is drawn twice when the light changes:
// in daylight, then under the blacklight inside the light's reach (a circle spreading from the
// button, or the finger's torch spot), where the hidden UV ink shows.

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
  type SkTypeface,
  Skia,
  StrokeCap,
  StrokeJoin,
  TileMode,
} from '@shopify/react-native-skia';

import { type Frame, LOOP, smooth } from './bfp-motion';
import { AIRPORTS, COPY, FLAGS, LIGHT, MAP, PAGE, REF_W, ROUTES, TOTALS, UI, UV } from './bfp-theme';

export type Faces = { regular: SkTypeface; medium: SkTypeface; semi: SkTypeface; bold: SkTypeface; mono: SkTypeface };

export type Res = {
  f: {
    title: SkFont;
    sub: SkFont;
    brand: SkFont;
    kinds: SkFont;
    big: SkFont;
    word: SkFont;
    row: SkFont;
    rowB: SkFont;
    statL: SkFont;
    statV: SkFont;
    mrz: SkFont;
    button: SkFont;
    share: SkFont;
    stamp: SkFont;
    tiny: SkFont;
  };
  map: SkImage;
  plane: SkPath;
  arcs: { path: SkPath; a: [number, number]; b: [number, number]; c: [number, number] }[];
  guilloche: SkPath;
  rosette: SkPath;
};

type Pal = typeof LIGHT & { glowLine?: string; stamp?: string; bgGlow?: string };

const PLANE = 'M0 -9 C0.9 -9 1.4 -7.8 1.4 -6.4 L1.4 -2.2 L8.6 2.2 L8.6 4 L1.4 1.9 L1.4 6 L3.6 7.8 L3.6 9 L0 8 L-3.6 9 L-3.6 7.8 L-1.4 6 L-1.4 1.9 L-8.6 4 L-8.6 2.2 L-1.4 -2.2 L-1.4 -6.4 C-1.4 -7.8 -0.9 -9 0 -9 Z';

function project(lon: number, lat: number): [number, number] {
  const x = ((lon - MAP.lon0) / MAP.lonSpan) * UI.card.w;
  const y = PAGE.map.y + (MAP.lat0 - lat) * MAP.ptPerLat;
  return [x, y];
}

export function makeRes(f: Faces, map: SkImage): Res {
  const arcs = ROUTES.map(([from, to, bow]) => {
    const a = project(AIRPORTS[from][0], AIRPORTS[from][1]);
    const b = project(AIRPORTS[to][0], AIRPORTS[to][1]);
    const mx = (a[0] + b[0]) / 2;
    const my = (a[1] + b[1]) / 2;
    const len = Math.hypot(b[0] - a[0], b[1] - a[1]);
    // routes bow toward the pole, like great circles drawn flat
    const c: [number, number] = [mx, my - len * bow];
    const path = Skia.Path.Make();
    path.moveTo(a[0], a[1]);
    path.quadTo(c[0], c[1], b[0], b[1]);
    return { path, a, b, c };
  });
  // UV security printing: fine sine lines across the page and a rosette by the totals
  const g = Skia.Path.Make();
  for (let i = 0; i < 22; i++) {
    const y0 = 20 + i * 19;
    g.moveTo(0, y0);
    for (let x = 0; x <= UI.card.w; x += 4) g.lineTo(x, y0 + 5 * Math.sin(x / 17 + i * 0.7) + 3 * Math.sin(x / 7.3 - i));
  }
  const ros = Skia.Path.Make();
  const R = 34;
  const r = 13;
  const d = 22;
  for (let k = 0; k <= 720; k++) {
    const th = (k / 720) * Math.PI * 2 * 13;
    const x = (R - r) * Math.cos(th) + d * Math.cos(((R - r) / r) * th);
    const y = (R - r) * Math.sin(th) - d * Math.sin(((R - r) / r) * th);
    if (k === 0) ros.moveTo(x, y);
    else ros.lineTo(x, y);
  }
  const F = (t: SkTypeface, s: number) => Skia.Font(t, s);
  return {
    f: {
      title: F(f.medium, 17),
      sub: F(f.regular, 14),
      brand: F(f.semi, 14.5),
      kinds: F(f.semi, 6.8),
      big: F(f.semi, PAGE.big.size),
      word: F(f.regular, PAGE.word.size),
      row: F(f.medium, PAGE.rows.size),
      rowB: F(f.regular, PAGE.rows.size),
      statL: F(f.regular, 10.5),
      statV: F(f.medium, 18),
      mrz: F(f.mono, PAGE.mrz.size),
      button: F(f.medium, 15),
      share: F(f.regular, 15),
      stamp: F(f.bold, 11),
      tiny: F(f.semi, 7),
    },
    map,
    plane: Skia.Path.MakeFromSVGString(PLANE)!,
    arcs,
    guilloche: g,
    rosette: ros,
  };
}

// ------------------------------------------------------------------ helpers (worklets)

export type Geo = { w: number; h: number; s: number; dy: number };
export type Live = { amb: number; torch: number; tx: number; ty: number; press: number; tiltX?: number; tiltY?: number };

function col(h: string, a = 1) {
  'worklet';
  if (h.startsWith('rgba')) {
    const n = h.slice(5, -1).split(',').map((v) => parseFloat(v));
    return Float32Array.of(n[0] / 255, n[1] / 255, n[2] / 255, n[3] * a);
  }
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
function text(c: SkCanvas, s: string, x: number, cy: number, font: SkFont, p: ReturnType<typeof Skia.Paint>, ax = 0) {
  'worklet';
  const w = ax === 0 ? 0 : font.measureText(s).width;
  c.drawText(s, x - w * ax, cy + font.getSize() * 0.36, p, font);
}
function groups(n: number) {
  'worklet';
  const s = String(Math.round(n));
  let out = '';
  for (let i = 0; i < s.length; i++) {
    out += s[i];
    const left = s.length - 1 - i;
    if (left > 0 && left % 3 === 0) out += ',';
  }
  return out;
}

function drawFlag(c: SkCanvas, x: number, y: number, r: number, k: number) {
  'worklet';
  const fl = FLAGS[k];
  c.save();
  const clip = Skia.Path.Make();
  clip.addCircle(x, y, r);
  c.clipPath(clip, ClipOp.Intersect, true);
  if (fl.kind === 'dot') {
    c.drawCircle(x, y, r, paint(fl.colors[0]));
    c.drawCircle(x, y, r * 0.45, paint(fl.colors[1]));
  } else {
    const n = fl.colors.length;
    for (let i = 0; i < n; i++) {
      const p = paint(fl.colors[i]);
      if (fl.kind === 'h') c.drawRect(Skia.XYWHRect(x - r, y - r + (2 * r * i) / n, 2 * r, (2 * r) / n + 0.2), p);
      else c.drawRect(Skia.XYWHRect(x - r + (2 * r * i) / n, y - r, (2 * r) / n + 0.2, 2 * r), p);
    }
  }
  c.restore();
  const ring = Skia.Paint();
  ring.setAntiAlias(true);
  ring.setStyle(PaintStyle.Stroke);
  ring.setStrokeWidth(0.6);
  ring.setColor(Float32Array.of(0, 0, 0, 0.15));
  c.drawCircle(x, y, r, ring);
}

/** The page of the passport (card space). */
function drawPage(c: SkCanvas, r: Res, f: Frame, pal: Pal, uv: boolean, live: Live) {
  'worklet';
  const cw = UI.card.w;
  // the line of tiny planes and the hub along the top
  const tp = paint(pal.ink, 0.28);
  for (let i = 0; i < 11; i++) {
    const x = 14 + i * 31;
    if (i === 2 || i === 9) {
      text(c, 'SFO', x - 6, PAGE.planesY, r.f.tiny, tp);
      continue;
    }
    c.save();
    c.translate(x, PAGE.planesY);
    c.rotate(90, 0, 0);
    c.scale(0.42, 0.42);
    c.drawPath(r.plane, tp);
    c.restore();
  }
  // the world, in dots
  const mp = Skia.Paint();
  mp.setAntiAlias(true);
  mp.setColorFilter(Skia.ColorFilter.MakeBlend(col(pal.map), BlendMode.SrcIn));
  c.drawImageRectOptions(r.map, Skia.XYWHRect(0, 0, r.map.width(), r.map.height()), Skia.XYWHRect(0, PAGE.map.y, cw, PAGE.map.h), FilterMode.Linear, MipmapMode.Linear, mp);
  if (uv) {
    // hidden ink: fine security lines, a rosette and a big plane watermark, glowing as the light settles
    const gl = Skia.Paint();
    gl.setAntiAlias(true);
    gl.setStyle(PaintStyle.Stroke);
    gl.setStrokeWidth(0.7);
    gl.setColor(col(pal.glowLine ?? '#5FF3FF', 0.42 * (0.4 + 0.6 * f.glow)));
    c.drawPath(r.guilloche, gl);
    c.save();
    c.translate(cw - 64, 318);
    c.rotate((360 * live.amb) / LOOP / 6, 0, 0);
    gl.setColor(col(pal.stamp ?? '#FF7AD9', 0.55 * f.glow));
    c.drawPath(r.rosette, gl);
    c.restore();
    c.save();
    c.translate(cw * 0.74, 296);
    c.rotate(-24, 0, 0);
    c.scale(4.6, 4.6);
    const wm = Skia.Paint();
    wm.setAntiAlias(true);
    wm.setColor(col(pal.glowLine ?? '#5FF3FF', 0.34 * f.glow));
    wm.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 0.6, true));
    c.drawPath(r.plane, wm);
    c.restore();
  }
  // the routes, each drawn in turn with a plane at its tip; airports pop as they are reached
  const ap = Skia.Paint();
  ap.setAntiAlias(true);
  ap.setStyle(PaintStyle.Stroke);
  ap.setStrokeWidth(uv ? 1.5 : 1.25);
  ap.setStrokeCap(StrokeCap.Round);
  const dots: number[] = [];
  for (let i = 0; i < r.arcs.length; i++) {
    const p = f.routes[i];
    if (p <= 0.001) continue;
    const arc = r.arcs[i];
    ap.setColor(col(pal.arc, f.fade));
    if (p < 0.999) {
      const pt = arc.path.copy();
      pt.trim(0, p, false);
      c.drawPath(pt, ap);
      // the plane at the tip, turned along the route
      const q = 1 - p;
      const x = q * q * arc.a[0] + 2 * q * p * arc.c[0] + p * p * arc.b[0];
      const y = q * q * arc.a[1] + 2 * q * p * arc.c[1] + p * p * arc.b[1];
      const dx = 2 * q * (arc.c[0] - arc.a[0]) + 2 * p * (arc.b[0] - arc.c[0]);
      const dy = 2 * q * (arc.c[1] - arc.a[1]) + 2 * p * (arc.b[1] - arc.c[1]);
      c.save();
      c.translate(x, y);
      c.rotate((Math.atan2(dy, dx) * 180) / Math.PI + 90, 0, 0);
      c.scale(0.62, 0.62);
      c.drawPath(r.plane, paint(uv ? '#FFFFFF' : pal.ink));
      c.restore();
    } else c.drawPath(arc.path, ap);
    dots.push(i, p);
  }
  const dp = paint(pal.airport, f.fade);
  for (let k = 0; k < dots.length; k += 2) {
    const arc = r.arcs[dots[k]];
    const p = dots[k + 1];
    const pop = (u: number) => {
      'worklet';
      return u <= 0 ? 0 : u >= 1 ? 1 : 1 + 0.6 * Math.sin(Math.PI * u) * (1 - u);
    };
    c.drawCircle(arc.a[0], arc.a[1], 2.1 * pop(Math.min(1, p * 6)), dp);
    if (p > 0.92) c.drawCircle(arc.b[0], arc.b[1], 2.1 * pop((p - 0.92) / 0.08), dp);
  }
  // flags, the rule, the passport's words
  for (let k = 0; k < FLAGS.length; k++) drawFlag(c, cw / 2 - 6 * 11.6 + k * 11.6, PAGE.flagsY, 5.4, k);
  const rule = Skia.Paint();
  rule.setColor(col(pal.ink, 0.15));
  rule.setStrokeWidth(0.8);
  c.drawLine(0, PAGE.ruleY, cw, PAGE.ruleY, rule);
  text(c, COPY.brand, PAGE.brand.x, PAGE.brand.y, r.f.brand, paint(pal.ink));
  c.drawRect(Skia.XYWHRect(PAGE.kinds.x - 12, PAGE.kinds.y - 3, 9, 6), paint(pal.ink, 0.75));
  text(c, COPY.kinds, PAGE.kinds.x, PAGE.kinds.y, r.f.kinds, paint(pal.ink));
  const lg = PAGE.logo;
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(lg.x, lg.y - lg.s / 2, lg.s, lg.s), 5, 5), paint(uv ? '#FFFFFF' : '#FFFFFF', uv ? 0.5 : 1));
  c.save();
  c.translate(lg.x + lg.s / 2, lg.y);
  c.rotate(45, 0, 0);
  c.scale(0.8, 0.8);
  c.drawPath(r.plane, paint(uv ? pal.ink : '#C9A8F2'));
  c.restore();
  // the counters
  const k = f.count;
  text(c, String(Math.round(TOTALS.flights * k)), PAGE.big.x, PAGE.big.y, r.f.big, paint(pal.text));
  text(c, COPY.flights, PAGE.word.x, PAGE.word.y, r.f.word, paint(pal.inkSoft));
  for (let i = 0; i < 4; i++) {
    const y = PAGE.rows.y[i];
    text(c, COPY.rows[i][0], PAGE.rows.x, y, r.f.row, paint(pal.inkSoft));
    const w = r.f.row.measureText(COPY.rows[i][0] + ' ').width;
    text(c, COPY.rows[i][1], PAGE.rows.x + w, y, r.f.rowB, paint(pal.grey));
  }
  const vals = [
    `${groups(TOTALS.km * k)} km`,
    `${Math.round(TOTALS.days * k)}d ${Math.round(TOTALS.hours * k)}h`,
    String(Math.round(TOTALS.airports * k)),
    String(Math.round(TOTALS.airlines * k)),
  ];
  for (let i = 0; i < 4; i++) {
    text(c, COPY.stats[i], PAGE.stats.x[i], PAGE.stats.labelY, r.f.statL, paint(pal.grey));
    text(c, vals[i], PAGE.stats.x[i], PAGE.stats.valueY, r.f.statV, paint(pal.text));
  }
  for (let i = 0; i < 2; i++) text(c, COPY.mrz[i], PAGE.mrz.x, PAGE.mrz.y[i], r.f.mrz, paint(pal.ink, 0.85));
  if (uv) {
    // the stamp only blacklight shows
    c.save();
    c.translate(cw - 104, 402);
    c.rotate(-8, 0, 0);
    const sp = Skia.Paint();
    sp.setAntiAlias(true);
    sp.setStyle(PaintStyle.Stroke);
    sp.setStrokeWidth(1.6);
    sp.setColor(col(pal.stamp ?? '#FF7AD9', 0.75 * f.glow));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-70, -13, 140, 26), 6, 6), sp);
    text(c, COPY.stamp, 0, 0, r.f.stamp, paint(pal.stamp ?? '#FF7AD9', 0.85 * f.glow), 0.5);
    c.restore();
  }
}

function drawWorld(c: SkCanvas, r: Res, f: Frame, pal: Pal, uv: boolean, live: Live, g: Geo) {
  'worklet';
  // backdrop
  if (uv) {
    const bg = Skia.Paint();
    bg.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: 0, y: 874 }, [col('#141052'), col(pal.bg), col('#05061A')], [0, 0.45, 1], TileMode.Clamp));
    c.drawRect(Skia.XYWHRect(0, -g.dy / g.s - 40, REF_W, 1000), bg);
    const glow = Skia.Paint();
    glow.setShader(Skia.Shader.MakeRadialGradient({ x: REF_W / 2, y: -20 }, 260, [col(pal.bgGlow ?? '#3A23B8', 0.85), col(pal.bgGlow ?? '#3A23B8', 0)], null, TileMode.Clamp));
    c.drawCircle(REF_W / 2, -20, 260, glow);
  } else c.drawRect(Skia.XYWHRect(0, -g.dy / g.s - 40, REF_W, 1000), paint(pal.bg));
  // header
  for (const b of [UI.close, UI.layout]) {
    if (!uv) {
      const sh = Skia.Paint();
      sh.setAntiAlias(true);
      sh.setColor(Float32Array.of(0, 0, 0, 0.07));
      sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 6, true));
      c.drawCircle(b.x, b.y + 2, b.r, sh);
    }
    c.drawCircle(b.x, b.y, b.r, paint(pal.chrome));
  }
  const ik = Skia.Paint();
  ik.setAntiAlias(true);
  ik.setStyle(PaintStyle.Stroke);
  ik.setStrokeWidth(2);
  ik.setStrokeCap(StrokeCap.Round);
  ik.setStrokeJoin(StrokeJoin.Round);
  ik.setColor(col(pal.chromeInk));
  c.drawLine(UI.close.x - 7, UI.close.y - 7, UI.close.x + 7, UI.close.y + 7, ik);
  c.drawLine(UI.close.x + 7, UI.close.y - 7, UI.close.x - 7, UI.close.y + 7, ik);
  ik.setStrokeWidth(1.6);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(UI.layout.x - 10, UI.layout.y - 8, 20, 16), 3, 3), ik);
  c.drawLine(UI.layout.x - 10, UI.layout.y - 3, UI.layout.x + 10, UI.layout.y - 3, ik);
  c.drawLine(UI.layout.x + 2, UI.layout.y - 3, UI.layout.x + 2, UI.layout.y + 8, ik);
  text(c, COPY.title, REF_W / 2, UI.titleY, r.f.title, paint(pal.header), 0.5);
  text(c, COPY.sub, REF_W / 2, UI.subY, r.f.sub, paint(pal.headerSub), 0.5);

  // the passport: its cover's edges, then the page
  const cd = UI.card;
  if (!uv) {
    const sh = Skia.Paint();
    sh.setAntiAlias(true);
    sh.setColor(Float32Array.of(0.05, 0.08, 0.2, 0.16));
    sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 16, true));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(cd.x + 6, cd.y + 14, cd.w - 12, cd.h), cd.r, cd.r), sh);
  } else {
    const sh = Skia.Paint();
    sh.setAntiAlias(true);
    sh.setColor(col('#7B6CFF', 0.35 * (0.5 + 0.5 * f.glow)));
    sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 22, true));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(cd.x, cd.y, cd.w, cd.h), cd.r, cd.r), sh);
  }
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(cd.x, cd.y, cd.w, cd.h), cd.r, cd.r), paint(pal.cover));
  const pageR = Skia.RRectXY(Skia.XYWHRect(cd.x, cd.y + cd.band, cd.w, cd.h - cd.band - cd.bandBottom), 4, 4);
  const pg = Skia.Paint();
  pg.setAntiAlias(true);
  pg.setShader(Skia.Shader.MakeLinearGradient({ x: cd.x, y: cd.y }, { x: cd.x + cd.w, y: cd.y + cd.h }, [col(pal.page), col(pal.page2)], null, TileMode.Clamp));
  c.drawRRect(pageR, pg);
  c.save();
  c.clipRRect(pageR, ClipOp.Intersect, true);
  c.translate(cd.x, cd.y);
  drawPage(c, r, f, pal, uv, live);
  c.restore();

  // Blacklight button
  const b = UI.button;
  const ps = 1 - 0.04 * Math.max(f.press, live.press);
  c.save();
  c.translate(b.x + b.w / 2, b.y + b.h / 2);
  c.scale(ps, ps);
  const rr = Skia.RRectXY(Skia.XYWHRect(-b.w / 2, -b.h / 2, b.w, b.h), b.h / 2, b.h / 2);
  if (uv) {
    const gw = Skia.Paint();
    gw.setAntiAlias(true);
    gw.setColor(col('#B38CFF', 0.55 + 0.25 * Math.sin((2 * Math.PI * 2 * live.amb) / LOOP)));
    gw.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 10, true));
    c.drawRRect(rr, gw);
  }
  c.drawRRect(rr, paint(pal.button));
  const br = Skia.Paint();
  br.setAntiAlias(true);
  br.setStyle(PaintStyle.Stroke);
  br.setStrokeWidth(uv ? 1.6 : 1);
  br.setColor(uv ? col('#D9C2FF') : Float32Array.of(0, 0, 0, 0.1));
  c.drawRRect(rr, br);
  // a small torch icon
  const ti = paint(pal.buttonInk);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-b.w / 2 + 16, -6.5, 5, 13), 1.5, 1.5), ti);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-b.w / 2 + 14.6, -8, 7.8, 4), 1, 1), ti);
  text(c, COPY.button, -b.w / 2 + 30, 0, r.f.button, paint(pal.buttonInk));
  c.restore();

  // share row
  const s = UI.share;
  for (let i = 0; i < 3; i++) {
    const x = s.x[i];
    const ic = paint(uv ? '#FFFFFF' : '#111111');
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x - s.s / 2, s.y - s.s / 2, s.s, s.s), 7, 7), ic);
    const inner = paint(uv ? '#141052' : '#FFFFFF');
    if (i === 0) {
      c.drawOval(Skia.XYWHRect(x - 8, s.y - 6.5, 16, 12), inner);
      c.drawCircle(x - 5, s.y + 6, 2, inner);
    } else if (i === 1) {
      for (let k = 0; k < 8; k++) {
        const a = (k / 8) * Math.PI * 2;
        const ring = Skia.Paint();
        ring.setAntiAlias(true);
        ring.setStyle(PaintStyle.Stroke);
        ring.setStrokeWidth(1.1);
        ring.setColor(col(uv ? '#141052' : '#FFFFFF'));
        c.drawCircle(x + Math.cos(a) * 3.6, s.y + Math.sin(a) * 3.6, 3.6, ring);
      }
    } else {
      for (let k = -1; k <= 1; k++) c.drawCircle(x + k * 5.5, s.y, 1.9, inner);
    }
    text(c, COPY.share[i], x, s.labelY, r.f.share, paint(uv ? '#FFFFFF' : '#111111'), 0.5);
  }
}

export function drawScene(c: SkCanvas, r: Res, g: Geo, f: Frame, live: Live) {
  'worklet';
  c.save();
  c.translate(0, g.dy);
  c.scale(g.s, g.s);
  drawWorld(c, r, f, LIGHT as Pal, false, live, g);
  // a hologram sheen on the page: it slides with the phone's tilt (and drifts slowly on its own)
  const sheenA = 1 - smooth(f.uv * 3);
  if (sheenA > 0.01) {
    const K = UI.card;
    const u = 0.5 + 0.42 * (live.tiltX ?? 0) + 0.18 * Math.sin((Math.PI * 2 * live.amb) / LOOP) + 0.1 * (live.tiltY ?? 0);
    const cx = K.x + K.w * u;
    const sh = Skia.Paint();
    sh.setAntiAlias(true);
    sh.setBlendMode(BlendMode.Plus);
    sh.setShader(
      Skia.Shader.MakeLinearGradient(
        { x: cx - 90, y: K.y },
        { x: cx + 90, y: K.y + K.h * 0.5 },
        [col('#FFFFFF', 0), col('#C9F2FF', 0.1 * sheenA), col('#FFE7FB', 0.16 * sheenA), col('#E9FFD9', 0.1 * sheenA), col('#FFFFFF', 0)],
        [0, 0.35, 0.5, 0.65, 1],
        TileMode.Clamp,
      ),
    );
    c.save();
    c.clipRRect(Skia.RRectXY(Skia.XYWHRect(K.x, K.y, K.w, K.h), K.r, K.r), ClipOp.Intersect, true);
    c.drawRect(Skia.XYWHRect(K.x, K.y, K.w, K.h), sh);
    c.restore();
  }
  const reach = f.uv;
  const torch = live.torch;
  if (reach > 0.001 || torch > 0.01) {
    // the same passport under the blacklight, seen only where the light reaches
    c.saveLayer();
    drawWorld(c, r, { ...f, glow: Math.max(f.glow, torch) }, UV as Pal, true, live, g);
    const mask = Skia.Paint();
    mask.setBlendMode(BlendMode.DstIn);
    c.saveLayer(mask);
    const bx = UI.button.x + UI.button.w / 2;
    const by = UI.button.y + UI.button.h / 2;
    if (reach > 0.001) {
      const R = 20 + reach * 1080;
      const m = Skia.Paint();
      m.setShader(Skia.Shader.MakeRadialGradient({ x: bx, y: by }, R, [col('#000000'), col('#000000'), col('#000000', 0)], [0, Math.max(0, (R - 70) / R), 1], TileMode.Clamp));
      c.drawCircle(bx, by, R, m);
    }
    if (torch > 0.01) {
      const R = 84 * smooth(torch);
      const m = Skia.Paint();
      m.setShader(Skia.Shader.MakeRadialGradient({ x: live.tx, y: live.ty }, R, [col('#000000'), col('#000000', 0.9), col('#000000', 0)], [0, 0.55, 1], TileMode.Clamp));
      c.drawCircle(live.tx, live.ty, R, m);
    }
    c.restore();
    c.restore();
    // the light's leading edge glows violet
    if (reach > 0.001 && reach < 0.999) {
      const R = 20 + reach * 1080;
      const e = Skia.Paint();
      e.setAntiAlias(true);
      e.setStyle(PaintStyle.Stroke);
      e.setStrokeWidth(26);
      e.setColor(col('#8E6BFF', 0.35 * Math.sin(Math.PI * reach)));
      e.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 18, true));
      c.drawCircle(bx, by, R - 40, e);
    }
  }
  c.restore();
}
