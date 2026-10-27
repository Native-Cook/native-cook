import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { type ReactNode, useCallback, useEffect, useMemo, useState } from 'react';
import { StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector, ScrollView } from 'react-native-gesture-handler';
import Animated, { cancelAnimation, Easing, FadeInDown, interpolate, type SharedValue, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useSharedValue, withSpring } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';
import Svg, { Circle, Defs, RadialGradient, Stop } from 'react-native-svg';

import { ProgressiveBlurViewport } from '@/shared/SkiaProgressiveBlur';

import { clamp, enter, rubber, segment, usePlayback } from './motion';
import { BodyScan, GuidePortrait, MiniDashboard, NocturneMark, TabBar } from './NocturneArtwork';
import { TourButton } from './TourButton';

// The reference is a 15-second three-part story; two seconds close its loop.
export const LOOP = 17;
const ANCHORS = [4.3, 8.4, 12];
const PAGES = [
  { title: 'An evening that fits you', body: 'A small, thoughtful routine.\nMake it yours, one night at a time.' },
  { title: 'Notice your rhythm', body: 'Check in with your evenings.\nSee the habits you are building.' },
  { title: 'Make space to unwind', body: 'Slow down and notice how you feel.\nA little attention goes a long way.' },
];

// Incoming-card edge samples include the long settling tail observed at 60fps.
function sourceCardSlide(time: number, start: number) {
  'worklet';
  return interpolate(time - start, [0, .016, .033, .083, .116, .133, .15, .183, .25, .283, .35, .466, .683], [0, .033, .104, .278, .481, .566, .632, .736, .868, .906, .953, .991, 1], 'clamp');
}

function timelinePage(time: number) {
  'worklet';
  return time >= 16.1 ? 0 : sourceCardSlide(time, 4.817) + sourceCardSlide(time, 9.834);
}

function CardFrame({ index, position, width, stride, reduced, children }: { index: number; position: SharedValue<number>; width: number; stride: number; reduced: boolean; children: ReactNode }) {
  // Neighbours stand back a little and turn toward the centre, so the row reads as a curved shelf.
  const style = useAnimatedStyle(() => {
    const d = index - position.get();
    return { transform: [{ perspective: 900 }, { scale: reduced ? 1 : 1 - .14 * clamp(Math.abs(d)) }, { rotateY: `${reduced ? 0 : clamp(d, -1, 1) * -9}deg` }] };
  });
  // Cards waiting at the edges sink into the night, so the one in focus is the only lit object.
  const shade = useAnimatedStyle(() => ({ opacity: .58 * clamp(Math.abs(index - position.get())) }));
  return <Animated.View style={[{ width, marginRight: stride - width, borderRadius: 22, overflow: 'hidden' }, style]}>{children}<Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, { backgroundColor: '#050C1A' }, shade]} /></Animated.View>;
}

// Faint stars drift around the mark while the welcome holds, the way the reference's sparks do.
const SPARKS = Array.from({ length: 11 }, (_, i) => {
  const angle = i * 2.399 + .4, radius = 58 + (i * 37) % 64;
  return { x: Math.cos(angle) * radius, y: Math.sin(angle) * radius * .72 - 22, size: i % 4 === 0 ? 3 : 2, delay: (i * .13) % .6, color: i % 3 === 0 ? '#7BE3D8' : '#8FB1FF' };
});
function Spark({ spark, time, reduced }: { spark: typeof SPARKS[number]; time: SharedValue<number>; reduced: boolean }) {
  const style = useAnimatedStyle(() => {
    const t = time.get();
    const shown = enter(segment(t, .2 + spark.delay, .8 + spark.delay)) * (1 - enter(segment(t, 1.35, 1.85)));
    const twinkle = .55 + .45 * Math.sin(t * 5.1 + spark.delay * 9);
    return { opacity: reduced ? 0 : shown * twinkle, transform: [{ translateX: spark.x * (1 + .06 * t) }, { translateY: spark.y * (1 + .06 * t) - 6 * t }] };
  });
  return <Animated.View style={[{ position: 'absolute', width: spark.size, height: spark.size, borderRadius: spark.size, backgroundColor: spark.color, boxShadow: `0 0 6px ${spark.color}` }, style]} />;
}

