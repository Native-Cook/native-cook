/*
 Dymo Label Input — the autoPlay script and the pose it produces. Every value is a pure function of
 the loop time t.

   0.0 – 1.2   rest: a blank stub of red tape out of the head, the wheel on A, an unlabelled jar.
   1.2 – 4.6   "OATS" is typed, 0.85 s a letter: the wheel spins to the letter (0.32 s, ease-in-out,
               a 1.5° click past it and back), the trigger is squeezed (0.16 s down, 0.2 s up), the
               letter whitens as it is pressed into the tape, and the tape advances one pitch (0.2 s).
   4.6 – 5.0   Return: the cutter snaps and the strip comes free.
   5.0 – 6.2   the strip flies onto the jar along an arc with a turn and a lift, and sticks with a
               squash (spring).
   6.2 – 7.4   hold.
   7.4 – 8.4   the jar slides off along the shelf and a fresh one slides in; new tape feeds out; the
               wheel spins back to A; the field clears.
   8.4 – 9.0   rest.
 */

export const LOOP = 9;
export const WORD = 'OATS';
/** The wheel's characters, clockwise. */
export const WHEEL = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-&. ';
export const STEP = 360 / WHEEL.length;
const LETTER_FROM = 1.2;
const LETTER_EVERY = 0.85;

export type Pose = {
  /** Wheel rotation (deg): slot j is under the head when wheel = -j * STEP. */
  wheel: number;
  trigger: number;
  /** Letters on the tape in the machine, how white each is (0..1), how far each pops (0..1). */
  tape: string;
  whiten: number[];
  pop: number[];
  /** Tape advance in pitches, and blank tape beyond the first letter (pt). */
  advance: number;
  lead: number;
  /** The cut strip: its letters, flight 0 (at the head) → 1 (on the jar), squash, cutter lever. */
  strip: string;
  fly: number;
  squash: number;
  cutter: number;
  /** The jar swap 0 → 1 (the labelled jar leaves, a fresh one arrives). */
  slide: number;
  /** Tape colour of the machine's tape and of the cut strip (linear 0..1 RGB). */
  tapeRGB: number[];
  stripRGB: number[];
};

export const TAPES: { name: string; hex: string; rgb: number[] }[] = [
  { name: 'Red', hex: '#C3313B', rgb: [0.76, 0.19, 0.23] },
  { name: 'Black', hex: '#2C2C31', rgb: [0.17, 0.17, 0.19] },
  { name: 'Blue', hex: '#2F5EA6', rgb: [0.18, 0.37, 0.65] },
  { name: 'Green', hex: '#2D7A59', rgb: [0.18, 0.48, 0.35] },
  { name: 'Yellow', hex: '#E5B636', rgb: [0.9, 0.71, 0.21] },
];

export function seg(t: number, a: number, b: number) {
  'worklet';
  return Math.min(1, Math.max(0, (t - a) / (b - a)));
}

export function easeInOut(u: number) {
  'worklet';
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}

export function easeOut(u: number) {
  'worklet';
  return 1 - Math.pow(1 - u, 3);
}

/** Spin with a detent click: ease-in-out to the target, then a small damped overshoot. */
export function click(u: number) {
  'worklet';
  if (u <= 0) return 0;
  const base = easeInOut(Math.min(1, u / 0.75));
  const after = Math.max(0, (u - 0.75) / 0.25);
  return base + (u > 0.75 ? 0.06 * Math.sin(after * Math.PI) * (1 - after) : 0);
}

/** Squash on landing: 0 → 1 → settles back to 0 with a spring. */
export function squash(u: number) {
  'worklet';
  if (u <= 0 || u >= 1) return 0;
  return Math.exp(-5 * u) * Math.sin(u * Math.PI * 2.5);
}

/** The shortest signed turn (deg) from slot a to slot b. */
export function slotTurn(a: number, b: number) {
  'worklet';
  let d = (b - a) * STEP;
  while (d > 180) d -= 360;
  while (d < -180) d += 360;
  return d;
}

export function slotOf(ch: string) {
  'worklet';
  const i = WHEEL.indexOf(ch.toUpperCase());
  return i < 0 ? WHEEL.length - 1 : i;
}

/** The loop's pose at t. */
export function loopPose(t: number): Pose {
  'worklet';
  const n = WORD.length;
  // Wheel: A → O → A → T → S, each spin in the first 0.32 s of its letter's slot; home to A at 7.4.
  let wheel = 0;
  let from = 0;
  for (let i = 0; i < n; i += 1) {
    const to = slotOf(WORD[i]);
    const a = LETTER_FROM + i * LETTER_EVERY;
    const turn = slotTurn(from, to);
    wheel += turn * click(seg(t, a, a + 0.32));
    from = to;
  }
  wheel += slotTurn(from, 0) * click(seg(t, 7.4, 7.95));
  wheel = -wheel;

  let trigger = 0;
  const whiten: number[] = [];
  const pop: number[] = [];
  let advance = 0;
  let pressed = 0;
  for (let i = 0; i < n; i += 1) {
    const a = LETTER_FROM + i * LETTER_EVERY + 0.36;
    trigger = Math.max(trigger, easeOut(seg(t, a, a + 0.16)) * (1 - easeInOut(seg(t, a + 0.16, a + 0.36))));
    const w = easeOut(seg(t, a + 0.08, a + 0.2));
    if (t >= a + 0.08) pressed = i + 1;
    whiten.push(w);
    pop.push(Math.sin(Math.PI * seg(t, a + 0.1, a + 0.34)));
    advance += easeInOut(seg(t, a + 0.3, a + 0.5));
  }
  const cutAt = 4.7;
  const cut = t >= cutAt;
  const cutter = easeOut(seg(t, 4.6, 4.7)) * (1 - easeInOut(seg(t, 4.7, 4.9)));
  const fly = easeInOut(seg(t, 5.0, 6.0));
  const sq = squash(seg(t, 5.95, 6.45));
  const slide = easeInOut(seg(t, 7.4, 8.4));
  // After the cut, the stub left in the machine is short; new tape feeds out with the jar swap.
  const feed = easeInOut(seg(t, 7.5, 8.3));
  const tape = cut ? '' : WORD.slice(0, pressed);
  return {
    wheel,
    trigger,
    tape,
    whiten: cut ? [] : whiten.slice(0, pressed),
    pop: cut ? [] : pop.slice(0, pressed),
    advance: cut ? 0 : advance,
    lead: cut ? 4 + 14 * feed : 18,
    strip: cut && slide < 1 ? WORD : '',
    fly,
    squash: sq,
    cutter,
    slide,
    tapeRGB: TAPES[0].rgb,
    stripRGB: TAPES[0].rgb,
  };
}

/** The field's text over the loop. */
export function loopText(t: number) {
  'worklet';
  if (t >= 7.4) return 0;
  let n = 0;
  for (let i = 0; i < WORD.length; i += 1) if (t >= LETTER_FROM + i * LETTER_EVERY + 0.44) n = i + 1;
  return n;
}
