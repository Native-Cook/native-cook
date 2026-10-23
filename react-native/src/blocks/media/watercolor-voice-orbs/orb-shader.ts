import { Skia } from '@shopify/react-native-skia';

/**
 * The orb's SkSL shader: the same code as WatercolorVoiceOrbs.metal. Uniforms, in order: center
 * (2), radius, tau, deepA/midA/lightA (3 each), deepB/midB/lightB (3 each), mix, band (2),
 * offset (2), swirl.
 */
export const ORB_EFFECT = Skia.RuntimeEffect.Make(`
uniform float2 uCenter;
uniform float uRadius;
uniform float uTau;
uniform float3 uDeepA;
uniform float3 uMidA;
uniform float3 uLightA;
uniform float3 uDeepB;
uniform float3 uMidB;
uniform float3 uLightB;
uniform float uMix;
uniform float2 uBand;
uniform float2 uOffset;
uniform float uSwirl;

float hash(float2 p) {
    float3 p3 = fract(float3(p.x, p.y, p.x) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x),
               mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
}

float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p = float2(p.x * 0.8 - p.y * 0.6, p.x * 0.6 + p.y * 0.8) * 2.02 + 17.1;
        a *= 0.5;
    }
    return v;
}

float3 paint(float v, float qx, float3 deep, float3 mid, float3 light) {
    float3 col = mix(deep, mid, smoothstep(0.22, 0.58, v));
    return mix(col, light, smoothstep(0.52, 0.9, v + 0.18 * (qx - 0.5)));
}

// Skia reads a runtime shader's output in the surface's colour space (Display P3 on iPhones),
// which makes these sRGB colours more saturated than the Metal version; convert them first.
half3 toScreen(float3 c) {
    c = max(c, float3(0.0));
    float3 lin = mix(c / 12.92, pow((c + 0.055) / 1.055, float3(2.4)), step(0.04045, c));
    return fromLinearSrgb(half3(lin));
}

half4 main(float2 position) {
    float2 uv = (position - uCenter) / uRadius;
    float r = length(uv);
    float ang = uSwirl * (1.0 - r * r) + 0.25 * sin(uTau);
    float c = cos(ang);
    float s = sin(ang);
    float2 p = float2(c * uv.x - s * uv.y, s * uv.x + c * uv.y) * 1.2 + uOffset;
    float2 q = float2(fbm(p + 0.7 * float2(cos(uTau), sin(uTau))),
                      fbm(p + float2(5.2, 1.3) + 0.7 * float2(sin(uTau), -cos(uTau))));
    float f = fbm(p + 2.4 * q + 0.5 * float2(cos(2.0 * uTau + 1.0), sin(2.0 * uTau + 1.0)));
    float v = f + 0.42 * dot(uv, uBand);
    float3 colA = paint(v, q.x, uDeepA, uMidA, uLightA);
    float3 colB = paint(v, q.x, uDeepB, uMidB, uLightB);
    // The seep: where the warp field is low the new colours arrive first.
    float seep = q.y + 0.25 * (f - 0.5);
    float3 col = mix(colA, colB, smoothstep(seep - 0.12, seep + 0.12, uMix * 1.5 - 0.25));
    // A little depth: the rim darkens, a soft light sits upper left.
    col *= 1.0 - 0.16 * smoothstep(0.62, 1.0, r);
    col += 0.08 * (1.0 - smoothstep(0.0, 0.9, length(uv - float2(-0.35, -0.45))));
    float edge = 1.0 / uRadius;
    float alpha = 1.0 - smoothstep(1.0 - edge, 1.0 + edge, r);
    return half4(toScreen(min(col, float3(1.0))) * half(alpha), half(alpha));
}
`);
