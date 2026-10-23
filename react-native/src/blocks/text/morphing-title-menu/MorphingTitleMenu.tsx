import {
  Blur,
  Canvas,
  Circle as SkiaCircle,
  Group,
  Image as SkiaImage,
  makeImageFromView,
  matchFont,
  Paint,
  Path as SkiaPath,
  Picture,
  RoundedRect,
  Shadow,
  Skia,
  Text as SkiaText,
  TileMode,
  useFont,
  type SkFont,
  type SkImage,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle, Path, Rect } from 'react-native-svg';

// A podcast list whose page title is a menu. Tapping the big condensed title springs a small
// popover out of it; picking the other list morphs the title letter by letter: each old letter
// sinks, shrinks and blurs away while the new word's letter drops in from above, sharpening as it
// lands, one slot after another from the left. The title's width follows, the chevron with it,
// and the list underneath cross-fades. A port of the SwiftUI block, number for number.
//
// autoPlay timeline (LOOP = 6 s):
//   0.00        rest on "Queue", popover closed (also the first frame on screen)
//   0.60        the title is tapped: the popover springs open (response 0.42 s, damping 0.64)
//   1.45        "Favorites" is tapped: the highlight pill and its check slide down (spring 0.32 / 0.78)
//   1.68        the popover shrinks back into the title (ease-in, 0.2 s)
//   1.80        the title morphs "Queue" -> "Faves" (0.63 s); the list cross-fades (0.35 s, ease-out)
//   2.43-3.60   rest on "Faves"
//   3.60        open; 4.45 pick "Queue"; 4.68 close; 4.80 the title morphs back
//   5.43-6.00   rest on "Queue"

/** Seconds. */
export const LOOP = 6;
const HALF = 3;
const OPEN_AT = 0.6;
const PICK_AT = 1.45;
const CLOSE_AFTER_PICK = 0.23;
const COMMIT_AFTER_CLOSE = 0.12;
const USER_HOLD = 2;

// The per-letter morph.
const STAGGER = 0.06;
const OUT_DURATION = 0.28;
const IN_DELAY = 0.05;
const IN_DURATION = 0.34;
/** How far a letter travels, how small it gets and how much it blurs, in units of the font size. */
const DROP = 0.48;
const MIN_SCALE = 0.55;
const BLUR = 0.09;
/** Under reduced motion the words only cross-fade, over this long. */
const FADE = 0.25;

const morphDuration = (slots: number) =>
  (Math.max(slots, 1) - 1) * STAGGER + Math.max(OUT_DURATION, IN_DELAY + IN_DURATION);

type Pose = { dy: number; scale: number; blur: number; opacity: number };
const REST: Pose = { dy: 0, scale: 1, blur: 0, opacity: 1 };

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}

