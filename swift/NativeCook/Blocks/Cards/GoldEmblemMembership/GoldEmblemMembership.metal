#include <metal_stdlib>
using namespace metal;

// Direct translation of gem-wgsl.ts. One transparent, premultiplied full-screen pass.
struct GoldEmblemMembershipUniforms { float4 view, card, tier, light; };
struct GoldEmblemMembershipMaterial { float3 base, hi, medal, ribA, ribB; };
struct GoldEmblemMembershipCast { float heat, melt, newMetal, stamp; };
struct GoldEmblemMembershipShader {
  GoldEmblemMembershipUniforms F;


float goldEmblemMembershipSq(float v) { return v * v; }
float3 goldEmblemMembershipLin(float3 c) { return pow(c, float3(2.2)); }
float goldEmblemMembershipHash2(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float goldEmblemMembershipNoise2(float2 x) {
  const auto i = floor(x); const auto f = fract(x); const auto u = f * f * (3.0 - 2.0 * f);
  return mix(mix(goldEmblemMembershipHash2(i), goldEmblemMembershipHash2(i + float2(1.0, 0.0)), u.x), mix(goldEmblemMembershipHash2(i + float2(0.0, 1.0)), goldEmblemMembershipHash2(i + float2(1.0, 1.0)), u.x), u.y);
}
float goldEmblemMembershipEase(float x) { const auto c = clamp(x, 0.0, 1.0); return c * c * (3.0 - 2.0 * c); }
float goldEmblemMembershipSdRound(float2 p, float2 b, float r) { const auto q = abs(p) - b + r; return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r; }
float goldEmblemMembershipSmin(float a, float b, float k) { const auto h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0); return mix(b, a, h) - k * h * (1.0 - h); }

/* ------------------------------------------------------------------ the metals: base, highlight, the medal's face, the ribbon's two colours */


GoldEmblemMembershipMaterial goldEmblemMembershipMetal(float i) {
  GoldEmblemMembershipMaterial m;
  if (i < 0.5) {
    m.base = goldEmblemMembershipLin(float3(0.82, 0.69, 0.47)); m.hi = goldEmblemMembershipLin(float3(0.98, 0.9, 0.72)); m.medal = goldEmblemMembershipLin(float3(0.93, 0.84, 0.66));
    m.ribA = goldEmblemMembershipLin(float3(0.98, 0.66, 0.16)); m.ribB = goldEmblemMembershipLin(float3(0.93, 0.33, 0.2));
  } else if (i < 1.5) {
    m.base = goldEmblemMembershipLin(float3(0.74, 0.79, 0.81)); m.hi = goldEmblemMembershipLin(float3(0.96, 0.98, 0.99)); m.medal = goldEmblemMembershipLin(float3(0.88, 0.91, 0.93));
    m.ribA = goldEmblemMembershipLin(float3(0.36, 0.62, 0.7)); m.ribB = goldEmblemMembershipLin(float3(0.85, 0.88, 0.9));
  } else {
    m.base = goldEmblemMembershipLin(float3(0.17, 0.17, 0.19)); m.hi = goldEmblemMembershipLin(float3(0.46, 0.46, 0.5)); m.medal = goldEmblemMembershipLin(float3(0.86, 0.7, 0.42));
    m.ribA = goldEmblemMembershipLin(float3(0.12, 0.12, 0.13)); m.ribB = goldEmblemMembershipLin(float3(0.88, 0.72, 0.42));
  }
  return m;
}

/* ------------------------------------------------------------------ the cast's state at a point of the card (u: 0 … 1 across) */


GoldEmblemMembershipCast goldEmblemMembershipCastAt(float u) {
  GoldEmblemMembershipCast c;
  const auto k = F.tier.z;
  // across from the swiped side: 0 at the side the heat comes from
  auto x = u;
  if (F.tier.w > 0.0) { x = 1.0 - u; }
  // the heat front runs across in the first third; the cooling front runs back in the last
  const auto hot = goldEmblemMembershipEase((k * 3.2 - x * 0.9) / 0.35);
  const auto cool = goldEmblemMembershipEase(((k - 0.58) * 3.0 - (1.0 - x) * 0.8) / 0.3);
  c.heat = hot * (1.0 - cool);
  c.melt = c.heat;
  c.newMetal = goldEmblemMembershipEase((k - 0.42) / 0.14);
  if (k <= 0.0) { c.heat = 0.0; c.melt = 0.0; c.newMetal = 0.0; }
  // the new medal is pressed in once the plate has cooled
  const auto s = clamp((k - 0.84) / 0.16, 0.0, 1.0);
  c.stamp = (k > 0.0 ? s * s * (3.0 - 2.0 * s) * (1.0 + 0.25 * sin(s * 3.14159265)) : 1.0);
  return c;
}

/* ------------------------------------------------------------------ the plate's height, in card points */

float goldEmblemMembershipCrown(float2 q) {
  // a little crown: a band and three points
  const auto band = goldEmblemMembershipSdRound(q - float2(0.0, 4.0), float2(9.0, 2.2), 0.8);
  auto pts = 1e9;
  for (auto i = -1; i <= 1; i++) {
    const auto x = float(i) * 7.5;
    const auto pq = q - float2(x, -1.5);
    // a triangle pointing up
    const auto tri = max(abs(pq.x) * 0.9 + pq.y * 0.45 - 2.8, -pq.y - 4.5);
    pts = min(pts, tri);
  }
  return min(band, pts);
}

float goldEmblemMembershipHeight(float2 p, GoldEmblemMembershipCast c) {
  const auto W = F.card.z; const auto H = F.card.w;
  const auto ctr = float2(W, H) * 0.5;
  const auto sd = goldEmblemMembershipSdRound(p - ctr, ctr, 10.0);
  // the bevel
  auto h = clamp(-sd / 3.0, 0.0, 1.0) * 0.8;
  // brushed grain along the diagonal, and two broad swells at the lower right, as in the reference
  const auto g = float2(dot(p, float2(0.94, 0.34)), dot(p, float2(-0.34, 0.94)));
  h += (goldEmblemMembershipNoise2(float2(g.x * 0.04, g.y * 1.6)) - 0.5) * 0.06;
  h += 0.35 * exp(-goldEmblemMembershipSq((length(p - float2(W * 0.95, H * 1.35)) - H * 0.95) / 22.0));
  h += 0.25 * exp(-goldEmblemMembershipSq((length(p - float2(W * 0.62, H * 1.6)) - H * 0.95) / 26.0));
  // the medal: a raised disc with a rim and a crown, pressed in by the stamp
  const auto mp = p - float2(313.0, 62.0) * float2(W / 367.0, H / 185.0);
  const auto mr = length(mp);
  const auto disc = (1.0 - smoothstep(22.0, 24.5, mr)) * 1.6 + exp(-goldEmblemMembershipSq((mr - 19.0) / 1.3)) * 0.7 - (1.0 - smoothstep(16.0, 17.5, mr)) * 0.5;
  const auto cr = (1.0 - smoothstep(-0.6, 0.6, goldEmblemMembershipCrown(mp * 1.15))) * 0.7;
  h += (disc + cr) * c.stamp;
  // molten: ripples running from the swiped side, a slow churn, all fading as it cools
  const auto t = F.view.w;
  auto xs = p.x;
  if (F.tier.w > 0.0) { xs = W - p.x; }
  const auto rip = sin(xs * 0.11 - t * 9.0) * exp(-xs / W) * 0.6 + sin(length(p - float2(W * 0.4, H * 0.5)) * 0.18 - t * 6.0) * 0.35;
  // a slow churn of smooth swells (value noise would show its grid in the liquid's highlights)
  const auto churn = sin(p.x * 0.045 + t * 1.1) * cos(p.y * 0.06 - t * 0.8) * 0.9 + sin((p.x + p.y * 1.3) * 0.08 + t * 1.6) * 0.45 + sin((p.x * 1.7 - p.y) * 0.05 - t * 0.9) * 0.4;
  h = mix(h, 0.9 + rip * 0.8 + churn, c.melt);
  return h;
}

/* ------------------------------------------------------------------ the shape: a rounded plate that sags and drips while molten */

float goldEmblemMembershipShapeD(float2 p) {
  const auto W = F.card.z; const auto H = F.card.w;
  const auto ctr = float2(W, H) * 0.5;
  const auto u = clamp(p.x / W, 0.0, 1.0);
  const auto c = goldEmblemMembershipCastAt(u);
  // the lower edge sags under the heat, and drips grow from it
  const auto sag = c.melt * 5.0 * sin(u * 3.14159265);
  auto dd = goldEmblemMembershipSdRound(p - ctr - float2(0.0, sag * 0.5), ctr + float2(0.0, sag * 0.5), 10.0);
  const auto k = F.tier.z;
  for (auto i = 0; i < 6; i++) {
    const auto fi = float(i);
    const auto dx = (0.12 + 0.15 * fi + 0.04 * sin(fi * 7.3)) * W;
    const auto ci = goldEmblemMembershipCastAt(dx / W);
    const auto len = ci.melt * (6.0 + 12.0 * fract(sin(fi * 12.7) * 437.5)) * (1.0 + 0.5 * sin(k * 3.14159265));
    if (len > 0.3) {
      const auto a = float2(dx, H - 2.0 + sag);
      const auto b = a + float2(0.0, len);
      const auto pa = p - a; const auto ba = b - a;
      const auto hh = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
      const auto r = 3.2 + 1.2 * hh;
      dd = goldEmblemMembershipSmin(dd, length(pa - ba * hh) - r, 5.0);
    }
  }
  return dd;
}

/* ------------------------------------------------------------------ shading */

float3 goldEmblemMembershipStudio(float3 r) {
  // a warm room: a soft box overhead swept by the drag, a window to the left, a darker floor
  const auto sw = F.light.x;
  auto c = mix(goldEmblemMembershipLin(float3(0.22, 0.2, 0.2)), goldEmblemMembershipLin(float3(0.78, 0.76, 0.74)), smoothstep(-0.4, 0.7, r.y));
  c += float3(1.0) * 1.6 * smoothstep(0.45, 0.8, r.y) * (1.0 - smoothstep(0.25, 0.6, abs(r.x - 0.25 * sw)));
  c += float3(1.0, 0.97, 0.92) * 0.9 * smoothstep(0.6, 0.9, -r.x) * smoothstep(-0.2, 0.3, r.y);
  return c;
}
// the colour of something at a temperature 0 … 1 (dull red, orange, yellow-white)
float3 goldEmblemMembershipGlow(float k) {
  return goldEmblemMembershipLin(float3(1.0, 0.25, 0.05)) * smoothstep(0.0, 0.4, k) + goldEmblemMembershipLin(float3(1.0, 0.62, 0.2)) * smoothstep(0.3, 0.75, k) * 1.4 + float3(1.0, 0.92, 0.75) * smoothstep(0.7, 1.0, k) * 1.6;
}

float3 goldEmblemMembershipEncode(float3 c) { return pow(clamp(c / (float3(1.0) + max(c - float3(0.85), float3(0.0)) * 0.6), float3(0.0), float3(1.0)), float3(1.0 / 2.2)); }

float4 goldEmblemMembershipCardFs(float4 pos) {
  const auto s = pos.xy / F.view.z;
  const auto p = s - F.card.xy;
  const auto W = F.card.z; const auto H = F.card.w;
  const auto dd = goldEmblemMembershipShapeD(p);
  // a soft shadow under the card on the dark page
  if (dd > 0.0) {
    const auto sh = exp(-goldEmblemMembershipSq(max(goldEmblemMembershipSdRound(p - float2(W * 0.5, H * 0.5 + 8.0), float2(W * 0.5 - 6.0, H * 0.5), 14.0), 0.0) / 10.0)) * 0.35;
    const auto a = sh * (1.0 - smoothstep(0.0, 0.8, -dd + 0.8));
    return float4(0.0, 0.0, 0.0, a);
  }
  const auto u = clamp(p.x / W, 0.0, 1.0);
  const auto c = goldEmblemMembershipCastAt(u);
  // the surface's slope from the height field
  const auto e = 0.75;
  const auto h0 = goldEmblemMembershipHeight(p, c);
  const auto hx = goldEmblemMembershipHeight(p + float2(e, 0.0), c) - h0;
  const auto hy = goldEmblemMembershipHeight(p + float2(0.0, e), c) - h0;
  const auto n = normalize(float3(-hx / e * 2.2, hy / e * 2.2, 1.0));
  const auto m0 = goldEmblemMembershipMetal(F.tier.x);
  const auto m1 = goldEmblemMembershipMetal(F.tier.y);
  const auto base = mix(m0.base, m1.base, c.newMetal);
  const auto hi = mix(m0.hi, m1.hi, c.newMetal);
  const auto r = reflect(float3(0.0, 0.0, -1.0), n);
  const auto env = goldEmblemMembershipStudio(r);
  // brushed metal: a broad diffuse sheen in its own colour, the room's light in its highlight colour
  // brushed metal: its colour in the room's light, the room's bright parts in its highlight colour, and a
  // soft streak across the grain that follows the drag
  const auto g2 = float2(dot(p, float2(0.94, 0.34)), dot(p, float2(-0.34, 0.94)));
  const auto streak = exp(-goldEmblemMembershipSq((g2.x / W - 0.35 - 0.25 * F.light.x) / 0.22)) * (0.85 + 0.15 * goldEmblemMembershipNoise2(float2(g2.x * 0.02, g2.y * 0.9)));
  auto col = base * (0.62 + 0.38 * n.z) * (0.8 + 0.2 * env) + hi * (env * 0.18 + float3(streak * 0.35));
  // the gentle diagonal tone of the reference: lighter at the top left
  col *= 1.06 - 0.16 * (p.x / W * 0.6 + p.y / H * 0.4);
  // the medal: its own face colour, and the ribbon above it
  const auto mp = p - float2(313.0, 62.0) * float2(W / 367.0, H / 185.0);
  const auto mr = length(mp);
  const auto medal = mix(m0.medal, m1.medal, c.newMetal);
  const auto onDisc = (1.0 - smoothstep(22.0, 24.0, mr)) * c.stamp;
  col = mix(col, medal * (0.55 + 0.55 * n.z) + hi * pow(max(env.r - 0.6, 0.0), 1.0) * 0.8, onDisc * 0.85);
  const auto rq = mp - float2(0.0, -36.0);
  const auto ribbon = (1.0 - smoothstep(-0.5, 0.5, max(abs(rq.x) - 24.0 + rq.y * 0.25, abs(rq.y) - 11.0))) * c.stamp;
  if (ribbon > 0.0 && mr > 21.5) {
    const auto stripe = fract((rq.x + 24.0) / 16.0);
    const auto ra = mix(m0.ribA, m1.ribA, c.newMetal);
    const auto rb = mix(m0.ribB, m1.ribB, c.newMetal);
    auto rc = mix(ra, rb, step(0.33, stripe) * step(stripe, 0.66));
    rc *= 0.8 + 0.3 * (1.0 - (rq.y + 11.0) / 22.0);
    col = mix(col, rc, ribbon);
  }
  // molten: the metal's own glow, white-hot where the heat is strongest, liquid highlights on it
  if (c.heat > 0.0) {
    // liquid metal: the glow dimmer where the surface turns from the eye, the room bright in its ripples
    const auto k = c.heat * (0.7 + 0.1 * sin(p.x * 0.05 + F.view.w * 1.3) * cos(p.y * 0.07 - F.view.w));
    const auto molten = goldEmblemMembershipGlow(k) * (0.45 + 0.55 * n.z) + pow(env, float3(1.5)) * 0.55 * goldEmblemMembershipLin(float3(1.0, 0.85, 0.7));
    col = mix(col, molten, smoothstep(0.0, 0.5, c.heat));
  }
  const auto a = 1.0 - smoothstep(-0.8, 0.0, dd);
  return float4(goldEmblemMembershipEncode(col) * a, a);
}

};
vertex float4 goldEmblemMembershipVertex(uint vi [[vertex_id]]) {
  float2 uv = float2(float((vi << 1u) & 2u), float(vi & 2u));
  return float4(uv * float2(2.0, -2.0) + float2(-1.0, 1.0), 0.0, 1.0);
}
fragment float4 goldEmblemMembershipFragment(float4 position [[position]], constant GoldEmblemMembershipUniforms &frame [[buffer(0)]]) {
  GoldEmblemMembershipShader shader; shader.F = frame;
  return shader.goldEmblemMembershipCardFs(position);
}
