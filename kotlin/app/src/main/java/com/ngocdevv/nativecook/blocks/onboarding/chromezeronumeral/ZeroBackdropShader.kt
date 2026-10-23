package com.ngocdevv.nativecook.blocks.onboarding.chromezeronumeral

// Original SkSL equations, unchanged for Android RuntimeShader.
internal const val ZERO_BACKDROP_SHADER = """
uniform float2 resolution;
uniform float drift;
uniform float flash;
uniform float dim;
half4 main(float2 xy) {
  float2 uv = xy / resolution;
  float2 p = uv - float2(0.5, 0.43);
  float centre = exp(-dot(p * float2(1.5, 1.0), p * float2(1.5, 1.0)) * 3.0);
  float3 ink = mix(float3(0.00784, 0.01176, 0.05882), float3(0.02745, 0.04314, 0.2), centre);
  // Parallel 30-degree ribbons in point space: positive y falls down the screen.
  // A narrow blue-white core sits within a periwinkle shoulder and electric-blue skirt.
  float2 axis = float2(0.8660254, 0.5);
  float2 normal = float2(-0.5, 0.8660254);
  float2 topDelta = (xy - float2(resolution.x * 0.64 + drift * 4.0,
    resolution.x * 0.015 + drift * 3.0)) / resolution.x;
  // Compact controls sit closer to the edge: lower the bottom ribbon with them.
  float bottomInset = resolution.y < 700.0 ? 0.04 : 0.12;
  float2 bottomDelta = (xy - float2(-resolution.x * 0.10 + drift * 3.0,
    resolution.y - resolution.x * bottomInset + drift * 4.0)) / resolution.x;
  float topAlong = dot(topDelta, axis);
  float bottomAlong = dot(bottomDelta, axis);
  float topAcross = dot(topDelta, normal);
  float bottomAcross = dot(bottomDelta, normal);
  float topWidth = 0.075 + 0.055 * smoothstep(-0.3, 0.5, topAlong);
  float topEnvelope = exp(-topAlong * topAlong / 0.30);
  float bottomEnvelope = exp(-bottomAlong * bottomAlong / 0.38);
  float core = exp(-topAcross * topAcross / (topWidth * topWidth)) * topEnvelope * 0.55
    + exp(-bottomAcross * bottomAcross / 0.0081) * bottomEnvelope * 0.62;
  float shoulder = exp(-topAcross * topAcross / 0.0361) * topEnvelope * 0.28
    + exp(-bottomAcross * bottomAcross / 0.0256) * bottomEnvelope * 0.32;
  float skirt = exp(-topAcross * topAcross / 0.09) * topEnvelope * 0.20
    + exp(-bottomAcross * bottomAcross / 0.0625) * bottomEnvelope * 0.22;
  float2 hazePoint = (xy - resolution * float2(0.36, 0.25)) / (resolution.x * float2(0.50, 0.65));
  float haze = exp(-dot(hazePoint, hazePoint)) * 0.035;
  // Rain retains 55% of the light; the fuse lifts its structure without bleaching it.
  float power = (1.0 + 0.035 * drift + 0.40 * flash) * (1.0 - 0.45 * dim);
  ink += power * (float3(0.788, 0.824, 1.0) * core
    + float3(0.557, 0.616, 1.0) * shoulder
    + float3(0.239, 0.294, 1.0) * skirt
    + float3(0.557, 0.616, 1.0) * haze);
  float vignette = 1.0 - 0.26 * smoothstep(0.24, 0.79, length(uv - 0.5));
  float noise = fract(sin(dot(xy, float2(12.9898, 78.233))) * 43758.5453) - 0.5;
  return half4(clamp(ink * vignette + noise / 255.0, 0.0, 1.0), 1.0);
}"""
