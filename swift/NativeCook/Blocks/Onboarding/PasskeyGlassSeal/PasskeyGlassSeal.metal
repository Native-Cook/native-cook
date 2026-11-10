#include <metal_stdlib>
using namespace metal;
struct PasskeyGlassSealUniforms { float4 view; float4 print; float4 glow; float4 key; };
struct PasskeyGlassSealPoint { float4 ab; float4 m; };
struct PasskeyGlassSealVOut { float4 pos [[position]]; float2 uv; };
struct PasskeyGlassSealTOut { float4 pos [[position]]; float2 uv; float3 col; float a; };
struct PasskeyGlassSealPx { float3 col; float a; };
struct PasskeyGlassSealShader {
    PasskeyGlassSealUniforms F;
const float PI = 3.14159265;
float passkeyGlassSealSq(float v) { return v * v; }
float3 passkeyGlassSealLin(float3 c) { return pow(c, float3(2.2)); }
float passkeyGlassSealEase(float x) { const auto c = clamp(x, 0.0, 1.0); return c * c * (3.0 - 2.0 * c); }
float2x2 passkeyGlassSealRot(float a) { const auto c = cos(a); const auto s = sin(a); return float2x2(c, s, -s, c); }

/* ------------------------------------------------------------------ the key (pt, y down, from the print's centre) */

float passkeyGlassSealSdBox(float2 p, float2 b) { const auto q = abs(p) - b; return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0); }
float passkeyGlassSealSdKey2(float2 p) {
  const auto ring = abs(length(p - float2(-50.0, 0.0)) - 17.5) - 6.5;
  auto dd = min(ring, passkeyGlassSealSdBox(p - float2(20.0, 0.0), float2(46.0, 6.5)));
  dd = min(dd, passkeyGlassSealSdBox(p - float2(58.0, 11.0), float2(4.0, 5.0)));
  dd = min(dd, passkeyGlassSealSdBox(p - float2(44.0, 9.5), float2(4.0, 3.5)));
  dd = min(dd, passkeyGlassSealSdBox(p - float2(30.5, 12.0), float2(3.5, 6.0)));
  return dd;
}
float passkeyGlassSealSdKey(float3 p) {
  const auto d2 = passkeyGlassSealSdKey2(p.xy) + 1.6;
  const auto w = float2(d2, abs(p.z) - 2.4);
  return min(max(w.x, w.y), 0.0) + length(max(w, float2(0.0))) - 1.6;
}
// the key in the card's frame: tilted for depth, turned about its shaft (its x axis) by the lock
float3 passkeyGlassSealKeyLocal(float3 p) {
  const auto turn = F.key.z * PI * 0.3;
  auto q = p;
  const auto xz = passkeyGlassSealRot(0.32) * q.xz;
  q = float3(xz.x, q.y, xz.y);
  const auto yz = passkeyGlassSealRot(-turn) * q.yz;
  q = float3(q.x, yz.x, yz.y);
  return q;
}
float passkeyGlassSealKeyD(float3 p) { return passkeyGlassSealSdKey(passkeyGlassSealKeyLocal(p)); }
float3 passkeyGlassSealKeyN(float3 p) {
  const auto e = 0.08;
  const auto k1 = float3(1.0, -1.0, -1.0); const auto k2 = float3(-1.0, -1.0, 1.0); const auto k3 = float3(-1.0, 1.0, -1.0); const auto k4 = float3(1.0, 1.0, 1.0);
  return normalize(k1 * passkeyGlassSealKeyD(p + k1 * e) + k2 * passkeyGlassSealKeyD(p + k2 * e) + k3 * passkeyGlassSealKeyD(p + k3 * e) + k4 * passkeyGlassSealKeyD(p + k4 * e));
}
float3 passkeyGlassSealStudio(float3 r) {
  // a warm room: the page's rose below, a wide soft box overhead, a hot rim light from the right
  auto c = mix(passkeyGlassSealLin(float3(0.36, 0.2, 0.32)), passkeyGlassSealLin(float3(0.95, 0.72, 0.8)), smoothstep(-0.6, 0.7, -r.y));
  c += float3(1.0, 0.95, 0.9) * 2.6 * smoothstep(0.55, 0.8, -r.y) * smoothstep(-0.9, -0.2, r.z) * (1.0 - smoothstep(0.5, 0.9, abs(r.x)));
  c += float3(1.0, 0.62, 0.42) * 2.0 * smoothstep(0.78, 0.95, dot(r, normalize(float3(0.85, 0.1, 0.5))));
  return c;
}



