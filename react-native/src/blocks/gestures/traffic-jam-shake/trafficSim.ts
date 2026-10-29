// The jam's physics, a port of TrafficJamShakeSim.swift number for number: every car is a spring
// to a shared ground, leans when it lags, stretches along its width when it moves fast (and grows
// tall in the air), and pushes its neighbours apart through contact springs. A hard bump makes the
// hit car honk, a harder one makes it hop. Fixed step 1/240 s, so the loop (stepped from rest at
// t = 0 with the scripted input below) is the same on every run and in every stack. Every function
// is a worklet and is declared above its callers.

export type Car = {
  x: number;
  y: number;
  /** Front face width, body height, cabin height, length (depth of the extrusion). */
  w: number;
  hb: number;
  hc: number;
  l: number;
  color: number;
  /** Rest angle, radians (positive turns clockwise on screen). */
  angle: number;
};

/** Seconds. */
export const LOOP = 8;
export const DT = 1 / 240;
/** The scripted tap on the front car. */
const TAP_TIME = 0.95;
export const TAP_CAR = 7;
const SHAKE_START = 1.95;
const SHAKE_END = 5.0;
/** One left-right swing of the ghost finger, seconds. */
const SWING = 0.66;
const AMPLITUDE = 10;
/** The residual is faded out over this span so the loop ends exactly at rest. */
const FADE_START = 6.4;
const FADE_END = 7.2;
export const USER_HOLD = 2;
export const SETTLE = 0.6;
export const HONK_LIFE = 0.5;

const GROUND_OMEGA = 30;
const GROUND_ZETA = 0.9;
const CAR_ZETA = 0.2;
const CONTACT_K = 1600;
const CONTACT_C = 10;
const HONK_SPEED = 70;
const HOP_SPEED = 100;
const GRAVITY = 2600;
const TAP_LIFT = 380;
const LEAN_PER_POINT = 0.007;
const LEAN_OMEGA = 13;
const LEAN_ZETA = 0.2;
const STRETCH_OMEGA = 18;
const STRETCH_ZETA = 0.25;

/** The pile, back to front (paint order). Colours index BODIES in trafficDraw. */
export const CARS: Car[] = [
  { x: 62, y: 150, w: 80, hb: 66, hc: 30, l: 54, color: 2, angle: 0 },
  { x: 150, y: 146, w: 78, hb: 74, hc: 28, l: 52, color: 1, angle: 0 },
  { x: 240, y: 156, w: 84, hb: 82, hc: 30, l: 58, color: 0, angle: 0 },
  { x: 70, y: 228, w: 104, hb: 36, hc: 28, l: 66, color: 3, angle: 0.2 },
  { x: 196, y: 220, w: 120, hb: 30, hc: 26, l: 72, color: 2, angle: 0 },
  { x: 92, y: 288, w: 132, hb: 34, hc: 26, l: 70, color: 0, angle: 0 },
  { x: 236, y: 290, w: 110, hb: 36, hc: 28, l: 66, color: 1, angle: -0.12 },
  { x: 196, y: 344, w: 112, hb: 28, hc: 24, l: 60, color: 4, angle: 0 },
];
const COUNT = CARS.length;

/** Neighbours that touch in the pile, as [i, j] flattened. */
const PAIRS = [0, 1, 1, 2, 0, 3, 1, 3, 1, 4, 2, 4, 3, 4, 3, 5, 4, 5, 4, 6, 2, 6, 5, 6, 5, 7, 6, 7];

/** Extrusion direction: 34° up and to the right. */
export const DIR_X = Math.cos((34 * Math.PI) / 180);
export const DIR_Y = -Math.sin((34 * Math.PI) / 180);

export function hash(i: number) {
  'worklet';
  const s = Math.sin(i * 12.9898) * 43758.5453;
  return s - Math.floor(s);
}
function spinSign(i: number) {
  'worklet';
  return hash(i + 7) < 0.5 ? -1 : 1;
}
export function seg(t: number, a: number, b: number) {
  'worklet';
  return Math.min(Math.max((t - a) / (b - a), 0), 1);
}
export function smooth(t: number, a: number, b: number) {
  'worklet';
  const x = seg(t, a, b);
  return x * x * (3 - 2 * x);
}
function easeOutCubic(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}
function easeInCubic(x: number) {
  'worklet';
  return x * x * x;
}

