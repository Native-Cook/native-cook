// Particle Ribbon Voice — a Frame (prv-motion.ts) → the shader's uniforms (prv-shaders.ts). One place, so the component and
// the offline harness feed the shader the same numbers.
import { smooth, type Frame } from './prv-motion';
import { ref } from './prv-theme';

export type Rgb = readonly [number, number, number];

export type Geometry = {
  /** canvas size, px */
  w: number;
  h: number;
  /** device px per design pt */
  s: number;
  /** the canvas's top, design pt */
  oy: number;
  /** 1 where Skia hands the output to a Display P3 screen (iOS) */
  p3: number;
  ramp: readonly [Rgb, Rgb, Rgb];
  bg: Rgb;
  bloom: number;
  gain: number;
  dust: number;
  /** half width at rest and at level 1 (design pt); the motion's own are `ref.ribbon.wdIdle` and `+ wdLevel` */
  width: readonly [number, number];
};

/** Map the sheet's half width from the pin's range (a thread … rest … peak) onto the host's. */
export function mapWidth(wd: number, lo: number, hi: number) {
  'worklet';
  const rest = ref.ribbon.wdIdle;
  const peak = ref.ribbon.wdIdle + ref.ribbon.wdLevel;
  if (wd <= rest) return (wd * lo) / rest;
  return lo + ((wd - rest) * (hi - lo)) / (peak - rest);
}

export function ribbonUniforms(f: Frame, g: Geometry) {
  'worklet';
  return {
    uRes: [g.w, g.h],
    uS: g.s,
    uOY: g.oy,
    uPh: f.phase,
    uLine: f.line,
    uSheet: [mapWidth(f.sheet[0], g.width[0], g.width[1]), f.sheet[1], f.sheet[2] * g.gain, f.sheet[3] * g.bloom],
    uTwA: f.twA,
    uTwB: f.twB,
    uThink: f.think,
    uPul: f.pul,
    uWin: f.win,
    uWinB: f.winB,
    uBump: f.bump,
    uOpt: [f.opt[0] * g.dust, f.opt[1], g.p3, f.opt[3]],
    uExtra: [f.extra[0], g.bg[0], g.bg[1], g.bg[2]],
    uC0: g.ramp[0],
    uC1: g.ramp[1],
    uC2: g.ramp[2],
    uDbg: 0,
  };
}

/** The ramp's colour at u (0 … 1 along the ribbon): the twin of rampAt() in the shader (sRGB 0 … 1). */
export function rampMix(ramp: readonly [Rgb, Rgb, Rgb], u: number): Rgb {
  'worklet';
  const a = smooth(0.3, 0.47, u);
  const b = smooth(0.49, 0.66, u);
  const r = ramp[0][0] + (ramp[1][0] - ramp[0][0]) * a;
  const g = ramp[0][1] + (ramp[1][1] - ramp[0][1]) * a;
  const bl = ramp[0][2] + (ramp[1][2] - ramp[0][2]) * a;
  return [r + (ramp[2][0] - r) * b, g + (ramp[2][1] - g) * b, bl + (ramp[2][2] - bl) * b];
}
