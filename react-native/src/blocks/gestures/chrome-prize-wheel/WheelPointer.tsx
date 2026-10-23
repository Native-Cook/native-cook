// Hinged gem and registered shadow; both rotate about the collision pivot, with a strike light.
import { Blur, Circle, Group, Image, RadialGradient, type SkImage } from '@shopify/react-native-skia';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

import type { WheelMotion } from './wheel-contract';
import type { ScreenLayout } from './wheel-theme';

export function WheelPointer({ layout: l, motion, clock, gem, shadow, reduced }: {
  layout: ScreenLayout; motion: WheelMotion; clock: SharedValue<number>;
  gem: SkImage; shadow: SkImage; reduced: boolean;
}) {
  const { swing, tick, strikes, dim } = motion;
  const transform = useDerivedValue(() => [{ translateX: l.pointerX }, { translateY: l.pointerY }, { rotate: swing.get() }]);
  // The shadow translates in screen space, then follows precisely the same rotation as the gem.
  const shadowTransform = useDerivedValue(() => [{ translateX: l.pointerX + 3 * l.k },
    { translateY: l.pointerY + 4 * l.k }, { rotate: swing.get() }]);
  const opacity = useDerivedValue(() => 1 - 0.6 * dim.get());
  const light = useDerivedValue(() => (0.025 + (reduced ? 0 : 0.014 * Math.sin(clock.get() * 1.5)) + tick.get() * 0.65));
  const sparkle = useDerivedValue(() => [{ translateX: 40 * l.k }, { rotate: strikes.get() * 2.399963 }]);
  const white = useDerivedValue(() => tick.get() * 0.9);
  return <Group opacity={opacity}>
    <Group transform={shadowTransform}>
      <Image image={shadow} x={-20 * l.k} y={-36 * l.k} width={88 * l.k} height={72 * l.k} fit="fill" />
    </Group>
    <Group transform={transform}>
      <Image image={gem} x={-20 * l.k} y={-36 * l.k} width={88 * l.k} height={72 * l.k} fit="fill" />
      <Circle cx={20 * l.k} cy={0} r={25 * l.k} blendMode="plus" opacity={light}>
        <RadialGradient c={{ x: 20 * l.k, y: 0 }} r={25 * l.k} colors={['#FFD5FC', '#F834DA88', '#BD31FF00']} />
      </Circle>
      <Group transform={sparkle} opacity={white} blendMode="plus">
        <Circle cx={0} cy={0} r={3 * l.k} color="#FFF2FF"><Blur blur={2 * l.k} /></Circle>
        <Group transform={[{ scaleX: 0.14 }]}><Circle r={12 * l.k} color="#FFF2FF" /></Group>
        <Group transform={[{ scaleY: 0.14 }]}><Circle r={12 * l.k} color="#FFF2FF" /></Group>
      </Group>
    </Group>
  </Group>;
}
