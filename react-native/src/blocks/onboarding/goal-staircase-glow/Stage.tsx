import { PaintStyle, type SkCanvas, type SkParagraph, Skia, StrokeCap, TileMode, vec } from '@shopify/react-native-skia';
import { Platform } from 'react-native';

// The whole scene is one drawing on a stage of 393 × 852 points, scaled to fit the screen. What
// it shows at a moment follows from that moment and a short list of times: when the scene
// appeared, when the stairs were called, when each hop began and when it all began to fade.

/** autoPlay: the stairs at STAIRS_AT, the climb at CLIMB_AT, the fade at RESET_AT, every LOOP seconds. */
export const LOOP = 8.5;
export const STAIRS_AT = 2.5;
export const CLIMB_AT = 4.4;
export const RESET_AT = 7.4;
/** After a touch autoPlay waits this long, then fades out and starts over. */
export const USER_HOLD = 3;
/** A time that has not come. */
export const NONE = 1e9;

// The scene appears: the ball, then the goal, whose ring swells once.
const BALL_IN = 0.4;
const GOAL_AFTER = 0.2;
// A dashed line is drawn from the ball towards the goal, and three question marks pop up.
const LINE_AT = 0.6;
const LINE_FOR = 0.5;
const MARKS_AT = 1.3;
const MARK_GAP = 0.12;
const MARK_POP = 0.25;
const SHRUG_FOR = 0.7;
// They leave by themselves, or as soon as the stairs are called.
const ASK_OUT = 2;
const ASK_FADE = 0.5;
/** The labels arrive one after the other, each sliding up into place. */
export const STAIR_GAP = 0.3;
export const STAIR_IN = 0.3;
const STAIR_RISE = 10;
/** A hop from step to step, and the last one onto the goal. */
export const HOP = 0.32;
export const GOAL_HOP = 0.4;
// The top of a hop is this far above the straight line between its ends.
const ARC = 22;
// A label lights up when the ball lands, and dips like a spring: 3 points down and back.
const LIT = 0.12;
const DIP = 6.34;
const DIP_DECAY = 11;
const DIP_SWING = 19.05;
const SQUASH = 0.08;
/** The goal bursts, glows on, and then everything fades to black and stays dark a moment. */
export const BURST = 0.4;
export const FADE = 0.5;
export const DARK = 0.6;

export const STAGE_WIDTH = 393;
export const STAGE_HEIGHT = 852;
const BALL = 19;
const BALL_AT = [81, 484];
const GOAL_AT = [288, 172];
const GOAL = 11;
const RING = 17;
const GOAL_LABEL = 213;
const FIRST_STEP = [158, 457];
const LAST_STEP = [291, 280];
const LABEL_WIDTH = 78;
const LABEL_HEIGHT = 22;
const LABEL_PAD = 10;
const LABEL_RADIUS = 4;
const LINE_FROM = [112, 455];
const LINE_TO = [210, 320];
// The question marks: where each is from the ball's centre, and its tilt in degrees.
const MARKS = [
  [-15, -38, -12],
  [1, -49, 4],
  [17, -37, 14],
];
// Everything is mirrored on the floor, which lies this far under the ball's spot: a dim, flattened
// copy straight below it, fainter the higher the thing is. The goal's copy is far down the screen.
const FLOOR = BALL_AT[1] + 52;
const FLOOR_FADE = 300;
const GOAL_MIRROR = 800;
const HALO = 120;
const STREAKS = 14;

const FONT = Platform.select({ ios: '.AppleSystemUIFont', default: 'sans-serif' });

export type Plot = {
  /** When the scene began to appear. */
  born: number;
  /** When the stairs were called. */
  stairs: number;
  /** When each hop began: one per step, and one onto the goal. */
  hops: number[];
  /** When everything began to fade to black. */
  fade: number;
};

export function freshPlot(born: number, steps: number): Plot {
  'worklet';
  const hops: number[] = [];
  for (let i = 0; i <= steps; i++) hops.push(NONE);
  return { born, stairs: NONE, hops, fade: NONE };
}

