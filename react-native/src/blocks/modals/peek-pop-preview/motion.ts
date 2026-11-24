// Every number the peek and the pop use. Swift and Kotlin are ported from this file.
//
// State (all 0…1, UI thread): `press` (the finger is down on a row), `peek` (row → preview card), `sink` (the
// preview pulled back toward its row; the card is drawn at peek − sink), `pop` (preview card → full page),
// `reveal` (the excerpt and the quick actions), `scroll` (points the open page is scrolled).

/** Seconds. One autoPlay loop; rest at both ends. */
export const LOOP = 10.5;

/** Springs: response in seconds and damping ratio (mass 1). */
export const PEEK_SPRING = { response: 0.46, ratio: 0.78 };
export const POP_SPRING = { response: 0.5, ratio: 0.84 };
export const CLOSE_SPRING = { response: 0.44, ratio: 0.88 };

/** Long press: the row swells for this long, then the peek begins. */
export const PRESS = 0.32;
export const PRESS_SCALE = 0.03; // the row grows 3 %
/** Once the peek is under way the swell lets go. */
export const PRESS_RELEASE = [0.1, 0.4];

/** Finger travel that maps to the whole pop (swipe up while peeking) and the whole way back (pull down). */
export const POP_UP_SPAN = 240; // pt up for pop 0 → 1
export const POP_DOWN_SPAN = 300; // pt of banded pull for pop 1 → 0
export const POP_DOWN_RUBBER = 500;
export const SINK_SPAN = 280; // pt of banded pull for the preview to sink all the way to its row
export const SINK_RUBBER = 200;
/** Release rules. */
export const POP_COMMIT = 0.4; // of pop
export const POP_VELOCITY = 500; // pt/s up
export const SINK_CLOSE = 40; // pt down while peeking
export const SINK_VELOCITY = 500; // pt/s down
export const DISMISS_PULL = 110; // pt down on the open page
export const DISMISS_VELOCITY = 600; // pt/s
/** Closing from the open page: the pop leads and the peek follows this much later, so the card shape shows. */
export const CLOSE_FOLLOW = 0.07;
export const SCROLL_RUBBER = 120;

/** Excerpt and actions: `reveal` runs 0 → 1 in REVEAL_IN while peeking, back in REVEAL_OUT. */
export const REVEAL_IN = 0.6;
export const REVEAL_OUT = 0.18;
export const REVEAL_DELAY = 0.1; // after the peek starts
/** Where on that clock the excerpt and the three actions start, and how long each takes (share of the clock). */
export const EXCERPT_AT = 0.05;
export const ACTION_AT = [0.28, 0.4, 0.52];
export const REVEAL_SPAN = 0.48;
export const EXCERPT_BLUR = 8; // pt
export const ACTION_RISE = 12; // pt
export const PANEL_RISE = 16;
export const PANEL_SCALE = 0.94;

/** Corner radii. */
export const RADIUS_ROW = 18;
export const RADIUS_CARD = 28;

/** Behind the preview. */
export const SCENE_SCALE = 0.955;
export const SCENE_DIM = 0.3;
export const SCENE_BLUR = 16; // BlurView intensity

/** The page's nav buttons settle in over this part of the pop. */
export const NAV_AT = [0.55, 1];
export const NAV_DROP = 14;

/** autoPlay: 2 s after the last touch everything closes and the loop restarts at 0. */
export const RESUME_AFTER = 2;
export const REDUCED_MS = 200;

/** The rows the ghost finger presses. */
export const ROW_A = 1;
export const ROW_B = 3;
/** The loop's events, in seconds. */
export const T = {
  appearA: 0.5,
  downA: 0.85, // finger lands on row 2; the peek begins PRESS later
  swipeA: 2.55, // still holding, it swipes up…
  releaseA: 2.95, // …and lets go: the preview pops into the page
  scrollStart: 4.0,
  scrollEnd: 4.75,
  pullStart: 5.6, // one swipe down: first it scrolls back to the top, then it pulls the page
  releaseD: 6.3, // lets go: the page falls back into its row
  switchRow: 7.0,
  appearB: 7.05,
  downB: 7.4,
  pullB: 8.55, // a short pull down while peeking
  releaseB: 8.8, // lets go: the preview falls back
  // 9.6 – 10.5 and 0 – 0.5: rest
};
/** Ghost travel, in points. */
export const GHOST_SWIPE_UP = 150;
export const GHOST_SCROLL = 190;
export const GHOST_DISMISS = 330; // GHOST_SCROLL of it un-scrolls, the rest pulls
export const GHOST_SINK = 46;
export const GHOST_SIZE = 46;
export const GHOST_FADE = 0.18;
export const GHOST_PRESS = 0.16;

