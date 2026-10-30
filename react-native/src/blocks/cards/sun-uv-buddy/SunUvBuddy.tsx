import { Canvas, Picture, Skia, useFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { drawScene, type Geo, type Live, makeRes, type Res } from './suv-draw';
import { type Driver, makeDriver, pickDay, REST, sizeFor, stepDriver, takeOver, uvAt } from './suv-motion';
import { C, DAYS, REF_H, REF_W, UI } from './suv-theme';
import { type Cue, useSuvAudio } from './use-suv-audio';

/*
 Sun UV Buddy — the UV forecast sheet of a weather app.

 Look (the pin, a screen recording): a yellow sheet over the map with "UV index", the place, five
 day chips, a scalloped sun lounging on a deck chair, "From 12:00 it will be moderate" with the
 index in a yellow dot, a line of advice with sunscreen and a cap, and a stem from the sheet down
 to the hour it talks about in a row of hourly bars.

 Motion (ours): the sun is the forecast. Slide the stem along the hours and the sun shrinks and
 dozes off toward the evening (eyes to slits, a "z"), swells back like a jelly at noon; switch the
 day and the bars spring to the new forecast one after another, and on a strong day the sun puts on
 its shades and its rays come out while the sheet's sky warms. The stem bends as it is dragged.

 autoPlay: LOOP = 9 s (suv-motion.ts); frame 9 s equals frame 0.
 Touch: drag along the bars (or the stem) to pick an hour (a tick per hour, it settles on the
 nearest); tap a day chip; tap the sun to squish it. Autoplay resumes 2 s after the last touch.
 Reduced motion: no springs or wobble; the sun and the bars change at once, the words fade.
*/

type Props = { autoPlay?: boolean; previewTime?: number };

let debugState = '';
const PITCH = UI.bars.w + UI.bars.gap;

export function SunUvBuddy({ autoPlay = false, previewTime }: Props) {
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
  const round = useFont(require('./assets/fonts/Nunito-ExtraBold.ttf'), 100);

  const res = useMemo<Res | null>(() => {
    if (!regular || !medium || !semi || !round) return null;
    const f = { regular: regular.getTypeface(), medium: medium.getTypeface(), semi: semi.getTypeface(), round: round.getTypeface() };
    if (!f.regular || !f.medium || !f.semi || !f.round) return null;
    return makeRes({ regular: f.regular, medium: f.medium, semi: f.semi, round: f.round });
  }, [regular, medium, semi, round]);

  const w = size?.width ?? REF_W;
  const h = size?.height ?? REF_H;
  const geo = useMemo<Geo>(() => ({ w, h, s: w / REF_W, dy: 0 }), [w, h]);

  const play = useSuvAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 2) Haptics.selectionAsync().catch(() => {});
    else Haptics.impactAsync(kind === 1 ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const driver = useSharedValue<Driver>(makeDriver(0));
  const clock = useSharedValue(0);
  const chipAt = useSharedValue(-10);
  const shadesOn = useSharedValue(0);
  const live = useSharedValue<Live>({ amb: 0, hour: REST.hour, hourV: 0, from: 2, to: 2, tau: 10, size: 1, squish: -1, dayPress: 0 });
  const ready = res !== null && size !== null;

  useEffect(() => {
    driver.set(makeDriver(previewTime ?? 0));
    clock.set(0);
  }, [auto, reduced, ready, previewTime, driver, clock]);

  // Debug (development builds only): `globalThis.__sunUv.at(2.4)` pins the loop, `.play()`
  // resumes, `.state()` reads the driver.
  const debugT = useSharedValue(-1);
  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __sunUv?: unknown };
    g.__sunUv = { at: (t: number) => debugT.set(t), play: () => debugT.set(-1), state: () => debugState };
    return () => {
      delete g.__sunUv;
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
    let base = now;
    const pinned = debugT.get() >= 0;
    if (pinned) {
      d.mode = 0;
      d.t = debugT.get();
      d.amb = debugT.get();
      base = stepDriver(d, 0, now, false);
    } else base = stepDriver(d, dt, now, auto);
    const tau = base - d.at;
    if (pinned || reduced) {
      // no spring: the size the hour asks for
      d.size = sizeFor(uvAt(d.hour, d.from, d.to, reduced ? 10 : tau));
      d.sizeV = 0;
    }
    // the shades go on when a finger brings a strong sun
    const strong = uvAt(d.hour, d.from, d.to, tau) > 4.2 ? 1 : 0;
    if (strong !== shadesOn.get()) {
      shadesOn.set(strong);
      if (strong === 1 && d.mode === 2) scheduleOnRN(cue, 'shades');
    }
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${d.t.toFixed(2)} mode=${d.mode} hour=${d.hour.toFixed(2)} day=${d.from}>${d.to} size=${d.size.toFixed(3)}`);
    }
    live.set({
      amb: reduced ? 0 : d.amb,
      hour: d.hour,
      hourV: reduced ? 0 : d.hourV,
      from: d.from,
      to: d.to,
      tau: reduced ? 10 : tau,
      size: d.size,
      squish: d.squishAt < 0 || reduced ? -1 : now - d.squishAt,
      dayPress: Math.max(0, 1 - (now - chipAt.get()) / 0.25),
    });
  };
  const frameCb = useFrameCallback((info) => {
    frameStep(info.timeSincePreviousFrame ?? 16);
  }, false);

  useEffect(() => {
    // under reduced motion the frame callback still runs, to apply touches, but nothing wobbles
    frameCb.setActive(ready && active);
    return () => frameCb.setActive(false);
  }, [ready, active, frameCb]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const c = recorder.beginRecording(Skia.XYWHRect(0, 0, geo.w, geo.h));
    if (res) drawScene(c, res, geo, live.get());
    else c.drawColor(Skia.Color(C.page));
    return recorder.finishRecordingAsPicture();
  }, [res, geo]);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };

  const zone = useSharedValue(0); // 1 hours, 2 chips, 3 sun
  const startX = useSharedValue(0);
  const lastHour = useSharedValue(0);
  const lastX = useSharedValue(0);
  const lastT = useSharedValue(0);

  const hourAt = (x: number) => {
    'worklet';
    return Math.max(0, Math.min(10, (x - UI.bars.x0 - UI.bars.w / 2) / PITCH));
  };

  const touch = Gesture.Pan()
    .minDistance(0)
    .enabled(previewTime === undefined)
    .onBegin((e) => {
      const d = driver.get();
      const now = clock.get();
      const x = e.x / geo.s;
      const y = e.y / geo.s;
      takeOver(d, now);
      startX.set(x);
      lastX.set(x);
      lastT.set(now);
      const S = UI.sun;
      if (y > UI.sheet.bottom - 8 && y < UI.bars.labelY + 20) {
        zone.set(1);
        d.dragging = 1;
        d.hourV = 0;
        d.hour = hourAt(x);
        lastHour.set(Math.round(d.hour));
        scheduleOnRN(cue, 'tick');
        scheduleOnRN(buzz, 2);
      } else if (Math.abs(y - UI.chips.y) < 24) zone.set(2);
      else if (Math.hypot(x - S.x, y - S.y) < S.r * 1.15) zone.set(3);
      else zone.set(0);
    })
    .onUpdate((e) => {
      if (zone.get() !== 1) return;
      const d = driver.get();
      const now = clock.get();
      d.last = now;
      const x = e.x / geo.s;
      const dtm = Math.max(1 / 240, now - lastT.get());
      const next = hourAt(x);
      d.hourV = d.hourV * 0.6 + ((next - d.hour) / dtm) * 0.4;
      d.hour = next;
      lastX.set(x);
      lastT.set(now);
      const step = Math.round(next);
      if (step !== lastHour.get()) {
        lastHour.set(step);
        scheduleOnRN(cue, 'tick');
        scheduleOnRN(buzz, 2);
      }
    })
    .onFinalize((e) => {
      const d = driver.get();
      const now = clock.get();
      d.last = now;
      const x = e.x / geo.s;
      const tap = Math.abs(x - startX.get()) < 10;
      if (zone.get() === 1) {
        d.dragging = 0;
        d.hourAim = Math.max(0, Math.min(10, Math.round(d.hour + d.hourV * 0.08)));
        if (reduced) {
          d.hour = d.hourAim;
          d.hourV = 0;
        }
        return;
      }
      if (!tap) return;
      if (zone.get() === 2) {
        const ch = UI.chips;
        for (let i = 0; i < 5; i++) {
          if (x > ch.x[i] - 4 && x < ch.x[i] + ch.w[i] + 4) {
            if (pickDay(d, i, now)) {
              chipAt.set(now);
              scheduleOnRN(buzz, 0);
            }
            break;
          }
        }
      } else if (zone.get() === 3 && !reduced) {
        d.squishAt = now;
        d.sizeV -= 1.6; // a poke: the jelly gives, then springs back
        scheduleOnRN(cue, 'boing');
        scheduleOnRN(buzz, 1);
      }
    });

  // VoiceOver: a double tap moves to the next day.
  const nextDay = () => {
    scheduleOnUI(() => {
      const d = driver.get();
      takeOver(d, clock.get());
      pickDay(d, (d.to + 1) % DAYS.length, clock.get());
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
          accessibilityLabel="UV index, Thursday. From 12:00 it will be moderate, 3. Sunscreen and a cap won't hurt."
          accessibilityHint="Double tap for the next day."
          onAccessibilityTap={nextDay}
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
  screen: { flex: 1, backgroundColor: C.page },
});
