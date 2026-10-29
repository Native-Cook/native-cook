import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, Platform, StyleSheet, useWindowDimensions, View } from 'react-native';
import { Canvas, Picture, Skia, matchFont, useFont } from '@shopify/react-native-skia';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';

import {
  HAND_FONT,
  PEACH,
  coversTop,
  drawScene,
  hexColor,
  makeChrome,
  makeGlassBack,
  makeGlassBody,
  makeGlassInterior,
  makeGlassLines,
  makeGrain,
  makeGrainLayer,
  makeLayout,
  makePot,
  makeTitle,
  type Art,
  type Fonts,
} from './floodArt';
import { beginTouch, endTouch, initialTouch, moveTouch, restsAfter, stepTouch, stillFrame, touchFrame } from './floodMotion';

/*
 Moka Flood Welcome — the first onboarding step of an invented morning-routine app, Brewday, as a
 flat illustration on peach: a crimson moka pot tilts and pours into a faceted glass, the coffee
 rises with a wobbling meniscus, domes over the rim, spills down the walls and floods the whole
 screen from the bottom; the welcome is written on the coffee in cream handwriting, then the flood
 drains.

 Loop (LOOP = 9.5 s): rest → the pot tilts (1.0 s) and pours (1.2) → the glass fills (1.72–3.6) →
 domes, spills and puddles (3.6–4.7) → the flood rises (4.7–6.5) as the pot rights itself and the
 stream breaks (5.15–5.4) → the title is written (6.55–7.55) → the flood drains (8.1–9.0) → rest.
 Hold to pour, drag left or down to pour harder; the loop resumes 2 s after the last touch. Reduced
 motion: the coffee page with its copy, still.
 */

const SANS = Platform.OS === 'ios' ? 'System' : 'sans-serif';

