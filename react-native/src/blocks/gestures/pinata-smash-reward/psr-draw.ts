// Piñata Smash Reward — drawing one frame onto a Skia canvas (UI thread).
//
// The piñata is a textured mesh (plan U1): a 12 × 12 grid over the art whose vertices go through
// yaw (an ellipsoid depth turned about the vertical axis), squash along the hit, the press-in and
// the roll about the ribbon's attach point. The tail is its own mesh and bends along its length.
// Loot and paper tiles are one atlas; confetti, streamers, ribbon, pill, token and header are
// drawn in code.

import {
  BlendMode,
  ClipOp,
  FilterMode,
  MipmapMode,
  PaintStyle,
  type SkCanvas,
  type SkFont,
  type SkImage,
  type SkPoint,
  type SkRect,
  type SkShader,
  type SkVertices,
  Skia,
  StrokeCap,
  StrokeJoin,
  TileMode,
  VertexMode,
} from '@shopify/react-native-skia';

import { bulbLevel, clamp01, type Frame, hash, KIND_W, K_GLOW, seg } from './psr-motion';
import { ART, C, COPY, L } from './psr-theme';

export type ArtLayout = {
  figure: { x: number; y: number; w: number; h: number };
  attach: { x: number; y: number };
  flank: { x: number; y: number };
  tailRoot: { x: number; y: number };
  tailBox: { x: number; y: number; w: number; h: number };
  bulbs: { x: number; y: number; r: number; color: string }[];
};

export type Res = {
  bodyShader: SkShader;
  burstShader: SkShader;
  crack1Shader: SkShader | null;
  crack2Shader: SkShader | null;
  tailShader: SkShader;
  atlas: SkImage;
  rects: SkRect[];
  pivots: { cx: number; cy: number; w: number }[];
  art: ArtLayout;
  k: number; // pt per art px
  ox: number; // pt of the art canvas origin at rest
  oy: number;
  cx: number; // body centre (pt, rest)
  cy: number;
  rx: number;
  ry: number;
  mesh: { uv: SkPoint[]; idx: number[]; n: number; u0: number; v0: number; du: number; dv: number };
  tail: { uv: SkPoint[]; idx: number[]; n: number; u0: number; v0: number; du: number; dv: number };
  label: SkFont;
  title: SkFont;
  chipFont: SkFont;
  labelTextW: number;
  doneTextW: number;
  titleW: number;
  chipW: number;
  bulbColors: Float32Array[];
};

export type View = { s: number; dy: number; top: number; w: number; h: number };

const GRID = 12;

function makeGrid(u0: number, v0: number, u1: number, v1: number) {
  const n = GRID;
  const du = (u1 - u0) / n;
  const dv = (v1 - v0) / n;
  const uv: SkPoint[] = [];
  for (let j = 0; j <= n; j++) for (let i = 0; i <= n; i++) uv.push(Skia.Point(u0 + i * du, v0 + j * dv));
  const idx: number[] = [];
  for (let j = 0; j < n; j++)
    for (let i = 0; i < n; i++) {
      const a = j * (n + 1) + i;
      idx.push(a, a + 1, a + n + 2, a, a + n + 2, a + n + 1);
    }
  return { uv, idx, n, u0, v0, du, dv };
}

function textW(font: SkFont, s: string) {
  const ids = font.getGlyphIDs(s);
  const ws = font.getGlyphWidths(ids);
  let w = 0;
  for (const x of ws) w += x;
  return w;
}

