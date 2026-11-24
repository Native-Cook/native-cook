// Nebula Dust Swirl — what shows without WebGPU (Expo Go, an old device): an even sample of the same
// nebula from the same pose(), each grain a small circle in one of four tones, added together. A
// finger turns the dust around it (a closed-form stir, no state), which eases away with the hold.

import { Canvas, Group, Path, Skia, type SkPath } from '@shopify/react-native-skia';
import { StyleSheet } from 'react-native';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

import { CORE, DUST, TOUCH, pose, seedsOf } from './nds-motion';
import { FRAME } from './gpu/NdsGpu';

type Props = {
  frame: SharedValue<number[]>;
  width: number;
  height: number;
  centreX: number;
  centreY: number;
  unit: number;
};

/** the seeds of the sampled grains, worked out once */
const SAMPLE = Array.from({ length: DUST.fallback }, (_, n) => {
  const i = n * DUST.fallbackStep;
  return { arm: i & 1, s: seedsOf(i) };
});

/** core, warm inner arm, cool outer arm, star */
const TONES = ['rgba(255,214,160,0.55)', 'rgba(240,160,120,0.5)', 'rgba(130,170,225,0.5)', 'rgba(255,248,236,0.9)'];

export function NdsSkiaFallback({ frame, width, height, centreX, centreY, unit }: Props) {
  const paths = useDerivedValue(() => {
    const f = frame.get();
    const b = [Skia.PathBuilder.Make(), Skia.PathBuilder.Make(), Skia.PathBuilder.Make(), Skia.PathBuilder.Make()];
    if (!f || f.length < FRAME.length) return [b[0].build(), b[1].build(), b[2].build(), b[3].build()];
    const t = f[FRAME.t];
    const on = f[FRAME.touchOn] * f[FRAME.hold];
    const tx = f[FRAME.touchX];
    const ty = f[FRAME.touchY];
    const R2 = TOUCH.radius * TOUCH.radius * 1.6;
    for (let n = 0; n < SAMPLE.length; n++) {
      const s = SAMPLE[n].s;
      const q = pose(s, SAMPLE[n].arm, t);
      let x = centreX + q[0] * unit;
      let y = centreY + q[1] * unit;
      if (on > 0.001) {
        // turn the grain around the finger, more the nearer it is
        const dx = x - tx;
        const dy = y - ty;
        const a = on * 1.1 * Math.exp(-(dx * dx + dy * dy) / R2);
        const c = Math.cos(a);
        const si = Math.sin(a);
        x = tx + dx * c - dy * si;
        y = ty + dx * si + dy * c;
      }
      const core = s[6] < CORE.share;
      const star = !core && s[7] > 1 - DUST.starShare;
      const k = star ? 3 : core ? 0 : q[3] < 0.38 ? 1 : 2;
      const r = (star ? DUST.star : (DUST.size + DUST.sizeAdd * s[2] * s[2])) * q[2] * 1.6;
      b[k].addCircle(x, y, r);
    }
    return [b[0].build(), b[1].build(), b[2].build(), b[3].build()];
  });
  const core = useDerivedValue<SkPath>(() => paths.get()[0]);
  const warm = useDerivedValue<SkPath>(() => paths.get()[1]);
  const cool = useDerivedValue<SkPath>(() => paths.get()[2]);
  const stars = useDerivedValue<SkPath>(() => paths.get()[3]);
  return (
    <Canvas style={[StyleSheet.absoluteFill, { width, height }]} pointerEvents="none">
      <Group blendMode="plus">
        <Path path={cool} color={TONES[2]} />
        <Path path={warm} color={TONES[1]} />
        <Path path={core} color={TONES[0]} />
        <Path path={stars} color={TONES[3]} />
      </Group>
    </Canvas>
  );
}
