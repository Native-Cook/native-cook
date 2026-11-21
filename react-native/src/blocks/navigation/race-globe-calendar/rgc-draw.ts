// Race Globe Calendar: everything drawn over the globe, recorded into one SkPicture per frame on the UI thread, in
// the same canvas as the globe so pins, arcs and the comet cannot swim against the coastlines, and the glass can
// blur the Earth behind it directly.

import { BlendMode, BlurStyle, ClipOp, type SkCanvas, type SkFont, type SkPath, Skia, TileMode } from '@shopify/react-native-skia';

import { COUNT, NIGHT, PAST, W } from './rgc-data';
import { AUTO_TRACK, clamp01, easeOut, type Frame, landingAt, mix, project, type Proj, smooth, smoothstep } from './rgc-motion';
import { C, G } from './rgc-theme';

export type Ctx = {
  fTitle: SkFont;
  fDate: SkFont;
  fChip: SkFont;
  fName: SkFont;
  fPlace: SkFont;
  fPin: SkFont;
  titles: string[];
  chips: string[];
  wheres: string[];
  pre: string[];
  suf: string[];
  hour: number[];
  chipW: number[];
  nameW: number[];
  placeW: number[];
  preW: number[];
  pinW: number[];
  digitW: number;
  colonW: number;
  /** advance of '0'…'9' and ':' (index 10) */
  charW: number[];
  circuits: SkPath[];
  shuffle: SkPath;
  trophy: SkPath;
  flag: SkPath;
};

/** Precomputed route arcs: great circles lifted 2–6 % of R by hop length, ARC_N + 1 points each (flat xyz). */
export const ARC_N = 40;
const buildArcs = () => {
  const out: number[][] = [];
  for (let j = 0; j < COUNT - 1; j++) {
    const a = W[j];
    const b = W[j + 1];
    const c = Math.min(Math.max(a[0] * b[0] + a[1] * b[1] + a[2] * b[2], -1), 1);
    const om = Math.acos(c);
    const lift = 0.02 + 0.04 * Math.min(om / ((120 * Math.PI) / 180), 1);
    const pts: number[] = [];
    for (let k = 0; k <= ARC_N; k++) {
      const s = k / ARC_N;
      const ka = Math.sin((1 - s) * om) / Math.sin(om);
      const kb = Math.sin(s * om) / Math.sin(om);
      const h = 1 + lift * Math.sin(Math.PI * s);
      pts.push((a[0] * ka + b[0] * kb) * h, (a[1] * ka + b[1] * kb) * h, (a[2] * ka + b[2] * kb) * h);
    }
    out.push(pts);
  }
  return out;
};
export const ARCS = buildArcs();

/** What the overlay needs besides the frame: the camera for this frame. */
export type View = { m: number[]; cx: number; cy: number; R: number; cardTop: number };

const paintFill = (hex: string, a: number) => {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(hex));
  p.setAlphaf(a);
  return p;
};
const paintStroke = (hex: string, a: number, w: number) => {
  'worklet';
  const p = paintFill(hex, a);
  p.setStyle(1);
  p.setStrokeWidth(w);
  return p;
};

/** Fade towards the limb: pins within 14° of it fade out (z is the cosine from the view axis). */
const limbFade = (z: number) => {
  'worklet';
  return smoothstep(0.25, 0.48, z);
};

