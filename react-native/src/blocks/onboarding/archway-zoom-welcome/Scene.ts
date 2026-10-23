import { FillType, type SkCanvas, Skia, TileMode } from '@shopify/react-native-skia';

// The scene is a camera travelling forwards for ever: through an archway to the bar chart behind
// it, on through the bars, to the next archway. Everything in it is drawn from one number, the
// seconds into a round, and each archway with its chart is a "generation" that is drawn twice:
// once on its way out (at `tau`) and once on its way in (at `tau - LOOP`). The one coming in
// stands, at the end of the round, exactly where the one going out stood at its start.

/** One round of the camera, in seconds. */
export const LOOP = 10.7;

// The times of a generation, in seconds from the moment it stands at rest.
// It comes into view small and far away, ARRIVE before, and grows to its place over GROW.
const ARRIVE = 3.6;
const ARRIVE_FADE = 0.7;
const GROW = 2.9;
const ARRIVE_SCALE = 0.4;
// The push in: the arch grows to ARCH_ZOOM times its size over PUSH, speeding up, and fades as its
// opening passes the edges of the screen.
const PUSH_AT = 0.7;
const PUSH = 1.4;
const ARCH_ZOOM = 4;
const ARCH_OUT_AT = 1.3;
const ARCH_OUT = 0.7;
// The chart is further away, so it grows more slowly, to FULL by CREEP_AT; then it creeps on by
// CREEP until LEAVE_AT, and from there the camera pushes through it.
const FULL = 7.5;
const CREEP_AT = 5;
const CREEP = 0.3;
const LEAVE_AT = 7.1;
const LEAVE_PUSH = 0.476;
// The two key bars turn green from the top down, and a marker drops in over each.
const GREEN_IN = 0.3;
const MARKER_OUT = 0.7;
const MARKER_BOB = 4;
const MARKER_BOB_EVERY = 2.1;
// The bars dissolve into mist: their feet first, then all of them; the green ones last.
const MIST = [7.8, 1, 8.3, 1.6];
const GREEN_MIST = [8.2, 1.2, 8.8, 1.7];
// The hills at the horizon sink as the camera sets off.
const HILLS_OUT = 1.4;
const HILLS_DROP = 40;
const HILLS_IN = 1.5;

// Sizes, in points at rest. Everything scales about the point on the horizon in the middle of the
// screen, which is HORIZON above the sheet.
export const HORIZON = 184;
// The arch: its base under the horizon, then its nested outlines, widest first, and its plinth.
const ARCH_BASE = 28;
const ARCH_WIDTHS = [112, 92, 72, 62];
const ARCH_HEIGHTS = [150, 140, 130, 125];
const PLINTH = [130, 8];
// The chart: the width of a key bar, and the ground it stands on under the horizon.
const UNIT = 12.7;
const GROUND = 25.5;
// A bar: its left and right edge and its height, in key-bar widths from the middle, and its colour.
// They are listed from the back to the front.
const LIGHT = 0;
const PALE = 1;
const MID = 2;
const DARK = 3;
const KEY = 4;
const BARS = [
  [0.18, 0.54, 1.4, LIGHT],
  [-0.52, 0, 1.03, PALE],
  [-2.07, -1.65, 0.4, MID],
  [1.65, 2.1, 1.75, DARK],
  [-1.53, -0.53, 1.6, KEY],
  [0.56, 1.56, 2.9, KEY],
  [-0.45, 0.47, 0.55, MID],
];
// The part of a bar's width that is its side face, which is darker by SIDE_SHADE.
const SIDE = 0.1;
const SIDE_SHADE = 0.12;
// A marker over a key bar: its width and height in key-bar widths, and how far over the bar it floats.
const MARKER = [0.3, 0.26, 0.4];
const MARKER_DROP = 0.5;

// Colours as red, green, blue from 0 to 1.
const GREYS = [
  [0.89, 0.89, 0.89],
  [0.851, 0.855, 0.851],
  [0.788, 0.788, 0.788],
  [0.745, 0.749, 0.745],
];
const GREEN = [0.141, 0.769, 0.537];
const GREEN_PALE = [0.698, 0.918, 0.839];
const MARKER_GREEN = [0.231, 0.796, 0.596];
const HILL = [0.925, 0.925, 0.925];
const ARCH_FACES = [
  [0.918, 0.918, 0.918],
  [0.941, 0.941, 0.941],
  [0.769, 0.769, 0.769],
];
const PLINTH_GREY = [0.851, 0.855, 0.851];
// The sky, from the top of the screen to its bottom edge: where each colour sits, then the colours.
const SKY_AT = [0, 0.55, 0.8, 1];
const SKY = [
  [0.996, 0.996, 0.996],
  [0.996, 0.996, 0.996],
  [0.745, 0.745, 0.745],
  [0.643, 0.643, 0.643],
];

