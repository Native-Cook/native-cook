#include <metal_stdlib>
using namespace metal;
struct PerfumeInkBloomFrameU { float4 view, bottle, liquid, mist; };
struct PerfumeInkBloomDrop { float4 at, tint; };
// Same encoded sRGB output and premultiplied blending as WebGPU; no P3 conversion.
constant float perfumeInkBloomPI = 3.14159265;
constant float perfumeInkBloomTAU = 6.2831853;
constant float perfumeInkBloomPITCH = 0.28;
constant float3 perfumeInkBloomBODY = float3(100.0, 90.0, 90.0);
constant float perfumeInkBloomWALL = 5.0;
constant float3 perfumeInkBloomRING_C = float3(80.0, 118.0, 0.0);
constant float3 perfumeInkBloomRING_N = float3(0.68, 0.27, 0.68);
constant float perfumeInkBloomLEVEL = 58.0;
struct PerfumeInkBloomShader {
 PerfumeInkBloomFrameU F;


float perfumeInkBloomSq(float v) { return v * v; }
float3 perfumeInkBloomLin(float3 c) { return pow(c, float3(2.2)); }
float perfumeInkBloomHash1(float n) { return fract(sin(n * 91.3458) * 47453.5453); }
float perfumeInkBloomEase(float x) { auto c = clamp(x, 0.0, 1.0); return c * c * (3.0 - 2.0 * c); }
float2x2 perfumeInkBloomRot(float a) { auto c = cos(a); auto s = sin(a); return float2x2(c, s, -s, c); }

/* ------------------------------------------------------------------ the bottle's frame
 world: pt, origin at the body's centre, x right, y up, z towards the eye. local: the bottle's own,
 y along its axis; the bottle leans in the screen's plane and is seen a little from above. */


float perfumeInkBloomLean() { return F.liquid.z + F.bottle.w; }
float3 perfumeInkBloomToLocal(float3 v) {
  auto xy = perfumeInkBloomRot(perfumeInkBloomLean()) * v.xy;
  auto q = float3(xy, v.z);
  auto yz = perfumeInkBloomRot(-perfumeInkBloomPITCH) * q.yz;
  return float3(q.x, yz.x, yz.y);
}
float3 perfumeInkBloomToWorld(float3 v) {
  auto yz = perfumeInkBloomRot(perfumeInkBloomPITCH) * v.yz;
  auto q = float3(v.x, yz.x, yz.y);
  auto xy = perfumeInkBloomRot(-perfumeInkBloomLean()) * q.xy;
  return float3(xy, q.z);
}

/* ------------------------------------------------------------------ the room (world directions) */

float3 perfumeInkBloomStudio(float3 r) {
  // a white perfumeInkBloomStudio: a big soft box overhead, a tall strip to each side with a dark gap, a grey floor
  auto c = mix(perfumeInkBloomLin(float3(0.78, 0.8, 0.86)), perfumeInkBloomLin(float3(0.98, 0.99, 1.0)), smoothstep(-0.6, 0.5, r.y));
  c += float3(1.0) * 2.4 * smoothstep(0.55, 0.85, r.y) * (1.0 - smoothstep(0.4, 0.8, abs(r.x)));
  auto side = abs(r.x);
  c += float3(1.0) * 1.8 * smoothstep(0.62, 0.72, side) * (1.0 - smoothstep(0.9, 0.97, side)) * smoothstep(-0.3, 0.2, r.z);
  c *= 1.0 - 0.8 * exp(-perfumeInkBloomSq((r.x - 0.16) / 0.05)) * smoothstep(-0.2, 0.3, r.z);
  c *= 1.0 - 0.45 * exp(-perfumeInkBloomSq((r.y + 0.35) / 0.12));
  return c;
}

/* ------------------------------------------------------------------ the body: analytic ellipsoids */



// t of the nearest and farthest hits of a ray with an perfumeInkBloomEllipsoid of radii e (local), or (-1, -1)
float2 perfumeInkBloomEllipsoid(float3 ro, float3 rd, float3 e) {
  auto o = ro / e; auto dd = rd / e;
  auto a = dot(dd, dd); auto b = dot(o, dd); auto c = dot(o, o) - 1.0;
  auto h = b * b - a * c;
  if (h < 0.0) { return float2(-1.0); }
  auto s = sqrt(h);
  return float2((-b - s) / a, (-b + s) / a);
}
float3 perfumeInkBloomEllNormal(float3 p, float3 e) { return normalize(p / (e * e)); }

/* ------------------------------------------------------------------ the atomiser: sphere-traced chrome (local) */

float perfumeInkBloomSdRoundBox(float3 p, float3 b, float r) { auto q = abs(p) - b + r; return length(max(q, float3(0.0))) + min(max(q.x, max(q.y, q.z)), 0.0) - r; }
float perfumeInkBloomSdHex(float3 p, float r, float h) {
  // a hexagonal prism along y
  auto k = float3(-0.8660254, 0.5, 0.57735);
  float2 q = abs(p.xz);
  q -= 2.0 * min(dot(k.xy, q), 0.0) * k.xy;
  auto dd = float2(length(q - float2(clamp(q.x, -k.z * r, k.z * r), r)) * sign(q.y - r), abs(p.y) - h);
  return min(max(dd.x, dd.y), 0.0) + length(max(dd, float2(0.0)));
}
float perfumeInkBloomSdTorus(float3 p, float R, float r) { auto q = float2(length(p.xz) - R, p.y); return length(q) - r; }
// the ring's centre and axis in the bottle's frame: at rest it hangs right of the cap, facing the eye


float3 perfumeInkBloomRingP(float3 p) {
  // a frame whose y is the ring's axis
  auto n = normalize(perfumeInkBloomRING_N);
  auto u = normalize(cross(n, float3(0.0, 1.0, 0.0)));
  auto w = cross(u, n);
  auto q = p - perfumeInkBloomRING_C;
  return float3(dot(q, u), dot(q, n), dot(q, w));
}
// 1 chrome collar and cap, 2 ring, 3 the blue disc on top
float2 perfumeInkBloomSdAtom(float3 p0) {
  auto dip = F.liquid.y * 7.0;
  auto p = p0;
  // the collar: a faceted crown of two hex prisms, the lower one wider
  auto dd = perfumeInkBloomSdHex(p - float3(0.0, 80.0, 0.0), 50.0, 10.0) - 4.0;
  dd = min(dd, perfumeInkBloomSdHex(p - float3(0.0, 96.0, 0.0), 40.0, 8.0) - 2.5);
  // the cap, pushed down by a press
  // a square prism with softly rounded edges, turned on its axis so two faces show
  auto cp = p - float3(0.0, 134.0 - dip, 0.0);
  auto cxz = perfumeInkBloomRot(0.62) * cp.xz;
  cp = float3(cxz.x, cp.y, cxz.y);
  dd = min(dd, perfumeInkBloomSdRoundBox(cp, float3(35.0, 42.0, 35.0), 12.0));
  auto m = 1.0;
  auto dp = p - float3(0.0, 176.5 - dip, 0.0);
  auto dxz = perfumeInkBloomRot(0.62) * dp.xz;
  dp = float3(dxz.x, dp.y, dxz.y);
  auto disc = perfumeInkBloomSdRoundBox(dp, float3(25.0, 2.6, 25.0), 2.4);
  if (disc < dd) { dd = disc; m = 3.0; }
  auto ring = perfumeInkBloomSdTorus(perfumeInkBloomRingP(p), 40.0, 2.8);
  if (ring < dd) { dd = ring; m = 2.0; }
  return float2(dd, m);
}
float3 perfumeInkBloomAtomN(float3 p) {
  auto e = 0.15;
  auto k1 = float3(1.0, -1.0, -1.0); auto k2 = float3(-1.0, -1.0, 1.0); auto k3 = float3(-1.0, 1.0, -1.0); auto k4 = float3(1.0, 1.0, 1.0);
  return normalize(k1 * perfumeInkBloomSdAtom(p + k1 * e).x + k2 * perfumeInkBloomSdAtom(p + k2 * e).x + k3 * perfumeInkBloomSdAtom(p + k3 * e).x + k4 * perfumeInkBloomSdAtom(p + k4 * e).x);
}

/* ------------------------------------------------------------------ the liquid */

// the surface: a plane across the body, level with the room's gravity, rocked by the slosh
float3 perfumeInkBloomSurfaceN() {
  auto up = perfumeInkBloomToLocal(float3(0.0, 1.0, 0.0));
  auto s = F.liquid.x;
  // rock it about the axis across the screen, so it tips left and right
  auto side = perfumeInkBloomToLocal(float3(1.0, 0.0, 0.0));
  return normalize(up * cos(s) + side * sin(s));
}

// the length of a ray's path through the liquid inside the inner wall
float perfumeInkBloomLiquidPath(float3 ro, float3 rd) {
  auto ti = perfumeInkBloomEllipsoid(ro, rd, perfumeInkBloomBODY - float3(perfumeInkBloomWALL));
  if (ti.y <= 0.0) { return 0.0; }
  auto a = max(ti.x, 0.0);
  auto b = ti.y;
  auto n = perfumeInkBloomSurfaceN();
  auto dn = dot(rd, n);
  auto h0 = dot(ro, n) - perfumeInkBloomLEVEL;
  if (abs(dn) < 1e-4) {
    if (h0 > 0.0) { return 0.0; }
    return b - a;
  }
  auto tp = -h0 / dn;
  if (dn > 0.0) { b = min(b, tp); } else { a = max(a, tp); }
  return max(b - a, 0.0);
}

struct PerfumeInkBloomPx { float3 col; float a; };

PerfumeInkBloomPx perfumeInkBloomBottlePx(float2 s) {
  PerfumeInkBloomPx o;
  o.col = float3(0.0);
  o.a = 0.0;
  auto sc = F.bottle.z;
  auto w = float3((s.x - F.bottle.x) / sc, (F.bottle.y - s.y) / sc, 0.0);
  // the shadow on the page: soft, blue, under the body
  auto sp = (w.xy - float2(14.0, -98.0)) / float2(118.0, 30.0);
  auto sh = exp(-dot(sp, sp) * 1.6) * 0.42;
  o.col = perfumeInkBloomLin(float3(0.6, 0.8, 0.95)) * sh;
  o.a = sh;
  auto ro = perfumeInkBloomToLocal(float3(w.xy, 400.0));
  auto rd = perfumeInkBloomToLocal(float3(0.0, 0.0, -1.0));
  // the body
  auto tb = perfumeInkBloomEllipsoid(ro, rd, perfumeInkBloomBODY);
  auto tBody = 1e9;
  if (tb.x > 0.0) { tBody = tb.x; }
  // the atomiser, traced only near it
  auto tA = 1e9;
  auto mA = 0.0;
  if (length(w.xy - perfumeInkBloomToWorld(float3(0.0, 150.0, 0.0)).xy) < 150.0) {
    auto t = 300.0;
    for (int i = 0; i < 80; i++) {
      auto h = perfumeInkBloomSdAtom(ro + rd * t);
      if (h.x < 0.05) { tA = t; mA = h.y; break; }
      t += h.x * 0.9;
      if (t > 520.0) { break; }
    }
  }
  if (tBody > 1e8 && tA > 1e8) { return o; }
  auto V = float3(0.0, 0.0, 1.0);
  if (tA < tBody) {
    auto p = ro + rd * tA;
    auto nl = perfumeInkBloomAtomN(p);
    auto n = perfumeInkBloomToWorld(nl);
    auto r = reflect(float3(0.0, 0.0, -1.0), n);
    auto c = float3(0.0);
    if (mA < 2.5) {
      // chrome: the room in it, a little cool
      auto fr = 0.8 + 0.2 * pow(1.0 - max(n.z, 0.0), 5.0);
      c = perfumeInkBloomStudio(r) * perfumeInkBloomLin(float3(0.95, 0.96, 0.98)) * fr;
    } else {
      // the blue glass disc on the cap
      auto fr = 0.04 + 0.96 * pow(1.0 - max(n.z, 0.0), 5.0);
      c = perfumeInkBloomLin(float3(0.18, 0.72, 0.95)) * (1.0 - fr) + perfumeInkBloomStudio(r) * fr;
    }
    o.col = c;
    o.a = 1.0;
    return o;
  }
  auto p = ro + rd * tBody;
  auto nl = perfumeInkBloomEllNormal(p, perfumeInkBloomBODY);
  auto n = perfumeInkBloomToWorld(nl);
  auto cosv = max(n.z, 0.0);
  auto fr = 0.04 + 0.96 * pow(1.0 - cosv, 5.0);
  auto r = reflect(float3(0.0, 0.0, -1.0), n);
  // into the glass and the liquid, bent a little
  auto rdIn = refract(rd, nl, 1.0 / 1.36);
  auto L = perfumeInkBloomLiquidPath(p + rdIn * 0.5, rdIn);
  auto sigma = float3(1.6, 0.28, 0.03) / 100.0;
  auto back = float3(1.0) * (0.98 + 0.08 * clamp(perfumeInkBloomToWorld(rdIn).y + 0.5, 0.0, 1.0));
  auto through = exp(-sigma * L);
  // the light leaving the back: the page, with the room's floor where the ray bends far

  // where the ray only crosses glass above the liquid, the glass greys the page faintly
  if (L <= 0.0) { through = float3(0.93, 0.95, 0.97); }
  auto c = back * through * (1.0 - fr) + perfumeInkBloomStudio(perfumeInkBloomToWorld(r)) * fr;
  // the wall at the rim: a bright edge
  c += float3(0.85, 0.95, 1.0) * pow(1.0 - cosv, 4.0) * 0.12;
  // the surface's meniscus, a bright thin line where it meets the wall
  auto n2 = perfumeInkBloomSurfaceN();
  auto hS = dot(p, n2) - perfumeInkBloomLEVEL;
  c += float3(0.9, 0.98, 1.0) * exp(-perfumeInkBloomSq(hS / 1.6)) * 0.25 * step(0.0, 1.0 - abs(hS) / 6.0);
  o.col = c;
  o.a = 1.0;
  return o;
}

float4 perfumeInkBloomNozzle() {
  auto dip = F.liquid.y * 7.0;
  auto local = float3(30.0, 164.0 - dip, 20.0);
  auto wq = perfumeInkBloomToWorld(local);
  auto at = float2(F.bottle.x + wq.x * F.bottle.z, F.bottle.y - wq.y * F.bottle.z);
  auto dir = perfumeInkBloomToWorld(float3(0.55, 0.84, 0.0));
  return float4(at, normalize(float2(dir.x, -dir.y)));
}

PerfumeInkBloomDrop perfumeInkBloomMist(uint i, device const float2 *notes) {


  auto fi = float(i);
  PerfumeInkBloomDrop o;
  auto age = F.mist.x;
  auto h1 = perfumeInkBloomHash1(fi + 1.0); auto h2 = perfumeInkBloomHash1(fi + 2.0); auto h3 = perfumeInkBloomHash1(fi + 3.0); auto h4 = perfumeInkBloomHash1(fi + 4.0); auto h5 = perfumeInkBloomHash1(fi + 5.0);
  auto tau = age - h1 * 0.42;
  if (age < 0.0 || tau < 0.0) {
    o.at = float4(0.0);
    o.tint = float4(0.0);
    return o;
  }
  auto nz = perfumeInkBloomNozzle();
  auto sc = F.bottle.z;
  // a cone out of the perfumeInkBloomNozzle, slowed by the air
  auto a = (h2 - 0.5) * 0.7 + (h3 - 0.5) * 0.3;
  auto dir = perfumeInkBloomRot(a) * nz.zw;
  auto speed = (130.0 + 150.0 * h3) * sc;
  auto k = 2.6;
  auto p = nz.xy + dir * speed * (1.0 - exp(-k * tau)) / k;
  // carried by a slow swirl and rising
  auto sw = min(tau, 2.0);
  p += float2(sin(tau * (1.3 + h4) + h5 * perfumeInkBloomTAU), cos(tau * (1.1 + h5) + h4 * perfumeInkBloomTAU)) * 16.0 * sw * sc;
  p += float2(0.0, -10.0 * tau) * sc;
  auto r = (1.2 + 3.2 * min(tau / 1.2, 1.0) * (0.5 + h4)) * sc;
  auto alpha = smoothstep(0.0, 0.05, tau) * (1.0 - 0.55 * smoothstep(0.4, 2.0, tau));
  // drawn onto the notes, one droplet to a point, staggered
  auto c = perfumeInkBloomEase((F.mist.y - h1 * 0.35) / 0.65);
  auto goal = notes[i];
  p = mix(p, goal, c);
  r = mix(r, 0.95 * sc, c);
  alpha = mix(alpha, 1.0, c);
  // and lifted away
  auto e = perfumeInkBloomEase((F.mist.z - h2 * 0.4) / 0.6);
  p += (float2(sin(h4 * perfumeInkBloomTAU) * 50.0, -90.0 * (0.6 + h3)) * e + float2(sin(e * 3.0 + h5 * perfumeInkBloomTAU), 0.0) * 12.0 * e) * sc;
  r += 1.6 * e * sc;
  alpha *= (1.0 - e) * (1.0 - e);
  o.at = float4(p, r, alpha);
  o.tint = float4(c * (1.0 - e), 0.0, 0.0, 0.0);
  return o;

}
};

