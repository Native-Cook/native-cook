/*
 Morse Key Input — timing, the tape and the decoder. Every frame of the self-playing loop is a
 pure function of the loop time t; under a finger a small simulation (Sim) is stepped on the UI
 thread and read through the same Pose.

 LOOP = 9 s, unit u = 0.24 s (a dot is one unit, the gap inside a letter one, between letters three):
   0.0 – 1.3   rest (the poster: the key at rest, a blank leader of tape, two empty answer slots)
   1.3 – 2.98  H: · · · · (presses at 1.30, 1.78, 2.26, 2.74)     3.48  H is decoded into slot 1
   3.70 – 4.42 I: · · (presses at 3.70, 4.18)                       4.92  I is decoded into slot 2
   1.1 – 5.1   the tape runs at 34 pt/s (0.2 s to speed up and to stop), printing a mark per press
   5.3 – 6.8   hold: "Correct · +40"
   6.8 – 7.9   the printed tape tears off at the register and falls away; 7.0 – 7.5 the answer
               rolls out and the chip fades
   7.6 – 8.4   a fresh leader feeds out of the register      8.4 – 9.0 rest
*/

export const LOOP = 9;
export const UNIT = 0.24;
export const TAPE_SPEED = 34;
/** Touch: a press longer than this is a dash; a pause longer than LETTER_GAP ends the letter. */
export const DASH_AT = 0.2;
export const LETTER_GAP = 0.55;
export const IDLE_BEFORE_BACK = 2;
export const BACK_FOR = 0.9;
/** The lever's travel stops on the contact: depth 1. */
export const KEY_OMEGA = 60;
export const KEY_ZETA = 0.5;

const PRESSES: readonly (readonly [number, number])[] = [
  [1.3, 1.54],
  [1.78, 2.02],
  [2.26, 2.5],
  [2.74, 2.98],
  [3.7, 3.94],
  [4.18, 4.42],
];
const DECODE = [3.48, 4.92];
const ANSWER = ['H', 'I'];

export const MORSE: Record<string, string> = {
  '.-': 'A', '-...': 'B', '-.-.': 'C', '-..': 'D', '.': 'E', '..-.': 'F', '--.': 'G', '....': 'H', '..': 'I',
  '.---': 'J', '-.-': 'K', '.-..': 'L', '--': 'M', '-.': 'N', '---': 'O', '.--.': 'P', '--.-': 'Q', '.-.': 'R',
  '...': 'S', '-': 'T', '..-': 'U', '...-': 'V', '.--': 'W', '-..-': 'X', '-.--': 'Y', '--..': 'Z',
  '-----': '0', '.----': '1', '..---': '2', '...--': '3', '....-': '4', '.....': '5', '-....': '6', '--...': '7', '---..': '8', '----.': '9',
};

export type Slot = { ch: string; in: number; out: number };

export type Strip = {
  /** Tape length out of the register (pt), the tape run so far, and the marks as [from, to] in run coordinates. */
  len: number;
  run: number;
  marks: number[];
};

export type Pose = {
  depth: number;
  /** Seconds since the contact last closed (sparks), and how hard it hums while held. */
  sparkAge: number;
  hum: number;
  /** Pen on the tape (0 … 1). */
  pen: number;
  tape: Strip;
  /** The torn strip falling away: progress 0 … 1 (0 = none). */
  fall: number;
  torn: Strip;
  slots: Slot[];
  /** The symbols keyed for the letter in progress, e.g. "..-". */
  buffer: string;
  bufferAlpha: number;
  /** The result chip: 0 hidden … 1 shown, and whether the answer is right. */
  chip: number;
  correct: number;
};

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeOut(x: number) {
  'worklet';
  const u = 1 - clamp01(x);
  return 1 - u * u * u;
}

export function easeInOut(x: number) {
  'worklet';
  const v = clamp01(x);
  return v < 0.5 ? 4 * v * v * v : 1 - Math.pow(-2 * v + 2, 3) / 2;
}

export function hash(n: number) {
  'worklet';
  const s = Math.sin(n * 12.9898 + 4.1414) * 43758.5453;
  return s - Math.floor(s);
}

