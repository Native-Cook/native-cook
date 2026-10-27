import { Ionicons } from '@expo/vector-icons';
import { StatusBar } from 'expo-status-bar';
import { ProgressiveBlurViewport } from '@/shared/SkiaProgressiveBlur';
import { useMotionPreference } from '@/shared/useMotionPreference';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AppState, Image, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView, Pressable, ScrollView } from 'react-native-gesture-handler';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Animated, { cancelAnimation, Easing, type SharedValue, useAnimatedProps, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useSharedValue, withRepeat, withSpring, withTiming } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import Svg, { Defs, LinearGradient as SvgGradient, Path, Stop } from 'react-native-svg';
import { LinearGradient } from 'expo-linear-gradient';
import { SwapText } from '@/shared/SwapText';
import * as Haptics from 'expo-haptics';
import { useFonts } from 'expo-font';

// Preserve the source's full 17.5 s narrative, then append a seamless return.
export const LOOP = 18.5;
function previewPhase(t: number) { if (!Number.isFinite(t)) return 0; const phase = t % LOOP; return phase < 0 ? phase + LOOP : phase; }
// Source f860–868: fitted visible card edges, not the clipped phone/notch edges.
// The last two offscreen knots continue the measured acceleration; no card is
// visible atf870. Values are normalized to the source viewport, except roll.
const LAST_X = [[860 / 60, 0], [861 / 60, 0.085104], [862 / 60, 0.112787], [863 / 60, 0.146173], [864 / 60, 0.209716], [865 / 60, 0.305564], [866 / 60, 0.434484], [867 / 60, 0.595059], [868 / 60, 0.783343], [869 / 60, 1], [870 / 60, 1.25]] as const;
const LAST_Y = [[860 / 60, 0], [861 / 60, -0.026521], [862 / 60, -0.03552], [863 / 60, -0.048075], [864 / 60, -0.072457], [865 / 60, -0.108951], [866 / 60, -0.158152], [867 / 60, -0.21929], [868 / 60, -0.291471], [869 / 60, -0.375], [870 / 60, -0.47]] as const;
const LAST_ROLL = [[860 / 60, 0], [861 / 60, 1.73147], [862 / 60, 2.26703], [863 / 60, 3.00973], [864 / 60, 4.32635], [865 / 60, 5.97817], [866 / 60, 9.07868], [867 / 60, 12.31907], [868 / 60, 16.26098], [869 / 60, 20.3], [870 / 60, 24.5]] as const;
const THROWS = [
  [[3.4, 0], [3.5, 0.02], [3.65, 0.04], [3.8, 0.07], [3.85, 0.18], [3.9, 0.42], [3.95, 0.85], [4, 1.4]],
  [[5.15, 0], [5.35, 0.03], [5.45, 0.055], [5.5, 0.09], [5.6, 0.3], [5.6833, 0.8], [5.75, 1.4]],
  [[9.1167, 0], [9.15, 0.1], [9.2, 0.3], [9.25, 0.75], [9.3333, 1.4]],
  [[11.2667, 0], [11.3, 0.1], [11.35, 0.55], [11.4333, 1.4]],
  [[12.55, 0], [12.5833, 0.1], [12.6333, 0.55], [12.75, 1.4]],
  LAST_X,
] as const;
const FLIP = [[0.6, 0], [0.65, 7], [0.7, 24], [0.75, 45], [0.8, 69], [0.8333, 87.4], [0.85, 90], [0.8833, 99], [0.9167, 117], [0.95, 133], [1, 154], [1.0667, 171], [1.1167, 180]] as const;
const LIFT = [[0, 0], [0.2, 0], [0.3, 0.05], [0.4, 0.27], [0.5, 0.55], [0.6, 0.81], [0.7, 1]] as const;
const ANCHORS = [1.8, 4.3, 6, 9.65, 11.65, 12.95, 15.5];
const DIRECTIONS = [1, -1, 1, 1, 1, 1];
const EASE = Easing.bezier(0.23, 1, 0.32, 1).factory();
const AnimatedPath = Animated.createAnimatedComponent(Path);
// Flat-vector covers in the reference's illustration style (generated, see the block notes).
const COVER = require('./assets/cover-thinking.webp');
const CHOICE = require('./assets/choice-food.webp');
const SUNNY = require('./assets/cover-sunny.webp');
const NIGHT = require('./assets/cover-night.webp');
const STEPS = [
  { title: 'Swipe the card\nto the right', symbol: '👉' },
  { title: 'Swipe the card\nto the left', symbol: '👈' },
  { title: 'Little questions.\nMany ways to connect.', symbol: '🃏' },
  { title: 'Pick a theme\nfor the moment.', symbol: '👀' },
  { title: 'One card can start\na great conversation.', symbol: '🗺️' },
  { title: 'You are ready.\nLet curiosity lead.', symbol: '☺️' },
];
type DeckPose = { step: number; x: number; flip: number; time: number };

