import { StatusBar } from 'expo-status-bar';
import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import * as Haptics from 'expo-haptics';
import { LinearGradient as ExpoLinearGradient } from 'expo-linear-gradient';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, Platform, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';
import Animated, { useAnimatedProps, useAnimatedReaction, useAnimatedStyle, useFrameCallback, useSharedValue, type SharedValue } from 'react-native-reanimated';
import Svg, { Circle, ClipPath, Defs, G, LinearGradient, Path, RadialGradient, Stop, Text as SvgText } from 'react-native-svg';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

const LOOP = 8;
const CREAM = '#F0F5E4';
const SERIF = Platform.OS === 'ios' ? 'Georgia' : 'serif';
const clamp = (value: number, low: number, high: number) => { 'worklet'; return Math.max(low, Math.min(high, value)); };
const smooth = (value: number) => { 'worklet'; const u = clamp(value, 0, 1); return u * u * (3 - 2 * u); };
const envelope = (time: number) => { 'worklet'; return smooth((time - 1.5) / 1.8) * (1 - smooth((time - 4.7) / 1.8)); };

type Props = { autoPlay?: boolean; previewTime?: number };

function resetGauge(pose: SharedValue<number>, clock: SharedValue<number>, mode: SharedValue<number>, previewTime: number | undefined, reduced: boolean) {
  'worklet';
  const time = previewTime == null ? 0 : ((previewTime % LOOP) + LOOP) % LOOP;
  clock.value = time;
  mode.value = 0;
  pose.value = reduced ? 0.5 : previewTime == null ? 0 : envelope(time);
}
function beginGaugeDrag(pose: SharedValue<number>, start: SharedValue<number>, mode: SharedValue<number>, crossed: SharedValue<boolean>) {
  'worklet'; start.value = pose.value; mode.value = 1; crossed.value = false;
}
function updateGaugeDrag(pose: SharedValue<number>, start: SharedValue<number>, mode: SharedValue<number>, crossed: SharedValue<boolean>, translationX: number) {
  'worklet';
  mode.value = 1;
  const value = clamp(start.value + translationX / 180, 0, 1);
  pose.value = value;
  const changed = !crossed.value && (start.value - 0.5) * (value - 0.5) <= 0;
  if (changed) crossed.value = true;
  return changed;
}
function releaseGauge(pose: SharedValue<number>, from: SharedValue<number>, elapsed: SharedValue<number>, mode: SharedValue<number>) {
  'worklet'; from.value = pose.value; elapsed.value = 0; mode.value = 2;
}
function accessibleGauge(pose: SharedValue<number>, from: SharedValue<number>, elapsed: SharedValue<number>, mode: SharedValue<number>, value: number) {
  'worklet'; pose.value = value; from.value = value; elapsed.value = 0; mode.value = 2;
}

const AnimatedSvgCircle = Animated.createAnimatedComponent(Circle);
const AnimatedSvgPath = Animated.createAnimatedComponent(Path);
const AnimatedSvgRadialGradient = Animated.createAnimatedComponent(RadialGradient);

function PearlFillGradient({ pose }: { pose: SharedValue<number> }) {
  const props = useAnimatedProps(() => ({ cx: `${35 + pose.value * 25}%` }));
  return <AnimatedSvgRadialGradient animatedProps={props} id="pearlFill" cy="35%" r="62%">
    <Stop offset="20%" stopColor="#8ED9BA" stopOpacity={0.1} />
    <Stop offset="72%" stopColor="#5C9F86" stopOpacity={0.3} />
    <Stop offset="97%" stopColor="#C8EED0" stopOpacity={0.7} />
    <Stop offset="100%" stopColor="#629D83" stopOpacity={0.2} />
  </AnimatedSvgRadialGradient>;
}

