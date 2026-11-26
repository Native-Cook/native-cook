import { useCallback, useEffect, useMemo, useState } from 'react';
import { AccessibilityInfo, AppState, Platform, StyleSheet, useWindowDimensions, View } from 'react-native';
import { Canvas, Picture, Skia, useFont, useTypeface, useImage } from '@shopify/react-native-skia';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';

import {
  CANS,
  CAN_H,
  CAN_W,
  INTER_MEDIUM,
  INTER_SEMIBOLD,
  MACHINE,
  MONO,
  ONEST,
  TITAN,
  drawCans,
  drawCard,
  drawPanel,
  keyAt,
  makeLayout,
  recordBase,
  slotAt,
  slotPoint,
  trayHit,
} from './snackArt';
import type { Fonts, Layout } from './snackArt';
import {
  COLS,
  DRINKS,
  ROWS,
  canIndex,
  frameAt,
  initialTouch,
  landedBeat,
  pressKey,
  slotCode,
  stepTouch,
  takeCan,
  takeOver,
  tapCan,
  touched,
} from './snackMotion';

/*
 Snack Stop Vending — a lobby snack machine as a product list: 20 cans on spiral coils behind glass.
 Type a code on the keypad (or tap a can): the LCD shows it, the card names the drink and its price,
 the coil turns, the can creeps forward, tips over the shelf edge, drops behind the glass into the
 tray with a bounce and the price pill turns into "Enjoy!". Then the can is taken and a new one slides
 forward into the slot.

 Loop (LOOP = 8 s): rest (0–0.85) → B (1.0), 4 (1.6) → coil turns (2.0–2.8) → tips (2.8–3.15) →
 falls (3.15–3.6) → bounces, Enjoy! (3.6–3.95) → taken (5.8–6.3), card back (6.0) → refill
 (6.4–7.0) → rest (7.0–8.0, the t = 0 frame).
 Touch: keys (letter, then 1–5; ✕ clears; anything else shakes the LCD), a can (its code), the tray
 (takes the can). 2 s after the last touch it takes a waiting can and the loop restarts at t = 0.
 Reduce Motion: no fall or bounce; the can fades out of its slot and in, lying in the tray (200 ms each).
*/

/** A light hit. On Android expo-haptics' impact is a weak raw pulse, so ask the system for its key click first. */
async function landHaptic() {
  if (process.env.EXPO_OS === 'web') return;
  const light = () => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
  try {
    if (Platform.OS === 'android') {
      await Haptics.performAndroidHapticsAsync(Haptics.AndroidHaptics.Virtual_Key).catch(light);
    } else {
      await light();
    }
  } catch {}
}

function announceReady(name: string) {
  AccessibilityInfo.announceForAccessibility(`${name} is ready in the tray`);
}

type Props = {
  /** Plays the B4 order on a loop of LOOP s. */
  autoPlay?: boolean;
};