/** Unit step response of an underdamped spring, 0 before τ = 0. */
function springStep(tau: number) {
  'worklet';
  if (tau <= 0) return 0;
  const wd = KEY_OMEGA * Math.sqrt(1 - KEY_ZETA * KEY_ZETA);
  const e = Math.exp(-KEY_ZETA * KEY_OMEGA * tau);
  return 1 - e * (Math.cos(wd * tau) + ((KEY_ZETA * KEY_OMEGA) / wd) * Math.sin(wd * tau));
}

/** The tape's run (pt) at loop time t: it speeds up over 0.2 s, runs, and slows over 0.2 s. */
function runAt(t: number) {
  'worklet';
  const a = 0.2;
  let tau = 0;
  if (t <= 1.1) tau = 0;
  else if (t < 1.1 + a) tau = ((t - 1.1) * (t - 1.1)) / (2 * a);
  else if (t < 4.9) tau = a / 2 + (t - 1.1 - a);
  else if (t < 4.9 + a) tau = a / 2 + (4.9 - 1.1 - a) + (t - 4.9) - ((t - 4.9) * (t - 4.9)) / (2 * a);
  else tau = 3.8;
  return tau * TAPE_SPEED;
}

function loopMarksAll() {
  'worklet';
  const marks: number[] = [];
  for (let i = 0; i < PRESSES.length; i += 1) marks.push(runAt(PRESSES[i][0] + 0.02), runAt(PRESSES[i][1]));
  return marks;
}

/** The pose of the self-playing loop at loop time t; `leader` is the resting tape length. */
export function loopPose(t: number, leader: number): Pose {
  'worklet';
  // The lever: superposed spring steps, stopped by the contact at depth 1.
  let depth = 0;
  let downAt = -99;
  let held = false;
  for (let i = 0; i < PRESSES.length; i += 1) {
    const [d, u] = PRESSES[i];
    depth += springStep(t - d) - springStep(t - u);
    if (t >= d) {
      downAt = d;
      held = t < u;
    }
  }
  depth = Math.max(0, Math.min(1, depth));
  const run = runAt(t);
  const marks: number[] = [];
  for (let i = 0; i < PRESSES.length; i += 1) {
    const [d, u] = PRESSES[i];
    if (t < d + 0.02) break;
    marks.push(runAt(d + 0.02), runAt(Math.min(t, u)));
  }
  let buffer = '';
  for (let i = 0; i < PRESSES.length; i += 1) {
    const d = PRESSES[i][0];
    if (t < d) break;
    if (d < DECODE[0] ? t < DECODE[0] : t < DECODE[1]) buffer += '.';
  }
  const slots: Slot[] = [];
  for (let i = 0; i < 2; i += 1) {
    const out = easeInOut(seg(t, 7.0 + i * 0.07, 7.4 + i * 0.07));
    slots.push({ ch: t >= DECODE[i] && t < 7.6 ? ANSWER[i] : '', in: easeOut(seg(t, DECODE[i], DECODE[i] + 0.3)), out });
  }
  const tear = t >= 6.8;
  const tape: Strip = tear ? { len: leader * easeOut(seg(t, 7.6, 8.4)), run: 0, marks: [] } : { len: leader + run, run, marks };
  const torn: Strip = { len: leader + runAt(LOOP), run: runAt(LOOP), marks: tear ? loopMarksAll() : [] };
  const fall = tear ? seg(t, 6.8, 7.9) : 0;
  return {
    depth,
    sparkAge: t - downAt - 0.02,
    hum: held ? 1 : 0,
    pen: depth > 0.85 ? 1 : 0,
    tape,
    fall,
    torn,
    slots,
    buffer,
    bufferAlpha: buffer.length > 0 ? 1 : 0,
    chip: easeOut(seg(t, 5.3, 5.6)) * (1 - easeInOut(seg(t, 6.8, 7.1))),
    correct: 1,
  };
}


/** The finger's simulation, mutated in place on the UI thread. */
export type Sim = {
  depth: number;
  vel: number;
  held: boolean;
  downAt: number;
  upAt: number;
  run: number;
  speed: number;
  marks: number[];
  buffer: string;
  decoded: string[];
  slotAt: number[];
  chipAt: number;
  correct: number;
  backAt: number;
  torn: Strip | null;
  /** Tape length when the finger took over (the leader may still be feeding out). */
  base: number;
};