function clamp(value: number, lower = 0, upper = 1) { 'worklet'; return Math.max(lower, Math.min(upper, value)); }
function track(t: number, keys: readonly (readonly [number, number])[]) {
  'worklet';
  if (t <= keys[0][0]) return keys[0][1];
  const last = keys.length - 1;
  if (t >= keys[last][0]) return keys[last][1];
  let i = 0;
  while (keys[i + 1][0] < t) i++;
  const [a, y0] = keys[i], [b, y1] = keys[i + 1];
  const secant = (y1 - y0) / (b - a);
  const before = i > 0 ? (y0 - keys[i - 1][1]) / (a - keys[i - 1][0]) : 0;
  const after = i + 1 < last ? (keys[i + 2][1] - y1) / (keys[i + 2][0] - b) : 0;
  const m0 = before * secant > 0 ? 2 * before * secant / (before + secant) : 0;
  const m1 = after * secant > 0 ? 2 * after * secant / (after + secant) : 0;
  const p = (t - a) / (b - a), p2 = p * p, p3 = p2 * p;
  return (2 * p3 - 3 * p2 + 1) * y0 + (p3 - 2 * p2 + p) * (b - a) * m0 + (-2 * p3 + 3 * p2) * y1 + (p3 - p2) * (b - a) * m1;
}
function projectedFace(x: number, y: number, scale: number, roll: number, yaw: number) {
  'worklet';
  const rz = roll * Math.PI / 180, ry = yaw * Math.PI / 180;
  const cz = Math.cos(rz), sz = Math.sin(rz), cy = Math.cos(ry), sy = Math.sin(ry);
  // RN's column-major P(1600)·T·S·Rz·Ry, restricted to the card's z=0 plane.
  // X,Y,W are unchanged; outputZ stays0 so sibling faces cannot intersect.
  // This retains the actual perspective foreshortening, not an affine substitute.
  return [
    scale * cz * cy, scale * sz * cy, 0, scale * sy / 1600,
    -scale * sz, scale * cz, 0, 0,
    0, 0, 1, 0,
    x, y, 0, 1,
  ];
}

