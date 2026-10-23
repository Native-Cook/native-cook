import { ImageShader, Rect, Shader, Skia, type SkImage } from '@shopify/react-native-skia';
import { useDerivedValue } from 'react-native-reanimated';
import { HERO_SHADOW_SKSL, HERO_SKSL, INNER_LIGHT_SKSL } from './metacard-shaders';
import { heroBounds, heroUniforms } from './metacard-timeline';
import type { MetacardValues } from './use-metacard-motion';

const heroEffect = Skia.RuntimeEffect.Make(HERO_SKSL), shadowEffect = Skia.RuntimeEffect.Make(HERO_SHADOW_SKSL);
const innerEffect = Skia.RuntimeEffect.Make(INNER_LIGHT_SKSL);
if (!heroEffect || !shadowEffect || !innerEffect) throw new Error('Metacard hero shader failed to compile.');
export function MetacardHero({ values, chip, reduced = false, still = 0 }: {
  values: MetacardValues; chip: SkImage; reduced?: boolean; still?: number;
}) {
  const { u, spin, clock, forward, objectPress, dragYaw, dragPitch } = values;
  const uniforms = useDerivedValue(() => heroUniforms(reduced ? still : u.get(), spin.get(), clock.get(), reduced ? 0 : forward.get(),
    objectPress.get(), dragYaw.get(), dragPitch.get(), reduced));
  const shadow = useDerivedValue(() => {
    const h = uniforms.get();
    return { centre: h.centre, halfSize: h.halfSize, morph: h.morph, shadowBlur: h.shadowBlur };
  });
  const bounds = useDerivedValue(() => {
    const h = uniforms.get(), r = Math.max(96, Math.hypot(h.halfSize[0], h.halfSize[1]) * 1.16);
    return { x: h.centre[0] - r - 64, y: h.centre[1] - r - 64, width: 2 * r + 128, height: 2 * r + 128 };
  });
  const objectBounds = useDerivedValue(() => heroBounds(uniforms.get()));
  return <>
    <Rect rect={bounds}><Shader source={shadowEffect!} uniforms={shadow} /></Rect>
    <Rect rect={bounds}><Shader source={innerEffect!} uniforms={uniforms} /></Rect>
    <Rect rect={objectBounds}><Shader source={heroEffect!} uniforms={uniforms}>
      <ImageShader image={chip} fit="none" />
    </Shader></Rect>
  </>;
}
