// Particle Ribbon Voice — the loop, the live state machine and the numbers the shader reads. Every frame of the
// autoplay is a pure function of the loop time t (no accumulated state), so the Swift and Kotlin ports can match it
// frame for frame; the live engine (a finger, a microphone) uses the same pulse, window and frame builders.
//
// What moves
//   sheet     half width Wd (pt): a base that springs between rest / thread / reply, plus the voice level
//   centre    a travelling sine, one whole wave every LOOP / 3 s
//   twist     the sheet's angle φ(x): the resting pattern (a thin gold tail, one long face-on stretch), a wound-up
//             thread while thinking, travelling twist pulses (one per syllable: from the left while the user speaks,
//             from the right while the assistant answers) and the finger's windows. cos φ = 0 is an edge-on crossing
//             and is a white line; cos φ = ±1 is the sheet face-on.
//   caption   line 2 follows the state: the question → "I'm listening" → three dots lit at each reversal of the
//             thread → the reply, one word each time a right-to-left twist crosses x = 201 → the question.
//
// Loop (LOOP = 9 s; the payoff is at 2.2 s):
//   t start | t end | element    | what                                                                | curve
//   0.00    | 0.55  | all        | rest: the opened ribbon, breathing (Wd 26 ± 4)                      | sin, 2 cycles
//   0.55    | 0.71  | caption    | question → "I'm listening", rising 6 pt                             | 160 ms
//   0.55    | 1.00  | sheet      | a twist leaves the left edge before any level exists                | pulse
//   0.78    | 3.00  | voice      | 8 syllables; level peaks 1.0 at 2.2 s (the gasp)                    | attack 70 ms, release 300 ms
//   3.00    | 3.35  | sheet      | release: Wd 26 → 3 (a thread), "I'm listening" → …                  | spring {20, 120, 1}
//   3.00    | 4.40  | centre     | the release's flick: one 80 pt bump (−14 pt) leaves x = 200 for the right at 260 pt/s | gaussian, 1.4 s
//   3.00    | 4.40  | thread     | the twist winds up to 6 half-turns that shuttle ±60 pt, 7 Hz / LOOP | sin; a dot lights at each reversal
//   4.40    | 6.55  | reply      | 4 syllables from the right edge; each word appears as its twist     | pulse, 260 pt/s
//           |       |            | crosses x = 201 (+0.93 s) and takes the ribbon's colour at 35 % for 240 ms |
//   6.60    | 7.40  | finger     | a finger parts the ribbon at x 300 → 175: face-on window, crossings | snap; the window follows 1 : 1
//           |       |            | at ±70 pt                                                           |
//   7.10    | 7.31  | caption    | the reply leaves, the question returns (600 ms after the width is back) | 100 ms out, 140 ms in
//   7.40    | 8.20  | sheet      | release: the crossings leave outward at ±260 pt/s and decay        | spring {20, 120, 1}
//   8.20    | 9.00  | all        | rest (= t 0; 0.55 + 0.8 = 15 % of the loop)                         | —

import { ref } from './prv-theme';

export const LOOP = 9;

export const T = {
  press: 0.55,
  release: 3.0,
  speak: 4.4,
  idleBack: 6.5,
  captionBack: 7.1, // 600 ms after the width is back (Pro layer 3)
  grab: 6.6,
  letGo: 7.4,
  thinkIn: 3.05,
  thinkOut: 4.4,
} as const;

/** Spring presets (DESIGN-SYSTEM §8 shape: response s, damping ratio ζ; mass 1). {damping 20, stiffness 120} is the concept's. */
export const SPRING = {
  width: { response: 0.574, zeta: 0.913 }, // { damping: 20, stiffness: 120, mass: 1 }
  thread: { response: 0.5, zeta: 1 },
  window: { response: 0.28, zeta: 0.9 }, // snap: the window forms in 0.26 s
  windowOut: { response: 0.5, zeta: 1 }, // the 320 ms release
} as const;

/** Reanimated configs for the finger's windows and the reduced-motion ease. */
export const springCfg = (response: number, zeta: number) => {
  'worklet';
  const k = Math.pow((2 * Math.PI) / response, 2);
  return { stiffness: k, damping: 2 * zeta * Math.sqrt(k), mass: 1 };
};

export const GESTURE = {
  hit: 70, // pt either side of the centre line that grabs the sheet (design pt)
  window: 70, // half width of the finger's window
  openMax: 26, // extra half width at |dy| ≥ openDy
  openDy: 90,
  levelPerPt: 220, // demo: finger height over the press → level 0 … 1 (design pt)
  levelBase: 0.25,
  cancelBelow: 0.25, // s: a shorter press only launches a twist
  resumeAfter: 2, // s: autoplay restarts this long after the last touch
  resumeEase: 0.6,
  speed: 260, // pt/s: a twist's speed
  bumpPerVel: 40, // px/s of finger speed per pt of bump height (Pro layer 5)
  bumpMax: 22, // pt
  bumpWidth: 80, // pt, half width
  bumpLife: 1.4, // s
  think: 1.4, // s: how long the demo thinks
  replyEnd: 2.1, // s after the reply starts
} as const;

// ------------------------------------------------------------------------------------------------ math

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}
export function mix(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}
export function smooth(a: number, b: number, x: number) {
  'worklet';
  const t = clamp01((x - a) / (b - a));
  return t * t * (3 - 2 * t);
}
/** Step response of a spring (DESIGN-SYSTEM §8): 0 → 1. */
export function springAt(t: number, response: number, zeta: number) {
  'worklet';
  if (t <= 0) return 0;
  const w = (2 * Math.PI) / response;
  if (zeta >= 1) return 1 - Math.exp(-w * t) * (1 + w * t);
  const wd = w * Math.sqrt(1 - zeta * zeta);
  return 1 - Math.exp(-zeta * w * t) * (Math.cos(wd * t) + ((zeta * w) / wd) * Math.sin(wd * t));
}
/** A level swell: a fast gaussian attack and an exponential release. */
function swell(tau: number, rise: number, fall: number) {
  'worklet';
  return tau < 0 ? Math.exp(-(tau / rise) * (tau / rise)) : Math.exp(-tau / fall);
}

// ------------------------------------------------------------------------------------------------ the voice

