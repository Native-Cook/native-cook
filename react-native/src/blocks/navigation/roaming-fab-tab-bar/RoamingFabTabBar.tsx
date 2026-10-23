import { BlurMask, Canvas, Circle, Group, Paint, Path, Skia, type SkPath } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { type ReactNode, useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import {
  ARC,
  BAR,
  BAR_BOTTOM,
  BUBBLE,
  CHAT_INSET,
  CHAT_RISE,
  CHAT_SIZE,
  COMPOSE_SCALE,
  GLYPH_STROKE,
  HOME_RISE,
  HOME_SIZE,
  ICONS,
  ICON_FADE,
  ICON_Y,
  LOOP,
  MARGIN,
  PAGE_FADE,
  PENCIL,
  PLUS,
  POP,
  POP_EASE,
  PRESS,
  PRESS_IN,
  PRESS_OUT,
  type Piece,
  REDUCED_SLIDE,
  SHADOW_ALPHA,
  SHADOW_BLUR,
  SHADOW_Y,
  SHRINK,
  SHRINK_EASE,
  SLIDE,
  SLIDE_EASE,
  STEPS,
  SWITCH,
  TAB_HIT,
  TITLE_LINE,
  TITLE_TOP,
  USER_HOLD,
  clamp01,
  mix,
  swell,
  wiggle,
} from './motion';

type Props = {
  /** The titles of the four pages, in the order of the tabs. */
  titles?: [string, string, string, string];
  searchHint?: string;
  addLabel?: string;
  /** The times at the end of the rows of the last page. */
  times?: string[];
  /** The round button. */
  accent?: string;
  /** Goes through the tabs by itself, every LOOP seconds. */
  autoPlay?: boolean;
  /** Called with the tab's place, 0 to 3. */
  onTabChange?: (index: number) => void;
  /** Called with the tab the round button was pressed on: 0 to start something, 3 to write. */
  onAction?: (index: number) => void;
};

const PAGE = '#FFFFFF';
const INK = '#111111';
const OUTLINE = '#55555A';
const MUTE = '#9A9AA0';
const PLACEHOLDER = '#F1F1F3';
const AVATAR = '#E2E2E6';
const ADD_FILL = '#EEF4FF';
// The widths of the first line of the nine rows and of the four cards.
const ROWS = [120, 150, 100, 140, 110, 130, 152, 104, 136];
const CARDS = [110, 150, 130, 120];
const TIMES = ['9:41', '9:12', 'Mon', 'Sun', 'Fri', 'Thu', 'Wed', 'Tue', 'Mon'];

const shapes = new Map<string, SkPath>();
function shape(d: string) {
  if (!shapes.has(d)) shapes.set(d, Skia.Path.MakeFromSVGString(d)!);
  return shapes.get(d)!;
}

function tap() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function select() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

/** One drawing of an icon. */
function Pieces({ pieces, color }: { pieces: Piece[]; color: string }) {
  return pieces.map(({ d, o, paint, width }, index) => {
    const filled = paint === 'fill' || paint === 'cutFill';
    const props = {
      color: paint === 'stroke' || paint === 'fill' ? color : PAGE,
      style: filled ? ('fill' as const) : ('stroke' as const),
      strokeWidth: width,
      strokeCap: 'round' as const,
      strokeJoin: 'round' as const,
    };
    return o ? <Circle key={index} cx={o[0]} cy={o[1]} r={o[2]} {...props} /> : <Path key={index} path={shape(d!)} {...props} />;
  });
}

type Switch = { current: SharedValue<number>; previous: SharedValue<number>; clock: SharedValue<number> };

/** A tab's icon: its outline or, while its tab is chosen, its heavy drawing, which wiggles in on its pivot. */
function TabIcon({ index, x, y, still, current, previous, clock }: Switch & { index: number; x: number; y: number; still: boolean }) {
  const { pivot, off, on } = ICONS[index];
  const chosen = useDerivedValue(() => {
    const changed = clamp01((clock.get() * SWITCH) / ICON_FADE);
    return current.get() === index ? changed : previous.get() === index ? 1 - changed : 0;
  });
  const plain = useDerivedValue(() => 1 - chosen.get());
  const transform = useDerivedValue(() => {
    const t = current.get() === index && !still ? clock.get() : 1;
    return [
      { translateX: x + pivot[0] },
      { translateY: y + pivot[1] },
      { rotate: (wiggle(t) * Math.PI) / 180 },
      { scale: swell(t) },
      { translateX: -pivot[0] },
      { translateY: -pivot[1] },
    ];
  });
  return (
    <Group transform={transform}>
      {/* Each drawing fades as one layer, so that a cut-out detail does not show through on the way. */}
      <Group layer={<Paint opacity={plain} />}>
        <Pieces pieces={off} color={OUTLINE} />
      </Group>
      <Group layer={<Paint opacity={chosen} />}>
        <Pieces pieces={on} color={INK} />
      </Group>
    </Group>
  );
}

/** A page: it fades in when its tab is chosen and out when another is. */
function Page({ index, top, title, current, previous, clock, children }: Switch & { index: number; top: number; title: string; children: ReactNode }) {
  const style = useAnimatedStyle(() => {
    const changed = clamp01((clock.get() * SWITCH) / PAGE_FADE);
    return { opacity: current.get() === index ? changed : previous.get() === index ? 1 - changed : 0 };
  });
  return (
    <Animated.View pointerEvents="none" style={[styles.fill, styles.page, { paddingTop: top + TITLE_TOP }, style]}>
      <Text style={styles.title} numberOfLines={1} accessibilityRole="header">
        {title}
      </Text>
      {children}
    </Animated.View>
  );
}

/** Nine people: a round picture and two lines, with whatever the page puts at the end of a row. */
function Rows({ end }: { end?: (index: number) => ReactNode }) {
  return ROWS.map((width, index) => (
    <View key={width} style={styles.row}>
      <View style={styles.face} />
      <View style={styles.rowText}>
        <View style={[styles.line, { width }]} />
        <View style={[styles.line, styles.lineShort]} />
      </View>
      {end?.(index)}
    </View>
  ));
}

/**
 * A plain four-icon tab bar and one round button that changes place and purpose with the tab. The
 * icon of the chosen tab turns into a heavier drawing and wiggles; the button shrinks away on the
 * tabs that have no use for it, pops in again, and slides along an arc between its two places while
 * one glyph turns into the other.
 */
export function RoamingFabTabBar({
  titles = ['Home', 'Search', 'Add friends', 'Chats'],
  searchHint = 'Find people and rooms',
  addLabel = 'Add',
  times = TIMES,
  accent = '#1274FF',
  autoPlay = false,
  onTabChange,
  onAction,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const size = laidOut ?? window;
  const [tab, setTab] = useState(0);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  // The tab that is chosen, the one that was, and how far the change has run, 0 to 1 at a constant speed.
  const current = useSharedValue(0);
  const previous = useSharedValue(0);
  const clock = useSharedValue(1);
  // The button: 0 at the first tab's place and 1 at the last tab's, how much of it there is, and how far it is pressed in.
  const place = useSharedValue(0);
  const shown = useSharedValue(1);
  const pressed = useSharedValue(0);

  const barTop = size.height - Math.max(insets.bottom, BAR_BOTTOM) - BAR;
  const tabX = (index: number) => (size.width * (index + 0.5)) / 4;
  const homeX = size.width / 2;
  const chatX = size.width - CHAT_INSET;

  const choose = (next: number) => {
    if (next === tab) return;
    const hides = next === 1 || next === 2;
    const hidden = tab === 1 || tab === 2;
    const target = next === 3 ? 1 : 0;
    const timing = (seconds: number, ease: readonly [number, number, number, number]) => ({
      duration: seconds * 1000,
      easing: reducedMotion ? Easing.linear : Easing.bezier(ease[0], ease[1], ease[2], ease[3]),
      reduceMotion: ReduceMotion.Never,
    });
    setTab(next);
    onTabChange?.(next);
    previous.set(tab);
    current.set(next);
    clock.set(0);
    clock.set(withTiming(1, { duration: SWITCH * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }));
    if (hides) shown.set(withTiming(0, timing(SHRINK, SHRINK_EASE)));
    else if (hidden) {
      // Out of sight, the button takes its new place at once and pops in there.
      place.set(target);
      shown.set(withTiming(1, timing(POP, POP_EASE)));
    } else place.set(withTiming(target, timing(reducedMotion ? REDUCED_SLIDE : SLIDE, SLIDE_EASE)));
  };

  // The loop reads the latest function when its timers fire.
  const latest = useRef(choose);
  useEffect(() => {
    latest.current = choose;
  });

  useEffect(() => {
    if (!autoPlay) return;
    let timers: ReturnType<typeof setTimeout>[] = [];
    const cycle = (origin: number) => {
      const at = (seconds: number, step: () => void) => setTimeout(step, origin + seconds * 1000 - Date.now());
      timers = [...STEPS.map(([seconds, index]) => at(seconds, () => latest.current(index))), at(LOOP, () => cycle(origin + LOOP * 1000))];
    };
    if (touches === 0) cycle(Date.now());
    else {
      timers = [
        setTimeout(() => {
          // Back to rest; the loop's first second covers the way there.
          latest.current(0);
          cycle(Date.now());
        }, USER_HOLD * 1000),
      ];
    }
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches]);

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => setTouches((n) => n + 1);

  // Under reduced motion the button does not travel: it fades out at one place and in at the other.
  const pose = useDerivedValue(() => (reducedMotion ? (place.get() < 0.5 ? 0 : 1) : place.get()));
  const buttonSize = useDerivedValue(() => mix(HOME_SIZE, CHAT_SIZE, pose.get()));
  const radius = useDerivedValue(() => buttonSize.get() / 2);
  const buttonOpacity = useDerivedValue(() => (reducedMotion ? clamp01(shown.get()) * Math.abs(place.get() * 2 - 1) : 1));
  const buttonTransform = useDerivedValue(() => {
    const p = pose.get();
    const scale = (reducedMotion ? 1 : shown.get()) * (1 - PRESS * pressed.get());
    return [
      { translateX: mix(homeX, chatX, p) },
      { translateY: barTop - mix(HOME_RISE, CHAT_RISE, p) - ARC * Math.sin(Math.PI * clamp01(p)) },
      { scale: Math.max(0, scale) },
    ];
  });
  // One glyph turns into the other on the way across, and the plus makes a quarter turn as it shrinks away.
  const plusOpacity = useDerivedValue(() => 1 - clamp01(pose.get()));
  const composeOpacity = useDerivedValue(() => clamp01(pose.get()));
  const plusTransform = useDerivedValue(() => [{ rotate: reducedMotion ? 0 : -Math.PI * pose.get() + (Math.PI / 2) * (1 - clamp01(shown.get())) }]);
  const composeTransform = useDerivedValue(() => [{ rotate: reducedMotion ? 0 : Math.PI * (1 - pose.get()) }, { scale: (CHAT_SIZE / HOME_SIZE) * COMPOSE_SCALE }]);

  const onLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setLaidOut({ width, height });
  };

  const change = { current, previous, clock };
  const buttonRise = tab === 3 ? CHAT_RISE : HOME_RISE;
  const buttonHit = tab === 3 ? CHAT_SIZE : HOME_SIZE;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="dark" />

      <Page index={0} top={insets.top} title={titles[0]} {...change}>
        {CARDS.map((width) => (
          <View key={width} style={styles.card}>
            <View style={[styles.line, { width }]} />
            <View style={styles.faces}>
              {[0, 1, 2, 3].map((n) => (
                <View key={n} style={styles.listener} />
              ))}
            </View>
            <View style={[styles.line, styles.lineShort]} />
          </View>
        ))}
      </Page>
      <Page index={1} top={insets.top} title={titles[1]} {...change}>
        <View style={styles.field}>
          <View style={styles.lens} />
          <View style={styles.lensHandle} />
          <Text style={styles.hint} numberOfLines={1}>
            {searchHint}
          </Text>
        </View>
        <Rows />
      </Page>
      <Page index={2} top={insets.top} title={titles[2]} {...change}>
        <Rows
          end={() => (
            <View style={styles.add}>
              <Text style={[styles.addLabel, { color: accent }]}>{addLabel}</Text>
            </View>
          )}
        />
      </Page>
      <Page index={3} top={insets.top} title={titles[3]} {...change}>
        <Rows end={(index) => <Text style={styles.time}>{times[index % times.length]}</Text>} />
      </Page>

      <View style={[styles.bar, { top: barTop }]} />
      <Canvas style={styles.fill} pointerEvents="none">
        {ICONS.map((_, index) => (
          <TabIcon key={index} index={index} x={tabX(index)} y={barTop + ICON_Y} still={reducedMotion} {...change} />
        ))}
        <Group transform={buttonTransform} opacity={buttonOpacity}>
          <Circle cx={0} cy={SHADOW_Y} r={radius} color={accent} opacity={SHADOW_ALPHA}>
            <BlurMask blur={SHADOW_BLUR} style="normal" />
          </Circle>
          <Circle cx={0} cy={0} r={radius} color={accent} />
          <Group transform={plusTransform} opacity={plusOpacity}>
            <Path path={shape(PLUS)} color="#ffffff" style="stroke" strokeWidth={GLYPH_STROKE} strokeCap="round" />
          </Group>
          <Group transform={composeTransform} opacity={composeOpacity}>
            <Path path={shape(BUBBLE)} color="#ffffff" style="stroke" strokeWidth={GLYPH_STROKE} strokeJoin="round" />
            <Path path={shape(PENCIL)} color="#ffffff" style="stroke" strokeWidth={GLYPH_STROKE} strokeCap="round" />
          </Group>
        </Group>
      </Canvas>

      {ICONS.map((_, index) => (
        <Pressable
          key={index}
          style={[styles.tab, { left: tabX(index) - TAB_HIT, top: barTop + ICON_Y - TAB_HIT }]}
          accessibilityRole="tab"
          accessibilityLabel={titles[index]}
          accessibilityState={{ selected: tab === index }}
          onPress={() => {
            touched();
            if (tab !== index) select();
            choose(index);
          }}
        />
      ))}
      {(tab === 0 || tab === 3) && (
        <Pressable
          style={{
            position: 'absolute',
            left: (tab === 3 ? chatX : homeX) - buttonHit / 2,
            top: barTop - buttonRise - buttonHit / 2,
            width: buttonHit,
            height: buttonHit,
            borderRadius: buttonHit / 2,
          }}
          accessibilityRole="button"
          accessibilityLabel={tab === 3 ? 'New chat' : 'Start a room'}
          onPressIn={() => pressed.set(withTiming(1, { duration: PRESS_IN * 1000, reduceMotion: ReduceMotion.Never }))}
          onPressOut={() => pressed.set(withTiming(0, { duration: PRESS_OUT * 1000, reduceMotion: ReduceMotion.Never }))}
          onPress={() => {
            touched();
            tap();
            onAction?.(tab);
          }}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: PAGE,
  },
  fill: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
  },
  page: {
    paddingHorizontal: MARGIN,
  },
  title: {
    height: TITLE_LINE,
    marginBottom: 6,
    color: INK,
    fontSize: 34,
    lineHeight: TITLE_LINE,
    fontWeight: '700',
    includeFontPadding: false,
  },
  card: {
    height: 132,
    marginTop: 14,
    padding: 20,
    borderRadius: 24,
    borderCurve: 'continuous',
    backgroundColor: PLACEHOLDER,
    justifyContent: 'space-between',
  },
  faces: {
    flexDirection: 'row',
    paddingLeft: 10,
  },
  listener: {
    width: 40,
    height: 40,
    marginLeft: -10,
    borderRadius: 20,
    borderWidth: 2,
    borderColor: PLACEHOLDER,
    backgroundColor: AVATAR,
  },
  line: {
    height: 12,
    borderRadius: 6,
    backgroundColor: AVATAR,
  },
  lineShort: {
    width: 80,
    height: 10,
  },
  field: {
    height: 44,
    marginTop: 14,
    marginBottom: 6,
    paddingLeft: 44,
    borderRadius: 22,
    backgroundColor: PLACEHOLDER,
    justifyContent: 'center',
  },
  lens: {
    position: 'absolute',
    left: 16,
    top: 14,
    width: 13,
    height: 13,
    borderRadius: 6.5,
    borderWidth: 2,
    borderColor: MUTE,
  },
  lensHandle: {
    position: 'absolute',
    left: 26,
    top: 26,
    width: 6,
    height: 2,
    borderRadius: 1,
    backgroundColor: MUTE,
    transform: [{ rotate: '45deg' }],
  },
  hint: {
    color: MUTE,
    fontSize: 16,
    lineHeight: 21,
    includeFontPadding: false,
  },
  row: {
    height: 64,
    flexDirection: 'row',
    alignItems: 'center',
  },
  face: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: PLACEHOLDER,
  },
  rowText: {
    flex: 1,
    marginLeft: 14,
    gap: 8,
  },
  add: {
    width: 60,
    height: 30,
    borderRadius: 15,
    backgroundColor: ADD_FILL,
    alignItems: 'center',
    justifyContent: 'center',
  },
  addLabel: {
    fontSize: 14,
    lineHeight: 18,
    fontWeight: '600',
    includeFontPadding: false,
  },
  time: {
    color: MUTE,
    fontSize: 13,
    lineHeight: 17,
    fontVariant: ['tabular-nums'],
    includeFontPadding: false,
  },
  bar: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: PAGE,
  },
  tab: {
    position: 'absolute',
    width: TAB_HIT * 2,
    height: TAB_HIT * 2,
  },
});
