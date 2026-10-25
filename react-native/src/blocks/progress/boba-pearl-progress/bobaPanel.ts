import { BlurStyle, PaintStyle, Skia, StrokeCap, StrokeJoin, TileMode, type SkCanvas, type SkFont } from '@shopify/react-native-skia';

import { ETA, type Geo, STATUS, STEPS, type Scene } from './bobaMotion';

/*
 The order screen around the cup: a warm counter backdrop (recorded once) and, per frame, the
 percentage, the status line and the four-step tracker. Text cross-fades (0.3 s, 6 pt drift) when
 the step changes; the percentage cross-fades 100 → 0 when the next cup arrives.
 */

export type Fonts = { pct: SkFont; status: SkFont; eta: SkFont; step: SkFont };

const INK = '#3B2418';
const INK_SOFT = '#8C6E5C';
/** Where each step sits on the track, and the percentage it starts at. */
const STEP_AT = [0, 60, 82, 100];

function fill(color: string) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  return p;
}

function widthOf(font: SkFont, text: string) {
  'worklet';
  const ws = font.getGlyphWidths(font.getGlyphIDs(text));
  let s = 0;
  for (let i = 0; i < ws.length; i += 1) s += ws[i];
  return s;
}

export function drawBackdrop(c: SkCanvas, g: Geo) {
  const bg = Skia.Paint();
  bg.setShader(
    Skia.Shader.MakeLinearGradient(Skia.Point(0, 0), Skia.Point(0, g.h), ['#F8EBDD', '#F2DCC6', '#E9C6AA'].map((h) => Skia.Color(h)), [0, 0.55, 1], TileMode.Clamp),
  );
  c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), bg);
  // A soft light behind the cup.
  const spot = Skia.Paint();
  spot.setShader(
    Skia.Shader.MakeRadialGradient(
      Skia.Point(g.cx, g.top + g.cupH * 0.45),
      g.cupH * 0.85,
      [Skia.Color('rgba(255,250,242,0.75)'), Skia.Color('rgba(255,246,236,0.25)'), Skia.Color('rgba(255,246,236,0)')],
      [0, 0.5, 1],
      TileMode.Clamp,
    ),
  );
  c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), spot);
  // The counter's edge behind the cup's base.
  const counter = Skia.Paint();
  counter.setShader(
    Skia.Shader.MakeLinearGradient(Skia.Point(0, g.base - 26 * g.u), Skia.Point(0, g.base + 40 * g.u), ['rgba(150,96,62,0)', 'rgba(150,96,62,0.10)', 'rgba(150,96,62,0)'].map((h) => Skia.Color(h)), [0, 0.45, 1], TileMode.Clamp),
  );
  c.drawRect(Skia.XYWHRect(0, g.base - 26 * g.u, g.w, 66 * g.u), counter);
  const grain = Skia.Paint();
  grain.setShader(Skia.Shader.MakeFractalNoise(0.9, 0.9, 2, 3, 0, 0));
  grain.setAlphaf(0.045);
  c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), grain);
}

function trackX(g: Geo, pct: number) {
  'worklet';
  const x0 = 32;
  const x1 = g.w - 32;
  let k = 0;
  for (let i = 0; i < 3; i += 1) {
    if (pct >= STEP_AT[i]) k = i + Math.min(1, (pct - STEP_AT[i]) / (STEP_AT[i + 1] - STEP_AT[i]));
  }
  return x0 + ((x1 - x0) * k) / 3;
}

