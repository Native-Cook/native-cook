import { FlashList } from '@shopify/flash-list';
import { BlurView } from 'expo-blur';
import { LinearGradient } from 'expo-linear-gradient';
import * as Haptics from 'expo-haptics';
import { type ReactElement, useMemo } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  interpolateColor,
  scrollTo,
  useAnimatedReaction,
  useAnimatedRef,
  useAnimatedScrollHandler,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { BG, HAIR, INK, LBL_FS, LBL_LH, LBL_Y, MUTE, RAIL_W, SIDE, SUB_LH, SUB_Y, TOT_LH, TOT_Y, ledgerKey, ledgerType, renderLedgerItem } from './LedgerRows';
import { type Item, type Section, buildItems, daySpent, formatMoney } from './ledgerData';
import {
  FOOTER_H,
  HEADER_H,
  IDLE,
  LOOP,
  PILL_H,
  PILL_TOP,
  PILL_W,
  RETURN_LEN,
  buildGeometry,
  clamp01,
  easeInOutCubic,
  ghostAt,
  mix,
  monthAt,
  pillAmount,
  pushOffset,
  runningAt,
  scrollAt,
  sectionAt,
  wheelAt,
} from './stickyMotion';

// One FlashList v2, wrapped once at module scope so Reanimated can read and drive its scroll on the UI thread.
const AnimatedFlashList = Animated.createAnimatedComponent(FlashList) as unknown as typeof FlashList<Item>;

const MODE_AUTO = 0;
const MODE_TOUCH = 1;
const MODE_RETURN = 2;

/** Pill: label scale, odometer geometry. */
const PILL_LABEL_SCALE = 0.84;
const LBL_W = 200;
const DIGIT_W = 9.6;
const DIGIT_H = 20;
const DOT_W = 5;
const DOLLAR_W = 9.5;
const PILL_FS = 15;
/** Month index. */
const MONTH_ITEM_H = 26;
/** The full bar fades out only once the in-list header is (almost) gone: 46 → 64 pt after sticking. */
const BAR_FADE_FROM = 46;
const BAR_FADE_TO = 64;
const BUBBLE_LH = 26;

function tick() {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export type StickySectionListProps = {
  sections: Section[];
  /** Short month names for the index, in section order (Section.month indexes into it). */
  months: readonly string[];
  /** Full month names for the bubble shown while dragging the index. */
  monthNames: readonly string[];
  /** Header above the first section; must be exactly `listTopHeight` tall. */
  listTop: ReactElement;
  listTopHeight: number;
  footer: ReactElement;
  width: number;
  autoPlay?: boolean;
  reducedMotion?: boolean;
  bottomInset?: number;
  /** Optional: receives the scroll offset (for a title bar above the list). */
  scrollY?: SharedValue<number>;
};

/** One odometer wheel: two strips of 0–9 so a digit can roll forward past 9. */
function Wheel({ run, place }: { run: SharedValue<number[]>; place: number }) {
  const style = useAnimatedStyle(() => {
    const r = run.get();
    return { transform: [{ translateY: -wheelAt(r[0], r[1], r[2], place) * DIGIT_H }] };
  });
  const digits = useMemo(() => Array.from({ length: 20 }, (_, i) => String(i % 10)), []);
  return (
    <View style={styles.wheel}>
      <Animated.View style={style}>
        {digits.map((d, i) => (
          <View key={i} style={styles.digitBox}>
            <Text style={styles.digit}>{d}</Text>
          </View>
        ))}
      </Animated.View>
    </View>
  );
}

/** A month in the index: ink capsule behind the current one, its text turns paper. */
function MonthMark({ label, i, month }: { label: string; i: number; month: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const d = clamp01(Math.abs(month.get() - i));
    return { color: interpolateColor(d, [0, 1], [BG, MUTE]) };
  });
  return (
    <View style={styles.monthItem}>
      <Animated.Text style={[styles.monthText, style]}>{label}</Animated.Text>
    </View>
  );
}

/**
 * The reusable piece: a sectioned FlashList whose headers stick, get pushed by the next one, and once stuck shrink
 * into a frosted pill with a rolling running total; a month index on the right follows and can be dragged to jump.
 * All of it reads one scroll value on the UI thread; rows never receive it, so they never re-render while scrolling.
 */
