// The dial's mechanism: the finger wheel's angle, the drag that winds it toward the finger stop,
// and the governed return that counts one pulse for each number that passes the stop. Plain
// arithmetic on a plain object, stepped in fixed substeps on the UI thread, so it ports to Swift
// and Kotlin line for line.
//
// Angles are in degrees on the screen: 0 points right and positive turns clockwise (screen y
// grows downward). `phi` is how far the wheel is wound clockwise from rest.

/** The finger stop's angle. */
export const STOP_ANGLE = 52;
/** Angle between neighbouring holes. */
export const HOLE_STEP = 30;
/** Holes, in dial order: digits 1…9 then 0. */
export const HOLES = 10;
/** The digit each hole dials. */
export const HOLE_DIGITS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 0];
// A finger in a hole meets the stop STOP_GAP degrees before the hole's centre would reach it.
const STOP_GAP = 16;
// Past the stop the wheel gives at most OVER_MAX degrees (rubber band, OVER_SOFT degrees soft);
// below rest it gives UNDER_MAX.
const OVER_MAX = 11;
const OVER_SOFT = 20;
const UNDER_MAX = 3;
const UNDER_SOFT = 8;
// A release within REACH_SLACK degrees of the stop still counts as reaching it.
const REACH_SLACK = 2;
// The governor: the wheel returns at RETURN_SPEED degrees per second (ten pulses a second),
// reaching that speed with a time constant of GOVERNOR_LAG s.
const RETURN_SPEED = 300;
const GOVERNOR_LAG = 0.035;
// Landing on the rest stop it bounces back BOUNCE of its speed, then settles on a spring.
const BOUNCE = 0.13;
const SETTLE_K = 900;
const SETTLE_DAMP = 30;
// Each pulse twitches the finger stop (degrees per second of kick, spring back).
const KICK = 34;
const KICK_K = 1400;
const KICK_DAMP = 26;

const SUBSTEP = 1 / 240;
export const MAX_FRAME = 1 / 30;

export const EVENT_TICK = 1;
export const EVENT_STOP = 2;
export const EVENT_DIGIT = 4;
export const EVENT_CLEAR = 8;

const MODE_REST = 0;
const MODE_DRAG = 1;
const MODE_RETURN = 2;
const MODE_SETTLE = 3;

export type DialSim = {
  phi: number;
  omega: number;
  mode: number;
  /** The hole the finger is in (0…9), or -1. */
  hole: number;
  touch: number;
  scripted: boolean;
  grabFinger: number;
  grabPhi: number;
  /** Raw wind the finger asks for (before the stops). */
  wish: number;
  reached: boolean;
  dialling: boolean;
  /** Pulses counted on this return. */
  pulses: number;
  /** The digit the last finished return dialled. */
  digit: number;
  kick: number;
  kickV: number;
  /** 0…1, how far the number card is pressed in. */
  card: number;
  cardTarget: number;
  reduced: boolean;
  events: number;
  eventsByUser: boolean;
};

export function createDial(reduced: boolean): DialSim {
  'worklet';
  return {
    phi: 0,
    omega: 0,
    mode: MODE_REST,
    hole: -1,
    touch: -1,
    scripted: false,
    grabFinger: 0,
    grabPhi: 0,
    wish: 0,
    reached: false,
    dialling: false,
    pulses: 0,
    digit: -1,
    kick: 0,
    kickV: 0,
    card: 0,
    cardTarget: 0,
    reduced,
    events: 0,
    eventsByUser: false,
  };
}

/** Where hole i sits at rest. */
export function holeRest(i: number) {
  'worklet';
  return STOP_ANGLE - (i + 2) * HOLE_STEP;
}

/** How far hole i winds before the finger meets the stop. */
export function holeTravel(i: number) {
  'worklet';
  return (i + 2) * HOLE_STEP - STOP_GAP;
}

/** `a` moved by whole turns to lie within half a turn of `near`. */
function nearest(a: number, near: number) {
  'worklet';
  return a - 360 * Math.round((a - near) / 360);
}

/** The hole whose disc contains the point at (angle, radius) from the centre, or -1. */
export function holeAt(s: DialSim, angle: number, radius: number, holeR: number, ringR: number) {
  'worklet';
  for (let i = 0; i < HOLES; i += 1) {
    // A hole already wound past the stop cannot be taken (the wheel would jump back to it).
    if (holeTravel(i) < s.phi) continue;
    const a = ((holeRest(i) + s.phi) * Math.PI) / 180;
    const t = (angle * Math.PI) / 180;
    const dx = radius * Math.cos(t) - ringR * Math.cos(a);
    const dy = radius * Math.sin(t) - ringR * Math.sin(a);
    if (dx * dx + dy * dy < holeR * holeR * 1.25) return i;
  }
  return -1;
}

