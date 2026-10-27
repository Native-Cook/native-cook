import { Skia } from '@shopify/react-native-skia';

/** The selector's orb (`tradingBotOrb` in TradingBotsMaterials.metal), output premultiplied. */
export const ORB = Skia.RuntimeEffect.Make(`uniform float2 size;
uniform float phase;
half4 tb_out(float3 col, float alpha) {
    col = clamp(col, 0.0, 1.0);
    return half4(half3(col * alpha), half(alpha));
}
// Hue around the shell, sampled from the reference every 45 degrees starting at the left.
float3 ringColour(float i) {
    if (i < 0.5) return float3(0.08, 0.27, 0.23);
    if (i < 1.5) return float3(0.08, 0.36, 0.19);
    if (i < 2.5) return float3(0.06, 0.3, 0.5);
    if (i < 3.5) return float3(0.06, 0.11, 0.76);
    if (i < 4.5) return float3(0.11, 0.12, 0.64);
    if (i < 5.5) return float3(0.13, 0.32, 0.6);
    if (i < 6.5) return float3(0.06, 0.44, 0.52);
    return float3(0.08, 0.31, 0.39);
}
half4 main(float2 position) {
    float2 p = (position / size - 0.5) * 2.0;
    float r = length(p);
    float alpha = 1.0 - smoothstep(0.985, 1.0, r);
    if (alpha <= 0.0) return half4(0);
    float z = sqrt(max(0.0, 1.0 - r * r));
    // (y points down, so -90 degrees is the top). The whole ring turns with phase.
    float th = atan(p.y, p.x) - phase;
    float u = fract((th + 3.141592653589793) / 6.283185307179586) * 8.0;
    float i0 = floor(u);
    float3 field = mix(ringColour(i0), ringColour(mod(i0 + 1.0, 8.0)), smoothstep(0.0, 1.0, fract(u)));
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
`)!;

/** The wallet card's holographic panel (`tradingBotWalletGradient`): 23 colours sampled from the reference. */
export const WALLET = Skia.RuntimeEffect.Make(`uniform float2 size;
// One colour sampled from the reference, weighted by its distance to uv.
void add(float2 uv, float2 at, float3 colour, inout float3 sum, inout float total) {
    float2 d = (uv - at) * float2(1.0, 1.35);
    float w = exp(-dot(d, d) / 0.018) + 1e-5;
    sum += colour * w;
    total += w;
}
half4 main(float2 position) {
    float2 uv = position / size;
    float3 sum = float3(0.0);
    float total = 0.0;
    add(uv, float2(0.03, 0.03), float3(0.55, 0.67, 0.92), sum, total);
    add(uv, float2(0.26, 0.03), float3(0.35, 0.55, 1.0), sum, total);
    add(uv, float2(0.48, 0.03), float3(0.32, 0.57, 0.98), sum, total);
    add(uv, float2(0.75, 0.03), float3(0.29, 0.67, 0.99), sum, total);
    add(uv, float2(0.98, 0.03), float3(0.27, 0.72, 0.97), sum, total);
    add(uv, float2(0.03, 0.15), float3(0.45, 0.53, 0.95), sum, total);
    add(uv, float2(0.26, 0.15), float3(0.44, 0.56, 0.98), sum, total);
    add(uv, float2(0.6, 0.15), float3(0.4, 0.64, 0.98), sum, total);
    add(uv, float2(0.98, 0.15), float3(0.41, 0.8, 0.98), sum, total);
    add(uv, float2(0.0, 0.3), float3(0.66, 0.6, 0.95), sum, total);
    add(uv, float2(1.0, 0.3), float3(0.52, 0.92, 0.88), sum, total);
    add(uv, float2(0.0, 0.43), float3(0.81, 0.66, 0.95), sum, total);
    add(uv, float2(1.0, 0.43), float3(0.56, 0.94, 0.81), sum, total);
    add(uv, float2(0.0, 0.49), float3(0.78, 0.56, 0.89), sum, total);
    add(uv, float2(0.18, 0.49), float3(0.68, 0.53, 0.92), sum, total);
    add(uv, float2(0.48, 0.49), float3(0.53, 0.56, 0.98), sum, total);
    add(uv, float2(0.79, 0.49), float3(0.34, 0.83, 0.95), sum, total);
    add(uv, float2(1.0, 0.49), float3(0.55, 0.91, 0.79), sum, total);
    add(uv, float2(0.0, 0.63), float3(0.6, 0.45, 0.72), sum, total);
    add(uv, float2(1.0, 0.63), float3(0.54, 0.82, 0.69), sum, total);
    add(uv, float2(0.0, 0.88), float3(0.36, 0.29, 0.44), sum, total);
    add(uv, float2(1.0, 0.88), float3(0.37, 0.52, 0.46), sum, total);
    add(uv, float2(0.5, 0.75), float3(0.45, 0.55, 0.85), sum, total);
    return half4(half3(sum / total), 1.0);
}
`)!;

export const DOTS = Skia.RuntimeEffect.Make(`uniform float spacing;
uniform float opacity;
half4 main(float2 position) {
    float2 cell = mod(position, spacing) - spacing * 0.5;
    float alpha = opacity * (1.0 - smoothstep(0.3, 0.6, length(cell)));
    return half4(half(alpha));
}
`)!;
