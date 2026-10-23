import { Canvas, Fill, ImageShader, Shader, Skia, useImage } from '@shopify/react-native-skia';
import { useEffect, useState } from 'react';
import { View } from 'react-native';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

import { MEDAL_METALS } from './medal-orientation';
import { MEDAL_SKSL } from './medal-shader';

const effect = Skia.RuntimeEffect.Make(MEDAL_SKSL);
if (!effect) throw new Error('Day One Medal: the medal shader did not compile.');

const TILE = [512, 956];

export type MedalCanvasProps = {
  width: number;
  height: number;
  centre: SharedValue<{ x: number; y: number }>;
  half: SharedValue<number>;
  rotation: SharedValue<number[]>;
  light: SharedValue<{ x: number; y: number }>;
  glint: SharedValue<number>;
  heat: SharedValue<number>;
  charge: SharedValue<number>;
  stamp: SharedValue<number>;
  morph: SharedValue<number>;
  sweep: SharedValue<number>;
  engrave: SharedValue<number>;
  from: number;
  to: number;
  onReady?: (ready: boolean) => void;
};

/** All strips stay loaded so a milestone change does not interrupt the molten relief. */
export function MedalCanvas({
  width, height, centre, half, rotation, light, glint, heat, charge, stamp, morph, sweep, engrave, from, to, onReady,
}: MedalCanvasProps) {
  const dayOne = useImage(require('./assets/medal-front-1.png'));
  const daySeven = useImage(require('./assets/medal-front-7.png'));
  const dayThirty = useImage(require('./assets/medal-front-30.png'));
  const back = useImage(require('./assets/medal-back.png'));
  const [laidOut, setLaidOut] = useState(false);
  const ready = laidOut && !!(dayOne && daySeven && dayThirty && back);
  useEffect(() => { onReady?.(ready); }, [onReady, ready]);
  const fronts = [dayOne, daySeven, dayThirty];
  const fromIndex = Math.max(0, Math.min(2, Math.round(from)));
  const toIndex = Math.max(0, Math.min(2, Math.round(to)));
  const metal = MEDAL_METALS[fromIndex];
  const metalNext = MEDAL_METALS[toIndex];

  const uniforms = useDerivedValue(() => {
    'worklet';
    const at = centre.get();
    const lean = light.get();
    return {
      uCentre: [at.x, at.y],
      uHalf: half.get(),
      uRot: rotation.get(),
      uTile: TILE,
      uLight: [lean.x, lean.y],
      uGlint: glint.get(),
      uHeat: heat.get(),
      uCharge: charge.get(),
      uStamp: stamp.get(),
      uMorph: morph.get(),
      uMetal: metal,
      uMetalNext: metalNext,
      uSweep: sweep.get(),
      uEngrave: engrave.get(),
    };
  });

  if (!dayOne || !daySeven || !dayThirty || !back) return null;
  return (
    <View style={{ width, height }} pointerEvents="none"
      onLayout={event => setLaidOut(event.nativeEvent.layout.width > 0 && event.nativeEvent.layout.height > 0)}>
    <Canvas style={{ width, height }} pointerEvents="none">
      <Fill>
        <Shader source={effect!} uniforms={uniforms}>
          <ImageShader image={fronts[fromIndex]} fit="none" />
          <ImageShader image={fronts[toIndex]} fit="none" />
          <ImageShader image={back} fit="none" />
        </Shader>
      </Fill>
    </Canvas>
    </View>
  );
}
