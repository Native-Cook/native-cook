import { Ionicons } from '@expo/vector-icons';
import { BlurView } from 'expo-blur';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  AccessibilityInfo,
  ActionSheetIOS,
  Alert,
  AppState,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  useWindowDimensions,
  View,
} from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView, ScrollView } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedStyle,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Text as SvgText } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { SkyBackdrop } from './SkyBackdrop';
import { QualityFace, SkyChart } from './SkyChart';
import {
  SKY_SCREEN_TIME_LOOP,
  skyScreenTimeAppear,
  skyScreenTimeBeginScrub,
  skyScreenTimeCadence,
  skyScreenTimeChooseDay,
  skyScreenTimeDuration,
  skyScreenTimeEndScrub,
  skyScreenTimeHours,
  skyScreenTimeInitialState,
  skyScreenTimeReset,
  skyScreenTimeScrub,
  skyScreenTimeSetActive,
  skyScreenTimeSetAutoPlay,
  skyScreenTimeSetQuiet,
  skyScreenTimeTick,
  skyScreenTimeTimeLabel,
  skyScreenTimeToggleFocus,
  skyScreenTimeTotal,
  type SkyScreenTimeCadence,
  type SkyScreenTimeFrame,
  type SkyScreenTimeMotionState,
} from './motion';

export { SKY_SCREEN_TIME_LOOP as LOOP };

const ROUNDED = Platform.OS === 'ios' ? 'ui-rounded' : undefined;

type Props = {
  autoPlay?: boolean;
};

type SemanticState = {
  cadence: SkyScreenTimeCadence;
  selectedIndex: number | null;
  title: SkyScreenTimeFrame['title'];
  focusSeconds: number;
};

function selectionHaptic() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

function AnimatedTitle({ index, title, frame, scale }: { index: number; title: string; frame: SharedValue<SkyScreenTimeFrame>; scale: number }) {
  const style = useAnimatedStyle(() => ({ opacity: frame.get().titleOpacities[index] }));
  return (
    <Animated.Text
      allowFontScaling={false}
      numberOfLines={1}
      adjustsFontSizeToFit
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants"
      style={[styles.heroTitle, { width: 362 * scale, height: 71 * scale, fontSize: 58 * scale, lineHeight: 69 * scale, letterSpacing: -2 * scale }, style]}
    >
      {title}
    </Animated.Text>
  );
}

function Metric({ label, value, scale }: { label: string; value: string; scale: number }) {
  return (
    <View style={{ width: 111 * scale, alignItems: 'center' }} accessible accessibilityLabel={`${label}, ${value}`}>
      <Text allowFontScaling={false} style={[styles.metricLabel, { fontSize: 12 * scale, lineHeight: 15 * scale }]}>{label}</Text>
      <Text allowFontScaling={false} style={[styles.metricValue, { marginTop: 3 * scale, fontSize: 20 * scale, lineHeight: 24 * scale }]}>{value}</Text>
    </View>
  );
}

function NavIcon({ name, size }: { name: React.ComponentProps<typeof Ionicons>['name']; size: number }) {
  return <Ionicons name={name} color="#000000" size={size} />;
}

function Wordmark({ scale }: { scale: number }) {
  return (
    <Svg width={90 * scale} height={35 * scale} viewBox="0 0 110 42" accessible={false}>
      <SvgText x={3} y={32} fontFamily="Helvetica Neue" fontSize={31} fontWeight="900" letterSpacing={-1.5} fill="transparent" stroke="#FFFFFF" strokeWidth={1.8}>MORU</SvgText>
    </Svg>
  );
}

