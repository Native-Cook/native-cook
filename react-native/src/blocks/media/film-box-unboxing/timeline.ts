/**
 * The screen as a function of time. Every moving part reads `sceneAt`, which turns the clock and
 * the moments of the five actions (unbox, load, shoot, retake, reset) into opacities, angles and
 * scales; touches and the autoPlay loop only write those moments.
 */

/** Seconds of one autoPlay loop. */
export const LOOP = 10;
/** Seconds without a touch, once the screen has settled, before it goes back to rest and plays again. */
export const USER_HOLD = 3;

// The autoPlay loop, in seconds from its start: rest, drag the knob, open, read the card, load,
// shoot, look at the photo, then fade through black to the closed box.
export const DRAG_AT = 1.4;
export const DRAG_FOR = 0.8;
export const UNBOX_AT = DRAG_AT + DRAG_FOR;
export const LOAD_AT = 6.6;
export const SHOOT_AT = 7.7;
export const RESET_AT = 9.6;
/** Each half of the fade through black that puts the box back. */
export const RESET_HALF = 0.2;

// The idle box bobs and sways on a 2.5 s period, a quarter of LOOP, so every loop sees the same sway.
export const BOB = 2.5;
export const BOB_Y = 4;
export const BOB_ROLL = 1.5;
export const BOB_YAW = 3;

// Opening, in seconds from the release of the knob.
export const PILL_OUT = 0.2;
export const SPINNER_AT = 0.1;
export const SPINNER_IN = 0.2;
export const SPINNER_OUT = 0.2;
/** Degrees the box tips towards the viewer as it opens, on a spring. */
export const TILT = 16;
export const TILT_RESPONSE = 0.4;
export const TILT_DAMPING = 0.6;
export const FLAPS_AT = 0.08;
export const FLAPS_FOR = 0.5;
/** Degrees the flaps swing open, past upright. */
export const FLAP_OPEN = 110;
export const GLOW_AT = 0.35;
export const GLOW_FOR = 0.35;
/** The streak of light that crosses the box. */
export const SWEEP_AT = 0.3;
export const SWEEP_FOR = 0.45;
/** The view dives into the open box. */
export const DIVE_AT = 0.7;
export const DIVE_FOR = 0.5;
export const DIVE_SCALE = 4;
export const BOX_OUT_AT = 0.95;
export const BOX_OUT = 0.25;
export const HEADER_OUT_AT = 0.7;
export const HEADER_OUT = 0.25;
export const NAME_AT = 1.15;
export const NAME_IN = 0.25;
export const REVEAL_AT = 1.7;

// The card, in seconds from REVEAL_AT.
export const CANISTER_IN = 0.25;
export const CANISTER_FROM = 0.6;
export const CANISTER_RESPONSE = 0.5;
export const CANISTER_DAMPING = 0.7;
/** The name's description, the four rows and the button come in one after the other. */
export const ROWS = 6;
export const ROWS_AT = 0.1;
export const ROW_STAGGER = 0.06;
export const ROW_IN = 0.3;
export const ROW_LIFT = 8;
/** The canister turns once about its long axis, then rests, every SPIN_PERIOD. */
export const SPIN_AT = 0.4;
export const SPIN_TURN = 2.2;
export const SPIN_PERIOD = 3;
export const OPEN_SETTLED = REVEAL_AT + 0.7;

// Loading, in seconds from the tap on Load film.
export const LOAD_GREY = 0.1;
export const CARD_OUT_AT = 0.05;
export const CARD_OUT = 0.25;
export const FLY = 0.55;
/** The canister's size at the top of the camera. */
export const SMALL = 0.3;
export const CAMERA_IN_AT = 0.25;
export const CAMERA_IN = 0.3;
export const CAMERA_LIFT = 24;
export const LOAD_SETTLED = 0.6;

