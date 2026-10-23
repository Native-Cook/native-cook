#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

constant float eventPassShareTau = 6.2831853;
constant float eventPassShareDegree = 0.01745329;
constant float3 eventPassShareLuma = float3(0.2126, 0.7152, 0.0722);

static float eventPassShareHash(float2 point) {
    return fract(sin(dot(point, float2(127.1, 311.7))) * 43758.5453);
}

static float eventPassShareNoise(float2 point) {
    float2 cell = floor(point);
    float2 fraction = fract(point);
    fraction = fraction * fraction * (3.0 - 2.0 * fraction);
    float a = eventPassShareHash(cell);
    float b = eventPassShareHash(cell + float2(1.0, 0.0));
    float c = eventPassShareHash(cell + float2(0.0, 1.0));
    float d = eventPassShareHash(cell + float2(1.0, 1.0));
    return mix(mix(a, b, fraction.x), mix(c, d, fraction.x), fraction.y);
}

static float3 eventPassShareThinFilm(float opticalPath) {
    return 0.5 + 0.5 * cos(eventPassShareTau * opticalPath / float3(650.0, 532.0, 450.0));
}

static float eventPassShareGreenInk(SwiftUI::Layer layer, float2 point) {
    float4 textureColor = float4(layer.sample(point));
    float3 color = textureColor.a > 0.004 ? textureColor.rgb / textureColor.a : float3(1.0);
    float luminance = dot(color, eventPassShareLuma);
    return smoothstep(0.035, 0.09, color.g - 0.5 * (color.r + color.b)) *
        (1.0 - smoothstep(0.42, 0.64, luminance)) * textureColor.a;
}

static float4 eventPassShareOver(float4 destination, float3 color, float alpha) {
    return float4(
        color * alpha + destination.rgb * (1.0 - alpha),
        alpha + destination.a * (1.0 - alpha)
    );
}

