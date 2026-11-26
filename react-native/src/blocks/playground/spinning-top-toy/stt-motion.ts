/**
 * The clocks of the screen, stepped on the UI thread once per frame. The pose functions are the designer's
 * (`ref.html` of #54), written for any flick: a run starts at spin speed W0 and decays e^(−0.32 τ); it falls when
 * the speed reaches the loop's W(5.2 s) = 13.3 rad/s, so its duration is D = ln(W0 / 13.3) / 0.32 (5.2 s for the
 * loop's W0 = 70). Tilt and wander grow with τ / D, the precession is 2π (1.1 τ + 0.55 τ² / D); the fall tips it to
 * −0.745 rad in 0.42 s, it rocks, and the band cools to an ember.
 *
 * The loop (7.6 s) flicks at 0.4 s with W0 = 70 and resets with a fade (7.0–7.6 s). A flick or a tap takes over;
 * once the top is down and still, 2 s after the last touch it resets the same way and the loop restarts at t = 0.
 * Reduce Motion: no wobble, wander, fall or hot-spot travel; the glow, the timer and the ember run in place.
 */

/** Loop period, s. */
export const LOOP = 7.6;
const FL = 0.4;
const DECAY = 0.32;
const LOOP_W0 = 70;
const LOOP_D = 5.2;
/** The speed at which a run falls (the loop's speed after 5.2 s), rad/s. */
const W_FALL = LOOP_W0 * Math.exp(-DECAY * LOOP_D);
const SPIN_DOWN = 0.35;
const TIP_TIME = 0.42;
const COOL_FROM = 0.42;
const COOL_TO = 1.02;
const FALLEN = -0.745;
const SLIDE = 46;
export const REST_TIP = { x: 203.7, y: 603.5 };
const TRAIL = 24;
const TRAIL_STEP = 0.09;
/** The reset: fade out 0.25 s, 0.1 s gap, fade in standing 0.25 s (7.0–7.6 s in the loop). */
const FADE_OUT = 0.25;
const GAP = 0.1;
const FADE_IN = 0.25;
const IDLE = 2.0;
const NUDGE = 0.05;
const NUDGE_DECAY = 3;
/** The best time on the chip (the designer's answer 4 on #54: a strong flick can beat it, the loop's 5.2 s cannot). */
export const BEST = 5.4;

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
function io(x: number) {
  'worklet';
  return x < 0.5 ? 2 * x * x : 1 - Math.pow(-2 * x + 2, 2) / 2;
}

/** How long a run starting at w0 spins before it falls, s. */
export function durationOf(w0: number) {
  'worklet';
  return Math.log(Math.max(w0, W_FALL) / W_FALL) / DECAY;
}

/** Spin speed τ s after the flick. */
function speedAt(tau: number, w0: number, D: number) {
  'worklet';
  if (tau < 0) return 0;
  if (tau < D) return w0 * Math.exp(-DECAY * tau);
  if (tau < D + SPIN_DOWN) return W_FALL * (1 - seg(tau, D, D + SPIN_DOWN));
  return 0;
}

/** Spin angle: the integral of the speed. */
function spinAt(tau: number, w0: number, D: number) {
  'worklet';
  if (tau < 0) return 0;
  let a = (w0 / DECAY) * (1 - Math.exp(-DECAY * Math.min(tau, D)));
  if (tau > D) {
    const u = Math.min(tau, D + SPIN_DOWN) - D;
    a += W_FALL * (u - (u * u) / (2 * SPIN_DOWN));
  }
  return a;
}

function tiltAt(tau: number, D: number) {
  'worklet';
  const m = 0.035 + 0.3 * Math.pow(clamp01(tau / D), 3);
  const tt = Math.min(tau, D);
  const psi = 2 * Math.PI * (1.1 * tt + (0.55 * tt * tt) / D);
  return m * Math.sin(psi);
}

