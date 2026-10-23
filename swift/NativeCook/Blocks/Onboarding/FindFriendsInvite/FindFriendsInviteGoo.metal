#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The liquid contacts: a metaball field. Each ball adds r^2/d^2 and the surface is the iso-line
// f = 1. `goo` blends the field from the union of the circles (max: exactly the resting avatars, the
// later one on top where two overlap) to the sum (bridges grow between nearby balls, droplets neck
// into the blob). The edge is antialiased with the field's gradient; a little dome lighting (`gloss`)
// makes it read as liquid. `data` holds 9 balls x 7 floats: x, y, r, unused, red, green, blue.
// `origin` is the view's top-left in screen points, so ball positions stay in screen space.

[[ stitchable ]] half4 findFriendsInviteGoo(float2 position, half4 color, device const float *data, int count,
                                            float goo, float gloss, float2 origin) {
    float2 p = position + origin;
    float fs = 0.0, fu = 0.0, ws = 0.0;
    float2 gs = float2(0.0), gu = float2(0.0);
    float3 cw = float3(0.0);
    float3 ctop = float3(-1.0);
    float3 cmax = float3(0.0);
    for (int i = 0; i < 9; i++) {
        int o = i * 7;
        float3 b = float3(data[o], data[o + 1], data[o + 2]);
        float3 k = float3(data[o + 4], data[o + 5], data[o + 6]);
        if (b.z <= 0.0) { continue; }
        float2 dv = p - b.xy;
        float d2 = max(dot(dv, dv), 0.0001);
        float q = b.z * b.z / d2;
        float2 gq = -2.0 * q * dv / d2;
        fs += q;
        gs += gq;
        if (q > fu) { fu = q; gu = gq; cmax = k; }
        if (q >= 1.0) { ctop = k; }
        float e = d2 + 30.0;
        float w = b.z / (e * e * e);
        cw += k * w;
        ws += w;
    }
    float f = mix(fu, fs, goo);
    float2 gr = mix(gu, gs, goo);
    float gl = max(length(gr), 0.0001);
    float sd = (f - 1.0) / gl;
    float a = clamp(sd * 3.0 + 0.5, 0.0, 1.0);
    if (a <= 0.0) { return half4(0.0); }
    float3 base = ctop.r >= 0.0 ? ctop : cmax;
    float3 mixc = ws > 0.0 ? cw / ws : base;
    float3 col = mix(base, mixc, smoothstep(0.0, 0.5, goo));
    float dep = max(clamp(sd / 16.0, 0.0, 1.0), clamp((f - 1.0) / 1.5, 0.0, 1.0));
    float2 outw = -gr / gl;
    float tilt = pow(1.0 - dep, 1.6) * 1.4;
    float3 n = normalize(float3(outw * tilt, 1.0));
    float3 lv = normalize(float3(-0.45, -0.65, 0.75));
    float dif = dot(n, lv) - lv.z;
    float3 hv = normalize(lv + float3(0.0, 0.0, 1.0));
    float spec = pow(max(dot(n, hv), 0.0), 60.0);
    float3 lit = col * (1.0 + 0.35 * dif) + float3(spec * 0.55);
    col = mix(col, lit, gloss);
    float3 outc = clamp(col, 0.0, 1.0);
    return half4(half3(outc * a), half(a));
}
