#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Where bean i rests in the pile, worked out once: a golden-angle spiral on the bowl's floor
// (radius 0.5*sqrt((i+0.5)/16)), raised into a mound as the pile grows.
constant float3 JellyScaleExchangeRestTable[16] = {
  float3(0.088388, 0.055769, 0.000000),
  float3(-0.112886, 0.067458, 0.093072),
  float3(0.017278, 0.079356, -0.177197),
  float3(0.142287, 0.091476, 0.167027),
  float3(-0.261112, 0.106520, -0.041565),
  float3(0.247345, 0.125398, -0.141612),
  float3(-0.082727, 0.145970, 0.276988),
  float3(-0.157788, 0.166658, -0.273414),
  float3(0.342324, 0.186323, 0.112506),
  float3(-0.356124, 0.206769, 0.132313),
  float3(0.171665, 0.229174, -0.330183),
  float3(0.126879, 0.251951, 0.364015),
  float3(-0.382382, 0.273762, -0.199420),
  float3(0.448563, 0.293502, -0.088772),
  float3(-0.273736, 0.310283, 0.350459),
  float3(-0.063267, 0.323418, -0.439238)
};

struct JellyScaleExchangeScene {

float4 uView;
float4 uPour;
float4 uCoin;
float4 uScale;

const float PI = 3.14159265;
const int N = 16;
float JellyScaleExchangeSq(float v) { return v*v; }
float JellyScaleExchangeHash1(float n) { return fract(sin(n*12.9898)*43758.5453); }
float JellyScaleExchangeSmin(float a, float b, float k) { float h = clamp(0.5 + 0.5*(b - a)/k, 0.0, 1.0); return mix(b, a, h) - k*h*(1.0 - h); }
float JellyScaleExchangeSmax(float a, float b, float k) { float h = clamp(0.5 - 0.5*(b - a)/k, 0.0, 1.0); return mix(b, a, h) + k*h*(1.0 - h); }
float3 JellyScaleExchangeRx(float3 p, float a) { float c = cos(a), s = sin(a); return float3(p.x, c*p.y - s*p.z, s*p.y + c*p.z); }
float3 JellyScaleExchangeRy(float3 p, float a) { float c = cos(a), s = sin(a); return float3(c*p.x + s*p.z, p.y, -s*p.x + c*p.z); }
float JellyScaleExchangeBox(float3 p, float3 e, float r) { float3 q = abs(p) - e + r; return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0) - r; }
float JellyScaleExchangeCap(float3 p, float3 a, float3 b, float r) { float3 pa = p - a, ba = b - a; float h = clamp(dot(pa, ba)/dot(ba, ba), 0.0, 1.0); return length(pa - ba*h) - r; }
float JellyScaleExchangeStar2(float2 p, float r, float rf) {
  const float2 k1 = float2(0.809016994375, -0.587785252292);
  const float2 k2 = float2(-k1.x, k1.y);
  p.x = abs(p.x);
  p -= 2.0*max(dot(k1, p), 0.0)*k1;
  p -= 2.0*max(dot(k2, p), 0.0)*k2;
  p.x = abs(p.x);
  p.y -= r;
  float2 ba = rf*float2(-k1.y, k1.x) - float2(0.0, 1.0);
  float h = clamp(dot(p, ba)/dot(ba, ba), 0.0, r);
  return length(p - ba*h)*sign(p.y*ba.x - p.x*ba.y);
}

const float3 BOWL_C = float3(0.0, 0.62, 0.0);
const float BOWL_R = 0.74;
const float3 PILE = float3(0.0, 0.16, 0.0); // the middle of the pile, where the gummy forms
const float3 RISE = float3(0.0, 1.05, 0.25); // where the coin stands above the bowl

float3 JellyScaleExchangeBeanColour(float i) {
  float k = fract(i*0.381966);
  if (k < 0.2) return float3(1.0, 0.45, 0.62);
  if (k < 0.4) return float3(0.7, 0.55, 1.0);
  if (k < 0.6) return float3(0.4, 0.88, 0.7);
  if (k < 0.8) return float3(1.0, 0.86, 0.35);
  return float3(0.45, 0.72, 1.0);
}