function PaperCard({ index, pose, grab, width, height, screenHeight, reduced }: { index: number; pose: SharedValue<DeckPose>; grab: SharedValue<number>; width: number; height: number; screenHeight: number; reduced: boolean }) {
  const style = useAnimatedStyle(() => {
    const { step, x, flip, time } = pose.get();
    const depth = index - step;
    const top = depth === 0;
    const advance = clamp(Math.abs(x) / width);
    const remainingDepth = Math.max(0, depth - advance);
    const rearFraction = depth > 0 ? remainingDepth / depth : 0;
    const introRear = step === 0 ? track(time, [[1.1, 0], [1.22, 0.6], [1.34, 1.04], [1.6, 1]]) : 1;
    const rearAlpha = step === 0 ? clamp((time - 1.1) / 0.2) : 1;
    const edgeOn = time >= 0.8417 && time < 0.875;
    const lastThrow = top && index === 5 && time >= LAST_X[0][0];
    const tx = top ? x : (depth % 2 === 0 ? -1 : 1) * width * 0.024 * rearFraction * introRear;
    const ty = lastThrow ? screenHeight * track(time, LAST_Y) : top ? -Math.min(36, Math.abs(x) * 0.065) : height * 0.011 * remainingDepth * introRear;
    const scale = top ? 1 : 1 - 0.015 * remainingDepth * introRear;
    // A sheet held near its bottom swings its top the other way: it pivots under the finger.
    const roll = lastThrow ? track(time, LAST_ROLL) : top ? clamp(x / 22, -24, 24) * grab.get() : (depth % 2 === 0 ? -0.85 : 1.4) * remainingDepth * introRear;
    // Three visible source edges constrain yaw to~0° before the last card clips.
    const yaw = lastThrow ? 0 : index === 0 && flip < 1 ? 180 + flip * 180 : top && !reduced ? clamp(-x / 23, -16, 16) : 0;
    // A sheet held off the deck throws a wider, softer shadow; one lying on it barely any.
    const lift = top && !reduced ? clamp(Math.abs(x) / (width * 0.6)) : 0;
    return {
      opacity: flip < 0.5 || edgeOn || depth < 0 || depth > 2 ? 0 : top ? 1 : rearAlpha,
      zIndex: 10 - index,
      shadowOpacity: 0.16 + 0.22 * lift,
      shadowRadius: 9 + 18 * lift,
      shadowOffset: { width: -x * 0.03, height: 4 + 14 * lift },
      transform: [{ matrix: projectedFace(tx, ty, scale, roll, yaw) }],
    };
  });
  // Light skims the bowed sheet: a soft highlight rides the crest of the bend.
  const sheen = useAnimatedStyle(() => {
    const p = pose.get();
    const amount = reduced || p.step !== index ? 0 : clamp(Math.abs(p.x) / (width * 0.5));
    return { opacity: amount * 0.9, transform: [{ translateX: (p.x < 0 ? 0.62 : 0.08) * width - p.x * 0.12 }] };
  });
  const paper = useAnimatedProps(() => {
    const p = pose.get();
    const bend = reduced || (index === 5 && p.step === 5 && p.time >= LAST_X[0][0]) ? 0 : p.step === index ? Math.min(14, Math.abs(p.x) / 28) : 3.5;
    const curve = p.x < 0 ? -bend : bend;
    // The bowed top and bottom, together with Y perspective, make a flexible sheet.
    return { d: `M26 5 Q${width * 0.5} ${5 + curve} ${width - 26} 5 Q${width - 5} 5 ${width - 5} 26 L${width - 5} ${height - 26} Q${width - 5} ${height - 5} ${width - 26} ${height - 5} Q${width / 2} ${height - 5 - curve} 26 ${height - 5} Q5 ${height - 5} 5 ${height - 26} L5 26 Q5 5 26 5Z` };
  });
  return <Animated.View pointerEvents="none" style={[styles.card, { width, height }, style]} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
    <Svg width={width} height={height} style={StyleSheet.absoluteFill}>
      <Defs><SvgGradient id={`paper-${index}`} x1="0" y1="0" x2="0.35" y2="1"><Stop offset="0" stopColor="#FFFFFF" /><Stop offset="0.6" stopColor="#FDFBFE" /><Stop offset="1" stopColor="#F6F4F8" /></SvgGradient></Defs>
      <AnimatedPath animatedProps={paper} fill={`url(#paper-${index})`} stroke="#E7E4EA" strokeWidth={1} />
    </Svg>
    <View pointerEvents="none" style={[StyleSheet.absoluteFill, styles.sheenClip]}><Animated.View style={[styles.sheen, { width: width * 0.3 }, sheen]}><LinearGradient colors={['#FFFFFF00', '#FFFFFFCC', '#FFFFFF00']} start={{ x: 0, y: 0.5 }} end={{ x: 1, y: 0.5 }} style={StyleSheet.absoluteFill} /></Animated.View></View>
    <View style={styles.cardCopy}><Text maxFontSizeMultiplier={1.1} style={styles.cardTitle}>{STEPS[index].title}</Text><Text maxFontSizeMultiplier={1.4} style={styles.symbol}>{STEPS[index].symbol}</Text></View>
    <Text maxFontSizeMultiplier={1.4} style={styles.number}>– {index + 1} –</Text>
  </Animated.View>;
}

