#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The sleep card's night sky, the same shader as the React Native block's (sky.ts), run as a colour effect.
// Coordinates are design points of the card (370 x 317); the output alpha is the night front, so at rest
// (night = 0) the pin's light card shows untouched. Colours are sRGB (SwiftUI needs no Display P3 conversion).

static float3 nssHex(uint v) {
    return float3(float((v >> 16) & 255u), float((v >> 8) & 255u), float(v & 255u)) / 255.0;
}

static float nssHash(float2 p) {
    float3 q = fract(float3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

static float nssNoise(float2 p) {
    float2 i = floor(p);
    float2 u = fract(p);
    u = u * u * (3.0 - 2.0 * u);
    float a = nssHash(i);
    float b = nssHash(i + float2(1.0, 0.0));
    float c = nssHash(i + float2(0.0, 1.0));
    float d = nssHash(i + float2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

[[stitchable]] half4 nightSleepScoreSky(float2 position, half4 layer, float2 res, float scale, float night,
                                        float phase, float clock, float meteor, float twinkle) {
    float2 xy = position / scale;
    float v = clamp(xy.y / res.y, 0.0, 1.0);
    float a = smoothstep(0.0, 0.45, phase);
    float b = smoothstep(0.55, 0.85, phase);
    float c = smoothstep(0.8, 1.0, phase);
    float3 top = mix(mix(mix(nssHex(0x0A0D2A), nssHex(0x05071A), a), nssHex(0x141B4C), b), nssHex(0x33508F), c);
    float3 bottom = mix(mix(mix(nssHex(0x1A1F52), nssHex(0x11153D), a), nssHex(0x433578), b), nssHex(0xC57A8E), c);
    // Dusk: while the card darkens at the start, the low sky keeps some evening warmth.
    float dusk = (1.0 - night) * (1.0 - smoothstep(0.0, 0.08, phase));
    bottom = mix(bottom, nssHex(0xB68DB4), 0.85 * dusk);
    float3 col = mix(top, bottom, pow(v, 1.35));

    // Faint nebula drifting with the night.
    float neb = nssNoise(xy * 0.018 + float2(phase * 1.6, 0.0)) * nssNoise(xy * 0.041 - float2(0.0, phase * 0.9));
    col += nssHex(0x6B4FC8) * neb * 0.16 * (1.0 - c);

    // Sunrise glow from the lower right, where 06:44 sits on the chart.
    float2 sunAt = float2(res.x * 0.94, res.y * 1.08);
    float sun = exp(-dot(xy - sunAt, xy - sunAt) / (res.x * res.x * 0.42)) * c;
    col = mix(col, nssHex(0xFFB78C), sun * 0.7);

    // Stars: one seeded star per lucky cell, twinkling, fading as dawn comes.
    float cell = 11.0;
    float2 id = floor(xy / cell);
    float stars = 0.0;
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            float2 k = id + float2(float(i), float(j));
            float h = nssHash(k);
            if (h > 0.6) {
                float2 at = (k + 0.15 + 0.7 * float2(nssHash(k + 7.1), nssHash(k + 3.7))) * cell;
                float size = mix(0.35, 1.15, pow(nssHash(k + 1.3), 3.0));
                float lum = mix(0.35, 1.0, nssHash(k + 5.9));
                float tw = mix(0.85, 0.62 + 0.38 * sin(clock * (1.2 + 2.6 * nssHash(k + 9.2)) + 6.2832 * nssHash(k + 2.4)), twinkle);
                float2 d = xy - at;
                float r = length(d);
                float spot = 1.0 - smoothstep(size * 0.2, size + 0.55, r) + 0.28 * exp(-r * r / (size * size * 9.0));
                float flare = step(0.93, nssHash(k + 1.3)) * (exp(-abs(d.x) * 1.1) * exp(-abs(d.y) * 7.0) + exp(-abs(d.y) * 1.1) * exp(-abs(d.x) * 7.0)) * 0.5;
                stars += lum * tw * (spot + flare);
            }
        }
    }
    float starFade = smoothstep(0.35, 0.9, night) * (1.0 - smoothstep(0.62, 0.95, phase)) * (1.0 - 0.55 * smoothstep(0.72, 1.0, v));
    col += float3(0.93, 0.95, 1.0) * stars * starFade;

    // One shooting star.
    if (meteor >= 0.0) {
        float e = 1.0 - (1.0 - meteor) * (1.0 - meteor);
        float2 from = float2(252.0, 16.0);
        float2 to = float2(160.0, 64.0);
        float2 dir = normalize(to - from);
        float2 head = mix(from, to, e);
        float2 rel = xy - head;
        float along = -dot(rel, dir);
        float across = dot(rel, float2(-dir.y, dir.x));
        float tail = clamp(along / 44.0, 0.0, 1.0);
        float streak = step(-0.6, along) * (1.0 - tail) * (1.0 - tail) * exp(-across * across / (0.5 + 1.6 * tail));
        streak += exp(-dot(rel, rel) / 2.2) * 0.8;
        col += float3(1.0, 0.97, 0.92) * streak * sin(3.14159 * meteor) * starFade;
    }

    // Night falls from the top and day comes back from the horizon (the same front as nightAt).
    float fw = 0.6;
    float fx = clamp((night * (1.0 + fw) - v) / fw, 0.0, 1.0);
    float alpha = fx * fx * fx * (fx * (fx * 6.0 - 15.0) + 10.0);
    return half4(half3(clamp(col, 0.0, 1.0) * alpha), half(alpha));
}

// Vertical rays that make the glow above the ribbon read as an aurora curtain. Used as a destination-in mask.
[[stitchable]] half4 nightSleepScoreRays(float2 position, float scale, float clock, float edge) {
    float x = position.x / scale;
    float reveal = 1.0 - smoothstep(edge - 8.0, edge + 1.0, x);
    float sway = sin(x * 0.13 + clock * 0.7) * 2.2;
    float rays = 0.5 + 0.5 * sin(x * 0.95 + sway + clock * 1.1);
    float fine = 0.5 + 0.5 * sin(x * 2.3 - clock * 1.7 + sway * 0.5);
    float a = 0.42 + 0.4 * rays + 0.18 * fine;
    return half4(half(a * reveal));
}
