// Clean Air Dial — drawing, every frame, in the pin's pixels (see cad-theme.ts).

import { BlendMode, ClipOp, FilterMode, MipmapMode, PaintStyle, type SkCanvas, type SkFont, type SkImage, type SkTypeface, Skia, StrokeCap, StrokeJoin, TileMode } from '@shopify/react-native-skia';

import { clamp01, F_HIGH, type Frame, levelAt, LOOP, mix, pmOf, smooth } from './cad-motion';
import { BASE_DUST, C, COPY, DUST, LEVELS, QUALITY, S, UI, X0, Y0 } from './cad-theme';

export type Faces = { regular: SkTypeface; medium: SkTypeface; semi: SkTypeface; bold: SkTypeface };
export type Res = {
  dot: SkImage;
  dotSrc: ReturnType<typeof Skia.XYWHRect>;
  f: { title: SkFont; room: SkFont; pm: SkFont; value: SkFont; stat: SkFont; unit: SkFont; cap: SkFont; label: SkFont; sub: SkFont; badge: SkFont };
};

const DOT = 32;

function makeDot() {
  const surface = Skia.Surface.MakeOffscreen(DOT, DOT) ?? Skia.Surface.Make(DOT, DOT);
  if (!surface) return null;
  const c = surface.getCanvas();
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(Skia.Shader.MakeRadialGradient({ x: DOT / 2, y: DOT / 2 }, DOT / 2, [Skia.Color('#FFFFFFFF'), Skia.Color('#FFFFFFCC'), Skia.Color('#FFFFFF00')], [0, 0.45, 1], TileMode.Clamp));
  c.drawCircle(DOT / 2, DOT / 2, DOT / 2, p);
  surface.flush();
  return surface.makeImageSnapshot().makeNonTextureImage();
}

export function makeRes(f: Faces): Res | null {
  const dot = makeDot();
  if (!dot) return null;
  return {
    dot,
    dotSrc: Skia.XYWHRect(0, 0, DOT, DOT),
    f: {
      title: Skia.Font(f.bold, 34),
      room: Skia.Font(f.medium, 25),
      pm: Skia.Font(f.medium, 30),
      value: Skia.Font(f.bold, 70),
      stat: Skia.Font(f.semi, 34),
      unit: Skia.Font(f.regular, 34),
      cap: Skia.Font(f.semi, 27),
      label: Skia.Font(f.bold, 25),
      sub: Skia.Font(f.medium, 24),
      badge: Skia.Font(f.bold, 20),
    },
  };
}

export type Geo = { w: number; h: number; s: number };

const TAU = Math.PI * 2;

// the dust: fixed attributes per particle, from a hash of its index
function hash(i: number, k: number) {
  const h = Math.sin((i + 1) * 12.9898 + k * 78.233) * 43758.5453;
  return h - Math.floor(h);
}
const P_R: number[] = [];
const P_TH: number[] = [];
const P_K: number[] = [];
const P_WA: number[] = [];
const P_WF: number[] = [];
const P_WP: number[] = [];
const P_SIZE: number[] = [];
const P_A: number[] = [];
for (let i = 0; i < DUST; i++) {
  P_R.push(Math.sqrt(hash(i, 1)) * (UI.dial.disk - 14));
  P_TH.push(hash(i, 2) * TAU);
  P_K.push(P_R[i] < UI.dial.disk * 0.55 ? 2 : 1);
  P_WA.push(4 + 10 * hash(i, 3));
  P_WF.push(1 + Math.floor(hash(i, 4) * 3));
  P_WP.push(hash(i, 5) * TAU);
  P_SIZE.push(4 + 6 * hash(i, 6) * hash(i, 6));
  P_A.push(0.3 + 0.55 * hash(i, 7));
}

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
function easeOutC(x: number) {
  'worklet';
  return 1 - Math.pow(1 - clamp01(x), 3);
}
function qualityOf(pm: number) {
  'worklet';
  for (let i = 0; i < QUALITY.length; i++) if (pm < QUALITY[i].max) return i;
  return QUALITY.length - 1;
}

// ------------------------------------------------------------------ icons (drawn, 2D)

