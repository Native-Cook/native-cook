import { Canvas, Image as SkiaImage, type SkImage } from '@shopify/react-native-skia';

import type { SceneSizeProps } from '../types';
import { canvasStyle } from '../utils/geometry';

export function LoadingCanvas({ viewport }: Pick<SceneSizeProps, 'viewport'>) {
  return (
    <Canvas
      colorSpace="srgb"
      pointerEvents="none"
      style={canvasStyle(viewport)}
    />
  );
}

type StillCanvasProps = SceneSizeProps &
  Readonly<{
    frame: SkImage;
  }>;

export function StillCanvas({ frame, viewport }: StillCanvasProps) {
  return (
    <Canvas
      colorSpace="srgb"
      opaque
      pointerEvents="none"
      style={canvasStyle(viewport)}>
      <SkiaImage
        fit="cover"
        height={viewport.height}
        image={frame}
        width={viewport.width}
        x={0}
        y={0}
      />
    </Canvas>
  );
}
