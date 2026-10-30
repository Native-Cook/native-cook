#pragma METAL fp math_mode(relaxed)
#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

float raffleDrumSpinHash(float2 point) {
    return fract(sin(dot(point, float2(127.1, 311.7))) * 43758.5453);
}

float raffleDrumSpinNoise(float2 point) {
    float2 cell = floor(point);
    float2 fraction = fract(point);
    fraction = fraction * fraction * (3.0 - 2.0 * fraction);
    return mix(
        mix(raffleDrumSpinHash(cell), raffleDrumSpinHash(cell + float2(1.0, 0.0)), fraction.x),
        mix(raffleDrumSpinHash(cell + float2(0.0, 1.0)), raffleDrumSpinHash(cell + 1.0), fraction.x),
        fraction.y
    );
}

float3 raffleDrumSpinToP3(float3 color) {
    float3 linear = pow(clamp(color, 0.0, 1.0), float3(2.2));
    float3 converted = float3(
        dot(linear, float3(0.8225, 0.1774, 0.0)),
        dot(linear, float3(0.0332, 0.9669, 0.0)),
        dot(linear, float3(0.0171, 0.0724, 0.9108))
    );
    return pow(converted, float3(1.0 / 2.2));
}

/// Direct Metal port of BG_SKSL. Argument order matches the SkSL uniforms.
[[ stitchable ]] half4 raffleDrumSpinBackground(
    float2 position,
    half4 source,
    float2 size,
    float2 light,
    float glow,
    float time
) {
    float y = position.y / size.y;
    float x = position.x / size.x;
    float3 color = mix(float3(0.09, 0.035, 0.2), float3(0.22, 0.1, 0.37), smoothstep(0.0, 0.19, y));
    color = mix(color, float3(0.11, 0.016, 0.26), smoothstep(0.19, 0.33, y));
    color = mix(color, float3(0.065, 0.008, 0.17), smoothstep(0.33, 0.4, y));
    color = mix(color, float3(0.03, 0.004, 0.075), smoothstep(0.4, 0.47, y));
    color = mix(color, float3(0.004, 0.002, 0.012), smoothstep(0.47, 0.56, y));
    color = mix(color, float3(0.012, 0.016, 0.1), smoothstep(0.75, 1.0, y));
    float2 softbox = float2((x - 0.5) / 0.2, (y - 0.105) / 0.1);
    color += float3(0.25, 0.24, 0.24) * exp(-dot(softbox, softbox));
    float2 spill = float2((x - 0.5) / 0.25, (y - 0.22) / 0.2);
    color += float3(0.07, 0.06, 0.12) * exp(-dot(spill, spill));
    float2 drum = (position - light) / size.x;
    color += float3(0.2, 0.12, 0.42) * exp(-dot(drum, drum) * 7.0) * glow * 0.5 + 0.0 * time;
    color += (raffleDrumSpinHash(position) - 0.5) * 0.012;
    return half4(half3(raffleDrumSpinToP3(max(color, float3(0.0)))), 1.0h);
}

float3 raffleDrumSpinRotateX(float3 value, float angle) {
    float sine = sin(angle), cosine = cos(angle);
    return float3(value.x, cosine * value.y - sine * value.z, sine * value.y + cosine * value.z);
}

float3 raffleDrumSpinRotateY(float3 value, float angle) {
    float sine = sin(angle), cosine = cos(angle);
    return float3(cosine * value.x + sine * value.z, value.y, -sine * value.x + cosine * value.z);
}

float3 raffleDrumSpinRotateZ(float3 value, float angle) {
    float sine = sin(angle), cosine = cos(angle);
    return float3(cosine * value.x - sine * value.y, sine * value.x + cosine * value.y, value.z);
}

float3 raffleDrumSpinSeamPoint(float t) {
    const float a = 0.78;
    const float b = 0.22;
    return float3(a * cos(t) + b * cos(3.0 * t), a * sin(t) - b * sin(3.0 * t), 2.0 * sqrt(a * b) * sin(2.0 * t));
}

// Cache the invariant coarse seam samples; dynamic refinement uses the original function.
constant float3 raffleDrumSpinSeamSamples[40] = {
    float3(1.0f,0.0f,0.0f),
    float3(0.966418147f,0.0221410319f,0.256018877f),
    float3(0.871136308f,0.0630497783f,0.486976713f),
    float3(0.729399562f,0.136821792f,0.670265615f),
    float3(0.563047945f,0.249241278f,0.787944019f),
    float3(0.395977944f,0.395981669f,0.828492582f),
    float3(0.249238253f,0.56305182f,0.787942171f),
    float3(0.136819616f,0.729403257f,0.670262039f),
    float3(0.0630484819f,0.87113905f,0.486971855f),
    float3(0.022140339f,0.966419578f,0.256013244f),
    float3(-4.08172582e-07f,1.0f,-5.9982699e-06f),
    float3(-0.0221417025f,0.966416657f,-0.256024659f),
    float3(-0.0630510747f,0.871133506f,-0.486981541f),
    float3(-0.136823907f,0.729395986f,-0.670269132f),
    float3(-0.249244392f,0.563043833f,-0.787945926f),
    float3(-0.395985305f,0.395974219f,-0.828492582f),
    float3(-0.563055754f,0.249235243f,-0.787940323f),
    float3(-0.729406774f,0.136817545f,-0.670258641f),
    float3(-0.871141672f,0.0630472004f,-0.486967117f),
    float3(-0.966421247f,0.0221397057f,-0.256007344f),
    float3(-1.0f,-8.16345164e-07f,1.19965398e-05f),
    float3(-0.966415107f,-0.0221422464f,0.256030172f),
    float3(-0.871130586f,-0.0630523711f,0.486986578f),
    float3(-0.729392171f,-0.136826098f,0.670272708f),
    float3(-0.563040137f,-0.249247283f,0.787947714f),
    float3(-0.395970702f,-0.395988911f,0.828492582f),
    float3(-0.249232292f,-0.563059628f,0.787938476f),
    float3(-0.136815339f,-0.729410648f,0.670255005f),
    float3(-0.0630458444f,-0.871144831f,0.486961782f),
    float3(-0.022139065f,-0.966422677f,0.25600186f),
    float3(1.4224056e-06f,-1.0f,-1.81923388e-05f),
    float3(0.0221430659f,-0.966413677f,-0.256035715f),
    float3(0.063053593f,-0.871127844f,-0.486991256f),
    float3(0.136828303f,-0.729388356f,-0.670276403f),
    float3(0.249250263f,-0.563036144f,-0.787949502f),
    float3(0.395992875f,-0.395966679f,-0.828492582f),
    float3(0.563063145f,-0.249229401f,-0.787936747f),
    float3(0.729413927f,-0.136813253f,-0.670251608f),
    float3(0.871147573f,-0.0630446076f,-0.486957103f),
    float3(0.966424167f,-0.0221384242f,-0.255996317f)
};

