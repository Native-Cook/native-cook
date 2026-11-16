#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Original SkSL equations use float intermediates; textures/results remain half.
// Image samples enter the RN target (encoded P3), then the finished pass adapts to SwiftUI sRGB.
namespace ReededGlassRevealShader {
// Match SkSL even for reversed edges (the left seam uses smoothstep(0.1, -0.3, u)).
float sstep(float a, float b, float x) {
    float t = clamp((x - a) / (b - a), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}
float3 decode(float3 c) {
    return select(pow((max(c, 0.0) + 0.055) / 1.055, float3(2.4)), c / 12.92, c <= 0.04045);
}
float3 encode(float3 c) {
    return select(1.055 * pow(max(c, 0.0), float3(1.0 / 2.4)) - 0.055, 12.92 * c, c <= 0.0031308);
}
float3 toP3(float3 c) {
    float3 l = decode(c);
    return encode(float3(dot(l, float3(0.8225, 0.1774, 0.0)),
                         dot(l, float3(0.0332, 0.9669, 0.0)),
                         dot(l, float3(0.0171, 0.0724, 0.9108))));
}
half4 toSwiftUI(half4 c) {
    if (c.a <= 0.0h) return half4(0.0h);
    float3 l = decode(float3(c.rgb) / float(c.a));
    float3 s = float3(dot(l, float3(1.22494018, -0.22494018, 0.0)),
                      dot(l, float3(-0.04205695, 1.04205695, 0.0)),
                      dot(l, float3(-0.01963755, -0.07863605, 1.0982736)));
    return half4(half3(encode(s)) * c.a, c.a);
}
struct Glass {

float4 uImg;
float uEdge[23];
float uSeed[22];
float uDepth[22];
float uMag;
float uMagAmt;
float2 uMagC;
float uS;
float uFlatMix;
float uKappa;
float uTau;
float uSlice;
float uSpread;
float uSag;
float uBlur;
float uEdgeK;
float uChroma;
float uGlintW;
float uSeam;
float uLight;
float uSpec;
float uRun;
float3 uRunRib;

float sq(float x) {
  return x * x;
}

float hash1(float n) {
  return fract(sin(n * 12.9898) * 43758.5453);
}

float vnoise(float y, float seed) {
  float i = floor(y);
  float f = y - i;
  float a = hash1(i + seed * 17.0);
  float b = hash1(i + 1.0 + seed * 17.0);
  float s = f * f * (3.0 - 2.0 * f);
  return mix(a, b, s);
}

float2 clampImg(float2 p) {
  return clamp(p, uImg.xy + 0.5, uImg.xy + uImg.zw - 0.5);
}

float4 img(float2 p, texture2d<half> portrait) {
  return float4(float3(toP3(float3(portrait.sample(sampler(coord::normalized, address::clamp_to_edge, filter::linear), (clampImg(p) - uImg.xy) / uImg.zw).rgb))), 1.0f);
}

float3 softAt(float2 p, texture2d<half> portraitSoft) {
  return float3(toP3(float3(portraitSoft.sample(sampler(coord::normalized, address::clamp_to_edge, filter::linear), (clampImg(p) - uImg.xy) / uImg.zw).rgb)));
}

// the seams wander a little as they run down the glass: each inner seam is its own smooth curve (a wander keyed to
// screen x jumped wherever x crossed a pitch boundary and printed a stair-step through the flute)
float wander(float yl, float k) {
  return 0.55 * sin(yl / 83.0 + k * 1.7) + 0.3 * sin(yl / 37.0 + k * 3.1);
}

float4 glass(float2 xy, float fixedDepth, texture2d<half> portrait, texture2d<half> portraitSoft) {
  float yl = xy.y / uS;
  float xw = xy.x;
  int idx = 0;
  for (int k = 1; k < 22; k++) {
    if (xw >= uEdge[k] + uS * wander(yl, float(k))) { idx = k; }
  }
  float e0 = 0.0;
  float e1 = 1.0;
  float a = 1.0;
  float aL = -9.0;
  float aR = -9.0;
  float sd = 0.5;
  for (int k = 0; k < 22; k++) {
    if (k == idx) { e0 = uEdge[k]; e1 = uEdge[k + 1]; a = uDepth[k]; sd = uSeed[k]; }
    if (k == idx - 1) { aL = uDepth[k]; }
    if (k == idx + 1) { aR = uDepth[k]; }
  }
  if (idx > 0) { e0 += uS * wander(yl, float(idx)); }
  if (idx < 21) { e1 += uS * wander(yl, float(idx + 1)); }
  if (aL < -8.0) { aL = a; }
  if (aR < -8.0) { aR = a; }
  if (fixedDepth >= 0.0) { a = fixedDepth; aL = fixedDepth; aR = fixedDepth; }
  float hw = 0.5 * (e1 - e0);
  float cx = e0 + hw;
  float u = clamp((xw - cx) / hw, -1.0, 1.0);
  float r1 = fract(sd * 7.13 + 0.17);
  float r2 = fract(sd * 13.71 + 0.53);
  float r3 = fract(sd * 29.3 + 0.81);
  float da = clamp(a, 0.0, 1.0);
  float al = clamp(a, -0.3, 1.3);

  // One cylinder lens per flute. Only x refracts (sag aside), so a feature keeps its scanline.
  float mag = 1.0 + uMagAmt * clamp(fixedDepth >= 0.0 ? fixedDepth : uMag, 0.0, 1.0);
  float sx0 = 1.0 / (mag * (1.0 + uSpread * al));
  float cp = uMagC.x + (cx - uMagC.x) * sx0;
  float hW = hw * sx0;
  float ph = uTau * u;
  float t1 = sin(uTau) / cos(uTau);
  float tt = sin(ph) / cos(ph) / t1;
  float dtt = uTau / (cos(ph) * cos(ph) * t1);
  // thicker glass towards the edges: the outer flutes read further across the scene and pick up the ears as thin slivers
  float edgeD = abs(cx - uMagC.x) / (0.5 * uImg.z);
  float kap = uKappa * (0.92 + 0.16 * r1) * (1.0 + uEdgeK * edgeD * edgeD);
  float lensX = cp + hW * (kap * tt + uSlice * (2.0 * r2 - 1.0));
  float sx = mix(xy.x, lensX, al);
  float foot = abs(mix(1.0, sx0 * kap * dtt, al));
  // five taps, 1.4 pt apart on the SCREEN: the squeeze at the flanks keeps the same soft edge as the crest
  float sp = clamp(uBlur * foot, 0.3, 14.0) * uS;
  // near the seams a flute pulls the scene away from the face's centre line: hairlines climb into cusps, dark necks run down
  float sy = uMagC.y + (xy.y - uMagC.y) * (1.0 - uSag * al * pow(abs(u), 4.0) * (0.6 + 0.8 * r3));
  float2 p = float2(sx, sy);

  float3 soft = (softAt(p - float2(2.0 * sp, 0.0), portraitSoft) + 4.0 * softAt(p - float2(sp, 0.0), portraitSoft) + 6.0 * softAt(p, portraitSoft) +
                4.0 * softAt(p + float2(sp, 0.0), portraitSoft) + softAt(p + float2(2.0 * sp, 0.0), portraitSoft)) * 0.0625;
  float off = uChroma * uS * sstep(0.2, 0.6, abs(aR - aL));
  if (off > 0.05) {
    soft.r = softAt(p + float2(off * foot, 0.0), portraitSoft).r;
    soft.b = softAt(p - float2(off * foot, 0.0), portraitSoft).b;
  }
  float3 col = mix(float4(float3(toP3(float3(portrait.sample(sampler(coord::normalized, address::clamp_to_edge, filter::linear), (clampImg(p) - uImg.xy) / uImg.zw).rgb))), 1.0f).rgb, soft, sstep(0.0, 0.5, da));

  float lum = dot(col, float3(0.299, 0.587, 0.114));
  // Where the lens and the straight view are both the pale wall, keep the wall flat (no curtain pleats from its vignette);
  // the wall seen through a flank next to the face stays as it is.
  float3 straight = softAt(xy, portraitSoft);
  float wallBoth = sstep(0.66, 0.80, lum) * sstep(0.66, 0.80, dot(straight, float3(0.299, 0.587, 0.114)));
  col = mix(col, straight, wallBoth * 0.85 * da);
  lum = dot(col, float3(0.299, 0.587, 0.114));
  // The pale wall keeps its exposure; the subject is greyer with near-black facial shadows.
  float subject = 1.0 - sstep(0.56, 0.78, lum);
  float3 neutral = mix(col, float3(lum), 0.32);
  float3 toned = max(float3(0.012), (neutral - float3(0.28)) * 1.5 + float3(0.30 - 0.08 * (1.0 - sstep(0.1, 0.3, lum))));
  col = mix(col, toned, da * subject * (1.0 - sstep(0.40, 0.60, lum)));

  // A rounded flute: soft dark troughs at the seams, a broad lift where the key light lands on its left flank.
  float flank = sstep(0.45, 1.0, abs(u));
  float sub = 0.05 + 0.95 * subject;
  col *= 1.0 - da * (0.04 + 0.26 * flank) * sub;
  col += float3(0.05 * da * exp(-sq((u + 0.42) / 0.36)) * subject);

  // Glints. The key glint wanders about the lit crest and slides with the light; a second, wider-wandering one
  // crosses it (the pin's X shapes); a bright hairline rides the lit seam. Each is lit along only part of its length.
  float ed = (1.0 - abs(u)) * hw;
  float lam = (220.0 + 160.0 * r1) * uS;
  float ug = -0.34 + 0.30 * uLight + 0.12 * sin(6.2832 * xy.y / lam + 6.2832 * r2) + 0.10 * (vnoise(yl / 83.0, r3 * 5.0) - 0.5);
  float gs = max(uGlintW * uS, 0.05);
  float g1 = exp(-sq((u - ug) * hw / gs));
  // one softbox above the glass: the crest glints are strongest across the face and fade towards the top and the capsule
  float box = 0.30 + 0.70 * exp(-sq((yl - 250.0) / 170.0));
  float lit = (0.35 + 0.65 * r3) * (0.55 + 0.45 * vnoise(yl / 95.0 + r2 * 3.0, r1 * 9.0 + 1.0));
  // a second glint now and then crosses the flute on a slant (the pin's cheek and hairline strokes)
  float lam2 = (110.0 + 70.0 * r2) * uS;
  float ug2 = 0.80 * sin(6.2832 * xy.y / lam2 + 6.2832 * r3);
  float g2 = exp(-sq((u - ug2) * hw / gs));
  float lit2 = sstep(0.62, 0.86, vnoise(yl / 55.0 + r3 * 4.0, r2 * 7.0 + 2.0));
  float seamL = exp(-sq((ed - 1.4 * uS) / (0.5 * uS))) * sstep(0.1, -0.3, u);
  float litS = sstep(0.38, 0.78, vnoise(yl / 45.0 + r1 * 5.0, r3 * 3.0 + 4.0));
  float gl = clamp(g1 * lit * box + 0.5 * g2 * lit2 * box + 0.8 * seamL * litS * box, 0.0, 1.0);
  float3 glintCol = float3(0.95, 0.95, 0.98);
  col = mix(col, glintCol, gl * (0.25 + 0.75 * subject) * 0.95 * da * uSpec);
  col *= 1.0 - 0.85 * exp(-sq(ed / (uSeam * uS))) * da * (0.3 + 0.7 * subject);

  // The front: the flute that is half down carries a 2 pt white specular. (clamped to 0..1 depth: no flash on the overshoot)
  float fs = exp(-sq((a - 0.5) / 0.2)) * step(0.0, a) * step(a, 1.0);
  col = mix(col, float3(1.0), 0.8 * fs * exp(-sq((u + 0.30) * hw / uS)) * uSpec);

  // At rest a soft glint runs down three flutes, once a loop.
  float isRun = 0.0;
  if (abs(float(idx) - uRunRib.x) < 0.5 || abs(float(idx) - uRunRib.y) < 0.5 || abs(float(idx) - uRunRib.z) < 0.5) { isRun = 1.0; }
  float y0 = mix(-40.0, 470.0, clamp(uRun, 0.0, 1.0)) * uS;
  float pulse = exp(-sq((xy.y - y0) / (55.0 * uS))) * step(0.0, uRun) * sin(3.1416 * clamp(uRun, 0.0, 1.0));
  col = mix(col, glintCol, isRun * pulse * exp(-sq((u - ug) * hw / (1.1 * uS))) * 0.55 * (0.4 + 0.6 * subject) * da);
  return float4(col, 1.0);
}

float4 render(float2 xy, texture2d<half> portrait, texture2d<half> portraitSoft) {
  if (uFlatMix < 0.0) { return glass(xy, -1.0, portrait, portraitSoft); }
  // No moving refraction, magnification or spring in Reduce Motion. Only opacity changes.
  return mix(glass(xy, 1.0, portrait, portraitSoft), img(xy, portrait), clamp(uFlatMix, 0.0, 1.0));
}

};
struct Capsule {

float4 uImg;
float4 uRect;
float uR;
float2 uBtn;
float uBtnR;
float4 uThumb;
float3 uTop;
float3 uLow;
float3 uEnd;
float uS;
float uFront;
float2 uEnds;

float sq(float x) {
  return x * x;
}

float4 img(float2 p, float4 r, texture2d<half> portrait) {
  float2 q = clamp(p, r.xy + 0.5, r.xy + r.zw - 0.5);
  return float4(float3(toP3(float3(portrait.sample(sampler(coord::normalized, address::clamp_to_edge, filter::linear), (q - uImg.xy) / uImg.zw).rgb))), 1.0f);
}

float sdBox(float2 p, float2 b, float r) {
  float2 q = abs(p) - b + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

float4 render(float2 xy, texture2d<half> portrait) {
  float2 b = 0.5 * uRect.zw;
  float2 p = xy - (uRect.xy + b);
  float d = sdBox(p, b, uR);
  if (d > 1.0) { return float4(0.0); }
  float e = 0.5;
  float2 g = float2(sdBox(p + float2(e, 0.0), b, uR) - sdBox(p - float2(e, 0.0), b, uR),
                    sdBox(p + float2(0.0, e), b, uR) - sdBox(p - float2(0.0, e), b, uR));
  float2 nrm = g / max(length(g), 0.0001);
  float inside = -d;

  // body: a dark vertical fall, lifted where the white button lights the glass
  float ty = clamp((xy.y - uRect.y) / uRect.w, 0.0, 1.0);
  float3 col = mix(uTop, uLow, sstep(0.1, 0.95, ty));
  // the white button and the frosted card light the glass around them
  float dB = max(length(xy - uBtn) - uBtnR, 0.0) / (16.0 * uS);
  float dT = max(sdBox(xy - (uThumb.xy + 0.5 * uThumb.zw), 0.5 * uThumb.zw, 4.0 * uS), 0.0) / (20.0 * uS);
  col += float3(0.14 * exp(-dB * dB) + 0.13 * exp(-dT * dT));

  // thick ends: the glass looks 34 pt outward (the wall turns the pin's grey), a broad haze from each end
  // and a crisp rim along the curved edge
  // the ends read the glass above them: as the front passes (depth -> 0) they refract less and blur less
  float dEnd = clamp(mix(uEnds.x, uEnds.y, clamp((xy.x - uRect.x) / uRect.z, 0.0, 1.0)), 0.0, 1.0);
  float2 sp = xy + nrm * (14.0 + 20.0 * dEnd) * uS;
  float3 seen = float3(0.0);
  for (int j = 0; j < 5; j++) {
    seen += img(sp + float2(0.0, (float(j) - 2.0) * (2.5 + 4.5 * dEnd) * uS), uImg, portrait).rgb;
  }
  seen = seen / 5.0;
  float3 rimCol = mix(uEnd, seen * 0.76, 0.45);
  float endD = b.x - abs(p.x);
  float reach = mix(40.0, 14.0, sstep(0.2, 1.0, ty)) * uS; // the haze reaches further along the top
  float haze = exp(-endD / reach);
  col = mix(col, rimCol, 0.92 * haze);
  float rim = exp(-inside / (9.0 * uS)) * pow(abs(nrm.x), 1.2);
  col = mix(col, rimCol, 0.85 * rim);

  // a 1 pt highlight along the top edge, a darker lip along the bottom
  float lip = 1.0 - sstep(0.0, 1.2 * uS, inside);
  // one light across both glasses: the front's 2 pt specular continues here, a 40 pt stretch of the highlight 0.30 -> 0.75
  float lit = 0.30 + 0.45 * exp(-sq((xy.x - uFront) / (14.0 * uS)));
  col += float3(lit * lip * sstep(0.2, 0.9, -nrm.y));
  col *= 1.0 - 0.25 * lip * sstep(0.2, 0.9, nrm.y);

  float alpha = 1.0 - sstep(-0.35, 0.35, d);
  return float4(col * alpha, alpha);
}

};

} // namespace ReededGlassRevealShader

// 93 floats, declaration order from rgr-shaders.ts.
[[ stitchable ]] half4 reededGlassRevealGlass(float2 xy, half4 color, texture2d<half> portrait, texture2d<half> portraitSoft, device const float *u, int count) {
    (void)color;
    if (count < 93) return half4(0.0h);
    ReededGlassRevealShader::Glass s;
    s.uImg = float4(u[0], u[1], u[2], u[3]);
    for (int i = 0; i < 23; i++) s.uEdge[i] = u[4 + i];
    for (int i = 0; i < 22; i++) s.uSeed[i] = u[27 + i];
    for (int i = 0; i < 22; i++) s.uDepth[i] = u[49 + i];
    s.uMag = u[71];
    s.uMagAmt = u[72];
    s.uMagC = float2(u[73], u[74]);
    s.uS = u[75];
    s.uFlatMix = u[76];
    s.uKappa = u[77];
    s.uTau = u[78];
    s.uSlice = u[79];
    s.uSpread = u[80];
    s.uSag = u[81];
    s.uBlur = u[82];
    s.uEdgeK = u[83];
    s.uChroma = u[84];
    s.uGlintW = u[85];
    s.uSeam = u[86];
    s.uLight = u[87];
    s.uSpec = u[88];
    s.uRun = u[89];
    s.uRunRib = float3(u[90], u[91], u[92]);
    return ReededGlassRevealShader::toSwiftUI(half4(s.render(xy, portrait, portraitSoft)));
}

// 29 floats, declaration order from rgr-shaders.ts.
[[ stitchable ]] half4 reededGlassRevealCapsule(float2 xy, half4 color, texture2d<half> portrait, device const float *u, int count) {
    (void)color;
    if (count < 29) return half4(0.0h);
    ReededGlassRevealShader::Capsule s;
    s.uImg = float4(u[0], u[1], u[2], u[3]);
    s.uRect = float4(u[4], u[5], u[6], u[7]);
    s.uR = u[8];
    s.uBtn = float2(u[9], u[10]);
    s.uBtnR = u[11];
    s.uThumb = float4(u[12], u[13], u[14], u[15]);
    s.uTop = float3(u[16], u[17], u[18]);
    s.uLow = float3(u[19], u[20], u[21]);
    s.uEnd = float3(u[22], u[23], u[24]);
    s.uS = u[25];
    s.uFront = u[26];
    s.uEnds = float2(u[27], u[28]);
    return ReededGlassRevealShader::toSwiftUI(half4(s.render(xy, portrait)));
}
