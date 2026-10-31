#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

static float3 petalLoaderSpinDecode(float3 c) {
    return select(c / 12.92, pow((c + 0.055) / 1.055, float3(2.4)), c > 0.04045);
}

static float3 petalLoaderSpinEncode(float3 c) {
    return select(c * 12.92, 1.055 * pow(max(c, 0.0), float3(1.0 / 2.4)) - 0.055, c > 0.0031308);
}

// The reference's glass fills blend in linear light. Explicit compositing preserves that
// brightness without changing its sRGB gradient stops, encoded halo, rim, or white glint.
[[ stitchable ]] half4 petalLoaderSpinGlass(
    float2 position, half4 input, float2 centre, float time, float spin, float flip,
    float pixel, texture2d<half> glint
) {
    const float pi = M_PI_F;
    float3 color = float3(4.0, 3.0, 10.0) / 255.0;
    float radius = length(position - centre);
    if (radius > 236.0) return half4(half3(color), 1.0h);
    color = mix(color, float3(110.0, 70.0, 255.0) / 255.0, 0.28 * (1.0 - radius / 236.0));
    float drift = time / 6.0 * 2.0 * pi + spin;
    for (int i = 0; i < 12; i++) {
        float angle = drift + float(i) * 2.0 * pi / 12.0 - pi / 2.0;
        float2 radial = float2(cos(angle), sin(angle));
        float2 tangent = float2(-radial.y, radial.x);
        float2 delta = position - centre - radial * 118.0;
        float face = cos(2.0 * pi * (time / 2.0 - float(i) / 12.0) + flip * pi);
        float squeeze = max(0.08, abs(face));
        float lit = max(0.0, face);
        float2 local = float2(dot(delta, radial), dot(delta, tangent) / squeeze);
        float d = length(local);
        if (d > 70.0) continue;
        float2 normal = local / max(d, 0.001);
        float aa = max(0.01, pixel * 0.5 * length(float2(normal.x, normal.y / squeeze)));

        float coverage = 1.0 - smoothstep(34.0 - aa, 34.0 + aa, d);
        if (coverage > 0.0) {
            float u = clamp((local.x + local.y + 68.0) / 136.0, 0.0, 1.0);
            float4 first = float4(round(70.0 + 90.0 * lit) / 255.0, round(90.0 + 60.0 * lit) / 255.0, 1.0, 0.35 + 0.4 * lit);
            float4 middle = float4(120.0 / 255.0, 40.0 / 255.0, 220.0 / 255.0, 0.32);
            float4 last = float4(20.0 / 255.0, 10.0 / 255.0, 80.0 / 255.0, 0.5);
            float4 body = u < 0.55 ? mix(first, middle, u / 0.55) : mix(middle, last, (u - 0.55) / 0.45);
            color = petalLoaderSpinEncode(mix(petalLoaderSpinDecode(color), petalLoaderSpinDecode(body.rgb), body.a * coverage));
        }

        float rim = 1.0 - smoothstep(-aa, aa, abs(d - 33.0) - 1.0 / squeeze);
        color = mix(color, float3(185.0, 168.0, 255.0) / 255.0, rim * (0.55 + 0.45 * lit));
        if (lit > 0.2) {
            float2 uv = (local + float2(46.2, 47.9)) / 72.0;
            if (all(uv >= 0.0) && all(uv <= 1.0)) {
                constexpr sampler linear(coord::normalized, address::clamp_to_edge, filter::linear);
                float alpha = float(glint.sample(linear, uv).a) * 0.5 * (lit - 0.2);
                color = mix(color, float3(1.0), alpha);
            }
        }
    }
    return half4(half3(color), 1.0h);
}
