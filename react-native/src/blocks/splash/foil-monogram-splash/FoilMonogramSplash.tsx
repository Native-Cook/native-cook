import {
  Canvas,
  Group,
  LinearGradient as SkiaGradient,
  matchFont,
  Path,
  RadialGradient,
  Rect,
  type SkFont,
  Text as SkiaText,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { SymbolView } from 'expo-symbols';
import { type ReactNode, useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { CROSSINGS, GAP, KNOT_HEIGHT, KNOT_WIDTH, makeArch, makeLoop, OVER_SPAN, STROKE } from './foilMonogramGeometry';

type Props = {
  /** Plays the splash, hands off to the app, closes and starts over, every LOOP s. */
  autoPlay?: boolean;
  onReserve?: () => void;
};

// Colours: espresso splash, gold foil, a cream app.
const SPLASH = '#130d0a';
const SPLASH_LIGHT = '#2a1e16';
const RIM = '#d8b77e';
const CREAM = '#f4ede3';
const INK = '#1f1812';
const MUTED = '#8a7a68';
const CARD = '#e7dccd';
const FOIL = ['#6f4b29', '#c9a167', '#f4e2b6', '#b8894e', '#e3c68e', '#8a6236', '#5e3f22'];
const FOIL_AT = [0, 0.22, 0.38, 0.55, 0.7, 0.86, 1];
const FOIL_EDGE = '#4e341b';
const SHINE = ['rgba(255,248,230,0)', 'rgba(255,248,230,0.78)', 'rgba(255,248,230,0)'];
const SERIF = process.env.EXPO_OS === 'ios' ? 'Georgia' : 'serif';

const WORD = 'LOAVEN';
const WORD_SIZE = 34; // pt
const WORD_TRACK = 11; // pt between letters
const TAGLINE = 'HOLLIS STREET BAKEHOUSE';

// The script, in seconds. It starts from a bare splash and comes back to it, so a recording of one
// LOOP loops cleanly.
const LOOP = 9;
const DRAW = [
  { at: 0.3, time: 1.4 }, // the right-leaning loop draws on from its top tip
  { at: 0.55, time: 1.4 }, // then the left-leaning one
];
const SWEEP_AT = 1.95; // a light sweeps across the foil
const SWEEP_TIME = 0.95;
const SWEEP_REACH = 190; // pt each side of the centre the light travels
const LETTERS_AT = 1.5; // the wordmark rises through its baseline, letter by letter
const LETTER_STAGGER = 0.07;
const LETTER_IN = 0.6;
const TAG_AT = 2.25;
const TAG_IN = 0.5;
const HANDOFF_AT = 3.9; // the letters drop out upwards, the knot flies to the header, the arch opens
const LETTER_OUT = 0.36;
const LETTER_OUT_STAGGER = 0.035;
const MOVE_AT = 3.98;
const MOVE_TIME = 0.9;
const ARCH_AT = 4.05;
const ARCH_TIME = 0.9;
const APP_AT = 4.4; // the app's content rises in
const APP_STAGGER = 0.07;
const APP_IN = 0.7;
const APP_RISE = 18; // pt
const APP_REST = 6; // without autoPlay the script holds here, on the app
const CLOSE_AT = 8; // the arch closes over the app and the header knot fades
const CLOSE_TIME = 0.8;
const KNOT_OUT = 0.3;
const RIM_FADE = 24; // pt of doorway radius over which its gold rim fades in
const CORE_OVERLAP = 0.012; // share of a loop the gold core of a woven-over patch runs past its cut
const SKIP_SPEED = 3.5; // a tap on the splash plays the rest of it this much faster
const RESUME_AFTER = 1.5; // s of rest after a touch before autoPlay moves on

// Size and place: the knot is MARK_SCALE times its units on the splash, HEADER_MARK pt tall in the app.
const MARK_SCALE = 1.15;
const HEADER_MARK = 36;
const HEADER_LEFT = 20;

// Curves.
const EASE_OUT: Curve = [0.23, 1, 0.32, 1];
const EASE_IN_OUT: Curve = [0.65, 0, 0.35, 1];
const TRANSITION: Curve = [0.77, 0, 0.175, 1];

type Curve = [number, number, number, number];

const PHOTO = (id: string, w: number) => `https://images.unsplash.com/${id}?auto=format&fit=crop&w=${w}&q=80`;
const HERO = PHOTO('photo-1567042661848-7161ce446f85', 1000);
const SHELF = [
  { name: 'Seeded rye', price: '$8', uri: PHOTO('photo-1509440159596-0249088772ff', 560) },
  { name: 'Twin baguettes', price: '$6', uri: PHOTO('photo-1622808516114-02a5749cd965', 560) },
];

/** Cubic bezier easing, as in CSS: solves x for `t` by Newton steps, then returns y. */
function bezier(curve: Curve, t: number) {
  'worklet';
  const [x1, y1, x2, y2] = curve;
  if (t <= 0) return 0;
  if (t >= 1) return 1;
  let u = t;
  for (let i = 0; i < 8; i++) {
    const x = 3 * (1 - u) * (1 - u) * u * x1 + 3 * (1 - u) * u * u * x2 + u * u * u - t;
    const dx = 3 * (1 - u) * (1 - u) * x1 + 6 * (1 - u) * u * (x2 - x1) + 3 * u * u * (1 - x2);
    if (Math.abs(x) < 1e-5 || Math.abs(dx) < 1e-6) break;
    u = Math.min(1, Math.max(0, u - x / dx));
  }
  return 3 * (1 - u) * (1 - u) * u * y1 + 3 * (1 - u) * u * u * y2 + u * u * u;
}

/** How far `t` is through a step that starts `at` and lasts `time`, from 0 to 1. */
function step(t: number, at: number, time: number) {
  'worklet';
  return Math.min(1, Math.max(0, (t - at) / time));
}

/** How far a loop of the knot is drawn. */
function drawn(t: number, loop: number, reduced: boolean) {
  'worklet';
  if (reduced) return t >= DRAW[0].at ? 1 : 0;
  return bezier(EASE_IN_OUT, step(t, DRAW[loop].at, DRAW[loop].time));
}

/** The knot's own fade: in under reduced motion, out at the end of the loop. */
function knotOpacity(t: number, reduced: boolean) {
  'worklet';
  const enter = reduced ? step(t, DRAW[0].at, 0.5) : 1;
  const leave = t >= CLOSE_AT ? 1 - step(t, CLOSE_AT, KNOT_OUT) : 1;
  const swap = reduced && t >= HANDOFF_AT && t < CLOSE_AT ? Math.abs(1 - 2 * step(t, HANDOFF_AT, 0.8)) : 1;
  return enter * leave * swap;
}

/** How far the knot has flown to the header: 0 on the splash, 1 in the app. */
function moved(t: number, reduced: boolean) {
  'worklet';
  if (reduced) return t >= HANDOFF_AT + 0.4 ? 1 : 0;
  return bezier(TRANSITION, step(t, MOVE_AT, MOVE_TIME));
}

/** How far the arch has opened onto the app. */
function opened(t: number) {
  'worklet';
  if (t >= CLOSE_AT) return 1 - bezier(TRANSITION, step(t, CLOSE_AT, CLOSE_TIME));
  return bezier(TRANSITION, step(t, ARCH_AT, ARCH_TIME));
}

/** Where the scripted light is, -0.25 (off to the left) to 1.25 (off to the right). */
function sweep(t: number) {
  'worklet';
  return -0.25 + 1.5 * bezier(EASE_IN_OUT, step(t, SWEEP_AT, SWEEP_TIME));
}

/** A letter's drop below (positive) or above (negative) its baseline, as a share of the reveal. */
function letterShift(t: number, i: number) {
  'worklet';
  const enter = bezier(EASE_OUT, step(t, LETTERS_AT + i * LETTER_STAGGER, LETTER_IN));
  const leave = bezier(TRANSITION, step(t, HANDOFF_AT + i * LETTER_OUT_STAGGER, LETTER_OUT));
  return 1 - enter - leave;
}

/** An app element's entrance, 0 to 1. */
function appIn(t: number, k: number) {
  'worklet';
  return bezier(EASE_OUT, step(t, APP_AT + k * APP_STAGGER, APP_IN));
}

/**
 * A splash for an invented bakery, Loaven. On espresso, a knot of two gold-foil loops draws on
 * stroke by stroke, woven over and under; a light sweeps across the foil while the wordmark rises
 * through its baseline letter by letter. Then the letters drop away, the knot flies up into the
 * app's header and an arched doorway opens from the middle onto the app.
 */
export function FoilMonogramSplash({ autoPlay = false, onReserve }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [interactive, setInteractive] = useState(false);

  const width = size?.width ?? 0;
  const height = size?.height ?? 0;

  const loops = useMemo(() => [makeLoop(1), makeLoop(-1)] as const, []);
  const arch = useMemo(() => makeArch(false), []);
  const archRim = useMemo(() => makeArch(true), []);
  const font = useMemo<SkFont>(() => matchFont({ fontFamily: SERIF, fontSize: WORD_SIZE, fontWeight: 'bold' }), []);

  // The wordmark's letters: each one's x and width, laid out with wide tracking.
  const letters = useMemo(() => {
    const widths = WORD.split('').map((ch) => {
      const w = font.getTextWidth(ch);
      return Number.isFinite(w) && w > 0 ? w : WORD_SIZE * 0.7;
    });
    const total = widths.reduce((sum, w) => sum + w, 0) + WORD_TRACK * (WORD.length - 1);
    return widths.map((w, i) => {
      const before = widths.slice(0, i).reduce((sum, v) => sum + v + WORD_TRACK, 0);
      return { ch: WORD[i], x: -total / 2 + before, width: w };
    });
  }, [font]);

  // Where things sit on this screen.
  const cx = width / 2;
  const cy = height * 0.43;
  const headerScale = HEADER_MARK / KNOT_HEIGHT;
  const hx = HEADER_LEFT + (KNOT_WIDTH * headerScale) / 2;
  const hy = insets.top + 10 + HEADER_MARK / 2;
  const baseline = cy + (KNOT_HEIGHT * MARK_SCALE) / 2 + 74;
  const archReach = Math.hypot(Math.max(cx, width - cx), cy) + 40;

  const clock = useSharedValue(0);
  const speed = useSharedValue(1);
  const pause = useSharedValue(0);
  const down = useSharedValue(false);
  const looping = useSharedValue(auto);
  const lightAt = useSharedValue(0.5);
  const follow = useSharedValue(0);

  useEffect(() => {
    looping.set(auto);
  }, [auto, looping]);

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 50) / 1000;
    if (!(dt > 0) || down.get()) return;
    if (pause.get() > 0) {
      pause.set(Math.max(0, pause.get() - dt));
      return;
    }
    const before = clock.get();
    // Without autoPlay the app stays once it is open; a replay jumps to the close.
    if (!looping.get() && before >= APP_REST && before < CLOSE_AT) return;
    let t = before + dt * speed.get();
    if (before < HANDOFF_AT && t >= HANDOFF_AT) speed.set(1);
    if (!looping.get() && before < APP_REST && t > APP_REST) t = APP_REST;
    if (t >= LOOP) t -= LOOP;
    clock.set(Number.isFinite(t) ? t : 0);
  });

  // The app takes touches only while it is open.
  useAnimatedReaction(
    () => clock.get() >= ARCH_AT + ARCH_TIME * 0.7 && clock.get() < CLOSE_AT,
    (open, was) => {
      if (open !== was) scheduleOnRN(setInteractive, open);
    },
  );

  const tick = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const select = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };

  // A tap on the splash plays the rest of it faster; a drag moves the light across the foil.
  const tap = Gesture.Tap()
    .maxDuration(400)
    .onEnd((_event, success) => {
      if (!success || clock.get() >= HANDOFF_AT) return;
      if (reduced) clock.set(HANDOFF_AT);
      else speed.set(SKIP_SPEED);
      pause.set(0);
      scheduleOnRN(tick);
    });
  const pan = Gesture.Pan()
    .maxPointers(1)
    .minDistance(6)
    .onStart((event) => {
      if (clock.get() >= HANDOFF_AT) return;
      down.set(true);
      lightAt.set(0.5 + (event.x - cx) / (2 * SWEEP_REACH));
      follow.set(withSpring(1, { duration: 300, dampingRatio: 1 }));
    })
    .onUpdate((event) => {
      if (!down.get()) return;
      lightAt.set(0.5 + (event.x - cx) / (2 * SWEEP_REACH));
    })
    .onFinalize(() => {
      if (!down.get()) return;
      down.set(false);
      follow.set(withTiming(0, { duration: 700 }));
      pause.set(looping.get() ? RESUME_AFTER : 0);
    });
  const gesture = Gesture.Race(pan, tap);

  const replay = () => {
    clock.set(CLOSE_AT);
    speed.set(1);
    pause.set(0);
    tick();
  };
  const holdApp = () => {
    if (looping.get()) pause.set(Math.max(pause.get(), 3));
  };

  // The light's position in screen x, from the script or the finger.
  const shineX = useDerivedValue(() => {
    const f = follow.get();
    const at = sweep(clock.get()) * (1 - f) + Math.min(1.25, Math.max(-0.25, lightAt.get())) * f;
    return cx + (at * 2 - 1) * SWEEP_REACH;
  });

  // The knot.
  const markTransform = useDerivedValue(() => {
    const m = moved(clock.get(), reduced);
    const s = MARK_SCALE + (headerScale - MARK_SCALE) * m;
    return [{ translateX: cx + (hx - cx) * m }, { translateY: cy + (hy - cy) * m }, { scale: s }];
  });
  const markOpacity = useDerivedValue(() => knotOpacity(clock.get(), reduced));
  // The light in the knot's own units.
  const markShine = useDerivedValue(() => {
    const m = moved(clock.get(), reduced);
    const s = MARK_SCALE + (headerScale - MARK_SCALE) * m;
    const x = (shineX.get() - (cx + (hx - cx) * m)) / s;
    return { start: { x: x - 46, y: -34 }, end: { x: x + 46, y: 34 } };
  });
  const markShineStart = useDerivedValue(() => markShine.get().start);
  const markShineEnd = useDerivedValue(() => markShine.get().end);

  // The splash with its arched doorway.
  const archTransform = useDerivedValue(() => [
    { translateX: cx },
    { translateY: cy },
    { scale: Math.max(0.001, opened(clock.get()) * archReach) },
  ]);
  const splashOpacity = useDerivedValue(() => {
    const t = clock.get();
    if (!reduced) return opened(t) >= 0.999 ? 0 : 1;
    if (t >= CLOSE_AT) return step(t, CLOSE_AT, 0.45);
    return 1 - step(t, ARCH_AT, 0.45);
  });
  const archOpen = useDerivedValue(() => (reduced ? 0.001 : Math.max(0.001, opened(clock.get()) * archReach)));
  const rimOpacity = useDerivedValue(() => {
    const o = opened(clock.get());
    // Faded out while the doorway is still a speck, so it never shows as a dot.
    return reduced || o <= 0 || o >= 1 ? 0 : 0.85 * Math.sin(Math.PI * o) * Math.min(1, (o * archReach) / RIM_FADE);
  });
  const rimWidth = useDerivedValue(() => 1.4 / archOpen.get());

  // The wordmark's light, in screen space.
  const wordShineStart = useDerivedValue(() => ({ x: shineX.get() - 46, y: baseline - 34 }));
  const wordShineEnd = useDerivedValue(() => ({ x: shineX.get() + 46, y: baseline + 34 }));

  const tagStyle = useAnimatedStyle(() => {
    const t = clock.get();
    const enter = bezier(EASE_OUT, step(t, TAG_AT, TAG_IN));
    const leave = step(t, HANDOFF_AT, 0.25);
    return {
      opacity: enter * (1 - leave),
      transform: [{ translateY: reduced ? 0 : (1 - enter) * 6 }],
    };
  });

  return (
    <GestureDetector gesture={gesture}>
      <View
        style={styles.screen}
        onLayout={(event: LayoutChangeEvent) => {
          const { width: w, height: h } = event.nativeEvent.layout;
          if (w > 0 && h > 0) setSize({ width: w, height: h });
        }}
      >
        {size && (
          <AppSurface
            clock={clock}
            reduced={reduced}
            interactive={interactive}
            insets={insets}
            height={height}
            onReplay={replay}
            onTouch={holdApp}
            onReserve={() => {
              select();
              onReserve?.();
            }}
          />
        )}

        {size && (
          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            {/* The splash, with the arch cut out of it as it opens. */}
            <Group layer opacity={splashOpacity}>
              <Rect x={0} y={0} width={width} height={height}>
                <RadialGradient c={vec(cx, cy)} r={height * 0.75} colors={[SPLASH_LIGHT, SPLASH]} />
              </Rect>
              <Group transform={archTransform}>
                <Path path={arch} blendMode="dstOut" color="black" />
              </Group>
            </Group>
            <Group transform={archTransform} opacity={rimOpacity}>
              <Path path={archRim} style="stroke" strokeWidth={rimWidth} color={RIM} />
            </Group>

            {/* The wordmark, each letter rising through a slot at its baseline. */}
            <Group layer>
              {letters.map((letter, i) => (
                <Letter key={i} index={i} clock={clock} font={font} x={cx + letter.x} width={letter.width} baseline={baseline} char={letter.ch} />
              ))}
              <Rect x={0} y={baseline - WORD_SIZE * 1.2} width={width} height={WORD_SIZE * 1.6} blendMode="srcATop">
                <SkiaGradient start={wordShineStart} end={wordShineEnd} colors={SHINE} />
              </Rect>
            </Group>

            {/* The knot. */}
            <Group transform={markTransform} opacity={markOpacity}>
              <Group layer>
                <Strand path={loops[0]} clock={clock} loop={0} reduced={reduced} />
                <Strand path={loops[1]} clock={clock} loop={1} reduced={reduced} />
                {CROSSINGS.map((crossing, i) => (
                  <Over key={i} path={loops[crossing.over]} at={crossing.at} clock={clock} loop={crossing.over} reduced={reduced} />
                ))}
                <Rect x={-KNOT_WIDTH} y={-KNOT_HEIGHT} width={KNOT_WIDTH * 2} height={KNOT_HEIGHT * 2} blendMode="srcATop">
                  <SkiaGradient start={markShineStart} end={markShineEnd} colors={SHINE} />
                </Rect>
              </Group>
            </Group>
          </Canvas>
        )}

        {size && (
          <Animated.Text pointerEvents="none" style={[styles.tagline, { top: baseline + 22 }, tagStyle]}>
            {TAGLINE}
          </Animated.Text>
        )}
      </View>
    </GestureDetector>
  );
}

