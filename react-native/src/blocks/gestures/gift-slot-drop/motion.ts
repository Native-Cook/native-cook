import { Easing } from 'react-native-reanimated';

/** Every number the motion uses. */
export const DRAG = {
  /** Movement that decides between a swipe (carousel) and a pull (drop). */
  decide: 8,
  /** Pull at which the slot will take the card. */
  commit: 170,
  /** Pull at which the card has shrunk to `minScale` and leans back by `leanDeg` (it tips into the slot, as in the source). */
  full: 250,
  /** The card keeps its width and tips back, so it reads about half height by the time it reaches the pocket (frame 056). */
  minScale: 0.92,
  leanDeg: 64,
  lift: 1.03,
  upResist: 0.2,
  swipeVelocity: 600,
} as const;

export const SPRINGS = {
  snap: { stiffness: 260, damping: 28, mass: 1 },
  back: { stiffness: 320, damping: 26, mass: 1 },
  centre: { stiffness: 420, damping: 30, mass: 0.8 },
  rise: { stiffness: 170, damping: 20, mass: 1 },
  lift: { stiffness: 500, damping: 30, mass: 0.6 },
} as const;

export const INSERT = {
  centreMs: 160,
  sinkMs: 300,
  sinkEase: Easing.bezier(0.77, 0, 0.175, 1),
  flashMs: 420,
} as const;

export const ACTIVATE = { ms: 1_150, dotMs: 280 } as const;

export const PRINT = {
  /** Three feeds, each pushing out this share of the card's height. */
  feeds: 3,
  feedShare: 0.22,
  feedMs: 260,
  feedGap: 90,
  feedEase: Easing.out(Easing.back(1.6)),
  /** The offer comes out narrower than the slot (300 pt × 0.48 = 144 pt), then grows as it rises. */
  startScale: 0.44,
  rollMs: 900,
  detailsDelay: 280,
  stagger: 60,
  confetti: 72,
  confettiLife: 1.6,
  gravity: 820,
} as const;

/** Upgrade: ambient life and material on the carousel. */
export const AMBIENT = { floatPt: 3, floatMs: 3_000, rollPerPt: 0.08, rollMax: 7, band: 0.4, bandMs: 6_000, slotBreathMs: 2_400 } as const;

/** The chevrons under the card: a slow bob, and the gap they keep ahead of a pulled card's top edge. */
export const CHEVRON = { bobPt: 4, bobMs: 1_400, gap: 18 } as const;

/** Closing the offer is an exit: strong ease-out under 300 ms; the cards' return is a slower, rarer beat. */
export const CLOSE = { ms: 260, ease: Easing.bezier(0.23, 1, 0.32, 1), returnMs: 420 } as const;

/** Press feedback on the offer's buttons. */
export const BUTTON_PRESS = { scale: 0.97, inMs: 100, outMs: 160 } as const;
export const REDUCED_MS = 200;

/** Autoplay: one scripted finger on one clock (ms). From the burger card, one swipe right to the parcels card (as the source),
 * pull it into the slot, read the offer, Done, then one swipe back. */
export const LOOP_MS = 15_000;
export const RESUME_AFTER_MS = 3_500;
export const SCRIPT = {
  swipes: [
    { at: 1_000, ms: 450, dx: 200 },
    { at: 12_700, ms: 450, dx: -200 },
  ],
  pullDown: 2_400,
  pullUp: 3_450,
  pullBy: 252,
  done: 11_000,
} as const;

/** QA only: when set, the script stops at this time (ms) so a state can be photographed. Keep null. */
export const QA_HOLD_AT: number | null = null;
