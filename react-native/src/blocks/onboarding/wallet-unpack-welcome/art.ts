import { BlurStyle, ClipOp, PaintStyle, Skia, TileMode, type SkCanvas, type SkPaint } from '@shopify/react-native-skia';

/**
 * The wallet, drawn in its own box (origin at its top left): a back panel, and a front pocket
 * with a scooped notch and a croc-scale grain; and the two notes that slide out between them.
 */

export const WALLET_W = 210;
export const WALLET_H = 152;
const WALLET_RADIUS = 16;
/** The pocket's top edge, below the wallet's, and the notch scooped out of it. */
export const POCKET_TOP = 24;
const NOTCH_W = 46;
const NOTCH_D = 13;
export const NOTE_W = 170;
export const NOTE_H = 60;
/** The notes' box: wide and tall enough for a note risen above the wallet. */
export const NOTES_W = 320;
export const NOTES_H = 440;

// Colours as 0–1 channels.
const BACK_TOP = [0x34 / 255, 0x41 / 255, 0x2f / 255];
const BACK_BOTTOM = [0x23 / 255, 0x2d / 255, 0x20 / 255];
const GRAIN = [0x2a / 255, 0x35 / 255, 0x26 / 255];
const TONES = [
  [0x3e / 255, 0x4c / 255, 0x39 / 255],
  [0x43 / 255, 0x52 / 255, 0x3e / 255],
  [0x39 / 255, 0x47 / 255, 0x34 / 255],
];
const NOTE_TOP = [0xdc / 255, 0xe6 / 255, 0xda / 255];
const NOTE_BOTTOM = [0xb5 / 255, 0xc6 / 255, 0xb6 / 255];

// The croc grain: rows of these heights; in each, scales 16 to 36 points wide, each nudged up or
// down a little and shaded one of three tones, all from a fixed seed.
const ROW_HEIGHTS = [13, 15, 12, 16, 14, 13, 15, 14, 12];
const SEED = 20261014;

/** Park–Miller: the same numbers in every language, without floating-point drift. */
function sequence(seed: number) {
  let s = seed;
  return () => {
    s = (s * 16807) % 2147483647;
    return s / 2147483647;
  };
}

/** Each scale as x, y, width, height, tone (0 to 2), in the wallet's box. */
function scales() {
  const next = sequence(SEED);
  const out: number[][] = [];
  let y = POCKET_TOP;
  let row = 0;
  while (y < WALLET_H) {
    const h = ROW_HEIGHTS[row % ROW_HEIGHTS.length];
    let x = -next() * 14;
    while (x < WALLET_W) {
      const w = 16 + next() * 20;
      const nudge = (next() - 0.5) * 3;
      out.push([x, y + nudge, w, h - Math.abs(nudge), Math.floor(next() * 3)]);
      x += w;
    }
    y += h;
    row += 1;
  }
  return out;
}

const SCALES = scales();

function color(c: number[], alpha = 1) {
  'worklet';
  return new Float32Array([c[0], c[1], c[2], alpha]);
}

/** The wallet's outline. */
function walletShape() {
  return Skia.PathBuilder.Make()
    .addRRect({ rect: { x: 0, y: 0, width: WALLET_W, height: WALLET_H }, rx: WALLET_RADIUS, ry: WALLET_RADIUS })
    .build();
}

/** The pocket: the wallet's lower part, its top edge scooped in the middle. */
function pocketShape() {
  const mid = WALLET_W / 2;
  const half = NOTCH_W / 2;
  const top = POCKET_TOP;
  const r = WALLET_RADIUS;
  // 0.55 of the half-axes puts the cubic's handles where a quarter ellipse needs them.
  return Skia.PathBuilder.Make()
    .moveTo(0, top)
    .lineTo(mid - half, top)
    .cubicTo(mid - half, top + NOTCH_D * 0.55, mid - half * 0.55, top + NOTCH_D, mid, top + NOTCH_D)
    .cubicTo(mid + half * 0.55, top + NOTCH_D, mid + half, top + NOTCH_D * 0.55, mid + half, top)
    .lineTo(WALLET_W, top)
    .lineTo(WALLET_W, WALLET_H - r)
    .quadTo(WALLET_W, WALLET_H, WALLET_W - r, WALLET_H)
    .lineTo(r, WALLET_H)
    .quadTo(0, WALLET_H, 0, WALLET_H - r)
    .close()
    .build();
}

/** The back panel, with the shade the pocket casts inside it. */
export function drawBack(canvas: SkCanvas) {
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: 0, y: WALLET_H }, [color(BACK_TOP), color(BACK_BOTTOM)], null, TileMode.Clamp));
  canvas.drawPath(walletShape(), paint);
  // A soft light along the top edge.
  paint.setShader(null);
  paint.setStyle(PaintStyle.Stroke);
  paint.setStrokeWidth(1);
  paint.setColor(color([1, 1, 1], 0.1));
  canvas.drawLine(WALLET_RADIUS, 0.8, WALLET_W - WALLET_RADIUS, 0.8, paint);
}

