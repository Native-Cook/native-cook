import { Canvas, Fill, Shader } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useCallback } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, { Easing, ReduceMotion, type SharedValue, useAnimatedStyle, useDerivedValue, useSharedValue, withTiming } from 'react-native-reanimated';

import { pillEffect, pillUniforms } from './badge-shaders';
import { type Rgb } from './vault-contract';

const PRESS_EASE = Easing.bezier(0.23, 1, 0.32, 1);
const ignoreHapticFailure = () => {};
type Props = {
  width: number; height: number; label: string; onPress: () => void; disabled?: boolean;
  tint: Rgb; labelColour: string;
  close?: boolean; fontSize?: number; press?: SharedValue<number>; onPressIn?: () => void; onPressOut?: () => void;
};
export function MetalPill({ width, height, label, tint, labelColour, onPress, disabled = false, close = false,
  fontSize = 15, press: externalPress, onPressIn, onPressOut }: Props) {
  const localPress = useSharedValue(0);
  const press = externalPress ?? localPress;
  const uniforms = useDerivedValue(() => pillUniforms({ w: width, h: height, press: press.get(), sheen: -1, dark: disabled ? 1 : 0, tint }));
  const labelStyle = useAnimatedStyle(() => ({ transform: [{ translateY: press.get() }] }));
  const pressIn = useCallback(() => {
    if (onPressIn) onPressIn();
    else {
      localPress.set(withTiming(1, { duration: 110, easing: PRESS_EASE, reduceMotion: ReduceMotion.Never }));
      void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(ignoreHapticFailure);
    }
  }, [localPress, onPressIn]);
  const pressOut = useCallback(() => {
    if (onPressOut) onPressOut();
    else localPress.set(withTiming(0, { duration: 110, easing: PRESS_EASE, reduceMotion: ReduceMotion.Never }));
  }, [localPress, onPressOut]);
  return <View style={{ width, height }}>
    <Canvas pointerEvents="none" style={{ width, height }}><Fill><Shader source={pillEffect} uniforms={uniforms} /></Fill></Canvas>
    <Pressable accessibilityRole="button" accessibilityLabel={label} accessibilityState={{ disabled }}
      disabled={disabled} hitSlop={close ? 4 : 0} onPress={onPress} onPressIn={pressIn} onPressOut={pressOut}
      style={[StyleSheet.absoluteFill, styles.hit]}>
      <Animated.View style={[styles.hit, labelStyle]}>
        {close ? <View style={styles.cross}>
          <View style={[styles.bar, { backgroundColor: labelColour, transform: [{ rotate: '45deg' }] }]} />
          <View style={[styles.bar, { backgroundColor: labelColour, transform: [{ rotate: '-45deg' }] }]} />
        </View> : <Text numberOfLines={1} style={[styles.label, { fontSize, color: labelColour }]}>{label}</Text>}
      </Animated.View>
    </Pressable>
  </View>;
}
const styles = StyleSheet.create({
  hit: { alignItems: 'center', justifyContent: 'center' },
  label: { fontWeight: '600' }, cross: { width: 14, height: 14, alignItems: 'center', justifyContent: 'center' },
  bar: { position: 'absolute', width: 14, height: 2, borderRadius: 1 },
});
