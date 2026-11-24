// Skeleton Content Resolve: the skeleton's timing, the pull and the 10 s film, as pure worklet functions.
//
// The whole screen hangs on two clocks: `sinceHide`, the seconds since the content last turned into
// its skeleton, and `sinceShow`, the seconds since the new content started to resolve (negative
// while it is still loading). Every element reads both, adds its own stagger, and gets `k`:
// 0 = a shimmering bar, 1 = the content it stood for.
//
// No React Native imports here, so the module also runs in Node.

export const LOOP = 10; // seconds

// ---------------------------------------------------------------- one element

export const STAGGER = 0.06; // s between elements, top to bottom, as they resolve
export const RESOLVE_S = 0.62; // s one element takes to resolve
export const HIDE_STAGGER = 0.02; // s between elements as they go back to bars
export const HIDE_S = 0.3;
export const MAX_ORDER = 11; // the last element's place in the stagger
export const RESOLVE_TOTAL = MAX_ORDER * STAGGER + RESOLVE_S;
export const HIDE_TOTAL = MAX_ORDER * HIDE_STAGGER + HIDE_S;
export const SETTLED = RESOLVE_TOTAL + 0.3; // sinceShow stops counting here

// Inside one element's k (0 … 1):
export const MORPH_TO = 0.62; // the bar reaches the text's width
export const TEXT_FROM = 0.3; // the text fades in inside the bar
export const TEXT_TO = 0.85;
export const DISSOLVE_FROM = 0.5; // the bar dissolves
export const BLOOM = 0.22; // and grows this much taller as it goes
export const WIPE_FROM = 0.05; // a cover's radial wipe
export const WIPE_TO = 0.92;
export const WIPE_SOFT = 0.35; // softness of the wipe's edge, of its radius

// The shimmer: one diagonal band of light crossing every bar at the same place on the screen.
export const SHIMMER_S = 1.15; // s per crossing
export const BAND = 150; // pt
export const SLANT = 0.32; // pt sideways per pt down

// ---------------------------------------------------------------- pull to refresh

export const THRESHOLD = 62; // pt of pull that arms a refresh
export const HOLD = 46; // pt the content stays down while loading
export const PULL_GAIN = 0.55; // the content follows the finger at about half speed
export const PULL_SOFT = 480; // pt: the further, the stiffer
export const LOAD_S = 1.6; // s a refresh "loads" when a finger starts it
export const SPIN_S = 0.9; // s per turn of the spinner
export const TICKS = 8;

// The content's spring (SwiftUI: .spring(response: 0.42, dampingFraction: 0.8)).
export const OFFSET_RESPONSE = 0.42;
export const OFFSET_DAMPING = 0.8;
const OMEGA = (2 * Math.PI) / OFFSET_RESPONSE;
const OMEGA_D = OMEGA * Math.sqrt(1 - OFFSET_DAMPING * OFFSET_DAMPING);
const DECAY = OFFSET_DAMPING * OMEGA;
/** The same spring for withSpring. */
export const OFFSET_SPRING = { mass: 1, stiffness: OMEGA * OMEGA, damping: 2 * DECAY };

export const MODE_AUTO = 0;
export const MODE_MANUAL = 1;
export const MODE_RETURN = 2;
export const IDLE_S = 2; // s without a touch before the film returns

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function clamp(x: number, lo: number, hi: number) {
  'worklet';
  return x < lo ? lo : x > hi ? hi : x;
}

/** 0 before a, 1 after b. */
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function smooth(x: number) {
  'worklet';
  const c = clamp01(x);
  return c * c * (3 - 2 * c);
}

export function easeOutCubic(x: number) {
  'worklet';
  const c = clamp01(x);
  return 1 - Math.pow(1 - c, 3);
}

export function easeInOutCubic(x: number) {
  'worklet';
  const c = clamp01(x);
  return c < 0.5 ? 4 * c * c * c : 1 - Math.pow(-2 * c + 2, 3) / 2;
}

