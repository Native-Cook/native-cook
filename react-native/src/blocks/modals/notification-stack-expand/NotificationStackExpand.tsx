// Relay, a team inbox: the screen, the rest of the feed and the autoPlay script around the reusable NotificationStack.
import { BlurView } from 'expo-blur';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { StyleSheet, Text, useWindowDimensions, View, type GestureResponderEvent } from 'react-native';
import Animated, { useAnimatedStyle, useFrameCallback, useSharedValue, withTiming, type SharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle, Path, Rect } from 'react-native-svg';

import { useReducedMotionPreference } from '@/shared/useReducedMotionPreference';

import { CRIT, FILTERS, LAUNCH, STANDUP, YESTERDAY, type NoticeGroup } from './data';
import { FeedFilter } from './FeedFilter';
import {
  ARRIVE_DROP,
  CARD_H,
  collapsedY,
  COLLAPSE_SPRING,
  DEPTH_SCALE,
  easeInOutCubic,
  easeOutCubic,
  EXPAND_SPRING,
  GAP_SPRING,
  GHOST_PEEK,
  groupHeight,
  HEADER_H,
  LOOP,
  MAX_CARDS,
  mix,
  PEEK_SPRING,
  peekFromDrag,
  PUSH_SPRING,
  RESUME_AFTER,
  seg,
  springAt,
  STAGGER,
  SWIPE_DRAG,
  T,
} from './motion';
import { ACCENT, BG, cardSurface, INK, MUTE, NoticeCard, NoticeFace } from './NoticeCard';
import {
  adoptPose,
  belowShift,
  CLOSE_W,
  NotificationStack,
  restoreStack,
  SHOW_LESS_W,
  stackAtRest,
  useStackMotion,
  type StackMotion,
} from './NotificationStack';

// Layout, in points.
const SIDE = 16;
const BRAND_H = 28;
const TITLE_H = 41;
const FILTER_H = 32;
const LABEL_H = 22;
/** From the top of the header block to the top of the group. */
const ABOVE_GROUP = BRAND_H + 4 + TITLE_H + 12 + FILTER_H + 20 + LABEL_H + 8;
const BELOW_GAP = 14;
const TAB_H = 50;
const GHOST = 44;

const MODE_AUTO = 0;
const MODE_MANUAL = 1;
const MODE_RETURN = 2;

/** Writes only real changes (to 1e-4), so nothing downstream runs while the pose holds still. */
function put(value: SharedValue<number>, next: number) {
  'worklet';
  const q = Math.round(next * 10000) / 10000;
  if (value.get() !== q) value.set(q);
}

/** The ghost finger's drag left across the first card: slow start, fast at the release. */
function swipeX(t: number, width: number) {
  'worklet';
  if (t <= T.swipeStart) return 0;
  if (t < T.swipeEnd) {
    const u = (t - T.swipeStart) / (T.swipeEnd - T.swipeStart);
    return -SWIPE_DRAG * (0.35 * u + 0.65 * u * u);
  }
  const v = seg(t, T.swipeEnd, T.flingSpan);
  const travel = width + 24 - SWIPE_DRAG;
  return -SWIPE_DRAG - travel * (0.4 * v + 0.6 * v * v);
}

/** The ghost finger's pull down on the stack, pt. */
function pullDy(t: number) {
  'worklet';
  return GHOST_PEEK * easeInOutCubic(seg(t, T.peekStart, T.peekRelease - T.peekStart));
}

function peekAt(t: number) {
  'worklet';
  if (t < T.peekStart) return 0;
  if (t < T.peekRelease) return peekFromDrag(pullDy(t));
  return peekFromDrag(GHOST_PEEK) * (1 - springAt(t - T.peekRelease, PEEK_SPRING));
}

/** Every card of the stack at loop time t. */
function applyPose(m: StackMotion, t: number, width: number, total: number) {
  'worklet';
  const gapShift = springAt(t - T.gap, GAP_SPRING) - springAt(t - T.arrive - 0.04, PUSH_SPRING);
  const arrive = easeOutCubic(seg(t, T.arrive, T.arriveSpan));
  for (let i = 0; i < total; i += 1) {
    const c = m.cards[i];
    const open = springAt(t - T.expand - i * STAGGER, EXPAND_SPRING);
    const close = springAt(t - T.collapse - (total - 1 - i) * STAGGER, COLLAPSE_SPRING);
    put(c.e, open - close);
    if (i === 0) {
      put(c.slot, 0);
      put(c.x, t < T.arrive ? swipeX(t, width) : 0);
      put(c.alpha, t < T.arrive ? 1 - seg(t, T.swipeEnd + 0.12, 0.14) : arrive);
      put(c.drop, t < T.arrive ? 0 : ARRIVE_DROP * (1 - arrive));
    } else {
      put(c.slot, i - gapShift);
      put(c.x, 0);
      put(c.alpha, 1);
      put(c.drop, 0);
    }
  }
  put(m.peek, peekAt(t));
  put(m.count, total - gapShift);
  put(m.countN, t >= T.gap && t < T.arrive ? total - 1 : total);
}