function fan(c: SkCanvas, x: number, y: number, s: number, ang: number, color: string) {
  'worklet';
  c.save();
  c.translate(x, y);
  c.rotate(ang, 0, 0);
  for (let i = 0; i < 4; i++) {
    c.save();
    c.rotate(i * 90, 0, 0);
    const b = Skia.Path.Make();
    b.moveTo(0, 0);
    b.cubicTo(-3 * s, -6 * s, -2 * s, -15 * s, 5 * s, -16 * s);
    b.cubicTo(10 * s, -16 * s, 9 * s, -8 * s, 2 * s, -2 * s);
    b.close();
    c.drawPath(b, paint(color));
    c.restore();
  }
  c.drawCircle(0, 0, 3 * s, paint(color));
  c.restore();
}
function stopwatch(c: SkCanvas, x: number, y: number, hand: number, color: string) {
  'worklet';
  c.drawCircle(x, y + 2, 15, stroke(color, 3.4));
  c.drawLine(x - 5, y - 19, x + 5, y - 19, stroke(color, 3.4));
  c.drawLine(x, y - 19, x, y - 13, stroke(color, 3));
  const a = (hand * Math.PI) / 180 - Math.PI / 2;
  c.drawLine(x, y + 2, x + Math.cos(a) * 9, y + 2 + Math.sin(a) * 9, stroke(color, 3.2));
}
function moon(c: SkCanvas, x: number, y: number, wob: number, color: string, bg: Float32Array) {
  'worklet';
  c.save();
  c.translate(x, y);
  c.rotate(wob, 0, 0);
  c.drawCircle(0, 0, 15, paint(color));
  const bp = Skia.Paint();
  bp.setAntiAlias(true);
  bp.setColor(bg);
  c.drawCircle(8, -7, 13, bp);
  // a little cloud at its foot
  c.drawCircle(-6, 11, 6, paint(color));
  c.drawCircle(1, 13, 5, paint(color));
  c.drawCircle(-12, 14, 4.5, paint(color));
  c.restore();
}
function snowflake(c: SkCanvas, x: number, y: number) {
  'worklet';
  const p = stroke(C.white, 3);
  for (let i = 0; i < 3; i++) {
    const a = (i * Math.PI) / 3;
    const dx = Math.cos(a) * 15;
    const dy = Math.sin(a) * 15;
    c.drawLine(x - dx, y - dy, x + dx, y + dy, p);
    for (const sgn of [-1, 1]) {
      const ex = x + sgn * dx * 0.62;
      const ey = y + sgn * dy * 0.62;
      const b1 = a + (sgn > 0 ? Math.PI : 0) + 0.6;
      const b2 = a + (sgn > 0 ? Math.PI : 0) - 0.6;
      c.drawLine(ex, ey, ex + Math.cos(b1) * 6, ey + Math.sin(b1) * 6, p);
      c.drawLine(ex, ey, ex + Math.cos(b2) * 6, ey + Math.sin(b2) * 6, p);
    }
  }
}
function drop(c: SkCanvas, x: number, y: number) {
  'worklet';
  const d = Skia.Path.Make();
  d.moveTo(x, y - 15);
  d.cubicTo(x + 4, y - 8, x + 12, y - 2, x + 12, y + 5);
  d.cubicTo(x + 12, y + 12, x + 6, y + 16, x, y + 16);
  d.cubicTo(x - 6, y + 16, x - 12, y + 12, x - 12, y + 5);
  d.cubicTo(x - 12, y - 2, x - 4, y - 8, x, y - 15);
  d.close();
  c.drawPath(d, stroke(C.white, 3));
  c.save();
  c.clipPath(d, ClipOp.Intersect, true);
  c.drawRect(Skia.XYWHRect(x - 14, y + 4, 28, 14), paint(C.white));
  c.restore();
}
function dots(c: SkCanvas, x: number, y: number) {
  'worklet';
  for (let r = -2; r <= 2; r++)
    for (let q = -2; q <= 2; q++) {
      if (Math.abs(r) + Math.abs(q) > 2 && !(Math.abs(r) === 1 && Math.abs(q) === 1)) continue;
      if ((r + q) % 2 !== 0) continue;
      c.drawCircle(x + q * 6.5, y + r * 6.5, 3.6, paint(C.white));
    }
}
function power(c: SkCanvas, x: number, y: number, color: string) {
  'worklet';
  const a = Skia.Path.Make();
  a.addArc(Skia.XYWHRect(x - 15, y - 13, 30, 30), -50, 280);
  c.drawPath(a, stroke(color, 3.6));
  c.drawLine(x, y - 18, x, y - 2, stroke(color, 3.6));
}
function gear(c: SkCanvas, x: number, y: number, color: string) {
  'worklet';
  const g = Skia.Path.Make();
  for (let i = 0; i < 16; i++) {
    const a0 = (i / 16) * TAU;
    const rr = i % 2 === 0 ? 18 : 13.5;
    const a1 = ((i + 1) / 16) * TAU;
    if (i === 0) g.moveTo(x + Math.cos(a0) * rr, y + Math.sin(a0) * rr);
    g.lineTo(x + Math.cos(a0 + 0.12) * rr, y + Math.sin(a0 + 0.12) * rr);
    g.lineTo(x + Math.cos(a1 - 0.12) * rr, y + Math.sin(a1 - 0.12) * rr);
  }
  g.close();
  c.drawPath(g, paint(color));
  c.drawCircle(x, y, 6, paint(C.chip));
}
function autoBadge(c: SkCanvas, r: Res, x: number, y: number, color: string, ink: string) {
  'worklet';
  const g = Skia.Path.Make();
  for (let i = 0; i <= 24; i++) {
    const a = (i / 24) * TAU;
    const rr = 17 + (i % 2 === 0 ? 1.6 : -1.2);
    if (i === 0) g.moveTo(x + Math.cos(a) * rr, y + Math.sin(a) * rr);
    else g.lineTo(x + Math.cos(a) * rr, y + Math.sin(a) * rr);
  }
  g.close();
  c.drawPath(g, paint(color));
  text(c, 'A', x, y, r.f.badge, paint(ink), 0.5);
}

