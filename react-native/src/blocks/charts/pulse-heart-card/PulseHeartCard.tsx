import { BlurMask, Canvas as SkiaCanvas, Group, RoundedRect } from '@shopify/react-native-skia';
import { BlurView } from 'expo-blur';
import { GlassView, isLiquidGlassAvailable } from 'expo-glass-effect';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { AccessibilityInfo, AppState, Pressable, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView, ScrollView, type GestureType } from 'react-native-gesture-handler';
import Animated, {
  type FrameInfo,
  type SharedValue,
  useAnimatedStyle,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSequence,
  withSpring,
  withTiming,
  Easing,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle, Line, Path, Rect } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { FRAME, HeartCanvas, REST_PHI, restFrame } from './HeartCanvas';
import {
  BAR_BOUNDS,
  CURSOR_FADE,
  IDLE_RESET,
  PRESS,
  LOOP,
  NEVER,
  REFERENCE_HEIGHT,
  REFERENCE_SAFE_TOP,
  REFERENCE_WIDTH,
  REST_BPM,
  SAMPLES,
  TRACE,
  TRACE_LEAD,
  autoplayAt,
  autoplayIdle,
  beatAt,
  clampIndex,
  sampleTime,
} from './motion';
import { COLORS, LAYOUT, LENS, SPACE, TAB_MOTION } from './theme';

/** iOS 26 Liquid Glass for the tab bar; older systems keep a blur under a translucent fill. */
const GLASS = isLiquidGlassAvailable();

export { LOOP };

type Props = {
  autoPlay?: boolean;
};

/** Who drives the heart. */
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);

/** Press feedback: 0.97 in 120 ms on touch-down, back in 100 ms (motion.ts PRESS). */
function PressScale({ onPress, children, style, ...a11y }: {
  onPress: () => void;
  children: ReactNode;
  style?: object;
  accessibilityRole?: 'button' | 'tab';
  accessibilityLabel?: string;
  accessibilityState?: { selected: boolean };
}) {
  const pressed = useSharedValue(1);
  const pressStyle = useAnimatedStyle(() => ({ transform: [{ scale: pressed.get() }] }));
  return (
    <Pressable
      {...a11y}
      accessible
      hitSlop={4}
      onPress={onPress}
      onPressIn={() => pressed.set(withTiming(PRESS.scale, { duration: PRESS.inMs, easing: EASE_OUT }))}
      onPressOut={() => pressed.set(withTiming(1, { duration: PRESS.outMs, easing: EASE_OUT }))}
    >
      <Animated.View style={[style, pressStyle]}>{children}</Animated.View>
    </Pressable>
  );
}

const AUTO = 0;
const MANUAL = 1;
const RELEASED = 2;
const REST = 3;

function selectionHaptic() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

function arcPath(cx: number, cy: number, radius: number, progress: number): string {
  const start = -Math.PI / 2;
  const end = start + Math.PI * 2 * progress;
  const x0 = cx + radius * Math.cos(start);
  const y0 = cy + radius * Math.sin(start);
  const x1 = cx + radius * Math.cos(end);
  const y1 = cy + radius * Math.sin(end);
  const largeArc = progress > 0.5 ? 1 : 0;
  return `M ${x0} ${y0} A ${radius} ${radius} 0 ${largeArc} 1 ${x1} ${y1}`;
}

const HEART_PATH =
  'M12 21.35 10.55 20C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09A6.04 6.04 0 0 1 16.5 3C19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.5L12 21.35Z';

function HeartIcon({ size, color = COLORS.heart }: { size: number; color?: string }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24" accessibilityElementsHidden>
      <Path d={HEART_PATH} fill={color} />
    </Svg>
  );
}

function FitnessIcon({ scale }: { scale: number }) {
  const size = LAYOUT.chips.icon * scale;
  const centre = size / 2;
  const rings = [
    { radius: 12.8 * scale, progress: 0.82, color: COLORS.ringRed },
    { radius: 9 * scale, progress: 0.92, color: COLORS.ringYellow },
    { radius: 5.2 * scale, progress: 0.88, color: COLORS.metricGreen },
  ];
  return (
    <Svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} accessibilityElementsHidden>
      {rings.map((ring) => (
        <Circle key={ring.color} cx={centre} cy={centre} r={ring.radius} fill="none" stroke={ring.color} strokeOpacity={0.18} strokeWidth={3.3 * scale} />
      ))}
      {rings.map((ring) => (
        <Path key={`${ring.color}-progress`} d={arcPath(centre, centre, ring.radius, ring.progress)} fill="none" stroke={ring.color} strokeWidth={3.3 * scale} strokeLinecap="round" />
      ))}
    </Svg>
  );
}

function VitalsIcon({ scale }: { scale: number }) {
  const size = LAYOUT.chips.icon * scale;
  const centre = size / 2;
  const points = [
    [centre, centre - 8 * scale],
    [centre - 8 * scale, centre],
    [centre + 8 * scale, centre],
    [centre, centre + 8 * scale],
  ];
  return (
    <Svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} accessibilityElementsHidden>
      {points.map(([cx, cy], index) => (
        <Circle key={`${cx}-${cy}`} cx={cx} cy={cy} r={5 * scale} fill="none" stroke={index === 0 ? COLORS.metricPink : COLORS.metricBlue} strokeWidth={3 * scale} />
      ))}
    </Svg>
  );
}