/** The user's phrase: [time, level]. A swell attacks in 70 ms and falls in 300 ms; swells combine as 1 − Π(1 − s). */
const USER: readonly (readonly [number, number])[] = [
  [0.78, 0.34],
  [1.02, 0.42],
  [1.3, 0.55],
  [1.62, 0.74],
  [1.92, 0.9],
  [2.2, 1.0],
  [2.5, 0.8],
  [2.78, 0.58],
];
/** The reply: [time its twist leaves the right edge, level]. */
const REPLY: readonly (readonly [number, number])[] = [
  [4.42, 0.55],
  [4.82, 0.7],
  [5.17, 0.6],
  [5.52, 0.5],
];
const X_WORD = 201; // a word appears when its twist crosses the middle
const X_RIGHT = 444;
const X_LEFT = -42;

function userLevel(t: number) {
  'worklet';
  let keep = 1;
  for (let i = 0; i < USER.length; i++) keep *= 1 - USER[i][1] * swell(t - USER[i][0], 0.07, 0.3);
  return 1 - keep;
}
function replyLevel(t: number) {
  'worklet';
  let keep = 1;
  for (let i = 0; i < REPLY.length; i++) keep *= 1 - REPLY[i][1] * swell(t - (REPLY[i][0] + 0.05), 0.07, 0.3);
  return 1 - keep;
}

// ------------------------------------------------------------------------------------------------ the frame

/** Everything the shader reads, in the shader's own uniform shapes (prv-shaders.ts). */
export type Frame = {
  phase: number; // loop phase 0 … 1 (whole cycles only)
  line: number[]; // y0, amplitude, wavelength, wave phase (cycles)
  sheet: number[]; // Wd, level, gain, bloom
  twA: number[]; // idle twist: amplitude, length, phase, slope
  twB: number[]; // tails: left, right, width, 0
  think: number[]; // amount, centre x, pitch, 0
  pul: number[]; // 8 × [x, amplitude, half width, velocity (unused by the shader; the hand-off reads it)]
  win: number[]; // 2 × [x, strength, face-on angle, half width]
  winB: number[]; // 2 × [finger y, opening, bulge, 0]
  bump: number[]; // 2 × [x, height (pt, signed), half width, velocity]: a flick's transverse bump on the centre line
  opt: number[]; // dust, loosening, p3, lift
  extra: number[]; // direction cue (reduced motion), 0, 0, 0
};

const IDLE = {
  amp: ref.twist.amp,
  length: ref.twist.length,
  phase: ref.twist.phase,
  slope: ref.twist.slope,
  tl: ref.twist.tailLeft,
  tr: ref.twist.tailRight,
  tw: ref.twist.tailWidth,
} as const;
const W_IDLE = ref.ribbon.wdIdle;
const W_THREAD = ref.ribbon.wdThread;
const W_SPEAK = ref.ribbon.wdSpeak;
const WD_LEVEL = ref.ribbon.wdLevel;
const Y0 = ref.ribbon.y0;
const AMP0 = ref.ribbon.amp;
const AMP_LEVEL = ref.ribbon.ampLevel;
const LAMBDA = ref.ribbon.wavelength;
const WAVE_P0 = ref.ribbon.waveP0;
const THINK_PITCH = (6 * Math.PI) / 402;
const THINK_F = 7 / LOOP; // shuttle: 7 cycles per loop

export function emptyPulses() {
  'worklet';
  return [0, 0, 40, 0, 0, 0, 40, 0, 0, 0, 40, 0, 0, 0, 40, 0, 0, 0, 40, 0, 0, 0, 40, 0, 0, 0, 40, 0, 0, 0, 40, 0];
}
export function emptyWin() {
  'worklet';
  return [0, 0, 0, GESTURE.window, 0, 0, 0, GESTURE.window];
}
export function emptyBump() {
  'worklet';
  return [0, 0, GESTURE.bumpWidth, 0, 0, 0, GESTURE.bumpWidth, 0];
}

type Params = {
  phase: number;
  waveP: number;
  amp: number;
  wd: number;
  level: number;
  idleW: number;
  idleP: number;
  think: number;
  thinkU0: number;
  pul: number[];
  win: number[];
  winB: number[];
  bump: number[];
  dir: number;
  loose: number;
  gain: number;
};

export function buildFrame(p: Params): Frame {
  'worklet';
  const iw = p.idleW;
  return {
    phase: p.phase,
    line: [Y0, p.amp, LAMBDA, p.waveP],
    sheet: [p.wd, p.level, p.gain, 0.7],
    twA: [IDLE.amp * iw, IDLE.length, p.idleP, IDLE.slope * iw],
    twB: [IDLE.tl * iw, IDLE.tr * iw, IDLE.tw, 0],
    think: [p.think, p.thinkU0, THINK_PITCH, 0],
    pul: p.pul,
    win: p.win,
    winB: p.winB,
    bump: p.bump,
    opt: [1, p.loose, 0, 0],
    extra: [p.dir, 0, 0, 0],
  };
}

/** The centre line at x (design pt) for a frame: the twin of lineAt() in the shader, without the finger's bulge. */
export function lineY(x: number, f: Frame) {
  'worklet';
  let y = f.line[0] + f.line[1] * Math.cos(2 * Math.PI * (x / f.line[2] - f.line[3]));
  for (let k = 0; k < 2; k++) {
    const d = (x - f.bump[k * 4]) / Math.max(f.bump[k * 4 + 2], 1);
    y += f.bump[k * 4 + 1] * Math.exp(-d * d);
  }
  return y;
}

/** The twist angle without the finger's windows: the twin of twistAt() in the shader (the windows latch from it). */
export function twistBase(u: number, f: Frame) {
  'worklet';
  let a = f.twA[0] * Math.sin((2 * Math.PI * u) / f.twA[1] + f.twA[2]) + f.twA[3] * ((u - 200) / 402);
  a += f.twB[0] * Math.exp(-((u / f.twB[2]) * (u / f.twB[2]))) + f.twB[1] * Math.exp(-(((402 - u) / f.twB[2]) * ((402 - u) / f.twB[2])));
  a += f.think[0] * (u - f.think[1]) * f.think[2];
  for (let i = 0; i < 8; i++) {
    const x = (u - f.pul[i * 4]) / Math.max(f.pul[i * 4 + 2], 1);
    a += f.pul[i * 4 + 1] * Math.exp(-x * x);
  }
  return a;
}

function faceOn(angle: number) {
  'worklet';
  return Math.round(angle / Math.PI) * Math.PI;
}

