import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, Platform, StyleSheet, useWindowDimensions, View } from 'react-native';
import { Canvas, Picture, Skia, matchFont, useFont, useImage } from '@shopify/react-native-skia';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';

import {
  ANTON,
  BURGER,
  BURGER_SCALE,
  CREAM,
  RED,
  ROW_SIZE,
  SAND,
  WORD_SIZE,
  drawBackground,
  drawBurgerImage,
  drawCheese,
  drawFloorShadow,
  drawLine,
  drawMark,
  drawRows,
  drawSteam,
  drawWord,
  hexColor,
  makeGrain,
  makeLayout,
  wordWidth,
} from './cheeseArt';
import {
  FALL_TIME,
  LAYERS,
  LOOP,
  PINCH_LEAD,
  ROWS,
  activeEvents,
  beginTouch,
  cheeseFrame,
  dropIn,
  dropInTouch,
  endTouch,
  initialTouch,
  jiggle,
  loopTime,
  meltsAt,
  mod,
  restsAfter,
  rowLitAt,
  rowSlide,
  updateTouch,
} from './cheeseMotion';

/*
 Cheese Drip Burger — the launch screen of an invented burger kitchen (Copper Bun): a deep red
 poster with a giant condensed "MELTY" above and below a double cheeseburger. The cheese is drawn in
 code as molten goo: a drape along each slice, drips that stretch under gravity, neck down and let go
 of a drop that falls into a puddle at the base, glossy with a warm gradient, a specular streak, a
 darker rim and a soft shadow on the patty. The giant word stacks up behind once, steam rises.

 Loop (LOOP = 8 s): rest → the melt builds (1.6–4.3 s), two drops fall (4.15, 4.6 s) → the drips set
 back to rest (5–7 s). Hold to melt more (drops keep falling), release and it slowly sets; tap to
 drop the burger in again. Reduced motion: the rest poster, still.
 */

const WORD = 'MELTY';
const FAMILY = Platform.OS === 'ios' ? 'System' : 'sans-serif';

/** A copy of `list` with `value` appended (no array spread inside worklets). */
function appended(list: number[], value: number) {
  'worklet';
  const out: number[] = [];
  for (let i = 0; i < list.length; i++) out.push(list[i]);
  out.push(value);
  return out;
}

