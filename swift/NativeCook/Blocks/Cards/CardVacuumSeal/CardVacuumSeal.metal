#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Line-for-line port of cvs-shaders.ts; slope map and card are sampled as encoded sRGB.
namespace CardVacuumSealShader {
inline float sstep(float a, float b, float x) {
    float k = clamp((x-a)/(b-a), 0.0, 1.0);
    return k*k*(3.0-2.0*k);
}


constant float P_DRAPE = 0.3;
constant float2 SHEET_PAD = float2(34.0, 38.5);
constant float SHEET_DY = 14.5;
constant float FALL = 60.0;
constant float DOME = 22.0;
constant float TUBE_R = 5.5;
constant float CREASE_M = 26.0;
constant float2 CREASE_PX = float2(1218.0, 825.0);


struct Scene {
    float uCreaseOn;
    float uS;
    float2 uC;
    float2 uCB;
    float uCR;
    float uLip;
    float uEar;
    float uP;
    float uPuff;
    float uSettle;
    float uGlint;
    float uSweep;
    float uSweepK;
    float uAz;
    float3 uDimple;
    float3 uFinger;
    float uStretch;
    float uShimmer;
    float uFade;
    float uMapK;
    float uSeed;
    float uAmp;
    float uExtra;
    float3 uTint;
float3 cMid() { return float3(0.0510, 0.0667, 0.1216); }
float3 cBloom() { return float3(0.4196, 0.2471, 0.8471); }
float3 cGel() { return float3(0.8235, 0.7373, 1.0000); }
float3 cShadow() { return float3(0.0196, 0.0118, 0.0314); }

float sq(float v) { return v*v; }
float sdRR(float2 p, float2 b, float r) {
  float2 q = abs(p) - b + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}
float sdSeg(float2 p, float2 a, float2 b) {
  float2 pa = p - a; float2 ba = b - a;
  float k = clamp(dot(pa, ba)/dot(ba, ba), 0.0, 1.0);
  return length(pa - ba*k);
}
float segT(float2 p, float2 a, float2 b) {
  float2 pa = p - a; float2 ba = b - a;
  return clamp(dot(pa, ba)/dot(ba, ba), 0.0, 1.0);
}
float smin(float a, float b, float k) {
  float hh = clamp(0.5 + 0.5*(b - a)/k, 0.0, 1.0);
  return mix(b, a, hh) - k*hh*(1.0 - hh);
}
float sat(float v) { return clamp(v, 0.0, 1.0); }
float easeS(float v) { float x = sat(v); return x*x*(3.0 - 2.0*x); }
float hbox(float v, float c, float hw, float soft) { return 1.0 - sstep(hw - soft, hw, abs(v - c)); }
// a rounded softbox: a superellipse (n = 3) with a soft edge, so no reflection has a rectangle's corners
float rbox(float a, float b, float ha, float hb, float soft) {
  float2 u = abs(float2(a/ha, b/hb));
  float rr = pow(u.x*u.x*u.x + u.y*u.y*u.y, 0.33333);
  return 1.0 - sstep(1.0 - soft, 1.0, rr);
}

// ---- the film's outline and height ------------------------------------------------------------
// where the sheet sits during the drape: falls FALL units and lands with a 3 unit sag
float sheetShift(float p) {
  float d = sat(p/P_DRAPE);
  float e = 1.0 - (1.0 - d)*(1.0 - d)*(1.0 - d);
  float sag = 3.0*sin(3.14159*sat((d - 0.62)/0.38));
  return -FALL*(1.0 - e) + sag;
}
// a corner is a soft bunch of film that bulges outward, rounded (not a thorn): a disc just past the lip's corner
float2 bunchCentre(float2 sg) { return sg*(uCB + float2(uLip*0.25, uLip*0.2)); }
float sdEar(float2 q, float2 sg) {
  return length(q - bunchCentre(sg)) - uEar*0.62;
}
// Rounded, bowed sides carry the inflated volume past the rigid card; only corners gather. A finger that drags past
// the bottom edge pulls the lip with it (uStretch).
float skinBodySd(float2 q) {
  float st = 0.5*uStretch;
  float2 qq = q - float2(0.0, st);
  float2 bb = uCB + float2(uLip, uLip + st);
  float2 u = qq/bb;
  float bow = 2.5*sat(1.0 - u.x*u.x)*sat(1.0 - u.y*u.y);
  return sdRR(qq, bb, uCR + uLip + 8.0) - bow;
}
float sdSkin(float2 q) {
  float d = skinBodySd(q);
  d = smin(d, sdEar(q, float2(-1.0, -1.0)), 14.0);
  d = smin(d, sdEar(q, float2(1.0, -1.0)), 14.0);
  d = smin(d, sdEar(q, float2(1.0, 1.0)), 14.0);
  d = smin(d, sdEar(q, float2(-1.0, 1.0)), 14.0);
  return d;
}
// a plastic sheet, not a tray: around its edge the film gathers in irregular puckers (phase-modulated, integer
// frequencies so the angle wraps without a seam), denser where the corners pinch
float pucker(float a) {
  return 0.9*sin(23.0*a + 1.7*sin(5.0*a + 0.6)) + 0.55*sin(41.0*a + 2.0 + 1.1*sin(3.0*a));
}
// the pillow is a superellipse (n = 5): a soft-cornered box whose dome has no seams; its outline wanders a little
// (a low lobe and the puckers), so the silhouette reads as a soft sheet, not a drawn rounded rectangle
float pillowN(float2 dsn, float p) {
  float2 r = dsn - uC - float2(0.0, SHEET_DY + sheetShift(p));
  float2 u = abs(r/(uCB + SHEET_PAD));
  float a = atan2(r.y, r.x);
  float wob = 1.0 + 0.011*sin(3.0*a + 1.1) + 0.007*sin(7.0*a + 0.4) + 0.0045*pucker(a);
  return pow(pow(u.x, 5.0) + pow(u.y, 5.0), 0.2)/wob;
}
float sdPillow(float2 dsn, float p) {
  return (pillowN(dsn, p) - 1.0)*(uCB.y + SHEET_PAD.y);
}
// the order in which the film meets the card: 0 at the top-left corner, 1 at the bottom edge.
// (cvs-motion.ts orderAt() is the same function, so the finger can lead the front)
float contactOrder(float2 q) {
  float2 u = clamp(q/uCB, -1.3, 1.3)*0.5 + 0.5;
  return sat(0.42*u.x + 0.58*u.y);
}
// local progress of the air leaving at this point (0 pillow … 1 skin)
float airGone(float2 q, float p) {
  float e = sat((p - P_DRAPE)/(1.0 - P_DRAPE));
  float W = 0.45;
  return easeS((e*(1.0 + W) - contactOrder(q))/W);
}
// the baked creases, as SLOPES (d h/dx, d h/dy; companded s = 0.6 sign(v) v^2): one layout, mirrored in x for seed 1 and
// turned 180 degrees (mirrored in x and y) for seed 2, which keeps the creases off the wordmark. A slope map has no 8-bit
// terraces, which a hard-edged softbox would show; flat outside the map. Baked procedurally by
// .work/card-vacuum-seal/tools/bake_crease_slopes.py (no Blender, no image model).
float2 creaseSlope(float2 q, texture2d<half> atlas) {
  float2 hf = uCB + float2(CREASE_M, CREASE_M);
  float2 uv = (q + hf)/(2.0*hf);
  float fx = uSeed > 0.5 ? -1.0 : 1.0;
  float fy = uSeed > 1.5 ? -1.0 : 1.0;
  uv = float2(fx > 0.0 ? uv.x : 1.0 - uv.x, fy > 0.0 ? uv.y : 1.0 - uv.y);
  constexpr sampler cs(coord::normalized, address::clamp_to_edge, filter::linear);
  // The slope map is copied byte-for-byte below the card, with a 2 px transparent guard row.
  float2 creasePx = clamp(uv*CREASE_PX, float2(0.5), CREASE_PX-float2(0.5));
  creasePx.y += float(atlas.get_height())-CREASE_PX.y;
  half4 m = atlas.sample(cs, creasePx/float2(atlas.get_width(), atlas.get_height()));
  float2 v = float2(m.r, m.g)*2.0 - 1.0;
  // the map's corner crimps converge to a point, which printed a ring of dots in each ear: the ears keep their own bunch
  float corner = sstep(5.0, 18.0, length(abs(q) - uCB));
  return float2(sign(v.x)*v.x*v.x*0.6*fx, sign(v.y)*v.y*v.y*0.6*fy)*corner;
}
// Fallback while the baked map is not decoded (uCreaseOn = 0): two ridged octaves, 0.9 pt peak, restricted to 14 pt of the rigid card edge.
// No raster map, texture sampling or simulation is needed. The integer seed moves the crease layout.
float creaseHash(float2 p) {
  return fract(sin(dot(p,float2(127.1,311.7)))*43758.5453);
}
float creaseNoise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  f = f*f*(3.0 - 2.0*f);
  float a = creaseHash(i);
  float b = creaseHash(i + float2(1.0,0.0));
  float c = creaseHash(i + float2(0.0,1.0));
  float d = creaseHash(i + float2(1.0,1.0));
  return mix(mix(a,b,f.x),mix(c,d,f.x),f.y);
}
float fallbackCreases(float2 q) {
  float edge = 1.0 - sstep(0.0, 14.0, abs(sdRR(q, uCB, uCR)));
  float2 phase = float2(uSeed*11.7, uSeed*23.1);
  float a = 1.0 - abs(2.0*creaseNoise(q*0.18 + phase) - 1.0);
  float b = 1.0 - abs(2.0*creaseNoise(float2(q.y,-q.x)*0.36 + phase) - 1.0);
  return 0.9*edge*(a + 0.5*b)/1.5;
}
float cornerPleats(float2 q) {
  float2 d = q - sign(q + float2(0.0001))*uCB;
  float radius = length(d);
  float angle = atan2(d.y, d.x);
  return 0.9*sin(7.0*angle + radius*0.24 + uSeed*0.7)*exp(-radius/16.0)*sstep(1.0, 5.0, radius);
}
float longCreases(float2 q) {
  float2 a = float2(-uCB.x + 8.0, -uCB.y + 10.0);
  float2 b = float2(-40.0, 18.0);
  float c1 = 0.75*exp(-sq(sdSeg(q,a,b)/2.0))*(1.0 - sstep(0.35,1.0,segT(q,a,b)));
  a = float2(uCB.x - 6.0, -uCB.y + 6.0); b = float2(52.0,-14.0);
  float c2 = 0.45*exp(-sq(sdSeg(q,a,b)/2.2))*(1.0 - sstep(0.35,1.0,segT(q,a,b)));
  a = float2(uCB.x - 10.0, uCB.y - 6.0); b = float2(30.0,40.0);
  float c3 = 0.7*exp(-sq(sdSeg(q,a,b)/2.0))*(1.0 - sstep(0.35,1.0,segT(q,a,b)));
  return c1 + c2 + c3;
}
// two extra short creases that a fast squeegee leaves behind
float extraCreases(float2 q) {
  float2 a1 = float2(0.42*uCB.x, -uCB.y + 8.0);
  float2 b1 = a1 + float2(-13.0, 24.0);
  float2 a2 = float2(uCB.x - 7.0, 0.18*uCB.y);
  float2 b2 = a2 + float2(-26.0, 9.0);
  float c1 = exp(-sq(sdSeg(q, a1, b1)/2.8))*(1.0 - sstep(0.4, 1.0, segT(q, a1, b1)));
  float c2 = exp(-sq(sdSeg(q, a2, b2)/3.0))*(1.0 - sstep(0.4, 1.0, segT(q, a2, b2)));
  return 0.9*c1 - 0.8*c2;
}
// A crown above the card: continuous curvature all the way across the face with a soft shoulder.
// The normal drives the reflection, not a painted gradient.
float skinVolume(float2 q) {
  float so = skinBodySd(q);
  float shoulder = sstep(0.0, 24.0, -so);
  float2 u = q/(uCB + uLip);
  float dome = pow(sat(1.0 - u.x*u.x)*sat(1.0 - u.y*u.y), 0.72);
  return 3.2*shoulder + DOME*dome;
}
float hSkin(float2 q) {
  float cd = sdRR(q, uCB, uCR);
  float h = skinVolume(q);
  // the thick rolled lip: an inflated tube (radius 5.5, 11 wide) along the whole border, its radius breathing along the
  // perimeter so the soft wrinkle highlights run along the edges and into the corners
  float ed = -sdSkin(q);
  float ang = atan2(q.y/uCB.y, q.x/uCB.x);
  float rt = TUBE_R*(1.0 + 0.13*sin(5.0*ang + 0.9) + 0.07*sin(11.0*ang + 2.1));
  // outer half a round section (the silhouette is seen edge-on), inner half a cosine into the face: no slope jump where
  // the tube meets the shoulder (a semicircle there printed a stepped double line along the rim)
  // In an ear the tube's inner side would fall again toward the ear's centre, a cone tip of the distance field that
  // printed a ring with a dot in each corner: there the section stays at its crown, so the ear is one soft bunch.
  float tx = (ed - rt)/rt;
  float2 eb = q - bunchCentre(sign(q + float2(0.0001)));
  float earW = exp(-dot(eb, eb)/72.0);
  float inner = mix(rt*(0.5 + 0.5*cos(3.14159*min(tx, 1.0))), rt, earW);
  h += (tx < 0.0 ? rt*sqrt(max(0.0, 1.0 - tx*tx)) : inner)*step(0.0, ed);
  // the four corners: soft rounded bunches that swell past the lip
  float2 b0 = q - bunchCentre(float2(-1.0, -1.0));
  float2 b1 = q - bunchCentre(float2(1.0, -1.0));
  float2 b2 = q - bunchCentre(float2(1.0, 1.0));
  float2 b3 = q - bunchCentre(float2(-1.0, 1.0));
  h += 0.4*(exp(-dot(b0, b0)/98.0) + exp(-dot(b1, b1)/98.0) + exp(-dot(b2, b2)/98.0) + exp(-dot(b3, b3)/98.0));
  // broad, soft undulations across the face: they catch the softbox as wide sheets
  h += 0.65*sin(q.x*0.011 + 0.9)*sin(q.y*0.017 + 1.3)*sstep(4.0, -24.0, cd);
  return h;
}
float hPillow(float2 dsn, float p) {
  float dn = pillowN(dsn, p);
  float dome = pow(sat(1.0 - dn*dn*dn), 0.55);
  float lift = 22.0*(1.0 + 0.04*uPuff);
  float2 r = dsn - uC;
  float folds = 0.9*sin(r.x*0.031 + r.y*0.012 + 0.8) + 0.6*sin(r.y*0.043 - r.x*0.02 + 2.3);
  // radial puckers where the sheet turns down to the page (they break the rim into glints, as gathered film does)
  float2 rc = r - float2(0.0, SHEET_DY + sheetShift(p));
  float edge = sstep(0.72, 0.96, dn);
  float pk = 1.3*edge*pucker(atan2(rc.y, rc.x));
  // faint crinkles over the whole sheet: warped, crossing waves 50 to 70 pt long
  float crk = 0.55*sin(dot(rc, float2(0.083, 0.051)) + 1.6*sin(dot(rc, float2(-0.027, 0.041)) + 0.7))
            + 0.4*sin(dot(rc, float2(-0.061, 0.097)) + 1.2*sin(rc.x*0.019 + 2.0));
  // while it falls (or lifts away) the sheet flutters: a wave runs across it and settles as it lands
  float dr = sat(p/P_DRAPE);
  float flutter = 2.6*(1.0 - easeS(dr))*sin(rc.x*0.022 + rc.y*0.009 - 9.0*dr);
  return lift*dome + (folds + crk + flutter)*dome + pk;
}
float filmH(float2 dsn, float p) {
  float2 q = dsn - uC;
  float g = airGone(q, p);
  float h = g > 0.999 ? hSkin(q) : (g < 0.001 ? hPillow(dsn, p) : mix(hPillow(dsn, p), hSkin(q), g));
  // Creases form only behind the contact front; the finger can reverse the same height field.
  if (g > 0.001) {
    h += g*uSettle*uAmp*(uCreaseOn > 0.5 ? 0.0 : fallbackCreases(q) + cornerPleats(q) + longCreases(q)) + g*uSettle*uAmp*uExtra*extraCreases(q);
  }
  // the crease being born on the contact front
  h += 1.4*exp(-sq((g - 0.5)/0.15))*(0.6 + 0.4*sin(q.x*0.05 + q.y*0.03));
  // the last bubble, chased out at the bottom edge under the tile
  float e = sat((p - P_DRAPE)/(1.0 - P_DRAPE));
  float k = sat((e - 0.8)/0.2);
  float br = 24.0*(1.0 - k*k);
  float2 bc = float2(0.0, uCB.y - 18.0 + 22.0*k);
  float2 bd = q - bc;
  h += 3.0*(1.0 - k)*step(0.001, br)*exp(-dot(bd, bd)/max(br*br*0.6, 0.01))*step(0.75, e);
  // the squeegee finger: the film piles into a crease right at the fingertip (2.2 high, 30 wide, across the front)
  float2 fd = dsn - uFinger.xy;
  float2 fdir = normalize(float2(0.42/uCB.x, 0.58/uCB.y));
  float fa = dot(fd, fdir);
  float fb = dot(fd, float2(-fdir.y, fdir.x));
  h += 2.2*uFinger.z*exp(-sq(fa)/(2.0*12.7*12.7))*exp(-sq(fb)/(2.0*42.0*42.0));
  // a finger pressing the sealed film
  float2 dd = dsn - uDimple.xy;
  h -= 4.5*uDimple.z*exp(-dot(dd, dd)/1058.0);
  return h;
}
float filmSd(float2 dsn, float p) {
  float2 q = dsn - uC;
  float g = airGone(q, p);
  return g > 0.999 ? sdSkin(q) : (g < 0.001 ? sdPillow(dsn, p) : mix(sdPillow(dsn, p), sdSkin(q), g));
}

// ---- the card under the film ----------------------------------------------------------------
float4 cardAt(float2 dsn, texture2d<half> map) {
  float2 q = dsn - uC;
  constexpr sampler ms(coord::normalized, address::clamp_to_zero, filter::linear);
  float2 px = (q + uCB)*uMapK;
  half4 m = map.sample(ms, px/float2(map.get_width(), map.get_height()));
  float coverage = sstep(0.6/uS, -0.6/uS, sdRR(q, uCB, uCR));
  m *= coverage;
  float a = float(m.a);
  if (a <= 0.0) { return float4(0.0); }
  float3 col = float3(m.rgb)/a;
  // the room's violet light bleeding onto the card from the screen's upper left (subtle, 0.20)
  float2 lq = (q + uCB + float2(24.0, 88.0))/float2(240.0, 270.0);
  col = mix(col, cBloom(), 0.20*exp(-dot(lq, lq)*2.2));
  // a soft glint runs over the bright content (the letters), left to right once per loop
  float lum = dot(col, float3(0.3, 0.59, 0.11));
  float letters = sstep(0.45, 0.75, lum);
  float x01 = (q.x + uCB.x)/(2.0*uCB.x);
  float wave = exp(-sq((x01 - (uShimmer*1.6 - 0.3))/0.10));
  col = mix(col, float3(1.0), 0.28*wave*letters);
  return float4(col*a, a);
}

// the studio, seen in the film, in reflected-ray space (r is the reflected view ray; its xy says where on screen the
// reflection points, 0 = straight back at the camera): a key softbox upper left (its azimuth drifts and sweeps) whose
// soft-edged core and body (rounded softboxes: broad, low-contrast folds, no rectangle corners) and a thin rim strip lower
// right. Nothing here is blurred: the crisp lines come from the film's own curvature (the tube, the creases).
float studio(float3 r, float gloss) {
  float az = uAz + uSweep*0.55*uSweepK;
  float2 kd = float2(sin(az), -cos(az));
  float along = dot(r.xy, kd);
  float across = dot(r.xy, float2(-kd.y, kd.x));
  float core = rbox(along - 0.36, across, 0.10, 0.34, 0.8);
  float body = rbox(along - 0.30, across, 0.26, 0.55, 0.9);
  float rim = hbox(along, -0.55, 0.05, 0.03)*hbox(across, 0.0, 0.30, 0.10);
  return (0.15*core*(1.0 + 0.4*uSweepK) + 0.045*body + 0.16*rim)*gloss;
}

half4 toP3(float3 c, float a) {
  float3 lin = pow(clamp(c, 0.0, 1.0), float3(2.2));
  float3 p3 = float3(0.8225*lin.r + 0.1774*lin.g, 0.0332*lin.r + 0.9669*lin.g, 0.0171*lin.r + 0.0724*lin.g + 0.8962*lin.b);
  return half4(half3(pow(p3, float3(1.0/2.2)))*half(a), half(a));
}

half4 render(float2 xy, texture2d<half> map) {
  float2 dsn = xy/uS;
  float aa = 0.6/uS;
  float p = clamp(uP, 0.0, 1.0);
  bool hasFilm = p > 0.001 && uFade > 0.001;
  float drape = sat(p/P_DRAPE);
  float kf = uFade*sat(drape*1.6);
  float sd = hasFilm ? filmSd(dsn, p) : 99.0;
  float cov = sstep(aa, -aa, sd);
  // the film's contact shadow on the page: offset (2, 3), 6 falloff, 0.35
  float shA = 0.0;
  if (hasFilm && cov < 1.0) {
    float ss = filmSd(dsn - float2(2.0, 3.0), p);
    shA = 0.35*kf*(1.0 - sstep(-1.0, 6.0, ss));
  }
  if (cov <= 0.0) {
    float4 c0 = cardAt(dsn, map);
    float sa = shA*(1.0 - c0.a);
    float A0 = c0.a + sa;
    if (A0 <= 0.0) { return half4(0.0); }
    return toP3((c0.rgb + cShadow()*sa)/A0, A0);
  }
  float2 q = dsn - uC;
  float g = airGone(q, p);
  // only the first moments of the drop are matte; a clear film is wet as soon as it lies down
  float gloss = mix(0.55, 1.0, g*g)*mix(0.55, 1.0, sstep(0.0, 0.1, p));
  // the normal of the film
  float eps = 0.8;
  float hx0 = filmH(dsn - float2(eps, 0.0), p);
  float hx1 = filmH(dsn + float2(eps, 0.0), p);
  float hy0 = filmH(dsn - float2(0.0, eps), p);
  float hy1 = filmH(dsn + float2(0.0, eps), p);
  float2 gr = float2(hx1 - hx0, hy1 - hy0)/(2.0*eps);
  if (uCreaseOn > 0.5) { gr += g*uSettle*uAmp*creaseSlope(q, map); }
  float3 n = normalize(float3(-gr.x, -gr.y, 1.0));
  // what is seen through it, ONCE: 3.5 % magnified under the pillow, bent by the slope (up to 9 under the pillow, 6 on the skin)
  float mag = mix(1.035, 1.0, g);
  // in a corner ear the film is a soft bunch past the card: it barely refracts (bent 6 pt, it showed a little mirrored
  // image of the card's rounded corner, a crescent and a dot, in every ear)
  float2 ebm = q - bunchCentre(sign(q + float2(0.0001)));
  float earM = exp(-dot(ebm, ebm)/72.0)*g;
  float2 at = uC + (dsn - uC)/mag + n.xy*mix(9.0, 6.0, g*g)*(1.0 - 0.85*earM);
  float4 under = cardAt(at, map);
  // light on the film
  float3 r = float3(2.0*n.z*n.x, 2.0*n.z*n.y, 2.0*n.z*n.z - 1.0);
  float fres = 0.04 + 0.96*pow(1.0 - n.z, 5.0);
  float spec = studio(r, gloss);
  float sheen = (fres - 0.04)*mix(0.10, 0.25, gloss);
  float crinkle = sstep(0.10, 0.34, 1.0 - n.z);
  float glint = uGlint*crinkle*exp(-sq((contactOrder(q) - 0.45)/0.55))*0.9;
  // the pin's lilac room, reflected by the surfaces that face left and lower left
  float gelA = 0.30*gloss*sstep(0.03, 0.30, dot(r.xy, float2(-0.95, 0.31)))*(1.0 - sstep(0.70, 1.0, length(r.xy)));
  // the film's own body: it ADDS light. Seen at a slant (the tube, the shoulders) the plastic is thick and milky white-lilac;
  // a veil of 30 % at the edge falls to 4 % in the middle, where the card shows clearly through
  float2 nd = n.xy/max(length(n.xy), 0.0001);
  float az = uAz + uSweep*0.55*uSweepK;
  float side = sstep(-0.25, 0.55, dot(nd, float2(sin(az), -cos(az))));
  // the milky body follows the depth into the film; in an ear it holds the tube's value, so it has no ring around a
  // centre point there either
  float ed = max(-sd, 0.0);
  ed = mix(ed, min(ed, TUBE_R), earM);
  float thick = sstep(0.08, 0.6, 1.0 - n.z);
  float tubeBand = exp(-sq((ed - TUBE_R)/(TUBE_R*0.85)));
  float veil = mix(0.55, 1.0, under.a);
  float bodyA = (0.04 + 0.18*exp(-ed/14.0))*veil + 0.45*thick + 0.22*tubeBand*(0.6 + 0.6*side);
  // silhouette: a thin bright rim on the key side, a faint one on the far side
  float rimLine = exp(-sq((sd + 0.6)/0.7));
  float rimA = rimLine*(0.45*side + 0.12*(1.0 - side))*gloss;
  float darkA = 0.06*exp(-sq((sd + 0.4)/0.55))*(1.0 - side)*kf;
  // the wordmark is embossed THROUGH the film: where it drapes over the letters their key-side edges catch the light
  float2 ld = float2(sin(az), -cos(az))*1.1;
  float4 e0 = cardAt(at - ld, map);
  float4 e1 = cardAt(at + ld, map);
  float emb = dot(e0.rgb - e1.rgb, float3(0.3, 0.59, 0.11));
  float embA = 0.7*max(emb, 0.0)*g;
  under.rgb *= 1.0 - 0.35*max(-emb, 0.0)*g;
  float specA = sat(spec + sheen + glint + rimA + embA);
  float filmA = sat(bodyA + gelA*veil + specA);
  float3 filmCol = (float3(0.90, 0.87, 1.0)*bodyA + cGel()*gelA*veil + float3(1.0, 0.99, 1.0)*specA)/max(filmA, 0.0001);
  float k = cov*kf;
  float fa = filmA*k;
  float3 pm = under.rgb*(1.0 - fa) + filmCol*filmA*k;
  float A = under.a*(1.0 - fa) + fa;
  // over the page and the tiles (nothing refracted there) the air pillow is a hazy layer of plastic, so what lies under it
  // is softened like the card is, instead of showing through crisp
  float hz = 0.24*(1.0 - under.a)*(1.0 - g)*k;
  pm = pm*(1.0 - hz) + float3(0.085, 0.08, 0.105)*hz;
  A = A*(1.0 - hz) + hz;
  float da = darkA*cov;
  pm = pm*(1.0 - da) + cShadow()*da;
  A = A*(1.0 - da) + da;
  // the shadow shows where nothing else covers
  float sa = shA*(1.0 - A);
  pm += cShadow()*sa;
  A += sa;
  if (A <= 0.0) { return half4(0.0); }
  return toP3(pm/A, A);
}

};