[[ stitchable ]] half4 eventPassShareFoil(
    float2 position,
    SwiftUI::Layer poster,
    float2 resolution,
    float4 foil
) {
    float amount = foil.z;
    if (amount <= 0.002) return half4(0.0);

    float4 textureColor = float4(poster.sample(position));
    if (textureColor.a < 0.01) return half4(0.0);
    float3 color = textureColor.rgb / textureColor.a;
    float luminance = dot(color, eventPassShareLuma);
    float scale = foil.w;

    float ink = eventPassShareGreenInk(poster, position);
    float gold = smoothstep(0.15, 0.26, color.r - color.b) *
        smoothstep(0.32, 0.48, luminance) * (1.0 - smoothstep(0.86, 0.95, luminance));
    float paper = smoothstep(0.80, 0.93, luminance);

    float2 origin = (position - 0.5 * resolution) / scale;
    float tiltX = foil.x * eventPassShareDegree;
    float tiltY = foil.y * eventPassShareDegree;
    float3 normal = normalize(float3(sin(tiltY), -sin(tiltX), 1.0));
    float3 eye = normalize(float3(-origin, 650.0));
    float3 light = normalize(float3(-0.30, -0.70, 1.0));
    float3 halfVector = normalize(light + eye);
    float2 slope = normal.xy - halfVector.xy;

    float flow = 0.85 * eventPassShareNoise(origin * 0.016 + float2(3.1, 7.7)) +
        0.30 * eventPassShareNoise(origin * 0.055 + float2(11.0, 2.0));
    float phase = dot(slope, float2(0.55, 0.84)) * 7.5 + flow;
    float3 film = eventPassShareThinFilm(400.0 + 470.0 * phase);

    float streak = dot(origin / 125.0, float2(0.97, 0.24)) -
        (sin(tiltY) * 0.97 - sin(tiltX) * 0.24) * 5.2 + 0.15;
    float sheen = exp(-streak * streak / 0.10);
    float glint = exp(-streak * streak / 0.010);
    float environment = 0.5 + 0.5 * sin(
        dot(origin, float2(0.020, 0.008)) * 6.0 +
        dot(slope, float2(1.0, 0.6)) * 16.0 + flow * 2.0
    );
    float reflection = 0.16 + 0.34 * environment * environment + 0.85 * sheen + 0.8 * glint;

    float foilMask = max(ink, gold);
    float2 grainPoint = origin / 0.7;
    float2 grainID = floor(grainPoint);
    float2 grainFraction = fract(grainPoint) - 0.5;
    float randomOne = eventPassShareHash(grainID);
    float randomTwo = eventPassShareHash(grainID + float2(17.31));
    float randomThree = eventPassShareHash(grainID + float2(41.73));
    float2 groove = float2(cos(randomOne * eventPassShareTau), sin(randomOne * eventPassShareTau));
    float twinkle = pow(max(0.0, cos(dot(slope, groove) * 70.0 + randomTwo * eventPassShareTau)), 24.0);
    float sparkle = twinkle * step(mix(0.72, 0.18, foilMask), randomThree) *
        exp(-dot(grainFraction, grainFraction) * 8.0) * (0.6 + sheen);
    float3 sparkleColor = mix(
        float3(1.0),
        eventPassShareThinFilm(420.0 + 600.0 * fract(randomTwo + phase * 0.35)),
        0.55
    );

    float4 accumulated = float4(0.0);
    accumulated = eventPassShareOver(
        accumulated,
        mix(float3(1.0), film, 0.30),
        paper * amount * (0.01 + 0.06 * sheen)
    );

    float3 deep = float3(0.06, 0.25, 0.19) * (0.75 + 0.55 * reflection);
    float3 holographic = mix(float3(0.82, 0.85, 0.84), pow(film, float3(0.8)), 0.80) *
        (0.42 + 0.78 * reflection);
    float lit = clamp((0.40 + 0.50 * environment + 0.70 * sheen) * amount, 0.0, 0.95);
    float3 inkColor = deep * (1.0 - lit) + holographic * lit + float3(glint * 0.40);
    accumulated = eventPassShareOver(accumulated, inkColor, ink * amount * 0.96);

    float3 goldColor = float3(0.84, 0.64, 0.33) * (0.50 + 0.70 * reflection) +
        film * 0.10 * sheen + float3(1.0, 0.94, 0.78) * glint * 0.45;
    accumulated = eventPassShareOver(accumulated, goldColor, gold * amount * 0.92);

    float2 lightDelta = normalize(float2(-0.35, -0.80)) * 0.55 * scale;
    float edge = eventPassShareGreenInk(poster, position + lightDelta) -
        eventPassShareGreenInk(poster, position - lightDelta);
    float emboss = smoothstep(0.0, 0.35, amount);
    accumulated = eventPassShareOver(accumulated, float3(1.0), max(-edge, 0.0) * 0.55 * emboss * ink);
    accumulated = eventPassShareOver(
        accumulated,
        float3(0.02, 0.07, 0.05),
        max(edge, 0.0) * 0.32 * emboss * (1.0 - ink)
    );

    float grainMask = 0.12 * paper * sheen + 0.20 * (1.0 - paper) * (1.0 - foilMask) + 1.2 * foilMask;
    accumulated = eventPassShareOver(
        accumulated,
        sparkleColor,
        clamp(sparkle * amount * grainMask, 0.0, 1.0)
    );

    float3 rgb = accumulated.a > 0.0001 ? accumulated.rgb / accumulated.a : float3(0.0);
    // SwiftUI Metal writes into the view's sRGB color space; Skia's Display-P3 workaround is not needed.
    return half4(half3(rgb * accumulated.a * textureColor.a), half(accumulated.a * textureColor.a));
}

// Round 3 ambient field. Keep these helpers separately namespaced from the foil shader above.
static float eventPassShareAmbientHash(float2 point) {
    return fract(sin(dot(point, float2(127.1, 311.7))) * 43758.5453);
}

