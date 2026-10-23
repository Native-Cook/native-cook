import {
  Canvas,
  ClipOp,
  createPicture,
  FilterMode,
  MipmapMode,
  Picture,
  type SkCanvas,
  Skia,
  type SkImage,
  type SkPaint,
  TileMode,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { useEffect, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { BLURBS, CATEGORIES, photoUrl, type Session, SESSIONS } from './ScatterCollageSessions';

type Props = {
  /** The practices in the collage, in the order they are born. Twenty to thirty read best. */
  sessions?: Session[];
  /** Plays the loop by itself: the collage drifts, one photo opens, the hero moves to the next, closes. */
  autoPlay?: boolean;
  /** The page behind the collage. */
  background?: string;
  /** The one accent: the active chip and the Start button. */
  accent?: string;
  /** Called when Start is pressed on a hero. */
  onStart?: (session: Session) => void;
};

// Type: a sturdy book serif (Georgia) for the hero's title and the wordmark, the system sans for the rest.
const SERIF = process.env.EXPO_OS === 'ios' ? 'Georgia' : 'serif';
const INK = '#1d1d1b';
const MUTED = '#8d8b85';
const PAPER = '#ecebe6';

// Photo widths in pixels: a tile is drawn at most ~200 pt wide, the hero fills the screen.
const TILE_PX = 480;
const HERO_PX = 1000;

// The collage is a field of photos flying slowly towards the viewer. Each tile lives on a cycle:
// it is born small near the vanishing point, grows by GROWTH times as it comes closer, moving
// out along its own direction, and leaves past the edges, while a new one is born behind it.
// Growth is exponential in time, so the zoom feels even rather than speeding up.
const GROWTH = 5;
// Where the field opens from, as shares of the screen: a little below the middle, so the photos
// rise as they come closer.
const VANISH_X = 0.5;
const VANISH_Y = 0.52;
// How far a tile's centre is from the vanishing point at the end of its cycle, as shares of the
// screen's width and height (times a per-tile reach of 0.84…1.16).
const SPREAD_X = 0.92;
const SPREAD_Y = 0.6;
// A tile's longer side at the end of its cycle, in pt on a 402 pt wide screen (times 0.82…1.18).
const BASE_SIZE = 236;
// A tile stays hidden for the first share of its cycle (FADE_FROM), fades in until FADE_IN, and
// fades out over the last share, so the far, tiny ones never clutter the middle.
const FADE_FROM = 0.14;
const FADE_IN = 0.36;
const FADE_OUT = 0.86;
// Corner radius of a tile: a floor plus a share of its shorter side.
const RADIUS_MIN = 3;
const RADIUS_SHARE = 0.05;
// Each photo fades in over this many seconds once it has loaded.
const PHOTO_FADE = 0.4;
// Successive tiles are a golden angle apart, so the field never lines up.
const GOLDEN = 2.399963;

// Opening a tile: it flies out of the collage and grows to fill the screen on a spring, its
// corners closing to 0, while the other tiles fade out. Closing reverses it into the tile's slot.
const OPEN_SPRING = { duration: 700, dampingRatio: 0.86 };
const CLOSE_SPRING = { duration: 640, dampingRatio: 0.9 };
const FADE_TIME = 240;
// The hero's copy rises in, line by line, over the last part of the flight (the spring covers
// most of the way quickly and the last few percent slowly, so this staggers it in time).
const REVEAL_FROM = 0.66;
const REVEAL_STEP = 0.04;
const REVEAL_SPAN = 0.14;
const REVEAL_RISE = 22;
// While a hero is dragged down, the collage fades back in behind it, up to PEEK, fully so once
// the finger has pulled PEEK_SPAN of the screen's height.
const PEEK = 0.7;
const PEEK_SPAN = 0.3;
// The full-size photo fades over the small one over this many seconds once it arrives.
const SHARP_FADE = 0.3;
// Full-size photos kept in memory.
const SHARP_KEEP = 10;
// At most this many heroes open at once: the visible tiles, read top to bottom.
const MAX_PAGES = 8;

// Paging between heroes: one screen per hero, the photo lagging its frame by PARALLAX of the
// distance and the copy by COPY_PARALLAX, so the next hero pushes the last one out in layers.
const PARALLAX = 0.42;
const COPY_PARALLAX = 0.3;
const PAGE_SPRING = { duration: 520, dampingRatio: 0.88 };
const PAGE_THROW = 0.22;
const RUBBER = 0.28;

// Dragging a hero down shrinks it towards a card that follows the finger; past DISMISS_DISTANCE
// or DISMISS_SPEED it closes into its slot, otherwise it springs back.
const DISMISS_SPAN = 0.55;
const DISMISS_SHRINK = 0.3;
const DISMISS_DISTANCE = 120;
const DISMISS_SPEED = 750;
const DISMISS_RADIUS = 44;
const DRAG_BACK = { duration: 420, dampingRatio: 0.82 };

// The chips under the collage: the active one's pill slides, the other photos dim.
const CHIP_SPRING = { duration: 420, dampingRatio: 0.8 };
const FILTER_TIME = 360;
const DIMMED = 0.16;

// autoPlay loop (seconds). The collage flies; a tile opens; the hero moves to the next one; it
// is dragged down and closes into that tile's slot; the collage flies on. The flight slows to a
// stop while a hero is open, so its slot waits for it. Each tile completes exactly one cycle per
// loop, so the last frame of the loop meets the first.
const LOOP = 15;
const OPEN_AT = 3.2;
const PAGE_AT = 6.3;
const DRAG_AT = 8.6;
const DISMISS_AT = 9.0;
const STOP_AT = 3.2;
const STOP_TIME = 0.6;
const GO_AT = 9.15;
const GO_TIME = 0.85;
const DRAG_PULL = 150;
// After a touch the loop waits this long before it starts over; longer while a hero is open.
const USER_HOLD = 3.5;
const HERO_HOLD = 7;

const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}

function smooth(from: number, to: number, x: number) {
  'worklet';
  const u = clamp01((x - from) / (to - from));
  return u * u * (3 - 2 * u);
}

/** Integral of a smoothstep that rises from 0 at `at` to 1 over `span` seconds, up to time t. */
function rampArea(t: number, at: number, span: number) {
  'worklet';
  if (t <= at) return 0;
  if (t >= at + span) return span / 2 + (t - at - span);
  const u = (t - at) / span;
  return span * (u * u * u - (u * u * u * u) / 2);
}

/** Seconds of flight the loop has run by time t: the flight stops for the hero and starts again. */
function flown(t: number) {
  'worklet';
  return t - rampArea(t, STOP_AT, STOP_TIME) + rampArea(t, GO_AT, GO_TIME);
}

// One full cycle of a tile takes the flight time of one loop.
const CYCLE = flown(LOOP);

type Tile = {
  phase: number;
  angle: number;
  reach: number;
  width: number;
  height: number;
};

type Geo = { x: number; y: number; w: number; h: number; r: number; u: number; alpha: number };

function noise(i: number, k: number) {
  const x = Math.sin(i * 127.1 + k * 311.7) * 43758.5453;
  return x - Math.floor(x);
}

/** The fixed part of each tile: when it is born, which way it travels, its size and shape. */
function makeTiles(sessions: Session[]): Tile[] {
  return sessions.map((s, i) => {
    const size = 0.82 + 0.36 * noise(i, 2);
    const [w, h] = s.shape === 'portrait' ? [0.78, 1.04] : s.shape === 'landscape' ? [1.08, 0.78] : [0.92, 0.92];
    return {
      phase: i / sessions.length,
      angle: i * GOLDEN + 0.5,
      reach: 0.84 + 0.32 * noise(i, 1),
      width: w * size,
      height: h * size,
    };
  });
}

/** Where a tile is on the screen for a given flight (0…1 is one cycle). */
function tileAt(tile: Tile, travel: number, width: number, height: number): Geo {
  'worklet';
  const raw = tile.phase + travel;
  const u = raw - Math.floor(raw);
  const s = Math.pow(GROWTH, u - 1);
  const base = BASE_SIZE * (width / 402) * s;
  const w = base * tile.width;
  const h = base * tile.height;
  const cx = width * VANISH_X + Math.cos(tile.angle) * width * SPREAD_X * tile.reach * s;
  const cy = height * VANISH_Y + Math.sin(tile.angle) * height * SPREAD_Y * tile.reach * s;
  return {
    x: cx - w / 2,
    y: cy - h / 2,
    w,
    h,
    r: RADIUS_MIN + RADIUS_SHARE * Math.min(w, h),
    u,
    alpha: smooth(FADE_FROM, FADE_IN, u) * (1 - smooth(FADE_OUT, 1, u)),
  };
}

/** The part of a photo that covers a w × h box, centred. */
function coverRect(image: SkImage, w: number, h: number) {
  'worklet';
  const iw = image.width();
  const ih = image.height();
  const scale = Math.max(w / iw, h / ih);
  const sw = w / scale;
  const sh = h / scale;
  return Skia.XYWHRect((iw - sw) / 2, (ih - sh) / 2, sw, sh);
}

/**
 * The hero's frame: between the tile's slot (open = 0) and the screen (open = 1). A drag down
 * turns the screen into a card that shrinks and follows the finger.
 */
function heroRect(slot: Geo, open: number, dx: number, dy: number, width: number, height: number) {
  'worklet';
  const pull = clamp01(dy / (height * DISMISS_SPAN));
  const s = 1 - DISMISS_SHRINK * pull;
  const fw = width * s;
  const fh = height * s;
  const fx = (width - fw) / 2 + dx;
  const fy = (height - fh) / 2 + dy * 0.62;
  const fr = DISMISS_RADIUS * clamp01(pull * 3);
  return {
    x: slot.x + (fx - slot.x) * open,
    y: slot.y + (fy - slot.y) * open,
    w: Math.max(1, slot.w + (fw - slot.w) * open),
    h: Math.max(1, slot.h + (fh - slot.h) * open),
    r: Math.max(0, slot.r + (fr - slot.r) * clamp01(open)),
  };
}

/** True when the clock went past `at` between `before` and `now`, wrapping round the loop. */
function passedAt(before: number, now: number, at: number) {
  'worklet';
  return (before < at && now >= at) || (now < before && (at > before || at <= now));
}

/** How much of a line of the hero's copy has risen in, by its place in the stagger. */
function reveal(open: number, order: number) {
  'worklet';
  return smooth(REVEAL_FROM + order * REVEAL_STEP, REVEAL_FROM + order * REVEAL_STEP + REVEAL_SPAN, open);
}

// Sort orders, as worklets of their own: the React Compiler hoists inline callbacks out of the
// worklets they sit in, which would leave them on the wrong runtime.
function byArea(a: { area: number }, b: { area: number }) {
  'worklet';
  return b.area - a.area;
}

function byY(a: { y: number }, b: { y: number }) {
  'worklet';
  return a.y - b.y;
}

function byDepth(a: { g: Geo }, b: { g: Geo }) {
  'worklet';
  return a.g.u - b.g.u;
}

type Loaded = { image: SkImage; at: number } | null;

/**
 * A photo in a rounded box, cropped to cover it: paper until the small copy arrives, then the
 * small copy, then the full-size one fading over it. `shift` slides the photo inside its box
 * (the paging parallax); `shade` darkens the top and the bottom for the hero's copy.
 */
function drawPhoto(
  canvas: SkCanvas,
  paint: SkPaint,
  low: Loaded,
  high: Loaded,
  now: number,
  x: number,
  y: number,
  w: number,
  h: number,
  r: number,
  shift: number,
  alpha: number,
  shade: number,
) {
  'worklet';
  const box = Skia.XYWHRect(x, y, w, h);
  const rrect = Skia.RRectXY(box, r, r);
  const lowIn = low ? clamp01((now - low.at) / PHOTO_FADE) : 0;
  const highIn = high ? clamp01((now - high.at) / SHARP_FADE) : 0;
  canvas.save();
  canvas.clipRRect(rrect, ClipOp.Intersect, true);
  if (lowIn < 1 && highIn < 1) {
    paint.setColor(Skia.Color(PAPER));
    paint.setAlphaf(alpha);
    canvas.drawRect(box, paint);
  }
  const dest = Skia.XYWHRect(x + shift, y, w, h);
  paint.setColor(Skia.Color('#000000'));
  if (low && lowIn > 0 && highIn < 1) {
    paint.setAlphaf(alpha * lowIn);
    canvas.drawImageRectOptions(low.image, coverRect(low.image, w, h), dest, FilterMode.Linear, MipmapMode.Linear, paint);
  }
  if (high && highIn > 0) {
    paint.setAlphaf(alpha * highIn);
    canvas.drawImageRectOptions(high.image, coverRect(high.image, w, h), dest, FilterMode.Linear, MipmapMode.Linear, paint);
  }
  if (shade > 0.004) {
    const veil = Skia.Paint();
    veil.setAlphaf(alpha * shade);
    veil.setShader(
      Skia.Shader.MakeLinearGradient(
        vec(0, y),
        vec(0, y + h),
        [Skia.Color('rgba(12,12,10,0.42)'), Skia.Color('rgba(12,12,10,0)'), Skia.Color('rgba(12,12,10,0.12)'), Skia.Color('rgba(12,12,10,0.86)')],
        [0, 0.2, 0.42, 1],
        TileMode.Clamp,
      ),
    );
    canvas.drawRect(box, veil);
  }
  canvas.restore();
}

const haptic = {
  open: () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  },
  tick: () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  },
  start: () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
  },
};

