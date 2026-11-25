/*
 Snack Stop Vending — the timeline and the touch model. Every value of a frame is a pure function
 of the loop time (autoplay) or of the clock and a few touch events (touch). Worklets only, each
 one declared above its callers.

 The dispense is one timeline of "beats" (seconds): the coil turns 2.0–2.8, the can tips 2.8–3.15,
 falls 3.15–3.6, bounces in the tray 3.6–3.95, is taken 5.8–6.3 and a new can slides forward
 6.4–7.0. The loop plays it for B4; touch plays it for any slot, from its start.
*/

/** One loop, in seconds. */
export const LOOP = 8.0;
/** Seconds without touch before the loop restarts. */
export const IDLE = 2;

export const ROWS = 4;
export const COLS = 5;
export const LETTERS = ['A', 'B', 'C', 'D'];
/** B4: row 1, column 3 (0-based). */
export const LOOP_SLOT = 1 * COLS + 3;

/**
 The 12 keys (3 per row, 4 rows), left to right, top to bottom, as printed on them. A letter, then
 a column 1–5, chooses a slot; 6 and 0 have no column (the LCD shakes); ✕ clears.
 One table: change it here only.
*/
export const KEYS = ['A', 'B', 'C', 'D', '1', '2', '3', '4', '5', '6', '0', '✕'];
/** The keys the loop presses: B, then 4. */
export const LOOP_KEY_1 = 1;
export const LOOP_KEY_2 = 7;

/** The five drinks, by can colour (`(2·row + col) mod 5`). */
export const DRINKS: [string, string, string][] = [
  ['Lime Fizz', 'Sparkling · 330 ml', '$1.50'],
  ['Pink Pop', 'Sparkling · 330 ml', '$1.75'],
  ['Violet Dusk', 'Still · 330 ml', '$1.75'],
  ['Tangerine Twist', 'Sparkling · 330 ml', '$1.50'],
  ['Sky Splash', 'Still · 330 ml', '$1.25'],
];

export function canIndex(slot: number) {
  'worklet';
  const r = Math.floor(slot / COLS);
  const c = slot % COLS;
  return (2 * r + c) % 5;
}

export function slotCode(slot: number) {
  'worklet';
  return LETTERS[Math.floor(slot / COLS)] + String((slot % COLS) + 1);
}

export function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeInQuad(x: number) {
  'worklet';
  return x * x;
}

