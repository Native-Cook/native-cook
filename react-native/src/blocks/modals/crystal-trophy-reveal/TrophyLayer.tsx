// One bounded retained draw; the screen owns the Canvas and every animated value.
import { FilterMode, ImageShader, MipmapMode, Rect, Shader, Skia, useImage } from '@shopify/react-native-skia';
import { useEffect } from 'react';
import { PixelRatio } from 'react-native';
import { useDerivedValue } from 'react-native-reanimated';

import { STAGE_BACK_EFFECT, useStageBackUniforms } from './StageLayers';
import { CAMERA, CRYSTAL, type LayerProps } from './trophy-contract';
import { CRYSTAL_PLANES, PLINTH_PLANES, crystalRim, crystalSparkles } from './trophy-cut';
import { TROPHY_SKSL } from './trophy-shader';

const effect = Skia.RuntimeEffect.Make(TROPHY_SKSL);
if (!effect) throw new Error('Crystal Trophy Reveal: the trophy shader did not compile.');
const pixelRatio = PixelRatio.get();

export function Trophy({ frame, values, still = false, onReady }: LayerProps): React.JSX.Element | null {
  const stageUniforms = useStageBackUniforms({ frame, values, still });
  const engraving = useImage(require('./assets/trophy-engrave.png'));
  const marble = useImage(require('./assets/trophy-marble.png'));
  const ready = engraving !== null && marble !== null;
  useEffect(() => { onReady?.(ready); }, [ready, onReady]);
  const { yaw, rise, lamp, under, etch, touch, flash, touchX, touchY, time } = values;
  const { originX, originY, unit } = frame;
  const uniforms = useDerivedValue(() => {
    'worklet';
    const turn = yaw.get();
    return {
      uOrigin: [originX, originY], uUnit: unit, uPixelRatio: pixelRatio,
      uCamera: [CAMERA.distance, CAMERA.elevation], uTurn: [Math.cos(turn), Math.sin(turn)],
      uPlanes: CRYSTAL_PLANES, uBase: PLINTH_PLANES, uRim: crystalRim(turn, rise.get(), frame),
      uFire: crystalSparkles(turn, rise.get(), flash.get(), frame),
      uDrop: (1 - rise.get()) * CRYSTAL.travel,
      uLamp: Math.max(0, lamp.get()), uUnder: Math.max(0, under.get()), uEtch: etch.get(),
      uTouch: touch.get(), uFlash: flash.get(), uFinger: [touchX.get(), touchY.get()],
      uTime: still ? 0 : time.get() % 15,
    };
  });
  if (!engraving || !marble) return null;
  return (
    <Rect x={originX - unit * 1.6} y={originY - unit * 3.65} width={unit * 3.2} height={unit * 4.4}>
      <Shader source={effect!} uniforms={uniforms}>
        <Shader source={STAGE_BACK_EFFECT} uniforms={stageUniforms} />
        <ImageShader image={engraving} fit="none" sampling={{ filter: FilterMode.Linear, mipmap: MipmapMode.None }} />
        <ImageShader image={marble} fit="none" tx="repeat" ty="repeat" sampling={{ filter: FilterMode.Linear, mipmap: MipmapMode.None }} />
      </Shader>
    </Rect>
  );
}