// ------------------------------------------------------------------ the dial

function drawTicks(c: SkCanvas, f: Frame) {
  'worklet';
  const D = UI.dial;
  const green = f.g;
  for (let k = 0; k < D.ticks; k++) {
    const a = (k / D.ticks) * TAU - Math.PI / 2;
    const on = clamp01(green - k + 0.5);
    // ticks near the finger stretch out while it drags
    const near = f.drag >= 0 ? Math.max(0, 1 - Math.abs(k - f.drag) / 3.5) : 0;
    // the leading green tick glows
    const lead = on > 0 && on < 1 ? 1 : Math.max(0, 1 - Math.abs(k - (green - 0.5)));
    const r0 = D.r0 - 3 * near;
    const r1 = D.r1 + 12 * near + 3 * lead * (1 - f.hush);
    const colr = mixCol(C.tick, C.green, on * (1 - f.hush), 1 - 0.45 * f.hush);
    const p = Skia.Paint();
    p.setAntiAlias(true);
    p.setStyle(PaintStyle.Stroke);
    p.setStrokeWidth(D.tickW);
    p.setStrokeCap(StrokeCap.Round);
    p.setColor(colr);
    const ca = Math.cos(a);
    const sa = Math.sin(a);
    if (lead > 0.3 && on > 0) {
      const gp = stroke(C.green, D.tickW * 3, 0.18 * lead * (1 - f.hush));
      c.drawLine(D.x + ca * r0, D.y + sa * r0, D.x + ca * r1, D.y + sa * r1, gp);
    }
    c.drawLine(D.x + ca * r0, D.y + sa * r0, D.x + ca * r1, D.y + sa * r1, p);
  }
}

