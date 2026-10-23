import { ClipOp, Skia, TileMode, type SkCanvas, type SkColor, type SkPaint, type SkPath } from '@shopify/react-native-skia';

import { createContours } from './mascot-contours';

import { ease, key, mix, segment, spring } from './mascot-motion';

const BLUE = 0x3C71FB, GREEN = 0x47D87B, YELLOW = 0xF7B30A, PINK = 0xF014CC, RED = 0xE64441, INK = 0x0A1230;
const FACES = [[-0.0851,-0.0043,0.0793,0.1087,0.1449],[-0.1688,0.0234,0.1044,0.1368,0.1517],[-0.1978,0.0515,0.0972,0.13,0.1442],[0.113,0.028,0.1164,0.141,0.1084],[0.0859,-0.0144,0.1103,0.1675,0.18]];
type Kind = 0 | 1 | 2 | 3 | 4;
export type Art = { flower: SkPath; smallFlower: SkPath; redDrop: SkPath; redBloom: SkPath; bean: SkPath; drop: SkPath; triangle: SkPath; heart: SkPath; clouds: SkPath[]; playLarge: SkPath; playSmall: SkPath };
function color(hex: number, alpha = 1): SkColor {
  'worklet'; return Float32Array.of(((hex >> 16) & 255) / 255, ((hex >> 8) & 255) / 255, (hex & 255) / 255, alpha);
}
function paint(hex: number, alpha = 1): SkPaint {
  'worklet'; const p = Skia.Paint(); p.setAntiAlias(true); p.setColor(color(hex, alpha)); return p;
}
function rounded(c: SkCanvas, x: number, y: number, w: number, h: number, radius: number, hex: number, alpha = 1) {
  'worklet'; c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y, w, h), radius, radius), paint(hex, alpha));
}
function oval(c: SkCanvas, x: number, y: number, w: number, h: number, hex: number, alpha = 1) {
  'worklet'; c.drawOval(Skia.XYWHRect(x, y, w, h), paint(hex, alpha));
}
/** Static contours: identical normalized coordinates to the Swift reference. */
export function makeArt(): Art {
  const { flower, smallFlower, redDrop, drop, roundedTriangle: triangle, bloom: redBloom, clouds } = createContours();
  const bean = Skia.PathBuilder.Make().moveTo(-0.48, 0.1)
    .cubicTo(-0.6, -0.2, -0.36, -0.5, -0.1, -0.49).cubicTo(0.27, -0.54, 0.58, -0.27, 0.49, -0.02)
    .cubicTo(0.5, 0.35, 0.36, 0.57, 0.1, 0.48).cubicTo(-0.07, 0.22, -0.42, 0.6, -0.48, 0.1).close().detach();
  const heart = Skia.PathBuilder.Make().moveTo(0, 0.45)
    .cubicTo(-0.12, 0.34, -0.61, 0.06, -0.42, -0.15).cubicTo(-0.38, -0.5, -0.08, -0.45, 0, -0.22)
    .cubicTo(0.12, -0.5, 0.45, -0.45, 0.42, -0.15).cubicTo(0.62, 0.11, 0.2, 0.37, 0, 0.45).close().detach();
  const playLarge = Skia.PathBuilder.Make().moveTo(0.008226, 0.798017).cubicTo(-0.043648, 1.008039, 0.160612, 1.025120, 0.316465, 0.978922).lineTo(0.881571, 0.811412).cubicTo(1.011260, 0.772969, 1.036216, 0.617018, 0.947625, 0.530013).lineTo(0.463247, 0.054304).cubicTo(0.375095, -0.032270, 0.209671, -0.017579, 0.177024, 0.114602).lineTo(0.008226, 0.798017).close().detach();
  const playSmall = Skia.PathBuilder.Make().moveTo(0.113345, 0.000089).cubicTo(-0.003439, -0.004944, -0.014833, 0.197077, 0.012199, 0.252632).lineTo(0.338096, 0.922400).cubicTo(0.413287, 1.076929, 0.578986, 0.967737, 0.630270, 0.878484).lineTo(0.989879, 0.252632).cubicTo(1.025614, 0.190439, 0.961646, 0.036653, 0.877499, 0.033026).lineTo(0.113345, 0.000089).close().detach();
  return { flower, smallFlower, redDrop, redBloom, bean, drop, triangle, heart, clouds, playLarge, playSmall };
}

