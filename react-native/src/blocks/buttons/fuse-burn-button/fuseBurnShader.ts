// The night backdrop, baked once into an image: a navy gradient with a cool lift at the top, a
// warm hint near the button, a vignette and fine grain. RuntimeEffect output is read as Display
// P3 on iOS, so the colour goes through toP3.

export const BACKDROP_SKSL = `
uniform float2 size;
uniform float2 warm;

float hash21(float2 p) {
  p = fract(p * float2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}

float3 toP3(float3 c) {
  float3 l = pow(clamp(c, 0.0, 1.0), float3(2.2));
  float3 m = float3(
    dot(l, float3(0.8225, 0.1774, 0.0)),
    dot(l, float3(0.0332, 0.9669, 0.0)),
    dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(m, float3(1.0 / 2.2));
}

half4 main(float2 p) {
  float2 uv = p / size;
  float3 a = float3(0.075, 0.098, 0.2);
  float3 b = float3(0.051, 0.071, 0.149);
  float3 c = float3(0.035, 0.047, 0.106);
  float3 col = uv.y < 0.45 ? mix(a, b, uv.y / 0.45) : mix(b, c, (uv.y - 0.45) / 0.55);
  // A faint warm pool round the button, as if lit by what is about to happen.
  float d = length((p - warm) / float2(size.x * 0.7, 120.0));
  col += float3(0.09, 0.04, 0.02) * exp(-d * d * 2.0);
  float2 v = uv - float2(0.5, 0.45);
  col *= 1.0 - dot(v, v) * 0.9;
  col += (hash21(floor(p * 2.0)) - 0.5) * 0.018;
  return half4(half3(toP3(col)), 1.0);
}
`;
