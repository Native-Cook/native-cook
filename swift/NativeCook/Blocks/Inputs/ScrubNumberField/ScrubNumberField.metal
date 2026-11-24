#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Scrub Number Field — the photo's adjustments and the digits' smear.
//
// scrubNumberFieldGrade is a port of PHOTO_SKSL (scrubShader.ts), step for step:
//   warmth   -1..1   white balance: red up and blue down (or the reverse), luminance kept
//   exposure in stops: linear light times 2^(1.35 e - 0.75), then a soft shoulder
//   vignette 0..1    darkens towards the corners
// Colour: Skia runs the effect on the image converted to the canvas's working space (Display P3 with
// the sRGB curve on an iPhone). SwiftUI hands a shader extended sRGB, so the pixel is taken to
// Display P3 first, graded there with the same numbers, and brought back to (extended) sRGB.

namespace scrubNumberFieldShader {

float3 toLinear(float3 c) {
    float3 a = abs(c);
    float3 lo = a / 12.92;
    float3 hi = pow((a + 0.055) / 1.055, float3(2.4));
    return sign(c) * mix(lo, hi, step(float3(0.04045), a));
}

float3 toEncoded(float3 lin) {
    float3 a = abs(lin);
    float3 lo = a * 12.92;
    float3 hi = 1.055 * pow(a, float3(1.0 / 2.4)) - 0.055;
    return sign(lin) * mix(lo, hi, step(float3(0.0031308), a));
}

// Linear up to 0.7, then an exponential shoulder that reaches 1 only at infinity.
float3 shoulder(float3 x) {
    float3 over = max(x - 0.7, 0.0);
    return min(x, float3(0.7)) + 0.3 * (1.0 - exp(-over / 0.3));
}

float ign(float2 p) { return fract(52.9829189 * fract(dot(p, float2(0.06711056, 0.00583715)))); }

float3 srgbToP3(float3 c) {
    return float3(
        dot(c, float3(0.8224621, 0.1775380, 0.0)),
        dot(c, float3(0.0331941, 0.9668058, 0.0)),
        dot(c, float3(0.0170827, 0.0723974, 0.9105199)));
}

float3 p3ToSrgb(float3 c) {
    return float3(
        dot(c, float3(1.2249401, -0.2249404, 0.0)),
        dot(c, float3(-0.0420569, 1.0420571, 0.0)),
        dot(c, float3(-0.0196376, -0.0786361, 1.0982735)));
}

} // namespace scrubNumberFieldShader

[[ stitchable ]] half4 scrubNumberFieldGrade(float2 position, half4 color, float2 size, float exposure, float warmth, float vignette, float dpr) {
    using namespace scrubNumberFieldShader;
    // the image as Skia's working space holds it: Display P3, sRGB curve
    float3 p3 = toEncoded(srgbToP3(toLinear(float3(color.rgb))));
    float3 lin = toLinear(clamp(p3, 0.0, 1.0));

    float3 wb = float3(1.0 + 0.50 * warmth, 1.0 + 0.06 * warmth, 1.0 - 0.60 * warmth);
    wb /= dot(wb, float3(0.2126, 0.7152, 0.0722));
    lin *= wb;

    lin *= exp2(1.35 * exposure - 0.75);

    float2 q = position / size - 0.5;
    float d = length(q) * 1.41421; // 0 centre, 1 corners
    lin *= 1.0 - 0.95 * vignette * smoothstep(0.18, 0.92, d);

    float3 rgb = toEncoded(shoulder(max(lin, 0.0)));
    float2 px = position * dpr;
    rgb += (ign(px) + ign(px + float2(41.0, 17.0)) - 1.0) / 255.0; // triangular dither
    rgb = clamp(rgb, 0.0, 1.0);

    float3 out = toEncoded(p3ToSrgb(toLinear(rgb)));
    return half4(half3(out), 1.0h) * color.a;
}

// The odometer's smear: inside the two fastest digit columns the layer is blurred vertically (a
// Gaussian of the column's own sigma, in pt) and then cut to the one-digit window, which is what
// Skia's blur layer inside a clip does. colA/colB = (left, right, sigma, 0); slot = (top, bottom).
[[ stitchable ]] half4 scrubNumberFieldSmear(float2 position, SwiftUI::Layer layer, float4 colA, float4 colB, float2 slot) {
    float sigma = -1.0;
    if (position.x >= colA.x && position.x <= colA.y) sigma = colA.z;
    else if (position.x >= colB.x && position.x <= colB.y) sigma = colB.z;
    if (sigma < 0.0) return layer.sample(position);
    if (position.y < slot.x || position.y > slot.y) return half4(0.0h);
    if (sigma < 0.05) return layer.sample(position);
    half4 sum = half4(0.0h);
    float total = 0.0;
    for (int i = -16; i <= 16; i++) {
        float u = float(i) * (3.0 / 16.0);
        float w = exp(-0.5 * u * u);
        sum += layer.sample(position + float2(0.0, u * sigma)) * half(w);
        total += w;
    }
    return sum / half(total);
}
