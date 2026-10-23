// Every motion value of the block. Times in seconds unless named _MS.

// The simulation runs at a fixed step so the autoplay is identical on every device and every loop.
export const STEP = 1 / 120;

// Keys: a spring-loaded cap that bottoms out on the chassis (restitution) and clicks there.
export const KEY = {
  pressStiffness: 1500, // finger force: ~70 ms to the bottom
  pressDamping: 34,
  returnStiffness: 520, // the cap's spring pushing it back up
  returnDamping: 17, // under-damped: a small rebound at the top
  bottomRestitution: 0.18,
  topRestitution: 0.32,
  latchDepth: 0.86, // a latched play key rests just above the bottom, caught by the latch
  rearm: 0.45, // depth below which a key can click again
};

// Transport: the capstan motor spins the tape up with inertia; a brake and friction stop it in finite time.
export const TRANSPORT = {
  spinUpTau: 0.32,
  brake: 0.55, // constant deceleration (speed units per second)
  friction: 1.0, // viscous term (per second)
  wind: 6, // fast-forward / rewind speed in play-speed units
  engageTau: 0.15, // a loaded cassette's hubs take up the capstan speed
  baseOmega: 2.6, // rad/s of a reel whose tape pack is refRadius at play speed
  refRadius: 25,
  progressRate: 1 / 900, // tape fraction per second at play speed (the pack radius moves visibly only when winding)
};

// Cassettes sit one screen width apart and are swapped like pages with momentum.
export const PAGER = {
  stiffness: 180,
  damping: 24,
  momentum: 0.18, // seconds of release velocity projected forward
  loadedWithin: 0.08, // pages from centre at which the deck engages a cassette
  rotateMax: 7, // degrees of yaw at full swipe speed (the cassette's real thickness shows)
  rotatePerSpeed: 3.2, // degrees per page/s
};

// Upgrade: the deck answers each key with a dip of the chassis and a rattle of the cassette in its tray.
export const CHASSIS = {
  dipStiffness: 900,
  dipDamping: 30,
  dipImpulse: 0.012, // pt of dip per pt/s of the key's impact speed
  dipTravel: 1, // pt per unit
  rattleStiffness: 420,
  rattleDamping: 9,
  rattleImpulse: 0.02,
  rattleTravel: 1,
  // Animation review (20 Oct): a cassette arriving in the tray kicks the rattle spring, pt/s at 1 page/s (≈ 2 pt clack).
  seatKick: 40,
};

// Upgrade: grab a loaded reel and turn it; the tape follows your finger and coasts on release.
export const SCRUB = {
  grabRadius: 34, // pt around a reel centre
  maxSpeed: 14, // play-speed units
  // Animation review (20 Oct): at most one tooth tick per 40 ms (25 Hz) however fast the reel turns.
  tickMinGap: 0.04,
};

// Upgrade: reels blur when winding; the shell catches a sheen as it slides.
export const MATERIAL = {
  blurFrom: 4, // rad/s at which the ghost hubs start to show
  blurSpan: 8,
  shutter: 1 / 60, // s of turn each ghost trails by (spread over the ghosts)
  ghosts: 1,
  sheenTravel: 0.6, // of a cassette width per cassette width moved
  sheenGain: 3,
  edge: 4.5, // pt: the shell's thickness seen when it yaws
  ledFlicker: 0.16, // a seeded level flicker while playing
  keyGlow: 0.55, // opacity of the red spill under a latched play key
};

export const LIGHT = {
  ledTau: 0.06,
  glyphOnTau: 0.04,
  glyphOffTau: 0.22,
};

// Autoplay: a scripted finger on one clock. The loop starts and ends stopped on the first cassette.
export const LOOP_MS = 9000;
export const RESUME_MS = 3500;
export const SCRIPT = {
  play: [0.7, 0.86],
  next: [3.3, 3.62],
  // A flick to the right on the cassette brings the first one back: p goes 1 → 1 − dragDist over dragTime, accelerating.
  drag: { start: 5.4, time: 0.32, dist: 0.3, power: 1.6 },
  ff: [4.3, 4.9],
  pause: [7.0, 7.16],
};

// Reduce Motion: no travel; changes cross-fade.
export const REDUCED = { fade: 0.18 };

export const HAPTIC = { click: 1, latch: 2, release: 3, commit: 4, tick: 5 } as const;

// QA holds for the replica gate (null in production).
export const QA_HOLD: null | 'rest' | 'a' | 'b' = null;
export const QA_B_PAGE = 0.599;
// QA: freeze the autoplay at this script time (seconds) for proof frames (null in production).
export const QA_T: null | number = null;
