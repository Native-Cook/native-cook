import * as Haptics from 'expo-haptics';
import { BlurTargetView } from 'expo-blur';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  AccessibilityInfo,
  AppState,
  Platform,
  Pressable,
  ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  View,
  useWindowDimensions,
  type GestureResponderEvent,
  type NativeScrollEvent,
  type NativeSyntheticEvent,
} from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  interpolateColor,
  useAnimatedStyle,
  useFrameCallback,
  useSharedValue,
  type FrameCallback,
  type SharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle, Defs, LinearGradient as SvgLinearGradient, Path, Rect, Stop, Text as SvgText } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import {
  NIGHT_SLEEP_SCORE_DATA,
  adjustStageNightSleepScore,
  appearNightSleepScore,
  beginScrubNightSleepScore,
  cadenceNightSleepScore,
  cancelScrubNightSleepScore,
  clockTimeNightSleepScore,
  endScrubNightSleepScore,
  frameControllerNightSleepScore,
  initialNightSleepScoreState,
  segmentIndexAtNightSleepScore,
  setActiveNightSleepScore,
  setAutoPlayNightSleepScore,
  setQuietNightSleepScore,
  setVisibleNightSleepScore,
  selectStageNightSleepScore,
  updateScrubNightSleepScore,
  resetNightSleepScore,
  NIGHT_SLEEP_SCORE_CHASE,
  chaseNightSleepScore,
  nightAtNightSleepScore,
  smootherStepNightSleepScore,
  NIGHT_SLEEP_SCORE_READOUT_SWAP as READOUT_SWAP,
  type NightSleepScoreFrame,
  type NightSleepScoreStage,
  type NightSleepScoreState,
} from './motion';
import { GlassTabBar, MetricRail, ScrollEdgeBlur, type NightSleepScoreTab } from './Chrome';
import { NightSkyBackdrop, NightSkyOverlay, type NightSkyValues } from './NightSky';
import { NIGHT_SLEEP_SCORE_CARD, NIGHT_SLEEP_SCORE_COLORS } from './theme';

export const LOOP = 9;

const C = {
  bg: '#F1F0F6', ink: '#101113', secondary: '#8D8D92', divider: '#E4E3EB', card: 'rgba(246,246,255,0.96)',
  awake: '#EF806B', rem: '#32B4D5', core: '#128CF0', deep: '#4934A2', teal: '#13C9BB', orange: '#F67D65', blue: '#4E63F8',
};
const STAGES: readonly NightSleepScoreStage[] = NIGHT_SLEEP_SCORE_DATA.stages;
type Tab = NightSleepScoreTab;
type Semantic = Readonly<{ stageIndex: number | null; minute: number; manual: boolean }>;
const REST_FRAME: NightSleepScoreFrame = { cursorProgress: 0, selectionPresence: 0, segmentIndex: null, isRest: true, night: 0, reveal: 0, meteor: -1 };
const DAY = NIGHT_SLEEP_SCORE_COLORS.day;
const CH = NIGHT_SLEEP_SCORE_CARD.chart;
const NIGHT = NIGHT_SLEEP_SCORE_COLORS.night;
const STAGE_LABEL: Record<NightSleepScoreStage, string> = { awake: 'Awake', rem: 'REM', core: 'Core', deep: 'Deep' };

/** Sky values eased toward their targets after a touch; autoplay writes them directly (locked) so the loop stays exact. */
type SkyChase = { night: number; vNight: number; reveal: number; vReveal: number; phase: number; vPhase: number; locked: boolean; last: number };
const SKY_REST: SkyChase = { night: 0, vNight: 0, reveal: 0, vReveal: 0, phase: 0, vPhase: 0, locked: true, last: -1 };
function setIfChanged(value: SharedValue<number>, next: number) { 'worklet'; if (value.get() !== next) value.set(next); }
function updateSkyOnUI(chase: SharedValue<SkyChase>, values: NightSkyValues, frame: NightSleepScoreFrame, state: NightSleepScoreState, now: number): boolean {
  'worklet';
  const c = chase.get(); const dt = c.last < 0 ? 1 / 60 : Math.min(.1, Math.max(0, now - c.last));
  const manual = state.selectedMinute !== null; const K = NIGHT_SLEEP_SCORE_CHASE;
  let next: SkyChase;
  if (state.quiet || (!manual && c.locked)) {
    const inv = dt > 0 ? 1 / dt : 0;
    next = { night: frame.night, vNight: (frame.night - c.night) * inv, reveal: frame.reveal, vReveal: (frame.reveal - c.reveal) * inv,
      phase: frame.cursorProgress, vPhase: (frame.cursorProgress - c.phase) * inv, locked: true, last: now };
  } else {
    const [n, vn] = chaseNightSleepScore(c.night, c.vNight, frame.night, dt, K.night);
    const [r, vr] = chaseNightSleepScore(c.reveal, c.vReveal, frame.reveal, dt, K.reveal);
    const [p, vp] = chaseNightSleepScore(c.phase, c.vPhase, frame.cursorProgress, dt, K.phase);
    const settled = !manual && Math.abs(n - frame.night) < K.lockDistance && Math.abs(r - frame.reveal) < K.lockDistance
      && Math.abs(p - frame.cursorProgress) < K.lockDistance && Math.abs(vn) < K.lockVelocity && Math.abs(vr) < K.lockVelocity && Math.abs(vp) < K.lockVelocity;
    next = settled
      ? { night: frame.night, vNight: 0, reveal: frame.reveal, vReveal: 0, phase: frame.cursorProgress, vPhase: 0, locked: true, last: now }
      : { night: Math.min(1, Math.max(0, n)), vNight: vn, reveal: Math.min(1, Math.max(0, r)), vReveal: vr, phase: Math.min(1, Math.max(0, p)), vPhase: vp, locked: false, last: now };
  }
  chase.set(next);
  setIfChanged(values.night, next.night); setIfChanged(values.reveal, next.reveal); setIfChanged(values.phase, next.phase);
  setIfChanged(values.meteor, next.locked && !manual ? frame.meteor : -1);
  setIfChanged(values.cursor, frame.cursorProgress); setIfChanged(values.presence, frame.selectionPresence);
  setIfChanged(values.twinkle, state.quiet ? 0 : 1);
  if (next.night > .0005 && !state.quiet) values.clock.set(now % 600);
  return !next.locked || (next.night > .0005 && !state.quiet);
}

