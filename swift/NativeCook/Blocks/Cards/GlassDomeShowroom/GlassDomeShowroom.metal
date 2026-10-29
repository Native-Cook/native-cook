#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Glass Dome Showroom: a directional motion smear. Averages 13 samples along `reach`
// (half the smear, in points) so a fast slide reads as a streak instead of a strobe.
[[ stitchable ]] half4 glassDomeShowroomSmear(float2 position, SwiftUI::Layer layer, float2 reach) {
    if (length(reach) < 0.35) {
        return layer.sample(position);
    }
    half4 sum = half4(0.0);
    half weight = 0.0;
    for (int i = 0; i < 13; i++) {
        float t = float(i) / 6.0 - 1.0;
        half w = half(1.0 - 0.55 * abs(t));
        sum += layer.sample(position + reach * t) * w;
        weight += w;
    }
    return sum / weight;
}