PasskeyGlassSealPx passkeyGlassSealKeyPx(float2 s) {
  PasskeyGlassSealPx o;
  o.col = float3(0.0);
  o.a = 0.0;
  const auto g = F.key.y;
  if (g <= 0.0) { return o; }
  const auto sc = F.print.z;
  const auto p2 = (s - F.print.xy) / sc;
  if (abs(p2.x) > 85.0 || abs(p2.y) > 40.0) { return o; }
  const auto ro = float3(p2, 30.0);
  const auto rd = float3(0.0, 0.0, -1.0);
  auto t = 0.0;
  auto hit = false;
  for (auto i = 0; i < 64; i++) {
    const auto dd = passkeyGlassSealKeyD(ro + rd * t);
    if (dd < 0.02) { hit = true; break; }
    t += dd * 0.85;
    if (t > 60.0) { break; }
  }
  if (!hit) { return o; }
  const auto p = ro + rd * t;
  const auto n = passkeyGlassSealKeyN(p);
  const auto v = float3(n.x, -n.y, n.z); // y up for the room
  const auto cosv = clamp(n.z, 0.0, 1.0);
  const auto fr = 0.05 + 0.95 * pow(1.0 - cosv, 4.0);
  const auto refl = passkeyGlassSealStudio(reflect(float3(0.0, 0.0, -1.0), float3(v.x, v.y, v.z)));
  // rose-gold glass: the body tinted where it is thick, the room seen in its faces
  const auto body = passkeyGlassSealLin(float3(1.0, 0.7, 0.6)) * (0.16 + 0.22 * cosv);
  auto c = body * (1.0 - fr) + refl * fr * 1.3;
  // a streak of the soft box across its faces
  c += float3(1.0, 0.96, 0.92) * 1.4 * pow(max(dot(reflect(normalize(float3(0.3, -0.8, -0.5)), float3(v.x, v.y, v.z)), float3(0.0, 0.0, 1.0)), 0.0), 24.0);
  c += passkeyGlassSealLin(float3(1.0, 0.85, 0.75)) * pow(1.0 - cosv, 2.0) * 0.5;
  o.col = c;
  o.a = g * (0.82 + 0.18 * fr);
  return o;
}

float3 passkeyGlassSealEncode(float3 c) { return pow(clamp(c / (float3(1.0) + max(c - float3(0.85), float3(0.0)) * 0.6), float3(0.0), float3(1.0)), float3(1.0 / 2.2)); }

float4 passkeyGlassSealCardFs(PasskeyGlassSealVOut v) {
  const auto s = v.pos.xy / F.view.z;
  auto col = float3(0.0);
  auto a = 0.0;
  // the scan line: a hot core, a soft orange glow, fading at its ends
  const auto sw = F.glow.y;
  if (sw > 0.0) {
    const auto dy = s.y - F.print.w;
    const auto ends = smoothstep(18.0, 40.0, s.x) * (1.0 - smoothstep(F.view.x - 40.0, F.view.x - 18.0, s.x));
    const auto core = exp(-passkeyGlassSealSq(dy / 1.3)) * ends;
    const auto glow = exp(-passkeyGlassSealSq(dy / 4.0)) * ends * 0.6;
    const auto c = passkeyGlassSealLin(float3(1.0, 0.5, 0.3)) * glow + passkeyGlassSealLin(float3(1.0, 0.66, 0.46)) * core * 1.2;
    const auto k = (glow + core) * sw;
    col += c * sw;
    a += clamp(k, 0.0, 1.0) * 0.9;
  }
  // a warm bloom round the lit print, and round the key
  const auto lit = F.glow.x * (1.0 - F.key.x * 0.6);
  if (lit > 0.0) {
    const auto q = (s - F.print.xy - float2(0.0, -2.0) * F.print.z) / (F.print.z * 70.0);
    const auto b = exp(-dot(q, q) * 1.6) * lit * 0.3;
    col += passkeyGlassSealLin(float3(1.0, 0.6, 0.45)) * b;
    a += b * 0.6;
  }
  const auto k = passkeyGlassSealKeyPx(s);
  col = k.col * k.a + col * (1.0 - k.a);
  a = k.a + a * (1.0 - k.a);
  if (a <= 0.0001) { return float4(0.0); }
  return float4(passkeyGlassSealEncode(col / a) * a, a);
}

