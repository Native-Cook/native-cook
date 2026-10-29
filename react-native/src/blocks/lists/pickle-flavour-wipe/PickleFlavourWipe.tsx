import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, Platform, StyleSheet, useWindowDimensions, View } from 'react-native';
import { Canvas, Picture, Skia, matchFont, useImage } from '@shopify/react-native-skia';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';

import {
  FOOD_IMAGES,
  JAR_IMAGES,
  PAGE_LIST,
  drawDots,
  drawScene,
  hexColor,
  makeBackdrops,
  makeBar,
  makeCtas,
  makeJar,
  makeLayout,
  makePattern,
  type Art,
  type Fonts,
} from './wipeArt';
import {
  LOOP,
  beginTouch,
  dragTouch,
  endTouch,
  fadeScene,
  fadeSwipe,
  fading,
  initialTouch,
  mod,
  restsAfter,
  takeHaptic,
  touchScene,
} from './wipeMotion';

/*
 Pickle Flavour Wipe — a pantry shop's flavour browser for an invented pickle brand, jarful. Each
 flavour is a page in its own colour with a jar and its ingredients floating around it; a diagonal
 slash wipes the next flavour in (Mango → Lime → Garlic), the jar springs up from below, the
 ingredients pop out from behind it and the big title slides in with a smear. The last page lines
 the three jars up.

 Loop (LOOP = 10 s): rest on Mango → wipes at 1.0, 3.25, 5.5 s → the Trio line-up → Mango wipes back
 in at 7.75 s and settles → rest. Swipe left or right to wipe to the next or previous flavour: the
 edge follows the finger, release completes the wipe or springs it back. Reduced motion: the Mango
 page, still; swipes cross-fade.
 */

const SANS = Platform.OS === 'ios' ? 'System' : 'sans-serif';
const HEAVY = Platform.OS === 'ios' ? 'Avenir Next' : 'sans-serif';
const HEAVY_WEIGHT = Platform.OS === 'ios' ? '800' : '900';
const SERIF = Platform.OS === 'ios' ? 'Georgia' : 'serif';

