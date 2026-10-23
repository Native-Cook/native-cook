import { clamp, interpolateKeyframes, type Point, type Rect } from './model';

// Embellishments around the measured opening. Each is a pure function of the
// shared clocks, so holding a pose holds them too.
export const IDLE_CYCLE_MS = 4_600;
export const BURST_GLINT_COUNT = 26;
export const BURST_DURATION_MS = 980;
export const MAX_TEAR_SPARKS = 34;

const TWO_PI = Math.PI * 2;

/** Deterministic 0..1 value, so particles need no stored random state. */
export function hash01(index: number, salt: number) {
  'worklet';
  const value = Math.sin(index * 127.1 + salt * 311.7) * 43758.5453;
  return value - Math.floor(value);
}

function smooth(value: number) {
  'worklet';
  const t = clamp(value, 0, 1);
  return t * t * (3 - 2 * t);
}

/** A restart of the idle cycle `ms` into it; `count` tells one restart from the next. */
export type IdlePrime = Readonly<{ count: number; ms: number }>;

/** Slow breath of the glow behind an untouched pack; 0 at the cycle boundary. */
export function idleAuraBreath(phase: number) {
  'worklet';
  return Math.sin(phase * TWO_PI);
}

/** A diagonal sheen crosses the intact pack once per idle cycle. */
export function idleSheen(phase: number, pack: Rect) {
  'worklet';
  const travel = clamp((phase - 0.04) / 0.3, 0, 1);
  const centerX = pack.x + pack.width * (-0.3 + 1.6 * smooth(travel));
  const centerY = pack.y + pack.height * 0.5;
  const band = pack.width * 0.2;
  return {
    start: { x: centerX - band, y: centerY - band * 0.55 },
    end: { x: centerX + band, y: centerY + band * 0.55 },
    opacity: Math.sin(travel * Math.PI),
  };
}

/** A light runs along the tear line between sheens, showing where to swipe. */
export function idleSeamHint(phase: number, pack: Rect) {
  'worklet';
  const travel = clamp((phase - 0.46) / 0.36, 0, 1);
  const strength = Math.sin(travel * Math.PI);
  const head = pack.x + pack.width * (0.03 + 0.94 * smooth(travel));
  const tail = Math.max(pack.x, head - pack.width * 0.26 * strength);
  return { head, tail, opacity: Math.pow(strength, 0.7) };
}

export type TearSpark = Readonly<{
  x: number; y: number; vx: number; vy: number; age: number; life: number; size: number;
}>;

/** A spark thrown backward from the tear frontier. */
export function spawnTearSpark(
  origin: Point,
  seed: number,
  direction: 1 | -1,
  width: number,
): TearSpark {
  'worklet';
  const speed = width * (0.22 + hash01(seed, 1) * 0.78);
  const spread = (hash01(seed, 2) - 0.5) * 2.5;
  const angle = (direction === 1 ? Math.PI : 0) + direction * spread;
  return {
    x: origin.x,
    y: origin.y,
    vx: Math.cos(angle) * speed,
    vy: Math.sin(angle) * speed - width * 0.14,
    age: 0,
    life: 230 + hash01(seed, 3) * 330,
    size: width * (0.006 + hash01(seed, 4) * 0.008),
  };
}

export function stepTearSpark(spark: TearSpark, seconds: number, width: number): TearSpark | null {
  'worklet';
  const age = spark.age + seconds * 1000;
  if (age >= spark.life) return null;
  const drag = Math.max(0, 1 - 2.4 * seconds);
  return {
    x: spark.x + spark.vx * seconds,
    y: spark.y + spark.vy * seconds,
    vx: spark.vx * drag,
    vy: spark.vy * drag + width * 1.5 * seconds,
    age,
    life: spark.life,
    size: spark.size,
  };
}

export type BurstGlint = Readonly<{ x: number; y: number; size: number; rotation: number }>;

/** Glints thrown out of the cut. Time is relative to the commit flash. */
export function burstGlintAt(
  index: number,
  ms: number,
  pack: Rect,
  cutY: number,
  width: number,
  height: number,
): BurstGlint | null {
  'worklet';
  const age = ms - hash01(index, 1) * 80;
  const life = 430 + hash01(index, 2) * 520;
  if (age <= 0 || age >= life) return null;

  const u = 0.06 + 0.88 * hash01(index, 3);
  // Glints near either side lean outward, giving the burst a fan silhouette.
  const angle = -Math.PI / 2 + (u - 0.5) * 1.7 + (hash01(index, 4) - 0.5) * 0.8;
  const speed = width * (0.6 + hash01(index, 5) * 1.25);
  const seconds = age / 1000;
  const drag = 4.4;
  const travel = speed * (1 - Math.exp(-drag * seconds)) / drag;
  const progress = age / life;
  const twinkle = 0.72 + 0.28 * Math.sin(age / 38 + index * 1.7);
  return {
    x: pack.x + pack.width * u + Math.cos(angle) * travel,
    y: cutY + Math.sin(angle) * travel + 0.5 * height * 0.34 * seconds * seconds,
    size: width * (0.011 + hash01(index, 6) * 0.021) *
      Math.min(1, progress * 9) * Math.pow(1 - progress, 0.75) * twinkle,
    rotation: hash01(index, 7) * Math.PI + seconds * (hash01(index, 8) - 0.5) * 7,
  };
}

/** Soft ring that keeps expanding after the single-frame flash ring is gone. */
export function shockwaveAt(ms: number, width: number, height: number) {
  'worklet';
  const eased = 1 - Math.pow(1 - clamp(ms / 480, 0, 1), 3);
  return {
    radiusX: width * (0.4 + 0.52 * eased),
    radiusY: height * (0.05 + 0.105 * eased),
    strokeWidth: width * (0.034 - 0.027 * eased),
    opacity: interpolateKeyframes(ms, [0, 24, 130, 480], [0, 0.62, 0.36, 0]),
  };
}

/** Brief zoom of the whole scene on the commit frame. */
export function commitPunchScale(ms: number) {
  'worklet';
  if (ms <= 0 || ms >= 340) return 1;
  return 1 + 0.03 * clamp(ms / 40, 0, 1) * Math.exp(-ms / 78);
}

/** Light spilling from the mouth while the card rises and centers. */
export function cardRiseLightAt(ms: number) {
  'worklet';
  return interpolateKeyframes(ms, [780, 880, 1040, 1380, 1640], [0, 0.9, 0.78, 0.34, 0]);
}
