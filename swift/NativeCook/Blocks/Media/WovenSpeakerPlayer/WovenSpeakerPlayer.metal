#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Direct SkSL translation, followed by the accepted RN Display-P3 host adapter.
namespace WovenSpeakerPlayerShader {
constant float TAU = 6.2831853;
constant float DOME_R = 165.0;
constant float DISPLAY_R = 105.0;
inline float wspMod(float x, float y) { return x-y*floor(x/y); }
struct Scene {

float2 uC;
float uS;
float uPx;
float4 uAng;
float4 uShear;
float4 uRingA;
float4 uRingB;
float4 uDent;
float4 uLamp;
float3 uAccent;
float4 uFibre;
float uP3;
float uN;





float sq(float x) { return x * x; }
float hash1(float n) { return fract(sin(n * 12.9898 + 4.1414) * 43758.5453); }
float hash2(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }

// value noise along a strand, smooth, 1-D
float vnoise(float x, float seed) {
  float i = floor(x);
  float f = fract(x);
  f = f * f * (3.0 - 2.0 * f);
  return mix(hash1(i + seed), hash1(i + 1.0 + seed), f);
}

// the shot's winding, fitted: Theta(phi) and its derivative
float windOf(float phi) { return phi * (0.425 + 0.0393 * phi * phi); }
float windD(float phi) { return 0.425 + 0.1179 * phi * phi; }

// the strand's brightness over the dome (peak of the over strands, 0 … 1), measured: 70/255 at the display, 37/255 at 91 %
float radialLevel(float x) {
  float peak = 0.222 - 0.08 * pow(smoothstep(0.72, 0.93, x), 0.9) + 0.045 * (1.0 - smoothstep(0.66, 0.72, x));
  float lip = 0.82 + 0.18 * smoothstep(0.636, 0.675, x);
  return peak * lip;
}

// one strand: box of half width hu (in u) filtered by fu (in u); above a pitch of 2 px it blends into the mean coverage
float strandCover(float u, float hu, float fu) {
  float d = abs(fract(u + 0.5) - 0.5);
  float f = max(fu, 0.0001);
  float c = clamp((hu + 0.5 * f - d) / f, 0.0, 1.0) * min(1.0, 2.0 * hu / f);
  float mean = min(2.0 * hu, 1.0);
  return mix(c, mean, smoothstep(0.34, 0.72, f));
}

float2 ringPulse(float r, float rad, float amp) {
  float d = (r - rad) / 16.0;
  float g = exp(-d * d);
  return float2(amp * g, amp * g * (-2.0 * d / 16.0));
}

float3 toP3(float3 c) {
  float3 lin = pow(c, float3(2.2));
  float3 p3 = float3(0.8225 * lin.r + 0.1774 * lin.g, 0.0332 * lin.r + 0.9669 * lin.g, 0.0171 * lin.r + 0.0724 * lin.g + 0.9108 * lin.b);
  return pow(p3, float3(1.0 / 2.2));
}

half4 render(float2 xy) {
  float2 p = (xy - uC) / uS;
  float r0 = length(p);
  float pxPt = 1.0 / (uPx * uS);
  if (r0 > DOME_R + 2.0) return half4(0.0);

  float2 dirOut = p / max(r0, 0.001);

  // ---- cloth: rings push it out, the finger's dent pulls it in
  float2 h1 = ringPulse(r0, uRingA.x, uRingA.y);
  float2 h2 = ringPulse(r0, uRingA.z, uRingA.w);
  float2 h3 = ringPulse(r0, uRingB.x, uRingB.y);
  float2 h4 = ringPulse(r0, uRingB.z, uRingB.w);
  float hh = h1.x + h2.x + h3.x + h4.x;
  float dh = h1.y + h2.y + h3.y + h4.y;
  float2 dd = p - uDent.xy;
  float dl = length(dd);
  float dentG = exp(-sq(dl / 26.0));
  float2 q = p + dirOut * (0.55 * hh) - dd * (0.026 * uDent.z * dentG);
  float r = length(q);
  float th = atan2(q.y, q.x);
  float x = clamp(r / DOME_R, 0.0, 0.9995);
  float phi = asin(x);
  float cph = max(sqrt(1.0 - x * x), 0.045);

  // ---- weave coordinates
  float k = uN / TAU;
  float sig = uShear.x;
  float cf = cos(th - uShear.y);
  float sf = sin(th - uShear.y);
  float wnd = windOf(phi);
  float wdr = windD(phi);
  float eS = 0.5 * sig;
  float thA = wnd * (1.0 + eS * cf);
  float thB = wnd * (1.0 - eS * cf);
  float uA = k * (th - uAng.x - thA);
  float uB = k * (th - uAng.y + thB);

  // out-of-line strands near the finger's last place (a few strands, pulled by pt)
  float gPull = exp(-sq((th - uFibre.y) * k / 2.2));
  float pull = uFibre.z * gPull;

  // analytic gradients, 1 / design pt
  float kt = k * (1.0 + wnd * eS * sf) / max(r, 1.0);
  float gAr = k * wdr * (1.0 + eS * cf) / (DOME_R * cph);
  float gBr = k * wdr * (1.0 - eS * cf) / (DOME_R * cph);
  float gA = sqrt(gAr * gAr + kt * kt);
  float gB = sqrt(gBr * gBr + kt * kt);
  uA += pull * gA;
  uB -= pull * gB;

  // ---- fibre: per strand and along it
  float iA = floor(uA + 0.5);
  float iB = floor(uB + 0.5);
  float ia = wspMod(iA, uN);
  float ib = wspMod(iB, uN) + 211.0;
  float fa = (hash1(ia * 1.7) * 2.0 - 1.0) * 0.12 + (vnoise(uB * 0.6, ia * 3.1) * 2.0 - 1.0) * 0.07;
  float fb = (hash1(ib * 1.7) * 2.0 - 1.0) * 0.12 + (vnoise(uA * 0.6, ib * 3.1) * 2.0 - 1.0) * 0.07;
  float dullA = step(hash1(ia * 5.3 + 7.0), 0.0417) * 0.2;
  float dullB = step(hash1(ib * 5.3 + 7.0), 0.0417) * 0.2;
  float amt = uFibre.x;
  float levA = 1.0 + amt * (fa - dullA);
  float levB = 1.0 + amt * (fb - dullB);
  float widA = 1.0 + amt * 0.33 * (hash1(ia * 2.9 + 1.0) * 2.0 - 1.0);
  float widB = 1.0 + amt * 0.33 * (hash1(ib * 2.9 + 1.0) * 2.0 - 1.0);

  // ---- coverage of every strand (pt → u), with the motion blur of a turning layer
  float hw = 0.5;
  float fpx = pxPt * 1.2;
  float blurA = uAng.z * k;
  float blurB = uAng.w * k;
  float fuA = max(fpx * gA, blurA);
  float fuB = max(fpx * gB, blurB);
  float hA = hw * widA * gA;
  float hB = hw * widB * gB;
  float cA = strandCover(uA, hA, fuA);
  float cB = strandCover(uB, hB, fuB);
  float cAw = strandCover(uA, hA * 2.0, fuA);
  float cBw = strandCover(uB, hB * 2.0, fuB);
  float aOver = 1.0 - step(0.5, wspMod(iA + iB, 2.0)); // 1 when A is on top at this crossing

  // ---- light: radial level, anisotropy, key from the upper left, rings
  float shiftIn = 14.0 * abs(sig) / DOME_R;
  float lvl = radialLevel(x - shiftIn * 0.55 * smoothstep(0.55, 0.75, x));
  float ang = 1.0 + 0.12 * cos(2.0 * th) - 0.05 * sin(th);
  float2 keyDir = normalize(float2(-0.62, -0.78));
  float slope = -dh * dot(dirOut, keyDir);
  float ringShade = 1.0 + 1.9 * slope + 0.5 * hh * 0.1;
  float dentShade = 1.0 - 0.25 * dentG * clamp(uDent.z / 5.0, 0.0, 1.0);
  float sep = uShear.z * 0.18;
  float lightA = lvl * ang * ringShade * dentShade * (1.0 + sep);
  float lightB = lvl * ang * ringShade * dentShade * (1.0 - sep);

  // red lamp: on the display's edge at the arc head's bearing
  float2 headP = float2(cos(uLamp.x), sin(uLamp.x)) * DISPLAY_R;
  float fingerSide = 0.5 + 0.5 * cos(th - uShear.y);
  float reach = uLamp.y * (1.0 + uShear.w * fingerSide);
  float dHead = length(p - headP);
  float lamp = uLamp.z * exp(-sq(dHead / max(reach, 1.0)));
  // the lamp is also a faint ring round the whole display
  float lampRing = uLamp.z * exp(-sq((r0 - DISPLAY_R) / 14.0)) * 0.32;
  float redIn = clamp(lamp + lampRing, 0.0, 1.0);

  float3 tint = float3(0.96, 0.975, 1.0);
  float3 hole = float3(0.02, 0.02, 0.026) + float3(0.025, 0.025, 0.029) * smoothstep(0.82, 1.0, x);
  hole += uAccent * 0.045 * redIn;

  float3 colA = tint * lightA * levA * mix(0.9, 1.08, aOver);
  float3 colB = tint * lightB * levB * mix(1.08, 0.9, aOver);
  colA = mix(colA, uAccent * (lightA * 2.2 + 0.12), 0.62 * redIn);
  colB = mix(colB, uAccent * (lightB * 2.2 + 0.12), 0.62 * redIn);
  // the under strand sits in the over strand's shadow
  float shadowOnA = (1.0 - aOver) * cBw;
  float shadowOnB = aOver * cAw;
  colA *= 1.0 - 0.3 * shadowOnA;
  colB *= 1.0 - 0.3 * shadowOnB;

  float3 col = hole;
  float cUnder = mix(cB, cA, 1.0 - aOver);
  float3 colUnder = mix(colB, colA, 1.0 - aOver);
  float cOver = mix(cA, cB, 1.0 - aOver);
  float3 colOver = mix(colA, colB, 1.0 - aOver);
  col = mix(col, colUnder, cUnder);
  col = mix(col, colOver, cOver);

  // the dome's rim: a thin bright line, lit from the upper left
  float lit = 0.5 + 0.5 * dot(dirOut, keyDir);
  float rim = exp(-sq((DOME_R - r0 - 0.7) / 2.1)) * (0.06 + 0.1 * lit);
  col += float3(0.93, 0.95, 1.0) * rim;

  // grain in the black
  col += (hash2(floor(xy * uPx)) - 0.5) * (3.0 / 255.0) * uFibre.w;

  float cov = 1.0 - smoothstep(-0.5 * pxPt, 0.7 * pxPt, r0 - DOME_R);
  col = clamp(col, 0.0, 1.0);
  col = mix(col, toP3(col), uP3);
  return half4(half3(col * cov), half(cov));
}

};
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


} // namespace WovenSpeakerPlayerShader