export function makeSim(leader: number): Sim {
  'worklet';
  return { depth: 0, vel: 0, held: false, downAt: -99, upAt: -99, run: 0, speed: 0, marks: [], buffer: '', decoded: [], slotAt: [], chipAt: -1, correct: 0, backAt: -1, torn: null, base: leader };
}

/** Steps the lever and the tape, and decodes a finished letter. Returns 1 when a letter landed. */
export function stepSim(s: Sim, now: number, dt: number) {
  'worklet';
  const target = s.held ? 1 : 0;
  // Semi-implicit spring; the contact stops the lever with a small bounce.
  s.vel += (-KEY_OMEGA * KEY_OMEGA * (s.depth - target) - 2 * KEY_ZETA * KEY_OMEGA * s.vel) * dt;
  s.depth += s.vel * dt;
  if (s.depth > 1) {
    s.depth = 1;
    s.vel = -0.25 * s.vel;
  }
  if (s.depth < 0) {
    s.depth = 0;
    s.vel = 0;
  }
  // The tape runs while keying and stops about a second after the last release.
  const busy = s.backAt < 0 && (s.held || now - s.upAt < 1.1);
  const want = busy ? TAPE_SPEED : 0;
  s.speed += (want - s.speed) * (1 - Math.exp(-dt / 0.08));
  s.run += s.speed * dt;
  if (s.held && s.marks.length > 0) s.marks[s.marks.length - 1] = s.run;
  if (!s.held && s.buffer.length > 0 && now - s.upAt > LETTER_GAP) {
    const ch = MORSE[s.buffer] ?? '?';
    s.buffer = '';
    if (s.decoded.length >= 2) {
      s.decoded = [];
      s.slotAt = [];
      s.chipAt = -1;
    }
    s.decoded.push(ch);
    s.slotAt.push(now);
    if (s.decoded.length === 2) {
      s.chipAt = now + 0.2;
      s.correct = s.decoded[0] === 'H' && s.decoded[1] === 'I' ? 1 : 0;
    }
    return 1;
  }
  return 0;
}

export function keyDown(s: Sim, now: number) {
  'worklet';
  s.held = true;
  s.downAt = now;
  s.marks.push(s.run, s.run);
}

export function keyUp(s: Sim, now: number) {
  'worklet';
  if (!s.held) return;
  s.held = false;
  s.upAt = now;
  s.buffer += now - s.downAt > DASH_AT ? '-' : '.';
  if (s.buffer.length > 6) s.buffer = s.buffer.slice(-6);
}

/** The pose under (or after) a finger at clock time `now`. */
export function simPose(s: Sim, now: number, leader: number): Pose {
  'worklet';
  const slots: Slot[] = [];
  const back = s.backAt >= 0 ? now - s.backAt : -1;
  for (let i = 0; i < 2; i += 1) {
    const at = s.slotAt[i] ?? -1;
    slots.push({
      ch: s.decoded[i] ?? '',
      in: at < 0 ? 0 : easeOut((now - at) / 0.3),
      out: back < 0 ? 0 : easeInOut(seg(back, 0.05 + i * 0.07, 0.45 + i * 0.07)),
    });
  }
  const chip = s.chipAt < 0 ? 0 : easeOut((now - s.chipAt) / 0.3) * (back < 0 ? 1 : 1 - easeInOut(back / 0.3));
  const tape: Strip = back >= 0 ? { len: leader * easeOut(seg(back, 0.25, BACK_FOR)), run: 0, marks: [] } : { len: s.base + s.run, run: s.run, marks: s.marks };
  return {
    depth: s.depth,
    sparkAge: now - s.downAt - 0.02,
    hum: s.held ? 1 : 0,
    pen: s.depth > 0.85 ? 1 : 0,
    tape,
    fall: back >= 0 ? seg(back, 0, BACK_FOR) : 0,
    torn: s.torn ?? { len: 0, run: 0, marks: [] },
    slots,
    buffer: s.buffer,
    bufferAlpha: s.buffer.length > 0 && back < 0 ? 1 : 0,
    chip,
    correct: s.correct,
  };
}
