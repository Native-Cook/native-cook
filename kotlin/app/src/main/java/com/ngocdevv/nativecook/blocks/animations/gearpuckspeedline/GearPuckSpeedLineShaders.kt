package com.ngocdevv.nativecook.blocks.animations.gearpuckspeedline

/** Canonical scene and barrel-glass equations, with RN's Display P3 correction disabled on native sRGB. */
internal object GearPuckSpeedLineShaders {
    const val SCENE = """

uniform float uP3;
float lin(float c) { return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4); }
float enc(float c) { return c <= 0.0031308 ? c * 12.92 : 1.055 * pow(c, 1.0 / 2.4) - 0.055; }
half3 toOut(half3 s) {
  if (uP3 < 0.5) return s;
  float3 l = float3(lin(s.r), lin(s.g), lin(s.b));
  float3 p = float3(0.8225 * l.r + 0.1774 * l.g, 0.0332 * l.r + 0.9669 * l.g, 0.0171 * l.r + 0.0724 * l.g + 0.9108 * l.b);
  return half3(enc(p.r), enc(p.g), enc(p.b));
}
float hash1(float n) { return fract(sin(n * 127.1 + 11.7) * 43758.5453); }
float hash2(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }

uniform float uH;
uniform float uRest;
uniform float4 uHump;   // x0, amplitude (pt), sigma left, sigma right
uniform float4 uPluck;  // x, line displacement, hem displacement (lagging), 0
uniform float uPhase;   // cells and strands stream left by this (pt)
uniform float3 uC0;
uniform float3 uC1;
uniform float3 uC2;
uniform float4 uFront;  // previous front x, radius, last front x, radius
uniform float2 uPacket; // x, gain
uniform float4 uCurt;   // extra length, whip amplitude, time (s), sway (pt)
uniform float uTrail;   // the hem trails behind the stream (pt)
uniform float4 uBox;    // the readout the curtain parts around: x, y, w, h
uniform float uBright;  // where the line is brightest (x)
uniform float uArc[64]; // extra arc length at x = i * 402 / 63

const float PI2 = 6.2831853;

float humpH(float x) {
  float dx = x - uHump.x;
  float s = dx < 0.0 ? uHump.z : uHump.w;
  return exp(-dx * dx / (2.0 * s * s));
}
float pl(float x, float a) {
  float d = x - uPluck.x;
  return a * exp(-d * d / 5000.0);
}
float cy(float x) { return uRest - uHump.y * humpH(x) + pl(x, uPluck.y); }
float arcAt(float x) {
  float f = clamp(x, 0.0, 402.0) / 402.0 * 63.0;
  float acc = 0.0;
  for (int i = 0; i < 64; i++) {
    float w = max(0.0, 1.0 - abs(f - float(i)));
    acc += w * uArc[i];
  }
  return acc;
}
half3 bandAtW(float x, float w) {
  half3 c = half3(uC0);
  c = mix(c, half3(uC1), 1.0 - smoothstep(uFront.y - w, uFront.y + w, abs(x - uFront.x)));
  c = mix(c, half3(uC2), 1.0 - smoothstep(uFront.w - w, uFront.w + w, abs(x - uFront.z)));
  return c;
}
half3 bandAt(float x) { return bandAtW(x, 8.0); }

half4 main(float2 xy) {
  float x = xy.x;
  float y = xy.y;

  // ---- the page: blue-black, lifted where the light is ----
  float lift = exp(-((x - uBright) * (x - uBright) + (y - uRest) * (y - uRest) * 1.6) / (2.0 * 230.0 * 230.0));
  half3 col = mix(half3(0.027, 0.039, 0.063), half3(0.047, 0.066, 0.098), lift);

  // ---- distance to the curve: foot-point projection ----
  float xp = x;
  for (int i = 0; i < 3; i++) {
    float yp = cy(xp);
    float sl = (cy(xp + 0.5) - cy(xp - 0.5));
    xp = xp + ((x - xp) + (y - yp) * sl) / (1.0 + sl * sl);
  }
  float d = length(float2(x - xp, y - cy(xp)));

  half3 band = bandAt(xp);
  float hh = humpH(xp);
  float env = 0.3 + 0.7 * exp(-(xp - uBright) * (xp - uBright) / (2.0 * 150.0 * 150.0));
  env *= mix(0.3, 1.0, smoothstep(-10.0, 80.0, xp) * smoothstep(412.0, 322.0, xp));
  float pk = uPacket.y * exp(-(xp - uPacket.x) * (xp - uPacket.x) / (2.0 * 14.0 * 14.0));
  env *= 1.0 + 0.6 * pk;
  // away from the line the foot point jumps across the hump's ridge (above the crest both flanks are equally near),
  // so the glow's colour and strength are taken from the pixel's own column there, and the fronts soften with
  // distance: no seams in the light on the page
  float xe = mix(xp, x, smoothstep(3.0, 26.0, d));
  half3 bandF = bandAtW(xe, 8.0 + 0.9 * d);
  float envF = 0.3 + 0.7 * exp(-(xe - uBright) * (xe - uBright) / (2.0 * 150.0 * 150.0));
  envF *= mix(0.3, 1.0, smoothstep(-10.0, 80.0, xe) * smoothstep(412.0, 322.0, xe));
  envF *= 1.0 + 0.6 * uPacket.y * exp(-(xe - uPacket.x) * (xe - uPacket.x) / (2.0 * 18.0 * 18.0));

  float cellG = 1.0;
  if (d < 14.0) {
    float s = xp + arcAt(xp) + uPhase;
    float cf = fract(s / 8.0);
    float on = smoothstep(0.0, 0.07, cf) * (1.0 - smoothstep(0.7, 0.77, cf));
    cellG = mix(0.36, 1.0, on);
  }
  float white = clamp(0.6 + 0.4 * hh * clamp(uHump.y / 110.0, 0.0, 1.0) + 0.5 * pk, 0.0, 1.0);
  float core = 1.0 - smoothstep(1.25, 2.25, d);
  half3 light = half3(0.0);
  light += core * cellG * mix(band, half3(1.0), white) * env;
  light += exp(-(d / 3.4) * (d / 3.4)) * cellG * band * 0.8 * env;
  light += 0.45 * 0.5 / (1.0 + (d / 11.0) * (d / 11.0)) * bandF * envF;
  light += exp(-d / 42.0) * 0.14 * bandF * envF;
  light += exp(-d / 170.0) * 0.085 * bandF * (0.45 + 0.55 * envF);

  // ---- the curtain: strands hang from the line (from the chord under the hump), longest under the hump ----
  float topLine = max(cy(x), uRest + pl(x, uPluck.y)) + 3.0;
  float inBox = smoothstep(uBox.x - 8.0, uBox.x, x) * (1.0 - smoothstep(uBox.x + uBox.z, uBox.x + uBox.z + 8.0, x));
  float boxBottom = uBox.y + uBox.w + 6.0;
  float top = mix(topLine, max(topLine, boxBottom), inBox * step(uBox.y, topLine + 40.0));
  float env2 = exp(-(x - uHump.x) * (x - uHump.x) / (2.0 * 110.0 * 110.0));
  float lNom = 40.0 + uCurt.x * env2;
  float hemBase = uRest + 3.0 + pl(x, uPluck.z);
  float depth0 = clamp((y - top) / max(hemBase + lNom - top, 1.0), 0.0, 1.0);
  float shift = (uCurt.w + uTrail) * pow(depth0, 1.5);
  float u = x - shift + uPhase;
  float id = floor(u / 6.0);
  float lc = u - id * 6.0 - 3.0;
  float n = hash1(id - 168.0 * floor(id / 168.0));
  float ls = lNom * (1.0 + 0.18 * (2.0 * n - 1.0)) + uCurt.y * sin(PI2 * 12.0 * uCurt.z - id * 0.9) * env2;
  float hem = hemBase + ls;
  if (y > top - 1.0 && y < hem + 1.0 && hem > top) {
    float dep = clamp((y - top) / (hem - top), 0.0, 1.0);
    float a = 0.5 * pow(1.0 - dep, 0.85) * smoothstep(top - 0.5, top + 0.8, y);
    float strand = 1.0 - smoothstep(0.6, 1.25, abs(lc));
    float glow = 0.16 * exp(-abs(lc) / 1.3);
    half3 sc = bandAt(x) * (0.55 + 0.45 * env) * mix(1.0, 1.25, env2);
    light += sc * a * (strand + glow);
  }
  // the wall behind the curtain catches its light
  float midY = uRest + 6.0 + lNom * 0.45;
  float wash = exp(-(y - midY) * (y - midY) / (2.0 * (lNom * 0.5 + 18.0) * (lNom * 0.5 + 18.0)));
  light += bandAtW(x, 30.0) * 0.05 * wash * (0.35 + 0.65 * env2) * step(uRest - 4.0, y);

  col += light;

  // ---- grain and dither ----
  float2 px = floor(xy * 3.0);
  float gr = (hash2(px) - 0.5) * 2.0 * 0.025;
  col *= 1.0 + gr;
  float ign = fract(52.9829189 * fract(0.06711056 * px.x + 0.00583715 * px.y));
  col += (ign - 0.5) * (3.0 / 255.0);
  return half4(toOut(clamp(col, 0.0, 1.0)), 1.0);
}

"""
    const val LENS = """

uniform float uP3;
float lin(float c) { return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4); }
float enc(float c) { return c <= 0.0031308 ? c * 12.92 : 1.055 * pow(c, 1.0 / 2.4) - 0.055; }
half3 toOut(half3 s) {
  if (uP3 < 0.5) return s;
  float3 l = float3(lin(s.r), lin(s.g), lin(s.b));
  float3 p = float3(0.8225 * l.r + 0.1774 * l.g, 0.0332 * l.r + 0.9669 * l.g, 0.0171 * l.r + 0.0724 * l.g + 0.9108 * l.b);
  return half3(enc(p.r), enc(p.g), enc(p.b));
}
float hash1(float n) { return fract(sin(n * 127.1 + 11.7) * 43758.5453); }
float hash2(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }

uniform shader letters;
uniform float2 uC;
uniform float uR;
uniform float uRing;   // ring scale (1.4 on a landing)
uniform float uFlash;
uniform float3 uLine;  // the line's colour where the ball is
uniform float uRoll;   // radians
uniform float uPress;

const float PI = 3.14159265;

half4 main(float2 xy) {
  float2 q = xy - uC;
  float r = length(q);
  float R = uR * (1.0 - 0.04 * uPress);
  float rho = r / R;
  half3 lineC = half3(uLine);

  // ---- outside: the ring, a soft occlusion where the ball sits in the capsule, contact light ----
  float ringR = 52.0 * uRing;
  float dr = abs(r - ringR);
  float ringA = ((1.0 - smoothstep(0.9, 1.5, dr)) * 0.85 + 0.4 * exp(-dr * dr / 50.0)) * (0.7 + 0.6 * uFlash);
  half3 glow = lineC * ringA;
  float occ = 0.5 * (1.0 - smoothstep(R, R + 8.0, r)) * step(R, r);
  float2 cq = (q - float2(0.0, R * 0.78)) / float2(R * 1.2, R * 0.35);
  glow += lineC * 0.2 * exp(-dot(cq, cq)) * step(R, r);
  half4 outside = half4(glow, clamp(max(max(glow.r, glow.g), glow.b), 0.0, 1.0));
  outside = half4(outside.rgb * (1.0 - occ), outside.a + occ * (1.0 - outside.a));

  if (rho > 1.04) return half4(toOut(outside.rgb), outside.a);

  // ---- the glass ----
  float rr = min(rho, 1.0);
  float3 n = float3(q / R, sqrt(max(1.0 - rr * rr, 0.0)));
  float3 L = normalize(float3(-0.45, -0.55, 0.7));
  float2 qb = q / R - float2(0.0, 0.32);
  half3 body = mix(half3(0.227, 0.024, 0.055), half3(0.071, 0.012, 0.039), smoothstep(0.15, 1.05, length(qb)));
  float lam = max(dot(n, L), 0.0);
  body += half3(0.639, 0.078, 0.169) * 0.6 * pow(lam, 3.0);

  // the letter under the ball, through the lens
  float sc = (1.0 / 2.2) * (1.0 + 1.8 * rr * rr);
  float2 src = uC + q * sc;
  float la = letters.eval(src).a;
  float dim = 1.0 - 0.45 * smoothstep(0.7, 1.0, rr);
  body = mix(body, half3(1.0, 0.914, 0.925), la * 0.94 * dim);

  // caustic crescent: light leaving the glass on the far side, rolling with the ball
  float ang = atan(q.y, q.x);
  float da = ang - (PI * 0.25 + uRoll);
  da = da - 2.0 * PI * floor((da + PI) / (2.0 * PI));
  float cres = exp(-da * da / (2.0 * 0.5 * 0.5)) * smoothstep(0.5, 0.78, rr) * (1.0 - smoothstep(0.88, 0.99, rr));
  body += mix(half3(0.761, 0.106, 0.212), lineC, 0.2) * 0.6 * cres;

  // the line's light caught on the lower rim
  body += lineC * 0.22 * smoothstep(0.82, 1.0, rr) * max(n.y, 0.0);

  // specular: fixed in the screen (the key light does not roll with the ball)
  float2 sp = (q - float2(-15.0, -19.0)) / float2(mix(10.5, 8.2, uPress), mix(6.8, 5.3, uPress));
  float spec = exp(-dot(sp, sp) * 1.3) * mix(0.55, 0.72, uPress);
  body = mix(body, half3(1.0), spec);

  // Fresnel rim, stronger towards the light
  float fres = smoothstep(0.86, 1.0, rr) * (0.14 + 0.18 * max(dot(normalize(q + 0.001), normalize(float2(-0.45, -0.55))), 0.0));
  body = mix(body, half3(1.0), fres);

  float inside = 1.0 - smoothstep(R - 0.7, R + 0.7, r);
  half3 c = mix(outside.rgb, body, inside);
  float a = mix(outside.a, 1.0, inside);
  return half4(toOut(c), a);
}

"""
}