/** The pocket with its grain, shading and lit edge, and the shadow its edge casts above it. */
export function drawPocket(canvas: SkCanvas) {
  const pocket = pocketShape();
  const paint = Skia.Paint();
  paint.setAntiAlias(true);

  // The shadow of the pocket's edge, over whatever is tucked in behind it.
  canvas.save();
  canvas.clipPath(walletShape(), ClipOp.Intersect, true);
  paint.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: 0, y: POCKET_TOP - 7 },
      { x: 0, y: POCKET_TOP + 1 },
      [color([0, 0, 0], 0), color([0, 0, 0], 0.28)],
      null,
      TileMode.Clamp,
    ),
  );
  canvas.drawRect({ x: 0, y: POCKET_TOP - 7, width: WALLET_W, height: 8 + NOTCH_D }, paint);
  canvas.restore();

  canvas.save();
  canvas.clipPath(pocket, ClipOp.Intersect, true);
  paint.setShader(null);
  paint.setColor(color(GRAIN));
  canvas.drawRect({ x: 0, y: 0, width: WALLET_W, height: WALLET_H }, paint);
  for (const [x, y, w, h, tone] of SCALES) {
    paint.setColor(color(TONES[tone]));
    canvas.drawRRect({ rect: { x: x + 1, y: y + 1, width: w - 2, height: h - 2 }, rx: 5, ry: 5 }, paint);
    // Each scale catches a little light along its top.
    paint.setColor(color([1, 1, 1], 0.05));
    canvas.drawRRect({ rect: { x: x + 3, y: y + 1.5, width: w - 6, height: 2 }, rx: 1, ry: 1 }, paint);
  }
  // Light from above, falling off towards the bottom.
  paint.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: 0, y: POCKET_TOP },
      { x: 0, y: WALLET_H },
      [color([1, 1, 1], 0.07), color([0, 0, 0], 0.22)],
      null,
      TileMode.Clamp,
    ),
  );
  canvas.drawRect({ x: 0, y: 0, width: WALLET_W, height: WALLET_H }, paint);
  canvas.restore();

  // The pocket's lit edge.
  paint.setShader(null);
  paint.setStyle(PaintStyle.Stroke);
  paint.setStrokeWidth(1);
  paint.setColor(color([1, 1, 1], 0.16));
  const mid = WALLET_W / 2;
  const half = NOTCH_W / 2;
  const edge = Skia.PathBuilder.Make()
    .moveTo(0, POCKET_TOP + 0.5)
    .lineTo(mid - half, POCKET_TOP + 0.5)
    .cubicTo(mid - half, POCKET_TOP + 0.5 + NOTCH_D * 0.55, mid - half * 0.55, POCKET_TOP + 0.5 + NOTCH_D, mid, POCKET_TOP + 0.5 + NOTCH_D)
    .cubicTo(mid + half * 0.55, POCKET_TOP + 0.5 + NOTCH_D, mid + half, POCKET_TOP + 0.5 + NOTCH_D * 0.55, mid + half, POCKET_TOP + 0.5)
    .lineTo(WALLET_W, POCKET_TOP + 0.5)
    .build();
  canvas.drawPath(edge, paint);
}

/** A note centred at (cx, cy), turned, its long edges rippling, softened by `blur`. */
function drawNote(canvas: SkCanvas, paint: SkPaint, cx: number, cy: number, turn: number, wave: number, phase: number, blur: number) {
  'worklet';
  const half = NOTE_W / 2;
  const builder = Skia.PathBuilder.Make();
  // The top edge left to right, then the bottom edge back, ten points apart.
  for (let x = -half; x <= half; x += 10) {
    const y = -NOTE_H / 2 + wave * Math.sin(((x + half) / 120) * Math.PI * 2 + phase);
    if (x === -half) builder.moveTo(x, y);
    else builder.lineTo(x, y);
  }
  for (let x = half; x >= -half; x -= 10) {
    builder.lineTo(x, NOTE_H / 2 + wave * Math.sin(((x + half) / 120) * Math.PI * 2 + phase + 0.6));
  }
  builder.close();
  const outline = builder.build();

  canvas.save();
  canvas.translate(cx, cy);
  canvas.rotate(turn, 0, 0);
  paint.setStyle(PaintStyle.Fill);
  paint.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, blur, true));
  paint.setShader(
    Skia.Shader.MakeLinearGradient({ x: 0, y: -NOTE_H / 2 }, { x: 0, y: NOTE_H / 2 }, [color(NOTE_TOP), color(NOTE_BOTTOM)], null, TileMode.Clamp),
  );
  canvas.drawPath(outline, paint);
  paint.setShader(null);
  // A paler oval where a note would carry its watermark, and a fine inner border.
  paint.setColor(color([1, 1, 1], 0.35));
  canvas.drawOval({ x: -half + 22, y: -16, width: 40, height: 32 }, paint);
  paint.setStyle(PaintStyle.Stroke);
  paint.setStrokeWidth(1);
  paint.setColor(color([1, 1, 1], 0.4));
  canvas.drawRect({ x: -half + 6, y: -NOTE_H / 2 + 6, width: NOTE_W - 12, height: NOTE_H - 12 }, paint);
  paint.setMaskFilter(null);
  canvas.restore();
}

/** Both notes in the notes' box, whose centre is the wallet's. */
export function drawNotes(
  canvas: SkCanvas,
  note1: { y: number; turn: number; wave: number; blur: number },
  note2: { y: number; turn: number; wave: number; blur: number },
  phase: number,
) {
  'worklet';
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  drawNote(canvas, paint, NOTES_W / 2, NOTES_H / 2 + note1.y, note1.turn, note1.wave, phase, note1.blur);
  drawNote(canvas, paint, NOTES_W / 2, NOTES_H / 2 + note2.y, note2.turn, note2.wave, phase + 1.3, note2.blur);
}
