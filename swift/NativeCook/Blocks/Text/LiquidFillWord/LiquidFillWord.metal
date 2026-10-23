#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The word, drawn white, comes in as the layer. Its edges are wobbled by sampling it a little off
// position, and each pixel inside is coloured by the liquid it sits in: lime at the bottom, green
// above it, salmon on top, each with a wavy surface, and green (with salmon cores) blobs swirling
// in the lime near its surface. Everything is in points; `phase` runs 0 to 2π once per loop.
// The same function is written in AGSL (Kotlin) and SkSL (React Native).
[[ stitchable ]] half4 liquidFillWordFill(
    float2 p, SwiftUI::Layer layer,
    float phase, float lime, float green, float salmon, float blobs, float slosh, float bottomY, float topY,
    half4 limeColor, half4 greenColor, half4 salmonColor, half4 ghostColor
) {
    float ph = phase;
    float2 w = float2(
        sin(p.y * 0.31 + p.x * 0.07 + 2.0 * ph) + 0.25 * sin(p.y * 0.73 - p.x * 0.19 - 3.0 * ph),
        cos(p.x * 0.27 - p.y * 0.05 + 3.0 * ph) + 0.25 * cos(p.x * 0.61 + p.y * 0.23 + 2.0 * ph)
    ) * 1.0;
    float a = float(layer.sample(p + w).a);
    float edge = smoothstep(0.35, 0.65, a);
    if (edge <= 0.0) { return half4(0.0); }

    float h = bottomY - topY;
    float u = (bottomY - p.y) / h;
    float aa = 0.9 / h;
    float amp = 0.025 + 0.045 * slosh;
    float wl = amp * (0.6 * sin(p.x * 0.045 + 2.0 * ph) + 0.4 * sin(p.x * 0.11 - 3.0 * ph + 1.3));
    float wg = amp * (0.6 * sin(p.x * 0.05 - 2.0 * ph + 2.1) + 0.4 * sin(p.x * 0.13 + 3.0 * ph + 0.4));
    float ws = amp * (0.6 * sin(p.x * 0.04 + 3.0 * ph + 4.0) + 0.4 * sin(p.x * 0.12 - 2.0 * ph + 2.6));
    float cl = 1.0 - smoothstep(lime + wl - aa, lime + wl + aa, u);
    float cg = 1.0 - smoothstep(green + wg - aa, green + wg + aa, u);
    float cs = 1.0 - smoothstep(salmon + ws - aa, salmon + ws + aa, u);

    float3 col = float3(ghostColor.rgb);
    col = mix(col, float3(salmonColor.rgb), cs);
    col = mix(col, float3(greenColor.rgb), cg);
    col = mix(col, float3(limeColor.rgb), cl);

    float b = sin(p.x * 0.05 + 2.0 * ph) + sin(p.y * 0.07 - ph + p.x * 0.025) + sin((p.x - p.y) * 0.04 + 3.0 * ph);
    float near = smoothstep(lime - 1.05, lime - 0.45, u);
    float tg = 3.1 - 2.4 * blobs * near;
    col = mix(col, float3(greenColor.rgb), smoothstep(tg - 0.04, tg + 0.04, b) * cl);
    float ts = 3.1 - 1.2 * blobs * near;
    col = mix(col, float3(salmonColor.rgb), smoothstep(ts - 0.04, ts + 0.04, b) * cl);

    return half4(half3(col * edge), half(edge));
}
