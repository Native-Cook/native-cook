import { PressScale } from '@/shared/PressScale';
import { ProgressiveBlurViewport } from '@/shared/SkiaProgressiveBlur';
import * as Haptics from 'expo-haptics';
import { useFonts } from 'expo-font';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { Modal, StyleSheet, Text, View, useWindowDimensions, type StyleProp, type ViewStyle } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView, Pressable, ScrollView } from 'react-native-gesture-handler';
import Animated, { cancelAnimation, Easing, interpolate, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useSharedValue, withDelay, withSpring, withTiming, type SharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';
import Svg, { Path } from 'react-native-svg';
import { Gift, Mascot } from './art';
import { normalizeSceneTime, phase, useSceneClock } from './motion';

export const LOOP = 9.5;
const SOURCE_END = 8.4;
const ENTER = Easing.bezierFn(0.23, 1, 0.32, 1);
function fraction(t: number, a: number, b: number) { 'worklet'; if (!Number.isFinite(t)) return 0; if (t >= b - 1e-9) return 1; return Math.min(1, Math.max(0, (t - a) / (b - a))); }
function sourcePush(t: number) {
  'worklet';
  // Incoming-screen edge measured from source frames270–290 (60fps).
  return interpolate(t, [4.5, 4.5167, 4.5333, 4.55, 4.5667, 4.5833, 4.6, 4.6333, 4.6667, 4.7, 4.75, 4.8, 4.8333, 4.9], [0, .068, .183, .294, .407, .503, .546, .731, .832, .894, .95, .978, .989, 1], 'clamp');
}
function sourceSheet(t: number) {
  'worklet';
  return interpolate(t, [3.167, 3.2, 3.2167, 3.2333, 3.25, 3.2667, 3.2833, 3.3, 3.3333, 3.35, 3.3833, 3.4167, 3.4333], [0, .30, .377, .499, .601, .685, .756, .815, .903, .935, .978, .998, 1], 'clamp');
}
function sourceGiftAlpha(t: number) { 'worklet'; return t < .583 ? 1 : phase(t, 8.75, 9.1); }
function sourceOfferAlpha(t: number) {
  'worklet';
  // Neutral ticket luminance measured at source frames38–65, independent of scale.
  return interpolate(t, [.583, .633, .65, .667, .683, .7, .75, .8, .85, .9, .95, 1, 1.05, 1.0833, 1.1], [0, .097, .124, .171, .226, .263, .359, .479, .581, .682, .779, .871, .954, 1, 1], 'clamp') * (1 - phase(t, 4.9, 4.92));
}
function sourceSignupAlpha(t: number) { 'worklet'; return (t >= 4.5 ? 1 : 0) * (1 - phase(t, SOURCE_END, 8.75)); }
type Stage = 'gift' | 'opening' | 'offer' | 'exit' | 'signup' | 'complete' | 'dismissed';
export type ConfettiGiftOfferProps = { autoPlay?: boolean; previewTime?: number; onContinue?: (details: { provider: 'apple' | 'google'; offerAccepted: boolean }) => void; onDismiss?: () => void };
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
function stageAt(t: number): Stage { 'worklet'; t += 1e-9; return t < .583 || t >= 8.75 ? 'gift' : t < 1.1 ? 'opening' : t < 3.167 ? 'offer' : t < 4.9 ? 'exit' : 'signup'; }
type PressOwnership = { onPressIn: () => void; onPressOut: () => void };
function Button({ children, onPress, onPressIn, onPressOut, secondary = false, mint = false, danger = false, icon, disabled = false, reduced = false, style }: { children: ReactNode; onPress: () => void; secondary?: boolean; mint?: boolean; danger?: boolean; icon?: ReactNode; disabled?: boolean; reduced?: boolean; style?: StyleProp<ViewStyle> } & PressOwnership) {
  return <PressScale accessibilityRole="button" disabled={disabled} accessibilityState={{ disabled }} reduced={reduced} onPressIn={onPressIn} onPressOut={onPressOut} onPress={onPress} style={[styles.button, secondary && styles.secondaryButton, mint && styles.mintButton, danger && styles.dangerButton, disabled && { opacity: 0.6 }, style]}>{icon}<Text style={[styles.buttonLabel, secondary && styles.secondaryLabel, mint && styles.mintLabel, danger && styles.dangerLabel]}>{children}</Text></PressScale>;
}
// "Offer expire in" counts down from 59:59 once the ticket has settled, one tick a second.
function Countdown({ time, manual }: { time: SharedValue<number>; manual: boolean }) {
  const [label, setLabel] = useState('00:00');
  const start = useRef<number | null>(null);
  useAnimatedReaction(() => manual ? -2 : time.get() < 1.1 ? -1 : Math.floor(time.get() - 1.1), (tick, previous) => { if (tick !== previous && tick !== -2) scheduleOnRN(setLabel, tick < 0 ? '00:00' : `59:${String(59 - (tick % 60)).padStart(2, '0')}`); });
  useEffect(() => {
    if (!manual) { start.current = null; return; }
    start.current = Date.now();
    const tick = () => { const seconds = Math.max(0, 3599 - Math.floor((Date.now() - (start.current ?? Date.now())) / 1000)); setLabel(`${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`); };
    const first = setTimeout(tick, 0), timer = setInterval(tick, 1000);
    return () => { clearTimeout(first); clearInterval(timer); };
  }, [manual]);
  return <View style={styles.countdown}><Text style={styles.countdownText} accessibilityLabel={`Offer expires in ${label}`}>Offer expire in: {label}</Text></View>;
}
function AppleLogo() { return <Svg width={17} height={20} viewBox="0 0 17 20" accessible={false}><Path d="M14.1 10.6c0-2.6 2.1-3.8 2.2-3.9-1.2-1.8-3.1-2-3.7-2-1.6-.2-3.1.9-3.9.9-.8 0-2-.9-3.4-.9C3.6 4.8 1.9 5.8 1 7.4c-1.9 3.3-.5 8.1 1.3 10.8.9 1.3 1.9 2.7 3.3 2.7 1.3-.1 1.8-.9 3.4-.9 1.6 0 2 .9 3.4.8 1.4 0 2.3-1.3 3.2-2.6 1-1.5 1.4-2.9 1.4-3-.1 0-2.9-1.1-2.9-4.6ZM11.6 3c.7-.9 1.2-2 1.1-3.2-1 0-2.3.7-3 1.6-.7.8-1.2 2-1.1 3.1 1.1.1 2.3-.6 3-1.5Z" fill="#FFFFFF" /></Svg>; }
function GoogleLogo() { return <Svg width={18} height={18} viewBox="0 0 18 18" accessible={false}><Path d="M17.6 9.2c0-.6-.1-1.2-.2-1.8H9v3.4h4.8a4.1 4.1 0 0 1-1.8 2.7v2.2h2.9c1.7-1.6 2.7-3.9 2.7-6.5Z" fill="#4285F4" /><Path d="M9 18c2.4 0 4.5-.8 5.9-2.2L12 13.6c-.8.5-1.8.9-3 .9-2.3 0-4.3-1.6-5-3.7h-3v2.3A9 9 0 0 0 9 18Z" fill="#34A853" /><Path d="M4 10.8a5.4 5.4 0 0 1 0-3.5V5H1a9 9 0 0 0 0 8.1l3-2.3Z" fill="#FBBC05" /><Path d="M9 3.6c1.3 0 2.5.5 3.4 1.3L15 2.4A9 9 0 0 0 1 5l3 2.3c.7-2.1 2.7-3.7 5-3.7Z" fill="#EA4335" /></Svg>; }
// Paper confetti: each strip falls toward a terminal speed, sways on its own period and
// keeps turning over, so its width breathes as the face tips away from the light.
const CONFETTI_COLORS = ['#3E7BF5', '#EF5A4E', '#7FD8BC', '#F4C43E'];
function Confetti({ index, age, width, height, reduced }: { index: number; age: SharedValue<number>; width: number; height: number; reduced: boolean }) {
  const seed = ((index * 137 + 31) % 101) / 101;
  const verticalSeed = ((index * 47 + 17) % 103) / 103;
  const depth = index % 5 === 0 ? .62 : index % 3 === 0 ? .82 : 1;
  const style = useAnimatedStyle(() => {
    const t = age.get(); const life = Math.max(0, t);
    const fall = height * (1.05 * life - .32 * (1 - Math.exp(-life * 2.4))) * depth;
    const spin = life * (5 + seed * 6) + index;
    return {
      opacity: reduced || t < 0 || t > 1.7 ? 0 : ENTER(fraction(t, 0, .08)) * (1 - phase(t, 1.2, 1.7)),
      transform: [
        { translateX: Math.sin(life * (2.2 + seed * 1.6) + index) * (14 + seed * 22) },
        { translateY: height * (verticalSeed * 1.2 - .25) + fall },
        { rotate: `${index * 31 + life * (seed > .5 ? 210 : -190)}deg` },
        { scaleX: .3 + .7 * Math.abs(Math.cos(spin)) },
      ],
    };
  });
  const w = (index % 3 ? 11 : 14) * depth, h = (index % 3 ? 30 : 36) * depth;
  return <Animated.View pointerEvents="none" style={[styles.confetti, { left: seed * width, top: 0, width: w, height: h, borderRadius: w / 2, backgroundColor: CONFETTI_COLORS[index % 4], opacity: depth < 1 ? .85 : 1 }, style]} />;
}
function CloseIcon() {
  return <Svg width={20} height={20} viewBox="0 0 20 20" accessible={false}><Path d="m4.5 4.5 11 11M15.5 4.5l-11 11" fill="none" stroke="#8E8E93" strokeWidth={2.2} strokeLinecap="round" /></Svg>;
}
// The ticket's faint "%" watermark, as [left %, top %, rotation].
const PERCENTS: [number, number, number][] = [[6, 6, -18], [84, 5, 14], [20, 22, 10], [74, 24, -12], [6, 44, 16], [88, 42, -20], [30, 9, -8], [62, 40, 18]];
// Three marks drift and twinkle around the account mascot: a teal dot, a blue star and a teal star.
function Sparkles({ time, reduced }: { time: SharedValue<number>; reduced: boolean }) {
  const a = useAnimatedStyle(() => ({ opacity: reduced ? 1 : .55 + .45 * Math.sin(time.get() * 3.1), transform: [{ translateY: reduced ? 0 : Math.sin(time.get() * 1.7) * 4 }] }));
  const b = useAnimatedStyle(() => ({ opacity: reduced ? 1 : .5 + .5 * Math.sin(time.get() * 2.6 + 1.2), transform: [{ scale: reduced ? 1 : .85 + .2 * Math.sin(time.get() * 2.6 + 1.2) }] }));
  const c = useAnimatedStyle(() => ({ opacity: reduced ? 1 : .6 + .4 * Math.sin(time.get() * 2.2 + 2.4), transform: [{ scale: reduced ? 1 : .9 + .15 * Math.sin(time.get() * 2.2 + 2.4) }] }));
  const star = (size: number, color: string) => <Svg width={size} height={size} viewBox="0 0 20 20" accessible={false}><Path d="M10 0c.8 6.2 3.3 8.9 10 10-6.7 1.1-9.2 3.8-10 10-.8-6.2-3.3-8.9-10-10 6.7-1.1 9.2-3.8 10-10Z" fill={color} /></Svg>;
  return <View pointerEvents="none" style={StyleSheet.absoluteFill}>
    <Animated.View style={[{ position: 'absolute', left: 65, top: -33, width: 12, height: 12, borderRadius: 6, backgroundColor: '#8ED9CF' }, a]} />
    <Animated.View style={[{ position: 'absolute', left: 42, top: 6 }, b]}>{star(18, '#3D6FE0')}</Animated.View>
    <Animated.View style={[{ position: 'absolute', right: 62, top: 12 }, c]}>{star(26, '#8ED9CF')}</Animated.View>
  </View>;
}
function ExitSheet({ visible, foreground, reduced, own, stay, skip, sourceTime, push, autoDriven, pressOwnership }: { visible: boolean; foreground: boolean; reduced: boolean; own: () => void; stay: () => void; skip: () => void; sourceTime: SharedValue<number>; push: SharedValue<number>; autoDriven: boolean; pressOwnership: PressOwnership }) {
  const inset = useSafeAreaInsets(); const { height, width, fontScale } = useWindowDimensions();
  const maxHeight = Math.max(240, height - inset.top - 20), travel = maxHeight + 32;
  const offset = useSharedValue(travel), initial = useSharedValue(0), panelHeight = useSharedValue(height * .598);
  useEffect(() => { if (!visible || !foreground) { cancelAnimation(offset); if (!visible) offset.set(travel); return; } offset.set(reduced ? 0 : withSpring(0, { duration: 400, dampingRatio: 0.8 })); return () => cancelAnimation(offset); }, [foreground, offset, reduced, travel, visible]);
  const close = useCallback(() => { if (autoDriven) offset.set(panelHeight.get() * (1 - sourceSheet(sourceTime.get()))); own(); if (reduced) skip(); else offset.set(withSpring(travel, { duration: 300, dampingRatio: 1, overshootClamping: true }, finished => { if (finished) scheduleOnRN(skip); })); }, [autoDriven, offset, own, panelHeight, reduced, skip, sourceTime, travel]);
  const pan = useMemo(() => Gesture.Pan().maxPointers(1).activeOffsetY([-8, 8]).onStart(() => { cancelAnimation(offset); if (autoDriven) offset.set(panelHeight.get() * (1 - sourceSheet(sourceTime.get()))); initial.set(offset.get()); scheduleOnRN(own); }).onUpdate(event => { const next = initial.get() + event.translationY; offset.set(next >= 0 ? next : next / (1 + Math.abs(next) / 80) * 0.35); }).onEnd((event, success) => { if (!success) { offset.set(reduced ? 0 : withSpring(0, { duration: 400, dampingRatio: 0.8 })); return; } if (offset.get() > 90 || event.velocityY > 700) { if (reduced) scheduleOnRN(skip); else offset.set(withSpring(travel, { duration: 400, dampingRatio: 1, velocity: event.velocityY, overshootClamping: true }, finished => { if (finished) scheduleOnRN(skip); })); } else offset.set(reduced ? 0 : withSpring(0, { duration: 400, dampingRatio: 0.8, velocity: event.velocityY })); }), [autoDriven, initial, offset, own, panelHeight, reduced, skip, sourceTime, travel]);
  const sheet = useAnimatedStyle(() => ({ transform: [{ translateY: reduced ? 0 : autoDriven ? panelHeight.get() * (1 - sourceSheet(sourceTime.get())) : offset.get() }] }));
  const scrim = useAnimatedStyle(() => ({ opacity: autoDriven ? phase(sourceTime.get(), 3.233, 3.4) : Math.max(0, 1 - offset.get() / travel) }));
  const outgoing = useAnimatedStyle(() => ({ transform: [{ translateX: reduced ? 0 : -push.get() * width }] }));
  const beginReading = () => { if (autoDriven) offset.set(withSpring(0, { duration: 400, dampingRatio: .8 })); own(); };
  return <Modal visible={visible} transparent animationType="none" onRequestClose={close} statusBarTranslucent><GestureHandlerRootView style={styles.modalRoot}><Animated.View style={[styles.modalRoot, outgoing]}>
    <Animated.View style={[StyleSheet.absoluteFill, scrim]}><Pressable accessibilityRole="button" accessibilityLabel="Continue without the sample offer" {...pressOwnership} onPress={close} style={styles.scrim} /></Animated.View>
    <Animated.View onLayout={event => panelHeight.set(event.nativeEvent.layout.height)} accessibilityViewIsModal onAccessibilityEscape={close} style={[styles.sheet, { maxHeight, minHeight: fontScale <= 1.3 ? height * 516 / 874 : undefined }, sheet]}>
      <View style={styles.sheetTop}><GestureDetector gesture={pan}><Animated.View style={styles.dragZone} accessible accessibilityRole="button" accessibilityLabel="Drag down to skip the offer" accessibilityActions={[{ name: 'activate', label: 'Skip the offer' }]} onAccessibilityAction={close}><View style={styles.handle} /></Animated.View></GestureDetector></View>
      <ProgressiveBlurViewport tint="light" edgeSize={28} enabled={visible} style={[styles.sheetScroll, { maxHeight: maxHeight - 48 }]}><ScrollView onScrollBeginDrag={beginReading} onMomentumScrollBegin={beginReading} showsVerticalScrollIndicator style={[styles.sheetScroll, { maxHeight: maxHeight - 48 }]} contentContainerStyle={{ paddingBottom: inset.bottom + 18 }}>
        <View style={styles.sheetMascot}><Mascot pose="worried" size={190} /></View>
        <Text maxFontSizeMultiplier={1.5} style={styles.sheetTitle}>Are you sure to skip{'\n'}<Text style={styles.sheetAccent}>-70%</Text> offer?</Text>
        <View style={styles.sheetButtons}><Button {...pressOwnership} reduced={reduced} onPress={stay}>Back to offer</Button><Button {...pressOwnership} reduced={reduced} onPress={skip} danger>Skip this offer forever</Button></View>
      </ScrollView></ProgressiveBlurViewport>
    </Animated.View>
  </Animated.View></GestureHandlerRootView></Modal>;
}
export function ConfettiGiftOffer({ autoPlay = false, previewTime, onContinue }: ConfettiGiftOfferProps) {
  const { width, height, fontScale } = useWindowDimensions(); const inset = useSafeAreaInsets();
  // The reference shouts its headlines in a heavy condensed grotesk.
  const [fontsLoaded] = useFonts({ 'GiftBarlow-ExtraBold': require('./assets/BarlowCondensed-ExtraBold.ttf') });
  const [stage, setStage] = useState<Stage>(stageAt(previewTime === undefined ? 0 : normalizeSceneTime(previewTime, LOOP))), [manual, setManual] = useState(false), [accepted, setAccepted] = useState(false);
  const [manualExitVisible, setManualExitVisible] = useState(false);
  const manualSV = useSharedValue(false);
  const { time, anchor, foreground, reduced } = useSceneClock(LOOP, autoPlay && !manual, 1.5, 0, previewTime, manualSV);
  const giftAlpha = useSharedValue(1), offerAlpha = useSharedValue(0), signupAlpha = useSharedValue(0), lidOpen = useSharedValue(0), burst = useSharedValue(-1), openingTime = useSharedValue(0), manualPush = useSharedValue(0);
  const quiet = useRef<ReturnType<typeof setTimeout> | null>(null);
  const heldControls = useRef(0);
  const reading = useRef({ dragging: false, momentum: false });
  const ownedStage = useRef<Stage | null>(null);
  const clearQuiet = useCallback(() => { if (quiet.current) clearTimeout(quiet.current); quiet.current = null; }, []);
  const applyAutoStage = useCallback((next: Stage) => { if (!manualSV.get()) setStage(next); }, [manualSV]);
  useAnimatedReaction(() => manualSV.get() ? null : stageAt(time.get()), (next, previous) => { if (next !== null && next !== previous) scheduleOnRN(applyAutoStage, next); });
  const resumeFrom = useCallback((next: Stage) => { if (next !== 'gift' && next !== 'offer') return; time.set(next === 'gift' ? 0 : 1.5); anchor.set(-1); ownedStage.current = null; manualSV.set(false); setManual(false); }, [anchor, manualSV, time]);
  const armQuiet = useCallback((next: Stage) => { clearQuiet(); if (heldControls.current > 0 || reading.current.dragging || reading.current.momentum || !autoPlay || reduced || !foreground || (next !== 'gift' && next !== 'offer')) return; quiet.current = setTimeout(() => resumeFrom(next), 2000); }, [autoPlay, clearQuiet, foreground, reduced, resumeFrom]);
  const own = useCallback((next: Stage) => {
    clearQuiet();
    if (!manualSV.get()) { const t = time.get(); giftAlpha.set(sourceGiftAlpha(t)); offerAlpha.set(sourceOfferAlpha(t)); signupAlpha.set(sourceSignupAlpha(t)); manualPush.set(sourcePush(t)); }
    ownedStage.current = next; manualSV.set(true); setManual(true); setStage(next);
  }, [clearQuiet, giftAlpha, manualPush, manualSV, offerAlpha, signupAlpha, time]);
  const pressIn = useCallback(() => {
    heldControls.current += 1;
    clearQuiet();
    // Existing manual opening/push animations retain ownership and keep running.
    if (!manualSV.get()) own(stage);
  }, [clearQuiet, manualSV, own, stage]);
  const pressOut = useCallback(() => {
    if (heldControls.current === 0) return;
    heldControls.current -= 1;
    if (heldControls.current === 0) armQuiet(ownedStage.current ?? stageAt(time.get()));
  }, [armQuiet, time]);
  const pressOwnership = { onPressIn: pressIn, onPressOut: pressOut };
  const go = useCallback((next: Stage) => { own(next); if (next === 'gift' || next === 'offer') { setManualExitVisible(false); manualPush.set(reduced || next === 'gift' ? 0 : withTiming(0, { duration: 220, easing: EASE_OUT })); } armQuiet(next); }, [armQuiet, manualPush, own, reduced]);
  const finishOpening = useCallback(() => { giftAlpha.set(0); offerAlpha.set(1); burst.set(reduced ? -1 : .52); if (!reduced) burst.set(withTiming(1.7, { duration: 1180, easing: Easing.linear })); ownedStage.current = 'offer'; setStage('offer'); armQuiet('offer'); }, [armQuiet, burst, giftAlpha, offerAlpha, reduced]);
  const finishPush = useCallback(() => setManualExitVisible(false), []);
  useEffect(() => {
    if (!foreground) { heldControls.current = 0; reading.current = { dragging: false, momentum: false }; clearQuiet(); [giftAlpha, offerAlpha, signupAlpha, lidOpen, burst, openingTime, manualPush].forEach(cancelAnimation); return; }
    if (!manual) return;
    const duration = reduced ? 0 : 220;
    if (stage === 'opening') {
      openingTime.set(withTiming(.74, { duration: reduced ? 0 : Math.max(0, .74 - openingTime.get()) * 1000, easing: Easing.linear }, finished => { if (finished) scheduleOnRN(finishOpening); }));
      return () => cancelAnimation(openingTime);
    }
    giftAlpha.set(withTiming(stage === 'gift' ? 1 : 0, { duration, easing: EASE_OUT }));
    if (stage === 'signup') {
      signupAlpha.set(1);
      offerAlpha.set(withDelay(reduced ? 0 : 400, withTiming(0, { duration: 0 })));
      manualPush.set(withTiming(1, { duration: reduced ? 0 : Math.max(0, 1 - manualPush.get()) * 400, easing: EASE_OUT }, finished => { if (finished) scheduleOnRN(finishPush); }));
    } else {
      offerAlpha.set(withTiming(stage === 'offer' || stage === 'exit' ? 1 : 0, { duration, easing: EASE_OUT }));
      signupAlpha.set(withTiming(0, { duration, easing: EASE_OUT }));
    }
    if (stage === 'gift') lidOpen.set(0);
  }, [burst, clearQuiet, finishOpening, finishPush, foreground, giftAlpha, lidOpen, manual, manualPush, offerAlpha, openingTime, reduced, signupAlpha, stage]);
  useEffect(() => () => { clearQuiet(); [giftAlpha, offerAlpha, signupAlpha, lidOpen, burst, openingTime, manualPush].forEach(cancelAnimation); }, [burst, clearQuiet, giftAlpha, lidOpen, manualPush, offerAlpha, openingTime, signupAlpha]);
  const previousForeground = useRef(foreground);
  useEffect(() => { if (foreground && !previousForeground.current && manual) armQuiet(stage); previousForeground.current = foreground; }, [armQuiet, foreground, manual, stage]);
  const open = () => { own('opening'); openingTime.set(0); manualPush.set(0); void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {}); };
  const stay = useCallback(() => go('offer'), [go]);
  const pushToSignup = useCallback((acceptedOffer: boolean, retainSheet: boolean) => { setAccepted(acceptedOffer); setManualExitVisible(retainSheet); own('signup'); }, [own]);
  const skip = useCallback(() => pushToSignup(false, true), [pushToSignup]);
  const ownSheet = useCallback(() => own('exit'), [own]);
  const reset = () => { setAccepted(false); go('gift'); };
  const pushProgress = useDerivedValue(() => manualSV.get() ? manualPush.get() : sourcePush(time.get()));
  const openProgress = useDerivedValue(() => manualSV.get() ? stage === 'opening' ? fraction(openingTime.get(), 0, .22) : lidOpen.get() : time.get() >= 8.75 ? 0 : fraction(time.get(), .333, .583));
  const confettiAge = useDerivedValue(() => {
    if (manualSV.get()) return stage === 'opening' ? openingTime.get() - .22 : burst.get();
    return time.get() >= 4.05 && time.get() < 4.9 ? time.get() - 4.05 : time.get() - .583;
  });
  const confettiPresence = useAnimatedStyle(() => ({ opacity: 1, transform: [{ translateX: reduced ? 0 : -width * pushProgress.get() }] }));
  const giftStyle = useAnimatedStyle(() => ({ opacity: manualSV.get() ? stage === 'opening' ? openingTime.get() < .22 ? 1 : 0 : giftAlpha.get() : sourceGiftAlpha(time.get()) }));
  const offerStyle = useAnimatedStyle(() => {
    const reveal = manualSV.get() ? stage === 'opening' ? ENTER(fraction(openingTime.get() + .363, .617, 1.1)) : 1 : ENTER(fraction(time.get(), .617, 1.1));
    const alpha = manualSV.get() ? stage === 'opening' ? sourceOfferAlpha(openingTime.get() + .363) : offerAlpha.get() : sourceOfferAlpha(time.get());
    return { opacity: alpha, transform: [{ translateX: reduced ? 0 : -width * pushProgress.get() }, { scale: reduced ? 1 : .64 + .36 * reveal }] };
  });
  const offerWashStyle = useAnimatedStyle(() => {
    const alpha = manualSV.get() ? stage === 'opening' ? fraction(openingTime.get(), .22, .304) : stage === 'offer' || stage === 'exit' || stage === 'signup' ? 1 : 0 : fraction(time.get(), .583, .667) * (1 - phase(time.get(), 4.9, 4.92));
    return { opacity: alpha, transform: [{ translateX: reduced ? 0 : -width * pushProgress.get() }] };
  });
  const signupStyle = useAnimatedStyle(() => ({ opacity: manualSV.get() ? signupAlpha.get() : sourceSignupAlpha(time.get()), transform: [{ translateX: reduced ? 0 : width * (1 - pushProgress.get()) }] }));
  // Reference positions, in points of its 402 × 874 screen (the recording is 720 × 1560).
  const u = height / 874, w = width / 402;
  const largeText = fontScale > 1.3 || height < 700;
  const readingEvents = { onScrollBeginDrag: () => { reading.current.dragging = true; own(stage); }, onScrollEndDrag: () => { reading.current.dragging = false; armQuiet(stage); }, onMomentumScrollBegin: () => { reading.current.momentum = true; clearQuiet(); }, onMomentumScrollEnd: () => { reading.current.momentum = false; armQuiet(stage); } };
  const place = (top: number, h?: number) => largeText ? undefined : { position: 'absolute' as const, top: top * u, left: 0, right: 0, ...(h === undefined ? null : { height: h * u }) };
  const finish = (provider: 'apple' | 'google') => { own('complete'); onContinue?.({ provider, offerAccepted: accepted }); void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {}); };
  if (!fontsLoaded) return <View style={styles.screen} />;
  const sceneContent = [styles.sceneContent, largeText ? { paddingTop: inset.top + 64, paddingBottom: inset.bottom + 24 } : { minHeight: height }];
  return <View style={styles.screen}><StatusBar style="dark" />
    <Animated.View pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={[StyleSheet.absoluteFill, { backgroundColor: '#FFFFFF' }, offerWashStyle]} />
    <Animated.View pointerEvents={stage === 'gift' ? 'auto' : 'none'} accessibilityElementsHidden={stage !== 'gift'} importantForAccessibility={stage === 'gift' ? 'auto' : 'no-hide-descendants'} style={[StyleSheet.absoluteFill, giftStyle]}>
      <ProgressiveBlurViewport tint="light" edgeSize={28} style={{ flex: 1 }} enabled={stage === 'gift'}><ScrollView {...readingEvents} contentContainerStyle={sceneContent} showsVerticalScrollIndicator={false}>
        <View style={place(133)}><Text accessibilityRole="header" maxFontSizeMultiplier={1.35} style={styles.headline}>We have a gift!{'\n'}Just for you!</Text></View>
        <View style={[styles.giftHero, place(338, 310)]}><View style={{ transform: [{ scale: largeText ? .9 : w }] }}><Gift open={openProgress} /></View></View>
        <View style={[{ alignItems: 'center' }, place(765)]}><Button {...pressOwnership} reduced={reduced} onPress={open} style={styles.openButton}>Open now</Button></View>
      </ScrollView></ProgressiveBlurViewport>
    </Animated.View>
    <Animated.View pointerEvents={stage === 'offer' ? 'auto' : 'none'} accessibilityElementsHidden={stage !== 'offer'} importantForAccessibility={stage === 'offer' ? 'auto' : 'no-hide-descendants'} style={[StyleSheet.absoluteFill, offerStyle]}>
      <ProgressiveBlurViewport tint="light" edgeSize={28} style={{ flex: 1 }} enabled={stage === 'offer'}><ScrollView {...readingEvents} showsVerticalScrollIndicator={false} contentContainerStyle={sceneContent}>
        <View style={place(86)}><Text accessibilityRole="header" maxFontSizeMultiplier={1.35} style={styles.headline}>Limited time{'\n'}<Text style={styles.accent}>-70%</Text> offer</Text></View>
        <View style={[styles.ticket, largeText ? { marginTop: 92 } : { position: 'absolute', top: 262 * u, left: 16 * w, right: 17 * w, height: 463 * u }]}>
          {PERCENTS.map((p, index) => <Text key={index} accessible={false} style={[styles.percent, { left: `${p[0]}%`, top: `${p[1]}%`, transform: [{ rotate: `${p[2]}deg` }] }]}>%</Text>)}
          <View style={[styles.ticketBody, !largeText && { height: 313 * u }]}><Countdown time={time} manual={manual} /><Text style={styles.oldPrice}>$4.99</Text><Text style={styles.price}>$1.49</Text><Text style={styles.perMonth}>per month</Text></View>
          <View style={styles.perforation}><View style={[styles.notch, styles.leftNotch]} /><View style={[styles.notch, styles.rightNotch]} /></View>
          <View style={styles.ticketFoot}><Button {...pressOwnership} reduced={reduced} mint onPress={() => pushToSignup(true, false)} style={styles.claimButton}>Claim your offer</Button></View>
        </View>
        <View style={[styles.offerMascot, largeText ? { position: 'absolute', top: inset.top + 64 + 98 - 6, left: 0, right: 0 } : { position: 'absolute', top: 210 * u, left: 0, right: 0 }]} pointerEvents="none"><Mascot pose="hearts" size={128} /></View>
        <Text style={[styles.finePrint, !largeText && { position: 'absolute', top: 734 * u, left: 24, right: 24 }]}>First year $17.99, then $59.99/year.{'\n'}Cancel anytime.</Text>
      </ScrollView></ProgressiveBlurViewport>
    </Animated.View>
    <Animated.View pointerEvents={stage === 'signup' ? 'auto' : 'none'} accessibilityElementsHidden={stage !== 'signup'} importantForAccessibility={stage === 'signup' ? 'auto' : 'no-hide-descendants'} style={[StyleSheet.absoluteFill, signupStyle]}>
      <LinearGradient pointerEvents="none" colors={['#D9E3FB', '#E2E5F7', '#EAE9F0']} locations={[0, .5, 1]} style={StyleSheet.absoluteFill} />
      <ProgressiveBlurViewport tint="light" edgeSize={28} style={{ flex: 1 }} enabled={stage === 'signup'}><ScrollView {...readingEvents} contentContainerStyle={sceneContent} showsVerticalScrollIndicator={false}>
        <View style={place(122)}><Text accessibilityRole="header" maxFontSizeMultiplier={1.35} style={styles.headline}>Now let’s{'\n'}create account</Text></View>
        <Text style={[styles.signupCopy, place(218)]}>Save your progress & reach your goals</Text>
        <View style={[styles.signupMascot, place(352, 300)]}><Sparkles time={time} reduced={reduced} /><Mascot pose="love" size={312} /></View>
        <View style={[styles.signupButtons, !largeText && { position: 'absolute', top: 695 * u, left: 33 * w, right: 33 * w, marginTop: 0 }]}>
          <Button {...pressOwnership} reduced={reduced} onPress={() => finish('apple')} icon={<AppleLogo />}>Continue with Apple</Button>
          <Button {...pressOwnership} reduced={reduced} secondary onPress={() => finish('google')} icon={<GoogleLogo />}>Continue with Google</Button>
        </View>
      </ScrollView></ProgressiveBlurViewport>
    </Animated.View>
    {(stage === 'complete' || stage === 'dismissed') && <View style={[StyleSheet.absoluteFill, { backgroundColor: '#E7EDFE' }]}><ProgressiveBlurViewport tint="light" edgeSize={28} style={{ flex: 1 }}><ScrollView contentContainerStyle={[styles.finish, { minHeight: height, paddingTop: inset.top + 90, paddingBottom: inset.bottom + 24 }]}><Mascot pose="love" size={200} /><Text accessibilityRole="header" maxFontSizeMultiplier={1.35} style={styles.headline}>{stage === 'complete' ? 'You’re in.' : 'All good.'}</Text><Text accessibilityLiveRegion="polite" style={styles.finishCopy}>{stage === 'complete' ? 'This is a demo: no account was created and no payment was made.' : 'The offer is closed. You can come back whenever you like.'}</Text><Button {...pressOwnership} reduced={reduced} onPress={reset}>Replay the gift</Button></ScrollView></ProgressiveBlurViewport></View>}
    <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, confettiPresence]}>{Array.from({ length: 56 }, (_, index) => <Confetti key={index} index={index} age={confettiAge} width={width} height={height} reduced={reduced} />)}</Animated.View>
    <Animated.View pointerEvents={stage === 'offer' ? 'box-none' : 'none'} style={[styles.closeWrap, { top: largeText ? inset.top + 8 : 62 * u }, offerStyle]}><PressScale accessibilityRole="button" accessibilityLabel="Leave the offer" {...pressOwnership} reduced={reduced} hitSlop={4} onPress={() => go('exit')} style={styles.closeChip}><CloseIcon /></PressScale></Animated.View>
    <ExitSheet visible={stage === 'exit' || manualExitVisible} sourceTime={time} push={pushProgress} autoDriven={!manual} foreground={foreground} reduced={reduced} own={ownSheet} stay={stay} skip={skip} pressOwnership={pressOwnership} />
  </View>;
}
const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#E7EDFE' }, closeWrap: { position: 'absolute', left: 18, zIndex: 10 }, closeChip: { width: 44, height: 44, borderRadius: 22, backgroundColor: '#F3F3F3', alignItems: 'center', justifyContent: 'center' }, sceneContent: { paddingHorizontal: 24, alignItems: 'stretch', paddingBottom: 8 }, headline: { color: '#1C1C1E', fontFamily: 'GiftBarlow-ExtraBold', fontSize: 50, lineHeight: 49, letterSpacing: -0.6, textAlign: 'center' }, accent: { color: '#45BA51' }, giftHero: { alignItems: 'center', justifyContent: 'center' }, openButton: { width: 163, minHeight: 57, borderRadius: 29, backgroundColor: '#171717' }, button: { minHeight: 57, borderRadius: 29, backgroundColor: '#171717', alignItems: 'center', justifyContent: 'center', flexDirection: 'row', gap: 9, paddingHorizontal: 20, paddingVertical: 14 }, buttonLabel: { color: '#FFFFFF', fontSize: 17, lineHeight: 22, fontWeight: '600', textAlign: 'center' }, secondaryButton: { backgroundColor: '#FFFFFF59', borderWidth: 1.5, borderColor: '#D3D3DD' }, secondaryLabel: { color: '#1C1C1E' }, mintButton: { backgroundColor: '#45BA51', borderWidth: 5, borderColor: '#2F7A36' }, mintLabel: { color: '#FFFFFF' }, dangerButton: { backgroundColor: '#FFFFFF', borderWidth: 1.5, borderColor: '#F7CFCB' }, dangerLabel: { color: '#E5484D' }, finePrint: { color: '#8E8E93', fontSize: 14, lineHeight: 19, textAlign: 'center', marginTop: 16, marginBottom: 8 },
  offerMascot: { alignItems: 'center', zIndex: 2 }, ticket: { backgroundColor: '#222222', borderRadius: 44, overflow: 'hidden' }, ticketBody: { alignItems: 'center', justifyContent: 'flex-start', paddingTop: 103, minHeight: 300 }, percent: { position: 'absolute', color: '#2D2D2D', fontSize: 30, fontWeight: '800' }, countdown: { height: 32, paddingHorizontal: 15, borderRadius: 9, backgroundColor: '#6E3C36', alignItems: 'center', justifyContent: 'center' }, countdownText: { color: '#FF4D4D', fontSize: 14, fontWeight: '600', fontVariant: ['tabular-nums'] }, oldPrice: { color: '#8C8C8C', fontSize: 17, lineHeight: 22, fontWeight: '600', textDecorationLine: 'line-through', marginTop: 27 }, price: { color: '#FFFFFF', fontSize: 60, lineHeight: 66, fontWeight: '800', letterSpacing: -1.5, fontVariant: ['tabular-nums'], marginTop: 2 }, perMonth: { color: '#C2C2C2', fontSize: 18, lineHeight: 22, fontWeight: '500', marginTop: 2 }, perforation: { height: 0, borderTopWidth: 1.5, borderColor: '#4A4A4A', borderStyle: 'dashed', marginHorizontal: 0 }, ticketFoot: { flex: 1, justifyContent: 'center', paddingHorizontal: 37, paddingTop: 10 }, claimButton: { minHeight: 65, borderRadius: 33 }, notch: { position: 'absolute', width: 22, height: 22, borderRadius: 11, top: -11, backgroundColor: '#FFFFFF' }, leftNotch: { left: -11 }, rightNotch: { right: -11 },
  signupMascot: { alignItems: 'center', justifyContent: 'center' }, signupCopy: { color: '#8E8E9A', textAlign: 'center', fontSize: 17, lineHeight: 24, marginTop: 10 }, signupButtons: { gap: 14, marginTop: 24 }, inputLabel: { fontSize: 9, fontWeight: '700', letterSpacing: 1, color: '#80788D', marginBottom: 7, marginTop: 12 }, input: { minHeight: 50, borderRadius: 14, paddingHorizontal: 17, paddingVertical: 13, fontSize: 15, color: '#33313E', backgroundColor: '#FAF9FE', borderWidth: 1, borderColor: '#DFD9EE' }, error: { color: '#8A6074', fontSize: 11, lineHeight: 17, marginTop: 12, marginBottom: 17 }, textAction: { minHeight: 48, alignItems: 'center', justifyContent: 'center' }, textActionLabel: { fontSize: 13, color: '#797185', fontWeight: '600' }, finish: { flexGrow: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 32, gap: 20 }, finishCopy: { textAlign: 'center', fontSize: 15, lineHeight: 23, color: '#787188', marginBottom: 16 },
  confetti: { position: 'absolute', borderRadius: 3 }, modalRoot: { flex: 1, justifyContent: 'flex-end' }, scrim: { flex: 1, backgroundColor: 'rgba(0,0,0,.19)' }, sheet: { backgroundColor: '#FFFFFF', borderTopLeftRadius: 34, borderTopRightRadius: 34, paddingHorizontal: 33 }, sheetTop: { minHeight: 30 }, sheetScroll: { flexGrow: 0, flexShrink: 1 }, sheetClose: { position: 'absolute', top: 0, right: -10, width: 48, height: 48, alignItems: 'center', justifyContent: 'center' }, dragZone: { height: 30, alignItems: 'center', justifyContent: 'center' }, handle: { width: 22, height: 4, borderRadius: 2, backgroundColor: '#C7C7CC' }, sheetMascot: { alignItems: 'center', marginTop: 0, height: 178, justifyContent: 'center' }, sheetTitle: { color: '#1C1C1E', fontSize: 30, lineHeight: 37, fontWeight: '800', letterSpacing: -0.5, textAlign: 'center', marginTop: 7 }, sheetAccent: { color: '#F0605A' }, sheetButtons: { gap: 13, marginTop: 34 },
});