float JellyScaleExchangeBounceOut(float x) {
  float n1 = 7.5625, d1 = 2.75;
  if (x < 1.0/d1) return n1*x*x;
  if (x < 2.0/d1) { x -= 1.5/d1; return n1*x*x + 0.75; }
  if (x < 2.5/d1) { x -= 2.25/d1; return n1*x*x + 0.9375; }
  x -= 2.625/d1; return n1*x*x + 0.984375;
}

// the beans and the gummy they melt into; returns distance and (out) the colour there
float JellyScaleExchangeCandy(float3 p, thread float3 &rgb) {
  float pour = uPour.x, melt = uPour.y;
  float d = 1e3;
  float3 acc = float3(0.0);
  float wsum = 0.0;
  float k = 0.015 + 0.28*smoothstep(0.0, 0.8, melt);
  float pull = smoothstep(0.1, 0.9, melt);
  // only march the beans near their column (where they fall and lie)
  float column = JellyScaleExchangeBox(p - float3(0.0, 1.05, 0.0), float3(0.62, 1.35, 0.62), 0.2);
  int count = column > 0.25 || melt >= 0.999 ? 0 : N;
  if (count == 0) d = max(column, 0.05);
  for (int j = 0; j < N; j++) {
    if (j >= count) break;
    float i = float(j);
    float u = clamp(pour - i, 0.0, 1.0);
    if (u <= 0.0) break;
    float3 rest = JellyScaleExchangeRestTable[j];
    float3 c = rest + float3(0.0, (1.0 - JellyScaleExchangeBounceOut(u))*1.7, 0.0);
    c = mix(c, PILE, pull*0.85);
    // a bean is a capsule within 0.32 of its centre: one too far to change the blend (nor its
    // colour, whose weight would be under exp(-5)) is skipped before its turn is worked out
    float lb = length(p - c) - 0.32;
    if (lb > d + k && lb > 0.15) continue;
    float yaw = JellyScaleExchangeHash1(i)*6.28 + (1.0 - u)*4.0;
    float tilt = (JellyScaleExchangeHash1(i + 7.0) - 0.5)*0.9;
    float3 q = JellyScaleExchangeRx(JellyScaleExchangeRy(p - c, yaw), tilt);
    float grow = 1.0 + 0.6*pull;
    float b = JellyScaleExchangeCap(q, float3(-0.14, 0.0, 0.0), float3(0.14, 0.0, 0.0), 0.105*grow);
    d = JellyScaleExchangeSmin(d, b, k);
    float w = exp(-max(b, 0.0)/0.03);
    acc += JellyScaleExchangeBeanColour(i)*w;
    wsum += w;
  }
  rgb = wsum > 0.0 ? acc/wsum : float3(1.0, 0.5, 0.7);
  // the melted gummy: one round JellyScaleExchangeCandy, and the coin it is pressed into
  float g = smoothstep(0.55, 1.0, melt);
  if (g > 0.0) {
    float cm = uPour.z;
    float3 at = mix(PILE + float3(0.0, 0.06, 0.0), RISE, smoothstep(0.0, 0.6, cm)) + float3(uCoin.xy, 0.0);
    float3 q = (p - at)/uCoin.z;
    float blob = length(q*float3(1.0, 1.25, 1.0)) - 0.44;
    float3 cq = JellyScaleExchangeRy(q, uPour.w);
    float2 w2 = float2(length(cq.xy) - 0.42, abs(cq.z) - 0.06);
    float coin = min(max(w2.x, w2.y), 0.0) + length(max(w2, 0.0)) - 0.05;
    // a raised rim and a raised star on each face
    float rim = length(float2(length(cq.xy) - 0.41, abs(cq.z) - 0.1)) - 0.035;
    float st = JellyScaleExchangeStar2(cq.xy, 0.2, 0.45);
    float relief = max(st, abs(cq.z) - 0.135) - 0.01;
    coin = min(coin, min(rim, relief));
    float one = mix(blob, coin, cm) - 0.08*sin(PI*cm);
    one *= uCoin.z;
    if (uCoin.w < 1.0) one += (1.0 - uCoin.w)*0.5;
    d = mix(d, one, g);
    float3 gummy = float3(1.0, 0.45, 0.66);
    float3 gold = float3(1.0, 0.76, 0.22);
    rgb = mix(rgb, mix(gummy, gold, smoothstep(0.2, 0.8, cm)), g);
  }
  return d;
}

