// Flip Focus Badge — the screen: a focus timer whose reward is an object. Flip the phone (or drag the badge) past 90° and
// the badge shows its back, which is the running session's clock; flip back to finish. If the session reached an hour the
// numeral presses into the glass behind the plate and punches out as the new number. The page around it (the line and its
// labels, the hint, the sheet's total) is a function of the same state, and the sheet only rolls the digits that change.
//
//   <FlipFocusScreen totalSeconds={37440} onStop={(s) => log(s)} />
//
// With `autoPlay` it plays the pin's own loop (LOOP in ffb-motion.ts) until a finger touches; 2 s after the last touch it
// eases back and plays again.
import { useFonts } from 'expo-font';
import { memo, useCallback, useEffect, useMemo, useState } from 'react';
import { Platform, StyleSheet, useWindowDimensions, View } from 'react-native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, {
  FadeIn,
  useAnimatedReaction,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withDelay,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Canvas, Fill, Group, LinearGradient, Rect, RoundedRect, rect, rrect, Shader, Skia, vec } from '@shopify/react-native-skia';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { FONTS, Line, RollTotal, Crossfade, Segmented, TopRow } from './FlipFocusParts';
import { NumeralBadge } from './NumeralBadge';
import { hex3 } from './ffb-geometry';
import { haptic, silence } from './ffb-haptics';
import { GESTURE, type Look, REST, SESSION_SECS } from './ffb-motion';
import { BACKDROP_SKSL } from './ffb-shaders';
import { COPY, comp, makeLayout, ref, SCENE, TYPE } from './ffb-theme';

const BACKDROP_FX = Skia.RuntimeEffect.Make(BACKDROP_SKSL);
const P3 = Platform.OS === 'ios' ? 1 : 0;
const GROUND: [number, number, number] = [hex3(ref.color.pageTop)[0], hex3(ref.color.pageMid)[0], hex3(ref.color.pageRight)[0]];

export type FlipFocusScreenProps = {
  /** the day's total so far, seconds (the pin: 10h 24m) */
  totalSeconds?: number;
  /** controlled: whether a session runs (the badge shows its back); `defaultRunning` starts uncontrolled */
  running?: boolean;
  defaultRunning?: boolean;
  onStart?: () => void;
  /** a session of at least `minSessionSeconds` ended: its length, seconds (session time, i.e. after `timeScale`) */
  onStop?: (seconds: number) => void;
  /** the day's hours crossed a whole number during a session */
  onHour?: (hours: number) => void;
  minSessionSeconds?: number;
  /** session seconds per real second (1 = real time; the demo runs fast so a session is visible) */
  timeScale?: number;
  source?: 'auto' | 'device' | 'drag';
  haptics?: boolean;
  autoPlay?: boolean;
};

const RANGES = [
  { date: COPY.date, total: '' },
  { date: 'Mar 2 – Mar 8', total: '47h 05m' },
  { date: 'March', total: '182h 40m' },
  { date: '2026', total: '1,206h' },
];

function fmt(secs: number) {
  const s = Math.max(0, Math.round(secs));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  return `${h}h ${String(m).padStart(2, '0')}m`;
}

export function FlipFocusScreen(props: FlipFocusScreenProps) {
  const [loaded] = useFonts(FONTS);
  return (
    <GestureHandlerRootView style={styles.root}>
      <Page {...props} fontsReady={loaded} />
    </GestureHandlerRootView>
  );
}

