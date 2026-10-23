#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// One sampled surface: the title, tile grid and palm shadow are all below this water.
[[ stitchable ]] half4 poolCausticWelcomeWater(
    float2 p,
    SwiftUI::Layer layer,
    float2 size,
    float phase,
    float amplitude,
    float2 pointer,
    float pointerStrength,
    float2 velocity
) {
    float2 world = p + float2(18.0, 83.0);
    float k1 = 0.029 * world.x + 0.018 * world.y - phase;
    float k2 = -0.021 * world.x + 0.031 * world.y + 2.0 * phase;
    float k3 = 0.038 * world.x - 0.012 * world.y - 3.0 * phase;
    float2 slope = float2(0.029 * cos(k1) - 0.01155 * cos(k2) + 0.0095 * cos(k3),
                          0.018 * cos(k1) + 0.01705 * cos(k2) - 0.003 * cos(k3));
    float2 delta = p - pointer;
    float lens = exp(-dot(delta, delta) / (115.0 * 115.0)) * pointerStrength;
    float2 shift = slope * (60.0 + 200.0 * amplitude + 90.0 * lens) + velocity * (4.0 * lens);
    float titleBand = exp(-pow((world.y - 420.0) / 115.0, 2.0));
    shift.y += 7.0 * titleBand * sin(0.045 * world.x + phase);
    float2 q = clamp(p + shift, float2(0.5), size - 0.5);
    half4 sampled = layer.sample(q);
    float3 col = float3(sampled.rgb);
    float softLight = pow(max(0.0, 0.55 + 0.25 * sin(k1 + k2) + 0.20 * cos(k2 - k3)), 8.0);
    float ridge = pow(max(0.0, 1.0 - abs(sin(k1 + k2 + 0.35 * sin(k3))) * 2.6), 4.0);
    float light = (0.65 * softLight + 0.35 * ridge) * (0.03 + 0.26 * amplitude + 0.10 * lens);
    // This is the same sampled working color space, with only a pale lift of the water light.
    col = mix(col, float3(0.615, 0.897, 0.956), min(light, 0.30));
    return half4(half3(col), sampled.a);
}
