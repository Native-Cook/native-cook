import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';
import Animated, { useAnimatedProps, useAnimatedReaction, useFrameCallback, useSharedValue, type SharedValue } from 'react-native-reanimated';
import Svg, { Circle, ClipPath, Defs, G, LinearGradient, Path, Rect, Stop, Text as SvgText } from 'react-native-svg';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';

export const LOOP = 8;
const ACCENT = '#685BEE';
const INK = '#272832';

const clamp = (value: number, low: number, high: number) => { 'worklet'; return Math.max(low, Math.min(high, value)); };
const smooth = (value: number) => { 'worklet'; const u = clamp(value, 0, 1); return u * u * (3 - 2 * u); };
const envelope = (time: number) => { 'worklet'; return smooth((time - 1.4) / 1.8) * (1 - smooth((time - 4.8) / 1.8)); };
const sample = (x: number, selection: number) => { 'worklet'; return 178 - 90 * x + 26 * Math.sin(2 * Math.PI * x) + 18 * Math.cos(6 * Math.PI * x + selection * 0.5); };

type Props = { autoPlay?: boolean; previewTime?: number };

function resetPrism(pose: SharedValue<number>, clock: SharedValue<number>, mode: SharedValue<number>, previewTime: number | undefined, reduced: boolean) {
  'worklet';
  const time = previewTime == null ? 0 : ((previewTime % LOOP) + LOOP) % LOOP;
  pose.value = reduced ? 0.5 : previewTime == null ? 0 : envelope(time);
  clock.value = time;
  mode.value = 0;
}
function chooseChartRange(selection: SharedValue<number>, pose: SharedValue<number>, from: SharedValue<number>, elapsed: SharedValue<number>, mode: SharedValue<number>, value: number, reduced: boolean) {
  'worklet'; selection.value = value;
  if (reduced) { mode.value = 0; return; }
  pose.value = 0; from.value = 0; elapsed.value = 0; mode.value = 2;
}
function beginScrub(pose: SharedValue<number>, start: SharedValue<number>, mode: SharedValue<number>, detent: SharedValue<boolean>) {
  'worklet'; start.value = pose.value; mode.value = 1; detent.value = false;
}
function updateScrub(pose: SharedValue<number>, start: SharedValue<number>, mode: SharedValue<number>, detent: SharedValue<boolean>, translationX: number) {
  'worklet';
  mode.value = 1;
  const next = clamp(start.value + translationX / 180, 0, 1);
  pose.value = next;
  const crossed = next > 0.85 && !detent.value;
  if (crossed) detent.value = true;
  return crossed;
}
function releaseScrub(pose: SharedValue<number>, from: SharedValue<number>, elapsed: SharedValue<number>, mode: SharedValue<number>) {
  'worklet'; from.value = pose.value; elapsed.value = 0; mode.value = 2;
}
function setScrub(pose: SharedValue<number>, from: SharedValue<number>, elapsed: SharedValue<number>, mode: SharedValue<number>, value: number) {
  'worklet'; pose.value = value; from.value = value; elapsed.value = 0; mode.value = 2;
}
function lensPoint(pose: number, selection: number) {
  'worklet';
  const x = 74 + pose * 208;
  const y = sample((x - 24) / 289, selection);
  return { x, y };
}
const AnimatedSvgCircle = Animated.createAnimatedComponent(Circle);
const AnimatedSvgGroup = Animated.createAnimatedComponent(G);
const AnimatedSvgPath = Animated.createAnimatedComponent(Path);

function MovingLens({ pose, selection, chartPath }: { pose: SharedValue<number>; selection: SharedValue<number>; chartPath: string }) {
  const circleProps = useAnimatedProps(() => {
    const center = lensPoint(pose.value, selection.value);
    return { cx: center.x, cy: center.y };
  });
  const zoomProps = useAnimatedProps(() => {
    const center = lensPoint(pose.value, selection.value);
    const magnification = 1.35;
    return { opacity: 1, matrix: [magnification, 0, 0, magnification, (1 - magnification) * center.x, (1 - magnification) * center.y] };
  });
  const cursorPath = useCursorPathProps(pose, selection);
  return <>
    <AnimatedSvgCircle animatedProps={circleProps} r={53} fill="url(#lensGlass)" opacity={0.8} />
    <G clipPath="url(#lensClip)">
      <AnimatedSvgGroup animatedProps={zoomProps}>
        <Path d={chartPath} fill="none" stroke={ACCENT} strokeWidth={2.5} strokeLinecap="round" strokeLinejoin="round" />
      </AnimatedSvgGroup>
    </G>
    <AnimatedSvgCircle animatedProps={circleProps} r={53} fill="none" stroke="url(#lensRim)" strokeWidth={3} />
    <AnimatedSvgCircle animatedProps={circleProps} r={48} fill="none" stroke="rgba(255,255,255,0.75)" strokeWidth={0.7} />
    <AnimatedSvgCircle animatedProps={circleProps} r={4} fill={ACCENT} />
    <AnimatedSvgPath pointerEvents="none" fill="none" stroke="rgba(104,91,238,0.4)" strokeWidth={1} strokeDasharray="3 3" animatedProps={cursorPath} />
  </>;
}

