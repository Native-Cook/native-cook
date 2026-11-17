#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Frost Heart Rewards. The gem pipelines are gpu/fhr-particles.ts (WGSL) translated line for line;
// the stitchable functions are fhr-shaders.ts (SkSL). Colours are plain sRGB: the SwiftUI layers
// are non-linear sRGB and the MTKView's layer is sRGB, so the Skia sRGB -> P3 workaround is left out.

namespace frostHeartRewardsGPU {

struct Params {
  float4 view, placement, state, effects, claims, manual, light, backdrop, viewport, crystalViewport, crystalRaster, breath;
};
struct Piece {
  float4 center;
  float4 pivot;
  uint4 caps;
  float4 planes[11];
};
constant uint SECTORS = 24;
constant float PI = 3.141592654;

struct Ctx {
  constant Params &P;
  constant Piece *pieces;
  texture2d<float> bear;
  texture2d<float> hug;
  texture2d<float> page;
  texture2d<float> rounded;
  texture2d<float> cutTex;
  sampler s;
};

inline float sat(float x) { return clamp(x, 0.0, 1.0); }
inline float phase(float t, float a, float b) { return sat((t - a) / (b - a)); }
inline float fadePhase(float t, float a, float b) { float u = phase(t, a, b); return u * u * (3.0 - 2.0 * u); }
inline float easing(float x) { float u = sat(x); return u > 0.5 ? 1.0 - pow(-2.0 * u + 2.0, 3.0) * 0.5 : 4.0 * u * u * u; }
inline float3 rotX(float3 p, float a) { return float3(p.x, p.y * cos(a) - p.z * sin(a), p.y * sin(a) + p.z * cos(a)); }
inline float3 rotY(float3 p, float a) { return float3(p.x * cos(a) + p.z * sin(a), p.y, -p.x * sin(a) + p.z * cos(a)); }
inline float3 rotZ(float3 p, float a) { return float3(p.x * cos(a) - p.y * sin(a), p.x * sin(a) + p.y * cos(a), p.z); }
inline float hashFract(float x) { return fract(precise::sin(x) * 43758.5453); }

inline float3 forward(thread const Ctx &c, float3 p) { return rotY(rotX(p, c.P.placement.y), c.P.placement.z); }
inline float3 inverse(thread const Ctx &c, float3 p) { return rotX(rotY(p, -c.P.placement.z), -c.P.placement.y); }

inline float spread(thread const Ctx &c, uint id) {
  float t = c.P.view.w;
  if (t < 5.4) { return c.P.state.y; }
  float age = max(0.0, t - 5.4 - float(id / 2u) * 0.04);
  return exp(-14.0 * age) * (cos(10.0 * age) + 1.4 * sin(10.0 * age));
}
inline float3 offsetOf(thread const Ctx &c, uint id) {
  float3 ctr = c.pieces[id].pivot.xyz;
  float3 direction = normalize(float3(ctr.xy * 0.9, ctr.z * 0.42 + 0.04));
  return direction * min(0.16, max(-0.025, spread(c, id)) * 0.16);
}
inline float spin(thread const Ctx &c, uint id) {
  uint group = id / 2u;
  return spread(c, id) * (0.055 + float(group % 3u) * 0.018) * ((group % 2u == 0u) ? 1.0 : -1.0);
}
inline float3 toLocal(thread const Ctx &c, float3 p, uint id) {
  float3 ctr = c.pieces[id].pivot.xyz;
  float sp = spin(c, id);
  return rotX(rotZ(rotY(inverse(c, p) - offsetOf(c, id) - ctr, -sp), -sp * 0.4), -sp * 0.6) + ctr;
}
inline float3 worldNormal(thread const Ctx &c, float3 n, uint id) {
  float sp = spin(c, id);
  return forward(c, rotY(rotZ(rotX(n, sp * 0.6), sp * 0.4), sp));
}

struct Hit {
  float near;
  float far;
  float3 normal;
  float3 exitNormal;
  float edge;
  uint enterFace;
  uint exitFace;
};

inline Hit makeHit(float n, float f, float3 a, float3 b, float e, uint ef, uint xf) {
  Hit h; h.near = n; h.far = f; h.normal = a; h.exitNormal = b; h.edge = e; h.enterFace = ef; h.exitFace = xf; return h;
}

Hit intersect(thread const Ctx &c, float3 ro, float3 rd, uint id) {
  float3 relative = ro - c.pieces[id].center.xyz;
  float projected = dot(relative, rd);
  float radius = c.pieces[id].center.w + 0.002;
  float outside = dot(relative, relative) - radius * radius;
  if (projected * projected - outside < -0.0001 || (projected > 0.0 && outside > 0.0001)) {
    return makeHit(-10000.0, -10000.0, float3(0, 0, 1), float3(0, 0, -1), 10000.0, 0u, 0u);
  }
  float lo = -10000.0, hi = 10000.0;
  float3 enter = float3(0, 0, 1), exitN = float3(0, 0, -1);
  float edge = 10000.0;
  uint enterFace = 0u, exitFace = 0u;
  uint count = c.pieces[id].caps.y;
  for (uint k = 0u; k < 11u; k++) {
    if (k >= count) { break; }
    uint j = k - 2u;
    if (k < 2u) { j = count - 2u + k; }
    float4 plane = c.pieces[id].planes[j];
    float den = dot(plane.xyz, rd);
    float dist = plane.w - dot(plane.xyz, ro);
    if (abs(den) < 0.00001) {
      if (dist < 0.0) { hi = -10000.0; }
    } else {
      float a = dist / den;
      if (den < 0.0) {
        if (a > lo) { edge = abs(a - lo); lo = a; enter = plane.xyz; enterFace = j; }
        else { edge = min(edge, abs(a - lo)); }
      } else {
        if (a < hi) { hi = a; exitN = plane.xyz; exitFace = j; }
      }
    }
    if (hi < lo) { break; }
  }
  return makeHit(lo, hi, enter, exitN, edge, enterFace, exitFace);
}

inline bool assembled(thread const Ctx &c) { return c.P.state.y < 0.004 && (c.P.view.w <= 3.9 || c.P.view.w >= 6.35); }

Hit unionHit(thread const Ctx &c, float3 ro, float3 rd) {
  uint owner = 0u; float near = 10000.0, far = 10000.0;
  float3 n = float3(0, 0, 1), en = float3(0, 0, -1);
  for (uint k = 0u; k < SECTORS; k++) {
    Hit h = intersect(c, ro, rd, k);
    if (h.far >= max(0.0, h.near)) {
      uint ext = uint(c.pieces[k].pivot.w);
      if (h.enterFace < ext && h.near > 0.0 && h.near < near) { near = h.near; n = h.normal; owner = k; }
      if (h.exitFace < ext && h.far > 0.0 && h.far < far) { far = h.far; en = h.exitNormal; }
    }
  }
  return makeHit(near, far, n, en, 10000.0, owner, 0u);
}

Hit groupHit(thread const Ctx &c, float3 ro, float3 rd, uint id) {
  uint base = (id / 2u) * 2u;
  uint owner = base; float near = 10000.0, far = 10000.0;
  float3 normal = float3(0, 0, 1), exitN = float3(0, 0, -1);
  for (uint k = base; k < base + 2u; k++) {
    Hit h = intersect(c, ro, rd, k);
    if (h.far >= max(0.0, h.near)) {
      uint mask = c.pieces[k].caps.x;
      if ((mask & (1u << h.enterFace)) != 0u && h.near > 0.0 && h.near < near) { near = h.near; normal = h.normal; owner = k; }
      if ((mask & (1u << h.exitFace)) != 0u && h.far > 0.0 && h.far < far) { far = h.far; exitN = h.exitNormal; }
    }
  }
  return makeHit(near, far, normal, exitN, 10000.0, owner, 0u);
}
inline Hit trace(thread const Ctx &c, float3 ro, float3 rd, uint id) { if (assembled(c)) { return unionHit(c, ro, rd); } return groupHit(c, ro, rd, id); }
inline float3 opticalNormal(thread const Ctx &c, float3 n, uint id) { if (assembled(c)) { return forward(c, n); } return worldNormal(c, n, id); }

float softbox(float3 r, float3 cIn, float2 size) {
  float3 center = normalize(cIn);
  float3 right = normalize(cross(float3(0, 1, 0), center));
  float3 up = cross(center, right);
  float facing = dot(r, center);
  float2 uv = float2(dot(r, right), dot(r, up)) / max(0.001, facing);
  float2 q = abs(uv) - size;
  float aperture = (1.0 - smoothstep(-0.014, 0.014, max(q.x, q.y))) * step(0.01, facing);
  float2 local = uv / max(size, float2(0.001));
  return aperture * (0.12 + 0.88 * exp(-dot(local, local) * 2.4));
}
float pinLight(float3 r, float3 cIn) {
  float3 center = normalize(cIn);
  float3 right = normalize(cross(float3(0, 1, 0), center));
  float3 up = cross(center, right);
  float facing = dot(r, center);
  float2 uv = float2(dot(r, right), dot(r, up)) / max(0.001, facing);
  float2 q = uv / 0.006;
  return pow(max(0.0, 1.0 - dot(q, q)), 2.0) * step(0.01, facing) * 12.0;
}
float3 environment(thread const Ctx &c, float3 ray) {
  float3 r = normalize(ray);
  float key = softbox(r, float3(-0.18, 0.78, 0.60), float2(0.12, 0.055)) * 8.0;
  float left = softbox(r, float3(-0.615690, -0.554370, 0.560000), float2(0.045, 0.30)) * 7.0
             + softbox(r, float3(0.17, -0.27, 0.95), float2(0.08, 0.25)) * 7.0;
  float right = softbox(r, float3(0.75, 0.12, 0.55), float2(0.019, 0.12)) * 14.0;
  float back = softbox(r, float3(0.15, 0.65, -0.75), float2(0.10, 0.035)) * 7.0;
  float lower = softbox(r, float3(-0.47543, -0.74502, 0.46788), float2(0.055, 0.055)) * 0.42;
  float lowerRight = softbox(r, float3(0.7729, -0.6340, 0.0256), float2(0.050, 0.028)) * 0.22;
  float bounce = pow(max(0.0, dot(r, normalize(float3(-0.3, -0.7, -0.6)))), 18.0) * 0.025;
  float room = pow(max(0.0, dot(r, normalize(float3(-0.25, 0.8, 0.35)))) * 0.5 + 0.5, 3.0) * 0.003;
  float wall = pow(max(0.0, dot(r, normalize(float3(0.6, -0.3, -0.7)))) * 0.5 + 0.5, 5.0) * 0.018;
  float pins = pinLight(r, float3(0.278374, -0.790470, 0.545588)) + pinLight(r, float3(0.189659, -0.940826, 0.280847));
  return float3(room) * float3(0.90, 0.94, 1.0) + float3(left) * float3(0.72, 0.86, 1.0)
       + float3(key + right + back + lower + lowerRight + pins)
       + mix(float3(1.0, 0.12, 0.55), float3(0.48, 0.09, 1.0), c.P.effects.z) * (bounce + wall);
}
float3 worldPoint(thread const Ctx &c, float3 p, uint id) {
  if (assembled(c)) { return forward(c, p); }
  float3 ctr = c.pieces[id].pivot.xyz;
  float sp = spin(c, id);
  return forward(c, rotY(rotZ(rotX(p - ctr, sp * 0.6), sp * 0.4), sp) + ctr + offsetOf(c, id));
}
inline float3 amethyst(float3 rgb) {
  return clamp(float3(dot(rgb, float3(0.58, 0.42, 0.0)), dot(rgb, float3(-0.16, 1.12, 0.04)), dot(rgb, float3(0.75, -0.25, 0.50))), float3(0.0), float3(1.0));
}
inline float3 bearColour(float3 rgb) {
  return clamp(rgb * float3(0.956408, 1.032831, 1.030621) + float3(0.019412, 0.024, -0.005808), float3(0.0), float3(1.0));
}
float3 backdrop(thread const Ctx &c, float2 screen) {
  float2 pageUV = clamp(screen / float2(402.0, 440.0), float2(0.0), float2(1.0));
  float3 bg = c.page.sample(c.s, pageUV, level(0)).rgb;
  float2 lamp = (screen - float2(177.0, 282.0)) / float2(120.0, 94.0);
  float lampAlpha = exp(-dot(lamp, lamp) * 1.5) * sat(c.P.state.y) * 0.15;
  bg = mix(bg, float3(0.92, 0.79, 0.87), lampAlpha);
  float keep = 1.0 - c.P.state.w;
  float2 p = (screen - float2(175.0, 278.0) - c.P.backdrop.xy * keep) / (1.0 + c.P.effects.x * keep) + float2(175.0, 278.0);
  p = (p - float2(175.0, 245.0)) / float2(1.0 - 0.03 * c.P.backdrop.z * keep, 1.0 + 0.015 * c.P.backdrop.z * keep) + float2(175.0, 245.0);
  float2 center = float2(178.5, 225.0);
  float2 v = p - center;
  float angle = -2.0 * PI / 180.0;
  float2 unrotated = float2(v.x * cos(angle) - v.y * sin(angle), v.x * sin(angle) + v.y * cos(angle)) + center;
  float2 uv = (unrotated - float2(63.5, 126.0)) / float2(230.0, 198.0);
  if (all(uv >= float2(0.0)) && all(uv <= float2(1.0))) {
    float4 rest = c.bear.sample(c.s, uv, level(0));
    float4 hug = c.hug.sample(c.s, uv, level(0));
    float open = sat(c.P.state.y) * keep;
    float a = rest.a * (1.0 - open) * (1.0 - smoothstep(292.0, 332.0, p.y));
    float b = hug.a * open * (1.0 - smoothstep(310.0, 350.0, p.y));
    float radius = mix(-24.0, 230.0, c.P.effects.z * keep);
    float wave = 1.0 - smoothstep(radius - 18.0, radius + 18.0, distance(p, float2(175.0, 278.0)));
    if (c.P.effects.z <= 0.0) { wave = 0.0; }
    if (c.P.effects.z * keep >= 1.0) { wave = 1.0; }
    float3 restColour = bearColour(rest.rgb);
    float3 hugColour = bearColour(hug.rgb);
    float3 restRGB = mix(restColour, amethyst(restColour), wave);
    float3 hugRGB = mix(hugColour, amethyst(hugColour), wave);
    bg = mix(bg, restRGB, a);
    bg = mix(bg, hugRGB, b);
  }
  return pow(max(bg, float3(0.0)), float3(2.2));
}
float3 plaqueReflection(thread const Ctx &c, float3 wp, float3 wr) {
  if (wr.z <= 0.015) { return float3(0.0); }
  float travel = (0.58 - wp.z) / wr.z;
  if (travel <= 0.0 || travel > 4.0) { return float3(0.0); }
  float3 q = wp + wr * travel;
  float2 screen = float2(175.0, 278.0) + float2(q.x, -q.y) * 105.0;
  float2 delta = screen - float2(187.5, 291.0);
  float cs = cos(-0.36), sn = sin(-0.36);
  float2 v = float2(delta.x * cs + delta.y * sn, -delta.x * sn + delta.y * cs);
  float2 p = abs(v) - float2(33.5, 3.0);
  float sdf = length(max(p, float2(0.0))) + min(max(p.x, p.y), 0.0) - 38.0;
  float coverage = 1.0 - smoothstep(-1.0, 3.0, sdf);
  float3 albedo = mix(float3(0.95, 0.025, 0.72), float3(0.33, 0.004, 0.31), sat((v.y + 41.0) / 82.0));
  float3 irradiance = environment(c, normalize(float3(-0.18, 0.78, 0.6))) * 0.12;
  return albedo * irradiance * coverage * c.P.state.x;
}
float3 exitLight(thread const Ctx &c, float3 pos, float3 ray, uint id) {
  float3 wp = worldPoint(c, pos, id);
  float3 wr = normalize(opticalNormal(c, ray, id));
  float3 light = environment(c, normalize(wr + wp * float3(0.16, 0.13, 0.08))) + plaqueReflection(c, wp, wr);
  if (wr.z < -0.015) {
    float travel = (-0.65 - wp.z) / wr.z;
    if (travel < 0.0 || travel > 10.0) { return light; }
    float3 q = wp + wr * travel;
    float2 screen = float2(175.0, 278.0) + float2(q.x, -q.y) * 105.0 * (1.0 + c.P.effects.x) + c.P.backdrop.xy * (1.0 - c.P.state.w);
    light += backdrop(c, screen);
  }
  return light;
}
float3 screenTransmission(thread const Ctx &c, float3 pos, float3 ray, float3 n, uint id, float ior) {
  float3 wp = worldPoint(c, pos, id);
  float3 wr = opticalNormal(c, refract(ray, n, 1.0 / ior), id);
  if (wr.z >= -0.015) { return float3(0.0); }
  float travel = (-0.65 - wp.z) / wr.z;
  if (travel < 0.0 || travel > 10.0) { return float3(0.0); }
  float3 q = wp + wr * travel;
  float2 point = float2(175.0, 278.0) + float2(q.x, -q.y) * 105.0 * (1.0 + c.P.effects.x) + c.P.backdrop.xy * (1.0 - c.P.state.w);
  float2 virtualImage = float2(175.0, 245.0) + (point - float2(175.0, 278.0)) * 0.58 + float2(wr.x, -wr.y) * 90.0;
  return mix(backdrop(c, point), backdrop(c, virtualImage), 0.65);
}
float3 optical(thread const Ctx &c, float3 ro, float3 rd, float3 n, uint id) {
  float ior = 2.42;
  float3 ray = refract(rd, n, 1.0 / ior);
  float3 pos = ro + ray * 0.0008;
  float3 colour = float3(0.0);
  float energy = 1.0;
  float t = c.P.view.w;
  bool moving = t >= 2.4 && t < 6.35;
  float hero = fadePhase(t, 6.35, 6.5) * (1.0 - fadePhase(t, 7.15, 7.4));
  uint maxBounces = moving ? 2u : (hero > 0.0 ? 4u : 3u);
  for (uint bounce = 0u; bounce < 4u; bounce++) {
    if (bounce >= maxBounces) { break; }
    Hit h = trace(c, pos, ray, id);
    if (h.far < 0.00001 || h.far > 9999.0) { colour += exitLight(c, pos, ray, id) * energy; energy = 0.0; break; }
    float3 hit = pos + ray * h.far;
    float3 outward = h.exitNormal;
    float3 leave = refract(ray, -outward, ior);
    if (dot(leave, leave) > 0.001) {
      float f0 = pow((ior - 1.0) / (ior + 1.0), 2.0);
      float fres = f0 + (1.0 - f0) * pow(1.0 - abs(dot(ray, outward)), 5.0);
      float3 r = refract(ray, -outward, 2.40);
      float3 b = refract(ray, -outward, 2.46);
      float3 light = float3(0.0, exitLight(c, hit, leave, id).g, 0.0);
      if (dot(r, r) > 0.001) { light.r = exitLight(c, hit, r, id).r; }
      if (dot(b, b) > 0.001) { light.b = exitLight(c, hit, b, id).b; }
      float weight = bounce == 3u ? hero : 1.0;
      colour += light * energy * (1.0 - fres) * weight;
      energy *= 1.0 - (1.0 - fres) * weight;
      break;
    }
    if (bounce + 1u >= maxBounces || energy < 0.012) { break; }
    ray = reflect(ray, outward);
    pos = hit + ray * 0.001;
  }
  float3 tail = screenTransmission(c, ro, rd, n, id, ior) + environment(c, normalize(opticalNormal(c, ray, id)));
  return (colour + tail * energy * 0.78) * 0.96;
}
inline float3 filmic(float3 cIn) {
  float3 x = max(float3(0.0), cIn);
  return (x * (2.51 * x + 0.03)) / (x * (2.51 * x + 0.59) + 0.14);
}
float facetEdge(thread const Ctx &c, float3 pos, float3 n, uint owner) {
  uint sector = owner;
  bool asm_ = assembled(c);
  uint count = asm_ ? uint(c.pieces[sector].pivot.w) : c.pieces[sector].caps.y;
  float d = 100.0;
  for (uint j = 0u; j < count; j++) {
    if (!asm_ && (c.pieces[sector].caps.x & (1u << j)) == 0u) { continue; }
    float4 plane = c.pieces[sector].planes[j];
    float crossLength = length(cross(n, plane.xyz));
    if (crossLength > 0.05) { d = min(d, abs(plane.w - dot(plane.xyz, pos)) / crossLength); }
  }
  return d;
}
inline float ageFor(constant Params &P, uint i) {
  if (P.manual.y > 0.5) { if (i == 4u) { return P.manual.x; } return P.claims[i]; }
  if (i == 3u) { return P.view.w - 0.6; }
  if (i == 4u) { return P.view.w - 0.95; }
  return -1000.0;
}

struct Out {
  float4 pos [[position]];
  float2 point;
  float2 uv;
  uint id [[flat]];
  float alpha [[flat]];
};
struct MaterialOut {
  float4 colour [[color(0)]];
  float4 surface [[color(1)]];
};
inline MaterialOut makeMaterial(float4 a, float4 b) { MaterialOut m; m.colour = a; m.surface = b; return m; }

inline float breathDistance(constant Params &P, float2 point) {
  float2 d = point - P.breath.xy;
  float ripple = sin(point.x * 0.31 + sin(point.y * 0.23)) * 1.3 + sin(point.y * 0.71 - point.x * 0.47) * 0.65;
  return length(d) + ripple;
}
inline float breathAt(constant Params &P, float2 point) { return (1.0 - smoothstep(20.0, 37.0, breathDistance(P, point))) * P.breath.z; }

MaterialOut material(thread const Ctx &c, Out input) {
  constant Params &P = c.P;
  if (input.alpha < 0.001) { discard_fragment(); }
  if (input.id == (SECTORS + 52u)) {
    float3 focus = float3(0.0);
    for (uint j = 0u; j < 12u; j++) {
      float3 n = forward(c, c.pieces[j * 2u].planes[1].xyz);
      float3 red = refract(normalize(float3(0.12, -0.8, -0.6)), n, 1.0 / 2.405);
      float3 blue = refract(normalize(float3(0.12, -0.8, -0.6)), n, 1.0 / 2.446);
      float2 at = float2(184.0 + red.x * 70.0, 353.0 + red.z * 17.0);
      float2 q = (input.point - at) / float2(17.0, 3.5);
      float2 qb = (input.point - at - float2((blue.x - red.x) * 130.0, 0.0)) / float2(17.0, 3.5);
      float a = exp(-dot(q, q) * 1.8);
      float b = exp(-dot(qb, qb) * 1.8);
      focus += float3(a, (a + b) * 0.5, b) * 0.014;
    }
    float a = min(0.13, max(focus.r, max(focus.g, focus.b))) * input.alpha;
    return makeMaterial(float4(float3(0.86, 0.88, 1.0) * a, a), float4(0.0));
  }
  if (input.id >= SECTORS) {
    float r = length(input.uv);
    float pearl = exp(-r * r * 4.8);
    float a = pearl * input.alpha;
    if (input.id >= (SECTORS + 40u)) {
      a = max(pearl, exp(-abs(input.uv.x) * 15.0 - abs(input.uv.y) * 2.5) * 0.45 + exp(-abs(input.uv.y) * 15.0 - abs(input.uv.x) * 2.5) * 0.45) * input.alpha;
    }
    return makeMaterial(float4(float3(1.0, 0.91, 0.95) * a, a), float4(0.0));
  }
  uint id = input.id;
  float zoom = 1.0 + P.effects.x;
  float2 point = input.point - P.backdrop.xy * (1.0 - P.state.w);
  float3 camera = float3((point.x - 175.0) / (105.0 * zoom), -(point.y - 278.0) / (105.0 * zoom), 4.0);
  float3 cameraOrigin = float3(camera.xy, 5.5);
  float3 cameraRay = float3(0.0, 0.0, -1.0);
  float3 origin = toLocal(c, cameraOrigin, id);
  float3 ray = normalize(toLocal(c, cameraOrigin + cameraRay, id) - origin);
  if (assembled(c)) { origin = inverse(c, cameraOrigin); ray = inverse(c, cameraRay); }
  Hit hit = trace(c, origin, ray, id);
  if (hit.far < max(0.0, hit.near) || hit.near > 9999.0 || hit.far > 9999.0) { discard_fragment(); }
  float3 pos = origin + ray * hit.near;
  float edgeDistance = facetEdge(c, pos, hit.normal, hit.enterFace);
  float planeNoise = hashFract(dot(hit.normal, float3(127.1, 311.7, 91.3)));
  float arrival = 2.42 + fract(atan2(hit.normal.y, hit.normal.x) / (2.0 * PI) + 0.5) * 1.03 + planeNoise * 0.12;
  float t = P.view.w;
  float age = phase(t, arrival, arrival + 0.18);
  float evaporation = 1.0 - pow(1.0 - age, 3.0);
  float grain = hashFract(dot(floor(pos.xy * 180.0), float2(127.1, 311.7)));
  float threshold = clamp(edgeDistance * 12.0, 0.0, 0.68) + 0.12 + grain * 0.12;
  float cleared = smoothstep(threshold - 0.10, threshold + 0.10, evaporation);
  if (age <= 0.0) { cleared = 0.0; }
  if (age >= 1.0 || t >= 3.8 || (P.effects.w > 0.5 && P.state.x > 0.001)) { cleared = 1.0; }
  cleared = max(cleared, smoothstep(0.55, 0.96, breathAt(P, input.point)));
  float mark = fadePhase(t, 2.1, 2.4) * (1.0 - cleared) * exp(-edgeDistance * 230.0);
  float laser = fadePhase(t, 2.4, 2.42) * exp(-pow((t - arrival) / 0.032, 2.0)) * exp(-edgeDistance * 190.0);
  if (cleared <= 0.0) { return makeMaterial(float4(0.0), float4(0.0, mark, laser, 1.0)); }
  float2 skinPoint = float2(175.0 + pos.x * 105.0, 278.0 - pos.y * 105.0);
  float2 v = skinPoint - float2(183.5, 279.5);
  float angle = 0.5 * PI / 180.0;
  float2 uv = (float2(v.x * cos(angle) - v.y * sin(angle), v.x * sin(angle) + v.y * cos(angle)) + float2(125.5, 100.5)) / float2(251.0, 201.0);
  float3 map = c.rounded.sample(c.s, uv, level(0)).rgb * 2.0 - 1.0;
  float2 screenNormal = float2(map.x * cos(-angle) - map.y * sin(-angle), map.x * sin(-angle) + map.y * cos(-angle));
  float3 rounded = normalize(float3(screenNormal.x, -screenNormal.y, max(0.15, map.z)));
  float normalClear = P.effects.w > 0.5 ? 1.0 : cleared;
  float3 n = normalize(mix(rounded, hit.normal, normalClear));
  float3 nw = opticalNormal(c, n, id);
  float fres = 0.173 + 0.827 * pow(1.0 - abs(dot(nw, -cameraRay)), 5.0);
  float4 carried = float4(0.0);
  float carryWeight = 0.0;
  bool asm_ = assembled(c);
  if (!asm_ && t > 3.9 && t < 6.35 && hit.normal.z > 0.15) {
    carried = c.cutTex.sample(c.s, (skinPoint - P.crystalViewport.xy) / P.crystalViewport.zw, level(0));
    carryWeight = 1.0 - fadePhase(t, 6.05, 6.35);
    if (carryWeight >= 1.0 && carried.a > 0.98) {
      float capDistance = 100.0;
      uint sector = hit.enterFace;
      for (uint k = 0u; k < c.pieces[sector].caps.y; k++) {
        if (k < uint(c.pieces[sector].pivot.w) || (c.pieces[sector].caps.x & (1u << k)) == 0u) { continue; }
        float4 plane = c.pieces[sector].planes[k];
        capDistance = min(capDistance, abs(plane.w - dot(plane.xyz, pos)));
      }
      float rim = exp(-capDistance * 105.0);
      return makeMaterial(float4(carried.rgb / carried.a + float3(1.0, 0.86, 1.13) * rim * 0.48, 1.0), float4(1.0, 0.0, 0.0, 1.0));
    }
  }
  float3 volume = optical(c, pos, ray, n, id);
  float3 body = mix(float3(1.0), float3(0.97, 0.92, 1.0), P.effects.z);
  float3 through = volume * body;
  float3 reflectedRay = reflect(cameraRay, nw);
  float3 wpos = worldPoint(c, pos, id);
  float3 reflection = environment(c, normalize(reflectedRay + wpos * float3(0.16, 0.13, 0.08))) + plaqueReflection(c, wpos, reflectedRay);
  float3 clear = screenTransmission(c, pos, ray, n, id, 2.42) * body * 0.96;
  float clearWeight = mix(0.52, 0.72, pow(abs(dot(nw, -cameraRay)), 4.0));
  float3 radiance = mix(mix(through, clear, clearWeight), reflection, fres);
  if (!asm_) {
    float capDistance = 100.0;
    uint sector = hit.enterFace;
    for (uint k = 0u; k < c.pieces[sector].caps.y; k++) {
      if (k < uint(c.pieces[sector].pivot.w) || (c.pieces[sector].caps.x & (1u << k)) == 0u) { continue; }
      float4 plane = c.pieces[sector].planes[k];
      capDistance = min(capDistance, abs(plane.w - dot(plane.xyz, pos)));
    }
    float rim = exp(-capDistance * 105.0);
    float cutFace = 1.0 - smoothstep(0.10, 0.32, abs(hit.normal.z));
    float3 seen = screenTransmission(c, pos, ray, n, id, 1.52);
    float3 thick = mix(float3(0.68, 0.52, 0.65), float3(1.05, 0.93, 1.12), sat(abs(nw.x) * 0.65 + abs(nw.y) * 0.45));
    float3 prism = float3(1.0, 0.86, 1.13) + float3(0.15 * sin(capDistance * 280.0), 0.08, 0.15 * cos(capDistance * 280.0));
    radiance = mix(radiance, seen * 0.48 + thick, cutFace * 0.9);
    radiance += rim * prism * 0.48;
    if (carryWeight > 0.0) {
      float3 front = carried.rgb / max(carried.a, 0.001);
      float valid = smoothstep(0.05, 0.95, carried.a);
      radiance = mix(radiance, mix(radiance, front, valid) + rim * prism * 0.48, carryWeight * (1.0 - cutFace));
      carryWeight = 0.0;
    }
  }
  float edge = exp(-edgeDistance * 420.0);
  radiance += edge * (reflection + environment(c, normalize(nw + float3(-0.1, 0.4, 0.2)))) * 0.16;
  radiance += edge * environment(c, normalize(float3(-0.18, 0.78, 0.60))) * abs(dot(nw, normalize(float3(-0.18, 0.78, 0.60)))) * 0.024;
  float core = exp(-length(input.point - float2(185.0, 289.0)) / 62.0) * P.breath.w * 0.12;
  radiance += float3(1.0, 0.86, 0.90) * core * cleared;
  float polish = exp(-pow((camera.x + camera.y * 0.18 - P.light.x) / 0.055, 2.0)) * P.effects.y * 0.5;
  radiance += float3(polish);
  return makeMaterial(mix(float4(radiance, 1.0), carried, carryWeight), float4(cleared, mark, laser, 1.0));
}

inline bool foregroundOccludes(constant Params &P, float2 screen) {
  float keep = 1.0 - P.state.w;
  float open = sat(P.state.y) * keep;
  float2 p = (screen - float2(175.0, 278.0) - P.backdrop.xy * keep) / (1.0 + P.effects.x * keep) + float2(175.0, 278.0);
  float2 delta = screen - float2(187.5, 291.0);
  float cs = cos(-0.36), sn = sin(-0.36);
  float2 v = float2(delta.x * cs + delta.y * sn, -delta.x * sn + delta.y * cs);
  float2 q = abs(v) - float2(33.5, 3.0);
  float sdf = length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - 38.0;
  return sdf < 2.0 || (open < 0.5 && distance(p, float2(286.5, 208.5)) < 23.0);
}

struct CompositeOut {
  float4 pos [[position]];
  float2 uv;
};

inline float4 crystalAt(texture2d<float> crystal, sampler s, float2 uv) {
  if (any(uv < float2(0.0)) || any(uv > float2(1.0))) { return float4(0.0); }
  return crystal.sample(s, uv, level(0));
}
float3 reconstructCrystal(constant Params &P, texture2d<float> crystal, sampler s, float2 crystalUV, float fullCoverage) {
  float4 source = crystalAt(crystal, s, crystalUV);
  float2 texel = 1.0 / P.crystalRaster.xy;
  float4 neighbors = (crystalAt(crystal, s, crystalUV + float2(texel.x, 0.0)) + crystalAt(crystal, s, crystalUV - float2(texel.x, 0.0))
                    + crystalAt(crystal, s, crystalUV + float2(0.0, texel.y)) + crystalAt(crystal, s, crystalUV - float2(0.0, texel.y))) * 0.25;
  float3 detail = clamp(source.rgb - neighbors.rgb, -float3(0.12), float3(0.12));
  float4 supported = source;
  if (fullCoverage > 0.0 && source.a < 0.999) {
    float4 sum = float4(0.0);
    float weight = 0.0;
    for (int y = -1; y <= 1; y++) {
      for (int x = -1; x <= 1; x++) {
        float2 q = float2(float(x), float(y));
        float w = 1.0 / (1.0 + dot(q, q));
        sum += crystalAt(crystal, s, crystalUV + q * texel) * w;
        weight += w;
      }
    }
    if (sum.a < 0.001 * weight) {
      for (int y = -2; y <= 2; y++) {
        for (int x = -2; x <= 2; x++) {
          if (abs(x) < 2 && abs(y) < 2) { continue; }
          float2 q = float2(float(x), float(y));
          float w = 1.0 / (1.0 + dot(q, q));
          sum += crystalAt(crystal, s, crystalUV + q * texel) * w;
          weight += w;
        }
      }
    }
    supported = sum / weight;
  }
  return max(float3(0.0), supported.rgb + detail * 0.28 * step(0.999, source.a)) / max(0.001, supported.a);
}

inline float loadAlpha(texture2d<float> t, float2 pos) {
  uint2 px = uint2(clamp(pos, float2(0.0), float2(float(t.get_width() - 1), float(t.get_height() - 1))));
  return t.read(px).a;
}

} // namespace frostHeartRewardsGPU

