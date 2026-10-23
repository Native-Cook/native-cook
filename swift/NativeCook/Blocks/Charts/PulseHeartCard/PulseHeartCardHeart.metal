#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The living heart, its halo and the ECG trace as one fragment program (heart-shader.ts).
//
// Shape: the pin's heart outline, measured at 3x and fitted as a polar cosine series around the
// heart's centre (theta from straight up, clockwise): r(theta) = sum c_k cos(k theta). The signed
// distance is approximated by (rho - r) / sqrt(1 + (r'/r)^2). Rest look: a closed-form colour fit per
// channel on the inside distance d, v = (y - 172) / 232 and u = (x - 201.2) / 122. The pink halo is the
// heart's red at a low alpha: a plateau to 40 pt and a ring peaking 37 pt out, gone by 53 pt. At rest
// the shader reproduces the pin; motion only adds. Output is premultiplied so the page shows through.
// All positions are "ref pt" (points of the 402 pt wide pin screen); `scale` is canvas pt per ref pt.

constant float2 pulseHeartCardCentre = float2(201.1667, 290.0);
constant float pulseHeartCardTop = 120.0;
constant float3 pulseHeartCardRed = float3(0.957, 0.18, 0.349);
// Polar cosine coefficients c_0...c_13 in ref pt; the halo uses the first four harmonics only.
constant float pulseHeartCardSeries[14] = {
    114.202, 15.914, -2.614, -14.183, -4.316, -4.079, -0.688, -1.807, -0.379, -1.124, -0.233, -0.716, -0.107, -0.477,
};
// Colour fit per channel: [a, b, c, e, f, g, h].
constant float pulseHeartCardFitR[7] = { 254.3, -16.0, -8.8, 2.4, 2.0, -0.8, -0.6 };
constant float pulseHeartCardFitG[7] = { 124.9, -22.2, 43.3, -37.6, -53.6, -27.7, -71.4 };
constant float pulseHeartCardFitB[7] = { 145.8, -31.5, 30.5, -24.5, -32.9, -26.7, -53.9 };
// ECG waveform gaussians [amp, centre, sigma]: P, Q, R (tip lands 4 pt under "BPM"), S, T.
constant float pulseHeartCardWaves[15] = {
    3.5, -0.2, 0.025,
    -3.0, -0.028, 0.009,
    26.0, 0.0, 0.0085,
    -8.0, 0.026, 0.01,
    6.0, 0.24, 0.04,
};
constant float pulseHeartCardBaseline = 362.0;
constant float pulseHeartCardSweep = 402.0;

