import { ClipOp, type SkCanvas, type SkFont, Skia } from '@shopify/react-native-skia';

import { type Fonts, type Geo, AMBER, plotX, plotY } from './barometerArt';
import { NOW_HPA, WORDS, clamp, pressureAt, softRound, timeLabel, trendIndex, wordIndex } from './barometerMotion';
import { fill, linear, radial, rgba, stroke, textWidth } from './barometerPaint';

/** The parts of the barometer that move, drawn every frame over the baked art. */

const TRENDS = ['Falling', 'Steady', 'Rising'];

/**
 An odometer: each column rolls from the one below it, so a held value rests on whole digits.
 Returns the drawn width. `digitW` is the widest digit's advance.
 */
function odometer(canvas: SkCanvas, value: number, x: number, base: number, font: SkFont, digitW: number, lineH: number, color: number) {
  'worklet';
  const r = softRound(value);
  const pos = [r, 0, 0, 0];
  for (let k = 1; k < 4; k += 1) {
    const below = pos[k - 1];
    const m = ((below % 10) + 10) % 10;
    pos[k] = Math.floor(below / 10) + Math.max(0, m - 9);
  }
  // The thousands column only shows from 1000 up; the others slide over to close the gap.
  const lead = clamp(pos[3], 0, 1);
  const shift = -digitW * (1 - lead);
  const top = base - lineH * 0.78;
  canvas.save();
  canvas.clipRect(Skia.XYWHRect(x - 4, top - 4, digitW * 4 + 8, lineH + 8), ClipOp.Intersect, true);
  for (let k = 0; k < 4; k += 1) {
    const col = 3 - k;
    const cx = x + shift + col * digitW;
    const d = ((pos[k] % 10) + 10) % 10;
    const i = Math.floor(d);
    const f = d - i;
    const alphaCol = k === 3 ? lead : 1;
    for (let j = 0; j < 2; j += 1) {
      const digit = (i + j) % 10;
      if (k === 3 && digit === 0) continue;
      const off = (j - f) * lineH;
      const a = alphaCol * (1 - Math.min(1, Math.abs(j - f)));
      if (a <= 0.01) continue;
      const s = `${digit}`;
      const w = textWidth(font, s);
      canvas.drawText(s, cx + (digitW - w) / 2, base + off, fill(color, a), font);
    }
  }
  canvas.restore();
  return digitW * 4 + shift;
}

/** Two words rolling: index i shown, rolling up to i + 1 as the fraction grows. Right-aligned at x. */
function rollWords(canvas: SkCanvas, words: string[], index: number, x: number, base: number, font: SkFont, color: number, travel: number) {
  'worklet';
  const i = clamp(Math.floor(index), 0, words.length - 1);
  const f = index - i;
  for (let j = 0; j < 2; j += 1) {
    const w = words[i + j];
    if (w === undefined) continue;
    const a = 1 - Math.min(1, Math.abs(j - f));
    if (a <= 0.01) continue;
    const width = textWidth(font, w);
    canvas.drawText(w, x - width, base + (j - f) * travel, fill(color, a), font);
  }
}

function drawHeader(canvas: SkCanvas, g: Geo, f: Fonts, digitW: number, hpa: number) {
  'worklet';
  const base = g.readoutBase;
  const width = odometer(canvas, hpa, 24, base, f.readout, digitW, 60, 0xf4ecd8);
  canvas.drawText('hPa', 24 + width + 8, base, fill(0xe9e0cc, 0.6), f.unit);
  // The forecast word and the trend against the set hand, rolling as they change.
  const right = g.w - 24;
  canvas.save();
  canvas.clipRect(Skia.XYWHRect(right - 160, base - 52, 160, 64), ClipOp.Intersect, true);
  rollWords(canvas, WORDS, wordIndex(hpa), right, base - 22, f.word, 0xf4ecd8, 26);
  const trend = trendIndex(hpa);
  rollWords(canvas, TRENDS, trend, right, base + 1, f.small, trend > 1.5 ? 0xa9d5c9 : trend < 0.5 ? 0xe39a7f : 0xd7d0bf, 18);
  canvas.restore();
  // Trend arrow left of the trend word, turning with the change.
  const delta = clamp((hpa - NOW_HPA) / 12, -1, 1);
  const tw = textWidth(f.small, TRENDS[Math.round(trend)]);
  canvas.save();
  canvas.translate(right - tw - 12, base - 4);
  canvas.rotate(-delta * 45, 0, 0);
  const arrow = Skia.PathBuilder.Make().moveTo(-5, 0).lineTo(4, 0).moveTo(1, -3).lineTo(4.5, 0).lineTo(1, 3).build();
  canvas.drawPath(arrow, stroke(0xe9e0cc, 1.5, 0.75));
  canvas.restore();
}