struct PerfumeInkBloomVOut { float4 pos [[position]]; float2 uv; };
vertex PerfumeInkBloomVOut perfumeInkBloomFullVs(uint vi [[vertex_id]]) {
 float2 uv=float2(float((vi<<1u)&2u),float(vi&2u));
 return {float4(uv*float2(2,-2)+float2(-1,1),0,1),uv};
}
float3 perfumeInkBloomEncode(float3 c) {
 return pow(clamp(c/(float3(1)+max(c-float3(.85),float3(0))*.6),float3(0),float3(1)),float3(1.0/2.2));
}
fragment float4 perfumeInkBloomBottleFs(PerfumeInkBloomVOut v [[stage_in]], constant PerfumeInkBloomFrameU &f [[buffer(0)]]) {
 PerfumeInkBloomShader s; s.F=f;
 auto px=s.perfumeInkBloomBottlePx(v.pos.xy/f.view.z);
 if(px.a<=.0001)return float4(0);
 if(px.a<.999)return float4(perfumeInkBloomEncode(px.col/px.a)*px.a,px.a);
 return float4(perfumeInkBloomEncode(px.col),1);
}
kernel void perfumeInkBloomMistCs(uint i [[thread_position_in_grid]], constant PerfumeInkBloomFrameU &f [[buffer(0)]], device PerfumeInkBloomDrop *drops [[buffer(1)]], device const float2 *notes [[buffer(2)]]) {
 if(i>=6144u)return;
 PerfumeInkBloomShader s; s.F=f; drops[i]=s.perfumeInkBloomMist(i,notes);
}
struct PerfumeInkBloomDOut { float4 pos [[position]]; float2 uv; float a, tint; };
vertex PerfumeInkBloomDOut perfumeInkBloomDropVs(uint vi [[vertex_id]], uint ii [[instance_id]], constant PerfumeInkBloomFrameU &f [[buffer(0)]], device const PerfumeInkBloomDrop *drops [[buffer(1)]]) {
 auto p=drops[ii]; float2 corner=float2(-1,-1);
 if(vi==1u||vi==4u)corner=float2(1,-1);
 if(vi==2u||vi==3u)corner=float2(-1,1);
 if(vi==5u)corner=float2(1,1);
 float r=p.at.z+.6; if(p.at.w<=.002)r=0;
 float2 q=p.at.xy+corner*r;
 return {float4(q.x/f.view.x*2-1,1-q.y/f.view.y*2,0,1),corner*r/max(p.at.z,.1f),p.at.w,p.tint.x};
}
fragment float4 perfumeInkBloomDropFs(PerfumeInkBloomDOut v [[stage_in]]) {
 float r=length(v.uv);
 float soft=exp(-r*r*2.2)*(1-smoothstep(.9f,1.4f,r));
 float crisp=1-smoothstep(.7f,1.15f,r);
 float k=mix(soft*.16,crisp*.85,v.tint)*v.a;
 float3 c=mix(float3(.55,.76,.98),float3(.11,.39,.95),v.tint);
 return float4(c*k,k);
}
