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
  Skia,
  StrokeCap,
  TileMode,
} from '@shopify/react-native-skia';

import { CONTACT, type Geo, PINS, PIN_COUNT, type Pose, SONG_SECONDS, TEETH, pinAngle, toothX } from './musicBoxMotion';

/*
 Music Box Player — the drawing, one Skia canvas. Baked once: a violet night room with a warm lamp
 glow, the walnut box with its lid open (velvet panel, gold inlay, an oval mirror), the velvet well,
 the brass bed plate and bearings, the brass cylinder, the comb's base, the front face with an
 escutcheon and the crank's hub, and the player panel's plate. Per frame: the cylinder's seams and
 pins (on its visible half, shaded by their angle), the 18 teeth (tips bent by the pins, ringing),
 a warm glow at each plucked tip, floating notes, the governor's fan, the crank, and the playhead.
*/

export type Layout = Geo & {
  s: number;
  boxX: number;
  boxW: number;
  lidTop: number;
  rimY: number;
  frontY: number;
  frontH: number;
  x0: number;
  x1: number;
  panelY: number;
  panelH: number;
};

export function makeLayout(w: number, h: number, top: number, bottom: number): Layout {
  const panelH = 176;
  const panelY = h - Math.max(bottom, 12) - panelH;
  const lidTop = top + 110;
  const s = Math.max(0.72, Math.min(1.1, (panelY - 18 - lidTop) / 430));
  const boxX = 14;
  const boxW = w - 28;
  const rimY = lidTop + 150 * s;
  const frontY = rimY + 196 * s;
  const x0 = boxX + 14;
  const x1 = boxX + boxW - 14;
  const cylR = 25 * s;
  const cylY = rimY + 24 * s + cylR;
  const tipY = cylY + cylR * Math.sin(CONTACT);
  const frontH = 64 * s;
  return {
    w,
    h,
    s,
    boxX,
    boxW,
    lidTop,
    rimY,
    frontY,
    frontH,
    x0,
    x1,
    panelY,
    panelH,
    cylX0: x0 + 40,
    cylX1: x1 - 22,
    cylY,
    cylR,
    tipY,
    combY: tipY + 52 * s,
    hubX: boxX + boxW - 50,
    hubY: frontY + frontH / 2,
    arm: 30 * s,
    fanX: x0 + 20,
    fanY: cylY,
    barX: 36,
    barY: panelY + 58,
    barW: w - 72,
  };
}

function paint(color: string, alpha = 1) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  if (alpha < 1) p.setAlphaf(alpha);
  return p;
}

function stroke(color: string, width: number, alpha = 1) {
  'worklet';
  const p = paint(color, alpha);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  p.setStrokeCap(StrokeCap.Round);
  return p;
}

function colors(list: string[]) {
  'worklet';
  const out = [];
  for (let i = 0; i < list.length; i += 1) out.push(Skia.Color(list[i]));
  return out;
}

function linear(x0: number, y0: number, x1: number, y1: number, list: string[], pos: number[] | null = null) {
  'worklet';
  return Skia.Shader.MakeLinearGradient(Skia.Point(x0, y0), Skia.Point(x1, y1), colors(list), pos, TileMode.Clamp);
}

function radial(x: number, y: number, r: number, list: string[], pos: number[] | null = null) {
  'worklet';
  return Skia.Shader.MakeRadialGradient(Skia.Point(x, y), Math.max(0.1, r), colors(list), pos, TileMode.Clamp);
}

function blurred(color: string, alpha: number, sigma: number) {
  'worklet';
  const p = paint(color, alpha);
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma, true));
  return p;
}

function fill(shader: ReturnType<typeof linear>) {
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(shader);
  return p;
}

function grain(alpha: number, seed: number) {
  const p = Skia.Paint();
  p.setShader(Skia.Shader.MakeFractalNoise(0.9, 0.9, 2, seed, 0, 0));
  p.setColorFilter(Skia.ColorFilter.MakeMatrix([0.33, 0.33, 0.33, 0, 0, 0.33, 0.33, 0.33, 0, 0, 0.33, 0.33, 0.33, 0, 0, 0, 0, 0, 0, alpha]));
  p.setBlendMode(BlendMode.Overlay);
  return p;
}

function rr(x: number, y: number, w: number, h: number, r: number) {
  return Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r);
}