/** Card text that turns light while the card is a night sky. */
function NightText({ night, at, day, dark, style, children, ...rest }: React.ComponentProps<typeof Text> & { night: SharedValue<number>; at: number; day: string; dark: string }) {
  const tint = useAnimatedStyle(() => ({ color: interpolateColor(nightAtNightSleepScore(night.get(), at), [0, 1], [day, dark]) }));
  return <Animated.Text {...rest} style={[style, tint]}>{children}</Animated.Text>;
}
function NightBorder({ night, radius }: { night: SharedValue<number>; radius: number }) {
  const tint = useAnimatedStyle(() => ({ borderColor: interpolateColor(night.get(), [0, 1], [DAY.border, NIGHT.border]) }));
  return <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, { borderRadius: radius, borderWidth: 1 }, tint]} />;
}
function NightDivider({ night, at, style }: { night: SharedValue<number>; at: number; style: object }) {
  const tint = useAnimatedStyle(() => ({ backgroundColor: interpolateColor(nightAtNightSleepScore(night.get(), at), [0, 1], [DAY.divider, NIGHT.divider]) }));
  return <Animated.View style={[style, tint]} />;
}

function stageColor(stage: NightSleepScoreStage): string {
  'worklet';
  switch (stage) { case 'awake': return C.awake; case 'rem': return C.rem; case 'core': return C.core; case 'deep': return C.deep; }
}
function currentUISeconds(): number { 'worklet'; return performance.now() / 1000; }
function commitFrame(state: SharedValue<NightSleepScoreState>, frame: SharedValue<NightSleepScoreFrame>, at: number): NightSleepScoreFrame {
  'worklet';
  const result = frameControllerNightSleepScore(state.get(), at);
  state.set(result.state); frame.set(result.frame);
  return result.frame;
}
function publishSemanticKey(frame: NightSleepScoreFrame, state: NightSleepScoreState): string {
  'worklet';
  const index = frame.selectionPresence > 0.001 ? frame.segmentIndex : null;
  if (index === null) return 'rest';
  const manual = state.selectedMinute !== null;
  return manual ? `${index}:${Math.floor(state.selectedMinute ?? 0)}:1` : `${index}:0:0`;
}
function updateSemanticOnRN(
  index: number | null, minute: number, manual: boolean,
  readout: React.MutableRefObject<((value: Semantic) => void) | null>,
  legend: React.MutableRefObject<((value: Semantic) => void) | null>,
  chart: React.MutableRefObject<((value: Semantic) => void) | null>,
) {
  const value = { stageIndex: index, minute, manual };
  readout.current?.(value); legend.current?.(value); chart.current?.(value);
}
function setFrameActiveOnRN(ref: React.MutableRefObject<FrameCallback | null>, active: boolean) {
  ref.current?.setActive(active);
}
function wakeOnUI(
  state: SharedValue<NightSleepScoreState>, frame: SharedValue<NightSleepScoreFrame>,
  requested: SharedValue<number>, setActiveRN: (active: boolean) => void,
) {
  'worklet';
  const now = currentUISeconds();
  const current = state.get();
  const result = frameControllerNightSleepScore(current, now);
  state.set(result.state); frame.set(result.frame); requested.set(-1);
  // Publish one semantic frame even when the controller is otherwise idle.
  scheduleOnRN(setActiveRN, true);
}
function updateQuietOnUI(state: SharedValue<NightSleepScoreState>, frame: SharedValue<NightSleepScoreFrame>, quiet: boolean) {
  'worklet'; const now = currentUISeconds();
  const next = setQuietNightSleepScore(state.get(), quiet, now); state.set(next);
  const result = frameControllerNightSleepScore(next, now); state.set(result.state); frame.set(result.frame);
}
function setActiveOnUI(state: SharedValue<NightSleepScoreState>, frame: SharedValue<NightSleepScoreFrame>, active: boolean) {
  'worklet'; const now = currentUISeconds();
  const next = setActiveNightSleepScore(state.get(), active, now); state.set(next);
  const result = frameControllerNightSleepScore(next, now); state.set(result.state); frame.set(result.frame);
}
function changeTabOnUI(
  state: SharedValue<NightSleepScoreState>, frame: SharedValue<NightSleepScoreFrame>, visible: boolean,
) {
  'worklet'; const now = currentUISeconds();
  const next = setVisibleNightSleepScore(state.get(), visible, now, true); state.set(next);
  const result = frameControllerNightSleepScore(next, now); state.set(result.state); frame.set(result.frame);
}
function resetOnUI(state: SharedValue<NightSleepScoreState>, frame: SharedValue<NightSleepScoreFrame>) {
  'worklet'; const now = currentUISeconds();
  const next = resetNightSleepScore(state.get(), now);
  state.set(next); const result = frameControllerNightSleepScore(next, now); state.set(result.state); frame.set(result.frame);
}
function selectStageOnUI(
  state: SharedValue<NightSleepScoreState>, frame: SharedValue<NightSleepScoreFrame>, stage: NightSleepScoreStage,
) {
  'worklet'; const now = currentUISeconds();
  const next = selectStageNightSleepScore(state.get(), stage, now); state.set(next);
  const result = frameControllerNightSleepScore(next, now); state.set(result.state); frame.set(result.frame);
}
function adjustStageOnUI(
  state: SharedValue<NightSleepScoreState>, frame: SharedValue<NightSleepScoreFrame>, forward: boolean,
) {
  'worklet'; const now = currentUISeconds();
  const next = adjustStageNightSleepScore(state.get(), forward, now); state.set(next);
  const result = frameControllerNightSleepScore(next, now); state.set(result.state); frame.set(result.frame);
}

function ScoreRing({ scale, night }: { scale: number; night: SharedValue<number> }) {
  const radius = NIGHT_SLEEP_SCORE_CARD.ring.radius; const circumference = 2 * Math.PI * radius;
  const dayRing = useAnimatedStyle(() => ({ opacity: 1 - nightAtNightSleepScore(night.get(), NIGHT_SLEEP_SCORE_CARD.at.ring) }));
  return <View style={{ width: 112 * scale, height: 112 * scale }} accessible accessibilityLabel="Sleep score 84" accessibilityRole="image">
    <Animated.View style={[StyleSheet.absoluteFill, dayRing]}><DayScoreRing scale={scale} radius={radius} circumference={circumference} /></Animated.View>
    <NightText night={night} at={NIGHT_SLEEP_SCORE_CARD.at.ring} day={DAY.ink} dark={NIGHT.ink} style={[styles.scoreNumber, { top: 38.8 * scale, left: -1 * scale, fontSize: 28.5 * scale }]}>84</NightText>
  </View>;
}
function DayScoreRing({ scale, radius, circumference }: { scale: number; radius: number; circumference: number }) {
  return <Svg width={112 * scale} height={112 * scale} viewBox="0 0 112 112">
    <Circle cx="56" cy="56" r={radius} fill="none" stroke={C.divider} strokeWidth={1}/>
    <Circle cx="56" cy="56" r={radius} fill="none" stroke={C.teal} strokeWidth="16" strokeLinecap="round" strokeDasharray={`${circumference * .215} ${circumference * .785}`} strokeDashoffset={-circumference * .75} rotation="-90" origin="56,56"/>
    <Circle cx="56" cy="56" r={radius} fill="none" stroke={C.orange} strokeWidth="16" strokeLinecap="round" strokeDasharray={`${circumference * .165} ${circumference * .835}`} strokeDashoffset={-circumference * .025} rotation="-90" origin="56,56"/>
    <Circle cx="56" cy="56" r={radius} fill="none" stroke={C.blue} strokeWidth="16" strokeLinecap="round" strokeDasharray={`${circumference * .44} ${circumference * .56}`} strokeDashoffset={-circumference * .25} rotation="-90" origin="56,56"/>
  </Svg>;
}