type StrandProps = { path: ReturnType<typeof makeLoop>; clock: SharedValue<number>; loop: number; reduced: boolean };

/** One loop of foil, drawn on from its top tip: a thin darker edge under a gold band. */
function Strand({ path, clock, loop, reduced }: StrandProps) {
  // Trims stay inside (0, 1]: a round cap would show as a dot before the stroke starts.
  const end = useDerivedValue(() => Math.max(0.001, drawn(clock.get(), loop, reduced)));
  const opacity = useDerivedValue(() => (drawn(clock.get(), loop, reduced) > 0.001 ? 1 : 0));
  return (
    <Group opacity={opacity}>
      <Path path={path} style="stroke" strokeWidth={STROKE} strokeCap="round" start={0} end={end} color={FOIL_EDGE} />
      <Path path={path} style="stroke" strokeWidth={STROKE - 1.6} strokeCap="round" start={0} end={end}>
        <Foil />
      </Path>
    </Group>
  );
}

type OverProps = StrandProps & { at: number };

/** Where a strand passes over the other: it clears a gap through the one below, then is drawn again. */
function Over({ path, at, clock, loop, reduced }: OverProps) {
  // The gap is cut and the strand's dark edge redrawn over the same stretch; its gold core runs a
  // little further each way, over the core already there, so no seam shows where the patch ends.
  // A trim never runs backwards: until the stroke reaches the crossing, the patch is hidden.
  const from = at - OVER_SPAN;
  const fromCore = from - CORE_OVERLAP;
  const end = useDerivedValue(() => Math.max(from + 0.001, Math.min(at + OVER_SPAN, drawn(clock.get(), loop, reduced))));
  const endCore = useDerivedValue(() => Math.max(fromCore + 0.001, Math.min(at + OVER_SPAN + CORE_OVERLAP, drawn(clock.get(), loop, reduced))));
  const shown = useDerivedValue(() => (drawn(clock.get(), loop, reduced) > from + 0.001 ? 1 : 0));
  return (
    <Group opacity={shown}>
      <Path path={path} style="stroke" strokeWidth={STROKE + GAP * 2} strokeCap="butt" start={from} end={end} blendMode="dstOut" color="black" />
      <Path path={path} style="stroke" strokeWidth={STROKE} strokeCap="butt" start={from} end={end} color={FOIL_EDGE} />
      <Path path={path} style="stroke" strokeWidth={STROKE - 1.6} strokeCap="butt" start={fromCore} end={endCore}>
        <Foil />
      </Path>
    </Group>
  );
}

