#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The mixing bowl, seen from above: a cream glazed ceramic bowl with a rolled lip, its shadow on
// the table, and the paint inside. A step-for-step port of the React Native SkSL
// (paintMixerShader.ts), minus its Display P3 conversion.
//
// Paint colour at a point is a weighted mean of absorbance (−ln of each channel): white base paint
// with weight `baseA.w`, and each drop with weight `dA.w` times its concentration there. A drop's
// concentration is a Gaussian blob around where it landed, sampled in a frame turned back by the
// drop's winding, with noisy edges and a darker rim where pigment pools; stirring (`dP.z`, the
// mixed share) spreads it towards an even coat. Ripples tilt the surface. The wash-back is a white
// front growing from the middle with a reverse twist. Output is premultiplied; outside the bowl
// only its shadow is drawn. Metal's pow is undefined for a negative base, so squares are x * x.

namespace paintMixerPickerShader {

struct Bowl {
    float2 center;
    float rim;
    float paintR;
    float unit;
};

static inline float sq(float x) { return x * x; }

static inline float sstep(float e0, float e1, float x) {
    float t = clamp((x - e0) / (e1 - e0), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

static inline float h21(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }

static float vnoise(float2 q) {
    float2 i = floor(q);
    float2 f = fract(q);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(h21(i), h21(i + float2(1.0, 0.0)), f.x), mix(h21(i + float2(0.0, 1.0)), h21(i + float2(1.0, 1.0)), f.x), f.y);
}

static inline float2 turn(float2 v, float a) {
    float c = cos(a);
    float s = sin(a);
    return float2(c * v.x - s * v.y, s * v.x + c * v.y);
}

// Adds one drop's absorbance at p into acc (xyz = weighted absorbance, w = weight).
static float4 addDrop(float4 acc, float2 p, float4 da, float4 dp, thread const Bowl &b) {
    if (da.w < 0.0005) return acc;
    float2 d = p - b.center;
    float r = length(d);
    float k = clamp(r / b.paintR, 0.0, 1.0);
    // Turn the point back by the winding: the middle has turned more than the rim.
    float2 q = b.center + turn(d, -dp.w * (1.0 - k * k) * 0.9);
    q += (float2(vnoise(q * 0.045 / b.unit), vnoise(q * 0.045 / b.unit + 7.3)) - 0.5) * 18.0 * b.unit;
    float2 off = q - dp.xy;
    float sig = (13.0 + 40.0 * dp.z) * b.unit;
    float c = exp(-dot(off, off) / (2.0 * sig * sig));
    // A slightly darker rim where the pigment pools at the blob's edge.
    float pool = c * (1.0 - c) * 4.0 * (1.0 - dp.z);
    float spread = mix(c, 1.0, pow(max(dp.z, 0.0), 2.2));
    float wgt = da.w * spread * (1.0 + 0.25 * pool);
    return acc + float4(da.xyz * wgt * (1.0 + 0.18 * pool), wgt);
}

static float rippleAt(float2 p, float4 rp, float unit) {
    if (rp.w <= 0.0) return 0.0;
    float dd = length(p - rp.xy);
    float front = rp.z * 120.0 * unit + 4.0 * unit;
    float env = exp(-sq((dd - front) / (11.0 * unit))) * exp(-rp.z * 2.4) * rp.w;
    return sin((dd - front) * 0.42 / unit) * env;
}

} // namespace paintMixerPickerShader

using namespace paintMixerPickerShader;

[[ stitchable ]] half4 paintMixerPicker(
    float2 p,
    half4 color,
    float2 center,
    float rim,
    float paintR,
    float unit,
    float4 baseA,
    float4 dA0, float4 dA1, float4 dA2, float4 dA3,
    float4 dP0, float4 dP1, float4 dP2, float4 dP3,
    float4 rip0, float4 rip1, float4 rip2,
    float2 wash
) {
    Bowl b = { center, rim, paintR, unit };
    float u = unit;
    float2 d = p - center;
    float r = length(d);
    float2 L = normalize(float2(-0.55, -0.83));

    // Shadow on the table: a soft ambient one and a tight contact one.
    float amb = 1.0 - sstep(rim - 6.0 * u, rim + 26.0 * u, length(p - center - float2(7.0, 14.0) * u));
    float contact = 1.0 - sstep(rim - 2.0 * u, rim + 5.0 * u, length(p - center - float2(1.0, 2.5) * u));
    float shadowA = amb * 0.18 + contact * 0.22;

    if (r > rim + 0.8) {
        return half4(half3(0.0), half(shadowA));
    }

    float3 col = float3(0.0);
    float lipIn = rim * 0.88;
    if (r < paintR + 1.0) {
        // Ripples displace the sample and tilt the surface.
        float wv = rippleAt(p, rip0, u) + rippleAt(p, rip1, u) + rippleAt(p, rip2, u);
        float2 pp = p + d / max(r, 1.0) * wv * 2.0 * u;
        float4 acc = float4(baseA.xyz * baseA.w, baseA.w);
        acc = addDrop(acc, pp, dA0, dP0, b);
        acc = addDrop(acc, pp, dA1, dP1, b);
        acc = addDrop(acc, pp, dA2, dP2, b);
        acc = addDrop(acc, pp, dA3, dP3, b);
        float3 a = acc.xyz / acc.w;
        // Wash-back: fresh white paint spreading from the middle in a reverse spiral.
        if (wash.x > 0.0) {
            float ang = atan2(d.y, d.x);
            float edge = wash.x + 14.0 * u * sin(2.0 * ang + r * 0.06 / u - wash.y);
            float fresh = 1.0 - sstep(edge - 22.0 * u, edge + 10.0 * u, r);
            a = mix(a, baseA.xyz, fresh);
        }
        col = exp(-a);
        // Gloss: a soft window highlight up and left, bent by the ripples; a dark meniscus at the rim.
        float2 hl = (p - center - float2(-0.34, -0.40) * paintR) / float2(paintR * 0.36, paintR * 0.22);
        float2 hb = max(abs(hl) - float2(0.35, 0.15), 0.0);
        float gloss = (1.0 - sstep(0.0, 0.55, length(hb))) * 0.09 + wv * 0.10;
        col += float3(gloss);
        col *= 1.0 - 0.16 * sstep(paintR - 5.0 * u, paintR, r);
    }
    if (r >= paintR - 0.5) {
        // Inner wall (concave: the far side from the light is lit) and the rolled lip.
        float3 glaze = float3(0.957, 0.937, 0.902);
        float3 c2;
        float2 n = d / max(r, 1.0);
        if (r < lipIn) {
            float k = (r - paintR) / (lipIn - paintR);
            float lit = 0.80 + 0.17 * dot(n, -L) + 0.06 * k;
            c2 = glaze * lit;
            c2 *= 1.0 - 0.18 * (1.0 - sstep(0.0, 0.35, k));
        } else {
            float k = (r - lipIn) / (rim - lipIn);
            float slope = k * 2.0 - 1.0;
            float lit = 0.92 + 0.22 * dot(n * slope, -L) - 0.10 * slope * slope;
            c2 = glaze * lit;
            c2 += float3(1.0) * pow(max(0.0, dot(n * slope, -L)), 8.0) * 0.18;
            c2 *= 1.0 - 0.22 * sstep(0.75, 1.0, k);
        }
        // Speckled stoneware.
        float speck = step(0.985, h21(floor(p * 1.3 / u)));
        c2 *= 1.0 - speck * 0.25;
        float blend = sstep(paintR - 0.5, paintR + 1.0, r);
        col = r < paintR + 1.0 ? mix(col, c2, blend) : c2;
    }
    float a = 1.0 - sstep(rim - 0.8, rim + 0.8, r);
    col = clamp(col, 0.0, 1.0);
    float outA = a + shadowA * (1.0 - a);
    return half4(half3(col * a), half(outA));
}
