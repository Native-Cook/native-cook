import {
  Canvas,
  ColorMatrix,
  Group,
  Image as SkiaImage,
  Paint,
  RadialGradient,
  Rect,
  useImage,
  vec,
} from '@shopify/react-native-skia';
import { StyleSheet } from 'react-native';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

import type { SkyScreenTimeFrame } from './motion';

const SKY = require('./assets/sky.jpg') as number;
const CLOUDS = require('./assets/clouds-near.png') as number;

type Props = {
  frame: SharedValue<SkyScreenTimeFrame>;
  width: number;
  height: number;
  scale: number;
};

export function SkyBackdrop({ frame, width, height, scale }: Props) {
  const sky = useImage(SKY);
  const clouds = useImage(CLOUDS);
  const matrix = useDerivedValue(() => {
    const saturation = 1 - frame.get().storm * 0.65;
    const inverse = 1 - saturation;
    const red = 0.213 * inverse;
    const green = 0.715 * inverse;
    const blue = 0.072 * inverse;
    return [
      red + saturation, green, blue, 0, 0,
      red, green + saturation, blue, 0, 0,
      red, green, blue + saturation, 0, 0,
      0, 0, 0, 1, 0,
    ];
  });
  const washOpacity = useDerivedValue(() => 1 - frame.get().storm * 0.3);
  const cloudOpacity = useDerivedValue(() => frame.get().storm * 0.85);
  const cloudRect = useDerivedValue(() => {
    const storm = frame.get().storm;
    const factor = 1 + storm * 0.04;
    const cloudWidth = 430 * scale * factor;
    const cloudHeight = 500 * scale * factor;
    return {
      x: (-25 + storm * 30) * scale - (cloudWidth - 430 * scale) / 2,
      y: (30 + storm * 30) * scale - (cloudHeight - 500 * scale) / 2,
      width: cloudWidth,
      height: cloudHeight,
    };
  });
  const tintOpacity = useDerivedValue(() => frame.get().storm * 0.3);

  return (
    <Canvas style={[styles.canvas, { width, height }]} pointerEvents="none">
      <Group layer={<Paint><ColorMatrix matrix={matrix} /></Paint>}>
        {sky && <SkiaImage image={sky} x={0} y={0} width={width} height={height} fit="cover" />}
      </Group>
      <Rect x={0} y={0} width={width} height={height} opacity={washOpacity}>
        <RadialGradient
          c={vec(width * 0.5, 0.26 * height)}
          r={250 * scale}
          colors={['rgba(46,112,233,0.55)', 'rgba(46,112,233,0)']}
          positions={[0.32, 1]}
        />
      </Rect>
      {clouds && <SkiaImage image={clouds} rect={cloudRect} fit="contain" opacity={cloudOpacity} />}
      <Rect x={0} y={0} width={width} height={height} color="#1E325B" opacity={tintOpacity} />
    </Canvas>
  );
}

const styles = StyleSheet.create({
  canvas: {
    position: 'absolute',
    left: 0,
    top: 0,
  },
});
