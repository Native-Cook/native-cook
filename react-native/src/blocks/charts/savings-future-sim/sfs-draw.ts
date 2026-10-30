// Savings Future Sim — drawing, every frame, in the pin's pixels (see sfs-theme.ts). One path is both
// the gauge's arc and the growth curve: each of its points slides from one shape to the other.

import { ClipOp, PaintStyle, type SkCanvas, type SkFont, type SkTypeface, Skia, StrokeCap, StrokeJoin, TileMode } from '@shopify/react-native-skia';

import { balance, type Frame, LOOP, mix, smooth } from './sfs-motion';
import { C, COPY, PIN_W, PLAN, S, UI } from './sfs-theme';

export type Faces = { regular: SkTypeface; medium: SkTypeface; semi: SkTypeface; bold: SkTypeface };
export type Res = { f: { joined: SkFont; title: SkFont; value: SkFont; pct: SkFont; label: SkFont; grow: SkFont; body: SkFont; button: SkFont; axis: SkFont; bubble: SkFont; skip: SkFont } };

export function makeRes(f: Faces): Res {
  return {
    f: {
      joined: Skia.Font(f.semi, 30),
      title: Skia.Font(f.semi, 40),
      value: Skia.Font(f.bold, 104),
      pct: Skia.Font(f.bold, 62),
      label: Skia.Font(f.regular, 30),
      grow: Skia.Font(f.semi, 32),
      body: Skia.Font(f.regular, 29),
      button: Skia.Font(f.semi, 32),
      axis: Skia.Font(f.medium, 21),
      bubble: Skia.Font(f.semi, 22),
      skip: Skia.Font(f.medium, 31),
    },
  };
}

export type Geo = { w: number; h: number; s: number };

const TAU = Math.PI * 2;
const N = 72;

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
function money(v: number) {
  'worklet';
  const s = String(Math.round(v));
  let out = '';
  for (let i = 0; i < s.length; i++) {
    out += s[i];
    const left = s.length - 1 - i;
    if (left > 0 && left % 3 === 0) out += ',';
  }
  return '$' + out;
}
function rateText(rate: number) {
  'worklet';
  const r = Math.round(rate * 100) / 100;
  const w = Math.floor(r);
  const d = Math.round((r - w) * 100);
  return `${w},${d < 10 ? '0' : ''}${d}`;
}
/** Where the rate puts the knob on the arc (0 … 1 along it): 6 % is about a third of the way. */
export function rateFrac(rate: number) {
  'worklet';
  return 0.1 + 0.125 * (rate - PLAN.minRate);
}

// ------------------------------------------------------------------ the arc ⇄ curve

/** Point u (0…1) of the shape at morph k: on the arc, on the curve, or in between. */
function shapePoint(u: number, k: number, rate: number) {
  'worklet';
  const G = UI.gauge;
  const th = Math.PI + Math.PI * u;
  const ax = G.x + G.r * Math.cos(th);
  const ay = G.y + G.r * Math.sin(th);
  const Ch = UI.chart;
  const top = balance(PLAN.years, PLAN.maxRate);
  const base = PLAN.start;
  const v = (balance(u * PLAN.years, rate) - base) / (top - base);
  const gx = Ch.x + u * Ch.w;
  const gy = Ch.y + Ch.h - Ch.h * v;
  // the arc opens from its left end: points further along follow a little later
  const ku = smooth(k * 1.35 - u * 0.35);
  return { x: mix(ax, gx, ku), y: mix(ay, gy, ku), k: ku };
}

