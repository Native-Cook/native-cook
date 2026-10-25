package com.ngocdevv.nativecook.blocks.sheets.rollerblindsheet

// Two AGSL passes (API 33+), the React Native SkSL unchanged (see rollerBlindShader.ts) except that
// Android draws in sRGB, so there is no P3 conversion. Coordinates are dp: the canvas is scaled by
// the density before the shaders are drawn.
//
// BLIND_ROOM_AGSL: the bedroom wall and its window (a morning sky, hills, the sun, a painted frame
// with mullions, a sill) and the patch of sunlight it throws on the wall; `dim` darkens the room.
// BLIND_FABRIC_AGSL: the linen of the blind only, drawn into an opaque rectangle from the roller to
// the hem, lit from the room and glowing where the window shines through it.

internal const val BLIND_ROOM_AGSL = """
uniform float2 size;
uniform float4 win;
uniform float dim;
uniform float unit;

float hs(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }

float vn(float2 q) {
  float2 i = floor(q);
  float2 f = fract(q);
  f = f * f * (3.0 - 2.0 * f);
  return mix(mix(hs(i), hs(i + float2(1.0, 0.0)), f.x), mix(hs(i + float2(0.0, 1.0)), hs(i + float2(1.0, 1.0)), f.x), f.y);
}

float3 toP3(float3 c) {
  // Android draws the shader in sRGB: no Display P3 conversion.
  return clamp(c, 0.0, 1.0);
}

// The window's frame and mullions (1 = wood, 0 = glass), softened by soft pt.
float frameMask(float2 p, float4 win, float u, float soft) {
  float2 l = p - win.xy;
  float bar = 9.0 * u;
  float edge = 12.0 * u;
  float outer = min(min(l.x, win.z - l.x), min(l.y, win.w - l.y));
  float f = 1.0 - smoothstep(edge - soft, edge + soft, outer);
  float vbar = 1.0 - smoothstep(bar * 0.5 - soft, bar * 0.5 + soft, abs(l.x - win.z * 0.5));
  float hbar = 1.0 - smoothstep(bar * 0.5 - soft, bar * 0.5 + soft, abs(l.y - win.w * 0.46));
  return max(f, max(vbar, hbar));
}


float3 sky(float2 p) {
  float2 l = (p - win.xy) / win.zw;
  float3 c = mix(float3(0.50, 0.68, 0.88), float3(0.84, 0.86, 0.88), smoothstep(0.0, 0.62, l.y));
  c = mix(c, float3(0.99, 0.82, 0.64), smoothstep(0.45, 0.9, l.y));
  // the sun, low on the right, and its glow
  float2 sd = (l - float2(0.72, 0.58)) * float2(win.z / win.w, 1.0);
  float d = length(sd);
  c += float3(1.0, 0.84, 0.58) * exp(-d * d * 14.0) * 0.6;
  c += float3(1.0, 0.97, 0.88) * exp(-d * d * 700.0) * 0.8;
  // soft clouds drifting nowhere (static)
  float cl = vn(l * float2(6.0, 14.0) + 2.0) * 0.6 + vn(l * float2(13.0, 30.0)) * 0.4;
  c = mix(c, float3(1.0, 0.95, 0.9), smoothstep(0.58, 0.82, cl) * 0.5 * (1.0 - smoothstep(0.55, 0.8, l.y)));
  // far hills, then nearer trees
  float hill = 0.80 + 0.05 * sin(l.x * 7.0 + 1.0) + 0.03 * sin(l.x * 17.0);
  c = mix(c, float3(0.70, 0.71, 0.80), smoothstep(hill - 0.004, hill + 0.004, l.y) * 0.75);
  float tree = 0.89 + 0.035 * vn(float2(l.x * 22.0, 3.0)) + 0.02 * sin(l.x * 40.0);
  c = mix(c, float3(0.36, 0.43, 0.42), smoothstep(tree - 0.004, tree + 0.004, l.y));
  // a faint sheen on the glass
  c += float3(0.05) * smoothstep(0.08, 0.0, abs(l.x - l.y * 0.6 - 0.15));
  return c;
}

half4 main(float2 p) {
  float yN = p.y / size.y;
  float3 wall = mix(float3(0.91, 0.875, 0.82), float3(0.82, 0.77, 0.71), smoothstep(0.0, 1.0, yN));
  wall += (vn(p * 0.6) - 0.5) * 0.02 + (hs(p) - 0.5) * 0.02;
  // the patch of sunlight the window throws down and to the left, with the mullions' shadows
  float2 sp = p;
  float drop = win.y + win.w + 26.0 * unit;
  float2 src = float2(sp.x + (sp.y - drop) * 0.35, win.y + (sp.y - drop) * 0.55 + win.w * 0.35);
  float inPatch = step(drop, sp.y) * step(win.x, src.x) * step(src.x, win.x + win.z) * step(src.y, win.y + win.w);
  float lit = inPatch * (1.0 - frameMask(src, win, unit, 7.0)) * (1.0 - smoothstep(drop, size.y, sp.y) * 0.6);
  wall += float3(0.10, 0.07, 0.02) * lit * (1.0 - dim);
  float3 col = wall;
  // the window: a shadowed reveal, the sky, the frame
  float2 l = p - win.xy;
  float inWin = step(0.0, l.x) * step(l.x, win.z) * step(0.0, l.y) * step(l.y, win.w);
  float3 glass = sky(p);
  float fm = frameMask(p, win, unit, 0.6);
  float3 wood = mix(float3(0.97, 0.955, 0.93), float3(0.86, 0.84, 0.81), smoothstep(0.0, win.w, l.y));
  col = mix(col, mix(glass, wood, fm), inWin);
  // the sill
  float sill = step(-14.0 * unit, l.x) * step(l.x, win.z + 14.0 * unit) * step(win.w, l.y) * step(l.y, win.w + 12.0 * unit);
  col = mix(col, mix(float3(0.98, 0.97, 0.95), float3(0.80, 0.78, 0.75), smoothstep(win.w, win.w + 12.0 * unit, l.y)), sill);
  col *= 1.0 - 0.25 * step(win.w + 12.0 * unit, l.y) * (1.0 - smoothstep(0.0, 14.0 * unit, l.y - win.w - 12.0 * unit)) * step(-14.0 * unit, l.x) * step(l.x, win.z + 14.0 * unit);
  // the room dims (and cools) as the blind closes
  col *= mix(float3(1.0), float3(0.64, 0.61, 0.60), dim);
  float2 v = p / size - 0.5;
  col *= 1.0 - 0.25 * dot(v, v);
  return half4(half3(toP3(col)), 1.0);
}
"""

