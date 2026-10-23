#include <metal_stdlib>
using namespace metal;

// Sampling-only inverse coordinate; RGB and premultiplied alpha stay untouched.
[[stitchable]] float2 moodPetalBowlRefraction(float2 position, float originY) {
    float depth = clamp((position.y + originY - 273.0f) / 153.0f, 0.0f, 1.0f);
    return float2(position.x - 1.6f * depth, position.y);
}
