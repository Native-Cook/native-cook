/** Paper texture, facet lighting, bin clipping, and soft-shadow shaders. */
#include <metal_stdlib>
using namespace metal;

struct CrumpleToDeleteVertex {
    float2 position;
    float2 uv;
    float4 color;
};

struct CrumpleToDeleteUniforms {
    float4 viewport;
    float4 ellipse;
    float4 pivot;
    float4 box;
    float4 params;
    float4 tint;
};

struct CrumpleToDeleteVarying {
    float4 position [[position]];
    float2 uv;
    float4 color;
    float2 point;
};

/** Projects the latest CPU-deformed paper mesh and the bin's pivot rotation. */
vertex CrumpleToDeleteVarying crumpleToDeleteVertex(
    uint id [[vertex_id]],
    constant CrumpleToDeleteVertex *vertices [[buffer(0)]],
    constant CrumpleToDeleteUniforms &uniforms [[buffer(1)]]
) {
    CrumpleToDeleteVertex inputVertex = vertices[id];
    float2 point = inputVertex.position;
    float2 delta = point - uniforms.pivot.xy;
    float cs = cos(uniforms.viewport.w);
    float sn = sin(uniforms.viewport.w);
    point = uniforms.pivot.xy + float2(delta.x * cs - delta.y * sn, delta.x * sn + delta.y * cs);
    CrumpleToDeleteVarying output;
    output.position = float4(point.x / uniforms.viewport.x * 2 - 1, 1 - point.y / uniforms.viewport.y * 2, 0, 1);
    output.uv = inputVertex.uv;
    output.color = inputVertex.color;
    output.point = inputVertex.position;
    return output;
}

static bool crumpleToDeleteOutside(float2 point, constant CrumpleToDeleteUniforms &uniforms) {
    return uniforms.viewport.z > 0.5 && length((point - uniforms.ellipse.xy) / uniforms.ellipse.zw) > 1;
}

/** Resolves the paper's texture and facet shade together, including straight-alpha bin artwork. */
fragment float4 crumpleToDeleteTexture(
    CrumpleToDeleteVarying inputVertex [[stage_in]],
    constant CrumpleToDeleteUniforms &uniforms [[buffer(1)]],
    texture2d<float> image [[texture(0)]]
) {
    if (crumpleToDeleteOutside(inputVertex.point, uniforms)) discard_fragment();
    constexpr sampler textureSampler(coord::normalized, address::clamp_to_edge, filter::linear);
    float4 texel = image.sample(textureSampler, inputVertex.uv);
    float alphaFactor = uniforms.params.w > 0.5 ? texel.a : 1.0;
    return float4(texel.rgb * alphaFactor * inputVertex.color.rgb, texel.a) * uniforms.params.z;
}

fragment float4 crumpleToDeleteShade(
    CrumpleToDeleteVarying inputVertex [[stage_in]],
    constant CrumpleToDeleteUniforms &uniforms [[buffer(1)]]
) {
    if (crumpleToDeleteOutside(inputVertex.point, uniforms)) discard_fragment();
    return inputVertex.color;
}

fragment float4 crumpleToDeleteSolid(
    CrumpleToDeleteVarying inputVertex [[stage_in]],
    constant CrumpleToDeleteUniforms &uniforms [[buffer(1)]]
) {
    if (crumpleToDeleteOutside(inputVertex.point, uniforms)) discard_fragment();
    return uniforms.tint;
}

static float crumpleToDeleteErf(float x) {
    float direction = sign(x);
    x = abs(x);
    float t = 1 / (1 + 0.3275911 * x);
    return direction * (1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736)
        * t + 0.254829592) * t * exp(-x * x));
}

/** Integrates the rounded paper shadow with the original 24-tap Gaussian approximation. */
fragment float4 crumpleToDeleteShadow(
    CrumpleToDeleteVarying inputVertex [[stage_in]],
    constant CrumpleToDeleteUniforms &uniforms [[buffer(1)]]
) {
    float2 point = inputVertex.point - uniforms.box.xy;
    float2 halfSize = uniforms.box.zw * 0.5;
    float radius = min(uniforms.params.x, min(halfSize.x, halfSize.y));
    float sigma = uniforms.params.y;
    float integral = 0;
    const int taps = 24;
    float start = max(-halfSize.y, point.y - 3 * sigma);
    float end = min(halfSize.y, point.y + 3 * sigma);
    if (end <= start) return float4(0);
    float step = (end - start) / taps;
    for (int i = 0; i < taps; i++) {
        float yy = start + (i + 0.5) * step;
        float corner = max(0.0, abs(yy) - (halfSize.y - radius));
        float extent = halfSize.x - radius + sqrt(max(0.0, radius * radius - corner * corner));
        float xIntegral = 0.5 * (crumpleToDeleteErf((extent - point.x) / (sigma * 1.41421356237))
            - crumpleToDeleteErf((-extent - point.x) / (sigma * 1.41421356237)));
        float dy = (point.y - yy) / sigma;
        integral += xIntegral * exp(-0.5 * dy * dy) / (sigma * 2.50662827463) * step;
    }
    float alpha = integral * uniforms.params.z;
    return float4(uniforms.tint.rgb * alpha, alpha);
}
