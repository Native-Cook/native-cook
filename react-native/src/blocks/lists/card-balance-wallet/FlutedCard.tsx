import { Canvas, Fill, ImageShader, Shader, Skia, useImage } from '@shopify/react-native-skia';
import { StyleSheet } from 'react-native';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

// The card's art: the pin's colours as a 16 x 10 field sampled from it (bilinear), seen through reeded
// glass. Each flute refracts the field sideways and catches light on one edge, only across the middle
// of the card where the pin has them, and the refraction follows the card's tilt.
const SKSL = `
uniform shader field;
uniform float2 size;
uniform float2 fieldSize;
uniform float shift;
uniform float spec;
uniform float light;

half4 main(float2 xy) {
  float2 uv = xy / size;
  // Reeds measured on the pin: 11.8 pt apart, a boundary at 85.2 pt from the card's left edge, from ~23 % to
  // ~80 % of the width. Across each reed the field is pulled sideways and brightens, then drops at the next edge.
  float px = uv.x * size.x - 85.2 + shift * 40.0;
  float f = fract(px / 11.8);
  float reach = smoothstep(0.2, 0.25, uv.x) * (1.0 - smoothstep(0.78, 0.84, uv.x));
  float2 q = uv;
  q.x += (f - 0.5) * 0.035 * reach;
  float3 c = field.eval(clamp(q, 0.0, 1.0) * fieldSize).rgb;
  c += (f - 0.5) * 0.06 * reach;
  // Specular streak, slanted 20 deg, catching more on each reed's bright edge; 0 at rest.
  float d = uv.x - light + (uv.y - 0.5) * 0.36 * size.y / size.x;
  c += spec * exp(-d * d / 0.006) * (0.75 + 0.5 * f * reach);
  float grain = fract(sin(dot(xy, float2(12.9898, 78.233))) * 43758.5453);
  c += (grain - 0.5) * 0.02;
  return half4(half3(c), 1.0);
}
`;

function makeEffect() {
  const effect = Skia.RuntimeEffect.Make(SKSL);
  if (!effect) throw new Error('Fluted card shader did not compile');
  return effect;
}
const effect = makeEffect();
const FIELD = { w: 16, h: 10 };

export function FlutedCard({ width, height, shift, spec, light }: {
  width: number; height: number; shift: SharedValue<number>; spec: SharedValue<number>; light: SharedValue<number>;
}) {
  const field = useImage(require('./assets/card-field.png'));
  const uniforms = useDerivedValue(() => ({ size: [width, height], fieldSize: [FIELD.w, FIELD.h], shift: shift.get(), spec: spec.get(), light: light.get() }));
  if (!field) return null;
  return (
    <Canvas style={[StyleSheet.absoluteFill, { width, height }]} pointerEvents="none">
      <Fill>
        <Shader source={effect} uniforms={uniforms}>
          <ImageShader image={field} fit="fill" tx="clamp" ty="clamp" rect={{ x: 0, y: 0, width: FIELD.w, height: FIELD.h }}
            sampling={{ filter: 1, mipmap: 0 }} />
        </Shader>
      </Fill>
    </Canvas>
  );
}
