// Murmuration Flock — what shows without WebGPU (Expo Go, an old device): an even sample of the same
// flock from the same pose(), each bird a short stroke along its heading, in one Skia path.

import { Canvas, Path, Skia } from '@shopify/react-native-skia';
import { StyleSheet } from 'react-native';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

import { FLOCK, TOUCH, pose, seedsOf } from './mf-motion';
import { FRAME } from './gpu/MfGpu';

type Props = {
  frame: SharedValue<number[]>;
  width: number;
  height: number;
  centreX: number;
  centreY: number;
  unit: number;
};

const STEP = Math.floor(FLOCK.count / FLOCK.fallback);

export function MfSkiaFallback({ frame, width, height, centreX, centreY, unit }: Props) {
  const path = useDerivedValue(() => {
    const f = frame.get();
    const b = Skia.PathBuilder.Make();
    if (!f || f.length < FRAME.length) return b.build();
    const t = f[FRAME.t];
    const on = f[FRAME.touchOn] * f[FRAME.hold];
    const tx = f[FRAME.touchX];
    const ty = f[FRAME.touchY];
    const R2 = TOUCH.radius * TOUCH.radius * 2.2;
    for (let n = 0; n < FLOCK.fallback; n++) {
      const s = seedsOf(n * STEP);
      const p = pose(s[0], s[1], s[2], s[3], t);
      const q = pose(s[0], s[1], s[2], s[3], t - 0.05);
      let x = centreX + p[0] * unit;
      let y = centreY + p[1] * unit;
      if (on > 0.001) {
        const dx = x - tx;
        const dy = y - ty;
        const r2 = dx * dx + dy * dy;
        const push = (on * 52 * Math.exp(-r2 / R2)) / Math.sqrt(r2 + 1);
        x += dx * push - dy * push * 0.6;
        y += dy * push + dx * push * 0.6;
      }
      const hx = (p[0] - q[0]) * unit;
      const hy = (p[1] - q[1]) * unit;
      const len = Math.sqrt(hx * hx + hy * hy) + 0.001;
      const k = (1.6 + 1.8 * s[2]) / len;
      b.moveTo(x - hx * k, y - hy * k);
      b.lineTo(x + hx * k, y + hy * k);
    }
    return b.build();
  });
  return (
    <Canvas style={[StyleSheet.absoluteFill, { width, height }]} pointerEvents="none">
      <Path path={path} style="stroke" strokeWidth={1.5} strokeCap="round" color="rgba(24,18,38,0.72)" />
    </Canvas>
  );
}