export function easeOutCubic(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

export function easeInOutQuad(x: number) {
  'worklet';
  return x < 0.5 ? 2 * x * x : 1 - Math.pow(-2 * x + 2, 2) / 2;
}

export function mod(x: number, m: number) {
  'worklet';
  return ((x % m) + m) % m;
}

/** The moving can on the dispense beats, as offsets from its slot. `kind`: 0 in the slot, 1 moving, 2 refilling. */
export type CanState = {
  kind: number;
  /** Fall progress 0…1 (the 40 pt move left and the rotation are linear in it). */
  fall: number;
  /** Down from the slot's shelf line toward the tray floor, 0…1 (ease-in quad of the fall). */
  drop: number;
  bounce: number;
  rot: number;
  scale: number;
  alpha: number;
  /** Taken: slid down this far (pt). */
  taken: number;
  clip: boolean;
  veil: boolean;
};

export function canAt(t: number): CanState {
  'worklet';
  const s: CanState = { kind: 0, fall: 0, drop: 0, bounce: 0, rot: 0, scale: 1, alpha: 1, taken: 0, clip: false, veil: false };
  if (t < 2.0 || t >= 7.0) return s;
  if (t >= 6.4) {
    const r = easeOutCubic(seg(t, 6.4, 7.0));
    s.kind = 2;
    s.scale = 0.85 + 0.15 * r;
    s.alpha = r;
    return s;
  }
  s.kind = 1;
  s.scale = 1 + 0.12 * easeInOutQuad(seg(t, 2.0, 2.8));
  if (t >= 2.8) {
    s.rot = 0.35 * easeInQuad(seg(t, 2.8, 3.15));
    s.scale = 1.12;
  }
  if (t >= 3.15) {
    const f = seg(t, 3.15, 3.6);
    s.fall = f;
    s.drop = easeInQuad(f);
    s.rot = 0.35 + (Math.PI / 2 - 0.35) * f;
  }
  if (t >= 3.6) {
    const b = seg(t, 3.6, 3.95);
    s.bounce = Math.abs(Math.exp(-b * 4) * Math.sin(b * Math.PI * 3) * 8);
    s.rot = Math.PI / 2;
    s.fall = 1;
    s.drop = 1;
  }
  if (t >= 5.8) {
    const k = seg(t, 5.8, 6.3);
    s.taken = 40 * easeInOutQuad(k);
    s.alpha = 1 - k;
  }
  s.clip = t >= 3.45;
  s.veil = t >= 3.45;
  return s;
}

/** The white glint running round the coil, 0…1 (−1: none). */
export function glintAt(t: number) {
  'worklet';
  const u = seg(t, 2.0, 2.8);
  return u > 0 && u < 1 ? u : -1;
}

export type Frame = {
  /** The slot being dispensed (−1: none). */
  slot: number;
  can: CanState;
  glint: number;
  /** Reduce Motion: the can lying in the tray fades in and out instead of falling. */
  trayAlpha: number;
  /** Key flashes: up to two [key, progress 0…1]. */
  flashA: number;
  flashAU: number;
  flashB: number;
  flashBU: number;
  /** The demo finger on a key: [key, alpha]. */
  fingerKey: number;
  finger: number;
  lcd: string;
  lcdShake: number;
  /** Card: 0 rest, 1 chosen; pill: 0 rest, 1 price, 2 Enjoy!. */
  card: number;
  pill: number;
  cardSlot: number;
};

/** The loop's frame at loop time t. */
export function loopFrame(t: number): Frame {
  'worklet';
  const u1 = seg(t, 1.0, 1.35);
  const u2 = seg(t, 1.6, 1.95);
  const f1 = seg(t, 0.85, 0.97) * (1 - seg(t, 1.3, 1.42));
  const f2 = seg(t, 1.45, 1.57) * (1 - seg(t, 1.9, 2.02));
  const chosen = t >= 1.6 && t < 6.0;
  return {
    slot: LOOP_SLOT,
    can: canAt(t),
    glint: glintAt(t),
    trayAlpha: 0,
    flashA: LOOP_KEY_1,
    flashAU: u1 > 0 && u1 < 1 ? u1 : -1,
    flashB: LOOP_KEY_2,
    flashBU: u2 > 0 && u2 < 1 ? u2 : -1,
    fingerKey: f1 > 0 ? LOOP_KEY_1 : LOOP_KEY_2,
    finger: f1 > 0 ? f1 : f2,
    lcd: t >= 6.0 || t < 1.0 ? '' : t < 1.6 ? 'B_' : 'B4',
    lcdShake: 0,
    card: chosen ? 1 : 0,
    pill: !chosen ? 0 : t >= 3.6 ? 2 : 1,
    cardSlot: LOOP_SLOT,
  };
}

export type Touch = {
  manual: boolean;
  loopStart: number;
  lastTouch: number;
  /** The letter typed so far ('' none). */
  letter: string;
  /** The slot chosen (−1 none), the clock time of its beat 2.0 and of its beat 5.8 (−1: not yet). */
  slot: number;
  dispAt: number;
  takeAt: number;
  /** Last two key presses: key and clock time (−1: none). */
  keyA: number;
  keyAAt: number;
  keyB: number;
  keyBAt: number;
  shakeAt: number;
};

export function initialTouch(): Touch {
  'worklet';
  return {
    manual: false,
    loopStart: 0,
    lastTouch: -100,
    letter: '',
    slot: -1,
    dispAt: -1,
    takeAt: -1,
    keyA: -1,
    keyAAt: -100,
    keyB: -1,
    keyBAt: -100,
    shakeAt: -100,
  };
}

function copy(s: Touch): Touch {
  'worklet';
  return {
    manual: s.manual,
    loopStart: s.loopStart,
    lastTouch: s.lastTouch,
    letter: s.letter,
    slot: s.slot,
    dispAt: s.dispAt,
    takeAt: s.takeAt,
    keyA: s.keyA,
    keyAAt: s.keyAAt,
    keyB: s.keyB,
    keyBAt: s.keyBAt,
    shakeAt: s.shakeAt,
  };
}

/** The dispense beat under touch (−1: no dispense). Holds at 5.79 with the can in the tray until it is taken. */
export function touchBeat(s: Touch, c: number) {
  'worklet';
  if (s.slot < 0) return -1;
  if (s.takeAt >= 0) return Math.min(5.8 + (c - s.takeAt), 7.0);
  return Math.min(2.0 + (c - s.dispAt), 5.79);
}

/** Reduce Motion's frame values for a beat: the can fades out of its slot, then fades in lying in the tray. */
function reducedCan(s: Touch, c: number): [CanState, number] {
  'worklet';
  const st: CanState = { kind: 0, fall: 1, drop: 1, bounce: 0, rot: Math.PI / 2, scale: 1.12, alpha: 1, taken: 0, clip: false, veil: false };
  if (s.slot < 0) return [st, 0];
  if (s.takeAt >= 0) {
    const v = c - s.takeAt;
    if (v >= 0.2) {
      st.kind = 2;
      st.scale = 1;
      st.alpha = seg(v, 0.2, 0.4);
      return [st, 0];
    }
    st.kind = 3;
    st.alpha = 0;
    return [st, 1 - seg(v, 0, 0.2)];
  }
  const u = c - s.dispAt;
  if (u < 0.2) {
    st.kind = 2;
    st.scale = 1;
    st.alpha = 1 - seg(u, 0, 0.2);
    return [st, 0];
  }
  st.kind = 3;
  st.alpha = 0;
  return [st, seg(u, 0.2, 0.4)];
}

/** The beat the screen's text follows under Reduce Motion (the can lands at 0.4 s). */
function reducedBeat(s: Touch, c: number) {
  'worklet';
  if (s.slot < 0) return -1;
  if (s.takeAt >= 0) return c - s.takeAt >= 0.2 ? 6.0 : 5.8;
  return c - s.dispAt >= 0.4 ? 3.6 : 2.0;
}

export function touchFrame(s: Touch, c: number, reduced: boolean): Frame {
  'worklet';
  const tv = reduced ? reducedBeat(s, c) : touchBeat(s, c);
  const ua = seg(c, s.keyAAt, s.keyAAt + 0.35);
  const ub = seg(c, s.keyBAt, s.keyBAt + 0.35);
  let lcd = s.letter.length > 0 ? s.letter + '_' : '';
  if (s.slot >= 0 && tv < 6.0) lcd = slotCode(s.slot);
  const shakeU = seg(c, s.shakeAt, s.shakeAt + 0.3);
  const shake = shakeU > 0 && shakeU < 1 ? 4 * Math.sin(shakeU * Math.PI * 6) : 0;
  const chosen = s.slot >= 0 && tv < 6.0;
  let can = canAt(tv >= 0 ? tv : 0);
  let tray = 0;
  if (reduced) {
    const r = reducedCan(s, c);
    can = r[0];
    tray = r[1];
  }
  return {
    slot: s.slot,
    can,
    glint: reduced || tv < 0 ? -1 : glintAt(tv),
    trayAlpha: tray,
    flashA: s.keyA,
    flashAU: ua > 0 && ua < 1 ? ua : -1,
    flashB: s.keyB,
    flashBU: ub > 0 && ub < 1 ? ub : -1,
    fingerKey: 0,
    finger: 0,
    lcd,
    lcdShake: shake,
    card: chosen ? 1 : 0,
    pill: !chosen ? 0 : tv >= 3.6 ? 2 : 1,
    cardSlot: s.slot,
  };
}

export function frameAt(s: Touch, c: number, reduced: boolean): Frame {
  'worklet';
  if (s.manual || reduced) return touchFrame(s, c, reduced);
  return loopFrame(mod(c - s.loopStart, LOOP));
}

/** Hands the loop over to touch, keeping exactly the frame on screen. */
export function takeOver(s: Touch, c: number): Touch {
  'worklet';
  const n = copy(s);
  n.lastTouch = c;
  if (s.manual) return n;
  n.manual = true;
  const t = mod(c - s.loopStart, LOOP);
  if (t >= 1.0 && t < 1.6) n.letter = 'B';
  if (t >= 1.6 && t < 7.0) {
    n.slot = LOOP_SLOT;
    n.dispAt = c - (t - 2.0);
    if (t >= 5.8) n.takeAt = c - (t - 5.8);
  }
  if (t >= 1.0 && t < 1.35) {
    n.keyA = LOOP_KEY_1;
    n.keyAAt = c - (t - 1.0);
  }
  if (t >= 1.6 && t < 1.95) {
    n.keyB = LOOP_KEY_2;
    n.keyBAt = c - (t - 1.6);
  }
  return n;
}

/** Is a dispense running or a can waiting in the tray? */
export function busy(s: Touch, c: number, reduced: boolean) {
  'worklet';
  if (s.slot < 0) return false;
  const tv = reduced ? reducedBeat(s, c) : touchBeat(s, c);
  return tv < 7.0 && !(s.takeAt >= 0 && (reduced ? c - s.takeAt >= 0.4 : tv >= 7.0));
}

function press(s: Touch, key: number, c: number): Touch {
  'worklet';
  const n = copy(s);
  n.keyB = s.keyA;
  n.keyBAt = s.keyAAt;
  n.keyA = key;
  n.keyAAt = c;
  return n;
}

/** Chooses a slot: the LCD shows its code and the dispense beats play from their start. */
export function choose(s: Touch, slot: number, c: number): Touch {
  'worklet';
  const n = copy(s);
  n.letter = '';
  n.slot = slot;
  n.dispAt = c;
  n.takeAt = -1;
  return n;
}

function shake(s: Touch, c: number): Touch {
  'worklet';
  const n = copy(s);
  n.letter = '';
  n.shakeAt = c;
  return n;
}

/** A key is pressed: a letter, then a column 1–5, chooses a slot; ✕ clears; anything else shakes the LCD. */
export function pressKey(s0: Touch, key: number, c: number, reduced: boolean): Touch {
  'worklet';
  const s = press(s0, key, c);
  const v = KEYS[key];
  if (busy(s, c, reduced)) return shake(s, c);
  const n = copy(s);
  n.slot = -1;
  if (v === '✕') {
    n.letter = '';
    return n;
  }
  if (LETTERS.indexOf(v) >= 0) {
    n.letter = v;
    return n;
  }
  const col = Number(v);
  if (n.letter.length === 0 || col < 1 || col > COLS) return shake(n, c);
  return choose(n, LETTERS.indexOf(n.letter) * COLS + (col - 1), c);
}

/** A tap on a can in its slot: the same as typing its code. */
export function tapCan(s: Touch, slot: number, c: number, reduced: boolean): Touch {
  'worklet';
  if (busy(s, c, reduced)) return shake(s, c);
  return choose(s, slot, c);
}

/** Is the dispensed can lying in the tray, ready to take? */
export function canInTray(s: Touch, c: number, reduced: boolean) {
  'worklet';
  if (s.slot < 0 || s.takeAt >= 0) return false;
  return reduced ? c - s.dispAt >= 0.4 : touchBeat(s, c) >= 3.95;
}

/** A tap on the tray takes the can (the 5.8 s beats), then the slot refills. */
export function takeCan(s: Touch, c: number, reduced: boolean): Touch {
  'worklet';
  if (!canInTray(s, c, reduced)) return s;
  const n = copy(s);
  n.takeAt = c;
  return n;
}

export function touched(s: Touch, c: number): Touch {
  'worklet';
  const n = copy(s);
  n.lastTouch = c;
  return n;
}

/**
 Settles finished dispenses and, 2 s after the last touch (or after a dispensed can lands, if
 later), takes a can left in the tray, clears a half-typed code and restarts the loop at t = 0 once
 the machine is back at rest (the t = 0 frame).
*/
export function stepTouch(s: Touch, c: number, autoPlay: boolean, reduced: boolean): Touch | null {
  'worklet';
  if (!s.manual) return null;
  let n: Touch | null = null;
  if (s.slot >= 0 && s.takeAt >= 0 && (reduced ? c - s.takeAt >= 0.4 : touchBeat(s, c) >= 7.0)) {
    n = copy(s);
    n.slot = -1;
    n.dispAt = -1;
    n.takeAt = -1;
  }
  const cur = n ?? s;
  // Idle counts from the last touch or the can landing in the tray, whichever is later, so a
  // dispensed can waits 2 s in the tray before the machine takes it back.
  const landed = cur.slot >= 0 && cur.takeAt < 0 ? cur.dispAt + (reduced ? 0.4 : 1.95) : -1e9;
  if (!autoPlay || reduced || c - Math.max(cur.lastTouch, landed) < IDLE) return n;
  if (canInTray(cur, c, reduced)) return takeCan(cur, c, reduced);
  if (cur.slot >= 0) return n;
  if (c - cur.shakeAt < 0.3 || c - cur.keyAAt < 0.35) return n;
  const r = initialTouch();
  r.loopStart = c;
  return r;
}

/** Has the can just landed (for the haptic and the announcement)? */
export function landedBeat(s: Touch, c: number, reduced: boolean) {
  'worklet';
  if (!s.manual && !reduced) return -1;
  return reduced ? reducedBeat(s, c) : touchBeat(s, c);
}
