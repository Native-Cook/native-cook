/**
 * A tape deck that is the whole screen. Press play: the key goes down and latches, the motor brings the tape up to
 * speed, the reels wind it from one pack to the other, the counter rolls and the meters move with the music. Fast
 * forward winds it on; rewind winds it back, and the counter's memory stops it at 000 by itself. Stop and pause act
 * and spring back up.
 *
 * One Skia picture drawn from one model stepped on the UI thread (model.ts, scene.ts); React only hears what it reads
 * out to VoiceOver.
 */
import { Canvas, Picture, createPicture, useTypeface } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useCallback, useMemo, useState } from 'react';
import { PixelRatio, StyleSheet, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { KEY_W, MODE, cloneModel, createModel, keyAt, keyX, pressKey, releaseKey, script, step, toRest, type Model } from './model';
import { HAPTIC, LOOP_MS, RESUME_MS } from './motion';
import { drawScene, makeScene, type Faces } from './scene';
import { COLOR, COPY, LAYOUT, makeLayout, type Layout } from './theme';

export type CassetteDeckProps = { autoPlay?: boolean };

function playHaptic(kind: number) {
  const ignore = () => {};
  if (kind === HAPTIC.latch) void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(ignore);
  else if (kind === HAPTIC.memo) void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(ignore);
  else void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(ignore);
}

export function CassetteDeck({ autoPlay = false }: CassetteDeckProps) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const regular = useTypeface(require('./assets/Inter-Regular.ttf'));
  const medium = useTypeface(require('./assets/Inter-Medium.ttf'));
  const semibold = useTypeface(require('./assets/Inter-SemiBold.ttf'));
  const hand = useTypeface(require('./assets/CoveredByYourGrace.ttf'));
  if (!regular || !medium || !semibold || !hand) return <View style={styles.root} />;
  const lay = makeLayout(width, height, insets.top, insets.bottom);
  return (
    <Screen key={`${width}x${height}-${insets.top}-${reduced}`} lay={lay} faces={{ regular, medium, semibold, hand }}
      reduced={reduced} autoPlay={autoPlay && !reduced} />
  );
}

function Screen({ lay, faces, reduced, autoPlay }: { lay: Layout; faces: Faces; reduced: boolean; autoPlay: boolean }) {
  const ctx = useMemo(() => makeScene(lay, faces, Math.min(PixelRatio.get(), 3) * lay.s), [lay, faces]);
  const model = useSharedValue<Model>(createModel(reduced));
  const [mode, setMode] = useState<number>(MODE.stop);
  const published = useSharedValue(-1);
  const paused = useSharedValue(!autoPlay);
  const touching = useSharedValue(false);
  const quiet = useSharedValue(0);
  const clock = useSharedValue(0);

  // ——— Helpers (declared before every worklet that calls them) ———
  const apply = (m: Model) => {
    'worklet';
    for (let i = 0; i < m.events.length; i++) scheduleOnRN(playHaptic, m.events[i]);
    m.events = [];
    if (m.version !== published.get()) {
      published.set(m.version);
      scheduleOnRN(setMode, m.mode);
    }
    model.set(m);
  };

  const pauseDemo = (m: Model) => {
    'worklet';
    quiet.set(0);
    m.silent = false;
    if (paused.get()) return;
    paused.set(true);
    m.ghostOT = 0;
    releaseKey(m);
  };

  useFrameCallback((frame) => {
    'worklet';
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const cur = model.get();
    let m: Model | null = null;
    if (autoPlay) {
      if (paused.get()) {
        if (!touching.get()) quiet.set(quiet.get() + dt);
        if (quiet.get() >= RESUME_MS / 1000) {
          // Back to the loop's start: stopped at 000, then the script again.
          m = cloneModel(cur);
          m.silent = true;
          toRest(m);
          clock.set(0);
          paused.set(false);
        }
      } else {
        m = cloneModel(cur);
        m.silent = true;
        const t0 = clock.get();
        const t1 = t0 + dt;
        script(m, t0, t1);
        clock.set(t1 >= LOOP_MS / 1000 ? t1 - LOOP_MS / 1000 : t1);
        if (!cur.moving && !m.moving) return;
      }
    }
    if (!m) {
      if (!cur.moving) return;
      m = cloneModel(cur);
    }
    step(m, dt);
    apply(m);
  });

  const touchStart = useCallback(() => {
    scheduleOnUI(() => {
      'worklet';
      touching.set(true);
      const m = cloneModel(model.get());
      pauseDemo(m);
      apply(m);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  const touchEnd = useCallback(() => {
    scheduleOnUI(() => {
      'worklet';
      touching.set(false);
      quiet.set(0);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const gesture = useMemo(() => Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      touching.set(true);
      const m = cloneModel(model.get());
      pauseDemo(m);
      pressKey(m, keyAt((e.x - lay.ox) / lay.s, (e.y - lay.oy) / lay.s));
      apply(m);
    })
    .onFinalize(() => {
      touching.set(false);
      quiet.set(0);
      const m = cloneModel(model.get());
      releaseKey(m);
      apply(m);
    }),
  // eslint-disable-next-line react-hooks/exhaustive-deps
  []);

  const picture = useDerivedValue(() => {
    const m = model.get();
    return createPicture((canvas) => {
      if (ctx) drawScene(canvas, ctx, m);
    });
  });

  const press = useCallback((i: number) => scheduleOnUI(() => {
    'worklet';
    const m = cloneModel(model.get());
    pauseDemo(m);
    pressKey(m, i);
    releaseKey(m);
    apply(m);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }), []);

  const state = ['Stopped', 'Playing', 'Fast forwarding', 'Rewinding', 'Paused'][mode];
  return (
    <GestureHandlerRootView style={styles.root}>
      <View style={styles.root} onTouchStart={touchStart} onTouchEnd={touchEnd} onTouchCancel={touchEnd}>
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          <Picture picture={picture} />
        </Canvas>
        <GestureDetector gesture={gesture}>
          <View style={StyleSheet.absoluteFill} collapsable={false}>
            <View accessible accessibilityRole="header" accessibilityLabel={`${COPY.title}, side ${COPY.side}. ${state}.`}
              style={[styles.a11y, { left: lay.ox + LAYOUT.well.x * lay.s, top: lay.oy + LAYOUT.well.y * lay.s, width: LAYOUT.well.w * lay.s, height: LAYOUT.well.h * lay.s }]} />
            {COPY.keys.map((label, i) => (
              <View key={label} accessible accessibilityRole="button" accessibilityLabel={label} onAccessibilityTap={() => press(i)}
                style={[styles.a11y, { left: lay.ox + keyX(i) * lay.s, top: lay.oy + LAYOUT.keys.y * lay.s, width: KEY_W * lay.s, height: LAYOUT.keys.h * lay.s }]} />
            ))}
          </View>
        </GestureDetector>
      </View>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: COLOR.plate },
  a11y: { position: 'absolute' },
});
