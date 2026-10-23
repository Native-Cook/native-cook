/** Measured in the supplied 60fps clip; drawing fractions use the 274px track.
 * This is an explanatory replay, not the latency of a live control. */
export const referenceDuration = 10050;
export const referenceGeometry = {
  width: 1000,
  height: 284,
  track: { x: 80, y: 94, width: 523, height: 104 },
  dial: { x: 849, y: 146, radius: 100 },
  brightness: { x: 676, y: 88, radius: 47 },
  volume: { x: 680, y: 198, radius: 47 },
  // Percentage readout box inside the track (right-aligned glyph run).
  readout: { x: 464, width: 110, y: 121, line: 38, size: 30 },
} as const;

type Point = readonly [number, number];
export function sample(points: readonly Point[], time: number) {
  "worklet";
  if (time <= points[0][0]) return points[0][1];
  for (let i = 1; i < points.length; i++) {
    if (time <= points[i][0]) {
      const [a, x] = points[i - 1],
        [b, y] = points[i];
      return x + ((y - x) * (time - a)) / (b - a);
    }
  }
  return points[points.length - 1][1];
}

const levelWidths: readonly Point[] = [
  [0, 0],
  [0.14, 61 / 274],
  [0.52, 163 / 274],
  [0.53, 166 / 274],
  [0.72, 212 / 274],
  [1, 1],
];
export function fillForLevel(level: number) {
  "worklet";
  return sample(levelWidths, Math.max(0, Math.min(1, level)));
}
const positionLevels = levelWidths.map(
  ([level, position]) => [position, level] as const,
);
export function levelForFill(position: number) {
  "worklet";
  return sample(positionLevels, Math.max(0, Math.min(1, position)));
}

const fillPixels: readonly Point[] = [
  [0, 26],
  [200, 52],
  [300, 96],
  [500, 137],
  [750, 156],
  [1000, 163],
  [1983, 163],
  [2083, 142],
  [2200, 82],
  [2350, 48],
  [2450, 40],
  [2550, 43],
  [2650, 63],
  [2800, 101],
  [3000, 126],
  [3250, 144],
  [3500, 153],
  [4000, 161],
  [4383, 166],
  [4667, 191],
  [5000, 206],
  [5500, 211],
  [6000, 212],
  [6583, 211],
  [6683, 204],
  [6833, 175],
  [7000, 120],
  [7250, 88],
  [7500, 72],
  [8000, 62],
  [8500, 61],
  [10050, 61],
];
const lightAlpha: readonly Point[] = [
  [0, 0],
  [200, 0.35],
  [600, 0.75],
  [1100, 1],
  [2200, 1],
  [2450, 0.55],
  [2750, 1],
  [9100, 1],
  [9400, 0.5],
  [9700, 0],
  [10050, 0],
];
const readoutAlpha: readonly Point[] = [
  [0, 0],
  [270, 0],
  [630, 1],
  [2000, 1],
  [2400, 0],
  [2750, 0],
  [3150, 1],
  [4400, 1],
  [4640, 0],
  [4950, 1],
  [6600, 1],
  [7000, 0],
  [7300, 1],
  [9100, 1],
  [9700, 0],
];

// Label luminance sampled from the same source ROI (97th percentile), relative
// to its settled foreground/background. The label fade leads the fill collapse.
const brightnessLabelAlpha: readonly Point[] = [
  [0, 0],
  [100, 0.13],
  [200, 0.34],
  [300, 0.56],
  [500, 0.99],
  [1900, 0.97],
  [2000, 0.72],
  [2050, 0.57],
  [2100, 0.43],
  [2150, 0.29],
  [2200, 0.12],
  [2250, 0.02],
  [2300, 0],
];
const volumeLabelAlpha: readonly Point[] = [
  [2483, 0],
  [2500, 0.04],
  [2550, 0.12],
  [2600, 0.24],
  [2700, 0.46],
  [2800, 0.7],
  [3000, 1],
];

export function referencePose(timeMs: number, reduced = false) {
  "worklet";
  const time = Math.max(0, Math.min(referenceDuration, timeMs));
  const mode = time < 2000 ? 0 : 1;
  const level =
    time < 2000 ? 0.52 : time < 4400 ? 0.53 : time < 6600 ? 0.72 : 0.14;
  const display = time < 2500 ? 52 : time < 4640 ? 53 : time < 7000 ? 72 : 14;
  const mix = sample(
    reduced
      ? [
          [2000, 0],
          [2200, 1],
        ]
      : [
          [2433, 0],
          [2583, 1],
        ],
    time,
  );
  return {
    mode,
    level,
    display: reduced ? Math.round(level * 100) : display,
    fill: reduced ? fillForLevel(level) : sample(fillPixels, time) / 274,
    light: reduced ? (time < 9700 ? 1 : 0) : sample(lightAlpha, time),
    number: reduced ? 1 : sample(readoutAlpha, time),
    brightnessLabel: reduced ? 1 - mode : sample(brightnessLabelAlpha, time),
    volumeLabel: reduced ? mode : sample(volumeLabelAlpha, time),
    mix,
    blur: reduced
      ? 0
      : sample(
          [
            [0, 0],
            [2150, 0],
            [2400, 14],
            [2500, 10],
            [2700, 0],
          ],
          time,
        ),
    phase:
      time < 2000 ? 0 : time < 4400 ? 1 : time < 6600 ? 2 : time < 9100 ? 3 : 4,
  };
}

/** iOS-style resistance past a hard limit: follows the finger at first, then
 * approaches `limit` asymptotically. Sign is preserved; 0 stays 0. */
export function rubberBand(overflow: number, limit: number) {
  "worklet";
  const distance = Math.abs(overflow);
  if (distance === 0 || limit <= 0) return 0;
  const resisted = (1 - 1 / ((distance * 0.55) / limit + 1)) * limit;
  return Math.sign(overflow) * resisted;
}