// id: 0 none, 1 scale body, 2 display, 3 pad, 4 bowl, 5 JellyScaleExchangeCandy
float JellyScaleExchangeSceneD(float3 p, thread float &id, thread float3 &rgb) {
  float sink = uScale.x;
  float body = JellyScaleExchangeBox(p - float3(0.0, -0.58, 0.0), float3(1.02, 0.32, 0.78), 0.24);
  float disp = JellyScaleExchangeBox(p - float3(0.0, -0.6, 0.78), float3(0.5, 0.15, 0.03), 0.06);
  body = JellyScaleExchangeSmax(body, -disp, 0.02);
  float pad = length(float2(length(p.xz) - 0.62, 0.0)) ;
  pad = max(length(p.xz) - 0.66, abs(p.y + 0.21 + sink) - 0.03) - 0.02;
  float3 bc = BOWL_C - float3(0.0, sink, 0.0);
  // a shallow dish: the sphere cut well below its middle, so the pile shows over the rim
  float bowl = max(abs(length(p - bc) - BOWL_R) - 0.04, p.y - (bc.y - 0.22));
  bowl = JellyScaleExchangeSmin(bowl, max(length(p.xz) - 0.3, abs(p.y + 0.15 + sink) - 0.04), 0.03);
  float3 cr;
  float c = JellyScaleExchangeCandy(p + float3(0.0, sink, 0.0), cr);
  id = 1.0; rgb = float3(0.0);
  float d = body;
  if (disp + 0.01 < d) { d = disp + 0.01; id = 2.0; }
  if (pad < d) { d = pad; id = 3.0; }
  if (bowl < d) { d = bowl; id = 4.0; }
  if (c < d) { d = c; id = 5.0; rgb = cr; }
  return d;
}
float JellyScaleExchangeSd(float3 p) { float id; float3 c; return JellyScaleExchangeSceneD(p, id, c); }

float3 JellyScaleExchangeToP3(float3 c) {
  float3 l = pow(max(c, float3(0.0)), float3(2.2));
  float3 m = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(max(m, float3(0.0)), float3(1.0/2.2));
}