export function StickySectionList({
  sections,
  months,
  monthNames,
  listTop,
  listTopHeight,
  footer,
  width: W,
  autoPlay = true,
  reducedMotion = false,
  bottomInset = 0,
  scrollY: externalScroll,
}: StickySectionListProps) {
  const items = useMemo(() => buildItems(sections), [sections]);
  const geo = useMemo(() => buildGeometry(sections, months.length, listTopHeight), [sections, months.length, listTopHeight]);
  const labels = useMemo(() => sections.map((s) => s.label), [sections]);
  const subs = useMemo(() => sections.map((s) => (s.txns.length === 1 ? '1 payment' : `${s.txns.length} payments`)), [sections]);
  const totals = useMemo(() => sections.map((s) => formatMoney(-daySpent(s), true)), [sections]);

  const listRef = useAnimatedRef<any>();
  const ownScroll = useSharedValue(0);
  const scrollY = externalScroll ?? ownScroll;
  const viewH = useSharedValue(600);
  const auto = autoPlay && !reducedMotion;

  const now = useSharedValue(0);
  const clock = useSharedValue(0);
  const mode = useSharedValue(MODE_AUTO);
  const lastTouch = useSharedValue(0);
  const dragging = useSharedValue(0);
  const returnFrom = useSharedValue(0);
  const returnAt = useSharedValue(0);
  const railMonth = useSharedValue(-1);
  const railActive = useSharedValue(0);
  const jumpSeq = useSharedValue(0);

  // ---- clock: the loop scripts the scroll; touch takes over; 2 s idle eases back to the top and restarts ----
  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 50) / 1000;
    const n = now.get() + dt;
    now.set(n);
    const m = mode.get();
    if (m === MODE_AUTO) {
      if (!auto) return;
      const t = (clock.get() + dt) % LOOP;
      clock.set(t);
      scrollTo(listRef, 0, scrollAt(t, geo), false);
      return;
    }
    if (m === MODE_TOUCH) {
      if (auto && dragging.get() === 0 && n - lastTouch.get() > IDLE) {
        returnFrom.set(scrollY.get());
        returnAt.set(n);
        mode.set(MODE_RETURN);
      }
      return;
    }
    const u = Math.min(1, (n - returnAt.get()) / RETURN_LEN);
    scrollTo(listRef, 0, returnFrom.get() * (1 - easeInOutCubic(u)), false);
    if (u >= 1) {
      clock.set(0);
      mode.set(MODE_AUTO);
    }
  });

  function takeOver() {
    'worklet';
    lastTouch.set(now.get());
    mode.set(MODE_TOUCH);
  }

  const onScroll = useAnimatedScrollHandler({
    onScroll: (e) => {
      scrollY.set(e.contentOffset.y);
      // Momentum after a fling is the finger's too: the idle return waits for it to stop (onMomentumEnd is not
      // reliable on every list), so any movement while the person has the list counts as activity.
      if (mode.get() === MODE_TOUCH) lastTouch.set(now.get());
    },
    onBeginDrag: () => {
      dragging.set(1);
      takeOver();
    },
    onEndDrag: () => {
      dragging.set(0);
      lastTouch.set(now.get());
    },
    onMomentumBegin: () => {
      lastTouch.set(now.get());
    },
    onMomentumEnd: () => {
      lastTouch.set(now.get());
    },
  });

  // ---- one derived state for every sticky piece: [k, pill, push y, month] ----
  const stick = useDerivedValue(() => {
    const s = scrollY.get();
    const k = sectionAt(s, geo);
    const p = reducedMotion ? (k >= 0 && s > geo.tops[k] + 4 ? 1 : 0) : pillAmount(s, k, geo);
    // The opaque bar stays until the in-list header has scrolled out from under it, so the two never show at once.
    const bar = k < 0 ? 1 : 1 - clamp01((s - geo.tops[k] - BAR_FADE_FROM) / (BAR_FADE_TO - BAR_FADE_FROM));
    return [k, p, pushOffset(s, k, p, geo), monthAt(s, geo), reducedMotion ? 1 - p : bar];
  });
  const run = useDerivedValue(() => {
    const s = scrollY.get();
    return runningAt(s, sectionAt(s, geo), geo);
  });
  const month = useDerivedValue(() => (railActive.get() > 0.5 && railMonth.get() >= 0 ? railMonth.get() : stick.get()[3]));

  // ---- month index: drag or tap to jump ----
  function railAt(y: number) {
    'worklet';
    const i = Math.max(0, Math.min(months.length - 1, Math.floor(y / MONTH_ITEM_H)));
    if (i !== railMonth.get()) {
      railMonth.set(i);
      jumpSeq.set(jumpSeq.get() + 1);
      scheduleOnRN(tick);
    }
  }
  // The jump itself: each new month under the finger moves the list there at once (UI thread).
  useAnimatedReaction(
    () => jumpSeq.get(),
    (seq, prev) => {
      const i = railMonth.get();
      if (seq === prev || prev === null || i < 0) return;
      const max = Math.max(0, geo.contentH - viewH.get());
      scrollTo(listRef, 0, Math.min(geo.monthTops[i], max), false);
    },
  );
  const rail = Gesture.Pan()
    .minDistance(0)
    .hitSlop({ left: 12, right: 4 })
    .onBegin((e) => {
      takeOver();
      dragging.set(1);
      railMonth.set(-1);
      railActive.set(withTiming(1, { duration: 140 }));
      railAt(e.y - 8);
    })
    .onUpdate((e) => {
      lastTouch.set(now.get());
      railAt(e.y - 8);
    })
    .onFinalize(() => {
      dragging.set(0);
      lastTouch.set(now.get());
      railActive.set(withTiming(0, { duration: 260 }));
    });

  // Haptic when the stuck header changes hands under a finger (not in autoplay).
  useAnimatedReaction(
    () => (mode.get() === MODE_TOUCH && dragging.get() === 1 && railActive.get() < 0.5 ? stick.get()[0] : -2),
    (k, prev) => {
      if (prev !== null && prev !== -2 && k !== -2 && k !== prev && k >= 0) scheduleOnRN(tick);
    },
  );

  // ---- styles ----
  const overlayStyle = useAnimatedStyle(() => {
    const st = stick.get();
    return { opacity: st[0] < 0 ? 0 : 1, transform: [{ translateY: st[2] }] };
  });
  const barStyle = useAnimatedStyle(() => ({ opacity: stick.get()[4] }));
  const fadeStyle = useAnimatedStyle(() => ({ opacity: 1 - stick.get()[4] }));
  const pillStyle = useAnimatedStyle(() => {
    const p = stick.get()[1];
    return {
      opacity: easeInOutCubic(clamp01(p * 1.25)),
      transform: [{ scaleX: mix((W - 8) / PILL_W, 1, p) }, { scaleY: mix(HEADER_H / PILL_H, 1, p) }],
    };
  });
  const pillLeft = (W - PILL_W) / 2;
  const labelStyle = useAnimatedStyle(() => {
    const st = stick.get();
    const p = st[1];
    const sc = mix(1, PILL_LABEL_SCALE, p);
    const xT = mix(SIDE, pillLeft + 16, p);
    const yT = mix(LBL_Y, PILL_TOP + (PILL_H - LBL_LH * PILL_LABEL_SCALE) / 2, p);
    return {
      transform: [{ translateX: xT - SIDE - (LBL_W * (1 - sc)) / 2 }, { translateY: yT - LBL_Y - (LBL_LH * (1 - sc)) / 2 }, { scale: sc }],
    };
  });
  const columnStyle = useAnimatedStyle(() => ({ transform: [{ translateY: -Math.max(0, stick.get()[0]) * LBL_LH }] }));
  // The day's subtitle and total leave early (first ~60 % of the shrink) so they never show around the arriving pill.
  const subColumnStyle = useAnimatedStyle(() => {
    const st = stick.get();
    return { opacity: 1 - clamp01(st[1] * 1.6), transform: [{ translateY: -Math.max(0, st[0]) * SUB_LH }] };
  });
  const totColumnStyle = useAnimatedStyle(() => {
    const st = stick.get();
    return { opacity: 1 - clamp01(st[1] * 1.6), transform: [{ translateY: -Math.max(0, st[0]) * TOT_LH }] };
  });
  const odoStyle = useAnimatedStyle(() => {
    const p = stick.get()[1];
    return { opacity: easeInOutCubic(clamp01((p - 0.35) / 0.65)), transform: [{ translateY: (1 - p) * 6 }] };
  });
  // Leading digits appear only when the total needs them; the dollar sign slides next to the first one.
  const hundredsStyle = useAnimatedStyle(() => {
    const r = run.get();
    return { opacity: clamp01((mix(r[0], r[1], r[2]) - 9000) / 1000) };
  });
  const tensStyle = useAnimatedStyle(() => {
    const r = run.get();
    return { opacity: clamp01((mix(r[0], r[1], r[2]) - 900) / 100) };
  });
  const dollarStyle = useAnimatedStyle(() => {
    const r = run.get();
    const v = mix(r[0], r[1], r[2]);
    const hidden = 2 - clamp01((v - 9000) / 1000) - clamp01((v - 900) / 100);
    return { transform: [{ translateX: hidden * DIGIT_W }] };
  });

  const railH = months.length * MONTH_ITEM_H;
  const markStyle = useAnimatedStyle(() => ({ transform: [{ translateY: month.get() * MONTH_ITEM_H }] }));
  const bubbleStyle = useAnimatedStyle(() => {
    const a = railActive.get();
    const i = Math.max(0, railMonth.get());
    return { opacity: a, transform: [{ translateY: i * MONTH_ITEM_H }, { translateX: (1 - a) * 10 }, { scale: mix(0.92, 1, a) }] };
  });
  const bubbleColumnStyle = useAnimatedStyle(() => ({ transform: [{ translateY: -Math.max(0, railMonth.get()) * BUBBLE_LH }] }));

  const ghostStyle = useAnimatedStyle(() => {
    const g = mode.get() === MODE_AUTO && auto ? ghostAt(clock.get()) : [0, 0.78];
    return { opacity: g[0] * 0.85, transform: [{ translateY: g[1] * viewH.get() - 22 }] };
  });

  const onLayout = (e: LayoutChangeEvent) => viewH.set(e.nativeEvent.layout.height);

  // Odometer x positions, right-aligned inside the pill.
  const odoRight = pillLeft + PILL_W - 14;
  const xCent = odoRight - DIGIT_W;
  const xDime = xCent - DIGIT_W;
  const xDot = xDime - DOT_W;
  const xOnes = xDot - DIGIT_W;
  const xTens = xOnes - DIGIT_W;
  const xHund = xTens - DIGIT_W;
  const xDollar = xHund - DOLLAR_W;
  const odoTop = PILL_TOP + (PILL_H - DIGIT_H) / 2;

  return (
    <View style={styles.root} onLayout={onLayout}>
      <AnimatedFlashList
        ref={listRef}
        data={items}
        renderItem={renderLedgerItem}
        keyExtractor={ledgerKey}
        getItemType={ledgerType}
        ListHeaderComponent={<View style={{ height: listTopHeight }}>{listTop}</View>}
        ListFooterComponent={<View style={{ height: FOOTER_H + bottomInset }}>{footer}</View>}
        onScroll={onScroll}
        scrollEventThrottle={16}
        showsVerticalScrollIndicator={false}
      />

      {/* The sticky header: one copy for every section, its labels rolled to the current one at the hand-over. */}
      {/* Hidden from accessibility: it repeats the in-list header under it, and its odometer is a column of digits. */}
      <Animated.View
        pointerEvents="none"
        style={[styles.overlay, { width: W }, overlayStyle]}
        accessibilityElementsHidden
        importantForAccessibility="no-hide-descendants"
      >
        <Animated.View style={[styles.bar, barStyle]}>
          <View style={[styles.clip, { left: SIDE, top: SUB_Y, height: SUB_LH, width: 160 }]}>
            <Animated.View style={subColumnStyle}>
              {subs.map((s, i) => (
                <Text key={i} style={styles.sub}>
                  {s}
                </Text>
              ))}
            </Animated.View>
          </View>
          <View style={[styles.clip, { right: RAIL_W + 4, top: TOT_Y, height: TOT_LH, width: 110 }]}>
            <Animated.View style={totColumnStyle}>
              {totals.map((s, i) => (
                <Text key={i} style={styles.total}>
                  {s}
                </Text>
              ))}
            </Animated.View>
          </View>
          <View style={styles.barHair} />
        </Animated.View>

        {/* Once the bar is gone, a short paper fade above and around the pill keeps the rows from reading through it. */}
        <Animated.View style={[styles.fadeBand, { width: W }, fadeStyle]}>
          <LinearGradient colors={[BG, BG, 'rgba(243,242,238,0)']} locations={[0, 0.8, 1]} style={StyleSheet.absoluteFill} />
        </Animated.View>

        <Animated.View style={[styles.pillShadowWide, { left: pillLeft, top: PILL_TOP }, pillStyle]}>
          <View style={styles.pillShadowTight}>
            <BlurView intensity={28} tint="light" style={StyleSheet.absoluteFill} />
            <View style={styles.pillTint} />
            <View style={styles.pillEdge} />
          </View>
        </Animated.View>

        <Animated.View style={[styles.clip, { left: SIDE, top: LBL_Y, height: LBL_LH, width: LBL_W }, labelStyle]}>
          <Animated.View style={columnStyle}>
            {labels.map((s, i) => (
              <Text key={i} style={styles.label} numberOfLines={1}>
                {s}
              </Text>
            ))}
          </Animated.View>
        </Animated.View>

        <Animated.View style={[StyleSheet.absoluteFill, odoStyle]}>
          <Animated.Text style={[styles.odoText, { left: xDollar, top: odoTop }, dollarStyle]}>$</Animated.Text>
          <Animated.View style={[styles.wheelAt, { left: xHund, top: odoTop }, hundredsStyle]}>
            <Wheel run={run} place={10000} />
          </Animated.View>
          <Animated.View style={[styles.wheelAt, { left: xTens, top: odoTop }, tensStyle]}>
            <Wheel run={run} place={1000} />
          </Animated.View>
          <View style={[styles.wheelAt, { left: xOnes, top: odoTop }]}>
            <Wheel run={run} place={100} />
          </View>
          <Text style={[styles.odoText, { left: xDot, top: odoTop, width: DOT_W }]}>.</Text>
          <View style={[styles.wheelAt, { left: xDime, top: odoTop }]}>
            <Wheel run={run} place={10} />
          </View>
          <View style={[styles.wheelAt, { left: xCent, top: odoTop }]}>
            <Wheel run={run} place={1} />
          </View>
        </Animated.View>
      </Animated.View>

      {/* Month index: follows the scroll; drag or tap it to jump. */}
      <GestureDetector gesture={rail}>
        <View style={[styles.rail, { height: railH + 16, marginTop: -(railH + 16) / 2 }]}>
          <Animated.View style={[styles.mark, markStyle]} />
          {months.map((m, i) => (
            <MonthMark key={m} label={m} i={i} month={month} />
          ))}
          <Animated.View
            pointerEvents="none"
            style={[styles.bubble, bubbleStyle]}
            accessibilityElementsHidden
            importantForAccessibility="no-hide-descendants"
          >
            <View style={styles.bubbleClip}>
              <Animated.View style={bubbleColumnStyle}>
                {monthNames.map((m) => (
                  <Text key={m} style={styles.bubbleText}>
                    {m}
                  </Text>
                ))}
              </Animated.View>
            </View>
          </Animated.View>
        </View>
      </GestureDetector>

      <Animated.View pointerEvents="none" style={[styles.ghost, { left: W * 0.5 - 22 }, ghostStyle]} />
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden' },
  overlay: { position: 'absolute', left: 0, top: 0, height: HEADER_H },
  bar: { ...StyleSheet.absoluteFill, backgroundColor: BG },
  barHair: { position: 'absolute', left: 0, right: 0, bottom: 0, height: StyleSheet.hairlineWidth, backgroundColor: HAIR },
  clip: { position: 'absolute', overflow: 'hidden' },
  label: { height: LBL_LH, fontSize: LBL_FS, lineHeight: LBL_LH, fontWeight: '700', color: INK, letterSpacing: -0.2 },
  sub: { height: SUB_LH, fontSize: 13, lineHeight: SUB_LH, color: MUTE, fontVariant: ['tabular-nums'] },
  total: { height: TOT_LH, fontSize: 15, lineHeight: TOT_LH, fontWeight: '600', color: MUTE, fontVariant: ['tabular-nums'], textAlign: 'right' },
  pillShadowWide: {
    position: 'absolute',
    width: PILL_W,
    height: PILL_H,
    borderRadius: PILL_H / 2,
    backgroundColor: 'rgba(243,242,238,0.3)',
    shadowColor: '#2a2218',
    shadowOpacity: 0.14,
    shadowRadius: 18,
    shadowOffset: { width: 0, height: 8 },
  },
  pillShadowTight: {
    flex: 1,
    borderRadius: PILL_H / 2,
    overflow: 'hidden',
    backgroundColor: 'rgba(243,242,238,0.35)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(40,34,26,0.14)',
    shadowColor: '#2a2218',
    shadowOpacity: 0.2,
    shadowRadius: 2,
    shadowOffset: { width: 0, height: 1 },
  },
  pillTint: { ...StyleSheet.absoluteFill, backgroundColor: 'rgba(250,249,246,0.74)' },
  fadeBand: { position: 'absolute', left: 0, top: 0, height: PILL_TOP + PILL_H + 6 },
  pillEdge: { position: 'absolute', left: 14, right: 14, top: 0, height: 1, backgroundColor: 'rgba(255,255,255,0.85)' },
  wheelAt: { position: 'absolute' },
  wheel: { width: DIGIT_W, height: DIGIT_H, overflow: 'hidden' },
  digitBox: { width: DIGIT_W, height: DIGIT_H, alignItems: 'center', justifyContent: 'center' },
  digit: { fontSize: PILL_FS, lineHeight: DIGIT_H, fontWeight: '600', color: INK, fontVariant: ['tabular-nums'] },
  odoText: { position: 'absolute', fontSize: PILL_FS, lineHeight: DIGIT_H, fontWeight: '600', color: INK, textAlign: 'center', width: DOLLAR_W },
  rail: { position: 'absolute', right: 4, top: '50%', width: RAIL_W - 6, paddingVertical: 8, alignItems: 'center' },
  mark: {
    position: 'absolute',
    top: 8,
    left: 2,
    right: 2,
    height: MONTH_ITEM_H,
    borderRadius: MONTH_ITEM_H / 2,
    backgroundColor: INK,
    shadowColor: '#2a2218',
    shadowOpacity: 0.18,
    shadowRadius: 6,
    shadowOffset: { width: 0, height: 3 },
  },
  monthItem: { height: MONTH_ITEM_H, alignSelf: 'stretch', alignItems: 'center', justifyContent: 'center' },
  monthText: { fontSize: 12, fontWeight: '600', fontVariant: ['tabular-nums'] },
  bubble: {
    position: 'absolute',
    top: 8 + (MONTH_ITEM_H - 44) / 2,
    right: RAIL_W + 4,
    height: 44,
    paddingHorizontal: 18,
    borderRadius: 22,
    backgroundColor: INK,
    justifyContent: 'center',
    shadowColor: '#2a2218',
    shadowOpacity: 0.2,
    shadowRadius: 16,
    shadowOffset: { width: 0, height: 8 },
  },
  bubbleClip: { height: BUBBLE_LH, overflow: 'hidden', minWidth: 96 },
  bubbleText: { height: BUBBLE_LH, fontSize: 19, lineHeight: BUBBLE_LH, fontWeight: '700', color: BG },
  ghost: {
    position: 'absolute',
    top: 0,
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: 'rgba(255,255,255,0.5)',
    borderWidth: 1,
    borderColor: 'rgba(40,34,26,0.16)',
    shadowColor: '#2a2218',
    shadowOpacity: 0.16,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 4 },
  },
});
