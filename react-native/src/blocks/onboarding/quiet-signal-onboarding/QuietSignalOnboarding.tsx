import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector, ScrollView } from 'react-native-gesture-handler';
import Animated, { cancelAnimation, Easing, FadeInDown, interpolate, type SharedValue, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useSharedValue, withTiming, withSpring } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { ProgressiveBlurViewport } from '@/shared/SkiaProgressiveBlur';
import { SwapText } from '@/shared/SwapText';

import { clamp, ease, enter, rubber, segment, usePlayback } from './motion';
import { QuietSignalDiagram } from './QuietSignalDiagrams';
import { TourButton } from './TourButton';

export const LOOP = 34;
const ANCHORS = [3, 11, 18, 25, 31.9];
const STARTS = [0, 9, 15.25, 22, 28.25];
const ENDS = [8.15, 14.4, 21.4, 27.5, 32.5];
const FADE_ENDS = [8.5, 14.8, 21.8, 27.9, 33.25];
const PAGES = [
  { title: 'We live in an over-\nstimulating world', body: 'It makes us tired, stressed, and\nunable to rest', art: 'A profile, document and winding lines show a busy mind.' },
  { title: 'A new world needs\nnew solutions', body: 'Technology that can help us\nto adapt', art: 'A luminous profile with gentle sound waves and audio bars.' },
  { title: 'That’s why we made Quiet Signal', body: 'An antidote to a distracting\nworld. Built on the science\nof sound', art: 'An outlined phone carries waves between two sets of sound bars.' },
  { title: 'Improve your state of mind\nwith the power of sound', body: 'Focus, relax, and unwind.\nBe more present. See the\nmagic in everyday life', art: 'Concentric rings and diagonal waves surround a profile.' },
  { title: 'Welcome to the sound\nwellness revolution', body: 'Welcome to Quiet Signal', art: 'A complete circle surrounds an original continuous flowing emblem.' },
];

function pageAt(time: number) {
  'worklet';
  if (time >= 33.25) return 0;
  if (time >= 28) return 4;
  if (time >= 21.75) return 3;
  if (time >= 14.75) return 2;
  if (time >= 8.5) return 1;
  return 0;
}

function Scene({ index, time, position, manualMode, reduced, size, largeText, onMeasure }: { index: number; time: SharedValue<number>; position: SharedValue<number>; manualMode: SharedValue<boolean>; reduced: boolean; size: number; largeText: boolean; onMeasure: (height: number) => void }) {
  const sceneStyle = useAnimatedStyle(() => {
    if (manualMode.get() || reduced) return { opacity: clamp(1 - Math.abs(position.get() - index) * 2) };
    return { opacity: enter(segment(time.get(), STARTS[index], STARTS[index] + 1.3)) * interpolate(segment(time.get(), ENDS[index], FADE_ENDS[index]), [0, .0486, .2857, .523, .762, 1], [1, .86, .4, .17, .06, 0], 'clamp') };
  });
  // Words surface out of the dark: a soft focus pull and a 6 pt rise under the fade.
  // Only the diagram on screen rebuilds its paths each frame.
  const live = useDerivedValue(() => manualMode.get() || reduced ? Math.abs(position.get() - index) < 0.75 : time.get() >= STARTS[index] - 0.1 && time.get() <= FADE_ENDS[index]);
  const copyStyle = useAnimatedStyle(() => {
    const shown = manualMode.get() || reduced ? 1 : index === 4 ? ease(segment(time.get(), 29.15, 30.1)) : enter(segment(time.get(), STARTS[index] + .45, STARTS[index] + 1.8));
    return { opacity: shown, filter: `blur(${((1 - shown) * 6).toFixed(2)}px)`, transform: [{ translateY: (1 - shown) * 6 }] };
  });
  return <Animated.View onLayout={event => onMeasure(event.nativeEvent.layout.height)} style={[styles.scene, sceneStyle]} pointerEvents="none" importantForAccessibility="no-hide-descendants" accessibilityElementsHidden>
    <View style={{ height: largeText ? 0 : 18 }} /><QuietSignalDiagram stage={index} time={time} live={live} reduced={reduced} size={size} />
    <Animated.View style={[styles.copy, { marginTop: largeText ? 24 : 34 }, copyStyle]}><Text maxFontSizeMultiplier={1.6} style={styles.heading}>{PAGES[index].title}</Text><Text maxFontSizeMultiplier={1.9} style={styles.body}>{PAGES[index].body}</Text></Animated.View>
  </Animated.View>;
}