/**
 * A wellness library as an airy collage: photos of different sizes float on a white page,
 * flying slowly towards the viewer at different depths, the small far ones barely moving and
 * the near ones sliding out past the edges. Tapping one flies it out of the collage into a
 * full-screen hero with a serif title, a Start button and a strip of thumbnails. Swiping the
 * hero sideways pushes in the next one; dragging it down closes it back into its slot.
 */
export function ScatterCollageHero({
  sessions = SESSIONS,
  autoPlay = false,
  background = '#fafaf7',
  accent = '#cdeec3',
  onStart,
}: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [tiles] = useState(() => makeTiles(sessions));
  // The heroes that are open, as indices into `sessions`, or null while the collage shows.
  const [hero, setHero] = useState<number[] | null>(null);
  const [chip, setChip] = useState(0);
  const [chipBoxes, setChipBoxes] = useState<{ x: number; width: number }[]>([]);

  const stage = useSharedValue({ width: 0, height: 0, top: 0, bottom: 0 });
  // How far the field has flown, in cycles (0…1); a clock for the photo fades.
  const travel = useSharedValue(0);
  const elapsed = useSharedValue(0);
  const loaded = useSharedValue<Loaded[]>([]);
  // Full-size copies for the heroes, fetched when a hero opens.
  const sharp = useSharedValue<Loaded[]>([]);
  // The hero: 0 in its slot, 1 full screen. `page` is the hero in front, `ids` the open list.
  const open = useSharedValue(0);
  const page = useSharedValue(0);
  const ids = useSharedValue<number[]>([]);
  const heroOn = useSharedValue(false);
  const closing = useSharedValue(false);
  const dragX = useSharedValue(0);
  const dragY = useSharedValue(0);
  // The pan's direction once it is clear: 1 across (paging), 2 down (closing).
  const axis = useSharedValue(0);
  const panFrom = useSharedValue(0);
  // The chips' filter, faded from one category to the next.
  const catFrom = useSharedValue(0);
  const catTo = useSharedValue(0);
  const catMix = useSharedValue(1);
  const pillX = useSharedValue(0);
  const pillW = useSharedValue(0);
  // autoPlay.
  const clock = useSharedValue(0);
  const looping = useSharedValue(auto);
  const hold = useSharedValue(0);

  useEffect(() => {
    looping.set(auto);
    clock.set(0);
    hold.set(0);
  }, [auto, clock, hold, looping]);

  // The collage draws its photos with Skia, each fading in as it arrives; the hero's copies are
  // fetched ahead to disk so a hero sharpens quickly once it opens.
  useEffect(() => {
    let alive = true;
    loaded.set(sessions.map(() => null));
    sharp.set(sessions.map(() => null));
    sessions.forEach((s, i) => {
      Skia.Data.fromURI(photoUrl(s.photo, TILE_PX))
        .then((data) => Skia.Image.MakeImageFromEncoded(data))
        .then((image) => {
          if (!alive || !image) return;
          loaded.modify((list) => {
            'worklet';
            const next = list.slice();
            next[i] = { image, at: elapsed.get() };
            return next;
          });
        })
        .catch(() => {});
    });
    // The thumbnails in the hero's strip use the same small photos.
    Image.prefetch(sessions.map((s) => photoUrl(s.photo, TILE_PX)), 'memory-disk').catch(() => {});
    return () => {
      alive = false;
    };
  }, [sessions, loaded, sharp, elapsed]);

  // Fetches the full-size photos of the heroes that just opened, the tapped one first, and lets
  // go of the ones furthest back once more than SHARP_KEEP are held.
  const sharpen = (list: number[], first: number) => {
    const order = [list[first], ...list.filter((_, k) => k !== first)];
    for (const index of order) {
      if (sharp.get()[index]) continue;
      Skia.Data.fromURI(photoUrl(sessions[index].photo, HERO_PX))
        .then((data) => Skia.Image.MakeImageFromEncoded(data))
        .then((image) => {
          if (!image) return;
          sharp.modify((held) => {
            'worklet';
            const next = held.slice();
            next[index] = { image, at: elapsed.get() };
            let count = 0;
            for (let k = next.length - 1; k >= 0; k -= 1) if (next[k]) count += 1;
            for (let k = 0; k < next.length && count > SHARP_KEEP; k += 1) {
              if (next[k] && list.indexOf(k) < 0) {
                next[k] = null;
                count -= 1;
              }
            }
            return next;
          });
        })
        .catch(() => {});
    }
  };

  const beginHero = (list: number[], first: number, user: boolean) => {
    setHero(list);
    sharpen(list, first);
    if (user) haptic.open();
  };
  const endHero = () => setHero(null);

  // Worklet helpers are named function declarations, defined before the worklets that call them.
  /** The category's weight on a tile: 1 when it is shown, DIMMED when filtered out. */
  function shown(index: number, category: number) {
    'worklet';
    return category === 0 || CATEGORIES[category] === sessions[index].category ? 1 : DIMMED;
  }

  /** The tiles fully on screen, read top to bottom, that a hero can page through. */
  function heroList(must: number) {
    'worklet';
    const { width, height, top, bottom } = stage.get();
    const t = travel.get();
    const found: { i: number; y: number; area: number }[] = [];
    for (let i = 0; i < tiles.length; i += 1) {
      const g = tileAt(tiles[i], t, width, height);
      const inside = g.x >= 6 && g.x + g.w <= width - 6 && g.y >= top + 6 && g.y + g.h <= height - bottom - 6;
      if (i === must || (inside && g.alpha > 0.9 && shown(i, catTo.get()) === 1)) {
        found.push({ i, y: g.y + g.h / 2, area: g.w * g.h });
      }
    }
    // The largest few, always with the tapped one, then read top to bottom.
    found.sort(byArea);
    const kept = found.slice(0, MAX_PAGES);
    let has = false;
    for (const f of kept) if (f.i === must) has = true;
    if (!has) {
      for (const f of found) if (f.i === must) kept[kept.length - 1] = f;
    }
    kept.sort(byY);
    const out: number[] = [];
    for (const f of kept) out.push(f.i);
    return out;
  }

  /** The tile the loop opens: a mid-sized one near the middle, fully on screen. */
  function showcase() {
    'worklet';
    const { width, height, top, bottom } = stage.get();
    const t = travel.get();
    let best = 0;
    let score = Infinity;
    for (let i = 0; i < tiles.length; i += 1) {
      const g = tileAt(tiles[i], t, width, height);
      const inside = g.x >= 12 && g.x + g.w <= width - 12 && g.y >= top + 40 && g.y + g.h <= height - bottom - 90;
      if (!inside || g.alpha < 0.95) continue;
      const off = Math.abs(g.x + g.w / 2 - width * 0.5) + Math.abs(g.y + g.h / 2 - height * 0.46) - g.w * 0.8;
      if (off < score) {
        score = off;
        best = i;
      }
    }
    return best;
  }

  function openTile(index: number, user: boolean) {
    'worklet';
    if (heroOn.get()) return;
    const list = heroList(index);
    const first = list.indexOf(index);
    ids.set(list);
    page.set(first);
    dragX.set(0);
    dragY.set(0);
    closing.set(false);
    heroOn.set(true);
    // The flight is drawn on the UI thread, so it starts on this frame; the copy and controls
    // mount meanwhile and rise in over its last part.
    open.set(0);
    open.set(reducedMotion ? withTiming(1, { duration: FADE_TIME }) : withSpring(1, OPEN_SPRING));
    scheduleOnRN(beginHero, list, first, user);
  }

  function closeHero(user: boolean, velocity: number) {
    'worklet';
    if (!heroOn.get() || closing.get()) return;
    closing.set(true);
    const done = (finished?: boolean) => {
      'worklet';
      // Cut short: the hero stays up and can still be closed, never stuck half-closing.
      if (!finished) {
        closing.set(false);
        return;
      }
      heroOn.set(false);
      closing.set(false);
      dragX.set(0);
      dragY.set(0);
      scheduleOnRN(endHero);
    };
    open.set(
      reducedMotion
        ? withTiming(0, { duration: FADE_TIME }, done)
        : withSpring(0, { ...CLOSE_SPRING, velocity: -velocity }, done),
    );
    if (user) scheduleOnRN(haptic.open);
  }

  function goTo(target: number, user: boolean, velocity: number) {
    'worklet';
    const n = ids.get().length;
    if (n === 0) return;
    const to = Math.min(n - 1, Math.max(0, Number.isFinite(target) ? target : 0));
    const before = Math.round(page.get());
    page.set(reducedMotion ? withTiming(to, { duration: FADE_TIME }) : withSpring(to, { ...PAGE_SPRING, velocity: Number.isFinite(velocity) ? velocity : 0 }));
    if (user && to !== before) scheduleOnRN(haptic.tick);
  }

  function touched() {
    'worklet';
    hold.set(heroOn.get() ? HERO_HOLD : USER_HOLD);
  }

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    if (dt <= 0 || stage.get().width === 0) return;
    elapsed.set(elapsed.get() + dt);
    if (hold.get() > 0) {
      hold.set(hold.get() - dt);
      if (hold.get() <= 0) {
        clock.set(0);
        // A hero left open goes back to its slot before the loop starts over.
        if (looping.get() && heroOn.get() && axis.get() === 0) closeHero(false, 0);
      }
    }
    if (looping.get() && hold.get() <= 0) {
      const before = clock.get();
      const t = (before + dt) % LOOP;
      clock.set(t);
      const step = t >= before ? flown(t) - flown(before) : flown(LOOP) - flown(before) + flown(t);
      travel.set((travel.get() + step / CYCLE) % 1);
      if (passedAt(before, t, OPEN_AT)) openTile(showcase(), false);
      if (passedAt(before, t, PAGE_AT) && heroOn.get() && ids.get().length > 1) {
        const k = Math.round(page.get());
        goTo(k + 1 < ids.get().length ? k + 1 : k - 1, false, 0);
      }
      if (passedAt(before, t, DRAG_AT) && heroOn.get()) {
        dragY.set(withTiming(DRAG_PULL, { duration: 380, easing: EASE_IN_OUT }));
        dragX.set(withTiming(-12, { duration: 380, easing: EASE_IN_OUT }));
      }
      if (passedAt(before, t, DISMISS_AT)) closeHero(false, 1.4);
    } else if (!reducedMotion) {
      // By hand the field flies whenever no hero covers it.
      travel.set((travel.get() + (dt * (1 - clamp01(open.get()))) / CYCLE) % 1);
    }
  });

  /** The hero in front, as an index into `ids`. */
  function front() {
    'worklet';
    return Math.max(0, Math.min(ids.get().length - 1, Math.round(page.get())));
  }

  const picture = useDerivedValue(() => {
    const { width, height } = stage.get();
    return createPicture(
      (canvas) => {
        if (width === 0) return;
        const paint = Skia.Paint();
        paint.setAntiAlias(true);
        const t = travel.get();
        const now = elapsed.get();
        const images = loaded.get();
        const on = heroOn.get();
        // The hero's own tile is drawn by the hero; the rest fade out under it, and back in a
        // little while it is dragged down.
        const hidden = on ? ids.get()[front()] : -1;
        const peek = clamp01(dragY.get() / (height * PEEK_SPAN));
        const others = 1 - clamp01(open.get()) * (1 - PEEK * peek);
        const mix = catMix.get();
        const from = catFrom.get();
        const to = catTo.get();
        // Far tiles first, so the near ones pass over them.
        const field: { i: number; g: Geo }[] = [];
        for (let i = 0; i < tiles.length; i += 1) field.push({ i, g: tileAt(tiles[i], t, width, height) });
        field.sort(byDepth);
        for (const { i, g } of field) {
          if (i === hidden) continue;
          const weight = shown(i, from) + (shown(i, to) - shown(i, from)) * mix;
          const alpha = g.alpha * weight * others;
          if (alpha < 0.004) continue;
          if (g.x > width || g.y > height || g.x + g.w < 0 || g.y + g.h < 0) continue;
          drawPhoto(canvas, paint, images[i], null, now, g.x, g.y, g.w, g.h, g.r, 0, alpha, 0);
        }
      },
      { width, height },
    );
  });

  // The hero, drawn above the collage's chrome: in flight (opening, closing or dragged down) it
  // is one photo between its slot and the screen; open, it is a row of screens paged sideways.
  const heroPicture = useDerivedValue(() => {
    const { width, height } = stage.get();
    return createPicture(
      (canvas) => {
        if (width === 0 || !heroOn.get()) return;
        const paint = Skia.Paint();
        paint.setAntiAlias(true);
        const list = ids.get();
        const low = loaded.get();
        const high = sharp.get();
        const now = elapsed.get();
        const p = page.get();
        const o = open.get();
        const dx = dragX.get();
        const dy = dragY.get();
        const shade = reveal(o, 0) * (1 - clamp01(dy / 160));
        const flying = clamp01(o) < 0.999 || closing.get() || Math.abs(dx) > 0.5 || Math.abs(dy) > 0.5;
        if (list.length === 0) return;
        if (flying) {
          const index = list[front()];
          if (reducedMotion) {
            drawPhoto(canvas, paint, low[index], high[index], now, 0, 0, width, height, 0, 0, clamp01(o), shade);
            return;
          }
          const slot = tileAt(tiles[index], travel.get(), width, height);
          const r = heroRect(slot, o, dx, dy, width, height);
          drawPhoto(canvas, paint, low[index], high[index], now, r.x, r.y, r.w, r.h, r.r, 0, 1, shade);
          return;
        }
        const k0 = Math.floor(p);
        for (let k = k0; k <= k0 + 1; k += 1) {
          if (k < 0 || k >= list.length) continue;
          const d = k - p;
          if (Math.abs(d) >= 1) continue;
          const index = list[k];
          if (reducedMotion) {
            drawPhoto(canvas, paint, low[index], high[index], now, 0, 0, width, height, 0, 0, 1 - Math.abs(d), shade);
          } else {
            drawPhoto(canvas, paint, low[index], high[index], now, d * width, 0, width, height, 0, -d * width * PARALLAX, 1, shade);
          }
        }
      },
      { width, height },
    );
  });

  const tap = Gesture.Tap()
    .maxDuration(400)
    .onEnd((event, success) => {
      if (!success || heroOn.get()) return;
      touched();
      const { width, height } = stage.get();
      const t = travel.get();
      // The nearest tile under the finger.
      let hit = -1;
      let nearest = -1;
      for (let i = 0; i < tiles.length; i += 1) {
        const g = tileAt(tiles[i], t, width, height);
        if (g.alpha < 0.4 || shown(i, catTo.get()) < 1) continue;
        if (event.x >= g.x && event.x <= g.x + g.w && event.y >= g.y && event.y <= g.y + g.h && g.u > nearest) {
          nearest = g.u;
          hit = i;
        }
      }
      if (hit >= 0) {
        openTile(hit, true);
        // Held longer now that a hero is open, so it can be read before the loop takes it back.
        touched();
      }
    });

  const heroPan = Gesture.Pan()
    .maxPointers(1)
    .minDistance(6)
    .onStart(() => {
      if (!heroOn.get() || closing.get()) return;
      touched();
      axis.set(0);
      page.set(page.get());
      panFrom.set(page.get());
    })
    .onUpdate((event) => {
      if (!heroOn.get() || closing.get()) return;
      const { width } = stage.get();
      if (axis.get() === 0) {
        axis.set(Math.abs(event.translationX) > Math.abs(event.translationY) ? 1 : 2);
        dragX.set(dragX.get());
        dragY.set(dragY.get());
      }
      if (axis.get() === 1) {
        // Past the first or the last hero the pager gives way less and less.
        const n = ids.get().length;
        let p = panFrom.get() - event.translationX / width;
        if (p < 0) p = p * RUBBER;
        if (p > n - 1) p = n - 1 + (p - (n - 1)) * RUBBER;
        page.set(p);
      } else {
        dragX.set(event.translationX * 0.7);
        dragY.set(event.translationY > 0 ? event.translationY : event.translationY * 0.18);
      }
    })
    .onEnd((event) => {
      if (!heroOn.get() || closing.get()) return;
      touched();
      const { width, height } = stage.get();
      if (axis.get() === 1) {
        const v = -event.velocityX / width;
        const from = Math.round(panFrom.get());
        const aim = Math.round(page.get() + v * PAGE_THROW);
        goTo(Math.min(from + 1, Math.max(from - 1, aim)), true, v);
      } else if (event.translationY > DISMISS_DISTANCE || event.velocityY > DISMISS_SPEED) {
        closeHero(true, Math.max(0, event.velocityY) / (height * 0.5));
      } else {
        dragX.set(withSpring(0, { ...DRAG_BACK, velocity: event.velocityX * 0.7 }));
        dragY.set(withSpring(0, { ...DRAG_BACK, velocity: event.velocityY }));
      }
      axis.set(0);
    })
    .onFinalize(() => {
      // A cancelled pan never reaches onEnd: settle on a hero and put a dragged one back.
      if (axis.get() !== 0 && heroOn.get() && !closing.get()) {
        if (axis.get() === 1) goTo(Math.round(page.get()), false, 0);
        else {
          dragX.set(withSpring(0, DRAG_BACK));
          dragY.set(withSpring(0, DRAG_BACK));
        }
      }
      axis.set(0);
    });

  const chooseChip = (index: number) => {
    touched();
    if (index === chip) return;
    setChip(index);
    haptic.tick();
    catFrom.set(catTo.get());
    catTo.set(index);
    catMix.set(0);
    catMix.set(withTiming(1, { duration: FILTER_TIME, easing: EASE_IN_OUT }));
    const box = chipBoxes[index];
    if (box) {
      pillX.set(reducedMotion ? box.x : withSpring(box.x, CHIP_SPRING));
      pillW.set(reducedMotion ? box.width : withSpring(box.width, CHIP_SPRING));
    }
  };

  const chromeStyle = useAnimatedStyle(() => ({ opacity: 1 - clamp01(open.get()) }));
  const pillStyle = useAnimatedStyle(() => ({ transform: [{ translateX: pillX.get() }], width: pillW.get() }));

  const width = size?.width ?? 0;

  return (
    <View
      style={[styles.screen, { backgroundColor: background }]}
      accessible={!hero}
      accessibilityLabel="Practice library: a collage of photos. Tap a photo to open it."
      onLayout={(event: LayoutChangeEvent) => {
        const layout = event.nativeEvent.layout;
        // A zero first layout would leave the pager dividing by a 0 width.
        if (layout.width <= 0 || layout.height <= 0) return;
        setSize({ width: layout.width, height: layout.height });
        stage.set({ width: layout.width, height: layout.height, top: insets.top, bottom: insets.bottom });
      }}
    >
      <GestureDetector gesture={tap}>
        <View style={StyleSheet.absoluteFill}>
          {size && (
            <Canvas style={StyleSheet.absoluteFill}>
              <Picture picture={picture} />
            </Canvas>
          )}
        </View>
      </GestureDetector>

      {/* The collage's chrome: a soft white fade under the wordmark and over the chips. */}
      <Animated.View pointerEvents={hero ? 'none' : 'box-none'} style={[StyleSheet.absoluteFill, chromeStyle]}>
        <LinearGradient
          pointerEvents="none"
          colors={[background, `${background}00`]}
          style={[styles.topFade, { height: insets.top + 86 }]}
        />
        <View pointerEvents="none" style={[styles.header, { top: insets.top + 8 }]}>
          <View>
            <Text style={styles.wordmark}>Stillhour</Text>
            <Text style={styles.subtitle}>{sessions.length} practices for slow days</Text>
          </View>
          <Avatar initials="AK" tone={accent} />
        </View>
        <LinearGradient
          pointerEvents="none"
          colors={[`${background}00`, background]}
          style={[styles.bottomFade, { height: insets.bottom + 120 }]}
        />
        <View style={[styles.chips, { bottom: insets.bottom + 14 }]}>
          <Animated.View style={[styles.pill, { backgroundColor: accent }, pillStyle]} />
          {CATEGORIES.map((label, index) => (
            <Pressable
              key={label}
              accessibilityRole="button"
              accessibilityState={{ selected: chip === index }}
              onPress={() => chooseChip(index)}
              onLayout={(event: LayoutChangeEvent) => {
                const { x, width: w } = event.nativeEvent.layout;
                setChipBoxes((list) => {
                  const next = list.slice();
                  next[index] = { x, width: w };
                  return next;
                });
                if (index === chip) {
                  pillX.set(x);
                  pillW.set(w);
                }
              }}
              style={[styles.chip, chip !== index && styles.chipIdle]}
            >
              <Text style={[styles.chipLabel, { color: chip === index ? INK : MUTED }]}>{label}</Text>
            </Pressable>
          ))}
        </View>
      </Animated.View>

      {/* The hero's photos, above the collage's chrome. */}
      {size && (
        <Canvas pointerEvents="none" style={StyleSheet.absoluteFill}>
          <Picture picture={heroPicture} />
        </Canvas>
      )}

      {/* The open hero's copy and controls. */}
      {hero && size && (
        <GestureDetector gesture={heroPan}>
          <View style={StyleSheet.absoluteFill}>
            {hero.map((sessionIndex, k) => (
              <HeroCopy
                key={sessionIndex}
                index={k}
                session={sessions[sessionIndex]}
                width={width}
                bottom={insets.bottom}
                open={open}
                page={page}
                dragY={dragY}
                reducedMotion={reducedMotion}
              />
            ))}
            <HeroControls
              list={hero}
              sessions={sessions}
              width={width}
              insets={insets}
              accent={accent}
              open={open}
              page={page}
              dragY={dragY}
              onClose={() => {
                touched();
                closeHero(true, 0);
              }}
              onPick={(k) => {
                touched();
                goTo(k, true, 0);
              }}
              onStart={(k) => {
                touched();
                haptic.start();
                // The pager can sit past either end mid-swipe; Start always names a real hero.
                const at = Math.min(hero.length - 1, Math.max(0, k));
                onStart?.(sessions[hero[at]]);
              }}
            />
          </View>
        </GestureDetector>
      )}
    </View>
  );
}