/** The foil's gradient across the knot, in its own units. */
function Foil() {
  return <SkiaGradient start={vec(-KNOT_WIDTH / 2, -KNOT_HEIGHT / 2)} end={vec(KNOT_WIDTH / 2, KNOT_HEIGHT / 2)} colors={FOIL} positions={FOIL_AT} />;
}

type LetterProps = {
  index: number;
  clock: SharedValue<number>;
  font: SkFont;
  x: number;
  width: number;
  baseline: number;
  char: string;
};

/** One letter of the wordmark, in foil, sliding through a slot at its baseline. */
function Letter({ index, clock, font, x, width, baseline, char }: LetterProps) {
  const reduced = useReducedMotion();
  const reveal = WORD_SIZE * 0.95;
  const transform = useDerivedValue(() => [{ translateY: reduced ? 0 : letterShift(clock.get(), index) * reveal }]);
  const opacity = useDerivedValue(() => {
    const t = clock.get();
    if (reduced) return step(t, LETTERS_AT, 0.5) * (1 - step(t, HANDOFF_AT, 0.3));
    const shift = letterShift(t, index);
    return shift >= 0.999 || shift <= -0.999 ? 0 : 1;
  });
  const clip = useMemo(
    () => ({ x: x - 6, y: baseline - WORD_SIZE * 0.86, width: width + 12, height: WORD_SIZE * 1.02 }),
    [x, width, baseline],
  );
  return (
    <Group clip={clip} opacity={opacity}>
      <Group transform={transform}>
        <SkiaText x={x} y={baseline} text={char} font={font}>
          <SkiaGradient start={vec(x, baseline - WORD_SIZE * 0.75)} end={vec(x + width, baseline)} colors={FOIL} positions={FOIL_AT} />
        </SkiaText>
      </Group>
    </Group>
  );
}

