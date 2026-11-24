import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { memo, useCallback, useMemo, useState } from 'react';
import { StyleSheet, Text, View, type LayoutChangeEvent } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  scrollTo,
  useAnimatedProps,
  useAnimatedReaction,
  useAnimatedRef,
  useAnimatedScrollHandler,
  useAnimatedStyle,
  useFrameCallback,
  useSharedValue,
  withSpring,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '@/shared/useReducedMotionPreference';

import {
  clamp,
  DROP_SPRING,
  EDGE_SPEED,
  EDGE_ZONE,
  filmCheck,
  filmGhost,
  filmGhostOnCheck,
  filmGhostTap,
  filmHeroSlot,
  filmLift,
  filmRowSlot,
  filmScroll,
  FILM_FROM,
  FILM_TO,
  IDLE_S,
  LIFT_SCALE,
  LIFT_SPRING,
  LONG_PRESS_MS,
  LOOP,
  mix,
  MODE_AUTO,
  MODE_MANUAL,
  MODE_RETURN,
  moveOrder,
  PART_SPRING,
  RETURN_S,
  ROW_H,
} from './springReorderMotion';

const AnimatedPath = Animated.createAnimatedComponent(Path);

const PAGE = '#f5f2ea';
const CARD = '#fffdf8';
const INK = '#26302b';
const MUTE = '#7b8078';
const HAIR = 'rgba(38,48,43,0.1)';
const GREEN = '#2f7d5b';
const CHECK_LEN = 16.8;
const CHECK_SIZE = 26;
const GRIP_X = 34; // from the right edge
const CHECK_X = 33; // centre of the circle from the left edge
const DOT = 44;

export type ChecklistItem = {
  id: string;
  title: string;
  note: string;
  packed?: boolean;
};

export type SpringReorderChecklistProps = {
  items: readonly ChecklistItem[];
  autoPlay?: boolean;
  /** Called after a drop with the ids in their new order. */
  onReorder?: (ids: string[]) => void;
  /** Called with the number of checked rows whenever it changes. */
  onPackedChange?: (count: number) => void;
};

type Ctx = {
  count: number;
  mode: SharedValue<number>;
  clock: SharedValue<number>;
  filmT: SharedValue<number>;
  filmTo: number;
  lastTouch: SharedValue<number>;
  holding: SharedValue<number>;
  order: SharedValue<number[]>;
  checked: SharedValue<number[]>;
  heroChecked: SharedValue<number>;
  dragId: SharedValue<number>;
  dragY: SharedValue<number>;
  startY: SharedValue<number>;
  startFinger: SharedValue<number>;
  startScroll: SharedValue<number>;
  finger: SharedValue<number>;
  scrollY: SharedValue<number>;
};

function liftTick() {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
}

function slotTick() {
  void Haptics.selectionAsync();
}

function dropTick() {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
}

function put(sv: SharedValue<number>, v: number) {
  'worklet';
  if (sv.get() !== v) sv.set(v);
}

/** The first touch during the film: its order and checks become the real ones. */
function takeOver(c: Ctx) {
  'worklet';
  c.holding.set(1);
  c.lastTouch.set(c.clock.get());
  if (c.mode.get() !== MODE_AUTO) {
    c.mode.set(MODE_MANUAL);
    return;
  }
  const t = c.filmT.get();
  const slot = clamp(Math.round(filmHeroSlot(t, FILM_FROM, c.filmTo)), FILM_FROM, c.filmTo);
  c.order.set(moveOrder(c.order.get(), FILM_FROM, slot));
  if (filmCheck(t) > 0.5) {
    const next = c.checked.get().slice();
    next[FILM_FROM] = 1;
    c.checked.set(next);
  }
  c.mode.set(MODE_MANUAL);
}

function release(c: Ctx) {
  'worklet';
  c.holding.set(0);
  c.lastTouch.set(c.clock.get());
}

/** Places the held row under the finger and reorders the table when it crosses into another slot. */
function applyDrag(c: Ctx) {
  'worklet';
  const id = c.dragId.get();
  if (id < 0) return;
  const y = c.startY.get() + (c.finger.get() - c.startFinger.get()) + (c.scrollY.get() - c.startScroll.get());
  const top = -ROW_H * 0.4;
  const bottom = (c.count - 1) * ROW_H + ROW_H * 0.4;
  c.dragY.set(clamp(y, top, bottom));
  const slot = clamp(Math.round(c.dragY.get() / ROW_H), 0, c.count - 1);
  const order = c.order.get();
  if (order[id] !== slot) {
    c.order.set(moveOrder(order, id, slot));
    scheduleOnRN(slotTick);
  }
}

