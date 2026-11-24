// The photo's adjustments: one Skia RuntimeEffect pass over the image.
//
//   warmth   -1..1   white balance: red up and blue down (or the reverse), luminance kept
//   exposure in stops: linear light times 2^(1.35 e - 0.75). The frame is treated as shot 0.75 of a
//                    stop under and the control is a third stronger than a true stop, so the change
//                    reads in a small silent video; then a soft shoulder so highlights roll off
//   vignette 0..1    darkens towards the corners
//
// Colour: Skia hands a runtime effect its child image already converted to the canvas's working
// space (Display P3 with the sRGB transfer curve on a wide-gamut iPhone, sRGB elsewhere), and reads
// the effect's output in that same space. The shader only decodes the transfer curve, works in linear
// light and encodes again; no gamut matrix is needed because no colour is authored here.

export const PHOTO_SKSL = `
uniform shader image;
uniform float2 size;
uniform float exposure;
uniform float warmth;
uniform float vignette;
uniform float dpr;

float3 toLinear(float3 c) {
  float3 lo = c / 12.92;
  float3 hi = pow((c + 0.055) / 1.055, float3(2.4));
  return mix(lo, hi, step(float3(0.04045), c));
}

float3 toEncoded(float3 lin) {
  float3 lo = lin * 12.92;
  float3 hi = 1.055 * pow(lin, float3(1.0 / 2.4)) - 0.055;
  return mix(lo, hi, step(float3(0.0031308), lin));
}

// Linear up to 0.7, then an exponential shoulder that reaches 1 only at infinity.
float3 shoulder(float3 x) {
  float3 over = max(x - 0.7, 0.0);
  return min(x, float3(0.7)) + 0.3 * (1.0 - exp(-over / 0.3));
}

float ign(float2 p) { return fract(52.9829189 * fract(dot(p, float2(0.06711056, 0.00583715)))); }

half4 main(float2 p) {
  float3 lin = toLinear(clamp(image.eval(p).rgb, 0.0, 1.0));

  float3 wb = float3(1.0 + 0.50 * warmth, 1.0 + 0.06 * warmth, 1.0 - 0.60 * warmth);
  wb /= dot(wb, float3(0.2126, 0.7152, 0.0722));
  lin *= wb;

  lin *= exp2(1.35 * exposure - 0.75);

  float2 q = p / size - 0.5;
  float d = length(q) * 1.41421;                       // 0 centre, 1 corners
  lin *= 1.0 - 0.95 * vignette * smoothstep(0.18, 0.92, d);

  float3 rgb = toEncoded(shoulder(max(lin, 0.0)));
  float2 px = p * dpr;
  rgb += (ign(px) + ign(px + float2(41.0, 17.0)) - 1.0) / 255.0;   // triangular dither
  return half4(half3(clamp(rgb, 0.0, 1.0)), 1.0);
}
`;

export const EXPOSURE_STOPS_PER_STEP = 0.05;
export const WARMTH_PER_STEP = 0.01;
export const VIGNETTE_PER_STEP = 0.01;
