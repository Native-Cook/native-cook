import { Ionicons, MaterialCommunityIcons } from '@expo/vector-icons';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { type ComponentProps, useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, TextInput, type TextInputProps, View } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  type SharedValue,
  useAnimatedProps,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { CandleChart, LINE_GREEN, frameAt } from './CandleChart';
import {
  LOOP,
  RANGES,
  type Range,
  STEPS,
  USER_HOLD,
  VIEWS,
  clamp01,
  dateLabel,
  duration,
  frames,
  lineAt,
  scrubAt,
} from './candle-data';
import { RollingNumber } from './RollingNumber';

export type { Range };
export { LOOP };

type Props = {
  name?: string;
  ticker?: string;
  currency?: string;
  /** The currency the price is quoted in, after the ticker. */
  quote?: string;
  /** The price after the close, under the change. */
  afterHours?: string;
  ranges?: Range[];
  /** Scrubs, switches range and view by itself, every LOOP seconds. */
  autoPlay?: boolean;
  noteAuthor?: string;
  note?: string;
  onRangeChange?: (range: Range, index: number) => void;
  onViewChange?: (view: string) => void;
  onBack?: () => void;
};

const PAGE = '#000000';
const INK = '#e7e9ea';
const MUTE = '#71767b';
const SURFACE = '#16181c';
const PILL_ON = '#2f3336';
const RULE = '#2f3336';
const MENU = 'rgba(32,35,39,0.97)';
const MENU_HOVER = 'rgba(255,255,255,0.12)';
const GREEN_TEXT = '#00b96b';
const RED_TEXT = '#f6465d';

const MARGIN = 16;
const LOGO = 40;
const CHART_PAD = 14;
const CHART_MIN = 200;
const DATE_H = 28;
const PILL_W = 46;
const PILL_H = 30;
const VIEW_W = 58;
const MENU_W = 184;
const MENU_ROW = 44;
const MENU_GAP = 8;
const MENU_SPRING = { damping: 20, stiffness: 380, mass: 0.8 };
const MENU_FROM = 0.8;
const MENU_OUT = 140;
const SLIDE = 220;
const HELD_IN = 150;
const HELD_OUT = 220;
const SMOOTH = Easing.inOut(Easing.cubic);

const AnimatedTextInput = Animated.createAnimatedComponent(TextInput);

/** Text set from the UI thread, so a price that changes every frame costs no React render. */
function LiveText({ text, style }: { text: SharedValue<string>; style: ComponentProps<typeof AnimatedTextInput>['style'] }) {
  const animatedProps = useAnimatedProps(() => ({ text: text.get(), defaultValue: text.get() }) as TextInputProps);
  return (
    <AnimatedTextInput
      editable={false}
      pointerEvents="none"
      accessible={false}
      underlineColorAndroid="transparent"
      style={[styles.live, style]}
      animatedProps={animatedProps}
    />
  );
}

/** Two small candles, for the views drawn as candles. */
function CandleGlyph({ color }: { color: string }) {
  return (
    <View style={styles.glyph}>
      {[0, 1].map((index) => (
        <View key={index} style={[styles.glyphCandle, { marginTop: index * 3 }]}>
          <View style={[styles.glyphWick, { backgroundColor: color }]} />
          <View style={[styles.glyphBody, { backgroundColor: color }]} />
        </View>
      ))}
    </View>
  );
}

function ViewIcon({ group, color }: { group: number; color: string }) {
  return group === 0 ? <MaterialCommunityIcons name="chart-line-variant" size={16} color={color} /> : <CandleGlyph color={color} />;
}

