import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, Platform, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';
import Animated, { useAnimatedProps, useAnimatedReaction, useFrameCallback, useSharedValue, type SharedValue } from 'react-native-reanimated';
import Svg, { Circle, G, Path, Rect, Text as SvgText } from 'react-native-svg';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';

export const LOOP = 8;
const INK = '#272832';
const SERIF = Platform.OS === 'ios' ? 'Georgia' : 'serif';
const ACCENT = '#731C38';
const AnimatedSvgGroup = Animated.createAnimatedComponent(G);
const AnimatedSvgPath = Animated.createAnimatedComponent(Path);
const AnimatedSvgCircle = Animated.createAnimatedComponent(Circle);
const clamp = (v: number, lo: number, hi: number) => { 'worklet'; return Math.max(lo, Math.min(hi, v)); };
const smooth = (v: number) => { 'worklet'; const u = clamp(v, 0, 1); return u * u * (3 - 2 * u); };
const envelope = (t: number) => { 'worklet'; return smooth((t - 1.4) / 1.8) * (1 - smooth((t - 4.8) / 1.8)); };
type Props = { autoPlay?: boolean; previewTime?: number };

function resetPose(pose: SharedValue<number>, clock: SharedValue<number>, mode: SharedValue<number>, previewTime: number | undefined, reduced: boolean) {
  'worklet'; const t = previewTime == null ? 0 : ((previewTime % LOOP) + LOOP) % LOOP;
  pose.value = reduced ? 1 : previewTime == null ? 0 : envelope(t); clock.value = t; mode.value = 0;
}
function beginDrag(pose: SharedValue<number>, start: SharedValue<number>, mode: SharedValue<number>, detent: SharedValue<boolean>) { 'worklet'; start.value = pose.value; mode.value = 1; detent.value = false; }
function updateDrag(pose: SharedValue<number>, start: SharedValue<number>, detent: SharedValue<boolean>, translationX: number) {
  'worklet'; pose.value = clamp(start.value + translationX / 180, 0, 1);
  const crossed = pose.value > 0.85 && !detent.value; if (crossed) detent.value = true; return crossed;
}
function releaseDrag(pose: SharedValue<number>, from: SharedValue<number>, elapsed: SharedValue<number>, mode: SharedValue<number>) { 'worklet'; from.value = pose.value; elapsed.value = 0; mode.value = 2; }
function setPose(pose: SharedValue<number>, from: SharedValue<number>, elapsed: SharedValue<number>, mode: SharedValue<number>, value: number) { 'worklet'; pose.value = value; from.value = value; elapsed.value = 0; mode.value = 2; }
function rotation(degrees: number, cx: number, cy: number) {
  'worklet'; const r = degrees * Math.PI / 180; const a = Math.cos(r); const b = Math.sin(r);
  return [a, b, -b, a, cx - a * cx + b * cy, cy - b * cx - a * cy];
}

function threadPath(p: number, highlighted: boolean) {
  'worklet';
  const knotX = 243 + 40 * p;
  const knotY = 40 - 12 * p;
  let d = '';
  for (let i = 0; i < 24; i++) {
    if ((i % 3 === 0) !== highlighted) continue;
    const n = i - 11.5;
    const x = knotX + n * (1.25 + 1.3 * p);
    const y = 4 + Math.sin(i * 0.7) * 5;
    d += `M ${knotX} ${knotY} Q ${knotX + n * 0.3 + 19 * p} 19 ${x} ${y} `;
  }
  return d;
}

