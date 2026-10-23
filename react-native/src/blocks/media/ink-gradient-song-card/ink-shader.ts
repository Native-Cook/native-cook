import { Skia } from '@shopify/react-native-skia';

/**
 * The card's ink as SkSL: the same code as InkGradientSongCard.metal. Uniforms, in order: size (2),
 * grain cell size, tau, stir point (2), stir strength. Coordinates are the card's points.
 */
export const INK_EFFECT = Skia.RuntimeEffect.Make(`
uniform float2 uSize;
uniform float uGrain;
uniform float uTau;
uniform float2 uStir;
uniform float uStrength;

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
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p = float2(p.x * 0.8 - p.y * 0.6, p.x * 0.6 + p.y * 0.8) * 2.03 + 11.3;
        a *= 0.5;
    }
    return v;
}

// Skia reads a runtime shader's output in the surface's colour space (Display P3 on iPhones),
// which makes these sRGB colours more saturated than the Metal version; convert them first.
half3 toScreen(float3 c) {
    c = max(c, float3(0.0));
    float3 lin = mix(c / 12.92, pow((c + 0.055) / 1.055, float3(2.4)), step(0.04045, c));
    return fromLinearSrgb(half3(lin));
}

half4 main(float2 position) {
    float2 uv = position / uSize.y;
    float2 s = uStir / uSize.y;
    // The stir: a swirl around the stirring point, fading over about a third of the card.
    float2 d = uv - s;
    float ang = uStrength * exp(-dot(d, d) / 0.045);
    float c = cos(ang);
    float sn = sin(ang);
    float2 w = s + float2(c * d.x - sn * d.y, sn * d.x + c * d.y);
    float2 p = w * 2.2;
    float2 q = float2(fbm(p + 0.8 * float2(cos(uTau), sin(uTau))),
                      fbm(p + float2(5.2, 1.3) + 0.8 * float2(sin(uTau), -cos(uTau))));
    float f = fbm(p + 2.2 * q + 0.6 * float2(cos(uTau + 1.0), sin(2.0 * uTau)));

    // Spray: each grain cell nudges the thresholds, so the edges between clouds stipple.
    float2 cell = floor(position / uGrain);
    float g1 = hash(cell) - 0.5;
    float g2 = hash(cell + float2(37.0, 17.0)) - 0.5;

    float3 burgundy = float3(0.420, 0.063, 0.125);
    float3 red = float3(0.878, 0.145, 0.122);
    float3 lilac = float3(0.788, 0.545, 0.910);
    float redAmount = smoothstep(0.55, 0.61, q.x + 0.22 * (uv.y - 0.5) + 0.05 * sin(uTau) + 0.16 * g1);
    float lilacAmount = smoothstep(0.57, 0.63, f + 0.04 * cos(uTau) + 0.16 * g2);
    float3 col = mix(burgundy, red, redAmount);
    col = mix(col, lilac, lilacAmount);
    // Film grain over everything.
    col *= 0.94 + 0.12 * hash(cell + float2(91.0, 7.0));
    return half4(toScreen(col), 1.0);
}
`);
