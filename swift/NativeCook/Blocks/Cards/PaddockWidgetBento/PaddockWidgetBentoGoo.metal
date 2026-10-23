#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Gooey neighbours: the blurred capsules are thresholded on alpha (alpha' = gain * alpha - bias), colours kept.
[[ stitchable ]] half4 paddockWidgetBentoGoo(float2 position, half4 color, float gain, float bias) {
    half a = color.a;
    if (a <= 0.0001h) return half4(0.0h);
    half3 rgb = color.rgb / a;
    half na = clamp(half(gain) * a - half(bias), 0.0h, 1.0h);
    return half4(rgb * na, na);
}
