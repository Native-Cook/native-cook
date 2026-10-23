// Scintillation: pin-sharp four-point glints that jump from corner to corner of the cut as the crystal turns.
// Each corner of the table and of the girdle is treated as a tiny mirror; when it lines up with one of the
// studio lights it fires. One additive Skia Atlas draw of the atlas's white spark tile.
import {
  Atlas, FilterMode, MipmapMode, Skia, useColorBuffer, useImage, useRSXformBuffer,
} from '@shopify/react-native-skia';

import { CAMERA, type GemLayerProps } from './gem-contract';
import { useDerivedValue } from 'react-native-reanimated';

import { GEM_CORNERS } from './gem-cut';
import { SPARK_TILE } from './orb-atlas';

const CORNERS = GEM_CORNERS;
const COUNT = CORNERS.length / 6;
/** Half-vectors of the studio's three lights in view space (key upper left, pink right, cyan lower left). */
const LAMPS = [-0.36, 0.47, 0.81, 0.5, 0.12, 0.86, -0.42, -0.44, 0.79];
/** How tight a corner's mirror is: higher fires more briefly. */
const TIGHTNESS = 26;
const SPRITES = Array.from({ length: COUNT }, () =>
  Skia.XYWHRect(SPARK_TILE.x, SPARK_TILE.y, SPARK_TILE.width, SPARK_TILE.height));

/** How brightly corner `i` fires (0..1) and where it is on the canvas. Writes x, y, strength into `out`. */
function glint(
  i: number, yaw: number, pitch: number, roll: number,
  centreX: number, centreY: number, radius: number, gain: number, stretch: number, time: number, out: number[],
): void {
  'worklet';
  const cy = Math.cos(yaw);
  const sy = Math.sin(yaw);
  const cp = Math.cos(pitch);
  const sp = Math.sin(pitch);
  const cr = Math.cos(roll);
  const sr = Math.sin(roll);
  const k = i * 6;
  const x = CORNERS[k];
  const y = CORNERS[k + 1];
  const z = CORNERS[k + 2];
  const nx = CORNERS[k + 3];
  const ny = CORNERS[k + 4];
  const nz = CORNERS[k + 5];
  // Rows of Rx(pitch) · Ry(yaw) · Rz(roll), as in GemLayer.
  const r00 = cy * cr;
  const r01 = -cy * sr;
  const r02 = sy;
  const r10 = cp * sr + sp * sy * cr;
  const r11 = cp * cr - sp * sy * sr;
  const r12 = -sp * cy;
  const r20 = sp * sr - cp * sy * cr;
  const r21 = sp * cr + cp * sy * sr;
  const r22 = cp * cy;
  const vx = r00 * x + r01 * y + r02 * z;
  const vy = r10 * x + r11 * y + r12 * z;
  const vz = r20 * x + r21 * y + r22 * z;
  const mx = r00 * nx + r01 * ny + r02 * nz;
  const my = r10 * nx + r11 * ny + r12 * nz;
  const mz = r20 * nx + r21 * ny + r22 * nz;
  let strength = 0;
  if (mz * (CAMERA - vz) - mx * vx - my * vy > 0.15) {
    for (let lamp = 0; lamp < 3; lamp++) {
      const facing = mx * LAMPS[lamp * 3] + my * LAMPS[lamp * 3 + 1] + mz * LAMPS[lamp * 3 + 2];
      if (facing > 0) strength += Math.pow(facing, TIGHTNESS);
    }
  }
  const projection = CAMERA / Math.max(CAMERA - vz, 0.001);
  out[0] = centreX + radius * vx * projection / Math.sqrt(stretch);
  out[1] = centreY - radius * vy * projection * stretch;
  out[2] = Math.min(1, strength * gain * (0.88 + 0.12 * Math.sin(time * Math.PI / 2 + i * 1.7)));
}

// Ranked thresholds retain three idle, four charged or five burst glints; ties fade together.
export function gemGlintPoints(yaw: number, pitch: number, roll: number, centreX: number,
  centreY: number, radius: number, stretch: number, charge: number, flare: number, time: number): number[] {
  'worklet';
  const points: number[] = [];
  const out = [0, 0, 0];
  let first = 0, second = 0, third = 0, fourth = 0, fifth = 0, sixth = 0;
  for (let i = 0; i < COUNT; i++) {
    glint(i, yaw, pitch, roll, centreX, centreY, radius, 0.95 + 0.65 * charge + 1.2 * flare, stretch, time, out);
    // Exclude the star and its bloom, including when the table turns edge-on in the burst.
    const dx = (out[0] - centreX) * Math.sqrt(stretch) / Math.max(radius, 0.01);
    const dy = (out[1] - centreY) / (Math.max(radius, 0.01) * stretch);
    const clearRadius = 0.54 + 0.26 * flare;
    const clearance = Math.max(0, Math.min(1, (Math.hypot(dx, dy) - clearRadius) / 0.10));
    const strength = out[2] * clearance;
    points.push(out[0], out[1], strength);
    if (strength > first) { sixth = fifth; fifth = fourth; fourth = third; third = second; second = first; first = strength; }
    else if (strength > second) { sixth = fifth; fifth = fourth; fourth = third; third = second; second = strength; }
    else if (strength > third) { sixth = fifth; fifth = fourth; fourth = third; third = strength; }
    else if (strength > fourth) { sixth = fifth; fifth = fourth; fourth = strength; }
    else if (strength > fifth) { sixth = fifth; fifth = strength; }
    else if (strength > sixth) sixth = strength;
  }
  for (let i = 2; i < points.length; i += 3) {
    const idleThreshold = fourth + (fifth - fourth) * charge;
    const threshold = idleThreshold + (sixth - idleThreshold) * flare;
    const ranked = Math.max(0, points[i] - threshold) * 2.8;
    points[i] = Math.min(1, ranked)
      * Math.min(1, 0.48 + 0.35 * charge + 0.52 * flare);
  }
  return points;
}

export function GemGlints({ centreX, centreY, radius, yaw, pitch, roll, stretch, charge, flare, time, opacity }: GemLayerProps) {
  const image = useImage(require('./assets/orb-atlas.png'));
  const points = useDerivedValue(() => gemGlintPoints(yaw.get(), pitch.get(), roll.get(), centreX.get(),
    centreY.get(), radius.get(), stretch.get(), charge.get(), flare.get(), time.get()));
  const transforms = useRSXformBuffer(COUNT, (transform, i) => {
    'worklet';
    const values = points.get();
    const strength = values[i * 3 + 2];
    if (strength < 0.005) { transform.set(0, 0, 0, 0); return; }
    const scale = (radius.get() * (0.14 + (0.34 + 0.10 * flare.get()) * strength)) / 128;
    transform.set(scale, 0, values[i * 3] - scale * 64, values[i * 3 + 1] - scale * 64);
  });
  const colors = useColorBuffer(COUNT, (color, i) => {
    'worklet';
    color[0] = 1; color[1] = 1; color[2] = 1;
    color[3] = points.get()[i * 3 + 2] * opacity.get();
  });

  if (!image) return null;
  return (
    <Atlas
      image={image}
      sprites={SPRITES}
      transforms={transforms}
      colors={colors}
      colorBlendMode="modulate"
      blendMode="plus"
      sampling={{ filter: FilterMode.Linear, mipmap: MipmapMode.None }}
    />
  );
}
