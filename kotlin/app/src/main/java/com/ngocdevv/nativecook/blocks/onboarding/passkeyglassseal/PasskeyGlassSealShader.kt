package com.ngocdevv.nativecook.blocks.onboarding.passkeyglassseal

internal val PasskeyGlassSealShader = """
uniform float4 uView;
uniform float4 uPrint;
uniform float4 uGlow;
uniform float4 uKey;
struct PasskeyGlassSealPx { float3 col; float a; };
const float PI = 3.14159265;
float passkeyGlassSealSq(float v) { return v * v; }
float3 passkeyGlassSealLin(float3 c) { return pow(c, float3(2.2)); }
float passkeyGlassSealEase(float x) { float c = clamp(x, 0.0, 1.0); return c * c * (3.0 - 2.0 * c); }
float2x2 passkeyGlassSealRot(float a) { float c = cos(a); float s = sin(a); return float2x2(c, s, -s, c); }

/* ------------------------------------------------------------------ the key (pt, y down, from the print's centre) */

float passkeyGlassSealSdBox(float2 p, float2 b) { float2 q = abs(p) - b; return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0); }
float passkeyGlassSealSdKey2(float2 p) {
  float ring = abs(length(p - float2(-50.0, 0.0)) - 17.5) - 6.5;
  float dd = min(ring, passkeyGlassSealSdBox(p - float2(20.0, 0.0), float2(46.0, 6.5)));
  dd = min(dd, passkeyGlassSealSdBox(p - float2(58.0, 11.0), float2(4.0, 5.0)));
  dd = min(dd, passkeyGlassSealSdBox(p - float2(44.0, 9.5), float2(4.0, 3.5)));
  dd = min(dd, passkeyGlassSealSdBox(p - float2(30.5, 12.0), float2(3.5, 6.0)));
  return dd;
}
float passkeyGlassSealSdKey(float3 p) {
  float d2 = passkeyGlassSealSdKey2(p.xy) + 1.6;
  float2 w = float2(d2, abs(p.z) - 2.4);
  return min(max(w.x, w.y), 0.0) + length(max(w, float2(0.0))) - 1.6;
}
// the key in the card's frame: tilted for depth, turned about its shaft (its x axis) by the lock
float3 passkeyGlassSealKeyLocal(float3 p) {
  float turn = uKey.z * PI * 0.3;
  float3 q = p;
  float2 xz = passkeyGlassSealRot(0.32) * q.xz;
  q = float3(xz.x, q.y, xz.y);
  float2 yz = passkeyGlassSealRot(-turn) * q.yz;
  q = float3(q.x, yz.x, yz.y);
  return q;
}
float passkeyGlassSealKeyD(float3 p) { return passkeyGlassSealSdKey(passkeyGlassSealKeyLocal(p)); }
float3 passkeyGlassSealKeyN(float3 p) {
  float e = 0.08;
  float3 k1 = float3(1.0, -1.0, -1.0); float3 k2 = float3(-1.0, -1.0, 1.0); float3 k3 = float3(-1.0, 1.0, -1.0); float3 k4 = float3(1.0, 1.0, 1.0);
  return normalize(k1 * passkeyGlassSealKeyD(p + k1 * e) + k2 * passkeyGlassSealKeyD(p + k2 * e) + k3 * passkeyGlassSealKeyD(p + k3 * e) + k4 * passkeyGlassSealKeyD(p + k4 * e));
}
float3 passkeyGlassSealStudio(float3 r) {
  // a warm room: the page's rose below, a wide soft box overhead, a hot rim light from the right
  float3 c = mix(passkeyGlassSealLin(float3(0.36, 0.2, 0.32)), passkeyGlassSealLin(float3(0.95, 0.72, 0.8)), smoothstep(-0.6, 0.7, -r.y));
  c += float3(1.0, 0.95, 0.9) * 2.6 * smoothstep(0.55, 0.8, -r.y) * smoothstep(-0.9, -0.2, r.z) * (1.0 - smoothstep(0.5, 0.9, abs(r.x)));
  c += float3(1.0, 0.62, 0.42) * 2.0 * smoothstep(0.78, 0.95, dot(r, normalize(float3(0.85, 0.1, 0.5))));
  return c;
}



PasskeyGlassSealPx passkeyGlassSealKeyPx(float2 s) {
  PasskeyGlassSealPx o;
  o.col = float3(0.0);
  o.a = 0.0;
  float g = uKey.y;
  if (g <= 0.0) { return o; }
  float sc = uPrint.z;
  float2 p2 = (s - uPrint.xy) / sc;
  if (abs(p2.x) > 85.0 || abs(p2.y) > 40.0) { return o; }
  float3 ro = float3(p2, 30.0);
  float3 rd = float3(0.0, 0.0, -1.0);
  float t = 0.0;
  bool hit = false;
  for (int i = 0; i < 40; i++) {
    float dd = passkeyGlassSealKeyD(ro + rd * t);
    if (dd < 0.02) { hit = true; break; }
    t += dd * 0.85;
    if (t > 60.0) { break; }
  }
  if (!hit) { return o; }
  float3 p = ro + rd * t;
  float3 n = passkeyGlassSealKeyN(p);
  float3 v = float3(n.x, -n.y, n.z); // y up for the room
  float cosv = clamp(n.z, 0.0, 1.0);
  float fr = 0.05 + 0.95 * pow(1.0 - cosv, 4.0);
  float3 refl = passkeyGlassSealStudio(reflect(float3(0.0, 0.0, -1.0), float3(v.x, v.y, v.z)));
  // rose-gold glass: the body tinted where it is thick, the room seen in its faces
  float3 body = passkeyGlassSealLin(float3(1.0, 0.7, 0.6)) * (0.16 + 0.22 * cosv);
  float3 c = body * (1.0 - fr) + refl * fr * 1.3;
  // a streak of the soft box across its faces
  c += float3(1.0, 0.96, 0.92) * 1.4 * pow(max(dot(reflect(normalize(float3(0.3, -0.8, -0.5)), float3(v.x, v.y, v.z)), float3(0.0, 0.0, 1.0)), 0.0), 24.0);
  c += passkeyGlassSealLin(float3(1.0, 0.85, 0.75)) * pow(1.0 - cosv, 2.0) * 0.5;
  o.col = c;
  o.a = g * (0.82 + 0.18 * fr);
  return o;
}

float3 passkeyGlassSealEncode(float3 c) { return pow(clamp(c / (float3(1.0) + max(c - float3(0.85), float3(0.0)) * 0.6), float3(0.0), float3(1.0)), float3(1.0 / 2.2)); }

half4 main(float2 pixel) {
  float2 s = pixel;
  float3 col = float3(0.0);
  float a = 0.0;
  // the scan line: a hot core, a soft orange glow, fading at its ends
  float sw = uGlow.y;
  if (sw > 0.0) {
    float dy = s.y - uPrint.w;
    float ends = smoothstep(18.0, 40.0, s.x) * (1.0 - smoothstep(uView.x - 40.0, uView.x - 18.0, s.x));
    float core = exp(-passkeyGlassSealSq(dy / 1.3)) * ends;
    float glow = exp(-passkeyGlassSealSq(dy / 4.0)) * ends * 0.6;
    float3 c = passkeyGlassSealLin(float3(1.0, 0.5, 0.3)) * glow + passkeyGlassSealLin(float3(1.0, 0.66, 0.46)) * core * 1.2;
    float k = (glow + core) * sw;
    col += c * sw;
    a += clamp(k, 0.0, 1.0) * 0.9;
  }
  // a warm bloom round the lit print, and round the key
  float lit = uGlow.x * (1.0 - uKey.x * 0.6);
  if (lit > 0.0) {
    float2 q = (s - uPrint.xy - float2(0.0, -2.0) * uPrint.z) / (uPrint.z * 70.0);
    float b = exp(-dot(q, q) * 1.6) * lit * 0.3;
    col += passkeyGlassSealLin(float3(1.0, 0.6, 0.45)) * b;
    a += b * 0.6;
  }
  PasskeyGlassSealPx k = passkeyGlassSealKeyPx(s);
  col = k.col * k.a + col * (1.0 - k.a);
  a = k.a + a * (1.0 - k.a);
  if (a <= 0.0001) { return half4(0.0); }
  return half4(passkeyGlassSealEncode(col / a) * a, a);
}


""".trimIndent()