export function makeRes(input: {
  body: SkImage;
  burst: SkImage;
  crack1: SkImage | null;
  crack2: SkImage | null;
  tail: SkImage;
  atlas: SkImage;
  atlasJson: Record<string, { x: number; y: number; w: number; h: number; cx: number; cy: number }>;
  names: readonly string[];
  art: ArtLayout;
  label: SkFont;
  title: SkFont;
  chipFont: SkFont;
}): Res {
  const sh = (img: SkImage) => img.makeShaderOptions(TileMode.Decal, TileMode.Decal, FilterMode.Linear, MipmapMode.Linear);
  const art = input.art;
  const k = ART.k;
  const ox = ART.ox;
  const oy = ART.oy;
  const pad = 14;
  const f = art.figure;
  const rects: SkRect[] = [];
  const pivots: { cx: number; cy: number; w: number }[] = [];
  input.names.forEach((name, i) => {
    const r = input.atlasJson[name] ?? input.atlasJson[Object.keys(input.atlasJson)[0]];
    rects.push(Skia.XYWHRect(r.x, r.y, r.w, r.h));
    pivots.push({ cx: r.cx, cy: r.cy, w: KIND_W[i] / r.w });
  });
  return {
    bodyShader: sh(input.body),
    burstShader: sh(input.burst),
    crack1Shader: input.crack1 ? sh(input.crack1) : null,
    crack2Shader: input.crack2 ? sh(input.crack2) : null,
    tailShader: sh(input.tail),
    atlas: input.atlas,
    rects,
    pivots,
    art,
    k,
    ox,
    oy,
    cx: ox + (f.x + f.w / 2) * k,
    cy: oy + (f.y + f.h * 0.55) * k,
    rx: (f.w / 2) * k,
    ry: (f.h / 2) * k,
    mesh: makeGrid(f.x - pad, f.y - pad, f.x + f.w + pad, f.y + f.h + pad),
    tail: makeGrid(Math.max(0, art.tailBox.x - 40), Math.max(0, art.tailBox.y - 40), Math.min(1080, art.tailBox.x + art.tailBox.w + 40), Math.min(1140, art.tailBox.y + art.tailBox.h + 40)),
    label: input.label,
    title: input.title,
    chipFont: input.chipFont,
    labelTextW: textW(input.label, COPY.label),
    doneTextW: textW(input.label, COPY.done),
    titleW: textW(input.title, COPY.title),
    chipW: textW(input.chipFont, '000'),
    bulbColors: art.bulbs.map((b) => Skia.Color(b.color)),
  };
}

/** The header's chip: where its coin sits (pt, reference space), for the tokens to aim at. */
export function chipCoin(res: Res) {
  const ch = L.chip;
  const w = ch.padL + ch.coin * 2 + ch.gap + res.chipW + ch.padR;
  const x0 = 402 - ch.right - w;
  return { x: x0 + ch.padL + ch.coin, y: ch.y + ch.h / 2, x0, w };
}

// ---------------------------------------------------------------- placement (canvas px → pt)

type Pose = {
  ax: number;
  ay: number;
  cr: number;
  sr: number;
  cy: number;
  sy: number;
  sq: number;
  sqc: number;
  sqs: number;
  press: number;
  A0x: number;
  A0y: number;
};

function poseOf(res: Res, f: Frame): Pose {
  'worklet';
  return {
    ax: f.ax,
    ay: f.ay,
    cr: Math.cos(f.roll),
    sr: Math.sin(f.roll),
    cy: Math.cos(f.yaw),
    sy: Math.sin(f.yaw),
    sq: f.sq,
    sqc: Math.cos(f.sqAng),
    sqs: Math.sin(f.sqAng),
    press: 1 - 0.03 * f.press,
    A0x: res.ox + res.art.attach.x * res.k,
    A0y: res.oy + res.art.attach.y * res.k,
  };
}

const DEPTH = 26;

/** Canvas px (u, v) → screen pt, plus a shade for the vertex (yaw lighting). */
function place(res: Res, p: Pose, u: number, v: number, out: { x: number; y: number; shade: number }) {
  'worklet';
  const x = res.ox + u * res.k;
  const y = res.oy + v * res.k;
  let dx = x - res.cx;
  let dy = y - res.cy;
  const nx = dx / res.rx;
  const ny = dy / res.ry;
  const dd = 1 - nx * nx - ny * ny;
  const depth = dd > 0 ? DEPTH * Math.sqrt(dd) : 0;
  const shade = 1 - 0.22 * p.sy * Math.max(-1, Math.min(1, nx));
  dx = dx * p.cy + depth * p.sy;
  // squash along the hit, a little stretch across it
  if (p.sq !== 0) {
    const a = dx * p.sqc + dy * p.sqs;
    const b = -dx * p.sqs + dy * p.sqc;
    const a2 = a * (1 - p.sq);
    const b2 = b * (1 + p.sq * 0.5);
    dx = a2 * p.sqc - b2 * p.sqs;
    dy = a2 * p.sqs + b2 * p.sqc;
  }
  dx *= p.press;
  dy *= p.press;
  // roll about the attach point, then carry the attach point to where the ribbon holds it
  const rx = res.cx + dx - p.A0x;
  const ry = res.cy + dy - p.A0y;
  out.x = p.ax + rx * p.cr - ry * p.sr;
  out.y = p.ay + rx * p.sr + ry * p.cr;
  out.shade = shade;
}

