import { ClipOp, FillType, type SkCanvas, type SkFont, Skia, TileMode } from '@shopify/react-native-skia';

import { SCALE_MAX, SCALE_MIN, SPAN, WORD_AT, WORDS, angleOf, pressureAt } from './barometerMotion';
import { fill, grain, linear, radial, rgba, spaced, stroke, textWidth } from './barometerPaint';

/*
 The barometer's static art, baked once: the navy chart-room background, the brass aneroid dial
 (bezel, ivory face, engraved scale, words, maker's mark) and the 48-hour chart card. A second
 bake holds what sits on the glass: the reflections and the brass "set" hand with its knob.
 */

export const INK = 0x2b2620;
export const BRASS = 0xc89b4a;
export const AMBER = 0xe6bb62;
export const SEA = 0x9fc6c8;
export const PAPER = 0xeee5cf;

export type Fonts = {
  kicker: SkFont;
  readout: SkFont;
  unit: SkFont;
  word: SkFont;
  small: SkFont;
  scale: SkFont;
  dialWord: SkFont;
  maker: SkFont;
  chartTitle: SkFont;
  label: SkFont;
  tip: SkFont;
};

export type Geo = {
  w: number;
  h: number;
  top: number;
  cx: number;
  cy: number;
  /** Bezel outer radius, face radius, the scale's outer ring. */
  rb: number;
  rf: number;
  rs: number;
  card: { x: number; y: number; w: number; h: number };
  plot: { x: number; y: number; w: number; h: number };
  statsTop: number;
  readoutBase: number;
};

/** Plot range in hPa. */
export const PLOT_MIN = 980;
export const PLOT_MAX = 1030;

export function makeGeo(w: number, h: number, top: number, bottom: number): Geo {
  const headerTop = top + 12;
  const statsTop = h - Math.max(bottom, 12) - 12 - 56;
  const dialTop = headerTop + 104;
  const minChart = 168;
  const avail = statsTop - 12 - minChart - 20 - dialTop;
  const d = Math.max(220, Math.min(w - 48, 330, avail));
  const rb = d / 2;
  const cy = dialTop + rb;
  const cardY = cy + rb + 20;
  const card = { x: 16, y: cardY, w: w - 32, h: statsTop - 12 - cardY };
  const plot = { x: card.x + 16, y: card.y + 50, w: card.w - 16 - 50, h: card.h - 50 - 30 };
  return { w, h, top, cx: w / 2, cy, rb, rf: rb - 15, rs: rb - 26, card, plot, statsTop, readoutBase: headerTop + 66 };
}

export function plotX(g: Geo, hour: number) {
  'worklet';
  return g.plot.x + ((hour + SPAN) / (2 * SPAN)) * g.plot.w;
}

export function plotY(g: Geo, hpa: number) {
  'worklet';
  return g.plot.y + (1 - (hpa - PLOT_MIN) / (PLOT_MAX - PLOT_MIN)) * g.plot.h;
}

export function hourAtX(g: Geo, x: number) {
  'worklet';
  return ((x - g.plot.x) / g.plot.w) * 2 * SPAN - SPAN;
}

/** Point on the dial at angle a (degrees from twelve o'clock, clockwise) and radius r. */
function polar(g: Geo, a: number, r: number) {
  'worklet';
  const rad = (a * Math.PI) / 180;
  return { x: g.cx + Math.sin(rad) * r, y: g.cy - Math.cos(rad) * r };
}

/** Text centred on angle a along a circle of radius r, tops outward. */
function arcText(canvas: SkCanvas, g: Geo, str: string, a: number, r: number, font: SkFont, paint: ReturnType<typeof fill>, track: number) {
  const ids = font.getGlyphIDs(str);
  const widths = font.getGlyphWidths(ids);
  let total = 0;
  for (let i = 0; i < widths.length; i += 1) total += widths[i] + (i < widths.length - 1 ? track : 0);
  let s = -total / 2;
  for (let i = 0; i < str.length && i < widths.length; i += 1) {
    const mid = s + widths[i] / 2;
    const ang = a + ((mid / r) * 180) / Math.PI;
    canvas.save();
    canvas.translate(g.cx, g.cy);
    canvas.rotate(ang, 0, 0);
    canvas.drawText(str[i], -widths[i] / 2, -r, paint, font);
    canvas.restore();
    s += widths[i] + track;
  }
}