function transformed(c: SkCanvas, path: SkPath, x: number, y: number, w: number, h: number, hex: number, angle = 0) {
  'worklet'; c.save(); c.translate(x, y); c.rotate(angle, 0, 0); c.scale(w, h); c.drawPath(path, paint(hex)); c.restore();
}
function mascot(c: SkCanvas, art: Art, kind: Kind, x: number, y: number, w: number, h: number, angle = 0, blink = 0, highlight = 0, look = 0, bodyTurn = 0) {
  'worklet'; if (w <= 0.01 || h <= 0.01) return;
  const path = kind === 0 ? art.flower : kind === 1 ? art.triangle : kind === 2 ? art.smallFlower : kind === 4 ? art.redDrop : art.drop;
  c.save(); c.translate(x, y); c.rotate(angle, 0, 0); c.scale(w, h);
  c.save(); c.rotate(bodyTurn, 0, 0); c.drawPath(path, paint([BLUE, GREEN, YELLOW, PINK, RED][kind])); c.restore();
  if (highlight > 0) {
    c.save(); c.clipPath(path, ClipOp.Intersect, true); oval(c, 0.02, -0.3, 0.5, 0.7, 0xffffff, highlight); c.restore();
  }
  const face = FACES[kind], small = w < 80;
  const ew = kind === 3 && small ? 0.10 : kind === 4 && small ? 0.11 : face[3];
  const height = kind === 3 && small ? 0.09 : kind === 4 && small ? 0.14 : face[4], eh = mix(height, 0.025, blink);
  for (let side = -1; side <= 1; side += 2) rounded(c, face[0] + look + side * face[2] - ew / 2, face[1] - eh / 2, ew, eh, ew / 2, 0xffffff);
  c.restore();
}

