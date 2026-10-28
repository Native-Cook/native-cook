import {
  BlurStyle,
  ClipOp,
  PaintStyle,
  Skia,
  StrokeCap,
  StrokeJoin,
  TileMode,
  type SkCanvas,
  type SkColor,
  type SkFont,
  type SkPaint,
  type SkPath,
} from '@shopify/react-native-skia';

import {
  COVERED_AT,
  drain,
  type Frame,
  hash,
  redBottom,
  redFade,
  redTop,
  smoothRange,
  stickerScale,
  streamHead,
  streamSway,
  streamWidth,
} from './sauceSqueezeMotion';

/*
 Sauce Squeeze Cart — everything drawn in code (a port of SauceSqueezeCartArt.swift): the squeeze
 bottle (a pillowy body with three bulges per side, a two-tier cap, a label with a tomato), the
 sauce stream, the red flood and the stickers. The bottle is drawn in its own coordinates: origin at
 the body's centre, cap pointing down (+y). Every function is a worklet, declared above its callers.
*/

export const PINK = 0xf792dc;
export const SAUCE = 0xe90002;
export const PLUM = 0x3c063e;
export const MUTED_PLUM = 0xa0528e;

const HALF_W = 86;
const HALF_H = 114;
/** The nozzle's tip below the body's centre. */
export const NOZZLE_Y = HALF_H + 38;

export type Fonts = {
  brand: SkFont;
  tag: SkFont;
  added: SkFont;
  title: SkFont;
  eyebrow: SkFont;
  name: SkFont;
  subtitle: SkFont;
  chip: SkFont;
  price: SkFont;
  small: SkFont;
  cta: SkFont;
  plus: SkFont;
};

export function hex(v: number, a = 1): SkColor {
  'worklet';
  return Float32Array.of(((v >> 16) & 0xff) / 255, ((v >> 8) & 0xff) / 255, (v & 0xff) / 255, a);
}

export function fill(color: SkColor): SkPaint {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(color);
  return p;
}

export function stroke(color: SkColor, width: number): SkPaint {
  'worklet';
  const p = fill(color);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  p.setStrokeCap(StrokeCap.Round);
  p.setStrokeJoin(StrokeJoin.Round);
  return p;
}

/** SwiftUI's blur radius as a Skia sigma (Compose uses 1.5 × the radius as Android's blur radius). */
function sigma(r: number) {
  'worklet';
  return r * 1.5 * 0.57735 + 0.5;
}

/** A soft (blurred) fill or stroke of a path, offset by (dx, dy). */
export function blurred(c: SkCanvas, path: SkPath, color: SkColor, radius: number, width: number, dx: number, dy: number) {
  'worklet';
  const p = width > 0 ? stroke(color, width) : fill(color);
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma(radius), true));
  c.save();
  c.translate(dx, dy);
  c.drawPath(path, p);
  c.restore();
}

export function circle(x: number, y: number, r: number): SkPath {
  'worklet';
  const p = Skia.Path.Make();
  p.addCircle(x, y, r);
  return p;
}

export function roundRect(x: number, y: number, w: number, h: number, r: number): SkPath {
  'worklet';
  const p = Skia.Path.Make();
  p.addRRect(Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r));
  return p;
}

function oval(x: number, y: number, w: number, h: number): SkPath {
  'worklet';
  const p = Skia.Path.Make();
  p.addOval(Skia.XYWHRect(x, y, w, h));
  return p;
}

export function glyphWidth(font: SkFont, s: string, tracking: number) {
  'worklet';
  const widths = font.getGlyphWidths(font.getGlyphIDs(s));
  let w = 0;
  for (let i = 0; i < widths.length; i++) w += widths[i];
  return w + tracking * Math.max(0, widths.length - 1);
}

/** Draws a line of text vertically centred on y: centred on x, or starting at x (anchor −1). */
export function label(c: SkCanvas, s: string, font: SkFont, x: number, y: number, color: SkColor, anchor: number, tracking: number) {
  'worklet';
  const ids = font.getGlyphIDs(s);
  const widths = font.getGlyphWidths(ids);
  const positions = [];
  let cursor = 0;
  for (let i = 0; i < ids.length; i++) {
    positions.push({ x: cursor, y: 0 });
    cursor += widths[i] + tracking;
  }
  const w = cursor - tracking;
  const m = font.getMetrics();
  const x0 = anchor < 0 ? x : x - w / 2;
  c.drawGlyphs(ids, positions, x0, y - (m.ascent + m.descent) / 2, font, fill(color));
}