const OMEGAS = CARS.map((_, i) => 2 * Math.PI * (1.4 + 1.1 * hash(i + 1)));
/** Unit vectors from each pair's first car to its second, at rest. */
const NORMALS: number[] = [];
for (let k = 0; k < PAIRS.length; k += 2) {
  const dx = CARS[PAIRS[k + 1]].x - CARS[PAIRS[k]].x;
  const dy = CARS[PAIRS[k + 1]].y - CARS[PAIRS[k]].y;
  const len = Math.max(Math.hypot(dx, dy), 1);
  NORMALS.push(dx / len, dy / len);
}

/** The pile's bounds in design points (with the hard shadow). */
export const BOUNDS = (() => {
  let left = Infinity;
  let top = Infinity;
  let right = -Infinity;
  let bottom = -Infinity;
  for (const c of CARS) {
    const t = c.y - c.hb - c.hc + 0.82 * c.l * DIR_Y;
    left = Math.min(left, c.x - c.w / 2);
    right = Math.max(right, c.x + c.w / 2 + c.l * DIR_X);
    top = Math.min(top, t);
    bottom = Math.max(bottom, c.y + 6);
  }
  return { left, top, right, bottom };
})();

function envelope(t: number) {
  'worklet';
  return AMPLITUDE * smooth(t, SHAKE_START, 2.6) * (1 - smooth(t, SHAKE_END - 0.55, SHAKE_END));
}
/** The ground target of the ghost finger's shake, design points. */
export function targetX(t: number) {
  'worklet';
  if (t <= SHAKE_START || t >= SHAKE_END) return 0;
  return envelope(t) * Math.sin((2 * Math.PI * (t - SHAKE_START)) / SWING);
}
export function targetY(t: number) {
  'worklet';
  if (t <= SHAKE_START || t >= SHAKE_END) return 0;
  return 0.25 * envelope(t) * Math.sin((4 * Math.PI * (t - SHAKE_START)) / SWING);
}

/** 1 for most of the loop, easing to 0 before it ends so the last frame is the rest frame. */
export function loopWindow(t: number) {
  'worklet';
  return 1 - smooth(t, FADE_START, FADE_END);
}

/** The ghost finger: opacity, scale, and whether it is on the tapped car (else the pile's centre). */
export function finger(t: number) {
  'worklet';
  if (t < 1.5) {
    const i = easeOutCubic(seg(t, 0.75, 0.95));
    const o = easeInCubic(seg(t, 1.0, 1.25));
    return { opacity: i * (1 - o), scale: 0.8 + 0.2 * i - 0.12 * seg(t, 0.92, 0.98) * (1 - seg(t, 0.98, 1.08)), onTap: true };
  }
  const i = easeOutCubic(seg(t, 1.7, 1.95));
  const o = easeInCubic(seg(t, SHAKE_END, SHAKE_END + 0.25));
  return { opacity: i * (1 - o), scale: 0.8 + 0.2 * i, onTap: false };
}

/** Engine idle: a tiny vertical tremble that repeats 36 times per loop. */
export function tremble(i: number, t: number) {
  'worklet';
  return 0.35 * Math.sin((2 * Math.PI * 36 * t) / LOOP + 2 * Math.PI * hash(i + 3));
}

export type Sim = {
  px: number[];
  py: number[];
  vx: number[];
  vy: number[];
  z: number[];
  vz: number[];
  a: number[];
  va: number[];
  e: number[];
  ve: number[];
  /** Seconds since the last honk. */
  honk: number[];
  honkCool: number[];
  hopCool: number[];
  gx: number;
  gy: number;
  gvx: number;
  gvy: number;
  /** Where the ground is pulled to: the script's shake, or the finger. */
  tx: number;
  ty: number;
  /** Steps done since the last reset. */
  steps: number;
  /** True while the loop's script drives the ground (false under the finger). */
  scripted: boolean;
  /** Counts hard bumps, for haptics. */
  bumps: number;
  tapped: boolean;
};

