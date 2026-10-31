#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;
namespace ClayCategoryTabsShader {
constant float PI = 3.14159265;
constant float3 WALL = float3(1.0, 0.9, 0.78);
constant float3 ROOF = float3(0.96, 0.36, 0.28);
constant float3 BRICK = float3(0.62, 0.28, 0.22);
constant float3 DOOR = float3(0.16, 0.6, 0.62);
constant float3 LAMP = float3(1.0, 0.82, 0.4);
constant float3 SMOKE = float3(0.86, 0.86, 0.9);
constant float3 SILK1 = float3(1.0, 0.42, 0.2);
constant float3 SILK2 = float3(1.0, 0.84, 0.3);
constant float3 WICKER = float3(0.66, 0.42, 0.22);
constant float3 ROPE = float3(0.3, 0.22, 0.16);
constant float3 BRASS = float3(1.0, 0.74, 0.28);
constant float3 WALNUT = float3(0.34, 0.2, 0.14);
struct ClayCategoryTabsPart { float d; float3 rgb; float gloss; float glow; };
struct ClayCategoryTabsRenderer {
float4 uObj;
float4 uMorph;
float4 uGhostX;
float4 uGhostA;
float4 uLive;


float ClayCategoryTabsSq(float v) { return v*v; }
float ClayCategoryTabsSmin(float a, float b, float k) { float h = clamp(0.5 + 0.5*(b - a)/k, 0.0, 1.0); return mix(b, a, h) - k*h*(1.0 - h); }
float ClayCategoryTabsSmax(float a, float b, float k) { float h = clamp(0.5 - 0.5*(b - a)/k, 0.0, 1.0); return mix(b, a, h) + k*h*(1.0 - h); }
float3 ClayCategoryTabsRx(float3 p, float a) { float c = cos(a), s = sin(a); return float3(p.x, c*p.y - s*p.z, s*p.y + c*p.z); }
float3 ClayCategoryTabsRy(float3 p, float a) { float c = cos(a), s = sin(a); return float3(c*p.x + s*p.z, p.y, -s*p.x + c*p.z); }
float3 ClayCategoryTabsRz(float3 p, float a) { float c = cos(a), s = sin(a); return float3(c*p.x - s*p.y, s*p.x + c*p.y, p.z); }
float ClayCategoryTabsEll(float3 p, float3 r) { float k0 = length(p/r); float k1 = length(p/(r*r)); return k0*(k0 - 1.0)/max(k1, 1e-5); }
float ClayCategoryTabsBox(float3 p, float3 e, float r) { float3 q = abs(p) - e + r; return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0) - r; }
float ClayCategoryTabsCap(float3 p, float3 a, float3 b, float r) { float3 pa = p - a, ba = b - a; float h = clamp(dot(pa, ba)/dot(ba, ba), 0.0, 1.0); return length(pa - ba*h) - r; }
float ClayCategoryTabsRcone(float3 p, float3 a, float3 b, float ra, float rb) {
  float3 pa = p - a, ba = b - a; float h = clamp(dot(pa, ba)/dot(ba, ba), 0.0, 1.0);
  return length(pa - ba*h) - mix(ra, rb, h);
}
// a cylinder standing on y, radius r, half height h, edges rounded by e
float ClayCategoryTabsCyl(float3 p, float r, float h, float e) { float2 d = float2(length(p.xz) - r + e, abs(p.y) - h + e); return min(max(d.x, d.y), 0.0) + length(max(d, 0.0)) - e; }


ClayCategoryTabsPart ClayCategoryTabsP(float d, float3 rgb, float gloss, float glow) { ClayCategoryTabsPart p; p.d = d; p.rgb = rgb; p.gloss = gloss; p.glow = glow; return p; }
ClayCategoryTabsPart ClayCategoryTabsNearer(ClayCategoryTabsPart a, ClayCategoryTabsPart b) { return a.d < b.d ? a : b; }


ClayCategoryTabsPart ClayCategoryTabsHouse(float3 p, float t) {
  float walls = ClayCategoryTabsBox(p - float3(0.0, -0.34, 0.0), float3(0.6, 0.44, 0.46), 0.1);
  // the roof: a triangular prism with soft edges
  float2 q = float2(abs(p.x), p.y - 0.06);
  float2 n = normalize(float2(0.66, 0.86));
  float d2 = max(dot(q - float2(0.0, 0.66), n), -q.y);
  float2 w = float2(d2 + 0.07, abs(p.z) - 0.56 + 0.07);
  float roof = min(max(w.x, w.y), 0.0) + length(max(w, 0.0)) - 0.07;
  float chim = ClayCategoryTabsBox(p - float3(0.36, 0.5, -0.12), float3(0.1, 0.22, 0.1), 0.035);
  ClayCategoryTabsPart h = ClayCategoryTabsP(walls, WALL, 0.25, 0.0);
  h = ClayCategoryTabsNearer(h, ClayCategoryTabsP(roof, ROOF, 0.45, 0.0));
  h = ClayCategoryTabsNearer(h, ClayCategoryTabsP(chim, BRICK, 0.3, 0.0));
  h = ClayCategoryTabsNearer(h, ClayCategoryTabsP(ClayCategoryTabsBox(p - float3(0.0, -0.53, 0.47), float3(0.15, 0.25, 0.04), 0.05), DOOR, 0.5, 0.0));
  float3 wq = float3(abs(p.x), p.y, p.z) - float3(0.38, -0.26, 0.47);
  h = ClayCategoryTabsNearer(h, ClayCategoryTabsP(ClayCategoryTabsBox(wq, float3(0.11, 0.11, 0.03), 0.04), LAMP, 0.6, 1.0));
  h = ClayCategoryTabsNearer(h, ClayCategoryTabsP(length(p - float3(0.0, 0.28, 0.55)) - 0.11, LAMP, 0.6, 1.0));
  // smoke: three puffs rising from the chimney, swelling as they fade
  for (int i = 0; i < 3; i++) {
    float u = fract(t/2.25 + float(i)/3.0);
    float3 c = float3(0.38 + 0.18*u, 0.8 + 0.62*u, -0.12);
    float r = 0.07 + 0.09*u;
    float puff = length(p - c) - r*(1.0 - smoothstep(0.7, 1.0, u));
    if (u > 0.04) h = ClayCategoryTabsNearer(h, ClayCategoryTabsP(puff, SMOKE, 0.1, 0.0));
  }
  return h;
}

ClayCategoryTabsPart ClayCategoryTabsBalloon(float3 p) {
  float3 q = p - float3(0.0, 0.2, 0.0);
  float env = ClayCategoryTabsEll(q, float3(0.74, 0.76, 0.74));
  float neck = ClayCategoryTabsRcone(p, float3(0.0, -0.22, 0.0), float3(0.0, -0.54, 0.0), 0.5, 0.14);
  float d = ClayCategoryTabsSmin(env, neck, 0.16);
  float a = atan2(q.z, q.x);
  float band = smoothstep(-0.25, 0.25, sin(a*5.0));
  ClayCategoryTabsPart b = ClayCategoryTabsP(d, mix(SILK1, SILK2, band), 0.55, 0.0);
  // a cream crown on top
  if (q.y > 0.58) b.rgb = mix(b.rgb, WALL, smoothstep(0.58, 0.64, q.y));
  b = ClayCategoryTabsNearer(b, ClayCategoryTabsP(ClayCategoryTabsBox(p - float3(0.0, -0.86, 0.0), float3(0.19, 0.13, 0.19), 0.05), WICKER, 0.2, 0.0));
  float3 rp = float3(abs(p.x), p.y, abs(p.z));
  b = ClayCategoryTabsNearer(b, ClayCategoryTabsP(ClayCategoryTabsCap(rp, float3(0.11, -0.52, 0.11), float3(0.15, -0.75, 0.15), 0.022), ROPE, 0.1, 0.0));
  // the burner's glow in the mouth
  b = ClayCategoryTabsNearer(b, ClayCategoryTabsP(length(p - float3(0.0, -0.56, 0.0)) - 0.07, LAMP, 0.4, 1.0));
  return b;
}

ClayCategoryTabsPart ClayCategoryTabsBell(float3 p, float knob) {
  float base = ClayCategoryTabsCyl(p - float3(0.0, -0.66, 0.0), 0.86, 0.08, 0.05);
  float dome = ClayCategoryTabsEll(p - float3(0.0, -0.56, 0.0), float3(0.7, 0.76, 0.7));
  dome = ClayCategoryTabsSmax(dome, -(p.y + 0.56), 0.02);
  float rim = length(float2(length(p.xz) - 0.7, p.y + 0.56)) - 0.05;
  dome = ClayCategoryTabsSmin(dome, rim, 0.04);
  float stem = ClayCategoryTabsCap(p, float3(0.0, 0.16, 0.0), float3(0.0, 0.34 - 0.09*knob, 0.0), 0.055);
  float ball = length(p - float3(0.0, 0.4 - 0.09*knob, 0.0)) - 0.12;
  ClayCategoryTabsPart b = ClayCategoryTabsP(base, WALNUT, 0.45, 0.0);
  b = ClayCategoryTabsNearer(b, ClayCategoryTabsP(ClayCategoryTabsSmin(dome, stem, 0.05), BRASS, 1.0, 0.0));
  b = ClayCategoryTabsNearer(b, ClayCategoryTabsP(ball, BRASS, 1.0, 0.0));
  return b;
}

ClayCategoryTabsPart ClayCategoryTabsShape(float3 p, float k, float t, float knob) {
  if (k < 0.5) return ClayCategoryTabsHouse(p, t);
  if (k < 1.5) return ClayCategoryTabsBalloon(p);
  return ClayCategoryTabsBell(p, knob);
}

// tf: yaw, squash, wobble, knob
float3 ClayCategoryTabsToObject(float3 p, float4 tf) {
  p.y /= 1.0 - 0.22*tf.y;
  p.xz /= 1.0 + 0.14*tf.y;
  p.y += 0.66; p = ClayCategoryTabsRz(p, tf.z); p.y -= 0.66; // the ClayCategoryTabsBell rocks on its base
  p = ClayCategoryTabsRy(p, tf.x);
  p = ClayCategoryTabsRx(p, -0.3);
  return p;
}

float ClayCategoryTabsSceneD(float3 p, float r, float4 tf, float t) {
  float lip = min(1.0 - 0.22*tf.y, 1.0 + 0.14*tf.y);
  float3 q = ClayCategoryTabsToObject(p, tf);
  float rc = clamp(r, 0.0, 2.0);
  float a = min(floor(rc), 1.0);
  float w = smoothstep(0.12, 0.88, rc - a);
  float d;
  if (w < 0.001) d = ClayCategoryTabsShape(q, a, t, tf.w).d;
  else if (w > 0.999) d = ClayCategoryTabsShape(q, a + 1.0, t, tf.w).d;
  else d = mix(ClayCategoryTabsShape(q, a, t, tf.w).d, ClayCategoryTabsShape(q, a + 1.0, t, tf.w).d, w) - 0.12*sin(PI*w);
  return d*lip*0.9;
}

float3 ClayCategoryTabsToLab(float3 c) {
  c = pow(max(c, float3(0.0)), float3(2.2));
  float3 l = float3(dot(c, float3(0.412221, 0.536333, 0.051446)), dot(c, float3(0.211903, 0.680700, 0.107397)), dot(c, float3(0.088302, 0.281719, 0.629979)));
  l = pow(max(l, float3(0.0)), float3(0.333333));
  return float3(dot(l, float3(0.210454, 0.793618, -0.004072)), dot(l, float3(1.977998, -2.428592, 0.450594)), dot(l, float3(0.025904, 0.782772, -0.808676)));
}
float3 ClayCategoryTabsFromLab(float3 c) {
  float3 l = float3(c.x + 0.396338*c.y + 0.215804*c.z, c.x - 0.105561*c.y - 0.063854*c.z, c.x - 0.089484*c.y - 1.291486*c.z);
  l = l*l*l;
  float3 rgb = float3(dot(l, float3(4.076742, -3.307712, 0.230970)), dot(l, float3(-1.268438, 2.609757, -0.341319)), dot(l, float3(-0.004196, -0.703419, 1.707615)));
  return pow(max(rgb, float3(0.0)), float3(1.0/2.2));
}

ClayCategoryTabsPart ClayCategoryTabsSurfaceAt(float3 p, float r, float4 tf, float t) {
  float3 q = ClayCategoryTabsToObject(p, tf);
  float rc = clamp(r, 0.0, 2.0);
  float a = min(floor(rc), 1.0);
  float w = smoothstep(0.12, 0.88, rc - a);
  if (w < 0.001) return ClayCategoryTabsShape(q, a, t, tf.w);
  if (w > 0.999) return ClayCategoryTabsShape(q, a + 1.0, t, tf.w);
  ClayCategoryTabsPart x = ClayCategoryTabsShape(q, a, t, tf.w), y = ClayCategoryTabsShape(q, a + 1.0, t, tf.w);
  return ClayCategoryTabsP(0.0, ClayCategoryTabsFromLab(mix(ClayCategoryTabsToLab(x.rgb), ClayCategoryTabsToLab(y.rgb), w)), mix(x.gloss, y.gloss, w), mix(x.glow, y.glow, w));
}

float3 ClayCategoryTabsToP3(float3 c) {
  float3 l = pow(max(c, float3(0.0)), float3(2.2));
  float3 m = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(max(m, float3(0.0)), float3(1.0/2.2));
}
float4 ClayCategoryTabsOver(float4 under, float3 c, float a) { return float4(c*a + under.rgb*(1.0 - a), a + under.a*(1.0 - a)); }

// one ClayCategoryTabsIcon: centre c, unit size S, category r, transform tf; ghost 1 = grey matte clay
float4 ClayCategoryTabsIcon(float4 acc, float2 xy, float2 c, float S, float r, float4 tf, float ghost, float alpha, float t) {
  if (alpha <= 0.0 || S < 1.0) return acc;
  float2 uv = (xy - c)/S;
  uv.y = -uv.y;
  float b2 = 1.5*1.5 - dot(uv, uv);
  if (b2 <= 0.0) return acc;
  float pxo = 1.0/(uLive.y*S);
  float zEnd = -sqrt(b2);
  float z = -zEnd;
  float best = 1e4, bestZ = z;
  bool hit = false;
  for (int i = 0; i < 64; i++) {
    float d = ClayCategoryTabsSceneD(float3(uv, z), r, tf, t);
    if (d < best) { best = d; bestZ = z; }
    if (d < 0.3*pxo) { hit = true; break; }
    z -= max(d*0.9, 0.35*pxo);
    if (z < zEnd) break;
  }
  if (hit) { for (int i = 0; i < 2; i++) bestZ -= ClayCategoryTabsSceneD(float3(uv, bestZ), r, tf, t); }
  float cover = (hit ? 1.0 : 1.0 - smoothstep(0.3*pxo, 1.3*pxo, best))*alpha;
  if (cover <= 0.0) return acc;
  float3 p = float3(uv, bestZ);
  float e = max(0.004, 1.2*pxo);
  const float2 k = float2(1.0, -1.0);
  float3 n = normalize(k.xyy*ClayCategoryTabsSceneD(p + k.xyy*e, r, tf, t) + k.yyx*ClayCategoryTabsSceneD(p + k.yyx*e, r, tf, t) + k.yxy*ClayCategoryTabsSceneD(p + k.yxy*e, r, tf, t) + k.xxx*ClayCategoryTabsSceneD(p + k.xxx*e, r, tf, t));
  ClayCategoryTabsPart s = ClayCategoryTabsSurfaceAt(p, r, tf, t);
  float ao = 0.0;
  for (int i = 0; i < 3; i++) {
    float h = 0.035 + float(i)*0.07;
    ao += max(0.0, h - ClayCategoryTabsSceneD(p + n*h, r, tf, t))/h/(1.0 + float(i));
  }
  ao = clamp(1.0 - ao*0.85, 0.45, 1.0);
  float3 base = s.rgb;
  float gloss = s.gloss;
  float glow = s.glow;
  if (ghost > 0.5) {
    float l = dot(base, float3(0.3, 0.55, 0.15));
    base = float3(0.42 + 0.3*l);
    gloss *= 0.3;
    glow = 0.0;
  }
  float3 V = float3(0.0, 0.0, 1.0);
  float3 key = normalize(float3(-0.55, 0.75, 0.8));
  float nl = clamp((dot(n, key) + 0.4)/1.4, 0.0, 1.0);
  float3 shade = mix(base*float3(0.6, 0.52, 0.72), base, 0.3);
  float3 col = mix(shade, base, nl)*(0.62 + 0.42*nl)*ao;
  col += base*float3(0.1, 0.1, 0.18)*max(dot(n, normalize(float3(0.8, -0.1, 0.5))), 0.0);
  float hn = max(dot(n, normalize(key + V)), 0.0);
  col += float3(1.0, 0.96, 0.9)*((0.08 + 0.22*gloss)*pow(hn, 16.0) + 0.45*gloss*pow(hn, 110.0))*ao;
  float rim = pow(1.0 - max(dot(n, V), 0.0), 3.0);
  col += float3(0.55, 0.62, 1.0)*rim*(ghost > 0.5 ? 0.12 : 0.3);
  col += base*glow*0.45;
  float3 ex = max(col - 0.86, 0.0);
  col = min(col, float3(0.86)) + ex/(1.0 + ex/0.14);
  return ClayCategoryTabsOver(acc, col, cover);
}

half4 ClayCategoryTabsMain(float2 xy) {
  float t = uLive.x;
  float4 acc = float4(0.0);
  float4 still = float4(0.45, 0.0, 0.0, 0.0);
  float gs = uGhostA.w;
  acc = ClayCategoryTabsIcon(acc, xy, float2(uGhostX.x, uGhostX.w), gs, 0.0, still, 1.0, uGhostA.x, t);
  acc = ClayCategoryTabsIcon(acc, xy, float2(uGhostX.y, uGhostX.w), gs, 1.0, still, 1.0, uGhostA.y, t);
  acc = ClayCategoryTabsIcon(acc, xy, float2(uGhostX.z, uGhostX.w), gs, 2.0, still, 1.0, uGhostA.z, t);
  // a soft shadow on the stage under the lit object
  float2 sc = float2(uObj.x, uGhostX.w + 0.95*uObj.z*0.9);
  float2 sd = (xy - sc)/(uObj.z*float2(0.75, 0.13));
  acc = ClayCategoryTabsOver(acc, float3(0.0), 0.35*exp(-dot(sd, sd)));
  acc = ClayCategoryTabsIcon(acc, xy, uObj.xy, uObj.z, uMorph.x, float4(uMorph.y, uObj.w, uMorph.z, uMorph.w), 0.0, 1.0, t);
  if (acc.a <= 0.0) return half4(0.0);
  float3 rgb = ClayCategoryTabsToP3(clamp(acc.rgb/acc.a, 0.0, 1.0));
  return half4(half3(rgb*acc.a), half(acc.a));
}

};
}
[[ stitchable ]] half4 ClayCategoryTabsMain(float2 position, float4 obj, float4 morph, float4 ghostX, float4 ghostA, float4 live) {
  ClayCategoryTabsShader::ClayCategoryTabsRenderer renderer {obj, morph, ghostX, ghostA, live};
  return renderer.ClayCategoryTabsMain(position);
}
