#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The intermediate contains encoded P3 components. The view itself is sRGB.
[[ stitchable ]] half4 glassPlaylistFolderDisplay(float2 position, half4 color) {
    float a = float(color.a);
    float3 v = float3(color.rgb) / max(a, 0.00001);
    float3 linear = select(pow((v + 0.055) / 1.055, float3(2.4)), v / 12.92, v <= 0.04045);
    float3 rgb = float3(
        1.224745 * linear.r - 0.224904 * linear.g,
        -0.042058 * linear.r + 1.042081 * linear.g,
        -0.019642 * linear.r - 0.078655 * linear.g + 1.098537 * linear.b);
    rgb = max(rgb, 0.0);
    rgb = select(1.055 * pow(rgb, float3(1.0 / 2.4)) - 0.055, rgb * 12.92, rgb <= 0.0031308);
    return half4(half3(rgb * a), half(a));
}

// Separable Gaussian in encoded P3, with paired bilinear taps.
constant float2 glassPlaylistFolderFrostTaps[] = {
    float2(1.498086744f, 0.056765785f),
    float2(3.495535833f, 0.055337794f),
    float2(5.492985154f, 0.052857325f),
    float2(7.490434841f, 0.049469404f),
    float2(9.487885025f, 0.045364519f),
    float2(11.485335839f, 0.040760931f),
    float2(13.482787417f, 0.035885585f),
    float2(15.480239889f, 0.030955947f),
    float2(17.477693388f, 0.026164732f),
    float2(19.475148047f, 0.021668888f),
    float2(21.472603996f, 0.017583490f),
    float2(23.470061367f, 0.013980466f),
    float2(25.467520291f, 0.010891466f),
    float2(27.464980899f, 0.008313792f),
    float2(29.462443321f, 0.006218133f),
    float2(31.459907687f, 0.004556895f),
    float2(33.457374127f, 0.003272096f),
    float2(35.454842770f, 0.002302137f),
    float2(37.452313745f, 0.001587027f),
    float2(39.449787180f, 0.001071977f),
    float2(41.447263202f, 0.000709471f),
};

[[ stitchable ]] half4 glassPlaylistFolderFrost(float2 position, SwiftUI::Layer layer, float2 axis) {
    bool horizontal = axis.x > 0.5;
    // The second pass is clipped to the flap. Only its vertical apron needs
    // the first pass; keep the rest of the full-screen backdrop inexpensive.
    if (position.x < 64.0 || position.x > 336.0 ||
        position.y < (horizontal ? 118.0 : 160.0) ||
        position.y > (horizontal ? 346.0 : 304.0)) {
        return layer.sample(position);
    }
    float4 result = float4(layer.sample(position)) * 0.028564275f;
    for (int i = 0; i < 21; ++i) {
        float2 tap = glassPlaylistFolderFrostTaps[i];
        float2 offset = axis * tap.x;
        result += (float4(layer.sample(position + offset)) + float4(layer.sample(position - offset))) * tap.y;
    }
    return half4(result);
}
