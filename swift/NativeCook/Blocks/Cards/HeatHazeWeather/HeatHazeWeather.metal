// Heat Haze Weather — the scene, one SwiftUI colour effect over the whole screen.
//
// A port of the React Native `hhw-shader.ts` (Skia SkSL), line for line. The scene is drawn in code:
// a sky in three stops with the sun and its glow, a far ridge and a mesa, a desert floor in
// perspective with scrub, a road with its lines and a row of utility poles. Heat haze is a
// displacement of the sampling point: two layers of value noise rise through a band around the
// horizon; they cross-fade on a flow period so the field loops. Near the horizon the road turns into
// a mirage: the sky, mirrored, shimmering. A finger adds its own local shimmer.
//
// Positions are in points. Colours are written as sRGB; with `wide` = 1 the result goes through the
// same linear sRGB → Display P3 matrix as the React Native build (whose Skia output is shown as P3 on
// iOS); the SwiftUI layer is non-linear sRGB.

#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

namespace heatHazeWeather {

constant float HORIZON = 0.54;
constant float AMP = 3.4;
constant float BAND_OFFSET = 0.015;
constant float BAND_FOLD = 0.07;
constant float CELL_X = 13.0;
constant float CELL_Y = 6.0;
constant float RISE = 8.0;
constant float TOUCH_AMP = 7.0;
constant float TOUCH_RADIUS = 74.0;

struct Ctx {
    float2 size;
    float2 sun;
    float heat;
    float dusk;
    float phase;
};

float3 hx(uint v) {
    return float3(float((v >> 16) & 255u), float((v >> 8) & 255u), float(v & 255u)) / 255.0;
}

float3 pal(uint noon, uint duskCol, float dusk) { return mix(hx(noon), hx(duskCol), dusk); }

float3 skyTop(float d) { return pal(0x2C69AE, 0x25284F, d); }
float3 skyMid(float d) { return pal(0x8DB5D6, 0x8A5A7E, d); }
float3 skyLow(float d) { return pal(0xF2E4C6, 0xF29A5E, d); }
float3 sunCol(float d) { return pal(0xFFF4DC, 0xFFB46E, d); }
float3 ridgeCol(float d) { return pal(0xA7A9B4, 0x5E4868, d); }
float3 mesaCol(float d) { return pal(0xB48C6C, 0x4A3044, d); }
float3 sandFar(float d) { return pal(0xDDBF90, 0xA06A52, d); }
float3 sandNear(float d) { return pal(0xBC915F, 0x4F3631, d); }
float3 asphalt(float d) { return pal(0x4C4744, 0x2B2528, d); }
float3 paint(float d) { return pal(0xEDE6D5, 0xC9AFA0, d); }
float3 poleCol(float d) { return pal(0x3C3431, 0x22191F, d); }

float hsh(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float vnoise(float2 p) {
    float2 i = floor(p);
    float2 q = fract(p);
    float2 u = q * q * (3.0 - 2.0 * q);
    float a = hsh(i);
    float b = hsh(i + float2(1.0, 0.0));
    float c = hsh(i + float2(0.0, 1.0));
    float d = hsh(i + float2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float3 skyAt(float2 p, Ctx c) {
    float hy = c.size.y * HORIZON;
    float v = clamp(p.y / hy, 0.0, 1.0);
    float3 top = skyTop(c.dusk);
    float3 mid = skyMid(c.dusk);
    float3 low = skyLow(c.dusk);
    float3 col = v < 0.6 ? mix(top, mid, smoothstep(0.0, 0.6, v)) : mix(mid, low, smoothstep(0.6, 1.0, v));
    float d = length(p - c.sun);
    float3 sc = sunCol(c.dusk);
    float r = c.size.x * 0.046;
    col += sc * (0.55 * exp(-d / (c.size.x * 0.075)) + (0.18 + 0.22 * c.dusk) * exp(-d / (c.size.x * 0.42)));
    col = mix(col, sc, smoothstep(r + 0.8, r - 0.8, d));
    return col;
}

float ridgeTop(float x, Ctx c) {
    float u = x / c.size.x;
    return c.size.y * (HORIZON - 0.03 - 0.014 * sin(u * 5.3 + 1.1) - 0.008 * sin(u * 13.1 + 0.3));
}

// a butte: a flat top, a cliff, then a long talus slope down to the plain
float butte(float u, float centre, float top, float base, float h) {
    float d = abs(u - centre);
    if (d >= base) { return 0.0; }
    if (d <= top) { return h; }
    return h * (1.0 - pow((d - top) / (base - top), 0.55));
}

float mesaTop(float x, Ctx c) {
    float u = x / c.size.x;
    float h = max(butte(u, 0.185, 0.07, 0.15, 0.07), butte(u, 0.865, 0.022, 0.06, 0.042));
    h += step(0.0005, h) * 0.003 * (vnoise(float2(x / 3.0, 7.0)) - 0.5);
    return c.size.y * (HORIZON - h) + step(h, 0.0) * c.size.y;
}

float3 groundAt(float2 p, Ctx c) {
    float hy = c.size.y * HORIZON;
    float span = c.size.y - hy;
    float dy = max(p.y - hy, 0.0001);
    float z = span / dy;
    float wb = c.size.x * 0.62;
    float xw = (p.x - c.size.x * 0.5) * z / wb;
    float perPt = z / wb;
    float nearK = clamp(dy / span, 0.0, 1.0);
    float3 col = mix(sandFar(c.dusk), sandNear(c.dusk), sqrt(nearK));
    float scrub = vnoise(float2(xw * 7.0, z * 2.6));
    col *= 1.0 - 0.3 * smoothstep(0.8, 0.9, scrub) * smoothstep(0.02, 0.12, nearK) * (1.0 - 0.5 * smoothstep(0.5, 1.0, nearK));
    col *= 0.95 + 0.1 * vnoise(float2(xw * 2.0, z * 0.7));
    col *= 0.96 + 0.08 * vnoise(float2(xw * 9.0, z * 4.0));

    float ax = abs(xw);
    float onRoad = 1.0 - smoothstep(0.95 - perPt, 0.95 + perPt, ax);
    float3 road = asphalt(c.dusk) * (0.94 + 0.1 * vnoise(float2(xw * 14.0, z * 6.0)));
    float edge = smoothstep(0.82 - perPt, 0.82 + perPt, ax) * (1.0 - smoothstep(0.87 - perPt, 0.87 + perPt, ax));
    float dash = (1.0 - smoothstep(0.022 - perPt, 0.022 + perPt, ax)) * step(fract(z * 0.35), 0.5);
    road = mix(road, paint(c.dusk), clamp(edge + dash, 0.0, 1.0) * 0.85);
    col = mix(col, road, onRoad);

    // far ground melts into the low sky
    col = mix(col, skyLow(c.dusk), exp(-dy / (c.size.y * 0.035)) * 0.62);
    return col;
}

float3 scene(float2 p, Ctx c) {
    float hy = c.size.y * HORIZON;
    float3 col;
    if (p.y < hy) {
        col = skyAt(p, c);
        float rt = ridgeTop(p.x, c);
        float3 ridge = mix(ridgeCol(c.dusk), skyLow(c.dusk), 0.35);
        col = mix(col, ridge, smoothstep(rt - 0.6, rt + 0.6, p.y));
        float mt = mesaTop(p.x, c);
        // horizontal strata, a dustier talus and the shadowed side away from the sun
        float strata = vnoise(float2(p.x / 46.0, p.y / 2.6));
        float3 mesa = mesaCol(c.dusk) * (0.84 + 0.2 * strata);
        float talus = smoothstep(mt + c.size.y * 0.018, hy, p.y);
        mesa = mix(mesa, mix(sandFar(c.dusk), mesaCol(c.dusk), 0.5), talus * 0.5);
        float cx0 = p.x < c.size.x * 0.5 ? c.size.x * 0.185 : c.size.x * 0.865;
        float halfW = p.x < c.size.x * 0.5 ? c.size.x * 0.11 : c.size.x * 0.04;
        float lit = smoothstep(-halfW, halfW, (p.x - cx0) * sign(c.sun.x - cx0));
        mesa *= 0.78 + 0.24 * lit;
        col = mix(col, mesa, smoothstep(mt - 0.6, mt + 0.6, p.y));
    } else {
        col = groundAt(p, c);
        // the mirage: the sky, mirrored at the horizon, over the road and the sand nearest it
        float band = c.size.y * 0.05;
        float k = c.heat * (1.0 - smoothstep(0.0, band, p.y - hy));
        if (k > 0.002) {
            // sampled on a circle in phase, so the shimmer loops with the flow
            float2 orbit = float2(cos(6.2831853 * c.phase), sin(6.2831853 * c.phase)) * 0.9;
            float wob = vnoise(float2(p.x / 9.0, (p.y - hy) / 2.0) + orbit);
            float3 mirrored = skyAt(float2(p.x, hy - (p.y - hy) * 1.7), c);
            col = mix(col, mirrored, k * (0.55 + 0.35 * wob));
        }
    }

    // utility poles on the right, doubling in depth, with two wires between them
    float span = c.size.y - hy;
    float wb = c.size.x * 0.62;
    float z = 1.4;
    float px0 = 0.0;
    float py0 = 0.0;
    float pb0 = 0.0;
    for (int j = 0; j < 6; j++) {
        float base = hy + span / z;
        float xs = c.size.x * 0.5 + 1.55 * wb / z;
        float ph = 2.3 * span / z;
        float pw = max(0.016 * wb / z, 0.45);
        float barY = base - ph * 0.92;
        float bw = 0.3 * wb / z;
        float bh = max(0.02 * span / z, 0.45);
        float inX = 1.0 - smoothstep(pw - 0.5, pw + 0.5, abs(p.x - xs));
        float inY = step(base - ph, p.y) * (1.0 - smoothstep(base - 0.5, base + 0.5, p.y));
        float inBar = (1.0 - smoothstep(bh - 0.5, bh + 0.5, abs(p.y - barY))) * (1.0 - smoothstep(bw - 0.5, bw + 0.5, abs(p.x - xs)));
        float wire = 0.0;
        if (j > 0 && p.x > xs - bw && p.x < px0 + pb0) {
            for (int w = 0; w < 2; w++) {
                float side = w == 0 ? -0.8 : 0.8;
                float xa = xs + side * bw;
                float xb = px0 + side * pb0;
                float u = clamp((p.x - xa) / max(xb - xa, 0.001), 0.0, 1.0);
                float yw = mix(barY, py0, u) + 0.09 * span / z * 4.0 * u * (1.0 - u);
                wire = max(wire, 1.0 - smoothstep(0.25, 0.85, abs(p.y - yw)));
            }
        }
        float fog = exp(-(base - hy) / (c.size.y * 0.05));
        float3 pole = mix(poleCol(c.dusk), skyLow(c.dusk), 0.75 * fog);
        col = mix(col, pole, max(max(inX * inY, inBar), wire * 0.7));
        px0 = xs;
        py0 = barY;
        pb0 = bw;
        z *= 1.9;
    }
    return col;
}

} // namespace heatHazeWeather

[[ stitchable ]] half4 heatHazeWeatherScene(float2 p, half4 color, float2 size, float2 sun, float heat, float dusk,
                                            float phase, float3 touch, float dpr, float wide) {
    using namespace heatHazeWeather;
    Ctx c = { size, sun, heat, dusk, phase };
    float hy = size.y * HORIZON;
    float w = exp(-abs(p.y - hy - size.y * BAND_OFFSET) / (size.y * BAND_FOLD));
    float2 tv = p - touch.xy;
    float poke = touch.z * exp(-dot(tv, tv) / (TOUCH_RADIUS * TOUCH_RADIUS));
    float amp = heat * AMP * w + poke * TOUCH_AMP;
    float2 off = float2(0.0);
    if (amp > 0.01) {
        // two layers rising through the band; each is invisible as it wraps
        float wa = 1.0 - abs(1.0 - 2.0 * phase);
        float norm = 1.0 / sqrt(wa * wa + (1.0 - wa) * (1.0 - wa));
        float2 q = float2(p.x / CELL_X, p.y / CELL_Y);
        float2 qa = q + float2(0.0, phase * RISE);
        float2 qb = q + float2(3.7, fract(phase + 0.5) * RISE);
        float na = vnoise(qa) + 0.5 * vnoise(qa * 2.3 + 5.1);
        float nb = vnoise(qb) + 0.5 * vnoise(qb * 2.3 + 5.1);
        float ma = vnoise(qa * 0.7 + 11.0);
        float mb = vnoise(qb * 0.7 + 11.0);
        float nx = (mix(nb, na, wa) / 1.5 - 0.5) * norm;
        float ny = (mix(mb, ma, wa) - 0.5) * norm;
        off = float2(nx * 2.0, ny) * amp;
    }
    float3 col = scene(p + off, c);
    // the air itself: a little paler where it is hottest, a little brighter under the finger
    col = mix(col, skyLow(dusk), heat * 0.1 * w);
    col += poke * 0.035;

    float2 v = (p / size - 0.5) * float2(0.9, 1.1);
    col *= 1.0 - 0.14 * dot(v, v) * 2.0;
    col += (hsh(floor(p * dpr)) - 0.5) * 0.024;
    col = clamp(col, 0.0, 1.0);

    if (wide > 0.5) {
        float3 lin = pow(col, float3(2.2));
        float3 p3 = float3(0.8225 * lin.r + 0.1774 * lin.g, 0.0332 * lin.r + 0.9669 * lin.g, 0.0171 * lin.r + 0.0724 * lin.g + 0.9108 * lin.b);
        col = pow(max(p3, float3(0.0)), float3(1.0 / 2.2));
    }
    return half4(half3(col), 1.0h);
}