/** A finger lands in hole `hole` at `angle`: it takes the wheel, wherever it was. */
export function dialGrab(s: DialSim, id: number, hole: number, angle: number, scripted: boolean) {
  'worklet';
  s.touch = id;
  s.scripted = scripted;
  s.hole = hole;
  s.mode = MODE_DRAG;
  s.grabFinger = angle;
  s.grabPhi = s.phi;
  s.wish = s.phi;
  s.omega = 0;
  s.reached = false;
  // Catching the wheel mid-return loses that number.
  s.dialling = false;
  s.pulses = 0;
}

/** The finger moved to `angle`; it is unwrapped against the last angle so it can go round. */
export function dialDrag(s: DialSim, id: number, angle: number) {
  'worklet';
  if (s.touch !== id || s.mode !== MODE_DRAG) return;
  const a = nearest(angle, s.grabFinger + (s.wish - s.grabPhi));
  s.wish = s.grabPhi + (a - s.grabFinger);
  const limit = holeTravel(s.hole);
  let phi = s.wish;
  if (phi > limit) phi = limit + OVER_MAX * (1 - Math.exp(-(phi - limit) / OVER_SOFT));
  else if (phi < 0) phi = -UNDER_MAX * (1 - Math.exp(phi / UNDER_SOFT));
  s.phi = phi;
  if (!s.reached && s.wish >= limit - REACH_SLACK) {
    s.reached = true;
    s.events |= EVENT_STOP;
    if (!s.scripted) s.eventsByUser = true;
  }
}

/** The finger lets go: the governor takes the wheel home, dialling if it reached the stop. */
export function dialRelease(s: DialSim, id: number) {
  'worklet';
  if (s.touch !== id) return;
  s.touch = -1;
  if (s.mode !== MODE_DRAG) return;
  s.dialling = s.reached;
  s.pulses = 0;
  s.mode = s.phi > 0 ? MODE_RETURN : MODE_SETTLE;
  s.omega = 0;
}

/** Long press on the number card: pressed while held, clears the strip when it fires. */
export function dialCardPress(s: DialSim, down: boolean) {
  'worklet';
  s.cardTarget = down ? 1 : 0;
}

/** A real finger takes over: the script's finger lets go without dialling. */
export function dialCancelScripted(s: DialSim) {
  'worklet';
  if (s.scripted && s.touch !== -1) {
    s.touch = -1;
    s.reached = false;
    s.dialling = false;
    if (s.mode === MODE_DRAG) s.mode = s.phi > 0 ? MODE_RETURN : MODE_SETTLE;
  }
  if (s.scripted) s.dialling = false;
  s.cardTarget = 0;
}

export function dialClear(s: DialSim, byUser: boolean) {
  'worklet';
  s.events |= EVENT_CLEAR;
  if (byUser) s.eventsByUser = true;
}

export function dialStep(s: DialSim, frame: number) {
  'worklet';
  if (!(frame > 0)) return;
  const dt = Math.min(frame, MAX_FRAME);
  const n = Math.max(1, Math.ceil(dt / SUBSTEP - 1e-6));
  const h = dt / n;
  for (let k = 0; k < n; k += 1) {
    if (s.mode === MODE_RETURN) {
      const before = s.phi;
      s.omega += (-RETURN_SPEED - s.omega) * (1 - Math.exp(-h / GOVERNOR_LAG));
      s.phi += s.omega * h;
      // A pulse for every multiple of HOLE_STEP the wheel passes on its way home.
      if (s.dialling && Math.floor(before / HOLE_STEP) > Math.floor(Math.max(s.phi, 0) / HOLE_STEP) && before > 0) {
        s.pulses += 1;
        s.events |= EVENT_TICK;
        if (!s.scripted) s.eventsByUser = true;
        s.kickV += KICK;
      }
      if (s.phi <= 0) {
        s.phi = 0;
        s.omega = s.reduced ? 0 : -s.omega * BOUNCE;
        s.mode = MODE_SETTLE;
        if (s.dialling) {
          s.digit = s.pulses % 10;
          s.events |= EVENT_DIGIT;
          if (!s.scripted) s.eventsByUser = true;
        }
        s.dialling = false;
      }
    } else if (s.mode === MODE_SETTLE) {
      s.omega += (-SETTLE_K * s.phi - SETTLE_DAMP * s.omega) * h;
      s.phi += s.omega * h;
      if (s.phi < 0) {
        s.phi = 0;
        s.omega = -s.omega * 0.3;
      }
      if (Math.abs(s.phi) < 0.02 && Math.abs(s.omega) < 0.5) {
        s.phi = 0;
        s.omega = 0;
        s.mode = MODE_REST;
      }
    }
    // The finger stop's twitch.
    s.kickV += (-KICK_K * s.kick - KICK_DAMP * s.kickV) * h;
    s.kick += s.kickV * h;
    // The card follows the press quickly.
    s.card += (s.cardTarget - s.card) * (1 - Math.exp(-h * 18));
  }
}