using namespace frostHeartRewardsGPU;

#define FHR_CTX Ctx c = { P, pieces, bear, hug, page, rounded, cutTex, s }

vertex Out frostHeartRewardsVertex(uint vi [[vertex_id]], uint id [[instance_id]],
                                   constant Params &P [[buffer(0)]], constant Piece *pieces [[buffer(1)]],
                                   texture2d<float> bear [[texture(0)]], texture2d<float> hug [[texture(1)]],
                                   texture2d<float> page [[texture(2)]], texture2d<float> rounded [[texture(4)]],
                                   texture2d<float> cutTex [[texture(5)]], sampler s [[sampler(0)]]) {
  FHR_CTX;
  const float2 corners[6] = { float2(-1, -1), float2(1, -1), float2(-1, 1), float2(-1, 1), float2(1, -1), float2(1, 1) };
  float2 uv = corners[vi];
  float S = P.view.z;
  float zoom = 1.0 + P.effects.x;
  float2 at = float2(175.0, 278.0);
  float2 extent = float2(0.0);
  float alpha = 0.0;
  if (id < SECTORS) {
    uint base = (id / 2u) * 2u;
    float3 pivot = pieces[base].pivot.xyz;
    float3 cc = worldPoint(c, pivot, id);
    float radius = max(pieces[base].center.w + distance(pieces[base].center.xyz, pivot), pieces[base + 1u].center.w + distance(pieces[base + 1u].center.xyz, pivot));
    at += float2(cc.x, -cc.y) * 105.0 * zoom;
    extent = float2(radius * 108.0 * zoom);
    alpha = 1.0 - P.state.w;
    if (id % 2u == 1u) { alpha = 0.0; }
    if (assembled(c)) {
      float3 center = forward(c, float3(0.08446908, -0.03181964, 0.13));
      float yaw = P.placement.z, pitch = P.placement.y;
      float2 bound = float2(abs(cos(yaw)) * 1.11478722 + abs(sin(yaw) * sin(pitch)) * 0.88462955 + abs(sin(yaw) * cos(pitch)) * 0.52,
                            abs(cos(pitch)) * 0.88462955 + abs(sin(pitch)) * 0.52);
      at = float2(175.0, 278.0) + float2(center.x, -center.y) * 105.0 * zoom;
      extent = bound * 105.0 * zoom + float2(1.5);
      if (id > 0u) { alpha = 0.0; }
    }
  } else if (id < (SECTORS + 40u)) {
    uint i = (id - SECTORS) / 8u;
    float tail = float((id - SECTORS) % 8u);
    float age = ageFor(P, i);
    float u = easing(phase(age, 0.0, 0.65));
    float q = max(0.0, u - tail * 0.006);
    float2 start = float2(147.5 + float(i) * 54.0, 511.0);
    float2 control = float2(start.x - 25.0, 350.0);
    at = (1.0 - q) * (1.0 - q) * start + 2.0 * (1.0 - q) * q * control + q * q * float2(175.0, 278.0);
    extent = float2((3.8 - tail * 0.35) * 1.6);
    alpha = (age > 0.0 && age < 0.65 ? pow(1.0 - tail / 8.0, 1.6) * fadePhase(age, 0.0, 0.04) * (1.0 - fadePhase(age, 0.60, 0.65)) : 0.0) * (1.0 - P.state.w);
  } else if (id == (SECTORS + 52u)) {
    at = float2(184.0, 354.0);
    extent = float2(112.0, 31.0);
    alpha = P.state.x * P.state.z * (1.0 - P.state.w);
  } else {
    alpha = 0.0;
    extent = float2(0.0);
  }
  if (P.effects.w > 0.5 && id >= SECTORS) { alpha = 0.0; }
  if (alpha < 0.001) { extent = float2(0.0); }
  float2 point = at + uv * extent;
  if (id < SECTORS) { point += P.backdrop.xy * (1.0 - P.state.w); }
  float2 pixel = (point - P.viewport.xy) * S + float2(0.0, P.placement.x);
  float2 clip = float2(pixel.x / P.view.x * 2.0 - 1.0, 1.0 - pixel.y / P.view.y * 2.0);
  if (id < SECTORS) {
    float2 gemPixel = (point - P.crystalViewport.xy) * P.crystalRaster.zw;
    clip = float2(gemPixel.x / P.crystalRaster.x * 2.0 - 1.0, 1.0 - gemPixel.y / P.crystalRaster.y * 2.0);
  }
  Out o;
  o.pos = float4(clip, 0.5, 1.0);
  o.point = point;
  o.uv = uv;
  o.id = id;
  o.alpha = alpha;
  return o;
}