export function SkyScreenTime({ autoPlay = false }: Props) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const initialReduced = useReducedMotion();
  const [reducedMotion, setReducedMotion] = useState(initialReduced);
  const [screenReader, setScreenReader] = useState(false);
  const [fewerEffects, setFewerEffects] = useState(false);
  const quiet = reducedMotion || screenReader || fewerEffects;
  const scale = width / 402;
  const top = useCallback((reference: number) => insets.top + (reference - 62) * scale, [insets.top, scale]);
  const [day, setDay] = useState(0);
  const [expanded, setExpanded] = useState(false);
  const [foregroundState, setForegroundState] = useState(AppState.currentState === 'active');
  const [initialMotion] = useState(() => skyScreenTimeAppear(skyScreenTimeInitialState(), autoPlay, quiet, 0));
  const initialTotal = skyScreenTimeTotal(0);
  const [semantic, setSemantic] = useState<SemanticState>({
    cadence: autoPlay && !quiet ? 'animation' : 'stopped',
    selectedIndex: null,
    title: 'Productive',
    focusSeconds: 0,
  });

  const motion = useSharedValue<SkyScreenTimeMotionState>(initialMotion);
  const frame = useSharedValue<SkyScreenTimeFrame>({
    hour: 3,
    selection: 0,
    quality: initialTotal.quality,
    storm: 0,
    focusSeconds: 0,
    selectedIndex: null,
    title: 'Productive',
    titleOpacities: [1, 0, 0],
  });
  const clock = useSharedValue(0);
  const lastReal = useSharedValue(-1);
  const publishedCadence = useSharedValue<SkyScreenTimeCadence>(semantic.cadence);
  const publishedIndex = useSharedValue(-1);
  const publishedTitle = useSharedValue<SkyScreenTimeFrame['title']>('Productive');
  const publishedFocus = useSharedValue(0);
  const touchAccepted = useSharedValue(false);
  const touchIdentifier = useSharedValue(-1);
  const detailsProgress = useSharedValue(0);

  const publishSemantic = useCallback((cadence: SkyScreenTimeCadence, selectedIndex: number, title: SkyScreenTimeFrame['title'], focusSeconds: number) => {
    setSemantic((current) => {
      const index = selectedIndex < 0 ? null : selectedIndex;
      return current.cadence === cadence && current.selectedIndex === index && current.title === title && current.focusSeconds === focusSeconds
        ? current
        : { cadence, selectedIndex: index, title, focusSeconds };
    });
  }, []);

  const publishUI = useCallback((nextFrame: SkyScreenTimeFrame, cadence: SkyScreenTimeCadence) => {
    'worklet';
    const selectedIndex = nextFrame.selectedIndex ?? -1;
    if (publishedCadence.get() !== cadence || publishedIndex.get() !== selectedIndex || publishedTitle.get() !== nextFrame.title || publishedFocus.get() !== nextFrame.focusSeconds) {
      publishedCadence.set(cadence);
      publishedIndex.set(selectedIndex);
      publishedTitle.set(nextFrame.title);
      publishedFocus.set(nextFrame.focusSeconds);
      scheduleOnRN(publishSemantic, cadence, selectedIndex, nextFrame.title, nextFrame.focusSeconds);
    }
  }, [publishSemantic, publishedCadence, publishedFocus, publishedIndex, publishedTitle]);

  const sampleUI = useCallback((now: number) => {
    'worklet';
    const tick = skyScreenTimeTick(motion.get(), now);
    motion.set(tick.state);
    frame.set(tick.frame);
    publishUI(tick.frame, skyScreenTimeCadence(tick.state, now));
  }, [frame, motion, publishUI]);

  const nowUI = useCallback(() => {
    'worklet';
    const real = performance.now() / 1000;
    const previous = lastReal.get();
    const next = clock.get() + (previous >= 0 ? Math.max(0, real - previous) : 0);
    clock.set(next);
    lastReal.set(real);
    return next;
  }, [clock, lastReal]);

  const initializeUI = useCallback((play: boolean, calm: boolean, active: boolean) => {
    'worklet';
    const now = nowUI();
    let next = skyScreenTimeAppear(motion.get(), play, calm, now);
    next = skyScreenTimeSetActive(next, active, now);
    motion.set(next);
    sampleUI(now);
  }, [motion, nowUI, sampleUI]);

  const configureUI = useCallback((kind: number, value: boolean) => {
    'worklet';
    const now = nowUI();
    let next = motion.get();
    if (kind === 0) next = skyScreenTimeSetAutoPlay(next, value, now);
    else if (kind === 1) next = skyScreenTimeSetQuiet(next, value, now);
    else {
      next = skyScreenTimeSetActive(next, value, now);
      if (!value) {
        touchAccepted.set(false);
        touchIdentifier.set(-1);
      }
    }
    motion.set(next);
    sampleUI(now);
  }, [motion, nowUI, sampleUI, touchAccepted, touchIdentifier]);

  const chooseDayUI = useCallback((index: number) => {
    'worklet';
    const now = nowUI();
    motion.set(skyScreenTimeChooseDay(motion.get(), index, now));
    sampleUI(now);
  }, [motion, nowUI, sampleUI]);

  const resetUI = useCallback(() => {
    'worklet';
    const now = nowUI();
    motion.set(skyScreenTimeReset(motion.get(), now));
    sampleUI(now);
  }, [motion, nowUI, sampleUI]);

  const toggleFocusUI = useCallback(() => {
    'worklet';
    const now = nowUI();
    motion.set(skyScreenTimeToggleFocus(motion.get(), now));
    sampleUI(now);
  }, [motion, nowUI, sampleUI]);

  const adjustHourUI = useCallback((delta: number) => {
    'worklet';
    const now = nowUI();
    const current = frame.get().selectedIndex ?? 0;
    let next = skyScreenTimeBeginScrub(motion.get(), now);
    next = skyScreenTimeScrub(next, current + delta);
    next = skyScreenTimeEndScrub(next, now);
    motion.set(next);
    sampleUI(now);
  }, [frame, motion, nowUI, sampleUI]);

  const frameWorklet = useCallback(() => {
    'worklet';
    sampleUI(nowUI());
  }, [nowUI, sampleUI]);
  const frameCallback = useFrameCallback(frameWorklet, false);

  const secondsUI = useCallback(() => {
    'worklet';
    sampleUI(nowUI());
  }, [nowUI, sampleUI]);

  useEffect(() => { scheduleOnUI(initializeUI, autoPlay, quiet, AppState.currentState === 'active'); }, [autoPlay, initializeUI, quiet]);
  useEffect(() => { scheduleOnUI(configureUI, 0, autoPlay); }, [autoPlay, configureUI]);
  useEffect(() => { scheduleOnUI(configureUI, 1, quiet); }, [configureUI, quiet]);
  useEffect(() => {
    frameCallback.setActive(foregroundState && semantic.cadence === 'animation');
  }, [foregroundState, frameCallback, semantic.cadence]);
  useEffect(() => {
    if (!foregroundState || semantic.cadence !== 'seconds') return;
    const timer = setInterval(() => scheduleOnUI(secondsUI), 1000);
    return () => clearInterval(timer);
  }, [foregroundState, secondsUI, semantic.cadence]);

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
      scheduleOnUI(configureUI, 2, active);
    });
    return () => subscription.remove();
  }, [configureUI]);

  const nativeScroll = useMemo(() => Gesture.Native(), []);
  const chartGesture = useMemo(() => Gesture.Manual()
    .simultaneousWithExternalGesture(nativeScroll)
    .onTouchesDown((event, manager) => {
      if (touchAccepted.get() || event.changedTouches.length === 0) return;
      manager.activate();
      const touch = event.changedTouches[0];
      const now = nowUI();
      let next = skyScreenTimeBeginScrub(motion.get(), now);
      next = skyScreenTimeScrub(next, (touch.x / scale - 6) / (292 / 24));
      motion.set(next);
      touchAccepted.set(true);
      touchIdentifier.set(touch.id);
      sampleUI(now);
    })
    .onTouchesMove((event) => {
      if (!touchAccepted.get()) return;
      const touch = event.changedTouches.find((item) => item.id === touchIdentifier.get());
      if (!touch) return;
      const now = nowUI();
      motion.set(skyScreenTimeScrub(motion.get(), (touch.x / scale - 6) / (292 / 24)));
      sampleUI(now);
    })
    .onTouchesUp((event, manager) => {
      if (!event.changedTouches.some((touch) => touch.id === touchIdentifier.get())) return;
      if (touchAccepted.get()) {
        const now = nowUI();
        motion.set(skyScreenTimeEndScrub(motion.get(), now));
        touchAccepted.set(false);
        touchIdentifier.set(-1);
        sampleUI(now);
        scheduleOnRN(selectionHaptic);
      }
      manager.end();
    })
    .onTouchesCancelled((event, manager) => {
      if (!event.changedTouches.some((touch) => touch.id === touchIdentifier.get())) return;
      if (touchAccepted.get()) {
        const now = nowUI();
        motion.set(skyScreenTimeEndScrub(motion.get(), now));
        touchAccepted.set(false);
        touchIdentifier.set(-1);
        sampleUI(now);
      }
      manager.end();
    })
    .onFinalize(() => {
      if (!touchAccepted.get()) return;
      const now = nowUI();
      motion.set(skyScreenTimeEndScrub(motion.get(), now));
      touchAccepted.set(false);
      touchIdentifier.set(-1);
      sampleUI(now);
    }), [motion, nativeScroll, nowUI, sampleUI, scale, touchAccepted, touchIdentifier]);

  const hours = useMemo(() => skyScreenTimeHours(day), [day]);
  const total = useMemo(() => skyScreenTimeTotal(day), [day]);
  const selected = semantic.selectedIndex === null ? total : hours[semantic.selectedIndex];
  const divisor = Math.max(0.01, selected.minutes);
  const percentages = [selected.productive, selected.neutral, selected.distracted].map((value) => Math.round(value / divisor * 100));
  const detailsStyle = useAnimatedStyle(() => ({ opacity: detailsProgress.get(), transform: [{ translateY: (1 - detailsProgress.get()) * 12 * scale }] }));
  const cardStyle = useAnimatedStyle(() => ({ height: (268 + 94 * detailsProgress.get()) * scale }));
  const ctaStyle = useAnimatedStyle(() => ({ transform: [{ translateY: 94 * detailsProgress.get() * scale }] }));

  const chooseDay = useCallback((index: number) => {
    setDay(index);
    scheduleOnUI(chooseDayUI, index);
    selectionHaptic();
  }, [chooseDayUI]);
  const showDayMenu = useCallback(() => {
    if (Platform.OS === 'ios') {
      ActionSheetIOS.showActionSheetWithOptions({ options: ['Cancel', 'Today', 'Yesterday'], cancelButtonIndex: 0 }, (index) => {
        if (index === 1) chooseDay(0);
        else if (index === 2) chooseDay(1);
      });
    } else {
      Alert.alert('Choose day', undefined, [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Today', onPress: () => chooseDay(0) },
        { text: 'Yesterday', onPress: () => chooseDay(1) },
      ]);
    }
  }, [chooseDay]);
  const reset = useCallback(() => {
    scheduleOnUI(resetUI);
    selectionHaptic();
  }, [resetUI]);
  const showOptions = useCallback(() => {
    const toggleEffects = () => setFewerEffects((current) => !current);
    if (Platform.OS === 'ios') {
      ActionSheetIOS.showActionSheetWithOptions({ options: ['Cancel', fewerEffects ? 'Use full effects' : 'Reduce effects', 'Clear selected hour'], cancelButtonIndex: 0 }, (index) => {
        if (index === 1) toggleEffects();
        else if (index === 2) reset();
      });
    } else {
      Alert.alert('Screen time options', undefined, [
        { text: 'Cancel', style: 'cancel' },
        { text: fewerEffects ? 'Use full effects' : 'Reduce effects', onPress: toggleEffects },
        { text: 'Clear selected hour', onPress: reset },
      ]);
    }
  }, [fewerEffects, reset]);
  const toggleExpanded = useCallback(() => {
    const next = !expanded;
    setExpanded(next);
    detailsProgress.set(quiet
      ? withTiming(next ? 1 : 0, { duration: 0 })
      : withSpring(next ? 1 : 0, { damping: 16.4, stiffness: 130, mass: 1 }));
  }, [detailsProgress, expanded, quiet]);
  const toggleFocus = useCallback(() => {
    scheduleOnUI(toggleFocusUI);
    selectionHaptic();
  }, [toggleFocusUI]);
  const adjustHour = useCallback((delta: number) => {
    scheduleOnUI(adjustHourUI, delta);
    selectionHaptic();
  }, [adjustHourUI]);

  const contentHeight = Math.max(height, insets.top + (812 + (expanded ? 94 : 0)) * scale + insets.bottom);
  const focusLabel = semantic.focusSeconds > 0
    ? `End focus · ${Math.floor(semantic.focusSeconds / 60)}:${String(semantic.focusSeconds % 60).padStart(2, '0')}`
    : 'Start focusing';
  const selectedActivity = semantic.selectedIndex === null
    ? 'Drag across the chart to see how each hour felt.'
    : `${skyScreenTimeDuration(hours[semantic.selectedIndex].minutes)} on screen · ${hours[semantic.selectedIndex].pickups} pickups · ${hours[semantic.selectedIndex].notifications} notifications`;

  return (
    <GestureHandlerRootView style={styles.screen}>
      <StatusBar style="light" />
      <SkyBackdrop frame={frame} width={width} height={height} scale={scale} />
      <GestureDetector gesture={nativeScroll}>
        <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ width, height: contentHeight }}>
          <View style={{ width, height: contentHeight }}>
            <View style={{ position: 'absolute', left: 20 * scale, top: top(79), width: 90 * scale, height: 35 * scale }} accessible accessibilityRole="header" accessibilityLabel="Moru">
              <Wordmark scale={scale} />
            </View>
            <Pressable onPress={showDayMenu} accessibilityRole="button" accessibilityLabel={day === 0 ? 'Today' : 'Yesterday'} accessibilityHint="Choose the day to review" style={[styles.dayButton, { left: 148 * scale, top: top(72), width: 106 * scale, height: 44 * scale }]}>
              <Text allowFontScaling={false} style={[styles.dayText, { fontSize: 15 * scale }]}>{day === 0 ? 'Today' : 'Yesterday'}</Text>
              <Ionicons name="chevron-down" color="#FFFFFF" size={13 * scale} />
            </Pressable>
            <Pressable onPress={reset} accessibilityRole="button" accessibilityLabel="Reset daily overview" style={[styles.headerAction, { left: 284 * scale, top: top(72), width: 44 * scale, height: 44 * scale, borderRadius: 22 * scale, borderWidth: scale }]}>
              <Ionicons name="refresh" color="#FFFFFF" size={24 * scale} />
            </Pressable>
            <Pressable onPress={showOptions} accessibilityRole="button" accessibilityLabel="Screen time options" style={[styles.headerAction, { left: 337 * scale, top: top(72), width: 44 * scale, height: 44 * scale, borderRadius: 22 * scale, borderWidth: scale }]}>
              <Ionicons name="ellipsis-horizontal" color="#FFFFFF" size={25 * scale} />
            </Pressable>

            <Text allowFontScaling={false} style={[styles.quality, { top: top(180), left: 111 * scale, width: 180 * scale, height: 24 * scale, fontSize: 18 * scale, lineHeight: 24 * scale }]}>
              {semantic.selectedIndex === null ? 'Quality' : skyScreenTimeTimeLabel(semantic.selectedIndex)}
            </Text>
            <View accessible accessibilityRole="text" accessibilityLabel={semantic.title} style={{ position: 'absolute', left: 20 * scale, top: top(202.5), width: 362 * scale, height: 71 * scale }}>
              {(['Productive', 'Balanced', 'Distracted'] as const).map((title, index) => (
                <AnimatedTitle key={title} index={index} title={title} frame={frame} scale={scale} />
              ))}
            </View>

            <View style={[styles.faces, { left: 124 * scale, top: top(274), width: 154 * scale, gap: 11 * scale }]}>
              {(['Productive', 'Neutral', 'Distracted'] as const).map((label, index) => (
                <View key={label} style={{ width: 44 * scale, alignItems: 'center' }} accessible accessibilityLabel={`${label}, ${percentages[index]} percent`}>
                  <QualityFace kind={index as 0 | 1 | 2} size={28 * scale} />
                  <Text allowFontScaling={false} numberOfLines={1} style={[styles.percent, { marginTop: 7 * scale, width: 44 * scale, fontSize: 16 * scale, lineHeight: 20 * scale }]}>{percentages[index]}%</Text>
                </View>
              ))}
            </View>
            <View pointerEvents="none" style={[styles.dots, { left: 193 * scale, top: top(350), width: 16 * scale, height: 6 * scale, gap: 5 * scale }]}>
              <View style={[styles.dot, { width: 6 * scale, height: 6 * scale, borderRadius: 3 * scale, opacity: 0.25 }]} />
              <View style={[styles.dot, { width: 6 * scale, height: 6 * scale, borderRadius: 3 * scale }]} />
            </View>

            <Animated.View style={[styles.card, { left: 20 * scale, top: top(419), width: 362 * scale, borderRadius: 32 * scale }, cardStyle]}>
              <View style={[styles.metrics, { left: 13 * scale, top: 27 * scale }]}>
                <Metric label="Screen time" value={skyScreenTimeDuration(total.minutes)} scale={scale} />
                <View style={[styles.divider, { width: 2 * scale, height: 16 * scale }]} />
                <Metric label="Pickups" value={String(total.pickups)} scale={scale} />
                <View style={[styles.divider, { width: 2 * scale, height: 16 * scale }]} />
                <Metric label="Notifications" value={String(total.notifications)} scale={scale} />
              </View>
              <GestureDetector gesture={chartGesture}>
                <View
                  style={{ position: 'absolute', left: 17 * scale, top: 89 * scale, width: 330 * scale, height: 126 * scale }}
                  accessible
                  accessibilityRole="adjustable"
                  accessibilityLabel="Hourly screen time"
                  accessibilityValue={{ text: semantic.selectedIndex === null ? 'Daily overview' : `${skyScreenTimeTimeLabel(semantic.selectedIndex)}, ${skyScreenTimeDuration(hours[semantic.selectedIndex].minutes)}` }}
                  onAccessibilityAction={(event) => {
                    if (event.nativeEvent.actionName === 'increment') adjustHour(1);
                    else if (event.nativeEvent.actionName === 'decrement') adjustHour(-1);
                  }}
                  accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
                >
                  <SkyChart hours={hours} frame={frame} width={330 * scale} height={126 * scale} />
                </View>
              </GestureDetector>
              <Pressable onPress={toggleExpanded} accessibilityRole="button" accessibilityLabel={expanded ? 'Hide hourly details' : 'Show hourly details'} style={[styles.chevron, { left: 141 * scale, top: 223 * scale, width: 80 * scale, height: 44 * scale }]}>
                <Ionicons name={expanded ? 'chevron-up' : 'chevron-down'} color="#C9CBD0" size={21 * scale} />
              </Pressable>
              <Animated.View style={[styles.details, { left: 22 * scale, top: 280 * scale, width: 318 * scale }, detailsStyle]} pointerEvents={expanded ? 'auto' : 'none'}>
                <Text allowFontScaling={false} style={[styles.detailsTitle, { fontSize: 15 * scale, lineHeight: 19 * scale }]}>{semantic.selectedIndex === null ? 'Your day at a glance' : `${skyScreenTimeTimeLabel(semantic.selectedIndex)} activity`}</Text>
                <Text allowFontScaling={false} style={[styles.detailsBody, { marginTop: 7 * scale, fontSize: 12 * scale, lineHeight: 16 * scale }]}>{selectedActivity}</Text>
              </Animated.View>
            </Animated.View>

            <Animated.View style={[{ position: 'absolute', left: 20 * scale, top: top(708), width: 362 * scale, height: 61 * scale }, ctaStyle]}>
              <Pressable onPress={toggleFocus} accessibilityRole="button" accessibilityLabel={focusLabel} accessibilityHint={semantic.focusSeconds > 0 ? 'Ends the current focus session' : 'Starts a twenty-five minute focus timer'} style={{ width: 362 * scale, height: 61 * scale }}>
                <LinearGradient
                  colors={['#4388D0', '#508FD9', '#C7E4FA']}
                  locations={[0, 0.8, 1]}
                  start={{ x: 0, y: 0.5 }}
                  end={{ x: 1, y: 0.5 }}
                  style={[styles.cta, { borderRadius: 31 * scale, borderWidth: scale }]}
                >
                  <Ionicons name={semantic.focusSeconds > 0 ? 'stop' : 'add'} color="#FFFFFF" size={24 * scale} />
                  <Text allowFontScaling={false} style={[styles.ctaText, { fontSize: 18 * scale }]}>{focusLabel}</Text>
                </LinearGradient>
              </Pressable>
            </Animated.View>
          </View>
        </ScrollView>
      </GestureDetector>

      <View style={[styles.nav, { left: 20 * scale, bottom: 20 * scale, width: 362 * scale, height: 64 * scale, borderRadius: 32 * scale, padding: 4 * scale, borderWidth: 1.2 * scale }]}>
        <BlurView intensity={38} tint="light" style={[StyleSheet.absoluteFill, { borderRadius: 32 * scale, overflow: 'hidden' }]} />
        {[
          ['Home', 'home'] as const,
          ['Rules', 'lock-closed-outline'] as const,
          ['Squads', 'people-outline'] as const,
          ['Profile', 'person-outline'] as const,
        ].map(([label, icon]) => (
          <View key={label} style={[styles.navItem, { width: 88.5 * scale, height: 56 * scale, borderRadius: 28 * scale }, label === 'Home' && styles.navSelected]} accessible accessibilityLabel={`${label}${label === 'Home' ? ', selected' : ''}`}>
            <NavIcon name={icon} size={23 * scale} />
            <Text allowFontScaling={false} style={[styles.navLabel, { marginTop: 4 * scale, fontSize: 11 * scale }]}>{label}</Text>
          </View>
        ))}
      </View>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#2E70E9' },
  dayButton: { position: 'absolute', flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 7 },
  dayText: { fontFamily: ROUNDED, color: '#FFFFFF', fontWeight: '700' },
  headerAction: { position: 'absolute', alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(0,80,210,0.10)', borderColor: 'rgba(255,255,255,0.35)' },
  quality: { fontFamily: ROUNDED, position: 'absolute', color: '#FFFFFF', fontWeight: '600', textAlign: 'center' },
  heroTitle: { fontFamily: ROUNDED, position: 'absolute', color: '#FFFFFF', fontWeight: '700', textAlign: 'center' },
  faces: { position: 'absolute', flexDirection: 'row' },
  percent: { fontFamily: ROUNDED, color: '#FFFFFF', fontWeight: '600', textAlign: 'center' },
  dots: { position: 'absolute', flexDirection: 'row', alignItems: 'center' },
  dot: { backgroundColor: '#FFFFFF' },
  card: { position: 'absolute', backgroundColor: '#FFFFFF', overflow: 'hidden' },
  metrics: { position: 'absolute', flexDirection: 'row', alignItems: 'center' },
  metricLabel: { fontFamily: ROUNDED, color: '#888995', fontWeight: '700' },
  metricValue: { fontFamily: ROUNDED, color: '#000000', fontWeight: '700' },
  divider: { backgroundColor: '#E4E4E8' },
  chevron: { position: 'absolute', alignItems: 'center', justifyContent: 'center' },
  details: { position: 'absolute' },
  detailsTitle: { color: '#000000', fontWeight: '600' },
  detailsBody: { color: '#757682' },
  cta: { width: '100%', height: '100%', flexDirection: 'row', gap: 8, alignItems: 'center', justifyContent: 'center', borderColor: 'rgba(255,255,255,0.45)' },
  ctaText: { fontFamily: ROUNDED, color: '#FFFFFF', fontWeight: '700' },
  nav: { position: 'absolute', flexDirection: 'row', borderColor: 'rgba(255,255,255,0.90)', overflow: 'hidden', shadowColor: '#000000', shadowOpacity: 0.12, shadowRadius: 15, shadowOffset: { width: 0, height: 10 }, elevation: 10 },
  navItem: { alignItems: 'center', justifyContent: 'center' },
  navSelected: { backgroundColor: 'rgba(0,0,0,0.09)' },
  navLabel: { color: '#000000', fontWeight: '600' },
});
