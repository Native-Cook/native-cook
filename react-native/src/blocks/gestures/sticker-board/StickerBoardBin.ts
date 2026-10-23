import { BlurStyle, ClipOp, Skia, type SkCanvas, type SkFont, StrokeCap, StrokeJoin, TileMode, vec } from '@shopify/react-native-skia';

import { clamp01, easeOutBack } from './StickerBoardDraw';

/*
 * The bin: a small steel pedal bin drawn in vector at the bottom of the board. Its units are points
 * of a 402 pt wide screen, measured from the middle of its base, up being negative.
 */

// Body: radius at the rim and at the foot, height, and how flat its round top and foot look.
export const BIN_TOP_R = 28;
export const BIN_FOOT_R = 24.5;
export const BIN_BODY = 64;
export const BIN_TILT = 0.28;
const BIN_SIDE = Math.sqrt(1 - BIN_TILT * BIN_TILT);
// The lid: a little wider than the rim, its thickness, and how far it opens on its hinge (degrees).
const LID_R = 29.6;
const LID_THICK = 4.5;
export const LID_OPEN = 100;
const FOOT = 6;
// The base stands this far above the bottom safe area; tucked away it sits this far below the screen.
export const BIN_FLOOR = 18;
const BIN_TUCK = 120;
// Where a sticker hangs over the bin as it is pulled in, and where a paper ball goes in.
export const BIN_HOVER = 72;
export const BIN_MOUTH_DROP = 4;
// The counter plate on the front, and the roll of its digits.
const PLATE_W = 28;
const PLATE_H = 16;
const PLATE_Y = -BIN_BODY * 0.46;
export const ROLL_TIME = 0.34;

/** Everything about the bin that moves, kept as springs (value and speed) and a few flags. */
export type BinState = {
  show: number;
  showV: number;
  lid: number;
  lidV: number;
  lean: number;
  leanV: number;
  grow: number;
  growV: number;
  squash: number;
  squashV: number;
  wobble: number;
  wobbleV: number;
  magnet: number;
  magnetV: number;
  // A loose sticker is within reach and held over the mouth.
  snapped: boolean;
  // A ball has gone in and the lid is slamming; whether it has hit, and who threw the ball.
  landed: boolean;
  slam: boolean;
  slamHit: boolean;
  byUser: boolean;
  // Seconds the bin stays up after the slam.
  linger: number;
  // Stickers thrown away (shown modulo 100), the number before, the roll between them, and balls
  // that went in since the last slam.
  count: number;
  prev: number;
  roll: number;
  pending: number;
};

export function binRest(): BinState {
  return {
    show: 0,
    showV: 0,
    lid: 0,
    lidV: 0,
    lean: 0,
    leanV: 0,
    grow: 1,
    growV: 0,
    squash: 0,
    squashV: 0,
    wobble: 0,
    wobbleV: 0,
    magnet: 0,
    magnetV: 0,
    snapped: false,
    landed: false,
    slam: false,
    slamHit: false,
    byUser: false,
    linger: 0,
    count: 0,
    prev: 0,
    roll: 1,
    pending: 0,
  };
}

/** Where the bin stands on the screen and how it is turned and scaled, from its springs. */
export type BinFrame = { x: number; y: number; rot: number; sx: number; sy: number; alpha: number };

export function binFrame(b: BinState, k: number, width: number, height: number, bottom: number, reduced: boolean): BinFrame {
  'worklet';
  const baseY = height - bottom - BIN_FLOOR * k;
  if (reduced) {
    return { x: width / 2, y: baseY, rot: 0, sx: k, sy: k, alpha: clamp01(b.show) };
  }
  const tuck = bottom + BIN_TUCK * k;
  const g = Math.max(0.2, b.grow);
  return {
    x: width / 2,
    y: baseY + (1 - b.show) * tuck,
    rot: b.lean + b.wobble,
    sx: k * g * (1 + 0.5 * b.squash),
    sy: k * g * (1 - b.squash),
    alpha: 1,
  };
}

