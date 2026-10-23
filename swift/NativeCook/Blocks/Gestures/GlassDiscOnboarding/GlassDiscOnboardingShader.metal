#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

namespace GlassDiscOnboardingShader {

float smooth(float edge0, float edge1, float x) {
    float u = clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
    return u * u * (3.0 - 2.0 * u);
}

float3 backdrop(float2 p, float2 size, float4 topLeft, float4 topRight,
                float4 bottomLeft, float4 bottomRight) {
    float2 u = clamp(p / size, 0.0, 1.0);
    return mix(mix(topLeft.rgb, topRight.rgb, u.x),
               mix(bottomLeft.rgb, bottomRight.rgb, u.x), u.y);
}

float reach(float radiusShare, float fold, float foldBack, float foldShape) {
    float peak = fold * foldShape / (foldShape + 1.0);
    if (radiusShare < fold) {
        return radiusShare * (1.0 - pow(radiusShare / fold, foldShape) / (foldShape + 1.0));
    }
    float beyond = radiusShare - fold;
    return peak - foldBack * beyond * beyond;
}

} // namespace GlassDiscOnboardingShader

// ColorEffect on an opaque full-screen rectangle. The four corners are the exact RN theme colours.
[[ stitchable ]] half4 glassDiscOnboardingBackdrop(
    float2 p, half4 inputColor, float2 size,
    float4 bgTL, float4 bgTR, float4 bgBL, float4 bgBR
) {
    return half4(half3(GlassDiscOnboardingShader::backdrop(p, size, bgTL, bgTR, bgBL, bgBR)), 1.0h);
}

