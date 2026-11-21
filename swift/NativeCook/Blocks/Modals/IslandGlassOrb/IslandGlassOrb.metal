#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;
constexpr sampler islandGlassOrbSampler(coord::normalized, address::clamp_to_edge, filter::linear);
constant float TAU = 6.2831853;
struct IslandGlassOrbShader {
float4 uBox;
float4 uDrop;
float uN;
float uM;
float uK;
float4 uNeck;
float uBan;
float uAmp;
float uAmpLag;
float4 uLine;
float uT;
float2 uPluck;
float3 uArc;
float uRefr;
float2 uRim;
float3 uShade;
float3 uShade2;
float uCap;
float uFree;
half3 toOut(half3 c) { return c; }



float sq(float x) { return x * x; }

float sdBox(float2 p, float4 b) {
  float2 hb = b.zw * 0.5;
  float r = min(hb.x, hb.y);
  float2 q = abs(p - (b.xy + hb)) - hb + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

// superellipse |x/a|^n + |y/b|^n = 1; distance ≈ F / |grad F|; R is the normalised radius (0 centre, 1 rim)
float sdSuper(float2 p, thread float &R) {
  float2 q = max(abs(p - uDrop.xy) / uDrop.zw, float2(1e-4));
  float s = pow(q.x, uN) + pow(q.y, uN);
  R = pow(s, 1.0 / uN);
  float2 g = pow(R, 1.0 - uN) * float2(pow(q.x, uN - 1.0) / uDrop.z, pow(q.y, uN - 1.0) / uDrop.w);
  return (R - 1.0) / max(length(g), 1e-4);
}

float sdNeck(float2 p) {
  float2 a = uNeck.xy;
  // the thread ends 8 pt inside the drop's top, so the black funnel feeds the lens instead of running through it
  float2 ba = float2(uDrop.x, uDrop.y - uDrop.w + 8.0) - a;
  float2 pa = p - a;
  float h = clamp(dot(pa, ba) / max(dot(ba, ba), 1e-3), 0.0, 1.0);
  // a funnel while the lens is still near (it tapers from the island's end down to the thread's thinnest, no bulb inside the
  // lens), the hourglass with a flare at both ends once the lens is free
  float rBase = mix(uNeck.z, mix(uNeck.w, uNeck.z, uFree), h);
  float r = mix(rBase, uNeck.w, 4.0 * h * (1.0 - h));
  return length(pa - ba * h) - r;
}

float smin(float a, float b, float k) {
  if (k < 0.01) return min(a, b);
  float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
  return mix(b, a, h) - k * h * (1.0 - h);
}

float scene(float2 p) {
  float dB = sdBox(p, uBox);
  if (uM < 0.001) return dB;
  float R;
  float dD = sdSuper(p, R);
  if (uNeck.w > 0.05) dD = smin(dD, sdNeck(p), uK);
  return smin(dB, dD, uK);
}

// the spectrum in its visible order, cyclic: red-orange → warm → cyan → blue → magenta → red-orange
float3 spectrum(float x) {
  float3 c0 = float3(1.000, 0.353, 0.122);
  float3 c1 = float3(0.961, 0.804, 0.588);
  float3 c2 = float3(0.392, 0.957, 0.976);
  float3 c3 = float3(0.169, 0.357, 1.000);
  float3 c4 = float3(1.000, 0.176, 0.541);
  float u = fract(x) * 5.0;
  float i = floor(u);
  float f = smoothstep(0.0, 1.0, u - i);
  float3 a = i < 1.0 ? c0 : i < 2.0 ? c1 : i < 3.0 ? c2 : i < 4.0 ? c3 : c4;
  float3 b = i < 1.0 ? c1 : i < 2.0 ? c2 : i < 3.0 ? c3 : i < 4.0 ? c4 : c0;
  return mix(a, b, f);
}

// four colour lobes sliding through each other along the line (whole numbers of cycles per 10 s loop)
float3 lobes(float u) {
  float3 cy = float3(0.392, 0.957, 0.976);
  float3 bl = float3(0.169, 0.357, 1.000);
  float3 mg = float3(1.000, 0.176, 0.541);
  float3 rd = float3(1.000, 0.353, 0.122);
  float u1 = 0.55 * sin(TAU * 0.6 * uT + 0.3);
  float u2 = 0.55 * sin(TAU * -0.9 * uT + 2.1);
  float u3 = 0.55 * sin(TAU * 1.3 * uT + 4.0);
  float u4 = 0.55 * sin(TAU * -0.4 * uT + 5.2);
  float g1 = exp(-sq((u - u1) / 0.34));
  float g2 = exp(-sq((u - u2) / 0.34));
  float g3 = exp(-sq((u - u3) / 0.30));
  float g4 = exp(-sq((u - u4) / 0.30));
  float w = g1 + g2 + g3 + g4 + 0.05;
  return (cy * g1 + bl * g2 + mg * g3 + rd * g4 + float3(1.0) * 0.05) / w;
}

half4 render(float2 p, texture2d<half> page, texture2d<half> blur, float Hc) {
  float d = scene(p);
  if (d > 46.0) return half4(0.0);

  float mc = clamp(uM, 0.0, 1.0);
  float R = 2.0;
  float dD = uM > 0.001 ? sdSuper(p, R) : 1e3;
  float dB = sdBox(p, uBox);

  // ---- the line (shared by the inside and the bleed) ----
  float3 lineAdd = float3(0.0);
  float lineOcc = 0.0; // how much the emissive band hides what is behind it
  float3 bleed = float3(0.0);
  float3 lineCol = float3(1.0);
  // where the black cap ends: the line's light stays on the clear side of it (it used to light the cap and print the
  // hardware island's silhouette at the loudest moment)
  float capY = uDrop.y - uDrop.w + uCap * 2.0 * uDrop.w;
  float onDrop = smoothstep(0.5, 0.9, mc);
  if (uLine.z > 0.5) {
    float hl = uLine.z * 0.5;
    float u = (p.x - uLine.x) / hl;
    if (abs(u) < 1.3) {
      float env = pow(max(1.0 - u * u, 0.0), 0.75);
      float tips = 1.0 - smoothstep(0.86, 1.0, abs(u));
      // a voice with a body: thickness 3 + 26·A·Σ(four gaussians, σ 18 pt) that slide along the line, tapered ends, the
      // centreline wavering ±5·A pt at 3 Hz (whole numbers of cycles per 10 s loop)
      float x = p.x - uLine.x;
      float g = 0.0;
      for (int i = 0; i < 4; i++) {
        float fi = float(i);
        float c = hl * (-0.75 + 0.5 * fi) + 14.0 * sin(TAU * (0.5 + 0.1 * fi) * uT + 1.7 * fi);
        float w = 0.8 + 0.2 * sin(TAU * (0.3 + 0.1 * fi) * uT + 2.1 * fi);
        g += w * exp(-sq(x - c) / 648.0);
      }
      float thk = 3.0 + 26.0 * uAmp * min(g, 1.0);
      float bend = uPluck.y * exp(-sq((p.x - uPluck.x) / 22.0));
      float wig = 5.0 * uAmp * sin(TAU * 3.0 * uT - u * 3.2);
      float shift = onDrop * max(0.0, 0.5 * (3.0 + 26.0 * uAmp) + 3.0 - (uLine.y - capY));
      float dyl = p.y - (uLine.y + shift + bend + wig);
      float hT = 0.5 * thk * env + 0.45;
      float chroma = clamp(0.2 + 1.25 * uAmp, 0.0, 1.0);
      // warm above the core (red-orange ↔ magenta), cool below (cyan → blue), the lobes sliding through both
      float v = dyl / hT;
      float3 rd = float3(1.000, 0.353, 0.122);
      float3 mg = float3(1.000, 0.176, 0.541);
      float3 cy = float3(0.392, 0.957, 0.976);
      float3 bl = float3(0.169, 0.357, 1.000);
      float3 warm = mix(rd, mg, smoothstep(0.3, 0.7, 0.5 + 0.5 * sin(TAU * 0.6 * uT + u * 2.6)));
      float3 cool = mix(cy, bl, smoothstep(0.15, 0.6, v + 0.25 * sin(TAU * -0.4 * uT + u * 2.0)));
      float3 base = mix(warm, cool, smoothstep(-0.3, 0.3, v));
      lineCol = mix(float3(1.0), mix(base, lobes(u), 0.2), chroma);
      float haloMask = mix(1.0, smoothstep(capY - 1.0, capY + 7.0, p.y), onDrop);
      float bodyMask = mix(1.0, smoothstep(capY - 3.0, capY + 3.0, p.y), onDrop);
      float body = exp(-sq(sq(v)) * 1.3) * 1.05 * bodyMask;
      float hot = exp(-sq(dyl / (hT * 0.1 + 0.28))) * smoothstep(0.05, 0.5, env) * (0.6 + 0.3 * uAmp) * bodyMask;
      float halo = exp(-sq(dyl / (hT * 3.0))) * 0.16 * haloMask;
      lineAdd = (lineCol * (body + halo) + float3(hot)) * tips;
      lineOcc = min(body, 1.0) * tips * 0.85 * chroma;
      // its light bleeds down into the clear half (α 0.25, ~14 pt)
      float under = smoothstep(-2.0, 6.0, dyl);
      bleed = lineCol * 0.25 * exp(-sq(dyl / 16.0)) * under * haloMask * pow(max(1.0 - u * u * 0.7, 0.0), 1.2) * (0.5 + 0.5 * chroma);
    }
  }

  // ---- outside: shadow, caustic, the arc's light under the banner ----
  half4 outside = half4(0.0);
  if (d > -1.0) {
    float sa = uShade.x;
    if (sa > 0.002) {
      float ds = scene(p - float2(0.0, uShade.y));
      float blur = uShade2.x;
      float sh = sa * (1.0 - smoothstep(-blur * 0.35, blur, ds));
      half3 tint = toOut(half3(0.165, 0.200, 0.169));
      outside = half4(tint * sh, sh);
    }
    float3 light = float3(0.0);
    if (uShade.z > 0.001) {
      float2 cc = float2(uDrop.x, uDrop.y + uDrop.w + 5.0);
      float2 q = (p - cc) / uShade2.yz;
      float cs = exp(-dot(q, q) * 1.3) * uShade.z;
      light += mix(float3(1.0), lineCol, 0.45) * cs;
    }
    if (uArc.x > 0.001) {
      float yb = uBox.y + uBox.w;
      float ua = (p.x - (uBox.x + uBox.z * 0.5)) / max(uArc.y * 0.5, 1.0);
      float below = max(p.y - yb, 0.0);
      float g = uArc.x * 0.34 * exp(-ua * ua * 1.6) * exp(-sq(below / 9.0)) * step(0.0, p.y - yb + 0.5);
      light += spectrum(ua * 0.22 + uArc.z) * g;
    }
    outside.rgb += toOut(half3(light));
  }
  float cover = 1.0 - smoothstep(-0.35, 0.35, d);
  if (cover <= 0.0) return outside;

  // ---- inside: black cap, clear lens, smoked banner glass ----
  // near-black (#010101): next to the hardware island a lighter black reads as a second shape
  half3 black = toOut(half3(0.004, 0.0055, 0.0055));
  half3 col = black;
  // the island and its thread stay black over the lens (the black stays with the island)
  float dIsl = dB;
  if (uNeck.w > 0.05) dIsl = smin(dB, sdNeck(p), uK);
  float islandBlack = 1.0 - smoothstep(-0.6, 1.0, dIsl);
  float vD = (p.y - (uDrop.y - uDrop.w)) / (2.0 * uDrop.w);
  float capLo = mix(-0.25, 0.46, uCap / 0.46);
  float clearD = smoothstep(capLo, capLo + 0.16, vD) * (1.0 - smoothstep(-1.0, 3.0, dD)) * (1.0 - islandBlack) * smoothstep(0.15, 0.55, uM);
  float vB = (p.y - uBox.y) / uBox.w;
  float clearB = uBan * smoothstep(0.84, 0.96, vB) * (1.0 - clearD);
  if (clearB > 0.001) {
    half3 smoke = blur.sample(islandGlassOrbSampler, p / float2(402.0, 330.0)).rgb * 0.45;
    col = mix(col, smoke, clearB);
  }
  if (clearD > 0.001) {
    float2 c = uDrop.xy;
    float2 rel = p - c;
    // sample radius s(R) = R·sc: magnified 1.33× in the middle, squeezed toward the rim like a thick ball lens, and
    // monotonic, so the image never folds (a fold showed the widgets' rims as two small rainbow rings)
    float Rc = min(R, 1.0);
    float sc0 = mix(0.75, 1.0 / 1.45, uFree); // the free lens magnifies 1.45×
    float sc = sc0 + (1.2 - sc0) * Rc * Rc * Rc;
    sc = mix(1.0, sc, uRefr);
    float len = max(length(rel), 1e-3);
    float2 dir = rel / len;
    float chromaPt = mix((0.5 + 1.5 * uAmp) / 3.0 * 2.0, 4.0, uFree) * smoothstep(0.25, 1.0, R) * uRefr; // free lens: 4 pt dispersion
    float2 sG = c + rel * sc;
    half r = page.sample(islandGlassOrbSampler, (sG + dir * chromaPt) / float2(402.0, Hc)).r;
    half g = page.sample(islandGlassOrbSampler, (sG) / float2(402.0, Hc)).g;
    half b = page.sample(islandGlassOrbSampler, (sG - dir * chromaPt) / float2(402.0, Hc)).b;
    half3 glass = half3(r, g, b);
    // a darker band where the cap thins out, then Fresnel (Schlick, F0 0.04) toward the rim
    glass *= mix(mix(0.86, 1.07, smoothstep(0.52, 0.8, vD)), 1.02, uFree);
    float nz = sqrt(max(1.0 - min(R, 1.0) * min(R, 1.0), 0.0));
    float F = 0.04 + 0.96 * pow(1.0 - nz, 5.0);
    glass = mix(glass, toOut(half3(0.93, 0.95, 0.93)), F * mix(0.25, 0.4, uFree));
    glass += toOut(half3(bleed));
    col = mix(col, glass, clearD);
  }

  // ---- light: the spectrum line and the reply arc (additive) ----
  float inL = 1.0 - smoothstep(-1.0, 1.0, d);
  col *= 1.0 - lineOcc * inL;
  float3 add = lineAdd * inL;
  if (uArc.x > 0.001) {
    float yb = uBox.y + uBox.w;
    float e = yb - p.y;
    float ua = (p.x - (uBox.x + uBox.z * 0.5)) / max(uArc.y * 0.5, 1.0);
    float th = 4.2;
    float a = uArc.x * exp(-ua * ua * 2.4) * exp(-sq((e - 2.4) / th));
    float hot = uArc.x * exp(-ua * ua * 5.0) * exp(-sq((e - 1.4) / 1.2)) * 0.55;
    add += spectrum(ua * 0.22 + uArc.z) * a * 0.95 + float3(hot);
  }
  col += toOut(half3(min(add, float3(1.6))));

  // ---- the Fresnel rim: a 1.25–3 pt line driven by loudness, brighter on the lower arc, spectral when the voice is loud ----
  float fz = uFree * (1.0 - smoothstep(2.0, 10.0, dD)); // the free-lens rim belongs to the drop, not to the island or the thread
  float rimWidth = mix(mix(1.25, 3.0, clamp(uAmp, 0.0, 1.0)), 2.0, fz); // free lens: a 2 pt spectral rim
  if (uRim.x + uRim.y > 0.001 && d > -rimWidth - 0.5) {
    float2 ex = float2(0.25, 0.0);
    float2 n = normalize(float2(scene(p + ex.xy) - scene(p - ex.xy), scene(p + ex.yx) - scene(p - ex.yx)) + 1e-5);
    float lw = smoothstep(-0.15, 0.75, n.y);
    float ra = mix(mix(uRim.x, uRim.y, lw), 0.85, fz);
    float rm = 1.0 - smoothstep(rimWidth - 0.5, rimWidth + 0.25, -d);
    float spec = mix(smoothstep(0.5, 1.0, uAmpLag) * mc, 1.0, fz);
    float ang = atan2(n.y, n.x) / TAU;
    float3 rc = mix(float3(1.0), spectrum(ang * 2.0 + uT * 0.35), spec * 0.85);
    col = mix(col, toOut(half3(rc)), rm * ra);
  }

  half4 inside = half4(col, 1.0);
  return inside * cover + outside * (1.0 - cover);
}
};
[[ stitchable ]] half4 islandGlassOrb(float2 p, float4 uBox, float4 uDrop, float uN, float uM, float uK, float4 uNeck, float uBan, float uAmp, float uAmpLag, float4 uLine, float uT, float2 uPluck, float3 uArc, float uRefr, float2 uRim, float3 uShade, float3 uShade2, float uCap, float uFree, texture2d<half> page, texture2d<half> blur, float Hc) {
 IslandGlassOrbShader shader = {uBox, uDrop, uN, uM, uK, uNeck, uBan, uAmp, uAmpLag, uLine, uT, uPluck, uArc, uRefr, uRim, uShade, uShade2, uCap, uFree};
 return shader.render(p,page,blur,Hc);
}
static float islandGlassOrbHash(float2 p) {
  return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}

// Static film grain, a premultiplied 3% overlay, matching GRAIN with uA = 0.03.
[[ stitchable ]] half4 islandGlassOrbGrain(float2 p) {
  float n = islandGlassOrbHash(floor(p * 3.0));
  return half4(half3(n), half(1.0)) * half(0.03);
}

struct IslandGlassOrbRelightShader {
float4 uBand;
float4 uHue;
float uT;
float uAmp;
half3 toOut(half3 c) { return c; }

float sq(float x) { return x * x; }
half3 spectrum(float x) {
  float u = fract(x) * 5.0;
  float i = floor(u);
  float f = smoothstep(0.0, 1.0, fract(u));
  half3 a = i < 1.0 ? half3(1,.353,.122) : i < 2.0 ? half3(.961,.804,.588) : i < 3.0 ? half3(.392,.957,.976) : i < 4.0 ? half3(.169,.357,1) : half3(1,.176,.541);
  half3 b = i < 1.0 ? half3(.961,.804,.588) : i < 2.0 ? half3(.392,.957,.976) : i < 3.0 ? half3(.169,.357,1) : i < 4.0 ? half3(1,.176,.541) : half3(1,.353,.122);
  return mix(a, b, f);
}
float band(float y, float yc) { return 1.0 - smoothstep(30.0, 70.0, abs(y - yc)); } // 140 pt tall
half4 render(float2 p, texture2d<half> ringMap, texture2d<half> specular, texture2d<half> floorMap) {
  float ring = ringMap.sample(islandGlassOrbSampler, (p) / float2(402.0,874.0)).r;
  float spec = specular.sample(islandGlassOrbSampler, (p) / float2(402.0,874.0)).r;
  float flo = floorMap.sample(islandGlassOrbSampler, (p) / float2(402.0,874.0)).r;
  float bandP = band(p.y, uBand.x) * uBand.y;
  float floorP = band(p.y, uBand.z) * uBand.w;
  half3 hueB = toOut(spectrum(uHue.x));
  if (uHue.z < 0.5) {
    // the light follows the ridges: full on and beside a ridge, a quarter of it on the plain glass between them
    float ridgeNear = 0.0;
    if (bandP > 0.001) {
      float r1 = specular.sample(islandGlassOrbSampler, (p + float2(5.0, 0.0)) / float2(402.0,874.0)).r + specular.sample(islandGlassOrbSampler, (p - float2(5.0, 0.0)) / float2(402.0,874.0)).r + specular.sample(islandGlassOrbSampler, (p + float2(0.0, 5.0)) / float2(402.0,874.0)).r + specular.sample(islandGlassOrbSampler, (p - float2(0.0, 5.0)) / float2(402.0,874.0)).r;
      float r2 = specular.sample(islandGlassOrbSampler, (p + float2(11.0, 0.0)) / float2(402.0,874.0)).r + specular.sample(islandGlassOrbSampler, (p - float2(11.0, 0.0)) / float2(402.0,874.0)).r + specular.sample(islandGlassOrbSampler, (p + float2(0.0, 11.0)) / float2(402.0,874.0)).r + specular.sample(islandGlassOrbSampler, (p - float2(0.0, 11.0)) / float2(402.0,874.0)).r;
      ridgeNear = clamp(max(spec, max(0.35 * r1, 0.28 * r2)) * 1.8, 0.0, 1.0);
    }
    float aRing = 0.30 * ring * (0.25 + 0.75 * ridgeNear) * bandP;
    float aFloor = 0.30 * flo * floorP;
    half3 hueF = toOut(spectrum(uHue.y));
    float a = clamp(aRing + aFloor, 0.0, 0.7);
    return half4(hueB * aRing + hueF * aFloor, a);
  }
  float3 add = float3(hueB) * 0.9 * spec * bandP;
  // the floor's caustic shimmers: two warped sine fields (38 and 61 pt, 15.2 and 12.2 pt/s in opposite directions, whole
  // numbers of cycles per 10 s loop), #DFF5C8 at 0.10 × matte × (1 + 1.5 A); while the assistant thinks it breathes with the
  // spinner (one turn a second) and is brighter, so the pill's second and a half is never still
  float think = uHue.w;
  float2 w1 = p + 6.0 * float2(sin(TAU * (p.y / 53.0) + TAU * 0.3 * uT), cos(TAU * (p.x / 47.0) - TAU * 0.2 * uT));
  float s1 = sin(TAU * (dot(w1, float2(0.94, 0.34)) / 38.0 - 0.4 * uT));
  float s2 = sin(TAU * (dot(w1, float2(-0.30, 0.95)) / 61.0 + 0.2 * uT));
  float field = 0.55 + 0.225 * (s1 + s2);
  float gain = 1.0 + 1.5 * uAmp + think * (4.2 + 1.6 * sin(TAU * uT));
  add += float3(0.875, 0.961, 0.784) * 0.10 * flo * field * gain;
  // the ridges breathe too while it thinks
  add += float3(1.0) * 0.26 * think * spec * (0.5 + 0.5 * sin(TAU * uT - 1.2));
  // one 60 pt glint runs along the ridges at 90 pt/s every 3.33 s, white at 0.35 (0.8 while thinking)
  float g = fract(uT * 0.3);
  float sPos = 360.0 + 300.0 * g;
  float gl = (1.0 - smoothstep(10.0, 30.0, abs(dot(p, float2(0.54, 0.84)) - sPos))) * smoothstep(0.0, 0.08, g) * (1.0 - smoothstep(0.92, 1.0, g));
  add += float3(1.0) * (0.35 + 0.45 * think) * spec * gl;
  add = min(add, float3(1.0));
  return half4(half3(toOut(half3(add))), max(add.r, max(add.g, add.b)));
}
};
[[ stitchable ]] half4 islandGlassOrbRelight(float2 p, float4 uBand, float4 uHue, float uT, float uAmp, texture2d<half> ringMap, texture2d<half> specular, texture2d<half> floorMap) {
 IslandGlassOrbRelightShader shader = {uBand, uHue, uT, uAmp}; return shader.render(p,ringMap,specular,floorMap);
}
