#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;
struct FilamentBulbDimmerUniforms { float4 view, anchor, bulb, glow, light, accent, arc, card, chips, misc; };
struct FilamentBulbDimmerMote { float4 home, seed; };
struct FilamentBulbDimmerSpark { float4 at, tint; };
struct FilamentBulbDimmerVOut { float4 pos [[position]]; float2 uv; };
struct FilamentBulbDimmerDOut { float4 pos [[position]]; float2 uv; float3 col; float soft; };
constant float filamentBulbDimmerTAU = 6.2831853f;
constant float filamentBulbDimmerPI = 3.14159265f;
constant float filamentBulbDimmerY_SOCKET = 0.5f;
constant float filamentBulbDimmerY_NECK = 1.12f;
constant float filamentBulbDimmerY_GLOBE = 2.74f;
constant float filamentBulbDimmerY_TOP = 2.16f;     // the cage's top ring of hooks
constant float filamentBulbDimmerY_BOT = 2.98f;     // its bottom ring
constant float filamentBulbDimmerR_TOP = 0.27f;
constant float filamentBulbDimmerR_BOT = 0.43f;
constant float filamentBulbDimmerR_FIL = 0.012f;
constant float filamentBulbDimmerR_WIRE = 0.0065f;
constant float filamentBulbDimmerGLASS = 0.022f;    // the envelope's thickness
// One-to-one translation of gpu/fbd-wgsl.ts. All scene quantities are linear sRGB HDR.
struct FilamentBulbDimmerScene {
 FilamentBulbDimmerUniforms F;
// the filamentBulbDimmerBulb, in R, down from the socket's top

float filamentBulbDimmerSq(float v) { return v * v; }
float3 filamentBulbDimmerLin(float3 c) { return pow(c, float3(2.2f)); }

float filamentBulbDimmerSeg2(float2 p, float2 a, float2 b) {
  auto pa = p - a; auto ba = b - a;
  return length(pa - ba * clamp(dot(pa, ba) / dot(ba, ba), 0.0f, 1.0f));
}
float filamentBulbDimmerSmin(float a, float b, float k) {
  auto h = clamp(0.5f + 0.5f * (b - a) / k, 0.0f, 1.0f);
  return mix(b, a, h) - k * h * (1.0f - h);
}
float filamentBulbDimmerSdRound(float2 p, float4 r, float rad) {
  auto c = r.xy + r.zw * 0.5f;
  auto q = abs(p - c) - r.zw * 0.5f + float2(rad);
  return length(max(q, float2(0.0f))) + min(max(q.x, q.y), 0.0f) - rad;
}

/* ------------------------------------------------------------------ the page and the light on it */

float3 filamentBulbDimmerPageBase(float2 p) {
  // the pin's page, #262630, a touch warmer towards the top left
  auto c = float3(0.149f, 0.149f, 0.188f);
  auto ty = 1.0f - smoothstep(0.0f, 0.5f, p.y / F.view.y);
  auto tx = 1.0f - smoothstep(0.0f, 0.8f, p.x / F.view.x);
  c = mix(c, float3(0.173f, 0.149f, 0.188f), ty * tx * 0.8f);
  return filamentBulbDimmerLin(c);
}

float2 filamentBulbDimmerFilamentAt() {
  auto R = F.anchor.z;
  return F.bulb.xy + float2(sin(F.bulb.z), cos(F.bulb.z)) * 2.55f * R;
}

// the light falling on the page (and on anything flat on it) at p
float filamentBulbDimmerIrradiance(float2 p) {
  auto R = F.anchor.z;
  auto zl = F.anchor.w;
  auto d = p - filamentBulbDimmerFilamentAt();
  auto d2 = dot(d, d);
  auto e = zl * zl * zl / pow(d2 + zl * zl, 1.5f);
  // the socket shades the light upwards a little
  auto up = smoothstep(0.5f, -1.0f, d.y / max(sqrt(d2), 1.0f));
  return e * (1.0f - 0.3f * up) + 0.035f * exp(-sqrt(d2) / (6.0f * R));
}

float3 filamentBulbDimmerPageLit(float2 p) {
  auto L = F.light.xyz * F.glow.z;
  return filamentBulbDimmerPageBase(p) + L * 0.075f * filamentBulbDimmerIrradiance(p);
}

// what the glass and the brass reflect when they look back at the page: the page and the app on it
float3 filamentBulbDimmerPageSeen(float2 p) {
  auto c = filamentBulbDimmerPageLit(p);
  auto L = F.light.xyz * F.glow.z * filamentBulbDimmerIrradiance(p);
  if (filamentBulbDimmerSdRound(p, F.card, 24.0f) < 0.0f) { c = filamentBulbDimmerLin(float3(0.239f, 0.22f, 0.247f)) * (1.0f + 2.0f * L); }
  auto chip = float4(0.0f, F.chips.z, F.chips.w, F.misc.x);
  if (filamentBulbDimmerSdRound(p, float4(F.chips.x, chip.yzw), 16.0f) < 0.0f || filamentBulbDimmerSdRound(p, float4(F.chips.y, chip.yzw), 16.0f) < 0.0f) {
    c = filamentBulbDimmerLin(float3(0.169f, 0.165f, 0.196f)) * (1.0f + 2.0f * L);
  }
  // the dial's band glows in the accent colour, its disc is white
  auto q = p - F.arc.xy;
  auto rm = (F.arc.z + F.arc.w) * 0.5f;
  auto bw = (F.arc.z - F.arc.w) * 0.5f;
  auto band = 1e5;
  if (q.y < 0.0f) { band = abs(length(q) - rm) - bw; } else { band = length(float2(abs(abs(q.x) - rm), max(q.y - F.misc.z, 0.0f))) - bw; }
  c += F.accent.xyz * F.light.w * 1.6f * (1.0f - smoothstep(-1.0f, 1.0f, band)) + F.accent.xyz * F.light.w * 0.25f * exp(-max(band, 0.0f) / 14.0f);
  if (length(q - float2(0.0f, -12.0f)) < F.misc.y) { c = float3(0.85f); }
  return c;
}

// the room in front of the screen: dark, a tall soft window up on the left, a cool fill on the right
float3 filamentBulbDimmerRoomFront(float3 r, float hi) {
  // a dim warm room: brighter overhead, a soft floor bounce
  auto c = mix(filamentBulbDimmerLin(float3(0.1f, 0.095f, 0.11f)), filamentBulbDimmerLin(float3(0.34f, 0.31f, 0.32f)), smoothstep(0.3f, -0.8f, r.y));
  auto k = normalize(float3(-0.46f, -0.62f, 0.64f));
  auto u = normalize(cross(k, float3(0.0f, 1.0f, 0.0f)));
  auto v = cross(u, k);
  auto a = float2(dot(r, u), dot(r, v)) / max(dot(r, k), 0.05f);
  auto win = (1.0f - smoothstep(0.15f, 0.2f, abs(a.x))) * (1.0f - smoothstep(0.27f, 0.33f, abs(a.y))) * step(0.0f, dot(r, k));
  // a tall window in two panes
  c += float3(1.0f, 0.97f, 0.93f) * 16.0f * hi * win * smoothstep(0.012f, 0.03f, abs(a.x));
  // a broad soft glow round it, and a cool fill on the right
  c += filamentBulbDimmerLin(float3(0.5f, 0.48f, 0.5f)) * 0.6f * smoothstep(0.7f, 0.97f, dot(r, k));
  auto f = normalize(float3(0.85f, -0.05f, 0.52f));
  c += filamentBulbDimmerLin(float3(0.45f, 0.5f, 0.62f)) * 1.2f * smoothstep(0.88f, 0.99f, dot(r, f));
  return c;
}

float3 filamentBulbDimmerEnv(float3 o, float3 r, float hi) {
  if (r.z < -0.02f) {
    auto t = -o.z / r.z;
    return filamentBulbDimmerPageSeen(o.xy + r.xy * t);
  }
  return filamentBulbDimmerRoomFront(r, hi);
}

/* ------------------------------------------------------------------ the cord */

float filamentBulbDimmerCordDist(float2 p) {
  auto a = F.anchor.xy;
  auto b = F.bulb.xy;
  auto c = (a + b) * 0.5f + float2(0.0f, F.glow.x * 2.0f);
  auto dmin = 1e5;
  auto prev = a;
  for (int i = 1; i <= 10; i++) {
    auto t = float(i) / 10.0f;
    auto q = mix(mix(a, c, t), mix(c, b, t), t);
    dmin = min(dmin, filamentBulbDimmerSeg2(p, prev, q));
    prev = q;
  }
  return dmin;
}

/* ------------------------------------------------------------------ the filamentBulbDimmerBulb, in its own frame (R) */

float filamentBulbDimmerGlassProfile(float r, float y) {
  auto sph = length(float2(r, y - filamentBulbDimmerY_GLOBE)) - 1.0f;
  auto neck = filamentBulbDimmerSeg2(float2(r, y), float2(0.0f, filamentBulbDimmerY_NECK), float2(0.0f, filamentBulbDimmerY_GLOBE - 0.5f)) - 0.3f;
  return max(filamentBulbDimmerSmin(sph, neck, 0.45f), filamentBulbDimmerY_NECK - 0.02f - y);
}
float filamentBulbDimmerGlassSdf(float3 p) { return filamentBulbDimmerGlassProfile(length(p.xz), p.y); }
float3 filamentBulbDimmerGlassNormal(float3 p) {
  auto r = max(length(p.xz), 1e-4f);
  auto e = 0.002f;
  auto gr = filamentBulbDimmerGlassProfile(r + e, p.y) - filamentBulbDimmerGlassProfile(r - e, p.y);
  auto gy = filamentBulbDimmerGlassProfile(r, p.y + e) - filamentBulbDimmerGlassProfile(r, p.y - e);
  return normalize(float3(gr * p.x / r, gy, gr * p.z / r));
}

// the socket (0) and the threaded cap (1): x distance, y material
float2 filamentBulbDimmerMetalSdf(float3 p) {
  auto r = length(p.xz);
  auto a = atan2(p.z, p.x);
  auto rs = 0.4f;
  rs += 0.007f * smoothstep(0.09f, 0.1f, p.y) * (1.0f - smoothstep(0.19f, 0.2f, p.y)) * abs(sin(a * 28.0f));
  rs -= 0.014f * (exp(-filamentBulbDimmerSq((p.y - 0.33f) / 0.012f)) + exp(-filamentBulbDimmerSq((p.y - 0.42f) / 0.012f)));
  auto qs = float2(r - (rs - 0.06f), abs(p.y - 0.25f) - 0.19f);
  auto socket = length(max(qs, float2(0.0f))) + min(max(qs.x, qs.y), 0.0f) - 0.06f;
  auto th = 1.0f - abs(fract((p.y - filamentBulbDimmerY_SOCKET) * 9.0f + a / filamentBulbDimmerTAU) * 2.0f - 1.0f);
  auto rc = 0.322f + 0.026f * smoothstep(0.12f, 0.88f, th);
  auto qc = float2(r - rc, abs(p.y - 0.81f) - 0.31f);
  auto cap = length(max(qc, float2(0.0f))) + min(max(qc.x, qc.y), 0.0f);
  if (socket < cap) { return float2(socket, 0.0f); }
  return float2(cap, 1.0f);
}
float3 filamentBulbDimmerMetalNormal(float3 p) {
  auto e = 0.0025f;
  auto k1 = float3(1.0f, -1.0f, -1.0f); auto k2 = float3(-1.0f, -1.0f, 1.0f); auto k3 = float3(-1.0f, 1.0f, -1.0f); auto k4 = float3(1.0f, 1.0f, 1.0f);
  return normalize(k1 * filamentBulbDimmerMetalSdf(p + k1 * e).x + k2 * filamentBulbDimmerMetalSdf(p + k2 * e).x + k3 * filamentBulbDimmerMetalSdf(p + k3 * e).x + k4 * filamentBulbDimmerMetalSdf(p + k4 * e).x);
}

// the closest approach of a ray (o + t d, |d| = 1) to the segment a-b: (distance, t)
float2 filamentBulbDimmerRaySeg(float3 o, float3 dir, float3 a, float3 b) {
  auto ba = b - a; auto oa = o - a;
  auto dd = dot(dir, ba); auto bb = dot(ba, ba);
  auto od = dot(oa, dir); auto ob = dot(oa, ba);
  auto u = clamp((ob - od * dd) / max(bb - dd * dd, 1e-6f), 0.0f, 1.0f);
  auto t = u * dd - od;
  return float2(length(oa + dir * t - ba * u), t);
}
float3 filamentBulbDimmerHookTop(int i) { auto a = float(i) * filamentBulbDimmerTAU / 8.0f; return float3(filamentBulbDimmerR_TOP * cos(a), filamentBulbDimmerY_TOP, filamentBulbDimmerR_TOP * sin(a)); }
float3 filamentBulbDimmerHookBot(int i) { auto a = (float(i) + 0.5f) * filamentBulbDimmerTAU / 8.0f; return float3(filamentBulbDimmerR_BOT * cos(a), filamentBulbDimmerY_BOT, filamentBulbDimmerR_BOT * sin(a)); }
float3 filamentBulbDimmerStrandA(int k) { if (k % 2 == 0) { return filamentBulbDimmerHookTop(k / 2); } return filamentBulbDimmerHookBot(k / 2); }
float3 filamentBulbDimmerStrandB(int k) { if (k % 2 == 0) { return filamentBulbDimmerHookBot(k / 2); } return filamentBulbDimmerHookTop((k / 2 + 1) % 8); }

struct FilamentBulbDimmerInside { float3 col; float hit; };

// what a ray sees between t0 and t1 filamentBulbDimmerFilamentBulbDimmerInside the globe: strands, wires, stem, and the glow round them
FilamentBulbDimmerInside filamentBulbDimmerFilamentBulbDimmerInside(float3 o, float3 dir, float t0, float t1, float E, float3 Lc, float lum) {
  auto tHit = t1;
  auto kind = 0;
  auto glow = 0.0f;
  auto near = 1e5;
  for (int k = 0; k < 16; k++) {
    auto h = filamentBulbDimmerRaySeg(o, dir, filamentBulbDimmerStrandA(k), filamentBulbDimmerStrandB(k));
    if (h.y > t0 && h.y < t1) {
      glow += exp(-h.x / 0.035f);
      if (h.x < filamentBulbDimmerR_FIL && h.y < tHit) { tHit = h.y; kind = 1; near = h.x; }
    }
  }
  for (int i = 0; i < 8; i++) {
    auto a = float(i) * filamentBulbDimmerTAU / 8.0f;
    auto b = (float(i) + 0.5f) * filamentBulbDimmerTAU / 8.0f;
    auto w1 = filamentBulbDimmerRaySeg(o, dir, float3(0.05f * cos(a), filamentBulbDimmerY_TOP - 0.03f, 0.05f * sin(a)), filamentBulbDimmerHookTop(i));
    if (w1.x < filamentBulbDimmerR_WIRE && w1.y > t0 && w1.y < tHit) { tHit = w1.y; kind = 2; }
    auto w2 = filamentBulbDimmerRaySeg(o, dir, float3(0.05f * cos(b), filamentBulbDimmerY_BOT + 0.02f, 0.05f * sin(b)), filamentBulbDimmerHookBot(i));
    if (w2.x < filamentBulbDimmerR_WIRE && w2.y > t0 && w2.y < tHit) { tHit = w2.y; kind = 2; }
  }
  auto col = float3(0.0f);
  if (kind == 1) {
    // the strand's core: white-hot in its middle when hot
    auto core = 1.0f - near / filamentBulbDimmerR_FIL;
    col = mix(Lc, float3(1.0f, 0.96f, 0.9f), 0.45f * F.glow.y * core) * E * (0.55f + 0.45f * core);
  } else if (kind == 2) {
    auto pw = o + dir * tHit;
    auto hot = exp(-abs(pw.y - (filamentBulbDimmerY_TOP + filamentBulbDimmerY_BOT) * 0.5f) * 1.5f);
    col = filamentBulbDimmerLin(float3(0.22f, 0.2f, 0.19f)) * 0.25f + Lc * (0.4f * lum + 0.05f * E) * hot;
  }
  // the glass stem in the middle, from the neck to the bottom ring: bright along its edges
  auto st = filamentBulbDimmerRaySeg(o, dir, float3(0.0f, 1.32f, 0.0f), float3(0.0f, filamentBulbDimmerY_BOT + 0.04f, 0.0f));
  if (st.x < 0.045f && st.y > t0 && st.y < tHit) {
    auto c = sqrt(max(1.0f - filamentBulbDimmerSq(st.x / 0.045f), 0.0f));
    auto fr = 0.04f + 0.96f * pow(1.0f - c, 5.0f);
    col = col * (1.0f - 0.3f * fr) + (Lc * (0.06f * E + 0.6f * lum) + float3(0.2f)) * fr;
  }
  col += Lc * E * glow * 0.012f;
  FilamentBulbDimmerInside r;
  r.col = col;
  r.hit = select(0.0f, 1.0f, kind != 0);
  return r;
}

// the filament filamentBulbDimmerMirrored in the inner face of the glass
float3 filamentBulbDimmerMirrored(float3 o, float3 dir, float E, float3 Lc) {
  auto md = 1e5;
  for (int k = 0; k < 16; k++) {
    auto h = filamentBulbDimmerRaySeg(o, dir, filamentBulbDimmerStrandA(k), filamentBulbDimmerStrandB(k));
    if (h.y > 0.01f) { md = min(md, h.x); }
  }
  return Lc * E * (0.7f * (1.0f - smoothstep(filamentBulbDimmerR_FIL * 0.6f, filamentBulbDimmerR_FIL * 2.0f, md)) + 0.05f * exp(-md / 0.05f));
}

struct FilamentBulbDimmerHit { float3 col; float a; };

FilamentBulbDimmerHit filamentBulbDimmerBulb(float2 p) {
  FilamentBulbDimmerHit out;
  out.col = float3(0.0f);
  out.a = 0.0f;
  auto R = F.anchor.z;
  auto zl = F.anchor.w;
  auto tilt = F.bulb.z;
  auto ct = cos(tilt); auto st = sin(tilt);
  auto axis2 = float2(st, ct);
  auto side2 = float2(ct, -st);
  auto lp = p - F.bulb.xy;
  auto lx = dot(lp, side2) / R;
  auto ly = dot(lp, axis2) / R;
  if (abs(lx) > 1.12f || ly < -0.05f || ly > 3.82f) { return out; }

  // world: x right, y down, z towards the viewer; the filamentBulbDimmerBulb hangs zl in front of the page
  auto cb = cos(F.bulb.w); auto sb = sin(F.bulb.w);
  auto axis = float3(axis2, 0.0f);
  auto side = float3(side2, 0.0f) * cb + float3(0.0f, 0.0f, 1.0f) * sb;
  auto fwd = float3(side2, 0.0f) * -sb + float3(0.0f, 0.0f, 1.0f) * cb;
  auto H = float3(F.bulb.xy, zl);
  auto centre = H + axis * filamentBulbDimmerY_GLOBE * R;
  auto D = 14.0f * R;
  auto row = float3(centre.xy, centre.z + D);
  auto rdw = normalize(float3(p - centre.xy, -D));
  auto toL = row - H;
  auto o = float3(dot(toL, side), dot(toL, axis), dot(toL, fwd)) / R;
  auto dir = normalize(float3(dot(rdw, side), dot(rdw, axis), dot(rdw, fwd)));
  // the bounding cylinder round the axis
  auto qa = dir.x * dir.x + dir.z * dir.z;
  auto qb = 2.0f * (o.x * dir.x + o.z * dir.z);
  auto qc = o.x * o.x + o.z * o.z - 1.1f * 1.1f;
  auto disc = qb * qb - 4.0f * qa * qc;
  if (disc < 0.0f) { return out; }
  auto sd = sqrt(disc);
  auto t = max((-qb - sd) / (2.0f * qa), 0.0f);
  auto tEnd = (-qb + sd) / (2.0f * qa);
  auto mat = -1.0f;
  auto md = 0.0f;
  for (int i = 0; i < 72; i++) {
    auto q = o + dir * t;
    auto m = filamentBulbDimmerMetalSdf(q);
    auto g = filamentBulbDimmerGlassSdf(q);
    auto dist = min(m.x, g);
    if (dist < 0.0012f) { if (m.x < g) { mat = m.y; } else { mat = 2.0f; } md = dist; break; }
    t += max(dist * 0.8f, 0.0015f);
    if (t > tEnd) { break; }
  }
  if (mat < 0.0f) { return out; }

  auto toWorld = float3x3(side, axis, fwd);
  auto q = o + dir * t;
  auto qw = H + toWorld * (q * R);
  auto Lc = F.light.xyz;
  auto lum = F.glow.z;
  auto E = F.glow.w;
  auto fil = float3(0.0f, 2.55f, 0.0f);
  out.a = 1.0f;
  if (mat < 1.5f) {
    // brass: the cap bright and new, the socket aged; filamentBulbDimmerLit by the filament below through the neck
    auto n = filamentBulbDimmerMetalNormal(q);
    auto nw = toWorld * n;
    auto v = -rdw;
    auto F0 = select(filamentBulbDimmerLin(float3(0.62f, 0.46f, 0.3f)), filamentBulbDimmerLin(float3(0.98f, 0.8f, 0.5f)), mat > 0.5f);
    auto fr = F0 + (float3(1.0f) - F0) * pow(1.0f - max(dot(nw, v), 0.0f), 5.0f);
    auto rw = reflect(rdw, nw);
    auto c = filamentBulbDimmerEnv(qw, rw, 1.0f) * fr;
    auto toF = fil - q;
    auto lf = normalize(toF);
    auto att = 1.0f / (1.0f + filamentBulbDimmerSq(length(toF) * 0.9f));
    auto diff = max(dot(n, lf), 0.0f);
    auto spec = pow(max(dot(reflect(-lf, n), normalize(float3(dot(v, side), dot(v, axis), dot(v, fwd)))), 0.0f), 30.0f);
    c += F0 * Lc * (0.03f * E + 0.6f * lum) * diff * att + Lc * (0.04f * E + 0.6f * lum) * spec * att;
    // the threads' grooves stay dark
    c *= 0.72f + 0.28f * smoothstep(0.0f, 0.02f, filamentBulbDimmerMetalSdf(q + n * 0.02f).x);
    out.col = c;
    return out;
  }

  // glass: reflect the room, see in, see the far side
  auto n1 = filamentBulbDimmerGlassNormal(q);
  auto n1w = toWorld * n1;
  auto cos1 = abs(dot(n1w, rdw));
  auto F1 = 0.04f + 0.96f * pow(1.0f - cos1, 5.0f);
  auto refl = filamentBulbDimmerEnv(qw, reflect(rdw, n1w), 3.0f);
  // through the shell into the filamentBulbDimmerBulb, then out through its far wall
  auto t2 = t + filamentBulbDimmerGLASS * 2.0f;
  for (int i = 0; i < 40; i++) {
    auto d2 = -(filamentBulbDimmerGlassSdf(o + dir * t2) + filamentBulbDimmerGLASS);
    if (d2 < 0.001f) { break; }
    t2 += max(d2 * 0.9f, 0.002f);
    if (t2 > tEnd + 1.0f) { break; }
  }
  auto q2 = o + dir * t2;
  auto inn = filamentBulbDimmerFilamentBulbDimmerInside(o, dir, t, t2, E, Lc, lum);
  auto tint = float3(0.985f, 0.965f, 0.93f);
  auto c = inn.col;
  if (inn.hit < 0.5f) {
    auto n2 = filamentBulbDimmerGlassNormal(q2);
    auto F2 = 0.04f + 0.96f * pow(1.0f - abs(dot(n2, dir)), 5.0f);
    auto back = filamentBulbDimmerMirrored(q2, reflect(dir, n2), E, Lc);
    auto q2w = H + toWorld * (q2 * R);
    auto S = q2w.xy + rdw.xy * (q2w.z / -rdw.z);
    auto behind = filamentBulbDimmerPageLit(S) * tint;
    // looking up the neck: the dark cement under the cap
    behind = mix(behind, filamentBulbDimmerLin(float3(0.12f, 0.09f, 0.07f)) * (0.2f + Lc * (0.5f * lum)), smoothstep(filamentBulbDimmerY_NECK + 0.35f, filamentBulbDimmerY_NECK + 0.05f, q2.y));
    c += F2 * back + (1.0f - F2) * behind;
  }
  auto shell = Lc * lum * (0.012f + 0.16f * pow(1.0f - cos1, 3.0f)) + Lc * E * 0.0015f;
  out.col = F1 * refl + (1.0f - F1) * tint * c + shell;
  // the cap's rim where it meets the glass catches the light
  return out;
}


};
vertex FilamentBulbDimmerVOut filamentBulbDimmerFullVs(uint vi [[vertex_id]]) {
 float2 uv=float2(float((vi<<1u)&2u),float(vi&2u));
 return {float4(uv*float2(2,-2)+float2(-1,1),0,1),uv};
}
fragment float4 filamentBulbDimmerSceneFs(FilamentBulbDimmerVOut v [[stage_in]], constant FilamentBulbDimmerUniforms &F [[buffer(0)]]) {
 FilamentBulbDimmerScene s={F}; float2 p=v.pos.xy/F.view.z;
 float3 c=s.filamentBulbDimmerPageLit(p); float R=F.anchor.z, lum=F.glow.z; float3 Lc=F.light.xyz;
 float cd=s.filamentBulbDimmerCordDist(p), px=1/F.view.z;
 c=mix(c,s.filamentBulbDimmerLin(float3(0.36,0.345,0.38))+Lc*lum*0.04,(1-smoothstep(0.62-px,0.62+px,cd))*0.92);
 auto b=s.filamentBulbDimmerBulb(p); c=mix(c,b.col,b.a);
 float h=length(p-s.filamentBulbDimmerFilamentAt());
 c+=Lc*lum*0.022*R/(h+0.32*R)*exp(-h/(7*R)); return float4(c,1);
}
float filamentBulbDimmerSq(float x) { return x*x; }
kernel void filamentBulbDimmerDustCs(uint3 gid [[thread_position_in_grid]], constant FilamentBulbDimmerUniforms &F [[buffer(0)]], constant float4 *hist [[buffer(1)]], device const FilamentBulbDimmerMote *motes [[buffer(2)]], device FilamentBulbDimmerSpark *sparksOut [[buffer(3)]]) {
  auto i = gid.x;
  if (i >= 2600) { return; }
  auto m = motes[i];
  auto t = F.view.w;
  auto R = F.anchor.z;
  auto zl = F.anchor.w;
  auto p = m.home.xyz;
  // a slow wander, whole cycles per loop
  auto n1 = 1.0 + floor(m.seed.z * 2.99);
  auto n2 = 1.0 + floor(m.seed.w * 2.99);
  p.x += 12.0 * sin(6.2831853f * (n1 * t / 10.0 + m.seed.x)) + 5.0 * sin(6.2831853f * (2.0 * t / 10.0 + m.seed.y));
  p.y += 9.0 * sin(6.2831853f * (n2 * t / 10.0 + m.seed.y)) - 4.0 * cos(6.2831853f * (t / 10.0 + m.seed.z));
  p.z += 0.35 * R * sin(6.2831853f * (t / 10.0 + m.seed.w));
  // the air the bulb pushed: its velocity over the last frames, nearer and newer counting more,
  // with a curl round its path
  auto off = float2(0.0);
  for (int k = 0; k < 47; k++) {
    auto c0 = hist[k].xy;
    auto vel = (c0 - hist[k + 1].xy) * 60.0;
    auto rel = p.xy - c0;
    auto g = exp(-dot(rel, rel) / filamentBulbDimmerSq(1.7 * R)) * exp(-filamentBulbDimmerSq((p.z - zl) / (2.4 * R)));
    auto w = exp(-float(k) / 16.0) / 60.0;
    auto curl = sign(rel.x * vel.y - rel.y * vel.x);
    off += (vel + float2(-vel.y, vel.x) * curl * 0.5) * w * g;
  }
  p = float3(p.xy + off * 1.15, p.z);
  // the light it scatters: forward-scattered towards the eye (Henyey-Greenstein, g 0.55)
  auto fil = float3(hist[0].xy, zl);
  auto tp = p - fil;
  auto dist = max(length(tp), 1.0);
  auto ct = tp.z / dist;
  auto gg = 0.55;
  auto hg = (1.0 - gg * gg) / pow(1.0 + gg * gg - 2.0 * gg * ct, 1.5) * (1.0 - gg) * (1.0 - gg) / (1.0 + gg);
  auto bright = F.glow.z * (0.18 + 1.6 * hg) / (1.0 + filamentBulbDimmerSq(dist / (2.3 * R)));
  // none inside the glass
  if (dist < 1.08 * R) { bright = 0.0; }
  auto glint = pow(0.5 + 0.5 * sin(6.2831853f * ((1.0 + floor(m.seed.x * 3.99)) * t / 10.0 + m.seed.w * 7.0)), 24.0);
  auto coc = abs(p.z - zl) / R * 1.5;
  auto size = m.home.w;
  auto radius = size + coc;
  auto energy = filamentBulbDimmerSq(size / radius);
  FilamentBulbDimmerSpark s;
  s.at = float4(p.xy, radius, bright * (1.0 + 2.5 * glint) * energy * F.misc.w);
  s.tint = float4(F.light.xyz, coc);
  sparksOut[i] = s;
}
vertex FilamentBulbDimmerDOut filamentBulbDimmerDustVs(uint vi [[vertex_id]], uint ii [[instance_id]], constant FilamentBulbDimmerUniforms &F [[buffer(0)]], device const FilamentBulbDimmerSpark *sparks [[buffer(1)]]) {
 auto s=sparks[ii]; float2 corner=float2(-1,-1);
 if(vi==1u||vi==4u)corner=float2(1,-1); if(vi==2u||vi==3u)corner=float2(-1,1); if(vi==5u)corner=float2(1,1);
 float r=s.at.z*1.6; if(s.at.w<0.0004)r=0; float2 at=s.at.xy+corner*r;
 return {float4(at.x/F.view.x*2-1,1-at.y/F.view.y*2,0,1),corner*1.6,s.tint.xyz*s.at.w,clamp(s.tint.w/1.5f,0.0f,1.0f)};
}
fragment float4 filamentBulbDimmerDustFs(FilamentBulbDimmerDOut v [[stage_in]]) {
 float r=length(v.uv); float point=exp(-r*r*2.2); float disc=(1-smoothstep(0.82f,1.0f,r))*(0.75+0.25*smoothstep(0.5f,0.95f,r))*0.5;
 return float4(v.col*mix(point,disc,v.soft),0);
}
float3 filamentBulbDimmerDown4(float2 uv,texture2d<float> src,sampler sp) {
 float2 tx=0.5/float2(src.get_width(),src.get_height());
 return (src.sample(sp,uv+float2(-tx.x,-tx.y)).rgb+src.sample(sp,uv+float2(tx.x,-tx.y)).rgb+src.sample(sp,uv+float2(-tx.x,tx.y)).rgb+src.sample(sp,uv+float2(tx.x,tx.y)).rgb)*0.25;
}
fragment float4 filamentBulbDimmerDownFirstFs(FilamentBulbDimmerVOut v [[stage_in]],texture2d<float> src [[texture(0)]],sampler sp [[sampler(0)]]) {
 float3 c=filamentBulbDimmerDown4(v.uv,src,sp); float peak=max(c.r,max(c.g,c.b)); float knee=pow(clamp(peak-0.35f,0.0f,0.7f),2.0f)/1.4;
 return float4(c*max(peak-0.7f,knee)/max(peak,1e-4f),1);
}
fragment float4 filamentBulbDimmerDownFs(FilamentBulbDimmerVOut v [[stage_in]],texture2d<float> src [[texture(0)]],sampler sp [[sampler(0)]]) { return float4(filamentBulbDimmerDown4(v.uv,src,sp),1); }
float3 filamentBulbDimmerTent(float2 uv, texture2d<float> low,sampler sp) {
 float2 tx=1.0/float2(low.get_width(),low.get_height()); float3 c=0;
 for(int y=-1;y<=1;y++)for(int x=-1;x<=1;x++)c+=low.sample(sp,uv+float2(x,y)*tx).rgb*float((2-abs(x))*(2-abs(y)))/16;
 return c;
}
fragment float4 filamentBulbDimmerUpFs(FilamentBulbDimmerVOut v [[stage_in]],texture2d<float> src [[texture(0)]],texture2d<float> low [[texture(1)]],sampler sp [[sampler(0)]]) { return float4(src.sample(sp,v.uv).rgb+filamentBulbDimmerTent(v.uv,low,sp),1); }
fragment float4 filamentBulbDimmerOutFs(FilamentBulbDimmerVOut v [[stage_in]],constant FilamentBulbDimmerUniforms &F [[buffer(0)]],texture2d<float> src [[texture(0)]],texture2d<float> low [[texture(1)]],sampler sp [[sampler(0)]]) {
 float3 c=src.sample(sp,v.uv).rgb+filamentBulbDimmerTent(v.uv,low,sp)*0.11;
 float m=max(c.r,max(c.g,c.b)); if(m>0.75) { float m2=0.75+0.25*(1-exp(-(m-0.75)/0.25)); c*=m2/m; c=mix(c,float3(m2),clamp((m-1)/6,0.0f,1.0f)*0.8); }
 c=max(c,float3(0)); if(F.accent.w>0.5)c=float3(dot(c,float3(0.8225,0.1774,0)),dot(c,float3(0.0332,0.9669,0)),dot(c,float3(0.0171,0.0724,0.9108)));
 float3 o=pow(c,float3(1.0/2.2)); o+=(fract(sin(dot(v.pos.xy,float2(12.9898,78.233)))*43758.5453)-0.5)/255;
 return float4(o,1);
}
struct FilamentBulbDimmerLight { float3 uL,uCol; float4 uCard,uChipL,uChipR,uTab,uRad;









float filamentBulbDimmerSdRR(float2 p, float4 r, float rad) {
  float2 c = r.xy + r.zw*0.5;
  float2 q = abs(p - c) - r.zw*0.5 + rad;
  return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - rad;
}

float3 filamentBulbDimmerLit(float2 p, float4 r, float rad, float albedo) {
  float sd = filamentBulbDimmerSdRR(p, r, rad);
  if (sd > 0.75) return float3(0.0);
  float e = 0.6;
  float2 g = normalize(float2(filamentBulbDimmerSdRR(p + float2(e, 0.0), r, rad) - filamentBulbDimmerSdRR(p - float2(e, 0.0), r, rad),
                              filamentBulbDimmerSdRR(p + float2(0.0, e), r, rad) - filamentBulbDimmerSdRR(p - float2(0.0, e), r, rad)) + 1e-5);
  float cov = clamp(0.5 - sd, 0.0, 1.0);
  float t = clamp(-sd/6.0, 0.0, 1.0);
  float3 n = normalize(float3(g*(1.0 - t)*1.6, 0.25 + t));
  float3 Lv = float3(uL.xy - p, uL.z);
  float d2 = dot(Lv, Lv);
  float3 l = Lv*rsqrt(d2);
  float att = uL.z*uL.z/d2;
  float diff = max(dot(n, l), 0.0)*att;
  float spec = pow(max(dot(reflect(-l, n), float3(0.0, 0.0, 1.0)), 0.0), 18.0)*sqrt(att)*(1.0 - t);
  // the faces take a little, the bevels facing the bulb most
  return uCol*(albedo*diff*mix(1.0, 0.2, t) + 0.6*spec)*cov;
}

half4 filamentBulbDimmerLightAt(float2 p) {
  float3 c = filamentBulbDimmerLit(p, uCard, uRad.x, 0.32) + filamentBulbDimmerLit(p, uChipL, uRad.y, 0.4) + filamentBulbDimmerLit(p, uChipR, uRad.y, 0.4) + filamentBulbDimmerLit(p, uTab, uRad.z, 0.25);
  // a pixel of zero alpha is dropped when the canvas is composited: give the light a little coverage
  float a = clamp(max(c.r, max(c.g, c.b))*0.3, 0.0, 1.0);
  return half4(half3(c), half(a));
}

};
[[stitchable]] half4 filamentBulbDimmerLight(float2 p,half4 color,float4 light,float4 col,float4 card,float4 chipL,float4 chipR,float4 tab) {
 FilamentBulbDimmerLight l={light.xyz,col.xyz,card,chipL,chipR,tab,float4(24,16,32,0)};
 return l.filamentBulbDimmerLightAt(p);
}
