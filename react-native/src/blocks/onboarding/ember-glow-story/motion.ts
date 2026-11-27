// Every pose is a function of the time since the page last changed, so a touch and the loop that
// plays by itself make exactly the same pictures. Times are in seconds, lengths in points.

/** autoPlay: the button is pressed before each of these moments and let go at it. */
export const LOOP = 11;
export const AUTO_TAPS = [2.6, 5.2, 7.8, 10.2];
export const PRESS_FOR = 0.15;
/** After a touch autoPlay waits this long before it goes back to the first page and starts over. */
export const USER_HOLD = 3;

// A page leaves upwards while the next one is written word by word.
export const OUT = 0.3;
export const OUT_SHIFT = 20;
export const WORDS_AFTER = 0.3;
export const WORD_STAGGER = 0.06;
export const WORD_IN = 0.25;
export const WORD_SHIFT = 8;
// Once the words are there, the key words light up one group after another, each with a flash.
export const LIGHT_AFTER = 0.15;
export const LIGHT_STAGGER = 0.3;
export const LIGHT_IN = 0.25;
export const FLASH_OUT = 0.5;
export const POP = 0.35;
// A page that continues the one before: the old words dim and move up to make room.
export const DIM = 0.3;
export const SLIDE = 0.5;
// Going back, or back to the first page, a sentence fades in whole.
export const WHOLE_IN = 0.35;
// The last page's button floods the screen with the glow before the first page returns.
export const RETURN = 0.8;
export const FLOOD = 0.35;
export const FLOOD_SCALE = 3.4;
export const FLOOD_FADE = [0.3, 0.55];

// The glow changes colour over GLOW_CROSS and surges SURGE points up the screen on the way.
export const GLOW_CROSS = 0.8;
export const SURGE = 80;
export const SURGE_IN = 0.18;
export const SURGE_OUT = 0.9;
// At rest its hot spot breathes and drifts; both periods divide LOOP.
export const BREATH = 2.2;
export const DRIFT = 15;
export const DRIFT_PERIOD = 5.5;
export const HOT_LOW = 0.6;
export const HOT_BREATH = 0.17;
// Under a finger on the button the hot spot comes over and brightens.
export const HOT_PRESS = 0.23;
export const PRESS_IN = 0.2;
export const PRESS_OUT = 0.3;
// The amber streak of the third page.
export const AMBER = [1, 1.8];

/** How a page was reached: written word by word, faded in whole, or after the flood. */
export const FORWARD = 0;
export const WHOLE = 1;
export const FLOODED = 2;

/** The page shown, the one before it, when it changed and how. */
export type Scene = { page: number; from: number; at: number; how: number };
/** The button: whether a finger is on it, since when (or when it left), and where. */
export type Press = { down: boolean; at: number; x: number };

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

/**
 * A page's block of text: its opacity and its offset. `rise` is how far a page that continues
 * the one before starts below its place; `handsOver` says the next page continues this one, so
 * this one gives way at once.
 */
export function blockPose(index: number, rise: number, handsOver: boolean, scene: Scene, t: number, still: boolean) {
  'worklet';
  const e = t - scene.at;
  if (index === scene.page) {
    if (scene.how !== FORWARD) {
      const p = easeOut((e - (scene.how === FLOODED ? RETURN - WHOLE_IN : OUT)) / WHOLE_IN);
      return { opacity: p, y: still ? 0 : WORD_SHIFT * (1 - p) };
    }
    return { opacity: e < 0 && rise > 0 ? 0 : 1, y: still ? 0 : rise * (1 - easeInOut(e / SLIDE)) };
  }
  if (index !== scene.from) return { opacity: 0, y: 0 };
  if (handsOver && scene.how === FORWARD) return { opacity: e < 0 ? 1 : 0, y: 0 };
  const p = easeIn(e / OUT);
  // Forwards the old page leaves upwards, backwards downwards.
  return { opacity: 1 - p, y: still ? 0 : (scene.page < scene.from && scene.how === WHOLE ? 1 : -1) * OUT_SHIFT * p };
}

/**
 * One word of the page that is showing. `order` is its place among the words that are new on the
 * page (negative for a word kept from the page before), `count` how many are new, `key` its turn
 * to light up (negative for a plain word), `was` whether it was lit on the page before.
 */
