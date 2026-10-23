import { Canvas, Group, Image as SkiaImage, Path, Rect, Skia, type SkImage } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  interpolateColor,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import {
  DISPLAY,
  DISPLAY_WEIGHT,
  HERO_PLACEHOLDER,
  INK,
  MuseumPage,
  PAGE_IN_TIME,
  PAPER,
  pageLayout,
  photo,
  SANS,
  SECTIONS,
  type Section,
  useSectionImages,
} from './EditorialMenuPages';

type Props = {
  /** Opens the menu, glides down the words, opens a page from it and comes back, on a loop. */
  autoPlay?: boolean;
  sections?: Section[];
  /** Called with a section's word when it is chosen from the menu. */
  onSelect?: (word: string) => void;
};

// The menu's timeline. Opening plays `open` from 0 to 1 over OPEN_TIME ms, linearly; every part
// takes its own slice of it and eases within that slice, so closing (1 to 0 over CLOSE_TIME ms)
// plays the same choreography backwards: the last letter drops first and the curtain leaves last.
const OPEN_TIME = 1150;
const CLOSE_TIME = 900;
const REDUCED_TIME = 260;
const PANEL: [number, number] = [0, 0.42];
const BURGER: [number, number] = [0, 0.36];
const WORDS_FROM = 0.3;
const WORD_STAGGER = 0.07;
const WORD_SPAN = 0.4;
const LETTER_STAGGER = 0.012;
const LETTER_SPAN = 0.3;
const FOOTER: [number, number] = [0.68, 1];
const EASE_OUT = Easing.bezierFn(0.23, 1, 0.32, 1);
const EASE_IN_OUT = Easing.bezierFn(0.77, 0, 0.175, 1);

// The curtain: its leading edge bows BULGE pt ahead of its sides at mid-travel (sin of its
// progress), flat when it starts and when it lands. The page under it sinks SINK pt, shrinks by
// SINK_SCALE and darkens to SINK_SHADE black.
const BULGE = 110;
const SINK = 34;
const SINK_SCALE = 0.04;
const SINK_SHADE = 0.28;

// The words: WORD_SIZE pt Iowan Old Style, one per ROW pt, each in a box that clips at its own
// baseline so its letters rise out of nothing. The list starts LIST_TOP pt below the safe area.
const WORD_SIZE = 52;
const ROW = 80;
const CLIP = 66;
const LIST_TOP = 112;
const LIST_LEFT = 22;
const NUMBER_WIDTH = 30;
const FOCUS_SHIFT = 14;
const DIMMED = 0.28;
const RULE_Y = CLIP + 3;

// The floating preview: a round photo PREVIEW_W pt across in a BORDER pt ring of paper, sitting
// PREVIEW_DX right of and PREVIEW_DY above the finger, chasing it at LAG (1/s) and leaning with
// its horizontal speed up to LEAN_MAX°. A new word wipes its photo up from the bottom over
// WIPE_TIME ms while it settles from WIPE_ZOOM, with a band of the word's colour on the wipe's edge.
const PREVIEW_W = 168;
const PREVIEW_H = PREVIEW_W;
const PREVIEW_DX = 100;
const PREVIEW_DY = -64;
const BORDER = 3;
const LAG = 7;
const LEAN_PER_SPEED = 0.012;
const LEAN_MAX = 9;
const WIPE_TIME = 460;
const WIPE_ZOOM = 1.14;
const BAND = 5;
const BG_TIME = 420;
const BASE_TINT = '#151310';
const CARD_TONE = '#2a2622';

// Item to page: the card flies to the hero's frame on a spring (stiffness FLIGHT_K, damping
// ratio FLIGHT_ZETA, mass 1), integrated per frame; its corners open from a full circle to the
// hero's square ones on the same progress, and its ring thins away.
const FLIGHT_K = 180;
const FLIGHT_ZETA = 0.9;
const FLIGHT_C = 2 * FLIGHT_ZETA * Math.sqrt(FLIGHT_K);

// autoPlay (seconds): rest, tap the burger, glide down the words and back up, lift on
// Architecture, rest on its page, tap the burger, glide up to Exhibitions, lift, rest. The end
// state equals the start state, so one LOOP loops cleanly.
const LOOP = 16;
const TAPS = [1.0, 10.0];
const GLIDES: [number, number][][] = [
  [
    [2.4, 0],
    [2.9, 0],
    [3.5, 1],
    [4.1, 2],
    [4.7, 3],
    [5.3, 4],
    [6.2, 2],
    [6.8, 2],
  ],
  [
    [11.4, 2],
    [11.9, 2],
    [12.5, 1],
    [13.1, 0],
    [13.6, 0],
  ],
];
const USER_HOLD = 4;
/** After a touch's pause the loop restarts this long before its own start, at rest. */
const RESUME_REST = 1;
const TAP_MS = 500;

function clamp(x: number, lo: number, hi: number) {
  'worklet';
  return Math.min(hi, Math.max(lo, x));
}

