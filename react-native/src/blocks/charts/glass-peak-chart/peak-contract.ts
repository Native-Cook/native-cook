// The contract between the drawing (PeakCanvas, peak-shader) and the motion (peak-sim, use-peak-motion).
// Everything is plain data in a 402×874 design space, in points; the canvas scales it to the window.

/** Design space the whole block is laid out in. */
export const DESIGN = { w: 402, h: 874 } as const;

/** The glass slab. `r` is the corner radius of its squircle outline. */
export const CARD = { x: 28, y: 176, w: 346, h: 480, r: 64 } as const;

/** The plot spans the slab edge to edge; value 1 sits on `top`, value 0 on `zero`. */
export const PLOT = { left: 28, right: 374, top: 294, zero: 476 } as const;

/** Header row: refresh glyph + status text on the left, period chip on the right. */
export const STATUS = { glyphX: 58, glyphY: 232, glyphR: 6, textX: 72, baseline: 236, size: 12 } as const;
export const PILL = { x: 242, y: 214, w: 108, h: 36, r: 18, textX: 258, baseline: 237, size: 13, chevronX: 330, chevronY: 232 } as const;

/** Number block, centred on the slab's axis (x = 201). */
export const TEXT = {
  axis: 201,
  captionBaseline: 512,
  captionSize: 14,
  numberBaseline: 576,
  numberSize: 68,
  numberTracking: -2,
  subBaseline: 602,
  subSize: 12,
} as const;

/** Touch regions (design points). */
export const HIT = {
  refresh: { x: 40, y: 210, w: 168, h: 44 },
  pill: { x: 238, y: 210, w: 116, h: 44 },
  plot: { x: 28, y: 258, w: 346, h: 236 },
  card: { x: 28, y: 176, w: 346, h: 480 },
} as const;

/** Samples of the curve, uniform in u (0 = left edge of the slab, 1 = right edge). */
export const SAMPLES = 96;

/** Period of the self-playing loop, seconds. Idle life in the shader must be periodic in it. */
export const LOOP = 13;

/** Beads of liquid light a splash can throw up. */
export const SPLASH = 8;

export const ORB_R = 11;
export const NODE_R = 4.5;

export type Period = {
  label: string;
  sub: string;
  /** Number shown at the peak. */
  value: number;
  /** Resting level of the liquid light, 0–1. */
  level: number;
  /** [u, v] knots of the curve; v is height 0–1. */
  knots: readonly (readonly [number, number])[];
  /** u of the four nodes; index 2 is the peak, where the orb rests. */
  nodes: readonly [number, number, number, number];
};

export const PEAK_NODE = 2;

export const PERIODS: readonly Period[] = [
  {
    label: 'Monthly',
    sub: 'increase vs last month',
    value: 300,
    level: 1,
    knots: [
      [0, 0.074], [0.103, 0.153], [0.21, 0.09], [0.318, 0.049], [0.38, 0.14], [0.418, 0.47],
      [0.45, 0.87], [0.488, 0.985], [0.526, 1], [0.571, 0.73], [0.626, 0.4], [0.692, 0.206],
      [0.733, 0.153], [0.828, 0.115], [1, 0.13],
    ],
    nodes: [0.103, 0.318, 0.526, 0.733],
  },
  {
    label: 'Weekly',
    sub: 'increase vs last week',
    value: 48,
    level: 0.42,
    knots: [
      [0, 0.13], [0.14, 0.23], [0.3, 0.1], [0.44, 0.2], [0.56, 0.4], [0.62, 0.455], [0.68, 0.47],
      [0.74, 0.44], [0.8, 0.33], [0.88, 0.2], [1, 0.27],
    ],
    nodes: [0.14, 0.3, 0.68, 0.88],
  },
  {
    label: 'Yearly',
    sub: 'increase vs last year',
    value: 124,
    level: 0.68,
    knots: [
      [0, 0.05], [0.16, 0.1], [0.28, 0.07], [0.42, 0.22], [0.54, 0.3], [0.64, 0.34], [0.72, 0.56],
      [0.77, 0.69], [0.8, 0.71], [0.84, 0.67], [0.94, 0.46], [1, 0.5],
    ],
    nodes: [0.16, 0.42, 0.8, 0.94],
  },
] as const;