function zeros() {
  'worklet';
  const out: number[] = [];
  for (let i = 0; i < COUNT; i++) out.push(0);
  return out;
}

export function makeSim(): Sim {
  'worklet';
  const honk = zeros();
  for (let i = 0; i < COUNT; i++) honk[i] = 9;
  return {
    px: zeros(), py: zeros(), vx: zeros(), vy: zeros(), z: zeros(), vz: zeros(),
    a: zeros(), va: zeros(), e: zeros(), ve: zeros(), honk, honkCool: zeros(), hopCool: zeros(),
    gx: 0, gy: 0, gvx: 0, gvy: 0, tx: 0, ty: 0, steps: 0, scripted: true, bumps: 0, tapped: false,
  };
}

export function resetSim(s: Sim) {
  'worklet';
  for (let i = 0; i < COUNT; i++) {
    s.px[i] = 0; s.py[i] = 0; s.vx[i] = 0; s.vy[i] = 0; s.z[i] = 0; s.vz[i] = 0;
    s.a[i] = 0; s.va[i] = 0; s.e[i] = 0; s.ve[i] = 0;
    s.honk[i] = 9; s.honkCool[i] = 0; s.hopCool[i] = 0;
  }
  s.gx = 0; s.gy = 0; s.gvx = 0; s.gvy = 0; s.tx = 0; s.ty = 0;
  s.steps = 0;
  s.tapped = false;
  s.scripted = true;
}

export function simTime(s: Sim) {
  'worklet';
  return s.steps * DT;
}

/** Multiplies every deviation from rest by k (used when a touch picks up a fading loop). */
export function scaleSim(s: Sim, k: number) {
  'worklet';
  for (let i = 0; i < COUNT; i++) {
    s.px[i] *= k; s.py[i] *= k; s.vx[i] *= k; s.vy[i] *= k; s.z[i] *= k; s.vz[i] *= k;
    s.a[i] *= k; s.va[i] *= k; s.e[i] *= k; s.ve[i] *= k;
  }
  s.gx *= k; s.gy *= k; s.gvx *= k; s.gvy *= k;
}

/** Makes car i hop and honk. */
export function tapCar(s: Sim, i: number) {
  'worklet';
  if (s.z[i] < 6) s.vz[i] = TAP_LIFT;
  s.va[i] += 2 * spinSign(i);
  s.honk[i] = 0;
  s.honkCool[i] = 0.8;
  s.hopCool[i] = 0.6;
}

function bump(s: Sim, k: number, speed: number) {
  'worklet';
  if (speed > HONK_SPEED && s.honkCool[k] <= 0) {
    s.honk[k] = 0;
    s.honkCool[k] = 0.8;
    s.bumps += 1;
  }
  if (speed > HOP_SPEED && s.hopCool[k] <= 0 && s.z[k] < 1) {
    s.vz[k] = Math.min(300, 2.5 * speed);
    s.va[k] += 1.2 * spinSign(k);
    s.hopCool[k] = 0.6;
  }
}

function land(s: Sim, i: number, impact: number) {
  'worklet';
  // Neighbours in pair order, as in the Swift list.
  for (let k = 0; k < PAIRS.length; k += 2) {
    let j = -1;
    let nx = 0;
    let ny = 0;
    if (PAIRS[k] === i) {
      j = PAIRS[k + 1];
      nx = NORMALS[k];
      ny = NORMALS[k + 1];
    } else if (PAIRS[k + 1] === i) {
      j = PAIRS[k];
      nx = -NORMALS[k];
      ny = -NORMALS[k + 1];
    } else {
      continue;
    }
    if (s.z[j] < 1 && s.hopCool[j] <= 0) {
      s.vz[j] += 0.32 * impact;
      s.vx[j] += 0.12 * impact * nx;
      s.vy[j] += 0.12 * impact * ny;
      s.hopCool[j] = 0.6;
    }
  }
}

