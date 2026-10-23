#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

constant float StitchWeavePreviewPi = 3.141592653589793;
constexpr sampler StitchWeavePreviewSampler(coord::normalized, address::clamp_to_edge, filter::linear);

static float StitchWeavePreviewSmooth(float x) { x = saturate(x); return x * x * (3.0 - 2.0 * x); }
static float StitchWeavePreviewEase(float x) {
    if (x <= 0.0) return 0.0;
    if (x >= 1.0) return 1.0;
    float u = x;
    for (int i = 0; i < 6; ++i) {
        float a = 1.0 - u;
        float value = 1.95 * a * a * u + 1.05 * a * u * u + u * u * u;
        float derivative = 1.95 * a * a - 1.8 * a * u + 1.95 * u * u;
        u = clamp(u - (value - x) / max(derivative, 0.1), 0.0, 1.0);
    }
    return u * u * (3.0 - 2.0 * u);
}
static float StitchWeavePreviewArc(float u, float amplitude, texture2d<half> atlas) {
    float2 at = float2(saturate(u) * 128.0 + 0.5, 128.5 + saturate(amplitude / 17.0) * 64.0) / float2(512.0, 256.0);
    float2 packed = float2(atlas.sample(StitchWeavePreviewSampler, at).rg);
    float normalized = dot(packed, float2(65280.0, 255.0)) / 65535.0;
    // Extrapolate the plane outside the selvage; only the small correction lookup clamps.
    return (u - 0.5) * 322.4 + (normalized - 0.5) * 16.0;
}
static float3 StitchWeavePreviewRotate(float3 p, float v, device const float *m) {
    float lag = saturate(1.0 - v);
    return float3(dot(float3(m[0], m[1], m[2]) + lag * float3(m[9], m[10], m[11]), p),
                  dot(float3(m[3], m[4], m[5]) + lag * float3(m[12], m[13], m[14]), p),
                  dot(float3(m[6], m[7], m[8]) + lag * float3(m[15], m[16], m[17]), p));
}
static float2 StitchWeavePreviewProject(float2 uv, float4 phase, float4 shape, device const float *m, texture2d<half> atlas) {
    float amplitude = (8.0 + 9.0 * phase.x) * sin(StitchWeavePreviewPi * uv.y);
    float x = StitchWeavePreviewArc(uv.x, max(0.0, amplitude), atlas);
    float y = 245.6 + uv.y * 326.4 - 420.0 + shape.y;
    float leading = 1.0 - (3.0 + 45.0 * phase.x) / 48.0;
    float lift = shape.x * StitchWeavePreviewSmooth(1.0 - max(0.0, (uv.y - leading) * 326.4) / 256.0);
    float z = amplitude * sin(2.0 * StitchWeavePreviewPi * uv.x + 0.6) + lift;
    float3 rotated = StitchWeavePreviewRotate(float3(x, y, z), uv.y, m);
    return float2(196.5, 420.0) + rotated.xy * (950.0 / (950.0 - rotated.z));
}
static float2 StitchWeavePreviewInverse(float2 pixel, float4 phase, float4 shape, device const float *m, texture2d<half> atlas) {
    float2 screen = pixel - float2(196.5, 420.0);
    float a = 950.0 * m[0] + screen.x * m[6], b = 950.0 * m[1] + screen.x * m[7];
    float c = 950.0 * m[3] + screen.y * m[6], d = 950.0 * m[4] + screen.y * m[7];
    float determinant = a * d - b * c;
    float2 plane = 950.0 * float2(d * screen.x - b * screen.y, a * screen.y - c * screen.x) / determinant;
    float2 uv = float2(plane.x / 322.4 + 0.5, (plane.y + 420.0 - shape.y - 245.6) / 326.4);
    // A near-planar estimate plus two bounded Newton corrections; no ray marching or cell scan.
    for (int iteration = 0; iteration < 2; ++iteration) {
        float2 p = StitchWeavePreviewProject(uv, phase, shape, m, atlas);
        float2 du = (StitchWeavePreviewProject(uv + float2(0.0006, 0.0), phase, shape, m, atlas) - p) / 0.0006;
        float2 dv = (StitchWeavePreviewProject(uv + float2(0.0, 0.0006), phase, shape, m, atlas) - p) / 0.0006;
        float2 error = p - pixel;
        float det = du.x * dv.y - dv.x * du.y;
        if (abs(det) > 1.0) uv -= clamp(float2(dv.y * error.x - dv.x * error.y, du.x * error.y - du.y * error.x) / det, float2(-0.15), float2(0.15));
    }
    return uv;
}
static half3 StitchWeavePreviewTile(float2 local, float frame, texture2d<half> atlas) {
    frame = clamp(frame, 0.0, 15.0);
    int a = int(floor(frame)), b = min(15, a + 1);
    float2 point = (local + float2(2.2, 2.4)) * 3.0;
    float2 first = (float2(float(a % 8) * 32.0, float(a / 8) * 40.0) + point) / float2(512.0, 256.0);
    float2 second = (float2(float(b % 8) * 32.0, float(b / 8) * 40.0) + point) / float2(512.0, 256.0);
    return mix(atlas.sample(StitchWeavePreviewSampler, first).rgb, atlas.sample(StitchWeavePreviewSampler, second).rgb, half(fract(frame)));
}
static half3 StitchWeavePreviewFibers(float2 local, float frame, texture2d<half> atlas) {
    frame = clamp(frame, 0.0, 15.0);
    int a = int(floor(frame)), b = min(15, a + 1);
    float2 point = (local + float2(2.2, 2.4)) * 3.0 + float2(256.0, 0.0);
    float2 first = (float2(float(a % 8) * 32.0, float(a / 8) * 40.0) + point) / float2(512.0, 256.0);
    float2 second = (float2(float(b % 8) * 32.0, float(b / 8) * 40.0) + point) / float2(512.0, 256.0);
    return mix(atlas.sample(StitchWeavePreviewSampler, first).rgb, atlas.sample(StitchWeavePreviewSampler, second).rgb, half(fract(frame)));
}
static float StitchWeavePreviewNoise(float2 p) {
    p = fract(p * float2(123.34, 456.21)); p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}
