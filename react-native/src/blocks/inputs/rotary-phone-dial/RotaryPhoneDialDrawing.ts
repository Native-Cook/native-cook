// Drawing for the rotary dial, as worklets on a Skia canvas. The light comes from the upper left
// throughout: bevels are bright toward it and dark away from it, shadows fall down and right.
// The chrome's reflections are a sweep gradient around the dial's centre that does not turn with
// the wheel (a polished disc spinning in place keeps its reflections), so only the holes, the
// shadows they cast and the numbers they reveal move.

import { BlendMode, BlurStyle, ClipOp, FillType, PaintStyle, Skia, type SkCanvas, type SkFont, StrokeCap, TileMode, vec } from '@shopify/react-native-skia';

import { HOLE_DIGITS, HOLES, holeRest, STOP_ANGLE } from './RotaryPhoneDialSim';

export type DialGeometry = {
  width: number;
  height: number;
  cx: number;
  cy: number;
  /** The enamel number plate. */
  plateR: number;
  /** The finger wheel. */
  wheelR: number;
  /** Radius of the circle the holes sit on, and of each hole. */
  ringR: number;
  holeR: number;
  /** The opening in the middle that holds the number card. */
  hubR: number;
  /** The paper strip's window. */
  strip: { x: number; y: number; w: number; h: number };
};

export type DialFonts = {
  digit: SkFont;
  letters: SkFont;
  cardName: SkFont;
  cardNumber: SkFont;
};

// Bakelite body, enamel plate, ink, chrome.
const BODY_HI = '#2F6253';
const BODY = '#163E33';
const BODY_LO = '#06150F';
const PLATE = '#F1E8D3';
const PLATE_EDGE = '#D8CAAA';
const INK = '#201D18';
const RED = '#A23A2C';
const PAPER = '#F4EDDC';
const CHROME = ['#D6D8DA', '#FAFAF9', '#979BA0', '#E6E7E8', '#6A6F74', '#CDD0D2', '#FBFBFA', '#8C9095', '#D6D8DA'];
const CHROME_STOPS = [0, 0.1, 0.24, 0.38, 0.5, 0.62, 0.76, 0.88, 1];

// The autoPlay finger reaches in from this direction (degrees, clockwise from the right).
const FINGER_FROM = 58;
// How far the finger shows before it has faded out, in fingertip radii.
const FINGER_LENGTH = 4.6;

// Letters over the digits, as on old exchange dials.
const LETTERS = ['', 'ABC', 'DEF', 'GHI', 'JKL', 'MNO', 'PRS', 'TUV', 'WXY', 'OPER'];

/** The name and number on the card in the middle. */
export const CARD_NAME = 'ELMWOOD';
export const CARD_NUMBER = '5-0193';

function rad(deg: number) {
  'worklet';
  return (deg * Math.PI) / 180;
}

function color(c: string) {
  'worklet';
  return Skia.Color(c);
}

function fill(c: string) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(color(c));
  return p;
}

function soft(c: string, sigma: number) {
  'worklet';
  const p = fill(c);
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma, true));
  return p;
}

function stroke(width: number) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  return p;
}

/** A light-to-dark linear gradient across a box around (x, y) of half-size r, lit from the upper left. */
function bevel(x: number, y: number, r: number, light: string, dark: string) {
  'worklet';
  return Skia.Shader.MakeLinearGradient(
    vec(x - r * 0.7, y - r * 0.7),
    vec(x + r * 0.7, y + r * 0.7),
    [color(light), color(dark)],
    null,
    TileMode.Clamp,
  );
}

function chrome(g: DialGeometry) {
  'worklet';
  return Skia.Shader.MakeSweepGradient(g.cx, g.cy, CHROME.map((c) => color(c)), CHROME_STOPS, TileMode.Clamp);
}

function centredText(canvas: SkCanvas, text: string, x: number, y: number, font: SkFont, paint: ReturnType<typeof Skia.Paint>, tracking: number) {
  'worklet';
  if (tracking === 0) {
    canvas.drawText(text, x - font.getTextWidth(text) / 2, y, paint, font);
    return;
  }
  // Letter-spaced: each glyph placed by hand.
  let total = 0;
  for (let i = 0; i < text.length; i += 1) total += font.getTextWidth(text[i]) + (i > 0 ? tracking : 0);
  let at = x - total / 2;
  for (let i = 0; i < text.length; i += 1) {
    canvas.drawText(text[i], at, y, paint, font);
    at += font.getTextWidth(text[i]) + tracking;
  }
}

