// Murmuration Flock — the loop, the flock's pose and the scripted hand, in closed form.
//
// A bird's place is a pure function of its seeds and the loop time: pose() below, written again in
// WGSL in gpu/mf-wgsl.ts. Only what a finger does to a bird is state (a displacement on a spring), and
// that is blended out in the rest stretch and cleared at the cut, so frame 12 s equals frame 0.

export const LOOP = 12;
export const TAU = Math.PI * 2;

export const FLOCK = {
  /** birds in the compute buffer */
  count: 20000,
  /** birds the Skia fallback draws, an even sample of the same flock */
  fallback: 900,
  /** the workgroup's size */
  group: 64,
  /** the flock's centre, as fractions of the screen */
  centreX: 0.5,
  centreY: 0.4,
  /** one pose unit in points = min(width × scaleW, height × scaleH) */
  scaleW: 0.5,
  scaleH: 0.3,
  /** how far back the heading is measured, seconds */
  headBack: 0.03,
  /** a bird's dart, pt: half length and half width at its smallest, plus what its depth seed adds */
  length: 1.7,
  lengthAdd: 1.5,
  width: 0.62,
  widthAdd: 0.5,
  /** wing beats per loop: base + seed × range, whole numbers so the flap closes */
  beats: 18,
  beatsRange: 12,
  /** opacity of one bird */
  alpha: 0.62,
} as const;

/** the ribbon the flock rests in */
export const CLOUD = {
  along: 0.92,
  sway: 0.14,
  swayFreq: 2.2,
  rise: 0.3,
  riseFreq: 2.7,
  ripple: 0.13,
  rippleFreq: 5.1,
  /** the sheet's half width at its middle, how much it narrows to the tips, and its least */
  width: 0.5,
  narrow: 0.55,
  widthMin: 0.04,
  /** the sheet swells and thins along its length, so the cloud has a body and a tail */
  swell: 0.4,
  swellFreq: 1.7,
  /** how much the sheet twists along its length: the folds are where it turns edge-on */
  twist: 2.4,
  twistWave: 1.2,
  twistWaveFreq: 3,
  /** the sheet is seen at this tilt */
  tilt: 0.72,
  /** each bird's own small orbit: least and added radius */
  orbit: 0.018,
  orbitAdd: 0.07,
} as const;

/** the soft circle of the inhale */
export const RING = {
  radius: 0.78,
  /** a bird sits between inner × radius and radius, denser at the rim */
  inner: 0.62,
  spread: 0.05,
  /** the circle is this much smaller when the morph begins */
  grow: 0.82,
} as const;

/** the morph's arc and the exhale's gust */
export const AIR = {
  arc: 0.12,
  arcAdd: 0.22,
  gust: 0.1,
  gustAdd: 0.26,
} as const;

/** the loop's phases, seconds */
export const T = {
  inhale: [1.4, 5.4],
  hold: [5.4, 6.4],
  exhale: [6.4, 10.4],
  /** each bird starts its morph up to this much late */
  stagger: 1.1,
  /** what a finger moved is blended out here */
  settle: [10.7, 11.7],
  /** the session timer rolls back here */
  rollback: [11.2, 11.9],
  /** the ghost finger */
  ghost: [7.0, 9.2],
} as const;

/** the finger */
export const TOUCH = {
  /** reach of the push (Gaussian), pt */
  radius: 74,
  /** outward and sideways force, pt/s² */
  flee: 2600,
  swirl: 1500,
  /** the spring that brings a bird back to its place */
  spring: 26,
  damp: 6.5,
  /** how quickly the touch comes and goes, 1/s */
  ease: 14,
  /** the film runs at this rate under a finger */
  filmRate: 0.25,
} as const;

/** a touch hands the flock back this long after it ends, seconds */
export const IDLE = 2;
/** then the film runs to the nearer end of the loop over this long, seconds */
export const RETURN = 1.4;
/** the timer's start, seconds (4:12) */
export const SESSION = 252;
/** the frozen moment under reduced motion: the circle, held */
export const STILL = 5.9;

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}

/** 0 before a, 1 after b */
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function smooth(v: number) {
  'worklet';
  return v * v * (3 - 2 * v);
}

export function smoother(v: number) {
  'worklet';
  return v * v * v * (v * (v * 6 - 15) + 10);
}

/** a whole-number hash of an integer (PCG), 0 … 2³²−1; the WGSL has the same one */
function pcg(v: number) {
  'worklet';
  const s = (Math.imul(v, 747796405) + 2891336453) >>> 0;
  const w = Math.imul((s >>> ((s >>> 28) + 4)) ^ s, 277803737) >>> 0;
  return ((w >>> 22) ^ w) >>> 0;
}

/** bird i's four seeds in 0…1: along the ribbon, across it, depth, phase */
export function seedsOf(i: number): [number, number, number, number] {
  'worklet';
  const k = 4294967296;
  return [pcg(i * 4) / k, pcg(i * 4 + 1) / k, pcg(i * 4 + 2) / k, pcg(i * 4 + 3) / k];
}

