#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

namespace ThermalGlassTilesShader {
inline float mod(float x, float y) { return x - y * floor(x / y); }
inline float hash21(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
inline float noise(float2 p) {
    float2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + float2(1.0, 0.0)), f.x),
               mix(hash21(i + float2(0.0, 1.0)), hash21(i + float2(1.0)), f.x), f.y);
}
inline float3 linearize(float3 c) {
    return mix(c / 12.92, pow((c + .055) / 1.055, float3(2.4)), step(float3(.04045), c));
}
inline float3 encode(float3 c) {
    return mix(c * 12.92, 1.055 * pow(max(c, float3(0.0)), float3(1.0 / 2.4)) - .055, step(float3(.0031308), c));
}
inline float boxSDF(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}
inline float sdf(float2 p, float4 rr, float r, float dome) {
    float2 ctr = rr.xy + rr.zw * .5;
    if (dome > .5) return length(p - ctr) - rr.z * .5;
    return boxSDF(p - ctr, rr.zw * .5, r);
}

// No resampling or resizing when packing. Each child has the RN ImageShader's
// linear / clamp / no-mipmap sampling, including pixel-centre coordinates.
inline float4 sampleImage(texture2d<half> map, float2 p, bool soft, bool palette = false) {
    float2 imageSize = palette ? float2(256.0, 67.0) : (soft ? float2(384.0, 320.0) : float2(1536.0, 1280.0));
    float2 origin = float2(0.0, palette ? 1604.0 : (soft ? 1282.0 : 0.0));
    constexpr sampler smp(coord::normalized, address::clamp_to_edge, filter::linear);
    float2 uv = (origin + clamp(p, float2(.5), imageSize - .5)) / float2(map.get_width(), map.get_height());
    return float4(map.sample(smp, uv));
}
inline float2 atlasUV(float2 uv, float f, float2 texSize) {
    float2 grid = float2(6.0, 4.0);
    float idx = mod(floor(f), grid.x * grid.y);
    if (idx < 0.0) idx += grid.x * grid.y;
    float2 cell = float2(mod(idx, grid.x), floor(idx / grid.x));
    float2 pad = .5 * grid / texSize;
    return (cell + clamp(uv, pad, 1.0 - pad)) / grid * texSize;
}
inline float4 imageHeat(float2 p, float f, bool soft, texture2d<half> map) {
    float3 figure = float3(201.0, 250.0, 1.173);
    float2 uv = (figure.xy + (p - figure.xy) / max(figure.z, .01)) / float2(402.0, 874.0);
    return sampleImage(map, atlasUV(uv, f, soft ? float2(384.0, 320.0) : float2(1536.0, 1280.0)), soft);
}

struct Scene {
    float scale;
    float2 offset;
    float time, frame, effort, streak, direction, peak, circlePeak, smallPeak;
    float3 touch;
    float wake, inkDark, pixelScale;

