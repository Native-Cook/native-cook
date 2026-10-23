import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  AccessibilityInfo,
  AppState,
  Image,
  type ImageSourcePropType,
  StyleSheet,
  Text,
  useWindowDimensions,
  View,
} from 'react-native';
import {
  Gesture,
  GestureDetector,
  GestureHandlerRootView,
  ScrollView,
  type GestureType,
} from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedProps,
  useAnimatedStyle,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path, Rect } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { GrassTimerHero } from './GrassTimerHero';
import {
  CLOCK_ANIMATION,
  CLOCK_SECONDS,
  CLOCK_STOPPED,
  LOOP,
  PHASE_ACTIVE,
  PHASE_MORPHING,
  PHASE_RETURNING,
  activate,
  advanceLogicalClock,
  beginCarousel,
  beginPress,
  cancelCarousel,
  cancelPress,
  createMotionState,
  demoFrame,
  endCarousel,
  endPress,
  resumeAfterBackground,
  sampleMotion,
  setAutoPlay,
  setQuiet,
  stop,
  suspendMotion,
  updateCarousel,
  type ClockCadence,
  type GrassFrame,
  type GrassPhase,
  type MotionState,
} from './motion';

export { LOOP };

type Props = {
  autoPlay?: boolean;
};

type SemanticState = {
  phase: GrassPhase;
  selectedCard: number;
  remainingSeconds: number;
  cadence: ClockCadence;
  chipVisible: boolean;
  timerControlVisible: boolean;
};

const ORB_OCEAN = require('./assets/orb-ocean.jpg') as number;
const ORB_WAVES = require('./assets/orb-waves.jpg') as number;
const ORB_AIRPLANE = require('./assets/orb-airplane.jpg') as number;
const STORY_MOUNTAINS = require('./assets/story-mountains.jpg') as number;
const STORY_CLOUDS = require('./assets/story-clouds.jpg') as number;

const BACKGROUND = '#000000';
const MUTED = 'rgba(255,255,255,0.60)';
const AnimatedRect = Animated.createAnimatedComponent(Rect);
const CHIP_PERIMETER = 80 + 2 * Math.PI * 19.25;

function impactHaptic() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function selectionHaptic() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

function formatTime(seconds: number): string {
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const rest = seconds % 60;
  return `${hours}:${minutes.toString().padStart(2, '0')}:${rest.toString().padStart(2, '0')}`;
}

function PlayIcon({ size, pause = false }: { size: number; pause?: boolean }) {
  if (pause) {
    return (
      <Svg width={size} height={size} viewBox="0 0 24 24" accessibilityElementsHidden>
        <Rect x={5} y={4} width={5} height={16} rx={1} fill="#FFFFFF" />
        <Rect x={14} y={4} width={5} height={16} rx={1} fill="#FFFFFF" />
      </Svg>
    );
  }
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24" accessibilityElementsHidden>
      <Path d="M7 4.8 19 12 7 19.2Z" fill="#FFFFFF" />
    </Svg>
  );
}

function Chevron({ size }: { size: number }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24" accessibilityElementsHidden>
      <Path d="m8 4 8 8-8 8" fill="none" stroke={MUTED} strokeWidth={2.2} strokeLinecap="round" strokeLinejoin="round" />
    </Svg>
  );
}

function ChipProgress({ index, frame, scale }: { index: number; frame: SharedValue<GrassFrame>; scale: number }) {
  const animatedProps = useAnimatedProps(() => {
    const current = frame.get();
    const progress = current.selectedCard === index ? current.pressProgress : 0;
    return { strokeDashoffset: (1 - progress) * CHIP_PERIMETER };
  });
  return (
    <Svg pointerEvents="none" style={StyleSheet.absoluteFill} width={80 * scale} height={40 * scale} viewBox="0 0 80 40">
      <AnimatedRect
        animatedProps={animatedProps}
        x={0.75}
        y={0.75}
        width={78.5}
        height={38.5}
        rx={19.25}
        fill="none"
        stroke="rgba(223,255,138,0.9)"
        strokeWidth={1.5}
        strokeLinecap="round"
        strokeDasharray={`${CHIP_PERIMETER} ${CHIP_PERIMETER}`}
      />
    </Svg>
  );
}