/** A point in the bin's own units on the screen. */
export function binPoint(f: BinFrame, lx: number, ly: number) {
  'worklet';
  const a = (f.rot * Math.PI) / 180;
  const x = lx * f.sx;
  const y = ly * f.sy;
  return [f.x + x * Math.cos(a) - y * Math.sin(a), f.y + x * Math.sin(a) + y * Math.cos(a)];
}

/** The fixed parts of the drawing, made once: outlines and the steel's shading. */
export function makeBinArt() {
  const H = BIN_BODY;
  const RT = BIN_TOP_R;
  const RB = BIN_FOOT_R;
  const T = BIN_TILT;
  const top = Skia.XYWHRect(-RT, -H - RT * T, RT * 2, RT * 2 * T);
  const foot = Skia.XYWHRect(-RB, -RB * T, RB * 2, RB * 2 * T);
  const body = Skia.PathBuilder.Make()
    .moveTo(-RT, -H)
    .lineTo(-RB, 0)
    .arcToOval(foot, 180, -180, false)
    .lineTo(RT, -H)
    .arcToOval(top, 0, -180, false)
    .close()
    .build();
  // The dark plastic foot ring: between the foot and a line FOOT above it.
  const rf = RB + ((RT - RB) * FOOT) / H;
  const footTop = Skia.XYWHRect(-rf, -FOOT - rf * T, rf * 2, rf * 2 * T);
  const footRing = Skia.PathBuilder.Make()
    .moveTo(-rf, -FOOT)
    .arcToOval(footTop, 180, -180, false)
    .lineTo(RB, 0)
    .arcToOval(foot, 0, 180, false)
    .close()
    .build();
  const footEdge = Skia.PathBuilder.Make().addArc(footTop, 180, -180).build();
  // A bead just under the rim, and the rim itself.
  const bead = Skia.PathBuilder.Make()
    .addArc(Skia.XYWHRect(-RT + 0.3, -H + 3.2 - RT * T, (RT - 0.3) * 2, (RT - 0.3) * 2 * T), 180, -180)
    .build();
  const rimR = RT + 0.9;
  const rim = Skia.XYWHRect(-rimR, -H - rimR * T, rimR * 2, rimR * 2 * T);
  const mouthR = RT - 2;
  const mouth = Skia.XYWHRect(-mouthR, -H - mouthR * T, mouthR * 2, mouthR * 2 * T);
  // A ball going in is seen above the rim and through the mouth, never through the front.
  const inside = Skia.PathBuilder.Make()
    .addRect(Skia.XYWHRect(-400, -1000, 800, 1000 - H))
    .addOval(mouth)
    .build();

  const steel = Skia.Shader.MakeLinearGradient(
    vec(-RT, 0),
    vec(RT, 0),
    ['#585c61', '#8a8f94', '#c8ccd0', '#f5f7f8', '#dde0e3', '#aab0b5', '#7e8388', '#63676c', '#4b4f54'].map((c) => Skia.Color(c)),
    [0, 0.08, 0.21, 0.29, 0.34, 0.55, 0.77, 0.91, 1],
    TileMode.Clamp,
  );
  const dusk = Skia.Shader.MakeLinearGradient(
    vec(0, -H),
    vec(0, 0),
    [Skia.Color('rgba(20,22,25,0)'), Skia.Color('rgba(20,22,25,0)'), Skia.Color('rgba(20,22,25,0.24)')],
    [0, 0.55, 1],
    TileMode.Clamp,
  );
  const cavity = Skia.Shader.MakeLinearGradient(
    vec(0, -H - mouthR * T),
    vec(0, -H + mouthR * T),
    [Skia.Color('#4f5358'), Skia.Color('#1f2124'), Skia.Color('#0f1011')],
    [0, 0.45, 1],
    TileMode.Clamp,
  );
  const lidTop = Skia.Shader.MakeLinearGradient(
    vec(-LID_R, -LID_R),
    vec(LID_R, LID_R),
    ['#fbfcfc', '#dadee1', '#aeb3b8', '#868b90'].map((c) => Skia.Color(c)),
    [0, 0.34, 0.72, 1],
    TileMode.Clamp,
  );
  const lidBand = Skia.Shader.MakeLinearGradient(
    vec(-LID_R, 0),
    vec(LID_R, 0),
    ['#4c5055', '#9ea3a8', '#d9dcdf', '#7c8186', '#43474b'].map((c) => Skia.Color(c)),
    [0, 0.2, 0.3, 0.7, 1],
    TileMode.Clamp,
  );
  const underside = Skia.Shader.MakeLinearGradient(
    vec(-LID_R, 0),
    vec(LID_R, 0),
    ['#3c4044', '#6a6f74', '#55595e', '#3a3d41'].map((c) => Skia.Color(c)),
    [0, 0.3, 0.65, 1],
    TileMode.Clamp,
  );
  const plate = Skia.RRectXY(Skia.XYWHRect(-PLATE_W / 2, PLATE_Y - PLATE_H / 2, PLATE_W, PLATE_H), 4, 4);
  const platePath = Skia.PathBuilder.Make().addRRect(plate).build();
  return { body, footRing, footEdge, bead, rim, mouth, inside, steel, dusk, cavity, lidTop, lidBand, underside, plate, platePath };
}