// Bottle

/** Width and height scale of a squash q (anchored at the cap), and the waist's pinch. */
function squashSX(q: number) {
  'worklet';
  return 1 + 0.18 * q;
}
function squashSY(q: number) {
  'worklet';
  return 1 - 0.14 * q;
}
function squashPinch(q: number) {
  'worklet';
  return 0.12 * Math.max(0, q);
}

/** The body's outline for a squash q, smoothed with quadratic curves through the midpoints. */
function bodyPath(q: number): SkPath {
  'worklet';
  const a = HALF_W;
  const b = HALF_H;
  const sx = squashSX(q);
  const sy = squashSY(q);
  const pinch = squashPinch(q);
  const n = 120;
  const xs: number[] = [];
  const ys: number[] = [];
  const e = 2 / 4.2;
  for (let i = 0; i < n; i++) {
    const th = (2 * Math.PI * i) / n;
    const c = Math.cos(th);
    const s = Math.sin(th);
    let x = a * (c < 0 ? -1 : 1) * Math.pow(Math.abs(c), e);
    let y = b * (s < 0 ? -1 : 1) * Math.pow(Math.abs(s), e);
    // Three bulges down each side.
    const v = (y + b) / (2 * b);
    const side = smoothRange(0.55, 0.92, Math.abs(x) / a);
    const env = smoothRange(0.06, 0.2, v) * (1 - smoothRange(0.8, 0.95, v));
    const sw = Math.sin(3 * Math.PI * v);
    x += (x < 0 ? -1 : 1) * (9 * sw * sw - 3.5) * side * env;
    // A pillowy top, a shoulder narrowing into the cap.
    if (y < 0) y -= 7 * Math.max(0, 1 - (x / a) * (x / a)) * smoothRange(0.6, 0.95, -y / b);
    if (y > 0) x *= 1 - 0.08 * smoothRange(0.7, 1, y / b);
    // Squeeze: wider and shorter, the waist pinched by the fingers, the top swelling.
    const waist = 1 - pinch * Math.exp(-Math.pow(y / (0.42 * b), 2));
    const swell = 1 + 0.4 * pinch * Math.exp(-Math.pow((y + 0.7 * b) / (0.3 * b), 2));
    xs.push(x * sx * waist * swell);
    ys.push(b + (y - b) * sy);
  }
  const path = Skia.Path.Make();
  path.moveTo((xs[n - 1] + xs[0]) / 2, (ys[n - 1] + ys[0]) / 2);
  for (let i = 0; i < n; i++) {
    const j = (i + 1) % n;
    path.quadTo(xs[i], ys[i], (xs[i] + xs[j]) / 2, (ys[i] + ys[j]) / 2);
  }
  path.close();
  return path;
}

function linear(x0: number, y0: number, x1: number, y1: number, colors: SkColor[], pos: number[] | null): SkPaint {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(Skia.Shader.MakeLinearGradient({ x: x0, y: y0 }, { x: x1, y: y1 }, colors, pos, TileMode.Clamp));
  return p;
}