function PearlHighlights({ pose }: { pose: SharedValue<number> }) {
  const primary = useAnimatedProps(() => ({ cx: 156 * (0.26 + pose.value * 0.35), cy: 156 * 0.23 }));
  return <AnimatedSvgCircle animatedProps={primary} r={35} fill="url(#pearlHighlight)" />;
}
function PearlBands({ pose }: { pose: SharedValue<number> }) {
  const paths = Array.from({ length: 6 }, (_, i) => <PearlBand key={i} index={i} pose={pose} />);
  return <>{paths}</>;
}
function PearlBand({ index, pose }: { index: number; pose: SharedValue<number> }) {
  const props = useAnimatedProps(() => {
    const y = index * 156 / 5;
    const endY = y - 12 + pose.value * 24;
    const controlY = y + (index - 2.5) * 15 + pose.value * 18;
    return { d: `M 0 ${y} Q 78 ${controlY} 156 ${endY}` };
  });
  return <AnimatedSvgPath animatedProps={props} fill="none" stroke="rgba(164,226,181,0.035)" strokeWidth={8} />;
}
function GaugeArc({ pose }: { pose: SharedValue<number> }) {
  const activePath = useAnimatedProps(() => {
    const u = 0.35 + 0.3 * pose.value;
    let d = '';
    for (let i = 0; i <= 50; i++) {
      const t = 0.12 + (u - 0.12) * i / 50;
      const x = 15 + 323 * t;
      const y = 75 - 160 * t * (1 - t);
      d += `${i === 0 ? 'M' : 'L'} ${x} ${y} `;
    }
    return { d };
  });
  return <>
    <Path d="M15 75 Q176.5 -5 338 75" fill="none" stroke="rgba(240,245,228,0.12)" strokeWidth={7} strokeLinecap="round" />
    <AnimatedSvgPath animatedProps={activePath} fill="none" stroke="url(#gaugeArc)" strokeWidth={10} strokeLinecap="round" />
    <Circle cx={176.5} cy={35.5} r={3.5} fill={CREAM} />
  </>;
}
function FollowPearl({ pose }: { pose: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const u = 0.35 + 0.3 * pose.value;
    return { transform: [{ translateX: 15 + 323 * u - 23 }, { translateY: 75 - 160 * u * (1 - u) - 23 }] };
  });
  return <Animated.View pointerEvents="none" style={[styles.followPearl, style]}>
    <Svg width={46} height={46} viewBox="0 0 46 46">
      <Defs>
        <RadialGradient id="smallPearl" cx="35%" cy="35%" r="70%">
          <Stop offset="0%" stopColor="#C8EED0" stopOpacity={0.85} />
          <Stop offset="65%" stopColor="#5C9F86" stopOpacity={0.42} />
          <Stop offset="98%" stopColor="#C8EED0" stopOpacity={0.75} />
          <Stop offset="100%" stopColor="#629D83" stopOpacity={0.3} />
        </RadialGradient>
      </Defs>
      <Circle cx={23} cy={23} r={22} fill="url(#smallPearl)" stroke="rgba(240,245,228,0.42)" strokeWidth={0.7} />
      <SvgText x={23} y={29} fill={CREAM} textAnchor="middle" fontFamily={SERIF} fontSize={18}>+6</SvgText>
    </Svg>
  </Animated.View>;
}