function haptic() {
  if (process.env.EXPO_OS === 'web') return;
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type Props = {
  /** Plays the melt on a loop of LOOP s. */
  autoPlay?: boolean;
};

export function CheeseDripBurger({ autoPlay = false }: Props) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const [active, setActive] = useState(AppState.currentState === 'active');
  const [awake, setAwake] = useState(false);
  const wake = useCallback(() => setAwake(true), []);
  const sleep = useCallback(() => setAwake(false), []);
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const k = width / 393;
  const designHeight = height / k;
  const layout = useMemo(() => makeLayout(designHeight, insets.top / k, insets.bottom / k), [designHeight, insets.top, insets.bottom, k]);

  const burger = useImage(BURGER);
  const anton = useFont(ANTON, WORD_SIZE);
  const grain = useMemo(() => makeGrain(), []);
  const fonts = useMemo(() => {
    const typeface = anton?.getTypeface();
    if (!anton || !typeface) return null;
    return {
      word: anton,
      row: Skia.Font(typeface, ROW_SIZE),
      brand: Skia.Font(typeface, 15),
      label: Skia.Font(typeface, 22),
      line: matchFont({ fontFamily: FAMILY, fontSize: 14, fontWeight: '600' }),
    };
  }, [anton]);

  const clock = useSharedValue(0);
  const startedAt = useSharedValue(-1);
  const clockBase = useSharedValue(0);
  const touch = useSharedValue(initialTouch());
  const asleep = useSharedValue(false);
  const pending = useSharedValue<number[]>([]);
  const pressAt = useSharedValue(0);
  const pressX = useSharedValue(0);
  const pressY = useSharedValue(0);
  const moved = useSharedValue(0);

  const tick = useFrameCallback((info) => {
    if (startedAt.get() < 0) {
      startedAt.set(info.timestamp);
      clockBase.set(clock.get());
    }
    const now = clockBase.get() + (info.timestamp - startedAt.get()) / 1000;
    clock.set(now);
    const result = updateTouch(touch.get(), now);
    if (result.scheduled) {
      touch.set(result.next);
      pending.set(appended(pending.get(), now + PINCH_LEAD));
    }
    const due = pending.get();
    if (due.length > 0) {
      const left: number[] = [];
      let fire = false;
      for (let i = 0; i < due.length; i++) {
        if (due[i] <= now) fire = true;
        else left.push(due[i]);
      }
      if (fire) {
        pending.set(left);
        scheduleOnRN(haptic);
      }
    }
    const s = touch.get();
    if (!autoPlay && !asleep.get() && !s.touching && now > restsAfter(s)) {
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
    const c = clock.get();
    const s = touch.get();
    const t = reduced ? 0 : loopTime(s, c, autoPlay);
    const ambient = reduced ? 0 : mod(c, LOOP);
    let melts: number[][] = [];
    if (reduced) {
      for (let li = 0; li < LAYERS.length; li++) {
        const row: number[] = [];
        for (let di = 0; di < LAYERS[li].drips.length; di++) row.push(0);
        melts.push(row);
      }
    } else {
      melts = meltsAt(s, c, t);
    }
    const events = reduced ? [] : activeEvents(s, c, t);
    const tau = reduced ? -1 : c - s.dropInClock;
    const lit: number[] = [];
    const slide: number[] = [];
    for (let i = 0; i < ROWS; i++) {
      lit.push(reduced ? 0 : rowLitAt(s, c, t, i));
      slide.push(t >= 0 ? rowSlide(t, i) : 0);
    }
    const frame = cheeseFrame(melts, events, ambient, jiggle(tau));
    const drop = dropIn(tau);

    canvas.save();
    canvas.scale(k, k);
    drawBackground(canvas, layout, grain);
    if (fonts) drawRows(canvas, layout, fonts.row, lit, slide, WORD);
    drawFloorShadow(canvas, layout, -drop[0]);

    canvas.save();
    canvas.translate(196.5, layout.baseY + drop[0]);
    canvas.scale(drop[1], drop[2]);
    canvas.translate(-196.5, -layout.baseY);
    canvas.translate(layout.imageX, layout.imageY);
    canvas.scale(BURGER_SCALE, BURGER_SCALE);
    drawBurgerImage(canvas, burger);
    drawCheese(canvas, frame);
    drawSteam(canvas, ambient, 1);
    canvas.restore();

    if (fonts) {
      const brandW = wordWidth(fonts.brand, 'COPPER BUN', 2.4);
      drawMark(canvas, 196.5 - (30 + brandW) / 2, layout.brandY - 7.5, SAND);
      drawWord(canvas, fonts.brand, 'COPPER BUN', 196.5 + 15, layout.brandY, SAND, 1, 2.4);
      drawWord(canvas, fonts.word, WORD, 196.5, layout.topWordY, CREAM, 1, 0);
      drawWord(canvas, fonts.word, WORD, 196.5, layout.bottomWordY, CREAM, 1, 0);
      drawWord(canvas, fonts.label, 'BURGER', 196.5 + 4.5, layout.labelY, SAND, 1, 9);
      drawLine(canvas, fonts.line, 'Double Melt  ·  2 patties, 2 slices  ·  $8.90', 196.5, layout.productY, SAND, 0.85);
    }
    canvas.restore();
    return recorder.finishRecordingAsPicture();
  });

  const startDrop = useCallback(() => {
    if (reduced) return;
    touch.set(dropInTouch(touch.get(), clock.get()));
    pending.set(appended(pending.get(), clock.get() + FALL_TIME));
    if (!autoPlay) {
      asleep.set(false);
      setAwake(true);
    }
  }, [reduced, autoPlay, touch, clock, pending, asleep]);

  const gesture = useMemo(
    () =>
      Gesture.Manual()
        .onTouchesDown((e, manager) => {
          if (reduced || touch.get().touching) return;
          manager.activate();
          const p = e.allTouches[0];
          pressAt.set(clock.get());
          pressX.set(p.x);
          pressY.set(p.y);
          moved.set(0);
          touch.set(beginTouch(touch.get(), clock.get(), autoPlay));
          if (!autoPlay) {
            asleep.set(false);
            scheduleOnRN(wake);
          }
        })
        .onTouchesMove((e) => {
          const p = e.allTouches[0];
          moved.set(Math.max(moved.get(), Math.hypot(p.x - pressX.get(), p.y - pressY.get()) / k));
        })
        .onTouchesUp((e, manager) => {
          if (e.numberOfTouches > 0 || !touch.get().touching) return;
          manager.end();
          const c = clock.get();
          touch.set(endTouch(touch.get(), c));
          if (c - pressAt.get() < 0.25 && moved.get() < 12) {
            touch.set(dropInTouch(touch.get(), c));
            pending.set(appended(pending.get(), c + FALL_TIME));
          }
        })
        .onTouchesCancelled(() => {
          if (!touch.get().touching) return;
          touch.set(endTouch(touch.get(), clock.get()));
        }),
    [reduced, autoPlay, k, touch, clock, wake, asleep, pending, pressAt, pressX, pressY, moved],
  );

  return (
    <View style={[styles.root, { backgroundColor: hexColor(RED) }]}>
      <StatusBar style="light" />
      <GestureDetector gesture={gesture}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityRole="button"
          accessibilityLabel="Copper Bun. MELTY burger. Double Melt, 2 patties, 2 slices, $8.90. A double cheeseburger with molten cheese dripping down every layer."
          accessibilityHint="Hold to melt more, tap to drop the burger in again"
          accessibilityActions={[{ name: 'activate' }]}
          onAccessibilityAction={startDrop}
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
