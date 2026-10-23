import { Easing, cubicBezier } from 'react-native-reanimated';

/**
 * Every number the motion uses. Springs (duration + damping ratio) carry what a finger moves or what has mass;
 * timings carry the drop's flight, light and opacity. Ease-out enters, ease-in-out crosses the screen, nothing eases in.
 */
export const EASE_OUT = [0.23, 1, 0.32, 1] as const;
export const EASE_IN_OUT = [0.77, 0, 0.175, 1] as const;
export const easeOut = Easing.bezier(...EASE_OUT);
/** On-screen morphs that are not driven by a finger (the receipt folding back, the Activity rows making room). */
export const easeInOut = Easing.bezier(...EASE_IN_OUT);

export const PULL = {
  /** The first detent is this far below the touch (pt); then one every `step`. */
  dead: 20,
  step: 16,
  /** The card is tethered: it follows the pull on a rubber band that never passes `tether` pt. */
  tether: 34,
  /** Pulling up from rest. */
  upResist: 0.12,
  /** Below this travel a touch is a tap (the card nods to teach the pull). */
  tap: 6,
} as const;

/** The drop: its radius holds the amount the way a drop's volume would (cube root), so $200 is not 40× a $5. */
export const DROP = {
  r0: 12,
  k: 5.2,
  /** How far the drop's centre hangs below the island's lower edge, as a share of its radius. */
  hang: 0.58,
  /** Smooth-union radius between island, drop and the neck left behind (pt). */
  blend: 15,
  /** Height of the liquid's edge profile as a share of the radius (a sessile drop, not a ball). */
  bevel: 0.9,
} as const;

/** The radius that holds `dollars` (0 = no drop). */
export const dropRadius = (dollars: number) => {
  'worklet';
  return dollars > 0 ? DROP.r0 + DROP.k * Math.cbrt(dollars) : 0;
};

export const SPRINGS = {
  /** Each detent: the drop takes the new amount with one liquid wobble. */
  gulp: { duration: 420, dampingRatio: 0.5 },
  /** Released or cancelled, the tethered card snaps back to its slot, carrying the finger's velocity. */
  card: { duration: 400, dampingRatio: 0.8 },
  /** Reduce Motion: the same settle without overshoot. */
  cardReduced: { duration: 400, dampingRatio: 1 },
  /** The ring sliding to another person (the finger moves it). */
  ring: { duration: 300, dampingRatio: 0.8 },
  /** The island opening into the receipt: a sheet's spring, carrying the drop's momentum. */
  receipt: { duration: 300, dampingRatio: 0.8 },
  /** The person catching the drop: squash, then one jelly rebound. */
  catch: { duration: 620, dampingRatio: 0.42 },
} as const;

export const FLIGHT = {
  /** Pinch-off and fall, one linear clock that the look shapes (ms). */
  ms: 700,
  /** Share of the clock spent travelling (ease-in-out: the slow start is the neck stretching and breaking). */
  travel: 0.78,
  /** From here to the end the drop drains into the avatar, ease-out. */
  absorbFrom: 0.64,
  /** The moment the drop meets the avatar's edge: haptic, ring ripple, receipt. */
  contact: 0.7,
  /** Speed stretches the drop along its path (a share of its radius at peak speed). */
  stretch: 0.2,
  /** The island's lip, left wobbling after the neck snaps. */
  recoilMs: 520,
  recoilHz: 5.2,
} as const;

export const SEND = {
  rippleMs: 520,
  ripple: 0.34,
  catchSquash: 0.1,
  /**
   * The receipt's content fades in over the island's growth from here; it scales with the pill. Not earlier: the pill
   * is a Skia canvas that can draw a frame behind the views, and earlier in the growth one frame is wider than the margin.
   */
  receiptContentFrom: 0.85,
  /** …and the content starts that much smaller still (gone by full size), so a lagging frame of the pill still holds it. */
  receiptContentLag: 0.06,
  /** Reduce Motion: the content does not scale, so it waits until the pill is nearly as wide as it is. */
  receiptContentFromReduced: 0.9,
  /** The receipt folding back (ease-in-out: its slow first 80 ms fade the content, then the pill folds). */
  closeMs: 240,
  rollMs: 400,
  undoMs: 4000,
  /** The card under the finger: pressed slower than it is let go (the press is the deliberate half). */
  pressScale: 0.98,
  pressInMs: 150,
  pressOutMs: 100,
  /** The ring appearing on a person and leaving (no finger on it: timing, out faster than in). */
  ringInMs: 200,
  ringOutMs: 160,
  /** A tap: the card nods `nod` pt and the island lets out a bead of `bead` pt, `nodMs` each way out. */
  nod: 12,
  bead: 11,
  nodMs: 140,
  reducedMs: 200,
  /** Reduce Motion: how long the island holds before it becomes the receipt. */
  reducedHoldMs: 120,
} as const;

/** Undo's press feedback (a Reanimated CSS transition; Reduce Motion keeps the dimming only). */
export const UNDO_PRESS = { scale: 0.97, opacity: 0.7, inMs: 150, outMs: 100 } as const;
export const EASE_OUT_CSS = cubicBezier(...EASE_OUT);

/**
 * Activity rows after a send or an Undo: a removed row fades first (`exitMs`), then the others make room
 * (`moveMs`, ease-in-out); the new row grows in from 0.97 once the room is 93 % made (`enterAfter` into the move).
 */
export const ROWS = { exitMs: 100, moveMs: 250, enterAfter: 175, enterMs: 200, enterScale: 0.97 } as const;

/** The balance's digits tick over one column after another (`stagger`), rising `travel` lines; `stepMs` is the drop's amount per detent. */
export const ROLL = { stagger: 40, travel: 0.3, stepMs: 120 } as const;

/** Autoplay: one scripted finger on one clock. Times in ms from the start of the loop, which starts at rest. */
export const LOOP_MS = 8_500;
export const RESUME_AFTER_MS = 3_500;
export const SCRIPT = {
  down: 700,
  /** Where the finger lands on the card (from the card's top-left) and how far it pulls: $45. */
  at: { x: 205, y: 150 },
  pullStart: 820,
  pullEnd: 2_050,
  pullBy: 156,
  /** Then it slides one column to the left, to Theo. */
  slideStart: 2_250,
  slideEnd: 2_750,
  slideTo: 1,
  release: 3_150,
  undo: 6_350,
} as const;
