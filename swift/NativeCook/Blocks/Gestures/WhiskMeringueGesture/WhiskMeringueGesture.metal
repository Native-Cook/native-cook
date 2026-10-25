#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

/*
 Whisk Meringue Gesture — the countertop, the glass bowl and what is in it, in one full-screen
 pass (a port of the React Native SkSL; colours are sRGB here, so there is no P3 conversion).

 Bowl space q: the bowl's centre is 0 and its rim a unit circle (the camera's tilt squashes it to
 an ellipse on screen, SQUASH). Back to front: a pale terrazzo countertop with the bowl's soft
 shadow and the caustic its glass focuses into it; the glass's inner wall above the contents; the
 contents' surface; the outer front wall, through which the contents show; and the rim's glass edge.

 Contents: clear whites are a yellowish film with ripples round the whisk and a window's
 reflection. Foam is Voronoi bubbles in a frame the swirl turns; cells switch on one by one by their
 hash as `foam` rises (and pop the same way when it falls). A height field gives the relief.
 */

namespace WhiskMeringueGestureK {

constant float SQUASH = 0.85;

inline float ss(float e0, float e1, float x) {
    float t = clamp((x - e0) / (e1 - e0), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

inline float hs(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }

inline float vn(float2 q) {
    float2 i = floor(q);
    float2 f = fract(q);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hs(i), hs(i + float2(1.0, 0.0)), f.x), mix(hs(i + float2(0.0, 1.0)), hs(i + float2(1.0, 1.0)), f.x), f.y);
}

inline float fbm(float2 q) { return vn(q) * 0.62 + vn(q * 2.1 + 5.3) * 0.38; }

float3 counter(float2 p, float2 size, float u) {
    float3 c = mix(float3(0.88, 0.855, 0.81), float3(0.76, 0.735, 0.69), ss(0.0, 1.0, (p.x * 0.4 + p.y) / size.y));
    // terrazzo chips
    float2 cq = p / (9.0 * u);
    float2 ci = floor(cq);
    float2 cf = fract(cq) - 0.5;
    float hh = hs(ci);
    float2 o = float2(hs(ci + 3.1), hs(ci + 7.7)) - 0.5;
    float chip = 1.0 - ss(0.10, 0.16, length(cf - o * 0.5) / (0.4 + hh));
    float3 chipC = hh < 0.33 ? float3(0.62, 0.56, 0.50) : (hh < 0.66 ? float3(0.70, 0.72, 0.70) : float3(0.80, 0.66, 0.56));
    c = mix(c, chipC, chip * step(0.55, hs(ci + 1.9)) * 0.6);
    c += (vn(p * 0.35) - 0.5) * 0.025;
    return c;
}

inline float2 rot(float2 v, float a) {
    float c = cos(a);
    float s = sin(a);
    return float2(c * v.x - s * v.y, s * v.x + c * v.y);
}

// Voronoi: x = distance to the nearest cell centre, y = to the second, z = the nearest cell's hash.
float3 voronoi(float2 q) {
    float2 i = floor(q);
    float2 f = fract(q);
    float d1 = 8.0;
    float d2 = 8.0;
    float id = 0.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            float2 g = float2(float(x), float(y));
            float2 c = g + float2(hs(i + g), hs(i + g + 17.3)) * 0.8 + 0.1;
            float d = length(c - f);
            if (d < d1) { d2 = d1; d1 = d; id = hs(i + g + 4.7); }
            else if (d < d2) { d2 = d; }
        }
    }
    return float3(d1, d2, id);
}

// How far the contents at radius rr have turned: a rigid turn plus a bounded twist.
inline float turnAt(float rr, float4 foam) {
    return foam.z * 0.7 + 1.8 * (1.0 - exp(-foam.z / 5.0)) * (1.0 - rr);
}