function Chevron({ scale, color = COLORS.faint }: { scale: number; color?: string }) {
  return (
    <Svg width={15 * scale} height={18 * scale} viewBox="0 0 15 18" accessibilityElementsHidden>
      <Path d="m5 3 5 6-5 6" fill="none" stroke={color} strokeWidth={1.8 * scale} strokeLinecap="round" strokeLinejoin="round" />
    </Svg>
  );
}

/** The pin's Longevity mark: a dark aperture of eight petals around a small hole. */
function ApertureIcon({ size, color }: { size: number; color: string }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24" accessibilityElementsHidden>
      <Circle cx={12} cy={12} r={10.4} fill={color} />
      {Array.from({ length: 8 }, (_, index) => {
        const angle = (index * Math.PI) / 4 + Math.PI / 8;
        return (
          <Line key={index} x1={12} y1={12} x2={12 + 11 * Math.cos(angle)} y2={12 + 11 * Math.sin(angle)} stroke={COLORS.card} strokeWidth={1.7} strokeLinecap="round" />
        );
      })}
      <Circle cx={12} cy={12} r={4} fill={COLORS.card} />
    </Svg>
  );
}

function BrowseIcon({ size, color }: { size: number; color: string }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24" accessibilityElementsHidden>
      {[0, 1].flatMap((row) => [0, 1].map((column) => (
        <Rect key={`${row}-${column}`} x={1.5 + column * 11} y={1.5 + row * 11} width={10} height={10} rx={2.3} fill={color} />
      )))}
    </Svg>
  );
}

function MetricChip({
  title,
  value,
  width,
  scale,
  active,
  icon,
  onPress,
}: {
  title: string;
  value: string;
  width: number;
  scale: number;
  active: boolean;
  icon: ReactNode;
  onPress?: () => void;
}) {
  const content = (
    <View
      style={[
        styles.metricChip,
        {
          width: width * scale,
          height: LAYOUT.strip.height * scale,
          borderRadius: LAYOUT.strip.radius * scale,
          paddingHorizontal: SPACE.chipPadding * scale,
          backgroundColor: active ? '#FFFFFF' : COLORS.card,
          borderColor: active ? 'transparent' : COLORS.border,
          borderWidth: active ? 0 : SPACE.hairline * scale,
        },
      ]}
      accessible={!onPress}
      accessibilityLabel={!onPress ? `${title}, ${value}` : undefined}
    >
      <View style={{ width: LAYOUT.chips.icon * scale, height: LAYOUT.chips.icon * scale, alignItems: 'center', justifyContent: 'center' }}>{icon}</View>
      <View style={{ marginLeft: SPACE.chipIconGap * scale, flex: 1 }}>
        <Text allowFontScaling={false} numberOfLines={1} style={[styles.metricTitle, { fontSize: 16 * scale, lineHeight: 19 * scale }]}>
          {title}
        </Text>
        <Text allowFontScaling={false} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.78} style={[styles.metricValue, { fontSize: 17 * scale, lineHeight: 21 * scale }]}>
          {value}
        </Text>
      </View>
    </View>
  );
  return onPress ? (
    <PressScale accessibilityRole="button" accessibilityLabel={`${title}, ${value}`} onPress={onPress}>
      {content}
    </PressScale>
  ) : content;
}

function PulseBar({ bar, index, scale, selected, opacity }: { bar: BarBoundsType; index: number; scale: number; selected: SharedValue<number>; opacity: SharedValue<number> }) {
  const activeStyle = useAnimatedStyle(() => ({ opacity: selected.get() === index ? opacity.get() : 0 }));
  const origin = LAYOUT.chart.origin;
  return (
    <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, activeStyle]}>
      {bar.segments.map(([y0, y1]) => (
        <View
          key={y0}
          style={[
            styles.chartBar,
            {
              left: (bar.x - LAYOUT.chart.left) * scale,
              top: (y0 - origin) * scale,
              width: bar.width * scale,
              height: (y1 - y0) * scale,
              borderRadius: (bar.width / 2) * scale,
            },
          ]}
        />
      ))}
    </Animated.View>
  );
}

type BarBoundsType = (typeof BAR_BOUNDS)[number];

