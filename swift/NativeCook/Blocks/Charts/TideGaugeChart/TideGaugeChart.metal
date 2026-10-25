#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Tide Gauge Chart — the water under the tide curve (one pass over the card) and the screen behind
// everything. A step-for-step port of tideGaugeShader.ts (TIDE_SKSL and BACKDROP_SKSL); see there
// for the full description. The surface is the same function as `surfaceY` in
// TideGaugeChartMotion.swift. SwiftUI colours are sRGB, so the P3 conversion is left out; GLSL's
// smoothstep is reimplemented (sstep) because the Skia version calls it with reversed edges. The
// water's output is premultiplied.

namespace tideGaugeChartShader {

constant float TAU = 6.2831853;
constant float H_MIN = -0.4;
constant float H_MAX = 3.0;

float sstep(float e0, float e1, float x) {
    float t = clamp((x - e0) / (e1 - e0), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

float tideAt(float hour) {
    return 1.35 + 1.05 * cos(TAU * (hour - 14.2) / 12.23);
}

float surfaceAt(float x, float4 plot, float4 scrub, float phase) {
    float hour = (x - plot.x) / plot.z * 24.0;
    float y = plot.y + (1.0 - (tideAt(hour) - H_MIN) / (H_MAX - H_MIN)) * plot.w;
    float T = TAU * phase;
    float amp = 1.0 + 1.6 * scrub.z;
    y += amp * (1.1 * sin(x * 0.071 - 3.0 * T) + 0.7 * sin(x * 0.13 + 5.0 * T + 1.3) + 0.4 * sin(x * 0.29 - 8.0 * T + 2.1));
    float d = clamp((x - scrub.x) / (plot.z * 0.5), -1.0, 1.0);
    return y + scrub.y * d;
}

float hash1(float n) { return fract(sin(n * 12.9898) * 43758.5453); }
float hash2(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }

float vnoise(float x) {
    float i = floor(x);
    float f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(hash1(i), hash1(i + 1.0), f);
}

float caustic(float2 q, float T) {
    float v = sin(q.x * 1.7 + sin(q.y * 1.3 + 2.0 * T) * 1.4 + T)
            + sin(q.y * 2.1 + sin(q.x * 1.1 - 3.0 * T) * 1.2 - 2.0 * T)
            + sin((q.x + q.y) * 1.3 + sin(q.x * 0.7 + T) + 3.0 * T);
    v = v / 3.0;
    return pow(max(1.0 - abs(v), 0.0), 7.0);
}

}

/// plot: x, y, w, h (card points). scrub: x, tilt, chop, glow.
[[ stitchable ]] half4 tideGaugeChartWater(float2 p, half4 color, float4 plot, float4 scrub, float phase) {
    using namespace tideGaugeChartShader;
    if (p.x < plot.x - 1.0 || p.x > plot.x + plot.z + 1.0) return half4(0.0h);
    float s = surfaceAt(p.x, plot, scrub, phase);
    float depth = p.y - s;
    float bottom = plot.y + plot.w;
    if (p.y > bottom + 0.5) return half4(0.0h);
    // Soft sheen just above the surface (spray light), then nothing.
    if (depth < -1.5) {
        float above = exp(depth / 5.0) * 0.10;
        return half4(half3(float3(0.75, 0.92, 0.97) * above), half(above));
    }
    float T = TAU * phase;
    float k = clamp(depth / max(bottom - s, 24.0), 0.0, 1.0);

    float3 shallow = float3(0.561, 0.839, 0.910);
    float3 mid = float3(0.110, 0.498, 0.682);
    float3 deep = float3(0.039, 0.227, 0.361);
    float3 c = mix(shallow, mid, sstep(0.0, 0.32, k));
    c = mix(c, deep, sstep(0.28, 1.0, k));

    // Caustics fade with depth; light shafts lean with the slosh.
    float cz = caustic(p * 0.05, T) * (1.0 - sstep(0.0, 0.85, k));
    c += float3(0.55, 0.85, 0.95) * cz * 0.22;
    float shaft = sin((p.x + depth * (0.35 + scrub.y * 0.01)) * 0.045 + sin(T) * 0.6);
    c += float3(0.4, 0.7, 0.85) * sstep(0.6, 1.0, shaft) * 0.05 * (1.0 - k);

    // The scrubber's column of water reads slightly lighter, and glows on the high tide.
    float dx = p.x - scrub.x;
    float column = exp(-dx * dx / (2.0 * 14.0 * 14.0));
    c += float3(0.45, 0.80, 0.90) * column * (0.05 + 0.16 * scrub.w) * (1.0 - k * 0.6);

    // Foam: a broken white line riding the surface.
    float breakup = vnoise(p.x * 0.11 + sin(T) * 0.8 + 3.0) * 0.5 + vnoise(p.x * 0.31 - 4.0) * 0.5;
    float foamLine = sstep(2.4, 0.2, abs(depth - 1.0)) * (0.55 + 0.45 * breakup);
    float foamSpeck = sstep(0.72, 0.95, vnoise(p.x * 0.6 + depth * 0.9)) * sstep(7.0, 2.0, depth) * 0.5;
    c = mix(c, float3(0.93, 0.97, 0.98), clamp(foamLine * 0.85 + foamSpeck * (0.4 + scrub.z), 0.0, 1.0));

    // Darken toward the plot's floor (the sea bed).
    c *= 1.0 - 0.25 * sstep(0.75, 1.0, k);

    float a = sstep(-1.5, 0.8, depth) * 0.96;
    return half4(half3(c * a), half(a));
}

/// The screen behind everything: a deep harbour gradient (three stops), a vignette and 4 % grain.
[[ stitchable ]] half4 tideGaugeChartBackdrop(float2 p, half4 color, float2 size) {
    using namespace tideGaugeChartShader;
    float2 uv = p / size;
    float3 top = float3(0.051, 0.137, 0.188);
    float3 mid = float3(0.039, 0.122, 0.161);
    float3 bot = float3(0.024, 0.071, 0.098);
    float3 c = mix(top, mid, sstep(0.0, 0.45, uv.y));
    c = mix(c, bot, sstep(0.45, 1.0, uv.y));
    // A cool glow behind the chart, upper middle.
    float2 g = (uv - float2(0.5, 0.42)) * float2(1.0, size.y / size.x);
    c += float3(0.02, 0.07, 0.09) * exp(-dot(g, g) * 5.0);
    float2 v = uv - 0.5;
    c *= 1.0 - 0.38 * sstep(0.25, 0.85, length(v * float2(1.1, 0.9)));
    c += (hash2(floor(p)) - 0.5) * 0.04;
    return half4(half3(c), 1.0h);
}