const drawArcs = (c: SkCanvas, f: Frame, v: View) => {
  'worklet';
  const first = Math.max(0, Math.floor(f.arcPos) - 9);
  const last = Math.min(COUNT - 2, Math.floor(f.arcPos));
  for (let j = first; j <= last; j++) {
    const prog = clamp01(f.arcPos - j);
    if (prog <= 0) continue;
    // the arc into the selected round is bright; older ones dim to 35 %
    const bright = 1 - clamp01((f.arcPos - (j + 1)) * 2.5);
    const alpha = mix(0.35, 0.92, bright);
    const pts = ARCS[j];
    const nEnd = prog * ARC_N;
    const kEnd = Math.ceil(nEnd);
    const sx: number[] = [];
    const sy: number[] = [];
    const sv: number[] = [];
    for (let k = 0; k <= kEnd; k++) {
      let px = pts[k * 3];
      let py = pts[k * 3 + 1];
      let pz = pts[k * 3 + 2];
      if (k === kEnd && kEnd > nEnd) {
        const fr = nEnd - (k - 1);
        px = mix(pts[(k - 1) * 3], px, fr);
        py = mix(pts[(k - 1) * 3 + 1], py, fr);
        pz = mix(pts[(k - 1) * 3 + 2], pz, fr);
      }
      const q = project(v.m, [px, py, pz], v.cx, v.cy, v.R);
      const l = Math.sqrt(px * px + py * py + pz * pz);
      sx.push(q.x);
      sy.push(q.y);
      sv.push(q.vis * limbFade(q.z / l));
    }
    // one path per run of segments with the same limb fade (joins between separate strokes show as beads)
    let b = Skia.PathBuilder.Make();
    let runA = -1;
    let open = false;
    let first = true;
    for (let k = 0; k < kEnd; k++) {
      const a = Math.min(sv[k], sv[k + 1]);
      const qa = a <= 0.001 ? 0 : Math.round(a * 20) / 20;
      if (open && qa !== runA) {
        const p = paintStroke('#FFFFFF', alpha * runA, 3);
        p.setStrokeCap(first ? 1 : 0);
        p.setStrokeJoin(1);
        c.drawPath(b.build(), p);
        first = false;
        b = Skia.PathBuilder.Make();
        open = false;
      }
      if (qa <= 0) continue;
      if (!open) {
        b.moveTo(sx[k], sy[k]);
        open = true;
        runA = qa;
      }
      b.lineTo(sx[k + 1], sy[k + 1]);
    }
    if (open) {
      const p = paintStroke('#FFFFFF', alpha * runA, 3);
      p.setStrokeCap(first ? 1 : 0);
      p.setStrokeJoin(1);
      c.drawPath(b.build(), p);
    }
    // arrowhead at 55 % of the arc
    const ka = Math.round(0.55 * ARC_N);
    if (prog >= 0.6 && sv[ka] > 0.001 && sv[ka + 1] > 0.001) {
      const tx = sx[ka + 1] - sx[ka - 1];
      const ty = sy[ka + 1] - sy[ka - 1];
      const tl = Math.sqrt(tx * tx + ty * ty) || 1;
      const ux = tx / tl;
      const uy = ty / tl;
      const hx = sx[ka];
      const hy = sy[ka];
      const b = Skia.PathBuilder.Make();
      b.moveTo(hx + ux * 5, hy + uy * 5);
      b.lineTo(hx - ux * 5 - uy * 4.5, hy - uy * 5 + ux * 4.5);
      b.lineTo(hx - ux * 2.6, hy - uy * 2.6);
      b.lineTo(hx - ux * 5 + uy * 4.5, hy - uy * 5 - ux * 4.5);
      b.close();
      c.drawPath(b.build(), paintFill('#FFFFFF', alpha * sv[ka]));
    }
    // the comet: a bright head with a 60 pt tail, while the arc draws itself
    if (f.comet > 0.5 && prog > 0 && prog < 1 && sv[kEnd] > 0.001) {
      const hx = sx[kEnd];
      const hy = sy[kEnd];
      let len = 0;
      const tb = Skia.PathBuilder.Make();
      tb.moveTo(hx, hy);
      let ex = hx;
      let ey = hy;
      for (let k = kEnd - 1; k >= 0 && len < 60; k--) {
        if (sv[k] <= 0.001) break;
        const dx = sx[k] - ex;
        const dy = sy[k] - ey;
        const dl = Math.sqrt(dx * dx + dy * dy);
        if (len + dl > 60) {
          const r = (60 - len) / dl;
          ex += dx * r;
          ey += dy * r;
          len = 60;
        } else {
          ex = sx[k];
          ey = sy[k];
          len += dl;
        }
        tb.lineTo(ex, ey);
      }
      const head = prog < 0.06 ? prog / 0.06 : 1;
      if (len > 2) {
        const tp = paintStroke('#FFFFFF', 1, 6);
        tp.setStrokeCap(1);
        tp.setShader(Skia.Shader.MakeLinearGradient({ x: hx, y: hy }, { x: ex, y: ey }, [Skia.Color('rgba(255,255,255,0.85)'), Skia.Color('rgba(190,215,255,0)')], null, TileMode.Clamp));
        tp.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2.2, true));
        tp.setAlphaf(head);
        c.drawPath(tb.build(), tp);
      }
      const g = Skia.Paint();
      g.setAntiAlias(true);
      g.setShader(Skia.Shader.MakeRadialGradient({ x: hx, y: hy }, 11, [Skia.Color('rgba(255,255,255,1)'), Skia.Color('rgba(205,226,255,0.55)'), Skia.Color('rgba(150,190,255,0)')], [0, 0.3, 1], TileMode.Clamp));
      g.setAlphaf(head);
      c.drawCircle(hx, hy, 11, g);
      c.drawCircle(hx, hy, 3.4, paintFill('#FFFFFF', head));
    }
  }
};

