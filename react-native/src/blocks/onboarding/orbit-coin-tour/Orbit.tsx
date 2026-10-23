import { PaintStyle, type SkCanvas, type SkPaint, Skia, StrokeCap, StrokeJoin } from '@shopify/react-native-skia';

// Everything is laid out on a stage of 393 × 852 points that is scaled to fit the screen, so the
// same numbers place the orbit and the cards on every phone. The orbit is drawn in one pass.

/** autoPlay: the orbit, the three scenes in turn, and back, every LOOP seconds. */
export const LOOP = 10.5;
export const OPEN_AT = 2;
export const SECOND_AT = 4.2;
export const THIRD_AT = 6.8;
export const CLOSE_AT = 9;
/** Seconds for the push from the orbit into a scene, and for the pull back out. */
export const PUSH = 1;
/** Seconds for one scene to slide to the next, and for a swipe to settle. */
export const SLIDE = 0.8;
export const SETTLE = 0.35;
/** A scene's contents come in over this long, starting REVEAL_AFTER into the push or the slide. */
export const REVEAL = 0.9;
export const REVEAL_AFTER = 0.5;
/** Reduced motion: the orbit and the scenes cross-fade in this long. */
export const FADE = 0.3;
/** After a touch autoPlay waits this long, then goes back to the orbit and starts over. */
export const USER_HOLD = 3;
/** The orbit's clock starts again at the pull back, so it is this far on when a loop begins. */
export const PHASE_AT_START = LOOP - CLOSE_AT;
/** Rings spun by hand coast for this long, ever slower: the curve starts 6.25 times as steep as it is long. */
export const COAST = 2;
export const COAST_REACH = COAST / 6.25;

export const STAGE_WIDTH = 393;
export const STAGE_HEIGHT = 852;
export const CX = 196.5;
export const CY = 355;

export type Rgb = number[];
export const SAND: Rgb = [237, 228, 217];
export const INK: Rgb = [55, 52, 46];
export const CORAL: Rgb = [250, 119, 107];
const GREY: Rgb = [181, 179, 182];
/** The background of each scene. */
export const GROUNDS: Rgb[] = [SAND, [54, 113, 104], [201, 190, 178]];

const RINGS = [120, 185, 260, 350, 450];
const RING_STROKE = 1.5;
const RING_ALPHA = 0.13;
/** Degrees a second along a ring of this radius: the inner rings are faster. */
function speedOf(ring: number) {
  'worklet';
  return 1300 / ring;
}
const DISC = 184;
// The letter on the disc, a polygon in a 60 × 72 box, drawn 68 points high.
const LETTER = [0, 72, 0, 0, 17, 0, 43, 44, 43, 0, 60, 0, 60, 72, 43, 72, 17, 28, 17, 72];
const LETTER_HEIGHT = 68;

/** The coin of each scene: its icon, the ring it rides and where on it (degrees, at phase 0). */
export const COINS = [
  { icon: 0, ring: 185, angle: -64 },
  { icon: 1, ring: 260, angle: 98 },
  { icon: 2, ring: 260, angle: 238 },
];
export const COIN = 46;
/** Where each scene's coin stops, above its card. */
export const COIN_TOP = [152, 152, 127];
// Small coins that keep flipping, and plain dots.
const MOONS = [
  { ring: 185, angle: 172, turn: 0 },
  { ring: 260, angle: 48, turn: 2 },
];
const MOON = 17;
const MOON_FLIP = 1.6;
const DOTS = [
  { ring: 350, angle: -62, r: 6, color: INK },
  { ring: 185, angle: 30, r: 9, color: INK },
  { ring: 185, angle: 140, r: 9, color: GREY },
];
// A coin seen edge-on is this thick.
const EDGE = 9;
const RIM = 3;

// The push: the rings grow past the camera and turn, and everything on them fades.
const PUSH_GROW = 1.5;
const PUSH_TURN = Math.PI / 2;

/** What the disc becomes in each scene: the block at the top of its card. */
export const HEROES = [
  { x: CX, y: 286, w: 304, h: 260, r: 14, color: [19, 111, 99] },
  { x: CX, y: 345, w: 240, h: 360, r: 16, color: [232, 98, 98] },
  { x: CX, y: 248, w: 304, h: 220, r: 14, color: [54, 59, 66] },
];

export function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

export function smooth(t: number, from: number, to: number) {
  'worklet';
  const u = clamp01((t - from) / (to - from));
  return u * u * (3 - 2 * u);
}

