// Snow Globe Gift — the shader's uniforms from the block's state.

import { type Layout } from './sgg-theme';

export type GlobeState = { wobble: number; swirl: number; spin: number; lid: number; glow: number };

export function makeUniforms(L: Layout, s: GlobeState, pixelRatio: number) {
  'worklet';
  return {
    uRes: [L.w, L.h],
    uC: [L.heroX, L.heroY],
    uS: L.heroS,
    uWobble: s.wobble,
    uSwirl: s.swirl,
    uSpin: s.spin,
    uLid: s.lid,
    uGlow: s.glow,
    uPx: 1 / pixelRatio,
  };
}