export function VitalityPearlGauge({ autoPlay = false, previewTime }: Props) {
  const { width, height } = useWindowDimensions();
  const safe = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const [active, setActive] = useState(AppState.currentState === 'active');
  const [manualActive, setManualActive] = useState(false);
  const scale = Math.min(width / 393, Math.max(0, height - safe.top - safe.bottom) / 760);
  const pose = useSharedValue(reduced ? 0.5 : previewTime == null ? 0 : envelope(((previewTime % LOOP) + LOOP) % LOOP));
  const clock = useSharedValue(previewTime == null ? 0 : ((previewTime % LOOP) + LOOP) % LOOP);
  const mode = useSharedValue(0);
  const dragStart = useSharedValue(0);
  const returnFrom = useSharedValue(0);
  const returnElapsed = useSharedValue(0);
  const crossed = useSharedValue(false);
  const clearManual = useCallback(() => setManualActive(false), []);
  const [balancePosition, setBalancePosition] = useState(0);
  useAnimatedReaction(
    () => pose.value < 0.33 ? 0 : pose.value > 0.67 ? 2 : 1,
    (position, previous) => { if (position !== previous) scheduleOnRN(setBalancePosition, position); },
  );
  const selection = useCallback(() => { Haptics.selectionAsync(); }, []);

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
    scheduleOnUI(resetGauge, pose, clock, mode, previewTime, reduced);
    scheduleOnRN(clearManual);
  }, [active, clearManual, clock, mode, pose, previewTime, reduced]);
  useEffect(() => {
    frame.setActive(active && !reduced && (previewTime == null || manualActive) && (autoPlay || manualActive));
  }, [active, autoPlay, frame, manualActive, previewTime, reduced]);

  const begin = useCallback(() => setManualActive(true), []);
  const cross = useCallback(() => { Haptics.selectionAsync(); }, []);
  const pan = useMemo(() => Gesture.Pan().minDistance(1)
    .onStart(() => {
      if (reduced) return;
      beginGaugeDrag(pose, dragStart, mode, crossed);
      scheduleOnRN(begin);
    })
    .onUpdate(event => {
      if (reduced) return;
      if (updateGaugeDrag(pose, dragStart, mode, crossed, event.translationX)) scheduleOnRN(cross);
    })
    .onFinalize(() => {
      if (reduced || mode.value !== 1) return;
      releaseGauge(pose, returnFrom, returnElapsed, mode);
    }), [begin, cross, crossed, dragStart, mode, pose, reduced, returnElapsed, returnFrom]);
  const accessibleValue = useCallback((value: number) => {
    if (reduced) { selection(); return; }
    setManualActive(true);
    scheduleOnUI(accessibleGauge, pose, returnFrom, returnElapsed, mode, value);
    selection();
  }, [mode, pose, reduced, returnElapsed, returnFrom, selection]);
  const heroStyle = useAnimatedStyle(() => ({ transform: [{ translateY: -8 * Math.sin(pose.value * Math.PI) }] }));

  return (
    <View style={styles.root}>
      <StatusBar style="light" />
      <ExpoLinearGradient colors={['#1C5546', '#102B22', '#101D16']} locations={[0, 0.55, 1]} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={StyleSheet.absoluteFill} />
      <View style={[styles.stageOuter, { top: safe.top, bottom: safe.bottom }]}>
        <View style={[styles.stage, { transform: [{ scale }] }]}>
          <View style={styles.header}>
            <Text style={styles.sun}>☼</Text>
            <Text style={styles.headerCopy}>DAILY RECOVERY</Text>
            <Text style={styles.today}>TODAY</Text>
          </View>
          <View style={styles.steps}>
            {Array.from({ length: 5 }, (_, i) => <View key={i} style={[styles.step, { width: i === 1 ? 35 : 9, opacity: i < 2 ? 0.65 : 0.14 }]} />)}
          </View>
          <Text style={styles.headline}>Your energy,{`\n`}coming into focus.</Text>
          <Animated.View style={[styles.mainPearl, heroStyle]}>
            <Svg width={156} height={156} viewBox="0 0 156 156">
              <Defs>
                <ClipPath id="pearlClip"><Circle cx={78} cy={78} r={77.5} /></ClipPath>
                <PearlFillGradient pose={pose} />
                <RadialGradient id="pearlHighlight" cx="50%" cy="50%" r="50%">
                  <Stop offset="0%" stopColor="#FFFFFF" stopOpacity={0.4} />
                  <Stop offset="100%" stopColor="#FFFFFF" stopOpacity={0} />
                </RadialGradient>
                <RadialGradient id="pearlGlow" cx="50%" cy="50%" r="50%">
                  <Stop offset="0%" stopColor="#CBB6EF" stopOpacity={0.08} />
                  <Stop offset="100%" stopColor="#CBB6EF" stopOpacity={0} />
                </RadialGradient>
                <LinearGradient id="pearlEdge" x1="0%" y1="0%" x2="100%" y2="100%">
                  <Stop offset="0%" stopColor={CREAM} stopOpacity={0.55} />
                  <Stop offset="50%" stopColor={CREAM} stopOpacity={0.02} />
                  <Stop offset="100%" stopColor="#A4E2B5" stopOpacity={0.45} />
                </LinearGradient>
              </Defs>
              <Circle cx={78} cy={78} r={78} fill="url(#pearlFill)" />
              <G clipPath="url(#pearlClip)"><PearlBands pose={pose} /></G>
              <PearlHighlights pose={pose} />
              <Circle cx={110} cy={118} r={27} fill="url(#pearlGlow)" />
              <Circle cx={78} cy={78} r={77.5} fill="none" stroke="url(#pearlEdge)" strokeWidth={1} />
            </Svg>
            <Text style={styles.score}>82</Text>
          </Animated.View>
          <Text style={styles.ready}>READY FOR YOUR DAY</Text>
          <GestureDetector gesture={pan}>
            <Animated.View accessible accessibilityRole="adjustable" accessibilityLabel="Recovery balance" accessibilityValue={{ text: `${['Lower', 'Centered', 'Higher'][balancePosition]} balance, recovery index 82` }} accessibilityHint="Drag to find your balance" accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]} onAccessibilityAction={event => accessibleValue(event.nativeEvent.actionName === 'increment' ? 1 : 0)} style={styles.arcHit}>
              <Svg width={353} height={126} viewBox="0 0 353 126" style={StyleSheet.absoluteFill}>
                <Defs><LinearGradient id="gaugeArc" x1="40" y1="40" x2="340" y2="80"><Stop offset="0%" stopColor="#A4E2B5" /><Stop offset="100%" stopColor="#CBB6EF" /></LinearGradient></Defs>
                <GaugeArc pose={pose} />
              </Svg>
              <FollowPearl pose={pose} />
              <Text style={styles.lower}>LOWER</Text>
              <Text style={styles.higher}>HIGHER</Text>
              <Text style={styles.current}>CURRENT</Text>
            </Animated.View>
          </GestureDetector>
          <View style={styles.stats}>
            <Stat symbol="☾" title="REST" value="7h 42m" />
            <Stat symbol="❧" title="BALANCE" value="Steady" />
          </View>
          <Text style={styles.footer}>Drag to find your balance.</Text>
          <Text style={styles.bottomLabel}>A MOMENT FOR YOURSELF</Text>
        </View>
      </View>
    </View>
  );
}