function haptic() {
  if (process.env.EXPO_OS === 'web') return;
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type Props = {
  /** Plays the pour and flood on a loop of LOOP s. */
  autoPlay?: boolean;
};

export function MokaFloodWelcome({ autoPlay = false }: Props) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const [active, setActive] = useState(AppState.currentState === 'active');
  const [awake, setAwake] = useState(false);
  const [covered, setCovered] = useState(reduced);
  const wake = useCallback(() => setAwake(true), []);
  const sleep = useCallback(() => setAwake(false), []);
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const k = width / 393;
  const layout = useMemo(() => makeLayout(height / k, insets.top / k, insets.bottom / k), [height, k, insets.top, insets.bottom]);

  const hand28 = useFont(HAND_FONT, 28);
  const hand22 = useFont(HAND_FONT, 22);
  const hand60 = useFont(HAND_FONT, 60);
  const fonts = useMemo<Fonts | null>(() => {
    if (!hand28 || !hand22 || !hand60) return null;
    return {
      hand28,
      hand22,
      hand60,
      semi15: matchFont({ fontFamily: SANS, fontSize: 15, fontWeight: '600' }),
      semi17: matchFont({ fontFamily: SANS, fontSize: 17, fontWeight: '600' }),
      sans16: matchFont({ fontFamily: SANS, fontSize: 16, fontWeight: '400' }),
    };
  }, [hand28, hand22, hand60]);
  const pot = useMemo(() => makePot(), []);
  const grain = useMemo(() => makeGrain(), []);
  const art = useMemo<Art>(
    () => ({
      pot,
      glassBack: makeGlassBack(layout),
      glassBody: makeGlassBody(layout),
      glassLines: makeGlassLines(layout),
      interior: makeGlassInterior(layout),
      grainPeach: makeGrainLayer(layout, grain, 0.06),
      grainCoffee: makeGrainLayer(layout, grain, 0.07),
      chromePeach: fonts ? makeChrome(layout, fonts, false) : null,
      chromeCoffee: fonts ? makeChrome(layout, fonts, true) : null,
      title: fonts ? makeTitle(layout, fonts) : null,
    }),
    [pot, grain, layout, fonts],
  );

  const clock = useSharedValue(0);
  const startedAt = useSharedValue(-1);
  const clockBase = useSharedValue(0);
  const touch = useSharedValue(initialTouch());
  const asleep = useSharedValue(false);
  const coveredNow = useSharedValue(reduced);
  const pressX = useSharedValue(0);
  const pressY = useSharedValue(0);
  const owned = useSharedValue(false);

  const tick = useFrameCallback((info) => {
    if (startedAt.get() < 0) {
      startedAt.set(info.timestamp);
      clockBase.set(clock.get());
    }
    const now = clockBase.get() + (info.timestamp - startedAt.get()) / 1000;
    clock.set(now);
    let s = stepTouch(touch.get(), now, autoPlay);
    if (s.hapticAt >= 0 && s.hapticAt <= now) {
      s = { ...s, hapticAt: -1 };
      scheduleOnRN(haptic);
    }
    touch.set(s);
    const cover = coversTop(layout, touchFrame(s, now, autoPlay));
    if (cover !== coveredNow.get()) {
      coveredNow.set(cover);
      scheduleOnRN(setCovered, cover);
    }
    if (!autoPlay && !asleep.get() && !s.touching && now > restsAfter(s) + 0.1) {
      asleep.set(true);
      scheduleOnRN(sleep);
    }
  }, false);
  const running = !reduced && active && (autoPlay || awake);
  useEffect(() => {
    startedAt.set(-1);
    tick.setActive(running);
    return () => tick.setActive(false);
  }, [running, tick, startedAt]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    const frame = reduced ? stillFrame() : touchFrame(touch.get(), clock.get(), autoPlay);
    canvas.save();
    canvas.scale(k, k);
    drawScene(canvas, layout, art, frame);
    canvas.restore();
    return recorder.finishRecordingAsPicture();
  });

  const ctaTop = layout.ctaTop;
  const gesture = useMemo(
    () =>
      Gesture.Manual()
        .onTouchesDown((e, manager) => {
          if (owned.get()) return;
          const p = e.allTouches[0];
          const y0 = p.y / k;
          if (y0 > ctaTop - 12) {
            // The Get started pill.
            const x0 = p.x / k;
            if (x0 >= 24 && x0 <= 369 && y0 < ctaTop + 56) scheduleOnRN(haptic);
            manager.fail();
            return;
          }
          if (reduced) return;
          manager.activate();
          owned.set(true);
          pressX.set(p.x);
          pressY.set(p.y);
          touch.set(beginTouch(touch.get(), clock.get(), autoPlay));
          if (!autoPlay) {
            asleep.set(false);
            scheduleOnRN(wake);
          }
        })
        .onTouchesMove((e) => {
          if (!owned.get()) return;
          const p = e.allTouches[0];
          touch.set(moveTouch(touch.get(), clock.get(), (p.x - pressX.get()) / k, (p.y - pressY.get()) / k));
        })
        .onTouchesUp((e, manager) => {
          if (e.numberOfTouches > 0 || !owned.get()) return;
          owned.set(false);
          manager.end();
          touch.set(endTouch(touch.get(), clock.get()));
        })
        .onTouchesCancelled(() => {
          if (!owned.get()) return;
          owned.set(false);
          touch.set(endTouch(touch.get(), clock.get()));
        }),
    [reduced, autoPlay, k, ctaTop, touch, clock, wake, asleep, pressX, pressY, owned],
  );

  return (
    <View style={[styles.root, { backgroundColor: hexColor(PEACH) }]}>
      <StatusBar style={covered || reduced ? 'light' : 'dark'} />
      <GestureDetector gesture={gesture}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityRole="button"
          accessibilityLabel="Brewday. Brew your morning routine! Brewday lines up your first cup, your stretch and the plan for today. Moka pot."
          accessibilityHint="Hold to pour coffee into the glass"
        >
          <Canvas style={StyleSheet.absoluteFill}>
            <Picture picture={picture} />
          </Canvas>
        </View>
      </GestureDetector>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
});
