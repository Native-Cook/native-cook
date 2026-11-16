package com.ngocdevv.nativecook.blocks.cards.coinpilecollect

// AGSL ports of cpc-shaders.ts (API 33+). AGSL does not reliably support dynamically indexed uniform arrays, so the 32 coins are
// 64 separate float4 uniforms (coinA0..31 = x, y, z, glow + 2 * face; coinB0..31 = ax, ay, phi, alpha) and both coin loops are
// unrolled into calls of one helper each. Output is sRGB on Android (uP3 stays 0).

internal const val CpcEMBOSS_CELL = 192

private const val CpcCommon = """
uniform shader emb;
uniform float uCell;
uniform float uP3;
uniform float uPx;
uniform float uR;
uniform float uH;
uniform float uK;
uniform float3 uAlbA;
uniform float3 uAlbB;
uniform float3 uAlbRim;
uniform float3 uEnvKey;
uniform float3 uEnvBounce;
uniform float3 uEnvRoom;

const float PI = 3.14159265;
const float3 LIGHT = float3(-0.5238, -0.5926, 0.6104);
const float3 CYAN = float3(0.25, 0.91, 1.0);

half3 toP3(half3 c) {
  half3 lin = pow(c, half3(2.2, 2.2, 2.2));
  half3 p = half3(0.8225 * lin.r + 0.1774 * lin.g, 0.0332 * lin.r + 0.9669 * lin.g, 0.0171 * lin.r + 0.0724 * lin.g + 0.9108 * lin.b);
  return pow(max(p, half3(0.0, 0.0, 0.0)), half3(0.4545, 0.4545, 0.4545));
}
half4 finish(float3 c, float alpha) {
  half3 o = half3(clamp(c, float3(0.0), float3(1.0)));
  if (uP3 > 0.5) { o = toP3(o); }
  return half4(o * half(alpha), half(alpha));
}
float sm(float a, float b, float x) { float k = clamp((x - a) / (b - a), 0.0, 1.0); return k * k * (3.0 - 2.0 * k); }
float dsm(float a, float b, float x) { float k = clamp((x - a) / (b - a), 0.0, 1.0); return 6.0 * k * (1.0 - k) / (b - a); }
float hash21(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float bayer(float2 p) { return fract(dot(floor(p * 3.0), float2(0.7548776662, 0.5698402910))); }

float3 envAt(float3 r) {
  float3 room = mix(uEnvRoom, uEnvRoom * 3.2, sm(-0.7, 0.9, r.z));
  float kb = sm(0.50, 0.93, dot(r, normalize(float3(-0.5, -0.62, 0.6))));
  float3 key = uEnvKey * kb * 0.95;
  float bb = sm(0.15, 0.85, dot(r, normalize(float3(0.62, 0.5, 0.45))));
  float3 blue = uEnvBounce * bb * 0.55;
  float rim = sm(0.2, 0.9, dot(r, normalize(float3(0.1, -0.9, 0.3)))) * 0.25;
  return room + key + blue + (uEnvKey * 0.5 + uEnvBounce * 0.3) * rim;
}

float meshH(float2 q) {
  float2 rr = float2(1.0, 1.7320508);
  float2 hh = rr * 0.5;
  float2 a = mod(q, rr) - hh;
  float2 b = mod(q - hh, rr) - hh;
  float2 g = dot(a, a) < dot(b, b) ? a : b;
  float e = 0.5 - max(dot(abs(g), normalize(float2(1.0, 1.7320508))), abs(g.x));
  return sm(0.04, 0.17, e);
}

float embH(float2 l, float cellIdx, float ext) {
  if (abs(l.x) > ext || abs(l.y) > ext) { return 0.0; }
  float2 px = float2((l.x / ext * 0.5 + 0.5) * uCell + cellIdx * uCell, (l.y / ext * 0.5 + 0.5) * uCell);
  return emb.eval(px).a;
}

float4 capSurface(float2 l, float rho, float meshFace, float cellIdx, float emblemHalf) {
  float R = uR;
  float dep = 0.55 * uK;
  float cham = 1.15 * uK;
  float dh = dep * dsm(0.80, 0.862, rho) - cham * dsm(0.925, 1.0, rho);
  float slope = dh / R;
  float rr = max(length(l), 1e-4);
  float gx = slope * l.x / rr;
  float gy = slope * l.y / rr;
  float tweak = 0.0;
  if (rho < 0.80) {
    if (meshFace > 0.5) {
      float k = 21.0 / (2.0 * R);
      float2 q = l * k;
      float h0 = meshH(q);
      float e = 0.05;
      float hx = meshH(q + float2(e, 0.0)) - meshH(q - float2(e, 0.0));
      float hy = meshH(q + float2(0.0, e)) - meshH(q - float2(0.0, e));
      float amp = 0.5 * k / (2.0 * e);
      gx += hx * amp * 0.55;
      gy += hy * amp * 0.55;
      tweak = h0 - 0.55;
    } else {
      float e = 1.1;
      float stepPt = (2.0 * emblemHalf) / uCell;
      float c0 = embH(l, cellIdx, emblemHalf);
      float cx1 = embH(l + float2(e * stepPt, 0.0), cellIdx, emblemHalf);
      float cx0 = embH(l - float2(e * stepPt, 0.0), cellIdx, emblemHalf);
      float cy1 = embH(l + float2(0.0, e * stepPt), cellIdx, emblemHalf);
      float cy0 = embH(l - float2(0.0, e * stepPt), cellIdx, emblemHalf);
      float amp = 0.85 * uK / (2.0 * e * stepPt);
      gx += (cx1 - cx0) * amp;
      gy += (cy1 - cy0) * amp;
      tweak = c0;
    }
  }
  return float4(-gx, -gy, 1.0, tweak);
}

float3 lightIt(float3 n, float3 alb, float metalness, float shadow, float ao, float spec1, float spec2) {
  float3 V = float3(0.0, 0.0, 1.0);
  float ndl = dot(n, LIGHT);
  float dif = clamp((ndl + 0.30) / 1.30, 0.0, 1.0);
  float3 H = normalize(LIGHT + V);
  float ndh = max(dot(n, H), 0.0);
  float sp = pow(ndh, spec1) * 1.0 + pow(ndh, spec2) * 0.10;
  float ndv = max(dot(n, V), 0.0);
  float fres = pow(1.0 - ndv, 4.0);
  float3 r = 2.0 * dot(n, V) * n - V;
  float3 env = envAt(r);
  float3 f0 = alb * 0.55 + 0.05;
  float3 refl = env * (f0 + (1.0 - f0) * fres * 0.9) * mix(0.35, 0.8, metalness);
  float3 body = alb * (0.10 * ao + 0.50 * dif * shadow * ao) + float3(0.010, 0.03, 0.07) * (1.0 - dif) * ao;
  float3 col = body * (1.0 - 0.35 * metalness) + refl * ao + (uEnvKey * 0.55 + 0.1) * sp * 0.55 * shadow * ao;
  return col;
}

float3 coinShade(float2 p, float4 A, float4 B, float tHit, float kind, float shadow, float ao, float halo, float amp,
                 float glow, float3 albCap, float meshFace, float cellIdx) {
  float R = uR;
  float Hh = uH;
  float az = sqrt(max(1.0 - B.x * B.x - B.y * B.y, 0.0004));
  float3 a3 = float3(B.x, B.y, az);
  float3 ref0 = abs(a3.y) > 0.9 ? float3(1.0, 0.0, 0.0) : float3(0.0, 1.0, 0.0);
  float3 u0 = normalize(cross(ref0, a3));
  float3 v0 = cross(a3, u0);
  float cs = cos(B.z);
  float sn = sin(B.z);
  float3 uu = cs * u0 + sn * v0;
  float3 vv = -sn * u0 + cs * v0;
  float3 q3 = float3(p - A.xy, tHit);
  float lu = dot(q3, uu);
  float lv = dot(q3, vv);
  float2 l = float2(lu, lv);
  float rr = max(length(l), 1e-4);
  float rho = rr / R;

  float3 nW;
  float3 alb;
  float metalness = 0.55;
  float spec1 = 34.0;
  float spec2 = 7.0;
  if (kind < 1.5) {
    float4 cs4 = capSurface(l, rho, meshFace, cellIdx, R * 0.62);
    float3 nl = normalize(cs4.xyz);
    nW = uu * nl.x + vv * nl.y + a3 * nl.z;
    alb = meshFace > 0.5 ? uAlbB : albCap;
    if (meshFace > 0.5) {
      alb *= 0.72 + 0.55 * clamp(cs4.w + 0.55, 0.0, 1.0);
      spec1 = 60.0;
    } else {
      alb *= 1.0 + 0.55 * cs4.w;
    }
    if (rho > 0.86) { alb = mix(alb, uAlbRim, 0.55 * sm(0.86, 0.93, rho)); }
    float ang = atan(lv, lu);
    alb *= 0.97 + 0.03 * sin(ang * 3.0 + rho * 90.0);
  } else {
    float th = atan(lv, lu);
    float kn = th * 92.0;
    float c1 = cos(kn);
    float2 radial = l / rr;
    float2 tang = float2(-radial.y, radial.x);
    float3 nl = normalize(float3(radial + tang * 0.30 * c1, 0.0));
    float roll = sm(Hh - 1.1 * uK, Hh, dot(q3, a3));
    nl = normalize(float3(nl.xy, 0.0) * (1.0 - 0.55 * roll) + float3(0.0, 0.0, 0.55 * roll));
    nW = uu * nl.x + vv * nl.y + a3 * nl.z;
    alb = uAlbRim * (0.84 + 0.16 * c1);
    metalness = 0.8;
    spec1 = 28.0;
  }
  nW = normalize(nW);
  float3 col = lightIt(nW, alb, metalness, shadow, ao, spec1, spec2);

  float rimF = 1.0 - max(nW.z, 0.0);
  float relief = clamp(dot(nW, LIGHT) * 0.5 + 0.5, 0.0, 1.0);
  float3 emit = CYAN * (0.20 + 0.62 * relief + 0.42 * rimF) + float3(0.55, 0.95, 1.0) * 0.34 * pow(max(nW.z, 0.0), 5.0);
  col = mix(col, emit, clamp(glow, 0.0, 1.0) * 0.82);
  col += CYAN * halo * 0.09 * amp * (1.0 - glow);
  return col;
}
"""