type AppProps = {
  clock: SharedValue<number>;
  reduced: boolean;
  interactive: boolean;
  insets: { top: number; bottom: number };
  height: number;
  onReplay: () => void;
  onTouch: () => void;
  onReserve: () => void;
};

/** The app behind the splash: today's bake at a bakery, with a header the knot lands in. */
function AppSurface({ clock, reduced, interactive, insets, height, onReplay, onTouch, onReserve }: AppProps) {
  const heroHeight = Math.min(310, Math.max(210, height * 0.34));
  const markWidth = (KNOT_WIDTH * HEADER_MARK) / KNOT_HEIGHT;
  return (
    <View
      style={[StyleSheet.absoluteFill, styles.app]}
      pointerEvents={interactive ? 'box-none' : 'none'}
      onTouchStart={onTouch}
    >
      <Rise clock={clock} k={1} reduced={reduced} style={[styles.header, { top: insets.top + 10, left: HEADER_LEFT + markWidth + 10 }]}>
        <Text style={styles.headerWord}>LOAVEN</Text>
      </Rise>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Replay the splash"
        onPress={onReplay}
        hitSlop={8}
        style={[styles.headerMark, { top: insets.top + 10, width: markWidth + 8 }]}
      />
      <Rise clock={clock} k={1} reduced={reduced} style={[styles.bag, { top: insets.top + 8 }]}>
        <SymbolView name="bag" size={19} weight="medium" tintColor={INK} />
        <View style={styles.badge}>
          <Text style={styles.badgeText}>2</Text>
        </View>
      </Rise>

      <View style={[styles.body, { paddingTop: insets.top + 70, paddingBottom: insets.bottom + 84 }]}>
        <Rise clock={clock} k={2} reduced={reduced}>
          <Text style={styles.eyebrow}>TUESDAY MORNING</Text>
          <Text style={styles.title}>The first loaves{'\n'}are out.</Text>
        </Rise>

        <Rise clock={clock} k={3} reduced={reduced} style={[styles.hero, { height: heroHeight }]}>
          <Image source={HERO} style={StyleSheet.absoluteFill} contentFit="cover" transition={250} />
          <LinearGradient colors={['rgba(14,9,6,0)', 'rgba(14,9,6,0.78)']} locations={[0.45, 1]} style={StyleSheet.absoluteFill} />
          <View style={styles.heroFoot}>
            <View style={{ flex: 1, gap: 3 }}>
              <Text style={styles.heroName}>Country sourdough</Text>
              <Text style={styles.heroMeta}>Out of the oven at 6:40 · 14 left</Text>
            </View>
            <View style={styles.price}>
              <Text style={styles.priceText}>$9</Text>
            </View>
          </View>
        </Rise>

        <Rise clock={clock} k={4} reduced={reduced} style={styles.sectionRow}>
          <Text style={styles.section}>From the shelf</Text>
          <Text style={styles.seeAll}>See all</Text>
        </Rise>

        <View style={styles.shelf}>
          {SHELF.map((item, i) => (
            <Rise key={item.name} clock={clock} k={5 + i} reduced={reduced} style={styles.tile}>
              <Image source={item.uri} style={styles.tileImage} contentFit="cover" transition={250} />
              <View style={styles.tileRow}>
                <Text style={styles.tileName} numberOfLines={1}>
                  {item.name}
                </Text>
                <Text style={styles.tilePrice}>{item.price}</Text>
              </View>
            </Rise>
          ))}
        </View>
      </View>

      <Rise clock={clock} k={7} reduced={reduced} style={[styles.cta, { bottom: insets.bottom + 12 }]}>
        <Reserve onPress={onReserve} />
      </Rise>
    </View>
  );
}

