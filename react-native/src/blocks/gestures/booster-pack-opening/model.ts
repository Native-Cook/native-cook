export type Point = Readonly<{ x: number; y: number }>;

export type Rect = Readonly<{
  x: number;
  y: number;
  width: number;
  height: number;
}>;

export const VIDEO_FPS = 60;
export const FLASH_FRAME = 124;
export const FINAL_REFERENCE_FRAME = 184;
export const POST_CUT_DURATION_MS =
  ((FINAL_REFERENCE_FRAME - FLASH_FRAME) / VIDEO_FPS) * 1000;
// A session continues beyond the reference video through card centering.
// Background music runs independently.
export const OPENING_DURATION_MS = 2000;

// Measurements are normalized to the visible phone viewport in the reference video.
export const REFERENCE_LAYOUT = {
  width: 750,
  height: 1660,
  pack: {
    centerX: 0.5,
    top: 0.472,
    width: 0.737,
    sourceAspect: 938 / 496,
    viewportHeightScale: 0.9834,
    settledBodyWidth: 0.735,
  },
  seamY: 0.572,
  card: {
    centerX: 0.5,
    width: 0.736,
    height: 0.482,
    finalTop: 0.448,
  },
  trace: {
    startX: 0.125,
    startY: 0.568,
    endX: 0.765,
    endY: 0.544,
    touchRadius: 0.067,
  },
} as const;

export const TRACE_PROGRESS_KEYFRAMES: readonly (readonly [number, number])[] = [
  [13, 0],
  [28, 0.06],
  [36, 0.29],
  [42, 0.44],
  [48, 0.52],
  [54, 0.62],
  [60, 0.72],
  [66, 0.81],
  [72, 0.88],
  [78, 0.95],
  [84, 0.98],
  [90, 1],
  [123, 1],
];

// Values measured from the reference video. Time is relative to the frame-124 cut flash.
export const FOIL_KEYFRAMES = [
  { ms: 0, x: 0.5, y: 0.522, scaleX: 1, scaleY: 1, rotation: 0 },
  { ms: 66.667, x: 0.518, y: 0.508, scaleX: 0.98, scaleY: 0.92, rotation: 7.6 },
  { ms: 116.667, x: 0.63, y: 0.46, scaleX: 0.846, scaleY: 0.821, rotation: 23.3 },
  { ms: 233.333, x: 0.697, y: 0.443, scaleX: 0.797, scaleY: 0.686, rotation: 23.1 },
  { ms: 333.333, x: 0.712, y: 0.444, scaleX: 0.8, scaleY: 0.57, rotation: 26.7 },
  { ms: 433.333, x: 0.732, y: 0.463, scaleX: 0.77, scaleY: 0.42, rotation: 25.5 },
  { ms: 533.333, x: 0.752, y: 0.482, scaleX: 0.73, scaleY: 0.36, rotation: 24.5 },
  { ms: 633.333, x: 0.779, y: 0.515, scaleX: 0.67, scaleY: 0.31, rotation: 9.2 },
  { ms: 733.333, x: 0.82, y: 0.61, scaleX: 0.46, scaleY: 0.46, rotation: -5 },
  { ms: 766.667, x: 0.84, y: 0.64, scaleX: 0.44, scaleY: 0.5, rotation: -5 },
  { ms: 833.333, x: 0.84, y: 0.69, scaleX: 0.4, scaleY: 0.52, rotation: -12 },
  { ms: 900, x: 0.765, y: 0.729, scaleX: 0.36, scaleY: 0.82, rotation: -24 },
  { ms: 1000, x: 1.06, y: 1.16, scaleX: 0.18, scaleY: 0.05, rotation: -35 },
] as const;

export const BODY_KEYFRAMES = [
  { ms: 0, seamY: 0.572, scale: 1 },
  { ms: 133.333, seamY: 0.573, scale: 1.002 },
  { ms: 333.333, seamY: 0.575, scale: 1.019 },
  { ms: 533.333, seamY: 0.582, scale: 1.047 },
  { ms: 733.333, seamY: 0.595, scale: 1.081 },
  { ms: 833.333, seamY: 0.642, scale: 1.1 },
  { ms: 900, seamY: 0.728, scale: 1.115 },
  { ms: 933.333, seamY: 0.8, scale: 1.122 },
  { ms: 966.667, seamY: 0.892, scale: 1.136 },
  { ms: 1000, seamY: 1, scale: 1.14 },
] as const;

export const CARD_KEYFRAMES = [
  { ms: 833.333, top: 0.65, opacity: 0 },
  { ms: 850, top: 0.624, opacity: 1 },
  { ms: 866.667, top: 0.603, opacity: 1 },
  { ms: 883.333, top: 0.581, opacity: 1 },
  { ms: 900, top: 0.556, opacity: 1 },
  { ms: 916.667, top: 0.535, opacity: 1 },
  { ms: 933.333, top: 0.514, opacity: 1 },
  { ms: 950, top: 0.494, opacity: 1 },
  { ms: 966.667, top: 0.477, opacity: 1 },
  { ms: 983.333, top: 0.462, opacity: 1 },
  { ms: 1000, top: 0.448, opacity: 1 },
] as const;

export function clamp(value: number, min: number, max: number) {
  'worklet';
  return Math.min(max, Math.max(min, value));
}

export function interpolateKeyframes(
  value: number,
  input: readonly number[],
  output: readonly number[],
) {
  'worklet';
  if (value <= input[0]) return output[0];
  const last = input.length - 1;
  if (value >= input[last]) return output[last];

  for (let index = 1; index < input.length; index += 1) {
    if (value <= input[index]) {
      const segment =
        (value - input[index - 1]) / (input[index] - input[index - 1]);
      return output[index - 1] + segment * (output[index] - output[index - 1]);
    }
  }
  return output[last];
}

const TRACE_FRAMES = TRACE_PROGRESS_KEYFRAMES.map(([keyframe]) => keyframe);
const TRACE_PROGRESS = TRACE_PROGRESS_KEYFRAMES.map(([, progress]) => progress);

/** How far along its stroke the finger of the reference video is at a frame. */
export function traceProgressAtFrame(frame: number) {
  'worklet';
  return interpolateKeyframes(frame, TRACE_FRAMES, TRACE_PROGRESS);
}