    /// FIELD_SKSL, including the source's sRGB -> encoded Display P3 output.
    half4 field(float2 xy, bool soft, texture2d<half> map) {
        float2 p = (xy - offset) / scale;
        float loopAngle = time * .78539816;
        float2 flow = float2(cos(loopAngle), sin(loopAngle)) * 1.2;
        float anisotropy = mix(1.0, 6.0, streak);
        float2 np = float2(p.x / anisotropy, p.y) * .012;
        float n = noise(np + flow) * .7 + noise(np * 2.1 + flow * .6) * .3;
        float haze = peak * (1.0 - smoothstep(270.0, 355.0, p.y));
        p.x += haze * (noise(p * .04 + float2(0.0, -time * 1.6)) - .5) * 5.0;
        float dy = (p.y - 572.0) / 92.0, ground = (p.y - 763.0) / 123.0;
        float base = .70 + .055 * n + .07 * sin(p.y * .009 + 1.7);
        base -= .30 * exp(-dy * dy); base += .15 * exp(-ground * ground);
        float f0 = floor(frame), fraction = fract(frame);
        float4 body = mix(imageHeat(p, f0, soft, map), imageHeat(p, f0 + 1.0, soft, map), fraction);
        float ramp = smoothstep(.50, .90, effort);
        float temp = mix(.018 + .008 * n, dot(body.rgb, float3(.2126, .7152, .0722)), ramp);
        float heat = mix(base, temp, body.a);
        float trail = 0.0;
        for (int k = 1; k <= 3; k++) {
            float4 past = imageHeat(p - float2(wake * float(k) * direction / 3.0, 0.0), frame - 2.0 * float(k) * direction, soft, map);
            trail = max(trail, past.a * pow(.6, float(k)));
        }
        heat = mix(heat, .27 + .52 * effort, trail * .35 * (1.0 - body.a) * step(.001, wake));
        heat = mix(heat, .28 + .49 * effort, .4 * body.a * (1.0 - body.a));
        heat += .12 * exp(-dot(p - touch.xy, p - touch.xy) / (46.0 * 46.0)) * exp(-max(0.0, touch.z) / .5) * step(0.0, touch.z);
        float row = clamp(effort, 0.0, 1.0) * 63.0;
        float3 c = sampleImage(map, float2(clamp(heat, 0.0, 1.0) * 255.0 + .5, row + .5), false, true).rgb;
        float film = hash21(floor(xy) + floor(time * 12.0) * float2(71.0, 113.0)) - .5;
        c += film * .04;
        float3 lin = linearize(clamp(c, 0.0, 1.0));
        c = encode(float3(dot(lin, float3(.82259287, .17753395, 0.0)),
                          dot(lin, float3(.03319951, .9667835, 0.0)),
                          dot(lin, float3(.01708535, .07239572, .91030148))));
        return half4(half3(clamp(c, 0.0, 1.0)), 1.0h);
    }

    /// GLASS_SKSL. The backdrop is evaluated in its original encoded P3 space,
    /// before the final host adapter; converting earlier changes the frost bed.
    half4 glass(float2 p, float4 rect, float radius, float circle, float4 labelRect, float tilePeak, texture2d<half> map) {
        float2 ctr = rect.xy + rect.zw * .5;
        float d = sdf(p, rect, radius, circle);
        float2 grad = normalize(float2(
            sdf(p + float2(.5, 0.0), rect, radius, circle) - sdf(p - float2(.5, 0.0), rect, radius, circle),
            sdf(p + float2(0.0, .5), rect, radius, circle) - sdf(p - float2(0.0, .5), rect, radius, circle)) + float2(.00001));
        float ms = scale;
        float edge = 1.0 - smoothstep(0.0, 18.0 * ms, -d);
        float profile = pow(clamp(1.0 + d / max(radius, 1.0), 0.0, 1.0), 3.0);
        float2 displacement = -grad * 14.0 * scale * profile;
        if (circle > .5) {
            float2 q = (p - ctr) / (rect.z * .5);
            displacement = -q * 22.0 * ms * (.45 + .55 * dot(q, q));
        }
        float2 uv = ctr + (p - ctr) / (1.0 + .06) + displacement;
        float3 c = float3(field(uv, true, map).rgb);
        c.r = float(field(uv + grad * 1.5 * scale * edge, true, map).r);
        c.b = float(field(uv - grad * 1.5 * scale * edge, true, map).b);
        float labelD = boxSDF(p - (labelRect.xy + labelRect.zw * .5), labelRect.zw * .5, 12.0 * ms);
        float label = 1.0 - smoothstep(-3.0 * ms, 12.0 * ms, labelD);
        float f = mix(.3, .46, label);
        c = mix(c, float3(dot(c, float3(.2126, .7152, .0722))), f * .38);
        c = mix(c, float3(.74, .75, .74), f * .48);
        float luminance = dot(c, float3(.2126, .7152, .0722));
        float target = inkDark > .5 ? max(.87, luminance) : min(.39, luminance);
        float3 readable = inkDark > .5 ? mix(c, float3(.96, .93, .86), .86) : c * target / max(luminance, .001);
        c = mix(c, readable, label);
        float key = clamp(dot(grad, normalize(float2(-1.0, -1.0))), 0.0, 1.0);
        float inner = 1.0 - smoothstep(0.0, 9.0 * ms, -d);
        c = mix(c, c * .94, (1.0 - key) * inner * .8);
        c += float3(.94, .95, .92) * exp(-abs(d + .55 * ms) / (.5 * ms)) * (.14 + .34 * key);
        c += float3(.24, .12, .035) * tilePeak * inner * .5;
        c += (hash21(floor(p) + floor(time * 12.0) * 71.0) - .5) * .04;
        return half4(half3(clamp(c, 0.0, 1.0)), 1.0h);
    }
};

