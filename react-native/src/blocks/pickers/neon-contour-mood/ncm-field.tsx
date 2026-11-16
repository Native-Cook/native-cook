import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, StyleSheet, type StyleProp, View, type ViewStyle } from 'react-native';
import { useFrameCallback, useReducedMotion, useSharedValue, type SharedValue } from 'react-native-reanimated';
import { Canvas, Fill, Shader, Skia } from '@shopify/react-native-skia';

import { followFrame, touch, type Cfg } from './ncm-engine';
import { GESTURE, newLook } from './ncm-motion';
import { CONTOUR_SKSL, newField, packMoods } from './ncm-shaders';
import { MOODS, ref, resolveTheme, type Mood } from './ncm-theme';

const contourFx = Skia.RuntimeEffect.Make(CONTOUR_SKSL);
const P3 = Platform.OS === 'ios' ? 1 : 0;

export type ContourFieldProps = {
  /** continuous 0 .. moods.length - 1; the rings follow it, one history step behind each other */
  progress: SharedValue<number>;
  moods?: readonly Mood[];
  rings?: number;
  spacing?: number;
  echoMs?: number;
  /** multiplies the size that fits the view (1 = the reference hero fills about 60 % of the shorter side) */
  scale?: number;
  background?: string;
  style?: StyleProp<ViewStyle>;
};

/** The contour field on its own: an ambient or AI-state surface driven by any shared value. Idle frames cost nothing. */
export function ContourField({
  progress,
  moods = MOODS,
  rings = 7,
  spacing = 26,
  echoMs = GESTURE.echoMs,
  scale = 1,
  background,
  style,
}: ContourFieldProps) {
  const [size, setSize] = useState({ w: 0, h: 0 });
  const reduced = useReducedMotion();
  const list = useMemo(() => (moods.length >= 2 ? moods.slice(0, 9) : MOODS), [moods]);
  const pack = useMemo(() => packMoods(list), [list]);
  const colors = useMemo(() => resolveTheme({ page: background }), [background]);
  const start = Math.min(1, list.length - 1);
  const field = useSharedValue(newField(colors.page, colors.core));
  const look = useSharedValue(newLook(start));
  const hist = useSharedValue<number[]>([start, start, start, start, start, start, start]);
  const cfg = useMemo<Cfg>(
    () => ({
      n: list.length,
      last: list.length - 1,
      echoMs,
      rings: Math.min(7, Math.max(3, rings)),
      spacing,
      breathing: false,
      reduced,
      cx: size.w / 2,
      cy: size.h / 2,
      heroS: (Math.min(size.w, size.h) / 480) * scale,
      cutoff: 1e6,
      fade: 1,
      p3: P3,
      ground: [1, 3, 5].map((i) => parseInt(colors.page.slice(i, i + 2), 16) / 255),
      core: [1, 3, 5].map((i) => parseInt(colors.core.slice(i, i + 2), 16) / 255),
      pack,
      widths: [],
      lens: [],
      table: [],
    }),
    [list, echoMs, rings, spacing, reduced, size, scale, colors, pack],
  );
  useEffect(() => {
    // a changed list, size or colour must repaint even when the progress value stands still: a fresh block refills itself
    field.set(newField(colors.page, colors.core));
  }, [cfg, colors, field]);
  useFrameCallback((frame) => {
    if (size.w === 0) return;
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16.67) / 1000, 0.05);
    if (followFrame(field.get(), look.get(), hist.get(), progress.get(), cfg, dt)) {
      field.modify(touch);
      look.modify(touch);
    }
  });
  const onLayout = (e: LayoutChangeEvent) => setSize({ w: e.nativeEvent.layout.width, h: e.nativeEvent.layout.height });
  return (
    <View style={[styles.fill, { backgroundColor: colors.page }, style]} onLayout={onLayout} accessible={false}>
      <Canvas style={StyleSheet.absoluteFill} accessible={false}>
        {contourFx && size.w > 0 ? (
          <Fill>
            <Shader source={contourFx} uniforms={field} />
          </Fill>
        ) : (
          <Fill color={ref.color.sheetTop} />
        )}
      </Canvas>
    </View>
  );
}
const styles = StyleSheet.create({ fill: { flex: 1, overflow: 'hidden' } });