/** A ring on the sphere's surface round world point w (angular radius a, radians), projected: a true ellipse. */
const ringPath = (w: number[], a: number, v: View) => {
  'worklet';
  // tangent basis at w
  let e1x = w[2];
  let e1y = 0;
  let e1z = -w[0];
  const l1 = Math.sqrt(e1x * e1x + e1z * e1z) || 1;
  e1x /= l1;
  e1z /= l1;
  const e2x = w[1] * e1z - w[2] * e1y;
  const e2y = w[2] * e1x - w[0] * e1z;
  const e2z = w[0] * e1y - w[1] * e1x;
  const ca = Math.cos(a);
  const sa = Math.sin(a);
  const b = Skia.PathBuilder.Make();
  for (let k = 0; k <= 48; k++) {
    const th = (k / 48) * Math.PI * 2;
    const ct = Math.cos(th) * sa;
    const st = Math.sin(th) * sa;
    const q = project(v.m, [w[0] * ca + e1x * ct + e2x * st, w[1] * ca + e1y * ct + e2y * st, w[2] * ca + e1z * ct + e2z * st], v.cx, v.cy, v.R);
    if (k === 0) b.moveTo(q.x, q.y);
    else b.lineTo(q.x, q.y);
  }
  return b.build();
};

type PinDraw = { i: number; x: number; y: number; hx: number; hy: number; r: number; z: number; sel: number; num: number; lift: number; a: number };