function meshVerts(res: Res, p: Pose, m: Res['mesh'], bend: number, root: { x: number; y: number } | null) {
  'worklet';
  const n = m.n;
  const pos: SkPoint[] = [];
  const cols: Float32Array[] = [];
  const o = { x: 0, y: 0, shade: 1 };
  for (let j = 0; j <= n; j++)
    for (let i = 0; i <= n; i++) {
      let u = m.u0 + i * m.du;
      let v = m.v0 + j * m.dv;
      if (root && bend !== 0) {
        // the tail bends more the further it is from its root
        const ddx = u - root.x;
        const ddy = v - root.y;
        const dist = Math.sqrt(ddx * ddx + ddy * ddy);
        const a = bend * Math.min(1, dist / 300);
        const ca = Math.cos(a);
        const sa = Math.sin(a);
        u = root.x + ddx * ca - ddy * sa;
        v = root.y + ddx * sa + ddy * ca;
      }
      place(res, p, u, v, o);
      pos.push(Skia.Point(o.x, o.y));
      const g = Math.max(0.6, Math.min(1, o.shade));
      cols.push(Float32Array.of(g, g, g, 1));
    }
  return Skia.MakeVertices(VertexMode.Triangles, pos, m.uv, cols, m.idx);
}

function drawMesh(canvas: SkCanvas, vx: SkVertices, shader: SkShader, alpha: number) {
  'worklet';
  if (alpha <= 0.002) return;
  const paint = Skia.Paint();
  paint.setShader(shader);
  paint.setAlphaf(alpha);
  canvas.drawVertices(vx, BlendMode.Modulate, paint);
}

// ---------------------------------------------------------------- pieces

function drawRibbon(canvas: SkCanvas, f: Frame, topX: number) {
  'worklet';
  const path = Skia.PathBuilder.Make();
  const x0 = topX;
  const y0 = -30;
  const x1 = f.ax;
  const y1 = f.ay;
  path.moveTo(x0, y0);
  const mx = (x0 + x1) / 2 - f.bow * 2;
  const my = (y0 + y1) / 2;
  path.quadTo(mx, my, x1, y1);
  const p = Skia.Paint();
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeCap(StrokeCap.Butt);
  p.setAntiAlias(true);
  p.setColor(Skia.Color(C.ribbonEdge));
  p.setStrokeWidth(L.ribbonW);
  canvas.drawPath(path.build(), p);
  p.setColor(Skia.Color(C.ribbon));
  p.setStrokeWidth(L.ribbonW - 1.6);
  canvas.drawPath(path.build(), p);
  p.setColor(Skia.Color(C.ribbonMid));
  p.setAlphaf(0.75);
  p.setStrokeWidth(L.ribbonMidW);
  canvas.drawPath(path.build(), p);
}

function drawToken(canvas: SkCanvas, x: number, y: number, s: number, a: number) {
  'worklet';
  const r = L.token * s;
  if (r < 0.5 || a <= 0.01) return;
  const p = Skia.Paint();
  p.setAntiAlias(true);
  const top = Skia.Color(C.tokenRim);
  const mid = Skia.Color(C.token);
  const low = Skia.Color(C.tokenDeep);
  p.setShader(
    Skia.Shader.MakeLinearGradient(Skia.Point(x - r * 0.5, y - r), Skia.Point(x + r * 0.4, y + r), [top, mid, low], [0, 0.45, 1], TileMode.Clamp),
  );
  p.setAlphaf(a);
  canvas.drawCircle(x, y, r, p);
  const inner = Skia.Paint();
  inner.setAntiAlias(true);
  inner.setColor(Skia.Color(C.token));
  inner.setAlphaf(a);
  canvas.drawCircle(x, y, r * 0.84, inner);
  // highlight
  const hl = Skia.Paint();
  hl.setAntiAlias(true);
  hl.setColor(Skia.Color('#FFFFFF'));
  hl.setAlphaf(0.18 * a);
  canvas.drawOval(Skia.XYWHRect(x - r * 0.62, y - r * 0.8, r * 0.9, r * 0.5), hl);
  // two stacked chevrons
  const st = Skia.Paint();
  st.setAntiAlias(true);
  st.setStyle(PaintStyle.Stroke);
  st.setStrokeWidth(2.2 * s);
  st.setStrokeCap(StrokeCap.Round);
  st.setStrokeJoin(StrokeJoin.Round);
  st.setColor(Skia.Color('#FFFFFF'));
  st.setAlphaf(0.9 * a);
  const w = r * 0.42;
  const h = r * 0.26;
  const path = Skia.PathBuilder.Make();
  for (let i = 0; i < 2; i++) {
    const cy = y - r * 0.14 + i * r * 0.34;
    path.moveTo(x - w, cy + h * 0.5);
    path.lineTo(x, cy - h * 0.5);
    path.lineTo(x + w, cy + h * 0.5);
  }
  canvas.drawPath(path.build(), st);
}

