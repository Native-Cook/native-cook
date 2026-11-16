#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Value-for-value translation of ffb-shaders.ts at a360cb09.
namespace FlipFocusBadgeShader {
constant float E_CAM = 1000.0;
constant float R_BOUND = 176.0;
constant float PLATE_R0 = 84.0;
constant float RING_R = 138.1;
constant float RING_RT = 7.3;
constant float ARC_SIN = 0.14867;
constant float ARC_COS = -0.98889;
constant float CLOCK_K = 0.38;
struct Badge {

texture2d<half> uAtlas;
float2 uC;
float uS;
float uPx;
float3 uBx;
float3 uBy;
float3 uBz;
float3 uRx;
float3 uRy;
float3 uRz;
float3 uRc;
float3 uSpH;
float3 uSpS;
float4 uFront;
float4 uBack;
float4 uBackX;
float4 uGlint;
float4 uMat;
float4 uAux;
float2 uPop;
float2 uNum;
float3 uTint;
float3 uEdge;
float3 uPlate;













// The source is a grayscale DATA atlas. Store it in alpha to bypass SwiftUI image gamma conversion.
// Native Skia PushImageShaderCmd uses linear filtering, clamp addressing, and no mipmaps.
float atlasValue(float2 pixel) {
  constexpr sampler glyphSampler(coord::normalized, address::clamp_to_edge, filter::linear);
  return float(uAtlas.sample(glyphSampler, pixel / float2(1120.0, 1080.0)).a);
}
float sq(float x) { return x * x; }
float sat1(float x) { return clamp(x, 0.0, 1.0); }

float hash21(float2 p) {
  float3 q = fract(float3(p.x, p.y, p.x) * 0.1031);
  q += dot(q, float3(q.y, q.z, q.x) + 33.33);
  return fract((q.x + q.y) * q.z);
}

// ---- the atlas -----------------------------------------------------------------------------------------------------
float sdfDigit(float cell, float2 g) {
  float cx = fmod(cell, 5.0);
  float cr = floor(cell / 5.0);
  float2 px = clamp(float2(32.0 + g.x * 2.0, 214.0 - g.y * 2.0), float2(1.5, 1.5), float2(222.5, 244.5));
  float v = atlasValue(float2(cx * 224.0, cr * 246.0) + px);
  return (v - 0.5) * 8.0;
}
float domeDigit(float cell, float2 g) {
  float cx = fmod(cell, 5.0);
  float cr = floor(cell / 5.0);
  float2 px = clamp(float2(32.0 + g.x * 2.0, 214.0 - g.y * 2.0), float2(1.5, 1.5), float2(222.5, 244.5));
  return atlasValue(float2(cx * 224.0, 588.0 + cr * 246.0) + px);
}
float sdfSmall(float idx, float2 g) {
  float2 px = clamp(float2(48.0 + g.x * 2.0, 48.0 - g.y * 2.0), float2(1.5, 1.5), float2(94.5, 94.5));
  float v = atlasValue(float2(idx * 96.0, 492.0) + px);
  return (v - 0.5) * 8.0;
}

float hrDist(float2 b) {
  if (uAux.w < 0.5) return 30.0;
  float s = max(uPop.y, 0.2);
  float2 g = float2(51.5, -10.2) + (b - float2(51.5, -10.2)) / s;
  return s * min(sdfSmall(0.0, g - float2(44.65, -9.0)), sdfSmall(1.0, g - float2(58.5, -11.4)));
}

// ---- the plate ------------------------------------------------------------------------------------------------------
float fScallop(float2 q) {
  float rr = length(q);
  float an = atan2(q.y, q.x + 0.0001);
  float sn = sin(6.0 * an);
  float rb = 73.3 + 11.1 * sqrt(sn * sn + 0.012);
  return (rr - rb) * 0.74;
}
float plateHalf(float2 q) {
  return 4.0 + 2.2 * (1.0 - min(dot(q, q) / (PLATE_R0 * PLATE_R0), 1.0));
}
float sdPlate(float3 b) {
  float d2 = fScallop(b.xy);
  float hp = plateHalf(b.xy);
  float rb = 2.4;
  float2 w = float2(d2 + rb, abs(b.z) - hp + rb);
  return min(max(w.x, w.y), 0.0) + length(max(w, float2(0.0, 0.0))) - rb;
}

// ---- rounded extrusions standing on a surface (d2 = horizontal signed distance, neg inside; zz above the base) ----------
float rext(float d2, float zlo, float zhi, float zz, float rb) {
  float2 w = float2(d2 + rb, abs(zz - 0.5 * (zlo + zhi)) - 0.5 * (zhi - zlo) + rb);
  return min(max(w.x, w.y), 0.0) + length(max(w, float2(0.0, 0.0))) - rb;
}
// the numerals: a vertical riser, a bevel and a gentle dome across the face (the atlas's dome field, smooth by design).
// depth scales the whole extrusion.
float numBody(float d2, float dm, float zz) {
  float hh = 9.0 * uMat.x + 0.05;
  float rb = min(3.4, hh * 0.6);
  return rext(d2, -3.0, hh, zz - 4.0 * uMat.x * dm, rb) * 0.92;
}

float dFrontDigit(float cell, float ox, float2 b) {
  return sdfDigit(cell, float2(b.x - ox, b.y - uNum.y) / uNum.x) * uNum.x;
}

// ---- the ring -------------------------------------------------------------------------------------------------------
float3 ringLocal(float3 pw) {
  return float3(dot(uRx, pw), dot(uRy, pw), dot(uRz, pw)) - uRc;
}
float sdRing(float3 pw) {
  float3 l = ringLocal(pw);
  l.x = abs(l.x);
  float k = (ARC_COS * l.x > ARC_SIN * l.y) ? (l.x * ARC_SIN + l.y * ARC_COS) : length(l.xy);
  return sqrt(max(dot(l, l) + RING_R * RING_R - 2.0 * RING_R * k, 0.0)) - RING_RT;
}
// a camera-facing sparkle: a thin star slab, lifted in front of the tube it sits on
float sdSpark(float3 pw, float3 c, float sc, float glint) {
  float3 q = pw - (c + float3(0.0, 0.0, 9.0));
  if (abs(q.x) > 24.0 || abs(q.y) > 24.0 || abs(q.z) > 12.0) return 6.0;
  float s2 = sc * (1.0 + 0.25 * glint);
  float d2 = sdfSmall(2.0, q.xy / s2) * s2;
  return rext(d2, -1.5, 1.5, q.z, 1.2);
}

// scene: x = distance, y = material (1 plate, 2 numeral, 3 hr, 4 plus, 5 dot, 6 ring, 7 sparkle)
float2 mapScene(float3 pw) {
  float3 b = float3(dot(uBx, pw), dot(uBy, pw), dot(uBz, pw));
  float res = sdPlate(b);
  float id = 1.0;
  if (b.z > -1.0 && b.z < 24.0 && abs(b.x) < 112.0 && abs(b.y) < 64.0) {
    float zb = plateHalf(b.xy);
    float zz = b.z - zb;
    if (uFront.x > -0.5) {
      float2 gt = float2(b.x - uFront.z, b.y - uNum.y) / uNum.x;
      float dt = sdfDigit(uFront.x, gt) * uNum.x;
      float mt = domeDigit(uFront.x, gt);
      float bt = numBody(dt, mt, zz);
      if (bt < res) { res = bt; id = 2.0; }
    }
    if (uFront.y > -0.5) {
      float2 gn = float2(b.x - uFront.w, b.y - uNum.y) / uNum.x;
      float dn = sdfDigit(uFront.y, gn) * uNum.x;
      float mn = domeDigit(uFront.y, gn);
      float bn = numBody(dn, mn, zz);
      if (bn < res) { res = bn; id = 2.0; }
    }
    if (uAux.w > 0.5 && b.x > 34.0 && b.y < 4.0 && b.y > -30.0) {
      float dh = hrDist(b.xy);
      float bh = rext(dh, -2.0, 4.2, zz - 9.0 * uMat.x - 0.05, 1.7);
      if (bh < res) { res = bh; id = 3.0; }
    }
    if (b.x < -30.0 && b.y > -6.0) {
      float sp = max(uPop.x, 0.2);
      float dp = sdfSmall(2.0, (b.xy - float2(-56.5, 14.2)) / sp) * sp;
      float bp = rext(dp, -2.0, 3.0 + 0.5 * uGlint.x, zz, 1.5);
      if (bp < res) { res = bp; id = 4.0; }
      float dd = sdfSmall(3.0, (b.xy - float2(-38.0, 29.8)) / sp) * sp;
      float bd = rext(dd, -2.0, 2.4, zz, 1.2);
      if (bd < res) { res = bd; id = 5.0; }
    }
  }
  float dr = sdRing(pw);
  if (dr < res) { res = dr; id = 6.0; }
  float ds = min(sdSpark(pw, uSpH, 0.92, uGlint.y), sdSpark(pw, uSpS, 0.86, uGlint.z));
  if (ds < res) { res = ds; id = 7.0; }
  return float2(res, id);
}

float3 calcNormal(float3 p, float eps) {
  float2 e = float2(1.0, -1.0) * 0.5773 * eps;
  return normalize(
    float3(e.x, e.y, e.y) * mapScene(p + float3(e.x, e.y, e.y)).x +
    float3(e.y, e.y, e.x) * mapScene(p + float3(e.y, e.y, e.x)).x +
    float3(e.y, e.x, e.y) * mapScene(p + float3(e.y, e.x, e.y)).x +
    float3(e.x, e.x, e.x) * mapScene(p + float3(e.x, e.x, e.x)).x);
}

// ---- the studio -----------------------------------------------------------------------------------------------------
float softBox(float2 a, float2 c, float2 hs, float soft) {
  float2 d = abs(a - c) - hs;
  return 1.0 - smoothstep(-soft, soft, max(d.x, d.y));
}
float3 rotYv(float3 r, float a) {
  float cs = cos(a);
  float sn = sin(a);
  return float3(cs * r.x + sn * r.z, r.y, -sn * r.x + cs * r.z);
}
// the chrome studio the numerals reflect: a pale cyclorama all round (dark only straight behind), a horizon band of
// deeper periwinkle, a softbox (upper left) and a strip (right)
float3 envChrome(float3 r, float soft) {
  float up = r.y;
  float fr = smoothstep(-0.90, -0.52, r.z);
  float3 sky = mix(float3(0.80, 0.855, 1.00), float3(0.975, 0.985, 1.02), smoothstep(-0.2, 0.75, up));
  // horizon bands: a deeper periwinkle one and a white streak above it, a faint pair below (arches hugging the top of a dome)
  float blueB = exp(-sq((up - 0.22) / 0.085)) + 0.55 * exp(-sq((up + 0.30) / 0.06));
  float whiteB = exp(-sq((up - 0.40) / 0.05)) + 0.25 * exp(-sq((up + 0.46) / 0.05));
  sky = mix(sky, float3(0.60, 0.70, 0.97), (1.0 - smoothstep(-0.55, 0.0, up)) * 0.85);
  sky = mix(sky, float3(0.50, 0.60, 0.95), clamp(blueB, 0.0, 0.92));
  sky += whiteB * 0.36;
  float key = softBox(r.xy, float2(-0.36, 0.60), float2(0.28, 0.14), soft);
  float strp = softBox(r.xy, float2(0.66, 0.02), float2(0.05, 0.48), soft);
  sky += (key * 0.40 + strp * 0.30) * float3(1.0, 1.0, 1.0);
  return mix(float3(0.10, 0.12, 0.30), sky, fr);
}
// the studio as seen by the glass plate: nearly black, the softbox and the strip only
float3 envGlass(float3 r, float soft) {
  // a long strip softbox, tilted like the ring (−14°) so its reflection crosses the glass as a soft grey band, and a
  // broad dim key above it; the dome of the pane (a few degrees) is what moves them across the plate
  float line = r.y - 0.15 * r.x - 0.004;
  float strp = exp(-sq(line / (0.030 + soft * 0.30)));
  float key = softBox(r.xy, float2(-0.05, 0.16), float2(0.40, 0.05), soft * 1.6);
  float fr = smoothstep(-0.2, 0.3, r.z);
  return fr * (strp * float3(3.4, 3.7, 4.7) + key * float3(1.2, 1.25, 1.4)) + float3(0.02, 0.022, 0.03);
}

float3 toP3(float3 c) {
  float3 lin = pow(max(c, float3(0.0, 0.0, 0.0)), float3(2.2, 2.2, 2.2));
  float3 p3 = float3(0.8225 * lin.x + 0.1774 * lin.y, 0.0332 * lin.x + 0.9669 * lin.y, 0.0171 * lin.x + 0.0724 * lin.y + 0.9108 * lin.z);
  return pow(p3, float3(1.0 / 2.2, 1.0 / 2.2, 1.0 / 2.2));
}

// the pin's navy outline round a raised glyph: distance of a surface point to the numerals, in the plate plane
float numeralDist(float2 b) {
  float d = 30.0;
  if (uFront.x > -0.5) d = min(d, dFrontDigit(uFront.x, uFront.z, b));
  if (uFront.y > -0.5) d = min(d, dFrontDigit(uFront.y, uFront.w, b));
  return d;
}

float3 shade(float3 p, float3 n, float3 rd, float id) {
  float3 v = -rd;
  float nv = sat1(dot(n, v));
  float3 rl = reflect(rd, n);
  float3 re = rotYv(rl, uMat.z);
  float soft = 0.05 + 0.32 * uMat.y;
  float3 lk = normalize(float3(-0.35, 0.55, 0.76));
  float ndl = max(dot(n, lk), 0.0);
  float3 b = float3(dot(uBx, p), dot(uBy, p), dot(uBz, p));
  float3 nb = float3(dot(uBx, n), dot(uBy, n), dot(uBz, n));
  float fres = pow(1.0 - nv, 4.0);
  float3 col = float3(0.0, 0.0, 0.0);

  if (id < 1.5) {
    // black glass plate
    float de = -fScallop(b.xy) / 0.74;
    float3 env = envGlass(re, soft);
    float f0 = 0.05 + 0.95 * fres;
    float dn = numeralDist(b.xy);
    float ao = 1.0 - 0.8 * exp(-max(dn, 0.0) / 2.6) * step(0.0, nb.z);
    col = uPlate * (0.78 + 0.22 * ndl) + env * f0 * 1.15;
    col *= ao;
    // a faint blue bounce round the numerals
    col += float3(0.10, 0.13, 0.26) * 0.10 * exp(-max(dn, 0.0) / 3.2) * smoothstep(-2.0, 2.0, dn) * step(0.0, nb.z);
    // the back face: "FOCUS" and the session clock, flat enamel
    if (nb.z < -0.35) {
      float uu = -b.x;
      float vv = b.y;
      float dd = 30.0;
      float yb = -32.4;
      dd = min(dd, sdfDigit(uBack.x, float2((uu - uBackX.x) / CLOCK_K, (vv - yb) / CLOCK_K)) * CLOCK_K);
      dd = min(dd, sdfDigit(uBack.y, float2((uu - uBackX.y) / CLOCK_K, (vv - yb) / CLOCK_K)) * CLOCK_K);
      dd = min(dd, sdfDigit(uBack.z, float2((uu - uBackX.z) / CLOCK_K, (vv - yb) / CLOCK_K)) * CLOCK_K);
      dd = min(dd, sdfDigit(uBack.w, float2((uu - uBackX.w) / CLOCK_K, (vv - yb) / CLOCK_K)) * CLOCK_K);
      dd = min(dd, sdfSmall(4.0, float2((uu - uGlint.w) / 0.9, (vv + 15.2) / 0.9)) * 0.9);
      float cap = 30.0;
      cap = min(cap, sdfSmall(5.0, float2(uu + 24.0, vv - 36.0)));
      cap = min(cap, sdfSmall(6.0, float2(uu + 12.0, vv - 36.0)));
      cap = min(cap, sdfSmall(7.0, float2(uu, vv - 36.0)));
      cap = min(cap, sdfSmall(8.0, float2(uu - 12.0, vv - 36.0)));
      cap = min(cap, sdfSmall(9.0, float2(uu - 24.0, vv - 36.0)));
      float mk = 1.0 - smoothstep(-0.35, 0.35, dd);
      float mc = (1.0 - smoothstep(-0.25, 0.25, cap)) * 0.62;
      col = mix(col, float3(0.93, 0.945, 0.985), max(mk, mc));
    }
    col = mix(col, float3(0.02, 0.025, 0.07), (1.0 - smoothstep(1.6, 2.4, hrDist(b.xy))) * smoothstep(-0.5, 0.0, hrDist(b.xy)) * step(0.3, nb.z) * 0.9);
    // the enamel edge, 1.5 pt, on the face and all round the rim
    float en = 1.0 - smoothstep(0.95, 1.55, de);
    float3 enc = uEdge * (0.78 + 0.22 * ndl) + float3(0.10, 0.11, 0.14) * f0;
    col = mix(col, enc, en);
  } else if (id < 2.5) {
    // numeral: liquid chrome, periwinkle. The face reflects the studio; the bevel runs from a dark hairline to a pale band.
    float3 env = envChrome(re, soft);
    float3 dif = uTint * (0.42 + 0.58 * ndl);
    float kr = 0.80 + 0.18 * fres;
    col = mix(dif, env * float3(0.96, 0.975, 1.0), kr);
    // the riser (seen only when the badge turns): the deeper blue of the sides
    float wall = (1.0 - smoothstep(0.02, 0.30, nb.z));
    col = mix(col, float3(0.35, 0.44, 0.88) * (0.45 + 0.55 * ndl), wall);
    // the pin's hairline of navy where a glyph meets the plate
    float dn = numeralDist(b.xy);
    col = mix(col, float3(0.05, 0.07, 0.20), (1.0 - smoothstep(0.0, 0.8, -dn)) * 0.9 * step(0.15, nb.z));
    col = mix(col, float3(0.02, 0.025, 0.07), (1.0 - smoothstep(1.6, 2.4, hrDist(b.xy))) * smoothstep(-0.5, 0.0, hrDist(b.xy)) * step(0.3, nb.z) * 0.92);
  } else if (id < 3.5) {
    // "hr": white, with the pin's heavy dark outline
    float3 env = envChrome(re, soft);
    col = mix(float3(0.93, 0.95, 1.0) * (0.60 + 0.40 * ndl), env, 0.5);
    float dh = hrDist(b.xy);
    col = mix(col, float3(0.03, 0.04, 0.10), (1.0 - smoothstep(0.0, 1.1, -dh)) * 0.92);
  } else if (id < 5.5) {
    // "+" and its dot: a pale lavender sticker with the pin's navy outline
    float3 env = envChrome(re, soft);
    col = mix(float3(0.86, 0.89, 0.985) * (0.68 + 0.32 * ndl), env, 0.30);
    col = mix(col, float3(0.05, 0.07, 0.20), (1.0 - smoothstep(0.30, 0.72, nb.z)) * 0.95);
    col += uGlint.x * 0.35 * float3(1.0, 1.0, 1.0) * step(id, 4.5);
  } else if (id < 6.5) {
    // ring: a satin tube, almost flat like the pin's, with a pale rim and a navy edge
    float3 bs = float3(0.56, 0.678, 0.953);
    col = bs * (0.85 + 0.09 * ndl);
    col += float3(0.05, 0.06, 0.07) * (1.0 - smoothstep(0.15, 0.55, nv));
    col = mix(col, float3(0.05, 0.07, 0.20), (1.0 - smoothstep(0.0, 0.16, nv)) * 0.85);
  } else {
    // sparkles on the ring: the same periwinkle, outlined in navy where they cross the tube
    float3 bs = float3(0.60, 0.72, 0.98);
    col = bs * (0.92 + 0.08 * ndl);
    col += 0.35 * max(uGlint.y, uGlint.z) * float3(1.0, 1.0, 1.0);
    float2 qh = (p.xy - (uSpH.xy)) / 0.92;
    float2 qs = (p.xy - (uSpS.xy)) / 0.86;
    float dsp = min(sdfSmall(2.0, qh) * 0.92, sdfSmall(2.0, qs) * 0.86);
    col = mix(col, float3(0.05, 0.07, 0.20), smoothstep(-1.5, -0.4, dsp) * 0.92);
  }
  return col;
}

half4 render(float2 fc) {
  float sc = uMat.w;
  float2 q = (fc - uC) / (uS * sc);
  float3 ro = float3(0.0, 0.0, E_CAM);
  float3 rd = normalize(float3(q.x, -q.y, -E_CAM));
  float bq = dot(ro, rd);
  float cq = dot(ro, ro) - R_BOUND * R_BOUND;
  float dc = bq * bq - cq;
  if (dc < 0.0) return half4(0.0, 0.0, 0.0, 0.0);
  float sdc = sqrt(dc);
  float tn = max(-bq - sdc, 0.0);
  float tf = -bq + sdc;
  float tt = tn;
  float bestR = 1e5;
  float bestT = tn;
  float hit = 0.0;
  float2 m = float2(0.0, 0.0);
  for (int i = 0; i < 104; i++) {
    float3 pp = ro + rd * tt;
    m = mapScene(pp);
    float pxw = uPx * tt / E_CAM;
    float rr = m.x / pxw;
    if (rr < bestR) { bestR = rr; bestT = tt; }
    if (m.x < 0.12 * pxw) { hit = 1.0; break; }
    tt += m.x * 0.82;
    if (tt > tf) break;
  }
  float cover = 1.0;
  if (hit < 0.5) {
    cover = 1.0 - smoothstep(0.35, 1.25, bestR);
    if (cover <= 0.001) return half4(0.0, 0.0, 0.0, 0.0);
    tt = bestT;
    m = mapScene(ro + rd * tt);
  }
  float3 p = ro + rd * tt;
  float3 n = calcNormal(p, 0.16);
  float3 col = shade(p, n, rd, m.y);
  col += (hash21(floor(fc * 3.0)) - 0.5) / 255.0;
  col = clamp(col, float3(0.0, 0.0, 0.0), float3(1.0, 1.0, 1.0));
  float3 outc = mix(col, toP3(col), uAux.x);
  return half4(half3(outc * cover), half(cover));
}

};
inline Badge loadBadge(device const float *u, texture2d<half> atlas) {
  Badge s;
  s.uAtlas = atlas;
  s.uC = float2(u[0], u[1]);
  s.uS = u[2];
  s.uPx = u[3];
  s.uBx = float3(u[4], u[5], u[6]);
  s.uBy = float3(u[7], u[8], u[9]);
  s.uBz = float3(u[10], u[11], u[12]);
  s.uRx = float3(u[13], u[14], u[15]);
  s.uRy = float3(u[16], u[17], u[18]);
  s.uRz = float3(u[19], u[20], u[21]);
  s.uRc = float3(u[22], u[23], u[24]);
  s.uSpH = float3(u[25], u[26], u[27]);
  s.uSpS = float3(u[28], u[29], u[30]);
  s.uFront = float4(u[31], u[32], u[33], u[34]);
  s.uBack = float4(u[35], u[36], u[37], u[38]);
  s.uBackX = float4(u[39], u[40], u[41], u[42]);
  s.uGlint = float4(u[43], u[44], u[45], u[46]);
  s.uMat = float4(u[47], u[48], u[49], u[50]);
  s.uAux = float4(u[51], u[52], u[53], u[54]);
  s.uPop = float2(u[55], u[56]);
  s.uNum = float2(u[57], u[58]);
  s.uTint = float3(u[59], u[60], u[61]);
  s.uEdge = float3(u[62], u[63], u[64]);
  s.uPlate = float3(u[65], u[66], u[67]);
  return s;
}


struct Backdrop {

float2 uSize;
float3 uGround;
float4 uShadow;
float uAmt;
float uYramp0;
float uYramp1;
float4 uAux;

float hash21(float2 p) {
  float3 q = fract(float3(p.x, p.y, p.x) * 0.1031);
  q += dot(q, float3(q.y, q.z, q.x) + 33.33);
  return fract((q.x + q.y) * q.z);
}
float3 toP3(float3 c) {
  float3 lin = pow(max(c, float3(0.0, 0.0, 0.0)), float3(2.2, 2.2, 2.2));
  float3 p3 = float3(0.8225 * lin.x + 0.1774 * lin.y, 0.0332 * lin.x + 0.9669 * lin.y, 0.0171 * lin.x + 0.0724 * lin.y + 0.9108 * lin.z);
  return pow(p3, float3(1.0 / 2.2, 1.0 / 2.2, 1.0 / 2.2));
}

half4 render(float2 fc) {
  float ry = smoothstep(uYramp0, uYramp1, fc.y);
  float rx = smoothstep(0.35, 1.0, fc.x / uSize.x) * smoothstep(uYramp0 + 0.15 * (uYramp1 - uYramp0), uYramp1 + 40.0, fc.y);
  float base = mix(uGround.x, uGround.y, ry) + (uGround.z - uGround.y) * rx;
  float2 sd = (fc - uShadow.xy) / uShadow.zw;
  float sh = exp(-dot(sd, sd) * 1.6) * uAmt;
  base *= 1.0 - 0.55 * sh;
  float gr = (hash21(floor(fc * 3.0) + uAux.y) - 0.5) * 0.034;
  float c = max(base + gr * (0.35 + 0.65 * smoothstep(0.0, 0.07, base)), 0.0);
  float3 col = float3(c, c, c);
  float3 outc = mix(col, toP3(col), uAux.x);
  return half4(half3(outc), 1.0);
}

};
inline Backdrop loadBackdrop(device const float *u) {
  Backdrop s;
  s.uSize = float2(u[0], u[1]);
  s.uGround = float3(u[2], u[3], u[4]);
  s.uShadow = float4(u[5], u[6], u[7], u[8]);
  s.uAmt = u[9];
  s.uYramp0 = u[10];
  s.uYramp1 = u[11];
  s.uAux = float4(u[12], u[13], u[14], u[15]);
  return s;
}

// RN returns gamma-2.2 converted P3 values to a Display P3 canvas. SwiftUI's
// non-linear colorEffect works in sRGB: decode the P3 output, transform primaries,
// then encode sRGB. Keep the source's half quantization and premultiplied alpha.
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

[[ stitchable ]] half4 flipFocusBadgeScene(float2 position, half4 color,
    device const float *uniforms, int count, texture2d<half> atlas) {
    if (count < 68) return half4(0.0h);
    FlipFocusBadgeShader::Badge s = FlipFocusBadgeShader::loadBadge(uniforms, atlas);
    return FlipFocusBadgeShader::displaySRGB(s.render(position));
}
[[ stitchable ]] half4 flipFocusBadgeBackdrop(float2 position, half4 color,
    device const float *uniforms, int count) {
    if (count < 16) return half4(0.0h);
    FlipFocusBadgeShader::Backdrop s = FlipFocusBadgeShader::loadBackdrop(uniforms);
    return FlipFocusBadgeShader::displaySRGB(s.render(position));
}
