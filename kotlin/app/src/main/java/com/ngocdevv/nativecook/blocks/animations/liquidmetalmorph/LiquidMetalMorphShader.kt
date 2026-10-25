package com.ngocdevv.nativecook.blocks.animations.liquidmetalmorph

// The whole "Forge" scene in one full-screen pass, the React Native SkSL (liquidMetalShader.ts)
// with two changes: the four satellites are four uniforms instead of an array, and the final
// sRGB → Display P3 conversion is dropped (Android draws runtime shaders in sRGB). Coordinates are
// dp: the canvas is scaled by the density before the shader is drawn.
//
// The blob is a 2D signed distance field (dp, negative inside): a drop, a four-point star and a
// disc mixed linearly by `w`, a ring hole punched in with a smooth maximum, a wobble of whole-loop
// sines, up to four satellite droplets and a droplet held by the finger, each joined with a smooth
// minimum so they neck and pinch off like mercury. Shading treats the field as a height map and
// reflects a studio gradient with two strip lights, a Fresnel rim, a key highlight and a faint
// chromatic split.

internal const val LIQUID_METAL_AGSL = """
uniform float2 size;
uniform float2 center;
uniform float R;
uniform float3 w;
uniform float4 wob;
uniform float4 sat0;
uniform float4 sat1;
uniform float4 sat2;
uniform float4 sat3;
uniform float4 finger;

float hash(float2 p) {
  return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
}

float smin(float a, float b, float k) {
  float h = max(k - abs(a - b), 0.0) / max(k, 0.0001);
  return min(a, b) - h * h * k * 0.25;
}

float sdUnevenCapsule(float2 p, float r1, float r2, float h) {
  p.x = abs(p.x);
  float b = (r1 - r2) / h;
  float a = sqrt(1.0 - b * b);
  float k = dot(p, float2(-b, a));
  if (k < 0.0) return length(p) - r1;
  if (k > a * h) return length(p - float2(0.0, h)) - r2;
  return dot(p, float2(a, b)) - r1;
}

float sdStar4(float2 p, float r, float m) {
  float an = 3.14159265 / 4.0;
  float en = 3.14159265 / m;
  float2 acs = float2(cos(an), sin(an));
  float2 ecs = float2(cos(en), sin(en));
  float bn = mod(atan(p.x, p.y), 2.0 * an) - an;
  p = length(p) * float2(cos(bn), abs(sin(bn)));
  p -= r * acs;
  p += ecs * clamp(-dot(p, ecs), 0.0, r * acs.y / ecs.y);
  return length(p) * sign(p.x);
}

float shapeDrop(float2 q) {
  return sdUnevenCapsule(float2(q.x, -(q.y - 0.26 * R)), 0.9 * R, 0.15 * R, 1.42 * R);
}

float shapeStar(float2 q) {
  return sdStar4(q, 1.18 * R, 2.55) - 0.13 * R;
}

float shapeDisc(float2 q) {
  return length(q) - 1.1 * R;
}

float smax(float a, float b, float k) {
  return -smin(-a, -b, k);
}

float addSat(float d, float2 q, float4 s) {
  if (s.z > 0.05) d = smin(d, length(q - s.xy) - s.z, s.w);
  return d;
}

float scene(float2 p) {
  float2 q = p - center;
  float d = 0.0;
  if (w.x != 0.0) d += w.x * shapeDrop(q);
  if (w.y != 0.0) d += w.y * shapeStar(q);
  if (w.z != 0.0) d += w.z * shapeDisc(q);
  float hole = 0.5 * R * smoothstep(0.35, 1.0, w.z);
  if (hole > 0.5) d = smax(d, hole - length(q), 12.0);
  float th = atan(q.y, q.x);
  d += wob.z * (0.6 * sin(3.0 * th + wob.x) + 0.4 * sin(2.0 * th - wob.y));
  d += wob.w * sin(5.0 * th + 0.7);
  d = addSat(d, q, sat0);
  d = addSat(d, q, sat1);
  d = addSat(d, q, sat2);
  d = addSat(d, q, sat3);
  if (finger.z > 0.05) d = smin(d, length(p - finger.xy) - finger.z, finger.w);
  return d;
}

float3 studio(float y, float x) {
  float3 c0 = float3(0.043, 0.047, 0.063);
  float3 c1 = float3(0.604, 0.639, 0.710);
  float3 c2 = float3(0.949, 0.957, 0.973);
  float3 c3 = float3(0.227, 0.247, 0.290);
  float3 c = c3;
  c = mix(c, c2, smoothstep(-1.0, -0.62, y));
  c = mix(c, c1, smoothstep(-0.5, -0.05, y));
  c = mix(c, c0, smoothstep(0.12, 0.3, y));
  c = mix(c, c3, smoothstep(0.34, 0.85, y));
  c += float3(0.86, 0.91, 1.0) * 0.5 * exp(-((x + 0.62) * (x + 0.62)) / 0.006) * smoothstep(0.35, -0.4, y);
  c += float3(1.0, 0.95, 0.88) * 0.32 * exp(-((x - 0.7) * (x - 0.7)) / 0.012) * smoothstep(0.45, -0.3, y);
  return c;
}

half4 main(float2 p) {
  float2 bq = (p - center) / size.y;
  float3 col = mix(float3(0.118, 0.125, 0.149), float3(0.071, 0.075, 0.086), smoothstep(0.0, 0.62, length(bq)));
  float2 sq = (p - center - float2(0.0, 1.62 * R)) / float2(1.25 * R, 0.24 * R);
  col *= 1.0 - 0.5 * exp(-dot(sq, sq));
  col *= 1.0 + (hash(floor(p)) - 0.5) * 0.08;
  float2 vq = p / size - 0.5;
  col *= 1.0 - 0.9 * dot(vq * float2(0.8, 0.6), vq * float2(0.8, 0.6));

  bool nearBlob = length(p - center) < 2.6 * R;
  bool nearFinger = finger.z > 0.05 && length(p - finger.xy) < finger.z + 30.0;
  if (nearBlob || nearFinger) {
    float d = scene(p);
    if (d < 1.5) {
      float e = 1.5;
      float2 g = float2(scene(p + float2(e, 0.0)) - scene(p - float2(e, 0.0)),
                        scene(p + float2(0.0, e)) - scene(p - float2(0.0, e))) / (2.0 * e);
      float gl = length(g);
      g = gl > 0.0001 ? g / gl : float2(0.0, -1.0);
      float W = 0.34 * R;
      float u = max(-d, 0.0);
      float s = 1.0 - clamp(u / W, 0.0, 1.0);
      float slope = min(s / sqrt(max(1.0 - s * s, 0.03)), 5.0);
      float2 dome = (p - center) / (1.7 * R);
      float dl = length(dome);
      dome *= min(dl, 0.75) / max(dl, 0.0001);
      float3 n = normalize(float3(g * slope + dome, 1.0));
      float3 r = float3(2.0 * n.z * n.x, 2.0 * n.z * n.y, 2.0 * n.z * n.z - 1.0);
      float split = (1.0 - n.z) * 0.06;
      float3 metal = float3(studio(r.y - split, r.x).r, studio(r.y, r.x).g, studio(r.y + split, r.x).b);
      float fres = pow(1.0 - n.z, 3.0);
      metal = mix(metal, float3(0.86, 0.9, 0.97), fres * 0.35);
      float3 key = normalize(float3(-0.42, -0.62, 0.66));
      metal += float3(1.0, 0.98, 0.95) * pow(max(dot(r, key), 0.0), 70.0) * 1.1;
      metal *= mix(0.5, 1.0, smoothstep(0.0, 2.5, u));
      float a = clamp(0.5 - d / 1.5, 0.0, 1.0);
      col = mix(col, metal, a);
    }
  }

  col = clamp(col, 0.0, 1.0);
  col += (hash(p + 17.0) - 0.5) / 255.0;
  return half4(half3(col), 1.0);
}
"""