/** Readiness gauge, measured on ref-07: centre (304, 65.3) in the card, centreline radius 44.5, 11 pt track ending 11° above level. */
const GAUGE = { cx: 304, cy: 65.3, r: 44.5, stroke: 11, from: .53, to: .97, lightFrom: .72, lightTo: .86, dot: .842, dotR: 3.4, numberBase: 5, numberSize: 30,
  track: '#E4E3EB', light: '#B9D6EE', blue: '#3A8DDE' } as const;
function ReadinessGauge({ scale }: { scale: number }) {
  const at = (turn: number) => [GAUGE.r * Math.cos(turn * Math.PI * 2), GAUGE.r * Math.sin(turn * Math.PI * 2)];
  const arc = (start: number, end: number) => { const a = at(start); const b = at(end); return `M${a[0]} ${a[1]} A${GAUGE.r} ${GAUGE.r} 0 0 1 ${b[0]} ${b[1]}`; };
  const dot = at(GAUGE.dot);
  return <Svg width={110 * scale} height={80 * scale} viewBox="-55 -57 110 80" accessibilityLabel="Readiness 7" accessibilityRole="image">
    <Path d={arc(GAUGE.from, GAUGE.to)} fill="none" stroke={GAUGE.track} strokeWidth={GAUGE.stroke} strokeLinecap="round"/>
    <Path d={arc(GAUGE.lightFrom, GAUGE.lightTo)} fill="none" stroke={GAUGE.light} strokeWidth={GAUGE.stroke} strokeLinecap="round"/>
    <Circle cx={dot[0]} cy={dot[1]} r={GAUGE.dotR} fill={GAUGE.blue}/>
    <SvgText x="0" y={GAUGE.numberBase} textAnchor="middle" fontSize={GAUGE.numberSize} fontWeight="700" fill={GAUGE.blue}>7</SvgText>
  </Svg>;
}

function semanticString(value: Semantic): string {
  if (value.stageIndex === null) return 'No stage selected';
  const stage = NIGHT_SLEEP_SCORE_DATA.segments[value.stageIndex].stage;
  const label = stage[0].toUpperCase() + stage.slice(1);
  return value.manual ? `Selected ${label}, ${value.minute} minutes into the overnight window` : `Night replay, ${label}`;
}

