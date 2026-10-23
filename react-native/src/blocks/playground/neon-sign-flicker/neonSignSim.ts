/**
 * The sign's life: which words are switched on, how bright each tube is, and how the sign swings.
 *
 * Everything advances on one fixed tick (TICK) from one clock, so the flicker, the swing and the
 * self-playing script run the same, step for step, on any platform. A tube's brightness follows
 * a target that is a pure function of the time since its word was switched (ignition buzz, then a
 * warm-up from dim to full), plus a brown-out the script can trigger, plus the stutter of the one
 * faulty letter. The "random" in the buzz is a hash of the tube and the time slot, so it is the
 * same on every run.
 */

// The simulation tick, in seconds, and the most ticks one slow frame may run (a 50 ms cap).
export const TICK = 1 / 60;
export const MAX_TICKS = 3;

// autoPlay (seconds): the sign powers on word by word, browns out, is pushed to swing, "late" is
// switched off and on, then everything powers down and the sign hangs still again.
export const LOOP = 12;
const SCRIPT_OPEN_ON = 0.5;
const SCRIPT_GLASS_ON = 1.45;
const SCRIPT_LATE_ON = 2.15;
const SCRIPT_SAG = 4.7;
const SCRIPT_PUSH = 5.7;
const SCRIPT_LATE_OFF = 8.3;
const SCRIPT_LATE_BACK = 8.9;
const SCRIPT_ALL_OFF = [10.7, 10.85, 11.0];
// From here to the end of the loop the swing is damped harder, so it is still at the seam.
const SCRIPT_SETTLE = 9.5;
// The push, in rad/s: about 10° of swing.
const SCRIPT_PUSH_SPEED = 0.55;

// Tubes of a word light one after another, this far apart (s), and go out almost together.
const LETTER_STEP = 0.16;
const LETTER_STEP_OFF = 0.03;
// Ignition: the gas buzzes on and off in slots of 1/BUZZ_RATE s for IGNITE s, more often lit as
// it catches; then it warms from WARM_FROM to full with a time constant of WARM_TIME.
const IGNITE = 0.42;
const BUZZ_RATE = 30;
const WARM_FROM = 0.58;
const WARM_TIME = 0.55;
// The faulty letter: every STUTTER_EVERY s it may stutter (chance STUTTER_CHANCE) for
// STUTTER_LENGTH s, dropping out in slots of 1/STUTTER_RATE s; it also burns a little dimmer.
const STUTTER_EVERY = 1.6;
const STUTTER_CHANCE = 0.7;
const STUTTER_LENGTH = 0.42;
const STUTTER_RATE = 22;
const FAULTY_DIM = 0.86;
// A brown-out lasts SAG_TIME s.
const SAG_TIME = 0.75;
// The gas answers in a few ms: brightness chases its target at these rates (1/s).
const RISE_RATE = 55;
const FALL_RATE = 28;
// Reduced motion: no buzz or stutter, words fade in over this long (s).
const CALM_FADE = 0.45;

// The sign hangs on two chains from one hook: a pendulum with a period of PERIOD s, its swing
// losing half its size every 1.4 s (DAMPING), and much faster while the loop settles.
const PERIOD = 2.0;
const W0 = (2 * Math.PI) / PERIOD;
const DAMPING = 1.0;
const DAMPING_SETTLE = 2.4;

export const WORDS = 3;

export type NeonState = {
  /** Simulation time (s) and the script's clock (s, wraps at LOOP). */
  time: number;
  clock: number;
  /** Seconds left before the script resumes after a touch. */
  hold: number;
  looping: boolean;
  reduced: boolean;
  /** Per word: switched on (1) or off (0), and the time it was last switched. */
  on: number[];
  switchedAt: number[];
  /** Per piece: its word, its place in the word, and its brightness 0…1. */
  word: number[];
  order: number[];
  level: number[];
  /** The piece that stutters. */
  faulty: number;
  /** Time of the last brown-out. */
  sagAt: number;
  /** The swing: angle (rad, clockwise) and its speed (rad/s). */
  theta: number;
  omega: number;
  dragging: boolean;
};