/** Monotone cubic (Fritsch–Carlson) through the knots, sampled at SAMPLES uniform u, then rounded by a small binomial blur. */
export function buildYs(knots: readonly (readonly [number, number])[]): number[] {
  const n = knots.length;
  const d: number[] = [];
  const m: number[] = [];
  for (let i = 0; i < n - 1; i++) d.push((knots[i + 1][1] - knots[i][1]) / (knots[i + 1][0] - knots[i][0]));
  m.push(d[0]);
  for (let i = 1; i < n - 1; i++) m.push(d[i - 1] * d[i] <= 0 ? 0 : (d[i - 1] + d[i]) / 2);
  m.push(d[n - 2]);
  for (let i = 0; i < n - 1; i++) {
    if (d[i] === 0) {
      m[i] = 0;
      m[i + 1] = 0;
    } else {
      const a = m[i] / d[i];
      const b = m[i + 1] / d[i];
      const s = a * a + b * b;
      if (s > 9) {
        const t = 3 / Math.sqrt(s);
        m[i] = t * a * d[i];
        m[i + 1] = t * b * d[i];
      }
    }
  }
  const ys: number[] = [];
  let k = 0;
  for (let j = 0; j < SAMPLES; j++) {
    const u = j / (SAMPLES - 1);
    while (k < n - 2 && u > knots[k + 1][0]) k++;
    const h = knots[k + 1][0] - knots[k][0];
    const t = (u - knots[k][0]) / h;
    const t2 = t * t;
    const t3 = t2 * t;
    ys.push(
      (2 * t3 - 3 * t2 + 1) * knots[k][1] +
        (t3 - 2 * t2 + t) * h * m[k] +
        (-2 * t3 + 3 * t2) * knots[k + 1][1] +
        (t3 - t2) * h * m[k + 1],
    );
  }
  // Round the joints: four passes of a 1-2-1 kernel, ends held.
  for (let pass = 0; pass < 4; pass++) {
    let prev = ys[0];
    for (let j = 1; j < SAMPLES - 1; j++) {
      const here = ys[j];
      ys[j] = (prev + 2 * here + ys[j + 1]) / 4;
      prev = here;
    }
  }
  return ys;
}

/** The three curves, sampled once. */
export const CURVES: readonly number[][] = [
  buildYs(PERIODS[0].knots),
  buildYs(PERIODS[1].knots),
  buildYs(PERIODS[2].knots),
];

/** Height 0–1 of a sampled curve at u (linear between samples: the tube is exactly this polyline). */
export function heightAt(ys: readonly number[], u: number): number {
  'worklet';
  const f = Math.min(1, Math.max(0, u)) * (SAMPLES - 1);
  const i = Math.min(SAMPLES - 2, Math.floor(f));
  return ys[i] + (ys[i + 1] - ys[i]) * (f - i);
}

export function xOf(u: number): number {
  'worklet';
  return PLOT.left + u * (PLOT.right - PLOT.left);
}

export function yOf(v: number): number {
  'worklet';
  return PLOT.zero - v * (PLOT.zero - PLOT.top);
}

/**
 * y of the liquid's front at x: where its light starts, thinning out upward from full strength near the bottom rim.
 * Shared by the shader (the pool's shape) and the motion (where the beam lands and a splash starts).
 */
export function poolFrontY(level: number, hump: number, humpX: number, slope: number, x: number): number {
  'worklet';
  const dx = x - humpX;
  return (
    CARD.y + CARD.h - (10 + 140 * level) - hump * 84 * Math.exp(-(dx * dx) / (2 * 58 * 58)) - slope * (x - 201) * 0.4
  );
}