/** The finger wheel's metal: a disc with the ten holes and the middle cut out. */
export function wheelPath(g: DialGeometry, phi: number) {
  'worklet';
  const b = Skia.PathBuilder.Make();
  b.setFillType(FillType.EvenOdd);
  b.addCircle(g.cx, g.cy, g.wheelR);
  for (let i = 0; i < HOLES; i += 1) {
    const a = rad(holeRest(i) + phi);
    b.addCircle(g.cx + g.ringR * Math.cos(a), g.cy + g.ringR * Math.sin(a), g.holeR);
  }
  b.addCircle(g.cx, g.cy, g.hubR);
  return b.build();
}

/** Everything that never moves: the phone's body, the strip's window, the enamel plate. */
export function drawStatic(canvas: SkCanvas, g: DialGeometry, fonts: DialFonts) {
  'worklet';
  const { width, height, cx, cy, plateR, ringR } = g;
  // Bakelite, lit from the upper left, with a broad soft sheen.
  const body = Skia.Paint();
  body.setShader(
    Skia.Shader.MakeRadialGradient(vec(width * 0.28, height * 0.16), height * 1.05, [color(BODY_HI), color(BODY), color(BODY_LO)], [0, 0.45, 1], TileMode.Clamp),
  );
  canvas.drawRect(Skia.XYWHRect(0, 0, width, height), body);
  canvas.save();
  canvas.rotate(-28, width * 0.3, height * 0.22);
  canvas.drawOval(Skia.XYWHRect(width * 0.3 - width * 0.55, height * 0.22 - 38, width * 1.1, 76), soft('rgba(255,255,255,0.07)', 26));
  canvas.restore();

  // The strip's window: a chrome frame sunk into the body, paper inside.
  const s = g.strip;
  const frame = Skia.RRectXY(Skia.XYWHRect(s.x - 6, s.y - 6, s.w + 12, s.h + 12), 14, 14);
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(s.x - 4, s.y + 2, s.w + 12, s.h + 12), 14, 14), soft('rgba(0,0,0,0.5)', 8));
  const rim = Skia.Paint();
  rim.setAntiAlias(true);
  rim.setShader(bevel(s.x + s.w / 2, s.y + s.h / 2, s.w / 2, '#F7F7F6', '#5E6368'));
  canvas.drawRRect(frame, rim);
  const paperBox = Skia.RRectXY(Skia.XYWHRect(s.x, s.y, s.w, s.h), 9, 9);
  const paper = Skia.Paint();
  paper.setShader(Skia.Shader.MakeLinearGradient(vec(0, s.y), vec(0, s.y + s.h), [color('#E3D9C2'), color(PAPER), color('#EDE4CF')], [0, 0.28, 1], TileMode.Clamp));
  canvas.drawRRect(paperBox, paper);
  canvas.save();
  canvas.clipRRect(paperBox, ClipOp.Intersect, true);
  // Ruled line, perforations along both edges, a shadow under the frame's top lip.
  const rule = stroke(0.8);
  rule.setColor(color('rgba(162,58,44,0.35)'));
  canvas.drawLine(s.x, s.y + s.h * 0.74, s.x + s.w, s.y + s.h * 0.74, rule);
  for (let x = s.x + 10; x < s.x + s.w; x += 14) {
    canvas.drawCircle(x, s.y + 7, 2.1, fill('rgba(70,55,35,0.22)'));
    canvas.drawCircle(x, s.y + s.h - 7, 2.1, fill('rgba(70,55,35,0.22)'));
  }
  canvas.drawRect(Skia.XYWHRect(s.x, s.y - 10, s.w, 14), soft('rgba(40,28,10,0.32)', 5));
  canvas.restore();

  // The dial's well: a dark recess, a chrome bezel, then the enamel plate.
  canvas.drawCircle(cx + 4, cy + 9, plateR * 1.13, soft('rgba(0,0,0,0.55)', 16));
  canvas.drawCircle(cx, cy, plateR * 1.1, fill(BODY_LO));
  const bezel = Skia.Paint();
  bezel.setAntiAlias(true);
  bezel.setShader(bevel(cx, cy, plateR * 1.08, '#F4F4F3', '#4D5257'));
  canvas.drawCircle(cx, cy, plateR * 1.075, bezel);
  canvas.drawCircle(cx, cy, plateR * 1.04, fill('#2A2C2E'));
  const plate = Skia.Paint();
  plate.setAntiAlias(true);
  plate.setShader(
    Skia.Shader.MakeRadialGradient(vec(cx - plateR * 0.3, cy - plateR * 0.35), plateR * 1.5, [color('#FBF6EA'), color(PLATE), color(PLATE_EDGE)], [0, 0.5, 1], TileMode.Clamp),
  );
  canvas.drawCircle(cx, cy, plateR, plate);
  // Fine enamel rings near the rim.
  const ring = stroke(0.8);
  ring.setColor(color('rgba(32,29,24,0.55)'));
  canvas.drawCircle(cx, cy, plateR - 4, ring);
  ring.setColor(color('rgba(32,29,24,0.18)'));
  canvas.drawCircle(cx, cy, g.wheelR + 3, ring);

  // The numbers, printed on the plate under each hole's resting place.
  const ink = fill(INK);
  const red = fill(RED);
  for (let i = 0; i < HOLES; i += 1) {
    const a = rad(holeRest(i));
    const x = cx + ringR * Math.cos(a);
    const y = cy + ringR * Math.sin(a);
    const d = HOLE_DIGITS[i];
    const label = LETTERS[d === 0 ? 9 : d - 1];
    const digit = String(d);
    if (label.length > 0) {
      centredText(canvas, label, x, y - g.holeR * 0.3, fonts.letters, red, 0.8);
      centredText(canvas, digit, x, y + g.holeR * 0.54, fonts.digit, ink, 0);
    } else {
      centredText(canvas, digit, x, y + g.holeR * 0.31, fonts.digit, ink, 0);
    }
    // A small dot on the outer ring marks each position.
    canvas.drawCircle(cx + (plateR - 9) * Math.cos(a), cy + (plateR - 9) * Math.sin(a), 1.6, ink);
  }
}

