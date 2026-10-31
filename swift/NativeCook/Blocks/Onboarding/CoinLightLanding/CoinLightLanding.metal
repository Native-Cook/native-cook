#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

constant float coinLightLandingH = 0.09;

float3 coinLightLandingRotY(float3 v, float a) { float c0 = cos(a); float s0 = sin(a); return float3(c0 * v.x + s0 * v.z, v.y, -s0 * v.x + c0 * v.z); }
float3 coinLightLandingRotX(float3 v, float a) { float c0 = cos(a); float s0 = sin(a); return float3(v.x, c0 * v.y - s0 * v.z, s0 * v.y + c0 * v.z); }
float coinLightLandingBox(float2 p, float2 b, float r) { float2 d = abs(p) - b + r; return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - r; }
float coinLightLandingMark(float2 p) {
  float d = coinLightLandingBox(p - float2(0.0, 0.3), float2(0.22, 0.09), 0.03);
  d = min(d, coinLightLandingBox(p - float2(0.0, 0.02), float2(0.36, 0.09), 0.03));
  d = min(d, coinLightLandingBox(p - float2(0.0, -0.26), float2(0.5, 0.09), 0.03));
  return d;
}
float coinLightLandingRelief(float2 p) {
  float r = length(p);
  float rim = smoothstep(0.8, 0.86, r) * (1.0 - smoothstep(0.95, 1.0, r));
  float m = 1.0 - smoothstep(-0.02, 0.03, coinLightLandingMark(float2(p.x, -p.y)));
  return 0.05 * max(rim, m);
}
float3 coinLightLandingEnv(float3 r) {
  float e = 0.08;
  e += 1.4 * smoothstep(0.2, 0.9, r.y);
  e += 0.9 * exp(-pow((r.x - 0.7) * 4.0, 2.0));
  e += 0.5 * exp(-pow((r.x + 0.8) * 4.0, 2.0));
  return float3(e);
}
[[ stitchable ]] half4 coinLightLandingCoin(float2 xy, float2 c, float R, float yaw, float pitch, float glow) {
  float2 q = (xy - c) / R;
  q.y = -q.y;
  float3 ro = coinLightLandingRotY(coinLightLandingRotX(float3(q, 3.0), -pitch), -yaw);
  float3 rd = coinLightLandingRotY(coinLightLandingRotX(float3(0.0, 0.0, -1.0), -pitch), -yaw);
  float best = 1e9;
  float3 n = float3(0.0);
  float part = 0.0;
  // the two faces
  for (int s = 0; s < 2; s++) {
    float z = s == 0 ? coinLightLandingH : -coinLightLandingH;
    if (abs(rd.z) > 1e-4) {
      float t = (z - ro.z) / rd.z;
      float3 p = ro + rd * t;
      if (t > 0.0 && t < best && length(p.xy) <= 1.0) {
        best = t;
        float e = 0.004;
        float hx = coinLightLandingRelief(p.xy + float2(e, 0.0)) - coinLightLandingRelief(p.xy - float2(e, 0.0));
        float hy = coinLightLandingRelief(p.xy + float2(0.0, e)) - coinLightLandingRelief(p.xy - float2(0.0, e));
        n = normalize(float3(-hx / (2.0 * e) * 0.6, -hy / (2.0 * e) * 0.6, s == 0 ? 1.0 : -1.0));
        part = coinLightLandingRelief(p.xy) > 0.02 ? 1.0 : 0.0;
      }
    }
  }
  // the rim (cylinder)
  float A = rd.x * rd.x + rd.y * rd.y;
  if (A > 1e-6) {
    float B = 2.0 * (ro.x * rd.x + ro.y * rd.y);
    float C = ro.x * ro.x + ro.y * ro.y - 1.0;
    float D = B * B - 4.0 * A * C;
    if (D >= 0.0) {
      float t = (-B - sqrt(D)) / (2.0 * A);
      float3 p = ro + rd * t;
      if (t > 0.0 && t < best && abs(p.z) <= coinLightLandingH) {
        best = t;
        float a = atan2(p.y, p.x);
        n = normalize(float3(p.xy, 0.0) + float3(-p.y, p.x, 0.0) * 0.25 * sin(a * 90.0));
        part = 2.0;
      }
    }
  }
  if (best > 1e8) return half4(0.0);
  float3 nw = coinLightLandingRotX(coinLightLandingRotY(n, yaw), pitch);
  float3 v = float3(0.0, 0.0, 1.0);
  float3 rf = reflect(-v, nw);
  float ndv = clamp(dot(nw, v), 0.0, 1.0);
  float3 gold = float3(1.0, 0.76, 0.36);
  float3 col = gold * coinLightLandingEnv(rf) * (part > 1.5 ? 0.7 : 1.0);
  col = mix(col, gold * 0.55, part > 0.5 && part < 1.5 ? 0.0 : 0.18);
  col += float3(1.0, 0.62, 0.22) * (0.25 + 0.35 * glow) * (0.6 + 0.4 * ndv);
  col += float3(1.0, 0.95, 0.85) * pow(1.0 - ndv, 4.0) * 0.4;
  col = float3(1.0) - exp(-col * 1.25);
  // antialias the silhouette with a 1 px fade on the outline (projected ellipse)
  float a = abs(cos(yaw)) + coinLightLandingH * abs(sin(yaw));
  float b = abs(cos(pitch)) + coinLightLandingH * abs(sin(pitch));
  float f = length(float2(q.x / a, q.y / b)) - 1.0;
  float alpha = clamp(0.5 - f * R * min(a, b), 0.0, 1.0);
  // This reference has no Skia gamut workaround: its RuntimeEffect output is tagged Display P3
  // on iOS. Canvas is sRGB, so map that output once to the same displayed sRGB color.
  float3 lin = select(col / 12.92, pow((col + 0.055) / 1.055, float3(2.4)), col > 0.04045);
  lin = float3(dot(lin,float3(1.2249401,-0.2249404,0.0)),
               dot(lin,float3(-0.0420569,1.0420571,0.0)),
               dot(lin,float3(-0.0196376,-0.0786361,1.0982735)));
  lin = max(lin,0.0);
  col = select(lin*12.92,1.055*pow(lin,float3(1.0/2.4))-0.055,lin>0.0031308);
  return half4(half3(col) * alpha, alpha);
}