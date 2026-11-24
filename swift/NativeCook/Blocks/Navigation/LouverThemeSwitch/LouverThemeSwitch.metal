// Louver Theme Switch — the turn, one fragment shader over the two snapshots.
//
// The screen is cut into 14 horizontal slats. Slat i turns by θ around its long axis, at
// y0 = (i + ½)·h. A point s along the slat sits at (X, y0 + s·cos θ, s·sin θ), seen by a camera D away
// from the screen's centre (cx, cy). Inverting that projection for a pixel (px, py):
//   s = D (py − y0) / (D cos θ + (py − cy) sin θ)      k = D / (D − s sin θ)      X = cx + (px − cx) / k
// The face towards the camera shows the light page at (X, y0 + s), or the dark page at (X, y0 − s),
// so at θ = π the dark page reads the right way up. A slat can overhang its band in perspective, so
// each pixel asks its own slat and both neighbours and paints them far to near, over a dark cavity.
// At rest the shader returns the page itself, so swapping it for the live view shows no change.
//
// Colour: the snapshots' sRGB bytes are mixed as written and the layer is sRGB, like the RN effect.

#include <metal_stdlib>
using namespace metal;

struct LouverThemeSwitchU {
    float4 view;    // width, height (pt), pixels per pt, -
    float4 turn;    // amount 0…1, direction ±1, -, -
    float4 cavity;  // the cavity's tone (sRGB), -
};

struct LouverThemeSwitchVaryings {
    float4 position [[position]];
};

vertex LouverThemeSwitchVaryings louverThemeSwitchVertex(uint i [[vertex_id]]) {
    float2 p = float2(float((i << 1u) & 2u), float(i & 2u));
    LouverThemeSwitchVaryings out;
    out.position = float4(p * 2.0 - 1.0, 0.0, 1.0);
    return out;
}

constant float LTS_SLATS = 14.0;
constant float LTS_SPREAD = 0.6;
constant float LTS_BACK = 1.3725;
constant float LTS_CAMERA = 1.35;
constant float LTS_OVERLAP = 0.5;
constant float LTS_LIGHT_Y = -0.52;
constant float LTS_LIGHT_Z = 0.85;
constant float LTS_AMBIENT = 0.46;
constant float LTS_GLOSS = 0.08;
constant float LTS_GLOSS_POWER = 48.0;
constant float LTS_EDGE = 0.07;
constant float LTS_EDGE_WIDTH = 1.2;
constant float LTS_CONTACT = 0.38;
constant float LTS_CONTACT_FOLD = 3.2;
constant float LTS_REST = 0.0005;

static float ltsEaseBack(float u) {
    float c = LTS_BACK;
    float v = 2.0 * u;
    if (u < 0.5) { return 0.5 * v * v * ((c + 1.0) * v - c); }
    float w = v - 2.0;
    return 0.5 * (w * w * ((c + 1.0) * w + c) + 2.0);
}

static float ltsAngle(float k, float2 turn) {
    float p = turn.y > 0.0 ? turn.x : 1.0 - turn.x;
    float e = ltsEaseBack(clamp(p * (1.0 + LTS_SPREAD) - LTS_SPREAD * k, 0.0, 1.0));
    return M_PI_F * (turn.y > 0.0 ? e : 1.0 - e);
}

static float3 ltsSample(texture2d<float> page, sampler smp, float2 uv, float2 res) {
    return page.sample(smp, clamp(uv, float2(0.5), res - float2(0.5)) / res).rgb;
}

