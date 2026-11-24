// Nebula Dust Swirl — the loop, the nebula's pose and the scripted finger, in closed form.
//
// A grain's place is a pure function of its seeds and the loop time: pose() below, written again in
// WGSL in gpu/nds-wgsl.ts. Grains come in pairs (index 2j and 2j + 1) that share their seeds and sit on
// opposite arms, half a turn apart. The whole nebula turns half a turn per loop, so at t = LOOP each
// grain stands exactly where its twin stood at t = 0 and the frame repeats. Everything else that moves
// (the arms' winding breath, each grain's small orbit, the twinkle) has whole-number harmonics of
// 2πt / LOOP. Only what a finger does to a grain is state (a displacement on a spring), blended out in
// the rest stretch and cleared at the cut.

export const LOOP = 10;
export const TAU = Math.PI * 2;

export const DUST = {
  /** grains in the compute buffer (even: they come in pairs) */
  count: 40000,
  /** grains the Skia fallback draws: every STEP-th, odd so both arms are sampled */
  fallback: 1370,
  fallbackStep: 29,
  /** the workgroup's size */
  group: 64,
  /** the nebula's centre, as fractions of the screen */
  centreX: 0.5,
  centreY: 0.4,
  /** one pose unit in points = min(width × scaleW, height × scaleH) */
  scaleW: 0.5,
  scaleH: 0.27,
  /** a grain's radius, pt, at its smallest, plus what its size seed adds (squared) */
  size: 0.5,
  sizeAdd: 0.8,
  /** a bright star's radius, pt, and the share of grains that are stars */
  star: 1.7,
  starShare: 0.006,
  /** twinkles per loop: base + seed × range, whole numbers */
  twinkle: 2,
  twinkleRange: 4,
} as const;

/** the spiral */
export const SPIRAL = {
  /** how far an arm winds: θ = arm·π + wind · log2(1 + 6r) */
  wind: 3.1,
  rMin: 0.06,
  rMax: 1.0,
  /** radial density: r = rMin + (rMax − rMin) · a^density */
  density: 0.85,
  /** the arm's angular spread, near and far, and its radial jitter */
  spread: 0.34,
  spreadOut: 0.5,
  jitter: 0.08,
  /** the disc's thickness */
  thick: 0.09,
  /** the disc is seen at this tilt, radians (0 = face on) */
  tilt: 0.9,
  /** the camera's distance, pose units */
  camera: 3.4,
  /** the arms' winding breathes by this much, radians, strongest at the centre */
  breath: 0.26,
  /** each grain's small orbit: least and added radius */
  orbit: 0.008,
  orbitAdd: 0.026,
} as const;

/** the warm core */
export const CORE = {
  /** the share of grains in the core */
  share: 0.15,
  /** the core's Gaussian radius, pose units, and how flat it is */
  radius: 0.17,
  flat: 0.8,
} as const;

/** the loop's phases, seconds */
export const T = {
  /** the ghost finger stirs the arms */
  ghost: [1.2, 6.0],
  /** what a finger moved is blended out here */
  settle: [8.6, 9.4],
  /** the sleep timer rolls back here */
  rollback: [9.2, 9.8],
} as const;

/** the finger */
export const TOUCH = {
  /** reach of the stir (Gaussian), pt */
  radius: 82,
  /** sideways force around the finger, pt/s² */
  swirl: 520,
  /** how strongly grains are dragged along with the finger, 1/s */
  drag: 3.2,
  /** the spring that brings a grain back to its place, and its damping */
  spring: 6,
  damp: 3,
  /** how quickly the touch comes and goes, 1/s */
  ease: 12,
  /** the film runs at this rate under a finger */
  filmRate: 0.4,
  /** the finger's speed is capped here, pt/s */
  maxSpeed: 2400,
} as const;

/** a touch hands the nebula back this long after it ends, seconds */
export const IDLE = 2;
/** then the film runs to the nearer end of the loop over this long, seconds */
export const RETURN = 1.6;
/** the sleep timer's start, seconds (38:12 left) and the whole session (45 min) */
export const SESSION = 2292;
export const SESSION_TOTAL = 2700;
/** the frozen moment under reduced motion */
export const STILL = 0;

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

/** a whole-number hash of an integer (PCG), 0 … 2³²−1; the WGSL has the same one */
function pcg(v: number) {
  'worklet';
  const s = (Math.imul(v, 747796405) + 2891336453) >>> 0;
  const w = Math.imul((s >>> ((s >>> 28) + 4)) ^ s, 277803737) >>> 0;
  return ((w >>> 22) ^ w) >>> 0;
}

/** grain i's eight seeds in 0…1 (shared by the pair): radius, spread, size, phase, height, spread', kind, tone */
export function seedsOf(i: number): number[] {
  'worklet';
  const pair = i >>> 1;
  const k = 4294967296;
  const out: number[] = [];
  for (let j = 0; j < 8; j++) out.push(pcg(pair * 8 + j) / k);
  return out;
}

