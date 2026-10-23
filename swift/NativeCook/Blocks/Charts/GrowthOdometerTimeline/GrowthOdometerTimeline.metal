#include <metal_stdlib>
using namespace metal;

namespace GrowthOdometerTimelineStage {
float mod(float a, float b) { return a - b * floor(a / b); }
float2 mod(float2 a, float b) { return a - b * floor(a / b); }
constexpr sampler grainSampler(coord::normalized, address::repeat, filter::nearest);
constexpr sampler digitSampler(coord::pixel, address::clamp_to_edge, filter::nearest);

constant float PAGE_FALLOFF = 1.7;
constant float NOTCH_HALF_HEIGHT_PT = 44.;
constant float NOTCH_DEPTH_PT = 14.;
constant float PI = 3.14159265;
constant float FOG_OFFSET_X_PT = 12.;
constant float FOG_RADIUS_X_PT = 75.;
constant float FOG_RADIUS_Y_PT = 110.;
constant float FOG_STRENGTH = .32;
constant float RAIL_FEATHER_PT = .7;
constant float FOG_BASE_GAIN = .75;
constant float FOG_HALO_GAIN = .25;
constant float FOG_GRIP_GAIN = .35;
constant float FOG_TICK_GAIN = .4;
constant float WAVE_ORIGIN_OFFSET_PT = 40.;
constant float WAVE_INNER_EDGE_PT = 6.;
constant float WAVE_OUTER_EDGE_PT = 2.;
constant float DOT_CELL_CENTER_PT = 3.;
constant float FRINGE_START_LEVEL = .10;
constant float FRINGE_FULL_LEVEL = .28;
constant float FRINGE_FADE_LEVEL = .55;
constant float FRINGE_END_LEVEL = .85;
constant float TWINKLE_REDUCED_GAIN = .8;
constant float TWINKLE_BASE_GAIN = .775;
constant float TWINKLE_DEPTH = .225;
constant float TWINKLE_PHASE_RADIANS = 6.283;
constant float TWINKLE_RADIANS_PER_SECOND = .8;
constant float DOT_OPACITY = .45;
constant float DOT_LOWER_GAIN = .28;
constant float DOT_FADE_OFFSET_PT = 100.;
constant float DOT_LIGHT_BOOST = .22;
constant float WAVE_DOT_GAIN = .9;
constant float WAVE_DOT_GLOW_VARIANCE_PT2 = 3.2;
constant float WAVE_DOT_GLOW_GAIN = .10;
constant float WAVE_RING_GAIN = .08;
constant float DITHER_BASE_GAIN = 3.;
constant float DITHER_LIGHT_GAIN = .08;
constant float DITHER_START_LEVEL = .08;
constant float DITHER_FULL_LEVEL = .35;
constant float RAMP_FOREST_LEVEL = .10;
constant float RAMP_OLIVE_LEVEL = .28;
constant float RAMP_MOSS_LEVEL = .48;
constant float RAMP_LIME_LEVEL = .68;
constant float RAMP_YELLOW_LEVEL = .86;
constant float RAMP_SHORT_WIDTH = .18;
constant float RAMP_MID_WIDTH = .20;
constant float RAMP_PALE_WIDTH = .14;
constant float2 HASH_SEED = float2(127.1, 311.7);
constant float HASH_GAIN = 43758.5453;
constant float2 CROWN_CENTER_PT = float2(430., -250.);
constant float2 CROWN_RADII_PT = float2(135., 210.);
constant float2 CORE_RADII_PT = float2(95., 170.);
constant float2 BRIDGE_CENTER_PT = float2(455., -110.);
constant float2 BRIDGE_RADII_PT = float2(60., 240.);
constant float2 TAIL_CENTER_PT = float2(440., 250.);
constant float2 TAIL_RADII_PT = float2(120., 200.);
constant float2 HAZE_CENTER_PT = float2(430., -40.);
constant float2 HAZE_RADII_PT = float2(240., 420.);
constant float3 FOREST_COLOR = float3(10, 31, 13) / 255.;
constant float3 OLIVE_COLOR = float3(30, 74, 26) / 255.;
constant float3 MOSS_COLOR = float3(70, 127, 39) / 255.;
constant float3 LIME_COLOR = float3(147, 194, 50) / 255.;
constant float3 YELLOW_COLOR = float3(221, 238, 85) / 255.;
constant float3 PALE_COLOR = float3(243, 249, 166) / 255.;
constant float3 WHITE_COLOR = float3(255, 255, 230) / 255.;
constant float3 WAVE_COLOR = float3(200, 255, 90) / 255.;
constant float3 WHEEL_FOG_COLOR = float3(63, 127, 31) / 255.;
constant float3 LUMINANCE_WEIGHTS = float3(.2126, .7152, .0722);
constant float CORE_TRAVEL_Y_PT = 150.;
constant float HAZE_FADE_START_PT = 230.;
constant float HAZE_FADE_END_PT = 330.;
constant float BREATHING_DEPTH = .03;
constant float BREATHING_RADIANS_PER_SECOND = 1.3823;
constant float HERO_RADIUS_GAIN = .10;
constant float WARP_X_FREQUENCY_PER_PT = .008;
constant float WARP_X_SPEED_PER_SECOND = .05;
constant float WARP_Y_FREQUENCY_PER_PT = .006;
constant float WARP_Y_SPEED_PER_SECOND = .045;
constant float WARP_Y_SEED = 7.;
constant float WARP_AMPLITUDE_PT = 28.;
constant float CROWN_BASE_FIELD = .10;
constant float CROWN_FIELD_GAIN = 1.50;
constant float CROWN_FULL_LEVEL = .55;
constant float CROWN_RADIUS_BASE = .45;
constant float CROWN_RADIUS_GAIN = .55;
constant float LOWER_START_LEVEL = .50;
constant float CORE_FIELD_GAIN = 1.10;
constant float CORE_CENTER_X_PT = 452.;
constant float CORE_START_Y_PT = -120.;
constant float CORE_RADIUS_BASE = .3;
constant float CORE_RADIUS_GAIN = .7;
constant float BRIDGE_FIELD_GAIN = .45;
constant float BRIDGE_START_LEVEL = .25;
constant float TAIL_FIELD_GAIN = .30;
constant float HAZE_FIELD_GAIN = .12;
constant float HAZE_START_LEVEL = .1;
constant float HAZE_LOWER_DIM = .5;
constant float BAY_OFFSET_X_PT = 60.;
constant float BAY_RADIUS_X_PT = 90.;
constant float BAY_RADIUS_Y_PT = 100.;
constant float INNER_FEATHER_PT = 65.;
constant float HERO_FIELD_GAIN = .38;
constant float OVERSCROLL_FIELD_GAIN = .5;
constant float TONE_MAP_CEILING = 1.10;
constant float TONE_MAP_EXPOSURE = 1.35;
constant float REFERENCE_WIDTH_PT = 402.;
constant float REFERENCE_HEIGHT_PT = 874.;
constant float HERO_WAVE_RADIUS_PT = 470.;
constant float HERO_WAVE_DURATION_SECONDS = .9;
constant float LOWER_FALLOFF_START_PT = 150.;
constant float LOWER_FALLOFF_END_PT = 400.;
constant float BAY_STRENGTH = .78;
constant float INNER_EDGE_PT = 220.;
constant float INNER_BAY_WIDTH_PT = 84.;
constant float INNER_BAY_VARIANCE_PT2 = 24000.;
constant float WAVE_FADE_START_PT = 300.;
constant float WAVE_FADE_END_PT = 465.;
constant float WAVE_WAKE_VARIANCE_PT2 = 1800.;
constant float DOT_GRID_PT = 6.;
constant float DOT_CORE_VARIANCE_PT2 = .64;


// width, height, centre y, rail x

// level, time, grip, tick

// age, surge, overscroll, reduced

// pixel ratio, x0, reserved, reserved

float hashPoint(float2 point) {
  return fract(sin(dot(point, HASH_SEED)) * HASH_GAIN);
}
float smoothNoise(float2 point) {
  float2 cellOrigin = floor(point);
  float2 fraction = fract(point);
  fraction = fraction * fraction * (3. - 2. * fraction);
  return mix(mix(hashPoint(cellOrigin), hashPoint(cellOrigin + float2(1, 0)), fraction.x),
    mix(hashPoint(cellOrigin + float2(0, 1)), hashPoint(cellOrigin + 1.), fraction.x), fraction.y);
}
float lobe(float2 point, float2 center, float2 radii) {
  float2 normalized = (point - center) / radii;
  return exp(-dot(normalized, normalized));
}
float3 ramp(float field) {
  float3 black = float3(0);
  float3 forest = FOREST_COLOR;
  float3 olive = OLIVE_COLOR;
  float3 moss = MOSS_COLOR;
  float3 lime = LIME_COLOR;
  float3 yellow = YELLOW_COLOR;
  float3 pale = PALE_COLOR;
  float3 white = WHITE_COLOR;
  if (field < RAMP_FOREST_LEVEL)  {
    return mix(black, forest, clamp(field / RAMP_FOREST_LEVEL, 0., 1.));
  }
  if (field < RAMP_OLIVE_LEVEL)  {
    return mix(forest, olive, (field - RAMP_FOREST_LEVEL) / RAMP_SHORT_WIDTH);
  }
  if (field < RAMP_MOSS_LEVEL)  {
    return mix(olive, moss, (field - RAMP_OLIVE_LEVEL) / RAMP_MID_WIDTH);
  }
  if (field < RAMP_LIME_LEVEL)  {
    return mix(moss, lime, (field - RAMP_MOSS_LEVEL) / RAMP_MID_WIDTH);
  }
  if (field < RAMP_YELLOW_LEVEL)  {
    return mix(lime, yellow, (field - RAMP_LIME_LEVEL) / RAMP_SHORT_WIDTH);
  }
  if (field < 1.)  {
    return mix(yellow, pale, (field - RAMP_YELLOW_LEVEL) / RAMP_PALE_WIDTH);
  }
  return mix(pale, white, clamp((field - 1.) / RAMP_FOREST_LEVEL, 0., 1.));
}
float4 render(float2 pixel, float4 geometry, float4 light, float4 hero, float4 screen, texture2d<float> grain) {
  float width = geometry.x;
  float height = geometry.y;
  float centerY = geometry.z;
  float railX = geometry.w;
  float haloLevel = clamp(light.x, 0., 1.);
  float time = light.y;
  float2 point = float2(pixel.x * REFERENCE_WIDTH_PT / width, (pixel.y - centerY)
    * REFERENCE_HEIGHT_PT / height);
  // Halo lobes: crown first, then the bridge, core and lower tail.
  float breathing = hero.w > .5 ? 1.: 1. + BREATHING_DEPTH * haloLevel * sin(time
    * BREATHING_RADIANS_PER_SECOND);
  float grow = (1. + HERO_RADIUS_GAIN * hero.y) * breathing;
  float2 warp = float2(smoothNoise(point * WARP_X_FREQUENCY_PER_PT + float2(time
    * WARP_X_SPEED_PER_SECOND, 0.)), smoothNoise(point * WARP_Y_FREQUENCY_PER_PT + float2(WARP_Y_SEED,
    time * WARP_Y_SPEED_PER_SECOND))) - .5;
  point += warp * WARP_AMPLITUDE_PT * haloLevel;
  // Separated light sources; the bottom fifth receives only the wide, dim haze.
  float field = (CROWN_BASE_FIELD + CROWN_FIELD_GAIN * smoothstep(0., CROWN_FULL_LEVEL, haloLevel))
    * lobe(point, CROWN_CENTER_PT, CROWN_RADII_PT * (CROWN_RADIUS_BASE + CROWN_RADIUS_GAIN
      * haloLevel) * grow);
  float lowerGrowth = smoothstep(LOWER_START_LEVEL, 1., haloLevel);
  field += CORE_FIELD_GAIN * lowerGrowth * lobe(point, float2(CORE_CENTER_X_PT, CORE_START_Y_PT
    + CORE_TRAVEL_Y_PT * haloLevel), CORE_RADII_PT * (CORE_RADIUS_BASE + CORE_RADIUS_GAIN * haloLevel)
      * grow);
  field += BRIDGE_FIELD_GAIN * smoothstep(BRIDGE_START_LEVEL, 1., haloLevel) * lobe(point,
    BRIDGE_CENTER_PT, BRIDGE_RADII_PT * grow);
  field += TAIL_FIELD_GAIN * lowerGrowth * lobe(point, TAIL_CENTER_PT, TAIL_RADII_PT * grow);
  field *= 1. - smoothstep(LOWER_FALLOFF_START_PT, LOWER_FALLOFF_END_PT, point.y);
  field += HAZE_FIELD_GAIN * smoothstep(HAZE_START_LEVEL, 1., haloLevel) * lobe(point, HAZE_CENTER_PT,
    HAZE_RADII_PT) * (1. - HAZE_LOWER_DIM * smoothstep(HAZE_FADE_START_PT, HAZE_FADE_END_PT, point.y));
  // Dark bay protects the identity and number from the halo.
  float2 bay = float2((pixel.x - screen.y - BAY_OFFSET_X_PT) / BAY_RADIUS_X_PT,
    (pixel.y - centerY) / BAY_RADIUS_Y_PT);
  field *= 1. - BAY_STRENGTH * exp(-dot(bay, bay));
  float innerEdge = INNER_EDGE_PT + INNER_BAY_WIDTH_PT * exp(-point.y * point.y / INNER_BAY_VARIANCE_PT2);
  field *= smoothstep(innerEdge, innerEdge + INNER_FEATHER_PT, point.x);
  field *= 1. + HERO_FIELD_GAIN * hero.y + OVERSCROLL_FIELD_GAIN * hero.z;
  // Compress before the ramp: bloom grows the falloff without clipping a white slab.
  // Tone map compresses the hot spot before the olive - to - yellow ramp.
  field = TONE_MAP_CEILING * (1. - exp(-TONE_MAP_EXPOSURE * field));
  // Page: a subtle radial lift keeps the black surface from looking flat.
  float2 pageUV = pixel / float2(width, height);
  float2 corner = pageUV - .5;
  float3 page = mix(float3(4, 5, 7), float3(10, 14, 20), exp(-dot(corner * PAGE_FALLOFF, corner
    * PAGE_FALLOFF))) / 255.;
  // Wheel fog: a local green reflection stops at the notched rail.
  float notch = abs(pixel.y - centerY) < NOTCH_HALF_HEIGHT_PT ? NOTCH_DEPTH_PT * (.5 + .5 * cos(PI
    * (pixel.y - centerY) / NOTCH_HALF_HEIGHT_PT)): 0.;
  float2 fogPoint = float2((pixel.x - (railX - FOG_OFFSET_X_PT)) / FOG_RADIUS_X_PT,
    (pixel.y - centerY) / FOG_RADIUS_Y_PT);
  float fogGlow = FOG_STRENGTH * exp(-dot(fogPoint, fogPoint))
    * (1. - smoothstep(railX - notch - RAIL_FEATHER_PT, railX - notch + RAIL_FEATHER_PT,
    pixel.x));
  fogGlow *= (FOG_BASE_GAIN + FOG_HALO_GAIN * haloLevel) * (1. + FOG_GRIP_GAIN * light.z
    + FOG_TICK_GAIN * light.w);
  page += WHEEL_FOG_COLOR * fogGlow;
  float3 color = 1. - (1. - page) * (1. - ramp(field));
  // Hero wave: a sharp light front with a soft trailing wake.
  float front = 0.;
  if (hero.x >= 0. && hero.x < HERO_WAVE_DURATION_SECONDS && hero.w < .5) {
    float waveProgress = hero.x / HERO_WAVE_DURATION_SECONDS;
    float waveRadius = HERO_WAVE_RADIUS_PT * (1. - (1. - waveProgress) * (1. - waveProgress));
    float frontOffset = length(float2((pixel.x - width - WAVE_ORIGIN_OFFSET_PT)
      * REFERENCE_WIDTH_PT / width, (pixel.y - centerY)*
      REFERENCE_HEIGHT_PT / height)) - waveRadius;
    // Sharp outer edge, 60 pt soft wake; disappear before reaching the left edge.
    front = (1. - smoothstep(-WAVE_INNER_EDGE_PT, WAVE_OUTER_EDGE_PT, frontOffset)) * exp(-frontOffset*
      frontOffset / WAVE_WAKE_VARIANCE_PT2);
    front *= 1. - smoothstep(WAVE_FADE_START_PT, WAVE_FADE_END_PT, waveRadius);
  }
  // Dot matrix: fine yellow points are visible only on the halo fringe.
  float2 gridIndex = floor(pixel / DOT_GRID_PT);
  float2 cellOffset = mod(pixel, DOT_GRID_PT) - DOT_CELL_CENTER_PT;
  float dotInk = exp(-dot(cellOffset, cellOffset) / DOT_CORE_VARIANCE_PT2);
  float fringe = smoothstep(FRINGE_START_LEVEL, FRINGE_FULL_LEVEL, field)
    * (1. - smoothstep(FRINGE_FADE_LEVEL, FRINGE_END_LEVEL, field));
  float twinkle = hero.w > .5 ? TWINKLE_REDUCED_GAIN: TWINKLE_BASE_GAIN + TWINKLE_DEPTH
    * sin(hashPoint(gridIndex) * TWINKLE_PHASE_RADIANS + time * TWINKLE_RADIANS_PER_SECOND);
  float dotAlpha = dotInk * fringe * DOT_OPACITY * mix(1., DOT_LOWER_GAIN,
    smoothstep(centerY - DOT_FADE_OFFSET_PT, height, pixel.y)) * twinkle;
  color = 1. - (1. - color) * (1. - ramp(field + DOT_LIGHT_BOOST) * dotAlpha);
  // The wave's colour is independent of the local halo: light points, never grey dots.
  float waveInk = dotInk * WAVE_DOT_GAIN + exp(-dot(cellOffset,
    cellOffset) / WAVE_DOT_GLOW_VARIANCE_PT2) * WAVE_DOT_GLOW_GAIN;
  color += WAVE_COLOR * front * (waveInk + WAVE_RING_GAIN);
  // Dither: blue noise breaks up 8 - bit gradients without a visible texture.
  float grainNoise = grain.sample(grainSampler, pixel * screen.x / float2(grain.get_width(), grain.get_height())).r - .5;
  color += grainNoise * (DITHER_BASE_GAIN / 255. + DITHER_LIGHT_GAIN * dot(color, LUMINANCE_WEIGHTS)
    * smoothstep(DITHER_START_LEVEL, DITHER_FULL_LEVEL, field));
  return float4(clamp(color, 0., 1.), 1.);
}

}

