import { Canvas, Fill, Shader, Skia } from '@shopify/react-native-skia';
import { memo, useMemo } from 'react';
import { StyleSheet } from 'react-native';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

const SOURCE = `
uniform float2 resolution;
uniform float sweep;
uniform float strength;
half4 main(float2 position) {
  float2 uv = position / resolution;
  float beam = 1.0 - smoothstep(0.0, 0.11, abs(uv.x + uv.y * 0.13 - sweep));
  float alpha = beam * strength * 0.38;
  return half4(alpha, alpha * 0.98, alpha * 0.80, alpha);
}`;

export const CommitShimmer = memo(function CommitShimmer({ sweep, enabled, width = 353, height = 52 }: {
  sweep: SharedValue<number>;
  enabled: SharedValue<number>;
  width?: number;
  height?: number;
}) {
  const effect = useMemo(() => Skia.RuntimeEffect.Make(SOURCE), []);
  const uniforms = useDerivedValue(() => ({ resolution: [width, height], sweep: sweep.get(), strength: enabled.get() }));
  if (!effect) return null;
  return (
    <Canvas style={StyleSheet.absoluteFill} pointerEvents="none" accessible={false}>
      <Fill><Shader source={effect} uniforms={uniforms} /></Fill>
    </Canvas>
  );
});
