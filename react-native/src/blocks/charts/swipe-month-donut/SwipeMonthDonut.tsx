import { Ionicons } from '@expo/vector-icons';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  type EntryAnimationsValues,
  type ExitAnimationsValues,
  LinearTransition,
  type SharedValue,
  cancelAnimation,
  useAnimatedReaction,
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

import { DonutRing } from './DonutRing';
import {
  CATEGORIES,
  type Category,
  FOCUS_CATEGORY,
  FOCUS_OFF,
  FOCUS_ON,
  LOOP,
  MONTHS,
  type Month,
  RETURN_FOR,
  USER_HOLD,
  clamp01,
  pageAt,
  springOut,
  shares as sharesOf,
  total,
} from './donut-data';

export type { Category, Month };
export { LOOP };

type Props = {
  /** The ring's categories, in its clockwise order. */
  categories?: Category[];
  /** One page each; `amounts` follow the order of `categories`. */
  months?: Month[];
  currency?: string;
  kinds?: [string, string];
  /** Swipes through the months by itself and taps one segment, every LOOP seconds. */
  autoPlay?: boolean;
  onMonthChange?: (month: Month, index: number) => void;
  onCategory?: (category: Category | null) => void;
  onAdd?: () => void;
};

const PAGE = '#f2f2f7';
const CARD = '#ffffff';
const INK = '#111111';
const MUTE = '#8e8e93';
const FAINT = '#c7c7cc';
const RULE = '#e5e5ea';
const TRACK = '#e3e3e8';
const BADGE = '#efeff4';

const MARGIN = 20;
const RING = 300;
const STROKE = 22;
const GAP = 4;
const POP = 6;
const BAR = 44;
const SEGMENTED_W = 172;
const SEGMENTED_H = 30;
const ROW = 44;
const DOT = 7;
const DOT_ACTIVE = 7;
const PLUS = 56;

// A swipe across this share of the ring turns one page; a fast flick turns one at most.
const DRAG_SHARE = 0.8;
const FLICK = 0.12;
const PAGE_SPRING = { damping: 24, stiffness: 200, mass: 1 };
const POP_SPRING = { damping: 13, stiffness: 260 };
const POP_IN = 0.35;
const POP_OUT = 0.25;
const ROW_IN = 260;
const ROW_OUT = 180;
const ROW_STAGGER = 40;
const ROW_SHIFT = 12;
const CARD_RESIZE = 300;
const THUMB_SLIDE = 220;
const SMOOTH = Easing.inOut(Easing.cubic);
const OUT = Easing.out(Easing.cubic);

function haptic(kind: 'page' | 'pop') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'page') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/** Rows come in from 12 points above with a stagger, and leave 12 points down. */
function rowIn(delay: number) {
  return (values: EntryAnimationsValues) => {
    'worklet';
    return {
      initialValues: { opacity: 0, transform: [{ translateY: -ROW_SHIFT }], originY: values.targetOriginY },
      animations: {
        opacity: withDelay(delay, withTiming(1, { duration: ROW_IN })),
        transform: [{ translateY: withDelay(delay, withTiming(0, { duration: ROW_IN, easing: OUT })) }],
      },
    };
  };
}

function rowOut(values: ExitAnimationsValues) {
  'worklet';
  return {
    initialValues: { opacity: 1, transform: [{ translateY: 0 }], originY: values.currentOriginY },
    animations: {
      opacity: withTiming(0, { duration: ROW_OUT }),
      transform: [{ translateY: withTiming(ROW_SHIFT, { duration: ROW_OUT, easing: OUT }) }],
    },
  };
}

function money(value: number, currency: string) {
  return `${currency}${Math.round(value).toLocaleString('en-US')}`;
}