function drawBackground(canvas: SkCanvas, g: Geo) {
  canvas.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), linear(0, 0, 0, g.h, [rgba(0x13232b), rgba(0x0f1c24), rgba(0x0a1318)], [0, 0.5, 1]));
  // A warm lamp over the dial and a cool vignette at the corners.
  canvas.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), radial(g.cx, g.cy - g.rb * 0.4, g.w * 0.95, [rgba(0x6b5530, 0.22), rgba(0x3a3a2c, 0.08), rgba(0x000000, 0)], [0, 0.5, 1]));
  canvas.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), radial(g.cx, g.h * 0.45, g.h * 0.75, [rgba(0x000000, 0), rgba(0x02070a, 0.1), rgba(0x02070a, 0.55)], [0, 0.6, 1]));
  grain(canvas, 0, 0, g.w, g.h, 0.07, 3);
}

function drawDial(canvas: SkCanvas, g: Geo, f: Fonts) {
  const { cx, cy, rb, rf, rs } = g;
  // Two shadows: soft ambient and a tight contact one.
  canvas.drawCircle(cx, cy + 16, rb, fill(0x000000, 0.45, 22));
  canvas.drawCircle(cx, cy + 2, rb, fill(0x000000, 0.55, 3));
  // Brass bezel: a sweep of light and dark bands reads as turned metal.
  const bezel = Skia.Paint();
  bezel.setAntiAlias(true);
  bezel.setShader(
    Skia.Shader.MakeSweepGradient(
      cx,
      cy,
      [rgba(0x7a5622), rgba(0xf4dc9f), rgba(0xa6782f), rgba(0x5e4119), rgba(0xd9b56c), rgba(0xfbe7b5), rgba(0x8c6428), rgba(0x7a5622)],
      [0, 0.12, 0.27, 0.42, 0.58, 0.7, 0.86, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawCircle(cx, cy, rb, bezel);
  canvas.drawCircle(cx, cy, rb, radial(cx, cy - rb * 0.3, rb * 1.3, [rgba(0xffffff, 0.18), rgba(0xffffff, 0), rgba(0x2a1a06, 0.35)], [0, 0.5, 1]));
  canvas.drawCircle(cx, cy, rb - 0.6, stroke(0xfff1c8, 1.2, 0.55));
  canvas.drawCircle(cx, cy, rf + 4, stroke(0x4a3312, 2.5, 0.8));
  canvas.drawCircle(cx, cy, rf + 1.4, stroke(0xf6dfa4, 1, 0.7));
  // Ivory face with a printed-paper grain.
  canvas.drawCircle(cx, cy, rf, radial(cx - rf * 0.15, cy - rf * 0.2, rf * 1.1, [rgba(0xf6eedb), rgba(0xece1c8), rgba(0xd8c7a3)], [0, 0.62, 1]));
  canvas.save();
  const clip = Skia.PathBuilder.Make().addCircle(cx, cy, rf).build();
  canvas.clipPath(clip, ClipOp.Intersect, true);
  grain(canvas, cx - rf, cy - rf, rf * 2, rf * 2, 0.16, 11);
  // The bezel's shadow falls on the face.
  canvas.drawCircle(cx, cy, rf, radial(cx, cy + 4, rf, [rgba(0x3a2a12, 0), rgba(0x3a2a12, 0), rgba(0x3a2a12, 0.32)], [0, 0.86, 1]));
  canvas.restore();

  // Coloured zone band just inside the scale.
  const zones: [number, number, number][] = [
    [SCALE_MIN, 990, 0x8e3b2e],
    [990, 1005, 0x3e5a73],
    [1005, 1018, 0x7d7158],
    [1018, 1035, 0xa47b2c],
    [1035, SCALE_MAX, 0xb5622a],
  ];
  const bandR = rs - 3;
  for (const [a, b, c] of zones) {
    const start = angleOf(a) - 90;
    const sweep = angleOf(b) - angleOf(a);
    const path = Skia.PathBuilder.Make().addArc(Skia.XYWHRect(cx - bandR, cy - bandR, bandR * 2, bandR * 2), start, sweep).build();
    canvas.drawPath(path, stroke(c, 4, 0.55));
  }
  // The scale: two engraved rings and ticks every hPa, longer every 5 and 10.
  const ring = (r: number) => Skia.PathBuilder.Make().addArc(Skia.XYWHRect(cx - r, cy - r, r * 2, r * 2), angleOf(SCALE_MIN) - 90, angleOf(SCALE_MAX) - angleOf(SCALE_MIN)).build();
  canvas.drawPath(ring(rs), stroke(INK, 1.1, 0.9));
  canvas.drawPath(ring(rs - 6), stroke(INK, 0.6, 0.6));
  const ticks = Skia.PathBuilder.Make();
  const majors = Skia.PathBuilder.Make();
  for (let v = SCALE_MIN; v <= SCALE_MAX; v += 1) {
    const a = angleOf(v);
    const len = v % 10 === 0 ? 13 : v % 5 === 0 ? 9 : 5;
    const p0 = polar(g, a, rs);
    const p1 = polar(g, a, rs - len);
    (v % 10 === 0 ? majors : ticks).moveTo(p0.x, p0.y).lineTo(p1.x, p1.y);
  }
  canvas.drawPath(ticks.build(), stroke(INK, 0.7, 0.8));
  canvas.drawPath(majors.build(), stroke(INK, 1.6, 0.95));
  const num = fill(INK, 0.92);
  for (let v = SCALE_MIN; v <= SCALE_MAX; v += 10) arcText(canvas, g, `${v}`, angleOf(v), rs - 26, f.scale, num, 0.2);
  const word = fill(0x3a2d22, 0.95);
  for (let i = 0; i < WORDS.length; i += 1) arcText(canvas, g, WORDS[i].toUpperCase(), angleOf(WORD_AT[i]), rs - 48, f.dialWord, word, 1.6);
  // Maker's mark and a guilloché rosette around the arbor.
  const mark = fill(0x4a3b2c, 0.85);
  spaced(canvas, 'SALTMARSH & CO.', cx, cy + rf * 0.36, f.maker, mark, 1.6, 0.5);
  spaced(canvas, 'ANEROID · HPA', cx, cy + rf * 0.36 + 13, f.maker, fill(0x4a3b2c, 0.55), 1.4, 0.5);
  for (let i = 0; i < 24; i += 1) {
    const a = (i * 360) / 24;
    const p = polar(g, a, rf * 0.11);
    canvas.drawCircle(p.x, p.y, rf * 0.075, stroke(0x6b5636, 0.5, 0.35));
  }
}

function drawCard(canvas: SkCanvas, g: Geo, f: Fonts) {
  const { card, plot } = g;
  const rr = Skia.RRectXY(Skia.XYWHRect(card.x, card.y, card.w, card.h), 22, 22);
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(card.x, card.y + 10, card.w, card.h), 22, 22), fill(0x000000, 0.35, 18));
  canvas.drawRRect(rr, linear(0, card.y, 0, card.y + card.h, [rgba(0x1b2e36), rgba(0x15252c), rgba(0x101d23)], [0, 0.5, 1]));
  canvas.drawRRect(rr, stroke(0xffffff, 1, 0.07));
  canvas.drawLine(card.x + 22, card.y + 0.8, card.x + card.w - 22, card.y + 0.8, stroke(0xffffff, 1, 0.1));
  canvas.drawText('48-hour pressure', card.x + 16, card.y + 28, fill(0xf1ead9), f.chartTitle);
  // Legend: past (solid) and forecast (dashed).
  const lx = card.x + card.w - 16;
  const fw = textWidth(f.label, 'Forecast');
  canvas.drawText('Forecast', lx - fw, card.y + 27, fill(0xd7d0bf, 0.6), f.label);
  const dash = stroke(SEA, 2, 0.9);
  dash.setPathEffect(Skia.PathEffect.MakeDash([4, 3], 0));
  canvas.drawLine(lx - fw - 24, card.y + 23, lx - fw - 8, card.y + 23, dash);
  const pw = textWidth(f.label, 'Past');
  const px = lx - fw - 36 - pw;
  canvas.drawText('Past', px, card.y + 27, fill(0xd7d0bf, 0.6), f.label);
  canvas.drawLine(px - 22, card.y + 23, px - 8, card.y + 23, stroke(AMBER, 2.2));

  // Grid and the right-hand scale.
  for (let v = 990; v <= 1030; v += 10) {
    const y = plotY(g, v);
    const grid = stroke(0xffffff, 1, 0.06);
    grid.setPathEffect(Skia.PathEffect.MakeDash([2, 4], 0));
    canvas.drawLine(plot.x, y, plot.x + plot.w, y, grid);
    canvas.drawText(`${v}`, plot.x + plot.w + 10, y + 4, fill(0xd7d0bf, 0.45), f.label);
  }
  // The lines: area under the past, an uncertainty fan over the forecast.
  const STEP = 0.5;
  const past = Skia.PathBuilder.Make();
  const pastArea = Skia.PathBuilder.Make().moveTo(plotX(g, -SPAN), plot.y + plot.h);
  for (let hr = -SPAN; hr <= 0.001; hr += STEP) {
    const x = plotX(g, hr);
    const y = plotY(g, pressureAt(hr));
    if (hr === -SPAN) past.moveTo(x, y);
    else past.lineTo(x, y);
    pastArea.lineTo(x, y);
  }
  pastArea.lineTo(plotX(g, 0), plot.y + plot.h).close();
  canvas.drawPath(pastArea.build(), linear(0, plot.y, 0, plot.y + plot.h, [rgba(AMBER, 0.3), rgba(0xb98a3c, 0.1), rgba(AMBER, 0)], [0, 0.55, 1]));
  const fan = Skia.PathBuilder.Make();
  for (let hr = 0; hr <= SPAN + 0.001; hr += STEP) fan[hr === 0 ? 'moveTo' : 'lineTo'](plotX(g, hr), plotY(g, pressureAt(hr) + (hr / SPAN) * 5));
  for (let hr = SPAN; hr >= -0.001; hr -= STEP) fan.lineTo(plotX(g, hr), plotY(g, pressureAt(hr) - (hr / SPAN) * 5));
  fan.close();
  canvas.drawPath(fan.build(), fill(SEA, 0.1));
  const future = Skia.PathBuilder.Make();
  for (let hr = 0; hr <= SPAN + 0.001; hr += STEP) future[hr === 0 ? 'moveTo' : 'lineTo'](plotX(g, hr), plotY(g, pressureAt(hr)));
  canvas.drawPath(past.build(), stroke(AMBER, 4, 0.25, 3));
  canvas.drawPath(past.build(), stroke(AMBER, 2.2));
  const fdash = stroke(SEA, 2, 0.95);
  fdash.setPathEffect(Skia.PathEffect.MakeDash([5, 4], 0));
  canvas.drawPath(future.build(), fdash);
  // Storm note at the low, and the now line.
  const lowX = plotX(g, -14);
  const lowY = plotY(g, 985);
  canvas.drawCircle(lowX, lowY, 2.5, fill(0xe0775c));
  const gw = textWidth(f.label, 'Gale 985');
  canvas.drawText('Gale 985', lowX - gw / 2, lowY + 18, fill(0xe0775c, 0.85), f.label);
  const nowLine = stroke(0xffffff, 1, 0.22);
  nowLine.setPathEffect(Skia.PathEffect.MakeDash([1, 3], 0));
  canvas.drawLine(plotX(g, 0), plot.y - 6, plotX(g, 0), plot.y + plot.h, nowLine);
  const labels = ['−24 h', '−12 h', 'Now', '+12 h', '+24 h'];
  for (let i = 0; i < labels.length; i += 1) {
    const x = plotX(g, -SPAN + i * 12);
    const lw = textWidth(f.label, labels[i]);
    const ax = i === 0 ? x : i === labels.length - 1 ? x - lw : x - lw / 2;
    canvas.drawText(labels[i], ax, plot.y + plot.h + 20, fill(0xd7d0bf, i === 2 ? 0.8 : 0.45), f.label);
  }
}

/** Everything under the needle. */
export function drawBase(canvas: SkCanvas, g: Geo, f: Fonts) {
  drawBackground(canvas, g);
  const ink = fill(0xe9e0cc, 0.62);
  spaced(canvas, 'SALTMARSH HARBOUR', 24, g.top + 24, f.kicker, ink, 2.2);
  canvas.drawText('Pressure · updated 13:52', 24, g.readoutBase + 24, fill(0xe9e0cc, 0.5), f.small);
  drawDial(canvas, g, f);
  drawCard(canvas, g, f);
}

/** What sits on and over the glass, in the dial's own box (origin at the bezel's top-left). */
export function drawGlass(canvas: SkCanvas, g: Geo, setAngle: number) {
  const ox = g.cx - g.rb;
  const oy = g.cy - g.rb;
  canvas.save();
  canvas.translate(-ox, -oy);
  const { cx, cy, rf } = g;
  // Brass set hand, under the glass's reflections.
  canvas.save();
  canvas.translate(cx, cy);
  canvas.rotate(setAngle, 0, 0);
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-1.6, -rf * 0.72 + 2, 3.2, rf * 0.72), 1.6, 1.6), fill(0x000000, 0.3, 2));
  const hand = Skia.PathBuilder.Make()
    .moveTo(-2, 0)
    .lineTo(-1.2, -rf * 0.6)
    .lineTo(-5, -rf * 0.6)
    .lineTo(0, -rf * 0.74)
    .lineTo(5, -rf * 0.6)
    .lineTo(1.2, -rf * 0.6)
    .lineTo(2, 0)
    .close()
    .build();
  canvas.drawPath(hand, linear(-4, 0, 4, 0, [rgba(0x8a6526), rgba(0xf3d695), rgba(0x9c7330)], [0, 0.5, 1]));
  canvas.restore();
  // Knurled set knob on the glass.
  canvas.drawCircle(cx, cy + 2, 13, fill(0x000000, 0.4, 3));
  canvas.drawCircle(cx, cy, 13, radial(cx - 4, cy - 5, 16, [rgba(0xfbe6ae), rgba(0xc89b4a), rgba(0x6e4d1c)], [0, 0.55, 1]));
  for (let i = 0; i < 28; i += 1) {
    const a = (i * Math.PI * 2) / 28;
    canvas.drawLine(cx + Math.cos(a) * 9.5, cy + Math.sin(a) * 9.5, cx + Math.cos(a) * 12.6, cy + Math.sin(a) * 12.6, stroke(0x4a3312, 0.7, 0.45));
  }
  canvas.drawCircle(cx, cy, 8.5, radial(cx - 2, cy - 3, 10, [rgba(0xfff3cf), rgba(0xd4a85a), rgba(0x8a6526)], [0, 0.6, 1]));
  // Glass: a crescent highlight top-left, a broad soft glare, a bright rim and a darker lower rim.
  canvas.save();
  const clip = Skia.PathBuilder.Make().addCircle(cx, cy, rf).build();
  canvas.clipPath(clip, ClipOp.Intersect, true);
  const crescent = Skia.PathBuilder.Make()
    .addCircle(cx, cy, rf - 3)
    .addCircle(cx + rf * 0.12, cy + rf * 0.14, rf * 0.98)
    .setFillType(FillType.EvenOdd)
    .build();
  canvas.drawPath(crescent, linear(cx - rf, cy - rf, cx, cy, [rgba(0xffffff, 0.42), rgba(0xffffff, 0.12), rgba(0xffffff, 0)], [0, 0.45, 1]));
  canvas.drawRect(
    Skia.XYWHRect(cx - rf, cy - rf, rf * 2, rf * 2),
    linear(cx - rf * 0.6, cy - rf, cx + rf * 0.3, cy + rf * 0.6, [rgba(0xffffff, 0), rgba(0xffffff, 0.09), rgba(0xffffff, 0.02), rgba(0xffffff, 0)], [0.25, 0.42, 0.52, 0.62]),
  );
  canvas.drawCircle(cx, cy, rf, radial(cx, cy, rf, [rgba(0x0b1a20, 0), rgba(0x0b1a20, 0), rgba(0x0b1a20, 0.18)], [0, 0.9, 1]));
  canvas.restore();
  canvas.drawCircle(cx, cy, rf - 1, stroke(0xffffff, 1.2, 0.22));
  canvas.restore();
}
