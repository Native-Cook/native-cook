import Ionicons from '@expo/vector-icons/Ionicons';
import * as Haptics from 'expo-haptics';
import { Image as ExpoImage } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useFocusEffect } from 'expo-router';
import { type ReactNode, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AppState, type LayoutChangeEvent, StyleSheet, Text, View, type ViewStyle } from 'react-native';
import { Gesture, GestureDetector, Pressable } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation,
  cubicBezier,
  Easing,
  interpolateColor,
  ReduceMotion,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { CommitShimmer } from './CommitShimmer';
import { FIGHTER_INDEX, FIGHTERS, type Fighter, fighterSummary, METRIC_LABELS, type Rect, STAT_ICONS, STAT_LABELS, standIn } from './fighters';
import { clamp, LOOP, mix, nearestHold, ramp, sceneTime, stepAt, WIDTH } from './motion';

export { LOOP } from './motion';
const YELLOW = '#ffdb35';
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);
type IconName = keyof typeof Ionicons.glyphMap;
type Step = 1 | 2 | 3;

// Roster geometry in stage points. Every card is 95 pt; the selection ring sits on top of one.
const ROSTER_TOP = 164;
const ROW = { x: 20, width: 353, height: 95, pitch: 107 } as const;
const ART_BOX = { x: 206, top: -14 } as const;
const HERO_BOX: Rect = { x: 45.8, y: 181.3, width: 287.3, height: 287.3 };
const PINNED_BOX: Rect = { x: 166, y: 38.4, width: 55, height: 55 };
const HALO: Rect = { x: 74, y: 206, width: 246, height: 246 };
// Card surfaces are translucent shades of the page, so a fighter's colour runs through all of them.
const SHADE = 'rgba(12,16,60,0.35)';
const SHADE_SELECTED = 'rgba(12,16,60,0.25)';
const SHADE_SOFT = 'rgba(12,16,60,0.31)';
const LIFT = 'rgba(255,255,255,0.12)';
const HALO_FILL = 'rgba(255,255,255,0.055)';
const STAT_CARD = 118;
const STAT_GAP = 10;
const STAT_SCROLL = FIGHTERS[0].stats.length * (STAT_CARD + STAT_GAP) - STAT_GAP - 353;

function rowTop(index: number) {
  'worklet';
  return ROSTER_TOP + index * ROW.pitch;
}
function containIn(fighter: Fighter, box: Rect): Rect {
  const ratio = fighter.artSize.width / fighter.artSize.height;
  const width = Math.min(box.width, box.height * ratio);
  const height = width / ratio;
  return { x: box.x + (box.width - width) / 2, y: box.y + (box.height - height) / 2, width, height };
}
/** Where a fighter's art sits on its own roster card, in stage points. */
function rowArtRect(fighter: Fighter, index: number): Rect {
  const fit = containIn(fighter, fighter.rowFit);
  return { x: ART_BOX.x + fit.x, y: rowTop(index) + ART_BOX.top + fit.y, width: fit.width, height: fit.height };
}
function mixRect(a: Rect, b: Rect, p: number): Rect {
  'worklet';
  return { x: mix(a.x, b.x, p), y: mix(a.y, b.y, p), width: mix(a.width, b.width, p), height: mix(a.height, b.height, p) };
}

function settleToHold(progress: SharedValue<number>, velocity = 0, animated = true) {
  'worklet';
  const target = nearestHold(progress.get(), velocity);
  if (!animated) { cancelAnimation(progress); progress.set(target); return; }
  progress.set(withSpring(target, {
    duration: 400, dampingRatio: Math.abs(velocity) > 0 ? 0.8 : 1,
    velocity, overshootClamping: true, reduceMotion: ReduceMotion.System,
  }));
}

function PressHighlight({ pressed, radius = 10 }: { pressed: boolean; radius?: number }) {
  return <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, {
    borderRadius: radius, backgroundColor: '#ffffff18', opacity: pressed ? 1 : 0,
    transitionProperty: 'opacity', transitionDuration: 120, transitionTimingFunction: CSS_EASE_OUT,
  }]} />;
}

export type FighterProfileTourProps = {
  autoPlay?: boolean;
  /** Deterministic preview position in seconds, from 0 to LOOP. */
  previewTime?: number;
  previewReducedMotion?: boolean;
  renderer?: string;
  onRendererStatus?: (status: { renderer: 'skia'; ready: boolean; detail: string }) => void;
  onComplete?: () => void;
};

type LayerProps = {
  children: ReactNode;
  time: SharedValue<number>;
  enter?: readonly [number, number];
  exit?: readonly [number, number];
  style?: ViewStyle;
  shift?: number;
  scaleFrom?: number;
};
function MotionLayer({ children, time, enter, exit, style, shift = 10, scaleFrom = 1 }: LayerProps) {
  const animated = useAnimatedStyle(() => {
    const t = time.get();
    const arrival = enter ? ramp(t, enter[0], enter[1]) : 1;
    const departure = exit ? ramp(t, exit[0], exit[1]) : 0;
    return {
      opacity: arrival * (1 - departure),
      transform: [{ translateY: shift * (1 - arrival) - shift * departure }, { scale: mix(scaleFrom, 1, arrival) }],
    };
  });
  return <Animated.View style={[style, animated]}>{children}</Animated.View>;
}

function Heading({ word, rest, time, step }: { word: string; rest: string; time: SharedValue<number>; step: Step }) {
  const enter = step === 1 ? undefined : step === 2 ? [0.18, 0.205] as const : [0.595, 0.62] as const;
  const exit = step === 1 ? [0.14, 0.168] as const : step === 2 ? [0.48, 0.505] as const : undefined;
  return (
    <MotionLayer time={time} enter={enter} exit={exit} style={{ ...styles.heading, top: step === 3 ? 109 : step === 2 ? 94 : 98 }} shift={8}>
      <Text accessibilityRole="header" maxFontSizeMultiplier={1.15} style={[styles.headingWord, step === 2 && { fontSize: 35, lineHeight: 42 }]}>{word}</Text>
      <Text maxFontSizeMultiplier={1.15} style={styles.headingRest}>{rest}</Text>
    </MotionLayer>
  );
}