/// Direct Metal port of BALL_SKSL.
[[ stitchable ]] half4 raffleDrumSpinBall(
    float2 position,
    float2 center,
    float radius,
    float3 rotation,
    float alpha,
    float blurX
) {
    float2 q = (position - center) / radius;
    q.x /= 1.0 + blurX;
    float d2 = dot(q, q);
    float edge = 1.5 / radius;
    float cover = 1.0 - smoothstep(1.0 - edge, 1.0, sqrt(d2));
    if (cover <= 0.0) return half4(0.0h);
    float3 normal = float3(q.x, -q.y, sqrt(max(0.0, 1.0 - d2)));
    float3 object = raffleDrumSpinRotateZ(raffleDrumSpinRotateY(raffleDrumSpinRotateX(normal, rotation.x), rotation.y), rotation.z);
    float best = 9.0, bestT = 0.0;
    for (int index = 0; index < 40; index++) {
        float t = float(index) * 0.15708;
        float distanceToSeam = distance(object, raffleDrumSpinSeamSamples[index]);
        if (distanceToSeam < best) { best = distanceToSeam; bestT = t; }
    }
    for (int index = 0; index < 6; index++) {
        float h = 0.08 / float(index + 1);
        float before = distance(object, raffleDrumSpinSeamPoint(bestT - h));
        float after = distance(object, raffleDrumSpinSeamPoint(bestT + h));
        if (before < best) { best = before; bestT -= h; }
        if (after < best) { best = after; bestT += h; }
    }
    float3 seam = raffleDrumSpinSeamPoint(bestT);
    float3 tangent = normalize(raffleDrumSpinSeamPoint(bestT + 0.01) - raffleDrumSpinSeamPoint(bestT - 0.01));
    float side = dot(object - seam, cross(seam, tangent));
    float v = abs(side);
    float u = bestT * 24.0 / 6.2831853;
    float stitchLine = abs(fract(u * 4.0 + v * 9.0) - 0.5);
    float stitch = (1.0 - smoothstep(0.1, 0.2, stitchLine)) * smoothstep(0.018, 0.03, v) * (1.0 - smoothstep(0.075, 0.09, v));
    float groove = 1.0 - smoothstep(0.0, 0.016, v);
    float3 leather = float3(0.97, 0.95, 0.91) * (0.96 + 0.04 * raffleDrumSpinNoise(object.xy * 40.0));
    float3 light = normalize(float3(-0.5, 0.6, 0.65));
    float diffuse = 0.5 + 0.5 * dot(normal, light);
    float specular = pow(max(0.0, dot(reflect(-light, normal), float3(0.0, 0.0, 1.0))), 22.0);
    float rim = pow(1.0 - normal.z, 2.2);
    float3 color = leather * (0.42 + 0.62 * diffuse);
    color = mix(color, color * 0.8, groove * 0.6);
    color = mix(color, float3(0.78, 0.14, 0.16), stitch);
    color += float3(0.55, 0.35, 1.0) * rim * 0.45;
    color += specular * 0.35;
    color *= 1.0 - 0.35 * smoothstep(0.2, -0.9, normal.y);
    float outputAlpha = cover * alpha;
    return half4(half3(raffleDrumSpinToP3(color)) * outputAlpha, outputAlpha);
}

/// Direct Metal port of RAYS_SKSL.
[[ stitchable ]] half4 raffleDrumSpinRays(
    float2 position,
    float2 center,
    float strength,
    float time
) {
    float2 delta = position - center;
    float radius = length(delta);
    float angle = atan2(delta.y, delta.x);
    float rays = pow(0.5 + 0.5 * cos(angle * 9.0 + time * 0.4), 3.0) * 0.8 + 0.2;
    float falloff = exp(-radius / 70.0) * smoothstep(8.0, 30.0, radius);
    float core = exp(-radius * radius / 1800.0);
    float amount = (rays * falloff * 0.9 + core * 0.5) * strength;
    return half4(half3(raffleDrumSpinToP3(float3(0.45, 0.24, 0.95))) * amount, amount);
}

constexpr sampler raffleDrumSpinTicketSampler(coord::normalized, address::clamp_to_zero, filter::linear);

float raffleDrumSpinTicketLuminance(texture2d<half> image, float2 uv, float2 imageSize) {
    half4 sample = image.sample(raffleDrumSpinTicketSampler, uv / imageSize);
    return sample.a > 0.004h
        ? dot(float3(sample.rgb) / float(sample.a), float3(0.3, 0.55, 0.15)) * float(sample.a)
        : 0.0;
}

float3 raffleDrumSpinTiltNormal(float3 normal, float2 tilt) {
    float sineY = sin(tilt.x), cosineY = cos(tilt.x);
    float3 first = float3(cosineY * normal.x + sineY * normal.z, normal.y, -sineY * normal.x + cosineY * normal.z);
    float sineX = sin(tilt.y), cosineX = cos(tilt.y);
    return float3(first.x, cosineX * first.y - sineX * first.z, sineX * first.y + cosineX * first.z);
}