static float eventPassShareAmbientNoise(float2 point) {
    float2 cell = floor(point);
    float2 fraction = fract(point);
    fraction = fraction * fraction * (3.0 - 2.0 * fraction);
    float a = eventPassShareAmbientHash(cell);
    float b = eventPassShareAmbientHash(cell + float2(1.0, 0.0));
    float c = eventPassShareAmbientHash(cell + float2(0.0, 1.0));
    float d = eventPassShareAmbientHash(cell + float2(1.0, 1.0));
    return mix(mix(a, b, fraction.x), mix(c, d, fraction.x), fraction.y);
}

static float3 eventPassShareAmbientLabToLinear(float3 color) {
    float l = color.x + 0.3963377774 * color.y + 0.2158037573 * color.z;
    float m = color.x - 0.1055613458 * color.y - 0.0638541728 * color.z;
    float s = color.x - 0.0894841775 * color.y - 1.2914855480 * color.z;
    l = l * l * l;
    m = m * m * m;
    s = s * s * s;
    return float3(
        4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
        -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
        -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
    );
}

static float eventPassShareAmbientEncode(float value) {
    return value <= 0.0031308 ? 12.92 * value : 1.055 * pow(value, 1.0 / 2.4) - 0.055;
}

static half4 eventPassShareAmbientEncodeOut(float3 linear, float p3, float grain) {
    linear = clamp(linear, 0.0, 1.0);
    if (p3 > 0.5) {
        linear = float3(
            dot(linear, float3(0.8225, 0.1774, 0.0)),
            dot(linear, float3(0.0332, 0.9669, 0.0)),
            dot(linear, float3(0.0171, 0.0724, 0.9108))
        );
    }
    float3 encoded = float3(
        eventPassShareAmbientEncode(linear.r),
        eventPassShareAmbientEncode(linear.g),
        eventPassShareAmbientEncode(linear.b)
    ) + grain;
    return half4(half3(clamp(encoded, 0.0, 1.0)), 1.0);
}

static float3 eventPassShareAmbientField(float2 point, float4 ambient) {
    float theta = ambient.x;
    point -= float2(ambient.z, -ambient.y) * 2.2;
    float2 orbit = 0.85 * float2(cos(theta), sin(theta));
    float2 noisePoint = point * 0.0075;
    point += 16.0 * float2(
        eventPassShareAmbientNoise(noisePoint + orbit) - 0.5,
        eventPassShareAmbientNoise(noisePoint - orbit + float2(17.0, 9.0)) - 0.5
    ) * 2.0;

    float3 lab = float3(0.1900, -0.0215, -0.0276);
    float2 delta;
    float weight;

    float2 center = float2(112.0, 214.0) + 26.0 * float2(cos(theta), 0.7 * sin(theta));
    delta = point - center;
    weight = 0.95 * exp(-dot(delta, delta) / 22500.0);
    lab = mix(lab, float3(0.6300, -0.1427, 0.0464), weight);

    center = float2(338.0, 352.0) + 22.0 * float2(cos(theta + 2.1), 0.7 * sin(theta + 2.1));
    delta = point - center;
    weight = 0.85 * exp(-dot(delta, delta) / 16384.0);
    lab = mix(lab, float3(0.6600, -0.1059, -0.0449), weight);

    center = float2(372.0, 226.0) + 14.0 * float2(cos(theta + 4.0), 0.7 * sin(theta + 4.0));
    delta = point - center;
    weight = 0.62 * exp(-dot(delta, delta) / 3600.0);
    lab = mix(lab, float3(0.7900, 0.0529, 0.1188), weight);

    center = float2(52.0, 572.0) + 24.0 * float2(cos(theta + 1.0), 0.7 * sin(theta + 1.0));
    delta = point - center;
    weight = 0.85 * exp(-dot(delta, delta) / 22500.0);
    lab = mix(lab, float3(0.5000, 0.0581, -0.1597), weight);

    center = float2(356.0, 652.0) + 18.0 * float2(cos(theta + 3.3), 0.7 * sin(theta + 3.3));
    delta = point - center;
    weight = 0.6 * exp(-dot(delta, delta) / 10816.0);
    lab = mix(lab, float3(0.5800, 0.1545, -0.0414), weight);

    float hueRotation = ambient.z * 0.02 + ambient.y * 0.015;
    float cosine = cos(hueRotation);
    float sine = sin(hueRotation);
    lab.yz = float2(lab.y * cosine - lab.z * sine, lab.y * sine + lab.z * cosine);
    lab.x *= 1.0 + 0.10 * ambient.w;
    return max(eventPassShareAmbientLabToLinear(lab), 0.0);
}