function makeChartPathForSelection(selected: number) {
  let d = '';
  for (let i = 0; i <= 96; i++) {
    const x = i / 96;
    d += `${i === 0 ? 'M' : 'L'} ${24 + x * 289} ${sample(x, selected)} `;
  }
  return d;
}

export function PrismWindowChart({ autoPlay = false, previewTime }: Props) {
  const { width, height } = useWindowDimensions();
  const safe = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const [active, setActive] = useState(AppState.currentState === 'active');
  const [manualActive, setManualActive] = useState(false);
  const scale = Math.min(width / 393, Math.max(0, height - safe.top - safe.bottom) / 760);
  const initialTime = previewTime == null ? 0 : ((previewTime % LOOP) + LOOP) % LOOP;
  const pose = useSharedValue(reduced ? 0.5 : previewTime == null ? 0 : envelope(initialTime));
  const [observation, setObservation] = useState(0);
  useAnimatedReaction(() => pose.value > 0.7 ? 2 : pose.value < 0.3 ? 0 : 1, (band, previous) => {
    if (band !== previous) scheduleOnRN(setObservation, band);
  });
  const clock = useSharedValue(initialTime);
  const mode = useSharedValue(0);
  const selection = useSharedValue(0);
  const dragStart = useSharedValue(0);
  const returnFrom = useSharedValue(0);
  const returnElapsed = useSharedValue(0);
  const detent = useSharedValue(false);
  const [range, setRangeState] = useState(0);
  const selectionHaptic = useCallback(() => { Haptics.selectionAsync(); }, []);
  const detentHaptic = useCallback(() => { Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); }, []);
  const clearManual = useCallback(() => setManualActive(false), []);

  useEffect(() => {
    const sub = AppState.addEventListener('change', state => { const visible = state === 'active'; setActive(visible); if (visible) setManualActive(false); });
    return () => sub.remove();
  }, []);
  const frame = useFrameCallback(info => {
    const dt = Math.max(0, (info.timeSincePreviousFrame ?? 0) / 1000);
    if (mode.value === 2) {
      returnElapsed.value += dt;
      if (returnElapsed.value < 2) pose.value = returnFrom.value;
      else {
        const t = (returnElapsed.value - 2) / 0.45;
        pose.value = returnFrom.value * (1 - smooth(t));
        if (t >= 1) {
          pose.value = previewTime == null ? 0 : envelope(((previewTime % LOOP) + LOOP) % LOOP);
          clock.value = previewTime == null ? 0 : ((previewTime % LOOP) + LOOP) % LOOP;
          mode.value = 0;
          if (!autoPlay || previewTime != null) scheduleOnRN(clearManual);
        }
      }
      return;
    }
    if (mode.value === 0 && !autoPlay) { scheduleOnRN(clearManual); return; }
    if (mode.value === 0 && autoPlay && previewTime == null) {
      clock.value = (clock.value + dt) % LOOP;
      pose.value = envelope(clock.value);
    }
  }, false);

  useEffect(() => {
    scheduleOnUI(resetPrism, pose, clock, mode, previewTime, reduced);
    scheduleOnRN(clearManual);
  }, [active, autoPlay, clearManual, clock, mode, pose, previewTime, reduced]);
  useEffect(() => {
    frame.setActive(active && !reduced && (previewTime == null || manualActive) && (autoPlay || manualActive));
  }, [active, autoPlay, frame, manualActive, previewTime, reduced]);

  const begin = useCallback(() => setManualActive(true), []);
  const pan = useMemo(() => Gesture.Pan().minDistance(1)
    .onStart(() => {
      if (reduced) return;
      beginScrub(pose, dragStart, mode, detent);
      scheduleOnRN(begin);
    })
    .onUpdate(event => {
      if (reduced) return;
      if (updateScrub(pose, dragStart, mode, detent, event.translationX / scale)) scheduleOnRN(detentHaptic);
    })
    .onFinalize(() => {
      if (reduced || mode.value !== 1) return;
      releaseScrub(pose, returnFrom, returnElapsed, mode);
    }), [begin, detent, detentHaptic, dragStart, mode, pose, reduced, returnElapsed, returnFrom, scale]);

  const accessibleSet = useCallback((value: number) => {
    if (reduced) return;
    setManualActive(true);
    scheduleOnUI(setScrub, pose, returnFrom, returnElapsed, mode, value);
    selectionHaptic();
  }, [mode, pose, reduced, returnElapsed, returnFrom, selectionHaptic]);
  const selectRange = useCallback((next: number) => {
    setRangeState(next);
    scheduleOnUI(chooseChartRange, selection, pose, returnFrom, returnElapsed, mode, next, reduced);
    if (!reduced) setManualActive(true);
    selectionHaptic();
  }, [mode, pose, reduced, returnElapsed, returnFrom, selection, selectionHaptic]);
  const resetView = useCallback(() => {
    setRangeState(0);
    scheduleOnUI(chooseChartRange, selection, pose, returnFrom, returnElapsed, mode, 0, reduced);
    if (!reduced) setManualActive(true);
    selectionHaptic();
  }, [mode, pose, reduced, returnElapsed, returnFrom, selection, selectionHaptic]);
  const basePath = useMemo(() => makeChartPathForSelection(range), [range]);
  const lensClipProps = useLensCircleProps(pose, selection);

  return (
    <View style={styles.root}>
      <StatusBar style="dark" />
      <View style={[styles.stageOuter, { top: safe.top, bottom: safe.bottom }]}>
        <View style={[styles.stage, { transform: [{ scale }] }]}>
          <View style={styles.header}>
            <Text style={styles.waveIcon}>≋</Text>
            <Text style={styles.headerTitle}>PRISM / OBSERVATORY</Text>
            <Text style={styles.brand}>NOVA</Text>
          </View>
          <Text style={styles.title}>See the{`\n`}whole picture.</Text>
          <Text style={styles.subtitle}>A closer look at your daily index.</Text>
          <GestureDetector gesture={pan}>
            <Animated.View accessible accessibilityRole="adjustable" accessibilityValue={{ text: ['Monday observation', 'Midweek observation', 'Friday observation'][observation] }} accessibilityLabel="Prism Window Chart" accessibilityHint="Scrub the lens horizontally" accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]} onAccessibilityAction={event => accessibleSet(event.nativeEvent.actionName === 'increment' ? 1 : 0)} style={styles.artwork}>
              <Svg width={337} height={330} viewBox="0 0 337 330">
                <Defs>
                  <LinearGradient id="chartPanel" x1="0" y1="0" x2="337" y2="290" gradientUnits="userSpaceOnUse">
                    <Stop offset="0%" stopColor="#FFFFFF" stopOpacity={0.85} />
                    <Stop offset="100%" stopColor="#CDD4FD" stopOpacity={0.7} />
                  </LinearGradient>
                  <LinearGradient id="lensGlass" x1="0" y1="0" x2="1" y2="1">
                    <Stop offset="0%" stopColor="#FFFFFF" stopOpacity={0.85} />
                    <Stop offset="100%" stopColor="#DDD9FF" stopOpacity={0.8} />
                  </LinearGradient>
                  <LinearGradient id="lensRim" x1="0" y1="0" x2="1" y2="1">
                    <Stop offset="0%" stopColor="#FFFFFF" />
                    <Stop offset="48%" stopColor="#8CE0E2" />
                    <Stop offset="100%" stopColor={ACCENT} stopOpacity={0.6} />
                  </LinearGradient>
                  <ClipPath id="lensClip"><AnimatedSvgCircle animatedProps={lensClipProps} r={53} /></ClipPath>
                </Defs>
                <Rect x={0} y={15} width={337} height={275} rx={28} fill="url(#chartPanel)" stroke="#FFFFFF" strokeWidth={1} />
                {[0, 1, 2, 3].map(i => <Path key={i} d={`M 24 ${70 + i * 48} H 313`} fill="none" stroke="rgba(104,91,238,0.09)" strokeWidth={1} />)}
                <Path d={basePath} fill="none" stroke="rgba(104,91,238,0.45)" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" />
                <MovingLens pose={pose} selection={selection} chartPath={basePath} />
                <SvgText x={168.5} y={40} fill="rgba(104,91,238,0.65)" textAnchor="middle" fontFamily="sans-serif" fontSize={9} letterSpacing={0.7}>OBSERVATION WINDOW</SvgText>
                {['MON', 'TUE', 'WED', 'THU', 'FRI'].map((day, i) => <SvgText key={day} x={35 + i * 66} y={263} fill="rgba(39,40,50,0.5)" textAnchor="middle" fontFamily="sans-serif" fontSize={8}>{day}</SvgText>)}
                <SvgText x={168.5} y={318} fill={INK} textAnchor="middle" fontFamily="sans-serif" fontSize={24} fontWeight="600">128.6</SvgText>
              </Svg>
            </Animated.View>
          </GestureDetector>
          <View style={styles.ranges}>
            {['Week', 'Month', 'Year'].map((name, index) => <Pressable key={name} onPress={() => selectRange(index)} accessibilityRole="button" accessibilityLabel={`${name} range`} accessibilityState={{ selected: range === index }} style={[styles.rangeButton, range === index && styles.rangeSelected]}><Text style={[styles.rangeLabel, range === index && styles.rangeSelectedLabel]}>{name}</Text></Pressable>)}
          </View>
          <Pressable onPress={resetView} accessibilityRole="button" accessibilityLabel="Reset view" style={({ pressed }) => [styles.reset, pressed && (reduced ? styles.resetReducedPressed : styles.resetPressed)]}>
            <Text style={styles.resetIcon}>↗</Text><Text style={styles.resetLabel}>Reset view</Text>
          </Pressable>
          <Text style={styles.footer}>SCRUB THE LENS HORIZONTALLY</Text>
        </View>
      </View>
    </View>
  );
}

