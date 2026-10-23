import {
  AlphaType,
  Canvas,
  Circle,
  ClipOp,
  ColorType,
  createPicture,
  FilterMode,
  Group,
  MipmapMode,
  Picture,
  type SkCanvas,
  Skia,
  type SkImage,
  type SkPaint,
  TileMode,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { type ReactNode, useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, type StyleProp, StyleSheet, Text, View, type ViewStyle } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { type MarqueePhoto, photoUrl, ROWS } from './VelocityMarqueePhotos';

/** One onboarding page: a short headline and the line under it. */
export type OnboardingPage = { title: string; body: string };

type Props = {
  /** The rows, top to bottom; the middle one runs the other way. Ten to fourteen photos a row. */
  rows?: MarqueePhoto[][];
  /** The pages, swiped through left and right; the dots under the rows follow them. */
  pages?: OnboardingPage[];
  /** The app's name, next to its mark at the top. */
  name?: string;
  /** Rests on each page, then swipes to the next one, throwing the rows, and starts over. */
  autoPlay?: boolean;
  onGetStarted?: () => void;
  onSignIn?: () => void;
  /** Called with a page's index when a swipe lands on it. */
  onPage?: (index: number) => void;
  /** The paper. */
  background?: string;
  ink?: string;
};

const PAGES: OnboardingPage[] = [
  { title: 'Photos that keep moving', body: 'Afterimage strings your camera roll into reels that drift by on their own.' },
  { title: 'Flick a reel to travel back', body: 'Throw it to race through a year of days, then let it ease to a stop.' },
  { title: 'Share the whole reel', body: 'Send a moving set of frames to the people who were there with you.' },
];

const IOS = process.env.EXPO_OS === 'ios';
const TEXT_FAMILY = IOS ? 'Avenir Next' : 'sans-serif';
// A tile before its photo arrives.
const TILE_PAPER = '#e3ded3';

// ─── Page ────────────────────────────────────────────────────────────────────────────────────
// The side margin (pt); the brand line sits HEADER_TOP below the safe top and is HEADER_H tall.
// The rows fill the space between it and the text, HERO_GAP clear of each.
const MARGIN = 24;
const MIN_TOP = 20;
const HEADER_TOP = 6;
const HEADER_H = 26;
const HERO_GAP = 18;
// The text block sits FOOT_GAP above the safe bottom.
const FOOT_GAP = 4;
// The paper's grain: a GRAIN_SIZE² tile of seeded noise, each texel GRAIN_TEXEL pt, with dark and
// light specks up to these opacities. Drawn once.
const GRAIN_SIZE = 128;
const GRAIN_TEXEL = 0.5;
const GRAIN_DARK = 0.06;
const GRAIN_LIGHT = 0.07;
const GRAIN_SEED = 20261112;

// ─── Rows ────────────────────────────────────────────────────────────────────────────────────
// The rows lie at TILT degrees, rising to the right, ROW_PITCH pt apart centre to centre. On a
// screen too short for them, the three rows shrink together (never below MIN_HERO_SCALE).
const TILT = -8;
const ROW_H = 96;
const ROW_PITCH = 112;
const MIN_HERO_SCALE = 0.72;
const MAX_HERO_SCALE = 1.2;
// Tile widths by orientation, the gap between tiles and their corner radius, in pt.
const PORTRAIT_W = 72;
const LANDSCAPE_W = 134;
const TILE_GAP = 12;
const TILE_RADIUS = 18;
// Photos are fetched at about three times their drawn width.
const PORTRAIT_PX = 240;
const LANDSCAPE_PX = 420;
// Each photo fades in over this many seconds once it has loaded.
const PHOTO_FADE = 0.45;

// ─── Speed ───────────────────────────────────────────────────────────────────────────────────
// Rows drift at DRIFT pt/s (row one leftwards, the way the pages go); a drag moves them with the
// finger and a release throws them at the finger's speed, which eases back to the drift on a
// critically damped spring of natural frequency OMEGA (1/s): v(t) = v0 (1 + ωt) e^(−ωt).
const DRIFT = 40;
const OMEGA = 3.2;
const THROW_SPRING = { mass: 1, stiffness: OMEGA * OMEGA, damping: 2 * OMEGA };
const MAX_THROW = 6000;
// A vertical drag moves the rows too, at this share, so a drag in any direction throws them.
const VERTICAL_SHARE = 0.4;
// The finger's speed while dragging is smoothed over about this many seconds.
const DRAG_SMOOTH = 0.06;

// The lean follows the speed on an underdamped spring, so tiles overshoot a little and wobble
// upright when the rows slow down. Skew is LEAN_PER degrees per pt/s, up to LEAN_MAX; tiles
// stretch along the row by STRETCH_PER per pt/s, up to STRETCH_MAX, and thin a third as much.
const LEAN_STIFFNESS = 140;
const LEAN_DAMPING = 11.8;
const LEAN_PER = 0.0055;
const LEAN_MAX = 16;
const STRETCH_PER = 0.00004;
const STRETCH_MAX = 0.12;

// ─── Pages ───────────────────────────────────────────────────────────────────────────────────
// A release that has moved PAGE_DISTANCE of the width sideways, or is going faster than
// PAGE_SPEED pt/s sideways, turns the page. The page settles on a critically damped spring.
const PAGE_DISTANCE = 0.18;
const PAGE_SPEED = 450;
const PAGE_SPRING = { duration: 560, dampingRatio: 1 };
// The text of a page slides TEXT_SHIFT pt with the page and is gone by TEXT_GONE of a page away,
// so the old line has left before the new one arrives.
const TEXT_SHIFT = 28;
const TEXT_GONE = 0.46;
// The page dots: DOT pt round, ACTIVE_W wide for the current page, DOT_GAP apart.
const DOT = 6;
const ACTIVE_W = 20;
const DOT_GAP = 6;
// Every page's text sits in one box as tall as the tallest page; until they are measured, the
// box allows this many lines.
const TITLE_SIZE = 30;
const TITLE_LINE = 35;
const TITLE_LINES = 2;
const BODY_SIZE = 16;
const BODY_LINE = 22;
const BODY_LINES = 3;
// A press on the button squeezes it on a spring (stiffness, damping) and lets it spring back.
const PRESS_SCALE = 0.97;
const PRESS_SPRING = { mass: 1, stiffness: 420, damping: 26 };

// ─── Entrance ────────────────────────────────────────────────────────────────────────────────
// Once laid out, the rows fade in over HERO_FADE ms while rising HERO_RISE pt, and the dots, the
// text, the button and the sign-in line follow, each STAGGER ms after the last.
const HERO_FADE = 900;
const HERO_RISE = 14;
const ENTER_DELAY = 220;
const STAGGER = 90;
const ENTER_FADE = 600;
const ENTER_RISE = 10;
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);

