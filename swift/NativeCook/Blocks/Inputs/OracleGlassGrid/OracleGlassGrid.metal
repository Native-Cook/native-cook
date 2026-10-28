#include <metal_stdlib>
using namespace metal;

// The lava behind the Oracle Glass Grid: soft green blobs drifting through purple with maroon rims, and teal
// pools at the edges. Each blob circles its own centre k = ±1 or 2 times per loop (`phase` = 2π · t / loop),
// so the field loops exactly. The same code is in OracleGlassGrid.kt (AGSL) and lavaShader.ts (SkSL).

float OGG_hash(float2 p) {
    return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
}

float OGG_smooth(float a, float b, float x) {
    float t = clamp((x - a) / (b - a), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

float OGG_blob(float2 p, float bx, float by, float r, float ry, float k, float ph, float s, float a) {
    float2 c = float2(bx + r * cos(k * a + ph), by + r * ry * sin(k * a + ph));
    float2 d = p - c;
    return exp(-dot(d, d) / (s * s));
}

[[ stitchable ]] half4 oracleGlassGridLava(float2 position, half4 color, float2 size, float phase) {
    float a = phase;
    float2 u = (position - 0.5 * size) / size.y * 2.0;
    float2 p = u + 0.10 * float2(sin(u.y * 3.1 + a + 0.4), cos(u.x * 2.7 - a + 1.1));

    float f = OGG_blob(p, -0.30, -0.62, 0.22, 0.8, 1.0, 0.0, 0.30, a)
            + OGG_blob(p, 0.28, -0.10, 0.20, 0.8, -1.0, 1.7, 0.34, a)
            + OGG_blob(p, -0.20, 0.42, 0.24, 0.8, 1.0, 3.1, 0.32, a)
            + OGG_blob(p, 0.30, 0.78, 0.20, 0.8, 2.0, 4.4, 0.26, a)
            + OGG_blob(p, 0.05, 0.05, 0.30, 0.8, -1.0, 5.5, 0.22, a);
    float g = OGG_blob(p, -0.55, -0.95, 0.18, 1.0, 1.0, 2.2, 0.36, a)
            + OGG_blob(p, 0.62, 0.35, 0.22, 1.0, -1.0, 0.6, 0.32, a)
            + OGG_blob(p, -0.58, 0.95, 0.15, 1.0, 1.0, 4.0, 0.30, a);

    float3 purple = float3(0.643, 0.400, 0.769);
    float3 plum = float3(0.550, 0.340, 0.660);
    float3 maroon = float3(0.430, 0.230, 0.270);
    float3 green = float3(0.114, 0.227, 0.169);
    float3 teal = float3(0.270, 0.640, 0.620);

    float3 col = mix(purple, plum, OGG_smooth(0.05, 0.45, f));
    col = mix(col, maroon, OGG_smooth(0.22, 0.70, f));
    col = mix(col, green, OGG_smooth(0.45, 1.15, f));
    col = mix(col, teal, OGG_smooth(0.15, 0.85, g) * (1.0 - OGG_smooth(0.25, 0.75, f)) * 0.9);

    float2 v = (position / size - 0.5) * float2(1.1, 0.9);
    col *= 1.0 - 0.35 * dot(v, v);
    col += (OGG_hash(floor(position)) - 0.5) * 0.03;
    return half4(half3(col), 1.0);
}