static float eventPassShareAmbientSdBox(float2 point, float2 bounds, float radius) {
    float2 delta = abs(point) - bounds + radius;
    return length(max(delta, 0.0)) + min(max(delta.x, delta.y), 0.0) - radius;
}

static float eventPassShareAmbientRim(float2 point, float2 center, float2 halfSize, float radius, float scale) {
    float distance = eventPassShareAmbientSdBox(point - center, halfSize, radius) * scale;
    return exp(-(distance - 0.9) * (distance - 0.9) / 0.8);
}

static float3 eventPassShareAmbientThinFilm(float opticalPath) {
    return 0.5 + 0.5 * cos(6.2831853 * opticalPath / float3(650.0, 532.0, 450.0));
}

static float3 eventPassShareAmbientFoilAt(float2 origin, float2 cardTilt) {
    float tiltX = cardTilt.x * 0.01745329;
    float tiltY = cardTilt.y * 0.01745329;
    float3 normal = normalize(float3(sin(tiltY), -sin(tiltX), 1.0));
    float3 eye = normalize(float3(-origin, 650.0));
    float3 light = normalize(float3(-0.30, -0.70, 1.0));
    float3 halfVector = normalize(light + eye);
    float2 slope = normal.xy - halfVector.xy;
    float flow = 0.85 * eventPassShareAmbientNoise(origin * 0.016 + float2(3.1, 7.7)) + 0.15;
    float phase = dot(slope, float2(0.55, 0.84)) * 7.5 + flow;
    return eventPassShareAmbientThinFilm(400.0 + 470.0 * phase);
}

[[ stitchable ]] half4 eventPassShareAmbientPage(
    float2 position,
    half4 inputColor,
    float2 resolution,
    float4 geometry,
    float4 ambient
) {
    float2 reference = float2(position.x / geometry.x, geometry.y + position.y / geometry.x);
    float3 linear = float3(0.0159, 0.0152, 0.0159);
    if (reference.y < 105.0 + geometry.z) {
        float3 field = eventPassShareAmbientField(reference, ambient);
        linear = field * mix(0.7, 0.95, smoothstep(10.0, 70.0, reference.y));
    }
    return eventPassShareAmbientEncodeOut(linear, geometry.w, (eventPassShareAmbientHash(floor(position * 3.0)) - 0.5) * 0.012);
}

