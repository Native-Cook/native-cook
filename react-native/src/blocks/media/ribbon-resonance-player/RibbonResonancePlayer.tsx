import { StatusBar } from 'expo-status-bar';
import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import * as Haptics from 'expo-haptics';
import { LinearGradient as ExpoLinearGradient } from 'expo-linear-gradient';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';
import Animated, { useAnimatedProps, useAnimatedStyle, useFrameCallback, useSharedValue, type SharedValue } from 'react-native-reanimated';
import Svg, { Circle, Defs, LinearGradient, Path, RadialGradient, Rect, Stop } from 'react-native-svg';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

const LOOP = 8;
const CREAM = '#E8E2FF';
const clamp = (value: number, min: number, max: number) => { 'worklet'; return Math.max(min, Math.min(max, value)); };
const smooth = (value: number) => { 'worklet'; const u = clamp(value, 0, 1); return u * u * (3 - 2 * u); };
const envelope = (time: number) => { 'worklet'; return smooth((time - 1.4) / 1.8) * (1 - smooth((time - 4.6) / 1.8)); };
const point = (theta: number, radius: number) => { 'worklet'; return { x: 180 + Math.cos(theta) * radius, y: 180 + Math.sin(theta) * radius }; };

type Props = { autoPlay?: boolean; previewTime?: number };

function resetPlayer(pose: SharedValue<number>, clock: SharedValue<number>, mode: SharedValue<number>, pausedAt: SharedValue<number>, previewTime: number | undefined, reduced: boolean) {
  'worklet';
  const time = previewTime == null ? 0 : ((previewTime % LOOP) + LOOP) % LOOP;
  clock.value = time;
  pausedAt.value = 0;
  pose.value = reduced ? 0 : previewTime == null ? 0 : envelope(time);
  mode.value = 0;
}
function finishManual(pose: SharedValue<number>, from: SharedValue<number>, elapsed: SharedValue<number>, mode: SharedValue<number>) {
  'worklet'; from.value = pose.value; elapsed.value = 0; mode.value = 2;
}
function beginBend(pose: SharedValue<number>, start: SharedValue<number>, mode: SharedValue<number>) {
  'worklet'; start.value = pose.value; mode.value = 1;
}
function updateBend(pose: SharedValue<number>, start: SharedValue<number>, mode: SharedValue<number>, translationX: number) {
  'worklet'; pose.value = clamp(start.value + translationX / 200, 0, 1); mode.value = 1;
}
function accessibleBend(pose: SharedValue<number>, from: SharedValue<number>, elapsed: SharedValue<number>, mode: SharedValue<number>, value: number) {
  'worklet'; pose.value = value; from.value = value; elapsed.value = 0; mode.value = 2;
}
function toggleClock(clock: SharedValue<number>, pausedAt: SharedValue<number>, wasPlaying: boolean) {
  'worklet'; if (wasPlaying) pausedAt.value = clock.value; else clock.value = pausedAt.value;
}
function RingString({ index, pose }: { index: number; pose: SharedValue<number> }) {
  const props = useAnimatedProps(() => {
    const a = index * 2 * Math.PI / 64;
    const startRadius = 63 / Math.max(Math.abs(Math.cos(a)), Math.abs(Math.sin(a)));
    const begin = point(a, startRadius);
    const theta = a + pose.value * 0.32 * Math.sin(a);
    const radius = 148 + pose.value * 18 * Math.sin(3 * a);
    const end = point(theta, radius);
    const middleX = (begin.x + end.x) / 2 - pose.value * 36 * Math.sin(2 * a) * Math.sin(a);
    const middleY = (begin.y + end.y) / 2 + pose.value * 36 * Math.sin(2 * a) * Math.cos(a);
    return { d: `M ${begin.x} ${begin.y} Q ${middleX} ${middleY} ${end.x} ${end.y}` };
  });
  return <AnimatedSvgPath animatedProps={props} fill="none" stroke="rgba(6,30,58,0.5)" strokeWidth={0.8} />;
}
function RingEndpoint({ index, pose }: { index: number; pose: SharedValue<number> }) {
  const props = useAnimatedProps(() => {
    const a = index * 2 * Math.PI / 64;
    const theta = a + pose.value * 0.32 * Math.sin(a);
    const radius = 148 + pose.value * 18 * Math.sin(3 * a);
    const end = point(theta, radius);
    return { cx: end.x, cy: end.y };
  });
  return <AnimatedSvgCircle animatedProps={props} r={2} fill="#130E37" />;
}
function CoverWave({ row, pose }: { row: number; pose: SharedValue<number> }) {
  const props = useAnimatedProps(() => {
    let d = '';
    for (let j = 0; j <= 60; j++) {
      const x = j * 1.4 + 21;
      const envelope = Math.sin(j / 60 * Math.PI) ** 2;
      const y = row * 4 + 47 + Math.sin(j * 0.36 + row * 0.32) * envelope * (6 + 4 * pose.value);
      d += `${j === 0 ? 'M' : 'L'} ${x} ${y} `;
    }
    return { d };
  });
  return <AnimatedSvgPath animatedProps={props} fill="none" stroke="rgba(133,109,244,0.6)" strokeWidth={1} />;
}