// ─── autoPlay ────────────────────────────────────────────────────────────────────────────────
// Loop (seconds): the page rests, then a swipe to the next page at each SWIPES time: a PUSH s
// push of the rows to FLING pt/s (leftwards, as a finger swiping to the next page throws them),
// released into the same spring as a hand's throw, the page turning on release. Three swipes
// carry the pages round to where they started. The fling's peak is set a little either side of
// FLING so the rows travel a whole number of row lengths per loop and close it seamlessly.
const LOOP = 15;
const SWIPES = [3.2, 8.2, 13.2];
const FLING = 1400;
const PUSH = 0.28;
const PAGE_TURN = 0.6;
// After a touch the loop waits this long, then starts over from where things are.
const USER_HOLD = 3;

function clamp(x: number, lo: number, hi: number) {
  'worklet';
  return Math.min(hi, Math.max(lo, x));
}

/** x mod m, always in [0, m). */
function wrap(x: number, m: number) {
  'worklet';
  return ((x % m) + m) % m;
}

/** How far page `index` is from the place `p`, the short way round `count` pages. */
function pageDistance(p: number, index: number, count: number) {
  'worklet';
  return wrap(p - index + count / 2, count) - count / 2;
}

/** A fling's speed over time, as a share of its peak: a smooth push, then the release spring. */
function flingShape(s: number) {
  'worklet';
  if (s <= 0) return 0;
  if (s <= PUSH) {
    const u = s / PUSH;
    return u * u * (3 - 2 * u);
  }
  const u = s - PUSH;
  return (1 + OMEGA * u) * Math.exp(-OMEGA * u);
}

/** How far a fling of peak 1 has carried the rows after s seconds (the integral of flingShape). */
function flingArea(s: number) {
  'worklet';
  if (s <= 0) return 0;
  if (s <= PUSH) {
    const u = s / PUSH;
    return PUSH * (u * u * u - (u * u * u * u) / 2);
  }
  const u = s - PUSH;
  return PUSH / 2 + 2 / OMEGA - Math.exp(-OMEGA * u) * (2 / OMEGA + u);
}

/** A strong ease-out, for the page turning in the script. */
function easeOut(u: number) {
  'worklet';
  const v = clamp(u, 0, 1);
  return 1 - (1 - v) * (1 - v) * (1 - v);
}

/** Where the scripted loop's page has got to by time t: one more page per swipe. */
function scriptedPage(t: number) {
  'worklet';
  let p = 0;
  for (let k = 0; k < SWIPES.length; k += 1) p += easeOut((t - SWIPES[k] - PUSH) / PAGE_TURN);
  return p;
}