function easeInOutCubic(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

/** The old letter of a slot, `p` from 0 (in place) to 1 (gone). */
function poseOut(p: number, size: number): Pose {
  'worklet';
  const e = p * p;
  return { dy: DROP * size * e, scale: 1 - (1 - MIN_SCALE) * e, blur: BLUR * size * p, opacity: 1 - p };
}

/** The new letter of a slot, `q` from 0 (above, small, blurred) to 1 (landed). */
function poseIn(q: number, size: number): Pose {
  'worklet';
  const e = 1 - Math.pow(1 - q, 3);
  const soft = 1 - (1 - q) * (1 - q);
  return {
    dy: -DROP * size * (1 - e),
    scale: MIN_SCALE + (1 - MIN_SCALE) * e,
    blur: BLUR * size * (1 - soft),
    opacity: Math.min(1, q * 2),
  };
}

// Springs given like SwiftUI's (response, damping fraction).
const spring = (response: number, dampingFraction: number) => {
  const stiffness = Math.pow((2 * Math.PI) / response, 2);
  return { mass: 1, stiffness, damping: 2 * dampingFraction * Math.sqrt(stiffness), reduceMotion: ReduceMotion.Never };
};
const OPEN = spring(0.42, 0.64);
const HIGHLIGHT = spring(0.32, 0.78);
const EASE_IN = Easing.bezier(0.42, 0, 1, 1);
const EASE_OUT = Easing.bezier(0, 0, 0.58, 1);

// Metrics, pt.
const SIDE = 30;
const HEADER_TOP = 40;
const TITLE_SIZE = 46;
const TITLE_BOX = 54;
const BASELINE = 47;
const CHEVRON_GAP = 7;
const CHEVRON_SIZE = 22;
const CANVAS_PAD = 30;
const CANVAS_WIDTH = 360;
const SUBTITLE_TOP = 8;
const LIST_TOP = 44;
const EPISODE_GAP = 34;
const MENU_WIDTH = 164;
const MENU_INSET = 7;
const MENU_ROW = 44;
const MENU_RADIUS = 22;
const MENU_X = SIDE - 12;
const MENU_GAP = 6;
/** Room around the popover's canvas for its shadow and its blur. */
const MENU_MARGIN = 70;
/** Room around a list's canvas for its blur and its drop. */
const LIST_PAD = 24;
/** Blur radii as in the SwiftUI version: the popover sharpens from 10, a list that is not shown is at 6. */
const MENU_BLUR = 10;
const LIST_BLUR = 6;
const SYSTEM_FONT = Platform.select({ ios: '.AppleSystemUIFont', default: 'sans-serif' });

const C = {
  background: '#F8F6F8',
  ink: '#0E0E10',
  ink2: '#2C2C2E',
  mute: '#9C9AA0',
  faint: '#B6B4B9',
  chip: '#ECEAED',
  chevron: '#A6A4A9',
  track: '#E6E4E7',
  fill: '#5E5D62',
  pill: '#F0EEF1',
  icon: '#6E6D72',
};

export type MorphingTitleMenuEpisode = {
  show: string;
  when: string;
  title: string;
  /** What the play button says after its verb: time left or the whole length. */
  length: string;
  /** How far it has been listened to, when it has been started. */
  progress?: number;
  playing?: boolean;
  art: [string, string];
  mark: string;
};

export type MorphingTitleMenuPage = {
  /** The word in the title. */
  title: string;
  /** Its row in the popover. */
  label: string;
  subtitle: string;
  episodes: MorphingTitleMenuEpisode[];
};

const PIXEL_HOURS: [string, string] = ['#1F2A44', '#3B5BDB'];
const STUDIO_NOTES: [string, string] = ['#F59F00', '#F76707'];
const LONG_BUILD: [string, string] = ['#2B8A3E', '#94D82D'];
const FIELDWORK: [string, string] = ['#C2255C', '#F783AC'];

export const MORPHING_TITLE_MENU_SAMPLES: MorphingTitleMenuPage[] = [
  {
    title: 'Queue',
    label: 'Queue',
    subtitle: 'Fri 4 Apr',
    episodes: [
      { show: 'Pixel Hours', when: '2d ago', title: 'Designing on expert mode', length: '5m', progress: 0.42, playing: true, art: PIXEL_HOURS, mark: 'P' },
      { show: 'Studio Notes', when: '4d ago', title: 'Why every tool ends up a canvas', length: '48m', art: STUDIO_NOTES, mark: 'S' },
      { show: 'The Long Build', when: '1w ago', title: 'Shipping the boring parts first', length: '1h 12m', art: LONG_BUILD, mark: 'L' },
    ],
  },
  {
    title: 'Faves',
    label: 'Favorites',
    subtitle: '2 Episodes',
    episodes: [
      { show: 'Fieldwork FM', when: 'Jul 11, 2024', title: 'Building product at Lumen: craft, metrics and taste', length: '2h 35m', art: FIELDWORK, mark: 'F' },
      { show: 'Studio Notes', when: 'Mar 3, 2025', title: 'Small teams, sharp tools', length: '54m', art: STUDIO_NOTES, mark: 'S' },
    ],
  },
];

/** A title word laid out in the display face: where each letter starts, its advance, the whole width. */
type Word = { text: string; xs: number[]; advances: number[]; width: number };

function layoutWord(text: string, font: SkFont | null): Word {
  if (!font) {
    // Until the face is loaded: Anton's average advance, so the chevron is roughly in place.
    const advances = [...text].map(() => TITLE_SIZE * 0.47);
    const xs = advances.map((_, i) => advances.slice(0, i).reduce((s, a) => s + a, 0));
    return { text, xs, advances, width: advances.reduce((s, a) => s + a, 0) };
  }
  const advances = font.getGlyphWidths(font.getGlyphIDs(text));
  const xs = advances.map((_, i) => advances.slice(0, i).reduce((s, a) => s + a, 0));
  return { text, xs, advances, width: advances.reduce((s, a) => s + a, 0) };
}

type Props = {
  pages?: MorphingTitleMenuPage[];
  /** Opens the popover and switches lists by itself, every LOOP seconds. */
  autoPlay?: boolean;
  searchLabel?: string;
  /** Called with the page's index when a list is picked. */
  onSelect?: (index: number) => void;
};

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function MorphingTitleMenu({
  pages = MORPHING_TITLE_MENU_SAMPLES,
  autoPlay = false,
  searchLabel = 'Search',
  onSelect,
}: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const font = useFont(require('./assets/Anton-Regular.ttf'), TITLE_SIZE);
  const words = useMemo(() => pages.map((p) => layoutWord(p.title, font)), [pages, font]);
  const capHeight = useMemo(() => (font ? font.measureText('H').height : TITLE_SIZE * 0.859), [font]);

  const [selected, setSelected] = useState(0);
  const [menuOpen, setMenuOpen] = useState(false);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  // The loop and the timers read the latest state when they fire.
  const latest = useRef({ selected, menuOpen, reduced, pages, words, onSelect });
  useEffect(() => {
    latest.current = { selected, menuOpen, reduced, pages, words, onSelect };
  });

  const menu = useSharedValue(0);
  const highlight = useSharedValue(0);
  // The morph lives in shared values only, so it starts on the frame it is committed and never waits
  // for React: seconds since it started, which word goes and which comes, and the title's width
  // before and after it.
  const morphT = useSharedValue(0);
  const morphFrom = useSharedValue(0);
  const morphTo = useSharedValue(0);
  const fromWidth = useSharedValue(0);
  const toWidth = useSharedValue(0);
  const morphing = useSharedValue(false);
  const morphTotal = useSharedValue(1);
  // The lists' cross-fade, the same way: the list shown, the one before it, and the fade's progress.
  const listOn = useSharedValue(0);
  const listOff = useSharedValue(-1);
  const listFade = useSharedValue(1);


  const pickTimers = useRef<ReturnType<typeof setTimeout>[]>([]);
  const clearPick = () => {
    pickTimers.current.forEach(clearTimeout);
    pickTimers.current = [];
  };
  useEffect(() => clearPick, []);

  const openMenu = useCallback(() => {
    highlight.set(latest.current.selected);
    setMenuOpen(true);
    menu.set(latest.current.reduced ? withTiming(1, { duration: 200, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }) : withSpring(1, OPEN));
  }, [highlight, menu]);

  const closeMenu = useCallback(() => {
    setMenuOpen(false);
    menu.set(withTiming(0, { duration: 200, easing: latest.current.reduced ? EASE_OUT : EASE_IN, reduceMotion: ReduceMotion.Never }));
  }, [menu]);

  const moveHighlight = useCallback(
    (i: number) => {
      highlight.set(latest.current.reduced ? i : withSpring(i, HIGHLIGHT));
    },
    [highlight],
  );

  const commit = useCallback(
    (i: number) => {
      const { selected: from, reduced: r, words: w, pages: p } = latest.current;
      if (i === from) return;
      const total = r ? FADE : morphDuration(Math.max(p[from].title.length, p[i].title.length));
      fromWidth.set(w[from].width);
      toWidth.set(w[i].width);
      morphFrom.set(from);
      morphTo.set(i);
      morphTotal.set(total);
      morphT.set(0);
      morphing.set(true);
      morphT.set(
        withTiming(total, { duration: total * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }, (finished) => {
          if (finished) morphing.set(false);
        }),
      );
      listOff.set(from);
      listOn.set(i);
      listFade.set(0);
      listFade.set(withTiming(1, { duration: r ? FADE * 1000 : 350, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }));
      latest.current.selected = i;
      setSelected(i);
      latest.current.onSelect?.(i);
    },
    [fromWidth, toWidth, morphFrom, morphTo, morphing, morphTotal, morphT, listOn, listOff, listFade],
  );

  /** A row of the popover was picked: the highlight moves to it, the popover closes and the title morphs. */
  const pick = useCallback(
    (i: number) => {
      moveHighlight(i);
      clearPick();
      pickTimers.current.push(
        setTimeout(() => {
          closeMenu();
          pickTimers.current.push(setTimeout(() => commit(i), COMMIT_AFTER_CLOSE * 1000));
        }, CLOSE_AFTER_PICK * 1000),
      );
    },
    [moveHighlight, closeMenu, commit],
  );

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => setTouches((n) => n + 1);

  // The screen shows (and the loop starts) once the title's face is loaded, so no frame has a wrong title.
  const ready = font != null;
  useEffect(() => {
    if (!autoPlay || reduced || !ready) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, Math.max(0, ms)));
    const cycle = (origin: number) => {
      const at = (t: number) => origin + t * 1000 - Date.now();
      for (const [offset, target] of [
        [0, 1],
        [HALF, 0],
      ] as const) {
        if (target >= latest.current.pages.length) continue;
        after(at(offset + OPEN_AT), openMenu);
        after(at(offset + PICK_AT), () => moveHighlight(target));
        after(at(offset + PICK_AT + CLOSE_AFTER_PICK), closeMenu);
        after(at(offset + PICK_AT + CLOSE_AFTER_PICK + COMMIT_AFTER_CLOSE), () => commit(target));
      }
      after(at(LOOP), () => cycle(origin + LOOP * 1000));
    };
    if (touches === 0) cycle(Date.now());
    else
      after(USER_HOLD * 1000, () => {
        clearPick();
        let wait = 0;
        if (latest.current.menuOpen) {
          closeMenu();
          wait = 250;
        }
        after(wait, () => {
          if (latest.current.selected !== 0) {
            commit(0);
            after(1000, () => cycle(Date.now()));
          } else cycle(Date.now());
        });
      });
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, reduced, ready, touches, openMenu, closeMenu, moveHighlight, commit]);

  // The title's width as it is drawn now.
  const restWidths = useMemo(() => words.map((w) => w.width), [words]);
  const titleWidth = useDerivedValue(() => {
    if (!morphing.get()) return restWidths[listOn.get()] ?? 0;
    const e = morphT.get();
    let t: number;
    if (reduced) t = clamp01(e / FADE);
    else {
      const total = morphTotal.get();
      t = easeInOutCubic(clamp01((e - 0.08) / (total - 0.08)));
    }
    return fromWidth.get() + (toWidth.get() - fromWidth.get()) * t;
  }, [restWidths]);

  const titleBoxStyle = useAnimatedStyle(() => ({ width: titleWidth.get() + CHEVRON_GAP + CHEVRON_SIZE }));
  const chevronStyle = useAnimatedStyle(() => ({ transform: [{ translateX: titleWidth.get() + CHEVRON_GAP }] }));
  const headerY = insets.top + HEADER_TOP;
  const pillTop = Math.max(0, BASELINE - capHeight / 2 - 23);

  if (!font) {
    return (
      <View style={styles.screen}>
        <StatusBar style="dark" />
      </View>
    );
  }

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />

      {/* The lists, cross-fading. */}
      <View style={[styles.lists, { top: headerY + TITLE_BOX + SUBTITLE_TOP }]}>
        {pages.map((page, i) => (
          <PageView key={page.title} page={page} index={i} on={selected === i} shownOn={listOn} shownOff={listOff} fade={listFade} reduced={reduced} />
        ))}
      </View>

      {/* The header: the title (a menu) and the search pill. */}
      <View style={[styles.header, { top: headerY }]}>
        <Animated.View style={[styles.titleBox, titleBoxStyle]}>
          {/* The letters, on a canvas larger than the title so they can fall, rise and blur. */}
          <Canvas style={styles.titleCanvas} pointerEvents="none">
            {font ? (
              <Letters
                font={font}
                words={words}
                current={listOn}
                from={morphFrom}
                to={morphTo}
                morphing={morphing}
                clock={morphT}
                reduced={reduced}
                capHeight={capHeight}
              />
            ) : null}
          </Canvas>
          <Animated.View style={[styles.chevron, { top: BASELINE - capHeight / 2 - CHEVRON_SIZE / 2 }, chevronStyle]}>
            <Icon kind="chevrons" color={C.chevron} size={CHEVRON_SIZE} />
          </Animated.View>
          <Pressable
            style={StyleSheet.absoluteFill}
            accessibilityRole="button"
            accessibilityLabel={pages[selected].title}
            accessibilityHint="Shows the lists"
            onPress={() => {
              touched();
              if (menuOpen) closeMenu();
              else {
                tick();
                openMenu();
              }
            }}
          />
        </Animated.View>
        <View style={{ flex: 1 }} />
        <View style={[styles.search, { marginTop: pillTop }]}>
          <Text style={styles.searchLabel} numberOfLines={1}>
            {searchLabel}
          </Text>
          <Icon kind="search" color={C.icon} size={19} />
        </View>
      </View>

      {menuOpen ? (
        <Pressable
          style={StyleSheet.absoluteFill}
          onPress={() => {
            touched();
            closeMenu();
          }}
        />
      ) : null}

      {/* The popover: drawn on a canvas so it can grow, fade and sharpen as one layer, like the SwiftUI
          version's blur; the rows above it only take the touches. */}
      <View
        style={[styles.popoverCanvasWrap, { top: headerY + TITLE_BOX + MENU_GAP - MENU_MARGIN }]}
        pointerEvents="none"
      >
        <PopoverArt labels={pages.map((p) => p.label)} menu={menu} highlight={highlight} reduced={reduced} />
      </View>
      <View
        style={[styles.popover, { top: headerY + TITLE_BOX + MENU_GAP }]}
        pointerEvents={menuOpen ? 'auto' : 'none'}
        accessibilityElementsHidden={!menuOpen}
        importantForAccessibility={menuOpen ? 'auto' : 'no-hide-descendants'}
      >
        {pages.map((page, i) => (
          <Pressable
            key={page.label}
            style={styles.menuRow}
            accessibilityRole="button"
            accessibilityLabel={page.label}
            accessibilityState={{ selected: selected === i }}
            onPress={() => {
              touched();
              tick();
              pick(i);
            }}
          />
        ))}
      </View>

      {/* The mini player. */}
      <View style={[styles.playerWrap, { bottom: insets.bottom + 8 }]} pointerEvents="none">
        <MiniPlayer now={pages[0].episodes[0]} />
      </View>
    </View>
  );
}

