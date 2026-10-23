#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The view, the same shader as the React Native block's, run as a layer effect: every point of
// the round window is folded into one wedge of the object cell, mirrored from one segment to the
// next. While the count morphs, two counts are drawn and blended, with a swirl that peaks halfway.
// The layer holds the object cell as drawn this frame, its centre at `cellCenter` (1 pt per cell
// unit); `center` is the window's centre in the layer.

// Folds a point of the window into the wedge that looks straight down into the chamber, where
// gravity keeps the pieces. GLSL's `mod` (floored) is written out: Metal's `fmod` truncates.
static float2 kaleidoscopeTwistFold(float2 d, float segs, float swirl) {
    float r = length(d);
    float w = 6.28318530718 / segs;
    float a = atan2(d.y, d.x) + swirl * r;
    float p = 2.0 * w;
    float m = a - p * floor(a / p);
    if (m > w) { m = p - m; }
    m += 1.57079632679 - 0.5 * w;
    return float2(cos(m), sin(m)) * r;
}

static float2 kaleidoscopeTwistToCell(float2 q, float2 apex, float span, float2 rot) {
    float2 c = apex + q * span;
    return float2(rot.x * c.x + rot.y * c.y, -rot.y * c.x + rot.x * c.y);
}

[[ stitchable ]] half4 kaleidoscopeTwistView(
    float2 p,
    SwiftUI::Layer layer,
    float2 center,
    float viewR,
    float2 cellCenter,
    float2 apex,
    float span,
    float segA,
    float segB,
    float mixB,
    float swirl,
    float2 rot
) {
    float2 d = (p - center) / viewR;
    half4 a = layer.sample(cellCenter + kaleidoscopeTwistToCell(kaleidoscopeTwistFold(d, segA, swirl), apex, span, rot));
    half4 b = layer.sample(cellCenter + kaleidoscopeTwistToCell(kaleidoscopeTwistFold(d, segB, swirl), apex, span, rot));
    half4 c = mix(a, b, half(mixB));
    float edge = clamp((1.0 - length(d)) * viewR, 0.0, 1.0);
    return c * half(edge);
}
