import { Canvas, Fill, ImageShader, Shader, type SkImage } from '@shopify/react-native-skia';
import { memo, useCallback } from 'react';
import { type GestureResponderEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, { interpolateColor, type SharedValue, useAnimatedStyle, useDerivedValue } from 'react-native-reanimated';

import { badgeEffect, badgeUniforms } from './badge-shaders';
import { type Action } from './vault-actions';
import { type BadgeDef, UNLOCK_DATE } from './vault-contract';
import { cellLayout, type VaultLayout } from './vault-layout';
import { type CellVisual } from './vault-state';
import { colour, textStyles, tone } from './vault-theme';

type Props = { badge: BadgeDef; index: number; layout: VaultLayout; image: SkImage | null;
  visual: SharedValue<CellVisual>; have: number; earned: boolean;
  dispatch: (action: Action, index?: number, x?: number, y?: number) => void };
export const BadgeCell = memo(function BadgeCell({ badge, index, layout, image, visual, have, earned, dispatch }: Props) {
  const col = layout.columns[index % 3];
  const boxes = cellLayout(layout, index);
  const accent = colour(badge.a);
  const uniforms = useDerivedValue(() => badgeUniforms(badge.tile, badge.a, badge.b, badge.c, visual.get()));
  const rattle = useAnimatedStyle(() => ({ transform: [{ translateX: visual.get().shake }] }));
  const nameStyle = useAnimatedStyle(() => ({ opacity: visual.get().present,
    color: interpolateColor(visual.get().reveal, [0, 1], [tone.secondary, tone.ink]) }));
  const progressStyle = useAnimatedStyle(() => ({ opacity: 1 - visual.get().reveal,
    color: interpolateColor(visual.get().flash, [0, 1], [tone.tertiary, tone.ink]) }));
  const earnedStyle = useAnimatedStyle(() => ({ opacity: visual.get().reveal }));
  const barStyle = useAnimatedStyle(() => ({ opacity: 1 - visual.get().reveal }));
  const fillStyle = useAnimatedStyle(() => ({ width: 72 * visual.get().progress,
    backgroundColor: interpolateColor(visual.get().flash, [0, 1], [accent, tone.ink]) }));
  const pressIn = useCallback((event: GestureResponderEvent) => {
    dispatch('press', index, (event.nativeEvent.locationX - col.width / 2) / layout.radius,
      (event.nativeEvent.locationY - layout.badgeY) / layout.radius);
  }, [col.width, dispatch, index, layout.badgeY, layout.radius]);
  const pressOut = useCallback(() => dispatch('release', index), [dispatch, index]);
  const press = useCallback(() => dispatch('open', index), [dispatch, index]);
  const progressText = badge.goal ? `${have} / ${badge.goal.need} ${badge.goal.unit}` : '';
  return <Pressable onPressIn={pressIn} onPressOut={pressOut} onPress={press}
    accessibilityRole="button" accessibilityLabel={earned ? `${badge.name}, earned ${badge.earned ?? UNLOCK_DATE}` :
      `${badge.name}, locked, ${have} of ${badge.goal?.need} ${badge.goal?.unit}`}
    accessibilityHint={earned ? 'Holds the badge up' : 'Not earned yet'}
    style={{ width: col.width, height: layout.pitch }}>
    <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, rattle]}>
      {image && <Canvas style={{ width: col.width, height: layout.canvasHeight }} pointerEvents="none">
        <Fill><Shader source={badgeEffect} uniforms={uniforms}><ImageShader image={image} fit="none" /></Shader></Fill>
      </Canvas>}
      <Animated.Text numberOfLines={1} style={[styles.name, { top: layout.nameY }, nameStyle]}>{badge.name}</Animated.Text>
      <View style={[styles.date, { top: layout.dateY }]}>
        {badge.goal && <Animated.Text numberOfLines={1} style={[styles.dateText, textStyles.number, progressStyle]}>{progressText}</Animated.Text>}
        <Animated.View style={[StyleSheet.absoluteFill, earnedStyle]}>
          <Text numberOfLines={1} style={[styles.dateText, textStyles.number, textStyles.secondary]}>{badge.earned ?? UNLOCK_DATE}</Text>
        </Animated.View>
      </View>
      {badge.goal && <Animated.View style={[styles.track, { top: layout.barY, left: boxes.bar.x - col.x }, barStyle]}>
        <Animated.View style={[styles.fill, fillStyle]} />
      </Animated.View>}
    </Animated.View>
  </Pressable>;
});
const styles = StyleSheet.create({
  name: { position: 'absolute', left: 0, right: 0, fontSize: 13, fontWeight: '600', letterSpacing: -0.16, lineHeight: 18, textAlign: 'center' },
  date: { position: 'absolute', left: 0, right: 0, height: 16 },
  dateText: { fontSize: 12, lineHeight: 16, textAlign: 'center' },
  track: { position: 'absolute', width: 72, height: 4, borderRadius: 2, overflow: 'hidden', backgroundColor: tone.hairline },
  fill: { height: 4, borderRadius: 2 },
});