const HIDDEN: Pose = { dy: 0, scale: 1, blur: 0, opacity: 0 };

/**
 * The title's letters, recorded into one picture per frame on the UI thread from the shared values: every
 * letter of every word, at rest, going, coming or hidden. Each letter is drawn in its own layer that
 * carries its opacity and blur, then moved and scaled about its centre (half its advance, half the cap
 * height), as in the SwiftUI version (the blur is not scaled with the letter).
 */
function Letters({
  font,
  words,
  current,
  from,
  to,
  morphing,
  clock,
  reduced,
  capHeight,
}: {
  font: SkFont;
  words: Word[];
  current: SharedValue<number>;
  from: SharedValue<number>;
  to: SharedValue<number>;
  morphing: SharedValue<boolean>;
  clock: SharedValue<number>;
  reduced: boolean;
  capHeight: number;
}) {
  const glyphs = useMemo(
    () => words.map((word) => ({ chars: [...word.text], xs: word.xs, advances: word.advances })),
    [words],
  );
  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, CANVAS_WIDTH, TITLE_BOX + 2 * CANVAS_PAD));
    const on = morphing.get();
    const e = clock.get();
    const cy = CANVAS_PAD + BASELINE - capHeight / 2;
    const ink = Skia.Color(C.ink);
    const plain = Skia.Paint();
    plain.setColor(ink);
    for (let w = 0; w < glyphs.length; w++) {
      const going = on && from.get() === w;
      const coming = on && to.get() === w;
      const resting = !on && current.get() === w;
      if (!going && !coming && !resting) continue;
      const g = glyphs[w];
      for (let i = 0; i < g.chars.length; i++) {
        const start = i * STAGGER;
        let pose: Pose = REST;
        if (!resting) {
          if (reduced) {
            const p = clamp01(e / FADE);
            pose = { dy: 0, scale: 1, blur: 0, opacity: going ? 1 - p : p };
          } else if (going) {
            const p = clamp01((e - start) / OUT_DURATION);
            pose = p < 1 ? poseOut(p, TITLE_SIZE) : HIDDEN;
          } else {
            const q = clamp01((e - start - IN_DELAY) / IN_DURATION);
            pose = q > 0 ? poseIn(q, TITLE_SIZE) : HIDDEN;
          }
        }
        if (pose.opacity <= 0.001) continue;
        const half = g.advances[i] / 2;
        const layer = Skia.Paint();
        layer.setAlphaf(pose.opacity);
        if (pose.blur > 0.05) layer.setImageFilter(Skia.ImageFilter.MakeBlur(pose.blur, pose.blur, TileMode.Decal, null));
        canvas.saveLayer(layer);
        canvas.translate(CANVAS_PAD + g.xs[i] + half, cy + pose.dy);
        canvas.scale(pose.scale, pose.scale);
        canvas.translate(-half, capHeight / 2);
        canvas.drawText(g.chars[i], 0, 0, plain, font);
        canvas.restore();
      }
    }
    return recorder.finishRecordingAsPicture();
  });
  return <Picture picture={picture} />;
}

