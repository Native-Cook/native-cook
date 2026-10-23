// Pure pose-to-material functions, also loaded by the CanvasKit proof renderer.
import { CARD, PACK, TEAR, type TearPose } from './tear-contract';
import { tearYAt } from './tear-geometry';

/** Quantised phase makes t and t + 13 produce identical float uniforms. */
export function sceneClock(clock: number): number {
  'worklet';
  return ((Math.round(clock * 1000000) % 13000000) + 13000000) % 13000000 / 1000000;
}

export function frameUniforms(pose: TearPose, clock: number) {
  'worklet';
  return { uHeat: pose.frameHeat, uRing: pose.frameRing, uTab: pose.tabGlow, uClock: sceneClock(clock) };
}

export function raysUniforms(pose: TearPose, clock: number) {
  'worklet';
  return { uHeat: pose.frameHeat, uClock: sceneClock(clock), uAge: pose.sparkleAge };
}

export function foilUniforms(pose: TearPose, clock: number) {
  'worklet';
  return {
    uLight: [pose.lightX, pose.lightY], uClock: sceneClock(clock), uCalm: pose.calm,
    uFront: pose.tearFront, uLeak: pose.leak, uMouth: pose.mouth,
    uEdgeY: tearYAt(pose.tearLine, pose.tearFront),
  };
}

export function curlUniforms(pose: TearPose) {
  'worklet';
  const front = Math.max(0, Math.min(PACK.width, pose.tearFront));
  return {
    uRadius: 7 + 9 * Math.sqrt(front / PACK.width),
    uHeight: tearYAt(pose.tearLine, front),
    uFree: pose.stripFree, uRoll: front / 7, uLight: [pose.lightX + Math.sin(pose.stripRot) * pose.stripFree, pose.lightY], uLeak: pose.leak,
  };
}

export function cardUniforms(pose: TearPose) {
  'worklet';
  const yaw = pose.cardFlip + pose.cardTiltY;
  const cy = Math.cos(yaw);
  const sy = Math.sin(yaw);
  const cx = Math.cos(pose.cardTiltX);
  const sx = Math.sin(pose.cardTiltX);
  // Column-major rotation, world x right/y down/z towards camera.
  return {
    uRot: [cy, sy * sx, -sy * cx, 0, cx, sx, sy, -cy * sx, cy * cx],
    uLight: [pose.lightX, pose.lightY], uTilt: [pose.cardTiltX, pose.cardTiltY],
    uSheen: pose.sheen, uLeak: pose.leak, uScale: pose.cardScale,
  };
}

export function lightUniforms(pose: TearPose, clock: number) {
  'worklet';
  return {
    uFront: pose.tearFront, uLeak: pose.leak * pose.packOpacity,
    uBurst: pose.burst * pose.packOpacity, uClock: sceneClock(clock), uEdgeY: tearYAt(pose.tearLine, pose.tearFront * 0.5),
  };
}

export function stageMatrix(pose: TearPose): number[] {
  'worklet';
  const cx = 201;
  const cy = 374;
  const px = Math.sin(pose.swayY) / 900;
  const py = -Math.sin(pose.swayX) / 900;
  const sx = Math.cos(pose.swayY);
  const sy = Math.cos(pose.swayX);
  return [sx + cx * px, cx * py, cx * (1 - sx) - cx * (cx * px + cy * py),
    cy * px, sy + cy * py, cy * (1 - sy) - cy * (cx * px + cy * py),
    px, py, 1 - cx * px - cy * py];
}

export function packMatrix(pose: TearPose): number[] {
  'worklet';
  const c = Math.cos(pose.packRot) * pose.packScale;
  const s = Math.sin(pose.packRot) * pose.packScale;
  return [c, -s, PACK.x + pose.packX + PACK.width / 2 - c * PACK.width / 2 + s * PACK.height / 2,
    s, c, PACK.y + pose.packY + PACK.height / 2 - s * PACK.width / 2 - c * PACK.height / 2, 0, 0, 1];
}

export function curlMatrix(pose: TearPose): number[] {
  'worklet';
  const a = -0.12 + pose.stripRot * pose.stripFree;
  const c = Math.cos(a);
  const s = Math.sin(a);
  const depth = 1 - pose.stripFree + pose.stripFree * (0.55 + 0.45 * Math.abs(Math.cos(pose.stripRot * 0.7)));
  return [c, -s * depth, pose.tearFront + pose.stripX * pose.stripFree,
    s, c * depth, pose.stripY * pose.stripFree, 0, 0, 1];
}

/** Once detached, the scroll falls from the rest seam independently of the sinking pack. */
export function freeCurlMatrix(pose: TearPose): number[] {
  'worklet';
  const local = curlMatrix(pose);
  local[2] += PACK.x;
  local[5] += PACK.y;
  return local;
}

function seedValue(seed: number): number {
  'worklet';
  const v = Math.sin(seed * 127.1 + 311.7) * 43758.5453;
  return v - Math.floor(v);
}

/** Each sprite keeps its seed; only its analytic trajectory changes on the UI thread. */
export function particlePose(pose: TearPose, clock: number, seed: number, burst: boolean) {
  'worklet';
  const a = seedValue(seed + 1);
  const b = seedValue(seed + 23);
  const c = seedValue(seed + 87);
  if (burst) {
    const age = Math.max(0, pose.sparkleAge);
    const travel = (1 - Math.exp(-age * 5.2)) * (175 + b * 135);
    const theta = a * Math.PI * 2;
    return {
      x: pose.cardX + Math.cos(theta) * travel,
      y: pose.cardY + Math.sin(theta) * travel + age * age * 42,
      size: (8 + c * 14) * (0.88 + 0.12 * Math.sin(age * 13 + a * 20)),
      rotation: a * Math.PI + age * (b - 0.5) * 1.2,
      alpha: pose.sparkleAge < 0 ? 0 : Math.max(0, 1 - age / 1.6) * (0.65 + 0.55 * Math.sin(age * 16 + b * 30) ** 2),
    };
  }
  const cycle = sceneClock(clock) / 13;
  const phase = (cycle * (1 + Math.floor(b * 2)) + a) % 1;
  const revealed = pose.frameHeat;
  const pulse = cycle * Math.PI * 8 + b * 50;
  const mouthX = PACK.x + pose.packX + PACK.width / 2;
  // Side shafts keep peeking motes off the card's engraved surface.
  const side = b < 0.5 ? -1 : 1;
  const peekX = mouthX + side * (98 + c * 15 + phase * 21);
  return {
    x: peekX * (1 - revealed) + (pose.cardX + side * (132 + c * 24)) * revealed
      + Math.sin(phase * Math.PI * 2 + c * 20) * 4,
    y: (PACK.y + pose.packY + TEAR.y - 6 - phase * 122) * (1 - revealed)
      + (pose.cardY + CARD.height * 0.7 - phase * 370) * revealed,
    size: 3 + c * 3 + 0.5 + 0.5 * Math.sin(pulse + 1.7),
    rotation: a * Math.PI + Math.sin(cycle * Math.PI * 2 + b * 12) * 0.7,
    alpha: pose.dust * Math.sin(phase * Math.PI) * (0.40 + 0.55 * Math.sin(pulse) ** 2),
  };
}