/** An app element that rises into place as the doorway opens. */
function Rise({ clock, k, reduced, style, children }: { clock: SharedValue<number>; k: number; reduced: boolean; style?: object; children: ReactNode }) {
  const animated = useAnimatedStyle(() => {
    const p = appIn(clock.get(), k);
    return { opacity: p, transform: [{ translateY: reduced ? 0 : (1 - p) * APP_RISE }] };
  });
  return <Animated.View style={[style, animated]}>{children}</Animated.View>;
}

/** The dark pill at the bottom, dipping to 0.97 under the finger. */
function Reserve({ onPress }: { onPress: () => void }) {
  const down = useSharedValue(1);
  const style = useAnimatedStyle(() => ({ transform: [{ scale: down.get() }] }));
  return (
    <Pressable
      accessibilityRole="button"
      onPressIn={() => down.set(withTiming(0.97, { duration: 110 }))}
      onPressOut={() => down.set(withTiming(1, { duration: 160 }))}
      onPress={onPress}
    >
      <Animated.View style={[styles.button, style]}>
        <Text style={styles.buttonLabel}>Reserve for pickup</Text>
      </Animated.View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
    backgroundColor: SPLASH,
  },
  tagline: {
    position: 'absolute',
    left: 0,
    right: 0,
    textAlign: 'center',
    color: 'rgba(216,183,126,0.72)',
    fontSize: 11,
    fontWeight: '600',
    letterSpacing: 3.2,
  },
  app: {
    backgroundColor: CREAM,
  },
  header: {
    position: 'absolute',
    height: HEADER_MARK,
    justifyContent: 'center',
  },
  headerWord: {
    fontFamily: SERIF,
    fontWeight: '700',
    fontSize: 17,
    letterSpacing: 4.5,
    color: INK,
  },
  headerMark: {
    position: 'absolute',
    left: HEADER_LEFT - 4,
    height: HEADER_MARK,
  },
  bag: {
    position: 'absolute',
    right: 20,
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(31,24,18,0.22)',
  },
  badge: {
    position: 'absolute',
    top: 4,
    right: 3,
    minWidth: 16,
    height: 16,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: INK,
  },
  badgeText: {
    color: CREAM,
    fontSize: 10,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
  body: {
    flex: 1,
    paddingHorizontal: 20,
    gap: 18,
  },
  eyebrow: {
    color: MUTED,
    fontSize: 12,
    fontWeight: '600',
    letterSpacing: 1.6,
    marginBottom: 6,
  },
  title: {
    fontFamily: SERIF,
    fontWeight: '700',
    fontSize: 32,
    lineHeight: 37,
    letterSpacing: -0.4,
    color: INK,
  },
  hero: {
    borderRadius: 26,
    borderCurve: 'continuous',
    overflow: 'hidden',
    backgroundColor: CARD,
  },
  heroFoot: {
    position: 'absolute',
    left: 18,
    right: 18,
    bottom: 16,
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: 12,
  },
  heroName: {
    color: '#fbf6ee',
    fontSize: 20,
    fontWeight: '700',
    letterSpacing: -0.3,
  },
  heroMeta: {
    color: 'rgba(251,246,238,0.75)',
    fontSize: 13,
    fontWeight: '500',
  },
  price: {
    height: 32,
    paddingHorizontal: 13,
    borderRadius: 16,
    justifyContent: 'center',
    backgroundColor: '#fbf6ee',
  },
  priceText: {
    color: INK,
    fontSize: 15,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
  sectionRow: {
    flexDirection: 'row',
    alignItems: 'baseline',
    justifyContent: 'space-between',
    marginTop: 2,
  },
  section: {
    fontFamily: SERIF,
    fontWeight: '700',
    fontSize: 20,
    color: INK,
  },
  seeAll: {
    color: MUTED,
    fontSize: 14,
    fontWeight: '600',
  },
  shelf: {
    flexDirection: 'row',
    gap: 12,
    marginTop: -6,
  },
  tile: {
    flex: 1,
    gap: 8,
  },
  tileImage: {
    height: 112,
    borderRadius: 18,
    borderCurve: 'continuous',
    backgroundColor: CARD,
  },
  tileRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: 8,
    paddingHorizontal: 2,
  },
  tileName: {
    flex: 1,
    color: INK,
    fontSize: 15,
    fontWeight: '600',
  },
  tilePrice: {
    color: MUTED,
    fontSize: 15,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  cta: {
    position: 'absolute',
    left: 20,
    right: 20,
  },
  button: {
    height: 56,
    borderRadius: 28,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: INK,
  },
  buttonLabel: {
    color: CREAM,
    fontSize: 16,
    fontWeight: '600',
    letterSpacing: -0.1,
  },
});
