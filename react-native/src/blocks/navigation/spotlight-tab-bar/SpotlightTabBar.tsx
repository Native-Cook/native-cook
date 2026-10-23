import { Canvas, Group, LinearGradient, Path, RoundedRect, Shadow, Skia, rect, rrect, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cancelAnimation,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

export type SpotlightTab = {
  title: string;
  /** A solid glyph in a 30 × 30 box, as SVG path data made of M, L, C and Z, filled even-odd. */
  glyph: string;
};

type Props = {
  tabs?: SpotlightTab[];
  /** The tab that is lit at first, from 0. */
  initialTab?: number;
  /** The screen, the pill, and a glyph in the shade and in the light. */
  field?: string;
  pill?: string;
  shade?: string;
  light?: string;
  /** Goes round the tabs by itself every LOOP seconds. */
  autoPlay?: boolean;
  /** Called with the tab a tap or a drag chose. */
  onSelect?: (index: number) => void;
};

export const GLYPHS = {
  person:
    'M15 4.5C17.76 4.5 20 6.74 20 9.5C20 12.26 17.76 14.5 15 14.5C12.24 14.5 10 12.26 10 9.5C10 6.74 12.24 4.5 15 4.5Z' +
    'M15 17C19.42 17 23 19.01 23 21.5C23 23.99 19.42 26 15 26C10.58 26 7 23.99 7 21.5C7 19.01 10.58 17 15 17Z',
  house:
    'M15 4L26 13.5L26 24C26 25.1 25.1 26 24 26L6 26C4.9 26 4 25.1 4 24L4 13.5Z' +
    'M12 26L12 20C12 18.34 13.34 17 15 17C16.66 17 18 18.34 18 20L18 26Z',
  bell:
    'M15 4C10.6 4 8 7.2 8 11.5L8 16.5L5.5 20.5C5.1 21.2 5.6 22 6.4 22L23.6 22C24.4 22 24.9 21.2 24.5 20.5L22 16.5L22 11.5C22 7.2 19.4 4 15 4Z' +
    'M12 23.5L18 23.5C18 25.2 16.7 26.5 15 26.5C13.3 26.5 12 25.2 12 23.5Z',
  camera:
    'M7.5 9L10.2 9L11.6 6.6C11.9 6.2 12.3 6 12.8 6L17.2 6C17.7 6 18.1 6.2 18.4 6.6L19.8 9L22.5 9C24.43 9 26 10.57 26 12.5L26 21.5C26 23.43 24.43 25 22.5 25L7.5 25C5.57 25 4 23.43 4 21.5L4 12.5C4 10.57 5.57 9 7.5 9Z' +
    'M15 12C17.76 12 20 14.24 20 17C20 19.76 17.76 22 15 22C12.24 22 10 19.76 10 17C10 14.24 12.24 12 15 12Z' +
    'M15 14.2C16.55 14.2 17.8 15.45 17.8 17C17.8 18.55 16.55 19.8 15 19.8C13.45 19.8 12.2 18.55 12.2 17C12.2 15.45 13.45 14.2 15 14.2Z',
};

const TABS: SpotlightTab[] = [
  { title: 'Profile', glyph: GLYPHS.person },
  { title: 'Home', glyph: GLYPHS.house },
  { title: 'Alerts', glyph: GLYPHS.bell },
  { title: 'Camera', glyph: GLYPHS.camera },
];

const FIELD = '#c4c4c4';
const PILL = '#686868';
const SHADE = '#3a3a3a';
const LIGHT = '#ffffff';
const INK = '#3a3a3a';
const CARD = '#d0d0d0';
const LINE = '#b6b6b6';
const MARK = '#9a9a9a';

// The pill: its margins, its height, and the first and last glyph's distance from its ends.
const SIDE = 28;
const PILL_HEIGHT = 64;
const PILL_LIFT = 6;
const EDGE = 62;
const GLYPH = 30;
// The lamp lies on the pill's top edge; its beam widens from the lamp to the pill's bottom edge.
const LAMP = 62;
const LAMP_HEIGHT = 6;
const BEAM_FOOT = 72;
// Room around the pill in its canvas, for the shadow and the lamp.
const BLEED = 44;
const SHADOW_DROP = 10;
const SHADOW_SIGMA = 11;
// The page.
const MARGIN = 24;
const TITLE_TOP = 25;
const CARDS = [180, 110, 110];
const CARD_GAP = 14;
const CARD_MARK = 44;

/** Seconds. The loop goes once round the tabs: a move every HOP_EVERY from HOP_AT. */
export const LOOP = 9;
const HOP_AT = 2;
const HOP_EVERY = 1.6;
// One move: the lamp slides for SLIDE, and SLIDE_PER_TAB longer for each tab after the first. The
// foot of the beam follows LAG behind, so the beam leans back and straightens on arrival.
const SLIDE = 0.45;
const SLIDE_PER_TAB = 0.05;
const LAG = 0.06;
// The beam's strength at rest; on arrival it flares by FLARE and settles in FLARE_FOR.
const BEAM = 0.55;
const FLARE = 0.15;
const FLARE_FOR = 0.25;
// Across the whole bar the lamp dims by DIM in mid-move; a dim left by a move that was cut short
// fades in DIM_FADE.
const DIM = 0.6;
const DIM_FADE = 0.15;
// A glyph is lit while the lamp is within this many tabs of it.
const REACH = 0.55;
// The page: the old one fades out, the new one in, and the mark on its top card a little later.
const PAGE_OUT = 0.15;
const PAGE_IN = 0.25;
const MARK_AFTER = 0.1;
const MARK_IN = 0.25;
// A finger that takes the lamp away from where it is gets it in this time.
const CATCH = 0.15;
// The clocks of a move run this long, well past its last change.
const REST = 1.2;
// With reduced motion the light fades from one tab to the next in this time.
const FADE = 0.2;
// After a touch the loop waits, then goes back to its first tab and waits for that to end.
const USER_HOLD = 3;
const SETTLE = 1;

/** Where the lamp's move started, or the finger that holds it. */
type Lamp = {
  /** A finger holds the lamp: it is at the finger, `offset` tabs aside at first. */
  held: boolean;
  offset: number;
  target: number;
  /** The lamp's place and the beam's foot at the start, in tabs. */
  x: number;
  foot: number;
  /** How dim the lamp was at the start. */
  dim: number;
};

/** The lights on the bar: one, and with reduced motion a second one fading in at the target. */
type Lights = { x: number; foot: number; power: number; flare: number; nextX: number; nextPower: number };

/** Where a change of page started. */
type Turn = { target: number; from: number; presence: number };

function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

function smooth(t: number) {
  'worklet';
  const u = clamp01(t);
  return u * u * (3 - 2 * u);
}

function easeOut(t: number) {
  'worklet';
  const u = 1 - clamp01(t);
  return 1 - u * u * u;
}

function easeInOut(t: number) {
  'worklet';
  const u = clamp01(t);
  return u < 0.5 ? 4 * u * u * u : 1 - 4 * (1 - u) * (1 - u) * (1 - u);
}

/** The lights, `t` seconds into a move or a hold. */
function lights(lamp: Lamp, t: number, finger: number, last: number, reduced: boolean): Lights {
  'worklet';
  if (lamp.held) {
    const x = Math.min(Math.max(finger + lamp.offset * (1 - easeOut(t / CATCH)), 0), last);
    return { x, foot: x, power: 1 - lamp.dim * (1 - clamp01(t / DIM_FADE)), flare: 0, nextX: x, nextPower: 0 };
  }
  if (reduced) {
    const faded = clamp01(t / FADE);
    return { x: lamp.x, foot: lamp.x, power: 1 - faded, flare: 0, nextX: lamp.target, nextPower: faded };
  }
  const distance = Math.abs(lamp.target - lamp.x);
  const slide = SLIDE + SLIDE_PER_TAB * Math.max(distance - 1, 0);
  const far = DIM * clamp01((distance - 1) / 2);
  const dim = Math.max(far * Math.sin(Math.PI * clamp01(t / slide)), lamp.dim * (1 - clamp01(t / DIM_FADE)));
  return {
    x: lamp.x + (lamp.target - lamp.x) * easeInOut(t / slide),
    foot: lamp.foot + (lamp.target - lamp.foot) * easeInOut((t - LAG) / slide),
    power: 1 - dim,
    flare: FLARE * Math.sin(Math.PI * clamp01((t - slide) / FLARE_FOR)),
    nextX: lamp.target,
    nextPower: 0,
  };
}

/** How much light falls on a glyph. */
function lit(now: Lights, index: number) {
  'worklet';
  return clamp01(smooth(1 - Math.abs(now.x - index) / REACH) * now.power + smooth(1 - Math.abs(now.nextX - index) / REACH) * now.nextPower);
}

function resting(tab: number): Lamp {
  return { held: false, offset: 0, target: tab, x: tab, foot: tab, dim: 0 };
}

/** A beam in the pill's box: from the lamp's width at the top edge to its foot at the bottom. */
function beamPath(top: number, foot: number) {
  'worklet';
  return Skia.PathBuilder.Make()
    .moveTo(top - LAMP / 2, 0)
    .lineTo(top + LAMP / 2, 0)
    .lineTo(foot + BEAM_FOOT / 2, PILL_HEIGHT)
    .lineTo(foot - BEAM_FOOT / 2, PILL_HEIGHT)
    .close()
    .build();
}

function Glyph({ index, glyph, x, shade, light, now }: { index: number; glyph: string; x: number; shade: string; light: string; now: SharedValue<Lights> }) {
  const shine = useDerivedValue(() => lit(now.get(), index));
  return (
    <Group transform={[{ translateX: x - GLYPH / 2 }, { translateY: (PILL_HEIGHT - GLYPH) / 2 }]}>
      <Path path={glyph} fillType="evenOdd" color={shade} />
      {/* The lit colour fades in over the shaded one. */}
      <Path path={glyph} fillType="evenOdd" color={light} opacity={shine} />
    </Group>
  );
}

function Page({
  index,
  tab,
  top,
  current,
  turn,
  clock,
}: {
  index: number;
  tab: SpotlightTab;
  top: number;
  current: boolean;
  turn: SharedValue<Turn>;
  clock: SharedValue<number>;
}) {
  const style = useAnimatedStyle(() => {
    const t = clock.get();
    const { target, from, presence } = turn.get();
    if (t < PAGE_OUT) return { opacity: index === from ? presence * (1 - t / PAGE_OUT) : 0 };
    return { opacity: index === target ? clamp01((t - PAGE_OUT) / PAGE_IN) : 0 };
  });
  // The mark leaves with its page and comes back after the next one.
  const markStyle = useAnimatedStyle(() => ({ opacity: clock.get() < PAGE_OUT ? 1 : clamp01((clock.get() - PAGE_OUT - MARK_AFTER) / MARK_IN) }));
  return (
    <Animated.View
      pointerEvents="none"
      accessibilityElementsHidden={!current}
      importantForAccessibility={current ? 'auto' : 'no-hide-descendants'}
      style={[styles.page, { paddingTop: top }, style]}
    >
      <Text style={styles.title} numberOfLines={1} accessibilityRole="header">
        {tab.title}
      </Text>
      {CARDS.map((height, k) => (
        <View key={k} style={[styles.card, { height }]}>
          {k === 0 && (
            <Animated.View style={[styles.mark, markStyle]}>
              <Canvas style={styles.markCanvas}>
                <Path path={tab.glyph} fillType="evenOdd" color={MARK} transform={[{ scale: CARD_MARK / GLYPH }]} />
              </Canvas>
            </Animated.View>
          )}
          <View style={[styles.line, styles.long]} />
          <View style={[styles.line, styles.short]} />
        </View>
      ))}
    </Animated.View>
  );
}

/**
 * A tab bar lit by a lamp: a white bar on the rim of a grey pill throws a cone of light into it,
 * and the glyph under the lamp turns white. Choosing a tab slides the lamp there, its beam leaning
 * back as it goes and flaring as it arrives; the glyphs it passes catch the light for a moment.
 * The lamp can also be dragged along the pill. The page above changes with the tab.
 */
export function SpotlightTabBar({
  tabs = TABS,
  initialTab = 0,
  field = FIELD,
  pill = PILL,
  shade = SHADE,
  light = LIGHT,
  autoPlay = false,
  onSelect,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const size = laidOut ?? window;
  const last = Math.max(tabs.length - 1, 0);
  const first = Math.min(Math.max(initialTab, 0), last);
  const [index, setIndex] = useState(first);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  const pillWidth = size.width - SIDE * 2;
  const pillTop = size.height - Math.max(insets.bottom, 16) - PILL_LIFT - PILL_HEIGHT;
  // From one glyph's centre to the next.
  const pitch = last > 0 ? (pillWidth - EDGE * 2) / last : 0;

  const lamp = useSharedValue<Lamp>(resting(first));
  // Seconds since the lamp's move, or the hold, started.
  const lampClock = useSharedValue(REST);
  // The finger's place along the pill, in tabs, while it holds the lamp.
  const finger = useSharedValue(0);
  const turn = useSharedValue<Turn>({ target: first, from: first, presence: 0 });
  const turnClock = useSharedValue(REST);
  const now = useDerivedValue(() => lights(lamp.get(), lampClock.get(), finger.get(), last, reducedMotion));

  const beam = useDerivedValue(() => beamPath(EDGE + now.get().x * pitch, EDGE + now.get().foot * pitch));
  const beamOpacity = useDerivedValue(() => (BEAM + now.get().flare) * now.get().power);
  const lampX = useDerivedValue(() => EDGE + now.get().x * pitch - LAMP / 2);
  const lampOpacity = useDerivedValue(() => now.get().power);
  const nextBeam = useDerivedValue(() => beamPath(EDGE + now.get().nextX * pitch, EDGE + now.get().nextX * pitch));
  const nextBeamOpacity = useDerivedValue(() => BEAM * now.get().nextPower);
  const nextLampX = useDerivedValue(() => EDGE + now.get().nextX * pitch - LAMP / 2);
  const nextLampOpacity = useDerivedValue(() => now.get().nextPower);

  useEffect(
    () => () => {
      cancelAnimation(lampClock);
      cancelAnimation(turnClock);
    },
    [lampClock, turnClock],
  );

  const run = (clock: SharedValue<number>) => {
    'worklet';
    clock.set(0);
    clock.set(withTiming(REST, { duration: REST * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }));
  };

  /** Sends the lamp to a tab from wherever it is, and turns the page if the tab is another one. */
  const go = (target: number) => {
    'worklet';
    const was = lights(lamp.get(), lampClock.get(), finger.get(), last, reducedMotion);
    // With reduced motion the light that is stronger is the one that fades out.
    const x = was.nextPower > 0.5 ? was.nextX : was.x;
    lamp.set({ held: false, offset: 0, target, x, foot: was.nextPower > 0.5 ? x : was.foot, dim: 1 - was.power });
    run(lampClock);
    const page = turn.get();
    if (page.target === target) return;
    const t = turnClock.get();
    const leaving = t < PAGE_OUT ? page.from : page.target;
    const presence = t < PAGE_OUT ? page.presence * (1 - t / PAGE_OUT) : clamp01((t - PAGE_OUT) / PAGE_IN);
    turn.set({ target, from: leaving, presence });
    run(turnClock);
  };

  /** Opens a tab: the same path for a tap and for the loop, which is silent. */
  const select = (target: number, byHand: boolean) => {
    if (target === index) return;
    if (byHand && process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
    setIndex(target);
    scheduleOnUI(go, target);
    if (byHand) onSelect?.(target);
  };

  /** A drag ended on a tab. */
  const dropped = (target: number) => {
    if (target === index) return;
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
    setIndex(target);
    onSelect?.(target);
  };

  // The timers read the latest state when they fire.
  const latest = useRef({ select, index, first, count: tabs.length });
  useEffect(() => {
    latest.current = { select, index, first, count: tabs.length };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, task: () => void) => timers.push(setTimeout(task, ms));
    const cycle = (origin: number) => {
      for (let k = 0; k < latest.current.count; k++) {
        after(origin + (HOP_AT + k * HOP_EVERY) * 1000 - Date.now(), () => latest.current.select((latest.current.index + 1) % latest.current.count, false));
      }
      after(origin + LOOP * 1000 - Date.now(), () => cycle(origin + LOOP * 1000));
    };
    if (touches === 0) cycle(Date.now());
    else {
      after(USER_HOLD * 1000, () => {
        // The loop starts over from the tab it was given.
        latest.current.select(latest.current.first, false);
        after(SETTLE * 1000, () => cycle(Date.now()));
      });
    }
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches]);

  // Dragging along the pill takes the lamp with the finger; it goes to the nearest tab on release.
  const pan = Gesture.Pan()
    .enabled(last > 0)
    .activeOffsetX([-8, 8])
    .onStart((event) => {
      const was = lights(lamp.get(), lampClock.get(), finger.get(), last, reducedMotion);
      const at = (event.x - EDGE) / pitch;
      finger.set(at);
      lamp.set({ held: true, offset: was.x - at, target: was.x, x: was.x, foot: was.x, dim: 1 - was.power });
      run(lampClock);
    })
    .onUpdate((event) => {
      finger.set((event.x - EDGE) / pitch);
    })
    .onEnd(() => {
      const was = lights(lamp.get(), lampClock.get(), finger.get(), last, reducedMotion);
      const target = Math.round(was.x);
      go(target);
      scheduleOnRN(dropped, target);
    });

  const onLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setLaidOut({ width, height });
  };

  const outline = rrect(rect(0, 0, pillWidth, PILL_HEIGHT), PILL_HEIGHT / 2, PILL_HEIGHT / 2);

  return (
    <View style={[styles.screen, { backgroundColor: field }]} onLayout={onLayout} onTouchStart={() => setTouches((n) => n + 1)}>
      <StatusBar style="dark" />

      {tabs.map((tab, k) => (
        <Page key={k} index={k} tab={tab} top={insets.top + TITLE_TOP} current={k === index} turn={turn} clock={turnClock} />
      ))}

      <Canvas
        style={[styles.canvas, { left: SIDE - BLEED, top: pillTop - BLEED, width: pillWidth + BLEED * 2, height: PILL_HEIGHT + BLEED * 2 }]}
        pointerEvents="none"
      >
        <Group transform={[{ translateX: BLEED }, { translateY: BLEED }]}>
          <RoundedRect rect={outline} color={pill}>
            <Shadow dx={0} dy={SHADOW_DROP} blur={SHADOW_SIGMA} color="rgba(0,0,0,0.28)" />
          </RoundedRect>
          {/* The beams stay inside the pill; the lamps lie across its edge. */}
          <Group clip={outline}>
            <Path path={beam} opacity={beamOpacity}>
              <LinearGradient start={vec(0, 0)} end={vec(0, PILL_HEIGHT)} colors={[light, 'rgba(255,255,255,0)']} />
            </Path>
            <Path path={nextBeam} opacity={nextBeamOpacity}>
              <LinearGradient start={vec(0, 0)} end={vec(0, PILL_HEIGHT)} colors={[light, 'rgba(255,255,255,0)']} />
            </Path>
          </Group>
          {tabs.map((tab, k) => (
            <Glyph key={k} index={k} glyph={tab.glyph} x={EDGE + k * pitch} shade={shade} light={light} now={now} />
          ))}
          <RoundedRect x={lampX} y={-LAMP_HEIGHT / 2} width={LAMP} height={LAMP_HEIGHT} r={LAMP_HEIGHT / 2} color={light} opacity={lampOpacity} />
          <RoundedRect x={nextLampX} y={-LAMP_HEIGHT / 2} width={LAMP} height={LAMP_HEIGHT} r={LAMP_HEIGHT / 2} color={light} opacity={nextLampOpacity} />
        </Group>
      </Canvas>

      <GestureDetector gesture={pan}>
        <View style={[styles.tabs, { left: SIDE, top: pillTop, width: pillWidth }]} accessibilityRole="tablist">
          {tabs.map((tab, k) => (
            <Pressable
              key={k}
              style={[styles.tab, { left: EDGE + k * pitch - Math.max(pitch, GLYPH * 2) / 2, width: Math.max(pitch, GLYPH * 2) }]}
              onPress={() => select(k, true)}
              accessibilityRole="tab"
              accessibilityLabel={tab.title}
              accessibilityState={{ selected: k === index }}
            />
          ))}
        </View>
      </GestureDetector>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
  },
  page: {
    position: 'absolute',
    left: MARGIN,
    right: MARGIN,
    top: 0,
  },
  title: {
    color: INK,
    fontSize: 34,
    lineHeight: 41,
    fontWeight: '700',
    marginBottom: 18 - CARD_GAP,
    includeFontPadding: false,
  },
  card: {
    marginTop: CARD_GAP,
    padding: 22,
    borderRadius: 22,
    borderCurve: 'continuous',
    backgroundColor: CARD,
    justifyContent: 'flex-end',
  },
  mark: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
    alignItems: 'center',
    justifyContent: 'center',
  },
  markCanvas: {
    width: CARD_MARK,
    height: CARD_MARK,
  },
  line: {
    height: 10,
    borderRadius: 5,
    backgroundColor: LINE,
  },
  long: {
    width: 150,
  },
  short: {
    width: 96,
    marginTop: 12,
  },
  canvas: {
    position: 'absolute',
  },
  tabs: {
    position: 'absolute',
    height: PILL_HEIGHT,
  },
  tab: {
    position: 'absolute',
    top: 0,
    bottom: 0,
  },
});
