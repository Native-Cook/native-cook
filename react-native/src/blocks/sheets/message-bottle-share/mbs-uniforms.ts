// Message Bottle Share — the shader's uniforms from the block's state.

import { type Layout } from './mbs-theme';

export type SeaState = { x: number; y: number; angle: number; scale: number; float: number; alpha: number; splash: number; t: number };

export function makeUniforms(L: Layout, s: SeaState, pixelRatio: number) {
  'worklet';
  return {
    uRes: [L.w, L.h],
    uHorizon: L.horizon,
    uPos: [s.x, s.y],
    uAngle: s.angle,
    uScale: s.scale,
    uFloat: s.float,
    uAlpha: s.alpha,
    uSplash: [L.landX, L.landY, s.splash],
    uT: s.t,
    uLoop: 10,
    uPx: 1 / pixelRatio,
  };
}

/** for stills */
export function stateAt(L: Layout, f: { t?: number }) {
  'worklet';
  return f as SeaState;
}
