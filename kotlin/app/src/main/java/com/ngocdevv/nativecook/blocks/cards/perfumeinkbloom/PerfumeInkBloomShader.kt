package com.ngocdevv.nativecook.blocks.cards.perfumeinkbloom

internal object PerfumeInkBloomShader {
val source = """
uniform float4 Fbottle;
uniform float4 Fliquid;
const float perfumeInkBloomPI = 3.14159265;
const float perfumeInkBloomTAU = 6.2831853;
const float perfumeInkBloomPITCH = 0.28;
const float3 perfumeInkBloomBODY = float3(100.0, 90.0, 90.0);
const float perfumeInkBloomWALL = 5.0;
const float3 perfumeInkBloomRING_C = float3(80.0, 118.0, 0.0);
const float3 perfumeInkBloomRING_N = float3(0.68, 0.27, 0.68);
const float perfumeInkBloomLEVEL = 58.0;
float perfumeInkBloomSq(float v) { return v * v; }
float3 perfumeInkBloomLin(float3 c) { return pow(c, float3(2.2)); }
float perfumeInkBloomHash1(float n) { return fract(sin(n * 91.3458) * 47453.5453); }
float perfumeInkBloomEase(float x) { float c = clamp(x, 0.0, 1.0); return c * c * (3.0 - 2.0 * c); }
float2x2 perfumeInkBloomRot(float a) { float c = cos(a); float s = sin(a); return float2x2(c, s, -s, c); }

/* ------------------------------------------------------------------ the bottle's frame
 world: pt, origin at the body's centre, x right, y up, z towards the eye. local: the bottle's own,
 y along its axis; the bottle leans in the screen's plane and is seen a little from above. */


float perfumeInkBloomLean() { return Fliquid.z + Fbottle.w; }
float3 perfumeInkBloomToLocal(float3 v) {
  float2 xy = perfumeInkBloomRot(perfumeInkBloomLean()) * v.xy;
  float3 q = float3(xy, v.z);
  float2 yz = perfumeInkBloomRot(-perfumeInkBloomPITCH) * q.yz;
  return float3(q.x, yz.x, yz.y);
}
float3 perfumeInkBloomToWorld(float3 v) {
  float2 yz = perfumeInkBloomRot(perfumeInkBloomPITCH) * v.yz;
  float3 q = float3(v.x, yz.x, yz.y);
  float2 xy = perfumeInkBloomRot(-perfumeInkBloomLean()) * q.xy;
  return float3(xy, q.z);
}

/* ------------------------------------------------------------------ the room (world directions) */

float3 perfumeInkBloomStudio(float3 r) {
  // a white perfumeInkBloomStudio: a big soft box overhead, a tall strip to each side with a dark gap, a grey floor
  float3 c = mix(perfumeInkBloomLin(float3(0.78, 0.8, 0.86)), perfumeInkBloomLin(float3(0.98, 0.99, 1.0)), smoothstep(-0.6, 0.5, r.y));
  c += float3(1.0) * 2.4 * smoothstep(0.55, 0.85, r.y) * (1.0 - smoothstep(0.4, 0.8, abs(r.x)));
  float side = abs(r.x);
  c += float3(1.0) * 1.8 * smoothstep(0.62, 0.72, side) * (1.0 - smoothstep(0.9, 0.97, side)) * smoothstep(-0.3, 0.2, r.z);
  c *= 1.0 - 0.8 * exp(-perfumeInkBloomSq((r.x - 0.16) / 0.05)) * smoothstep(-0.2, 0.3, r.z);
  c *= 1.0 - 0.45 * exp(-perfumeInkBloomSq((r.y + 0.35) / 0.12));
  return c;
}

/* ------------------------------------------------------------------ the body: analytic ellipsoids */



// t of the nearest and farthest hits of a ray with an perfumeInkBloomEllipsoid of radii e (local), or (-1, -1)
float2 perfumeInkBloomEllipsoid(float3 ro, float3 rd, float3 e) {
  float3 o = ro / e; float3 dd = rd / e;
  float a = dot(dd, dd); float b = dot(o, dd); float c = dot(o, o) - 1.0;
  float h = b * b - a * c;
  if (h < 0.0) { return float2(-1.0); }
  float s = sqrt(h);
  return float2((-b - s) / a, (-b + s) / a);
}
float3 perfumeInkBloomEllNormal(float3 p, float3 e) { return normalize(p / (e * e)); }

/* ------------------------------------------------------------------ the atomiser: sphere-traced chrome (local) */

float perfumeInkBloomSdRoundBox(float3 p, float3 b, float r) { float3 q = abs(p) - b + r; return length(max(q, float3(0.0))) + min(max(q.x, max(q.y, q.z)), 0.0) - r; }
float perfumeInkBloomSdHex(float3 p, float r, float h) {
  // a hexagonal prism along y
  float3 k = float3(-0.8660254, 0.5, 0.57735);
  float2 q = abs(p.xz);
  q -= 2.0 * min(dot(k.xy, q), 0.0) * k.xy;
  float2 dd = float2(length(q - float2(clamp(q.x, -k.z * r, k.z * r), r)) * sign(q.y - r), abs(p.y) - h);
  return min(max(dd.x, dd.y), 0.0) + length(max(dd, float2(0.0)));
}
float perfumeInkBloomSdTorus(float3 p, float R, float r) { float2 q = float2(length(p.xz) - R, p.y); return length(q) - r; }
// the ring's centre and axis in the bottle's frame: at rest it hangs right of the cap, facing the eye


float3 perfumeInkBloomRingP(float3 p) {
  // a frame whose y is the ring's axis
  float3 n = normalize(perfumeInkBloomRING_N);
  float3 u = normalize(cross(n, float3(0.0, 1.0, 0.0)));
  float3 w = cross(u, n);
  float3 q = p - perfumeInkBloomRING_C;
  return float3(dot(q, u), dot(q, n), dot(q, w));
}
// 1 chrome collar and cap, 2 ring, 3 the blue disc on top
float2 perfumeInkBloomSdAtom(float3 p0) {
  float dip = Fliquid.y * 7.0;
  float3 p = p0;
  // the collar: a faceted crown of two hex prisms, the lower one wider
  float dd = perfumeInkBloomSdHex(p - float3(0.0, 80.0, 0.0), 50.0, 10.0) - 4.0;
  dd = min(dd, perfumeInkBloomSdHex(p - float3(0.0, 96.0, 0.0), 40.0, 8.0) - 2.5);
  // the cap, pushed down by a press
  // a square prism with softly rounded edges, turned on its axis so two faces show
  float3 cp = p - float3(0.0, 134.0 - dip, 0.0);
  float2 cxz = perfumeInkBloomRot(0.62) * cp.xz;
  cp = float3(cxz.x, cp.y, cxz.y);
  dd = min(dd, perfumeInkBloomSdRoundBox(cp, float3(35.0, 42.0, 35.0), 12.0));
  float m = 1.0;
  float3 dp = p - float3(0.0, 176.5 - dip, 0.0);
  float2 dxz = perfumeInkBloomRot(0.62) * dp.xz;
  dp = float3(dxz.x, dp.y, dxz.y);
  float disc = perfumeInkBloomSdRoundBox(dp, float3(25.0, 2.6, 25.0), 2.4);
  if (disc < dd) { dd = disc; m = 3.0; }
  float ring = perfumeInkBloomSdTorus(perfumeInkBloomRingP(p), 40.0, 2.8);
  if (ring < dd) { dd = ring; m = 2.0; }
  return float2(dd, m);
}
float3 perfumeInkBloomAtomN(float3 p) {
  float e = 0.15;
  float3 k1 = float3(1.0, -1.0, -1.0); float3 k2 = float3(-1.0, -1.0, 1.0); float3 k3 = float3(-1.0, 1.0, -1.0); float3 k4 = float3(1.0, 1.0, 1.0);
  return normalize(k1 * perfumeInkBloomSdAtom(p + k1 * e).x + k2 * perfumeInkBloomSdAtom(p + k2 * e).x + k3 * perfumeInkBloomSdAtom(p + k3 * e).x + k4 * perfumeInkBloomSdAtom(p + k4 * e).x);
}

/* ------------------------------------------------------------------ the liquid */

// the surface: a plane across the body, level with the room's gravity, rocked by the slosh
float3 perfumeInkBloomSurfaceN() {
  float3 up = perfumeInkBloomToLocal(float3(0.0, 1.0, 0.0));
  float s = Fliquid.x;
  // rock it about the axis across the screen, so it tips left and right
  float3 side = perfumeInkBloomToLocal(float3(1.0, 0.0, 0.0));
  return normalize(up * cos(s) + side * sin(s));
}

// the length of a ray's path through the liquid inside the inner wall
float perfumeInkBloomLiquidPath(float3 ro, float3 rd) {
  float2 ti = perfumeInkBloomEllipsoid(ro, rd, perfumeInkBloomBODY - float3(perfumeInkBloomWALL));
  if (ti.y <= 0.0) { return 0.0; }
  float a = max(ti.x, 0.0);
  float b = ti.y;
  float3 n = perfumeInkBloomSurfaceN();
  float dn = dot(rd, n);
  float h0 = dot(ro, n) - perfumeInkBloomLEVEL;
  if (abs(dn) < 1e-4) {
    if (h0 > 0.0) { return 0.0; }
    return b - a;
  }
  float tp = -h0 / dn;
  if (dn > 0.0) { b = min(b, tp); } else { a = max(a, tp); }
  return max(b - a, 0.0);
}



float4 perfumeInkBloomBottlePx(float2 s) {
  float3 col; float alpha;
  col = float3(0.0);
  alpha = 0.0;
  float sc = Fbottle.z;
  float3 w = float3((s.x - Fbottle.x) / sc, (Fbottle.y - s.y) / sc, 0.0);
  // the shadow on the page: soft, blue, under the body
  float2 sp = (w.xy - float2(14.0, -98.0)) / float2(118.0, 30.0);
  float sh = exp(-dot(sp, sp) * 1.6) * 0.42;
  col = perfumeInkBloomLin(float3(0.6, 0.8, 0.95)) * sh;
  alpha = sh;
  float3 ro = perfumeInkBloomToLocal(float3(w.xy, 400.0));
  float3 rd = perfumeInkBloomToLocal(float3(0.0, 0.0, -1.0));
  // the body
  float2 tb = perfumeInkBloomEllipsoid(ro, rd, perfumeInkBloomBODY);
  float tBody = 1e9;
  if (tb.x > 0.0) { tBody = tb.x; }
  // the atomiser, traced only near it
  float tA = 1e9;
  float mA = 0.0;
  if (length(w.xy - perfumeInkBloomToWorld(float3(0.0, 150.0, 0.0)).xy) < 150.0) {
    float t = 300.0;
    for (int i = 0; i < 60; i++) {
      float2 h = perfumeInkBloomSdAtom(ro + rd * t);
      if (h.x < 0.05) { tA = t; mA = h.y; break; }
      t += h.x * 0.9;
      if (t > 520.0) { break; }
    }
  }
  if (tBody > 1e8 && tA > 1e8) { return float4(col,alpha); }
  float3 V = float3(0.0, 0.0, 1.0);
  if (tA < tBody) {
    float3 p = ro + rd * tA;
    float3 nl = perfumeInkBloomAtomN(p);
    float3 n = perfumeInkBloomToWorld(nl);
    float3 r = reflect(float3(0.0, 0.0, -1.0), n);
    float3 c = float3(0.0);
    if (mA < 2.5) {
      // chrome: the room in it, a little cool
      float fr = 0.8 + 0.2 * pow(1.0 - max(n.z, 0.0), 5.0);
      c = perfumeInkBloomStudio(r) * perfumeInkBloomLin(float3(0.95, 0.96, 0.98)) * fr;
    } else {
      // the blue glass disc on the cap
      float fr = 0.04 + 0.96 * pow(1.0 - max(n.z, 0.0), 5.0);
      c = perfumeInkBloomLin(float3(0.18, 0.72, 0.95)) * (1.0 - fr) + perfumeInkBloomStudio(r) * fr;
    }
    col = c;
    alpha = 1.0;
    return float4(col,alpha);
  }
  float3 p = ro + rd * tBody;
  float3 nl = perfumeInkBloomEllNormal(p, perfumeInkBloomBODY);
  float3 n = perfumeInkBloomToWorld(nl);
  float cosv = max(n.z, 0.0);
  float fr = 0.04 + 0.96 * pow(1.0 - cosv, 5.0);
  float3 r = reflect(float3(0.0, 0.0, -1.0), n);
  // into the glass and the liquid, bent a little
  float3 rdIn = refract(rd, nl, 1.0 / 1.36);
  float L = perfumeInkBloomLiquidPath(p + rdIn * 0.5, rdIn);
  float3 sigma = float3(1.6, 0.28, 0.03) / 100.0;
  float3 back = float3(1.0) * (0.98 + 0.08 * clamp(perfumeInkBloomToWorld(rdIn).y + 0.5, 0.0, 1.0));
  float3 through = exp(-sigma * L);
  // the light leaving the back: the page, with the room's floor where the ray bends far

  // where the ray only crosses glass above the liquid, the glass greys the page faintly
  if (L <= 0.0) { through = float3(0.93, 0.95, 0.97); }
  float3 c = back * through * (1.0 - fr) + perfumeInkBloomStudio(perfumeInkBloomToWorld(r)) * fr;
  // the wall at the rim: a bright edge
  c += float3(0.85, 0.95, 1.0) * pow(1.0 - cosv, 4.0) * 0.12;
  // the surface's meniscus, a bright thin line where it meets the wall
  float3 n2 = perfumeInkBloomSurfaceN();
  float hS = dot(p, n2) - perfumeInkBloomLEVEL;
  c += float3(0.9, 0.98, 1.0) * exp(-perfumeInkBloomSq(hS / 1.6)) * 0.25 * step(0.0, 1.0 - abs(hS) / 6.0);
  col = c;
  alpha = 1.0;
  return float4(col,alpha);
}

float3 perfumeInkBloomEncode(float3 c) {
 return pow(clamp(c/(float3(1)+max(c-float3(.85),float3(0))*.6),float3(0),float3(1)),float3(1.0/2.2));
}
half4 main(float2 xy) {
 float4 px=perfumeInkBloomBottlePx(xy);
 if(px.a<=.0001)return half4(0);
 if(px.a<.999)return half4(perfumeInkBloomEncode(px.rgb/px.a)*px.a,px.a);
 return half4(perfumeInkBloomEncode(px.rgb),1);
}

"""
}
