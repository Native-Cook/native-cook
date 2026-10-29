#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The whole screen behind the copy. The layer is a mask: black, with the cover line's words drawn
// white and blurred by 1.6 pt. Each pixel is first carried back through the swirl (rotated about
// `centre` by an angle that grows towards it), then the words are drawn there as horizontal stripes
// whose thickness follows the blurred mask, so every stripe tapers to a rounded end at a letter
// edge. Where the swirl twists hard the stripes break into capsules coloured from the palette.
// Everything is in points. The same function is written in AGSL (Kotlin) and SkSL (React Native).

static float stripeSwirlTypeHash1(float n) { return fract(sin(n * 12.9898) * 43758.5453); }
static float stripeSwirlTypeHash2(float2 v) { return fract(sin(dot(v, float2(127.1, 311.7))) * 43758.5453); }

[[ stitchable ]] half4 stripeSwirlTypeField(
    float2 p, SwiftUI::Layer layer,
    float2 size, float2 centre, float twist, float radius, float spin,
    half4 inkColor, half4 backColor,
    half4 c0, half4 c1, half4 c2, half4 c3, half4 c4
) {
    // The swirl: angle = twist * s^2 + spin * s, s = 1 at the centre, 0 at `radius`. q is where the
    // pixel comes from; gy and gx are how fast q.y and q.x change across the screen.
    float2 d = p - centre;
    float r = length(d);
    float R = max(radius, 1.0);
    float s = clamp(1.0 - r / R, 0.0, 1.0);
    float theta = twist * s * s + spin * s;
    float slope = r < R ? -(2.0 * twist * s + spin) / R : 0.0;
    float2 rhat = r > 0.001 ? d / r : float2(0.0);
    float cs = cos(theta);
    float sn = sin(theta);
    float2 q = centre + float2(cs * d.x + sn * d.y, -sn * d.x + cs * d.y);
    float2 gy = float2(-sn, cs) - (cs * d.x + sn * d.y) * slope * rhat;
    float2 gx = float2(cs, sn) + (-sn * d.x + cs * d.y) * slope * rhat;
    float g2 = max(dot(gy, gy), 1e-4);
    float g = sqrt(g2);
    float gxl = max(length(gx), 1e-3);

    // Backdrop: warm near-black, a vignette and a static grain.
    float2 uv = p / size - 0.5;
    float vig = 1.0 - 0.55 * smoothstep(0.25, 0.85, length(uv * float2(size.x / size.y, 1.0)) * 1.25);
    float grain = (stripeSwirlTypeHash2(floor(p * 2.0)) - 0.5) * 0.05;
    float3 col = float3(backColor.rgb) * vig + grain * 0.35;

    // Stripes are strokes: period 6 pt in the words, but 1.7 pt half thick on screen however much
    // the swirl squeezes them, so where it packs them tight they overlap like drawn lines (the lower
    // stripe on top). Each is thinner towards the letter edges (the blurred mask), and where the
    // swirl twists hard it breaks into capsules of 22-48 pt, coloured from the palette.
    float period = 6.0;
    float aa = 0.45;
    float colour = smoothstep(0.5, 1.8, abs(theta));
    // Where the swirl packs the stripes (g > 1.2) they break into pieces too, still off-white.
    float broken = max(colour, smoothstep(1.2, 2.0, g));
    float base = floor(q.y / period);
    for (int k = -2; k <= 2; k++) {
        float row = base + float(k);
        float yk = (row + 0.5) * period;
        float dyk = q.y - yk;
        float dist = abs(dyk) / g;
        if (dist > 1.7 + aa) { continue; }
        // The nearest point of this stripe, back in the words.
        float2 step = -dyk * gy / g2;
        float sx = q.x + dot(gx, step);
        float m = float(layer.sample(float2(sx, yk)).r);
        float halfT = 1.7 * smoothstep(0.22, 0.85, m);
        if (halfT < 0.3) { continue; }

        float hr = stripeSwirlTypeHash1(row + 3.1);
        float len = 22.0 + 26.0 * hr;
        float xs = sx + hr * 97.0;
        float piece = floor(xs / len);
        float xm = (piece + 0.5) * len;
        float ex = max(abs(xs - xm) / gxl - ((0.5 * len - 1.2) / gxl - halfT), 0.0) * broken;
        float e = length(float2(ex, dist)) - halfT;
        float cover = 1.0 - smoothstep(-aa, aa, e);

        float pick = stripeSwirlTypeHash2(float2(piece, row));
        float3 tint = float3(c0.rgb);
        if (pick > 0.18) { tint = float3(c1.rgb); }
        if (pick > 0.36) { tint = float3(c2.rgb); }
        if (pick > 0.54) { tint = float3(c3.rgb); }
        if (pick > 0.72) { tint = float3(c4.rgb); }
        if (pick > 0.9) { tint = float3(inkColor.rgb); }
        col = mix(col, mix(float3(inkColor.rgb), tint, colour), cover);
    }
    return half4(half3(col), 1.0);
}
