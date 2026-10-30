import { Canvas, Picture, Skia, useFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { drawScene, type Geo, makeRes, type Res } from './aft-draw';
import { ACT, type Driver, type Frame, frameAt, frameOf, ICONS, makeDriver, stepDriver, T, takeOver } from './aft-motion';
import { C, REF_W, S, UI } from './aft-theme';
import { type Cue, useAftAudio } from './use-aft-audio';

/*
 AI Feature Tour — the "what the assistant can do" screen of a health app's onboarding.

 Look (the pin, an app screen): a lavender wash at the top, "Halden Intelligence can:", five rows of a
 glossy icon tile with a title and two lines (check ins, trends, workout templates, food, a training
 plan), a sixth row fading under a black Continue pill.

 Motion (ours): each icon does its own job, one after another: the calendar's check-in hops across
 its blue days, the trend line is erased and drawn again with its dot riding the tip and the dashed
 forecast following, the chosen workout card slides out and its check redraws, a scan line sweeps the
 avocado photo and sparkles mark what it found, the plan's lines are written in again with their
 marks. Continue sends the five icons along their own arcs into the button, where they become one
 sparkle; then they pop back home.

 autoPlay: LOOP = 9 s (aft-motion.ts); frame 9 s equals frame 0.
 Touch: tap a row to play its icon's act; Continue presses in and sends the icons. 2 s after the last
 touch, once everything is still, the loop resumes.
 Reduced motion: no loop; a tap shows the act's end at once; Continue only presses.
*/

type Props = { autoPlay?: boolean; previewTime?: number };

let debugState = '';

export function AiFeatureTour({ autoPlay = false, previewTime }: Props) {
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

  const res = useMemo<Res | null>(() => {
    if (!regular || !medium || !semi || !bold) return null;
    const f = { regular: regular.getTypeface(), medium: medium.getTypeface(), semi: semi.getTypeface(), bold: bold.getTypeface() };
    if (!f.regular || !f.medium || !f.semi || !f.bold) return null;
    return makeRes({ regular: f.regular, medium: f.medium, semi: f.semi, bold: f.bold });
  }, [regular, medium, semi, bold]);

  const w = size?.width ?? REF_W;
  const h = size?.height ?? 874;
  const geo = useMemo<Geo>(() => ({ w, h, s: w / REF_W }), [w, h]);

  const play = useAftAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const later = (c: Cue, ms: number) => setTimeout(() => play(c), ms);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 2) Haptics.selectionAsync().catch(() => {});
    else if (kind === 3) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    else Haptics.impactAsync(kind === 1 ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const sendSounds = () => {
    play('whoosh');
    later('sparkle', Math.round((T.sparkle - T.press) * 1000));
    setTimeout(() => buzz(3), Math.round((T.sparkle - T.press) * 1000));
  };

  const driver = useSharedValue<Driver>(makeDriver(0));
  const clock = useSharedValue(0);
  const frame = useSharedValue<Frame>(frameAt(0));
  const ready = res !== null && size !== null;

  useEffect(() => {
    driver.set(makeDriver(previewTime ?? 0));
    clock.set(0);
  }, [auto, reduced, ready, previewTime, driver, clock]);

  // Debug (development builds only): `globalThis.__featureTour.at(7.4)` pins the loop, `.play()`
  // resumes, `.state()` reads the driver.
  const debugT = useSharedValue(-1);
  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __featureTour?: unknown };
    g.__featureTour = { at: (t: number) => debugT.set(t), play: () => debugT.set(-1), state: () => debugState };
    return () => {
      delete g.__featureTour;
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
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${d.t.toFixed(2)} mode=${d.mode} fly=${d.flyAt > 0 ? (now - d.flyAt).toFixed(2) : '-'}`);
    }
    frame.set(frameOf(d, now));
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

  const onButton = (x: number, y: number) => {
    'worklet';
    const B = UI.button;
    return x > B.x - 10 && x < B.x + B.w + 10 && y > B.y - 10 && y < B.y + B.h + 10;
  };

  const touch = Gesture.Pan()
    .minDistance(0)
    .enabled(previewTime === undefined)
    .onBegin((e) => {
      const d = driver.get();
      const now = clock.get();
      takeOver(d, now);
      const x = e.x / geo.s / S;
      const y = e.y / geo.s / S;
      if (onButton(x, y)) {
        d.pressing = 1;
        d.pressAt = now;
      }
    })
    .onFinalize((e) => {
      const d = driver.get();
      const now = clock.get();
      d.last = now;
      const x = e.x / geo.s / S;
      const y = e.y / geo.s / S;
      if (d.pressing === 1) {
        d.pressing = 0;
        if (!onButton(x, y)) return;
        scheduleOnRN(cue, 'tap');
        if (reduced || now - d.flyAt < 9 - T.press) return;
        d.flyAt = now;
        scheduleOnRN(sendSounds);
        return;
      }
      const R = UI.rows;
      for (let i = 0; i < ICONS; i++) {
        const ty = R.y0 + i * R.pitch;
        if (y > ty - 70 && y < ty + 130) {
          if (now - d.actAt[i] < ACT) return;
          d.actAt[i] = reduced ? now - ACT : now;
          scheduleOnRN(cue, 'tick');
          scheduleOnRN(buzz, 2);
          return;
        }
      }
    });

  // VoiceOver: a double tap continues.
  const continueForAccessibility = () => {
    scheduleOnUI(() => {
      const d = driver.get();
      const now = clock.get();
      takeOver(d, now);
      d.flyAt = now;
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
          accessibilityLabel="Halden Intelligence can: create check ins, understand your trends, create workout templates, log food, create a training plan. Continue."
          accessibilityHint="Continues."
          onAccessibilityTap={continueForAccessibility}
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