fragment MaterialOut frostHeartRewardsFragment(Out input [[stage_in]],
                                               constant Params &P [[buffer(0)]], constant Piece *pieces [[buffer(1)]],
                                               texture2d<float> bear [[texture(0)]], texture2d<float> hug [[texture(1)]],
                                               texture2d<float> page [[texture(2)]], texture2d<float> rounded [[texture(4)]],
                                               texture2d<float> cutTex [[texture(5)]], sampler s [[sampler(0)]]) {
  FHR_CTX;
  return material(c, input);
}

fragment float4 frostHeartRewardsOverlayFragment(Out input [[stage_in]],
                                                 constant Params &P [[buffer(0)]], constant Piece *pieces [[buffer(1)]],
                                                 texture2d<float> bear [[texture(0)]], texture2d<float> hug [[texture(1)]],
                                                 texture2d<float> page [[texture(2)]], texture2d<float> rounded [[texture(4)]],
                                                 texture2d<float> cutTex [[texture(5)]], sampler s [[sampler(0)]]) {
  FHR_CTX;
  return material(c, input).colour;
}

kernel void frostHeartRewardsGlints(uint lane [[thread_index_in_threadgroup]],
                                    constant Params &P [[buffer(0)]], constant Piece *pieces [[buffer(1)]],
                                    device float4 *glints [[buffer(2)]],
                                    texture2d<float> crystal [[texture(6)]]) {
  threadgroup float peakScores[256];
  threadgroup float2 peakPoints[256];
  Ctx c = { P, pieces, crystal, crystal, crystal, crystal, crystal, sampler() };
  float2 scale = P.crystalRaster.zw;
  float2 origin = (float2(45.0, 175.0) - P.crystalViewport.xy) * scale;
  uint2 size = uint2(260u, 220u);
  float score = 0.0;
  float2 point = float2(0.0);
  float hold = fadePhase(P.view.w, 7.4, 7.65) * (1.0 - fadePhase(P.view.w, 8.65, 8.9));
  int2 maxPx = int2(int(crystal.get_width()) - 1, int(crystal.get_height()) - 1);
  if (hold <= 0.0) {
    for (uint index = lane; index < size.x * size.y; index += 256u) {
      float2 design = float2(float(index % size.x), float(index / size.x));
      int2 px = int2(origin + design * scale);
      float4 cc = crystal.read(uint2(clamp(px, int2(0), maxPx)));
      float2 screen = float2(px) / scale + P.crystalViewport.xy;
      float value = (foregroundOccludes(P, screen) || (hold > 0.01 && screen.y > 275.0)) ? 0.0 : max(cc.r, max(cc.g, cc.b));
      if (value > score) { score = value; point = screen; }
    }
  }
  peakScores[lane] = score;
  peakPoints[lane] = point;
  threadgroup_barrier(mem_flags::mem_threadgroup);
  if (lane == 0u) {
    float4 targets[3] = { float4(0.0), float4(0.0), float4(0.0) };
    for (uint star = 0u; star < 3u; star++) {
      float best = 0.02;
      float2 at = float2(0.0);
      if (hold <= 0.0) {
        for (uint j = 0u; j < 256u; j++) {
          bool separate = true;
          for (uint previous = 0u; previous < star; previous++) {
            if (distance(peakPoints[j], targets[previous].xy) < 26.0) { separate = false; }
          }
          if (separate && peakScores[j] > best) { best = peakScores[j]; at = peakPoints[j]; }
        }
      }
      targets[star] = float4(at, smoothstep(0.02, 0.35, best) * 0.85, best);
      if (hold > 0.0) {
        float age = P.view.w - 7.4;
        float k = float(star);
        const float3 anchors[3] = { float3(0.60, 0.54, -0.12), float3(-0.58, 0.52, -0.12), float3(0.88, 0.12, -0.10) };
        float3 anchor = anchors[star] + float3(0.045 * sin(age * 2.2 + k * 1.7), 0.025 * sin(age * 1.9 + k * 2.0), 0.0);
        float3 w = forward(c, anchor);
        float2 pin = float2(175.0, 278.0) + float2(w.x, -w.y) * 105.0 * (1.0 + P.effects.x) + P.backdrop.xy * (1.0 - P.state.w);
        int2 px = int2((pin - P.crystalViewport.xy) * P.crystalRaster.zw);
        float4 surface = crystal.read(uint2(clamp(px, int2(0), maxPx)));
        float visible = (surface.a > 0.9 && !foregroundOccludes(P, pin)) ? 1.0 : 0.0;
        float breath = 0.55 + 0.40 * pow(sin(age * 2.4 + k * 1.2), 2.0);
        targets[star] = float4(pin, visible * breath * hold, max(surface.r, max(surface.g, surface.b)));
      }
    }
    for (uint star = 0u; star < 3u; star++) {
      float4 nextPeak = targets[star];
      float4 old = glints[star];
      float blend = 1.0 - exp(-P.backdrop.w / 0.09);
      if (P.backdrop.w > 0.5) { glints[star] = nextPeak; }
      else if (distance(nextPeak.xy, old.xy) > 35.0 && old.z > 0.025) { glints[star] = float4(old.xy, old.z * (1.0 - blend), old.w); }
      else { glints[star] = float4(mix(old.xy, nextPeak.xy, blend), mix(old.z, nextPeak.z, blend), nextPeak.w); }
    }
  }
}

