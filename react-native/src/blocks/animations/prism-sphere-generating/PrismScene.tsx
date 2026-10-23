import { Fill, Rect, Shader, Skia } from '@shopify/react-native-skia';
import { useDerivedValue } from 'react-native-reanimated';

import type { PrismSceneProps } from './prism-contract';
import { PRISM_BACKDROP_SKSL, PRISM_SPHERE_SKSL } from './prism-shader';

// Compile the two analytic passes once; animation updates only the existing uniforms.
const backdropEffect = Skia.RuntimeEffect.Make(PRISM_BACKDROP_SKSL);
const sphereEffect = Skia.RuntimeEffect.Make(PRISM_SPHERE_SKSL);
if (!backdropEffect) throw new Error('Prism Sphere: backdrop SkSL failed to compile.');
if (!sphereEffect) throw new Error('Prism Sphere: sphere SkSL failed to compile.');

export function PrismBackdrop({ width, height, cx, cy, radius, scale, clock, energy, charge, flash, ring }: PrismSceneProps) {
  const uniforms = useDerivedValue(() => {
    'worklet';
    return {
      uSize: [width, height], uCentre: [cx, cy], uRadius: radius,
      uScale: scale.get(), uClock: clock.get(), uEnergy: energy.get(),
      uCharge: charge.get(), uFlash: flash.get(), uRing: ring.get(),
    };
  });
  return <Fill><Shader source={backdropEffect!} uniforms={uniforms} /></Fill>;
}

export function PrismSphere({ width, height, cx, cy, radius, rotation, fan, scale, clock, energy, sweep, sweepPower, charge, flash, light }: PrismSceneProps) {
  const uniforms = useDerivedValue(() => {
    'worklet';
    const m = rotation.get();
    return {
      uSize: [width, height], uCentre: [cx, cy], uRadius: radius,
      uScale: scale.get(), uClock: clock.get(), uEnergy: energy.get(),
      uCharge: charge.get(), uFlash: flash.get(),
      // SkSL stores columns; prism-math stores rows. Do not pass the row array directly.
      uRot: [m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8]],
      uFan: fan.get(), uSweep: sweep.get(), uSweepPower: sweepPower.get(), uLight: light.get(),
    };
  });
  const extent = radius * 1.12 * 1.05;
  return (
    <Rect x={cx - extent} y={cy - extent} width={extent * 2} height={extent * 2}>
      <Shader source={sphereEffect!} uniforms={uniforms} />
    </Rect>
  );
}
