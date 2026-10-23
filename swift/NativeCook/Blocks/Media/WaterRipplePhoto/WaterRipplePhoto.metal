#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The photo as seen through moving water, the same shader as the React Native block's, run as a
// layer effect. The layer holds the current photo, cropped to fill the screen; the next photo
// comes as a texture with the rectangle it fills. For each ripple the height of the surface is a
// wave packet around a ring of radius 190 pt/s × age; its slope bends the light, shifting the
// image along the ring's radius, and its curvature focuses light into bright caustic lines. The
// swell to the next photo is one more, much larger packet whose front reveals the next photo.

constant float waterRipplePhotoPi = 3.14159265;

[[ stitchable ]] half4 waterRipplePhotoWater(
    float2 p,
    SwiftUI::Layer layer,
    float2 size,
    float time,
    device const float *ripples,
    int rippleFloats,
    texture2d<half> nextPhoto,
    float4 nextRect,
    float2 origin,
    float swell,
    float mixing,
    float still
) {
    const float k = 2.0 * waterRipplePhotoPi / 24.0;
    float2 shift = float2(0.0);
    float light = 0.0;
    float motion = 1.0 - still;

    // A slow swell all over, so the surface is never quite still.
    shift += motion * 1.4 * float2(sin(p.y * 0.031 + time * 1.3), cos(p.x * 0.027 + time * 1.05));

    for (int i = 0; i + 3 < rippleFloats; i += 4) {
        float4 r = float4(ripples[i], ripples[i + 1], ripples[i + 2], ripples[i + 3]);
        float age = time - r.z;
        if (r.w <= 0.0 || age < 0.0 || age > 3.2) continue;
        float2 v = p - r.xy;
        float d = length(v);
        float x = d - 190.0 * age;
        float width = 14.0 + 26.0 * age;
        float env = exp(-x * x / (width * width)) * r.w * exp(-1.15 * age) / sqrt(1.0 + d * 0.012);
        // Fades in over the first moments so a ring does not pop.
        env *= smoothstep(0.0, 0.06, age);
        float phase = x * k;
        shift += (v / max(d, 1.0)) * cos(phase) * env * 9.0 * motion;
        // Under reduced motion the rings only glint softly.
        light -= sin(phase) * env * (0.35 + 0.65 * motion);
    }

    // The swell's front, with a train of smaller waves behind it.
    float mask = mixing;
    if (swell > 0.0) {
        float2 v = p - origin;
        float d = length(v);
        float x = d - swell;
        float front = exp(-x * x / (60.0 * 60.0));
        float behind = x < 0.0 ? exp(x / 160.0) : 0.0;
        float wave = cos(x * 0.075) * front * 17.0 + sin(x * 0.15) * behind * 4.0;
        shift += (v / max(d, 1.0)) * wave * motion;
        light -= (sin(x * 0.075) * front * 0.38 + cos(x * 0.15) * behind * 0.22) * motion;
        float edge = x + sin(atan2(v.y, v.x) * 7.0 + swell * 0.01) * 10.0;
        mask = 1.0 - smoothstep(-26.0, 26.0, edge);
    }

    float2 q = clamp(p + shift, float2(0.5), size - 0.5);
    float3 col = float3(layer.sample(q).rgb);
    if (mask > 0.001) {
        constexpr sampler linear(coord::normalized, address::clamp_to_edge, filter::linear);
        float3 b = float3(nextPhoto.sample(linear, (q - nextRect.xy) / nextRect.zw).rgb);
        col = mix(col, b, mask);
    }

    // Caustics: bright where the surface focuses light, a little darker where it spreads it.
    float glint = max(light, 0.0);
    col += float3(1.0, 0.98, 0.95) * pow(glint, 1.4) * 0.3;
    col *= 1.0 + min(light, 0.0) * 0.16;
    return half4(half3(col), 1.0);
}