export function drawNeedle(canvas: SkCanvas, g: Geo, angle: number) {
  'worklet';
  const { cx, cy, rf } = g;
  const len = rf * 0.84;
  const tail = rf * 0.22;
  const body = Skia.PathBuilder.Make()
    .moveTo(-2.2, tail)
    .lineTo(-0.7, -len * 0.86)
    .lineTo(-3.4, -len * 0.86)
    .lineTo(0, -len)
    .lineTo(3.4, -len * 0.86)
    .lineTo(0.7, -len * 0.86)
    .lineTo(2.2, tail)
    .close()
    .build();
  // Shadow on the face first (the needle stands a few points off it).
  canvas.save();
  canvas.translate(cx + 2.5, cy + 4.5);
  canvas.rotate(angle, 0, 0);
  canvas.drawPath(body, fill(0x2a1c0a, 0.28, 2.2));
  canvas.drawCircle(0, tail - 3, 7, stroke(0x2a1c0a, 2.4, 0.22, 2));
  canvas.restore();
  canvas.save();
  canvas.translate(cx, cy);
  canvas.rotate(angle, 0, 0);
  // Blued steel: dark at the edges, a cool sheen down the middle.
  canvas.drawPath(body, linear(-3.4, 0, 3.4, 0, [rgba(0x0c1626), rgba(0x3a5680), rgba(0x101b2e)], [0, 0.5, 1]));
  canvas.drawCircle(0, tail - 3, 7, stroke(0x15233a, 2.4));
  canvas.drawCircle(0, tail - 3, 7, stroke(0x5a7aa6, 0.6, 0.5));
  canvas.restore();
  // Brass arbor cap.
  canvas.drawCircle(cx, cy, 6.5, radial(cx - 2, cy - 2, 8, [rgba(0xfde9b6), rgba(0xbf913f), rgba(0x6a4818)], [0, 0.6, 1]));
}

function drawCursor(canvas: SkCanvas, g: Geo, f: Fonts, hour: number) {
  'worklet';
  const h = clamp(hour, -24, 24);
  const x = plotX(g, h);
  const hpa = pressureAt(h);
  const y = plotY(g, hpa);
  const { plot } = g;
  canvas.drawLine(x, plot.y - 4, x, plot.y + plot.h, stroke(0xf4ecd8, 1, 0.35));
  const color = h > 0 ? 0x9fc6c8 : AMBER;
  canvas.drawCircle(x, y, 10, fill(color, 0.22, 4));
  canvas.drawCircle(x, y, 5.5, fill(0x0f1c22));
  canvas.drawCircle(x, y, 5.5, stroke(color, 2.2));
  // Tooltip above the dot (below it near the top), kept inside the plot.
  const label = `${timeLabel(h)} · ${Math.round(hpa)} hPa`;
  const tw = textWidth(f.tip, label);
  const bw = tw + 20;
  const bx = clamp(x - bw / 2, plot.x - 4, plot.x + plot.w + 40 - bw);
  const above = y - 38 >= plot.y - 12;
  const by = above ? y - 38 : y + 14;
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(bx, by + 3, bw, 24), 12, 12), fill(0x000000, 0.35, 6));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(bx, by, bw, 24), 12, 12), fill(0xf2ead6));
  canvas.drawText(label, bx + 10, by + 16, fill(0x1d2a2f), f.tip);
}

/** Header readout and chart cursor, over everything else. */
export function drawOverlay(canvas: SkCanvas, g: Geo, f: Fonts, digitW: number, hour: number, hpaNeedle: number) {
  'worklet';
  drawHeader(canvas, g, f, digitW, hpaNeedle);
  drawCursor(canvas, g, f, hour);
}
