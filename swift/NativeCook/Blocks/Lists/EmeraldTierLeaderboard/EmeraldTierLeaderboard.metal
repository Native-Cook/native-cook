#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Emerald Tier Leaderboard — line-by-line Metal ports of the React Native SkSL:
//   emeraldLightGlass  <- EmeraldLightSurface.tsx (EMERALD_LIGHT_GLASS)
//   emeraldArt         <- EmeraldMaterials.tsx (ART)
//   emeraldRecast      <- recastOptics.ts (RECAST_SKSL), always with the Skia-optics flag on
// SkSL `main(float2 p)` is in canvas points, which is what SwiftUI hands a colour effect as
// `position`. SwiftUI colours are sRGB and the SkSL did no colour-space conversion, so the maths
// is copied unchanged. A local called `distance` is renamed `dist` so it cannot hide metal::distance.

namespace emeraldTierLeaderboardShader {

inline float pool(float2 p) { return exp(-dot(p, p)); }

inline float roundedDistance(float2 p, float4 rect, float radius) {
    float2 q = abs(p - rect.xy - rect.zw * .5) - (rect.zw * .5 - radius);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

inline float topMask(float2 p, float4 rect, float depth) {
    if (depth < .01) return 0.0;
    float level = clamp((rect.y - p.y) / max(depth, .01), 0.0, 1.0);
    float inset = rect.z * (14.0 / 123.0) * level;
    return smoothstep(rect.x + inset - .55, rect.x + inset + .55, p.x)
        * (1.0 - smoothstep(rect.x + rect.z - inset - .55, rect.x + rect.z - inset + .55, p.x))
        * smoothstep(rect.y - depth - .55, rect.y - depth + .55, p.y)
        * (1.0 - smoothstep(rect.y - .55, rect.y + .55, p.y));
}

inline float4 over(float4 front, float4 back) { return front + back * (1.0 - front.a); }

inline float3 backdrop(float2 p) {
    // Known procedural emerald page field, never sampled from a native view or a photo.
    float y = clamp(p.y / 335.0, 0.0, 1.0);
    float3 c = mix(float3(.024, .294, .216), float3(.031, .047, .035), smoothstep(0.0, 1.0, y));
    return c * (.93 + .07 * cos(p.x * .012));
}

inline float4 opticalBody(float2 p, float4 rect, float4 mat, float4 motion) {
    if (rect.z <= 0.0 || rect.w <= 0.0) return float4(0.0);
    if (p.x < rect.x - 1.0 || p.x > rect.x + rect.z + 1.0 || p.y < rect.y - mat.y - 1.0 || p.y > rect.y + rect.w + 1.0) return float4(0.0);
    float dist = roundedDistance(p, rect, mat.x);
    float front = 1.0 - smoothstep(-.55, .55, dist);
    float top = topMask(p, rect, mat.y);
    float coverage = max(front, top);
    if (coverage <= 0.0) return float4(0.0);
    float2 uv = (p - rect.xy) / max(rect.zw, float2(.01));
    float edge = 1.0 - smoothstep(1.0, 8.0, max(0.0, -dist));
    float2 radial = (uv - .5) * float2(1.0, .55);
    float2 normal = radial / max(length(radial), .001);
    float2 refracted = p + normal * (2.0 + mat.y * .22) * edge;
    float3 sampleColor = backdrop(refracted);
    float band = uv.x + uv.y * .28 - mix(-.35, 1.55, motion.z);
    float sweep = exp(-band * band / .014) * motion.y;
    float wave = pow(.5 + .5 * sin(uv.x * 10.0 + uv.y * 5.0 + motion.x * 6.2831853), 5.0);
    float sway = sin(motion.x * 3.14159265);
    float movement = sway * sway * (1.0 - motion.w);
    float caustic = wave * smoothstep(.58, 1.0, uv.y) * (.035 * movement + .08 * motion.y);
    float alpha = (edge * .065 + sweep * .20 + caustic + top * (.025 + .06 * motion.y)) * coverage;
    float3 tint = mix(sampleColor, float3(.72, .94, .84), clamp(sweep * 2.0 + caustic * 2.0 + top * .35, 0.0, 1.0));
    return float4(tint * alpha, alpha);
}

inline float4 body(float2 p, float4 rect, float4 mat, float4 motion, float2 flags) {
    if (rect.z <= 0.0 || rect.w <= 0.0) return float4(0.0);
    if (p.x < rect.x - 24.0 || p.x > rect.x + rect.z + 24.0 || p.y < rect.y - mat.y - 12.0 || p.y > rect.y + rect.w + 28.0) return float4(0.0);
    float dist = roundedDistance(p, rect, mat.x);
    float front = 1.0 - smoothstep(-.55, .55, dist);
    float top = topMask(p, rect, mat.y);
    float moving = sin(motion.x * 3.14159265) * (1.0 - motion.w);
    float shadowDistance = roundedDistance(p - float2(0.0, 3.0 + moving * 3.0), rect, mat.x);
    float shadow = exp(-pow(max(0.0, shadowDistance) / (5.0 + moving * 5.0), 2.0)) * (.24 - moving * .06);
    float4 result = float4(float3(.003, .012, .008) * shadow, shadow);
    float2 uv = (p - rect.xy) / max(rect.zw, float2(.01));
    float reservoir = smoothstep(.43, 1.0, uv.y);
    float2 rightPoint = (uv - float2(.90, 1.06)) / float2(.50, .38);
    float2 leftPoint = (uv - float2(.13, 1.08)) / float2(.62, .36);
    float3 color = float3(.022, .043, .041) + float3(.010, .013, .012) * (1.0 - clamp(uv.y, 0.0, 1.0));
    float energy = flags.x > .5 ? .045 : .14 + .30 * mat.z;
    color += reservoir * energy * (float3(.20, .70, .45) * exp(-dot(rightPoint, rightPoint)) + float3(.045, .21, .19) * exp(-dot(leftPoint, leftPoint)));
    float grain = fract(sin(dot(floor((p - rect.xy) * 1.7), float2(12.9898, 78.233))) * 43758.5453) - .5;
    color += grain * .007;
    float rim = 1.0 - smoothstep(.10, 1.15, abs(dist));
    float rimPower = .075 + .07 * (1.0 - clamp(uv.x, 0.0, 1.0)) + smoothstep(.65, 1.0, uv.y) * (.08 + .16 * mat.z);
    color += float3(.55, .84, .72) * rim * rimPower;
    float alpha = front * (flags.x > .5 ? 1.0 : .985);
    float topLevel = clamp((rect.y - p.y) / max(mat.y, .01), 0.0, 1.0);
    float3 topColor = mix(float3(.032, .081, .069), float3(.091, .183, .15), topLevel);
    float4 face = over(float4(clamp(color, 0.0, 1.0) * alpha, alpha), float4(topColor * top, top));
    result = over(face, result);
    if (flags.x < .5 && flags.y > 0.0) result = over(opticalBody(p, rect, mat, motion) * flags.y, result);
    return result;
}

} // namespace emeraldTierLeaderboardShader

/// Inky upper body, a contained lower light reservoir, and a thin refractive edge.
[[ stitchable ]] half4 emeraldLightGlass(float2 p, half4 inColor, float2 size, float radius, float light, float response, float phase) {
    using namespace emeraldTierLeaderboardShader;
    float2 uv = p / size;
    float r = min(radius, min(size.x, size.y) * .5);
    float2 q = abs(p - size * .5) - (size * .5 - r);
    float edgeDistance = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
    float coverage = 1.0 - smoothstep(-.65, .65, edgeDistance);

    // Light diffuses upward from below the lower lip; the upper body stays dark.
    float reservoir = smoothstep(.38, .90, uv.y);
    float rightPool = pool((uv - float2(.90, 1.06)) / float2(.43, .35));
    float leftPool = pool((uv - float2(.16, 1.10)) / float2(.55, .32));
    float depth = pool((uv - float2(.12, -.24)) / float2(.85, .90));
    float3 color = float3(.018, .035, .037) + float3(.012, .019, .025) * depth;
    float energy = light * (1.0 + response * .20);
    color += reservoir * energy * (float3(.075, .40, .42) * leftPool + float3(.42, .83, .63) * rightPool);

    // Fine monochrome grain stays static instead of crawling with the frame clock.
    float grain = fract(sin(dot(floor(p * 1.7), float2(12.9898, 78.233))) * 43758.5453) - .5;
    color += grain * .012 * (.45 + .55 * reservoir);

    float rim = 1.0 - smoothstep(.10, 1.0, max(0.0, -edgeDistance));
    float lower = smoothstep(.60, 1.0, uv.y);
    float rimPower = .075 + .10 * (1.0 - uv.x) + lower * (.11 + .46 * rightPool) * light;
    color += float3(.64, .91, .85) * rim * rimPower;

    // One reflection answers the existing crown contact; no independent VFX clock.
    float band = uv.x + uv.y * .25 - mix(-.30, 1.55, phase);
    float reflection = exp(-band * band / .008);
    color += float3(.18, .32, .23) * reflection * response * light * (.25 + .75 * reservoir);
    float alpha = coverage * .985;
    return half4(half3(clamp(color, 0.0, 1.0) * alpha), half(alpha));
}

/// The artwork with one gold glint that crosses it when the rank lands.
[[ stitchable ]] half4 emeraldArt(float2 p, SwiftUI::Layer layer, float2 size, float phase, float power) {
    half4 s = layer.sample(p);
    float a = s.a;
    float3 base = float3(s.rgb) / max(a, 0.001);
    float2 uv = p / size;
    float line = uv.x + uv.y * .32 - mix(-.3, 1.5, phase);
    float shine = exp(-pow(line / .055, 2.0));
    float facets = smoothstep(.18, .7, max(base.r, max(base.g, base.b)));
    float3 c = base + float3(.70, .61, .30) * shine * power * (.32 + .68 * facets);
    return half4(half3(clamp(c, 0.0, 1.0) * a), half(a));
}

/// Four glass bodies in one pass (393 x 335 arena). flags = (solid, skia optics on).
[[ stitchable ]] half4 emeraldRecast(float2 p, half4 inColor, float4 motion, float2 flags,
                                     float4 rect0, float4 rect1, float4 rect2, float4 rect3,
                                     float4 material0, float4 material1, float4 material2, float4 material3) {
    using namespace emeraldTierLeaderboardShader;
    float4 result = float4(0.0);
    result = over(body(p, rect0, material0, motion, flags), result);
    result = over(body(p, rect1, material1, motion, flags), result);
    result = over(body(p, rect2, material2, motion, flags), result);
    result = over(body(p, rect3, material3, motion, flags), result);
    return half4(result);
}
