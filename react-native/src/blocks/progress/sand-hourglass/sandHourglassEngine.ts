/**
 * The focus timer around the sand: the glass's turn, the minute ruler, the session (set, running,
 * done) and the autoPlay script, kept free of React and Skia so it ports step for step to SwiftUI
 * and Compose. The component runs `tickEngine` at a fixed 1/60 s and draws what it finds.
 *
 * The sand is the clock. A session starts when the glass is turned over; the neck's gate then lets
 * the grains through at the rate that empties the upper bulb in the chosen number of minutes, and the
 * time left is read straight from the grains still above the neck. Turning the glass mid-session
 * does what it does to a real one: the sand that already ran out is now on top and runs back.
 */

import { feedGate, GRAINS, type SandState, stepSand } from './sandHourglassSim';

export const TICK = 1 / 60;
// Passes of the grain grid per tick: grains fall 4 cells per tick.
const PASSES = 4;

// The turn: a spring on the glass's angle (rad), a little under critical so it lands with a sway.
const TURN_STIFFNESS = 38;
const TURN_DAMPING = 7.4;
// Seconds of spin a release throws ahead when it picks which way up the glass lands.
const THROW = 0.22;

// The ruler: minutes it offers, finger travel per minute (pt), the coast after a flick (speed decays
// at this rate, 1/s), its fastest coast (min/s), the speed under which it snaps to a whole minute,
// that snap, and how much of a pull past either end shows.
export const MIN_MINUTES = 1;
export const MAX_MINUTES = 60;
export const RULER_PX = 11;
const COAST_DECAY = 4.5;
const COAST_MAX = 60;
const SNAP_SPEED = 2;
const SNAP_STIFFNESS = 220;
const SNAP_DAMPING = 28;
const RUBBER = 0.3;
// Selection ticks are at most this often (s).
const TICK_GAP = 0.04;
export const DEFAULT_MINUTES = 25;

// Phases of a session and the time the "done" moment holds before the glass is ready again (s).
export const IDLE = 0;
export const RUNNING = 1;
export const DONE = 2;
const DONE_HOLD = 2.6;
// The last grain leaves the neck this long before it lands (s).
const LAST_GRAIN = 0.35;
// A glass turned back before this much of a session has run (s) simply goes back to being set.
const CANCEL_WITHIN = 2;
// The labels and the ruler cross-fade at this rate (1/s).
const FADE_RATE = 6;

// autoPlay (s): the ruler is pulled up to 40 min and back to 25, the glass is turned over at TURN_AT,
// the sand runs out in about 10.5 s with the neck open, the session ends, and the glass rests.
export const LOOP = 18;
const SCRIPT_RULER: number[][] = [
  // [from (s), to (s), minutes from, minutes to]
  [0.6, 1.5, 25, 40],
  [1.9, 2.7, 40, 25],
];
const TURN_AT = 3.1;
// The time of day the script pretends it is, for "Ends at" (minutes).
export const SCRIPT_TIME = 9 * 60 + 41;
// After a touch the loop waits this long, then starts over.
const USER_HOLD = 4;

export type Engine = {
  looping: boolean;
  still: boolean;
  clock: number;
  hold: number;
  time: number;
  // The glass's angle (rad, clockwise), its spin and where its spring is taking it.
  angle: number;
  spin: number;
  target: number;
  turning: boolean;
  grabAngle: number;
  fingerLast: number;
  turned: number;
  /** A turn made by hand: it lands with a haptic. */
  landing: boolean;
  // Ruler: minutes under the needle (fractional while it moves) and what moves it.
  minutes: number;
  rulerV: number;
  rulerDragging: boolean;
  rulerAtGrab: number;
  rulerCoasting: boolean;
  rulerSnapping: boolean;
  lastWhole: number;
  lastTick: number;
  // Session.
  phase: number;
  duration: number;
  endsAt: number;
  doneTime: number;
  lastGrain: number;
  startTime: number;
  /** The session was started by a finger: its end gets a haptic. */
  userSession: boolean;
  /** 0 while setting, 1 while a session runs or has just ended: hides the ruler, shows the end. */
  runBlend: number;
  /** 1 during the "done" moment. */
  doneBlend: number;
  /** Grains above the neck, whichever bulb is up, and the time that leaves (s). */
  remaining: number;
  secondsLeft: number;
  // Answered with haptics by the component.
  userTicks: number;
  userLandings: number;
  userCompletions: number;
};