private const val CpcPileHead = """
uniform float uN;
uniform float3 uBg;
uniform float2 uTile;
uniform float uAmp;

void hitCoin(float2 p, float4 A, float4 B, float idx, float dith, inout float zBest, inout float bi, inout float4 bA,
             inout float4 bB, inout float tBest, inout float kindBest, inout float edgeBest) {
  float R = uR;
  float Hh = uH;
  if (idx >= uN) { return; }
  if (B.w < 0.02) { return; }
  if (B.w < 0.999 && B.w < dith) { return; }
  float2 d = p - A.xy;
  float d2 = dot(d, d);
  float rb = R + Hh;
  if (d2 > rb * rb) { return; }
  float az = sqrt(max(1.0 - B.x * B.x - B.y * B.y, 0.0004));
  float s0 = B.x * d.x + B.y * d.y;
  float tCap = (Hh - s0) / az;
  float tHit = -1e4;
  float kind = 0.0;
  float edge = 1.0;
  float inCap = R * R - (d2 + tCap * tCap - Hh * Hh);
  if (inCap >= 0.0) { tHit = tCap; kind = 1.0; edge = inCap / (2.0 * R); }
  float a2 = 1.0 - az * az;
  if (a2 > 0.003) {
    float bq = s0 * az;
    float cq = d2 - s0 * s0 - R * R;
    float disc = bq * bq - a2 * cq;
    if (disc >= 0.0) {
      float tS = (bq + sqrt(disc)) / a2;
      float sS = s0 + az * tS;
      if (abs(sS) <= Hh && tS > tHit) { tHit = tS; kind = 2.0; edge = sqrt(disc) / 4.0; }
    }
  }
  if (kind > 0.0) {
    float zh = A.z + tHit;
    if (zh > zBest) { zBest = zh; bi = idx; bA = A; bB = B; tBest = tHit; kindBest = kind; edgeBest = edge; }
  }
}

void shadeCoin(float2 p, float4 A, float4 B, float idx, float bi, float zp, inout float shadow, inout float occ, inout float halo) {
  float R = uR;
  float Hh = uH;
  if (idx >= uN) { return; }
  if (B.w < 0.02) { return; }
  if (idx == bi) { return; }
  float azj = sqrt(max(1.0 - B.x * B.x - B.y * B.y, 0.0004));
  float rj = R * (0.60 + 0.40 * azj);
  float topj = A.z + Hh + R * sqrt(B.x * B.x + B.y * B.y) * 0.5;
  float dz = topj - zp;
  float glow = A.w - 2.0 * step(1.5, A.w);
  if (dz > 0.5) {
    float2 q = p + LIGHT.xy * (dz / LIGHT.z);
    float dq = length(q - A.xy);
    float pen = 1.2 + 0.30 * dz;
    shadow *= 1.0 - 0.82 * (1.0 - sm(rj - pen, rj + pen, dq)) * B.w;
    float dc = length(p - A.xy);
    occ += (1.0 - sm(rj * 0.55, rj * 1.25, dc)) * sm(0.0, 7.0, dz) * 0.5 * B.w;
  }
  float dh2 = length(p - A.xy);
  halo += glow * exp(-max(dh2 - rj * 0.6, 0.0) / (R * 0.55));
}

half4 main(float2 p) {
  float zBest = -1e4;
  float bi = -1.0;
  float4 bA = float4(0.0);
  float4 bB = float4(0.0, 0.0, 0.0, 1.0);
  float tBest = 0.0;
  float kindBest = 0.0;
  float edgeBest = 1.0;
  float dith = bayer(p / max(uPx, 1e-4));
/*HITS*/
  float zp = bi >= 0.0 ? zBest : 0.0;
  float shadow = 1.0;
  float occ = 0.0;
  float halo = 0.0;
/*SHADES*/
  float ao = clamp(1.0 - occ * 0.55, 0.35, 1.0);

  float3 floorCol = uBg * (1.0 + 0.10 * (p.y / uTile.y - 0.5));
  floorCol *= mix(0.42, 1.0, shadow) * mix(0.55, 1.0, ao);
  floorCol += CYAN * halo * 0.11 * uAmp;
  floorCol += (hash21(p / max(uPx, 1e-4)) - 0.5) * (2.2 / 255.0);
  if (bi < 0.0) { return finish(floorCol, 1.0); }

  float4 A = bA;
  float4 B = bB;
  float face = step(1.5, A.w);
  float glow = A.w - 2.0 * face;
  float3 col = coinShade(p, A, B, tBest, kindBest, shadow, ao, halo, uAmp, glow, uAlbA, face, 0.0);
  float cov = clamp(edgeBest / max(uPx * 0.9, 1e-4) + 0.5, 0.0, 1.0);
  col = mix(floorCol, col, cov);
  return finish(col, 1.0);
}
"""

