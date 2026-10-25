#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

/*
 Pottery Wheel Shape — the whole studio in one full-screen pass, back to front: a dark plaster wall
 and concrete floor under a warm spotlight, the splash pan's inside, the aluminium wheel head
 (machined rings and slip smears turning with the wheel), the clay, then the splash pan's front
 wall over the head's edge, and the fingertip pads. A port of the React Native SkSL; the maths is
 the same (colours are sRGB here, so there is no P3 conversion).

 The clay is a surface of revolution seen from 16° above: for a pixel, the height of the front
 surface solves y = (v + z·sin e) / cos e with z = √(r(y)² − x²); the profile r(y) is a cubic
 B-spline through eight radii, domed while the clay is a closed lump (shape.y = 0) and cut flat with
 a lip once it is open (shape.y = 1).
 */

namespace PotteryWheelShapeK {

constant float SIN_E = 0.2756;
constant float COS_E = 0.9613;
constant float TAU = 6.2831853;

struct Prof {
    float4 a;
    float4 b;
    float H;
    float open;
    float unit;
};

// smoothstep that is defined for reversed edges too.
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

inline float4 bsp(float4 d) {
    d = abs(d);
    float4 d2 = d * d;
    float4 nearV = (4.0 - 6.0 * d2 + 3.0 * d2 * d) / 6.0;
    float4 e = max(2.0 - d, float4(0.0));
    float4 farV = e * e * e / 6.0;
    return mix(nearV, farV, step(float4(1.0), d));
}

inline float radius(float y, Prof P) {
    float H = P.H;
    float u = clamp(y / H, 0.0, 1.0) * 7.0;
    float4 w0 = bsp(float4(u) - float4(0.0, 1.0, 2.0, 3.0));
    float4 w1 = bsp(float4(u) - float4(4.0, 5.0, 6.0, 7.0));
    float r = (dot(w0, P.a) + dot(w1, P.b)) / max(dot(w0, float4(1.0)) + dot(w1, float4(1.0)), 0.001);
    float cap = sqrt(clamp((H - y) / (0.42 * H), 0.0, 1.0));
    r *= mix(cap, 1.0, P.open);
    return r + 5.0 * P.unit * exp(-max(y, 0.0) / (5.0 * P.unit));
}

// Approximate signed distance (pt) to an ellipse with semi-axes ab, centred at the origin.
inline float ellD(float2 q, float2 ab) {
    float2 s = q / ab;
    float g = length(s);
    float2 gr = s / (ab * max(g, 0.0001));
    return (g - 1.0) / max(length(gr), 0.0001);
}

float3 background(float2 p, float X, float V, float2 size, float2 base, float unit) {
    float yN = p.y / size.y;
    float3 wall = mix(float3(0.135, 0.105, 0.088), float3(0.085, 0.067, 0.058), ss(0.0, 0.75, yN));
    float horizon = base.y - 150.0 * unit;
    float fl = ss(horizon - 6.0, horizon + 30.0, p.y);
    float3 floorC = mix(float3(0.118, 0.094, 0.080), float3(0.070, 0.056, 0.048), ss(horizon, size.y, p.y));
    floorC += (vn(p * float2(0.05, 0.22)) - 0.5) * 0.018;
    float3 c = mix(wall, floorC, fl);
    // an out-of-focus shelf of bisque pots on the wall
    float shelfY = base.y - 262.0 * unit;
    float plank = ss(shelfY - 9.0, shelfY - 3.0, p.y) * (1.0 - ss(shelfY + 8.0, shelfY + 16.0, p.y));
    c = mix(c, float3(0.20, 0.135, 0.095), plank * 0.85);
    c *= 1.0 - 0.35 * ss(shelfY + 10.0, shelfY + 18.0, p.y) * (1.0 - ss(shelfY + 18.0, shelfY + 46.0, p.y));
    for (int k = 0; k < 5; k++) {
        float fk = float(k);
        float px = size.x * (0.1 + 0.2 * fk) + (hs(float2(fk, 3.0)) - 0.5) * 30.0;
        float ph = (34.0 + 40.0 * hs(float2(fk, 9.0))) * unit;
        float pr = (12.0 + 12.0 * hs(float2(fk, 5.0))) * unit;
        float yy = (shelfY - 6.0 - p.y) / ph;
        float prof = pr * (0.75 + 0.45 * sin(clamp(yy, 0.0, 1.0) * 3.0 + 0.3));
        float inPot = (1.0 - ss(-6.0, 6.0, abs(p.x - px) - prof)) * ss(-0.08, 0.06, yy) * (1.0 - ss(0.92, 1.06, yy));
        float3 bisque = mix(float3(0.30, 0.20, 0.15), float3(0.44, 0.33, 0.26), hs(float2(fk, 1.0))) * (0.75 + 0.35 * ss(prof, -prof, p.x - px));
        c = mix(c, bisque, inPot * 0.8);
    }
    // a warm spotlight pool on the wall and floor
    float2 d = (p - float2(base.x, base.y - 170.0 * unit)) / (float2(260.0, 300.0) * unit);
    c += float3(0.20, 0.125, 0.07) * exp(-dot(d, d) * 1.6);
    // the pan's shadow on the floor
    float2 sd = float2(X, V + 72.0 * unit) / (float2(205.0, 54.0) * unit);
    c *= 1.0 - 0.55 * exp(-dot(sd, sd) * 1.4);
    return c;
}

float3 shadeHeadTop(float X, float V, float RW, float r0, float spin, float unit) {
    float hx = X;
    float hz = -V / SIN_E;
    float rho = length(float2(hx, hz));
    float a = atan2(hz, hx) + spin;
    float2 polar = float2(cos(a), sin(a)) * rho / (26.0 * unit);
    float slip = ss(0.42, 0.78, vn(polar * 1.6 + 3.0) * 0.7 + vn(polar * 4.0) * 0.3);
    float3 metal = mix(float3(0.42, 0.43, 0.44), float3(0.70, 0.70, 0.69), ss(-RW, RW, -hz * 0.8 + hx * 0.3));
    metal += 0.035 * sin(rho / unit * 0.9);
    float3 slipC = mix(float3(0.36, 0.22, 0.16), float3(0.52, 0.34, 0.25), vn(polar * 7.0));
    float3 c = mix(metal, slipC, slip * 0.75);
    float ring = ss(r0 + 22.0 * unit, r0 + 4.0 * unit, rho);
    c = mix(c, float3(0.40, 0.25, 0.18), ring * 0.8);
    float2 sh = float2(hx - 18.0 * unit, hz + 22.0 * unit);
    c *= 1.0 - 0.45 * ss(r0 + 26.0 * unit, r0 - 6.0 * unit, length(sh));
    c *= 1.0 - 0.35 * ss(r0 + 8.0 * unit, r0, rho);
    c += float3(0.16) * ss(RW - 5.0 * unit, RW - 1.0 * unit, rho);
    return c;
}

} // namespace PotteryWheelShapeK

