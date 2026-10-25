import { BlurMask, Group, LinearGradient, RoundedRect, type SkFont, Text as SkiaText, vec } from '@shopify/react-native-skia';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

import { type Glyph } from './letterpressArt';
import { type Pose } from './letterpressMotion';

/** One metal type block: steel with a bevel, the letter in reverse on its face, a shadow below. */
export function TypeBlock({ index, glyph, size, font, pose }: { index: number; glyph: Glyph; size: number; font: SkFont; pose: SharedValue<Pose> }) {
  const x = glyph.x - 4;
  const y = glyph.baseline - size * 0.82;
  const w = glyph.w + 8;
  const h = size * 1.08;
  const cx = x + w / 2;
  const cy = y + h / 2;
  const transform = useDerivedValue(() => {
    const a = pose.get().block[index];
    return [{ translateY: -8 * (1 - a) }, { scale: 1 + 0.25 * (1 - a) }];
  });
  const opacity = useDerivedValue(() => {
    const p = pose.get();
    const a = p.block[index];
    if (a <= 0.001) return 0;
    return p.falling[index] ? Math.min(1, a / 0.3) : a * a;
  });
  // The shadow falls away from the lamp, further and softer the higher the block is.
  const shadow = useDerivedValue(() => {
    const p = pose.get();
    const lift = 1.5 + 16 * (1 - p.block[index]);
    return [{ translateX: -Math.cos(p.light) * lift }, { translateY: -Math.sin(p.light) * lift }];
  });
  const shadowBlur = useDerivedValue(() => 2 + 9 * (1 - pose.get().block[index]));
  const textX = glyph.x;
  return (
    <Group opacity={opacity}>
      <Group transform={shadow}>
        <RoundedRect x={x} y={y} width={w} height={h} r={3} color="rgba(40,26,14,0.42)">
          <BlurMask blur={shadowBlur} style="normal" />
        </RoundedRect>
      </Group>
      <Group transform={transform} origin={vec(cx, cy)}>
        <RoundedRect x={x} y={y} width={w} height={h} r={3}>
          <LinearGradient start={vec(x, y)} end={vec(x + w, y + h)} colors={['#6b6f78', '#43464d', '#2b2d32', '#3a3d43']} />
        </RoundedRect>
        <RoundedRect x={x + 0.5} y={y + 0.5} width={w - 1} height={h - 1} r={2.5} style="stroke" strokeWidth={1}>
          <LinearGradient
            start={vec(x, y)}
            end={vec(x + w, y + h)}
            colors={['rgba(255,255,255,0.35)', 'rgba(255,255,255,0.04)', 'rgba(0,0,0,0.4)']}
          />
        </RoundedRect>
        <Group transform={[{ scaleX: -1 }]} origin={vec(cx, cy)}>
          <SkiaText x={textX + 0.8} y={glyph.baseline + 0.8} text={glyph.ch} font={font} color="#1d1e22" />
          <SkiaText x={textX} y={glyph.baseline} text={glyph.ch} font={font} color="#b9bdc4" />
        </Group>
      </Group>
    </Group>
  );
}
