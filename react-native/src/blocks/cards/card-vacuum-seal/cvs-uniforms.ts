// Card Vacuum Seal — packs the film shader's uniforms from the geometry (fixed per card) and the film's state (per
// frame). One function for the canvas (a worklet on the UI thread) and for the offline renders in
// .work/card-vacuum-seal/tools, so what is reviewed is what ships.
import { lightDrift, type Look, shimmerAt } from './cvs-motion';

/** a card's geometry in the film canvas, in design units (1 unit = `S` screen pt) */
export type FilmGeom = {
  S: number;
  cx: number; // card centre in the canvas, design units
  cy: number;
  hw: number; // card half size, design units
  hh: number;
  radius: number; // corner radius, design units
  lip: number;
  ear: number;
  mapK: number; // snapshot px per design unit
};

/** everything that moves, besides `look` */
export type FilmInput = {
  look: Look;
  time: number; // s, the ambient clock: the light's drift and the glint across the letters
  azimuth: number; // rad, the key light's resting azimuth clockwise from 12 o'clock
  tilt: number; // rad added to it by the device's tilt
  dimpleX: number;
  dimpleY: number;
  dimpleK: number;
  fingerX: number;
  fingerY: number;
  fingerK: number;
  stretch: number;
  fade: number;
  seed: number;
  amp: number;
  extra: number;
  tint: readonly [number, number, number];
  creaseOn: number; // 1 when the baked crease map is decoded, 0 for the SkSL fallback
};

export function filmUniforms(g: FilmGeom, f: FilmInput) {
  'worklet';
  const l = f.look;
  return {
    uCreaseOn: f.creaseOn,
    uS: g.S,
    uC: [g.cx, g.cy],
    uCB: [g.hw, g.hh],
    uCR: g.radius,
    uLip: g.lip,
    uEar: g.ear,
    uP: l.p,
    uPuff: l.puff,
    uSettle: l.settle,
    uGlint: l.glint,
    uSweep: l.sweep,
    uSweepK: l.sweepK,
    uAz: f.azimuth + lightDrift(f.time) + f.tilt,
    uDimple: [f.dimpleX, f.dimpleY, f.dimpleK],
    uFinger: [f.fingerX, f.fingerY, f.fingerK],
    uStretch: f.stretch,
    uShimmer: shimmerAt(f.time),
    uFade: f.fade,
    uMapK: g.mapK,
    uSeed: f.seed,
    uAmp: f.amp,
    uExtra: f.extra,
    uTint: [f.tint[0], f.tint[1], f.tint[2]],
  };
}

/** '#RRGGBB' → [r, g, b] 0..1 (call at module scope, not in a worklet) */
export function hexToRgb(hex: string): [number, number, number] {
  const v = parseInt(hex.slice(1), 16);
  return [((v >> 16) & 255) / 255, ((v >> 8) & 255) / 255, (v & 255) / 255];
}