/** A roster card. Every one can be picked; picking the chosen one again starts the tour. */
function Row({ fighter, index, time, selected, accent, onPick, interactive, hideArt }: {
  fighter: Fighter; index: number; time: SharedValue<number>; selected: boolean; accent: string;
  onPick: (index: number) => void; interactive: boolean; hideArt: SharedValue<number>;
}) {
  // The chosen card's art is handed to the travelling actor the moment the tour begins.
  const art = useAnimatedStyle(() => ({ opacity: selected ? 1 - hideArt.get() : 1 }));
  const fit = containIn(fighter, fighter.rowFit);
  return (
    <MotionLayer time={time} exit={selected ? [0.12, 0.15] : [0.125 + index * 0.005, 0.185 + index * 0.005]} shift={selected ? 0 : -6} style={{ marginBottom: ROW.pitch - ROW.height }}>
      <Pressable accessibilityRole="button" accessibilityLabel={selected ? `${fighter.name}, ${fighter.tier} tier, selected. Explore ${fighter.name}` : `${fighter.name}, ${fighter.tier} tier`}
        accessibilityState={{ selected, disabled: !interactive }} disabled={!interactive} onPress={() => onPick(index)} style={styles.row}>
        {({ pressed }) => <>
          {/* The chosen card's surface is drawn by the morph, which later becomes the hero halo. */}
          {!selected && <View pointerEvents="none" style={[StyleSheet.absoluteFill, styles.rowSurface]} />}
          <PressHighlight pressed={pressed} radius={12} />
          <View style={styles.rowCopy}>
            <Animated.Text maxFontSizeMultiplier={1.15} style={[styles.tier, { color: accent, transitionProperty: 'color', transitionDuration: 420, transitionTimingFunction: CSS_EASE_OUT }]}>{fighter.tier} TIER</Animated.Text>
            <Text maxFontSizeMultiplier={1.15} style={styles.rowName}>{fighter.name}</Text>
          </View>
          <Animated.View pointerEvents="none" style={[styles.rowArt, art]}>
            <ExpoImage source={fighter.art} contentFit="fill" style={{ position: 'absolute', left: fit.x, top: fit.y, width: fit.width, height: fit.height }} transition={0} accessible={false} />
          </Animated.View>
        </>}
      </Pressable>
    </MotionLayer>
  );
}

function Stat({ index, fighter, time, accent }: { index: number; fighter: Fighter; time: SharedValue<number>; accent: string }) {
  const stat = fighter.stats[index];
  const fill = useAnimatedStyle(() => ({ transformOrigin: [0, 0, 0], transform: [{ scaleX: stat.amount * ramp(time.get(), 0.30 + index * 0.005, 0.335 + index * 0.005) }] }));
  return <MotionLayer time={time} enter={[0.278 + index * 0.005, 0.306 + index * 0.005]} exit={[0.50 + index * 0.005, 0.53 + index * 0.005]} scaleFrom={0.97} style={styles.stat}>
    <View style={styles.statTop}>
      <Ionicons name={STAT_ICONS[index] as IconName} size={27} color={accent} />
      <Text maxFontSizeMultiplier={1.15} style={styles.statValue}>{stat.value}</Text>
    </View>
    <Text maxFontSizeMultiplier={1.15} style={styles.statName}>{STAT_LABELS[index]}</Text>
    <View style={styles.statRail}><Animated.View style={[styles.statFill, fill]} /></View>
  </MotionLayer>;
}

/** Stat cards slide sideways like the reference: the demo glides through them, a finger can too. */
function StatStrip({ fighter, time, accent, offset }: { fighter: Fighter; time: SharedValue<number>; accent: string; offset: SharedValue<number> }) {
  const strip = useAnimatedStyle(() => ({ transform: [{ translateX: -offset.get() }] }));
  return <Animated.View style={[styles.statsStrip, strip]}>
    {fighter.stats.map((_, index) => <Stat key={STAT_LABELS[index]} index={index} fighter={fighter} time={time} accent={accent} />)}
  </Animated.View>;
}

function Metric({ label, yours, theirs, index, cardIndex, time, accent }: {
  label: string; yours: number; theirs: number; index: number; cardIndex: number; time: SharedValue<number>; accent: string;
}) {
  const grow = useDerivedValue(() => ramp(time.get(), 0.655 + cardIndex * 0.005 + index * 0.002, 0.69 + cardIndex * 0.005 + index * 0.002));
  const theirFill = useAnimatedStyle(() => ({ transformOrigin: [0, 0, 0], transform: [{ scaleX: theirs * grow.get() }] }));
  const yourFill = useAnimatedStyle(() => ({ transformOrigin: [0, 0, 0], transform: [{ scaleX: yours * grow.get() }] }));
  return <View style={styles.metric}>
    <Text maxFontSizeMultiplier={1.15} style={styles.metricName}>{label}</Text>
    <View style={styles.metricRail}><Animated.View style={[styles.metricFill, { backgroundColor: accent }, theirFill]} /></View>
    <View style={[styles.metricRail, styles.yourRail]}><Animated.View style={[styles.metricFill, { backgroundColor: YELLOW }, yourFill]} /></View>
  </View>;
}