/**
 * The popover's look: a white card with its shadow, the highlight pill and its check, and the labels.
 * It scales from its top-left corner (12 % in), fades and sharpens from a blur of 10 as one layer.
 */
function PopoverArt({
  labels,
  menu,
  highlight,
  reduced,
}: {
  labels: string[];
  menu: SharedValue<number>;
  highlight: SharedValue<number>;
  reduced: boolean;
}) {
  const font = useMemo(() => matchFont({ fontFamily: SYSTEM_FONT, fontSize: 16, fontWeight: '500' }), []);
  const metrics = useMemo(() => font.getMetrics(), [font]);
  const height = 2 * MENU_INSET + labels.length * MENU_ROW;
  const ox = MENU_MARGIN + 0.12 * MENU_WIDTH;
  const oy = MENU_MARGIN;
  const pillWidth = MENU_WIDTH - 2 * MENU_INSET;
  const checkX = MENU_MARGIN + MENU_INSET + pillWidth - 11 - 10.5;
  const transform = useDerivedValue(() => {
    const s = reduced ? 1 : 0.45 + 0.55 * menu.get();
    return [{ translateX: ox }, { translateY: oy }, { scale: s }, { translateX: -ox }, { translateY: -oy }];
  });
  const opacity = useDerivedValue(() => clamp01(reduced ? menu.get() : menu.get() * 1.8));
  const blur = useDerivedValue(() => (reduced ? 0 : Math.max(0, 1 - menu.get()) * MENU_BLUR));
  const pillY = useDerivedValue(() => MENU_MARGIN + MENU_INSET + highlight.get() * MENU_ROW);
  const checkY = useDerivedValue(() => pillY.get() + MENU_ROW / 2);
  const checkTransform = useDerivedValue(() => [
    { translateX: checkX - 7 },
    { translateY: checkY.get() - 7 },
    { scale: 14 / 24 },
  ]);
  return (
    <Canvas style={{ width: MENU_WIDTH + 2 * MENU_MARGIN, height: height + 2 * MENU_MARGIN }}>
      <Group
        transform={transform}
        layer={
          <Paint opacity={opacity}>
            <Blur blur={blur} mode="decal" />
          </Paint>
        }
      >
        <RoundedRect x={MENU_MARGIN} y={MENU_MARGIN} width={MENU_WIDTH} height={height} r={MENU_RADIUS} color="#FFFFFF">
          <Shadow dx={0} dy={16} blur={17} color="rgba(0,0,0,0.13)" />
          <Shadow dx={0} dy={1} blur={1} color="rgba(0,0,0,0.06)" />
        </RoundedRect>
        <RoundedRect x={MENU_MARGIN + MENU_INSET} y={pillY} width={pillWidth} height={MENU_ROW} r={MENU_RADIUS - MENU_INSET} color={C.pill} />
        <SkiaCircle cx={checkX} cy={checkY} r={10.5} color={C.ink} />
        <Group transform={checkTransform}>
          <SkiaPath path="M6.5 12.5 L10.2 16 L17.5 8.5" style="stroke" strokeWidth={2.8} strokeCap="round" strokeJoin="round" color="#FFFFFF" />
        </Group>
        {labels.map((label, i) => (
          <SkiaText
            key={label}
            x={MENU_MARGIN + MENU_INSET + 14}
            y={MENU_MARGIN + MENU_INSET + i * MENU_ROW + MENU_ROW / 2 - (metrics.ascent + metrics.descent) / 2}
            text={label}
            font={font}
            color={C.ink2}
          />
        ))}
      </Group>
    </Canvas>
  );
}

