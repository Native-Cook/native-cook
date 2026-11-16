#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Direct translation of the approved RN SkSL. Uniform order and all shader arithmetic
// are retained, including RN's gamma-2.2 P3 encoding and post-encoding dither.
namespace SparkSphereScoreShader {
inline float mod(float x, float y) { return x - y * floor(x / y); }
constant float TAU = 6.2831853;
constant float R0 = 163.6;
constant float DEL = 5.0;

struct Disc {
float4 uGeo;
float4 uMemA;
float4 uMemB;
float4 uDim;
float4 uS1;
float4 uS2;
float4 uLook;
float4 uClock;
float3 uCoreO;
float3 uCoreI;
float3 uHazeC;
float4 uRimC;
float4 uShape;
float4 uMisc;
float3 uBase;
float4 uProbe;
float4 uBand;
float4 uRing;


float sq(float x) { return x * x; }
float sstep(float a, float b, float x) {
  float t = clamp((x - a) / (b - a), 0.0, 1.0);
  return t * t * (3.0 - 2.0 * t);
}
float angDiff(float a, float b) {
  float d = a - b;
  return atan2(sin(d), cos(d));
}
float3 hash33(float3 p3) {
  p3 = fract(p3 * float3(0.1031, 0.1030, 0.0973));
  p3 += dot(p3, p3.yxz + 33.33);
  return fract((p3.xxy + p3.yxx) * p3.zyx);
}
float rimMul(float phi) {
  float r = 1.0 + uMemA.x * cos(2.0 * phi + uMemA.y) + uMemA.z * cos(3.0 * phi + uMemA.w)
          + uMemB.x * cos(4.0 * phi + uMemB.y) + uMemB.z * cos(5.0 * phi + uMemB.w);
  float d = angDiff(phi, uDim.y);
  float w = max(uDim.z, 0.05);
  r -= uDim.x * exp(-(d * d) / (w * w));
  return r * uDim.w;
}
float rimDer(float phi) {
  float r = -2.0 * uMemA.x * sin(2.0 * phi + uMemA.y) - 3.0 * uMemA.z * sin(3.0 * phi + uMemA.w)
          - 4.0 * uMemB.x * sin(4.0 * phi + uMemB.y) - 5.0 * uMemB.z * sin(5.0 * phi + uMemB.w);
  float d = angDiff(phi, uDim.y);
  float w = max(uDim.z, 0.05);
  r += uDim.x * 2.0 * d / (w * w) * exp(-(d * d) / (w * w));
  return r * uDim.w;
}

// A divergence-free drift (the curl of a stream function), in design units. c1 carries the fine eddies, c2 only the broad ones.
float2 drift(float2 p, float ph) {
  float x = p.x / R0;
  float y = p.y / R0;
  float a1 = ph;
  float a2 = 2.0 * ph + 0.9;
  float a3 = 3.0 * ph + 2.1;
  float e = 1.0 - 0.85 * sstep(0.7, 1.0, length(float2(x, y)));
  float s1 = cos(2.3 * x + a1) * sin(2.9 * y + a2);
  float dpx = 2.3 * s1 + 0.5 * 5.1 * cos(5.1 * x - 4.2 * y + a3);
  float dpy = 2.9 * (sin(2.3 * x + a1) * cos(2.9 * y + a2)) - 0.5 * 4.2 * cos(5.1 * x - 4.2 * y + a3);
  float bx = 2.3 * s1;
  float by = 2.9 * (sin(2.3 * x + a1) * cos(2.9 * y + a2));
  return float2(uS2.y * dpy + uS2.z * by, -(uS2.y * dpx + uS2.z * bx)) * (R0 * e);
}

// the spark field: light (premultiplied, additive) at design point pd, radius ratio rho (0 centre, 1 outline)
float3 sparks(float2 pd, float rho) {
  float heat = uS1.x;
  float cold = uS1.y;
  float fill = uS1.z;
  float ph = TAU * (uClock.x + uS2.x);
  // rotation, a little faster toward the middle (more would shear the sparks into arcs)
  float th = uS1.w * (1.0 + 0.10 * (1.0 - clamp(rho, 0.0, 1.0)));
  float cs = cos(th);
  float sn = sin(th);
  float2 p = float2(cs * pd.x + sn * pd.y, -sn * pd.x + cs * pd.y);
  // look-up point: undo the drift (three fixed-point steps; the drift is smooth and its gradient stays below 0.7)
  float2 pl = p - drift(p, ph);
  pl = p - drift(pl, ph);
  pl = p - drift(pl, ph);
  float rl = length(pl);
  float k0 = floor(rl / DEL);
  float aTurn = atan2(pl.y, pl.x) * 0.15915494 + 1.0;
  float3 acc = float3(0.0);
  float jitA = 0.5 * DEL * (1.0 - cold);
  float epiAmp = uS2.w * (1.0 - 0.85 * cold);
  for (int a = -2; a <= 2; a++) {
    float k = k0 + float(a);
    if (k < 0.0 || k > 40.0) { continue; }
    float mk = max(3.0, floor(TAU * (k + 0.5) + 0.5));
    float tw = fract(k * 0.381966);
    float jc = floor(aTurn * mk - tw);
    float maxd = floor((mk - 1.0) * 0.5);
    for (int b = -2; b <= 2; b++) {
      float db = float(b);
      if (abs(db) > maxd) { continue; }
      float jw = mod(jc + db, mk);
      float3 h1 = hash33(float3(k, jw, 3.17));
      // where this slot sits when nothing moves it
      float tha = TAU * (jw + 0.5 + tw) / mk;
      float rk = (k + 0.5) * DEL;
      float2 bpos = rk * float2(cos(tha), sin(tha)) + jitA * 2.0 * (h1.yz - 0.5);
      float qb = length(bpos) / R0;
      // existence: the pin's density at rest, a flat fill when hot, everything when cold
      float clmp = 0.35 + 1.1 * (0.5 + 0.5 * sin(0.041 * bpos.x + 1.3 + TAU * uClock.x) * sin(0.047 * bpos.y - 0.7 - TAU * uClock.x));
      float pr = (0.06 + 0.94 * sstep(0.34, 0.80, qb)) * (1.0 + 0.10 * sstep(0.88, 1.0, qb)) * 0.50 * clmp;
      float pf = 0.80 * (1.0 - uClock.w * (1.0 - sstep(uRing.z, uRing.w, qb)));
      float pe = mix(pr, pf, fill);
      pe = mix(pe, 1.0, cold) * (1.0 - sstep(0.96, 1.04, qb));
      float liveK = sstep(0.0, 0.07, pe - h1.x);
      if (liveK <= 0.0) { continue; }
      float3 h2 = hash33(float3(k, jw, 7.31));
      float3 h3 = hash33(float3(k, jw, 11.7));
      // closed track: an epicycle of 1–3 cycles per loop, wider and faster when hot
      float k1 = 1.0 + floor(h3.x * 3.0);
      float k2 = 1.0 + floor(h3.y * 3.0);
      float2 epiV = epiAmp * (0.45 + 0.55 * h2.z) * float2(cos(k1 * ph + TAU * h3.y), sin(k2 * ph + TAU * h3.x));
      float2 sp = bpos + epiV + drift(bpos, ph);
      // probe: a warm finger agitates what is near it
      float2 dp = pd - uProbe.xy;
      float nrp = uProbe.w * exp(-dot(dp, dp) / (uProbe.z * uProbe.z));
      sp += (1.0 - cold) * nrp * 2.2 * epiV;
      float qd = length(sp) / R0;
      float gr = exp(-sq((qd - uBand.w) / max(uRing.y, 0.01)));
      sp += sp / max(length(sp), 1.0) * (uRing.x * 5.0 * gr);
      float2 dv = p - sp;
      float dist = length(dv);
      // size: mostly fine, a few coarse, the nearest few soft out-of-focus discs
      float qs = length(sp) / R0;
      float zs = (h2.z < 0.5 ? -1.0 : 1.0) * sqrt(max(0.0, 1.0 - min(qb, 1.0) * min(qb, 1.0)));
      float bokeh = (h3.z < 0.05 && zs > 0.18) ? 1.0 : 0.0;
      float d = bokeh > 0.5 ? 4.6 + 2.6 * h2.y : 0.9 + 4.7 * pow(h2.x, 1.9);
      d = mix(d, 1.6 + 0.9 * h2.x, cold) * (1.0 + 0.45 * max(zs, 0.0));
      float hs = 0.5 * d;
      if (dist > hs * 2.6 + 0.5) { continue; }
      float softK = bokeh > 0.5 ? 1.0 : sstep(4.4, 7.0, d) * 0.2;
      float A0 = mix((0.28 + 0.62 * pow(h2.y, 1.1)) * (0.40 + 0.60 * sstep(0.30, 0.72, qb)), 0.62, cold);
      float sizeF = 0.62 + 0.38 * sstep(1.0, 2.6, d) - 0.2 * sstep(5.0, 9.0, d);
      float backK = zs < 0.0 ? 0.55 : 1.0;
      float angS = atan2(sp.y, sp.x);
      float wv = exp(-sq(angDiff(angS, uLook.z) / 0.55));
      float twk = 1.0 + uLook.y * wv * (0.5 + 0.5 * qs);
      // crystal: rings of light travel from the pole (the middle) to the limb, one every 1.2 s
      float lat = asin(clamp(qs, 0.0, 0.999));
      float rw = pow(0.5 + 0.5 * cos(5.0 * lat - uClock.y), 3.0);
      twk = mix(twk, 1.0 + uClock.z * rw, cold);
      float fl = 1.0 + uLook.w * (sin(TAU * (k1 * (uClock.x + uS2.x) + h3.x)) * 0.5 + 0.25);
      float bd = (qs - uBand.x) / max(uBand.z, 0.01);
      float bandK = 1.0 + uBand.y * exp(-bd * bd);
      float rd = (qs - uBand.w) / max(uRing.y, 0.01);
      float ringk = 1.0 + uRing.x * exp(-rd * rd);
      float excl = 1.0 - uClock.w * (1.0 - sstep(uRing.z, uRing.w, qs));
      float limbK = 1.0 + cold * 0.9 * sstep(0.6, 1.0, qs);
      float av = A0 * sizeF * backK * uLook.x * twk * fl * bandK * ringk * excl * limbK * (1.0 - 0.35 * nrp) * liveK;
      float x = max(dist / hs, 0.0001);
      float e = 5.6 - 2.4 * softK;
      float coreV = exp(-1.6 * pow(x, e));
      float haloV = 0.05 * exp(-x * x * 1.1) * (1.0 - 0.6 * softK);
      float s0 = coreV + haloV;
      float s1 = av * s0;
      float s = s1 > 0.66 ? 0.66 + 0.2 * (1.0 - exp(-(s1 - 0.66) / 0.2)) : s1;
      float3 cc = mix(uCoreI, uCoreO, sstep(0.25, 0.9, qs));
      float3 midC = cc * 0.3;
      float3 sc3 = mix(midC, cc, coreV / max(s0, 0.0001)) * s;
      acc = acc + sc3 - acc * sc3;
    }
  }
  return acc;
}

half4 render(float2 fc) {
  float2 pd = (fc - uGeo.xy) / uGeo.z;
  float rad = length(pd);
  // nothing of the sphere (not even its shadow, which is gone by 1.45 R) reaches this far
  if (rad > 1.5 * R0) { return half4(0.0); }
  float phi = atan2(pd.y, pd.x);
  float m = rimMul(phi);
  float Rm = R0 * m;
  float slp = rimDer(phi) / max(m, 0.2);
  float dist = (rad - Rm) / sqrt(1.0 + slp * slp);
  float rho = rad / max(Rm, 1.0);
  float aa = 0.75 / max(uGeo.w * uGeo.z, 0.5);
  // outside: the dark halo hugging the rim (black; premultiplied alpha adds no colour)
  float sh = uMisc.x * exp(-sq(max(dist, 0.0) / (0.11 * R0)));
  if (dist > 0.45 * R0) { return half4(half3(0.0), half(sh)); }
  float cov0 = 1.0 - sstep(-aa, aa, dist);

  // the body: the hollow, the haze deepening toward the rim (the pin's measured ramp), the inner rim glow
  float g = pow(sstep(mix(0.5, 0.2, clamp(uS1.z / 0.85, 0.0, 1.0)), 0.96, rho), 1.25);
  float3 bodyC = mix(uBase, uHazeC, clamp(g * uMisc.z, 0.0, 1.0));
  float glowV = exp(-pow(max(-dist, 0.0) / max(uShape.z, 1.0), 1.4)) * uShape.w;
  float3 rimc = uRimC.rgb;
  bodyC += rimc * glowV;

  // the sparks: the field is skipped where it cannot show
  float3 L = float3(0.0);
  if (rho < 1.12 && (rho > 0.27 || uS1.z > 0.03 || uS1.y > 0.03)) {
    L = sparks(pd / max(m, 0.5), rho);
  }
  float3 pm = (bodyC + L) * cov0;

  // the rim film: its width swings with the outline's slope; brighter on the arcs that face the key (10 and 4 o'clock)
  float w = uShape.x + uShape.y * clamp(abs(slp) * 14.0, 0.0, 1.0);
  float hw = 0.5 * w;
  float lnA = 1.0 - sstep(hw - aa, hw + aa, abs(dist));
  float k1 = exp(-sq(angDiff(phi, -2.618) / 0.9));
  float k2 = exp(-sq(angDiff(phi, 0.5236) / 0.9));
  float keyL = 1.0 + uMisc.y * (k1 + 0.7 * k2);
  float ln = lnA * clamp(uRimC.w * keyL, 0.0, 1.0);
  pm = pm * (1.0 - ln) + rimc * ln;
  float aOut = 1.0 - (1.0 - cov0) * (1.0 - ln);
  aOut = aOut + sh * (1.0 - aOut);
  pm = min(pm, float3(aOut));

  if (uMisc.w > 0.5) {
    float3 lin = pow(max(pm, float3(0.0001)), float3(2.2));
    float3 p3 = float3(0.8225 * lin.r + 0.1774 * lin.g, 0.0332 * lin.r + 0.9669 * lin.g, 0.0171 * lin.r + 0.0724 * lin.g + 0.9108 * lin.b);
    pm = pow(max(p3, float3(0.0)), float3(1.0 / 2.2)) * step(0.0001, max(pm.r, max(pm.g, pm.b)));
  }
  // one level of ordered noise (interleaved gradient noise) so the haze and the shadow do not band
  float dn = fract(52.9829189 * fract(dot(fc, float2(0.06711056, 0.00583715)))) - 0.5;
  pm = clamp(pm + float3(dn / 255.0 * aOut), float3(0.0), float3(aOut));
  return half4(half3(pm), half(aOut));
}
};

inline Disc loadDisc(device const float *u) {
    Disc s;
    s.uGeo = float4(u[0], u[1], u[2], u[3]);
    s.uMemA = float4(u[4], u[5], u[6], u[7]);
    s.uMemB = float4(u[8], u[9], u[10], u[11]);
    s.uDim = float4(u[12], u[13], u[14], u[15]);
    s.uS1 = float4(u[16], u[17], u[18], u[19]);
    s.uS2 = float4(u[20], u[21], u[22], u[23]);
    s.uLook = float4(u[24], u[25], u[26], u[27]);
    s.uClock = float4(u[28], u[29], u[30], u[31]);
    s.uCoreO = float3(u[32], u[33], u[34]);
    s.uCoreI = float3(u[35], u[36], u[37]);
    s.uHazeC = float3(u[38], u[39], u[40]);
    s.uRimC = float4(u[41], u[42], u[43], u[44]);
    s.uShape = float4(u[45], u[46], u[47], u[48]);
    s.uMisc = float4(u[49], u[50], u[51], u[52]);
    s.uBase = float3(u[53], u[54], u[55]);
    s.uProbe = float4(u[56], u[57], u[58], u[59]);
    s.uBand = float4(u[60], u[61], u[62], u[63]);
    s.uRing = float4(u[64], u[65], u[66], u[67]);
    return s;
}

struct Ruler {
float4 uBox;
float4 uTick;
float4 uMore;
float4 uCol;
float4 uInk;
float4 uMark;

float sq(float x) { return x * x; }
float sstep(float a, float b, float x) {
  float t = clamp((x - a) / (b - a), 0.0, 1.0);
  return t * t * (3.0 - 2.0 * t);
}
float boxd(float2 p, float2 hb, float r) {
  float2 q = abs(p) - hb + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}
half4 render(float2 fc) {
  float idx = clamp(floor((fc.x - uBox.x) / uBox.y + 0.5), 0.0, 80.0);
  float tx = uBox.x + idx * uBox.y;
  float dxc = tx - uTick.w;
  float dnear = abs(dxc);
  // near the cursor the ticks rise and brighten; the ones just to its right (the pin shows them) stay lit longer
  float trl = dxc > 0.0 ? exp(-dxc / (3.1 * uBox.y)) : exp(-dnear / (0.9 * uBox.y));
  float rse = 1.0 + 0.30 * exp(-sq(dnear / 30.0)) * (0.4 + 0.6 * uMore.y);
  float rr = abs(dnear - uMore.z);
  float rip = uMore.w * exp(-sq(rr / 26.0)) * step(0.0, uMore.z) * exp(-uMore.z / 150.0);
  float th = 0.5 * uBox.w * rse + rip;
  float lmk = (mod(idx, 10.0) < 0.5) ? 1.0 : 0.0;
  float3 bpos = mix(uInk.rgb, uMark.rgb, lmk);
  float hw = 0.5 * uTick.x;
  float d = boxd(fc - float2(tx, uBox.z), float2(hw, th), min(hw, 1.0));
  float cov = 1.0 - sstep(-0.35, 0.35, d);
  float3 tc = mix(bpos, uCol.rgb, clamp(trl * uCol.w, 0.0, 1.0) * 0.9);
  float aOut = cov;
  float3 pm = tc * cov;
  // the cursor: a taller bar that grows under the finger
  float ch = 0.5 * (uTick.z * (1.0 + 0.2 * uMore.y));
  float dc = boxd(fc - float2(uTick.w, uBox.z), float2(0.5 * uTick.y, ch), 1.0);
  float cc = 1.0 - sstep(-0.35, 0.35, dc);
  float3 cur = float3(0.969, 0.984, 0.988);
  pm = pm * (1.0 - cc) + cur * cc;
  aOut = 1.0 - (1.0 - aOut) * (1.0 - cc);
  if (uMark.w > 0.5) {
    float3 lin = pow(max(pm, float3(0.0001)), float3(2.2));
    float3 p3 = float3(0.8225 * lin.r + 0.1774 * lin.g, 0.0332 * lin.r + 0.9669 * lin.g, 0.0171 * lin.r + 0.0724 * lin.g + 0.9108 * lin.b);
    pm = pow(max(p3, float3(0.0)), float3(1.0 / 2.2)) * step(0.0001, max(pm.r, max(pm.g, pm.b)));
  }
  return half4(half3(pm), half(aOut));
}
};

inline Ruler loadRuler(device const float *u) {
    Ruler s;
    s.uBox = float4(u[0], u[1], u[2], u[3]);
    s.uTick = float4(u[4], u[5], u[6], u[7]);
    s.uMore = float4(u[8], u[9], u[10], u[11]);
    s.uCol = float4(u[12], u[13], u[14], u[15]);
    s.uInk = float4(u[16], u[17], u[18], u[19]);
    s.uMark = float4(u[20], u[21], u[22], u[23]);
    return s;
}

struct Ground {
float4 uGr;
float3 uG0;
float3 uG1;
half4 render(float2 fc) {
  float t = clamp((fc.y - uGr.x) / max(uGr.y - uGr.x, 1.0), 0.0, 1.0);
  float3 c = mix(uG0, uG1, t);
  if (uGr.w > 0.5) {
    float3 lin = pow(max(c, float3(0.0001)), float3(2.2));
    float3 p3 = float3(0.8225 * lin.r + 0.1774 * lin.g, 0.0332 * lin.r + 0.9669 * lin.g, 0.0171 * lin.r + 0.0724 * lin.g + 0.9108 * lin.b);
    c = pow(max(p3, float3(0.0)), float3(1.0 / 2.2));
  }
  float dn = fract(52.9829189 * fract(dot(fc, float2(0.06711056, 0.00583715)))) - 0.5;
  c = clamp(c + float3(dn / 255.0), float3(0.0), float3(1.0));
  return half4(half3(c), 1.0);
}
};

inline Ground loadGround(device const float *u) {
    Ground s;
    s.uGr = float4(u[0], u[1], u[2], u[3]);
    s.uG0 = float3(u[4], u[5], u[6]);
    s.uG1 = float3(u[7], u[8], u[9]);
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


} // namespace SparkSphereScoreShader

[[ stitchable ]] half4 sparkSphereScoreDisc(
    float2 position, half4 color, float2 origin,
    device const float *uniforms, int uniformCount
) {
    if (uniformCount < 68) return half4(0.0h);
    auto s = SparkSphereScoreShader::loadDisc(uniforms);
    return SparkSphereScoreShader::displaySRGB(s.render(position + origin));
}

[[ stitchable ]] half4 sparkSphereScoreRuler(
    float2 position, half4 color, float2 origin,
    device const float *uniforms, int uniformCount
) {
    if (uniformCount < 24) return half4(0.0h);
    auto s = SparkSphereScoreShader::loadRuler(uniforms);
    return SparkSphereScoreShader::displaySRGB(s.render(position + origin));
}

[[ stitchable ]] half4 sparkSphereScoreGround(
    float2 position, half4 color, float2 origin,
    device const float *uniforms, int uniformCount
) {
    if (uniformCount < 10) return half4(0.0h);
    auto s = SparkSphereScoreShader::loadGround(uniforms);
    return SparkSphereScoreShader::displaySRGB(s.render(position + origin));
}