const drawPins = (c: SkCanvas, f: Frame, v: View, ctx: Ctx, proj: Proj[]) => {
  'worklet';
  const lnd = landingAt(f.land);
  // 6 pt dots for every round outside the numbered window
  const dots = Skia.PathBuilder.Make();
  const rims = Skia.PathBuilder.Make();
  const list: PinDraw[] = [];
  for (let i = 0; i < COUNT; i++) {
    const q = proj[i];
    if (q.vis < 0.5) continue;
    const a = limbFade(q.z);
    if (a <= 0.01) continue;
    const num = clamp01((2.5 - Math.abs(i - f.selF)) * 14) * f.numbered;
    let sel = 0;
    if (i === f.to && f.land >= 0) sel = 1;
    if (i === f.from && f.from !== f.to) sel = Math.max(sel, 1 - f.leave);
    if (num <= 0.001 && sel <= 0.001) {
      dots.addCircle(q.x, q.y, G.pin.dot);
      rims.addCircle(q.x, q.y, G.pin.dot + 0.6);
      continue;
    }
    // the landing pin hops up as the comet arrives and drops onto the surface with `pop`
    let lift = 0;
    let grow = sel;
    if (i === f.to && f.from !== f.to) {
      lift = f.land < 0 ? 10 * smooth((f.land + 0.3) / 0.3) : -lnd.drop;
      grow = f.land < 0 ? 0 : lnd.scale > 0 ? (lnd.scale - 0.6) / 0.4 : 0;
    }
    const k = i === f.to && f.from !== f.to && f.land >= 0 ? Math.max(num, Math.min(grow, 1)) : Math.max(num, sel > 0 ? 1 : 0);
    lift *= k;
    const r = mix(G.pin.dot, mix(G.pin.r, G.pin.selR, grow), k);
    const tail = G.pin.tail * k;
    list.push({ i, x: q.x, y: q.y, hx: q.x, hy: q.y - tail - r - lift, r, z: q.z, sel: grow, num: k, lift, a });
  }
  c.drawPath(rims.build(), paintFill(C.pinRim, 0.9));
  c.drawPath(dots.build(), paintFill(C.dot, 1));

  // keep numbered pins 4 pt apart on screen, the selected one stays put
  for (let it = 0; it < 4; it++) {
    for (let p = 0; p < list.length; p++) {
      for (let q = p + 1; q < list.length; q++) {
        const A = list[p];
        const B = list[q];
        const dx = B.hx - A.hx;
        const dy = B.hy - A.hy;
        const dd = Math.sqrt(dx * dx + dy * dy) || 0.01;
        const need = A.r + B.r + 4;
        if (dd >= need) continue;
        const push = need - dd;
        const ux = dx / dd;
        const uy = dy / dd;
        const wa = A.sel > B.sel ? 0 : B.sel > A.sel ? 1 : 0.5;
        A.hx -= ux * push * wa;
        A.hy -= uy * push * wa;
        B.hx += ux * push * (1 - wa);
        B.hy += uy * push * (1 - wa);
      }
    }
  }
  // back to front, the selected pin last (insertion sort: no callbacks inside worklets)
  for (let p = 1; p < list.length; p++) {
    const it = list[p];
    let q = p - 1;
    while (q >= 0 && (list[q].sel > it.sel || (list[q].sel === it.sel && list[q].z > it.z))) {
      list[q + 1] = list[q];
      q--;
    }
    list[q + 1] = it;
  }
  for (let n = 0; n < list.length; n++) {
    const P = list[n];
    // contact shadow on the surface
    const sh = paintFill('#000000', 0.35 * P.a * clamp01(1 - P.lift / 16) * P.num);
    sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2.2, true));
    c.drawOval(Skia.XYWHRect(P.x - 6 - P.lift * 0.3, P.y - 2, 12 + P.lift * 0.6, 4), sh);
    // tail to the surface point
    const tb = Skia.PathBuilder.Make();
    const by = P.hy + P.r * 0.55;
    tb.moveTo(P.hx - P.r * 0.5, by);
    tb.lineTo(P.x, P.y - P.lift);
    tb.lineTo(P.hx + P.r * 0.5, by);
    tb.close();
    const fill = paintFill(C.pin, P.a);
    c.drawPath(tb.build(), fill);
    if (P.sel > 0.01) c.drawCircle(P.hx, P.hy, P.r + 2.4, paintFill('#FFFFFF', P.a * P.sel));
    // the head: a little dome, lighter on top
    const hp = Skia.Paint();
    hp.setAntiAlias(true);
    hp.setShader(Skia.Shader.MakeLinearGradient({ x: P.hx, y: P.hy - P.r }, { x: P.hx, y: P.hy + P.r }, [Skia.Color('#5C60F0'), Skia.Color('#3E42CF')], null, TileMode.Clamp));
    hp.setAlphaf(P.a);
    c.drawCircle(P.hx, P.hy, P.r, hp);
    c.drawCircle(P.hx, P.hy, P.r - 0.5, paintStroke(C.pinRim, P.a * 0.95, 1));
    if (P.r > 10.5) {
      const s = P.r / G.pin.r;
      c.save();
      c.translate(P.hx, P.hy);
      c.scale(s, s);
      c.drawText(String(P.i + 1), -ctx.pinW[P.i] / 2, 4.7, paintFill('#FFFFFF', P.a), ctx.fPin);
      c.restore();
    }
  }
  return list;
};