function PulseChart({
  scale,
  selectedIndex,
  selectedShared,
  cursorOpacity,
  pan,
  onAccessibilityAction,
}: {
  scale: number;
  selectedIndex: number | null;
  selectedShared: SharedValue<number>;
  cursorOpacity: SharedValue<number>;
  pan: GestureType;
  onAccessibilityAction: (event: { nativeEvent: { actionName: string } }) => void;
}) {
  const C = LAYOUT.chart;
  const cursorLineStyle = useAnimatedStyle(() => {
    const index = selectedShared.get() >= 0 ? selectedShared.get() : BAR_BOUNDS.length - 1;
    const bar = BAR_BOUNDS[index];
    const centreX = (bar.x - C.left + bar.width / 2) * scale;
    return { opacity: cursorOpacity.get() * 0.25, transform: [{ translateX: centreX }] };
  });
  const cursorRingStyle = useAnimatedStyle(() => {
    const index = selectedShared.get() >= 0 ? selectedShared.get() : BAR_BOUNDS.length - 1;
    const bar = BAR_BOUNDS[index];
    const centreX = (bar.x - C.left + bar.width / 2) * scale;
    const centreY = (bar.y0 - C.origin - 3) * scale;
    return { opacity: cursorOpacity.get(), transform: [{ translateX: centreX - 4.5 * scale }, { translateY: centreY - 4.5 * scale }] };
  });
  const latestRingStyle = useAnimatedStyle(() => ({ opacity: 1 - cursorOpacity.get() }));
  const latest = BAR_BOUNDS[BAR_BOUNDS.length - 1];
  const latestX = (latest.x - C.left + latest.width / 2) * scale;
  const latestY = (latest.y0 - C.origin - 3) * scale;
  const text = selectedIndex === null ? '43–120 BPM' : `${SAMPLES[selectedIndex]} BPM · ${sampleTime(selectedIndex)}`;
  const accessibilityValue = selectedIndex === null ? '43 to 120 BPM. Latest reading 62 BPM' : `${SAMPLES[selectedIndex]} BPM at ${sampleTime(selectedIndex)}`;
  const plotWidth = C.width * scale;
  const plotHeight = C.plotHeight * scale;

  return (
    <GestureDetector gesture={pan}>
      <View
        style={[styles.chart, { top: (C.top - REFERENCE_SAFE_TOP) * scale, marginLeft: (-C.width / 2) * scale, width: C.width * scale, height: C.height * scale }]}
        accessible
        accessibilityRole="adjustable"
        accessibilityLabel="Heart rate history"
        accessibilityValue={{ text: accessibilityValue }}
        accessibilityHint="Swipe up or down to select a reading"
        accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
        onAccessibilityAction={onAccessibilityAction}
      >
        <Text allowFontScaling={false} style={[styles.chartTitle, { marginLeft: C.titleInset * scale, fontSize: C.titleSize * scale, lineHeight: C.titleLine * scale, fontVariant: ['tabular-nums'] }]}>
          {text}
        </Text>
        <View style={{ position: 'absolute', left: 0, top: C.plotTop * scale, width: plotWidth, height: plotHeight }} pointerEvents="none">
          <SkiaCanvas style={StyleSheet.absoluteFill}>
            {/* The pin's bars are soft-edged capsules; a 0.8 pt blur matches them. */}
            <Group opacity={0.78}>
              {BAR_BOUNDS.flatMap((bar) => bar.segments.map(([y0, y1]) => (
                <RoundedRect
                  key={`${bar.x}-${y0}`}
                  x={(bar.x - C.left) * scale}
                  y={(y0 - C.origin) * scale}
                  width={bar.width * scale}
                  height={(y1 - y0) * scale}
                  r={(bar.width / 2) * scale}
                  color={COLORS.chartPink}
                />
              )))}
              <BlurMask blur={C.barBlur * scale} style="normal" />
            </Group>
          </SkiaCanvas>
          <Svg width={plotWidth} height={plotHeight} viewBox={`0 0 ${plotWidth} ${plotHeight}`}>
            {Array.from({ length: 33 }, (_, index) => (
              <Circle key={`dot-${index}`} cx={(2 + index * 10.35 + 0.7) * scale} cy={60.7 * scale} r={0.7 * scale} fill={COLORS.faint} fillOpacity={0.55} />
            ))}
            <Line x1={2 * scale} y1={57 * scale} x2={2 * scale} y2={plotHeight} stroke={COLORS.faint} strokeOpacity={0.7} strokeWidth={scale} />
            <Line x1={334 * scale} y1={57 * scale} x2={334 * scale} y2={plotHeight} stroke={COLORS.faint} strokeOpacity={0.7} strokeWidth={scale} />
          </Svg>
          {BAR_BOUNDS.map((bar, index) => (
            <PulseBar key={`active-${bar.x}`} bar={bar} index={index} scale={scale} selected={selectedShared} opacity={cursorOpacity} />
          ))}
          <Animated.View style={[styles.cursorLine, { top: 2 * scale, height: 58 * scale, width: scale }, cursorLineStyle]} />
          <Animated.View style={[styles.cursorRing, { width: 9 * scale, height: 9 * scale, borderRadius: 4.5 * scale, borderWidth: 2.4 * scale }, cursorRingStyle]} />
          <Animated.View
            style={[styles.cursorRing, { left: latestX - 4.5 * scale, top: latestY - 4.5 * scale, width: 9 * scale, height: 9 * scale, borderRadius: 4.5 * scale, borderWidth: 2.4 * scale }, latestRingStyle]}
          />
        </View>
        <View style={[styles.chartLabels, { top: C.labelTop * scale, width: plotWidth }]} pointerEvents="none">
          <Text allowFontScaling={false} style={[styles.chartLabel, { fontSize: C.labelSize * scale, lineHeight: C.labelLine * scale }]}>12AM</Text>
          <Text allowFontScaling={false} style={[styles.chartLabel, { fontSize: C.labelSize * scale, lineHeight: C.labelLine * scale }]}>12AM</Text>
        </View>
      </View>
    </GestureDetector>
  );
}