// Shooting, in seconds from the tap on the shutter, and retaking.
export const PRESS = 0.15;
export const PRESS_SCALE = 0.9;
export const BUTTON_PRESS_SCALE = 0.94;
export const FLASH = 0.12;
export const FLASH_ALPHA = 0.3;
export const PHOTO_AT = 0.05;
export const PHOTO_IN = 0.4;
export const PHOTO_FROM = 1.1;
export const CAMERA_ROW_OUT_AT = 0.1;
export const CAMERA_ROW_OUT = 0.2;
export const PHOTO_ROW_IN_AT = 0.2;
export const PHOTO_ROW_IN = 0.25;
export const SHOT_SETTLED = 0.5;
export const PHOTO_OUT = 0.25;
export const RETAKE_SETTLED = 0.4;

/** What the controls on screen accept. */
export const STAGE_BUSY = 0;
export const STAGE_BOX = 1;
export const STAGE_FILM = 2;
export const STAGE_CAMERA = 3;
export const STAGE_PHOTO = 4;

const TAU = Math.PI * 2;

/** When each action happened, in seconds of the screen's clock; -1 when it has not. */
export type Events = { unbox: number; load: number; shot: number; retake: number; reset: number };

export const NO_EVENTS: Events = { unbox: -1, load: -1, shot: -1, retake: -1, reset: -1 };

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

/** 0 before `from`, 1 after `to`, a smoothstep between. */
export function smooth(t: number, from: number, to: number) {
  'worklet';
  const x = clamp01((t - from) / (to - from));
  return x * x * (3 - 2 * x);
}

export function easeOut(x: number) {
  'worklet';
  const k = 1 - clamp01(x);
  return 1 - k * k * k;
}

export function easeIn(x: number) {
  'worklet';
  const k = clamp01(x);
  return k * k * k;
}

export function easeInOut(x: number) {
  'worklet';
  const k = clamp01(x);
  return k < 0.5 ? 4 * k * k * k : 1 - Math.pow(-2 * k + 2, 3) / 2;
}

/**
 * A spring from 0 to 1 after `t` seconds, as SwiftUI defines one: `response` is the period of the
 * undamped spring, `damping` the fraction of critical damping (below 1).
 */
export function springAt(t: number, response: number, damping: number) {
  'worklet';
  if (t <= 0) return 0;
  const w0 = TAU / response;
  const wd = w0 * Math.sqrt(1 - damping * damping);
  const decay = Math.exp(-damping * w0 * t);
  return 1 - decay * (Math.cos(wd * t) + ((damping * w0) / wd) * Math.sin(wd * t));
}

/** Up and back down once over `length` seconds. */
function pulse(t: number, length: number) {
  'worklet';
  return t < 0 || t > length ? 0 : Math.sin((Math.PI * t) / length);
}

/** The canister's turn, `t` seconds after the card came in. */
function spinAt(t: number, still: boolean) {
  'worklet';
  if (still || t < SPIN_AT) return 0;
  return easeInOut(((t - SPIN_AT) % SPIN_PERIOD) / SPIN_TURN) * TAU;
}

export type Scene = {
  stage: number;
  header: number;
  /** The pill's ring, knob and label. */
  pill: number;
  spinner: number;
  /** Seconds the spinner has turned. */
  spinnerTime: number;
  boxAlpha: number;
  /** The box's sway, in degrees and points. */
  yaw: number;
  roll: number;
  lift: number;
  tilt: number;
  flap: number;
  glow: number;
  /** Where the streak of light is across the box, 0 to 1; outside that range it is not drawn. */
  sweep: number;
  dive: number;
  name: number;
  canister: number;
  canisterScale: number;
  /** 0 on the card, 1 at the top of the camera. */
  fly: number;
  spin: number;
  rows: number[];
  cardOut: number;
  grey: number;
  loadScale: number;
  /** Seconds since the card came in, which the expiry counts down from. */
  shown: number;
  camera: number;
  cameraRow: number;
  photoRow: number;
  shutter: number;
  flash: number;
  photo: number;
  photoScale: number;
  overlay: number;
};