// The session screen arrives as a short cascade, 70 ms a group, each settling 12 pt.
function rise(order: number, reduced: boolean) {
  return reduced ? undefined : FadeInDown.duration(420).delay(100 + order * 70).easing(Easing.bezier(.23, 1, .32, 1)).withInitialValues({ opacity: 0, transform: [{ translateY: 12 }] });
}

export type QuietSignalOnboardingProps = { autoPlay?: boolean; /** Freeze a normalized timeline phase in seconds for deterministic inspection. */ previewTime?: number; onBeginSession?: () => void; onPageChange?: (page: number) => void };

export function QuietSignalOnboarding({ autoPlay = false, previewTime, onBeginSession, onPageChange }: QuietSignalOnboardingProps) {
  const { width, height, fontScale } = useWindowDimensions();
  const largeText = fontScale > 1.35;
  const layoutKey = `${width}:${fontScale}`;
  const [sceneMeasurement, setSceneMeasurement] = useState({ key: '', height: 0 });
  const insets = useSafeAreaInsets();
  const { time, enabled, playing, reduced, pause, settle, stop, restart, remember, readingHandlers, beginGesture, endGesture, previewing, previewPhase } = usePlayback(LOOP, autoPlay, ANCHORS[0], previewTime);
  const timelineDriven = autoPlay || previewing;
  const [page, setPage] = useState(() => previewPhase === undefined ? 0 : Math.round(pageAt(previewPhase)));
  const controls = { onPressIn: pause, onPressOut: () => settle(ANCHORS[page]) };
  const [session, setSession] = useState(false);
  const [sessionMode, setSessionMode] = useState('A quiet break');
  const sessionLive = useSharedValue(true);
  const manualMode = useSharedValue(!timelineDriven);
  const renderManual = useDerivedValue(() => !previewing && manualMode.get());
  const manual = useSharedValue(0);
  const startPage = useSharedValue(0);
  const panning = useSharedValue(false);
  const scenePresence = useSharedValue(1);
  const scenePresenceStyle = useAnimatedStyle(() => ({ opacity: previewing ? 1 : scenePresence.get() }));
  const position = useDerivedValue(() => renderManual.get() ? manual.get() : pageAt(time.get()));
  const size = Math.min(width - 80, Math.max(220, height - insets.top - insets.bottom - 430), largeText ? 230 : 300);
  const sceneHeight = sceneMeasurement.key === layoutKey ? sceneMeasurement.height : size + 175 * Math.max(fontScale, 1);
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
    cancelAnimation(scenePresence);
    if (reduced) { manual.set(target); scenePresence.set(1); }
    else scenePresence.set(withTiming(0, { duration: 100, easing: enter }, finished => { if (!finished) return; manual.set(target); scenePresence.set(withTiming(1, { duration: 180, easing: enter })); }));
    landed(target);
  }, [landed, manual, manualMode, pause, position, previewing, reduced, scenePresence]);
  const pan = useMemo(() => Gesture.Pan().enabled(!previewing).activeOffsetX([-10, 10]).failOffsetY([-18, 18]).maxPointers(1)
    .onStart(() => { panning.set(true); cancelAnimation(scenePresence); scenePresence.set(1); enabled.set(false); cancelAnimation(manual); manual.set(position.get()); startPage.set(manual.get()); manualMode.set(true); scheduleOnRN(beginGesture); })
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
    }), [beginGesture, enabled, endGesture, landed, manual, manualMode, panning, position, previewing, reduced, scenePresence, settle, startPage, width]);

  function continueTour() {
    if (previewing) return;
    if (page < 4) { go(page + 1); return; }
    stop(); setSession(true); onBeginSession?.();
    void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
  }


  return <View style={styles.root}><StatusBar style="light" />
    {session && !previewing ? <ProgressiveBlurViewport style={{ flex: 1 }} tint="dark" edgeSize={30}><ScrollView contentContainerStyle={[styles.session, { paddingTop: insets.top + 30, paddingBottom: insets.bottom + 16 }]}>
      <Animated.View entering={rise(0, reduced)} style={{ alignItems: 'center' }}><Text allowFontScaling={false} style={styles.eyebrow}>QUIET SIGNAL</Text><QuietSignalDiagram stage={4} time={time} live={sessionLive} reduced size={210} /></Animated.View>
      <Animated.View entering={rise(1, reduced)} style={{ alignItems: 'center' }}><Text style={styles.sessionTitle}>Your moment is ready.</Text><Text maxFontSizeMultiplier={1.9} style={styles.body}>Choose a direction for this 20-minute session.</Text></Animated.View>
      <Animated.View entering={rise(2, reduced)} style={styles.modes}>{['A little focus', 'A quiet break', 'An easy evening'].map(mode => <TourButton {...controls} key={mode} background={sessionMode === mode ? '#D8D8E0' : '#212126'} color={sessionMode === mode ? '#141419' : '#B7B7C2'} reduced={reduced} onPress={() => { setSessionMode(mode); void Haptics.selectionAsync(); }} label={`${mode}${sessionMode === mode ? ', selected' : ''}`}>{mode}</TourButton>)}</Animated.View>
      <Animated.View entering={rise(3, reduced)} style={{ alignItems: 'center' }}><Text style={styles.sessionTime}>20:00</Text><SwapText style={styles.previewCaption} reduced={reduced} text={`${sessionMode} · silent session preview`} /><Text style={styles.previewNote}>This preview is yours to explore.{ '\n' }No audio is playing.</Text></Animated.View>
      <TourButton {...controls} background="#25252D" reduced={reduced} style={{ width: '100%', marginTop: 26 }} onPress={() => { setSession(false); manual.set(4); manualMode.set(true); restart(ANCHORS[4]); }}>Back to the introduction</TourButton>
    </ScrollView></ProgressiveBlurViewport> : <View style={[styles.content, { paddingTop: insets.top + 16, paddingBottom: insets.bottom + 10 }]}>
      <ProgressiveBlurViewport style={{ flex: 1 }} tint="dark" edgeSize={30}><ScrollView {...readingHandlers} key={layoutKey} style={{ flex: 1, minHeight: 0 }} contentContainerStyle={{ flexGrow: 1, justifyContent: largeText ? 'flex-start' : 'center', paddingVertical: largeText ? 16 : 0 }} showsVerticalScrollIndicator={false}>
        <GestureDetector gesture={pan}><Animated.View style={[{ height: sceneHeight }, scenePresenceStyle]} accessible accessibilityRole="adjustable" accessibilityLabel={`Page ${page + 1} of 5. ${PAGES[page].title} ${PAGES[page].body} ${PAGES[page].art}`} accessibilityActions={[{ name: 'increment', label: 'Next stage' }, { name: 'decrement', label: 'Previous stage' }]} onAccessibilityAction={event => go(page + (event.nativeEvent.actionName === 'increment' ? 1 : -1))}>
          {PAGES.map((_, index) => <Scene key={index} index={index} time={time} position={position} manualMode={renderManual} reduced={reduced} size={size} largeText={largeText} onMeasure={measureScene} />)}
        </Animated.View></GestureDetector>
      </ScrollView></ProgressiveBlurViewport>
      <View style={styles.footer}>
        <TourButton {...controls} reduced={reduced} background="#24242B" label={page === 4 ? 'Continue to a quiet moment' : 'Continue'} onPress={continueTour}><SwapText maxFontSizeMultiplier={1.6} style={styles.cta} reduced={reduced} text="Continue" /></TourButton>
      </View>
    </View>}
  </View>;
}