/**
 * grain with seeds s on arm `arm` (0 or 1) at loop time t: screen x, y in pose units around the
 * nebula's centre, its perspective scale, and its distance from the centre in the disc (0…1)
 */
export function pose(s: number[], arm: number, t: number): [number, number, number, number] {
  'worklet';
  const w = (TAU * t) / LOOP;
  let x;
  let y;
  let h;
  let r;
  if (s[6] < CORE.share) {
    // the core: a flattened Gaussian ball
    r = CORE.radius * Math.sqrt(-2 * Math.log(1 - 0.97 * s[0])) * 0.62;
    const a = TAU * s[1] + arm * Math.PI;
    x = r * Math.cos(a);
    y = r * Math.sin(a);
    h = CORE.flat * CORE.radius * (s[4] + s[5] - 1);
  } else {
    // an arm grain
    r = SPIRAL.rMin + (SPIRAL.rMax - SPIRAL.rMin) * Math.pow(s[0], SPIRAL.density);
    r *= 1 + SPIRAL.jitter * (s[5] - 0.5);
    const tri = s[1] + s[5] - 1;
    const a = arm * Math.PI + SPIRAL.wind * Math.log2(1 + 6 * r) + tri * (SPIRAL.spread + SPIRAL.spreadOut * r);
    x = r * Math.cos(a);
    y = r * Math.sin(a);
    h = SPIRAL.thick * (s[4] - 0.5) * (1.3 - r);
  }
  // the whole nebula turns half a turn per loop; the winding breathes, most at the centre
  const inner = 1 - Math.min(r, 1);
  const rot = (Math.PI * t) / LOOP + SPIRAL.breath * inner * inner * Math.sin(w + 1.3);
  const cr = Math.cos(rot);
  const sr = Math.sin(rot);
  let dx = x * cr - y * sr;
  let dy = x * sr + y * cr;
  const k = 1 + Math.floor(s[2] * 3);
  const rj = SPIRAL.orbit + SPIRAL.orbitAdd * s[2] * s[2];
  dx += rj * Math.cos(TAU * s[3] + k * w);
  dy += rj * Math.sin(TAU * s[3] + k * w);
  // tilt the disc away from the viewer and look at it in perspective
  const ct = Math.cos(SPIRAL.tilt);
  const st = Math.sin(SPIRAL.tilt);
  const sy = dy * ct - h * st;
  const z = dy * st + h * ct;
  const p = SPIRAL.camera / (SPIRAL.camera - z);
  return [dx * p, sy * p, p, Math.min(r, 1)];
}

/** the ghost finger at t: x, y in pose units around the centre, how much shows */
export function ghostAt(t: number): [number, number, number] {
  'worklet';
  const u = seg(t, T.ghost[0], T.ghost[1]);
  const e = smooth(u);
  const alpha = smooth(seg(u, 0, 0.1)) * (1 - smooth(seg(u, 0.9, 1)));
  // an arc round the core, along the arms, in the disc's tilt
  const a = -0.3 + 3.4 * e;
  const rad = 0.64 - 0.14 * Math.sin(Math.PI * e);
  return [rad * Math.cos(a), rad * Math.sin(a) * Math.cos(SPIRAL.tilt) * 1.15, alpha];
}

/** how much of the finger's push is shown at t */
export function holdAt(t: number) {
  'worklet';
  return 1 - smooth(seg(t, T.settle[0], T.settle[1]));
}

/** the sleep timer in seconds, a real number; it counts down and rolls back in the rest */
export function sessionAt(t: number, start: number = SESSION) {
  'worklet';
  const ran = Math.min(t, T.rollback[0]);
  const v = start - ran * (1 - smooth(seg(t, T.rollback[0], T.rollback[1])));
  const up = Math.ceil(v);
  return up - smooth(seg(up - v, 0, 0.3));
}

/** the core's breath, 0…1, twice per loop */
export function glowAt(t: number) {
  'worklet';
  return 0.5 - 0.5 * Math.cos((TAU * 2 * t) / LOOP);
}

/** the dust's light, linear 0…1 per channel, and how bright one grain is */
export const TONE = {
  core: [1.0, 0.84, 0.62],
  warm: [0.95, 0.6, 0.44],
  cool: [0.46, 0.62, 0.88],
  teal: [0.52, 0.78, 0.78],
  rose: [0.8, 0.5, 0.62],
  star: [1.0, 0.97, 0.92],
  /** the share of arm grains that are rose dust */
  roseShare: 0.14,
  /** one grain's brightness: core and arm grains, stars */
  coreGain: 0.11,
  armGain: 0.17,
  starGain: 0.6,
} as const;
