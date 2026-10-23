// Quiet glossy decorations: a single atlas, with orbital drag, parallax and a damped burst kick.
import { Atlas, Skia, type SkImage, useRSXformBuffer } from '@shopify/react-native-skia';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

import type { WheelMotion } from './wheel-contract';
import { SKY_DOTS, SKY_STARS, skyPose, type ScreenLayout } from './wheel-theme';

const COUNT = SKY_STARS.length + SKY_DOTS.length;
const sprites = Array.from({ length: COUNT }, (_, i) => Skia.XYWHRect(
  (i < SKY_STARS.length ? SKY_STARS[i][4] : i % 4 === 0 ? 2 : 3) * 192, 0, 192, 192));

export function WheelSky({ layout, motion, clock, image, reduced }: {
  layout: ScreenLayout; motion: WheelMotion; clock: SharedValue<number>; image: SkImage; reduced: boolean;
}) {
  const { swirl, burst, tiltX, tiltY } = motion;
  const transforms = useRSXformBuffer(COUNT, (out, i) => {
    'worklet';
    const p = skyPose(layout, i, clock.get(), swirl.get(), burst.get(), tiltX.get(), tiltY.get(), reduced);
    const c = Math.cos(p.angle) * p.size / 192;
    const s = Math.sin(p.angle) * p.size / 192;
    out.set(c, s, p.x - 96 * c + 96 * s, p.y - 96 * s - 96 * c);
  });
  const colors = useDerivedValue(() => {
    const result = [];
    for (let i = 0; i < COUNT; i++) {
      const p = skyPose(layout, i, clock.get(), swirl.get(), burst.get(), tiltX.get(), tiltY.get(), reduced);
      result.push(Skia.Color(`rgba(255,255,255,${p.opacity})`));
    }
    return result;
  });
  return <Atlas image={image} sprites={sprites} transforms={transforms} colors={colors} colorBlendMode="modulate" />;
}