inline Scene load(device const float *u) {
    Scene s;
    s.uCreaseOn = u[0];
    s.uS = u[1];
    s.uC = float2(u[2], u[3]);
    s.uCB = float2(u[4], u[5]);
    s.uCR = u[6];
    s.uLip = u[7];
    s.uEar = u[8];
    s.uP = u[9];
    s.uPuff = u[10];
    s.uSettle = u[11];
    s.uGlint = u[12];
    s.uSweep = u[13];
    s.uSweepK = u[14];
    s.uAz = u[15];
    s.uDimple = float3(u[16], u[17], u[18]);
    s.uFinger = float3(u[19], u[20], u[21]);
    s.uStretch = u[22];
    s.uShimmer = u[23];
    s.uFade = u[24];
    s.uMapK = u[25];
    s.uSeed = u[26];
    s.uAmp = u[27];
    s.uExtra = u[28];
    s.uTint = float3(u[29], u[30], u[31]);
    return s;
}
inline half4 displaySRGB(half4 color) {
    if (color.a <= 0.0h) return half4(0.0h);
    float a = float(color.a);
    float3 c = float3(color.rgb) / a;
    c = select(c / 12.92, pow((c + 0.055) / 1.055, float3(2.4)), c > 0.04045);
    c = float3(dot(c, float3(1.22474527, -0.22490437, 0.0)),
               dot(c, float3(-0.04205797, 1.04208100, 0.0)),
               dot(c, float3(-0.01964227, -0.07865492, 1.09853719)));
    c = max(c, 0.0);
    c = select(c * 12.92, 1.055 * pow(c, float3(1.0 / 2.4)) - 0.055, c > 0.0031308);
    return half4(half3(c * a), color.a);
}

} // namespace CardVacuumSealShader

[[ stitchable ]] half4 cardVacuumSeal(float2 position, half4 input,
    device const float *uniforms, int uniformCount, texture2d<half> atlas) {
    if (uniformCount < 32) return half4(0.0h);
    auto s = CardVacuumSealShader::load(uniforms);
    return CardVacuumSealShader::displaySRGB(s.render(position, atlas));
}
