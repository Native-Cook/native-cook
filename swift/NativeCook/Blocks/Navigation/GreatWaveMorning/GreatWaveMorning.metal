#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

float GreatWaveMorningHash(float2 p) {
    return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
}
// The original black paper-grain overlay, composed over the complete sRGB Canvas.
[[ stitchable ]] half4 GreatWaveMorningPaper(float2 p, half4 color) {
    float grain = GreatWaveMorningHash(floor(p * 0.75));
    float fiber = GreatWaveMorningHash(floor(p * float2(0.08, 1.4)));
    half alpha = half((grain * 0.55 + fiber * 0.45) * 0.07);
    return half4(color.rgb * (1 - alpha), color.a);
}
