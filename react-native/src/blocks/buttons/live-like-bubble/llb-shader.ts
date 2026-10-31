// Live Like Bubble — a glossy 3D like bubble that swallows the hearts thrown at it.
//
// One SkSL effect over the screen, an orthographic camera tilted a little down onto a pale floor with a
// faint grid. The bubble is a puffy red speech bubble with a tail and a raised white heart on its face;
// six small puffy hearts fly at it (their places and sizes come from the app) and melt into it through
// a smooth union, so a heart arriving pulls a gooey neck out of the bubble before it is swallowed, and
// the bubble swells with each one. Soft shadows on the floor. Premultiplied, sRGB → Display P3.
export const LLB_SKSL = `
uniform float4 uView;   // width, height (pt), points per unit, pixel ratio
uniform float4 uBubble; // centre y on the canvas (pt), swell 0…1, wobble (rad), camera tilt (rad)
uniform float4 uH0;     // heart: x, y, z (units), size (0 = none)
uniform float4 uH1;
uniform float4 uH2;
uniform float4 uH3;
uniform float4 uH4;
uniform float4 uH5;

const float PI = 3.14159265;
float sq(float v) { return v*v; }
float smin(float a, float b, float k) { float h = clamp(0.5 + 0.5*(b - a)/k, 0.0, 1.0); return mix(b, a, h) - k*h*(1.0 - h); }
float3 rx(float3 p, float a) { float c = cos(a), s = sin(a); return float3(p.x, c*p.y - s*p.z, s*p.y + c*p.z); }
float3 ry(float3 p, float a) { float c = cos(a), s = sin(a); return float3(c*p.x + s*p.z, p.y, -s*p.x + c*p.z); }
float3 rz(float3 p, float a) { float c = cos(a), s = sin(a); return float3(c*p.x - s*p.y, s*p.x + c*p.y, p.z); }
float ell(float3 p, float3 r) { float k0 = length(p/r); float k1 = length(p/(r*r)); return k0*(k0 - 1.0)/max(k1, 1e-5); }
float box(float3 p, float3 e, float r) { float3 q = abs(p) - e + r; return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0) - r; }

const float3 CENTRE = float3(0.0, 1.15, 0.0);

// a puffy heart of two tilted lobes, unit size about 1
float heart(float3 p) {
  float3 a = rz(float3(p.x, p.y, p.z) - float3(0.33, 0.1, 0.0), 0.56);
  float3 b = rz(float3(-p.x, p.y, p.z) - float3(0.33, 0.1, 0.0), 0.56);
  return smin(ell(a, float3(0.56, 0.86, 0.46)), ell(b, float3(0.56, 0.86, 0.46)), 0.16);
}

// the bubble: a rounded slab with a tail; and (out) whether this is the white heart on its face
float bubble(float3 p, out float white) {
  float s = 1.0 + 0.07*uBubble.y;
  p = (p - CENTRE)/s;
  p = rz(p, uBubble.z*0.08);
  float body = box(p, float3(1.1, 0.62, 0.2), 0.2);
  // the tail: a rounded diamond half sunk into the body's lower edge, a little left of the middle
  float tail = box(rz(p - float3(-0.1, -0.74, 0.0), PI*0.25), float3(0.19, 0.19, 0.15), 0.07);
  float d = smin(body, tail, 0.1);
  // the raised heart on the face, on the left
  float h = heart((p - float3(-0.5, -0.02, 0.27))/0.34)*0.34;
  h = max(h, -(p.z - 0.12));
  white = h < d ? 1.0 : 0.0;
  return min(d, h)*s*0.9;
}

float flyer(float3 p, float4 h, float i) {
  if (h.w <= 0.001) return 1e3;
  float3 q = p - h.xyz;
  q = ry(rz(q, sin(i*1.7)*0.5), cos(i*2.3)*0.7);
  return heart(q/h.w)*h.w*0.85;
}

float sceneD(float3 p, out float white) {
  float d = bubble(p, white);
  float hs = 1e3;
  hs = min(hs, flyer(p, uH0, 0.0));
  hs = min(hs, flyer(p, uH1, 1.0));
  hs = min(hs, flyer(p, uH2, 2.0));
  hs = min(hs, flyer(p, uH3, 3.0));
  hs = min(hs, flyer(p, uH4, 4.0));
  hs = min(hs, flyer(p, uH5, 5.0));
  if (hs < d) white = 0.0;
  return smin(d, hs, 0.14);
}
float sd(float3 p) { float w; return sceneD(p, w); }

float3 toP3(float3 c) {
  float3 l = pow(max(c, float3(0.0)), float3(2.2));
  float3 m = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(max(m, float3(0.0)), float3(1.0/2.2));
}

float shadowOf(float2 g, float4 h) {
  if (h.w <= 0.001) return 0.0;
  float2 q = (g - h.xz)/(h.w*(1.0 + h.y*0.6));
  return 0.35*exp(-dot(q, q)*1.5)/(1.0 + h.y)*smoothstep(0.0, 0.4, h.y);
}

half4 main(float2 xy) {
  float ppu = uView.z;
  float2 sp = (xy - float2(uView.x*0.5, uBubble.x))/ppu;
  sp.y = -sp.y;
  float tilt = uBubble.w;
  float3 fwd = normalize(float3(0.0, -sin(tilt), -cos(tilt)));
  float3 up = normalize(float3(0.0, cos(tilt), -sin(tilt)));
  float3 ro = CENTRE + float3(sp.x, 0.0, 0.0) + up*sp.y - fwd*6.0;
  float3 rd = fwd;
  // the floor at y = 0: pale, a faint grid, soft shadows
  float tf = -ro.y/rd.y;
  float3 fp = ro + rd*tf;
  float2 gr = abs(fract(fp.xz*0.8) - 0.5);
  float grid = 1.0 - smoothstep(0.0, 0.015/max(0.4, 1.0), min(gr.x, gr.y));
  float3 floorC = float3(0.9, 0.9, 0.92) - float3(0.05)*grid*exp(-length(fp.xz)*0.25);
  float2 bq = (fp.xz - float2(0.0, 0.0))/float2(1.6, 0.6);
  float sh = 0.4*exp(-dot(bq, bq)*1.4);
  sh += shadowOf(fp.xz, uH0) + shadowOf(fp.xz, uH1) + shadowOf(fp.xz, uH2) + shadowOf(fp.xz, uH3) + shadowOf(fp.xz, uH4) + shadowOf(fp.xz, uH5);
  float3 bg = mix(floorC, float3(0.94, 0.94, 0.96), smoothstep(-2.0, -6.0, fp.z))*(1.0 - min(sh, 0.6));
  float px = 1.0/(ppu*uView.w);
  float t = 3.0, best = 1e4, bestT = t;
  bool hit = false;
  for (int i = 0; i < 72; i++) {
    float d = sd(ro + rd*t);
    if (d < best) { best = d; bestT = t; }
    if (d < 0.35*px) { hit = true; break; }
    t += d*0.9;
    if (t > 14.0) break;
  }
  float cover = hit ? 1.0 : 1.0 - smoothstep(0.35*px, 1.4*px, best);
  if (cover <= 0.0) return half4(half3(toP3(bg)), 1.0);
  float3 p = ro + rd*bestT;
  if (hit) { for (int i = 0; i < 2; i++) p += rd*sd(p); }
  float white;
  sceneD(p, white);
  float e = max(0.002, px);
  const float2 k = float2(1.0, -1.0);
  float3 n = normalize(k.xyy*sd(p + k.xyy*e) + k.yyx*sd(p + k.yyx*e) + k.yxy*sd(p + k.yxy*e) + k.xxx*sd(p + k.xxx*e));
  float ao = 0.0;
  for (int j = 0; j < 3; j++) { float hh = 0.03 + 0.06*float(j); ao += max(0.0, hh - sd(p + n*hh))/hh/(1.0 + float(j)); }
  ao = clamp(1.0 - ao*0.9, 0.5, 1.0);
  float3 V = -rd;
  float3 key = normalize(float3(-0.45, 0.8, 0.55));
  float nl = clamp((dot(n, key) + 0.35)/1.35, 0.0, 1.0);
  float ndv = max(dot(n, V), 0.0);
  float hn = max(dot(n, normalize(key + V)), 0.0);
  float3 base = mix(float3(1.0, 0.2, 0.38), float3(1.0, 1.0, 1.0), white);
  float3 col = mix(base*float3(0.72, 0.55, 0.66), base, nl)*(0.78 + 0.32*nl)*ao;
  col += float3(1.0, 0.97, 0.95)*(0.25*pow(hn, 22.0) + 0.6*pow(hn, 160.0))*ao;
  col += float3(1.0, 0.75, 0.85)*0.25*pow(1.0 - ndv, 2.5);
  // the floor's light bounced back up into the underside
  col += base*0.12*max(-n.y, 0.0);
  float3 ex = max(col - 0.9, 0.0);
  col = min(col, float3(0.9)) + ex/(1.0 + ex/0.1);
  col = mix(bg, col, cover);
  return half4(half3(toP3(clamp(col, 0.0, 1.0))), 1.0);
}
`;