type RowProps = {
  item: ChecklistItem;
  index: number;
  ctx: Ctx;
  onLift: () => void;
  onDrop: (order: number[]) => void;
  onToggled: (checked: number[]) => void;
};

const Row = memo(function Row({ item, index, ctx, onLift, onDrop, onToggled }: RowProps) {
  const y = useSharedValue(index * ROW_H);
  const lift = useSharedValue(0);
  const draw = useSharedValue(item.packed ? 1 : 0);
  const titleW = useSharedValue(0);
  const isHero = index === FILM_FROM;
  const homeDraw = item.packed ? 1 : 0;

  // The film writes the row directly.
  useAnimatedReaction(
    () => (ctx.mode.get() === MODE_AUTO ? ctx.filmT.get() : -1),
    (t) => {
      if (t < 0) return;
      put(y, filmRowSlot(index, t, FILM_FROM, ctx.filmTo) * ROW_H);
      if (isHero) {
        put(lift, filmLift(t));
        put(draw, Math.max(homeDraw, filmCheck(t)));
      }
    },
  );

  // A held row follows the finger.
  useAnimatedReaction(
    () => (ctx.dragId.get() === index ? ctx.dragY.get() : null),
    (v) => {
      if (v !== null) y.set(v);
    },
  );

  // Off the film, a row springs to its slot: parting for a neighbour, settling with an overshoot when dropped.
  useAnimatedReaction(
    () => {
      if (ctx.mode.get() === MODE_AUTO) return -1;
      if (ctx.dragId.get() === index) return -2;
      return ctx.order.get()[index];
    },
    (slot, prev) => {
      if (slot < 0 || prev === null || slot === prev) return;
      y.set(withSpring(slot * ROW_H, prev === -2 ? DROP_SPRING : PART_SPRING));
    },
  );

  useAnimatedReaction(
    () => (ctx.mode.get() === MODE_AUTO ? -1 : ctx.dragId.get() === index ? 1 : 0),
    (held, prev) => {
      if (held < 0 || held === prev) return;
      lift.set(withSpring(held, LIFT_SPRING));
    },
  );

  useAnimatedReaction(
    () => (ctx.mode.get() === MODE_AUTO ? -1 : ctx.checked.get()[index]),
    (on, prev) => {
      if (on < 0 || on === prev) return;
      draw.set(withTiming(on, { duration: on ? 420 : 260 }));
    },
  );

  const rowStyle = useAnimatedStyle(() => {
    const l = lift.get();
    return {
      zIndex: l > 0.01 ? 10 : 1,
      transform: [{ translateY: y.get() }, { scale: 1 + (LIFT_SCALE - 1) * l }],
    };
  });
  const plateStyle = useAnimatedStyle(() => ({ opacity: clamp(lift.get(), 0, 1) }));
  const hairStyle = useAnimatedStyle(() => ({ opacity: 1 - clamp(lift.get() * 2, 0, 1) }));
  const discStyle = useAnimatedStyle(() => {
    const d = draw.get();
    return { opacity: clamp(d * 3, 0, 1), transform: [{ scale: mix(0.72, 1, d) }] };
  });
  const checkProps = useAnimatedProps(() => {
    const d = draw.get();
    return { strokeDashoffset: CHECK_LEN * (1 - d), strokeOpacity: d > 0.02 ? 1 : 0 };
  });
  const titleStyle = useAnimatedStyle(() => ({ opacity: 1 - 0.48 * draw.get() }));
  const strikeStyle = useAnimatedStyle(() => ({ width: titleW.get(), transform: [{ scaleX: draw.get() }] }));

  const onTitleLayout = useCallback(
    (e: LayoutChangeEvent) => {
      titleW.set(e.nativeEvent.layout.width);
    },
    [titleW],
  );

  const pan = Gesture.Pan()
    .activateAfterLongPress(LONG_PRESS_MS)
    .maxPointers(1)
    .onBegin(() => {
      takeOver(ctx);
    })
    .onStart((e) => {
      const slot = ctx.order.get()[index];
      ctx.startY.set(slot * ROW_H);
      ctx.dragY.set(slot * ROW_H);
      ctx.startFinger.set(e.absoluteY);
      ctx.finger.set(e.absoluteY);
      ctx.startScroll.set(ctx.scrollY.get());
      ctx.dragId.set(index);
      scheduleOnRN(onLift);
    })
    .onUpdate((e) => {
      if (ctx.dragId.get() !== index) return;
      ctx.finger.set(e.absoluteY);
      applyDrag(ctx);
    })
    .onFinalize(() => {
      if (ctx.dragId.get() === index) {
        ctx.dragId.set(-1);
        scheduleOnRN(onDrop, ctx.order.get().slice());
      }
      release(ctx);
    });

  const tap = Gesture.Tap()
    .maxDuration(LONG_PRESS_MS - 20)
    .onEnd((_e, success) => {
      if (!success) return;
      const next = ctx.checked.get().slice();
      next[index] = next[index] ? 0 : 1;
      ctx.checked.set(next);
      scheduleOnRN(onToggled, next);
    });

  const gesture = Gesture.Race(pan, tap);

  return (
    <GestureDetector gesture={gesture}>
      <Animated.View
        accessibilityLabel={`${item.title}, ${item.note}`}
        accessibilityRole="checkbox"
        accessible
        style={[styles.row, rowStyle]}
      >
        <Animated.View style={[styles.base, hairStyle]} />
        <Animated.View style={[styles.plate, plateStyle]} />
        <View style={styles.check}>
          <View style={styles.ring} />
          <Animated.View style={[styles.disc, discStyle]} />
          <Svg height={CHECK_SIZE} style={StyleSheet.absoluteFill} viewBox="0 0 24 24" width={CHECK_SIZE}>
            <AnimatedPath
              animatedProps={checkProps}
              d="M6.4 12.6 L10.5 16.5 L17.8 8.2"
              fill="none"
              stroke="#fbfff9"
              strokeDasharray={`${CHECK_LEN} ${CHECK_LEN}`}
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2.4}
            />
          </Svg>
        </View>
        <View style={styles.texts}>
          <View style={styles.titleWrap}>
            <Animated.Text numberOfLines={1} onLayout={onTitleLayout} style={[styles.title, titleStyle]}>
              {item.title}
            </Animated.Text>
            <Animated.View style={[styles.strike, strikeStyle]} />
          </View>
          <Text numberOfLines={1} style={styles.note}>
            {item.note}
          </Text>
        </View>
        <View style={styles.grip}>
          <View style={styles.gripLine} />
          <View style={styles.gripLine} />
          <View style={styles.gripLine} />
        </View>
        <Animated.View style={[styles.hair, hairStyle]} />
      </Animated.View>
    </GestureDetector>
  );
});