function MenuRow({ index, label, group, hover, check, onPress }: { index: number; label: string; group: number; hover: SharedValue<number>; check: SharedValue<number>; onPress: () => void }) {
  const rowStyle = useAnimatedStyle(() => ({ backgroundColor: hover.get() === index ? MENU_HOVER : 'transparent' }));
  // The check slides from row to row as the view changes.
  const checkStyle = useAnimatedStyle(() => ({ opacity: clamp01(1 - Math.abs(check.get() - index) * 1.5) }));
  return (
    <Pressable onPress={onPress} accessibilityRole="menuitem" accessibilityLabel={label}>
      {({ pressed }) => (
        <Animated.View style={[styles.menuRow, index > 0 && styles.menuRule, rowStyle, pressed && { backgroundColor: MENU_HOVER }]}>
          <Animated.View style={[styles.check, checkStyle]}>
            <Ionicons name="checkmark" size={17} color={INK} />
          </Animated.View>
          <Text style={styles.menuLabel}>{label}</Text>
          <ViewIcon group={group} color={INK} />
        </Animated.View>
      )}
    </Pressable>
  );
}

function haptic() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

/**
 * A stock screen whose area chart turns into candles. The view menu opens from its button; choosing
 * an interval fades the line and grows a candle out of every point, left to right; a longer interval
 * merges neighbouring candles into wider ones, and Line collapses them back into the line. Ranges
 * morph the data, and a finger scrubs the price.
 */