function lerp(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

/** 0…1 through `slice` of the timeline. */
function part(t: number, slice: [number, number]) {
  'worklet';
  return clamp((t - slice[0]) / (slice[1] - slice[0]), 0, 1);
}

function wordReveal(t: number, index: number) {
  'worklet';
  return EASE_OUT(clamp((t - WORDS_FROM - index * WORD_STAGGER) / WORD_SPAN, 0, 1));
}

function letterReveal(t: number, index: number, letter: number) {
  'worklet';
  return EASE_OUT(clamp((t - WORDS_FROM - index * WORD_STAGGER - letter * LETTER_STAGGER) / LETTER_SPAN, 0, 1));
}

function crossed(before: number, now: number, at: number) {
  'worklet';
  return (before < at && now >= at) || (now < before && (at > before || at <= now));
}

/** 1 at `at`, falling to 0 `width` s either side. */
function pulse(c: number, at: number, width: number) {
  'worklet';
  return clamp(1 - Math.abs(c - at) / width, 0, 1);
}

function smooth(u: number) {
  'worklet';
  const t = clamp(u, 0, 1);
  return t * t * (3 - 2 * t);
}

/** The curtain down to `p` of the screen, its leading edge bowed ahead in the direction it moves. */
function curtainPath(width: number, height: number, p: number, dir: number) {
  'worklet';
  const builder = Skia.PathBuilder.Make();
  const edge = p * height;
  if (!(edge > 0.5) || !(width > 0)) return builder.build();
  const bow = edge + (4 / 3) * dir * BULGE * Math.sin(Math.PI * p);
  builder.moveTo(-2, -2);
  builder.lineTo(width + 2, -2);
  builder.lineTo(width + 2, edge);
  builder.cubicTo(width * 0.72, bow, width * 0.28, bow, -2, edge);
  builder.close();
  return builder.build();
}

function haptic(kind: 'tick' | 'light') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'tick') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type Geo = { width: number; height: number; top: number; bottom: number };

type LetterProps = { char: string; index: number; letter: number; open: SharedValue<number>; italic: SharedValue<number>; reduced: boolean };

/** One roman letter, rising out of the word's clipped baseline a beat after the one before it. */
function Letter({ char, index, letter, open, italic, reduced }: LetterProps) {
  const style = useAnimatedStyle(() => {
    if (reduced) return { opacity: wordReveal(open.get(), index) * (1 - italic.get()) };
    const r = letterReveal(open.get(), index, letter);
    return { opacity: 1 - italic.get(), transform: [{ translateY: (1 - r) * CLIP }, { rotate: `${(1 - r) * 4}deg` }] };
  });
  return <Animated.Text style={[styles.word, style]}>{char}</Animated.Text>;
}

type WordProps = {
  index: number;
  word: string;
  count: string;
  open: SharedValue<number>;
  hover: SharedValue<number>;
  reduced: boolean;
};

/**
 * One menu word. Its letters rise one by one; under the finger it crossfades to its italic,
 * steps right and draws a rule under itself while the other words dim.
 */
function MenuWord({ index, word, count, open, hover, reduced }: WordProps) {
  const focus = useSharedValue(1);
  const shift = useSharedValue(0);
  const italic = useSharedValue(0);
  const rule = useSharedValue(0);
  const romanW = useSharedValue(0);
  const italicW = useSharedValue(0);
  useAnimatedReaction(
    () => hover.get(),
    (h) => {
      const on = h < 0 || h === index;
      const held = h === index;
      focus.set(withTiming(on ? 1 : DIMMED, { duration: 220 }));
      shift.set(withTiming(held && !reduced ? FOCUS_SHIFT : 0, { duration: 320, easing: Easing.bezier(0.23, 1, 0.32, 1) }));
      italic.set(withTiming(held ? 1 : 0, { duration: 240 }));
      rule.set(withTiming(held ? 1 : 0, held ? { duration: 380, easing: Easing.bezier(0.23, 1, 0.32, 1) } : { duration: 200 }));
    },
  );
  const rowStyle = useAnimatedStyle(() => ({ opacity: focus.get(), transform: [{ translateX: shift.get() }] }));
  const fadeStyle = useAnimatedStyle(() => ({ opacity: wordReveal(open.get(), index) }));
  const italicStyle = useAnimatedStyle(() => {
    const r = wordReveal(open.get(), index);
    return reduced ? { opacity: italic.get() * r } : { opacity: italic.get(), transform: [{ translateY: (1 - r) * CLIP }] };
  });
  const countStyle = useAnimatedStyle(() => ({
    opacity: wordReveal(open.get(), index),
    transform: [{ translateX: -(romanW.get() - italicW.get()) * italic.get() }],
  }));
  const ruleStyle = useAnimatedStyle(() => ({
    width: Math.max(0, rule.get() * lerp(romanW.get(), italicW.get(), italic.get())),
    opacity: wordReveal(open.get(), index),
  }));
  const letters = Array.from(word);
  return (
    <Animated.View style={[styles.row, rowStyle]} accessible accessibilityLabel={`${word}, ${count}`}>
      <Animated.Text style={[styles.number, fadeStyle]}>{String(index + 1).padStart(2, '0')}</Animated.Text>
      <View>
        <View style={styles.clip}>
          <View style={styles.letters} onLayout={(e: LayoutChangeEvent) => romanW.set(e.nativeEvent.layout.width)}>
            {letters.map((char, j) => (
              <Letter key={j} char={char} index={index} letter={j} open={open} italic={italic} reduced={reduced} />
            ))}
          </View>
          <Animated.Text
            style={[styles.word, styles.italicWord, italicStyle]}
            numberOfLines={1}
            onLayout={(e: LayoutChangeEvent) => italicW.set(e.nativeEvent.layout.width)}
          >
            {word}
          </Animated.Text>
        </View>
        <Animated.View style={[styles.rule, ruleStyle]} />
      </View>
      <Animated.Text style={[styles.count, countStyle]}>{count}</Animated.Text>
    </Animated.View>
  );
}

