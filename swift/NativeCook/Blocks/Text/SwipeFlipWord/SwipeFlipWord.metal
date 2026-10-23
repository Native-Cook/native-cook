#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Swipe Flip Word: the word is printed on every face of a square prism lying along it, one prism per
// column of pixels. Each column turns about its horizontal axis (R behind the screen) by its own angle,
// so a turn that starts on one side and travels to the other twists the word like a ribbon.
// Seen through a camera F in front of the screen. See SwipeFlipWord.swift for the numbers.

/// easeInOutBack with overshoot c1 (c1 = 0 is a cubic ease-in-out).
static float swipeFlipWordEase(float x, float c1) {
    float c2 = c1 * 1.525;
    if (x < 0.5) {
        float t = 2.0 * x;
        return t * t * ((c2 + 1.0) * t - c2) / 2.0;
    }
    float t = 2.0 * x - 2.0;
    return (t * t * ((c2 + 1.0) * t + c2) + 2.0) / 2.0;
}

/// A column's turn: word = (left, width, direction ±1, -), roll = (a, b, sign ±1, overshoot).
static float swipeFlipWordAngle(float x, float4 word, float4 roll) {
    float u = clamp((x - word.x) / word.y, 0.0, 1.0);
    if (word.z < 0.0) u = 1.0 - u;
    float q = clamp(roll.x - u * roll.y, 0.0, 1.0);
    return roll.z * 1.5707963 * swipeFlipWordEase(q, roll.w);
}

/// geo = (cx, cy, R, F) in points: camera centre x, the word's glyph centre y, the prism's half side, focal length.
[[ stitchable ]] half4 swipeFlipWordRoll(float2 pos, SwiftUI::Layer layer, float4 geo, float4 word, float4 roll) {
    float cx = geo.x, cy = geo.y, R = geo.z, F = geo.w;
    float d = pos.y - cy;
    half4 result = half4(0.0);
    float nearest = -1e9;
    for (int k = -1; k <= 2; k++) {
        float x = pos.x;
        float v = 0.0, z = 0.0, psi = 0.0;
        // The column's angle depends on the source x, which depends on the depth: three rounds settle it.
        for (int i = 0; i < 3; i++) {
            psi = swipeFlipWordAngle(x, word, roll) - float(k) * 1.5707963;
            float s = sin(psi), c = cos(psi);
            v = -(F * R * s + d * (F + R - R * c)) / (d * s + F * c);
            z = -R + R * c - v * s;
            x = cx + (pos.x - cx) * (F - z) / F;
        }
        float c = cos(psi);
        if (c <= 0.001 || abs(v) > R || z <= nearest) continue;
        nearest = z;
        half4 color = layer.sample(float2(x, cy - v));
        // Faces turned away from the viewer are darker.
        half shade = half(0.55 + 0.45 * c);
        result = half4(color.rgb * shade, color.a);
    }
    return result;
}
