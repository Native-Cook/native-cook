// Star Coupon Tear — a glowing puffy star that is pulled down into a coupon ticket.
//
// One SkSL effect over the screen. The object is ray-marched straight on in its own unit space: a
// five-point star puffed into a soft pillow, or a ticket (a rounded slab with a half-round notch in
// each side, a dashed perforation and a raised percent sign), or a blend of the two; the star's points
// are drawn toward the ticket's corners as it stretches. Glossy candy light with a warm glow around it
// on the dark page and sparkles that twinkle while it is a star. Premultiplied, sRGB → Display P3.
export const SCT_SKSL = `
uniform float4 uObj;    // centre x, y (pt), size (pt, unit), opacity
uniform float4 uShape;  // ticket 0…1, turn (rad), tilt (rad), stretch (pull, 0…1)
uniform float4 uLive;   // time (rad, one turn per loop), glow 0…1, sparkles 0…1, pixel ratio

const float PI = 3.14159265;
float sq(float v) { return v*v; }
float smin(float a, float b, float k) { float h = clamp(0.5 + 0.5*(b - a)/k, 0.0, 1.0); return mix(b, a, h) - k*h*(1.0 - h); }
float smax(float a, float b, float k) { float h = clamp(0.5 - 0.5*(b - a)/k, 0.0, 1.0); return mix(b, a, h) + k*h*(1.0 - h); }
float3 rx(float3 p, float a) { float c = cos(a), s = sin(a); return float3(p.x, c*p.y - s*p.z, s*p.y + c*p.z); }
float3 ry(float3 p, float a) { float c = cos(a), s = sin(a); return float3(c*p.x + s*p.z, p.y, -s*p.x + c*p.z); }
float rrect(float2 p, float2 e, float r) { float2 q = abs(p) - e + r; return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r; }
float star2(float2 p, float r, float rf) {
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
// a 2D shape puffed into a pillow whose middle swells
float pillow(float d2, float z, float th, float rr) {
  float t = th + 0.18*clamp(-d2/0.6, 0.0, 1.0);
  float2 w = float2(d2 + rr, abs(z) - t + rr);
  return min(max(w.x, w.y), 0.0) + length(max(w, 0.0)) - rr;
}

float starD(float3 p) {
  float d2 = star2(p.xy - float2(0.0, -0.06), 0.95, 0.5) - 0.06;
  return pillow(d2, p.z, 0.2, 0.18);
}
// the ticket, and (out) whether this point is on the raised percent sign
float ticketD(float3 p, out float sign) {
  float2 q = p.xy;
  float d2 = rrect(q, float2(1.0, 0.6), 0.14);
  // a half-round notch on each side, where the stub is torn off
  d2 = smax(d2, -(length(float2(abs(q.x) - 1.0, q.y)) - 0.17), 0.04);
  float2 w = float2(d2 + 0.1, abs(p.z) - 0.14 + 0.1);
  float d = min(max(w.x, w.y), 0.0) + length(max(w, 0.0)) - 0.1;
  // the percent sign, raised on the face
  float2 s = q - float2(0.28, 0.0);
  float slash = abs(dot(s, normalize(float2(1.0, -0.5)))) - 0.045;
  slash = max(slash, length(s) - 0.36);
  float o1 = abs(length(s - float2(-0.16, 0.2)) - 0.1) - 0.04;
  float o2 = abs(length(s - float2(0.16, -0.2)) - 0.1) - 0.04;
  float pc = min(slash, min(o1, o2));
  float relief = max(pc, abs(p.z - 0.15) - 0.05);
  sign = relief < d ? 1.0 : 0.0;
  return smin(d, relief, 0.01);
}

float sceneD(float3 p, out float sign) {
  float m = clamp(uShape.x, 0.0, 1.0);
  float3 q = rx(ry(p, uShape.y), uShape.z);
  // the pull stretches it downward
  q.y = q.y/(1.0 + 0.25*uShape.w) + 0.1*uShape.w;
  sign = 0.0;
  float s = m < 0.999 ? starD(q) : 1e3;
  float tk = m > 0.001 ? ticketD(q, sign) : 1e3;
  if (m < 0.001) return s*0.85;
  if (m > 0.999) return tk*0.85;
  sign *= smoothstep(0.6, 1.0, m);
  return (mix(s, tk, m) - 0.1*sin(PI*m))*0.85;
}
float sd(float3 p) { float s; return sceneD(p, s); }

float3 toP3(float3 c) {
  float3 l = pow(max(c, float3(0.0)), float3(2.2));
  float3 m = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(max(m, float3(0.0)), float3(1.0/2.2));
}
float hash(float n) { return fract(sin(n*12.9898)*43758.5453); }

half4 main(float2 xy) {
  float S = uObj.z;
  float2 uv = (xy - uObj.xy)/S;
  uv.y = -uv.y;
  float m = clamp(uShape.x, 0.0, 1.0);
  float3 gold = float3(1.0, 0.82, 0.2);
  float3 coral = float3(1.0, 0.55, 0.22);
  float3 tint = mix(gold, coral, m);
  // the glow on the page, and sparkles round the star
  float r = length(uv);
  float glow = uLive.y*0.55*exp(-sq(max(r - 0.6, 0.0)/0.55))*uObj.w;
  float3 acc = tint*glow;
  float alpha = glow;
  for (int i = 0; i < 6; i++) {
    float fi = float(i);
    float a = fi*1.047 + 0.4;
    float2 c = float2(cos(a), sin(a))*(1.25 + 0.15*hash(fi));
    float tw = pow(max(sin(uLive.x*2.0 + fi*1.7), 0.0), 3.0);
    float2 d = abs(uv - c);
    float sp = (exp(-d.x*60.0)*exp(-d.y*8.0) + exp(-d.y*60.0)*exp(-d.x*8.0))*tw*uLive.z*(1.0 - m);
    acc += float3(1.0, 0.95, 0.75)*sp;
    alpha += sp;
  }
  float b2 = 1.4*1.4 - dot(uv, uv)/(1.0 + 0.5*uShape.w);
  if (b2 <= 0.0 || uObj.w <= 0.0) return half4(half3(toP3(min(acc, 1.0))), half(min(alpha, 1.0)));
  float pxo = 1.0/(uLive.w*S);
  float z = 0.9, best = 1e4, bestZ = z;
  bool hit = false;
  for (int i = 0; i < 56; i++) {
    float d = sd(float3(uv, z));
    if (d < best) { best = d; bestZ = z; }
    if (d < 0.3*pxo) { hit = true; break; }
    z -= max(d*0.9, 0.35*pxo);
    if (z < -0.9) break;
  }
  float cover = (hit ? 1.0 : 1.0 - smoothstep(0.3*pxo, 1.3*pxo, best))*uObj.w;
  if (cover > 0.0) {
    float3 p = float3(uv, bestZ);
    if (hit) { for (int i = 0; i < 2; i++) p.z -= sd(p); }
    float e = max(0.003, pxo);
    const float2 k = float2(1.0, -1.0);
    float3 n = normalize(k.xyy*sd(p + k.xyy*e) + k.yyx*sd(p + k.yyx*e) + k.yxy*sd(p + k.yxy*e) + k.xxx*sd(p + k.xxx*e));
    float sgn;
    sceneD(p, sgn);
    float3 base = mix(tint, float3(1.0, 0.98, 0.92), sgn);
    // the dashed perforation across the ticket, near its stub
    float3 q = rx(ry(p, uShape.y), uShape.z);
    float dash = step(0.5, fract(q.y*6.0))*(1.0 - smoothstep(0.012, 0.025, abs(q.x + 0.55)))*smoothstep(0.6, 1.0, m)*step(abs(q.y), 0.52);
    base = mix(base, base*0.7, dash);
    float3 V = float3(0.0, 0.0, 1.0);
    float3 key = normalize(float3(-0.45, 0.7, 0.75));
    float nl = clamp((dot(n, key) + 0.35)/1.35, 0.0, 1.0);
    float hn = max(dot(n, normalize(key + V)), 0.0);
    float ndv = max(dot(n, V), 0.0);
    float3 col = mix(base*float3(0.75, 0.55, 0.45), base, nl)*(0.7 + 0.35*nl);
    col += float3(1.0, 0.98, 0.9)*(0.25*pow(hn, 24.0) + 0.7*pow(hn, 150.0));
    col += tint*0.35*pow(1.0 - ndv, 2.0);
    float3 ex = max(col - 0.9, 0.0);
    col = min(col, float3(0.9)) + ex/(1.0 + ex/0.1);
    acc = col*cover + acc*(1.0 - cover);
    alpha = cover + alpha*(1.0 - cover);
  }
  return half4(half3(toP3(clamp(acc/max(alpha, 1e-4), 0.0, 1.0))*min(alpha, 1.0)), half(min(alpha, 1.0)));
}
`;
