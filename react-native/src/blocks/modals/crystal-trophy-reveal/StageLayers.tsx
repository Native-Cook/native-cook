// Retained stage draws: one opaque backdrop, cone-bounded dust and two bounded velvet wings.
import { Fill, Group, Path, Rect, Shader, Skia, type SkRuntimeEffect } from '@shopify/react-native-skia';
import { useEffect } from 'react';
import { useDerivedValue } from 'react-native-reanimated';

import { STAGE_BACK_SKSL, STAGE_CURTAIN_SKSL, STAGE_DUST_SKSL, stageFlecks } from './stage-shader';
import type { LayerProps } from './trophy-contract';

const backEffect = Skia.RuntimeEffect.Make(STAGE_BACK_SKSL);
const curtainEffect = Skia.RuntimeEffect.Make(STAGE_CURTAIN_SKSL);
const dustEffect = Skia.RuntimeEffect.Make(STAGE_DUST_SKSL);
if (!backEffect || !curtainEffect || !dustEffect) {
  throw new Error('Crystal Trophy Reveal: a stage shader did not compile.');
}

/** The compiled back-of-stage shader, so the crystal can nest it as a child and refract the real stage. */
export const STAGE_BACK_EFFECT: SkRuntimeEffect = backEffect;

/** The uniforms `StageBack` draws with. The trophy layer calls this too, for its nested copy of the stage. */
export function useStageBackUniforms({ frame, values, still = false }: LayerProps) {
  const { time, lamp, beam, under, seam, rise, yaw, flash } = values;
  const { width, height, originX, originY, unit } = frame;
  return useDerivedValue(() => {
    'worklet';
    return {
      uSize: [width, height], uOrigin: [originX, originY], uUnit: unit,
      // Idle drift has a 15 s period (the self-playing loop's); 6000 s is a whole number of periods.
      uTime: still ? 0 : time.get() % 6000,
      uLamp: Math.max(0, lamp.get()), uBeam: beam.get(),
      uUnder: under.get(), uSeam: seam.get(), uRise: rise.get(), uFlash: flash.get(),
      uFlecks: stageFlecks(frame, yaw.get()),
    };
  });
}

export function StageBack(props: LayerProps): React.JSX.Element | null {
  const { onReady } = props;
  const uniforms = useStageBackUniforms(props);
  useEffect(() => { onReady?.(true); }, [onReady]);
  return <Fill><Shader source={STAGE_BACK_EFFECT} uniforms={uniforms} /></Fill>;
}

export function StageFront({ frame, values, still = false, onReady }: LayerProps): React.JSX.Element | null {
  const { time, lamp, beam, open, hem, shiver, seam } = values;
  const { width, height, originX, originY, unit, k } = frame;
  // Both front effects read this single uniform worklet. Additional worklets only bound draw geometry.
  const uniforms = useDerivedValue(() => {
    'worklet';
    return {
      uSize: [width, height], uOrigin: [originX, originY], uUnit: unit,
      uTime: still ? 0 : time.get() % 6000,
      uLamp: Math.max(0, lamp.get()), uBeam: beam.get(),
      uOpen: open.get(), uHem: hem.get(), uShiver: shiver.get(), uSeam: seam.get(),
    };
  });
  const curtainStrips = useDerivedValue(() => {
    'worklet';
    // 32 points include the ripple, rounded fold, rim bloom and floor shadow.
    const extent = Math.min(width / 2, Math.max(
      width * (0.5 - 0.35 * open.get()), width * (0.5 - 0.385 * hem.get()),
    ) + 32 * k);
    const path = Skia.PathBuilder.Make();
    path.addRect(Skia.XYWHRect(0, 0, extent, height));
    path.addRect(Skia.XYWHRect(width - extent, 0, extent, height));
    return path.build();
  });
  const dustBounds = useDerivedValue(() => {
    'worklet';
    const b = Math.max(0, Math.min(1, beam.get()));
    const poolX = width * 0.24 * (1 - b) + originX * b - 0.08 * width * Math.sin(b * Math.PI);
    const poolY = height * 0.17 * (1 - b) + originY * b;
    const lampX = originX + 0.32 * unit;
    const left = Math.max(0, Math.min(poolX - 115 * k, lampX - 10 * k));
    const right = Math.min(width, Math.max(poolX + 115 * k, lampX + 10 * k));
    return Skia.XYWHRect(left, 0, right - left, Math.max(0, poolY));
  });
  useEffect(() => { onReady?.(true); }, [onReady]);
  return (
    <Group>
      <Rect rect={dustBounds}><Shader source={dustEffect!} uniforms={uniforms} /></Rect>
      <Path path={curtainStrips}><Shader source={curtainEffect!} uniforms={uniforms} /></Path>
    </Group>
  );
}
