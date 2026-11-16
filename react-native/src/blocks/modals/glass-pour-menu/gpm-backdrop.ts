import type { RefObject } from 'react';
import type { View } from 'react-native';
import { FilterMode, makeImageFromView, MipmapMode, Skia, TileMode, type SkImage, type SkShader } from '@shopify/react-native-skia';

export type GlassBackdrop = SkImage | SkShader | RefObject<View | null>;
export type BackdropPair = { sharp: SkImage; frost: SkImage; soft: SkImage };

/** Snapshot refs only on open; image/shader changes rebake once. Coordinates are local screen points. */
export async function bakeBackdrop(backdrop: GlassBackdrop, width: number, height: number): Promise<BackdropPair> {
  const captured = 'current' in backdrop;
  const source = captured ? await makeImageFromView(backdrop) : backdrop;
  if (!source) throw new Error('The backdrop view could not be captured');
  const surface = Skia.Surface.MakeOffscreen(Math.ceil(width), Math.ceil(height));
  if (!surface) throw new Error('The backdrop surface could not be allocated');
  const paint = Skia.Paint();
  try {
    const matrix = Skia.Matrix();
    if (source.__typename__ === 'Image') matrix.scale(width / source.width(), height / source.height());
    const shader =
      source.__typename__ === 'Image'
        ? source.makeShaderOptions(TileMode.Clamp, TileMode.Clamp, FilterMode.Linear, MipmapMode.None, matrix)
        : source;
    paint.setShader(shader);
    surface.getCanvas().drawPaint(paint);
    // Skia 2.6: copy each snapshot to the CPU, the canvas thread cannot sample this offscreen context's textures.
    const sharpTex = surface.makeImageSnapshot();
    const sharp = sharpTex.makeNonTextureImage() ?? sharpTex;
    const blurred = (sigma: number) => {
      surface.getCanvas().clear(Skia.Color('transparent'));
      paint.setImageFilter(Skia.ImageFilter.MakeBlur(sigma, sigma, TileMode.Clamp));
      surface.getCanvas().drawPaint(paint);
      const tex = surface.makeImageSnapshot();
      return tex.makeNonTextureImage() ?? tex;
    };
    // the frosted panel, and the softer copy the selection lozenge shows (FROST_BLUR / SOFT_BLUR in gpm-shaders.ts)
    const frost = blurred(10);
    const soft = blurred(3.5);
    return { sharp, frost, soft };
  } finally {
    paint.dispose();
    surface.dispose();
    if (captured) source.dispose();
  }
}