/**
 * A checklist reordered by long-press. The held row lifts, its neighbours part on springs while it moves, it settles
 * with a small overshoot, and the check draws its stroke. Rows are transforms over a fixed order table: nothing is laid
 * out while dragging, and the list scrolls itself when the row nears an edge.
 */
export function SpringReorderChecklist({ items, autoPlay = true, onReorder, onPackedChange }: SpringReorderChecklistProps) {
  const reduced = useReducedMotionPreference();
  const film = autoPlay && !reduced;
  const insets = useSafeAreaInsets();
  const count = items.length;
  const filmTo = Math.min(FILM_TO, count - 1);
  const bottomPad = Math.max(insets.bottom, 12) + 28;
  const contentH = count * ROW_H + bottomPad;

  const home = useMemo(() => items.map((_, i) => i), [items]);
  const homeChecks = useMemo(() => items.map((it) => (it.packed ? 1 : 0)), [items]);

  const [locked, setLocked] = useState(false);

  const scrollRef = useAnimatedRef<Animated.ScrollView>();
  const mode = useSharedValue(film ? MODE_AUTO : MODE_MANUAL);
  const clock = useSharedValue(0);
  const loopStart = useSharedValue(-1);
  const filmT = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const returnAt = useSharedValue(0);
  const holding = useSharedValue(0);
  const order = useSharedValue<number[]>(home);
  const checked = useSharedValue<number[]>(homeChecks);
  const heroChecked = useSharedValue(0);
  const dragId = useSharedValue(-1);
  const dragY = useSharedValue(0);
  const startY = useSharedValue(0);
  const startFinger = useSharedValue(0);
  const startScroll = useSharedValue(0);
  const finger = useSharedValue(0);
  const scrollY = useSharedValue(0);
  const viewH = useSharedValue(0);
  const ghost = useSharedValue(0);
  const ghostX = useSharedValue(0);
  const ghostY = useSharedValue(0);
  const ghostTap = useSharedValue(0);
  const viewW = useSharedValue(0);

  const ctx = useMemo<Ctx>(
    () => ({
      count,
      mode,
      clock,
      filmT,
      filmTo,
      lastTouch,
      holding,
      order,
      checked,
      heroChecked,
      dragId,
      dragY,
      startY,
      startFinger,
      startScroll,
      finger,
      scrollY,
    }),
    [checked, clock, count, dragId, dragY, filmT, filmTo, finger, heroChecked, holding, lastTouch, mode, order, scrollY, startFinger, startScroll, startY],
  );

  useFrameCallback((frame) => {
    'worklet';
    const now = frame.timestamp / 1000;
    const dt = clamp(now - clock.get(), 1 / 240, 0.05);
    clock.set(now);
    const m = mode.get();
    if (m === MODE_AUTO) {
      if (!film) {
        mode.set(MODE_MANUAL);
        put(ghost, 0);
        return;
      }
      if (viewH.get() <= 0) return;
      if (loopStart.get() < 0) loopStart.set(now);
      const t = (now - loopStart.get()) % LOOP;
      filmT.set(t);
      const maxScroll = Math.max(0, contentH - viewH.get());
      const distance = Math.min(maxScroll, Math.max(0, (filmTo + 1) * ROW_H + 18 - viewH.get()));
      const sy = filmScroll(t, distance);
      if (Math.abs(sy - scrollY.get()) > 0.01) scrollTo(scrollRef, 0, sy, false);
      scrollY.set(sy);
      const onCheck = filmGhostOnCheck(t);
      put(ghost, clamp(filmGhost(t), 0, 1));
      put(ghostX, onCheck ? CHECK_X : viewW.get() - GRIP_X);
      put(ghostY, filmHeroSlot(t, FILM_FROM, filmTo) * ROW_H + ROW_H / 2 - sy);
      put(ghostTap, filmGhostTap(t));
    } else if (m === MODE_MANUAL) {
      put(ghost, 0);
      if (dragId.get() >= 0) {
        // Auto-scroll: faster the deeper the row is in the edge zone.
        const center = dragY.get() - scrollY.get() + ROW_H / 2;
        const h = viewH.get();
        let v = 0;
        if (center < EDGE_ZONE) v = -EDGE_SPEED * Math.pow((EDGE_ZONE - center) / EDGE_ZONE, 2);
        else if (center > h - EDGE_ZONE) v = EDGE_SPEED * Math.pow((center - (h - EDGE_ZONE)) / EDGE_ZONE, 2);
        if (v !== 0) {
          const maxScroll = Math.max(0, contentH - h);
          const next = clamp(scrollY.get() + v * dt, 0, maxScroll);
          if (next !== scrollY.get()) {
            scrollY.set(next);
            scrollTo(scrollRef, 0, next, false);
            applyDrag(ctx);
          }
        }
      } else if (film && holding.get() === 0 && now - lastTouch.get() > IDLE_S) {
        mode.set(MODE_RETURN);
        returnAt.set(now + RETURN_S);
        order.set(home);
        checked.set(homeChecks);
        scrollTo(scrollRef, 0, 0, true);
      }
    } else if (now >= returnAt.get()) {
      scrollTo(scrollRef, 0, 0, false);
      scrollY.set(0);
      filmT.set(0);
      loopStart.set(now);
      mode.set(MODE_AUTO);
    }
  });

  const onScroll = useAnimatedScrollHandler({
    onScroll: (e) => {
      if (mode.get() === MODE_AUTO) return;
      scrollY.set(e.contentOffset.y);
      if (mode.get() === MODE_MANUAL) lastTouch.set(clock.get());
    },
    onBeginDrag: () => {
      takeOver(ctx);
    },
    onEndDrag: () => {
      release(ctx);
    },
    onMomentumEnd: () => {
      lastTouch.set(clock.get());
    },
  });

  // The count the header shows: the table's, plus the film's check.
  useAnimatedReaction(
    () => {
      const list = checked.get();
      let n = 0;
      for (let i = 0; i < list.length; i += 1) n += list[i];
      if (mode.get() === MODE_AUTO && !list[FILM_FROM] && filmCheck(filmT.get()) > 0.5) n += 1;
      return n;
    },
    (n, prev) => {
      if (n !== prev && onPackedChange) scheduleOnRN(onPackedChange, n);
    },
  );

  const lockScroll = useCallback(() => {
    setLocked(true);
    liftTick();
  }, []);
  const onDrop = useCallback(
    (next: number[]) => {
      setLocked(false);
      dropTick();
      if (!onReorder) return;
      const ids: string[] = [];
      next.forEach((slot, i) => {
        ids[slot] = items[i].id;
      });
      onReorder(ids);
    },
    [items, onReorder],
  );
  const onToggled = useCallback((_next: number[]) => {
    slotTick();
  }, []);

  const onLayout = useCallback(
    (e: LayoutChangeEvent) => {
      viewH.set(e.nativeEvent.layout.height);
      viewW.set(e.nativeEvent.layout.width);
    },
    [viewH, viewW],
  );

  const ghostStyle = useAnimatedStyle(() => ({
    opacity: ghost.get() * 0.9,
    transform: [
      { translateX: ghostX.get() - DOT / 2 },
      { translateY: ghostY.get() - DOT / 2 },
      { scale: 1 - 0.14 * ghostTap.get() },
    ],
  }));

  return (
    <View onLayout={onLayout} style={styles.viewport}>
      <Animated.ScrollView
        contentContainerStyle={{ height: contentH }}
        onScroll={onScroll}
        ref={scrollRef}
        scrollEnabled={!locked}
        scrollEventThrottle={16}
        showsVerticalScrollIndicator={false}
      >
        <View style={{ height: count * ROW_H }}>
          {items.map((item, i) => (
            <Row ctx={ctx} index={i} item={item} key={item.id} onDrop={onDrop} onLift={lockScroll} onToggled={onToggled} />
          ))}
        </View>
      </Animated.ScrollView>
      <LinearGradient colors={['rgba(245,242,234,0)', 'rgba(245,242,234,0.86)', PAGE]} pointerEvents="none" style={[styles.fade, { height: 44 + insets.bottom * 0.5 }]} />
      <Animated.View pointerEvents="none" style={[styles.ghost, ghostStyle]} />
    </View>
  );
}