export function makeEngine(looping: boolean, still: boolean): Engine {
  'worklet';
  return {
    looping,
    still,
    clock: 0,
    hold: 0,
    time: 0,
    angle: 0,
    spin: 0,
    target: 0,
    turning: false,
    grabAngle: 0,
    fingerLast: 0,
    turned: 0,
    landing: false,
    minutes: DEFAULT_MINUTES,
    rulerV: 0,
    rulerDragging: false,
    rulerAtGrab: DEFAULT_MINUTES,
    rulerCoasting: false,
    rulerSnapping: false,
    lastWhole: DEFAULT_MINUTES,
    lastTick: -1,
    phase: IDLE,
    duration: DEFAULT_MINUTES,
    endsAt: 0,
    doneTime: 0,
    lastGrain: 0,
    startTime: 0,
    userSession: false,
    runBlend: 0,
    doneBlend: 0,
    remaining: 0,
    secondsLeft: DEFAULT_MINUTES * 60,
    userTicks: 0,
    userLandings: 0,
    userCompletions: 0,
  };
}

/** Minutes the ruler shows, as a whole number in range. */
export function chosenMinutes(e: Engine) {
  'worklet';
  return Math.max(MIN_MINUTES, Math.min(MAX_MINUTES, Math.round(e.minutes)));
}

function touched(e: Engine, user: boolean) {
  'worklet';
  if (user) e.hold = USER_HOLD;
}

/** A session starts (or a finished one starts again) as the glass goes over. */
function startSession(e: Engine, wallMinutes: number, user: boolean) {
  'worklet';
  if (e.phase === RUNNING) return;
  e.phase = RUNNING;
  e.startTime = e.time;
  e.userSession = user;
  e.duration = chosenMinutes(e);
  e.endsAt = (e.looping ? SCRIPT_TIME : wallMinutes) + e.duration;
  e.lastGrain = 0;
}

/** Sends the glass over to the next way up. */
export function flip(e: Engine, user: boolean, wallMinutes: number) {
  'worklet';
  touched(e, user);
  e.target = Math.round(e.target / Math.PI) * Math.PI + Math.PI;
  e.landing = user;
  startSession(e, wallMinutes, user);
}

/** A finger takes hold of the glass (a drag around it or a two-finger twist). */
export function grabGlass(e: Engine, user: boolean) {
  'worklet';
  touched(e, user);
  e.turning = true;
  e.grabAngle = e.angle;
  e.spin = 0;
  e.turned = 0;
}

/** The glass follows the finger: `turn` rad from where it was grabbed. */
export function turnGlass(e: Engine, turn: number) {
  'worklet';
  if (!e.turning || !Number.isFinite(turn)) return;
  e.turned = turn;
  e.angle = e.grabAngle + turn;
}

/** Lets go at `velocity` rad/s: the spring takes the glass to the nearest way up, thrown ahead. */
export function releaseGlass(e: Engine, velocity: number, user: boolean, wallMinutes: number) {
  'worklet';
  if (!e.turning) return;
  e.turning = false;
  touched(e, user);
  const before = Math.round(e.grabAngle / Math.PI) * Math.PI;
  const v = e.still || !Number.isFinite(velocity) ? 0 : Math.max(-30, Math.min(30, velocity));
  const goal = Math.round((e.angle + v * THROW) / Math.PI) * Math.PI;
  e.spin = v;
  e.target = goal;
  e.landing = user && goal !== before;
  if (goal !== before) startSession(e, wallMinutes, user);
}

/** The ruler only listens while a session is being set. */
export function rulerLive(e: Engine) {
  'worklet';
  return e.phase === IDLE && e.runBlend < 0.5;
}

export function grabRuler(e: Engine, user: boolean) {
  'worklet';
  touched(e, user);
  e.rulerDragging = true;
  e.rulerCoasting = false;
  e.rulerSnapping = false;
  e.rulerV = 0;
  e.rulerAtGrab = e.minutes;
}