function TasselArtwork({ pose }: { pose: SharedValue<number> }) {
  const backingProps = useAnimatedProps(() => ({ opacity: 1, matrix: rotation(-7 + pose.value * 1.5, 168, 190) }));
  const paperProps = useAnimatedProps(() => ({ opacity: 1, matrix: rotation(2 + pose.value * 1.5, 168, 190) }));
  const threadsProps = useAnimatedProps(() => ({ d: threadPath(pose.value, false) }));
  const highlightProps = useAnimatedProps(() => ({ d: threadPath(pose.value, true) }));
  const cordProps = useAnimatedProps(() => {
    const p = pose.value; const x = 243 + 40 * p; const y = 40 - 12 * p;
    return { d: `M 243 82 Q ${268 + 34 * p} ${59 + 12 * p} ${x} ${y}` };
  });
  const knotProps = useAnimatedProps(() => ({ cx: 243 + 40 * pose.value, cy: 40 - 12 * pose.value }));
  return <Svg width={337} height={330} viewBox="0 0 337 330">
    <AnimatedSvgGroup animatedProps={backingProps}>
      <Rect x={56} y={64} width={225} height={262} rx={3} fill="#000000" opacity={0.09} />
      <Rect x={56} y={55} width={225} height={262} rx={3} fill={ACCENT} />
    </AnimatedSvgGroup>
    <AnimatedSvgGroup animatedProps={paperProps}>
      <Rect x={58} y={75} width={220} height={233} rx={3} fill="#FFFCF5" stroke="rgba(255,255,255,0.7)" />
      {Array.from({ length: 12 }, (_, i) => <Circle key={i} cx={69 + i * 18} cy={304} r={4} fill={ACCENT} />)}
      <SvgText x={168} y={146} fill={INK} textAnchor="middle" fontFamily="sans-serif" fontSize={36} fontWeight="600">GIFT</SvgText>
      <SvgText x={168} y={174} fill={ACCENT} textAnchor="middle" fontFamily="sans-serif" fontSize={9} fontWeight="700" letterSpacing={0.6}>FOR A LITTLE MOMENT</SvgText>
      <SvgText x={166} y={219} fill="rgba(39,40,50,0.7)" textAnchor="middle" fontFamily="sans-serif" fontSize={10}>TO     Someone wonderful</SvgText>
      <Path d="M 81 235 H 254" stroke="rgba(39,40,50,0.15)" strokeWidth={1} />
      <SvgText x={168} y={266} fill="rgba(39,40,50,0.55)" textAnchor="middle" fontFamily="sans-serif" fontSize={9}>A keepsake, chosen with care.</SvgText>
      <SvgText x={168} y={292} fill={ACCENT} textAnchor="middle" fontFamily="sans-serif" fontSize={8} letterSpacing={0.5}>CERTIFICATE     0148</SvgText>
    </AnimatedSvgGroup>
    <Circle cx={243} cy={82} r={7} fill="none" stroke="#A56C41" strokeWidth={3} />
    <AnimatedSvgPath animatedProps={cordProps} fill="none" stroke={ACCENT} strokeWidth={4} strokeLinecap="round" />
    <AnimatedSvgPath animatedProps={threadsProps} fill="none" stroke={ACCENT} strokeWidth={0.85} strokeLinecap="round" />
    <AnimatedSvgPath animatedProps={highlightProps} fill="none" stroke="#B64460" strokeWidth={1.3} strokeLinecap="round" />
    <AnimatedSvgCircle animatedProps={knotProps} r={5} fill={ACCENT} />
  </Svg>;
}

