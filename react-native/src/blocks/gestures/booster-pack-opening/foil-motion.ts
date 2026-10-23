import {
  FOIL_KEYFRAMES,
  clamp,
  interpolateKeyframes,
  type Point,
  type Rect,
} from './model';

const TIMES = FOIL_KEYFRAMES.map(({ ms }) => ms);
const X = FOIL_KEYFRAMES.map(({ x }) => x);
const Y = FOIL_KEYFRAMES.map(({ y }) => y);
const SCALE_X = FOIL_KEYFRAMES.map(({ scaleX }) => scaleX);
const SCALE_Y = FOIL_KEYFRAMES.map(({ scaleY }) => scaleY);
const ROTATION = FOIL_KEYFRAMES.map(({ rotation }) => rotation);

/** Deform the actual cut mesh, so a live swipe keeps its own torn edge. */
export function deformFlyingFoil(
  base: readonly Point[],
  pack: Rect,
  width: number,
  height: number,
  time: number,
): Point[] {
  'worklet';
  if (base.length === 0) return [];

  const targetX = interpolateKeyframes(time, TIMES, X) * width;
  const targetY = interpolateKeyframes(time, TIMES, Y) * height;
  const scaleX = interpolateKeyframes(time, TIMES, SCALE_X);
  const scaleY = interpolateKeyframes(time, TIMES, SCALE_Y);
  const rotation = interpolateKeyframes(time, TIMES, ROTATION) * Math.PI / 180;
  const cosine = Math.cos(rotation);
  const sine = Math.sin(rotation);
  const originX = width * 0.5;
  const originY = height * 0.522;
  const flight = clamp((time - 58) / 575, 0, 1);
  const curlStrength = clamp((flight - 0.18) / 0.55, 0, 1);
  const rollProgress = clamp((time - 433.333) / 200, 0, 1);
  const lateRoll = rollProgress * rollProgress * (3 - 2 * rollProgress);
  const edgeOnPose = interpolateKeyframes(time,
    [433.333, 633.333, 733.333, 1000], [0, 1, 0, 0],
  );

  // At f133 the leading top corner is 17 px farther right than an affine
  // ribbon; by f150 it is 25 px farther right. The torn lower corner stays put.
  const leadingShear = width * interpolateKeyframes(time,
    [0, 66.667, 116.667, 150, 333.333, 433.333, 533.333, 633.333, 1000],
    [0, 0, 0.045, 0.045, 0.06, 0.07, 0.04, 0, 0],
  );
  const leadingLift = height * interpolateKeyframes(time,
    [0, 150, 333.333, 433.333, 533.333, 633.333, 1000],
    [0, 0, 0.007, 0.012, 0.008, 0, 0],
  );

  return base.map((point, index) => {
    const u = clamp((point.x - pack.x) / pack.width, 0, 1);
    const isBottom = index % 2 === 1;
    const column = Math.floor(index / 2);
    const top = base[column * 2];
    const bottom = base[column * 2 + 1];
    const centerY = (top.y + bottom.y) * 0.5;
    const halfHeight = Math.max((bottom.y - top.y) * 0.5, 0);
    const edgeSign = isBottom ? 1 : -1;
    const wave = Math.sin(u * Math.PI) * height * 0.013 * flight;
    const curlPhase = Math.PI * (u - 0.04);
    const earlyCurl = 1 + curlStrength * (Math.cos(curlPhase) - 1);
    // f162 has a thin leading edge and a broad trailing face. A cosine across
    // the full width puts the broad face in the middle and leaves a hooked tip.
    const trailingCurl =
      -(0.035 + 0.965 * Math.sin(Math.PI * 0.5 * u * u)) *
      (1 + edgeOnPose * 0.3);
    const curlScale = earlyCurl + (trailingCurl - earlyCurl) * lateRoll;
    const lateTailFan =
      1 + clamp((time - 650) / 150, 0, 1) * Math.pow(u, 8) * 1.55;
    const ribbonWave =
      Math.sin(u * Math.PI * 2 + flight * Math.PI * 0.35) *
      height * 0.0055 * curlStrength;
    const crossShear =
      edgeSign * halfHeight * scaleY * Math.sin(curlPhase) *
      curlStrength * 0.28 * (1 - lateRoll);
    const twist =
      (u - 0.5) * height * 0.018 * flight *
      (isBottom ? 1 : -0.25) * (1 - lateRoll);
    const leadingInfluence = Math.pow(1 - u, 2);
    const localX =
      (point.x - originX) * scaleX + crossShear +
      (isBottom ? 0 : leadingShear * leadingInfluence);
    const localY =
      (centerY - originY) * scaleY +
      edgeSign * halfHeight * scaleY * curlScale * lateTailFan +
      wave + ribbonWave + twist;
    const leadingRollU = clamp(u / 0.6, 0, 1);
    const leadingRollTilt =
      1 - leadingRollU * leadingRollU * (3 - 2 * leadingRollU);
    // The nearly edge-on f162 pose slopes down from the raised leading tip;
    // lifting this section separately retains the lower sag of the broad tail.
    const edgeOnLift =
      height * edgeOnPose * (0.0023 + 0.0082 * leadingRollTilt);
    return {
      x: targetX + localX * cosine - localY * sine,
      y: targetY + localX * sine + localY * cosine -
        leadingLift * leadingInfluence - edgeOnLift,
    };
  });
}
