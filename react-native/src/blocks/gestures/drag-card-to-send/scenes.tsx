/**
 * The Skia layers: the card's material with its shadow, and the liquid. They take plain numbers or animated values,
 * so the same elements draw on screen and in the offline renders.
 */
import { BlurMask, Group, Rect, RoundedRect, Shader, type Uniforms } from '@shopify/react-native-skia';
import type { DerivedValue } from 'react-native-reanimated';

import type { LiquidLook } from './liquid-look';
import { SHADERS } from './shaders';
import { COLORS, LAYOUT } from './theme';

type Num = number | DerivedValue<number>;

export function liquidUniforms(l: LiquidLook, aa: number): Uniforms {
  'worklet';
  return {
    uIsland: l.island,
    uBulb: l.bulb,
    uDrop: l.drop,
    uDir: l.dir,
    uNeck: [l.neck[0], l.neck[1], l.neck[2], l.neck[3]],
    uNeckR: l.neck[4],
    uLip: l.lip,
    uTail: l.tail,
    uShadow: l.shadow,
    uAA: aa,
  };
}

export function LiquidLayer({ width, height, uniforms }: { width: number; height: Num; uniforms: Uniforms | DerivedValue<Uniforms> }) {
  return (
    <Rect x={0} y={0} width={width} height={height}>
      <Shader source={SHADERS.liquid} uniforms={uniforms} />
    </Rect>
  );
}

/** How the card's two shadows read for a lift of 0 (in its slot) to 1 (stretched to the end of its tether). */
export function cardShadow(lift: number) {
  'worklet';
  return {
    contactY: 3 + 2 * lift,
    contactBlur: 4 + 2 * lift,
    contactOpacity: 0.16 - 0.06 * lift,
    ambientY: 14 + 12 * lift,
    ambientBlur: 16 + 10 * lift,
    ambientOpacity: 0.2 + 0.06 * lift,
  };
}

type CardProps = {
  width: number;
  height: number;
  pad: number;
  pixelRatio: number;
  uniforms: Uniforms | DerivedValue<Uniforms>;
  contactY: Num;
  contactBlur: Num;
  contactOpacity: Num;
  ambientY: Num;
  ambientBlur: Num;
  ambientOpacity: Num;
};

export function CardMaterial(p: CardProps) {
  const h = p.height;
  const r = LAYOUT.cardRadius;
  return (
    <Group transform={[{ translateX: p.pad }, { translateY: p.pad }]}>
      {/* Ambient: the card's own hue, broad; it grows as the card is pulled off its slot. */}
      <Group opacity={p.ambientOpacity}>
        <RoundedRect x={12} y={p.ambientY} width={p.width - 24} height={h} r={r} color={COLORS.cardShadow}>
          <BlurMask blur={p.ambientBlur} style="normal" />
        </RoundedRect>
      </Group>
      <Group opacity={p.contactOpacity}>
        <RoundedRect x={4} y={p.contactY} width={p.width - 8} height={h} r={r} color={COLORS.cardShadow}>
          <BlurMask blur={p.contactBlur} style="normal" />
        </RoundedRect>
      </Group>
      <Rect x={0} y={0} width={p.width} height={h}>
        <Shader source={SHADERS.card} uniforms={p.uniforms} />
      </Rect>
    </Group>
  );
}

export function cardUniforms(width: number, height: number, shift: number, press: number, aa: number): Uniforms {
  'worklet';
  return { uSize: [width, height], uR: LAYOUT.cardRadius, uAA: aa, uShift: shift, uPress: press };
}
