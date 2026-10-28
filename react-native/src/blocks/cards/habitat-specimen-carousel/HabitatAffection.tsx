import { Canvas, Fill, Shader, type Uniforms } from '@shopify/react-native-skia';
import { memo } from 'react';
import { StyleSheet } from 'react-native';
import Animated, { useAnimatedStyle, useDerivedValue, type SharedValue } from 'react-native-reanimated';

import { wrapPosition } from './HabitatMotion';
import { RIGS } from './HabitatRig';
import { AFFECTION_EFFECT } from './HabitatShaders';

/** Heart colours per habitat: ember, spray and petal. */
const HEARTS = [[1, 0.6, 0.42], [0.62, 0.89, 1], [0.78, 0.94, 0.56]] as const;
/** How far above the hero the hearts may rise before the layer ends. */
const HEADROOM = 70;

/** Hearts rising from the head of the specimen being petted; transparent and still otherwise. */
export const HabitatAffection = memo(function HabitatAffection({ pet, petClock, petUntil, position, reduced, width, heroTop, heroHeight, size }: {
  pet: SharedValue<number>; petClock: SharedValue<number>; petUntil: SharedValue<number>; position: SharedValue<number>;
  reduced: SharedValue<boolean>; width: number; heroTop: number; heroHeight: number; size: number;
}) {
  const top = Math.max(0, heroTop - HEADROOM);
  const height = heroTop + heroHeight - top;
  const alive = useDerivedValue(() => pet.get() > 0 || (petUntil.get() >= 0 && petClock.get() < petUntil.get() + 1.4));
  const uniforms = useDerivedValue<Uniforms>(() => {
    const index = Math.round(wrapPosition(position.get())) % 3;
    const head = RIGS[index].joints[0].center;
    const k = size / 512;
    return {
      resolution: [width, height],
      source: [(width - size) / 2 + head[0] * k, heroTop - top + (heroHeight - size) / 2 + head[1] * k],
      clock: alive.get() ? petClock.get() : 0,
      until: alive.get() ? Math.min(petUntil.get(), 1e6) : -1,
      unit: width / 393,
      color: [...HEARTS[index]],
      reduced: reduced.get() ? 1 : 0,
    };
  });
  const visibility = useAnimatedStyle(() => ({ opacity: alive.get() ? 1 : 0 }));
  if (!AFFECTION_EFFECT) return null;
  return <Animated.View pointerEvents="none" style={[styles.layer, { top, width, height }, visibility]} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
    <Canvas style={{ width, height }} pointerEvents="none">
      <Fill><Shader source={AFFECTION_EFFECT} uniforms={uniforms} /></Fill>
    </Canvas>
  </Animated.View>;
});

const styles = StyleSheet.create({ layer: { position: 'absolute', left: 0, zIndex: 19 } });
