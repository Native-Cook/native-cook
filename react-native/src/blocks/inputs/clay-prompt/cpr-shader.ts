// Clay Prompt — the clay objects a prompt calls up, and the picture they become.
//
// One SkSL effect over the screen. Up to four clay objects (cat, sofa, moon, plant) live in one
// orthographic world measured in points, each with its own place, size, presence and turn, and are
// ray-marched together, so they can melt into each other: a smooth union whose radius (uMerge.z)
// grows while Generate pulls them together, and a pull towards one round blob (uMerge.w). Each
// object is sculpted from a few rounded primitives with clay eyes, nose and legs as their own
// colours; colours of touching clay mix in OKLab. Lighting is a soft studio by day and, inside the
// result card, a night room lit by the moon (uNight), with contact shadows on the floor. The card
// itself (wall, window of stars, floor) is drawn in 2D and opens as a circle from the blob.
// Premultiplied, sRGB → Display P3.
export const CPR_SKSL = `
uniform float4 uO0;   // object 0: centre x, y (pt), size (pt), presence 0…1
uniform float4 uO1;
uniform float4 uO2;
uniform float4 uO3;
uniform float4 uP0;   // object 0: kind (0 cat, 1 sofa, 2 moon, 3 plant), turn, squash, glow
uniform float4 uP1;
uniform float4 uP2;
uniform float4 uP3;
uniform float4 uMerge; // blob centre x, y (pt), smooth-union radius (pt), pull to a blob 0…1
uniform float4 uCard;  // result card x, y, w, h (pt)
uniform float2 uReveal; // card opened 0…1, night light 0…1
uniform float uBlobR;   // blob radius (pt)
uniform float uFade;    // the card's opacity (the wipe)
uniform float uTime;
uniform float uPixelRatio;

const float PI = 3.14159265;
float sq(float v) { return v*v; }
float smin(float a, float b, float k) { if (k <= 0.0) return min(a, b); float h = clamp(0.5 + 0.5*(b - a)/k, 0.0, 1.0); return mix(b, a, h) - k*h*(1.0 - h); }
float smax(float a, float b, float k) { float h = clamp(0.5 - 0.5*(b - a)/k, 0.0, 1.0); return mix(b, a, h) + k*h*(1.0 - h); }
float3 rx(float3 p, float a) { float c = cos(a), s = sin(a); return float3(p.x, c*p.y - s*p.z, s*p.y + c*p.z); }
float3 ry(float3 p, float a) { float c = cos(a), s = sin(a); return float3(c*p.x + s*p.z, p.y, -s*p.x + c*p.z); }
float3 rz(float3 p, float a) { float c = cos(a), s = sin(a); return float3(c*p.x - s*p.y, s*p.x + c*p.y, p.z); }
float ell(float3 p, float3 r) { float k0 = length(p/r); float k1 = length(p/(r*r)); return k0*(k0 - 1.0)/max(k1, 1e-5); }
float box(float3 p, float3 e, float r) { float3 q = abs(p) - e + r; return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0) - r; }
float cap(float3 p, float3 a, float3 b, float r) { float3 pa = p - a, ba = b - a; float h = clamp(dot(pa, ba)/dot(ba, ba), 0.0, 1.0); return length(pa - ba*h) - r; }
float rcone(float3 p, float3 a, float3 b, float ra, float rb) {
  float3 pa = p - a, ba = b - a; float h = clamp(dot(pa, ba)/dot(ba, ba), 0.0, 1.0);
  return length(pa - ba*h) - mix(ra, rb, h);
}

// a coloured distance: the body and its details
struct Part { float d; float3 rgb; float gloss; float glow; };
Part partG(float d, float3 rgb, float gloss, float glow) { Part p; p.d = d; p.rgb = rgb; p.gloss = gloss; p.glow = glow; return p; }
Part part(float d, float3 rgb, float gloss) { return partG(d, rgb, gloss, 0.0); }
Part nearer(Part a, Part b) { return a.d < b.d ? a : b; }

const float3 GINGER = float3(0.98, 0.56, 0.22);
const float3 CREAM = float3(1.0, 0.9, 0.78);
const float3 INK = float3(0.13, 0.09, 0.12);
const float3 PINK = float3(1.0, 0.52, 0.62);
const float3 SOFA = float3(0.27, 0.55, 0.92);
const float3 WOOD = float3(0.45, 0.27, 0.18);
const float3 MOON = float3(1.0, 0.84, 0.36);
const float3 POT = float3(0.88, 0.46, 0.30);
const float3 LEAF = float3(0.36, 0.76, 0.40);

Part cat(float3 p) {
  float body = ell(p - float3(0.0, -0.38, 0.0), float3(0.56, 0.5, 0.46));
  float head = ell(p - float3(0.0, 0.3, 0.1), float3(0.46, 0.39, 0.41));
  float ears = min(rcone(float3(abs(p.x), p.y, p.z), float3(0.22, 0.52, 0.08), float3(0.34, 0.86, 0.06), 0.15, 0.035),
                   1e3);
  float tail = cap(p, float3(0.38, -0.78, -0.05), float3(0.78, -0.42, 0.0), 0.1);
  tail = smin(tail, cap(p, float3(0.78, -0.42, 0.0), float3(0.74, -0.08, 0.05), 0.09), 0.06);
  float paws = ell(float3(abs(p.x), p.y, p.z) - float3(0.19, -0.84, 0.34), float3(0.15, 0.1, 0.14));
  float d = smin(body, head, 0.14);
  d = smin(d, ears, 0.05);
  d = smin(d, tail, 0.08);
  d = smin(d, paws, 0.06);
  Part c = part(d, GINGER, 0.5);
  // a cream muzzle and chest, printed into the body
  float muzzle = ell(p - float3(0.0, 0.2, 0.38), float3(0.2, 0.13, 0.12));
  float chest = ell(p - float3(0.0, -0.3, 0.3), float3(0.26, 0.32, 0.2));
  if (min(muzzle, chest) < 0.012) c.rgb = CREAM;
  Part eyes = part(length(float3(abs(p.x), p.y, p.z) - float3(0.16, 0.36, 0.43)) - 0.062, INK, 0.9);
  Part nose = part(ell(p - float3(0.0, 0.25, 0.5), float3(0.055, 0.04, 0.04)), PINK, 0.7);
  return nearer(nearer(c, eyes), nose);
}

Part sofa(float3 p) {
  float base = box(p - float3(0.0, -0.42, 0.0), float3(0.98, 0.22, 0.46), 0.13);
  float back = box(p - float3(0.0, 0.14, -0.3), float3(0.9, 0.34, 0.15), 0.15);
  float arms = cap(float3(abs(p.x), p.y, p.z), float3(0.94, -0.12, -0.38), float3(0.94, -0.12, 0.4), 0.21);
  float seat = box(float3(abs(p.x), p.y, p.z) - float3(0.43, -0.16, 0.06), float3(0.41, 0.1, 0.36), 0.1);
  float d = smin(base, back, 0.06);
  d = smin(d, arms, 0.05);
  d = smin(d, seat, 0.04);
  Part s = part(d, SOFA, 0.45);
  Part legs = part(cap(float3(abs(p.x), p.y, abs(p.z)), float3(0.76, -0.66, 0.3), float3(0.76, -0.8, 0.3), 0.065), WOOD, 0.5);
  return nearer(s, legs);
}

Part moon(float3 p) {
  // a crescent drawn in 2D, then puffed into a clay pillow
  float2 q = p.xy;
  float d2 = smax(length(q) - 0.74, -(length(q - float2(0.36, 0.26)) - 0.6), 0.05);
  float2 w = float2(d2 + 0.16, abs(p.z) - 0.1);
  float d = min(max(w.x, w.y), 0.0) + length(max(w, 0.0)) - 0.16;
  // two soft craters pressed into the front
  d = smax(d, -(length(p - float3(-0.48, -0.08, 0.3)) - 0.11), 0.04);
  d = smax(d, -(length(p - float3(-0.2, -0.46, 0.3)) - 0.08), 0.03);
  return partG(d, MOON, 0.55, 1.0);
}

Part plant(float3 p) {
  float pot = rcone(p, float3(0.0, -0.86, 0.0), float3(0.0, -0.28, 0.0), 0.3, 0.42);
  pot = max(pot, abs(p.y + 0.57) - 0.31);
  float rim = length(float2(length(p.xz) - 0.42, p.y + 0.26)) - 0.075;
  Part pp = part(smin(pot, rim, 0.04), POT, 0.4);
  float3 q = p - float3(0.0, -0.22, 0.0);
  float l1 = ell(rz(q, 0.0) - float3(0.0, 0.5, 0.0), float3(0.17, 0.5, 0.09));
  float l2 = ell(rz(q, 0.62) - float3(0.0, 0.42, 0.04), float3(0.15, 0.42, 0.08));
  float l3 = ell(rz(q, -0.62) - float3(0.0, 0.42, -0.04), float3(0.15, 0.42, 0.08));
  Part leaves = part(smin(l1, smin(l2, l3, 0.05), 0.05), LEAF, 0.55);
  return nearer(pp, leaves);
}

// one object in its own space: tilted a little towards the camera, turned, squashed
Part object(float3 p, float4 o, float4 a) {
  if (o.w <= 0.001) { return part(1e4, float3(0.0), 0.0); }
  float s = o.z*o.w;
  float3 q = (p - float3(o.x, -o.y, 0.0))/s;
  float sq0 = a.z;
  q.y /= 1.0 - sq0; q.xz /= 1.0 + 0.5*sq0;
  q = rx(q, -0.32);
  q = ry(q, a.y);
  Part r;
  if (a.x < 0.5) r = cat(q); else if (a.x < 1.5) r = sofa(q); else if (a.x < 2.5) r = moon(q); else r = plant(q);
  r.d *= s*(1.0 - 0.5*abs(sq0));
  r.glow *= a.w;
  return r;
}

float boundOf(float3 p, float4 o) { return o.w <= 0.001 ? 1e4 : length(p - float3(o.x, -o.y, 0.0)) - 1.2*o.z*o.w; }

// the whole clay world: objects joined by a smooth union of radius uMerge.z, pulled into a blob
float sceneD(float3 p) {
  float k = uMerge.z;
  float d = 1e4;
  if (boundOf(p, uO0) < k + 4.0) d = smin(d, object(p, uO0, uP0).d, k);
  else d = smin(d, boundOf(p, uO0) + 2.0, k);
  if (boundOf(p, uO1) < k + 4.0) d = smin(d, object(p, uO1, uP1).d, k);
  else d = smin(d, boundOf(p, uO1) + 2.0, k);
  if (boundOf(p, uO2) < k + 4.0) d = smin(d, object(p, uO2, uP2).d, k);
  else d = smin(d, boundOf(p, uO2) + 2.0, k);
  if (boundOf(p, uO3) < k + 4.0) d = smin(d, object(p, uO3, uP3).d, k);
  else d = smin(d, boundOf(p, uO3) + 2.0, k);
  if (uMerge.w > 0.0) {
    float b = length(p - float3(uMerge.x, -uMerge.y, 0.0)) - uBlobR;
    d = mix(d, smin(d, b, k), uMerge.w);
  }
  return d;
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

// colour where the clay is: the nearest object's colour, mixed with its neighbours where they melt
Part surfaceAt(float3 p) {
  Part a = object(p, uO0, uP0), b = object(p, uO1, uP1), c = object(p, uO2, uP2), e = object(p, uO3, uP3);
  float k = max(uMerge.z, 0.5);
  // while the clay melts the colours do not average to mud: they marble, like kneaded plasticine
  float marble = smoothstep(0.05, 0.6, uMerge.w)*k*0.9;
  float3 q = p/18.0;
  float na = sin(q.x*1.7 + sin(q.y*2.3 + q.z)*2.0), nb = sin(q.y*1.9 + sin(q.z*2.1 + q.x)*2.0);
  float nc = sin(q.z*2.2 + sin(q.x*1.6 + q.y)*2.0), ne = sin((q.x + q.y)*1.3 + sin(q.z*1.7)*2.0);
  float ad = a.d + na*marble, bd = b.d + nb*marble, cd = c.d + nc*marble, ed = e.d + ne*marble;
  float m = min(min(a.d, b.d), min(c.d, e.d));
  float mm = min(min(ad, bd), min(cd, ed));
  float soft = mix(0.5*k, 0.22*k + 1.0, smoothstep(0.05, 0.4, uMerge.w));
  float wa = exp(-(ad - mm)/soft), wb = exp(-(bd - mm)/soft), wc = exp(-(cd - mm)/soft), we = exp(-(ed - mm)/soft);
  float W = wa + wb + wc + we;
  float3 lab = (toLab(a.rgb)*wa + toLab(b.rgb)*wb + toLab(c.rgb)*wc + toLab(e.rgb)*we)/W;
  Part r;
  r.d = m;
  r.rgb = fromLab(lab);
  r.gloss = (a.gloss*wa + b.gloss*wb + c.gloss*wc + e.gloss*we)/W;
  r.glow = (a.glow*wa + b.glow*wb + c.glow*wc + e.glow*we)/W;
  return r;
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
float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7)))*43758.5453); }
float rrect(float2 p, float2 e, float r) { float2 q = abs(p) - e + r; return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r; }

// the result card: a night room with a window of stars and a warm floor
float4 card(float2 xy) {
  float2 c = uCard.xy + uCard.zw*0.5;
  float d = rrect(xy - c, uCard.zw*0.5, 28.0);
  float open = uReveal.x;
  if (open <= 0.0) return float4(0.0);
  float2 bc = uMerge.xy;
  float far = length(max(abs(bc - uCard.xy), abs(bc - uCard.xy - uCard.zw)));
  float circle = length(xy - bc) - open*far*1.05;
  float inside = (1.0 - smoothstep(-0.6, 0.6, max(d, circle)))*uFade;
  if (inside <= 0.0) return float4(0.0);
  float2 u = (xy - uCard.xy)/uCard.zw;
  float3 wall = mix(float3(0.22, 0.19, 0.42), float3(0.13, 0.11, 0.27), u.y);
  // the window, with the night sky
  float2 wc = float2(0.72, 0.27);
  float wd = rrect((u - wc)*uCard.zw, float2(0.2, 0.17)*uCard.zw, 18.0);
  float3 sky = mix(float3(0.10, 0.12, 0.30), float3(0.20, 0.22, 0.48), u.y*2.0);
  float2 st = floor(xy/9.0);
  float2 sp = (st + 0.2 + 0.6*float2(hash(st + 1.7), hash(st + 5.3)))*9.0;
  float star = step(0.93, hash(st))*(1.0 - smoothstep(0.3, 1.3, length(xy - sp)))*(0.65 + 0.35*sin(uTime*2.0 + hash(st + 3.1)*6.28));
  sky += float3(1.0, 0.95, 0.85)*star*0.8;
  float3 col = mix(wall, sky, 1.0 - smoothstep(-0.6, 0.6, wd));
  // the frame of the window
  col = mix(col, float3(0.32, 0.27, 0.52), (1.0 - smoothstep(0.0, 1.2, abs(wd + 3.0) - 3.0))*0.9);
  // moonlight on the wall, a warm floor
  col += float3(0.18, 0.16, 0.3)*exp(-sq(length((u - wc)*float2(1.0, 0.8))/0.35))*uReveal.y;
  float floorY = 0.78;
  col = mix(col, mix(float3(0.42, 0.25, 0.22), float3(0.30, 0.17, 0.16), (u.y - floorY)*4.0), smoothstep(floorY - 0.002, floorY + 0.002, u.y));
  // a soft vignette
  col *= 1.0 - 0.25*sq(length(u - 0.5));
  // a rim of light along the edge while it opens
  col += float3(1.0, 0.8, 0.6)*0.5*(1.0 - smoothstep(0.0, 6.0, abs(circle)))*(1.0 - open);
  return float4(col, inside);
}

float shadowOf(float2 xy, float4 o, float4 a) {
  if (o.w <= 0.001 || (a.x > 1.5 && a.x < 2.5)) return 0.0;
  float s = o.z*o.w;
  float2 q = (xy - float2(o.x, o.y + 0.92*s))/(s*float2(0.85, 0.16));
  return exp(-dot(q, q)*1.4)*0.5;
}

half4 main(float2 xy) {
  float px = 1.0/uPixelRatio;
  float4 bg = card(xy);
  float3 under = bg.rgb*bg.a;
  float underA = bg.a;
  // contact shadows (on the composer or on the card's floor)
  float sh = shadowOf(xy, uO0, uP0) + shadowOf(xy, uO1, uP1) + shadowOf(xy, uO2, uP2) + shadowOf(xy, uO3, uP3);
  sh = clamp(sh, 0.0, 0.65)*(1.0 - uMerge.w);
  under = under*(1.0 - sh);
  underA = underA + sh*(1.0 - underA);

  // is any clay near this pixel?
  float3 p0 = float3(xy.x, -xy.y, 0.0);
  float reach = 1e4;
  reach = min(reach, length(xy - uO0.xy) - 1.25*uO0.z*uO0.w - uMerge.z - (uO0.w > 0.001 ? 0.0 : 1e4));
  reach = min(reach, length(xy - uO1.xy) - 1.25*uO1.z*uO1.w - uMerge.z - (uO1.w > 0.001 ? 0.0 : 1e4));
  reach = min(reach, length(xy - uO2.xy) - 1.25*uO2.z*uO2.w - uMerge.z - (uO2.w > 0.001 ? 0.0 : 1e4));
  reach = min(reach, length(xy - uO3.xy) - 1.25*uO3.z*uO3.w - uMerge.z - (uO3.w > 0.001 ? 0.0 : 1e4));
  if (uMerge.w > 0.0) reach = min(reach, length(xy - uMerge.xy) - uBlobR - uMerge.z);
  if (reach > 0.0) return premul(under/max(underA, 1e-4), underA);

  float zTop = 200.0;
  float t = 0.0;
  float best = 1e4, bestT = 0.0;
  bool hit = false;
  for (int i = 0; i < 64; i++) {
    float3 p = float3(xy.x, -xy.y, zTop - t);
    float d = sceneD(p);
    if (d < best) { best = d; bestT = t; }
    if (d < 0.25*px) { hit = true; break; }
    t += max(d*0.9, 0.3*px);
    if (t > 2.0*zTop) break;
  }
  float cover = hit ? 1.0 : 1.0 - smoothstep(0.25*px, 1.2*px, best);
  if (cover <= 0.0) return premul(under/max(underA, 1e-4), underA);
  float3 p = float3(xy.x, -xy.y, zTop - bestT);
  float e = 0.35;
  float3 n = nrm(p, e);
  Part s = surfaceAt(p);
  // ambient occlusion from the field
  float ao = 0.0;
  for (int i = 0; i < 3; i++) {
    float h = 1.5 + float(i)*3.5;
    ao += max(0.0, h - sceneD(p + n*h))/h/(1.0 + float(i));
  }
  ao = clamp(1.0 - ao*0.9, 0.5, 1.0);
  float3 V = float3(0.0, 0.0, 1.0);
  float night = uReveal.y;
  float3 key = normalize(mix(float3(-0.6, 0.75, 0.9), float3(0.55, 0.6, 0.7), night));
  float3 fill = normalize(float3(0.8, 0.1, 0.5));
  float nl = clamp((dot(n, key) + 0.42)/1.42, 0.0, 1.0);
  float3 base = s.rgb;
  float3 shadowTint = mix(base*float3(0.7, 0.6, 0.72), base, 0.35);
  float3 col = mix(shadowTint, base, nl)*(0.66 + 0.38*nl)*ao;
  col += base*float3(0.1, 0.13, 0.2)*max(dot(n, fill), 0.0);
  float3 H = normalize(key + V);
  float hn = max(dot(n, H), 0.0);
  col += float3(1.0, 0.95, 0.86)*((0.1 + 0.22*s.gloss)*pow(hn, 18.0) + 0.3*s.gloss*pow(hn, 110.0))*ao;
  float rim = pow(1.0 - max(dot(n, V), 0.0), 3.0);
  col += mix(float3(0.45, 0.6, 0.95), float3(0.7, 0.75, 1.0), night)*rim*(0.2 + 0.25*night)*max(dot(n, normalize(float3(0.8, 0.4, -0.2))), 0.2);
  // at night the room is blue and the moon glows
  col = mix(col, col*float3(0.62, 0.66, 0.95) + base*0.05, night*0.6*(1.0 - s.glow));
  col += base*s.glow*(0.28 + 0.4*night);
  // the shoulder keeps saturated clay from clipping flat
  float3 ex = max(col - 0.86, 0.0);
  col = min(col, float3(0.86)) + ex/(1.0 + ex/0.14);
  float3 outC = col*cover + under*(1.0 - cover);
  float outA = cover + underA*(1.0 - cover);
  return premul(outC/max(outA, 1e-4), outA);
}
`;