static half4 StitchWeavePreviewCell(
    float2 materialPoint, float2 uv, int column, int row, float4 phase, float4 shape, float4 light, float4 selection,
    device const float *matrix, device const float *weights, texture2d<half> atlas,
    half3 cream, half3 moss, half3 slate, half3 recess, half3 ink, half3 highlight,
    float3 surface, float3 localLight
) {
    if (column < 0 || column >= 52 || row < 0 || row >= 48) return half4(0.0h);
    float2 local = materialPoint - float2(35.3 + float(column) * 6.2, 572.0 - float(row + 1) * 6.8);
    local.y = (local.y - 3.4) / (1.0 + 0.03 * shape.z) + 3.4;
    if (local.x < -1.8 || local.x > 8.05 || local.y < -2.0 || local.y > 10.3) return half4(0.0h);
    float formation = 1.0;
    float delay = matrix[66 + column];
    if (row >= 3 && phase.x < 0.999999) {
        if (phase.x <= 0.0 || float(row) > 4.0 + phase.x * 45.0) return half4(0.0h);
        float rows = 3.0 + 45.0 * StitchWeavePreviewEase((phase.y - delay) / 1.9);
        formation = saturate((rows - float(row + 1) + 0.45) / 0.45);
    }
    if (formation <= 0.0) return half4(0.0h);
    float elapsed = (phase.y - matrix[18 + row] - delay) / max(0.05, phase.w) + phase.z;
    if (row >= 3 && formation >= 0.999 && elapsed > 0.0 && elapsed < 0.16) {
        float settle = -1.8 * sin(StitchWeavePreviewPi * elapsed / 0.16) * exp(-elapsed / 0.06);
        local.y -= settle * saturate((6.3 - local.y) / 5.8);
    }
    half3 yarnData = StitchWeavePreviewTile(local, formation * 15.0, atlas);
    half coverage = yarnData.r;
    half underside = StitchWeavePreviewTile(local - float2(0.45, 0.6), formation * 15.0, atlas).r * 0.22h;
    if (coverage < 0.001h && underside < 0.001h) return half4(0.0h);

    float wave = 0.0;
    if (light.y >= 0.0) {
        float lag = light.w > 0.5 ? 0.0 : saturate((selection.z - 1.0 - float(row)) / max(1.0, selection.z - 1.0)) * 0.22;
        wave = StitchWeavePreviewSmooth((light.y - lag) / (light.w > 0.5 ? 0.18 : 0.16));
    }
    float3 patterns = float3(weights[row * 6], weights[row * 6 + 1], weights[row * 6 + 2]);
    float3 colors = float3(weights[row * 6 + 3], weights[row * 6 + 4], weights[row * 6 + 5]);
    float3 targetPattern = float3(selection.x < 0.5 ? 1.0 : 0.0, selection.x >= 0.5 && selection.x < 1.5 ? 1.0 : 0.0, selection.x >= 1.5 ? 1.0 : 0.0);
    float3 targetColor = float3(selection.y < 0.5 ? 1.0 : 0.0, selection.y >= 0.5 && selection.y < 1.5 ? 1.0 : 0.0, selection.y >= 1.5 ? 1.0 : 0.0);
    patterns = mix(mix(patterns, targetPattern, wave), float3(1.0, 0.0, 0.0), light.z);
    colors = mix(mix(colors, targetColor, wave), float3(1.0, 0.0, 0.0), light.z);
    float2 motifs = float2(atlas.sample(StitchWeavePreviewSampler, float2(float(column) + 0.5, 208.5 + float(row)) / float2(512.0, 256.0)).rg);
    half marked = half(dot(patterns.xy, motifs));
    half3 base = cream * half(colors.x) + moss * half(colors.y) + slate * half(colors.z);
    base = mix(base, ink, marked);
    float2 nxy = float2(yarnData.gb) * 2.0 - 1.0;
    float nz = sqrt(max(0.02, 1.0 - dot(nxy, nxy)));
    float3 normal = normalize(float3(nxy, nz) + 0.3 * surface);
    float diffuse = saturate(dot(normal, localLight));
    float broadLight = saturate(dot(surface, localLight));
    half shade = half((0.86 + 0.14 * diffuse) * (0.66 + 0.34 * broadLight * broadLight));
    half3 lit = base * shade;
    // Broad light wraps the cotton bundle; two or three fine fibers stay attached to its curve.
    lit = mix(lit, base, 0.22h);
    half3 fibers = StitchWeavePreviewFibers(local, formation * 15.0, atlas);
    lit *= 0.965h + 0.050h * fibers.r + 0.020h * fibers.b;
    half ridge = half(pow(nz, 7.0) * 0.11);
    lit = mix(lit, mix(highlight, half3(0.19h, 0.24h, 0.23h), marked), ridge);
    float band = exp(-pow((materialPoint.x - shape.w) / 27.0, 2.0));
    lit += base * half(light.x * band) * (1.0h - marked);
    lit *= half(0.975 + 0.05 * StitchWeavePreviewNoise(floor(materialPoint * 6.0)));
    half alpha = coverage + underside * (1.0h - coverage);
    half3 rgb = lit * coverage + recess * (0.50h * underside * (1.0h - coverage));
    return half4(rgb, alpha);
}