/** The wheel, wound `phi` degrees clockwise: its shadow on the plate, the metal, the bevels. */
export function drawWheel(canvas: SkCanvas, g: DialGeometry, phi: number) {
  'worklet';
  const { cx, cy, wheelR, ringR, holeR, hubR } = g;
  const metal = wheelPath(g, phi);

  // Its shadow on the plate, seen around the rim and inside the holes.
  canvas.save();
  canvas.clipPath(Skia.PathBuilder.Make().addCircle(cx, cy, g.plateR).build(), ClipOp.Intersect, true);
  canvas.save();
  canvas.translate(2.5, 4.5);
  canvas.drawPath(metal, soft('rgba(48,30,10,0.42)', 4.5));
  canvas.restore();
  canvas.restore();

  const body = Skia.Paint();
  body.setAntiAlias(true);
  body.setShader(chrome(g));
  canvas.drawPath(metal, body);
  canvas.save();
  canvas.clipPath(metal, ClipOp.Intersect, true);
  // Turned-metal rings, and a darkening toward the rim for the disc's curvature.
  const lathe = stroke(0.6);
  for (let k = 0; k < 34; k += 1) {
    const r = hubR + ((wheelR - hubR) * (k + 0.5)) / 34;
    lathe.setColor(color(k % 2 === 0 ? 'rgba(255,255,255,0.10)' : 'rgba(0,0,0,0.07)'));
    canvas.drawCircle(cx, cy, r, lathe);
  }
  const curve = Skia.Paint();
  curve.setShader(
    Skia.Shader.MakeRadialGradient(vec(cx, cy), wheelR, [color('rgba(0,0,0,0)'), color('rgba(0,0,0,0)'), color('rgba(0,0,0,0.22)')], [0, 0.78, 1], TileMode.Clamp),
  );
  canvas.drawCircle(cx, cy, wheelR, curve);
  // Inside each hole the wall shows: dark on the side away from the light, bright on the other.
  const wall = stroke(3.2);
  for (let i = 0; i < HOLES; i += 1) {
    const a = rad(holeRest(i) + phi);
    const x = cx + ringR * Math.cos(a);
    const y = cy + ringR * Math.sin(a);
    wall.setShader(bevel(x, y, holeR, '#3A3E43', '#FFFFFF'));
    canvas.drawCircle(x, y, holeR + 0.6, wall);
  }
  canvas.restore();
  // The rim's bevel, and the hub's retaining ring.
  const edge = stroke(2.2);
  edge.setShader(bevel(cx, cy, wheelR, '#FFFFFF', '#41454A'));
  canvas.drawCircle(cx, cy, wheelR - 1.1, edge);
  const hubRing = stroke(5);
  hubRing.setShader(bevel(cx, cy, hubR, '#50555A', '#FFFFFF'));
  canvas.drawCircle(cx, cy, hubR + 1.5, hubRing);
}

