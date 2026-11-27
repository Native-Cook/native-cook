import { MaterialIcons } from '@expo/vector-icons';
import { Canvas, Circle, Group, LinearGradient, RadialGradient, Rect, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useEffectEvent, useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import {
  AUTO_TAPS,
  FLOODED,
  FORWARD,
  LOOP,
  PRESS_FOR,
  type Press,
  RETURN,
  SURGE,
  type Scene,
  USER_HOLD,
  WHOLE,
  amberAt,
  blockPose,
  glowPose,
  hotAt,
  pressAt,
  smooth,
  wordAtRest,
  wordPose,
} from './motion';

export type EmberGlyph = 'clock' | 'bolt' | 'figure' | 'calendar';

export type EmberWord = {
  /** A word, or a glyph drawn at the height of a capital letter. */
  text?: string;
  glyph?: EmberGlyph;
  /** A key word lights up; the number is its turn among the key words of its page. */
  key?: number;
  /** It was lit on the page before, which this page continues. */
  was?: boolean;
  italic?: boolean;
  /** No space before it. */
  glue?: boolean;
};

export type EmberPage = {
  /** The sentence, line by line: the lines are fixed, nothing wraps. */
  lines: EmberWord[][];
  /** Which of `glows` lights the page. */
  glow: number;
  /** A streak of amber in the hot spot. */
  amber?: boolean;
  /** How many words at the start are the page before, kept on screen. */
  keeps?: number;
};

export type EmberGlow = {
  /** The tint from where it starts down to the bottom edge, and where each colour sits (0 top, 1 bottom). */
  tint: string[];
  stops: number[];
  /** The hot spot at the bottom edge: its middle, its rim, and its radii. */
  hot: [string, string];
  size: [number, number];
  /** Plain words under this light. */
  mute: string;
};

type Props = {
  pages?: EmberPage[];
  glows?: EmberGlow[];
  nextLabel?: string;
  doneLabel?: string;
  /** Goes through the pages by itself every LOOP seconds. */
  autoPlay?: boolean;
  onPageChange?: (page: number) => void;
  /** Called when the last page's button is pressed. */
  onDone?: () => void;
};

const word = (text: string, key?: number): EmberWord => ({ text, key });
const glyph = (name: EmberGlyph, key?: number): EmberWord => ({ glyph: name, key });

const PAGES: EmberPage[] = [
  {
    glow: 0,
    lines: [[word('Industrial')], [word('revolution'), word('made')], [word('us'), word('plan'), word('around')], [word('the'), glyph('clock', 0), word('clock', 0)]],
  },
  {
    glow: 1,
    lines: [
      [word('But'), word('every'), word('person')],
      [word('has'), word('their'), word('own')],
      [word('unique'), glyph('bolt', 0), word('energy', 0)],
      [word('rhythm', 1)],
    ],
  },
  {
    glow: 1,
    amber: true,
    keeps: 10,
    lines: [
      [word('But'), word('every'), word('person')],
      [word('has'), word('their'), word('own')],
      [word('unique'), { glyph: 'bolt', was: true }, { text: 'energy', was: true }],
      [{ text: 'rhythm', was: true }, { text: ',', key: 0, glue: true }, word('and', 0), word('it’s', 0)],
      [{ text: 'not', key: 1, italic: true }, { text: 'random', key: 1, italic: true }, word('it’s', 2), word('a', 2)],
      [word('predictable')],
      [word('biology', 3)],
    ],
  },
  {
    glow: 2,
    lines: [
      [word('Unlock'), word('your'), word('full')],
      [word('potential'), word('by'), word('pairing')],
      [word('your'), glyph('figure', 0), word('biology', 0), word('and')],
      [glyph('calendar', 1), word('schedule', 1)],
    ],
  },
];

const BACKGROUND = '#010200';
const GLOWS: EmberGlow[] = [
  { tint: [BACKGROUND, '#0c0706', '#160b0a', '#24130f'], stops: [0.68, 0.8, 0.9, 1], hot: ['#e4ccc4', '#8a5a4c'], size: [150, 46], mute: '#8e8a84' },
  { tint: [BACKGROUND, '#101a06', '#1a3a13', '#264c01', '#3a6a08'], stops: [0.57, 0.65, 0.75, 0.82, 1], hot: ['#9bd12f', '#508131'], size: [210, 120], mute: '#8c936e' },
  { tint: [BACKGROUND, '#2b0d12', '#3c000d', '#510013'], stops: [0.62, 0.7, 0.81, 1], hot: ['#e8621a', '#a3180f'], size: [210, 105], mute: '#9c7f7c' },
];
const AMBER = '#e0a21a';
const AMBER_SIZE = [90, 46];
const AMBER_ASIDE = 40;
const LIT = 'rgba(255, 255, 255, 0.95)';

// The type is set for a screen 393 points wide and scaled down on a narrower one.
const DESIGN_WIDTH = 393;
const MARGIN = 40;
const FONT = 30;
const LINE = 43;
const SPACE = 7.5;
const GLYPH = 26;
const BUTTON_MARGIN = 28;
const BUTTON_HEIGHT = 54;
const TEXT_GAP = 38;
// The hot spot's middle: this far across the screen, and this share of its height below the edge.
const HOT_ACROSS = 0.6;
const HOT_BELOW = 0.15;
// How its light falls off from the middle to the rim.
const FALLOFF = [0, 0.3, 0.6, 0.85, 1];
const FALLOFF_LIGHT = [1, 0.7, 0.35, 0.08, 0];
// A finger that moves further than this is not tapping; this far downwards, it is going back.
const SLOP = 12;
const BACK_SWIPE = 40;

const ICONS: Record<EmberGlyph, keyof typeof MaterialIcons.glyphMap> = { clock: 'schedule', bolt: 'bolt', figure: 'man', calendar: 'calendar-month' };

/** A hex colour at an opacity. */
function fade(hex: string, opacity: number) {
  const value = parseInt(hex.slice(1), 16);
  return `rgba(${(value >> 16) & 0xff}, ${(value >> 8) & 0xff}, ${value & 0xff}, ${opacity})`;
}

/** The colours of a hot spot from its middle to its rim. */
function falloff(middle: string, rim: string) {
  return FALLOFF_LIGHT.map((light, index) => fade(index < 2 ? middle : rim, light));
}

type Stage = {
  clock: SharedValue<number>;
  scene: SharedValue<Scene>;
  press: SharedValue<Press>;
  /** The glow of each page, and whether it has the amber streak. */
  glowOf: number[];
  amberOf: boolean[];
  width: number;
  height: number;
  still: boolean;
};

/** One colour of the glow: a tint rising from the bottom edge and a hot spot half below it. */
function Glow({ color, glow, stage }: { color: number; glow: EmberGlow; stage: Stage }) {
  const { clock, scene, press, glowOf, width, height, still } = stage;
  const [rx, ry] = glow.size;
  const from = glow.stops[0] * height;
  const pose = useDerivedValue(() => {
    const here = scene.get();
    return glowPose(color, glowOf[here.page], glowOf[here.from], here, clock.get(), still);
  });
  const opacity = useDerivedValue(() => pose.get().opacity);
  // Stretched from the bottom edge, and lifted.
  const transform = useDerivedValue(() => [{ translateY: height }, { scaleY: pose.get().stretch }, { translateY: -height - pose.get().lift }]);
  const hot = useDerivedValue(() => hotAt(width * HOT_ACROSS, press.get(), clock.get(), still));
  const light = useDerivedValue(() => hot.get().light);
  const spot = useDerivedValue(() => [{ translateX: hot.get().x }, { translateY: height + ry * HOT_BELOW }, { scaleY: ry / rx }]);
  const colors = useMemo(() => falloff(glow.hot[0], glow.hot[1]), [glow.hot]);
  const positions = glow.stops.map((stop) => (stop - glow.stops[0]) / (1 - glow.stops[0]));
  return (
    <Group opacity={opacity} transform={transform}>
      {/* The tint runs on below the screen, so the surge leaves no gap under it. */}
      <Rect x={0} y={from} width={width} height={height - from + SURGE + 40} dither>
        <LinearGradient start={vec(0, from)} end={vec(0, height)} colors={glow.tint} positions={positions} />
      </Rect>
      <Group transform={spot} opacity={light}>
        <Circle cx={0} cy={0} r={rx} dither>
          <RadialGradient c={vec(0, 0)} r={rx} colors={colors} positions={FALLOFF} />
        </Circle>
      </Group>
    </Group>
  );
}

function Amber({ stage }: { stage: Stage }) {
  const { clock, scene, press, glowOf, amberOf, width, height, still } = stage;
  const [rx, ry] = AMBER_SIZE;
  const opacity = useDerivedValue(() => {
    const here = scene.get();
    return amberAt(amberOf[here.page], amberOf[here.from], here, clock.get()) * hotAt(0, press.get(), clock.get(), still).light;
  });
  const spot = useDerivedValue(() => {
    const here = scene.get();
    const lift = glowPose(glowOf[here.page], glowOf[here.page], glowOf[here.from], here, clock.get(), still).lift;
    const x = hotAt(width * HOT_ACROSS, press.get(), clock.get(), still).x + AMBER_ASIDE;
    return [{ translateX: x }, { translateY: height + ry * HOT_BELOW - lift }, { scaleY: ry / rx }];
  });
  const colors = useMemo(() => falloff(AMBER, AMBER), []);
  return (
    <Group transform={spot} opacity={opacity}>
      <Circle cx={0} cy={0} r={rx} dither>
        <RadialGradient c={vec(0, 0)} r={rx} colors={colors} positions={FALLOFF} />
      </Circle>
    </Group>
  );
}

type WordProps = {
  item: EmberWord;
  page: number;
  /** Its place among the words that are new on its page, or -1. */
  order: number;
  count: number;
  first: boolean;
  glow: EmberGlow;
  fit: number;
  stage: Stage;
};

function Word({ item, page, order, count, first, glow, fit, stage }: WordProps) {
  const { clock, scene, still } = stage;
  const key = item.key ?? -1;
  const was = item.was === true;
  const pose = useDerivedValue(() => {
    const here = scene.get();
    return here.page === page ? wordPose(order, count, key, was, here, clock.get(), still) : wordAtRest(key);
  });
  const wrapStyle = useAnimatedStyle(() => ({ opacity: pose.get().opacity, transform: [{ translateY: pose.get().y }, { scale: item.glyph ? pose.get().pop : 1 }] }));
  const litStyle = useAnimatedStyle(() => ({ opacity: pose.get().lit }));
  const flashStyle = useAnimatedStyle(() => ({ opacity: pose.get().flash }));
  const gap = first || item.glue ? 0 : SPACE * fit;
  const lights = key >= 0 || was;

  if (item.glyph) {
    const size = GLYPH * fit;
    return (
      <Animated.View style={[{ marginLeft: gap, width: size, height: size }, wrapStyle]}>
        <MaterialIcons name={ICONS[item.glyph]} size={size} color={glow.mute} />
        {lights ? (
          <Animated.View style={[StyleSheet.absoluteFill, litStyle]}>
            <MaterialIcons name={ICONS[item.glyph]} size={size} color={LIT} />
          </Animated.View>
        ) : null}
      </Animated.View>
    );
  }
  const type = [styles.word, { fontSize: FONT * fit, lineHeight: LINE * fit, fontStyle: item.italic ? ('italic' as const) : ('normal' as const) }];
  return (
    <Animated.View style={[{ marginLeft: gap }, wrapStyle]}>
      <Text style={[type, { color: glow.mute }]}>{item.text}</Text>
      {lights ? <Animated.Text style={[type, styles.over, { color: LIT }, litStyle]}>{item.text}</Animated.Text> : null}
      {/* The flash as a word lights up: the same word with a halo in the colour of the glow. */}
      {key >= 0 ? (
        <Animated.Text style={[type, styles.over, { color: LIT, textShadowColor: glow.hot[0], textShadowRadius: 12 }, flashStyle]}>{item.text}</Animated.Text>
      ) : null}
    </Animated.View>
  );
}

type PageProps = { index: number; pages: EmberPage[]; glow: EmberGlow; shown: boolean; bottom: number; fit: number; stage: Stage };

function PageText({ index, pages, glow, shown, bottom, fit, stage }: PageProps) {
  const { clock, scene, still } = stage;
  const page = pages[index];
  const keeps = page.keeps ?? 0;
  const count = page.lines.reduce((sum, line) => sum + line.length, 0) - keeps;
  // A page that continues the one before starts lower by the lines it adds.
  const rise = keeps > 0 && index > 0 ? (page.lines.length - pages[index - 1].lines.length) * LINE * fit : 0;
  const handsOver = (pages[index + 1]?.keeps ?? 0) > 0;
  const style = useAnimatedStyle(() => {
    const here = scene.get();
    const pose = blockPose(index, here.from === index - 1 ? rise : 0, handsOver && here.page === index + 1, here, clock.get(), still);
    return { opacity: pose.opacity, transform: [{ translateY: pose.y }] };
  });
  let seen = 0;
  return (
    <Animated.View
      pointerEvents="none"
      accessible={shown}
      accessibilityElementsHidden={!shown}
      importantForAccessibility={shown ? 'yes' : 'no-hide-descendants'}
      accessibilityLabel={page.lines.map((line) => line.map((item) => item.text ?? '').join(' ')).join(' ')}
      style={[styles.page, { left: MARGIN * fit, bottom }, style]}
    >
      {page.lines.map((line, row) => (
        <View key={row} style={[styles.line, { height: LINE * fit }]}>
          {line.map((item, column) => {
            const place = seen++;
            return <Word key={column} item={item} page={index} order={place - keeps} count={count} first={column === 0} glow={glow} fit={fit} stage={stage} />;
          })}
        </View>
      ))}
    </Animated.View>
  );
}

/**
 * A short argument told one sentence a page on black, under a glow that rises from the bottom
 * edge and changes colour with each page. Sentences are written word by word and their key words
 * light up. A tap goes on, a swipe down goes back, and the glow comes under a finger on the button.
 */
export function EmberGlowStory({ pages = PAGES, glows = GLOWS, nextLabel = 'Next', doneLabel = 'Unlock', autoPlay = false, onPageChange, onDone }: Props) {
  const still = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const size = laidOut ?? window;
  const fit = Math.min(1.1, size.width / DESIGN_WIDTH);
  const last = pages.length - 1;
  const buttonBottom = Math.max(insets.bottom + 3, 20);
  const buttonTop = size.height - buttonBottom - BUTTON_HEIGHT;

  // The last change of page, for screen readers and the callbacks; the pictures follow `scene`.
  const [change, setChange] = useState<{ page: number; how: 'tap' | 'auto' | 'rest' | null; done: boolean }>({ page: 0, how: null, done: false });
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  // Seconds since the first frame: `now` always, `clock` only while something moves.
  const now = useSharedValue(0);
  const clock = useSharedValue(0);
  // The story opens as it is just after the last page's flood: the first page, whole.
  const scene = useSharedValue<Scene>({ page: 0, from: last, at: -RETURN, how: FLOODED });
  const press = useSharedValue<Press>({ down: false, at: -10, x: 0 });
  // Without motion nothing breathes, so the clock may stop.
  const settled = useSharedValue(still ? 1 : Infinity);
  // Where the finger went down, and whether it has moved too far to be a tap.
  const start = useSharedValue({ x: 0, y: 0 });
  const moved = useSharedValue(false);

  // The timestamp of the first frame. Every render registers the frame callback anew, which
  // starts its `timeSinceFirstFrame` over, so the time is counted from here instead.
  const born = useSharedValue(-1);

  useFrameCallback((frame) => {
    if (born.get() < 0) born.set(frame.timestamp);
    const t = (frame.timestamp - born.get()) / 1000;
    now.set(t);
    const moving = Math.min(t, settled.get());
    if (moving !== clock.get()) clock.set(moving);
  });

  const report = useEffectEvent((latest: typeof change) => {
    if (latest.how === null) return;
    if (latest.how === 'tap' && process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
    onPageChange?.(latest.page);
    if (latest.done) onDone?.();
  });
  useEffect(() => report(change), [change]);

  const actions = useMemo(() => {
    const touched = () => setTouches((n) => n + 1);
    /** Shows a page. Taps, the loop that plays by itself and the return to rest all come through here. */
    const goTo = (page: number, how: number, by: 'tap' | 'auto' | 'rest') => {
      'worklet';
      const t = now.get();
      scene.set({ page, from: scene.get().page, at: t, how });
      settled.set(still ? t + 3 : Infinity);
      scheduleOnRN(setChange, { page, how: by, done: how === FLOODED });
    };
    const advance = (by: 'tap' | 'auto') => {
      'worklet';
      const page = scene.get().page;
      if (page < last) goTo(page + 1, FORWARD, by);
      else goTo(0, FLOODED, by);
    };
    const back = () => {
      'worklet';
      const page = scene.get().page;
      if (page > 0) goTo(page - 1, WHOLE, 'tap');
    };
    const rest = () => {
      'worklet';
      if (scene.get().page !== 0) goTo(0, WHOLE, 'rest');
    };
    /** Puts a finger on the button at x, or takes it off. */
    const hold = (down: boolean, x: number) => {
      'worklet';
      const t = now.get();
      if (press.get().down === down) return;
      press.set({ down, at: t, x: down ? x : press.get().x });
      if (still) settled.set(Math.max(settled.get(), t + 1));
    };
    return { touched, advance, back, rest, hold };
  }, [now, scene, press, settled, still, last]);

  const gesture = useMemo(() => {
    const { touched, advance, back, hold } = actions;
    return Gesture.Pan()
      .manualActivation(true)
      .onTouchesDown((event) => {
        const touch = event.changedTouches[0];
        start.set({ x: touch.x, y: touch.y });
        moved.set(false);
        scheduleOnRN(touched);
        const onButton = touch.y >= buttonTop && touch.y <= buttonTop + BUTTON_HEIGHT && touch.x >= BUTTON_MARGIN && touch.x <= size.width - BUTTON_MARGIN;
        if (onButton) hold(true, touch.x);
      })
      .onTouchesMove((event) => {
        const touch = event.changedTouches[0];
        if (moved.get() || Math.hypot(touch.x - start.get().x, touch.y - start.get().y) <= SLOP) return;
        moved.set(true);
        hold(false, 0);
      })
      .onTouchesUp((event) => {
        const touch = event.changedTouches[0];
        hold(false, 0);
        const dx = touch.x - start.get().x;
        const dy = touch.y - start.get().y;
        if (!moved.get()) advance('tap');
        else if (dy > BACK_SWIPE && dy > Math.abs(dx)) back();
      })
      .onTouchesCancelled(() => {
        hold(false, 0);
      });
  }, [actions, start, moved, buttonTop, size.width]);

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    const cycle = (origin: number) => {
      for (const at of AUTO_TAPS) {
        after(origin + (at - PRESS_FOR) * 1000 - Date.now(), () => scheduleOnUI(actions.hold, true, size.width / 2));
        after(origin + at * 1000 - Date.now(), () => {
          scheduleOnUI(actions.hold, false, 0);
          scheduleOnUI(actions.advance, 'auto');
        });
      }
      after(origin + LOOP * 1000 - Date.now(), () => cycle(origin + LOOP * 1000));
    };
    if (touches === 0) cycle(Date.now());
    else {
      after(USER_HOLD * 1000, () => {
        // The way back to the first page takes RETURN, and the loop starts from there.
        scheduleOnUI(actions.rest);
        cycle(Date.now() + RETURN * 1000);
      });
    }
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches, actions, size.width]);

  const glowOf = pages.map((page) => page.glow);
  const amberOf = pages.map((page) => page.amber === true);
  const stage: Stage = { clock, scene, press, glowOf, amberOf, width: size.width, height: size.height, still };

  const pressedStyle = useAnimatedStyle(() => ({ opacity: pressAt(press.get(), clock.get()) }));
  // The last page's label takes the place of the others'.
  const doneShown = useDerivedValue(() => {
    const here = scene.get();
    const e = clock.get() - here.at;
    return here.page === last ? smooth(e, 0.2, 0.5) : here.from === last ? 1 - smooth(e, 0, 0.25) : 0;
  });
  const nextStyle = useAnimatedStyle(() => ({ opacity: 1 - doneShown.get() }));
  const doneStyle = useAnimatedStyle(() => ({ opacity: doneShown.get() }));
  const lockStyle = useAnimatedStyle(() => ({ transform: [{ scale: still ? 1 : 0.6 + 0.4 * doneShown.get() }] }));

  const onLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setLaidOut({ width, height });
  };

  return (
    <GestureDetector gesture={gesture}>
      <View style={styles.screen} onLayout={onLayout}>
        <StatusBar style="light" />

        <Canvas style={styles.fill} pointerEvents="none">
          {glows.map((glow, color) => (
            <Glow key={color} color={color} glow={glow} stage={stage} />
          ))}
          <Amber stage={stage} />
        </Canvas>

        {pages.map((page, index) => (
          <PageText
            key={index}
            index={index}
            pages={pages}
            glow={glows[page.glow]}
            shown={change.page === index}
            bottom={buttonBottom + BUTTON_HEIGHT + TEXT_GAP}
            fit={fit}
            stage={stage}
          />
        ))}

        <View
          style={[styles.button, { bottom: buttonBottom }]}
          accessible
          accessibilityRole="button"
          accessibilityLabel={change.page === last ? doneLabel : nextLabel}
          onAccessibilityTap={() => {
            actions.touched();
            scheduleOnUI(actions.advance, 'tap');
          }}
        >
          <Animated.View style={[styles.fill, styles.pressed, pressedStyle]} />
          <Animated.Text style={[styles.label, nextStyle]} numberOfLines={1}>
            {nextLabel}
          </Animated.Text>
          <Animated.View style={[styles.done, doneStyle]}>
            <Animated.View style={lockStyle}>
              <MaterialIcons name="lock-open" size={18} color="#ffffff" />
            </Animated.View>
            <Text style={styles.label} numberOfLines={1}>
              {doneLabel}
            </Text>
          </Animated.View>
        </View>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: BACKGROUND,
    overflow: 'hidden',
  },
  fill: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
  },
  page: {
    position: 'absolute',
  },
  line: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  word: {
    fontWeight: '700',
    letterSpacing: -0.3,
    includeFontPadding: false,
  },
  over: {
    position: 'absolute',
    left: 0,
    top: 0,
  },
  button: {
    position: 'absolute',
    left: BUTTON_MARGIN,
    right: BUTTON_MARGIN,
    height: BUTTON_HEIGHT,
    borderRadius: BUTTON_HEIGHT / 2,
    backgroundColor: 'rgba(255, 255, 255, 0.06)',
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  },
  pressed: {
    backgroundColor: 'rgba(255, 255, 255, 0.085)',
  },
  label: {
    color: '#ffffff',
    fontSize: 16,
    lineHeight: 20,
    fontWeight: '600',
    includeFontPadding: false,
  },
  done: {
    position: 'absolute',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
});
