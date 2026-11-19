#include <metal_stdlib>
using namespace metal;

// Deferred shading of a product photo's G-buffer (rendered in Blender). The whole frame is surface: the watch stands on
// a seamless paper sweep, so its passes blend into the paper's and it has no cut-out edge.
//   albedo   base colour, sRGB (the paper is white; the preset tints it)
//   gbuf     R, G = view-space normal x, y (0.5 + 0.5 n); B = depth, piecewise (0.1 .. 0.7 spans the watch)
//   mat      R = roughness, G = metallic, B = ambient occlusion
//   matte    the watch's antialiased matte
//   before   the same shot under flat light (left of the before | after divider)
// Positions are in photo widths from the centre (x right, y up, z toward the viewer), orthographic view. One softbox
// key, a hair light from behind, an ambient shaped by occlusion, and the paper reflected in the metal. A port of the
// React Native block's SkSL; Skia's eval(pixel) becomes a normalized sample here.

#define uSize   u[0]
#define uLight  float3(u[1], u[2], u[3])
#define uKey    float3(u[4], u[5], u[6])
#define uRim    float3(u[7], u[8], u[9])
#define uAmb    float3(u[10], u[11], u[12])
#define uPaper  float3(u[13], u[14], u[15])
#define uSoft   u[16]
#define uSplit  u[17]
#define uDecode u[18]

constant float PRS_PRODUCT = 0.7089;
constant float PRS_NEAR_A = -0.8027;
constant float PRS_FAR_A = 3.6341;
constant float2 PRS_SB = float2(0.12, 0.17);
constant float2 PRS_MATTE_LO = float2(0.2900, 0.1514);
constant float2 PRS_MATTE_HI = float2(0.7061, 0.7158);

// SwiftUI takes few texture arguments, so the five passes come as one 3 x 2 atlas of 1024 px tiles:
// albedo, gbuf, mat on the top row; matte, before below.
#define PRS_ALBEDO 0
#define PRS_GBUF 1
#define PRS_MAT 2
#define PRS_MATTE 3
#define PRS_BEFORE 4

struct ProductRelightStudioTex {
  texture2d<half> atlas;
  bool dec;
};

static float3 prsEncode(float3 c);

/// A texel as Skia's eval would read it (the stored, sRGB-encoded value).
static float3 prsSample(thread const ProductRelightStudioTex &tx, int tile, float2 uv) {
  constexpr sampler s(coord::normalized, address::clamp_to_edge, filter::linear);
  float2 c = clamp(uv, float2(0.5 / 1024.0), float2(1.0 - 0.5 / 1024.0));
  float2 a = (float2(float(tile % 3), float(tile / 3)) + c) / float2(3.0, 2.0);
  float3 v = float3(tx.atlas.sample(s, a).rgb);
  return tx.dec ? prsEncode(v) : v;
}

static bool prsInMatte(float2 uv, float pad) {
  return uv.x > PRS_MATTE_LO.x - pad && uv.x < PRS_MATTE_HI.x + pad && uv.y > PRS_MATTE_LO.y - pad && uv.y < PRS_MATTE_HI.y + pad;
}

static float3 prsLin(float3 c) { return pow(max(c, float3(0.0)), float3(2.2)); }

static float prsZOf(float dn) {
  float r = dn < 0.1 ? PRS_NEAR_A * (1.0 - dn / 0.1)
          : (dn < 0.7 ? (dn - 0.1) / 0.6 * PRS_PRODUCT : PRS_PRODUCT + (dn - 0.7) / 0.3 * (PRS_FAR_A - PRS_PRODUCT));
  return -(r - PRS_PRODUCT * 0.5);
}

static float3 prsNormalOf(float2 rg) {
  float2 nxy = rg * 2.0 - 1.0;
  return float3(nxy, sqrt(max(0.0, 1.0 - dot(nxy, nxy))));
}

