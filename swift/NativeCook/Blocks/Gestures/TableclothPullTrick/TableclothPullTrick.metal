#include <metal_stdlib>
using namespace metal;

// One point of the cloth's mesh, as TableclothPullTrickScene.vertices writes it: where it is on
// screen (pt), where it is in the baked linen (0 … 1), and the shade laid over the linen there.
struct TableclothPullTrickVertex {
    float2 position;
    float2 linen;
    float4 shade;
};

struct TableclothPullTrickVaried {
    float4 position [[position]];
    float2 linen;
    float4 shade;
};

vertex TableclothPullTrickVaried tableclothPullTrickVertex(uint id [[vertex_id]],
                                                           device const TableclothPullTrickVertex *points [[buffer(0)]],
                                                           constant float2 &viewSize [[buffer(1)]]) {
    TableclothPullTrickVertex p = points[id];
    TableclothPullTrickVaried out;
    float2 ndc = p.position / viewSize * 2.0 - 1.0;
    out.position = float4(ndc.x, -ndc.y, 0.0, 1.0);
    out.linen = p.linen;
    out.shade = p.shade;
    return out;
}

// The linen at `alpha`, then the fold's shade over it (as the React Native version draws the
// textured mesh and then a colour-only mesh over it), premultiplied for source-over blending.
fragment float4 tableclothPullTrickCloth(TableclothPullTrickVaried in [[stage_in]],
                                         texture2d<float> linen [[texture(0)]],
                                         constant float &alpha [[buffer(0)]]) {
    constexpr sampler s(address::clamp_to_edge, filter::linear, mip_filter::linear);
    float3 tex = linen.sample(s, in.linen).rgb;
    float sa = clamp(in.shade.a, 0.0, 1.0);
    float3 rgb = tex * alpha * (1.0 - sa) + in.shade.rgb * sa;
    float a = alpha + sa - alpha * sa;
    return float4(rgb, a);
}
