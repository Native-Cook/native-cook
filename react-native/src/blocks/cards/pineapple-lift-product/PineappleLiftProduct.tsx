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
  BOTTLE_IMAGE,
  BOTTOM_IMAGE,
  FLOWER_IMAGE,
  SKY_MID,
  TOP_IMAGE,
  drawDrops,
  drawFlowers,
  drawHero,
  hexColor,
  makeBackground,
  makeBottle,
  makeGrain,
  makeLayout,
  makeUi,
  rimClip,
  type Fonts,
  type HeroArt,
} from './liftArt';
import { LOOP, beginTouch, dragTouch, endTouch, initialTouch, mod, restsAfter, takeHaptic, touchFrame, type Frame } from './liftMotion';

/*
 Pineapple Lift Product — the product page of an invented body oil (Isla Bloom, Golden Pineapple)
 on a sunny yellow backdrop. At rest a whole pineapple stands closed; its crowned top half lifts
 away on a spring, the amber bottle rises out of the fruit and hovers between the halves while a
 ring of juice drops bursts off the cut rim and the hibiscus flowers drift; then everything settles.

 Loop (LOOP = 7 s): rest → press and lift (1.0–2.4 s) → the bottle rises (1.3 s) in a burst of drops
 (1.45 s) → hovers → sinks back (4.6–5.5 s) → the top closes and lands (5.75 s) → rest. Drag up to
 lift it yourself, release to let it spring shut; tap for a hop and a splash. Reduced motion: the
 open product shot, still.
 */

const SANS = Platform.OS === 'ios' ? 'System' : 'sans-serif';
const SERIF = Platform.OS === 'ios' ? 'Georgia' : 'serif';

function haptic() {
  if (process.env.EXPO_OS === 'web') return;
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function makeFonts(): Fonts {
  const sans = (size: number, weight: '400' | '500' | '600' | '700') => matchFont({ fontFamily: SANS, fontSize: size, fontWeight: weight });
  return {
    sans13: sans(13, '600'),
    semi11: sans(11, '600'),
    serif34: matchFont({ fontFamily: SERIF, fontSize: 34, fontWeight: '400' }),
    sans14: sans(14, '400'),
    bold26: sans(26, '700'),
    medium12: sans(12, '500'),
    semi16: sans(16, '600'),
    bold10: sans(10, '700'),
    labelSemi: sans(5.5, '600'),
    labelSerifBold: matchFont({ fontFamily: SERIF, fontSize: 10.5, fontWeight: '700' }),
    labelSerifItalic: matchFont({ fontFamily: SERIF, fontSize: 9, fontStyle: 'italic' }),
    labelSmall: sans(5, '500'),
  };
}

type Props = {
  /** Plays the lift on a loop of LOOP s. */
  autoPlay?: boolean;
};

export function PineappleLiftProduct({ autoPlay = false }: Props) {
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

  const topImage = useImage(TOP_IMAGE);
  const bottomImage = useImage(BOTTOM_IMAGE);
  const bottleImage = useImage(BOTTLE_IMAGE);
  const flowerImage = useImage(FLOWER_IMAGE);
  const fonts = useMemo(() => makeFonts(), []);
  const grain = useMemo(() => makeGrain(), []);
  const background = useMemo(() => makeBackground(layout, grain), [layout, grain]);
  const ui = useMemo(() => makeUi(layout, fonts), [layout, fonts]);
  const bottle = useMemo(() => (bottleImage ? makeBottle(bottleImage, fonts) : null), [bottleImage, fonts]);
  const hero = useMemo<HeroArt>(() => {
    const faceOval = Skia.PathBuilder.Make()
      .addOval(Skia.XYWHRect(layout.faceX - layout.rx, layout.faceY - layout.ry, 2 * layout.rx, 2 * layout.ry))
      .detach();
    return { top: topImage, bottom: bottomImage, bottle, bottleClip: rimClip(layout, -2), topClip: rimClip(layout, 1), faceOval };
  }, [layout, topImage, bottomImage, bottle]);

  const clock = useSharedValue(0);
  const startedAt = useSharedValue(-1);
  const clockBase = useSharedValue(0);
  const touch = useSharedValue(initialTouch());
  const asleep = useSharedValue(false);
  const pressAt = useSharedValue(0);
  const pressX = useSharedValue(0);
  const pressY = useSharedValue(0);
  const moved = useSharedValue(0);
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
    const c = clock.get();
    let frame: Frame;
    if (reduced) {
      frame = { open: 1, rise: 1, squash: 0, glow: 1, drift: 0, sheen: -1, bursts: [] };
    } else {
      frame = touchFrame(touch.get(), c, autoPlay);
    }
    const ambient = reduced ? 0 : mod(c, LOOP);

    canvas.save();
    canvas.scale(k, k);
    canvas.drawPicture(background);
    drawFlowers(canvas, layout, flowerImage, frame.drift, ambient);
    drawHero(canvas, layout, hero, frame, ambient);
    drawDrops(canvas, layout, frame);
    canvas.drawPicture(ui);
    canvas.restore();
    return recorder.finishRecordingAsPicture();
  });

  const tapAction = useCallback(() => {
    if (reduced) return;
    const c = clock.get();
    touch.set(endTouch(beginTouch(touch.get(), c, autoPlay), c, true));
    if (!autoPlay) {
      asleep.set(false);
      setAwake(true);
    }
  }, [reduced, autoPlay, touch, clock, asleep]);

  const ctaTop = layout.ctaTop;
  const gesture = useMemo(
    () =>
      Gesture.Manual()
        .onTouchesDown((e, manager) => {
          if (owned.get()) return;
          const p = e.allTouches[0];
          const y0 = p.y / k;
          if (y0 > ctaTop - 8) {
            // The Add to bag pill.
            const x0 = p.x / k;
            if (x0 >= 169 && x0 <= 369 && y0 < ctaTop + 56) scheduleOnRN(haptic);
            manager.fail();
            return;
          }
          if (reduced) return;
          manager.activate();
          owned.set(true);
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
          if (!owned.get()) return;
          const p = e.allTouches[0];
          const dx = (p.x - pressX.get()) / k;
          const dy = (p.y - pressY.get()) / k;
          moved.set(Math.max(moved.get(), Math.hypot(dx, dy)));
          touch.set(dragTouch(touch.get(), clock.get(), dy));
        })
        .onTouchesUp((e, manager) => {
          if (e.numberOfTouches > 0 || !owned.get()) return;
          owned.set(false);
          manager.end();
          const c = clock.get();
          const tapped = c - pressAt.get() < 0.25 && moved.get() < 10;
          touch.set(endTouch(touch.get(), c, tapped));
        })
        .onTouchesCancelled(() => {
          if (!owned.get()) return;
          owned.set(false);
          touch.set(endTouch(touch.get(), clock.get(), false));
        }),
    [reduced, autoPlay, k, ctaTop, touch, clock, wake, asleep, pressAt, pressX, pressY, moved, owned],
  );

  return (
    <View style={[styles.root, { backgroundColor: hexColor(SKY_MID) }]}>
      <StatusBar style="dark" />
      <GestureDetector gesture={gesture}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityRole="button"
          accessibilityLabel="Isla Bloom. Golden Pineapple body oil, 100 ml, $28. A pineapple cut in half, the top lifting away as an amber bottle of body oil rises out of it."
          accessibilityHint="Drag up to lift the bottle out, tap for a splash"
          accessibilityActions={[{ name: 'activate' }]}
          onAccessibilityAction={tapAction}
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
