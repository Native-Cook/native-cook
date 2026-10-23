// Stateless two-depth atlas burst; near stars accelerate in apparent size as they pass the camera.
import { Atlas, Blur, Group, Skia, type SkImage, useRSXformBuffer } from '@shopify/react-native-skia';
import { useDerivedValue } from 'react-native-reanimated';

import type { WheelMotion } from './wheel-contract';
import { particlePose, type ScreenLayout } from './wheel-theme';

const COUNT = 88;
const sprites = Array.from({ length: COUNT }, (_, i) => {
  const n = i % 64;
  const tile = n % 7 < 4 ? n % 2 : n % 7 === 4 ? 2 : 3;
  return Skia.XYWHRect(tile * 192, 0, 192, 192);
});

export function WheelParticles({ layout, motion, image, near, reduced }: {
  layout: ScreenLayout; motion: WheelMotion; image: SkImage; near: boolean; reduced: boolean;
}) {
  const { burst, burstPower } = motion;
  const transforms = useRSXformBuffer(COUNT, (out, i) => {
    'worklet';
    const p = particlePose(layout, i, reduced ? -1 : burst.get(), burstPower.get());
    const c = Math.cos(p.angle) * p.size / 192;
    const s = Math.sin(p.angle) * p.size / 192;
    out.set(c, s, p.x - 96 * c + 96 * s, p.y - 96 * s - 96 * c);
  });
  const colors = useDerivedValue(() => {
    const result = [];
    for (let i = 0; i < COUNT; i++) {
      const p = particlePose(layout, i, reduced ? -1 : burst.get(), burstPower.get());
      result.push(Skia.Color(`rgba(255,255,255,${p.near === near ? p.opacity : 0})`));
    }
    return result;
  });
  const soft = useDerivedValue(() => near ? Math.max(0, burst.get() - 0.4) * 1.8 * layout.k : 0);
  return <Group>
    <Atlas image={image} sprites={sprites} transforms={transforms} colors={colors} colorBlendMode="modulate">
      <Blur blur={soft} />
    </Atlas>
  </Group>;
}
