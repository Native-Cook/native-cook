#include <metal_stdlib>
#include <SwiftUI/SwiftUI.h>
using namespace metal;

float3 fridgeDoorGroceryLinear(float3 x) {
    return select(x / 12.92, pow(max((x + 0.055) / 1.055, 0.0), float3(2.4)), x > 0.04045);
}
float3 fridgeDoorGroceryGamma(float3 x) {
    return select(x * 12.92, 1.055 * pow(max(x, 0.0), float3(1.0 / 2.4)) - 0.055, x > 0.0031308);
}

// The Skia iOS surface blends in nonlinear Display P3. The Canvas stores those
// coordinates as numerical sRGB, then converts the finished opaque scene once.
[[stitchable]] half4 fridgeDoorGroceryDecode(float2 position, half4 color) {
    float a = float(color.a);
    float3 p = fridgeDoorGroceryLinear(float3(color.rgb) / max(a, 0.00001));
    float3 s = float3(
        1.22474527 * p.r - 0.22490437 * p.g,
        -0.04205797 * p.r + 1.04208100 * p.g,
        -0.01964227 * p.r - 0.07865492 * p.g + 1.09853719 * p.b
    );
    return half4(half3(clamp(fridgeDoorGroceryGamma(s), 0.0, 1.0) * a), half(a));
}
