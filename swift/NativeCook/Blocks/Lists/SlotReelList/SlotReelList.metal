#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

/*
 Slot Reel List — the three reels in one pass over the window (a layerEffect).

 The layer holds the printed strip at its top-left (three columns of `rows` rows, `rowH` pt each).
 Each reel is a cylinder of radius `radius` seen side-on: a pixel `yc` points from the window's
 centre looks at the angle θ = asin(yc / radius), and the strip is read at row
 `offset + θ / rowAngle`, so rows near the window's edges are squeezed exactly as on a drum.
 Light falls off with cos θ, a soft specular band rides above the payline, and a spinning reel is
 blurred along its travel by averaging eight jittered reads spread over the distance it covers in
 ~1/30 s. On the payline row the win glow warms the print and a slanted band of light sweeps
 across all three reels. Positions are in pt, relative to the window's top-left.
*/

static half3 SlotReelListRead(SwiftUI::Layer layer, float c, float lx, float r, float colW, float rowH, float rows) {
    float v = r + 0.5;
    v = v - rows * floor(v / rows);
    return layer.sample(float2(c * colW + lx, v * rowH)).rgb;
}

static float SlotReelListStep(float a, float b, float x) {
    float t = clamp((x - a) / (b - a), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

[[ stitchable ]] half4 SlotReelListDrums(
    float2 p, SwiftUI::Layer layer,
    float2 win, float colW, float gap, float rowH, float radius, float rows,
    float3 off, float3 blur, float sweep, float glow
) {
    if (p.y > win.y + 1.0 || p.x > win.x + 1.0) { return half4(0.0); }
    float pitch = colW + gap;
    float c = floor(p.x / pitch);
    float lx = p.x - c * pitch;
    float yc = p.y - win.y * 0.5;
    float s = clamp(yc / radius, -0.999, 0.999);
    float th = asin(s);
    float ct = cos(th);

    if (c < 0.0 || c > 2.0 || lx > colW) {
        // The dark steel between the drums, lit a little across its middle.
        float3 steel = mix(float3(0.07, 0.04, 0.035), float3(0.2, 0.13, 0.11), ct * ct * ct);
        return half4(half3(steel), 1.0);
    }

    float o = c < 0.5 ? off.x : (c < 1.5 ? off.y : off.z);
    float b = c < 0.5 ? blur.x : (c < 1.5 ? blur.y : blur.z);
    float r = o + th * radius / rowH;
    // Eight reads spread over the blur, each pixel's set shifted by a hash so the copies melt into
    // a smooth streak instead of stepping.
    float3 col = float3(0.0);
    float j = fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
    for (int i = 0; i < 8; i++) {
        col += float3(SlotReelListRead(layer, c, lx, r + b * ((float(i) + j) / 8.0 - 0.5), colW, rowH, rows));
    }
    col *= 0.125;

    // The drum's light: falling off toward its top and bottom, a soft band of gloss just above the
    // payline, and the sides of each drum a shade darker (it is round across, too).
    float across = (lx / colW) * 2.0 - 1.0;
    float shade = (0.16 + 0.84 * pow(ct, 1.6)) * (1.0 - 0.18 * across * across);
    float g = (th + 0.42) / 0.2;
    float spec = exp(-g * g) * 0.16;
    col = col * shade + float3(1.0, 0.96, 0.9) * spec * ct;

    // The win: the payline row warms up and a slanted band of light sweeps across.
    float rowA = rowH / radius;
    float line = 1.0 - SlotReelListStep(rowA * 0.42, rowA * 0.56, abs(th));
    col = mix(col, col * 1.06 + float3(0.32, 0.2, 0.04) * 0.32, glow * line);
    if (sweep > -0.5) {
        float bx = mix(-80.0, win.x + 80.0, sweep);
        float bd = (p.x - bx + yc * 0.5) / 26.0;
        float band = exp(-bd * bd);
        col += float3(1.0, 0.93, 0.78) * band * line * 0.55;
    }
    return half4(half3(col), 1.0);
}
