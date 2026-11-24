#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Spring Disclosure FAQ — a static paper grain over the page so the flat colour does not look
// vector-made. A port of GRAIN in SpringDisclosureFaq.tsx: per device pixel, white or black at up
// to 3.5 %, composited over the page colour the layer was filled with.

[[ stitchable ]] half4 springDisclosureFaqGrain(float2 position, half4 color, float dpr) {
    float2 p = floor(position * dpr);
    float n = fract(precise::sin(dot(p, float2(127.1, 311.7))) * 43758.5453) - 0.5;
    half a = half(abs(n) * 0.07);
    half3 rgb = n > 0.0 ? color.rgb + (half3(1.0) - color.rgb) * a : color.rgb * (1.0h - a);
    return half4(rgb, color.a);
}
