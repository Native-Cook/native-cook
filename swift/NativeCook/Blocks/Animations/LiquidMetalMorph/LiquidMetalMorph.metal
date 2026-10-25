#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The whole "Forge" scene in one full-screen pass: a graphite backdrop with grain and a vignette,
// and a blob of liquid chrome on it. A step-for-step port of the React Native SkSL
// (liquidMetalShader.ts); see there for the full description. Colours are sRGB, so the Display P3
// conversion at the end of the SkSL is left out.
//
// GLSL's `mod` is a floored modulo (not Metal's `fmod`), GLSL's `atan(y, x)` is Metal's
// `atan2(y, x)`, and GLSL's `smoothstep` is reimplemented so reversed edges behave the same.

namespace liquidMetalMorphShader {

struct Uniforms {
    float2 size;
    float2 center;
    float R;
    float3 w;
    float4 wob;
    float4 sats[4];
    float4 finger;
};

static inline float sstep(float e0, float e1, float x) {
    float t = clamp((x - e0) / (e1 - e0), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

static inline float gmod(float x, float y) {
    return x - y * floor(x / y);
}

static inline float hash(float2 p) {
    return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
}

static inline float smin(float a, float b, float k) {
    float h = max(k - abs(a - b), 0.0) / max(k, 0.0001);
    return min(a, b) - h * h * k * 0.25;
}

static inline float smax(float a, float b, float k) {
    return -smin(-a, -b, k);
}

// iq's uneven capsule: a circle of r1 at the origin joined to one of r2 at (0, h), y up.
static float sdUnevenCapsule(float2 p, float r1, float r2, float h) {
    p.x = abs(p.x);
    float b = (r1 - r2) / h;
    float a = sqrt(1.0 - b * b);
    float k = dot(p, float2(-b, a));
    if (k < 0.0) return length(p) - r1;
    if (k > a * h) return length(p - float2(0.0, h)) - r2;
    return dot(p, float2(a, b)) - r1;
}

// iq's star with four points, m in (2, 4): smaller m, thinner points.
static float sdStar4(float2 p, float r, float m) {
    float an = 3.14159265 / 4.0;
    float en = 3.14159265 / m;
    float2 acs = float2(cos(an), sin(an));
    float2 ecs = float2(cos(en), sin(en));
    float bn = gmod(atan2(p.x, p.y), 2.0 * an) - an;
    p = length(p) * float2(cos(bn), abs(sin(bn)));
    p -= r * acs;
    p += ecs * clamp(-dot(p, ecs), 0.0, r * acs.y / ecs.y);
    return length(p) * sign(p.x);
}

static float scene(float2 p, thread const Uniforms &u) {
    float R = u.R;
    float2 q = p - u.center;
    float d = 0.0;
    // Drop, tip up: flip y (the screen's y runs down), the round end a little below the centre.
    if (u.w.x != 0.0) d += u.w.x * sdUnevenCapsule(float2(q.x, -(q.y - 0.26 * R)), 0.9 * R, 0.15 * R, 1.42 * R);
    if (u.w.y != 0.0) d += u.w.y * (sdStar4(q, 1.18 * R, 2.55) - 0.13 * R);
    // The ring is a disc with a hole punched in it: blending into a disc and then opening the
    // hole reads as the metal tearing open.
    if (u.w.z != 0.0) d += u.w.z * (length(q) - 1.1 * R);
    float hole = 0.5 * R * sstep(0.35, 1.0, u.w.z);
    if (hole > 0.5) d = smax(d, hole - length(q), 12.0);
    float th = atan2(q.y, q.x);
    d += u.wob.z * (0.6 * sin(3.0 * th + u.wob.x) + 0.4 * sin(2.0 * th - u.wob.y));
    d += u.wob.w * sin(5.0 * th + 0.7);
    for (int i = 0; i < 4; i++) {
        float4 s = u.sats[i];
        if (s.z > 0.05) d = smin(d, length(q - s.xy) - s.z, s.w);
    }
    if (u.finger.z > 0.05) d = smin(d, length(p - u.finger.xy) - u.finger.z, u.finger.w);
    return d;
}

// The studio the chrome reflects, by the mirror direction (y down, as on screen).
static float3 studio(float y, float x) {
    float3 c0 = float3(0.043, 0.047, 0.063);
    float3 c1 = float3(0.604, 0.639, 0.710);
    float3 c2 = float3(0.949, 0.957, 0.973);
    float3 c3 = float3(0.227, 0.247, 0.290);
    float3 c = c3;
    c = mix(c, c2, sstep(-1.0, -0.62, y));
    c = mix(c, c1, sstep(-0.5, -0.05, y));
    c = mix(c, c0, sstep(0.12, 0.3, y));
    c = mix(c, c3, sstep(0.34, 0.85, y));
    // Two tall strip lights, cool on the left and warm on the right, brightest above the horizon.
    c += float3(0.86, 0.91, 1.0) * 0.5 * exp(-((x + 0.62) * (x + 0.62)) / 0.006) * sstep(0.35, -0.4, y);
    c += float3(1.0, 0.95, 0.88) * 0.32 * exp(-((x - 0.7) * (x - 0.7)) / 0.012) * sstep(0.45, -0.3, y);
    return c;
}

} // namespace liquidMetalMorphShader

using namespace liquidMetalMorphShader;

[[ stitchable ]] half4 liquidMetalMorph(
    float2 p, half4 color,
    float2 size, float2 center, float R, float3 w, float4 wob,
    device const float *sats, int satCount, float4 finger
) {
    Uniforms u;
    u.size = size;
    u.center = center;
    u.R = R;
    u.w = w;
    u.wob = wob;
    for (int i = 0; i < 4; i++) {
        u.sats[i] = satCount >= (i + 1) * 4
            ? float4(sats[i * 4], sats[i * 4 + 1], sats[i * 4 + 2], sats[i * 4 + 3])
            : float4(0.0);
    }
    u.finger = finger;

    // Graphite backdrop: #1e2026 in the middle to #121316 at the edges, a soft shadow on the
    // "floor" under the blob, 4 % grain and a vignette.
    float2 bq = (p - center) / size.y;
    float3 col = mix(float3(0.118, 0.125, 0.149), float3(0.071, 0.075, 0.086), sstep(0.0, 0.62, length(bq)));
    float2 sq = (p - center - float2(0.0, 1.62 * R)) / float2(1.25 * R, 0.24 * R);
    col *= 1.0 - 0.5 * exp(-dot(sq, sq));
    col *= 1.0 + (hash(floor(p)) - 0.5) * 0.08;
    float2 vq = p / size - 0.5;
    col *= 1.0 - 0.9 * dot(vq * float2(0.8, 0.6), vq * float2(0.8, 0.6));

    bool nearBlob = length(p - center) < 2.6 * R;
    bool nearFinger = finger.z > 0.05 && length(p - finger.xy) < finger.z + 30.0;
    if (nearBlob || nearFinger) {
        float d = scene(p, u);
        if (d < 1.5) {
            float e = 1.5;
            float2 g = float2(scene(p + float2(e, 0.0), u) - scene(p - float2(e, 0.0), u),
                              scene(p + float2(0.0, e), u) - scene(p - float2(0.0, e), u)) / (2.0 * e);
            float gl = length(g);
            g = gl > 0.0001 ? g / gl : float2(0.0, -1.0);
            // A rounded lip across the first W pt from the edge, on top of a gentle dome over the
            // whole blob (from the distance to the centre, so the star's creases never show).
            float W = 0.34 * R;
            float uu = max(-d, 0.0);
            float s = 1.0 - clamp(uu / W, 0.0, 1.0);
            float slope = min(s / sqrt(max(1.0 - s * s, 0.03)), 5.0);
            float2 dome = (p - center) / (1.7 * R);
            float dl = length(dome);
            dome *= min(dl, 0.75) / max(dl, 0.0001);
            float3 n = normalize(float3(g * slope + dome, 1.0));
            float3 r = float3(2.0 * n.z * n.x, 2.0 * n.z * n.y, 2.0 * n.z * n.z - 1.0);
            // A faint chromatic split where the surface turns away.
            float split = (1.0 - n.z) * 0.06;
            float3 metal = float3(studio(r.y - split, r.x).r, studio(r.y, r.x).g, studio(r.y + split, r.x).b);
            float fres = pow(1.0 - n.z, 3.0);
            metal = mix(metal, float3(0.86, 0.9, 0.97), fres * 0.35);
            float3 key = normalize(float3(-0.42, -0.62, 0.66));
            metal += float3(1.0, 0.98, 0.95) * pow(max(dot(r, key), 0.0), 70.0) * 1.1;
            // A thin darker line right at the silhouette gives the blob its edge.
            metal *= mix(0.5, 1.0, sstep(0.0, 2.5, uu));
            float a = clamp(0.5 - d / 1.5, 0.0, 1.0);
            col = mix(col, metal, a);
        }
    }

    col = clamp(col, 0.0, 1.0);
    col += (hash(p + 17.0) - 0.5) / 255.0;
    return half4(half3(col), 1.0h);
}
