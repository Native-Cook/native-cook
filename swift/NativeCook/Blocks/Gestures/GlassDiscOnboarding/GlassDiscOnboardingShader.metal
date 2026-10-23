#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

namespace GlassDiscOnboardingLensShader {

float smooth(float edge0, float edge1, float value) {
    float amount = clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
    return amount * amount * (3.0 - 2.0 * amount);
}

} // namespace GlassDiscOnboardingLensShader

/// A direct port of the RN SkSL lens. The layer is the map page, already rasterized once at mount.
[[ stitchable ]] half4 glassDiscOnboardingLens(
    float2 position,
    SwiftUI::Layer map,
    float2 size,
    float2 center,
    float radius,
    float magnification,
    float rim,
    float lift,
    float2 look
) {
    float2 delta = position - center;
    float distance = length(delta);
    float edge = radius - distance;
    float2 unit = delta / max(distance, 0.001);
    if (edge < 0.0) {
        float outside = -edge;
        float alpha = rim * lift * 0.16 * exp(-pow(outside / 9.0, 2.0))
                    * (0.35 + 0.65 * GlassDiscOnboardingLensShader::smooth(-0.5, 1.0, unit.y));
        return half4(half3(float3(0.11, 0.17, 0.13) * alpha), half(alpha));
    }

    float inside = clamp(edge / 18.0, 0.0, 1.0);
    float band = (1.0 - inside) * (1.0 - inside) * rim;
    float2 samplePoint = center + look + delta / magnification - unit * band * 20.0;
    float spread = band * 2.2;
    float3 color = float3(
        map.sample(samplePoint - unit * spread).r,
        map.sample(samplePoint).g,
        map.sample(samplePoint + unit * spread).b
    );

    float hairline = (1.0 - GlassDiscOnboardingLensShader::smooth(0.0, 1.4, edge)) * rim;
    float upper = pow(max(dot(unit, float2(-0.32, -0.95)), 0.0), 1.4);
    float lower = pow(max(dot(unit, float2(0.25, 0.97)), 0.0), 2.0);
    color = mix(color, float3(1.0), hairline * (0.55 + 0.4 * upper));
    color += band * 0.1 * upper;
    color *= 1.0 - 0.14 * band * (1.0 - upper) * (0.4 + 0.6 * lower);
    float coverage = GlassDiscOnboardingLensShader::smooth(-0.6, 0.6, edge);
    return half4(half3(color * coverage), half(coverage));
}