function step(s: Sim) {
  'worklet';
  const dt = DT;
  const t = s.steps * DT;
  if (s.scripted) {
    s.tx = targetX(t);
    s.ty = targetY(t);
    if (!s.tapped && t >= TAP_TIME) {
      s.tapped = true;
      tapCar(s, TAP_CAR);
    }
  }

  // The ground follows its target on a stiff spring.
  const w0 = GROUND_OMEGA;
  s.gvx += (w0 * w0 * (s.tx - s.gx) - 2 * GROUND_ZETA * w0 * s.gvx) * dt;
  s.gvy += (w0 * w0 * (s.ty - s.gy) - 2 * GROUND_ZETA * w0 * s.gvy) * dt;
  s.gx += s.gvx * dt;
  s.gy += s.gvy * dt;

  const ax: number[] = [];
  const ay: number[] = [];
  for (let i = 0; i < COUNT; i++) {
    const w = OMEGAS[i];
    ax.push(-w * w * (s.px[i] - s.gx) - 2 * CAR_ZETA * w * (s.vx[i] - s.gvx));
    ay.push(-w * w * (s.py[i] - s.gy) - 2 * CAR_ZETA * w * (s.vy[i] - s.gvy));
  }
  for (let k = 0; k < PAIRS.length; k += 2) {
    const i = PAIRS[k];
    const j = PAIRS[k + 1];
    const nx = NORMALS[k];
    const ny = NORMALS[k + 1];
    const d = (s.px[j] - s.px[i]) * nx + (s.py[j] - s.py[i]) * ny;
    if (d >= 0) continue;
    const vi = s.vx[i] * nx + s.vy[i] * ny;
    const vj = s.vx[j] * nx + s.vy[j] * ny;
    const approach = Math.max(0, vi - vj);
    const f = CONTACT_K * -d + CONTACT_C * approach;
    ax[i] -= f * nx;
    ay[i] -= f * ny;
    ax[j] += f * nx;
    ay[j] += f * ny;
    // The car being run into is the one that complains.
    bump(s, vi > -vj ? j : i, approach);
  }

  for (let i = 0; i < COUNT; i++) {
    s.vx[i] += ax[i] * dt;
    s.vy[i] += ay[i] * dt;
    s.px[i] += s.vx[i] * dt;
    s.py[i] += s.vy[i] * dt;

    if (s.z[i] > 0 || s.vz[i] > 0) {
      s.vz[i] -= GRAVITY * dt;
      s.z[i] += s.vz[i] * dt;
      if (s.z[i] <= 0) {
        const impact = -s.vz[i];
        s.z[i] = 0;
        s.vz[i] = impact > 160 ? impact * 0.28 : 0;
        s.ve[i] += 0.0025 * impact;
        if (impact > 220) land(s, i, impact);
      }
    }

    const lean = LEAN_PER_POINT * (s.px[i] - s.gx);
    const wa = LEAN_OMEGA;
    s.va[i] += (-wa * wa * (s.a[i] - lean) - 2 * LEAN_ZETA * wa * s.va[i]) * dt;
    s.a[i] += s.va[i] * dt;

    const stretch = Math.min(0.4, 0.0024 * Math.abs(s.vx[i] - s.gvx)) - Math.min(0.18, 0.0006 * Math.abs(s.vz[i]));
    const we = STRETCH_OMEGA;
    s.ve[i] += (-we * we * (s.e[i] - stretch) - 2 * STRETCH_ZETA * we * s.ve[i]) * dt;
    s.e[i] = Math.min(0.7, Math.max(-0.35, s.e[i] + s.ve[i] * dt));

    s.honk[i] += dt;
    s.honkCool[i] -= dt;
    s.hopCool[i] -= dt;
  }
  s.steps += 1;
}

/** Steps until t seconds after the last reset; a gap longer than maxGap is skipped, not simulated. */
export function runSim(s: Sim, t: number, maxGap: number) {
  'worklet';
  const target = Math.floor(t / DT);
  if ((target - s.steps) * DT > maxGap) s.steps = target - Math.floor(maxGap / DT);
  while (s.steps < target) step(s);
}