vertex float4 frostHeartRewardsCoverageVertex(uint vi [[vertex_id]], constant float4 *vertices [[buffer(3)]],
                                              constant Params &P [[buffer(0)]], constant Piece *pieces [[buffer(1)]]) {
  texture2d<float> none;
  Ctx c = { P, pieces, none, none, none, none, none, sampler() };
  float4 vtx = vertices[vi];
  float3 w = worldPoint(c, vtx.xyz, uint(vtx.w));
  float2 point = float2(175.0, 278.0) + float2(w.x, -w.y) * 105.0 * (1.0 + P.effects.x) + P.backdrop.xy * (1.0 - P.state.w);
  float2 pixel = (point - P.viewport.xy) * P.view.z;
  return float4(pixel.x / P.view.x * 2.0 - 1.0, 1.0 - pixel.y / P.view.y * 2.0, 0.5, 1.0);
}
fragment float4 frostHeartRewardsCoverageFragment() { return float4(1.0); }

vertex CompositeOut frostHeartRewardsCompositeVertex(uint vi [[vertex_id]]) {
  const float2 corners[3] = { float2(-1, -1), float2(3, -1), float2(-1, 3) };
  float2 p = corners[vi];
  CompositeOut o;
  o.pos = float4(p, 0.0, 1.0);
  o.uv = float2(p.x * 0.5 + 0.5, 0.5 - p.y * 0.5);
  return o;
}