/// base = the wheel head's centre on screen; profA/profB = radii; shape = (H, open, wobble x
/// offset, speed rev/s); spin = the wheel's angle; fingerA/B = (x, y, alpha, radius). All in pt.
[[ stitchable ]] half4 potteryWheelShape(float2 p, half4 color, float2 size, float2 base, float unit,
                                         float4 profA, float4 profB, float4 shape, float spin,
                                         float4 fingerA, float4 fingerB) {
    using namespace PotteryWheelShapeK;
    Prof P = { profA, profB, shape.x, shape.y, unit };
    float X = p.x - base.x;
    float V = base.y - p.y;
    float H = shape.x;
    float RP = 172.0 * unit;
    float yR = 8.0 * unit;
    float D = 46.0 * unit;
    float RW = 116.0 * unit;
    float TW = 14.0 * unit;

    float3 col = background(p, X, V, size, base, unit);

    // --- splash pan, inside (seen through its rim)
    float2 rimQ = float2(X, V - yR * COS_E);
    float rimD = ellD(rimQ, float2(RP, RP * SIN_E));
    float s = rimQ.y / (RP * SIN_E);
    float3 panIn = mix(float3(0.075, 0.064, 0.058), float3(0.17, 0.145, 0.13), ss(-0.2, 1.0, s));
    float2 spl = float2(X, V * 3.4) / (14.0 * unit);
    panIn = mix(panIn, float3(0.30, 0.20, 0.15), ss(0.70, 0.9, vn(spl)) * 0.3);
    col = mix(col, panIn, 1.0 - ss(-0.8, 0.8, rimD));
    float xs = X / RP;
    float lipBand = 1.0 - ss(2.4 * unit, 4.6 * unit, abs(rimD + 0.5 * unit));
    float3 lipHi = mix(float3(0.22, 0.19, 0.17), float3(0.48, 0.43, 0.39), ss(0.8, -0.6, xs) * step(rimQ.y, 0.0) + 0.25);
    col = mix(col, lipHi, lipBand * step(abs(X), RP + 4.0 * unit) * step(0.0, rimQ.y));

    // --- wheel head: side band, then the top
    float headFront = -sqrt(max(RW * RW - X * X, 0.0)) * SIN_E;
    float band = step(abs(X), RW) * step(V, headFront + 0.5) * step(headFront - TW * COS_E, V);
    float3 side = mix(float3(0.24, 0.245, 0.25), float3(0.62, 0.62, 0.61), ss(RW, -RW * 0.35, X));
    side *= 0.8 + 0.2 * ss(headFront - TW * COS_E, headFront, V);
    side += 0.05 * sin(V / unit * 1.7);
    col = mix(col, side, band);
    float headD = ellD(float2(X, V), float2(RW, RW * SIN_E));
    float r0 = radius(0.0, P);
    col = mix(col, shadeHeadTop(X, V, RW, r0, spin, unit), 1.0 - ss(-0.8, 0.8, headD));

    // --- the clay: march the view ray through the band of heights it can meet the clay in, then
    // refine the first hit by bisection.
    float Xc = X - shape.z;
    float rmax = max(max(max(profA.x, profA.y), max(profA.z, profA.w)), max(max(profB.x, profB.y), max(profB.z, profB.w))) + 6.0 * unit;
    float yHi = min((V + rmax * SIN_E) / COS_E, H);
    float yLo = max((V - rmax * SIN_E) / COS_E, 0.0);
    float best = -1e4;
    float hitY = -1.0;
    float prevY = yHi;
    if (yHi > yLo && abs(Xc) < rmax) {
        for (int i = 0; i <= 14; i++) {
            float yy = mix(yHi, yLo, float(i) / 14.0);
            float zz = (yy * COS_E - V) / SIN_E;
            float dd = radius(yy, P) - length(float2(Xc, zz));
            best = max(best, dd);
            if (dd > 0.0 && hitY < 0.0) {
                float a0 = prevY;
                float a1 = yy;
                for (int j = 0; j < 5; j++) {
                    float m = 0.5 * (a0 + a1);
                    float zm = (m * COS_E - V) / SIN_E;
                    if (radius(m, P) - length(float2(Xc, zm)) > 0.0) a1 = m; else a0 = m;
                }
                hitY = a1;
            }
            prevY = yy;
        }
    }
    float y = max(hitY, 0.0);
    float r = radius(y, P);
    float z = (y * COS_E - V) / SIN_E;
    float cover = hitY >= 0.0 ? 1.0 : ss(-1.0, 0.0, best);
    if (hitY >= H - 0.01 && shape.y > 0.5) cover = 0.0;
    float Rt = radius(H, P);
    float open = shape.y;
    float speed = shape.w;

    if (cover > 0.0) {
        float rs = max(r, 0.5);
        float sinT = clamp(Xc / rs, -1.0, 1.0);
        float cosT = clamp(z / rs, -1.0, 1.0);
        float dr = (radius(y + 1.0, P) - radius(y - 1.0, P)) * 0.5;
        float a = atan2(sinT, cosT) + spin;
        // throwing marks: a helix (pitch 5 pt) that climbs as the wheel turns
        float pitch = 5.0 * unit;
        float ridge = sin(TAU * (y / pitch - a / TAU));
        float ridge2 = sin(TAU * (y / (pitch * 2.7) - a / TAU) + 1.7);
        float depth = mix(0.04, 0.13 + 0.07 * vn(float2(y / (18.0 * unit), 2.0)), open) * ss(0.0, 14.0 * unit, y);
        float3 n = normalize(float3(sinT, -dr + (ridge * 0.7 + ridge2 * 0.3) * depth, cosT));
        float3 viewD = float3(0.0, SIN_E, COS_E);
        float3 L = normalize(float3(-0.55, 0.62, 0.56));
        float diff = clamp(dot(n, L) * 0.62 + 0.42, 0.0, 1.0);
        float2 tq = float2(cos(a) * 2.4 + y / (30.0 * unit), sin(a) * 2.4 - y / (44.0 * unit));
        float tex = vn(tq * 2.0) * 0.6 + vn(tq * 5.0) * 0.4;
        tex = mix(tex, 0.5, clamp(speed * 0.28, 0.0, 0.7));
        float3 wet = mix(float3(0.40, 0.26, 0.195), float3(0.54, 0.37, 0.28), tex);
        wet = mix(float3(0.33, 0.19, 0.135), wet, ss(0.0, 22.0 * unit, y));
        float3 c = wet * (0.30 + diff * float3(1.05, 0.96, 0.86));
        float3 R = reflect(-viewD, n);
        float box = (1.0 - ss(0.08, 0.15, abs(R.x + 0.40))) * (1.0 - ss(0.26, 0.42, abs(R.y - 0.28))) * step(0.0, R.z);
        float3 hh = normalize(L + viewD);
        float spec = pow(max(dot(n, hh), 0.0), 64.0);
        float f1 = 1.0 - max(dot(n, viewD), 0.0);
        float fres = f1 * f1 * f1;
        float rl = max(dot(n, normalize(float3(0.85, 0.15, -0.35))), 0.0);
        float rim = rl * rl;
        c += float3(0.95, 0.88, 0.80) * box * 0.42 + float3(1.0, 0.92, 0.84) * spec * 0.32;
        c += float3(0.95, 0.62, 0.38) * rim * 0.20 + float3(0.55, 0.45, 0.40) * fres * 0.14;
        c *= mix(0.55, 1.0, ss(0.0, 16.0 * unit, y));
        col = mix(col, c, cover);
    } else if (open > 0.01 && Rt > 1.0) {
        // --- inside the vessel, seen through the opening, and the lip's top face
        float2 q = float2(Xc, V - H * COS_E);
        float2 ab = float2(Rt, Rt * SIN_E);
        float od = ellD(q, ab);
        float g = length(q / ab);
        float sb = q.y / ab.y;
        float wallT = 7.0 * unit;
        float lip = ss(1.0 - wallT / Rt - 0.04, 1.0 - wallT / Rt, g);
        float3 inner = mix(float3(0.10, 0.058, 0.042), float3(0.34, 0.20, 0.145), ss(-0.3, 1.0, sb));
        float3 lipC = mix(float3(0.50, 0.31, 0.22), float3(0.78, 0.58, 0.46), ss(-0.8, 0.8, sb - q.x / ab.x * 0.6));
        float3 c = mix(inner, lipC, lip);
        col = mix(col, c, (1.0 - ss(-0.8, 0.8, od)) * open);
    }

    // --- splash pan, front wall and rim lip (in front of the wheel head)
    float rimFront = yR * COS_E - sqrt(max(RP * RP - X * X, 0.0)) * SIN_E;
    float wallA = step(abs(X), RP) * (1.0 - ss(rimFront - 0.6, rimFront + 0.6, V)) * step(rimFront - D * COS_E, V);
    float3 wallC = mix(float3(0.13, 0.112, 0.10), float3(0.26, 0.225, 0.20), ss(0.9, -0.5, xs));
    wallC *= 0.72 + 0.28 * ss(rimFront - D * COS_E, rimFront - 4.0 * unit, V);
    wallC += (vn(float2(X, V) / (3.0 * unit)) - 0.5) * 0.03;
    wallC += float3(0.10, 0.085, 0.075) * ss(rimFront - 9.0 * unit, rimFront - 2.0 * unit, V);
    col = mix(col, wallC, wallA);
    col = mix(col, lipHi, lipBand * step(abs(X), RP + 4.0 * unit) * step(rimQ.y, 0.0));

    // --- fingertip pads (where the hands work the clay)
    for (int k = 0; k < 2; k++) {
        float4 f = k == 0 ? fingerA : fingerB;
        if (f.z > 0.001) {
            float d = length(p - f.xy) / f.w;
            float disc = 1.0 - ss(0.86, 1.0, d);
            float ringF = ss(0.62, 0.9, d) * disc;
            float halo = exp(-d * d * 1.6) * 0.25;
            col = mix(col, float3(1.0, 0.94, 0.88), (disc * 0.16 + ringF * 0.38) * f.z);
            col += float3(0.30, 0.24, 0.18) * halo * f.z;
        }
    }

    // grain and vignette
    col += (hs(p * 0.731 + 7.0) - 0.5) * 0.035;
    float2 vq = p / size - 0.5;
    col *= 1.0 - 0.55 * dot(vq * float2(1.1, 0.9), vq * float2(1.1, 0.9));
    return half4(half3(saturate(col)), 1.0);
}
