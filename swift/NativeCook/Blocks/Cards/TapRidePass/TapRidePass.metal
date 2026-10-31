#include <metal_stdlib>
using namespace metal;
/// Thermal paper in local point coordinates: warm fibres, a sheen and darkened edges.
/// paperSize is the ticket's current width / height in points; incoming alpha retains the view mask.
[[ stitchable ]] half4 tapRidePassPaper(float2 position, half4 color, float2 paperSize) {
    float2 localPosition = position - paperSize / 2.0;
    float2 normalizedPosition = localPosition / paperSize + 0.5;
    float fineGrain = fract(sin(dot(floor(localPosition * 2.0), float2(12.9898, 78.233))) * 43758.5453);
    float fibreGrain = fract(sin(dot(floor(localPosition * float2(0.5, 3.0)), float2(39.346, 11.135))) * 24634.6345);
    half3 paperColor = half3(0.960, 0.945, 0.915) - half3(0.045, 0.040, 0.034) * half(normalizedPosition.y);
    paperColor += half((fineGrain - 0.5) * 0.026 + (fibreGrain - 0.5) * 0.012);
    float sheen = exp(-pow((normalizedPosition.x * 0.6 + normalizedPosition.y * 0.4 - 0.42) / 0.18, 2.0));
    paperColor += half3(0.03, 0.03, 0.028) * half(sheen);
    float2 edgeDistances = min(normalizedPosition, 1.0 - normalizedPosition) * paperSize;
    float edgeShade = 1.0 - smoothstep(0.0, 6.0, min(edgeDistances.x, edgeDistances.y));
    paperColor -= half3(0.06, 0.055, 0.05) * half(edgeShade);
    paperColor -= half3(0.025, 0.024, 0.022) * half(smoothstep(0.55, 1.0, normalizedPosition.y));
    return half4(paperColor, 1.0) * color.a;
}