fragment float4 frostHeartRewardsBreathFragment(CompositeOut input [[stage_in]],
                                                constant Params &P [[buffer(0)]], constant Piece *pieces [[buffer(1)]],
                                                texture2d<float> bear [[texture(0)]], texture2d<float> hug [[texture(1)]],
                                                texture2d<float> page [[texture(2)]], texture2d<float> skin [[texture(3)]],
                                                texture2d<float> crystal [[texture(6)]], texture2d<float> coverageTex [[texture(8)]],
                                                sampler s [[sampler(0)]]) {
  Ctx c = { P, pieces, bear, hug, page, page, page, s };
  float2 point = (input.uv * P.view.xy - float2(0.0, P.placement.x)) / P.view.z + P.viewport.xy;
  float wet = breathAt(P, point);
  if (wet <= 0.0001) { discard_fragment(); }
  float2 crystalUV = (point - P.crystalViewport.xy) / P.crystalViewport.zw;
  float coverage = loadAlpha(coverageTex, input.pos.xy) * (1.0 - P.state.w);
  if (coverage <= 0.0) { discard_fragment(); }
  float4 sk = skin.sample(s, point / float2(402.0, 440.0), level(0));
  float3 crystalColour = pow(filmic(reconstructCrystal(P, crystal, s, crystalUV, coverage)), float3(1.0 / 2.2));
  float reveal = smoothstep(0.55, 0.96, wet);
  float3 background = pow(backdrop(c, point), float3(1.0 / 2.2));
  float3 frost = mix(background, sk.rgb, sk.a);
  float2 soften = float2(2.5, 0.0);
  float3 softSkin = (skin.sample(s, (point + soften.xy) / float2(402.0, 440.0), level(0)).rgb + skin.sample(s, (point - soften.xy) / float2(402.0, 440.0), level(0)).rgb
                   + skin.sample(s, (point + soften.yx) / float2(402.0, 440.0), level(0)).rgb + skin.sample(s, (point - soften.yx) / float2(402.0, 440.0), level(0)).rgb) * 0.25;
  float3 softened = mix(background, softSkin, sk.a);
  float3 thin = mix(frost, max(frost, softened), 0.65 * wet);
  float3 damp = mix(thin, max(thin, float3(0.96, 0.97, 1.0)), 0.36 * sqrt(wet));
  float3 luma = float3(0.2126, 0.7152, 0.0722);
  float minimum = min(1.0, dot(frost, luma) + 0.018);
  float luminance = dot(crystalColour, luma);
  float lift = sat((minimum - luminance) / max(0.001, 1.0 - luminance));
  float3 clearGlass = mix(crystalColour, float3(1.0), lift);
  float ring = exp(-pow((breathDistance(P, point) - 30.0) / 2.2, 2.0)) * P.breath.z * 0.045;
  float3 wetColour = mix(damp, clearGlass, reveal) + float3(0.90, 0.95, 1.0) * ring;
  float alpha = max(1.0 - pow(1.0 - wet, 3.0), ring) * coverage;
  return float4(wetColour * alpha, alpha);
}

