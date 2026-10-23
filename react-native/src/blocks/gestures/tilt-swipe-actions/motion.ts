import { Easing } from 'react-native-reanimated';

/** Every motion value of the block. Distances in pt, velocities in pt/s, times in ms unless named in seconds. */
// Never ease-in on UI.
export const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
export const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
export const SHEET = [0.32, 0.72, 0, 1] as const; // iOS sheet curve: things that travel across the screen
export const EASE_SHEET = Easing.bezier(...SHEET);

/** Right swipe: pull a transaction and it breaks into equal shares, one detent per person. */
export const SPLIT = {
  detentStart: 72, // pull that makes two people
  detentStep: 60, // each further person
  hysteresis: 8, // pull back this far past a detent before it un-breaks
  follow: 0.6, // the row's content follows the finger at 0.6× before the first break
  // The row's content is gone before the pieces' content arrives, so no frame shows both (design.md started at
  // 24–72 / 48–84, which crossed the two texts at mid-pull).
  contentFade: [20, 56] as const, // pull over which the row's content fades out
  piecesIn: [56, 84] as const, // pull over which the pieces' content comes in
  dimIn: [24, 52] as const, // pull over which the other rows dim to 30 %
  hudIn: 160, // ms: the HUD arrives with the first break, over a row that is already dimmed
  overRubber: 0.35, // past the last detent
  lift: { duration: 120 }, // touch-down
  barIn: 20, // pt of pull over which the strip of paper lifts out of the ledger
  // Luxury restyle (luxury-design.md §H–W): expensive objects overshoot less.
  count: { duration: 300, dampingRatio: 0.82 }, // the bar re-flows to the new number of pieces
  snap: 60, // pt of seam opening per unit of the count spring's overshoot: the seams snap, then settle
  gapRest: 6,
  gapStretch: 0.07, // pt of seam per pt pulled past the current detent
  gapMax: 8,
  trailMax: 3, // the mock's far share trails 3 pt // the far piece trails toward the finger by up to 3 pt
  trailPer: 0.08,
  trail: { duration: 180, dampingRatio: 0.9 },
  close: { duration: 480, dampingRatio: 0.86 }, // release: the seams heal
  cancel: { duration: 360, dampingRatio: 1 },
  avatarFrom: 0.8,
  ink: 140, // your share turns to ink (and back)
  roll: 180, // a piece's amount rolls in when the count changes
  rollRise: 6,
  contentOut: 120, // the pieces' content fades as the seams heal
  dimTo: 0.28,
  settle: 220,
} as const;

/** Left swipe: Flag and Delete, iOS Mail's behaviour with every spring tuned. */
export const SWIPE = {
  activeOffsetX: 12,
  failOffsetY: 10,
  tapSlop: 8, // a touch that moves further is not a tap on Flag, Delete or the row
  openAt: 78, // strip width that opens on release
  openFlick: -500, // velocity that opens from any distance
  fullAt: 0.55, // fraction of the row's width that arms Delete
  fullRubber: 0.35, // past 85 % of the width
  rightRubber: 0.25, // dragging an open row right, past rest
  deleteFlick: -1400, // velocity that deletes when the strip is past deleteFlickAt
  deleteFlickAt: 0.4,
  open: { duration: 420, dampingRatio: 0.8 },
  close: { duration: 360, dampingRatio: 1 },
  arm: { duration: 220, dampingRatio: 0.75 }, // Delete stretches across the strip
  pillFrom: 0.6, // a pill's scale when it is born
  slideOut: 260, // the deleted row leaves (ease-out, with the finger's velocity)
  fold: 280, // the slot folds (ease-out)
} as const;

export const TIMING = {
  roll: 300, // a row's amount and the week total roll to their new value (UI budget)
  rollRise: 8,
  undoVisible: 4000,
  toastIn: 280, // sheet curve
  toastOut: 140, // gone before the sheet, growing back, reaches it
  toastRise: 16,
  toastContent: 0.55, // the toast's text comes in over the last 45 % of its rise
  flight: 360, // the requested people's avatars fly from the pieces to the toast (sheet curve)
  flightStagger: 60,
  flightDelay: 160, // they leave once the seams have started to heal
  sheet: 280, // the sheet's height when a row or the toast takes or gives room
  reduced: 180,
  reducedSwap: 150,
  pressScale: 0.97, // press feedback: subtle, it is tens of presses a day
  pressIn: 100,
  pressOut: 160,
  menuIn: 200, // the FAB's menu: a popover from its trigger, ease-out, no overshoot (nothing was flung)
  menuOut: 150, // closes faster than it opened
  menuStagger: 0.12,
  flagIn: 160, // the flag mark next to a title
  reducedCount: 150, // Reduce Motion: the shares re-flow without a spring
  tab: 260,
  tabSlide: 24,
} as const;


/** QA only: freeze the demo's clock at this second; open the FAB menu or show the Overview card. Ship null. */
export const QA_HOLD_AT: number | null = null;
export const QA_STATE: 'menu' | 'cards' | null = null;

/**
 * Autoplay: one scripted finger on one clock (seconds). Split last night's dinner three ways (two breaks, a beat on
 * each) and let go; while the request shows, swipe Metro card open (the mock's third frame), Undo the request, then
 * tap the open row shut. Rest at both ends.
 */
export const LOOP_MS = 10_000;
export const RESUME_MS = 3_500;
export const DEMO = {
  fingerFade: 0.15,
  // x: where the finger lands, from the ledger's left edge; y: below the row's centre (the mock's finger sits on
  // the bar's trailing corner, off the amounts). Each move eases between its keys.
  split: { row: 2, down: 0.7, up: 2.9, x: 193, y: 24, keys: [[0.7, 0], [1.25, 100], [1.6, 100], [2.1, 170], [2.9, 170]] as const },
  open: { row: 3, down: 3.7, up: 4.25, x: 300, y: 0, keys: [[3.7, 0], [4.25, -150]] as const },
  undo: { down: 5.6, up: 5.75 },
  close: { row: 3, x: 120, down: 7.1, up: 7.22 },
  tidy: 0.8,
} as const;
