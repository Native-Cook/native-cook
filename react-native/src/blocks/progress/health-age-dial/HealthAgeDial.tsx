import { Canvas, Circle, Group, Path, RadialGradient, Skia, SweepGradient, vec } from '@shopify/react-native-skia';
import { BlurTargetView, BlurView } from 'expo-blur';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useRef } from 'react';
import { Platform, Pressable, StyleSheet, Text, TextInput, View, useWindowDimensions, type TextStyle } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  scrollTo,
  useAnimatedProps,
  useAnimatedRef,
  useAnimatedScrollHandler,
  useAnimatedStyle,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { DIAL_SIZE, DialRing, NUMBER_LINE, YearsOdometer } from './Dial';
import { BedGlyph, GridGlyph, HeartGlyph, InfoGlyph, OVERVIEW_FILLS, PetalMark, PulseGlyph } from './Glyphs';
import { CHART_H, CHART_W, HistoryChart } from './HistoryChart';
import {
  FLIGHT,
  LAND,
  LOOP,
  PRESS,
  RESUME_AFTER,
  RETURN_DURATION,
  REVEAL,
  SPRINGS,
  clamp01,
  easeInOut,
  easeOut,
  landBump,
  landingTime,
  lerp,
  loopFrame,
  pop,
  seg,
  smoothstep,
  springDisplacement,
  trailFor,
} from './motion';
import {
  AGES,
  PIN_SAFE_TOP,
  PIN_WIDTH,
  REST_YEARS,
  baseline,
  colors,
  copy,
  layout,
  months,
  radius,
  ringStops,
  space,
  type,
} from './theme';

export { LOOP };

const D = layout.dial;
const OUTER = D.r + D.stroke / 2;
const CHIP = layout.chip;
const CH = layout.chart;
/** Scroll offset at which the dial's own position meets the chip row: it lands there. */
const Y_LAND = D.cy - (CHIP.top + CHIP.height / 2);
const RING_RATIO = CHIP.ring / (2 * OUTER);
const LAND_T = landingTime(Y_LAND, layout.historyOffset);
const SF_ASCENT = 0.953;
const SF_SPAN = 0.712; // ascent − descent, in em
const TAB_ICON = layout.tab.icon;
/** Reduced Motion: the dial scrolls with the page and is gone before it reaches the chips (pt of scroll). */
const REDUCED_FADE = [4, 30] as const;
const AUTO = 0;
const MANUAL = 1;
const RETURN = 2;

/** Top of a Text whose first baseline must sit at `base` (SF Pro on iOS). */
function topFor(base: number, size: number, lineHeight?: number): number {
  return lineHeight ? base - (lineHeight + SF_SPAN * size) / 2 : base - SF_ASCENT * size;
}

function nowSeconds(): number {
  'worklet';
  return performance.now() / 1000;
}

function setIf(sv: SharedValue<number>, v: number) {
  'worklet';
  if (Math.abs(sv.get() - v) > 1e-4) sv.set(v);
}