/**
 * One list. At rest it is the native views; while the lists cross-fade it is drawn from a snapshot on a
 * canvas, so it can blur (6 when hidden) and drop 8 pt like the SwiftUI version, which a native view cannot
 * do on iOS. Until the snapshot exists, the native view fades alone.
 */
function PageView({
  page,
  index,
  on,
  shownOn,
  shownOff,
  fade,
  reduced,
}: {
  page: MorphingTitleMenuPage;
  index: number;
  on: boolean;
  shownOn: SharedValue<number>;
  shownOff: SharedValue<number>;
  fade: SharedValue<number>;
  reduced: boolean;
}) {
  // How much of this list shows: 1 shown, 0 hidden, in between while the lists cross-fade.
  const shown = useDerivedValue(() => (shownOn.get() === index ? fade.get() : shownOff.get() === index ? 1 - fade.get() : 0));
  const ready = useSharedValue(false);
  const content = useRef<View>(null);
  const [snap, setSnap] = useState<{ image: SkImage; width: number; height: number } | null>(null);
  const captureTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(
    () => () => {
      if (captureTimer.current) clearTimeout(captureTimer.current);
    },
    [],
  );
  const capture = (width: number, height: number) => {
    if (captureTimer.current) clearTimeout(captureTimer.current);
    // Let the rows (gradients, icons) draw first.
    captureTimer.current = setTimeout(() => {
      makeImageFromView(content)
        .then((image) => {
          if (!image) return;
          setSnap({ image, width, height });
          ready.set(true);
        })
        .catch(() => {});
    }, 300);
  };
  const style = useAnimatedStyle(() => {
    const s = shown.get();
    if (ready.get()) return { opacity: s >= 1 ? 1 : 0, transform: [{ translateY: 0 }] };
    return { opacity: s, transform: [{ translateY: reduced ? 0 : (1 - s) * 8 }] };
  });
  const layerOpacity = useDerivedValue(() => {
    const s = shown.get();
    return s > 0 && s < 1 ? s : 0;
  });
  const layerBlur = useDerivedValue(() => (reduced ? 0 : (1 - shown.get()) * LIST_BLUR));
  const layerTransform = useDerivedValue(() => [{ translateY: reduced ? 0 : (1 - shown.get()) * 8 }]);
  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="box-none">
      <Animated.View
        style={[StyleSheet.absoluteFill, style]}
        pointerEvents={on ? 'auto' : 'none'}
        accessibilityElementsHidden={!on}
        importantForAccessibility={on ? 'auto' : 'no-hide-descendants'}
      >
        <View
          ref={content}
          collapsable={false}
          onLayout={(e) => capture(e.nativeEvent.layout.width, e.nativeEvent.layout.height)}
        >
          <Text style={styles.subtitle} numberOfLines={1}>
            {page.subtitle}
          </Text>
          <View style={{ marginTop: LIST_TOP, gap: EPISODE_GAP }}>
            {page.episodes.map((e) => (
              <EpisodeRow key={e.title} episode={e} />
            ))}
          </View>
        </View>
      </Animated.View>
      {snap ? (
        <Canvas
          style={{ position: 'absolute', left: -LIST_PAD, top: -LIST_PAD, width: snap.width + 2 * LIST_PAD, height: snap.height + 2 * LIST_PAD }}
          pointerEvents="none"
        >
          <Group
            transform={layerTransform}
            layer={
              <Paint opacity={layerOpacity}>
                <Blur blur={layerBlur} mode="decal" />
              </Paint>
            }
          >
            <SkiaImage image={snap.image} x={LIST_PAD} y={LIST_PAD} width={snap.width} height={snap.height} fit="fill" />
          </Group>
        </Canvas>
      ) : null}
    </View>
  );
}