// The contents' height at surface point s (surface radius = 1), in surface units.
float height(float2 s, float4 foam, float4 whisk, float4 lpeak) {
    float rr = length(s);
    float a = atan2(s.y, s.x);
    float sw = turnAt(rr, foam);
    float2 sr = rot(s, -sw);
    float lumps = fbm(sr * 5.0 + 2.0) * 0.06;
    float rb = 0.5 + 0.5 * sin(a * 7.0 - sw + rr * 15.0);
    float ridge = rb * rb * rb * ss(0.98, 0.7, rr) * ss(0.06, 0.32, rr) * 0.09;
    float rsH = mix(0.80, 0.90, foam.x);
    float soH = mix(0.17, 0.10, foam.x);
    float2 d = s - (lpeak.xy - float2(0.0, soH)) / rsH;
    float pk = exp(-dot(d, d) / 0.014) * 0.34 * lpeak.z;
    float2 wd = s - (whisk.xy - float2(0.0, soH)) / rsH;
    float w2 = dot(wd, wd);
    float wake = (-exp(-w2 / 0.012) * 0.05 + exp(-w2 / 0.05) * 0.025) * foam.w * (1.0 - whisk.z);
    return foam.x * (lumps + ridge * foam.y + wake) + pk;
}

} // namespace WhiskMeringueGestureK