function hand(c: SkCanvas, art: Art, x: number, y: number, hex: number, w = 20, h = 24, angle = 0) {
  'worklet'; transformed(c, art.bean, x, y, w, h, hex, angle);
}
function shadow(c: SkCanvas, x: number, y: number, w: number, h: number, opacity = 0.65) {
  'worklet'; oval(c, x - w / 2, y - h / 2, w, h, 0xECECFF, opacity);
}
function phone(c: SkCanvas, x: number, y: number, w: number, h: number, angle: number, upright = 1, flash = 0) {
  'worklet'; c.save(); c.translate(x, y); c.rotate(angle, 0, 0);
  rounded(c, -w / 2 - 1.7, -h / 2 + 1.6, w, h, 4, 0xB7BEDC); rounded(c, -w / 2, -h / 2, w, h, 4, INK);
  if (upright < 0.99) {
    c.saveLayer(paint(0xffffff, 1 - upright));
    rounded(c, -w / 2 + 2.2, -h / 2 + 2, w - 4.4, h - 4, 3, 0xDDF0FE);
    oval(c, -w * 0.15, -h * 0.15, w * 0.34, h * 0.32, 0xffffff); c.restore();
  }
  if (upright > 0) {
    c.saveLayer(paint(0xffffff, upright));
    rounded(c, w / 2 - 12, -h / 2 + 3, 9, 9, 2, 0x2A4380); oval(c, w / 2 - 9.8, -h / 2 + 5.2, 4.6, 4.6, 0xffffff); c.restore();
  }
  if (flash > 0) oval(c, w / 2 - 10.8, -h / 2 + 4.2, 6.6, 6.6, 0xffffff, flash);
  c.restore();
}
function note(c: SkCanvas, x: number, y: number, scale: number, angle: number, hex: number) {
  'worklet'; c.save(); c.translate(x, y); c.rotate(angle, 0, 0); c.scale(scale, scale);
  oval(c, -5, 5, 7, 5, hex); c.drawRect(Skia.XYWHRect(0, -8, 2, 15), paint(hex));
  const flag = Skia.PathBuilder.Make().moveTo(1, -8).quadTo(6, -8, 7, -2).lineTo(6, 1).quadTo(4, -4, 1, -4).close().detach();
  c.drawPath(flag, paint(hex)); c.restore();
}
function hangout(c: SkCanvas, art: Art, t: number) {
  'worklet';
  const x = key(t, [[0, 137.8], [0.25, 152.6], [0.38, 154.7], [0.65, 175.5], [1.1, 186.7], [1.38, 102.5], [1.65, 102.5], [1.9, 130.8]]);
  const y = key(t, [[0, 258.3], [0.25, 237.7], [0.38, 242.1], [0.65, 318.8], [1.1, 334.3], [1.38, 268], [1.65, 268], [1.9, 287.2]]);
  const angle = key(t, [[0, -12.6], [0.65, -1.7], [1.2, -5.8], [1.65, 12.3], [1.9, -7.6]]);
  const sinkRed = ease(segment(t, 0.04, 0.45)), sinkYellow = ease(segment(t, 0.01, 0.37));
  if (sinkRed < 1) {
    const s = 1 - sinkRed; mascot(c, art, 4, mix(96.9, 235, sinkRed), mix(415.1, 447, sinkRed), 89.7 * s, 89.8 * s, -35 - 35 * sinkRed);
    hand(c, art, 155, 381, RED, 24 * s, 23 * s, -25);
  }
  if (sinkYellow < 1) {
    const s = 1 - sinkYellow; mascot(c, art, 2, mix(267.5, 235, sinkYellow), mix(325.5, 445, sinkYellow), 75.6 * s, 79.5 * s, 22.4 + 60 * sinkYellow);
  }
  const reappear = ease(segment(t, 1.53, 1.91));
  if (reappear > 0) mascot(c, art, 2, mix(235, 282, reappear), mix(446, 279, reappear), 65.7 * reappear, 74.6 * reappear, 7.5);
  const held = ease(segment(t, 0.65, 0.98)) * (1 - ease(segment(t, 1.25, 1.55)));
  shadow(c, 235, 459, 73 * (1 - held * 0.2), 20 * (1 - held * 0.2), 0.6 * (1 - held * 0.6));
  phone(c, mix(235, 252, held), mix(450, 375, held), mix(49, 30, held), mix(22, 48, held), mix(-5, 6, held), held);
  mascot(c, art, 0, x, y, 115.4, 109.2, angle);
  hand(c, art, x - 57, y + 60, BLUE, 25, 30, -20); hand(c, art, mix(x + 78, 240, held), mix(y + 12, 381, held), BLUE, 28, 28, 15);
  for (let i = 0; i < 5; i++) {
    const p = segment(t, 1.43 + i * 0.065, 2.06 + i * 0.02);
    if (p > 0 && p < 1) {
      const side = i % 2 === 0 ? -1 : 1;
      c.saveLayer(paint(0xffffff, Math.sin(Math.PI * p))); note(c, 235 + side * (10 + 80 * p), 441 - 140 * p, 0.6 + 0.5 * p, side * 25, [GREEN, PINK, BLUE, RED, GREEN][i]); c.restore();
    }
  }
}
function connection(c: SkCanvas, art: Art, t: number) {
  'worklet';
  const x = key(t, [[2.0, 196.5], [2.125, 234.2], [2.25, 242.9], [2.375, 236.6], [2.5, 206.6], [2.625, 149.6], [2.75, 140], [2.875, 147.1], [3, 173.6], [3.125, 194], [3.25, 196.5], [4.4, 196.5]]);
  const y = key(t, [[2.0, 316], [2.125, 283], [2.25, 269.6], [2.375, 277.8], [2.5, 295.8], [2.625, 280.6], [2.75, 272.4], [2.875, 317.4], [3, 352.9], [3.125, 328], [3.25, 324.5], [4.4, 322]]);
  const width = key(t, [[2.0, 3], [2.125, 130], [2.25, 156], [2.375, 147], [2.5, 142], [2.75, 142], [2.875, 161], [3, 188], [3.125, 217], [3.25, 231], [3.375, 228], [3.5, 212], [3.625, 178], [4.4, 178]]);
  const height = key(t, [[2.0, 3], [2.125, 123], [2.25, 147], [2.375, 133], [2.5, 120], [2.75, 135], [2.875, 141], [3, 150], [3.125, 197], [3.25, 220], [3.375, 216], [3.5, 201], [3.625, 169], [4.4, 168]]);
  const blink = Math.sin(Math.PI * segment(t, 2.41, 2.59));
  const look = key(t, [[2.0, 0], [2.25, 0.15], [2.75, -0.04], [3.2, 0.13], [4.4, 0.13]]);
  const leave = 1 - ease(segment(t, 4.30, 4.48));
  const friends = [[1,105.7,227,13.6,99.7,97.1],[3,301.4,241.2,-15.8,89,108.3],[4,93.2,403.1,-21.1,91.5,94.7],[2,302,399.1,23.5,102.9,115.5]];
  for (let i = 0; i < friends.length; i++) {
    const f = friends[i], p = Math.max(0, spring(Math.max(0, t - 3.2 - i * 0.045)));
    const bob = 2.5 * Math.sin((t - 3.2) * 2 * Math.PI + i) * segment(t, 3.65, 3.9);
    mascot(c, art, f[0] as Kind, mix(196.5, f[1], p), mix(316, f[2], p) + bob, f[4] * leave * Math.min(1, p * 1.8), f[5] * leave * Math.min(1, p * 1.8), f[3] * p, segment(t, 3.31, 3.39) * (1 - segment(t, 3.42, 3.5)), 0, i === 2 ? -0.15 : 0, i === 2 ? -90 : 0);
  }
  mascot(c, art, 0, x, y, width * leave, height * leave, key(t, [[2,0],[3.6,0],[4.3,-11]]), Math.pow(blink,3), 0, look);
}
function space(c: SkCanvas, art: Art, t: number) {
  'worklet'; const born = spring(Math.max(0, t - 4.47)), end = 1 - ease(segment(t, 6.95, 7.22));
  c.save(); c.translate(196.5, 326); c.scale(Math.max(0.001, born * end), Math.max(0.001, born * end)); c.translate(-196.5, -326);
  const flap = ease(segment(t, 4.65, 5.22)), right = ease(segment(t, 4.71, 5.28));
  rounded(c, 120, 218, 151, 213, 9, YELLOW); c.drawRect(Skia.XYWHRect(120, 242, 151, 165), paint(0xF1F6F8));
  const lp = Skia.PathBuilder.Make().moveTo(120, 242).lineTo(mix(196, 92, flap), mix(242, 229, flap))
    .quadTo(mix(200, 87, flap), 323, mix(196, 92, flap), mix(407, 390, flap)).lineTo(120, 407).close().detach();
  const rp = Skia.PathBuilder.Make().moveTo(271, 242).lineTo(mix(196, 299, right), mix(242, 227, right))
    .quadTo(mix(192, 305, right), 323, mix(196, 299, right), mix(407, 390, right)).lineTo(271, 407).close().detach();
  c.drawPath(lp, paint(0xFFECB0)); c.drawPath(rp, paint(0xFFECB0));
  shadow(c, 82, 255, 32, 7); shadow(c, 265, 483, 46, 10);
  mascot(c, art, 3, key(t, [[4.48, 73], [5.4, 70.5], [6.1, 83.3], [6.7, 72.1]]), key(t, [[4.48, 231], [5.4, 212.2], [6.1, 214.9], [6.7, 224.6]]), 54, 64, key(t, [[4.48, -15.4], [5.4, -15.9], [6.1, -36.4], [6.7, -0.7]]), 0, 0, 0);
  mascot(c, art, 4, key(t, [[4.48, 286], [5.4, 305.5], [6.1, 256], [6.7, 265.1]]), key(t, [[4.48, 423.4], [5.4, 421.3], [6.1, 438.1], [6.7, 428.3]]), 66, 66, key(t, [[4.48, 14.8], [5.4, 17.2], [6.1, -24.8], [6.7, -8.9]]), key(t, [[4.8,0],[6.1,0],[6.7,0.85],[6.95,0]]), 0, -0.17);
  if (t > 5.7) {
    const gx = key(t, [[5.7, 81], [5.75, 76.1], [5.875, 72], [6, 77.2], [6.125, 102.1], [6.25, 129.9], [6.375, 147.1], [6.5, 148.5]]);
    const gy = key(t, [[5.7, 274], [5.75, 269.4], [5.875, 281.4], [6, 284.7], [6.125, 298.3], [6.25, 313.6], [6.375, 323.7], [6.5, 324.2]]);
    const bx = key(t, [[5.7, 306], [5.75, 308.4], [5.875, 322], [6, 315.5], [6.125, 287.9], [6.25, 256.8], [6.375, 239.3], [6.5, 236.6]]);
    const by = key(t, [[5.7, 392], [5.75, 388.4], [5.875, 376.4], [6, 372.5], [6.125, 355.3], [6.25, 337.1], [6.375, 325], [6.5, 324.2]]);
    rounded(c, gx - 23.5, gy - 23.5, 47, 47, 7, GREEN); oval(c, bx - 25.5, by - 25.5, 51, 51, BLUE);
  }
  hand(c, art, key(t, [[4.8, 178], [5.4, 114], [6.1, 138.6], [6.7, 137.5]]), key(t, [[4.8, 286], [5.4, 246.7], [6.1, 227.3], [6.7, 283.8]]), PINK, 25, 28, -15);
  hand(c, art, key(t, [[4.8, 181], [5.4, 82.4], [6.1, 63], [6.7, 110.8]]), key(t, [[4.8, 353], [5.4, 274], [6.1, 300], [6.7, 302.1]]), PINK, 25, 28, 18);
  hand(c, art, key(t, [[4.8, 211], [5.4, 255], [6.1, 219.4], [6.7, 242.9]]), key(t, [[4.8, 320], [5.4, 345], [6.1, 371.2], [6.7, 368.2]]), RED, 29, 27, 15);
  hand(c, art, key(t, [[4.8, 207], [5.4, 300], [6.1, 340], [6.7, 272.7]]), key(t, [[4.8, 383], [5.4, 412], [6.1, 374], [6.7, 375.8]]), RED, 25, 28, 10);
  c.restore();
}

