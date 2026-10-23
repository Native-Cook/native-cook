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
  /** Divides LOOP_MS so the breath falls at the same place in every loop. */
  breathMs: 4_000,
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
  confetti: 70,
  confettiLife: 2.6,
  gravity: 520,
  drag: 0.985,
  buttonOpenMs: 520,
  /** Light sweep across the card art on arrival, and the price counting up. */
  sweepMs: 900,
  countMs: 760,
} as const;

export const RESET = { fadeMs: 420 } as const;

/** Headline exit: letters slide down one after another over the first part of the drag (progress units). */
export const HEADLINE = { stagger: 0.006, span: 0.05, drop: SPACE.xl } as const;

/** Ambient life while sealed. */
export const AMBIENT = { floatPt: SPACE.xs, floatMs: 3_200, glowMs: 9_000, glowAmount: 0.12 } as const;
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
export const LOOP_MS = 8_000;
export const RESUME_AFTER_MS = 3_500;
export const SCRIPT = {
  /** A slow, readable pull: 1.6 s for the flap and the card, then about 1 s of reveal. */
  down: 700,
  up: 2_300,
  rise: 300,
  /** About 1.7 s of finished screen after the texts have risen, then the cross-fade back. */
  reset: 6_000,
} as const;

/** QA only: when set, the script stops at this time (ms) so a state can be photographed. Keep null. */
export const QA_HOLD_AT: number | null = null;
