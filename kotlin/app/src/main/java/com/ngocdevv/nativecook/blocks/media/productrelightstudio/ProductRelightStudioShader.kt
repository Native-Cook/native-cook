package com.ngocdevv.nativecook.blocks.media.productrelightstudio

// Deferred shading of the product photo's G-buffer (rendered in Blender), the React Native block's SkSL
// (relightShader.ts) as AGSL. The whole frame is surface: the watch stands on a seamless paper sweep.
//   albedo   base colour, sRGB (the paper is white; the preset tints it)
//   gbuf     R, G = view-space normal x, y (0.5 + 0.5 n); B = depth, piecewise (0.1 .. 0.7 spans the watch)
//   mat      R = roughness, G = metallic, B = ambient occlusion (on the paper: the watch's contact shadow)
//   matte    the watch's antialiased matte
//   before   the same shot under flat light (left of the before | after divider)
// Android writes a runtime shader's output as sRGB, so the iOS-only Display P3 branch (`wide`) is left out. Every
// input is a plain sRGB bitmap with float3 uniforms (no layout(color)), so the data passes reach the maths unchanged.
internal const val RELIGHT_AGSL = """
uniform shader albedo;
uniform shader gbuf;
uniform shader mat;
uniform shader matte;
uniform shader before;

uniform float size;      // the photo's side in pixels
uniform float3 light;    // the key's position
uniform float3 keyCol;   // colour x power
uniform float3 rimCol;   // colour x power
uniform float3 ambCol;
uniform float3 paper;    // the sweep's colour (sRGB)
uniform float soft;      // shadow softness
uniform float split;     // before | after divider, 0..1 across the photo (0 = all after)

// depth decoding (from the Blender camera, in photo widths from the watch's nearest point)
const float PRODUCT = 0.7089;   // the watch's depth range
const float NEAR_A = -0.8027;   // the nearest paper
const float FAR_A = 3.6341;     // the farthest paper
const float2 SB = float2(0.12, 0.17);  // the softbox's half size (photo widths)
// the matte's bounding box (photo fractions, y down), padded by two texels for the linear filter: outside it the
// matte reads 0, so taps there can be skipped without changing a pixel
const float2 MATTE_LO = float2(0.2900, 0.1514);
const float2 MATTE_HI = float2(0.7061, 0.7158);

bool inMatte(float2 uv, float pad) {
  return uv.x > MATTE_LO.x - pad && uv.x < MATTE_HI.x + pad && uv.y > MATTE_LO.y - pad && uv.y < MATTE_HI.y + pad;
}

float3 lin(float3 c) { return pow(max(c, float3(0.0)), float3(2.2)); }

float zOf(float dn) {
  float r = dn < 0.1 ? NEAR_A * (1.0 - dn / 0.1)
          : (dn < 0.7 ? (dn - 0.1) / 0.6 * PRODUCT : PRODUCT + (dn - 0.7) / 0.3 * (FAR_A - PRODUCT));
  return -(r - PRODUCT * 0.5);
}

float3 normalOf(float2 rg) {
  float2 nxy = rg * 2.0 - 1.0;
  return float3(nxy, sqrt(max(0.0, 1.0 - dot(nxy, nxy))));
}

/** The watch's shadow on itself: twelve taps toward the light through the depth buffer. */
float occlusion(float3 P, float stepLen, float spread) {
  float3 Ld = normalize(light - P);
  float occ = 0.0;
  for (int i = 1; i <= 12; i++) {
    float s = float(i) * stepLen;
    float3 Q = P + Ld * s;
    float2 qu = float2(Q.x + 0.5, 0.5 - Q.y);
    float2 q = qu * size;
    if (inMatte(qu, 0.0) && matte.eval(q).g > 0.5) {
      float h = zOf(gbuf.eval(q).b) - Q.z;
      // the watch is in front of the ray (but not so far that the ray passes behind it): blocked, softer with distance
      occ = max(occ, smoothstep(0.0, 0.004 + spread * s, h) * (1.0 - smoothstep(0.22, 0.36, h)));
    }
  }
  return 1.0 - occ;
}

/**
 * The watch's soft shadow on the paper: the ray toward the light meets the watch's plane (z = 0, it is thin), and the
 * matte is read there over a disc that grows with the distance, so the shadow is sharp where the watch stands and soft
 * further off.
 */
float paperShadow(float3 P) {
  float3 Lv = light - P;
  float dl = length(Lv);
  float3 Ld = Lv / dl;
  if (abs(Ld.z) < 0.02) return 1.0;
  float s = -P.z / Ld.z;
  if (s <= 0.0 || s >= dl) return 1.0;
  float3 Q = P + Ld * s;
  float2 q = float2(Q.x + 0.5, 0.5 - Q.y);
  float r = min(0.003 + soft * 0.1 * s, 0.09);
  // the ray meets the watch's plane away from the watch: every tap below would read 0 (most of the paper)
  if (!inMatte(q, r)) return 1.0;
  float c = matte.eval(q * size).g * 0.2;
  for (int i = 0; i < 8; i++) {
    float a = float(i) * 0.785398 + 0.39;
    float2 o = float2(cos(a), sin(a));
    c += matte.eval((q + o * r) * size).g * 0.06 + matte.eval((q + o * (r * 0.45)) * size).g * 0.04;
  }
  // the further the paper is from the watch, the fainter its shadow (the softbox wraps round it)
  return 1.0 - 0.82 / (1.0 + 3.0 * s * s) * smoothstep(0.0, 1.0, c);
}

float3 encode(float3 c) {
  c = clamp(c, 0.0, 1.0);
  return pow(c, float3(1.0 / 2.2));
}

half4 main(float2 xy) {
  // left of the before | after divider only the original shows: skip the relighting there
  if (xy.x < split * size) {
    float3 b = before.eval(xy).rgb;
    return half4(half3(b), 1.0);
  }
  float2 uv = xy / size;
  float3 g = gbuf.eval(xy).rgb;
  float mask = matte.eval(xy).g;
  float3 n = normalOf(g.rg);
  float3 P = float3(uv.x - 0.5, 0.5 - uv.y, zOf(g.b));
  float3 paperC = lin(paper);
  float3 alb = lin(albedo.eval(xy).rgb) * mix(paperC, float3(1.0), mask);
  float3 m = mat.eval(xy).rgb;
  float rough = clamp(mix(0.85, m.r, mask), 0.06, 1.0);
  float metal = m.g * mask;
  float ao = m.b;

  float keyL = max(max(keyCol.r, keyCol.g), keyCol.b);
  float3 key = lin(keyCol / max(keyL, 1e-3)) * keyL;
  float3 V = float3(0.0, 0.0, 1.0);
  float3 Lv = light - P;
  float d2 = dot(Lv, Lv);
  float3 L = Lv * inversesqrt(d2);
  float3 H = normalize(L + V);
  float NdL = max(dot(n, L), 0.0);
  float NdV = max(n.z, 0.02);
  float NdH = max(dot(n, H), 0.0);
  float a2 = rough * rough * rough * rough;
  float den = NdH * NdH * (a2 - 1.0) + 1.0;
  float D = a2 / (3.14159 * den * den);
  float k = (rough + 1.0) * (rough + 1.0) / 8.0;
  float Gs = NdL / (NdL * (1.0 - k) + k) * NdV / (NdV * (1.0 - k) + k);
  float3 F0 = mix(float3(0.04), alb, metal);
  float3 F = F0 + (1.0 - F0) * pow(1.0 - max(dot(H, V), 0.0), 5.0);
  float3 spec = D * Gs * F / max(4.0 * NdL * NdV, 0.02);
  float3 kd = (1.0 - F) * (1.0 - metal);
  // the paper falls off more gently than the small watch (a big softbox lights a sweep evenly)
  float atten = mix(1.6 / (1.0 + d2 * 1.1), 1.6 / (1.0 + d2 * 2.5), mask);
  // the watch shades itself by a march through the depth buffer; the paper takes its soft projected shadow
  float sh = NdL > 0.0 ? (mask > 0.5 ? occlusion(P + n * 0.006, 0.014, 0.22 * soft) : paperShadow(P)) : 0.0;
  float3 direct = (kd * alb / 3.14159 + spec * mask) * key * NdL * atten * sh;

  // the paper as the watch sees it (what its metal reflects): lit by the key and the ambient
  float3 paperLit = paperC * (key * 0.16 + lin(ambCol) * 3.0);

  // hair light: from behind and above, a little to the side away from the key, the same on every preset
  float2 rimDir = normalize(float2(-0.45 * clamp(light.x * 3.0, -1.0, 1.0), 1.0));
  float rimL = max(max(rimCol.r, rimCol.g), rimCol.b);
  float edge = pow(1.0 - NdV, 2.6) * smoothstep(-0.2, 0.8, dot(normalize(n.xy + float2(1e-4, 0.0)), rimDir));
  float3 rim = lin(rimCol / max(rimL, 1e-3)) * rimL * edge * mix(0.5, 1.0, metal) * mix(alb, float3(1.0), 0.5) * ao * mask;

  // reflections: the paper all round and the key's softbox in it, blurred by roughness
  float3 R = 2.0 * n.z * n - V;
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
      box = (1.0 - smoothstep(SB.x - blur, SB.x + blur, q.x)) * (1.0 - smoothstep(SB.y - blur, SB.y + blur, q.y));
      box *= 1.0 / (1.0 + rough * rough * 40.0);
    }
  }
  env += key * box * 0.55 * sh;
  float3 Fv = F0 + (1.0 - F0) * pow(1.0 - NdV, 5.0) * (1.0 - rough);
  float3 refl = env * Fv * mix(ao, 1.0, 0.3) * mask;

  float3 amb = alb * (1.0 - metal) * lin(ambCol) * 3.0 * ao;
  float3 col = direct + rim + refl + amb;

  // ACES fit, then sRGB (or Display P3)
  float3 c = clamp((col * (2.51 * col + 0.03)) / (col * (2.43 * col + 0.59) + 0.14), 0.0, 1.0);
  return half4(half3(encode(c)), 1.0);
}
"""
