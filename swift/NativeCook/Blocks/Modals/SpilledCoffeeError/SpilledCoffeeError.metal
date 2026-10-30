#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Premultiplied cool grain, evaluated in screen points as in the RN runtime effect.
[[ stitchable ]] half4 SpilledCoffeeErrorGrain(float2 position) {
    float2 p = floor(position * 0.9);
    float g = fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
    return half4(0.18, 0.2, 0.3, 1.0) * half(g * 0.05);
}
