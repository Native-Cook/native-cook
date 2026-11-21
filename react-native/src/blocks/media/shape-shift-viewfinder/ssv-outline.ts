import { useEffect, useMemo } from 'react';
import { type SharedValue, useDerivedValue, useFrameCallback, useSharedValue } from 'react-native-reanimated';
import { Skia, type SkPath } from '@shopify/react-native-skia';
import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { springStep } from './ssv-motion';
import { createShapeBank, type Outline, radiiOf, restWeights } from './ssv-shapes';
import { BAND_SPRINGS, G } from './ssv-theme';
const TAU = Math.PI * 2;

/** The outline's samples in the shape's own coordinates (centre 0, 0), jelly stretch and iris twist applied. */
const outlinePoints = (radii: number[], iris: number, twistDeg: number, es: number, dirX: number, dirY: number, rotDeg: number): [number[], number[]] => {
  'worklet';
  const n = radii.length;
  const twist = (twistDeg * Math.PI) / 180;
  const rot = (rotDeg * Math.PI) / 180;
  const thD = Math.atan2(dirX, -dirY);
  const xs: number[] = new Array(n);
  const ys: number[] = new Array(n);
  for (let j = 0; j < n; j++) {
    const ph = (j / n) * TAU + twist;
    const a = ph + rot - thD;
    const f = 1 + es * Math.cos(2 * a) - 0.5 * es * Math.cos(a);
    const r = radii[j] * G.R * iris * f;
    xs[j] = r * Math.sin(ph);
    ys[j] = -r * Math.cos(ph);
  }
  return [xs, ys];
};

/** How far before the wall the outline starts to give (pt): contact is a soft knee, so the flat meets the curve round. */
const KNEE = 10;
/** The deepest a sample may go past the wall before the outline as a whole is held off it (the throw flattens ~37). */
const PEN_MAX = 40;

/**
 * The outline sample (x, y) against the stage's rounded rect inset by `inset` (stage points): signed distance s (< 0
 * inside) along the outward normal. The sample is eased onto a soft knee — unchanged deeper than KNEE inside, then
 * s' = −KNEE · e^(−(s + KNEE) / KNEE), which never reaches the wall — and written to `out`. Returns s.
 */
const toWall = (x: number, y: number, wall: readonly number[], out: number[]) => {
  'worklet';
  const inset = wall[5];
  const cr = wall[6];
  const mx = wall[3] / 2;
  const my = wall[4] / 2;
  const dx = x - mx;
  const dy = y - my;
  const ax = Math.abs(dx) - (mx - inset - cr);
  const ay = Math.abs(dy) - (my - inset - cr);
  let sd: number;
  let nx = 0;
  let ny = 0;
  if (ax > 0 && ay > 0) {
    const l = Math.hypot(ax, ay);
    sd = l - cr;
    nx = (Math.sign(dx) * ax) / l;
    ny = (Math.sign(dy) * ay) / l;
  } else if (ax > ay) {
    sd = ax - cr;
    nx = Math.sign(dx);
  } else {
    sd = ay - cr;
    ny = Math.sign(dy);
  }
  const soft = sd <= -KNEE ? sd : -KNEE * Math.exp(-(sd + KNEE) / KNEE);
  out[0] = x + nx * (soft - sd);
  out[1] = y + ny * (soft - sd);
  return sd;
};

/**
 * The stage's wall: `wall` = [centre x, centre y, scale, stage width, stage height, inset, corner radius] in stage points.
 * Samples outside the inset rounded rect are projected back onto it, and the area lost returns as a bulge of
 * × (1 + 0.6 · loss / area) on the radii perpendicular to the wall. Works on the samples in place (shape coordinates);
 * returns the number of samples that touched the wall.
 */
