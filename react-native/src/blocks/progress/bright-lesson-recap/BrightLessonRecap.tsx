import { ProgressiveBlurViewport } from '@/shared/SkiaProgressiveBlur';
import * as Haptics from 'expo-haptics';
import { useFonts } from 'expo-font';
import { Image } from 'expo-image';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { Share, StyleSheet, Text, TextInput, View, useWindowDimensions } from 'react-native';
import { Pressable, ScrollView } from 'react-native-gesture-handler';
import Animated, { Easing, interpolate, useAnimatedProps, useAnimatedReaction, useAnimatedStyle, useSharedValue, withTiming, type SharedValue } from 'react-native-reanimated';
import { PressScale } from '@/shared/PressScale';
import { SwapText } from '@/shared/SwapText';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle, Path } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';
import { normalizeSceneTime, phase, useSceneClock } from './motion';

// Preserve the 351-frame source story; only the final 0.649s resets the demo.
export const LOOP = 6.5;
const SOURCE_END = 5.851;
const ENTER = Easing.bezierFn(0.23, 1, 0.32, 1);
const AnimatedInput = Animated.createAnimatedComponent(TextInput);
const MASCOT = require('./assets/pip-celebrates.png');
export type BrightLessonRecapProps = { autoPlay?: boolean; previewTime?: number; onClaim?: (points: number) => void };
const STATS = [
  { label: 'TOTAL XP', value: 45, sparkle: 1.90, body: 2.367, cap: 2.667, capEnd: 2.734, land: 3.1, color: '#D98A00', light: '#FFF4D6', border: '#F4AE16', kind: 'points' },
  { label: 'AMAZING', value: 100, sparkle: 2.85, body: 2.967, cap: 3.25, capEnd: 3.283, land: 3.283, color: '#3E9B22', light: '#E7F7DE', border: '#5BC236', kind: 'accuracy' },
  { label: 'QUICK', value: 220, sparkle: 3.56, body: 3.65, cap: 3.967, capEnd: 4.05, land: 3.95, color: '#1985C9', light: '#E1F2FC', border: '#2FA8EA', kind: 'time' },
] as const;
function fraction(t: number, a: number, b: number) { 'worklet'; if (!Number.isFinite(t)) return 0; if (t >= b - 1e-9) return 1; return Math.min(1, Math.max(0, (t - a) / (b - a))); }
function dockAt(t: number) {
  'worklet';
  // Normalized widths measured at source frames72,76,78,80,81,84,87.
  return interpolate(t, [1.2, 1.267, 1.3, 1.333, 1.35, 1.4, 1.45], [0, 0.09, 0.22, 0.63, 0.80, 0.96, 1], 'clamp');
}
function counterProgress(index: number, t: number) {
  'worklet';
  if (index === 0) return 0.89 * ENTER(fraction(t, 2.35, 2.65)) + 0.11 * fraction(t, 2.8, 3.1);
  return fraction(t, index === 1 ? 2.95 : 3.65, index === 1 ? 3.283 : 3.95);
}
function StatIcon({ kind, color, size = 23 }: { kind: string; color: string; size?: number }) {
  // Solid glyphs, as on a game's result card: a filled bolt, a sealed check, a clock face.
  return <Svg width={size} height={size} viewBox="0 0 24 24" strokeLinecap="round" strokeLinejoin="round">
    {kind === 'points' ? <Path d="M13.4 2.2 4.8 13.4h6.1l-1.1 8.4 8.9-11.6h-6.2l.9-8Z" fill={color} stroke={color} strokeWidth={1.2} /> : kind === 'accuracy' ? <><Circle cx={12} cy={12} r={9.6} fill={color} /><Path d="m7.8 12.2 2.9 2.9 5.6-6.2" fill="none" stroke="#FFFFFF" strokeWidth={2.3} /></> : <><Circle cx={12} cy={12} r={9.6} fill={color} /><Path d="M12 6.8V12l3.4 2.1" fill="none" stroke="#FFFFFF" strokeWidth={2.2} /></>}
  </Svg>;
}
function StatSpark({ index, card, time, height, reduced }: { index: number; card: number; time: SharedValue<number>; height: number; reduced: boolean }) {
  const stat = STATS[card];
  const style = useAnimatedStyle(() => {
    const t = time.get(); const show = phase(t, stat.sparkle, stat.sparkle + 0.08) * (1 - phase(t, stat.cap + 0.1, stat.cap + 0.5));
    return { opacity: reduced ? 0 : show * (0.55 + 0.45 * Math.pow(Math.sin(t * 18 + index), 2)), transform: [{ rotate: '45deg' }] };
  });
  return <Animated.View pointerEvents="none" style={[styles.statSpark, { left: `${50 + Math.cos(index * Math.PI / 4) * 47}%`, top: height * (0.62 + Math.sin(index * Math.PI / 4) * 0.29), backgroundColor: stat.border }, style]} />;
}
function StatCard({ index, time, stacked, fontScale, compactHeight, reduced }: { index: number; time: SharedValue<number>; stacked: boolean; fontScale: number; compactHeight: number; reduced: boolean }) {
  const stat = STATS[index]; const bodyHeight = compactHeight * 0.76, capHeight = compactHeight * 0.27;
  const [counterWidth, setCounterWidth] = useState((stat.kind === 'points' ? 36 : 60) * fontScale);
  const finalCounterText = stat.kind === 'time' ? '3:40' : stat.kind === 'accuracy' ? '100%' : '42';
  const body = useAnimatedStyle(() => { const p = ENTER(fraction(time.get(), stat.body, stat.body + 0.2)); return { opacity: fraction(time.get(), stat.body - 1 / 60, stat.body), transform: [{ scaleX: 0.56 + 0.44 * p }, { scaleY: 0.5 + 0.5 * p }] }; });
  const valueStyle = useAnimatedStyle(() => {
    const t = time.get(), p = ENTER(fraction(t, stat.body, stat.body + 0.2));
    // The number lands with a small kick: +10% for 90 ms, then settles over 220 ms.
    const kick = reduced ? 0 : fraction(t, stat.land, stat.land + .09) * (1 - ENTER(fraction(t, stat.land + .09, stat.land + .31)));
    return { opacity: fraction(t, stat.body - 1 / 60, stat.body), transform: [{ scale: (0.8 + 0.2 * p) * (1 + .1 * kick) }] };
  });
  const cap = useAnimatedStyle(() => { const p = ENTER(fraction(time.get(), stat.cap, stat.capEnd)); return { opacity: p, transform: [{ translateY: (1 - p) * capHeight }] }; });
  const stackedStyle = useAnimatedStyle(() => ({ opacity: fraction(time.get(), stat.body - 1 / 60, stat.body) }));
  const counter = useAnimatedProps(() => {
    const p = counterProgress(index, time.get());
    // Source counts the minute and second columns independently, then lands at3:40.
    const text = stat.kind === 'time' ? `${Math.floor(3 * p)}:${String(Math.round(40 * p)).padStart(2, '0')}` : `${Math.round(stat.value * p)}${stat.kind === 'accuracy' ? '%' : ''}`;
    return { text, defaultValue: text };
  });
  const label = `${stat.label.toLowerCase()}: ${stat.kind === 'time' ? '3 minutes 40 seconds' : stat.value + (stat.kind === 'accuracy' ? ' percent' : '')}`;
  if (stacked) return <Animated.View accessible accessibilityLabel={label} style={[styles.stat, styles.statStacked, { borderColor: stat.border }, stackedStyle]}>
    <View style={[styles.statCap, { backgroundColor: stat.light }]}><Text style={[styles.statLabel, { color: stat.color }]}>{stat.label}</Text></View>
    <View style={styles.statIcon}><StatIcon kind={stat.kind} color={stat.color} /></View>
    <AnimatedInput animatedProps={counter} defaultValue="0" multiline scrollEnabled={false} editable={false} focusable={false} caretHidden accessible={false} importantForAccessibility="no" pointerEvents="none" underlineColorAndroid="transparent" style={[styles.counter, { color: stat.color, minHeight: Math.ceil(34 * fontScale) + 16 }]} />
  </Animated.View>;
  return <View accessible accessibilityLabel={label} style={[styles.pillSlot, { height: compactHeight }]}>
    <Text accessible={false} accessibilityElementsHidden importantForAccessibility="no-hide-descendants" pointerEvents="none" onTextLayout={event => { const measured = Math.ceil(event.nativeEvent.lines[0]?.width ?? 0) + 8; if (measured > 8) setCounterWidth(current => Math.abs(current - measured) > .5 ? measured : current); }} style={styles.pillCounterMeasure}>{finalCounterText}</Text>
    {Array.from({ length: 8 }, (_, spark) => <StatSpark key={spark} index={spark} card={index} time={time} height={compactHeight} reduced={reduced} />)}
    <Animated.View pointerEvents="none" style={[styles.pillOutline, { height: bodyHeight, borderColor: stat.border }, body]} />
    <View pointerEvents="none" style={[styles.capMask, { height: capHeight }]}><Animated.View style={[styles.pillCap, { height: capHeight, backgroundColor: stat.border }, cap]}><Text style={[styles.pillLabel, { color: '#FFFFFF' }]}>{stat.label}</Text></Animated.View></View>
    <Animated.View pointerEvents="none" style={[styles.pillValue, { height: bodyHeight }, valueStyle]}><StatIcon kind={stat.kind} color={stat.color} size={18} /><AnimatedInput animatedProps={counter} defaultValue="0" editable={false} focusable={false} caretHidden accessible={false} importantForAccessibility="no" pointerEvents="none" underlineColorAndroid="transparent" style={[styles.pillCounter, { color: stat.color, width: counterWidth, minHeight: 24 * fontScale + 4 }]} /></Animated.View>
  </View>;
}
function Sparkle({ index, time, centerY, reduced }: { index: number; time: SharedValue<number>; centerY: number; reduced: boolean }) {
  const angle = index * Math.PI / 3;
  const style = useAnimatedStyle(() => {
    const t = time.get(), p = phase(t, 1.333, 1.5);
    return { opacity: reduced ? 0 : p * (1 - phase(t, SOURCE_END, 6.25)), transform: [{ translateX: Math.cos(angle) * 116 }, { translateY: Math.sin(angle) * 104 }, { scale: 0.82 + 0.22 * Math.sin(t * Math.PI * 4 / LOOP * 1.5 + index * 1.7) }] };
  });
  return <Animated.View pointerEvents="none" style={[styles.sparkle, { top: centerY }, style]}><Svg width={index % 3 === 1 ? 14 : 22} height={index % 3 === 1 ? 14 : 22} viewBox="0 0 20 20"><Path d="M10 0C10.9 6.4 13.6 9.1 20 10 13.6 10.9 10.9 13.6 10 20 9.1 13.6 6.4 10.9 0 10 6.4 9.1 9.1 6.4 10 0Z" fill={index % 2 ? '#78D64B' : '#8FDF5F'} /></Svg></Animated.View>;
}
export function BrightLessonRecap({ autoPlay = false, previewTime, onClaim }: BrightLessonRecapProps) {
  const { width, height, fontScale } = useWindowDimensions(); const inset = useSafeAreaInsets();
  // The reference sets every word in a heavy rounded face; Nunito carries that voice.
  const [fontsLoaded] = useFonts({ 'BrightNunito-Black': require('./assets/Nunito-Black.ttf'), 'BrightNunito-ExtraBold': require('./assets/Nunito-ExtraBold.ttf') });
  const [playing, setPlaying] = useState(autoPlay), [paused, setPaused] = useState(false), [interacting, setInteracting] = useState(false), [claimed, setClaimed] = useState(false);
  const { time, anchor, foreground, reduced } = useSceneClock(LOOP, playing && !paused && !claimed, 5, autoPlay ? 0 : 5, previewTime);
  const initialPhase = previewTime === undefined ? reduced || !autoPlay ? 5 : 0 : normalizeSceneTime(previewTime, LOOP);
  const [actionsEnabled, setActionsEnabled] = useState(initialPhase >= 2.35 && initialPhase < SOURCE_END);
  const [mascotEnabled, setMascotEnabled] = useState(initialPhase > 0.333 && initialPhase < 6.25);
  useEffect(() => { if (!paused || interacting || !foreground || reduced || claimed) return; const timer = setTimeout(() => setPaused(false), 2000); return () => clearTimeout(timer); }, [claimed, foreground, interacting, paused, reduced]);
  useAnimatedReaction(() => !autoPlay && time.get() >= SOURCE_END, (done, previous) => { if (done && !previous) scheduleOnRN(setPlaying, false); });
  useAnimatedReaction(() => claimed || (time.get() >= 2.35 && time.get() < SOURCE_END), (enabled, previous) => { if (enabled !== previous) scheduleOnRN(setActionsEnabled, enabled); });
  useAnimatedReaction(() => time.get() > 0.333 && time.get() < 6.25, (enabled, previous) => { if (enabled !== previous) scheduleOnRN(setMascotEnabled, enabled); });
  const pause = () => { setInteracting(true); setPaused(true); };
  const release = () => setInteracting(false);
  // Claiming sends the points up out of the button: +42 rises 56 pt and fades in 700 ms.
  const reward = useSharedValue(0);
  const rewardStyle = useAnimatedStyle(() => { const p = reward.get(); return { opacity: p <= 0 || p >= 1 ? 0 : Math.min(1, p * 6) * (1 - ENTER(fraction(p, .45, 1))), transform: [{ translateY: -56 * ENTER(p) }, { scale: .9 + .2 * ENTER(fraction(p, 0, .3)) }] }; });
  const replay = () => { setInteracting(false); setClaimed(false); setPaused(false); time.set(reduced ? 5 : 0); anchor.set(-1); setPlaying(!reduced); void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {}); };
  const claim = () => { const t = time.get(); if (claimed || t < 2.35 || t >= SOURCE_END) return; setInteracting(false); setClaimed(true); setPaused(true); time.set(5); onClaim?.(45); if (!reduced) { reward.set(0); reward.set(withTiming(1, { duration: 700, easing: Easing.linear })); } void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {}); };
  const flowLayout = fontScale > 1.3 || width < 350;
  const contentInset = flowLayout ? 24 : width * 0.04;
  // The scene starts 18 pt under the top inset; positions below are fractions of the reference screen.
  const sectionTop = inset.top + 18;
  const mascotSize = flowLayout ? 220 : width * 0.8;
  const compactHeight = width * 55 / 272;
  const heroHeight = flowLayout ? 250 : Math.max(350, height * 426 / 586 - sectionTop);
  const actionGap = flowLayout ? 28 : Math.max(12, height * 525 / 586 - (sectionTop + heroHeight + compactHeight));
  const scene = useAnimatedStyle(() => ({ opacity: 1 - phase(time.get(), SOURCE_END, 6.25) }));
  const mascot = useAnimatedStyle(() => {
    const t = time.get(), build = ENTER(fraction(t, 0.333, 0.533)), dock = dockAt(t);
    const x = interpolate(t, [0.333, 0.4, 0.5, 0.6, 0.75, 1.2, 1.45], [-0.8, -0.28, -0.09, -0.02, 0.015, 0.085, 0.03], 'clamp');
    const center = interpolate(t, [0.333, 0.5, 0.75, 1.2, 1.3, 1.35, 1.5, 1.7, 1.9], [0.44, 0.439, 0.428, 0.423, 0.433, 0.427, 0.392, 0.378, 0.376], 'clamp');
    return { opacity: build, transform: [{ translateX: x * width }, { translateY: flowLayout ? -10 * dock : height * center - sectionTop - mascotSize * 31 / 60 }, { scale: 1 - 0.39 * dock }] };
  });
  const bands = useAnimatedStyle(() => ({ opacity: time.get() < 1.333 ? ENTER(fraction(time.get(), 0.333, 0.533)) : 0, transform: [{ translateX: reduced ? 0 : -(1 - ENTER(fraction(time.get(), 0.333, 0.55))) * width }, { rotate: '-12deg' }] }));
  const bigTitle = useAnimatedStyle(() => ({ opacity: time.get() < 1.333 ? ENTER(fraction(time.get(), 0.35, 0.55)) : 0, transform: [{ translateY: -height * 0.06 * dockAt(time.get()) }, { scale: 1 - 0.18 * dockAt(time.get()) }] }));
  const finalTitle = useAnimatedStyle(() => ({ opacity: time.get() >= 1.333 ? 1 : 0, transform: [{ translateY: reduced ? 0 : height * 0.147 * (1 - ENTER(fraction(time.get(), 1.333, 1.5))) }] }));
  const subtitle = useAnimatedStyle(() => ({ opacity: ENTER(fraction(time.get(), 1.367, 1.5)) }));
  const flowTitle = useAnimatedStyle(() => ({ opacity: ENTER(fraction(time.get(), 0.35, 0.55)) }));
  const actions = useAnimatedStyle(() => ({ opacity: phase(time.get(), 1.9, 2.35) * (1 - phase(time.get(), SOURCE_END, 6.25)) }));
  return <View style={styles.screen}><StatusBar style="dark" />{fontsLoaded && <ProgressiveBlurViewport tint="light" edgeSize={28} style={{ flex: 1 }}><ScrollView onScrollBeginDrag={pause} onScrollEndDrag={release} showsVerticalScrollIndicator={false} contentContainerStyle={[styles.content, { minHeight: height, paddingHorizontal: contentInset, paddingTop: inset.top + 18, paddingBottom: inset.bottom + 18 }]}>
    <Animated.View style={scene}>
      <Pressable accessibilityRole="button" accessibilityLabel="Replay lesson celebration" disabled={!mascotEnabled} accessibilityElementsHidden={!mascotEnabled} importantForAccessibility={mascotEnabled ? 'auto' : 'no-hide-descendants'} onPress={replay} style={[{ height: heroHeight, width: '100%' }, flowLayout && styles.flowHero]}>
        <Animated.View pointerEvents="none" style={[styles.bands, { top: flowLayout ? 70 : height * 0.275 - sectionTop, left: -width * 0.25, width: width * 1.5, height: flowLayout ? 160 : height * 0.385 }, bands]}><View style={styles.bandLight} /><View style={styles.bandBright} /><View style={styles.bandDark} /></Animated.View>
        {Array.from({ length: 6 }, (_, index) => <Sparkle key={index} index={index} time={time} centerY={flowLayout ? 120 : height * 0.376 - sectionTop} reduced={reduced} />)}
        <Animated.View style={[styles.mascot, { left: (width - contentInset * 2 - mascotSize) / 2, width: mascotSize, height: mascotSize * 31 / 30 }, mascot]}><Image source={MASCOT} contentFit="contain" transition={0} style={styles.mascotImage} accessible={false} /></Animated.View>
        {!flowLayout && <><Animated.Text maxFontSizeMultiplier={1.4} style={[styles.title, { top: height * 0.769 - sectionTop }, bigTitle]}>{'Brilliant\nwork!'}</Animated.Text><Animated.Text maxFontSizeMultiplier={1.6} style={[styles.finalTitle, { top: height * 344 / 586 - sectionTop - 4 }, finalTitle]}>Brilliant work!</Animated.Text><Animated.Text style={[styles.subtitle, { top: height * 380 / 586 - sectionTop }, subtitle]}>Take a bow!</Animated.Text></>}
      </Pressable>
      {flowLayout && <View style={styles.flowCopy}><Animated.Text accessibilityRole="header" maxFontSizeMultiplier={1.6} style={[styles.flowTitle, flowTitle]}>Brilliant work!</Animated.Text><Animated.Text style={[styles.flowBody, subtitle]}>Take a bow!</Animated.Text></View>}
      <View style={[styles.stats, flowLayout ? styles.statsStacked : { width: width * 218 / 272, gap: width * 12.5 / 272, alignSelf: 'center' }]}>{STATS.map((stat, index) => <StatCard key={stat.kind} index={index} time={time} stacked={flowLayout} fontScale={fontScale} compactHeight={compactHeight} reduced={reduced} />)}</View>
    </Animated.View>
    <View style={flowLayout ? styles.spacer : { height: actionGap }} />
    <Animated.View pointerEvents={actionsEnabled ? 'auto' : 'none'} accessibilityElementsHidden={!actionsEnabled} importantForAccessibility={actionsEnabled ? 'auto' : 'no-hide-descendants'} style={[styles.actions, !flowLayout && { marginTop: 0 }, claimed ? undefined : actions]}>
      {(flowLayout || claimed) && <Text accessibilityLiveRegion="polite" style={[styles.success, !flowLayout && styles.successFloating]}>{claimed ? '45 XP added to this demo session.' : 'A perfect finish. You earned this.'}</Text>}
      <View style={!flowLayout && styles.actionRow}>
        {!flowLayout && <PressScale accessibilityRole="button" accessibilityLabel="Share your result" reduced={reduced} onPress={() => { void Share.share({ message: 'Perfect lesson: 45 XP, 100% accuracy, 3:40.' }).catch(() => {}); }} style={styles.replayIcon}><Svg width={22} height={22} viewBox="0 0 24 24" fill="none" stroke="#268DDD" strokeWidth={2.4} strokeLinecap="round" strokeLinejoin="round"><Path d="M12 14.5V3.5" /><Path d="M8 7.2 12 3.3l4 3.9" /><Path d="M8.5 10.5H7a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-6a2 2 0 0 0-2-2h-1.5" /></Svg></PressScale>}
        <Pressable accessibilityRole="button" accessibilityLabel={claimed ? 'XP claimed' : 'Claim 45 XP'} accessibilityState={{ disabled: claimed || !actionsEnabled }} disabled={claimed || !actionsEnabled} onPressIn={pause} onPressOut={release} onPress={claim} style={[styles.claimBase, !flowLayout && styles.compactBase, claimed && styles.claimedBase]}>
          {/* A physical key: the face sits 4 pt proud of its base and drops flush while held. */}
          {({ pressed }) => <View style={[styles.claim, !flowLayout && styles.compactClaim, claimed && styles.claimed, { transform: [{ translateY: pressed && !reduced ? 4 : 0 }] }]}><SwapText style={styles.claimText} reduced={reduced} text={claimed ? 'XP CLAIMED  ✓' : 'CLAIM XP'} /></View>}
        </Pressable>
        <Animated.Text pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={[styles.reward, rewardStyle]}>+45 XP</Animated.Text>
      </View>
      {flowLayout && <Pressable accessibilityRole="button" accessibilityLabel="Replay celebration" onPress={replay} style={({ pressed }) => [styles.replay, { opacity: pressed ? 0.6 : 1 }]}><Text style={styles.replayLabel}>Replay celebration</Text></Pressable>}
    </Animated.View>
  </ScrollView></ProgressiveBlurViewport>}</View>;
}
const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#FFFFFF' }, content: { flexGrow: 1 }, header: { flexDirection: 'row', flexWrap: 'wrap', gap: 10, justifyContent: 'space-between', alignItems: 'center', minHeight: 26 }, brand: { color: '#3D5B4B', fontSize: 10, letterSpacing: 1.8, fontFamily: 'BrightNunito-Black' }, chapter: { color: '#7C8A7D', fontSize: 10, fontFamily: 'BrightNunito-ExtraBold', letterSpacing: 1 },
  mascot: { position: 'absolute', top: 0 }, mascotImage: { width: '100%', height: '100%' }, bands: { position: 'absolute', overflow: 'hidden' }, bandLight: { flex: 0.18, backgroundColor: '#DCF5C6' }, bandBright: { flex: 0.64, backgroundColor: '#B4E892' }, bandDark: { flex: 0.18, backgroundColor: '#92D577' }, title: { position: 'absolute', left: 0, right: 0, textAlign: 'center', color: '#FFB100', fontSize: 49, lineHeight: 62, fontFamily: 'BrightNunito-Black', letterSpacing: -1.2 }, finalTitle: { position: 'absolute', left: 0, right: 0, textAlign: 'center', color: '#FFB100', fontSize: 28, lineHeight: 34, fontFamily: 'BrightNunito-Black', letterSpacing: -0.8 }, subtitle: { position: 'absolute', left: 0, right: 0, color: '#555555', fontSize: 15, lineHeight: 23, fontWeight: '400', textAlign: 'center' }, sparkle: { position: 'absolute', left: '50%', marginLeft: -9, marginTop: -9 },
  flowHero: { overflow: 'hidden' }, flowCopy: { gap: 16, marginBottom: 28 }, flowTitle: { color: '#F0A500', fontSize: 30, lineHeight: 37, fontFamily: 'BrightNunito-Black', textAlign: 'center', letterSpacing: -0.8 }, flowBody: { color: '#4B4B4B', fontFamily: 'BrightNunito-ExtraBold', fontSize: 16, lineHeight: 23, textAlign: 'center' }, stats: { flexDirection: 'row', marginTop: 0 }, statsStacked: { flexDirection: 'column', gap: 16 }, stat: { borderWidth: 1.5, borderRadius: 17, minHeight: 128, overflow: 'hidden', backgroundColor: '#FFFDF9' }, statStacked: { flex: 0, flexGrow: 0, flexShrink: 0, width: '100%' }, statCap: { paddingVertical: 10, paddingHorizontal: 3 }, statLabel: { fontSize: 10, fontFamily: 'BrightNunito-Black', textAlign: 'center', letterSpacing: 0.3 }, statIcon: { alignSelf: 'center', marginTop: 10 }, counter: { paddingHorizontal: 12, paddingTop: 6, paddingBottom: 10, margin: 0, marginTop: 5, width: '100%', textAlign: 'center', fontSize: 27, lineHeight: 34, fontFamily: 'BrightNunito-Black', fontVariant: ['tabular-nums'] },
  pillSlot: { flex: 1 }, pillOutline: { position: 'absolute', bottom: 0, left: 0, right: 0, borderWidth: 2.5, borderRadius: 12, backgroundColor: '#FFFFFF' }, capMask: { position: 'absolute', top: 0, left: 0, right: 0, borderTopLeftRadius: 11, borderTopRightRadius: 11, overflow: 'hidden' }, pillCap: { alignItems: 'center', justifyContent: 'center' }, pillLabel: { fontSize: 9, lineHeight: 12, fontFamily: 'BrightNunito-Black', textAlign: 'center', letterSpacing: .6 }, pillValue: { position: 'absolute', bottom: 0, left: 0, right: 0, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 4 }, pillCounterMeasure: { position: 'absolute', opacity: 0, fontSize: 18, lineHeight: 24, fontFamily: 'BrightNunito-Black', fontVariant: ['tabular-nums'] }, pillCounter: { padding: 0, margin: 0, fontSize: 18, lineHeight: 24, fontFamily: 'BrightNunito-Black', fontVariant: ['tabular-nums'], textAlign: 'left' }, statSpark: { position: 'absolute', width: 4, height: 4, borderRadius: 0.7 },
  spacer: { flex: 1, minHeight: 28 }, actions: { marginTop: 8 }, actionRow: { flexDirection: 'row', alignItems: 'stretch', gap: 9 }, compactClaim: { minHeight: 44, paddingVertical: 10, borderRadius: 12 }, claimBase: { borderRadius: 17, backgroundColor: '#1679C4', paddingBottom: 4 }, compactBase: { flex: 1, borderRadius: 12 }, claimedBase: { backgroundColor: '#1B6847' }, reward: { position: 'absolute', right: '30%', top: -6, color: '#D98A00', fontSize: 22, fontFamily: 'BrightNunito-Black', letterSpacing: -.4 }, success: { color: '#5D7463', textAlign: 'center', fontSize: 12, lineHeight: 18, marginBottom: 13 }, successFloating: { position: 'absolute', left: 0, right: 0, top: -30, marginBottom: 0 }, claim: { minHeight: 52, paddingVertical: 16, paddingHorizontal: 15, borderRadius: 17, alignItems: 'center', justifyContent: 'center', backgroundColor: '#1F9BEA' }, claimed: { backgroundColor: '#2E8A64' }, claimText: { color: '#FFFFFF', fontSize: 14, lineHeight: 20, fontFamily: 'BrightNunito-Black', letterSpacing: 0.7 }, replay: { minHeight: 48, alignItems: 'center', justifyContent: 'center', marginTop: 3 }, replayLabel: { color: '#597D94', fontSize: 13, fontFamily: 'BrightNunito-Black' }, replayIcon: { width: 48, minHeight: 48, borderWidth: 1, borderColor: '#C7D2D8', borderRadius: 12, alignItems: 'center', justifyContent: 'center' }, replayGlyph: { fontSize: 24, color: '#268DDD' },
});