/// Direct Metal port of FOIL_SKSL.
[[ stitchable ]] half4 raffleDrumSpinFoil(
    float2 position,
    texture2d<half> ticket,
    float2 origin,
    float2 size,
    float2 imageSize,
    float2 tilt,
    float lit,
    float sweep,
    float mode,
    float burn,
    float body
) {
    float2 q = (position - origin) / size;
    float2 uv = q * imageSize;
    half4 ticketPixel = ticket.sample(raffleDrumSpinTicketSampler, uv / imageSize);
    float alpha = float(ticketPixel.a);
    if (mode > 0.5) {
        float front = body + 0.09 - burn * (body + 0.27);
        float noise = raffleDrumSpinNoise(q * float2(28.0, 11.0)) * 0.65
            + raffleDrumSpinNoise(q * float2(90.0, 34.0)) * 0.35;
        float distance = q.x - front + (noise - 0.5) * 0.16;
        if (mode < 1.5) {
            float keep = 1.0 - smoothstep(-0.003, 0.003, distance);
            return half4(0.0h, 0.0h, 0.0h, half(keep));
        }
        float live = step(0.0005, burn) * (1.0 - step(0.9995, burn)) * step(q.x, body);
        float edge = exp(-pow(max(-distance, 0.0) / 0.03, 1.4)) * step(distance, 0.004) * alpha * live;
        float3 hot = mix(float3(1.0, 0.45, 0.08), float3(1.0, 0.96, 0.84), exp(-pow(distance / 0.007, 2.0)));
        return half4(half3(raffleDrumSpinToP3(hot)) * edge, edge);
    }
    if (alpha < 0.002) return half4(0.0h);
    float3 albedo = float3(ticketPixel.rgb) / alpha;
    float2 pixel = imageSize / size * 1.4;
    float heightX = raffleDrumSpinTicketLuminance(ticket, uv + float2(pixel.x, 0.0), imageSize)
        - raffleDrumSpinTicketLuminance(ticket, uv - float2(pixel.x, 0.0), imageSize);
    float heightY = raffleDrumSpinTicketLuminance(ticket, uv + float2(0.0, pixel.y), imageSize)
        - raffleDrumSpinTicketLuminance(ticket, uv - float2(0.0, pixel.y), imageSize);
    float3 normal = raffleDrumSpinTiltNormal(normalize(float3(-heightX * 2.6, -heightY * 2.6, 1.0)), tilt);
    float3 light = normalize(float3(-0.35, -0.6, 1.0));
    float3 halfVector = normalize(light + float3(0.0, 0.0, 1.0));
    float diffuse = clamp(dot(normal, light), 0.0, 1.0);
    float specular = pow(clamp(dot(normal, halfVector), 0.0, 1.0), 70.0);
    float2 centered = q - 0.5;
    float band = dot(centered * float2(1.0, 0.36), normalize(float2(1.0, 0.6))) + tilt.x * 0.9 + tilt.y * 0.5;
    float sheen = exp(-pow(band / 0.13, 2.0));
    float timedSweep = sweep > 0.0
        ? exp(-pow((dot(centered * float2(1.0, 0.36), normalize(float2(1.0, 0.5))) - mix(-0.75, 0.75, sweep)) / 0.06, 2.0))
        : 0.0;
    float2 glitterPoint = position / 3.0;
    float2 cell = floor(glitterPoint);
    float2 offset = float2(raffleDrumSpinHash(cell + 5.7), raffleDrumSpinHash(cell + 9.1)) * 0.6 + 0.2;
    float dotMask = 1.0 - smoothstep(0.05, 0.32, length(fract(glitterPoint) - offset));
    float3 facet = raffleDrumSpinTiltNormal(normalize(float3(
        (raffleDrumSpinHash(cell) - 0.5) * 0.8,
        (raffleDrumSpinHash(cell + 17.3) - 0.5) * 0.8,
        1.0
    )), tilt);
    float glint = pow(clamp(dot(facet, halfVector), 0.0, 1.0), 600.0)
        * step(0.9, raffleDrumSpinHash(cell + 3.1)) * dotMask;
    float busy = clamp(length(float2(heightX, heightY)) * 7.0, 0.0, 1.0);
    float3 film = 0.5 + 0.5 * cos(6.2831853 * (normal.z * 1.6 + tilt.x * 0.8 + float3(0.0, 0.33, 0.67)));
    float3 color = albedo * (0.84 + 0.3 * diffuse);
    color += float3(1.0, 0.9, 0.68) * (specular * 0.5 + sheen * 0.2 + timedSweep * 0.6);
    color += float3(1.0, 0.95, 0.82) * glint * 0.9;
    color = mix(color, color * (0.75 + 0.5 * film), busy * 0.14);
    color = mix(albedo, color, lit);
    return half4(half3(raffleDrumSpinToP3(min(color, float3(1.0)))) * alpha, alpha);
}

float raffleDrumSpinLightBand(float value, float center, float width) {
    return exp(-pow((value - center) / width, 2.0));
}

/// Direct Metal port of LIGHT_SKSL.
[[ stitchable ]] half4 raffleDrumSpinLight(
    float2 position,
    float2 size,
    float2 ball,
    float streak,
    float2 core,
    float rays,
    float rotation,
    float2 ringCenter,
    float ringRadius,
    float ring,
    float2 stage,
    float aurora,
    float time,
    float scale
) {
    float3 color = float3(0.0);
    if (streak > 0.0) {
        float2 delta = position - ball;
        float amount = exp(-abs(delta.y) / (2.2 * scale)) * exp(-pow(delta.x / (48.0 * scale), 2.0));
        color += float3(1.0, 0.93, 0.8) * amount * 0.4 * streak;
    }
    if (rays > 0.0) {
        float2 delta = position - core;
        float radius = length(delta) / scale;
        float angle = atan2(delta.y, delta.x) + rotation;
        float spoke = pow(max(0.0, cos(angle * 9.0)), 10.0) * 0.6
            + pow(max(0.0, cos(angle * 14.0 + 1.3)), 16.0) * 0.4;
        float rayLength = 80.0 + 90.0 * raffleDrumSpinNoise(float2(floor(angle * 14.0 / 6.2831853 * 4.0), 3.0));
        float falloff = exp(-radius / rayLength) * smoothstep(8.0, 40.0, radius);
        float glow = exp(-radius * radius / (2.0 * 55.0 * 55.0));
        color += float3(1.0, 0.88, 0.62) * (spoke * falloff * 0.42 + glow * 0.22) * rays;
    }
    if (ring > 0.0) {
        float radius = length(position - ringCenter);
        float width = 9.0 * scale;
        float3 split = float3(
            raffleDrumSpinLightBand(radius, ringRadius * 1.008, width),
            raffleDrumSpinLightBand(radius, ringRadius, width),
            raffleDrumSpinLightBand(radius, ringRadius * 0.992, width)
        );
        color += mix(float3(raffleDrumSpinLightBand(radius, ringRadius, width)), split, 0.55)
            * float3(1.0, 0.93, 0.8) * 0.4 * ring
            + float3(1.0, 0.9, 0.7) * raffleDrumSpinLightBand(radius, ringRadius * 0.92, width * 3.0) * 0.12 * ring;
    }
    if (aurora > 0.0) {
        float2 q = (position - stage) / (size.x * 0.5);
        float mask = exp(-dot(q * float2(0.85, 2.6), q * float2(0.85, 2.6)));
        float2 warped = q * 2.2 + float2(
            raffleDrumSpinNoise(q * 1.7 + time * 0.25),
            raffleDrumSpinNoise(q * 1.9 - time * 0.2)
        ) * 1.4;
        float silk = raffleDrumSpinLightBand(sin(warped.x * 2.1 + warped.y * 1.3 + time * 0.6), 0.6, 0.35)
            + 0.6 * raffleDrumSpinLightBand(sin(warped.x * -1.4 + warped.y * 2.6 - time * 0.45), 0.4, 0.3);
        float3 auroraColor = mix(float3(0.45, 0.25, 0.95), float3(1.0, 0.72, 0.35),
            smoothstep(-0.4, 0.6, q.x + 0.3 * sin(time * 0.7)));
        color += auroraColor * silk * mask * 0.38 * aurora;
    }
    float3 output = raffleDrumSpinToP3(min(color, float3(1.0)));
    return half4(half3(output), half(max(output.r, max(output.g, output.b))));
}

