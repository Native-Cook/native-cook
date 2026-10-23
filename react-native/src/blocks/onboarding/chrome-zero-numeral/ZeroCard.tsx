// Chrome Zero — alpha-clipped card lighting, contact shadow and restrained parallax.
import { Canvas, Fill, ImageShader, Shader, Skia, type SkImage } from '@shopify/react-native-skia';
import Animated, { type SharedValue, useAnimatedStyle, useDerivedValue } from 'react-native-reanimated';
import { REST } from './zero-contract';
import type { ZeroLayout } from './zero-theme';
import type { ZeroFrame } from './zero-timeline';

const CARD_LIGHT = Skia.RuntimeEffect.Make(`
uniform shader cardImage;
uniform float2 dimensions;
uniform float sweep;
uniform float yaw;
uniform float strength;
uniform float fuse;
half4 main(float2 xy) {
  float2 uv = xy / dimensions;
  half4 card = cardImage.eval(uv * float2(972.0, 648.0));
  float2 shadowPoint = (uv - float2(0.635 - yaw * 0.045, 0.65)) / float2(0.23, 0.12);
  float contact = exp(-dot(shadowPoint, shadowPoint) * 1.8) * 0.48 * strength;
  float stripe = uv.x + uv.y * 0.37 - (sweep * 0.96 + 0.6);
  float sheen = exp(-stripe * stripe / 0.0028) * 0.26;
  sheen *= step(-0.999, sweep) * (1.0 - step(0.999, sweep));
  // One crisp boundary and a soft tail, about 10% of the card width. Yaw moves the light.
  float reflectedBand = uv.x + uv.y * 0.28 - (0.57 + yaw * 0.42);
  float hardLight = smoothstep(-0.004, 0.003, reflectedBand)
    * exp(-max(reflectedBand, 0.0) * max(reflectedBand, 0.0) / 0.0064) * 0.24;
  // A flattened, softly defocused zero below its foot; bead gaps reappear while held.
  float2 reflectedPoint = (uv - float2(0.645 - yaw * 0.045, 0.73)) / float2(0.25, 0.075);
  float oval = length(reflectedPoint);
  float rim = oval - 0.82;
  float smear = exp(-rim * rim / 0.15) * exp(-abs(reflectedPoint.y) * 0.5);
  float fragments = 0.18 + 0.82 * smoothstep(0.15, 0.85,
    0.5 + 0.5 * cos(reflectedPoint.x * 31.0 + reflectedPoint.y * 9.0));
  float reflection = smear * mix(fragments, 1.0, fuse) * strength * 0.13;
  float3 lightColour = float3(0.71, 0.79, 1.0);
  float3 lit = card.rgb * (1.0 - contact)
    + lightColour * (sheen + hardLight + reflection) * card.a;
  return half4(min(lit, float3(card.a)), card.a);
}`);
if (!CARD_LIGHT) throw new Error('Chrome Zero: card lighting shader did not compile.');

export function ZeroCard({ layout, frame, reduced, image: card }: {
  layout: ZeroLayout; frame: SharedValue<ZeroFrame>; reduced: boolean; image: SkImage | null;
}) {
  const { x, y, width, height } = layout.card; const k = layout.k;
  const style = useAnimatedStyle(() => {
    const f = frame.get(); const parallax = reduced ? 0 : Math.sin(f.yaw - REST.yaw);
    return { opacity: f.cardOpacity, transform: [
      { translateX: (-40 * (1 - f.card) - 6 * parallax) * k },
      { translateY: (64 * (1 - f.card) + 6 * f.cardExit + 2 * parallax) * k },
      { rotate: `${-10 * (1 - f.card) - 1.5 * parallax}deg` },
      { scale: 0.92 + 0.08 * f.card },
    ] };
  });
  const uniforms = useDerivedValue(() => {
    const f = frame.get();
    return { dimensions: [width, height], sweep: f.sweep,
      yaw: reduced ? 0 : Math.sin(f.yaw - REST.yaw), strength: f.build * (1 - f.drain), fuse: f.fuse };
  });
  return <Animated.View pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants"
    style={[{ position: 'absolute', left: x, top: y, width, height }, style]}>
    {card && <Canvas style={{ width, height }}><Fill>
      <Shader source={CARD_LIGHT!} uniforms={uniforms}><ImageShader image={card} fit="none" /></Shader>
    </Fill></Canvas>}
  </Animated.View>;
}