function drawDust(c: SkCanvas, r: Res, f: Frame) {
  'worklet';
  const D = UI.dial;
  const srcs: ReturnType<typeof Skia.XYWHRect>[] = [];
  const xf: ReturnType<typeof Skia.RSXform>[] = [];
  const cols: Float32Array[] = [];
  const fx = f.f;
  const reach = fx * BASE_DUST * 1.15;
  const band = BASE_DUST * 0.15;
  const wob = (TAU * f.amb) / LOOP;
  const tint = col(C.dust);
  for (let i = 0; i < DUST; i++) {
    let pull = 0;
    let vis = 1;
    if (i < BASE_DUST) pull = clamp01((reach - i) / band);
    else {
      // the extra dust of a stale room: comes in from the edge as F drops below 0
      const v = clamp01((-fx * 80 - (i - BASE_DUST)) / 10);
      if (v <= 0) continue;
      pull = 1 - v;
      vis = 1;
    }
    const extra = i >= BASE_DUST;
    const r0 = P_R[i];
    let rr = r0;
    let th = P_TH[i] + P_K[i] * f.phi;
    let a = P_A[i] * vis;
    let size = P_SIZE[i];
    if (pull > 0) {
      if (f.ret === 1 || extra) {
        // drifting in from the edge
        rr = mix(r0, D.disk + 10, pull);
        a *= 1 - pull;
      } else {
        // spiralling into the core and gone
        rr = r0 * Math.pow(1 - pull, 1.5);
        th += pull * pull * TAU * 1.2;
        a *= 1 - smooth((pull - 0.65) / 0.35);
        size *= 1 - 0.5 * pull;
      }
    }
    if (a < 0.01) continue;
    const wx = P_WA[i] * Math.sin(P_WF[i] * wob + P_WP[i]);
    const wy = P_WA[i] * Math.cos(P_WF[i] * wob + P_WP[i] * 1.3);
    const x = D.x + Math.cos(th) * rr + wx * (1 - pull);
    const y = D.y + Math.sin(th) * rr + wy * (1 - pull);
    srcs.push(r.dotSrc);
    xf.push(Skia.RSXformFromRadians(size / DOT, 0, x, y, DOT / 2, DOT / 2));
    cols.push(Float32Array.of(tint[0], tint[1], tint[2], a * (1 - 0.6 * f.hush)));
  }
  if (srcs.length === 0) return;
  const ap = Skia.Paint();
  ap.setAntiAlias(true);
  c.drawAtlas(r.dot, srcs, xf, ap, BlendMode.Modulate, cols, { filter: FilterMode.Linear, mipmap: MipmapMode.None });
}

/** Faint swirls of air in the dial, stronger the faster the fan. */
function drawSwirl(c: SkCanvas, f: Frame) {
  'worklet';
  const D = UI.dial;
  const speed = clamp01((f.g - LEVELS[1].ticks) / (LEVELS[3].ticks - LEVELS[1].ticks));
  const a0 = 0.05 + 0.18 * speed;
  if (a0 * (1 - f.hush) < 0.02) return;
  for (let k = 0; k < 4; k++) {
    const base = f.phi * 2 + (k * TAU) / 4;
    const p = Skia.Path.Make();
    for (let s = 0; s <= 24; s++) {
      const u = s / 24;
      const rr = mix(D.disk * 0.9, D.disk * 0.25, u);
      const th = base + u * 2.2;
      const x = D.x + Math.cos(th) * rr;
      const y = D.y + Math.sin(th) * rr;
      if (s === 0) p.moveTo(x, y);
      else p.lineTo(x, y);
    }
    const sp = Skia.Paint();
    sp.setAntiAlias(true);
    sp.setStyle(PaintStyle.Stroke);
    sp.setStrokeWidth(3);
    sp.setStrokeCap(StrokeCap.Round);
    const m = Skia.Matrix();
    m.translate(D.x, D.y);
    m.rotate(base);
    m.translate(-D.x, -D.y);
    sp.setShader(Skia.Shader.MakeSweepGradient(D.x, D.y, [col('#FFFFFF', 0), col('#9FE8C0', a0 * (1 - f.hush)), col('#FFFFFF', 0)], [0, 0.08, 0.25], TileMode.Clamp, m));
    c.drawPath(p, sp);
  }
}