function selectionHaptic() {
  if (Platform.OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

function lightHaptic() {
  if (Platform.OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

const AnimatedTextInput = Animated.createAnimatedComponent(TextInput);
const AnimatedPressable = Animated.createAnimatedComponent(Pressable);
const PRESS_EASE = Easing.bezier(0.23, 1, 0.32, 1);

function txt(spec: { size: number; weight: string; color?: string; tracking?: number }, color?: string): TextStyle {
  return {
    fontSize: spec.size,
    fontWeight: spec.weight as TextStyle['fontWeight'],
    color: color ?? spec.color,
    letterSpacing: spec.tracking ?? 0,
  };
}

/** The Health Age chip's ring: a small copy of the dial that the big one flies into. */
function MiniDial({ size }: { size: number }) {
  const c = size / 2;
  const r = (CHIP.ring - 6) / 2;
  const { ring, sweep } = useMemo(() => {
    const p = Skia.Path.Make();
    p.addArc({ x: c - r, y: c - r, width: 2 * r, height: 2 * r }, D.start - 90, D.end - D.start);
    const stops = ringStops.map(([a, col]) => ({ p: ((((a - 90) % 360) + 360) % 360) / 360, col }));
    stops.sort((s, t) => s.p - t.p);
    return { ring: p, sweep: { colors: stops.map((s) => s.col), positions: stops.map((s) => s.p) } };
  }, [c, r]);
  const a = ((REST_YEARS * D.degPerYear - 90) * Math.PI) / 180;
  return (
    <Canvas style={{ width: size, height: size }} pointerEvents="none">
      <Group opacity={0.78}>
        <Path path={ring} style="stroke" strokeWidth={6} strokeCap="round">
          <SweepGradient c={vec(c, c)} colors={sweep.colors} positions={sweep.positions} />
        </Path>
      </Group>
      <Circle cx={c + r * Math.cos(a)} cy={c + r * Math.sin(a)} r={3.3} color="#4A8FDB" />
    </Canvas>
  );
}

function Chip({
  left,
  top,
  width,
  selected,
  blurTarget,
  icon,
  title,
  value,
  onPress,
}: {
  left: number;
  top: number;
  width: number;
  selected: boolean;
  blurTarget: React.RefObject<View | null>;
  icon: React.ReactNode;
  title: string;
  value: string;
  onPress?: () => void;
}) {
  const pressed = useSharedValue(0);
  const pressStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - (1 - PRESS.scale) * pressed.get() }] }));
  return (
    <AnimatedPressable
      onPress={onPress}
      disabled={!onPress}
      onPressIn={() => pressed.set(withTiming(1, { duration: PRESS.pressIn, easing: PRESS_EASE }))}
      onPressOut={() => pressed.set(withTiming(0, { duration: PRESS.release, easing: PRESS_EASE }))}
      style={[styles.chip, { left, top, width, height: CHIP.height, borderRadius: radius.chip }, selected ? styles.chipSelected : styles.chipPlain, pressStyle]}
      accessible
      accessibilityRole={onPress ? 'button' : undefined}
      accessibilityLabel={`${title}, ${value}`}
      accessibilityHint={onPress ? 'Scrolls back to the dial' : undefined}
      accessibilityState={{ selected }}
    >
      <BlurView
        pointerEvents="none"
        blurTarget={blurTarget}
        blurMethod="dimezisBlurViewSdk31Plus"
        intensity={selected ? 30 : 24}
        tint="light"
        style={[StyleSheet.absoluteFill, { borderRadius: radius.chip, overflow: 'hidden' }]}
      />
      <View
        pointerEvents="none"
        style={[StyleSheet.absoluteFill, { borderRadius: radius.chip, backgroundColor: selected ? colors.chipSelected : colors.chipFill }]}
      />
      <View style={[styles.chipIcon, { left: space.chipPadLeading - 1, width: CHIP.icon + 2, height: CHIP.height }]}>{icon}</View>
      <Text style={[styles.abs, txt(type.chipTitle), { left: space.chipPadLeading + CHIP.icon + space.chipIconGap, top: topFor(baseline.chipTitle, type.chipTitle.size) - CHIP.top }]} allowFontScaling={false}>
        {title}
      </Text>
      <Text style={[styles.abs, txt(type.chipValue), { left: space.chipPadLeading + CHIP.icon + space.chipIconGap, top: topFor(baseline.chipValue, type.chipValue.size) - CHIP.top }]} allowFontScaling={false}>
        {value}
      </Text>
    </AnimatedPressable>
  );
}

function FactorCard({
  index,
  top,
  width,
  count,
  badge,
}: {
  index: 0 | 1;
  top: number;
  width: number;
  count: SharedValue<number>;
  badge: SharedValue<number>;
}) {
  const factor = copy.factors[index];
  const tint = index === 0 ? colors.sleepTint : colors.hrvTint;
  const titleColor = index === 0 ? colors.sleep : colors.hrv;
  const valueProps = useAnimatedProps(() => {
    const v = factor.years * count.get();
    return { text: `+${v.toFixed(1)} years`, defaultValue: `+${v.toFixed(1)} years` } as never;
  });
  const badgeStyle = useAnimatedStyle(() => {
    const p = pop(badge.get());
    return { opacity: p.opacity, transform: [{ scale: p.scale }] };
  });
  const ic = layout.cardIcon;
  return (
    <View
      style={[styles.card, { left: space.cardInset, top, width, height: layout.cards[index].height, borderRadius: radius.card }]}
      accessible
      accessibilityLabel={`${factor.title}, plus ${factor.years} years. ${factor.body}`}
    >
      <View style={[styles.cardIcon, { left: space.cardIconLeading, top: ic.cy - ic.d / 2, width: ic.d, height: ic.d, borderRadius: ic.d / 2, backgroundColor: tint }]}>
        <Animated.View style={badgeStyle}>
          {index === 0 ? <BedGlyph size={ic.glyph} color={colors.sleepGlyph} /> : <PulseGlyph size={ic.glyph} color={colors.hrvGlyph} />}
        </Animated.View>
      </View>
      <Text style={[styles.abs, txt(type.cardTitle, titleColor), { left: space.cardTextLeading, top: topFor(layout.cardTitleBaseline, type.cardTitle.size) }]} allowFontScaling={false}>
        {factor.title}
      </Text>
      <AnimatedTextInput
        editable={false}
        pointerEvents="none"
        underlineColorAndroid="transparent"
        defaultValue={`+${factor.years.toFixed(1)} years`}
        animatedProps={valueProps}
        allowFontScaling={false}
        style={[styles.abs, styles.valueInput, txt(type.cardValue), { left: space.cardTextLeading, top: topFor(layout.cardValueBaseline, type.cardValue.size), width: width - space.cardTextLeading - 8 }]}
      />
      <Text
        style={[styles.abs, txt(type.cardBody), { left: space.cardTextLeading, right: 12, top: topFor(layout.cardBodyBaseline, type.cardBody.size, type.cardBody.line), lineHeight: type.cardBody.line }]}
        allowFontScaling={false}
      >
        {factor.body}
      </Text>
    </View>
  );
}