function bolt(x: number, y: number, s: number) {
  'worklet';
  const p = Skia.PathBuilder.Make();
  const P = [
    [8.2, 0],
    [0.6, 10],
    [6, 10],
    [4.6, 17],
    [12.4, 6.6],
    [7.1, 6.6],
  ];
  p.moveTo(x + P[0][0] * s, y + P[0][1] * s);
  for (let i = 1; i < P.length; i++) p.lineTo(x + P[i][0] * s, y + P[i][1] * s);
  p.close();
  return p.build();
}

function mixColor(a: Float32Array, b: Float32Array, t: number) {
  'worklet';
  return Float32Array.of(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t, 1);
}

function drawPill(canvas: SkCanvas, res: Res, f: Frame) {
  'worklet';
  const P = L.pill;
  const w = f.pillW;
  const h = P.h + (L.barH - P.h) * f.wB;
  const x = P.cx - w / 2 + f.shake;
  const y = P.y + (P.h - h) / 2;
  const cLabel = Skia.Color(C.label);
  const cBar = Skia.Color(C.bar);
  const cDone = Skia.Color(C.done);
  const sum = Math.max(1e-4, f.wL + f.wB + f.wD);
  let col = mixColor(cLabel, cBar, f.wB / sum);
  col = mixColor(col, cDone, f.wD / sum);
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(col);
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y, w, h), P.r, P.r), p);
  canvas.save();
  canvas.clipRRect(Skia.RRectXY(Skia.XYWHRect(x, y, w, h), P.r, P.r), ClipOp.Intersect, true);
  const cx = P.cx + f.shake;
  const base = P.y + P.h / 2 + L.font * 0.35;
  // label: text + bolt, centred as a group
  const la = clamp01((f.wL - 0.55) / 0.45);
  if (la > 0.01) {
    const gw = res.labelTextW + L.bolt.gap + L.bolt.w;
    const tx = cx - gw / 2;
    const tp = Skia.Paint();
    tp.setAntiAlias(true);
    tp.setColor(Skia.Color(C.white));
    tp.setAlphaf(la);
    canvas.drawText(COPY.label, tx, base, tp, res.label);
    const bp = Skia.Paint();
    bp.setAntiAlias(true);
    bp.setStyle(PaintStyle.Stroke);
    bp.setStrokeWidth(L.bolt.stroke);
    bp.setStrokeJoin(StrokeJoin.Round);
    bp.setColor(Skia.Color(C.white));
    bp.setAlphaf(la);
    canvas.drawPath(bolt(tx + res.labelTextW + L.bolt.gap, P.y + P.h / 2 - L.bolt.h / 2, 1), bp);
  }
  // bar: track and fill
  const ba = clamp01((f.wB - 0.4) / 0.6);
  if (ba > 0.01) {
    const tw = L.track.w;
    const th = L.track.h;
    const tx = cx - tw / 2;
    const ty = P.y + P.h / 2 - th / 2;
    const tp = Skia.Paint();
    tp.setAntiAlias(true);
    tp.setColor(Skia.Color(C.track));
    tp.setAlphaf(ba);
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(tx, ty, tw, th), th / 2, th / 2), tp);
    const fw = Math.min(tw, Math.max(L.fillMin, tw * f.fill));
    if (f.fillGlow > 0.01) {
      const gp = Skia.Paint();
      gp.setAntiAlias(true);
      gp.setColor(Skia.Color(C.fillGlow));
      gp.setAlphaf(0.55 * f.fillGlow * ba);
      gp.setMaskFilter(Skia.MaskFilter.MakeBlur(1, 2, true));
      canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(tx - 1, ty - 1, fw + 2, th + 2), th / 2 + 1, th / 2 + 1), gp);
    }
    const fp = Skia.Paint();
    fp.setAntiAlias(true);
    fp.setColor(Skia.Color(C.fill));
    fp.setAlphaf(ba);
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(tx, ty, fw, th), th / 2, th / 2), fp);
  }
  // done
  // the text waits until the pill has reached 98 % of its width, then fades in (f.doneText)
  const da = Math.min(clamp01((f.wD - 0.5) / 0.5), f.doneText);
  if (da > 0.01) {
    const tp = Skia.Paint();
    tp.setAntiAlias(true);
    tp.setColor(Skia.Color(C.doneInk));
    tp.setAlphaf(da);
    canvas.drawText(COPY.done, cx - res.doneTextW / 2, base, tp, res.label);
  }
  canvas.restore();
  // U7: six violet sparks out of the bar's right end on the burst
  if (f.sparks >= 0 && f.sparks < 0.5) {
    const sp = Skia.Paint();
    sp.setAntiAlias(true);
    sp.setStyle(PaintStyle.Stroke);
    sp.setStrokeCap(StrokeCap.Round);
    sp.setStrokeWidth(2);
    sp.setColor(Skia.Color(C.fill));
    const ex = P.cx + 60;
    const ey = P.y + P.h / 2;
    const u = f.sparks / 0.5;
    for (let i = 0; i < 6; i++) {
      const a = (-70 + i * 28) * (Math.PI / 180);
      const r0 = 6 + 34 * u;
      const r1 = r0 + 9 * (1 - u);
      sp.setAlphaf(1 - u);
      canvas.drawLine(ex + Math.cos(a) * r0, ey + Math.sin(a) * r0, ex + Math.cos(a) * r1, ey + Math.sin(a) * r1, sp);
    }
  }
}