export function wordPose(order: number, count: number, key: number, was: boolean, scene: Scene, t: number, still: boolean) {
  'worklet';
  const e = t - scene.at;
  if (scene.how !== FORWARD) return { opacity: 1, y: 0, lit: key >= 0 ? 1 : 0, flash: 0, pop: 1 };
  if (order < 0) return { opacity: 1, y: 0, lit: was ? 1 - smooth(e, 0, DIM) : 0, flash: 0, pop: 1 };
  const shown = easeOut((e - WORDS_AFTER - (still ? 0 : order * WORD_STAGGER)) / WORD_IN);
  if (key < 0) return { opacity: shown, y: still ? 0 : WORD_SHIFT * (1 - shown), lit: 0, flash: 0, pop: 1 };
  const due = WORDS_AFTER + count * WORD_STAGGER + LIGHT_AFTER + key * LIGHT_STAGGER;
  const lit = smooth(e, due, due + LIGHT_IN);
  return {
    opacity: shown,
    y: still ? 0 : WORD_SHIFT * (1 - shown),
    lit,
    flash: still ? 0 : lit * (1 - smooth(e, due + LIGHT_IN, due + LIGHT_IN + FLASH_OUT)),
    // A glyph is a little small until its word lights up, and overshoots as it grows.
    pop: still ? 1 : 0.8 + 0.2 * lit + 0.15 * Math.sin(Math.PI * clamp01((e - due) / POP)),
  };
}

/** A word of a page that is leaving, or of any page at rest. */
export function wordAtRest(key: number) {
  'worklet';
  return { opacity: 1, y: 0, lit: key >= 0 ? 1 : 0, flash: 0, pop: 1 };
}

/**
 * One colour of the glow: its opacity, how far it has climbed, and its stretch from the bottom
 * edge. `glow` and `before` are the colours of the page showing and of the one before it.
 */
export function glowPose(color: number, glow: number, before: number, scene: Scene, t: number, still: boolean) {
  'worklet';
  const e = t - scene.at;
  const flooded = scene.how === FLOODED;
  const arrived = flooded ? smooth(e, RETURN - 0.3, RETURN) : smooth(e, 0, GLOW_CROSS);
  const left = flooded ? 1 - smooth(e, FLOOD_FADE[0], FLOOD_FADE[1]) : 1 - arrived;
  const opacity = glow === before ? (color === glow ? 1 : 0) : color === glow ? arrived : color === before ? left : 0;
  const surge = still || scene.how !== FORWARD ? 0 : SURGE * smooth(e, 0, SURGE_IN) * (1 - easeOut(e / SURGE_OUT));
  const stretch = !still && flooded && color === before && color !== glow ? 1 + (FLOOD_SCALE - 1) * easeOut(e / FLOOD) : 1;
  return { opacity, lift: surge, stretch };
}

/** The amber streak: there on a page that has it, gone soon after it is left. */
export function amberAt(has: boolean, had: boolean, scene: Scene, t: number) {
  'worklet';
  const e = t - scene.at;
  if (has) return scene.how === FORWARD ? smooth(e, AMBER[0], AMBER[1]) : smooth(e, 0, GLOW_CROSS);
  return had ? 1 - smooth(e, 0.2, GLOW_CROSS) : 0;
}

/** How much of a press the button shows, 0 to 1. */
export function pressAt(press: Press, t: number) {
  'worklet';
  return press.down ? easeOut((t - press.at) / PRESS_IN) : 1 - easeOut((t - press.at) / PRESS_OUT);
}

/** The hot spot: how bright it is, and where its middle is for a resting place `home`. */
export function hotAt(home: number, press: Press, t: number, still: boolean) {
  'worklet';
  const pressed = pressAt(press, t);
  const breath = still ? 0.5 : 0.5 - 0.5 * Math.cos((t / BREATH) * 2 * Math.PI);
  const drift = still ? 0 : DRIFT * Math.sin((t / DRIFT_PERIOD) * 2 * Math.PI);
  return { light: HOT_LOW + HOT_BREATH * breath + HOT_PRESS * pressed, x: home + drift + (press.x - home - drift) * pressed };
}
