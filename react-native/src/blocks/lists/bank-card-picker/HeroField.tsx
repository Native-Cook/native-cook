import { AlphaType, Blur, Canvas, ColorType, FilterMode, Group, Image, MipmapMode, Paint, Skia } from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { StyleSheet } from 'react-native';

import { HERO_FIELD, HERO_GRID } from './heroColours';

// draw past the canvas so the blur has colour to take in at the edges
const BLEED = 48;

// The hero's background: the sampled colour field stretched over it with linear filtering and
// softened so no cell edge shows.
export function HeroField({ width, height }: { width: number; height: number }) {
  const image = useMemo(() => {
    const { cols, rows } = HERO_GRID;
    const bytes = new Uint8Array(cols * rows * 4);
    HERO_FIELD.forEach((row, r) =>
      row.forEach((hex, c) => {
        const n = parseInt(hex.slice(1), 16);
        const o = (r * cols + c) * 4;
        bytes[o] = (n >> 16) & 255;
        bytes[o + 1] = (n >> 8) & 255;
        bytes[o + 2] = n & 255;
        bytes[o + 3] = 255;
      }),
    );
    return Skia.Image.MakeImage({ width: cols, height: rows, alphaType: AlphaType.Opaque, colorType: ColorType.RGBA_8888 }, Skia.Data.fromBytes(bytes), cols * 4);
  }, []);
  if (!image) return null;
  return (
    <Canvas style={[StyleSheet.absoluteFill, { width, height }]} pointerEvents="none">
      <Group layer={<Paint><Blur blur={14} mode="clamp" /></Paint>}>
        <Image image={image} x={-BLEED} y={-BLEED} width={width + BLEED * 2} height={height + BLEED * 2} fit="fill" sampling={{ filter: FilterMode.Linear, mipmap: MipmapMode.None }} />
      </Group>
    </Canvas>
  );
}
