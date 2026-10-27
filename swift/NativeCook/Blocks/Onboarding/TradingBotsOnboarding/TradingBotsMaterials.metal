#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Bot bodies for Trading Bots Onboarding. Each takes the view size and a phase in
// seconds; p runs from -1 to 1 across the square, y pointing down.

static half4 tb_out(float3 col, float alpha) {
    col = clamp(col, 0.0, 1.0);
    return half4(half3(col * alpha), half(alpha));
}

// Iridescent glass orb (@ScalpX on the selector): a navy core inside a shell whose
// colour turns slowly, green, periwinkle, blue, teal, with a frosted rim.
[[ stitchable ]] half4 tradingBotOrb(float2 position, half4 input, float2 size, float phase) {
    float2 p = (position / size - 0.5) * 2.0;
    float r = length(p);
    float alpha = 1.0 - smoothstep(0.985, 1.0, r);
    if (alpha <= 0.0) return half4(0);
    float z = sqrt(max(0.0, 1.0 - r * r));
    // Hue around the shell, sampled from the reference every 45° starting at the left
    // (y points down, so -90° is the top). The whole ring turns with `phase`.
    const float3 ring[8] = {
        float3(0.08, 0.27, 0.23), float3(0.08, 0.36, 0.19), float3(0.06, 0.30, 0.50), float3(0.06, 0.11, 0.76),
        float3(0.11, 0.12, 0.64), float3(0.13, 0.32, 0.60), float3(0.06, 0.44, 0.52), float3(0.08, 0.31, 0.39)
    };
    float th = atan2(p.y, p.x) - phase;
    float u = fract((th + M_PI_F) / (2.0 * M_PI_F)) * 8.0;
    int i0 = int(floor(u)) % 8;
    float3 field = mix(ring[i0], ring[(i0 + 1) % 8], smoothstep(0.0, 1.0, fract(u)));
    // navy-black heart: colour only gathers in the outer half of the ball
    float k = smoothstep(0.32, 0.86, r);
    float3 col = mix(float3(0.0, 0.0, 0.07), field * 1.35, k);
    // frosted shell: the last tenth of the radius goes milky grey-green
    float frost = smoothstep(0.76, 0.985, r);
    col = mix(col, field * 0.4 + float3(0.36, 0.42, 0.42), frost * 0.72);
    // lavender edge light on the right
    float rim = exp(-pow((r - 0.968) / 0.03, 2.0));
    col += rim * float3(0.32, 0.24, 0.50) * smoothstep(-0.2, 0.9, p.x);
    // faint key from the upper left
    float key = exp(-3.0 * dot(p - float2(-0.5, -0.62), p - float2(-0.5, -0.62)));
    col += key * float3(0.05, 0.06, 0.07);
    col *= 0.92 + 0.08 * z;
    return tb_out(col, alpha);
}

// The wallet card's holographic panel, interpolated between colours sampled from the
// reference: periwinkle and blue at the top, lavender down the left, mint down the right.
[[ stitchable ]] half4 tradingBotWalletGradient(float2 position, half4 input, float2 size) {
    float2 uv = position / size;
    const float2 at[23] = {
        float2(0.03, 0.03), float2(0.26, 0.03), float2(0.48, 0.03), float2(0.75, 0.03), float2(0.98, 0.03),
        float2(0.03, 0.15), float2(0.26, 0.15), float2(0.60, 0.15), float2(0.98, 0.15),
        float2(0.0, 0.30), float2(1.0, 0.30), float2(0.0, 0.43), float2(1.0, 0.43),
        float2(0.0, 0.49), float2(0.18, 0.49), float2(0.48, 0.49), float2(0.79, 0.49), float2(1.0, 0.49),
        float2(0.0, 0.63), float2(1.0, 0.63), float2(0.0, 0.88), float2(1.0, 0.88), float2(0.5, 0.75)
    };
    const float3 colour[23] = {
        float3(0.55, 0.67, 0.92), float3(0.35, 0.55, 1.0), float3(0.32, 0.57, 0.98), float3(0.29, 0.67, 0.99), float3(0.27, 0.72, 0.97),
        float3(0.45, 0.53, 0.95), float3(0.44, 0.56, 0.98), float3(0.40, 0.64, 0.98), float3(0.41, 0.80, 0.98),
        float3(0.66, 0.60, 0.95), float3(0.52, 0.92, 0.88), float3(0.81, 0.66, 0.95), float3(0.56, 0.94, 0.81),
        float3(0.78, 0.56, 0.89), float3(0.68, 0.53, 0.92), float3(0.53, 0.56, 0.98), float3(0.34, 0.83, 0.95), float3(0.55, 0.91, 0.79),
        float3(0.60, 0.45, 0.72), float3(0.54, 0.82, 0.69), float3(0.36, 0.29, 0.44), float3(0.37, 0.52, 0.46), float3(0.45, 0.55, 0.85)
    };
    float3 sum = float3(0.0);
    float total = 0.0;
    for (int i = 0; i < 23; i++) {
        float2 d = (uv - at[i]) * float2(1.0, 1.35);
        float w = exp(-dot(d, d) / 0.018) + 1e-5;
        sum += colour[i] * w;
        total += w;
    }
    return half4(half3(sum / total), 1.0);
}