/// bowl = (cx, cy, R, unit); foam = (foam, peaks, swirl, agitation);
/// whisk = (head x, head y in bowl units, lift, ripple phase); lpeak = (x, y, amount, 0).
[[ stitchable ]] half4 whiskMeringueGesture(float2 p, half4 color, float2 size, float4 bowl, float4 foam,
                                            float4 whisk, float4 lpeak) {
    using namespace WhiskMeringueGestureK;
    float R = bowl.z;
    float u = bowl.w;
    float2 q = float2(p.x - bowl.x, (p.y - bowl.y) / SQUASH) / R;
    float r = length(q);
    float3 col = counter(p, size, u);

    // the bowl's shadow on the counter (light from the top left), and the glass's caustic in it
    float2 sq = (q - float2(0.16, 0.34)) / float2(1.04, 0.92);
    float sd = length(sq);
    col *= 1.0 - 0.34 * (1.0 - ss(0.62, 1.08, sd));
    float caus = ss(0.55, 0.85, sd) * (1.0 - ss(0.85, 1.0, sd)) * ss(0.0, 0.6, sq.x + sq.y);
    col += float3(0.12, 0.11, 0.07) * caus;

    float fo = foam.x;
    float rs = mix(0.80, 0.90, fo);
    float sOff = mix(0.17, 0.10, fo);
    float2 s = (q - float2(0.0, sOff)) / rs;
    float sr = length(s);
    float dropQ = 0.42;
    float2 bq = float2(q.x, q.y > 0.0 ? q.y / (1.0 + dropQ) : q.y);
    float body = length(bq);
    float inBody = 1.0 - ss(1.0, 1.0 + 1.2 / R, body);
    float rimW = 6.0 * u / R;

    // contents colour at a surface point (also seen through the front wall)
    float3 liquid = mix(col * float3(0.9, 0.92, 0.9), float3(0.97, 0.93, 0.70), 0.2);
    float wd = length(s - (whisk.xy - float2(0.0, sOff)) / rs);
    float ripple = sin(wd * 46.0 - whisk.w * 3.0) * foam.w * exp(-wd * 3.2);
    float2 win = s + float2(0.42, 0.5) + ripple * 0.06;
    float window = (1.0 - ss(0.06, 0.2, abs(win.x + win.y * 0.25))) * (1.0 - ss(0.1, 0.26, abs(win.y)));
    liquid += float3(0.95, 0.95, 0.9) * window * 0.22 + ripple * 0.05;
    liquid *= 1.0 - 0.07 * ss(0.52, 0.6, sr) * (1.0 - ss(0.6, 0.74, sr));
    liquid += float3(0.08, 0.075, 0.04) * ss(0.9, 1.0, sr);

    float3 contents = liquid;
    float cover = 0.0;
    if (fo > 0.001) {
        float sw = turnAt(sr, foam);
        float2 srot = rot(s, -sw);
        float cell = mix(11.0, 3.2, ss(0.0, 0.9, fo)) * u / (R * rs);
        float3 v = voronoi(srot / cell);
        float want = ss(0.0, 0.55, fo) * 1.25 - 0.18 + (fbm(srot * 3.0) - 0.5) * 0.35;
        float disk = 1.0 - ss(mix(0.36, 0.9, fo), mix(0.44, 1.0, fo), v.x);
        cover = step(v.z, want) * max(disk, ss(0.35, 0.6, fo));
        float edge = v.y - v.x;
        float contrast = mix(1.0, 0.12, ss(0.55, 1.0, fo));
        float fill = ss(0.15, 0.6, fo);
        float wall = max(1.0 - ss(0.0, mix(0.10, 0.35, fo), edge), ss(0.26, 0.4, v.x) * (1.0 - fill));
        float3 bubble = mix(float3(0.93, 0.92, 0.86), float3(0.99, 0.985, 0.96), ss(0.0, 0.3, edge));
        bubble = mix(float3(0.975, 0.97, 0.95), bubble, contrast);
        // relief from the height field
        float e = 0.012;
        float h0 = height(s, foam, whisk, lpeak);
        float hx = height(s + float2(e, 0.0), foam, whisk, lpeak) - h0;
        float hy = height(s + float2(0.0, e), foam, whisk, lpeak) - h0;
        float3 n = normalize(float3(-hx / e, -hy / e, 0.9));
        float3 L = normalize(float3(-0.5, -0.62, 0.62));
        float diff = clamp(dot(n, L) * 0.55 + 0.55, 0.0, 1.15);
        float3 hh = normalize(L + float3(0.0, 0.0, 1.0));
        float gloss = mix(0.15, 0.85, ss(0.4, 1.0, fo));
        float spec = pow(max(dot(n, hh), 0.0), mix(12.0, 46.0, fo)) * gloss;
        float3 foamC = bubble * (0.78 + 0.3 * diff) + spec * 0.35;
        foamC *= 1.0 - 0.10 * ss(0.92, 1.0, sr);
        float see = (1.0 - fill) * (1.0 - wall) * (0.55 + 0.45 * ss(0.25, 0.0, v.x));
        foamC = mix(foamC, liquid * 1.04, see);
        contents = mix(liquid, foamC, cover * ss(0.0, 0.08, fo));
    }

    // inside the rim
    float inRim = 1.0 - ss(1.0 - rimW - 1.0 / R, 1.0 - rimW, r);
    float inSurf = 1.0 - ss(1.0 - 1.4 / (R * rs), 1.0, sr);
    // the glass's inner wall above the contents: the counter, refracted and a little darker
    float3 wallC = counter(p + float2(q.x, q.y) * 14.0 * u, size, u) * float3(0.80, 0.85, 0.85);
    float nq = max(0.0, -q.y);
    wallC += float3(0.14) * nq * nq * ss(0.75, 0.95, r);
    float cling = ss(1.12, 1.0, sr) * ss(0.4, 0.9, fo) * 0.6;
    wallC = mix(wallC, float3(0.95, 0.94, 0.91), cling);
    float3 inner = mix(wallC, contents, inSurf);
    col = mix(col, inner, inRim);

    // the outer front wall: the contents through the glass
    float front = inBody * step(0.0, q.y) * ss(1.0 - 0.5 / R, 1.0 + rimW, r);
    float depthK = clamp(body, 0.0, 1.0);
    float3 through = mix(contents, liquid, 0.25) * 0.84;
    through = mix(through, col * 0.8, ss(0.86, 1.0, body));
    float streak = (1.0 - ss(0.03, 0.08, abs(q.x + 0.55))) * 0.22 + (1.0 - ss(0.015, 0.04, abs(q.x - 0.62))) * 0.12;
    through += float3(streak) * depthK;
    through = mix(through, float3(0.42, 0.52, 0.52), ss(0.93, 1.0, body) * 0.6);
    col = mix(col, through * float3(0.94, 0.99, 0.99), front);

    // the rim: thick glass catching the light at the top left
    float rimD = abs(r - 1.0) / rimW;
    float rimA = 1.0 - ss(0.75, 1.15, rimD);
    float ang = atan2(q.y, q.x);
    float cl = max(0.0, cos(ang + 2.25));
    float lit = cl * cl * cl;
    float3 rimC = mix(col * 0.9, float3(0.98, 1.0, 1.0), 0.35 + 0.55 * lit);
    rimC *= 1.0 - 0.18 * ss(0.1, 0.9, rimD) * step(0.0, r - 1.0);
    col = mix(col, rimC, rimA);

    col += (hs(p * 0.917 + 3.0) - 0.5) * 0.025;
    return half4(half3(saturate(col)), 1.0);
}