export function ease(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

function mix(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

function tint(a: Rgb, b: Rgb, t: number, alpha: number) {
  'worklet';
  return Float32Array.of(mix(a[0], b[0], t) / 255, mix(a[1], b[1], t) / 255, mix(a[2], b[2], t) / 255, alpha);
}

/** Where coin `index` is on its ring, in stage points. */
export function coinPlace(index: number, phase: number, spin: number) {
  'worklet';
  const coin = COINS[index];
  const angle = ((coin.angle + speedOf(coin.ring) * phase) * Math.PI) / 180 + spin;
  return { x: CX + Math.cos(angle) * coin.ring, y: CY + Math.sin(angle) * coin.ring };
}

const HOUSE_ROOF = Skia.Path.MakeFromSVGString('M6 19L20 8L34 19')!;
const HOUSE_BODY = Skia.Path.MakeFromSVGString('M10 18V32H30V18')!;
const CARD_STRIPE = Skia.Path.MakeFromSVGString('M5 17H35')!;
const CARD_WAVES = Skia.Path.MakeFromSVGString('M24 31A11 11 0 0 1 35 20M29 31A6 6 0 0 1 35 25')!;
const LETTER_PATH = Skia.Path.MakeFromSVGString(`M${LETTER.join(' ')}Z`)!;
export { LETTER_PATH };

/**
 * A coin at (cx, cy): `flip` is the cosine of its turn about its vertical axis, so 1 is face-on,
 * 0 edge-on and below 0 its blank back. `icon` is 0 the house, 1 the card, 2 the rings, -1 none.
 */
export function drawCoin(canvas: SkCanvas, fill: SkPaint, line: SkPaint, cx: number, cy: number, radius: number, flip: number, icon: number, alpha: number) {
  'worklet';
  if (alpha <= 0) return;
  const k = radius / COIN;
  const wide = Math.abs(flip);
  const edge = EDGE * k * Math.sqrt(1 - wide * wide);
  // The back and the band between the two faces, which is what shows when the coin is edge-on.
  fill.setColor(tint(INK, INK, 0, alpha));
  canvas.drawOval(Skia.XYWHRect(cx - edge / 2 - radius * wide, cy - radius, radius * wide * 2, radius * 2), fill);
  canvas.drawRect(Skia.XYWHRect(cx - edge / 2, cy - radius, edge, radius * 2), fill);
  canvas.save();
  canvas.translate(cx + edge / 2, cy);
  canvas.scale(Math.max(wide, 0.001), 1);
  canvas.drawCircle(0, 0, radius, fill);
  fill.setColor(tint(SAND, SAND, 0, alpha));
  canvas.drawCircle(0, 0, radius - RIM * k, fill);
  line.setStrokeWidth(1);
  line.setColor(tint(INK, INK, 0, alpha * 0.3));
  canvas.drawCircle(0, 0, radius * 0.8, line);
  if (flip > 0 && icon >= 0) {
    canvas.translate(-20, -20);
    line.setStrokeWidth(2);
    line.setColor(tint(INK, INK, 0, alpha));
    fill.setColor(tint(CORAL, CORAL, 0, alpha));
    if (icon === 0) {
      canvas.drawPath(HOUSE_ROOF, line);
      canvas.drawPath(HOUSE_BODY, line);
      canvas.drawCircle(20, 23, 2.6, fill);
    } else if (icon === 1) {
      canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(5, 10, 30, 21), 4, 4), line);
      canvas.drawPath(CARD_STRIPE, line);
      canvas.drawPath(CARD_WAVES, line);
      canvas.drawCircle(11.5, 25, 2.6, fill);
    } else {
      canvas.drawCircle(20, 20, 13, line);
      canvas.drawCircle(20, 20, 7, line);
      canvas.drawCircle(20, 20, 2.6, fill);
    }
  }
  canvas.restore();
}

export function coinPaints() {
  'worklet';
  const fill = Skia.Paint();
  const line = Skia.Paint();
  line.setStyle(PaintStyle.Stroke);
  line.setStrokeCap(StrokeCap.Round);
  line.setStrokeJoin(StrokeJoin.Round);
  return { fill, line };
}

/**
 * The orbit as a picture `width` × `height`, with the stage scaled by `scale` in its middle.
 * `tour` is how far the push into scene `scene` (1 to 3) has gone, `phase` the orbit's clock in
 * seconds and `spin` what the hand added, in radians.
 */