// The artwork lags its card by a fifth of a card width, a window onto a deeper scene.
function Parallax({ index, position, distance, reduced, children }: { index: number; position: SharedValue<number>; distance: number; reduced: boolean; children: ReactNode }) {
  const style = useAnimatedStyle(() => ({ transform: [{ translateX: reduced ? 0 : clamp(index - position.get(), -1, 1) * -distance }] }));
  return <Animated.View style={style} importantForAccessibility="no-hide-descendants" accessibilityElementsHidden>{children}</Animated.View>;
}

function CardCopy({ index, time, reduced, children }: { index: number; time: SharedValue<number>; reduced: boolean; children: ReactNode }) {
  const reveal = useAnimatedStyle(() => {
    const local = time.get() - (index === 2 ? 5.017 : 0);
    const shown = reduced || index === 0 ? 1 : interpolate(local, [5, 5.05, 5.1, 5.15, 5.2, 5.25, 5.3, 5.4], [0, .117, .224, .384, .581, .733, .905, 1], 'clamp');
    return { opacity: shown, filter: `blur(${((1 - shown) * 5).toFixed(2)}px)`, transform: [{ translateY: (1 - shown) * 6 }] };
  });
  return <Animated.View style={[styles.cardCopy, reveal]}>{children}</Animated.View>;
}

// The plan arrives as a short cascade, 70 ms a line, each line settling 12 pt.
function rise(order: number, reduced: boolean) {
  return reduced ? undefined : FadeInDown.duration(380).delay(80 + order * 70).easing(Easing.bezier(.23, 1, .32, 1)).withInitialValues({ opacity: 0, transform: [{ translateY: 12 }] });
}

export type NocturneProgramCarouselProps = { autoPlay?: boolean; /** Freeze a normalized timeline phase in seconds for deterministic inspection. */ previewTime?: number; onStartPlan?: () => void; onPageChange?: (page: number) => void };