namespace GrowthOdometerTimelineNumber {
float mod(float a, float b) { return a - b * floor(a / b); }
float2 mod(float2 a, float b) { return a - b * floor(a / b); }
constexpr sampler grainSampler(coord::normalized, address::repeat, filter::nearest);
constexpr sampler digitSampler(coord::pixel, address::clamp_to_edge, filter::nearest);

constant int BLUR_TAP_COUNT = 10;
constant float BLUR_TAP_DIVISOR = 10.;
constant float BLUR_TAP_WEIGHT = .1;
constant float GLOW_KERNEL_WEIGHT_DIVISOR = 48.;
constant float GLOW_SAMPLE_X_PT = 1.5;
constant float GLOW_SAMPLE_Y_PT = 2.;
constant int GLOW_EXPOSURE_COUNT = 3;
constant float GLOW_EXPOSURE_DIVISOR = 3.;
constant float GLOW_EXPOSURE_OFFSET = .5;
constant float2 HASH_SEED = float2(127.1, 311.7);
constant float HASH_GAIN = 43758.5453;
constant float COMMA_END_PT = 34.;
constant float SHEEN_END_SECONDS = 1.2;
constant float3 COMMA_COLOR = float3(.96, .98, .98);
constant float3 DIGIT_COLOR = float3(.985, 1., .995);
constant float3 RIM_COLOR = float3(.776, 1., .290);
constant float3 FLASH_COLOR = float3(.949, 1., .659);
constant float3 GLOW_COLOR = float3(.70, 1., .25);
constant float RIM_HALO_GAIN = .85;
constant float RIM_STRENGTH = .30;
constant float SHEEN_TRAVEL_PT = 174.;
constant float SHEEN_OFFSET_PT = 26.;
constant float CYLINDER_SHADE_GAIN = .65;
constant float TEXTURE_SCALE = 3.;
constant float COMMA_ROW_Y_PT = 520.;
constant float WINDOW_FADE_START_PT = 21.;
constant float HUNDREDS_END_PT = 58.;
constant float TENS_END_PT = 82.;
constant float UNITS_END_PT = 106.;
constant float LEADING_CROP_X_PT = 4.;
constant float BLUR_EPSILON_CELLS = .001;
constant float RIM_BASE_GAIN = .15;
constant float FLASH_STRENGTH = .5;
constant float SHEEN_START_SECONDS = .5;
constant float SHEEN_SLOPE = .32;
constant float SHEEN_DURATION_SECONDS = .7;
constant float SHEEN_WIDTH_PT = 12.;
constant float SHEEN_STRENGTH = .5;
constant float CYLINDER_SHADE_BASE = .35;
constant float GLOW_THRESHOLD = .01;
constant float GLOW_STRENGTH = .28;
constant float CELL_HEIGHT_PT = 52.;
constant float WINDOW_HALF_HEIGHT_PT = 26.;
constant float CELL_WIDTH_PT = 24.;
constant float NUMBER_WIDTH_PT = 122.;
constant float CYLINDER_RADIUS_PT = 34.;
constant float LEADING_WIDTH_PT = 16.;
constant float RIM_SAMPLE_OFFSET_PT = 1.2;
constant float ASIN_LIMIT = .999;


// x0, cy, pixel ratio, halo

// units, tens, hundreds, thousands

// lead angle, blur units, tens, hundreds

// blur thousands, lead, flash, hero age

// alpha, reduced, unused, unused

float hashPoint(float2 point) {
  return fract(sin(dot(point, HASH_SEED)) * HASH_GAIN);
}
// Greyscale tiles encode coverage in red; color - managed RGB is not digit data.
float glyphCoverage(float sourceX, float sourceY, float drum, texture2d<float> digits) {
  float row = floor(sourceY / CELL_HEIGHT_PT);
  float tileY = mod(sourceY, CELL_HEIGHT_PT);
  if (drum > 3.5) {
    if (row != 1.)  {
      return 0.;
    }
    row = 1.;
  }
  else row = mod(row, 10.);
  if (sourceX < 0. || sourceX >= CELL_WIDTH_PT)  {
    return 0.;
  }
  return digits.sample(digitSampler, float2(sourceX * TEXTURE_SCALE, (row * CELL_HEIGHT_PT + tileY) * TEXTURE_SCALE)).r;
}
float4 render(float2 pixel, float4 geometry, float4 angles, float4 extra, float4 motion, float4 face, texture2d<float> digits) {
  // Slot lookup: the leading drum mechanically slides the lower group.
  float localX = pixel.x - geometry.x;
  float localY = pixel.y - geometry.y;
  float lead = clamp(extra.x, 0., 1.);
  if (abs(localY) > WINDOW_HALF_HEIGHT_PT || localX < 0. || localX > NUMBER_WIDTH_PT)  {
    return float4(0);
  }
  float sourceX = localX;
  float angle = extra.x;
  float blurCells = motion.y;
  float drum = 4.;
  float groupX = localX - LEADING_WIDTH_PT * lead;
  if (groupX >= 0.) {
    if (groupX < CELL_WIDTH_PT) {
      drum = 3.;
      angle = angles.w;
      blurCells = motion.x;
      sourceX = groupX;
    }
    else if (groupX < COMMA_END_PT) {
      float commaY = CYLINDER_RADIUS_PT * asin(clamp(localY / CYLINDER_RADIUS_PT, -ASIN_LIMIT, ASIN_LIMIT))+
        WINDOW_HALF_HEIGHT_PT;
      float coverage = commaY >= 0. && commaY < CELL_HEIGHT_PT
         ? digits.sample(digitSampler, float2((groupX - CELL_WIDTH_PT) * TEXTURE_SCALE,
        (COMMA_ROW_Y_PT + commaY) * TEXTURE_SCALE)).r: 0.;
      coverage *= 1. - smoothstep(WINDOW_FADE_START_PT, WINDOW_HALF_HEIGHT_PT, abs(localY));
      return float4(COMMA_COLOR * coverage, coverage) * face.x;
    }
    else if (groupX < HUNDREDS_END_PT) {
      drum = 2.;
      angle = angles.z;
      blurCells = extra.w;
      sourceX = groupX - COMMA_END_PT;
    }
    else if (groupX < TENS_END_PT) {
      drum = 1.;
      angle = angles.y;
      blurCells = extra.z;
      sourceX = groupX - HUNDREDS_END_PT;
    }
    else if (groupX < UNITS_END_PT) {
      drum = 0.;
      angle = angles.x;
      blurCells = extra.y;
      sourceX = groupX - TENS_END_PT;
    }
    else return float4(0);
  }
  else sourceX = localX + LEADING_CROP_X_PT;
  // Cylinder mapping: an inverse arc makes the window a curved drum face.
  float cylinderAngle = asin(clamp(localY / CYLINDER_RADIUS_PT, -ASIN_LIMIT, ASIN_LIMIT));
  float sourceY = WINDOW_HALF_HEIGHT_PT + CYLINDER_RADIUS_PT * cylinderAngle + angle * CELL_HEIGHT_PT;
  // Motion blur: jittered taps form a short directional exposure.
  float coverage = 0.;
  float rightCoverage = 0.;
  if (abs(blurCells) < BLUR_EPSILON_CELLS) {
    coverage = glyphCoverage(sourceX, sourceY, drum, digits);
    rightCoverage = glyphCoverage(sourceX + RIM_SAMPLE_OFFSET_PT, sourceY, drum, digits);
  }
  else {
    float jitter = hashPoint(floor(pixel * geometry.z));
    for (int tapIndex = 0;tapIndex < BLUR_TAP_COUNT;tapIndex++) {
      float blurOffset = -((float(tapIndex) + jitter) / BLUR_TAP_DIVISOR) * blurCells * CELL_HEIGHT_PT;
      coverage += glyphCoverage(sourceX, sourceY + blurOffset, drum, digits) * BLUR_TAP_WEIGHT;
      rightCoverage += glyphCoverage(
        sourceX + RIM_SAMPLE_OFFSET_PT, sourceY + blurOffset, drum, digits
      ) * BLUR_TAP_WEIGHT;
    }
  }
  // Light: cylinder shading, lime rim, hero flash and moving sheen.
  float fade = 1. - smoothstep(WINDOW_FADE_START_PT, WINDOW_HALF_HEIGHT_PT, abs(localY));
  float edge = max(coverage - rightCoverage, 0.);
  float3 tint = mix(DIGIT_COLOR, RIM_COLOR, edge * (RIM_BASE_GAIN + RIM_HALO_GAIN * geometry.w)
    * RIM_STRENGTH);
  tint = mix(tint, FLASH_COLOR, motion.z * FLASH_STRENGTH);
  float sheen = 0.;
  if (motion.w > SHEEN_START_SECONDS && motion.w < SHEEN_END_SECONDS && face.y < .5) {
    float band = (localX + localY
      * SHEEN_SLOPE - ((motion.w - SHEEN_START_SECONDS) / SHEEN_DURATION_SECONDS
        * SHEEN_TRAVEL_PT - SHEEN_OFFSET_PT)) / SHEEN_WIDTH_PT;
    sheen = exp(-band * band) * SHEEN_STRENGTH;
  }
  tint = mix(tint, float3(1.), sheen);
  float shade = CYLINDER_SHADE_BASE + CYLINDER_SHADE_GAIN * cos(cylinderAngle);
  float alpha = coverage * fade;
  float outerGlow = 0.;
  if (motion.z > GLOW_THRESHOLD) {
    // Small Gaussian kernel over the same exposure as the moving glyph. Shifted
    // unblurred copies would leave rectangular ghost outlines during the flash.
    float softCoverage = 0.;
    for (int kernelX = -1; kernelX <= 1; kernelX++) {
      for (int kernelY = -1; kernelY <= 1; kernelY++) {
        float weight = (kernelX == 0 ? 2. : 1.) * (kernelY == 0 ? 2. : 1.) / GLOW_KERNEL_WEIGHT_DIVISOR;
        for (int exposureIndex = 0; exposureIndex < GLOW_EXPOSURE_COUNT; exposureIndex++) {
          softCoverage += glyphCoverage(
            sourceX + float(kernelX) * GLOW_SAMPLE_X_PT,
            sourceY + float(kernelY) * GLOW_SAMPLE_Y_PT - blurCells * CELL_HEIGHT_PT
              * (float(exposureIndex) + GLOW_EXPOSURE_OFFSET) / GLOW_EXPOSURE_DIVISOR,
            drum, digits
          ) * weight;
        }
      }
    }
    outerGlow = max(softCoverage - coverage, 0.) * GLOW_STRENGTH * motion.z * fade;
  }
  float3 lightColor = tint * alpha * shade + GLOW_COLOR * outerGlow;
  // Valid premultiplied light, drawn with Plus on the destination paint.
  // Alpha never erases the page; outside ink/glow all four channels are zero.
  float outputCoverage = max(alpha, max(lightColor.r, max(lightColor.g, lightColor.b)));
  return float4(lightColor, outputCoverage) * face.x;
}

}


