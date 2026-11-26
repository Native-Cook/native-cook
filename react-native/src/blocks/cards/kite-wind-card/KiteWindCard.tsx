import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, Platform, StyleSheet, useWindowDimensions, View } from 'react-native';
import type { AccessibilityActionEvent } from 'react-native';
import { Canvas, Picture, Skia, useTypeface, useImage } from '@shopify/react-native-skia';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';

import {
  CLOUDS,
  INTER_DISPLAY_BOLD,
  INTER_MEDIUM,
  INTER_SEMIBOLD,
  KITE,
  cardHit,
  cardTop,
  drawCard,
  drawClouds,
  drawHeadline,
  drawKite,
  drawSky,
  kiteHit,
  makeLayout,
} from './kiteArt';
import type { Fonts, Layout } from './kiteArt';
import {
  HOURS,
  REST_P,
  dragCard,
  dragKite,
  frameAt,
  goTo,
  grabCard,
  grabKite,
  hourLabel,
  initialTouch,
  releaseCard,
  releaseKite,
  stepTouch,
  takeOver,
  touchP,
  windAt,
} from './kiteMotion';

/*
 Kite Wind Card — a wind forecast for flying a kite: a big number in km/h over a saturated blue sky,
 a yellow kite on its line with a tail of bows, clouds below, and a card with today's hours. Scrub the
 hours and the wind changes: the kite tugs and sways harder, its tail waves faster, its line pulls
 straighter, and the number counts through.

 Loop (LOOP = 8 s): rest at 12 pm, 18 km/h (0–1.2) → scrub to 3 pm, 26 km/h (1.2–3.2) → hold,
 Strong gusts (3.2–4.6) → back to 12 pm (4.6–6.4) → rest (6.4–8.0, the t = 0 frame).
 Touch: drag the hour card (31.4 pt per hour, snaps to the nearest hour on release); drag the kite
 (it pulls up to 30 pt on its line, then springs back). 2 s after the last touch it eases back to
 12 pm and rejoins the loop where it rests. Reduce Motion: kite, tail and clouds still; the number
 and card change without animation.
*/

/** A selection tick for each hour passed. On Android expo-haptics' impact is a weak raw pulse, so ask the system first. */
async function hourHaptic() {
  if (process.env.EXPO_OS === 'web') return;
  const light = () => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
  try {
    if (Platform.OS === 'android') {
      await Haptics.performAndroidHapticsAsync(Haptics.AndroidHaptics.Segment_Tick).catch(light);
    } else {
      await Haptics.selectionAsync();
    }
  } catch {}
}

type Props = {
  /** Plays the hour scrub on a loop of LOOP s. */
  autoPlay?: boolean;
};