/** The plot as it stands at `t`: once a fade and the dark after it are over, the scene is new. */
export function plotAt(plot: Plot, t: number): Plot {
  'worklet';
  if (plot.fade < NONE && t >= plot.fade + FADE + DARK) return freshPlot(plot.fade + FADE + DARK, plot.hops.length - 1);
  return plot;
}

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function smooth(t: number, from: number, to: number) {
  'worklet';
  const u = clamp01((t - from) / (to - from));
  return u * u * (3 - 2 * u);
}

function easeOut(t: number) {
  'worklet';
  const u = 1 - clamp01(t);
  return 1 - u * u * u;
}

/** A number from 0 to 1 that depends on `n` alone, the same on every platform. */
function hash(n: number) {
  'worklet';
  let h = Math.imul(n ^ 0x9e3779b9, 0x85ebca6b);
  h ^= h >>> 13;
  h = Math.imul(h, 0xc2b2ae35);
  h ^= h >>> 16;
  return (h >>> 0) / 4294967296;
}

/** The centre of step `index` (0 is the lowest) of `count`. */
function stepAt(index: number, count: number) {
  'worklet';
  const u = count > 1 ? index / (count - 1) : 0;
  return [FIRST_STEP[0] + (LAST_STEP[0] - FIRST_STEP[0]) * u, FIRST_STEP[1] + (LAST_STEP[1] - FIRST_STEP[1]) * u];
}

function dipAt(since: number) {
  'worklet';
  return since <= 0 ? 0 : DIP * Math.exp(-DIP_DECAY * since) * Math.sin(DIP_SWING * since);
}

// Soft discs of one colour that fade from the centre to nothing (cos², so there is no rim), in a
// unit circle: lights, glows and the copies on the floor are these, stretched.
const FALLOFF_AT = [0, 0.2, 0.4, 0.6, 0.8, 1];
const FALLOFF = [1, 0.905, 0.655, 0.345, 0.095, 0];

function soft(red: number, green: number, blue: number) {
  return Skia.Shader.MakeRadialGradient(
    vec(0, 0),
    1,
    FALLOFF.map((f) => Float32Array.of(red / 255, green / 255, blue / 255, f)),
    FALLOFF_AT,
    TileMode.Clamp,
  );
}

const HALO_LIGHT = soft(0x24, 0x1f, 0x1e);
const BALL_LIGHT = soft(0xff, 0xe2, 0xa8);
const GOLD_LIGHT = soft(0xfe, 0xd0, 0x1b);
const WHITE_LIGHT = soft(0xff, 0xff, 0xff);
const BALL_FACE = Skia.Shader.MakeLinearGradient(
  vec(-BALL * 0.7, -BALL * 0.7),
  vec(BALL * 0.7, BALL * 0.7),
  [Skia.Color('#FFF4DC'), Skia.Color('#FFE2A8')],
  null,
  TileMode.Clamp,
);
const GOLD = Skia.Color('#FED01B');
const WHITE = Skia.Color('#FFFFFF');
const DASHES = Skia.PathEffect.MakeDash([4, 4], 0);
// A question mark 13 points high, drawn round its middle.
const MARK = Skia.PathBuilder.Make()
  .addArc({ x: -3.3, y: -6.3, width: 6.6, height: 6.6 }, 180, 235)
  .lineTo(0.2, 1)
  .lineTo(0, 2.6)
  .build();

export type Words = { dim: SkParagraph; lit: SkParagraph; width: number };

function paragraph(text: string, color: string, size: number, weight: number, spacing: number) {
  const builder = Skia.ParagraphBuilder.Make({ maxLines: 1 });
  builder.pushStyle({ color: Skia.Color(color), fontFamilies: [FONT], fontSize: size, fontStyle: { weight }, letterSpacing: spacing, heightMultiplier: 1.4, halfLeading: true });
  builder.addText(text);
  const built = builder.build();
  built.layout(STAGE_WIDTH);
  return built;
}

