#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Separable Gaussian in the glyph's local coordinates, before its rotation and scale.
// The local vertical axis follows the wheel tangent after the numeral rotates.
[[ stitchable ]] half4 twinRingTimePickerBlur(float2 position, SwiftUI::Layer layer, float sigma, float2 axis) {
    if (sigma < 0.01) return layer.sample(position);
    half4 color=half4(0.0);
    float weight=0.0;
    for (int i=-24; i<=24; i++) {
        float x=float(i)/6.0;
        float w=exp(-0.5*x*x);
        color+=layer.sample(position+axis*(x*sigma))*half(w);
        weight+=w;
    }
    return color/half(weight);
}
