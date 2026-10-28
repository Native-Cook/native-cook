import { Canvas, Fill, Group, Shader, Skia, type Uniforms } from '@shopify/react-native-skia';
import { memo } from 'react';
import { StyleSheet } from 'react-native';
import { useDerivedValue, type SharedValue } from 'react-native-reanimated';

import type { TrophyApplauseFrame } from './TrophySavingsMilestoneCelebration';
import { clamp, smooth } from './TrophySavingsMilestoneMotion';

type Props = { frame: SharedValue<TrophyApplauseFrame> };

// Exact reference bowl, lip, collar, stem and foot in the shared 393×350 scene.
// The brown pedestal starts at y266 and occludes the last point of the gold foot.
const GOLD = Skia.Path.MakeFromSVGString(`
  M139 120C166 105 226 105 252 120C252 157 240 186 214 208
  C208 215 202 218 195.5 219C189 218 183 216 178 211C150 190 140 156 139 120Z
  M137.5 108.5C137.5 101.7 163.5 96.2 195.5 96.2
  C228.1 96.2 254.5 101.7 254.5 108.5L253 120C226 105 166 105 138.5 120Z
  M182.5 214.5H208.5Q211.5 214.5 211.5 217.5V221Q211.5 224 208.5 224
  H182.5Q179.5 224 179.5 221V217.5Q179.5 214.5 182.5 214.5Z
  M189 222C192 223 200 223 203.5 222V237C204.5 250 216 255 226.5 257
  V261C211 256 180 256 165 261V257C176 255 189 249 189 237Z
  M158.5 262C160.5 259 162.5 257.5 166.5 256.5
  C183 254.5 208 254.5 225 256.5C229 257.5 231.5 259 232.5 262L232 266H158.5Z
`);

// Compile once. This is a material reflection, not another ambient pool or moving star.
const GOLD_SWEEP = Skia.RuntimeEffect.Make(`
  uniform float sweep;
  uniform float strength;

  half4 main(float2 xy) {
    float curvedFace = (xy.x - 195.5) * (xy.x - 195.5) * 0.0007;
    float distanceToBand = xy.x + 0.28 * (xy.y - 160.0) + curvedFace - sweep;
    float narrow = distanceToBand / 3.4;
    float broad = distanceToBand / 9.0;
    float core = exp(-0.5 * narrow * narrow);
    float shoulder = exp(-0.5 * broad * broad);
    float stemFalloff = 1.0 - 0.18 * smoothstep(218.0, 266.0, xy.y);
    float alpha = strength * stemFalloff * (0.50 * core + 0.14 * shoulder);
    float3 gold = mix(float3(1.0, 0.84, 0.30), float3(1.0, 0.98, 0.82), core);
    return half4(gold * alpha, alpha);
  }
`);

/** One contact-led specular pass; frame ownership supplies pause/reset/reduced behavior. */
export const TrophySavingsMilestoneShimmer = memo(function TrophySavingsMilestoneShimmer({ frame }: Props) {
  const uniforms = useDerivedValue<Uniforms>(() => {
    const scene = frame.get();
    if (!scene.active || scene.reduced || scene.contactStart < 0) return { sweep: 123, strength: 0 };
    const age = (scene.clock - scene.contactStart) / Math.max(0.001, scene.cadence ?? 1);
    if (age < 0 || age >= 0.30) return { sweep: 123, strength: 0 };
    const travel = clamp(age / 0.22);
    // Fast arrival carries the brightest band across the bowl just before the gallery crest.
    const sweep = 123 + 160 * (1 - (1 - travel) * (1 - travel));
    const strength = smooth(age / 0.035) * (1 - smooth((age - 0.08) / 0.22));
    return { sweep, strength };
  });

  if (!GOLD_SWEEP || !GOLD) return null;
  return (
    <Canvas style={styles.canvas} pointerEvents="none" accessible={false}>
      <Group clip={GOLD}>
        <Fill><Shader source={GOLD_SWEEP} uniforms={uniforms} /></Fill>
      </Group>
    </Canvas>
  );
});

const styles = StyleSheet.create({
  canvas: { position: 'absolute', left: 0, top: 0, width: 393, height: 350 },
});