function drawDial(c: SkCanvas, r: Res, f: Frame) {
  'worklet';
  const D = UI.dial;
  drawTicks(c, f);
  // the disk
  const dp = Skia.Paint();
  dp.setAntiAlias(true);
  dp.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: D.y - D.disk }, { x: 0, y: D.y + D.disk }, [col(C.diskTop), col(C.diskBot)], null, TileMode.Clamp));
  c.drawCircle(D.x, D.y, D.disk, dp);
  c.drawCircle(D.x, D.y, D.disk, stroke(C.rim, 3));
  c.save();
  const clip = Skia.Path.Make();
  clip.addCircle(D.x, D.y, D.disk - 2);
  c.clipPath(clip, ClipOp.Intersect, true);
  drawSwirl(c, f);
  drawDust(c, r, f);
  // the moment the air turns Good, a ring of fresh air sweeps out across the disk
  const goodF = (12 - 6.5) / 13;
  const wave = f.ret === 0 ? clamp01((f.f - goodF) / 0.14) : 0;
  if (wave > 0 && wave < 1) {
    const rr = mix(30, D.disk, easeOutC(wave));
    const wp = Skia.Paint();
    wp.setAntiAlias(true);
    wp.setStyle(PaintStyle.Stroke);
    wp.setStrokeWidth(mix(26, 6, wave));
    wp.setShader(Skia.Shader.MakeRadialGradient({ x: D.x, y: D.y }, rr + 16, [col(QUALITY[0].color, 0), col(QUALITY[0].color, 0.35 * (1 - wave)), col(QUALITY[0].color, 0)], [0.6, 0.9, 1], TileMode.Clamp));
    c.drawCircle(D.x, D.y, rr, wp);
  }
  // the air quality cap
  const pm = pmOf(f.f);
  const q = qualityOf(pm);
  const lo = q > 0 ? QUALITY[q - 1].max : -10;
  const hi = QUALITY[q].max;
  // crossfade over half a unit either side of a band's edge
  const kLo = q > 0 ? smooth((pm - lo) / 0.8 + 0.5) : 1;
  const kHi = q < QUALITY.length - 1 ? smooth((hi - pm) / 0.8 + 0.5) : 1;
  const capCol = kLo < 1 ? mixCol(QUALITY[q - 1].color, QUALITY[q].color, kLo) : kHi < 1 ? mixCol(QUALITY[q + 1].color, QUALITY[q].color, kHi) : col(QUALITY[q].color);
  const cp = Skia.Paint();
  cp.setAntiAlias(true);
  cp.setColor(Float32Array.of(capCol[0], capCol[1], capCol[2], 1 - 0.55 * f.hush));
  c.drawCircle(D.x, UI.cap.cy, UI.cap.r, cp);
  // its word slides: the old one up, the new one in from below
  c.save();
  const cap = Skia.Path.Make();
  cap.addCircle(D.x, UI.cap.cy, UI.cap.r);
  c.clipPath(cap, ClipOp.Intersect, true);
  const tp = (a: number) => paint(C.capText, a);
  if (kLo < 1) {
    text(c, QUALITY[q - 1].word, D.x, UI.cap.text - 26 * kLo, r.f.cap, tp(1 - kLo), 0.5);
    text(c, QUALITY[q].word, D.x, UI.cap.text + 26 * (1 - kLo), r.f.cap, tp(kLo), 0.5);
  } else if (kHi < 1) {
    text(c, QUALITY[q + 1].word, D.x, UI.cap.text + 26 * kHi, r.f.cap, tp(1 - kHi), 0.5);
    text(c, QUALITY[q].word, D.x, UI.cap.text - 26 * (1 - kHi), r.f.cap, tp(kHi), 0.5);
  } else text(c, QUALITY[q].word, D.x, UI.cap.text, r.f.cap, tp(1), 0.5);
  c.restore();
  c.restore();
  // a gentle breath of light in the core while dust is being pulled in
  const pulling = f.ret === 0 && f.f > 0.01 && f.f < F_HIGH + 0.3 ? clamp01(f.f / 0.15) * clamp01((F_HIGH + 0.25 - f.f) / 0.25) : 0;
  if (pulling > 0.01) {
    const gp = Skia.Paint();
    gp.setShader(Skia.Shader.MakeRadialGradient({ x: D.x, y: D.y }, 60, [col('#9FE8C0', 0.12 * pulling), col('#9FE8C0', 0)], null, TileMode.Clamp));
    c.drawCircle(D.x, D.y, 60, gp);
  }
  c.drawLine(D.x - D.disk + 2, UI.divider, D.x + D.disk - 2, UI.divider, stroke(C.divider, 2));
  // PM 2.5 and its value, rolling
  text(c, COPY.pm, D.x, UI.pm.y, r.f.pm, paint(C.label));
  const v = pm;
  const n0 = Math.floor(v);
  const k = smooth(v - n0);
  const s0 = String(n0).padStart(3, '0');
  const s1 = String(n0 + 1).padStart(3, '0');
  const w = r.f.value.measureText(s0).width;
  let x = D.x - w / 2;
  const H = 74;
  const vp = paint(C.white, 1 - 0.5 * f.hush);
  c.save();
  c.clipRect(Skia.XYWHRect(D.x - 160, UI.pm.value - 40, 320, 80), ClipOp.Intersect, true);
  for (let i = 0; i < 3; i++) {
    const a = s0[i];
    const b = s1[i];
    const cw = r.f.value.measureText(a).width;
    if (a === b || k < 0.001) text(c, a, x, UI.pm.value, r.f.value, vp);
    else {
      text(c, a, x, UI.pm.value + k * H, r.f.value, vp);
      text(c, b, x, UI.pm.value - (1 - k) * H, r.f.value, vp);
    }
    x += cw;
  }
  c.restore();
  // stats
  const St = UI.stats;
  for (let i = 0; i < 3; i++) {
    const sx = St.x[i];
    const vw = r.f.stat.measureText(COPY.stats[i].v).width;
    const uw = r.f.unit.measureText(COPY.stats[i].u).width;
    text(c, COPY.stats[i].v, sx - (vw + uw) / 2, St.v, r.f.stat, paint(C.white));
    text(c, COPY.stats[i].u, sx - (vw + uw) / 2 + vw, St.v, r.f.unit, paint(C.unit));
    if (i === 0) snowflake(c, sx, St.icon);
    else if (i === 1) drop(c, sx, St.icon);
    else dots(c, sx, St.icon);
  }
  for (let i = 0; i < 2; i++) c.drawLine(St.div[i], St.divY[0], St.div[i], St.divY[1], stroke(C.divider, 2));
}