const glassRRect = (c: SkCanvas, rr: ReturnType<typeof Skia.RRectXY>, blur: number, fillTop: string, aTop: number, fillBot: string, aBot: number, a: number) => {
  'worklet';
  c.save();
  c.clipRRect(rr, ClipOp.Intersect, true);
  const lp = Skia.Paint();
  lp.setAlphaf(a);
  c.saveLayer(lp, rr.rect, Skia.ImageFilter.MakeBlur(blur, blur, TileMode.Clamp, null));
  const g = Skia.Paint();
  const top = Skia.Color(fillTop);
  const bot = Skia.Color(fillBot);
  top[3] = aTop;
  bot[3] = aBot;
  g.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: rr.rect.y }, { x: 0, y: rr.rect.y + rr.rect.height }, [top, bot], null, TileMode.Clamp));
  c.drawRect(rr.rect, g);
  c.restore();
  c.restore();
  // rim: 1 pt, lit from above
  const rim = Skia.Paint();
  rim.setAntiAlias(true);
  rim.setStyle(1);
  rim.setStrokeWidth(1);
  rim.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: rr.rect.y }, { x: 0, y: rr.rect.y + rr.rect.height }, [Skia.Color('rgba(255,255,255,0.20)'), Skia.Color('rgba(255,255,255,0.05)')], null, TileMode.Clamp));
  rim.setAlphaf(a);
  const ins = Skia.RRectXY(Skia.XYWHRect(rr.rect.x + 0.5, rr.rect.y + 0.5, rr.rect.width - 1, rr.rect.height - 1), rr.rx - 0.5, rr.ry - 0.5);
  c.drawRRect(ins, rim);
};

const drawCallout = (c: SkCanvas, f: Frame, ctx: Ctx, pins: PinDraw[]) => {
  'worklet';
  const lnd = landingAt(f.land);
  const k = lnd.callout * (1 - f.calloutOut);
  if (k <= 0.005) return;
  let P: PinDraw | null = null;
  for (let n = 0; n < pins.length; n++) if (pins[n].i === f.to) P = pins[n];
  if (!P) return;
  const i = f.to;
  const L = G.callout;
  const w = L.pad + Math.max(ctx.nameW[i], ctx.placeW[i]) + 12 + L.chip + 12;
  const h = L.h;
  const x = Math.min(Math.max(P.hx - w / 2, 16), 386 - w);
  const bottom = P.hy - P.r - 2.4 - L.gap;
  const sink = f.calloutOut * 6;
  const s = mix(0.72, 1, k);
  const a = clamp01(k * 2.2) * P.a;
  c.save();
  c.translate(P.hx, bottom + sink);
  c.scale(s, s);
  c.translate(-P.hx, -(bottom + sink));
  const y = bottom - h + sink;
  const rr = Skia.RRectXY(Skia.XYWHRect(x, y, w, h), L.r, L.r);
  // soft shadow
  const sh = paintFill('#000000', 0.35 * a);
  sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 10, true));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x + 2, y + 6, w - 4, h), L.r, L.r), sh);
  glassRRect(c, rr, 16, 'rgba(20,22,26,1)', 0.72, 'rgba(14,16,20,1)', 0.8, a);
  // the pointer under the pill
  const tb = Skia.PathBuilder.Make();
  const px = Math.min(Math.max(P.hx, x + 16), x + w - 16);
  tb.moveTo(px - 6, y + h - 0.5);
  tb.lineTo(px, y + h + 6);
  tb.lineTo(px + 6, y + h - 0.5);
  tb.close();
  c.drawPath(tb.build(), paintFill('#14161A', 0.8 * a));
  // text rises 4 pt into place
  const ty = (1 - k) * 4;
  c.drawText(ctx.titles[i], x + L.pad, y + 23 + ty, paintFill('#FFFFFF', a), ctx.fName);
  c.drawText(ctx.wheres[i], x + L.pad, y + 42 + ty, paintFill('#FFFFFF', 0.58 * a), ctx.fPlace);
  // the circuit chip, its outline stroked as it lands
  const cx = x + w - 12 - L.chip;
  const cy = y + (h - L.chip) / 2;
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx, cy, L.chip, L.chip), 8, 8), paintFill(C.chipLine, a));
  const cir = f.pulse >= 0 ? easeOut(f.pulse / 0.45) : lnd.circuit;
  const trim = Skia.Path.Trim(ctx.circuits[i], 0, Math.max(0.001, cir), false);
  if (trim) {
    c.save();
    c.translate(cx + 3, cy + 3);
    c.scale(24 / 28, 24 / 28);
    const sp = paintStroke('#FFFFFF', 0.92 * a, 2.3);
    sp.setStrokeCap(1);
    sp.setStrokeJoin(1);
    c.drawPath(trim, sp);
    c.restore();
  }
  c.restore();
};

