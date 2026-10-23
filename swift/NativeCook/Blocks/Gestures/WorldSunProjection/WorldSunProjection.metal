#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

[[ stitchable ]] half4 worldSunProjectionEnvironment(float2 position, SwiftUI::Layer layer,
                                                     float2 size, float yawDegrees, float pitchDegrees) {
    const float yaw = yawDegrees * M_PI_F / 180.0;
    const float pitch = pitchDegrees * M_PI_F / 180.0;
    const float3 forward = float3(sin(yaw) * cos(pitch), sin(pitch), cos(yaw) * cos(pitch));
    const float3 right = float3(cos(yaw), 0.0, -sin(yaw));
    const float3 up = float3(-sin(yaw) * sin(pitch), cos(pitch), -cos(yaw) * sin(pitch));
    const float2 point = position / size * float2(393.0, 852.0);
    const float3 ray = normalize(forward + right * ((point.x - 196.5) / 510.0)
                                - up * ((point.y - 426.0) / 510.0));
    const float azimuth = atan2(ray.x, ray.z);
    const float elevation = asin(clamp(ray.y, -1.0, 1.0));
    const float2 uv = float2(fract(0.5 + azimuth / (2.0 * M_PI_F)),
                             clamp((M_PI_F * 0.5 - elevation) / (M_PI_F * 150.0 / 180.0), 0.0, 1.0));
    return layer.sample(uv * size);
}