[[ stitchable ]] half4 StitchWeavePreviewMaterial(
    float2 pixel, texture2d<half> atlas, float4 phase, float4 shape, float4 light, float4 selection,
    device const float *matrix, int matrixCount, device const float *weights, int weightCount,
    half4 cream, half4 moss, half4 slate, half4 recess, half4 ink, half4 highlight
) {
    if (pixel.x < 22.0 || pixel.x > 371.0 || pixel.y < 184.0 || pixel.y > 595.0) return half4(0.0h);
    float2 uv = StitchWeavePreviewInverse(pixel, phase, shape, matrix, atlas);
    if (uv.x < -0.025 || uv.x > 1.025 || uv.y < -0.03 || uv.y > 1.04) return half4(0.0h);
    if (distance(StitchWeavePreviewProject(uv, phase, shape, matrix, atlas), pixel) > 0.75) return half4(0.0h);
    float2 point = float2(35.3 + uv.x * 322.4, 245.6 + uv.y * 326.4);
    int column = int(floor(uv.x * 52.0)), row = int(floor((572.0 - point.y) / 6.8));
    float amplitude = (8.0 + 9.0 * phase.x);
    float slopeX = amplitude * (2.0 * StitchWeavePreviewPi / 322.4) * cos(2.0 * StitchWeavePreviewPi * uv.x + 0.6) * sin(StitchWeavePreviewPi * uv.y);
    float slopeY = amplitude * (StitchWeavePreviewPi / 326.4) * sin(2.0 * StitchWeavePreviewPi * uv.x + 0.6) * cos(StitchWeavePreviewPi * uv.y);
    float3 surface = normalize(float3(-slopeX, -slopeY, 1.0));
    float3 worldLight = normalize(float3(-0.42, -0.57, 1.0));
    float3 localLight = normalize(float3(dot(StitchWeavePreviewRotate(float3(1.0, 0.0, 0.0), uv.y, matrix), worldLight),
                                        dot(StitchWeavePreviewRotate(float3(0.0, 1.0, 0.0), uv.y, matrix), worldLight),
                                        dot(StitchWeavePreviewRotate(float3(0.0, 0.0, 1.0), uv.y, matrix), worldLight)));
    half4 result = half4(0.0h);
    // Only overlapping local yarn cells are sampled; all other 2,487 stitches cost nothing here.
    for (int dy = -1; dy <= 1; ++dy) for (int dx = -1; dx <= 1; ++dx) {
        half4 fiber = StitchWeavePreviewCell(point, uv, column + dx, row + dy, phase, shape, light, selection, matrix, weights, atlas,
                                           cream.rgb, moss.rgb, slate.rgb, recess.rgb, ink.rgb, highlight.rgb, surface, localLight);
        result = fiber + result * (1.0h - fiber.a);
    }
    return result;
}