fragment float4 frostHeartRewardsCompositeFragment(CompositeOut input [[stage_in]],
                                                   constant Params &P [[buffer(0)]], constant float4 *glints [[buffer(2)]],
                                                   constant float2 *portals [[buffer(3)]],
                                                   texture2d<float> skin [[texture(3)]], texture2d<float> crystal [[texture(6)]],
                                                   texture2d<float> materialTex [[texture(7)]], texture2d<float> coverageTex [[texture(8)]],
                                                   sampler s [[sampler(0)]]) {
  float2 px = input.uv * P.view.xy;
  float2 point = (px - float2(0.0, P.placement.x)) / P.view.z + P.viewport.xy;
  float2 crystalUV = (point - P.crystalViewport.xy) / P.crystalViewport.zw;
  float4 info = materialTex.sample(s, crystalUV, level(0));
  float fullCoverage = loadAlpha(coverageTex, input.pos.xy);
  float4 sk = skin.sample(s, point / float2(402.0, 440.0), level(0));
  float t = P.view.w;
  float cut = (t > 2.40001 || (P.effects.w > 0.5 && P.state.x > 0.001)) ? 1.0 : 0.0;
  float clear = info.r / max(0.001, info.a);
  if (t >= 3.8 || (P.effects.w > 0.5 && P.state.x > 0.001)) { clear = 1.0; }
  if (cut == 0.0) {
    float etch = info.g * 0.12 * fullCoverage;
    return float4(float3(0.65, 0.69, 0.77) * etch, etch);
  }
  float3 crystalColour = pow(filmic(reconstructCrystal(P, crystal, s, crystalUV, fullCoverage)), float3(1.0 / 2.2));
  float3 glow = float3(0.0);
  if (t >= 2.4 && point.x > 25.0 && point.x < 325.0 && point.y > 140.0 && point.y < 405.0) {
    for (uint j = 0u; j < 12u; j++) {
      float angle = float(j) * 2.399963;
      float radius = mix(12.0, 24.0, float(j % 3u) / 2.0);
      float2 uv = crystalUV + float2(cos(angle), sin(angle)) * radius / P.crystalViewport.zw;
      float3 smp = crystalAt(crystal, s, uv).rgb;
      glow += min(max(smp - float3(2.8), float3(0.0)), float3(8.0)) * 0.0045;
    }
  }
  float flare = 0.0, halo = 0.0;
  if (P.effects.w < 0.5) {
    for (uint j = 0u; j < 3u; j++) {
      float4 peak = glints[j];
      float2 q = abs(point - peak.xy);
      float quiet = mix(0.22, 1.0, fadePhase(t, 6.4, 6.65));
      float gate = peak.z * P.state.x * (1.0 - P.state.w) * quiet;
      float cross = exp(-q.x * 3.5 - q.y * 0.35) + exp(-q.y * 3.5 - q.x * 0.35);
      flare += cross * gate * (1.0 - smoothstep(8.0, 12.0, max(q.x, q.y)));
      halo += exp(-dot(q, q) / 64.0) * gate * 0.035;
    }
  }
  float coverage = fullCoverage * (1.0 - P.state.w);
  float skinAlpha = sk.a * (1.0 - P.state.w);
  bool nativeSkin = t < 3.9;
  float alpha = nativeSkin ? coverage * clear : mix(skinAlpha, coverage, clear);
  float3 colour = nativeSkin ? crystalColour * coverage * clear : mix(sk.rgb * skinAlpha, crystalColour * coverage, clear);
  float etch = info.g * 0.12 * coverage;
  colour = colour * (1.0 - etch) + float3(0.65, 0.69, 0.77) * etch;
  float laser = info.b * 0.7 * coverage;
  colour += float3(laser * 0.9, laser * 0.84, laser * 0.74);
  float completion = 0.0, rays = 0.0;
  if (P.effects.w < 0.5) {
    float attack = 1.0 - pow(1.0 - phase(t, 3.8, 3.92), 3.0);
    float release = pow(1.0 - phase(t, 3.92, 4.22), 3.0);
    completion = attack * release * coverage * 0.30;
    float rayWindow = fadePhase(t, 2.4, 2.5) * (1.0 - fadePhase(t, 3.8, 3.94));
    float2 core = float2(185.0, 289.0);
    if (rayWindow > 0.0) {
      for (uint k = 0u; k < 12u; k++) {
        float2 portal = portals[k];
        float2 direction = normalize(portal - core);
        float2 opening = mix(core, portal, 0.88);
        float4 surface = materialTex.sample(s, (opening - P.crystalViewport.xy) / P.crystalViewport.zw, level(0));
        float gate = sat(surface.r / max(0.001, surface.a) + surface.b * 0.8) * surface.a;
        float2 delta = point - portal;
        float along = dot(delta, direction);
        float across = abs(delta.x * direction.y - delta.y * direction.x);
        float streak = exp(-across * across / 0.21) * exp(-max(0.0, along) / 12.0) * smoothstep(-4.0, 1.0, along) * (1.0 - smoothstep(19.0, 27.0, along));
        rays += streak * gate * P.breath.w * rayWindow * 0.29 * (1.0 - smoothstep(356.0, 365.0, point.y));
      }
    }
  }
  float3 signature = float3(1.0, 0.88, 0.93) * (completion + rays * (1.0 - coverage * 0.7));
  float3 extra = clamp((glow + float3(flare + halo)) * clear + signature, float3(0.0), float3(1.0));
  float extraAlpha = max(extra.r, max(extra.g, extra.b));
  colour = colour * (1.0 - extraAlpha) + extra;
  return float4(colour, alpha + (etch + extraAlpha - etch * extraAlpha) * (1.0 - alpha));
}

