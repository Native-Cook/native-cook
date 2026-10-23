// The signature autoPlay writes: "Mara Ellison" in a quick cursive hand, an i-dot and an
// underline flourish. Each stroke is a run of key points in a small grid (x 0…102, the letters'
// baseline at y 40.5, the x-height 9 units above it and the loops of the capitals and the l's up
// to y 12), drawn through as a Catmull-Rom curve and timed like a hand: slower in the tight turns,
// faster on the long runs, quickest on the flourish.

type Key = readonly [number, number];

type ScriptStroke = {
  keys: readonly Key[];
  /** Speed on a straight run, in grid units a second. */
  speed: number;
  /** Pause before the pen lands, in seconds. */
  gap: number;
};

const BASELINE = 40.5;
// The hand leans right: x moves this many units per unit of height above the baseline.
const SLANT = 0.22;
// "Ellison", its dot and nothing else start this far right of where their keys say, to open a
// word space after "Mara".
const WORD_SPACE = 4;

const STROKES: readonly ScriptStroke[] = [
  // M, a, r, a in one stroke.
  {
    gap: 0,
    speed: 235,
    keys: [
      [2, 41], [4, 33], [6.5, 20], [8.5, 13.5], [10, 13], [11, 16], [12, 26], [13, 38], [13.5, 40],
      [13.8, 36], [15, 24], [17, 16], [18.5, 15], [19.5, 18], [20.5, 28], [21.5, 38], [22.5, 40.5],
      [24.5, 39], [26.5, 35], [29, 32], [30, 31.5],
      [28, 31], [26.3, 33.5], [26.2, 37.5], [27.8, 40], [30, 39], [31.3, 35.5], [31.8, 31.5],
      [31.6, 36], [32, 39.8], [33.5, 40.3], [35, 38],
      [36.2, 33], [37.2, 31.2], [38.4, 32.6], [39.4, 31.4], [40, 33], [40.2, 38], [41, 40.2], [42.5, 38.5],
      [44.5, 33], [46, 31.6], [44, 31.2], [42.6, 34], [42.8, 38.2], [44.6, 40.2], [46.4, 38.6],
      [47.2, 34.5], [47.6, 31.2], [47.4, 36], [47.9, 39.8], [49.5, 40.4], [51.5, 38],
    ],
  },
  // E, l, l, i, s, o, n in one stroke.
  {
    gap: 0.26,
    speed: 245,
    keys: [
      [62, 17], [60.5, 14.5], [57.5, 14], [55.5, 16.5], [56.5, 21], [59.5, 24],
      [56, 25], [53.5, 28.5], [53.5, 34], [55.5, 39], [59.5, 40.5], [63, 38.5], [65, 35],
      [67, 28], [69, 19], [69.5, 14], [68.5, 12], [67.2, 13.5], [66.8, 19], [67.2, 30], [67.8, 38.5],
      [69, 40.4], [70.8, 39],
      [72.5, 30], [74, 20], [74.3, 14.5], [73.3, 12.6], [72.1, 14.2], [71.8, 20], [72.2, 31], [72.8, 39],
      [74, 40.5], [75.6, 38.4],
      [76.6, 34], [77, 31.5], [77.1, 36], [77.6, 40], [79, 40], [80.3, 37.5],
      [81.6, 31.2], [83.6, 35.5], [83.8, 39], [82, 40.6], [80.6, 39.6], [82.4, 40.4], [84.6, 39],
      [86.5, 33], [88.4, 31.4], [86.6, 32.5], [85.9, 36.5], [87.2, 40.2], [89.4, 39.6], [90.3, 35.5],
      [89.4, 31.8], [88.3, 32.4], [90.5, 32.8], [92, 32.5],
      [93, 32], [93.4, 36], [93.4, 40.2], [93.8, 35], [95.6, 31.6], [97.4, 32.6], [97.8, 36.5],
      [98, 40], [99.5, 40.6], [101.5, 38],
    ],
  },
  // The dot of the i.
  { gap: 0.22, speed: 60, keys: [[77.6, 25.6], [78.4, 25.1]] },
  // The flourish under the name, right to left and back.
  {
    gap: 0.24,
    speed: 420,
    keys: [
      [99, 44.5], [85, 46], [65, 47], [45, 47.2], [25, 46.5], [12, 45], [7, 43], [9.5, 41.8], [14, 42.3],
    ],
  },
];

