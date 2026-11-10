package com.ngocdevv.nativecook.blocks.cards.eyewearlenstint

/** The API 33+ AGSL equivalent of the React Native WGSL lens pass. */
internal object EyewearLensTintShader {
    const val SOURCE = """
uniform shader uPhoto;
uniform float4 uLensA;
uniform float4 uLensB;
uniform float4 uTint;
uniform float4 uGlare;
uniform float uTime;

float sq(float v) { return v * v; }
float3 lin(float3 c) { return pow(max(c, float3(0.0)), float3(2.2)); }
float ease(float x) {
    float c = clamp(x, 0.0, 1.0);
    return c * c * (3.0 - 2.0 * c);
}

float3 keepOf(float i) {
    if (i < 0.5) return float3(0.05, 0.07, 0.11);
    if (i < 1.5) return float3(0.75, 0.32, 0.38);
    if (i < 2.5) return float3(0.72, 0.42, 0.10);
    return float3(0.12, 0.42, 0.40);
}

float3 glowOf(float i) {
    if (i < 0.5) return float3(0.25, 0.55, 0.60);
    if (i < 1.5) return float3(1.00, 0.75, 0.80);
    if (i < 2.5) return float3(1.00, 0.85, 0.50);
    return float3(0.50, 0.95, 0.90);
}

float3 photoAt(float2 screen) {
    return lin(uPhoto.eval(screen).rgb);
}

float2 pourMask(float2 q, float which) {
    float k = uTint.z;
    if (k <= 0.0) return float2(0.0);

    bool first = abs(which - uTint.w) < 0.5;
    float outer = which > 0.5 ? 1.0 : -1.0;
    float2 start;
    float t0;
    if (first) {
        start = float2(0.45 * outer, -0.55);
        t0 = 0.0;
    } else {
        start = float2(-0.85 * outer, -0.20);
        t0 = 0.30;
    }
    float u = clamp((k - t0) / (1.0 - t0), 0.0, 1.0);
    float2 p = q - start;
    float dd = length(p * float2(1.0, 0.8)) - max(p.y, 0.0) * 0.45;
    float angle = atan(p.y, p.x);
    float wob = sin(angle * 5.0 + uTime * 4.0) * 0.05 +
        sin(angle * 9.0 - uTime * 6.0) * 0.025;
    float front = u * u * (3.0 - 2.0 * u) * 2.4;
    float mask = 1.0 - smoothstep(front - 0.08, front + 0.02, dd + wob);
    float edge = exp(-sq((dd + wob - front + 0.03) / 0.03)) *
        step(0.001, u) * (1.0 - step(0.999, u));
    return float2(mask, edge);
}

float4 lensPx(float2 screen, float2 centre, float radius, float which) {
    float2 q = (screen - centre) / radius;
    float rr = length(q);
    if (rr > 1.0) return float4(0.0);

    // A minus lens makes the face behind it a little smaller.
    float3 behind = photoAt(centre + (screen - centre) * (0.93 - 0.04 * rr * rr));
    float2 pm = pourMask(q, which);
    float3 keep = mix(keepOf(uTint.x), keepOf(uTint.y), pm.x);
    float3 glow = mix(glowOf(uTint.x), glowOf(uTint.y), pm.x);
    float gradient = mix(0.55, 1.15, ease(q.y * 0.6 + 0.55));
    float3 colour = behind * keep * gradient;

    colour += lin(glow) * 0.16 * ease((q.y - 0.05) / 0.7) *
        (1.0 - smoothstep(0.75, 1.0, rr));

    // A curved studio-window highlight on the upper-left of the glass.
    float highlightDistance = length(q - float2(-0.05, 0.05)) - 0.78;
    float window = exp(-sq(highlightDistance / 0.07)) *
        (1.0 - smoothstep(-0.65, -0.25, q.y)) *
        smoothstep(-0.95, -0.45, q.x) *
        (1.0 - smoothstep(-0.15, 0.30, q.x));
    colour += float3(1.0) * window * 0.36;

    // The hand-driven glare is also part of the loop near its end.
    float glare = uGlare.x;
    float2 glareDirection = normalize(float2(1.0, -0.7));
    float glareDistance = dot(q, glareDirection) - glare * 0.9;
    colour += float3(1.0, 0.98, 0.95) * 0.35 *
        exp(-sq(glareDistance / 0.12)) * (0.12 + 0.88 * abs(glare));

    // The wavering front catches light as it reaches the lens.
    colour += lin(glow) * 0.5 * pm.y;
    colour += float3(0.90, 0.95, 1.0) * 0.08 * smoothstep(0.85, 1.0, rr);
    return float4(colour, 1.0 - smoothstep(0.97, 1.0, rr));
}

float3 encode(float3 c) {
    float3 mapped = c / (float3(1.0) + max(c - float3(0.85), float3(0.0)) * 0.6);
    return pow(clamp(mapped, float3(0.0), float3(1.0)), float3(1.0 / 2.2));
}

half4 main(float2 screen) {
    float4 pixel = lensPx(screen, uLensA.xy, uLensA.z, 0.0);
    if (pixel.w <= 0.0) pixel = lensPx(screen, uLensB.xy, uLensB.z, 1.0);
    if (pixel.w <= 0.0) return half4(0.0);
    float3 encoded = encode(pixel.xyz) * pixel.w;
    return half4(half3(encoded), half(pixel.w));
}
"""
}