private const val CpcXCoinBody = """
uniform float4 uCoin;
uniform float4 uAxis;
uniform float uShadowOn;

half4 main(float2 p) {
  float R = uR;
  float Hh = uH;
  float4 A = float4(uCoin.xyz, 0.0);
  float4 B = float4(uAxis.xyz, 1.0);
  float az = sqrt(max(1.0 - B.x * B.x - B.y * B.y, 0.0004));
  float a2 = 1.0 - az * az;
  float hitN = 0.0;
  float tFirst = 0.0;
  float kFirst = 0.0;
  float2 pFirst = p;
  float got = 0.0;
  for (int k = 0; k < 5; k++) {
    float2 o = float2(0.0);
    if (k == 1) { o = float2(0.25, 0.25); }
    if (k == 2) { o = float2(-0.25, 0.25); }
    if (k == 3) { o = float2(0.25, -0.25); }
    if (k == 4) { o = float2(-0.25, -0.25); }
    float2 q = p + o * uPx;
    float2 d = q - A.xy;
    float d2 = dot(d, d);
    float rb = R + Hh;
    float tHit = -1e4;
    float kind = 0.0;
    if (d2 <= rb * rb) {
      float s0 = B.x * d.x + B.y * d.y;
      float tCap = (Hh - s0) / az;
      if (d2 + tCap * tCap - Hh * Hh <= R * R) { tHit = tCap; kind = 1.0; }
      if (a2 > 0.003) {
        float bq = s0 * az;
        float cq = d2 - s0 * s0 - R * R;
        float disc = bq * bq - a2 * cq;
        if (disc >= 0.0) {
          float tS = (bq + sqrt(disc)) / a2;
          float sS = s0 + az * tS;
          if (abs(sS) <= Hh && tS > tHit) { tHit = tS; kind = 2.0; }
        }
      }
    }
    if (k > 0 && kind > 0.0) { hitN += 1.0; }
    if (kind > 0.0 && got < 0.5) { got = 1.0; tFirst = tHit; kFirst = kind; pFirst = q; }
  }
  if (got < 0.5) { return half4(0.0); }
  float cov = hitN * 0.25;
  float face = uAxis.w;
  float3 albCap = face > 0.5 ? uAlbB : uAlbA;
  float3 col = coinShade(pFirst, A, B, tFirst, kFirst, 1.0, 1.0, 0.0, 0.0, 0.0, albCap, 0.0, 1.0 + face);
  return finish(col, cov);
}
"""