function tomato(c: SkCanvas, x: number, y: number) {
  'worklet';
  c.save();
  c.translate(x, y);
  const lobes = Skia.Path.Make();
  lobes.addOval(Skia.XYWHRect(-25, -16, 30, 34));
  lobes.addOval(Skia.XYWHRect(-5, -16, 30, 34));
  lobes.addOval(Skia.XYWHRect(-16, -18, 32, 37));
  const body = Skia.Paint();
  body.setAntiAlias(true);
  body.setShader(
    Skia.Shader.MakeRadialGradient({ x: -6, y: -6 }, 30, [hex(0xf2473a), hex(0xe3201b), hex(0xb5100e)], null, TileMode.Clamp),
  );
  c.drawPath(lobes, body);
  const crease = stroke(hex(0xb5100e, 0.6), 1.4);
  const l = Skia.Path.Make();
  l.moveTo(-5, -12);
  l.quadTo(-10, 0, -6, 14);
  c.drawPath(l, crease);
  const r = Skia.Path.Make();
  r.moveTo(6, -12);
  r.quadTo(11, 0, 7, 14);
  c.drawPath(r, crease);
  c.drawPath(oval(-15, -11, 9, 6), fill(hex(0xffffff, 0.45)));
  // Calyx: a five-point green star with a stem.
  const star = Skia.Path.Make();
  for (let i = 0; i < 10; i++) {
    const a = -Math.PI / 2 + (i * Math.PI) / 5;
    const rr = i % 2 === 0 ? 12 : 4;
    const px = rr * Math.cos(a);
    const py = -16 + rr * Math.sin(a) * 0.55;
    if (i === 0) star.moveTo(px, py);
    else star.lineTo(px, py);
  }
  star.close();
  c.drawPath(star, fill(hex(0x2e8b3a)));
  c.drawPath(roundRect(-1.5, -24, 3, 8, 1.5), fill(hex(0x2e8b3a)));
  c.restore();
}

function bottleLabel(c: SkCanvas, fonts: Fonts) {
  'worklet';
  c.drawPath(roundRect(-52, -74, 104, 140, 18), fill(hex(0xe8553a)));
  c.drawPath(roundRect(-42, -64, 84, 120, 12), fill(hex(0xf6b4b0, 0.9)));
  tomato(c, 0, -14);
  label(c, 'PLUMPO', fonts.brand, 0, 36, hex(0xc22018), 0, 1.4);
}

/** The whole bottle, in its own coordinates (the caller translates and rotates the canvas). */
export function drawBottle(c: SkCanvas, f: Frame, fonts: Fonts) {
  'worklet';
  const b = HALF_H;
  const sx = squashSX(f.q);
  const sy = squashSY(f.q);
  const pinch = squashPinch(f.q);
  const body = bodyPath(f.q);

  // Soft shadow on the page.
  blurred(c, body, hex(0x9a1e6e, 0.22), 18, 0, 8, 22);

  // Cap: a white collar and a smaller tip, tucked under the body.
  const cw = 1 + 0.06 * f.q;
  const capShade = linear(-56, 0, 56, 0, [hex(0xffffff), hex(0xf3eef1), hex(0xcfc4cb)], [0, 0.5, 1]);
  c.drawPath(roundRect(-38 * cw, b + 16, 76 * cw, 22, 8), capShade);
  c.drawPath(roundRect(-38 * cw, b + 33, 76 * cw, 5, 2.5), fill(hex(0xbdb1b9)));
  c.drawPath(roundRect(-56 * cw, b - 10, 112 * cw, 30, 10), capShade);
  c.drawPath(roundRect(-56 * cw, b + 14, 112 * cw, 6, 3), fill(hex(0xd8ced5)));
  c.drawPath(oval(-6, b + 35.5, 12, 3), fill(hex(0x9e2030)));

  // Body: a red gradient across, darker towards the cap.
  c.drawPath(body, linear(-HALF_W, 0, HALF_W, 0, [hex(0xf3302e), hex(0xd80c12), hex(0xa30008)], [0, 0.45, 1]));
  c.save();
  c.clipPath(body, ClipOp.Intersect, true);
  c.drawPath(body, linear(0, 0, 0, b, [hex(0x7a0006, 0), hex(0x7a0006, 0.32)], null));
  // Inner shading: dark along the right and bottom, light along the top left.
  blurred(c, body, hex(0x6e0005, 0.55), 7, 12, -9, -7);
  blurred(c, body, hex(0xff8a7a, 0.35), 5, 6, 6, 7);

  // Label: scaled with the waist so it squeezes with the body.
  c.save();
  c.translate(0, b);
  c.scale(sx * (1 - pinch * 0.9), sy);
  c.translate(0, -b);
  bottleLabel(c, fonts);
  c.restore();

  // Specular streaks that slide a little as the bottle turns.
  const hx = -HALF_W * 0.66 + f.spin * 0.25;
  c.save();
  c.translate(0, b);
  c.scale(sx, sy);
  c.translate(0, -b);
  blurred(c, roundRect(hx - 6, -b * 0.62, 12, b * 1.04, 6), hex(0xffffff, 0.5), 3, 0, 0, 0);
  blurred(c, roundRect(hx + 13, -b * 0.5, 4, b * 0.55, 2), hex(0xffffff, 0.28), 3, 0, 0, 0);
  blurred(c, oval(-HALF_W * 0.2 - 36 + f.spin * 0.3, -b - 2, 72, 18), hex(0xffffff, 0.38), 6, 0, 0, 0);
  c.restore();
  c.restore();
}

