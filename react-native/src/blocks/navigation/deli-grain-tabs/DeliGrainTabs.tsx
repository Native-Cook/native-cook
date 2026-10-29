import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, Platform, StyleSheet, useWindowDimensions, View } from 'react-native';
import { Canvas, Picture, Skia, matchFont } from '@shopify/react-native-skia';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';

import { CATEGORIES, CREAM, drawScene, makeLabels, makePaper, makePaths, sampleGrains, type Art } from './grainArt';
import {
  GRAIN_COUNT,
  RESUME_AFTER,
  beginTouch,
  endTouch,
  initialTouch,
  makeLayout,
  makeSeeds,
  moveTouch,
  restsAfter,
  stepTouch,
  tabX,
  tabY,
  tapTouch,
  touchFrame,
} from './grainMotion';

/*
 Deli Grain Tabs — an invented Italian deli's category screen on cream, after the pin's layout: two
 rows of six small food silhouettes as tabs and one large silhouette in the middle with the
 category's name and item count. When the tab changes, the big silhouette crumbles into 1,400 flour
 grains that drain from the bottom, swirl, and rain into the next shape bottom-first while the tab
 and its indicator move along the strip.

 Loop (LOOP = 9.6 s): Balsamico rests (0.9 s) → sifts into Flour & Pizza (1.5 s) → rests → Pasta &
 Risotto (bottom row) → Sweets & Coffee → back to Balsamico. Tap a tab to sift to it; drag across
 the big shape to stir the grains; the loop resumes 2 s after the last touch. Reduced motion:
 Balsamico, still; a tab tap cross-fades.
 */

const SANS = Platform.OS === 'ios' ? 'System' : 'sans-serif';

function haptic() {
  if (process.env.EXPO_OS === 'web') return;
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type Props = {
  /** Plays the sift through four categories on a loop of LOOP s. */
  autoPlay?: boolean;
};

export function DeliGrainTabs({ autoPlay = false }: Props) {
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
  const layout = useMemo(() => makeLayout(height / k, insets.top / k, insets.bottom / k), [height, k, insets.top, insets.bottom]);
  const paper = useMemo(() => makePaper(width, height), [width, height]);
  const art = useMemo<Art>(() => {
    const paths = makePaths();
    const fonts = {
      name: matchFont({ fontFamily: SANS, fontSize: 17, fontWeight: '600' }),
      count: matchFont({ fontFamily: SANS, fontSize: 13, fontWeight: '400' }),
    };
    return {
      paths,
      grains: paths.map((p, i) => sampleGrains(p, i)),
      seeds: makeSeeds(),
      fonts,
      labels: makeLabels(fonts),
    };
  }, []);

  const looping = autoPlay && !reduced;
  const clock = useSharedValue(0);
  const epoch = useSharedValue(-1);
  const touch = useSharedValue(initialTouch());
  const asleep = useSharedValue(true);
  const press = useSharedValue({ tab: -1, x: 0, y: 0, moved: false, stir: false });

  const tick = useFrameCallback((info) => {
    if (epoch.get() < 0) epoch.set(info.timestamp - clock.get() * 1000);
    const now = (info.timestamp - epoch.get()) / 1000;
    clock.set(now);
    const s = stepTouch(touch.get(), now, looping);
    if (s !== touch.get()) touch.set(s);
    if (!asleep.get() && !s.touching) {
      const settle = looping ? restsAfter(s) + RESUME_AFTER : restsAfter(s);
      if (now > settle + 0.1) {
        asleep.set(true);
        scheduleOnRN(sleep);
      }
    }
  }, false);
  const running = active && (looping || awake);
  useEffect(() => {
    epoch.set(-1);
    tick.setActive(running);
    return () => tick.setActive(false);
  }, [running, tick, epoch]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    const frame = touchFrame(touch.get(), clock.get(), looping);
    const xs: number[] = new Array(GRAIN_COUNT);
    const ys: number[] = new Array(GRAIN_COUNT);
    const fly: number[] = new Array(GRAIN_COUNT);
    canvas.save();
    canvas.scale(k, k);
    drawScene(canvas, layout, frame, reduced, art, xs, ys, fly);
    canvas.restore();
    return recorder.finishRecordingAsPicture();
  });

  const gesture = useMemo(
    () =>
      Gesture.Pan()
        .minDistance(0)
        .onBegin((e) => {
          const x = e.x / k;
          const y = e.y / k;
          // Which tab, if any: a 54 × 60 pt box around each icon.
          let tab = -1;
          for (let i = 0; i < CATEGORIES.length; i += 1) {
            const dy = y - tabY(layout, i);
            if (Math.abs(x - tabX(i)) <= 27 && dy >= -26 && dy <= 34) tab = i;
          }
          const inHero = y > layout.topRow + 40 && y < layout.bottomRow - 40;
          const stir = tab < 0 && inHero && !reduced;
          press.set({ tab, x, y, moved: false, stir });
          if (stir) {
            touch.set(beginTouch(touch.get(), x, y, clock.get(), looping));
            asleep.set(false);
            scheduleOnRN(wake);
          }
        })
        .onUpdate((e) => {
          const p = press.get();
          const x = e.x / k;
          const y = e.y / k;
          if (p.tab >= 0) {
            if (!p.moved && (x - p.x) * (x - p.x) + (y - p.y) * (y - p.y) > 144) press.set({ ...p, moved: true });
            return;
          }
          if (p.stir) touch.set(moveTouch(touch.get(), x, y, clock.get()));
        })
        .onFinalize(() => {
          const p = press.get();
          if (p.tab >= 0 && !p.moved) {
            scheduleOnRN(haptic);
            touch.set(tapTouch(touch.get(), p.tab, clock.get(), looping, reduced, layout, art.grains, art.seeds));
            asleep.set(false);
            scheduleOnRN(wake);
          }
          if (p.stir) touch.set(endTouch(touch.get(), clock.get()));
          press.set({ tab: -1, x: 0, y: 0, moved: false, stir: false });
        }),
    [k, layout, reduced, looping, art, touch, clock, press, asleep, wake],
  );

  return (
    <View style={[styles.root, { backgroundColor: CREAM }]}>
      <StatusBar style="dark" />
      <GestureDetector gesture={gesture}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityLabel={`Deli categories. ${CATEGORIES.map((c) => `${c.name}, ${c.count} products`).join(', ')}.`}
          accessibilityHint="Tap a category to show it; drag across the big shape to stir it"
        >
          <Canvas style={StyleSheet.absoluteFill}>
            <Picture picture={paper} />
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
