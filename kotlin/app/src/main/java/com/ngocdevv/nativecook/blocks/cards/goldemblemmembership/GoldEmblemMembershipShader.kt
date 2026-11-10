package com.ngocdevv.nativecook.blocks.cards.goldemblemmembership

// The WebGPU height field, translated to AGSL. No image or compute input is used by this block.
internal object GoldEmblemMembershipShader {
    val source = """
uniform float4 uView;
uniform float4 uCard;
uniform float4 uTier;
uniform float4 uLight;
const float PI = 3.14159265;
const float TAU = 6.2831853;
float sq(float v) { return v * v; }
float3 lin(float3 c) { return pow(c, float3(2.2)); }
float hash2(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float noise2(float2 x) {
  float2 i = floor(x); float2 f = fract(x); float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash2(i), hash2(i + float2(1.0, 0.0)), u.x), mix(hash2(i + float2(0.0, 1.0)), hash2(i + float2(1.0, 1.0)), u.x), u.y);
}
float ease(float x) { float c = clamp(x, 0.0, 1.0); return c * c * (3.0 - 2.0 * c); }
float sdRound(float2 p, float2 b, float r) { float2 q = abs(p) - b + r; return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r; }
float smin(float a, float b, float k) { float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0); return mix(b, a, h) - k * h * (1.0 - h); }

/* ------------------------------------------------------------------ the metals: base, highlight, the medal's face, the ribbon's two colours */

struct Metal { float3 base; float3 hi; float3 medal; float3 ribA; float3 ribB; };
Metal metal(float i) {
  Metal m;
  if (i < 0.5) {
    m.base = lin(float3(0.82, 0.69, 0.47)); m.hi = lin(float3(0.98, 0.9, 0.72)); m.medal = lin(float3(0.93, 0.84, 0.66));
    m.ribA = lin(float3(0.98, 0.66, 0.16)); m.ribB = lin(float3(0.93, 0.33, 0.2));
  } else if (i < 1.5) {
    m.base = lin(float3(0.74, 0.79, 0.81)); m.hi = lin(float3(0.96, 0.98, 0.99)); m.medal = lin(float3(0.88, 0.91, 0.93));
    m.ribA = lin(float3(0.36, 0.62, 0.7)); m.ribB = lin(float3(0.85, 0.88, 0.9));
  } else {
    m.base = lin(float3(0.17, 0.17, 0.19)); m.hi = lin(float3(0.46, 0.46, 0.5)); m.medal = lin(float3(0.86, 0.7, 0.42));
    m.ribA = lin(float3(0.12, 0.12, 0.13)); m.ribB = lin(float3(0.88, 0.72, 0.42));
  }
  return m;
}

/* ------------------------------------------------------------------ the cast's state at a point of the card (u: 0 … 1 across) */

struct Cast { float heat; float melt; float newMetal; float stamp; };
Cast castAt(float u) {
  Cast c;
  float k = uTier.z;
  // across from the swiped side: 0 at the side the heat comes from
  float x = u;
  if (uTier.w > 0.0) { x = 1.0 - u; }
  // the heat front runs across in the first third; the cooling front runs back in the last
  float hot = ease((k * 3.2 - x * 0.9) / 0.35);
  float cool = ease(((k - 0.58) * 3.0 - (1.0 - x) * 0.8) / 0.3);
  c.heat = hot * (1.0 - cool);
  c.melt = c.heat;
  c.newMetal = ease((k - 0.42) / 0.14);
  if (k <= 0.0) { c.heat = 0.0; c.melt = 0.0; c.newMetal = 0.0; }
  // the new medal is pressed in once the plate has cooled
  float s = clamp((k - 0.84) / 0.16, 0.0, 1.0);
  c.stamp = (k > 0.0 ? s * s * (3.0 - 2.0 * s) * (1.0 + 0.25 * sin(s * PI)) : 1.0);
  return c;
}

/* ------------------------------------------------------------------ the plate's height, in card points */

float crown(float2 q) {
  // a little crown: a band and three points
  float band = sdRound(q - float2(0.0, 4.0), float2(9.0, 2.2), 0.8);
  float pts = 1e9;
  for (int i = -1; i <= 1; i++) {
    float x = float(i) * 7.5;
    float2 pq = q - float2(x, -1.5);
    // a triangle pointing up
    float tri = max(abs(pq.x) * 0.9 + pq.y * 0.45 - 2.8, -pq.y - 4.5);
    pts = min(pts, tri);
  }
  return min(band, pts);
}

float height(float2 p, Cast c) {
  float W = uCard.z; float H = uCard.w;
  float2 ctr = float2(W, H) * 0.5;
  float sd = sdRound(p - ctr, ctr, 10.0);
  // the bevel
  float h = clamp(-sd / 3.0, 0.0, 1.0) * 0.8;
  // brushed grain along the diagonal, and two broad swells at the lower right, as in the reference
  float2 g = float2(dot(p, float2(0.94, 0.34)), dot(p, float2(-0.34, 0.94)));
  h += (noise2(float2(g.x * 0.04, g.y * 1.6)) - 0.5) * 0.06;
  h += 0.35 * exp(-sq((length(p - float2(W * 0.95, H * 1.35)) - H * 0.95) / 22.0));
  h += 0.25 * exp(-sq((length(p - float2(W * 0.62, H * 1.6)) - H * 0.95) / 26.0));
  // the medal: a raised disc with a rim and a crown, pressed in by the stamp
  float2 mp = p - float2(313.0, 62.0) * float2(W / 367.0, H / 185.0);
  float mr = length(mp);
  float disc = (1.0 - smoothstep(22.0, 24.5, mr)) * 1.6 + exp(-sq((mr - 19.0) / 1.3)) * 0.7 - (1.0 - smoothstep(16.0, 17.5, mr)) * 0.5;
  float cr = (1.0 - smoothstep(-0.6, 0.6, crown(mp * 1.15))) * 0.7;
  h += (disc + cr) * c.stamp;
  // molten: ripples running from the swiped side, a slow churn, all fading as it cools
  float t = uView.w;
  float xs = p.x;
  if (uTier.w > 0.0) { xs = W - p.x; }
  float rip = sin(xs * 0.11 - t * 9.0) * exp(-xs / W) * 0.6 + sin(length(p - float2(W * 0.4, H * 0.5)) * 0.18 - t * 6.0) * 0.35;
  // a slow churn of smooth swells (value noise would show its grid in the liquid's highlights)
  float churn = sin(p.x * 0.045 + t * 1.1) * cos(p.y * 0.06 - t * 0.8) * 0.9 + sin((p.x + p.y * 1.3) * 0.08 + t * 1.6) * 0.45 + sin((p.x * 1.7 - p.y) * 0.05 - t * 0.9) * 0.4;
  h = mix(h, 0.9 + rip * 0.8 + churn, c.melt);
  return h;
}

/* ------------------------------------------------------------------ the shape: a rounded plate that sags and drips while molten */

float shapeD(float2 p) {
  float W = uCard.z; float H = uCard.w;
  float2 ctr = float2(W, H) * 0.5;
  float u = clamp(p.x / W, 0.0, 1.0);
  Cast c = castAt(u);
  // the lower edge sags under the heat, and drips grow from it
  float sag = c.melt * 5.0 * sin(u * PI);
  float dd = sdRound(p - ctr - float2(0.0, sag * 0.5), ctr + float2(0.0, sag * 0.5), 10.0);
  float k = uTier.z;
  for (int i = 0; i < 6; i++) {
    float fi = float(i);
    float dx = (0.12 + 0.15 * fi + 0.04 * sin(fi * 7.3)) * W;
    Cast ci = castAt(dx / W);
    float len = ci.melt * (6.0 + 12.0 * fract(sin(fi * 12.7) * 437.5)) * (1.0 + 0.5 * sin(k * PI));
    if (len > 0.3) {
      float2 a = float2(dx, H - 2.0 + sag);
      float2 b = a + float2(0.0, len);
      float2 pa = p - a; float2 ba = b - a;
      float hh = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
      float r = 3.2 + 1.2 * hh;
      dd = smin(dd, length(pa - ba * hh) - r, 5.0);
    }
  }
  return dd;
}

/* ------------------------------------------------------------------ shading */

float3 studio(float3 r) {
  // a warm room: a soft box overhead swept by the drag, a window to the left, a darker floor
  float sw = uLight.x;
  float3 c = mix(lin(float3(0.22, 0.2, 0.2)), lin(float3(0.78, 0.76, 0.74)), smoothstep(-0.4, 0.7, r.y));
  c += float3(1.0) * 1.6 * smoothstep(0.45, 0.8, r.y) * (1.0 - smoothstep(0.25, 0.6, abs(r.x - 0.25 * sw)));
  c += float3(1.0, 0.97, 0.92) * 0.9 * smoothstep(0.6, 0.9, -r.x) * smoothstep(-0.2, 0.3, r.y);
  return c;
}
// the colour of something at a temperature 0 … 1 (dull red, orange, yellow-white)
float3 glow(float k) {
  return lin(float3(1.0, 0.25, 0.05)) * smoothstep(0.0, 0.4, k) + lin(float3(1.0, 0.62, 0.2)) * smoothstep(0.3, 0.75, k) * 1.4 + float3(1.0, 0.92, 0.75) * smoothstep(0.7, 1.0, k) * 1.6;
}

float3 encode(float3 c) { return pow(clamp(c / (float3(1.0) + max(c - float3(0.85), float3(0.0)) * 0.6), float3(0.0), float3(1.0)), float3(1.0 / 2.2)); }

float4 main(float2 xy) {
  float2 s = xy;
  float2 p = s - uCard.xy;
  float W = uCard.z; float H = uCard.w;
  float dd = shapeD(p);
  // a soft shadow under the card on the dark page
  if (dd > 0.0) {
    float sh = exp(-sq(max(sdRound(p - float2(W * 0.5, H * 0.5 + 8.0), float2(W * 0.5 - 6.0, H * 0.5), 14.0), 0.0) / 10.0)) * 0.35;
    float a = sh * (1.0 - smoothstep(0.0, 0.8, -dd + 0.8));
    return float4(0.0, 0.0, 0.0, a);
  }
  float u = clamp(p.x / W, 0.0, 1.0);
  Cast c = castAt(u);
  // the surface's slope from the height field
  float e = 0.75;
  float h0 = height(p, c);
  float hx = height(p + float2(e, 0.0), c) - h0;
  float hy = height(p + float2(0.0, e), c) - h0;
  float3 n = normalize(float3(-hx / e * 2.2, hy / e * 2.2, 1.0));
  Metal m0 = metal(uTier.x);
  Metal m1 = metal(uTier.y);
  float3 base = mix(m0.base, m1.base, c.newMetal);
  float3 hi = mix(m0.hi, m1.hi, c.newMetal);
  float3 r = reflect(float3(0.0, 0.0, -1.0), n);
  float3 env = studio(r);
  // brushed metal: a broad diffuse sheen in its own colour, the room's light in its highlight colour
  // brushed metal: its colour in the room's light, the room's bright parts in its highlight colour, and a
  // soft streak across the grain that follows the drag
  float2 g2 = float2(dot(p, float2(0.94, 0.34)), dot(p, float2(-0.34, 0.94)));
  float streak = exp(-sq((g2.x / W - 0.35 - 0.25 * uLight.x) / 0.22)) * (0.85 + 0.15 * noise2(float2(g2.x * 0.02, g2.y * 0.9)));
  float3 col = base * (0.62 + 0.38 * n.z) * (0.8 + 0.2 * env) + hi * (env * 0.18 + float3(streak * 0.35));
  // the gentle diagonal tone of the reference: lighter at the top left
  col *= 1.06 - 0.16 * (p.x / W * 0.6 + p.y / H * 0.4);
  // the medal: its own face colour, and the ribbon above it
  float2 mp = p - float2(313.0, 62.0) * float2(W / 367.0, H / 185.0);
  float mr = length(mp);
  float3 medal = mix(m0.medal, m1.medal, c.newMetal);
  float onDisc = (1.0 - smoothstep(22.0, 24.0, mr)) * c.stamp;
  col = mix(col, medal * (0.55 + 0.55 * n.z) + hi * pow(max(env.r - 0.6, 0.0), 1.0) * 0.8, onDisc * 0.85);
  float2 rq = mp - float2(0.0, -36.0);
  float ribbon = (1.0 - smoothstep(-0.5, 0.5, max(abs(rq.x) - 24.0 + rq.y * 0.25, abs(rq.y) - 11.0))) * c.stamp;
  if (ribbon > 0.0 && mr > 21.5) {
    float stripe = fract((rq.x + 24.0) / 16.0);
    float3 ra = mix(m0.ribA, m1.ribA, c.newMetal);
    float3 rb = mix(m0.ribB, m1.ribB, c.newMetal);
    float3 rc = mix(ra, rb, step(0.33, stripe) * step(stripe, 0.66));
    rc *= 0.8 + 0.3 * (1.0 - (rq.y + 11.0) / 22.0);
    col = mix(col, rc, ribbon);
  }
  // molten: the metal's own glow, white-hot where the heat is strongest, liquid highlights on it
  if (c.heat > 0.0) {
    // liquid metal: the glow dimmer where the surface turns from the eye, the room bright in its ripples
    float k = c.heat * (0.7 + 0.1 * sin(p.x * 0.05 + uView.w * 1.3) * cos(p.y * 0.07 - uView.w));
    float3 molten = glow(k) * (0.45 + 0.55 * n.z) + pow(env, float3(1.5)) * 0.55 * lin(float3(1.0, 0.85, 0.7));
    col = mix(col, molten, smoothstep(0.0, 0.5, c.heat));
  }
  float a = 1.0 - smoothstep(-0.8, 0.0, dd);
  return float4(encode(col) * a, a);
}

""".trimIndent()
}