function wanderAt(tau: number, D: number) {
  'worklet';
  const tt = Math.min(Math.max(tau, 0), D);
  const r = 14 + 58 * clamp01(tau / D);
  const a = 1.3 * tt + 0.9;
  return { x: r * Math.cos(a) - r * Math.cos(0.9), y: 0.28 * (r * Math.sin(a) - r * Math.sin(0.9)) };
}

export type Pose = { x: number; y: number; A: number };

/** The tip and the tilt τ s after the flick (τ < 0: standing ready). */
export function poseAt(tau: number, w0: number, D: number, still: boolean): Pose {
  'worklet';
  if (tau < 0 || still) return { x: REST_TIP.x, y: REST_TIP.y, A: 0 };
  if (tau < D) {
    const w = wanderAt(tau, D);
    return { x: REST_TIP.x + w.x, y: REST_TIP.y + w.y, A: tiltAt(tau, D) };
  }
  const w = wanderAt(D, D);
  // ref.html: the tilt magnitude just before the fall, at the precession phase of the fall.
  const a0 = (0.035 + 0.3 * Math.pow((D - 1e-3) / D, 3)) * Math.sin(2 * Math.PI * (1.1 * D + 0.55 * D));
  const f = seg(tau, D, D + TIP_TIME);
  let A = a0 + (FALLEN - a0) * f * f;
  if (tau > D + TIP_TIME) {
    const u = tau - (D + TIP_TIME);
    A = FALLEN + 0.07 * Math.exp(-u * 7) * Math.cos(u * 24);
  }
  return { x: REST_TIP.x + w.x + SLIDE * io(f), y: REST_TIP.y + w.y, A };
}

function glowAt(tau: number, w0: number, D: number) {
  'worklet';
  if (tau < 0) return 0.35;
  if (tau < D) return Math.min(1, 0.35 + 0.65 * Math.min(1, tau / 0.15) * (0.55 + (0.45 * speedAt(tau, w0, D)) / LOOP_W0));
  if (tau < D + COOL_FROM) return 0.7;
  return 0.7 - 0.52 * io(seg(tau, D + COOL_FROM, D + COOL_TO));
}

export type Frame = {
  tip: number[];
  A: number;
  spin: number;
  W: number;
  glow: number;
  alpha: number;
  fall: number;
  /** 24 × (x, y, strength). */
  trail: number[][];
  /** Seconds spun, and whether it is down (the line changes). */
  secs: number;
  down: number;
  /** The pill pressed (0 / 1), the best time and its pop scale. */
  pill: number;
  best: number;
  bestPop: number;
};

/** Everything τ s after a flick at w0 (alpha and trail fade given by the caller). */
function runFrame(tau: number, w0: number, D: number, still: boolean, alpha: number, trailFade: number, nudge: number): Frame {
  'worklet';
  const P = poseAt(tau, w0, D, still);
  const A = still ? 0 : P.A + (tau >= 0 && tau < D ? nudge : 0);
  const trail: number[][] = [];
  for (let i = 0; i < TRAIL; i++) {
    const ta = tau - (i + 1) * TRAIL_STEP;
    if (ta < 0 || trailFade <= 0 || still) {
      trail.push([0, 0, 0]);
      continue;
    }
    const pa = poseAt(Math.min(ta, D), w0, D, still);
    trail.push([pa.x, pa.y, (1 - i / TRAIL) * Math.min(1, speedAt(ta, w0, D) / 20) * trailFade]);
  }
  return {
    tip: [P.x, P.y], A, spin: still ? 0 : spinAt(tau, w0, D), W: still ? 0 : speedAt(tau, w0, D), glow: glowAt(tau, w0, D),
    // The shadow stretches only once the fall starts (the designer's answer 2; ref.html stretched it on left tilts too).
    alpha, fall: tau >= D && !still ? clamp01(A / FALLEN) * alpha : 0, trail, secs: tau < 0 ? 0 : Math.min(tau, D), down: tau >= D ? 1 : 0, pill: 0,
    best: BEST, bestPop: 1,
  };
}

