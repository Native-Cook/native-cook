// Every motion value of the block. Times in seconds unless the name ends in _MS; distances in points.

export const STEP = 1 / 120; // fixed simulation step, so the finger and the autoplay give the same result
export const MAX_STEPS = 30; // per frame: the sim keeps wall-clock time through dropped frames, so the loop stays 12 s

export const ROPE = {
  gravity: 2600,
  damping: 0.988, // velocity kept per step
  passes: 10,
  beadWeight: 0.25, // inverse mass: the bead is four times a rope point
  sideReach: 72, // bead x from the anchor before rubber-banding
  liftReach: 90, // bead may be lifted this far above rest (rope goes slack)
  releaseSpeed: 1800, // finger velocity handed to the bead, clamped
  snapRest: 1.5, // at the loop seam the rope is snapped to rest when within this
  settleSteps: 600,
};

export const SWITCH = {
  linear: 36, // free travel before the rubber band
  travel: 66, // rubber-band limit of the pull
  detent: 44, // click: the theme toggles
  rearm: 20, // pull must come back above this before it can toggle again
  stiffness: 300,
  damping: 26,
  reducedDamping: 46, // critically damped under Reduce Motion
};

export const BULB_MOTION = {
  period: 6, // the source's swing
  idleAmp: 14, // points at the glass centre
  reducedIdleAmp: 0,
  damping: 0.3, // ratio: a tugged bulb settles within the loop
  maxThrow: 0.02, // rad/s handed to the swing on release (≈ 20 pt more swing)
  maxAngle: 0.025, // about 55 pt at the glass, the source swing's own reach
};

export const REVEAL = {
  toLight: 0.72, // circle grows from the bulb, out-quint
  toDark: 0.62, // circle shrinks into the bulb, in-out cubic
  reducedFade: 0.2,
  margin: 40, // past the farthest corner
  textBand: 70, // text recolours while the front crosses this band
};

export const LEVEL = {
  follow: 16, // per second, the liquid's lag behind the pull
  fill: 10, // per second after the click
};

// Neon ignition when the dark theme returns, from the source's first second (dim, flicker, full).
export const IGNITE = {
  // Neon re-ignition: three buzzing stutters, then on.
  times: [0, 0.05, 0.11, 0.19, 0.26, 0.36, 0.45, 0.6],
  values: [0.1, 0.8, 0.15, 0.9, 0.3, 1, 0.6, 1],
};

export const HAPTIC = { grab: 1, detent: 2, home: 3, tick: 4 } as const;

// Upgrade: the gesture's feel and the hero beat.
export const FEEL = {
  tick: 12, // a selection haptic every 12 pt of pull
  flickSpeed: 900, // a downward release faster than this (pt/s) past flickMin also commits
  flickMin: 24,
  kick: 0.005, // rad/s: the switch's snap tugs the bulb into a ~10 pt swing
  preview: 0.2, // share of the reveal radius the light bleeds in while pulling (scrub)
};
export const HERO = {
  ember: 0.9, // s: tungsten warms from ember (~1800 K) to warm white
  flickers: [0.12, 0.22, 0.34, 0.42] as const, // two quick dips: [start, end] pairs, s after commit
  flickerDepth: 0.55,
  flash: 0.28, // s: soft white flash on commit to light
  flashPeak: 0.32,
  edge: 34, // pt: the warm soft edge of the reveal
  stutter: [0.05, 0.11, 0.19, 0.26, 0.36, 0.45] as const, // neon re-ignition: 3 buzzing stutters
  hum: { period: 3, low: 0.92 }, // neon hum
  spec: 26, // pt: the glass highlight slides this far across a full swing
  shadow: { dx: 26, dy: 34, alpha: 0.2, blur: 10 }, // the bulb's cast shadow on the light wall
  tilt: 0.05, // rad of extra swing at full phone tilt
  motes: 26,
  roll: 0.42, // s: Dark ↔ Light label roll
};

// Autoplay: one 12 s loop (two swing periods). Finger offsets from the bead's rest position.
export const LOOP_MS = 12_000;
export const RESUME_MS = 3_500;
export const LIGHT_REST_AT = 5; // where the script resumes when the theme is light
export const SCRIPT = [
  // [start, end, from dx, from dy, to dx, to dy, down at start, up at end]
  [1.2, 2.0, 0, 0, -6, 30, 1, 0],
  [2.0, 2.35, -6, 30, -4, 18, 0, 0],
  [2.35, 2.8, -4, 18, 4, 64, 0, 0],
  [2.8, 2.95, 4, 64, 4, 64, 0, 1],
  [7.2, 7.75, 0, 0, 8, 60, 1, 0],
  [7.75, 7.9, 8, 60, 8, 60, 0, 1],
] as const;

// A tap on the bead plays one full pull through the same kernel.
export const TAP_PULL = [
  [0, 0.3, 0, 0, 2, 60, 1, 0],
  [0.3, 0.4, 2, 60, 2, 60, 0, 1],
] as const;
export const TAP = { slop: 8, time: 0.25 };

// Replica: the source video's own loop — the bulb lights in its first 0.75 s (blue pixels 17 %, 47 %, 47 %, 100 %
// at 0, 0.25, 0.5, 0.75 s), then swings with a 6 s period from the left of centre.
export const REPLICA_LOOP_MS = 6000;
export const REPLICA_IGNITE = { times: [0, 0.25, 0.5, 0.75], values: [0.08, 0.5, 0.5, 1] }; // the refs' f001 is nearly dark
export const REPLICA_SWING_PHASE = -Math.PI / 2; // the source starts at its left extreme (f001)
export const REPLICA_PERIOD = 5.8; // s: fitted to the refs (left at 0, right at 2.75, −0.88 at 5.25)
// QA only: pins the replica's clock to this time (seconds) for reference screenshots; -1 runs normally.
export const REPLICA_HOLD = -1;
// QA only: slows the whole simulation for slow-motion proof captures (1 = real time).
export const QA_SPEED = 1;
