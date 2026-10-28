import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, Platform, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';
import Animated, { useAnimatedProps, useAnimatedReaction, useFrameCallback, useSharedValue, type SharedValue } from 'react-native-reanimated';
import Svg, { Circle, ClipPath, Defs, G, LinearGradient, Path, Rect, Stop, Text as SvgText } from 'react-native-svg';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';

export const LOOP = 8;
const INK = '#272832';
const ACCENT = '#7C2544';
const AnimatedSvgGroup = Animated.createAnimatedComponent(G);
const SERIF = Platform.OS === 'ios' ? 'Georgia' : 'serif';
const clamp = (v: number, lo: number, hi: number) => { 'worklet'; return Math.max(lo, Math.min(hi, v)); };
const smooth = (v: number) => { 'worklet'; const u = clamp(v, 0, 1); return u * u * (3 - 2 * u); };
const envelope = (t: number) => { 'worklet'; return smooth((t - 1.4) / 1.8) * (1 - smooth((t - 4.8) / 1.8)); };
type Props = { autoPlay?: boolean; previewTime?: number };

function resetPearl(pose: SharedValue<number>, clock: SharedValue<number>, mode: SharedValue<number>, previewTime: number | undefined, reduced: boolean) {
  'worklet'; const t = previewTime == null ? 0 : ((previewTime % LOOP) + LOOP) % LOOP;
  pose.value = reduced ? 1 : previewTime == null ? 0 : envelope(t); clock.value = t; mode.value = 0;
}
function beginDrag(pose: SharedValue<number>, start: SharedValue<number>, mode: SharedValue<number>, detent: SharedValue<boolean>) { 'worklet'; start.value = pose.value; mode.value = 1; detent.value = false; }
function updateDrag(pose: SharedValue<number>, start: SharedValue<number>, detent: SharedValue<boolean>, translationY: number) {
  'worklet'; pose.value = clamp(start.value - translationY / 180, 0, 1);
  const crossed = pose.value > 0.85 && !detent.value; if (crossed) detent.value = true; return crossed;
}
function releaseDrag(pose: SharedValue<number>, from: SharedValue<number>, elapsed: SharedValue<number>, mode: SharedValue<number>) { 'worklet'; from.value = pose.value; elapsed.value = 0; mode.value = 2; }
function setPose(pose: SharedValue<number>, from: SharedValue<number>, elapsed: SharedValue<number>, mode: SharedValue<number>, value: number) { 'worklet'; pose.value = value; from.value = value; elapsed.value = 0; mode.value = 2; }
function lidMatrix(p: number) {
  'worklet'; const r = 4 * p * Math.PI / 180; const a = Math.cos(r); const b = Math.sin(r); const cx = 168.5; const cy = 190; const dy = -108 * p;
  return [a, b, -b, a, cx - a * cx + b * cy, cy + dy - b * cx - a * cy];
}

