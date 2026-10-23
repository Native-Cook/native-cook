/**
 * The machine on screen: its values become a turn, a slide and a few shader uniforms on the UI thread, and the
 * scene (LeverScene.tsx) draws them. No React state changes per frame, and no layer is blurred at run time: the
 * soft shadows, the glow and the steam are computed in the shaders.
 */
import { Canvas, type Transforms3d, type Uniforms } from '@shopify/react-native-skia';
import { PixelRatio, StyleSheet } from 'react-native';
import { useDerivedValue, type DerivedValue } from 'react-native-reanimated';

import { GaugeGlass, LeverScene, TimerGlass } from './LeverScene';
import { machineLook, type MachineFrame } from './lever-look';
import type { MachineValues } from './use-pull-lever';

const RAD = Math.PI / 180;

type Props = {
  values: MachineValues;
  width: number;
  height: number;
  /** The lever's pivot on screen, and the machine's scale. */
  px: number;
  py: number;
  scale: number;
  reduceMotion: boolean;
};

/** Every frame's look, derived once and shared by the scene and the gauge's glass. */
export function useMachineLook(values: MachineValues, reduceMotion: boolean) {
  const { angle, bar, zone, brew, ready, cupX, cupA, clock, puffAt } = values;
  const frame = useDerivedValue<MachineFrame>(() => ({
    angle: angle.get(),
    bar: bar.get(),
    zone: zone.get(),
    brew: brew.get(),
    ready: ready.get(),
    cupX: cupX.get(),
    cupA: cupA.get(),
    clock: clock.get(),
    puffAt: puffAt.get(),
    reduceMotion,
  }));
  return useDerivedValue(() => machineLook(frame.get()));
}

export function PullLever({ values, width, height, px, py, scale, reduceMotion }: Props) {
  const pixelRatio = PixelRatio.get();
  const aa = 1 / (scale * pixelRatio);
  const look = useMachineLook(values, reduceMotion);

  const turn = useDerivedValue<Transforms3d>(() => [{ rotate: values.angle.get() * RAD }]);
  const lever = useDerivedValue<Uniforms>(() => ({ uRot: look.get().rot, uAA: aa }));
  const body = useDerivedValue<Uniforms>(() => ({ uAA: aa, uZone: look.get().zone, uReady: look.get().ready }));
  const tray = useDerivedValue<Uniforms>(() => ({ uAA: aa, uReady: look.get().ready }));
  const cup = useDerivedValue<Uniforms>(() => ({ ...look.get().cup, uAA: aa }));
  const cupAt = useDerivedValue<Transforms3d>(() => [{ translateX: values.cupX.get() }]);
  const cupOpacity = useDerivedValue(() => values.cupA.get());
  const streams = useDerivedValue<Uniforms>(() => ({ ...look.get().streams, uAA: aa }));
  const steam = useDerivedValue<Uniforms>(() => look.get().steam);

  return (
    <Canvas style={[StyleSheet.absoluteFill, { width, height }]} pointerEvents="none">
      <LeverScene
        width={width}
        height={height}
        px={px}
        py={py}
        scale={scale}
        pixelRatio={pixelRatio}
        turn={turn}
        lever={lever}
        body={body}
        tray={tray}
        cup={cup}
        cupAt={cupAt}
        cupOpacity={cupOpacity}
        streams={streams}
        steam={steam}
      />
    </Canvas>
  );
}

/** The gauge's needle and glass, laid over its numerals. `size` is the bezel's diameter in the machine's units. */
export function GaugeWindow({ size, scale, needle }: { size: number; scale: number; needle: DerivedValue<number> }) {
  const aa = 1 / (scale * PixelRatio.get());
  const uniforms = useDerivedValue<Uniforms>(() => ({ uNeedle: needle.get(), uAA: aa }));
  return (
    <Canvas style={[StyleSheet.absoluteFill, { width: size * scale, height: size * scale }]} pointerEvents="none">
      <GaugeGlass size={size} scale={scale} needle={uniforms} />
    </Canvas>
  );
}

/** The timer's glass, laid over its drums. */
export function TimerWindow({ width, height }: { width: number; height: number }) {
  return (
    <Canvas style={[StyleSheet.absoluteFill, { width, height }]} pointerEvents="none">
      <TimerGlass width={width} height={height} />
    </Canvas>
  );
}
