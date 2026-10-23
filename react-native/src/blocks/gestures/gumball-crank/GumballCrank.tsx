/**
 * Today's treat from a gumball machine. Turn the handle once around: it clicks through four detents (the pawl will not
 * let it go back) and the gumballs jostle at each; the capsule drops into the chute, rolls out of the door toward you,
 * splits, and a card rises out of it with the day's coins. Collect sends them flying into the wallet.
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

import {
  BUTTON, PHASE, cloneModel, collect, createModel, grabAt, pressCollect, release, script, step, toRest, turnTo,
  type Model,
} from './model';
import { HAPTIC, LOOP_MS, RESUME_MS } from './motion';
import { drawScene, makeScene } from './scene';
import { COLOR, COPY, LAYOUT, WALLET, makeLayout, type Layout } from './theme';

export type GumballCrankProps = { autoPlay?: boolean };

function playHaptic(kind: number) {
  const ignore = () => {};
  if (kind === HAPTIC.tick) void Haptics.selectionAsync().catch(ignore);
  else if (kind === HAPTIC.strain) void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft).catch(ignore);
  else if (kind === HAPTIC.open) void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(ignore);
  else void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(ignore); // drop, collect
}

export function GumballCrank({ autoPlay = false }: GumballCrankProps) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const serif = useTypeface(require('./assets/InstrumentSerif-Regular.ttf'));
  const regular = useTypeface(require('./assets/Inter-Regular.ttf'));
  const medium = useTypeface(require('./assets/Inter-Medium.ttf'));
  const semibold = useTypeface(require('./assets/Inter-SemiBold.ttf'));
  if (!serif || !regular || !medium || !semibold) return <View style={styles.root} />;
  const lay = makeLayout(width, height, insets.top, insets.bottom);
  return (
    <Screen key={`${width}x${height}-${insets.top}-${reduced}`} lay={lay} faces={{ serif, regular, medium, semibold }}
      reduced={reduced} autoPlay={autoPlay && !reduced} />
  );
}

type Ui = { phase: number; wallet: number };

function Screen({ lay, faces, reduced, autoPlay }: {
  lay: Layout; faces: Parameters<typeof makeScene>[1]; reduced: boolean; autoPlay: boolean;
}) {
  const ctx = useMemo(() => makeScene(lay, faces, Math.min(PixelRatio.get(), 3) * lay.s), [lay, faces]);
  const model = useSharedValue<Model>(createModel(reduced));
  const [ui, setUi] = useState<Ui>({ phase: PHASE.ready, wallet: WALLET.before });
  const published = useSharedValue(-1);
  const paused = useSharedValue(!autoPlay);
  const touching = useSharedValue(false);
  const quiet = useSharedValue(0);
  const clock = useSharedValue(0);
  const onButton = useSharedValue(false);

  // ——— Helpers (declared before every worklet that calls them) ———
  const apply = (m: Model) => {
    'worklet';
    for (let i = 0; i < m.events.length; i++) scheduleOnRN(playHaptic, m.events[i]);
    m.events = [];
    if (m.version !== published.get()) {
      published.set(m.version);
      scheduleOnRN(setUi, { phase: m.phase, wallet: Math.round(m.wallet) });
    }
    model.set(m);
  };

  /** A touch: the script lets go where it is; the model speaks (haptics) from now on. */
  const pauseDemo = (m: Model) => {
    'worklet';
    quiet.set(0);
    m.silent = false;
    if (paused.get()) return;
    paused.set(true);
    m.ghostOT = 0;
    m.ghostPT = 0;
    if (m.scriptDown) {
      m.scriptDown = false;
      release(m);
    }
  };

  const toStage = (x: number, y: number): [number, number] => {
    'worklet';
    return [(x - lay.ox) / lay.s, (y - lay.oy) / lay.s];
  };

  const inButton = (x: number, y: number) => {
    'worklet';
    return x >= BUTTON.x && x <= BUTTON.x + BUTTON.w && y >= BUTTON.y - 8 && y <= BUTTON.y + BUTTON.h + 8;
  };

  useFrameCallback((frame) => {
    'worklet';
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const cur = model.get();
    let m: Model | null = null;
    if (autoPlay) {
      if (paused.get()) {
        const idle = !touching.get() && !cur.grab && cur.phase !== PHASE.collecting && cur.phase !== PHASE.dispensing;
        if (idle) quiet.set(quiet.get() + dt);
        if (quiet.get() >= RESUME_MS / 1000) {
          // Back to the loop's start (the card goes, the balance and the hint return), then the script again.
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
      const [x, y] = toStage(e.x, e.y);
      if (m.phase === PHASE.reward && inButton(x, y)) {
        onButton.set(true);
        pressCollect(m, true);
      } else grabAt(m, x, y);
      apply(m);
    })
    .onUpdate((e) => {
      const m = cloneModel(model.get());
      const [x, y] = toStage(e.x, e.y);
      if (onButton.get()) pressCollect(m, inButton(x, y));
      else turnTo(m, x, y);
      apply(m);
    })
    .onFinalize((e) => {
      touching.set(false);
      quiet.set(0);
      const m = cloneModel(model.get());
      const [x, y] = toStage(e.x, e.y);
      if (onButton.get()) {
        onButton.set(false);
        if (inButton(x, y)) collect(m);
        else pressCollect(m, false);
      } else release(m);
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

  const turn = useCallback(() => scheduleOnUI(() => {
    'worklet';
    // VoiceOver: one activation turns the handle a quarter; four dispense.
    const m = cloneModel(model.get());
    pauseDemo(m);
    if (m.phase === PHASE.reward) collect(m);
    else if (m.phase === PHASE.ready) {
      const [px, py] = [STAGE_PLATE.x, STAGE_PLATE.y - LAYOUT.arm];
      grabAt(m, px, py);
      const a = m.angle + Math.PI / 2 + 0.02;
      turnTo(m, STAGE_PLATE.x + LAYOUT.arm * Math.sin(a), STAGE_PLATE.y - LAYOUT.arm * Math.cos(a));
      release(m);
    }
    apply(m);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }), []);

  const sx = (x: number) => lay.ox + x * lay.s;
  const sy = (y: number) => lay.oy + y * lay.s;
  return (
    <GestureHandlerRootView style={styles.root}>
      <View style={styles.root} onTouchStart={touchStart} onTouchEnd={touchEnd} onTouchCancel={touchEnd}>
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          <Picture picture={picture} />
        </Canvas>
        <GestureDetector gesture={gesture}>
          <View style={StyleSheet.absoluteFill} collapsable={false}>
            <View accessible accessibilityRole="header" accessibilityLabel={`${COPY.title}. ${COPY.sub}. Wallet ${ui.wallet} coins.`}
              style={[styles.a11y, { left: sx(16), top: sy(8), width: 240 * lay.s, height: 64 * lay.s }]} />
            <View
              accessible
              accessibilityRole="button"
              accessibilityLabel={ui.phase === PHASE.reward ? `${COPY.prize} ${COPY.unit}. ${COPY.collect}` : 'Gumball machine handle'}
              accessibilityHint={ui.phase === PHASE.reward ? 'Adds the coins to your wallet.' : 'Turns the handle a quarter. Four turns dispense today’s capsule.'}
              onAccessibilityTap={turn}
              style={[styles.a11y, { left: sx(STAGE_PLATE.x - 60), top: sy(STAGE_PLATE.y - 60), width: 120 * lay.s, height: 120 * lay.s }]}
            />
          </View>
        </GestureDetector>
      </View>
    </GestureHandlerRootView>
  );
}

const STAGE_PLATE = { x: 201, y: LAYOUT.plate.y };

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: COLOR.wallTop },
  a11y: { position: 'absolute' },
});