type PreviewProps = {
  index: number;
  image: SkImage | null;
  current: SharedValue<number>;
  previous: SharedValue<number>;
  wipe: SharedValue<number>;
  w: SharedValue<number>;
  h: SharedValue<number>;
};

/**
 * One section's photo inside the card, drawn "cover" into the card's animating rect. The current
 * photo fills the part the wipe has passed (from the bottom up) and the previous one the rest,
 * so they never overlap.
 */
function PreviewPhoto({ index, image, current, previous, wipe, w, h }: PreviewProps) {
  const clip = useDerivedValue(() => {
    const cw = w.get();
    const ch = h.get();
    const edge = ch * (1 - wipe.get());
    if (current.get() === index) return Skia.XYWHRect(0, edge, cw, ch - edge);
    if (previous.get() === index) return Skia.XYWHRect(0, 0, cw, edge);
    return Skia.XYWHRect(0, 0, 0, 0);
  });
  // The incoming photo settles from slightly zoomed in as it is uncovered.
  const transform = useDerivedValue(() => {
    const zoom = current.get() === index ? WIPE_ZOOM - (WIPE_ZOOM - 1) * EASE_OUT(wipe.get()) : 1;
    const hx = w.get() / 2;
    const hy = h.get() / 2;
    return [{ translateX: hx }, { translateY: hy }, { scale: zoom }, { translateX: -hx }, { translateY: -hy }];
  });
  if (!image) return null;
  return (
    <Group clip={clip}>
      <Group transform={transform}>
        <SkiaImage image={image} x={0} y={0} width={w} height={h} fit="cover" />
      </Group>
    </Group>
  );
}

/**
 * A museum's site with a hamburger button. Tapping it morphs the two lines into a cross and rolls
 * "Menu" to "Close" while a curtain with a soft liquid edge comes down over the sinking page and
 * the menu's serif words rise letter by letter from their clipped baselines. Running a finger
 * down the words turns the one under it italic, rules it and floats a lagging, leaning photo
 * card that wipes to each word's photo while the menu takes that word's colour. Lifting on a
 * word flies the card into its page's hero as the curtain lifts, then the page writes itself in.
 */