function pulse(t: number, center: number, duration: number) {
  'worklet'; const p = segment(t, center - duration / 2, center + duration / 2); return p > 0 && p < 1 ? Math.sin(Math.PI * p) : 0;
}
function memoryPlay(c: SkCanvas, path: SkPath, x: number, y: number, w: number, h: number) {
  'worklet'; c.save(); c.translate(x - w / 2, y - h / 2); c.scale(w, h); c.drawPath(path, paint(PINK)); c.restore();
}
function memories(c: SkCanvas, art: Art, t: number) {
  'worklet'; const entrance = Math.max(0, spring(Math.max(0, t - 7.2))), bob = Math.sin((t - 7.45) * 2 * Math.PI) * 3;
  const flash = pulse(t, 7.86, 0.18) + pulse(t, 8.55, 0.18) + pulse(t, 9.22, 0.18);
  shadow(c, 112, 473, 44, 7, 0.45); mascot(c, art, 3, 109.3, 373 + bob, 118.6 * entrance, 136.6 * entrance, key(t, [[7.48, -2.8], [7.78, -2.8], [8.3, -15.4], [8.9, -13], [9.4, -2.8]]), 0, 0.23 + flash * 0.25, 0.06);
  phone(c, key(t, [[7.48, 149.8], [7.85, 174], [8.9, 174], [9.4, 160]]), key(t, [[7.48, 425.8], [7.85, 402], [8.3, 399.5], [8.9, 405], [9.4, 416]]) + bob, 31 * entrance, 52 * entrance, -9 + 4 * flash, 1, flash * 0.6);
  const red = ease(segment(t, 7.96, 8.13)), pink = ease(segment(t, 8.9, 9.2));
  const tint = red < 0.5 ? 0xFFFBEE : pink < 0.5 ? 0xFFF0EF : 0xFFF0FB, scale = entrance * (1 + flash * 0.055);
  c.save(); c.translate(252, 255 - bob * 0.6); c.scale(scale, scale);
  oval(c, -88, -63, 176, 126, tint);
  const bubble = Skia.PathBuilder.Make()
    .moveTo(-42, 43).quadTo(-38, 80, -29, 78).lineTo(2, 57).close().detach(); c.drawPath(bubble, paint(tint));
  c.saveLayer(paint(0xffffff, 1 - red)); c.rotate(-16, 0, 0); rounded(c, -31, -31, 62, 62, 7, YELLOW); c.restore();
  if (red > 0) { c.saveLayer(paint(0xffffff, red * (1 - pink))); transformed(c, art.redBloom, 0, 0, 80.8, 85.2, RED, 0); c.restore(); }
  if (pink > 0) { c.saveLayer(paint(0xffffff, pink)); memoryPlay(c, art.playLarge, -10, -9.558013, 74, 83); memoryPlay(c, art.playSmall, 48.5, 0.441987, 49, 51); c.restore(); }
  c.restore();
}
export function drawStory(c: SkCanvas, art: Art, t: number) {
  'worklet'; const home = 1 - ease(segment(t, 9.45, 9.85));
  for (let i = 0; i < 2; i++) {
    c.save(); c.translate(-t * (i === 0 ? 9 : 38) * home, 0);
    const p = paint(0xffffff); p.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: 535 }, { x: 0, y: 728 }, [color(i === 0 ? 0xF5F9FB : 0xE6EDF1), color(0xffffff)], null, TileMode.Clamp));
    c.drawPath(art.clouds[i], p); c.restore();
  }
  const first = t < 1.98 ? 1 - ease(segment(t, 1.82, 1.98)) : ease(segment(t, 9.5, 9.85));
  if (first > 0.001) { c.saveLayer(paint(0xffffff, first)); hangout(c, art, t > 9.45 ? 0 : t); c.restore(); }
  const second = ease(segment(t, 2.02, 2.12)) * (1 - ease(segment(t, 4.30, 4.48)));
  if (second > 0.001) { c.saveLayer(paint(0xffffff, second)); connection(c, art, t); c.restore(); }
  const third = ease(segment(t, 4.48, 4.65)) * (1 - ease(segment(t, 6.95, 7.22)));
  if (third > 0.001) { c.saveLayer(paint(0xffffff, third)); space(c, art, t); c.restore(); }
  const fourth = ease(segment(t, 7.2, 7.48)) * (1 - ease(segment(t, 9.45, 9.78)));
  if (fourth > 0.001) { c.saveLayer(paint(0xffffff, fourth)); memories(c, art, t); c.restore(); }
}
