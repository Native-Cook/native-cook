#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The glass rod, the same shader as the React Native block's, run as a layer effect over the
// pages. Everything under the rod is seen through a cylinder: across it the image is flipped and
// squeezed (the centre shows a strip 0.55 times as wide, the edges reach 1.45 radii to the other
// side), red and blue split a little, the edges darken as the glass turns away, and two highlights
// run down it. Outside the rod, a soft shadow falls on the page to its right. All in points.

[[ stitchable ]] half4 glassRodOnboardingRod(
    float2 p,
    SwiftUI::Layer layer,
    float2 size,
    float2 center,
    float angle,
    float radius,
    float chroma,
    float shade
) {
    float2 n = float2(cos(angle), sin(angle));
    float2 t = float2(-n.y, n.x);
    float u = dot(p - center, n);
    float s = u / radius;
    if (abs(s) >= 1.0) {
        half4 c = layer.sample(p);
        float shadow = s > 1.0 ? (1.0 - smoothstep(1.0, 1.9, s)) * 0.3 * shade : 0.0;
        return half4(c.rgb * half(1.0 - shadow), c.a);
    }
    float z = sqrt(max(0.0, 1.0 - s * s));
    float bend = -s * (0.55 + 0.9 * (1.0 - z));
    float along = dot(p - center, t);
    float2 base = center + t * (along * (0.97 + 0.03 * z));
    float2 lo = float2(0.5);
    float2 hi = size - 0.5;
    float2 pr = clamp(base + n * radius * bend * (1.0 + chroma), lo, hi);
    float2 pg = clamp(base + n * radius * bend, lo, hi);
    float2 pb = clamp(base + n * radius * bend * (1.0 - chroma), lo, hi);
    float3 col = float3(layer.sample(pr).r, layer.sample(pg).g, layer.sample(pb).b);
    // A faint warm body to the glass, darker where it turns away.
    col = col * (0.9 + 0.1 * z) * float3(1.0, 0.985, 0.965);
    float fres = pow(1.0 - z, 2.4);
    col = mix(col, float3(0.05, 0.04, 0.035), fres * 0.6);
    // Light from the upper left: a broad highlight, a thin one on the far side, a lit rim.
    float down = clamp(along / size.y + 0.5, 0.0, 1.0);
    float spec = exp(-pow((s + 0.5) / 0.1, 2.0)) * (0.5 - 0.2 * down) + exp(-pow((s - 0.8) / 0.045, 2.0)) * 0.2;
    float rim = smoothstep(0.9, 1.0, abs(s)) * 0.28;
    col += float3(spec + rim);
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0h);
}
