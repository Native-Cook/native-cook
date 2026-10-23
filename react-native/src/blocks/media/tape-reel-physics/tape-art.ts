import { BlurStyle, ClipOp, PaintStyle, type SkCanvas, type SkFont, type SkPath, Skia, StrokeCap, StrokeJoin, TileMode } from '@shopify/react-native-skia';

import { type Frame, ringing, smoothstep, span, spin, supply, supplyAngle, takeUp, takeUpAngle } from './tape-model';

/** Drawing: the cassette (in a 362 × 229 design space), the transport keys and the VU meters. */

export const W = 362;
export const H = 229;
const SUPPLY = [107, 138];
const TAKE_UP = [255, 138];
const LEFT_GUIDE = [81, 206];
const RIGHT_GUIDE = [281, 206];
const GUIDE_R = 5;
/** The span between the guides runs along y = 211 from x = 81 to 281. */
export const SPAN_Y = 211;
export const SPAN_X0 = 81;
export const SPAN_X1 = 281;
const WINDOW = { x: 54, y: 86, width: 254, height: 105 };
export const AMBER = 0xf2a93b;

export type Fonts = {
  label: SkFont;
  bold12: SkFont;
  semi10: SkFont;
  mono8: SkFont;
  bold13: SkFont;
  semi11: SkFont;
  semi24: SkFont;
  mono12: SkFont;
  mono17: SkFont;
  regular12: SkFont;
};

export function rgb(hex: number, a = 1) {
  'worklet';
  return Float32Array.of(((hex >> 16) & 0xff) / 255, ((hex >> 8) & 0xff) / 255, (hex & 0xff) / 255, Math.min(Math.max(a, 0), 1));
}

export function fill(c: Float32Array) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(c);
  return p;
}

export function stroke(c: Float32Array, w: number, round = false) {
  'worklet';
  const p = fill(c);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(w);
  if (round) {
    p.setStrokeCap(StrokeCap.Round);
    p.setStrokeJoin(StrokeJoin.Round);
  }
  return p;
}

export function rrect(x: number, y: number, w: number, h: number, r: number) {
  'worklet';
  return Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r);
}

/** A string's advance. SkFont.getTextWidth rounds every glyph's advance to whole points, which puts centred and right-aligned text off by several points; the glyph widths are exact. */
export function textWidth(font: SkFont, s: string) {
  'worklet';
  const ws = font.getGlyphWidths(font.getGlyphIDs(s));
  let w = 0;
  for (let i = 0; i < ws.length; i++) w += ws[i];
  return w;
}

/** Draws text with its box anchored like SwiftUI's: hx 0/0.5/1 (left, centre, right), vy 0.5 (centre) or 1 (bottom); tracking adds space after each letter. */
export function text(c: SkCanvas, s: string, x: number, y: number, font: SkFont, color: Float32Array, hx = 0.5, vy = 0.5, tracking = 0) {
  'worklet';
  const m = font.getMetrics();
  const widths = tracking > 0 ? s.split('').map((ch) => textWidth(font, ch)) : [];
  const w = tracking > 0 ? widths.reduce((a, b) => a + b, 0) + tracking * (s.length - 1) : textWidth(font, s);
  const baseline = vy >= 1 ? y - m.descent : y - (m.ascent + m.descent) / 2;
  const paint = fill(color);
  let left = x - w * hx;
  if (tracking > 0) {
    for (let i = 0; i < s.length; i++) {
      c.drawText(s[i], left, baseline, paint, font);
      left += widths[i] + tracking;
    }
  } else {
    c.drawText(s, left, baseline, paint, font);
  }
}

/** The point where tape leaves a pack of radius r for a point p, on the side away from the cassette's middle. */
function tangent(cx: number, cy: number, r: number, px: number, py: number, left: boolean): number[] {
  'worklet';
  const dx = px - cx;
  const dy = py - cy;
  const d = Math.max(Math.hypot(dx, dy), r + 0.01);
  const phi = Math.atan2(dy, dx);
  const a = Math.acos(r / d);
  const t1 = [cx + r * Math.cos(phi + a), cy + r * Math.sin(phi + a)];
  const t2 = [cx + r * Math.cos(phi - a), cy + r * Math.sin(phi - a)];
  return left ? (t1[0] < t2[0] ? t1 : t2) : t1[0] > t2[0] ? t1 : t2;
}

