import { Easing } from 'react-native-reanimated';

import { SPACE } from './theme';

/** Every number the motion uses. */
export const OPEN = {
  /** Upward drag (pt) for a full open: the first half folds the flap, the second lifts the card. */
  travel: 320,
  /** Progress past which a release completes the reveal. */
  commit: 0.42,
  flapShare: 0.33,
  /** Enough to lift the card's bottom edge clear of the envelope: the second part of the drag is 1:1. */
  cardRise: 222,
  tiltMax: 10,
  breathDeg: 7,
  /** One breath per loop: the flap lifts a few degrees at 9.0–9.7 s, after the reset, to say it opens. */
  breathMs: 10_000,
  breathLead: 1_000,
  /** The wax seal cracks over the first part of the flap's lift. */
  sealSplitDeg: 40,
  /** Envelope settles down this far as the flap opens; the pocket's V opens to this fraction of the height. */
  settlePt: 23,
  pocketApex: 0.38,
  /** Paper curl: the flap's lower part lags the fold by up to this much. */
  curlDeg: 28,
} as const;

export const SPRINGS = {
  close: { stiffness: 260, damping: 18, mass: 1 },
  finish: { stiffness: 140, damping: 18, mass: 1 },
  press: { stiffness: 500, damping: 28, mass: 0.6 },
} as const;

/** After the reveal commits, in ms from the release. */
export const REVEAL = {
  finishMs: 420,
  flyMs: 720,
  /** Animation review (20 Oct): the table's on-screen move curve (built-in cubic is weaker). */
  flyEase: Easing.bezierFn(0.77, 0, 0.175, 1),
  envelopeDropMs: 520,
  pageMs: 600,
  textDelay: 520,
  stagger: 80,
  confetti: 0, // a letter, not a party: no confetti
  confettiLife: 2.6,
  gravity: 520,
  drag: 0.985,
  buttonOpenMs: 520,
  /** Light sweep across the card art on arrival, and the price counting up. */
  sweepMs: 900,
  countMs: 760,
  /** Once the card has landed it turns over to the note: a heavy card, so it overshoots a few degrees and settles. */
  flipDelay: 160,
  flipMs: 900,
  flipBack: 0.9, // ease-out-back's overshoot constant: about 4 % (7°)
  flipLift: 0.05, // it lifts toward you by this share of its size mid-turn
} as const;

/**
 * Back to sealed, sequenced (owner, 21 Oct: the two headlines cross-faded on top of each other). Within the same
 * 420 ms, the revealed screen leaves first (title, message, CTA and card out by `outShare`), then the sealed one
 * arrives (envelope and headline from `inFrom`); the headline's letters rise back in, one after another, the way
 * they left. Shares of the window; ms otherwise. The last letter lands at 180 + 14·5 + 160 = 410 ms.
 */
export const RESET = { fadeMs: 420, outShare: 0.4, inFrom: 0.43, textOutMs: 160, letterMs: 160, letterStagger: 5 } as const;

/** Headline exit: letters slide down one after another over the first part of the drag (progress units). */
export const HEADLINE = { stagger: 0.006, span: 0.05, drop: SPACE.xl } as const;

/** Ambient life while sealed. */
export const AMBIENT = { floatPt: 2, floatMs: 5_000, glowMs: 10_000, glowAmount: 0 } as const;
export const REDUCED_MS = 200;

/** Animation review (20 Oct): press feedback and the final texts' entrance. */
export const POLISH = {
  easeOut: [0.23, 1, 0.32, 1] as const,
  pressScale: 0.96,
  pressInMs: 100,
  pressOutMs: 160,
  /** The final title, message and CTA rise this far as they fade in (was 14). */
  textRise: SPACE.lg,
};

/** Autoplay: one scripted finger on one clock (ms). */
export const LOOP_MS = 10_000;
export const RESUME_AFTER_MS = 2_000;
export const SCRIPT = {
  /** A slow, readable pull: 1.5 s for the seal, the flap and the card; the card lands by about 3.2 s and turns over. */
  down: 600,
  up: 2_100,
  rise: 300,
  /** About 4 s of the note to read, then back to sealed by 8.6 s and rest. */
  reset: 8_200,
} as const;

/** QA only: when set, the script stops at this time (ms) so a state can be photographed. Keep null. */
export const QA_HOLD_AT: number | null = null;
