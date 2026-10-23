import { clamp, smooth } from './growth-math';
import { type GrowthState } from './growth-state';
import { type Metrics } from './growth-theme';
import { visuals } from './growth-visuals';

const REFERENCE_WIDTH_PT = 402;
const REFERENCE_HEIGHT_PT = 874;
const BREATHING_DEPTH = 0.03;
const BREATHING_RADIANS_PER_SECOND = 1.3823;
const HERO_RADIUS_GAIN = 0.10;
const HERO_FIELD_GAIN = 0.38;
const OVERSCROLL_FIELD_GAIN = 0.5;
const LAST_MONTH = 12;
const TONE_MAP_CEILING = 1.10;
const TONE_MAP_EXPOSURE = 1.35;
const BAY = { offsetX: 60, radiusX: 90, radiusY: 100, strength: 0.78 };
const INNER_EDGE = { x: 220, bayWidth: 84, variance: 24000, feather: 65 };
const LOWER_FALLOFF = { start: 150, end: 400 };
const CROWN = { x: 430, y: -250, radiusX: 135, radiusY: 210 };
const CORE = { x: 452, y: -120, travelY: 150, radiusX: 95, radiusY: 170 };
const BRIDGE = { x: 455, y: -110, radiusX: 60, radiusY: 240 };
const TAIL = { x: 440, y: 250, radiusX: 120, radiusY: 200 };
const HAZE = { x: 430, y: -40, radiusX: 240, radiusY: 420 };
const GROWTH = {
  crownFloor: 0.10,
  crownGain: 1.50,
  crownFullLevel: 0.55,
  crownRadiusFloor: 0.45,
  crownRadiusGain: 0.55,
  lowerStartLevel: 0.50,
  coreGain: 1.10,
  coreRadiusFloor: 0.3,
  coreRadiusGain: 0.7,
  bridgeGain: 0.45,
  bridgeStartLevel: 0.25,
  tailGain: 0.30,
  hazeGain: 0.12,
  hazeStartLevel: 0.1,
  hazeLowerDim: 0.5,
  hazeFadeStartY: 230,
  hazeFadeEndY: 330,
};

function lobe(x: number, y: number, centerX: number, centerY: number, radiusX: number, radiusY: number) {
  'worklet';
  const normalizedX = (x - centerX) / radiusX;
  const normalizedY = (y - centerY) / radiusY;
  return Math.exp(-(normalizedX * normalizedX + normalizedY * normalizedY));
}

/** Match the stage's five lobes, bay and tone map, without the cosmetic noise warp. */
export function haloFieldAt(x: number, y: number, wheel: GrowthState, layout: Metrics) {
  'worklet';
  const haloLevel = clamp(wheel.halo, 0, 1);
  const effects = visuals(wheel);
  const pointX = x * REFERENCE_WIDTH_PT / layout.width;
  const pointY = (y - layout.cy) * REFERENCE_HEIGHT_PT / layout.height;
  const breathing = wheel.reduced
    ? 1
    : 1 + BREATHING_DEPTH * haloLevel * Math.sin(effects.time * BREATHING_RADIANS_PER_SECOND);
  const growth = (1 + HERO_RADIUS_GAIN * effects.surge) * breathing;
  const crownScale = (GROWTH.crownRadiusFloor + GROWTH.crownRadiusGain * haloLevel) * growth;
  let field = (GROWTH.crownFloor + GROWTH.crownGain * smooth(0, GROWTH.crownFullLevel, haloLevel))
    * lobe(pointX, pointY, CROWN.x, CROWN.y, CROWN.radiusX * crownScale, CROWN.radiusY * crownScale);
  const lowerGrowth = smooth(GROWTH.lowerStartLevel, 1, haloLevel);
  const coreScale = (GROWTH.coreRadiusFloor + GROWTH.coreRadiusGain * haloLevel) * growth;
  field += GROWTH.coreGain * lowerGrowth * lobe(
    pointX, pointY, CORE.x, CORE.y + CORE.travelY * haloLevel,
    CORE.radiusX * coreScale, CORE.radiusY * coreScale,
  );
  field += GROWTH.bridgeGain * smooth(GROWTH.bridgeStartLevel, 1, haloLevel) * lobe(
    pointX, pointY, BRIDGE.x, BRIDGE.y, BRIDGE.radiusX * growth, BRIDGE.radiusY * growth,
  );
  field += GROWTH.tailGain * lowerGrowth * lobe(
    pointX, pointY, TAIL.x, TAIL.y, TAIL.radiusX * growth, TAIL.radiusY * growth,
  );
  field *= 1 - smooth(LOWER_FALLOFF.start, LOWER_FALLOFF.end, pointY);
  field += GROWTH.hazeGain * smooth(GROWTH.hazeStartLevel, 1, haloLevel)
    * lobe(pointX, pointY, HAZE.x, HAZE.y, HAZE.radiusX, HAZE.radiusY)
    * (1 - GROWTH.hazeLowerDim * smooth(GROWTH.hazeFadeStartY, GROWTH.hazeFadeEndY, pointY));
  const bayX = (x - layout.x0 - BAY.offsetX) / BAY.radiusX;
  const bayY = (y - layout.cy) / BAY.radiusY;
  field *= 1 - BAY.strength * Math.exp(-(bayX * bayX + bayY * bayY));
  const innerEdge = INNER_EDGE.x + INNER_EDGE.bayWidth * Math.exp(-pointY * pointY / INNER_EDGE.variance);
  field *= smooth(innerEdge, innerEdge + INNER_EDGE.feather, pointX);
  field *= 1 + HERO_FIELD_GAIN * effects.surge + OVERSCROLL_FIELD_GAIN * Math.max(0, wheel.p - LAST_MONTH);
  return TONE_MAP_CEILING * (1 - Math.exp(-TONE_MAP_EXPOSURE * field));
}
