#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

namespace VitalsChipMorphLiquidShader {

constant float3 SKY_TOP = float3(1.0000, 1.0000, 1.0001);
constant float3 SKY_LOW = float3(0.8498, 0.8623, 0.9056);
constant float3 HORIZON = float3(0.4656, 0.4779, 0.5430);
constant float3 FLOOR_NEAR = float3(0.7785, 0.7879, 0.8347);
constant float3 FLOOR_FAR = float3(0.9418, 0.9450, 0.9630);
constant float3 KEY = float3(-0.47, -0.74, 0.48);

float sat1(float value) { return clamp(value, 0.0, 1.0); }

float2 toReference(float2 point, float scale) {
    return float2(point.x / scale, point.y / scale + 400.0);
}

float2 toLayer(float2 point, float scale) {
    return float2(point.x * scale, (point.y - 400.0) * scale);
}

struct SoftField {
    float4 color;
    float2 gradient;
};

// SwiftUI layer effects expose one source layer. Sample three symmetric rings
// of its Gaussian field and evaluate the Gaussian derivative at the same time.
SoftField softField(SwiftUI::Layer layer, float2 point, float sigma, float scale) {
    float4 color = float4(layer.sample(point));
    float2 gradient = float2(0.0);
    float total = 1.0;
    for (int ring = 1; ring <= 3; ring++) {
        float normalizedRadius = float(ring) * 0.75;
        float radius = normalizedRadius * sigma;
        float weight = exp(-0.5 * normalizedRadius * normalizedRadius) * normalizedRadius;
        float phase = (ring & 1) == 0 ? 0.2617994 : 0.0;
        for (int index = 0; index < 12; index++) {
            float angle = float(index) * 0.5235988 + phase;
            float2 offset = float2(cos(angle), sin(angle)) * radius;
            float4 sample = float4(layer.sample(point + offset));
            color += sample * weight;
            gradient += sample.a * offset * (weight / (sigma * sigma));
            total += weight;
        }
    }
    return SoftField { color / total, gradient * (scale / total) };
}

float2 flowAt(float2 point, float clock, float wobble) {
    float a = sin(point.y * 0.021 + clock * 5.3 + sin(point.x * 0.013 - clock * 3.1) * 1.7);
    float b = sin(point.x * 0.019 - clock * 4.7 + sin(point.y * 0.017 + clock * 2.3) * 1.5);
    return float2(a, b) * 7.0 * wobble;
}

float2 rippleAt(float2 point, float2 heartCenter, float rippleTime, float rippleAmount, thread float2 &direction) {
    float2 delta = point - heartCenter;
    float radius = max(length(delta), 1.0);
    direction = delta / radius;
    if (rippleAmount <= 0.0) return float2(0.0);
    float x = radius - rippleTime * 820.0;
    float envelope = exp(-x * x / 9800.0) * exp(-rippleTime / 0.5) * rippleAmount;
    envelope *= sat1(radius / 60.0);
    return float2(envelope * cos(x * 0.0982), envelope);
}

float3 studio(float3 reflected) {
    float t = reflected.y;
    float3 sky = mix(SKY_TOP, SKY_LOW, smoothstep(-0.95, 0.0, t));
    float3 floorColor = mix(FLOOR_NEAR, FLOOR_FAR, smoothstep(0.0, 0.85, t));
    float3 color = mix(sky, floorColor, step(0.0, t));
    color = mix(color, HORIZON, exp(-t * t / 0.006) * 0.75);
    color += float3(0.35) * smoothstep(0.09, 0.02, abs(reflected.x + 0.38))
        * smoothstep(-0.15, -0.55, t);
    return color;
}

float3 straight(float4 color) {
    return color.a > 0.0005 ? color.rgb / color.a : float3(0.0);
}

half4 render(
    float2 position,
    SwiftUI::Layer image,
    float scale,
    float clock,
    float liquid,
    float wobble,
    float goo,
    float2 gooCenter,
    float2 heartCenter,
    float rippleTime,
    float rippleAmount
) {
    half4 rest = image.sample(position);
    float2 point = toReference(position, scale);
    float2 direction;
    float2 ripple = rippleAt(point, heartCenter, rippleTime, rippleAmount, direction);
    float material = max(max(liquid, wobble), ripple.y);
    if (material <= 0.0005) return rest;

    float2 displaced = point + flowAt(point, clock, wobble) + direction * ripple.x * 3.5;
    float2 q = toLayer(displaced, scale);
    float4 base = float4(image.sample(q));
    SoftField field = softField(image, q, 15.0 * scale, scale);
    float4 soft = field.color;
    float2 gradient = field.gradient;

    float antialiasWidth = clamp(length(gradient) * 1.4, 0.015, 0.3);
    float2 gooDelta = (displaced - gooCenter) / 260.0;
    float gooHere = goo * (1.0 - smoothstep(0.55, 1.0, length(gooDelta)));
    float gooAlpha = smoothstep(0.5 - antialiasWidth, 0.5 + antialiasWidth, soft.a)
        * gooHere * (1.0 - smoothstep(0.0, 0.08, base.a));
    float alpha = max(base.a, gooAlpha);
    if (alpha <= 0.001) return mix(rest, half4(0.0h), half(material));

    float inside = sat1(base.a * 1.6);
    float3 color = mix(straight(soft), straight(base), inside);
    float lit = max(liquid, ripple.y);
    float2 slope = -gradient * 70.0 * liquid - direction * ripple.x * 0.95 * alpha;
    float3 normal = normalize(float3(slope, 1.0));
    float tilt = 1.0 - normal.z;

    float4 bent = float4(image.sample(toLayer(displaced - slope * 12.0, scale)));
    if (bent.a > 0.2) color = mix(color, straight(bent), sat1(tilt * 3.0));

    float3 lightDirection = normalize(KEY);
    float diffuse = dot(normal, lightDirection);
    float3 halfVector = normalize(lightDirection + float3(0.0, 0.0, 1.0));
    float highlight = max(dot(normal, halfVector), 0.0);
    float specular = pow(highlight, 90.0) * 1.15 + pow(highlight, 14.0) * 0.10;
    float fresnel = 0.05 + 0.95 * pow(tilt, 2.2);
    float3 reflected = float3(2.0 * normal.z * normal.x,
                              2.0 * normal.z * normal.y,
                              2.0 * normal.z * normal.z - 1.0);
    float2 outward = length(normal.xy) > 0.0001 ? normalize(normal.xy) : float2(0.0);
    float caustic = max(dot(outward, -normalize(lightDirection.xy)), 0.0)
        * smoothstep(0.02, 0.35, tilt);

    float3 body = color * (0.86 + 0.22 * diffuse)
        * (1.0 - 0.22 * smoothstep(0.25, 0.85, tilt));
    body += color * caustic * 0.55;
    float3 shaded = mix(body, studio(reflected), sat1(fresnel * 0.9)) + float3(specular);
    float3 outputColor = mix(color, shaded, lit);
    half4 wet = half4(half3(outputColor * alpha), half(alpha));
    return mix(rest, wet, half(sat1(material * 4.0)));
}

} // namespace VitalsChipMorphLiquidShader

[[ stitchable ]] half4 vitalsChipMorphLiquid(
    float2 position,
    SwiftUI::Layer image,
    float scale,
    float clock,
    float liquid,
    float wobble,
    float goo,
    float2 gooCenter,
    float2 heartCenter,
    float rippleTime,
    float rippleAmount
) {
    return VitalsChipMorphLiquidShader::render(
        position, image, scale, clock, liquid, wobble, goo,
        gooCenter, heartCenter, rippleTime, rippleAmount
    );
}