function drawShape(c: SkCanvas, f: Frame) {
  'worklet';
  const k = f.morph;
  const split = mix(rateFrac(f.rate), (f.year - 0) / PLAN.years, smooth(k));
  const pts: { x: number; y: number }[] = [];
  for (let i = 0; i <= N; i++) pts.push(shapePoint(i / N, k, f.rate));
  const width = mix(UI.gauge.w, 7, smooth(k));
  // under the curve: a purple wash, bright up to the scrubber
  if (k > 0.02) {
    const Ch = UI.chart;
    const fill = Skia.Path.Make();
    fill.moveTo(pts[0].x, Ch.y + Ch.h);
    for (let i = 0; i <= N; i++) fill.lineTo(pts[i].x, pts[i].y);
    fill.lineTo(pts[N].x, Ch.y + Ch.h);
    fill.close();
    const fp = Skia.Paint();
    fp.setAntiAlias(true);
    fp.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: Ch.y }, { x: 0, y: Ch.y + Ch.h }, [col(C.purple, 0.38 * k), col(C.purple, 0)], null, TileMode.Clamp));
    const sx = Ch.x + split * Ch.w;
    c.save();
    c.clipRect(Skia.XYWHRect(Ch.x - 20, Ch.y - 60, sx - Ch.x + 20, Ch.h + 80), ClipOp.Intersect, true);
    c.drawPath(fill, fp);
    c.restore();
    c.save();
    c.clipRect(Skia.XYWHRect(sx, Ch.y - 60, Ch.w, Ch.h + 80), ClipOp.Intersect, true);
    c.drawPath(fill, paint(C.purple, 0.06 * k));
    c.restore();
  }
  // the grey rest (the arc's track, or the curve ahead of the scrubber), fading toward its far end
  const rest = Skia.Path.Make();
  const si = Math.max(0, Math.min(N, Math.floor(split * N)));
  for (let i = si; i <= N; i++) {
    if (i === si) rest.moveTo(pts[i].x, pts[i].y);
    else rest.lineTo(pts[i].x, pts[i].y);
  }
  const rp = Skia.Paint();
  rp.setAntiAlias(true);
  rp.setStyle(PaintStyle.Stroke);
  rp.setStrokeWidth(width);
  rp.setStrokeCap(StrokeCap.Butt);
  rp.setStrokeJoin(StrokeJoin.Round);
  rp.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: UI.gauge.y - UI.gauge.r }, { x: 0, y: UI.gauge.y + 10 }, [col(C.track, 1), col(C.track, mix(0.15, 1, k))], [0.6, 1], TileMode.Clamp));
  // on the curve, what lies ahead of the scrubber is a projection: dashed
  if (smooth(k) > 0.6) {
    rp.setStrokeCap(StrokeCap.Round);
    rp.setPathEffect(Skia.PathEffect.MakeDash([12, 12], 0));
    rp.setShader(null);
    rp.setColor(col('#6D6A95', 1));
  }
  c.drawPath(rest, rp);
  // milestones at 10, 20 and 30 years: small marks that pulse as the scrubber passes
  if (k > 0.5) {
    const ma = smooth((k - 0.5) * 2);
    for (const m of [10, 20, 30]) {
      const mp = shapePoint(m / PLAN.years, k, f.rate);
      const pulse = Math.exp(-((f.year - m) * (f.year - m)) / 1.2);
      c.drawCircle(mp.x, mp.y, 6, paint(f.year >= m ? C.purple : '#6D6A95', ma));
      if (pulse > 0.02) c.drawCircle(mp.x, mp.y, 8 + 16 * pulse, stroke(C.purple, 3, 0.7 * pulse * ma));
    }
  }
  // the purple part, from the start to the knob
  const lead = Skia.Path.Make();
  for (let i = 0; i <= si; i++) {
    if (i === 0) lead.moveTo(pts[i].x, pts[i].y);
    else lead.lineTo(pts[i].x, pts[i].y);
  }
  const knob = shapePoint(split, k, f.rate);
  lead.lineTo(knob.x, knob.y);
  const lp = Skia.Paint();
  lp.setAntiAlias(true);
  lp.setStyle(PaintStyle.Stroke);
  lp.setStrokeWidth(width);
  lp.setStrokeCap(StrokeCap.Butt);
  lp.setStrokeJoin(StrokeJoin.Round);
  lp.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: UI.gauge.y + 10 }, { x: 0, y: UI.gauge.y - UI.gauge.r }, [col(C.purpleDeep, mix(0.0, 1, k)), col(C.purple, 1)], [0, 0.6], TileMode.Clamp));
  c.drawPath(lead, lp);
  // the scrubber's line down to the axis
  if (k > 0.5) {
    const a = smooth((k - 0.5) * 2);
    const Ch = UI.chart;
    for (let y = knob.y + 26; y < Ch.y + Ch.h; y += 14) c.drawLine(knob.x, y, knob.x, Math.min(Ch.y + Ch.h, y + 7), stroke(C.purple, 2, 0.6 * a));
  }
  // the knob: it breathes at rest, grows while held
  const breathe = 1 + 0.06 * Math.sin((TAU * 3 * f.amb) / LOOP);
  const kr = 22 * breathe * (1 + 0.2 * f.knob);
  c.drawCircle(knob.x, knob.y, kr + 7, paint(C.card));
  c.drawCircle(knob.x, knob.y, kr, paint(C.purple));
  return knob;
}