export function KiteWindCard({ autoPlay = false }: Props) {
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [active, setActive] = useState(AppState.currentState === 'active');
  const [hourIndex, setHourIndex] = useState(REST_P);
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const layout: Layout = useMemo(() => makeLayout(width, height), [width, height]);
  const kite = useImage(KITE);
  const clouds = useImage(CLOUDS);
  // Typefaces, not fonts: Skia 2.6.2's font.getTypeface() returns the typeface without a reference, so each wrapper frees it once when collected.
  const display = useTypeface(INTER_DISPLAY_BOLD);
  const semibold = useTypeface(INTER_SEMIBOLD);
  const medium = useTypeface(INTER_MEDIUM);
  const fonts: Fonts | null = useMemo(() => {
    if (!display || !semibold || !medium) return null;
    return {
      eyebrow: Skia.Font(semibold, 13),
      number: Skia.Font(display, 132),
      unit: Skia.Font(display, 24),
      note: Skia.Font(medium, 16),
      today: Skia.Font(semibold, 15),
      cardValue: Skia.Font(medium, 13),
      hour: Skia.Font(medium, 11),
    };
  }, [display, semibold, medium]);

  const clock = useSharedValue(0);
  const startedAt = useSharedValue(-1);
  const clockBase = useSharedValue(0);
  const touch = useSharedValue(initialTouch());
  const lastHour = useSharedValue(REST_P);
  const pressX = useSharedValue(0);
  const pressY = useSharedValue(0);
  const onCard = useSharedValue(false);
  const onKite = useSharedValue(false);

  const tick = useFrameCallback((info) => {
    if (startedAt.get() < 0) {
      startedAt.set(info.timestamp);
      clockBase.set(clock.get());
    }
    const now = clockBase.get() + (info.timestamp - startedAt.get()) / 1000;
    clock.set(now);
    const s = touch.get();
    const next = stepTouch(s, now, autoPlay, reduced);
    if (next) touch.set(next);
    const cur = next ?? s;
    const h = Math.round(frameAt(cur, now, reduced).p);
    if (h !== lastHour.get()) {
      lastHour.set(h);
      scheduleOnRN(setHourIndex, h);
      if (cur.manual && cur.dragging) scheduleOnRN(hourHaptic);
    }
  }, false);
  const running = active && !reduced;
  useEffect(() => {
    startedAt.set(-1);
    tick.setActive(running);
    return () => tick.setActive(false);
  }, [running, tick, startedAt]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    const frame = frameAt(touch.get(), clock.get(), reduced);
    drawSky(canvas, layout);
    drawKite(canvas, layout, kite, frame);
    drawClouds(canvas, layout, clouds, frame);
    if (fonts) {
      drawHeadline(canvas, layout, fonts, frame);
      drawCard(canvas, layout, fonts, frame);
    }
    return recorder.finishRecordingAsPicture();
  });

  /** One hour up or down (the adjustable element's increment and decrement). */
  const stepHour = useCallback(
    (delta: number) => {
      const c = clock.get();
      const s = takeOver(touch.get(), c, reduced);
      const to = Math.min(9, Math.max(0, Math.round(touchP(s, c)) + delta));
      touch.set(goTo(s, to, c, reduced));
      if (reduced) {
        lastHour.set(to);
        setHourIndex(to);
      }
    },
    [clock, touch, reduced, lastHour],
  );

  const onAction = useCallback(
    (event: AccessibilityActionEvent) => {
      if (event.nativeEvent.actionName === 'increment') stepHour(1);
      if (event.nativeEvent.actionName === 'decrement') stepHour(-1);
    },
    [stepHour],
  );

  const gesture = useMemo(
    () =>
      Gesture.Manual()
        .onTouchesDown((e, manager) => {
          manager.activate();
          const pt = e.allTouches[0];
          const c = clock.get();
          pressX.set(pt.x);
          pressY.set(pt.y);
          let s = takeOver(touch.get(), c, reduced);
          const frame = frameAt(s, c, reduced);
          onCard.set(false);
          onKite.set(false);
          if (cardHit(layout, pt.x, pt.y)) {
            onCard.set(true);
            s = grabCard(s, c);
          } else if (!reduced && kiteHit(layout, frame, pt.x, pt.y)) {
            onKite.set(true);
            s = grabKite(s, c);
          }
          touch.set(s);
        })
        .onTouchesMove((e) => {
          const pt = e.allTouches[0];
          const c = clock.get();
          const dx = pt.x - pressX.get();
          const dy = pt.y - pressY.get();
          if (onCard.get()) touch.set(dragCard(touch.get(), dx, c));
          else if (onKite.get()) touch.set(dragKite(touch.get(), dx, dy, c));
        })
        .onTouchesUp((e, manager) => {
          if (e.numberOfTouches > 0) return;
          manager.end();
          const c = clock.get();
          let s = touch.get();
          if (onCard.get()) s = releaseCard(s, c, reduced);
          else if (onKite.get()) s = releaseKite(s, c);
          if (reduced) scheduleOnRN(setHourIndex, Math.round(touchP(s, c)));
          touch.set(s);
          onCard.set(false);
          onKite.set(false);
        })
        .onTouchesCancelled(() => {
          const c = clock.get();
          let s = touch.get();
          if (onCard.get()) s = releaseCard(s, c, reduced);
          else if (onKite.get()) s = releaseKite(s, c);
          touch.set(s);
          onCard.set(false);
          onKite.set(false);
        }),
    [layout, touch, clock, reduced, pressX, pressY, onCard, onKite],
  );

  const hour = HOURS[hourIndex];
  const wind = windAt(hourIndex);
  const top = cardTop(layout);

  return (
    <View style={styles.root}>
      <StatusBar style="light" />
      <GestureDetector gesture={gesture}>
        <View style={StyleSheet.absoluteFill}>
          <Canvas style={StyleSheet.absoluteFill}>
            <Picture picture={picture} />
          </Canvas>
          <View
            accessible
            accessibilityRole="header"
            accessibilityLabel={`Harbour Park, north-east wind, ${Math.round(wind)} kilometres per hour`}
            style={[styles.a11y, { left: layout.ox + 26, top: 70 * layout.fy, width: 300, height: 220 * layout.fy }]}
          />
          <View
            accessible
            accessibilityRole="adjustable"
            accessibilityLabel="Wind"
            accessibilityValue={{
              text: `${hourLabel(hour)}, ${Math.round(wind)} kilometres per hour, gusts ${Math.round(wind * 1.4)}`,
            }}
            accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
            onAccessibilityAction={onAction}
            pointerEvents="none"
            style={[styles.a11y, { left: layout.ox + 20, top, width: 353, height: 128 }]}
          />
        </View>
      </GestureDetector>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#3FB0FF' },
  a11y: { position: 'absolute' },
});
