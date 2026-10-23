// Page Curl Journal: every motion value.

// QA holds freeze one state for the fidelity screenshots (null in the shipped block).
//  'rest' = title page, 'a' = the source's mid-turn frame, 'b' = the cover page, 'p1'/'p2' = preface/foreword.
export const QA_HOLD: null | 'rest' | 'a' | 'b' | 'p1' | 'p2' = null;

// Slow-motion review: the autoplay clock, every spring and every timing run at QA_SPEED (1 in the shipped block),
// and QA_START starts the loop at that time (from the cover when it is past the last turn).
export const QA_SPEED: number = 1;
export const QA_START: number = 0;

type SpringCfg = { mass?: number; stiffness?: number; damping?: number; duration?: number; dampingRatio?: number };
export function sp<T extends SpringCfg>(c: T): T {
  'worklet';
  const k = QA_SPEED;
  if (c.stiffness !== undefined) return { ...c, stiffness: c.stiffness * k * k, damping: (c.damping ?? 10) * k };
  if (c.duration !== undefined) return { ...c, duration: c.duration / k };
  return c;
}
export function ms(x: number) {
  'worklet';
  return x / QA_SPEED;
}

// The source's mid-turn frame (015): the top-right corner of the title page lies at this point.
export const HOLD_A = { grab: [402, -4], tip: [-187.68, 250.27] };

export const TURN = {
  pickSlop: 4, // pt of travel before a drag picks forward or back
  commitVelocity: 450, // pt/s: a flick commits whatever the distance
  commitProgress: 0.5, // past half a turn the page goes over on release
  rampIn: 120, // pt of pull over which the roll grows to its full radius (a sheet first bends gently)
  rampOut: 140, // and shrinks as it lands flat
  // Mass-spring for the released page: a little heavier going over than falling back.
  over: { mass: 1, stiffness: 190, damping: 27 },
  back: { mass: 1, stiffness: 260, damping: 32 },
  // The sheet flexes with speed: the roll opens up to +60 % at 2500 pt/s and springs back when it lands.
  flexPerSpeed: 1 / 4200,
  flexMax: 0.6,
  flex: { mass: 1, stiffness: 240, damping: 14 },
  releaseKick: 1 / 9000, // a release kicks the roll's flex by its speed: a flung sheet billows, then settles
  buttonGrabY: 0.62, // "Turn the page" lifts the right edge at 62 % of the height
  preLift: 10, // pt: touch-down in the right 40 % lifts the corner within a frame
  preLiftZone: 0.6,
  backFactor: 2, // a backward drag moves the fold 1:1, so the hidden corner moves twice as far
};

// The cast shadow answers the lift: narrower and lighter while the sheet is low, as fitted at the source's lift (p ≈ 0.8).
export const LIFT = { widthBase: 0.6, widthPerTurn: 0.5, darkBase: 0.8, darkPerTurn: 0.25 };

// Riffle: a flick right this fast turns every page back to the title, sheets in flight together.
export const RIFFLE = {
  velocity: 1500, // pt/s of the fold
  staggerMs: 180,
  spring: { mass: 1, stiffness: 260, damping: 30 },
  launch: 1800, // pt/s each later sheet starts with
  slots: 3,
};

// Haptic grain while dragging: a tick per step of fold travel, never closer than minGapMs.
export const GRAIN = { stepPt: 34, minGapMs: 55 };

// Idle nudge: the bottom-right corner lifts and falls back with a wobble.
export const NUDGE = {
  afterMs: 4500,
  everyMs: 7000,
  lift: [-30, -18] as [number, number],
  up: { duration: 420, dampingRatio: 0.85 },
  down: { mass: 1, stiffness: 300, damping: 16 },
  holdMs: 160,
  kick: 1.2, // flex impulse when it drops
  max: 2, // animation review (20 Oct): two hints per idle stretch, then still until the next touch
};

// The mark's sparkles twinkle when the title page settles.
export const TWINKLE = { durationMs: 900, scale: 1.35, turnDeg: 45, staggerMs: 120 };

// Foil light on the cover: tilt moves the band; on its own it sweeps every 6 s and once on landing.
export const FOIL = {
  sweepMs: 1400,
  everyMs: 6000,
  width: 26, // pt
  amount: 0.62,
  angleDeg: 62, // band direction from the x axis
  tiltGain: 260, // pt of band travel per radian of roll
  rest: -400, // band position at rest: off the title
  reach: 520, // band position at the end of a sweep
};

// Page-stack edge at the right: one hairline per sheet still to turn.
export const STACK = { gap: 1.6, line: 1 };

export const FADE_MS = 200; // Reduce Motion: pages cross-fade

export const IDLE_RESUME_MS = 2000;

// Autoplay: a scripted finger through the same input as a real one. Times in ms on a 10 s loop.
// Each stroke: down at `from`, moves through `via` to `to`, lifts at t1 with the path's speed.
export const LOOP_MS = 10000;
export type Stroke = { t0: number; t1: number; from: [number, number]; via: [number, number]; to: [number, number] };
export const SCRIPT: Stroke[] = [
  // A hesitant peek at the corner, let go: the page falls back.
  { t0: 600, t1: 1900, from: [384, 64], via: [150, 330], to: [310, 170] },
  // Title page over to the preface: a short drag and a flick.
  { t0: 2500, t1: 3000, from: [386, 540], via: [300, 520], to: [150, 480] },
  { t0: 4000, t1: 4500, from: [388, 300], via: [300, 310], to: [160, 340] },
  { t0: 5500, t1: 6000, from: [388, 690], via: [300, 670], to: [160, 610] },
  // A fast flick right from the last page: the riffle turns every page back to the title.
  { t0: 7300, t1: 7580, from: [60, 450], via: [120, 446], to: [260, 438] },
];