export function createNeonState(word: number[], order: number[], faulty: number, looping: boolean, reduced: boolean): NeonState {
  'worklet';
  const n = word.length;
  const level: number[] = [];
  for (let i = 0; i < n; i += 1) level.push(0);
  return {
    time: 0,
    clock: 0,
    hold: 0,
    looping,
    reduced,
    on: [0, 0, 0],
    switchedAt: [-100, -100, -100],
    word: word.slice(),
    order: order.slice(),
    level,
    faulty,
    sagAt: -100,
    theta: 0,
    omega: 0,
    dragging: false,
  };
}

/** A hash of two integers to [0, 1): the same numbers give the same value on every platform. */
function hash(a: number, b: number) {
  'worklet';
  let h = (Math.imul(a | 0, 374761393) + Math.imul(b | 0, 668265263)) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  h ^= h >>> 16;
  return (h >>> 0) / 4294967296;
}

/** Switches a word on or off; does nothing when it already is. Returns whether it changed. */
export function setWord(st: NeonState, w: number, on: number) {
  'worklet';
  if (st.on[w] === on) return false;
  st.on[w] = on;
  st.switchedAt[w] = st.time;
  return true;
}

/** How bright the stuttering letter is, `age` s after its word lit. */
function stutter(i: number, age: number) {
  'worklet';
  const episode = Math.floor(age / STUTTER_EVERY);
  if (hash(i * 7 + 3, episode) > STUTTER_CHANCE) return 1;
  const start = 0.15 + 0.9 * hash(i * 7 + 5, episode);
  const into = age - episode * STUTTER_EVERY - start;
  if (into < 0 || into > STUTTER_LENGTH) return 1;
  return hash(i * 13 + 1, Math.floor(age * STUTTER_RATE)) > 0.45 ? 0.95 : 0.07;
}

/** The brown-out's dip, `dt` s after it began: two blinks and a slow recovery. */
function sag(dt: number) {
  'worklet';
  if (dt < 0 || dt > SAG_TIME) return 1;
  if (dt < 0.07) return 0.22;
  if (dt < 0.15) return 0.9;
  if (dt < 0.24) return 0.12;
  return 0.5 + 0.5 * ((dt - 0.24) / (SAG_TIME - 0.24));
}

/** What piece `i` is heading for right now. */
function target(st: NeonState, i: number) {
  'worklet';
  const w = st.word[i];
  if (st.on[w] === 0) {
    const age = st.time - st.switchedAt[w] - st.order[i] * LETTER_STEP_OFF;
    if (age < 0) return st.level[i];
    // A last gasp as the current dies.
    if (!st.reduced && age < 0.06) return 0.12;
    if (!st.reduced && age < 0.11) return 0.5;
    return 0;
  }
  const age = st.time - st.switchedAt[w] - st.order[i] * LETTER_STEP;
  if (age < 0) return 0;
  if (st.reduced) return Math.min(1, age / CALM_FADE);
  let b: number;
  if (age < IGNITE) {
    // Lit more often as the gas catches.
    const lit = hash(i * 31 + 17, Math.floor(age * BUZZ_RATE)) > 0.85 - (0.7 * age) / IGNITE;
    b = lit ? 0.45 + 0.35 * hash(i * 31 + 19, Math.floor(age * BUZZ_RATE)) : 0.03;
  } else {
    b = WARM_FROM + (1 - WARM_FROM) * (1 - Math.exp(-(age - IGNITE) / WARM_TIME));
  }
  if (i === st.faulty && age >= IGNITE) b *= FAULTY_DIM * stutter(i, age - IGNITE);
  return b * sag(st.time - st.sagAt);
}

function crossed(before: number, after: number, at: number) {
  'worklet';
  return before < at && after >= at;
}