// ------------------------------------------------------------------------------------------------ autoplay

type Pul = { t0: number; x0: number; v: number; a0: number; w: number; tau: number; life: number; rise: number };

const C = GESTURE.speed;
const FINGER = { x0: 300, x1: 175 };
/** All the twists of the loop, in the order they are born. */
const AUTO: Pul[] = [];
AUTO.push({ t0: T.press, x0: X_LEFT, v: C, a0: 0.9 * Math.PI, w: 64, tau: 0.9, life: 1.5, rise: 0 });
for (let i = 0; i < USER.length; i++) {
  AUTO.push({ t0: USER[i][0] - 0.18, x0: X_LEFT, v: C, a0: Math.PI * (0.3 + 0.55 * USER[i][1]), w: 64, tau: 0.9, life: 1.5, rise: 0 });
}
for (let i = 0; i < REPLY.length; i++) {
  AUTO.push({ t0: REPLY[i][0], x0: X_RIGHT, v: -C, a0: Math.PI * (0.3 + 0.55 * REPLY[i][1]), w: 64, tau: 0.9, life: 1.3, rise: 0 });
}
AUTO.push({ t0: T.letGo, x0: FINGER.x1 - GESTURE.window, v: -C, a0: 0.7 * Math.PI, w: 40, tau: 0.45, life: 1.2, rise: 0.12 });
AUTO.push({ t0: T.letGo, x0: FINGER.x1 + GESTURE.window, v: C, a0: 0.7 * Math.PI, w: 40, tau: 0.45, life: 1.2, rise: 0.12 });

/** The loop's flicks: [t0, x0, velocity, signed height, half width, life] (one, at the release: the thread is flicked toward the speaking side). */
const AUTO_BUMP: readonly (readonly number[])[] = [[T.release, 200, C, -14, GESTURE.bumpWidth, GESTURE.bumpLife]];

/** A bump rises in 80 ms and leaves over its last 0.5 s. */
function bumpGate(age: number, life: number) {
  'worklet';
  return smooth(0, 0.08, age) * (1 - smooth(life - 0.5, life, age));
}

function bumpsAt(t: number): number[] {
  'worklet';
  const out = emptyBump();
  let slot = 0;
  for (let i = AUTO_BUMP.length - 1; i >= 0 && slot < 2; i--) {
    const b = AUTO_BUMP[i];
    const age = t - b[0];
    if (age < 0 || age > b[5]) continue;
    out[slot * 4] = b[1] + b[2] * age;
    out[slot * 4 + 1] = b[3] * bumpGate(age, b[5]);
    out[slot * 4 + 2] = b[4];
    out[slot * 4 + 3] = b[2];
    slot++;
  }
  return out;
}

/** When each reply word appears (the loop's table; the same numbers drive the live engine). */
export const WORD_AT: readonly number[] = REPLY.map((r) => r[0] + (X_RIGHT - X_WORD) / C);

function pulsesAt(t: number): number[] {
  'worklet';
  const out = emptyPulses();
  let slot = 0;
  for (let i = AUTO.length - 1; i >= 0 && slot < 8; i--) {
    const p = AUTO[i];
    const age = t - p.t0;
    if (age < 0 || age > p.life) continue;
    const gate = smooth(0, Math.max(p.rise, 1e-3), age) * (1 - smooth(p.life - 0.5, p.life, age));
    out[slot * 4] = p.x0 + p.v * age;
    out[slot * 4 + 1] = p.a0 * Math.exp(-age / p.tau) * gate;
    out[slot * 4 + 2] = p.w;
    out[slot * 4 + 3] = p.v;
    slot++;
  }
  return out;
}

function baseWidth(t: number) {
  'worklet';
  const s = SPRING.width;
  return (
    W_IDLE +
    (W_THREAD - W_IDLE) * springAt(t - T.release, s.response, s.zeta) +
    (W_SPEAK - W_THREAD) * springAt(t - T.speak, s.response, s.zeta) +
    (W_IDLE - W_SPEAK) * springAt(t - T.idleBack, s.response, s.zeta)
  );
}

/** The finger of the parting beat: x, y (design pt) and the window's strength. */
function fingerAt(t: number, c: number) {
  'worklet';
  const k = smooth(6.75, 7.3, t);
  const e = k * k * (3 - 2 * k);
  const x = FINGER.x0 + (FINGER.x1 - FINGER.x0) * e;
  const dy = -70 * smooth(6.7, 7.05, t) + 50 * smooth(7.1, 7.38, t);
  const sIn = springAt(t - T.grab, SPRING.window.response, SPRING.window.zeta);
  const sOut = springAt(t - T.letGo, SPRING.windowOut.response, SPRING.windowOut.zeta);
  return { x, dy, s: clamp01(sIn - sOut), c };
}

/** The loop at time t without the finger: the sheet, the twists, the thread. */
function frameNoWindow(tIn: number): Frame {
  'worklet';
  const t = ((tIn % LOOP) + LOOP) % LOOP;
  const lu = userLevel(t);
  const lr = replyLevel(t);
  const level = 1 - (1 - lu) * (1 - lr);
  const base = baseWidth(t);
  const idleW = clamp01((base - W_THREAD) / (W_IDLE - W_THREAD));
  const wd = base + 4 * Math.sin((2 * Math.PI * 2 * t) / LOOP) * idleW + WD_LEVEL * (lu + lr);
  const levelLag = 1 - (1 - userLevel(t - 0.2)) * (1 - replyLevel(t - 0.2));
  const sp = SPRING.thread;
  const think = springAt(t - T.thinkIn, sp.response, sp.zeta) - springAt(t - T.thinkOut, sp.response, sp.zeta);
  return buildFrame({
    phase: t / LOOP,
    waveP: WAVE_P0 + (3 * t) / LOOP,
    amp: AMP0 + AMP_LEVEL * levelLag,
    wd,
    level,
    idleW,
    idleP: IDLE.phase + (2 * Math.PI * t) / LOOP,
    think: clamp01(think),
    thinkU0: 201 + 60 * Math.sin(2 * Math.PI * THINK_F * (t - T.release)),
    pul: pulsesAt(t),
    win: emptyWin(),
    winB: [0, 0, 0, 0, 0, 0, 0, 0],
    bump: bumpsAt(t),
    dir: 0,
    loose: 0.3 + 0.7 * level,
    gain: 1,
  });
}