/** Everything on screen at `now`, from the moments of the actions. */
export function sceneAt(now: number, ev: Events, still: boolean): Scene {
  'worklet';
  const resetting = ev.reset >= 0 ? now - ev.reset : -1;
  // Past the darkest moment of the fade the box is back, closed.
  const cleared = resetting >= RESET_HALF;
  let overlay = 0;
  if (resetting >= 0 && resetting < RESET_HALF) overlay = smooth(resetting, 0, RESET_HALF);
  else if (resetting >= RESET_HALF) overlay = 1 - smooth(resetting, RESET_HALF, RESET_HALF * 2);

  const o = !cleared && ev.unbox >= 0 ? now - ev.unbox : -1;
  const l = o >= 0 && ev.load > ev.unbox ? now - ev.load : -1;
  const shotOn = l >= 0 && ev.shot > ev.load && ev.shot > ev.retake;
  const s = shotOn ? now - ev.shot : -1;
  const r = l >= 0 && ev.retake > ev.load && ev.retake > ev.shot ? now - ev.retake : -1;
  const v = o - REVEAL_AT;

  const phase = (now / BOB) * TAU;
  const sway = still ? 0 : 1;

  const rows: number[] = [];
  for (let i = 0; i < ROWS; i++) {
    const at = ROWS_AT + i * ROW_STAGGER;
    rows.push(o < 0 ? 0 : still ? smooth(v, ROWS_AT, ROWS_AT + ROW_IN) : smooth(v, at, at + ROW_IN));
  }

  let spin = spinAt(v, still);
  let fly = 0;
  let canister = o < 0 ? 0 : smooth(v, 0, CANISTER_IN);
  if (l >= 0) {
    // The canister finishes its turn on the way up, the short way round.
    const from = spinAt(ev.load - ev.unbox - REVEAL_AT, still);
    const to = from > Math.PI ? TAU : 0;
    if (still) {
      fly = l >= FLY / 2 ? 1 : 0;
      canister = 1 - smooth(l, 0, FLY / 2) + smooth(l, FLY / 2, FLY);
    } else {
      fly = easeInOut(l / FLY);
    }
    spin = from + (to - from) * fly;
  }

  let stage = STAGE_CAMERA;
  if (resetting >= 0 && resetting < RESET_HALF * 2) stage = STAGE_BUSY;
  else if (o < 0) stage = STAGE_BOX;
  else if (l < 0) stage = v >= ROWS_AT + ROW_IN ? STAGE_FILM : STAGE_BUSY;
  else if (l < LOAD_SETTLED) stage = STAGE_BUSY;
  else if (shotOn) stage = s >= 0.3 ? STAGE_PHOTO : STAGE_BUSY;
  else if (r >= 0 && r < 0.3) stage = STAGE_BUSY;

  return {
    stage,
    header: o < 0 ? 1 : 1 - smooth(o, HEADER_OUT_AT, HEADER_OUT_AT + HEADER_OUT),
    pill: o < 0 ? 1 : 1 - smooth(o, 0, PILL_OUT),
    spinner: o < 0 ? 0 : smooth(o, SPINNER_AT, SPINNER_AT + SPINNER_IN) * (1 - smooth(o, REVEAL_AT, REVEAL_AT + SPINNER_OUT)),
    spinnerTime: Math.max(0, o),
    boxAlpha: o < 0 ? 1 : still ? 1 - smooth(o, 0.3, 0.6) : 1 - smooth(o, BOX_OUT_AT, BOX_OUT_AT + BOX_OUT),
    yaw: BOB_YAW * Math.cos(phase) * sway,
    roll: BOB_ROLL * Math.sin(phase) * sway,
    lift: -BOB_Y * Math.sin(phase) * sway,
    tilt: o < 0 || still ? 0 : TILT * springAt(o, TILT_RESPONSE, TILT_DAMPING),
    flap: o < 0 || still ? 0 : FLAP_OPEN * easeOut((o - FLAPS_AT) / FLAPS_FOR),
    glow: o < 0 || still ? 0 : smooth(o, GLOW_AT, GLOW_AT + GLOW_FOR),
    sweep: o < 0 || still ? -1 : (o - SWEEP_AT) / SWEEP_FOR,
    dive: o < 0 || still ? 0 : easeIn((o - DIVE_AT) / DIVE_FOR),
    name: o < 0 ? 0 : smooth(o, NAME_AT, NAME_AT + NAME_IN),
    canister,
    canisterScale: still || o < 0 ? 1 : CANISTER_FROM + (1 - CANISTER_FROM) * springAt(v, CANISTER_RESPONSE, CANISTER_DAMPING),
    fly,
    spin,
    rows,
    cardOut: l < 0 ? 0 : smooth(l, CARD_OUT_AT, CARD_OUT_AT + CARD_OUT),
    grey: l < 0 ? 0 : smooth(l, 0, LOAD_GREY),
    loadScale: l < 0 || still ? 1 : 1 - (1 - BUTTON_PRESS_SCALE) * pulse(l, PRESS),
    shown: Math.max(0, v),
    camera: l < 0 ? 0 : smooth(l, CAMERA_IN_AT, CAMERA_IN_AT + CAMERA_IN),
    cameraRow: shotOn ? 1 - smooth(s, CAMERA_ROW_OUT_AT, CAMERA_ROW_OUT_AT + CAMERA_ROW_OUT) : r >= 0 ? smooth(r, 0.1, 0.35) : 1,
    photoRow: shotOn ? smooth(s, PHOTO_ROW_IN_AT, PHOTO_ROW_IN_AT + PHOTO_ROW_IN) : r >= 0 ? 1 - smooth(r, 0, 0.2) : 0,
    shutter: shotOn && !still ? 1 - (1 - PRESS_SCALE) * pulse(s, PRESS) : 1,
    flash: shotOn && s < FLASH ? FLASH_ALPHA * (1 - s / FLASH) : 0,
    photo: shotOn ? smooth(s, PHOTO_AT, PHOTO_AT + PHOTO_IN) : r >= 0 ? 1 - smooth(r, 0, PHOTO_OUT) : 0,
    photoScale: shotOn && !still ? PHOTO_FROM - (PHOTO_FROM - 1) * easeOut((s - PHOTO_AT) / PHOTO_IN) : 1,
    overlay,
  };
}