function SleepChart({
  scale, frame, state, publishSemantic, chartRef, setActiveRN, requestedActive, haptic, night,
}: {
  scale: number; frame: SharedValue<NightSleepScoreFrame>; state: SharedValue<NightSleepScoreState>; night: SharedValue<number>;
  publishSemantic: (index: number | null, minute: number, manual: boolean) => void;
  chartRef: React.MutableRefObject<((value: Semantic) => void) | null>;
  setActiveRN: (active: boolean) => void; requestedActive: SharedValue<number>; haptic: () => void;
}) {
  const owner = useSharedValue(-1);
  const completed = useSharedValue(false);
  const lastMinute = useSharedValue(-1);
  const staticChart = useMemo(() => {
    const levels = CH.levels;
    let elapsed = 0; const bars: { index: number; x: number; y: number; width: number; stage: NightSleepScoreStage }[] = [];
    const connectors: string[] = [];
    let previous: { x: number; y: number } | null = null;
    NIGHT_SLEEP_SCORE_DATA.segments.forEach((segment, index) => {
      const left = CH.width * elapsed / NIGHT_SLEEP_SCORE_DATA.windowMinutes;
      const y = levels[segment.stage];
      if (previous) connectors.push(`M${previous.x},${previous.y} L${left},${y}`);
      elapsed += segment.minutes;
      const right = CH.width * elapsed / NIGHT_SLEEP_SCORE_DATA.windowMinutes;
      bars.push({ index, x: left, y: y - CH.barHeight / 2, width: Math.max(1.5, right - left - CH.barGap), stage: segment.stage });
      previous = { x: right, y };
    });
    return { levels, bars, connector: connectors.join(' ') };
  }, []);
  const staticOpacity = useAnimatedStyle(() => ({ opacity: (1 - .35 * frame.get().selectionPresence) * (1 - .86 * nightAtNightSleepScore(night.get(), NIGHT_SLEEP_SCORE_CARD.at.chart)) }));
  const selectedBar = useAnimatedStyle(() => {
    const f = frame.get(); const i = f.segmentIndex;
    if (i === null || f.selectionPresence <= 0) return { opacity: 0, width: 0, backgroundColor: C.core, transform: [{ translateX: 0 }, { translateY: 0 }] };
    const bar = staticChart.bars[i];
    return { opacity: f.selectionPresence > 0 ? 1 - nightAtNightSleepScore(night.get(), NIGHT_SLEEP_SCORE_CARD.at.chart) : 0, width: bar.width * scale, backgroundColor: stageColor(bar.stage), transform: [{ translateX: bar.x * scale }, { translateY: bar.y * scale }] };
  });
  const cursorStyle = useAnimatedStyle(() => ({ opacity: .9 * frame.get().selectionPresence * (1 - nightAtNightSleepScore(night.get(), NIGHT_SLEEP_SCORE_CARD.at.chart)), transform: [{ translateX: frame.get().cursorProgress * CH.width * scale }] }));
  const haloStyle = useAnimatedStyle(() => ({ opacity: .12 * frame.get().selectionPresence * (1 - nightAtNightSleepScore(night.get(), NIGHT_SLEEP_SCORE_CARD.at.chart)), transform: [{ translateX: frame.get().cursorProgress * CH.width * scale }] }));
  const [semantic, setSemantic] = useState<Semantic>({ stageIndex: null, minute: 0, manual: false });
  useEffect(() => { chartRef.current = setSemantic; return () => { chartRef.current = null; }; }, [chartRef, setSemantic]);
  const chooseAction = useCallback((name: string) => {
    if (name === 'increment') scheduleOnUI(adjustStageOnUI, state, frame, true);
    else if (name === 'decrement') scheduleOnUI(adjustStageOnUI, state, frame, false);
    else {
      const stage = name.replace('Inspect ', '').toLowerCase() as NightSleepScoreStage;
      if (STAGES.includes(stage)) scheduleOnUI(selectStageOnUI, state, frame, stage);
    }
    haptic();
    scheduleOnUI(wakeOnUI, state, frame, requestedActive, setActiveRN);
  }, [frame, haptic, requestedActive, setActiveRN, state]);
  const gesture = useMemo(() => Gesture.Pan()
    .onTouchesDown((event) => {
      'worklet';
      const id = event.changedTouches[0]?.id ?? -1;
      if (owner.get() === -1) owner.set(id);
    })
    .minDistance(0)
    .maxPointers(1)
    .shouldCancelWhenOutside(false)
    .onStart((event) => {
      'worklet';
      if (owner.get() === -1) return;
      completed.set(false); lastMinute.set(-1);
      const x = Math.min(CH.width * scale, Math.max(0, event.x - CH.touchInset * scale));
      const minute = x / (CH.width * scale) * NIGHT_SLEEP_SCORE_DATA.windowMinutes;
      state.set(beginScrubNightSleepScore(state.get(), minute, currentUISeconds()));
      commitFrame(state, frame, currentUISeconds());
      wakeOnUI(state, frame, requestedActive, setActiveRN);
    })
    .onUpdate((event) => {
      'worklet';
      if (owner.get() === -1 || event.numberOfPointers !== 1) return;
      const x = Math.min(CH.width * scale, Math.max(0, event.x - CH.touchInset * scale));
      const minute = x / (CH.width * scale) * NIGHT_SLEEP_SCORE_DATA.windowMinutes;
      state.set(updateScrubNightSleepScore(state.get(), minute));
      commitFrame(state, frame, currentUISeconds());
      const quantized = Math.floor(minute);
      if (quantized !== lastMinute.get()) {
        lastMinute.set(quantized);
        const index = segmentIndexAtNightSleepScore(minute);
        scheduleOnRN(publishSemantic, index, quantized, true);
      }
      wakeOnUI(state, frame, requestedActive, setActiveRN);
    })
    .onEnd(() => {
      'worklet';
      if (owner.get() === -1) return;
      completed.set(true);
      const now = currentUISeconds(); state.set(endScrubNightSleepScore(state.get(), now));
      commitFrame(state, frame, now);
      const selected = state.get().selectedMinute;
      if (selected !== null) {
        const index = segmentIndexAtNightSleepScore(selected);
        scheduleOnRN(publishSemantic, index, Math.floor(selected), true);
      }
      scheduleOnRN(haptic);
      wakeOnUI(state, frame, requestedActive, setActiveRN);
    })
    .onFinalize(() => {
      'worklet';
      if (owner.get() !== -1 && !completed.get()) {
        const now = currentUISeconds(); state.set(cancelScrubNightSleepScore(state.get(), now));
        commitFrame(state, frame, now);
        const selected = state.get().selectedMinute;
        scheduleOnRN(publishSemantic, selected === null ? null : segmentIndexAtNightSleepScore(selected), selected === null ? 0 : Math.floor(selected), selected !== null);
        wakeOnUI(state, frame, requestedActive, setActiveRN);
      }
      owner.set(-1);
    }), [completed, frame, haptic, lastMinute, owner, publishSemantic, requestedActive, scale, setActiveRN, state]);

  const tap = useMemo(() => Gesture.Tap().maxDistance(8 * scale).onEnd((event, success) => {
    'worklet';
    if (!success) return;
    const x = Math.min(CH.width * scale, Math.max(0, event.x - CH.touchInset * scale));
    const now = currentUISeconds();
    const begun = beginScrubNightSleepScore(state.get(), x / (CH.width * scale) * NIGHT_SLEEP_SCORE_DATA.windowMinutes, now);
    state.set(endScrubNightSleepScore(begun, now));
    commitFrame(state, frame, now);
    wakeOnUI(state, frame, requestedActive, setActiveRN);
    scheduleOnRN(haptic);
  }), [frame, haptic, requestedActive, scale, setActiveRN, state]);
  const chartGesture = useMemo(() => Gesture.Exclusive(gesture, tap), [gesture, tap]);

  return (
    <>
    <View style={[styles.chartArea, { left: CH.left * scale, top: CH.top * scale, width: CH.width * scale, height: CH.height * scale }]} pointerEvents="none">
      <Animated.View style={[StyleSheet.absoluteFill, staticOpacity]}>
        <Svg width="100%" height="100%" viewBox={`0 0 ${CH.width} ${CH.height}`} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
          <Defs>
            <SvgLinearGradient id="nssAwake" x1="0" y1="0" x2="0" y2="1">
              <Stop offset="0" stopColor={C.awake} stopOpacity={1} /><Stop offset="1" stopColor={C.awake} stopOpacity={.35} />
            </SvgLinearGradient>
          </Defs>
          {CH.grid.map((y) => <Path key={`g${y}`} d={`M0 ${y} H${CH.width}`} stroke={CH.gridColor} strokeWidth={.9} strokeDasharray={CH.gridDash} />)}
          <Rect x={0} y={CH.levels.core - CH.barHeight / 2} width={CH.width} height={CH.barHeight} rx={CH.barRadius} fill={CH.coreTrack} />
          <Path d={staticChart.connector} fill="none" stroke={CH.connectorColor} strokeOpacity={CH.connectorOpacity} strokeWidth={CH.connectorWidth} />
          {staticChart.bars.map((bar) => <Rect key={bar.index} x={bar.x} y={bar.y} width={bar.width} height={CH.barHeight}
            rx={Math.min(CH.barRadius, bar.width / 2)} fill={bar.stage === 'awake' ? 'url(#nssAwake)' : C[bar.stage]} />)}
        </Svg>
      </Animated.View>
      <Animated.View pointerEvents="none" style={[styles.selectedSegment, { height: CH.barHeight * scale, borderRadius: CH.barRadius * scale }, selectedBar]} />
      <Animated.View pointerEvents="none" style={[styles.cursorHalo, haloStyle, { height: 80 * scale, width: 7 * scale }]} />
      <Animated.View pointerEvents="none" style={[styles.cursor, cursorStyle, { top: 1 * scale, height: 80 * scale, width: 1.5 * scale }]} />
    </View>
    <GestureDetector gesture={chartGesture}>
      <View
        style={[styles.chartGestureRegion, { left: (CH.left - CH.touchInset) * scale, top: (CH.top - 9) * scale, width: (CH.width + 2 * CH.touchInset) * scale, height: (CH.height + 16) * scale }]}
        accessible accessibilityRole="adjustable" accessibilityLabel="Sleep stage timeline, 23:18 to 06:44" accessibilityValue={{ text: semanticString(semantic) }}
        accessibilityActions={[
          { name: 'increment', label: 'Next sleep stage' }, { name: 'decrement', label: 'Previous sleep stage' },
          { name: 'Inspect Awake', label: 'Inspect Awake' }, { name: 'Inspect REM', label: 'Inspect REM' },
          { name: 'Inspect Core', label: 'Inspect Core' }, { name: 'Inspect Deep', label: 'Inspect Deep' },
        ]}
        onAccessibilityAction={(event) => chooseAction(event.nativeEvent.actionName)}
      />
    </GestureDetector>
    </>
  );
}

