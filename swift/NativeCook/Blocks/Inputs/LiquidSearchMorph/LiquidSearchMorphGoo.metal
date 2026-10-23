#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The goo, in two passes over the pieces drawn in plain white: a Gaussian blur across, then one
// down that also pushes the blurred alpha through alpha × gain − cut (clamped), so shapes that come
// close melt into one with a crisp, antialiased edge in the ink colour. The blur runs here rather
// than through SwiftUI's blur, which works on a downsampled copy and leaves the ramped edge jagged.

static float liquidSearchMorphBlurAlpha(SwiftUI::Layer layer, float2 position, float2 axis, float sigma) {
    int reach = int(ceil(sigma * 3.0));
    float total = 0.0;
    float sum = 0.0;
    for (int i = -reach; i <= reach; i++) {
        float w = exp(-float(i * i) / (2.0 * sigma * sigma));
        sum += float(layer.sample(position + axis * float(i)).a) * w;
        total += w;
    }
    return sum / total;
}

[[ stitchable ]] half4 liquidSearchMorphBlurAcross(float2 position, SwiftUI::Layer layer, float sigma) {
    half a = half(liquidSearchMorphBlurAlpha(layer, position, float2(1.0, 0.0), sigma));
    return half4(a, a, a, a);
}

[[ stitchable ]] half4 liquidSearchMorphGooDown(float2 position, SwiftUI::Layer layer, float sigma, half4 ink, float gain, float cut) {
    float blurred = liquidSearchMorphBlurAlpha(layer, position, float2(0.0, 1.0), sigma);
    half a = half(saturate(blurred * gain - cut)) * ink.a;
    return half4(ink.rgb * a, a);
}