function drawChips(c: SkCanvas, r: Res, f: Frame) {
  'worklet';
  const P = UI.chips;
  const lvl = levelAt(Math.max(0, f.g));
  for (let i = 0; i < 3; i++) {
    const x = P.x[i];
    const press = f.press === i ? f.pressK : 0;
    const s = 1 - 0.04 * press;
    c.save();
    c.translate(x, P.y);
    c.scale(s, s);
    let bg: string = C.chip;
    if (i === 0) bg = C.blue;
    if (i === 2) bg = C.purple;
    const on = i === 0 ? f.power : i === 1 ? f.timer : f.scenes;
    const fill = i === 1 ? mixCol(C.chip, '#2A2A2A', on) : mixCol(C.chip, bg, on);
    const pp = Skia.Paint();
    pp.setAntiAlias(true);
    pp.setColor(fill);
    c.drawRRect(rrect(-P.w / 2, -P.h / 2, P.w, P.h, P.h / 2), pp);
    if (i === 0) {
      fan(c, -32, 0, 1.25, ((f.phi * 4) * 180) / Math.PI, C.white);
      const up = Skia.Path.Make();
      up.moveTo(28, -9);
      up.lineTo(36, -17);
      up.lineTo(44, -9);
      up.close();
      const dn = Skia.Path.Make();
      dn.moveTo(28, 9);
      dn.lineTo(36, 17);
      dn.lineTo(44, 9);
      dn.close();
      c.drawPath(up, paint('#FFFFFF', 0.55));
      c.drawPath(dn, paint('#FFFFFF', 0.55));
    } else if (i === 1) {
      const hand = f.timer > 0 ? Math.min(360, f.timerAt * 240) * f.timer : 0;
      stopwatch(c, 0, 0, hand, C.white);
    } else {
      const wob = 6 * Math.sin((TAU * 2 * f.amb) / LOOP);
      moon(c, -25, 0, wob, C.white, mixCol(C.chip, C.purple, on));
      const ch = Skia.Path.Make();
      ch.moveTo(38, -8);
      ch.lineTo(45, 0);
      ch.lineTo(38, 8);
      c.drawPath(ch, stroke('#FFFFFF', 3.2));
    }
    c.restore();
    text(c, COPY.chips[i], x, P.label, r.f.label, paint(C.white), 0.5);
  }
  // the speed's name rolls when the level changes
  const near = f.g;
  const gp = paint(C.sub);
  let shown = false;
  for (let l = 0; l < LEVELS.length; l++) {
    const d = (near - LEVELS[l].ticks) / Math.max(1, l < LEVELS.length - 1 ? LEVELS[l + 1].ticks - LEVELS[l].ticks : 9);
    const a = clamp01(1 - Math.abs(near - LEVELS[l].ticks) / 4.5);
    if (a < 0.02) continue;
    shown = true;
    text(c, f.power < 0.5 ? 'Off' : LEVELS[l].name, P.x[0], P.sub - d * 30, r.f.sub, paint(C.sub, a), 0.5);
  }
  if (!shown) text(c, LEVELS[lvl].name, P.x[0], P.sub, r.f.sub, gp, 0.5);
  text(c, f.timer > 0.5 ? COPY.timerOn : COPY.timerOff, P.x[1], P.sub, r.f.sub, gp, 0.5);
  text(c, f.scenes > 0.5 ? 'On' : 'Off', P.x[2], P.sub, r.f.sub, gp, 0.5);
}

