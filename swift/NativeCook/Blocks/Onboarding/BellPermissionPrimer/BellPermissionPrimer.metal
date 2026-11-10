#include <metal_stdlib>
using namespace metal;

// Value-for-value translation of the current TypeGPU-resolved WGSL. Numeric output is sRGB.
struct BellPermissionPrimerUniforms { float4 view, hero, pose, lift, island, slab, rings, look; };
constant float BellPermissionPrimerTAU=6.2831853;
constant float4 BellPermissionPrimerDeco[15]={float4(34, 91, 14, 1),float4(367, 88, 12, 0),float4(218, 148, 12, 0),float4(122, 110, 6, 2),float4(327, 150, 7, 2),float4(25, 170, 5, 2),float4(110, 232, 10, 1),float4(330, 258, 13, 1),float4(32, 293, 7, 2),float4(346, 344, 6, 2),float4(301, 511, 13, 1),float4(370, 583, 9, 1),float4(323, 724, 11, 1),float4(180, 635, 6, 2),float4(70, 740, 5, 2)};
struct BellPermissionPrimerScene {
    BellPermissionPrimerUniforms F;
    float bellPermissionPrimerSmoothstep(float a,float b,float x) { float v=clamp((x-a)/(b-a),0.0f,1.0f); return v*v*(3.0f-2.0f*v); }
float bellPermissionPrimerSq(float v) { return v * v; }
float3 bellPermissionPrimerLin(float3 c) { return pow(c, float3(2.2)); }
float bellPermissionPrimerHash1(float n) { return fract(sin(n * 91.3458) * 47453.5453); }
float bellPermissionPrimerSmin(float a, float b, float k) { auto h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0); return mix(b, a, h) - k * h * (1.0 - h); }
float bellPermissionPrimerSmax2(float a, float b, float k) { auto h = clamp(0.5 - 0.5 * (b - a) / k, 0.0, 1.0); return mix(b, a, h) + k * h * (1.0 - h); }
float bellPermissionPrimerSdTorusXY(float3 p, float R, float r) { auto q = float2(length(p.xy) - R, p.z); return length(q) - r; }
float bellPermissionPrimerSdTorusXZ(float3 p, float R, float r) { auto q = float2(length(p.xz) - R, p.y); return length(q) - r; }
float bellPermissionPrimerSdRoundCone(float3 p, float3 a, float3 b, float r1, float r2) {
  auto ba = b - a; auto l2 = dot(ba, ba); auto rr = r1 - r2; auto a2 = l2 - rr * rr; auto il2 = 1.0 / l2;
  auto pa = p - a; auto y = dot(pa, ba); auto z = y - l2;
  auto xv = pa * l2 - ba * y; auto x2 = dot(xv, xv);
  auto y2 = y * y * l2; auto z2 = z * z * l2;
  auto k = sign(rr) * rr * rr * x2;
  if (sign(z) * a2 * z2 > k) { return sqrt(x2 + z2) * il2 - r2; }
  if (sign(y) * a2 * y2 < k) { return sqrt(x2 + y2) * il2 - r1; }
  return (sqrt(x2 * a2 * il2) + y * rr) * il2 - r1;
}
float bellPermissionPrimerSeg3(float3 p, float3 a, float3 b) { auto pa = p - a; auto ba = b - a; return length(pa - ba * clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0)); }

/* ------------------------------------------------------------------ the bell, in its own frame (units, y up, pivot at 0) */

// x distance, y material: 0 porcelain, 1 gold, 2 the clapper, 3 the gold inside
float2 bellPermissionPrimerSdBell(float3 p) {
  auto outer = bellPermissionPrimerSmin(length(p - float3(0.0, -0.55, 0.0)) - 0.34, bellPermissionPrimerSdRoundCone(p, float3(0.0, -0.58, 0.0), float3(0.0, -1.2, 0.0), 0.33, 0.6), 0.16);
  auto inner = bellPermissionPrimerSdRoundCone(p, float3(0.0, -0.66, 0.0), float3(0.0, -1.34, 0.0), 0.27, 0.56);
  auto body = bellPermissionPrimerSmax2(outer, -inner, 0.02);
  body = max(body, -(p.y + 1.27));
  auto d = float2(body, 0.0);
  if (abs(inner) < abs(outer) && p.y < -0.6) { d.y = 3.0; }
  auto lip = bellPermissionPrimerSdTorusXZ(p - float3(0.0, -1.26, 0.0), 0.585, 0.045);
  if (lip < d.x) { d = float2(lip, 1.0); }
  auto crown = bellPermissionPrimerSdTorusXY(p - float3(0.0, -0.11, 0.0), 0.1, 0.035);
  if (crown < d.x) { d = float2(crown, 1.0); }
  // the clapper swings on its own angle under the shoulder
  auto rel = F.pose.y - F.pose.x;
  auto cs = cos(rel); auto sn = sin(rel);
  auto tip = float3(0.0, -0.62, 0.0) + float3(sn * 0.7, -cs * 0.7, 0.0);
  auto rod = bellPermissionPrimerSeg3(p, float3(0.0, -0.62, 0.0), tip) - 0.018;
  auto ball = length(p - tip) - 0.11;
  auto cl = bellPermissionPrimerSmin(rod, ball, 0.03);
  if (cl < d.x) { d = float2(cl, 2.0); }
  return d;
}

