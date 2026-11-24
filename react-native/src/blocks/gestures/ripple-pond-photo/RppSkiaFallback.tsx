// Ripple Pond Photo — the fallback water, for when WebGPU is not there (Expo Go, web, a lost device).
//
// No simulation: up to eight rings are drawn analytically in one Skia shader over the photo, each a
// wave packet leaving its source at RING.speed, so there are no reflections off the edges and a wake
// is a string of small rings. The refraction, shading and glint follow the WebGPU look.

import { Canvas, Fill, ImageShader, Shader, Skia, useImage } from '@shopify/react-native-skia';
import { StyleSheet } from 'react-native';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

import { LOOK } from './rpp-motion';

/** the analytic ring: speed pt/s, the packet's half width pt, its wavelength pt, decay per second */
export const RING = { speed: 173, width: 34, wave: 26, decay: 0.9, life: 7, count: 8 } as const;

// Identifiers avoid WGSL builtin and reserved names (Skia may compile this to WGSL).
const SKSL = `
uniform shader photo;
uniform float2 res;
uniform float2 clock;      // seconds now, the water's level 0…1 (0 = calmed)
uniform float4 rings[8];   // x, y (pt), born (s), strength (0 = unused)

half4 main(float2 p) {
  float2 bend = float2(0.0);
  for (int i = 0; i < 8; i++) {
    float4 ring = rings[i];
    float age = clock.x - ring.z;
    if (ring.w > 0.0 && age > 0.0 && age < ${RING.life.toFixed(1)}) {
      float2 d = p - ring.xy;
      float far = length(d) + 0.001;
      float x = far - age * ${RING.speed.toFixed(1)};
      float env = exp(-x * x / ${(RING.width * RING.width).toFixed(1)}) * exp(-age * ${RING.decay.toFixed(2)}) * ring.w * clamp(age / 0.15, 0.0, 1.0) / sqrt(1.0 + far / 40.0);
      bend += d / far * cos(x * ${((2 * Math.PI) / RING.wave).toFixed(4)}) * env;
    }
  }
  bend *= 0.16 * clock.y;
  float2 shift = bend * ${LOOK.refract.toFixed(1)};
  float reach = length(shift);
  if (reach > ${LOOK.refractMax.toFixed(1)}) { shift *= ${LOOK.refractMax.toFixed(1)} / reach; }
  float2 at = clamp(p + shift, float2(0.5), res - float2(0.5));
  half4 c = photo.eval(at);
  float3 L = normalize(float3(-0.45, -0.6, 0.66));
  float3 H = normalize(L + float3(0.0, 0.0, 1.0));
  float3 n = normalize(float3(-bend * ${LOOK.normal.toFixed(1)}, 1.0));
  float glint = max(pow(max(dot(n, H), 0.0), ${LOOK.specPower.toFixed(1)}) - pow(H.z, ${LOOK.specPower.toFixed(1)}), 0.0);
  float3 col = c.rgb * (1.0 + clamp(-dot(bend, L.xy) * ${LOOK.shade.toFixed(1)}, -0.22, 0.22));
  col += glint * ${(LOOK.spec * 0.6).toFixed(2)} * float3(1.0, 0.98, 0.92);
  return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
`;

const EFFECT = Skia.RuntimeEffect.Make(SKSL);

type Props = {
  photoAsset: number;
  width: number;
  height: number;
  /** seconds on the block's clock */
  clock: SharedValue<number>;
  /** the water's level, 1 normally, eased to 0 before the loop's cut */
  level: SharedValue<number>;
  /** RING.count rings of x, y, born, strength */
  rings: SharedValue<number[]>;
};

export function RppSkiaFallback({ photoAsset, width, height, clock, level, rings }: Props) {
  const image = useImage(photoAsset);
  const uniforms = useDerivedValue(() => ({
    res: [width, height],
    clock: [clock.get(), level.get()],
    rings: rings.get(),
  }));
  if (!image || !EFFECT) return null;
  return (
    <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
      <Fill>
        <Shader source={EFFECT} uniforms={uniforms}>
          <ImageShader image={image} fit="cover" rect={{ x: 0, y: 0, width, height }} />
        </Shader>
      </Fill>
    </Canvas>
  );
}
