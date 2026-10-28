import { Skia } from '@shopify/react-native-skia';

/*
 The lava behind the Oracle Glass Grid (a port of OracleGlassGrid.metal): soft green blobs drifting through
 purple with maroon rims, and teal pools at the edges. Each blob circles its own centre k = ±1 or 2 times per
 loop (phase = 2π · t / LOOP), so the field loops exactly. The output is converted from sRGB to Display P3,
 because Skia treats runtime-effect output as P3 on iOS.
*/
const LAVA_SKSL = `
uniform float2 size;
uniform float phase;

float hash(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
float sm(float a, float b, float x) { float t = clamp((x - a) / (b - a), 0.0, 1.0); return t * t * (3.0 - 2.0 * t); }
float blob(float2 p, float bx, float by, float r, float ry, float k, float ph, float s, float a) {
  float2 c = float2(bx + r * cos(k * a + ph), by + r * ry * sin(k * a + ph));
  float2 d = p - c;
  return exp(-dot(d, d) / (s * s));
}
float3 toP3(float3 c) {
  float3 l = pow(clamp(c, 0.0, 1.0), float3(2.2));
  float3 m = float3(
    dot(l, float3(0.8225, 0.1774, 0.0)),
    dot(l, float3(0.0332, 0.9669, 0.0)),
    dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(m, float3(1.0 / 2.2));
}

half4 main(float2 position) {
  float a = phase;
  float2 u = (position - 0.5 * size) / size.y * 2.0;
  float2 p = u + 0.10 * float2(sin(u.y * 3.1 + a + 0.4), cos(u.x * 2.7 - a + 1.1));
  float f = blob(p, -0.30, -0.62, 0.22, 0.8, 1.0, 0.0, 0.30, a)
          + blob(p, 0.28, -0.10, 0.20, 0.8, -1.0, 1.7, 0.34, a)
          + blob(p, -0.20, 0.42, 0.24, 0.8, 1.0, 3.1, 0.32, a)
          + blob(p, 0.30, 0.78, 0.20, 0.8, 2.0, 4.4, 0.26, a)
          + blob(p, 0.05, 0.05, 0.30, 0.8, -1.0, 5.5, 0.22, a);
  float g = blob(p, -0.55, -0.95, 0.18, 1.0, 1.0, 2.2, 0.36, a)
          + blob(p, 0.62, 0.35, 0.22, 1.0, -1.0, 0.6, 0.32, a)
          + blob(p, -0.58, 0.95, 0.15, 1.0, 1.0, 4.0, 0.30, a);
  float3 purple = float3(0.643, 0.400, 0.769);
  float3 plum = float3(0.550, 0.340, 0.660);
  float3 maroon = float3(0.430, 0.230, 0.270);
  float3 green = float3(0.114, 0.227, 0.169);
  float3 teal = float3(0.270, 0.640, 0.620);
  float3 col = mix(purple, plum, sm(0.05, 0.45, f));
  col = mix(col, maroon, sm(0.22, 0.70, f));
  col = mix(col, green, sm(0.45, 1.15, f));
  col = mix(col, teal, sm(0.15, 0.85, g) * (1.0 - sm(0.25, 0.75, f)) * 0.9);
  float2 v = (position / size - 0.5) * float2(1.1, 0.9);
  col *= 1.0 - 0.35 * dot(v, v);
  col += (hash(floor(position)) - 0.5) * 0.03;
  return half4(half3(toP3(col)), 1.0);
}
`;

export const LAVA_EFFECT = Skia.RuntimeEffect.Make(LAVA_SKSL);
