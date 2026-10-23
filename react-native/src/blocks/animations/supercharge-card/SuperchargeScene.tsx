// One canvas keeps the material, live print, electricity and impact flash in the same frame.
import { Canvas, Fill, FilterMode, Group, ImageShader, MipmapMode, Shader, Skia, useImage } from '@shopify/react-native-skia';
import { type ReactNode, useEffect, useMemo } from 'react';
import { useDerivedValue } from 'react-native-reanimated';

import { BOLT_CENTRE, designFrame, PIVOT } from './supercharge-layout';
import { SUPERCHARGE_FLASH_SKSL, SUPERCHARGE_SKSL } from './supercharge-shader';
import type { SuperchargeValues } from './supercharge-types';

const effect = Skia.RuntimeEffect.Make(SUPERCHARGE_SKSL);
const flashEffect = Skia.RuntimeEffect.Make(SUPERCHARGE_FLASH_SKSL);
if (!effect || !flashEffect) throw new Error('Supercharge Card: a scene shader did not compile.');

type Props = {
  width: number;
  height: number;
  values: SuperchargeValues;
  onReady?: (ready: boolean) => void;
  children?: ReactNode;
};

export function SuperchargeScene({ width, height, values, onReady, children }: Props) {
  const paper = useImage(require('./assets/paper-strip.png'));
  const treeImage = useImage(require('./assets/tree-strip.png'));
  const ready = !!(paper && treeImage);
  useEffect(() => { onReady?.(ready); }, [onReady, ready]);

  const frame = designFrame(width, height);
  // These arrays are captured once per size change, never allocated by the frame worklet.
  const resolution = useMemo(() => [width, height], [width, height]);
  const placement = useMemo(() => [frame.x, frame.y, frame.k], [frame.x, frame.y, frame.k]);
  const {
    clock, card, sink, energy, glow, glyph, glyphScale, ring, ringLit, dim, flash,
    tree, treeGlow, pulse, shock, shaft, touch,
  } = values;
  const uniforms = useDerivedValue(() => {
    'worklet';
    const pose = card.get();
    return {
      resolution, frame: placement,
      uClock: clock.get(), uCard: pose, uSink: sink.get(), uEnergy: energy.get(),
      uGlow: glow.get(), uGlyph: glyph.get(), uGlyphScale: glyphScale.get(),
      uRing: ring.get(), uRingLit: ringLit.get(), uDim: dim.get(),
      uTree: tree.get(), uTreeGlow: treeGlow.get(), uPulse: pulse.get(),
      uShock: shock.get(), uShaft: shaft.get(), uTouch: touch.get(),
      uShake: Math.sqrt(pose[0] * pose[0] + pose[1] * pose[1]),
    };
  });
  const flashUniforms = useDerivedValue(() => {
    'worklet';
    const pose = card.get();
    const x = (BOLT_CENTRE.x - PIVOT.x) * pose[3];
    const y = (BOLT_CENTRE.y + sink.get() - PIVOT.y) * pose[3];
    const co = Math.cos(pose[2]), si = Math.sin(pose[2]);
    return {
      centre: [
        placement[0] + (PIVOT.x + pose[0] + co * x - si * y) * placement[2],
        placement[1] + (PIVOT.y + pose[1] + si * x + co * y) * placement[2],
      ],
      scale: placement[2], flash: flash.get(),
    };
  });

  if (!paper || !treeImage) return null;
  return (
    <Canvas style={{ width, height }} pointerEvents="none">
      <Fill>
        <Shader source={effect!} uniforms={uniforms}>
          <ImageShader image={paper} fit="none" sampling={{ filter: FilterMode.Linear, mipmap: MipmapMode.None }} />
          <ImageShader image={treeImage} fit="none" sampling={{ filter: FilterMode.Linear, mipmap: MipmapMode.None }} />
        </Shader>
      </Fill>
      <Group transform={[{ translateX: frame.x }, { translateY: frame.y }, { scale: frame.k }]}>
        {children}
      </Group>
      <Fill blendMode="plus">
        <Shader source={flashEffect!} uniforms={flashUniforms} />
      </Fill>
    </Canvas>
  );
}