/** A word in its two looks, laid out once. */
export function words(text: string, dim: string, lit: string, size: number, weight: number, spacing: number): Words {
  const first = paragraph(text, dim, size, weight, spacing);
  return { dim: first, lit: paragraph(text, lit, size, weight, spacing), width: first.getLongestLine() };
}

function light(canvas: SkCanvas, shader: typeof HALO_LIGHT, x: number, y: number, rx: number, ry: number, alpha: number) {
  'worklet';
  if (alpha <= 0) return;
  const paint = Skia.Paint();
  paint.setShader(shader);
  paint.setAlphaf(Math.min(1, alpha));
  canvas.save();
  canvas.translate(x, y);
  canvas.scale(rx, ry);
  canvas.drawCircle(0, 0, 1, paint);
  canvas.restore();
}

/** Draws a word centred on (x, y). */
function write(canvas: SkCanvas, word: SkParagraph, width: number, x: number, y: number, alpha: number) {
  'worklet';
  if (alpha <= 0) return;
  const left = x - width / 2;
  const top = y - word.getHeight() / 2;
  if (alpha < 1) {
    const layer = Skia.Paint();
    layer.setAlphaf(alpha);
    canvas.saveLayer(layer, Skia.XYWHRect(left - 2, top - 2, width + 4, word.getHeight() + 4));
    word.paint(canvas, left, top);
    canvas.restore();
  } else {
    word.paint(canvas, left, top);
  }
}

/**
 * The scene at `t` seconds as a picture `width` × `height`, with the stage scaled by `scale` in
 * its middle. `still` is reduced motion: the ball does not arc or squash and nothing wobbles.
 */