function smooth(t: number) {
  'worklet';
  const u = Math.min(1, Math.max(0, t));
  return u * u * (3 - 2 * u);
}

function tint(color: number[], alpha: number) {
  'worklet';
  return Float32Array.of(color[0], color[1], color[2], alpha);
}

function shaded(color: number[]) {
  'worklet';
  return color.map((c) => c * (1 - SIDE_SHADE));
}

function archScale(tau: number) {
  'worklet';
  if (tau < 0) return ARRIVE_SCALE + (1 - ARRIVE_SCALE) * smooth((tau + GROW) / GROW);
  const u = Math.min(1, Math.max(0, (tau - PUSH_AT) / PUSH));
  return 1 + (ARCH_ZOOM - 1) * u * u;
}

function archAlpha(tau: number) {
  'worklet';
  return tau < 0 ? smooth((tau + ARRIVE) / ARRIVE_FADE) : 1 - smooth((tau - ARCH_OUT_AT) / ARCH_OUT);
}

function chartScale(tau: number) {
  'worklet';
  if (tau < 0) return archScale(tau);
  if (tau < CREEP_AT) return 1 + (FULL - 1) * smooth((tau - PUSH_AT) / (CREEP_AT - PUSH_AT));
  const creep = LEAVE_AT - CREEP_AT;
  if (tau < LEAVE_AT) return FULL + CREEP * ((tau - CREEP_AT) / creep) ** 2;
  // It leaves at the speed the creep ended with, and gathers more.
  const on = tau - LEAVE_AT;
  return FULL + CREEP + ((2 * CREEP) / creep) * on + LEAVE_PUSH * on * on;
}

/** An arch's outline: a rectangle with a round top, standing on the arch's base. */
function outline(builder: ReturnType<typeof Skia.PathBuilder.Make>, width: number, height: number) {
  'worklet';
  const half = width / 2;
  const spring = ARCH_BASE - height + half;
  builder.moveTo(-half, ARCH_BASE);
  builder.lineTo(-half, spring);
  builder.arcToOval(Skia.XYWHRect(-half, spring - half, width, width), 180, 180, false);
  builder.lineTo(half, ARCH_BASE);
  builder.close();
}