/** When the last action's motion ends; the screen counts its idle time from there. */
export function settledAt(ev: Events) {
  'worklet';
  return Math.max(
    ev.unbox < 0 ? -1 : ev.unbox + OPEN_SETTLED,
    ev.load < 0 ? -1 : ev.load + LOAD_SETTLED,
    ev.shot < 0 ? -1 : ev.shot + SHOT_SETTLED,
    ev.retake < 0 ? -1 : ev.retake + RETAKE_SETTLED,
    ev.reset < 0 ? -1 : ev.reset + RESET_HALF * 2,
  );
}

/** The actions of the autoPlay loop `local` seconds into a loop that started at `base`. */
export function loopEvents(local: number, base: number): Events {
  'worklet';
  return {
    unbox: local >= UNBOX_AT ? base + UNBOX_AT : -1,
    load: local >= LOAD_AT ? base + LOAD_AT : -1,
    shot: local >= SHOOT_AT ? base + SHOOT_AT : -1,
    retake: -1,
    reset: local >= RESET_AT ? base + RESET_AT : -1,
  };
}

/** Where the loop's finger holds the knob, 0 to 1; under reduced motion it jumps. */
export function loopDrag(local: number, still: boolean) {
  'worklet';
  if (local < DRAG_AT || local >= RESET_AT + RESET_HALF) return 0;
  if (still) return 1;
  return easeInOut((local - DRAG_AT) / DRAG_FOR);
}

/** "17h 21m 58s". */
export function countdown(seconds: number) {
  const left = Math.max(0, seconds);
  const h = Math.floor(left / 3600);
  const m = Math.floor((left % 3600) / 60);
  const s = left % 60;
  return `${h}h ${String(m).padStart(2, '0')}m ${String(s).padStart(2, '0')}s`;
}
