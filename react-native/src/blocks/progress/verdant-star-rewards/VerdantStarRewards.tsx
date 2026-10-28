import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';
import Animated, { useAnimatedProps, useAnimatedReaction, useFrameCallback, useSharedValue, type SharedValue } from 'react-native-reanimated';
import Svg, { Circle, Defs, Ellipse, LinearGradient as SvgLinearGradient, Path, Polygon, RadialGradient, Stop, Text as SvgText } from 'react-native-svg';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';

export const LOOP = 8;
const ACCENT = '#B2FF52';
const INK = '#F1F6E8';
const CENTER = { x: 168.5, y: 155 };

const clamp = (value: number, lo: number, hi: number) => { 'worklet'; return Math.max(lo, Math.min(hi, value)); };
const smooth = (value: number) => { 'worklet'; const u = clamp(value, 0, 1); return u * u * (3 - 2 * u); };
const envelope = (time: number) => { 'worklet'; return smooth((time - 1.4) / 1.8) * (1 - smooth((time - 4.8) / 1.8)); };

type Props = { autoPlay?: boolean; previewTime?: number };

function resetRewards(pose: SharedValue<number>, clock: SharedValue<number>, mode: SharedValue<number>, previewTime: number | undefined, reduced: boolean) {
  'worklet';
  const time = previewTime == null ? 0 : ((previewTime % LOOP) + LOOP) % LOOP;
  clock.value = time;
  mode.value = 0;
  pose.value = reduced ? 1 : previewTime == null ? 0 : envelope(time);
}
function beginGather(pose: SharedValue<number>, start: SharedValue<number>, mode: SharedValue<number>, detent: SharedValue<boolean>) {
  'worklet'; start.value = pose.value; mode.value = 1; detent.value = false;
}
function moveGather(pose: SharedValue<number>, start: SharedValue<number>, mode: SharedValue<number>, detent: SharedValue<boolean>, translationY: number) {
  'worklet';
  mode.value = 1;
  const next = clamp(start.value - translationY / 180, 0, 1);
  pose.value = next;
  const snapped = next > 0.85 && !detent.value;
  if (snapped) detent.value = true;
  return { next, snapped };
}
function releaseGather(pose: SharedValue<number>, from: SharedValue<number>, elapsed: SharedValue<number>, mode: SharedValue<number>) {
  'worklet'; from.value = pose.value; elapsed.value = 0; mode.value = 2;
}
function claimGather(pose: SharedValue<number>, from: SharedValue<number>, elapsed: SharedValue<number>, mode: SharedValue<number>) {
  'worklet'; pose.value = 1; from.value = 1; elapsed.value = 0; mode.value = 2;
}
function accessibleGather(pose: SharedValue<number>, from: SharedValue<number>, elapsed: SharedValue<number>, mode: SharedValue<number>, value: number) {
  'worklet'; pose.value = value; from.value = value; elapsed.value = 0; mode.value = 2;
}

function starPoint(index: number) {
  'worklet';
  const angle = index * Math.PI / 5 - Math.PI / 2;
  const radius = index % 2 === 0 ? 99 : 42.57;
  return { x: CENTER.x + Math.cos(angle) * radius, y: CENTER.y + Math.sin(angle) * radius };
}
function particleTarget(index: number) {
  'worklet';
  const edge = index / 72 * 10;
  const vertex = Math.floor(edge);
  const mix = edge - vertex;
  const a = starPoint(vertex);
  const b = starPoint(vertex + 1);
  return { x: a.x + (b.x - a.x) * mix, y: a.y + (b.y - a.y) * mix };
}
function Particle({ index, pose }: { index: number; pose: SharedValue<number> }) {
  const props = useAnimatedProps(() => {
    const angle = index * 2.399963;
    const target = particleTarget(index);
    const radius = 112 + (index % 7) * 4;
    const sx = CENTER.x + Math.cos(angle) * radius;
    const sy = CENTER.y + Math.sin(angle) * radius * 0.79;
    const q = smooth(Math.min(1, pose.value * (1.05 + (index % 5) * 0.03)));
    const curl = Math.sin(q * Math.PI) * 22;
    return {
      cx: sx + (target.x - sx) * q + Math.cos(angle + Math.PI / 2) * curl,
      cy: sy + (target.y - sy) * q + Math.sin(angle + Math.PI / 2) * curl,
    };
  });
  const radius = 1.4 + (index % 4) * 0.55;
  return <AnimatedSvgCircle animatedProps={props} r={radius} fill={index % 3 === 0 ? '#EEFFC6' : 'rgba(178,255,82,0.65)'} />;
}

