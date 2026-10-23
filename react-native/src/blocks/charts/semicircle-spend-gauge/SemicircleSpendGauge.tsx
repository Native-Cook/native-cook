import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Canvas, Path, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { type ComponentProps, useEffect, useRef, useState } from 'react';
import { type GestureResponderEvent, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, {
  Easing,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

export type SpendCategory = {
  name: string;
  /** The segment's colour, and the icon's glyph and circle. */
  color: string;
  ink: string;
  tint: string;
  icon: ComponentProps<typeof MaterialCommunityIcons>['name'];
};

export type SpendPeriod = {
  /** The dropdown's label: "Last Week". */
  label: string;
  /** The word in the footer: "weekly". */
  span: string;
  /** The whole half circle stands for the limit. */
  limit: number;
  /** One amount per category, in the same order. */
  amounts: number[];
};

type Props = {
  title?: string;
  /** Up to three categories, drawn left to right. */
  categories?: SpendCategory[];
  periods?: SpendPeriod[];
  /** The period shown at rest. */
  initialPeriod?: number;
  symbol?: string;
  spentLabel?: string;
  /** Pops each segment in turn and switches the period and back, every LOOP seconds. */
  autoPlay?: boolean;
  /** Called with the category's place, or -1 when the selection is cleared. */
  onSelect?: (index: number) => void;
  onPeriodChange?: (index: number, period: SpendPeriod) => void;
};

const CATEGORIES: SpendCategory[] = [
  { name: 'Shopping', color: '#375efb', ink: '#2748c8', tint: '#e8eeff', icon: 'shopping-outline' },
  { name: 'Utilities', color: '#2cbdf2', ink: '#1b93b8', tint: '#e1f6fd', icon: 'receipt-text-outline' },
  { name: 'Others', color: '#9aa4b2', ink: '#5f6368', tint: '#f3f4f5', icon: 'circle-multiple-outline' },
];

const PERIODS: SpendPeriod[] = [
  { label: 'Last Week', span: 'weekly', limit: 2000, amounts: [900, 600, 300] },
  { label: 'Last Month', span: 'monthly', limit: 8000, amounts: [2880, 2150, 1390] },
];

const PAGE = '#dfe0e2';
const CARD = '#ffffff';
const INK = '#111111';
const MUTE = '#5f6368';
const CAPTION = '#6b6b6b';
const LINE = '#e8e8e8';
const BORDER = '#e3e3e3';
const TRACK = '#e6e7e9';
const PRESSED = '#f2f3f5';

const MARGIN = 16;
const MAX_CARD = 420;
const RADIUS = 28;
const PAD = 18;
// The gauge: a half ring that pops a segment out radially, with a hairline gap between segments.
const GAUGE = 260;
const THICKNESS = 27;
const GAP = 2;
const GAUGE_TOP = 22;
const POP = 8;
// Taps this far outside the ring still pick a segment.
const TAP_SLOP = 14;
const DIMMED = 0.35;
const ICON = 40;
const ICON_POP = 1.1;

// Motion.
const POP_SPRING = { damping: 13, stiffness: 240, mass: 0.8 };
const FLOW_SPRING = { damping: 17, stiffness: 120, mass: 1 };
const DIM_FOR = 220;
const ROLL = 600;
const MENU_FOR = 200;
const MENU_FROM = 0.9;
const REDUCED_FADE = 160;

// The autoPlay timeline, in seconds from the start of each loop: each segment pops in turn, the
// selection clears, then the dropdown switches to the next period and back.
export const LOOP = 10;
const POP_AT = [0.8, 1.9, 3.0];
const CLEAR_AT = 4.1;
const OPEN_AT = [4.8, 7.4];
const PICK_AT = [5.5, 8.1];
// The menu row lights this long before the autoPlay finger picks it.
const PRESS_FOR = 0.18;
const USER_HOLD = 3;
// How long the card takes to come back to rest after a touch, before the loop restarts.
const SETTLE = 1.2;

function cents(symbol: string, value: number) {
  const whole = Math.floor(Math.round(value * 100) / 100);
  const fraction = Math.round(value * 100) % 100;
  return `${symbol}${whole.toLocaleString('en-US')}.${String(fraction).padStart(2, '0')}`;
}

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/** One segment of the half ring between two fractions of it, pushed out along its middle. */
function segment(
  cx: number,
  cy: number,
  start: number,
  end: number,
  pop: number,
  gapStart: boolean,
  gapEnd: boolean,
) {
  'worklet';
  const outer = GAUGE / 2;
  const inner = outer - THICKNESS;
  // Half the gap, as an angle at the ring's middle.
  const half = ((GAP / 2 / (outer - THICKNESS / 2)) * 180) / Math.PI;
  const a0 = 180 + start * 180 + (gapStart ? half : 0);
  const a1 = 180 + end * 180 - (gapEnd ? half : 0);
  const sweep = Math.max(0, a1 - a0);
  const middle = (((a0 + a1) / 2) * Math.PI) / 180;
  const dx = Math.cos(middle) * POP * pop;
  const dy = Math.sin(middle) * POP * pop;
  return Skia.PathBuilder.Make()
    .arcToOval({ x: cx - outer, y: cy - outer, width: outer * 2, height: outer * 2 }, a0, sweep, true)
    .arcToOval({ x: cx - inner, y: cy - inner, width: inner * 2, height: inner * 2 }, a0 + sweep, -sweep, false)
    .close()
    .offset(dx, dy)
    .build();
}

function Segment({
  index,
  ends,
  pops,
  dims,
  color,
  cx,
  cy,
  count,
}: {
  index: number;
  ends: SharedValue<number>[];
  pops: SharedValue<number>[];
  dims: SharedValue<number>[];
  color: string;
  cx: number;
  cy: number;
  count: number;
}) {
  const start = index === 0 ? null : ends[index - 1];
  const end = ends[index];
  const pop = pops[index];
  const path = useDerivedValue(() =>
    segment(cx, cy, start ? start.get() : 0, end.get(), pop.get(), index > 0, index < count - 1 || end.get() < 0.999),
  );
  const opacity = useDerivedValue(() => 1 - (1 - DIMMED) * dims[index].get());
  return <Path path={path} color={color} opacity={opacity} />;
}

function CategoryColumn({
  category,
  amount,
  pop,
  dim,
  symbol,
  first,
  onPress,
}: {
  category: SpendCategory;
  amount: number;
  pop: SharedValue<number>;
  dim: SharedValue<number>;
  symbol: string;
  first: boolean;
  onPress: () => void;
}) {
  const circleStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 + (ICON_POP - 1) * pop.get() }] }));
  const fadeStyle = useAnimatedStyle(() => ({ opacity: 1 - (1 - DIMMED) * dim.get() }));
  return (
    <Pressable
      style={[styles.column, !first && styles.columnRule]}
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={`${category.name}, ${cents(symbol, amount)}`}
    >
      <Animated.View style={[styles.columnInner, fadeStyle]}>
        <Animated.View style={[styles.iconCircle, { backgroundColor: category.tint }, circleStyle]}>
          <MaterialCommunityIcons name={category.icon} size={20} color={category.ink} />
        </Animated.View>
        <Text style={styles.columnLabel} numberOfLines={1}>
          {category.name}
        </Text>
        <Text style={styles.columnAmount} numberOfLines={1} adjustsFontSizeToFit>
          {cents(symbol, amount)}
        </Text>
      </Animated.View>
    </Pressable>
  );
}