export function LineToCandlesSwitch({
  name = 'Northwind Corp',
  ticker = 'NWND',
  currency = '$',
  quote = 'USD',
  afterHours = 'After hours $198.52 (−0.18%)',
  ranges = RANGES,
  autoPlay = false,
  noteAuthor = 'Market note',
  note = 'Northwind closed the week up almost ten percent after its quarterly results beat estimates.',
  onRangeChange,
  onViewChange,
  onBack,
}: Props) {
  const still = useReducedMotion();
  const insets = useSafeAreaInsets();
  const [chart, setChart] = useState({ width: 0, height: 0 });
  const [range, setRange] = useState(STEPS[0].range);
  const [view, setView] = useState(STEPS[0].view);
  const [menuOpen, setMenuOpen] = useState(false);
  const tables = useMemo(() => ranges.map((r, index) => frames(r, index + 1)), [ranges]);
  const spans = useMemo(() => ranges.map((r) => ({ start: r.start, end: r.end, hours: r.hours })), [ranges]);

  const first = tables[STEPS[0].range][STEPS[0].view];
  const from = useSharedValue(first);
  const to = useSharedValue(first);
  const time = useSharedValue(1);
  const length = useSharedValue(1);
  const shownRange = useSharedValue(STEPS[0].range);
  const shownView = useSharedValue(STEPS[0].view);
  const slot = useSharedValue(STEPS[0].range);
  const check = useSharedValue(STEPS[0].view);
  const menu = useSharedValue(0);
  const menuState = useSharedValue(-1);
  const hover = useSharedValue(-1);
  const cursor = useSharedValue(0.5);
  const held = useSharedValue(0);
  const down = useSharedValue(0);
  const auto = useSharedValue(autoPlay);
  const origin = useSharedValue(-1);

  const chosen = (r: number, v: number) => {
    setRange(r);
    setView(v);
  };

  /** Shows a range in a view, morphing from whatever is on screen now. */
  const show = (r: number, v: number) => {
    'worklet';
    const next = tables[r][v];
    const now = frameAt(from.get(), to.get(), time.get(), length.get());
    const d = still ? 0 : duration(now, next);
    from.set(now);
    to.set(next);
    length.set(Math.max(d, 0.001));
    time.set(0);
    time.set(d === 0 ? 1 : withTiming(d, { duration: d * 1000, easing: Easing.linear }));
    slot.set(still ? r : withTiming(r, { duration: SLIDE, easing: SMOOTH }));
    check.set(still ? v : withTiming(v, { duration: SLIDE, easing: SMOOTH }));
    shownRange.set(r);
    shownView.set(v);
    scheduleOnRN(chosen, r, v);
  };

  /** -1 closes the menu; a row number opens it with that row under the finger (4: none). */
  const setMenu = (state: number) => {
    'worklet';
    const open = state >= 0;
    menuState.set(state);
    hover.set(state);
    menu.set(still ? (open ? 1 : 0) : open ? withSpring(1, MENU_SPRING) : withTiming(0, { duration: MENU_OUT }));
    scheduleOnRN(setMenuOpen, open);
  };

  useFrameCallback((info) => {
    if (!auto.get()) return;
    if (origin.get() < 0) origin.set(info.timestamp);
    const t = (((info.timestamp - origin.get()) / 1000) % LOOP + LOOP) % LOOP;
    let k = 0;
    for (let i = 0; i < STEPS.length; i++) if (STEPS[i].at <= t) k = i;
    const step = STEPS[k];
    if (step.range !== shownRange.get() || step.view !== shownView.get()) show(step.range, step.view);
    if (step.menu !== menuState.get()) setMenu(step.menu);
    const scrub = scrubAt(t);
    const x = still ? 0.55 : scrub.x;
    if (cursor.get() !== x) cursor.set(x);
    if (held.get() !== scrub.held) held.set(still ? scrub.down : scrub.held);
    if (down.get() !== scrub.down) down.set(scrub.down);
  });

  // A touch takes over at once; some seconds after the last one the loop plays again from its first state.
  const [touches, setTouches] = useState(0);
  const touched = () => setTouches((n) => n + 1);
  useEffect(() => {
    if (!autoPlay || touches === 0) return;
    const replay = setTimeout(() => {
      if (down.get()) return;
      origin.set(-1);
      auto.set(true);
    }, USER_HOLD * 1000);
    return () => clearTimeout(replay);
  }, [autoPlay, touches, down, origin, auto]);

  /** A tap off the chart stops autoPlay where it is, lifting the finger it may have had down. */
  const takeOver = () => {
    'worklet';
    auto.set(false);
    down.set(0);
    held.set(still ? 0 : withTiming(0, { duration: HELD_OUT }));
  };

  const chooseRange = (r: number) => {
    'worklet';
    takeOver();
    if (menuState.get() >= 0) setMenu(-1);
    show(r, shownView.get());
  };

  const chooseView = (v: number) => {
    'worklet';
    takeOver();
    setMenu(-1);
    show(shownRange.get(), v);
  };

  const flipMenu = () => {
    'worklet';
    takeOver();
    setMenu(menuState.get() >= 0 ? -1 : 4);
  };

  const pickRange = (r: number) => {
    touched();
    haptic();
    onRangeChange?.(ranges[r], r);
    scheduleOnUI(chooseRange, r);
  };

  const pickView = (v: number) => {
    touched();
    haptic();
    onViewChange?.(VIEWS[v].label);
    scheduleOnUI(chooseView, v);
  };

  const toggleMenu = () => {
    touched();
    scheduleOnUI(flipMenu);
  };

  const pan = Gesture.Pan()
    .minDistance(0)
    .shouldCancelWhenOutside(false)
    .onBegin((event) => {
      auto.set(false);
      if (menuState.get() >= 0) setMenu(-1);
      cursor.set(clamp01(event.x / chart.width));
      down.set(1);
      held.set(still ? 1 : withTiming(1, { duration: HELD_IN }));
      scheduleOnRN(touched);
    })
    .onUpdate((event) => {
      cursor.set(clamp01(event.x / chart.width));
    })
    .onFinalize(() => {
      down.set(0);
      held.set(still ? 0 : withTiming(0, { duration: HELD_OUT }));
      scheduleOnRN(touched);
    });

  // The header reads the price under the finger, or the latest one, against the range's first.
  const price = useDerivedValue(() => {
    const line = to.get().line;
    return down.get() ? lineAt(line, cursor.get()) : line[line.length - 1];
  });
  const change = useDerivedValue(() => price.get() - to.get().line[0]);
  const changeText = useDerivedValue(() => {
    const c = change.get();
    const percent = (c / to.get().line[0]) * 100;
    return `${c < 0 ? '▼' : '▲'} ${currency}${Math.abs(c).toFixed(2)} (${c < 0 ? '−' : '+'}${Math.abs(percent).toFixed(2)}%)`;
  });
  const changeStyle = useAnimatedStyle(() => ({ color: change.get() < 0 ? RED_TEXT : GREEN_TEXT }));
  const dateText = useDerivedValue(() => {
    const span = spans[shownRange.get()];
    return dateLabel(span.start + cursor.get() * (span.end - span.start), span.hours);
  });
  const dateStyle = useAnimatedStyle(() => ({ opacity: held.get() }));
  const pillStyle = useAnimatedStyle(() => ({ transform: [{ translateX: slot.get() * PILL_W }] }));
  const menuStyle = useAnimatedStyle(() => ({
    opacity: clamp01(menu.get()),
    transform: [{ scale: MENU_FROM + (1 - MENU_FROM) * menu.get() }],
  }));

  const onChartLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setChart({ width, height: height - DATE_H });
  };

  const priceFont = { fontSize: 34, lineHeight: 41, fontWeight: '700' as const, color: INK, letterSpacing: -0.5 };

  return (
    <GestureHandlerRootView style={styles.screen}>
      <StatusBar style="light" />
      <View style={[styles.bar, { marginTop: insets.top }]}>
        <Pressable hitSlop={10} accessibilityRole="button" accessibilityLabel="Back" onPress={onBack}>
          <Ionicons name="arrow-back" size={24} color={INK} />
        </Pressable>
      </View>

      <View style={styles.header}>
        <View style={styles.company}>
          <View style={styles.logo}>
            <Text style={styles.logoMark}>{name.slice(0, 1)}</Text>
          </View>
          <View>
            <Text style={styles.name}>{name}</Text>
            <Text style={styles.ticker}>
              {ticker} · {quote}
            </Text>
          </View>
        </View>
        <View style={styles.price} accessible accessibilityLabel={`${name} price`}>
          <Text style={priceFont}>{currency}</Text>
          <RollingNumber value={price} decimals={2} wholeDigits={3} font={priceFont} still={still} />
        </View>
        <LiveText text={changeText} style={[styles.change, changeStyle]} />
        <Text style={styles.afterHours}>{afterHours}</Text>
      </View>

      <GestureDetector gesture={pan}>
        <View style={styles.chart} onLayout={onChartLayout} accessible accessibilityLabel={`${name} chart, ${ranges[range].label}, ${VIEWS[view].label}`}>
          {chart.width > 0 && (
            <CandleChart width={chart.width} height={chart.height} pad={CHART_PAD} from={from} to={to} time={time} length={length} cursor={cursor} held={held} />
          )}
          <LiveText text={dateText} style={[styles.date, dateStyle]} />
        </View>
      </GestureDetector>

      <View style={styles.controls}>
        <View style={styles.pills} accessibilityRole="tablist">
          <Animated.View style={[styles.pill, pillStyle]} />
          {ranges.map((r, index) => (
            <Pressable key={r.label} style={styles.pillSlot} onPress={() => pickRange(index)} accessibilityRole="tab" accessibilityState={{ selected: range === index }}>
              <Text style={[styles.pillLabel, range === index && styles.pillLabelOn]}>{r.label}</Text>
            </Pressable>
          ))}
        </View>
        <Pressable onPress={toggleMenu} accessibilityRole="button" accessibilityLabel={`Chart type, ${VIEWS[view].label}`} accessibilityState={{ expanded: menuOpen }}>
          <View style={styles.viewButton}>
            <ViewIcon group={VIEWS[view].group} color={INK} />
            <Ionicons name="chevron-expand" size={13} color={MUTE} />
          </View>
        </Pressable>
        <Animated.View pointerEvents={menuOpen ? 'auto' : 'none'} style={[styles.menu, menuStyle]} accessibilityRole="menu">
          {VIEWS.map((v, index) => (
            <MenuRow key={v.label} index={index} label={v.label} group={v.group} hover={hover} check={check} onPress={() => pickView(index)} />
          ))}
        </Animated.View>
      </View>

      <View style={[styles.note, { marginBottom: Math.max(insets.bottom, 16) }]}>
        <View style={styles.avatar}>
          <MaterialCommunityIcons name="newspaper-variant-outline" size={20} color={INK} />
        </View>
        <View style={styles.noteText}>
          <Text style={styles.noteAuthor}>{noteAuthor}</Text>
          <Text style={styles.noteBody} numberOfLines={2} lineBreakStrategyIOS="standard">
            {note}
          </Text>
        </View>
      </View>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: PAGE,
  },
  bar: {
    height: 44,
    justifyContent: 'center',
    paddingHorizontal: MARGIN,
  },
  header: {
    paddingHorizontal: MARGIN,
    marginTop: 4,
  },
  company: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  logo: {
    width: LOGO,
    height: LOGO,
    borderRadius: 12,
    borderCurve: 'continuous',
    backgroundColor: LINE_GREEN,
    alignItems: 'center',
    justifyContent: 'center',
  },
  logoMark: {
    color: '#ffffff',
    fontSize: 20,
    lineHeight: 24,
    fontWeight: '800',
  },
  name: {
    color: INK,
    fontSize: 17,
    lineHeight: 22,
    fontWeight: '600',
  },
  ticker: {
    color: MUTE,
    fontSize: 14,
    lineHeight: 18,
  },
  price: {
    flexDirection: 'row',
    marginTop: 10,
  },
  live: {
    padding: 0,
    margin: 0,
  },
  change: {
    marginTop: 2,
    height: 20,
    fontSize: 15,
    lineHeight: 20,
    fontWeight: '500',
    fontVariant: ['tabular-nums'],
  },
  afterHours: {
    marginTop: 2,
    color: MUTE,
    fontSize: 13,
    lineHeight: 18,
  },
  chart: {
    flex: 1,
    minHeight: CHART_MIN + DATE_H,
    marginTop: 12,
  },
  date: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    height: DATE_H,
    textAlign: 'center',
    color: MUTE,
    fontSize: 14,
    lineHeight: 18,
  },
  controls: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: 12,
    paddingHorizontal: MARGIN,
    zIndex: 1,
  },
  pills: {
    flexDirection: 'row',
  },
  pill: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: PILL_W,
    height: PILL_H,
    borderRadius: PILL_H / 2,
    backgroundColor: PILL_ON,
  },
  pillSlot: {
    width: PILL_W,
    height: PILL_H,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pillLabel: {
    color: MUTE,
    fontSize: 14,
    lineHeight: 18,
    fontWeight: '600',
  },
  pillLabelOn: {
    color: INK,
  },
  viewButton: {
    width: VIEW_W,
    height: PILL_H,
    borderRadius: PILL_H / 2,
    backgroundColor: SURFACE,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 4,
  },
  glyph: {
    width: 16,
    height: 16,
    flexDirection: 'row',
    justifyContent: 'center',
    gap: 3,
  },
  glyphCandle: {
    alignItems: 'center',
    height: 13,
  },
  glyphWick: {
    position: 'absolute',
    width: 1,
    top: 0,
    bottom: 0,
  },
  glyphBody: {
    width: 5,
    height: 8,
    marginTop: 2,
    borderRadius: 1,
  },
  menu: {
    position: 'absolute',
    right: MARGIN,
    bottom: PILL_H + MENU_GAP,
    width: MENU_W,
    borderRadius: 14,
    borderCurve: 'continuous',
    backgroundColor: MENU,
    overflow: 'hidden',
    transformOrigin: 'right bottom',
    boxShadow: [{ offsetX: 0, offsetY: 8, blurRadius: 24, color: 'rgba(0,0,0,0.6)' }],
  },
  menuRow: {
    height: MENU_ROW,
    flexDirection: 'row',
    alignItems: 'center',
    paddingLeft: 10,
    paddingRight: 16,
  },
  menuRule: {
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: RULE,
  },
  check: {
    width: 26,
  },
  menuLabel: {
    flex: 1,
    color: INK,
    fontSize: 16,
    lineHeight: 21,
  },
  note: {
    flexDirection: 'row',
    gap: 12,
    marginTop: 20,
    paddingTop: 14,
    paddingHorizontal: MARGIN,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: RULE,
  },
  avatar: {
    width: LOGO,
    height: LOGO,
    borderRadius: LOGO / 2,
    backgroundColor: SURFACE,
    alignItems: 'center',
    justifyContent: 'center',
  },
  noteText: {
    flex: 1,
  },
  noteAuthor: {
    color: INK,
    fontSize: 15,
    lineHeight: 20,
    fontWeight: '700',
  },
  noteBody: {
    marginTop: 2,
    color: INK,
    fontSize: 15,
    lineHeight: 20,
  },
});