function chipCoinW(res: Res) {
  'worklet';
  const ch = L.chip;
  const w = ch.padL + ch.coin * 2 + ch.gap + res.chipW + ch.padR;
  return { x0: 402 - ch.right - w, w };
}

function drawHeader(canvas: SkCanvas, res: Res, f: Frame, top: number) {
  'worklet';
  const tp = Skia.Paint();
  tp.setAntiAlias(true);
  tp.setColor(Skia.Color(C.ink));
  canvas.drawText(COPY.title, L.title.x, L.title.base + top, tp, res.title);
  const cc = chipCoinW(res);
  const ch = L.chip;
  const b = 1 + 0.08 * f.chipBump;
  canvas.save();
  canvas.translate(cc.x0 + cc.w / 2, ch.y + ch.h / 2 + top);
  canvas.scale(b, b);
  canvas.translate(-(cc.x0 + cc.w / 2), -(ch.y + ch.h / 2 + top));
  const cp = Skia.Paint();
  cp.setAntiAlias(true);
  cp.setColor(Skia.Color(C.chip));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(cc.x0, ch.y + top, cc.w, ch.h), ch.r, ch.r), cp);
  drawToken(canvas, cc.x0 + ch.padL + ch.coin, ch.y + ch.h / 2 + top, ch.coin / L.token, 1);
  const np = Skia.Paint();
  np.setAntiAlias(true);
  np.setColor(Skia.Color(C.chipInk));
  const n = Math.round(f.count);
  canvas.drawText(`${n}`, cc.x0 + ch.padL + ch.coin * 2 + ch.gap, ch.y + ch.h / 2 + top + ch.size * 0.36, np, res.chipFont);
  canvas.restore();
}

const CONFETTI = C.confetti.map((c) => Skia.Color(c));

