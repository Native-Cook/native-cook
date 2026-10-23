// Chrome Zero — chrome stars, a model-pinned additive glint and two planes of dust.
import { Blur, Canvas, Group, Image as SkImage, type SkImage as Sprite, useImage } from '@shopify/react-native-skia';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';
import { projectZero, REST } from './zero-contract';
import type { ZeroLayout } from './zero-theme';
import type { ZeroFrame } from './zero-timeline';

type Props = { layout: ZeroLayout; frame: SharedValue<ZeroFrame>; reduced: boolean };
type SparkProps = Props & { image: Sprite | null };

function Sparkle({ layout, frame, reduced, index, image: sprite }: SparkProps & { index: number }) {
  const { x, y, size } = layout.sparkles[index]; const k = layout.k;
  const alpha = useDerivedValue(() => frame.get().sparkleOpacity[index]);
  const transform = useDerivedValue(() => {
    const f = frame.get(); const p = f.sparkles[index];
    const parallax = reduced ? 0 : Math.sin(f.yaw - REST.yaw) * (index === 0 ? 4 : -3) * k;
    return [{ translateX: parallax }, { translateY: reduced ? 0 : f.drift * (index + 1) * k },
      { rotate: reduced ? 0 : f.sparkleRotation[index] }, { scale: p * f.twinkle[index] }];
  });
  return <Group opacity={alpha} transform={transform} origin={{ x, y }}>
    <SkImage image={sprite} x={x - size / 2} y={y - size / 2} width={size} height={size} fit="contain" />
  </Group>;
}

function Glint({ layout, frame, image: sprite }: Omit<SparkProps, 'reduced'>) {
  const { centreX, centreY, unit, k } = layout;
  const transform = useDerivedValue(() => {
    const f = frame.get();
    const p = projectZero(-0.42, 0.86, 0.26, f.yaw, f.pitch, f.scale, centreX, centreY, unit);
    return [{ translateX: p.x }, { translateY: p.y }, { scale: 0.7 + 0.5 * f.glint }];
  });
  const alpha = useDerivedValue(() => frame.get().glint);
  return <Group transform={transform} opacity={alpha} blendMode="plus">
    <SkImage image={sprite} x={-24 * k} y={-24 * k} width={48 * k} height={48 * k} fit="contain">
      <Blur blur={3 * k} />
    </SkImage>
    <SkImage image={sprite} x={-16 * k} y={-16 * k} width={32 * k} height={32 * k} fit="contain" />
  </Group>;
}

export function ZeroSparkles(props: SparkProps) {
  return <Canvas pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants"
    style={{ position: 'absolute', width: props.layout.width, height: props.layout.heroHeight }}>
    <Sparkle {...props} index={0} /><Sparkle {...props} index={1} /><Sparkle {...props} index={2} />
    <Glint layout={props.layout} frame={props.frame} image={props.image} />
  </Canvas>;
}

// x, y, size; positions are in the 402 × 874 reference space.
const FAR = [[62, 164, 6], [310, 112, 10], [358, 390, 8], [40, 452, 14], [272, 518, 8]];
const NEAR = [[46, 344, 14], [350, 494, 22], [288, 216, 10]];

function Mote({ layout, frame, reduced, near, index }: Props & { near: boolean; index: number }) {
  const sprite = useImage(require('./assets/zero-bokeh.png'));
  const [px, py, size] = (near ? NEAR : FAR)[index];
  const { k, offsetX } = layout;
  const alpha = useDerivedValue(() => near ? frame.get().nearDustOpacity[index] : frame.get().farDustOpacity[index]);
  const transform = useDerivedValue(() => {
    const f = frame.get();
    const yawShift = reduced ? 0 : Math.sin(f.yaw - REST.yaw) * (near ? -12 : -4);
    return [{ translateX: (yawShift + (reduced ? 0 : f.drift * (index + 2))) * k },
      { translateY: reduced ? 0 : f.drift * (near ? 6 : 3) * k }];
  });
  return <Group opacity={alpha} transform={transform}>
    <SkImage image={sprite} x={offsetX + (px - size / 2) * k} y={(py - size / 2) * k}
      width={size * k} height={size * k} fit="contain"><Blur blur={(near ? 2 : 0.3) * k} /></SkImage>
  </Group>;
}

export function ZeroDust(props: Props & { near?: boolean }) {
  const near = props.near ?? false;
  const count = near ? 3 : 5;
  return <Canvas pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants"
    style={{ position: 'absolute', width: props.layout.width, height: props.layout.heroHeight }}>
    {Array.from({ length: count }, (_, index) => <Mote key={index} {...props} near={near} index={index} />)}
  </Canvas>;
}
