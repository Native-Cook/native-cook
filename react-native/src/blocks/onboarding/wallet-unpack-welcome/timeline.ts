/**
 * The hero as a function of one clock: `heroAt(q)` turns the time into the sequence (the card
 * falls back and the wallet comes up, two notes rise out of it and slide back, the card rises out
 * and lands) into positions, angles and opacities. The autoPlay loop and a tap read the same one.
 */

/** Seconds of one autoPlay loop. */
export const LOOP = 7.5;
/** Seconds after the last touch, once the sequence has ended, before the loop starts again. */
export const USER_HOLD = 3;

// The sequence, in seconds from the start of a loop; a tap starts it at OUT_AT.
/** The card lies still until then. */
export const OUT_AT = 0.8;
/** The card tips back past edge-on and drops behind the wallet, to show below it. */
const OUT_FOR = 0.7;
const TIP_FOR = 0.6;
const TIP = 104;
const DROP = 100;
const WALLET_IN_AT = 0.25;
const WALLET_IN = 0.3;
const WALLET_FROM = 0.94;
const NOTE1_UP_AT = 1.9;
const NOTE2_UP_AT = 2.2;
const NOTE_UP = 0.6;
const NOTE2_DOWN_AT = 3.5;
const NOTE1_DOWN_AT = 3.7;
const NOTE_DOWN = 0.5;
/** The card rises out of the pocket, tilted, then lands flat on a spring as the wallet goes. */
const BACK_AT = 4.7;
const RISE = 0.55;
const RISE_FROM_Y = 10;
const RISE_Y = -88;
const RISE_TURN = -12;
const RISE_TIP = 28;
const RISE_FROM_SCALE = 0.9;
const RISE_SCALE = 0.96;
const LAND_RESPONSE = 0.55;
const LAND_DAMPING = 0.8;
const DISSOLVE_AT = 0.35;
const DISSOLVE = 0.4;
/** The sequence is over and the card at rest by then. */
export const SEQUENCE_END = BACK_AT + RISE + 1;

// The two notes, in points from the hero's centre: resting with a pale edge showing above the
// pocket, and how high each one rises.
export const NOTE_REST = -24;
const NOTE1_UP = -118;
const NOTE2_UP = -62;
const NOTE1_TURN = -3;
const NOTE2_TURN = 2;
/** The notes ripple while out: points of wave and its period. */
const WAVE = 3.5;
const WAVE_PERIOD = 1.1;
const BLUR_REST = 0.8;
const BLUR_MOVING = 1.8;

const TAU = Math.PI * 2;

function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function smooth(t: number, from: number, to: number) {
  'worklet';
  const x = clamp01((t - from) / (to - from));
  return x * x * (3 - 2 * x);
}

function easeOut(x: number) {
  'worklet';
  const k = 1 - clamp01(x);
  return 1 - k * k * k;
}

function easeInOut(x: number) {
  'worklet';
  const k = clamp01(x);
  return k < 0.5 ? 4 * k * k * k : 1 - Math.pow(-2 * k + 2, 3) / 2;
}

/** A spring from 0 to 1 after `t` seconds, as SwiftUI's spring(response:dampingFraction:) moves. */
function springAt(t: number, response: number, damping: number) {
  'worklet';
  if (t <= 0) return 0;
  const w0 = TAU / response;
  const wd = w0 * Math.sqrt(1 - damping * damping);
  return 1 - Math.exp(-damping * w0 * t) * (Math.cos(wd * t) + ((damping * w0) / wd) * Math.sin(wd * t));
}

/** How far a note is out of the wallet, 0 to 1, and how fast it is moving (0 still, 1 fastest). */
function lift(q: number, upAt: number, downAt: number, still: boolean) {
  'worklet';
  if (still) return { out: q >= upAt && q < downAt + NOTE_DOWN / 2 ? 1 : 0, moving: 0 };
  if (q < upAt || q >= downAt + NOTE_DOWN) return { out: 0, moving: 0 };
  if (q < upAt + NOTE_UP) {
    const p = (q - upAt) / NOTE_UP;
    return { out: easeOut(p), moving: Math.sin(Math.PI * p) };
  }
  if (q < downAt) return { out: 1, moving: 0 };
  const p = (q - downAt) / NOTE_DOWN;
  return { out: 1 - easeInOut(p), moving: Math.sin(Math.PI * p) };
}