/** Past either end the ruler gives way less and less. */
function rubber(raw: number) {
  'worklet';
  if (raw < MIN_MINUTES) {
    const d = MIN_MINUTES - raw;
    return MIN_MINUTES - (d * RUBBER) / (1 + d * 0.08);
  }
  if (raw > MAX_MINUTES) {
    const d = raw - MAX_MINUTES;
    return MAX_MINUTES + (d * RUBBER) / (1 + d * 0.08);
  }
  return raw;
}

/** A selection tick each time a new whole minute comes under the needle. */
function answerRuler(e: Engine, user: boolean) {
  'worklet';
  const whole = chosenMinutes(e);
  if (whole === e.lastWhole) return;
  e.lastWhole = whole;
  if (user && e.time - e.lastTick >= TICK_GAP) {
    e.lastTick = e.time;
    e.userTicks += 1;
  }
}

/** A drag to the left brings longer times under the needle. */
export function dragRuler(e: Engine, translationX: number, user: boolean) {
  'worklet';
  if (!e.rulerDragging || !Number.isFinite(translationX)) return;
  touched(e, user);
  e.minutes = rubber(e.rulerAtGrab - translationX / RULER_PX);
  answerRuler(e, user);
}

export function releaseRuler(e: Engine, velocityX: number, user: boolean) {
  'worklet';
  if (!e.rulerDragging) return;
  e.rulerDragging = false;
  touched(e, user);
  const v = e.still || !Number.isFinite(velocityX) ? 0 : -velocityX / RULER_PX;
  e.rulerV = Math.max(-COAST_MAX, Math.min(COAST_MAX, v));
  const outside = e.minutes < MIN_MINUTES || e.minutes > MAX_MINUTES;
  e.rulerCoasting = !outside;
  e.rulerSnapping = outside;
  if (outside) e.rulerV = 0;
}

function stepRuler(e: Engine) {
  'worklet';
  if (e.rulerCoasting) {
    e.minutes += e.rulerV * TICK;
    e.rulerV *= Math.exp(-COAST_DECAY * TICK);
    if (e.minutes < MIN_MINUTES || e.minutes > MAX_MINUTES) {
      e.minutes = Math.max(MIN_MINUTES, Math.min(MAX_MINUTES, e.minutes));
      e.rulerV = 0;
      e.rulerCoasting = false;
      e.rulerSnapping = true;
    } else if (Math.abs(e.rulerV) < SNAP_SPEED) {
      e.rulerCoasting = false;
      e.rulerSnapping = true;
    }
    answerRuler(e, true);
  } else if (e.rulerSnapping) {
    const goal = chosenMinutes(e);
    e.rulerV += (SNAP_STIFFNESS * (goal - e.minutes) - SNAP_DAMPING * e.rulerV) * TICK;
    e.minutes += e.rulerV * TICK;
    if (Math.abs(e.minutes - goal) < 0.002 && Math.abs(e.rulerV) < 0.02) {
      e.minutes = goal;
      e.rulerV = 0;
      e.rulerSnapping = false;
    }
    answerRuler(e, true);
  }
}

/** Puts the loop back at its resting state. */
function restartScript(e: Engine) {
  'worklet';
  e.clock = 0;
  e.minutes = DEFAULT_MINUTES;
  e.lastWhole = DEFAULT_MINUTES;
  e.rulerV = 0;
  e.rulerDragging = false;
  e.rulerCoasting = false;
  e.rulerSnapping = false;
}

