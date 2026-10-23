// The stage is drawn for a screen 393 by 852 points and scaled to the screen's width; every pose
// is a function of the time since something happened, so a touch and the loop that plays by
// itself make exactly the same pictures. Lengths are in the stage's points, times in seconds.

/** autoPlay: three cards are fed, the bookmark is tapped, and the screen goes back to rest. */
export const LOOP = 9;
export const AUTO_FEEDS = [0, 0.3, 0.6];
export const AUTO_ORBIT = 1.2;
export const AUTO_REST = 7.5;
/** After a touch autoPlay waits this long before it goes back to rest and starts over. */
export const USER_HOLD = 3;

export const STAGE_WIDTH = 393;
export const STAGE_HEIGHT = 852;
export const CENTER_X = STAGE_WIDTH / 2;
export const BOOK_WIDTH = 177;
export const BOOK_HEIGHT = 213;
export const BOOK_TOP = 320;
// The hub of the orbits, and the bookmark there: 70 points wide and leaning to the right.
export const ORBIT_Y = 355;
export const BOOK_SMALL = 70 / BOOK_WIDTH;
export const TILT = 15;
export const RINGS = [107.5, 182.5];
// Degrees a second: the inner ring turns clockwise, the outer one the other way.
export const SPINS = [6, -4];

// A card let go with its lower edge at the top of the stage reaches the pocket 0.45 s later.
export const GRAVITY = 3160;
// Near the pocket a card is smaller and darker; a card dragged aside is pulled back over it.
export const CARD_SHRINK = 0.85;
export const CARD_DARK = 0.25;
export const SHRINK_FROM = 100;
export const SUCK = 0.2;
// How much of the waiting card shows under the top edge, and how it comes and goes.
export const PEEK = 106;
export const WAIT_IN = 0.3;
export const WAIT_OUT = 0.2;
export const WAIT_AFTER = 0.25;
export const BACK = 0.25;
// A waiting card pulled down by less than this goes back; by more, it falls in.
export const FEED_AFTER = 40;

// The gulp: the sides bow out by BULGE and the top edge dips, then both swing back once.
export const BULGE = 9.5;
export const DIP = 4;
export const GULP_DECAY = 9;
export const GULP_SPEED = 13;
const GULP_PEAK = Math.atan(GULP_SPEED / GULP_DECAY) / GULP_SPEED;
const GULP_GAIN = 1 / (Math.exp(-GULP_DECAY * GULP_PEAK) * Math.sin(GULP_SPEED * GULP_PEAK));
export const GLOW_IN = 0.12;
export const GLOW_HOLD = 0.2;
export const GLOW_OUT = 0.5;

// To the orbits: the bookmark shrinks, springs up towards the viewer as it leans, and drops to
// the hub while the rings open and the icons fly out one after another.
export const SHRINK = 0.4;
export const SHRUNK = 0.62;
export const LIFT = 0.4;
export const LIFTED = 1.5;
export const LIFT_STIFFNESS = 324;
export const LIFT_DAMPING = 18;
export const DROP = 0.5;
export const ORBIT_IN = SHRINK + LIFT + DROP;
export const RINGS_IN = 0.4;
export const ICON_STAGGER = 0.04;
export const ICON_OUT = 0.35;
export const ICON_STIFFNESS = 400;
export const ICON_DAMPING = 22;
// Back to the pocket: icons and rings fall into the bookmark, which grows and straightens.
export const COLLAPSE = 0.4;
export const COLLAPSE_STAGGER = 0.02;
export const GROW_AFTER = 0.3;
export const GROW = 0.5;
export const POCKET_IN = GROW_AFTER + GROW;
// Without motion one picture fades out and the other in.
export const SWAP = 0.3;

// Characters a second; each letter is up to TYPE_JITTER of a letter early or late.
export const TYPE_AFTER = 0.2;
export const TYPE_RATE = 28;
export const TYPE_JITTER = 0.35;
export const DELETE_RATE = 60;
export const BLINK = 0.5;
export const LINE_SHIFT = 0.2;

/** Which picture is up, since when, and how long the rings had been turning by then. */
export type Mode = { orbit: boolean; at: number; spun: number };
/** A card let go: when, where its lower edge was, how far aside, and how strongly the pocket glows for it. */
export type Drop = { at: number; bottom: number; dx: number; glow: number };
/** The card waiting at the top edge: whether it is there, and since when. */
export type Wait = { on: boolean; at: number };
/** How far a finger has pulled the waiting card; `at` is when it was let go without being fed. */
export type Hold = { dx: number; dy: number; at: number };
/** The tagline goes from one text to another from `at` on. */
export type Tagline = { from: string; to: string; at: number };

