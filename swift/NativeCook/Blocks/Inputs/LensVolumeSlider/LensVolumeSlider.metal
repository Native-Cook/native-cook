#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The layer is the live speaker plate plus the real SwiftUI glyph and numeral.  Sampling its source through an
// inverted spherical coordinate maps both image detail and current text through the glass ball.
[[ stitchable ]] half4 lensVolumeSliderRefraction(float2 p, SwiftUI::Layer layer, float2 centre, float radius,
                                                  float gain, float marble, float scale) {
    float2 d = p - centre;
    float r = length(d);
    float q = clamp(r / max(radius, 0.001), 0.0, 1.0);
    // RN's BODY_BEND = 8 pt: the rod samples sharp photo, separately from the frosted track beneath it.
    if (r > radius) {
        float2 samplePoint = p - float2(0.0, d.y / max(radius, 0.001) * 8.0 * scale);
        return layer.sample(samplePoint);
    }
    // Magnification is a reciprocal sample transform, matching RN's inverted ball exactly.
    float magnification = mix(gain, 0.85, q * q);
    float2 samplePoint = centre - d / magnification;
    if (marble > 0.01) {
        // The mute state feels like a denser marble: sample a little more of the bright room above it.
        float2 roomPoint = float2(200.0, 70.0) * scale - d / max(radius, 0.001) * 175.0 * scale * (0.62 + 0.38 * q * q);
        samplePoint = mix(samplePoint, roomPoint, marble);
    }
    float2 normal = r > 0.001 ? d / r : float2(1.0, 0.0);
    float dispersion = mix(0.55, 1.25, smoothstep(0.38, 0.96, q));
    half4 red = layer.sample(samplePoint + normal * dispersion);
    half4 green = layer.sample(samplePoint);
    half4 blue = layer.sample(samplePoint - normal * dispersion);
    half3 colour = half3(red.r, green.g, blue.b);
    float vignette = 1.0 - 0.12 * q * q;
    float pooled = 0.10 * smoothstep(1.0, 0.25, length((d - float2(radius * 0.34, radius * 0.38)) / radius));
    return half4(colour * half(vignette) + half(pooled), green.a);
}
