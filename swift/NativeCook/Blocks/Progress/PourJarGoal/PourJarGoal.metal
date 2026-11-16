#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Literal translation of pjg-shaders.ts. The alpha-packed source tables are decoded
// and quantized identically on the CPU, then passed through a SwiftUI float array.
namespace PourJarGoalShader {
struct Scene {
    float2 resolution;
    float4 jar;
    float2 vesselSize;
    float2 radii;
    float custom, meanY, fillLevel, bubbleCount;
    float4 bubbleGeometry[12];
    float2 finger;
    float stream, dropY, dropRadius, impact, ambient, reduced, p3;
    float3 pageColor, surfaceColor, depthColor;
    device const float *heights;
    device const float *walls;
    float read_heights(int i) { return heights[i]; }
    float read_wall(int i, float row) { return walls[i + (row > 0.5 ? 128 : 0)]; }
float sq(float v) { return v * v; }
float sat(float v) { return clamp(v, 0.0, 1.0); }
float hash(float n) { return fract(sin(n * 12.9898 + 4.1) * 43758.5453); }
float roundBox(float2 q, float2 b, float r) {
  float2 d = abs(q) - b + r;
  return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - r;
}
float wallAt(float y, bool rightSide) {
  float n = clamp(y / vesselSize.y * 128.0 - 0.5, 0.0, 127.0);
  int a = int(floor(n)); int b = (a < 127 ? a + 1 : 127);
  return rightSide ? mix(read_wall(a, 1.0), read_wall(b, 1.0), fract(n)) : mix(read_wall(a, 0.0), read_wall(b, 0.0), fract(n));
}
float shape(float2 q) {
  if (custom < 0.5) {
    float r = q.y < vesselSize.y * 0.5 ? radii.x : radii.y;
    return roundBox(q - vesselSize * 0.5, vesselSize * 0.5, r);
  }
  float y = clamp(q.y, 0.0, vesselSize.y);
  float l = wallAt(y, false), r = wallAt(y, true);
  return max(max(l - q.x, q.x - r), max(-q.y, q.y - vesselSize.y));
}
float surfaceAt(float x) {
  float l = wallAt(meanY, false), r = wallAt(meanY, true);
  float u = sat((x - l) / max(r - l, 1.0));
  float n = u * 63.0;
  int a = int(floor(n)); int b = (a < 63 ? a + 1 : 63);
  float wave = mix(read_heights(a), read_heights(b), fract(n));
  float rest = (sin(u * 6.2831853 + 0.2) * 1.9 + sin(u * 12.5663706 + 1.2) * 1.4) * (1.0 - reduced);
  float meniscus = 3.0 * exp(-min(max(x - l, 0.0), max(r - x, 0.0)) / 9.0);
  return meanY - wave - meniscus - rest;
}
float3 pageAt(float2 xy) {
  float y = sat(xy.y / resolution.y);
  float fade = smoothstep(0.10, 0.61, y);
  float3 base = mix(pageColor, float3(0.969, 0.975, 0.986), fade);
  float tint = (0.35 + 0.65 * fillLevel) * ambient;
  base = mix(base, float3(0.995, 0.926, 0.900), 0.11 * tint * (1.0 - fade));
  float2 poolP = (xy - float2(jar.x + jar.z * 0.5, jar.y + jar.w + 12.0 * jar.z / vesselSize.x));
  float2 poolSize = float2(jar.z * 0.586 * (1.0 + 0.08 * impact), 23.0 * jar.z / vesselSize.x);
  float pool = exp(-dot(poolP / poolSize, poolP / poolSize) * 1.5) * (0.14 * fillLevel / 0.75 + 0.06 * impact) * ambient;
  base = mix(base, float3(1.0, 0.46, 0.33), pool);
  return base;
}
float3 linearize(float3 c) { return mix(c / 12.92, pow((c + 0.055) / 1.055, float3(2.4)), step(float3(0.04045), c)); }
float3 encode(float3 c) { c = max(c, float3(0.0)); return mix(c * 12.92, 1.055 * pow(c, float3(1.0 / 2.4)) - 0.055, step(float3(0.0031308), c)); }
float3 outputColor(float3 c) {
  if (p3 < 0.5) return c;
  float3 l = linearize(c);
  return encode(float3(dot(l, float3(0.822593, 0.177534, 0.0)), dot(l, float3(0.0332, 0.966784, 0.0)), dot(l, float3(0.017085, 0.072396, 0.910302))));
}
half4 render(float2 xy) {
  float3 bg = pageAt(xy);
  float2 q = (xy - jar.xy) / jar.zw * vesselSize;
  // All light and shadow tails are evaluated on the full page, never clipped to a rectangle.
  float2 shadowP = (q - float2(vesselSize.x * 0.51, vesselSize.y + 2.0)) / float2(vesselSize.x * 0.42, 4.5);
  float shadow = exp(-dot(shadowP, shadowP) * 1.8) * 0.055;
  bg = mix(bg, float3(0.48, 0.33, 0.29), shadow);
  float d = shape(q);
  float aa = max(0.20, vesselSize.x / jar.z * 0.35);
  float mask = 1.0 - smoothstep(-aa, aa, d);
  float3 color = bg;
  if (d < 2.0) {
    float2 delta = float2(shape(q + float2(0.5, 0.0)) - shape(q - float2(0.5, 0.0)), shape(q + float2(0.0, 0.5)) - shape(q - float2(0.0, 0.5)));
    float2 normal = delta / max(length(delta), 0.001);
    float edge = exp(-abs(d + 1.0) / 2.1);
    float3 glass = pageAt(xy + normal * 5.0 * jar.z / vesselSize.x);
    glass = mix(glass, float3(0.995, 0.998, 1.0), 0.14 + edge * 0.36);
    float wall = smoothstep(0.0, 0.8, -d) * (1.0 - smoothstep(1.0, 3.0, -d));
    glass = mix(glass, float3(0.70, 0.74, 0.78), wall * 0.20);
    glass += float3(0.02) * exp(-sq((q.x - 16.0) / 9.0));
    float surf = surfaceAt(q.x);
    float slope = (surfaceAt(q.x + 1.0) - surfaceAt(q.x - 1.0)) * 0.5;
    float depth = q.y - surf;
    float liquidMask = smoothstep(-0.4, 0.5, depth) * (1.0 - smoothstep(-2.0, -0.6, d));
    float absorb = 1.0 - exp(-max(depth, 0.0) / 180.0);
    float3 liquid = mix(surfaceColor, depthColor, absorb);
    // Clear body: mirrored page sampling supplies a restrained transmitted contribution.
    float2 refractXY = float2(jar.x + jar.z - (xy.x - jar.x) + slope * 4.0, xy.y);
    liquid = mix(liquid, pageAt(refractXY) * float3(1.0, 0.65, 0.48), 0.08);
    float lip = exp(-max(depth, 0.0) / (6.5 + min(abs(slope), 1.0) * 6.0));
    liquid = mix(liquid, float3(1.0, 0.77, 0.69), lip * 0.62);
    float edgeLight = exp(-sq((q.x - 31.0) / 14.0)) * 0.64 + exp(-sq((q.x - vesselSize.x + 8.0) / 6.0)) * 0.30;
    liquid = mix(liquid, float3(1.0, 0.94, 0.90), edgeLight);
    float floorDepth = vesselSize.y - q.y;
    liquid *= 1.0 - 0.07 * exp(-sq((floorDepth - 10.0) / 10.0)) * exp(-sq((q.x - vesselSize.x * 0.5) / (vesselSize.x * 0.45)));
    float foot = exp(-sq((d + 3.0) / 1.0)) * smoothstep(vesselSize.y - 55.0, vesselSize.y - 10.0, q.y);
    liquid = mix(liquid, float3(1.0, 0.77, 0.68), foot * 0.4);
    for (int i = 0; i < 12; i++) {
      if (float(i) < bubbleCount) {
        float4 geometry = bubbleGeometry[i];
        float bx = geometry.x; float by = geometry.y; float radius = geometry.z; float lens = geometry.w;
        float seed = float(i) + 1.0;
        float2 bq = (q - float2(bx, by)) / float2(lens, 1.0);
        float bd = length(bq) - radius;
        float bubble = (1.0 - smoothstep(-0.7, 0.6, bd)) * smoothstep(surf + 6.0, surf + 14.0, by);
        float fade = 0.22 + 0.08 * hash(seed);
        liquid = mix(liquid, float3(1.0, 0.85, 0.75), bubble * fade);
        liquid *= 1.0 - exp(-sq((bd + 0.4) / 0.7)) * sat(bq.y / radius) * 0.055;
      }
    }
    // Empty glass: a 3 pt wall with its own inner contour, so the vessel reads as one
    // thick glass body over its full height, not only where liquid fills it.
    float px = vesselSize.x / jar.z / 3.0;
    float empty = 1.0 - liquidMask;
    float band = smoothstep(-3.4, -2.6, d) * (1.0 - smoothstep(-0.4, 0.2, d));
    glass = mix(glass, float3(0.985, 0.990, 1.0), band * 0.55);
    float innerLine = exp(-sq((d + 3.1) / max(0.42, px * 1.3)));
    glass = mix(glass, float3(0.60, 0.65, 0.71), innerLine * 0.30);
    // Lit shoulder (key light top-left) and a darker lower-right turn of the wall.
    float keyFacing = smoothstep(0.35, 0.95, dot(normal, normalize(float2(-0.55, -0.83))));
    float shoulder = keyFacing * exp(-sq((d + 1.6) / 1.1));
    float turn = smoothstep(0.3, 0.95, dot(normal, normalize(float2(0.6, 0.8)))) * exp(-sq((d + 1.8) / 1.6));
    color = mix(glass, liquid, liquidMask);
    float surfaceLine = exp(-sq(depth / 0.6));
    color = mix(color, float3(1.0, 0.91, 0.85), surfaceLine * 0.7 * liquidMask);
    // Narrow softbox reflection is one light, shared with the broad body reflection; it
    // continues as a fainter streak down the whole wall.
    float specH = 150.0 * (1.0 - 0.2 * (1.0 - smoothstep(0.0, 18.0, meanY - 24.0)));
    float spec = 1.0 - smoothstep(-0.6, 0.7, roundBox(q - float2(35.0, 18.0 + specH * 0.5), float2(4.0, specH * 0.5), 2.0));
    color = mix(color, float3(1.0), spec * mix(0.70, 0.35, liquidMask));
    float streakEnd = vesselSize.y - radii.y * 0.55;
    float streakFade = smoothstep(18.0, 40.0, q.y) * (1.0 - smoothstep(streakEnd - 60.0, streakEnd, q.y));
    float streak = exp(-sq((q.x - 35.0) / 1.6)) * streakFade * (1.0 - spec);
    color = mix(color, float3(1.0), streak * mix(0.30, 0.20, liquidMask));
    float sideGlint = exp(-sq((d + 5.5) / 2.2)) * smoothstep(0.6, 0.95, normal.x) * smoothstep(30.0, 90.0, q.y);
    color = mix(color, float3(1.0), sideGlint * mix(0.35, 0.28, liquidMask));
    color = mix(color, float3(1.0), shoulder * 0.75);
    color = mix(color, float3(0.62, 0.66, 0.71), turn * empty * 0.18);
    // Back rim, visible through the empty part of the jar; no second liquid surface.
    float2 rimQ = (q - float2(vesselSize.x * 0.5, 13.0)) / float2(vesselSize.x * 0.37, 12.0);
    float back = exp(-pow((length(rimQ) - 1.0) * 11.0, 2.0)) * (1.0 - liquidMask) * step(q.y, 13.0);
    color = mix(color, float3(0.66, 0.71, 0.75), back * 0.20);
    float rim = exp(-sq((d + 0.6) / 0.65));
    color = mix(color, float3(1.0), rim * 0.54);
    color = mix(bg, color, mask);
    // Crisp outer contour: a cool hairline around the empty glass, a pale one where the
    // liquid fills it. Centred on the edge so both halves are anti-aliased.
    float filled = smoothstep(-0.4, 0.5, depth);
    float contour = exp(-sq(d / max(0.36, px * 1.15)));
    color = mix(color, mix(float3(0.69, 0.73, 0.78), float3(1.0, 0.80, 0.74), filled), contour * mix(0.62, 0.45, filled));
    if (reduced < 0.5) {
      float streamMask = (1.0 - smoothstep(stream * 0.5, stream * 0.5 + 0.7, abs(q.x - vesselSize.x * 0.5))) * step(10.0, q.y) * (1.0 - smoothstep(surf, surf + 12.0, q.y)) * step(0.1, stream);
      color = mix(color, surfaceColor, streamMask * mask);
      float dropD = length((q - float2(finger.x, dropY)) / float2(1.0, 1.3)) - dropRadius;
      float dropMask = (1.0 - smoothstep(-0.4, 0.6, dropD)) * step(0.1, dropRadius) * mask;
      color = mix(color, surfaceColor, dropMask);
    }
  }
  // Static sub-LSB dither prevents posterization without shimmering at rest.
  color += (hash(floor(xy.x * 3.0) + floor(xy.y * 3.0) * 311.0) - 0.5) / 700.0;
  return half4(half3(outputColor(clamp(color, 0.0, 1.0))), 1.0h);
}

};

// RN converts authored sRGB to encoded P3. SwiftUI colorEffect uses sRGB, so
// decode that P3 output back to the host space, as in the accepted GlassPayMorph.
inline half4 displaySRGB(half4 color) {
    float3 c = float3(color.rgb);
    c = select(c / 12.92, pow((c + 0.055) / 1.055, float3(2.4)), c > 0.04045);
    c = float3(dot(c, float3(1.22474527, -0.22490437, 0.0)),
               dot(c, float3(-0.04205797, 1.04208100, 0.0)),
               dot(c, float3(-0.01964227, -0.07865492, 1.09853719)));
    c = max(c, 0.0);
    c = select(c * 12.92, 1.055 * pow(c, float3(1.0 / 2.4)) - 0.055, c > 0.0031308);
    return half4(half3(c), 1.0h);
}
} // namespace PourJarGoalShader

