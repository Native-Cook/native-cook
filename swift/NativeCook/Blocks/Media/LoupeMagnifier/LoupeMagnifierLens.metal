#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The whole screen in one pass, as a layer effect on the cover-fitted photo: the photo dimmed with
// a vignette, the loupe's soft shadow, and inside the glass the photo magnified with barrel
// distortion, colour fringes and a dark edge. `p` is in the photo layer's points; `origin` is where
// the layer's top left sits on the screen, `layerSize` its size.
[[ stitchable ]] half4 loupeMagnifierLens(
    float2 p, SwiftUI::Layer layer, float2 origin, float2 layerSize, float2 viewSize,
    float2 center, float radius, float rim, float zoom, float dim,
    float2 shadowOffset, float shadowSoft, float barrel, float fringe, float shown
) {
    float2 s = p + origin;
    float2 lo = float2(0.5);
    float2 hi = layerSize - 0.5;
    float2 d = s - center;
    float r = length(d) / radius;
    if (r < 1.0) {
        float k = 1.0 + barrel * r * r;
        float split = fringe * r * r * r;
        float2 base = d / zoom;
        half3 col;
        col.r = layer.sample(clamp(center + base * (k + split) - origin, lo, hi)).r;
        col.g = layer.sample(clamp(center + base * k - origin, lo, hi)).g;
        col.b = layer.sample(clamp(center + base * (k - split) - origin, lo, hi)).b;
        col *= 1.06 * (1.0 - 0.5 * smoothstep(0.74, 1.0, r));
        return half4(col * shown, 1.0);
    }
    half3 col = layer.sample(clamp(p, lo, hi)).rgb * dim * shown;
    float2 uv = s / viewSize - 0.5;
    col *= 1.0 - 0.7 * dot(uv, uv);
    float sd = length(s - center - shadowOffset) - (radius + rim);
    col *= 1.0 - 0.5 * (1.0 - smoothstep(-shadowSoft * 0.4, shadowSoft, sd));
    return half4(col, 1.0);
}
