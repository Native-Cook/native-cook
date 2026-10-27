import MaskedView from '@react-native-masked-view/masked-view';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector, ScrollView } from 'react-native-gesture-handler';
import Animated, { cancelAnimation, interpolate, type SharedValue, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useSharedValue, withSpring } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';
import Svg, { Defs, Ellipse, RadialGradient, Stop } from 'react-native-svg';

import { ProgressiveBlurViewport } from '@/shared/SkiaProgressiveBlur';
import { PressScale } from '@/shared/PressScale';
import { Ionicons } from '@expo/vector-icons';

import { clamp, enter, rubber, segment, usePlayback } from './motion';
import { SpectrumIllustration, SpectrumMark, SpectrumParticles } from './SpectrumArtwork';
import { TourButton } from './TourButton';

export const LOOP = 22;
const ANCHORS = [2.5, 7.5, 10, 11.75, 14];
const PAGES = [
  { title: 'Spectrum', body: 'An easier way to keep up\nwith the news', art: 'Spectrum dot emblem on a warm glowing field.' },
  { title: 'Stories, not articles', body: 'We show stories covered by\nmultiple sources, so you\nalways see varied perspectives.', art: 'Ten fictional publication badges orbit a blue news icon.' },
  { title: 'News selected for you', body: 'Spectrum gives you powerful\ncontrol over which stories you see,\nand which you don’t.', art: 'A person silhouette above red and green topic preferences.' },
  { title: 'Understand more, faster', body: 'Get a summary, cut straight to the\ndetails, listen on your drive, or dive\ninto the articles.', art: 'A blue summary panel above an article, a 16-perspective pill and audio-preview icon.' },
  { title: 'Ask questions', body: 'Spectrum finds answers.\nIf you need clarification, want further\ndetails, or sourced analysis…\njust ask.', art: 'An original waterfront-garden photograph beside a question and a short answer.' },
];

// Normalized positions sampled from the source's 60fps logo/orb landmarks.
function sourceSwipe(time: number, start: number) {
  'worklet';
  return interpolate(time - start, [0, .033, .05, .067, .1, .133, .167, .2, .233, .267, .3, .333, .383], [0, .104, .13, .17, .333, .51, .672, .791, .876, .931, .963, .985, 1], 'clamp');
}

function timelinePage(t: number) {
  'worklet';
  return sourceSwipe(t, 6.45) + sourceSwipe(t, 8.7) + sourceSwipe(t, 11.117) + sourceSwipe(t, 12.833) - sourceSwipe(t, 15.4)
    - interpolate(t, [16.2, 16.25, 16.3, 16.35, 16.4, 16.45, 16.5, 16.55, 16.65], [0, .093, .304, .563, .77, .893, .959, .989, 1], 'clamp')
    - interpolate(t, [17.1, 17.167, 17.25, 17.5, 17.75, 18, 18.05, 18.1, 18.15, 18.2, 18.25, 18.3, 18.35, 18.45], [0, .026, .059, .119, .2, .252, .281, .33, .46, .667, .822, .911, .963, 1], 'clamp')
    - interpolate(t, [19, 19.083, 19.167, 19.3, 19.5, 19.75, 20, 20.05, 20.1, 20.15, 20.2, 20.25, 20.3, 20.4], [0, .011, .043, .074, .119, .23, .319, .47, .574, .707, .844, .93, .974, 1], 'clamp');
}

type WelcomeFrame = { x: number; y: number; width: number; height: number };

