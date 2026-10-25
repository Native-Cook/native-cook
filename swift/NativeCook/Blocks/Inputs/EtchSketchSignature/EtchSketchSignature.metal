#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The etch-a-sketch's powder screen and its scraped line. Step-for-step ports of the React Native
// SkSL (etchArt.ts), without its Display P3 conversion (SwiftUI colours are sRGB). Positions are
// in points, in the toy's own (unrotated) coordinates.

namespace etchSketchSignatureShader {

float h(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }

// smoothstep that also behaves for reversed edges, as SkSL's does.
float sstep(float a, float b, float x) {
    float t = clamp((x - a) / (b - a), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

float box(float2 p, float2 c, float2 e, float r) {
    float2 q = abs(p - c) - e + r;
    return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}

}

/// Aluminium powder on glass: a cool brushed grey with a fine grain, flakes that glint as the toy
/// tilts, a soft inner shadow under the bezel, and a brighter band where the powder resettles.
[[ stitchable ]] half4 etchSketchSignatureScreen(float2 p, half4 color, float4 rect, float tilt, float sweep, float band) {
    using namespace etchSketchSignatureShader;
    float2 uv = (p - rect.xy) / rect.zw;
    float3 top = float3(0.89, 0.9, 0.91);
    float3 mid = float3(0.78, 0.79, 0.81);
    float3 bot = float3(0.62, 0.635, 0.665);
    float3 c = uv.y < 0.5 ? mix(top, mid, uv.y * 2.0) : mix(mid, bot, (uv.y - 0.5) * 2.0);
    c *= float3(0.985, 0.995, 1.02);
    // A soft diagonal sheen that slides as the toy tilts.
    float sh = (uv.x * 0.8 + uv.y * 0.6 - 0.38 - tilt * 4.0) / 0.13;
    c += 0.07 * exp(-sh * sh);
    // Powder: fine grain, and flakes that catch the light (more of them as it tilts).
    float g = h(floor(p * 2.0));
    float g2 = h(floor(p * 0.6) + 7.0);
    c *= 0.92 + 0.08 * g + 0.05 * (g2 - 0.5);
    float flake = step(0.9, h(floor(p * 1.4) + 13.0));
    float ang = h(floor(p * 1.4) + 29.0) * 6.2831;
    float glint = max(0.0, cos(ang - tilt * 45.0 - 0.8));
    float g4 = glint * glint * glint * glint;
    c += 0.32 * flake * (g4 * g4 * glint * glint);
    c += 0.045 * step(0.985, h(floor(p * 2.0) + 41.0));
    float d = box(p, rect.xy + rect.zw * 0.5, rect.zw * 0.5, 14.0);
    c *= 1.0 - 0.4 * sstep(-18.0, 0.0, d);
    c *= 1.0 - 0.12 * sstep(0.06, 0.0, uv.y);
    float k = (p.y - sweep) / 22.0;
    c += 0.05 * band * exp(-k * k) * (0.4 + g);
    return half4(half3(c) * color.a, color.a);
}

/// The scraped line: a little uneven, and erased above a ragged front as the powder resettles.
[[ stitchable ]] half4 etchSketchSignatureLine(float2 p, half4 color, float sweep) {
    using namespace etchSketchSignatureShader;
    float g = h(floor(p * 2.0));
    float n = h(floor(p * 0.25) + 3.0);
    float edge = sweep + (n - 0.5) * 26.0 + (g - 0.5) * 8.0;
    float keep = sstep(edge - 2.0, edge + 2.0, p.y);
    return color * half((0.82 + 0.18 * g) * keep);
}