inline Scene load(device const float *u) {
    Scene s;
    s.scale = u[0]; s.offset = float2(u[1], u[2]);
    s.time = u[3]; s.frame = u[4]; s.effort = u[5]; s.streak = u[6]; s.direction = u[7];
    s.peak = u[8]; s.circlePeak = u[9]; s.smallPeak = u[10]; s.touch = float3(u[11], u[12], u[13]);
    s.wake = u[14]; s.inkDark = u[15]; s.pixelScale = u[16];
    return s;
}

// Same encoded Display P3 -> sRGB host adapter as the accepted GlassPayMorph port.
inline half4 displaySRGB(half4 color) {
    float3 c = linearize(float3(color.rgb));
    c = float3(dot(c, float3(1.22474527, -.22490437, 0.0)),
               dot(c, float3(-.04205797, 1.04208100, 0.0)),
               dot(c, float3(-.01964227, -.07865492, 1.09853719)));
    return half4(half3(encode(max(c, 0.0))), color.a);
}
} // namespace ThermalGlassTilesShader

[[ stitchable ]] half4 thermalGlassTiles(float2 position, half4 input,
    device const float *uniforms, int uniformCount, texture2d<half> atlas) {
    if (uniformCount < 17) return input;
    auto s = ThermalGlassTilesShader::load(uniforms);
    half4 result = s.field(position, false, atlas);
    float2 p = (position - s.offset) / s.scale;
    // The three source RoundedRects never overlap. Evaluate the lens only in its
    // antialiased footprint; all materials share the same full-screen field.
    for (int i = 0; i < 3; ++i) {
        float4 rect = i == 0 ? float4(51.0, 196.0, 300.0, 300.0) :
                      (i == 1 ? float4(51.0, 512.0, 142.0, 142.0) : float4(209.0, 512.0, 142.0, 142.0));
        float radius = i == 0 ? 72.0 : (i == 1 ? 71.0 : 40.0);
        float d = ThermalGlassTilesShader::sdf(p, rect, radius, i == 1 ? 1.0 : 0.0);
        float coverage = clamp(.5 - d * s.scale * s.pixelScale, 0.0, 1.0);
        if (coverage <= 0.0) continue;
        float4 labelRect = i == 0 ? float4(51.0, 350.0, 300.0, 180.0) :
                           (i == 1 ? float4(73.0, 526.0, 98.0, 80.0) : float4(222.0, 525.0, 116.0, 114.0));
        rect.xy = s.offset + rect.xy * s.scale; rect.zw *= s.scale;
        labelRect.xy = s.offset + labelRect.xy * s.scale; labelRect.zw *= s.scale;
        float peak = i == 0 ? s.peak : (i == 1 ? s.circlePeak : s.smallPeak);
        half4 tile = s.glass(position, rect, radius * s.scale, i == 1 ? 1.0 : 0.0, labelRect, peak, atlas);
        result = mix(result, tile, half(coverage));
    }
    return ThermalGlassTilesShader::displaySRGB(result);
}