[[ stitchable ]] half4 raffleDrumSpinLiquid(
    float2 position,
    float2 origin,
    float2 size,
    float fill,
    float drain,
    float2 from,
    float time
) {
    float2 point = position - origin;
    float x = point.x / size.x;
    float slosh = sin(x * 9.0 + time * 9.0) * 2.2
        + sin(x * 21.0 - time * 13.0) * 0.9;
    float level = mix(size.y + 6.0, -8.0, fill) + slosh * (1.0 - fill * 0.85);
    float under = smoothstep(level - 0.8, level + 0.8, point.y);
    float radius = length(position - from);
    float angle = atan2(position.y - from.y, position.x - from.x);
    float reach = max(from.x - origin.x, origin.x + size.x - from.x) + 10.0;
    float edge = drain * reach + sin(angle * 7.0 + time * 12.0) * 3.0 * drain * (1.0 - drain);
    float gone = drain > 0.0 ? smoothstep(edge - 1.0, edge + 1.0, radius) : 1.0;
    float alpha = under * gone;
    if (alpha <= 0.001) return half4(0.0h);
    float y = point.y / size.y;
    float3 gold = mix(float3(1.0, 0.94, 0.77), float3(0.95, 0.77, 0.39), smoothstep(0.0, 0.5, y));
    gold = mix(gold, float3(0.77, 0.54, 0.17), smoothstep(0.5, 1.0, y));
    float meniscus = exp(-pow((point.y - level) / 1.6, 2.0)) * (1.0 - fill * 0.5);
    float rim = drain > 0.0 ? exp(-pow((radius - edge) / 1.8, 2.0)) : 0.0;
    gold += float3(1.0, 0.98, 0.9) * (meniscus * 0.7 + rim * 0.6);
    float3 output = raffleDrumSpinToP3(min(gold, float3(1.0)));
    return half4(half3(output) * half(alpha), half(alpha));
}

