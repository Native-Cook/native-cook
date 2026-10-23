import { BlurStyle, ClipOp, PaintStyle, type SkCanvas, Skia, StrokeCap, StrokeJoin, TileMode, vec } from '@shopify/react-native-skia';

import { clamp01, easeInOut, type Frame, lerp } from './motion';
import { actionBox, type Box, colors, layout, orbs, payeeBox } from './theme';

/**
 * Skia drawing for the two canvases. Everything takes ref px (see theme.ts) and maps them with
 * `g`: x → x·U, y → Y0 + y·U. Backdrop = everything under the text; hero = the debit card above it.
 */
export type Geo = { U: number; Y0: number };

function hexc(hex: string, a = 1) {
  'worklet';
  const n = parseInt(hex.slice(1), 16);
  return Float32Array.of(((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255, clamp01(a));
}

function fill(hex: string, a = 1) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(hexc(hex, a));
  return p;
}

function stroke(hex: string, w: number, a = 1) {
  'worklet';
  const p = fill(hex, a);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(w);
  p.setStrokeCap(StrokeCap.Round);
  p.setStrokeJoin(StrokeJoin.Round);
  return p;
}

function rrect(x: number, y: number, w: number, h: number, r: number) {
  'worklet';
  const rr = Math.max(0, Math.min(r, w / 2, h / 2));
  return Skia.RRectXY(Skia.XYWHRect(x, y, Math.max(0, w), Math.max(0, h)), rr, rr);
}

/** A box in ref px → a rounded rect in pt. */
function boxRR(b: Box, g: Geo) {
  'worklet';
  return rrect(b.x * g.U, g.Y0 + b.y * g.U, b.w * g.U, b.h * g.U, b.r * g.U);
}

function lerpBox(a: Box, b: Box, p: number): Box {
  'worklet';
  return { x: lerp(a.x, b.x, p), y: lerp(a.y, b.y, p), w: lerp(a.w, b.w, p), h: lerp(a.h, b.h, p), r: lerp(a.r, b.r, p) };
}

/** Scales the canvas by s about the point (cx, cy) in pt. */
function about(canvas: SkCanvas, cx: number, cy: number, s: number) {
  'worklet';
  canvas.translate(cx, cy);
  canvas.scale(s, s);
  canvas.translate(-cx, -cy);
}

/** A gradient sphere: dark lower left to light upper right, its rim shaded a little. */
function drawOrb(canvas: SkCanvas, cx: number, cy: number, r: number, stops: readonly string[], alpha: number) {
  'worklet';
  if (alpha <= 0.001 || r <= 0.2) return;
  const p = Skia.Paint();
  p.setAntiAlias(true);
  const k = 0.72 * r;
  p.setShader(
    Skia.Shader.MakeLinearGradient(
      vec(cx - k, cy + k),
      vec(cx + k, cy - k),
      stops.map((s) => hexc(s, alpha)),
      [0, 0.22, 0.5, 0.72, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawCircle(cx, cy, r, p);
  const rim = Skia.Paint();
  rim.setAntiAlias(true);
  rim.setShader(
    Skia.Shader.MakeRadialGradient(
      vec(cx + 0.35 * r, cy - 0.35 * r),
      1.45 * r,
      [hexc('#0A1040', 0), hexc('#0A1040', 0), hexc('#0A1040', 0.22 * alpha)],
      [0, 0.62, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawCircle(cx, cy, r, rim);
}

function path(points: readonly (readonly number[])[], g: Geo, dy = 0, sy = 1, base = 0) {
  'worklet';
  const pa = Skia.Path.Make();
  points.forEach((pt, i) => {
    const x = pt[0] * g.U;
    const y = g.Y0 + (base + (pt[1] - base) * sy + dy) * g.U;
    if (i === 0) pa.moveTo(x, y);
    else pa.lineTo(x, y);
  });
  return pa;
}

/** The ↙ / ↗ arrow (dir 1 = down-left, -1 = up-right), centred on (cx, cy), size s pt. */
function arrow(cx: number, cy: number, s: number, dir: number) {
  'worklet';
  const h = s / 2;
  const pa = Skia.Path.Make();
  if (dir > 0) {
    pa.moveTo(cx + h, cy - h);
    pa.lineTo(cx - h, cy + h);
    pa.moveTo(cx - h, cy - h * 0.15);
    pa.lineTo(cx - h, cy + h);
    pa.lineTo(cx + h * 0.15, cy + h);
  } else {
    pa.moveTo(cx - h, cy + h);
    pa.lineTo(cx + h, cy - h);
    pa.moveTo(cx - h * 0.15, cy - h);
    pa.lineTo(cx + h, cy - h);
    pa.lineTo(cx + h, cy + h * 0.15);
  }
  return pa;
}

function gradientStroke(x0: number, y0: number, x1: number, y1: number, c0: string, c1: string, w: number, a: number) {
  'worklet';
  const p = stroke('#000000', w, a);
  p.setShader(Skia.Shader.MakeLinearGradient(vec(x0, y0), vec(x1, y1), [hexc(c0, a), hexc(c1, a)], null, TileMode.Clamp));
  return p;
}

/** Small dark marks on the payee badges (invented, not any payment network's logo). */
function badgeGlyph(canvas: SkCanvas, i: number, cx: number, cy: number, s: number, a: number) {
  'worklet';
  const ink = stroke('#111111', s * 0.2, a);
  const pa = Skia.Path.Make();
  if (i === 0) {
    // A bolt.
    pa.moveTo(cx - s * 0.35, cy - s * 0.35);
    pa.lineTo(cx + s * 0.35, cy - s * 0.35);
    pa.lineTo(cx - s * 0.05, cy + s * 0.4);
    pa.moveTo(cx - s * 0.2, cy + s * 0.02);
    pa.lineTo(cx + s * 0.18, cy + s * 0.02);
  } else if (i === 1) {
    // A V and a dot.
    pa.moveTo(cx - s * 0.42, cy - s * 0.35);
    pa.lineTo(cx - s * 0.12, cy + s * 0.38);
    pa.lineTo(cx + s * 0.18, cy - s * 0.35);
    canvas.drawCircle(cx + s * 0.42, cy + s * 0.3, s * 0.11, fill('#111111', a));
  } else if (i === 2) {
    // A leaf.
    pa.moveTo(cx - s * 0.3, cy + s * 0.4);
    pa.quadTo(cx - s * 0.35, cy - s * 0.4, cx + s * 0.35, cy - s * 0.38);
    pa.quadTo(cx + s * 0.3, cy + s * 0.35, cx - s * 0.3, cy + s * 0.4);
  } else {
    // A card.
    const p = fill('#111111', a);
    canvas.drawRRect(rrect(cx - s * 0.42, cy - s * 0.3, s * 0.84, s * 0.6, s * 0.12), p);
    canvas.drawRect(Skia.XYWHRect(cx - s * 0.42, cy - s * 0.12, s * 0.84, s * 0.12), fill('#FFFFFF', a));
    return;
  }
  canvas.drawPath(pa, ink);
}

export function drawBackdrop(canvas: SkCanvas, f: Frame, g: Geo, bannerPress: number) {
  'worklet';
  const U = g.U;
  const X = (v: number) => v * U;
  const Y = (v: number) => g.Y0 + v * U;
  const card = colors.card;

  // Settings button: a pale disc and a hexagon nut.
  const st = layout.settings;
  canvas.drawCircle(X(st.cx), Y(st.cy), X(st.d / 2), fill(card));
  const nut = layout.nut;
  const hex = Skia.Path.Make();
  for (let k = 0; k < 6; k++) {
    const an = (Math.PI / 3) * k - Math.PI / 2;
    const px = X(nut.cx) + Math.cos(an) * X(nut.r - nut.ring / 2);
    const py = Y(nut.cy) + Math.sin(an) * X(nut.r - nut.ring / 2);
    if (k === 0) hex.moveTo(px, py);
    else hex.lineTo(px, py);
  }
  hex.close();
  const nutPaint = stroke(colors.nut, X(nut.ring));
  canvas.drawPath(hex, nutPaint);
  canvas.drawCircle(X(nut.cx), Y(nut.cy), X(nut.hole), fill(card));

  // Avatar and its notification dot.
  const av = layout.avatar;
  drawOrb(canvas, X(av.cx), Y(av.cy), X(av.d / 2), orbs.avatar, 1);
  if (f.dot < 1) {
    const d = layout.dot;
    canvas.drawCircle(X(d.cx), Y(d.cy), X(d.d / 2) * (1 - 0.5 * f.dot), fill(colors.dot, 1 - f.dot));
  }

  // The verification banner.
  if (f.banner < 1) {
    const b = layout.banner;
    const a = 1 - f.banner;
    canvas.save();
    const s = (1 - 0.03 * f.banner) * (1 - 0.03 * bannerPress);
    canvas.translate(0, -X(30) * f.banner);
    about(canvas, X(b.x + b.w / 2), Y(b.y + b.h / 2), s);
    canvas.drawRRect(boxRR(b, g), fill(card, a));
    const bar = layout.bar;
    const h = X(bar.h);
    const x0 = X(bar.x0);
    const len = X(bar.x1 - bar.x0);
    canvas.drawRRect(rrect(x0, Y(bar.cy) - h / 2, len, h, h / 2), fill(colors.track, a));
    const frac = lerp(bar.rest, 1, f.fill);
    const fl = len * frac;
    const fade = Math.min(X(bar.fade) * (1 - f.fill), fl);
    const bp = Skia.Paint();
    bp.setAntiAlias(true);
    bp.setShader(
      Skia.Shader.MakeLinearGradient(
        vec(x0, 0),
        vec(x0 + fl, 0),
        [hexc(colors.barStart, a), hexc(colors.barEnd, a), hexc(colors.barEnd, 0)],
        [0, Math.max(0.01, (fl - fade) / fl), 1],
        TileMode.Clamp,
      ),
    );
    canvas.drawRRect(rrect(x0, Y(bar.cy) - h / 2, fl, h, h / 2), bp);
    canvas.restore();
  }

  // Receive / Send.
  [f.pill0, f.pill1].forEach((k, i) => {
    if (k <= 0.001) return;
    const pl = layout.pills[i];
    const cx = X(pl.x + pl.w / 2);
    const cy = Y(pl.y + pl.h / 2);
    canvas.save();
    about(canvas, cx, cy, 0.9 + 0.1 * k);
    const a = clamp01(k * 2);
    canvas.drawRRect(rrect(X(pl.x), Y(pl.y), X(pl.w), X(pl.h), X(pl.h / 2)), fill('#000000', a));
    canvas.drawPath(arrow(X(pl.icon.cx), Y(pl.icon.cy), X(24), i === 0 ? 1 : -1), stroke('#FFFFFF', X(6.2), a));
    canvas.restore();
  });

  // Transactions.
  const rise = (k: number) => X(48) * (1 - k);
  if (f.txHead > 0.001) {
    const sa = layout.seeAll;
    canvas.drawRRect(rrect(X(sa.x), Y(sa.y) + rise(f.txHead), X(sa.w), X(sa.h), X(sa.h / 2)), fill(colors.pill, f.txHead));
  }
  if (f.txCard > 0.001) {
    const tc = layout.txCard;
    const dy = rise(f.txCard);
    canvas.save();
    canvas.translate(0, dy);
    canvas.drawRRect(boxRR(tc, g), fill(card, f.txCard));
    canvas.drawRect(Skia.XYWHRect(X(tc.x), Y(layout.hairline) - X(0.75), X(tc.w), X(1.5)), fill(colors.hairline, f.txCard));
    canvas.restore();
  }
  [f.txRow0, f.txRow1].forEach((k, i) => {
    if (k <= 0.001) return;
    const lg = layout.txRows[i].logo;
    const cx = X(lg.cx);
    const cy = Y(lg.cy) + rise(k);
    if (i === 0) {
      const r = X(lg.d / 2);
      canvas.drawCircle(cx, cy, r, fill(colors.cafe, k));
      canvas.drawCircle(cx, cy, r * 0.84, stroke('#FFFFFF', X(3), 0.9 * k));
      // A monogram H with a small roof: the café's own mark.
      const w = stroke('#FFFFFF', X(7.5), k);
      w.setStrokeCap(StrokeCap.Butt);
      const m = Skia.Path.Make();
      const hh = r * 0.42;
      const hw = r * 0.3;
      m.moveTo(cx - hw, cy - hh * 0.55);
      m.lineTo(cx - hw, cy + hh);
      m.moveTo(cx + hw, cy - hh * 0.55);
      m.lineTo(cx + hw, cy + hh);
      m.moveTo(cx - hw, cy + hh * 0.15);
      m.lineTo(cx + hw, cy + hh * 0.15);
      canvas.drawPath(m, w);
      const roof = stroke('#FFFFFF', X(6), k);
      const rp = Skia.Path.Make();
      rp.moveTo(cx - hw * 1.45, cy - hh * 0.35);
      rp.lineTo(cx, cy - hh * 1.15);
      rp.lineTo(cx + hw * 1.45, cy - hh * 0.35);
      canvas.drawPath(rp, roof);
    } else {
      drawOrb(canvas, cx, cy, X(lg.d / 2), orbs.dieter, k);
    }
  });

  // Recent payees: three action cards become four payee cards.
  const cards = [f.card0, f.card1, f.card2];
  for (let i = 0; i < 3; i++) {
    canvas.drawRRect(boxRR(lerpBox(actionBox(i), payeeBox(i), cards[i]), g), fill(card));
  }
  if (f.card3 > 0.001) {
    const b4 = payeeBox(3);
    canvas.drawRRect(boxRR({ ...b4, x: b4.x + 150 * (1 - f.card3) }, g), fill(card, f.card3));
  }
  if (f.names > 0.001) {
    const s2 = layout.seeAll2;
    canvas.drawRRect(rrect(X(s2.x), Y(s2.y), X(s2.w), X(s2.h), X(s2.h / 2)), fill(colors.pill, f.names));
  }
  // Action icons.
  const ia = 1 - f.actionsOut;
  if (ia > 0.001) {
    const ac = layout.actions;
    for (let i = 0; i < 3; i++) {
      const b = actionBox(i);
      const cx = X(b.x + ac.iconDx[i]);
      const cy = Y(ac.iconCy);
      canvas.save();
      about(canvas, cx, cy, 0.94 + 0.06 * ia);
      const w = X(ac.stroke);
      if (i === 0) {
        const h = X(ac.plus / 2);
        canvas.drawLine(cx - h, cy, cx + h, cy, gradientStroke(cx - h, cy, cx + h, cy, '#5E9488', '#D19AEF', w, ia));
        canvas.drawLine(cx, cy - h, cx, cy + h, gradientStroke(cx, cy - h, cx, cy + h, '#D69CF2', '#5E8C78', w, ia));
      } else {
        const s = X(ac.arrow);
        const dir = i === 1 ? -1 : 1;
        const p = i === 1
          ? gradientStroke(cx - s / 2, cy + s / 2, cx + s / 2, cy - s / 2, '#6CC77A', '#6FD3DB', w, ia)
          : gradientStroke(cx + s / 2, cy - s / 2, cx - s / 2, cy + s / 2, '#4D82E0', '#A33CC8', w, ia);
        canvas.drawPath(arrow(cx, cy, s, dir), p);
      }
      canvas.restore();
    }
  }
  // Payee orbs and their badges.
  const pop = [f.orb0, f.orb1, f.orb2, f.orb3];
  const py = layout.payees;
  for (let i = 0; i < 4; i++) {
    const k = pop[i];
    if (k <= 0.001) continue;
    const b = payeeBox(i);
    const cx = X(b.x + b.w / 2);
    const cy = Y(py.orbCy);
    const a = clamp01(k * 3);
    canvas.save();
    about(canvas, cx, cy, 0.6 + 0.4 * k);
    drawOrb(canvas, cx, cy, X(py.orbD / 2), orbs.payees[i], a);
    const bx = cx + X(py.badgeOff);
    const by = cy + X(py.badgeOff);
    canvas.drawCircle(bx, by, X(py.badgeD / 2), fill('#FFFFFF', a));
    badgeGlyph(canvas, i, bx, by, X(19), a);
    canvas.restore();
  }

  // Analytics: More, the chart card and its plot.
  const mo = layout.more;
  const moY = lerp(mo.a.y, mo.b.y, f.reflow2);
  canvas.drawRRect(rrect(X(mo.x), Y(moY), X(mo.w), X(mo.h), X(mo.h / 2)), fill(colors.pill));
  const ch = layout.chart;
  const top = lerp(ch.a.y, ch.b.y, f.reflow2);
  canvas.drawRRect(boxRR({ x: ch.x, y: top, w: ch.w, h: ch.h, r: ch.r }, g), fill(card));
  const bottom = top + lerp(ch.plotA.bottom, ch.plotB.bottom, f.plot);

  // A: the empty week, a flat grey band under a dashed line.
  const aa = 1 - f.plotA;
  if (aa > 0.001) {
    const pa = ch.plotA;
    const y0 = top + pa.top;
    const ap = Skia.Paint();
    ap.setAntiAlias(true);
    ap.setShader(Skia.Shader.MakeLinearGradient(vec(0, Y(y0)), vec(0, Y(bottom)), [hexc(colors.plotTop, aa), hexc(card, aa)], null, TileMode.Clamp));
    canvas.drawRect(Skia.XYWHRect(X(pa.x0), Y(y0), X(pa.x1 - pa.x0), Y(bottom) - Y(y0)), ap);
    const dash = stroke(colors.dash, X(2.5), aa);
    dash.setStrokeCap(StrokeCap.Butt);
    dash.setPathEffect(Skia.PathEffect.MakeDash([X(6), X(4)], 0));
    canvas.drawLine(X(pa.x0), Y(y0), X(pa.x1), Y(y0), dash);
  }

  // B: the week so far in orange and the forecast in grey, growing up from the bottom.
  if (f.draw > 0.001 || f.forecast > 0.001) {
    const sy = 0.35 + 0.65 * f.plot;
    const dy = bottom - ch.lineBottom; // moves pin 14's bottom onto the current one
    const fa = f.forecast;
    if (fa > 0.001) {
      const fc = path(ch.forecast, g, dy, sy, ch.lineBottom);
      const area = fc.copy();
      area.lineTo(X(1134), Y(bottom));
      area.lineTo(X(470), Y(bottom));
      area.close();
      const gp = Skia.Paint();
      gp.setAntiAlias(true);
      gp.setShader(Skia.Shader.MakeLinearGradient(vec(0, Y(bottom - 245)), vec(0, Y(bottom)), [hexc(colors.forecastTop, fa), hexc(card, fa)], null, TileMode.Clamp));
      canvas.drawPath(area, gp);
      const dash = stroke(colors.dash, X(2.5), fa);
      dash.setPathEffect(Skia.PathEffect.MakeDash([X(6), X(4)], 0));
      canvas.drawPath(fc, dash);
      const tr = path(ch.trend, g, dy, sy, ch.lineBottom);
      const td = stroke(colors.dash, X(2.5), 0.8 * fa);
      td.setPathEffect(Skia.PathEffect.MakeDash([X(6), X(4)], 0));
      canvas.drawPath(tr, td);
    }
    if (f.draw > 0.001) {
      const ln = path(ch.line, g, dy, sy, ch.lineBottom);
      const area = ln.copy();
      area.lineTo(X(470), Y(bottom));
      area.lineTo(X(72), Y(bottom));
      area.close();
      canvas.save();
      const x0 = X(ch.line[0][0]) - X(4);
      const clipW = (X(470) - x0 + X(4)) * f.draw;
      canvas.clipRect(Skia.XYWHRect(x0, Y(top), clipW, Y(bottom + 40) - Y(top)), ClipOp.Intersect, true);
      canvas.drawPath(area, fill(colors.peach, 0.95));
      canvas.drawPath(ln, stroke(colors.orange, X(4.5)));
      canvas.restore();
    }
  }
}

/** The debit card's pose: centre in ref px, scale (1 = its banner size) and tilt in degrees. */
export function cardPose(f: Frame, cardPress: number, bannerPress: number) {
  'worklet';
  const c = layout.card;
  const u = f.fly;
  const a = c.a;
  const b = c.b;
  const k = c.ctrl;
  const v = 1 - u;
  let cx = v * v * a.cx + 2 * u * v * k.x + u * u * b.cx;
  let cy = v * v * a.cy + 2 * u * v * k.y + u * u * b.cy;
  let s = lerp(1, b.s, u) * (1 + 0.08 * f.lift) * (1 - f.squeeze);
  s *= 1 - 0.03 * cardPress * clamp01(u);
  if (u < 0.05 && bannerPress > 0) {
    // Resting in the banner: scale with it, about the banner's centre.
    const bn = layout.banner;
    const bs = 1 - 0.03 * bannerPress;
    const bcx = bn.x + bn.w / 2;
    const bcy = bn.y + bn.h / 2;
    cx = bcx + (cx - bcx) * bs;
    cy = bcy + (cy - bcy) * bs;
    s *= bs;
  }
  return { cx, cy, s, tilt: f.tilt };
}

export function drawHero(canvas: SkCanvas, f: Frame, g: Geo, cardPress: number, bannerPress: number) {
  'worklet';
  const U = g.U;
  const c = layout.card;
  const pose = cardPose(f, cardPress, bannerPress);
  const fly = clamp01(f.fly);
  const w = c.w * U;
  const h = c.h * U;
  const r = c.r * U;

  canvas.save();
  canvas.translate(pose.cx * U, g.Y0 + pose.cy * U);
  canvas.rotate(pose.tilt, 0, 0);
  canvas.scale(pose.s, pose.s);
  canvas.translate(-w / 2, -h / 2);

  // Two shadows: a soft ambient one that grows while the card is lifted, a tight contact one.
  const amb = Skia.Paint();
  amb.setAntiAlias(true);
  const sig = lerp(lerp(9, 4, fly), 20, f.lift) * U;
  amb.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sig, true));
  amb.setColor(hexc('#2A4466', lerp(lerp(0.42, 0.2, fly), 0.26, f.lift)));
  const oy = lerp(lerp(13, 5, fly), 34, f.lift) * U;
  canvas.drawRRect(rrect(w * 0.06, oy + h * 0.06, w * 0.88, h * 0.9, r), amb);
  const contact = Skia.Paint();
  contact.setAntiAlias(true);
  contact.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2.5 * U, true));
  contact.setColor(hexc('#1A2A40', lerp(lerp(0.22, 0.16, fly), 0, f.lift)));
  canvas.drawRRect(rrect(w * 0.02, 2 * U, w * 0.96, h, r), contact);

  // The plastic: deep blue at the top to a cyan glow near the bottom edge.
  const body = rrect(0, 0, w, h, r);
  const bp = Skia.Paint();
  bp.setAntiAlias(true);
  bp.setShader(
    Skia.Shader.MakeLinearGradient(
      vec(0, 0),
      vec(0, h),
      ['#2062D8', '#134BCA', '#155DD3', '#1174E2', '#0C8CF2', '#0C9FFF', '#09B5F9', '#11E4FE', '#12FDFE', '#2FF5EC'].map((s) => hexc(s)),
      [0, 0.09, 0.22, 0.38, 0.54, 0.7, 0.79, 0.87, 0.92, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawRRect(body, bp);
  canvas.save();
  canvas.clipRRect(body, ClipOp.Intersect, true);
  // A soft wave of light across the lower half, and darker side edges.
  const wave = Skia.Paint();
  wave.setAntiAlias(true);
  wave.setShader(
    Skia.Shader.MakeRadialGradient(vec(w * 0.55, h * 1.05), w * 0.75, [hexc('#7FF6FF', 0.35), hexc('#7FF6FF', 0)], null, TileMode.Clamp),
  );
  canvas.drawRect(Skia.XYWHRect(0, 0, w, h), wave);
  const top = Skia.Paint();
  top.setAntiAlias(true);
  top.setShader(Skia.Shader.MakeLinearGradient(vec(0, 0), vec(0, 3 * U), [hexc('#FFFFFF', 0.28), hexc('#FFFFFF', 0)], null, TileMode.Clamp));
  canvas.drawRect(Skia.XYWHRect(0, 0, w, 3 * U), top);
  // The landing sheen.
  if (f.sheen > 0 && f.sheen < 1) {
    const sx = lerp(-0.6 * w, 1.6 * w, easeInOut(f.sheen));
    const sp = Skia.Paint();
    sp.setAntiAlias(true);
    sp.setShader(
      Skia.Shader.MakeLinearGradient(
        vec(sx - 0.22 * w, 0),
        vec(sx + 0.22 * w, 0.35 * h),
        [hexc('#FFFFFF', 0), hexc('#FFFFFF', 0.55), hexc('#FFFFFF', 0)],
        [0, 0.5, 1],
        TileMode.Clamp,
      ),
    );
    canvas.drawRect(Skia.XYWHRect(0, 0, w, h), sp);
  }
  canvas.restore();

  // The asterisk, top left.
  const ax = 32 * U;
  const ay = 33 * U;
  const arm = 11.5 * U;
  const ink = stroke('#FFFFFF', 6 * U);
  for (let k = 0; k < 3; k++) {
    const an = (Math.PI / 3) * k + Math.PI / 2;
    canvas.drawLine(ax - Math.cos(an) * arm, ay - Math.sin(an) * arm, ax + Math.cos(an) * arm, ay + Math.sin(an) * arm, ink);
  }
  // An invented network mark: two overlapping rounded squares, coral and amber, on a white rim.
  const mx = 192 * U;
  const my = 28 * U;
  const sq = 21 * U;
  canvas.drawRRect(rrect(mx - 19 * U, my - 13.5 * U, 38 * U, 27 * U, 8 * U), fill('#FFFFFF', 0.95));
  canvas.drawRRect(rrect(mx - 16.5 * U, my - sq / 2, sq, sq, 6 * U), fill('#F23B2E'));
  canvas.drawRRect(rrect(mx - 4.5 * U, my - sq / 2, sq, sq, 6 * U), fill('#FFA41C', 0.9));
  canvas.restore();
}