const TABS = [
  { kind: 'heart', title: 'Insights' },
  { kind: 'longevity', title: 'Longevity' },
  { kind: 'browse', title: 'Browse' },
] as const;

/**
 * The selection lens as a small piece of glass: the bar and the page under it refract through a tinted capsule (Liquid Glass
 * on iOS 26, a blur elsewhere), and a white rim with a sheen along its top edge catches the light. Its tint keeps the pin's pill.
 */
function GlassLens({ radius }: { radius: number }) {
  return (
    <>
      {GLASS ? (
        <GlassView glassEffectStyle="clear" tintColor={LENS.glassTint} style={[StyleSheet.absoluteFill, { borderRadius: radius }]} />
      ) : (
        <>
          <BlurView intensity={LENS.blur} tint="light" style={StyleSheet.absoluteFill} />
          <View style={[StyleSheet.absoluteFill, { backgroundColor: LENS.fallback }]} />
        </>
      )}
      <LinearGradient colors={[LENS.sheen, 'transparent']} locations={[0, LENS.sheenTo]} style={StyleSheet.absoluteFill} />
      <View style={[StyleSheet.absoluteFill, { borderRadius: radius, borderWidth: LENS.rimWidth, borderColor: LENS.rim }]} />
    </>
  );
}

/**
 * The pin's floating tab bar as iOS 26 Liquid Glass: the page refracts under the capsule and the
 * selection lens slides to a tapped tab on a spring, stretching as it travels. Tabs are peers, so
 * nothing else moves; the screens behind Longevity and Browse are not part of the block.
 */
function PulseTabBar({ scale, still }: { scale: number; still: boolean }) {
  const T = LAYOUT.tabBar;
  const [active, setActive] = useState(0);
  const lensX = useSharedValue(0);
  const lensStretch = useSharedValue(1);
  const lensStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: lensX.get() * T.item * scale }, { scaleX: lensStretch.get() }],
  }));
  const select = (index: number) => {
    if (index === active) return;
    setActive(index);
    if (still) {
      lensX.set(index);
    } else {
      lensX.set(withSpring(index, TAB_MOTION.lens));
      lensStretch.set(withSequence(
        withTiming(TAB_MOTION.stretch, { duration: TAB_MOTION.stretchMs, easing: EASE_OUT }),
        withSpring(1, TAB_MOTION.settle),
      ));
    }
    selectionHaptic();
  };
  return (
    <View
      style={[
        styles.tabBar,
        {
          width: T.width * scale,
          height: T.height * scale,
          borderRadius: T.radius * scale,
          padding: SPACE.tabPadding * scale,
          bottom: T.bottom * scale,
          marginLeft: (-T.width / 2) * scale,
          boxShadow: [{ offsetX: 0, offsetY: 5 * scale, blurRadius: 12 * scale, color: COLORS.shadow }],
        },
      ]}
      accessibilityRole="tablist"
    >
      {GLASS ? (
        <GlassView glassEffectStyle="regular" tintColor={COLORS.tabGlassTint} style={[StyleSheet.absoluteFill, { borderRadius: T.radius * scale }]} />
      ) : (
        <>
          <BlurView intensity={30} tint="light" style={StyleSheet.absoluteFill} />
          <View style={[StyleSheet.absoluteFill, { backgroundColor: COLORS.tabBar }]} />
        </>
      )}
      <Animated.View
        pointerEvents="none"
        style={[
          styles.tabLens,
          { left: SPACE.tabPadding * scale, top: SPACE.tabPadding * scale, width: T.item * scale, height: T.itemHeight * scale, borderRadius: (T.itemHeight / 2) * scale },
          lensStyle,
        ]}
      >
        <GlassLens radius={(T.itemHeight / 2) * scale} />
      </Animated.View>
      {TABS.map((tab, index) => {
        const selected = index === active;
        const tint = selected ? COLORS.activeBlue : COLORS.ink;
        return (
          <PressScale
            key={tab.kind}
            onPress={() => select(index)}
            style={[styles.tabItem, { width: T.item * scale, height: T.itemHeight * scale }]}
            accessibilityRole="tab"
            accessibilityState={{ selected }}
            accessibilityLabel={tab.title}
          >
            <View style={{ position: 'absolute', top: (T.iconCentre - T.icon / 2) * scale, width: T.icon * scale, height: T.icon * scale }}>
              {tab.kind === 'heart' ? <HeartIcon size={T.icon * scale} color={tint} /> : tab.kind === 'longevity' ? <ApertureIcon size={T.icon * scale} color={tint} /> : <BrowseIcon size={T.icon * scale} color={tint} />}
            </View>
            <Text allowFontScaling={false} style={[styles.tabLabel, { top: T.labelTop * scale, color: tint, fontSize: T.labelSize * scale, lineHeight: T.labelLine * scale }]}>
              {tab.title}
            </Text>
          </PressScale>
        );
      })}
    </View>
  );
}

