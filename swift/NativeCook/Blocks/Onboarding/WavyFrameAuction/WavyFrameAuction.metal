#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;
float wavyFrameAuctionHash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float wavyFrameAuctionNoise(float2 p) {
  float2 i = floor(p); float2 f = fract(p); float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(wavyFrameAuctionHash(i), wavyFrameAuctionHash(i + float2(1, 0)), u.x), mix(wavyFrameAuctionHash(i + float2(0, 1)), wavyFrameAuctionHash(i + float2(1, 1)), u.x), u.y);
}
float wavyFrameAuctionFbm(float2 p) { float s = 0.0; float a = 0.5; for (int i = 0; i < 5; i++) { s += a * wavyFrameAuctionNoise(p); p = p * 2.02 + 3.1; a *= 0.5; } return s; }
[[ stitchable ]] half4 wavyFrameAuctionArt(float2 xy, float2 o, float2 sz, float ph, float flow) {
  float2 uv = (xy - o) / sz.y * 2.4;
  float2 drift = float2(cos(ph), sin(ph)) * (0.5 + 0.6 * flow);
  float2 q = float2(wavyFrameAuctionFbm(uv + drift), wavyFrameAuctionFbm(uv + float2(5.2, 1.3) - drift));
  float2 r = float2(wavyFrameAuctionFbm(uv + 3.0 * q + float2(1.7, 9.2) + drift * 0.6), wavyFrameAuctionFbm(uv + 3.0 * q + float2(8.3, 2.8)));
  float f = wavyFrameAuctionFbm(uv + 3.5 * r);
  float3 deep = float3(0.13, 0.145, 0.12);
  float3 violet = float3(0.47, 0.38, 0.97);
  float3 lime = float3(0.78, 0.96, 0.38);
  float3 pink = float3(1.0, 0.44, 0.71);
  float3 amber = float3(1.0, 0.7, 0.3);
  float3 col = mix(deep, violet, smoothstep(0.2, 0.6, f));
  col = mix(col, pink, smoothstep(0.45, 0.8, length(q)) * 0.75);
  col = mix(col, lime, smoothstep(0.62, 0.9, r.x) * 0.85);
  col = mix(col, amber, smoothstep(0.7, 0.95, f * r.y * 1.6) * 0.6);
  // flowing contour lines
  col += 0.08 * smoothstep(0.45, 0.5, fract(f * 9.0)) * (1.0 - smoothstep(0.5, 0.55, fract(f * 9.0)));
  col += (wavyFrameAuctionHash(floor(xy)) - 0.5) * 0.06;
  // Skia's runtime-effect layer is tagged Display P3 on iOS. Convert only at the
  // framebuffer boundary; the reference's noise and palette above remain untouched.
  float3 encoded = float3(half3(col));
  float3 lin = select(encoded / 12.92, pow((encoded + 0.055) / 1.055, float3(2.4)), encoded > 0.04045);
  float3 srgb = float3(
    1.22494018 * lin.r - 0.22494018 * lin.g,
    -0.04205695 * lin.r + 1.04205695 * lin.g,
    -0.01963755 * lin.r - 0.07863605 * lin.g + 1.09827360 * lin.b);
  float3 output = select(srgb * 12.92, 1.055 * pow(max(srgb, 0.0), float3(1.0 / 2.4)) - 0.055, srgb > 0.0031308);
  return half4(half3(output), 1.0);
}