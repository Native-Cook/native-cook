import { Canvas, Fill, Shader, Skia } from '@shopify/react-native-skia';
import { useEffect } from 'react';
import { PixelRatio } from 'react-native';
import { useAnimatedReaction, useDerivedValue, useSharedValue } from 'react-native-reanimated';

import { CAMERA, type IconStageProps } from './icon-contract';
import { ICON_SKSL } from './icon-shader';
import { advanceIconTimes, iconFrame } from './icon-timeline';

const effect = Skia.RuntimeEffect.Make(ICON_SKSL);
if (!effect) throw new Error('Icon Morph Sheet: the procedural clay icon shader failed to compile.');
const pixelRatio = PixelRatio.get();

export function IconStage({
  width, height, centreX, centreY, unit, position, clock, running,
  bounce, lean, press, reducedMotion, onReady,
}: IconStageProps) {
  const times = useSharedValue([0, 0, 0, 0]);
  useAnimatedReaction(
    () => [clock.get(), position.get(), running.get(), reducedMotion ? 1 : 0],
    (now, before) => {
      const current = times.get();
      const next = advanceIconTimes(current, now[1], before?.[1] ?? now[1],
        before ? now[0] - before[0] : 0, now[2], before?.[2] ?? 0, now[3] === 1);
      for (let i = 0; i < 4; i++) {
        if (next[i] !== current[i]) { times.set(next); break; }
      }
    },
  );
  const uniforms = useDerivedValue(() => {
    const t = times.get();
    return {
      uCentre: [centreX, centreY], uUnit: unit, uPixelRatio: pixelRatio, uCamera: CAMERA,
      ...iconFrame(position.get(), t[0], t[1], t[2], t[3],
        bounce.get(), lean.get(), press.get(), reducedMotion),
    };
  });
  useEffect(() => { onReady?.(true); }, [onReady]);
  return (
    <Canvas style={{ width, height }} pointerEvents="none">
      <Fill><Shader source={effect!} uniforms={uniforms} /></Fill>
    </Canvas>
  );
}