private const val CpcPageSrc = """
uniform float uP3;
uniform float2 uSize;
uniform float3 uPage;
uniform float3 uGlow;
uniform float3 uGlow2;

float hash21(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }

half4 main(float2 p) {
  float2 uv = p / uSize;
  float aspect = uSize.x / uSize.y;
  float2 q = (uv - float2(-0.05, 0.86)) * float2(aspect, 1.0);
  float g = exp(-pow(length(q) / 0.58, 2.0));
  float2 q2 = (uv - float2(1.05, 0.12)) * float2(aspect, 1.0);
  float g2 = exp(-pow(length(q2) / 0.55, 2.0));
  float3 c = uPage + (uGlow - uPage) * g + uGlow2 * g2;
  c += (hash21(p) - 0.5) * (2.4 / 255.0);
  half3 o = half3(clamp(c, float3(0.0), float3(1.0)));
  return half4(o, 1.0);
}
"""

internal fun cpcPileSource(): String {
    val hits = (0 until 32).joinToString("\n") { "  hitCoin(p, coinA$it, coinB$it, ${it}.0, dith, zBest, bi, bA, bB, tBest, kindBest, edgeBest);" }
    val shades = (0 until 32).joinToString("\n") { "  shadeCoin(p, coinA$it, coinB$it, ${it}.0, bi, zp, shadow, occ, halo);" }
    val uniforms = (0 until 32).joinToString("\n") { "uniform float4 coinA$it;\nuniform float4 coinB$it;" }
    return CpcCommon + uniforms + "\n" + CpcPileHead.replace("/*HITS*/", hits).replace("/*SHADES*/", shades)
}

internal fun cpcXCoinSource(): String = CpcCommon + CpcXCoinBody
internal fun cpcPageSource(): String = CpcPageSrc