function EpisodeRow({ episode }: { episode: MorphingTitleMenuEpisode }) {
  return (
    <View accessible>
      <View style={styles.meta}>
        <Art colors={episode.art} mark={episode.mark} size={20} radius={5} />
        <Text style={styles.show} numberOfLines={1}>
          {episode.show}
        </Text>
        <Text style={styles.when} numberOfLines={1}>
          {episode.when}
        </Text>
        <View style={styles.saved}>
          <Icon kind="check" color="#FFFFFF" size={11} />
        </View>
      </View>
      <Text style={styles.episodeTitle} numberOfLines={3}>
        {episode.title}
      </Text>
      {episode.progress != null ? (
        <View style={styles.track}>
          <View style={[styles.fill, { width: `${episode.progress * 100}%` }]} />
        </View>
      ) : null}
      <View style={styles.actions}>
        <View style={styles.playChip}>
          <Icon kind={episode.playing ? 'pause' : 'play'} color={C.ink} size={15} />
          <Text style={styles.playVerb} numberOfLines={1}>
            {episode.playing ? 'Pause' : 'Play'}
          </Text>
          <Text style={styles.playLength} numberOfLines={1}>
            {episode.length}
          </Text>
        </View>
        <View style={styles.roundChip}>
          <Icon kind="archive" color={C.icon} size={18} />
        </View>
        <View style={styles.dots}>
          <Icon kind="dots" color={C.faint} size={20} />
        </View>
      </View>
    </View>
  );
}

