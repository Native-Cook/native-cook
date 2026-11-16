#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Faithful cpc-shaders.ts translation. Local coordinates, coverage, relief, light and colour math are unchanged.
namespace CoinPileCollectShader {
constant float PI = 3.14159265;
constant float3 LIGHT = float3(-0.5238, -0.5926, 0.6104);   // toward the key light (upper left, toward the viewer)
constant float3 CYAN = float3(0.25, 0.91, 1.0);
float2 glslMod(float2 x, float2 y) { return x - y * floor(x / y); }
float4 read4(device const float *v, int k) { return float4(v[k], v[k+1], v[k+2], v[k+3]); }
struct Scene {
    float uCell;
    float uP3;
    float uPx;
    float uR;
    float uH;
    float uK;
    float3 uAlbA;
    float3 uAlbB;
    float3 uAlbRim;
    float3 uEnvKey;
    float3 uEnvBounce;
    float3 uEnvRoom;
    float uN;
    float3 uBg;
    float2 uTile;
    float uAmp;
    float4 uCoin;
    float4 uAxis;
    float uShadowOn;
    float2 uSize;
    float3 uPage;
    float3 uGlow;
    float3 uGlow2;



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

// studio the satin metal reflects: a soft box up-left, a coloured bounce low-right, a dark room
float3 envAt(float3 r) {
  float3 room = mix(uEnvRoom, uEnvRoom * 3.2, sm(-0.7, 0.9, r.z));
  float kb = sm(0.50, 0.93, dot(r, normalize(float3(-0.5, -0.62, 0.6))));
  float3 key = uEnvKey * kb * 0.95;
  float bb = sm(0.15, 0.85, dot(r, normalize(float3(0.62, 0.5, 0.45))));
  float3 blue = uEnvBounce * bb * 0.55;
  float rim = sm(0.2, 0.9, dot(r, normalize(float3(0.1, -0.9, 0.3)))) * 0.25;
  return room + key + blue + (uEnvKey * 0.5 + uEnvBounce * 0.3) * rim;
}

// mesh face: hexagonal perforation. returns the height 0..1 (1 on the ridges between holes)
float meshH(float2 q) {
  float2 rr = float2(1.0, 1.7320508);
  float2 hh = rr * 0.5;
  float2 a = glslMod(q, rr) - hh;
  float2 b = glslMod(q - hh, rr) - hh;
  float2 g = dot(a, a) < dot(b, b) ? a : b;
  float e = 0.5 - max(dot(abs(g), normalize(float2(1.0, 1.7320508))), abs(g.x));
  return sm(0.04, 0.17, e);
}

// emblem ridge height from the atlas (cell index), l in pt, ext = half extent of the cell in pt
float embH(float2 l, float cellIdx, float ext, texture2d<half> emb) {
  if (abs(l.x) > ext || abs(l.y) > ext) { return 0.0; }
  float2 px = float2((l.x / ext * 0.5 + 0.5) * uCell + cellIdx * uCell, (l.y / ext * 0.5 + 0.5) * uCell);
  return float(emb.sample(sampler(coord::pixel, address::clamp_to_zero, filter::linear), px).a);
}

// everything the cap of a coin looks like: returns a local normal (xy = -gradient) in .xyz, and albedo tweak in w
float4 capSurface(float2 l, float rho, float meshFace, float cellIdx, float emblemHalf, texture2d<half> emb) {
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
      // hexagonal mesh, ~21 cells across the face
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
      float step_pt = (2.0 * emblemHalf) / uCell;
      float c0 = embH(l, cellIdx, emblemHalf, emb);
      float cx1 = embH(l + float2(e * step_pt, 0.0), cellIdx, emblemHalf, emb);
      float cx0 = embH(l - float2(e * step_pt, 0.0), cellIdx, emblemHalf, emb);
      float cy1 = embH(l + float2(0.0, e * step_pt), cellIdx, emblemHalf, emb);
      float cy0 = embH(l - float2(0.0, e * step_pt), cellIdx, emblemHalf, emb);
      float amp = 0.85 * uK / (2.0 * e * step_pt);
      gx += (cx1 - cx0) * amp;
      gy += (cy1 - cy0) * amp;
      tweak = c0;
    }
  }
  return float4(-gx, -gy, 1.0, tweak);
}

// light a point of a coin. n world normal, v = (0,0,1); returns colour
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

