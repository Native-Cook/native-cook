import { Group, ImageShader, Rect, Shader, Skia, type SkImage, type SkPath } from '@shopify/react-native-skia';
import { type DerivedValue, type SharedValue, useDerivedValue } from 'react-native-reanimated';

import { pieceMotion, type Pose } from './waxSealMotion';
import { WAX_SKSL } from './waxSealShader';

/** Whether the seal is drawn as its pieces (cracked or falling) rather than whole. */
export function sealBroken(p: Pose) {
  'worklet';
  return p.crack > 0.001 || p.fall > 0;
}

/** A transform that puts a layer far off screen (cheaper than a group opacity of 0). */
export const OFF = [{ translateY: 100000 }];

export const sealEffect = Skia.RuntimeEffect.Make(WAX_SKSL);

export type WaxUniforms = { origin: number[]; scale: number; R: number; pad: number; gleam: number };

/** One piece of the broken seal: the wax under a wedge-shaped clip, thrown by its own motion. */
export function Piece({
  index,
  mid,
  wedge,
  pose,
  R,
  side,
  uniforms,
  mask,
  maskSide,
}: {
  index: number;
  mid: number;
  wedge: SkPath;
  pose: SharedValue<Pose>;
  R: number;
  side: number;
  uniforms: DerivedValue<WaxUniforms>;
  mask: SkImage;
  maskSide: number;
}) {
  const transform = useDerivedValue(() => {
    const p = pose.get();
    if (!sealBroken(p)) return OFF;
    const [dx, dy, rot, px, py] = pieceMotion(index, mid, p.fall, R);
    return [{ translateX: px + dx }, { translateY: py + dy }, { rotate: rot }, { translateX: -px }, { translateY: -py }];
  });
  if (!sealEffect) return null;
  return (
    <Group transform={transform}>
      <Group clip={wedge}>
        <Rect x={-side / 2} y={-side / 2} width={side} height={side}>
          <Shader source={sealEffect} uniforms={uniforms}>
            <ImageShader image={mask} fit="none" rect={{ x: 0, y: 0, width: maskSide, height: maskSide }} />
          </Shader>
        </Rect>
      </Group>
    </Group>
  );
}

