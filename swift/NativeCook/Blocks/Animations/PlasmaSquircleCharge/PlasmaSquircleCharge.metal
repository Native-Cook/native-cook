#include <metal_stdlib>
using namespace metal;

// The RN SkSL field, in point coordinates. SwiftUI already uses the destination color space;
// the extra sRGB-to-P3 conversion required by Skia on iOS is not needed here.
[[ stitchable ]] half4 plasmaSquircleChargeField(
    float2 xy, half4 input, float2 centre, float cover, float charge, float phase
) {
    float2 p = xy - centre;
    float a = atan2(p.y, p.x);
    float2 q = abs(p) - float2(156.0, 116.0) + 74.0;
    float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - 74.0;
    float T = phase * 6.28318530718;

    // Each time coefficient is an integer, so the crackle repeats after exactly six seconds.
    float amp = 1.2 + 3.4 * charge;
    float wobble = amp * (
        0.5 * sin(a * 7.0 + T * 3.0) +
        0.3 * sin(a * 13.0 - T * 5.0) +
        0.2 * sin(a * 29.0 + T * 11.0)
    );
    float e = d + wobble;

    // GLSL mod uses floor, while Metal fmod uses truncation for negative angles.
    float da = abs((a + 0.785398) - 1.570796 * floor((a + 0.785398) / 1.570796) - 0.785398);
    float reach = cover * 0.86;
    // Equivalent to RN's reversed-edge smoothstep, without undefined edge ordering.
    float lit = cover >= 0.999 ? 1.0 :
        (1.0 - smoothstep(reach - 0.05, reach + 0.05, da)) * step(0.001, cover);
    float crackle = 0.72 + 0.28 * sin(a * 41.0 + T * 23.0) * sin(a * 17.0 - T * 17.0);

    float core = exp(-abs(e) / 1.2);
    float halo = exp(-abs(e) / 10.0);
    float bloom = 1.0 / (1.0 + (d / 40.0) * (d / 40.0));
    float rest = exp(-abs(d) / 0.9) * 0.14;
    float2 s = (p - float2(0.0, 40.0)) / float2(230.0, 150.0);
    float stage = exp(-dot(s, s) * 1.6);
    float on = charge * lit * crackle;
    float3 col = float3(0.020, 0.016, 0.043)
        + float3(0.10, 0.07, 0.24) * stage * (0.55 + 0.45 * charge);
    col += float3(0.95, 0.93, 1.0) * rest;
    col += float3(0.60, 0.48, 1.0) * halo * on * 0.9;
    col += float3(0.95, 0.93, 1.0) * core * on * 1.3;
    col += float3(0.30, 0.28, 0.95) * bloom * charge * charge * 0.38;
    col = 1.0 - exp(-col * 1.4);
    return half4(half3(col), 1.0);
}