/** The bead of sauce swelling at the nozzle while squeezed. */
export function drawBead(c: SkCanvas, s: number) {
  'worklet';
  const r = 7 * s;
  if (r <= 0.3) return;
  const cy = NOZZLE_Y + r * 0.7;
  c.drawPath(circle(0, cy, r), fill(hex(SAUCE)));
  c.drawPath(circle(-r * 0.35, cy - r * 0.3, r * 0.28), fill(hex(0xffffff, 0.5)));
}

// Stream, drops and the red

/** The ribbon of sauce from the nozzle (screen coordinates). */
export function drawStream(c: SkCanvas, rootX: number, rootY: number, tau: number, h: number) {
  'worklet';
  const head = streamHead(tau);
  const width = streamWidth(tau);
  if (head <= 0.001 || width <= 0.5) return;
  const sway = streamSway(tau);
  const len = (h + 60 - rootY) * head;
  const n = 48;
  const cx: number[] = [];
  const cy: number[] = [];
  for (let k = 0; k <= n; k++) {
    const u = k / n;
    const dy = len * u;
    cx.push(rootX + sway * Math.pow(u, 0.8) * Math.sin(2 * Math.PI * (dy / 300 - 1.6 * tau)));
    cy.push(rootY + dy);
  }
  const lx: number[] = [];
  const ly: number[] = [];
  const rx: number[] = [];
  const ry: number[] = [];
  for (let k = 0; k <= n; k++) {
    const a = Math.max(0, k - 1);
    const bb = Math.min(n, k + 1);
    let nx = -(cy[bb] - cy[a]);
    let ny = cx[bb] - cx[a];
    const l = Math.max(0.0001, Math.sqrt(nx * nx + ny * ny));
    nx /= l;
    ny /= l;
    const hw = (width / 2) * (0.55 + 0.45 * smoothRange(0, 0.25, k / n));
    lx.push(cx[k] + nx * hw);
    ly.push(cy[k] + ny * hw);
    rx.push(cx[k] - nx * hw);
    ry.push(cy[k] - ny * hw);
  }
  const p = Skia.Path.Make();
  p.moveTo(lx[0], ly[0]);
  for (let k = 1; k <= n; k++) p.lineTo(lx[k], ly[k]);
  // A round head.
  const ang = Math.atan2(cy[n] - cy[n - 1], cx[n] - cx[n - 1]);
  const r = width / 2;
  p.arcToOval(Skia.XYWHRect(cx[n] - r, cy[n] - r, 2 * r, 2 * r), ((ang + Math.PI / 2) * 180) / Math.PI, -180, false);
  for (let k = n; k >= 0; k--) p.lineTo(rx[k], ry[k]);
  p.close();
  c.drawPath(p, fill(hex(SAUCE)));

  // Gloss along the left of the ribbon.
  const gloss = Skia.Path.Make();
  const k0 = 3;
  const k1 = Math.floor(n * 0.9);
  for (let k = k0; k <= k1; k++) {
    const gx = lx[k] * 0.62 + cx[k] * 0.38;
    const gy = ly[k] * 0.62 + cy[k] * 0.38;
    if (k === k0) gloss.moveTo(gx, gy);
    else gloss.lineTo(gx, gy);
  }
  blurred(c, gloss, hex(0xff6a58, 0.6), 1.5, Math.max(2, width * 0.1), 0, 0);
}

/** Droplets thrown from the nozzle as the stream starts. */
export function drawDrops(c: SkCanvas, rootX: number, rootY: number, tau: number) {
  'worklet';
  for (let i = 0; i < 6; i++) {
    const t = tau - 0.02 * i;
    if (t <= 0 || t >= 0.9) continue;
    const vx = (hash(i + 1) - 0.5) * 520;
    const vy = -160 + hash(i + 7) * 300;
    const x = rootX + vx * t;
    const y = rootY + vy * t + 0.5 * 1600 * t * t;
    const r = (4 + 5 * hash(i + 13)) * (1 - smoothRange(0.6, 0.9, t));
    c.drawPath(circle(x, y, r), fill(hex(SAUCE)));
    c.drawPath(circle(x - r * 0.3, y - r * 0.3, r * 0.3), fill(hex(0xffffff, 0.45)));
  }
}

