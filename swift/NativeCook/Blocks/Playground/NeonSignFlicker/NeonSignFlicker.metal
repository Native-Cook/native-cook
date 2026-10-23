#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The wall, the same shader as the React Native block's: brick albedo lit by a faint cold street
// light from above and by the sign's own glow. The glow comes from a baked image holding each
// word's light in one channel (red = "open", green = "late", blue = the glass) in the sign's
// frame, so the light swings with the sign. `wallSize` is the area the albedo image covers (pt),
// `spillRect` the area (sign points) the light image covers; outside it there is no light.

[[ stitchable ]] half4 neonSignFlickerWall(
    float2 p,
    texture2d<half> wall,
    texture2d<half> spill,
    float2 wallSize,
    float4 spillRect,
    float2 pivotW,
    float2 pivotL,
    float2 rot,
    float3 level,
    float3 c0,
    float3 c1,
    float3 c2,
    float2 size
) {
    constexpr sampler clamped(coord::normalized, address::clamp_to_edge, filter::linear);
    constexpr sampler decal(coord::normalized, address::clamp_to_zero, filter::linear);
    half3 albedo = wall.sample(clamped, p / wallSize).rgb;
    float2 d = p - pivotW;
    float2 q = pivotL + float2(rot.x * d.x + rot.y * d.y, -rot.y * d.x + rot.x * d.y);
    half3 s = spill.sample(decal, (q - spillRect.xy) / spillRect.zw).rgb;
    float3 light = float(s.r) * level.x * c0 + float(s.g) * level.y * c1 + float(s.b) * level.z * c2;
    float v = p.y / size.y;
    float2 c = (p - 0.5 * size) / size;
    float ambient = (0.07 + 0.11 * (1.0 - v)) * (1.0 - 0.9 * dot(c, c));
    float3 col = float3(albedo) * (ambient * float3(0.78, 0.86, 1.0) + light * 2.4) + light * 0.035;
    col = col / (1.0 + 0.25 * col);
    return half4(half3(col), 1.0);
}
