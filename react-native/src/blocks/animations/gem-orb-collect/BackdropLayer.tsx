// Full-page atmosphere; one draw and one UI-thread uniform packet per frame.
import { Fill, Shader, Skia } from '@shopify/react-native-skia';
import { PixelRatio } from 'react-native';
import { useDerivedValue } from 'react-native-reanimated';

import { BACKDROP_SKSL } from './backdrop-shader';
import { PALETTE, TIMING, type BackdropLayerProps } from './gem-contract';

const effect = Skia.RuntimeEffect.Make(BACKDROP_SKSL);
if (!effect) throw new Error('Gem Orb Collect: backdrop shader failed to compile.');
const pixelRatio = PixelRatio.get();
function rgb(hex: string): number[] {
  return [parseInt(hex.slice(1, 3), 16) / 255, parseInt(hex.slice(3, 5), 16) / 255, parseInt(hex.slice(5, 7), 16) / 255];
}
const top = rgb(PALETTE.pageTop);
const mid = rgb(PALETTE.pageMid);
const bloom = rgb(PALETTE.bloom);
const dust = rgb(PALETTE.dust);

export function BackdropLayer({
  width, height, gemX, gemY, gemRadius, yaw, pitch, charge, flare, hush, shock, beams, time,
}: BackdropLayerProps) {
  const uniforms = useDerivedValue(() => {
    'worklet';
    return {
      uSize: [width, height], uGem: [gemX.get(), gemY.get()], uRadius: gemRadius,
      uYaw: yaw.get(), uPitch: pitch.get(), uCharge: charge.get(), uFlare: flare.get(),
      uHush: hush.get(), uShock: shock.get(), uBeams: beams.get(),
      uPhase: time.get() * (2 * Math.PI / TIMING.loop), uPixelRatio: pixelRatio,
      uTop: top, uMid: mid, uBloom: bloom, uDust: dust,
    };
  });
  return <Fill><Shader source={effect!} uniforms={uniforms} /></Fill>;
}
