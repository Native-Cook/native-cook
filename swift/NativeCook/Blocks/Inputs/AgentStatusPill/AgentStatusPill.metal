#include <metal_stdlib>
using namespace metal;

// The RN shader's four materials, with the same local coordinates and loop phase.
// SwiftUI's colorEffect uses sRGB, so the Skia-on-iOS Display P3 conversion is omitted.
static float AgentStatusPillSmooth(float a, float b, float x) {
    float u = clamp((x - a) / (b - a), 0.0, 1.0);
    return u * u * (3.0 - 2.0 * u);
}

static float3 AgentStatusPillThinking(float2 q, float ph) {
    float wave = 0.55 + 0.22 * sin(q.x * 5.0 + ph * 2.0) + 0.08 * sin(q.x * 11.0 - ph * 3.0);
    float distance = (q.y - wave) / 0.22;
    float band = exp(-distance * distance);
    float3 hue = mix(float3(1.0, 0.55, 0.12), float3(0.45, 1.0, 0.45), AgentStatusPillSmooth(0.1, 0.55, q.x));
    hue = mix(hue, float3(0.72, 0.25, 1.0), AgentStatusPillSmooth(0.55, 0.95, q.x));
    float3 base = mix(float3(0.06, 0.03, 0.16), float3(0.16, 0.08, 0.3), q.y);
    return base + hue * band * 1.1 + hue * AgentStatusPillSmooth(0.75, 1.0, q.y) * 0.5;
}

static float3 AgentStatusPillSearching(float2 q, float ph) {
    float cols = floor(q.x * 6.0);
    float shade = 0.12 + 0.08 * sin(cols * 2.7 + ph * 2.0) + 0.06 * sin(q.y * 6.0 + cols + ph * 3.0);
    float distance = (fract(q.x - ph / 6.2831853) - 0.5) / 0.12;
    float sweep = exp(-distance * distance);
    float smoke = 0.08 * sin(q.x * 9.0 + q.y * 5.0 - ph * 4.0);
    return float3(shade + smoke + 0.35 * sweep) * float3(0.95, 0.95, 1.0);
}

static float3 AgentStatusPillPlanning(float2 q, float ph) {
    float edge = 0.5 + 0.12 * sin(q.y * 4.0 + ph * 2.0) + 0.06 * sin(q.y * 9.0 - ph * 3.0);
    float k = AgentStatusPillSmooth(edge - 0.08, edge + 0.08, q.x);
    float3 left = mix(float3(0.1, 0.35, 1.0), float3(0.15, 0.85, 1.0), AgentStatusPillSmooth(0.0, edge, q.x));
    float3 right = mix(float3(1.0, 0.25, 0.3), float3(0.85, 0.05, 0.15), AgentStatusPillSmooth(edge, 1.0, q.x));
    return mix(left, right, k);
}

static float3 AgentStatusPillWriting(float2 q, float ph) {
    float s = 0.5 + 0.5 * sin((q.x * 2.0 + q.y * 0.8 + 0.08 * sin(q.y * 6.0 + ph * 2.0)) * 6.2831853 - ph * 3.0);
    float3 a = float3(0.0, 0.55, 0.52);
    float3 b = float3(0.55, 0.95, 0.4);
    return mix(a, b, 0.35 * AgentStatusPillSmooth(0.1, 0.9, s) + 0.55 * q.x);
}

[[ stitchable ]] half4 agentStatusPillMaterial(float2 position, half4 color, float2 size, float4 modes, float phase) {
    float2 q = position / size;
    float3 c = AgentStatusPillThinking(q, phase) * modes.x
        + AgentStatusPillSearching(q, phase) * modes.y
        + AgentStatusPillPlanning(q, phase) * modes.z
        + AgentStatusPillWriting(q, phase) * modes.w;
    c += float3(0.22) * AgentStatusPillSmooth(0.42, 0.0, q.y) * AgentStatusPillSmooth(0.0, 0.08, q.y);
    c *= 1.0 - 0.18 * AgentStatusPillSmooth(0.7, 1.0, q.y);
    return half4(half3(clamp(c, 0.0, 1.0)) * color.a, color.a);
}