half4 JellyScaleExchangeRender(float2 xy) {
  float ppu = uView.z;
  float2 sp = (xy - float2(uView.x*0.5, uScale.w))/ppu;
  sp.y = -sp.y;
  float tilt = uScale.y;
  float3 fwd = JellyScaleExchangeRx(float3(0.0, 0.0, -1.0), -tilt);
  float3 up = JellyScaleExchangeRx(float3(0.0, 1.0, 0.0), -tilt);
  float3 ro = float3(sp.x, 0.0, 0.0) + up*sp.y - fwd*4.0;
  float3 rd = fwd;
  float px = 1.0/(ppu*uView.w);
  // a soft shadow on the table under the scale
  float2 g = sp - float2(0.0, -1.0);
  float shadow = 0.45*exp(-JellyScaleExchangeSq(g.x/1.15) - JellyScaleExchangeSq(g.y/0.16));
  // march only where the ray can meet something: the box the scale, bowl and falling beans
  // stay in, and the sphere round the gummy or coin when it leaves it on its way to the pill
  float tIn = 1e4, tOut = -1.0;
  {
    float3 lo = float3(-1.35, -1.0, -1.15), hi = float3(1.35, 2.7, 1.15);
    float3 inv = 1.0/rd;
    float3 a = (lo - ro)*inv, b = (hi - ro)*inv;
    float3 mn = min(a, b), mx = max(a, b);
    float t0 = max(max(mn.x, mn.y), mn.z), t1 = min(min(mx.x, mx.y), mx.z);
    if (t1 > max(t0, 0.0)) { tIn = max(t0, 0.0); tOut = t1; }
    if (uPour.y > 0.55) {
      float3 at = mix(PILE + float3(0.0, 0.06, 0.0), RISE, smoothstep(0.0, 0.6, uPour.z)) + float3(uCoin.xy, 0.0) - float3(0.0, uScale.x, 0.0);
      float r = 0.75*uCoin.z + 0.6;
      float3 oc = ro - at;
      float bq = dot(oc, rd), cq = dot(oc, oc) - r*r, disc = bq*bq - cq;
      if (disc > 0.0) { float sq = sqrt(disc); tIn = min(tIn, max(-bq - sq, 0.0)); tOut = max(tOut, -bq + sq); }
    }
  }
  if (tOut < tIn) return half4(half3(0.0), half(shadow));
  float t = tIn, best = 1e4, bestT = t;
  bool hit = false;
  for (int i = 0; i < 80; i++) {
    float d = JellyScaleExchangeSd(ro + rd*t);
    if (d < best) { best = d; bestT = t; }
    if (d < 0.35*px) { hit = true; break; }
    t += d*0.9;
    if (t > tOut) break;
  }
  float cover = hit ? 1.0 : 1.0 - smoothstep(0.35*px, 1.4*px, best);
  if (cover <= 0.0) return half4(half3(0.0), half(shadow));
  float3 p = ro + rd*bestT;
  if (hit) { for (int i = 0; i < 2; i++) p += rd*JellyScaleExchangeSd(p); }
  float id; float3 crgb;
  JellyScaleExchangeSceneD(p, id, crgb);
  float e = max(0.003, px);
  const float2 k = float2(1.0, -1.0);
  float3 n = normalize(k.xyy*JellyScaleExchangeSd(p + k.xyy*e) + k.yyx*JellyScaleExchangeSd(p + k.yyx*e) + k.yxy*JellyScaleExchangeSd(p + k.yxy*e) + k.xxx*JellyScaleExchangeSd(p + k.xxx*e));
  float ao = 0.0;
  for (int j = 0; j < 3; j++) { float hh = 0.03 + 0.06*float(j); ao += max(0.0, hh - JellyScaleExchangeSd(p + n*hh))/hh/(1.0 + float(j)); }
  ao = clamp(1.0 - ao*0.9, 0.4, 1.0);
  float3 V = -rd;
  float3 key = normalize(float3(-0.5, 0.8, 0.6));
  float nl = clamp((dot(n, key) + 0.35)/1.35, 0.0, 1.0);
  float ndv = max(dot(n, V), 0.0);
  float hn = max(dot(n, normalize(key + V)), 0.0);
  float3 base; float gloss; float sss = 0.0;
  if (id < 1.5) { base = float3(0.45, 0.52, 1.0); gloss = 0.3; }
  else if (id < 2.5) { base = float3(0.06, 0.08, 0.16); gloss = 0.9; }
  else if (id < 3.5) { base = float3(0.86, 0.88, 0.95); gloss = 0.4; }
  else if (id < 4.5) { base = float3(0.97, 0.97, 0.99); gloss = 0.8; }
  else { base = crgb; gloss = 1.0; sss = 1.0 - smoothstep(0.3, 0.9, uPour.z); }
  float3 col = mix(base*float3(0.55, 0.52, 0.7), base, nl)*(0.6 + 0.45*nl)*ao;
  // JellyScaleExchangeCandy: light passing through its thin edges
  col += base*sss*0.45*pow(1.0 - ndv, 1.5);
  col += float3(1.0, 0.97, 0.92)*((0.05 + 0.25*gloss)*pow(hn, 20.0) + 0.6*gloss*pow(hn, 140.0))*ao;
  col += float3(0.55, 0.6, 1.0)*0.2*pow(1.0 - ndv, 3.0);
  // the gold coin's mirror-like shine
  if (id > 4.5) col += float3(1.0, 0.85, 0.5)*0.35*smoothstep(0.5, 1.0, uPour.z)*pow(max(reflect(rd, n).y, 0.0), 2.0);
  float3 ex = max(col - 0.9, 0.0);
  col = min(col, float3(0.9)) + ex/(1.0 + ex/0.1);
  float a = cover;
  float3 outC = col*a;
  float outA = a + shadow*(1.0 - a);
  return half4(half3(JellyScaleExchangeToP3(clamp(outC/max(outA, 1e-4), 0.0, 1.0))*outA), half(outA));
}

};

[[ stitchable ]] half4 JellyScaleExchangeMain(float2 position, half4 color, float4 view, float4 pour, float4 coin, float4 scale) {
  JellyScaleExchangeScene scene;
  scene.uView = view; scene.uPour = pour; scene.uCoin = coin; scene.uScale = scale;
  return scene.JellyScaleExchangeRender(position);
}
