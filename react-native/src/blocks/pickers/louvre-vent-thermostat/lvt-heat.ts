// Louvre Vent Thermostat: the heat ramp (spec §3, OKLCH-baked HEX) and every colour that follows the chamber's heat.
// All functions take the chamber's (lagging) temperature in °C and return sRGB triples 0..1.

import { clamp, mix, smooth } from './lvt-motion';

type RGB = [number, number, number];

const hex = (h: string): RGB => [parseInt(h.slice(1, 3), 16) / 255, parseInt(h.slice(3, 5), 16) / 255, parseInt(h.slice(5, 7), 16) / 255];

const mix3 = (a: RGB, b: RGB, k: number): RGB => {
  'worklet';
  return [mix(a[0], b[0], k), mix(a[1], b[1], k), mix(a[2], b[2], k)];
};

/** Piecewise-linear lookup in a table of [°C, colour]. */
const ramp = (h: number, xs: number[], cs: RGB[]): RGB => {
  'worklet';
  if (h <= xs[0]) return cs[0];
  for (let i = 1; i < xs.length; i++) {
    if (h <= xs[i]) return mix3(cs[i - 1], cs[i], (h - xs[i - 1]) / (xs[i] - xs[i - 1]));
  }
  return cs[cs.length - 1];
};
const rampN = (h: number, xs: number[], vs: number[]) => {
  'worklet';
  if (h <= xs[0]) return vs[0];
  for (let i = 1; i < xs.length; i++) {
    if (h <= xs[i]) return mix(vs[i - 1], vs[i], (h - xs[i - 1]) / (xs[i] - xs[i - 1]));
  }
  return vs[vs.length - 1];
};

const HX = [16, 20, 24, 28, 32];
const HC: RGB[] = [hex('#3E5568'), hex('#6A2A1C'), hex('#C2410C'), hex('#EB7F02'), hex('#FFB238')];
const HI = [0.35, 0.5, 0.72, 0.92, 1.05];

/** The chamber's light: colour, intensity, element bars (off when cold), white-hot core (from 25 °C). */
export const heatLight = (h: number) => {
  'worklet';
  return { rgb: ramp(h, HX, HC), i: rampN(h, HX, HI), elem: smooth(19.5, 27, h), core: smooth(25, 30, h) };
};

const GX = [17.5, 24, 30];
const G0: RGB[] = [hex('#2E404C'), hex('#EB7F02'), hex('#FFB238')];
const G1: RGB[] = [hex('#24323D'), hex('#69230F'), hex('#EB7F02')];
const G2: RGB[] = [hex('#121A20'), hex('#2B0C01'), hex('#69230F')];

/** The bottom glow: three stops, alpha and height (234 pt at rest, 380 at 30 °C, 120 cold). */
export const glowOf = (h: number) => {
  'worklet';
  return {
    g0: ramp(h, GX, G0),
    g1: ramp(h, GX, G1),
    g2: ramp(h, GX, G2),
    a: rampN(h, GX, [0.6, 1, 1]),
    h: rampN(h, [16, 17.5, 24, 30, 32], [110, 120, 234, 380, 420]),
  };
};

const EX = [17.5, 24, 30];
const EL: RGB[] = [hex('#0B1218'), hex('#2A0305'), hex('#420709')];
const ER: RGB[] = [hex('#0B1218'), hex('#2F0204'), hex('#4A080A')];
const DC: RGB[] = [hex('#27404F'), hex('#3A1418'), hex('#7A2A12')];

/** Screen-edge light, dot tint and dot strength. */
export const airOf = (h: number) => {
  'worklet';
  return {
    edgeL: ramp(h, EX, EL),
    edgeR: ramp(h, EX, ER),
    dot: ramp(h, EX, DC),
    dotA: rampN(h, EX, [0.6, 1, 1.15]),
    amp: rampN(h, EX, [0.6, 0.5, 1]),
  };
};

/** sRGB triple → '#rrggbb'. */
export const toHex = (c: RGB) => {
  'worklet';
  const f = (v: number) => {
    const s = Math.round(clamp(v, 0, 1) * 255).toString(16);
    return s.length === 1 ? `0${s}` : s;
  };
  return `#${f(c[0])}${f(c[1])}${f(c[2])}`;
};
