import { Skia } from '@shopify/react-native-skia';

// Foil on the cover: a silver light band that passes over the cover's own ink. The mask is the cover page's
// snapshot (1 − luminance), so the light sits exactly on the glyphs and the sparkle, never beside them.
const SOURCE = `
uniform shader cover;
uniform float2 dir;
uniform float center;
uniform float width;
uniform float amount;

half4 main(float2 p) {
  half4 c = cover.eval(p);
  float lum = (float(c.r) + float(c.g) + float(c.b)) / 3.0;
  float mask = clamp((1.0 - lum) * 1.4, 0.0, 1.0);
  float k = (dot(p, dir) - center) / width;
  float band = exp(-k * k);
  // A thin warm-to-cool split across the band reads as foil rather than a white smear.
  float3 tint = mix(float3(1.0, 0.93, 0.82), float3(0.84, 0.92, 1.0), clamp(k * 0.5 + 0.5, 0.0, 1.0));
  float a = amount * mask * band;
  return half4(half3(tint * a), half(a));
}
`;

export const foilEffect = Skia.RuntimeEffect.Make(SOURCE)!;