/** The finger stop: a curved chrome hook reaching in over the holes' track, twitched by `kick`. */
export function drawStop(canvas: SkCanvas, g: DialGeometry, kick: number) {
  'worklet';
  const { cx, cy, plateR, ringR, holeR } = g;
  const outer = plateR + 4;
  const inner = ringR - holeR * 0.25;
  const b = Skia.PathBuilder.Make();
  // Drawn along the +x axis, then turned to the stop's angle.
  b.moveTo(outer, -9);
  b.cubicTo(outer - (outer - inner) * 0.45, -8, inner + 10, -6.5, inner + 3, -4);
  b.quadTo(inner - 3, -1.5, inner + 0.5, 3.6);
  b.cubicTo(inner + 12, 6.5, outer - (outer - inner) * 0.4, 9, outer, 10);
  b.close();
  const hook = b.build();
  canvas.save();
  canvas.translate(cx, cy);
  canvas.rotate(STOP_ANGLE + kick * 0.04, 0, 0);
  canvas.save();
  canvas.translate(2, 4);
  canvas.drawPath(hook, soft('rgba(20,12,4,0.45)', 3.5));
  canvas.restore();
  const metal = Skia.Paint();
  metal.setAntiAlias(true);
  metal.setShader(
    Skia.Shader.MakeLinearGradient(vec(0, -9), vec(0, 10), [color('#FDFDFC'), color('#C9CCCF'), color('#7C8187'), color('#B5B8BB')], [0, 0.35, 0.75, 1], TileMode.Clamp),
  );
  canvas.drawPath(hook, metal);
  const line = stroke(0.8);
  line.setColor(color('rgba(30,30,30,0.45)'));
  canvas.drawPath(hook, line);
  // The screw that holds it to the plate.
  canvas.drawCircle(outer - 7, 0.5, 4.2, fill('#6E7378'));
  const head = Skia.Paint();
  head.setAntiAlias(true);
  head.setShader(bevel(outer - 7, 0.5, 4, '#FFFFFF', '#83888D'));
  canvas.drawCircle(outer - 7, 0.5, 3.4, head);
  const slot = stroke(1);
  slot.setColor(color('rgba(40,40,40,0.6)'));
  slot.setStrokeCap(StrokeCap.Round);
  canvas.drawLine(outer - 9.3, -1.4, outer - 4.7, 2.4, slot);
  canvas.restore();
}

/** The number card in the middle, under a clear dome; `press` (0…1) pushes it in. */
export function drawCard(canvas: SkCanvas, g: DialGeometry, fonts: DialFonts, press: number) {
  'worklet';
  const { cx, cy, hubR } = g;
  const r = hubR - 2;
  canvas.save();
  const k = 1 - 0.035 * press;
  canvas.translate(cx, cy);
  canvas.scale(k, k);
  canvas.translate(-cx, -cy);
  const card = Skia.Paint();
  card.setAntiAlias(true);
  card.setShader(
    Skia.Shader.MakeRadialGradient(vec(cx - r * 0.25, cy - r * 0.3), r * 1.4, [color('#FFFBF1'), color(PAPER), color('#DCD0B5')], [0, 0.55, 1], TileMode.Clamp),
  );
  canvas.drawCircle(cx, cy, r, card);
  // The card's printed border and the shadow of the retaining ring on it.
  const border = stroke(1.2);
  border.setColor(color(RED));
  canvas.drawCircle(cx, cy, r - 7, border);
  border.setStrokeWidth(0.6);
  canvas.drawCircle(cx, cy, r - 10, border);
  const ink = fill(INK);
  centredText(canvas, CARD_NAME, cx, cy - r * 0.12, fonts.cardName, fill(RED), 2.2);
  centredText(canvas, CARD_NUMBER, cx, cy + r * 0.34, fonts.cardNumber, ink, 0.6);
  const inner = stroke(6);
  inner.setColor(color('rgba(40,28,10,0.28)'));
  inner.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3, true));
  canvas.drawCircle(cx + 1, cy + 2, r + 1, inner);
  // The clear dome: a broad sheen and a crisp window highlight, dimmer when pressed.
  const sheen = Skia.Paint();
  sheen.setAntiAlias(true);
  sheen.setShader(
    Skia.Shader.MakeLinearGradient(vec(cx - r, cy - r), vec(cx + r * 0.2, cy + r * 0.2), [color('rgba(255,255,255,0.55)'), color('rgba(255,255,255,0)')], null, TileMode.Clamp),
  );
  sheen.setAlphaf(1 - 0.5 * press);
  canvas.save();
  canvas.clipPath(Skia.PathBuilder.Make().addCircle(cx, cy, r).build(), ClipOp.Intersect, true);
  canvas.drawOval(Skia.XYWHRect(cx - r * 1.05, cy - r * 1.02, r * 1.5, r * 0.95), sheen);
  canvas.restore();
  const glint = soft('rgba(255,255,255,0.85)', 1.2);
  glint.setAlphaf(0.85 * (1 - 0.6 * press));
  canvas.save();
  canvas.rotate(-38, cx, cy);
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx - r * 0.42, cy - r * 0.78, r * 0.5, 4.5), 2.25, 2.25), glint);
  canvas.restore();
  canvas.drawCircle(cx, cy, r * 0.94, soft(`rgba(0,0,0,${0.14 * press})`, 8));
  canvas.restore();
}

