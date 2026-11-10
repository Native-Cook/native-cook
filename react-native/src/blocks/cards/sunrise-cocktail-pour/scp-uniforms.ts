// Sunrise Cocktail Pour — the shader's uniforms from the block's state.

import { type Layout } from './scp-theme';

export type DrinkState = { level: number; red: number; rise: number; mix: number; stream: number; swirl: number; shift: number; phase: number };

export function makeUniforms(L: Layout, s: DrinkState, pixelRatio: number) {
  'worklet';
  return {
    uRes: [L.w, L.h],
    uC: [L.heroX, L.heroY],
    uS: L.heroS,
    uShift: s.shift,
    uLevel: s.level,
    uRed: s.red,
    uRise: s.rise,
    uMix: s.mix,
    uStream: s.stream,
    uSwirl: s.swirl,
    uPhase: s.phase,
    uPx: 1 / pixelRatio,
  };
}

export function stateAt(_L: Layout, f: { level: number; red: number; rise: number; mix: number; stream: number; swirl: number; shift: number; t?: number }): DrinkState {
  'worklet';
  return { ...f, phase: (Math.PI * 2 * 3 * (f.t ?? 0)) / 10 };
}