/** The parting beat's face-on angle: the field at the grab, a constant of the loop. */
export const GRAB_FACE_ON = faceOn(twistBase(FINGER.x0, frameNoWindow(T.grab)));

/** The loop at time t (any real; it wraps). */
export function frameAt(tIn: number): Frame {
  'worklet';
  const t = ((tIn % LOOP) + LOOP) % LOOP;
  const f = frameNoWindow(t);
  const fg = fingerAt(t, lineY(FINGER.x0, f));
  if (fg.s > 0.001) {
    const cf = lineY(fg.x, f);
    f.win = [fg.x, fg.s, GRAB_FACE_ON, GESTURE.window, 0, 0, 0, GESTURE.window];
    f.winB = [cf + fg.dy, GESTURE.openMax * clamp01(Math.abs(fg.dy) / GESTURE.openDy), 0.3, 0, 0, 0, 0, 0];
  }
  return f;
}

// ------------------------------------------------------------------------------------------------ the caption

/** Line 2's layers, 0 … 1 each: [question, listening, dot 1, dot 2, dot 3, word 1 … word 7, tint of word 1 … tint of word 7]. */
export const CAPTION_LAYERS = 19;
/** The most words a reply may have: one per twist that crosses the middle. */
export const MAX_WORDS = 7;
/** A new word takes the ribbon's colour beneath it for this long (s), at `TINT` of the way, then settles to white. */
export const TINT_FOR = 0.24;
export const TINT = 0.35;

/** A line leaves in 100 ms; the next one arrives 70 ms into that, in 140 ms, rising 6 pt: two lines are never both legible. */
const OUT = 0.1;
const IN = 0.14;
function outAt(t: number, a: number) {
  'worklet';
  return smooth(a, a + OUT, t);
}
function inAt(t: number, a: number) {
  'worklet';
  return smooth(a + 0.7 * OUT, a + 0.7 * OUT + IN, t);
}

export function captionAt(tIn: number): number[] {
  'worklet';
  const t = ((tIn % LOOP) + LOOP) % LOOP;
  const out = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0];
  out[0] = clamp01(1 - outAt(t, T.press) + inAt(t, T.captionBack));
  out[1] = inAt(t, T.press) * (1 - outAt(t, T.release));
  const dots = inAt(t, T.release) * (1 - outAt(t, T.speak));
  out[2] = dots * smooth(T.release + 0.12, T.release + 0.2, t);
  out[3] = dots * smooth(T.release + 0.25 / THINK_F, T.release + 0.25 / THINK_F + 0.08, t);
  out[4] = dots * smooth(T.release + 0.75 / THINK_F, T.release + 0.75 / THINK_F + 0.08, t);
  const words = 1 - outAt(t, T.captionBack);
  for (let i = 0; i < WORD_AT.length; i++) {
    out[5 + i] = words * smooth(WORD_AT[i], WORD_AT[i] + 0.06, t);
    out[5 + MAX_WORDS + i] = t >= WORD_AT[i] ? out[5 + i] * (1 - smooth(WORD_AT[i], WORD_AT[i] + TINT_FOR, t)) : 0;
  }
  return out;
}

// ------------------------------------------------------------------------------------------------ the request

/** The request heard in the loop, one entry per word: its syllables (8 in all, the voice's 0.78 … 3.00 s). */
export const ASK_SYLLABLES: readonly number[] = [1, 1, 2, 1, 1, 1, 1]; // Book · a · ta-ble · for · two · at · eight
const ASK_FROM = 0.78;
const ASK_STEP = (T.release - ASK_FROM) / 8;
/** [word 1 … word 7, the talk control pressed]: a word shows as its first syllable is heard; all leave with the reply. */
export const ASK_LAYERS = MAX_WORDS + 1;

export function askAt(tIn: number): number[] {
  'worklet';
  const t = ((tIn % LOOP) + LOOP) % LOOP;
  const out = [0, 0, 0, 0, 0, 0, 0, 0];
  const gone = 1 - smooth(T.captionBack, T.captionBack + 0.2, t);
  let heard = 0;
  for (let i = 0; i < ASK_SYLLABLES.length && i < MAX_WORDS; i++) {
    const at = ASK_FROM + heard * ASK_STEP;
    out[i] = smooth(at, at + 0.12, t) * gone;
    heard += ASK_SYLLABLES[i];
  }
  out[MAX_WORDS] = smooth(T.press - 0.06, T.press + 0.06, t) * (1 - smooth(T.release - 0.06, T.release + 0.06, t));
  return out;
}

/** What the ribbon's state is at loop time t (for the accessibility value and the haptic-free autoplay). */
export function stateAt(tIn: number): 0 | 1 | 2 | 3 {
  'worklet';
  const t = ((tIn % LOOP) + LOOP) % LOOP;
  if (t >= T.press && t < T.release) return 1;
  if (t >= T.release && t < T.speak) return 2;
  if (t >= T.speak && t < T.idleBack) return 3;
  return 0;
}

// ------------------------------------------------------------------------------------------------ live

/** Pulses in the live engine: 8 × [x, v, a, w, age, tau, life, speaking]. */
const PL = 8;
/** Windows: 2 × [on, id, x, xT, s, sV, theta, y, open, vx, ready]. */
const WL = 11;
/** Bumps in the live engine: 2 × [x, v, height, half width, age, life]. */
const BL = 6;

export type Live = {
  t: number;
  mode: 0 | 1 | 2 | 3; // idle, listening, thinking, speaking
  modeT: number;
  talk: number; // 1 while a finger holds the talk
  talkY0: number;
  talkLevel: number; // the finger's level, 0 … 1
  hold: number;
  level: number;
  base: number;
  baseV: number;
  think: number;
  thinkV: number;
  waveP: number;
  idleP: number;
  acc: number;
  pul: number[];
  win: number[];
  bump: number[];
  talkX: number; // the talking finger's last x, y and its smoothed vertical speed (design pt/s): a release flicks a bump
  talkY: number;
  talkT: number;
  talkVy: number;
  sg: number[]; // the sides of each finger of a pair of windows (a crossing passing under the other finger flips one)
  cross: number; // counts those flips (the component turns each into a haptic)
  words: number; // how many reply words have appeared
  cap: number[]; // CAPTION_LAYERS smoothed layer values
  dots: number; // dots lit
  quiet: number; // seconds since the last touch
  gainIn: number; // the finger's brightness lift wanted, 0 or 1
  gain: number; // … and the lift drawn (eased: 60 ms in, 320 ms out)
  fromMode: number; // the mode before this one (the question waits 600 ms after a reply)
  wordT: number[]; // when each reply word appeared (s.t), −1 before
  res: number; // the last gesture's answer (read back by the component)
};

