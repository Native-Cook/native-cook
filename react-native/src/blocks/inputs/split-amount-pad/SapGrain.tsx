// Split Amount Pad — paper grain over the page: one static Skia pass, drawn once.

import { Canvas, Fill, Shader, Skia } from '@shopify/react-native-skia';
import { StyleSheet } from 'react-native';

// Neutral warm specks at up to 5 % opacity, premultiplied. Nothing here depends on time.
const EFFECT = Skia.RuntimeEffect.Make(`
half4 main(float2 p) {
  float2 q = floor(p * 2.0);
  float n = fract(sin(dot(q, float2(12.9898, 78.233))) * 43758.5453);
  float a = n * n * 0.05;
  return half4(half3(0.16, 0.13, 0.08) * a, a);
}
`);

export function SapGrain() {
  if (!EFFECT) return null;
  return (
    <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
      <Fill>
        <Shader source={EFFECT} />
      </Fill>
    </Canvas>
  );
}