function Legend({
  scale, frame, onSelect, valueRef, adjust, night,
}: {
  scale: number; frame: SharedValue<NightSleepScoreFrame>; night: SharedValue<number>; onSelect: (stage: NightSleepScoreStage) => void;
  valueRef: React.MutableRefObject<((value: Semantic) => void) | null>; adjust: (forward: boolean) => void;
}) {
  const [semantic, setSemantic] = useState<Semantic>({ stageIndex: null, minute: 0, manual: false });
  const press = useRef<{ x: number; y: number; row: number } | null>(null);
  useEffect(() => { valueRef.current = setSemantic; return () => { valueRef.current = null; }; }, [setSemantic, valueRef]);
  const highlight = useAnimatedStyle(() => {
    const f = frame.get(); const index = f.selectionPresence > 0 ? f.segmentIndex : null;
    const stageIndex = index === null ? 0 : STAGES.indexOf(NIGHT_SLEEP_SCORE_DATA.segments[index].stage);
    return { opacity: .12 * f.selectionPresence, backgroundColor: interpolateColor(nightAtNightSleepScore(night.get(), NIGHT_SLEEP_SCORE_CARD.at.legend[stageIndex]), [0, 1], [DAY.highlight, NIGHT.highlight]), transform: [{ translateY: stageIndex * 22 * scale }] };
  });
  const doAction = (name: string) => {
    if (name === 'increment') adjust(true); else if (name === 'decrement') adjust(false);
    else { const stage = name.replace('Inspect ', '').toLowerCase() as NightSleepScoreStage; onSelect(stage); }
  };
  const onResponderGrant = (event: GestureResponderEvent) => {
    const { pageX, pageY, locationY } = event.nativeEvent;
    press.current = { x: pageX, y: pageY, row: Math.max(0, Math.min(3, Math.floor(locationY / (22 * scale)))) };
  };
  const onResponderMove = (event: GestureResponderEvent) => {
    const start = press.current;
    if (start && (event.nativeEvent.touches.length !== 1 || Math.hypot(event.nativeEvent.pageX - start.x, event.nativeEvent.pageY - start.y) > 8 * scale)) press.current = null;
  };
  const onResponderRelease = () => {
    const start = press.current; press.current = null;
    if (start) onSelect(STAGES[start.row]);
  };
  return (
    <View
      style={[styles.legend, { left: 16 * scale, top: 149.6 * scale, width: 135 * scale, height: 88 * scale }]}
      accessible accessibilityRole="adjustable" accessibilityLabel="Sleep stage legend"
      accessibilityValue={{ text: `Awake 14 minutes, REM 63 minutes, Core 333 minutes, Deep 36 minutes. ${semanticString(semantic)}` }}
      accessibilityActions={[{ name: 'increment', label: 'Next stage' }, { name: 'decrement', label: 'Previous stage' },
        { name: 'Inspect Awake', label: 'Inspect Awake' }, { name: 'Inspect REM', label: 'Inspect REM' },
        { name: 'Inspect Core', label: 'Inspect Core' }, { name: 'Inspect Deep', label: 'Inspect Deep' }]}
      onAccessibilityAction={(e) => doAction(e.nativeEvent.actionName)}
      onStartShouldSetResponder={() => true} onResponderGrant={onResponderGrant} onResponderMove={onResponderMove}
      onResponderRelease={onResponderRelease} onResponderTerminate={() => { press.current = null; }} onResponderTerminationRequest={() => true}
    >
      <Animated.View pointerEvents="none" style={[styles.legendHighlight, highlight, { height: 22 * scale, borderRadius: 11 * scale }]} />
      {STAGES.map((stage) => {
        const minutes = NIGHT_SLEEP_SCORE_DATA.segments.reduce((sum, segment) => sum + (segment.stage === stage ? segment.minutes : 0), 0);
        const duration = minutes < 60 ? `${minutes}m` : `${Math.floor(minutes / 60)}h ${minutes % 60}m`;
        return <View key={stage} pointerEvents="none" style={[styles.legendRow, { height: 22 * scale }]}>
          <NightText night={night} at={NIGHT_SLEEP_SCORE_CARD.at.legend[STAGES.indexOf(stage)]} day={DAY.stages[stage]} dark={NIGHT.stages[stage]} style={{ fontSize: 15 * scale, fontWeight: '600' }}>{STAGE_LABEL[stage]}</NightText>
          <NightText night={night} at={NIGHT_SLEEP_SCORE_CARD.at.legend[STAGES.indexOf(stage)]} day={DAY.ink} dark={NIGHT.ink} style={{ fontSize: 15 * scale, fontWeight: '700' }}>{duration}</NightText>
        </View>;
      })}
    </View>
  );
}