/** The red that floods up from the bottom and later retracts upwards, dripping. */
export function drawRed(c: SkCanvas, tau: number, w: number, h: number) {
  'worklet';
  const flooding = tau < COVERED_AT + 0.1;
  const draining = drain(tau) > 0;
  if (tau <= 0.3 || drain(tau) >= 1) return;
  const count = Math.floor((w + 40) / 3) + 1;
  const xs: number[] = [];
  const top: number[] = [];
  const bottom: number[] = [];
  for (let i = 0; i < count; i++) {
    const x = -20 + 3 * i;
    xs.push(x);
    top.push(flooding ? redTop(x, tau, h) : -400);
    bottom.push(draining ? redBottom(x, tau, w, h) : h + 400);
  }
  const p = Skia.Path.Make();
  p.moveTo(xs[0], top[0]);
  for (let i = 1; i < count; i++) p.lineTo(xs[i], top[i]);
  for (let i = count - 1; i >= 0; i--) p.lineTo(xs[i], bottom[i]);
  p.close();
  c.drawPath(p, fill(hex(SAUCE)));

  // A glossy line just inside the moving edge.
  if (!flooding && !draining) return;
  const edge = flooding ? top : bottom;
  const dy = flooding ? 7 : -7;
  const line = Skia.Path.Make();
  for (let i = 0; i < count; i++) {
    if (i === 0) line.moveTo(xs[i], edge[i] + dy);
    else line.lineTo(xs[i], edge[i] + dy);
  }
  c.save();
  c.clipPath(p, ClipOp.Intersect, true);
  blurred(c, line, hex(0xff5444, 0.55), 2, 3, 0, 0);
  c.restore();
}

// Stickers

/** A line-drawn shopping cart centred at (x, y). */
export function drawCart(c: SkCanvas, x: number, y: number, k: number, ink: SkColor) {
  'worklet';
  const p = Skia.Path.Make();
  p.moveTo(x - 13 * k, y - 9 * k);
  p.lineTo(x - 9 * k, y - 9 * k);
  p.lineTo(x - 5 * k, y + 5 * k);
  p.lineTo(x + 9 * k, y + 5 * k);
  p.lineTo(x + 12 * k, y - 5 * k);
  p.lineTo(x - 7.6 * k, y - 5 * k);
  c.drawPath(p, stroke(ink, 2.2 * k));
  c.drawPath(circle(x - 3 * k, y + 10 * k, 2 * k), fill(ink));
  c.drawPath(circle(x + 7 * k, y + 10 * k, 2 * k), fill(ink));
}

function starPath(): SkPath {
  'worklet';
  const star = Skia.Path.Make();
  for (let j = 0; j < 16; j++) {
    const a = -Math.PI / 2 + (j * Math.PI) / 8;
    const r = j % 2 === 0 ? 22 : 8.5;
    if (j === 0) star.moveTo(r * Math.cos(a), r * Math.sin(a));
    else star.lineTo(r * Math.cos(a), r * Math.sin(a));
  }
  star.close();
  return star;
}

/** The outer shape of a sticker, for its shadow. */
function silhouette(i: number, fonts: Fonts): SkPath {
  'worklet';
  if (i === 0) return starPath();
  if (i === 1) return circle(0, 0, 38);
  if (i === 2) {
    const w = glyphWidth(fonts.tag, 'IN YOUR CART', 0) + 32;
    return roundRect(-w / 2 - 4, -21 - 4, w + 8, 50, 14);
  }
  if (i === 3) return circle(0, 0, 44);
  if (i === 4) {
    const w = glyphWidth(fonts.added, '+1 ADDED', 0) + 40;
    return roundRect(-w / 2 - 4, -20 - 4, w + 8, 48, 24);
  }
  return roundRect(-32, -21, 62, 42, 14);
}