function WarmBackdrop({ frame, glow, page, width, height }: { frame: SharedValue<WelcomeFrame>; glow: SharedValue<number>; page: SharedValue<number>; width: number; height: number }) {
  const style = useAnimatedStyle(() => {
    const rect = frame.get();
    return { opacity: clamp(1 - page.get()) * glow.get(), transform: [{ translateX: (rect.x + rect.width / 2 - .5) * width }, { translateY: (rect.y + rect.height / 2 - .5) * height }, { scaleX: rect.width }, { scaleY: rect.height }] };
  });
  // A light field, not a flat gradient: hot magenta in the top-right corner, amber on the left,
  // a blush of green underfoot, and each edge glowing faintly inward like a lit screen bezel.
  const blobs: [number, number, number, number, string, number][] = [
    [.98, .0, .78, .34, '#F06BD8', .78], [.02, .18, .62, .32, '#FFAA3D', .72], [.38, .06, .62, .2, '#FFE16A', .7],
    [.64, .14, .5, .2, '#FF92B6', .5], [-.04, .48, .18, .36, '#FFBE4A', .55], [1.04, .46, .17, .42, '#F27FCD', .52],
    [.22, 1.04, .62, .13, '#94D67F', .62], [.88, 1.03, .44, .11, '#F4A2CB', .42],
  ];
  return <Animated.View pointerEvents="none" style={[styles.warmBackdrop, style]}>
    <Svg width={width} height={height} style={StyleSheet.absoluteFill}>
      <Defs>{blobs.map(([, , , , color, alpha], i) => <RadialGradient key={i} id={`spectrum-warm-${i}`}><Stop offset="0" stopColor={color} stopOpacity={alpha} /><Stop offset="0.55" stopColor={color} stopOpacity={alpha * .38} /><Stop offset="1" stopColor={color} stopOpacity={0} /></RadialGradient>)}</Defs>
      {blobs.map(([x, y, rx, ry], i) => <Ellipse key={i} cx={x * width} cy={y * height} rx={rx * width} ry={ry * height} fill={`url(#spectrum-warm-${i})`} />)}
    </Svg>
  </Animated.View>;
}

function NewsPage({ index, width, time, page, reduced, autoPlay, manualMode, scale, onMeasure }: { index: number; width: number; time: SharedValue<number>; page: SharedValue<number>; reduced: boolean; autoPlay: boolean; manualMode: SharedValue<boolean>; scale: number; onMeasure: (height: number) => void }) {
  const style = useAnimatedStyle(() => {
    const distance = index - page.get();
    const intro = index !== 0 || reduced || !autoPlay || manualMode.get() ? 1 : enter(segment(time.get(), 0, .5)) * (1 - enter(segment(time.get(), 21.6, 22)));
    return { opacity: reduced ? (Math.round(page.get()) === index ? 1 : 0) : clamp(1.3 - Math.abs(distance) * .3) * intro, transform: [{ translateX: reduced ? 0 : distance * width }] };
  });
  // Copy hands over faster than the art, so two headlines never meet mid-swipe.
  const copyStyle = useAnimatedStyle(() => ({ opacity: reduced ? 1 : clamp(1 - Math.abs(index - page.get()) * 1.7), transform: [{ translateX: reduced ? 0 : (index - page.get()) * -width * .18 }] }));
  return <Animated.View onLayout={event => onMeasure(event.nativeEvent.layout.height)} pointerEvents="none" style={[styles.page, { width }, style]} importantForAccessibility="no-hide-descendants" accessibilityElementsHidden><SpectrumIllustration page={index} time={time} scale={scale} /><Animated.View style={[styles.copy, index === 0 && styles.copyWelcome, copyStyle]}>{index === 0 ? <Text style={styles.tagline}>{PAGES[0].body}</Text> : <><Text style={styles.heading}>{PAGES[index].title}</Text><Text style={styles.body}>{PAGES[index].body}</Text></>}</Animated.View></Animated.View>;
}

export type SpectrumNewsCarouselProps = { autoPlay?: boolean; /** Freeze a normalized timeline phase in seconds for deterministic inspection. */ previewTime?: number; onOpenBriefing?: () => void; onPageChange?: (page: number) => void };