/** HH:MM for minutes since midnight. */
const hhmm = (m: number) => {
  'worklet';
  const v = ((Math.round(m) % 1440) + 1440) % 1440;
  const h = Math.floor(v / 60);
  const mm = v % 60;
  return `${h < 10 ? '0' : ''}${h}:${mm < 10 ? '0' : ''}${mm}`;
};

const drawCard = (c: SkCanvas, f: Frame, ctx: Ctx, x: number, y: number, i: number, focus: number, sheen: number) => {
  'worklet';
  const g = G.card;
  const sc = 1 - 0.02 * f.press * focus;
  c.save();
  c.translate(x + g.w / 2, y + g.h / 2);
  c.scale(sc, sc);
  c.translate(-(x + g.w / 2), -(y + g.h / 2));
  const rr = Skia.RRectXY(Skia.XYWHRect(x, y, g.w, g.h), g.r, g.r);
  const sh = paintFill('#000000', 0.45);
  sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 18, true));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x + 6, y + 14, g.w - 12, g.h - 6), g.r, g.r), sh);
  glassRRect(c, rr, 28, 'rgba(16,20,26,1)', 0.6, 'rgba(11,14,19,1)', 0.78, 1);
  c.save();
  c.clipRRect(rr, ClipOp.Intersect, true);

  // the arrival sheen: warm for a night race, blue for an upcoming one
  if (sheen >= 0 && sheen <= 1 && PAST[i] === 0) {
    c.save();
    c.clipRRect(rr, ClipOp.Intersect, true);
    const bx = x + mix(-90, g.w + 90, sheen);
    const col = NIGHT[i] ? 'rgba(255,178,90,' : 'rgba(62,123,255,';
    const pk = 0.22 * Math.sin(Math.PI * sheen);
    const p = Skia.Paint();
    p.setBlendMode(BlendMode.Plus);
    p.setShader(Skia.Shader.MakeLinearGradient({ x: bx - 70, y: y }, { x: bx + 70, y: y + 50 }, [Skia.Color(`${col}0)`), Skia.Color(`${col}${pk})`), Skia.Color(`${col}0)`)], null, TileMode.Clamp));
    c.drawRect(rr.rect, p);
    c.restore();
  }

  // chip: round and winner or countdown
  const ch = G.chip;
  const cw = 10 + 15 + 6 + ctx.chipW[i] + 12;
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x + ch.dx, y + ch.dy, cw, ch.h), ch.r, ch.r), paintFill('#FFFFFF', 0.1));
  c.save();
  c.translate(x + ch.dx + 10, y + ch.dy + 5.5);
  c.scale(15 / 24, 15 / 24);
  const ip = paintStroke('#FFFFFF', 0.95, 3);
  ip.setStrokeCap(1);
  ip.setStrokeJoin(1);
  c.drawPath(PAST[i] ? ctx.trophy : ctx.flag, ip);
  c.restore();
  c.drawText(ctx.chips[i], x + ch.dx + 31, y + ch.dy + 17.6, paintFill('#FFFFFF', 0.95), ctx.fChip);

  c.drawText(ctx.titles[i], x + 17, y + G.titleBase, paintFill('#FFFFFF', 1), ctx.fTitle);

  // date line: the start time sits in fixed digit cells so it can roll while the hour is scrubbed
  const dp = paintFill('#FFFFFF', 0.62);
  const base = y + G.dateBase;
  c.drawText(ctx.pre[i], x + 17, base, dp, ctx.fDate);
  const m = (ctx.hour[i] + f.hour * focus) * 60;
  const st = m / 5;
  const si = Math.floor(st + 1e-6);
  const roll = smoothstep(0.6, 1, st - si);
  const cur = hhmm(si * 5);
  const nxt = hhmm(si * 5 + 5);
  let cx = x + 17 + ctx.preW[i];
  c.save();
  c.clipRect(Skia.XYWHRect(x, base - 15, G.card.w, 20), ClipOp.Intersect, true);
  for (let p = 0; p < 5; p++) {
    const cellW = p === 2 ? ctx.colonW : ctx.digitW;
    const ca = cur.charAt(p);
    const na = nxt.charAt(p);
    const wa = ctx.charW[ca === ':' ? 10 : ca.charCodeAt(0) - 48];
    if (roll > 0.001 && ca !== na) {
      const wb = ctx.charW[na === ':' ? 10 : na.charCodeAt(0) - 48];
      c.drawText(ca, cx + (cellW - wa) / 2, base - roll * 17, dp, ctx.fDate);
      c.drawText(na, cx + (cellW - wb) / 2, base + (1 - roll) * 17, dp, ctx.fDate);
    } else {
      c.drawText(ca, cx + (cellW - wa) / 2, base, dp, ctx.fDate);
    }
    cx += cellW;
  }
  c.restore();
  c.drawText(ctx.suf[i], cx, base, dp, ctx.fDate);
  c.restore();
  c.restore();
};

