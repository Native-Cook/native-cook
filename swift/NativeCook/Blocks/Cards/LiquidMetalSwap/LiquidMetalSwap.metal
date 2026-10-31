#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

namespace LiquidMetalSwapShader {
constant float PI = 3.14159265;
constant float R = 78.0;    // coin radius
constant float T = 8.5;     // coin half thickness
constant float3 TARGET = float3(0.0, 64.0, 0.0);
constant float DIST = 560.0;
constant float FOCAL = 740.0; // pt: the coin, at the target, is drawn 740/560 of its size
constant float PITCH = 0.2;


struct Scene {
float4 uStage;
float4 uCoin;
float4 uPool;
float2 uLight;
float3 uTint;
float3 uBg;
float LiquidMetalSwapSq(float v) { return v*v; }
float LiquidMetalSwapSmin(float a, float b, float k) {
  if (k <= 0.0) return min(a, b);
  float h = clamp(0.5 + 0.5*(b - a)/k, 0.0, 1.0);
  return mix(b, a, h) - k*h*(1.0 - h);
}
float2 LiquidMetalSwapRot(float2 p, float a) { float c = cos(a), s = sin(a); return float2(c*p.x - s*p.y, s*p.x + c*p.y); }
float3 LiquidMetalSwapRy(float3 v, float a) { float c = cos(a), s = sin(a); return float3(c*v.x + s*v.z, v.y, -s*v.x + c*v.z); }

float LiquidMetalSwapSdRoundCyl(float3 p, float r, float h, float rr) {
  float2 d = float2(length(p.xy) - r + rr, abs(p.z) - h + rr);
  return min(max(d.x, d.y), 0.0) + length(max(d, 0.0)) - rr;
}
// a 2D shape raised to |z| < h, its edge rounded by rr
float LiquidMetalSwapExtrude(float3 p, float d2, float h, float rr) {
  float2 w = float2(d2 + rr, abs(p.z) - h + rr);
  return min(max(w.x, w.y), 0.0) + length(max(w, 0.0)) - rr;
}
float LiquidMetalSwapSdTri(float2 p, float r) {
  const float k = 1.7320508;
  p.x = abs(p.x) - r;
  p.y = p.y + r/k;
  if (p.x + k*p.y > 0.0) p = float2(p.x - k*p.y, -k*p.x - p.y)/2.0;
  p.x -= clamp(p.x, -2.0*r, 0.0);
  return -length(p)*sign(p.y);
}
float LiquidMetalSwapSdRhombus(float2 p, float2 b) {
  p = abs(p);
  float h = clamp(((b.x - 2.0*p.x)*b.x - (b.y - 2.0*p.y)*b.y)/dot(b, b), -1.0, 1.0);
  float d = length(p - 0.5*b*float2(1.0 - h, 1.0 + h));
  return d*sign(p.x*b.y + p.y*b.x - b.x*b.y);
}
// the token's mark, 2D, pt
float LiquidMetalSwapMark(float2 p, float kind) {
  if (kind < 0.5) {
    float tri = abs(LiquidMetalSwapSdTri(p + float2(0.0, 4.0), 25.0)) - 4.2;
    return min(tri, length(p + float2(0.0, 1.0)) - 5.5);
  }
  float a = LiquidMetalSwapSdRhombus(p, float2(8.5, 33.0));
  float b = LiquidMetalSwapSdRhombus(p, float2(33.0, 8.5));
  float c = LiquidMetalSwapSdRhombus(LiquidMetalSwapRot(p, 0.785398), float2(4.0, 18.0));
  float e = LiquidMetalSwapSdRhombus(LiquidMetalSwapRot(p, -0.785398), float2(4.0, 18.0));
  return min(min(a, b), min(c, e)) - 0.6;
}

float LiquidMetalSwapCoinY() { return R + 1.0 - uCoin.y*(2.0*R + 26.0); }

float LiquidMetalSwapCoin(float3 p) {
  float3 q = p - float3(0.0, LiquidMetalSwapCoinY(), 0.0);
  q = LiquidMetalSwapRy(q, uCoin.w);
  float soft = uCoin.z;
  float ang = atan2(q.y, q.x);
  // the field domes a little, so the face does not mirror one flat colour
  q.z = sign(q.z)*max(abs(q.z) - 3.6*(1.0 - soft)*(1.0 - LiquidMetalSwapSq(min(length(q.xy)/R, 1.0))), 0.0);
  float edge = R - 0.4 + 0.4*sin(ang*96.0)*(1.0 - soft);
  float d = LiquidMetalSwapSdRoundCyl(q, edge, T, mix(2.6, 9.0, soft));
  float relief = 1.7*(1.0 - soft);
  if (relief > 0.02) {
    float ring = abs(length(q.xy) - (R - 9.5)) - 3.0;
    d = LiquidMetalSwapSmin(d, LiquidMetalSwapExtrude(q, ring, T + relief, 0.9), 0.8);
    d = LiquidMetalSwapSmin(d, LiquidMetalSwapExtrude(q, LiquidMetalSwapMark(q.xy, uCoin.x), T + relief, 0.9), 0.8);
  }
  return d;
}

float LiquidMetalSwapPool(float3 p) {
  float rp = uPool.x;
  if (rp < 1.0) return 1e4;
  float t = uStage.w;
  float wob = uPool.z;
  float ang = atan2(p.z, p.x);
  float rw = rp*(1.0 + wob*(0.05*sin(3.0*ang + t*1.7) + 0.03*sin(5.0*ang - t*2.3)));
  float r = length(p.xz);
  float y = p.y - uPool.y*0.22*wob*sin(r*0.11 - t*7.0)*exp(-r/max(rp, 1.0));
  float3 e = float3(rw, uPool.y, rw*0.78);
  float3 s = float3(p.x, y, p.z);
  float k0 = length(s/e);
  float k1 = length(s/(e*e));
  return k0*(k0 - 1.0)/max(k1, 1e-4);
}

float LiquidMetalSwapDrops(float3 p) {
  float amt = uPool.w;
  if (amt < 0.01) return 1e4;
  float d = 1e4;
  for (int i = 0; i < 4; i++) {
    float fi = float(i);
    float a = fi*1.7 + 0.6;
    float ph = clamp(amt*1.25 - fi*0.08, 0.0, 1.0);
    float h = 58.0*sin(PI*ph)*(0.7 + 0.3*fract(fi*0.61));
    float rr = uPool.x*(0.55 + 0.25*fract(fi*0.37));
    float3 c = float3(cos(a)*rr, h + 4.0, sin(a)*rr*0.8);
    d = min(d, length(p - c) - (3.5 + 2.5*sin(PI*ph)));
  }
  return d;
}

float LiquidMetalSwapScene(float3 p) {
  float liquid = clamp(uPool.x/60.0, 0.0, 1.0);
  float d = LiquidMetalSwapSmin(LiquidMetalSwapCoin(p), LiquidMetalSwapPool(p), 24.0*liquid);
  d = LiquidMetalSwapSmin(d, LiquidMetalSwapDrops(p), 10.0);
  return max(d, -p.y);
}

float3 LiquidMetalSwapNormalAt(float3 p) {
  const float e = 0.35;
  float2 k = float2(1.0, -1.0);
  return normalize(k.xyy*LiquidMetalSwapScene(p + k.xyy*e) + k.yyx*LiquidMetalSwapScene(p + k.yyx*e) + k.yxy*LiquidMetalSwapScene(p + k.yxy*e) + k.xxx*LiquidMetalSwapScene(p + k.xxx*e));
}

// the studio the chrome reflects
float3 LiquidMetalSwapEnv(float3 r) {
  r = LiquidMetalSwapRy(r, uLight.x);
  float y = r.y + uLight.y;
  float az = atan2(r.x, -r.z);
  float3 sky = mix(float3(0.30, 0.32, 0.37), float3(0.80, 0.83, 0.90), smoothstep(0.0, 0.85, y));
  sky += 2.2*smoothstep(0.80, 0.83, y);
  float s1 = smoothstep(0.15, 0.11, abs(az - 1.95))*smoothstep(0.03, 0.08, y)*smoothstep(0.72, 0.64, y);
  float s2 = smoothstep(0.10, 0.07, abs(az + 1.25))*smoothstep(0.05, 0.10, y)*smoothstep(0.60, 0.52, y);
  sky += 1.7*s1 + 1.1*s2;
  sky += float3(1.0, 0.86, 0.70)*0.55*exp(-LiquidMetalSwapSq((y - 0.04)/0.008));
  float3 ground = mix(float3(0.035, 0.038, 0.05), float3(0.12, 0.125, 0.14), smoothstep(-0.35, 0.0, y));
  float3 c = mix(ground, sky, smoothstep(-0.006, 0.006, y));
  // a white card standing in front of the coin, as in a product shot: it lights the faces, and its
  // hard top edge crosses the domed field as a line of light
  float card = smoothstep(1.3, 0.9, abs(az))*smoothstep(-0.62, -0.42, y);
  float top = smoothstep(-0.195, -0.185, y);
  float3 face = mix(mix(float3(0.10, 0.11, 0.13), float3(0.34, 0.35, 0.39), smoothstep(-0.36, -0.19, y)),
                    float3(0.92, 0.94, 0.98)*mix(1.0, 0.7, smoothstep(-0.19, -0.02, y)), top);
  c = mix(c, face, card);
  return c;
}

float3 LiquidMetalSwapChrome(float3 p, float3 rd) {
  float3 n = LiquidMetalSwapNormalAt(p);
  float3 r = reflect(rd, n);
  float lift = 0.007;
  float3 c = float3(LiquidMetalSwapEnv(r + float3(0.0, lift, 0.0)).r, LiquidMetalSwapEnv(r).g, LiquidMetalSwapEnv(r - float3(0.0, lift, 0.0)).b);
  float f = pow(1.0 - clamp(dot(n, -rd), 0.0, 1.0), 5.0);
  float3 F = mix(uTint, float3(1.0), f);
  float ao = clamp(0.35 + 0.65*LiquidMetalSwapScene(p + n*7.0)/7.0, 0.0, 1.0);
  ao *= clamp(0.55 + 0.45*p.y/14.0, 0.0, 1.0) + (1.0 - clamp(0.55 + 0.45*p.y/14.0, 0.0, 1.0))*0.4;
  return c*F*ao;
}

float3 LiquidMetalSwapToP3(float3 c) {
  float3 l = pow(max(c, float3(0.0)), float3(2.2));
  float3 m = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(max(m, float3(0.0)), float3(1.0/2.2));
}
float3 LiquidMetalSwapTone(float3 c) { return 1.0 - exp(-c*1.15); }

half4 LiquidMetalSwapMain(float2 xy) {
  float W = uStage.x, H = uStage.y;
  float2 uv = float2(xy.x - 0.5*W, 0.5*H - xy.y);
  float3 fwd = float3(0.0, -sin(PITCH), cos(PITCH));
  float3 ro = TARGET - fwd*DIST;
  float3 right = float3(1.0, 0.0, 0.0);
  float3 up = cross(fwd, right);
  float3 rd = normalize(fwd*FOCAL + right*uv.x + up*uv.y);

  // backdrop: the page, a soft pool of light on the floor
  float tFloor = rd.y < 0.0 ? -ro.y/rd.y : 1e5;
  float3 bg = uBg;
  if (tFloor < 1e5) {
    float3 fp = ro + rd*tFloor;
    float spot = exp(-LiquidMetalSwapSq(length(fp.xz*float2(1.0, 1.6))/260.0))*smoothstep(H, H - 48.0, xy.y);
    bg = uBg + float3(0.10, 0.105, 0.12)*spot;
    // contact shadow from the metal
    float occ = clamp(LiquidMetalSwapScene(fp + float3(0.0, 10.0, 0.0))/10.0, 0.0, 1.0);
    float occ2 = clamp(LiquidMetalSwapScene(fp + float3(0.0, 30.0, 0.0))/30.0, 0.0, 1.0);
    bg *= mix(0.45, 1.0, occ)*mix(0.75, 1.0, occ2);
  }

  // march only inside the box the metal can reach
  float3 bmin = float3(-175.0, 0.0, -175.0), bmax = float3(175.0, 180.0, 175.0);
  float3 inv = 1.0/rd;
  float3 ta = (bmin - ro)*inv, tb = (bmax - ro)*inv;
  float3 tlo = min(ta, tb), thi = max(ta, tb);
  float t0 = max(max(tlo.x, tlo.y), tlo.z), t1 = min(min(thi.x, thi.y), thi.z);
  float t = max(t0, 0.0);
  float tEnd = min(t1, tFloor);
  float dmin = 1e5;
  float tmin = t;
  bool hit = false;
  if (t0 < t1) {
    for (int i = 0; i < 80; i++) {
      float3 p = ro + rd*t;
      float d = LiquidMetalSwapScene(p);
      if (d < dmin) { dmin = d; tmin = t; }
      if (d < 0.08) { hit = true; break; }
      t += d*0.9;
      if (t > tEnd) break;
    }
  }
  float3 col = bg;
  float pxw = 1.0/uStage.z*(tmin/FOCAL)*1.4;
  if (hit) {
    col = LiquidMetalSwapTone(LiquidMetalSwapChrome(ro + rd*t, rd));
  } else if (dmin < pxw) {
    float a = 1.0 - dmin/pxw;
    col = mix(bg, LiquidMetalSwapTone(LiquidMetalSwapChrome(ro + rd*tmin, rd)), a);
  }
  return half4(half3(LiquidMetalSwapToP3(clamp(col, 0.0, 1.0))), 1.0);
}

};
}

[[ stitchable ]] half4 LiquidMetalSwapMain(float2 position, half4 color, float4 stage, float4 coin, float4 pool, float2 light, float3 tint, float3 bg) {
  LiquidMetalSwapShader::Scene scene { stage, coin, pool, light, tint, bg };
  return scene.LiquidMetalSwapMain(position);
}