type CopyProps = {
  index: number;
  session: Session;
  width: number;
  bottom: number;
  open: SharedValue<number>;
  page: SharedValue<number>;
  dragY: SharedValue<number>;
  reducedMotion: boolean;
};

// The hero's bottom stack, from the bottom edge up: thumbnails, the Start row, the copy.
const THUMB_H = 84;
const THUMB_GAP = 10;
const SIDE = 22;
const ROW_H = 48;
const stackBottom = (bottom: number) => bottom + 12;
const rowBottom = (bottom: number) => stackBottom(bottom) + THUMB_H + 24;
const copyBottom = (bottom: number) => rowBottom(bottom) + ROW_H + 22;

/** A hero's words: guide, title, line and the time and date beside Start, sliding with its page. */
function HeroCopy({ index, session, width, bottom, open, page, dragY, reducedMotion }: CopyProps) {
  const line = (order: number) => () => {
    'worklet';
    const d = index - page.get();
    const shown = reveal(open.get(), order);
    const nearby = clamp01(1 - Math.abs(d) * 1.6);
    const drag = 1 - clamp01(dragY.get() / 140);
    return {
      opacity: shown * nearby * drag,
      transform: [
        { translateX: reducedMotion ? 0 : d * width * COPY_PARALLAX },
        { translateY: reducedMotion ? 0 : (1 - shown) * REVEAL_RISE },
      ],
    };
  };
  const guide = useAnimatedStyle(line(0));
  const first = useAnimatedStyle(line(1));
  const second = useAnimatedStyle(line(2));
  const blurb = useAnimatedStyle(line(3));
  const meta = useAnimatedStyle(line(4));
  const initials = session.guide
    .split(' ')
    .map((w) => w[0])
    .join('');
  return (
    <View pointerEvents="none" style={StyleSheet.absoluteFill}>
      <View style={[styles.copy, { bottom: copyBottom(bottom) }]}>
        <Animated.View style={[styles.guideRow, guide]}>
          <View style={styles.guideDot}>
            <Text style={styles.guideInitials}>{initials}</Text>
          </View>
          <Text style={styles.guideName}>{session.guide}</Text>
        </Animated.View>
        <Animated.Text style={[styles.title, first]}>{session.title[0]}</Animated.Text>
        <Animated.Text style={[styles.title, styles.titleItalic, second]}>{session.title[1]}</Animated.Text>
        <Animated.Text numberOfLines={2} style={[styles.blurb, blurb]}>
          {BLURBS[session.category]}
        </Animated.Text>
      </View>
      <Animated.View style={[styles.metaRow, { bottom: rowBottom(bottom) }, meta]}>
        <View style={styles.meta}>
          <ClockIcon />
          <View>
            <Text style={styles.metaLabel}>Time</Text>
            <Text style={styles.metaValue}>{session.minutes} min</Text>
          </View>
        </View>
        <View style={styles.meta}>
          <View style={styles.metaRight}>
            <Text style={[styles.metaLabel, styles.alignRight]}>Added</Text>
            <Text style={[styles.metaValue, styles.alignRight]}>{session.date}</Text>
          </View>
          <CalendarIcon />
        </View>
      </Animated.View>
    </View>
  );
}

