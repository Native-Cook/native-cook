#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Chalkboard Search — the chalk and the slate, as SwiftUI shader fills. Step-for-step ports of
// CHALK_SKSL and SLATE_SKSL in chalkboardSearchArt.ts. SwiftUI colours are sRGB, so the Display P3
// conversion of the React Native version is not needed. GLSL's smoothstep is reimplemented so it
// behaves the same with reversed edges, and the hash uses the precise sine for its large arguments.

namespace chalkboardSearchShader {

float sstep(float a, float b, float x) {
    float t = clamp((x - a) / (b - a), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

float hash21(float2 q) { return fract(precise::sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }

float vnoise(float2 q) {
    float2 i = floor(q);
    float2 f = fract(q);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + float2(1.0, 0.0)), f.x), mix(hash21(i + float2(0.0, 1.0)), hash21(i + float2(1.0, 1.0)), f.x), f.y);
}

float fbm(float2 q) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) { s += a * vnoise(q); q = q * 2.03 + 7.1; a *= 0.5; }
    return s;
}

} // namespace chalkboardSearchShader

using namespace chalkboardSearchShader;

/// Off-white chalk whose coverage breaks up with two noises (one stretched along the stroke
/// direction, like chalk dragged over slate) and a fine grain. Premultiplied output.
[[ stitchable ]] half4 chalkboardSearchChalk(float2 p, float seed) {
    float n1 = vnoise(p * float2(0.5, 0.85) + seed);
    float n2 = vnoise(p * float2(2.2, 0.6) + seed * 1.7);
    float g = hash21(floor(p * 2.0) + seed);
    float a = sstep(0.2, 0.66, n1 * 0.5 + n2 * 0.32 + g * 0.34);
    a = 0.3 + 0.7 * a;
    float3 c = float3(0.945, 0.94, 0.905);
    return half4(half3(c * a), half(a));
}

/// Dark green slate with old eraser swirls, grain and a vignette. `origin` is the slate's top-left
/// corner and `res` its size, in the same points as `p`.
[[ stitchable ]] half4 chalkboardSearchSlate(float2 pos, float2 origin, float2 res) {
    float2 p = pos - origin;
    float2 uv = p / res;
    float w = fbm(p * 0.008);
    // Old eraser swirls: arcs bent by the noise, faint and patchy.
    float arc = sin(length(p - res * float2(0.3, 0.7)) * 0.045 + w * 5.0);
    float swirl = sstep(0.55, 1.0, arc) * sstep(0.35, 0.75, fbm(p * 0.004 + 3.0));
    float smudge = sstep(0.42, 0.85, w);
    float3 base = float3(0.118, 0.169, 0.145);
    float3 lift = float3(0.22, 0.27, 0.245);
    float3 c = mix(base, lift, smudge * 0.32 + swirl * 0.22);
    c *= 1.0 + (hash21(floor(p * 1.5)) - 0.5) * 0.07;
    float v = sstep(1.0, 0.25, length((uv - 0.5) * float2(1.1, 1.0)));
    c *= mix(0.62, 1.0, v);
    return half4(half3(c), 1.0h);
}