const AnimatedSvgCircle = Animated.createAnimatedComponent(Circle);
const AnimatedSvgEllipse = Animated.createAnimatedComponent(Ellipse);
const AnimatedSvgPolygon = Animated.createAnimatedComponent(Polygon);
const AnimatedSvgPath = Animated.createAnimatedComponent(Path);

function OrbitRing({ index, pose }: { index: number; pose: SharedValue<number> }) {
  const props = useAnimatedProps(() => ({ strokeOpacity: 0.1 * (1 - pose.value) }));
  return <AnimatedSvgEllipse animatedProps={props} cx={168.5} cy={155} rx={144.5 - index * 14} ry={101 - index * 16} fill="none" stroke={ACCENT} strokeWidth={1} />;
}
function AssembledStar({ pose }: { pose: SharedValue<number> }) {
  const props = useAnimatedProps(() => ({ opacity: pose.value, strokeOpacity: pose.value * 0.8 }));
  return <AnimatedSvgPolygon animatedProps={props} points={Array.from({ length: 10 }, (_, i) => { const pt = starPoint(i); return `${pt.x},${pt.y}`; }).join(' ')} fill="url(#rewardStar)" stroke="#E5FFB7" strokeWidth={1.5} />;
}
function StarFacet({ index, pose }: { index: number; pose: SharedValue<number> }) {
  const props = useAnimatedProps(() => ({ opacity: (index % 2 === 0 ? 0.24 : 0.06) * pose.value }));
  const outer = starPoint(index * 2);
  const inner = starPoint(index * 2 + 1);
  return <AnimatedSvgPath animatedProps={props} d={`M ${CENTER.x} ${CENTER.y} L ${outer.x} ${outer.y} L ${inner.x} ${inner.y} Z`} fill="#FFFFFF" />;
}
function RewardHalo({ pose }: { pose: SharedValue<number> }) {
  const props = useAnimatedProps(() => ({ opacity: 0.625 + pose.value * 0.375 }));
  return <AnimatedSvgCircle animatedProps={props} cx={168.5} cy={155} r={132} fill="url(#rewardHalo)" />;
}

function RewardArtwork({ pose, activated }: { pose: SharedValue<number>; activated: boolean }) {
  return <Svg width={337} height={330} viewBox="0 0 337 330">
    <Defs>
      <SvgLinearGradient id="rewardStar" x1="110" y1="60" x2="230" y2="250" gradientUnits="userSpaceOnUse">
        <Stop offset="0%" stopColor="#E3FFA5" />
        <Stop offset="55%" stopColor={ACCENT} />
        <Stop offset="100%" stopColor="#347414" />
      </SvgLinearGradient>
      <RadialGradient id="rewardHalo" cx="50%" cy="50%" r="50%">
        <Stop offset="0%" stopColor={ACCENT} stopOpacity={0.4} />
        <Stop offset="100%" stopColor={ACCENT} stopOpacity={0} />
      </RadialGradient>
    </Defs>
    {[0, 1, 2].map(ring => <OrbitRing key={ring} index={ring} pose={pose} />)}
    <RewardHalo pose={pose} />
    <AssembledStar pose={pose} />
    {Array.from({ length: 5 }, (_, i) => <StarFacet key={i} index={i} pose={pose} />)}
    {Array.from({ length: 72 }, (_, i) => <Particle key={i} index={i} pose={pose} />)}
    <SvgText x={168.5} y={292} fill={ACCENT} textAnchor="middle" fontFamily="sans-serif" fontSize={12} fontWeight="600">{activated ? 'REWARD COLLECTED' : '150 POINTS'}</SvgText>
  </Svg>;
}

