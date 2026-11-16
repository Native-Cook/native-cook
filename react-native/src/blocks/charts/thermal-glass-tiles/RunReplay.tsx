import { useCallback, useEffect, useMemo, useState } from 'react';
import { StatusBar } from 'expo-status-bar';
import { AppState, StyleSheet, Text as NativeText, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { Canvas, Fill, Group, Skia, useImage, useFont, type SkPath, type DataSourceParam } from '@shopify/react-native-skia';
import Animated, {
  cancelAnimation,
  useAnimatedProps,
  useAnimatedReaction,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { TileContent } from './tgt-content';
import { effortAt, nearestRoute, ROUTE, RUN, sampleAt, timeText, type RoutePoint, type RunSample } from './tgt-data';
import { kilometreHaptic, onsetHaptic, peakHaptic } from './tgt-haptics';
import { GlassTile, ThermalField } from './tgt-material';
import {
  clamp,
  coastAt,
  LOOP,
  MOTION,
  PEAK_START,
  bpmPulseAt,
  peakAt,
  progressAt,
  rubberProgress,
  smooth,
  sceneTimeAt,
  strideAt,
} from './tgt-motion';
import { comp, makeLayout, sys, type TileRect } from './tgt-theme';

export type RunReplayProps = {
  data?: RunSample[];
  route?: SkPath | string;
  progress?: SharedValue<number>;
  defaultProgress?: number;
  returnAfterMs?: number | null;
  units?: 'km' | 'mi';
  autoPlay?: boolean;
  onScrubStart?: () => void;
  onScrub?: (distance: number, sample: RunSample) => void;
  onScrubEnd?: (distance: number) => void;
  tokens?: { font?: DataSourceParam; inkLight?: string; inkDark?: string; marker?: string };
};
const AnimatedView = Animated.createAnimatedComponent(View);
function resampleRoute(route: SkPath | string): RoutePoint[] {
  const path = typeof route === 'string' ? Skia.Path.MakeFromSVGString(route) : route;
  if (!path)
    return [
      { x: 214, y: 245 },
      { x: 214, y: 245 },
    ];
  const contour = Skia.ContourMeasureIter(path, true, 1).next();
  if (!contour)
    return [
      { x: 214, y: 245 },
      { x: 214, y: 245 },
    ];
  const len = contour.length();
  return Array.from({ length: MOTION.routeSamples }, (_, i) => {
    const [p] = contour.getPosTan((len * i) / (MOTION.routeSamples - 1));
    return { x: p.x, y: p.y };
  });
}
export function RunReplay({
  data = RUN,
  route = ROUTE,
  progress: controlled,
  defaultProgress = 1,
  returnAfterMs = MOTION.returnAfterMs,
  units = 'km',
  autoPlay = false,
  onScrubStart,
  onScrub,
  onScrubEnd,
  tokens,
}: RunReplayProps) {
  const { width, height, fontScale } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const sharpReady = useImage(require('./assets/runner-heat-atlas.png'));
  const blurReady = useImage(require('./assets/runner-heat-atlas-blur.png'));
  const paletteReady = useImage(require('./assets/thermal-palette.png'));
  const fontReady = useFont(require('./assets/fonts/Urbanist-Regular.ttf'), sys.font.note);
  const lightReady = useFont(require('./assets/fonts/Urbanist-Light.ttf'), sys.font.display.size);
  const ready = !!sharpReady && !!blurReady && !!paletteReady && !!fontReady && !!lightReady;
  const layout = makeLayout(width, height, insets.top, insets.bottom);
  const { s, ox, oy } = layout;
  const localProgress = useSharedValue(clamp(defaultProgress));
  const progress = controlled ?? localProgress;
  const loopEpoch = useSharedValue(-1);
  const time = useSharedValue(0),
    clock = useSharedValue(0),
    lastTouch = useSharedValue(-1),
    pinned = useSharedValue(-1);
  const playing = useSharedValue(autoPlay),
    held = useSharedValue(false),
    returning = useSharedValue(false);
  const coasting = useSharedValue(false),
    coastOrigin = useSharedValue(1),
    coastVelocity = useSharedValue(0),
    releasedAt = useSharedValue(0);
  const startX = useSharedValue(0),
    startP = useSharedValue(1),
    routeGrab = useSharedValue(false);
  const velocity = useSharedValue(0),
    lastP = useSharedValue(1),
    lastEvent = useSharedValue(0);
  const touch = useSharedValue([0, 0, -1]),
    touchPosition = useSharedValue([0, 0]),
    touchStart = useSharedValue(-100);
  const peakStart = useSharedValue(-100),
    peakArmed = useSharedValue(true),
    lastTick = useSharedValue(-100);
  const [active, setActive] = useState(false);
  const [foreground, setForeground] = useState(AppState.currentState === 'active');
  const points = useMemo(() => resampleRoute(route), [route]);
  const routeLength = useMemo(
    () =>
      Math.max(
        1,
        points.reduce((length, pt, i) => (i ? length + Math.hypot(pt.x - points[i - 1].x, pt.y - points[i - 1].y) : 0), 0),
      ),
    [points],
  );
  const contactExtreme = useMemo(
    () => data.reduce((best, d, i) => (d.contact > (data[best]?.contact ?? 0) ? i : best), 0) / Math.max(1, data.length - 1),
    [data],
  );
  const verticalExtreme = useMemo(
    () => data.reduce((best, d, i) => (d.oscillation > (data[best]?.oscillation ?? 0) ? i : best), 0) / Math.max(1, data.length - 1),
    [data],
  );
  const sample = useDerivedValue(() => sampleAt(data, progress.get()));
  const effort = useDerivedValue(() => effortAt(sample.get()));
  const streak = useDerivedValue(() => clamp((376 - sample.get().pace) / 96));
  const direction = useDerivedValue(() =>
    playing.get() || pinned.get() >= 0
      ? Math.sign(progressAt(time.get() + 1 / 120) - progressAt(time.get() - 1 / 120)) || 1
      : Math.sign(velocity.get()) || 1,
  );
  const phase = useDerivedValue(() => strideAt(progress.get(), time.get(), reduced));
  const thermalEffort = useSharedValue(effortAt(sampleAt(data, defaultProgress)));
  useAnimatedReaction(
    () => effort.get(),
    (e) => {
      thermalEffort.set(reduced ? withTiming(e, { duration: 200 }) : e);
    },
  );
  const fieldTime = useDerivedValue(() => (reduced ? 0 : playing.get() || pinned.get() >= 0 ? sceneTimeAt(time.get()) : time.get()));
  const peak = useDerivedValue(() =>
    reduced
      ? 0
      : playing.get() || pinned.get() >= 0
        ? peakAt(time.get())
        : smooth(0, 0.08, clock.get() - peakStart.get()) * (1 - smooth(0.55, 0.7, clock.get() - peakStart.get())),
  );
  const circlePeak = useDerivedValue(() =>
    reduced
      ? 0
      : playing.get() || pinned.get() >= 0
        ? peakAt(time.get() - 0.08)
        : smooth(0.08, 0.16, clock.get() - peakStart.get()) * (1 - smooth(0.63, 0.78, clock.get() - peakStart.get())),
  );
  const smallPeak = useDerivedValue(() =>
    reduced
      ? 0
      : playing.get() || pinned.get() >= 0
        ? peakAt(time.get() - 0.16)
        : smooth(0.16, 0.24, clock.get() - peakStart.get()) * (1 - smooth(0.71, 0.86, clock.get() - peakStart.get())),
  );
  const bpmScale = useDerivedValue(() =>
    reduced ? 1 : bpmPulseAt(playing.get() || pinned.get() >= 0 ? time.get() - PEAK_START : clock.get() - peakStart.get()),
  );
  const ringBase = useSharedValue(4);
  useAnimatedReaction(
    () => held.get(),
    (h) => ringBase.set(reduced ? (h ? 6 : 4) : withSpring(h ? 6 : 4, MOTION.ring)),
  );
  const ring = useDerivedValue(
    () => ringBase.get() + (reduced || held.get() ? 0 : 2 * Math.pow(Math.max(0, Math.sin((time.get() * Math.PI) / 2)), 8)),
  );

  useEffect(() => {
    const sub = AppState.addEventListener('change', (next) => {
      loopEpoch.set(-1);
      setForeground(next === 'active');
    });
    return () => sub.remove();
  }, [loopEpoch]);
  const wake = useCallback(() => setActive(!reduced), [reduced]);
  const callback = useFrameCallback((frame) => {
    const dt = Math.max(0, (frame.timeSincePreviousFrame ?? 16.6667) / 1000);
    clock.set(clock.get() + dt);
    if (pinned.get() >= 0) {
      const t = pinned.get() % LOOP;
      time.set(t);
      progress.set(progressAt(t));
      return;
    }
    if (playing.get()) {
      if (loopEpoch.get() < 0) loopEpoch.set(frame.timestamp / 1000 - time.get());
      time.set((frame.timestamp / 1000 - loopEpoch.get()) % LOOP);
      progress.set(progressAt(time.get()));
    } else {
      if (coasting.get() && !held.get() && !returning.get()) {
        const c = coastAt(coastOrigin.get(), coastVelocity.get(), clock.get() - releasedAt.get());
        progress.set(rubberProgress(c.position));
        velocity.set(c.velocity);
        if (Math.abs(c.velocity) < 0.001 || clock.get() - releasedAt.get() > 1.2) {
          coasting.set(false);
          progress.set(withSpring(clamp(c.position), { ...MOTION.snap, velocity: c.velocity }));
        }
      }
      if (!returning.get()) time.set((time.get() + dt) % LOOP);
      if (
        !held.get() &&
        lastTouch.get() >= 0 &&
        !returning.get() &&
        returnAfterMs !== null &&
        clock.get() - lastTouch.get() >= returnAfterMs / 1000
      ) {
        coasting.set(false);
        returning.set(true);
        time.set(withTiming(0, { duration: 600 }));
        progress.set(
          withSpring(1, { ...MOTION.return, velocity: 0 }, (finished) => {
            if (finished) {
              returning.set(false);
              lastTouch.set(-1);
              time.set(0);
              loopEpoch.set(-1);
              playing.set(autoPlay);
              if (!autoPlay) scheduleOnRN(setActive, false);
            }
          }),
        );
      }
      if (!held.get() && returnAfterMs === null && Math.abs(progress.get() - lastP.get()) < 0.00001 && clock.get() - lastTouch.get() > 2) {
        scheduleOnRN(setActive, false);
      }
      lastP.set(progress.get());
    }
    if (held.get()) touch.set([touchPosition.get()[0], touchPosition.get()[1], 0]);
    else touch.set([touchPosition.get()[0], touchPosition.get()[1], clock.get() - touchStart.get()]);
  }, false);
  useEffect(() => {
    callback.setActive(active && foreground && !reduced);
    return () => callback.setActive(false);
  }, [active, foreground, reduced, callback]);
  useEffect(() => {
    if (!ready) return;
    const firstPaint = requestAnimationFrame(() => {
      loopEpoch.set(-1);
      playing.set(autoPlay && !reduced);
      setActive(autoPlay && !reduced);
    });
    return () => cancelAnimationFrame(firstPaint);
  }, [ready, autoPlay, reduced, playing, loopEpoch]);
  useEffect(() => {
    if (!__DEV__) return;
    const hook = {
      pin: (t: number) => {
        cancelAnimation(progress);
        pinned.set(Math.max(0, t));
        time.set(Math.max(0, t) % LOOP);
        progress.set(progressAt(t));
        setActive(false);
      },
      at: (t: number) => {
        cancelAnimation(progress);
        pinned.set(Math.max(0, t));
        time.set(Math.max(0, t) % LOOP);
        progress.set(progressAt(t));
        setActive(false);
      },
      play: () => {
        pinned.set(-1);
        time.set(0);
        loopEpoch.set(-1);
        playing.set(true);
        setActive(!reduced);
      },
      progress: (p: number) => {
        cancelAnimation(progress);
        pinned.set(-1);
        playing.set(false);
        progress.set(clamp(p));
        setActive(false);
      },
      state: () => ({ progress: progress.get(), time: time.get(), reduced }),
    };
    const g = globalThis as typeof globalThis & { __tgt?: typeof hook };
    g.__tgt = hook;
    return () => {
      if (g.__tgt === hook) delete g.__tgt;
    };
  }, [pinned, playing, progress, reduced, time, loopEpoch]);
  useAnimatedReaction(
    () => effort.get(),
    (e) => {
      if (e < 0.6) peakArmed.set(true);
      if (e >= 0.8 && peakArmed.get()) {
        peakArmed.set(false);
        peakStart.set(clock.get());
        if (!playing.get() && !returning.get() && pinned.get() < 0) scheduleOnRN(peakHaptic);
      }
    },
  );
  useAnimatedReaction(
    () => sample.get().distance,
    (distance, previous) => {
      const now = Date.now();
      if (!playing.get() && pinned.get() < 0 && (held.get() || coasting.get())) {
        if (previous !== null && Math.floor(distance) !== Math.floor(previous) && now - lastTick.get() >= MOTION.hapticGapMs) {
          lastTick.set(now);
          scheduleOnRN(kilometreHaptic);
        }
        if (held.get() && onScrub && now - lastEvent.get() >= 1000 / MOTION.maxCallbacksHz) {
          lastEvent.set(now);
          scheduleOnRN(onScrub, distance, sample.get());
        }
      }
    },
  );
  const begin = useCallback(() => {
    wake();
    onScrubStart?.();
    onsetHaptic();
  }, [wake, onScrubStart]);
  const end = useCallback((distance: number) => onScrubEnd?.(distance), [onScrubEnd]);
  const pan = useMemo(
    () =>
      Gesture.Pan()
        .enabled(data.length > 0)
        .minDistance(0)
        .maxPointers(1)
        .onBegin((e) => {
          cancelAnimation(progress);
          cancelAnimation(time);
          pinned.set(-1);
          playing.set(false);
          returning.set(false);
          coasting.set(false);
          held.set(true);
          velocity.set(0);
          startX.set(e.x);
          startP.set(progress.get());
          lastTouch.set(clock.get());
          const x = comp.large.x + e.x / s,
            y = comp.large.y + e.y / s;
          const near = nearestRoute(points, x, y, progress.get());
          routeGrab.set(near.distance <= comp.route.grab);
          if (near.distance <= comp.route.grab) progress.set(near.p);
          touchPosition.set([x, y]);
          touchStart.set(clock.get());
          touch.set([x, y, 0]);
          scheduleOnRN(begin);
        })
        .onUpdate((e) => {
          const x = comp.large.x + e.x / s,
            y = comp.large.y + e.y / s;
          let next = startP.get() + (e.x - startX.get()) / (MOTION.dragPt * s);
          if (routeGrab.get()) next = nearestRoute(points, x, y, progress.get()).p;
          const raw = rubberProgress(next);
          progress.set(raw);
          // Route tangent velocity is projected from adjacent equal-arc samples.
          if (routeGrab.get()) {
            const i = Math.round(clamp(raw) * (points.length - 1)),
              a = points[Math.max(0, i - 1)],
              b = points[Math.min(points.length - 1, i + 1)];
            const len = Math.max(0.001, Math.hypot(b.x - a.x, b.y - a.y));
            velocity.set((e.velocityX * (b.x - a.x) + e.velocityY * (b.y - a.y)) / (s * len * routeLength));
          } else velocity.set(e.velocityX / (MOTION.dragPt * s));
          touchPosition.set([x, y]);
          lastTouch.set(clock.get());
        })
        .onEnd(() => {
          const v = velocity.get();
          if (!reduced) {
            coastOrigin.set(progress.get());
            coastVelocity.set(v);
            releasedAt.set(clock.get());
            coasting.set(true);
          } else progress.set(clamp(progress.get()));
        })
        .onFinalize(() => {
          held.set(false);
          lastTouch.set(clock.get());
          touchStart.set(clock.get());
          scheduleOnRN(end, sample.get().distance);
        }),
    [
      data.length,
      progress,
      pinned,
      playing,
      returning,
      held,
      velocity,
      startX,
      startP,
      lastTouch,
      clock,
      s,
      points,
      routeLength,
      routeGrab,
      touchPosition,
      touchStart,
      touch,
      begin,
      reduced,
      coasting,
      coastOrigin,
      coastVelocity,
      releasedAt,
      end,
      sample,
      time,
    ],
  );
  const shortcut = useCallback(
    (target: number) => {
      cancelAnimation(progress);
      pinned.set(-1);
      playing.set(false);
      returning.set(false);
      coasting.set(false);
      lastTouch.set(clock.get());
      onScrubStart?.();
      onsetHaptic();
      wake();
      progress.set(reduced ? target : withSpring(target, MOTION.shortcut));
      onScrubEnd?.(sampleAt(data, target).distance);
    },
    [progress, pinned, playing, returning, coasting, lastTouch, clock, onScrubStart, wake, reduced, onScrubEnd, data],
  );
  const warmTile = useCallback(
    (x: number, y: number) => {
      'worklet';
      touchPosition.set([x, y]);
      touchStart.set(clock.get());
      touch.set([x, y, 0]);
      scheduleOnRN(wake);
    },
    [touchPosition, touchStart, clock, touch, wake],
  );
  const contactTap = useMemo(
    () =>
      Gesture.Tap()
        .enabled(data.length > 0)
        .onBegin(() => warmTile(122, 583))
        .onEnd((_e, ok) => {
          if (ok) scheduleOnRN(shortcut, contactExtreme);
        }),
    [shortcut, contactExtreme, data.length, warmTile],
  );
  const verticalTap = useMemo(
    () =>
      Gesture.Tap()
        .enabled(data.length > 0)
        .onBegin(() => warmTile(280, 583))
        .onEnd((_e, ok) => {
          if (ok) scheduleOnRN(shortcut, verticalExtreme);
        }),
    [shortcut, verticalExtreme, data.length, warmTile],
  );
  const totalDistance = data[data.length - 1]?.distance ?? 29.6;
  const replayA11y = useAnimatedProps(() => ({
    accessibilityValue: {
      min: 0,
      max: totalDistance * (units === 'mi' ? 0.621371 : 1),
      now: sample.get().distance * (units === 'mi' ? 0.621371 : 1),
      text: `${(sample.get().distance * (units === 'mi' ? 0.621371 : 1)).toFixed(1)} ${units === 'mi' ? 'miles' : 'kilometres'}, ${Math.round(sample.get().bpm)} beats per minute, pace ${timeText(sample.get().pace * (units === 'mi' ? 1.609344 : 1))} per ${units === 'mi' ? 'mile' : 'kilometre'}`,
    },
  }));
  const contactA11y = useAnimatedProps(() => ({ accessibilityValue: { text: `${Math.round(sample.get().contact)} milliseconds` } }));
  const verticalA11y = useAnimatedProps(() => ({ accessibilityValue: { text: `${sample.get().oscillation.toFixed(1)} centimetres` } }));
  const rect = (r: TileRect): TileRect => ({ x: ox + r.x * s, y: oy + r.y * s, w: r.w * s, h: r.h * s, r: r.r * s });
  const field = (
    <ThermalField
      effort={thermalEffort}
      streak={streak}
      direction={direction}
      phase={phase}
      time={fieldTime}
      peak={peak}
      touch={touch}
      width={width}
      height={height}
      scale={s}
      offset={[ox, oy]}
      wake={reduced ? 0 : sys.hero.field.peakWake}
      grain={sys.hero.field.grain}
      touchHeat={!reduced}
    />
  );
  const frostedField = (
    <ThermalField
      soft
      effort={thermalEffort}
      streak={streak}
      direction={direction}
      phase={phase}
      time={fieldTime}
      peak={peak}
      touch={touch}
      width={width}
      height={height}
      scale={s}
      offset={[ox, oy]}
      wake={reduced ? 0 : sys.hero.field.peakWake}
      grain={sys.hero.field.grain}
      touchHeat={!reduced}
    />
  );
  const large = rect(comp.large),
    contact = rect(comp.contact),
    vertical = rect(comp.vertical);
  const darkInk = useDerivedValue<number>(() => (effort.get() > 0.82 ? 1 : 0));
  const ink = useDerivedValue(() => (darkInk.get() > 0.5 ? (tokens?.inkDark ?? sys.ink.dark) : (tokens?.inkLight ?? sys.ink.primary)));
  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      {ready ? (
        <Canvas style={StyleSheet.absoluteFill}>
          <Fill>{field}</Fill>
          <GlassTile
            rect={large}
            darkInk={darkInk}
            backdrop={frostedField}
            time={fieldTime}
            peak={peak}
            scale={s}
            labelRect={[
              ox + comp.labelWells.large[0] * s,
              oy + comp.labelWells.large[1] * s,
              comp.labelWells.large[2] * s,
              comp.labelWells.large[3] * s,
            ]}
          />
          <GlassTile
            rect={contact}
            shape="circle"
            darkInk={darkInk}
            backdrop={frostedField}
            time={fieldTime}
            peak={circlePeak}
            scale={s}
            labelRect={[ox + 73 * s, oy + 526 * s, 98 * s, 80 * s]}
          />
          <GlassTile
            rect={vertical}
            darkInk={darkInk}
            backdrop={frostedField}
            time={fieldTime}
            peak={smallPeak}
            scale={s}
            labelRect={[ox + 222 * s, oy + 525 * s, 116 * s, 114 * s]}
          />
          <Group transform={[{ translateX: ox }, { translateY: oy }, { scale: s }]}>
            <TileContent
              sample={sample}
              units={units}
              points={points}
              progress={progress}
              ring={ring}
              fontScale={fontScale}
              ink={ink}
              fontSource={tokens?.font}
              marker={tokens?.marker}
              bpmScale={bpmScale}
              reduced={reduced}
            />
          </Group>
        </Canvas>
      ) : null}
      <GestureDetector gesture={pan}>
        <AnimatedView
          style={[styles.hit, { left: large.x, top: large.y, width: large.w, height: large.h }]}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel="Run replay"
          accessibilityHint="Drag along the route to replay your run. Swipe up or down to step one kilometre."
          animatedProps={replayA11y}
          accessibilityActions={[
            { name: 'increment', label: 'Next kilometre' },
            { name: 'decrement', label: 'Previous kilometre' },
          ]}
          onAccessibilityAction={(e) =>
            shortcut(
              clamp(
                progress.get() +
                  ((e.nativeEvent.actionName === 'increment' ? 1 : -1) * (units === 'mi' ? 1.609344 : 1)) / Math.max(0.1, totalDistance),
              ),
            )
          }
        />
      </GestureDetector>
      <GestureDetector gesture={contactTap}>
        <AnimatedView
          style={[styles.hit, { left: contact.x, top: contact.y, width: contact.w, height: contact.h }]}
          animatedProps={contactA11y}
          accessible
          accessibilityRole="button"
          accessibilityLabel="Contact time. Replay the longest ground contact."
          onAccessibilityTap={() => shortcut(contactExtreme)}
        />
      </GestureDetector>
      <GestureDetector gesture={verticalTap}>
        <AnimatedView
          style={[styles.hit, { left: vertical.x, top: vertical.y, width: vertical.w, height: vertical.h }]}
          animatedProps={verticalA11y}
          accessible
          accessibilityRole="button"
          accessibilityLabel="Vertical oscillation. Replay the highest bounce."
          onAccessibilityTap={() => shortcut(verticalExtreme)}
        />
      </GestureDetector>
      {data.length === 0 ? <NativeText style={styles.empty}>No run samples</NativeText> : null}
    </View>
  );
}
const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: sys.surface.page },
  hit: { position: 'absolute', backgroundColor: 'transparent' },
  empty: { position: 'absolute', top: '47%', alignSelf: 'center', color: sys.ink.primary, fontSize: 16 },
});
