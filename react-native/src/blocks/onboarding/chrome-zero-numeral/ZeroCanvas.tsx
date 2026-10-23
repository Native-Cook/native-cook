// One retained Skia draw; animation state crosses to the shader only through UI-thread uniforms.
import { Canvas, Fill, FilterMode, ImageShader, MipmapMode, Shader, Skia, useImage } from '@shopify/react-native-skia';
import { useEffect } from 'react';
import { PixelRatio } from 'react-native';
import { useDerivedValue } from 'react-native-reanimated';

import { CAMERA, PERCENT, type ZeroCanvasProps } from './zero-contract';
import { ZERO_SKSL } from './zero-shader';

const effect = Skia.RuntimeEffect.Make(ZERO_SKSL);
if (!effect) throw new Error('Chrome Zero Numeral: the chrome ray-marching shader did not compile.');
const pixelRatio = PixelRatio.get();

export function ZeroCanvas({
  width, height, centreX, centreY, unit, yaw, pitch, scale, build, drain, fuse,
  ripple, rippleX, rippleY, sweep, percent, flash, onReady,
}: ZeroCanvasProps) {
  const env = useImage(require('./assets/zero-env.png'));
  const ready = env !== null;
  useEffect(() => { onReady?.(ready); }, [onReady, ready]);

  const uniforms = useDerivedValue(() => {
    'worklet';
    const y = yaw.get();
    const p = pitch.get();
    return {
      uCentre: [centreX, centreY],
      uUnit: unit,
      uPixelRatio: pixelRatio,
      uCamera: CAMERA,
      uTurn: [Math.cos(y), Math.sin(y), Math.cos(p), Math.sin(p)],
      uDock: [PERCENT.x, PERCENT.y, PERCENT.z],
      uScale: scale.get(),
      uBuild: build.get(),
      uDrain: drain.get(),
      uFuse: fuse.get(),
      uRipple: ripple.get(),
      uRippleOrigin: [rippleX.get(), rippleY.get()],
      uSweep: sweep.get(),
      uPercent: percent.get(),
      uFlash: flash.get(),
    };
  });

  if (!env) return null;
  return (
    <Canvas style={{ width, height }} pointerEvents="none">
      <Fill>
        <Shader source={effect!} uniforms={uniforms}>
          <ImageShader image={env} fit="none" sampling={{ filter: FilterMode.Linear, mipmap: MipmapMode.None }} />
        </Shader>
      </Fill>
    </Canvas>
  );
}