export function stagePicture(width: number, height: number, scale: number, t: number, given: Plot, labels: Words[], goal: Words, still: boolean) {
  'worklet';
  const recorder = Skia.PictureRecorder();
  const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
  const plot = plotAt(given, t);
  // For the frame in which the list of steps has changed and the plot has not yet.
  const count = Math.min(labels.length, plot.hops.length - 1);
  const life = t - plot.born;
  const out = plot.fade < NONE ? 1 - smooth(t, plot.fade, plot.fade + FADE) : 1;
  if (life < 0 || out <= 0) return recorder.finishRecordingAsPicture();
  canvas.save();
  canvas.translate((width - STAGE_WIDTH * scale) / 2, (height - STAGE_HEIGHT * scale) / 2);
  canvas.scale(scale, scale);

  const ballIn = smooth(life, 0, BALL_IN) * out;
  const goalIn = smooth(life, GOAL_AFTER, GOAL_AFTER + BALL_IN) * out;
  const burstAt = plot.hops[count] + GOAL_HOP;
  const burst = t - burstAt;
  const glowing = smooth(burst, 0, 0.15);

  // Where the ball is: on its spot, on a step, or on the arc of a hop.
  let x = BALL_AT[0];
  let y = BALL_AT[1];
  let landed = -NONE;
  for (let k = 0; k <= count; k++) {
    const from = plot.hops[k];
    if (t < from) break;
    const to = k < count ? stepAt(k, count) : GOAL_AT;
    const toX = to[0];
    const toY = k < count ? to[1] - LABEL_HEIGHT / 2 - BALL : to[1];
    const u = (t - from) / (k < count ? HOP : GOAL_HOP);
    if (u >= 1 || still) {
      x = toX;
      y = toY + (k < count ? dipAt(t - from - HOP) : 0);
      landed = from + (k < count ? HOP : GOAL_HOP);
    } else {
      // One curve through a point above the middle, at a steady pace: up fast, over slowly, down fast.
      const midX = (x + toX) / 2;
      const midY = (y + toY) / 2 - ARC * 2;
      x = (1 - u) * (1 - u) * x + 2 * u * (1 - u) * midX + u * u * toX;
      y = (1 - u) * (1 - u) * y + 2 * u * (1 - u) * midY + u * u * toY;
      landed = -NONE;
    }
  }
  const since = t - landed;
  // It flattens for a moment where it lands, shrugs at its question, and gathers itself before the climb.
  let squash = since >= 0 && since < 0.2 ? SQUASH * (1 - smooth(since, 0.06, 0.2)) : 0;
  const shrug = Math.sin(Math.PI * clamp01((life - MARKS_AT) / SHRUG_FOR));
  if (!still) squash += 0.06 * shrug * shrug;
  const ballAlpha = ballIn * (1 - smooth(burst, 0, LIT));

  light(canvas, HALO_LIGHT, x, y, HALO, HALO, ballAlpha);
  light(canvas, HALO_LIGHT, GOAL_AT[0], GOAL_AT[1], HALO * 1.1, HALO * 1.1, goalIn);
  light(canvas, BALL_LIGHT, x, FLOOR, BALL * 1.5, BALL * 0.9, 0.25 * ballAlpha * (1 - 0.6 * clamp01((BALL_AT[1] - y) / FLOOR_FADE)));
  light(canvas, GOLD_LIGHT, GOAL_AT[0], GOAL_MIRROR, 30, 12, 0.25 * goalIn * (1 + glowing));

  const pen = Skia.Paint();
  pen.setStyle(PaintStyle.Stroke);
  pen.setStrokeCap(StrokeCap.Round);
  const fill = Skia.Paint();

  // The question: a dashed line towards the goal, and three marks over the ball.
  const leave = Math.min(plot.born + ASK_OUT, plot.stairs);
  const asking = (1 - smooth(t, leave, leave + ASK_FADE)) * out;
  const drawn = clamp01((life - LINE_AT) / LINE_FOR) * (1 - smooth(t, leave, leave + ASK_FADE));
  if (drawn > 0 && asking > 0) {
    const line = Skia.Paint();
    line.setStyle(PaintStyle.Stroke);
    line.setStrokeWidth(1);
    line.setColor(WHITE);
    line.setAlphaf(0.55 * asking);
    line.setPathEffect(DASHES);
    canvas.drawLine(LINE_FROM[0], LINE_FROM[1], LINE_FROM[0] + (LINE_TO[0] - LINE_FROM[0]) * drawn, LINE_FROM[1] + (LINE_TO[1] - LINE_FROM[1]) * drawn, line);
  }

  // The stairs, from the lowest label up.
  for (let i = 0; i < count; i++) {
    const at = stepAt(i, count);
    const arrived = still ? smooth(t - plot.stairs, 0, STAIR_IN) : easeOut((t - plot.stairs - i * STAIR_GAP) / STAIR_IN);
    if (arrived <= 0) continue;
    const landing = plot.hops[i] + HOP;
    const lit = smooth(t, landing, landing + LIT);
    const shown = arrived * out;
    const w = Math.max(LABEL_WIDTH, labels[i].width + LABEL_PAD * 2);
    const cy = at[1] + (still ? 0 : STAIR_RISE * (1 - arrived) + dipAt(t - landing));
    light(canvas, WHITE_LIGHT, at[0], FLOOR + 4, w * 0.6, 7, shown * (0.06 + 0.2 * lit));
    const box = Skia.RRectXY(Skia.XYWHRect(at[0] - w / 2, cy - LABEL_HEIGHT / 2, w, LABEL_HEIGHT), LABEL_RADIUS, LABEL_RADIUS);
    if (lit > 0) {
      // A soft glow: three wider, fainter copies.
      fill.setColor(WHITE);
      for (let g = 3; g >= 1; g--) {
        fill.setAlphaf(shown * lit * (0.13 - g * 0.03));
        const d = g * 3;
        canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(at[0] - w / 2 - d, cy - LABEL_HEIGHT / 2 - d, w + d * 2, LABEL_HEIGHT + d * 2), LABEL_RADIUS + d, LABEL_RADIUS + d), fill);
      }
      fill.setAlphaf(shown * lit);
      canvas.drawRRect(box, fill);
    }
    pen.setStrokeWidth(1);
    pen.setColor(WHITE);
    pen.setAlphaf(0.35 * shown * (1 - lit));
    canvas.drawRRect(box, pen);
    write(canvas, lit > 0.5 ? labels[i].lit : labels[i].dim, labels[i].width, at[0], cy, shown);
  }

  // The goal: a gold disc in a thin ring, its glow, and its name.
  const swell = still ? 1 : 1 + 0.3 * Math.sin(Math.PI * clamp01((life - GOAL_AFTER) / BALL_IN));
  light(canvas, GOLD_LIGHT, GOAL_AT[0], GOAL_AT[1], (GOAL + 40) * (1 + 0.3 * glowing), (GOAL + 40) * (1 + 0.3 * glowing), 0.35 * goalIn * (1 + glowing));
  pen.setStrokeWidth(1);
  pen.setColor(WHITE);
  pen.setAlphaf(0.6 * goalIn);
  canvas.drawCircle(GOAL_AT[0], GOAL_AT[1], RING * swell, pen);
  fill.setColor(GOLD);
  fill.setAlphaf(goalIn);
  canvas.drawCircle(GOAL_AT[0], GOAL_AT[1], GOAL, fill);
  write(canvas, glowing > 0.5 ? goal.lit : goal.dim, goal.width, GOAL_AT[0], GOAL_LABEL, goalIn);
  if (burst > 0 && burst < BURST && !still) {
    // Short streaks fly out of it and fade.
    const p = burst / BURST;
    pen.setStrokeWidth(3);
    pen.setColor(GOLD);
    pen.setAlphaf((1 - p) * out);
    for (let i = 0; i < STREAKS; i++) {
      const angle = ((i + hash(i * 2 + 1) * 0.6) / STREAKS) * 2 * Math.PI;
      const reach = RING + (30 + 20 * hash(i * 2 + 2)) * easeOut(p);
      const cos = Math.cos(angle);
      const sin = Math.sin(angle);
      canvas.drawLine(GOAL_AT[0] + cos * reach, GOAL_AT[1] + sin * reach, GOAL_AT[0] + cos * (reach + 12 * (1 - p)), GOAL_AT[1] + sin * (reach + 12 * (1 - p)), pen);
    }
  }

  // The ball, with its glow.
  if (ballAlpha > 0) {
    light(canvas, BALL_LIGHT, x, y, BALL + 30, BALL + 30, 0.35 * ballAlpha);
    canvas.save();
    canvas.translate(x, y + BALL * squash);
    canvas.scale(1 + squash * 0.5, 1 - squash);
    const face = Skia.Paint();
    face.setShader(BALL_FACE);
    face.setAlphaf(ballAlpha);
    canvas.drawCircle(0, 0, BALL, face);
    canvas.restore();
  }
  if (asking > 0) {
    for (let i = 0; i < MARKS.length; i++) {
      const u = clamp01((life - MARKS_AT - i * MARK_GAP) / MARK_POP) - 1;
      if (u <= -1) continue;
      // Each pops up past its size and settles, then sways.
      const pop = still ? 1 : 1 + 2.7 * u * u * u + 1.7 * u * u;
      const sway = still ? 0 : 8 * Math.sin(life * 6 + i * 2);
      canvas.save();
      canvas.translate(BALL_AT[0] + MARKS[i][0], BALL_AT[1] + MARKS[i][1]);
      canvas.rotate(MARKS[i][2] + sway, 0, 0);
      canvas.scale(pop, pop);
      pen.setStrokeWidth(2);
      pen.setColor(WHITE);
      pen.setAlphaf(asking * (still ? smooth(u, -1, 0) : 1));
      canvas.drawPath(MARK, pen);
      fill.setColor(WHITE);
      fill.setAlphaf(asking * (still ? smooth(u, -1, 0) : 1));
      canvas.drawCircle(0, 5.4, 1.3, fill);
      canvas.restore();
    }
  }
  canvas.restore();
  return recorder.finishRecordingAsPicture();
}