/** One archway with its chart and its hills, `tau` seconds after it stood at rest (before, if negative). */
function paintGeneration(canvas: SkCanvas, tau: number, middle: number, horizon: number) {
  'worklet';
  if (tau < -ARRIVE) return;
  const fill = Skia.Paint();
  fill.setAntiAlias(true);
  const wash = Skia.Paint();
  wash.setAntiAlias(true);
  const arriving = tau < 0 ? archAlpha(tau) : 1;

  const hills = tau < 0 ? smooth((tau + ARRIVE) / HILLS_IN) : 1 - smooth((tau - PUSH_AT) / HILLS_OUT);
  if (hills > 0) {
    const y = horizon + 8 + (tau < 0 ? 0 : HILLS_DROP * smooth((tau - PUSH_AT) / HILLS_OUT));
    const ridge = Skia.PathBuilder.Make();
    ridge.moveTo(middle - 230, y).quadTo(middle - 90, y - 34, middle + 40, y).close();
    ridge.moveTo(middle - 60, y).quadTo(middle + 100, y - 42, middle + 240, y).close();
    fill.setColor(tint(HILL, hills));
    canvas.drawPath(ridge.build(), fill);
  }

  // The chart, a bar at a time.
  const scale = chartScale(tau);
  const unit = UNIT * scale;
  const ground = horizon + GROUND * scale;
  const green = tau < 0 ? 0 : smooth((tau - PUSH_AT) / GREEN_IN);
  // A face of a bar between `top` and `bottom`, shaded from the bar's `head` down to the ground.
  const bar = (left: number, right: number, top: number, bottom: number, head: number, from: Float32Array, to: Float32Array, slant: number) => {
    wash.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: head }, { x: 0, y: ground }, [from, to], null, TileMode.Clamp));
    if (slant === 0) {
      canvas.drawRect(Skia.XYWHRect(left, top, right - left, bottom - top), wash);
      return;
    }
    // A side face: its top runs down and away.
    const face = Skia.PathBuilder.Make();
    face.moveTo(left, top).lineTo(right, top + slant).lineTo(right, bottom).lineTo(left, bottom).close();
    canvas.drawPath(face.build(), wash);
  };
  for (let index = 0; index < BARS.length; index++) {
    const [from, to, height, kind] = BARS[index];
    const key = kind === KEY;
    const mist = key ? GREEN_MIST : MIST;
    const foot = arriving * (1 - smooth((tau - mist[0]) / mist[1]));
    const head = arriving * (1 - smooth((tau - mist[2]) / mist[3]));
    if (head <= 0) continue;
    const left = middle + from * unit;
    const right = middle + to * unit;
    const top = ground - height * unit;
    const side = (right - left) * SIDE;
    const grey = GREYS[key ? MID : kind];
    // A key bar is green down to `turn`, and still grey below it.
    const turn = key ? top + (ground - top) * green : top;
    if (turn > top) {
      bar(left, right - side, top, turn, top, tint(GREEN, head), tint(GREEN_PALE, foot), 0);
      bar(right - side, right, top, turn, top, tint(shaded(GREEN), head), tint(shaded(GREEN_PALE), foot), side * 0.6);
    }
    if (turn < ground) {
      bar(left, right - side, turn, ground, top, tint(grey, head), tint(grey, foot), 0);
      bar(right - side, right, turn, ground, top, tint(shaded(grey), head), tint(shaded(grey), foot), turn > top ? 0 : side * 0.6);
    }
    if (key && tau > PUSH_AT && tau < LEAVE_AT + MARKER_OUT) {
      // The marker drops into place, bobs, and sinks onto its bar at the end.
      const sink = smooth((tau - LEAVE_AT) / MARKER_OUT);
      const lift = MARKER[2] + MARKER_DROP * (1 - green) - MARKER[2] * sink;
      const bob = MARKER_BOB * Math.sin((tau / MARKER_BOB_EVERY) * 2 * Math.PI + index) * smooth((tau - PUSH_AT - GREEN_IN) / 1) * (1 - sink);
      const x = (left + right - side) / 2;
      const tip = top - lift * unit + bob;
      const marker = Skia.PathBuilder.Make();
      marker
        .moveTo(x - (MARKER[0] * unit) / 2, tip - MARKER[1] * unit)
        .lineTo(x + (MARKER[0] * unit) / 2, tip - MARKER[1] * unit)
        .lineTo(x, tip)
        .close();
      fill.setColor(tint(MARKER_GREEN, green * (1 - sink)));
      canvas.drawPath(marker.build(), fill);
    }
  }

  // The arch in front of it: three faces, each the gap between two outlines, on a plinth.
  const alpha = archAlpha(tau);
  if (alpha <= 0) return;
  canvas.save();
  canvas.translate(middle, horizon);
  const zoom = archScale(tau);
  canvas.scale(zoom, zoom);
  fill.setColor(tint(PLINTH_GREY, alpha));
  canvas.drawRect(Skia.XYWHRect(-PLINTH[0] / 2, ARCH_BASE, PLINTH[0], PLINTH[1]), fill);
  for (let step = 0; step < ARCH_FACES.length; step++) {
    const ring = Skia.PathBuilder.Make();
    ring.setFillType(FillType.EvenOdd);
    outline(ring, ARCH_WIDTHS[step], ARCH_HEIGHTS[step]);
    outline(ring, ARCH_WIDTHS[step + 1], ARCH_HEIGHTS[step + 1]);
    fill.setColor(tint(ARCH_FACES[step], alpha));
    canvas.drawPath(ring.build(), fill);
  }
  canvas.restore();
}

/**
 * Paints the scene on a screen of the given size, `tau` seconds into a round (0 up to LOOP), with
 * the horizon at `horizon` points from the top.
 */
export function paintScene(canvas: SkCanvas, width: number, height: number, horizon: number, tau: number) {
  'worklet';
  const sky = Skia.Paint();
  sky.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: 0, y: 0 },
      { x: 0, y: height },
      SKY.map((color) => tint(color, 1)),
      SKY_AT,
      TileMode.Clamp,
    ),
  );
  canvas.drawRect(Skia.XYWHRect(0, 0, width, height), sky);
  // The one coming in is further away, so it is behind the one going out.
  paintGeneration(canvas, tau - LOOP, width / 2, horizon);
  paintGeneration(canvas, tau, width / 2, horizon);
}
