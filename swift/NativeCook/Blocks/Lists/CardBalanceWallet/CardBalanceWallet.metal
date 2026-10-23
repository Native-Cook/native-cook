#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

[[ stitchable ]] half4 cardBalanceWalletFluted(
    float2 xy,
    SwiftUI::Layer layer,
    float2 size,
    float shift,
    float spec,
    float light
) {
    float2 uv = xy / size;
    float px = uv.x * size.x - 85.2 + shift * 40.0;
    float flute = fract(px / 11.8);
    float reach = smoothstep(0.2, 0.25, uv.x) * (1.0 - smoothstep(0.78, 0.84, uv.x));
    float2 samplePoint = uv;
    samplePoint.x += (flute - 0.5) * 0.035 * reach;
    samplePoint = clamp(samplePoint, float2(0.0), float2(1.0));
    float3 colour = float3(layer.sample(samplePoint * size).rgb);
    colour += (flute - 0.5) * 0.06 * reach;
    float distance = uv.x - light + (uv.y - 0.5) * 0.36 * size.y / size.x;
    colour += spec * exp(-distance * distance / 0.006) * (0.75 + 0.5 * flute * reach);
    float grain = fract(sin(dot(xy, float2(12.9898, 78.233))) * 43758.5453);
    colour += (grain - 0.5) * 0.02;
    return half4(half3(colour), 1.0);
}