function drawBottom(c: SkCanvas, r: Res, f: Frame) {
  'worklet';
  const B = UI.bottom;
  const P = UI.chips;
  for (let i = 0; i < 3; i++) {
    const x = B.x[i];
    const press = f.press === 3 + i ? f.pressK : 0;
    const s = 1 - 0.04 * press;
    c.save();
    c.translate(x, B.y);
    c.scale(s, s);
    if (i === 1) {
      const on = f.power;
      const pp = Skia.Paint();
      pp.setAntiAlias(true);
      pp.setColor(mixCol(C.chip, C.power, on));
      c.drawRRect(rrect(-P.w / 2, -P.h / 2, P.w, P.h, P.h / 2), pp);
      power(c, 0, 0, on > 0.5 ? '#151515' : C.white);
    } else if (i === 0) {
      const pp = Skia.Paint();
      pp.setAntiAlias(true);
      pp.setColor(mixCol(C.chip, '#E9E9E9', f.auto));
      c.drawRRect(rrect(-P.w / 2, -P.h / 2, P.w, P.h, P.h / 2), pp);
      autoBadge(c, r, 0, 0, f.auto > 0.5 ? '#151515' : C.white, f.auto > 0.5 ? '#E9E9E9' : C.chip);
    } else {
      c.drawRRect(rrect(-P.w / 2, -P.h / 2, P.w, P.h, P.h / 2), paint(C.chip));
      gear(c, 0, 0, C.white);
    }
    c.restore();
  }
}

export function drawScene(c: SkCanvas, r: Res, g: Geo, f: Frame) {
  'worklet';
  c.drawColor(col(C.bg));
  c.save();
  c.scale(g.s, g.s);
  c.translate(0, Y0);
  c.scale(S, S);
  c.translate(-X0, 0);
  // header
  const cx = 600;
  const chev = Skia.Path.Make();
  chev.moveTo(X0 + 52, UI.header.y - 16);
  chev.lineTo(X0 + 36, UI.header.y);
  chev.lineTo(X0 + 52, UI.header.y + 16);
  c.drawPath(chev, stroke(C.white, 4));
  text(c, COPY.title, cx, UI.header.y - 8, r.f.title, paint(C.white), 0.5);
  text(c, COPY.room, cx, UI.header.sub + 2, r.f.room, paint(C.label), 0.5);
  for (let i = 0; i < 3; i++) c.drawCircle(X0 + 670 - 64 + i * 12, UI.header.y, 3.6, paint(C.white));
  drawDial(c, r, f);
  drawChips(c, r, f);
  drawBottom(c, r, f);
  c.restore();
}