internal const val BLIND_FABRIC_AGSL = """
uniform float4 win;
uniform float4 roll;
uniform float4 hem;

float hs(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }

float vn(float2 q) {
  float2 i = floor(q);
  float2 f = fract(q);
  f = f * f * (3.0 - 2.0 * f);
  return mix(mix(hs(i), hs(i + float2(1.0, 0.0)), f.x), mix(hs(i + float2(0.0, 1.0)), hs(i + float2(1.0, 1.0)), f.x), f.y);
}

float3 toP3(float3 c) {
  // Android draws the shader in sRGB: no Display P3 conversion.
  return clamp(c, 0.0, 1.0);
}

// The window's frame and mullions (1 = wood, 0 = glass), softened by soft pt.
float frameMask(float2 p, float4 win, float u, float soft) {
  float2 l = p - win.xy;
  float bar = 9.0 * u;
  float edge = 12.0 * u;
  float outer = min(min(l.x, win.z - l.x), min(l.y, win.w - l.y));
  float f = 1.0 - smoothstep(edge - soft, edge + soft, outer);
  float vbar = 1.0 - smoothstep(bar * 0.5 - soft, bar * 0.5 + soft, abs(l.x - win.z * 0.5));
  float hbar = 1.0 - smoothstep(bar * 0.5 - soft, bar * 0.5 + soft, abs(l.y - win.w * 0.46));
  return max(f, max(vbar, hbar));
}


float3 linen(float2 q, float u) {
  float warp = sin(q.x * 6.2831 / (1.7 * u));
  float weft = sin(q.y * 6.2831 / (1.5 * u));
  float slub = vn(float2(q.x * 0.03, q.y * 0.9)) - 0.5;
  float thread = vn(float2(q.x * 0.6, q.y * 0.05)) - 0.5;
  float3 c = float3(0.86, 0.80, 0.70);
  c *= 1.0 + warp * weft * 0.035 + slub * 0.07 + thread * 0.04;
  return c;
}

half4 main(float2 p) {
  float u = hem.w;
  float ry = roll.z;
  float R = roll.w;
  float hemY = hem.x;
  float roomLight = hem.z;
  float2 q = float2(p.x, p.y - hemY);
  float3 c = linen(q, u);
  // front light from the room, soft folds near the hem
  float fold = sin(p.x * 0.045 + 1.3) * 0.5 + sin(p.x * 0.11) * 0.5;
  float nearHem = exp(-clamp(hemY - p.y, 0.0, 400.0) / (90.0 * u));
  c *= (0.62 + 0.38 * roomLight) * (1.0 + fold * 0.05 * nearHem);
  // the window glowing through the weave, the mullions as soft shadows
  float2 l = p - win.xy;
  float inWin = smoothstep(-8.0, 8.0, l.x) * smoothstep(-8.0, 8.0, win.z - l.x) * smoothstep(-8.0, 8.0, l.y) * smoothstep(-8.0, 8.0, win.w - l.y);
  float through = inWin * (1.0 - frameMask(p, win, u, 9.0) * 0.75);
  float yN = clamp(l.y / win.w, 0.0, 1.0);
  float3 glow = mix(float3(1.0, 0.95, 0.86), float3(1.0, 0.86, 0.68), yN);
  float2 sd = (l / win.zw - float2(0.72, 0.58)) * float2(2.0, 1.4);
  float sun = exp(-dot(sd, sd) * 5.0);
  c = mix(c, c * 0.55 + glow * (0.42 + 0.25 * sun), through * 0.85);
  // the top of the fabric in the roll's shadow
  c *= 1.0 - 0.35 * exp(-clamp(p.y - ry - R, 0.0, 200.0) / (8.0 * u));
  c += (hs(p * 0.71) - 0.5) * 0.02;
  return half4(half3(toP3(c)), 1.0);
}
"""