/** All confetti in one vertices draw: each piece a rotated quad (strip or diamond) that tumbles. */
function drawConfetti(canvas: SkCanvas, f: Frame) {
  'worklet';
  const pos: SkPoint[] = [];
  const cols: Float32Array[] = [];
  const idx: number[] = [];
  for (let i = 0; i < f.conf.length; i++) {
    const c = f.conf[i];
    if (c.a <= 0.01) continue;
    const ca = Math.cos(c.rot);
    const sa = Math.sin(c.rot);
    const hw = c.w / 2;
    const hh = c.h / 2;
    const k = pos.length;
    const pts = c.diamond ? [0, -hh, hw, 0, 0, hh, -hw, 0] : [-hw, -hh, hw, -hh, hw, hh, -hw, hh];
    const base = CONFETTI[c.c];
    const col = Float32Array.of(base[0], base[1], base[2], c.a);
    for (let q = 0; q < 4; q++) {
      const x = pts[2 * q];
      const y = pts[2 * q + 1];
      pos.push(Skia.Point(c.x + x * ca - y * sa, c.y + x * sa + y * ca));
      cols.push(col);
    }
    idx.push(k, k + 1, k + 2, k, k + 2, k + 3);
  }
  if (pos.length === 0) return;
  const vx = Skia.MakeVertices(VertexMode.Triangles, pos, null, cols, idx);
  canvas.drawVertices(vx, BlendMode.Dst, Skia.Paint());
}

function drawStreamers(canvas: SkCanvas, f: Frame) {
  'worklet';
  if (f.streamers < 0) return;
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(3);
  p.setStrokeCap(StrokeCap.Round);
  for (let i = 0; i < 10; i++) {
    const delay = hash(i + 40) * 0.5;
    const tau = f.streamers - delay;
    if (tau < 0) continue;
    const v = 180 + 80 * hash(i + 41);
    const x0 = 24 + (354 * (i + hash(i + 42) * 0.8)) / 10;
    const y0 = -60 + v * tau;
    if (y0 > 900) continue;
    const sway = 20 * Math.sin(tau * 2.4 + i);
    const path = Skia.PathBuilder.Make();
    for (let s = 0; s <= 12; s++) {
      const q = s / 12;
      const x = x0 + sway + 7 * Math.sin(q * Math.PI * 3 + tau * 9 + i);
      const y = y0 + q * 46;
      if (s === 0) path.moveTo(x, y);
      else path.lineTo(x, y);
    }
    p.setColor(Skia.Color(C.streamers[i % 2]));
    p.setAlphaf(f.fade * (1 - seg(y0, 780, 860)));
    canvas.drawPath(path.build(), p);
  }
}

function drawAtlasPieces(canvas: SkCanvas, res: Res, f: Frame) {
  'worklet';
  const n = f.loot.length;
  if (n === 0) return;
  const srcs: SkRect[] = [];
  const xs = [];
  const cols: Float32Array[] = [];
  for (let i = 0; i < n; i++) {
    const l = f.loot[i];
    if (l.a <= 0.01) continue;
    const pv = res.pivots[l.k];
    const s = pv.w * l.s * Math.max(0.25, Math.abs(l.sx));
    const sc = s * Math.cos(l.rot);
    const ss = s * Math.sin(l.rot);
    xs.push(Skia.RSXform(sc, ss, l.x - (sc * pv.cx - ss * pv.cy), l.y - (ss * pv.cx + sc * pv.cy)));
    srcs.push(res.rects[l.k]);
    cols.push(Float32Array.of(l.a, l.a, l.a, l.a));
  }
  if (xs.length === 0) return;
  const p = Skia.Paint();
  canvas.drawAtlas(res.atlas, srcs, xs, p, BlendMode.Modulate, cols, { filter: FilterMode.Linear, mipmap: MipmapMode.Linear });
}

type Glow = { x: number; y: number; r: number; c: Float32Array; a: number };
const WHITE = Float32Array.of(1, 1, 1, 1);
const HOLE = Float32Array.of(1, 0.83, 0.42, 1);

/** Soft round glows from the atlas's glow sprite, tinted per glow, in one draw. */
function drawGlows(canvas: SkCanvas, res: Res, gs: Glow[], mode: BlendMode) {
  'worklet';
  const srcs: SkRect[] = [];
  const xs = [];
  const cols: Float32Array[] = [];
  const src = res.rects[K_GLOW];
  const pv = res.pivots[K_GLOW];
  for (let i = 0; i < gs.length; i++) {
    const g = gs[i];
    if (g.a <= 0.003 || g.r <= 0.5) continue;
    const s = g.r / pv.cx;
    xs.push(Skia.RSXform(s, 0, g.x - s * pv.cx, g.y - s * pv.cy));
    srcs.push(src);
    const a = Math.min(1, g.a);
    cols.push(Float32Array.of(g.c[0], g.c[1], g.c[2], a));
  }
  if (xs.length === 0) return;
  const p = Skia.Paint();
  p.setBlendMode(mode);
  canvas.drawAtlas(res.atlas, srcs, xs, p, BlendMode.Modulate, cols, { filter: FilterMode.Linear, mipmap: MipmapMode.Linear });
}