type ControlsProps = {
  list: number[];
  sessions: Session[];
  width: number;
  insets: { top: number; bottom: number };
  accent: string;
  open: SharedValue<number>;
  page: SharedValue<number>;
  dragY: SharedValue<number>;
  onClose: () => void;
  onPick: (k: number) => void;
  onStart: (k: number) => void;
};

/** What stays put while the heroes page: close, the avatar, Start and the thumbnail strip. */
function HeroControls({ list, sessions, width, insets, accent, open, page, dragY, onClose, onPick, onStart }: ControlsProps) {
  const fade = (order: number) => () => {
    'worklet';
    const shown = reveal(open.get(), order);
    return {
      opacity: shown * (1 - clamp01(dragY.get() / 140)),
      transform: [{ translateY: (1 - shown) * REVEAL_RISE }],
    };
  };
  const top = useAnimatedStyle(fade(0));
  const start = useAnimatedStyle(fade(4));
  const strip = useAnimatedStyle(fade(5));
  const inner = width - SIDE * 2;
  const slot = (inner - THUMB_GAP * 3) / 4;
  const rail = useAnimatedStyle(() => {
    // The active thumbnail stays in the second slot until the strip runs out.
    const shift = Math.min(Math.max(0, list.length - 4), Math.max(0, page.get() - 1));
    return { transform: [{ translateX: -shift * (slot + THUMB_GAP) }] };
  });
  return (
    <View pointerEvents="box-none" style={StyleSheet.absoluteFill}>
      <Animated.View style={[styles.topBar, { top: insets.top + 6 }, top]}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Close"
          onPress={onClose}
          hitSlop={8}
          style={({ pressed }) => [styles.roundButton, pressed && styles.pressed]}
        >
          <Svg width={18} height={18} viewBox="0 0 24 24">
            <Path d="M5 9l7 7 7-7" stroke="#ffffff" strokeWidth={2.4} fill="none" strokeLinecap="round" strokeLinejoin="round" />
          </Svg>
        </Pressable>
        <Avatar initials="AK" tone={accent} />
      </Animated.View>

      <Animated.View pointerEvents="box-none" style={[styles.startRow, { bottom: rowBottom(insets.bottom) }, start]}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Start"
          onPress={() => onStart(Math.round(page.get()))}
          style={({ pressed }) => [styles.start, { backgroundColor: accent }, pressed && styles.pressed]}
        >
          <View style={styles.play}>
            <Svg width={12} height={12} viewBox="0 0 24 24">
              <Path d="M7 4.5v15a1 1 0 0 0 1.5.86l12.4-7.5a1 1 0 0 0 0-1.72L8.5 3.64A1 1 0 0 0 7 4.5z" fill={accent} />
            </Svg>
          </View>
          <Text style={styles.startLabel}>Start</Text>
        </Pressable>
      </Animated.View>

      <Animated.View style={[styles.strip, { bottom: stackBottom(insets.bottom), width: inner }, strip]}>
        <Animated.View style={[styles.rail, rail]}>
          {list.map((sessionIndex, k) => (
            <Thumb
              key={sessionIndex}
              index={k}
              uri={photoUrl(sessions[sessionIndex].photo, TILE_PX)}
              label={sessions[sessionIndex].title.join(' ')}
              size={slot}
              page={page}
              onPress={() => onPick(k)}
            />
          ))}
        </Animated.View>
      </Animated.View>
    </View>
  );
}