/** how far bird `d` is from the ribbon (0) to the circle (1) at t */
export function morphAt(t: number, d: number) {
  'worklet';
  const late = d * T.stagger;
  return smoother(seg(t, T.inhale[0] + late, T.inhale[1])) - smoother(seg(t, T.exhale[0] + late, T.exhale[1]));
}

/** the exhale's gust at t, 0…1…0 */
export function gustAt(t: number) {
  'worklet';
  const s = Math.sin(Math.PI * seg(t, T.exhale[0], T.exhale[1]));
  return s * s;
}

/** bird (a, b, c, d) at loop time t, in pose units around the flock's centre */
export function pose(a: number, b: number, c: number, d: number, t: number): [number, number] {
  'worklet';
  const w = (TAU * t) / LOOP;
  const u = 2 * a - 1;
  const v0 = 2 * b - 1;
  const v = v0 * (0.55 + 0.45 * Math.abs(v0));
  const width = (CLOUD.width * (1 - CLOUD.narrow * u * u) + CLOUD.widthMin) * (1 + CLOUD.swell * Math.sin(CLOUD.swellFreq * u + w));
  const phi = CLOUD.twist * u + w + CLOUD.twistWave * Math.sin(2 * w + CLOUD.twistWaveFreq * u);
  const k = 1 + Math.floor(c * 3);
  const orbit = TAU * d + k * w;
  const rj = CLOUD.orbit + CLOUD.orbitAdd * c * c;
  const cloudX = CLOUD.along * u + CLOUD.sway * Math.sin(CLOUD.swayFreq * u + w) - Math.sin(phi) * width * v + rj * Math.cos(orbit);
  const cloudY = CLOUD.rise * Math.sin(CLOUD.riseFreq * u + w) + CLOUD.ripple * Math.sin(CLOUD.rippleFreq * u - 2 * w) + Math.cos(phi) * width * v * CLOUD.tilt + rj * Math.sin(orbit);

  const m = morphAt(t, d);
  const spin = TAU * a + w;
  const r = (RING.radius * (RING.inner + (1 - RING.inner) * Math.sqrt(b)) + RING.spread * (c - 0.5)) * (RING.grow + (1 - RING.grow) * m);
  const ringX = r * Math.cos(spin);
  const ringY = r * Math.sin(spin);

  const dx = ringX - cloudX;
  const dy = ringY - cloudY;
  const bend = Math.sin(Math.PI * m) * (AIR.arc + AIR.arcAdd * (2 * c - 1));
  let x = cloudX + dx * m - dy * bend;
  let y = cloudY + dy * m + dx * bend;
  const g = gustAt(t);
  if (g > 0) {
    const len = Math.sqrt(x * x + y * y) + 0.001;
    const push = g * (AIR.gust + AIR.gustAdd * c);
    x += (x / len) * push;
    y += (y / len) * push;
  }
  return [x, y];
}

/** the breath's word at t: how much of Settle, Inhale and Exhale shows */
export function wordsAt(t: number): [number, number, number] {
  'worklet';
  const inhale = smooth(seg(t, T.inhale[0] - 0.15, T.inhale[0] + 0.25)) * (1 - smooth(seg(t, T.exhale[0] - 0.2, T.exhale[0] + 0.15)));
  const exhale = smooth(seg(t, T.exhale[0] - 0.05, T.exhale[0] + 0.3)) * (1 - smooth(seg(t, T.exhale[1] - 0.2, T.exhale[1] + 0.2)));
  return [clamp01(1 - inhale - exhale), inhale, exhale];
}

/** the faint guide circle's strength at t */
export function guideAt(t: number) {
  'worklet';
  return smooth(seg(t, T.inhale[0] + 0.6, T.inhale[1])) * (1 - smooth(seg(t, T.exhale[0], T.exhale[0] + 1.2)));
}

/** the session timer in seconds, a real number; it counts down and rolls back in the rest */
export function sessionAt(t: number) {
  'worklet';
  const ran = Math.min(t, T.rollback[0]);
  const v = SESSION - ran * (1 - smooth(seg(t, T.rollback[0], T.rollback[1])));
  const up = Math.ceil(v);
  return up - smooth(seg(up - v, 0, 0.3));
}

/** the ghost finger at t: x, y as fractions of the screen, and how much of it shows */
export function ghostAt(t: number): [number, number, number] {
  'worklet';
  const u = seg(t, T.ghost[0], T.ghost[1]);
  const e = smooth(u);
  const alpha = smooth(seg(u, 0, 0.14)) * (1 - smooth(seg(u, 0.86, 1)));
  return [0.2 + 0.6 * e, FLOCK.centreY + 0.1 - 0.16 * e + 0.03 * Math.sin(u * TAU), alpha];
}
