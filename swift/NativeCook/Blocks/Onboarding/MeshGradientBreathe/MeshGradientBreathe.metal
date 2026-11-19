#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The light behind Breathe Mesh: a 5 x 5 Catmull-Rom mesh gradient in OKLab whose control points move,
// and the glass orb that guides the breath, in one pass. A port of the React Native `breatheShader.ts`.
//
// u: size(2) orb(3: x, y, radius in points) lift dpr | off[25] (x, y) | lab[25] (L, a, b)
// The SwiftUI layer is non-linear sRGB; see encode() for the P3 step.

namespace meshGradientBreathe {

constant int N = 5;
constant float G = 4.0;
constant int OFF = 7;
constant int LAB = OFF + 50;

float cr(float x) {
  x = abs(x);
  if (x < 1.0) return (1.5 * x - 2.5) * x * x + 1.0;
  if (x < 2.0) return ((-0.5 * x + 2.5) * x - 4.0) * x + 2.0;
  return 0.0;
}

float wt(float g, int i) {
  float w = cr(g - float(i));
  if (i == 0) w += cr(g + 1.0);
  if (i == N - 1) w += cr(g - float(N));
  return w;
}

float2 disp(float2 uv, device const float *u) {
  float2 g = clamp(uv, 0.0, 1.0) * G;
  float wx[5];
  for (int i = 0; i < N; i++) { wx[i] = wt(g.x, i); }
  float2 acc = float2(0.0);
  for (int j = 0; j < N; j++) {
    float wy = wt(g.y, j);
    for (int i = 0; i < N; i++) {
      int k = OFF + (j * N + i) * 2;
      acc += (wx[i] * wy) * float2(u[k], u[k + 1]);
    }
  }
  return acc;
}

float3 meshLab(float2 uv, device const float *u) {
  float2 g = clamp(uv, 0.0, 1.0) * G;
  float wx[5];
  for (int i = 0; i < N; i++) { wx[i] = wt(g.x, i); }
  float3 acc = float3(0.0);
  for (int j = 0; j < N; j++) {
    float wy = wt(g.y, j);
    for (int i = 0; i < N; i++) {
      int k = LAB + (j * N + i) * 3;
      acc += (wx[i] * wy) * float3(u[k], u[k + 1], u[k + 2]);
    }
  }
  return acc;
}

float3 meshAt(float2 q, device const float *u) {
  float2 uv = q - disp(q, u);
  uv = q - disp(uv, u);
  return meshLab(uv, u);
}

float3 labToLinear(float3 c) {
  float l = c.x + 0.3963377774 * c.y + 0.2158037573 * c.z;
  float m = c.x - 0.1055613458 * c.y - 0.0638541728 * c.z;
  float s = c.x - 0.0894841775 * c.y - 1.2914855480 * c.z;
  l = l * l * l; m = m * m * m; s = s * s * s;
  return float3(
     4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
    -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
    -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s);
}

float3 encode(float3 lin) {
  lin = clamp(lin, 0.0, 1.0);
  // The React Native build writes Display P3 numbers that end up shown as sRGB; the reference video was
  // measured to match this exactly, so the same matrix is applied here.
  lin = float3(
    dot(lin, float3(0.8225, 0.1774, 0.0)),
    dot(lin, float3(0.0332, 0.9669, 0.0)),
    dot(lin, float3(0.0171, 0.0724, 0.9108)));
  float3 lo = lin * 12.92;
  float3 hi = 1.055 * pow(lin, float3(1.0 / 2.4)) - 0.055;
  return mix(lo, hi, step(float3(0.0031308), lin));
}

float h21(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float ign(float2 p) { return fract(52.9829189 * fract(dot(p, float2(0.06711056, 0.00583715)))); }

} // namespace meshGradientBreathe

[[ stitchable ]] half4 meshGradientBreatheLight(float2 p, device const float *u, int count) {
  using namespace meshGradientBreathe;
  float2 size = float2(u[0], u[1]);
  float3 orb = float3(u[2], u[3], u[4]);
  float lift = u[5];
  float dpr = u[6];

  float2 q = p / size;
  float3 lin = labToLinear(meshAt(q, u));

  float2 d = (p - orb.xy) / orb.z;
  float len = length(d);
  float2 ds = (p - orb.xy - float2(0.0, orb.z * 0.18)) / (orb.z * 1.25);
  float sh = 1.0 - smoothstep(0.55, 1.15, length(ds));
  lin *= 1.0 - 0.07 * sh;
  lin += float3(0.05 + 0.05 * lift) * exp(-max(len - 1.0, 0.0) * 5.0) * step(1.0, len);

  if (len < 1.02) {
    float z = sqrt(max(1.0 - len * len, 0.0));
    float2 sp = orb.xy + (p - orb.xy) * (0.42 + 0.38 * len * len) - float2(0.0, orb.z * 0.10);
    float3 seen = labToLinear(meshAt(sp / size, u));
    float fres = pow(1.0 - z, 3.0);
    float3 glass = seen * (1.04 + 0.05 * lift);
    glass = mix(glass, float3(1.0), fres * (0.30 + 0.20 * lift));
    glass *= 1.0 - 0.10 * smoothstep(0.2, 1.0, d.y) * smoothstep(0.4, 1.0, len);
    float2 hl = d - float2(-0.36, -0.46);
    glass += float3(0.42 * exp(-dot(hl, hl) * 20.0));
    float2 hl2 = d - float2(0.42, 0.52);
    glass += float3(0.10 * exp(-dot(hl2, hl2) * 30.0));
    float aa = 1.0 - smoothstep(1.0 - 1.5 / orb.z, 1.0, len);
    lin = mix(lin, glass, aa);
  }

  float3 rgb = encode(lin);
  float2 px = p * dpr;
  float n = ign(px) + ign(px + float2(41.0, 17.0)) - 1.0;
  float g = h21(floor(px)) - 0.5;
  rgb += n / 255.0 + g * 0.02;
  return half4(half3(rgb), 1.0);
}