export function NocturneProgramCarousel({ autoPlay = false, previewTime, onStartPlan, onPageChange }: NocturneProgramCarouselProps) {
  const { width, height, fontScale } = useWindowDimensions();
  const largeText = fontScale > 1.35;
  const layoutKey = `${width}:${fontScale}`;
  const [cardMeasurement, setCardMeasurement] = useState({ key: '', height: 0 });
  const insets = useSafeAreaInsets();
  const playback = usePlayback(LOOP, autoPlay, ANCHORS[0], previewTime);
  const { time, enabled, playing, reduced, pause, settle, stop, restart, remember, readingHandlers, beginGesture, endGesture, previewing, previewPhase } = playback;
  const timelineDriven = autoPlay || previewing;
  const [page, setPage] = useState(() => previewPhase === undefined ? 0 : Math.round(timelinePage(previewPhase)));
  const controls = { onPressIn: pause, onPressOut: () => settle(ANCHORS[page]) };
  const [plan, setPlan] = useState(false);
  const [programVisible, setProgramVisible] = useState(!timelineDriven || reduced || (previewPhase !== undefined && previewPhase >= 2.25 && previewPhase < 15.5));
  const manual = useSharedValue(0);
  const touching = useSharedValue(false);
  const startPage = useSharedValue(0);
  const panning = useSharedValue(false);
  const cardWidth = Math.min(width * .76, 370);
  const stride = cardWidth * (.735 / .7556);
  const artHeight = largeText ? 210 : Math.max(206, Math.min(304, height - insets.top - insets.bottom - 380));
  const minimumCardHeight = artHeight + 150;
  const cardHeight = cardMeasurement.key === layoutKey ? cardMeasurement.height : artHeight + 250 * Math.max(1, fontScale);
  const measureCard = (measuredHeight: number) => {
    const next = Math.ceil(measuredHeight);
    setCardMeasurement(current => current.key === layoutKey && current.height >= next ? current : { key: layoutKey, height: Math.max(current.key === layoutKey ? current.height : 0, next) });
  };
  useAnimatedReaction(() => !timelineDriven || (!previewing && touching.get()) || reduced || (time.get() >= 2.25 && time.get() < 15.5), (next, previous) => { if (next !== previous) scheduleOnRN(setProgramVisible, next); });
  const position = useDerivedValue(() => !previewing && touching.get() ? manual.get() : timelinePage(time.get()));

  const announcePage = useCallback((next: number) => {
    setPage(next);
    remember(ANCHORS[next]);
    onPageChange?.(next);
  }, [onPageChange, remember]);
  useAnimatedReaction(() => clamp(Math.round(position.get()), 0, 2), (next, previous) => {
    if (next !== previous) scheduleOnRN(announcePage, next);
  });
  useEffect(() => {
    if (playing) touching.set(false);
  }, [playing, touching]);

  const landed = useCallback((next: number) => {
    settle(ANCHORS[next]);
    void Haptics.selectionAsync();
  }, [settle]);
  const go = useCallback((next: number) => {
    if (previewing) return;
    const target = Math.max(0, Math.min(2, next));
    pause();
    manual.set(position.get());
    touching.set(true);
    manual.set(reduced ? target : withSpring(target, { duration: 400, dampingRatio: .8 }));
    landed(target);
  }, [landed, manual, pause, position, previewing, reduced, touching]);
  const pan = useMemo(() => Gesture.Pan().enabled(!previewing).activeOffsetX([-10, 10]).failOffsetY([-18, 18]).maxPointers(1)
    .onStart(() => { panning.set(true);
      enabled.set(false);
      cancelAnimation(manual);
      manual.set(position.get());
      startPage.set(manual.get());
      touching.set(true);
      scheduleOnRN(beginGesture);
    })
    .onUpdate(event => {
      if (!reduced) manual.set(rubber(startPage.get() - event.translationX / stride, 2));
    })
    .onFinalize((event, success) => {
      if (!panning.get()) return;
      panning.set(false);
      scheduleOnRN(endGesture);
      const direction = (Math.abs(event.velocityX) > 450 ? event.velocityX : event.translationX) < 0 ? 1 : -1;
      const moved = success && (Math.abs(event.translationX) > stride * .18 || Math.abs(event.velocityX) > 450);
      const target = clamp(Math.round(startPage.get()) + (moved ? direction : 0), 0, 2);
      manual.set(reduced ? target : withSpring(target, { duration: 400, dampingRatio: .8, velocity: -event.velocityX / stride }));
      if (moved && target !== Math.round(startPage.get())) scheduleOnRN(landed, target);
      else scheduleOnRN(settle, ANCHORS[target]);
    }), [beginGesture, enabled, endGesture, landed, manual, panning, position, previewing, reduced, settle, startPage, stride, touching]);

  const trackStyle = useAnimatedStyle(() => ({ transform: [{ translateX: (width - cardWidth) / 2 - position.get() * stride }] }));
  const programStyle = useAnimatedStyle(() => ({ opacity: !timelineDriven || (!previewing && touching.get()) || reduced ? 1 : enter(segment(time.get(), 1.5, 2.25)) * (1 - enter(segment(time.get(), 15.5, 16.1))) }));
  const welcomeStyle = useAnimatedStyle(() => ({ opacity: !timelineDriven || (!previewing && touching.get()) || reduced ? 0 : enter(segment(time.get(), 0, .5)) * (1 - enter(segment(time.get(), 1.5, 2))), transform: [{ scale: reduced ? 1 : 1 + .06 * (1 - enter(segment(time.get(), 0, .5))) }] }));
  const sheenStyle = useAnimatedStyle(() => {
    const t = time.get();
    const start = t < 6 ? 2.4 : 8.9;
    const progress = segment(t, start, start + .65);
    return { opacity: reduced || !timelineDriven || t < start || t > start + .65 ? 0 : .45, transform: [{ translateX: (width + 64) * progress }, { rotateZ: '18deg' }] };
  });

  function startPlan() {
    if (previewing) return;
    stop();
    setPlan(true);
    onStartPlan?.();
    void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
  }

  const header = <View style={styles.header}><NocturneMark /><Text maxFontSizeMultiplier={1.5} style={styles.heading}>The Nocturne program</Text><Text maxFontSizeMultiplier={1.8} style={styles.subheading}>A few good habits. A little more calm.</Text></View>;

  return <View style={styles.root}><StatusBar style="light" />
    <LinearGradient colors={['#0A1A3A', '#03101F', '#010A16']} locations={[0, .3, 1]} style={StyleSheet.absoluteFill} />
    {plan && !previewing ? <ProgressiveBlurViewport style={{ flex: 1 }} tint="dark" edgeSize={30}><ScrollView contentContainerStyle={[styles.result, { paddingTop: insets.top + 32, paddingBottom: insets.bottom + 14 }]}>
      <Animated.View entering={rise(0, reduced)}><NocturneMark size={50} /></Animated.View>
      <Animated.View entering={rise(1, reduced)}><Text style={styles.resultEyebrow}>YOUR EVENING PLAN</Text><Text style={styles.resultTitle}>A softer landing.</Text></Animated.View>
      <Animated.View entering={rise(2, reduced)}><Text style={styles.resultBody}>Three small moments to make tonight your own.</Text></Animated.View>
      <View style={styles.planList}>{[['21:15', 'Put the day down', 'Write one thing you want to leave for tomorrow.'], ['21:25', 'Find a quiet minute', 'Dim the room and take a comfortable breath.'], ['21:30', 'Choose something gentle', 'A few pages, a warm drink, a little less noise.']].map(([clock, title, body], i) => <Animated.View entering={rise(3 + i, reduced)} key={clock} style={styles.planRow}><Text style={styles.planTime}>{clock}</Text><View style={{ flex: 1 }}><Text style={styles.planTitle}>{title}</Text><Text style={styles.planBody}>{body}</Text></View></Animated.View>)}</View>
      <View style={{ flex: 1 }} /><TourButton {...controls} reduced={reduced} onPress={() => { setPlan(false); touching.set(true); manual.set(page); restart(ANCHORS[page]); }}>Back to the tour</TourButton>
    </ScrollView></ProgressiveBlurViewport> : <>
      <Animated.View pointerEvents="none" accessibilityElementsHidden={programVisible} importantForAccessibility={programVisible ? "no-hide-descendants" : "auto"} style={[styles.welcome, welcomeStyle]}><View style={styles.welcomeMark}><Svg pointerEvents="none" width={180} height={180} style={styles.welcomeHalo}><Defs><RadialGradient id="nocturne-halo"><Stop offset="0" stopColor="#3E7BFF" stopOpacity={.34} /><Stop offset="0.45" stopColor="#3E7BFF" stopOpacity={.1} /><Stop offset="1" stopColor="#3E7BFF" stopOpacity={0} /></RadialGradient></Defs><Circle cx={90} cy={90} r={90} fill="url(#nocturne-halo)" /></Svg>{SPARKS.map((spark, i) => <Spark key={i} spark={spark} time={time} reduced={reduced} />)}<NocturneMark size={44} /></View><Text maxFontSizeMultiplier={1.6} style={styles.welcomeText}>Welcome to{ '\n' }a quieter evening</Text></Animated.View>
      <Animated.View pointerEvents={programVisible ? 'auto' : 'none'} accessibilityElementsHidden={!programVisible} importantForAccessibility={programVisible ? 'auto' : 'no-hide-descendants'} style={[styles.program, programStyle, { paddingTop: insets.top + 18 }]}>
        {!largeText && header}
        <ProgressiveBlurViewport style={{ flex: 1 }} tint="dark" edgeSize={30} enabled={programVisible}><ScrollView {...readingHandlers} key={layoutKey} style={{ flex: 1, minHeight: 0 }} contentContainerStyle={{ flexGrow: 1, justifyContent: largeText ? 'flex-start' : 'center', paddingTop: largeText ? 8 : 20, paddingBottom: largeText ? 8 : 20 + height * .028, gap: largeText ? 24 : 0 }} showsVerticalScrollIndicator={false}>
          {largeText && header}
          <GestureDetector gesture={pan}><View style={{ height: cardHeight, overflow: 'hidden' }} accessible accessibilityRole="adjustable" accessibilityLabel={`Nocturne tour, page ${page + 1} of 3. ${PAGES[page].title}. ${PAGES[page].body}`} accessibilityActions={[{ name: 'increment', label: 'Next page' }, { name: 'decrement', label: 'Previous page' }]} onAccessibilityAction={event => go(page + (event.nativeEvent.actionName === 'increment' ? 1 : -1))}>
            <Animated.View style={[styles.track, trackStyle]}>{PAGES.map((item, index) => <CardFrame key={item.title} index={index} position={position} width={cardWidth} stride={stride} reduced={reduced}><LinearGradient onLayout={event => measureCard(event.nativeEvent.layout.height)} colors={['#0B2A70', '#0A2560', '#071C48', '#061840']} locations={[0, .45, .75, 1]} style={[styles.card, { width: cardWidth, minHeight: minimumCardHeight }]}>
              <Parallax index={index} position={position} distance={cardWidth * .22} reduced={reduced}>{index === 0 ? <GuidePortrait time={time} reduced={reduced} height={artHeight} /> : index === 1 ? <MiniDashboard time={time} reduced={reduced} height={artHeight} /> : <BodyScan time={time} reduced={reduced} height={artHeight} />}</Parallax>
              <CardCopy index={index} time={time} reduced={reduced}><Text maxFontSizeMultiplier={1.5} style={styles.cardTitle}>{item.title}</Text><Text maxFontSizeMultiplier={1.7} style={styles.cardBody}>{item.body}</Text><View style={styles.cardDots} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">{PAGES.map((_, i) => <View key={i} style={[styles.cardDot, i === index && styles.cardDotActive]} />)}</View></CardCopy>
            </LinearGradient></CardFrame>)}</Animated.View>
          </View></GestureDetector>
        </ScrollView></ProgressiveBlurViewport>
        <View style={[styles.footer, { paddingBottom: Math.max(insets.bottom - 6, 8) }]}>
          <View style={{ borderRadius: 28, overflow: 'hidden' }}><TourButton {...controls} reduced={reduced} background="#005EFE" style={{ minHeight: 55 }} onPress={startPlan}>Build my evening plan</TourButton><Animated.View pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={[{ position: 'absolute', left: -64, top: '-50%', width: 64, height: '200%' }, sheenStyle]}><LinearGradient colors={['#FFFFFF00', '#FFFFFFAA', '#FFFFFF00']} start={{ x: 0, y: .5 }} end={{ x: 1, y: .5 }} style={{ flex: 1 }} /></Animated.View></View>
          <View style={{ opacity: 0 }}><TabBar /></View>
        </View>
      </Animated.View>
      {/* The tab bar belongs to the app around the tour, so it stays put while the welcome hands over. */}
      <View pointerEvents="none" style={[styles.tabs, { paddingBottom: Math.max(insets.bottom - 6, 8) }]}><TabBar /></View>
    </>}
  </View>;
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#010A16' }, program: { flex: 1 }, header: { alignItems: 'center', paddingHorizontal: 24, gap: 6 }, heading: { color: '#F2F5FD', fontSize: 22, lineHeight: 28, fontWeight: '700', letterSpacing: -.5, textAlign: 'center', marginTop: 8 }, subheading: { color: '#9AA8C6', fontSize: 14, lineHeight: 19, textAlign: 'center' }, track: { flexDirection: 'row', alignItems: 'stretch' }, card: { overflow: 'hidden', borderRadius: 18, borderWidth: 1, borderColor: '#2A4C9440', paddingTop: 14, boxShadow: '0 14px 30px #00040C80' }, cardCopy: { paddingHorizontal: 16, paddingTop: 14, paddingBottom: 20, alignItems: 'center' }, cardTitle: { color: '#FFFFFF', fontSize: 20, lineHeight: 26, fontWeight: '700', textAlign: 'center', letterSpacing: -.4 }, cardBody: { color: '#A3B3D4', fontSize: 14, lineHeight: 19, textAlign: 'center', marginTop: 12 }, cardDots: { flexDirection: 'row', gap: 4, marginTop: 22, alignItems: 'center' }, cardDot: { width: 4, height: 4, borderRadius: 2, backgroundColor: '#5D6F99' }, cardDotActive: { width: 14, backgroundColor: '#FFFFFF' }, footer: { paddingHorizontal: 30, gap: 34 }, tabs: { position: 'absolute', left: 0, right: 0, bottom: 0, paddingHorizontal: 30 }, navigation: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between' }, dots: { flexDirection: 'row' }, dot: { height: 5, borderRadius: 4 }, welcome: { position: 'absolute', top: 0, right: 0, bottom: 0, left: 0, alignItems: 'center', justifyContent: 'center', gap: 16, paddingBottom: 90 }, welcomeMark: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' }, welcomeHalo: { position: 'absolute', left: -68, top: -68 }, welcomeText: { color: '#F2F5FD', fontSize: 19, fontWeight: '700', lineHeight: 25, textAlign: 'center' },
  result: { flexGrow: 1, paddingHorizontal: 28 }, resultEyebrow: { color: '#78CFC8', fontSize: 11, letterSpacing: 2, marginTop: 24 }, resultTitle: { color: '#F1F5FC', fontSize: 36, fontWeight: '700', letterSpacing: -1, marginTop: 12 }, resultBody: { color: '#9AAEC9', fontSize: 16, lineHeight: 24, marginTop: 14 }, planList: { gap: 28, marginTop: 42 }, planRow: { flexDirection: 'row', gap: 20 }, planTime: { color: '#79DAD0', fontSize: 13, fontVariant: ['tabular-nums'], paddingTop: 3 }, planTitle: { color: '#DCE8F7', fontSize: 18, fontWeight: '600' }, planBody: { color: '#8CA0BD', fontSize: 14, lineHeight: 22, marginTop: 7 },
});