// A spring's step from 0 to 1 over u = 0 … 1, 11 % past the target on the way.
const SETTLE_A = 5.2;
const SETTLE_B = 7.5;
const SETTLE_END = 1 - Math.exp(-SETTLE_A) * (Math.cos(SETTLE_B) + (SETTLE_A / SETTLE_B) * Math.sin(SETTLE_B));

/** 0 → 1 with one overshoot, exactly 1 at u = 1. */
export function settle(u: number) {
  'worklet';
  const c = clamp01(u);
  const s = 1 - Math.exp(-SETTLE_A * c) * (Math.cos(SETTLE_B * c) + (SETTLE_A / SETTLE_B) * Math.sin(SETTLE_B * c));
  return s + (1 - SETTLE_END) * c;
}

/** An element's state: 0 = skeleton, 1 = content. `order` is its place in the stagger. */
export function kAt(order: number, sinceHide: number, sinceShow: number) {
  'worklet';
  if (sinceShow >= 0) return seg(sinceShow, order * STAGGER, order * STAGGER + RESOLVE_S);
  if (sinceHide >= 0) return 1 - seg(sinceHide, order * HIDE_STAGGER, order * HIDE_STAGGER + HIDE_S);
  return 1;
}

/** How far the bar is from its placeholder width (0) to the content's width (1). */
export function barMorph(k: number) {
  'worklet';
  return settle(k / MORPH_TO);
}

export function contentAlpha(k: number) {
  'worklet';
  return smooth(seg(k, TEXT_FROM, TEXT_TO));
}

export function barAlpha(k: number) {
  'worklet';
  return 1 - smooth(seg(k, DISSOLVE_FROM, 1));
}

export function barBloom(k: number) {
  'worklet';
  return 1 + BLOOM * smooth(seg(k, DISSOLVE_FROM, 1));
}

/** The shimmer on one element: there once the bars have formed, gone as soon as it starts to resolve. */
export function shimmerAlpha(k: number, sinceHide: number) {
  'worklet';
  if (sinceHide < 0) return 0;
  return smooth(seg(sinceHide, 0.12, 0.42)) * (1 - seg(k, 0, 0.25));
}

/** A cover's wipe, 0 = covered … 1 = open. */
export function wipe(k: number) {
  'worklet';
  return easeInOutCubic(seg(k, WIPE_FROM, WIPE_TO));
}

/** The band's centre at the top of the list, for a list `span` wide (its width plus SLANT × its height). */
export function shimmerX(sinceHide: number, span: number) {
  'worklet';
  if (sinceHide < 0) return -BAND;
  const lap = sinceHide / SHIMMER_S;
  return -BAND + (lap - Math.floor(lap)) * (span + 2 * BAND);
}

/** How far the content comes down for a finger that has moved dy. */
export function pullFor(dy: number) {
  'worklet';
  if (dy <= 0) return 0;
  return (dy * PULL_GAIN) / (1 + dy / PULL_SOFT);
}

/** The content's spring s seconds after it was let go at `from`, at rest, heading for `to`. */
export function offsetSpring(from: number, to: number, s: number) {
  'worklet';
  if (s <= 0) return from;
  const a = from - to;
  const b = (DECAY * a) / OMEGA_D;
  return to + Math.exp(-DECAY * s) * (a * Math.cos(OMEGA_D * s) + b * Math.sin(OMEGA_D * s));
}

/** One spinner tick's opacity while a pull arms it: the ticks appear one after another. */
export function tickArming(index: number, reveal: number) {
  'worklet';
  return 0.85 * smooth(seg(reveal * TICKS, index, index + 1));
}

/** One spinner tick's opacity while it spins. */
export function tickSpinning(index: number, sinceHide: number) {
  'worklet';
  const head = (Math.max(0, sinceHide) / SPIN_S) * TICKS;
  const behind = (((head - index) % TICKS) + TICKS) % TICKS;
  return 1 - 0.78 * (behind / TICKS);
}

// ---------------------------------------------------------------- the film
//
//  0.00  rest: the first set of episodes
//  0.80  a finger pulls the list down
//  1.70  let go: the content turns into bars (0.52 s), the list holds 46 pt down, the spinner turns
//  2.26  (unseen) the second set of episodes takes the bars' places
//  3.30  it resolves, top to bottom (1.28 s), the list springs back
//  5.50  a second pull
//  6.40  let go: bars again; the first set comes back underneath
//  7.75  it resolves
//  9.03  rest