/// The watch's shadow on itself: twelve taps toward the light through the depth buffer.
static float prsOcclusion(float3 P, float stepLen, float spread, float3 light, thread const ProductRelightStudioTex &tx) {
  float3 Ld = normalize(light - P);
  float occ = 0.0;
  for (int i = 1; i <= 12; i++) {
    float s = float(i) * stepLen;
    float3 Q = P + Ld * s;
    float2 qu = float2(Q.x + 0.5, 0.5 - Q.y);
    if (prsInMatte(qu, 0.0) && prsSample(tx, PRS_MATTE, qu).g > 0.5) {
      float h = prsZOf(prsSample(tx, PRS_GBUF, qu).b) - Q.z;
      occ = max(occ, smoothstep(0.0, 0.004 + spread * s, h) * (1.0 - smoothstep(0.22, 0.36, h)));
    }
  }
  return 1.0 - occ;
}

/// The watch's soft shadow on the paper, read from the matte over a disc that grows with the distance.
static float prsPaperShadow(float3 P, float3 light, float soft, thread const ProductRelightStudioTex &tx) {
  float3 Lv = light - P;
  float dl = length(Lv);
  float3 Ld = Lv / dl;
  if (abs(Ld.z) < 0.02) return 1.0;
  float s = -P.z / Ld.z;
  if (s <= 0.0 || s >= dl) return 1.0;
  float3 Q = P + Ld * s;
  float2 q = float2(Q.x + 0.5, 0.5 - Q.y);
  float r = min(0.003 + soft * 0.1 * s, 0.09);
  if (!prsInMatte(q, r)) return 1.0;
  float c = prsSample(tx, PRS_MATTE, q).g * 0.2;
  for (int i = 0; i < 8; i++) {
    float a = float(i) * 0.785398 + 0.39;
    float2 o = float2(cos(a), sin(a));
    c += prsSample(tx, PRS_MATTE, q + o * r).g * 0.06 + prsSample(tx, PRS_MATTE, q + o * (r * 0.45)).g * 0.04;
  }
  return 1.0 - 0.82 / (1.0 + 3.0 * s * s) * smoothstep(0.0, 1.0, c);
}

static float3 prsEncode(float3 c) {
  return pow(clamp(c, 0.0, 1.0), float3(1.0 / 2.2));
}