function IllustratedDeck({ pose, width, height, reduced, introTravel }: { pose: SharedValue<DeckPose>; width: number; height: number; reduced: boolean; introTravel: number }) {
  const frontStyle = useAnimatedStyle(() => {
    const { step, time, flip } = pose.get();
    const outro = step === 6;
    const rise = outro ? EASE(clamp((time - 14.9) / 0.3)) : 1;
    return {
      opacity: reduced ? (outro ? 1 : 0) : outro ? rise : time < 0.8417 && flip < 0.5 ? 1 : 0,
      transform: [{ perspective: 1600 }, { translateY: outro ? introTravel + 160 * (1 - rise) : introTravel * (1 - track(time, LIFT)) }, { rotateY: `${outro ? 0 : flip * 180}deg` }],
    };
  });
  const choiceStyle = useAnimatedStyle(() => ({ opacity: pose.get().step === 6 ? 1 - EASE(clamp((pose.get().time - 17.5) / 0.4)) : 0 }));
  const outroBack = useAnimatedStyle(() => ({ opacity: pose.get().step === 6 ? 1 : 0 }));
  const backsStyle = useAnimatedStyle(() => ({ opacity: pose.get().step === 6 ? 1 : 1 - track(pose.get().time, [[0.2, 0], [0.6, 1]]) }));
  return <Animated.View style={[styles.illustrated, { width: width - 10, height: height - 10, top: 5, left: 5 }, frontStyle]} pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
    <Animated.View style={[StyleSheet.absoluteFill, backsStyle]}>
      {/* The cards behind the cover peek above it: sunny then night before the tutorial, thinking then sunny after it. */}
      <View style={[styles.coverBack, { transformOrigin: 'top center', transform: [{ translateY: -introTravel * 0.58 }, { scale: 0.67 }] }]}><Image source={SUNNY} style={styles.coverImage} resizeMode="cover" /><Animated.View style={[StyleSheet.absoluteFill, outroBack]}><Image source={COVER} style={styles.coverImage} resizeMode="cover" /></Animated.View></View>
      <View style={[styles.coverBack, { transformOrigin: 'top center', transform: [{ translateY: -introTravel * 0.3504 }, { scale: 0.83 }] }]}><Image source={NIGHT} style={styles.coverImage} resizeMode="cover" /><Animated.View style={[StyleSheet.absoluteFill, outroBack]}><Image source={SUNNY} style={styles.coverImage} resizeMode="cover" /></Animated.View><View style={styles.backShade} /></View>
    </Animated.View>
    <View style={styles.coverClip}><Image source={COVER} style={styles.coverImage} resizeMode="cover" /><Animated.View style={[StyleSheet.absoluteFill, choiceStyle]}><Image source={CHOICE} style={styles.coverImage} resizeMode="cover" /></Animated.View></View>
  </Animated.View>;
}

