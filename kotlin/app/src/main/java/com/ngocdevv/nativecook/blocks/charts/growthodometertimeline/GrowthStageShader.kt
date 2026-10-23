package com.ngocdevv.nativecook.blocks.charts.growthodometertimeline

// Original SkSL equations and uniform names; AGSL accepts the same source.
internal const val GROWTH_STAGE_SHADER = """
const float PAGE_FALLOFF = 1.7;
const float NOTCH_HALF_HEIGHT_PT = 44.;
const float NOTCH_DEPTH_PT = 14.;
const float PI = 3.14159265;
const float FOG_OFFSET_X_PT = 12.;
const float FOG_RADIUS_X_PT = 75.;
const float FOG_RADIUS_Y_PT = 110.;
const float FOG_STRENGTH = .32;
const float RAIL_FEATHER_PT = .7;
const float FOG_BASE_GAIN = .75;
const float FOG_HALO_GAIN = .25;
const float FOG_GRIP_GAIN = .35;
const float FOG_TICK_GAIN = .4;
const float WAVE_ORIGIN_OFFSET_PT = 40.;
const float WAVE_INNER_EDGE_PT = 6.;
const float WAVE_OUTER_EDGE_PT = 2.;
const float DOT_CELL_CENTER_PT = 3.;
const float FRINGE_START_LEVEL = .10;
const float FRINGE_FULL_LEVEL = .28;
const float FRINGE_FADE_LEVEL = .55;
const float FRINGE_END_LEVEL = .85;
const float TWINKLE_REDUCED_GAIN = .8;
const float TWINKLE_BASE_GAIN = .775;
const float TWINKLE_DEPTH = .225;
const float TWINKLE_PHASE_RADIANS = 6.283;
const float TWINKLE_RADIANS_PER_SECOND = .8;
const float DOT_OPACITY = .45;
const float DOT_LOWER_GAIN = .28;
const float DOT_FADE_OFFSET_PT = 100.;
const float DOT_LIGHT_BOOST = .22;
const float WAVE_DOT_GAIN = .9;
const float WAVE_DOT_GLOW_VARIANCE_PT2 = 3.2;
const float WAVE_DOT_GLOW_GAIN = .10;
const float WAVE_RING_GAIN = .08;
const float DITHER_BASE_GAIN = 3.;
const float DITHER_LIGHT_GAIN = .08;
const float DITHER_START_LEVEL = .08;
const float DITHER_FULL_LEVEL = .35;
const float RAMP_FOREST_LEVEL = .10;
const float RAMP_OLIVE_LEVEL = .28;
const float RAMP_MOSS_LEVEL = .48;
const float RAMP_LIME_LEVEL = .68;
const float RAMP_YELLOW_LEVEL = .86;
const float RAMP_SHORT_WIDTH = .18;
const float RAMP_MID_WIDTH = .20;
const float RAMP_PALE_WIDTH = .14;
const float2 HASH_SEED = float2(127.1, 311.7);
const float HASH_GAIN = 43758.5453;
const float2 CROWN_CENTER_PT = float2(430., -250.);
const float2 CROWN_RADII_PT = float2(135., 210.);
const float2 CORE_RADII_PT = float2(95., 170.);
const float2 BRIDGE_CENTER_PT = float2(455., -110.);
const float2 BRIDGE_RADII_PT = float2(60., 240.);
const float2 TAIL_CENTER_PT = float2(440., 250.);
const float2 TAIL_RADII_PT = float2(120., 200.);
const float2 HAZE_CENTER_PT = float2(430., -40.);
const float2 HAZE_RADII_PT = float2(240., 420.);
const float3 FOREST_COLOR = float3(10, 31, 13) / 255.;
const float3 OLIVE_COLOR = float3(30, 74, 26) / 255.;
const float3 MOSS_COLOR = float3(70, 127, 39) / 255.;
const float3 LIME_COLOR = float3(147, 194, 50) / 255.;
const float3 YELLOW_COLOR = float3(221, 238, 85) / 255.;
const float3 PALE_COLOR = float3(243, 249, 166) / 255.;
const float3 WHITE_COLOR = float3(255, 255, 230) / 255.;
const float3 WAVE_COLOR = float3(200, 255, 90) / 255.;
const float3 WHEEL_FOG_COLOR = float3(63, 127, 31) / 255.;
const float3 LUMINANCE_WEIGHTS = float3(.2126, .7152, .0722);
const float CORE_TRAVEL_Y_PT = 150.;
const float HAZE_FADE_START_PT = 230.;
const float HAZE_FADE_END_PT = 330.;
const float BREATHING_DEPTH = .03;
const float BREATHING_RADIANS_PER_SECOND = 1.3823;
const float HERO_RADIUS_GAIN = .10;
const float WARP_X_FREQUENCY_PER_PT = .008;
const float WARP_X_SPEED_PER_SECOND = .05;
const float WARP_Y_FREQUENCY_PER_PT = .006;
const float WARP_Y_SPEED_PER_SECOND = .045;
const float WARP_Y_SEED = 7.;
const float WARP_AMPLITUDE_PT = 28.;
const float CROWN_BASE_FIELD = .10;
const float CROWN_FIELD_GAIN = 1.50;
const float CROWN_FULL_LEVEL = .55;
const float CROWN_RADIUS_BASE = .45;
const float CROWN_RADIUS_GAIN = .55;
const float LOWER_START_LEVEL = .50;
const float CORE_FIELD_GAIN = 1.10;
const float CORE_CENTER_X_PT = 452.;
const float CORE_START_Y_PT = -120.;
const float CORE_RADIUS_BASE = .3;
const float CORE_RADIUS_GAIN = .7;
const float BRIDGE_FIELD_GAIN = .45;
const float BRIDGE_START_LEVEL = .25;
const float TAIL_FIELD_GAIN = .30;
const float HAZE_FIELD_GAIN = .12;
const float HAZE_START_LEVEL = .1;
const float HAZE_LOWER_DIM = .5;
const float BAY_OFFSET_X_PT = 60.;
const float BAY_RADIUS_X_PT = 90.;
const float BAY_RADIUS_Y_PT = 100.;
const float INNER_FEATHER_PT = 65.;
const float HERO_FIELD_GAIN = .38;
const float OVERSCROLL_FIELD_GAIN = .5;
const float TONE_MAP_CEILING = 1.10;
const float TONE_MAP_EXPOSURE = 1.35;
const float REFERENCE_WIDTH_PT = 402.;
const float REFERENCE_HEIGHT_PT = 874.;
const float HERO_WAVE_RADIUS_PT = 470.;
const float HERO_WAVE_DURATION_SECONDS = .9;
const float LOWER_FALLOFF_START_PT = 150.;
const float LOWER_FALLOFF_END_PT = 400.;
const float BAY_STRENGTH = .78;
const float INNER_EDGE_PT = 220.;
const float INNER_BAY_WIDTH_PT = 84.;
const float INNER_BAY_VARIANCE_PT2 = 24000.;
const float WAVE_FADE_START_PT = 300.;
const float WAVE_FADE_END_PT = 465.;
const float WAVE_WAKE_VARIANCE_PT2 = 1800.;
const float DOT_GRID_PT = 6.;
const float DOT_CORE_VARIANCE_PT2 = .64;

uniform float4 geometry;
// width, height, centre y, rail x
uniform float4 light;
// level, time, grip, tick
uniform float4 hero;
// age, surge, overscroll, reduced
uniform float4 screen;
// pixel ratio, x0, reserved, reserved
uniform shader grain;
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
half4 main(float2 pixel) {
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
  float grainNoise = grain.eval(pixel * screen.x).r - .5;
  color += grainNoise * (DITHER_BASE_GAIN / 255. + DITHER_LIGHT_GAIN * dot(color, LUMINANCE_WEIGHTS)
    * smoothstep(DITHER_START_LEVEL, DITHER_FULL_LEVEL, field));
  return half4(clamp(color, 0., 1.), 1.);
}
"""
