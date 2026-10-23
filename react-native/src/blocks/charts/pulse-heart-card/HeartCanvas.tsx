import { Canvas, Fill, Shader, Skia, type Uniforms } from '@shopify/react-native-skia';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

import { HEART, HEART_SKSL } from './heart-shader';

const HEART_EFFECT = Skia.RuntimeEffect.Make(HEART_SKSL);

/** Layout of the frame array written by the UI thread every frame. */
export const FRAME = {
  body: 0,
  lobe: 1,
  squash: 2,
  lift: 3,
  gloss: 4,
  light: 5,
  push: 6,
  flash: 7,
  tau0: 8,
  tau1: 9,
  phi: 10,
  traceStart: 11,
  traceEnd: 12,
  length: 13,
} as const;

/** The rest frame's pen position: no autoplay or scrub frame ever has it, so it marks "already at rest". */
export const REST_PHI = -99;

/** A heart at rest, with no trace (the pin's frame). */
export function restFrame(): number[] {
  'worklet';
  const frame = new Array<number>(FRAME.length).fill(0);
  frame[FRAME.tau0] = 99;
  frame[FRAME.tau1] = 99;
  frame[FRAME.phi] = REST_PHI;
  frame[FRAME.traceStart] = 0;
  frame[FRAME.traceEnd] = -1;
  return frame;
}

type Props = {
  top: number;
  width: number;
  scale: number;
  frame: SharedValue<number[]>;
};

/** The heart, its halo and the ECG trace: one RuntimeEffect over the canvas. */
export function HeartCanvas({ top, width, scale, frame }: Props) {
  const uniforms = useDerivedValue<Uniforms>(() => {
    const f = frame.get();
    return {
      scale,
      deform: [f[FRAME.body], f[FRAME.lobe], f[FRAME.squash], f[FRAME.lift]],
      shine: [f[FRAME.gloss], f[FRAME.light], f[FRAME.push], f[FRAME.flash]],
      taus: [f[FRAME.tau0], f[FRAME.tau1]],
      trace: [f[FRAME.phi], f[FRAME.traceStart], f[FRAME.traceEnd]],
    };
  });
  if (!HEART_EFFECT) return null;
  const height = (HEART.canvasBottom - HEART.canvasTop) * scale;
  return (
    <Canvas pointerEvents="none" style={{ position: 'absolute', left: 0, top, width, height }}>
      <Fill>
        <Shader source={HEART_EFFECT} uniforms={uniforms} />
      </Fill>
    </Canvas>
  );
}