struct GrowthOdometerTimelineUniforms {
    float4 geometry;
    float4 light;
    float4 hero;
    float4 screen;
    float4 numberGeometry;
    float4 angles;
    float4 extra;
    float4 motion;
    float4 face;
};
struct GrowthOdometerTimelineVertex {
    float4 position [[position]];
};
vertex GrowthOdometerTimelineVertex growthOdometerTimelineVertex(uint id [[vertex_id]]) {
    float2 points[3] = {float2(-1, -1), float2(3, -1), float2(-1, 3)};
    return {float4(points[id], 0, 1)};
}
// Integrate the blurred stroke alpha rather than adding a sharp shadow source.
// The vertical convolution follows the notch, so its concave edge stays soft.
float growthOdometerTimelineNormalCDF(float x) {
    float sign = x < 0 ? -1.0 : 1.0;
    x = abs(x) * 0.70710678;
    float t = 1.0 / (1.0 + 0.3275911 * x);
    float polynomial = (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t;
    return 0.5 * (1.0 + sign * (1.0 - polynomial * exp(-x * x)));
}
float growthOdometerTimelineRailGlow(float2 pixel, float railX, float cy, float radius, float width, float sigma) {
    if (abs(pixel.x - railX) > 48 || abs(pixel.y - cy) > radius + 24) return 0;
    float alpha = 0;
    for (int sample = -12; sample <= 12; sample++) {
        float dy = float(sample) * 2;
        float y = pixel.y + dy;
        float notch = abs(y - cy) < 44 ? 14 * (0.5 + 0.5 * cos(M_PI_F * (y - cy) / 44)) : 0;
        float dx = pixel.x - (railX - notch);
        float horizontal = growthOdometerTimelineNormalCDF((dx + width / 2) / sigma)
            - growthOdometerTimelineNormalCDF((dx - width / 2) / sigma);
        float vertical = exp(-dy * dy / (2 * sigma * sigma)) * 2 / (sqrt(2 * M_PI_F) * sigma);
        alpha += horizontal * vertical * max(0.0, 1.0 - abs(y - cy) / radius);
    }
    return alpha;
}
fragment float4 growthOdometerTimelineFragment(
    GrowthOdometerTimelineVertex in [[stage_in]],
    constant GrowthOdometerTimelineUniforms &u [[buffer(0)]],
    texture2d<float> grain [[texture(0)]],
    texture2d<float> digits [[texture(1)]]) {
    float2 pixel = in.position.xy / u.screen.x;
    float4 stage = GrowthOdometerTimelineStage::render(pixel, u.geometry, u.light, u.hero, u.screen, grain);
    float glow = growthOdometerTimelineRailGlow(pixel, u.geometry.w, u.geometry.z, 100, 5, 6)
        * 0.6 * (1 + 0.35 * u.light.z + 0.4 * u.light.w);
    float heroAge = u.hero.x - 0.3;
    float heroAlpha = u.hero.w > 0.5 ? 0 : (1 - smoothstep(0.5, 1.3, heroAge)) * smoothstep(0.0, 0.08, heroAge);
    if (heroAlpha > 0) {
        float radius = max(1.0, clamp(heroAge / 0.5, 0.0, 1.0) * max(u.geometry.z, u.geometry.y - u.geometry.z));
        glow += growthOdometerTimelineRailGlow(pixel, u.geometry.w, u.geometry.z, radius, 4, 5) * heroAlpha;
    }
    stage.rgb = mix(stage.rgb, float3(168, 240, 60) / 255, min(glow, 1.0));
    float4 number = GrowthOdometerTimelineNumber::render(pixel, u.numberGeometry, u.angles, u.extra, u.motion, u.face, digits);
    return float4(min(stage.rgb + number.rgb, 1.0), 1);
}
