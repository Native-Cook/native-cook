#include <metal_stdlib>
using namespace metal;

// Direct translation of stm-medal.ts. Values and output remain sRGB.
struct SeasonTierMedalsShader {
 float2 c; float R, yaw, pitch, roll, chev;
 float3 metal, deep; float glassy, sweep, glow;
const float H = 0.11;
const float E = 0.13;
const float RIM = 0.095;

float seasonTierMedalsCapsule(float2 p, float2 a, float2 b, float r) {
  float2 pa = p - a; float2 ba = b - a;
  float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
  return length(pa - ba * h) - r;
}

// distance to the chevrons (negative inside)
float seasonTierMedalsEmblem(float2 p) {
  float cr = cos(roll); float sr = sin(roll);
  p = float2(cr * p.x - sr * p.y, sr * p.x + cr * p.y);
  float n = chev;
  float s = n > 2.5 ? 0.74 : (n > 1.5 ? 0.88 : 1.0);
  float gap = 0.32 * s;
  float y0 = (n - 1.0) * gap * 0.5;
  float d = 9.0;
  for (int i = 0; i < 3; i++) {
    if (float(i) < n) {
      float yy = y0 - float(i) * gap - 0.04;
      float2 tip = float2(0.0, yy + 0.16 * s);
      float w = 0.40 * s;
      float th = 0.105 * s;
      d = min(d, seasonTierMedalsCapsule(p, float2(-w, yy - 0.15 * s), tip, th));
      d = min(d, seasonTierMedalsCapsule(p, tip, float2(w, yy - 0.15 * s), th));
    }
  }
  return d;
}

float seasonTierMedalsRimH(float r) {
  float s = clamp((r - 0.78) / 0.22, 0.0, 1.0);
  float k = 2.0 * s - 1.0;
  return RIM * sqrt(max(0.0, 1.0 - k * k));
}

// front relief above z = H
float seasonTierMedalsFront(float2 p) {
  float r = length(p);
  float h = seasonTierMedalsRimH(r);
  if (r < 0.8) {
    float d = seasonTierMedalsEmblem(p);
    float k = clamp(-d / 0.075, 0.0, 1.0);
    float e = E * sqrt(1.0 - (1.0 - k) * (1.0 - k));
    h = max(h, e);
  }
  return h;
}
// back relief below z = -H
float seasonTierMedalsBack(float2 p) { return seasonTierMedalsRimH(length(p)) * 0.7; }

float3 seasonTierMedalsRotY(float3 v, float a) { float c0 = cos(a); float s0 = sin(a); return float3(c0 * v.x + s0 * v.z, v.y, -s0 * v.x + c0 * v.z); }
float3 seasonTierMedalsRotX(float3 v, float a) { float c0 = cos(a); float s0 = sin(a); return float3(v.x, c0 * v.y - s0 * v.z, s0 * v.y + c0 * v.z); }

float seasonTierMedalsStudio(float3 r) {
  float e = 0.025;
  e += 1.5 * smoothstep(0.30, 0.80, r.y) * smoothstep(-0.9, 0.1, r.z);
  e += 1.1 * exp(-pow((r.x - 0.70) * 4.5, 2.0)) * smoothstep(-0.5, 0.3, r.y + 0.25);
  e += 0.45 * exp(-pow((r.x + 0.82) * 4.0, 2.0));
  e += 0.07 * smoothstep(0.0, -0.8, r.y);
  e += 1.6 * exp(-pow((r.x + r.y * 0.35 - sweep) * 5.0, 2.0));
  return e;
}

// inside test for a point in medal space
float seasonTierMedalsInside(float3 p) {
  float r = length(p.xy);
  if (r > 1.0) return 0.0;
  if (p.z > H + seasonTierMedalsFront(p.xy)) return 0.0;
  if (p.z < -H - seasonTierMedalsBack(p.xy)) return 0.0;
  return 1.0;
}

half4 seasonTierMedalsMain(float2 xy) {
  float2 q = (xy - c) / R;
  q.y = -q.y;
  // silhouette: the projected outline, an ellipse with half axes a (x) and b (y)
  float a = abs(cos(yaw)) + (H + 0.06) * abs(sin(yaw));
  float b = abs(cos(pitch)) + (H + 0.06) * abs(sin(pitch));
  float2 qe = float2(q.x / a, q.y / b);
  float f = length(qe) - 1.0;
  float2 g = float2(qe.x / a, qe.y / b) / max(length(qe), 1e-4);
  float dist = f / max(length(g), 1e-4);
  float alpha = clamp(0.5 - dist * R, 0.0, 1.0);
  if (alpha <= 0.0) return half4(0.0);

  // shade from a point 1 px inside the outline so the edge never misses
  float2 qs = q * (1.0 - 1.2 / R);
  float3 ro = float3(qs, 3.0);
  float3 rd = float3(0.0, 0.0, -1.0);
  ro = seasonTierMedalsRotY(seasonTierMedalsRotX(ro, -pitch), -yaw);
  rd = seasonTierMedalsRotY(seasonTierMedalsRotX(rd, -pitch), -yaw);

  // the bounding cylinder: r <= 1, z in [-H - RIM, H + E]
  float zt = H + E; float zb = -H - RIM;
  float t0 = -1e5; float t1 = 1e5;
  if (abs(rd.z) > 1e-5) {
    float ta = (zt - ro.z) / rd.z; float tb = (zb - ro.z) / rd.z;
    t0 = max(t0, min(ta, tb)); t1 = min(t1, max(ta, tb));
  } else if (ro.z > zt || ro.z < zb) { return half4(0.0); }
  float A = rd.x * rd.x + rd.y * rd.y;
  if (A > 1e-6) {
    float B = 2.0 * (ro.x * rd.x + ro.y * rd.y);
    float C = ro.x * ro.x + ro.y * ro.y - 1.0;
    float disc = B * B - 4.0 * A * C;
    if (disc < 0.0) return half4(0.0);
    float sq = sqrt(disc);
    t0 = max(t0, (-B - sq) / (2.0 * A)); t1 = min(t1, (-B + sq) / (2.0 * A));
  } else if (ro.x * ro.x + ro.y * ro.y > 1.0) { return half4(0.0); }
  if (t1 <= t0) return half4(0.0);

  // march the slab, then bisect the first crossing
  float tp = t0; float th = -1.0;
  float span = t1 - t0;
  for (int i = 0; i <= 48; i++) {
    float t = t0 + span * float(i) / 48.0;
    if (seasonTierMedalsInside(ro + rd * t) > 0.5) { th = t; break; }
    tp = t;
  }
  if (th < 0.0) return half4(0.0);
  float lo = tp; float hi = th;
  for (int i = 0; i < 6; i++) {
    float m = 0.5 * (lo + hi);
    if (seasonTierMedalsInside(ro + rd * m) > 0.5) hi = m; else lo = m;
  }
  float3 p = ro + rd * hi;
  float r = length(p.xy);

  // which surface, and its normal (medal space)
  float3 n;
  float part = 0.0; // 0 field, 1 polished (rim / emblem), 2 edge
  float eps = 0.004;
  float topZ = H + seasonTierMedalsFront(p.xy);
  float botZ = -H - seasonTierMedalsBack(p.xy);
  float ed = 1.0;
  if (abs(p.z - topZ) < 0.012 && r < 0.995) {
    float hx = seasonTierMedalsFront(p.xy + float2(eps, 0.0)) - seasonTierMedalsFront(p.xy - float2(eps, 0.0));
    float hy = seasonTierMedalsFront(p.xy + float2(0.0, eps)) - seasonTierMedalsFront(p.xy - float2(0.0, eps));
    n = normalize(float3(-hx / (2.0 * eps), -hy / (2.0 * eps), 1.0));
    ed = seasonTierMedalsEmblem(p.xy);
    part = (r > 0.78 || ed < 0.0) ? 1.0 : 0.0;
  } else if (abs(p.z - botZ) < 0.012 && r < 0.995) {
    float hx = seasonTierMedalsBack(p.xy + float2(eps, 0.0)) - seasonTierMedalsBack(p.xy - float2(eps, 0.0));
    float hy = seasonTierMedalsBack(p.xy + float2(0.0, eps)) - seasonTierMedalsBack(p.xy - float2(0.0, eps));
    n = normalize(float3(-hx / (2.0 * eps), -hy / (2.0 * eps), -1.0));
    part = r > 0.78 ? 1.0 : 0.0;
  } else {
    float ph = atan2(p.y, p.x);
    float reed = 0.22 * sin(ph * 110.0);
    float3 radial = float3(p.xy / max(r, 1e-4), 0.0);
    float3 tang = float3(-radial.y, radial.x, 0.0);
    n = normalize(radial + tang * reed);
    part = 2.0;
  }

  // to world space
  float3 nw = seasonTierMedalsRotX(seasonTierMedalsRotY(n, yaw), pitch);
  float3 v = float3(0.0, 0.0, 1.0);
  float3 rf = reflect(-v, nw);
  float ndv = clamp(dot(nw, v), 0.0, 1.0);
  float3 L = normalize(float3(0.45, 0.75, 0.6));
  float dif = clamp(dot(nw, L), 0.0, 1.0);
  float env = seasonTierMedalsStudio(rf) * (1.0 + glow * 0.6);

  float3 col;
  float3 fres = metal + (float3(1.0) - metal) * pow(1.0 - ndv, 5.0);
  if (part > 1.5) {
    col = mix(deep, metal, 0.35) * (0.25 + 0.75 * env) * 0.9;
  } else if (part > 0.5) {
    col = fres * env * 0.95 + deep * 0.06;
  } else {
    // brushed field: concentric grain, mostly diffuse, a soft sheen
    float grain = 0.92 + 0.08 * sin(r * 420.0 + sin(r * 37.0) * 3.0);
    float occ = 0.62 + 0.38 * smoothstep(0.0, 0.12, ed);
    occ *= 0.72 + 0.28 * smoothstep(0.80, 0.70, r);
    col = (metal * (0.38 + 0.55 * dif) + metal * 0.32 * env) * grain * occ;
  }
  // the glassy rim of the silver: a cool fresnel with a faint violet fringe
  float rimZone = smoothstep(0.74, 0.86, r) * (part > 1.5 ? 0.6 : 1.0);
  col += glassy * rimZone * pow(1.0 - ndv, 1.6) * float3(0.42, 0.48, 0.95) * 0.55;
  col *= 1.0 + glow * 0.35;
  col = float3(1.0) - exp(-col * 1.35);
  // Skia's iOS runtime-effect surface interprets its output as Display P3.
  float3 linear = select(pow((col + 0.055) / 1.055, float3(2.4)), col / 12.92, col <= 0.04045);
  linear = float3(1.224745 * linear.r - 0.224904 * linear.g,
                  -0.042058 * linear.r + 1.042081 * linear.g,
                  -0.019642 * linear.r - 0.078655 * linear.g + 1.098537 * linear.b);
  col = select(1.055 * pow(max(linear, 0.0), float3(1.0 / 2.4)) - 0.055, 12.92 * linear, linear <= 0.0031308);
  return half4(half3(col * alpha), half(alpha));
}
};
[[ stitchable ]] half4 seasonTierMedalsMedal(float2 xy, half4 input,
 float2 center, float radius, float yaw, float pitch, float roll, float chevrons,
 float3 albedo, float3 deep, float glassy, float sweep, float glow) {
 SeasonTierMedalsShader medal{center, radius, yaw, pitch, roll, chevrons, albedo, deep, glassy, sweep, glow};
 return medal.seasonTierMedalsMain(xy);
}

// Skia interpolates the page gradient's straight RGBA channels before premultiplying.
[[ stitchable ]] half4 seasonTierMedalsPage(float2 xy, float2 center, float radius, float3 tint) {
    float t = clamp(length(xy - center) / radius, 0.0, 1.0);
    float alpha = 0.85 * (1.0 - t);
    float3 color = mix(tint, float3(18.0, 18.0, 20.0) / 255.0, t);
    return half4(half3(color * alpha), half(alpha));
}