function haptic() {
  if (process.env.EXPO_OS === 'web') return;
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function makeFonts(): Fonts {
  const sans = (size: number, weight: '500' | '600' | '700') => matchFont({ fontFamily: SANS, fontSize: size, fontWeight: weight });
  const heavy = (size: number) => matchFont({ fontFamily: HEAVY, fontSize: size, fontWeight: HEAVY_WEIGHT });
  return {
    title: heavy(92),
    mark: heavy(24),
    labelMark: heavy(13),
    labelSerif: matchFont({ fontFamily: SERIF, fontSize: 23, fontWeight: '700', fontStyle: 'italic' }),
    labelSmall: sans(6.5, '700'),
    sub: sans(15, '600'),
    line: sans(15, '500'),
    lineBold: sans(21, '700'),
    price: sans(26, '700'),
    weight: sans(12, '500'),
    cta: sans(16, '600'),
  };
}

type Props = {
  /** Plays the flavour wipes on a loop of LOOP s. */
  autoPlay?: boolean;
};

export function PickleFlavourWipe({ autoPlay = false }: Props) {
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

  const jarMango = useImage(JAR_IMAGES[0]);
  const jarLime = useImage(JAR_IMAGES[1]);
  const jarGarlic = useImage(JAR_IMAGES[2]);
  const carrot = useImage(FOOD_IMAGES[0]);
  const lime = useImage(FOOD_IMAGES[1]);
  const garlic = useImage(FOOD_IMAGES[2]);
  const fonts = useMemo(() => makeFonts(), []);
  const pattern = useMemo(() => makePattern(), []);
  const backdrops = useMemo(() => makeBackdrops(layout), [layout]);
  const ctas = useMemo(() => makeCtas(layout, fonts), [layout, fonts]);
  const bar = useMemo(() => makeBar(layout, fonts), [layout, fonts]);
  const art = useMemo<Art>(() => {
    const raw = [jarMango, jarLime, jarGarlic];
    return {
      jars: raw.map((img, i) => (img ? makeJar(img, i, fonts) : null)),
      foods: [carrot, lime, garlic],
      pattern,
      backdrops,
      ctas,
      fonts,
    };
  }, [jarMango, jarLime, jarGarlic, carrot, lime, garlic, pattern, backdrops, ctas, fonts]);

  const clock = useSharedValue(0);
  const startedAt = useSharedValue(-1);
  const clockBase = useSharedValue(0);
  const touch = useSharedValue(initialTouch());
  const asleep = useSharedValue(false);
  const pressX = useSharedValue(0);
  const lastX = useSharedValue(0);
  const lastT = useSharedValue(0);
  const speedX = useSharedValue(0);
  const dragging = useSharedValue(false);
  const owned = useSharedValue(false);

  const tick = useFrameCallback((info) => {
    if (startedAt.get() < 0) {
      startedAt.set(info.timestamp);
      clockBase.set(clock.get());
    }
    const now = clockBase.get() + (info.timestamp - startedAt.get()) / 1000;
    clock.set(now);
    const result = takeHaptic(touch.get(), now);
    if (result.fire) {
      touch.set(result.next);
      scheduleOnRN(haptic);
    }
    const s = touch.get();
    if ((!autoPlay || reduced) && !asleep.get() && !s.touching && now > restsAfter(s) + 0.1 && !fading(s, now)) {
      asleep.set(true);
      scheduleOnRN(sleep);
    }
  }, false);
  const running = active && ((autoPlay && !reduced) || awake);
  useEffect(() => {
    startedAt.set(-1);
    tick.setActive(running);
    return () => tick.setActive(false);
  }, [running, tick, startedAt]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    const c = clock.get();
    const scene = reduced ? fadeScene(touch.get(), c) : touchScene(touch.get(), c, autoPlay);
    const ambient = reduced ? 0 : mod(c, LOOP);
    canvas.save();
    canvas.scale(k, k);
    drawScene(canvas, layout, art, scene, ambient);
    canvas.drawPicture(bar);
    drawDots(canvas, layout, scene);
    canvas.restore();
    return recorder.finishRecordingAsPicture();
  });

  const step = useCallback(
    (dx: number) => {
      const c = clock.get();
      if (reduced) {
        touch.set(fadeSwipe(touch.get(), c, dx * 100));
      } else {
        touch.set(endTouch(dragTouch(beginTouch(touch.get(), c, autoPlay), c, dx * 120), c, dx * 1000));
      }
      asleep.set(false);
      setAwake(true);
    },
    [reduced, autoPlay, touch, clock, asleep],
  );

  const ctaTop = layout.ctaTop;
  const gesture = useMemo(
    () =>
      Gesture.Manual()
        .onTouchesDown((e, manager) => {
          if (owned.get()) return;
          const p = e.allTouches[0];
          const y0 = p.y / k;
          if (y0 > ctaTop - 8) {
            // The Add to basket pill.
            const x0 = p.x / k;
            if (x0 >= 179 && x0 <= 369 && y0 < ctaTop + 56) scheduleOnRN(haptic);
            manager.fail();
            return;
          }
          manager.activate();
          owned.set(true);
          dragging.set(false);
          pressX.set(p.x);
          lastX.set(p.x);
          lastT.set(clock.get());
          speedX.set(0);
        })
        .onTouchesMove((e) => {
          if (!owned.get()) return;
          const p = e.allTouches[0];
          const dx = (p.x - pressX.get()) / k;
          const c = clock.get();
          const dt = c - lastT.get();
          if (dt > 0.004) {
            const v = (p.x - lastX.get()) / k / dt;
            speedX.set(speedX.get() * 0.3 + v * 0.7);
            lastX.set(p.x);
            lastT.set(c);
          }
          if (reduced) return;
          if (!dragging.get() && Math.abs(dx) > 6) {
            dragging.set(true);
            touch.set(beginTouch(touch.get(), c, autoPlay));
            asleep.set(false);
            scheduleOnRN(wake);
          }
          if (dragging.get()) touch.set(dragTouch(touch.get(), c, dx));
        })
        .onTouchesUp((e, manager) => {
          if (e.numberOfTouches > 0 || !owned.get()) return;
          owned.set(false);
          manager.end();
          const c = clock.get();
          const p = e.allTouches[0];
          const dx = p ? (p.x - pressX.get()) / k : 0;
          if (reduced) {
            touch.set(fadeSwipe(touch.get(), c, dx));
            asleep.set(false);
            scheduleOnRN(wake);
            return;
          }
          if (!dragging.get()) return;
          dragging.set(false);
          touch.set(endTouch(touch.get(), c, speedX.get()));
        })
        .onTouchesCancelled(() => {
          if (!owned.get()) return;
          owned.set(false);
          if (!dragging.get()) return;
          dragging.set(false);
          touch.set(endTouch(touch.get(), clock.get(), 0));
        }),
    [reduced, autoPlay, k, ctaTop, touch, clock, wake, asleep, pressX, lastX, lastT, speedX, dragging, owned],
  );

  return (
    <View style={[styles.root, { backgroundColor: hexColor(PAGE_LIST[0].mid) }]}>
      <StatusBar style="light" />
      <GestureDetector gesture={gesture}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel="jarful pickles. Mango Mixed Pickle, Lime Chilli Pickle, Garlic Whole Clove Pickle and the Trio tasting box."
          accessibilityHint="Swipe to wipe to the next flavour"
          accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
          onAccessibilityAction={(event) => step(event.nativeEvent.actionName === 'increment' ? -1 : 1)}
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