export function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

export function smooth(t: number, from: number, to: number) {
  'worklet';
  const u = clamp01((t - from) / (to - from));
  return u * u * (3 - 2 * u);
}

export function easeIn(t: number) {
  'worklet';
  const u = clamp01(t);
  return u * u;
}

export function easeOut(t: number) {
  'worklet';
  const u = 1 - clamp01(t);
  return 1 - u * u * u;
}

export function easeInOut(t: number) {
  'worklet';
  const u = clamp01(t);
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}

/** A spring of mass 1 from 0 to 1, t seconds after it was let go. */
export function spring(t: number, stiffness: number, damping: number) {
  'worklet';
  if (t <= 0) return 0;
  const omega = Math.sqrt(stiffness);
  const zeta = damping / (2 * omega);
  const damped = omega * Math.sqrt(1 - zeta * zeta);
  return 1 - Math.exp(-zeta * omega * t) * (Math.cos(damped * t) + ((zeta * omega) / damped) * Math.sin(damped * t));
}

/** A number between 0 and 1 that is always the same for the same n. */
export function hash(n: number) {
  'worklet';
  const x = Math.sin(n * 127.1 + 311.7) * 43758.5453;
  return x - Math.floor(x);
}

/** With reduced motion: which picture shows `e` seconds after a change, and how opaque it is. */
function swapped(orbit: boolean, e: number) {
  'worklet';
  const q = clamp01(e / SWAP);
  return { orbit: q >= 0.5 ? orbit : !orbit, opacity: Math.abs(1 - 2 * q) };
}

/** The bookmark: its scale, its lean in degrees, how far it is on its way to the hub, its opacity. */
export function bookmarkPose(mode: Mode, t: number, still: boolean) {
  'worklet';
  const e = t - mode.at;
  if (still) {
    const now = swapped(mode.orbit, e);
    return { scale: now.orbit ? BOOK_SMALL : 1, tilt: now.orbit ? TILT : 0, lift: now.orbit ? 1 : 0, opacity: now.opacity };
  }
  if (mode.orbit) {
    const up = spring(e - SHRINK, LIFT_STIFFNESS, LIFT_DAMPING);
    const lifted = 1 + (SHRUNK - 1) * easeInOut(e / SHRINK) + (LIFTED - SHRUNK) * up;
    return {
      scale: lifted + (BOOK_SMALL - lifted) * easeInOut((e - SHRINK - LIFT) / DROP),
      tilt: TILT * up,
      lift: easeInOut((e - SHRINK) / (LIFT + DROP)),
      opacity: 1,
    };
  }
  const grown = easeInOut((e - GROW_AFTER) / GROW);
  return { scale: BOOK_SMALL + (1 - BOOK_SMALL) * grown, tilt: TILT * (1 - grown), lift: 1 - grown, opacity: 1 };
}

/** How open the rings are, 0 to 1. */
export function ringsAt(mode: Mode, t: number, still: boolean) {
  'worklet';
  const e = t - mode.at;
  if (still) {
    const now = swapped(mode.orbit, e);
    return now.orbit ? now.opacity : 0;
  }
  return mode.orbit ? easeOut((e - SHRINK - LIFT) / RINGS_IN) : 1 - easeIn(e / COLLAPSE);
}

/** One icon: its offset from the hub, its scale and its opacity. `turn` is what a finger added, in degrees. */
export function iconPose(index: number, ring: number, angle: number, mode: Mode, t: number, turn: number, still: boolean) {
  'worklet';
  const e = t - mode.at;
  if (still) {
    const now = swapped(mode.orbit, e);
    const at = ((angle + turn) * Math.PI) / 180;
    return { x: RINGS[ring] * Math.cos(at), y: RINGS[ring] * Math.sin(at), scale: 1, opacity: now.orbit ? now.opacity : 0 };
  }
  let out: number;
  let scale: number;
  let spun: number;
  if (mode.orbit) {
    const since = e - SHRINK - LIFT - index * ICON_STAGGER;
    out = easeOut(since / ICON_OUT);
    scale = spring(since, ICON_STIFFNESS, ICON_DAMPING);
    spun = Math.max(0, e - SHRINK - LIFT);
  } else {
    out = 1 - easeIn((e - index * COLLAPSE_STAGGER) / (COLLAPSE - 8 * COLLAPSE_STAGGER));
    scale = out;
    spun = mode.spun + e;
  }
  const at = ((angle + turn + SPINS[ring] * spun) * Math.PI) / 180;
  return { x: RINGS[ring] * out * Math.cos(at), y: RINGS[ring] * out * Math.sin(at), scale, opacity: smooth(scale, 0, 0.15) };
}