const squash = (xs: number[], ys: number[], rotDeg: number, wall: readonly number[]): number => {
  'worklet';
  const n = xs.length;
  const rot = (rotDeg * Math.PI) / 180;
  const ca = Math.cos(rot);
  const sa = Math.sin(rot);
  const cx = wall[0];
  const cy = wall[1];
  const s = wall[2];
  const px: number[] = new Array(n);
  const py: number[] = new Array(n);
  const q = [0, 0];
  let hit = 0;
  let near = 0;
  let pushX = 0;
  let pushY = 0;
  let deepest = -Infinity;
  let dnx = 0;
  let dny = 0;
  for (let j = 0; j < n; j++) {
    px[j] = cx + s * (xs[j] * ca - ys[j] * sa);
    py[j] = cy + s * (xs[j] * sa + ys[j] * ca);
    const sd = toWall(px[j], py[j], wall, q);
    if (sd > deepest) {
      deepest = sd;
      const ex = px[j] - q[0];
      const ey = py[j] - q[1];
      const em = Math.hypot(ex, ey) || 1;
      dnx = ex / em;
      dny = ey / em;
    }
  }
  // however far the centre is pushed past it, the jelly flattens at most PEN_MAX against a wall: the rest of the push
  // slides the whole outline back, so it rests on the wall instead of folding into a sliver
  if (deepest > PEN_MAX) {
    const back = deepest - PEN_MAX;
    for (let j = 0; j < n; j++) {
      px[j] -= dnx * back;
      py[j] -= dny * back;
    }
  }
  let area0 = 0;
  let area1 = 0;
  const ox: number[] = new Array(n);
  const oy: number[] = new Array(n);
  for (let j = 0; j < n; j++) {
    const sd = toWall(px[j], py[j], wall, q);
    if (sd > -KNEE) near++;
    if (sd > 0) {
      hit++;
      pushX += q[0] - px[j];
      pushY += q[1] - py[j];
    }
    ox[j] = q[0];
    oy[j] = q[1];
  }
  if (near === 0) return 0;
  for (let j = 0; j < n; j++) {
    const i = (j + 1) % n;
    area0 += px[j] * py[i] - px[i] * py[j];
    area1 += ox[j] * oy[i] - ox[i] * oy[j];
  }
  area0 = Math.abs(area0) / 2;
  area1 = Math.abs(area1) / 2;
  const loss = area0 - area1;
  const pm = Math.hypot(pushX, pushY);
  if (hit > 0 && loss > 0.5 && pm > 1e-6) {
    const f = 1 + (0.6 * loss) / Math.max(area0, 1);
    const nx = pushX / pm;
    const ny = pushY / pm;
    const tx = -ny;
    const ty = nx;
    for (let j = 0; j < n; j++) {
      const u = (ox[j] - cx) * tx + (oy[j] - cy) * ty;
      const v = (ox[j] - cx) * nx + (oy[j] - cy) * ny;
      toWall(cx + tx * u * f + nx * v, cy + ty * u * f + ny * v, wall, q);
      ox[j] = q[0];
      oy[j] = q[1];
    }
  }
  for (let j = 0; j < n; j++) {
    const dx = ox[j] - cx;
    const dy = oy[j] - cy;
    xs[j] = (dx * ca + dy * sa) / s;
    ys[j] = (-dx * sa + dy * ca) / s;
  }
  return hit;
};

/** Whether the outline touches the stage's wall (and how many samples do): for the haptic and the flood's launch. */
export const wallHits = (radii: number[], iris: number, twistDeg: number, es: number, dirX: number, dirY: number, rotDeg: number, wall: readonly number[]): number => {
  'worklet';
  const [xs, ys] = outlinePoints(radii, iris, twistDeg, es, dirX, dirY, rotDeg);
  const rot = (rotDeg * Math.PI) / 180;
  const ca = Math.cos(rot);
  const sa = Math.sin(rot);
  const q = [0, 0];
  let hit = 0;
  for (let j = 0; j < xs.length; j++) {
    if (toWall(wall[0] + wall[2] * (xs[j] * ca - ys[j] * sa), wall[1] + wall[2] * (xs[j] * sa + ys[j] * ca), wall, q) > 0) hit++;
  }
  return hit;
};

/**
 * The outline as a closed path in the shape's own coordinates (centre at 0, 0, radius R): N radii joined with
 * Catmull-Rom → cubic segments, closed by the iris, deformed by the jelly in the stage's frame, and squashed flat
 * against the stage's wall when `wall` is given.
 */
export const buildOutline = (radii: number[], iris: number, twistDeg: number, es: number, dirX: number, dirY: number, rotDeg: number, wall: readonly number[] | null = null): SkPath => {
  'worklet';
  const b = Skia.PathBuilder.Make();
  if (iris < 0.002) return b.detach();
  const [xs, ys] = outlinePoints(radii, iris, twistDeg, es, dirX, dirY, rotDeg);
  if (wall) squash(xs, ys, rotDeg, wall);
  const n = xs.length;
  b.moveTo(xs[0], ys[0]);
  for (let j = 0; j < n; j++) {
    const i0 = (j - 1 + n) % n;
    const i2 = (j + 1) % n;
    const i3 = (j + 2) % n;
    b.cubicTo(
      xs[j] + (xs[i2] - xs[i0]) / 6,
      ys[j] + (ys[i2] - ys[i0]) / 6,
      xs[i2] - (xs[i3] - xs[j]) / 6,
      ys[i2] - (ys[i3] - ys[j]) / 6,
      xs[i2],
      ys[i2],
    );
  }
  b.close();
  return b.detach();
};

/** Morph arbitrary star-shaped outlines without discarding velocity when the target changes. */
export function useMorphingOutline(shapes: Readonly<Record<string, Outline>>, current: SharedValue<string>): SharedValue<SkPath> {
  const bank = useMemo(() => createShapeBank(shapes), [shapes]);
  const reduced = useReducedMotionPreference();
  const count = bank.count;
  const weightsN = count * 6;
  const weights = useSharedValue([...restWeights(0, count), ...new Array(weightsN).fill(0)]);
  useEffect(() => { weights.set([...restWeights(0, count), ...new Array(weightsN).fill(0)]); }, [bank, count, weightsN, weights]);
  useFrameCallback((frame) => {
    const goal = Math.max(0, bank.names.indexOf(current.get()));
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16.67) / 1000, 0.05);
    weights.modify((w) => {
      'worklet';
      for (let b = 0; b < 6; b++) for (let s = 0; s < count; s++) {
        const i = b * count + s;
        const target = s === goal ? 1 : 0;
        const spring = BAND_SPRINGS[b];
        const next = springStep(w[i] - target, w[weightsN + i], spring.d, spring.k, dt);
        w[i] = reduced ? target : next[0] + target;
        w[weightsN + i] = reduced ? 0 : next[1];
      }
      return w;
    });
  });
  return useDerivedValue(() => buildOutline(radiiOf(weights.get(), bank.bands), 1, 0, 0, 1, 0, 0));
}