function TabBar({ width, blurTarget }: { width: number; blurTarget: React.RefObject<View | null> }) {
  const tb = layout.tab;
  const tabW = tb.item;
  const tabInset = (tb.width - 3 * tabW) / 2;
  const needsOpaque = Platform.OS === 'android' && Number(Platform.Version) < 31;
  const icons = [
    <HeartGlyph key="h" size={TAB_ICON.heart} color={colors.tabText} />,
    <PetalMark key="l" size={TAB_ICON.petal} shape="tab" fills={Array(8).fill(colors.tabBlue)} />,
    <GridGlyph key="g" size={TAB_ICON.grid} color={colors.tabText} />,
  ];
  return (
    <View
      pointerEvents="none"
      accessibilityRole="tablist"
      style={[styles.tabBar, { left: (width - tb.width) / 2, bottom: space.tabBottom, width: tb.width, height: tb.height, borderRadius: radius.tab, backgroundColor: needsOpaque ? 'rgba(250,250,252,0.94)' : 'rgba(255,255,255,0.22)' }]}
    >
      <BlurView pointerEvents="none" blurTarget={blurTarget} blurMethod="dimezisBlurViewSdk31Plus" intensity={34} tint="light" style={[StyleSheet.absoluteFill, { borderRadius: radius.tab, overflow: 'hidden' }]} />
      <View style={[styles.tabPill, { left: tabInset + tabW + (tabW - tb.pill.width) / 2, top: (tb.height - tb.pill.height) / 2, width: tb.pill.width, height: tb.pill.height, borderRadius: radius.tabPill }]} />
      {copy.tabs.map((label, i) => {
        const selected = i === 1;
        return (
          <View key={label} accessible accessibilityRole="tab" accessibilityState={{ selected }} accessibilityLabel={label} style={[styles.tab, { left: tabInset + i * tabW, width: tabW, height: tb.height }]}>
            {icons[i]}
            <Text style={[txt(type.tab, selected ? colors.blue : colors.tabText), { marginTop: TAB_ICON.labelGap }]} allowFontScaling={false}>
              {label}
            </Text>
          </View>
        );
      })}
    </View>
  );
}