/** Adds a slack run of tape from a to b, bowed `sag` points to one side (−1 left, +1 right). */
function slackRun(p: SkPath, ax: number, ay: number, bx: number, by: number, sag: number, side: number, move: boolean) {
  'worklet';
  if (move) p.moveTo(ax, ay);
  else p.lineTo(ax, ay);
  const dx = bx - ax;
  const dy = by - ay;
  const len = Math.max(Math.hypot(dx, dy), 0.01);
  let nx = -dy / len;
  let ny = dx / len;
  if (nx * side < 0) {
    nx = -nx;
    ny = -ny;
  }
  for (let i = 1; i <= 16; i++) {
    const u = i / 16;
    const o = sag * Math.sin(Math.PI * u);
    p.lineTo(ax + dx * u + nx * o, ay + dy * u + ny * o);
  }
}

function pack(c: SkCanvas, cx: number, cy: number, r: number) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  // SwiftUI's radial gradient runs from radius 16 to r.
  p.setShader(Skia.Shader.MakeRadialGradient({ x: cx, y: cy }, r, [rgb(0x3d2a18), rgb(0x3d2a18), rgb(0x23160c)], [0, 16 / r, 1], TileMode.Clamp));
  c.drawCircle(cx, cy, r, p);
  for (const k of [4, 9, 15, 22]) if (r - k > 17) c.drawCircle(cx, cy, r - k, stroke(Float32Array.of(0, 0, 0, 0.22), 0.6));
  const sheen = Skia.Path.Make();
  sheen.addArc(Skia.XYWHRect(cx - r + 2.5, cy - r + 2.5, 2 * r - 5, 2 * r - 5), 200, 55);
  c.drawPath(sheen, stroke(rgb(0xa88560, 0.3), 2, true));
}

/** A hub: a white ring with six teeth and three dark notches. Spinning fast, the teeth smear into rings. */
function hub(c: SkCanvas, cx: number, cy: number, angle: number, omega: number) {
  'worklet';
  const b = smoothstep(10, 40, Math.abs(omega));
  c.drawCircle(cx, cy, 16, fill(Float32Array.of(1, 1, 1, 0.84)));
  c.drawCircle(cx, cy, 9, fill(rgb(0x100d0a)));
  c.save();
  c.translate(cx, cy);
  c.rotate((angle * 180) / Math.PI, 0, 0);
  for (let k = 0; k < 6; k++) {
    c.save();
    c.rotate(k * 60, 0, 0);
    c.drawRRect(rrect(5.8, -1.3, 3.6, 2.6, 0.8), fill(Float32Array.of(1, 1, 1, 0.84 * (1 - 0.8 * b))));
    c.restore();
  }
  for (let k = 0; k < 3; k++) {
    c.save();
    c.rotate(k * 120 + 30, 0, 0);
    c.drawRRect(rrect(10.5, -1.6, 4.5, 3.2, 1), fill(rgb(0x2a241c, 1 - 0.8 * b)));
    c.restore();
  }
  c.restore();
  if (b > 0.01) {
    c.drawCircle(cx, cy, 7.6, stroke(Float32Array.of(1, 1, 1, 0.45 * b), 2.6));
    c.drawCircle(cx, cy, 12.75, stroke(rgb(0x2a241c, 0.35 * b), 3.2));
  }
}

