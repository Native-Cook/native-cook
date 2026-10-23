import { BackdropFilter, Group, Path, Rect, RuntimeShader, Shader, Skia } from '@shopify/react-native-skia';
import { useDerivedValue, useSharedValue, type SharedValue } from 'react-native-reanimated';

import type { FolderDrive, OrbDrive } from './burst-contract';
import { FOLDER } from './burst-contract';
import { folderBounds, folderPlacement, frontPanelPath } from './folder-geometry';
import { ANALYTIC_GLASS_SKSL, BACK_PANEL_SKSL, BACKDROP_GLASS_SKSL, ORB_SKSL } from './folder-shaders';

function effect(source: string) {
  const compiled = Skia.RuntimeEffect.Make(source);
  if (!compiled) throw new Error('Glass folder shader could not compile');
  return compiled;
}
const backEffect = effect(BACK_PANEL_SKSL);
const frontEffect = effect(BACKDROP_GLASS_SKSL);
const analyticEffect = effect(ANALYTIC_GLASS_SKSL);
const orbEffect = effect(ORB_SKSL);

function useFolderValues(drive: FolderDrive) {
  const { cx, cy, scale, open, press, squash } = drive;
  return useDerivedValue(() => ({
    cx: cx.get(), cy: cy.get(), scale: scale.get(), open: open.get(), press: press.get(), squash: squash.get(),
  }));
}

export function FolderBack({ drive }: { drive: FolderDrive }) {
  const values = useFolderValues(drive);
  const { flow } = drive;
  const bounds = useDerivedValue(() => folderBounds(values.get()));
  const uniforms = useDerivedValue(() => {
    const v = values.get();
    const p = folderPlacement(v);
    return { center: [p.x, p.y], stretch: [p.sx, p.sy], opened: v.open, flow: flow.get() };
  });
  return <Rect rect={bounds}><Shader source={backEffect} uniforms={uniforms} /></Rect>;
}

export function FolderOrb({ orb, clock: suppliedClock }: { orb: OrbDrive; clock?: SharedValue<number> }) {
  const { x, y, r, glow, beat, mark } = orb;
  const frozenClock = useSharedValue(0);
  const clock = suppliedClock ?? frozenClock;
  const bounds = useDerivedValue(() => ({
    x: x.get() - r.get() * 2.2, y: y.get() - r.get() * 2.2, width: r.get() * 4.4, height: r.get() * 4.4,
  }));
  const uniforms = useDerivedValue(() => ({
    center: [x.get(), y.get()], radius: r.get(), glow: glow.get(), beat: beat.get(), mark: mark.get(), time: clock.get() / 1000,
  }));
  return <Rect rect={bounds}><Shader source={orbEffect} uniforms={uniforms} /></Rect>;
}

/** Optional orb makes the analytic fallback track an arbitrary parent-controlled flight.
 * Without it the fallback assumes the contract's stored disc, fading out as the folder opens.
 * Keep this component at canvas root: filter coordinates are global canvas points.
 */
export function FolderFront({ drive, width, height, refraction = 'backdrop', orb, clock: suppliedClock }: {
  drive: FolderDrive; width: number; height: number; refraction?: 'backdrop' | 'analytic'; orb?: OrbDrive; clock?: SharedValue<number>;
}) {
  const values = useFolderValues(drive);
  const { flow } = drive;
  const orbX = orb?.x;
  const orbY = orb?.y;
  const orbR = orb?.r;
  const orbGlow = orb?.glow;
  const orbBeat = orb?.beat;
  const orbMark = orb?.mark;
  const frozenClock = useSharedValue(0);
  const clock = suppliedClock ?? frozenClock;
  const pd = 1; // Both native shader modes receive canvas points (verified on iOS).
  const path = useDerivedValue(() => frontPanelPath(values.get()));
  const uniforms = useDerivedValue(() => {
    const v = values.get();
    const p = folderPlacement(v);
    return {
      center: [p.x, p.y], stretch: [p.sx, p.sy], opened: v.open, pressed: v.press,
      flow: flow.get(), time: clock.get() / 1000, pd, orbBeat: orbBeat ? orbBeat.get() : 0.2, orbMark: orbMark ? orbMark.get() : 0,
      orb: [orbX ? orbX.get() : p.x, orbY ? orbY.get() : p.y + FOLDER.orbStoredY * p.sy,
        orbR ? orbR.get() : FOLDER.orbR * p.sx * Math.max(0, 1 - v.open),
        orbGlow ? orbGlow.get() : 0.8 * Math.max(0, Math.min(1, 1 - v.open))],
    };
  });
  if (refraction === 'analytic') {
    return <Path path={path}><Shader source={analyticEffect} uniforms={uniforms} /></Path>;
  }
  return (
    <Group clip={{ x: 0, y: 0, width, height }}>
      <BackdropFilter clip={path} filter={<RuntimeShader source={frontEffect} uniforms={uniforms} />} />
    </Group>
  );
}
