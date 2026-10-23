import { Skia } from '@shopify/react-native-skia';

import { STAGE_SKSL } from './growth-stage-shader';
import { ODOMETER_SKSL } from './growth-odometer-shader';
import { clamp, visuals, type GrowthState } from './growth-sim';
import { LAST_MONTH } from './growth-physics';
import { type Metrics } from './growth-theme';

export { STAGE_SKSL, ODOMETER_SKSL };
export const STAGE_EFFECT = Skia.RuntimeEffect.Make(STAGE_SKSL);
export const ODOMETER_EFFECT = Skia.RuntimeEffect.Make(ODOMETER_SKSL);
if (!STAGE_EFFECT || !ODOMETER_EFFECT) throw new Error('Growth timeline: shader compilation failed');

const BLUR_SPEED_DIVISOR = 100; // digits/second per cell of exposure
const MAX_BLUR_CELLS = 0.22;

function drumBlur(speed: number) {
  'worklet';
  return clamp(speed / BLUR_SPEED_DIVISOR, -MAX_BLUR_CELLS, MAX_BLUR_CELLS);
}

// Layout arrays are immutable for a measured size, so the UI thread reuses them.
export function stageLayoutUniforms(layout: Metrics, pixelRatio: number) {
  return {
    geometry: [layout.width, layout.height, layout.cy, layout.railX],
    screen: [pixelRatio, layout.x0, 0, 0],
  };
}

export function stageUniforms(wheel: GrowthState, layout: ReturnType<typeof stageLayoutUniforms>) {
  'worklet';
  const effects = visuals(wheel);
  return {
    geometry: layout.geometry,
    light: [wheel.halo, effects.time, wheel.grip, effects.tick],
    hero: [effects.age, effects.surge, Math.max(0, wheel.p - LAST_MONTH), wheel.reduced ? 1 : 0],
    screen: layout.screen,
  };
}

export function numberUniforms(wheel: GrowthState, layout: Metrics, pixelRatio: number) {
  'worklet';
  const drums = wheel.drums;
  const effects = visuals(wheel);
  return {
    geometry: [Math.round(layout.x0 * pixelRatio) / pixelRatio, layout.cy, pixelRatio, wheel.halo],
    angles: [drums[0].theta, drums[1].theta, drums[2].theta, drums[3].theta],
    extra: [drums[4].theta, drumBlur(drums[0].omega), drumBlur(drums[1].omega), drumBlur(drums[2].omega)],
    motion: [drumBlur(drums[3].omega), drumBlur(drums[4].omega), effects.flash, effects.age],
    face: [effects.numberAlpha, wheel.reduced ? 1 : 0, 0, 0],
  };
}