const styles = StyleSheet.create({ root: { flex: 1, backgroundColor: '#070709' }, content: { flex: 1 }, brand: { color: '#71717C', letterSpacing: 2.8, fontSize: 10, textAlign: 'center' }, scene: { position: 'absolute', top: 0, left: 0, right: 0, alignItems: 'center', paddingHorizontal: 28 }, copy: { alignItems: 'center', marginTop: 48 }, heading: { color: '#FFFFFF', fontSize: 20, lineHeight: 27, fontWeight: '600', textAlign: 'center', letterSpacing: -.2 }, body: { color: '#7C7C80', fontSize: 20.5, lineHeight: 27, textAlign: 'center', marginTop: 10 }, footer: { paddingHorizontal: 50 }, pager: { flexDirection: 'row', justifyContent: 'center', marginBottom: 4 }, dot: { width: 4, height: 4, borderRadius: 2 }, cta: { color: '#F2F2F7', fontSize: 15, lineHeight: 20, fontWeight: '500', textAlign: 'center' }, session: { flexGrow: 1, alignItems: 'center', paddingHorizontal: 28 }, eyebrow: { color: '#8A8A96', fontSize: 11, letterSpacing: 3 }, sessionTitle: { color: '#E8E8F0', fontSize: 28, lineHeight: 35, textAlign: 'center', letterSpacing: -.7 }, modes: { alignSelf: 'stretch', gap: 9, marginTop: 24 }, sessionTime: { color: '#D8D8E2', fontSize: 43, fontWeight: '300', fontVariant: ['tabular-nums'], marginTop: 28 }, previewCaption: { color: '#8E8E9C', fontSize: 12, marginTop: 9 }, previewNote: { color: '#777783', fontSize: 12, lineHeight: 19, textAlign: 'center', marginTop: 18 } });
