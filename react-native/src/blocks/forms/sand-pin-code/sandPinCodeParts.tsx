import { BlurMask, Group, LinearGradient as SkiaLinearGradient, Path, RoundedRect, Skia, type SkPath, vec } from '@shopify/react-native-skia';
import { LinearGradient } from 'expo-linear-gradient';
import { SymbolView } from 'expo-symbols';
import { memo, useMemo } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle, useDerivedValue } from 'react-native-reanimated';

import { CARVE, type Sand, clamp01, easeInOutCubic, hash } from './sandPinCodeMotion';

/*
 Sand PIN Code — the pieces drawn per slot and per key: a carved digit's groove strokes, the
 rake, and the river-stone keys.
*/

export const KEY_W = 84;
export const KEY_H = 60;

export type GrooveProps = {
  index: number;
  sand: SharedValue<Sand>;
  tNow: SharedValue<number>;
  paths: SkPath[];
  empty: SkPath;
  x: number;
  y: number;
};

/** One slot's carved digit: berm, lit lower wall, floor, shadowed upper wall, and the unlock glow. */
export const SlotGroove = memo(function SlotGroove({ index, sand, tNow, paths, empty, x, y }: GrooveProps) {
  const path = useDerivedValue(() => {
    const d = sand.get().digits[index];
    return d >= 0 ? paths[d] : empty;
  });
  const end = useDerivedValue(() => {
    const s = sand.get();
    if (s.carveAt[index] < 0) return 0;
    return easeInOutCubic(clamp01((tNow.get() - s.carveAt[index]) / CARVE));
  });
  const opacity = useDerivedValue(() => {
    const s = sand.get();
    const t = tNow.get();
    if (s.digits[index] < 0 || s.carveAt[index] < 0 || t < s.carveAt[index]) return 0;
    if (s.passAt[index] >= 0 && t >= s.passAt[index]) return 0;
    return s.fade;
  });
  const glow = useDerivedValue(() => sand.get().bright * 0.85);
  const transform = useMemo(() => [{ translateX: x }, { translateY: y }], [x, y]);
  return (
    <Group transform={transform} opacity={opacity}>
      <Path path={path} end={end} style="stroke" strokeWidth={10} strokeCap="round" strokeJoin="round" color="rgba(255,244,222,0.18)">
        <BlurMask blur={2.5} style="normal" />
      </Path>
      <Path path={path} end={end} style="stroke" strokeWidth={6.2} strokeCap="round" strokeJoin="round" color="rgba(255,246,228,0.7)" transform={[{ translateX: 1.1 }, { translateY: 1.3 }]} />
      <Path path={path} end={end} style="stroke" strokeWidth={5} strokeCap="round" strokeJoin="round" color="#a68b66" />
      <Path path={path} end={end} style="stroke" strokeWidth={2.6} strokeCap="round" strokeJoin="round" color="rgba(98,74,44,0.75)" transform={[{ translateX: -0.9 }, { translateY: -1 }]}>
        <BlurMask blur={0.8} style="normal" />
      </Path>
      <Path path={path} end={end} style="stroke" strokeWidth={4} strokeCap="round" strokeJoin="round" color="rgba(255,206,120,1)" opacity={glow}>
        <BlurMask blur={3} style="normal" />
      </Path>
    </Group>
  );
});

/** The rake, at x = 0: a bamboo crossbar with seven tines and its handle running off the bottom right. */
export function RakeArt({ y }: { y: number }) {
  const handle = useMemo(() => {
    return Skia.PathBuilder.Make().moveTo(-2, y + 10).lineTo(140, y + 420).build();
  }, [y]);
  return (
    <Group>
      <Path path={handle} style="stroke" strokeWidth={10} strokeCap="round" color="rgba(60,40,15,0.25)" transform={[{ translateX: 12 }, { translateY: 10 }]}>
        <BlurMask blur={6} style="normal" />
      </Path>
      <RoundedRect x={-8} y={y - 54} width={16} height={108} r={6} color="rgba(60,40,15,0.28)" transform={[{ translateX: 8 }, { translateY: 8 }]}>
        <BlurMask blur={6} style="normal" />
      </RoundedRect>
      <Path path={handle} style="stroke" strokeWidth={9} strokeCap="round" color="#a87e45" />
      <Path path={handle} style="stroke" strokeWidth={2} strokeCap="round" color="rgba(255,232,190,0.5)" transform={[{ translateX: -2 }]} />
      {Array.from({ length: 7 }, (_, k) => (
        <RoundedRect key={k} x={6} y={y - 45 + k * 14} width={9} height={4} r={2} color="#6f5129" />
      ))}
      <RoundedRect x={-8} y={y - 54} width={16} height={108} r={6}>
        <SkiaLinearGradient start={vec(-8, 0)} end={vec(8, 0)} colors={['#7e5a2a', '#c99b5e', '#e2bb7d', '#8c6531']} positions={[0, 0.35, 0.55, 1]} />
      </RoundedRect>
      <RoundedRect x={-5} y={y - 50} width={3} height={100} r={1.5} color="rgba(255,236,200,0.45)" />
    </Group>
  );
}

