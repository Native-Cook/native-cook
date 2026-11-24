// The reusable piece: a date chip that grows into an inline month grid and folds back.
// The range is one Skia shape per month; the page around it only reads `pushStyle` height through `motion.open`.
import { Canvas, Path } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { memo, useCallback, useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useSharedValue,
  withSpring,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { formatRange, WEEKDAYS, type CalendarMonth } from './calendar';
import {
  CAP_SPRING,
  CELL_RISE,
  CELL_SPAN,
  CELL_SPREAD,
  clamp01,
  DRAG_DAY_MS,
  FOLD_SPRING,
  mix,
  OPEN_SPRING,
  PAGE_RUBBER,
  PAGE_THRESHOLD,
  PAGE_VELOCITY,
  REVEAL_IN,
  REVEAL_OUT,
  seg,
  springConfig,
  T,
} from './motion';
import { buildRange } from './rangePath';

export const INK = '#16262E';
export const MUTE = '#6E7F86';
export const CARD = '#FDFCF8';
export const TIDE = '#1C6B7A';
export const TIDE_SOFT = '#CFE4E6';
const FAINT = 'rgba(22,38,46,0.26)';
const FILL = '#EDF1EF';

// Layout, in points.
export const CHIP_HEIGHT = 68;
export const GRID_PAD = 12;
export const TITLE_ROW = 40;
export const WEEK_ROW = 22;
export const DAY_ROW = 40;
export const GRID_ROWS = 6;
export const GRID_BOTTOM = 12;
export const OPEN_HEIGHT = CHIP_HEIGHT + TITLE_ROW + WEEK_ROW + DAY_ROW * GRID_ROWS + GRID_BOTTOM;
export const GRID_TOP = TITLE_ROW + WEEK_ROW;
const BAND_INSET = 2;
const CAP = 36;
const LINE = 22;
const RADIUS_CLOSED = 20;
const RADIUS_OPEN = 24;

const OPEN_CONFIG = springConfig(OPEN_SPRING);
const FOLD_CONFIG = springConfig(FOLD_SPRING);
const CAP_CONFIG = springConfig(CAP_SPRING);
const PAGE_CONFIG = springConfig({ response: 0.38, ratio: 0.9 });

export type DateRange = { start: number; end: number };

export function useDateChipMotion(initialPage: number) {
  const open = useSharedValue(0);
  const reveal = useSharedValue(0);
  const roll = useSharedValue(0);
  const hasValue = useSharedValue(0);
  const start = useSharedValue(-1);
  const head = useSharedValue(-1);
  const complete = useSharedValue(0);
  const capStart = useSharedValue(0);
  const capEnd = useSharedValue(0);
  const page = useSharedValue(initialPage);
  const press = useSharedValue(0);
  const adoptTick = useSharedValue(0);
  const resetTick = useSharedValue(0);
  return { open, reveal, roll, hasValue, start, head, complete, capStart, capEnd, page, press, adoptTick, resetTick };
}
export type DateChipMotion = ReturnType<typeof useDateChipMotion>;

function light() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

function isBlocked(day: number, blocked: number[]) {
  'worklet';
  for (let i = 0; i < blocked.length; i += 1) if (blocked[i] === day) return true;
  return false;
}

function blockedBetween(a: number, b: number, blocked: number[]) {
  'worklet';
  const lo = Math.min(a, b);
  const hi = Math.max(a, b);
  for (let i = 0; i < blocked.length; i += 1) if (blocked[i] >= lo && blocked[i] <= hi) return true;
  return false;
}

/** The furthest day from `anchor` towards `day` that a stay can reach without crossing a blocked night. */
function reach(anchor: number, day: number, blocked: number[]) {
  'worklet';
  const step = day > anchor ? 1 : -1;
  let out = anchor;
  while (out !== day) {
    const next = out + step;
    if (isBlocked(next, blocked)) break;
    out = next;
  }
  return out;
}

/** A cap's place in the grid; a head between two weeks stays on the last column, then jumps to the next row. */
function capPosition(day: number, first: number, offset: number, cell: number) {
  'worklet';
  const index = day - first + offset;
  let row = Math.floor(index / 7);
  let col = index - row * 7;
  if (col > 6.5) {
    row += 1;
    col = 0;
  } else if (col > 6) {
    col = 6;
  }
  return { x: col * cell + (cell - CAP) / 2, y: row * DAY_ROW + (DAY_ROW - CAP) / 2 };
}

const DayCell = memo(function DayCell({
  day,
  col,
  row,
  cell,
  muted,
  blocked,
  reveal,
}: {
  day: number;
  col: number;
  row: number;
  cell: number;
  muted: boolean;
  blocked: boolean;
  reveal: SharedValue<number>;
}) {
  const delay = ((col + row) / 11) * CELL_SPREAD;
  const style = useAnimatedStyle(() => {
    const u = seg(reveal.get(), delay, CELL_SPAN);
    return { opacity: u, transform: [{ translateY: (1 - u) * CELL_RISE }] };
  });
  return (
    <Animated.View style={[styles.cell, { left: col * cell, top: row * DAY_ROW, width: cell }, style]}>
      <Text style={[styles.day, muted && styles.dayMuted, blocked && styles.dayBlocked]}>{day}</Text>
    </Animated.View>
  );
});

/** Its own tiny component: the number changes as the head passes each day, without rendering the month. */
function CapNumber({ value, first }: { value: SharedValue<number>; first: number }) {
  const [day, setDay] = useState(0);
  useAnimatedReaction(
    () => Math.round(value.get()) - first + 1,
    (next, previous) => {
      if (next !== previous) scheduleOnRN(setDay, next);
    },
  );
  return <Text style={styles.capText}>{day > 0 ? day : ''}</Text>;
}

const MonthPage = memo(function MonthPage({
  month,
  index,
  width,
  cell,
  motion: m,
  today,
  blocked,
}: {
  month: CalendarMonth;
  index: number;
  width: number;
  cell: number;
  motion: DateChipMotion;
  today: number;
  blocked: number[];
}) {
  const { first, offset, days } = month;
  const last = first + days - 1;

  const path = useDerivedValue(() => buildRange(m.start.get(), m.head.get(), first, offset, days, cell, DAY_ROW, BAND_INSET));

  const startCap = useAnimatedStyle(() => {
    const s = m.start.get();
    const shown = s >= first && s <= last;
    if (!shown) return { opacity: 0, transform: [{ translateX: 0 }, { translateY: 0 }, { scale: 0.7 }] };
    const p = capPosition(s, first, offset, cell);
    const u = m.capStart.get();
    return { opacity: clamp01(u * 2), transform: [{ translateX: p.x }, { translateY: p.y }, { scale: 0.7 + 0.3 * u }] };
  });
  const endCap = useAnimatedStyle(() => {
    const s = m.start.get();
    const h = m.head.get();
    const shown = s >= 0 && h >= first && h < last + 1 && Math.abs(h - s) > 0.5;
    if (!shown) return { opacity: 0, transform: [{ translateX: 0 }, { translateY: 0 }, { scale: 0.7 }] };
    const p = capPosition(h, first, offset, cell);
    const u = m.capEnd.get();
    return { opacity: clamp01(u * 2), transform: [{ translateX: p.x }, { translateY: p.y }, { scale: 0.7 + 0.3 * u }] };
  });

  const cells = useMemo(() => {
    const out: { day: number; col: number; row: number; muted: boolean; blocked: boolean }[] = [];
    for (let d = 0; d < days; d += 1) {
      const place = d + offset;
      const value = first + d;
      out.push({
        day: d + 1,
        col: place % 7,
        row: Math.floor(place / 7),
        muted: value < today,
        blocked: blocked.includes(value),
      });
    }
    return out;
  }, [blocked, days, first, offset, today]);

  return (
    <View style={[styles.page, { left: index * width, width }]}>
      <View style={styles.titleRow}>
        <Text style={styles.month}>{month.title}</Text>
      </View>
      <View style={styles.weekRow}>
        {WEEKDAYS.map((label, i) => (
          <Text key={`${label}-${i}`} style={[styles.week, { width: cell }]}>{label}</Text>
        ))}
      </View>
      <View style={{ height: DAY_ROW * GRID_ROWS }}>
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          <Path path={path} color={TIDE_SOFT} />
        </Canvas>
        {cells.map(item => (
          <DayCell key={item.day} {...item} cell={cell} reveal={m.reveal} />
        ))}
        <Animated.View style={[styles.cap, startCap]} pointerEvents="none">
          <CapNumber value={m.start} first={first} />
        </Animated.View>
        <Animated.View style={[styles.cap, endCap]} pointerEvents="none">
          <CapNumber value={m.head} first={first} />
        </Animated.View>
      </View>
    </View>
  );
});

function Chevron({ flip = false }: { flip?: boolean }) {
  return <View style={[styles.chevron, flip && styles.chevronFlip]} />;
}

export type DateChipCalendarProps = {
  motion: DateChipMotion;
  months: CalendarMonth[];
  initialPage: number;
  /** Serial of the first day that can be booked. */
  today: number;
  /** Serials of the nights that are taken. */
  blocked: number[];
  /** Width of the card in points. */
  width: number;
  /** The label autoPlay rolls to. */
  scriptLabel: string;
  placeholder?: string;
  caption?: string;
  reducedMotion?: boolean;
  onChange?: (range: DateRange | null) => void;
  onActivity?: () => void;
};

export function DateChipCalendar({
  motion: m,
  months,
  initialPage,
  today,
  blocked,
  width,
  scriptLabel,
  placeholder = 'Add dates',
  caption = 'Dates',
  reducedMotion = false,
  onChange,
  onActivity,
}: DateChipCalendarProps) {
  const [page, setPage] = useState(initialPage);
  const [isOpen, setIsOpen] = useState(false);
  const [label, setLabel] = useState(scriptLabel);
  const [hasRange, setHasRange] = useState(false);

  const pageWidth = width - GRID_PAD * 2;
  const cell = pageWidth / 7;

  // Gesture bookkeeping, on the UI thread.
  const dragKind = useSharedValue(0); // 0 none, 1 range, 2 page
  const lastDay = useSharedValue(-1);
  const pageBase = useSharedValue(initialPage);

  const go = useCallback((value: SharedValue<number>, to: number, config: { stiffness: number; damping: number; mass: number }) => {
    value.set(reducedMotion ? withTiming(to, { duration: 180 }) : withSpring(to, config));
  }, [reducedMotion]);

  const openCalendar = useCallback(() => {
    onActivity?.();
    light();
    setIsOpen(true);
    go(m.open, 1, OPEN_CONFIG);
    m.reveal.set(withTiming(1, { duration: reducedMotion ? 120 : REVEAL_IN * 1000 }));
  }, [go, m.open, m.reveal, onActivity, reducedMotion]);

  const fold = useCallback(() => {
    onActivity?.();
    light();
    setIsOpen(false);
    go(m.open, 0, FOLD_CONFIG);
    m.reveal.set(withTiming(0, { duration: reducedMotion ? 120 : REVEAL_OUT * 1000 }));
  }, [go, m.open, m.reveal, onActivity, reducedMotion]);

  const commit = useCallback((start: number, end: number) => {
    onActivity?.();
    const range = { start: Math.min(start, end), end: Math.max(start, end) };
    setLabel(formatRange(range.start, range.end));
    setHasRange(true);
    onChange?.(range);
    m.hasValue.set(withTiming(1, { duration: 160 }));
    m.roll.set(withTiming(1, { duration: reducedMotion ? 120 : T.rollSpan * 1000 }));
  }, [m.hasValue, m.roll, onActivity, onChange, reducedMotion]);

  const clear = useCallback(() => {
    onActivity?.();
    light();
    setHasRange(false);
    onChange?.(null);
    m.start.set(-1);
    m.head.set(-1);
    m.complete.set(0);
    m.capStart.set(0);
    m.capEnd.set(0);
    m.hasValue.set(withTiming(0, { duration: 160 }));
    m.roll.set(withTiming(0, { duration: reducedMotion ? 120 : T.rollSpan * 1000 }));
  }, [m, onActivity, onChange, reducedMotion]);

  const resetAll = useCallback(() => {
    setIsOpen(false);
    setHasRange(false);
    setPage(initialPage);
    setLabel(scriptLabel);
  }, [initialPage, scriptLabel]);

  const adopt = useCallback((opened: boolean, start: number, end: number) => {
    setIsOpen(opened);
    if (start >= 0 && end !== start) {
      setHasRange(true);
      setLabel(formatRange(Math.min(start, end), Math.max(start, end)));
      onChange?.({ start: Math.min(start, end), end: Math.max(start, end) });
    }
  }, [onChange]);

  useAnimatedReaction(
    () => m.resetTick.get(),
    (tick, previous) => {
      if (previous !== null && tick !== previous) scheduleOnRN(resetAll);
    },
  );
  useAnimatedReaction(
    () => m.adoptTick.get(),
    (tick, previous) => {
      if (previous !== null && tick !== previous) {
        scheduleOnRN(adopt, m.open.get() > 0.5, m.start.get(), Math.round(m.head.get()));
      }
    },
  );

  const changePage = useCallback((next: number) => {
    onActivity?.();
    const target = Math.max(0, Math.min(months.length - 1, next));
    setPage(target);
    m.page.set(reducedMotion ? target : withSpring(target, PAGE_CONFIG));
  }, [m.page, months.length, onActivity, reducedMotion]);

  // Hit testing runs on the UI thread against plain copies of the month table.
  const table = useMemo(() => months.map(({ first, offset, days }) => ({ first, offset, days })), [months]);

  const dayAt = (x: number, y: number) => {
    'worklet';
    const current = table[Math.round(m.page.get())];
    if (!current) return -1;
    const col = Math.floor((x - GRID_PAD) / cell);
    const row = Math.floor((y - GRID_TOP) / DAY_ROW); // y is relative to the grid, under the chip
    if (col < 0 || col > 6 || row < 0 || row >= GRID_ROWS) return -1;
    const day = row * 7 + col - current.offset;
    if (day < 0 || day >= current.days) return -1;
    const value = current.first + day;
    if (value < today || isBlocked(value, blocked)) return -1;
    return value;
  };

  // A new first day replaces a finished stay: the chip and the total go back to empty until it has an end.
  const dropRange = useCallback(() => {
    setHasRange(false);
    onChange?.(null);
    m.hasValue.set(withTiming(0, { duration: 160 }));
    m.roll.set(withTiming(0, { duration: reducedMotion ? 120 : T.rollSpan * 1000 }));
  }, [m.hasValue, m.roll, onChange, reducedMotion]);

  const beginAt = (day: number) => {
    'worklet';
    if (m.complete.get() === 1) scheduleOnRN(dropRange);
    m.start.set(day);
    m.head.set(day);
    m.complete.set(0);
    m.capEnd.set(0);
    m.capStart.set(0);
    m.capStart.set(withSpring(1, CAP_CONFIG));
    lastDay.set(day);
    scheduleOnRN(light);
  };

  const tap = Gesture.Tap()
    .enabled(isOpen)
    .maxDistance(8)
    .onEnd(event => {
      const day = dayAt(event.x, event.y);
      if (day < 0) return;
      const s = m.start.get();
      if (s < 0 || m.complete.get() === 1 || day === s || blockedBetween(s, day, blocked)) {
        beginAt(day);
        return;
      }
      m.head.set(withTiming(day, { duration: DRAG_DAY_MS * 2 }));
      m.capEnd.set(withSpring(1, CAP_CONFIG));
      m.complete.set(1);
      scheduleOnRN(light);
      scheduleOnRN(commit, s, day);
    });

  const pan = Gesture.Pan()
    .enabled(isOpen)
    .minDistance(8)
    .onStart(event => {
      const startX = event.x - event.translationX;
      const startY = event.y - event.translationY;
      const day = dayAt(startX, startY);
      const s = m.start.get();
      const h = Math.round(m.head.get());
      const onCap = day >= 0 && s >= 0 && (day === s || day === h);
      if (!onCap && Math.abs(event.translationX) > Math.abs(event.translationY) * 1.2) {
        dragKind.set(2);
        pageBase.set(Math.round(m.page.get()));
        return;
      }
      if (day < 0) {
        dragKind.set(0);
        return;
      }
      dragKind.set(1);
      if (onCap && m.complete.get() === 1) {
        // Grab either end: the other one becomes the anchor.
        if (day === s) {
          m.start.set(h);
          m.head.set(s);
        }
        lastDay.set(day);
        m.complete.set(0);
      } else {
        beginAt(day);
      }
    })
    .onUpdate(event => {
      if (dragKind.get() === 2) {
        const base = pageBase.get();
        let next = base - event.translationX / pageWidth;
        const max = table.length - 1;
        if (next < 0) next *= PAGE_RUBBER;
        else if (next > max) next = max + (next - max) * PAGE_RUBBER;
        m.page.set(next);
        return;
      }
      if (dragKind.get() !== 1) return;
      const raw = dayAt(event.x, event.y);
      if (raw < 0) return;
      const day = reach(m.start.get(), raw, blocked);
      if (day === lastDay.get()) return;
      lastDay.set(day);
      m.head.set(withTiming(day, { duration: DRAG_DAY_MS }));
      m.capEnd.set(withSpring(day === m.start.get() ? 0 : 1, CAP_CONFIG));
      scheduleOnRN(light);
    })
    .onEnd(event => {
      if (dragKind.get() === 2) {
        const base = pageBase.get();
        const moved = m.page.get() - base;
        let next = base;
        if (moved > PAGE_THRESHOLD || event.velocityX < -PAGE_VELOCITY) next = base + 1;
        else if (moved < -PAGE_THRESHOLD || event.velocityX > PAGE_VELOCITY) next = base - 1;
        next = Math.max(0, Math.min(table.length - 1, next));
        m.page.set(withSpring(next, { ...PAGE_CONFIG, velocity: -event.velocityX / pageWidth }));
        if (next !== base) scheduleOnRN(setPage, next);
      } else if (dragKind.get() === 1) {
        const s = m.start.get();
        const e = lastDay.get();
        if (e !== s) {
          m.complete.set(1);
          scheduleOnRN(commit, s, e);
        }
      }
      dragKind.set(0);
    });

  const gesture = Gesture.Race(pan, tap);

  const cardStyle = useAnimatedStyle(() => {
    const o = m.open.get();
    return {
      height: mix(o, CHIP_HEIGHT, OPEN_HEIGHT),
      borderRadius: mix(clamp01(o), RADIUS_CLOSED, RADIUS_OPEN),
      transform: [{ scale: 1 - 0.015 * m.press.get() }],
    };
  });
  const gridStyle = useAnimatedStyle(() => ({ opacity: clamp01(m.reveal.get() * 5) }));
  const trackStyle = useAnimatedStyle(() => ({ transform: [{ translateX: -m.page.get() * pageWidth }] }));
  const rollStyle = useAnimatedStyle(() => ({ transform: [{ translateY: -clamp01(m.roll.get()) * LINE }] }));
  const chevronStyle = useAnimatedStyle(() => ({
    opacity: (1 - clamp01(m.open.get() * 2.5)) * (1 - m.hasValue.get()),
  }));
  const clearStyle = useAnimatedStyle(() => ({
    opacity: (1 - clamp01(m.open.get() * 2.5)) * m.hasValue.get(),
  }));
  const doneStyle = useAnimatedStyle(() => ({
    opacity: clamp01((m.open.get() - 0.4) * 2.5),
    transform: [{ scale: 0.92 + 0.08 * clamp01(m.open.get()) }],
  }));

  const pages = [page - 1, page, page + 1].filter(index => index >= 0 && index < months.length);

  return (
    <Animated.View style={[styles.card, { width }, cardStyle]}>
      <Pressable
        style={styles.chip}
        onPress={isOpen ? fold : openCalendar}
        accessibilityRole="button"
        accessibilityLabel={hasRange ? `Dates, ${label}` : `Dates, ${placeholder}`}
        accessibilityState={{ expanded: isOpen }}
      >
        <View style={styles.calendarIcon}>
          <View style={styles.calendarTop} />
          <View style={styles.calendarDot} />
        </View>
        <View style={styles.chipText}>
          <Text style={styles.caption}>{caption}</Text>
          <View style={styles.rollClip}>
            <Animated.View style={rollStyle}>
              <View style={styles.rollLine}><Text style={styles.rollMuted} numberOfLines={1}>{placeholder}</Text></View>
              <View style={styles.rollLine}><Text style={styles.rollValue} numberOfLines={1}>{label}</Text></View>
            </Animated.View>
          </View>
        </View>
      </Pressable>

      <View style={styles.trailing} pointerEvents="box-none">
        <Animated.View style={[styles.trailingItem, chevronStyle]} pointerEvents="none">
          <Chevron />
        </Animated.View>
        <Animated.View style={[styles.trailingItem, clearStyle]} pointerEvents={hasRange && !isOpen ? 'auto' : 'none'}>
          <Pressable onPress={clear} hitSlop={10} accessibilityRole="button" accessibilityLabel="Clear dates" style={styles.clear}>
            <View style={[styles.clearBar, styles.clearA]} />
            <View style={[styles.clearBar, styles.clearB]} />
          </Pressable>
        </Animated.View>
        <Animated.View style={[styles.trailingItem, doneStyle]} pointerEvents={isOpen ? 'auto' : 'none'}>
          <Pressable onPress={fold} hitSlop={8} accessibilityRole="button" accessibilityLabel="Done" style={styles.done}>
            <Text style={styles.doneText}>Done</Text>
          </Pressable>
        </Animated.View>
      </View>

      <GestureDetector gesture={gesture}>
        <Animated.View style={[styles.grid, { height: OPEN_HEIGHT - CHIP_HEIGHT }, gridStyle]} pointerEvents={isOpen ? 'auto' : 'none'}>
          <View style={[styles.pager, { width: pageWidth }]}>
            <Animated.View style={[styles.track, trackStyle]}>
              {pages.map(index => (
                <MonthPage
                  key={months[index].key}
                  month={months[index]}
                  index={index}
                  width={pageWidth}
                  cell={cell}
                  motion={m}
                  today={today}
                  blocked={blocked}
                />
              ))}
            </Animated.View>
          </View>
          <View style={styles.arrows}>
            <Pressable
              onPress={() => changePage(page - 1)}
              disabled={page === 0}
              hitSlop={6}
              style={[styles.arrow, page === 0 && styles.arrowOff]}
              accessibilityRole="button"
              accessibilityLabel="Previous month"
            >
              <View style={styles.arrowLeft} />
            </Pressable>
            <Pressable
              onPress={() => changePage(page + 1)}
              disabled={page === months.length - 1}
              hitSlop={6}
              style={[styles.arrow, page === months.length - 1 && styles.arrowOff]}
              accessibilityRole="button"
              accessibilityLabel="Next month"
            >
              <View style={styles.arrowRight} />
            </Pressable>
          </View>
        </Animated.View>
      </GestureDetector>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  card: {
    overflow: 'hidden',
    backgroundColor: CARD,
    borderCurve: 'continuous',
    boxShadow: '0 1px 2px rgba(16,40,48,0.10), 0 10px 26px rgba(16,40,48,0.10), inset 0 1px 0 rgba(255,255,255,0.9)',
  },
  chip: { height: CHIP_HEIGHT, flexDirection: 'row', alignItems: 'center', paddingLeft: 16, paddingRight: 88 },
  calendarIcon: {
    width: 36,
    height: 36,
    borderRadius: 11,
    borderCurve: 'continuous',
    backgroundColor: TIDE_SOFT,
    alignItems: 'center',
    overflow: 'hidden',
  },
  calendarTop: { width: 36, height: 10, backgroundColor: TIDE },
  calendarDot: { width: 8, height: 8, borderRadius: 2.5, marginTop: 6, backgroundColor: TIDE },
  chipText: { flex: 1, marginLeft: 12 },
  caption: { fontSize: 13, color: MUTE, fontWeight: '500' },
  rollClip: { height: LINE, overflow: 'hidden' },
  rollLine: { height: LINE, justifyContent: 'center' },
  rollMuted: { fontSize: 17, fontWeight: '600', color: MUTE },
  rollValue: { fontSize: 17, fontWeight: '600', color: INK, fontVariant: ['tabular-nums'] },
  trailing: { position: 'absolute', top: 0, right: 12, width: 72, height: CHIP_HEIGHT },
  trailingItem: { ...StyleSheet.absoluteFill, alignItems: 'flex-end', justifyContent: 'center' },
  chevron: {
    width: 9,
    height: 9,
    marginRight: 8,
    marginTop: -4,
    borderRightWidth: 2,
    borderBottomWidth: 2,
    borderColor: MUTE,
    transform: [{ rotate: '45deg' }],
  },
  chevronFlip: { transform: [{ rotate: '225deg' }] },
  clear: { width: 30, height: 30, borderRadius: 15, backgroundColor: FILL, alignItems: 'center', justifyContent: 'center' },
  clearBar: { position: 'absolute', width: 12, height: 1.8, borderRadius: 1, backgroundColor: INK },
  clearA: { transform: [{ rotate: '45deg' }] },
  clearB: { transform: [{ rotate: '-45deg' }] },
  done: { height: 32, paddingHorizontal: 14, borderRadius: 16, backgroundColor: TIDE, alignItems: 'center', justifyContent: 'center' },
  doneText: { fontSize: 15, fontWeight: '600', color: CARD },
  grid: { position: 'absolute', left: 0, right: 0, top: CHIP_HEIGHT },
  pager: { position: 'absolute', left: GRID_PAD, top: 0, bottom: 0, overflow: 'hidden' },
  track: { flex: 1 },
  page: { position: 'absolute', top: 0, bottom: 0 },
  titleRow: { height: TITLE_ROW, justifyContent: 'center', paddingLeft: 4 },
  month: { fontSize: 17, fontWeight: '700', color: INK },
  weekRow: { height: WEEK_ROW, flexDirection: 'row', alignItems: 'center' },
  week: { textAlign: 'center', fontSize: 12, fontWeight: '600', color: MUTE },
  cell: { position: 'absolute', height: DAY_ROW, alignItems: 'center', justifyContent: 'center' },
  day: { fontSize: 16, fontWeight: '500', color: INK, fontVariant: ['tabular-nums'] },
  dayMuted: { color: FAINT },
  dayBlocked: { color: FAINT, textDecorationLine: 'line-through' },
  cap: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: CAP,
    height: CAP,
    borderRadius: CAP / 2,
    backgroundColor: TIDE,
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 1px 2px rgba(16,60,70,0.30), 0 4px 10px rgba(16,60,70,0.22)',
  },
  capText: { fontSize: 16, fontWeight: '700', color: CARD, fontVariant: ['tabular-nums'] },
  arrows: { position: 'absolute', top: 4, right: GRID_PAD, height: 32, flexDirection: 'row', gap: 4 },
  arrow: { width: 32, height: 32, borderRadius: 16, backgroundColor: FILL, alignItems: 'center', justifyContent: 'center' },
  arrowOff: { opacity: 0.4 },
  arrowLeft: { width: 8, height: 8, marginLeft: 3, borderLeftWidth: 2, borderBottomWidth: 2, borderColor: INK, transform: [{ rotate: '45deg' }] },
  arrowRight: { width: 8, height: 8, marginRight: 3, borderRightWidth: 2, borderTopWidth: 2, borderColor: INK, transform: [{ rotate: '45deg' }] },
});