const Page = memo(function Page({
  totalSeconds = 37440,
  running: runningProp,
  defaultRunning = false,
  onStart,
  onStop,
  onHour,
  minSessionSeconds = GESTURE.minSession,
  timeScale = 1,
  source = 'auto',
  haptics = true,
  autoPlay = false,
  fontsReady,
}: FlipFocusScreenProps & { fontsReady: boolean }) {
  const { width: W, height: H } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduce = useReducedMotionPreference();
  const L = useMemo(() => makeLayout(W, H, insets.top, insets.bottom), [W, H, insets.top, insets.bottom]);
  const S = L.S;
  const cue = haptics ? haptic : silence;

  // ---- state ------------------------------------------------------------------------------------------------------
  const lookV = useSharedValue<Look>(REST);
  const playingV = useSharedValue(autoPlay && !reduce);
  const totalSecs = useSharedValue(totalSeconds);
  const elapsed = useSharedValue(0);
  const runningV = useSharedValue(defaultRunning);
  const clockV = useSharedValue(0);
  const orbitV = useSharedValue(totalSeconds / 3600);
  const hintLive = useSharedValue(defaultRunning ? 1 : 0);
  const labelLive = useSharedValue(0);
  const rollLive = useSharedValue(1);
  const hoursSeen = useSharedValue(Math.floor(totalSeconds / 3600));
  const [playing, setPlaying] = useState(autoPlay && !reduce);
  const [valueN, setValueN] = useState(Math.floor(totalSeconds / 3600));
  const [lab, setLab] = useState({ a: Math.floor(totalSeconds / 3600), b: Math.floor(totalSeconds / 3600) });
  const [roll, setRoll] = useState({ from: fmt(totalSeconds), to: fmt(totalSeconds) });
  const [range, setRange] = useState(0);
  const [runningJS, setRunningJS] = useState(defaultRunning);
  const runningNow = runningProp ?? runningJS;

  useAnimatedReaction(
    () => playingV.get(),
    (p, prev) => {
      if (p !== prev) scheduleOnRN(setPlaying, p);
    },
  );
  useEffect(() => {
    playingV.set(autoPlay && !reduce);
    queueMicrotask(() => setPlaying(autoPlay && !reduce));
  }, [autoPlay, reduce, playingV]);

  // the loop restarted: the live session state goes back to the pin's
  useEffect(() => {
    if (!playing) return;
    runningV.set(false);
    elapsed.set(0);
    totalSecs.set(totalSeconds);
    orbitV.set(totalSeconds / 3600);
    hintLive.set(0);
    hoursSeen.set(Math.floor(totalSeconds / 3600));
    rollLive.set(1);
    labelLive.set(0);
    queueMicrotask(() => {
      setRunningJS(false);
      setValueN(Math.floor(totalSeconds / 3600));
      setLab({ a: Math.floor(totalSeconds / 3600), b: Math.floor(totalSeconds / 3600) });
      setRoll({ from: fmt(totalSeconds), to: fmt(totalSeconds) });
    });
  }, [playing, totalSeconds, runningV, elapsed, totalSecs, orbitV, hintLive, hoursSeen, rollLive, labelLive]);

  // ---- the session -----------------------------------------------------------------------------------------------
  const hourJS = useCallback(
    (hours: number) => {
      setValueN(hours);
      setLab((prev) => ({ a: prev.b, b: hours }));
      labelLive.set(0);
      labelLive.set(
        withTiming(1, { duration: 300 }, (done) => {
          'worklet';
          if (done) scheduleOnRN(setLab, { a: hours, b: hours });
        }),
      );
      onHour?.(hours);
    },
    [labelLive, onHour],
  );
  const start = useCallback(() => {
    if (runningV.get()) return;
    runningV.set(true);
    elapsed.set(0);
    clockV.set(0);
    hintLive.set(withTiming(1, { duration: 300 }));
    setRunningJS(true);
    onStart?.();
  }, [runningV, elapsed, clockV, hintLive, onStart]);
  const stop = useCallback(() => {
    if (!runningV.get()) return;
    const el = elapsed.get();
    runningV.set(false);
    hintLive.set(withTiming(0, { duration: 300 }));
    setRunningJS(false);
    if (el < minSessionSeconds) {
      // a look, not a start: nothing is counted
      elapsed.set(0);
      cue.discard();
      return;
    }
    const before = totalSecs.get();
    const after = before + el;
    totalSecs.set(after);
    elapsed.set(0);
    setRoll({ from: fmt(before), to: fmt(after) });
    rollLive.set(0);
    rollLive.set(withDelay(200, withTiming(1, { duration: 700 })));
    onStop?.(Math.round(el));
  }, [runningV, elapsed, hintLive, minSessionSeconds, cue, totalSecs, rollLive, onStop]);
  const onFace = useCallback(
    (face: 'front' | 'back') => {
      if (runningProp !== undefined) {
        if (face === 'back') onStart?.();
        else onStop?.(Math.round(elapsed.get()));
        return;
      }
      if (face === 'back') start();
      else stop();
    },
    [runningProp, start, stop, onStart, onStop, elapsed],
  );
  // controlled `running`
  useEffect(() => {
    if (runningProp === undefined) return;
    queueMicrotask(() => (runningProp ? start() : stop()));
  }, [runningProp, start, stop]);

  useFrameCallback((info) => {
    'worklet';
    if (playingV.get()) return;
    const dt = Math.min((info.timeSincePreviousFrame ?? 16.7) / 1000, 0.05);
    if (runningV.get()) {
      const el = elapsed.get() + dt * timeScale;
      elapsed.set(el);
      clockV.set(el);
      const total = totalSecs.get() + el;
      orbitV.set(total / 3600);
      const hoursNow = Math.floor(total / 3600);
      if (hoursNow > hoursSeen.get()) {
        hoursSeen.set(hoursNow);
        scheduleOnRN(hourJS, hoursNow);
      }
    } else {
      orbitV.set(totalSecs.get() / 3600);
    }
  });

  // ---- the page's values: the loop's while it plays, the session's when a finger has taken over ----------------------------
  const page = useDerivedValue(() => {
    if (playingV.get()) {
      const l = lookV.get();
      return { fill: l.fill, hint: l.hint, label: l.labelK, roll: l.sheetK, sweep: l.sweep };
    }
    const frac = (totalSecs.get() + elapsed.get()) / 3600;
    return { fill: frac - Math.floor(frac), hint: hintLive.get(), label: labelLive.get(), roll: rollLive.get(), sweep: -1 };
  });
  const hintK = useDerivedValue(() => page.get().hint);
  const labelK = useDerivedValue(() => page.get().label);
  const rollK = useDerivedValue(() => page.get().roll);

  // ---- geometry ----------------------------------------------------------------------------------------------------
  const trackX = L.x(comp.progress.x);
  const trackW = comp.progress.w * S;
  const trackH = comp.progress.h * S;
  const trackY = L.yc(comp.progress.y);
  const trackR = trackH / 2;
  const trackClip = useMemo(() => rrect(rect(trackX, trackY, trackW, trackH), trackR, trackR), [trackX, trackY, trackW, trackH, trackR]);
  const fillW = useDerivedValue(() => Math.max(page.get().fill * trackW, trackH));
  const sweepT = useDerivedValue(() => [{ translateX: trackX + Math.max(page.get().sweep, 0) * (trackW + 44 * S) - 44 * S }]);
  const sweepO = useDerivedValue(() => (page.get().sweep >= 0 ? 0.6 * Math.sin(Math.PI * page.get().sweep) : 0));
  const badgeTop = L.yc(SCENE.centre.y) - 150 * S;

  // L's helpers are plain JS functions: read them here on the JS thread, never inside the worklet.
  const shadowX = L.x(SCENE.centre.x);
  const shadowY = L.yc(398);
  const ramp0 = L.yc(150);
  const ramp1 = L.yc(400);
  const backdrop = useDerivedValue(() => {
    const c = Math.abs(Math.cos((lookV.get().yaw * Math.PI) / 180));
    return {
      uSize: [W, H],
      uGround: GROUND,
      uShadow: [shadowX, shadowY, 95 * S * (0.4 + 0.6 * c), 26 * S],
      uAmt: 0.35 + 0.1 * c,
      uYramp0: ramp0,
      uYramp1: ramp1,
      uAux: [P3, 0, 0, 0],
    };
  });

  const orbit = useMemo(() => ({ progress: orbitV }), [orbitV]);
  const labA = playing ? Math.floor(totalSeconds / 3600) : lab.a;
  const labB = playing ? Math.floor(totalSeconds / 3600) + 1 : lab.b;
  const rollFrom = playing ? fmt(totalSeconds) : roll.from;
  const rollTo = playing ? fmt(totalSeconds + SESSION_SECS) : roll.to;
  const sheetLocal = (y: number) => (y - comp.sheet.y) * S;
  const tickBase = L.yc(461.3);
  const tickW = 48 * S;
  const labelColor = ref.color.inkLabel;
  const sheetStyle = {
    position: 'absolute' as const,
    left: L.x(comp.sheet.x),
    top: L.sheetTop,
    width: comp.sheet.w * S,
    height: comp.sheet.h * S,
    borderRadius: comp.sheet.r * S,
    borderCurve: 'continuous' as const,
    backgroundColor: ref.color.sheet,
    overflow: 'hidden' as const,
  };

  return (
    <View style={styles.page}>
      <Canvas style={StyleSheet.absoluteFill} accessible={false}>
        {BACKDROP_FX ? (
          <Fill>
            <Shader source={BACKDROP_FX} uniforms={backdrop} />
          </Fill>
        ) : (
          <Fill color={ref.color.pageMid} />
        )}
        <Group clip={trackClip}>
          <RoundedRect x={trackX} y={trackY} width={trackW} height={trackH} r={trackR} color={ref.color.track} />
          <RoundedRect x={trackX} y={trackY} width={fillW} height={trackH} r={trackR} color={ref.color.fill} />
          <Group transform={sweepT} opacity={sweepO}>
            <Rect x={0} y={trackY} width={44 * S} height={trackH}>
              <LinearGradient
                start={vec(0, 0)}
                end={vec(44 * S, 0)}
                colors={['#FFFFFF00', '#FFFFFFFF', '#FFFFFF00']}
                positions={[0, 0.5, 1]}
              />
            </Rect>
          </Group>
        </Group>
      </Canvas>

      <NumeralBadge
        value={valueN}
        face={runningProp === undefined ? undefined : runningProp ? 'back' : 'front'}
        onFaceChange={onFace}
        clock={clockV}
        orbit={orbit}
        source={source}
        haptics={haptics}
        autoPlay={autoPlay}
        look={lookV}
        playing={playingV}
        size={W}
        accessibilityLabel={runningNow ? COPY.badgeA11yRunning('') : COPY.badgeA11y(valueN)}
        style={{ position: 'absolute', left: 0, top: badgeTop }}
      />

      {fontsReady ? (
        <>
          <TopRow L={L} />
          <View style={StyleSheet.absoluteFill} pointerEvents="none">
            <Crossfade
              a={`${labA}h`}
              b={`${labB}h`}
              k={labelK}
              typo={TYPE.tick}
              L={L}
              baseline={tickBase}
              color={labelColor}
              align="left"
              left={trackX}
              width={tickW}
            />
            <Crossfade
              a={`${labA + 1}h`}
              b={`${labB + 1}h`}
              k={labelK}
              typo={TYPE.tick}
              L={L}
              baseline={tickBase}
              color={labelColor}
              align="right"
              left={trackX + trackW - tickW}
              width={tickW}
            />
            <Crossfade
              a={COPY.hintIdle}
              b={COPY.hintFocus}
              k={hintK}
              typo={TYPE.hint}
              L={L}
              baseline={L.yc(521.2)}
              color={ref.color.inkHint}
              slide={8 * S}
            />
          </View>
          <View style={sheetStyle} accessibilityRole="summary">
            <View
              style={{
                position: 'absolute',
                left: ((comp.sheet.w - comp.grabber.w) / 2) * S,
                top: sheetLocal(comp.grabber.y),
                width: comp.grabber.w * S,
                height: comp.grabber.h * S,
                borderRadius: (comp.grabber.h / 2) * S,
                backgroundColor: ref.color.grabber,
              }}
            />
            <Animated.View key={`d${range}`} entering={FadeIn.duration(200)} style={StyleSheet.absoluteFill} pointerEvents="none">
              <Line typo={TYPE.date} L={L} baseline={sheetLocal(673.4)} color={ref.color.inkDate}>
                {RANGES[range].date}
              </Line>
            </Animated.View>
            {range === 0 ? (
              <RollTotal from={rollFrom} to={rollTo} k={rollK} L={L} baseline={sheetLocal(728.4)} color={ref.color.ink} />
            ) : (
              <Animated.View key={`t${range}`} entering={FadeIn.duration(200)} style={StyleSheet.absoluteFill} pointerEvents="none">
                <Line typo={TYPE.total} L={L} baseline={sheetLocal(728.4)} color={ref.color.ink}>
                  {RANGES[range].total}
                </Line>
              </Animated.View>
            )}
            <Line typo={TYPE.caption} L={L} baseline={sheetLocal(760.4)} color={ref.color.inkMute}>
              {COPY.caption}
            </Line>
            <Segmented L={L} index={range} onChange={setRange} top={sheetLocal(comp.seg.y)} />
          </View>
        </>
      ) : null}
    </View>
  );
});

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: ref.color.pageMid },
  page: { flex: 1, backgroundColor: ref.color.pageMid },
});
