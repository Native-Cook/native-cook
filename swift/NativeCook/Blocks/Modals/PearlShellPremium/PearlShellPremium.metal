#include <metal_stdlib>
using namespace metal;

struct PearlShellPremiumUniforms {
  float2 res, center;
  float scale, yaw, open, glow, lift, spin, phase, px;
};
// P3 drawable: the source's sRGB-to-P3 conversion is performed exactly once.
struct PearlShellPremiumRaymarch {

  float2 uRes;
  float2 uC;
  float uS;
  float uYaw;
  float uOpen;
  float uGlow;
  float uLift;
  float uSpin;
  float uPhase;
  float uPx;

const float3 TINT = float3(0.93, 0.94, 0.99);
const float3 HINGE = float3(0.0, 0.0, -0.78);
float pearlShellPremiumSq(float v) { return v*v; }
float pearlShellPremiumDot2(float2 v) { return dot(v, v); }

// a valve: half an ellipsoid, open side up (y >= 0 cut), ribs fanning from the hinge
float pearlShellPremiumSdValve(float3 p) {
  float3 r = float3(1.0, 0.34, 0.86);
  float3 q = p/r;
  float k0 = length(q);
  float k1 = length(q/r);
  float d = k0*(k0 - 1.0)/k1;
  // ribs: radial grooves around the hinge
  float a = atan2(p.x, p.z - HINGE.z);
  d += 0.018*cos(a*15.0)*smoothstep(0.1, 0.6, length(p.xz - HINGE.xz));
  // a shell, not a solid: hollow it out
  d = abs(d + 0.05) - 0.05;
  // keep the half under the opening plane, with a scalloped rim
  float rim = p.y - 0.015*cos(a*15.0);
  return max(d, rim);
}
float3 pearlShellPremiumRx(float3 v, float a) { float c = cos(a), s = sin(a); return float3(v.x, c*v.y - s*v.z, s*v.y + c*v.z); }
float3 pearlShellPremiumRy(float3 v, float a) { float c = cos(a), s = sin(a); return float3(c*v.x + s*v.z, v.y, -s*v.x + c*v.z); }
float pearlShellPremiumSdBottom(float3 p) { return pearlShellPremiumSdValve(p); }
float pearlShellPremiumSdTop(float3 p) {
  // the top valve is the bottom one flipped, turned open about the hinge
  float3 q = pearlShellPremiumRx(p - HINGE, uOpen) + HINGE;
  q.y = -q.y;
  return pearlShellPremiumSdValve(q);
}
float3 pearlShellPremiumPearlC() { return float3(0.0, 0.08 + uLift, 0.05); }
const float PEARL_R = 0.27;
float pearlShellPremiumSdShell(float3 p) { return min(pearlShellPremiumSdBottom(p), pearlShellPremiumSdTop(p)); }
float3 pearlShellPremiumNShell(float3 p) {
  const float e = 0.0015;
  float2 k = float2(1.0, -1.0);
  return normalize(k.xyy*pearlShellPremiumSdShell(p + k.xyy*e) + k.yyx*pearlShellPremiumSdShell(p + k.yyx*e) + k.yxy*pearlShellPremiumSdShell(p + k.yxy*e) + k.xxx*pearlShellPremiumSdShell(p + k.xxx*e));
}

// the view: from in front and above
float3 pearlShellPremiumToObj(float3 v) { return pearlShellPremiumRy(pearlShellPremiumRx(v, -0.62), -uYaw); }
float3 pearlShellPremiumToWorld(float3 v) { return pearlShellPremiumRx(pearlShellPremiumRy(v, uYaw), 0.62); }

float3 pearlShellPremiumSea(float3 r) {
  float3 c = mix(float3(0.03, 0.05, 0.1), float3(0.12, 0.2, 0.32), smoothstep(-0.3, 0.9, r.y));
  c += float3(0.7, 0.85, 1.0)*6.0*smoothstep(0.93, 0.96, dot(r, normalize(float3(-0.3, 0.9, 0.3))));
  c += float3(0.6, 0.8, 1.0)*3.0*smoothstep(0.86, 0.9, r.x)*(1.0 - smoothstep(0.3, 0.45, abs(r.y - 0.2)));
  c += float3(0.9, 0.85, 1.0)*2.0*pow(max(dot(r, normalize(float3(-0.2, 0.4, 0.9))), 0.0), 12.0);
  return c;
}
float3 pearlShellPremiumFilm(float cosi, float3 p) {
  float d = 1.4 + 0.9*cosi + 0.2*sin(2.5*p.x + 2.0*p.z);
  return 0.6 + 0.4*cos(6.2831853*(d + float3(0.0, 0.33, 0.67)));
}

float pearlShellPremiumCaustic(float2 q) {
  float c = 0.0;
  c += sin(q.x*0.045 + uPhase) + sin(q.y*0.05 - uPhase*0.8);
  c += sin((q.x + q.y)*0.035 + uPhase*1.3) + sin((q.x - q.y)*0.04 - uPhase*0.6);
  return pow(clamp(0.5 + 0.18*c, 0.0, 1.0), 4.0);
}
float3 pearlShellPremiumPage(float2 xy) {
  float3 col = mix(float3(0.035, 0.06, 0.12), float3(0.06, 0.1, 0.17), clamp(xy.y/uRes.y, 0.0, 1.0));
  // sand under the clam, lit by caustics and by the pearl
  float2 q = (xy - uC - float2(0.0, 0.25*uS))/(uS*float2(2.4, 1.0));
  float sand = exp(-dot(q, q)*1.2);
  col += float3(0.08, 0.11, 0.16)*sand + float3(0.2, 0.3, 0.38)*pearlShellPremiumCaustic(xy)*sand*0.35;
  col += float3(0.95, 0.88, 1.0)*0.35*uGlow*exp(-pearlShellPremiumDot2((xy - uC)/(uS*1.6)));
  // the clam's shadow
  float2 qs = (xy - uC - float2(0.0, 0.42*uS))/(uS*float2(1.1, 0.22));
  col *= 1.0 - 0.6*exp(-dot(qs, qs)*1.6);
  return col;
}

float3 pearlShellPremiumRender(float2 xy, thread float &cover) {
  float2 u = (xy - uC)/uS;
  float px = uPx/uS;
  float3 ro = pearlShellPremiumToObj(float3(u.x, -u.y, 6.0));
  float3 rd = pearlShellPremiumToObj(float3(0.0, 0.0, -1.0));
  float t = 3.0;
  float dmin = 1e3;
  float tBest = t;
  int what = 0;
  for (int i = 0; i < 80; i++) {
    float3 p = ro + rd*t;
    float ds = pearlShellPremiumSdShell(p);
    float dp = length(p - pearlShellPremiumPearlC()) - PEARL_R;
    float d = min(ds, dp);
    if (d < dmin) { dmin = d; tBest = t; what = ds < dp ? 1 : 2; }
    if (d < 0.001) break;
    t += d*0.8;
    if (t > 9.0) break;
  }
  cover = 1.0 - smoothstep(0.0, 1.6*px, dmin);
  if (cover <= 0.0) return float3(0.0);
  float3 p = ro + rd*tBest;
  float3 vW = float3(0.0, 0.0, -1.0);
  if (what == 2) {
    // the pearl: nacre, a soft key, a thin-pearlShellPremiumFilm sheen that turns, its own light
    float3 n = normalize(p - pearlShellPremiumPearlC());
    float3 nS = pearlShellPremiumRy(n, uSpin);
    float3 nW = pearlShellPremiumToWorld(n);
    float cosi = clamp(dot(-rd, n), 0.0, 1.0);
    float key = clamp(dot(nW, normalize(float3(-0.4, 0.8, 0.45))), 0.0, 1.0);
    float3 base = float3(0.94, 0.92, 0.97)*(0.5 + 0.55*key);
    float3 sheen = pearlShellPremiumFilm(cosi, nS*2.0);
    float3 col = base*mix(float3(1.0), sheen*1.1, 0.18) + min(pearlShellPremiumSea(reflect(vW, nW)), float3(2.0))*0.25*pow(1.0 - cosi, 2.0);
    col += float3(1.0, 0.96, 1.0)*0.45*uGlow*(0.4 + 0.6*cosi);
    return col;
  }
  float3 n = pearlShellPremiumNShell(p);
  float3 nW = pearlShellPremiumToWorld(n);
  float cosi = clamp(abs(dot(rd, n)), 0.0, 1.0);
  float F = 0.04 + 0.96*pow(1.0 - cosi, 5.0);
  float3 refl = pearlShellPremiumSea(reflect(vW, nW));
  // frosted pearly glass: milky, iridescent, lit from the pearl inside
  float3 nacre = pearlShellPremiumFilm(cosi, p*1.6);
  float wrap = clamp(dot(nW, normalize(float3(-0.3, 0.9, 0.3)))*0.5 + 0.5, 0.0, 1.0);
  float3 body = mix(float3(0.32, 0.38, 0.5), float3(0.86, 0.88, 0.96), wrap)*TINT;
  body *= mix(float3(1.0), nacre*1.08, 0.2);
  float inner = exp(-pearlShellPremiumSq(length(p - pearlShellPremiumPearlC())/0.9));
  body += float3(1.0, 0.95, 1.0)*0.5*uGlow*inner;
  body = mix(body, pearlShellPremiumPage(xy)*1.2, 0.18);
  return mix(body, refl, F*0.8);
}

float3 pearlShellPremiumToP3(float3 c) {
  float3 l = pow(max(c, float3(0.0)), float3(2.2));
  float3 m = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(max(m, float3(0.0)), float3(1.0/2.2));
}

half4 pearlShellPremiumMain(float2 xy) {
  float3 col = pearlShellPremiumPage(xy);
  float2 u = (xy - uC)/uS;
  if (abs(u.x) < 1.3 && u.y > -2.0 && u.y < 1.1) {
    float cv;
    float3 g = pearlShellPremiumRender(xy, cv);
    col = mix(col, g, cv);
  }
  col = col/(1.0 + 0.12*max(max(col.r, col.g), col.b) - 0.12);
  float3 o = pearlShellPremiumToP3(clamp(col, 0.0, 1.0));
  o += (fract(sin(dot(xy, float2(12.9898, 78.233)))*43758.5453) - 0.5)/255.0;
  return half4(half3(o), 1.0);
}

};
vertex float4 pearlShellPremiumVertex(uint id [[vertex_id]]) {
  const float2 p[3] = {float2(-1,-1),float2(3,-1),float2(-1,3)};
  return float4(p[id],0,1);
}
fragment half4 pearlShellPremiumFragment(float4 position [[position]],
    constant PearlShellPremiumUniforms &u [[buffer(0)]]) {
  PearlShellPremiumRaymarch r;
  r.uRes=u.res; r.uC=u.center; r.uS=u.scale; r.uYaw=u.yaw;
  r.uOpen=u.open; r.uGlow=u.glow; r.uLift=u.lift;
  r.uSpin=u.spin; r.uPhase=u.phase; r.uPx=u.px;
  return r.pearlShellPremiumMain(position.xy*u.px);
}