const styles = StyleSheet.create({
  viewport: { flex: 1, backgroundColor: PAGE },
  row: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    height: ROW_H,
    flexDirection: 'row',
    alignItems: 'center',
    paddingLeft: CHECK_X - CHECK_SIZE / 2,
    paddingRight: 20,
  },
  base: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, backgroundColor: PAGE },
  plate: {
    position: 'absolute',
    top: 0,
    bottom: 0,
    left: 6,
    right: 6,
    borderRadius: 16,
    backgroundColor: CARD,
    boxShadow: '0 1px 3px rgba(32,40,34,0.2), 0 14px 30px rgba(32,40,34,0.2)',
  },
  check: { width: CHECK_SIZE, height: CHECK_SIZE },
  ring: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, borderRadius: CHECK_SIZE / 2, borderWidth: 1.75, borderColor: 'rgba(38,48,43,0.28)' },
  disc: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, borderRadius: CHECK_SIZE / 2, backgroundColor: GREEN },
  texts: { flex: 1, marginLeft: 14, marginRight: 12 },
  titleWrap: { alignSelf: 'flex-start', maxWidth: '100%', justifyContent: 'center' },
  title: { color: INK, fontSize: 17, fontWeight: '500' },
  strike: { position: 'absolute', left: 0, height: 1.5, borderRadius: 1, backgroundColor: 'rgba(38,48,43,0.55)', transformOrigin: 'left' },
  note: { color: MUTE, fontSize: 13, marginTop: 2, fontVariant: ['tabular-nums'] },
  grip: { width: 28, height: 28, alignItems: 'center', justifyContent: 'center', gap: 3 },
  gripLine: { width: 16, height: 1.75, borderRadius: 1, backgroundColor: 'rgba(38,48,43,0.3)' },
  hair: { position: 'absolute', left: CHECK_X + CHECK_SIZE / 2 + 14, right: 0, bottom: 0, height: StyleSheet.hairlineWidth, backgroundColor: HAIR },
  fade: { position: 'absolute', left: 0, right: 0, bottom: 0 },
  ghost: { position: 'absolute', left: 0, top: 0, width: DOT, height: DOT, borderRadius: DOT / 2, backgroundColor: 'rgba(38,48,43,0.2)' },
});