function MiniPlayer({ now }: { now: MorphingTitleMenuEpisode }) {
  return (
    <View style={styles.player}>
      <Art colors={now.art} mark={now.mark} size={44} radius={12} />
      <View style={{ flex: 1, gap: 2 }}>
        <Text style={styles.playerTitle} numberOfLines={1}>
          {now.title}
        </Text>
        <Text style={styles.playerSub} numberOfLines={1}>
          {`${now.show} · ${now.length} left`}
        </Text>
      </View>
      <View style={styles.playerButton}>
        <Icon kind="pause" color="#FFFFFF" size={16} />
      </View>
    </View>
  );
}

/** A show's cover: a two-colour square with its initial. */
function Art({ colors, mark, size, radius }: { colors: [string, string]; mark: string; size: number; radius: number }) {
  return (
    <LinearGradient
      colors={colors}
      start={{ x: 0, y: 0 }}
      end={{ x: 1, y: 1 }}
      style={{ width: size, height: size, borderRadius: radius, alignItems: 'center', justifyContent: 'center', borderCurve: 'continuous' }}
    >
      <Text style={{ fontSize: size * 0.46, fontWeight: '800', color: 'rgba(255,255,255,0.92)' }}>{mark}</Text>
    </LinearGradient>
  );
}

type IconKind = 'chevrons' | 'search' | 'check' | 'pause' | 'play' | 'archive' | 'dots';