// ---------------------------------------------------------------------------------------------
// autoPlay: dials four digits, then presses the card to clear the strip, and rests. Each digit:
// a finger settles into its hole, winds it to the stop on an ease-in-out curve, rests against the
// stop, lets go, and waits for the governed return.

/** The number the script dials. */
const SCRIPT_DIGITS = [4, 1, 0, 7];
const START = 0.7;
const FINGER_IN = 0.28;
const DRAG_SPEED = 250;
const DRAG_MIN = 0.32;
const HOLD = 0.2;
const AFTER = 0.42;
const CLEAR_HOLD = 0.75;
const CLEAR_REST = 1.3;

type Stroke = { at: number; hole: number; drag: number; release: number };

function holeOf(digit: number) {
  'worklet';
  return digit === 0 ? 9 : digit - 1;
}

function buildScript() {
  'worklet';
  const strokes: Stroke[] = [];
  let t = START;
  for (let k = 0; k < SCRIPT_DIGITS.length; k += 1) {
    const hole = holeOf(SCRIPT_DIGITS[k]);
    const travel = holeTravel(hole);
    const drag = Math.max(DRAG_MIN, travel / DRAG_SPEED);
    const release = t + FINGER_IN + drag + HOLD;
    strokes.push({ at: t, hole, drag, release });
    t = release + travel / RETURN_SPEED + AFTER;
  }
  return { strokes, clearAt: t, end: t + CLEAR_HOLD + CLEAR_REST };
}

const PLAN = buildScript();
/** Seconds the script takes before it starts over. */
export const LOOP = Math.ceil(PLAN.end * 2) / 2;
export const SCRIPT_ID = 1000;

function easeInOut(p: number) {
  'worklet';
  const t = Math.min(1, Math.max(0, p));
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

/** True when the loop's clock went past `at` between `before` and `now` (wrapping at LOOP). */
function passed(before: number, now: number, at: number) {
  'worklet';
  return now >= before ? before < at && now >= at : at > before || at <= now;
}

/**
 * Drives the scripted finger from the loop's clock, which moved from `before` to `now`. Returns
 * where the finger is drawn: [hole, angle, opacity] (hole -1 when it is away).
 */
export function dialScript(s: DialSim, before: number, now: number) {
  'worklet';
  let hole = -1;
  let angle = 0;
  let shown = 0;
  for (let k = 0; k < PLAN.strokes.length; k += 1) {
    const st = PLAN.strokes[k];
    const rest = holeRest(st.hole);
    const travel = holeTravel(st.hole);
    if (passed(before, now, st.at + FINGER_IN)) dialGrab(s, SCRIPT_ID, st.hole, rest, true);
    if (now >= st.at && now < st.release + 0.3) {
      hole = st.hole;
      const u = (now - st.at - FINGER_IN) / st.drag;
      // A little past the stop, so the wheel visibly presses into it.
      const wind = (travel + 5) * easeInOut(u);
      angle = rest + (now >= st.release ? travel : Math.max(0, wind));
      shown = Math.min(1, (now - st.at) / FINGER_IN, Math.max(0, (st.release + 0.3 - now) / 0.3));
      if (s.touch === SCRIPT_ID && now < st.release) dialDrag(s, SCRIPT_ID, angle);
    }
    if (passed(before, now, st.release)) dialRelease(s, SCRIPT_ID);
  }
  if (passed(before, now, PLAN.clearAt)) dialCardPress(s, true);
  if (passed(before, now, PLAN.clearAt + CLEAR_HOLD)) {
    dialCardPress(s, false);
    dialClear(s, false);
  }
  return [hole, angle, shown];
}
