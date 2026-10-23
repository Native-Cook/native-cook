// One retained draw and one UI-thread uniform packet for either crystal control.
import { Rect, Shader, Skia } from '@shopify/react-native-skia';
import { PixelRatio } from 'react-native';
import { useDerivedValue } from 'react-native-reanimated';

import { CRYSTAL_PILL_SKSL } from './crystal-pill-shader';
import type { CrystalPillLayerProps } from './gem-contract';

const effect = Skia.RuntimeEffect.Make(CRYSTAL_PILL_SKSL);
if (!effect) throw new Error('Gem Orb Collect: crystal pill shader did not compile.');
const pixelWidth = 1 / PixelRatio.get();
const GLOW_PADDING = 40;

export function CrystalPillLayer({
  x, y, width, height, tone, press, glow, sheen, opacity, scale, time,
}: CrystalPillLayerProps) {
  const uniforms = useDerivedValue(() => ({
    uCentre: [x + width / 2, y + height / 2],
    uSize: [width, height],
    uTone: tone === 'capsule' ? 1 : 0,
    uPress: press.get(),
    uGlow: glow.get(),
    uSheen: sheen.get(),
    uOpacity: opacity.get(),
    uScale: scale.get(),
    uTime: time.get(),
    uPixelWidth: pixelWidth,
  }));

  return (
    <Rect x={x - GLOW_PADDING} y={y - GLOW_PADDING}
      width={width + 2 * GLOW_PADDING} height={height + 2 * GLOW_PADDING}>
      <Shader source={effect!} uniforms={uniforms} />
    </Rect>
  );
}
