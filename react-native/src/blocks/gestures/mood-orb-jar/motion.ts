// Every motion value of the block. Seconds unless the name ends in _MS; points unless noted.

export const STEP = 1 / 120;
export const MAX_STEPS = 30; // per frame: the sim keeps wall-clock time through dropped frames, so the loop stays 12 s

export const PILE = {
  count: 16,
  seed: 11,
  settleSteps: 900,
  home: 60, // spring back to the slot
  damping: 4, // per second
  passes: 3,
  restSpeed: 18, // a landed orb slower than this...
  restTime: 0.35, // ...for this long becomes part of the pile
};

export const THROW = {
  gravity: 2200,
  apex: 70, // the solved arc peaks this far above the mouth
  solved: 0.75, // share of the solved launch in the final velocity
  minFlick: 380, // upward speed that counts as a flick
  lift: 1.15, // held orb scale
  spin: 6, // radians per second of spin in flight
  regrow: 0.3, // the picker's orb grows back
  maxSpeed: 2400,
};

export const UNDO = { threshold: 96, rise: 0.35, fly: 0.55, rowSpring: { stiffness: 300, damping: 28 } };

// The lid lifts straight up and floats (as in the source), tilting ~3° on the way: 0 closed … 1 lifted.
export const LID = { open: 1, lift: 20, tilt: 0.09, stiffness: 120, damping: 9, hold: 2.4 }; // damping 9: overshoots, then floats

export const HAPTIC = { grab: 1, throw: 2, land: 3, undo: 4, landSoft: 5, landHard: 6 } as const;

// Upgrade.
export const SOFT = {
  stiffness: 420, // squash spring: overshoots into a stretch
  damping: 14,
  perSpeed: 1 / 2600, // squash per pt/s of sudden deceleration
  max: 0.28,
  landing: 0.15, // the first impact squashes at least to 0.85 / 1.15
  trigger: 140, // pt/s of deceleration in one step that counts as an impact
  hard: 900, // landing speed above this → heavy haptic, below soft → light
  soft: 350,
};
export const TILT = { slosh: 14, glass: 6 }; // pt of pile slosh and glass highlight drift at full phone tilt
export const GHOST = { duration: 0.85, lift: 0.3, rise: 46, arc: 60, grow: 0.36 }; // shared element: the landed orb lifts out, arcs to the list, the card grows from it
export const RIPPLE = { duration: 0.6, radius: 46 };
export const JAR_KNOCK = { amp: 1.5, decay: 14, hz: 9 }; // pt: a landing rocks the jar sideways and it settles in about 0.3 s
export const HOP = { every: 2, phase: 1, speed: 70 }; // ambient: one pile orb hops a little every 2 s of the 12 s loop, at 1, 3, … 11 s
export const AIM = { points: 14, step: 0.045 }; // dotted aim arc while holding an orb

export const REDUCED = { fade: 0.2 };

// Autoplay: 12 s.
export const LOOP_MS = 12_000;
export const RESUME_MS = 3_500;
export const SCRIPT = {
  mood: 4,
  pick: [1.0, 1.35] as const,
  pickBy: [-30, -80] as const,
  release: [-500, -1500] as const,
  swipe: [7.0, 7.55] as const,
  swipeBy: -150,
};
// QA only: after mount, runs the demo to this time (seconds) and freezes it for reference screenshots; -1 off.
export const REPLICA_HOLD = -1;
// QA only: slows the simulation for slow-motion proof captures (1 = real time).
export const QA_SPEED = 1;
export const TRAIL = { copies: 4, gap: 0.028, alpha: [0.32, 0.2, 0.12, 0.06] as const };