/**
 * y of the liquid's visible surface at x: where its light reaches about half strength. The front above it is only
 * haze. Ripples, the meniscus glint, the beam's landing and the splash all live on this line, never on the front.
 */
export function poolSurfaceY(level: number, hump: number, humpX: number, slope: number, x: number): number {
  'worklet';
  const bottom = CARD.y + CARD.h;
  return bottom - 0.42 * (bottom - poolFrontY(level, hump, humpX, slope, x));
}

/** Where a node's drop line ends: above the type it would otherwise cross. */
export function dropBottom(x: number, peak: boolean): number {
  'worklet';
  const off = Math.abs(x - TEXT.axis);
  if (off < 64) return 492;
  if (peak) return 516;
  return off < 100 ? 516 : 556;
}

/**
 * One picture. The motion writes a new Frame every display frame; the canvas draws it and nothing else.
 * Coordinates are design points; gains are 1 at rest unless stated.
 */
export type Frame = {
  /** Scene clock in seconds for idle life. In the self-playing loop it wraps at LOOP. */
  t: number;
  /** 0–1 fade of the whole scene once fonts and the canvas are ready. */
  presented: number;

  /** SAMPLES heights 0–1: the tube's shape now (mid-morph included). */
  ys: number[];
  /** The tube is lit from u = 0 to `lit`; beyond it only the unlit glass filament shows. 1 at rest. */
  lit: number;
  /** 0–1 brightness of the travelling tip at u = lit (0 at rest). */
  tip: number;
  /** Light gain of the lit tube (1 at rest, breathes a few percent). */
  tube: number;

  /** The orb: the big light that rests on the peak, rides the head while the line draws, and follows a finger. */
  orbX: number;
  orbY: number;
  /** Radius in points (ORB_R at rest on a peak, smaller away from it, larger on impact). */
  orbR: number;
  /** 0 = dark glass bead, 1 = rest, up to 2 on impact. */
  orbGain: number;

  /** The four node beads (index PEAK_NODE is under the orb at rest). */
  nodeX: number[];
  nodeY: number[];
  /** 0 = unlit glass bead, 1 = lit. */
  nodeLit: number[];
  /** Scale, 1 at rest (pops past 1 as the head passes). */
  nodeScale: number[];
  /** 0–1 growth of each drop line, from the node down to nodeBottom. */
  drop: number[];
  nodeBottom: number[];

  /** Liquid light. Level 0 = drained ember, 1 = the Monthly resting pool. */
  poolLevel: number;
  /** x of the hump the orb pulls up in the liquid's front, and its height (0 = flat front, 1 = strong). */
  poolHumpX: number;
  poolHump: number;
  /** Slosh: tilt of the front, −1…1 (positive = higher on the right). 0 at rest. */
  poolSlope: number;
  /** Brightness of the liquid, 1 at rest. */
  poolGain: number;
  /** 0–1 agitation of the liquid: ripples running along its front, livelier caustics, a glint on the meniscus. 0 at rest. */
  poolStir: number;
  /** Splash: SPLASH beads of liquid light thrown up from the pool, in design points (radius 0 = not drawn). */
  splashX: number[];
  splashY: number[];
  splashR: number[];

  /** Light shaft from the orb straight down: 0–1 strength (0.22 at rest) and 0–1 reach (1 = it touches the liquid). */
  beam: number;
  beamReach: number;

  /** Shock ring from the peak impact: centre, radius in points, 0–1 strength. It lights the rim where it crosses it. */
  ringX: number;
  ringY: number;
  ringR: number;
  ringA: number;
  /** 0–1 white flare at the orb on impact. */
  flash: number;
  /** 0–1 extra light in the whole rim (press answer, glints). */
  rimGlint: number;

  /** Tilt of the slab in radians (rotateX: + = top edge away; rotateY: + = right edge away), and its scale. */
  tiltX: number;
  tiltY: number;
  cardScale: number;

  /** Number shown (rounded by the canvas), its opacity, scale and 0–1 extra glow. */
  value: number;
  numberAlpha: number;
  numberScale: number;
  numberGlow: number;

  /** Period chip and sub-label: `periodMix` 0→1 rolls from PERIODS[periodPrev] to PERIODS[period]. */
  period: number;
  periodPrev: number;
  periodMix: number;
  /** Status text: 0 = "Data updated just now", 1 = "Updating…"; `statusMix` 0→1 rolls statusPrev → status. */
  status: number;
  statusPrev: number;
  statusMix: number;
  /** Rotation of the refresh glyph, radians. */
  spin: number;
  /** 0–1 press of the chip and of the refresh control; rotation of the chevron in radians (0 at rest). */
  pillPress: number;
  refreshPress: number;
  chevron: number;

  /** Finger light: where the glass is touched and how strongly (0 = no touch). */
  touchX: number;
  touchY: number;
  touchA: number;
};