export type BinArt = ReturnType<typeof makeBinArt>;

function fillPaint(color: string) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  return p;
}

function strokePaint(color: string, width: number) {
  'worklet';
  const p = fillPaint(color);
  p.setStyle(1);
  p.setStrokeWidth(width);
  p.setStrokeCap(StrokeCap.Round);
  p.setStrokeJoin(StrokeJoin.Round);
  return p;
}

/** A circle of radius `r` on the lid's own plane, seen through the lid's hinge turn (`a`, `y`). */
function lidDisc(canvas: SkCanvas, y: number, a: number, r: number, paint: ReturnType<typeof fillPaint>) {
  'worklet';
  canvas.save();
  canvas.translate(0, y);
  canvas.scale(1, a);
  canvas.drawCircle(0, 0, r, paint);
  canvas.restore();
}

/**
 * The lid, hinged at the back of the rim. Turned back by θ, a point of its plane `y` from its
 * middle lands at `y·(t·cosθ − s·sinθ)` on the screen, so it is drawn as a circle squeezed by that
 * factor; past about 17° the factor turns negative and its underside faces us.
 */
function drawLid(canvas: SkCanvas, art: BinArt, lid: number) {
  'worklet';
  const th = (Math.max(0, lid) * LID_OPEN * Math.PI) / 180;
  const T = BIN_TILT;
  let a = T * Math.cos(th) - BIN_SIDE * Math.sin(th);
  if (Math.abs(a) < 0.004) a = a < 0 ? -0.004 : 0.004;
  const yb = -BIN_BODY - T * LID_R + LID_R * a;
  const yt = yb - (T * Math.sin(th) + BIN_SIDE * Math.cos(th)) * LID_THICK;
  const band = Skia.Paint();
  band.setAntiAlias(true);
  band.setShader(art.lidBand);
  const edge = strokePaint('rgba(36, 38, 42, 0.55)', 0.7);
  if (a >= 0) {
    // Top face up: the edge band below, then the domed top.
    lidDisc(canvas, yb, a, LID_R, band);
    canvas.drawRect(Skia.XYWHRect(-LID_R, Math.min(yb, yt), LID_R * 2, Math.abs(yb - yt)), band);
    const face = Skia.Paint();
    face.setAntiAlias(true);
    face.setShader(art.lidTop);
    lidDisc(canvas, yt, a, LID_R, face);
    canvas.save();
    canvas.translate(0, yt);
    canvas.scale(1, a);
    canvas.drawCircle(0, 0, LID_R, edge);
    // An embossed ring, and a sharp highlight along the front edge.
    canvas.drawCircle(0, 0, LID_R * 0.72, strokePaint('rgba(60, 64, 70, 0.32)', 1));
    canvas.drawPath(Skia.PathBuilder.Make().addArc(Skia.XYWHRect(-LID_R * 0.72, -LID_R * 0.72 + 1.2, LID_R * 1.44, LID_R * 1.44), 20, 140).build(), strokePaint('rgba(255, 255, 255, 0.7)', 0.9));
    canvas.drawPath(Skia.PathBuilder.Make().addArc(Skia.XYWHRect(-LID_R * 0.9, -LID_R * 0.9, LID_R * 1.8, LID_R * 1.8), 205, 55).build(), strokePaint('rgba(255, 255, 255, 0.95)', 1.5));
    canvas.restore();
  } else {
    // Underside to us: the top face is behind, then the band, then the underside.
    lidDisc(canvas, yt, a, LID_R, band);
    canvas.drawRect(Skia.XYWHRect(-LID_R, Math.min(yb, yt), LID_R * 2, Math.abs(yb - yt)), band);
    const under = Skia.Paint();
    under.setAntiAlias(true);
    under.setShader(art.underside);
    lidDisc(canvas, yb, a, LID_R, under);
    canvas.save();
    canvas.translate(0, yb);
    canvas.scale(1, a);
    canvas.drawCircle(0, 0, LID_R, edge);
    canvas.drawCircle(0, 0, LID_R * 0.82, strokePaint('rgba(20, 22, 25, 0.4)', 1.2));
    canvas.restore();
  }
}

