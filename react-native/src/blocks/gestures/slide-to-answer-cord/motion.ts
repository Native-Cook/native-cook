// Every motion value of the block. Seconds unless the name ends in _MS; points unless noted.

export const STEP = 1 / 120;
export const MAX_STEPS = 30; // per frame: the sim keeps wall-clock time through dropped frames, so the loop stays 8 s

export const CHAIN = {
  gravity: 900, // gives the source's 4 s swing on a 340 pt cord
  hangStretch: 150, // the handset's weight stretches the coil this far beyond its natural length
  linkMass: 0.05,
  handsetMass: 1,
  damping: 0.35, // per second, on every particle
  handsetDamping: 0.6,
  idleDrive: 75, // sideways force while ringing, at the swing period: the source's ±86 pt (±126 source px)
  drivePeriod: 4,
  straighten: 160, // pulls each link toward its neighbours' midpoint: a coil has some stiffness and never kinks
  lookEase: 0.2, // s: what is drawn follows that hand-over with this time constant (both ways)
  toSource: 0.3, // s: after a call the handset eases back onto the source's swing with this time constant
};

export const TILT = {
  stiffness: 42, // the handset turning to follow the cord's last link
  damping: 5.5,
};

export const DRAG = {
  answer: 64, // lift to answer
  decline: 72, // pull to decline or hang up
  reach: 110, // rubber band beyond this
  releaseSpeed: 1600,
};

export const STATE = {
  pickUp: 72, // the cord shortens this much when answered: the handset rises and stays up
  drop: 24, // and lengthens this much when declined
  lengthEase: 7, // per second
  colorEase: 14, // per second: short, so the hue change does not linger on its brownish middle
  resetAfter: 1.4, // declined / ended back to ringing
};

export const RING = {
  every: 2, // a ring every 2 s while ringing
  offset: 1.2, // s: the rings fall at 1.2, 3.2, … s of the clock
  buzz: [0, 0.2] as const, // two buzzes per ring
  buzzLength: 0.09,
  buzzAngle: 0.05, // radians
  buzzHz: 38,
  pulse: 1.3, // a neon ring leaves the handset
  pulseFrom: 44,
  pulseTo: 170,
};

export const HAPTIC = { grab: 1, answer: 2, decline: 3, tick: 4 } as const;

// Autoplay: two swing periods. The script lifts to answer, holds the call, then pulls down to hang up; the receiver is
// back on its swing, ringing, before the loop ends.
export const LOOP_MS = 8_000;
export const RESUME_MS = 2_000;
/** Where a resumed demo picks up when a call is still connected (s into the loop). */
export const RESUME_IN_CALL = 3.9;
export const PREROLL_LOOPS = 3; // the swing reaches its steady loop before the first frame
export const SCRIPT = [
  // [start, end, from dx, from dy, to dx, to dy, down at start, up at end] from the handset's grab point
  [1.6, 2.0, 0, 0, 0, -10, 1, 0],
  [2.0, 2.55, 0, -10, 6, -84, 0, 0],
  [2.55, 2.7, 6, -84, 6, -84, 0, 1],
  [4.4, 4.75, 0, 0, 0, 14, 1, 0],
  [4.75, 5.25, 0, 14, -4, 92, 0, 0],
  [5.25, 5.4, -4, 92, -4, 92, 0, 1],
] as const;
// Replica: the source's swing, measured on its frames (4 s period) as Fourier series in screen px / degrees:
// [mean, cos 1, sin 1, cos 2, sin 2, cos 3, sin 3, cos 4, sin 4] of the handset's glow centroid and its lean.
// The handset follows it exactly (the cord stays a simulated chain); `fit` maps those measures onto the handset.
export const REPLICA_SWING = {
  period: 4,
  x: [578.19, -256.06, 44.02, -0.52, 0.09, 5.88, -3.23, 0.0, -0.12],
  y: [1262.69, 8.17, -1.38, -19.1, 6.54, -0.36, -0.25, 1.13, -0.5],
  lean: [9.82, -11.66, 1.96, 0.1, -0.05, 0.22, -0.16, 0.03, -0.01],
  fit: { centroid: [16.5, 251.3], leanPerRad: -0.9426, leanAt0: -2.078 }, // px from the cord's end, in the handset's frame
  attach: [3, -22], // pt: where the source's cord meets the handset, in the traced handset's frame
};
// Replica: the source’s cord, measured on its 16 frames of one swing (0.25 s apart): the centre line’s x (px) at these
// heights (px), down to 780 px (below, the handset’s glow spoils the measure). The replica’s cord follows it; its wave is drawn along it.
export const REPLICA_CORD = {
  y: [300, 360, 420, 480, 540, 600, 660, 720, 780],
  x: [
    [449.9, 442.3, 430.5, 415.6, 399.0, 382.0, 365.8, 351.7, 341.0],
    [464.8, 453.7, 441.3, 427.9, 414.0, 400.1, 386.6, 373.9, 362.4],
    [488.3, 473.8, 461.4, 450.6, 440.9, 431.7, 422.6, 412.8, 402.0],
    [514.2, 504.5, 496.5, 489.9, 484.3, 479.5, 475.0, 470.6, 465.7],
    [537.1, 532.4, 529.3, 527.5, 526.6, 526.4, 526.7, 527.1, 527.4],
    [554.3, 561.4, 565.8, 568.5, 570.6, 573.0, 576.8, 582.9, 592.4],
    [561.5, 579.9, 590.5, 596.0, 598.8, 601.6, 607.1, 617.8, 636.4],
    [566.2, 590.6, 604.7, 611.8, 615.5, 619.0, 625.9, 639.5, 663.3],
    [566.1, 591.2, 605.6, 612.8, 616.4, 619.9, 626.8, 640.6, 664.7],
    [562.4, 581.9, 593.2, 598.9, 601.8, 604.7, 610.3, 621.5, 641.0],
    [555.4, 564.5, 570.0, 573.1, 575.3, 577.7, 581.8, 588.6, 599.6],
    [540.1, 536.2, 533.8, 532.7, 532.5, 533.1, 534.0, 535.1, 536.0],
    [517.0, 508.2, 501.0, 495.1, 490.3, 486.1, 482.3, 478.5, 474.5],
    [491.5, 477.2, 465.2, 454.9, 445.7, 437.1, 428.5, 419.4, 409.1],
    [467.4, 456.2, 443.8, 430.6, 417.1, 403.5, 390.4, 378.1, 367.2],
    [450.9, 443.0, 431.1, 416.4, 400.0, 383.2, 367.2, 353.2, 342.3],
  ],
};
// QA only: after the pre-roll, runs the swing to this time (seconds) and freezes it for reference screenshots; -1 off.
export const REPLICA_HOLD = -1;
// QA only: slows the simulation for slow-motion proof captures (1 = real time).
export const QA_SPEED = 1;

// Upgrade.
export const FLICK = { speed: 900, min: 24 }; // a flick faster than this (pt/s) past min pt commits (velocity OR distance)
export const TICK = 22; // pt of drag per selection tick while the coil stretches
export const WASH = { duration: 0.7 }; // the new call state's colour floods out from the handset
export const MORPH = { duration: 0.5 }; // Accept disc → in-call glass control pill
export const PRESS = { scale: 0.95, in: 120, out: 160 }; // ms: press feedback on the call discs and the pill's controls
export const CHEVRON = { period: 1.5, rise: 18 }; // 'lift' chevrons drifting up above the handset while ringing