function MatchCard({ index, fighter, you, time, expanded, expandedTarget, firstExpanded, active, accent, onToggle }: {
  index: number; fighter: Fighter; you: Fighter; time: SharedValue<number>; expanded: SharedValue<number>; expandedTarget: SharedValue<number>;
  firstExpanded: SharedValue<number>; active: boolean; accent: string; onToggle: () => void;
}) {
  const [isOpen, setIsOpen] = useState(true);
  useAnimatedReaction(() => expandedTarget.get() > 0.5, (next, previous) => { if (next !== previous) scheduleOnRN(setIsOpen, next); });
  const opening = useDerivedValue(() => ramp(time.get(), 0.632 + index * 0.005, 0.657 + index * 0.005) * expanded.get());
  const card = useAnimatedStyle(() => {
    const enter = ramp(time.get(), 0.6 + index * 0.006, 0.628 + index * 0.006);
    return {
      opacity: enter,
      transform: [{ translateY: 12 * (1 - enter) - (index === 1 ? 128 * (1 - firstExpanded.get()) : 0) }, { scale: 0.975 + 0.025 * enter }],
    };
  });
  // The counter lunges in from the right, the opponent's side, just after the VS slash.
  const lunge = useAnimatedStyle(() => {
    const enter = ramp(time.get(), 0.6 + index * 0.006, 0.638 + index * 0.006, true);
    return { transform: [{ translateX: 46 * (1 - enter) }, { scale: 1.12 - 0.12 * enter }] };
  });
  const background = useAnimatedStyle(() => ({ transformOrigin: [0, 0, 0], transform: [{ scaleY: (64 + 128 * opening.get()) / 192 }] }));
  const body = useAnimatedStyle(() => ({
    opacity: ramp(time.get(), 0.642 + index * 0.005, 0.667 + index * 0.005),
    transform: [{ translateY: -128 * (1 - opening.get()) }],
  }));
  const arrow = useAnimatedStyle(() => ({ transform: [{ rotate: `${180 * (1 - expanded.get())}deg` }] }));
  const head = containIn(fighter, { x: 12, y: 9, width: 87, height: 101 });
  return <Animated.View style={[styles.matchCard, { top: index * 205 }, card]}>
    <Animated.View pointerEvents="none" style={[styles.matchBackground, background]} />
    <Pressable accessibilityRole="button" accessibilityLabel={`${fighter.name} matchup statistics, ${fighter.tier} tier`} accessibilityState={{ expanded: isOpen, disabled: !active }} disabled={!active} onPress={onToggle} style={styles.matchToggle}>
      {({ pressed }) => <>
        <View pointerEvents="none" style={styles.matchHeaderArtClip}>
          <Animated.View style={[{ position: 'absolute', left: head.x, top: head.y, width: head.width, height: head.height }, lunge]}>
            <ExpoImage source={fighter.art} contentFit="fill" style={StyleSheet.absoluteFill} transition={0} accessible={false} />
          </Animated.View>
        </View>
        <PressHighlight pressed={pressed} radius={11} />
        <Text maxFontSizeMultiplier={1.15} style={styles.matchName}>{fighter.name}</Text>
        <Text style={[styles.matchTier, { color: accent }]}>{fighter.tier} Tier</Text>
        <Animated.View style={[styles.chevron, arrow]}><Ionicons name="chevron-up" color={accent} size={17} /></Animated.View>
      </>}
    </Pressable>
    <View pointerEvents="none" style={styles.matchBodyClip} accessibilityElementsHidden={!isOpen} importantForAccessibility={isOpen ? 'auto' : 'no-hide-descendants'}>
      <Animated.View style={[styles.matchBodyContent, body]}>
        <View style={[styles.matchLowerArt, { left: head.x, top: head.y - 64, width: head.width, height: head.height }]}>
          <ExpoImage source={fighter.art} contentFit="fill" style={StyleSheet.absoluteFill} transition={0} accessible={false} />
        </View>
        <View style={styles.matchBody} accessible accessibilityLabel={METRIC_LABELS.map((label, metric) => `${label}: ${fighter.name} ${Math.round(fighter.metrics[metric] * 100)}, ${you.name} ${Math.round(you.metrics[metric] * 100)}`).join('. ')}>
          {METRIC_LABELS.map((label, metric) => <Metric key={label} label={label} theirs={fighter.metrics[metric]} yours={you.metrics[metric]} index={metric} cardIndex={index} time={time} accent={accent} />)}
        </View>
      </Animated.View>
    </View>
  </Animated.View>;
}

/** A Smash-style VS beat between learning the fighter and meeting its counters. */
function VersusSlash({ time, accent, height }: { time: SharedValue<number>; accent: string; height: number }) {
  const slash = useAnimatedStyle(() => {
    const t = time.get();
    const p = ramp(t, 0.552, 0.59, true);
    const visible = t > 0.552 && t < 0.6 ? Math.sin(Math.PI * ramp(t, 0.552, 0.6)) : 0;
    return { opacity: visible, transform: [{ rotate: '-22deg' }, { translateX: mix(-430, 430, p) }] };
  });
  const letters = useAnimatedStyle(() => {
    const t = time.get();
    const arrive = ramp(t, 0.556, 0.574);
    const leave = ramp(t, 0.588, 0.606);
    return { opacity: arrive * (1 - leave), transform: [{ scale: mix(1.6, 1, arrive) + leave * 0.18 }, { rotate: '-8deg' }] };
  });
  const flash = useAnimatedStyle(() => {
    const t = time.get();
    return { opacity: t > 0.566 && t < 0.586 ? 0.16 * Math.sin(Math.PI * ramp(t, 0.566, 0.586)) : 0 };
  });
  return <View pointerEvents="none" style={[styles.versusStage, { height }]} accessible={false} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
    <Animated.View style={[styles.flash, flash]} />
    <Animated.View style={[styles.slash, slash]}><View style={[styles.slashGlow, { backgroundColor: accent }]} /><View style={styles.slashCore} /></Animated.View>
    <Animated.View style={[styles.versus, letters]}><Text style={styles.versusText}>VS</Text></Animated.View>
  </View>;
}

/** One fighter's page gradient; picking a fighter cross-fades them. Villager's is the base. */
function PageLayer({ fighter, index, weight }: { fighter: Fighter; index: number; weight: SharedValue<number> }) {
  const fade = useAnimatedStyle(() => ({ opacity: index === 0 ? 1 : weight.get() }));
  return <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, fade]}>
    <LinearGradient colors={fighter.page} style={StyleSheet.absoluteFill} />
  </Animated.View>;
}