export type Hero = {
  card: number;
  /** The card's printing, which goes before the card is edge-on. */
  face: number;
  cardY: number;
  /** Degrees about the card's horizontal axis (top edge away) and in the screen's plane. */
  tip: number;
  turn: number;
  cardScale: number;
  wallet: number;
  walletScale: number;
  note1Y: number;
  note2Y: number;
  note1Turn: number;
  note2Turn: number;
  note1Wave: number;
  note2Wave: number;
  /** The ripple's phase, radians. */
  phase: number;
  note1Blur: number;
  note2Blur: number;
};

/** The hero `q` seconds into the sequence; reduced motion swaps the movement for cross-fades. */
export function heroAt(q: number, still: boolean): Hero {
  'worklet';
  let card = 1;
  let face = 1;
  let cardY = 0;
  let tip = 0;
  let turn = 0;
  let cardScale = 1;
  let wallet = 0;
  let walletScale = 1;

  const c = q - OUT_AT;
  const b = q - BACK_AT;
  if (c >= 0 && b < 0) {
    if (still) {
      card = 1 - smooth(c, 0, 0.3);
      wallet = smooth(c, 0, 0.3);
    } else {
      tip = TIP * easeInOut(c / TIP_FOR);
      cardY = DROP * easeInOut(c / OUT_FOR);
      face = 1 - smooth(tip, 60, 88);
      card = 1 - smooth(c, OUT_FOR - 0.2, OUT_FOR);
      wallet = smooth(c, WALLET_IN_AT, WALLET_IN_AT + WALLET_IN);
      walletScale = WALLET_FROM + (1 - WALLET_FROM) * easeOut((c - WALLET_IN_AT) / WALLET_IN);
    }
  } else if (b >= 0) {
    if (still) {
      card = smooth(b, 0, 0.3);
      wallet = 1 - smooth(b, 0, 0.3);
    } else {
      const gone = smooth(b, DISSOLVE_AT, DISSOLVE_AT + DISSOLVE);
      wallet = 1 - gone;
      walletScale = 1 - (1 - WALLET_FROM) * gone;
      if (b < RISE) {
        const r = easeOut(b / RISE);
        cardY = RISE_FROM_Y + (RISE_Y - RISE_FROM_Y) * r;
        turn = RISE_TURN * r;
        tip = RISE_TIP * r;
        cardScale = RISE_FROM_SCALE + (RISE_SCALE - RISE_FROM_SCALE) * r;
      } else {
        const left = 1 - springAt(b - RISE, LAND_RESPONSE, LAND_DAMPING);
        cardY = RISE_Y * left;
        turn = RISE_TURN * left;
        tip = RISE_TIP * left;
        cardScale = RISE_SCALE + (1 - RISE_SCALE) * (1 - left);
      }
    }
  }

  const n1 = lift(q, NOTE1_UP_AT, NOTE1_DOWN_AT, still);
  const n2 = lift(q, NOTE2_UP_AT, NOTE2_DOWN_AT, still);
  return {
    card,
    face,
    cardY,
    tip,
    turn,
    cardScale,
    wallet,
    walletScale,
    note1Y: NOTE_REST + (NOTE1_UP - NOTE_REST) * n1.out,
    note2Y: NOTE_REST + (NOTE2_UP - NOTE_REST) * n2.out,
    note1Turn: still ? 0 : NOTE1_TURN * n1.out,
    note2Turn: still ? 0 : NOTE2_TURN * n2.out,
    note1Wave: still ? 0 : WAVE * n1.out,
    note2Wave: still ? 0 : WAVE * n2.out,
    phase: ((q - NOTE1_UP_AT) / WAVE_PERIOD) * TAU,
    note1Blur: BLUR_REST + BLUR_MOVING * n1.moving,
    note2Blur: BLUR_REST + BLUR_MOVING * n2.moving,
  };
}