function Readout({
  scale, frame, onDone, registerRef, night,
}: {
  scale: number; frame: SharedValue<NightSleepScoreFrame>; night: SharedValue<number>;
  onDone: () => void; registerRef: React.MutableRefObject<((value: Semantic) => void) | null>;
}) {
  const [semantic, setSemantic] = useState<Semantic>({ stageIndex: null, minute: 0, manual: false });
  useEffect(() => { registerRef.current = setSemantic; return () => { registerRef.current = null; }; }, [registerRef, setSemantic]);
  // Sequential, not overlapping: one line fades out before the other fades in, so the two never read on top of each other.
  const summaryStyle = useAnimatedStyle(() => ({ opacity: smootherStepNightSleepScore((READOUT_SWAP - frame.get().selectionPresence) / READOUT_SWAP) }));
  const detailStyle = useAnimatedStyle(() => ({ opacity: smootherStepNightSleepScore((frame.get().selectionPresence - (1 - READOUT_SWAP)) / READOUT_SWAP) }));
  const stage = semantic.stageIndex === null ? null : NIGHT_SLEEP_SCORE_DATA.segments[semantic.stageIndex].stage;
  const title = stage === null ? '' : STAGE_LABEL[stage];
  return (
    <View style={[styles.readout, { left: 16 * scale, top: 262 * scale, width: 338 * scale, height: 52 * scale }]}>
      <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, summaryStyle]}>
        <NightText night={night} at={NIGHT_SLEEP_SCORE_CARD.at.readout} day={DAY.ink} dark={NIGHT.ink} style={{ fontSize: 15 * scale, lineHeight: 20 * scale }}>You only had a few interruptions last night which helped you get a high sleep score.</NightText>
      </Animated.View>
      <Animated.View style={[StyleSheet.absoluteFill, detailStyle]} pointerEvents="box-none">
        {stage !== null && <>
          <NightText night={night} at={NIGHT_SLEEP_SCORE_CARD.at.readout} day={DAY.ink} dark={NIGHT.ink} style={{ fontSize: 15 * scale, lineHeight: 20 * scale, fontWeight: '600' }}>
            {semantic.manual ? `${title} · ${clockTimeNightSleepScore(semantic.minute)}` : `Night replay · ${title}`}
          </NightText>
          <NightText night={night} at={NIGHT_SLEEP_SCORE_CARD.at.readoutSub} day={DAY.secondary} dark={NIGHT.secondary} style={{ fontSize: 13 * scale, lineHeight: 17 * scale }}>23:18–06:44 overnight window</NightText>
          {semantic.manual && <Pressable onPress={onDone} accessibilityRole="button" accessibilityLabel="Done" style={[styles.doneButton, { width: 44 * scale, height: 44 * scale, right: 0, top: 0 }]}><NightText night={night} at={NIGHT_SLEEP_SCORE_CARD.at.readout} day={DAY.accent} dark={NIGHT.accent} style={{ fontSize: 14 * scale, fontWeight: '600' }}>Done</NightText></Pressable>}
        </>}
      </Animated.View>
    </View>
  );
}
function Destination({ tab, scale }: { tab: Exclude<Tab, 'insights'>; scale: number }) {
  const title = tab === 'longevity' ? 'A gentler evening' : 'Sleep stages';
  const text = tab === 'longevity'
    ? ['Small routines can make room for a calmer night.', 'Keep your wake time steady when it feels comfortable.', 'Dim bright lights during your wind-down.', 'Leave a little quiet before bed.']
    : ['A quick guide to the colors in your night.', 'Awake: 14 minutes marked in coral.', 'REM: 1 hour 3 minutes marked in turquoise.', 'Core: 5 hours 33 minutes marked in blue.', 'Deep: 36 minutes marked in violet.'];
  return <View style={[styles.destination, { marginHorizontal: 16 * scale, marginTop: 46 * scale, padding: 24 * scale, borderRadius: 28 * scale }]}>
    <Text style={{ fontSize: 28 * scale, letterSpacing: -.7 * scale, color: C.ink, fontWeight: '700', marginBottom: 18 * scale }}>{title}</Text>
    {text.map((line, i) => <Text key={line} style={{ color: C.secondary, fontSize: 17 * scale, lineHeight: 24 * scale, backgroundColor: i ? 'rgba(255,255,255,.72)' : 'transparent', borderRadius: 18 * scale, padding: i ? 16 * scale : 0, marginBottom: i === text.length - 1 ? 0 : 18 * scale }}>{line}</Text>)}
  </View>;
}

