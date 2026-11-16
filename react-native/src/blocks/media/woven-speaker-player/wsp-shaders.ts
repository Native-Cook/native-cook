// Woven Speaker Player — the dome, one runtime effect drawn into the dome's square (346 pt at S = 1).
//
// The shot's weave is two families of strands wound round the dome in opposite senses. Measured on the shot
// (`.work/woven-speaker-player/pin-measure.md`): 128 strands per family round the dome at every radius (FFT peak 128, harmonics
// 256, 384), the strand angle to the radial direction grows from 25° (r/R .69) through 45° (.88) to 70° (.97), which is a
// constant winding rate on a sphere seen from above; the brightness of the strands falls from 70/255 at the display's edge
// to 37/255 near the rim, where a bright rim line catches the light.
//
// Weave. Seen from above, a point at distance r from the centre sits on a hemisphere at the polar angle φ = asin(r / R). A
// strand of the first family keeps  θ − ψA − Θ(φ)  constant, one of the second family  θ − ψB + Θ(φ)  (θ the polar angle, ψA
// and ψB the angles the two layers have been turned by, Θ(φ) = 0.425 φ + 0.0393 φ³ the winding fitted to the shot). With
// k = N / 2π strands per radian the strand coordinates are  uA = k (θ − ψA − ΘA),  uB = k (θ − ψB + ΘB)  and a strand lies at
// every integer. Shear: ΘA = Θ (1 + e σ cos(θ − θf)), ΘB = Θ (1 − e σ cos(θ − θf)): a held shear stretches the lozenges on the
// side of the finger and tightens them on the other.
// The gradient of u is analytic (not fwidth): ∂u/∂r = ∓ k Θ'(1 ± e σ c) / (R cos φ), ∂u/∂θ / r = k (1 + Θ e σ sin(θ − θf)) / r, so
// all three shader languages compute the same strand width in points.
// A strand is a box of half width hw (0.45 pt) filtered by one pixel and by the motion blur of the layer's turn; when the
// pitch drops under two pixels the contrast is blended into the mean coverage, so the weave never aliases (moire).
// Over / under: at a crossing of strand iA and strand iB the one with (iA + iB) even is on top (15 % brighter, continuous),
// the other is dimmed in a 1 pt shadow either side of it: that is what makes it cloth and not a wire grid.
//
// Cloth: `uRings` (radius, height) ×4 are travelling pulses h(r) = a exp(−((r − r0) / 16)²) that push the weave out
// (parallax 0.55 h) and shade it (slope against the key light from the upper left); `uDent` is the finger's dent
// (Gaussian σ 26 pt) that pulls the strands towards it. Fibre: each strand ±12 % in brightness by its index and along its
// length, 1 in 24 duller, width ±0.15 pt; 3–5 strands near the finger's last place stay out of line (`uFibre`).
// Light: one soft key from the upper left, one red lamp at the arc head's bearing on the display's edge (squared fall-off,
// reach `uLamp.y`, doubled on the finger's side while turning), 3 % grain in the black; a bright ring at 70 % of the radius
// slides inward with the shear.
//
// Uniforms (design pt = screen pt / uS; angles in radians, screen axes: x right, y down):
//   uC      float2  dome centre, screen pt            uS      float   screen pt per design pt
//   uPx     float   device px per screen pt           uAng    float4  ψA, ψB, blurA, blurB (rad of turn per frame)
//   uShear  float4  σ −1…1, finger bearing θf, layer brightness split 0…1, finger-side reach 0…1
//   uRingA  float4  r0, h0, r1, h1 (pt)              uRingB  float4  r2, h2, r3, h3
//   uDent   float4  x, y (design pt from the centre), depth (pt), 0
//   uLamp   float4  head bearing, reach (pt), strength 0…1, 0
//   uAccent float3  light colour (sRGB 0…1)
//   uFibre  float4  amount 0…1, out-of-line bearing, out-of-line pt, grain 0…1
//   uP3     float   1 = convert to Display P3 (iOS)           uN      float   strands per layer round the dome (even; the shot: 128)
// No MSL / C++ names as identifiers (see COMMON.md).

export const WEAVE_SKSL = `
uniform float2 uC;
uniform float uS;
uniform float uPx;
uniform float4 uAng;
uniform float4 uShear;
uniform float4 uRingA;
uniform float4 uRingB;
uniform float4 uDent;
uniform float4 uLamp;
uniform float3 uAccent;
uniform float4 uFibre;
uniform float uP3;
uniform float uN;

const float TAU = 6.2831853;
const float DOME_R = 165.0;
const float DISPLAY_R = 105.0;

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

half4 main(float2 xy) {
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
  float th = atan(q.y, q.x);
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
  float ia = mod(iA, uN);
  float ib = mod(iB, uN) + 211.0;
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
  float aOver = 1.0 - step(0.5, mod(iA + iB, 2.0)); // 1 when A is on top at this crossing

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
`;
