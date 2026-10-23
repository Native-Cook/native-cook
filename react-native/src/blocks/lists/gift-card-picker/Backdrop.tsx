import { AlphaType, Blur, Canvas, ColorType, FilterMode, Group, Image, MipmapMode, Paint, Skia } from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { StyleSheet } from 'react-native';

import { BACKDROPS, BACKDROP_GRID } from './backdrops';

// One stock's backdrop: its sampled colour field, stretched over the sheet with linear
// filtering and softened so no cell edge shows.
export function BackdropField({ index, width, height }: { index: number; width: number; height: number }) {
  const image = useMemo(() => {
    const { cols, rows } = BACKDROP_GRID;
    const bytes = new Uint8Array(cols * rows * 4);
    BACKDROPS[index].forEach((row, r) =>
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
  }, [index]);
  if (!image) return null;
  return (
    <Canvas style={[StyleSheet.absoluteFill, { width, height }]} pointerEvents="none">
      <Group layer={<Paint><Blur blur={18} mode="clamp" /></Paint>}>
        <Image image={image} x={0} y={0} width={width} height={height} fit="fill" sampling={{ filter: FilterMode.Linear, mipmap: MipmapMode.None }} />
      </Group>
    </Canvas>
  );
}