/** The centre of one page: the month, its total, and the change from the month before. */
function MonthPage({ month, index, page, width, still, currency }: { month: Month; index: number; page: SharedValue<number>; width: number; still: boolean; currency: string }) {
  const style = useAnimatedStyle(() => {
    const away = index - page.get();
    // Reduced motion: pages cross-fade in place instead of sliding.
    if (still) return { opacity: clamp01(1 - Math.abs(away) * 2), transform: [{ translateX: 0 }] };
    return { opacity: clamp01(1.4 - Math.abs(away)), transform: [{ translateX: away * width }] };
  });
  const { change } = month;
  return (
    <Animated.View style={[styles.centre, style]} pointerEvents="none">
      <Text style={styles.month}>{month.label}</Text>
      <Text style={styles.amount}>{money(total(month), currency)}</Text>
      <View style={styles.change}>
        <Ionicons name={change.up ? 'arrow-up-circle' : 'arrow-down-circle'} size={17} color={FAINT} />
        <Text style={styles.changeLabel}>
          {money(change.amount, currency)} ({change.percent}%)
        </Text>
      </View>
    </Animated.View>
  );
}

function PageDot({ index, page }: { index: number; page: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const near = clamp01(1 - Math.abs(index - page.get()));
    return { opacity: 0.3 + 0.7 * near, transform: [{ scale: (DOT + (DOT_ACTIVE - DOT) * near) / DOT }] };
  });
  return <Animated.View style={[styles.dot, style]} />;
}

/**
 * A month of spending as a ring of round-capped segments, paged with a swipe. Between two months
 * every segment's start and length follow the finger, so categories shrink into their neighbours
 * or grow out of them; the total slides with the page and the list below re-flows. Tapping a
 * segment pops it out and shows its amount in the centre.
 */