export type PebbleProps = {
  index: number;
  label: string;
  sand: SharedValue<Sand>;
  onPress: (index: number) => void;
};

/** A river stone key: irregular corners, a warm grey gradient, two shadows on the sand, an engraved label. */
export const Pebble = memo(function Pebble({ index, label, sand, onPress }: PebbleProps) {
  const shape = useMemo(() => {
    const r = (k: number) => 22 + hash(index * 9 + k) * 14;
    return { borderTopLeftRadius: r(1), borderTopRightRadius: r(2), borderBottomLeftRadius: r(3), borderBottomRightRadius: r(4), rotate: `${(hash(index * 5 + 2) - 0.5) * 6}deg` };
  }, [index]);
  const style = useAnimatedStyle(() => {
    const s = sand.get();
    const p = s.key === index ? s.press : 0;
    return { transform: [{ translateY: 2 * p }, { scaleX: 1 + 0.05 * p }, { scaleY: 1 - 0.09 * p }, { rotate: shape.rotate }] };
  });
  if (label === '') {
    return (
      <View style={styles.keySlot}>
        <Text style={styles.forgot}>Forgot?</Text>
      </View>
    );
  }
  const tone = hash(index * 3 + 1);
  const colors = tone > 0.66 ? (['#b7aea1', '#8f877c', '#655f57'] as const) : tone > 0.33 ? (['#a9a59c', '#807c74', '#57544f'] as const) : (['#bca995', '#94826e', '#6a5b4b'] as const);
  return (
    <Pressable onPressIn={() => onPress(index)} hitSlop={6} accessibilityRole="button" accessibilityLabel={label === 'del' ? 'Delete' : label} style={styles.keySlot}>
      <Animated.View style={[styles.pebble, shape, style]}>
        <LinearGradient colors={colors} locations={[0, 0.55, 1]} start={{ x: 0.2, y: 0 }} end={{ x: 0.8, y: 1 }} style={[StyleSheet.absoluteFill, shape]} />
        <View style={[StyleSheet.absoluteFill, shape, styles.pebbleEdge]} />
        {label === 'del' ? (
          <SymbolView name={{ ios: 'delete.left', android: 'backspace', web: 'backspace' }} size={20} tintColor="rgba(40,35,30,0.78)" />
        ) : (
          <Text style={styles.keyText}>{label}</Text>
        )}
      </Animated.View>
    </Pressable>
  );
});

const styles = StyleSheet.create({
  keySlot: { width: KEY_W, height: KEY_H, alignItems: 'center', justifyContent: 'center' },
  pebble: {
    width: KEY_W - 6,
    height: KEY_H - 4,
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 2px 3px rgba(70,48,20,0.4), 0 12px 20px rgba(70,48,20,0.28)',
  },
  pebbleEdge: { boxShadow: 'inset 0 1.5px 0 rgba(255,255,255,0.35), inset 0 -2px 3px rgba(30,24,18,0.35)' },
  keyText: {
    color: 'rgba(38,33,28,0.8)',
    fontSize: 26,
    fontWeight: '500',
    fontVariant: ['tabular-nums'],
    textShadowColor: 'rgba(255,255,255,0.35)',
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 0,
  },
  forgot: { color: 'rgba(61,45,24,0.6)', fontSize: 13, fontWeight: '600' },
});
