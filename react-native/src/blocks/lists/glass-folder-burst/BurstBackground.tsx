import { Rect, Shader, Skia } from '@shopify/react-native-skia';
import type { SharedValue } from 'react-native-reanimated';
import { useDerivedValue, useSharedValue } from 'react-native-reanimated';

import type { FolderDrive, OrbDrive } from './burst-contract';
import { FOLDER } from './burst-contract';
import { folderPlacement, folderStoredLight } from './folder-geometry';
import { BACKGROUND_SKSL } from './folder-shaders';

function makeBackgroundEffect() {
  const source = Skia.RuntimeEffect.Make(BACKGROUND_SKSL);
  if (!source) throw new Error('Glass folder background could not compile');
  return source;
}
const backgroundEffect = makeBackgroundEffect();

export function BurstBackground({ width, height, drive, bloomX, bloomY, bloom, orb, clock: suppliedClock }: {
  width: number; height: number; drive: FolderDrive;
  bloomX: SharedValue<number>; bloomY: SharedValue<number>; bloom: SharedValue<number>;
  /** Supply the flight to make the floor caustic track exact disc coverage. */
  orb?: OrbDrive;
  clock?: SharedValue<number>;
}) {
  const { cx, cy, scale, open, press, squash, flow } = drive;
  const orbX = orb?.x;
  const orbY = orb?.y;
  const orbR = orb?.r;
  const orbGlow = orb?.glow;
  const frozenClock = useSharedValue(0);
  const clock = suppliedClock ?? frozenClock;
  const uniforms = useDerivedValue(() => {
    const v = { cx: cx.get(), cy: cy.get(), scale: scale.get(),
      open: open.get(), press: press.get(), squash: squash.get() };
    const p = folderPlacement(v);
    const ox = orbX ? orbX.get() : p.x;
    const oy = orbY ? orbY.get() : p.y + FOLDER.orbStoredY * p.sy;
    const radius = orbR ? orbR.get() : FOLDER.orbR * p.sx;
    const glow = orbGlow ? orbGlow.get() : 0.8 * Math.max(0, Math.min(1, 1 - v.open));
    return { size: [width, height], center: [p.x, p.y], scale: p.sy, flow: flow.get(),
      caustic: [Math.max(p.x - 50 * p.sx, Math.min(p.x + 50 * p.sx, ox)),
        folderStoredLight(v, ox, oy, radius, glow)],
      time: clock.get() / 1000, bloomCenter: [bloomX.get(), bloomY.get()], bloom: bloom.get() };
  });
  return <Rect x={0} y={0} width={width} height={height}><Shader source={backgroundEffect} uniforms={uniforms} /></Rect>;
}