/**
 * A spending summary card around a half-donut gauge: the half circle is the period's limit and
 * each category takes its share of it. Tapping a segment pops it out and dims the rest while the
 * centre rolls to its total; the dropdown switches the period, and the segments re-flow on a
 * spring as every amount rolls to its new value.
 */
export function SemicircleSpendGauge({
  title = 'Spending Summary',
  categories = CATEGORIES,
  periods = PERIODS,
  initialPeriod = 0,
  symbol = '$',
  spentLabel = 'SPENT',
  autoPlay = false,
  onSelect,
  onPeriodChange,
}: Props) {
  const reduced = useReducedMotion();
  const window = useWindowDimensions();
  const cardWidth = Math.min(MAX_CARD, window.width - MARGIN * 2);
  const rest = Math.min(Math.max(0, initialPeriod), periods.length - 1);

  const [period, setPeriod] = useState(rest);
  const [selected, setSelected] = useState(-1);
  const [menuOpen, setMenuOpen] = useState(false);
  const [autoPressed, setAutoPressed] = useState(-1);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  const count = categories.length;
  const fractions = (index: number) => {
    const { amounts, limit } = periods[index];
    let sum = 0;
    return categories.map((_, i) => (sum = Math.min(1, sum + (amounts[i] ?? 0) / limit)));
  };

  // Where each segment ends along the half ring (0 left, 1 right), its pop and its dimming.
  const initialEnds = fractions(rest);
  const end0 = useSharedValue(initialEnds[0] ?? 0);
  const end1 = useSharedValue(initialEnds[1] ?? 0);
  const end2 = useSharedValue(initialEnds[2] ?? 0);
  const pop0 = useSharedValue(0);
  const pop1 = useSharedValue(0);
  const pop2 = useSharedValue(0);
  const dim0 = useSharedValue(0);
  const dim1 = useSharedValue(0);
  const dim2 = useSharedValue(0);
  const ends = [end0, end1, end2].slice(0, count);
  const pops = [pop0, pop1, pop2];
  const dims = [dim0, dim1, dim2];

  // The numbers on the card roll: the centre, then each category.
  const amounts = periods[period].amounts;
  const total = amounts.reduce((sum, value) => sum + value, 0);
  const centre = useSharedValue(total);
  const column0 = useSharedValue(amounts[0] ?? 0);
  const column1 = useSharedValue(amounts[1] ?? 0);
  const column2 = useSharedValue(amounts[2] ?? 0);
  const columnValues = [column0, column1, column2];
  const [numbers, setNumbers] = useState({ centre: total, columns: amounts.slice() });
  const menu = useSharedValue(0);

  const roll = (value: SharedValue<number>, to: number) =>
    value.set(reduced ? to : withTiming(to, { duration: ROLL, easing: Easing.out(Easing.cubic) }));

  useEffect(() => {
    const target = periods[period].amounts;
    const next = fractions(period);
    ends.forEach((value, i) => value.set(reduced ? next[i] : withSpring(next[i], FLOW_SPRING)));
    target.forEach((value, i) => columnValues[i] && roll(columnValues[i], value));
    // The values only follow the period and the selection.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [period]);

  useEffect(() => {
    const target = periods[period].amounts;
    roll(centre, selected < 0 ? target.reduce((sum, value) => sum + value, 0) : target[selected]);
    pops.forEach((value, i) => {
      const on = i === selected ? 1 : 0;
      value.set(reduced ? 0 : withSpring(on, POP_SPRING));
    });
    dims.forEach((value, i) => {
      const off = selected >= 0 && i !== selected ? 1 : 0;
      value.set(withTiming(off, { duration: reduced ? REDUCED_FADE : DIM_FOR }));
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selected, period]);

  useEffect(() => {
    menu.set(withTiming(menuOpen ? 1 : 0, { duration: reduced ? REDUCED_FADE : MENU_FOR, easing: Easing.out(Easing.cubic) }));
  }, [menuOpen, menu, reduced]);

  useAnimatedReaction(
    () => [
      Math.round(centre.get() * 100),
      Math.round(column0.get() * 100),
      Math.round(column1.get() * 100),
      Math.round(column2.get() * 100),
    ],
    (now, before) => {
      if (before && now[0] === before[0] && now[1] === before[1] && now[2] === before[2] && now[3] === before[3]) return;
      scheduleOnRN(setNumbers, { centre: now[0] / 100, columns: [now[1] / 100, now[2] / 100, now[3] / 100] });
    },
  );

  const select = (index: number, byHand: boolean) => {
    const next = index === selected ? -1 : index;
    setSelected(next);
    if (byHand) tick();
    onSelect?.(next);
  };

  const pick = (index: number, byHand: boolean) => {
    setMenuOpen(false);
    setAutoPressed(-1);
    if (index === period) return;
    setSelected(-1);
    setPeriod(index);
    if (byHand) tick();
    onPeriodChange?.(index, periods[index]);
  };

  // The loop reads the latest state when its timers fire.
  const latest = useRef({ select, pick });
  useEffect(() => {
    latest.current = { select, pick };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    const at = (origin: number, seconds: number, run: () => void) => after(origin + seconds * 1000 - Date.now(), run);
    const cycle = (origin: number) => {
      POP_AT.forEach((s, i) => at(origin, s, () => latest.current.select(i, false)));
      at(origin, CLEAR_AT, () => latest.current.select(-1, false));
      OPEN_AT.forEach((s, i) => {
        const target = i === 0 ? (rest + 1) % periods.length : rest;
        at(origin, s, () => setMenuOpen(true));
        at(origin, PICK_AT[i] - PRESS_FOR, () => setAutoPressed(target));
        at(origin, PICK_AT[i], () => latest.current.pick(target, false));
      });
      at(origin, LOOP, () => cycle(origin + LOOP * 1000));
    };
    if (touches === 0) cycle(Date.now());
    else
      after(USER_HOLD * 1000, () => {
        // Back to rest, then the loop starts once everything has settled.
        setMenuOpen(false);
        setAutoPressed(-1);
        setSelected(-1);
        setPeriod(rest);
        after(SETTLE * 1000, () => cycle(Date.now()));
      });
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches, rest, periods.length]);

  const touched = () => setTouches((n) => n + 1);

  const gaugeWidth = GAUGE + POP * 2;
  const gaugeHeight = GAUGE / 2 + POP;
  const cx = gaugeWidth / 2;
  const cy = gaugeHeight;

  const onGaugePress = (event: GestureResponderEvent) => {
    touched();
    // The touch is measured in the padded area the gauge is centred in.
    const dx = event.nativeEvent.locationX - ((cardWidth - PAD * 2 - gaugeWidth) / 2 + cx);
    const dy = event.nativeEvent.locationY - (GAUGE_TOP + cy);
    const distance = Math.hypot(dx, dy);
    if (distance < GAUGE / 2 - THICKNESS - TAP_SLOP || distance > GAUGE / 2 + TAP_SLOP || dy > TAP_SLOP) {
      if (selected >= 0) select(selected, true);
      return;
    }
    // The fraction along the half ring, 0 at the left end.
    const along = Math.min(1, Math.max(0, 1 - Math.atan2(-dy, dx) / Math.PI));
    const index = fractions(period).findIndex((f) => along <= f);
    if (index >= 0) select(index, true);
    else if (selected >= 0) select(selected, true);
  };

  const menuStyle = useAnimatedStyle(() => ({
    opacity: menu.get(),
    transform: [{ scale: reduced ? 1 : MENU_FROM + (1 - MENU_FROM) * menu.get() }],
  }));

  const current = periods[period];
  const centreLabel = selected < 0 ? spentLabel : categories[selected].name.toUpperCase();

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <View style={[styles.card, { width: cardWidth }]}>
        <View style={styles.header}>
          <View style={styles.titleRow}>
            <MaterialCommunityIcons name="chart-pie-outline" size={22} color={MUTE} />
            <Text style={styles.title} numberOfLines={1}>
              {title}
            </Text>
          </View>
          <Pressable
            style={styles.pill}
            accessibilityRole="button"
            accessibilityLabel={`Period: ${current.label}`}
            onPress={() => {
              touched();
              setMenuOpen((open) => !open);
            }}
          >
            <Text style={styles.pillLabel}>{current.label}</Text>
            <MaterialCommunityIcons name="chevron-down" size={18} color={INK} />
          </Pressable>
        </View>
        <View style={styles.divider} />

        <Pressable style={styles.gaugeArea} onPress={onGaugePress} accessibilityRole="none">
          <View style={{ width: gaugeWidth, height: gaugeHeight }} pointerEvents="none">
            <Canvas style={StyleSheet.absoluteFill}>
              <Track ends={ends} cx={cx} cy={cy} />
              {categories.map((category, i) => (
                <Segment
                  key={category.name}
                  index={i}
                  ends={ends}
                  pops={pops}
                  dims={dims}
                  color={category.color}
                  cx={cx}
                  cy={cy}
                  count={count}
                />
              ))}
            </Canvas>
            <View style={styles.centre} accessible accessibilityLabel={`${centreLabel} ${cents(symbol, numbers.centre)}`}>
              <Text style={styles.centreLabel} numberOfLines={1}>
                {centreLabel}
              </Text>
              <Text style={styles.centreValue} numberOfLines={1} adjustsFontSizeToFit>
                {cents(symbol, numbers.centre)}
              </Text>
            </View>
          </View>
        </Pressable>
        <View style={styles.divider} />

        <View style={styles.columns}>
          {categories.map((category, i) => (
            <CategoryColumn
              key={category.name}
              category={category}
              amount={numbers.columns[i] ?? 0}
              pop={pops[i]}
              dim={dims[i]}
              symbol={symbol}
              first={i === 0}
              onPress={() => {
                touched();
                select(i, true);
              }}
            />
          ))}
        </View>

        <View style={styles.footer}>
          <Text style={styles.footerText} numberOfLines={1}>
            Your {current.span} spending limit is <Text style={styles.footerAmount}>{`${symbol}${current.limit}.`}</Text>
          </Text>
          <MaterialCommunityIcons name="information" size={16} color="#8e9196" />
        </View>

        {menuOpen && (
          <Pressable
            style={StyleSheet.absoluteFill}
            accessibilityLabel="Close menu"
            onPress={() => {
              touched();
              setMenuOpen(false);
            }}
          />
        )}
        <Animated.View pointerEvents={menuOpen ? 'auto' : 'none'} style={[styles.menu, menuStyle]}>
          {periods.map((p, i) => (
            <Pressable
              key={p.label}
              onPress={() => {
                touched();
                pick(i, true);
              }}
              accessibilityRole="menuitem"
              accessibilityState={{ selected: i === period }}
            >
              {({ pressed }) => (
                <View style={[styles.menuRow, (pressed || autoPressed === i) && styles.menuRowPressed]}>
                  <Text style={styles.menuLabel}>{p.label}</Text>
                  {i === period && <MaterialCommunityIcons name="check" size={18} color={categories[0]?.color ?? INK} />}
                </View>
              )}
            </Pressable>
          ))}
        </Animated.View>
      </View>
    </View>
  );
}

/** The grey rest of the half ring: the part of the limit not spent. */
function Track({ ends, cx, cy }: { ends: SharedValue<number>[]; cx: number; cy: number }) {
  const last = ends[ends.length - 1];
  const path = useDerivedValue(() => segment(cx, cy, last.get(), 1, 0, true, false));
  return <Path path={path} color={TRACK} />;
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: PAGE,
  },
  card: {
    borderRadius: RADIUS,
    borderCurve: 'continuous',
    backgroundColor: CARD,
    paddingHorizontal: PAD,
    paddingBottom: PAD,
    boxShadow: [{ offsetX: 0, offsetY: 16, blurRadius: 40, color: 'rgba(0,0,0,0.06)' }],
  },
  header: {
    height: 72,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  titleRow: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  title: {
    flexShrink: 1,
    color: INK,
    fontSize: 18,
    lineHeight: 24,
    fontWeight: '600',
  },
  pill: {
    height: 38,
    paddingLeft: 14,
    paddingRight: 10,
    borderRadius: 12,
    borderCurve: 'continuous',
    borderWidth: 1,
    borderColor: BORDER,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  pillLabel: {
    color: INK,
    fontSize: 15,
    lineHeight: 20,
  },
  divider: {
    height: 1,
    backgroundColor: LINE,
  },
  gaugeArea: {
    alignItems: 'center',
    paddingTop: GAUGE_TOP,
    paddingBottom: 18,
  },
  centre: {
    position: 'absolute',
    left: POP + THICKNESS + 8,
    right: POP + THICKNESS + 8,
    bottom: -2,
    alignItems: 'center',
  },
  centreLabel: {
    color: CAPTION,
    fontSize: 14,
    lineHeight: 18,
    letterSpacing: 1.6,
  },
  centreValue: {
    marginTop: 6,
    color: INK,
    fontSize: 32,
    lineHeight: 38,
    fontWeight: '600',
    letterSpacing: -0.8,
    fontVariant: ['tabular-nums'],
  },
  columns: {
    flexDirection: 'row',
    paddingVertical: 18,
  },
  column: {
    flex: 1,
  },
  columnRule: {
    borderLeftWidth: 1,
    borderLeftColor: LINE,
  },
  columnInner: {
    alignItems: 'center',
    paddingHorizontal: 6,
  },
  iconCircle: {
    width: ICON,
    height: ICON,
    borderRadius: ICON / 2,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 14,
  },
  columnLabel: {
    color: MUTE,
    fontSize: 15,
    lineHeight: 20,
  },
  columnAmount: {
    marginTop: 4,
    color: INK,
    fontSize: 17,
    lineHeight: 22,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  footer: {
    height: 44,
    paddingHorizontal: 14,
    borderRadius: 14,
    borderCurve: 'continuous',
    borderWidth: 1,
    borderColor: BORDER,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 8,
  },
  footerText: {
    flexShrink: 1,
    color: MUTE,
    fontSize: 14,
    lineHeight: 19,
  },
  footerAmount: {
    color: INK,
    fontWeight: '600',
  },
  menu: {
    position: 'absolute',
    top: 62,
    right: PAD,
    width: 168,
    paddingVertical: 6,
    borderRadius: 14,
    borderCurve: 'continuous',
    backgroundColor: CARD,
    borderWidth: 1,
    borderColor: BORDER,
    boxShadow: [{ offsetX: 0, offsetY: 10, blurRadius: 28, color: 'rgba(0,0,0,0.12)' }],
    transformOrigin: 'top right',
  },
  menuRow: {
    height: 40,
    marginHorizontal: 6,
    paddingHorizontal: 10,
    borderRadius: 9,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  menuRowPressed: {
    backgroundColor: PRESSED,
  },
  menuLabel: {
    color: INK,
    fontSize: 15,
    lineHeight: 20,
  },
});