// Samples are taken about this far apart along a stroke, in grid units.
const STEP = 0.45;
// How much a turn slows the hand: speed × (FLOOR + (1 − FLOOR) / (1 + TURN · curvature)).
const FLOOR = 0.32;
const TURN = 9;
// The pen speeds up over its first RAMP units and slows over its last RAMP units.
const RAMP = 5;

export type Script = {
  x: number[];
  y: number[];
  /** Seconds from the start of the signature. */
  t: number[];
  /** Index of the stroke the sample belongs to. */
  stroke: number[];
  duration: number;
};

function catmull(p0: number, p1: number, p2: number, p3: number, u: number) {
  return 0.5 * (2 * p1 + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u * u + (-p0 + 3 * p1 - 3 * p2 + p3) * u * u * u);
}

/** The key points drawn through as one smooth curve, sampled about STEP apart. */
function densify(keys: readonly Key[]) {
  const out: Key[] = [keys[0]];
  for (let i = 0; i < keys.length - 1; i += 1) {
    const p0 = keys[Math.max(0, i - 1)];
    const p1 = keys[i];
    const p2 = keys[i + 1];
    const p3 = keys[Math.min(keys.length - 1, i + 2)];
    const steps = Math.max(1, Math.ceil(Math.hypot(p2[0] - p1[0], p2[1] - p1[1]) / STEP));
    for (let k = 1; k <= steps; k += 1) {
      const u = k / steps;
      out.push([catmull(p0[0], p1[0], p2[0], p3[0], u), catmull(p0[1], p1[1], p2[1], p3[1], u)]);
    }
  }
  return out;
}

/**
 * The signature laid out between `left` and `right` with its baseline at `baseline` (pt), and
 * timed sample by sample.
 */
export function signatureScript(left: number, right: number, baseline: number): Script {
  const strokes = STROKES.map((s, index) => ({
    ...s,
    keys: s.keys.map(([x, y]): Key => [x + (BASELINE - y) * SLANT + (index === 1 || index === 2 ? WORD_SPACE : 0), y]),
  }));
  let minX = Infinity;
  let maxX = -Infinity;
  strokes.forEach((s) => s.keys.forEach(([x]) => {
    minX = Math.min(minX, x);
    maxX = Math.max(maxX, x);
  }));
  const unit = (right - left) / (maxX - minX);
  const script: Script = { x: [], y: [], t: [], stroke: [], duration: 0 };
  let clock = 0;
  strokes.forEach((s, index) => {
    clock += s.gap;
    const points = densify(s.keys);
    // Arc length to every sample, for the ramps at both ends.
    const along = [0];
    for (let i = 1; i < points.length; i += 1) {
      along.push(along[i - 1] + Math.hypot(points[i][0] - points[i - 1][0], points[i][1] - points[i - 1][1]));
    }
    const total = along[along.length - 1];
    for (let i = 0; i < points.length; i += 1) {
      if (i > 0) {
        const ds = along[i] - along[i - 1];
        // Curvature from the turn between the segment before and the one after.
        const a = points[Math.max(0, i - 1)];
        const b = points[i];
        const c = points[Math.min(points.length - 1, i + 1)];
        const h1 = Math.atan2(b[1] - a[1], b[0] - a[0]);
        const h2 = Math.atan2(c[1] - b[1], c[0] - b[0]);
        let turn = Math.abs(h2 - h1);
        if (turn > Math.PI) turn = 2 * Math.PI - turn;
        const curvature = turn / Math.max(ds, 1e-3);
        const ends = Math.min(1, 0.35 + (0.65 * Math.min(along[i], total - along[i])) / RAMP);
        const speed = s.speed * (FLOOR + (1 - FLOOR) / (1 + TURN * curvature)) * ends;
        clock += ds / Math.max(speed, 1);
      }
      script.x.push(left + (points[i][0] - minX) * unit);
      script.y.push(baseline + (points[i][1] - BASELINE) * unit);
      script.t.push(clock);
      script.stroke.push(index);
    }
  });
  script.duration = clock;
  return script;
}
