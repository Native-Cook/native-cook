#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

/*
 Elevator Floor Nav — a vertical motion blur for the store sliding by behind the doorway: a
 Gaussian along y only (the React Native version uses a blur image filter with sigma x ≈ 0),
 sigma in points, 17 taps spread over ±2.5 sigma.
*/
[[ stitchable ]] half4 elevatorFloorNavMotionBlur(float2 position, SwiftUI::Layer layer, float sigma) {
    if (sigma < 0.3) return layer.sample(position);
    half4 acc = half4(0.0);
    float total = 0.0;
    for (int i = -8; i <= 8; i++) {
        float dy = float(i) / 8.0 * 2.5 * sigma;
        float wgt = exp(-0.5 * (dy * dy) / (sigma * sigma));
        acc += layer.sample(position + float2(0.0, dy)) * half(wgt);
        total += wgt;
    }
    return acc / half(total);
}