// slat i at pixel p: colour with coverage in alpha, and how near the camera it is there
static float4 ltsSlat(float i, float2 p, constant LouverThemeSwitchU &u, texture2d<float> light, texture2d<float> dark, sampler smp, thread float &depth) {
    depth = -1.0e6;
    float2 res = u.view.xy;
    float px = u.view.z;
    if (i < 0.0 || i > LTS_SLATS - 1.0) { return float4(0.0); }
    float h = res.y / LTS_SLATS;
    float y0 = (i + 0.5) * h;
    float th = ltsAngle(i / (LTS_SLATS - 1.0), u.turn.xy);
    float sn = sin(th);
    float cs = cos(th);
    float cx = res.x * 0.5;
    float cy = res.y * 0.5;
    float cam = res.y * LTS_CAMERA;
    float denom = cam * cs + (p.y - cy) * sn;
    if (abs(denom) < 0.001) { return float4(0.0); }
    float s = cam * (p.y - y0) / denom;
    float reach = h * 0.5 + LTS_OVERLAP;
    float slope = abs(cam * (cam * cs + (y0 - cy) * sn) / (denom * denom));
    float cover = clamp((reach - abs(s)) / max(slope, 0.001) * px + 0.5, 0.0, 1.0);
    if (cover <= 0.0) { return float4(0.0); }
    float k = cam / (cam - s * sn);
    float wx = cx + (p.x - cx) / k;
    cover *= clamp(min(wx, res.x - wx) * px + 0.5, 0.0, 1.0);
    if (cover <= 0.0) { return float4(0.0); }
    depth = s * sn;

    float3 normal = float3(0.0, -sn, cs);
    float3 toEye = float3(cx - wx, cy - (y0 + s * cs), cam - s * sn);
    bool faceUp = dot(normal, toEye) > 0.0;
    if (!faceUp) { normal = -normal; }
    float3 col = faceUp ? ltsSample(light, smp, float2(wx, y0 + s), res) : ltsSample(dark, smp, float2(wx, y0 - s), res);

    float3 sun = normalize(float3(0.0, LTS_LIGHT_Y, LTS_LIGHT_Z));
    float lit = max(dot(normal, sun), 0.0) / sun.z;
    float turned = sn * sn;
    col *= min(LTS_AMBIENT + (1.0 - LTS_AMBIENT) * lit, 1.0);
    float3 mid = normalize(sun + float3(0.0, 0.0, 1.0));
    col += pow(max(dot(normal, mid), 0.0), LTS_GLOSS_POWER) * LTS_GLOSS * turned;
    float rim = reach - abs(s);
    col += smoothstep(LTS_EDGE_WIDTH, 0.0, rim) * LTS_EDGE * abs(sn);
    col *= 1.0 - LTS_CONTACT * exp(-max(rim, 0.0) / LTS_CONTACT_FOLD) * clamp(abs(sn) * 1.4, 0.0, 1.0);
    return float4(clamp(col, 0.0, 1.0), cover);
}

fragment float4 louverThemeSwitchTurn(LouverThemeSwitchVaryings in [[stage_in]],
                                      constant LouverThemeSwitchU &u [[buffer(0)]],
                                      texture2d<float> light [[texture(0)]],
                                      texture2d<float> dark [[texture(1)]],
                                      sampler smp [[sampler(0)]]) {
    float2 res = u.view.xy;
    float2 p = in.position.xy / u.view.z;
    if (u.turn.x <= LTS_REST) { return float4(ltsSample(light, smp, p, res), 1.0); }
    if (u.turn.x >= 1.0 - LTS_REST) { return float4(ltsSample(dark, smp, p, res), 1.0); }
    float band = floor(p.y / (res.y / LTS_SLATS));
    float da; float db; float dc;
    float4 a = ltsSlat(band - 1.0, p, u, light, dark, smp, da);
    float4 b = ltsSlat(band, p, u, light, dark, smp, db);
    float4 c = ltsSlat(band + 1.0, p, u, light, dark, smp, dc);
    // far to near
    if (da > db) { float4 q = a; a = b; b = q; float r = da; da = db; db = r; }
    if (db > dc) { float4 q = b; b = c; c = q; float r = db; db = dc; dc = r; }
    if (da > db) { float4 q = a; a = b; b = q; float r = da; da = db; db = r; }
    float shade = 0.72 + 0.28 * abs(p.y / res.y - 0.5) * 2.0;
    float3 col = u.cavity.rgb * (1.2 - 0.4 * shade);
    col = mix(col, a.rgb, a.a);
    col = mix(col, b.rgb, b.a);
    col = mix(col, c.rgb, c.a);
    return float4(col, 1.0);
}