// the colour of one coin at one pixel. A = [x, y, z, _], B = [ax, ay, phi, _]; tHit and kind (1 cap, 2 wall) come from
// the ray test. albCap is the cap's albedo; meshFace 1 draws the hexagonal mesh, else the emblem of cell cellIdx.
float3 coinShade(float2 p, float4 A, float4 B, float tHit, float kind, float shadow, float ao, float halo, float amp,
                 float glow, float3 albCap, float meshFace, float cellIdx, texture2d<half> emb) {
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
    float4 cs4 = capSurface(l, rho, meshFace, cellIdx, R * 0.62, emb);
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
    // a faint spun-metal grain
    float ang = atan2(lv, lu);
    alb *= 0.97 + 0.03 * sin(ang * 3.0 + rho * 90.0);
  } else {
    float th = atan2(lv, lu);
    float kn = th * 92.0;
    float c1 = cos(kn);
    float2 radial = l / rr;
    float2 tang = float2(-radial.y, radial.x);
    float3 nl = normalize(float3(radial + tang * 0.30 * c1, 0.0));
    // the top edge of the wall rolls over into the cap's chamfer
    float roll = sm(Hh - 1.1 * uK, Hh, dot(q3, a3));
    nl = normalize(float3(nl.xy, 0.0) * (1.0 - 0.55 * roll) + float3(0.0, 0.0, 0.55 * roll));
    nW = uu * nl.x + vv * nl.y + a3 * nl.z;
    alb = uAlbRim * (0.84 + 0.16 * c1);
    metalness = 0.8;
    spec1 = 28.0;
  }
  nW = normalize(nW);
  float3 col = lightIt(nW, alb, metalness, shadow, ao, spec1, spec2);

  // emission: the coin lights up from inside, and its neighbours catch the spill
  float rimF = 1.0 - max(nW.z, 0.0);
  float relief = clamp(dot(nW, LIGHT) * 0.5 + 0.5, 0.0, 1.0);
  // lit from inside: the cyan follows the relief (the mark and the chamfer stay readable), a white-hot core in the middle
  float3 emit = CYAN * (0.20 + 0.62 * relief + 0.42 * rimF) + float3(0.55, 0.95, 1.0) * 0.34 * pow(max(nW.z, 0.0), 5.0);
  col = mix(col, emit, clamp(glow, 0.0, 1.0) * 0.82);
  col += CYAN * halo * 0.09 * amp * (1.0 - glow);
  return col;
}


half4 renderPile(float2 p, device const float *coins, texture2d<half> emb) {
  float R = uR;
  float Hh = uH;
  float zBest = -1e4;
  int bi = -1;
  float4 bA = float4(0.0);
  float4 bB = float4(0.0, 0.0, 0.0, 1.0);
  float tBest = 0.0;
  float kindBest = 0.0;
  float edgeBest = 1.0;
  float dith = bayer(p / max(uPx, 1e-4));
  for (int i = 0; i < 32; i++) {
    if (float(i) >= uN) { break; }
    float4 A = read4(coins, 8 * i);
    float4 B = read4(coins, 8 * i + 4);
    if (B.w < 0.02) { continue; }
    if (B.w < 0.999 && B.w < dith) { continue; }
    float2 d = p - A.xy;
    float d2 = dot(d, d);
    float rb = R + Hh;
    if (d2 > rb * rb) { continue; }
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
      if (zh > zBest) { zBest = zh; bi = i; bA = A; bB = B; tBest = tHit; kindBest = kind; edgeBest = edge; }
    }
  }

  // light pool + shadows that every coin throws on what is under it (the tile floor, or another coin)
  float zp = bi >= 0 ? zBest : 0.0;
  float shadow = 1.0;
  float occ = 0.0;
  float halo = 0.0;
  for (int j = 0; j < 32; j++) {
    if (float(j) >= uN) { break; }
    float4 A = read4(coins, 8 * j);
    float4 B = read4(coins, 8 * j + 4);
    if (B.w < 0.02) { continue; }
    if (j == bi) { continue; }
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
  float ao = clamp(1.0 - occ * 0.55, 0.35, 1.0);

  float3 floorCol = uBg * (1.0 + 0.10 * (p.y / uTile.y - 0.5));
  floorCol *= mix(0.42, 1.0, shadow) * mix(0.55, 1.0, ao);
  floorCol += CYAN * halo * 0.11 * uAmp;
  // a hair of grain so the flat teal does not band
  floorCol += (hash21(p / max(uPx, 1e-4)) - 0.5) * (2.2 / 255.0);
  if (bi < 0) { return finish(floorCol, 1.0); }

  float4 A = bA;
  float4 B = bB;
  float face = step(1.5, A.w);
  float glow = A.w - 2.0 * face;
  float3 col = coinShade(p, A, B, tBest, kindBest, shadow, ao, halo, uAmp, glow, uAlbA, face, 0.0, emb);
  // coverage of the silhouette against the floor (the pile's own coin-on-coin edges carry a rim and a shadow)
  float cov = clamp(edgeBest / max(uPx * 0.9, 1e-4) + 0.5, 0.0, 1.0);
  col = mix(floorCol, col, cov);
  return finish(col, 1.0);
}


