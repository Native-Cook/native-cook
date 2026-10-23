// The pin's heading, read from its frames every 0.6 s and scaled to the fitted turn at 1.8 s (-35°): the phone turns in the hand and the
// signs keep pointing at the friends, so the whole post swings around its pole.
// [time ms, heading deg]; the last key equals the first so the loop is seamless.
export const HEADING_KEYS: readonly (readonly [number, number])[] = [
  [0, 0],
  [600, -11],
  [1200, -23],
  [1800, -35],
  [2400, -18],
  [3000, -7],
  [3600, 0],
  [4200, 3],
  [4800, -17],
  [5400, -3],
  [6000, -8],
  [6600, -15],
  [7200, 0],
];
export const LOOP_MS = 7200;
export const RESUME_AFTER_MS = 3500;
export const RETURN_MS = 700; // heading eases back to 0 before the loop restarts
export const DRAG_DEG_PER_PT = 0.45;
// release: the post keeps turning with the finger's speed and slows like a turnstile
export const DECAY = 0.994;
