import { Canvas, Picture, Skia, useFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { drawScene, type Geo, makeRes, panelK, type Res } from './cpf-draw';
import { type Driver, FLY, type Frame, frameAt, frameOf, makeDriver, MAX_SCROLL, push, replay, stepDriver, takeOver } from './cpf-motion';
import { C, CATS, MAX_PICKS, REF_W, S, UI } from './cpf-theme';
import { type Cue, useCpfAudio } from './use-cpf-audio';

/*
 Cashback Pick Five — choosing this month's cashback categories, five of nine.

 Look (the pin, two screens of a recording): "Choose 5 of 9 categories for June" under the brand's bar,
 a lavender banner for a points raffle, "With your card · Pay" and a white list of categories, each a
 coloured round icon, a percentage and a name, and a checkbox; a grey "Choose" button, which becomes a
 panel with a switch and a black "Choose 1 of 5" once something is picked.

 Motion (ours): the button carries a tray of five slots. A pick ticks its box with a pop and the
 category's icon jumps out of its row along a high arc, turning, and drops into the next slot, which
 squashes as it catches it; "Choose N of 5" rolls its number; the panel springs up with the first pick.
 A sixth pick is refused: its box and the button shake, its edge flashes red. Dropping one sends its
 icon back to its row and the later ones slide a slot left. When the tray is full a sheen runs across
 the button. Stars twinkle on the banner, and burst now and then.

 autoPlay: LOOP = 9 s (cpf-motion.ts); frame 9 s equals frame 0.
 Touch: tap a row (or its box) to pick or drop it; drag the page to scroll (momentum); tap the banner's
 box for a sparkle. 2 s after the last touch the picks go back one by one and the loop resumes.
 Reduced motion: no flights; picks land in the tray at once.
*/

type Props = { autoPlay?: boolean; previewTime?: number };

let debugState = '';

export function CashbackPickFive({ autoPlay = false, previewTime }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);

  const regular = useFont(require('./assets/fonts/Inter-Regular.ttf'), 100);
  const medium = useFont(require('./assets/fonts/Inter-Medium.ttf'), 100);
  const semi = useFont(require('./assets/fonts/Inter-SemiBold.ttf'), 100);
  const bold = useFont(require('./assets/fonts/Inter-Bold.ttf'), 100);
  const heavy = useFont(require('./assets/fonts/Inter-ExtraBold.ttf'), 100);

  const res = useMemo<Res | null>(() => {
    if (!regular || !medium || !semi || !bold || !heavy) return null;
    const f = { regular: regular.getTypeface(), medium: medium.getTypeface(), semi: semi.getTypeface(), bold: bold.getTypeface(), heavy: heavy.getTypeface() };
    if (!f.regular || !f.medium || !f.semi || !f.bold || !f.heavy) return null;
    return makeRes({ regular: f.regular, medium: f.medium, semi: f.semi, bold: f.bold, heavy: f.heavy });
  }, [regular, medium, semi, bold, heavy]);

  const w = size?.width ?? REF_W;
  const h = size?.height ?? 874;
  const geo = useMemo<Geo>(() => ({ w, h, s: w / REF_W }), [w, h]);

  const play = useCpfAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const later = (c: Cue, ms: number) => setTimeout(() => play(c), ms);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 2) Haptics.selectionAsync().catch(() => {});
    else if (kind === 3) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning).catch(() => {});
    else Haptics.impactAsync(kind === 1 ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const pickSounds = () => {
    play('pop');
    later('drop', Math.round(FLY * 1000));
    setTimeout(() => buzz(0), Math.round(FLY * 1000));
  };

  const driver = useSharedValue<Driver>(makeDriver(0));
  const clock = useSharedValue(0);
  const frame = useSharedValue<Frame>(frameAt(0));
  const ready = res !== null && size !== null;

  useEffect(() => {
    driver.set(makeDriver(previewTime ?? 0));
    clock.set(0);
  }, [auto, reduced, ready, previewTime, driver, clock]);

  // Debug (development builds only): `globalThis.__pickFive.at(3.3)` pins the loop, `.play()`
  // resumes, `.state()` reads the driver.
  const debugT = useSharedValue(-1);
  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __pickFive?: unknown };
    g.__pickFive = { at: (t: number) => debugT.set(t), play: () => debugT.set(-1), state: () => debugState };
    return () => {
      delete g.__pickFive;
    };
  }, [debugT]);
  const mirror = (s: string) => {
    debugState = s;
  };

  const frameStep = (since: number) => {
    'worklet';
    const dt = Math.min(since / 1000, 0.05);
    const now = clock.get() + dt;
    clock.set(now);
    const d = driver.get();
    if (debugT.get() >= 0) {
      d.mode = 0;
      d.t = debugT.get();
      d.amb = debugT.get();
    } else stepDriver(d, dt, now, auto);
    const f = frameOf(d, now);
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${d.t.toFixed(2)} mode=${d.mode} picks=${f.s.picks.join(',')} scroll=${f.scroll.toFixed(0)} ev=${d.events.length} home=${d.homing}`);
    }
    frame.set(f);
  };
  const frameCb = useFrameCallback((tick) => {
    // a frame that throws must not stop the callback for good
    try {
      frameStep(tick.timeSincePreviousFrame ?? 16);
    } catch (err) {
      if (__DEV__) scheduleOnRN(mirror, `error ${String(err)}`);
    }
  }, false);

  useEffect(() => {
    frameCb.setActive(ready && active);
    return () => frameCb.setActive(false);
  }, [ready, active, frameCb]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const c = recorder.beginRecording(Skia.XYWHRect(0, 0, geo.w, geo.h));
    if (res) drawScene(c, res, geo, frame.get());
    else c.drawColor(Skia.Color(C.bg));
    return recorder.finishRecordingAsPicture();
  }, [res, geo]);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };

  const start = useSharedValue({ y: 0, scroll: 0 });

  const touch = Gesture.Pan()
    .minDistance(0)
    .enabled(previewTime === undefined)
    .onBegin((e) => {
      const d = driver.get();
      const now = clock.get();
      takeOver(d, now);
      d.v = 0;
      if (!Number.isFinite(d.scroll)) d.scroll = 0;
      start.set({ y: e.y, scroll: d.scroll });
    })
    .onUpdate((e) => {
      const d = driver.get();
      d.last = clock.get();
      const dy = e.translationY / geo.s / S;
      if (d.dragging === 0 && Math.abs(dy) > 10) d.dragging = 1;
      const next = start.get().scroll - dy;
      if (d.dragging === 1 && Number.isFinite(next)) d.scroll = Math.max(0, Math.min(MAX_SCROLL, next));
    })
    .onFinalize((e) => {
      const d = driver.get();
      const now = clock.get();
      d.last = now;
      if (d.dragging === 1) {
        d.dragging = 0;
        const v = -e.velocityY / geo.s / S;
        d.v = Number.isFinite(v) ? v : 0;
        return;
      }
      const x = e.x / geo.s / S;
      const y = e.y / geo.s / S;
      const f = frameOf(d, now);
      // below the panel (or the grey button) nothing is a row
      const k = panelK(f);
      const floor = k > 0.5 ? UI.panel.y : UI.disabled.y - 20;
      if (y >= floor || y < 222) return;
      const py = y + d.scroll;
      const Bn = UI.banner;
      if (py > Bn.y && py < Bn.y + Bn.h && x > Bn.x && x < Bn.x + Bn.w) {
        d.sparkleAt = now;
        scheduleOnRN(cue, 'sparkle');
        scheduleOnRN(buzz, 2);
        return;
      }
      const L = UI.list;
      for (let i = 0; i < CATS.length; i++) {
        const cy = L.top + i * L.pitch;
        if (Math.abs(py - cy) < L.pitch / 2 && x > L.x && x < L.x + L.w) {
          const s = replay(d.events, now);
          const picked = s.slotOf[i] >= 0;
          const at = reduced ? now - FLY - 0.4 : now;
          push(d, { t: at, k: picked ? 1 : 0, row: i, v: 0 });
          if (picked) {
            scheduleOnRN(cue, 'back');
            scheduleOnRN(buzz, 2);
          } else if (s.picks.length >= MAX_PICKS) {
            scheduleOnRN(cue, 'nope');
            scheduleOnRN(buzz, 3);
          } else scheduleOnRN(pickSounds);
          return;
        }
      }
    });

  // VoiceOver: a double tap picks the next free category.
  const pickNext = () => {
    scheduleOnUI(() => {
      const d = driver.get();
      const now = clock.get();
      takeOver(d, now);
      const s = replay(d.events, now);
      for (let i = 0; i < CATS.length; i++) {
        if (s.slotOf[i] < 0) {
          push(d, { t: now, k: 0, row: i, v: 0 });
          return;
        }
      }
    });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="dark" />
      <GestureDetector gesture={touch}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityRole="button"
          accessibilityLabel="Choose 5 of 9 cashback categories for June. 2% all purchases, 10% cafés, bars and restaurants, 3% supermarkets, 10% clothes and shoes, 10% taxi."
          accessibilityHint="Picks the next category."
          onAccessibilityTap={pickNext}
        >
          {size ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Picture picture={picture} />
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: C.bg },
});
