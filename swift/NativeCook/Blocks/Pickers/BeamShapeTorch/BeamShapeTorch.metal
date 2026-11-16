#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Faithful BEAM_SKSL translation. RN returns encoded Display P3 on iOS;
// the final adapter changes only the host color space, as in GlassPayMorph.
namespace BeamShapeTorchShader {
struct Scene {
    float2 uLens; float uS, uB, uHot, uTheta, uReach, uGain, uWide;
    float4 uCap; float uCapR, uShow, uPhase, uStill;
    float3 uGlass, uLight; float uFringe, uAfter, uAfterReach, uP3;
float sq(float x) { return x * x; }

float hash2(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }

float sdRound(float2 p, float2 c, float2 hs, float rr) {
  float2 q = abs(p - c) - hs + float2(rr, rr);
  return length(max(q, float2(0.0, 0.0))) + min(max(q.x, q.y), 0.0) - rr;
}

float3 coneWindow(float a, float3 th3, float sIn, float sOut) {
  return float3(
    1.0 - smoothstep(th3.x - sIn, th3.x + sOut, a),
    1.0 - smoothstep(th3.y - sIn, th3.y + sOut, a),
    1.0 - smoothstep(th3.z - sIn, th3.z + sOut, a));
}

half4 render(float2 p) {
  // the black glass body
  float yy = clamp((p.y - uCap.y) / max(uCap.w, 1.0), 0.0, 1.0);
  float3 col = mix(uGlass * 0.08, uGlass, smoothstep(0.08, 0.55, yy));
  col = mix(col, uGlass * 0.89, smoothstep(0.62, 1.0, yy));

  float2 cc = float2(uCap.x + uCap.z * 0.5, uCap.y + uCap.w * 0.5);
  float sdPx = sdRound(p, cc, float2(uCap.z, uCap.w) * 0.5, uCapR);
  float sd = sdPx / uS; // design pt, negative inside

  // polar round the lens, from the edge of a 14 pt aperture
  float2 d = (p - uLens) / uS;
  float up = -d.y;
  float r = length(d);
  float ap = 7.0 * (1.0 - smoothstep(0.0, 40.0, up));
  float lat = max(abs(d.x) - ap, 0.0);
  // the cone starts at the lens, softly, and its base is a rounded cap (higher at the sides), not a flat cut
  float a = mix(1.5708, atan2(lat, max(up, 0.001)), smoothstep(-5.0, 3.0, up - 4.0 * min(sq(d.x / 10.0), 1.0)));
  float R = max(uReach, 1.0);
  float w = clamp(uWide, 0.0, 1.0);
  float th = uTheta;
  float shimmer = 1.0 + 0.03 * (1.0 - w) * sin(6.2831853 * 6.0 * uPhase) * (1.0 - uStill);
  float energy = uB * pow(uGain, 0.45) * shimmer;
  float3 lc = uLight / max(max(max(uLight.r, uLight.g), uLight.b), 0.001);

  // Filled angular window. All channels share the same radial falloff; dispersion can only REMOVE energy at the edge.
  float sIn = (1.5 + 3.5 * w) * 0.017453293;
  float sOut = (1.0 + 2.0 * w) * 0.017453293;
  float dispersion = 0.045 * w * uFringe;
  float3 angles = th * float3(1.0 + dispersion, 1.0, 1.0 - dispersion);
  float3 angular = coneWindow(a, angles, sIn, sOut);
  float r0 = 26.0 + 0.3 * R;
  float fall = 1.0 / (1.0 + pow(r / r0, 1.6));
  float cut = 1.0 - smoothstep(0.75 * R, 1.05 * R, r);
  // the cone fades in over its first 12 pt, from the lens glow, so its base is a soft cap and never a visible cut
  float baseFade = smoothstep(-1.0, 12.0, up - 4.0 * min(sq(d.x / 10.0), 1.0));
  float3 cone = angular * fall * cut * baseFade * energy * 1.25;

  // The collimated pencil begins at the aperture, never a spindle detached above it. Its on-axis energy decreases
  // monotonically from 1 to .35 over R; the small halo is confined to its first 60 pt.
  float pw = 1.0 - smoothstep(0.0, 0.25, w);
  float sigma = 5.0 + max(up, 0.0) * tan(th);
  float pen = exp(-sq(abs(d.x) / sigma)) * smoothstep(-3.0, 3.0, up);
  float penLen = mix(1.0, 0.35, clamp(up / R, 0.0, 1.0));
  float penCut = 1.0 - smoothstep(0.98 * R, 1.1 * R, up);
  float halo60 = exp(-sq(r / 60.0));
  float pencil = (pen * penLen * penCut * 1.5 + halo60 * 0.08) * energy;
  float3 light = mix(cone, float3(pencil), pw);

  // Lens heat keeps the meaningful 140 ms afterglow; its light is attached to the aperture.
  float hot = uHot * mix(1.0, 1.35, clamp((uGain - 1.0) / 6.5, 0.0, 1.0));
  light += float3(exp(-sq(r / (6.0 + 2.0 * hot))) * hot * 2.0);

  // A 46 x 10 pt landing spot at the pencil's ceiling plane, safely BELOW the island.
  float land = smoothstep(190.0, 212.0, R) * pw * uB;
  float2 sp = float2(d.x / 23.0, (up - R) / 5.0);
  float spot = exp(-dot(sp, sp)) * land * 0.55;
  float3 tint = mix(lc, float3(213.0, 209.0, 242.0) / 255.0, 0.45 * smoothstep(0.0, 0.6 * R, r));
  float3 lit = (1.0 - exp(-light * uShow)) * tint;
  lit = 1.0 - (1.0 - lit) * (1.0 - float3(233.0, 229.0, 246.0) / 255.0 * spot * uShow);

  // light thrown onto the inner walls, only where the cone points
  float toward = exp(-sq(a / (th + 0.5))) * uB * pow(uGain, 0.25) / (1.0 + sq(r / 190.0));
  float edge = exp(min(sd, 0.0) / 18.0);
  float3 walls = float3(0.788, 0.769, 0.839) * toward * (edge * mix(0.2, 1.0, w) * 0.9 + 0.03) * 0.1;

  col = col + walls * uShow;
  col = 1.0 - (1.0 - col) * (1.0 - lit); // the light over the glass

  // the afterimage of a held pencil, over what is lit
  float2 q = float2(d.x / 9.0, 0.0);
  float stripe = exp(-dot(q, q)) * smoothstep(0.0, 24.0, up) * (1.0 - smoothstep(0.8 * uAfterReach, uAfterReach, up));
  float lumOut = dot(col, float3(0.3333, 0.3333, 0.3333));
  col = mix(col, float3(42.0, 36.0, 64.0) / 255.0, 0.18 * uAfter * stripe * smoothstep(0.03, 0.3, w) * smoothstep(0.05, 0.3, lumOut));

  // glass edge: 1 px all round (6 %, 9 % on the top third), the wallpaper's magenta on the lower left
  float inner = 1.0 - smoothstep(0.0, 1.3, -sdPx);
  float rimOn = smoothstep(0.3, 0.9, uShow) * step(0.0, -sdPx);
  // The exact squircle rim is stroked at one physical pixel by the canvas, not approximated with this rounded SDF.
  float2 nrm = normalize(p - cc + float2(0.001, 0.001));
  float ll = sq(clamp(dot(nrm, float2(-0.7071, 0.7071)), 0.0, 1.0));
  col += float3(0.78, 0.4, 0.61) * inner * rimOn * ll * 0.08;

  // dither
  col += (hash2(floor(p * 3.0)) - 0.5) / 255.0; // per device pixel at @3x

  float3 outc = clamp(col, 0.0, 1.0);
  float3 lin = pow(outc, float3(2.2));
  float3 p3 = float3(0.8225 * lin.r + 0.1774 * lin.g, 0.0332 * lin.r + 0.9669 * lin.g, 0.0171 * lin.r + 0.0724 * lin.g + 0.9108 * lin.b);
  float3 enc = pow(p3, float3(1.0 / 2.2));
  return half4(half3(mix(outc, enc, uP3)), 1.0);
}

};
inline half4 displaySRGB(half4 color) {
    if (color.a <= 0.0h) return half4(0.0h);
    float a = float(color.a);
    float3 c = float3(color.rgb) / a;
    c = select(c / 12.92, pow((c + 0.055) / 1.055, float3(2.4)), c > 0.04045);
    c = float3(dot(c, float3(1.22474527, -0.22490437, 0.0)),
               dot(c, float3(-0.04205797, 1.04208100, 0.0)),
               dot(c, float3(-0.01964227, -0.07865492, 1.09853719)));
    c = max(c, 0.0);
    c = select(c * 12.92, 1.055 * pow(c, float3(1.0 / 2.4)) - 0.055, c > 0.0031308);
    return half4(half3(c * a), color.a);
}


} // namespace BeamShapeTorchShader