[[ stitchable ]] half4 eventPassShareAmbientSheet(
    float2 position,
    half4 inputColor,
    float2 resolution,
    float4 geometry,
    float4 ambient,
    float4 card
) {
    float scale = geometry.x;
    float2 reference = float2(position.x / scale, geometry.y + position.y / scale);
    float2 sheetPoint = reference + float2(0.0, geometry.z);
    float3 field = eventPassShareAmbientField(sheetPoint, ambient);
    float bleed = exp(-(reference.y - 61.0) / 38.0);
    float3 charcoal = float3(0.0159, 0.0152, 0.0159);
    float3 linear = mix(charcoal, field * 0.9, 0.2 + 0.22 * bleed);

    float below = reference.y - 734.0;
    if (below > 0.0) {
        float3 mirrored = eventPassShareAmbientField(float2(reference.x, 734.0 - below * 1.6) + float2(0.0, geometry.z), ambient);
        linear += mirrored * 0.3 * exp(-below / 70.0) * smoothstep(0.0, 6.0, below);
    }

    float topEdge = (reference.y - 61.0) * scale;
    linear += (float3(0.10) + field * 0.6) * exp(-topEdge * topEdge / 0.9) * (0.55 + 0.45 * exp(-abs(reference.x - 201.0) / 120.0));

    float2 shift = float2(card.y, -card.x) * 1.6;
    float2 cardCenter = float2(201.5, 464.3) + shift;
    float2 cardHalfSize = float2(135.15, 191.0);
    float cardDistance = eventPassShareAmbientSdBox(reference - cardCenter, cardHalfSize, 23.0);
    float reach = cardDistance > 0.0 ? exp(-cardDistance / 20.0) : exp(cardDistance / 5.0);
    float2 direction = reference - cardCenter;
    float face = 0.65 + 0.35 * dot(normalize(direction + 0.0001), normalize(float2(card.y, -card.x) + 0.0001)) * min(1.0, length(card.xy) / 4.0);
    float spill = (0.12 + (1.0 - 0.12) * card.z) * reach * face;
    float3 spillColor = field * 1.6 + 0.04;
    if (spill > 0.003) {
        float2 origin = clamp(direction, -cardHalfSize, cardHalfSize) * (250.0 / 270.3);
        spillColor = mix(spillColor, eventPassShareAmbientFoilAt(origin, card.xy) * 0.9, 0.65);
    }

    float2 stageCenter = float2(201.0, 464.0);
    float2 stageHalfSize = float2(180.5, 270.0);
    float stageDistance = eventPassShareAmbientSdBox(reference - stageCenter, stageHalfSize, 14.0) * scale;
    float inStage = clamp(0.5 - stageDistance * 3.0, 0.0, 1.0);
    float gain = reference.y < 330.0
        ? mix(1.25, 0.7, smoothstep(236.0, 330.0, reference.y))
        : mix(0.7, 0.38, smoothstep(330.0, 690.0, reference.y));
    float3 window = field * gain + spillColor * spill * 0.55;
    float top = 1.0 - smoothstep(194.0, 234.0, reference.y);
    window += float3(0.9, 0.95, 1.0) * exp(-(stageDistance + 0.6) * (stageDistance + 0.6) / 0.35) * (0.05 + 0.16 * top);
    linear = mix(linear + spillColor * spill * 0.18, window, inStage);

    float3 dispersion = float3(0.0);
    if (reference.y > 749.0) {
        float2 center = float2(201.0, 801.0);
        float2 halfSize = float2(180.5, 25.5);
        dispersion.r += eventPassShareAmbientRim(reference + 0.35, center, halfSize, 25.5, scale);
        dispersion.g += eventPassShareAmbientRim(reference, center, halfSize, 25.5, scale);
        dispersion.b += eventPassShareAmbientRim(reference - 0.35, center, halfSize, 25.5, scale);
    } else if (reference.y < 170.0) {
        float2 center = float2(201.0, 148.0);
        float2 halfSize = float2(79.0, 17.0);
        dispersion.r += eventPassShareAmbientRim(reference + 0.3, center, halfSize, 17.0, scale);
        dispersion.g += eventPassShareAmbientRim(reference, center, halfSize, 17.0, scale);
        dispersion.b += eventPassShareAmbientRim(reference - 0.3, center, halfSize, 17.0, scale);
        float2 closePoint = reference - float2(363.0, 100.0);
        float dr = (length(closePoint + 0.3) - 22.0) * scale;
        float dg = (length(closePoint) - 22.0) * scale;
        float db = (length(closePoint - 0.3) - 22.0) * scale;
        dispersion += float3(exp(-(dr - 0.9) * (dr - 0.9) / 0.8), exp(-(dg - 0.9) * (dg - 0.9) / 0.8), exp(-(db - 0.9) * (db - 0.9) / 0.8));
    }
    linear += dispersion * (field * 1.4 + 0.05) * 0.5;
    float grain = (eventPassShareAmbientHash(floor(position * 3.0)) - 0.5) * mix(0.010, 0.018, inStage);
    return eventPassShareAmbientEncodeOut(linear, geometry.w, grain);
}