type Point = { t: number; x: number; y: number };

function ghostAt(path: Point[], t: number, axis: 'x' | 'y') {
  'worklet';
  if (t <= path[0].t) return path[0][axis];
  for (let i = 1; i < path.length; i += 1) {
    if (t <= path[i].t) {
      const u = easeInOutCubic((t - path[i - 1].t) / (path[i].t - path[i - 1].t));
      return mix(u, path[i - 1][axis], path[i][axis]);
    }
  }
  return path[path.length - 1][axis];
}

function ghostOpacity(t: number) {
  'worklet';
  const a = seg(t, T.ghostIn, 0.18) * (1 - seg(t, T.tapOpen + 0.15, 0.2));
  const b = seg(t, T.swipeStart - 0.35, 0.2) * (1 - seg(t, T.swipeEnd + 0.05, 0.18));
  const c = seg(t, T.tapLess - 0.35, 0.2) * (1 - seg(t, T.tapLess + 0.1, 0.2));
  const d = seg(t, T.peekStart - 0.35, 0.2) * (1 - seg(t, T.peekRelease + 0.05, 0.2));
  return Math.max(a, b, c, d);
}

function ghostPress(t: number) {
  'worklet';
  let press = 0;
  const taps = [T.tapOpen, T.tapLess];
  for (let i = 0; i < taps.length; i += 1) {
    const d = t - taps[i];
    if (d > -0.1 && d < 0.12) press = Math.max(press, d < 0 ? 1 + d / 0.1 : 1 - d / 0.12);
  }
  const drag = seg(t, T.swipeStart - 0.08, 0.08) * (1 - seg(t, T.swipeEnd, 0.1));
  const pull = seg(t, T.peekStart - 0.08, 0.08) * (1 - seg(t, T.peekRelease, 0.1));
  return Math.max(press, drag, pull);
}

/** The rest pose: stacked, whole, still. Reduce Motion: the list shown open instead. */
function staticPose(m: StackMotion, total: number, open: number) {
  'worklet';
  for (let i = 0; i < m.cards.length; i += 1) {
    const c = m.cards[i];
    c.e.set(open);
    c.slot.set(i);
    c.x.set(0);
    c.alpha.set(i < total ? 1 : 0);
    c.drop.set(0);
  }
  m.peek.set(0);
  m.count.set(total);
  m.countN.set(total);
  m.open.set(open);
  m.swiped.set(-1);
  const gone = [0, 0, 0, 0];
  for (let i = 0; i < gone.length; i += 1) gone[i] = i < total ? 0 : 1;
  m.gone.set(gone);
}

/** A group that stays stacked: its back cards peek under the front one. */
function StillStack({ group, width }: { group: NoticeGroup; width: number }) {
  const shown = group.notices.slice(0, 3);
  return (
    <View style={{ width, height: groupHeight(0, shown.length) }} accessible accessibilityLabel={`${group.title}, ${shown.length} notifications`}>
      {shown
        .map((notice, i) => (
          <View
            key={notice.id}
            style={[
              styles.still,
              { width, transform: [{ translateY: collapsedY(i) }, { scale: 1 - DEPTH_SCALE * i }] },
            ]}
          >
            {i === 0 ? <NoticeFace notice={notice} /> : <View style={[styles.stillTint, { opacity: 0.32 * i }]} />}
          </View>
        ))
        .reverse()}
    </View>
  );
}

function BrandMark() {
  return (
    <Svg width={22} height={22} viewBox="0 0 22 22">
      <Rect x={1} y={5} width={14} height={14} rx={4.5} fill="#8FC2B6" />
      <Rect x={7} y={2} width={14} height={14} rx={4.5} fill={ACCENT} />
      <Path d="M10.5 9 H17.5 M10.5 12 H15" stroke="#E9F3F0" strokeWidth={1.6} strokeLinecap="round" />
    </Svg>
  );
}

type TabIcon = 'inbox' | 'channels' | 'search' | 'you';