type Tile = { offset: number; width: number; index: number };
type Row = { tiles: Tile[]; length: number; dir: number };

function tileWidth(photo: MarqueePhoto) {
  return photo.shape === 'portrait' ? PORTRAIT_W : LANDSCAPE_W;
}

/** Each row's tiles laid end to end, and the length after which the row repeats. */
function makeRows(rows: MarqueePhoto[][]) {
  let index = 0;
  const made: Row[] = [];
  rows.forEach((photos) => {
    let x = 0;
    const tiles = photos.map((photo) => {
      const tile = { offset: x, width: tileWidth(photo), index };
      x += tile.width + TILE_GAP;
      index += 1;
      return tile;
    });
    if (tiles.length > 0) made.push({ tiles, length: x, dir: made.length % 2 === 0 ? 1 : -1 });
  });
  return made;
}

/** Seeded noise (mulberry32), the same numbers in all three ports, so the grain matches. */
function grainPixels(size: number, seed: number) {
  let s = seed >>> 0;
  const next = () => {
    s = (s + 0x6d2b79f5) >>> 0;
    let t = Math.imul(s ^ (s >>> 15), 1 | s);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
  // Premultiplied RGBA: dark specks are black, light specks white, each with its own strength.
  const bytes = new Uint8Array(size * size * 4);
  for (let i = 0; i < size * size; i += 1) {
    const n = next();
    const dark = n < 0.5;
    const a = Math.round((dark ? (0.5 - n) * 2 * GRAIN_DARK : (n - 0.5) * 2 * GRAIN_LIGHT) * 255);
    const c = dark ? 0 : a;
    bytes[i * 4] = c;
    bytes[i * 4 + 1] = c;
    bytes[i * 4 + 2] = c;
    bytes[i * 4 + 3] = a;
  }
  return bytes;
}

/** The part of a photo that covers a w × h box, centred. */
function coverRect(image: SkImage, w: number, h: number) {
  'worklet';
  const iw = Math.max(1, image.width());
  const ih = Math.max(1, image.height());
  const scale = Math.max(w / iw, h / ih);
  const sw = w / scale;
  const sh = h / scale;
  return Skia.XYWHRect((iw - sw) / 2, (ih - sh) / 2, sw, sh);
}

type Loaded = { image: SkImage; at: number } | null;

/** One photo centred on the origin, clipped to a rounded box: paper until it arrives, then fading in. */
function drawTile(canvas: SkCanvas, paint: SkPaint, loaded: Loaded, now: number, w: number, h: number) {
  'worklet';
  const box = Skia.XYWHRect(-w / 2, -h / 2, w, h);
  canvas.save();
  canvas.clipRRect(Skia.RRectXY(box, TILE_RADIUS, TILE_RADIUS), ClipOp.Intersect, true);
  const shown = loaded ? clamp((now - loaded.at) / PHOTO_FADE, 0, 1) : 0;
  if (shown < 1) {
    paint.setColor(Skia.Color(TILE_PAPER));
    canvas.drawRect(box, paint);
  }
  if (loaded && shown > 0) {
    paint.setColor(Skia.Color('#000000'));
    paint.setAlphaf(shown);
    canvas.drawImageRectOptions(loaded.image, coverRect(loaded.image, w, h), box, FilterMode.Linear, MipmapMode.Linear, paint);
    paint.setAlphaf(1);
  }
  canvas.restore();
}

/** The paper, drawn once: a flat colour and a fine grain. */
function drawPaper(canvas: SkCanvas, width: number, height: number, paper: string, grain: SkImage | null) {
  const paint = Skia.Paint();
  paint.setColor(Skia.Color(paper));
  canvas.drawRect(Skia.XYWHRect(0, 0, width, height), paint);
  if (grain) {
    const speck = Skia.Paint();
    speck.setShader(
      grain.makeShaderOptions(TileMode.Repeat, TileMode.Repeat, FilterMode.Nearest, MipmapMode.None, Skia.Matrix().scale(GRAIN_TEXEL, GRAIN_TEXEL)),
    );
    canvas.drawRect(Skia.XYWHRect(0, 0, width, height), speck);
  }
}

const haptic = {
  page: () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  },
  press: () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  },
};

/** The app's mark: a solid disc linked with a ring, the ring cut where it crosses the disc. */
function Mark({ ink, paper }: { ink: string; paper: string }) {
  const disc = useMemo(() => Skia.RRectXY(Skia.XYWHRect(1, 1, 18, 18), 9, 9), []);
  return (
    <Canvas style={styles.mark}>
      <Circle cx={10} cy={10} r={9} color={ink} />
      <Circle cx={19} cy={10} r={7.5} style="stroke" strokeWidth={2} color={ink} />
      <Group clip={disc}>
        <Circle cx={19} cy={10} r={7.5} style="stroke" strokeWidth={2} color={paper} />
      </Group>
    </Canvas>
  );
}

