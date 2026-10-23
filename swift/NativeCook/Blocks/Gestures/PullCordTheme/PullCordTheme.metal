#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

static float pullCordThemeHash12(float2 p) {
    float3 p3 = fract(float3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

static float pullCordThemeNoise(float2 p) {
    float2 i = floor(p);
    float2 u = fract(p);
    u = u * u * (3.0 - 2.0 * u);
    float a = pullCordThemeHash12(i);
    float b = pullCordThemeHash12(i + float2(1.0, 0.0));
    float c = pullCordThemeHash12(i + float2(0.0, 1.0));
    float d = pullCordThemeHash12(i + float2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

static float pullCordThemeSegmentDistance(float2 p, float2 a, float2 b) {
    float2 pa = p - a;
    float2 ba = b - a;
    float h = clamp(dot(pa, ba) / max(dot(ba, ba), 0.0001), 0.0, 1.0);
    return length(pa - ba * h);
}

static float pullCordThemeLuma(float3 color) {
    return dot(pow(clamp(color, 0.0, 1.0), float3(2.2)), float3(0.2126, 0.7152, 0.0722));
}

static float pullCordThemeContrast(float3 a, float3 b) {
    float first = pullCordThemeLuma(a);
    float second = pullCordThemeLuma(b);
    return (max(first, second) + 0.05) / (min(first, second) + 0.05);
}

static float pullCordThemeChainShadow(
    float2 p,
    float2 bulb,
    float scale,
    device const float *chain,
    int chainFloats
) {
    int count = chainFloats / 2;
    float distance = 100000.0;
    for (int index = 0; index < count - 2; ++index) {
        float2 a = bulb + (float2(chain[index * 2], chain[index * 2 + 1]) - bulb) * 2.0;
        float2 b = bulb + (float2(chain[index * 2 + 2], chain[index * 2 + 3]) - bulb) * 2.0;
        distance = min(distance, pullCordThemeSegmentDistance(p, a, b));
    }
    float2 a = bulb + (float2(chain[(count - 2) * 2], chain[(count - 2) * 2 + 1]) - bulb) * 2.0;
    float2 b = bulb + (float2(chain[(count - 1) * 2], chain[(count - 1) * 2 + 1]) - bulb) * 2.0;
    float tip = pullCordThemeSegmentDistance(p, a, b);
    float width = 10.0 * scale;
    float shadow = 0.45 * exp(-(distance * distance) / (width * width));
    shadow += 0.7 * exp(-(tip * tip) / (width * width * 1.6));
    return min(shadow, 0.6);
}

[[ stitchable ]] half4 pullCordThemePage(
    float2 p,
    float2 bulb,
    float2 axis,
    float depth,
    float2 range,
    float front,
    float band,
    float flat,
    float bright,
    float3 lamp,
    float scale,
    float adapt,
    float role,
    device const float *chain,
    int chainFloats
) {
    const float3 dayPaper = float3(0.945098, 0.921569, 0.878431);
    const float3 dayInk = float3(0.152941, 0.137255, 0.117647);
    const float3 dayMute = float3(0.431373, 0.396078, 0.345098);
    const float3 dayAccent = float3(0.184314, 0.364706, 0.282353);
    const float3 nightPaper = float3(0.070588, 0.070588, 0.066667);
    const float3 nightInk = float3(0.847059, 0.815686, 0.760784);
    const float3 nightMute = float3(0.556863, 0.529412, 0.486275);
    const float3 nightAccent = float3(0.513725, 0.690196, 0.592157);

    float2 delta = p - bulb;
    float radius = sqrt(dot(delta, delta) + depth * depth);
    float incidence = depth / radius;
    float direct = smoothstep(0.207912, 0.469472, dot(delta, axis) / radius);
    direct *= incidence * incidence * incidence;
    float distance2 = 4.0 * depth * depth;
    float bounce = distance2 / (dot(delta, delta) + distance2);
    float illuminance = direct + 0.03 * bounce;
    float rank = 1.0 - clamp((log(illuminance) - range.x) / (range.y - range.x), 0.0, 1.0);
    float mask = clamp((front * (1.0 + band) - rank) / band, 0.0, 1.0);
    mask = mask * mask * (3.0 - 2.0 * mask);
    mask = mix(mask, front, flat);

    float lit = direct * (1.0 - pullCordThemeChainShadow(p, bulb, scale, chain, chainFloats));
    float glow = pow(clamp(lit / 0.55, 0.0, 1.0), 0.45) * bright;
    float2 shadePoint = (p - bulb + float2(0.0, 30.0 * scale)) / (float2(110.0, 60.0) * scale);
    float occlusion = 1.0 - smoothstep(0.55, 1.45, length(shadePoint));
    float heat = pow(bright, 0.25);
    float3 tint = lamp / max(max(lamp.r, lamp.g), max(lamp.b, 0.001));
    float room = (0.83 + 0.06 * bounce * (1.0 - 0.7 * occlusion)) * mix(0.5, 1.0, heat);
    float3 light = room * mix(float3(1.0), float3(1.0, 0.8, 0.58), 0.35 * (1.0 - heat));
    light += 0.15 * glow * tint;
    float lift = 1.0 + 0.35 * adapt;
    float3 paper = mix(nightPaper * lift + lamp * glow * 0.5, dayPaper * light, mask);

    if (role < 0.5) {
        float3 color = paper;
        color += (pullCordThemeNoise(p * float2(0.7, 2.2) / scale) - 0.5) * 0.016;
        color += (pullCordThemeHash12(floor(p * 3.0)) - 0.5) * 0.008;
        return half4(half3(color), 1.0h);
    }

    float3 dayRole = role < 1.5 ? dayInk : (role < 2.5 ? dayMute : dayAccent);
    float3 nightRole = role < 1.5 ? nightInk : (role < 2.5 ? nightMute : nightAccent);
    float3 litDayInk = dayInk * light;
    float3 litNightInk = nightInk * lift + lamp * glow * 0.12;
    bool useDay = pullCordThemeContrast(litDayInk, paper) >= pullCordThemeContrast(litNightInk, paper);
    float3 own = useDay ? dayRole * light : nightRole * lift + lamp * glow * 0.12;
    float3 far;
    if (role >= 2.5) {
        far = useDay ? own * 0.5 : mix(own, float3(1.0), 0.5);
    } else {
        far = useDay ? litDayInk : litNightInk;
    }
    float3 color = mix(far, own, smoothstep(3.0, 4.2, pullCordThemeContrast(own, paper)));
    return half4(half3(color), 1.0h);
}

static float pullCordThemeBeam(float2 p, float2 bulb, float2 axis, float scale) {
    float2 delta = p - bulb;
    float radius = max(length(delta), 0.001);
    float cone = smoothstep(0.342020, 0.559193, dot(delta, axis) / radius);
    float reach = 140.0 * scale;
    float falloff = 1.0 / (1.0 + radius * radius / (reach * reach));
    return cone * falloff * smoothstep(12.0 * scale, 44.0 * scale, radius);
}

[[ stitchable ]] half4 pullCordThemeAir(
    float2 p,
    float2 bulb,
    float2 axis,
    float bright,
    float3 lamp,
    float time,
    float scale
) {
    const float tau = 6.2831853;
    float wave = tau * time / 12.0;
    float beam = pullCordThemeBeam(p, bulb, axis, scale);
    float2 drift = float2(sin(wave), cos(wave)) * 0.7;
    float haze = beam * (0.7 + 0.6 * pullCordThemeNoise(p / (64.0 * scale) + drift)) * 0.07;
    float2 side = float2(axis.y, -axis.x);
    float reference = max(pullCordThemeBeam(bulb + axis * 60.0 * scale, bulb, axis, scale), 0.001);
    float motes = 0.0;
    for (int index = 0; index < 12; ++index) {
        float fi = float(index);
        float h1 = pullCordThemeHash12(float2(fi, 1.7));
        float h2 = pullCordThemeHash12(float2(fi, 9.3));
        float h3 = pullCordThemeHash12(float2(fi, 4.1));
        float along = (40.0 + 230.0 * h1) * scale;
        float across = (h2 - 0.5) * 1.6 * along;
        float2 centre = bulb + axis * along + side * across;
        centre += float2(sin(wave * (1.0 + floor(h3 * 2.0)) + h1 * tau), sin(wave + h2 * tau)) * 6.0 * scale;
        float size = mix(0.7, 1.3, h3) * scale;
        float2 offset = p - centre;
        float glow = exp(-dot(offset, offset) / (size * size));
        float twinkle = 0.65 + 0.35 * sin(wave * (1.0 + floor(h2 * 3.0)) + h3 * tau);
        motes += glow * twinkle * pullCordThemeBeam(centre, bulb, axis, scale) / reference;
    }
    float3 color = lamp * bright * (haze + 0.32 * min(motes, 1.0));
    color += (pullCordThemeHash12(p * 1.37) - 0.5) / 255.0;
    color = max(color, float3(0.0));
    return half4(half3(color), half(max(color.r, max(color.g, color.b))));
}