function Stat({ symbol, title, value }: { symbol: string; title: string; value: string }) {
  return <View style={styles.stat}>
    <View style={styles.statHeader}><Text style={styles.statSymbol}>{symbol}</Text><Text style={styles.statTitle}>{title}</Text></View>
    <Text style={styles.statValue}>{value}</Text>
  </View>;
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#102B22' },
  stageOuter: { position: 'absolute', left: 0, right: 0, alignItems: 'center', justifyContent: 'center' },
  stage: { width: 393, height: 760, overflow: 'visible' },
  header: { position: 'absolute', top: 18, left: 28, width: 337, height: 24, flexDirection: 'row', alignItems: 'center' },
  sun: { color: CREAM, fontSize: 14, marginRight: 8 },
  headerCopy: { color: CREAM, fontSize: 10, fontWeight: '500', letterSpacing: 2 },
  today: { color: CREAM, fontSize: 10, marginLeft: 'auto' },
  steps: { position: 'absolute', top: 77.5, left: 152.5, width: 88, height: 3, flexDirection: 'row', gap: 6 },
  step: { height: 3, borderRadius: 2, backgroundColor: CREAM },
  headline: { position: 'absolute', top: 111, left: 24, width: 345, color: CREAM, fontSize: 32, fontFamily: SERIF, lineHeight: 38, textAlign: 'center' },
  mainPearl: { position: 'absolute', left: 118.5, top: 252, width: 156, height: 156, alignItems: 'center', justifyContent: 'center', shadowColor: '#A4E2B5', shadowOpacity: 0.1, shadowRadius: 24 },
  score: { position: 'absolute', width: 156, textAlign: 'center', color: CREAM, fontFamily: SERIF, fontSize: 72, fontWeight: '400' },
  ready: { position: 'absolute', left: 0, top: 434, width: 393, color: 'rgba(240,245,228,0.5)', fontSize: 9, fontWeight: '500', letterSpacing: 2.5, textAlign: 'center' },
  arcHit: { position: 'absolute', left: 20, top: 467, width: 353, height: 126 },
  followPearl: { position: 'absolute', left: 0, top: 0, width: 46, height: 46 },
  lower: { position: 'absolute', left: 7, top: 91, color: 'rgba(240,245,228,0.4)', fontSize: 9, letterSpacing: 1, transform: [{ rotate: '-20deg' }] },
  higher: { position: 'absolute', right: 4, top: 91, color: 'rgba(240,245,228,0.4)', fontSize: 9, letterSpacing: 1, transform: [{ rotate: '20deg' }] },
  current: { position: 'absolute', left: 0, top: 66, width: 353, color: 'rgba(240,245,228,0.5)', fontSize: 9, letterSpacing: 1.3, textAlign: 'center' },
  stats: { position: 'absolute', left: 28, top: 610, width: 337, height: 70, flexDirection: 'row', gap: 14 },
  stat: { width: 161.5, height: 70, borderRadius: 20, borderWidth: 1, borderColor: 'rgba(240,245,228,0.08)', backgroundColor: 'rgba(240,245,228,0.035)', paddingHorizontal: 16, paddingTop: 10, gap: 8 },
  statHeader: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  statSymbol: { color: 'rgba(240,245,228,0.5)', fontSize: 12 },
  statTitle: { color: 'rgba(240,245,228,0.5)', fontSize: 9, letterSpacing: 1.3 },
  statValue: { color: CREAM, fontSize: 22, fontFamily: SERIF },
  footer: { position: 'absolute', top: 711, width: 393, color: 'rgba(240,245,228,0.55)', fontSize: 12, textAlign: 'center' },
  bottomLabel: { position: 'absolute', top: 742, width: 393, color: 'rgba(240,245,228,0.35)', fontSize: 8, fontWeight: '500', letterSpacing: 2, textAlign: 'center' },
});