function sticker(c: SkCanvas, i: number, fonts: Fonts) {
  'worklet';
  const plum = hex(PLUM);
  const white = hex(0xffffff);
  if (i === 0) {
    c.drawPath(starPath(), fill(white));
  } else if (i === 1) {
    c.drawPath(circle(0, 0, 38), fill(white));
    c.drawPath(circle(0, 0, 34), fill(hex(0xf8d3ef)));
    drawCart(c, 0, 0, 1.15, plum);
  } else if (i === 2) {
    const w = glyphWidth(fonts.tag, 'IN YOUR CART', 0) + 32;
    const h = 42;
    c.drawPath(roundRect(-w / 2 - 4, -h / 2 - 4, w + 8, h + 8, 14), fill(white));
    c.drawPath(roundRect(-w / 2, -h / 2, w, h, 10), linear(-w / 2, 0, w / 2, 0, [hex(0x9ec2fa), hex(0xd3e1ff)], null));
    label(c, 'IN YOUR CART', fonts.tag, 0, 0, plum, 0, 0);
  } else if (i === 3) {
    c.drawPath(circle(0, 0, 44), fill(white));
    const face = Skia.Paint();
    face.setAntiAlias(true);
    face.setShader(
      Skia.Shader.MakeRadialGradient({ x: -10, y: -12 }, 52, [hex(0xfbf79a), hex(0xf0e54a), hex(0xd4a92a)], [0, 0.5, 1], TileMode.Clamp),
    );
    c.drawPath(circle(0, 0, 40), face);
    c.drawPath(roundRect(-15.5, -14, 5, 10, 2.5), fill(plum));
    c.drawPath(roundRect(9, -15, 5, 10, 2.5), fill(plum));
    const smile = Skia.Path.Make();
    smile.addArc(Skia.XYWHRect(-22, -26, 44, 44), 25, 130);
    c.drawPath(smile, stroke(plum, 3.5));
  } else if (i === 4) {
    const w = glyphWidth(fonts.added, '+1 ADDED', 0) + 40;
    const h = 40;
    c.drawPath(roundRect(-w / 2 - 4, -h / 2 - 4, w + 8, h + 8, 24), fill(white));
    c.drawPath(roundRect(-w / 2, -h / 2, w, h, 20), fill(hex(0x22c32c)));
    c.drawPath(circle(-w / 2 + 12, 0, 3.5), fill(plum));
    label(c, '+1 ADDED', fonts.added, 5, 0, plum, 0, 0);
  } else {
    const arrow = Skia.Path.Make();
    arrow.moveTo(-24, 0);
    arrow.lineTo(20, 0);
    arrow.moveTo(6, -13);
    arrow.lineTo(21, 0);
    arrow.lineTo(6, 13);
    c.drawPath(arrow, stroke(white, 17));
    c.drawPath(arrow, stroke(plum, 7));
  }
}

/** Positions relative to the middle of the red (x, y, rotation), drawn at 1.15 ×. */
const STICKER_X = [-124, 112, -12, -74, 104, 14];
const STICKER_Y = [-118, -92, -34, 64, 52, 150];
const STICKER_ROT = [0, 12, -10, 16, -68, -45];

export function drawStickers(c: SkCanvas, tau: number, cx: number, cy: number, reduced: boolean, fonts: Fonts) {
  'worklet';
  for (let i = 0; i < 6; i++) {
    let k = stickerScale(i, tau);
    let alpha = 1;
    if (reduced) {
      alpha = redFade(tau);
      k = alpha > 0.001 ? 1 : 0;
    }
    if (k <= 0.01) continue;
    const float = reduced ? 0 : 3 * Math.sin(2 * Math.PI * (tau / 1.6) + i * 1.7);
    const rot = STICKER_ROT[i] + (reduced ? 0 : 30 * (1 - Math.min(1, k)));
    c.save();
    c.translate(cx + STICKER_X[i], cy + STICKER_Y[i] + float);
    c.rotate(rot, 0, 0);
    c.scale(1.15 * k, 1.15 * k);
    if (alpha < 0.999) {
      const layer = Skia.Paint();
      layer.setAlphaf(alpha);
      c.saveLayer(layer);
    } else {
      c.save();
    }
    blurred(c, silhouette(i, fonts), hex(0x5a0010, 0.25), 10, 0, 0, 6);
    sticker(c, i, fonts);
    c.restore();
    c.restore();
  }
}