export function orbitPicture(width: number, height: number, scale: number, tour: number, scene: number, phase: number, spin: number) {
  'worklet';
  const recorder = Skia.PictureRecorder();
  const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
  const { fill, line } = coinPaints();
  const e = ease(tour);
  const hero = scene - 1;
  const target = HEROES[hero];
  // Everything on the rings is gone before the push ends.
  const away = 1 - smooth(e, 0.3, 0.9);
  const grow = 1 + PUSH_GROW * e;
  const turn = spin + PUSH_TURN * e;

  fill.setColor(tint(SAND, GROUNDS[hero], e, 1));
  canvas.drawRect(Skia.XYWHRect(0, 0, width, height), fill);
  canvas.save();
  canvas.translate((width - STAGE_WIDTH * scale) / 2, (height - STAGE_HEIGHT * scale) / 2);
  canvas.scale(scale, scale);

  // The rings keep their thin line while they grow: the radius is scaled, not the layer.
  line.setStrokeWidth(RING_STROKE);
  line.setColor(tint(INK, INK, 0, RING_ALPHA * away));
  for (let i = 0; i < RINGS.length; i++) canvas.drawCircle(CX, CY, RINGS[i] * grow, line);

  for (let i = 0; i < DOTS.length; i++) {
    const dot = DOTS[i];
    const angle = ((dot.angle + speedOf(dot.ring) * phase) * Math.PI) / 180 + turn;
    fill.setColor(tint(dot.color, dot.color, 0, away));
    canvas.drawCircle(CX + Math.cos(angle) * dot.ring * grow, CY + Math.sin(angle) * dot.ring * grow, dot.r, fill);
  }
  for (let i = 0; i < MOONS.length; i++) {
    const moon = MOONS[i];
    const angle = ((moon.angle + speedOf(moon.ring) * phase) * Math.PI) / 180 + turn;
    const flip = Math.cos(MOON_FLIP * phase + moon.turn);
    drawCoin(canvas, fill, line, CX + Math.cos(angle) * moon.ring * grow, CY + Math.sin(angle) * moon.ring * grow, MOON, flip, -1, away);
  }

  // The disc, on its way to being the top of the scene's card.
  const w = mix(DISC, target.w, e);
  const h = mix(DISC, target.h, e);
  const r = mix(DISC / 2, target.r, e);
  const x = mix(CX, target.x, e);
  const y = mix(CY, target.y, e);
  fill.setColor(tint(CORAL, target.color, e, 1));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x - w / 2, y - h / 2, w, h), r, r), fill);
  const letter = 1 - smooth(e, 0, 0.35);
  if (letter > 0) {
    const k = (LETTER_HEIGHT / 72) * (w / DISC);
    canvas.save();
    canvas.translate(x - 30 * k, y - 36 * k);
    canvas.scale(k, k);
    fill.setColor(tint(SAND, SAND, 0, letter));
    canvas.drawPath(LETTER_PATH, fill);
    canvas.restore();
  }

  for (let i = 0; i < COINS.length; i++) {
    const coin = COINS[i];
    const angle = ((coin.angle + speedOf(coin.ring) * phase) * Math.PI) / 180 + spin;
    if (i === hero) {
      // The scene's coin rides round to the top of the screen, flipping once on the way.
      let arc = -Math.PI / 2 - angle;
      arc -= 2 * Math.PI * Math.round(arc / (2 * Math.PI));
      const at = angle + arc * e;
      const reach = mix(coin.ring, CY - COIN_TOP[i], e);
      drawCoin(canvas, fill, line, CX + Math.cos(at) * reach, CY + Math.sin(at) * reach, COIN, Math.cos(2 * Math.PI * e), coin.icon, 1);
    } else {
      const at = angle + PUSH_TURN * e;
      drawCoin(canvas, fill, line, CX + Math.cos(at) * coin.ring * grow, CY + Math.sin(at) * coin.ring * grow, COIN, 1, coin.icon, away);
    }
  }
  canvas.restore();
  return recorder.finishRecordingAsPicture();
}

/** A scene's coin alone, in a box 2 × (COIN + 8) wide, for the top of its card. */
export function coinPicture(icon: number, flip: number) {
  'worklet';
  const side = (COIN + 8) * 2;
  const recorder = Skia.PictureRecorder();
  const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, side, side));
  const { fill, line } = coinPaints();
  drawCoin(canvas, fill, line, side / 2, side / 2, COIN, flip, icon, 1);
  return recorder.finishRecordingAsPicture();
}