// LayerEffect on a transparent W×2H texture. Top: the community screen in place. Bottom: its lone
// headline at (W/2, 1.5H). Clip the output to H, after applying the effect. Coordinates are points.
[[ stitchable ]] half4 glassDiscOnboardingLens(
    float2 p, SwiftUI::Layer layer, float2 size,
    float4 geometry,         // cx, cy, radius, stretch
    float4 light,            // glint, glintAngle, lightX, lightY
    float4 effects,          // glow, spreadBoost, reveal, ring
    float2 texOrigin,
    float4 lensA,            // fold, foldBack, foldShape, kY
    float4 lensB,            // kX, squeezeX, spread, fringeFrom
    float4 lensC,            // fade, mirrorFloor, ringFrom, ringTo
    float4 rim,              // rimLight, rimWidth, shadowAt, shadowWidth
    float4 shadow,           // shadowAlpha, beadFrom, beadSpan, beadFrost
    float2 glass,            // domeFrost, alpha
    float4 bgTL, float4 bgTR, float4 bgBL, float4 bgBR,
    float4 shadowColor, float4 ringInner, float4 ringCore, float4 ringOuter, float4 ringBlush
) {
    float2 center = geometry.xy;
    float radius = geometry.z;
    float alpha = glass.y;
    if (alpha <= 0.001 || radius <= 0.5 || p.y >= size.y) return half4(0.0h);

    float small = clamp((shadow.y - radius) / shadow.z, 0.0, 1.0);
    float k = sqrt(1.0 + geometry.w);
    float2 d = float2((p.x - center.x) * k, (p.y - center.y) / k) / radius;
    float r = length(d);
    float len = r * radius;
    if (r >= 1.0) {
        float t = (len - radius - rim.z) / rim.w;
        float a = shadow.x * (1.0 + small) * exp(-t * t) * alpha
                * (0.5 + GlassDiscOnboardingShader::smooth(-0.4, 1.0, d.y));
        float g = effects.x * effects.w * exp(-pow((r - 1.0) / 0.22, 2.0))
                * GlassDiscOnboardingShader::smooth(-0.2, 0.8, d.y) * alpha;
        float3 castColor = mix(ringCore.rgb, ringOuter.rgb, 0.5 + 0.5 * d.x);
        return half4(half3(shadowColor.rgb * a + castColor * g), half(a + g));
    }

    float3 bg = GlassDiscOnboardingShader::backdrop(p, size, bgTL, bgTR, bgBL, bgBR);
    float2 unit = r > 0.0001 ? d / r : float2(0.0);
    float foldedRadius = length(float2(d.x * lensB.y, d.y));
    float foldedReach = GlassDiscOnboardingShader::reach(foldedRadius, lensA.x, lensA.y, lensA.z);
    float2 offset = float2(unit.x * foldedReach * lensB.x, unit.y * foldedReach * lensA.w) * radius;
    float fringe = clamp((r - lensB.w) / max(0.001, lensA.x - lensB.w), 0.0, 1.0);
    float2 spread = unit * lensB.z * (1.0 + effects.y) * fringe;
    float visibility = effects.z * (foldedRadius < lensA.x ? 1.0
                       : clamp(1.0 - (foldedRadius - lensA.x) * lensC.x, lensC.y, 1.0));
    half4 redSample = layer.sample(texOrigin + offset + spread);
    half4 greenSample = layer.sample(texOrigin + offset);
    half4 blueSample = layer.sample(texOrigin + offset - spread);
    float3 color = float3(
        bg.r * (1.0 - redSample.a * visibility) + redSample.r * visibility,
        bg.g * (1.0 - greenSample.a * visibility) + greenSample.g * visibility,
        bg.b * (1.0 - blueSample.a * visibility) + blueSample.b * visibility
    );

    float below = GlassDiscOnboardingShader::smooth(-0.12, 0.42, d.y);
    float across = clamp((r - lensC.z) / (lensC.w - lensC.z), 0.0, 1.0);
    float band = GlassDiscOnboardingShader::smooth(0.0, 0.18, across)
               * (1.0 - GlassDiscOnboardingShader::smooth(0.72, 1.0, across)) * below;
    float3 iris = mix(ringInner.rgb, ringCore.rgb,
                      GlassDiscOnboardingShader::smooth(0.08, 0.42, across));
    iris = mix(iris, ringOuter.rgb, GlassDiscOnboardingShader::smooth(0.5, 0.85, across));
    iris = mix(iris, ringBlush.rgb,
               GlassDiscOnboardingShader::smooth(0.82, 1.0, across) * 0.6);
    color = mix(color, float3(1.0), effects.w * 0.22
                * GlassDiscOnboardingShader::smooth(lensC.w - 0.05, 1.0, r) * below);
    color = mix(color, iris, clamp(band * effects.w, 0.0, 1.0));
    color = mix(color, float3(1.0), glass.x
                * (0.55 + 0.45 * GlassDiscOnboardingShader::smooth(0.2, -0.9, d.y)));
    color = mix(color, float3(1.0), shadow.w * small
                * (0.6 + 0.4 * GlassDiscOnboardingShader::smooth(0.3, -0.8, d.y)));
    float rimBand = GlassDiscOnboardingShader::smooth(1.0 - rim.y / radius,
                                                      1.0 - 1.5 / radius, r);
    color += rim.x * rimBand;
    float angle = atan2(d.y, d.x);
    float deltaAngle = angle - light.y;
    deltaAngle -= 6.2831853 * floor((deltaAngle + 3.1415927) / 6.2831853);
    color += light.x * 0.32 * exp(-deltaAngle * deltaAngle / 0.05)
           * GlassDiscOnboardingShader::smooth(0.82, 0.99, r);
    float2 highlight = float2(-0.36, -0.46) + light.zw * 0.3;
    color += 0.035 * exp(-dot(d - highlight, d - highlight) / 0.06)
           * (1.0 - small * 0.5);

    float px = 1.0 / radius;
    float edge = 1.0 - GlassDiscOnboardingShader::smooth(1.0 - 1.5 * px, 1.0, r);
    float t = (len - radius - rim.z) / rim.w;
    float shadowAlpha = shadow.x * (1.0 + small) * exp(-t * t);
    float outputAlpha = alpha * (edge + (1.0 - edge) * shadowAlpha);
    float3 outputColor = color * edge * alpha
                       + shadowColor.rgb * shadowAlpha * (1.0 - edge) * alpha;
    return half4(half3(outputColor), half(outputAlpha));
}
