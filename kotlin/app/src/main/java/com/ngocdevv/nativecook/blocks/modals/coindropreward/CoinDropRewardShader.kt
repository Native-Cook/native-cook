package com.ngocdevv.nativecook.blocks.modals.coindropreward

/** The reference SDF in AGSL. All geometry remains in dp; main converts device pixels.
 * Android's default canvas is sRGB, so the iOS Skia Display-P3 output conversion is not applied.
 */
internal const val COIN_DROP_REWARD_AGSL = """
uniform float4 uObj;    // centre x, y (pt), size (pt, the unit radius), opacity
uniform float4 uShape;  // drop 0…1 (coin → drop), turn (rad, about the vertical), trail angle (rad), stretch 0…1
uniform float uDensity;
uniform float4 uLive;   // trailing droplet 0…1, pixel ratio, -, -

const float PI = 3.14159265;
float sq(float v) { return v*v; }
float smin(float a, float b, float k) { float h = clamp(0.5 + 0.5*(b - a)/k, 0.0, 1.0); return mix(b, a, h) - k*h*(1.0 - h); }
float3 ry(float3 p, float a) { float c = cos(a), s = sin(a); return float3(c*p.x + s*p.z, p.y, -s*p.x + c*p.z); }
float3 rz(float3 p, float a) { float c = cos(a), s = sin(a); return float3(c*p.x - s*p.y, s*p.x + c*p.y, p.z); }
float3 rx(float3 p, float a) { float c = cos(a), s = sin(a); return float3(p.x, c*p.y - s*p.z, s*p.y + c*p.z); }
float cap2(float2 p, float2 a, float2 b) { float2 pa = p - a, ba = b - a; float h = clamp(dot(pa, ba)/dot(ba, ba), 0.0, 1.0); return length(pa - ba*h); }
float ell(float3 p, float3 r) { float k0 = length(p/r); float k1 = length(p/(r*r)); return k0*(k0 - 1.0)/max(k1, 1e-5); }

// the letter P on the coin's face, as a 2D distance (0 on its centre line)
float letterP(float2 q) {
  float stem = cap2(q, float2(-0.2, -0.42), float2(-0.2, 0.4));
  float2 b = q - float2(0.0, 0.17);
  float bowl = b.x >= 0.0 ? abs(length(b) - 0.23) : 1e3;
  // the bowl's straight top and bottom bars back to the stem
  bowl = min(bowl, cap2(q, float2(-0.2, 0.4), float2(0.0, 0.4)));
  bowl = min(bowl, cap2(q, float2(-0.2, -0.06), float2(0.0, -0.06)));
  return min(stem, bowl);
}

// coin: a bevelled disc facing the viewer; returns distance, and in letter whether it is the P
float coin(float3 p, out float letter) {
  float r = length(p.xy);
  float2 w = float2(r - 0.86, abs(p.z) - 0.1);
  float d = min(max(w.x, w.y), 0.0) + length(max(w, 0.0)) - 0.14;
  // a sunken face inside a raised rim
  float face = smoothstep(0.62, 0.7, r);
  d += 0.035*(1.0 - face)*step(0.0, p.z);
  // the raised P
  float lp = letterP(p.xy*1.05) - 0.085;
  float relief = length(float2(max(lp, 0.0), max(p.z - 0.24 + 0.035, 0.0))) + min(max(lp, p.z - 0.24 + 0.035), 0.0) - 0.035;
  relief = max(relief, -p.z);
  letter = relief < d ? 1.0 : 0.0;
  return min(d, relief);
}

// drop: a round head with its tail along +y (rotated so the tail trails the motion)
float drop(float3 p, float stretch) {
  float head = length(p - float3(0.0, -0.18, 0.0)) - 0.66;
  float tip = 0.62 + 0.55*stretch;
  float3 a = float3(0.0, -0.18, 0.0), b = float3(0.0, tip, 0.0);
  float3 pa = p - a, ba = b - a; float h = clamp(dot(pa, ba)/dot(ba, ba), 0.0, 1.0);
  float tail = length(pa - ba*h) - mix(0.5, 0.04, h);
  return smin(head, tail, 0.2);
}

float sceneD(float3 p, out float letter) {
  float m = clamp(uShape.x, 0.0, 1.0);
  float3 q = ry(p, uShape.y);
  q = rx(q, -0.12);
  float c = coin(q, letter);
  if (m < 0.001) return c*0.9;
  float3 dp = rz(p, uShape.z);
  float dr = drop(dp, uShape.w);
  // the trailing droplet, joined by a neck that thins as it pulls away
  float tr = uLive.x;
  if (tr > 0.0) {
    float gap = 0.9 + 1.1*tr;
    float s = length(dp - float3(0.0, gap, 0.0)) - 0.22*(1.0 - 0.4*tr);
    dr = smin(dr, s, 0.32*(1.0 - tr) + 0.01);
  }
  if (m > 0.999) { letter = 0.0; return dr*0.9; }
  letter *= 1.0 - smoothstep(0.0, 0.35, m);
  return (mix(c, dr, m) - 0.12*sin(PI*m))*0.9;
}
float sd(float3 p) { float l; return sceneD(p, l); }

float3 toP3(float3 c) {
  float3 l = pow(max(c, float3(0.0)), float3(2.2));
  float3 m = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(max(m, float3(0.0)), float3(1.0/2.2));
}

half4 main(float2 pixelPosition) {
  float2 xy = pixelPosition/uDensity;
  float S = uObj.z;
  if (uObj.w <= 0.0 || S < 0.5) return half4(0.0);
  float2 uv = (xy - uObj.xy)/S;
  uv.y = -uv.y;
  float R = 1.15 + 1.6*uShape.w*uShape.x + 2.2*uLive.x;
  float b2 = R*R - dot(uv, uv);
  if (b2 <= 0.0) return half4(0.0);
  float pxo = 1.0/(uLive.y*S);
  float zEnd = -min(sqrt(b2), 1.3);
  float z = -zEnd;
  float best = 1e4, bestZ = z;
  bool hit = false;
  for (int i = 0; i < 56; i++) {
    float d = sd(float3(uv, z));
    if (d < best) { best = d; bestZ = z; }
    if (d < 0.3*pxo) { hit = true; break; }
    z -= max(d*0.9, 0.35*pxo);
    if (z < zEnd) break;
  }
  if (hit) { for (int i = 0; i < 2; i++) bestZ -= sd(float3(uv, bestZ)); }
  float cover = (hit ? 1.0 : 1.0 - smoothstep(0.3*pxo, 1.3*pxo, best))*uObj.w;
  if (cover <= 0.0) return half4(0.0);
  float3 p = float3(uv, bestZ);
  float e = max(0.003, 1.2*pxo);
  const float2 k = float2(1.0, -1.0);
  float3 n = normalize(k.xyy*sd(p + k.xyy*e) + k.yyx*sd(p + k.yyx*e) + k.yxy*sd(p + k.yxy*e) + k.xxx*sd(p + k.xxx*e));
  float letter;
  sceneD(p, letter);
  float m = clamp(uShape.x, 0.0, 1.0);
  float3 V = float3(0.0, 0.0, 1.0);
  float ndv = max(dot(n, V), 0.0);
  float3 key = normalize(float3(-0.5, 0.75, 0.75));
  // a deep blue body that lightens toward the light, white for the letter
  float3 deep = float3(0.08, 0.3, 0.95);
  float3 light = float3(0.42, 0.72, 1.0);
  float lam = clamp((dot(n, key) + 0.3)/1.3, 0.0, 1.0);
  float3 body = mix(deep, light, lam*0.8 + 0.2*(1.0 - ndv));
  // the liquid is more see-through: its centre takes a glow from inside
  body = mix(body, mix(float3(0.2, 0.55, 1.0), float3(0.55, 0.85, 1.0), pow(ndv, 2.0)), m*0.6);
  float3 col = mix(body, float3(0.97, 0.98, 1.0)*(0.75 + 0.3*lam), letter);
  // fresnel rim and two sharp highlights (glass and wet metal)
  float fr = pow(1.0 - ndv, 3.0);
  col += float3(0.7, 0.85, 1.0)*fr*(0.35 + 0.35*m);
  float3 H = normalize(key + V);
  col += float3(1.0)*(0.5*pow(max(dot(n, H), 0.0), 60.0) + 0.9*pow(max(dot(n, H), 0.0), 400.0));
  float3 H2 = normalize(normalize(float3(0.7, -0.4, 0.6)) + V);
  col += float3(0.6, 0.8, 1.0)*0.35*pow(max(dot(n, H2), 0.0), 80.0);
  float3 ex = max(col - 0.9, 0.0);
  col = min(col, float3(0.9)) + ex/(1.0 + ex/0.1);
  return half4(half3(clamp(col, 0.0, 1.0)*cover), half(cover));
}
"""