export function clamp(value: number, low: number, high: number) {
  'worklet';
  return Math.min(Math.max(value, low), high);
}

export function mix(from: number, to: number, amount: number) {
  'worklet';
  return from + (to - from) * amount;
}

/** Row → preview card → full page. */
export function mix3(row: number, card: number, full: number, peek: number, pop: number) {
  'worklet';
  return mix(mix(row, card, peek), full, pop);
}

/** 0 before a, 1 after b. */
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp((t - a) / (b - a), 0, 1);
}

export function easeOutCubic(u: number) {
  'worklet';
  const k = 1 - u;
  return 1 - k * k * k;
}

export function easeInOutCubic(u: number) {
  'worklet';
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}

/** Rubber band: follows at first, then resists. */
export function rubber(distance: number, limit: number) {
  'worklet';
  return distance / (1 + distance / limit);
}

/** d(rubber)/d(distance). */
export function rubberSlope(distance: number, limit: number) {
  'worklet';
  const k = 1 + distance / limit;
  return 1 / (k * k);
}

/** Stiffness and damping (mass 1) of a response / damping-ratio spring. */
export function stiffnessOf(response: number) {
  'worklet';
  const w = (2 * Math.PI) / response;
  return w * w;
}

export function dampingOf(response: number, ratio: number) {
  'worklet';
  return (2 * ratio * 2 * Math.PI) / response;
}

/** An under-damped spring in closed form: where it is `time` after leaving `from` at `velocity` (units/s) for `to`. */
export function springAt(time: number, from: number, to: number, velocity: number, response: number, ratio: number) {
  'worklet';
  if (time <= 0) return from;
  const w = (2 * Math.PI) / response;
  const wd = w * Math.sqrt(1 - ratio * ratio);
  const a = to - from;
  const b = (ratio * w * a - velocity) / wd;
  return to - Math.exp(-ratio * w * time) * (a * Math.cos(wd * time) + b * Math.sin(wd * time));
}

/** The ghost finger's travel 0…1 over a drag: it speeds up, so the release carries velocity. */
export function ghostPull(u: number) {
  'worklet';
  return 0.35 * u + 0.65 * u * u;
}

export function ghostPullSpeed(u: number) {
  'worklet';
  return 0.35 + 1.3 * u;
}

// ---- The loop, as functions of its time t. The finger drives the same five values with real springs. ----

/** Which row is out at t. */
export function scriptRow(t: number) {
  'worklet';
  return t < T.switchRow ? ROW_A : ROW_B;
}

function pressFrom(t: number, down: number) {
  'worklet';
  const start = down + PRESS;
  return easeOutCubic(seg(t, down, start)) * (1 - seg(t, start + PRESS_RELEASE[0], start + PRESS_RELEASE[1]));
}

export function scriptPress(t: number) {
  'worklet';
  return t < T.switchRow ? pressFrom(t, T.downA) : pressFrom(t, T.downB);
}

/** Banded pull on the open page at the moment the ghost lets go, and how fast it is growing (pt/s). */
function dismissPull(t: number) {
  'worklet';
  const u = seg(t, T.pullStart, T.releaseD);
  return Math.max(0, GHOST_DISMISS * ghostPull(u) - GHOST_SCROLL);
}