export const STATUS_TEXT = ['Data updated just now', 'Updating…'] as const;
export const CAPTION = 'Conversion rate';

/** The resting picture of a period: the state every transition ends in and the loop starts and ends on. */
export function restFrame(period: number): Frame {
  const p = PERIODS[period];
  const ys = CURVES[period];
  const nodeX: number[] = [];
  const nodeY: number[] = [];
  const nodeBottom: number[] = [];
  for (let i = 0; i < 4; i++) {
    nodeX.push(xOf(p.nodes[i]));
    nodeY.push(yOf(heightAt(ys, p.nodes[i])));
    nodeBottom.push(dropBottom(nodeX[i], i === PEAK_NODE));
  }
  return {
    t: 0,
    presented: 1,
    ys: ys.slice(),
    lit: 1,
    tip: 0,
    tube: 1,
    orbX: nodeX[PEAK_NODE],
    orbY: nodeY[PEAK_NODE],
    orbR: ORB_R,
    orbGain: 1,
    nodeX,
    nodeY,
    nodeLit: [1, 1, 1, 1],
    nodeScale: [1, 1, 1, 1],
    drop: [1, 1, 1, 1],
    nodeBottom,
    poolLevel: p.level,
    poolHumpX: nodeX[PEAK_NODE],
    poolHump: 0.35,
    poolSlope: 0,
    poolGain: 1,
    poolStir: 0,
    splashX: [0, 0, 0, 0, 0, 0, 0, 0],
    splashY: [0, 0, 0, 0, 0, 0, 0, 0],
    splashR: [0, 0, 0, 0, 0, 0, 0, 0],
    beam: 0.22,
    beamReach: 1,
    ringX: nodeX[PEAK_NODE],
    ringY: nodeY[PEAK_NODE],
    ringR: 0,
    ringA: 0,
    flash: 0,
    rimGlint: 0,
    tiltX: 0,
    tiltY: 0,
    cardScale: 1,
    value: p.value,
    numberAlpha: 1,
    numberScale: 1,
    numberGlow: 0,
    period,
    periodPrev: period,
    periodMix: 1,
    status: 0,
    statusPrev: 0,
    statusMix: 1,
    spin: 0,
    pillPress: 0,
    refreshPress: 0,
    chevron: 0,
    touchX: CARD.x + CARD.w / 2,
    touchY: CARD.y + CARD.h / 2,
    touchA: 0,
  };
}

/** Props of the drawing. `still` = Reduce Motion: no idle life. */
export type PeakCanvasProps = {
  width: number;
  height: number;
  frame: import('react-native-reanimated').SharedValue<Frame>;
  still: boolean;
  /** Called once when fonts are loaded and the first picture can be drawn. */
  onReady: () => void;
};
