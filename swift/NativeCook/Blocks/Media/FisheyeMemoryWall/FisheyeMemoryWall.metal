#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Fisheye Memory Wall: a barrel lens over a repeating wall of tiles. Every screen point is traced back
// to the wall (wall = pan + (p − c) · f(d), d = the point's radius in the lens ellipse) and the
// pre-rendered atlas of one wall period is sampled with repeat addressing.

constexpr sampler FisheyeMemoryWallSampler(coord::normalized, address::repeat, filter::linear);

/// Wall points per screen point along a ray at normalised radius d; k is the bulge strength.
static float FisheyeMemoryWallF(float d, float k) {
    float d2 = d * d;
    return 1.0 / (1.0 + 0.6 * k) + k * (0.217 * d2 / 3.0 + 2.284 * d2 * d2 * d2 / 7.0);
}

/// The radial derivative of the mapping: how much wall one screen point covers along the ray.
static float FisheyeMemoryWallRadial(float d, float k) {
    float d2 = d * d;
    return 1.0 / (1.0 + 0.6 * k) + k * (0.217 * d2 + 2.284 * d2 * d2 * d2);
}

static half3 FisheyeMemoryWallTap(float2 p, float4 lens, float4 view, float4 wall, texture2d<half> atlas) {
    float2 q = (p - lens.xy) / lens.zw;
    float d = min(length(q), 1.0);
    float2 w = view.xy + (p - lens.xy) * FisheyeMemoryWallF(d, view.z);
    return atlas.sample(FisheyeMemoryWallSampler, w / wall.xy).rgb;
}

/// lens: centre x, y and radii; view: wall point under the centre (x, y), bulge k, atlas texels per
/// screen pixel; wall: period w, h, one screen pixel in points, unused; focus: the focused tile's
/// rect in the period and (corner radius, ring opacity).
[[ stitchable ]] half4 FisheyeMemoryWallLens(float2 position, texture2d<half> atlas, float4 lens, float4 view,
                                              float4 wall, float4 focus, float2 ring, half4 background) {
    float2 q = (position - lens.xy) / lens.zw;
    float d = length(q);
    float minify = FisheyeMemoryWallRadial(min(d, 1.0), view.z) * view.w;
    half3 color;
    if (minify > 1.4) {
        // Four taps inside the pixel where the wall is squeezed, so the rim does not shimmer.
        float o = wall.z * 0.25;
        color = (FisheyeMemoryWallTap(position + float2(-o, -o), lens, view, wall, atlas)
               + FisheyeMemoryWallTap(position + float2(o, -o), lens, view, wall, atlas)
               + FisheyeMemoryWallTap(position + float2(-o, o), lens, view, wall, atlas)
               + FisheyeMemoryWallTap(position + float2(o, o), lens, view, wall, atlas)) * 0.25h;
    } else {
        color = FisheyeMemoryWallTap(position, lens, view, wall, atlas);
    }
    if (ring.y > 0.001) {
        // A white ring hugs the focused tile, bent by the lens like the tile itself.
        float dc = min(d, 1.0);
        float f = FisheyeMemoryWallF(dc, view.z);
        float2 w = view.xy + (position - lens.xy) * f;
        w = w - floor(w / wall.xy) * wall.xy;
        float2 half_ = focus.zw * 0.5;
        float2 qb = abs(w - (focus.xy + half_)) - half_ + ring.x;
        float sd = length(max(qb, 0.0)) + min(max(qb.x, qb.y), 0.0) - ring.x;
        float px = wall.z * FisheyeMemoryWallRadial(dc, view.z);
        float line = 1.0 - smoothstep(0.9, 0.9 + px, abs(sd - 2.2));
        float glow = exp(-max(sd - 2.2, 0.0) * 0.35) * step(0.0, sd) * 0.18;
        color = mix(color, half3(1.0h), half(ring.y * min(1.0, line * 0.92 + glow)));
    }
    // The sphere darkens towards its rim and catches a soft light at the upper left.
    float vignette = 1.0 - 0.82 * smoothstep(0.7, 1.0, d);
    color *= half(vignette);
    float2 sheen = (position - float2(lens.x - 0.36 * lens.z, lens.y - 0.42 * lens.w)) / (0.9 * lens.z);
    color += half3(0.07 * exp(-dot(sheen, sheen) * 2.2) * (1.0 - smoothstep(0.8, 1.0, d)));
    float outside = smoothstep(0.985, 1.02, d);
    color = mix(color, background.rgb, half(outside));
    return half4(color, 1.0h);
}