export function PaperDeckTutorial({ autoPlay = false, previewTime, onComplete }: { autoPlay?: boolean; previewTime?: number; onComplete?: () => void }) {
  const { width: screenWidth, height: screenHeight, fontScale } = useWindowDimensions();
  const safe = useSafeAreaInsets();
  const reduced = useMotionPreference();
  // The reference prints its cards in a semibold text serif and its header in a grotesk.
  const [fontsLoaded] = useFonts({ 'PaperDeckSerif-Semibold': require('./assets/SourceSerif4-Semibold.ttf'), 'PaperDeckInter-Bold': require('./assets/Inter-Bold.ttf'), 'PaperDeckInter-Regular': require('./assets/Inter-Regular.ttf') });
  const [foreground, setForeground] = useState(AppState.currentState === 'active');
  useEffect(() => { const subscription = AppState.addEventListener('change', value => setForeground(value === 'active')); return () => subscription.remove(); }, []);
  const width = Math.min(screenWidth * 0.9, 410);
  const height = Math.max(fontScale > 1.4 ? 520 : 400, Math.min(660, screenHeight * 0.6));
  const clock = useSharedValue(previewTime === undefined ? !autoPlay || reduced ? 1.1 : 0 : previewPhase(previewTime));
  const manual = useSharedValue(reduced || (previewTime === undefined && !autoPlay));
  const manualStep = useSharedValue(0);
  const x = useSharedValue(0);
  const origin = useSharedValue(0);
  const grab = useSharedValue(1);
  const holding = useSharedValue(false);
  const [step, setStep] = useState(0);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pose = useDerivedValue<DeckPose>(() => {
    if (manual.get()) return { step: manualStep.get(), x: reduced ? 0 : x.get(), flip: 1, time: manualStep.get() === 6 ? 15.6 : 2 };
    const t = clock.get();
    if (t >= 17.9) return { step: 0, x: 0, flip: 0, time: 0 };
    let active = 0;
    while (active < 6 && t >= THROWS[active][THROWS[active].length - 1][0]) active++;
    return { step: active, x: active < 6 ? DIRECTIONS[active] * screenWidth * track(t, THROWS[active]) : 0, flip: track(t, FLIP) / 180, time: t };
  });
  useAnimatedReaction(() => pose.get().step, (next, old) => { if (next !== old) scheduleOnRN(setStep, next); });
  // Gear and lock frame the illustrated deck (the cover before the tutorial, the deck after it).
  const [onDeck, setOnDeck] = useState(true);
  useAnimatedReaction(() => pose.get().step === 6 || (pose.get().step === 0 && pose.get().time < 0.85), (next, old) => { if (next !== old) scheduleOnRN(setOnDeck, next); });
  useEffect(() => { if (reduced) { cancelAnimation(clock); cancelAnimation(x); manualStep.set(pose.get().step); manual.set(true); x.set(0); } }, [clock, manual, manualStep, pose, reduced, x]);
  const play = useCallback(() => {
    if (previewTime !== undefined) { cancelAnimation(clock); clock.set(previewPhase(previewTime)); manual.set(reduced); return; }
    if (!autoPlay || reduced || !foreground || manualStep.get() >= 6) return;
    manual.set(false); clock.set(0);
    clock.set(withRepeat(withTiming(LOOP, { duration: LOOP * 1000, easing: Easing.linear }), -1, false));
  }, [autoPlay, clock, foreground, manual, manualStep, previewTime, reduced]);
  useEffect(() => { play(); return () => { cancelAnimation(clock); cancelAnimation(x); if (timer.current) clearTimeout(timer.current); }; }, [clock, play, x]);
  const stopTimer = useCallback(() => { if (timer.current) clearTimeout(timer.current); }, []);
  const queueResume = useCallback(() => {
    if (!autoPlay || reduced || !foreground || previewTime !== undefined) return;
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(() => {
      if (holding.get() || manualStep.get() >= 6) return;
      x.set(withSpring(0, { duration: 400, dampingRatio: 0.8 }, finished => {
        if (!finished || holding.get()) return;
        const anchor = ANCHORS[manualStep.get()];
        grab.set(1);
        clock.set(anchor); manual.set(false);
        clock.set(withTiming(LOOP, { duration: (LOOP - anchor) * 1000, easing: Easing.linear }, done => { if (done) scheduleOnRN(play); }));
      }));
    }, 2000);
  }, [autoPlay, clock, foreground, grab, holding, manual, manualStep, play, previewTime, reduced, x]);
  const haptic = useCallback(() => { void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); }, []);
  const complete = useCallback(() => { onComplete?.(); }, [onComplete]);
  const gesture = useMemo(() => Gesture.Pan().maxPointers(1).activeOffsetX([-10, 10]).failOffsetY([-18, 18])
    // Deferred RNGH event: the timer ref is touched only after scheduleOnRN runs.
    // eslint-disable-next-line react-hooks/refs
    .onBegin(event => {
      grab.set(event.y > height * 0.62 ? -0.7 : 1);
      const current = pose.get();
      holding.set(true); manualStep.set(current.step); x.set(current.x); manual.set(true);
      cancelAnimation(clock); cancelAnimation(x); origin.set(x.get()); scheduleOnRN(stopTimer);
    })
    .onUpdate(event => {
      if (manualStep.get() >= 6 || reduced) return;
      const next = origin.get() + event.translationX;
      const limit = screenWidth * 0.75;
      x.set(Math.abs(next) > limit ? Math.sign(next) * (limit + (Math.abs(next) - limit) * 0.45) : next);
    })
    .onEnd((event, success) => {
      if (!success) { x.set(reduced ? 0 : withSpring(0, { duration: 400, dampingRatio: 0.8 })); return; }
      if (manualStep.get() >= 6) return;
      const commit = Math.abs(x.get()) > width * 0.26 || Math.abs(event.velocityX) > 650;
      if (!commit) { x.set(withSpring(0, { duration: 400, dampingRatio: 0.8, velocity: event.velocityX })); return; }
      scheduleOnRN(haptic);
      const direction = Math.abs(event.velocityX) > 650 ? Math.sign(event.velocityX) : Math.sign(x.get());
      if (reduced) { const next = Math.min(6, manualStep.get() + 1); manualStep.set(next); x.set(0); if (next === 6) scheduleOnRN(complete); return; }
      x.set(withSpring(direction * screenWidth * 1.4, { duration: 400, dampingRatio: 0.8, velocity: event.velocityX }, done => {
        if (!done) return;
        const next = Math.min(6, manualStep.get() + 1); manualStep.set(next); x.set(0);
        if (next === 6) scheduleOnRN(complete);
      }));
    })
    // Deferred RNGH event; registering this worklet does not execute queueResume.
    // eslint-disable-next-line react-hooks/refs
    .onFinalize(() => { holding.set(false); scheduleOnRN(queueResume); }), [clock, complete, grab, haptic, height, holding, manual, manualStep, origin, pose, queueResume, reduced, screenWidth, stopTimer, width, x]);
  const buttonBegin = () => {
    holding.set(true); stopTimer(); const p = pose.get(); manualStep.set(p.step); x.set(p.x); manual.set(true); cancelAnimation(clock); cancelAnimation(x);
  };
  const buttonEnd = () => { holding.set(false); queueResume(); };
  const advance = (back = false) => {
    const current = manualStep.get();
    if (back || current >= 6 || reduced) { manualStep.set(back ? Math.max(0, current - 1) : current >= 6 ? 0 : current + 1); x.set(0); if (!back && current === 5) complete(); return; }
    haptic();
    x.set(withSpring(DIRECTIONS[current] * screenWidth * 1.4, { duration: 400, dampingRatio: 0.8 }, done => { if (done) { manualStep.set(current + 1); x.set(0); if (current === 5) scheduleOnRN(complete); } }));
  };
  return <GestureHandlerRootView key={`type-${fontScale}`} style={styles.screen}><StatusBar style="light" />
    <ProgressiveBlurViewport style={{ flex: 1 }} tint="dark" edgeSize={28} enabled={fontScale > 1.4}><ScrollView onTouchStart={buttonBegin} onTouchEnd={buttonEnd} onTouchCancel={buttonEnd} onScrollBeginDrag={buttonBegin} onScrollEndDrag={buttonEnd} onMomentumScrollBegin={buttonBegin} onMomentumScrollEnd={buttonEnd} bounces={false} scrollEnabled={fontScale > 1.4} contentContainerStyle={{ minHeight: screenHeight, paddingTop: safe.top + 12, paddingBottom: Math.max(safe.bottom, 16) }}>
      {fontsLoaded && <><View style={[styles.header, { height: 62 * Math.max(1, fontScale * 0.8) }]}>
        {/* The reference's chrome: gear and lock discs around the deck, a close disc while the tutorial runs. */}
        {onDeck && <Pressable accessibilityRole="button" accessibilityLabel="Restart tutorial" onPressIn={buttonBegin} onPressOut={buttonEnd} onPress={() => { manualStep.set(0); x.set(0); }} style={[styles.close, styles.closeLeft]}><View style={styles.chromeDisc}><Ionicons name="settings-sharp" size={13} color="#D8D6D8" /></View></Pressable>}
        <View accessibilityLiveRegion="polite" style={styles.headerCopy}><SwapText maxFontSizeMultiplier={1.6} style={styles.headerTitle} reduced={reduced} text={step === 6 ? 'This or that' : 'Tutorial'} /><SwapText maxFontSizeMultiplier={1.8} style={styles.progress} reduced={reduced} delay={30} text={step === 6 ? '1 / 200' : `${step + 1} / 6`} /></View>
        <Pressable accessibilityRole="button" accessibilityLabel={onDeck ? 'Unlock all decks' : 'Finish tutorial'} onPressIn={buttonBegin} onPressOut={buttonEnd} onPress={() => { if (onDeck) return; manualStep.set(6); x.set(0); complete(); }} style={styles.close}>{onDeck ? <View style={styles.chromeDisc}><Ionicons name="lock-closed" size={12} color="#D8D6D8" /></View> : <View style={styles.closeDisc}><Svg width={12} height={12} viewBox="0 0 12 12"><Path d="M2 2l8 8M10 2 2 10" stroke="#0A090A" strokeWidth={2} strokeLinecap="round" /></Svg></View>}</Pressable>
      </View>
      <View style={[styles.stage, { minHeight: height + 90 }, fontScale <= 1.4 && { justifyContent: 'flex-start', paddingTop: Math.max(8, screenHeight * 0.224 - safe.top - 12 - 62) }]}>
        <GestureDetector gesture={gesture}>
          <Animated.View style={{ width, height, transform: [{ translateY: 0 }] }} accessible accessibilityRole="adjustable" accessibilityLabel={step < 6 ? `Card ${step + 1} of 6. ${STEPS[step].title.replace('\n', ' ')}` : 'This or that. Burger or apple?'} accessibilityHint="Swipe the card left or right, or use the adjust actions." accessibilityActions={[{ name: 'increment', label: 'Next card' }, { name: 'decrement', label: 'Previous card' }]} onAccessibilityAction={event => { buttonBegin(); advance(event.nativeEvent.actionName === 'decrement'); buttonEnd(); }}>
            {[5, 4, 3, 2, 1, 0].map(index => <PaperCard key={index} index={index} pose={pose} grab={grab} width={width} height={height} screenHeight={screenHeight} reduced={reduced} />)}
            <IllustratedDeck pose={pose} width={width} height={height} reduced={reduced} introTravel={screenHeight * 0.125} />
          </Animated.View>
        </GestureDetector>
      </View></>}
    </ScrollView></ProgressiveBlurViewport>
  </GestureHandlerRootView>;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#050505', overflow: 'hidden' }, header: { height: 62, alignItems: 'center', justifyContent: 'flex-start', marginHorizontal: 10 }, headerCopy: { marginTop: 26 }, headerTitle: { color: '#F8F7F7', fontFamily: 'PaperDeckInter-Bold', textAlign: 'center', fontSize: 22, lineHeight: 27, letterSpacing: -0.4 }, progress: { color: '#8E8A8D', fontFamily: 'PaperDeckInter-Regular', fontSize: 15, lineHeight: 19, textAlign: 'center', marginTop: 6, fontVariant: ['tabular-nums'] }, close: { position: 'absolute', right: 0, top: -29, width: 48, height: 48, alignItems: 'center', justifyContent: 'center' }, closeLeft: { right: undefined, left: 0 }, closeDisc: { width: 27, height: 27, borderRadius: 13.5, backgroundColor: '#F4F2F4', alignItems: 'center', justifyContent: 'center' }, chromeDisc: { width: 27, height: 27, borderRadius: 13.5, backgroundColor: '#2C2B2D', alignItems: 'center', justifyContent: 'center' },
  stage: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingTop: 8 }, card: { position: 'absolute', top: 0, left: 0, backfaceVisibility: 'hidden', shadowColor: '#000', shadowOpacity: 0.19, shadowOffset: { width: 0, height: 5 }, shadowRadius: 11 },
  cardCopy: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 22, paddingTop: 10, paddingBottom: 0 }, cardTitle: { color: '#141215', fontFamily: 'PaperDeckSerif-Semibold', fontSize: 37, lineHeight: 41, letterSpacing: -0.5, textAlign: 'center' }, symbol: { fontSize: 28, lineHeight: 36, marginTop: 40 }, number: { color: '#1F1B1F', textAlign: 'center', position: 'absolute', bottom: 38, left: 0, right: 0, fontFamily: 'PaperDeckSerif-Semibold', fontSize: 19, lineHeight: 24 },
  illustrated: { position: 'absolute', top: 0, left: 0, zIndex: 20, backfaceVisibility: 'hidden' }, coverBack: { ...StyleSheet.absoluteFill, borderRadius: 27, overflow: 'hidden', backgroundColor: '#1B2240' }, backShade: { ...StyleSheet.absoluteFill, backgroundColor: '#00000024' }, coverClip: { flex: 1, borderRadius: 27, overflow: 'hidden', backgroundColor: '#9260dc' }, coverImage: { width: '100%', height: '100%' },
  sheenClip: { overflow: 'hidden', borderRadius: 24 }, sheen: { position: 'absolute', top: 0, bottom: 0, left: 0 },
});
