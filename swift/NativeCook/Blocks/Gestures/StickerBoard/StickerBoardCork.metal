#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

static float stickerBoardHash(float2 p) {
    return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
}

static float stickerBoardNoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float a = stickerBoardHash(i);
    float b = stickerBoardHash(i + float2(1.0, 0.0));
    float c = stickerBoardHash(i + float2(0.0, 1.0));
    float d = stickerBoardHash(i + float2(1.0, 1.0));
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

// Cork: warm ground, fine granules, darker flecks and a soft vignette. `position` in points.
[[ stitchable ]] half4 stickerBoardCork(float2 position, float2 size) {
    float2 xy = position;
    float fine = stickerBoardNoise(xy * 0.9);
    float mid = stickerBoardNoise(xy * 0.22);
    float wide = stickerBoardNoise(xy * 0.035);
    float3 base = float3(0.73, 0.54, 0.34);
    float3 col = base * (0.8 + 0.22 * mid + 0.14 * wide) * (0.88 + 0.22 * fine);
    float fleck = step(0.9, stickerBoardHash(floor(xy * 0.45))) * smoothstep(0.3, 0.8, stickerBoardNoise(xy * 0.7));
    col *= 1.0 - 0.45 * fleck;
    float2 uv = xy / size - 0.5;
    col *= 1.0 - 0.9 * dot(uv, uv);
    return half4(half3(col), 1.0);
}