// the island (or the notification it opens into): black glass, a rounded slab in world units
float4 bellPermissionPrimerIslandRect() {
  auto e = F.pose.w;
  return mix(F.island, F.slab, e);
}
float bellPermissionPrimerSdIsland(float2 pw, float z) {
  auto U = F.hero.z;
  auto r = bellPermissionPrimerIslandRect();
  auto c = (r.xy + r.zw * 0.5) / U;
  auto halfSize = r.zw * 0.5 / U;
  auto rad = min(halfSize.y, mix(halfSize.y, 34.0 / U, F.pose.w));
  auto q2 = abs(pw - c) - halfSize + float2(rad);
  auto d2 = length(max(q2, float2(0.0))) + min(max(q2.x, q2.y), 0.0) - rad;
  auto w = float2(d2, abs(z) - 0.05);
  return min(max(w.x, w.y), 0.0) + length(max(w, float2(0.0)));
}

// where the bell's middle is on the screen (pt): under its pivot, drawn up by the finger, then pulled
// into the island
float2 bellPermissionPrimerBellCentre() {
  auto U = F.hero.z;
  auto rest = F.hero.xy + float2(0.0, 0.85 * U + F.hero.w) + F.lift.xy;
  auto isl = F.island.xy + F.island.zw * 0.5;
  return mix(rest, isl, F.pose.z);
}
float bellPermissionPrimerBellScale() { return mix(1.0, 0.03, F.pose.z); }

struct BellPermissionPrimerObj {  float d; float m; float isl ; };

// the bell and the island as one field, in world units (y down, x right: the screen's axes / U)
BellPermissionPrimerObj bellPermissionPrimerSdObj(float3 pw) {
  auto U = F.hero.z;
  auto bc = bellPermissionPrimerBellCentre() / U;
  auto sc = bellPermissionPrimerBellScale();
  // the bell's own frame: y up from its middle, the swing about the pivot 0.85 above it
  auto q = float3(pw.x - bc.x, -(pw.y - bc.y), pw.z) / sc;
  // seen a little from the side and above (squarely once swallowed)
  auto ya = F.look.x * (1.0 - F.pose.z); auto pa = F.look.y * (1.0 - F.pose.z);
  auto cy = cos(ya); auto sy = sin(ya); auto cp = cos(pa); auto sp = sin(pa);
  q = transpose(float3x3(float3(cy, sp * sy, -cp * sy), float3(0.0, cp, sp), float3(sy, -sp * cy, cp * cy))) * q;
  q += float3(0.0, -0.85, 0.0);
  auto sw = F.pose.x * (1.0 - F.pose.z);
  auto cs = cos(sw); auto sn = sin(sw);
  q = float3(cs * q.x + sn * q.y, -sn * q.x + cs * q.y, q.z);
  auto b = bellPermissionPrimerSdBell(q);
  auto db = b.x * sc;
  auto di = bellPermissionPrimerSdIsland(pw.xy, pw.z);
  // the nearer they come, the further the goo reaches between them
  // (how far the bell's crown is from the island's underside, in units)
  auto crown = (bellPermissionPrimerBellCentre().y - 0.95 * U * sc) / U;
  auto gap = max(crown - (F.island.y + F.island.w) / U, 0.0);
  auto k = (0.55 * bellPermissionPrimerSmoothstep(1.6, 0.15, gap) + 0.4 * F.pose.z) * (1.0 - F.pose.w);
  BellPermissionPrimerObj o;
  if (k < 0.002) {
    o.d = min(db, di);
    o.isl = (di < db ? 1.0 : 0.0);
  } else {
    auto h = clamp(0.5 + 0.5 * (di - db) / k, 0.0, 1.0);
    o.d = mix(di, db, h) - k * h * (1.0 - h);
    o.isl = 1.0 - h;
  }
  o.m = b.y;
  return o;
}

