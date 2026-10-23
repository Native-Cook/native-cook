/**
 * The source's rush (f018–f025): when a pile is saved, dozens of cards pour down one central column into the
 * bookmark, smeared by speed. Each streak is a baked, pre-blurred copy (never an animated blur), stretched along
 * its fall as it speeds up. One timing drives them all; each card reads its own window of it.
 */
import { Image } from 'expo-image';
import { memo, useMemo, useState } from 'react';
import { StyleSheet } from 'react-native';
import Animated, { useAnimatedStyle, useDerivedValue, type SharedValue } from 'react-native-reanimated';

import { STREAM } from './motion';

const STREAKS = [
  require('./assets/streak-orange-woman.png'),
  require('./assets/streak-violet-flowers.png'),
  require('./assets/streak-pastel-bloom.png'),
  require('./assets/streak-blue-figure.png'),
  require('./assets/streak-white-cat.png'),
  require('./assets/streak-lemon.png'),
  require('./assets/streak-white-van.png'),
  require('./assets/streak-slow-jazz.png'),
  require('./assets/streak-valley.png'),
  require('./assets/streak-pop-bust.png'),
];
/** The baked streak: a 60 × 72 pt card with 58 pt of smear above and below and 8 pt at the sides. */
const SW = 76;
const SH = 188;

type Drop = { art: number; x: number; w: number; start: number; dur: number; y0: number };

/** Seeded, so every loop pours the same stream (no Math.random in a loop that must repeat exactly). */
function drops(n: number, seed: number): Drop[] {
  let s = seed;
  const rnd = () => {
    s = (s * 16807) % 2147483647;
    return (s - 1) / 2147483646;
  };
  return Array.from({ length: n }, (_, i) => {
    const big = rnd() < 0.2;
    return {
      art: i % STREAKS.length,
      x: (rnd() - 0.5) * 2 * STREAM.spread,
      w: big ? 90 + rnd() * 50 : 40 + rnd() * 40,
      start: (i / n) * STREAM.pour,
      dur: Math.min(STREAM.fall * (0.75 + rnd() * 0.5), 1 - (i / n) * STREAM.pour),
      y0: -100 + rnd() * 340, // the source's cards are already all over the screen as they start to fall
    };
  });
}

type Target = { to: { x: number; y: number }; riseY: number; rise: SharedValue<number>; k: number };

function Streak({ d, g, to, riseY, rise, k }: Target & { d: Drop; g: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const u = Math.min(Math.max((g.get() - d.start) / d.dur, 0), 1);
    // Cubic in-out (animate-expo: no ease-in on UI); speed is its derivative, which drives the smear.
    const e = u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
    const speed = (u < 0.5 ? 12 * u * u : 3 * Math.pow(-2 * u + 2, 2)) / 3;
    const cx = to.x + d.x * k * (1 - e * e * e);
    const ty = to.y + (riseY - to.y) * rise.get(); // the glyph rises while the last cards pour in (f023–f025)
    const cy = d.y0 * k + (ty - d.y0 * k) * e;
    const scale = (d.w / 60) * k * (1 - 0.85 * e * e * e);
    const alpha = u <= 0 || u >= 1 ? 0 : Math.min(u * 6, 1) * (1 - Math.max(u - 0.85, 0) / 0.15);
    return {
      opacity: alpha,
      transform: [{ translateX: cx - SW / 2 }, { translateY: cy - SH / 2 }, { scale }, { scaleY: 1 + STREAM.stretch * speed }],
    };
  });
  return (
    <Animated.View style={[styles.streak, style]} pointerEvents="none">
      <Image source={STREAKS[d.art]} style={StyleSheet.absoluteFill} contentFit="fill" transition={0} />
    </Animated.View>
  );
}

/** Mount (keyed per save-all) to pour once. */
export const Stream = memo(function Stream({ clock, to, riseY, rise, k }: Target & { clock: SharedValue<number> }) {
  // On the block's clock, like the flights: a QA hold freezes the stream and the loop repeats exactly.
  const [start] = useState(() => clock.get());
  const g = useDerivedValue(() => {
    const e = clock.get() - start;
    return e < 0 ? 1 : Math.min(e / (STREAM.total / 1000), 1);
  });
  const list = useMemo(() => drops(STREAM.count, 7), []);
  return (
    <>
      {list.map((d, i) => (
        <Streak key={i} d={d} g={g} to={to} riseY={riseY} rise={rise} k={k} />
      ))}
    </>
  );
});

const styles = StyleSheet.create({
  streak: { position: 'absolute', left: 0, top: 0, width: SW, height: SH },
});
