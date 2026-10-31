package com.ngocdevv.nativecook.blocks.gestures.clayreactionmorph

// The reference SkSL, with layer pixels converted to logical points at entry.
internal const val ClayReactionMorphAgsl = """
uniform float uDensity;
uniform float4 uObj;    // centre x, y (pt), size (pt, the unit radius), opacity
uniform float4 uMorph;  // reaction 0…3, yaw, lean (finger speed), squash (+ flat, − tall)
uniform float4 uLive;   // time (s), how alive the expression is 0…1, halo 0…1, pixel ratio
uniform float4 uHole;   // the held bubble: x, y, w, h (pt)
uniform float4 uDim;    // backdrop dim 0…1, bubble corner radius (pt), bubble scale, page luminance
uniform float4 uSeat;   // the reaction's seat on the bubble: x, y, radius (pt), presence 0…1

const float PI = 3.14159265;
float sq(float v) { return v*v; }
float dot2(float2 v) { return dot(v, v); }
float smin(float a, float b, float k) { float h = clamp(0.5 + 0.5*(b - a)/k, 0.0, 1.0); return mix(b, a, h) - k*h*(1.0 - h); }
float smax(float a, float b, float k) { float h = clamp(0.5 - 0.5*(b - a)/k, 0.0, 1.0); return mix(b, a, h) + k*h*(1.0 - h); }
float3 rx(float3 p, float a) { float c = cos(a), s = sin(a); return float3(p.x, c*p.y - s*p.z, s*p.y + c*p.z); }
float3 ry(float3 p, float a) { float c = cos(a), s = sin(a); return float3(c*p.x + s*p.z, p.y, -s*p.x + c*p.z); }
float3 rz(float3 p, float a) { float c = cos(a), s = sin(a); return float3(c*p.x - s*p.y, s*p.x + c*p.y, p.z); }
float2 r2(float2 p, float a) { float c = cos(a), s = sin(a); return float2(c*p.x - s*p.y, s*p.x + c*p.y); }
float ell(float3 p, float3 r) { float k0 = length(p/r); float k1 = length(p/(r*r)); return k0*(k0 - 1.0)/max(k1, 1e-5); }
float ell2(float2 p, float2 r) { float k0 = length(p/r); float k1 = length(p/(r*r)); return k0*(k0 - 1.0)/max(k1, 1e-5); }
float box(float3 p, float3 e, float r) { float3 q = abs(p) - e + r; return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0) - r; }
float cap(float3 p, float3 a, float3 b, float r) { float3 pa = p - a, ba = b - a; float h = clamp(dot(pa, ba)/dot(ba, ba), 0.0, 1.0); return length(pa - ba*h) - r; }
float rrect(float2 p, float2 e, float r) { float2 q = abs(p) - e + r; return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r; }
// an arc of a circle of radius R round c, symmetric about the vertical, its middle at the top
float arc2(float2 p, float2 c, float R, float span) {
  float2 q = p - c; q.x = abs(q.x);
  if (atan(q.x, q.y) < span) return abs(length(q) - R);
  return length(q - float2(sin(span), cos(span))*R);
}
float heart2(float2 p) {
  p.x = abs(p.x);
  if (p.y + p.x > 1.0) return sqrt(dot2(p - float2(0.25, 0.75))) - 0.353553;
  return sqrt(min(dot2(p - float2(0.0, 1.0)), dot2(p - 0.5*max(p.x + p.y, 0.0))))*sign(p.x - p.y);
}

struct Part { float d; float3 rgb; float gloss; };
Part P(float d, float3 rgb, float gloss) { Part p; p.d = d; p.rgb = rgb; p.gloss = gloss; return p; }
Part nearer(Part a, Part b) { return a.d < b.d ? a : b; }

const float3 BLUE = float3(0.22, 0.5, 1.0);
const float3 NAIL = float3(0.62, 0.8, 1.0);
const float3 RED = float3(1.0, 0.17, 0.3);
const float3 SUN = float3(1.0, 0.8, 0.2);
const float3 AMBER = float3(1.0, 0.62, 0.14);
const float3 INK = float3(0.24, 0.12, 0.07);
const float3 MOUTH = float3(0.4, 0.06, 0.1);
const float3 TONGUE = float3(1.0, 0.4, 0.47);
const float3 TEETH = float3(1.0, 0.97, 0.92);
const float3 TEAR = float3(0.4, 0.8, 1.0);

// Like: a fist of four curled fingers seen from the front, knuckles to the right, the thumb up
Part like(float3 p) {
  p.y += 0.04; p.x += 0.1;
  float palm = box(p - float3(0.14, -0.3, -0.12), float3(0.46, 0.46, 0.34), 0.3);
  float f = 1e4;
  for (int i = 0; i < 4; i++) {
    float fi = float(i);
    float y = 0.12 - 0.265*fi;
    f = smin(f, cap(p, float3(-0.2, y, 0.17), float3(0.55 - 0.05*fi, y - 0.01, 0.12), 0.165 - 0.012*fi), 0.035);
  }
  float thumb = cap(p, float3(-0.27, -0.06, 0.1), float3(-0.17, 0.8, 0.04), 0.245);
  float d = smin(palm, f, 0.07);
  d = smin(d, thumb, 0.13);
  Part h = P(d, BLUE, 0.55);
  // a lighter nail on the thumb's tip
  if (ell(p - float3(-0.16, 0.8, 0.26), float3(0.15, 0.16, 0.09)) < 0.0) h.rgb = NAIL;
  return h;
}

// Love: two tilted clay lobes pressed together into a puffy heart
float lobe(float3 p, float side) {
  float3 q = rz(float3(p.x*side, p.y, p.z) - float3(0.33, 0.1, 0.0), 0.56);
  return ell(q, float3(0.56, 0.86, 0.46));
}
Part love(float3 p) {
  float d = smin(lobe(p, 1.0), lobe(p, -1.0), 0.16);
  return P(d, RED, 0.75);
}

float face(float3 p) { return ell(p, float3(1.0, 0.96, 0.84)); }

// Haha: closed eyes, an open grin with teeth and tongue, a tear on each side
Part haha(float3 p) {
  float f = face(p);
  float2 uv = p.xy;
  float m2 = smax(ell2(uv - float2(0.0, -0.1), float2(0.56, 0.5)), uv.y + 0.1, 0.06);
  float cav = max(m2, 0.32 - p.z);
  float d = smax(f, -cav, 0.05);
  Part h = P(d, SUN, 0.45);
  if (m2 < 0.03 && f < -0.015) h.rgb = uv.y > -0.25 ? TEETH : MOUTH;
  float2 e = float2(abs(uv.x), uv.y);
  float eye = arc2(r2(e - float2(0.34, 0.2), -0.22), float2(0.0, -0.06), 0.17, 1.2);
  Part eyes = P(length(float2(eye, f)) - 0.058, INK, 0.6);
  Part tongue = P(ell(p - float3(0.0, -0.46, 0.4), float3(0.26, 0.14, 0.2)), TONGUE, 0.7);
  float3 t = float3(abs(p.x), p.y, p.z) - float3(0.84, -0.02, 0.36);
  float tear = smin(ell(t, float3(0.15, 0.2, 0.12)), length(t - float3(-0.04, 0.24, -0.02)) - 0.05, 0.12);
  Part tears = P(tear, TEAR, 1.0);
  return nearer(nearer(h, eyes), nearer(tongue, tears));
}

// Wow: round glossy eyes, raised brows, an O mouth with a puckered lip
Part wow(float3 p) {
  float f = face(p);
  float2 uv = p.xy;
  float m2 = ell2(uv - float2(0.0, -0.4), float2(0.19, 0.25));
  float cav = max(m2, 0.4 - p.z);
  float d = smax(f, -cav, 0.04);
  d = smin(d, length(float2(m2 - 0.04, f)) - 0.06, 0.04);
  Part h = P(d, AMBER, 0.45);
  if (m2 < 0.02 && f < -0.015) h.rgb = MOUTH;
  float3 e = float3(abs(p.x), p.y, p.z);
  Part eyes = P(ell(e - float3(0.32, 0.16, 0.8), float3(0.11, 0.16, 0.1)), INK, 1.0);
  float brow = arc2(float2(abs(uv.x), uv.y), float2(0.33, 0.3), 0.22, 0.85);
  Part brows = P(length(float2(brow, f)) - 0.05, INK, 0.5);
  return nearer(h, nearer(eyes, brows));
}

Part shape(float3 p, float k) {
  if (k < 0.5) return like(p);
  if (k < 1.5) return love(p);
  if (k < 2.5) return haha(p);
  return wow(p);
}

float3 bodyRgb(float k) { return k < 0.5 ? BLUE : k < 1.5 ? RED : k < 2.5 ? SUN : AMBER; }

float3 reactionRgb(float r) {
  float3 c = mix(BLUE, RED, clamp(r, 0.0, 1.0));
  c = mix(c, SUN, clamp(r - 1.0, 0.0, 1.0));
  return mix(c, AMBER, clamp(r - 2.0, 0.0, 1.0));
}

// the pair of reactions at r and how far the clay has melted from the first to the second
float3 pairAt(float r) {
  float rc = clamp(r, 0.0, 3.0);
  float a = min(floor(rc), 2.0);
  float w = smoothstep(0.15, 0.85, rc - a);
  return float3(a, a + 1.0, w);
}

// the object's own space: squashed, leaning with the finger, turning, alive
float3 toObject(float3 p) {
  float sqz = uMorph.w;
  p.y /= 1.0 - 0.24*sqz;
  p.xz /= 1.0 + 0.16*sqz;
  float r = clamp(uMorph.x, 0.0, 3.0);
  float near = 1.0 - sin(PI*clamp(pairAt(r).z, 0.0, 1.0));
  float live = uLive.y*near;
  float t = uLive.x;
  float k = floor(r + 0.5);
  float wave = 0.0, beat = 0.0;
  if (k < 0.5) wave = 0.1*sin(t*5.0);
  else if (k < 1.5) beat = 0.07*pow(max(sin(t*6.3), 0.0), 6.0) + 0.04*pow(max(sin(t*6.3 - 0.9), 0.0), 6.0);
  else if (k < 2.5) { wave = 0.12*sin(t*15.0); p.y -= 0.03*live*abs(sin(t*15.0)); }
  else beat = 0.035*sin(t*2.6);
  p /= 1.0 + beat*live;
  p = rz(p, uMorph.z*0.55 + wave*live);
  p = ry(p, uMorph.y);
  p = rx(p, -0.1 + 0.25*uMorph.z*uMorph.z);
  return p;
}

float sceneD(float3 p) {
  float sqz = uMorph.w;
  float lip = min(1.0 - 0.24*sqz, 1.0 + 0.16*sqz);
  float3 q = toObject(p);
  float3 pr = pairAt(uMorph.x);
  float d;
  if (pr.z < 0.001) d = shape(q, pr.x).d;
  else if (pr.z > 0.999) d = shape(q, pr.y).d;
  else d = mix(shape(q, pr.x).d, shape(q, pr.y).d, pr.z) - 0.1*sin(PI*pr.z);
  return d*lip*0.92;
}

float3 toLab(float3 c) {
  c = pow(max(c, float3(0.0)), float3(2.2));
  float3 l = float3(dot(c, float3(0.412221, 0.536333, 0.051446)), dot(c, float3(0.211903, 0.680700, 0.107397)), dot(c, float3(0.088302, 0.281719, 0.629979)));
  l = pow(max(l, float3(0.0)), float3(0.333333));
  return float3(dot(l, float3(0.210454, 0.793618, -0.004072)), dot(l, float3(1.977998, -2.428592, 0.450594)), dot(l, float3(0.025904, 0.782772, -0.808676)));
}
float3 fromLab(float3 c) {
  float3 l = float3(c.x + 0.396338*c.y + 0.215804*c.z, c.x - 0.105561*c.y - 0.063854*c.z, c.x - 0.089484*c.y - 1.291486*c.z);
  l = l*l*l;
  float3 rgb = float3(dot(l, float3(4.076742, -3.307712, 0.230970)), dot(l, float3(-1.268438, 2.609757, -0.341319)), dot(l, float3(-0.004196, -0.703419, 1.707615)));
  return pow(max(rgb, float3(0.0)), float3(1.0/2.2));
}

Part surfaceAt(float3 p) {
  float3 q = toObject(p);
  float3 pr = pairAt(uMorph.x);
  if (pr.z < 0.001) return shape(q, pr.x);
  if (pr.z > 0.999) return shape(q, pr.y);
  Part a = shape(q, pr.x), b = shape(q, pr.y);
  // the bodies' colours melt evenly; a feature (eyes, mouth, tears, nail) keeps its colour where its
  // own surface is the one here, instead of smearing into the other face
  float k = pr.z;
  if (length(a.rgb - bodyRgb(pr.x)) + length(b.rgb - bodyRgb(pr.y)) > 0.05) {
    float wa = (1.0 - pr.z)*exp(-max(a.d, 0.0)/0.04);
    float wb = pr.z*exp(-max(b.d, 0.0)/0.04);
    k = wb/max(wa + wb, 1e-4);
  }
  return P(0.0, fromLab(mix(toLab(a.rgb), toLab(b.rgb), k)), mix(a.gloss, b.gloss, k));
}

float3 nrm(float3 p, float e) {
  const float2 k = float2(1.0, -1.0);
  return normalize(k.xyy*sceneD(p + k.xyy*e) + k.yyx*sceneD(p + k.yyx*e) + k.yxy*sceneD(p + k.yxy*e) + k.xxx*sceneD(p + k.xxx*e));
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
// paint c with alpha a over a premultiplied (col, alpha)
float4 over(float4 under, float3 c, float a) { return float4(c*a + under.rgb*(1.0 - a), a + under.a*(1.0 - a)); }

half4 main(float2 fragCoord) {
  float2 xy = fragCoord / uDensity;
  float px = 1.0/uLive.w;
  // the chat dims, except the held bubble
  float2 hc = uHole.xy + uHole.zw*0.5;
  float hd = rrect(xy - hc, uHole.zw*0.5*uDim.z, uDim.y*uDim.z);
  float4 acc = float4(0.0);
  acc = over(acc, float3(0.0, 0.0, 0.02), uDim.x*0.64*smoothstep(-0.6, 0.6, hd));

  float S = uObj.z;
  float2 uv = (xy - uObj.xy)/S;
  uv.y = -uv.y;
  float rad = length(uv);
  // a halo of the reaction's light on the dim
  float3 tint = reactionRgb(uMorph.x);
  float halo = uLive.z*0.42*exp(-sq(max(rad - 0.55, 0.0)/0.75))*uObj.w;
  acc = float4(acc.rgb + tint*halo, acc.a + halo*(1.0 - acc.a));

  // the seat: a dark disc ringed in the page's colour, on the bubble's corner
  float sd = length(xy - uSeat.xy) - uSeat.z;
  acc = over(acc, float3(uDim.w), uSeat.w*(1.0 - smoothstep(-0.6, 0.6, sd - 2.5)));
  acc = over(acc, float3(0.17, 0.17, 0.2), uSeat.w*(1.0 - smoothstep(-0.6, 0.6, sd)));

  float b2 = 1.45*1.45 - dot(uv, uv);
  if (uObj.w <= 0.0 || b2 <= 0.0 || S < 1.0) {
    return acc.a <= 0.0 ? half4(0.0) : premul(acc.rgb/acc.a, acc.a);
  }
  float pxo = px/S;
  float zEnd = -sqrt(b2);
  float z = -zEnd;
  float best = 1e4, bestZ = z;
  bool hit = false;
  for (int i = 0; i < 64; i++) {
    float d = sceneD(float3(uv, z));
    if (d < best) { best = d; bestZ = z; }
    if (d < 0.3*pxo) { hit = true; break; }
    z -= max(d*0.9, 0.35*pxo);
    if (z < zEnd) break;
  }
  // two more steps settle the hit onto the surface, so the shading has no rings
  if (hit) { for (int i = 0; i < 2; i++) bestZ -= sceneD(float3(uv, bestZ)); }
  float cover = (hit ? 1.0 : 1.0 - smoothstep(0.3*pxo, 1.3*pxo, best))*uObj.w;
  if (cover <= 0.0) return acc.a <= 0.0 ? half4(0.0) : premul(acc.rgb/acc.a, acc.a);
  float3 p = float3(uv, bestZ);
  float3 n = nrm(p, max(0.004, 1.2*pxo));
  Part s = surfaceAt(p);
  float ao = 0.0;
  for (int i = 0; i < 3; i++) {
    float h = 0.035 + float(i)*0.07;
    ao += max(0.0, h - sceneD(p + n*h))/h/(1.0 + float(i));
  }
  ao = clamp(1.0 - ao*0.85, 0.45, 1.0);
  float3 V = float3(0.0, 0.0, 1.0);
  float3 key = normalize(float3(-0.55, 0.7, 0.85));
  float3 fill = normalize(float3(0.8, -0.1, 0.5));
  float nl = clamp((dot(n, key) + 0.4)/1.4, 0.0, 1.0);
  float3 base = s.rgb;
  float3 shade = mix(base*float3(0.62, 0.52, 0.7), base, 0.3);
  float3 col = mix(shade, base, nl)*(0.62 + 0.42*nl)*ao;
  col += base*float3(0.12, 0.12, 0.2)*max(dot(n, fill), 0.0);
  float3 H = normalize(key + V);
  float hn = max(dot(n, H), 0.0);
  col += float3(1.0, 0.96, 0.9)*((0.08 + 0.22*s.gloss)*pow(hn, 16.0) + 0.42*s.gloss*pow(hn, 120.0))*ao;
  // a cool rim from behind, and the reaction's own light bounced back from the page below
  float rim = pow(1.0 - max(dot(n, V), 0.0), 3.0);
  col += float3(0.55, 0.62, 1.0)*rim*0.32*max(dot(n, normalize(float3(0.7, 0.5, -0.3))), 0.15);
  col += tint*0.18*max(-n.y, 0.0)*uLive.z;
  // a shoulder so saturated clay keeps its form instead of clipping flat
  float3 ex = max(col - 0.86, 0.0);
  col = min(col, float3(0.86)) + ex/(1.0 + ex/0.14);
  acc = over(acc, col, cover);
  return premul(acc.rgb/max(acc.a, 1e-4), acc.a);
}
"""