// smoothstep that is also defined for edge0 > edge1 (the shader uses reversed edges).
static float pulseHeartCardSmooth(float edge0, float edge1, float x) {
    float t = clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

// Skia treats RuntimeEffect output as Display P3, so the React Native shader converts its sRGB fit
// before returning. SwiftUI color effects already interpret numeric output in the view's working
// colour space; applying that conversion here a second time desaturates the heart and halo.
static float3 pulseHeartCardToP3(float3 c) {
    return clamp(c, 0.0, 1.0);
}

static float pulseHeartCardRadiusAt(float cs, float sn, thread float &dr) {
    float r = pulseHeartCardSeries[0];
    dr = 0.0;
    float ck = cs;
    float sk = sn;
    r += pulseHeartCardSeries[1] * ck;
    dr -= pulseHeartCardSeries[1] * sk;
    for (int k = 2; k < 14; k += 1) {
        float t = ck * cs - sk * sn;
        sk = sk * cs + ck * sn;
        ck = t;
        r += pulseHeartCardSeries[k] * ck;
        dr -= float(k) * pulseHeartCardSeries[k] * sk;
    }
    return r;
}

// Approximate signed distance to the heart outline (ref pt, negative inside), q from the centre.
static float pulseHeartCardShapeDist(float2 q) {
    float rho = length(q) + 0.0001;
    float dr;
    float r = pulseHeartCardRadiusAt(-q.y / rho, q.x / rho, dr);
    return (rho - r) / sqrt(1.0 + (dr * dr) / (r * r));
}

// Inverse of the beat's deformation: screen offset from the centre -> undeformed offset.
static float2 pulseHeartCardUnwarp(float2 q, float4 deform) {
    float w = pulseHeartCardSmooth(20.0, -90.0, q.y);  // upper lobes
    float sx = (1.0 + deform.x) * (1.0 + deform.z) + deform.y * w;
    float sy = (1.0 + deform.x) * (1.0 - 0.8 * deform.z) + 0.4 * deform.y * w;
    return float2(q.x / sx, q.y / sy);
}

static float pulseHeartCardHaloRadius(float cs, float sn) {
    float r = pulseHeartCardSeries[0];
    float ck = cs;
    float sk = sn;
    r += pulseHeartCardSeries[1] * ck;
    for (int k = 2; k < 4; k += 1) {
        float t = ck * cs - sk * sn;
        sk = sk * cs + ck * sn;
        ck = t;
        r += pulseHeartCardSeries[k] * ck;
    }
    return r;
}

// Distance to the rounder halo heart (radial, ref pt).
static float pulseHeartCardHaloDist(float2 q, float4 deform) {
    float2 q0 = pulseHeartCardUnwarp(q, deform);
    float rho = length(q0) + 0.0001;
    return (rho - pulseHeartCardHaloRadius(-q0.y / rho, q0.x / rho)) * (1.0 + deform.x);
}

static float pulseHeartCardDistAt(float2 q, float4 deform) {
    return pulseHeartCardShapeDist(pulseHeartCardUnwarp(q, deform)) * (1.0 + deform.x);
}

// Soft ellipse at c with radii r, rotated by a (rad). edge >= 0: crisp rim at that inner share;
// edge < 0: gaussian falloff.
static float pulseHeartCardBlob(float2 q, float2 c, float2 r, float a, float edge) {
    float2 d = q - c;
    float cs = cos(a);
    float sn = sin(a);
    d = float2(cs * d.x + sn * d.y, -sn * d.x + cs * d.y) / r;
    float e = dot(d, d);
    return edge >= 0.0 ? pulseHeartCardSmooth(1.0, edge, e) : exp(-2.0 * e);
}

static float pulseHeartCardEcg(float u) {
    float sum = 0.0;
    for (int i = 0; i < 5; i += 1) {
        float amp = pulseHeartCardWaves[i * 3];
        float centre = pulseHeartCardWaves[i * 3 + 1];
        float sigma = pulseHeartCardWaves[i * 3 + 2];
        sum += amp * exp(-((u - centre) * (u - centre)) / (2.0 * sigma * sigma));
    }
    return sum;
}

static float pulseHeartCardRipple(float d, float tau) {
    if (tau < 0.0 || tau > 0.75) return 0.0;
    float o = 4.0 + 150.0 * tau;
    float w = 4.0 + 12.0 * tau;
    float k = 1.0 - tau / 0.75;
    return 0.11 * k * k * pulseHeartCardSmooth(0.0, 0.03, tau) * exp(-((d - o) * (d - o)) / (w * w));
}

static float pulseHeartCardFit(constant float *f, float e1, float e2, float v, float u) {
    return f[0] + f[1] * e1 + f[2] * e1 * v + f[3] * e2 + f[4] * e2 * v + f[5] * v + f[6] * u * u;
}

// deform: body, lobe, squash, lift. shine: gloss, light, push, flash. taus: seconds since the last two
// lubs (ripples). trace: phi, traceStart, traceEnd.
[[ stitchable ]] half4 pulseHeartCardHeart(float2 pos, half4 color, float scale, float4 deform, float4 shine, float2 taus, float3 trace) {
    float2 p = pos / scale + float2(0.0, pulseHeartCardTop);
    float2 q = p - pulseHeartCardCentre - float2(0.0, deform.w);
    float sd = pulseHeartCardDistAt(q, deform);

    // Halo: the pin's pink ring, pushed out by the beat, plus the pressure ripples.
    float d = pulseHeartCardHaloDist(q, deform) - shine.z;
    float vy = clamp((p.y - 172.0) / 232.0, 0.0, 1.0);
    float halo = (7.0 * (1.0 - pulseHeartCardSmooth(36.0, 56.0, d)) + 7.0 * exp(-((d - 36.0) * (d - 36.0)) / 110.0)) / 193.0;
    halo *= (1.12 - 0.25 * vy) * pulseHeartCardSmooth(-1.0, 3.0, sd);
    float rip = (pulseHeartCardRipple(sd, taus.x) + pulseHeartCardRipple(sd, taus.y))
        * pulseHeartCardSmooth(120.0, 160.0, p.y) * pulseHeartCardSmooth(500.0, 450.0, p.y);
    float aHalo = clamp(halo + rip * pulseHeartCardSmooth(-1.0, 3.0, sd), 0.0, 1.0);
    float4 o = float4(pulseHeartCardToP3(pulseHeartCardRed) * aHalo, aHalo);

    // Body: the pin's matte gel shading, carried by the deformation.
    float bodyA = 1.0 - pulseHeartCardSmooth(-2.0, 2.0, sd);
    if (bodyA > 0.0) {
        float2 q0 = pulseHeartCardUnwarp(q, deform);
        float din = max(-sd / (1.0 + deform.x), 0.0);
        float v = (q0.y + pulseHeartCardCentre.y - 172.0) / 232.0;
        float u = q0.x / 122.0;
        float e1 = exp(-din / 3.0);
        float e2 = exp(-din / 15.0);
        float3 col = float3(
            pulseHeartCardFit(pulseHeartCardFitR, e1, e2, v, u),
            pulseHeartCardFit(pulseHeartCardFitG, e1, e2, v, u),
            pulseHeartCardFit(pulseHeartCardFitB, e1, e2, v, u)) / 255.0;

        // Wet gloss: painted highlights on the lobes, carried by the deformation (q0) and slid by
        // the beat's light shift; a rim sheen on the upper-left edge. Zero at rest (the pin is matte).
        float gloss = shine.x;
        if (gloss > 0.002) {
            float2 slide = shine.y * float2(40.0, 22.0);
            float spec = 0.85 * pulseHeartCardBlob(q0, float2(-62.0, -66.0) + slide, float2(24.0, 10.5), -0.7, 0.3)
                       + 0.45 * pulseHeartCardBlob(q0, float2(58.0, -72.0) + 0.5 * slide, float2(9.0, 4.5), 0.6, 0.3)
                       + 0.14 * pulseHeartCardBlob(q0, float2(-30.0, -40.0) + slide, float2(78.0, 56.0), -0.5, -1.0);
            float rim = exp(-din / 3.5) * clamp(dot(normalize(q0 + float2(0.0, 0.0001)), float2(-0.55, -0.83)), 0.0, 1.0) * 0.32;
            col = mix(col, float3(0.86, 0.06, 0.24), 0.10 * gloss);   // the muscle flushes deeper
            col += (spec + rim) * gloss;
        }
        // R-peak bloom: the heart lights from within for ~200 ms (the reading's flash).
        float2 bd = q0 - float2(0.0, -6.0);
        float bloom = shine.w * 0.2 * exp(-dot(bd, bd) / 7200.0);
        col += bloom * float3(1.0, 0.82, 0.86);
        float3 pc = pulseHeartCardToP3(col);
        o = float4(pc * bodyA, bodyA) + o * (1.0 - bodyA);
    }

    // ECG trace: a phosphor line swept left to right once per beat, R-peak at the heart's centre.
    float phi = trace.x;
    if (p.y > pulseHeartCardBaseline - 40.0 && p.y < pulseHeartCardBaseline + 22.0 && phi > trace.y) {
        float W = pulseHeartCardSweep;
        float penU = fract(phi + 0.5) - 0.5;
        float u = (p.x - pulseHeartCardCentre.x) / W;
        float age = fract(penU - u);
        float drawnAt = phi - age;
        float fadeOut = 1.0 - pulseHeartCardSmooth(trace.z, trace.z + 0.6, phi);
        float vis = step(trace.y, drawnAt) * step(drawnAt, trace.z) * pulseHeartCardSmooth(0.97, 0.9, age);
        float alpha = vis * exp(-1.25 * age) * fadeOut;
        float h = 0.0015;
        float yT = pulseHeartCardBaseline - pulseHeartCardEcg(u);
        float slope = (pulseHeartCardEcg(u - h) - pulseHeartCardEcg(u + h)) / (2.0 * h * W);
        float dist = abs(p.y - yT) / sqrt(1.0 + slope * slope);
        float core = 1.0 - pulseHeartCardSmooth(0.95, 1.8, dist);
        float glow = exp(-(dist * dist) / 26.0) * 0.4;
        float penOn = step(trace.y, phi) * step(phi, trace.z) * fadeOut;
        float2 head = float2(pulseHeartCardCentre.x + penU * W, pulseHeartCardBaseline - pulseHeartCardEcg(penU));
        float2 hv = p - head;
        float hd = dot(hv, hv);
        float pen = penOn * (exp(-hd / 7.0) + 0.45 * exp(-hd / 70.0));
        float a = clamp(max(core, glow) * alpha + pen, 0.0, 1.0);
        if (a > 0.001) {
            float3 tc = mix(pulseHeartCardRed * 0.92, float3(1.0), bodyA);
            o = float4(pulseHeartCardToP3(tc) * a, a) + o * (1.0 - a);
        }
    }
    return half4(o);
}
