import * as Haptics from 'expo-haptics';
import { LinearGradient as ExpoLinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';
import Animated, { useAnimatedProps, useAnimatedReaction, useFrameCallback, useSharedValue, type SharedValue } from 'react-native-reanimated';
import Svg, { Circle, Defs, Ellipse, LinearGradient, Path, RadialGradient, Rect, Stop, Text as SvgText } from 'react-native-svg';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';

export const LOOP = 8;
const ACCENT = '#6655F5';
const INK = '#272832';
const clamp = (value: number, low: number, high: number) => { 'worklet'; return Math.max(low, Math.min(high, value)); };
const smooth = (value: number) => { 'worklet'; const u = clamp(value, 0, 1); return u * u * (3 - 2 * u); };
const envelope = (time: number) => { 'worklet'; return smooth((time - 1.4) / 1.8) * (1 - smooth((time - 4.8) / 1.8)); };

type Props = { autoPlay?: boolean; previewTime?: number };

function resetMoth(pose: SharedValue<number>, clock: SharedValue<number>, mode: SharedValue<number>, previewTime: number | undefined, reduced: boolean) {
  'worklet';
  const time = previewTime == null ? 0 : ((previewTime % LOOP) + LOOP) % LOOP;
  pose.value = reduced ? 1 : previewTime == null ? 0 : envelope(time);
  clock.value = time;
  mode.value = 0;
}
function beginMothDrag(pose: SharedValue<number>, start: SharedValue<number>, mode: SharedValue<number>, detent: SharedValue<boolean>) {
  'worklet'; start.value = pose.value; mode.value = 1; detent.value = false;
}
function updateMothDrag(pose: SharedValue<number>, start: SharedValue<number>, mode: SharedValue<number>, detent: SharedValue<boolean>, translationY: number) {
  'worklet';
  pose.value = clamp(start.value - translationY / 180, 0, 1);
  mode.value = 1;
  const snapped = pose.value > 0.85 && !detent.value;
  if (snapped) detent.value = true;
  return snapped;
}
function releaseMoth(pose: SharedValue<number>, from: SharedValue<number>, elapsed: SharedValue<number>, mode: SharedValue<number>) {
  'worklet'; from.value = pose.value; elapsed.value = 0; mode.value = 2;
}
function setMothPose(pose: SharedValue<number>, from: SharedValue<number>, elapsed: SharedValue<number>, mode: SharedValue<number>, value: number) {
  'worklet'; pose.value = value; from.value = value; elapsed.value = 0; mode.value = 2;
}

const AnimatedSvgEllipse = Animated.createAnimatedComponent(Ellipse);
const AnimatedSvgPath = Animated.createAnimatedComponent(Path);

function MothWing({ side, lower, pose }: { side: number; lower?: boolean; pose: SharedValue<number> }) {
  const props = useAnimatedProps(() => {
    const spread = 0.42 + pose.value * 0.58;
    if (lower) {
      return { d: `M 168.5 157 Q ${168.5 + side * 58 * spread} 178 ${168.5 + side * 48 * spread} 209 Q ${168.5 + side * 7 * spread} 204 168.5 170 Z` };
    }
    return {
      d: `M 168.5 155 C ${168.5 + side * 22 * spread} 124 ${168.5 + side * 53 * spread} 109 ${168.5 + side * 70 * spread} ${92 - pose.value * 4} C ${168.5 + side * 68 * spread} 157 ${168.5 + side * 53 * spread} 170 ${168.5 + side * 25 * spread} 174 Z`,
    };
  });
  return <AnimatedSvgPath animatedProps={props} fill={lower ? '#E7E1FF' : '#FFFFFF'} />;
}
function MothVein({ side, pose }: { side: number; pose: SharedValue<number> }) {
  const props = useAnimatedProps(() => {
    const spread = 0.42 + pose.value * 0.58;
    return { d: `M 168.5 155 L ${168.5 + side * 54 * spread} 114` };
  });
  return <AnimatedSvgPath animatedProps={props} fill="none" stroke="rgba(102,85,245,0.25)" strokeWidth={1} />;
}

function MothArtwork({ pose, activated }: { pose: SharedValue<number>; activated: boolean }) {
  const halo = useAnimatedProps(() => ({ rx: 129.5 + pose.value * 9, ry: 129.5 + pose.value * 9, opacity: 0.47 + pose.value * 0.53 }));
  return <Svg width={337} height={330} viewBox="0 0 337 330" style={styles.mothCanvas}>
    <Defs>
      <RadialGradient id="mothHalo" cx="50%" cy="50%" r="50%">
        <Stop offset="0%" stopColor={ACCENT} stopOpacity={0.17} />
        <Stop offset="100%" stopColor={ACCENT} stopOpacity={0} />
      </RadialGradient>
      <LinearGradient id="mothDisc" x1="80" y1="50" x2="255" y2="260" gradientUnits="userSpaceOnUse">
        <Stop offset="0%" stopColor="#A99BFF" />
        <Stop offset="56%" stopColor={ACCENT} />
        <Stop offset="100%" stopColor="#5145DC" />
      </LinearGradient>
    </Defs>
    <AnimatedSvgEllipse animatedProps={halo} cx={168.5} cy={155} fill="url(#mothHalo)" />
    <Circle cx={168.5} cy={155} r={105} fill="url(#mothDisc)" stroke="rgba(255,255,255,0.65)" strokeWidth={1.5} />
    <MothWing side={-1} pose={pose} />
    <MothWing side={1} pose={pose} />
    <MothWing side={-1} lower pose={pose} />
    <MothWing side={1} lower pose={pose} />
    <MothVein side={-1} pose={pose} />
    <MothVein side={1} pose={pose} />
    <Path d="M 168.5 137 Q 149.5 134 150.5 117 M 168.5 137 Q 187.5 134 186.5 117" fill="none" stroke="#FFFFFF" strokeWidth={1.5} />
    <Rect x={165.5} y={133} width={6} height={48} rx={3} fill="#FFFFFF" />
    <SvgText x={168.5} y={304} fill={ACCENT} textAnchor="middle" fontFamily="sans-serif" fontSize={10} fontWeight="600">{activated ? 'CONNECTED' : 'READY WHEN YOU ARE'}</SvgText>
  </Svg>;
}

export function MothLinkBloom({ autoPlay = false, previewTime }: Props) {
  const { width, height } = useWindowDimensions();
  const safe = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const [active, setActive] = useState(AppState.currentState === 'active');
  const [manualActive, setManualActive] = useState(false);
  const [activated, setActivated] = useState(false);
  const [selection, setSelection] = useState(0);
  const scale = Math.min(width / 393, Math.max(0, height - safe.top - safe.bottom) / 760);
  const [expanded, setExpanded] = useState(false);
  const initialTime = previewTime == null ? 0 : ((previewTime % LOOP) + LOOP) % LOOP;
  const pose = useSharedValue(reduced ? 1 : previewTime == null ? 0 : envelope(initialTime));
  const clock = useSharedValue(initialTime);
  const mode = useSharedValue(0);
  const dragStart = useSharedValue(0);
  const returnFrom = useSharedValue(0);
  const returnElapsed = useSharedValue(0);
  const detent = useSharedValue(false);
  useAnimatedReaction(() => pose.value > 0.7, (value, previous) => { if (value !== previous) scheduleOnRN(setExpanded, value); });
  const clearManual = useCallback(() => setManualActive(false), []);
  const detentImpact = useCallback(() => { Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); }, []);
  const selectionHaptic = useCallback(() => Haptics.selectionAsync(), []);

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
    scheduleOnUI(resetMoth, pose, clock, mode, previewTime, reduced);
    scheduleOnRN(clearManual);
  }, [active, autoPlay, clearManual, clock, mode, pose, previewTime, reduced]);
  useEffect(() => {
    frame.setActive(active && !reduced && (previewTime == null || manualActive) && (autoPlay || manualActive));
  }, [active, autoPlay, frame, manualActive, previewTime, reduced]);

  const begin = useCallback(() => setManualActive(true), []);
  const pan = useMemo(() => Gesture.Pan().minDistance(1)
    .onStart(() => {
      if (reduced) return;
      beginMothDrag(pose, dragStart, mode, detent);
      scheduleOnRN(begin);
    })
    .onUpdate(event => {
      if (reduced) return;
      if (updateMothDrag(pose, dragStart, mode, detent, event.translationY / scale)) scheduleOnRN(detentImpact);
    })
    .onFinalize(() => {
      if (reduced || mode.value !== 1) return;
      releaseMoth(pose, returnFrom, returnElapsed, mode);
    }), [begin, detent, detentImpact, dragStart, mode, pose, reduced, returnElapsed, returnFrom, scale]);

  const accessSet = useCallback((value: number) => {
    if (reduced) return;
    setManualActive(true);
    scheduleOnUI(setMothPose, pose, returnFrom, returnElapsed, mode, value);
    selectionHaptic();
  }, [mode, pose, reduced, returnElapsed, returnFrom, selectionHaptic]);
  const selectServer = useCallback((index: number) => {
    setSelection(index);
    setManualActive(true);
    scheduleOnUI(releaseMoth, pose, returnFrom, returnElapsed, mode);
    selectionHaptic();
  }, [mode, pose, returnElapsed, returnFrom, selectionHaptic]);
  const connect = useCallback(() => {
    const next = !activated;
    setActivated(next);
    if (reduced) {
      selectionHaptic();
      return;
    }
    setManualActive(true);
    scheduleOnUI(setMothPose, pose, returnFrom, returnElapsed, mode, next ? 1 : 0);
    selectionHaptic();
  }, [activated, mode, pose, reduced, returnElapsed, returnFrom, selectionHaptic]);

  return (
    <View style={styles.root}>
      <StatusBar style="dark" />
      <View style={[styles.stageOuter, { top: safe.top, bottom: safe.bottom }]}>
        <View style={[styles.stage, { transform: [{ scale }] }]}>
          <ExpoLinearGradient colors={['#F6F5FF', '#F6F5FF']} style={StyleSheet.absoluteFill} />
          <View style={styles.header}>
            <Text style={styles.network}>⌘</Text>
            <Text style={styles.headerTitle}>LUMA / PRIVATE LINK</Text>
            <Text style={styles.private}>PRIVATE</Text>
          </View>
          <Text style={styles.title}>A quieter{`\n`}connection.</Text>
          <Text style={styles.subtitle}>One touch. A little more peace.</Text>
          <GestureDetector gesture={pan}>
            <Animated.View accessible accessibilityRole="adjustable" accessibilityLabel="Moth Link Bloom" accessibilityValue={{ text: expanded ? 'Wings spread' : 'Wings folded' }} accessibilityHint="Drag upward to spread wings" accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]} onAccessibilityAction={event => accessSet(event.nativeEvent.actionName === 'increment' ? 1 : 0)} style={styles.artwork}>
              <MothArtwork pose={pose} activated={activated} />
            </Animated.View>
          </GestureDetector>
          <View style={styles.servers}>
            {[0, 1].map(index => {
              const selected = selection === index;
              return <Pressable key={index} onPress={() => selectServer(index)} accessibilityRole="button" accessibilityLabel={index === 0 ? 'North server, 24 milliseconds' : 'West server, 38 milliseconds'} accessibilityState={{ selected }} style={[styles.serverCard, selected && styles.serverSelected]}>
                <Text style={[styles.serverName, selected && styles.serverNameSelected]}>{index === 0 ? 'North / 24 ms' : 'West / 38 ms'}</Text>
                <Text style={[styles.serverStatus, selected && styles.serverNameSelected]}>{selected ? 'SELECTED' : 'AVAILABLE'}</Text>
              </Pressable>;
            })}
          </View>
          <Pressable onPress={connect} accessibilityRole="button" accessibilityLabel={activated ? 'Disconnect' : 'Connect'} style={({ pressed }) => [styles.connect, pressed && (reduced ? styles.connectReducedPressed : styles.connectPressed)]}>
            <Text style={styles.connectIcon}>{activated ? '✓' : '↗'}</Text>
            <Text style={styles.connectLabel}>{activated ? 'Disconnect' : 'Connect'}</Text>
          </Pressable>
          <Text style={styles.footer}>DRAG UPWARD TO SPREAD WINGS</Text>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#F6F5FF' },
  stageOuter: { position: 'absolute', left: 0, right: 0, alignItems: 'center', justifyContent: 'center' },
  stage: { width: 393, height: 760, overflow: 'visible' },
  header: { position: 'absolute', left: 28, top: 20, width: 337, height: 24, flexDirection: 'row', alignItems: 'center' },
  network: { color: INK, fontSize: 15, marginRight: 8 },
  headerTitle: { color: INK, fontSize: 10, fontWeight: '600', letterSpacing: 1.8 },
  private: { color: 'rgba(39,40,50,0.55)', fontSize: 10, fontFamily: 'monospace', marginLeft: 'auto' },
  title: { position: 'absolute', top: 77, width: 393, color: INK, fontSize: 38, fontWeight: '600', letterSpacing: -1.2, lineHeight: 41, textAlign: 'center' },
  subtitle: { position: 'absolute', top: 180, width: 393, color: 'rgba(39,40,50,0.6)', fontSize: 13, textAlign: 'center' },
  artwork: { position: 'absolute', left: 28, top: 220, width: 337, height: 330 },
  mothCanvas: { shadowColor: ACCENT, shadowOpacity: 0.2, shadowRadius: 14, shadowOffset: { width: 0, height: 12 }, elevation: 10 },
  servers: { position: 'absolute', left: 28, top: 559, width: 337, height: 64, flexDirection: 'row', gap: 10 },
  serverCard: { width: 163.5, height: 64, borderRadius: 18, alignItems: 'center', justifyContent: 'center', gap: 7, backgroundColor: 'rgba(255,255,255,0.7)' },
  serverSelected: { backgroundColor: 'rgba(102,85,245,0.12)' },
  serverName: { color: INK, fontSize: 12, fontWeight: '600' },
  serverNameSelected: { color: ACCENT },
  serverStatus: { color: 'rgba(39,40,50,0.55)', fontSize: 8, letterSpacing: 1.2 },
  connect: { position: 'absolute', left: 28, top: 653, width: 337, height: 54, borderRadius: 18, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 9, backgroundColor: ACCENT },
  connectPressed: { transform: [{ scale: 0.97 }], opacity: 0.8 },
  connectReducedPressed: { opacity: 0.8 },
  connectIcon: { color: '#FFFFFF', fontSize: 15 },
  connectLabel: { color: '#FFFFFF', fontSize: 15, fontWeight: '600' },
  footer: { position: 'absolute', top: 731, width: 393, color: 'rgba(39,40,50,0.45)', fontSize: 9, fontWeight: '500', letterSpacing: 1.6, textAlign: 'center' },
});