export function liveRest(): Live {
  'worklet';
  const f0 = frameAt(0);
  return {
    t: 0,
    mode: 0,
    modeT: 0,
    talk: 0,
    talkY0: 0,
    talkLevel: 0,
    hold: 0,
    level: 0,
    base: W_IDLE,
    baseV: 0,
    think: 0,
    thinkV: 0,
    waveP: f0.line[3],
    idleP: f0.twA[2],
    acc: 0,
    pul: new Array(PL * 8).fill(0),
    win: new Array(2 * WL).fill(0),
    bump: [0, 0, 0, GESTURE.bumpWidth, 99, 1, 0, 0, 0, GESTURE.bumpWidth, 99, 1],
    talkX: 0,
    talkY: 0,
    talkT: 0,
    talkVy: 0,
    sg: [0, 0, 0, 0],
    cross: 0,
    words: 0,
    cap: captionAt(0),
    dots: 0,
    quiet: 0,
    gainIn: 0,
    gain: 0,
    fromMode: 0,
    wordT: [-1, -1, -1, -1, -1, -1, -1],
    res: 0,
  };
}

/** Take the live state over from the loop at time t (a touch during autoplay): nothing jumps. */
export function liveFromLoop(s: Live, tIn: number) {
  'worklet';
  const t = ((tIn % LOOP) + LOOP) % LOOP;
  const f = frameAt(t);
  s.t = t;
  s.mode = stateAt(t);
  s.modeT = 0;
  s.level = f.sheet[1];
  s.base = baseWidth(t);
  s.baseV = 0;
  s.think = f.think[0];
  s.thinkV = 0;
  s.waveP = f.line[3];
  s.idleP = f.twA[2];
  s.acc = 0;
  s.talk = 0;
  s.hold = 0;
  s.words = 0;
  s.wordT = [-1, -1, -1, -1, -1, -1, -1];
  s.fromMode = s.mode;
  s.gain = 0;
  for (let i = 0; i < PL; i++) {
    const a = f.pul[i * 4 + 1];
    const on = Math.abs(a) > 0.01 ? 1 : 0;
    s.pul[i * 8] = f.pul[i * 4];
    s.pul[i * 8 + 1] = f.pul[i * 4 + 3];
    s.pul[i * 8 + 2] = a;
    s.pul[i * 8 + 3] = f.pul[i * 4 + 2];
    s.pul[i * 8 + 4] = on ? 0.2 : 9;
    s.pul[i * 8 + 5] = 0.9;
    s.pul[i * 8 + 6] = 1.5;
    s.pul[i * 8 + 7] = f.pul[i * 4 + 3] < 0 ? 1 : 0;
  }
  for (let i = 0; i < 2 * WL; i++) s.win[i] = 0;
  for (let k = 0; k < 2; k++) {
    const h = f.bump[k * 4 + 1];
    const on = Math.abs(h) > 0.05;
    s.bump[k * BL] = f.bump[k * 4];
    s.bump[k * BL + 1] = f.bump[k * 4 + 3];
    s.bump[k * BL + 2] = h;
    s.bump[k * BL + 3] = f.bump[k * 4 + 2];
    s.bump[k * BL + 4] = on ? 0.3 : 99;
    s.bump[k * BL + 5] = GESTURE.bumpLife;
  }
  s.talkVy = 0;
  s.sg = [0, 0, 0, 0];
  const cap = captionAt(t);
  for (let i = 0; i < CAPTION_LAYERS; i++) s.cap[i] = cap[i];
  s.dots = 0;
  s.quiet = 0;
  s.gainIn = 0;
}

function emit(s: Live, x0: number, v: number, a: number, w: number, tau: number, life: number, speaking: number) {
  'worklet';
  // reuse a dead slot, else the oldest
  let k = 0;
  let oldest = -1;
  for (let i = 0; i < PL; i++) {
    const age = s.pul[i * 8 + 4];
    if (age >= s.pul[i * 8 + 6]) {
      k = i;
      oldest = 1e9;
      break;
    }
    if (age > oldest) {
      oldest = age;
      k = i;
    }
  }
  const o = k * 8;
  s.pul[o] = x0;
  s.pul[o + 1] = v;
  s.pul[o + 2] = a;
  s.pul[o + 3] = w;
  s.pul[o + 4] = 0;
  s.pul[o + 5] = tau;
  s.pul[o + 6] = life;
  s.pul[o + 7] = speaking;
}

/** Launch a transverse bump from x (design pt) toward the speaking side (Pro layer 5). */
function emitBump(s: Live, x0: number, v: number, h: number, w: number) {
  'worklet';
  let k = 0;
  let oldest = -1;
  for (let i = 0; i < 2; i++) {
    const age = s.bump[i * BL + 4];
    if (age >= s.bump[i * BL + 5]) {
      k = i;
      break;
    }
    if (age > oldest) {
      oldest = age;
      k = i;
    }
  }
  const o = k * BL;
  s.bump[o] = x0;
  s.bump[o + 1] = v;
  s.bump[o + 2] = h;
  s.bump[o + 3] = w;
  s.bump[o + 4] = 0;
  s.bump[o + 5] = GESTURE.bumpLife;
}

/** Set the mode (a prop, or the demo's flow). */
export function liveMode(s: Live, mode: 0 | 1 | 2 | 3) {
  'worklet';
  if (s.mode === mode) return;
  s.fromMode = s.mode;
  s.mode = mode;
  s.modeT = 0;
  s.acc = 0.55; // the first syllable of a phrase leaves almost at once
  if (mode === 3) {
    s.words = 0;
    for (let i = 0; i < MAX_WORDS; i++) s.wordT[i] = -1;
  }
  if (mode === 2) s.dots = 0;
}