float raffleDrumSpinMorphBox(float2 point, float2 bounds, float radius) {
    float2 q = abs(point) - bounds + radius;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

float4 raffleDrumSpinMorphSeamAt(float2 point, float2 center, float radius, float3 rotation) {
    float2 q = (point - center) / radius;
    float magnitude = length(q);
    float2 safe = magnitude > 0.999 ? q / magnitude * 0.999 : q;
    float3 normal = float3(safe.x, -safe.y, sqrt(max(0.0, 1.0 - dot(safe, safe))));
    float3 object = raffleDrumSpinRotateY(raffleDrumSpinRotateX(normal, rotation.x), rotation.y);
    float best = 9.0, bestT = 0.0;
    for (int index = 0; index < 40; index++) {
        float t = float(index) * 0.15708;
        float distanceToSeam = distance(object, raffleDrumSpinSeamSamples[index]);
        if (distanceToSeam < best) { best = distanceToSeam; bestT = t; }
    }
    for (int index = 0; index < 6; index++) {
        float step = 0.08 / float(index + 1);
        float before = distance(object, raffleDrumSpinSeamPoint(bestT - step));
        float after = distance(object, raffleDrumSpinSeamPoint(bestT + step));
        if (before < best) { best = before; bestT -= step; }
        if (after < best) { best = after; bestT += step; }
    }
    float3 seam = raffleDrumSpinSeamPoint(bestT);
    float3 tangent = normalize(raffleDrumSpinSeamPoint(bestT + 0.01) - raffleDrumSpinSeamPoint(bestT - 0.01));
    float side = dot(object - seam, cross(seam, tangent));
    return float4(side, bestT, abs(side), normal.z);
}

float3 raffleDrumSpinMorphLeather(
    float2 point, float2 center, float radius, float3 rotation,
    float4 seam, float stitches, float morph, float glow
) {
    float2 q = (point - center) / radius;
    float3 normal = float3(q.x, -q.y, sqrt(max(0.0, 1.0 - dot(q, q))));
    float v = seam.z;
    float u = seam.y * 24.0 / 6.2831853;
    float line = abs(fract(u * 4.0 + v * 9.0) - 0.5);
    float popped = step(fract(seam.y / 6.2831853 + 0.25), stitches);
    float stitch = (1.0 - smoothstep(0.1, 0.2, line)) * smoothstep(0.018, 0.03, v)
        * (1.0 - smoothstep(0.075, 0.09, v)) * (1.0 - popped);
    float groove = 1.0 - smoothstep(0.0, 0.016, v);
    float3 light = normalize(float3(-0.5, 0.6, 0.65));
    float diffuse = 0.5 + 0.5 * dot(normal, light);
    float specular = pow(max(0.0, dot(reflect(-light, normal), float3(0.0, 0.0, 1.0))), 22.0);
    float rim = pow(1.0 - normal.z, 2.2);
    float3 color = float3(0.97, 0.95, 0.91) * (0.42 + 0.62 * diffuse);
    color = mix(color, color * 0.8, groove * 0.6);
    color = mix(color, float3(0.78, 0.14, 0.16), stitch);
    color += float3(0.55, 0.35, 1.0) * rim * 0.45 + specular * 0.35;
    color += float3(1.0, 0.85, 0.5) * glow * rim * 0.7;
    color *= 1.0 - 0.35 * smoothstep(0.2, -0.9, normal.y);
    float seamGlow = max(stitches * 0.5, smoothstep(0.0, 0.2, morph));
    color = mix(color, float3(1.0, 0.86, 0.5) * 1.3,
        seamGlow * (1.0 - smoothstep(0.0, 0.035 + 0.05 * seamGlow, v)));
    return color;
}

float raffleDrumSpinMorphEase(float value) {
    value = clamp(value, 0.0, 1.0);
    return value * value * (3.0 - 2.0 * value);
}

float raffleDrumSpinMorphSmoothMax(float a, float b, float smoothing) {
    float h = max(smoothing - abs(a - b), 0.0) / smoothing;
    return max(a, b) + h * h * smoothing * 0.25;
}

/// Direct Metal port of MORPH_SKSL, including its two leather covers, ticket child texture and halo.
[[ stitchable ]] half4 raffleDrumSpinMorph(
    float2 point,
    texture2d<half> ticket,
    float2 center,
    float radius,
    float3 rotation,
    float stitches,
    float morph,
    float2 ticketCenter,
    float2 ticketSize,
    float body,
    float2 imageSize,
    float corner,
    float glow,
    float alpha
) {
    float crack = smoothstep(0.0, 0.2, morph);
    float peel = raffleDrumSpinMorphEase((morph - 0.1) / 0.5);
    float shape = raffleDrumSpinMorphEase((morph - 0.3) / 0.58);
    float opening = radius * 0.34 * peel;
    float turn = 1.0 - 0.24 * sin(3.14159 * peel) * (1.0 - shape);
    float edge = 1.0 + 3.0 * shape * (1.0 - shape);
    float2 bodyCenter = float2(ticketCenter.x - ticketSize.x * 0.5 + ticketSize.x * body * 0.5, ticketCenter.y);
    float2 stubCenter = float2(ticketCenter.x + ticketSize.x * 0.5 - ticketSize.x * (1.0 - body) * 0.5, ticketCenter.y);
    float3 accumulated = float3(0.0);
    float accumulatedAlpha = 0.0;
    bool late = shape > 0.985;
    float2 uv = (point - (ticketCenter - ticketSize * 0.5)) / ticketSize * imageSize;
    half4 ticketPixel = ticket.sample(raffleDrumSpinTicketSampler, uv / imageSize);
    float3 foil = ticketPixel.a > 0.001h ? float3(ticketPixel.rgb) / float(ticketPixel.a) : float3(0.95, 0.74, 0.36);
    float tint = smoothstep(0.3, 0.75, morph);
    float3 gold = float3(1.0, 0.84, 0.48);
    bool still = peel <= 0.0;
    float4 sharedSeam = (late || !still || length(point - center) > radius + 2.0)
        ? float4(0.0) : raffleDrumSpinMorphSeamAt(point, center, radius, rotation);
    for (int piece = 0; piece < 2; piece++) {
        float sign = piece == 0 ? 1.0 : -1.0;
        float2 coverCenter = center + float2(-sign * opening, 0.0);
        float2 middle = coverCenter + float2(-sign * 0.42 * radius, 0.0);
        float2 peeledPoint = float2(middle.x + (point.x - middle.x) / turn, point.y);
        float2 targetCenter = piece == 0 ? bodyCenter : stubCenter;
        float2 targetBounds = piece == 0
            ? float2(ticketSize.x * body * 0.5, ticketSize.y * 0.5)
            : float2(ticketSize.x * (1.0 - body) * 0.5, ticketSize.y * 0.5);
        float2 initialBounds = float2(0.5 * radius * turn, 0.9 * radius);
        float2 rectangleCenter = mix(middle, targetCenter, shape);
        float2 rectangleBounds = mix(initialBounds, targetBounds, shape);
        float cornerRadius = mix(0.45 * radius, corner, shape);
        float rectangleDistance = raffleDrumSpinMorphBox(
            point - rectangleCenter, rectangleBounds,
            min(cornerRadius, min(rectangleBounds.x, rectangleBounds.y))
        );
        float sphereDistance = (length((peeledPoint - coverCenter) / radius) - 1.0) * radius;
        if (!late && mix(sphereDistance, rectangleDistance, shape) > edge) continue;
        float4 seam = late ? float4(0.0) : (still ? sharedSeam : raffleDrumSpinMorphSeamAt(peeledPoint, coverCenter, radius, rotation));
        float ballDistance = late ? 1e4 : raffleDrumSpinMorphSmoothMax(sphereDistance, -sign * seam.x * radius, 0.22 * radius);
        float distance = late ? raffleDrumSpinMorphBox(point - targetCenter, targetBounds, corner)
            : mix(ballDistance, rectangleDistance, shape);
        float coverage = 1.0 - smoothstep(-edge, edge, distance);
        if (coverage <= 0.001) continue;
        float3 leather = late ? float3(0.0) : raffleDrumSpinMorphLeather(
            peeledPoint, coverCenter, radius, rotation, seam, stitches, morph, glow
        );
        float3 color = mix(leather, foil, tint);
        float rim = exp(-abs(distance) / (1.5 + 4.0 * shape)) * crack * (1.0 - smoothstep(0.75, 1.0, morph));
        color = mix(color, gold * 1.25, rim * 0.8);
        color += gold * sin(3.14159 * shape) * 0.2;
        float pixelAlpha = coverage * mix(1.0, float(ticketPixel.a), smoothstep(0.85, 1.0, morph));
        accumulated += color * pixelAlpha;
        accumulatedAlpha += pixelAlpha;
    }
    accumulatedAlpha = min(accumulatedAlpha, 1.0);
    float3 color = accumulatedAlpha > 0.0 ? accumulated / max(accumulatedAlpha, 0.0001) : float3(0.0);
    float normalizedRadius = length((point - center) / radius);
    float core = crack * (1.0 - smoothstep(0.5, 0.8, morph))
        * (1.0 - smoothstep(0.55, 1.2 + 0.4 * peel, normalizedRadius));
    float3 coreColor = mix(gold, float3(1.0, 0.97, 0.88), exp(-normalizedRadius * normalizedRadius * 3.0));
    float halo = 0.0;
    if (morph < 0.5) {
        halo = glow * exp(-max(normalizedRadius - 1.0, 0.0) * 3.2)
            * (1.0 - smoothstep(1.5, 1.9, normalizedRadius)) * step(1.0, normalizedRadius)
            * (1.0 - morph * 2.0);
    }
    float under = clamp(core + halo, 0.0, 1.0);
    float3 underColor = core > halo ? coreColor : float3(1.0, 0.86, 0.55);
    float outputAlpha = clamp(accumulatedAlpha + under * (1.0 - accumulatedAlpha), 0.0, 1.0);
    float3 outputColor = (color * accumulatedAlpha + underColor * under * (1.0 - accumulatedAlpha)) / max(outputAlpha, 0.0001);
    outputAlpha *= alpha;
    return half4(half3(raffleDrumSpinToP3(min(outputColor, float3(1.0)))) * outputAlpha, outputAlpha);
}

struct RaffleDrumSpinFrameU {
    float4 viewport;
    float4 drum;
    float4 cam;
    float4 misc;
    float4 burst;
    float4 ghost;
};

struct RaffleDrumSpinBallU { float4 pos; float4 rot; float4 fx; };
struct RaffleDrumSpinMeshVertex { packed_float3 p; packed_float3 n; float mat; float spins; };

constant float RaffleDrumSpinNear = 2.8;
constant float RaffleDrumSpinFar = 9.5;
constant sampler raffleDrumSpinLinearSampler(coord::normalized, address::repeat, filter::linear, mip_filter::linear);
constant sampler raffleDrumSpinConfettiSampler(coord::normalized, address::clamp_to_edge, filter::linear, mip_filter::linear);

float raffleDrumSpinDepth(float z) { return clamp((z - RaffleDrumSpinNear) / (RaffleDrumSpinFar - RaffleDrumSpinNear), 0.0, 1.0); }
float4 raffleDrumSpinClip(float2 px, float z, constant RaffleDrumSpinFrameU &f) {
    float2 ndc = float2(px.x / f.viewport.x * 2.0 - 1.0, 1.0 - px.y / f.viewport.y * 2.0);
    return float4(ndc * z, raffleDrumSpinDepth(z) * z, z);
}
float3 raffleDrumSpinRotX(float3 v, float a) { float s = sin(a), c = cos(a); return float3(v.x, c*v.y-s*v.z, s*v.y+c*v.z); }
float3 raffleDrumSpinRotY(float3 v, float a) { float s = sin(a), c = cos(a); return float3(c*v.x+s*v.z, v.y, -s*v.x+c*v.z); }
float3 raffleDrumSpinRotZ(float3 v, float a) { float s = sin(a), c = cos(a); return float3(c*v.x-s*v.y, s*v.x+c*v.y, v.z); }
float3 raffleDrumSpinView(float3 p, constant RaffleDrumSpinFrameU &f) {
    float x1=f.cam.x*p.x+f.cam.y*p.z, z1=-f.cam.y*p.x+f.cam.x*p.z;
    return float3(x1, f.cam.z*p.y-f.cam.w*z1, f.cam.w*p.y+f.cam.z*z1);
}
float3 raffleDrumSpinStudio(float3 r) {
    float up=r.y;
    float3 c=mix(float3(0.08,0.06,0.14),float3(0.46,0.42,0.56),smoothstep(-0.7,0.25,up));
    c+=float3(1.0,0.98,1.02)*smoothstep(0.3,0.9,up)*1.25;
    float strip1=exp(-pow((r.x-0.6)/0.11,2.0))*smoothstep(-0.4,0.4,up);
    float strip2=exp(-pow((r.x+0.66)/0.09,2.0))*smoothstep(-0.3,0.5,up)*0.8;
    c+=float3(1.0,0.98,1.0)*(strip1*1.5+strip2);
    c+=float3(0.5,0.38,0.85)*exp(-pow((r.z-0.9)/0.4,2.0))*0.45;
    c-=float3(0.18)*exp(-pow((up+0.05)/0.07,2.0));
    return max(c,float3(0.0));
}
float3 raffleDrumSpinReflection(float3 r, constant RaffleDrumSpinFrameU &f, texture2d<float> env) {
    if (f.ghost.z < 0.5) return raffleDrumSpinStudio(r);
    float2 uv=float2(atan2(r.x,r.z)/6.2831853+0.5,acos(clamp(r.y,-1.0,1.0))/3.14159265);
    float3 e=env.sample(raffleDrumSpinLinearSampler,uv,level(0.0)).rgb;
    e=mix(float3(dot(e,float3(0.3,0.55,0.15))),e,0.35);
    float3 fill=float3(0.78,0.77,0.84)*smoothstep(0.15,-0.75,r.z)*(0.55+0.45*smoothstep(-0.6,0.5,r.y));
    return e*1.3+pow(e,float3(2.2))*2.4+raffleDrumSpinStudio(r)*0.4+fill;
}

struct RaffleDrumSpinDrumOut {
    float4 position [[position]];
    float3 n;
    float3 v;
    float mat [[flat]];
    float alpha;
};

vertex RaffleDrumSpinDrumOut raffleDrumSpinDrumVertex(
    uint vertexID [[vertex_id]], uint instanceID [[instance_id]],
    const device RaffleDrumSpinMeshVertex *vertices [[buffer(0)]], constant RaffleDrumSpinFrameU &f [[buffer(1)]]) {
    RaffleDrumSpinMeshVertex input=vertices[vertexID];
    float3 sourceP=float3(input.p), sourceN=float3(input.n);
    float3 wp=sourceP, wn=sourceN;
    float ghost=float(instanceID);
    if (input.spins>0.5) { float a=f.drum.w-ghost*f.ghost.x; wp=raffleDrumSpinRotX(sourceP,a); wn=raffleDrumSpinRotX(sourceN,a); }
    float3 vp=raffleDrumSpinView(wp,f), vn=raffleDrumSpinView(wn,f);
    float z=f.misc.x+vp.z;
    float2 px=f.drum.xy+float2(vp.x,-vp.y)*f.drum.z/z;
    RaffleDrumSpinDrumOut out;
    out.position=raffleDrumSpinClip(px,z,f); out.n=vn; out.v=-float3(vp.x,vp.y,z); out.mat=input.mat;
    out.alpha=instanceID>0 ? 0.55*(1.0-ghost/(f.ghost.y+1.0)) : 1.0;
    if (instanceID>0 && input.spins<0.5) out.position=float4(2.0,2.0,0.0,1.0);
    return out;
}

fragment float4 raffleDrumSpinDrumFragment(
    RaffleDrumSpinDrumOut i [[stage_in]], constant RaffleDrumSpinFrameU &f [[buffer(1)]], texture2d<float> env [[texture(0)]]) {
    float3 v=normalize(i.v), n=normalize(i.n); if(dot(n,v)<0.0)n=-n;
    float nv=max(dot(n,v),0.0); float3 r=reflect(-v,n); float3 e=raffleDrumSpinReflection(r,f,env);
    float3 tint=float3(0.93,0.93,0.98); float rough=0.0;
    if(i.mat>0.5&&i.mat<1.5){tint=float3(0.82,0.82,0.88);e=mix(e,raffleDrumSpinReflection(normalize(n+float3(0,0.3,0)),f,env),0.35);}
    if(i.mat>1.5&&i.mat<2.5){tint=float3(0.34,0.33,0.4);e=mix(e,raffleDrumSpinReflection(n,f,env),0.35)*0.8+float3(0.035,0.03,0.05);}
    if(i.mat>2.5){tint=float3(0.05,0.045,0.06);rough=1.0;}
    float fres=0.55+0.45*pow(1.0-nv,4.0); float3 col=e*tint*fres;
    if(rough>0.5){float3 l=normalize(float3(-0.4,0.8,0.5));col=tint+float3(1,0.97,0.94)*pow(max(dot(reflect(-l,n),v),0.0),48.0)*0.9+float3(0.3,0.18,0.62)*pow(1.0-nv,2.5)*0.8;}
    float3 key=normalize(float3(-0.55,0.7,0.45));
    col+=float3(1,0.86,0.62)*pow(max(dot(r,key),0.0),90.0)*0.55*(1.0-rough);
    col+=float3(0.5,0.3,1)*pow(1.0-nv,3.0)*0.16*(1.0+f.misc.z);
    return float4(col*i.alpha,i.alpha);
}

struct RaffleDrumSpinBallOut { float4 position [[position]]; float2 q; uint id [[flat]]; };
vertex RaffleDrumSpinBallOut raffleDrumSpinBallVertex(
    uint vi [[vertex_id]], uint ii [[instance_id]], constant RaffleDrumSpinFrameU &f [[buffer(1)]], constant RaffleDrumSpinBallU *balls [[buffer(2)]]) {
    const float2 corners[6]={float2(-1,-1),float2(1,-1),float2(1,1),float2(-1,-1),float2(1,1),float2(-1,1)};
    RaffleDrumSpinBallU b=balls[ii]; float margin=1.0+0.9*b.fx.x; float2 q=corners[vi]*margin;
    RaffleDrumSpinBallOut out; out.position=raffleDrumSpinClip(b.pos.xy+q*b.pos.z,b.pos.w,f); if(b.rot.w<=0.001)out.position=float4(2,2,0,1); out.q=q; out.id=ii; return out;
}
float3 raffleDrumSpinSeam(float t){const float A=0.78,B=0.22;return float3(A*cos(t)+B*cos(3*t),A*sin(t)-B*sin(3*t),2*sqrt(A*B)*sin(2*t));}
struct RaffleDrumSpinBallFrag { float4 color [[color(0)]]; float depth [[depth(any)]]; };
fragment RaffleDrumSpinBallFrag raffleDrumSpinBallFragment(
    RaffleDrumSpinBallOut i [[stage_in]], constant RaffleDrumSpinFrameU &f [[buffer(1)]], constant RaffleDrumSpinBallU *balls [[buffer(2)]]) {
    RaffleDrumSpinBallU b=balls[i.id]; float d2=dot(i.q,i.q), glow=b.fx.x; RaffleDrumSpinBallFrag out;
    if(d2>1.0){float rr=sqrt(d2);float h=glow*exp(-(rr-1)*3.2)*(1-smoothstep(1.5,1.9,rr));if(h<0.004)discard_fragment();float3 hc=mix(float3(1,0.86,0.55),float3(0.72,0.55,1),smoothstep(1,1.6,rr));out.color=float4(hc*h,h)*b.rot.w;out.depth=raffleDrumSpinDepth(b.pos.w);return out;}
    float3 n=float3(i.q.x,-i.q.y,sqrt(1-d2)); float3 o=raffleDrumSpinRotZ(raffleDrumSpinRotY(raffleDrumSpinRotX(n,b.rot.x),b.rot.y),b.rot.z);
    float best=9,bt=0;for(int k=0;k<48;k++){float t=float(k)*0.1309,dd=distance(o,raffleDrumSpinSeam(t));if(dd<best){best=dd;bt=t;}}
    for(int k=0;k<6;k++){float h=0.065/float(k+1),da=distance(o,raffleDrumSpinSeam(bt-h)),db=distance(o,raffleDrumSpinSeam(bt+h));if(da<best){best=da;bt-=h;}if(db<best){best=db;bt+=h;}}
    float3 sp=raffleDrumSpinSeam(bt),tg=normalize(raffleDrumSpinSeam(bt+0.01)-raffleDrumSpinSeam(bt-0.01));float vv=abs(dot(o-sp,cross(sp,tg)));float u=bt*24/6.2831853;
    float line=abs(fract(u*4+vv*9)-0.5),stitch=(1-smoothstep(0.1,0.2,line))*smoothstep(0.018,0.03,vv)*(1-smoothstep(0.075,0.09,vv)),groove=1-smoothstep(0,0.016,vv);
    float3 L=normalize(float3(-0.5,0.6,0.65));float diff=0.5+0.5*dot(n,L),spec=pow(max(0.0,dot(reflect(-L,n),float3(0,0,1))),22.0),rim=pow(1-n.z,2.2);
    float3 col=float3(0.97,0.95,0.91)*(0.42+0.62*diff);col=mix(col,col*0.8,groove*0.6);col=mix(col,float3(0.78,0.14,0.16),stitch);col+=float3(0.55,0.35,1)*rim*0.45+float3(spec*0.35);col*=1-0.35*smoothstep(0.2,-0.9,n.y);col+=float3(1,0.85,0.5)*glow*rim*0.8;
    float edge=1-smoothstep(1-2/b.pos.z,1,sqrt(d2)),a=edge*b.rot.w;out.color=float4(col*a,a);float worldR=b.pos.z*b.pos.w/f.drum.z;out.depth=raffleDrumSpinDepth(b.pos.w-worldR*n.z);return out;
}

struct RaffleDrumSpinFoilOut { float4 position [[position]]; float3 n; float a; float hue; float2 uv; float kind [[flat]]; };
// Keep the WGSL operation order: the fractional hash selects each atlas cell,
// trajectory and rotation. Disabling contraction changes those selections.
float raffleDrumSpinH1(float i,float s){return fract(sin(i*127.1+s*311.7)*43758.5453);}
float raffleDrumSpinCellScale(float c){if(c==0||c==1)return 0.9;if(c==4||c==10)return 0.62;if(c==7)return 0.55;if(c==11)return 0.7;return 1;}
vertex RaffleDrumSpinFoilOut raffleDrumSpinFoilVertex(uint vi [[vertex_id]],uint ii [[instance_id]],constant RaffleDrumSpinFrameU &f [[buffer(1)]]){
    const float2 corners[6]={float2(-1,-1),float2(1,-1),float2(1,1),float2(-1,-1),float2(1,1),float2(-1,1)};float i=float(ii),t=f.misc.w;RaffleDrumSpinFoilOut out;
    if(t<0){out.position=float4(2,2,0,1);out.a=0;return out;}float pr=f.viewport.z,pick=raffleDrumSpinH1(i,14),kind=0,cell=floor(raffleDrumSpinH1(i,15)*11.999);
    if(raffleDrumSpinH1(i,16)<0.35)cell=floor(raffleDrumSpinH1(i,17)*3.999);if(pick>0.86){kind=1;cell=12+step(0.5,raffleDrumSpinH1(i,18));}if(pick>0.96){kind=2;cell=14+step(0.6,raffleDrumSpinH1(i,18));}
    float ang=raffleDrumSpinH1(i,1),up=raffleDrumSpinH1(i,2),sp=(260+560*pow(raffleDrumSpinH1(i,3),0.8))*pr;if(kind==2)sp*=0.45;float drag=3.2,travel=sp*(1-exp(-drag*t))/drag;
    float2 dir=normalize(float2(cos(ang*6.2831853)*1.25,sin(ang*6.2831853)*0.75-0.45*up));float fall=150*pr*t*t*0.5+26*pr*t;if(kind==2)fall*=0.3;
    float sway=sin(t*(3+3*raffleDrumSpinH1(i,4))+i)*14*pr*smoothstep(0.25,1.0,t);float2 c=f.burst.xy+dir*(f.burst.z*0.7+travel)+float2(sway,fall);
    float halfSize=(8+6*pow(raffleDrumSpinH1(i,5),1.6))*pr*raffleDrumSpinCellScale(cell);float3 rp,rn=float3(0,0,1);float2 corner=corners[vi];
    if(kind==0){float3 axis=normalize(float3(raffleDrumSpinH1(i,7)-0.5,raffleDrumSpinH1(i,8)-0.5,raffleDrumSpinH1(i,9)-0.5)+float3(0,0,0.01));float spin=t*(5+8*raffleDrumSpinH1(i,10))*exp(-t*0.35)+raffleDrumSpinH1(i,11)*6.28;float3 local=float3(corner*halfSize,0),ca=cos(spin),sa=sin(spin);rp=local*ca+cross(axis,local)*sa+axis*dot(axis,local)*(1-ca);rn=float3(0,0,1)*ca+cross(axis,float3(0,0,1))*sa+axis*axis.z*(1-ca);}
    else if(kind==1){halfSize=(9+12*raffleDrumSpinH1(i,5))*pr*(0.55+0.45*sin(t*(9+6*raffleDrumSpinH1(i,19))+i*2));float r=raffleDrumSpinH1(i,20)*0.6;rp=float3(float2(corner.x*cos(r)-corner.y*sin(r),corner.x*sin(r)+corner.y*cos(r))*halfSize,0);}
    else{halfSize=(13+14*raffleDrumSpinH1(i,5))*pr;rp=float3(corner*halfSize,0);}out.position=raffleDrumSpinClip(c+rp.xy,3,f);out.n=rn;
    float life=1-smoothstep(1.4+0.6*raffleDrumSpinH1(i,12),2.3,t);out.a=life*smoothstep(0,0.05,t);if(kind==2)out.a*=0.32*smoothstep(0,0.3,t);out.hue=raffleDrumSpinH1(i,13);float col=cell-4*floor(cell/4),row=floor(cell/4);out.uv=(float2(col,row)+corner*0.5+0.5)/4;out.kind=kind;return out;
}
fragment float4 raffleDrumSpinFoilFragment(RaffleDrumSpinFoilOut i [[stage_in]],constant RaffleDrumSpinFrameU &f [[buffer(1)]],texture2d<float> conf [[texture(1)]]){
    float4 tex=conf.sample(raffleDrumSpinConfettiSampler,i.uv);float facing=abs(normalize(i.n).z);if(f.ghost.w<0.5){float3 base=float3(0.98,0.76,0.36);if(i.hue>0.6)base=float3(0.62,0.42,1);if(i.kind>0.5)return 0;float3 col=base*(0.3+0.7*facing)+float3(1,0.97,0.9)*pow(facing,30.0)*0.8;return float4(col*i.a,i.a);}
    if(i.kind==1){float3 lc=min(tex.rgb*tex.a*i.a*1.4,float3(1));return float4(lc,max(lc.r,max(lc.g,lc.b)));}if(i.kind==2)return float4(tex.rgb*tex.a*i.a,tex.a*i.a);
    float lum=dot(tex.rgb,float3(0.3,0.55,0.15));float3 col=tex.rgb*(0.38+0.75*facing);col+=float3(1,0.96,0.86)*pow(facing,18.0)*smoothstep(0.35,0.9,lum)*0.9;float a=tex.a*i.a;return float4(col*a,a);
}


float raffleDrumSpinGlassBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

// Skia RuntimeShader image filters use point-space intermediate surfaces. Their
// BGRA8 child and output are linearly reconstructed under the canvas's device scale.
// This is a rasterization rule, independent of the timeline and lens coordinates.
half4 raffleDrumSpinGlassUnorm(half4 value) {
    return half4(round(clamp(float4(value), 0.0, 1.0) * 255.0) / 255.0);
}
half4 raffleDrumSpinGlassTexel(SwiftUI::Layer image, float2 p) {
    return raffleDrumSpinGlassUnorm(image.sample(p));
}
half4 raffleDrumSpinGlassInput(SwiftUI::Layer image, float2 p) {
    float2 lo = floor(p - 0.5);
    float2 f = fract(p - 0.5);
    half4 a = raffleDrumSpinGlassTexel(image, lo + float2(0.5, 0.5));
    half4 b = raffleDrumSpinGlassTexel(image, lo + float2(1.5, 0.5));
    half4 c = raffleDrumSpinGlassTexel(image, lo + float2(0.5, 1.5));
    half4 d = raffleDrumSpinGlassTexel(image, lo + float2(1.5, 1.5));
    return mix(mix(a, b, half(f.x)), mix(c, d, half(f.x)), half(f.y));
}

/// The same card-only image filter as GLASS_SKSL.
half4 raffleDrumSpinGlassPoint(float2 p, SwiftUI::Layer image, float4 lens, float radius, float k) {
    half4 base = raffleDrumSpinGlassTexel(image, p);
    if (k <= 0.001) return base;
    float2 c = lens.xy + lens.zw * 0.5;
    float2 b = lens.zw * 0.5;
    float d = raffleDrumSpinGlassBox(p - c, b, radius);
    if (d > 1.5) return base;
    float e = 0.6;
    float2 grad = normalize(float2(
        raffleDrumSpinGlassBox(p - c + float2(e, 0.0), b, radius) - raffleDrumSpinGlassBox(p - c - float2(e, 0.0), b, radius),
        raffleDrumSpinGlassBox(p - c + float2(0.0, e), b, radius) - raffleDrumSpinGlassBox(p - c - float2(0.0, e), b, radius)
    ) + 1e-5);
    float edge = clamp(1.0 + d / 22.0, 0.0, 1.0);
    float disp = edge * edge * 5.0 * k;
    float2 q = c + (p - grad * disp - c) * (1.0 - 0.012 * k);
    half4 col = raffleDrumSpinGlassInput(image, q);
    float rim = exp(-abs(d) / 1.4) * (0.35 + 0.4 * clamp(dot(-grad, normalize(float2(1.0, 1.0))), 0.0, 1.0));
    float inner = edge * edge * 0.12;
    col.rgb = half3(float3(col.rgb) + float3(0.92, 0.88, 1.0) * (rim + inner) * k);
    col.a = max(col.a, half(clamp((rim + inner) * k, 0.0, 1.0)));
    return mix(base, col, half(smoothstep(1.5, -1.5, d) * k));
}

[[ stitchable ]] half4 raffleDrumSpinGlass(float2 p, SwiftUI::Layer image, float4 lens, float radius, float k) {
    float2 lo = floor(p - 0.5);
    float2 f = fract(p - 0.5);
    half4 a = raffleDrumSpinGlassUnorm(raffleDrumSpinGlassPoint(lo + float2(0.5,0.5), image, lens, radius, k));
    half4 b = raffleDrumSpinGlassUnorm(raffleDrumSpinGlassPoint(lo + float2(1.5,0.5), image, lens, radius, k));
    half4 c = raffleDrumSpinGlassUnorm(raffleDrumSpinGlassPoint(lo + float2(0.5,1.5), image, lens, radius, k));
    half4 d = raffleDrumSpinGlassUnorm(raffleDrumSpinGlassPoint(lo + float2(1.5,1.5), image, lens, radius, k));
    return mix(mix(a,b,half(f.x)), mix(c,d,half(f.x)), half(f.y));
}

/// drawGlow's radial paint and Plus over the opaque recordBase image, without a new surface.
[[ stitchable ]] half4 raffleDrumSpinBaseGlow(float2 p, half4 base, float2 center, float radius, float glow, half4 ink) {
    float k = glow * 0.5;
    if (k <= 0.004) return base;
    float r = distance(p, center) / radius;
    float a;
    if (r < 0.35) a = mix(k, k * 0.55, clamp(r / 0.35, 0.0, 1.0));
    else if (r < 0.7) a = mix(k * 0.55, k * 0.16, (r - 0.35) / 0.35);
    else a = mix(k * 0.16, 0.0, clamp((r - 0.7) / 0.3, 0.0, 1.0));
    return half4(min(base.rgb + ink.rgb * half(a), half3(1.0)), min(base.a + half(a), half(1.0)));
}