export function cassette(c: SkCanvas, f: Frame, t: number, still: boolean, fonts: Fonts) {
  'worklet';
  const shellPaint = Skia.Paint();
  shellPaint.setAntiAlias(true);
  shellPaint.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: 0, y: H }, [rgb(0xf0e9d8), rgb(0xd9cfb7)], null, TileMode.Clamp));
  c.drawRRect(rrect(0, 0, W, H, 14), shellPaint);
  c.drawRRect(rrect(0.5, 0.5, W - 1, H - 1, 14), stroke(rgb(0xbdb197), 1));
  c.drawRRect(rrect(5, 5, W - 10, H - 10, 10), stroke(Float32Array.of(1, 1, 1, 0.35), 1));
  // Screws.
  for (const [x, y] of [
    [14, 14],
    [W - 14, 14],
    [14, H - 14],
    [W - 14, H - 14],
    [W / 2, 182],
  ]) {
    c.drawCircle(x, y, 5, fill(rgb(0xbfb49b)));
    c.drawLine(x - 3.5 * Math.cos(0.5), y - 3.5 * Math.sin(0.5), x + 3.5 * Math.cos(0.5), y + 3.5 * Math.sin(0.5), stroke(rgb(0x8d836c), 1.2));
  }

  // Label.
  const label = rrect(22, 12, 318, 66, 6);
  c.drawRRect(label, fill(rgb(0xfbf8f0)));
  c.save();
  c.clipRRect(label, ClipOp.Intersect, true);
  c.drawRect(Skia.XYWHRect(22, 12, 318, 9), fill(rgb(0xe4572e)));
  c.drawRect(Skia.XYWHRect(22, 21, 318, 2.5), fill(rgb(0xf2a93b)));
  c.restore();
  for (const y of [55, 68]) c.drawLine(56, y, 330, y, stroke(rgb(0x6c8fd8, 0.55), 0.8));
  c.drawCircle(38, 46, 10, stroke(rgb(0x2b2b2b), 1.2));
  text(c, 'A', 38, 46, fonts.bold12, rgb(0x2b2b2b));
  text(c, 'Night Drive Mix', 58, 53, fonts.label, rgb(0x1f3a8a), 0, 1);
  text(c, 'C60', 328, 34, fonts.semi10, rgb(0x6b6355), 1);

  // Window, packs and hubs.
  const win = rrect(WINDOW.x, WINDOW.y, WINDOW.width, WINDOW.height, 10);
  c.drawRRect(win, fill(rgb(0x100d0a)));
  const rs = supply(f.s);
  const rt = takeUp(f.s);
  let extra = 0;
  const p = f.pluck;
  if (p.length === 3) extra = p[2] < 0 ? p[1] : still ? 0 : ringing(p[0], p[0], p[1], t - p[2]);
  const aS = supplyAngle(f.s) + 0.03 * Math.abs(extra);
  const aT = takeUpAngle(f.s) - 0.03 * Math.abs(extra);
  c.save();
  c.clipRRect(win, ClipOp.Intersect, true);
  pack(c, SUPPLY[0], SUPPLY[1], rs);
  pack(c, TAKE_UP[0], TAKE_UP[1], rt);
  hub(c, SUPPLY[0], SUPPLY[1], aS, spin(f.v, rs));
  hub(c, TAKE_UP[0], TAKE_UP[1], aT, spin(f.v, rt));
  // Glare across the glass.
  const glare = Skia.Path.Make();
  glare.moveTo(120, 86);
  glare.lineTo(170, 86);
  glare.lineTo(120, 191);
  glare.lineTo(70, 191);
  glare.close();
  c.drawPath(glare, fill(Float32Array.of(1, 1, 1, 0.06)));
  const thin = Skia.Path.Make();
  thin.moveTo(182, 86);
  thin.lineTo(194, 86);
  thin.lineTo(144, 191);
  thin.lineTo(132, 191);
  thin.close();
  c.drawPath(thin, fill(Float32Array.of(1, 1, 1, 0.05)));
  c.restore();
  c.drawRRect(win, stroke(rgb(0xb5aa90), 1.5));

  // The head opening at the bottom, with its felt pad.
  const mouth = Skia.Path.Make();
  mouth.moveTo(112, H);
  mouth.lineTo(124, 198);
  mouth.lineTo(238, 198);
  mouth.lineTo(250, H);
  mouth.close();
  c.drawPath(mouth, fill(rgb(0x2a241c)));
  c.drawRect(Skia.XYWHRect(172, 213, 18, 7), fill(rgb(0xc9b48a)));
  for (const x of [140, 222]) c.drawCircle(x, 220, 4, fill(rgb(0x0f0c09)));

  // The tape: supply pack → left guide → the span → right guide → take-up pack.
  const lox = LEFT_GUIDE[0] - GUIDE_R;
  const rox = RIGHT_GUIDE[0] + GUIDE_R;
  const gy = LEFT_GUIDE[1];
  const tape = Skia.Path.Make();
  const ts = tangent(SUPPLY[0], SUPPLY[1], rs, lox, gy, true);
  slackRun(tape, ts[0], ts[1], lox, gy, f.sag, -1, true);
  tape.arcToOval(Skia.XYWHRect(LEFT_GUIDE[0] - GUIDE_R, gy - GUIDE_R, 2 * GUIDE_R, 2 * GUIDE_R), 180, -90, false);
  let x = SPAN_X0;
  while (x < SPAN_X1) {
    x = Math.min(x + 2, SPAN_X1);
    const u = (x - SPAN_X0) / (SPAN_X1 - SPAN_X0);
    tape.lineTo(x, SPAN_Y + span(u, f, t, still));
  }
  tape.arcToOval(Skia.XYWHRect(RIGHT_GUIDE[0] - GUIDE_R, gy - GUIDE_R, 2 * GUIDE_R, 2 * GUIDE_R), 90, -90, false);
  const tt = tangent(TAKE_UP[0], TAKE_UP[1], rt, rox, gy, false);
  slackRun(tape, rox, gy, tt[0], tt[1], f.sag, 1, false);
  c.drawPath(tape, stroke(rgb(0x4a3320), 2.4, true));
  c.save();
  c.translate(0, -0.7);
  c.drawPath(tape, stroke(rgb(0xa88560, 0.55), 0.7, true));
  c.restore();
  // Steel guides.
  for (const g of [LEFT_GUIDE, RIGHT_GUIDE]) {
    const gp = Skia.Paint();
    gp.setAntiAlias(true);
    gp.setShader(
      Skia.Shader.MakeLinearGradient({ x: g[0] - GUIDE_R, y: g[1] - GUIDE_R }, { x: g[0] + GUIDE_R, y: g[1] + GUIDE_R }, [rgb(0xe4e7eb), rgb(0x868d96)], null, TileMode.Clamp),
    );
    c.drawCircle(g[0], g[1], GUIDE_R, gp);
    c.drawCircle(g[0], g[1], GUIDE_R - 3, fill(rgb(0x5a6068)));
  }
}