/** When the lower edge of a dropped card reaches the pocket. */
function gulpStart(drop: Drop) {
  'worklet';
  return drop.at + Math.sqrt(Math.max(0, (2 * (BOOK_TOP - drop.bottom)) / GRAVITY));
}

/** How far the bookmark's sides bow out, in points (the top edge dips in proportion), and how strong the glow under it is. */
export function gulpAt(drops: Drop[], t: number, still: boolean) {
  'worklet';
  let bulge = 0;
  let glow = 0;
  for (let i = 0; i < drops.length; i++) {
    const u = t - gulpStart(drops[i]);
    if (u <= 0) continue;
    bulge += BULGE * GULP_GAIN * Math.exp(-GULP_DECAY * u) * Math.sin(GULP_SPEED * u);
    glow += drops[i].glow * smooth(u, 0, GLOW_IN) * (1 - smooth(u, GLOW_HOLD, GLOW_HOLD + GLOW_OUT));
  }
  return { bulge: still ? 0 : bulge, glow: Math.min(1, glow) };
}

/** A card's scale for the place of its lower edge. */
export function cardScale(bottom: number) {
  'worklet';
  return 1 - (1 - CARD_SHRINK) * smooth(bottom, SHRINK_FROM, BOOK_TOP);
}

/**
 * One card: where its lower edge is, how far aside it is, its scale about that edge, how dark it
 * is and its opacity. `top` is the stage's y at the top of the screen; `waiting` says the card is
 * the one at the top edge.
 */
export function cardPose(height: number, waiting: boolean, wait: Wait, hold: Hold, drop: Drop, top: number, t: number, still: boolean) {
  'worklet';
  const shown = wait.on ? easeOut((t - wait.at) / WAIT_IN) : 1 - easeIn((t - wait.at) / WAIT_OUT);
  if (waiting && shown > 0) {
    // Pulled by a finger, or on its way back after being let go too early.
    const pull = 1 - easeOut((t - hold.at) / BACK);
    const bottom = top + PEEK * (still ? 1 : shown) + hold.dy * pull;
    const scale = cardScale(bottom);
    return { x: hold.dx * pull, bottom, scale, dark: (CARD_DARK * (1 - scale)) / (1 - CARD_SHRINK), opacity: still ? shown : 1 };
  }
  const e = t - drop.at;
  if (e < 0) return { x: 0, bottom: top, scale: 1, dark: 0, opacity: 0 };
  // Without motion a card that was fed fades where it is.
  if (still) return { x: drop.dx, bottom: drop.bottom, scale: cardScale(drop.bottom), dark: 0, opacity: 1 - clamp01(e / SUCK) };
  const bottom = drop.bottom + (GRAVITY * e * e) / 2;
  const scale = cardScale(bottom);
  // Gone once its upper edge is behind the bookmark.
  const opacity = bottom - height * scale < BOOK_TOP + 10 ? 1 : 0;
  return { x: drop.dx * (1 - easeOut(e / SUCK)), bottom, scale, dark: (CARD_DARK * (1 - scale)) / (1 - CARD_SHRINK), opacity };
}

function commonPrefix(a: string, b: string) {
  'worklet';
  let n = 0;
  while (n < a.length && n < b.length && a[n] === b[n]) n++;
  return n;
}

/** When the tagline has finished changing. */
export function typedBy(tagline: Tagline) {
  'worklet';
  const kept = commonPrefix(tagline.from, tagline.to);
  return tagline.at + (tagline.from.length - kept) / DELETE_RATE + (tagline.to.length - kept + TYPE_JITTER) / TYPE_RATE;
}

/** The tagline as it reads at t: the old text is deleted from its end down to what the two share, then the new one is typed. */
export function typedAt(tagline: Tagline, t: number, still: boolean) {
  'worklet';
  const e = t - tagline.at;
  if (e < 0) return tagline.from;
  if (still) return tagline.to;
  const kept = commonPrefix(tagline.from, tagline.to);
  const deleting = (tagline.from.length - kept) / DELETE_RATE;
  if (e < deleting) return tagline.from.slice(0, tagline.from.length - Math.floor(e * DELETE_RATE));
  // Letter n is due at (n + its own lateness) / TYPE_RATE.
  const due = (e - deleting) * TYPE_RATE;
  let count = Math.max(0, Math.floor(due) - 1);
  while (count < tagline.to.length - kept && count + (hash(count) * 2 - 1) * TYPE_JITTER <= due) count++;
  return tagline.to.slice(0, kept + count);
}
