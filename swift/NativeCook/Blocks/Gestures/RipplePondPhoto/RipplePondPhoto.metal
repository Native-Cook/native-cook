// Ripple Pond Photo — the water, in two passes over one triangle.
//
//  1. ripplePondPhotoStep, on a gridW × gridH rgba16Float texture, ping-ponged: r is the height now,
//     g the height one step ago. next = (2h − prev + c²·∇²h) · damp, with the neighbours clamped at
//     the border, so the edges are walls and rings bounce off them. Up to four drops are pressed into
//     both r and g (a displacement, not a push), each a zero-mean dip (1 − q)·exp(−q), so the water's
//     level never drifts.
//  2. ripplePondPhotoView, on the screen: the field's slope bends the photo's coordinate (red and
//     blue a little more and less than green), its curvature brightens where the surface focuses
//     light, the slope towards the light shades the rings and a narrow highlight glints on their lit
//     side. A flat field returns the photo untouched, which is what makes the loop's cut invisible.

#include <metal_stdlib>
using namespace metal;

struct RipplePondPhotoStepU {
    float4 cfg;        // c², damping, grid width, grid height
    float4 drops[4];   // x, y (0…1), radius (cells), depth
};

struct RipplePondPhotoViewU {
    float4 canvas;     // width, height (px), pixel ratio, -
    float4 grid;       // grid width, height, photo uv scale x, y (cover)
    float4 bend;       // refraction (pt per slope), its limit (pt), chroma spread, normal gain
    float4 light;      // highlight power, highlight gain, caustic gain, shade gain
};

struct RipplePondPhotoVaryings {
    float4 position [[position]];
};

vertex RipplePondPhotoVaryings ripplePondPhotoVertex(uint i [[vertex_id]]) {
    float2 p = float2(float((i << 1u) & 2u), float(i & 2u));
    RipplePondPhotoVaryings out;
    out.position = float4(p * 2.0 - 1.0, 0.0, 1.0);
    return out;
}

fragment float4 ripplePondPhotoStep(RipplePondPhotoVaryings in [[stage_in]],
                                    constant RipplePondPhotoStepU &S [[buffer(0)]],
                                    texture2d<float> field [[texture(0)]]) {
    int2 size = int2(S.cfg.zw);
    int2 hi = size - int2(1);
    int2 p = clamp(int2(in.position.xy), int2(0), hi);
    float2 c = field.read(uint2(p)).rg;
    float l = field.read(uint2(clamp(p + int2(-1, 0), int2(0), hi))).r;
    float r = field.read(uint2(clamp(p + int2(1, 0), int2(0), hi))).r;
    float u = field.read(uint2(clamp(p + int2(0, -1), int2(0), hi))).r;
    float d = field.read(uint2(clamp(p + int2(0, 1), int2(0), hi))).r;
    float next = (2.0 * c.x - c.y + S.cfg.x * (l + r + u + d - 4.0 * c.x)) * S.cfg.y;
    float add = 0.0;
    for (int i = 0; i < 4; i++) {
        float4 drop = S.drops[i];
        if (drop.w != 0.0) {
            float2 q = float2(p) + float2(0.5) - drop.xy * S.cfg.zw;
            float k = dot(q, q) / (drop.z * drop.z);
            add += drop.w * (1.0 - k) * exp(-k);
        }
    }
    return float4(next + add, c.x + add, 0.0, 1.0);
}

static float ripplePondPhotoHeight(texture2d<float> field, sampler samp, float2 uv) {
    return field.sample(samp, uv, level(0.0)).r;
}

static float3 ripplePondPhotoPhoto(texture2d<float> photo, sampler samp, float2 uv, float2 scale) {
    float2 q = (uv - float2(0.5)) * scale + float2(0.5);
    return photo.sample(samp, clamp(q, float2(0.001), float2(0.999)), level(0.0)).rgb;
}

fragment float4 ripplePondPhotoView(RipplePondPhotoVaryings in [[stage_in]],
                                    constant RipplePondPhotoViewU &V [[buffer(0)]],
                                    texture2d<float> field [[texture(0)]],
                                    texture2d<float> photo [[texture(1)]],
                                    sampler samp [[sampler(0)]]) {
    float2 uv = in.position.xy / V.canvas.xy;
    float2 cell = float2(1.0) / V.grid.xy;
    float hc = ripplePondPhotoHeight(field, samp, uv);
    float hl = ripplePondPhotoHeight(field, samp, uv - float2(cell.x, 0.0));
    float hr = ripplePondPhotoHeight(field, samp, uv + float2(cell.x, 0.0));
    float hu = ripplePondPhotoHeight(field, samp, uv - float2(0.0, cell.y));
    float hd = ripplePondPhotoHeight(field, samp, uv + float2(0.0, cell.y));
    float2 slope = float2(hr - hl, hd - hu) * 0.5;

    // refraction, in points, then as a step in uv
    float2 bend = slope * V.bend.x;
    float far = length(bend);
    if (far > V.bend.y) { bend = bend * (V.bend.y / far); }
    float2 shift = bend * V.canvas.z / V.canvas.xy;
    float red = ripplePondPhotoPhoto(photo, samp, uv + shift * (1.0 + V.bend.z), V.grid.zw).r;
    float green = ripplePondPhotoPhoto(photo, samp, uv + shift, V.grid.zw).g;
    float blue = ripplePondPhotoPhoto(photo, samp, uv + shift * (1.0 - V.bend.z), V.grid.zw).b;
    float3 col = pow(float3(red, green, blue), float3(2.2));

    // light from the upper left
    float3 L = normalize(float3(-0.45, -0.6, 0.66));
    float3 H = normalize(L + float3(0.0, 0.0, 1.0));
    float3 n = normalize(float3(-slope * V.bend.w, 1.0));
    float glint = max(pow(max(dot(n, H), 0.0), V.light.x) - pow(H.z, V.light.x), 0.0);
    float lap = hl + hr + hu + hd - 4.0 * hc;
    col = col * (1.0 + clamp(-lap * V.light.z, -0.2, 0.5));
    col = col * (1.0 + clamp(-dot(slope, L.xy) * V.light.w, -0.22, 0.22));
    col = col + glint * V.light.y * float3(1.0, 0.98, 0.92);
    return float4(pow(clamp(col, float3(0.0), float3(1.0)), float3(1.0 / 2.2)), 1.0);
}