export function NightSleepScore({ autoPlay = false }: { autoPlay?: boolean }) {
  const insets = useSafeAreaInsets();
  const { width: screenWidth, height: screenHeight } = useWindowDimensions();
  const scale = screenWidth / 402;
  const state = useSharedValue<NightSleepScoreState>(initialNightSleepScoreState());
  const frame = useSharedValue<NightSleepScoreFrame>(REST_FRAME);
  const lastSemanticKey = useSharedValue('wake');
  const requestedActive = useSharedValue(-1);
  const skyChase = useSharedValue<SkyChase>(SKY_REST);
  const skyNight = useSharedValue(0); const skyReveal = useSharedValue(0); const skyPhase = useSharedValue(0);
  const skyMeteor = useSharedValue(-1); const skyClock = useSharedValue(0); const skyTwinkle = useSharedValue(1);
  const skyCursor = useSharedValue(0); const skyPresence = useSharedValue(0);
  const sky = useMemo<NightSkyValues>(() => ({ night: skyNight, reveal: skyReveal, phase: skyPhase, meteor: skyMeteor, clock: skyClock,
    twinkle: skyTwinkle, cursor: skyCursor, presence: skyPresence }), [skyClock, skyCursor, skyMeteor, skyNight, skyPhase, skyPresence, skyReveal, skyTwinkle]);
  const readoutRef = useRef<((value: Semantic) => void) | null>(null);
  const legendRef = useRef<((value: Semantic) => void) | null>(null);
  const chartRef = useRef<((value: Semantic) => void) | null>(null);
  const frameRef = useRef<FrameCallback | null>(null);
  const [tab, setTab] = useState<Tab>('insights');
  const [screenReader, setScreenReader] = useState<boolean | null>(null);
  const [reducedMotion, setReducedMotion] = useState<boolean | null>(null);
  const [chartVisible, setChartVisible] = useState(true);
  const feedBlurTargetRef = useRef<View>(null);
  const quiet = reducedMotion !== false || screenReader !== false;

  const setFrameActiveRN = useCallback((active: boolean) => setFrameActiveOnRN(frameRef, active), []);
  const publishSemantic = useCallback((index: number | null, minute: number, manual: boolean) => {
    updateSemanticOnRN(index, minute, manual, readoutRef, legendRef, chartRef);
  }, []);
  const triggerHaptic = useCallback(() => { if (Platform.OS !== 'web') Haptics.selectionAsync().catch(() => {}); }, []);

  useEffect(() => {
    let mounted = true;
    AccessibilityInfo.isReduceMotionEnabled().then((enabled) => { if (mounted) setReducedMotion(enabled); });
    AccessibilityInfo.isScreenReaderEnabled().then((enabled) => { if (mounted) setScreenReader(enabled); });
    const reduce = AccessibilityInfo.addEventListener('reduceMotionChanged', setReducedMotion);
    const reader = AccessibilityInfo.addEventListener('screenReaderChanged', setScreenReader);
    return () => { mounted = false; reduce.remove(); reader.remove(); };
  }, []);
  useEffect(() => {
    scheduleOnUI((shared: SharedValue<NightSleepScoreState>, frameValue: SharedValue<NightSleepScoreFrame>, enabled: boolean, isQuiet: boolean) => {
      'worklet'; const now = currentUISeconds();
      let next = appearNightSleepScore(shared.get(), enabled, isQuiet, now);
      if (next.autoPlay !== enabled) next = setAutoPlayNightSleepScore(next, enabled, now);
      if (next.quiet !== isQuiet) next = setQuietNightSleepScore(next, isQuiet, now);
      shared.set(next); const sample = frameControllerNightSleepScore(next, now); shared.set(sample.state); frameValue.set(sample.frame);
      scheduleOnRN(setFrameActiveRN, cadenceNightSleepScore(sample.state, now) === 'animation');
    }, state, frame, autoPlay, quiet);
  }, [autoPlay, frame, quiet, setFrameActiveRN, state]);
  useEffect(() => {
    const subscription = AppState.addEventListener('change', (status) => {
      const active = status === 'active';
      scheduleOnUI(setActiveOnUI, state, frame, active);
      if (active) scheduleOnUI(wakeOnUI, state, frame, requestedActive, setFrameActiveRN);
      else setFrameActiveRN(false);
    });
    return () => subscription.remove();
  }, [frame, requestedActive, setFrameActiveRN, state]);
  useEffect(() => {
    scheduleOnUI(updateQuietOnUI, state, frame, quiet);
    scheduleOnUI(wakeOnUI, state, frame, requestedActive, setFrameActiveRN);
  }, [frame, quiet, requestedActive, setFrameActiveRN, state]);
  useEffect(() => {
    scheduleOnUI((shared: SharedValue<NightSleepScoreState>, frameValue: SharedValue<NightSleepScoreFrame>, enabled: boolean) => {
      'worklet'; const now = currentUISeconds(); const next = setAutoPlayNightSleepScore(shared.get(), enabled, now); shared.set(next);
      const sample = frameControllerNightSleepScore(next, now); shared.set(sample.state); frameValue.set(sample.frame);
    }, state, frame, autoPlay);
    scheduleOnUI(wakeOnUI, state, frame, requestedActive, setFrameActiveRN);
  }, [autoPlay, frame, requestedActive, setFrameActiveRN, state]);

  const frameCallback = useFrameCallback(useCallback(() => {
    'worklet';
    const now = currentUISeconds();
    const sample = frameControllerNightSleepScore(state.get(), now);
    state.set(sample.state); frame.set(sample.frame);
    const semanticKey = publishSemanticKey(sample.frame, sample.state);
    if (semanticKey !== lastSemanticKey.get()) {
      lastSemanticKey.set(semanticKey);
      const index = sample.frame.selectionPresence > .001 ? sample.frame.segmentIndex : null;
      const manual = index !== null && sample.state.selectedMinute !== null;
      const minute = manual ? Math.floor(sample.state.selectedMinute ?? 0) : 0;
      scheduleOnRN(publishSemantic, index, minute, manual);
    }
    const skyMoving = updateSkyOnUI(skyChase, sky, sample.frame, sample.state, now);
    const shouldRun = cadenceNightSleepScore(sample.state, now) === 'animation' || skyMoving;
    if (requestedActive.get() !== Number(shouldRun)) {
      requestedActive.set(Number(shouldRun));
      scheduleOnRN(setFrameActiveRN, shouldRun);
    }
  }, [frame, lastSemanticKey, publishSemantic, requestedActive, setFrameActiveRN, sky, skyChase, state]), false);
  useEffect(() => {
    frameRef.current = frameCallback;
    frameCallback.setActive(false);
    return () => { frameCallback.setActive(false); frameRef.current = null; };
  }, [frameCallback]);

  const selectStage = useCallback((stage: NightSleepScoreStage) => {
    scheduleOnUI(selectStageOnUI, state, frame, stage);
    scheduleOnUI(wakeOnUI, state, frame, requestedActive, setFrameActiveRN);
    triggerHaptic();
  }, [frame, requestedActive, setFrameActiveRN, state, triggerHaptic]);
  const adjustStage = useCallback((forward: boolean) => {
    scheduleOnUI(adjustStageOnUI, state, frame, forward);
    scheduleOnUI(wakeOnUI, state, frame, requestedActive, setFrameActiveRN);
    triggerHaptic();
  }, [frame, requestedActive, setFrameActiveRN, state, triggerHaptic]);
  const reset = useCallback(() => {
    scheduleOnUI(resetOnUI, state, frame);
    scheduleOnRN(publishSemantic, null, 0, false);
    scheduleOnUI(wakeOnUI, state, frame, requestedActive, setFrameActiveRN);
    triggerHaptic();
  }, [frame, publishSemantic, requestedActive, setFrameActiveRN, state, triggerHaptic]);
  const selectTab = useCallback((next: Tab) => {
    if (next === tab) return;
    setTab(next);
    setChartVisible(next === 'insights');
    scheduleOnUI(changeTabOnUI, state, frame, next === 'insights');
    scheduleOnRN(publishSemantic, null, 0, false);
    scheduleOnUI(wakeOnUI, state, frame, requestedActive, setFrameActiveRN);
    triggerHaptic();
  }, [frame, publishSemantic, requestedActive, setFrameActiveRN, state, tab, triggerHaptic]);
  const onScroll = useCallback((event: NativeSyntheticEvent<NativeScrollEvent>) => {
    const offset = event.nativeEvent.contentOffset.y;
    const chartTop = insets.top + 385 * scale;
    const chartBottom = insets.top + 467 * scale;
    const visible = chartBottom - offset > insets.top && chartTop - offset < screenHeight - 105 * scale;
    if (visible !== chartVisible) {
      setChartVisible(visible);
      scheduleOnUI((shared: SharedValue<NightSleepScoreState>, frameValue: SharedValue<NightSleepScoreFrame>, shown: boolean) => {
        'worklet'; const now = currentUISeconds(); const next = setVisibleNightSleepScore(shared.get(), shown, now); shared.set(next);
        const sample = frameControllerNightSleepScore(next, now); shared.set(sample.state); frameValue.set(sample.frame);
      }, state, frame, tab === 'insights' && visible);
      scheduleOnUI(wakeOnUI, state, frame, requestedActive, setFrameActiveRN);
    }
  }, [chartVisible, frame, insets.top, requestedActive, scale, screenHeight, setFrameActiveRN, state, tab]);

  const readinessText = 'Your training load has been lower than your normal level for the past few days and all your other metrics are in good shape. It looks like you’re ready for a demanding day.';
  const scrollContentHeight = screenHeight + 180 * scale;
  return (
    <View style={styles.root}>
      <StatusBar barStyle="dark-content" />
      <View pointerEvents="none" style={[styles.topScrim, { height: insets.top, backgroundColor: C.bg }]} />
      <BlurTargetView ref={feedBlurTargetRef} collapsable={false} style={StyleSheet.absoluteFill}>
        <ScrollView
          key={tab}
          style={StyleSheet.absoluteFill}
          contentContainerStyle={{ width: screenWidth, minHeight: scrollContentHeight, paddingTop: insets.top, paddingBottom: 112 * scale }}
          onScroll={onScroll} scrollEventThrottle={100} showsVerticalScrollIndicator={false}
        >
          <MetricRail scale={scale} />
          {tab === 'insights' ? <>
          <View style={{ height: 75 * scale, paddingTop: 27.6 * scale, paddingHorizontal: 32 * scale }}>
            <Text numberOfLines={1} style={{ color: C.ink, fontSize: 34 * scale, lineHeight: 41 * scale, letterSpacing: -.45 * scale, fontWeight: '700' }}>A Good Night’s Sleep</Text>
          </View>
          <Text style={{ width: 338 * scale, height: 84 * scale, marginTop: -3 * scale, marginHorizontal: 32 * scale, color: C.secondary, fontSize: 22 * scale, lineHeight: 28 * scale }}>
            You got about an hour more sleep last night than the night before, which helped lift your sleep score.
          </Text>
          <View style={[styles.sleepCard, { width: 370 * scale, height: 317 * scale, marginTop: 15 * scale, marginHorizontal: 16 * scale, borderRadius: 28 * scale }]}>
            <View pointerEvents="none" style={[StyleSheet.absoluteFill, { borderRadius: NIGHT_SLEEP_SCORE_CARD.radius * scale, overflow: 'hidden' }]}>
              <NightSkyBackdrop scale={scale} values={sky} />
            </View>
            <NightBorder night={skyNight} radius={NIGHT_SLEEP_SCORE_CARD.radius * scale} />
            <View style={{ position: 'absolute', left: 16 * scale, top: 18 * scale }}>
              <NightText night={skyNight} at={NIGHT_SLEEP_SCORE_CARD.at.label} day={DAY.secondary} dark={NIGHT.secondary} style={{ fontSize: 15 * scale, marginTop: -.7 * scale }}>Sleep</NightText>
              <NightText night={skyNight} at={NIGHT_SLEEP_SCORE_CARD.at.title} day={DAY.ink} dark={NIGHT.ink} style={{ fontSize: 19 * scale, lineHeight: 24 * scale, fontWeight: '700', marginTop: 1.4 * scale }}>High Score</NightText>
              <NightText night={skyNight} at={NIGHT_SLEEP_SCORE_CARD.at.duration} day={DAY.secondary} dark={NIGHT.secondary} style={{ fontSize: 15 * scale, marginTop: 13 * scale }}>Duration</NightText>
              <NightText night={skyNight} at={NIGHT_SLEEP_SCORE_CARD.at.durationValue} day={DAY.ink} dark={NIGHT.ink} style={{ fontSize: 15 * scale, fontWeight: '600', marginTop: 2 * scale }}>7h 12m</NightText>
            </View>
            <View style={{ position: 'absolute', left: (NIGHT_SLEEP_SCORE_CARD.ring.cx - 56) * scale, top: (NIGHT_SLEEP_SCORE_CARD.ring.cy - 56) * scale }}><ScoreRing scale={scale} night={skyNight} /></View>
            <NightDivider night={skyNight} at={NIGHT_SLEEP_SCORE_CARD.at.divider1} style={[styles.divider, { left: 16 * scale, top: 134 * scale, width: 338 * scale, height: Math.max(1, scale) }]} />
            <Legend scale={scale} frame={frame} onSelect={selectStage} valueRef={legendRef} adjust={adjustStage} night={skyNight} />
            <SleepChart scale={scale} frame={frame} state={state} publishSemantic={publishSemantic} chartRef={chartRef} setActiveRN={setFrameActiveRN} requestedActive={requestedActive} haptic={triggerHaptic} night={skyNight} />
            <View pointerEvents="none" style={StyleSheet.absoluteFill}><NightSkyOverlay scale={scale} values={sky} /></View>
            <NightDivider night={skyNight} at={NIGHT_SLEEP_SCORE_CARD.at.divider2} style={[styles.divider, { left: 16 * scale, top: 247 * scale, width: 338 * scale, height: Math.max(1, scale) }]} />
            <Readout scale={scale} frame={frame} onDone={reset} registerRef={readoutRef} night={skyNight} />
          </View>
          <View style={[styles.readinessCard, { width: 370 * scale, height: 290 * scale, marginTop: 11 * scale, marginHorizontal: 16 * scale, borderRadius: 28 * scale }]}>
            <View pointerEvents="none" style={[StyleSheet.absoluteFill, { borderRadius: 28 * scale, borderWidth: 1, borderColor: 'rgba(255,255,255,.75)' }]} />
            <Text style={{ position: 'absolute', left: 16 * scale, top: 16.3 * scale, color: C.secondary, fontSize: 15 * scale }}>Readiness</Text>
            <Text style={{ position: 'absolute', left: 16 * scale, top: 38 * scale, color: C.ink, fontSize: 19 * scale, fontWeight: '700' }}>Ready</Text>
            <View style={{ position: 'absolute', left: (GAUGE.cx - 55) * scale, top: (GAUGE.cy - 57) * scale }}><ReadinessGauge scale={scale} /></View>
            <View style={[styles.divider, { left: 16 * scale, top: 88 * scale, width: 338 * scale, height: Math.max(1, scale) }]} />
            <Text style={{ position: 'absolute', left: 16 * scale, right: 16 * scale, top: 103 * scale, color: C.ink, fontSize: 15 * scale, lineHeight: 20 * scale }}>{readinessText}</Text>
          </View>
          </> : <Destination tab={tab} scale={scale} />}
        </ScrollView>
      </BlurTargetView>
      <ScrollEdgeBlur scale={scale} />
      <GlassTabBar active={tab} scale={scale} still={quiet} blurTarget={feedBlurTargetRef} onSelect={selectTab} />
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: C.bg },
  topScrim: { position: 'absolute', zIndex: 3, top: 0, left: 0, right: 0 },
  sleepCard: { position: 'relative', backgroundColor: C.card, shadowColor: '#2F5BD6', shadowOpacity: .2, shadowRadius: 18, shadowOffset: { width: 0, height: 4 }, elevation: 3 },
  readinessCard: { position: 'relative', backgroundColor: C.card, shadowColor: '#2F5BD6', shadowOpacity: .2, shadowRadius: 18, shadowOffset: { width: 0, height: 4 }, elevation: 3 },
  divider: { position: 'absolute', backgroundColor: C.divider },
  chartArea: { position: 'absolute', overflow: 'hidden' },
  selectedSegment: { position: 'absolute', left: 0, top: 0 },
  cursorHalo: { position: 'absolute', left: 0, top: 1, borderRadius: 4, backgroundColor: C.core },
  cursor: { position: 'absolute', left: 0, backgroundColor: C.ink, borderRadius: 1 },
  chartGestureRegion: { position: 'absolute', backgroundColor: 'transparent' },
  legend: { position: 'absolute' },
  legendHighlight: { position: 'absolute', left: -6, right: -4, top: 0, backgroundColor: C.core },
  legendRow: { width: '100%', flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  readout: { position: 'absolute' },
  scoreNumber: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontWeight: '700' },
  doneButton: { position: 'absolute', justifyContent: 'center', alignItems: 'flex-end' },
  destination: { backgroundColor: C.card, borderWidth: 1, borderColor: 'rgba(255,255,255,.75)' },
});
