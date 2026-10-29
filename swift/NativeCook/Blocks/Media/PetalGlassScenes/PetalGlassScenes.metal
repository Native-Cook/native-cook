#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The whole picture of Petal Glass Scenes in one colour effect, in layout units (393 wide).
//
// The world: two petal scenes, each an image placed in its own drifting rectangle. Scene B grows
// out of the card as a rounded-rect front (the card's shape offset by `front`), a lens bulge
// along it bending the petals and splitting their colours.
//
// The card: a thick glass slab over the world. Inside it the world is magnified 1.06×, warped by a
// slow liquid field, pulled inward by the 20-unit bevel (more at the rim) and split into R, G and
// B samples by an amount that grows towards the rim. Then light: a thin rim line and a sheen on
// the side that faces the light, a darker far side, a faint diagonal band, and a soft shadow
// under the card.

constexpr sampler petalGlassScenesSampler(coord::normalized, address::clamp_to_edge, filter::linear);

static float petalGlassScenesBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

// The card: a rounded rectangle of half size h and corner radius r.
static float petalGlassScenesCard(float2 p, float2 h, float r) {
    return petalGlassScenesBox(p, h - r, r);
}

static float2 petalGlassScenesCardNormal(float2 p, float2 h, float r) {
    float2 q = abs(p) - (h - r);
    float2 s = float2(p.x < 0.0 ? -1.0 : 1.0, p.y < 0.0 ? -1.0 : 1.0);
    if (q.x > 0.0 && q.y > 0.0) return normalize(q) * s;
    return q.x > q.y ? float2(s.x, 0.0) : float2(0.0, s.y);
}

// The front: the card's shape offset by `front` (inside the card it shrinks to its centre line).
static float petalGlassScenesFront(float2 p, float2 h, float r, float front) {
    float2 b = h - r + front;
    float rr = r + min(min(b.x, b.y), 0.0);
    return petalGlassScenesBox(p, max(b, 0.0), rr);
}

static half3 petalGlassScenesImage(texture2d<half> image, float4 rect, float2 q) {
    return image.sample(petalGlassScenesSampler, (q - rect.xy) / rect.zw).rgb;
}

// The world at q: scene A, and scene B inside the front, bent and colour-split along the front.
static float3 petalGlassScenesWorld(
    float2 q, texture2d<half> imageA, float4 rectA, texture2d<half> imageB, float4 rectB,
    float2 c, float2 h, float r, float front, float bulge, float fade
) {
    float2 local = q - c;
    float3 col;
    if (fade >= 0.0) {
        float3 a = float3(petalGlassScenesImage(imageA, rectA, q));
        float3 b = float3(petalGlassScenesImage(imageB, rectB, q));
        return mix(a, b, fade);
    }
    float x = petalGlassScenesFront(local, h, r, front);
    float2 shift = float2(0.0);
    float glow = 0.0;
    if (bulge > 0.001) {
        float2 b = max(h - r + front, 1.0) + r;
        float2 n = normalize(local / (b * b) + 1e-6);
        float g = exp(-(x / 32.0) * (x / 32.0)) * bulge;
        shift = n * (-x / 32.0) * g * 30.0;
        glow = g * exp(-(x / 7.0) * (x / 7.0));
    }
    float m = 1.0 - smoothstep(-1.2, 1.2, x);
    if (length(shift) < 0.02) {
        // Away from the front: one sample per scene.
        float3 a = m < 0.999 ? float3(petalGlassScenesImage(imageA, rectA, q)) : float3(0.0);
        float3 b = m > 0.001 ? float3(petalGlassScenesImage(imageB, rectB, q)) : float3(0.0);
        col = mix(a, b, m);
    } else {
        float2 qr = q + shift * 1.18;
        float2 qg = q + shift;
        float2 qb = q + shift * 0.82;
        float3 a = float3(0.0);
        float3 b = float3(0.0);
        if (m < 0.999) a = float3(petalGlassScenesImage(imageA, rectA, qr).r, petalGlassScenesImage(imageA, rectA, qg).g,
                                  petalGlassScenesImage(imageA, rectA, qb).b);
        if (m > 0.001) b = float3(petalGlassScenesImage(imageB, rectB, qr).r, petalGlassScenesImage(imageB, rectB, qg).g,
                                  petalGlassScenesImage(imageB, rectB, qb).b);
        col = mix(a, b, m);
    }
    return col + glow * 0.30;
}

