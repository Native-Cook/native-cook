import { Skia } from '@shopify/react-native-skia';

// The Loaven knot: two long loops (stadiums) crossed at ±KNOT_TILT, woven over and under at their
// four crossings like a braided loaf. Everything is in mark units around the knot's centre.

export const LOOP_W = 46; // width of one loop, mark units
export const LOOP_H = 136; // length of one loop
export const KNOT_TILT = 30; // degrees each loop leans from upright
export const STROKE = 8; // foil band width
export const GAP = 2.6; // clear space on each side of a strand where it passes over the other
export const OVER_SPAN = 0.06; // share of a loop's length redrawn on top around each crossing

const R = LOOP_W / 2;
const HALF = LOOP_H / 2 - R; // half the straight part
const TILT = (KNOT_TILT * Math.PI) / 180;

/** The knot's size in mark units, stroke included. */
export const KNOT_WIDTH = 2 * (HALF * Math.sin(TILT) + R + STROKE / 2);
export const KNOT_HEIGHT = 2 * (HALF * Math.cos(TILT) + R + STROKE / 2);

/**
 * Where the loops cross, as a share of the length of the loop that passes over there (each loop
 * starts at its top tip and runs clockwise). Along the right-leaning loop the crossings fall at
 * 0.2091, 0.3728, 0.7091 and 0.8728, alternating over and under, so the knot is a true weave;
 * the left-leaning loop meets them at 0.2909, 0.6272, 0.7909 and 0.1272.
 */
export const CROSSINGS: { over: 0 | 1; at: number }[] = [
  { over: 0, at: 0.2091 },
  { over: 1, at: 0.6272 },
  { over: 0, at: 0.7091 },
  { over: 1, at: 0.1272 },
];

/**
 * One loop, upright, then leaned by `sign` × KNOT_TILT: it starts at its top tip and runs
 * clockwise, so a trim of [0, u] matches the shares above.
 */
export function makeLoop(sign: 1 | -1) {
  const b = Skia.PathBuilder.Make();
  b.moveTo(0, -HALF - R);
  b.arcToOval(Skia.XYWHRect(-R, -HALF - R, 2 * R, 2 * R), -90, 90, false);
  b.lineTo(R, HALF);
  b.arcToOval(Skia.XYWHRect(-R, HALF - R, 2 * R, 2 * R), 0, 180, false);
  b.lineTo(-R, -HALF);
  b.arcToOval(Skia.XYWHRect(-R, -HALF - R, 2 * R, 2 * R), 180, 90, false);
  const c = Math.cos(sign * TILT);
  const s = Math.sin(sign * TILT);
  b.transform([c, -s, 0, s, c, 0, 0, 0, 1]);
  return b.detach();
}

/**
 * An arched doorway, unit radius: a half circle over a body that runs 1.5 radii below its centre.
 * Scaled up from the knot's centre, it opens the splash onto the app.
 */
export function makeArch(outline: boolean) {
  const b = Skia.PathBuilder.Make();
  b.moveTo(-1, 1.5);
  b.lineTo(-1, 0);
  b.arcToOval(Skia.XYWHRect(-1, -1, 2, 2), 180, 180, false);
  b.lineTo(1, 1.5);
  if (!outline) b.close();
  return b.detach();
}
