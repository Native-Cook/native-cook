// The wave: the edge of the next page, which waits behind the right side of the screen as a bump
// and is pulled across it. Everything here is a function of the time, so that a frame can be
// drawn from what was last decided (a grab, a release) without anything being stored per frame.

/** autoPlay: one page every PERIOD seconds; with three pages the screen is back at its start after LOOP. */
export const PERIOD = 3.2;
export const LOOP = 9.6;
// Within a period: the bump rests, a finger that is not there drags it out, lets go, and the
// next page settles for LEAD seconds before its own period starts.
export const DRAG_AT = 0.7;
export const DRAG_FOR = 0.9;
export const RELEASE = 0.8;
export const LEAD = PERIOD - DRAG_AT - DRAG_FOR - RELEASE;
/** After a touch autoPlay waits this long before it goes back to the first page and starts over. */
export const USER_HOLD = 3;
/** What moves by itself (the bump's breath, the artwork) repeats on this period, which divides PERIOD. */
export const AMBIENT = 1.6;

// The strip of the next page along the right edge, and the bump it swells into: how far the tip
// reaches into the page, half its height, how much deeper it breathes, and where it sits.
export const STRIP = 8;
export const BUMP = 46;
export const BUMP_SPREAD = 95;
export const BREATH = 6;
export const BUMP_Y = 0.604;
// The breath is gone once the tip has been pulled this far.
const BREATH_OUT = 40;
/** The arrow's centre, measured from the tip. */
export const ARROW_INSET = 20;
// While it is dragged the tip stays under the finger, the straight part follows at a third of
// the finger's speed, and the bump grows taller by this much for each point of travel.
const EDGE_FOLLOW = 0.3;
const SPREAD_GAIN = 2.4;
// The shoulders of the bump: how long the curve's handles are at the edge and at the tip, as a
// share of half the bump's height.
const K_EDGE = 0.45;
const K_TIP = 0.45;
// Let go: the straight part reaches the left side in SWEEP seconds, and the bulge springs flat.
const SWEEP = 0.5;
const SETTLE = { stiffness: 100, damping: 12 };
const FLAT_FROM = 0.6;
// Meanwhile the bump shrinks to this share of the screen's height, so that the dent is a bulge too.
const DENT_SPREAD = 0.42;
const ARROW_OUT = 0.15;
// The next page's content comes in at the end of the wave.
const REVEAL_FROM = RELEASE - 0.35;
const REVEAL_TO = RELEASE - 0.05;
// A new bump grows out of the edge with a spring, the strip and the arrow with it.
const GROW = { stiffness: 300, damping: 20 };
const STRIP_IN = 0.2;
const ARROW_FROM = 0.15;
const ARROW_TO = 0.45;
// The bump slides to the height of the finger that took it.
const GRAB = 0.18;
// Let go too early: the wave springs back.
const BACK = { stiffness: 260, damping: 22 };
export const BACK_FOR = 0.6;
const BACK_SLIDE = 0.3;
/** A release completes when the finger has travelled this share of the width, or flicks left. */
export const COMPLETE_AT = 0.4;
export const FLICK = 600;
// A wave that plays by itself pulls the tip to this share of the width before it lets go.
const AUTO_TIP = 0.4;
/** A tap on the arrow drags for this long; Skip is shorter and its release twice as fast. */
export const TAP_DRAG = 0.45;
export const SKIP_DRAG = 0.2;
export const SKIP_SPEED = 2;
// Reduced motion: the next page fades in over the old one.
const FADE = 0.3;

export const REST = 0;
export const HAND = 1;
export const SCRIPT = 2;
export const LET_GO = 3;
export const BACKING = 4;

/** What was last decided. `travel` and `cy` are the finger's, or where a release or a return started. */
export type Wave = {
  mode: number;
  page: number;
  target: number;
  /** When the mode began, in seconds on the screen's clock. */
  t0: number;
  dragFor: number;
  speed: number;
  travel: number;
  cy: number;
  cyFrom: number;
};

/** One frame: which pages show, and the wave between them. */
export type Shape = {
  page: number;
  /** The page coming in over the wave, or -1. */
  incoming: number;
  /** The wave takes the colour of the page after `page`, blended by `mix` into that of `tint`. */
  tint: number;
  mix: number;
  reach: number;
  depth: number;
  cy: number;
  spread: number;
  arrow: number;
  reveal: number;
  fade: number;
};

export function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

export function smooth(t: number, from: number, to: number) {
  'worklet';
  const u = clamp01((t - from) / (to - from));
  return u * u * (3 - 2 * u);
}

function easeOut(t: number) {
  'worklet';
  return 1 - (1 - t) * (1 - t) * (1 - t);
}

