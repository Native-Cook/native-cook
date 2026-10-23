export const ODOMETER_SKSL = `
const int BLUR_TAP_COUNT = 10;
const float BLUR_TAP_DIVISOR = 10.;
const float BLUR_TAP_WEIGHT = .1;
const float GLOW_KERNEL_WEIGHT_DIVISOR = 48.;
const float GLOW_SAMPLE_X_PT = 1.5;
const float GLOW_SAMPLE_Y_PT = 2.;
const int GLOW_EXPOSURE_COUNT = 3;
const float GLOW_EXPOSURE_DIVISOR = 3.;
const float GLOW_EXPOSURE_OFFSET = .5;
const float2 HASH_SEED = float2(127.1, 311.7);
const float HASH_GAIN = 43758.5453;
const float COMMA_END_PT = 34.;
const float SHEEN_END_SECONDS = 1.2;
const float3 COMMA_COLOR = float3(.96, .98, .98);
const float3 DIGIT_COLOR = float3(.985, 1., .995);
const float3 RIM_COLOR = float3(.776, 1., .290);
const float3 FLASH_COLOR = float3(.949, 1., .659);
const float3 GLOW_COLOR = float3(.70, 1., .25);
const float RIM_HALO_GAIN = .85;
const float RIM_STRENGTH = .30;
const float SHEEN_TRAVEL_PT = 174.;
const float SHEEN_OFFSET_PT = 26.;
const float CYLINDER_SHADE_GAIN = .65;
const float TEXTURE_SCALE = 3.;
const float COMMA_ROW_Y_PT = 520.;
const float WINDOW_FADE_START_PT = 21.;
const float HUNDREDS_END_PT = 58.;
const float TENS_END_PT = 82.;
const float UNITS_END_PT = 106.;
const float LEADING_CROP_X_PT = 4.;
const float BLUR_EPSILON_CELLS = .001;
const float RIM_BASE_GAIN = .15;
const float FLASH_STRENGTH = .5;
const float SHEEN_START_SECONDS = .5;
const float SHEEN_SLOPE = .32;
const float SHEEN_DURATION_SECONDS = .7;
const float SHEEN_WIDTH_PT = 12.;
const float SHEEN_STRENGTH = .5;
const float CYLINDER_SHADE_BASE = .35;
const float GLOW_THRESHOLD = .01;
const float GLOW_STRENGTH = .28;
const float CELL_HEIGHT_PT = 52.;
const float WINDOW_HALF_HEIGHT_PT = 26.;
const float CELL_WIDTH_PT = 24.;
const float NUMBER_WIDTH_PT = 122.;
const float CYLINDER_RADIUS_PT = 34.;
const float LEADING_WIDTH_PT = 16.;
const float RIM_SAMPLE_OFFSET_PT = 1.2;
const float ASIN_LIMIT = .999;

uniform float4 geometry;
// x0, cy, pixel ratio, halo
uniform float4 angles;
// units, tens, hundreds, thousands
uniform float4 extra;
// lead angle, blur units, tens, hundreds
uniform float4 motion;
// blur thousands, lead, flash, hero age
uniform float4 face;
// alpha, reduced, unused, unused
uniform shader digits;
float hashPoint(float2 point) {
  return fract(sin(dot(point, HASH_SEED)) * HASH_GAIN);
}
// Greyscale tiles encode coverage in red; color - managed RGB is not digit data.
float glyphCoverage(float sourceX, float sourceY, float drum) {
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
  return digits.eval(float2(sourceX * TEXTURE_SCALE, (row * CELL_HEIGHT_PT + tileY) * TEXTURE_SCALE)).r;
}
half4 main(float2 pixel) {
  // Slot lookup: the leading drum mechanically slides the lower group.
  float localX = pixel.x - geometry.x;
  float localY = pixel.y - geometry.y;
  float lead = clamp(extra.x, 0., 1.);
  if (abs(localY) > WINDOW_HALF_HEIGHT_PT || localX < 0. || localX > NUMBER_WIDTH_PT)  {
    return half4(0);
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
         ? digits.eval(float2((groupX - CELL_WIDTH_PT) * TEXTURE_SCALE,
        (COMMA_ROW_Y_PT + commaY) * TEXTURE_SCALE)).r: 0.;
      coverage *= 1. - smoothstep(WINDOW_FADE_START_PT, WINDOW_HALF_HEIGHT_PT, abs(localY));
      return half4(COMMA_COLOR * coverage, coverage) * face.x;
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
    else return half4(0);
  }
  else sourceX = localX + LEADING_CROP_X_PT;
  // Cylinder mapping: an inverse arc makes the window a curved drum face.
  float cylinderAngle = asin(clamp(localY / CYLINDER_RADIUS_PT, -ASIN_LIMIT, ASIN_LIMIT));
  float sourceY = WINDOW_HALF_HEIGHT_PT + CYLINDER_RADIUS_PT * cylinderAngle + angle * CELL_HEIGHT_PT;
  // Motion blur: jittered taps form a short directional exposure.
  float coverage = 0.;
  float rightCoverage = 0.;
  if (abs(blurCells) < BLUR_EPSILON_CELLS) {
    coverage = glyphCoverage(sourceX, sourceY, drum);
    rightCoverage = glyphCoverage(sourceX + RIM_SAMPLE_OFFSET_PT, sourceY, drum);
  }
  else {
    float jitter = hashPoint(floor(pixel * geometry.z));
    for (int tapIndex = 0;tapIndex < BLUR_TAP_COUNT;tapIndex++) {
      float blurOffset = -((float(tapIndex) + jitter) / BLUR_TAP_DIVISOR) * blurCells * CELL_HEIGHT_PT;
      coverage += glyphCoverage(sourceX, sourceY + blurOffset, drum) * BLUR_TAP_WEIGHT;
      rightCoverage += glyphCoverage(
        sourceX + RIM_SAMPLE_OFFSET_PT, sourceY + blurOffset, drum
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
            drum
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
  return half4(lightColor, outputCoverage) * face.x;
}
`;
