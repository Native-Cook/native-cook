#include <metal_stdlib>
using namespace metal;
struct EyewearLensTintUniforms {
  float4 view, lensA, lensB, tint, glare, photo;
};
struct EyewearLensTintPx { float3 col; float a; };
constexpr sampler eyewearLensTintSampler(coord::normalized, address::clamp_to_edge, filter::linear);
float eyewearLensTintSq(float v) { return v * v; }
float3 eyewearLensTintLin(float3 c) { return pow(c, float3(2.2)); }
float eyewearLensTintEase(float x) { float c = clamp(x, 0.0, 1.0); return c * c * (3.0 - 2.0 * c); }
float3 eyewearLensTintKeep(float i) {
  if (i < 0.5) return float3(0.05, 0.07, 0.11);
  if (i < 1.5) return float3(0.75, 0.32, 0.38);
  if (i < 2.5) return float3(0.72, 0.42, 0.1);
  return float3(0.12, 0.42, 0.4);
}
float3 eyewearLensTintGlow(float i) {
  if (i < 0.5) return float3(0.25, 0.55, 0.6);
  if (i < 1.5) return float3(1.0, 0.75, 0.8);
  if (i < 2.5) return float3(1.0, 0.85, 0.5);
  return float3(0.5, 0.95, 0.9);
}
float3 eyewearLensTintPhoto(float2 s, constant EyewearLensTintUniforms &F, texture2d<float> photo) {
  float2 uv = (s - F.photo.xy) / F.photo.zw;
  return eyewearLensTintLin(photo.sample(eyewearLensTintSampler, clamp(uv, float2(0.001), float2(0.999)), level(0.0)).rgb);
}
float2 eyewearLensTintPour(float2 q, float which, constant EyewearLensTintUniforms &F) {
  float k = F.tint.z;
  if (k <= 0.0) return float2(0.0);
  bool first = abs(which - F.tint.w) < 0.5;
  float outer = which > 0.5 ? 1.0 : -1.0;
  float2 start = first ? float2(0.45 * outer, -0.55) : float2(-0.85 * outer, -0.2);
  float t0 = first ? 0.0 : 0.3;
  float u = clamp((k - t0) / (1.0 - t0), 0.0, 1.0);
  float2 p = q - start;
  float dd = length(p * float2(1.0, 0.8)) - max(p.y, 0.0) * 0.45;
  float wob = sin(atan2(p.y, p.x) * 5.0 + F.view.w * 4.0) * 0.05 + sin(atan2(p.y, p.x) * 9.0 - F.view.w * 6.0) * 0.025;
  float front = u * u * (3.0 - 2.0 * u) * 2.4;
  float m = 1.0 - smoothstep(front - 0.08, front + 0.02, dd + wob);
  float edge = exp(-eyewearLensTintSq((dd + wob - front + 0.03) / 0.03)) * step(0.001, u) * (1.0 - step(0.999, u));
  return float2(m, edge);
}
EyewearLensTintPx eyewearLensTintLens(float2 s, float2 c, float r, float which, constant EyewearLensTintUniforms &F, texture2d<float> photo) {
  EyewearLensTintPx o = {float3(0.0), 0.0};
  float2 q = (s - c) / r;
  float rr = length(q);
  if (rr > 1.0) return o;
  float3 behind = eyewearLensTintPhoto(c + (s - c) * (0.93 - 0.04 * rr * rr), F, photo);
  float2 pm = eyewearLensTintPour(q, which, F);
  float3 keep = mix(eyewearLensTintKeep(F.tint.x), eyewearLensTintKeep(F.tint.y), pm.x);
  float3 glw = mix(eyewearLensTintGlow(F.tint.x), eyewearLensTintGlow(F.tint.y), pm.x);
  float grad = mix(0.55, 1.15, eyewearLensTintEase(q.y * 0.6 + 0.55));
  float3 col = behind * keep * grad;
  col += eyewearLensTintLin(glw) * 0.16 * eyewearLensTintEase((q.y - 0.05) / 0.7) * (1.0 - smoothstep(0.75, 1.0, rr));
  float hl = length(q - float2(-0.05, 0.05)) - 0.78;
  float win = exp(-eyewearLensTintSq(hl / 0.07)) * smoothstep(-0.25, -0.65, q.y) * smoothstep(-0.95, -0.45, q.x) * smoothstep(0.3, -0.15, q.x);
  col += float3(1.0) * win * 0.36;
  float g = F.glare.x;
  float gd = dot(q, normalize(float2(1.0, -0.7))) - g * 0.9;
  col += float3(1.0, 0.98, 0.95) * 0.35 * exp(-eyewearLensTintSq(gd / 0.12)) * (0.12 + 0.88 * abs(g));
  col += eyewearLensTintLin(glw) * 0.5 * pm.y;
  col += float3(0.9, 0.95, 1.0) * 0.08 * smoothstep(0.85, 1.0, rr);
  o.col = col; o.a = 1.0 - smoothstep(0.97, 1.0, rr);
  return o;
}
vertex float4 eyewearLensTintVertex(uint vi [[vertex_id]]) {
  float2 uv = float2(float((vi << 1u) & 2u), float(vi & 2u));
  return float4(uv * float2(2.0, -2.0) + float2(-1.0, 1.0), 0.0, 1.0);
}
float3 eyewearLensTintEncode(float3 c) {
  return pow(clamp(c / (float3(1.0) + max(c - float3(0.85), float3(0.0)) * 0.6), float3(0.0), float3(1.0)), float3(1.0 / 2.2));
}
fragment float4 eyewearLensTintFragment(float4 pos [[position]], constant EyewearLensTintUniforms &F [[buffer(0)]], texture2d<float> photo [[texture(0)]]) {
  float2 s = pos.xy / F.view.z;
  EyewearLensTintPx px = eyewearLensTintLens(s, F.lensA.xy, F.lensA.z, 0.0, F, photo);
  if (px.a <= 0.0) px = eyewearLensTintLens(s, F.lensB.xy, F.lensB.z, 1.0, F, photo);
  if (px.a <= 0.0) return float4(0.0);
  return float4(eyewearLensTintEncode(px.col) * px.a, px.a);
}
