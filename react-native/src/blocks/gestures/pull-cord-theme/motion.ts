// Every motion value of the block. Times in seconds unless the name ends in _MS; distances in points.

export const STEP = 1 / 120; // fixed simulation step, so a finger and the autoplay give the same result
export const MAX_STEPS = 30; // per frame: the sim keeps wall-clock time through dropped frames

export const EASE_OUT = [0.23, 1, 0.32, 1] as const;
export const EASE_IN_OUT = [0.77, 0, 0.175, 1] as const;

// The ball chain: position-based (verlet) on the UI thread, one particle per ball.
export const ROPE = {
  gravity: 4000, // pt/s²: about a 1 s swing for this length, as a real chain of the drawn size would have
  damping: 0.995, // velocity kept per step (air)
  passes: 14,
  pullWeight: 0.3, // inverse mass: the teardrop weighs about three balls
  bend: 1.7, // two links apart stay at least this many pitches apart: a ball chain bends ~50° per joint at most
  slack: 0.86, // a link may shorten to this (the balls slide on the wire), never lengthen
  lift: 0.6, // the pull can be lifted this share of the chain's length (the chain goes slack)
  releaseSpeed: 1500, // finger velocity handed to the pull on release, clamped
  snapRest: 1.5, // at the loop seam the chain is snapped to rest when within this
  settleSteps: 900,
};

// The pull switch: a short stiff travel, a detent, a recoil.
export const SWITCH = {
  travel: 9, // hard stop of the chain's travel out of the housing
  detent: 7, // click: the lamp toggles
  give: 2.5, // after the click the cam lets the chain back this much (the recoil)
  rearm: 2, // the chain must come back within this before it can click again
  stretch: 3, // past the hard stop the chain itself gives at most this much
  spring: { duration: 0.4, dampingRatio: 0.8 }, // draws the chain back in on release: a snap back after a drag
  kick: 1.6, // pt of sideways shiver sent up the chain by the click
  flickSpeed: 900, // a fast downward release past flickMin also clicks
  flickMin: 3,
};

// The pendant: a damped pendulum, at rest unless something moves it.
export const SWING = {
  period: 2.4,
  dampingRatio: 0.3,
  couple: 0.016, // rad of lean per unit of the taut chain's sideways slope
  kick: 0.02, // rad/s from the switch's snap
  maxThrow: 0.05, // rad/s handed to the shade by a flick of the shade itself
  maxAngle: 0.06, // rad the shade can be held off its rest
  grabRadius: 80, // a touch this close to the bulb holds the shade
};

// Tungsten: heat follows the switch with a time constant; brightness grows with heat to the fourth.
export const FILAMENT = {
  warmUp: 0.06, // about 150 ms to warm white
  coolDown: 0.12, // the filament's afterglow: orange for about 300 ms
  reduced: 0.04,
  power: 4,
  flicker: 0.3, // s after switching on: a small irregular flicker while the filament settles
  flickerDepth: 0.07,
};

// The theme front: a level through the page's illuminance, Night 0 to Day 1.
export const FRONT = {
  onDelay: 0.08, // the paper develops just after the filament glows
  on: 1.2, // EASE_OUT
  off: 0.45, // EASE_IN_OUT: darkness closes in toward the lamp, about as fast as the filament cools
  reduced: 0.2, // EASE_OUT: a cross-fade of the whole page
  band: 0.24, // softness of the front, in the field's units (0 brightest, 1 darkest)
};

export const HAPTIC = { click: 1 } as const;

// Autoplay: one 12 s loop from rest (Day). Finger offsets from the pull's centre at rest.
export const LOOP_MS = 12_000;
export const RESUME_MS = 3_500;
export const NIGHT_REST_AT = 5; // where the script resumes when the page is Night
export const SCRIPT = [
  // [start, end, from dx, from dy, to dx, to dy, down at start, up at end]
  // Off: take the pull, draw it to the left (the chain swings with it), tug down through the click, let go.
  [1.0, 1.55, 0, 0, -16, 2, 1, 0],
  [1.55, 1.95, -16, 2, -11, 14, 0, 0],
  [1.95, 2.1, -11, 14, -11, 14, 0, 1],
  // On: from the right this time.
  [7.0, 7.5, 0, 0, 14, 3, 1, 0],
  [7.5, 7.85, 14, 3, 10, 14, 0, 0],
  [7.85, 8.0, 10, 14, 10, 14, 0, 1],
] as const;

// A tap on the pull, or the accessibility action, plays one straight tug through the same kernel.
export const TAP_PULL = [
  [0, 0.22, 0, 0, 0, 14, 1, 0],
  [0.22, 0.32, 0, 14, 0, 14, 0, 1],
] as const;
export const TAP = { slop: 8, time: 0.25 };
