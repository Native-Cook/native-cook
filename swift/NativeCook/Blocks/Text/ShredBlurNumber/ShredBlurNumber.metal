#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The number comes in as the layer: black glyphs on an opaque pink canvas of `size` points. The
// canvas is cut into 36 vertical strips. Each strip samples the layer through its own window:
// narrower or wider than the strip (slivers or magnified pieces), slid sideways, stretched
// vertically about the middle and shifted up or down. `amount` (0-1) blends every strip from the
// clean number to that; strips start in turn, from the left (`lead` 0), from the right (`lead` 1)
// or outwards from a finger at `focusX` (`focusMix` 1). `phase` runs 0 to 2π once per loop and
// makes the comb ripple while it is shredded. Everything is in points.
// The mapping is affine per strip, so the Kotlin and React Native ports draw each strip as a
// clipped, transformed copy of the number with the same numbers instead of a shader.

namespace ShredBlurNumberShaders {
float hash1(float n) {
    float3 p3 = fract(float3(n, n * 1.37, n * 0.73) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}
float hash2(float2 c) {
    float3 p3 = fract(float3(c.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}
}

[[ stitchable ]] half4 shredBlurNumberShred(
    float2 p, SwiftUI::Layer layer,
    float2 size, float amount, float lead, float focusX, float focusMix, float phase, float drift
) {
    // SwiftUI runs the shader over the layer grown by maxSampleOffset: keep to the canvas.
    if (p.x < 0.0 || p.y < 0.0 || p.x > size.x || p.y > size.y) { return half4(0.0); }
    const float n = 36.0;
    float w = size.x / n;
    float i = floor(p.x / w);
    float f = p.x / w - i;
    float c = (i + 0.5) * w;
    float order = mix(i / (n - 1.0), 1.0 - i / (n - 1.0), lead);
    order = mix(order, clamp(abs(c - focusX) / (size.x * 0.6), 0.0, 1.0), focusMix);
    float a = clamp(amount * 2.2 - 1.2 * order, 0.0, 1.0);
    a = a * a * (3.0 - 2.0 * a);
    if (a <= 0.0) { return layer.sample(p); }

    float h1 = ShredBlurNumberShaders::hash1(i + 1.0);
    float h2 = ShredBlurNumberShaders::hash1(i + 31.0);
    float h3 = ShredBlurNumberShaders::hash1(i + 71.0);
    // Two slow waves across the strips, so neighbours look alike (the comb), plus a little jitter.
    float s1 = 0.5 + 0.5 * sin(i * 0.35 + phase + 1.0);
    float s2 = 0.5 + 0.5 * sin(i * 0.22 - 2.0 * phase + 2.0);
    float span = mix(1.0, 1.4 + 2.2 * s1 + 0.8 * h1, a);
    float cx = c + a * ((s2 - 0.5) * 2.0 * w + (h2 - 0.5) * 1.6 * w + drift * (0.5 + h3));
    float xs = cx + (f - 0.5) * w * span;
    float stretch = 1.0 + a * (0.3 + 1.6 * s2 + 0.6 * h2);
    float yoff = a * size.y * 0.10 * (sin(i * 0.4 + 2.0 * phase + 1.3) + 0.8 * (h1 - 0.5));
    float ys = size.y * 0.5 + (p.y - size.y * 0.5 - yoff) / stretch;
    return layer.sample(float2(xs, ys));
}

// Film grain over the whole screen: cells of 1.25 pt, a new pattern `frame` by `frame`, white and
// black specks up to `amount` × 0.5 opacity. Premultiplied output.
[[ stitchable ]] half4 shredBlurNumberGrain(float2 p, half4 color, float amount, float frame) {
    float v = ShredBlurNumberShaders::hash2(floor(p / 1.25) + float2(frame * 17.0, frame * 31.0)) - 0.5;
    float a = amount * abs(v);
    return v > 0.0 ? half4(half(a), half(a), half(a), half(a)) : half4(0.0, 0.0, 0.0, half(a));
}