export const PULL_S = 0.9;
export const FINGER_DY = 170; // pt the film's finger travels
export const RELEASE_1 = 1.7;
export const RESOLVE_1 = 3.3;
export const RELEASE_2 = 6.4;
export const RESOLVE_2 = 7.75;
export const SWAP_AFTER = HIDE_TOTAL + 0.04; // s after a release when the hidden content is replaced
export const FILM_PULL = pullFor(FINGER_DY);

/** The release that started the cycle the film is in, or -1 before the first. */
export function filmRelease(t: number) {
  'worklet';
  return t >= RELEASE_2 ? RELEASE_2 : t >= RELEASE_1 ? RELEASE_1 : -1;
}

/** When that cycle's content starts to resolve. */
export function filmResolve(t: number) {
  'worklet';
  return t >= RELEASE_2 ? RESOLVE_2 : RESOLVE_1;
}

/** Which set of episodes that cycle loads. */
export function filmTarget(t: number) {
  'worklet';
  return t >= RELEASE_2 ? 0 : t >= RELEASE_1 ? 1 : 0;
}

export function filmSinceHide(t: number) {
  'worklet';
  const r = filmRelease(t);
  return r < 0 ? -1 : t - r;
}

export function filmSinceShow(t: number) {
  'worklet';
  const r = filmRelease(t);
  if (r < 0) return SETTLED;
  const s = filmResolve(t);
  return t >= s ? Math.min(SETTLED, t - s) : -1;
}

/** Which set of episodes is mounted (it changes while the bars hide it). */
export function filmData(t: number) {
  'worklet';
  const r = filmRelease(t);
  if (r < 0) return 0;
  const target = filmTarget(t);
  return t - r >= SWAP_AFTER ? target : 1 - target;
}

/** How far the film's finger has dragged, pt. */
export function filmFinger(t: number) {
  'worklet';
  const a = t < RELEASE_1 + 0.5 ? RELEASE_1 - PULL_S : RELEASE_2 - PULL_S;
  return FINGER_DY * easeOutCubic(seg(t, a, a + PULL_S));
}

/** The ghost finger's opacity. */
export function filmGhost(t: number) {
  'worklet';
  const a1 = RELEASE_1 - PULL_S;
  const a2 = RELEASE_2 - PULL_S;
  const first = smooth(seg(t, a1 - 0.22, a1)) * (1 - smooth(seg(t, RELEASE_1, RELEASE_1 + 0.22)));
  const second = smooth(seg(t, a2 - 0.22, a2)) * (1 - smooth(seg(t, RELEASE_2, RELEASE_2 + 0.22)));
  return Math.max(first, second);
}

/** One cycle's list offset: pulled, held while loading, sprung back as it resolves. */
function cycleOffset(t: number, release: number, resolve: number) {
  'worklet';
  if (t < release - PULL_S) return 0;
  if (t < release) return pullFor(FINGER_DY * easeOutCubic(seg(t, release - PULL_S, release)));
  if (t < resolve) return offsetSpring(FILM_PULL, HOLD, t - release);
  // the spring back, forced exactly onto 0 once it has settled
  return offsetSpring(HOLD, 0, t - resolve) * (1 - smooth(seg(t, resolve + 0.7, resolve + 0.95)));
}

/** How far down the list is in the film, pt. */
export function filmOffset(t: number) {
  'worklet';
  return t < RELEASE_2 - PULL_S ? cycleOffset(t, RELEASE_1, RESOLVE_1) : cycleOffset(t, RELEASE_2, RESOLVE_2);
}

/** How armed the spinner is while the film's finger pulls, 0 … 1. */
export function filmReveal(t: number) {
  'worklet';
  const pulling = (t >= RELEASE_1 - PULL_S && t < RELEASE_1) || (t >= RELEASE_2 - PULL_S && t < RELEASE_2);
  return pulling ? clamp01(filmOffset(t) / THRESHOLD) : 0;
}
