#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The commit button's shimmer: a soft diagonal beam that sweeps across once per press.
// Line-for-line port of the SkSL in CommitShimmer.tsx. `position` is in the button's own points.
[[ stitchable ]] half4 fighterProfileTourShimmer(float2 position, half4 color, float2 resolution, float sweep, float strength) {
    float2 uv = position / resolution;
    float beam = 1.0 - smoothstep(0.0, 0.11, abs(uv.x + uv.y * 0.13 - sweep));
    float alpha = beam * strength * 0.38;
    return half4(half(alpha), half(alpha * 0.98), half(alpha * 0.80), half(alpha));
}