[[ stitchable ]] half4 wovenSpeakerPlayer(float2 position, half4 source,
                                         float2 center, float scale, float pixels,
                                         device const float *u, int count) {
    if (count < 25) return half4(0.0h);
    WovenSpeakerPlayerShader::Scene s;
    s.uC = center; s.uS = scale; s.uPx = pixels;
    s.uAng = float4(u[0],u[1],u[2],u[3]);
    s.uShear = float4(u[4],u[5],u[6],u[7]);
    s.uRingA = float4(u[8],u[9],u[10],u[11]);
    s.uRingB = float4(u[12],u[13],u[14],u[15]);
    s.uDent = float4(u[16],u[17],u[18],0.0);
    s.uLamp = float4(u[19],u[20],u[21],0.0);
    s.uAccent = float3(1.0,59.0/255.0,48.0/255.0);
    s.uFibre = float4(0.5,u[22],u[23],1.0);
    s.uP3 = 1.0; s.uN = 128.0;
    // The page and radial wash share this pass so the dome has no rectangular layer boundary.
    float wash = u[24] * clamp(1.0-length(position-center)/(300.0*scale),0.0,1.0);
    float3 page = mix(float3(19.0/255.0),s.uAccent,wash);
    half4 dome = WovenSpeakerPlayerShader::displaySRGB(s.render(position));
    return half4(half3(float3(dome.rgb)+page*(1.0-float(dome.a))),1.0h);
}