function easeInOut(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

function runScript(e: Engine, before: number, after: number, wallMinutes: number) {
  'worklet';
  for (let i = 0; i < SCRIPT_RULER.length; i += 1) {
    const [t0, t1, m0, m1] = SCRIPT_RULER[i];
    if (after > t0 && before < t1) {
      const u = Math.min(1, (after - t0) / (t1 - t0));
      e.minutes = m0 + (m1 - m0) * easeInOut(u);
      answerRuler(e, false);
    }
  }
  if (before < TURN_AT && after >= TURN_AT) flip(e, false, wallMinutes);
}

/**
 * One fixed tick: the script, the turn's spring, the ruler, the gate, the sand, then the session.
 * `paint` has just counted the grains (st.upper, st.lower, st.neck) from the previous tick.
 * Returns true when a turn made by hand has just landed.
 */
export function tickEngine(e: Engine, st: SandState, wallMinutes: number) {
  'worklet';
  e.time += TICK;
  let landed = false;
  if (!e.turning) {
    if (e.hold > 0) {
      e.hold -= TICK;
      if (e.hold <= 0 && e.looping) restartScript(e);
    } else if (e.looping) {
      const before = e.clock;
      const after = before + TICK;
      runScript(e, before, after, wallMinutes);
      e.clock = after;
      if (after >= LOOP) restartScript(e);
    }
    const a = e.angle;
    const goal = e.target;
    if (e.still) {
      e.angle = goal;
      e.spin = 0;
      if (e.landing) {
        e.landing = false;
        landed = true;
      }
    } else {
      const w = e.spin + (TURN_STIFFNESS * (goal - a) - TURN_DAMPING * e.spin) * TICK;
      const next = a + w * TICK;
      if (e.landing && (a - goal) * (next - goal) <= 0) {
        e.landing = false;
        landed = true;
      }
      e.spin = w;
      e.angle = next;
    }
    // A whole turn changes nothing on screen, so the numbers are kept small.
    if (Math.abs(e.target) > 2 * Math.PI + 0.1) {
      const turn = Math.sign(e.target) * 2 * Math.PI;
      e.target -= turn;
      e.angle -= turn;
    }
  }
  if (!e.rulerDragging) stepRuler(e);

  // The neck lets the grains through at the session's rate; the self-playing loop runs it open.
  const minutes = e.phase === RUNNING ? e.duration : chosenMinutes(e);
  st.gate = e.looping ? -1 : GRAINS / (minutes * 60);
  feedGate(st, TICK);
  const a = e.angle;
  for (let p = 0; p < PASSES; p += 1) stepSand(st, Math.sin(a), Math.cos(a));

  // The time left is the sand above the neck.
  const up = Math.cos(e.angle) >= 0 ? st.upper : st.lower;
  e.remaining = up;
  const length = (e.phase === RUNNING ? e.duration : chosenMinutes(e)) * 60;
  e.secondsLeft = e.phase === IDLE ? chosenMinutes(e) * 60 : (up / GRAINS) * length;

  if (e.phase === RUNNING) {
    // Only once the glass has landed: before a turn the empty bulb is still on top.
    const upright = !e.turning && Math.abs(e.angle - e.target) < 0.15 && Math.abs(e.spin) < 1;
    if (upright && up === 0 && st.neck === 0) {
      e.lastGrain += TICK;
      if (e.time - e.startTime < CANCEL_WITHIN) {
        // Turned straight back: nothing ran, so there is nothing to celebrate.
        e.phase = IDLE;
      } else if (e.lastGrain >= LAST_GRAIN) {
        e.phase = DONE;
        e.doneTime = 0;
        if (e.userSession) e.userCompletions += 1;
      }
    } else {
      e.lastGrain = 0;
    }
  } else if (e.phase === DONE) {
    e.doneTime += TICK;
    if (e.doneTime >= DONE_HOLD) e.phase = IDLE;
  }
  const fade = 1 - Math.exp(-FADE_RATE * TICK);
  e.runBlend += ((e.phase === IDLE ? 0 : 1) - e.runBlend) * fade;
  e.doneBlend += ((e.phase === DONE ? 1 : 0) - e.doneBlend) * fade;
  return landed;
}

/** "25:00", "4:07". */
export function formatClock(seconds: number) {
  'worklet';
  const whole = Math.max(0, Math.ceil(Number.isFinite(seconds) ? seconds : 0));
  const m = Math.floor(whole / 60);
  const s = whole % 60;
  return `${m < 10 ? '0' : ''}${m}:${s < 10 ? '0' : ''}${s}`;
}

/** "Ends at 10:06". */
export function endsAtText(e: Engine) {
  'worklet';
  const t = ((Math.round(e.endsAt) % 1440) + 1440) % 1440;
  const h = Math.floor(t / 60);
  const m = t % 60;
  return `Ends at ${h < 10 ? '0' : ''}${h}:${m < 10 ? '0' : ''}${m}`;
}

/** How long the "done" moment has been on (s), for the light sweep and the settle. */
export function doneAge(e: Engine) {
  'worklet';
  return e.phase === DONE ? e.doneTime : -1;
}
