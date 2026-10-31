package com.ngocdevv.nativecook.blocks.gestures.puffysticker

internal const val PuffyStickerAgsl = """
uniform float uDensity;
uniform float2 uC;        // sticker centre, pt
uniform float uS;         // sticker radius, pt
uniform float uPuff;      // 0 flat … 1 inflated
uniform float uA;         // shape from (0 heart, 1 star, 2 cloud, 3 smiley)
uniform float uB;         // shape to
uniform float uW;         // blend 0 … 1 (a = floor(shape), b = a + 1)
uniform float2 uScale;    // squash and stretch
uniform float uRot;       // axis of the squash, radians
uniform float2 uLight;    // light offset from the tilt / drag
uniform float uPixelRatio;

const float PI = 3.14159265;
float sq(float v) { return v*v; }
float2 rot(float2 p, float a) { float c=cos(a), s=sin(a); return float2(c*p.x+s*p.y, -s*p.x+c*p.y); }
float smin(float a, float b, float k) { float h=clamp(0.5+0.5*(b-a)/k,0.0,1.0); return mix(b,a,h)-k*h*(1.0-h); }

float heart(float2 p) {
  p.y = -p.y*1.06 + 0.7; p.x = abs(p.x);
  p *= 0.74;
  if (p.y + p.x > 1.0) return (sqrt(dot(p - float2(0.25, 0.75), p - float2(0.25, 0.75))) - sqrt(2.0)/4.0)/0.74;
  float2 a = p - float2(0.0, 1.0); float2 b = p - 0.5*max(p.x + p.y, 0.0);
  return sqrt(min(dot(a, a), dot(b, b)))*sign(p.x - p.y)/0.74;
}
float star(float2 p) {
  // five points, rounded
  float r = 0.92, rf = 0.5;
  const float2 k1 = float2(0.809016994375, -0.587785252292);
  const float2 k2 = float2(-k1.x, k1.y);
  p.y = -p.y + 0.06;
  p.x = abs(p.x);
  p -= 2.0*max(dot(k1, p), 0.0)*k1;
  p -= 2.0*max(dot(k2, p), 0.0)*k2;
  p.x = abs(p.x);
  p.y -= r;
  float2 ba = rf*float2(-k1.y, k1.x) - float2(0.0, 1.0);
  float h = clamp(dot(p, ba)/dot(ba, ba), 0.0, r);
  return length(p - ba*h)*sign(p.y*ba.x - p.x*ba.y) - 0.07;
}
float rbox(float2 p, float2 b, float r) { float2 q=abs(p)-b+r; return length(max(q,0.0))+min(max(q.x,q.y),0.0)-r; }
float cloud(float2 p) {
  p.y -= 0.06;
  float d = length(p - float2(-0.5, 0.14)) - 0.36;
  d = smin(d, length(p - float2(-0.04, -0.2)) - 0.5, 0.14);
  d = smin(d, length(p - float2(0.52, 0.06)) - 0.38, 0.14);
  d = smin(d, rbox(p - float2(0.0, 0.28), float2(0.84, 0.2), 0.2), 0.16);
  return d;
}
float smiley(float2 p) { return length(p) - 0.86; }
float shapeOf(float2 p, float id) {
  if (id < 0.5) return heart(p);
  if (id < 1.5) return star(p);
  if (id < 2.5) return cloud(p);
  return smiley(p);
}
float3 colourOf(float id) {
  if (id < 0.5) return float3(1.0, 0.24, 0.43);
  if (id < 1.5) return float3(1.0, 0.76, 0.10);
  if (id < 2.5) return float3(0.31, 0.70, 1.0);
  return float3(1.0, 0.54, 0.12);
}
// the sticker's own frame: squash, turn
float2 local(float2 xy) {
  // squash and stretch along uRot without turning the sticker
  float2 q = (xy - uC)/uS;
  q = rot(q, uRot);
  q /= uScale;
  return rot(q, -uRot);
}
float body(float2 q) {
  float a = shapeOf(q, uA);
  if (uW <= 0.0) return a;
  float b = shapeOf(q, uB);
  return mix(a, b, uW) - 0.09*sin(PI*uW);
}
const float RIM = 0.075;
// distance to the whole die-cut sticker (shape plus white rim), smoothed over a ring of taps so the
// height has no crease along the middle of the shape
float outline(float2 q) { return body(q) - RIM; }
// The balloon's height is the sticker's coverage blurred over three rings of taps (a smooth dome
// with no crease along the middle of the shape); the same taps give the height's gradient, because
// the gradient of a Gaussian blur is the blur of the coverage weighted by each tap's offset.
// soft coverage: a ramp across the edge, so a few taps blur without facets
float coverAt(float2 q, float px) { return smoothstep(0.32, -0.32, outline(q)); }
float3 blurred(float2 q, float spread, float px) {
  float h = coverAt(q, px)*0.16;
  float2 g = float2(0.0);
  float wsum = 0.16;
  for (int ring = 0; ring < 3; ring++) {
    float r = spread*(0.28 + 0.36*float(ring));
    float w = 0.12 - 0.03*float(ring);
    float sig2 = r*r;
    for (int i = 0; i < 8; i++) {
      float a = (float(i) + 0.5*float(ring))*PI*0.25;
      float2 o = r*float2(cos(a), sin(a));
      float c = coverAt(q + o, px);
      h += c*w;
      g += c*w*o/sig2;
      wsum += w;
    }
  }
  return float3(h/wsum, g/wsum);
}
float3 toP3(float3 c) {
  float3 l = pow(max(c, float3(0.0)), float3(2.2));
  float3 m = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(max(m, float3(0.0)), float3(1.0/2.2));
}
half4 premul(float3 rgb, float a) {
  if (a <= 0.0) return half4(0.0);
  return half4(half3(toP3(clamp(rgb, 0.0, 1.0))*a), half(a));
}

half4 main(float2 pixel) {
  float2 xy = pixel/uDensity;
  float2 q = local(xy);
  float px = 1.0/(uS*uPixelRatio*min(uScale.x, uScale.y));
  float d = outline(q);
  // contact shadow on the page, pushed away from the light as the sticker rises
  float2 off = float2(0.05, 0.11)*uPuff + float2(0.012, 0.02);
  float sd = outline(local(xy - off*uS));
  float blur = mix(0.02, 0.2, uPuff);
  float shadowA = exp(-max(sd, 0.0)/blur)*mix(0.25, 0.42, uPuff);
  float cover = 1.0 - smoothstep(-px, px, d);
  if (cover <= 0.0) return premul(float3(0.0), shadowA);
  float spread = 0.95;
  float3 hb = blurred(q, spread, px);
  float H = max(hb.x, 0.02);
  float depth = 2.6*uPuff;
  // a balloon's cross-section: steep at the seam, round over the top
  float2 grad = hb.yz*(0.5/sqrt(H))*depth;
  float3 n = normalize(float3(-grad.x, grad.y, 1.0));
  // colour: printed area inside the shape, white die-cut rim outside it
  float inner = body(q);
  float printed = 1.0 - smoothstep(-px, px, inner);
  float3 ink = mix(colourOf(uA), colourOf(uB), uW);
  // the smiley's face, printed on it
  float faceW = (uA > 2.5 ? 1.0 - uW : 0.0) + (uB > 2.5 ? uW : 0.0);
  if (faceW > 0.0) {
    float2 f = q;
    float eye = min(length((f - float2(-0.27, -0.2))*float2(1.0, 0.62)) - 0.09, length((f - float2(0.27, -0.2))*float2(1.0, 0.62)) - 0.09);
    float mouth = abs(length(f - float2(0.0, -0.06)) - 0.44) - 0.055;
    mouth = max(mouth, -(f.y - 0.12));
    float faceD = min(eye, mouth);
    ink = mix(ink, float3(0.23, 0.10, 0.03), (1.0 - smoothstep(-px, px, faceD))*faceW);
  }
  float3 base = mix(float3(0.985, 0.985, 0.99), ink, printed);
  // vinyl shading
  float3 L = normalize(float3(-0.45 + uLight.x, 0.62 + uLight.y, 0.66));
  float3 V = float3(0.0, 0.0, 1.0);
  float wrap = clamp((dot(n, L) + 0.35)/1.35, 0.0, 1.0);
  float3 shadowTint = base*base*float3(0.82, 0.78, 0.9);
  float3 c = mix(shadowTint, base, wrap)*(0.55 + 0.5*wrap);
  // the seam where the balloon meets the page darkens a little
  c *= 1.0 - 0.28*uPuff*smoothstep(0.55, 0.15, H);
  float gloss = uPuff;
  c += float3(1.0)*pow(max(dot(n, normalize(L + V)), 0.0), 110.0)*1.1*gloss;
  c += float3(1.0)*pow(max(dot(n, normalize(L + V)), 0.0), 14.0)*0.18*gloss;
  float3 W = normalize(float3(0.7 + uLight.x*0.5, -0.25, 0.65));
  c += float3(0.95, 0.97, 1.0)*pow(max(dot(n, normalize(W + V)), 0.0), 26.0)*0.35*gloss;
  c += base*0.45*pow(1.0 - n.z, 2.5)*gloss;
  // a flat sticker is a matte print with a faint sheen
  float3 matte = base*(0.96 + 0.04*smoothstep(0.8, -0.8, q.x + q.y));
  c = mix(matte, c, smoothstep(0.0, 0.35, uPuff));
  float a = cover + shadowA*(1.0 - cover);
  return premul(c*cover/max(a, 0.0001), a);
}
"""
