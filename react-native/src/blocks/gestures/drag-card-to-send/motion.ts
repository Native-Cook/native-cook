import { Easing } from 'react-native-reanimated';

/** Every number the motion uses. Springs carry mass, timings carry light, colour and opacity. */
export const PULL = {
  /** Pull distance (pt) that maps to a full drop (g = 1). */
  full: 400,
  /** Pull distance that opens the sheet on its own. */
  commit: 380,
  /** Horizontal follow while pulling: the card is on a rail, a little play reads as physical. */
  sideFollow: 0.35,
  /** Pulling up from rest: rubber band. */
  upResist: 0.25,
  /** At a full pull the card has shrunk this much; it stays opaque (source frame a). */
  shrink: 0.01,
  fade: 0,
  /** The drop is dark glass while it grows; it turns solid as it becomes the sheet. */
  dropOpacity: 0.97,
  /** The frosted ghost trails this far above the pulled card, under "Drag to send". */
  ghostLead: 95,
  /** How much the pushed transactions shrink at the source frame a pull (263 pt). */
  listSink: 0.05,
  /** Opacity of the deep-blue wash on the card at the source frame a pull. */
  deep: 0.14,
  /** Peak opacity of the white arrow inside the growing drop. */
  arrow: 0.9,
} as const;

/** Upgrade: ambient light and the send burst. */
export const AMBIENT = { sheenMs: 6_500, sparks: 12, sparkReach: 46 } as const;

export const PRESS = { scale: 0.97, duration: 90, inMs: 100, outMs: 160 } as const;

export const SPRINGS = {
  /** g 1 → 2: the drop becomes the sheet, a slow heavy liquid. */
  open: { stiffness: 170, damping: 19, mass: 1 },
  /** Cancelled pull: the drop is sucked back with one wobble. */
  retract: { stiffness: 300, damping: 15, mass: 1 },
  /** The card going home or to the dock. */
  card: { stiffness: 380, damping: 30, mass: 1 },
  /** Card scale into the token, light and quick. */
  token: { stiffness: 420, damping: 28, mass: 0.8 },
  /** Hovered target swelling. */
  hover: { stiffness: 520, damping: 26, mass: 0.7 },
  /** The card re-entering after a send. */
  enter: { stiffness: 260, damping: 22, mass: 1 },
  /** Check badge. */
  check: { stiffness: 520, damping: 18, mass: 0.7 },
} as const;

export const MAGNET = {
  /** Distance (pt) at which a target starts to pull the token. */
  radius: 64,
  /** Distance that counts as "over" the target. */
  over: 48,
  /** Share of the way the token is pulled toward the target centre. */
  pull: 0.6,
  targetScale: 1.2,
} as const;

export const SEND = {
  absorbMs: 340,
  absorbScale: 0.08,
  // animate-expo: never ease-in on UI; on-screen movement uses this ease-in-out.
  absorbEase: Easing.bezier(0.77, 0, 0.175, 1),
  ringMs: 620,
  ringEase: Easing.inOut(Easing.cubic),
  holdMs: 420,
  // The sheet folding back into the island is an exit: strong ease-out, short.
  closeMs: 380,
  closeEase: Easing.bezier(0.23, 1, 0.32, 1),
  enterFrom: 0.92,
  fadeMs: 220,
  rollMs: 700,
  undoMs: 4000,
  pillSwapMs: 240,
} as const;

export const FADE = { contentFrom: 0.75, contentTo: 1.15, homeDim: 0.3, reducedMs: 200, ghost: 0.4 } as const;

/** Autoplay: one scripted finger, one clock. Every value in ms from the start of the loop. */
export const LOOP_MS = 10_000;
export const RESUME_AFTER_MS = 3_500;
export const SCRIPT = {
  down: 600,
  pullEnd: 1_900,
  /** As in source frame f025, the card is pulled almost to the bottom before the sheet takes over. */
  pullBy: 390,
  hoverStart: 2_300,
  hoverArrive: 3_300,
  release: 3_700,
  undoTap: 7_900,
} as const;

/** QA only: slows the autoplay script by this factor for slow-motion review. Keep 1. */
export const QA_SLOWMO = 1;
/** QA only: when set, the script stops at this time (ms) so a state can be photographed. Keep null. */
export const QA_HOLD_AT: number | null = null;
/** QA only: open the sheet straight away (no card in hand), as in source frame f028. Keep false. */
export const QA_SHEET = false;
