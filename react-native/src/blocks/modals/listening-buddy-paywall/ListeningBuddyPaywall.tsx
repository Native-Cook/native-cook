import { Canvas, Picture, Skia, useFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { drawScene, type Geo, makeRes, type Res } from './lbp-draw';
import { type Driver, type Frame, frameAt, frameOf, makeDriver, select, stepDriver, takeOver } from './lbp-motion';
import { C, DY, REF_W, S, UI } from './lbp-theme';
import { type Cue, useLbpAudio } from './use-lbp-audio';

/*
 Listening Buddy Paywall — the subscription screen of a meditation app, with its buddy listening.

 Look (the pin, a paywall): a cream sky with clouds and hills, "Subscribe now" and a line about the
 library, an orange character in headphones at the left, three plan cards (Monthly, Annual framed in
 orange under a "MOST POPULAR" band, Lifetime), two lines of terms, Restore · Terms, and a full-width
 CONTINUE bar. The buddy here is our own: a round orange pebble with a sprout, drawn in code.

 Motion (ours): the buddy listens. It sways and nods on a beat (twelve to a loop), rings pulse out of
 its headphones and a note drifts up now and then. Choosing a plan slides the orange frame to it, the
 card swells, the lines under the plans change, and the buddy's face follows: content for Monthly,
 wide-eyed with an open mouth for Lifetime, blissful (eyes shut, blushing, grinning) for Annual.
 Continue makes it hop and sends a burst of notes up from the bar.

 autoPlay: LOOP = 9 s (lbp-motion.ts); frame 9 s equals frame 0.
 Touch: tap a plan; tap CONTINUE (it darkens and its label presses to 0.96); poke the buddy (it squashes
 and hops); the close button presses. 2 s after the last touch Annual is chosen again and the loop resumes.
 Reduced motion: no sway, rings or notes; plan changes land at once.
*/

type Props = { autoPlay?: boolean; previewTime?: number };

let debugState = '';

export function ListeningBuddyPaywall({ autoPlay = false, previewTime }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);

  const regular = useFont(require('./assets/fonts/Manrope_400Regular.ttf'), 100);
  const medium = useFont(require('./assets/fonts/Manrope_500Medium.ttf'), 100);
  const semi = useFont(require('./assets/fonts/Manrope_600SemiBold.ttf'), 100);
  const bold = useFont(require('./assets/fonts/Manrope_700Bold.ttf'), 100);
  const round = useFont(require('./assets/fonts/Nunito-ExtraBold.ttf'), 100);

  const res = useMemo<Res | null>(() => {
    if (!regular || !medium || !semi || !bold || !round) return null;
    const f = { regular: regular.getTypeface(), medium: medium.getTypeface(), semi: semi.getTypeface(), bold: bold.getTypeface(), round: round.getTypeface() };
    if (!f.regular || !f.medium || !f.semi || !f.bold || !f.round) return null;
    return makeRes({ regular: f.regular, medium: f.medium, semi: f.semi, bold: f.bold, round: f.round });
  }, [regular, medium, semi, bold, round]);

  const w = size?.width ?? REF_W;
  const h = size?.height ?? 874;
  const geo = useMemo<Geo>(() => ({ w, h, s: w / REF_W }), [w, h]);

  const play = useLbpAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 2) Haptics.selectionAsync().catch(() => {});
    else if (kind === 3) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    else Haptics.impactAsync(kind === 1 ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const driver = useSharedValue<Driver>(makeDriver(0));
  const clock = useSharedValue(0);
  const frame = useSharedValue<Frame>(frameAt(0));
  const ready = res !== null && size !== null;

  useEffect(() => {
    driver.set(makeDriver(previewTime ?? 0));
    clock.set(0);
  }, [auto, reduced, ready, previewTime, driver, clock]);

  // Debug (development builds only): `globalThis.__buddyPaywall.at(3.4)` pins the loop, `.play()`
  // resumes, `.state()` reads the driver.
  const debugT = useSharedValue(-1);
  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __buddyPaywall?: unknown };
    g.__buddyPaywall = { at: (t: number) => debugT.set(t), play: () => debugT.set(-1), state: () => debugState };
    return () => {
      delete g.__buddyPaywall;
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
    if (reduced) {
      f.amb = 0;
      f.selAt = 10;
      f.go = -1;
      f.poke = -1;
    }
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${d.t.toFixed(2)} mode=${d.mode} plan=${d.plan} go=${f.go.toFixed(2)}`);
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
    else c.drawColor(Skia.Color(C.sky));
    return recorder.finishRecordingAsPicture();
  }, [res, geo]);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };

  const toPin = (x: number, y: number) => {
    'worklet';
    return { x: x / geo.s / S, y: (y / geo.s - DY) / S };
  };

  const touch = Gesture.Pan()
    .minDistance(0)
    .enabled(previewTime === undefined)
    .onBegin((e) => {
      const d = driver.get();
      const now = clock.get();
      takeOver(d, now);
      const p = toPin(e.x, e.y);
      d.released = 0;
      if (p.y > UI.bar.y) {
        d.pressing = 1;
        d.pressAt = now;
      } else if (Math.hypot(p.x - UI.close.x, p.y - UI.close.y) < UI.close.r + 14) {
        d.pressing = 2;
        d.pressAt = now;
      }
    })
    .onFinalize((e) => {
      const d = driver.get();
      const now = clock.get();
      d.last = now;
      const p = toPin(e.x, e.y);
      const was = d.pressing;
      if (was > 0) {
        d.released = was;
        d.releaseAt = now;
        d.pressing = 0;
      }
      if (was === 1) {
        if (p.y < UI.bar.y - 20) return;
        d.goAt = now;
        scheduleOnRN(cue, 'notes');
        scheduleOnRN(buzz, 3);
        return;
      }
      if (was === 2) {
        scheduleOnRN(buzz, 0);
        return;
      }
      const K = UI.cards;
      if (p.y > K.y - 14 && p.y < K.y + K.h + 14) {
        for (let i = 0; i < 3; i++) {
          if (p.x > K.x[i] - 12 && p.x < K.x[i] + K.w + 12 && select(d, i, now)) {
            scheduleOnRN(cue, i === 0 ? 'pick1' : i === 2 ? 'pick2' : 'pick3');
            scheduleOnRN(buzz, 2);
          }
        }
        return;
      }
      // the buddy (its body sits at the left of the sky)
      if (p.x < 300 && p.y > 380 && p.y < UI.white) {
        d.pokeAt = now;
        scheduleOnRN(cue, 'boop');
        scheduleOnRN(buzz, 0);
      }
    });

  // VoiceOver: a double tap chooses the next plan.
  const nextPlan = () => {
    scheduleOnUI(() => {
      const d = driver.get();
      const now = clock.get();
      takeOver(d, now);
      select(d, (d.plan + 1) % 3, now);
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
          accessibilityLabel="Subscribe now. Monthly 12,99 euros per month. Annual 94,99 euros per year, most popular, selected. Lifetime 449,99 euros, one payment. Continue."
          accessibilityHint="Chooses the next plan."
          onAccessibilityTap={nextPlan}
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
  screen: { flex: 1, backgroundColor: C.sky },
});
