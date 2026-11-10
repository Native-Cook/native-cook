// Filament Bulb Dimmer — what the drawing layers read from the block's state: the scene the WebGPU
// layer renders (see gpu/FbdGpu.tsx, by index S) and the Skia fallback's uniforms.

import { lumOf, poseOf, radianceOf } from './fbd-motion';
import { accentLin, BULB, type Layout, lightColor } from './fbd-theme';

export type BulbState = { th: number; s: number; tw: number; heat: number; on: number; colour: number; t: number };

/** the scene, as the WebGPU layer reads it (indices in S) */
export function makeScene(L: Layout, st: BulbState) {
  'worklet';
  const p = poseOf(L, st.th, st.s);
  const lc = lightColor(st.heat, st.colour);
  const ac = accentLin(st.colour);
  const lum = lumOf(st.heat);
  return [st.t, p.hx, p.hy, p.tilt, st.tw, p.sag, st.heat, lum, radianceOf(st.heat), lc[0], lc[1], lc[2], st.on, ac[0], ac[1], ac[2], p.fx, p.fy];
}

/** the Skia fallback's uniforms: the same bulb, drawn flat */
export function makeUniforms(L: Layout, st: BulbState, pixelRatio: number) {
  'worklet';
  const lc = lightColor(st.heat, st.colour);
  // the fallback works in display-referred colour
  const tint = [Math.pow(lc[0], 1 / 2.2), Math.pow(lc[1], 1 / 2.2), Math.pow(lc[2], 1 / 2.2)];
  return {
    uRes: [L.w, L.h],
    uAnchor: [L.anchorX, L.anchorY],
    uCord: L.cord + BULB.socket * L.R,
    uR: L.R,
    uSwing: -st.th, // the flat shader turns the other way
    uPull: st.s,
    uPower: st.heat * (0.15 + 0.85 * st.heat),
    uTint: tint,
    uPx: 1 / pixelRatio,
  };
}
