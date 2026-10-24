import { flow, type FlowKey } from './motion';
import { SIGNATURE, signatureCubic, signatureTravel } from './BuzzStreaksSignature';

export type ActingKind = 'orange' | 'pink';
export type ActingPoint = { x: number; y: number };
export type ActingPose = { x: number; y: number; rotation: number; sy: number };
type PoseFrame = readonly [time: number, x: number, y: number, degrees: number, sy: number];
const DEGREES = Math.PI / 180;
const REST: ActingPose = { x: 0, y: 0, rotation: 0, sy: 1 };
const ORANGE: readonly PoseFrame[] = [
  [.60, 0, 0, 0, 1], [.68, 0, 0, 0, 1], [.96, -5, 4, -5, .95], [1.10, -5, 4, -5, .95],
  [1.20, 2, 7, 3, .89], [1.34, 0, -4, -2, 1.06], [1.55, 0, 0, 0, 1],
  [2.58, 0, 0, 0, 1], [2.70, -5, -7, -8, 1.05], [2.86, .8, 1.12, 1.28, .992], [2.98, 0, 0, 0, 1],
  [3.04, 0, 0, 0, 1], [3.10, 0, 3, 0, .95], [3.25, 2, -11, 3, 1.04],
  [3.52, 0, 2, -1, .94], [3.74, 0, 0, 0, 1], [3.84, 0, 0, 0, 1],
];
const PINK: readonly PoseFrame[] = [
  [.60, 0, 0, 0, 1], [1.52, 0, 0, 0, 1], [1.64, 7, 5, 8, .94],
  [1.81, -1.26, -.9, -1.44, 1.0108], [1.98, 0, 0, 0, 1], [2.00, 0, 0, 0, 1],
  [2.12, 4, 3, 4, .96], [2.16, 4, 3, 4, .96], [2.26, -3, 6, -4, .91],
  [2.43, 0, -4, 2, 1.05], [2.70, 0, 0, 0, 1], [3.14, 0, 0, 0, 1],
  [3.20, 0, 3, 0, .95], [3.35, -3, -13, -3, 1.04], [3.62, 0, 2, 1, .94], [3.84, 0, 0, 0, 1],
];

function poseTracks(frames: readonly PoseFrame[]) {
  return {
    x: frames.map(frame => [frame[0], frame[1]] as FlowKey),
    y: frames.map(frame => [frame[0], frame[2]] as FlowKey),
    rotation: frames.map(frame => [frame[0], frame[3] * DEGREES] as FlowKey),
    sy: frames.map(frame => [frame[0], frame[4]] as FlowKey),
  };
}

const POSES = { orange: poseTracks(ORANGE), pink: poseTracks(PINK) };
const ATTENTION: readonly FlowKey[] = [[.60, 0], [.82, 1], [3.64, 1], [3.84, 0]];
const OWN_KEY: Record<ActingKind, readonly FlowKey[]> = {
  orange: [[.60, 1], [1.12, 1], [1.24, 0], [3.84, 0]],
  pink: [[.60, 0], [1.52, 0], [1.90, 1], [2.20, 1], [2.30, 0], [3.84, 0]],
};
const SIGNAL: readonly FlowKey[] = [[.60, 0], [1.20, 0], [1.28, 1], [1.52, 1], [1.66, 0],
  [2.26, 0], [2.34, 1], [2.58, 1], [2.74, 0], [3.84, 0]];
const REWARD: readonly FlowKey[] = [[.60, 0], [2.86, 0], [3.06, 1], [3.84, 1]];
const BLINK: readonly FlowKey[] = [[0, 0], [.09, 1], [.12, 1], [.27, 0]];
const HAPPY: readonly FlowKey[] = [[3.18, 0], [3.32, 1], [3.48, 1], [3.64, 0]];

/** One additive actor pose, in stage units and radians; no accumulated animation state. */
export function actingPose(time: number, kind: ActingKind, enabled = true): ActingPose {
  'worklet';
  if (!enabled || time <= .60 || time >= 3.84) return REST;
  const tracks = POSES[kind];
  return { x: flow(time, tracks.x), y: flow(time, tracks.y),
    rotation: flow(time, tracks.rotation), sy: flow(time, tracks.sy) };
}

/** Both pivot handoffs occur inside an exactly neutral pose interval. */
export function actingPivot(time: number, kind: ActingKind, key: number[]): ActingPoint {
  'worklet';
  if (kind === 'orange') return time < 1.58 ? { x: key[0], y: key[1] } : { x: 89.056, y: 318 };
  return time < 2.84 ? { x: key[0], y: key[1] } : { x: 270.75, y: 539.5 };
}

/** Exact T(delta) T(pivot) R S T(-pivot), with the matching inverse for world gaze/anchors. */
export function actingTransformPoint(point: ActingPoint, pose: ActingPose, pivot: ActingPoint, inverse = false): ActingPoint {
  'worklet';
  if (pose.x === 0 && pose.y === 0 && pose.rotation === 0 && pose.sy === 1) return { x: point.x, y: point.y };
  const sx = 1 / Math.pow(pose.sy, .6), c = Math.cos(pose.rotation), s = Math.sin(pose.rotation);
  if (inverse) {
    const x = point.x - pose.x - pivot.x, y = point.y - pose.y - pivot.y;
    return { x: pivot.x + (c * x + s * y) / sx, y: pivot.y + (-s * x + c * y) / pose.sy };
  }
  const x = (point.x - pivot.x) * sx, y = (point.y - pivot.y) * pose.sy;
  return { x: pose.x + pivot.x + c * x - s * y, y: pose.y + pivot.y + s * x + c * y };
}

export function actingBlink(time: number, kind: ActingKind, enabled = true) {
  'worklet';
  return enabled ? flow(time - (kind === 'pink' ? 1.54 : 2.60), BLINK) : 0;
}

export function actingHappy(time: number, kind: ActingKind, enabled = true) {
  'worklet';
  return enabled ? flow(time - (kind === 'pink' ? .10 : 0), HAPPY) : 0;
}

/** Blend attention between key, friend, moving signal and reward without snapping targets. */
export function actingAttentionTarget(time: number, kind: ActingKind, ownKey: ActingPoint, friend: ActingPoint): ActingPoint {
  'worklet';
  const own = flow(time, OWN_KEY[kind]), signal = flow(time, SIGNAL), reward = flow(time, REWARD);
  const other = Math.max(0, 1 - own - signal - reward), total = Math.max(1, own + signal + reward);
  const relay = SIGNATURE.relay[time < 2 ? 0 : 1], progress = signatureTravel(time, relay);
  const signalX = signatureCubic(relay.points, progress, 0), signalY = signatureCubic(relay.points, progress, 1);
  return { x: (own * ownKey.x + other * friend.x + signal * signalX + reward * 180) / total,
    y: (own * ownKey.y + other * friend.y + signal * signalY + reward * 423) / total };
}

/** Additional pupil movement is bounded in source space and remains inside the existing eye clip. */
export function actingGazeOffset(time: number, eye: ActingPoint, target: ActingPoint, pose: ActingPose, pivot: ActingPoint, enabled = true): ActingPoint {
  'worklet';
  if (!enabled || time <= .60 || time >= 3.84) return { x: 0, y: 0 };
  const local = actingTransformPoint(target, pose, pivot, true);
  const x = local.x - eye.x, y = local.y - eye.y, distance = Math.hypot(x, y);
  const gain = distance > 1e-7 ? Math.min(2.6, distance * .025) * flow(time, ATTENTION) / distance : 0;
  return { x: x * gain, y: y * gain };
}
