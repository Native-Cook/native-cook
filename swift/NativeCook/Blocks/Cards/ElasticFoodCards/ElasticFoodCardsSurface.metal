#include <metal_stdlib>
using namespace metal;

// Return a smoothstep's value and its analytic derivative in point coordinates.
static float2 elasticFoodCardsSurfaceSmooth(float a, float b, float x) {
    float u = clamp((x - a) / (b - a), 0.0f, 1.0f);
    float derivative = (x > a && x < b) ? 6.0f * u * (1.0f - u) / (b - a) : 0.0f;
    return float2(u * u * (3.0f - 2.0f * u), derivative);
}

// One Gaussian and its analytic spatial gradient. Width varies along the ray.
static float3 elasticFoodCardsSurfaceGaussian(float d, float2 dd, float width, float2 dw) {
    float s = d / width;
    float value = exp(-0.5f * s * s);
    float2 ds = dd / width - d * dw / (width * width);
    return float3(value, -value * s * ds);
}

[[ stitchable ]] half4 elasticFoodCardsSurface(float2 position, half4 inputColor,
                                               float2 anchor, float amplitude, float fanWidth,
                                               float tailY, float contact) {
    if (amplitude <= 0.0f || fanWidth <= 0.0f) { return inputColor; }

    float2 left = elasticFoodCardsSurfaceSmooth(0.0f, 14.0f, position.x);
    float2 right = elasticFoodCardsSurfaceSmooth(379.0f, 393.0f, position.x);
    float2 top = elasticFoodCardsSurfaceSmooth(40.0f, 56.0f, position.y);
    float2 bottom = elasticFoodCardsSurfaceSmooth(tailY - 40.0f, tailY + 8.0f, position.y);
    float bx = left.x * (1.0f - right.x);
    float by = top.x * (1.0f - bottom.x);
    float boundary = bx * by;
    if (boundary <= 0.0f) { return inputColor; }
    float2 boundaryGradient = float2(
        (left.y * (1.0f - right.x) - left.x * right.y) * by,
        (top.y * (1.0f - bottom.x) - top.x * bottom.y) * bx
    );

    constexpr float2 ends[10] = {
        float2(8, 32), float2(25, 110), float2(67, 185), float2(94, 222),
        float2(167, 250), float2(235, 248), float2(308, 221), float2(332, 183),
        float2(364, 112), float2(388, 30)
    };
    constexpr float bends[10] = {-8, 13, -11, 16, -6, 9, -15, 10, -13, 7};
    constexpr float weights[10] = {0.68, 0.82, 0.92, 0.78, 1.0, 0.91, 0.83, 0.95, 0.76, 0.65};

    float height = 0.0f;
    float2 heightGradient = float2(0.0f);
    float valley = 0.0f;
    float widthScale = clamp(fanWidth, 0.0f, 378.0f) / 378.0f;
    float fanGain = elasticFoodCardsSurfaceSmooth(0.0f, 24.0f, fanWidth).x;
    float strength = clamp(amplitude, 0.0f, 24.0f) * fanGain;

    for (int i = 0; i < 10; ++i) {
        // Endpoints stay on the page as the grip rises. Upper side lobes also rise above the fist.
        float2 end = float2(anchor.x + (ends[i].x - 190.0f) * widthScale * 1.2f,
                            ends[i].y * tailY / 250.0f);
        float2 D = end - anchor;
        float lengthSquared = max(dot(D, D), 1.0f);
        float inverseLength = rsqrt(lengthSquared);
        float2 N = float2(-D.y, D.x) * inverseLength;
        float2 r = position - anchor;
        float rawT = dot(r, D) / lengthSquared;
        float t = clamp(rawT, 0.0f, 1.0f);
        if (rawT <= 0.0f || rawT >= 1.0f) { continue; }
        float2 dt = D / lengthSquared;
        float bend = bends[i] * widthScale;
        float d = dot(r, N) - bend * 4.0f * t * (1.0f - t);
        float2 dd = N - bend * 4.0f * (1.0f - 2.0f * t) * dt;
        float width = 2.4f + 4.5f * t;
        float shoulderWidth = 5.0f + 6.0f * t;
        float3 narrow = elasticFoodCardsSurfaceGaussian(d, dd, width, 4.5f * dt);
        float3 broad = elasticFoodCardsSurfaceGaussian(d, dd, shoulderWidth, 6.0f * dt);
        float profile = 0.55f * broad.x - narrow.x;
        float2 profileGradient = 0.55f * broad.yz - narrow.yz;
        float envelope = 16.0f * t * t * (1.0f - t) * (1.0f - t);
        float envelopeDerivative = 32.0f * t * (1.0f - t) * (1.0f - 2.0f * t);
        float gain = strength * weights[i];
        height += gain * envelope * profile;
        heightGradient += gain * (envelope * profileGradient + profile * envelopeDerivative * dt);
        valley += weights[i] * envelope * narrow.x;
    }

    // The product rule is necessary: the edge feather is a physical flattening of the surface.
    heightGradient = boundary * heightGradient + height * boundaryGradient;
    float3 normal = normalize(float3(-heightGradient.x, -heightGradient.y, 1.0f));
    float3 light = normalize(float3(-0.4f, -0.6f, 0.7f));
    float diffuse = max(0.0f, dot(normal, light));
    float flatLighting = 0.72f + 0.28f * light.z;
    float lighting = (0.72f + 0.28f * diffuse) / flatLighting;
    float ao = min(0.18f, 0.055f * (strength / 24.0f) * boundary * valley
                   * (0.3f + 0.7f * clamp(contact, 0.0f, 1.0f)));
    float3 halfVector = normalize(light + float3(0, 0, 1));
    float specular = 0.035f * max(0.0f, pow(max(0.0f, dot(normal, halfVector)), 8.0f)
                                 - pow(halfVector.z, 8.0f));
    float factor = lighting * (1.0f - ao) + specular;
    // SwiftUI supplies premultiplied color in the destination space. Preserve its neutral base.
    return half4(half3(clamp(float3(inputColor.rgb) * factor, 0.0f, float(inputColor.a))), inputColor.a);
}