export function SwipeMonthDonut({
  categories = CATEGORIES,
  months = MONTHS,
  currency = '$',
  kinds = ['Expense', 'Income'],
  autoPlay = false,
  onMonthChange,
  onCategory,
  onAdd,
}: Props) {
  const still = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  const size = Math.min(RING, window.width - MARGIN * 2) + POP * 2;
  const radius = size / 2 - POP - STROKE / 2 - 1;
  const inner = (radius - STROKE / 2) * 2 - 8;
  const table = useMemo(() => sharesOf(months), [months]);
  const last = months.length - 1;

  const [month, setMonth] = useState(0);
  const [focused, setFocused] = useState(-1);
  const [kind, setKind] = useState(0);

  const page = useSharedValue(0);
  const focus = useSharedValue(-1);
  const out = useSharedValue(0);
  const thumb = useSharedValue(0);
  const start = useSharedValue(0);
  const auto = useSharedValue(autoPlay);
  const origin = useSharedValue(-1);

  // The shares between the two pages the pager is between.
  const shares = useDerivedValue(() => {
    const p = Math.min(last, Math.max(0, page.get()));
    const from = Math.min(last, Math.floor(p));
    const to = Math.min(last, from + 1);
    const u = p - from;
    const a = table[from];
    const b = table[to];
    const mixed: number[] = [];
    for (let i = 0; i < a.length; i++) mixed.push(a[i] + (b[i] - a[i]) * u);
    return mixed;
  });

  const turned = (index: number) => {
    setMonth(index);
    onMonthChange?.(months[index], index);
  };
  useAnimatedReaction(
    () => Math.min(last, Math.max(0, Math.round(page.get()))),
    (next, prev) => {
      if (prev !== null && next !== prev) scheduleOnRN(turned, next);
    },
  );
  useAnimatedReaction(
    () => (out.get() > 0.01 ? focus.get() : -1),
    (next, prev) => {
      if (next !== prev) scheduleOnRN(setFocused, next);
    },
  );

  useFrameCallback((info) => {
    if (!auto.get()) return;
    if (origin.get() < 0) origin.set(info.timestamp);
    const t = (((info.timestamp - origin.get()) / 1000) % LOOP + LOOP) % LOOP;
    const p = still ? Math.round(pageAt(t)) : pageAt(t);
    const away = still
      ? t >= FOCUS_ON && t < FOCUS_OFF ? 1 : 0
      : t < FOCUS_ON
        ? 0
        : t < FOCUS_OFF
          ? springOut((t - FOCUS_ON) / POP_IN)
          : 1 - clamp01((t - FOCUS_OFF) / POP_OUT);
    if (page.get() !== p) page.set(p);
    if (focus.get() !== FOCUS_CATEGORY) focus.set(FOCUS_CATEGORY);
    if (out.get() !== away) out.set(away);
  });

  // A touch takes over at once; some seconds after the last one the pager glides home and plays again.
  const [touches, setTouches] = useState(0);
  const touched = () => setTouches((n) => n + 1);
  // A tap off the ring stops autoPlay too: the pager settles on the nearest page.
  const takeOver = () => {
    touched();
    scheduleOnUI(() => {
      'worklet';
      if (!auto.get()) return;
      auto.set(false);
      page.set(withTiming(Math.round(page.get()), { duration: still ? 0 : 300, easing: SMOOTH }));
      out.set(withTiming(0, { duration: still ? 0 : POP_OUT * 1000 }));
    });
  };
  useEffect(() => {
    if (!autoPlay || touches === 0) return;
    const settle = setTimeout(() => {
      const timing = { duration: still ? 0 : RETURN_FOR * 1000, easing: SMOOTH };
      page.set(withTiming(0, timing));
      out.set(withTiming(0, timing));
    }, USER_HOLD * 1000);
    const replay = setTimeout(() => {
      origin.set(-1);
      auto.set(true);
    }, (USER_HOLD + RETURN_FOR) * 1000);
    return () => {
      clearTimeout(settle);
      clearTimeout(replay);
    };
  }, [autoPlay, touches, still, page, out, origin, auto]);

  const centre = size / 2;
  const pick = (index: number) => onCategory?.(index < 0 ? null : categories[index]);
  const pan = Gesture.Pan()
    .activeOffsetX([-10, 10])
    .failOffsetY([-14, 14])
    .onBegin(() => {
      auto.set(false);
      cancelAnimation(page);
      start.set(Math.round(page.get()));
    })
    .onStart(() => {
      out.set(withTiming(0, { duration: 150 }));
    })
    .onUpdate((event) => {
      let p = start.get() - event.translationX / (size * DRAG_SHARE);
      // Past the first or last page the ring resists.
      if (p < 0) p = p / 3;
      if (p > last) p = last + (p - last) / 3;
      page.set(p);
    })
    .onEnd((event) => {
      const flung = page.get() - (event.velocityX / (size * DRAG_SHARE)) * FLICK;
      const target = Math.min(last, Math.max(0, Math.min(start.get() + 1, Math.max(start.get() - 1, Math.round(flung)))));
      page.set(still ? target : withSpring(target, PAGE_SPRING));
      if (target !== start.get()) scheduleOnRN(haptic, 'page');
    })
    .onFinalize(() => {
      scheduleOnRN(touched);
    });

  const tap = Gesture.Tap()
    .maxDistance(10)
    .onEnd((event) => {
      auto.set(false);
      scheduleOnRN(touched);
      const dx = event.x - centre;
      const dy = event.y - centre;
      const reach = Math.sqrt(dx * dx + dy * dy);
      let hit = -1;
      if (Math.abs(reach - radius) < STROKE) {
        // Clockwise from the top, in turns.
        const turn = (Math.atan2(dy, dx) / (Math.PI * 2) + 1.25) % 1;
        const list = shares.get();
        let at = 0;
        for (let i = 0; i < list.length; i++) {
          if (list[i] > 0 && turn >= at && turn < at + list[i]) hit = i;
          at += list[i];
        }
      }
      const showing = out.get() > 0.5 ? focus.get() : -1;
      if (hit < 0 || hit === showing) {
        out.set(withTiming(0, { duration: still ? 0 : POP_OUT * 1000 }));
        scheduleOnRN(pick, -1);
        return;
      }
      focus.set(hit);
      out.set(0);
      out.set(still ? 1 : withSpring(1, POP_SPRING));
      scheduleOnRN(haptic, 'pop');
      scheduleOnRN(pick, hit);
    });

  const pagesStyle = useAnimatedStyle(() => ({ opacity: 1 - clamp01(out.get()) }));
  const focusStyle = useAnimatedStyle(() => ({ opacity: clamp01(out.get()), transform: [{ scale: 0.94 + 0.06 * clamp01(out.get()) }] }));
  const thumbStyle = useAnimatedStyle(() => ({ transform: [{ translateX: thumb.get() * (SEGMENTED_W / 2 - 2) }] }));

  const current = months[month];
  const sum = total(current);
  const rows = categories
    .map((category, index) => ({ category, index, amount: current.amounts[index], count: current.counts[index] }))
    .filter((row) => row.amount > 0)
    .sort((a, b) => b.amount - a.amount);
  const shown = focused >= 0 ? categories[focused] : null;
  const shownAmount = focused >= 0 ? current.amounts[focused] : 0;

  return (
    <GestureHandlerRootView style={styles.screen}>
      <StatusBar style="dark" />
      <View style={[styles.bar, { marginTop: insets.top }]}>
        <Pressable hitSlop={8} accessibilityRole="button" accessibilityLabel="More" onPress={takeOver}>
          <Ionicons name="ellipsis-horizontal" size={22} color={MUTE} />
        </Pressable>
        <View style={styles.segmented} accessibilityRole="tablist">
          <Animated.View style={[styles.thumb, thumbStyle]} />
          {kinds.map((label, index) => (
            <Pressable
              key={label}
              style={styles.kind}
              accessibilityRole="tab"
              accessibilityState={{ selected: kind === index }}
              onPress={() => {
                takeOver();
                setKind(index);
                thumb.set(still ? index : withTiming(index, { duration: THUMB_SLIDE, easing: SMOOTH }));
              }}
            >
              <Text style={styles.kindLabel}>{label}</Text>
            </Pressable>
          ))}
        </View>
        <Pressable hitSlop={8} accessibilityRole="button" accessibilityLabel="History" onPress={takeOver}>
          <Ionicons name="time-outline" size={24} color={MUTE} />
        </Pressable>
      </View>

      <GestureDetector gesture={Gesture.Race(pan, tap)}>
        <View
          style={[styles.donut, { width: size, height: size }]}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel={`${current.label}, ${money(sum, currency)} spent`}
          accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
          onAccessibilityAction={(event) => {
            const next = Math.min(last, Math.max(0, month + (event.nativeEvent.actionName === 'increment' ? 1 : -1)));
            auto.set(false);
            touched();
            page.set(withTiming(next, { duration: still ? 0 : 400, easing: SMOOTH }));
          }}
        >
          <DonutRing size={size} stroke={STROKE} gap={GAP} pop={POP} colors={categories.map((c) => c.color)} shares={shares} focus={focus} out={out} />
          <View style={[styles.window, { width: inner, height: inner, borderRadius: inner / 2, left: (size - inner) / 2, top: (size - inner) / 2 }]} pointerEvents="none">
            <Animated.View style={[StyleSheet.absoluteFill, pagesStyle]}>
              {months.map((m, index) => (
                <MonthPage key={m.label} month={m} index={index} page={page} width={inner} still={still} currency={currency} />
              ))}
            </Animated.View>
            <Animated.View style={[styles.centre, focusStyle]}>
              {shown && (
                <>
                  <Text style={styles.month}>
                    {shown.icon} {shown.name}
                  </Text>
                  <Text style={styles.amount}>{money(shownAmount, currency)}</Text>
                  <Text style={styles.changeLabel}>{sum > 0 ? Math.round((shownAmount / sum) * 100) : 0}% of the month</Text>
                </>
              )}
            </Animated.View>
          </View>
        </View>
      </GestureDetector>

      <View style={styles.dots}>
        {months.map((m, index) => (
          <PageDot key={m.label} index={index} page={page} />
        ))}
      </View>

      <Animated.View style={styles.card} layout={still ? undefined : LinearTransition.duration(CARD_RESIZE)}>
        {rows.map((row, order) => (
          <Animated.View
            key={row.category.name}
            layout={still ? undefined : LinearTransition.duration(CARD_RESIZE)}
            entering={still ? undefined : rowIn(order * ROW_STAGGER)}
            exiting={still ? undefined : rowOut}
            style={styles.row}
          >
            <View style={[styles.swatch, { backgroundColor: row.category.color }]} />
            <Text style={styles.icon}>{row.category.icon}</Text>
            <View style={[styles.rowBody, order > 0 && styles.rowRule]}>
              <Text style={styles.rowLabel} numberOfLines={1}>
                {row.category.name}
              </Text>
              <View style={styles.count}>
                <Text style={styles.countLabel}>{row.count}</Text>
              </View>
              <Text style={styles.rowAmount}>{money(row.amount, currency)}</Text>
              <Ionicons name="chevron-forward" size={16} color={FAINT} />
            </View>
          </Animated.View>
        ))}
      </Animated.View>

      <Pressable
        style={[styles.plus, { bottom: Math.max(insets.bottom, 16) + 12 }]}
        accessibilityRole="button"
        accessibilityLabel="Add expense"
        onPress={() => {
          takeOver();
          onAdd?.();
        }}
      >
        <Ionicons name="add" size={30} color="#ffffff" />
      </Pressable>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: PAGE,
  },
  bar: {
    height: BAR,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: MARGIN,
  },
  segmented: {
    width: SEGMENTED_W,
    height: SEGMENTED_H,
    flexDirection: 'row',
    padding: 2,
    borderRadius: 9,
    borderCurve: 'continuous',
    backgroundColor: TRACK,
  },
  thumb: {
    position: 'absolute',
    left: 2,
    top: 2,
    width: SEGMENTED_W / 2 - 2,
    height: SEGMENTED_H - 4,
    borderRadius: 7,
    borderCurve: 'continuous',
    backgroundColor: '#ffffff',
    boxShadow: [{ offsetX: 0, offsetY: 1, blurRadius: 3, color: 'rgba(0,0,0,0.12)' }],
  },
  kind: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  kindLabel: {
    color: INK,
    fontSize: 13,
    lineHeight: 18,
    fontWeight: '500',
  },
  donut: {
    alignSelf: 'center',
    marginTop: 28,
  },
  window: {
    position: 'absolute',
    overflow: 'hidden',
  },
  centre: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
    alignItems: 'center',
    justifyContent: 'center',
  },
  month: {
    color: MUTE,
    fontSize: 15,
    lineHeight: 20,
  },
  amount: {
    marginTop: 4,
    color: INK,
    fontSize: 44,
    lineHeight: 52,
    fontWeight: '700',
    letterSpacing: -0.8,
    fontVariant: ['tabular-nums'],
  },
  change: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    marginTop: 4,
  },
  changeLabel: {
    color: MUTE,
    fontSize: 15,
    lineHeight: 20,
    fontVariant: ['tabular-nums'],
  },
  dots: {
    flexDirection: 'row',
    alignSelf: 'center',
    gap: 8,
    marginTop: 20,
  },
  dot: {
    width: DOT,
    height: DOT,
    borderRadius: DOT / 2,
    backgroundColor: INK,
  },
  card: {
    marginTop: 24,
    marginHorizontal: MARGIN,
    paddingHorizontal: 14,
    borderRadius: 14,
    borderCurve: 'continuous',
    backgroundColor: CARD,
    overflow: 'hidden',
  },
  row: {
    height: ROW,
    flexDirection: 'row',
    alignItems: 'center',
  },
  swatch: {
    width: 10,
    height: 10,
    borderRadius: 3,
    marginRight: 14,
  },
  icon: {
    width: 26,
    fontSize: 19,
    lineHeight: 24,
    marginRight: 10,
  },
  rowBody: {
    flex: 1,
    height: ROW,
    flexDirection: 'row',
    alignItems: 'center',
  },
  rowRule: {
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: RULE,
  },
  rowLabel: {
    flexShrink: 1,
    color: INK,
    fontSize: 16,
    lineHeight: 21,
  },
  count: {
    marginLeft: 8,
    minWidth: 20,
    height: 20,
    paddingHorizontal: 6,
    borderRadius: 6,
    backgroundColor: BADGE,
    alignItems: 'center',
    justifyContent: 'center',
  },
  countLabel: {
    color: MUTE,
    fontSize: 12,
    lineHeight: 16,
    fontVariant: ['tabular-nums'],
  },
  rowAmount: {
    marginLeft: 'auto',
    marginRight: 6,
    color: MUTE,
    fontSize: 16,
    lineHeight: 21,
    fontVariant: ['tabular-nums'],
  },
  plus: {
    position: 'absolute',
    alignSelf: 'center',
    width: PLUS,
    height: PLUS,
    borderRadius: PLUS / 2,
    backgroundColor: INK,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
