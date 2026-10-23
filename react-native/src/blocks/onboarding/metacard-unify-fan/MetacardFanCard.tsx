import { ImageShader, Rect, Shader, Skia, type SkImage } from '@shopify/react-native-skia';
import { useDerivedValue } from 'react-native-reanimated';
import { FAN_SHADOW_SKSL, FAN_SKSL } from './metacard-shaders';
import { fanBounds, fanUniforms } from './metacard-timeline';
import type { MetacardValues } from './use-metacard-motion';

const faceEffect = Skia.RuntimeEffect.Make(FAN_SKSL);
const shadowEffect = Skia.RuntimeEffect.Make(FAN_SHADOW_SKSL);
if (!faceEffect || !shadowEffect) throw new Error('Metacard fan shader failed to compile.');
export function MetacardFanCard({ index, face, shadow, values, seam = false, reduced = false }: {
  index: number; face: SkImage; shadow: SkImage; values: MetacardValues; seam?: boolean; reduced?: boolean;
}) {
  const { spin, anchorSpin, u, forward, fanPress } = values;
  const uniforms = useDerivedValue(() => fanUniforms(index, spin.get(), reduced ? 0 : u.get(), forward.get(), fanPress.get(), seam, anchorSpin.get()));
  const rect = useDerivedValue(() => fanBounds(uniforms.get()));
  const shadowRect = useDerivedValue(() => fanBounds(uniforms.get(), true));
  return <>
    <Rect rect={shadowRect}><Shader source={shadowEffect!} uniforms={uniforms}>
      <ImageShader image={shadow} fit="none" />
    </Shader></Rect>
    <Rect rect={rect}><Shader source={faceEffect!} uniforms={uniforms}>
      <ImageShader image={face} fit="none" />
    </Shader></Rect>
  </>;
}