export function VerdantStarRewards({ autoPlay = false, previewTime }: Props) {
  const { width, height } = useWindowDimensions();
  const safe = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const [active, setActive] = useState(AppState.currentState === 'active');
  const [manualActive, setManualActive] = useState(false);
  const [activated, setActivated] = useState(false);
  const scale = Math.min(width / 393, Math.max(0, height - safe.top - safe.bottom) / 760);
  const initialTime = previewTime == null ? 0 : ((previewTime % LOOP) + LOOP) % LOOP;
  const [assembled, setAssembled] = useState(reduced || envelope(initialTime) > 0.7);
  const pose = useSharedValue(reduced ? 1 : previewTime == null ? 0 : envelope(initialTime));
  const clock = useSharedValue(initialTime);
  const mode = useSharedValue(0);
  const dragStart = useSharedValue(0);
  const returnFrom = useSharedValue(0);
  const returnElapsed = useSharedValue(0);
  const detent = useSharedValue(false);
  const clearManual = useCallback(() => setManualActive(false), []);
  const haptic = useCallback(() => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light), []);
  const selectionHaptic = useCallback(() => Haptics.selectionAsync(), []);

  useAnimatedReaction(() => pose.value > 0.7, (next, previous) => {
    if (next !== previous) scheduleOnRN(setAssembled, next);
  });

  useEffect(() => {
    const subscription = AppState.addEventListener('change', state => {
      const visible = state === 'active';
      setActive(visible);
      if (visible) setManualActive(false);
    });
    return () => subscription.remove();
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
    scheduleOnUI(resetRewards, pose, clock, mode, previewTime, reduced);
    scheduleOnRN(clearManual);
  }, [active, autoPlay, clearManual, clock, mode, pose, previewTime, reduced]);
  useEffect(() => {
    frame.setActive(active && !reduced && (previewTime == null || manualActive) && (autoPlay || manualActive));
  }, [active, autoPlay, frame, manualActive, previewTime, reduced]);

  const begin = useCallback(() => setManualActive(true), []);
  const detentHaptic = useCallback(() => { haptic(); }, [haptic]);
  const pan = useMemo(() => Gesture.Pan().minDistance(1)
    .onStart(() => {
      if (reduced) return;
      beginGather(pose, dragStart, mode, detent);
      scheduleOnRN(begin);
    })
    .onUpdate(event => {
      if (reduced) return;
      const result = moveGather(pose, dragStart, mode, detent, event.translationY / scale);
      if (result.snapped) scheduleOnRN(detentHaptic);
    })
    .onFinalize(() => {
      if (reduced || mode.value !== 1) return;
      releaseGather(pose, returnFrom, returnElapsed, mode);
  }), [begin, detent, detentHaptic, dragStart, mode, pose, reduced, returnElapsed, returnFrom, scale]);

  const accessSet = useCallback((value: number) => {
    if (reduced) return;
    setManualActive(true);
    scheduleOnUI(accessibleGather, pose, returnFrom, returnElapsed, mode, value);
    selectionHaptic();
  }, [mode, pose, reduced, returnElapsed, returnFrom, selectionHaptic]);

  const activate = useCallback(() => {
    setActivated(true);
    setManualActive(true);
    scheduleOnUI(claimGather, pose, returnFrom, returnElapsed, mode);
    selectionHaptic();
  }, [mode, pose, returnElapsed, returnFrom, selectionHaptic]);

  return (
    <View style={styles.root}>
      <StatusBar style="light" />
      <View style={[styles.stageOuter, { top: safe.top, bottom: safe.bottom }]}>
        <View style={[styles.stage, { transform: [{ scale }] }]}>
          <View style={StyleSheet.absoluteFill} />
          <View style={styles.header}>
            <Text style={styles.sparkle}>✧</Text>
            <Text style={styles.kicker}>HABIT / REWARDS</Text>
            <Text style={styles.day}>DAY 03</Text>
          </View>
          <Text style={styles.title}>Good days{`\n`}add up.</Text>
          <Text style={styles.subtitle}>Your daily reward is ready.</Text>
          <GestureDetector gesture={pan}>
            <Animated.View accessible accessibilityRole="adjustable" accessibilityLabel="Verdant Star Rewards" accessibilityValue={{ text: assembled ? 'Star assembled' : 'Gather the reward star' }} accessibilityHint="Drag upward to gather" accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]} onAccessibilityAction={event => accessSet(event.nativeEvent.actionName === 'increment' ? 1 : 0)} style={styles.artwork}>
              <RewardArtwork pose={pose} activated={activated} />
            </Animated.View>
          </GestureDetector>
          <View style={styles.detail}>
            {Array.from({ length: 7 }, (_, i) => {
              const current = i === 2;
              const claimed = i < 2 || (current && activated);
              return <View key={i} style={[styles.dayCell, current && styles.dayCellCurrent]}>
                <Text style={[styles.dayNumber, current && styles.dayNumberCurrent]}>{i + 1}</Text>
                <Text style={[styles.dayMark, current && styles.dayNumberCurrent]}>{claimed ? '✓' : '✧'}</Text>
              </View>;
            })}
          </View>
          <Pressable onPress={activate} accessibilityRole="button" accessibilityLabel={activated ? '150 points collected' : 'Claim 150 points'} style={({ pressed }) => [styles.cta, pressed && (reduced ? styles.ctaReducedPressed : styles.ctaPressed)]}>
            <Text style={styles.ctaIcon}>{activated ? '✓' : '↗'}</Text>
            <Text style={styles.ctaLabel}>{activated ? '150 points collected' : 'Claim 150 points'}</Text>
          </Pressable>
          <Text style={styles.footer}>DRAG UPWARD TO GATHER</Text>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#12170D' },
  stageOuter: { position: 'absolute', left: 0, right: 0, alignItems: 'center', justifyContent: 'center' },
  stage: { width: 393, height: 760, overflow: 'visible' },
  header: { position: 'absolute', left: 28, top: 20, width: 337, height: 24, flexDirection: 'row', alignItems: 'center' },
  sparkle: { color: INK, fontSize: 15, marginRight: 8 },
  kicker: { color: INK, fontSize: 10, fontWeight: '600', letterSpacing: 1.8 },
  day: { color: 'rgba(241,246,232,0.55)', fontSize: 10, fontFamily: 'monospace', marginLeft: 'auto' },
  title: { position: 'absolute', top: 77, width: 393, color: INK, fontSize: 38, fontWeight: '600', letterSpacing: -1.2, lineHeight: 41, textAlign: 'center' },
  subtitle: { position: 'absolute', top: 180, width: 393, color: 'rgba(241,246,232,0.6)', fontSize: 13, textAlign: 'center' },
  artwork: { position: 'absolute', left: 28, top: 220, width: 337, height: 330 },
  detail: { position: 'absolute', left: 28, top: 565, width: 337, height: 52, flexDirection: 'row', justifyContent: 'center', gap: 6 },
  dayCell: { width: 41, height: 52, borderRadius: 12, alignItems: 'center', justifyContent: 'center', gap: 7, backgroundColor: 'rgba(241,246,232,0.05)' },
  dayCellCurrent: { backgroundColor: ACCENT },
  dayNumber: { color: 'rgba(241,246,232,0.45)', fontSize: 10 },
  dayMark: { color: 'rgba(241,246,232,0.45)', fontSize: 13 },
  dayNumberCurrent: { color: '#18200E' },
  cta: { position: 'absolute', left: 28, top: 653, width: 337, height: 54, borderRadius: 18, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 9, backgroundColor: ACCENT },
  ctaPressed: { transform: [{ scale: 0.97 }], opacity: 0.8 },
  ctaReducedPressed: { opacity: 0.8 },
  ctaIcon: { color: '#18200E', fontSize: 15 },
  ctaLabel: { color: '#18200E', fontSize: 15, fontWeight: '600' },
  footer: { position: 'absolute', top: 731, width: 393, color: 'rgba(241,246,232,0.45)', fontSize: 9, fontWeight: '500', letterSpacing: 1.6, textAlign: 'center' },
});