const AnimatedSvgPath = Animated.createAnimatedComponent(Path);
const AnimatedSvgCircle = Animated.createAnimatedComponent(Circle);

export function RibbonResonancePlayer({ autoPlay = false, previewTime }: Props) {
  const { width, height } = useWindowDimensions();
  const safe = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const [active, setActive] = useState(AppState.currentState === 'active');
  const [playing, setPlaying] = useState(true);
  const [manualActive, setManualActive] = useState(false);
  const [seconds, setSeconds] = useState(744);
  const scale = Math.min(width / 393, Math.max(0, height - safe.top - safe.bottom) / 760);
  const pose = useSharedValue(reduced ? 0 : previewTime == null ? 0 : envelope(((previewTime % LOOP) + LOOP) % LOOP));
  const clock = useSharedValue(previewTime == null ? 0 : ((previewTime % LOOP) + LOOP) % LOOP);
  const pausedAt = useSharedValue(0);
  const mode = useSharedValue(0); // loop, touch, return
  const dragStart = useSharedValue(0);
  const returnFrom = useSharedValue(0);
  const returnElapsed = useSharedValue(0);
  const hapticTouch = useCallback(() => { Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); }, []);
  const hapticSelection = useCallback(() => { Haptics.selectionAsync(); }, []);
  const clearManual = useCallback(() => setManualActive(false), []);

  useEffect(() => {
    const sub = AppState.addEventListener('change', state => {
      const visible = state === 'active';
      setActive(visible);
      if (visible) setManualActive(false);
    });
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
          pose.value = 0;
          clock.value = 0;
          pausedAt.value = 0;
          pose.value = previewTime == null ? 0 : envelope(((previewTime % LOOP) + LOOP) % LOOP);
          mode.value = 0;
          if (!autoPlay || !playing || previewTime != null) scheduleOnRN(clearManual);
        }
      }
      return;
    }
    if (mode.value === 0 && (!autoPlay || !playing)) {
      scheduleOnRN(clearManual);
      return;
    }
    if (mode.value === 0 && autoPlay && playing && previewTime == null) {
      clock.value = (clock.value + dt) % LOOP;
      pose.value = envelope(clock.value);
    }
  }, false);

  useEffect(() => {
    scheduleOnUI(resetPlayer, pose, clock, mode, pausedAt, previewTime, reduced);
    scheduleOnRN(clearManual);
  }, [active, clearManual, clock, mode, pausedAt, pose, previewTime, reduced]);
  useEffect(() => {
    frame.setActive(active && !reduced && (previewTime == null || manualActive) && ((autoPlay && playing) || manualActive));
  }, [active, autoPlay, frame, manualActive, playing, previewTime, reduced]);

  const beginTouch = useCallback(() => setManualActive(true), []);
  const bend = useMemo(() => Gesture.Pan().minDistance(1)
    .onStart(() => {
      if (reduced) return;
      beginBend(pose, dragStart, mode);
      scheduleOnRN(beginTouch);
      scheduleOnRN(hapticTouch);
    })
    .onUpdate(event => {
      if (reduced) return;
      updateBend(pose, dragStart, mode, event.translationX);
    })
    .onFinalize(() => {
      if (reduced || mode.value !== 1) return;
      finishManual(pose, returnFrom, returnElapsed, mode);
  }), [beginTouch, dragStart, hapticTouch, mode, pose, reduced, returnElapsed, returnFrom]);
  const coverStyle = useAnimatedStyle(() => ({ transform: [{ rotateZ: `${pose.value * 12}deg` }] }));
  const interrupt = useCallback(() => {
    setManualActive(true);
    scheduleOnUI(finishManual, pose, returnFrom, returnElapsed, mode);
    hapticSelection();
  }, [hapticSelection, mode, pose, returnElapsed, returnFrom]);
  const seekBy = useCallback((delta: number) => {
    setSeconds(value => clamp(value + delta, 0, 2280));
    interrupt();
  }, [interrupt]);
  const togglePlay = useCallback(() => {
    scheduleOnUI(toggleClock, clock, pausedAt, playing);
    setPlaying(value => !value);
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
  }, [clock, pausedAt, playing]);
  const accessibleSetBend = useCallback((value: number) => {
    setManualActive(true);
    scheduleOnUI(accessibleBend, pose, returnFrom, returnElapsed, mode, value);
    hapticSelection();
  }, [hapticSelection, mode, pose, returnElapsed, returnFrom]);

  return (
    <View style={styles.root}>
      <StatusBar style="light" />
      <ExpoLinearGradient colors={['#160D3B', '#231858', '#29205F']} locations={[0, 0.5, 1]} start={{ x: 0.5, y: 0 }} end={{ x: 0.5, y: 1 }} style={StyleSheet.absoluteFill} />
      <View style={[styles.stageOuter, { top: safe.top, bottom: safe.bottom }]}>
        <View style={[styles.stage, { transform: [{ scale }] }]}>
          <View style={styles.header}>
            <Text style={styles.headerWave}>≋</Text>
            <Text style={styles.headerTitle}>RESONANCE / NOW PLAYING</Text>
            <Text style={styles.headerMore}>•••</Text>
          </View>
          <GestureDetector gesture={bend}>
            <Animated.View accessible accessibilityRole="adjustable" accessibilityLabel="Resonance strings" accessibilityHint="Drag right to bend the strings" accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]} onAccessibilityAction={event => { if (event.nativeEvent.actionName === 'increment') accessibleSetBend(1); else if (event.nativeEvent.actionName === 'decrement') accessibleSetBend(0); }} style={styles.hero}>
              <Svg width={360} height={360} viewBox="0 0 360 360" style={StyleSheet.absoluteFill}>
                <Defs>
                  <RadialGradient id="resonanceGlow" cx="50%" cy="50%" rx="50%" ry="50%">
                    <Stop offset="0%" stopColor="#2DE4CB" stopOpacity={1} />
                    <Stop offset="17.6%" stopColor="#2DE4CB" stopOpacity={1} />
                    <Stop offset="45.1%" stopColor="#0C9AAB" stopOpacity={0.65} />
                    <Stop offset="72.5%" stopColor="#3D4DB9" stopOpacity={0.25} />
                    <Stop offset="100%" stopColor="#0C9AAB" stopOpacity={0} />
                  </RadialGradient>
                </Defs>
                <Circle cx={180} cy={180} r={170} fill="url(#resonanceGlow)" />
                {Array.from({ length: 64 }, (_, index) => <RingString key={`s-${index}`} index={index} pose={pose} />)}
                {Array.from({ length: 64 }, (_, index) => <RingEndpoint key={`e-${index}`} index={index} pose={pose} />)}
              </Svg>
              <Animated.View style={[styles.cover, coverStyle]}>
                <Svg width={126} height={126} viewBox="0 0 126 126">
                  <Defs><LinearGradient id="coverGlow" x1="0%" y1="0%" x2="100%" y2="100%"><Stop offset="0%" stopColor="#140D37" /><Stop offset="100%" stopColor="#241358" /></LinearGradient></Defs>
                  <Rect x={0.5} y={0.5} width={125} height={125} fill="url(#coverGlow)" stroke="rgba(117,102,192,0.25)" />
                  {Array.from({ length: 9 }, (_, row) => <CoverWave key={row} row={row} pose={pose} />)}
                </Svg>
              </Animated.View>
            </Animated.View>
          </GestureDetector>
          <View style={styles.trackCopy}>
            <Text style={styles.artist}>Morrow Radio</Text>
            <Text style={styles.trackTitle}>The space between</Text>
          </View>
          <View style={styles.progressBlock}>
            <View style={styles.progressTrack}>
              <View style={[styles.progressFill, { width: `${seconds / 2280 * 100}%` }]} />
              <View style={[styles.progressKnob, { left: `${seconds / 2280 * 100}%` }]} />
            </View>
            <View style={styles.times}>
              <Text style={styles.time}>{`${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`}</Text>
              <Text style={styles.time}>38:00</Text>
            </View>
          </View>
          <View style={styles.controls}>
            <Pressable onPress={() => seekBy(-15)} accessibilityRole="button" accessibilityLabel="Back 15 seconds" style={styles.skipButton}><Text style={styles.skipIcon}>↶15</Text></Pressable>
            <Pressable onPress={togglePlay} accessibilityRole="button" accessibilityLabel={playing ? 'Pause' : 'Play'} style={styles.playButton}><Text style={styles.playIcon}>{playing ? 'Ⅱ' : '▶'}</Text></Pressable>
            <Pressable onPress={() => seekBy(15)} accessibilityRole="button" accessibilityLabel="Forward 15 seconds" style={styles.skipButton}><Text style={styles.skipIcon}>↷15</Text></Pressable>
          </View>
          <Text style={styles.footer}>DRAG TO FEEL THE RESONANCE</Text>
          <View style={styles.homeIndicator} />
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#160D3B' },
  stageOuter: { position: 'absolute', left: 0, right: 0, alignItems: 'center', justifyContent: 'center' },
  stage: { width: 393, height: 760, overflow: 'visible' },
  header: { position: 'absolute', left: 30, top: 18, width: 333, height: 24, flexDirection: 'row', alignItems: 'center' },
  headerWave: { color: CREAM, fontSize: 22, lineHeight: 24, marginRight: 8 },
  headerTitle: { color: CREAM, fontSize: 10, fontWeight: '600', letterSpacing: 1.6 },
  headerMore: { color: CREAM, fontSize: 14, letterSpacing: 2, marginLeft: 'auto', marginTop: -8 },
  hero: { position: 'absolute', left: 16.5, top: 80, width: 360, height: 360, alignItems: 'center', justifyContent: 'center' },
  cover: { position: 'absolute', width: 126, height: 126, left: 117, top: 117, shadowColor: '#000', shadowOpacity: 0.3, shadowRadius: 16, shadowOffset: { width: 0, height: 12 }, elevation: 12 },
  trackCopy: { position: 'absolute', left: 30, top: 458, width: 333, gap: 7 },
  artist: { color: 'rgba(232,226,255,0.55)', fontSize: 13 },
  trackTitle: { color: CREAM, fontSize: 24, fontWeight: '600' },
  progressBlock: { position: 'absolute', left: 30, top: 538, width: 333 },
  progressTrack: { height: 3, width: 333, borderRadius: 2, backgroundColor: 'rgba(232,226,255,0.13)' },
  progressFill: { height: 3, borderRadius: 2, backgroundColor: '#AEA1FC' },
  progressKnob: { position: 'absolute', top: -3.5, width: 10, height: 10, borderRadius: 5, marginLeft: -5, backgroundColor: CREAM },
  times: { marginTop: 10, flexDirection: 'row', justifyContent: 'space-between' },
  time: { color: 'rgba(232,226,255,0.55)', fontSize: 11, fontFamily: 'monospace' },
  controls: { position: 'absolute', left: 60.5, top: 599, width: 272, height: 62, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  skipButton: { width: 54, height: 62, alignItems: 'center', justifyContent: 'center' },
  skipIcon: { color: CREAM, fontSize: 22, fontWeight: '500' },
  playButton: { width: 112, height: 62, borderRadius: 31, alignItems: 'center', justifyContent: 'center', backgroundColor: '#AEA1FC' },
  playIcon: { color: '#231858', fontSize: 24, fontWeight: '700' },
  footer: { position: 'absolute', left: 0, top: 708, width: 393, color: 'rgba(232,226,255,0.5)', fontSize: 9, fontWeight: '500', letterSpacing: 1.8, textAlign: 'center' },
  homeIndicator: { position: 'absolute', width: 34, height: 3, borderRadius: 2, left: 179.5, top: 744, backgroundColor: 'rgba(232,226,255,0.3)' },
});