[[ stitchable ]] half4 pourJarGoal(float2 position, half4 color,
                                  device const float *u, int count) {
    if (count < 384) return color;
    PourJarGoalShader::Scene s;
    s.resolution = float2(u[0], u[1]);
    s.jar = float4(u[2], u[3], u[4], u[5]);
    s.vesselSize = float2(222.0, 422.0);
    s.radii = float2(68.0, 70.0);
    s.custom = 0.0;
    s.meanY = u[6]; s.fillLevel = u[7]; s.bubbleCount = u[8];
    s.finger = float2(u[9], u[10]);
    s.stream = u[11]; s.dropY = u[12]; s.dropRadius = u[13];
    s.impact = u[14]; s.ambient = 1.0; s.reduced = u[15]; s.p3 = 1.0;
    s.pageColor = float3(251.0, 241.0, 238.0) / 255.0;
    s.surfaceColor = float3(255.0, 154.0, 122.0) / 255.0;
    s.depthColor = float3(255.0, 74.0, 48.0) / 255.0;
    for (int i = 0; i < 12; ++i) {
        int j = 16 + i * 4;
        s.bubbleGeometry[i] = float4(u[j], u[j+1], u[j+2], u[j+3]);
    }
    s.heights = u + 64;
    s.walls = u + 128;
    return PourJarGoalShader::displaySRGB(s.render(position));
}