export function SnackStopVending({ autoPlay = false }: Props) {
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const layout: Layout = useMemo(() => makeLayout(width, height), [width, height]);
  const machine = useImage(MACHINE);
  const can0 = useImage(CANS[0]);
  const can1 = useImage(CANS[1]);
  const can2 = useImage(CANS[2]);
  const can3 = useImage(CANS[3]);
  const can4 = useImage(CANS[4]);
  const cans = useMemo(() => [can0, can1, can2, can3, can4], [can0, can1, can2, can3, can4]);
  const base = useMemo(() => (machine ? recordBase(layout, machine) : null), [machine, layout]);

  const titan = useFont(TITAN, 30);
  // Typefaces, not fonts: Skia 2.6.2's font.getTypeface() returns the typeface without a reference, so each wrapper frees it once when collected.
  const onest = useTypeface(ONEST);
  const medium = useFont(INTER_MEDIUM, 14);
  const semibold = useFont(INTER_SEMIBOLD, 9);
  const mono = useFont(MONO, 15);
  const fonts: Fonts | null = useMemo(() => {
    if (!titan || !onest || !medium || !semibold || !mono) return null;
    return { title: titan, sub: medium, name: Skia.Font(onest, 20), line: medium, pill: Skia.Font(onest, 22), lcd: mono, key: semibold };
  }, [titan, onest, medium, semibold, mono]);

  const clock = useSharedValue(0);
  const startedAt = useSharedValue(-1);
  const clockBase = useSharedValue(0);
  const touch = useSharedValue(initialTouch());
  const lastBeat = useSharedValue(-1);

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
    const beat = landedBeat(cur, now, reduced);
    const prev = lastBeat.get();
    if (beat >= 3.6 && prev >= 0 && prev < 3.6 && cur.slot >= 0) {
      scheduleOnRN(landHaptic);
      scheduleOnRN(announceReady, DRINKS[canIndex(cur.slot)][0]);
    }
    lastBeat.set(beat);
  }, false);
  const running = active;
  useEffect(() => {
    startedAt.set(-1);
    tick.setActive(running);
    return () => tick.setActive(false);
  }, [running, tick, startedAt]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    const frame = frameAt(touch.get(), clock.get(), reduced);
    if (base) canvas.drawPicture(base);
    drawCans(canvas, layout, cans, frame);
    if (fonts) {
      drawPanel(canvas, layout, fonts, frame);
      drawCard(canvas, layout, fonts, frame);
    }
    return recorder.finishRecordingAsPicture();
  });

  /** Choose a slot without the keypad (the accessibility action). */
  const chooseSlot = useCallback(
    (slot: number) => {
      const c = clock.get();
      touch.set(tapCan(takeOver(touch.get(), c), slot, c, reduced));
    },
    [clock, touch, reduced],
  );

  const gesture = useMemo(
    () =>
      Gesture.Manual().onTouchesDown((e, manager) => {
        manager.activate();
        const p = e.changedTouches[0] ?? e.allTouches[0];
        const c = clock.get();
        let s = takeOver(touch.get(), c);
        const key = keyAt(layout, p.x, p.y);
        if (key >= 0) s = pressKey(s, key, c, reduced);
        else if (trayHit(layout, p.x, p.y)) s = takeCan(s, c, reduced);
        else {
          const slot = slotAt(layout, p.x, p.y);
          if (slot >= 0) s = tapCan(s, slot, c, reduced);
        }
        touch.set(touched(s, c));
        manager.end();
      }),
    [layout, touch, clock, reduced],
  );

  const slots = useMemo(() => {
    const out: { slot: number; label: string; left: number; top: number }[] = [];
    for (let slot = 0; slot < ROWS * COLS; slot++) {
      const [x, y] = slotPoint(layout, slot);
      const drink = DRINKS[canIndex(slot)];
      out.push({ slot, label: `${drink[0]}, ${slotCode(slot)}, ${drink[2]}`, left: x - CAN_W / 2, top: y - CAN_H });
    }
    return out;
  }, [layout]);

  return (
    <View style={styles.root}>
      <StatusBar style="dark" />
      <GestureDetector gesture={gesture}>
        <View style={StyleSheet.absoluteFill}>
          <Canvas style={StyleSheet.absoluteFill}>
            <Picture picture={picture} />
          </Canvas>
          <View
            accessible
            accessibilityRole="header"
            accessibilityLabel="Snack Stop, Lobby, Level 2"
            style={[styles.a11y, { left: layout.ox + 28, top: 56 * layout.fy, width: 200, height: 50 }]}
          />
          <View accessibilityRole="list" style={StyleSheet.absoluteFill} pointerEvents="box-none">
            {slots.map((s) => (
              <View
                key={s.slot}
                accessible
                accessibilityRole="button"
                accessibilityLabel={s.label}
                accessibilityActions={[{ name: 'activate' }]}
                onAccessibilityAction={() => chooseSlot(s.slot)}
                pointerEvents="none"
                style={[styles.a11y, { left: s.left, top: s.top, width: CAN_W, height: CAN_H }]}
              />
            ))}
          </View>
        </View>
      </GestureDetector>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#FFD6BF' },
  a11y: { position: 'absolute' },
});