function useLensCircleProps(pose: SharedValue<number>, selection: SharedValue<number>) {
  return useAnimatedProps(() => {
    const x = 74 + pose.value * 208;
    const y = sample((x - 24) / 289, selection.value);
    return { cx: x, cy: y };
  });
}
function useCursorPathProps(pose: SharedValue<number>, selection: SharedValue<number>) {
  return useAnimatedProps(() => {
    const x = 74 + pose.value * 208;
    const y = sample((x - 24) / 289, selection.value);
    return { d: `M ${x} ${y + 56} L ${x} 247` };
  });
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#E9ECFF' },
  stageOuter: { position: 'absolute', left: 0, right: 0, alignItems: 'center', justifyContent: 'center' },
  stage: { width: 393, height: 760, overflow: 'visible' },
  header: { position: 'absolute', left: 28, top: 20, width: 337, height: 24, flexDirection: 'row', alignItems: 'center' },
  waveIcon: { color: INK, fontSize: 17, marginRight: 8 },
  headerTitle: { color: INK, fontSize: 10, fontWeight: '600', letterSpacing: 1.8 },
  brand: { color: 'rgba(39,40,50,0.55)', fontSize: 10, fontFamily: 'monospace', marginLeft: 'auto' },
  title: { position: 'absolute', top: 77, width: 393, color: INK, fontSize: 38, fontWeight: '600', letterSpacing: -1.2, lineHeight: 41, textAlign: 'center' },
  subtitle: { position: 'absolute', top: 180, width: 393, color: 'rgba(39,40,50,0.6)', fontSize: 13, textAlign: 'center' },
  artwork: { position: 'absolute', left: 28, top: 220, width: 337, height: 330 },
  ranges: { position: 'absolute', left: 36.5, top: 568, width: 320, height: 46, flexDirection: 'row', gap: 8 },
  rangeButton: { width: 104, height: 46, borderRadius: 15, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(255,255,255,0.6)' },
  rangeSelected: { backgroundColor: ACCENT },
  rangeLabel: { color: INK, fontSize: 12, fontWeight: '500' },
  rangeSelectedLabel: { color: '#FFFFFF' },
  reset: { position: 'absolute', left: 28, top: 653, width: 337, height: 54, borderRadius: 18, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 9, backgroundColor: ACCENT },
  resetPressed: { transform: [{ scale: 0.97 }], opacity: 0.8 },
  resetReducedPressed: { opacity: 0.8 },
  resetIcon: { color: '#FFFFFF', fontSize: 15 },
  resetLabel: { color: '#FFFFFF', fontSize: 15, fontWeight: '600' },
  footer: { position: 'absolute', top: 731, width: 393, color: 'rgba(39,40,50,0.45)', fontSize: 9, fontWeight: '500', letterSpacing: 1.6, textAlign: 'center' },
});
