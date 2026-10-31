#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// One-to-one SkSL distance fields and lighting; uniforms are local to each invocation.
struct TapEarnCoinField {

float2 uCentre;
float uUnit;
float uPixelRatio;
float uTilt;
float uPress;
float uHop;
float uMorph;
float uSwing;
float uShadow;
float uGleam;
float uAlpha;
float2 uLean;
float uGlow;


float sq(float v) { return v*v; }
float3 rx(float3 p, float a) { float c=cos(a), s=sin(a); return float3(p.x, c*p.y+s*p.z, -s*p.y+c*p.z); }
float3 rz(float3 p, float a) { float c=cos(a), s=sin(a); return float3(c*p.x+s*p.y, -s*p.x+c*p.y, p.z); }
float2 rot(float2 p, float a) { float c=cos(a), s=sin(a); return float2(c*p.x-s*p.y, s*p.x+c*p.y); }
float rrect(float2 p, float2 ext, float r) {
  float2 q=abs(p)-ext+r;
  return length(max(q,0.0))+min(max(q.x,q.y),0.0)-r;
}
float smin(float a, float b, float k) { float h=clamp(0.5+0.5*(b-a)/k,0.0,1.0); return mix(b,a,h)-k*h*(1.0-h); }
float smax(float a, float b, float k) { return -smin(-a,-b,k); }
float segD(float2 p, float2 a, float2 b) {
  float2 v=p-a, w=b-a;
  return length(v-w*clamp(dot(v,w)/dot(w,w),0.0,1.0));
}
float arcD(float2 p, float2 sc, float ra, float rb) {
  p.x=abs(p.x);
  return ((sc.y*p.x>sc.x*p.y) ? length(p-sc*ra) : abs(length(p)-ra)) - rb;
}

// The cent mark embossed on the cap: an open ring and a bar through it.
float centGlyph(float2 g) {
  float ringD = arcD(float2(g.y, -g.x), float2(sin(2.2), cos(2.2)), 0.2, 0.055);
  float barD = segD(g, float2(0.0, -0.3), float2(0.0, 0.3)) - 0.05;
  return min(ringD, barD);
}

// ---- button: a superelliptic gold dome with a pocket, a round cap pressed into it
float buttonD(float3 q) {
  float squash = 1.0 - 0.07*uHop;
  float wide = 1.0 + 0.025*uHop;
  float r = length(q.xz);
  float n = 2.15;
  float2 e = float2(r/wide, q.y > 0.0 ? q.y/(0.50*squash) : -q.y/0.2);
  float k = pow(pow(e.x, n) + pow(e.y, n), 1.0/n);
  float base = (k - 1.0)*0.42*squash;
  base = smax(base, -q.y - 0.12, 0.04);
  // a pocket the cap sits in, leaving a dark seam
  float seat = 0.37*squash;
  float pocket = max(r - 0.665, seat - q.y);
  base = smax(base, -pocket, 0.012);

  // the cap rocks toward an off-centre press, like an arcade button
  float3 qc = q - float3(0.0, 0.26, 0.0);
  qc = rz(rx(qc, uLean.y), uLean.x) + float3(0.0, 0.26, 0.0);
  r = length(qc.xz);
  float top = (0.63 - 0.12*uPress)*squash;
  float dome = 0.022*(1.0 - sq(min(r/0.62, 1.0)));
  float bottom = 0.26;
  float cy = 0.5*(top + bottom);
  float cap = rrect(float2(r, qc.y - cy - 0.5*dome), float2(0.62, 0.5*(top - bottom) + 0.5*dome), 0.08);
  float topW = smoothstep(top - 0.07, top - 0.01, qc.y);
  // raised rim and the embossed glyph
  float rim = 0.012*smoothstep(0.035, 0.0, abs(r - 0.545));
  float glyph = centGlyph(float2(qc.x, -qc.z)*0.8);
  float emb = 0.03*smoothstep(0.018, -0.012, glyph)*(1.0 - smoothstep(0.6, 0.95, uMorph*1.6));
  cap -= (rim + emb)*topW;
  return min(base, cap);
}

// ---- bell: a flared body, a rolled lip, a knob, a clapper and a red badge
float bellBody(float3 b) {
  float r = length(b.xz);
  float y = b.y;
  float u = clamp((y - 0.08)/0.78, 0.0, 1.0);
  float R = 0.30 + 0.31*pow(1.0 - u, 1.7);
  float side = (r - R)*0.82;
  float body = smax(side, max(0.06 - y, y - 0.86), 0.05);
  body = smin(body, length(b - float3(0.0, 0.79, 0.0)) - 0.315, 0.06);
  float lip = length(float2(r - 0.6, y - 0.085)) - 0.078;
  body = smin(body, lip, 0.05);
  float knob = length(b - float3(0.0, 1.13, 0.0)) - 0.085;
  body = smin(body, knob, 0.05);
  float clapper = length(b - float3(-sin(uSwing)*0.18, -0.02, 0.0)) - 0.12;
  return min(body, clapper);
}
float bellDot(float3 b) {
  return length(b - float3(0.37, 0.98, 0.16)) - 0.155;
}
float3 bellPoint(float3 q) {
  float3 b = q + float3(0.0, 0.53, 0.0);
  // swing about the knob
  float2 xy = rot(b.xy - float2(0.0, 1.13), uSwing) + float2(0.0, 1.13);
  return float3(xy, b.z);
}

float3 objectPoint(float3 p) {
  return rx(p, uTilt);
}

float sceneD(float3 p) {
  float3 q = objectPoint(p);
  float w = uMorph;
  if (w <= 0.001) return buttonD(q + float3(0.0, 0.28, 0.0));
  float3 b = bellPoint(q);
  float bell = min(bellBody(b), bellDot(b) + 0.4*(1.0 - smoothstep(0.7, 1.0, w)));
  if (w >= 0.999) return bell;
  float btn = buttonD(q + float3(0.0, 0.28, 0.0));
  return mix(btn, bell, w) - 0.11*sin(3.14159265359*w);
}

// 0 gold body, 1 red badge
float materialAt(float3 p) {
  if (uMorph < 0.7) return 0.0;
  float3 b = bellPoint(objectPoint(p));
  float d = bellDot(b);
  return smoothstep(0.012, -0.004, d)*smoothstep(0.7, 1.0, uMorph);
}

float3 normalAt(float3 p) {
  const float e = 0.0015;
  const float2 k = float2(1.0, -1.0);
  float3 n = k.xyy*sceneD(p + k.xyy*e) + k.yyx*sceneD(p + k.yyx*e) + k.yxy*sceneD(p + k.yxy*e) + k.xxx*sceneD(p + k.xxx*e);
  return n/max(length(n), 0.000001);
}

float pixelHash(float2 p) {
  float3 v = fract(float3(p.x, p.y, p.x)*0.1031);
  v += dot(v, v.yzx + 33.33);
  return fract((v.x + v.y)*v.z);
}
float3 toP3(float3 c) {
  float3 l = pow(max(c, float3(0.0)), float3(2.2));
  float3 m = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(max(m, float3(0.0)), float3(1.0/2.2));
}
half4 finish(float3 rgb, float alpha, float2 xy) {
  alpha *= uAlpha; rgb *= uAlpha;
  if (alpha <= 0.0) return half4(0.0);
  float2 px = floor(xy*uPixelRatio);
  float noise = (pixelHash(px) - pixelHash(px + float2(71.7, 193.3)))/255.0;
  float a = clamp(alpha + noise, 0.0, 1.0);
  float3 straight = toP3(rgb/max(alpha, 0.000001));
  return half4(half3(clamp(straight*a, float3(0.0), float3(a))), half(a));
}

half4 render(float2 xy) {
  float2 s = (xy - uCentre)/uUnit;
  // contact shadow on the page (button) or the sheet (bell)
  float w = uMorph;
  float sw = mix(1.0, 0.42, w), sh = mix(0.22, 0.1, w), sy = mix(0.42, 0.74, w);
  float lift = 1.0 + 0.6*max(uHop, 0.0);
  float shadow = exp(-sq(s.x/(sw*lift)) - sq((s.y - sy)/(sh*lift)))*uShadow*0.42/lift;
  shadow *= 1.0 - smoothstep(1.15, 1.6, max(abs(s.x), abs(s.y)));
  float3 shadowRGB = float3(0.42, 0.27, 0.08);
  // a warm bloom on the page behind the button after each payout
  float glow = uGlow*0.34*exp(-sq(s.x/1.3) - sq((s.y + 0.05)/0.9))*(1.0 - w);
  glow *= 1.0 - smoothstep(1.2, 1.62, max(abs(s.x), abs(s.y)));
  float3 underRGB = shadowRGB*shadow + float3(1.0, 0.70, 0.28)*glow*(1.0 - shadow);
  float underA = shadow + glow*(1.0 - shadow);

  float D = 7.0;
  float3 ro = float3(0.0, 0.0, D);
  float3 rd = normalize(float3(s.x, -s.y, -D));
  float radius = 1.32;
  float b = dot(ro, rd);
  float det = b*b - dot(ro, ro) + radius*radius;
  if (det < 0.0) return finish(underRGB, underA, xy);
  float t = -b - sqrt(det), far = -b + sqrt(det);
  float pixel = 1.0/(uUnit*uPixelRatio);
  float threshold = pixel*0.2;
  float closest = 10.0, bestT = t;
  bool hit = false;
  for (int i = 0; i < 64; i++) {
    float d = sceneD(ro + rd*t);
    if (d < closest) { closest = d; bestT = t; }
    if (d < threshold) { hit = true; break; }
    t += max(d*0.72, threshold*0.6);
    if (t > far) break;
  }
  float cover = hit ? 1.0 : 1.0 - smoothstep(threshold, pixel*1.2, closest);
  if (cover <= 0.0) return finish(underRGB, underA, xy);
  float3 p = ro + rd*bestT;
  float3 n = normalAt(p);
  float ao = 0.0;
  for (int i = 0; i < 3; i++) {
    float h = 0.03 + float(i)*0.07;
    ao += max(0.0, h - sceneD(p + n*h))/(1.0 + float(i))*2.2;
  }
  ao = clamp(1.0 - ao, 0.35, 1.0);

  // satin gold: the colour comes from what the surface reflects
  float3 gold = mix(float3(0.80, 0.61, 0.23), float3(1.0, 0.76, 0.12), w);
  float3 v = -rd;
  float3 refl = reflect(rd, n);
  float3 key = normalize(float3(-0.55, 0.85, 0.55));
  // studio: a broad soft box at eye level in front, a dimmer ceiling, a dark floor
  float horizon = exp(-sq((refl.y - 0.1)/0.34))*(0.55 + 0.45*clamp(refl.z, 0.0, 1.0));
  float sky = smoothstep(0.35, 1.0, refl.y);
  float floorBounce = smoothstep(-0.05, -0.5, refl.y);
  float graze = sq(1.0 - clamp(dot(n, v), 0.0, 1.0));
  float env = 0.12 + 0.9*horizon + 0.36*sky + 0.32*floorBounce - 0.5*graze;
  float diffuse = clamp(dot(n, key)*0.5 + 0.5, 0.0, 1.0);
  float3 colour = gold*(0.16 + 0.78*env + 0.2*diffuse);
  float3 hv = normalize(key + v);
  float hn = max(dot(n, hv), 0.0);
  colour += float3(1.0, 0.86, 0.52)*(0.45*pow(hn, 18.0) + 0.55*pow(hn, 90.0));
  // warm bounce from the page on faces turned down
  colour += float3(1.0, 0.62, 0.22)*0.10*max(-n.y, 0.0);
  // a gleam that sweeps across after each payout
  float3 q = objectPoint(p);
  float band = exp(-sq((q.x*0.8 + q.z*0.6 - uGleam*1.6)/0.16));
  colour += float3(1.0, 0.93, 0.70)*0.55*band*smoothstep(-1.0, -0.8, uGleam)*(1.0 - smoothstep(0.8, 1.0, uGleam))*max(n.y, 0.0);
  colour *= ao;
  float red = materialAt(p);
  if (red > 0.0) {
    float3 redCol = float3(0.94, 0.16, 0.13)*(0.35 + 0.7*diffuse) + float3(1.0, 0.8, 0.75)*0.6*pow(hn, 40.0);
    colour = mix(colour, redCol, red);
  }
  // soft shoulder on highlights
  float3 over = max(colour - 0.9, 0.0);
  colour = min(colour, float3(0.9)) + over/(1.0 + over/0.12);
  float a = cover + underA*(1.0 - cover);
  float3 rgb = colour*cover + underRGB*(1.0 - cover);
  return finish(rgb, a, xy);
}

};

[[ stitchable ]] half4 TapEarnCoinMain(float2 position, half4 source, float2 uCentre, float uUnit, float uPixelRatio, float uTilt, float uPress, float uHop, float uMorph, float uSwing, float uShadow, float uGleam, float uAlpha, float2 uLean, float uGlow, float2 origin) {
  TapEarnCoinField field = {uCentre, uUnit, uPixelRatio, uTilt, uPress, uHop, uMorph, uSwing, uShadow, uGleam, uAlpha, uLean, uGlow};
  return field.render(position + origin);
}