function CardCopy({
  index,
  frame,
  scale,
  safeTop,
  selectedForAccessibility,
}: {
  index: number;
  frame: SharedValue<GrassFrame>;
  scale: number;
  safeTop: number;
  selectedForAccessibility: boolean;
}) {
  const title = index === 0 ? 'Touch Grass' : 'Quiet Day';
  const duration = index === 0 ? '8 hrs' : '1 day';
  const style = useAnimatedStyle(() => {
    const current = frame.get();
    const primary = current.selectedCard === index;
    const x = primary ? 14 + current.carouselOffset : current.neighborCardX;
    return {
      opacity: primary ? current.copyOpacity : current.secondCardOpacity,
      transform: [{ translateX: x * scale }, { translateY: safeTop - 62 * scale }],
    };
  });
  const chipStyle = useAnimatedStyle(() => {
    const current = frame.get();
    return { transform: [{ scale: current.selectedCard === index ? current.chipScale : 1 }] };
  });

  return (
    <Animated.View
      pointerEvents="none"
      style={[styles.cardCopy, { width: 232 * scale, height: 327 * scale }, style]}
      accessible={selectedForAccessibility}
      accessibilityElementsHidden={!selectedForAccessibility}
      accessibilityLabel={`Timer. ${title}. No Distracting Apps. Start ${duration} focus timer.`}
      importantForAccessibility={selectedForAccessibility ? 'yes' : 'no-hide-descendants'}
    >
      <Text allowFontScaling={false} style={[styles.timerCaption, { left: 143 * scale, top: 76 * scale, width: 80 * scale, fontSize: 18 * scale, lineHeight: 22 * scale }]}>Timer</Text>
      <View style={[styles.cardTitleBlock, { left: 25 * scale, top: 201 * scale, width: 200 * scale }]}>
        <Text allowFontScaling={false} style={[styles.cardTitle, { fontSize: 22 * scale, lineHeight: 27 * scale }]}>{title}</Text>
        <Text allowFontScaling={false} numberOfLines={1} style={[styles.cardSubtitle, { fontSize: 17 * scale, lineHeight: 21 * scale }]}>{'No Distracting Apps'}</Text>
      </View>
      <Animated.View style={[styles.chipVisual, { left: 25 * scale, top: 262 * scale, width: 80 * scale, height: 40 * scale, borderRadius: 20 * scale }, chipStyle]}>
        <PlayIcon size={14 * scale} />
        <Text allowFontScaling={false} style={[styles.chipText, { fontSize: 16 * scale, lineHeight: 20 * scale }]}>{duration}</Text>
        <ChipProgress index={index} frame={frame} scale={scale} />
      </Animated.View>
    </Animated.View>
  );
}

function TimerReadout({ frame, scale, safeTop, semantic }: { frame: SharedValue<GrassFrame>; scale: number; safeTop: number; semantic: SemanticState }) {
  const style = useAnimatedStyle(() => {
    const current = frame.get();
    const aperture = current.aperture;
    return {
      opacity: current.timerOpacity,
      transform: [
        { translateX: (aperture.x + current.carouselOffset + aperture.width / 2 - 90) * scale },
        { translateY: safeTop + (aperture.y - 62 + aperture.height / 2 - 34) * scale },
      ],
    };
  });
  return (
    <Animated.View
      pointerEvents="none"
      style={[styles.timerReadout, { width: 180 * scale, height: 68 * scale }, style]}
      accessible={semantic.phase === PHASE_ACTIVE}
      accessibilityElementsHidden={semantic.phase !== PHASE_ACTIVE}
      importantForAccessibility={semantic.phase === PHASE_ACTIVE ? 'yes' : 'no-hide-descendants'}
      accessibilityLabel={`Focus timer, ${formatTime(semantic.remainingSeconds)} remaining`}
    >
      <Text allowFontScaling={false} style={[styles.timerValue, { fontSize: 31 * scale, lineHeight: 38 * scale }]}>{formatTime(semantic.remainingSeconds)}</Text>
      <Text allowFontScaling={false} style={[styles.timerName, { marginTop: 5 * scale, fontSize: 10 * scale, lineHeight: 13 * scale, letterSpacing: 1.4 * scale }]}>
        {semantic.selectedCard === 0 ? 'TOUCH GRASS' : 'QUIET DAY'}
      </Text>
    </Animated.View>
  );
}