/** A finger comes down (design pt). `parts` allows the sheet to be grabbed. Returns 1 for talk, 2 for part, 0 for nothing. */
export function liveDown(s: Live, id: number, x: number, y: number, c: number, parts: boolean, twist: number) {
  'worklet';
  s.quiet = 0;
  if (parts && Math.abs(y - c) <= GESTURE.hit && x > 4 && x < 398) {
    for (let k = 0; k < 2; k++) {
      if (s.win[k * WL] === 0) {
        const o = k * WL;
        s.win[o] = 1;
        s.win[o + 1] = id;
        s.win[o + 2] = x;
        s.win[o + 3] = x;
        s.win[o + 4] = 0;
        s.win[o + 5] = 0;
        s.win[o + 6] = faceOn(twist);
        s.win[o + 7] = y;
        s.win[o + 8] = 0;
        s.win[o + 9] = 0;
        s.win[o + 10] = s.t;
        return 2;
      }
    }
  }
  if (s.talk === 0) {
    s.talk = id + 1;
    s.talkY0 = y;
    s.talkX = x;
    s.talkY = y;
    s.talkT = s.t;
    s.talkVy = 0;
    s.talkLevel = clamp01(GESTURE.levelBase);
    s.hold = 0;
    s.gainIn = 1;
    emit(s, X_LEFT, C, 0.9 * Math.PI, 64, 0.9, 1.5, 0);
    liveMode(s, 1);
    return 1;
  }
  return 0;
}

export function liveMove(s: Live, id: number, x: number, y: number, c: number, vx: number) {
  'worklet';
  s.quiet = 0;
  for (let k = 0; k < 2; k++) {
    const o = k * WL;
    if (s.win[o] === 1 && s.win[o + 1] === id) {
      const nx = Math.min(Math.max(x, 20), 382);
      // the finger's horizontal speed (design pt/s), smoothed: the crossings it lets go of leave with it
      const dtv = s.t - s.win[o + 10];
      if (dtv > 0.004) {
        s.win[o + 9] += ((nx - s.win[o + 3]) / dtv - s.win[o + 9]) * 0.35;
        s.win[o + 10] = s.t;
      } else if (vx !== 0) s.win[o + 9] = vx;
      s.win[o + 3] = nx;
      s.win[o + 7] = y;
      s.win[o + 8] = GESTURE.openMax * clamp01(Math.abs(y - c) / GESTURE.openDy);
      return;
    }
  }
  if (s.talk === id + 1) {
    s.talkLevel = clamp01((s.talkY0 - y) / GESTURE.levelPerPt + GESTURE.levelBase);
    s.talkX = x;
    // the finger's vertical speed, smoothed over a few events (the flick on release reads it)
    const dtv = s.t - s.talkT;
    if (dtv > 0.004) {
      s.talkVy += ((y - s.talkY) / dtv - s.talkVy) * 0.35;
      s.talkY = y;
      s.talkT = s.t;
    }
  }
}

/** A finger leaves. Returns 1 when a talk ended long enough to count (the demo then thinks). */
export function liveUp(s: Live, id: number, demoFlow: boolean) {
  'worklet';
  s.quiet = 0;
  for (let k = 0; k < 2; k++) {
    const o = k * WL;
    if (s.win[o] === 1 && s.win[o + 1] === id) {
      // the window goes: the crossings leave in opposite directions at 260 pt/s plus the finger's vx, and decay
      s.win[o] = 2;
      const xc = s.win[o + 2];
      const vx = s.win[o + 9];
      emit(s, xc - GESTURE.window, -C + 0.4 * vx, 0.7 * Math.PI, 40, 0.45, 1.2, 0);
      emit(s, xc + GESTURE.window, C + 0.4 * vx, 0.7 * Math.PI, 40, 0.45, 1.2, 0);
      return 0;
    }
  }
  if (s.talk === id + 1) {
    s.talk = 0;
    s.gainIn = 0;
    if (s.hold < GESTURE.cancelBelow) {
      liveMode(s, 0);
      return 0;
    }
    // the release's flick: the finger's vertical speed launches one bump toward the speaking side while the width falls
    const h = Math.max(-GESTURE.bumpMax, Math.min(GESTURE.bumpMax, s.talkVy / GESTURE.bumpPerVel));
    if (Math.abs(h) >= 1.5) emitBump(s, s.talkX, C, h, GESTURE.bumpWidth);
    s.talkVy = 0;
    if (demoFlow) liveMode(s, 2);
    return 1;
  }
  return 0;
}

const SPEAK_AT = REPLY.map((r) => r[0] - REPLY[0][0]);