function SupportMetric({ title, value, scale }: { title: string; value: string; scale: number }) {
  const S = LAYOUT.support;
  return (
    <View style={{ width: S.width * scale }} accessible accessibilityLabel={`${title}, ${value}`}>
      <View style={{ flexDirection: 'row', alignItems: 'center', height: S.titleLine * scale }}>
        <Text allowFontScaling={false} numberOfLines={1} style={[styles.supportTitle, { fontSize: S.titleSize * scale, lineHeight: S.titleLine * scale }]}>{title}</Text>
        <Chevron scale={scale} />
      </View>
      <Text allowFontScaling={false} style={[styles.supportValue, { marginTop: S.valueGap * scale, fontSize: S.valueSize * scale, lineHeight: S.valueLine * scale }]}>{value}</Text>
      <Text allowFontScaling={false} style={[styles.supportNote, { fontSize: S.noteSize * scale, lineHeight: S.noteLine * scale }]}>Typical</Text>
    </View>
  );
}

export function PulseHeartCard({ autoPlay = false }: Props) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const initialReducedMotion = useReducedMotion();
  const [reducedMotion, setReducedMotion] = useState(initialReducedMotion);
  const [foregroundState, setForegroundState] = useState(AppState.currentState === 'active');
  const [screenReader, setScreenReader] = useState(false);
  const [selectedIndex, setSelectedIndex] = useState<number | null>(null);
  const [driving, setDriving] = useState(autoPlay);
  const quiet = reducedMotion || screenReader;
  const scale = width / REFERENCE_WIDTH;
  const contentHeight = Math.max(height - insets.top, (REFERENCE_HEIGHT - REFERENCE_SAFE_TOP) * scale);
  const selectedBPM = selectedIndex === null ? REST_BPM : SAMPLES[selectedIndex];

  // UI-thread state. `frame` is everything the shader and the reading need this frame.
  const frame = useSharedValue<number[]>(restFrame());
  const clock = useSharedValue(0);
  const lastTimestamp = useSharedValue(-1);
  const foreground = useSharedValue(AppState.currentState === 'active');
  const quietShared = useSharedValue(quiet);
  const autoShared = useSharedValue(autoPlay);
  const mode = useSharedValue(autoPlay ? AUTO : REST);
  const loopOrigin = useSharedValue(0);
  const phi = useSharedValue(-99);
  const bpm = useSharedValue(REST_BPM);
  const lastLub = useSharedValue(-NEVER);
  const prevLub = useSharedValue(-NEVER);
  const traceStart = useSharedValue(0);
  const traceEnd = useSharedValue(-1);
  const releasedAt = useSharedValue(-1);
  const selectedShared = useSharedValue(-1);
  const cursorOpacity = useSharedValue(0);
  const lastHapticAt = useSharedValue(-1e9);

  const publishSelection = useCallback((index: number) => {
    setSelectedIndex((current) => (current === index ? current : index));
    setDriving(true);
  }, []);
  const clearSelection = useCallback((stillDriving: boolean) => {
    setSelectedIndex(null);
    setDriving(stillDriving);
  }, []);

  /** Back to the pin's frame; autoplay restarts its loop from t = 0. */
  const resetUI = useCallback(() => {
    'worklet';
    selectedShared.set(-1);
    cursorOpacity.set(0);
    bpm.set(REST_BPM);
    releasedAt.set(-1);
    loopOrigin.set(clock.get());
    mode.set(autoShared.get() ? AUTO : REST);
    frame.set(restFrame());
    scheduleOnRN(clearSelection, autoShared.get());
  }, [autoShared, bpm, clearSelection, clock, cursorOpacity, frame, loopOrigin, mode, releasedAt, selectedShared]);

  const selectSampleUI = useCallback((rawIndex: number) => {
    'worklet';
    const index = clampIndex(rawIndex);
    const previous = selectedShared.get();
    const now = clock.get();
    const currentMode = mode.get();
    if (currentMode !== MANUAL) {
      // Take over without a jump: continue the autoplay beat and trace if they are on screen,
      // otherwise start a fresh sweep with the pen at the left edge.
      if (currentMode === AUTO) {
        const p = autoplayAt(now - loopOrigin.get());
        if (p.phi >= p.traceStart && p.phi <= p.traceEnd + TRACE.fadeOut) {
          phi.set(p.phi);
          traceStart.set(p.traceStart);
          lastLub.set(now - p.tau0);
          prevLub.set(now - p.tau1);
        } else {
          phi.set(-TRACE_LEAD);
          traceStart.set(-TRACE_LEAD);
          lastLub.set(now - NEVER);
          prevLub.set(now - NEVER);
        }
      } else if (currentMode === REST || phi.get() > traceEnd.get() + TRACE.fadeOut) {
        if (currentMode === REST) {
          phi.set(-TRACE_LEAD);
          lastLub.set(now - NEVER);
          prevLub.set(now - NEVER);
        }
        traceStart.set(phi.get());
      }
      traceEnd.set(1e6);
      mode.set(MANUAL);
    }
    selectedShared.set(index);
    bpm.set(SAMPLES[index]);
    releasedAt.set(-1);
    cursorOpacity.set(1);
    if (previous !== index) {
      if (!quietShared.get() && now - lastHapticAt.get() >= 0.1) {
        lastHapticAt.set(now);
        scheduleOnRN(selectionHaptic);
      }
      scheduleOnRN(publishSelection, index);
    }
  }, [bpm, clock, cursorOpacity, lastHapticAt, lastLub, loopOrigin, mode, phi, prevLub, publishSelection, quietShared, releasedAt, selectedShared, traceEnd, traceStart]);

  const releaseSelectionUI = useCallback(() => {
    'worklet';
    if (mode.get() !== MANUAL) return;
    mode.set(RELEASED);
    releasedAt.set(clock.get());
    traceEnd.set(phi.get());
  }, [clock, mode, phi, releasedAt, traceEnd]);

  const setQuietUI = useCallback((nextQuiet: boolean) => {
    'worklet';
    quietShared.set(nextQuiet);
    frame.set(restFrame());
    if (!nextQuiet) loopOrigin.set(clock.get());
  }, [clock, frame, loopOrigin, quietShared]);

  useEffect(() => {
    scheduleOnUI(setQuietUI, quiet);
  }, [quiet, setQuietUI]);

  useEffect(() => {
    autoShared.set(autoPlay);
    scheduleOnUI(resetUI);
  }, [autoPlay, autoShared, resetUI]);

  useEffect(() => {
    const subscription = AccessibilityInfo.addEventListener('reduceMotionChanged', setReducedMotion);
    return () => subscription.remove();
  }, []);

  useEffect(() => {
    let mounted = true;
    void AccessibilityInfo.isScreenReaderEnabled().then((enabled) => {
      if (mounted) setScreenReader(enabled);
    });
    const subscription = AccessibilityInfo.addEventListener('screenReaderChanged', setScreenReader);
    return () => {
      mounted = false;
      subscription.remove();
    };
  }, []);

  const setForegroundUI = useCallback((active: boolean) => {
    'worklet';
    foreground.set(active);
    lastTimestamp.set(-1);
    if (active && mode.get() === MANUAL) releaseSelectionUI();
  }, [foreground, lastTimestamp, mode, releaseSelectionUI]);

  useEffect(() => {
    const subscription = AppState.addEventListener('change', (state) => {
      const active = state === 'active';
      setForegroundState(active);
      scheduleOnUI(setForegroundUI, active);
    });
    return () => subscription.remove();
  }, [setForegroundUI]);

  const frameWorklet = useCallback((info: FrameInfo) => {
    'worklet';
    if (!foreground.get()) {
      lastTimestamp.set(-1);
      return;
    }
    const nowMs = info.timestamp / 1000;
    const previous = lastTimestamp.get();
    lastTimestamp.set(nowMs);
    if (previous < 0) return;
    const dt = Math.min(Math.max(nowMs - previous, 0), 0.1);
    const now = clock.get() + dt;
    clock.set(now);
    if (quietShared.get()) return;

    const currentMode = mode.get();
    if (currentMode === REST) return;
    let tau0: number;
    let tau1: number;
    let untilNext: number;
    let period: number;
    let p: number;
    let start: number;
    let end: number;
    if (currentMode === AUTO) {
      const pulse = autoplayAt(now - loopOrigin.get());
      ({ tau0, tau1, untilNext, period } = pulse);
      p = pulse.phi;
      start = pulse.traceStart;
      end = pulse.traceEnd;
    } else {
      if (currentMode === RELEASED) {
        const idle = now - releasedAt.get();
        if (idle >= IDLE_RESET) {
          resetUI();
          return;
        }
        // The selection fades over the last 200 ms instead of vanishing.
        cursorOpacity.set(Math.min(1, (IDLE_RESET - idle) / CURSOR_FADE));
      }
      period = 60 / bpm.get();
      const before = phi.get();
      const after = before + dt / period;
      phi.set(after);
      if (currentMode === MANUAL && Math.floor(after) > Math.floor(before)) {
        // The lub happened inside this frame: date it exactly.
        const at = now - (after - Math.floor(after)) * period;
        prevLub.set(lastLub.get());
        lastLub.set(at);
      }
      tau0 = Math.min(now - lastLub.get(), NEVER);
      tau1 = Math.min(now - prevLub.get(), NEVER);
      untilNext = currentMode === MANUAL ? (Math.ceil(after) - after) * period : NEVER;
      p = after;
      start = traceStart.get();
      end = traceEnd.get();
    }
    const beat = beatAt(tau0, tau1, untilNext, period);
    if (currentMode === AUTO && autoplayIdle(beat, tau0, p, start, end)) {
      // The loop's rest stretch: the pin's frame is written once, then nothing changes, so the shader stops redrawing until the
      // next beat gathers. Only the clock above keeps ticking.
      if (frame.get()[FRAME.phi] !== REST_PHI) frame.set(restFrame());
      return;
    }
    const next = frame.get().slice();
    next[FRAME.body] = beat.body;
    next[FRAME.lobe] = beat.lobe;
    next[FRAME.squash] = beat.squash;
    next[FRAME.lift] = beat.lift;
    next[FRAME.gloss] = beat.gloss;
    next[FRAME.light] = beat.light;
    next[FRAME.push] = beat.push;
    next[FRAME.flash] = beat.flash;
    next[FRAME.tau0] = tau0;
    next[FRAME.tau1] = tau1;
    next[FRAME.phi] = p;
    next[FRAME.traceStart] = start;
    next[FRAME.traceEnd] = end;
    frame.set(next);
  }, [bpm, clock, cursorOpacity, foreground, frame, lastLub, lastTimestamp, loopOrigin, mode, phi, prevLub, quietShared, releasedAt, resetUI, traceEnd, traceStart]);

  const frameCallback = useFrameCallback(frameWorklet, false);
  const frameEnabled = foregroundState && !quiet && driving;
  useEffect(() => {
    lastTimestamp.set(-1);
    frameCallback.setActive(frameEnabled);
  }, [frameCallback, frameEnabled, lastTimestamp]);

  // The reading is printed on the heart: it rides 60 % of the beat and flashes on the R-peak.
  const RD = LAYOUT.reading;
  const numberCentre = RD.numberTop + RD.numberLine / 2;
  const unitCentre = RD.unitTop + RD.unitLine / 2;
  const ride = (centreY: number) => {
    'worklet';
    const f = frame.get();
    const s = 1 + 0.6 * f[FRAME.body];
    return [{ translateY: ((centreY - RD.heartCentreY) * (s - 1) + 0.6 * f[FRAME.lift]) * scale }, { scale: s }];
  };
  const numberStyle = useAnimatedStyle(() => ({ transform: ride(numberCentre) }));
  const unitStyle = useAnimatedStyle(() => ({ transform: ride(unitCentre) }));
  const flashStyle = useAnimatedStyle(() => ({ opacity: 0.85 * frame.get()[FRAME.flash] }));
  const chipHeartStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 + 1.5 * frame.get()[FRAME.body] }] }));

  const chartPan = useMemo(() => {
    const indexAtX = (x: number) => {
      'worklet';
      return clampIndex((x / scale - 3.9) / 7.25);
    };
    return Gesture.Pan()
      .minDistance(0)
      .shouldCancelWhenOutside(false)
      .onBegin((event) => selectSampleUI(indexAtX(event.x)))
      .onUpdate((event) => selectSampleUI(indexAtX(event.x)))
      .onFinalize(() => releaseSelectionUI());
  }, [releaseSelectionUI, scale, selectSampleUI]);

  const resetFromHeart = () => scheduleOnUI(resetUI);
  const adjustChart = (event: { nativeEvent: { actionName: string } }) => {
    const current = selectedIndex === null ? BAR_BOUNDS.length - 1 : selectedIndex;
    const direction = event.nativeEvent.actionName === 'increment' ? 1 : -1;
    scheduleOnUI(selectSampleUI, current + direction);
    scheduleOnUI(releaseSelectionUI);
  };

  const top = (referenceY: number) => (referenceY - REFERENCE_SAFE_TOP) * scale;
  const copyLeft = (width - LAYOUT.headline.width * scale) / 2;
  const headlineLeft = LAYOUT.headline.left * scale;
  const bodyLeft = LAYOUT.body.left * scale;
  const reading = (text: string, flash: boolean, style: object, unit: boolean) => (
    <Animated.Text
      allowFontScaling={false}
      style={[
        unit ? styles.bpmUnit : styles.bpm,
        unit
          ? { top: top(RD.unitTop), fontSize: RD.unitSize * scale, lineHeight: RD.unitLine * scale }
          : { top: top(RD.numberTop), fontSize: RD.numberSize * scale, lineHeight: RD.numberLine * scale, letterSpacing: RD.numberTracking * scale },
        flash && { color: COLORS.flash },
        style,
        flash && flashStyle,
      ]}
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants"
    >
      {text}
    </Animated.Text>
  );

  return (
    <GestureHandlerRootView style={styles.screen}>
      <StatusBar style="dark" />
      <ScrollView
        style={styles.scroll}
        showsVerticalScrollIndicator={false}
        contentContainerStyle={{ paddingTop: insets.top, paddingBottom: Math.max(insets.bottom, 16), minHeight: contentHeight + insets.top }}
      >
        <View style={[styles.stage, { width, height: contentHeight }]}>
          <HeartCanvas top={top(LAYOUT.heartCanvas.top)} width={width} scale={scale} frame={frame} />

          <View style={[styles.metricStrip, { top: 0, width, height: LAYOUT.strip.height * scale }]} accessibilityLabel="Cardio metrics">
            <MetricChip title="Fitness" value="5,253 steps" width={LAYOUT.chips.fitness} scale={scale} active={false} icon={<FitnessIcon scale={scale} />} />
            <View style={{ width: (SPACE.chipGap + 1) * scale }} />
            <MetricChip
              title="Heart"
              value={`${selectedBPM} BPM`}
              width={LAYOUT.chips.heart}
              scale={scale}
              active
              icon={(
                <Animated.View style={chipHeartStyle}>
                  <HeartIcon size={LAYOUT.chips.icon * scale} />
                </Animated.View>
              )}
              onPress={resetFromHeart}
            />
            <View style={{ width: SPACE.chipGap * scale }} />
            <MetricChip title="Vitals" value="Typical" width={LAYOUT.chips.vitals} scale={scale} active={false} icon={<VitalsIcon scale={scale} />} />
          </View>

          <View
            style={[styles.readingBox, { top: top(RD.numberTop), height: (RD.unitTop + RD.unitLine - RD.numberTop) * scale }]}
            accessible
            accessibilityLabel="Heart rate"
            accessibilityValue={{ text: `${selectedBPM} beats per minute` }}
          />
          {reading(String(selectedBPM), false, numberStyle, false)}
          {reading('BPM', false, unitStyle, true)}
          {reading('BPM', true, unitStyle, true)}

          <View style={[styles.copy, { left: headlineLeft, top: top(LAYOUT.headline.top), width: LAYOUT.headline.width * scale, height: LAYOUT.headline.height * scale }]} accessible accessibilityLabel="Cardio Fitness. Trending Up.">
            <Text allowFontScaling={false} style={[styles.copyHeading, { fontSize: LAYOUT.headline.size * scale, lineHeight: LAYOUT.headline.line * scale }]}>Cardio Fitness{`\n`}Trending Up</Text>
          </View>
          <View style={[styles.copy, { left: bodyLeft, top: top(LAYOUT.body.top), width: LAYOUT.body.width * scale, height: LAYOUT.body.height * scale }]} accessible accessibilityLabel="On average, your cardio fitness went up over the last month. This is great for your long-term health.">
            <Text allowFontScaling={false} style={[styles.copyBodyText, { fontSize: LAYOUT.body.size * scale, lineHeight: LAYOUT.body.line * scale }]}>On average, your cardio fitness{`\n`}went up over the last month. This is{`\n`}great for your long-term health.</Text>
          </View>

          <PulseChart
            scale={scale}
            selectedIndex={selectedIndex}
            selectedShared={selectedShared}
            cursorOpacity={cursorOpacity}
            pan={chartPan}
            onAccessibilityAction={adjustChart}
          />

          <View style={[styles.supportingMetrics, { left: copyLeft, top: top(LAYOUT.support.top), width: LAYOUT.headline.width * scale }]} accessibilityLabel="Supporting metrics">
            <SupportMetric title="Resting Heart Rate" value="55 BPM" scale={scale} />
            <View style={{ width: SPACE.metricGap * scale }} />
            <SupportMetric title="Recovery HRV" value="54 MS" scale={scale} />
          </View>
          {/* The pin's next row, mostly under the glass tab bar. */}
          <View style={[styles.supportingMetrics, { left: copyLeft, top: top(LAYOUT.support.nextTop), width: LAYOUT.headline.width * scale }]} accessibilityLabel="More metrics">
            <SupportMetric title="Walking Heart Rate" value="98 BPM" scale={scale} />
            <View style={{ width: SPACE.metricGap * scale }} />
            <SupportMetric title="Cardio Recovery" value="28 BPM" scale={scale} />
          </View>
        </View>
      </ScrollView>
      <PulseTabBar scale={scale} still={quiet} />
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: COLORS.background },
  scroll: { flex: 1, backgroundColor: COLORS.background },
  stage: { alignSelf: 'center', position: 'relative', backgroundColor: COLORS.background },
  metricStrip: { position: 'absolute', left: 0, flexDirection: 'row', alignItems: 'flex-start', overflow: 'hidden' },
  metricChip: { flexDirection: 'row', alignItems: 'center', borderCurve: 'continuous' },
  metricTitle: { color: COLORS.muted, fontWeight: '400' },
  metricValue: { color: COLORS.ink, fontWeight: '600', fontVariant: ['tabular-nums'] },
  readingBox: { position: 'absolute', left: '25%', right: '25%' },
  bpm: { position: 'absolute', left: 0, right: 0, color: COLORS.ink, fontWeight: '600', textAlign: 'center', fontVariant: ['tabular-nums'] },
  bpmUnit: { position: 'absolute', left: 0, right: 0, color: COLORS.bpmUnit, fontWeight: '500', textAlign: 'center' },
  copy: { position: 'absolute' },
  copyHeading: { color: COLORS.ink, fontWeight: '700' },
  copyBodyText: { color: COLORS.muted, fontWeight: '400' },
  chart: { position: 'absolute', left: '50%', overflow: 'visible' },
  chartTitle: { color: COLORS.ink, fontWeight: '600' },
  chartBar: { position: 'absolute', backgroundColor: COLORS.heart },
  cursorLine: { position: 'absolute', left: 0, backgroundColor: COLORS.heart, opacity: 0.25 },
  cursorRing: { position: 'absolute', left: 0, top: 0, backgroundColor: '#FFFFFF', borderColor: COLORS.heart },
  chartLabels: { position: 'absolute', left: 0, flexDirection: 'row', justifyContent: 'space-between' },
  chartLabel: { color: COLORS.faint, fontWeight: '400' },
  supportingMetrics: { position: 'absolute', flexDirection: 'row', alignItems: 'flex-start' },
  supportTitle: { color: COLORS.heartDark, fontWeight: '600', flexShrink: 1 },
  supportValue: { color: COLORS.supportValue, fontWeight: '600' },
  tabBar: { position: 'absolute', left: '50%', flexDirection: 'row', alignItems: 'center', borderWidth: 1, borderColor: COLORS.tabBorder, overflow: 'hidden' },
  tabItem: { alignItems: 'center' },
  tabLens: { position: 'absolute', overflow: 'hidden' },
  supportNote: { color: COLORS.muted, fontWeight: '400' },
  tabLabel: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontWeight: '600' },
});