export function HealthAgeDial({ autoPlay = false }: { autoPlay?: boolean }) {
  const insets = useSafeAreaInsets();
  const { width: W, height: H } = useWindowDimensions();
  const reduced = useReducedMotion();
  const dy = insets.top - PIN_SAFE_TOP;
  const center = W / 2;
  const blurTarget = useRef<View>(null);

  // Horizontal positions: centred things keep their offset from the centre, the rest their inset.
  const dialCx = center + (D.cx - PIN_WIDTH / 2);
  const chipAvail = W - space.chipInset - space.chipInsetTrailing - space.chipGap;
  const chipW = chipAvail * CHIP.leftShare;
  const chipW2 = chipAvail - chipW;
  const chipIconCx = space.chipInset + chipW + space.chipGap + space.chipPadLeading + CHIP.icon / 2;
  const chipCy = dy + CHIP.top + CHIP.height / 2;
  const cardW = W - 2 * space.cardInset;
  const contentHeight = dy + layout.contentBottom + 112;

  const scrollRef = useAnimatedRef<Animated.ScrollView>();
  const offset = useSharedValue(0);
  const mode = useSharedValue(autoPlay && !reduced ? AUTO : MANUAL);
  /** Set on the UI runtime's first frame (its clock is not the JS runtime's). */
  const loopStart = useSharedValue(-1);
  const lastTouch = useSharedValue(-100);
  const scrolling = useSharedValue(0);
  const dragging = useSharedValue(0);
  const years = useSharedValue<number>(REST_YEARS);
  const trail = useSharedValue(0);
  const lift = useSharedValue(0);
  const releaseAt = useSharedValue(-1);
  const releaseFrom = useSharedValue<number>(REST_YEARS);
  const releaseVel = useSharedValue(0);
  const returnAt = useSharedValue(-1);
  const returnFrom = useSharedValue(0);
  const returnResume = useSharedValue(1);
  const prevYears = useSharedValue<number>(REST_YEARS);
  const prevNow = useSharedValue(0);
  const revealAt = useSharedValue(-1);
  const fadeAt = useSharedValue(-1);
  const revealOpacity = useSharedValue(1);
  const landAt = useSharedValue(-100);
  const landed = useSharedValue(0);
  const lastYearStep = useSharedValue(Math.floor(REST_YEARS));
  // Derived per frame from the times above (set only when they change).
  const lineProgress = useSharedValue(reduced ? 1 : 0);
  const guides = useSharedValue(reduced ? 1 : 0);
  const count1 = useSharedValue(reduced ? 1 : 0);
  const count2 = useSharedValue(reduced ? 1 : 0);
  const badge1 = useSharedValue(reduced ? 1 : 0);
  const badge2 = useSharedValue(reduced ? 1 : 0);
  const bump = useSharedValue(0);
  const reducedSV = useSharedValue(reduced ? 1 : 0);
  const autoSV = useSharedValue(autoPlay ? 1 : 0);
  useEffect(() => {
    reducedSV.set(reduced ? 1 : 0);
    autoSV.set(autoPlay ? 1 : 0);
  }, [autoPlay, autoSV, reduced, reducedSV]);

  /** A touch anywhere: stop the loop; let a moving dot settle from where it is. */
  const markTouch = useCallback(() => {
    'worklet';
    const now = nowSeconds();
    lastTouch.set(now);
    if (mode.get() !== MANUAL) {
      mode.set(MANUAL);
      returnAt.set(-1);
      if (dragging.get() === 0 && Math.abs(years.get() - REST_YEARS) > 1e-3) {
        const dt = Math.max(1 / 240, now - prevNow.get());
        releaseFrom.set(years.get());
        releaseVel.set((years.get() - prevYears.get()) / dt);
        releaseAt.set(now);
      }
    }
  }, [dragging, lastTouch, mode, prevNow, prevYears, releaseAt, releaseFrom, releaseVel, returnAt, years]);

  const onRootTouch = useCallback(() => scheduleOnUI(markTouch), [markTouch]);

  /** The Health Age chip takes you back to the dial (it flies out of the chip on the way). */
  const glideToDial = useCallback(() => {
    scheduleOnUI(() => {
      'worklet';
      markTouch();
      if (offset.get() <= 1) return;
      mode.set(RETURN);
      returnResume.set(0);
      returnAt.set(nowSeconds());
      returnFrom.set(offset.get());
    });
  }, [markTouch, mode, offset, returnAt, returnFrom, returnResume]);

  const scrollHandler = useAnimatedScrollHandler({
    onScroll: (e) => {
      offset.set(e.contentOffset.y);
      if (scrolling.get() === 1) lastTouch.set(nowSeconds());
    },
    onBeginDrag: () => {
      scrolling.set(1);
      markTouch();
    },
    onEndDrag: () => {
      scrolling.set(0);
      lastTouch.set(nowSeconds());
    },
    onMomentumBegin: () => {
      if (mode.get() === MANUAL) scrolling.set(1);
    },
    onMomentumEnd: () => {
      scrolling.set(0);
      if (mode.get() === MANUAL) lastTouch.set(nowSeconds());
    },
  });

  const chartTopOnScreen = dy + CH.top;
  const screenH = H;
  useFrameCallback(() => {
    'worklet';
    const now = nowSeconds();
    if (loopStart.get() < 0) loopStart.set(now);
    const isReduced = reducedSV.get() === 1;
    const y = offset.get();
    let m = mode.get();
    if (m === AUTO && (isReduced || autoSV.get() === 0)) {
      m = MANUAL;
      mode.set(MANUAL);
    }
    let v = years.get();

    if (m === AUTO) {
      const t = (((now - loopStart.get()) % LOOP) + LOOP) % LOOP;
      const f = loopFrame(t, REST_YEARS, D.degPerYear, layout.historyOffset);
      scrollTo(scrollRef, 0, f.scroll, false);
      v = f.years;
      setIf(trail, trailFor(f.velocity));
      revealAt.set(f.reveal < 0 ? -1 : now - f.reveal);
      fadeAt.set(-1);
      setIf(revealOpacity, f.revealOpacity);
      landAt.set(now - (t - LAND_T));
      landed.set(f.scroll >= Y_LAND - 0.5 ? 1 : 0);
    } else {
      if (m === RETURN) {
        const p = clamp01((now - returnAt.get()) / RETURN_DURATION);
        scrollTo(scrollRef, 0, returnFrom.get() * (1 - easeInOut(p)), false);
        const settled = releaseAt.get() < 0 || now - releaseAt.get() > 1.2;
        if (p >= 1 && returnResume.get() === 0) {
          mode.set(MANUAL);
          lastTouch.set(now);
        } else if (p >= 1 && settled) {
          mode.set(AUTO);
          loopStart.set(now);
          releaseAt.set(-1);
          v = REST_YEARS;
        }
      }
      if (dragging.get() === 0 && releaseAt.get() >= 0) {
        const tau = now - releaseAt.get();
        if (isReduced) {
          v = lerp(releaseFrom.get(), REST_YEARS, easeOut(tau / 0.2));
        } else {
          v = REST_YEARS + springDisplacement(tau, releaseFrom.get() - REST_YEARS, releaseVel.get(), SPRINGS.dot);
        }
        if (tau > 1.6) {
          releaseAt.set(-1);
          v = REST_YEARS;
        }
      }
      // The comet follows the dot's real speed.
      const dt = Math.max(1 / 240, now - prevNow.get());
      const vel = ((v - prevYears.get()) / dt) * D.degPerYear;
      const target = isReduced ? 0 : trailFor(vel);
      setIf(trail, trail.get() + (target - trail.get()) * 0.5);

      // The history draws when it comes into view and fades when it leaves.
      const top = chartTopOnScreen - y;
      const visible = top < screenH - 150;
      const gone = top > screenH - 90;
      if (revealAt.get() < 0 && visible) {
        revealAt.set(isReduced ? now - 10 : now);
        fadeAt.set(-1);
        revealOpacity.set(1);
      } else if (revealAt.get() >= 0 && gone) {
        if (fadeAt.get() < 0) fadeAt.set(now);
        const fp = (now - fadeAt.get()) / REVEAL.fade;
        setIf(revealOpacity, 1 - clamp01(fp));
        if (fp >= 1) {
          revealAt.set(-1);
          fadeAt.set(-1);
        }
      } else if (revealAt.get() >= 0) {
        fadeAt.set(-1);
        setIf(revealOpacity, 1);
      }

      // The dial lands in the chip: a bump and one light tap.
      const p = clamp01(y / Y_LAND);
      if (p >= 0.999 && landed.get() === 0) {
        landed.set(1);
        if (!isReduced) {
          landAt.set(now);
          if (m === MANUAL) scheduleOnRN(lightHaptic);
        }
      } else if (p < 0.9 && landed.get() === 1) {
        landed.set(0);
      }

      if (
        m === MANUAL &&
        autoSV.get() === 1 &&
        !isReduced &&
        dragging.get() === 0 &&
        scrolling.get() === 0 &&
        now - lastTouch.get() > RESUME_AFTER
      ) {
        mode.set(RETURN);
        returnResume.set(1);
        returnAt.set(now);
        returnFrom.set(Math.max(0, y));
      }
    }

    prevYears.set(v);
    prevNow.set(now);
    setIf(years, v);

    // History reveal, from the seconds since it started.
    const e = revealAt.get() < 0 ? -1 : now - revealAt.get();
    setIf(lineProgress, e < 0 ? 0 : easeInOut(e / REVEAL.line));
    setIf(guides, e < 0 ? 0 : easeOut((e - REVEAL.guidesAt) / REVEAL.guides));
    setIf(count1, e < 0 ? 0 : easeOut((e - REVEAL.countAt[0]) / REVEAL.count));
    setIf(count2, e < 0 ? 0 : easeOut((e - REVEAL.countAt[1]) / REVEAL.count));
    setIf(badge1, e < 0 ? 0 : Math.min(e - REVEAL.badgeAt[0], 2));
    setIf(badge2, e < 0 ? 0 : Math.min(e - REVEAL.badgeAt[1], 2));
    setIf(bump, isReduced ? 0 : landBump(now - landAt.get()));
  });

  // Drag the dot round the dial: "what if" your health age were…
  const pan = useMemo(() => {
    const size = 2 * OUTER + 32;
    const c = size / 2;
    return Gesture.Pan()
      .manualActivation(true)
      .onTouchesDown((e, manager) => {
        const touch = e.changedTouches[0];
        const dx = touch.x - c;
        const dyy = touch.y - c;
        const dist = Math.sqrt(dx * dx + dyy * dyy);
        const onRing = Math.abs(dist - D.r) < D.stroke / 2 + 14;
        if (onRing && offset.get() < 4) manager.activate();
        else manager.fail();
      })
      .onStart(() => {
        dragging.set(1);
        releaseAt.set(-1);
        markTouch();
        lift.set(withSpring(1, { duration: 300, dampingRatio: 0.8 }));
      })
      .onUpdate((e) => {
        const a = (Math.atan2(e.x - c, -(e.y - c)) * 180) / Math.PI;
        let next = a / D.degPerYear;
        // Do not jump across the gap at the bottom of the dial.
        if (Math.abs(a) > 150 && Math.sign(a) !== Math.sign(years.get())) next = years.get();
        next = Math.max(-D.maxYears, Math.min(D.maxYears, next));
        years.set(next);
        lastTouch.set(nowSeconds());
        const step = Math.floor(next);
        if (step !== lastYearStep.get()) {
          lastYearStep.set(step);
          scheduleOnRN(selectionHaptic);
        }
      })
      .onFinalize((e) => {
        if (dragging.get() === 0) return;
        dragging.set(0);
        const now = nowSeconds();
        const a = (years.get() * D.degPerYear * Math.PI) / 180;
        const omega = (e.velocityX * Math.cos(a) + e.velocityY * Math.sin(a)) / D.r; // rad/s, clockwise
        const yearsPerSecond = (omega * 180) / Math.PI / D.degPerYear;
        releaseFrom.set(years.get());
        releaseVel.set(Math.max(-40, Math.min(40, yearsPerSecond)));
        releaseAt.set(now);
        lastTouch.set(now);
        lastYearStep.set(Math.floor(REST_YEARS));
        lift.set(withSpring(0, { duration: 400, dampingRatio: 1 }));
      });
  }, [dragging, lastTouch, lastYearStep, lift, markTouch, offset, releaseAt, releaseFrom, releaseVel, years]);
  const native = useMemo(() => Gesture.Native(), []);
  const dialPan = useMemo(() => pan.blocksExternalGesture(native), [native, pan]);

  // The dial's flight from its place into the Health Age chip, driven by the scroll offset.
  const dialStyle = useAnimatedStyle(() => {
    const y = offset.get();
    if (reducedSV.get() === 1) {
      return { opacity: 1 - seg(y, REDUCED_FADE[0], REDUCED_FADE[1]), transform: [{ translateX: 0 }, { translateY: -y }, { scale: 1 }] };
    }
    const s = smoothstep(clamp01(y / Y_LAND));
    const cx = lerp(dialCx, chipIconCx, s);
    const cy = lerp(dy + D.cy - y, chipCy, s);
    return {
      opacity: 1 - seg(clamp01(y / Y_LAND), FLIGHT.crossfadeStart, 1),
      transform: [{ translateX: cx - dialCx }, { translateY: cy - (dy + D.cy) }, { scale: lerp(1, RING_RATIO, s) }],
    };
  });
  const numberStyle = useAnimatedStyle(() => {
    const p = reducedSV.get() === 1 ? 0 : clamp01(offset.get() / Y_LAND);
    return { opacity: 1 - seg(p, 0, FLIGHT.numberFadeEnd) };
  });
  const haloStyle = useAnimatedStyle(() => ({ opacity: 1 - seg(offset.get(), 0, Y_LAND * 0.7) }));
  const miniStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 + LAND.amp * bump.get() }] }));
  // While the dot is dragged, the Health Age stat reads the "what if" age (same tenths as the dial).
  const healthAgeProps = useAnimatedProps(() => {
    const v = Math.round((AGES.actual + years.get()) * 10) / 10;
    const text = `${v.toFixed(1)} years`;
    return { text, defaultValue: text } as never;
  });
  const guideStyle = useAnimatedStyle(() => ({ opacity: guides.get() * revealOpacity.get() }));

  const dialLeft = dialCx - DIAL_SIZE / 2;
  const dialTop = dy + D.cy - DIAL_SIZE / 2;
  const chartLeft = CH.left;
  const textW = W - space.textInset * 2;
  const halo = layout.halo;
  const haloSize = halo.r * 2;

  return (
    <View style={styles.root} onTouchStart={onRootTouch} onTouchEnd={onRootTouch}>
      <StatusBar style="dark" />
      <BlurTargetView ref={blurTarget} collapsable={false} style={StyleSheet.absoluteFill}>
        <GestureDetector gesture={native}>
          <Animated.ScrollView
            ref={scrollRef}
            onScroll={scrollHandler}
            scrollEventThrottle={1}
            showsVerticalScrollIndicator={false}
            contentContainerStyle={{ width: W, height: contentHeight }}
            style={StyleSheet.absoluteFill}
          >
            <Animated.View pointerEvents="none" style={[styles.abs, { left: center + (halo.cx - PIN_WIDTH / 2) - halo.r, top: dy + halo.cy - halo.r }, haloStyle]}>
              <Canvas style={{ width: haloSize, height: haloSize }}>
                <Circle cx={halo.r} cy={halo.r} r={halo.r}>
                  <RadialGradient c={vec(halo.r, halo.r)} r={halo.r} colors={['rgba(185,219,250,0.78)', 'rgba(196,224,250,0.55)', 'rgba(214,230,248,0)']} positions={[0, 0.52, 1]} />
                </Circle>
              </Canvas>
            </Animated.View>

            <View pointerEvents="none" style={[styles.abs, { left: W - (PIN_WIDTH - layout.info.cx) - layout.info.d / 2, top: dy + layout.info.cy - layout.info.d / 2 }]} accessible accessibilityLabel="About Health Age">
              <InfoGlyph size={layout.info.d} />
            </View>

            <GestureDetector gesture={dialPan}>
              <View
                style={[styles.abs, { left: dialCx - OUTER - 16, top: dy + D.cy - OUTER - 16, width: 2 * OUTER + 32, height: 2 * OUTER + 32 }]}
                accessible
                accessibilityRole="image"
                accessibilityLabel={`Health age ${AGES.health} years, ${Math.abs(REST_YEARS)} years younger than your actual age of ${AGES.actual}`}
              />
            </GestureDetector>

            <Text style={[styles.abs, txt(type.title), { left: space.textInset, width: textW + 8, top: dy + topFor(baseline.title, type.title.size, type.title.line), lineHeight: type.title.line }]} allowFontScaling={false} accessibilityRole="header">
              {copy.title}
            </Text>
            <Text style={[styles.abs, txt(type.body), { left: space.textInset, width: textW + 8, top: dy + topFor(baseline.body, type.body.size, type.body.line), lineHeight: type.body.line }]} allowFontScaling={false}>
              {copy.body}
            </Text>
            <Text style={[styles.abs, txt(type.statLabel), { left: space.textInset, top: dy + topFor(baseline.statLabel, type.statLabel.size) }]} allowFontScaling={false}>
              {copy.healthAgeLabel}
            </Text>
            <AnimatedTextInput
              editable={false}
              pointerEvents="none"
              defaultValue={copy.healthAgeValue}
              animatedProps={healthAgeProps}
              allowFontScaling={false}
              accessibilityLabel={`${copy.healthAgeLabel} ${copy.healthAgeValue}`}
              style={[styles.abs, styles.valueInput, txt(type.statValue), { left: space.textInset, top: dy + topFor(baseline.statValue, type.statValue.size), width: 150 }]}
            />
            <Text style={[styles.abs, txt(type.statLabel), { left: center + (space.statColumn - PIN_WIDTH / 2), top: dy + topFor(baseline.statLabel, type.statLabel.size) }]} allowFontScaling={false}>
              {copy.actualAgeLabel}
            </Text>
            <Text style={[styles.abs, txt(type.statValue), { left: center + (space.statColumn - PIN_WIDTH / 2), top: dy + topFor(baseline.statValue, type.statValue.size) }]} allowFontScaling={false}>
              {copy.actualAgeValue}
            </Text>

            <Text style={[styles.abs, txt(type.heading), { left: space.textInset, top: dy + topFor(layout.historyHeading, type.heading.size) }]} allowFontScaling={false} accessibilityRole="header">
              {copy.history}
            </Text>
            <View style={[styles.abs, { left: chartLeft, top: dy + CH.top, width: CHART_W, height: CHART_H }]} accessible accessibilityLabel="Health age history, November to March: younger than your actual age, trending younger">
              <HistoryChart progress={lineProgress} opacity={revealOpacity} />
            </View>
            {months.map((mth, i) => (
              <Text key={mth} style={[styles.abs, txt(type.axis), { left: chartLeft + ((CH.right - CH.left) * i) / CH.columns + CH.labelOffset, top: dy + topFor(CH.labelBaseline, type.axis.size) }]} allowFontScaling={false}>
                {mth}
              </Text>
            ))}
            <Animated.Text style={[styles.abs, txt(type.guide), { left: CH.guideX, top: dy + topFor(CH.olderBaseline, type.guide.size) }, guideStyle]} allowFontScaling={false}>
              {copy.older}
            </Animated.Text>
            <Animated.Text style={[styles.abs, txt(type.guide), { left: CH.guideX, top: dy + topFor(CH.youngerBaseline, type.guide.size) }, guideStyle]} allowFontScaling={false}>
              {copy.younger}
            </Animated.Text>

            <Text style={[styles.abs, txt(type.heading), { left: space.textInset, top: dy + topFor(layout.raisingHeading, type.heading.size) }]} allowFontScaling={false} accessibilityRole="header">
              {copy.raising}
            </Text>
            <FactorCard index={0} top={dy + layout.cards[0].top} width={cardW} count={count1} badge={badge1} />
            <FactorCard index={1} top={dy + layout.cards[1].top} width={cardW} count={count2} badge={badge2} />
          </Animated.ScrollView>
        </GestureDetector>
      </BlurTargetView>

      <LinearGradient
        pointerEvents="none"
        colors={['rgba(241,239,248,1)', 'rgba(241,239,248,0.92)', 'rgba(241,239,248,0.62)', 'rgba(241,239,248,0)']}
        locations={[0, 0.32, 0.62, 1]}
        style={[styles.abs, { left: 0, right: 0, top: 0, height: dy + CHIP.top + CHIP.height + 10 }]}
      />
      <View pointerEvents="box-none" style={StyleSheet.absoluteFill}>
        <Chip
          left={space.chipInset}
          top={dy + CHIP.top}
          width={chipW}
          selected={false}
          blurTarget={blurTarget}
          icon={<PetalMark size={CHIP.icon} fills={OVERVIEW_FILLS} />}
          title={copy.overviewTitle}
          value={copy.overviewValue}
        />
        <Chip
          left={space.chipInset + chipW + space.chipGap}
          top={dy + CHIP.top}
          width={chipW2}
          selected
          onPress={glideToDial}
          blurTarget={blurTarget}
          icon={
            <Animated.View style={miniStyle}>
              <MiniDial size={CHIP.icon + 2} />
            </Animated.View>
          }
          title={copy.chipTitle}
          value={copy.chipValue}
        />
      </View>

      <Animated.View pointerEvents="none" style={[styles.abs, { left: dialLeft, top: dialTop, width: DIAL_SIZE, height: DIAL_SIZE }, dialStyle]}>
        <DialRing years={years} trail={trail} lift={lift} />
        <Animated.View style={[styles.abs, { left: 0, right: 0, top: 0, bottom: 0 }, numberStyle]} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
          <View style={[styles.abs, { left: 0, right: 0, top: DIAL_SIZE / 2 + (baseline.number - D.cy) - 0.953 * type.number.size, height: NUMBER_LINE, alignItems: 'center', transform: [{ translateX: layout.numberCenterX - D.cx }] }]}>
            <YearsOdometer years={years} />
          </View>
          <Text style={[styles.abs, txt(type.unit), { left: 0, right: 0, textAlign: 'center', top: DIAL_SIZE / 2 + (baseline.unit - D.cy) - SF_ASCENT * type.unit.size, transform: [{ translateX: layout.unitCenterX - D.cx }] }]} allowFontScaling={false}>
            {copy.unit}
          </Text>
        </Animated.View>
      </Animated.View>

      <TabBar width={W} blurTarget={blurTarget} />
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.page },
  abs: { position: 'absolute' },
  chip: { position: 'absolute' },
  chipPlain: { borderWidth: StyleSheet.hairlineWidth * 2, borderColor: colors.chipBorder },
  chipSelected: {
    shadowColor: '#4A4880',
    shadowOpacity: 0.06,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 3 },
  },
  chipIcon: { position: 'absolute', top: 0, alignItems: 'center', justifyContent: 'center' },
  card: {
    position: 'absolute',
    backgroundColor: colors.card,
    shadowColor: '#4D4A88',
    shadowOpacity: 0.04,
    shadowRadius: 14,
    shadowOffset: { width: 0, height: 4 },
  },
  cardIcon: { position: 'absolute', alignItems: 'center', justifyContent: 'center' },
  valueInput: { padding: 0, margin: 0, borderWidth: 0 },
  tabBar: {
    position: 'absolute',
    overflow: 'visible',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.85)',
    shadowColor: '#000',
    shadowOpacity: 0.1,
    shadowRadius: 16,
    shadowOffset: { width: 0, height: 5 },
  },
  tabPill: { position: 'absolute', backgroundColor: colors.tabSelected },
  tab: { position: 'absolute', top: 0, alignItems: 'center', justifyContent: 'center' },
});