/** Advance the live state by dt seconds. `ext` ≥ 0 overrides the level (a microphone or the player). */
export function liveStep(s: Live, dtIn: number, demoFlow: boolean, ext: number, speed: number) {
  'worklet';
  const dt = Math.min(Math.max(dtIn, 0), 0.05);
  const prevT = s.modeT;
  s.t += dt;
  s.modeT += dt;
  s.quiet += s.talk === 0 && s.win[0] !== 1 && s.win[WL] !== 1 ? dt : 0;
  if (s.talk > 0) {
    s.hold += dt;
    s.talkVy *= Math.exp(-dt / 0.12); // a finger that stops is not flicking
  }

  // the demo's flow: thinking → speaking → idle
  if (demoFlow) {
    if (s.mode === 2 && s.modeT >= GESTURE.think) liveMode(s, 3);
    if (s.mode === 3 && s.modeT >= GESTURE.replyEnd + 0.9) liveMode(s, 0);
  }

  // level: the finger's (or the microphone's) while listening, the reply's script while speaking
  let target = 0;
  if (s.mode === 1) target = ext >= 0 ? ext : s.talkLevel;
  else if (s.mode === 3) target = ext >= 0 ? ext : replyLevel(s.modeT + REPLY[0][0]) * 1;
  const tau = target > s.level ? 0.06 : 0.32;
  s.level += (target - s.level) * (1 - Math.exp(-dt / tau));

  // twists: one per syllable while listening (faster when louder), the reply's script while speaking
  if (s.mode === 1) {
    s.acc += (0.7 + 2.6 * s.level) * dt;
    if (s.acc >= 1) {
      s.acc -= 1;
      emit(s, X_LEFT, speed, Math.PI * (0.3 + 0.55 * s.level), 64, 0.9, 1.5, 0);
    }
  } else if (s.mode === 3) {
    if (ext >= 0) {
      // a real reply: one twist per syllable of its level, from the right edge
      s.acc += (0.7 + 2.6 * s.level) * dt;
      if (s.acc >= 1) {
        s.acc -= 1;
        emit(s, X_RIGHT, -speed, Math.PI * (0.3 + 0.55 * s.level), 64, 0.9, 1.3, 1);
      }
    } else {
      for (let i = 0; i < SPEAK_AT.length; i++) {
        if (prevT <= SPEAK_AT[i] && s.modeT > SPEAK_AT[i]) emit(s, X_RIGHT, -speed, Math.PI * (0.3 + 0.55 * REPLY[i][1]), 64, 0.9, 1.3, 1);
      }
    }
  }

  // the base width and the thread: springs, sub-stepped
  const bTarget = s.mode === 2 ? W_THREAD : s.mode === 3 ? W_SPEAK : W_IDLE;
  const kB = 120;
  const cB = 20;
  const kT = 157.9;
  const cT = 25.1;
  const tTarget = s.mode === 2 ? 1 : 0;
  const n = Math.max(1, Math.ceil(dt / 0.004));
  const h = dt / n;
  for (let i = 0; i < n; i++) {
    s.baseV += (kB * (bTarget - s.base) - cB * s.baseV) * h;
    s.base += s.baseV * h;
    s.thinkV += (kT * (tTarget - s.think) - cT * s.thinkV) * h;
    s.think += s.thinkV * h;
  }
  s.waveP += (3 * dt) / LOOP;
  s.idleP += (2 * Math.PI * dt) / LOOP;

  // pulses
  for (let i = 0; i < PL; i++) {
    const o = i * 8;
    if (s.pul[o + 4] >= s.pul[o + 6]) {
      s.pul[o + 2] = 0;
      continue;
    }
    const x0 = s.pul[o];
    s.pul[o] += s.pul[o + 1] * dt;
    s.pul[o + 4] += dt;
    s.pul[o + 2] *= Math.exp(-dt / s.pul[o + 5]);
    // a reply word appears as its twist crosses the middle
    if (s.pul[o + 7] === 1 && x0 > X_WORD && s.pul[o] <= X_WORD) {
      if (s.words < MAX_WORDS) s.wordT[s.words] = s.t;
      s.words += 1;
    }
  }

  // windows
  for (let k = 0; k < 2; k++) {
    const o = k * WL;
    const st = s.win[o];
    if (st === 0) continue;
    s.win[o + 2] += (s.win[o + 3] - s.win[o + 2]) * (1 - Math.exp(-dt / 0.03));
    const held = st === 1;
    if (held) s.win[o + 9] *= Math.exp(-dt / 0.12); // a finger that stops is not carrying the crossings
    const sp = held ? SPRING.window : SPRING.windowOut;
    const kk = Math.pow((2 * Math.PI) / sp.response, 2);
    const cc = 2 * sp.zeta * Math.sqrt(kk);
    for (let i = 0; i < n; i++) {
      s.win[o + 5] += (kk * ((held ? 1 : 0) - s.win[o + 4]) - cc * s.win[o + 5]) * h;
      s.win[o + 4] += s.win[o + 5] * h;
    }
    if (!held && s.win[o + 4] < 0.002) {
      s.win[o] = 0;
      s.win[o + 4] = 0;
    }
  }

  // bumps ride along the centre line at 260 pt/s
  for (let k = 0; k < 2; k++) {
    const o = k * BL;
    if (s.bump[o + 4] >= s.bump[o + 5]) continue;
    s.bump[o] += s.bump[o + 1] * dt;
    s.bump[o + 4] += dt;
  }

  // two fingers: a crossing (the edge of one window) passing under the other finger flips one of four signs
  if (s.win[0] === 1 && s.win[WL] === 1) {
    const xa = s.win[2];
    const xb = s.win[WL + 2];
    const d = GESTURE.window;
    for (let i = 0; i < 4; i++) {
      const v = i === 0 ? xb - (xa - d) : i === 1 ? xb - (xa + d) : i === 2 ? xa - (xb - d) : xa - (xb + d);
      const sg = v > 0 ? 1 : -1;
      if (s.sg[i] !== 0 && s.sg[i] !== sg) s.cross += 1;
      s.sg[i] = sg;
    }
  } else {
    for (let i = 0; i < 4; i++) s.sg[i] = 0;
  }

  // the caption's layers follow the mode: a layer arrives only once the others are (almost) gone, so no two lines overlap
  const kOut = 1 - Math.exp(-dt / 0.035);
  const kIn = 1 - Math.exp(-dt / 0.05);
  const cap = s.cap;
  // one dot per reversal of the thread (the shuttle's extremes: 0.25 / f and 0.75 / f) and the first at once
  s.dots = s.mode === 2 ? 1 + (s.modeT >= 0.25 / THINK_F ? 1 : 0) + (s.modeT >= 0.75 / THINK_F ? 1 : 0) : 0;
  // group 0 the question, 1 listening, 2 the dots, 3 the reply words
  // after a reply the sentence stays 600 ms, then the question comes back (Pro layer 3)
  const holdReply = s.mode === 0 && s.fromMode === 3 && s.modeT < 0.6;
  const want0 = s.mode === 0 && !holdReply ? 1 : 0;
  const want1 = s.mode === 1 ? 1 : 0;
  const want2 = s.mode === 2 ? 1 : 0;
  const want3 = s.mode === 3 || holdReply ? 1 : 0;
  const g1 = cap[1];
  const g2 = Math.max(cap[2], cap[3], cap[4]);
  let g3 = 0;
  for (let i = 5; i < 5 + MAX_WORDS; i++) g3 = Math.max(g3, cap[i]);
  const g0 = cap[0];
  const open0 = g1 < 0.25 && g2 < 0.25 && g3 < 0.25 ? 1 : 0;
  const open1 = g0 < 0.25 && g2 < 0.25 && g3 < 0.25 ? 1 : 0;
  const open2 = g0 < 0.25 && g1 < 0.25 && g3 < 0.25 ? 1 : 0;
  const open3 = g0 < 0.25 && g1 < 0.25 && g2 < 0.25 ? 1 : 0;
  cap[0] += (want0 * open0 - cap[0]) * (want0 * open0 > cap[0] ? kIn : kOut);
  cap[1] += (want1 * open1 - cap[1]) * (want1 * open1 > cap[1] ? kIn : kOut);
  for (let i = 0; i < 3; i++) {
    const tg = want2 * open2 * (s.dots > i ? 1 : 0);
    cap[2 + i] += (tg - cap[2 + i]) * (tg > cap[2 + i] ? kIn : kOut);
  }
  for (let i = 0; i < MAX_WORDS; i++) {
    const tg = want3 * open3 * (s.words > i ? 1 : 0);
    cap[5 + i] += (tg - cap[5 + i]) * (tg > cap[5 + i] ? 1 - Math.exp(-dt / 0.02) : kOut);
    const ageW = s.wordT[i] < 0 ? 9 : s.t - s.wordT[i];
    cap[5 + MAX_WORDS + i] = ageW < TINT_FOR ? cap[5 + i] * (1 - smooth(0, TINT_FOR, ageW)) : 0;
  }

  // the finger's brightness lift: 60 ms in, 320 ms out (nothing pops)
  s.gain += ((s.gainIn > 0 ? 1 : 0) - s.gain) * (1 - Math.exp(-dt / (s.gainIn > 0 ? 0.06 : 0.32)));
}