// Keys: rewind, play, fast forward, pause, stop, record.

export function key(c: SkCanvas, glyph: number, x: number, y: number, w: number, h: number, pressed: boolean) {
  'worklet';
  const sink = pressed ? 2 : 0;
  // The well the key sits in.
  c.drawRRect(rrect(x, y + 4, w, h, 10), fill(rgb(0x0b0a08)));
  const ky = y + sink;
  const body = Skia.Paint();
  body.setAntiAlias(true);
  body.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: 0, y: ky },
      { x: 0, y: ky + h },
      pressed ? [rgb(0x232325), rgb(0x1c1c1e)] : [rgb(0x333336), rgb(0x232325)],
      null,
      TileMode.Clamp,
    ),
  );
  c.drawRRect(rrect(x, ky, w, h, 10), body);
  c.drawLine(x + 10, ky + 0.75, x + w - 10, ky + 0.75, stroke(Float32Array.of(1, 1, 1, 0.1), 1));
  if (pressed) c.drawRRect(rrect(x + 0.6, ky + 0.6, w - 1.2, h - 1.2, 10), stroke(glyph === 5 ? rgb(0xe5484d, 0.9) : rgb(AMBER, 0.9), 1.2));
  const color = glyph === 5 ? rgb(0xe5484d) : pressed ? rgb(AMBER) : rgb(0xf1e8d6);
  const cx = x + w / 2;
  const cy = ky + h / 2;
  const g = Skia.Path.Make();
  const tri = (tx: number, dir: number) => {
    g.moveTo(tx - 5 * dir, cy - 6);
    g.lineTo(tx + 5 * dir, cy);
    g.lineTo(tx - 5 * dir, cy + 6);
    g.close();
  };
  if (glyph === 0) {
    tri(cx - 4, -1);
    tri(cx + 5, -1);
  } else if (glyph === 1) {
    tri(cx + 1, 1);
  } else if (glyph === 2) {
    tri(cx - 5, 1);
    tri(cx + 4, 1);
  } else if (glyph === 3) {
    g.addRRect(rrect(cx - 5.5, cy - 6.5, 4, 13, 1));
    g.addRRect(rrect(cx + 1.5, cy - 6.5, 4, 13, 1));
  } else if (glyph === 4) {
    g.addRRect(rrect(cx - 5.5, cy - 5.5, 11, 11, 1.5));
  } else {
    g.addCircle(cx, cy, 6);
  }
  c.drawPath(g, fill(color));
}

// Meters

const MARKS: [number, string][] = [
  [0, '−20'],
  [0.35, '−10'],
  [0.6, '−5'],
  [0.75, '0'],
  [1, '+3'],
];

