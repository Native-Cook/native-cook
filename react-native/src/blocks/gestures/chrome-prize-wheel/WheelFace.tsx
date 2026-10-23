// Retained wheel drawing; the host owns the Canvas, layout, pointer and hero prize.
import {
  Blur, Circle, FilterMode, ImageShader, MipmapMode, RadialGradient, Rect, Shader, Skia, useImage,
} from '@shopify/react-native-skia';
import { useEffect } from 'react';
import { PixelRatio } from 'react-native';
import { useDerivedValue } from 'react-native-reanimated';

import type { WheelFaceProps } from './wheel-contract';
import { WHEEL_BLUR_CAP, WHEEL_SHUTTER, WHEEL_SKSL } from './wheel-shader';

const effect = Skia.RuntimeEffect.Make(WHEEL_SKSL);
if (!effect) throw new Error('Chrome Prize Wheel: wheel shader did not compile.');
const pixelRatio = PixelRatio.get();
const sampling = { filter: FilterMode.Linear, mipmap: MipmapMode.None };

export function WheelFace({
  cx, cy, radius, theta, omega, tiltX, tiltY, envTurn, swayA, swayB, winner, lift, glow,
  winnerPrize, winnerPrizeScale, dim, comet, hubPress, onReady,
}: WheelFaceProps) {
  const env = useImage(require('./assets/wheel-env.png'));
  const envSoft = useImage(require('./assets/wheel-env-soft.png'));
  const prizes = useImage(require('./assets/wheel-prizes.png'));
  const ready = !!(effect && env && envSoft && prizes);
  useEffect(() => { onReady?.(ready); }, [onReady, ready]);
  useEffect(() => () => { onReady?.(false); }, [onReady]);

  const uniforms = useDerivedValue(() => {
    'worklet';
    const rotation = theta.get() % (Math.PI * 2);
    return {
      uCentre: [cx, cy],
      uRadius: radius,
      uPixelRatio: pixelRatio,
      uTheta: rotation,
      uSpan: Math.max(-WHEEL_BLUR_CAP, Math.min(WHEEL_BLUR_CAP, omega.get() * WHEEL_SHUTTER)),
      // Eight milliradians of axle run-out makes the rotationally symmetric chrome breathe.
      uTilt: [tiltX.get() + 0.008 * Math.sin(rotation), tiltY.get() + 0.008 * Math.cos(rotation)],
      uEnvTurn: envTurn.get(),
      uSwayA: swayA.get(),
      uSwayB: swayB.get(),
      uWinner: winner.get(),
      uLift: lift.get(),
      uGlow: glow.get(),
      uWinnerPrize: winnerPrize.get(),
      uWinnerPrizeScale: winnerPrizeScale.get(),
      uDim: dim.get(),
      uComet: comet.get(),
      uHubPress: hubPress.get(),
    };
  });
  const bloomOpacity = useDerivedValue(() => 0.4 * (1 - 0.7 * dim.get()));

  if (!env || !envSoft || !prizes || radius <= 0) return null;
  return (
    <>
      <Circle cx={cx} cy={cy} r={radius * 1.35} opacity={bloomOpacity}>
        <RadialGradient c={{ x: cx, y: cy }} r={radius * 1.35}
          colors={['#2438E0', '#2438E0', '#2438E000']} positions={[0, 0.60, 1]} />
      </Circle>
      <Circle cx={cx + radius * 0.045} cy={cy + radius * 0.075} r={radius * 0.99} color="#000006D9">
        <Blur blur={radius * 0.055} />
      </Circle>
      <Rect x={cx - radius - 1} y={cy - radius - 1} width={radius * 2 + 2} height={radius * 2 + 2}>
        <Shader source={effect!} uniforms={uniforms}>
          <ImageShader image={env} fit="none" tx="repeat" ty="clamp" sampling={sampling} />
          <ImageShader image={envSoft} fit="none" tx="repeat" ty="clamp" sampling={sampling} />
          <ImageShader image={prizes} fit="none" tx="clamp" ty="clamp" sampling={sampling} />
        </Shader>
      </Rect>
    </>
  );
}