/** The frame the live state draws. */
export function liveFrame(s: Live, c: Frame | null): Frame {
  'worklet';
  const idleW = clamp01((s.base - W_THREAD) / (W_IDLE - W_THREAD));
  const pul = emptyPulses();
  let slot = 0;
  for (let i = 0; i < PL && slot < 8; i++) {
    const o = i * 8;
    if (s.pul[o + 4] >= s.pul[o + 6]) continue;
    const age = s.pul[o + 4];
    const life = s.pul[o + 6];
    const gate = 1 - smooth(life - 0.5, life, age);
    pul[slot * 4] = s.pul[o];
    pul[slot * 4 + 1] = s.pul[o + 2] * gate;
    pul[slot * 4 + 2] = s.pul[o + 3];
    pul[slot * 4 + 3] = s.pul[o + 1];
    slot++;
  }
  const win = emptyWin();
  const winB = [0, 0, 0, 0, 0, 0, 0, 0];
  for (let k = 0; k < 2; k++) {
    const o = k * WL;
    if (s.win[o] === 0) continue;
    win[k * 4] = s.win[o + 2];
    win[k * 4 + 1] = clamp01(s.win[o + 4]);
    win[k * 4 + 2] = s.win[o + 6];
    win[k * 4 + 3] = GESTURE.window;
    winB[k * 4] = s.win[o + 7];
    winB[k * 4 + 1] = s.win[o + 8];
    winB[k * 4 + 2] = 0.3;
  }
  const bump = emptyBump();
  for (let k = 0; k < 2; k++) {
    const o = k * BL;
    if (s.bump[o + 4] >= s.bump[o + 5]) continue;
    bump[k * 4] = s.bump[o];
    bump[k * 4 + 1] = s.bump[o + 2] * bumpGate(s.bump[o + 4], s.bump[o + 5]);
    bump[k * 4 + 2] = s.bump[o + 3];
    bump[k * 4 + 3] = s.bump[o + 1];
  }
  const wd = s.base + 4 * Math.sin(s.idleP * 2) * idleW + WD_LEVEL * s.level;
  void c;
  return buildFrame({
    phase: (s.t / LOOP) % 1,
    waveP: s.waveP,
    amp: AMP0 + AMP_LEVEL * s.level,
    wd,
    level: s.level,
    idleW,
    idleP: s.idleP,
    think: clamp01(s.think),
    thinkU0: 201 + 60 * Math.sin(2 * Math.PI * THINK_F * s.modeT),
    pul,
    win,
    winB,
    bump,
    dir: 0,
    loose: 0.3 + 0.7 * s.level,
    gain: 1 + 0.2 * s.gain,
  });
}

/** The resting frame under Reduce Motion: a still band whose width follows the level; thinking is a thread. */
export function reducedFrame(mode: number, level: number, pulse: number): Frame {
  'worklet';
  const wd = (mode === 2 ? W_THREAD + 2 : mode === 3 ? W_SPEAK : W_IDLE) + WD_LEVEL * level;
  const idleW = mode === 2 ? 0 : 1;
  const f = buildFrame({
    phase: 0,
    waveP: WAVE_P0,
    amp: AMP0,
    wd,
    level: mode === 2 ? 0 : level,
    idleW,
    idleP: IDLE.phase,
    think: 0,
    thinkU0: 201,
    pul: emptyPulses(),
    win: emptyWin(),
    winB: [0, 0, 0, 0, 0, 0, 0, 0],
    bump: emptyBump(),
    dir: mode === 1 ? 1 : mode === 3 ? -1 : 0,
    loose: 0,
    gain: mode === 2 ? 0.75 + 0.25 * pulse : 1,
  });
  return f;
}

function lerpArr(x: number[], y: number[], w: number) {
  'worklet';
  const out = x.slice();
  for (let i = 0; i < x.length; i++) out[i] = x[i] + (y[i] - x[i]) * w;
  return out;
}

/** Blend two frames (the 600 ms ease back to the loop's rest). `b`'s phases are brought to `a`'s whole cycle first. */
export function blendFrame(a: Frame, b: Frame, w: number): Frame {
  'worklet';
  const waveB = b.line[3] + Math.round(a.line[3] - b.line[3]);
  const pul = a.pul.slice();
  for (let i = 0; i < 8; i++) pul[i * 4 + 1] *= 1 - w;
  const win = a.win.slice();
  for (let k = 0; k < 2; k++) win[k * 4 + 1] *= 1 - w;
  const bump = a.bump.slice();
  for (let k = 0; k < 2; k++) bump[k * 4 + 1] *= 1 - w;
  const idleB = b.twA[2] + 2 * Math.PI * Math.round((a.twA[2] - b.twA[2]) / (2 * Math.PI));
  return {
    phase: b.phase,
    line: [mix(a.line[0], b.line[0], w), mix(a.line[1], b.line[1], w), b.line[2], mix(a.line[3], waveB, w)],
    sheet: lerpArr(a.sheet, b.sheet, w),
    twA: [mix(a.twA[0], b.twA[0], w), b.twA[1], mix(a.twA[2], idleB, w), mix(a.twA[3], b.twA[3], w)],
    twB: lerpArr(a.twB, b.twB, w),
    think: [mix(a.think[0], b.think[0], w), mix(a.think[1], b.think[1], w), a.think[2], 0],
    pul,
    win,
    winB: a.winB,
    bump,
    opt: lerpArr(a.opt, b.opt, w),
    extra: lerpArr(a.extra, b.extra, w),
  };
}