PasskeyGlassSealTOut passkeyGlassSealThreadVs(uint vi, uint ii, device const PasskeyGlassSealPoint *pts) {
  const auto p = pts[ii];
  const auto sc = F.print.z;
  // the outer ridges come off first; each swirls round the core on its way
  const auto stagger = (1.0 - p.m.x) * 0.45 + p.m.z * 0.03;
  const auto e = passkeyGlassSealEase((F.key.x - stagger) / 0.47);
  const auto ab = mix(p.ab.xy, p.ab.zw, e);
  const auto swirl = passkeyGlassSealRot((1.0 - e) * e * 4.0 * (0.9 + 0.5 * p.m.x)) * (ab - float2(0.0, -2.0)) + float2(0.0, -2.0);
  const auto at = F.print.xy + swirl * sc;
  // the glow runs out from the core along the ridges; a hot front, then a warm settled light
  const auto front = F.glow.x * 1.25;
  const auto dd = p.m.y;
  const auto lit = clamp((front - dd) / 0.18, 0.0, 1.0);
  const auto flash = exp(-passkeyGlassSealSq((front - dd) / 0.06)) * step(0.001, F.glow.x) * (1.0 - step(1.2, front));
  auto c = mix(float3(1.0), float3(1.0, 0.86, 0.72), lit) * (0.75 + 0.5 * lit) + float3(1.0, 0.55, 0.32) * flash * 1.6;
  // in flight, a warm thread; on the key, its bright edge, fading as the glass fills it
  c = mix(c, float3(1.0, 0.8, 0.66) * 1.3, (1.0 - e) * e * 4.0);
  auto a = (0.62 + 0.38 * lit) * (1.0 - F.key.y);
  auto r = (1.05 + 0.5 * flash + 0.25 * (1.0 - e) * e * 4.0) * sc;
  auto corner = float2(-1.0, -1.0);
  if (vi == 1u || vi == 4u) { corner = float2(1.0, -1.0); }
  if (vi == 2u || vi == 3u) { corner = float2(-1.0, 1.0); }
  if (vi == 5u) { corner = float2(1.0, 1.0); }
  const auto q = at + corner * (r + 0.8);
  PasskeyGlassSealTOut o;
  o.pos = float4(q.x / F.view.x * 2.0 - 1.0, 1.0 - q.y / F.view.y * 2.0, 0.0, 1.0);
  o.uv = corner * (r + 0.8) / r;
  o.col = c;
  o.a = a;
  return o;
}

float4 passkeyGlassSealThreadFs(PasskeyGlassSealTOut v) {
  const auto r = length(v.uv);
  const auto a = (1.0 - smoothstep(0.55, 1.05, r)) * v.a * 0.55;
  return float4(passkeyGlassSealEncode(passkeyGlassSealLin(min(v.col, float3(1.6)))) * a, a);
}

};
vertex PasskeyGlassSealVOut passkeyGlassSealFullVs(uint vi [[vertex_id]]) {
    float2 uv = float2(float((vi << 1u) & 2u), float(vi & 2u));
    return {float4(uv*float2(2,-2)+float2(-1,1),0,1),uv};
}
fragment float4 passkeyGlassSealCardFs(PasskeyGlassSealVOut v [[stage_in]], constant PasskeyGlassSealUniforms &F [[buffer(0)]], texture2d<float> printMask [[texture(0)]]) {
    if (F.glow.z > 0.5) {
        constexpr sampler smp(coord::normalized,address::clamp_to_edge,filter::linear);
        float2 at = v.pos.xy/F.view.z;
        float alpha = printMask.sample(smp,at/F.view.xy).r*(0.62+0.38*F.glow.x);
        float4 col = float4(float3(alpha),alpha);
        float2 q = min(at-float2(20.0,F.print.w),float2(F.view.x-20.0,F.print.w+2.0)-at);
        float line = clamp(min(q.x,q.y)*F.view.z+0.5,0.0,1.0)*F.glow.y;
        return float4(float3(1.0,141.0/255.0,92.0/255.0)*line+col.rgb*(1.0-line),line+col.a*(1.0-line));
    }
    PasskeyGlassSealShader s{F}; return s.passkeyGlassSealCardFs(v);
}
vertex PasskeyGlassSealTOut passkeyGlassSealThreadVs(uint vi [[vertex_id]], uint ii [[instance_id]], constant PasskeyGlassSealUniforms &F [[buffer(0)]], device const PasskeyGlassSealPoint *pts [[buffer(1)]]) {
    PasskeyGlassSealShader s{F}; return s.passkeyGlassSealThreadVs(vi,ii,pts);
}
fragment float4 passkeyGlassSealThreadFs(PasskeyGlassSealTOut v [[stage_in]]) {
    PasskeyGlassSealShader s; return s.passkeyGlassSealThreadFs(v);
}