export function SpectrumNewsCarousel({ autoPlay = false, previewTime, onOpenBriefing, onPageChange }: SpectrumNewsCarouselProps) {
  const { width, height, fontScale } = useWindowDimensions();
  const largeText = fontScale > 1.35;
  const layoutKey = `${width}:${fontScale}`;
  const [sceneMeasurement, setSceneMeasurement] = useState({ key: '', height: 0 });
  const insets = useSafeAreaInsets();
  const { time, enabled, playing, reduced, pause, settle, stop, restart, remember, readingHandlers, beginGesture, endGesture, previewing, previewPhase } = usePlayback(LOOP, autoPlay, ANCHORS[0], previewTime);
  const timelineDriven = autoPlay || previewing;
  const [page, setPage] = useState(() => previewPhase === undefined ? 0 : Math.round(timelinePage(previewPhase)));
  const controls = { onPressIn: pause, onPressOut: () => settle(ANCHORS[page]) };
  const [briefing, setBriefing] = useState(false);
  const [saved, setSaved] = useState(false);
  const [introReady, setIntroReady] = useState(!timelineDriven || reduced || (previewPhase !== undefined && previewPhase >= 1.5 && previewPhase < 21.4));
  const manualMode = useSharedValue(!timelineDriven);
  const renderManual = useDerivedValue(() => !previewing && manualMode.get());
  const manual = useSharedValue(0);
  const startPage = useSharedValue(0);
  const panning = useSharedValue(false);
  const position = useDerivedValue(() => renderManual.get() ? manual.get() : timelinePage(time.get()));
  const welcomeFrame = useDerivedValue<WelcomeFrame>(() => {
    if (reduced || !timelineDriven || renderManual.get()) return { x: 0, y: 0, width: 1, height: 1 };
    const t = time.get();
    const sample = t > 21.4 ? interpolate(t, [21.4, 22], [1.75, 0], 'clamp') : t;
    const knots = [0, .75, 1, 1.25, 1.5, 1.75];
    return {
      x: interpolate(sample, knots, [.39, .3593, .2185, .0926, .0259, 0], 'clamp'),
      y: interpolate(sample, knots, [.2621, .2586, .1575, .0634, .0274, 0], 'clamp'),
      width: interpolate(sample, knots, [.22, .2815, .563, .8148, .9481, 1], 'clamp'),
      height: interpolate(sample, knots, [.13, .1575, .4846, .7705, .9264, 1], 'clamp'),
    };
  });
  const welcomeGlow = useDerivedValue(() => reduced || !timelineDriven || renderManual.get() ? 1 : enter(segment(time.get(), .6, .75)) * (1 - enter(segment(time.get(), 21.6, 22))));
  const expansionMask = useAnimatedStyle(() => {
    const rect = welcomeFrame.get();
    // Only this absolute childless mask changes layout; content keeps its native geometry.
    // The card's corners open out to the screen's own as it fills it.
    return { left: rect.x * width, top: rect.y * height, width: rect.width * width, height: rect.height * height, borderRadius: interpolate(rect.width, [.9, 1], [28, 0], 'clamp') };
  });
  useAnimatedReaction(() => !timelineDriven || reduced || renderManual.get() || (time.get() >= 1.5 && time.get() < 21.4), (next, previous) => { if (next !== previous) scheduleOnRN(setIntroReady, next); });
  const scale = Math.min((width - 64) / 300, Math.max(.7, (height - insets.top - insets.bottom - 365) / 285), 1.08);
  const sceneHeight = sceneMeasurement.key === layoutKey ? sceneMeasurement.height : 285 * scale + 210 * Math.max(1, fontScale);
  const measureScene = (measuredHeight: number) => {
    const next = Math.ceil(measuredHeight);
    setSceneMeasurement(current => current.key === layoutKey && current.height >= next ? current : { key: layoutKey, height: Math.max(current.key === layoutKey ? current.height : 0, next) });
  };
  const announcePage = useCallback((next: number) => { setPage(next); remember(ANCHORS[next]); onPageChange?.(next); }, [onPageChange, remember]);
  useAnimatedReaction(() => clamp(Math.round(position.get()), 0, 4), (next, previous) => { if (next !== previous) scheduleOnRN(announcePage, next); });
  useEffect(() => { if (playing) manualMode.set(false); }, [manualMode, playing]);
  const landed = useCallback((next: number) => { settle(ANCHORS[next]); void Haptics.selectionAsync(); }, [settle]);
  const go = useCallback((next: number) => {
    if (previewing) return;
    const target = Math.max(0, Math.min(4, next));
    pause(); manual.set(position.get()); manualMode.set(true);
    manual.set(reduced ? target : withSpring(target, { duration: 400, dampingRatio: .8 }));
    landed(target);
  }, [landed, manual, manualMode, pause, position, previewing, reduced]);
  const pan = useMemo(() => Gesture.Pan().enabled(!previewing).activeOffsetX([-10, 10]).failOffsetY([-18, 18]).maxPointers(1)
    .onStart(() => { panning.set(true); enabled.set(false); cancelAnimation(manual); manual.set(position.get()); startPage.set(manual.get()); manualMode.set(true); scheduleOnRN(beginGesture); })
    .onUpdate(event => { if (!reduced) manual.set(rubber(startPage.get() - event.translationX / width, 4)); })
    .onFinalize((event, success) => {
      if (!panning.get()) return;
      panning.set(false);
      scheduleOnRN(endGesture);
      const moved = success && (Math.abs(event.translationX) > width * .18 || Math.abs(event.velocityX) > 450);
      const target = clamp(Math.round(startPage.get()) + (moved ? (Math.abs(event.velocityX) > 450 ? event.velocityX : event.translationX) < 0 ? 1 : -1 : 0), 0, 4);
      manual.set(reduced ? target : withSpring(target, { duration: 400, dampingRatio: .8, velocity: -event.velocityX / width }));
      if (moved && target !== Math.round(startPage.get())) scheduleOnRN(landed, target);
      else scheduleOnRN(settle, ANCHORS[target]);
    }), [beginGesture, enabled, endGesture, landed, manual, manualMode, panning, position, previewing, reduced, settle, startPage, width]);

  function openBriefing() {
    if (previewing) return;
    stop(); setBriefing(true); onOpenBriefing?.();
    void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
  }

  const pageControls = <View style={styles.pager}>{PAGES.map((_, i) => <TourButton {...controls} key={i} subtle reduced={reduced} onPress={() => go(i)} label={`Page ${i + 1}: ${PAGES[i].title}`} style={{ width: 16, minWidth: 16 }}><View style={[styles.dot, { backgroundColor: page === i ? '#2B292E' : '#DAD7D2' }]} /></TourButton>)}</View>;

  return <View style={styles.root}><StatusBar style="dark" />
    {briefing && !previewing ? <ProgressiveBlurViewport style={{ flex: 1 }} tint="light" edgeSize={30}><ScrollView contentContainerStyle={[styles.briefing, { paddingTop: insets.top + 24, paddingBottom: insets.bottom + 20 }]}>
      <View style={styles.briefingBrand}><SpectrumMark size={34} /><Text allowFontScaling={false} style={styles.briefingWordmark}>Spectrum</Text></View><Text style={styles.date}>YOUR DAILY BRIEFING</Text><Text style={styles.briefingTitle}>Room for a{ '\n' }different view.</Text><Text style={styles.briefingIntro}>A small collection to start your day.</Text>
      <Image source={require('./assets/waterfront-garden.webp')} style={styles.briefingPhoto} contentFit="cover" /><Text style={styles.storyCategory}>PLACES · 4 MIN READ</Text><Text style={styles.storyHeadline}>The city is meeting its river again.</Text><Text style={styles.storyBody}>A new waterfront garden turns a once-forgotten edge into a place to walk, gather and slow down.</Text>
      <View style={styles.perspectives}><Text style={styles.perspectiveLabel}>THREE WAYS TO LOOK AT IT</Text>{['Public space: who gets to use the waterfront?', 'Design: how do paths make room for water?', 'Everyday life: what makes a walk feel welcoming?'].map(line => <Text key={line} style={styles.perspective}>{line}</Text>)}</View>
      <TourButton {...controls} reduced={reduced} background={saved ? '#E4EDDF' : '#222126'} color={saved ? '#37532D' : '#FBF9F5'} onPress={() => setSaved(!saved)}>{saved ? 'Saved to your reading list' : 'Save this story'}</TourButton><TourButton {...controls} subtle reduced={reduced} color="#77746D" onPress={() => { setBriefing(false); manual.set(page); manualMode.set(true); restart(ANCHORS[page]); }}>Back to the tour</TourButton>
    </ScrollView></ProgressiveBlurViewport> : <MaskedView androidRenderingMode="software" style={{ flex: 1 }} maskElement={<Animated.View style={[{ position: 'absolute', borderRadius: 28, backgroundColor: '#000000' }, expansionMask]} />}>
      <WarmBackdrop frame={welcomeFrame} glow={welcomeGlow} page={position} width={width} height={height} />
      <View style={{ position: 'absolute', top: insets.top, left: 0, right: 0 }} pointerEvents="none"><SpectrumParticles time={time} page={position} width={width} height={330} /></View>
      <View style={[styles.content, { paddingTop: insets.top + 10, paddingBottom: insets.bottom + 4 }]}>
        <ProgressiveBlurViewport style={{ flex: 1 }} tint="light" edgeSize={30}><ScrollView {...readingHandlers} key={layoutKey} style={{ flex: 1, minHeight: 0 }} contentContainerStyle={{ flexGrow: 1, justifyContent: 'flex-start', paddingTop: Math.max(0, (height - insets.top - insets.bottom - 680) * .18), paddingBottom: largeText ? 16 : 0 }} showsVerticalScrollIndicator={false}>
          <GestureDetector gesture={pan}><View style={{ height: sceneHeight, overflow: 'hidden' }} accessible accessibilityRole="adjustable" accessibilityLabel={`Page ${page + 1} of 5. ${PAGES[page].title} ${PAGES[page].body} ${PAGES[page].art}`} accessibilityActions={[{ name: 'increment', label: 'Next story' }, { name: 'decrement', label: 'Previous story' }]} onAccessibilityAction={event => go(page + (event.nativeEvent.actionName === 'increment' ? 1 : -1))}>
            {PAGES.map((_, index) => <NewsPage key={index} index={index} width={width} time={time} page={position} reduced={reduced} autoPlay={timelineDriven} manualMode={renderManual} scale={scale} onMeasure={measureScene} />)}
          </View></GestureDetector>
        </ScrollView></ProgressiveBlurViewport>
        <View style={styles.footer} pointerEvents={introReady ? 'auto' : 'none'} accessibilityElementsHidden={!introReady} importantForAccessibility={introReady ? 'auto' : 'no-hide-descendants'}>
          {pageControls}
          <PressScale {...controls} reduced={reduced} accessibilityRole="button" accessibilityLabel="Continue with Apple" onPress={openBriefing} style={styles.appleButton}><Ionicons name="logo-apple" size={21} color="#FFFFFF" /><Text maxFontSizeMultiplier={1.6} style={styles.appleLabel}>Continue with Apple</Text></PressScale>
          <Text style={styles.finePrint}>By signing up for and using Spectrum, you agree to our{'\n'}<Text style={styles.link}>Terms of Service</Text> & <Text style={styles.link}>Privacy Policy</Text></Text>
        </View>
      </View>
    </MaskedView>}
  </View>;
}