function PearlArtwork({ pose }: { pose: SharedValue<number> }) {
  const lidProps = useAnimatedProps(() => ({ opacity: 1, matrix: lidMatrix(pose.value) }));
  const jewelryProps = useAnimatedProps(() => ({ opacity: 1, matrix: [1, 0, 0, 1, 0, -5 * pose.value] }));
  return <Svg width={337} height={330} viewBox="0 0 337 330">
    <G transform="translate(30.3 49) scale(0.82)">
      <G transform="translate(0 7)"><Rect x={65} y={61} width={207} height={258} rx={32} fill="#000000" opacity={0.16} /></G>
      <Rect x={65} y={61} width={207} height={258} rx={32} fill="url(#pearlSilver)" stroke="#FFFFFF" strokeWidth={1.5} />
      <Rect x={76} y={72} width={185} height={234} rx={25} fill="url(#pearlInterior)" />
      <AnimatedSvgGroup animatedProps={jewelryProps}>
        <Circle cx={127} cy={276} r={22} fill="none" stroke="#DFE2DA" strokeWidth={3} />
        <Circle cx={127} cy={276} r={22} fill="none" stroke="rgba(255,255,255,0.7)" strokeWidth={1} />
        <Circle cx={183} cy={241} r={19} fill="none" stroke="#ECEEE4" strokeWidth={3} />
        {Array.from({ length: 16 }, (_, i) => {
          const a = i * Math.PI * 2 / 16; const x = 183 + Math.cos(a) * 26; const y = 241 + Math.sin(a) * 26;
          return <EllipseLeaf key={i} x={x} y={y} angle={a} />;
        })}
        <Circle cx={212} cy={279} r={6} fill="url(#pearlStud)" />
        <Circle cx={231} cy={287} r={6} fill="url(#pearlStud)" />
      </AnimatedSvgGroup>
      <AnimatedSvgGroup animatedProps={lidProps}>
        <Rect x={67} y={66} width={207} height={258} rx={32} fill="#000000" opacity={0.18} />
        <Rect x={65} y={61} width={207} height={258} rx={32} fill="url(#pearlSilver)" stroke="rgba(255,255,255,0.9)" strokeWidth={1.5} />
        <G clipPath="url(#pearlLidClip)">{Array.from({ length: 53 }, (_, i) => <Path key={i} d={`M 65 ${63 + i * 5} L 272 ${60 + i * 5}`} stroke={i % 2 === 0 ? 'rgba(255,255,255,0.12)' : 'rgba(255,255,255,0.04)'} strokeWidth={0.7} />)}</G>
        <Circle cx={168.5} cy={190} r={38} fill={ACCENT} />
        <Circle cx={168.5} cy={190} r={17} fill="none" stroke="#4D172A" strokeWidth={6} />
        <SvgText x={168.5} y={263} textAnchor="middle" fill="rgba(39,40,50,0.6)" fontSize={9} fontFamily="sans-serif">FORME</SvgText>
      </AnimatedSvgGroup>
    </G>
    <Defs>
      <ClipPath id="pearlLidClip"><Rect x={65} y={61} width={207} height={258} rx={32} /></ClipPath>
      <LinearGradient id="pearlSilver" x1="0" y1="0" x2="1" y2="1"><Stop offset="0%" stopColor="#FEFEFA"/><Stop offset="48%" stopColor="#B7BAB5"/><Stop offset="100%" stopColor="#ECEDE7"/></LinearGradient>
      <LinearGradient id="pearlInterior" x1="0" y1="0" x2="0" y2="1"><Stop offset="0%" stopColor="#565A53"/><Stop offset="100%" stopColor="#858B7F"/></LinearGradient>
      <LinearGradient id="pearlStud" x1="0" y1="0" x2="1" y2="1"><Stop offset="0%" stopColor="#FFFFFF"/><Stop offset="50%" stopColor="#BEC3BA"/><Stop offset="100%" stopColor="#737A70"/></LinearGradient>
    </Defs>
  </Svg>;
}

function EllipseLeaf({ x, y, angle }: { x: number; y: number; angle: number }) {
  return <G transform={`translate(${x} ${y}) rotate(${angle * 180 / Math.PI})`}><Rect x={-5} y={-2} width={10} height={4} rx={2} fill="url(#pearlSilver)" /></G>;
}

