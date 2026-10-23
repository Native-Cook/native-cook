#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The ink over warm paper, the same shader as the React Native block's, run as a layer effect.
// The layer holds the photo already printed, cropped to fill the print; the photo the ink is
// bringing in comes as a texture with the rectangle it fills.

static float inkBleedRevealHash(float2 p) {
    float3 q = fract(float3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

static float inkBleedRevealNoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(inkBleedRevealHash(i), inkBleedRevealHash(i + float2(1.0, 0.0)), u.x),
        mix(inkBleedRevealHash(i + float2(0.0, 1.0)), inkBleedRevealHash(i + float2(1.0, 1.0)), u.x),
        u.y
    );
}

static float inkBleedRevealFbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) {
        v += a * inkBleedRevealNoise(p);
        p = p * 2.07 + 17.3;
        a *= 0.5;
    }
    return v / 0.875;
}

// Warm paper: fine grain, a few long fibres and a soft cloudiness, darker towards the corners.
static float3 inkBleedRevealPaper(float2 p, float2 size) {
    float grain = inkBleedRevealNoise(p * 1.7) - 0.5;
    float fibre = inkBleedRevealNoise(float2(p.x * 0.05 + p.y * 0.02, p.y * 0.9)) - 0.5;
    float cloud = inkBleedRevealNoise(p * 0.012) - 0.5;
    float3 c = float3(0.953, 0.937, 0.906);
    c += grain * 0.03 + fibre * 0.02 + cloud * 0.03;
    float2 q = p / size - 0.5;
    c *= 1.0 - dot(q, q) * 0.16;
    return c;
}

static float inkBleedRevealBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

constant float3 inkBleedRevealInkColour = float3(0.086, 0.102, 0.137);

// The photo as pigment on this paper: a little less saturated, multiplied over the grain, with a
// thin darker line at the print's edge where the ink pooled as it dried.
static float3 inkBleedRevealPrinted(float3 c, float3 sheet, float edge) {
    float l = dot(c, float3(0.299, 0.587, 0.114));
    c = mix(float3(l), c, 0.86) * 0.95 + 0.025;
    c *= sheet * 1.05;
    float e = (edge + 2.0) / 2.6;
    return mix(c, inkBleedRevealInkColour * 0.9 + c * 0.25, 0.38 * exp(-e * e));
}

// `base` > 0 when a photo is printed, `top` > 0 while the ink brings in the next one. The front is
// a circle of radius `front` around `drop`, its edge pushed in and out by noise sampled around the
// drop (lobes), by the paper's blotches and by a fine fringe; behind it the paper is dark with wet
// ink for 40 pt before the photo comes through. Ink only takes inside the ragged print.
[[ stitchable ]] half4 inkBleedRevealInk(
    float2 p,
    SwiftUI::Layer layer,
    float2 size,
    float4 print,
    texture2d<half> incoming,
    float4 incomingRect,
    float base,
    float top,
    float2 drop,
    float front,
    float seed,
    float dry,
    float blend
) {
    float3 sheet = inkBleedRevealPaper(p, size);
    float3 col = sheet;

    float2 centre = print.xy + print.zw * 0.5;
    float edge = inkBleedRevealBox(p - centre, print.zw * 0.5 - 12.0, 34.0);
    edge += (inkBleedRevealFbm(p * 0.008 + 3.1) - 0.5) * 38.0;
    edge += (inkBleedRevealFbm(p * 0.05 + 7.7) - 0.5) * 7.0;
    float feather = (inkBleedRevealNoise(p * 0.32) - 0.5) * 2.6;
    float inside = 1.0 - smoothstep(-1.0, 1.0, edge + feather);

    if (base > 0.5) {
        // Drying back to paper goes in blotches, not all at once.
        float blot = inkBleedRevealFbm(p * 0.008 + 11.0);
        float keep = smoothstep(blot - 0.03, blot + 0.03, dry * 1.3 - 0.15) * smoothstep(0.0, 0.45, dry);
        col = mix(col, inkBleedRevealPrinted(float3(layer.sample(p).rgb), sheet, edge), inside * keep);
    }

    float2 v = p - drop;
    float r = length(v);
    // Past the farthest the ragged front can reach, the new ink has not arrived: skip its noise.
    if (top > 0.5 && r < front * 1.18 + 34.0) {
        float2 dir = v / max(r, 0.001);
        float grow = smoothstep(0.0, 120.0, front);
        // Big soft lobes around the drop, blotches from the paper's own absorbency, and a fine
        // feathery fringe.
        float lobes = (inkBleedRevealFbm(dir * 1.5 + seed) - 0.5) * (front * 0.16 * 2.0 + 4.0);
        float blotch = (inkBleedRevealFbm(p * 0.022 + seed * 1.7) - 0.5) * 46.0 * grow;
        float fringe = (inkBleedRevealFbm(p * 0.21 + seed) - 0.5) * 9.0 * grow;
        float d = r + lobes + blotch + fringe - front;
        float wet = (1.0 - smoothstep(-0.9, 0.9, d)) * inside;
        // Behind the front the ink dries unevenly, letting the photo through in a mottle.
        float mottle = (inkBleedRevealFbm(p * 0.03 + seed * 2.3) - 0.5) * 24.0;
        float resolve = smoothstep(0.0, 40.0, -d + mottle);
        float k = (d + 2.5) / 3.2;
        float ring = exp(-k * k);
        float3 wash = mix(col, inkBleedRevealInkColour, 0.86);
        // Outside the photo the texture reads as clear, like a decal image shader.
        constexpr sampler linear(coord::normalized, address::clamp_to_zero, filter::linear);
        float3 photo = float3(incoming.sample(linear, (p - incomingRect.xy) / incomingRect.zw).rgb);
        float3 layerColour = mix(wash, inkBleedRevealPrinted(photo, sheet, edge), resolve);
        layerColour = mix(layerColour, inkBleedRevealInkColour * 0.55, ring * 0.8);
        col = mix(col, layerColour, wet * blend);
    }
    return half4(half3(col), 1.0);
}
