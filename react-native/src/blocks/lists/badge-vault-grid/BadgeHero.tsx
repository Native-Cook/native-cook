import { Canvas, Fill, Group, ImageShader, Rect, Shader, type SkImage } from '@shopify/react-native-skia';
import { memo } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle, useDerivedValue } from 'react-native-reanimated';

import { badgeEffect, badgeUniforms, floodEffect, floodUniforms } from './badge-shaders';
import { BADGES, type BadgeDef } from './vault-contract';
import { type VaultLayout } from './vault-layout';
import { type HeroVisual } from './vault-state';

export function BadgeHero({ image, hero, layout, onLayout }: {
  image: SkImage | null; hero: SharedValue<HeroVisual>; layout: VaultLayout; onLayout: () => void;
}) {
  const uniforms = useDerivedValue(() => {
    const v = hero.get(); const badge = BADGES[Math.max(0, v.index)];
    return badgeUniforms(badge.tile, badge.a, badge.b, badge.c, v.pose);
  });
  const flood = useDerivedValue(() => {
    const v = hero.get(); return floodUniforms(BADGES[Math.max(0, v.index)].flood, v.flood);
  });
  const bounds = useDerivedValue(() => {
    const p = hero.get().pose; return { x: p.cx - 1.35 * p.r, y: p.cy - 1.35 * p.r, width: 2.7 * p.r, height: 2.7 * p.r };
  });
  const alpha = useDerivedValue(() => hero.get().alpha);
  return <View onLayout={onLayout} pointerEvents="none" style={StyleSheet.absoluteFill}>
    <Canvas pointerEvents="none" style={{ width: layout.width, height: layout.height }}>
      <Group opacity={alpha}>
        <Fill><Shader source={floodEffect} uniforms={flood} /></Fill>
        {image && <Rect rect={bounds}><Shader source={badgeEffect} uniforms={uniforms}><ImageShader image={image} fit="none" /></Shader></Rect>}
      </Group>
    </Canvas>
  </View>;
}
/** Kept ready above the canvas so the name transfers on the same UI frame as its badge. */
export const MorphName = memo(function MorphName({ badge, index, hero, layout }: {
  badge: BadgeDef; index: number; hero: SharedValue<HeroVisual>; layout: VaultLayout;
}) {
  const style = useAnimatedStyle(() => {
    const v = hero.get();
    return { opacity: v.index === index ? v.alpha : 0,
      transform: [{ translateX: v.nameX - layout.width / 2 }, { translateY: v.nameY - 19 }, { scale: v.nameScale }] };
  });
  return <Animated.View pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants"
    style={[styles.name, { width: layout.width }, style]}>
    <Animated.Text numberOfLines={1} style={[styles.title, { fontSize: layout.titleSize }]}>{badge.name}</Animated.Text>
  </Animated.View>;
});
const styles = StyleSheet.create({
  name: { position: 'absolute', left: 0, top: 0, height: 38 },
  title: { color: '#FFFFFF', lineHeight: 38, fontWeight: '700', letterSpacing: -0.4, textAlign: 'center' },
});
