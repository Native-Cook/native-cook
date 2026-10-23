import { Canvas, LinearGradient, Rect, matchFont, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useRef } from 'react';
import { Platform, StyleSheet, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { cancelAnimation, useFrameCallback, useReducedMotion, useSharedValue, withDecay } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { DECAY, DRAG_DEG_PER_PT, HEADING_KEYS, LOOP_MS, RESUME_AFTER_MS, RETURN_MS } from './motion';
import { SignBoard } from './SignBoard';
import { SIGNS } from './signs';
import { BG, TYPE } from './theme';

// freezeAt (dev only): stop the self-playing swing at this time (ms) for frame-exact screenshots
type Props = { autoPlay?: boolean; freezeAt?: number };

const FAMILY = Platform.select({ ios: '.AppleSystemUIFont', default: 'sans-serif' });

// Catmull-Rom through the heading keys, so the swing has no corners.
function headingAt(t: number) {
  'worklet';
  const n = HEADING_KEYS.length;
  let i = 0;
  while (i < n - 2 && HEADING_KEYS[i + 1][0] <= t) i++;
  // index access, not destructuring: Babel's array helpers do not exist on the UI runtime
  const t1 = HEADING_KEYS[i][0];
  const v1 = HEADING_KEYS[i][1];
  const t2 = HEADING_KEYS[i + 1][0];
  const v2 = HEADING_KEYS[i + 1][1];
  // neighbours wrap around the loop (first and last keys are the same pose)
  const v0 = i > 0 ? HEADING_KEYS[i - 1][1] : HEADING_KEYS[n - 2][1];
  const v3 = i + 2 < n ? HEADING_KEYS[i + 2][1] : HEADING_KEYS[1][1];
  const u = Math.max(0, Math.min(1, (t - t1) / (t2 - t1)));
  const u2 = u * u;
  const u3 = u2 * u;
  return 0.5 * (2 * v1 + (-v0 + v2) * u + (2 * v0 - 5 * v1 + 4 * v2 - v3) * u2 + (-v0 + 3 * v1 - 3 * v2 + v3) * u3);
}

export function StackedColourCards({ autoPlay = false, freezeAt = -1 }: Props) {
  'use no memo';
  const { width, height } = useWindowDimensions();
  const reduceMotion = useReducedMotion();
  const auto = autoPlay && !reduceMotion;

  const fonts = useMemo(
    () => ({
      name: matchFont({ fontFamily: FAMILY, fontSize: TYPE.name, fontWeight: '500' }),
      km: matchFont({ fontFamily: FAMILY, fontSize: TYPE.km, fontWeight: '500' }),
    }),
    [],
  );

  const heading = useSharedValue(0); // degrees the whole post is turned
  const playing = useSharedValue(auto);
  const loopStart = useSharedValue(-1);
  const returning = useSharedValue(0); // > 0: easing back to rest since this frame time; -1: start on the next frame
  const returnFrom = useSharedValue(0);
  const dragStart = useSharedValue(0);

  useFrameCallback((f) => {
    if (!playing.get()) return;
    if (returning.get() === -1) returning.set(f.timestamp);
    if (returning.get() > 0) {
      const k = Math.min(1, (f.timestamp - returning.get()) / RETURN_MS);
      const e = k * k * (3 - 2 * k);
      heading.set(returnFrom.get() * (1 - e));
      if (k >= 1) {
        returning.set(0);
        loopStart.set(f.timestamp);
      }
      return;
    }
    if (loopStart.get() < 0) loopStart.set(f.timestamp);
    const raw = f.timestamp - loopStart.get();
    if (freezeAt >= 0 && raw >= freezeAt) {
      heading.set(headingAt(freezeAt % LOOP_MS));
      playing.set(false);
      return;
    }
    heading.set(headingAt(raw % LOOP_MS));
  });

  // Any touch stops the swing; 3.5 s after the last one the post eases home and swings again.
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const resume = () => {
    returnFrom.set(((heading.get() % 360) + 540) % 360 - 180);
    returning.set(-1);
    playing.set(true);
  };
  const onTouchStart = () => {
    if (!auto) return;
    playing.set(false);
    returning.set(0);
    if (timer.current) clearTimeout(timer.current);
  };
  const onTouchEnd = () => {
    if (!auto) return;
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(resume, RESUME_AFTER_MS);
  };
  useEffect(
    () => () => {
      if (timer.current) clearTimeout(timer.current);
    },
    [],
  );

  const tick = () => Haptics.selectionAsync();
  // 1:1 from the grab point; on release the post keeps the finger's speed and slows down
  const pan = Gesture.Pan()
    .maxPointers(1)
    .onStart(() => {
      cancelAnimation(heading);
      dragStart.set(heading.get());
      scheduleOnRN(tick);
    })
    .onUpdate((e) => {
      heading.set(dragStart.get() - e.translationX * DRAG_DEG_PER_PT);
    })
    .onEnd((e) => {
      if (reduceMotion) return;
      heading.set(withDecay({ velocity: -e.velocityX * DRAG_DEG_PER_PT, deceleration: DECAY }));
    });

  const cx = width / 2;
  const order = SIGNS.map((s, i) => ({ s, i })).reverse();
  return (
    <GestureDetector gesture={pan}>
      <View style={styles.root} onTouchStart={onTouchStart} onTouchEnd={onTouchEnd} onTouchCancel={onTouchEnd}>
        <StatusBar style="light" />
        <Canvas style={StyleSheet.absoluteFill}>
          <Rect x={0} y={0} width={width} height={height}>
            <LinearGradient start={vec(0, 0)} end={vec(0, height)} colors={[BG.top, BG.mid, BG.bottom]} positions={[...BG.stops]} />
          </Rect>
          {/* lower boards first: each board sits on the one below and hides its top face */}
          {order.map(({ s }) => (
            <SignBoard key={s.name} sign={s} cx={cx} heading={heading} nameFont={fonts.name} kmFont={fonts.km} />
          ))}
        </Canvas>
        {/* what a screen reader gets: the friends, top to bottom, with their distance */}
        {SIGNS.filter((s) => s.named !== false).map((s) => (
          <View
            key={s.name}
            accessible
            accessibilityLabel={s.km !== undefined ? `${s.name}, ${s.km} kilometres away` : s.name}
            style={[styles.a11y, { top: s.y - s.h / 2, height: s.h, left: cx - 110 }]}
          />
        ))}
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: BG.top },
  a11y: { position: 'absolute', width: 220 },
});
