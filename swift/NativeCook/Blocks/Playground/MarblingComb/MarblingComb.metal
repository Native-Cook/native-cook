#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The two small shaders of Marbling Comb, ported line for line from the React Native SkSL. Both
// return premultiplied colours: white light where the value is positive, black shade where it is
// negative, drawn over the paint.

static float marblingCombHash(float2 p) {
    float3 q = fract(float3(p.x, p.y, p.x) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

static float marblingCombNoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = marblingCombHash(i);
    float b = marblingCombHash(i + float2(1.0, 0.0));
    float c = marblingCombHash(i + float2(0.0, 1.0));
    float d = marblingCombHash(i + float2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

// Light on the size: a broad sheen drifting slowly, with a brighter core, pigment grain, and a soft
// vignette towards the tray's rim.
[[ stitchable ]] half4 marblingCombWater(float2 p, float2 size, float phase) {
    float2 uv = p / size;
    float band = uv.x * 0.62 + uv.y * 0.42 - 0.36 - 0.05 * sin(phase);
    float sheen = exp(-band * band / 0.02) * 0.075 + exp(-band * band / 0.0012) * 0.035;
    float grain = (marblingCombHash(floor(p * 2.0)) - 0.5) * 0.05;
    float2 c = (uv - 0.5) * float2(1.0, size.y / size.x * 0.62);
    float shade = smoothstep(0.22, 0.86, length(c)) * 0.46;
    float light = sheen + grain;
    float w = max(light, 0.0);
    float k = max(-light, 0.0) + shade;
    return half4(w, w, w, w + (1.0 - w) * min(k, 1.0));
}

// The paper's fine tooth plus long, faint fibres.
[[ stitchable ]] half4 marblingCombPaper(float2 p, float strength) {
    float tooth = marblingCombHash(floor(p * 2.0)) - 0.5;
    float fibre = marblingCombNoise(float2(p.x * 0.06, p.y * 0.9)) - 0.5;
    float cloud = marblingCombNoise(p * 0.035) - 0.5;
    float v = (tooth * 0.5 + fibre * 0.7 + cloud * 0.9) * strength;
    float w = max(v, 0.0);
    float k = max(-v, 0.0);
    return half4(w, w, w, w + (1.0 - w) * k);
}
