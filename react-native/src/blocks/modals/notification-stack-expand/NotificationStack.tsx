// The reusable piece: a group of notifications that sits as a collapsed stack and fans out into a list.
// Tap the stack to open it, pull it down to peek, swipe a card left to clear it, Show less to restack.
// All motion lives in StackMotion (shared values), so a script (autoPlay) or a finger can drive the same cards.
import * as Haptics from 'expo-haptics';
import { memo, useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  useAnimatedReaction,
  useAnimatedStyle,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';
import Svg, { Path } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { SwapText } from '@/shared/SwapText';

import type { NoticeGroup } from './data';
import {
  CARD_H,
  CLEAR_W,
  clamp01,
  COLLAPSE_SPRING,
  collapsedY,
  DEPTH_SCALE,
  EXPAND_SPRING,
  expandedY,
  FLING_FRACTION,
  FLING_VELOCITY,
  GAP_SPRING,
  groupHeight,
  HEADER_H,
  MAX_CARDS,
  MAX_DEPTH,
  mix,
  OPEN_FRACTION,
  PEEK_MAX,
  PEEK_OPEN_DISTANCE,
  PEEK_OPEN_VELOCITY,
  PEEK_RANGE,
  PEEK_SPRING,
  peekFromDrag,
  RADIUS,
  rubberRight,
  STAGGER,
  SWIPE_SPRING,
  ARRIVE_DROP,
  ARRIVE_SCALE,
  springConfig,
} from './motion';
import { ACCENT, cardSurface, DANGER, INK, NoticeFace } from './NoticeCard';

export type CardMotion = {
  /** Fan-out: 0 stacked, 1 in the list (springs overshoot a little past both). */
  e: SharedValue<number>;
  /** Place in the group: 0 front / top. Fractional while the cards close a gap. */
  slot: SharedValue<number>;
  /** Swipe offset, pt. */
  x: SharedValue<number>;
  alpha: SharedValue<number>;
  /** Drops onto the stack from this many pt above (a returning card). */
  drop: SharedValue<number>;
};

export type StackMotion = {
  cards: CardMotion[];
  /** Drag-down peek, added to every card's fan-out. */
  peek: SharedValue<number>;
  /** Cards still in the group, fractional while one leaves (drives the height below). */
  count: SharedValue<number>;
  /** Whole cards in the group, for the header's "4 new". */
  countN: SharedValue<number>;
  /** 1 once a finger opened the list (manual), 0 stacked. */
  open: SharedValue<number>;
  /** 1 per cleared card. */
  gone: SharedValue<number[]>;
  /** The card resting open on its Clear button, or -1. */
  swiped: SharedValue<number>;
  /** 1 while a script poses the cards; the first touch hands them to the finger. */
  driven: SharedValue<number>;
  /** Fingers on the stack right now, and a counter of touches (the driver's idle timer). */
  held: SharedValue<number>;
  touches: SharedValue<number>;
  /** Reduce Motion: short fades instead of springs. */
  calm: SharedValue<number>;
};

function useCardMotion(index: number): CardMotion {
  const e = useSharedValue(0);
  const slot = useSharedValue(index);
  const x = useSharedValue(0);
  const alpha = useSharedValue(1);
  const drop = useSharedValue(0);
  return useMemo(() => ({ e, slot, x, alpha, drop }), [alpha, drop, e, slot, x]);
}

/** Motion for a group of up to MAX_CARDS (4) cards, resting stacked. */
export function useStackMotion(count: number): StackMotion {
  const c0 = useCardMotion(0);
  const c1 = useCardMotion(1);
  const c2 = useCardMotion(2);
  const c3 = useCardMotion(3);
  const n = Math.min(count, MAX_CARDS);
  const peek = useSharedValue(0);
  const countF = useSharedValue(n);
  const countN = useSharedValue(n);
  const open = useSharedValue(0);
  const gone = useSharedValue<number[]>([0, 1, 2, 3].map(i => (i < n ? 0 : 1)));
  const swiped = useSharedValue(-1);
  const driven = useSharedValue(0);
  const held = useSharedValue(0);
  const touches = useSharedValue(0);
  const calm = useSharedValue(0);
  return useMemo(() => ({
    cards: [c0, c1, c2, c3],
    peek,
    count: countF,
    countN,
    open,
    gone,
    swiped,
    driven,
    held,
    touches,
    calm,
  }), [c0, c1, c2, c3, peek, countF, countN, open, gone, swiped, driven, held, touches, calm]);
}

function lightTap() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/** A spring, or under Reduce Motion a 200 ms fade-like timing. */
function move(value: SharedValue<number>, to: number, s: { response: number; ratio: number }, calm: number, velocity: number) {
  'worklet';
  if (calm === 1) value.set(withTiming(to, { duration: 200 }));
  else value.set(withSpring(to, { ...springConfig(s), velocity }));
}

/** Re-ranks the cards still in the group and springs each to its place. */
function settleSlots(m: StackMotion) {
  'worklet';
  const gone = m.gone.get();
  let rank = 0;
  for (let i = 0; i < m.cards.length; i += 1) {
    if (gone[i] === 1) continue;
    move(m.cards[i].slot, rank, GAP_SPRING, m.calm.get(), 0);
    rank += 1;
  }
  move(m.count, rank, GAP_SPRING, m.calm.get(), 0);
  m.countN.set(rank);
}

/** Fans the cards out (top first) or restacks them (bottom first), on staggered springs. */
export function setOpen(m: StackMotion, open: number, velocity: number) {
  'worklet';
  m.open.set(open);
  const n = m.cards.length;
  const calm = m.calm.get();
  for (let i = 0; i < n; i += 1) {
    const c = m.cards[i];
    if (m.swiped.get() === i || c.x.get() > 0) move(c.x, 0, SWIPE_SPRING, calm, 0);
    const order = open === 1 ? i : n - 1 - i;
    const s = open === 1 ? EXPAND_SPRING : COLLAPSE_SPRING;
    const spring = calm === 1 ? withTiming(open, { duration: 200 }) : withSpring(open, { ...springConfig(s), velocity });
    c.e.set(calm === 1 ? spring : withDelay(order * STAGGER * 1000, spring));
  }
  m.swiped.set(-1);
}

/** Throws card i off to the left and closes the gap. */
export function clearCard(m: StackMotion, i: number, velocity: number) {
  'worklet';
  const c = m.cards[i];
  const calm = m.calm.get();
  const off = -560;
  if (calm === 1) {
    c.alpha.set(withTiming(0, { duration: 200 }));
    // Out of the way of touches once it has faded.
    c.x.set(withDelay(220, withTiming(off, { duration: 0 })));
  } else {
    const speed = Math.max(Math.abs(velocity), 1400);
    c.x.set(withTiming(off, { duration: Math.min(260, Math.max(140, ((c.x.get() - off) / speed) * 1000)) }));
    c.alpha.set(withDelay(140, withTiming(0, { duration: 140 })));
  }
  const next = m.gone.get().slice();
  next[i] = 1;
  m.gone.set(next);
  if (m.swiped.get() === i) m.swiped.set(-1);
  settleSlots(m);
  let left = 0;
  for (let k = 0; k < next.length; k += 1) if (next[k] === 0) left += 1;
  if (left === 0) m.open.set(0);
  scheduleOnRN(lightTap);
}

/** The header's clear mark: every card leaves, top first. */
export function clearAll(m: StackMotion) {
  'worklet';
  const next = m.gone.get().slice();
  let order = 0;
  for (let i = 0; i < m.cards.length; i += 1) {
    if (next[i] === 1) continue;
    const c = m.cards[i];
    c.alpha.set(withDelay(order * STAGGER * 1000, withTiming(0, { duration: 180 })));
    c.drop.set(withDelay(order * STAGGER * 1000, withTiming(-12, { duration: 180 })));
    c.x.set(withDelay(order * STAGGER * 1000 + 200, withTiming(-560, { duration: 0 })));
    next[i] = 1;
    order += 1;
  }
  m.gone.set(next);
  m.open.set(0);
  m.swiped.set(-1);
  move(m.count, 0, GAP_SPRING, m.calm.get(), 0);
  m.countN.set(0);
  scheduleOnRN(lightTap);
}

/** A script let go: whatever pose it left becomes the finger's starting point. */
export function adoptPose(m: StackMotion) {
  'worklet';
  if (m.driven.get() === 0) return;
  m.driven.set(0);
  const open = m.cards[0].e.get() > 0.5 ? 1 : 0;
  const prev = m.gone.get();
  const gone = prev.slice();
  for (let i = 0; i < m.cards.length; i += 1) {
    const c = m.cards[i];
    if (prev[i] === 0 && (c.alpha.get() < 0.5 || c.x.get() < -260)) gone[i] = 1;
  }
  m.gone.set(gone);
  m.open.set(open);
  m.swiped.set(-1);
  const calm = m.calm.get();
  move(m.peek, 0, PEEK_SPRING, calm, 0);
  for (let i = 0; i < m.cards.length; i += 1) {
    const c = m.cards[i];
    move(c.e, open, EXPAND_SPRING, calm, 0);
    move(c.drop, 0, SWIPE_SPRING, calm, 0);
    if (gone[i] === 1) {
      c.alpha.set(withTiming(0, { duration: 120 }));
    } else {
      move(c.x, 0, SWIPE_SPRING, calm, 0);
      c.alpha.set(withTiming(1, { duration: 120 }));
    }
  }
  settleSlots(m);
}

/** Back to the resting stack with every card in it: cleared cards drop back on in order. */
export function restoreStack(m: StackMotion, total: number) {
  'worklet';
  const gone = m.gone.get();
  const calm = m.calm.get();
  setOpen(m, 0, 0);
  move(m.peek, 0, PEEK_SPRING, calm, 0);
  const next = gone.slice();
  for (let i = 0; i < m.cards.length; i += 1) {
    const c = m.cards[i];
    if (i >= total) continue;
    if (gone[i] === 1) {
      c.x.set(0);
      c.drop.set(ARRIVE_DROP);
      move(c.drop, 0, SWIPE_SPRING, calm, 0);
      c.alpha.set(withDelay(120, withTiming(1, { duration: 260 })));
      next[i] = 0;
    } else {
      move(c.x, 0, SWIPE_SPRING, calm, 0);
    }
  }
  m.gone.set(next);
  settleSlots(m);
}

/** True once the stack rests closed, whole and still. */
export function stackAtRest(m: StackMotion, total: number) {
  'worklet';
  if (Math.abs(m.peek.get()) > 0.004) return false;
  for (let i = 0; i < total && i < m.cards.length; i += 1) {
    const c = m.cards[i];
    if (Math.abs(c.e.get()) > 0.004 || Math.abs(c.slot.get() - i) > 0.01 || c.alpha.get() < 0.995) return false;
    if (Math.abs(c.x.get()) > 0.5 || Math.abs(c.drop.get()) > 0.5) return false;
  }
  return true;
}

/** How far the content under the group moves while it opens, closes or loses a card. */
export function belowShift(m: StackMotion, total: number) {
  'worklet';
  const last = m.cards[Math.max(0, Math.min(total, m.cards.length) - 1)];
  const e = last.e.get() + m.peek.get();
  return groupHeight(e, m.count.get()) - groupHeight(0, Math.min(total, m.cards.length));
}

function grab(m: StackMotion) {
  'worklet';
  m.touches.set(m.touches.get() + 1);
  m.held.set(1);
  adoptPose(m);
}

function letGo(m: StackMotion) {
  'worklet';
  m.held.set(0);
}

function ClearGlyph() {
  return (
    <Svg width={10} height={10} viewBox="0 0 10 10">
      <Path d="M1.5 1.5 L8.5 8.5 M8.5 1.5 L1.5 8.5" stroke={INK} strokeWidth={1.6} strokeLinecap="round" />
    </Svg>
  );
}

type CardProps = {
  index: number;
  m: StackMotion;
  width: number;
  notice: NoticeGroup['notices'][number];
};

const StackCard = memo(function StackCard({ index, m, width, notice }: CardProps) {
  const c = m.cards[index];
  const startX = useSharedValue(0);

  const gesture = useMemo(() => {
    const tap = Gesture.Tap()
      .maxDistance(10)
      .onBegin(() => grab(m))
      .onEnd(() => {
        if (m.open.get() === 0) {
          setOpen(m, 1, 0);
          scheduleOnRN(lightTap);
        } else if (m.swiped.get() >= 0) {
          move(m.cards[m.swiped.get()].x, 0, SWIPE_SPRING, m.calm.get(), 0);
          m.swiped.set(-1);
        }
      })
      .onFinalize(() => letGo(m));

    const swipe = Gesture.Pan()
      .activeOffsetX([-14, 14])
      .failOffsetY([-14, 14])
      .onBegin(() => grab(m))
      .onStart(() => {
        const other = m.swiped.get();
        if (other >= 0 && other !== index) {
          move(m.cards[other].x, 0, SWIPE_SPRING, m.calm.get(), 0);
          m.swiped.set(-1);
        }
        startX.set(c.x.get());
      })
      .onUpdate(ev => {
        if (m.open.get() === 0) return;
        c.x.set(rubberRight(startX.get() + ev.translationX));
      })
      .onEnd(ev => {
        if (m.open.get() === 0) return;
        const x = c.x.get();
        if (x < -width * FLING_FRACTION || (ev.velocityX < -FLING_VELOCITY && x < -24)) {
          clearCard(m, index, ev.velocityX);
        } else if (x < -CLEAR_W * OPEN_FRACTION && ev.velocityX < 400) {
          move(c.x, -CLEAR_W - 8, SWIPE_SPRING, m.calm.get(), ev.velocityX);
          m.swiped.set(index);
        } else {
          move(c.x, 0, SWIPE_SPRING, m.calm.get(), ev.velocityX);
          if (m.swiped.get() === index) m.swiped.set(-1);
        }
      })
      .onFinalize(() => letGo(m));

    const pull = Gesture.Pan()
      .activeOffsetY([-14, 14])
      .failOffsetX([-14, 14])
      .onBegin(() => grab(m))
      .onUpdate(ev => {
        if (m.open.get() === 1) return;
        m.peek.set(peekFromDrag(ev.translationY));
      })
      .onEnd(ev => {
        if (m.open.get() === 1) return;
        const unit = (ev.velocityY * PEEK_MAX) / PEEK_RANGE;
        if (ev.translationY > PEEK_OPEN_DISTANCE || ev.velocityY > PEEK_OPEN_VELOCITY) {
          // The peek becomes the start of the fan-out, carrying the finger's speed.
          const p = m.peek.get();
          m.peek.set(0);
          for (let i = 0; i < m.cards.length; i += 1) m.cards[i].e.set(m.cards[i].e.get() + p);
          setOpen(m, 1, unit);
          scheduleOnRN(lightTap);
        } else {
          move(m.peek, 0, PEEK_SPRING, m.calm.get(), unit);
        }
      })
      .onFinalize(() => letGo(m));

    return Gesture.Race(swipe, pull, tap);
  }, [c, index, m, startX, width]);

  const rowStyle = useAnimatedStyle(() => {
    const s = c.slot.get();
    const e = c.e.get() + m.peek.get();
    const eu = clamp01(e);
    const depth = Math.min(Math.max(s, 0), MAX_DEPTH);
    const y = mix(e, collapsedY(s), expandedY(s));
    const k = mix(eu, 1 - DEPTH_SCALE * depth, 1);
    const stacked = s <= MAX_DEPTH ? 1 : clamp01(MAX_DEPTH + 1 - s);
    const drop = c.drop.get();
    return {
      opacity: c.alpha.get() * mix(eu, stacked, 1),
      transform: [{ translateY: y - drop }, { scale: k * (1 + (ARRIVE_SCALE * drop) / ARRIVE_DROP) }],
    };
  });

  const cardStyle = useAnimatedStyle(() => ({ transform: [{ translateX: c.x.get() }] }));

  const faceStyle = useAnimatedStyle(() => {
    const eu = clamp01(c.e.get() + m.peek.get());
    return { opacity: mix(eu, clamp01(1 - c.slot.get()), 1) };
  });

  const tintStyle = useAnimatedStyle(() => {
    const eu = clamp01(c.e.get() + m.peek.get());
    return { opacity: (1 - eu) * Math.min(Math.max(c.slot.get(), 0), MAX_DEPTH) * 0.32 };
  });

  const clearStyle = useAnimatedStyle(() => {
    // A faded (cleared) card leaves no button behind to catch touches.
    const w = c.alpha.get() < 0.05 ? 0 : Math.min(Math.max(-c.x.get() - 8, 0), width);
    return { width: w, opacity: clamp01(w / 36) };
  });
  const clearLabelStyle = useAnimatedStyle(() => {
    const w = -c.x.get() - 8;
    return { opacity: clamp01((w - 48) / 20) };
  });

  const onClear = () => scheduleOnUI(clearCard, m, index, 0);
  const onAction = (name: string) => {
    if (name === 'clear') scheduleOnUI(clearCard, m, index, 0);
    else scheduleOnUI(setOpen, m, m.open.get() === 1 ? 0 : 1, 0);
  };

  return (
    <Animated.View style={[styles.row, { width, zIndex: MAX_CARDS - index }, rowStyle]}>
      <Animated.View style={[styles.clear, clearStyle]}>
        <Pressable style={styles.clearHit} onPress={onClear} accessibilityRole="button" accessibilityLabel="Clear">
          <Animated.Text style={[styles.clearLabel, clearLabelStyle]} numberOfLines={1}>Clear</Animated.Text>
        </Pressable>
      </Animated.View>
      <GestureDetector gesture={gesture}>
        <Animated.View
          style={[styles.card, { width }, cardStyle]}
          accessible
          accessibilityLabel={`${notice.from}, ${notice.time}. ${notice.body}`}
          accessibilityActions={[{ name: 'activate', label: 'Show all' }, { name: 'clear', label: 'Clear' }]}
          onAccessibilityAction={ev => onAction(ev.nativeEvent.actionName)}
        >
          <Animated.View style={[styles.face, faceStyle]}>
            <NoticeFace notice={notice} />
          </Animated.View>
          <Animated.View style={[styles.tint, tintStyle]} pointerEvents="none" />
        </Animated.View>
      </GestureDetector>
    </Animated.View>
  );
});

export type NotificationStackProps = {
  group: NoticeGroup;
  motion: StackMotion;
  width: number;
  /** Reduce Motion: list shown open, fades instead of springs. */
  reducedMotion?: boolean;
};

/** A notification group: a stack that fans out into a list with a header (count, Show less, clear). */
export function NotificationStack({ group, motion: m, width, reducedMotion = false }: NotificationStackProps) {
  const notices = group.notices.slice(0, MAX_CARDS);
  const total = notices.length;
  const [count, setCount] = useState(total);

  useAnimatedReaction(
    () => m.countN.get(),
    (n, prev) => {
      if (prev !== null && n !== prev) scheduleOnRN(setCount, n);
    },
  );
  useAnimatedReaction(
    () => (reducedMotion ? 1 : 0),
    calm => m.calm.set(calm),
  );

  const headerStyle = useAnimatedStyle(() => {
    const e = m.cards[0].e.get();
    const shown = clamp01((e - 0.35) / 0.5) * clamp01(m.count.get());
    return { opacity: shown, transform: [{ translateY: (1 - clamp01(e)) * 10 }] };
  });

  const showLess = () => {
    lightTap();
    scheduleOnUI(setOpen, m, 0, 0);
  };
  const clear = () => scheduleOnUI(clearAll, m);

  return (
    <View style={[styles.group, { width, height: groupHeight(1, total) }]} pointerEvents="box-none">
      <Animated.View style={[styles.header, headerStyle]}>
        <View style={styles.headerText}>
          <Text style={styles.title} numberOfLines={1}>{group.title}</Text>
          <SwapText text={count === 0 ? 'Cleared' : `${count} new`} style={styles.count} reduced={reducedMotion} />
        </View>
        <Pressable onPress={showLess} style={styles.less} hitSlop={6} accessibilityRole="button" accessibilityLabel="Show less">
          <Text style={styles.lessLabel}>Show less</Text>
        </Pressable>
        <Pressable onPress={clear} style={styles.close} hitSlop={8} accessibilityRole="button" accessibilityLabel={`Clear ${group.title}`}>
          <ClearGlyph />
        </Pressable>
      </Animated.View>
      {notices.map((notice, index) => (
        <StackCard key={notice.id} index={index} m={m} width={width} notice={notice} />
      ))}
    </View>
  );
}

export const SHOW_LESS_W = 88;
export const CLOSE_W = 28;

const styles = StyleSheet.create({
  group: { position: 'relative' },
  header: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    height: HEADER_H - 8,
    flexDirection: 'row',
    alignItems: 'center',
  },
  headerText: { flex: 1, flexDirection: 'row', alignItems: 'baseline', minWidth: 0, paddingLeft: 4 },
  title: { fontSize: 20, lineHeight: 26, fontWeight: '700', color: INK, flexShrink: 1 },
  count: { marginLeft: 8, fontSize: 14, fontWeight: '500', color: ACCENT, fontVariant: ['tabular-nums'] },
  less: {
    width: SHOW_LESS_W,
    height: 30,
    borderRadius: 15,
    backgroundColor: 'rgba(27,31,36,0.07)',
    alignItems: 'center',
    justifyContent: 'center',
    marginLeft: 8,
  },
  lessLabel: { fontSize: 14, fontWeight: '600', color: INK },
  close: {
    width: CLOSE_W,
    height: CLOSE_W,
    borderRadius: CLOSE_W / 2,
    backgroundColor: 'rgba(27,31,36,0.07)',
    alignItems: 'center',
    justifyContent: 'center',
    marginLeft: 8,
  },
  row: { position: 'absolute', left: 0, top: 0, height: CARD_H },
  card: { ...cardSurface },
  face: { flex: 1 },
  tint: { ...StyleSheet.absoluteFill, borderRadius: RADIUS, borderCurve: 'continuous', backgroundColor: '#C9CFD7' },
  clear: {
    position: 'absolute',
    right: 0,
    top: 0,
    height: CARD_H,
    borderRadius: RADIUS,
    borderCurve: 'continuous',
    backgroundColor: DANGER,
    overflow: 'hidden',
    boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.25)',
  },
  clearHit: { flex: 1, alignItems: 'flex-end', justifyContent: 'center', paddingRight: 20 },
  clearLabel: { fontSize: 15, fontWeight: '600', color: '#FFF6F5', width: 44, textAlign: 'center' },
});