export function meter(c: SkCanvas, x: number, y: number, w: number, h: number, lvl: number, glow: number, channel: string, fonts: Fonts) {
  'worklet';
  c.drawRRect(rrect(x, y, w, h, 14), fill(rgb(0x221e18)));
  const fx = x + 8;
  const fy = y + 8;
  const fw = w - 16;
  const fh = h - 16;
  const face = rrect(fx, fy, fw, fh, 8);
  const fp = Skia.Paint();
  fp.setAntiAlias(true);
  fp.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: fy }, { x: 0, y: fy + fh }, [rgb(0xf3e9cf), rgb(0xdccba4)], null, TileMode.Clamp));
  c.drawRRect(face, fp);
  const gp = Skia.Paint();
  gp.setAntiAlias(true);
  gp.setShader(Skia.Shader.MakeRadialGradient({ x: fx + fw / 2, y: fy + fh }, fw * 0.8, [rgb(0xffc65c, 0.35 * glow), rgb(0xffc65c, 0)], null, TileMode.Clamp));
  c.drawRRect(face, gp);
  const px = fx + fw / 2;
  const py = fy + fh - 10;
  const radius = fw * 0.46;
  const at = (u: number, rr: number) => {
    const a = ((-140 + 100 * u) * Math.PI) / 180;
    return [px + rr * Math.cos(a), py + rr * Math.sin(a)];
  };
  // Scale: a thin arc, a red zone from 0 VU, ticks and numbers.
  const arc = Skia.Path.Make();
  arc.addArc(Skia.XYWHRect(px - radius, py - radius, 2 * radius, 2 * radius), -140, 75);
  c.drawPath(arc, stroke(rgb(0x3a3225), 1));
  const red = radius + 1.5;
  const redArc = Skia.Path.Make();
  redArc.addArc(Skia.XYWHRect(px - red, py - red, 2 * red, 2 * red), -65, 25);
  c.drawPath(redArc, stroke(rgb(0xc8402f), 4));
  for (let i = 0; i <= 20; i++) {
    const u = i / 20;
    const major = i % 4 === 0;
    const a = at(u, radius);
    const b = at(u, radius + (major ? 8 : 4.5));
    c.drawLine(a[0], a[1], b[0], b[1], stroke(u > 0.75 ? rgb(0xc8402f) : rgb(0x3a3225), major ? 1.2 : 0.8));
  }
  for (const [u, s] of MARKS) {
    const p = at(u, radius + 16);
    text(c, s, p[0], p[1], fonts.mono8, u > 0.74 ? rgb(0xc8402f) : rgb(0x4a4030));
  }
  text(c, 'VU', fx + fw / 2, py - radius * 0.42, fonts.bold13, rgb(0x3a3225));
  text(c, channel, fx + 12, fy + fh - 12, fonts.semi10, rgb(0x6b5f48));
  // Needle and its cap.
  const tip = at(lvl, radius + 6);
  c.drawLine(px, py, tip[0], tip[1], stroke(rgb(0x1a1612), 1.3, true));
  c.drawRect(Skia.XYWHRect(fx, fy + fh - 8, fw, 8), fill(rgb(0xcbbb94)));
  c.drawCircle(px, py, 5, fill(rgb(0x1a1612)));
}

/** A four-digit odometer: the last digit rolls with the tape, fading as it leaves the window. */
export function counter(c: SkCanvas, x: number, y: number, w: number, h: number, value: number, fonts: Fonts) {
  'worklet';
  c.drawRRect(rrect(x, y, w, h, 8), fill(rgb(0x0e0c09)));
  c.drawRRect(rrect(x + 0.5, y + 0.5, w - 1, h - 1, 8), stroke(rgb(0x3a3225), 1));
  const n = Math.floor(value);
  const frac = value - n;
  const digits = String(n % 10000).padStart(4, '0');
  const cell = (w - 12) / 4;
  const cy = y + h / 2;
  for (let i = 0; i < 4; i++) {
    const dx = x + 6 + cell * (i + 0.5);
    if (i === 3) {
      const roll = 22 * smoothstep(0.7, 1, frac);
      text(c, digits[i], dx, cy - roll, fonts.mono17, rgb(0xf1e8d6, 1 - roll / 22));
      text(c, String((n + 1) % 10), dx, cy + 22 - roll, fonts.mono17, rgb(0xf1e8d6, roll / 22));
    } else {
      text(c, digits[i], dx, cy, fonts.mono17, rgb(0xf1e8d6));
    }
    if (i > 0) c.drawLine(x + 6 + cell * i, y + 6, x + 6 + cell * i, y + h - 6, stroke(Float32Array.of(1, 1, 1, 0.08), 1));
  }
  c.drawRect(Skia.XYWHRect(x + 1, y + 1, w - 2, h / 2 - 1), fill(Float32Array.of(1, 1, 1, 0.04)));
}

/** The cassette's soft shadow. */
export function shadow(c: SkCanvas, x: number, y: number, w: number, h: number) {
  'worklet';
  const p = fill(Float32Array.of(0, 0, 0, 0.6));
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 14, true));
  c.drawOval(Skia.XYWHRect(x, y, w, h), p);
}