function TabGlyph({ kind, color }: { kind: TabIcon; color: string }) {
  const stroke = { stroke: color, strokeWidth: 1.8, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const, fill: 'none' };
  return (
    <Svg width={26} height={24} viewBox="0 0 26 24">
      {kind === 'inbox' ? (
        <Path d="M4 13 L6.5 5.2 A2 2 0 0 1 8.4 3.8 H17.6 A2 2 0 0 1 19.5 5.2 L22 13 V18 A2 2 0 0 1 20 20 H6 A2 2 0 0 1 4 18 Z M4 13 H9 L10.5 15.5 H15.5 L17 13 H22" {...stroke} />
      ) : null}
      {kind === 'channels' ? <Path d="M10 3.5 L8.4 20.5 M17.6 3.5 L16 20.5 M5 8.5 H21.5 M4.5 15.5 H21" {...stroke} /> : null}
      {kind === 'search' ? (
        <>
          <Circle cx={11.5} cy={10.5} r={6.5} {...stroke} />
          <Path d="M16.3 15.3 L21 20" {...stroke} />
        </>
      ) : null}
      {kind === 'you' ? (
        <>
          <Circle cx={13} cy={8.5} r={4.2} {...stroke} />
          <Path d="M5 20.5 C6.3 16.4 9.3 14.6 13 14.6 C16.7 14.6 19.7 16.4 21 20.5" {...stroke} />
        </>
      ) : null}
    </Svg>
  );
}

const TABS: { kind: TabIcon; label: string }[] = [
  { kind: 'inbox', label: 'Inbox' },
  { kind: 'channels', label: 'Channels' },
  { kind: 'search', label: 'Search' },
  { kind: 'you', label: 'You' },
];

export type NotificationStackExpandProps = {
  /** Plays the 9 s loop until a finger takes over; it resumes 2 s after the last touch. */
  autoPlay?: boolean;
  /** The group that stacks and fans out (its first four notices). */
  group?: NoticeGroup;
};