export function PearlJewelDrawer({ autoPlay = false, previewTime }: Props) {
  const { width, height } = useWindowDimensions(); const safe = useSafeAreaInsets(); const reduced = useReducedMotionPreference();
  const [active, setActive] = useState(AppState.currentState === 'active'); const [manualActive, setManualActive] = useState(false); const [activated, setActivated] = useState(false);
  const scale = Math.min(width / 393, Math.max(0, height - safe.top - safe.bottom) / 760); const initialTime = previewTime == null ? 0 : ((previewTime % LOOP) + LOOP) % LOOP;
  const pose = useSharedValue(reduced ? 1 : previewTime == null ? 0 : envelope(initialTime)); const clock = useSharedValue(initialTime); const mode = useSharedValue(0);
  const dragStart = useSharedValue(0); const returnFrom = useSharedValue(0); const returnElapsed = useSharedValue(0); const detent = useSharedValue(false);
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
  useEffect(() => { const sub = AppState.addEventListener('change', state => { const shown = state === 'active'; setActive(shown); if (shown) setManualActive(false); }); return () => sub.remove(); }, []);
  useEffect(() => { scheduleOnUI(resetPearl, pose, clock, mode, previewTime, reduced); scheduleOnRN(clearManual); }, [active, autoPlay, clearManual, clock, mode, pose, previewTime, reduced]);
  useEffect(() => { frame.setActive(active && !reduced && (previewTime == null || manualActive) && (autoPlay || manualActive)); }, [active, autoPlay, frame, manualActive, previewTime, reduced]);
  const begin = useCallback(() => setManualActive(true), []); const detentImpact = useCallback(() => { Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); }, []);
  const pan = useMemo(() => Gesture.Pan().minDistance(1).onStart(() => { if (reduced) return; beginDrag(pose, dragStart, mode, detent); scheduleOnRN(begin); }).onUpdate(e => { if (!reduced && updateDrag(pose, dragStart, detent, e.translationY / scale)) scheduleOnRN(detentImpact); }).onFinalize(() => { if (!reduced && mode.value === 1) releaseDrag(pose, returnFrom, returnElapsed, mode); }), [begin, detent, detentImpact, dragStart, mode, pose, reduced, returnElapsed, returnFrom, scale]);
  const adjust = useCallback((value: number) => { if (reduced) return; setManualActive(true); scheduleOnUI(setPose, pose, returnFrom, returnElapsed, mode, value); Haptics.selectionAsync(); }, [mode, pose, reduced, returnElapsed, returnFrom]);
  const open = useCallback(() => { setActivated(true); if (!reduced) { setManualActive(true); scheduleOnUI(setPose, pose, returnFrom, returnElapsed, mode, 1); } Haptics.selectionAsync(); }, [mode, pose, reduced, returnElapsed, returnFrom]);
  const [revealed, setRevealed] = useState(false);
  useAnimatedReaction(() => pose.value > 0.7, (value, previous) => {
    if (value !== previous) scheduleOnRN(setRevealed, value);
  });
  return <View style={styles.root}>
    <StatusBar style="dark" />
    <View style={[styles.stageOuter, { top: safe.top, bottom: safe.bottom }]}>
      <View style={[styles.stage, { transform: [{ scale }] }]}>
        <View style={styles.header}><Text style={styles.stackIcon}>▣</Text><Text style={styles.kicker}>FORME / LITTLE TREASURES</Text><Text style={styles.silver}>SILVER</Text></View>
        <Text style={styles.title}>Keep the{`\n`}little things.</Text>
        <Text style={styles.subtitle}>A quiet place for your favorites.</Text>
        <GestureDetector gesture={pan}>
          <Animated.View accessible accessibilityRole="adjustable" accessibilityLabel="Pearl Jewel Drawer" accessibilityHint="Drag the lid upward" accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]} onAccessibilityAction={e => adjust(e.nativeEvent.actionName === 'increment' ? 1 : 0)} accessibilityValue={{ text: revealed ? 'Three silver treasures revealed' : 'Lid closed' }} style={styles.artwork}>
            <PearlArtwork pose={pose} />
          </Animated.View>
        </GestureDetector>
        <View style={styles.detail}><View style={styles.detailItem}><Text style={styles.detailNumber}>03</Text><Text style={styles.detailLabel}>TREASURES</Text></View><View style={styles.divider}/><View style={styles.detailItem}><Text style={styles.detailNumber}>Silver</Text><Text style={styles.detailLabel}>COLLECTION</Text></View></View>
        <Pressable onPress={open} accessibilityRole="button" accessibilityLabel={activated ? 'Reveal collection' : 'Open keepsake'} style={({ pressed }) => [styles.cta, pressed && (reduced ? styles.ctaReducedPressed : styles.ctaPressed)]}>
          <Text style={styles.ctaLabel}>{activated ? '✓  Reveal collection' : '↗  Open keepsake'}</Text>
        </Pressable>
        <Text style={styles.footer}>DRAG THE LID UPWARD</Text>
      </View>
    </View>
  </View>;
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#ECEBE6' }, stageOuter: { position: 'absolute', left: 0, right: 0, alignItems: 'center', justifyContent: 'center' }, stage: { width: 393, height: 760, overflow: 'visible' },
  header: { position: 'absolute', left: 28, top: 20, width: 337, height: 24, flexDirection: 'row', alignItems: 'center' }, stackIcon: { color: INK, fontSize: 15, marginRight: 8 }, kicker: { color: INK, fontSize: 10, fontWeight: '600', letterSpacing: 1.8 }, silver: { color: 'rgba(39,40,50,0.55)', fontSize: 10, fontFamily: 'monospace', marginLeft: 'auto' },
  title: { position: 'absolute', top: 79, width: 393, color: INK, fontFamily: SERIF, fontSize: 38, fontWeight: '600', letterSpacing: -1.2, lineHeight: 41, textAlign: 'center' }, subtitle: { position: 'absolute', top: 180, width: 393, color: 'rgba(39,40,50,0.6)', fontSize: 13, textAlign: 'center' }, artwork: { position: 'absolute', left: 28, top: 220, width: 337, height: 330 },
  detail: { position: 'absolute', top: 562, left: 70, width: 253, height: 58, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 24 }, detailItem: { alignItems: 'center', gap: 6 }, detailNumber: { color: INK, fontFamily: SERIF, fontSize: 22 }, detailLabel: { color: 'rgba(39,40,50,0.55)', fontSize: 8, letterSpacing: 1.5 }, divider: { width: 1, height: 33, backgroundColor: 'rgba(39,40,50,0.15)' },
  cta: { position: 'absolute', left: 28, top: 653, width: 337, height: 54, borderRadius: 18, alignItems: 'center', justifyContent: 'center', backgroundColor: ACCENT }, ctaLabel: { color: '#FFFFFF', fontSize: 15, fontWeight: '600' }, ctaPressed: { transform: [{ scale: 0.97 }], opacity: 0.8 }, ctaReducedPressed: { opacity: 0.8 }, footer: { position: 'absolute', top: 731, width: 393, color: 'rgba(39,40,50,0.45)', fontSize: 9, fontWeight: '500', letterSpacing: 1.6, textAlign: 'center' },
});