function drawValue(c: SkCanvas, r: Res, f: Frame, knob: { x: number; y: number }) {
  'worklet';
  const k = smooth(f.morph * 1.3 - 0.15);
  const cx = UI.gauge.x;
  // the rate (gauge) and the balance (curve) cross over
  if (k < 0.99) {
    const a = 1 - k;
    const rt = rateText(f.rate);
    const vw = r.f.value.measureText(rt).width;
    const pw = r.f.pct.measureText('%').width;
    const x0 = cx - (vw + pw + 6) / 2;
    text(c, rt, x0, UI.value.y - 12 * k, r.f.value, paint(C.white, a));
    text(c, '%', x0 + vw + 6, UI.value.y + 12 - 12 * k, r.f.pct, paint(C.white, a));
    text(c, COPY.rateLabel, cx, UI.label.y - 12 * k, r.f.label, paint(C.mute, a), 0.5);
  }
  if (k > 0.01) {
    const yi = Math.max(1, Math.floor(f.year));
    // the digits roll during the first third of each year
    const roll = smooth((f.year - yi) / 0.35);
    const now = money(balance(yi, f.rate));
    const prev = money(balance(Math.max(0, yi - 1), f.rate));
    const fnt = r.f.value;
    const y = UI.valueChart.y + 12 * (1 - k);
    const w = fnt.measureText(now).width * 0.82;
    let x = cx - w / 2;
    c.save();
    c.translate(x, y);
    c.scale(0.82, 0.82);
    c.translate(-x, -y);
    c.save();
    c.clipRect(Skia.XYWHRect(x - 10, y - 70, w / 0.82 + 20, 140), ClipOp.Intersect, true);
    if (prev.length !== now.length || f.year < 1.02) text(c, now, x, y, fnt, paint(C.white, k));
    else {
      for (let i = 0; i < now.length; i++) {
        const a = prev[i];
        const b = now[i];
        const cw = fnt.measureText(b).width;
        if (a === b || roll >= 1) text(c, b, x, y, fnt, paint(C.white, k));
        else {
          text(c, a, x, y - 90 * roll, fnt, paint(C.white, k * (1 - roll)));
          text(c, b, x, y + 90 * (1 - roll), fnt, paint(C.white, k * roll));
        }
        x += cw;
      }
    }
    c.restore();
    c.restore();
    text(c, `in ${yi} ${yi === 1 ? 'year' : 'years'}`, cx, UI.valueChart.label + 12 * (1 - k), r.f.label, paint(C.mute, k), 0.5);
    // the axis under the curve
    const Ch = UI.chart;
    const ya = Ch.y + Ch.h + 26;
    for (const yr of [1, 10, 20, 30]) text(c, `${yr}y`, Ch.x + (yr / PLAN.years) * Ch.w, ya, r.f.axis, paint(C.mute, k * 0.8), 0.5);
    c.drawLine(Ch.x, Ch.y + Ch.h, Ch.x + Ch.w, Ch.y + Ch.h, stroke(C.divider, 2, k));
    // a bubble with the year above the knob
    const bw = 104;
    const by = knob.y - 52;
    c.drawRRect(rrect(knob.x - bw / 2, by - 21, bw, 42, 21), paint(C.chip, k));
    text(c, `Year ${yi}`, knob.x, by, r.f.bubble, paint(C.white, k), 0.5);
  }
}

function shareIcon(c: SkCanvas, i: number, x: number, y: number) {
  'worklet';
  const w = paint(C.white);
  if (i === 0) {
    c.drawCircle(x, y, 52, paint(C.green));
    const b = Skia.Path.Make();
    b.addRRect(rrect(x - 24, y - 20, 48, 34, 16));
    b.moveTo(x - 12, y + 12);
    b.lineTo(x - 18, y + 24);
    b.lineTo(x - 2, y + 14);
    b.close();
    c.drawPath(b, w);
  } else if (i === 1) {
    c.drawCircle(x, y, 52, paint(C.chip));
    const p = stroke(C.white, 5);
    c.drawLine(x - 8, y + 8, x + 8, y - 8, p);
    c.save();
    c.rotate(-45, x - 15, y + 7);
    c.drawRRect(rrect(x - 30, y - 1, 30, 16, 8), p);
    c.restore();
    c.save();
    c.rotate(-45, x + 15, y - 7);
    c.drawRRect(rrect(x, y - 15, 30, 16, 8), p);
    c.restore();
  } else if (i === 2) {
    c.drawCircle(x, y, 52, paint(C.blue));
    const pl = Skia.Path.Make();
    pl.moveTo(x - 24, y);
    pl.lineTo(x + 24, y - 20);
    pl.lineTo(x + 10, y + 22);
    pl.lineTo(x + 2, y + 6);
    pl.close();
    c.drawPath(pl, w);
  } else {
    c.drawCircle(x, y, 52, paint(C.chip));
    for (let k = -1; k <= 1; k++) c.drawCircle(x + k * 15, y, 5, paint('#C9C9D6'));
  }
}