export function drawPanel(c: SkCanvas, g: Geo, sc: Scene, fonts: Fonts) {
  'worklet';
  // Percentage, cross-fading during the cup swap.
  const py = g.percentY + 34;
  const fading = sc.pct[2] > 0 && sc.pct[2] < 1;
  if (fading) {
    const a = fill(INK);
    a.setAlphaf(1 - sc.pct[2]);
    c.drawText(`${sc.pct[0]}%`, 24, py - 8 * sc.pct[2], a, fonts.pct);
    const b = fill(INK);
    b.setAlphaf(sc.pct[2]);
    c.drawText(`${sc.pct[1]}%`, 24, py + 8 * (1 - sc.pct[2]), b, fonts.pct);
  } else {
    c.drawText(`${Math.floor(sc.percent)}%`, 24, py, fill(INK), fonts.pct);
  }
  // Status and time, right aligned, cross-fading on a step change.
  const rx = g.w - 24;
  for (const [idx, alpha, shift] of [
    [sc.prevStep, 1 - sc.stepFade, -6 * sc.stepFade],
    [sc.step, sc.stepFade, 6 * (1 - sc.stepFade)],
  ]) {
    if (alpha <= 0.01) continue;
    const s = fill(INK);
    s.setAlphaf(alpha);
    const e = fill(INK_SOFT);
    e.setAlphaf(alpha);
    c.drawText(STATUS[idx], rx - widthOf(fonts.status, STATUS[idx]), py - 18 + shift, s, fonts.status);
    c.drawText(ETA[idx], rx - widthOf(fonts.eta, ETA[idx]), py + shift, e, fonts.eta);
  }

  // The tracker.
  const y = g.stepsY;
  const x0 = 32;
  const x1 = g.w - 32;
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x0, y - 2, x1 - x0, 4), 2, 2), fill('rgba(59,36,24,0.12)'));
  const fx = trackX(g, sc.percent);
  const done = fill('#8A4F2C');
  done.setShader(Skia.Shader.MakeLinearGradient(Skia.Point(x0, 0), Skia.Point(x1, 0), ['#D29A68', '#A9653A', '#7D4423'].map((h) => Skia.Color(h)), [0, 0.5, 1], TileMode.Clamp));
  if (fx > x0 + 0.5) c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x0, y - 2, fx - x0, 4), 2, 2), done);
  const check = Skia.Paint();
  check.setAntiAlias(true);
  check.setStyle(PaintStyle.Stroke);
  check.setStrokeWidth(1.8);
  check.setStrokeCap(StrokeCap.Round);
  check.setStrokeJoin(StrokeJoin.Round);
  check.setColor(Skia.Color('#FFF6EC'));
  for (let i = 0; i < 4; i += 1) {
    const nx = x0 + ((x1 - x0) * i) / 3;
    const reached = sc.percent >= STEP_AT[i] - 0.01 && (i > 0 || sc.percent > 0.5);
    const active = sc.step === i;
    if (active) {
      const halo = fill('rgba(169,101,58,0.25)');
      halo.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 4, false));
      c.drawCircle(nx, y, 13, halo);
    }
    c.drawCircle(nx, y + 1, 9, fill('rgba(59,36,24,0.18)'));
    c.drawCircle(nx, y, 9, fill(reached ? '#8A4F2C' : '#F6E6D6'));
    if (!reached) {
      const ring = fill('rgba(59,36,24,0.22)');
      ring.setStyle(PaintStyle.Stroke);
      ring.setStrokeWidth(1.5);
      c.drawCircle(nx, y, 8.25, ring);
    } else if (i < sc.step || (i === 3 && sc.step === 3)) {
      const p = Skia.PathBuilder.Make();
      p.moveTo(nx - 3.6, y + 0.2);
      p.lineTo(nx - 1, y + 2.8);
      p.lineTo(nx + 3.8, y - 2.6);
      c.drawPath(p.build(), check);
    } else {
      c.drawCircle(nx, y, 3, fill('#FFF6EC'));
    }
    const label = STEPS[i];
    const lw = widthOf(fonts.step, label);
    const lx = Math.max(16, Math.min(g.w - 16 - lw, nx - lw / 2));
    c.drawText(label, lx, y + 28, fill(active ? INK : INK_SOFT), fonts.step);
  }
}