[[ stitchable ]] half4 beamShapeTorch(float2 p, half4 color, device const float *u, int count) {
    if (count < 27) return half4(0.0h);
    BeamShapeTorchShader::Scene s;
    s.uLens = float2(u[0], u[1]);
    s.uS = u[2]; s.uB = u[3]; s.uHot = u[4]; s.uTheta = u[5];
    s.uReach = u[6]; s.uGain = u[7]; s.uWide = u[8];
    s.uCap = float4(u[9], u[10], u[11], u[12]);
    s.uCapR = u[13]; s.uShow = u[14]; s.uPhase = u[15]; s.uStill = u[16];
    s.uGlass = float3(u[17], u[18], u[19]); s.uLight = float3(u[20], u[21], u[22]);
    s.uFringe = u[23]; s.uAfter = u[24]; s.uAfterReach = u[25]; s.uP3 = u[26];
    return BeamShapeTorchShader::displaySRGB(s.render(p));
}

[[ stitchable ]] half4 beamShapeTorchGrain(float2 xy, half4 color, float px, device const float *u, int count) {
    float3 q = fract(float3(floor(xy * px).x, floor(xy * px).y, floor(xy * px).x) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    float g = fract((q.x + q.y) * q.z) - 0.5;
    float a = abs(g) * 0.07;
    float lum = g > 0.0 ? 1.0 : 0.0;
    // Composite in the same display space as the RN full-screen canvas, without an isolated blend layer.
    float3 col = float3(color.rgb) * (1.0 - a) + lum * a;
    if (count >= 6) {
        col *= mix(float3(1.0), float3(30.0, 8.0, 21.0) / 255.0, u[3]);
        float radial = length(xy - float2(u[0], u[1] + 108.0 * u[2])) / (120.0 * u[2]);
        float veil = (1.0 - clamp((radial - 0.5) / 0.5, 0.0, 1.0)) * u[4];
        col = 1.0 - (1.0 - col) * (1.0 - veil);
        float halo = (1.0 - clamp(length(xy - float2(u[0], u[1])) / (60.0 * u[2]), 0.0, 1.0)) * u[5];
        col = 1.0 - (1.0 - col) * (1.0 - float3(246.0, 201.0, 230.0) / 255.0 * halo);
    }
    return half4(half3(col), 1.0h);
}