export function drawScene(c: SkCanvas, r: Res, g: Geo, f: Frame) {
  'worklet';
  c.save();
  c.scale(g.s, g.s);
  c.scale(S, S);
  const H = g.h / g.s / S;
  const bg = Skia.Paint();
  bg.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: 0, y: H }, [col(C.bgTop), col(C.bg)], null, TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(0, 0, PIN_W, H), bg);
  for (const b of [UI.menu, UI.help]) c.drawCircle(b.x, b.y, b.r, paint(C.chip));
  for (let k = -1; k <= 1; k++) c.drawCircle(UI.menu.x + k * 12, UI.menu.y, 4, paint(C.white));
  c.drawCircle(UI.help.x, UI.help.y, 20, stroke(C.white, 3.5));
  text(c, '?', UI.help.x, UI.help.y, r.f.bubble, paint(C.white), 0.5);
  // joined
  const jw = r.f.joined.measureText(COPY.joined).width;
  const jx = PIN_W / 2 - (jw + 54) / 2;
  c.drawCircle(jx + 18, UI.joined.y, 18, paint(C.purple));
  c.drawLine(jx + 10, UI.joined.y, jx + 16, UI.joined.y + 6, stroke(C.white, 3.4));
  c.drawLine(jx + 16, UI.joined.y + 6, jx + 26, UI.joined.y - 6, stroke(C.white, 3.4));
  text(c, COPY.joined, jx + 54, UI.joined.y, r.f.joined, paint(C.white));
  for (let l = 0; l < 2; l++) text(c, COPY.title[l], PIN_W / 2, UI.title[l], r.f.title, paint(C.white), 0.5);
  const K = UI.card;
  c.drawRRect(rrect(K.x, K.y, K.w, K.h, K.r), paint(C.card));
  const knob = drawShape(c, f);
  drawValue(c, r, f, knob);
  c.drawLine(80, UI.divider, 656, UI.divider, stroke(C.divider, 2));
  text(c, COPY.grow, 81, UI.grow, r.f.grow, paint(C.white));
  const body = [`Explore what ${rateText(f.rate)}% could look like over`, COPY.body[1], COPY.body[2]];
  for (let l = 0; l < 3; l++) text(c, body[l], 81, UI.body[l], r.f.body, paint(C.body));
  // the button: presses to 0.96; while the curve shows it offers the way back
  const B = UI.button;
  const s = 1 - 0.04 * f.press;
  c.save();
  c.translate(B.x + B.w / 2, B.y + B.h / 2);
  c.scale(s, s);
  c.drawRRect(rrect(-B.w / 2, -B.h / 2, B.w, B.h, B.h / 2), paint(C.button));
  const ok = smooth(f.morph * 2 - 0.6);
  const labels = [COPY.button, 'Back to My Rate'];
  for (let i = 0; i < 2; i++) {
    const a = i === 0 ? 1 - ok : ok;
    if (a < 0.01) continue;
    const lw = r.f.button.measureText(labels[i]).width;
    const x0 = -(lw + 46) / 2;
    const dy = i === 0 ? -10 * ok : 10 * (1 - ok);
    for (let b = 0; b < 3; b++) c.drawRRect(rrect(x0 + b * 10, dy + 14 - (b + 1) * 9, 5, (b + 1) * 9, 2.5), paint(C.white, a));
    text(c, labels[i], x0 + 46, dy, r.f.button, paint(C.white, a));
  }
  c.restore();
  // the second card, with the share buttons over it
  const K2 = UI.card2;
  c.drawRRect(rrect(K2.x, K2.y, K2.w, K2.h, K2.r), paint(C.card));
  text(c, COPY.skip, 74, K2.y + 60, r.f.skip, paint(C.white));
  text(c, COPY.more, 662, K2.y + 60, r.f.skip, paint(C.purple), 1);
  c.drawLine(74, K2.y + 112, 662, K2.y + 112, stroke(C.divider, 2));
  for (let i = 0; i < 4; i++) shareIcon(c, i, UI.share.x[i], UI.share.y);
  c.restore();
}