export function scriptPop(t: number) {
  'worklet';
  if (t < T.swipeA) return 0;
  if (t < T.releaseA) return (GHOST_SWIPE_UP * ghostPull(seg(t, T.swipeA, T.releaseA))) / POP_UP_SPAN;
  if (t < T.pullStart) {
    const from = GHOST_SWIPE_UP / POP_UP_SPAN;
    const velocity = (GHOST_SWIPE_UP * ghostPullSpeed(1)) / (T.releaseA - T.swipeA) / POP_UP_SPAN;
    const value = springAt(t - T.releaseA, from, 1, velocity, POP_SPRING.response, POP_SPRING.ratio);
    // The spring's last thousandth is pinned, so the page is exactly open before the next touch.
    return mix(value, 1, seg(t, T.scrollStart - 0.4, T.scrollStart - 0.1));
  }
  if (t < T.releaseD) return 1 - rubber(dismissPull(t), POP_DOWN_RUBBER) / POP_DOWN_SPAN;
  const pull = GHOST_DISMISS - GHOST_SCROLL;
  const from = 1 - rubber(pull, POP_DOWN_RUBBER) / POP_DOWN_SPAN;
  const speed = (GHOST_DISMISS * ghostPullSpeed(1)) / (T.releaseD - T.pullStart);
  const velocity = (-speed * rubberSlope(pull, POP_DOWN_RUBBER)) / POP_DOWN_SPAN;
  return springAt(t - T.releaseD, from, 0, velocity, CLOSE_SPRING.response, CLOSE_SPRING.ratio) * (1 - seg(t, T.switchRow - 0.15, T.switchRow));
}

/** Peek minus sink: how far the row has become the preview card. */
export function scriptPeek(t: number) {
  'worklet';
  if (t < T.switchRow) {
    const start = T.downA + PRESS;
    if (t < start) return 0;
    const close = T.releaseD + CLOSE_FOLLOW;
    if (t < close) return mix(springAt(t - start, 0, 1, 0, PEEK_SPRING.response, PEEK_SPRING.ratio), 1, seg(t, T.swipeA - 0.4, T.swipeA - 0.1));
    return springAt(t - close, 1, 0, 0, CLOSE_SPRING.response, CLOSE_SPRING.ratio) * (1 - seg(t, T.switchRow - 0.15, T.switchRow));
  }
  const start = T.downB + PRESS;
  if (t < start) return 0;
  if (t < T.pullB) return mix(springAt(t - start, 0, 1, 0, PEEK_SPRING.response, PEEK_SPRING.ratio), 1, seg(t, T.pullB - 0.4, T.pullB - 0.1));
  if (t < T.releaseB) return 1 - rubber(GHOST_SINK * ghostPull(seg(t, T.pullB, T.releaseB)), SINK_RUBBER) / SINK_SPAN;
  const from = 1 - rubber(GHOST_SINK, SINK_RUBBER) / SINK_SPAN;
  const speed = (GHOST_SINK * ghostPullSpeed(1)) / (T.releaseB - T.pullB);
  const velocity = (-speed * rubberSlope(GHOST_SINK, SINK_RUBBER)) / SINK_SPAN;
  return springAt(t - T.releaseB, from, 0, velocity, CLOSE_SPRING.response, CLOSE_SPRING.ratio) * (1 - seg(t, LOOP - 1.05, LOOP - 0.9));
}

export function scriptReveal(t: number) {
  'worklet';
  if (t < T.switchRow) {
    const on = T.downA + PRESS + REVEAL_DELAY;
    return seg(t, on, on + REVEAL_IN) * (1 - seg(t, T.releaseD, T.releaseD + REVEAL_OUT));
  }
  const on = T.downB + PRESS + REVEAL_DELAY;
  return seg(t, on, on + REVEAL_IN) * (1 - seg(t, T.releaseB, T.releaseB + REVEAL_OUT));
}

/** Points the open page is scrolled. */
export function scriptScroll(t: number) {
  'worklet';
  if (t < T.scrollStart) return 0;
  if (t < T.pullStart) return GHOST_SCROLL * easeInOutCubic(seg(t, T.scrollStart, T.scrollEnd));
  return Math.max(0, GHOST_SCROLL - GHOST_DISMISS * ghostPull(seg(t, T.pullStart, T.releaseD)));
}
