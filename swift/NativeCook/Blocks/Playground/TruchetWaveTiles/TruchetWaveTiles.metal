#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

static float truchetWaveTilesHash(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

// A fine print grain over everything: specks a little lighter or darker than what lies beneath
// (white specks added, dark specks as clear-black coverage), premultiplied.
[[ stitchable ]] half4 truchetWaveTilesGrain(float2 p, float strength) {
    float g = truchetWaveTilesHash(floor(p * 1.5));
    float a = abs(g - 0.5) * strength;
    float v = g > 0.5 ? 1.0 : 0.0;
    return half4(half(v * a), half(v * a), half(v * a), half(a));
}