const drawButton = (c: SkCanvas, f: Frame, ctx: Ctx) => {
  'worklet';
  const b = G.shuffle;
  const s = 1 - 0.1 * f.shuffle;
  c.save();
  c.translate(b.cx, b.cy);
  c.scale(s, s);
  c.translate(-b.cx, -b.cy);
  const rr = Skia.RRectXY(Skia.XYWHRect(b.cx - b.r, b.cy - b.r, b.r * 2, b.r * 2), b.r, b.r);
  glassRRect(c, rr, 20, 'rgba(34,38,46,1)', 0.55, 'rgba(22,25,31,1)', 0.62, 1);
  c.save();
  c.translate(b.cx, b.cy);
  c.rotate(f.shuffleRot, 0, 0);
  c.scale(20 / 24, 20 / 24);
  c.translate(-12, -12);
  const ip = paintStroke('#FFFFFF', 1, 2.4);
  ip.setStrokeCap(1);
  ip.setStrokeJoin(1);
  c.drawPath(ctx.shuffle, ip);
  c.restore();
  c.restore();
};

export const drawOverlay = (c: SkCanvas, f: Frame, v: View, ctx: Ctx) => {
  'worklet';
  const proj: Proj[] = [];
  for (let i = 0; i < COUNT; i++) proj.push(project(v.m, W[i], v.cx, v.cy, v.R));

  drawArcs(c, f, v);

  // landing ripple on the surface
  const lnd = landingAt(f.land);
  if (lnd.ripple >= 0 && f.from !== f.to) {
    const e = 1 - (1 - lnd.ripple) * (1 - lnd.ripple);
    const w = W[f.to];
    const q = proj[f.to];
    if (q.vis > 0.5) {
      c.drawPath(ringPath(w, (34 * e) / v.R, v), paintStroke('#FFFFFF', 0.5 * (1 - lnd.ripple), 2));
      if (lnd.ripple > 0.15) {
        const e2 = 1 - (1 - (lnd.ripple - 0.15) / 0.85) ** 2;
        c.drawPath(ringPath(w, (22 * e2) / v.R, v), paintStroke('#B9BCFF', 0.35 * (1 - lnd.ripple), 1.5));
      }
    }
  }

  const pins = drawPins(c, f, v, ctx, proj);
  drawCallout(c, f, ctx, pins);
  drawButton(c, f, ctx);

  // the card row
  const g = G.card;
  const k0 = Math.floor(f.cardPos) - 1;
  const focusK = Math.round(f.cardPos);
  // The last autoplay card is the first card again. Wrap its neighbors so the edge slivers match across the seam.
  const cycle = AUTO_TRACK.length - 1;
  for (let k = k0; k <= k0 + 3; k++) {
    let i = -1;
    if (f.track === 0) {
      i = AUTO_TRACK[((k % cycle) + cycle) % cycle];
    } else if (k >= 0 && k < COUNT) i = k;
    if (i < 0) continue;
    const x = g.x + (k - f.cardPos) * g.pitch;
    if (x < -g.w - 4 || x > 406) continue;
    const focus = k === focusK ? 1 : 0;
    const sh = f.track === 0 ? (k === f.sheenSlot ? f.sheen : -1) : k === focusK ? f.sheen : -1;
    drawCard(c, f, ctx, x, v.cardTop, i, focus, sh);
  }
};