/** The number on the plate, each digit centred in a cell of the same width so it never shifts. */
function drawNumber(canvas: SkCanvas, n: number, y: number, font: SkFont, paint: ReturnType<typeof fillPaint>) {
  'worklet';
  const text = `${Math.max(0, Math.floor(n)) % 100}`;
  const cell = font.measureText('0').width;
  const x0 = (-cell * text.length) / 2;
  for (let i = 0; i < text.length; i += 1) {
    const ch = text.charAt(i);
    const w = font.measureText(ch).width;
    canvas.drawText(ch, x0 + cell * i + (cell - w) / 2, y, paint, font);
  }
}

/**
 * The bin and its contact shadow. `inside` draws whatever is falling in, in the bin's own units,
 * after the lid (which stands behind the mouth when open) and clipped to the mouth.
 */
export function drawBin(canvas: SkCanvas, art: BinArt, f: BinFrame, b: BinState, font: SkFont, k: number, inside: () => void) {
  'worklet';
  if (b.show < 0.004 && Math.abs(b.showV) < 0.01 && f.alpha >= 1) return;
  if (f.alpha <= 0.004) return;
  const layer = f.alpha < 0.995;
  if (layer) {
    const p = Skia.Paint();
    p.setAlphaf(f.alpha);
    canvas.saveLayer(p);
  }
  const RB = BIN_FOOT_R;
  const T = BIN_TILT;
  const H = BIN_BODY;
  const gx = f.sx / Math.max(0.0001, k);
  // The contact shadow stays on the board, under the foot, whatever the bin's lean.
  const soft = fillPaint('#2a1505');
  soft.setAlphaf(0.34);
  soft.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 5 * k, true));
  canvas.drawOval(Skia.XYWHRect(f.x - (RB + 9) * f.sx, f.y - 3 * k, (RB + 9) * 2 * f.sx, (RB * T * 2 + 8) * k), soft);
  const tight = fillPaint('#1c0e03');
  tight.setAlphaf(0.32);
  tight.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 1.6 * k, true));
  canvas.drawOval(Skia.XYWHRect(f.x - (RB + 1.5) * gx * k, f.y - RB * T * k, (RB + 1.5) * 2 * gx * k, (RB * T * 2 + 1.5) * k), tight);

  canvas.save();
  canvas.translate(f.x, f.y);
  canvas.rotate(f.rot, 0, 0);
  canvas.scale(f.sx, f.sy);

  // Body: brushed steel, darker towards the foot, a sharp highlight and a faint second one.
  const steel = Skia.Paint();
  steel.setAntiAlias(true);
  steel.setShader(art.steel);
  canvas.drawPath(art.body, steel);
  canvas.save();
  canvas.clipPath(art.body, ClipOp.Intersect, true);
  const dusk = Skia.Paint();
  dusk.setShader(art.dusk);
  canvas.drawRect(Skia.XYWHRect(-BIN_TOP_R, -H - 20, BIN_TOP_R * 2, H + 30), dusk);
  canvas.drawLine(-BIN_TOP_R * 0.37, -H + 5, -RB * 0.37, -FOOT - 2, strokePaint('rgba(255, 255, 255, 0.92)', 1.4));
  canvas.drawLine(BIN_TOP_R * 0.6, -H + 6, RB * 0.6, -FOOT - 3, strokePaint('rgba(255, 255, 255, 0.28)', 1));
  canvas.drawPath(art.bead, strokePaint('rgba(30, 32, 36, 0.3)', 1.1));
  canvas.restore();
  // The foot ring and the pedal, pressed down a little as the lid opens.
  canvas.drawPath(art.footRing, fillPaint('#26282b'));
  canvas.drawPath(art.footEdge, strokePaint('rgba(255, 255, 255, 0.2)', 0.8));
  canvas.drawPath(art.body, strokePaint('rgba(28, 30, 33, 0.55)', 0.8));
  const down = 1.6 * clamp01(b.lid);
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-11, RB * T - 3 + down, 22, 6), 2.6, 2.6), fillPaint('#1d1f22'));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-10.4, RB * T - 2.6 + down, 20.8, 2.2), 1.1, 1.1), fillPaint('#4b4f54'));

  // The counter plate, its digits rolling up to the new number.
  canvas.drawRRect(art.plate, fillPaint('#202226'));
  canvas.drawRRect(art.plate, strokePaint('rgba(255, 255, 255, 0.16)', 0.6));
  canvas.save();
  canvas.clipPath(art.platePath, ClipOp.Intersect, true);
  const ink = fillPaint('#f3eee4');
  const r = clamp01(b.roll);
  const e = r >= 1 ? 1 : easeOutBack(r);
  const base = PLATE_Y + 4.1;
  if (r < 1) drawNumber(canvas, b.prev, base - e * PLATE_H, font, ink);
  drawNumber(canvas, b.count, base + (1 - e) * PLATE_H, font, ink);
  canvas.restore();

  // The rim, and the dark mouth inside it.
  canvas.drawOval(art.rim, fillPaint('#e4e7e9'));
  const cavity = Skia.Paint();
  cavity.setAntiAlias(true);
  cavity.setShader(art.cavity);
  canvas.drawOval(art.mouth, cavity);
  canvas.drawOval(art.rim, strokePaint('#3a3d41', 0.9));
  canvas.drawOval(art.mouth, strokePaint('rgba(0, 0, 0, 0.5)', 0.7));

  drawLid(canvas, art, b.lid);
  // The hinge, seen once the lid stands up behind the mouth.
  if (b.lid > 0.3) {
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-8, -H - (BIN_TOP_R + 0.9) * T - 2.4, 16, 3.6), 1.2, 1.2), fillPaint('#2c2f33'));
  }
  canvas.save();
  canvas.clipPath(art.inside, ClipOp.Intersect, true);
  inside();
  canvas.restore();
  canvas.restore();
  if (layer) canvas.restore();
}