/** Runs the script from `before` to `after` on the loop's clock. */
function script(st: NeonState, before: number, after: number) {
  'worklet';
  if (crossed(before, after, SCRIPT_OPEN_ON)) setWord(st, 0, 1);
  if (crossed(before, after, SCRIPT_GLASS_ON)) setWord(st, 2, 1);
  if (crossed(before, after, SCRIPT_LATE_ON)) setWord(st, 1, 1);
  if (crossed(before, after, SCRIPT_SAG)) st.sagAt = st.time;
  if (crossed(before, after, SCRIPT_PUSH) && !st.reduced) st.omega += SCRIPT_PUSH_SPEED;
  if (crossed(before, after, SCRIPT_LATE_OFF)) setWord(st, 1, 0);
  if (crossed(before, after, SCRIPT_LATE_BACK)) setWord(st, 1, 1);
  if (crossed(before, after, SCRIPT_ALL_OFF[0])) setWord(st, 2, 0);
  if (crossed(before, after, SCRIPT_ALL_OFF[1])) setWord(st, 0, 0);
  if (crossed(before, after, SCRIPT_ALL_OFF[2])) setWord(st, 1, 0);
}

/** One tick: the script, every tube's brightness, then the swing. */
export function stepNeon(st: NeonState) {
  'worklet';
  st.time += TICK;
  if (st.hold > 0) {
    st.hold -= TICK;
    if (st.hold <= 0) st.clock = 0;
  } else if (st.looping) {
    const before = st.clock;
    let after = before + TICK;
    if (after >= LOOP) {
      script(st, before, LOOP);
      after -= LOOP;
      script(st, -1, after);
    } else {
      script(st, before, after);
    }
    st.clock = after;
  }

  const rise = 1 - Math.exp(-RISE_RATE * TICK);
  const fall = 1 - Math.exp(-FALL_RATE * TICK);
  for (let i = 0; i < st.level.length; i += 1) {
    const goal = target(st, i);
    const l = st.level[i];
    st.level[i] = l + (goal - l) * (goal > l ? rise : fall);
  }

  if (st.dragging) return;
  if (st.reduced) {
    st.theta = 0;
    st.omega = 0;
    return;
  }
  const damping = st.looping && st.hold <= 0 && st.clock > SCRIPT_SETTLE ? DAMPING_SETTLE : DAMPING;
  const accel = -W0 * W0 * Math.sin(st.theta) - damping * st.omega;
  st.omega += accel * TICK;
  st.theta += st.omega * TICK;
}

/** Mean brightness of each word's tubes, for the light it throws. */
export function wordLevels(st: NeonState, out: number[]) {
  'worklet';
  for (let w = 0; w < WORDS; w += 1) out[w] = 0;
  const count = [0, 0, 0];
  for (let i = 0; i < st.level.length; i += 1) {
    out[st.word[i]] += st.level[i];
    count[st.word[i]] += 1;
  }
  for (let w = 0; w < WORDS; w += 1) out[w] = count[w] > 0 ? out[w] / count[w] : 0;
}

/**
 * The wall: brick albedo lit by a faint cold street light from above and by the sign's own glow.
 * The glow comes from a baked image holding each word's light in one channel (red = "open",
 * green = "late", blue = the glass) in the sign's frame, so the light swings with the sign.
 */
export const WALL_SKSL = `
uniform shader wall;
uniform shader spill;
uniform float2 pivotW;
uniform float2 pivotL;
uniform float2 rot;
uniform float3 level;
uniform float3 c0;
uniform float3 c1;
uniform float3 c2;
uniform float2 size;

half4 main(float2 p) {
  half3 albedo = wall.eval(p).rgb;
  float2 d = p - pivotW;
  float2 q = pivotL + float2(rot.x * d.x + rot.y * d.y, -rot.y * d.x + rot.x * d.y);
  half3 s = spill.eval(q).rgb;
  float3 light = s.r * level.x * c0 + s.g * level.y * c1 + s.b * level.z * c2;
  float v = p.y / size.y;
  float2 c = (p - 0.5 * size) / size;
  float ambient = (0.07 + 0.11 * (1.0 - v)) * (1.0 - 0.9 * dot(c, c));
  float3 col = float3(albedo) * (ambient * float3(0.78, 0.86, 1.0) + light * 2.4) + light * 0.035;
  col = col / (1.0 + 0.25 * col);
  return half4(half3(col), 1.0);
}
`;
