#include <metal_stdlib>
using namespace metal;

// One point of the cube's meshes, as JellyCubeSquishArt.build writes it: where it is on screen (pt)
// and its colour (not premultiplied), lit on the CPU per vertex as the React Native version does.
struct JellyCubeSquishVertex {
    float2 position;
    float2 unused;
    float4 colour;
};

struct JellyCubeSquishVaried {
    float4 position [[position]];
    float4 colour;
};

vertex JellyCubeSquishVaried jellyCubeSquishVertex(uint id [[vertex_id]],
                                                   device const JellyCubeSquishVertex *points [[buffer(0)]],
                                                   constant float2 &viewSize [[buffer(1)]]) {
    JellyCubeSquishVertex p = points[id];
    JellyCubeSquishVaried out;
    float2 ndc = p.position / viewSize * 2.0 - 1.0;
    out.position = float4(ndc.x, -ndc.y, 0.0, 1.0);
    out.colour = p.colour;
    return out;
}

// Premultiplied, for source-over (and for the additive gloss, whose pipeline adds it).
fragment float4 jellyCubeSquishFragment(JellyCubeSquishVaried in [[stage_in]]) {
    float a = clamp(in.colour.a, 0.0, 1.0);
    return float4(clamp(in.colour.rgb, 0.0, 1.0) * a, a);
}