function easeInOut(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

/** How far a spring of mass 1 has come from 0 to 1 after `t` seconds; it overshoots. */
function spring(t: number, { stiffness, damping }: { stiffness: number; damping: number }) {
  'worklet';
  if (t <= 0) return 0;
  const w = Math.sqrt(stiffness);
  const z = damping / (2 * w);
  const wd = w * Math.sqrt(1 - z * z);
  return 1 - Math.exp(-z * w * t) * (Math.cos(wd * t) + ((z * w) / wd) * Math.sin(wd * t));
}

export function resting(page: number, since: number): Wave {
  'worklet';
  return { mode: REST, page, target: page, t0: since, dragFor: 0, speed: 1, travel: 0, cy: 0, cyFrom: 0 };
}

/** A wave that plays by itself from `at`: dragged out for `dragFor` seconds, then let go. */
export function script(page: number, target: number, at: number, dragFor: number, speed: number): Wave {
  'worklet';
  return { mode: SCRIPT, page, target, t0: at, dragFor, speed, travel: 0, cy: 0, cyFrom: 0 };
}

/** The wave once what was started has run out: a page that arrived, or a bump that is back. */
export function settle(w: Wave, now: number): Wave {
  'worklet';
  if (w.mode === SCRIPT || w.mode === LET_GO) {
    const end = w.t0 + (w.mode === SCRIPT ? w.dragFor : 0) + RELEASE / w.speed;
    return now >= end ? resting(w.target, end) : w;
  }
  if (w.mode === BACKING && now >= w.t0 + BACK_FOR) return resting(w.page, w.t0 + BACK_FOR - LEAD);
  return w;
}

/** Where the finger is, real or not: how far it has pulled the tip, and at which height. */
export function held(w: Wave, now: number, width: number, height: number, still: boolean) {
  'worklet';
  const restY = height * BUMP_Y;
  if (w.mode === HAND) return { travel: w.travel, cy: w.cyFrom + (w.cy - w.cyFrom) * smooth(now - w.t0, 0, GRAB) };
  if (w.mode === BACKING) {
    return { travel: w.travel * (1 - spring(now - w.t0, BACK)), cy: w.cy + (restY - w.cy) * smooth(now - w.t0, 0, BACK_SLIDE) };
  }
  if (w.mode === SCRIPT) {
    const full = still ? 0 : width * (1 - AUTO_TIP) - STRIP - BUMP;
    return { travel: full * easeInOut(clamp01((now - w.t0) / w.dragFor)), cy: restY };
  }
  return { travel: w.mode === LET_GO ? w.travel : 0, cy: w.mode === LET_GO ? w.cy : restY };
}

export function resolve(wave: Wave, now: number, width: number, height: number, count: number, still: boolean): Shape {
  'worklet';
  const w = settle(wave, now);
  const breath = still ? 0 : BREATH * (0.5 - 0.5 * Math.cos((2 * Math.PI * now) / AMBIENT));
  if (w.mode === REST) {
    const tau = now - w.t0;
    return {
      page: w.page,
      incoming: -1,
      tint: w.page,
      mix: 0,
      reach: STRIP * (still ? 1 : clamp01(tau / STRIP_IN)),
      depth: (BUMP + breath) * (still ? 1 : spring(tau, GROW)),
      cy: height * BUMP_Y,
      spread: BUMP_SPREAD,
      arrow: smooth(tau, ARROW_FROM, ARROW_TO),
      reveal: 0,
      fade: 0,
    };
  }
  const { travel, cy } = held(w, now, width, height, still);
  const reach = STRIP + EDGE_FOLLOW * travel;
  const depth = BUMP + breath * (1 - clamp01(travel / BREATH_OUT)) + (1 - EDGE_FOLLOW) * travel;
  const spread = BUMP_SPREAD + SPREAD_GAIN * travel;
  // Seconds since the wave was let go; negative while it is held.
  const u = w.mode === LET_GO ? (now - w.t0) * w.speed : w.mode === SCRIPT ? (now - w.t0 - w.dragFor) * w.speed : -1;
  if (u < 0) {
    const mix = w.mode === SCRIPT ? clamp01((now - w.t0) / w.dragFor) : 0;
    return { page: w.page, incoming: -1, tint: w.target, mix, reach, depth, cy, spread, arrow: 1, reveal: 0, fade: 0 };
  }
  if (still) {
    const fade = smooth(u, 0, FADE);
    return { page: w.page, incoming: w.target, tint: w.target, mix: 1, reach, depth, cy, spread, arrow: 1 - fade, reveal: fade, fade };
  }
  const sweep = easeOut(clamp01(u / SWEEP));
  return {
    page: w.page,
    incoming: w.target,
    tint: w.target,
    mix: 1,
    reach: reach + (width - reach) * sweep,
    // The bulge overshoots into a dent, a last sliver of the old page at the left side.
    depth: depth * (1 - spring(u, SETTLE)) * (1 - smooth(u, FLAT_FROM, RELEASE)),
    cy,
    spread: spread + (Math.min(spread, height * DENT_SPREAD) - spread) * sweep,
    arrow: 1 - clamp01(u / ARROW_OUT),
    reveal: smooth(u, REVEAL_FROM, REVEAL_TO),
    fade: 0,
  };
}

/** The next page's side of the wave as one closed path: recomputed for every frame. */
export function outline(s: Shape, width: number, height: number) {
  'worklet';
  const ex = width - s.reach;
  const tx = ex - s.depth;
  const top = s.cy - s.spread;
  const bottom = s.cy + s.spread;
  const above = Math.min(top, -1);
  const below = Math.max(bottom, height + 1);
  return (
    `M${width + 1} ${above}L${ex} ${above}L${ex} ${top}` +
    `C${ex} ${top + s.spread * K_EDGE} ${tx} ${s.cy - s.spread * K_TIP} ${tx} ${s.cy}` +
    `C${tx} ${s.cy + s.spread * K_TIP} ${ex} ${bottom - s.spread * K_EDGE} ${ex} ${bottom}` +
    `L${ex} ${below}L${width + 1} ${below}Z`
  );
}