/** Fades and rises in once `on`, `delay` ms later. */
function Enter({ on, delay, style, children }: { on: boolean; delay: number; style?: StyleProp<ViewStyle>; children: ReactNode }) {
  const reducedMotion = useReducedMotion();
  const v = useSharedValue(0);
  useEffect(() => {
    if (on) v.set(withDelay(delay, withTiming(1, { duration: ENTER_FADE, easing: EASE_OUT })));
  }, [on, delay, v]);
  const animated = useAnimatedStyle(() => ({
    opacity: v.get(),
    transform: [{ translateY: reducedMotion ? 0 : (1 - v.get()) * ENTER_RISE }],
  }));
  return (
    <Animated.View pointerEvents="box-none" style={[style, animated]}>
      {children}
    </Animated.View>
  );
}

/** One page dot: wide and dark for the page shown, a small faint dot for the others. */
function Dot({ index, count, page, ink }: { index: number; count: number; page: SharedValue<number>; ink: string }) {
  const animated = useAnimatedStyle(() => {
    const p = page.get();
    let left = 0;
    let width = DOT;
    for (let k = 0; k <= index; k += 1) {
      const near = clamp(1 - Math.abs(pageDistance(p, k, count)), 0, 1);
      width = DOT + (ACTIVE_W - DOT) * near;
      if (k < index) left += width + DOT_GAP;
    }
    const near = clamp(1 - Math.abs(pageDistance(p, index, count)), 0, 1);
    return { left, width, opacity: 0.2 + 0.8 * near };
  });
  return <Animated.View style={[styles.dot, { backgroundColor: ink }, animated]} />;
}

/** One page's headline and line, sliding with the page and gone before the next one arrives. */
function PageText({
  item,
  index,
  count,
  page,
  ink,
  onHeight,
}: {
  item: OnboardingPage;
  index: number;
  count: number;
  page: SharedValue<number>;
  ink: string;
  onHeight: (index: number, height: number) => void;
}) {
  const reducedMotion = useReducedMotion();
  const animated = useAnimatedStyle(() => {
    const d = pageDistance(page.get(), index, count);
    return {
      opacity: clamp(1 - Math.abs(d) / TEXT_GONE, 0, 1),
      transform: [{ translateX: reducedMotion ? 0 : -d * TEXT_SHIFT }],
    };
  });
  return (
    <Animated.View
      style={[styles.pageText, animated]}
      pointerEvents="none"
      onLayout={(event: LayoutChangeEvent) => onHeight(index, event.nativeEvent.layout.height)}
    >
      <Text style={[styles.title, { color: ink }]} numberOfLines={TITLE_LINES} maxFontSizeMultiplier={1.15}>
        {item.title}
      </Text>
      <Text style={[styles.body, { color: ink }]} numberOfLines={BODY_LINES} maxFontSizeMultiplier={1.15}>
        {item.body}
      </Text>
    </Animated.View>
  );
}

/**
 * An onboarding screen whose hero is three tilted rows of photo tiles running as endless
 * marquees, the middle one the other way. Swiping through the pages throws the rows with the
 * finger's speed: they race, the tiles lean into the motion and stretch, then ease back to the
 * idle drift while the headline under them changes. A "Get started" button and a sign-in line
 * sit at the foot.
 */
