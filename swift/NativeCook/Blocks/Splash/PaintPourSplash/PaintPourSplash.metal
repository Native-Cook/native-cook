#include <metal_stdlib>
using namespace metal;

struct PaintPourSplashVertexOut { float4 position [[position]]; };
vertex PaintPourSplashVertexOut paintPourSplashVertex(uint id [[vertex_id]]) {
    const float2 vertices[] = {float2(-1, -1), float2(3, -1), float2(-1, 3)};
    return {float4(vertices[id], 0, 1)};
}
float paintPourSplashHash(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}
float paintPourSplashNoise(float2 p) {
    float2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(paintPourSplashHash(i), paintPourSplashHash(i + float2(1, 0)), f.x),
               mix(paintPourSplashHash(i + float2(0, 1)), paintPourSplashHash(i + float2(1, 1)), f.x), f.y);
}
float3 paintPourSplashSample(texture2d<float> image, float2 uv) {
    constexpr sampler s(coord::normalized, address::clamp_to_edge, filter::linear);
    return pow(max(image.sample(s, uv).rgb, 0.0), float3(2.2));
}
fragment float4 paintPourSplashFragment(PaintPourSplashVertexOut in [[stage_in]],
    texture2d<float> image [[texture(0)]], constant float4 &u [[buffer(0)]]) {
    float fitW = u.y * 708.0 / 1536.0;
    float2 uv = float2((in.position.x - (u.x - fitW) * 0.5) / fitW, in.position.y / u.y);
    float3 base = paintPourSplashSample(image, uv);
    float sat = max(max(base.r, base.g), base.b) - min(min(base.r, base.g), base.b);
    float stream = smoothstep(0.12, 0.3, sat) * (1 - smoothstep(0.78, 0.83, uv.y)) * smoothstep(0.12, 0.2, uv.y);
    float pool = smoothstep(0.12, 0.3, sat) * smoothstep(0.78, 0.84, uv.y);
    float ph0 = fract(u.z / 2.0), ph1 = fract(u.z / 2.0 + 0.5);
    float w0 = 1 - abs(2 * ph0 - 1);
    float2 nuv = uv * float2(18, 6);
    float2 n0 = float2(paintPourSplashNoise(nuv + ph0 * 3) - 0.5, paintPourSplashNoise(nuv + 7 - ph0 * 3) - 0.5) * 0.004;
    float2 n1 = float2(paintPourSplashNoise(nuv + ph1 * 3 + 3.1) - 0.5, paintPourSplashNoise(nuv + 11 - ph1 * 3) - 0.5) * 0.004;
    float3 a = paintPourSplashSample(image, uv + (float2(0.08, -1) * ph0 * 0.045 + n0) * stream);
    float3 b = paintPourSplashSample(image, uv + (float2(0.08, -1) * ph1 * 0.045 + n1) * stream);
    float3 col = mix(b, a, w0);
    float2 d = uv - float2(0.52, 0.92);
    float r = length(d * float2(1, 3));
    float2 rip = normalize(d + 1e-4) * sin(r * 80 - 6.2832 * u.z) * 0.0025 * pool;
    col = mix(col, paintPourSplashSample(image, uv + rip), pool);
    return float4(pow(clamp(col, 0.0, 1.0), float3(1.0 / 2.2)), 1);
}
