import { ImageShader, Shader, Skia, type SkImage } from '@shopify/react-native-skia';
import { useDerivedValue, type SharedValue } from 'react-native-reanimated';

import type { Rect } from './model';

const surface = Skia.RuntimeEffect.Make(`
uniform shader artwork;
uniform float4 textureRect;
uniform float strength;

half4 main(float2 xy) {
  half4 sampled = artwork.eval(xy);
  float3 print = float3(sampled.rgb) / max(float(sampled.a), 0.001);
  float2 uv = (xy-textureRect.xy)/max(textureRect.zw,float2(0.001));

  // The material was tuned on a photographed pack with a pink softbox crescent
  // below its upper-left seal. The reference video loses that reflection as the
  // front turns away. Subtract only its additive energy in that texture region.
  float flank = smoothstep(0.006,0.026,uv.x)
    * (1.0-smoothstep(0.105,0.235,uv.x));
  float upperPanel = smoothstep(0.158,0.175,uv.y)
    * (1.0-smoothstep(0.222,0.278,uv.y));
  // Registered reference crops measure excess RGB near [123,143,74]. Green
  // above the dark violet ink floor estimates that light, preserving printed
  // detail and the rest of the artwork outside the crescent.
  float deglare = max(print.g-0.10,0.0) * flank * upperPanel
    * clamp(strength,0.0,1.0);
  float3 color = max(print-deglare*float3(0.89,1.0,0.52),float3(0.0));
  return half4(color*sampled.a,sampled.a);
}
`);

type Props = {
  image: SkImage;
  textureRect: SharedValue<Rect>;
  strength: SharedValue<number>;
};

/** Texture-space light correction, so body deformation carries it with the ink. */
export function BodyMaterial({ image, textureRect, strength }: Props) {
  const uniforms = useDerivedValue(() => {
    const rect = textureRect.get();
    return {
      textureRect: [rect.x, rect.y, rect.width, rect.height],
      strength: strength.get(),
    };
  });
  if (!surface) throw new Error('Could not compile the pack body material');
  return (
    <Shader source={surface} uniforms={uniforms}>
      <ImageShader image={image} rect={textureRect} fit="fill" tx="clamp" ty="clamp" />
    </Shader>
  );
}