function walnut(c: SkCanvas, x: number, y: number, w: number, h: number, seed: number) {
  c.drawRect(Skia.XYWHRect(x, y, w, h), fill(linear(x, y, x, y + h, ['#6A3E24', '#55301B', '#40230F'])));
  c.save();
  c.clipRect(Skia.XYWHRect(x, y, w, h), ClipOp.Intersect, true);
  for (let i = 0; i < Math.round(h / 4); i += 1) {
    const b = Skia.PathBuilder.Make();
    const yy = y + i * 4 + 2;
    b.moveTo(x, yy);
    for (let k = 1; k <= 10; k += 1) b.lineTo(x + (w * k) / 10, yy + Math.sin(k * 0.9 + i * 1.7 + seed) * 1.6);
    c.drawPath(b.build(), stroke(i % 3 === 0 ? '#2A140A' : '#7C4A2C', 0.7, 0.35));
  }
  c.restore();
}

function screw(c: SkCanvas, x: number, y: number, r: number) {
  c.drawCircle(x, y, r, fill(radial(x - r * 0.4, y - r * 0.4, r * 1.6, ['#FFF0BE', '#C89A45', '#6E4F1C'])));
  c.drawLine(x - r * 0.6, y + r * 0.3, x + r * 0.6, y - r * 0.3, stroke('#4A3410', 0.8));
}