// ---------------------------------------------------------------- the scene

export function drawScene(canvas: SkCanvas, res: Res, f: Frame, view: View, reveal: number) {
  'worklet';
  canvas.drawColor(Skia.Color(C.page));
  canvas.save();
  canvas.scale(view.s, view.s);
  canvas.translate(0, view.dy);

  // stage punch-in (U2), about the flank
  canvas.save();
  if (f.stage !== 1) {
    canvas.translate(f.stageX, f.stageY);
    canvas.scale(f.stage, f.stage);
    canvas.translate(-f.stageX, -f.stageY);
  }

  // floor shadow (U9)
  {
    const sp = Skia.Paint();
    sp.setAntiAlias(true);
    sp.setColor(Skia.Color(C.shadow));
    sp.setAlphaf(0.05 * (1 - 0.5 * f.shadowLift) * reveal);
    sp.setMaskFilter(Skia.MaskFilter.MakeBlur(1, 6, true));
    const sw = L.shadow.w * (1 - 0.4 * f.shadowLift);
    const sh = L.shadow.h * (1 - 0.4 * f.shadowLift);
    const sx = f.ax - 10;
    canvas.drawOval(Skia.XYWHRect(sx - sw / 2, L.shadow.y - sh / 2, sw, sh), sp);
  }

  const pose = poseOf(res, f);
  const A0x = pose.A0x;
  drawRibbon(canvas, f, A0x - 8);

  // the piñata, its tail and its lights in one layer (the bloom and glows stay on the body)
  const art = res.art;
  canvas.saveLayer();
  const tv = meshVerts(res, pose, res.tail, f.tail, art.tailRoot);
  drawMesh(canvas, tv, res.tailShader, 1);
  const bv = meshVerts(res, pose, res.mesh, 0, null);
  if (f.burst < 0.999) drawMesh(canvas, bv, res.bodyShader, 1);
  if (f.burst > 0.001) drawMesh(canvas, bv, res.burstShader, f.burst);
  if (res.crack1Shader && f.crack1 > 0.002 && f.burst < 0.999) drawMesh(canvas, bv, res.crack1Shader, f.crack1 * (1 - f.burst));
  if (res.crack2Shader && f.crack2 > 0.002 && f.burst < 0.999) drawMesh(canvas, bv, res.crack2Shader, f.crack2 * (1 - f.burst));
  // bulbs light the body around them (U5), the hit blooms (white), the hole glows warm (U2):
  // all are the atlas's soft glow, tinted, in one draw kept on the body (SrcATop)
  const o = { x: 0, y: 0, shade: 1 };
  const bulbXY: number[] = [];
  const lit: Glow[] = [];
  const levels: number[] = [];
  for (let i = 0; i < art.bulbs.length; i++) {
    const b = art.bulbs[i];
    place(res, pose, b.x, b.y, o);
    bulbXY.push(o.x, o.y);
    const lv = bulbLevel(i, f);
    levels.push(lv);
    lit.push({ x: o.x, y: o.y, r: 20 + 10 * Math.max(0, lv - 1), c: res.bulbColors[i], a: 0.34 * Math.min(1.8, lv) });
  }
  if (f.bloom > 0.002) {
    place(res, pose, (f.bloomX - res.ox) / res.k, (f.bloomY - res.oy) / res.k, o);
    lit.push({ x: o.x, y: o.y, r: 90, c: WHITE, a: f.bloom * 1.1 });
  }
  drawGlows(canvas, res, lit, BlendMode.SrcATop);
  canvas.restore();

  // the bulbs are lit: a coloured halo and a hot core, both brighter on a hit
  const halos: Glow[] = [];
  for (let i = 0; i < art.bulbs.length; i++) {
    const lv = levels[i];
    const x = bulbXY[2 * i];
    const y = bulbXY[2 * i + 1];
    const r = art.bulbs[i].r * res.k;
    halos.push({ x, y, r: r * (2.4 + 1.4 * clamp01(lv - 1)), c: res.bulbColors[i], a: 0.45 + 0.5 * clamp01(lv - 1) });
    halos.push({ x, y, r: r * 1.1, c: WHITE, a: 0.3 + 0.6 * clamp01(lv - 0.9) });
  }
  // warm light pours out of the fresh hole (screened, so it reads as light, not paint)
  if (f.hole > 0.002) {
    place(res, pose, art.flank.x, art.flank.y, o);
    halos.push({ x: o.x, y: o.y, r: 52, c: HOLE, a: f.hole * 1.3 });
  }
  drawGlows(canvas, res, halos, BlendMode.Screen);

  // tap ripple (pale disc, soft edge) and the burst's shock ring
  if (f.rippleA > 0.002) {
    const p = Skia.Paint();
    p.setAntiAlias(true);
    const c = Skia.Color(C.ripple);
    const r = f.rippleR;
    p.setShader(
      Skia.Shader.MakeRadialGradient(
        Skia.Point(f.rippleX, f.rippleY),
        r,
        [Float32Array.of(c[0], c[1], c[2], f.rippleA * 0.55), Float32Array.of(c[0], c[1], c[2], f.rippleA * 0.8), Float32Array.of(c[0], c[1], c[2], 0)],
        [0, Math.max(0, 1 - 24 / r), 1],
        TileMode.Clamp,
      ),
    );
    canvas.drawCircle(f.rippleX, f.rippleY, r, p);
  }
  if (f.ringA > 0.002) {
    const p = Skia.Paint();
    p.setAntiAlias(true);
    p.setStyle(PaintStyle.Stroke);
    p.setStrokeWidth(3);
    p.setColor(Skia.Color(C.fillGlow));
    p.setAlphaf(f.ringA * 0.6);
    canvas.drawCircle(f.ringX, f.ringY, f.ringR, p);
  }

  // loot, tiles, confetti, streamers
  drawAtlasPieces(canvas, res, f);
  drawConfetti(canvas, f);
  drawStreamers(canvas, f);

  canvas.restore(); // stage

  drawPill(canvas, res, f);
  canvas.restore(); // view

  // the header band: a white strip with a soft fade under the title and chip, above the stage, so
  // the hop, the drop-in, the ribbon and the streamers slide in from under it
  canvas.save();
  canvas.scale(view.s, view.s);
  {
    const B = L.band;
    const bottom = B.bottom + view.top;
    const bp = Skia.Paint();
    bp.setColor(Skia.Color(C.page));
    canvas.drawRect(Skia.XYWHRect(0, -10, 402, bottom + 10), bp);
    const page = Skia.Color(C.page);
    const clear = Float32Array.of(page[0], page[1], page[2], 0);
    const fp = Skia.Paint();
    fp.setShader(Skia.Shader.MakeLinearGradient(Skia.Point(0, bottom), Skia.Point(0, bottom + B.fade), [page, clear], null, TileMode.Clamp));
    canvas.drawRect(Skia.XYWHRect(0, bottom, 402, B.fade), fp);
  }
  canvas.restore();

  // tokens fly over the band into the chip (trail first)
  canvas.save();
  canvas.scale(view.s, view.s);
  canvas.translate(0, view.dy);
  // tokens (trail first)
  for (let i = 0; i < f.tokens.length; i++) {
    const tk = f.tokens[i];
    // a short motion trail: four samples, 12 ms apart, small and faint
    for (let q = tk.tx.length - 1; q >= 0; q--) drawToken(canvas, tk.tx[q], tk.ty[q], tk.s * (0.9 - q * 0.08), 0.11 - q * 0.025);
    drawToken(canvas, tk.x, tk.y, tk.s, tk.a);
  }
  canvas.restore();

  // header is top-anchored (follows the safe area)
  canvas.save();
  canvas.scale(view.s, view.s);
  drawHeader(canvas, res, f, view.top);
  canvas.restore();

  if (f.flash > 0.002) {
    const p = Skia.Paint();
    p.setColor(Skia.Color('#FFFFFF'));
    p.setAlphaf(f.flash);
    canvas.drawRect(Skia.XYWHRect(0, 0, view.w, view.h), p);
  }
  // first draws: the art and text appear together
  if (reveal < 0.999) {
    const p = Skia.Paint();
    p.setColor(Skia.Color(C.page));
    p.setAlphaf(1 - reveal);
    canvas.drawRect(Skia.XYWHRect(0, 0, view.w, view.h), p);
  }
}