type ThumbProps = { index: number; uri: string; label: string; size: number; page: SharedValue<number>; onPress: () => void };

/** One thumbnail in the strip: the hero in front is lifted, ringed and fully lit. */
function Thumb({ index, uri, label, size, page, onPress }: ThumbProps) {
  const style = useAnimatedStyle(() => {
    const near = clamp01(1 - Math.abs(index - page.get()));
    return { opacity: 0.55 + 0.45 * near, transform: [{ translateY: -5 * near }] };
  });
  const ring = useAnimatedStyle(() => ({ opacity: clamp01(1 - Math.abs(index - page.get())) }));
  return (
    <Pressable accessibilityRole="button" accessibilityLabel={label} onPress={onPress}>
      <Animated.View style={[styles.thumb, { width: size }, style]}>
        <Image style={StyleSheet.absoluteFill} source={{ uri }} contentFit="cover" cachePolicy="memory-disk" />
        <Animated.View style={[styles.thumbRing, ring]} />
      </Animated.View>
    </Pressable>
  );
}

function Avatar({ initials, tone }: { initials: string; tone: string }) {
  return (
    <View style={[styles.avatar, { backgroundColor: tone }]}>
      <Text style={styles.avatarLabel}>{initials}</Text>
    </View>
  );
}

function ClockIcon() {
  return (
    <Svg width={20} height={20} viewBox="0 0 24 24">
      <Path d="M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zm0 4.5V12l3 2" stroke="#ffffff" strokeWidth={1.7} fill="none" strokeLinecap="round" strokeLinejoin="round" />
    </Svg>
  );
}