const styles = StyleSheet.create({ root: { flex: 1, backgroundColor: '#FCFBF8' }, content: { flex: 1 }, warmBackdrop: { position: 'absolute', top: 0, right: 0, bottom: 0, left: 0, overflow: 'hidden', backgroundColor: '#FCFBF8' }, page: { position: 'absolute', top: 0, left: 0, alignItems: 'center', paddingHorizontal: 24 }, copy: { marginTop: 35, alignItems: 'center' }, heading: { color: '#111111', fontSize: 16, lineHeight: 21, fontWeight: '600', textAlign: 'center' }, body: { color: '#6B6B6B', fontSize: 15, lineHeight: 19.5, marginTop: 12, textAlign: 'center' }, tagline: { color: '#7A7A7A', fontSize: 18, lineHeight: 23, textAlign: 'center' }, copyWelcome: { marginTop: 10 }, appleButton: { minHeight: 48, paddingVertical: 8, width: 300, maxWidth: '100%', alignSelf: 'center', borderRadius: 7, backgroundColor: '#000000', flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6 }, appleLabel: { color: '#FFFFFF', fontSize: 19, fontWeight: '500', letterSpacing: -0.2 }, link: { textDecorationLine: 'underline' }, footer: { paddingHorizontal: 24 }, pager: { flexDirection: 'row', justifyContent: 'center', marginBottom: 4 }, dot: { height: 6, width: 6, borderRadius: 3 }, navigation: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: 4 }, finePrint: { color: '#8A8A8A', fontSize: 10, lineHeight: 13, textAlign: 'center', marginTop: 20, marginBottom: 14 },
  briefing: { paddingHorizontal: 27, flexGrow: 1 }, briefingBrand: { flexDirection: 'row', alignItems: 'center', gap: 10 }, briefingWordmark: { color: '#25242A', fontSize: 22, fontWeight: '800', letterSpacing: -.7 }, date: { color: '#A19B91', fontSize: 10, letterSpacing: 1.7, marginTop: 33 }, briefingTitle: { color: '#2B292D', fontSize: 39, lineHeight: 44, fontWeight: '700', letterSpacing: -1.3, marginTop: 12 }, briefingIntro: { color: '#8D877D', fontSize: 15, lineHeight: 23, marginTop: 14 }, briefingPhoto: { width: '100%', height: 202, borderRadius: 16, marginTop: 27 }, storyCategory: { color: '#8B9478', fontSize: 9, fontWeight: '700', letterSpacing: 1.2, marginTop: 22 }, storyHeadline: { color: '#35322F', fontSize: 25, lineHeight: 31, fontWeight: '600', letterSpacing: -.6, marginTop: 10 }, storyBody: { color: '#898278', fontSize: 15, lineHeight: 24, marginTop: 12 }, perspectives: { marginVertical: 25, borderTopWidth: 1, borderTopColor: '#E7E1D7', paddingTop: 19, gap: 13 }, perspectiveLabel: { color: '#A29B90', fontSize: 9, letterSpacing: 1.3 }, perspective: { color: '#716A61', fontSize: 13, lineHeight: 21 },
});