// ---------------------------------------------------------------------------------------------
// Static page rays (RAYS_SKSL with every uniform at zero) and the one-off raster passes.

namespace frostHeartRewardsGPU {
constant float RAY_ANGLES[14] = { -173, -158, -143, -130, -119, -108, -96, -84, -71, -58, -44, -29, -15, -3 };
constant float RAY_STRENGTHS[14] = { 0.45, 0.55, 0.48, 0.72, 0.61, 0.6, 0.76, 0.59, 0.72, 0.55, 0.68, 0.69, 0.38, 0.33 };
inline float hash2(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float3 rays(float2 p) {
  float2 origin = float2(177.0, 203.0);
  float2 q = p - origin;
  float x = atan2(q.y, q.x) + 3.14159265;
  float a = (x - 6.2831853 * floor(x / 6.2831853)) - 3.14159265;
  float beam = 0.0;
  for (int i = 0; i < 14; i++) {
    float d = abs(a - RAY_ANGLES[i] * 3.14159265358979 / 180.0);
    d = min(d, 6.2831853 - d);
    float w = 0.025 + float(i % 3) * 0.009;
    beam += RAY_STRENGTHS[i] * exp(-0.5 * (d / w) * (d / w));
  }
  float dist = length(q);
  beam *= sat(dist / 145.0) * exp(-dist / 440.0) * sat((510.0 - p.y) / 270.0);
  float lum = beam * 0.082;
  float3 rgb = float3(0.07451) + float3(lum);
  float2 glow = (p - float2(191.0, 370.0)) / float2(57.0, 32.0);
  rgb += float3(0.32, 0.1412, 0.36) * exp(-0.5 * dot(glow, glow));
  float2 rim = (p - float2(266.0, 345.0)) / float2(29.0, 43.0);
  rgb += float3(0.36, 0.09, 0.31) * exp(-0.5 * dot(rim, rim));
  rgb += (hash2(floor(p * 3.0)) - 0.5) * 0.002;
  return rgb;
}
}

[[stitchable]] half4 frostHeartRewardsRays(float2 p) { return half4(half3(clamp(rays(p), 0.0, 1.0)), 1.0h); }

fragment float4 frostHeartRewardsPageFragment(CompositeOut input [[stage_in]]) {
  return float4(clamp(rays(input.pos.xy), 0.0, 1.0), 1.0);
}

struct FrostHeartRewardsSkinParams { float4 box; float rotation; float scale; float2 direction; float sigma; float floorY; float floorFade; float pad; };

// The heart sprite rotated into its screen box (premultiplied, bilinear, no mipmaps).
fragment float4 frostHeartRewardsSkinDrawFragment(CompositeOut input [[stage_in]], constant FrostHeartRewardsSkinParams &K [[buffer(0)]],
                                                  texture2d<float> heart [[texture(0)]]) {
  constexpr sampler s(coord::normalized, address::clamp_to_zero, filter::linear);
  float2 p = input.pos.xy / K.scale;
  float2 center = K.box.xy + K.box.zw * 0.5;
  float a = -K.rotation;
  float2 v = p - center;
  float2 local = float2(v.x * cos(a) - v.y * sin(a), v.x * sin(a) + v.y * cos(a)) + center;
  float2 uv = (local - K.box.xy) / K.box.zw;
  if (any(uv < float2(0.0)) || any(uv > float2(1.0))) { return float4(0.0); }
  return heart.sample(s, uv);
}
// One separable Gaussian pass (decal edges). The second pass applies the floor mask and
// writes straight alpha, like the PNG round trip in fhr-backdrop.ts.
fragment float4 frostHeartRewardsSkinBlurFragment(CompositeOut input [[stage_in]], constant FrostHeartRewardsSkinParams &K [[buffer(0)]],
                                                  texture2d<float> source [[texture(0)]]) {
  int radius = int(ceil(K.sigma * 3.0));
  int2 size = int2(source.get_width(), source.get_height());
  int2 at = int2(input.pos.xy);
  float4 sum = float4(0.0);
  float weight = 0.0;
  for (int i = -radius; i <= radius; i++) {
    float w = exp(-0.5 * float(i * i) / (K.sigma * K.sigma));
    int2 q = at + int2(K.direction) * i;
    weight += w;
    if (q.x < 0 || q.y < 0 || q.x >= size.x || q.y >= size.y) { continue; }
    sum += source.read(uint2(q)) * w;
  }
  sum /= weight;
  if (K.floorFade > 0.0) {
    float y = input.pos.y / K.scale;
    float m = 1.0 - smoothstep(K.floorY, K.floorY + K.floorFade, y);
    sum *= m;
    if (sum.a <= 0.0) { return float4(0.0); }
    return float4(sum.rgb / sum.a, sum.a);
  }
  return sum;
}

// ---------------------------------------------------------------------------------------------
// The tgfx stage shaders (fhr-shaders.ts), used as SwiftUI fills.

[[stitchable]] half4 frostHeartRewardsSlab(float2 p, float time, float spotlight, float polish, float core) {
  float2 center = float2(187.5, 291.0);
  float2 q = abs(p - center) - float2(33.5, 3.0);
  float sd = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - 38.0;
  float a = 1.0 - smoothstep(-0.6, 0.5, sd);
  float v = clamp((p.y - 250.0) / 82.0, 0.0, 1.0);
  float3 c = mix(float3(1.0, 0.085, 0.84), float3(0.68, 0.025, 0.62), pow(v, 0.9));
  float grain = fract(sin(dot(floor(p * 1.5), float2(127.1, 311.7))) * 43758.5453) - 0.5;
  c += float3(grain * 0.012);
  float rim = exp(-pow((sd + 1.7) / 1.2, 2.0));
  float top = 1.0 - smoothstep(0.24, 0.62, v);
  c += float3(0.94, 0.78, 0.91) * rim * (0.10 + top * 0.5);
  c -= float3(0.13, 0.045, 0.10) * exp(-pow((sd + 3.5) / 2.8, 2.0)) * v;
  float shine = exp(-pow((p.y - 262.0 + (p.x - center.x) * 0.12) / 8.0, 2.0));
  c += float3(0.43, 0.25, 0.38) * shine * 0.30;
  float sweep = exp(-pow((p.x - center.x - sin(time * 0.6283185) * 68.0) / 13.0, 2.0));
  c += float3(0.21, 0.15, 0.19) * sweep * (spotlight * 0.32 + polish * 0.44);
  c += float3(0.15, 0.11, 0.13) * core * exp(-dot((p - center) / float2(78, 43), (p - center) / float2(78, 43)));
  return half4(half3(clamp(c, 0.0, 1.0) * a), half(a));
}

[[stitchable]] half4 frostHeartRewardsSpotlight(float2 p, float light) {
  float2 q = (p - float2(175, 270)) / float2(205, 245);
  float boundary = smoothstep(96.0, 130.0, p.y) * (1.0 - smoothstep(395.0, 480.0, p.y));
  float vignette = smoothstep(0.28, 1.08, length(q)) * 0.25 * light * boundary;
  float coneWidth = 28.0 + max(0.0, p.y - 76.0) * 0.47;
  float cone = (1.0 - smoothstep(coneWidth * 0.52, coneWidth, abs(p.x - 174.0)));
  cone *= smoothstep(78.0, 142.0, p.y) * (1.0 - smoothstep(300.0, 442.0, p.y));
  float lamp = cone * 0.11 * light * boundary;
  float alpha = vignette + lamp * (1.0 - vignette);
  float3 color = float3(1.0, 0.955, 0.865) * lamp * (1.0 - vignette);
  return half4(half3(color), half(alpha));
}

[[stitchable]] half4 frostHeartRewardsTag(float2 p, float light, float time, float polish, float platinum) {
  float v = clamp(dot(p - float2(295, 144), float2(67, 86)) / 11885.0, 0.0, 1.0);
  float3 purple = mix(float3(0.761, 0.408, 0.953), float3(0.631, 0.286, 0.890), min(v / 0.5, 1.0));
  purple = mix(purple, float3(0.690, 0.294, 0.894), max(0.0, (v - 0.5) * 2.0));
  float brush = (fract(sin(dot(float2(floor(p.x * 0.14), floor(p.y * 2.6)), float2(127.1, 311.7))) * 43758.5453) - 0.5) * mix(0.065, 0.018, platinum);
  float sweep = exp(-pow((p.x - mix(282.0, 371.0, clamp((time - 6.65) / 0.6, 0.0, 1.0))) / 7.0, 2.0));
  float band = 0.5 + 0.5 * cos(v * 12.0 - 0.7);
  float3 steel = mix(float3(0.25, 0.31, 0.41), float3(0.65, 0.72, 0.81), band);
  float3 bright = mix(float3(0.77, 0.80, 0.84), float3(0.99, 0.98, 0.95), band);
  bright += float3(0.045, 0.022, -0.018) * pow(abs(2.0 * v - 1.0), 6.0);
  float3 metal = mix(steel, bright, platinum);
  metal += float3(brush) + float3(0.22, 0.24, 0.27) * sweep * (0.35 + polish * 0.5);
  return half4(half3(mix(purple, clamp(metal, 0.0, 1.0), light)), 1.0h);
}

[[stitchable]] half4 frostHeartRewardsCaustic(float2 p, float time, float light, float display, float polish) {
  float2 q = (p - float2(177, 332)) / float2(124, 39);
  float mask = exp(-dot(q, q) * 1.8);
  float a = sin(p.x * 0.053 + sin(p.y * 0.095 + time * 0.6283185) * 2.0);
  float b = cos(p.y * 0.108 - p.x * 0.024 - time * 1.256637);
  float line = pow(max(0.0, 1.0 - abs(a + b) * 0.58), 12.0);
  float alpha = mask * line * light * (0.07 + display * 0.04 + polish * 0.055);
  return half4(half3(float3(0.78, 0.74, 1.0) * alpha), half(alpha));
}
