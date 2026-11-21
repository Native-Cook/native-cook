// Tumble Card Routine: SkSL. Only the grain lives here: the blurs (motion streak, depth of field) are Skia image
// filters, built per frame in the component.

import { Skia } from '@shopify/react-native-skia';

/**
 * Static film grain: one hashed value per device pixel (3 px per pt), mid-grey on average, so it is blended with
 * soft light at a few percent and never shifts the colour (no P3 conversion needed for a neutral grey).
 */
const GRAIN = `
half4 main(float2 xy) {
  float2 p = floor(xy * 3.0);
  float n = fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
  float m = fract(sin(dot(p + 17.0, float2(39.3468, 11.135))) * 24634.6345);
  float g = 0.5 + 0.5 * (n + m - 1.0);
  return half4(half3(g), 1.0);
}
`;

export const GRAIN_FX = Skia.RuntimeEffect.Make(GRAIN)!;