function ChipControl({
  frame,
  gesture,
  scale,
  safeTop,
  semantic,
  onAccessibilityActivate,
}: {
  frame: SharedValue<GrassFrame>;
  gesture: GestureType;
  scale: number;
  safeTop: number;
  semantic: SemanticState;
  onAccessibilityActivate: () => void;
}) {
  const style = useAnimatedStyle(() => {
    const current = frame.get();
    return {
      opacity: current.copyOpacity > 0.1 ? 1 : 0,
      transform: [
        { translateX: (14 + current.carouselOffset + 21) * scale },
        { translateY: safeTop + (262 - 62 - 2) * scale },
      ],
    };
  });
  const duration = semantic.selectedCard === 0 ? '8 hrs' : '1 day';
  return (
    <GestureDetector gesture={gesture} touchAction="pan-y">
      <Animated.View
        style={[styles.chipControl, { width: 88 * scale, height: 44 * scale }, style]}
        accessible
        accessibilityRole="button"
        accessibilityLabel={`Start ${duration} focus timer`}
        accessibilityHint="Double tap to start, or touch and hold for 650 milliseconds"
        onAccessibilityTap={onAccessibilityActivate}
      />
    </GestureDetector>
  );
}

function StopControl({
  frame,
  gesture,
  scale,
  safeTop,
  phase,
  onAccessibilityActivate,
}: {
  frame: SharedValue<GrassFrame>;
  gesture: GestureType;
  scale: number;
  safeTop: number;
  phase: GrassPhase;
  onAccessibilityActivate: () => void;
}) {
  const style = useAnimatedStyle(() => {
    const current = frame.get();
    const aperture = current.aperture;
    const shown = current.timerOpacity > 0.1 &&
      (current.phase === PHASE_ACTIVE || current.phase === PHASE_MORPHING || current.phase === PHASE_RETURNING);
    return {
      opacity: shown ? current.timerOpacity : 0,
      transform: [
        { translateX: (aperture.x + current.carouselOffset + aperture.width / 2 - 38) * scale },
        { translateY: safeTop + (aperture.y - 62 + aperture.height / 2 + 32) * scale },
      ],
    };
  });
  const returning = phase === PHASE_RETURNING;
  return (
    <GestureDetector gesture={gesture}>
      <Animated.View
        style={[styles.stopControl, { width: 76 * scale, height: 44 * scale, borderRadius: 22 * scale }, style]}
        accessible
        accessibilityRole="button"
        accessibilityLabel={returning ? 'Resume focus timer' : 'Stop focus timer'}
        onAccessibilityTap={onAccessibilityActivate}
      >
        <Text allowFontScaling={false} style={[styles.stopText, { fontSize: 14 * scale }]}>{returning ? 'Resume' : 'Stop'}</Text>
      </Animated.View>
    </GestureDetector>
  );
}

function MixItem({ source, label, scale, selected, onToggle }: { source: ImageSourcePropType; label: string; scale: number; selected: boolean; onToggle: (label: string) => void }) {
  const tap = useMemo(() => Gesture.Tap().onEnd((_event, success) => {
    if (success) scheduleOnRN(onToggle, label);
  }), [label, onToggle]);
  return (
    <GestureDetector gesture={tap}>
      <View style={{ width: 105 * scale }} accessible accessibilityRole="button" accessibilityLabel={`${label}, ${selected ? 'playing' : 'paused'}`}>
        <View style={[styles.orb, { width: 105 * scale, height: 105 * scale, borderRadius: 52.5 * scale, borderWidth: 1.5 * scale }]}>
          <Image source={source} resizeMode="cover" style={{ width: 98 * scale, height: 98 * scale, borderRadius: 49 * scale }} />
          <View style={styles.orbIcon}><PlayIcon size={28 * scale} pause={selected} /></View>
        </View>
        <Text allowFontScaling={false} style={[styles.orbLabel, { marginTop: 10 * scale, fontSize: 14 * scale, lineHeight: 17 * scale }]}>{label}</Text>
      </View>
    </GestureDetector>
  );
}