/** The loop at time t (ref.html: from 7.35 s the top stands ready again while it fades in). */
export function loopFrame(t: number): Frame {
  'worklet';
  const tr = t >= 7.35 ? 0 : t;
  const alpha = t < 7.0 ? 1 : t < 7.25 ? 1 - seg(t, 7.0, 7.25) : seg(t, 7.35, 7.6);
  const trailFade = tr >= 7.25 ? 0 : tr < 7.0 ? 1 : 1 - seg(tr, 7.0, 7.25);
  const F = runFrame(tr - FL, LOOP_W0, LOOP_D, false, alpha, trailFade, 0);
  F.down = tr >= FL + LOOP_D && tr < 7.25 ? 1 : 0;
  F.pill = t > 0.25 && t < 0.45 ? 1 : 0;
  return F;
}

export const MODE = { auto: 0, live: 1 } as const;
/** Live phases. */
const PHASE = { ready: 0, run: 1, reset: 2 } as const;

export type Core = {
  mode: number;
  last: number;
  start: number;
  still: boolean;
  phase: number;
  /** The run: its start speed, duration and time since the flick (s). */
  w0: number;
  D: number;
  tau: number;
  /** The nudge on the wobble (rad), decaying. */
  nudge: number;
  /** The reset's clock (s), and a flick to play when it ends (0: none). */
  resetT: number;
  flickAfter: number;
  /** The pill held. */
  pill: boolean;
  best: number;
  bestAge: number;
  touches: number;
  idle: number;
  /** Events for the JS side: flicks and landings (haptics and announcements). */
  flicks: number;
  lands: number;
  landed: boolean;
  out: Frame;
};

export function newCore(): Core {
  return {
    mode: MODE.auto, last: -1, start: -1, still: false, phase: PHASE.ready, w0: LOOP_W0, D: LOOP_D, tau: -1, nudge: 0,
    resetT: 0, flickAfter: 0, pill: false, best: BEST, bestAge: 1e3, touches: 0, idle: 0, flicks: 0, lands: 0,
    landed: false, out: loopFrame(0),
  };
}

/** Leave the loop at the frame on screen. */
function takeOver(c: Core) {
  'worklet';
  if (c.mode !== MODE.auto) return;
  const t = c.start < 0 ? 0 : ((c.last - c.start) / 1000) % LOOP;
  c.mode = MODE.live;
  c.w0 = LOOP_W0;
  c.D = LOOP_D;
  c.nudge = 0;
  c.landed = false;
  if (t < FL || t >= 7.35) {
    c.phase = PHASE.ready;
    c.tau = -1;
  } else if (t < 7.0) {
    c.phase = PHASE.run;
    c.tau = t - FL;
    c.landed = c.tau >= c.D + TIP_TIME;
  } else {
    // In the reset: carry on from its clock.
    c.phase = PHASE.reset;
    c.tau = Math.min(t, 7.25) - FL;
    c.resetT = t - 7.0;
  }
}

function flick(c: Core, w0: number) {
  'worklet';
  c.phase = PHASE.run;
  c.w0 = Math.min(90, Math.max(40, w0));
  c.D = durationOf(c.w0);
  c.tau = 0;
  c.nudge = 0;
  c.landed = false;
  c.flicks += 1;
}

/** A flick on the top (speed in pt/s, 0 for a tap) or a tap on the pill. */
export function spin(c: Core, speed: number, onTop: boolean) {
  'worklet';
  takeOver(c);
  c.idle = 0;
  const w0 = speed > 0 ? speed * 0.05 : LOOP_W0;
  if (c.phase === PHASE.ready) {
    flick(c, w0);
  } else if (c.phase === PHASE.run && c.tau < c.D) {
    // A tap on the spinning top nudges it; the pill does nothing mid-spin.
    if (onTop && speed <= 0) c.nudge += NUDGE;
  } else if (c.phase === PHASE.run) {
    // Down: stand it up again (the reset fade), then spin.
    c.phase = PHASE.reset;
    c.resetT = 0;
    c.flickAfter = w0;
  } else {
    c.flickAfter = w0;
  }
}