export function FighterProfileTour({ autoPlay = true, previewTime, previewReducedMotion = false, renderer: _renderer, onRendererStatus, onComplete }: FighterProfileTourProps) {
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState({ width: 393, height: 852 });
  const [activeStep, setActiveStep] = useState<Step>(1);
  const [completed, setCompleted] = useState(false);
  const [selected, setSelected] = useState(0);
  const you = FIGHTERS[selected];
  const counters = you.counters.map(key => FIGHTERS[FIGHTER_INDEX[key]]);
  const systemReduced = useReducedMotion();
  const reduced = systemReduced || previewReducedMotion;
  const controlled = previewTime !== undefined;
  const routeFocused = useRef(false);
  const interrupted = useRef(false);
  const progress = useSharedValue(0);
  const playing = useSharedValue(autoPlay && !controlled);
  const reducedValue = useSharedValue(reduced ? 1 : 0);
  const sweep = useSharedValue(-1);
  const shimmerEnabled = useSharedValue(reduced ? 0 : 1);
  const dragStart = useSharedValue(0);
  const dragOrigin = useSharedValue(0);
  const gestureActive = useSharedValue(false);
  const resumeDelay = useSharedValue(0);
  const firstExpanded = useSharedValue(1);
  const secondExpanded = useSharedValue(1);
  const firstTarget = useSharedValue(1);
  const secondTarget = useSharedValue(1);
  const ringRow = useSharedValue(0);
  const villagerWeight = useSharedValue(1);
  const lucasWeight = useSharedValue(0);
  const puffWeight = useSharedValue(0);
  const rosaWeight = useSharedValue(0);
  const bowserWeight = useSharedValue(0);
  const weights = useMemo(() => [villagerWeight, lucasWeight, puffWeight, rosaWeight, bowserWeight], [bowserWeight, lucasWeight, puffWeight, rosaWeight, villagerWeight]);
  const stripManual = useSharedValue(-1);
  const stripStart = useSharedValue(0);
  const time = useDerivedValue(() => sceneTime(progress.get(), reducedValue.get() > 0));
  const available = Math.max(1, size.height - insets.top - insets.bottom);
  // Background always fills the device; content also fits compact phones without clipping its CTA.
  const scale = Math.min(size.width / WIDTH, available / 770);
  const stageHeight = available / scale + 44;
  const stageWidth = WIDTH * scale;
  const stageLeft = (size.width - stageWidth) / 2;

  useEffect(() => { reducedValue.set(reduced ? 1 : 0); shimmerEnabled.set(reduced ? 0 : 1); }, [reduced, reducedValue, shimmerEnabled]);
  useEffect(() => {
    cancelAnimation(progress);
    playing.set(autoPlay && !controlled);
    if (previewTime !== undefined) progress.set(clamp(previewTime / LOOP));
    return () => { playing.set(false); cancelAnimation(progress); cancelAnimation(sweep); };
  }, [autoPlay, controlled, playing, previewTime, progress, sweep]);
  useEffect(() => { onRendererStatus?.({ renderer: 'skia', ready: true, detail: 'Native layout, persistent PNG actor, RuntimeEffect commit shimmer' }); }, [onRendererStatus]);
  const clock = useFrameCallback(frame => {
    if (!playing.get()) return;
    const elapsed = Math.min(frame.timeSincePreviousFrame ?? 0, 48);
    if (resumeDelay.get() > 0) { resumeDelay.set(Math.max(0, resumeDelay.get() - elapsed)); return; }
    const delta = elapsed / (LOOP * 1000);
    progress.set((progress.get() + delta) % 1);
  }, false);
  const pauseForInterruption = useCallback(() => {
    interrupted.current = true;
    gestureActive.set(false);
    clock.setActive(false);
    cancelAnimation(progress);
    cancelAnimation(sweep);
  }, [clock, gestureActive, progress, sweep]);
  const restoreAfterInterruption = useCallback(() => {
    if (interrupted.current && !controlled) {
      settleToHold(progress, 0, false);
      playing.set(autoPlay);
      resumeDelay.set(autoPlay ? 2000 : 0);
    }
    interrupted.current = false;
    clock.setActive(routeFocused.current && autoPlay && !controlled && AppState.currentState === 'active');
  }, [autoPlay, clock, controlled, playing, progress, resumeDelay]);
  useFocusEffect(useCallback(() => {
    routeFocused.current = true;
    restoreAfterInterruption();
    return () => { routeFocused.current = false; pauseForInterruption(); };
  }, [pauseForInterruption, restoreAfterInterruption]));
  useEffect(() => {
    const subscription = AppState.addEventListener('change', state => {
      if (state !== 'active') pauseForInterruption();
      else if (routeFocused.current) restoreAfterInterruption();
    });
    return () => { subscription.remove(); clock.setActive(false); };
  }, [clock, pauseForInterruption, restoreAfterInterruption]);
  useAnimatedReaction(() => stepAt(time.get()), (next, previous) => { if (next !== previous) scheduleOnRN(setActiveStep, next); });

  const haptic = useCallback(() => { void Haptics.selectionAsync().catch(() => {}); }, []);
  const shimmer = useCallback(() => { if (!reduced) { sweep.set(-0.15); sweep.set(withTiming(1.2, { duration: 220, easing: Easing.linear, reduceMotion: ReduceMotion.Never })); } }, [reduced, sweep]);
  const commit = useCallback((kind: 'continue' | 'skip' | 'select') => {
    haptic(); shimmer(); playing.set(false); resumeDelay.set(0); clock.setActive(false);
    stripManual.set(-1);
    const raw = progress.get();
    const current = sceneTime(raw, false);
    if (raw >= 0.84) progress.set(current); // Same visible pose, forward manual coordinate.
    const currentStep = stepAt(sceneTime(current, reduced));
    if (currentStep === 3 && kind === 'continue') {
      progress.set(withSpring(0.72, { duration: 400, dampingRatio: 1, overshootClamping: true, reduceMotion: reduced ? ReduceMotion.Always : ReduceMotion.System }));
      setCompleted(true); onComplete?.(); return;
    }
    const target = kind === 'skip' || currentStep !== 1 ? 0.72 : 0.40;
    // Retarget the current pose; retaining the prior spring also retains its velocity.
    progress.set(withSpring(target, { duration: 400, dampingRatio: 1, overshootClamping: true, reduceMotion: reduced ? ReduceMotion.Always : ReduceMotion.System }));
  }, [clock, haptic, onComplete, playing, progress, reduced, resumeDelay, shimmer, stripManual]);
  /** Picking another card moves the ring and recolours the page; picking the chosen one starts. */
  const pick = useCallback((index: number) => {
    if (index === selected) { commit('select'); return; }
    haptic();
    playing.set(false); clock.setActive(false); resumeDelay.set(0);
    setSelected(index); setCompleted(false);
    const motion = reduced ? ReduceMotion.Always : ReduceMotion.System;
    ringRow.set(withSpring(index, { duration: 380, dampingRatio: 0.82, reduceMotion: motion }));
    weights.forEach((weight, fighter) => weight.set(withTiming(fighter === index ? 1 : 0, { duration: 420, easing: EASE_OUT, reduceMotion: motion })));
  }, [clock, commit, haptic, playing, reduced, resumeDelay, ringRow, selected, weights]);
  const toggle = useCallback((expanded: SharedValue<number>, target: SharedValue<number>) => {
    haptic(); playing.set(false); clock.setActive(false);
    if (progress.get() >= 0.84) progress.set(sceneTime(progress.get(), false));
    progress.set(withSpring(0.72, { duration: 400, dampingRatio: 1, overshootClamping: true, reduceMotion: reduced ? ReduceMotion.Always : ReduceMotion.System }));
    const next = target.get() > 0.5 ? 0 : 1;
    target.set(next);
    expanded.set(withTiming(next, { duration: 180, easing: EASE_OUT, reduceMotion: reduced ? ReduceMotion.Always : ReduceMotion.System }));
  }, [clock, haptic, playing, progress, reduced]);
  const dragCommitted = useCallback(() => { clock.setActive(false); setCompleted(false); haptic(); }, [clock, haptic]);
  const dragCancelled = useCallback(() => { clock.setActive(false); }, [clock]);
  // The demo glides the stat cards once while step 2 rests; a finger owns them after that.
  const stripOffset = useDerivedValue(() => {
    if (stripManual.get() >= 0) return stripManual.get();
    return reducedValue.get() > 0 ? 0 : STAT_SCROLL * ramp(time.get(), 0.405, 0.445, true);
  });
  const statsPan = useMemo(() => Gesture.Pan().maxPointers(1).activeOffsetX([-8, 8]).failOffsetY([-14, 14])
    .onStart(() => { stripStart.set(stripOffset.get()); stripManual.set(stripOffset.get()); playing.set(false); resumeDelay.set(0); })
    .onUpdate(event => {
      const raw = stripStart.get() - event.translationX;
      const edge = clamp(raw, 0, STAT_SCROLL);
      stripManual.set(edge + (raw - edge) / (1 + Math.abs(raw - edge) / 60));
    })
    .onEnd(event => {
      const projected = stripManual.get() - event.velocityX * 0.18;
      const snapped = clamp(Math.round(projected / (STAT_CARD + STAT_GAP)) * (STAT_CARD + STAT_GAP), 0, STAT_SCROLL);
      stripManual.set(withSpring(snapped, { duration: 420, dampingRatio: 0.9, velocity: -event.velocityX, reduceMotion: ReduceMotion.System }));
    }), [playing, resumeDelay, stripManual, stripOffset, stripStart]);
  const pan = useMemo(() => Gesture.Pan().maxPointers(1).activeOffsetX([-14, 14]).failOffsetY([-18, 18]).requireExternalGestureToFail(statsPan)
    .onStart(event => {
      dragOrigin.set(event.translationX);
      gestureActive.set(true); playing.set(false); resumeDelay.set(0); cancelAnimation(progress);
      const start = sceneTime(progress.get(), false); dragStart.set(start); progress.set(start);
    })
    .onUpdate(event => { progress.set(clamp(dragStart.get() - (event.translationX - dragOrigin.get()) / stageWidth * 0.72, 0, 0.72)); })
    .onEnd((event, success) => {
      if (!success) return;
      gestureActive.set(false);
      settleToHold(progress, clamp(-event.velocityX / stageWidth * 0.72, -3, 3));
      scheduleOnRN(dragCommitted);
    })
    .onFinalize((_event, success) => {
      // Failed taps never activated this pan and must leave their Pressable action untouched.
      if (!success && gestureActive.get()) {
        gestureActive.set(false);
        settleToHold(progress);
        scheduleOnRN(dragCancelled);
      }
    }), [dragCancelled, dragCommitted, dragOrigin, dragStart, gestureActive, playing, progress, resumeDelay, stageWidth, statsPan]);

  // The chosen fighter leaves its card: the clip grows from the card to the page, so the art
  // breaks out of its frame as it lifts, then the same art becomes the hero and later the badge.
  const fromRect = rowArtRect(you, selected);
  const heroRect = standIn(you, HERO_BOX);
  const pinnedRect = standIn(you, PINNED_BOX);
  const actorClip = useAnimatedStyle(() => {
    const t = time.get();
    const open = ramp(t, 0.12, 0.2, true);
    const card = { x: ROW.x, y: rowTop(ringRow.get()), width: ROW.width, height: ROW.height };
    const clip = mixRect(card, { x: 0, y: 0, width: WIDTH, height: stageHeight }, open);
    return { opacity: t >= 0.118 ? 1 : 0, left: clip.x, top: clip.y, width: clip.width, height: clip.height, borderRadius: mix(12, 0, open) };
  });
  const actorArt = useAnimatedStyle(() => {
    const t = time.get();
    const open = ramp(t, 0.12, 0.2, true);
    const clipX = mix(ROW.x, 0, open);
    const clipY = mix(rowTop(ringRow.get()), 0, open);
    const art = mixRect(mixRect(fromRect, heroRect, ramp(t, 0.12, 0.32, true)), pinnedRect, ramp(t, 0.47, 0.6, true));
    return { left: art.x - clipX, top: art.y - clipY, width: art.width, height: art.height };
  });
  const hideRowArt = useDerivedValue<number>(() => (time.get() >= 0.118 ? 1 : 0));
  // The chosen card's own surface morphs into the hero halo behind the fighter.
  const morph = useAnimatedStyle(() => {
    const t = time.get();
    const m = ramp(t, 0.12, 0.26, true);
    const shape = mixRect({ x: ROW.x, y: rowTop(ringRow.get()), width: ROW.width, height: ROW.height }, HALO, m);
    return {
      left: shape.x, top: shape.y, width: shape.width, height: shape.height, borderRadius: mix(12, HALO.width / 2, m),
      backgroundColor: interpolateColor(m, [0, 1], [SHADE_SELECTED, HALO_FILL]),
      opacity: 1 - ramp(t, 0.48, 0.535),
    };
  });
  const ring = useAnimatedStyle(() => ({
    opacity: 1 - ramp(time.get(), 0.12, 0.15),
    transform: [{ translateY: rowTop(ringRow.get()) - 1.5 }],
  }));
  const badge = useAnimatedStyle(() => {
    const p = ramp(time.get(), 0.6, 0.68);
    return { opacity: p, transform: [{ scale: 0.8 + 0.2 * p }] };
  });
  const dots = useAnimatedStyle(() => { const t = time.get(); return { transform: [{ translateX: mix(mix(0, 62, ramp(t, 0.12, 0.32, true)), 124, ramp(t, 0.47, 0.68, true)) }] }; });
  const ctaContent = useAnimatedStyle(() => {
    const t = time.get();
    const opacity = t < 0.575 ? 1 - ramp(t, 0.56, 0.573) : ramp(t, 0.58, 0.595);
    return { opacity };
  });
  const layout = useCallback((event: LayoutChangeEvent) => { const { width, height } = event.nativeEvent.layout; setSize(previous => previous.width === width && previous.height === height ? previous : { width, height }); }, []);

  return <View style={styles.root} onLayout={layout} testID="fighter-profile-tour">
    <StatusBar style="light" />
    {FIGHTERS.map((fighter, index) => <PageLayer key={fighter.key} fighter={fighter} index={index} weight={weights[index]} />)}
    <GestureDetector gesture={pan}>
      <Animated.View style={{ position: 'absolute', left: stageLeft, top: insets.top - 44 * scale, width: WIDTH, height: stageHeight, transformOrigin: [0, 0, 0], transform: [{ scale }] }}>
        <View style={styles.halo} />
        <View style={styles.topbar}><Text style={styles.stepCount} accessibilityLiveRegion="polite">{activeStep} of 3</Text><Pressable accessibilityRole="button" accessibilityLabel="Skip to matchups" onPress={() => commit('skip')} hitSlop={Math.max(0, (48 / scale - 44) / 2)} style={styles.skip}>{({ pressed }) => <><PressHighlight pressed={pressed} /><Text style={styles.skipText}>Skip</Text></>}</Pressable></View>
        <Animated.View pointerEvents="none" style={[styles.morph, morph]} />
        <View accessibilityElementsHidden={activeStep !== 1} importantForAccessibility={activeStep === 1 ? 'auto' : 'no-hide-descendants'} pointerEvents={activeStep === 1 ? 'auto' : 'none'}>
          <Heading word="Pick" rest="your fighter" time={time} step={1} />
          <View style={styles.roster}>
            {FIGHTERS.map((fighter, index) => <Row key={fighter.key} fighter={fighter} index={index} time={time} selected={index === selected} accent={you.accent}
              onPick={pick} interactive={activeStep === 1} hideArt={hideRowArt} />)}
          </View>
          <Animated.View pointerEvents="none" style={[styles.ring, ring]}><View style={styles.check}><Ionicons name="checkmark" size={18} color="#15318c" /></View></Animated.View>
        </View>
        <View accessibilityElementsHidden={activeStep !== 2} importantForAccessibility={activeStep === 2 ? 'auto' : 'no-hide-descendants'} pointerEvents={activeStep === 2 ? 'box-none' : 'none'}>
          <Heading word="Know" rest="your strengths" time={time} step={2} />
          <MotionLayer time={time} enter={[0.225, 0.25]} exit={[0.48, 0.51]} style={styles.heroNameWrap}><Text maxFontSizeMultiplier={1.15} numberOfLines={1} adjustsFontSizeToFit style={styles.heroName}>{you.name}</Text></MotionLayer>
          <MotionLayer time={time} enter={[0.265, 0.29]} exit={[0.49, 0.52]} style={styles.overview}>
            <View style={styles.sectionHeading}><View style={styles.dash} /><Text style={styles.sectionTitle}>Overview</Text></View>
            <View style={styles.tags}><View style={styles.gameTag}><Ionicons name={you.seriesIcon} size={19} color={you.accent} /><Text maxFontSizeMultiplier={1.15} numberOfLines={1} style={styles.tagText}>{you.series.toUpperCase()}</Text></View><View style={styles.rankTag}><Text style={[styles.rankLetter, { color: you.accent }]}>{you.tier}</Text><Text style={styles.tagText}>TIER</Text></View></View>
          </MotionLayer>
          <MotionLayer time={time} enter={[0.270, 0.295]} exit={[0.495, 0.525]} style={styles.descriptionWrap}><Text maxFontSizeMultiplier={1.15} style={styles.description}>{fighterSummary(you)}</Text></MotionLayer>
          <GestureDetector gesture={statsPan}>
            <View style={styles.stats} accessible accessibilityLabel={you.stats.map((stat, index) => `${STAT_LABELS[index]} ${stat.value}`).join(', ')} accessibilityHint="Swipe sideways for more stats">
              <StatStrip fighter={you} time={time} accent={you.accent} offset={stripOffset} />
            </View>
          </GestureDetector>
        </View>
        <View accessibilityElementsHidden={activeStep !== 3} importantForAccessibility={activeStep === 3 ? 'auto' : 'no-hide-descendants'} pointerEvents={activeStep === 3 ? 'auto' : 'none'}>
          <Heading word="Find" rest="your matchup" time={time} step={3} />
          <MotionLayer time={time} enter={[0.598, 0.623]} style={styles.matchIntro}>
            <View style={styles.sectionHeading}>
              <View style={styles.dash} /><Text maxFontSizeMultiplier={1.15} style={styles.sectionTitle}>Counter Matchups</Text>
              <View style={styles.legend} accessible accessibilityLabel={`Yellow bars are ${you.name}, coloured bars are the counter`}>
                <View style={[styles.legendDot, { backgroundColor: YELLOW }]} /><Text maxFontSizeMultiplier={1.15} style={styles.legendText}>You</Text>
                <View style={[styles.legendDot, styles.legendGap, { backgroundColor: you.accent }]} /><Text maxFontSizeMultiplier={1.15} style={styles.legendText}>Them</Text>
              </View>
            </View>
            <MotionLayer time={time} enter={[0.603, 0.628]} shift={6}><Text maxFontSizeMultiplier={1.15} style={styles.matchDescription}>These are the fighters {you.name} struggles against. Learn their strengths before your next match.</Text></MotionLayer>
          </MotionLayer>
          <View style={styles.matchList}>
            <MatchCard index={0} fighter={counters[0]} you={you} time={time} expanded={firstExpanded} expandedTarget={firstTarget} firstExpanded={firstExpanded} active={activeStep === 3} accent={you.accent} onToggle={() => toggle(firstExpanded, firstTarget)} />
            <MatchCard index={1} fighter={counters[1]} you={you} time={time} expanded={secondExpanded} expandedTarget={secondTarget} firstExpanded={firstExpanded} active={activeStep === 3} accent={you.accent} onToggle={() => toggle(secondExpanded, secondTarget)} />
          </View>
          <VersusSlash time={time} accent={you.accent} height={stageHeight} />
        </View>
        <Animated.View pointerEvents="none" style={[styles.badge, badge]} />
        <Animated.View pointerEvents="none" accessible accessibilityLabel={`${you.name}, your fighter`} style={[styles.actorClip, actorClip]}>
          <Animated.View style={[styles.actorArt, actorArt]}>
            <ExpoImage source={you.art} style={StyleSheet.absoluteFill} contentFit="fill" allowDownscaling={false} transition={0} accessible={false} accessibilityIgnoresInvertColors />
          </Animated.View>
        </Animated.View>
        <View style={[styles.footer, { top: stageHeight - 88 }]}>
          <View style={styles.dots} accessible accessibilityLabel={`Step ${activeStep} of 3`}><View style={styles.dot} /><View style={styles.dot} /><View style={styles.dot} /><Animated.View style={[styles.activeDot, dots]} /></View>
          <Pressable accessibilityRole="button" accessibilityLabel={activeStep === 3 ? completed ? `${you.name} is ready to train` : 'Finish onboarding and start training' : `Continue to step ${activeStep + 1}`} onPress={() => commit('continue')} pressRetentionOffset={16} hitSlop={Math.max(0, (48 / scale - 52) / 2)} style={styles.ctaTarget}>
            {({ pressed }) => <Animated.View style={[styles.continue, {
              transform: [{ scale: pressed && !reduced ? 0.97 : 1 }], opacity: pressed && reduced ? 0.9 : 1,
              transitionProperty: ['transform', 'opacity'], transitionDuration: 120, transitionTimingFunction: CSS_EASE_OUT,
            }]}>
              <Animated.View style={[styles.ctaContent, ctaContent]}><Text maxFontSizeMultiplier={1.15} style={styles.continueText}>{activeStep === 3 ? completed ? 'Ready to train' : 'Start training' : 'Continue'}</Text><Ionicons name="chevron-forward" size={22} color="#071f69" /></Animated.View>
              <CommitShimmer sweep={sweep} enabled={shimmerEnabled} />
            </Animated.View>}
          </Pressable>
        </View>
      </Animated.View>
    </GestureDetector>
  </View>;
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: FIGHTERS[0].page[1], overflow: 'hidden' },
  halo: { position: 'absolute', left: -13, top: 55, width: 420, height: 420, borderRadius: 210, backgroundColor: 'rgba(255,255,255,0.04)' },
  topbar: { position: 'absolute', left: 24, right: 16, top: 49, height: 38, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', zIndex: 9 },
  stepCount: { color: 'rgba(255,255,255,0.86)', fontSize: 15 }, skip: { minWidth: 60, minHeight: 44, alignItems: 'center', justifyContent: 'center', borderRadius: 10 }, skipText: { color: 'white', fontSize: 15 },
  heading: { position: 'absolute', left: 24, width: 345, flexDirection: 'row', alignItems: 'baseline', gap: 7 }, headingWord: { color: YELLOW, fontSize: 39, lineHeight: 47, fontWeight: '800', letterSpacing: -1 }, headingRest: { color: 'white', fontSize: 26, fontWeight: '800', letterSpacing: -0.65 },
  roster: { position: 'absolute', left: ROW.x, right: 20, top: ROSTER_TOP },
  row: { height: ROW.height, overflow: 'hidden', borderRadius: 12 },
  rowSurface: { borderRadius: 12, backgroundColor: SHADE },
  ring: { position: 'absolute', left: ROW.x, top: 0, width: ROW.width, height: ROW.height + 3, borderRadius: 13, borderWidth: 2.5, borderColor: YELLOW, zIndex: 7 },
  morph: { position: 'absolute' },
  rowCopy: { position: 'absolute', left: 27, top: 30, zIndex: 2 }, tier: { fontSize: 11, fontWeight: '600', letterSpacing: 0.2, marginBottom: 2 }, rowName: { color: 'white', fontSize: 21, fontWeight: '800', letterSpacing: -0.4 },
  rowArt: { position: 'absolute', left: ART_BOX.x - ROW.x, top: ART_BOX.top, width: 149, height: 112 },
  check: { position: 'absolute', right: 11.5, top: 35, width: 24, height: 24, borderRadius: 12, backgroundColor: YELLOW, alignItems: 'center', justifyContent: 'center' },
  actorClip: { position: 'absolute', overflow: 'hidden', zIndex: 5 }, actorArt: { position: 'absolute' },
  badge: { position: 'absolute', left: PINNED_BOX.x - 8, top: PINNED_BOX.y - 6, width: PINNED_BOX.width + 16, height: PINNED_BOX.width + 16, borderRadius: (PINNED_BOX.width + 16) / 2, borderWidth: 2, borderColor: YELLOW, backgroundColor: 'rgba(255,255,255,0.08)', zIndex: 4 },
  heroNameWrap: { position: 'absolute', left: 20, right: 20, top: 143 }, heroName: { textAlign: 'center', color: YELLOW, fontSize: 55, lineHeight: 55, fontWeight: '800', letterSpacing: -1.6 },
  overview: { position: 'absolute', left: 24, right: 24, top: 454 }, sectionHeading: { flexDirection: 'row', alignItems: 'center', gap: 12, height: 29 }, dash: { width: 22, height: 4, borderRadius: 2, backgroundColor: '#ffa900' }, sectionTitle: { fontSize: 23, fontWeight: '700', letterSpacing: -0.5, color: 'white' }, tags: { flexDirection: 'row', gap: 10, marginTop: 10 }, gameTag: { minWidth: 181, height: 30, paddingHorizontal: 14, flexDirection: 'row', gap: 9, alignItems: 'center', justifyContent: 'center', borderRadius: 8, backgroundColor: SHADE_SOFT }, rankTag: { width: 100, height: 30, flexDirection: 'row', gap: 9, alignItems: 'center', justifyContent: 'center', borderRadius: 8, backgroundColor: SHADE_SOFT }, tagText: { color: 'white', fontSize: 10, fontWeight: '700' }, rankLetter: { fontSize: 19, fontWeight: '800' }, descriptionWrap: { position: 'absolute', left: 24, right: 24, top: 536 }, description: { color: 'rgba(255,255,255,0.86)', fontSize: 14, lineHeight: 21 },
  stats: { position: 'absolute', left: 20, right: 20, top: 612, height: 100 },
  statsStrip: { flexDirection: 'row', gap: STAT_GAP },
  stat: { width: STAT_CARD, height: 100, paddingHorizontal: 14, paddingTop: 11, backgroundColor: SHADE, borderRadius: 9 }, statTop: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, statValue: { color: 'white', fontSize: 17, fontWeight: '800', fontVariant: ['tabular-nums'], letterSpacing: -0.3 }, statName: { color: 'white', fontSize: 12, marginTop: 8 }, statRail: { height: 6, backgroundColor: LIFT, borderRadius: 3, overflow: 'hidden', marginTop: 9 }, statFill: { width: '100%', height: 6, borderRadius: 3, backgroundColor: YELLOW },
  matchIntro: { position: 'absolute', left: 24, right: 24, top: 186 }, matchDescription: { color: 'rgba(255,255,255,0.86)', fontSize: 16, lineHeight: 24, marginTop: 17 },
  legend: { marginLeft: 'auto', flexDirection: 'row', alignItems: 'center', gap: 5 }, legendDot: { width: 8, height: 8, borderRadius: 4 }, legendGap: { marginLeft: 8 }, legendText: { color: 'rgba(255,255,255,0.78)', fontSize: 11, fontWeight: '600' },
  matchList: { position: 'absolute', left: 20, right: 20, top: 321 }, matchCard: { position: 'absolute', left: 0, right: 0, height: 192, transformOrigin: 'top center' }, matchBackground: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, borderRadius: 11, backgroundColor: SHADE }, matchHeaderArtClip: { position: 'absolute', left: 0, right: 0, top: 0, height: 64, overflow: 'hidden', borderTopLeftRadius: 11, borderTopRightRadius: 11 }, matchBodyClip: { position: 'absolute', left: 0, right: 0, top: 64, height: 128, overflow: 'hidden' }, matchBodyContent: { height: 128 }, matchLowerArt: { position: 'absolute' }, matchToggle: { height: 64, paddingLeft: 111, paddingRight: 30, paddingTop: 13, borderRadius: 11, zIndex: 2 }, matchName: { color: 'white', fontSize: 20, fontWeight: '800', letterSpacing: -0.3 }, matchTier: { fontSize: 13, marginTop: 4 }, chevron: { position: 'absolute', right: 16, top: 23 }, matchBody: { position: 'absolute', left: 111, right: 23, top: 0 }, metric: { marginBottom: 5 }, metricName: { color: 'white', fontSize: 12, lineHeight: 15, marginBottom: 3 }, metricRail: { height: 3, backgroundColor: LIFT, borderRadius: 1.5, overflow: 'hidden' }, yourRail: { marginTop: 2 }, metricFill: { width: '100%', height: 3, borderRadius: 1.5 },
  flash: { position: 'absolute', left: -40, right: -40, top: -60, bottom: -60, backgroundColor: 'white' },
  versusStage: { position: 'absolute', left: 0, top: 0, width: WIDTH },
  slash: { position: 'absolute', left: -60, top: 452, width: 520, height: 14, alignItems: 'center', justifyContent: 'center' },
  slashGlow: { position: 'absolute', left: 0, right: 0, height: 14, borderRadius: 7, opacity: 0.45 },
  slashCore: { position: 'absolute', left: 0, right: 0, height: 3, borderRadius: 1.5, backgroundColor: 'white' },
  versus: { position: 'absolute', left: 0, right: 0, top: 414, alignItems: 'center' },
  versusText: { color: YELLOW, fontSize: 78, lineHeight: 86, fontWeight: '900', fontStyle: 'italic', letterSpacing: -4, textShadowColor: 'rgba(7,31,105,0.55)', textShadowOffset: { width: 0, height: 4 }, textShadowRadius: 0 },
  footer: { position: 'absolute', left: 20, right: 20, zIndex: 8 }, dots: { alignSelf: 'center', flexDirection: 'row', gap: 8, height: 6, marginBottom: 16 }, dot: { width: 54, height: 6, borderRadius: 3, backgroundColor: 'rgba(255,255,255,0.13)' }, activeDot: { position: 'absolute', left: 0, width: 54, height: 6, borderRadius: 3, backgroundColor: YELLOW }, ctaTarget: { height: 52 }, ctaContent: { flexDirection: 'row', alignItems: 'center', gap: 10 }, continue: { width: '100%', height: 52, borderRadius: 14, overflow: 'hidden', backgroundColor: YELLOW, flexDirection: 'row', gap: 10, justifyContent: 'center', alignItems: 'center' }, continueText: { color: '#071f69', fontSize: 18, fontWeight: '800', letterSpacing: -0.4 },
});
