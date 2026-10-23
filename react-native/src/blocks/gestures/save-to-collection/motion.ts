/** Every motion value of the block. Times in ms unless named in seconds. */
import { Easing } from 'react-native-reanimated';

// animate-expo: never ease-in on UI; strong ease-out for entrances, in-out for on-screen movement.
export const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
export const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
/** The glyph's climb, fitted to the source's f021–f028 (y per frame): a soft start, a long settle. */
export const RISE_EASE = Easing.bezierFn(0.2, 0, 0.5, 1);
export const SAVE = {
  holdMs: 300, // long press before a card lifts
  liftScale: 1.06,
  magnetRadius: 150, // within this distance of the bookmark the card is pulled in
  magnetPull: 0.45, // how far toward the bookmark the card is drawn at the edge of the glyph
  magnetShrink: 0.45, // scale lost at full pull
  arc: 0.08, // control point offset of the flight curve: the source's rush falls almost straight down its column
  endScale: 0.06,
  tiltPerVelocity: 0.012, // degrees per pt/s
  tiltMax: 14,
  flickSpeed: 900, // pt/s that counts as a throw
  flickAim: 0.8, // cosine between the throw and the direction to the bookmark
  floatAmp: 2.5, // ambient: pt the pile drifts
  floatPeriod: 3.25, // s; four per 13 s loop
  trail: 8, // ghost copies behind a flying card: the source's motion streaks
  trailStep: 0.022, // progress between ghosts
  streak: 1.4, // how far a card stretches along its path at full speed
  streakBlur: 16, // blur of the trailing copies
} as const;

/** The rush (f018–f025): a stream of streaked cards pours down the central column into the bookmark. */
export const STREAM = {
  count: 46,
  total: 2100, // ms, from the tap to the last streak swallowed (the source: f018 → f025 at 4 fps)
  pour: 0.75, // fraction of `total` over which the drops leave the top
  fall: 0.3, // fraction of `total` one drop takes to fall
  spread: 125, // pt either side of the column at the top (the source's stream fills the screen's width before it narrows)
  stretch: 0.6, // extra length along the fall at full speed
} as const;

export const SPRING = {
  lift: { damping: 18, stiffness: 320, mass: 0.7 },
  back: { damping: 20, stiffness: 220, mass: 1 }, // a card dropped outside returns to its place
  bounce: { damping: 8, stiffness: 420, mass: 0.6 }, // bookmark gulp
  check: { damping: 16, stiffness: 380, mass: 0.6 },
  tilt: { damping: 16, stiffness: 220, mass: 0.6 },
} as const;

export const TIMING = {
  pressIn: 100, // press feedback (review-animations: 100–160 ms, asymmetric)
  pressOut: 160,
  flight: 560, // one card into the bookmark
  dropFlight: 300, // from the magnet into the bookmark
  flockStagger: 70,
  badgeRoll: 160,
  dim: 140,
  reduced: 200,
  cornerIn: 220,
  deal: 380,
  dealStagger: 110,
  dealDrop: 18,
  nextPile: 600, // after the last card of a pile is saved, the next pile deals in
  landRing: 560,
  countIn: 260,
  flickFlight: 360,
  rise: 1700, // the bookmark climbs (measured on f021–f028: 761 → 390 pt in 1.7 s)
  riseDown: 850, // and comes back down as the next pile deals
  riseHold: 500,
};

/** Autoplay, seconds. */
/** QA only: hides the payoff pill for the pixel gate's state b. Ship false. */
export const REPLICA_ONLY = false;
/** QA only: freezes the demo's script clock at this second (pixel gate). Ship null. */
export const QA_HOLD_AT: number | null = null;

export const LOOP_MS = 13_000;
export const RESUME_MS = 3_500;
export const DEMO = {
  fingerFade: 0.15,
  drag: { card: 'lemon', down: 1.6, lifted: 1.9, end: 3.0, up: 3.25 }, // carry one card in
  saveAll: 4.2, // tap the bookmark: the rest of the pile flies in, the next pile deals
  rise: 5.05, // the script raises the glyph and deals the next pile at fixed times (no JS-timer drift)
  riseAgain: 9.45,
  saveAllAgain: 8.6, // gives the second pile time on screen and leaves 1 s after its deal before the 13 s wrap
} as const;
