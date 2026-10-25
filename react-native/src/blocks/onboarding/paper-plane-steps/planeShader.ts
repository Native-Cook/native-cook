/*
 Paper Plane Steps — the screen behind the paper: a dusk-blue gradient in three stops with a warm
 glow low on the horizon where the plane flies off, a vignette and grain. sRGB → Display P3 on iOS.
*/

export const BACKDROP_SKSL = `
uniform float2 size;
uniform float p3;

float h2(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }

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
  float3 a = float3(0.082, 0.106, 0.188);
  float3 b = float3(0.118, 0.137, 0.224);
  float3 c0 = float3(0.165, 0.149, 0.212);
  float3 c = mix(a, b, smoothstep(0.0, 0.5, uv.y));
  c = mix(c, c0, smoothstep(0.5, 1.0, uv.y));
  // Warm light at the upper right, where the planes leave.
  float2 g = (uv - float2(0.95, 0.08)) * float2(1.0, size.y / size.x);
  c += float3(0.16, 0.08, 0.05) * exp(-dot(g, g) * 3.0);
  float2 v = uv - 0.5;
  c *= 1.0 - 0.38 * smoothstep(0.3, 0.9, length(v * float2(1.1, 0.9)));
  c += (h2(floor(p)) - 0.5) * 0.04;
  return half4(half3(p3 > 0.5 ? toP3(c) : c), 1.0);
}
`;