function drawScene(c: SkCanvas, g: Layout, f: SkFont) {
  const { w, h, s } = g;
  c.drawRect(Skia.XYWHRect(0, 0, w, h), fill(linear(0, 0, 0, h, ['#231D3A', '#1B1730', '#141126', '#0E0C1C'], [0, 0.4, 0.75, 1])));
  c.drawRect(Skia.XYWHRect(0, 0, w, h), fill(radial(w * 0.5, g.rimY, w * 0.95, ['rgba(255,190,120,0.26)', 'rgba(180,120,120,0.1)', 'rgba(40,30,70,0)'], [0, 0.5, 1])));
  // Soft bokeh from the night-light, out of focus behind the box.
  for (let i = 0; i < 7; i += 1) {
    const x = w * (0.08 + 0.84 * Math.abs(Math.sin(i * 7.31 + 0.4)));
    const y = g.lidTop * (0.35 + 0.6 * Math.abs(Math.sin(i * 3.17 + 1)));
    c.drawCircle(x, y, 10 + 18 * Math.abs(Math.sin(i * 1.3)), blurred(i % 2 === 0 ? '#F3C987' : '#B9A6F0', 0.1, 8));
  }
  const { boxX: bx, boxW: bw } = g;
  // Shadow under the box.
  c.drawOval(Skia.XYWHRect(bx - 4, g.frontY + g.frontH - 10, bw + 8, 34), blurred('#05040A', 0.7, 14));
  // The lid, standing open behind the box: a trapezoid (tilted back), walnut frame, velvet inside.
  const inset = bw * 0.035;
  const lid = Skia.PathBuilder.Make()
    .moveTo(bx + inset, g.lidTop)
    .lineTo(bx + bw - inset, g.lidTop)
    .lineTo(bx + bw, g.rimY)
    .lineTo(bx, g.rimY)
    .close()
    .build();
  c.save();
  c.clipPath(lid, ClipOp.Intersect, true);
  walnut(c, bx, g.lidTop, bw, g.rimY - g.lidTop, 3);
  c.restore();
  const pad = 12 * s;
  const vel = Skia.PathBuilder.Make()
    .moveTo(bx + inset + pad, g.lidTop + pad)
    .lineTo(bx + bw - inset - pad, g.lidTop + pad)
    .lineTo(bx + bw - pad, g.rimY - pad * 0.5)
    .lineTo(bx + pad, g.rimY - pad * 0.5)
    .close()
    .build();
  c.drawPath(vel, fill(radial(bx + bw * 0.4, g.lidTop + 20, bw * 0.8, ['#4C3A78', '#352866', '#241A48'], [0, 0.5, 1])));
  c.save();
  c.clipPath(vel, ClipOp.Intersect, true);
  c.drawRect(Skia.XYWHRect(bx, g.lidTop, bw, g.rimY - g.lidTop), grain(0.12, 5));
  c.restore();
  c.drawPath(vel, stroke('#D9B56A', 1.4, 0.8));
  // The oval mirror, catching the lamp.
  const mx = g.w / 2;
  const my = (g.lidTop + g.rimY) / 2;
  const mrx = bw * 0.2;
  const mry = (g.rimY - g.lidTop) * 0.3;
  c.drawOval(Skia.XYWHRect(mx - mrx - 4, my - mry - 4, mrx * 2 + 8, mry * 2 + 8), fill(linear(0, my - mry, 0, my + mry, ['#F5DA94', '#B98B3C', '#7A5520'])));
  c.drawOval(Skia.XYWHRect(mx - mrx, my - mry, mrx * 2, mry * 2), fill(linear(mx - mrx, my - mry, mx + mrx, my + mry, ['#9AA4C8', '#5A5F8A', '#2C2E55', '#6E6A98'], [0, 0.35, 0.7, 1])));
  c.save();
  c.clipPath(Skia.PathBuilder.Make().addOval(Skia.XYWHRect(mx - mrx, my - mry, mrx * 2, mry * 2)).build(), ClipOp.Intersect, true);
  c.drawRect(Skia.XYWHRect(mx - mrx * 0.6, my - mry, mrx * 0.35, mry * 2), fill(linear(mx - mrx * 0.6, 0, mx - mrx * 0.25, 0, ['rgba(255,255,255,0)', 'rgba(255,255,255,0.3)', 'rgba(255,255,255,0)'])));
  c.drawCircle(mx + mrx * 0.3, my + mry * 0.2, mry * 0.45, blurred('#FFC98A', 0.35, 10));
  c.restore();
  // Brass hinges.
  for (const hx of [bx + bw * 0.22, bx + bw * 0.78]) c.drawRect(Skia.XYWHRect(hx - 16, g.rimY - 5, 32, 8), fill(linear(0, g.rimY - 5, 0, g.rimY + 3, ['#F4D78C', '#A7782E', '#6B4A18'])));
  // Box rim and the velvet well.
  walnut(c, bx, g.rimY, bw, g.frontY - g.rimY, 9);
  c.drawLine(bx, g.rimY + 0.5, bx + bw, g.rimY + 0.5, stroke('#E8B888', 1, 0.5));
  const well = rr(g.x0, g.rimY + 10 * s, g.x1 - g.x0, g.frontY - g.rimY - 18 * s, 6);
  c.drawRRect(well, fill(linear(0, g.rimY, 0, g.frontY, ['#2A1E4C', '#3A2A66', '#2E2254'])));
  c.save();
  c.clipRRect(well, ClipOp.Intersect, true);
  c.drawRect(Skia.XYWHRect(g.x0, g.rimY, g.x1 - g.x0, g.frontY - g.rimY), grain(0.14, 8));
  c.drawRRect(rr(g.x0 - 10, g.rimY + 2 * s, g.x1 - g.x0 + 20, 18 * s, 6), blurred('#0C0818', 0.7, 8));
  c.restore();
  // Brass bed plate with screws, and the bearings at the cylinder's ends.
  const py0 = g.cylY - g.cylR - 10 * s;
  const py1 = g.combY + 26 * s;
  c.drawRRect(rr(g.cylX0 - 12, py0 + 3, g.cylX1 - g.cylX0 + 24, py1 - py0, 5), blurred('#0A0614', 0.6, 5));
  c.drawRRect(rr(g.cylX0 - 12, py0, g.cylX1 - g.cylX0 + 24, py1 - py0, 5), fill(linear(0, py0, 0, py1, ['#D8B56C', '#B58C44', '#8C6628'])));
  c.drawRRect(rr(g.cylX0 - 12, py0, g.cylX1 - g.cylX0 + 24, py1 - py0, 5), stroke('#FFE9B0', 0.8, 0.5));
  screw(c, g.cylX0 - 4, py1 - 8 * s, 3 * s);
  screw(c, g.cylX1 + 4, py1 - 8 * s, 3 * s);
  for (const ex of [g.cylX0 - 6, g.cylX1 + 6]) {
    c.drawRect(Skia.XYWHRect(ex - 5, g.cylY - g.cylR - 6, 10, g.cylR * 2 + 12), fill(linear(ex - 5, 0, ex + 5, 0, ['#8C6628', '#F0D08A', '#7A5520'])));
    c.drawCircle(ex, g.cylY, 3, paint('#4A3410'));
  }
  // The cylinder: brass, lit from above.
  c.drawRect(
    Skia.XYWHRect(g.cylX0, g.cylY - g.cylR, g.cylX1 - g.cylX0, g.cylR * 2),
    fill(linear(0, g.cylY - g.cylR, 0, g.cylY + g.cylR, ['#5E4316', '#C99D4C', '#F6DE9A', '#D9AE5C', '#8A6224', '#3F2B0C'], [0, 0.18, 0.34, 0.5, 0.78, 1])),
  );
  c.drawRect(Skia.XYWHRect(g.cylX0, g.cylY - g.cylR, g.cylX1 - g.cylX0, g.cylR * 2), grain(0.12, 4));
  // The comb's base: a steel block with screws; the lead weights under the bass teeth.
  const cb = g.combY;
  c.drawRRect(rr(g.cylX0 - 2, cb, g.cylX1 - g.cylX0 + 4, 16 * s, 3), fill(linear(0, cb, 0, cb + 16 * s, ['#E9EEF2', '#9AA4AE', '#5C656F'])));
  for (let k = 0; k < 5; k += 1) screw(c, g.cylX0 + 14 + ((g.cylX1 - g.cylX0 - 28) * k) / 4, cb + 8 * s, 2.6 * s);
  // Front face: walnut with an inlay band, a keyhole escutcheon and the crank's hub plate.
  walnut(c, bx, g.frontY, bw, g.frontH, 13);
  c.drawRect(Skia.XYWHRect(bx, g.frontY, bw, g.frontH), fill(linear(0, g.frontY, 0, g.frontY + g.frontH, ['rgba(255,220,180,0.1)', 'rgba(0,0,0,0)', 'rgba(10,4,0,0.35)'])));
  c.drawLine(bx, g.frontY + 0.5, bx + bw, g.frontY + 0.5, stroke('#F0C090', 1, 0.45));
  c.drawLine(bx + 16, g.frontY + g.frontH * 0.5, g.hubX - 44, g.frontY + g.frontH * 0.5, stroke('#D9B56A', 1.2, 0.6));
  const ex = g.w * 0.42;
  const ey = g.frontY + g.frontH * 0.5;
  c.drawOval(Skia.XYWHRect(ex - 13, ey - 17, 26, 34), fill(linear(0, ey - 17, 0, ey + 17, ['#F6DA90', '#B98B3C', '#6E4D1A'])));
  c.drawCircle(ex, ey - 4, 3.2, paint('#1A0E05'));
  c.drawRect(Skia.XYWHRect(ex - 1.4, ey - 4, 2.8, 10), paint('#1A0E05'));
  c.drawCircle(g.hubX, g.hubY, 16 * s, blurred('#0A0408', 0.5, 4));
  c.drawCircle(g.hubX, g.hubY, 14 * s, fill(radial(g.hubX - 4, g.hubY - 5, 18 * s, ['#FBE6A8', '#C2913E', '#6E4D1A'])));
  // Player panel plate.
  const pp = rr(16, g.panelY, w - 32, g.panelH, 24);
  c.drawRRect(rr(16, g.panelY + 10, w - 32, g.panelH, 24), blurred('#05040A', 0.55, 16));
  c.drawRRect(pp, fill(linear(0, g.panelY, 0, g.panelY + g.panelH, ['#2C2645', '#231E3A', '#1C1830'])));
  c.drawRRect(rr(16.5, g.panelY + 0.5, w - 33, g.panelH - 1, 23.5), stroke('#E9E3FF', 1, 0.1));
  c.drawRRect(rr(g.barX, g.barY - 2, g.barW, 4, 2), paint('#E9E3FF', 0.14));
  const total = `0:${SONG_SECONDS}`;
  const ws = f.getGlyphWidths(f.getGlyphIDs(total));
  let tw = 0;
  for (let i = 0; i < ws.length; i += 1) tw += ws[i];
  c.drawText(total, g.barX + g.barW - tw, g.barY + 22, paint('#E9E3FF', 0.5), f);
  const vig = Skia.Paint();
  vig.setShader(radial(w / 2, h * 0.45, Math.hypot(w, h) * 0.62, ['rgba(8,6,16,0)', 'rgba(8,6,16,0.25)', 'rgba(6,4,12,0.6)'], [0, 0.6, 1]));
  c.drawRect(Skia.XYWHRect(0, 0, w, h), vig);
}

