#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The rows are the actual shared Geometry.rotation (forward Rz*Rx*Ry).
static float3 lenticularEditionPassInverse(float3 v, float3 r0, float3 r1, float3 r2) {
    return float3(dot(float3(r0.x, r1.x, r2.x), v),
                  dot(float3(r0.y, r1.y, r2.y), v),
                  dot(float3(r0.z, r1.z, r2.z), v));
}

static float lenticularEditionPassBow(float2 p) {
    return 3.5f * (1.0f - 4.0f * p.x * p.x / (333.0f * 333.0f))
                * (1.0f - 4.0f * p.y * p.y / (211.0f * 211.0f));
}

static float2 lenticularEditionPassBowGradient(float2 p) {
    return float2(-28.0f * p.x / (333.0f * 333.0f) * (1.0f - 4.0f * p.y * p.y / (211.0f * 211.0f)),
                  -28.0f * p.y / (211.0f * 211.0f) * (1.0f - 4.0f * p.x * p.x / (333.0f * 333.0f)));
}

static float lenticularEditionPassDistance(float2 p) {
    float2 q = abs(p) - float2(160.5f, 99.5f);
    return length(max(q, 0.0f)) + min(max(q.x, q.y), 0.0f) - 6.0f;
}

static float3 lenticularEditionPassIntersection(float3 c, float3 d, float offset) {
    if (abs(d.z) < 0.1f) return float3(10000.0f);
    float plane = -c.z / d.z;
    float t = plane;
    // Card bow and laminate are less than5pt deep; a12pt bracket is conservative.
    for (int i = 0; i < 6; ++i) {
        float3 p = c + t * d;
        float2 gradient = lenticularEditionPassBowGradient(p.xy);
        float residual = p.z - lenticularEditionPassBow(p.xy) - offset;
        float derivative = d.z - dot(gradient, d.xy);
        if (abs(derivative) > 0.1f) t = clamp(t - residual / derivative, plane - 12.0f, plane + 12.0f);
    }
    return c + t * d;
}

[[ stitchable ]] half4 lenticularEditionPassLens(float2 position, SwiftUI::Layer layer,
                                                float3 row0, float3 row1, float3 row2,
                                                float actualYaw, float explicitViewB) {
    // The350x265 output is top-leading inside a350x426 source. Center is175/124.
    float2 screen = position - float2(175.0f, 124.0f);
    float3 c = lenticularEditionPassInverse(float3(0, 0, 800), row0, row1, row2);
    float3 d = lenticularEditionPassInverse(float3(screen / 800.0f, -1.0f), row0, row1, row2);
    float3 front = lenticularEditionPassIntersection(c, d, 0.0f);
    float distance = lenticularEditionPassDistance(front.xy);
    float residual = abs(front.z - lenticularEditionPassBow(front.xy));
    float coverage = residual <= 0.02f ? 1.0f - smoothstep(-0.32f, 0.32f, distance) : 0.0f;

    float3 back = lenticularEditionPassIntersection(c, d, -1.4f);
    float backDistance = lenticularEditionPassDistance(back.xy);
    float backResidual = abs(back.z - lenticularEditionPassBow(back.xy) + 1.4f);
    float backCoverage = backResidual <= 0.02f ? 1.0f - smoothstep(-0.32f, 0.32f, backDistance) : 0.0f;
    // Reject non-card pixels before sampling the tall source atlas.
    if (coverage <= 0.0f && backCoverage <= 0.0f) return half4(0);

    float edgeLight = 1.0f - smoothstep(-30.0f, 30.0f, back.y);
    half3 edge = mix(half3(0.39h, 0.48h, 0.55h), half3(0.80h, 0.85h, 0.83h), half(edgeLight));
    half4 laminate = half4(edge * half(backCoverage), half(backCoverage));
    if (coverage <= 0.0f) return laminate;

    constexpr float pitch = 1.45f;
    constexpr float pi = 3.14159265358979323846f;
    float ridge = floor((front.x + 166.5f) / pitch);
    float ridgeCenter = (ridge + 0.5f) * pitch - 166.5f;
    float beta = actualYaw + 0.035f * ridgeCenter + 0.8f * sin(2.0f * pi * ridgeCenter / 333.0f);
    float viewB = explicitViewB >= 0.0f ? clamp(explicitViewB, 0.0f, 1.0f) : smoothstep(-0.7f, 0.7f, beta);
    float phase = fract((front.x + 166.5f) / pitch);
    float displacement = 0.29267578125f * sin(2.0f * pi * phase);
    float x = front.x + 175.0f + displacement;
    half4 a = layer.sample(float2(x, front.y + 106.5f));
    half4 b = layer.sample(float2(x, front.y + 319.5f));
    half4 print = mix(a, b, half(viewB));
    float shade = 1.0f + 0.055f * cos(2.0f * pi * phase) + 0.025f * cos(4.0f * pi * phase + 0.7f);
    float trough = 0.045f * smoothstep(0.9f, 1.0f, phase);
    print.rgb *= half(shade * (1.0f - trough));

    float reflectedCenter = 22.5f * clamp(actualYaw / 14.0f, -1.0f, 1.0f);
    float sigma = 33.972872f;
    float reflection = (0.06f + 0.08f * clamp(abs(actualYaw) / 14.0f, 0.0f, 1.0f))
                     * exp(-0.5f * pow((front.x - reflectedCenter) / sigma, 2.0f));
    print.rgb += (print.a - print.rgb) * half(reflection);
    float border = 1.0f - smoothstep(-1.2f, -0.8f, distance);
    // Only the innermost1pt border is printed; the face remains a lightly rounded physical card.
    float borderWeight = (1.0f - border) * 0.35f;
    print.rgb = mix(print.rgb, half3(0.718h, 0.776h, 0.808h) * print.a, half(borderWeight));
    half4 face = print * half(coverage);
    return face + laminate * (1.0h - face.a);
}
