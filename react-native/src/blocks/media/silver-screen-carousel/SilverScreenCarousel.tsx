import { ProgressiveBlurViewport } from '@/shared/SkiaProgressiveBlur';
import { PressScale } from '@/shared/PressScale';
import { SwapText } from '@/shared/SwapText';
import { useFonts } from 'expo-font';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Platform, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector, Pressable, ScrollView } from 'react-native-gesture-handler';
import Animated, { cancelAnimation, cubicBezier, Easing, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useSharedValue, withSpring, withTiming, type SharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';
import { phase, useSceneClock } from './motion';

// Measured from all 1,679 source frames: 238px stride, -25.1675px/s.
export const LOOP = 29;
const SOURCE_END = 27.983;
const SECONDS_PER_CARD = 238 / 25.16752758685;
const SPRING = { duration: 400, dampingRatio: 0.8 };
const PROFILES = [
  { id: 'ember', code: 'EM', name: 'Ember chrome', description: 'Bold reds. Clear blue skies.', image: require('./assets/red-geometry.jpg'), colors: ['#3B79B9', '#F6BA72', '#C74728'] as const },
  { id: 'afternoon', code: 'AF', name: 'Afternoon film', description: 'Warm light. Soft green shadows.', image: require('./assets/coastal-afternoon.jpg'), colors: ['#38543B', '#A6B092', '#EFB88A'] as const },
  { id: 'silver', code: 'SV', name: 'Silver midnight', description: 'Deep blacks. Luminous grain.', image: require('./assets/midnight-stage.jpg'), colors: ['#252728', '#7E8383', '#E6E7E3'] as const },
  { id: 'salt', code: 'SA', name: 'Salt air', description: 'Cool blues. An honest portrait.', image: require('./assets/salt-air.jpg'), colors: ['#343F65', '#8294A8', '#E9E3D6'] as const },
];
export type SilverScreenCarouselProps = { autoPlay?: boolean; previewTime?: number; onSelect?: (profile: string) => void; onApply?: (profile: string) => void };
function autoPosition(t: number) {
  'worklet';
  return t < 28.35 ? Math.min(t, SOURCE_END) / SECONDS_PER_CARD : 0;
}
const PARALLAX = 22;
function ProfileCard({ index, width, position, active, flash, reduced }: { index: number; width: number; position: SharedValue<number>; active: boolean; flash: SharedValue<number>; reduced: boolean }) {
  const profile = PROFILES[index];
  const card = useAnimatedStyle(() => {
    const distance = index - position.get();
    return { opacity: Math.abs(distance) < 4 ? 1 : 0 };
  });
  // The photograph drifts a little slower than its frame, like a print sliding under glass.
  const photo = useAnimatedStyle(() => ({ transform: [{ translateX: reduced ? 0 : Math.max(-1, Math.min(1, index - position.get())) * -PARALLAX }] }));
  // Frames waiting at the edges are a step darker than the one in the gate.
  const shade = useAnimatedStyle(() => ({ opacity: Math.min(1, Math.abs(index - position.get())) * .22 }));
  const shutter = useAnimatedStyle(() => ({ opacity: active ? flash.get() : 0 }));
  const imageWidth = width - 15, imageHeight = imageWidth * 257 / 327;
  return <Animated.View accessible={active} accessibilityLabel={`${profile.name}. ${profile.description}`} accessibilityElementsHidden={!active} importantForAccessibility={active ? 'yes' : 'no-hide-descendants'} style={[styles.card, { width, minHeight: width * 364 / 342 }, card]}>
    <View style={{ width: imageWidth, height: imageHeight, borderRadius: 10, overflow: 'hidden', backgroundColor: '#161616' }}>
      <Animated.View style={[{ position: 'absolute', top: 0, bottom: 0, left: -PARALLAX, width: imageWidth + PARALLAX * 2 }, photo]}><Image source={profile.image} contentFit="cover" transition={0} cachePolicy="memory" style={StyleSheet.absoluteFill} accessible={false} /></Animated.View>
      <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, { backgroundColor: '#FFFDF4' }, shutter]} />
      <View pointerEvents="none" style={[StyleSheet.absoluteFill, styles.photoEdge]} />
    </View>
    <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, { borderRadius: 15, backgroundColor: '#000000' }, shade]} />
    <View style={styles.profileDetails}><Text style={styles.code}>{profile.code}</Text><View style={styles.description}><Text style={styles.profileName}>{profile.name}</Text><Text style={styles.profileDescription}>{profile.description}</Text></View></View>
    <LinearGradient colors={profile.colors} start={{ x: 0, y: 0 }} end={{ x: 1, y: 0 }} style={styles.swatch} />
  </Animated.View>;
}
export function SilverScreenCarousel({ autoPlay = false, previewTime, onSelect, onApply }: SilverScreenCarouselProps) {
  const { width, height, fontScale } = useWindowDimensions();
  const inset = useSafeAreaInsets();
  const referenceLayout = fontScale <= 1.3 && width >= 350;
  // Reference positions on its 402 × 874 pt screen: title box 206, card 368 (offset 15 for the scroll inset) (342 × 364), pager centre 811.
  const [fontsLoaded] = useFonts({ 'SourceSerif4-Regular': require('./assets/SourceSerif4-Regular.ttf') });
  const unit = height / 874;
  const cardWidth = Math.min(380, width * 342 / 402), gap = width * 10 / 402, step = cardWidth + gap, cardHeight = cardWidth * 364 / 342;
  const headingTop = 191 * unit, cardTop = 353 * unit;
  const headingMargin = referenceLayout ? Math.max(24, headingTop - inset.top - 16) : 27;
  const cardMargin = referenceLayout ? Math.max(18, cardTop - headingTop - 116 * fontScale) : 24;
  const footerMargin = referenceLayout ? Math.max(18, 795 * unit - cardTop - cardHeight - 29) : 16;
  const [selected, setSelected] = useState(0);
  const [manual, setManual] = useState(false);
  const [applied, setApplied] = useState<string | null>(null);
  const { time, anchor, foreground, reduced } = useSceneClock(LOOP, autoPlay && !manual && !applied, 0, 0, previewTime);
  const manualMode = useSharedValue(false), manualPosition = useSharedValue(0), dragStart = useSharedValue(0);
  const position = useDerivedValue(() => manualMode.get() ? manualPosition.get() : autoPosition(time.get()));
  const [waiting, setWaiting] = useState(false), [quietGeneration, setQuietGeneration] = useState(0);
  const clearResume = useCallback(() => setWaiting(false), []);
  const resume = useCallback(() => { time.set(0); anchor.set(-1); manualMode.set(false); setManual(false); setWaiting(false); }, [anchor, manualMode, time]);
  const armResume = useCallback(() => { setQuietGeneration(value => value + 1); setWaiting(true); }, []);
  useEffect(() => {
    if (!waiting || !manual || !autoPlay || reduced || applied || !foreground) return;
    const timer = setTimeout(() => {
      manualPosition.set(withSpring(0, SPRING, finished => { if (finished) scheduleOnRN(resume); }));
    }, 2000);
    return () => clearTimeout(timer);
  }, [applied, autoPlay, foreground, manual, manualPosition, quietGeneration, reduced, resume, waiting]);
  const begin = useCallback(() => { clearResume(); setManual(true); setApplied(null); }, [clearResume]);
  const commit = useCallback((index: number) => { setSelected(index); onSelect?.(PROFILES[index].id); void Haptics.selectionAsync().catch(() => {}); }, [onSelect]);
  useAnimatedReaction(() => Math.min(3, Math.max(0, Math.round(position.get()))), (next, previous) => { if (next !== previous) scheduleOnRN(setSelected, next); });
  useEffect(() => { if (!foreground) cancelAnimation(manualPosition); return () => cancelAnimation(manualPosition); }, [foreground, manualPosition]);
  const pan = useMemo(() => Gesture.Pan().maxPointers(1).activeOffsetX([-8, 8]).failOffsetY([-16, 16])
    .onStart(() => { cancelAnimation(manualPosition); const current = position.get(); manualPosition.set(current); dragStart.set(current); manualMode.set(true); scheduleOnRN(begin); })
    .onUpdate(event => { const raw = dragStart.get() - event.translationX / step; const bound = Math.min(3, Math.max(0, raw)); const over = raw - bound; manualPosition.set(bound + over / (1 + Math.abs(over) * 4) * 0.5); })
    .onEnd((event, success) => { if (!success) { const rollback = Math.min(3, Math.max(0, Math.round(dragStart.get()))); manualPosition.set(reduced ? rollback : withSpring(rollback, SPRING)); return; } const velocity = -event.velocityX / step; const next = Math.min(3, Math.max(0, Math.round(manualPosition.get() + velocity * 0.18))); manualPosition.set(reduced ? next : withSpring(next, { ...SPRING, velocity })); scheduleOnRN(commit, next); })
    .onFinalize(() => { scheduleOnRN(armResume); }), [armResume, begin, commit, dragStart, manualMode, manualPosition, position, reduced, step]);
  const track = useAnimatedStyle(() => ({ opacity: manualMode.get() || reduced ? 1 : 1 - phase(time.get(), SOURCE_END, 28.35) + phase(time.get(), 28.35, 28.7), transform: [{ translateX: -position.get() * step }] }));
  const select = (index: number) => { begin(); manualPosition.set(position.get()); manualMode.set(true); manualPosition.set(reduced ? index : withSpring(index, SPRING)); commit(index); armResume(); };
  const apply = () => { clearResume(); const index = Math.min(3, Math.max(0, Math.round(position.get()))); manualPosition.set(index); manualMode.set(true); setManual(true); setApplied(PROFILES[index].name); onApply?.(PROFILES[index].id); fireShutter(); void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {}); };
  // Applying a profile fires the shutter once over the frame in the gate.
  const flash = useSharedValue(0);
  const fireShutter = () => { if (reduced) return; flash.set(.85); flash.set(withTiming(0, { duration: 420, easing: Easing.bezier(.23, 1, .32, 1) })); };
  const pauseReading = () => { clearResume(); manualPosition.set(position.get()); manualMode.set(true); setManual(true); };
  return <View style={styles.screen}>
    <StatusBar style="light" /><ProgressiveBlurViewport tint="dark" edgeSize={32} style={{ flex: 1 }}><ScrollView showsVerticalScrollIndicator={false} onScrollBeginDrag={pauseReading} onScrollEndDrag={armResume} onMomentumScrollBegin={clearResume} onMomentumScrollEnd={armResume} contentContainerStyle={{ minHeight: height, paddingTop: inset.top + 16, paddingBottom: inset.bottom + 16 }}>
    <View style={[styles.heading, { marginTop: headingMargin, marginBottom: cardMargin, opacity: fontsLoaded ? 1 : 0 }]}><Text accessibilityRole="header" maxFontSizeMultiplier={1.5} style={[styles.title, fontsLoaded && { fontFamily: 'SourceSerif4-Regular' }]}>Aesthetic shots{'\n'}without editing</Text><Text style={styles.subtitle}>Not another filter, but soft{'\n'}film-inspired profiles</Text></View>
    <GestureDetector gesture={pan}><Animated.View style={[styles.carousel, { minHeight: cardHeight }]} accessibilityRole="adjustable" accessibilityLabel={`Film profile: ${PROFILES[selected].name}`} accessibilityValue={{ min: 1, max: 4, now: selected + 1 }} accessibilityActions={[{ name: 'increment', label: 'Next profile' }, { name: 'decrement', label: 'Previous profile' }]} onAccessibilityAction={event => select(Math.min(3, Math.max(0, selected + (event.nativeEvent.actionName === 'increment' ? 1 : -1))))}>
      <Animated.View style={[styles.track, { paddingLeft: (width - cardWidth) / 2, gap }, track]}>{PROFILES.map((p, index) => <ProfileCard key={p.id} index={index} width={cardWidth} position={position} active={selected === index} flash={flash} reduced={reduced} />)}</Animated.View>
    </Animated.View></GestureDetector>
    <View style={[styles.footer, { marginTop: footerMargin }]}>
      {referenceLayout ? <View style={styles.sourcePager}>
        {/* As in the reference, the dots count onboarding steps (this is the first), not film profiles. */}
        <View accessible accessibilityRole="text" accessibilityLabel="Step 1 of 4" style={styles.sourceDotsTarget}><View style={styles.sourceDots}>{[0, 1, 2, 3].map(index => <View key={index} style={[styles.dot, index === 0 && styles.activeDot]} />)}</View></View>
        <PressScale accessibilityRole="button" accessibilityLabel={applied ? 'Try another film profile' : `Use ${PROFILES[selected].name}`} reduced={reduced} onPress={applied ? () => { setApplied(null); armResume(); } : apply} style={[styles.arrow, applied && styles.arrowApplied]}><SwapText style={[styles.arrowGlyph, applied && { color: '#121612' }]} reduced={reduced} text={applied ? '✓' : '→'} /></PressScale>
      </View> : <>
      <View style={styles.pager}><Pressable accessibilityRole="button" accessibilityLabel="Previous film profile" disabled={selected === 0} onPress={() => select(selected - 1)} style={({ pressed }) => [styles.arrow, { opacity: selected === 0 ? 0.32 : pressed ? 0.6 : 1 }]}><Text allowFontScaling={false} style={styles.arrowGlyph}>←</Text></Pressable><View style={styles.dots}>{PROFILES.map((p, index) => <Pressable key={p.id} accessibilityRole="button" accessibilityLabel={`Select ${p.name}`} accessibilityState={{ selected: selected === index }} onPress={() => select(index)} style={styles.dotTarget}><Animated.View style={[styles.dot, selected === index && styles.activeDot, { transitionProperty: ['width', 'height', 'backgroundColor'], transitionDuration: reduced ? 0 : 240, transitionTimingFunction: cubicBezier(.23, 1, .32, 1) }]} /></Pressable>)}</View><Pressable accessibilityRole="button" accessibilityLabel="Next film profile" disabled={selected === 3} onPress={() => select(selected + 1)} style={({ pressed }) => [styles.arrow, { opacity: selected === 3 ? 0.32 : pressed ? 0.6 : 1 }]}><Text allowFontScaling={false} style={styles.arrowGlyph}>→</Text></Pressable></View>
      <PressScale accessibilityRole="button" reduced={reduced} onPress={applied ? () => { setApplied(null); armResume(); } : apply} style={styles.apply}><SwapText style={styles.applyLabel} reduced={reduced} text={applied ? 'Try another profile' : `Use ${PROFILES[selected].name}`} /></PressScale>

      </>}
      {(applied || !referenceLayout) && <Text accessibilityLiveRegion="polite" style={styles.status}>{applied ? `${applied} applied to your preview.` : 'Swipe to find your point of view.'}</Text>}
    </View>
  </ScrollView></ProgressiveBlurViewport></View>;
}
const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#000000', overflow: 'hidden' },
  heading: { alignItems: 'center', paddingHorizontal: 24, marginTop: 27, marginBottom: 24 }, title: { color: '#D9D9D7', fontFamily: Platform.OS === 'ios' ? 'Georgia' : 'serif', fontSize: 28, lineHeight: 32, textAlign: 'center', letterSpacing: -0.4 }, subtitle: { color: '#6E6E6E', fontSize: 13, lineHeight: 20, textAlign: 'center', marginTop: 12 },
  carousel: { flexGrow: 0, flexShrink: 0, justifyContent: 'center', overflow: 'hidden' }, track: { flexDirection: 'row', gap: 18, alignItems: 'stretch' }, card: { backgroundColor: '#0B0B0B', padding: 7, borderRadius: 15, borderWidth: 1, borderColor: '#1F1F1F' }, profileDetails: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 5, paddingTop: 22, paddingBottom: 20 }, code: { fontSize: 26, fontWeight: '600', color: '#F2F2F0', letterSpacing: -0.6 }, description: { flex: 1, gap: 4 }, profileName: { fontSize: 11.5, color: '#E6E6E4', fontWeight: '500' }, profileDescription: { fontSize: 10, lineHeight: 14, color: '#8A8A88' }, swatch: { height: 5, borderRadius: 3, marginHorizontal: 5, marginBottom: 7 },
  sourcePager: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, sourceDotsTarget: { minWidth: 48, height: 48, justifyContent: 'center' }, sourceDots: { flexDirection: 'row', gap: 6, alignItems: 'center' },
  footer: { paddingHorizontal: 34, marginTop: 16 }, pager: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 10 }, arrow: { width: 58, height: 58, borderRadius: 29, backgroundColor: '#1A1A1A', alignItems: 'center', justifyContent: 'center' }, arrowGlyph: { color: '#D9DED5', fontSize: 22, lineHeight: 26, textAlign: 'center' }, arrowApplied: { backgroundColor: '#E7ECE2' }, photoEdge: { borderRadius: 10, borderWidth: 1, borderColor: '#FFFFFF14' }, dots: { flexDirection: 'row' }, dotTarget: { width: 48, height: 48, alignItems: 'center', justifyContent: 'center' }, dot: { width: 5, height: 5, borderRadius: 3, backgroundColor: '#3A3A3A' }, activeDot: { width: 6, height: 6, backgroundColor: '#F2F2F0' }, apply: { minHeight: 48, borderRadius: 25, backgroundColor: '#E7ECE2', alignItems: 'center', justifyContent: 'center', paddingVertical: 12, paddingHorizontal: 20 }, applyLabel: { color: '#192019', fontSize: 14, fontWeight: '600' }, status: { color: '#A3ADA0', textAlign: 'center', fontSize: 11, lineHeight: 16, marginTop: 10 },
});