export function NotificationStackExpand({ autoPlay = true, group = LAUNCH }: NotificationStackExpandProps) {
  const { width } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const playing = autoPlay && !reduced;
  const total = Math.min(group.notices.length, MAX_CARDS);

  const m = useStackMotion(total);
  const [filter, setFilter] = useState(0);

  const clock = useSharedValue(0);
  const mode = useSharedValue(playing ? MODE_AUTO : MODE_MANUAL);
  const seen = useSharedValue(0);
  const idle = useSharedValue(0);
  const rootHeld = useSharedValue(0);
  const ghostX = useSharedValue(0);
  const ghostY = useSharedValue(0);
  const ghostO = useSharedValue(0);
  const ghostS = useSharedValue(0);

  const rowW = width - SIDE * 2;
  const headerTop = insets.top + 6;
  const groupTop = headerTop + ABOVE_GROUP;
  const belowTop = groupTop + groupHeight(0, total) + BELOW_GAP;
  const tabBottom = Math.max(insets.bottom, 8);

  const ghostPath = useMemo<Point[]>(() => {
    const cx = SIDE + rowW * 0.56;
    const cy = groupTop + CARD_H / 2;
    const c0y = groupTop + HEADER_H + CARD_H / 2;
    const grabX = SIDE + rowW - 56;
    const lessX = SIDE + rowW - CLOSE_W - 8 - SHOW_LESS_W / 2;
    const lessY = groupTop + (HEADER_H - 8) / 2;
    const pullY = cy - 8;
    return [
      { t: T.ghostIn, x: cx + 28, y: cy + 40 },
      { t: T.tapOpen - 0.08, x: cx, y: cy },
      { t: T.tapOpen + 0.12, x: cx, y: cy },
      { t: T.tapOpen + 0.4, x: cx + 12, y: cy + 30 },
      { t: T.swipeStart - 0.35, x: grabX + 18, y: c0y + 30 },
      { t: T.swipeStart - 0.04, x: grabX, y: c0y },
      { t: T.swipeStart, x: grabX, y: c0y },
      // While the finger drags, its x is the card's (below); y holds.
      { t: T.swipeEnd, x: grabX - SWIPE_DRAG, y: c0y },
      { t: T.swipeEnd + 0.24, x: grabX - SWIPE_DRAG - 70, y: c0y - 10 },
      { t: T.tapLess - 0.35, x: lessX + 20, y: lessY + 40 },
      { t: T.tapLess - 0.08, x: lessX, y: lessY },
      { t: T.tapLess + 0.12, x: lessX, y: lessY },
      { t: T.tapLess + 0.4, x: lessX + 10, y: lessY + 30 },
      { t: T.peekStart - 0.35, x: cx + 20, y: pullY + 34 },
      { t: T.peekStart - 0.04, x: cx, y: pullY },
      { t: T.peekStart, x: cx, y: pullY },
      { t: T.peekRelease, x: cx, y: pullY + GHOST_PEEK },
      { t: T.peekRelease + 0.3, x: cx + 10, y: pullY + GHOST_PEEK + 14 },
    ];
  }, [groupTop, rowW]);

  const grabX = SIDE + rowW - 56;
  const pullY = groupTop + CARD_H / 2 - 8;

  const onTouch = useCallback((event: GestureResponderEvent) => {
    rootHeld.set(event.nativeEvent.touches.length);
    m.touches.set(m.touches.get() + 1);
  }, [m.touches, rootHeld]);

  const frameLoop = useFrameCallback(frame => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 48) / 1000;
    if (dt <= 0) return;

    if (m.touches.get() !== seen.get()) {
      seen.set(m.touches.get());
      idle.set(0);
      if (mode.get() !== MODE_MANUAL) {
        adoptPose(m);
        ghostO.set(withTiming(0, { duration: 120 }));
        mode.set(MODE_MANUAL);
      }
    }
    if (!playing) return;

    if (mode.get() === MODE_MANUAL) {
      if (m.held.get() > 0 || rootHeld.get() > 0) {
        idle.set(0);
        return;
      }
      idle.set(idle.get() + dt);
      if (idle.get() >= RESUME_AFTER) {
        restoreStack(m, total);
        mode.set(MODE_RETURN);
      }
      return;
    }
    if (mode.get() === MODE_RETURN) {
      if (stackAtRest(m, total)) {
        clock.set(0);
        m.driven.set(1);
        mode.set(MODE_AUTO);
      }
      return;
    }

    let t = clock.get() + dt;
    if (t >= LOOP) t -= LOOP;
    clock.set(t);
    applyPose(m, t, rowW, total);
    const inSwipe = t > T.swipeStart && t < T.swipeEnd;
    const inPull = t > T.peekStart && t < T.peekRelease;
    ghostX.set(inSwipe ? grabX + swipeX(t, rowW) : ghostAt(ghostPath, t, 'x'));
    ghostY.set(inPull ? pullY + pullDy(t) : ghostAt(ghostPath, t, 'y'));
    ghostO.set(ghostOpacity(t));
    ghostS.set(ghostPress(t));
  }, playing);

  useEffect(() => {
    frameLoop.setActive(playing);
    if (playing) {
      clock.set(0);
      mode.set(MODE_AUTO);
      m.driven.set(1);
      return;
    }
    // Reduce Motion: the list stands open and legible. autoPlay off: the resting stack.
    mode.set(MODE_MANUAL);
    m.driven.set(0);
    ghostO.set(0);
    staticPose(m, total, reduced ? 1 : 0);
  }, [clock, frameLoop, ghostO, m, mode, playing, reduced, total]);

  const belowStyle = useAnimatedStyle(() => ({ transform: [{ translateY: belowShift(m, total) }] }));

  const ghostStyle = useAnimatedStyle(() => ({
    opacity: mode.get() === MODE_AUTO ? ghostO.get() * 0.9 : 0,
    transform: [
      { translateX: ghostX.get() - GHOST / 2 },
      { translateY: ghostY.get() - GHOST / 2 },
      { scale: 1 - 0.16 * ghostS.get() },
    ],
  }));

  return (
    <View style={styles.screen} onTouchStart={onTouch} onTouchEnd={onTouch} onTouchCancel={onTouch}>
      <StatusBar style="dark" />
      <LinearGradient colors={BG} locations={[0, 0.5, 1]} style={StyleSheet.absoluteFill} />

      <View style={[styles.header, { top: headerTop, width: rowW }]}>
        <View style={styles.brandRow}>
          <BrandMark />
          <Text style={styles.brand}>Relay</Text>
          <Text style={styles.workspace}>Fernway</Text>
          <LinearGradient colors={['#C7D3E0', '#8EA2B8', '#64788F']} locations={[0, 0.5, 1]} style={styles.me}>
            <Text style={styles.meText}>AL</Text>
          </LinearGradient>
        </View>
        <View style={styles.titleRow}>
          <Text style={styles.title} accessibilityRole="header">Inbox</Text>
          <Text style={styles.unread}>9 unread</Text>
        </View>
        <View style={styles.filter}>
          <FeedFilter options={FILTERS} selected={filter} onSelect={setFilter} width={rowW} />
        </View>
        <Text style={styles.section}>Today</Text>
      </View>

      <Animated.View style={[styles.below, { top: belowTop, width: rowW }, belowStyle]}>
        <NoticeCard notice={STANDUP} />
        <View style={styles.gap12} />
        <StillStack group={CRIT} width={rowW} />
        <Text style={[styles.section, styles.sectionBelow]}>Yesterday</Text>
        <NoticeCard notice={YESTERDAY[0]} />
        <View style={styles.gap8} />
        <NoticeCard notice={YESTERDAY[1]} />
      </Animated.View>

      <View style={[styles.groupWrap, { top: groupTop }]} pointerEvents="box-none">
        <NotificationStack group={group} motion={m} width={rowW} reducedMotion={reduced} />
      </View>

      <BlurView intensity={70} tint="light" style={[styles.tabBar, { height: TAB_H + tabBottom, paddingBottom: tabBottom }]}>
        <View style={styles.tabLine} />
        {TABS.map((tab, i) => (
          <View key={tab.kind} style={styles.tab} accessibilityRole="tab" accessibilityState={{ selected: i === 0 }} accessible>
            <TabGlyph kind={tab.kind} color={i === 0 ? ACCENT : '#8A9099'} />
            <Text style={[styles.tabLabel, i === 0 && styles.tabOn]}>{tab.label}</Text>
            {i === 0 ? (
              <View style={styles.tabBadge}>
                <Text style={styles.tabBadgeText}>9</Text>
              </View>
            ) : null}
          </View>
        ))}
      </BlurView>

      <Animated.View style={[styles.ghost, ghostStyle]} pointerEvents="none" />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: BG[1], overflow: 'hidden' },
  header: { position: 'absolute', left: SIDE },
  brandRow: { height: BRAND_H, flexDirection: 'row', alignItems: 'center' },
  brand: { marginLeft: 7, fontSize: 16, fontWeight: '700', color: INK },
  workspace: { marginLeft: 8, fontSize: 15, color: MUTE },
  me: { marginLeft: 'auto', width: 28, height: 28, borderRadius: 14, alignItems: 'center', justifyContent: 'center' },
  meText: { fontSize: 11, fontWeight: '700', color: '#FFFFFF' },
  titleRow: { marginTop: 4, height: TITLE_H, flexDirection: 'row', alignItems: 'baseline' },
  title: { fontSize: 34, lineHeight: TITLE_H, fontWeight: '700', color: INK, letterSpacing: 0.2 },
  unread: { marginLeft: 'auto', fontSize: 15, fontWeight: '500', color: MUTE, fontVariant: ['tabular-nums'] },
  filter: { marginTop: 12, height: FILTER_H, justifyContent: 'center' },
  section: { marginTop: 20, height: LABEL_H, fontSize: 15, lineHeight: LABEL_H, fontWeight: '600', color: MUTE, paddingLeft: 4 },
  sectionBelow: { marginTop: 22, marginBottom: 8 },
  groupWrap: { position: 'absolute', left: SIDE },
  below: { position: 'absolute', left: SIDE },
  gap12: { height: 12 },
  gap8: { height: 8 },
  still: { ...cardSurface, position: 'absolute', left: 0, top: 0 },
  stillTint: { flex: 1, borderRadius: 20, borderCurve: 'continuous', backgroundColor: '#C9CFD7' },
  tabBar: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    flexDirection: 'row',
    backgroundColor: 'rgba(244,245,247,0.72)',
  },
  tabLine: { position: 'absolute', left: 0, right: 0, top: 0, height: StyleSheet.hairlineWidth, backgroundColor: 'rgba(27,31,36,0.16)' },
  tab: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingTop: 6 },
  tabLabel: { marginTop: 2, fontSize: 10.5, fontWeight: '500', color: '#8A9099' },
  tabOn: { color: ACCENT, fontWeight: '600' },
  tabBadge: {
    position: 'absolute',
    top: 3,
    left: '50%',
    marginLeft: 6,
    minWidth: 17,
    height: 17,
    borderRadius: 8.5,
    paddingHorizontal: 4,
    backgroundColor: '#E2484D',
    alignItems: 'center',
    justifyContent: 'center',
  },
  tabBadgeText: { fontSize: 11, fontWeight: '700', color: '#FFFFFF', fontVariant: ['tabular-nums'] },
  ghost: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: GHOST,
    height: GHOST,
    borderRadius: GHOST / 2,
    backgroundColor: 'rgba(110,118,130,0.32)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.75)',
  },
});
