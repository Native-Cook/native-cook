#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

namespace ParticleRibbonVoiceShader {
constant float TAU = 6.2831853;
struct Scene {

float2 uRes;
float uS;
float uOY;
float uPh;
float4 uLine;
float4 uSheet;
float4 uTwA;
float4 uTwB;
float4 uThink;
float4 uPul[8];
float4 uWin[2];
float4 uWinB[2];
float4 uBump[2];
float4 uOpt;
float4 uExtra;
float3 uC0;
float3 uC1;
float3 uC2;
float uDbg;



float sq(float x) { return x * x; }

// hashes without sin(): the same pattern on every GPU
float h11(float p) {
  p = fract(p * 0.1031);
  p *= p + 33.33;
  p *= p + p;
  return fract(p);
}
float h21(float2 p) {
  float3 q = fract(float3(p.x, p.y, p.x) * 0.1031);
  q += dot(q, q.yzx + 33.33);
  return fract((q.x + q.y) * q.z);
}

float vn(float x) {
  float i = floor(x);
  float f = fract(x);
  f = f * f * (3.0 - 2.0 * f);
  return mix(h11(i), h11(i + 1.0), f);
}

float winShape(float ad, float h) {
  return 1.0 - smoothstep(h, h + 46.0, ad);
}

// the twist angle along the ribbon
float twistAt(float u) {
  float a = uTwA.x * sin(TAU * u / uTwA.y + uTwA.z) + uTwA.w * ((u - 200.0) / 402.0);
  a += uTwB.x * exp(-sq(u / uTwB.z)) + uTwB.y * exp(-sq((402.0 - u) / uTwB.z));
  a += uThink.x * (u - uThink.y) * uThink.z;
  for (int i = 0; i < 8; i++) {
    float4 p = uPul[i];
    float x = (u - p.x) / max(p.z, 1.0);
    a += p.y * exp(-x * x);
  }
  for (int w = 0; w < 2; w++) {
    float4 f = uWin[w];
    float d = u - f.x;
    float ad = abs(d);
    float s = f.y * winShape(ad, f.w);
    float psi = f.z + 1.5707963 * (d >= 0.0 ? 1.0 : -1.0) * pow(min(ad / max(f.w, 1.0), 1.0), 2.4);
    a = mix(a, psi, s);
  }
  return a;
}

float lineAt(float u) {
  float y = uLine.x + uLine.y * cos(TAU * (u / uLine.z - uLine.w));
  for (int k = 0; k < 2; k++) {
    float4 bp = uBump[k];
    float bd = (u - bp.x) / max(bp.z, 1.0);
    y += bp.y * exp(-bd * bd);
  }
  for (int w = 0; w < 2; w++) {
    float4 f = uWin[w];
    float4 b = uWinB[w];
    float s = f.y * winShape(abs(u - f.x), f.w);
    float pull = b.z * (b.x - y);
    y += s * pull / (1.0 + abs(pull) / 34.0);
  }
  return y;
}

float widthAt(float u) {
  float wd = uSheet.x;
  for (int w = 0; w < 2; w++) {
    float4 f = uWin[w];
    float s = f.y * winShape(abs(u - f.x), f.w);
    wd += s * uWinB[w].y;
  }
  return wd;
}

float3 rampAt(float u) {
  float3 c = mix(uC0, uC1, smoothstep(0.30, 0.47, u));
  return mix(c, uC2, smoothstep(0.49, 0.66, u));
}

float muA() { return 0.42; }

// the line's profile across the sheet: a crisp edge, an exponential body (scale L) toward the dust, a soft outside;
// at L -> 0 it closes into a symmetric gaussian of the sheet's own thickness (sg), so the line never jumps at a crossing
float lineProfile(float t, float L, float sg) {
  float pa = (t >= 0.0 ? exp(-t / L) : exp(-sq(t / sg) * 0.5)) / (L + 1.25 * sg);
  float pg = 0.3989 / sg * exp(-sq(t - L) / (2.0 * sg * sg));
  return mix(pa, pg, 1.0 - smoothstep(0.25 * sg, 1.5 * sg, L));
}

float flowN(float x, float y, float tph, float k) {
  return sin(0.037 * x + 0.029 * y + tph * k + 0.6 * sin(0.021 * y - tph)) + 0.7 * sin(0.017 * x - 0.043 * y - 2.0 * tph * k + 1.3);
}

half4 render(float2 fc) {
  float2 q = fc / uS;
  float x = q.x;
  float y = q.y + uOY;
  float tph = TAU * uPh;

  float ph0 = twistAt(x);
  float cp0 = cos(ph0);
  if (uDbg > 0.5) return half4(half3(float3(max(cp0, 0.0), max(-cp0, 0.0), 0.0)), 1.0);
  float c0 = lineAt(x);
  float sc0 = widthAt(x) * cp0;
  float sg0 = sc0 >= 0.0 ? 1.0 : -1.0;
  float t0 = (c0 + sc0 - y) * sg0;

  // stateless fringe: a flow that loosens the dust (not the edge); whole cycles per loop
  // (it is zero where the sheet is edge-on: there the side the dust hangs on flips, and a warp that follows it would tear the glow)
  float loose = uOpt.y * smoothstep(3.0, 28.0, t0) * smoothstep(0.0, 10.0, abs(sc0));
  float fx = sin(0.036 * x + 0.022 * y + tph) + 0.6 * sin(0.014 * x - 0.043 * y - 2.0 * tph + 1.3);
  float fy = sin(0.033 * x - 0.026 * y + 2.0 * tph + 0.7) + 0.6 * sin(-0.016 * x - 0.037 * y + tph + 2.1);
  x += loose * 6.0 * fx;
  y += loose * 6.0 * fy;

  float3 hdr = float3(0.0);
  float3 colX = rampAt(x / 402.0);
  float cc = lineAt(x);
  float sc = widthAt(x) * cos(twistAt(x));
  float sg = sc >= 0.0 ? 1.0 : -1.0;
  float t = (cc + sc - y) * sg;
  float L = muA() * abs(sc);
  float sgF = 2.1;
  bool inBand = (t > -12.0) && (t < 9.0 * L + 60.0);

  // ---- sparks: 1 pt columns, 100 strata each ----
  float nA = 11.0;
  float r0 = 0.85;
  float3 dcol = float3(0.0);
  if (inBand) {
    for (int a = -1; a <= 1; a++) {
      float jc = floor(x) + float(a);
      float xj = jc + 0.5;
      float phj = twistAt(xj);
      float cpj = cos(phj);
      float spj = sin(phj);
      float cj = lineAt(xj);
      float cd = lineAt(xj + 0.5) - lineAt(xj - 0.5);
      float wj = widthAt(xj);
      float scj = wj * cpj;
      float estar = (cj + scj - y) * scj / (scj * scj + sgF * sgF);
      float pstar = 1.0 - exp(-max(estar, 0.0) / muA());
      float kstar = floor(nA * pstar);
      for (int b = -1; b <= 1; b++) {
        float kk = clamp(kstar + float(b), 0.0, nA - 1.0);
        float h1 = h21(float2(jc, kk));
        float h2 = h21(float2(jc + 311.0, kk + 17.0));
        float h3 = h21(float2(jc + 57.0, kk + 91.0));
        float h4 = h21(float2(jc + 713.0, kk + 5.0));
        float pk = (kk + 0.12 + 0.76 * h1) / nA;
        float e = -muA() * log(1.0 - pk);
        float xk = xj + (h2 - 0.5);
        float yk = cj + cd * (xk - xj) + scj - scj * e + (h3 - 0.5) * 2.0 * sgF;
        float zk = wj * spj * (e - 1.0);
        float rk = r0 + 0.012 * abs(zk);
        float dd = length(float2(x - xk, y - yk));
        float prof = exp(-sq(dd / rk) * 1.8);
        float tws = 0.65 + 0.35 * sin(TAU * (floor(2.0 + 4.0 * h3) * uPh + h1));
        float amp = (0.34 * (0.3 + 0.7 * h4) + (h4 > 0.86 ? 1.7 : 0.0)) * sq(r0 / rk) * tws;
        dcol += rampAt(xk / 402.0) * amp * prof;
      }
    }
  }
  // analytic density at this pixel (particles per pt^2) once many particles would overlap
  float rho = nA * lineProfile(t, max(L, 0.02), sgF);
  float cover = rho * 3.1416 * 0.55 * r0 * r0;
  float wA = smoothstep(0.7, 2.2, cover);
  float meanAmp = 0.34 * 0.65 + 0.14 * 1.7;
  // an edge-on stretch is where 40,000 particles pile onto one line: the unresolved ones brighten it (a face-on stretch keeps its sparks)
  float boost = 1.0 + 3.0 * (1.0 - smoothstep(1.5, 12.0, L));
  hdr += mix(dcol, colX * (rho * meanAmp * 0.52), wA) * (1.0 + 0.6 * (boost - 1.0));

  // ---- out-of-focus discs: 6 pt columns, 24 strata, from the lower edge up ----
  float nB = 10.0;
  if (inBand) {
    for (int a2 = -1; a2 <= 1; a2++) {
      float jc = floor(x / 6.0) + float(a2);
      float xj = jc * 6.0 + 3.0;
      float phj = twistAt(xj);
      float cpj = cos(phj);
      float spj = sin(phj);
      float cj = lineAt(xj);
      float cd = (lineAt(xj + 3.0) - lineAt(xj - 3.0)) / 6.0;
      float wj = widthAt(xj);
      float scj = wj * cpj;
      float vstar = (cj - y) * scj / (scj * scj + 6.76);
      float pstar = 1.0 / (1.0 + exp(-((vstar + 1.0) - 1.05) / 0.3));
      float kstar = floor(nB * pstar);
      for (int b2 = -2; b2 <= 2; b2++) {
        float kk = clamp(kstar + float(b2), 0.0, nB - 1.0);
        float h1 = h21(float2(jc + 1000.0, kk));
        float h2 = h21(float2(jc + 1311.0, kk + 17.0));
        float h3 = h21(float2(jc + 1057.0, kk + 91.0));
        float pk = (kk + 0.12 + 0.76 * h1) / nB;
        float v = max(-1.0 + 1.05 + 0.3 * log(pk / (1.0 - pk)), -0.85);
        float xk = xj + (h2 - 0.5) * 6.0;
        float yk = cj + cd * (xk - xj) - scj * v;
        float zk = wj * spj * v;
        float rk = 1.8 + 3.8 * h3 * h3 + 0.03 * abs(zk);
        float dd = length(float2(x - xk, y - yk));
        float disc = exp(-sq(dd / rk) * 1.7) + 0.18 * exp(-sq((dd - 0.8 * rk) / (0.3 * rk)));
        float clump = 0.08 + 1.8 * smoothstep(-0.3, 1.3, flowN(xk, yk, tph, 1.0));
        float amp = 0.6 * sq(2.6 / rk) * uOpt.x * (0.4 + h1) * clump * (0.6 + 1.5 * smoothstep(0.2, 0.55, xk / 402.0));
        hdr += rampAt(xk / 402.0) * amp * disc;
      }
    }
  }

  // ---- the lower edge: a string of beads ----
  float beads = 0.3 + 1.7 * sq(vn(x / 2.6 + 3.1 * floor(uPh * 4.0) * 0.0 + 11.0));
  hdr += colX * lineProfile(t, 1.0 + 0.07 * abs(sc), 0.9) * beads * 4.5 * boost;

  // ---- bloom: the same density, widened. Its offset toward the dust (the constants 1.5 and 6) must vanish where the sheet is
  // edge-on, or the glow jumps from one side of the line to the other at every crossing and shows as vertical blocks ----
  float kc = smoothstep(0.0, 8.0, abs(sc));
  hdr += colX * (lineProfile(t, L + 1.5 * kc, 6.0) * 0.55 + lineProfile(t, L * 1.5 + 6.0 * kc, 18.0) * 1.1) * uSheet.w * (1.0 + 0.35 * (boost - 1.0));

  hdr *= uSheet.z * (1.0 + 0.5 * uExtra.x * (1.0 - 2.0 * q.x / 402.0)) * smoothstep(2.0, 30.0, q.x) * (1.0 - smoothstep(372.0, 400.0, q.x));
  float3 lit = 1.0 - exp(-hdr * (1.0 + uOpt.w));
  // the strip's own top and bottom edges never show: the light is gone 14 pt before them (the loop and the finger keep it
  // clear of them anyway; this is the guard that makes the rectangle impossible to see)
  float stripH = uRes.y / uS;
  lit *= smoothstep(0.0, 14.0, q.y) * (1.0 - smoothstep(stripH - 14.0, stripH, q.y));
  float3 bg = float3(uExtra.y, uExtra.z, uExtra.w);
  float3 outc = bg + (1.0 - bg) * lit;
  float3 lin = pow(outc, float3(2.2));
  float3 p3 = float3(0.8225 * lin.r + 0.1774 * lin.g, 0.0332 * lin.r + 0.9669 * lin.g, 0.0171 * lin.r + 0.0724 * lin.g + 0.9108 * lin.b);
  float3 enc = pow(p3, float3(1.0 / 2.2));
  // one least-significant bit of dither, only where there is light: the dark bloom does not band and the page stays exact
  float dith = (h21(fc) - 0.5) / 255.0 * smoothstep(0.0, 0.01, max(lit.r, max(lit.g, lit.b)));
  return half4(half3(mix(outc, enc, uOpt.z) + dith), 1.0);
}

};

Scene load(device const float *u) {
    Scene s;
    s.uRes = float2(u[0], u[1]);
    s.uS = u[2];
    s.uOY = u[3];
    s.uPh = u[4];
    s.uLine = float4(u[5], u[6], u[7], u[8]);
    s.uSheet = float4(u[9], u[10], u[11], u[12]);
    s.uTwA = float4(u[13], u[14], u[15], u[16]);
    s.uTwB = float4(u[17], u[18], u[19], u[20]);
    s.uThink = float4(u[21], u[22], u[23], u[24]);
    s.uPul[0] = float4(u[25], u[26], u[27], u[28]);
    s.uPul[1] = float4(u[29], u[30], u[31], u[32]);
    s.uPul[2] = float4(u[33], u[34], u[35], u[36]);
    s.uPul[3] = float4(u[37], u[38], u[39], u[40]);
    s.uPul[4] = float4(u[41], u[42], u[43], u[44]);
    s.uPul[5] = float4(u[45], u[46], u[47], u[48]);
    s.uPul[6] = float4(u[49], u[50], u[51], u[52]);
    s.uPul[7] = float4(u[53], u[54], u[55], u[56]);
    s.uWin[0] = float4(u[57], u[58], u[59], u[60]);
    s.uWin[1] = float4(u[61], u[62], u[63], u[64]);
    s.uWinB[0] = float4(u[65], u[66], u[67], u[68]);
    s.uWinB[1] = float4(u[69], u[70], u[71], u[72]);
    s.uBump[0] = float4(u[73], u[74], u[75], u[76]);
    s.uBump[1] = float4(u[77], u[78], u[79], u[80]);
    s.uOpt = float4(u[81], u[82], u[83], u[84]);
    s.uExtra = float4(u[85], u[86], u[87], u[88]);
    s.uC0 = float3(u[89], u[90], u[91]);
    s.uC1 = float3(u[92], u[93], u[94]);
    s.uC2 = float3(u[95], u[96], u[97]);
    s.uDbg = u[98];
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

} // namespace ParticleRibbonVoiceShader

// Geometry is in screen points, as in the RN high-quality canvas (q = 1).
[[ stitchable ]] half4 particleRibbonVoice(float2 position, half4 color,
    device const float *uniforms, int uniformCount) {
    if (uniformCount < 99) return half4(0.0h);
    ParticleRibbonVoiceShader::Scene s = ParticleRibbonVoiceShader::load(uniforms);
    return ParticleRibbonVoiceShader::displaySRGB(s.render(position));
}