export function VelocityMarquee({
  rows = ROWS,
  pages = PAGES,
  name = 'Afterimage',
  autoPlay = false,
  onGetStarted,
  onSignIn,
  onPage,
  background = '#f2efe8',
  ink = '#161512',
}: Props) {
  const insets = useSafeAreaInsets();
  const top = Math.max(insets.top, MIN_TOP);
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const count = Math.max(1, pages.length);
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  // Where the text block starts; the rows fill the space above it.
  const [textTop, setTextTop] = useState<number | null>(null);
  // Each page's text height as laid out, so the box fits the tallest page and no more.
  const [heights, setHeights] = useState<Record<number, number>>({});
  const measured = pages.every((_, i) => (heights[i] ?? 0) > 0);
  const textHeight = measured ? Math.max(...pages.map((_, i) => heights[i] ?? 0)) : null;
  const onHeight = (index: number, height: number) =>
    setHeights((prev) => (prev[index] === height ? prev : { ...prev, [index]: height }));
  const layout = useMemo(() => makeRows(rows), [rows]);
  // Rows of different lengths travel at speeds in proportion, so all of them repeat together.
  const reference = useMemo(
    () => Math.max(1, layout.reduce((sum, row) => sum + row.length, 0) / Math.max(1, layout.length)),
    [layout],
  );
  // The script's fling: the peak nearest FLING that, with the drift, carries the rows a whole
  // number of row lengths per loop. Each swipe also counts the tail of its own fling from the
  // loop before, so the speed is the same either side of the seam.
  const fling = useMemo(() => {
    const area = SWIPES.reduce((sum, at) => sum + flingArea(2 * LOOP - at), 0);
    const laps = Math.max(1, Math.round((DRIFT * LOOP + FLING * area) / reference));
    return Math.max(0, (laps * reference - DRIFT * LOOP) / Math.max(0.001, area));
  }, [reference]);
  const grain = useMemo(() => {
    const data = Skia.Data.fromBytes(grainPixels(GRAIN_SIZE, GRAIN_SEED));
    return Skia.Image.MakeImage(
      { width: GRAIN_SIZE, height: GRAIN_SIZE, colorType: ColorType.RGBA_8888, alphaType: AlphaType.Premul },
      data,
      GRAIN_SIZE * 4,
    );
  }, []);
  // The paper is recorded once per size and drawn by a canvas of its own that never redraws.
  const paper = useMemo(() => {
    if (!size) return null;
    return createPicture((canvas) => drawPaper(canvas, size.width, size.height, background, grain), {
      width: size.width,
      height: size.height,
    });
  }, [size, background, grain]);
  const ready = size !== null && textTop !== null && textHeight !== null;

  const stage = useSharedValue({ width: 0, height: 0, heroTop: 0, heroBottom: 0 });
  // Distance travelled, in pt of the reference row (row one moves with it), and the speed above
  // the drift (pt/s). The drift and every swipe to the next page run leftwards: negative.
  const travel = useSharedValue(0);
  const boost = useSharedValue(0);
  // The lean spring's position and speed, following `boost`.
  const lean = useSharedValue(0);
  const leanSpeed = useSharedValue(0);
  const dragging = useSharedValue(false);
  const elapsed = useSharedValue(0);
  const loaded = useSharedValue<Loaded[]>([]);
  // The page as a place: 1.5 is halfway from the second page to the third. The script runs it
  // round past the last page, so it is read the short way round.
  const page = useSharedValue(0);
  // autoPlay: the loop's clock, the travel and the page it started from, and the pause after a touch.
  const clock = useSharedValue(0);
  const from = useSharedValue(0);
  const pageFrom = useSharedValue(0);
  const looping = useSharedValue(auto);
  const hold = useSharedValue(0);
  const press = useSharedValue(1);
  const signInPress = useSharedValue(1);
  const hero = useSharedValue(0);

  useEffect(() => {
    looping.set(auto);
    clock.set(0);
    from.set(travel.get());
    pageFrom.set(Math.round(wrap(page.get(), count)) % count);
    hold.set(0);
  }, [auto, count, clock, from, hold, looping, page, pageFrom, travel]);

  useEffect(() => {
    if (!size || textTop === null) return;
    const heroTop = top + HEADER_TOP + HEADER_H + HERO_GAP;
    stage.set({ width: size.width, height: size.height, heroTop, heroBottom: Math.max(heroTop + 1, textTop - HERO_GAP) });
  }, [size, textTop, top, stage]);

  // The rows rise and fade in once everything has a place.
  useEffect(() => {
    if (ready) hero.set(withTiming(1, { duration: HERO_FADE, easing: EASE_OUT }));
  }, [ready, hero]);

  // The photos are drawn by Skia, each fading in as it arrives; a photo that fails stays paper.
  useEffect(() => {
    let alive = true;
    const photos = rows.flat();
    loaded.set(photos.map(() => null));
    photos.forEach((photo, i) => {
      Skia.Data.fromURI(photoUrl(photo.id, photo.shape === 'portrait' ? PORTRAIT_PX : LANDSCAPE_PX))
        .then((data) => Skia.Image.MakeImageFromEncoded(data))
        .then((image) => {
          if (!alive || !image) return;
          loaded.modify((list) => {
            'worklet';
            if (i >= list.length) return list;
            const next = list.slice();
            next[i] = { image, at: elapsed.get() };
            return next;
          });
        })
        .catch(() => {});
    });
    return () => {
      alive = false;
    };
  }, [rows, loaded, elapsed]);

  const landed = (index: number) => onPage?.(index);

  /** Where the scripted loop has carried the rows by time t, and how fast above the drift. */
  const scripted = (t: number) => {
    'worklet';
    let moved = -DRIFT * t;
    let speed = 0;
    for (let k = 0; k < SWIPES.length; k += 1) {
      const at = SWIPES[k];
      moved -= fling * (flingArea(t - at) + flingArea(t + LOOP - at) - flingArea(LOOP - at));
      speed -= fling * (flingShape(t - at) + flingShape(t + LOOP - at));
    }
    return { travel: moved, boost: speed };
  };

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    if (!(dt > 0)) return;
    elapsed.set(elapsed.get() + dt);
    const drift = reducedMotion ? 0 : -DRIFT;
    if (hold.get() > 0 && !dragging.get()) {
      hold.set(hold.get() - dt);
      if (hold.get() <= 0) {
        // The loop starts over from wherever the hand left the rows and the page.
        clock.set(0);
        from.set(travel.get());
        pageFrom.set(Math.round(wrap(page.get(), count)) % count);
      }
    }
    if (looping.get() && hold.get() <= 0 && !dragging.get()) {
      const t = clock.get() + dt;
      if (t >= LOOP) {
        // One loop carries every row a whole number of lengths and the page round to where it
        // started, so this is the same picture as t = 0.
        from.set(from.get() + scripted(LOOP).travel);
        clock.set(t - LOOP);
      } else {
        clock.set(t);
      }
      const now = scripted(clock.get());
      travel.set(from.get() + now.travel);
      boost.set(now.boost);
      page.set(wrap(pageFrom.get() + scriptedPage(clock.get()), count));
    } else if (!dragging.get()) {
      travel.set(travel.get() + (drift + boost.get()) * dt);
    } else {
      // The finger moves the rows itself (see the pan); the drift carries on under it.
      travel.set(travel.get() + drift * dt);
    }
    // Everything repeats every reference length, so the distance is kept small without a seam.
    const laps = Math.trunc(travel.get() / reference / 64) * 64;
    if (laps !== 0) {
      travel.set(travel.get() - laps * reference);
      from.set(from.get() - laps * reference);
    }
    if (!Number.isFinite(travel.get())) travel.set(0);
    if (!Number.isFinite(boost.get())) boost.set(0);
    if (!Number.isFinite(page.get())) page.set(0);
    // The lean chases the speed on its spring; under reduced motion nothing leans.
    const target = reducedMotion ? 0 : boost.get();
    const accel = LEAN_STIFFNESS * (target - lean.get()) - LEAN_DAMPING * leanSpeed.get();
    leanSpeed.set(leanSpeed.get() + accel * dt);
    lean.set(lean.get() + leanSpeed.get() * dt);
    if (!Number.isFinite(lean.get()) || !Number.isFinite(leanSpeed.get())) {
      lean.set(0);
      leanSpeed.set(0);
    }
  });

  /** Turns to the page `step` away from the one shown, if there is one, with a tick. */
  const turn = (step: number) => {
    'worklet';
    // Read the short way round, so a page caught mid-way back to the first settles without
    // passing through the others.
    const p = wrap(page.get() + 0.5, count) - 0.5;
    const current = clamp(Math.round(p), 0, count - 1);
    const next = clamp(current + step, 0, count - 1);
    page.set(p);
    page.set(reducedMotion ? withTiming(next, { duration: 320 }) : withSpring(next, PAGE_SPRING));
    if (next !== current) {
      scheduleOnRN(haptic.page);
      scheduleOnRN(landed, next);
    }
  };

  const pan = Gesture.Pan()
    .minDistance(2)
    .onBegin(() => {
      // Catching the rows stops the throw under the finger; the lean relaxes on its spring.
      dragging.set(true);
      hold.set(USER_HOLD);
      boost.set(0);
    })
    .onChange((event) => {
      const along = event.changeX - event.changeY * VERTICAL_SHARE;
      if (!Number.isFinite(along)) return;
      // Each row moves by the finger in its own direction; travel is measured on row one.
      travel.set(travel.get() + along);
      const speed = event.velocityX - event.velocityY * VERTICAL_SHARE;
      if (!Number.isFinite(speed)) return;
      const k = clamp(1 / 60 / DRAG_SMOOTH, 0, 1);
      boost.set(boost.get() + (clamp(speed, -MAX_THROW, MAX_THROW) - boost.get()) * k);
    })
    .onEnd((event) => {
      const raw = event.velocityX - event.velocityY * VERTICAL_SHARE;
      const speed = Number.isFinite(raw) ? clamp(raw, -MAX_THROW, MAX_THROW) : 0;
      // The throw carries the finger's speed and settles back to the drift on the spring.
      boost.set(reducedMotion ? 0 : speed);
      if (!reducedMotion) boost.set(withSpring(0, THROW_SPRING));
      // A sideways swipe turns the page: leftwards to the next one.
      const dx = Number.isFinite(event.translationX) ? event.translationX : 0;
      const vx = Number.isFinite(event.velocityX) ? event.velocityX : 0;
      const w = Math.max(1, stage.get().width);
      const sideways = Math.abs(dx) > Math.abs(event.translationY ?? 0);
      if (sideways && (dx < -w * PAGE_DISTANCE || vx < -PAGE_SPEED)) turn(1);
      else if (sideways && (dx > w * PAGE_DISTANCE || vx > PAGE_SPEED)) turn(-1);
    })
    .onFinalize(() => {
      dragging.set(false);
      hold.set(USER_HOLD);
    });

  // The button squeezes on touch down and springs back on release, with a light tap.
  const go = () => onGetStarted?.();
  const signIn = () => onSignIn?.();
  const buttonTap = Gesture.Tap()
    .maxDuration(60000)
    .maxDistance(40)
    .onBegin(() => {
      press.set(withSpring(PRESS_SCALE, PRESS_SPRING));
      scheduleOnRN(haptic.press);
    })
    .onEnd((_event, success) => {
      if (success) scheduleOnRN(go);
    })
    .onFinalize(() => {
      press.set(withSpring(1, PRESS_SPRING));
    });
  const signInTap = Gesture.Tap()
    .maxDuration(60000)
    .maxDistance(30)
    .onBegin(() => {
      signInPress.set(withTiming(0.45, { duration: 100 }));
    })
    .onEnd((_event, success) => {
      if (success) scheduleOnRN(signIn);
    })
    .onFinalize(() => {
      signInPress.set(withTiming(1, { duration: 200 }));
    });
  const buttonStyle = useAnimatedStyle(() => ({ transform: [{ scale: press.get() }] }));
  const signInStyle = useAnimatedStyle(() => ({ opacity: signInPress.get() }));
  const heroStyle = useAnimatedStyle(() => ({
    opacity: hero.get(),
    transform: [{ translateY: reducedMotion ? 0 : (1 - hero.get()) * HERO_RISE }],
  }));

  const picture = useDerivedValue(() => {
    const { width, height, heroTop, heroBottom } = stage.get();
    return createPicture(
      (canvas) => {
        if (width === 0 || layout.length === 0) return;
        const paint = Skia.Paint();
        paint.setAntiAlias(true);
        const x = travel.get();
        const now = elapsed.get();
        const images = loaded.get();
        const l = lean.get();
        const angle = clamp(l * LEAN_PER, -LEAN_MAX, LEAN_MAX);
        const skew = Math.tan((angle * Math.PI) / 180);

        // The rows fill the hero, growing or shrinking together with the screen; the tilt lifts the right
        // end of each row and drops the left, so that is counted in the height they need.
        const rise = width * Math.tan((Math.abs(TILT) * Math.PI) / 180);
        const need = (layout.length - 1) * ROW_PITCH + ROW_H + rise;
        const scale = clamp((heroBottom - heroTop) / need, MIN_HERO_SCALE, MAX_HERO_SCALE);
        const midY = (heroTop + heroBottom) / 2;
        const span = width / 2 / scale + LANDSCAPE_W;
        const stretch = 1 + Math.min(STRETCH_MAX, Math.abs(l) * STRETCH_PER);
        const thin = 1 - (stretch - 1) / 3;
        canvas.save();
        canvas.translate(width / 2, midY);
        canvas.scale(scale, scale);
        for (let r = 0; r < layout.length; r += 1) {
          const row = layout[r];
          const moved = row.dir * x * (row.length / reference);
          canvas.save();
          canvas.translate(0, (r - (layout.length - 1) / 2) * ROW_PITCH);
          canvas.rotate(TILT, 0, 0);
          for (const tile of row.tiles) {
            const c = wrap(tile.offset + tile.width / 2 + moved + row.length / 2, row.length) - row.length / 2;
            if (Math.abs(c) - tile.width / 2 > span) continue;
            canvas.save();
            canvas.translate(c, 0);
            // A row moving right leans its tops to the right, like type set in italic.
            canvas.skew(-row.dir * skew, 0);
            canvas.scale(stretch, thin);
            drawTile(canvas, paint, images[tile.index] ?? null, now, tile.width, ROW_H);
            canvas.restore();
          }
          canvas.restore();
        }
        canvas.restore();
      },
      { width, height },
    );
  });

  return (
    <View
      style={[styles.screen, { backgroundColor: background }]}
      onLayout={(event: LayoutChangeEvent) => {
        const box = event.nativeEvent.layout;
        setSize({ width: box.width, height: box.height });
      }}
    >
      {paper && (
        <Canvas style={StyleSheet.absoluteFill}>
          <Picture picture={paper} />
        </Canvas>
      )}
      <GestureDetector gesture={pan}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel={`${name} introduction, ${count} pages. Swipe left or right to turn the page.`}
          accessibilityActions={[
            { name: 'increment', label: 'Next page' },
            { name: 'decrement', label: 'Previous page' },
          ]}
          onAccessibilityAction={(event) => scheduleOnUI(turn, event.nativeEvent.actionName === 'increment' ? 1 : -1)}
        >
          {ready && (
            <Animated.View style={[StyleSheet.absoluteFill, heroStyle]} pointerEvents="none">
              <Canvas style={StyleSheet.absoluteFill}>
                <Picture picture={picture} />
              </Canvas>
            </Animated.View>
          )}
        </View>
      </GestureDetector>

      <Enter on={ready} delay={0} style={[styles.header, { top: top + HEADER_TOP }]}>
        <View style={styles.brand} pointerEvents="none">
          <Mark ink={ink} paper={background} />
          <Text style={[styles.wordmark, { color: ink }]}>{name}</Text>
        </View>
      </Enter>

      <View
        pointerEvents="box-none"
        style={[styles.foot, { bottom: insets.bottom + FOOT_GAP }]}
        onLayout={(event: LayoutChangeEvent) => setTextTop(event.nativeEvent.layout.y)}
      >
        <Enter on={ready} delay={ENTER_DELAY} style={styles.dots}>
          {pages.map((item, i) => (
            <Dot key={i} index={i} count={count} page={page} ink={ink} />
          ))}
        </Enter>
        <Enter on={ready} delay={ENTER_DELAY + STAGGER} style={[styles.pages, textHeight !== null && { height: textHeight }]}>
          {pages.map((item, i) => (
            <PageText key={i} item={item} index={i} count={count} page={page} ink={ink} onHeight={onHeight} />
          ))}
        </Enter>
        <Enter on={ready} delay={ENTER_DELAY + STAGGER * 2}>
          <GestureDetector gesture={buttonTap}>
            <Animated.View
              style={[styles.button, { backgroundColor: ink }, buttonStyle]}
              accessible
              accessibilityRole="button"
              accessibilityLabel="Get started"
            >
              <Text style={[styles.buttonText, { color: background }]}>Get started</Text>
            </Animated.View>
          </GestureDetector>
        </Enter>
        <Enter on={ready} delay={ENTER_DELAY + STAGGER * 3} style={styles.signInRow}>
          <GestureDetector gesture={signInTap}>
            <Animated.View style={[styles.signIn, signInStyle]} accessible accessibilityRole="button" accessibilityLabel="Sign in">
              <Text style={[styles.signInText, styles.signInMuted, { color: ink }]}>Already have an account?</Text>
              <Text style={[styles.signInText, styles.signInStrong, { color: ink }]}>Sign in</Text>
            </Animated.View>
          </GestureDetector>
        </Enter>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
  },
  header: {
    position: 'absolute',
    left: MARGIN,
    right: MARGIN,
    height: HEADER_H,
    alignItems: 'center',
    justifyContent: 'center',
  },
  brand: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  mark: {
    width: 28,
    height: 20,
  },
  wordmark: {
    fontFamily: TEXT_FAMILY,
    fontSize: 19,
    lineHeight: 24,
    fontWeight: '600',
    letterSpacing: -0.2,
    includeFontPadding: false,
  },
  foot: {
    position: 'absolute',
    left: MARGIN,
    right: MARGIN,
  },
  dots: {
    height: DOT,
    marginBottom: 22,
  },
  dot: {
    position: 'absolute',
    top: 0,
    height: DOT,
    borderRadius: DOT / 2,
  },
  pages: {
    height: TITLE_LINE * TITLE_LINES + 10 + BODY_LINE * BODY_LINES,
    marginBottom: 32,
  },
  pageText: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    gap: 10,
  },
  title: {
    fontFamily: TEXT_FAMILY,
    fontSize: TITLE_SIZE,
    lineHeight: TITLE_LINE,
    fontWeight: '600',
    letterSpacing: -0.4,
    includeFontPadding: false,
  },
  body: {
    fontFamily: TEXT_FAMILY,
    fontSize: BODY_SIZE,
    lineHeight: BODY_LINE,
    fontWeight: '400',
    opacity: 0.6,
    includeFontPadding: false,
  },
  button: {
    height: 56,
    borderRadius: 28,
    alignItems: 'center',
    justifyContent: 'center',
  },
  buttonText: {
    fontFamily: TEXT_FAMILY,
    fontSize: 17,
    fontWeight: '600',
    letterSpacing: 0.1,
  },
  signInRow: {
    alignItems: 'center',
    marginTop: 4,
  },
  signIn: {
    height: 44,
    paddingHorizontal: 12,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
  },
  signInText: {
    fontFamily: TEXT_FAMILY,
    fontSize: 15,
  },
  signInMuted: {
    fontWeight: '400',
    opacity: 0.55,
  },
  signInStrong: {
    fontWeight: '600',
  },
});