/** The few icons of the screen, drawn in a 24-unit box, as in the other stacks. */
function Icon({ kind, color, size }: { kind: IconKind; color: string; size: number }) {
  const line = { stroke: color, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const, fill: 'none' };
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      {kind === 'chevrons' ? <Path d="M6.5 9 L12 3.5 L17.5 9 M6.5 15 L12 20.5 L17.5 15" strokeWidth={2.4} {...line} /> : null}
      {kind === 'search' ? (
        <>
          <Circle cx={10.5} cy={10.5} r={6.25} strokeWidth={2.3} {...line} />
          <Path d="M15.3 15.3 L20 20" strokeWidth={2.3} {...line} />
        </>
      ) : null}
      {kind === 'check' ? <Path d="M6.5 12.5 L10.2 16 L17.5 8.5" strokeWidth={2.8} {...line} /> : null}
      {kind === 'pause' ? (
        <>
          <Rect x={6} y={4.5} width={4} height={15} rx={1.3} fill={color} />
          <Rect x={14} y={4.5} width={4} height={15} rx={1.3} fill={color} />
        </>
      ) : null}
      {kind === 'play' ? <Path d="M7 4.8 L19.2 12 L7 19.2 Z" fill={color} stroke={color} strokeWidth={1.5} strokeLinejoin="round" /> : null}
      {kind === 'archive' ? (
        <>
          <Rect x={3.5} y={4.5} width={17} height={4.5} rx={1.4} strokeWidth={1.8} {...line} />
          <Path d="M5 9 V18 Q5 19.5 6.5 19.5 H17.5 Q19 19.5 19 18 V9 M10 12.8 H14" strokeWidth={1.8} {...line} />
        </>
      ) : null}
      {kind === 'dots' ? (
        <>
          <Circle cx={5} cy={12} r={1.7} fill={color} />
          <Circle cx={12} cy={12} r={1.7} fill={color} />
          <Circle cx={19} cy={12} r={1.7} fill={color} />
        </>
      ) : null}
    </Svg>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: C.background },
  lists: { position: 'absolute', left: SIDE, right: SIDE, bottom: 0 },
  header: { position: 'absolute', left: SIDE, right: SIDE, flexDirection: 'row', alignItems: 'flex-start' },
  titleBox: { height: TITLE_BOX },
  titleCanvas: {
    position: 'absolute',
    left: -CANVAS_PAD,
    top: -CANVAS_PAD,
    width: CANVAS_WIDTH,
    height: TITLE_BOX + 2 * CANVAS_PAD,
  },
  chevron: { position: 'absolute', left: 0, width: CHEVRON_SIZE, height: CHEVRON_SIZE },
  search: {
    height: 46,
    paddingHorizontal: 18,
    borderRadius: 23,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    backgroundColor: '#FAF9FA',
    borderWidth: 1,
    borderColor: '#FFFFFF',
    boxShadow: '0 5px 12px rgba(0,0,0,0.07), 0 0.5px 1px rgba(0,0,0,0.05)',
  },
  searchLabel: { fontSize: 17, fontWeight: '500', color: C.icon },
  popoverCanvasWrap: { position: 'absolute', left: MENU_X - MENU_MARGIN },
  popover: { position: 'absolute', left: MENU_X, width: MENU_WIDTH, padding: MENU_INSET },
  menuRow: { height: MENU_ROW, justifyContent: 'center', paddingLeft: 14 },
  subtitle: { fontSize: 17, fontWeight: '500', color: C.mute },
  meta: { flexDirection: 'row', alignItems: 'center', gap: 7 },
  show: { fontSize: 13, fontWeight: '600', color: C.ink2, flexShrink: 1 },
  when: { fontSize: 13, fontWeight: '500', color: C.faint },
  saved: { width: 15, height: 15, borderRadius: 4, backgroundColor: '#DDDBDF', alignItems: 'center', justifyContent: 'center' },
  episodeTitle: { marginTop: 9, fontSize: 18, fontWeight: '600', color: C.ink },
  track: { marginTop: 13, height: 3, borderRadius: 1.5, backgroundColor: C.track, overflow: 'hidden' },
  fill: { height: 3, borderRadius: 1.5, backgroundColor: C.fill },
  actions: { marginTop: 13, flexDirection: 'row', alignItems: 'center', gap: 10 },
  playChip: { height: 36, paddingHorizontal: 14, borderRadius: 18, backgroundColor: C.chip, flexDirection: 'row', alignItems: 'center', gap: 7 },
  playVerb: { fontSize: 15, fontWeight: '600', color: C.ink },
  playLength: { fontSize: 15, fontWeight: '500', color: C.mute },
  roundChip: { width: 36, height: 36, borderRadius: 18, backgroundColor: C.chip, alignItems: 'center', justifyContent: 'center' },
  dots: { width: 30, height: 36, alignItems: 'center', justifyContent: 'center' },
  playerWrap: { position: 'absolute', left: 16, right: 16 },
  player: {
    height: 64,
    borderRadius: 32,
    backgroundColor: '#FFFFFF',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingLeft: 10,
    paddingRight: 11,
    boxShadow: '0 10px 22px rgba(0,0,0,0.10), 0 0.5px 1px rgba(0,0,0,0.05)',
  },
  playerTitle: { fontSize: 15, fontWeight: '600', color: C.ink },
  playerSub: { fontSize: 13, fontWeight: '500', color: C.mute },
  playerButton: { width: 42, height: 42, borderRadius: 21, backgroundColor: C.ink, alignItems: 'center', justifyContent: 'center' },
});