function CalendarIcon() {
  return (
    <Svg width={20} height={20} viewBox="0 0 24 24">
      <Path
        d="M5 6.5h14a1.5 1.5 0 0 1 1.5 1.5v11A1.5 1.5 0 0 1 19 20.5H5A1.5 1.5 0 0 1 3.5 19V8A1.5 1.5 0 0 1 5 6.5zM3.5 10.5h17M8 4v4M16 4v4"
        stroke="#ffffff"
        strokeWidth={1.7}
        fill="none"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </Svg>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
  },
  topFade: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
  },
  bottomFade: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
  },
  header: {
    position: 'absolute',
    left: SIDE,
    right: SIDE,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  wordmark: {
    fontFamily: SERIF,
    fontSize: 28,
    fontStyle: 'italic',
    color: INK,
    letterSpacing: -0.3,
  },
  subtitle: {
    marginTop: 2,
    fontSize: 12,
    fontWeight: '500',
    color: MUTED,
    letterSpacing: 0.2,
  },
  avatar: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarLabel: {
    fontSize: 13,
    fontWeight: '700',
    color: INK,
    letterSpacing: 0.4,
  },
  chips: {
    position: 'absolute',
    left: 16,
    right: 16,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  pill: {
    position: 'absolute',
    left: 0,
    top: 0,
    height: 36,
    borderRadius: 18,
  },
  chip: {
    height: 36,
    paddingHorizontal: 14,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
  },
  chipIdle: {
    backgroundColor: 'rgba(29,29,27,0.05)',
  },
  chipLabel: {
    fontSize: 13,
    fontWeight: '600',
  },
  copy: {
    position: 'absolute',
    left: SIDE,
    right: SIDE,
  },
  guideRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginBottom: 12,
  },
  guideDot: {
    width: 26,
    height: 26,
    borderRadius: 13,
    backgroundColor: 'rgba(255,255,255,0.22)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,255,255,0.5)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  guideInitials: {
    fontSize: 10,
    fontWeight: '700',
    color: '#ffffff',
  },
  guideName: {
    fontSize: 14,
    fontWeight: '600',
    color: '#ffffff',
  },
  title: {
    fontFamily: SERIF,
    fontSize: 56,
    lineHeight: 58,
    fontWeight: '700',
    color: '#ffffff',
    letterSpacing: -0.5,
  },
  titleItalic: {
    fontWeight: '400',
    fontStyle: 'italic',
  },
  blurb: {
    marginTop: 12,
    fontSize: 14,
    lineHeight: 20,
    color: 'rgba(255,255,255,0.74)',
    maxWidth: 320,
  },
  metaRow: {
    position: 'absolute',
    left: SIDE,
    right: SIDE,
    height: ROW_H,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  meta: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  metaRight: {
    alignItems: 'flex-end',
  },
  metaLabel: {
    fontSize: 10,
    fontWeight: '500',
    color: 'rgba(255,255,255,0.6)',
    letterSpacing: 0.3,
  },
  metaValue: {
    fontSize: 14,
    fontWeight: '600',
    color: '#ffffff',
    fontVariant: ['tabular-nums'],
  },
  alignRight: {
    textAlign: 'right',
  },
  topBar: {
    position: 'absolute',
    left: SIDE - 4,
    right: SIDE,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  roundButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255,255,255,0.18)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,255,255,0.35)',
  },
  pressed: {
    opacity: 0.7,
    transform: [{ scale: 0.96 }],
  },
  startRow: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: ROW_H,
    alignItems: 'center',
    justifyContent: 'center',
  },
  start: {
    height: ROW_H,
    paddingLeft: 6,
    paddingRight: 22,
    borderRadius: ROW_H / 2,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  play: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: INK,
    alignItems: 'center',
    justifyContent: 'center',
    paddingLeft: 2,
  },
  startLabel: {
    fontSize: 16,
    fontWeight: '700',
    color: INK,
  },
  strip: {
    position: 'absolute',
    left: SIDE,
    height: THUMB_H + 8,
    paddingTop: 6,
    overflow: 'hidden',
  },
  rail: {
    flexDirection: 'row',
    gap: THUMB_GAP,
  },
  thumb: {
    height: THUMB_H,
    borderRadius: 14,
    borderCurve: 'continuous',
    overflow: 'hidden',
    backgroundColor: 'rgba(255,255,255,0.12)',
  },
  thumbRing: {
    position: 'absolute',
    top: 0,
    right: 0,
    bottom: 0,
    left: 0,
    borderRadius: 14,
    borderCurve: 'continuous',
    borderWidth: 2,
    borderColor: '#ffffff',
  },
});