/**
 * The autoPlay finger, seen from above: a fingertip pressed into the hole, reaching in from the
 * lower right as a right hand's would, with a nail and a soft shadow, fading out toward the hand
 * so it reads as a touch rather than an object.
 */
export function drawFinger(canvas: SkCanvas, g: DialGeometry, angle: number, shown: number) {
  'worklet';
  if (shown <= 0.01) return;
  const a = rad(angle);
  const x = g.cx + g.ringR * Math.cos(a);
  const y = g.cy + g.ringR * Math.sin(a);
  const r = g.holeR * 0.82;
  const length = r * FINGER_LENGTH;
  canvas.save();
  canvas.translate(x, y);
  canvas.rotate(FINGER_FROM, 0, 0);
  canvas.saveLayer();
  // Along +x from the tip (at the origin) back toward the hand, widening a little.
  const b = Skia.PathBuilder.Make();
  b.moveTo(0, -r);
  b.cubicTo(length * 0.4, -r * 1.02, length * 0.7, -r * 1.12, length, -r * 1.2);
  b.lineTo(length, r * 1.2);
  b.cubicTo(length * 0.7, r * 1.12, length * 0.4, r * 1.02, 0, r);
  b.arcToRotated(r, r, 0, false, false, 0, -r);
  b.close();
  const finger = b.build();
  canvas.save();
  canvas.translate(3, 5);
  canvas.drawPath(finger, soft('rgba(30,15,5,0.34)', 7));
  canvas.restore();
  const skin = Skia.Paint();
  skin.setAntiAlias(true);
  skin.setShader(
    Skia.Shader.MakeLinearGradient(vec(0, -r), vec(0, r), [color('#F0CDB8'), color('#E2B39B'), color('#B57E66')], [0, 0.45, 1], TileMode.Clamp),
  );
  canvas.drawPath(finger, skin);
  const nail = Skia.Paint();
  nail.setAntiAlias(true);
  nail.setShader(
    Skia.Shader.MakeLinearGradient(vec(0, -r * 0.6), vec(0, r * 0.6), [color('#FBE4DA'), color('#E9BFB0'), color('#C9968A')], null, TileMode.Clamp),
  );
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-r * 0.55, -r * 0.56, r * 1.15, r * 1.12), r * 0.5, r * 0.5), nail);
  canvas.drawOval(Skia.XYWHRect(-r * 0.3, -r * 0.42, r * 0.6, r * 0.22), soft('rgba(255,255,255,0.55)', 1.5));
  // Fade toward the hand, and the whole finger in and out with `shown`.
  const fade = Skia.Paint();
  fade.setBlendMode(BlendMode.DstIn);
  fade.setShader(
    Skia.Shader.MakeLinearGradient(vec(r * 1.4, 0), vec(length, 0), [Skia.Color(`rgba(0,0,0,${shown})`), Skia.Color('rgba(0,0,0,0)')], null, TileMode.Clamp),
  );
  canvas.drawRect(Skia.XYWHRect(-r * 2, -r * 3, length + r * 4, r * 6), fade);
  canvas.restore();
  canvas.restore();
}

/** Pulse dots at the strip's right end: one fills for each number that passes the stop. */
export function drawPulses(canvas: SkCanvas, g: DialGeometry, pulses: number, shown: number) {
  'worklet';
  if (shown <= 0.01) return;
  const s = g.strip;
  const y = s.y + s.h * 0.5;
  for (let k = 0; k < 10; k += 1) {
    const x = s.x + s.w - 18 - (9 - k) * 9;
    if (k < pulses) canvas.drawCircle(x, y, 2.6, fill(`rgba(162,58,44,${0.9 * shown})`));
    else {
      const ring = stroke(0.9);
      ring.setColor(color(`rgba(32,29,24,${0.28 * shown})`));
      canvas.drawCircle(x, y, 2.4, ring);
    }
  }
}