export function TasselGiftCertificate({ autoPlay = false, previewTime }: Props) {
  const { width, height } = useWindowDimensions(); const safe = useSafeAreaInsets(); const reduced = useReducedMotionPreference();
  const [active, setActive] = useState(AppState.currentState === 'active');
  const [manualActive, setManualActive] = useState(false); const [activated, setActivated] = useState(false);
  const scale = Math.min(width / 393, Math.max(0, height - safe.top - safe.bottom) / 760);
  const [expanded, setExpanded] = useState(false);
  const initialTime = previewTime == null ? 0 : ((previewTime % LOOP) + LOOP) % LOOP;
  const pose = useSharedValue(reduced ? 1 : previewTime == null ? 0 : envelope(initialTime)); const clock = useSharedValue(initialTime);
  const mode = useSharedValue(0); const dragStart = useSharedValue(0); const returnFrom = useSharedValue(0); const returnElapsed = useSharedValue(0);
  const detent = useSharedValue(false);
  useAnimatedReaction(() => pose.value > 0.7, (value, previous) => { if (value !== previous) scheduleOnRN(setExpanded, value); });
  const detentImpact = useCallback(() => { Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); }, []);
  const clearManual = useCallback(() => setManualActive(false), []);
  const frame = useFrameCallback(info => {
    const dt = Math.max(0, (info.timeSincePreviousFrame ?? 0) / 1000);
    if (mode.value === 2) {
      returnElapsed.value += dt;
      if (returnElapsed.value < 2) pose.value = returnFrom.value;
      else { const t = (returnElapsed.value - 2) / 0.45; pose.value = returnFrom.value * (1 - smooth(t)); if (t >= 1) { pose.value = previewTime == null ? 0 : envelope(((previewTime % LOOP) + LOOP) % LOOP); clock.value = previewTime == null ? 0 : ((previewTime % LOOP) + LOOP) % LOOP; mode.value = 0; if (!autoPlay || previewTime != null) scheduleOnRN(clearManual); } }
      return;
    }
    if (mode.value === 0 && !autoPlay) { scheduleOnRN(clearManual); return; }
    if (mode.value === 0 && autoPlay && previewTime == null) { clock.value = (clock.value + dt) % LOOP; pose.value = envelope(clock.value); }
  }, false);
  useEffect(() => { const sub = AppState.addEventListener('change', s => { const shown = s === 'active'; setActive(shown); if (shown) setManualActive(false); }); return () => sub.remove(); }, []);
  useEffect(() => { scheduleOnUI(resetPose, pose, clock, mode, previewTime, reduced); scheduleOnRN(clearManual); }, [active, autoPlay, clearManual, clock, mode, pose, previewTime, reduced]);
  useEffect(() => { frame.setActive(active && !reduced && (previewTime == null || manualActive) && (autoPlay || manualActive)); }, [active, autoPlay, frame, manualActive, previewTime, reduced]);
  const begin = useCallback(() => setManualActive(true), []);
  const pan = useMemo(() => Gesture.Pan().minDistance(1).onStart(() => { if (reduced) return; beginDrag(pose, dragStart, mode, detent); scheduleOnRN(begin); }).onUpdate(e => { if (!reduced && updateDrag(pose, dragStart, detent, e.translationX / scale)) scheduleOnRN(detentImpact); }).onFinalize(() => { if (!reduced && mode.value === 1) releaseDrag(pose, returnFrom, returnElapsed, mode); }), [begin, detent, detentImpact, dragStart, mode, pose, reduced, returnElapsed, returnFrom, scale]);
  const adjust = useCallback((value: number) => { if (reduced) return; setManualActive(true); scheduleOnUI(setPose, pose, returnFrom, returnElapsed, mode, value); Haptics.selectionAsync(); }, [mode, pose, reduced, returnElapsed, returnFrom]);
  const save = useCallback(() => { setActivated(true); if (!reduced) { setManualActive(true); scheduleOnUI(setPose, pose, returnFrom, returnElapsed, mode, 1); } Haptics.selectionAsync(); }, [mode, pose, reduced, returnElapsed, returnFrom]);
  return <View style={styles.root}>
    <StatusBar style="dark" />
    <View style={[styles.stageOuter, { top: safe.top, bottom: safe.bottom }]}>
      <View style={[styles.stage, { transform: [{ scale }] }]}>
        <View style={styles.header}><Text style={styles.giftIcon}>✧</Text><Text style={styles.kicker}>ATELIER / A SMALL GIFT</Text><Text style={styles.code}>0148</Text></View>
        <Text style={styles.title}>Something{`\n`}just for you.</Text>
        <Text style={styles.subtitle}>A keepsake for a moment of your own.</Text>
        <GestureDetector gesture={pan}>
          <Animated.View accessible accessibilityRole="adjustable" accessibilityLabel="Tassel Gift Certificate" accessibilityHint="Drag sideways on the tassel" accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]} onAccessibilityAction={e => adjust(e.nativeEvent.actionName === 'increment' ? 1 : 0)} accessibilityValue={{ text: expanded ? 'Tassel fanned' : 'Tassel ready to pull' }} style={styles.artwork}>
            <TasselArtwork pose={pose} />
          </Animated.View>
        </GestureDetector>
        <View style={styles.detail}><Text style={styles.detailTitle}>{activated ? 'Saved to your keepsakes' : 'A small gesture. A lasting memory.'}</Text><Text style={styles.detailMeta}>THOUGHTFULLY CHOSEN  /  NO. 0148</Text></View>
        <Pressable onPress={save} accessibilityRole="button" accessibilityLabel={activated ? 'Saved to keepsakes' : 'Save keepsake'} style={({ pressed }) => [styles.cta, pressed && (reduced ? styles.ctaReducedPressed : styles.ctaPressed)]}>
          <Text style={styles.ctaLabel}>{activated ? '✓  Saved to keepsakes' : '↗  Save keepsake'}</Text>
        </Pressable>
        <Text style={styles.footer}>DRAG SIDEWAYS ON THE TASSEL</Text>
      </View>
    </View>
  </View>;
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#EEE8E1' }, stageOuter: { position: 'absolute', left: 0, right: 0, alignItems: 'center', justifyContent: 'center' }, stage: { width: 393, height: 760, overflow: 'visible' },
  header: { position: 'absolute', left: 28, top: 20, width: 337, height: 24, flexDirection: 'row', alignItems: 'center' }, giftIcon: { fontSize: 15, marginRight: 8, color: INK }, kicker: { color: INK, fontSize: 10, fontWeight: '600', letterSpacing: 1.8 }, code: { marginLeft: 'auto', color: 'rgba(39,40,50,0.55)', fontFamily: 'monospace', fontSize: 10 },
  title: { position: 'absolute', top: 79, width: 393, color: INK, fontFamily: SERIF, fontSize: 38, fontWeight: '600', letterSpacing: -1.2, lineHeight: 41, textAlign: 'center' }, subtitle: { position: 'absolute', top: 180, width: 393, color: 'rgba(39,40,50,0.6)', fontSize: 13, textAlign: 'center' }, artwork: { position: 'absolute', left: 28, top: 220, width: 337, height: 330 },
  detail: { position: 'absolute', top: 574, width: 393, alignItems: 'center', gap: 8 }, detailTitle: { color: INK, fontFamily: SERIF, fontSize: 17 }, detailMeta: { color: 'rgba(39,40,50,0.5)', fontSize: 8, letterSpacing: 1.7 },
  cta: { position: 'absolute', left: 28, top: 653, width: 337, height: 54, borderRadius: 18, alignItems: 'center', justifyContent: 'center', backgroundColor: ACCENT }, ctaLabel: { color: '#FFFFFF', fontSize: 15, fontWeight: '600' }, ctaPressed: { transform: [{ scale: 0.97 }], opacity: 0.8 }, ctaReducedPressed: { opacity: 0.8 }, footer: { position: 'absolute', top: 731, width: 393, color: 'rgba(39,40,50,0.45)', fontSize: 9, fontWeight: '500', letterSpacing: 1.6, textAlign: 'center' },
});
