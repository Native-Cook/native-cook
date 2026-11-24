// Coral Growth Streak — the fallback reef, for when WebGPU is not there (Expo Go, web, a lost device)
// and for reduced motion.
//
// No simulation: a labyrinth that looks like the Gray-Scott coral (seven plane waves of one wavelength,
// thresholded) is uncovered out to the open radius and around each touch, with the WebGPU view's
// colours, light, dissolve and bloom.

import { Canvas, Fill, Shader, Skia } from '@shopify/react-native-skia';
import { Platform, StyleSheet } from 'react-native';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

/** touches the fallback remembers */
export const TOUCHES = 8;
/** Skia treats a runtime effect's output as Display P3 on iOS (AGENTS, Mistakes) */
const WIDE = Platform.OS === 'ios' ? 1 : 0;

// Identifiers avoid WGSL builtin and reserved names (Skia may compile this to WGSL).
const SKSL = `
uniform float2 res;
uniform float4 anim;        // level 0…1, bloom 0…1, phase 0…1, open radius (fraction of the reef's)
uniform float2 clock;       // seconds now, 1: write Display P3
uniform float4 touches[8];  // x, y (pt), born (s), on

float3 toLinear(float3 c) { return pow(c, float3(2.2)); }
float grainAt(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }

// the labyrinth's height at d (the reef's radius is 1), before it is uncovered
float labyrinth(float2 d) {
  float2 p = d + 0.03 * float2(sin(d.y * 5.0), sin(d.x * 4.3));
  float sum = 0.0;
  for (int i = 0; i < 7; i++) {
    float a = float(i) * 2.399 + 0.5;
    sum += cos(38.0 * dot(p, float2(cos(a), sin(a))) + float(i) * 1.7);
  }
  return smoothstep(-0.1, 0.22, sum / 7.0);
}

// how much of the coral has grown at d
float grown(float2 d) {
  float r = length(d) + 0.05 * sin(d.x * 9.0 + 1.3) * sin(d.y * 8.0 - 0.7);
  float m = 1.0 - smoothstep(anim.w - 0.14, anim.w, r);
  for (int i = 0; i < 8; i++) {
    float4 touch = touches[i];
    float age = clock.x - touch.z;
    if (touch.w > 0.0 && age > 0.0) {
      float reach = 1.2 * (1.0 - exp(-age * 0.35));
      float2 c = (touch.xy / res - 0.5) * 2.0;
      m = max(m, 1.0 - smoothstep(reach - 0.14, reach, length(d - c)));
    }
  }
  float far = length(d);
  return m * smoothstep(far - 0.3, far, anim.x * 1.6 - 0.3);
}

float heightAt(float2 d) { return labyrinth(d) * grown(d); }

half4 main(float2 xy) {
  float2 d = (xy / res - 0.5) * 2.0;
  float r = length(d);
  float e = 2.0 / 256.0;
  float hc = heightAt(d);
  float2 slope = float2(heightAt(d + float2(e, 0.0)) - hc, heightAt(d + float2(0.0, e)) - hc);

  float3 L = normalize(float3(-0.45, -0.6, 0.66));
  float3 H = normalize(L + float3(0.0, 0.0, 1.0));
  float3 n = normalize(float3(-slope * 2.4, 1.0));
  float dif = max(dot(n, L), 0.0);
  float spec = pow(max(dot(n, H), 0.0), 40.0);
  float shadow = heightAt(d + float2(-2.0, -3.0) * e) * (1.0 - hc);

  float ph = anim.z * 6.2831853;
  float2 q = d * 3.2;
  float c1 = sin(q.x * 1.7 + sin(q.y * 2.3 + ph) * 1.2 + ph);
  float c2 = sin(q.y * 2.1 - sin(q.x * 1.9 - ph) * 1.3 + 2.0 * ph);
  float caustic = pow(0.5 + 0.5 * c1 * c2, 3.0);
  float3 bed = mix(toLinear(float3(0.075, 0.300, 0.340)), toLinear(float3(0.030, 0.170, 0.215)), smoothstep(0.0, 1.0, r));
  bed += caustic * 0.022 * float3(0.5, 1.0, 0.95);
  bed += step(0.965, grainAt(floor(xy / 1.5))) * 0.02 * float3(1.0, 0.9, 0.7);
  bed *= 1.0 - 0.55 * shadow;

  float3 body = mix(toLinear(float3(0.900, 0.300, 0.370)), toLinear(float3(1.000, 0.530, 0.360)), smoothstep(0.1, 1.0, r));
  body = mix(body * float3(0.34, 0.22, 0.42), body, smoothstep(0.15, 0.75, hc));
  body = mix(body, toLinear(float3(1.000, 0.880, 0.720)), smoothstep(0.82, 1.0, hc) * 0.3);
  float3 coral = body * (0.42 + 0.74 * dif) + spec * 0.3 * float3(1.0, 0.95, 0.85);
  float cover = smoothstep(0.08, 0.3, hc);
  float3 col = mix(bed, coral, cover);

  float off = (r - (anim.y * 1.5 - 0.25)) / 0.17;
  float band = exp(-off * off) * sin(3.14159265 * anim.y);
  col += band * (cover * float3(1.0, 0.62, 0.45) * 0.5 + float3(0.01, 0.035, 0.035));

  float rim = smoothstep(0.5, 1.0, r);
  col *= 1.0 - 0.4 * rim * rim;
  col = clamp(col, 0.0, 1.0);
  if (clock.y > 0.5) {
    col = float3(
      dot(col, float3(0.8225, 0.1774, 0.0)),
      dot(col, float3(0.0332, 0.9669, 0.0)),
      dot(col, float3(0.0171, 0.0724, 0.9108)));
  }
  float alpha = clamp((1.0 - r) * res.x * 0.5, 0.0, 1.0);
  return half4(half3(pow(col, float3(1.0 / 2.2)) * alpha), alpha);
}
`;

const EFFECT = Skia.RuntimeEffect.Make(SKSL);

type Props = {
  /** the canvas's side, pt */
  size: number;
  /** seconds on the block's clock */
  clock: SharedValue<number>;
  level: SharedValue<number>;
  bloom: SharedValue<number>;
  phase: SharedValue<number>;
  /** the open radius as a fraction of the reef's */
  ring: SharedValue<number>;
  /** TOUCHES touches of x, y (pt), born (s), on */
  touches: SharedValue<number[]>;
};

export function CgsSkiaFallback({ size, clock, level, bloom, phase, ring, touches }: Props) {
  const uniforms = useDerivedValue(() => ({
    res: [size, size],
    anim: [level.get(), bloom.get(), phase.get(), ring.get()],
    clock: [clock.get(), WIDE],
    touches: touches.get(),
  }));
  if (!EFFECT) return null;
  return (
    <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
      <Fill>
        <Shader source={EFFECT} uniforms={uniforms} />
      </Fill>
    </Canvas>
  );
}