export function pillDown(c: Core, down: boolean) {
  'worklet';
  takeOver(c);
  c.pill = down;
  c.touches = Math.max(0, c.touches + (down ? 1 : -1));
  c.idle = 0;
}

export function touchDown(c: Core) {
  'worklet';
  takeOver(c);
  c.touches += 1;
  c.idle = 0;
}

export function touchUp(c: Core) {
  'worklet';
  c.touches = Math.max(0, c.touches - 1);
  c.idle = 0;
}

function liveFrame(c: Core): Frame {
  'worklet';
  let F: Frame;
  if (c.phase === PHASE.reset) {
    const r = c.resetT;
    const standing = r >= FADE_OUT + GAP;
    const alpha = r < FADE_OUT ? 1 - r / FADE_OUT : standing ? clamp01((r - FADE_OUT - GAP) / FADE_IN) : 0;
    F = runFrame(standing ? -1 : c.tau, c.w0, c.D, c.still, alpha, standing ? 0 : 1 - clamp01(r / FADE_OUT), 0);
    F.down = standing ? 0 : 1;
  } else {
    F = runFrame(c.tau, c.w0, c.D, c.still, 1, 1, c.nudge);
  }
  F.pill = c.pill ? 1 : 0;
  F.best = c.best;
  F.bestPop = c.bestAge < 0.3 ? 1 + 0.12 * Math.sin((Math.PI * c.bestAge) / 0.3) : 1;
  return F;
}

export function step(c: Core, now: number, autoPlay: boolean) {
  'worklet';
  const dt = c.last < 0 ? 0 : Math.min(Math.max((now - c.last) / 1000, 0), 0.05);
  c.last = now;

  if (c.mode === MODE.auto) {
    if (autoPlay && !c.still) {
      if (c.start < 0) c.start = now;
      c.out = loopFrame(((now - c.start) / 1000) % LOOP);
      return;
    }
    takeOver(c);
  }

  c.bestAge += dt;
  c.nudge *= Math.exp(-NUDGE_DECAY * dt);
  if (c.phase === PHASE.run) {
    c.tau += dt;
    if (!c.landed && c.tau >= c.D + TIP_TIME) {
      c.landed = true;
      c.lands += 1;
      if (c.D > c.best) {
        c.best = Math.round(c.D * 10) / 10;
        c.bestAge = 0;
      }
    }
  } else if (c.phase === PHASE.reset) {
    c.resetT += dt;
    if (c.resetT >= FADE_OUT + GAP + FADE_IN) {
      c.phase = PHASE.ready;
      c.tau = -1;
      if (c.flickAfter > 0) {
        const w0 = c.flickAfter;
        c.flickAfter = 0;
        flick(c, w0);
      } else if (autoPlay && !c.still) {
        // Back to the loop at t = 0: the same ready frame.
        c.mode = MODE.auto;
        c.start = now;
        c.out = loopFrame(0);
        return;
      }
    }
  }
  c.out = liveFrame(c);

  // The idle clock runs once the top is down and still (or standing ready), with no finger on it.
  const settled = c.phase === PHASE.ready || (c.phase === PHASE.run && c.tau >= c.D + COOL_TO);
  if (c.touches > 0 || !settled) {
    c.idle = 0;
    return;
  }
  c.idle += dt;
  if (!autoPlay || c.idle < IDLE) return;
  if (c.phase === PHASE.run) {
    c.phase = PHASE.reset;
    c.resetT = 0;
  } else if (!c.still) {
    c.mode = MODE.auto;
    c.start = now;
    c.out = loopFrame(0);
  }
}

export function setStill(c: Core, still: boolean) {
  'worklet';
  if (still === c.still) return;
  c.still = still;
  if (still) takeOver(c);
  else if (c.mode === MODE.live) c.idle = IDLE;
}