function StaticEditorial({ scale, safeTop, selectedMix, onToggleMix }: { scale: number; safeTop: number; selectedMix: string | null; onToggleMix: (label: string) => void }) {
  const top = (reference: number) => safeTop + (reference - 62) * scale;
  return (
    <View pointerEvents="box-none" style={StyleSheet.absoluteFill}>
      <View style={[styles.mixesCopy, { left: 30 * scale, top: top(371), width: 342 * scale }]}>
        <View style={styles.headingRow}>
          <Text allowFontScaling={false} style={[styles.sectionTitle, { fontSize: 18 * scale, lineHeight: 22 * scale }]}>Mixes</Text>
          <Chevron size={13 * scale} />
        </View>
        <Text allowFontScaling={false} style={[styles.sectionBody, { marginTop: 1 * scale, fontSize: 16.7 * scale, lineHeight: 22.4 * scale }]}>
          {'Personalized soundscapes to focus, relax\nand sleep.'}
        </Text>
      </View>
      <View style={[styles.mixes, { left: 14 * scale, top: top(453), gap: 25 * scale }]}>
        <MixItem source={ORB_OCEAN} label="Ocean" scale={scale} selected={selectedMix === 'Ocean'} onToggle={onToggleMix} />
        <MixItem source={ORB_WAVES} label="Waves" scale={scale} selected={selectedMix === 'Waves'} onToggle={onToggleMix} />
        <MixItem source={ORB_AIRPLANE} label="Airplane" scale={scale} selected={selectedMix === 'Airplane'} onToggle={onToggleMix} />
      </View>
      <View style={[styles.storiesCopy, { left: 30 * scale, top: top(631), width: 342 * scale }]}>
        <Text allowFontScaling={false} style={[styles.sectionTitle, { fontSize: 18 * scale, lineHeight: 22 * scale }]}>Sleep Stories</Text>
        <Text allowFontScaling={false} numberOfLines={1} style={[styles.sectionBody, { fontSize: 16.7 * scale, lineHeight: 22 * scale }]}>Relax and drift off to these original stories</Text>
      </View>
      <Image source={STORY_MOUNTAINS} resizeMode="cover" style={[styles.story, { left: 14 * scale, top: top(689), width: 272 * scale, height: 182 * scale, borderRadius: 36 * scale }]} />
      <Image source={STORY_CLOUDS} resizeMode="cover" style={[styles.story, { left: 303 * scale, top: top(689), width: 95 * scale, height: 182 * scale, borderTopLeftRadius: 36 * scale, borderBottomLeftRadius: 36 * scale }]} />
    </View>
  );
}

function TabIcon({ kind, scale }: { kind: 'home' | 'apps' | 'timer'; scale: number }) {
  const size = 21 * scale;
  if (kind === 'timer') return <PlayIcon size={size} />;
  if (kind === 'home') {
    return (
      <Svg width={size} height={size} viewBox="0 0 24 24" accessibilityElementsHidden>
        <Path d="M3 11.2 12 3l9 8.2v9H15v-6H9v6H3Z" fill="none" stroke="#FFFFFF" strokeWidth={1.8} strokeLinejoin="round" />
      </Svg>
    );
  }
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24" accessibilityElementsHidden>
      {[3, 13].flatMap((y) => [3, 13].map((x) => <Rect key={`${x}-${y}`} x={x} y={y} width={8} height={8} rx={1.5} fill="#FFFFFF" />))}
    </Svg>
  );
}

function BottomTabs({ scale }: { scale: number }) {
  const item = (kind: 'home' | 'apps' | 'timer', label: string, selected: boolean) => (
    <View key={kind} style={[styles.tab, { width: 92 * scale, height: 53 * scale, borderRadius: 26.5 * scale }, selected && styles.tabSelected]} accessible accessibilityLabel={label}>
      <TabIcon kind={kind} scale={scale} />
      <Text allowFontScaling={false} style={[styles.tabLabel, { marginTop: 4 * scale, fontSize: 12 * scale, lineHeight: 15 * scale, opacity: selected ? 1 : 0.72 }]}>{label}</Text>
    </View>
  );
  return (
    <View style={[styles.tabs, { width: 280 * scale, height: 62 * scale, borderRadius: 31 * scale, padding: 4 * scale }]}>
      {item('home', 'Home', false)}
      {item('apps', 'My Apps', false)}
      {item('timer', 'Timer', true)}
    </View>
  );
}