half4 renderExchange(float2 p, texture2d<half> emb) {
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
  float3 col = coinShade(pFirst, A, B, tFirst, kFirst, 1.0, 1.0, 0.0, 0.0, 0.0, albCap, 0.0, 1.0 + face, emb);
  return finish(col, cov);
}
half4 renderPage(float2 p) {
  float2 uv = p / uSize;
  float aspect = uSize.x / uSize.y;
  float2 q = (uv - float2(-0.05, 0.86)) * float2(aspect, 1.0);
  float g = exp(-pow(length(q) / 0.58, 2.0));
  float2 q2 = (uv - float2(1.05, 0.12)) * float2(aspect, 1.0);
  float g2 = exp(-pow(length(q2) / 0.55, 2.0));
  float3 c = uPage + (uGlow - uPage) * g + uGlow2 * g2;
  c += (hash21(p) - 0.5) * (2.4 / 255.0);
  half3 o = half3(clamp(c, float3(0.0), float3(1.0)));
  if (uP3 > 0.5) { o = toP3(o); }
  return half4(o, 1.0);
}

};
Scene load(float px, bool exchange) {
    Scene s;
    s.uCell=192.0; s.uP3=1.0; s.uPx=px;
    s.uR=exchange?50.0:35.0; s.uH=exchange?5.0:4.2; s.uK=exchange?50.0/35.0:1.0;
    s.uAlbA=exchange?float3(200,104,159)/255.0:float3(29,86,102)/255.0;
    s.uAlbB=exchange?float3(230,174,142)/255.0:float3(10,36,45)/255.0;
    s.uAlbRim=exchange?float3(226,160,184)/255.0:float3(47,124,150)/255.0;
    s.uEnvKey=exchange?float3(1.0,0.86,0.94):float3(0.72,0.95,1.0);
    s.uEnvBounce=exchange?float3(0.5,0.2,0.62):float3(0.14,0.3,0.8);
    s.uEnvRoom=exchange?float3(0.12,0.04,0.12):float3(0.01,0.04,0.06);
    s.uN=26.0; s.uBg=float3(30,84,122)/255.0; s.uTile=float2(174,340); s.uAmp=1.0;
    s.uCoin=float4(88,54,5,1); s.uAxis=float4(0,0,0,0); s.uShadowOn=0.0;
    s.uSize=float2(402,874); s.uPage=float3(7,4,12)/255.0;
    s.uGlow=float3(34,16,60)/255.0; s.uGlow2=float3(4,4,12)/255.0;
    return s;
}
inline half4 displaySRGB(half4 color) {
    if (color.a <= 0.0h) return half4(0.0h);
    float a = float(color.a);
    float3 c = float3(color.rgb) / a;
    c = select(c / 12.92, pow((c + 0.055) / 1.055, float3(2.4)), c > 0.04045);
    c = float3(dot(c, float3(1.22474527, -0.22490437, 0.0)),
               dot(c, float3(-0.04205797, 1.04208100, 0.0)),
               dot(c, float3(-0.01964227, -0.07865492, 1.09853719)));
    c = max(c, 0.0);
    c = select(c * 12.92, 1.055 * pow(c, float3(1.0 / 2.4)) - 0.055, c > 0.0031308);
    return half4(half3(c * a), color.a);
}


}

[[stitchable]] half4 coinPileCollectPile(float2 position, half4 color, float px,
    device const float *coins, int count, texture2d<half> emb) {
    if (count < 208) return half4(0.0h);
    auto s = CoinPileCollectShader::load(px, false);
    return CoinPileCollectShader::displaySRGB(s.renderPile(position, coins, emb));
}
[[stitchable]] half4 coinPileCollectExchange(float2 position, half4 color, float px,
    device const float *axis, int count, texture2d<half> emb) {
    if (count < 4) return half4(0.0h);
    auto s = CoinPileCollectShader::load(px, true);
    s.uAxis=CoinPileCollectShader::read4(axis,0);
    return CoinPileCollectShader::displaySRGB(s.renderExchange(position, emb));
}
[[stitchable]] half4 coinPileCollectPage(float2 position, half4 color, float2 size) {
    auto s = CoinPileCollectShader::load(1.0, false);
    s.uSize=size;
    return CoinPileCollectShader::displaySRGB(s.renderPage(position));
}