float3 bellPermissionPrimerNormalAt(float3 p) {
  auto e = 0.0015;
  auto k1 = float3(1.0, -1.0, -1.0); auto k2 = float3(-1.0, -1.0, 1.0); auto k3 = float3(-1.0, 1.0, -1.0); auto k4 = float3(1.0, 1.0, 1.0);
  return normalize(k1 * bellPermissionPrimerSdObj(p + k1 * e).d + k2 * bellPermissionPrimerSdObj(p + k2 * e).d + k3 * bellPermissionPrimerSdObj(p + k3 * e).d + k4 * bellPermissionPrimerSdObj(p + k4 * e).d);
}

/* ------------------------------------------------------------------ the world */

float3 bellPermissionPrimerStudio(float3 r) {
  auto c = mix(bellPermissionPrimerLin(float3(0.42, 0.62, 0.98)), bellPermissionPrimerLin(float3(0.97, 0.98, 1.0)), bellPermissionPrimerSmoothstep(-0.5, 0.7, r.y));
  auto k = normalize(float3(-0.5, 0.78, 0.4));
  c += float3(1.0, 0.99, 0.97) * 4.0 * bellPermissionPrimerSmoothstep(0.88, 0.97, dot(r, k));
  auto f = normalize(float3(0.85, 0.2, 0.5));
  c += float3(0.9, 0.95, 1.0) * 1.0 * bellPermissionPrimerSmoothstep(0.86, 0.98, dot(r, f));
  // the blue disc below
  c = mix(c, bellPermissionPrimerLin(float3(0.25, 0.42, 0.98)), bellPermissionPrimerSmoothstep(0.0, -0.7, r.y) * 0.7);
  return c;
}

struct BellPermissionPrimerPx {  float3 col; float a ; };

BellPermissionPrimerPx bellPermissionPrimerOver(BellPermissionPrimerPx base, float3 c, float a) {
  BellPermissionPrimerPx o;
  o.col = c * a + base.col * (1.0 - a);
  o.a = a + base.a * (1.0 - a);
  return o;
}

// a little bell glyph for the faint shapes
float bellPermissionPrimerSdBellGlyph(float2 p) {
  auto body = max(length(p * float2(1.0, 0.85) - float2(0.0, -0.05)) - 0.55, p.y - 0.42);
  auto lip = length(max(abs(p - float2(0.0, 0.45)) - float2(0.62, 0.06), float2(0.0))) - 0.04;
  auto knob = length(p - float2(0.0, -0.66)) - 0.1;
  return min(min(body, lip), knob);
}