export function GrassTimerCarousel({ autoPlay = false }: Props) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const initialReduced = useReducedMotion();
  const [reducedMotion, setReducedMotion] = useState(initialReduced);
  const [screenReader, setScreenReader] = useState(false);
  const quiet = reducedMotion || screenReader;
  const scale = width / 402;
  const contentHeight = Math.max(height + 160, insets.top + (920 - 62) * scale + 72 + insets.bottom);
  const heroHeight = insets.top + (392 - 62) * scale;
  const initialFrame = demoFrame(0);
  const initialCadence: ClockCadence = autoPlay && !quiet ? CLOCK_ANIMATION : CLOCK_STOPPED;
  const [semantic, setSemantic] = useState<SemanticState>({
    phase: initialFrame.phase,
    selectedCard: 0,
    remainingSeconds: initialFrame.remainingSeconds,
    cadence: initialCadence,
    chipVisible: true,
    timerControlVisible: false,
  });
  const [foregroundState, setForegroundState] = useState(AppState.currentState === 'active');
  const [selectedMix, setSelectedMix] = useState<string | null>(null);
  const backgroundWall = useRef<number | null>(null);

  const clock = useSharedValue(0);
  const lastTimestamp = useSharedValue(-1);
  const foreground = useSharedValue(AppState.currentState === 'active');
  const quietShared = useSharedValue(quiet);
  const motion = useSharedValue<MotionState>(createMotionState(0, autoPlay, quiet));
  const frame = useSharedValue<GrassFrame>(initialFrame);
  const publishedPhase = useSharedValue<GrassPhase>(initialFrame.phase);
  const publishedSelected = useSharedValue(0);
  const publishedSeconds = useSharedValue(initialFrame.remainingSeconds);
  const publishedCadence = useSharedValue<ClockCadence>(initialCadence);
  const publishedChipVisible = useSharedValue(true);
  const publishedTimerControlVisible = useSharedValue(false);
  const seenActivation = useSharedValue(0);
  const seenSelection = useSharedValue(0);
  const chipCanceled = useSharedValue(false);
  const chipEnded = useSharedValue(false);
  const carouselAccepted = useSharedValue(false);
  const carouselEnded = useSharedValue(false);

  const publishSemantic = useCallback((phase: GrassPhase, selectedCard: number, remainingSeconds: number, cadence: ClockCadence, chipVisible: boolean, timerControlVisible: boolean) => {
    setSemantic((current) => current.phase === phase && current.selectedCard === selectedCard && current.remainingSeconds === remainingSeconds && current.cadence === cadence && current.chipVisible === chipVisible && current.timerControlVisible === timerControlVisible
      ? current
      : { phase, selectedCard, remainingSeconds, cadence, chipVisible, timerControlVisible });
  }, []);

  const publishUI = useCallback((nextFrame: GrassFrame, cadence: ClockCadence) => {
    'worklet';
    if (nextFrame.activationSequence > seenActivation.get()) {
      seenActivation.set(nextFrame.activationSequence);
      scheduleOnRN(impactHaptic);
    }
    if (nextFrame.selectionSequence > seenSelection.get()) {
      seenSelection.set(nextFrame.selectionSequence);
      scheduleOnRN(impactHaptic);
    }
    const chipVisible = nextFrame.copyOpacity >= 0.5;
    const timerControlVisible = nextFrame.timerOpacity > 0.1 &&
      (nextFrame.phase === PHASE_ACTIVE || nextFrame.phase === PHASE_MORPHING || nextFrame.phase === PHASE_RETURNING);
    if (publishedPhase.get() !== nextFrame.phase || publishedSelected.get() !== nextFrame.selectedCard ||
        publishedSeconds.get() !== nextFrame.remainingSeconds || publishedCadence.get() !== cadence ||
        publishedChipVisible.get() !== chipVisible || publishedTimerControlVisible.get() !== timerControlVisible) {
      publishedPhase.set(nextFrame.phase);
      publishedSelected.set(nextFrame.selectedCard);
      publishedSeconds.set(nextFrame.remainingSeconds);
      publishedCadence.set(cadence);
      publishedChipVisible.set(chipVisible);
      publishedTimerControlVisible.set(timerControlVisible);
      scheduleOnRN(publishSemantic, nextFrame.phase, nextFrame.selectedCard, nextFrame.remainingSeconds, cadence, chipVisible, timerControlVisible);
    }
  }, [publishSemantic, publishedCadence, publishedChipVisible, publishedPhase, publishedSeconds, publishedSelected, publishedTimerControlVisible, seenActivation, seenSelection]);

  const sampleUI = useCallback((now: number) => {
    'worklet';
    const result = sampleMotion(motion.get(), now, quietShared.get());
    motion.set(result.state);
    frame.set(result.frame);
    publishUI(result.frame, result.cadence);
  }, [frame, motion, publishUI, quietShared]);

  const logicalNowUI = useCallback((realSample = performance.now() / 1000) => {
    'worklet';
    const next = advanceLogicalClock(clock.get(), lastTimestamp.get(), realSample, foreground.get());
    clock.set(next.clock);
    lastTimestamp.set(next.realSample);
    return next.clock;
  }, [clock, foreground, lastTimestamp]);

  const beginPressUI = useCallback(() => {
    'worklet';
    const now = logicalNowUI();
    motion.set(beginPress(motion.get(), now));
    sampleUI(now);
  }, [logicalNowUI, motion, sampleUI]);

  const endPressUI = useCallback(() => {
    'worklet';
    const now = logicalNowUI();
    motion.set(endPress(motion.get(), now, quietShared.get()));
    sampleUI(now);
  }, [logicalNowUI, motion, quietShared, sampleUI]);

  const cancelPressUI = useCallback(() => {
    'worklet';
    const now = logicalNowUI();
    motion.set(cancelPress(motion.get(), now));
    sampleUI(now);
  }, [logicalNowUI, motion, sampleUI]);

  const activateUI = useCallback(() => {
    'worklet';
    const now = logicalNowUI();
    motion.set(activate(motion.get(), now, quietShared.get()));
    sampleUI(now);
  }, [logicalNowUI, motion, quietShared, sampleUI]);

  const stopOrResumeUI = useCallback(() => {
    'worklet';
    const now = logicalNowUI();
    const current = motion.get();
    motion.set(current.phase === PHASE_RETURNING ? activate(current, now, quietShared.get()) : stop(current, now, quietShared.get()));
    sampleUI(now);
  }, [logicalNowUI, motion, quietShared, sampleUI]);

  const beginCarouselUI = useCallback(() => {
    'worklet';
    const now = logicalNowUI();
    const next = beginCarousel(motion.get(), now);
    motion.set(next);
    carouselAccepted.set(next.carouselDragging);
    sampleUI(now);
  }, [carouselAccepted, logicalNowUI, motion, sampleUI]);

  const updateCarouselUI = useCallback((translation: number) => {
    'worklet';
    if (!carouselAccepted.get()) return;
    const now = logicalNowUI();
    motion.set(updateCarousel(motion.get(), translation));
    sampleUI(now);
  }, [carouselAccepted, logicalNowUI, motion, sampleUI]);

  const endCarouselUI = useCallback((translation: number, predicted: number) => {
    'worklet';
    if (!carouselAccepted.get()) return;
    const now = logicalNowUI();
    motion.set(endCarousel(motion.get(), translation, predicted, now));
    carouselAccepted.set(false);
    sampleUI(now);
  }, [carouselAccepted, logicalNowUI, motion, sampleUI]);

  const cancelCarouselUI = useCallback(() => {
    'worklet';
    if (!carouselAccepted.get()) return;
    const now = logicalNowUI();
    motion.set(cancelCarousel(motion.get(), now));
    carouselAccepted.set(false);
    sampleUI(now);
  }, [carouselAccepted, logicalNowUI, motion, sampleUI]);

  const setAutoPlayUI = useCallback((enabled: boolean) => {
    'worklet';
    const now = logicalNowUI();
    motion.set(setAutoPlay(motion.get(), enabled, now));
    sampleUI(now);
  }, [logicalNowUI, motion, sampleUI]);

  const setQuietUI = useCallback((nextQuiet: boolean) => {
    'worklet';
    quietShared.set(nextQuiet);
    const now = logicalNowUI();
    motion.set(setQuiet(motion.get(), nextQuiet, now));
    sampleUI(now);
  }, [logicalNowUI, motion, quietShared, sampleUI]);

  const backgroundUI = useCallback(() => {
    'worklet';
    const now = logicalNowUI();
    foreground.set(false);
    lastTimestamp.set(-1);
    motion.set(suspendMotion(motion.get(), now));
    sampleUI(now);
  }, [foreground, lastTimestamp, logicalNowUI, motion, sampleUI]);

  const foregroundUI = useCallback((backgroundSeconds: number) => {
    'worklet';
    foreground.set(true);
    lastTimestamp.set(performance.now() / 1000);
    motion.set(resumeAfterBackground(motion.get(), backgroundSeconds));
    sampleUI(clock.get());
  }, [clock, foreground, lastTimestamp, motion, sampleUI]);

  const stepSecondsUI = useCallback(() => {
    'worklet';
    if (!foreground.get()) return;
    sampleUI(logicalNowUI());
  }, [foreground, logicalNowUI, sampleUI]);

  const frameWorklet = useCallback(() => {
    'worklet';
    if (!foreground.get()) {
      lastTimestamp.set(-1);
      return;
    }
    // Frame timestamps and performance.now() can have different origins after resume.
    // Every input, frame and seconds tick samples the same UI-runtime clock.
    sampleUI(logicalNowUI());
  }, [foreground, lastTimestamp, logicalNowUI, sampleUI]);

  const frameCallback = useFrameCallback(frameWorklet, false);

  useEffect(() => {
    frameCallback.setActive(foregroundState && semantic.cadence === CLOCK_ANIMATION);
  }, [foregroundState, frameCallback, semantic.cadence]);

  useEffect(() => {
    if (!foregroundState || semantic.cadence !== CLOCK_SECONDS) return;
    const timer = setInterval(() => {
      scheduleOnUI(stepSecondsUI);
    }, 1000);
    return () => clearInterval(timer);
  }, [foregroundState, semantic.cadence, stepSecondsUI]);

  useEffect(() => { scheduleOnUI(setAutoPlayUI, autoPlay); }, [autoPlay, setAutoPlayUI]);
  useEffect(() => { scheduleOnUI(setQuietUI, quiet); }, [quiet, setQuietUI]);

  useEffect(() => {
    const subscription = AccessibilityInfo.addEventListener('reduceMotionChanged', setReducedMotion);
    return () => subscription.remove();
  }, []);

  useEffect(() => {
    let mounted = true;
    void AccessibilityInfo.isScreenReaderEnabled().then((enabled) => { if (mounted) setScreenReader(enabled); });
    const subscription = AccessibilityInfo.addEventListener('screenReaderChanged', setScreenReader);
    return () => { mounted = false; subscription.remove(); };
  }, []);

  useEffect(() => {
    const subscription = AppState.addEventListener('change', (next) => {
      const active = next === 'active';
      setForegroundState(active);
      if (!active && backgroundWall.current === null) {
        backgroundWall.current = Date.now() / 1000;
        scheduleOnUI(backgroundUI);
      } else if (active && backgroundWall.current !== null) {
        const duration = backgroundWall.current === null ? 0 : Date.now() / 1000 - backgroundWall.current;
        backgroundWall.current = null;
        scheduleOnUI(foregroundUI, duration);
      }
    });
    return () => subscription.remove();
  }, [backgroundUI, foregroundUI]);

  const nativeScroll = useMemo(() => Gesture.Native(), []);
  const carouselPan = useMemo(() => Gesture.Pan()
    .activeOffsetX([-8 * scale, 8 * scale])
    .failOffsetY([-8 * scale, 8 * scale])
    .simultaneousWithExternalGesture(nativeScroll)
    .onStart(() => {
      carouselEnded.set(false);
      beginCarouselUI();
    })
    .onUpdate((event) => updateCarouselUI(event.translationX / scale))
    .onEnd((event) => {
      carouselEnded.set(true);
      endCarouselUI(event.translationX / scale, (event.translationX + event.velocityX * 0.2) / scale);
    })
    .onFinalize(() => {
      if (!carouselEnded.get()) cancelCarouselUI();
      carouselEnded.set(false);
    }), [beginCarouselUI, cancelCarouselUI, carouselEnded, endCarouselUI, nativeScroll, scale, updateCarouselUI]);
  const scrollGesture = useMemo(() => Gesture.Simultaneous(nativeScroll, carouselPan), [carouselPan, nativeScroll]);

  const chipPan = useMemo(() => Gesture.Pan()
    .minDistance(0)
    .maxPointers(1)
    .simultaneousWithExternalGesture(nativeScroll)
    .onBegin(() => {
      chipCanceled.set(false);
      chipEnded.set(false);
      beginPressUI();
    })
    .onUpdate((event) => {
      if (chipCanceled.get()) return;
      if (Math.hypot(event.translationX, event.translationY) > 18 * scale) {
        chipCanceled.set(true);
        cancelPressUI();
      }
    })
    .onTouchesUp(() => {
      // A stationary quick tap may end before Pan becomes ACTIVE.
      // Touch-up still owns commitment; cancellation remains in onFinalize.
      if (!chipEnded.get()) {
        chipEnded.set(true);
        if (!chipCanceled.get()) endPressUI();
      }
    })
    .onEnd(() => {
      if (!chipEnded.get()) {
        chipEnded.set(true);
        if (!chipCanceled.get()) endPressUI();
      }
    })
    .onFinalize(() => {
      if (!chipEnded.get() && !chipCanceled.get()) cancelPressUI();
      chipEnded.set(false);
      chipCanceled.set(false);
    }), [beginPressUI, cancelPressUI, chipCanceled, chipEnded, endPressUI, nativeScroll, scale]);

  const stopTap = useMemo(() => Gesture.Tap().onEnd((_event, success) => {
    if (success) stopOrResumeUI();
  }), [stopOrResumeUI]);

  const accessibilityActivate = useCallback(() => scheduleOnUI(activateUI), [activateUI]);
  const accessibilityStop = useCallback(() => scheduleOnUI(stopOrResumeUI), [stopOrResumeUI]);
  const toggleMix = useCallback((label: string) => {
    setSelectedMix((current) => current === label ? null : label);
    selectionHaptic();
  }, []);

  return (
    <GestureHandlerRootView style={styles.screen}>
      <StatusBar style="light" />
      <GestureDetector gesture={scrollGesture} touchAction="pan-y">
        <ScrollView
          style={styles.scroll}
          contentContainerStyle={{ width, height: contentHeight }}
          showsVerticalScrollIndicator={false}
        >
          <View style={{ width, height: contentHeight }}>
            <GrassTimerHero frame={frame} scale={scale} safeTop={insets.top} width={width} height={heroHeight} />
            <CardCopy index={0} frame={frame} scale={scale} safeTop={insets.top} selectedForAccessibility={semantic.selectedCard === 0 && semantic.chipVisible} />
            <CardCopy index={1} frame={frame} scale={scale} safeTop={insets.top} selectedForAccessibility={semantic.selectedCard === 1 && semantic.chipVisible} />
            <TimerReadout frame={frame} scale={scale} safeTop={insets.top} semantic={semantic} />
            {semantic.chipVisible && <ChipControl frame={frame} gesture={chipPan} scale={scale} safeTop={insets.top} semantic={semantic} onAccessibilityActivate={accessibilityActivate} />}
            {semantic.timerControlVisible && <StopControl frame={frame} gesture={stopTap} scale={scale} safeTop={insets.top} phase={semantic.phase} onAccessibilityActivate={accessibilityStop} />}
            <StaticEditorial scale={scale} safeTop={insets.top} selectedMix={selectedMix} onToggleMix={toggleMix} />
          </View>
        </ScrollView>
      </GestureDetector>
      <View style={[styles.tabsPosition, { bottom: 27 * scale, left: (width - 280 * scale) / 2 }]}>
        <BottomTabs scale={scale} />
      </View>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: BACKGROUND },
  scroll: { flex: 1, backgroundColor: BACKGROUND, overflow: 'visible' },
  cardCopy: { position: 'absolute', left: 0, top: 0 },
  timerCaption: { position: 'absolute', color: '#FFFFFF', fontWeight: '500', textAlign: 'center' },
  cardTitleBlock: { position: 'absolute' },
  cardTitle: { color: '#FFFFFF', fontWeight: '600' },
  cardSubtitle: { color: MUTED, fontWeight: '400' },
  chipVisual: { position: 'absolute', flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, backgroundColor: 'rgba(255,255,255,0.14)', borderWidth: 1, borderColor: 'rgba(255,255,255,0.12)', overflow: 'hidden' },
  chipText: { color: '#FFFFFF', fontWeight: '500' },
  timerReadout: { position: 'absolute', left: 0, top: 0, alignItems: 'center' },
  timerValue: { color: '#FFFFFF', fontWeight: '500', fontVariant: ['tabular-nums'] },
  timerName: { color: 'rgba(255,255,255,0.75)', fontWeight: '600' },
  chipControl: { position: 'absolute', left: 0, top: 0 },
  stopControl: { position: 'absolute', left: 0, top: 0, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(0,0,0,0.25)', borderWidth: 1, borderColor: 'rgba(255,255,255,0.18)' },
  stopText: { color: 'rgba(255,255,255,0.9)', fontWeight: '600' },
  mixesCopy: { position: 'absolute' },
  headingRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  sectionTitle: { color: '#FFFFFF', fontWeight: '500' },
  sectionBody: { color: MUTED, fontWeight: '400' },
  mixes: { position: 'absolute', flexDirection: 'row' },
  orb: { alignItems: 'center', justifyContent: 'center', borderColor: 'rgba(255,255,255,0.30)', overflow: 'hidden' },
  orbIcon: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, alignItems: 'center', justifyContent: 'center' },
  orbLabel: { color: '#FFFFFF', fontWeight: '600', textAlign: 'center' },
  storiesCopy: { position: 'absolute' },
  story: { position: 'absolute', overflow: 'hidden' },
  tabsPosition: { position: 'absolute' },
  tabs: { flexDirection: 'row', backgroundColor: 'rgba(52,74,103,0.78)', borderWidth: 1, borderColor: 'rgba(255,255,255,0.18)' },
  tab: { alignItems: 'center', justifyContent: 'center' },
  tabSelected: { backgroundColor: 'rgba(0,0,0,0.20)' },
  tabLabel: { color: '#FFFFFF', fontWeight: '600' },
});