[[ stitchable ]] half4 productRelightStudioShade(float2 xy, half4 color, device const float *u, int n,
                                                  texture2d<half> atlas) {
  // textures that the system decoded to linear light are encoded back first, so the maths sees what Skia saw
  bool dec = uDecode > 0.5;
  ProductRelightStudioTex tx = { atlas, dec };
  float size = uSize;
  float2 uv = xy / size;
  if (xy.x < uSplit * size) return half4(half3(prsSample(tx, PRS_BEFORE, uv)), 1.0);
  float3 light = uLight;
  float3 keyCol = uKey;
  float3 rimCol = uRim;
  float3 ambCol = uAmb;
  float3 paper = uPaper;
  float soft = uSoft;

  float3 g = prsSample(tx, PRS_GBUF, uv);
  float mask = prsSample(tx, PRS_MATTE, uv).g;
  float3 nrm = prsNormalOf(g.rg);
  float3 P = float3(uv.x - 0.5, 0.5 - uv.y, prsZOf(g.b));
  float3 paperC = prsLin(paper);
  float3 alb = prsLin(prsSample(tx, PRS_ALBEDO, uv)) * mix(paperC, float3(1.0), mask);
  float3 m = prsSample(tx, PRS_MAT, uv);
  float rough = clamp(mix(0.85, m.r, mask), 0.06, 1.0);
  float metal = m.g * mask;
  float ao = m.b;

  float keyL = max(max(keyCol.r, keyCol.g), keyCol.b);
  float3 key = prsLin(keyCol / max(keyL, 1e-3)) * keyL;
  float3 V = float3(0.0, 0.0, 1.0);
  float3 Lv = light - P;
  float d2 = dot(Lv, Lv);
  float3 L = Lv * rsqrt(d2);
  float3 H = normalize(L + V);
  float NdL = max(dot(nrm, L), 0.0);
  float NdV = max(nrm.z, 0.02);
  float NdH = max(dot(nrm, H), 0.0);
  float a2 = rough * rough * rough * rough;
  float den = NdH * NdH * (a2 - 1.0) + 1.0;
  float D = a2 / (3.14159 * den * den);
  float k = (rough + 1.0) * (rough + 1.0) / 8.0;
  float Gs = NdL / (NdL * (1.0 - k) + k) * NdV / (NdV * (1.0 - k) + k);
  float3 F0 = mix(float3(0.04), alb, metal);
  float3 F = F0 + (1.0 - F0) * pow(1.0 - max(dot(H, V), 0.0), 5.0);
  float3 spec = D * Gs * F / max(4.0 * NdL * NdV, 0.02);
  float3 kd = (1.0 - F) * (1.0 - metal);
  float atten = mix(1.6 / (1.0 + d2 * 1.1), 1.6 / (1.0 + d2 * 2.5), mask);
  float sh = NdL > 0.0 ? (mask > 0.5 ? prsOcclusion(P + nrm * 0.006, 0.014, 0.22 * soft, light, tx) : prsPaperShadow(P, light, soft, tx)) : 0.0;
  float3 direct = (kd * alb / 3.14159 + spec * mask) * key * NdL * atten * sh;

  float3 paperLit = paperC * (key * 0.16 + prsLin(ambCol) * 3.0);

  float2 rimDir = normalize(float2(-0.45 * clamp(light.x * 3.0, -1.0, 1.0), 1.0));
  float rimL = max(max(rimCol.r, rimCol.g), rimCol.b);
  float edge = pow(1.0 - NdV, 2.6) * smoothstep(-0.2, 0.8, dot(normalize(nrm.xy + float2(1e-4, 0.0)), rimDir));
  float3 rim = prsLin(rimCol / max(rimL, 1e-3)) * rimL * edge * mix(0.5, 1.0, metal) * mix(alb, float3(1.0), 0.5) * ao * mask;

  float3 R = 2.0 * nrm.z * nrm - V;
  float3 env = paperLit * mix(0.6, 1.15, clamp(R.y * 0.5 + 0.5, 0.0, 1.0));
  float lobe = pow(max(dot(R, L), 0.0), 2.0 / (rough * rough + 0.01));
  env += key * lobe * 0.25 * atten * sh;
  float3 nL = normalize(-light);
  float den2 = dot(R, nL);
  float box = 0.0;
  if (den2 < -0.05) {
    float sHit = dot(light - P, nL) / den2;
    if (sHit > 0.0) {
      float3 Hp = P + R * sHit - light;
      float3 bu = normalize(cross(float3(0.0, 1.0, 0.0), nL));
      float3 bv = cross(nL, bu);
      float2 q = abs(float2(dot(Hp, bu), dot(Hp, bv)));
      float blur = 0.004 + rough * rough * sHit * 1.6;
      box = (1.0 - smoothstep(PRS_SB.x - blur, PRS_SB.x + blur, q.x)) * (1.0 - smoothstep(PRS_SB.y - blur, PRS_SB.y + blur, q.y));
      box *= 1.0 / (1.0 + rough * rough * 40.0);
    }
  }
  env += key * box * 0.55 * sh;
  float3 Fv = F0 + (1.0 - F0) * pow(1.0 - NdV, 5.0) * (1.0 - rough);
  float3 refl = env * Fv * mix(ao, 1.0, 0.3) * mask;

  float3 amb = alb * (1.0 - metal) * prsLin(ambCol) * 3.0 * ao;
  float3 col = direct + rim + refl + amb;

  // ACES fit, then sRGB
  float3 c = clamp((col * (2.51 * col + 0.03)) / (col * (2.43 * col + 0.59) + 0.14), 0.0, 1.0);
  return half4(half3(prsEncode(c)), 1.0);
}