export function EditorialMenuMorph({ autoPlay = false, sections = SECTIONS, onSelect }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [menuOpen, setMenuOpen] = useState(false);
  const [page, setPage] = useState({ index: 0, previous: 0 });
  const width = size?.width ?? 0;
  const height = size?.height ?? 0;
  const top = insets.top;
  const bottom = insets.bottom;
  const listTop = top + LIST_TOP;
  const count = sections.length;
  const layout = pageLayout(width, height, top, bottom);
  const images = useSectionImages(size ? sections.map((s) => photo(s.photo, layout.heroW, layout.heroH)) : []);
  const pageIndex = clamp(page.index, 0, Math.max(0, count - 1));
  const tints = sections.map((s) => s.tint);

  const geo = useSharedValue<Geo>({ width: 0, height: 0, top: 0, bottom: 0 });
  const open = useSharedValue(0);
  const isOpen = useSharedValue(false);
  const dir = useSharedValue(1);
  const hover = useSharedValue(-1);
  const current = useSharedValue(0);
  const previous = useSharedValue(-1);
  const wipe = useSharedValue(1);
  const shown = useSharedValue(0);
  const fingerX = useSharedValue(0);
  const fingerY = useSharedValue(0);
  const px = useSharedValue(0);
  const py = useSharedValue(0);
  const lean = useSharedValue(0);
  const bgFrom = useSharedValue(BASE_TINT);
  const bgTo = useSharedValue(BASE_TINT);
  const bgT = useSharedValue(1);
  // The card as drawn: centre, size, turn (degrees), corner radius, opacity, and how far its
  // empty tone has gone from the menu's to the page's placeholder.
  const cardX = useSharedValue(0);
  const cardY = useSharedValue(0);
  const cardW = useSharedValue(PREVIEW_W);
  const cardH = useSharedValue(PREVIEW_H);
  const cardRot = useSharedValue(0);
  const cardR = useSharedValue(PREVIEW_W / 2);
  const cardAlpha = useSharedValue(0);
  const cardTone = useSharedValue(0);
  // Item to page: 0 at rest or hovering, 1 while the card flies to (or sits on) the hero.
  const phase = useSharedValue(0);
  const flight = useSharedValue(0);
  const flightV = useSharedValue(0);
  const flightAlpha = useSharedValue(1);
  const startX = useSharedValue(0);
  const startY = useSharedValue(0);
  const startW = useSharedValue(PREVIEW_W);
  const startH = useSharedValue(PREVIEW_H);
  const startRot = useSharedValue(0);
  const heroShown = useSharedValue(1);
  const pageIn = useSharedValue(1);
  const sectionSV = useSharedValue(0);
  // The touch: 0 none, 1 on the burger, 2 over the open menu.
  const touchKind = useSharedValue(0);
  const touchAt = useSharedValue(0);
  const now = useSharedValue(0);
  // autoPlay
  const clock = useSharedValue(0);
  const looping = useSharedValue(auto);
  const hold = useSharedValue(0);
  const userTouch = useSharedValue(false);
  const ghostX = useSharedValue(0);
  const ghostY = useSharedValue(0);
  const ghostAlpha = useSharedValue(0);
  const ghostPress = useSharedValue(0);

  useEffect(() => {
    geo.set({ width, height, top, bottom });
  }, [width, height, top, bottom, geo]);
  useEffect(() => {
    looping.set(auto);
    clock.set(0);
    hold.set(0);
  }, [auto, clock, hold, looping]);

  const committed = (index: number, user: boolean) => {
    setPage((p) => ({ index, previous: p.index }));
    if (user) haptic('light');
    onSelect?.(sections[index]?.word ?? '');
  };

  /** The card has landed and the curtain is up: the page's hero takes over, its text writes in. */
  const handoff = () => {
    'worklet';
    phase.set(0);
    shown.set(0);
    cardAlpha.set(0);
    heroShown.set(reduced ? withTiming(1, { duration: 300 }) : 1);
    pageIn.set(0);
    pageIn.set(withTiming(1, { duration: PAGE_IN_TIME, easing: Easing.linear }));
    hover.set(-1);
  };

  const toggle = (user: boolean) => {
    'worklet';
    if (phase.get() === 1) handoff();
    const next = !isOpen.get();
    isOpen.set(next);
    dir.set(next ? 1 : -1);
    hover.set(-1);
    open.set(withTiming(next ? 1 : 0, { duration: reduced ? REDUCED_TIME : next ? OPEN_TIME : CLOSE_TIME, easing: Easing.linear }));
    scheduleOnRN(setMenuOpen, next);
    if (user) scheduleOnRN(haptic, 'light');
  };

  /** Lifting on word `h`: the card flies to the hero while the curtain lifts off the new page. */
  const commit = (h: number) => {
    'worklet';
    const s = 0.86 + 0.14 * shown.get();
    startX.set(px.get());
    startY.set(py.get());
    startW.set(PREVIEW_W * s);
    startH.set(PREVIEW_H * s);
    startRot.set(lean.get());
    flight.set(0);
    flightV.set(0);
    if (reduced) {
      flightAlpha.set(shown.get());
      flightAlpha.set(withTiming(0, { duration: 200 }));
    } else {
      flightAlpha.set(Math.max(shown.get(), 0.01));
      flightAlpha.set(withTiming(1, { duration: 160 }));
    }
    if (current.get() !== h) {
      previous.set(-1);
      current.set(h);
      wipe.set(1);
    }
    phase.set(1);
    heroShown.set(0);
    pageIn.set(0);
    sectionSV.set(h);
    // The same choreography as a close, keeping the chosen word lit while it drops.
    isOpen.set(false);
    dir.set(-1);
    open.set(withTiming(0, { duration: reduced ? REDUCED_TIME : CLOSE_TIME, easing: Easing.linear }));
    scheduleOnRN(setMenuOpen, false);
    scheduleOnRN(committed, h, userTouch.get());
  };

  /** Shows section `i` without the menu: its hero fades in and its text writes in again. */
  const swapTo = (i: number) => {
    'worklet';
    if (phase.get() === 1) handoff();
    sectionSV.set(i);
    heroShown.set(0);
    heroShown.set(withDelay(80, withTiming(1, { duration: 400 })));
    pageIn.set(0);
    pageIn.set(withTiming(1, { duration: PAGE_IN_TIME, easing: Easing.linear }));
    scheduleOnRN(committed, i, false);
  };

  /** The word under a point, or -1. */
  const wordAt = (x: number, y: number) => {
    'worklet';
    const k = Math.floor((y - listTop) / ROW);
    if (k < 0 || k >= count || x > geo.get().width - 8) return -1;
    return k;
  };

  const release = () => {
    'worklet';
    const h = hover.get();
    if (h >= 0 && isOpen.get() && open.get() > 0.9 && phase.get() === 0) commit(h);
    else hover.set(-1);
  };

  // Each new word: the background crossfades to its colour and its photo wipes in.
  useAnimatedReaction(
    () => hover.get(),
    (next, before) => {
      if (before === null || next === before) return;
      const mixed = interpolateColor(bgT.get(), [0, 1], [bgFrom.get(), bgTo.get()]);
      bgFrom.set(mixed);
      bgTo.set(next >= 0 && next < tints.length ? tints[next] : BASE_TINT);
      bgT.set(0);
      bgT.set(withTiming(1, { duration: BG_TIME }));
      if (phase.get() === 1) return;
      if (next < 0) {
        shown.set(withTiming(0, { duration: 220 }));
        return;
      }
      if (before < 0 || shown.get() < 0.05) {
        // The first word under the finger: the card appears where the finger is, already filled.
        previous.set(-1);
        current.set(next);
        wipe.set(1);
        px.set(fingerX.get() + PREVIEW_DX);
        py.set(fingerY.get() + PREVIEW_DY);
      } else {
        previous.set(current.get());
        current.set(next);
        wipe.set(0);
        wipe.set(withTiming(1, { duration: reduced ? 1 : WIPE_TIME, easing: Easing.bezier(0.65, 0, 0.35, 1) }));
      }
      shown.set(withTiming(1, { duration: 260, easing: Easing.bezier(0.23, 1, 0.32, 1) }));
      if (userTouch.get()) scheduleOnRN(haptic, 'tick');
    },
  );

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    now.set(frame.timestamp);
    if (!(dt > 0)) return;
    const g = geo.get();
    const W = g.width;

    // The card chases the finger; it leans into its own sideways speed.
    const tx = clamp(fingerX.get() + PREVIEW_DX, PREVIEW_W / 2 + 10, W - PREVIEW_W / 2 - 10);
    const ty = Math.max(fingerY.get() + PREVIEW_DY, g.top + 60 + PREVIEW_H / 2);
    const k = reduced ? 1 : 1 - Math.exp(-dt * LAG);
    const nx = px.get() + (tx - px.get()) * k;
    const vx = (nx - px.get()) / dt;
    px.set(nx);
    py.set(py.get() + (ty - py.get()) * k);
    const leanTarget = reduced ? 0 : clamp(vx * LEAN_PER_SPEED, -LEAN_MAX, LEAN_MAX);
    lean.set(lean.get() + (leanTarget - lean.get()) * (1 - Math.exp(-dt * 10)));

    if (phase.get() === 1) {
      // The flight: a damped spring from 0 to 1, in small steps so a long frame stays stable.
      let f = flight.get();
      let v = flightV.get();
      if (reduced) {
        f = 1;
        v = 0;
      } else {
        const h = dt / 4;
        for (let i = 0; i < 4; i += 1) {
          v += (-FLIGHT_K * (f - 1) - FLIGHT_C * v) * h;
          f += v * h;
        }
        if (Math.abs(1 - f) < 0.0015 && Math.abs(v) < 0.02) {
          f = 1;
          v = 0;
        }
      }
      flight.set(f);
      flightV.set(v);
      // The hero's frame as it is drawn right now, inside the sinking page.
      const L = pageLayout(W, g.height, g.top, g.bottom);
      const p = reduced ? 0 : EASE_IN_OUT(part(open.get(), PANEL));
      const s = 1 - SINK_SCALE * p;
      const hx = W / 2 + (L.heroX + L.heroW / 2 - W / 2) * s;
      const hy = g.height / 2 + (L.heroY + L.heroH / 2 - g.height / 2) * s + SINK * p;
      const F = reduced ? 0 : clamp(f, 0, 1);
      cardX.set(lerp(startX.get(), hx, F));
      cardY.set(lerp(startY.get(), hy, F));
      cardW.set(lerp(startW.get(), L.heroW * s, F));
      cardH.set(lerp(startH.get(), L.heroH * s, F));
      cardRot.set(lerp(startRot.get(), 0, F));
      // A circle opening into the hero's rectangle: the corner keeps to half the shorter side,
      // shrinking to nothing a little ahead of the size so the last of the round goes calmly.
      cardR.set((Math.min(cardW.get(), cardH.get()) / 2) * Math.pow(1 - F, 1.4));
      cardTone.set(F);
      cardAlpha.set(flightAlpha.get());
      if (f >= 1 && open.get() <= 0.0005) handoff();
    } else {
      const s = 0.86 + 0.14 * shown.get();
      cardX.set(px.get());
      cardY.set(py.get());
      cardW.set(PREVIEW_W * s);
      cardH.set(PREVIEW_H * s);
      cardRot.set(lean.get());
      cardR.set((PREVIEW_W * s) / 2);
      cardTone.set(0);
      cardAlpha.set(shown.get());
    }

    if (hold.get() > 0) {
      hold.set(hold.get() - dt);
      if (hold.get() <= 0 && looping.get()) {
        // The pause is over: close the menu, go back to the first page, start the loop again.
        clock.set(-RESUME_REST);
        userTouch.set(false);
        if (isOpen.get()) toggle(false);
        if (sectionSV.get() !== 0) swapTo(0);
      }
      return;
    }
    if (!looping.get() || W === 0) return;
    const before = clock.get();
    let c = before + dt;
    if (c >= LOOP) c -= LOOP;
    clock.set(c);

    const burgerX = W - 36;
    const burgerY = g.top + 30;
    let ghostOn = 0;
    let press = 0;
    for (const at of TAPS) {
      if (crossed(before, c, at - 0.3)) {
        ghostX.set(burgerX);
        ghostY.set(burgerY);
      }
      if (crossed(before, c, at) && !isOpen.get()) toggle(false);
      ghostOn = Math.max(ghostOn, Math.min(1, 2 * pulse(c, at, 0.3)));
      press = Math.max(press, pulse(c, at, 0.12));
    }
    // The glides: from word to word, easing between stops, drifting a little to the right; the
    // last stop is the lift.
    for (const glide of GLIDES) {
      const first = glide[0][0];
      const lift = glide[glide.length - 1][0];
      if (c >= first && c < lift && isOpen.get()) {
        let row = glide[0][1];
        for (let i = 1; i < glide.length; i += 1) {
          const t0 = glide[i - 1][0];
          const t1 = glide[i][0];
          if (c >= t0) row = lerp(glide[i - 1][1], glide[i][1], smooth((c - t0) / (t1 - t0)));
        }
        const x = LIST_LEFT + 90 + 50 * Math.sin((c - first) * 1.3);
        const y = listTop + (row + 0.5) * ROW;
        fingerX.set(x);
        fingerY.set(y);
        ghostX.set(x);
        ghostY.set(y);
        hover.set(wordAt(x, y));
      }
      if (crossed(before, c, lift)) release();
      ghostOn = Math.max(ghostOn, clamp(Math.min((c - first + 0.25) / 0.2, (lift + 0.2 - c) / 0.2), 0, 1));
    }
    ghostAlpha.set(ghostOn);
    ghostPress.set(press);
  });

  const touched = () => {
    'worklet';
    hold.set(USER_HOLD);
    userTouch.set(true);
    ghostAlpha.set(0);
  };

  /** The burger and its "Menu" label, with some slop around them. */
  const onBurger = (x: number, y: number) => {
    'worklet';
    const W = geo.get().width;
    return x >= W - 36 - 22 - 58 && x <= W && Math.abs(y - (geo.get().top + 30)) <= 32;
  };

  // One touch for the whole screen: a tap on the burger toggles the menu; over the open menu the
  // finger is a pointer from touch down, and lifting on a word opens its page.
  const touch = Gesture.Pan()
    .minDistance(0)
    .maxPointers(1)
    .shouldCancelWhenOutside(false)
    .onBegin((event) => {
      touched();
      if (onBurger(event.x, event.y)) {
        touchKind.set(1);
        touchAt.set(now.get());
      } else if (isOpen.get()) {
        touchKind.set(2);
        fingerX.set(event.x);
        fingerY.set(event.y);
        hover.set(wordAt(event.x, event.y));
      } else {
        touchKind.set(0);
      }
    })
    .onUpdate((event) => {
      if (touchKind.get() !== 2) return;
      fingerX.set(event.x);
      fingerY.set(event.y);
      if (isOpen.get()) hover.set(wordAt(event.x, event.y));
    })
    .onFinalize((event) => {
      const kind = touchKind.get();
      touchKind.set(0);
      if (kind === 1) {
        if (onBurger(event.x, event.y) && now.get() - touchAt.get() <= TAP_MS) toggle(true);
      } else if (kind === 2) {
        release();
      }
    });

  // ── styles ─────────────────────────────────────────────────────────────
  const curtain = useDerivedValue(() => {
    const g = geo.get();
    const p = EASE_IN_OUT(part(open.get(), PANEL));
    if (reduced) return curtainPath(g.width, g.height, p > 0 ? 1 : 0, 0);
    return curtainPath(g.width, g.height, p, dir.get());
  });
  const curtainOpacity = useDerivedValue(() => (reduced ? EASE_IN_OUT(part(open.get(), PANEL)) : 1));
  const curtainColor = useDerivedValue(() => interpolateColor(bgT.get(), [0, 1], [bgFrom.get(), bgTo.get()]));
  const pageStyle = useAnimatedStyle(() => {
    const p = reduced ? 0 : EASE_IN_OUT(part(open.get(), PANEL));
    return { transform: [{ translateY: SINK * p }, { scale: 1 - SINK_SCALE * p }] };
  });
  const shadeStyle = useAnimatedStyle(() => ({ opacity: SINK_SHADE * EASE_IN_OUT(part(open.get(), PANEL)) }));
  const headerInk = useDerivedValue(() => interpolateColor(EASE_IN_OUT(part(open.get(), BURGER)), [0, 1], [INK, PAPER]));
  const inkStyle = useAnimatedStyle(() => ({ color: headerInk.get() }));
  const menuLabelStyle = useAnimatedStyle(() => {
    const b = EASE_IN_OUT(part(open.get(), BURGER));
    return reduced ? { color: headerInk.get(), opacity: 1 - b } : { color: headerInk.get(), transform: [{ translateY: -b * 14 }] };
  });
  const closeLabelStyle = useAnimatedStyle(() => {
    const b = EASE_IN_OUT(part(open.get(), BURGER));
    return reduced ? { color: headerInk.get(), opacity: b } : { color: headerInk.get(), transform: [{ translateY: (1 - b) * 14 }] };
  });
  const lineTopStyle = useAnimatedStyle(() => {
    const b = EASE_IN_OUT(part(open.get(), BURGER));
    const meet = clamp(b * 2, 0, 1);
    const turn = clamp(b * 2 - 1, 0, 1);
    return {
      backgroundColor: headerInk.get(),
      transform: [{ translateY: -5 * (1 - meet) }, { rotate: `${45 * turn}deg` }, { scaleX: 1 - 0.08 * meet }],
    };
  });
  const lineBottomStyle = useAnimatedStyle(() => {
    const b = EASE_IN_OUT(part(open.get(), BURGER));
    const meet = clamp(b * 2, 0, 1);
    const turn = clamp(b * 2 - 1, 0, 1);
    // Closed it is the shorter line, flush right; it lengthens as it meets the other.
    const short = 17 / 26;
    return {
      backgroundColor: headerInk.get(),
      transform: [
        { translateX: (1 - meet) * ((26 - 17) / 2) },
        { translateY: 5 * (1 - meet) },
        { rotate: `${-45 * turn}deg` },
        { scaleX: short + (0.92 - short) * meet },
      ],
    };
  });
  const footerStyle = useAnimatedStyle(() => {
    const f = EASE_OUT(part(open.get(), FOOTER));
    return { opacity: f, transform: [{ translateY: reduced ? 0 : (1 - f) * 16 }] };
  });

  const cardTransform = useDerivedValue(() => [
    { translateX: cardX.get() },
    { translateY: cardY.get() },
    { rotate: (cardRot.get() * Math.PI) / 180 },
    { translateX: -cardW.get() / 2 },
    { translateY: -cardH.get() / 2 },
  ]);
  const cardClip = useDerivedValue(() => Skia.RRectXY(Skia.XYWHRect(0, 0, cardW.get(), cardH.get()), cardR.get(), cardR.get()));
  // The paper ring, drawn just inside the edge so the clip never cuts it.
  const ringPath = useDerivedValue(() => {
    const w = cardW.get();
    const h = cardH.get();
    const inset = BORDER / 2;
    const r = Math.max(0, cardR.get() - inset);
    const builder = Skia.PathBuilder.Make();
    if (w > BORDER * 2 && h > BORDER * 2) builder.addRRect(Skia.RRectXY(Skia.XYWHRect(inset, inset, w - BORDER, h - BORDER), r, r));
    return builder.build();
  });
  // A stroke of width 0 is a hairline in Skia, so the ring also fades as it thins.
  const ringWidth = useDerivedValue(() => Math.max(0.5, BORDER * (1 - cardTone.get())));
  const ringOpacity = useDerivedValue(() => clamp(1 - cardTone.get() * 1.6, 0, 1));
  const cardBase = useDerivedValue(() => interpolateColor(cardTone.get(), [0, 1], [CARD_TONE, HERO_PLACEHOLDER]));
  const bandY = useDerivedValue(() => cardH.get() * (1 - wipe.get()) - BAND);
  const bandOpacity = useDerivedValue(() => (wipe.get() > 0.001 && wipe.get() < 0.999 ? 1 : 0));
  const bandColor = useDerivedValue(() => (current.get() >= 0 && current.get() < tints.length ? tints[current.get()] : BASE_TINT));

  const ghostStyle = useAnimatedStyle(() => ({
    opacity: ghostAlpha.get() * 0.85,
    transform: [{ translateX: ghostX.get() - 22 }, { translateY: ghostY.get() - 22 }, { scale: 1 - 0.2 * ghostPress.get() }],
  }));

  const section = sections[pageIndex];
  return (
    <GestureDetector gesture={touch}>
      <View
        style={styles.screen}
        onLayout={(event: LayoutChangeEvent) => {
          const { width: w, height: h } = event.nativeEvent.layout;
          setSize({ width: w, height: h });
        }}
        accessibilityLabel={`Orsel, Museum of Form. ${menuOpen ? 'Menu open' : 'A menu button at the top right opens the sections'}: ${sections.map((s) => s.word).join(', ')}.`}
      >
        {size && section && (
          <Animated.View style={[StyleSheet.absoluteFill, pageStyle]}>
            <MuseumPage
              section={section}
              index={pageIndex}
              previous={clamp(page.previous, 0, Math.max(0, count - 1))}
              total={count}
              layout={layout}
              image={images[pageIndex] ?? null}
              heroShown={heroShown}
              pageIn={pageIn}
              bottom={bottom}
              reduced={reduced}
            />
          </Animated.View>
        )}
        <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, styles.shade, shadeStyle]} />

        {size && (
          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            <Path path={curtain} color={curtainColor} opacity={curtainOpacity} />
          </Canvas>
        )}

        <View pointerEvents="none" style={StyleSheet.absoluteFill}>
          <View style={{ position: 'absolute', left: LIST_LEFT, right: 0, top: listTop }}>
            {sections.map((s, i) => (
              <MenuWord key={s.word} index={i} word={s.word} count={s.count} open={open} hover={hover} reduced={reduced} />
            ))}
          </View>
          <Animated.View style={[styles.menuFooter, { bottom: bottom + 22 }, footerStyle]}>
            <View style={{ gap: 3 }}>
              <Text style={styles.footerLead}>Open today, 10:00–18:00</Text>
              <Text style={styles.footerText}>Kestrel Quay 4, Lisbourne</Text>
            </View>
            <Text style={styles.footerText}>Tickets · Members · Shop</Text>
          </Animated.View>
        </View>

        {size && (
          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            <Group transform={cardTransform} opacity={cardAlpha}>
              <Group clip={cardClip}>
                <Rect x={0} y={0} width={cardW} height={cardH} color={cardBase} />
                {sections.map((s, i) => (
                  <PreviewPhoto
                    key={s.word}
                    index={i}
                    image={images[i] ?? null}
                    current={current}
                    previous={previous}
                    wipe={wipe}
                    w={cardW}
                    h={cardH}
                  />
                ))}
                <Rect x={0} y={bandY} width={cardW} height={BAND} color={bandColor} opacity={bandOpacity} />
              </Group>
              <Path path={ringPath} style="stroke" strokeWidth={ringWidth} color={PAPER} opacity={ringOpacity} />
            </Group>
          </Canvas>
        )}

        <View pointerEvents="none" style={[styles.header, { top: top + 6 }]}>
          <View>
            <Animated.Text style={[styles.wordmark, inkStyle]}>ORSEL</Animated.Text>
            <Animated.Text style={[styles.wordmarkSub, inkStyle]}>MUSEUM OF FORM</Animated.Text>
          </View>
          <View style={styles.menuButton} accessibilityRole="button" accessibilityLabel={menuOpen ? 'Close menu' : 'Open menu'}>
            <View style={styles.labelClip}>
              <Animated.Text style={[styles.label, menuLabelStyle]}>MENU</Animated.Text>
              <Animated.Text style={[styles.label, styles.labelUnder, closeLabelStyle]}>CLOSE</Animated.Text>
            </View>
            <View style={styles.burger}>
              <Animated.View style={[styles.line, lineTopStyle]} />
              <Animated.View style={[styles.line, lineBottomStyle]} />
            </View>
          </View>
        </View>

        <Animated.View pointerEvents="none" style={[styles.ghost, ghostStyle]} />
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: PAPER, overflow: 'hidden' },
  shade: { backgroundColor: '#000' },
  header: {
    position: 'absolute',
    left: 20,
    right: 20,
    height: 48,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  wordmark: { fontFamily: DISPLAY, fontSize: 24, fontWeight: '700', letterSpacing: 5 },
  wordmarkSub: { fontFamily: SANS, fontSize: 8.5, fontWeight: '600', letterSpacing: 2.6, marginTop: -1 },
  menuButton: { flexDirection: 'row', alignItems: 'center', marginRight: -6 },
  labelClip: { height: 14, overflow: 'hidden', alignItems: 'flex-end', minWidth: 44 },
  label: { fontFamily: SANS, fontSize: 11, lineHeight: 14, fontWeight: '600', letterSpacing: 1.6 },
  labelUnder: { position: 'absolute', right: 0, top: 0 },
  burger: { width: 44, height: 44 },
  line: { position: 'absolute', left: 9, top: 21.2, width: 26, height: 1.6, borderRadius: 1 },
  row: { height: ROW, flexDirection: 'row', alignItems: 'flex-start' },
  number: {
    width: NUMBER_WIDTH,
    marginTop: 12,
    fontFamily: DISPLAY,
    fontWeight: DISPLAY_WEIGHT,
    fontStyle: 'italic',
    fontSize: 15,
    color: 'rgba(239,233,223,0.72)',
    fontVariant: ['tabular-nums'],
  },
  clip: { height: CLIP, overflow: 'hidden' },
  letters: { flexDirection: 'row', alignSelf: 'flex-start' },
  word: { fontFamily: DISPLAY, fontWeight: DISPLAY_WEIGHT, fontSize: WORD_SIZE, lineHeight: CLIP, color: PAPER, letterSpacing: -0.6 },
  italicWord: { position: 'absolute', left: 0, top: 0, fontStyle: 'italic' },
  rule: { position: 'absolute', left: 0, top: RULE_Y, height: 1, backgroundColor: 'rgba(239,233,223,0.85)' },
  count: {
    marginLeft: 6,
    marginTop: 11,
    fontFamily: SANS,
    fontSize: 11,
    fontWeight: '600',
    letterSpacing: 0.4,
    color: 'rgba(239,233,223,0.62)',
    fontVariant: ['tabular-nums'],
  },
  menuFooter: {
    position: 'absolute',
    left: LIST_LEFT,
    right: 22,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-end',
    paddingTop: 16,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(239,233,223,0.35)',
  },
  footerLead: { fontFamily: DISPLAY, fontWeight: DISPLAY_WEIGHT, fontStyle: 'italic', fontSize: 16, color: PAPER },
  footerText: { fontFamily: SANS, fontSize: 12, fontWeight: '500', color: 'rgba(239,233,223,0.7)' },
  ghost: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: 'rgba(120,110,98,0.35)',
    borderWidth: 1.5,
    borderColor: 'rgba(255,255,255,0.8)',
  },
});
