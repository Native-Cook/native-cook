#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The grain grid's size, in cells: must match SandHourglassSand.
constant int sandHourglassCols = 77;
constant int sandHourglassRows = 151;

// The grain shader. It reads one RGBA texel per cell (the grid, passed as bytes) and draws each
// grain as a small lit bead that overlaps its neighbours a little, so a pile reads as packed sand
// and the stream as loose grains. `light` points from a grain towards the light, in the glass's frame.
[[ stitchable ]] half4 sandHourglassGrains(float2 p, device const void *data, int size, float cell, float2 light) {
    device const uchar4 *cells = (device const uchar4 *)data;
    int count = size / 4;
    float2 g = p / cell;
    float2 c = floor(g);
    float2 f = g - c - 0.5;
    half4 best = half4(0.0);
    float cover = 0.0;
    float2 off = float2(0.0);
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            float2 o = float2(float(i), float(j));
            int cx = int(c.x) + i;
            int cy = int(c.y) + j;
            // Outside the grid reads as empty, like a decal-tiled image.
            if (cx < 0 || cy < 0 || cx >= sandHourglassCols || cy >= sandHourglassRows) continue;
            int index = cy * sandHourglassCols + cx;
            if (index >= count) continue;
            uchar4 texel = cells[index];
            if (texel.a > 127) {
                // Each cell's grain sits a little off centre and varies in size, so the grid never shows.
                float2 cc = c + o;
                float2 h = fract(sin(float2(dot(cc, float2(127.1, 311.7)), dot(cc, float2(269.5, 183.3)))) * 43758.5453);
                float2 d = f - o - (h - 0.5) * 0.32;
                float r = 0.5 + 0.1 * h.x;
                float k = 1.0 - smoothstep(r - 0.1, r + 0.12, length(d));
                if (k > cover) {
                    cover = k;
                    best = half4(texel) / 255.0h;
                    off = d;
                }
            }
        }
    }
    if (cover <= 0.0) {
        return half4(0.0);
    }
    float lit = clamp(dot(off, light) / 0.6, -1.0, 1.0);
    float spec = 1.0 - smoothstep(0.0, 0.2, length(off - light * 0.24));
    half3 col = best.rgb * half(1.0 + 0.3 * lit) + half3(0.26, 0.24, 0.2) * half(spec);
    return half4(col * half(cover), half(cover));
}
