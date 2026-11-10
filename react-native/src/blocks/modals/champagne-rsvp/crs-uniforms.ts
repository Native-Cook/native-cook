// Champagne RSVP — the shader's uniforms from the block's state.

import { type Layout } from './crs-theme';

export type GlassState = { lean: number; clink: number; burst: number; phase: number };

export function makeUniforms(L: Layout, s: GlassState, pixelRatio: number) {
  'worklet';
  const S = L.heroS;
  // where the two rims meet: above the middle of the feet, at the rim's height
  const rimY = L.feetY - Math.cos(s.lean) * 2.62 * S;
  return {
    uRes: [L.w, L.h],
    uL: [L.feetX[0], L.feetY],
    uR: [L.feetX[1], L.feetY],
    uS: S,
    // the left glass leans by `lean` (positive = to the right), the right one is its mirror
    uRot: [s.lean, -s.lean],
    uClink: s.clink,
    uClinkAt: [L.w / 2, rimY],
    uBurst: s.burst,
    uPhase: s.phase,
    uPx: 1 / pixelRatio,
  };
}