BellPermissionPrimerPx bellPermissionPrimerShade(float2 s) {
  BellPermissionPrimerPx o;
  o.col = float3(0.0);
  o.a = 0.0;
  auto t = F.view.w;
  auto lipW = F.hero.xy + float2(0.0, 1.27 * F.hero.z);
  // the faint shapes, floating, pushed out by each ring as it passes
  for (int i = 0; i < 15; i++) {
    auto dd = BellPermissionPrimerDeco[i];
    auto fi = float(i);
    auto c = dd.xy + float2(sin(BellPermissionPrimerTAU * (t / 10.0 + bellPermissionPrimerHash1(fi))) * 3.0, sin(BellPermissionPrimerTAU * (2.0 * t / 10.0 + bellPermissionPrimerHash1(fi + 3.0))) * 4.0);
    for (int k = 0; k < 3; k++) {
      auto rp = F.rings[k];
      if (rp > 0.0) {
        auto dv = c - lipW;
        auto dist = length(dv);
        auto front = rp * 420.0;
        c += normalize(dv + float2(0.001)) * 9.0 * exp(-bellPermissionPrimerSq((dist - front) / 26.0)) * (1.0 - rp);
      }
    }
    auto q = (s - c) / dd.z;
    auto g = 1e5;
    if (dd.w < 0.5) { g = bellPermissionPrimerSdBellGlyph(q); }
    else if (dd.w < 1.5) { g = length(q * float2(1.0, 1.15)) - 0.62 + 0.08 * sin(atan2(q.y, q.x) * 3.0 + fi); }
    else { g = length(q) - 0.5; }
    auto a = (1.0 - bellPermissionPrimerSmoothstep(-0.04, 0.04, g)) * 0.55;
    if (a > 0.0) { o = bellPermissionPrimerOver(o, bellPermissionPrimerLin(float3(0.75, 0.86, 1.0)), a); }
  }
  // the rings: a soft bright line running out from the lip
  for (int k = 0; k < 3; k++) {
    auto rp = F.rings[k];
    if (rp > 0.0) {
      auto front = rp * 420.0;
      auto dist = length(s - lipW);
      auto a = exp(-bellPermissionPrimerSq((dist - front) / (3.0 + 10.0 * rp))) * (1.0 - rp) * 0.55;
      o = bellPermissionPrimerOver(o, bellPermissionPrimerLin(float3(0.55, 0.75, 1.0)), a);
    }
  }
  // the bell and the island: a box round both, then a near-orthographic camera, the bell seen a
  // little from the side and above (the island squarely)
  auto U = F.hero.z;
  auto bc = bellPermissionPrimerBellCentre();
  auto r = bellPermissionPrimerIslandRect();
  auto lo = min(bc - float2(1.0, 1.25) * U * bellPermissionPrimerBellScale(), r.xy) - float2(U * 0.6);
  auto hi = max(bc + float2(1.0, 0.75) * U * bellPermissionPrimerBellScale(), r.xy + r.zw) + float2(U * 0.6);
  if (s.x < lo.x || s.y < lo.y || s.x > hi.x || s.y > hi.y) { return o; }
  // an orthographic camera in front of the screen (world: the screen's axes / U, z towards the eye)
  auto ro = float3(s / U, 12.0);
  auto rd = float3(0.0, 0.0, -1.0);
  auto tt = 10.0;
  auto hit = false;
  for (int i = 0; i < 96; i++) {
    auto dd = bellPermissionPrimerSdObj(ro + rd * tt).d;
    if (dd < 0.0008) { hit = true; break; }
    tt += dd * 0.85;
    if (tt > 14.5) { break; }
  }
  // the bell's soft shadow on the page
  auto shp = float3(s.x / U - 0.02, s.y / U - 0.12, 0.0);
  auto shd = bellPermissionPrimerSdObj(shp);
  auto sh = 0.18 * (1.0 - bellPermissionPrimerSmoothstep(-0.05, 0.4, shd.d)) * (1.0 - shd.isl) * (1.0 - F.pose.z);
  if (!hit) {
    if (sh > 0.002) { o = bellPermissionPrimerOver(o, bellPermissionPrimerLin(float3(0.36, 0.45, 0.62)), sh); }
    return o;
  }
  auto p = ro + rd * tt;
  auto ob = bellPermissionPrimerSdObj(p);
  auto nw = bellPermissionPrimerNormalAt(p);
  // into the view's frame for lighting (y up, z towards the eye)
  auto nv = float3(nw.x, -nw.y, nw.z);
  auto rdv = float3(0.0, 0.0, -1.0);
  auto vv = float3(0.0, 0.0, 1.0);
  auto L = normalize(float3(-0.5, 0.75, 0.55));
  auto cosv = max(dot(nv, vv), 0.0);
  auto refl = bellPermissionPrimerStudio(reflect(rdv, nv));
  auto spec = pow(max(dot(reflect(-L, nv), vv), 0.0), 60.0);
  auto wrap = clamp((dot(nv, L) + 0.4) / 1.4, 0.0, 1.0);
  auto col = float3(0.0);
  // the goo between them is porcelain turning black, never the gold of the crown
  auto mm = ob.m;
  if (ob.isl > 0.04 && mm > 0.5) { mm = 0.0; }
  if (mm < 0.5) {
    col = bellPermissionPrimerLin(float3(0.97, 0.975, 0.99)) * (0.3 + 0.72 * wrap);
    col = mix(col, col * bellPermissionPrimerLin(float3(0.62, 0.74, 1.0)), bellPermissionPrimerSmoothstep(0.2, -0.7, dot(nv, L)) * 0.85);
    col += refl * 0.08 + float3(1.0) * spec * 0.6;
  } else if (mm < 1.5 || mm > 2.5) {
    auto gold = bellPermissionPrimerLin(float3(0.96, 0.76, 0.26));
    auto inside = (mm > 2.5 ? 0.55 : 1.0);
    col = gold * (0.25 + 0.45 * wrap) * inside + refl * gold * 0.85 + float3(1.0, 0.95, 0.8) * spec * 0.8;
  } else {
    col = bellPermissionPrimerLin(float3(0.95, 0.48, 0.63)) * (0.4 + 0.65 * wrap) + refl * 0.06 + float3(1.0) * spec * 0.7;
  }
  // the island is black glass, as the real one; only the goo between them catches the light
  auto sheen = F.pose.z * (1.0 - F.pose.w) * (1.0 - F.pose.w);
  auto black = (refl * 0.05 + float3(1.0) * spec * 0.25) * sheen;
  col = mix(col, black, clamp(ob.isl + F.pose.z * 0.6, 0.0, 1.0));
  o = bellPermissionPrimerOver(o, col, 1.0);
  return o;
}