[[ stitchable ]] half4 petalGlassScenesRender(
    float2 position,
    half4 color,
    float k,
    float2 size, // units
    texture2d<half> imageA,
    float4 rectA,
    texture2d<half> imageB,
    float4 rectB,
    float4 card, // centre x, y, half width, half height (units)
    float4 change, // front, bulge, warp amplitude, fade (negative: front)
    float4 light, // theta, drag phase, light x, light y
    float4 extra // corner radius, sheen position, glass on (1) / flat (0), unused
) {
    float2 q = position / k;
    float2 c = card.xy;
    float2 h = card.zw;
    float r = extra.x;
    float front = change.x;
    float bulge = change.y;
    float fade = change.w;

    float2 local = q - c;
    float d = petalGlassScenesCard(local, h, r);

    float3 col;
    if (d > 0.0) {
        col = petalGlassScenesWorld(q, imageA, rectA, imageB, rectB, c, h, r, front, bulge, fade);
        // Shadows: a tight contact one and a soft ambient one, both below the card.
        float contact = petalGlassScenesCard(local - float2(0.0, 3.0), h, r);
        float ambient = petalGlassScenesCard(local - float2(0.0, 18.0), h, r);
        float shade = 0.14 * (1.0 - smoothstep(-2.0, 7.0, contact)) + 0.16 * (1.0 - smoothstep(-12.0, 34.0, ambient));
        col *= 1.0 - shade;
    }
    if (d < 1.0) {
        float theta = light.x;
        float dp = light.y;
        float2 L = normalize(light.zw);
        float edge = clamp(1.0 + d / 20.0, 0.0, 1.0); // 0 deep inside → 1 at the rim
        float2 n = petalGlassScenesCardNormal(local, h, r);
        float bend = pow(edge, 2.5) * 30.0 * extra.z;
        float amp = change.z * extra.z;
        float2 warp = amp * float2(
            sin(local.y * 0.034 + theta + dp + 1.3 * sin(local.x * 0.021 + 2.0 * theta)),
            cos(local.x * 0.030 - theta + dp * 0.7 + 1.1 * sin(local.y * 0.026 - theta))
        );
        float2 disp = -local * (1.0 - 1.0 / 1.06) * extra.z + warp * (1.0 - 0.6 * edge) - n * bend;
        float ch = 0.08 + 0.12 * edge;
        float3 cr = petalGlassScenesWorld(q + disp * (1.0 + ch), imageA, rectA, imageB, rectB, c, h, r, front, bulge, fade);
        float3 cg = petalGlassScenesWorld(q + disp, imageA, rectA, imageB, rectB, c, h, r, front, bulge, fade);
        float3 cb = petalGlassScenesWorld(q + disp * (1.0 - ch), imageA, rectA, imageB, rectB, c, h, r, front, bulge, fade);
        float3 glass = float3(cr.r, cg.g, cb.b);

        float facing = dot(n, L);
        float rim = 1.0 - smoothstep(0.0, 1.8, -d);
        glass += rim * (0.10 + 0.50 * max(facing, 0.0) + 0.14 * max(-facing, 0.0));
        float e3 = edge * edge * edge;
        glass += e3 * 0.16 * max(facing, 0.0);
        glass *= 1.0 - e3 * 0.10 * max(-facing, 0.0);
        glass = mix(glass, float3(1.0), 0.045);
        float band = dot(local, normalize(float2(0.82, -0.57))) - extra.y;
        glass += 0.07 * exp(-band * band / (38.0 * 38.0));

        float a = clamp(0.5 - d * k, 0.0, 1.0);
        col = d > 0.0 ? mix(col, glass, a) : glass;
    }

    // Scrims so the type at the top and bottom reads on the palest petals.
    col *= 1.0 - 0.12 * (1.0 - smoothstep(0.0, 150.0, q.y));
    col *= 1.0 - 0.26 * smoothstep(size.y - 260.0, size.y, q.y);
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
