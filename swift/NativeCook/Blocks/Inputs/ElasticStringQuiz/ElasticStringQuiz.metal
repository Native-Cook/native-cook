#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;
struct ElasticStringQuizField {

float uP3;
float lin(float c) { return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4); }
float enc(float c) { return c <= 0.0031308 ? c * 12.92 : 1.055 * pow(c, 1.0 / 2.4) - 0.055; }
half3 toOut(float3 s) {
  if (uP3 < 0.5) return half3(s);
  float3 l = float3(lin(s.r), lin(s.g), lin(s.b));
  float3 p = float3(0.8225 * l.r + 0.1774 * l.g, 0.0332 * l.r + 0.9669 * l.g, 0.0171 * l.r + 0.0724 * l.g + 0.9108 * l.b);
  return half3(enc(p.r), enc(p.g), enc(p.b));
}

float2 uC;
float uGain;
float uCool;
float uPx;
float2 uSpan; // top and bottom of the screen (pt)

float hash(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }

half4 render(float2 xy) {
  float3 centre = mix(float3(0.459, 0.173, 0.282), float3(0.353, 0.118, 0.227), uCool);
  float3 mid = float3(0.216, 0.090, 0.216);
  float3 edge = float3(0.169, 0.067, 0.173);
  float2 d = (xy - uC) * float2(1.0, 0.86);
  float r = length(d) / 260.0;
  float3 glow = mix(centre, mid, smoothstep(0.0, 1.0, r));
  glow = mix(glow, edge, smoothstep(0.85, 2.0, r));
  // the glow's strength: tension leans and brightens it
  float3 col = edge + (glow - edge) * uGain;
  // vertical falloff to the top and bottom edges
  float v = (xy.y - uSpan.x) / max(uSpan.y - uSpan.x, 1.0);
  float vf = smoothstep(0.0, 0.16, v) * smoothstep(1.0, 0.8, v);
  col = mix(edge, col, mix(0.55, 1.0, vf));
  float2 px = floor(xy * uPx);
  float n1 = hash(px);
  float n2 = hash(px + float2(17.0, 31.0));
  col += (n1 + n2 - 1.0) / 255.0;
  col *= 1.0 + (hash(px * 0.5 + float2(3.0, 7.0)) - 0.5) * 0.03;
  return half4(toOut(clamp(col, 0.0, 1.0)), 1.0);
}

};
[[ stitchable ]] half4 elasticStringQuizField(float2 xy,constant float *p,int count) { ElasticStringQuizField f; f.uP3=p[0]; f.uC=float2(p[1],p[2]); f.uGain=p[3]; f.uCool=p[4]; f.uPx=p[5]; f.uSpan=float2(p[6],p[7]); return f.render(xy); }