float3 bellPermissionPrimerEncode(float3 c) { return pow(clamp(c/(float3(1.0)+max(c-float3(0.85),float3(0.0))*0.6),float3(0.0),float3(1.0)),float3(1.0/2.2)); }
};
struct BellPermissionPrimerVertexOut { float4 position [[position]]; };
vertex BellPermissionPrimerVertexOut bellPermissionPrimerFullVs(uint vi [[vertex_id]]) {
    float2 uv=float2(float((vi<<1u)&2u),float(vi&2u));
    return {float4(uv*float2(2,-2)+float2(-1,1),0,1)};
}
fragment float4 bellPermissionPrimerHeroFs(BellPermissionPrimerVertexOut v [[stage_in]],constant BellPermissionPrimerUniforms &frame [[buffer(0)]]) {
    BellPermissionPrimerScene scene; scene.F=frame;
    auto px=scene.bellPermissionPrimerShade(v.position.xy/frame.view.z);
    if(px.a<=0) return float4(0);
    return float4(scene.bellPermissionPrimerEncode(px.col/px.a)*px.a,px.a);
}
kernel void bellPermissionPrimerSparksCs(constant BellPermissionPrimerUniforms &F [[buffer(0)]],device float4 *sparks [[buffer(1)]],uint i [[thread_position_in_grid]]) {
    if(i>=260u) return;
    BellPermissionPrimerScene scene; scene.F=F;
    float fi=float(i);
    float m=F.pose.z*(1.0-F.pose.w)+F.pose.w*step(F.pose.w,0.999)*1.0;
    float age=clamp((F.pose.z-0.55-0.3*scene.bellPermissionPrimerHash1(fi+9.0))/0.45,0.0,1.0)*0.5+F.pose.w*0.5;
    if(age<=0.0 || age>=1.0) { sparks[i]=float4(0); return; }
    float4 r=F.island; float2 c=r.xy+r.zw*0.5;
    float ang=scene.bellPermissionPrimerHash1(fi+2.0)*BellPermissionPrimerTAU;
    float2 dir=float2(cos(ang),sin(ang)*0.6);
    float2 rim=c+dir*float2(r.z*0.5,r.w*0.8);
    float2 p=rim+dir*(8.0+60.0*scene.bellPermissionPrimerHash1(fi+3.0))*sqrt(age)+float2(0,30.0*age*age);
    sparks[i]=float4(p,(1.2+2.6*scene.bellPermissionPrimerHash1(fi+4.0))*(1.0-0.5*age),(1.0-age)*(1.0-age)*step(0.001,m));
}
struct BellPermissionPrimerSparkOut { float4 position [[position]]; float2 uv; float a; };
vertex BellPermissionPrimerSparkOut bellPermissionPrimerSparkVs(uint vi [[vertex_id]],uint ii [[instance_id]],constant BellPermissionPrimerUniforms &F [[buffer(0)]],const device float4 *sparks [[buffer(1)]]) {
    float4 s=sparks[ii]; float2 corner=float2(-1,-1);
    if(vi==1u || vi==4u) corner=float2(1,-1);
    if(vi==2u || vi==3u) corner=float2(-1,1);
    if(vi==5u) corner=float2(1,1);
    float r=s.z*2; if(s.w<=0.001) r=0;
    float2 at=s.xy+corner*r;
    return {float4(at.x/F.view.x*2-1,1-at.y/F.view.y*2,0,1),corner,s.w};
}
fragment float4 bellPermissionPrimerSparkFs(BellPermissionPrimerSparkOut v [[stage_in]]) {
    float2 d=abs(v.uv);
    float star=max(exp(-d.x*10.0)*exp(-d.y*2.5),exp(-d.y*10.0)*exp(-d.x*2.5));
    float a=clamp(max(star,exp(-dot(d,d)*9.0))*v.a,0.0,1.0);
    return float4(float3(0.55,0.74,1.0)*a,a);
}