export function bakeScene(g: Layout, f: SkFont, scale: number): SkImage | null {
  const surface = Skia.Surface.Make(Math.ceil(g.w * scale), Math.ceil(g.h * scale));
  if (!surface) return null;
  const c = surface.getCanvas();
  c.scale(scale, scale);
  drawScene(c, g, f);
  surface.flush();
  return surface.makeImageSnapshot();
}

/** An eighth note, about 16 pt tall, its head's centre at the origin. */
export function makeNote(): SkPath {
  return Skia.PathBuilder.Make()
    .addOval(Skia.XYWHRect(-4.6, -3.2, 9.2, 6.4))
    .moveTo(3.6, -1)
    .lineTo(3.6, -16)
    .lineTo(5.2, -16)
    .cubicTo(6.5, -12, 10.5, -11.5, 9.5, -6.5)
    .cubicTo(9, -9.5, 7, -10.5, 5.2, -11)
    .lineTo(5.2, -1)
    .close()
    .build();
}

function wrap(a: number) {
  'worklet';
  const k = Math.PI * 2;
  let x = ((a % k) + k) % k;
  if (x > Math.PI) x -= k;
  return x;
}

export function drawFrame(c: SkCanvas, g: Layout, scene: SkImage, note: SkPath, time: SkFont, p: Pose) {
  'worklet';
  c.drawImageRectOptions(scene, Skia.XYWHRect(0, 0, scene.width(), scene.height()), Skia.XYWHRect(0, 0, g.w, g.h), FilterMode.Linear, MipmapMode.None, Skia.Paint());
  const s = g.s;
  // Seams on the cylinder so its turning reads between pins.
  for (let k = 0; k < 8; k += 1) {
    const phi = wrap((k * Math.PI) / 4 + p.angle);
    const cv = Math.cos(phi);
    if (cv <= 0.05) continue;
    const y = g.cylY + g.cylR * Math.sin(phi);
    c.drawLine(g.cylX0, y, g.cylX1, y, stroke('#3F2B0C', 0.7, 0.28 * cv));
  }
  // Pins.
  const pin = paint('#FFF3CF');
  const pinShade = paint('#3A2608');
  for (let i = 0; i < PIN_COUNT; i += 1) {
    const phi = wrap(pinAngle(i) + p.angle);
    const cv = Math.cos(phi);
    if (cv <= 0.04) continue;
    const x = toothX(g, PINS[i * 2 + 1]);
    const y = g.cylY + g.cylR * Math.sin(phi);
    const r = 1.9 * s;
    pinShade.setAlphaf(0.5 * cv);
    c.drawOval(Skia.XYWHRect(x - r, y - r * cv + 1.2, r * 2, r * 2 * Math.max(0.3, cv)), pinShade);
    pin.setAlphaf(0.35 + 0.65 * cv);
    c.drawOval(Skia.XYWHRect(x - r, y - r * cv, r * 2, r * 2 * Math.max(0.3, cv)), pin);
  }
  // Teeth: steel strips from the base up to the cylinder, tips bent by the pins.
  const pitch = (g.cylX1 - g.cylX0 - 24) / TEETH;
  const tw = pitch * 0.64;
  const toothPaint = Skia.Paint();
  toothPaint.setAntiAlias(true);
  toothPaint.setShader(linear(0, g.tipY, 0, g.combY, ['#F7FAFC', '#B7C0C9', '#7D8791'], [0, 0.4, 1]));
  const edges = Skia.PathBuilder.Make();
  const shades = Skia.PathBuilder.Make();
  for (let j = 0; j < TEETH; j += 1) {
    const x = toothX(g, j);
    const ty = g.tipY + 1 + p.tips[j];
    const w0 = tw * (1.12 - (0.24 * j) / TEETH);
    const b = Skia.PathBuilder.Make()
      .moveTo(x - w0 / 2, g.combY + 1)
      .lineTo(x - w0 / 2 + p.tips[j] * 0.05, ty + 1.5)
      .quadTo(x, ty - 1, x + w0 / 2 + p.tips[j] * 0.05, ty + 1.5)
      .lineTo(x + w0 / 2, g.combY + 1)
      .close()
      .build();
    c.drawPath(b, toothPaint);
    edges.moveTo(x - w0 / 2 + 0.7, g.combY).lineTo(x - w0 / 2 + 0.7, ty + 2);
    shades.moveTo(x + w0 / 2 - 0.6, g.combY).lineTo(x + w0 / 2 - 0.6, ty + 2);
  }
  c.drawPath(edges.build(), stroke('#FFFFFF', 0.8, 0.7));
  c.drawPath(shades.build(), stroke('#3E464E', 0.8, 0.5));
  // Warm glow where a tooth was plucked, and a ring on the cylinder.
  for (let j = 0; j < TEETH; j += 1) {
    const k = p.glow[j];
    if (k < 0.02) continue;
    const x = toothX(g, j);
    const gl = Skia.Paint();
    gl.setShader(radial(x, g.tipY, 22 * s, ['rgba(255,214,140,0.9)', 'rgba(255,180,100,0.3)', 'rgba(255,160,80,0)'], [0, 0.4, 1]));
    gl.setAlphaf(k);
    gl.setBlendMode(BlendMode.Plus);
    c.drawCircle(x, g.tipY, 22 * s, gl);
  }
  // Notes floating up.
  for (let i = 0; i < p.notes.length; i += 5) {
    const a = p.notes[i + 3];
    if (a < 0.01) continue;
    c.save();
    c.translate(p.notes[i], p.notes[i + 1]);
    c.rotate(p.notes[i + 4], 0, 0);
    c.scale(p.notes[i + 2] * s, p.notes[i + 2] * s);
    const halo = Skia.Paint();
    halo.setShader(radial(2, -6, 16, ['rgba(255,210,140,0.45)', 'rgba(255,210,140,0)']));
    halo.setAlphaf(a);
    c.drawCircle(2, -6, 16, halo);
    c.drawPath(note, paint('#FFF1D6', a));
    c.restore();
  }
  // The governor's fan: two blades turning about a vertical axle.
  const fw = 9 * s;
  const fh = 16 * s;
  c.drawLine(g.fanX, g.fanY - fh - 4, g.fanX, g.fanY + fh + 4, stroke('#C9A050', 1.6));
  for (let b = 0; b < 2; b += 1) {
    const ang = p.fan + b * Math.PI;
    const cw = Math.cos(ang);
    const half = Math.abs(fw * Math.sin(ang));
    const dir = cw >= 0 ? 1 : -1;
    const blade = Skia.Paint();
    blade.setShader(linear(g.fanX, 0, g.fanX + dir * half * 2, 0, ['#FFF0C0', '#B8893A']));
    blade.setAntiAlias(true);
    if (half > 0.3) c.drawRect(Skia.XYWHRect(dir > 0 ? g.fanX : g.fanX - half * 2, g.fanY - fh / 2, half * 2, fh), blade);
  }
  // The crank: arm and ivory knob turning about the hub.
  const ca = p.crank;
  const kx = g.hubX + Math.cos(ca) * g.arm;
  const ky = g.hubY + Math.sin(ca) * g.arm;
  c.drawLine(g.hubX + 2, g.hubY + 3, kx + 2, ky + 3, stroke('#0A0408', 7 * s, 0.35));
  c.drawLine(g.hubX, g.hubY, kx, ky, stroke('#D6AA55', 6 * s));
  c.drawLine(g.hubX, g.hubY, kx, ky, stroke('#FFF0C4', 1.6 * s, 0.6));
  c.drawCircle(kx + 2, ky + 3, 10 * s, blurred('#0A0408', 0.45, 3));
  const kp = Skia.Paint();
  kp.setAntiAlias(true);
  kp.setShader(radial(kx - 3, ky - 4, 13 * s, ['#FFFBF0', '#EADDC4', '#B9A27E']));
  c.drawCircle(kx, ky, 9.5 * s, kp);
  c.drawCircle(g.hubX, g.hubY, 5 * s, paint('#6E4D1A'));
  c.drawCircle(g.hubX - 1, g.hubY - 1, 2 * s, paint('#FFF0C4', 0.8));
  // Playhead.
  const px = g.barX + g.barW * Math.min(1, Math.max(0, p.progress));
  const bar = Skia.Paint();
  bar.setShader(linear(g.barX, 0, g.barX + g.barW, 0, ['#F3C987', '#E9A6C8']));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(g.barX, g.barY - 2, Math.max(4, px - g.barX), 4), 2, 2), bar);
  c.drawCircle(px, g.barY, 7, blurred('#000000', 0.35, 2));
  c.drawCircle(px, g.barY, 6, paint('#FFF6EA'));
  const sec = Math.floor(Math.min(1, Math.max(0, p.progress)) * SONG_SECONDS + 1e-4);
  const label = `0:${sec < 10 ? '0' : ''}${sec}`;
  c.drawText(label, g.barX, g.barY + 22, paint('#E9E3FF', 0.75), time);
}
