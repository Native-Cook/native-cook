/*
 The night scene in one pass: a dusk sky with stars, drifting fog, a sea of layered sine waves,
 the lighthouse beam lighting the fog, and the onboarding text that exists only where the beam
 falls (plus a 0.8 s afterglow, from five earlier beam angles).

 The beam is a cone from the lamp. Its screen direction is (cos φ, E sin φ + TILT) (see
 lighthouseMotion.ts); a foreshortened beam (pointing at or away from the viewer) is shorter and
 wider. The text comes from `content`, an image of the three pages side by side, drawn once;
 `page` slides it. Fog and stars use the ambient clock on a circle, so they loop.

 Colours are sRGB; on iOS Skia treats the output as Display P3, so `toP3` converts when p3 = 1.
 */

export const LIGHTHOUSE_SKSL = `
uniform shader content;
uniform float2 size;
uniform float p3;
uniform float unit;
uniform float2 lamp;
uniform float horizon;
uniform float4 phiA;    // φ now, 0.1 s, 0.22 s, 0.36 s ago
uniform float2 phiB;    // φ 0.55 s and 0.8 s ago
uniform float2 clk;     // ambient clock, loop length
uniform float4 text;    // page offset (pt), content image top (pt), pixel ratio, unused

const float TAU = 6.2831853;
const float E = 0.3;
const float TILT = 0.12;
const float TAN_BETA = 0.56;

float h21(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }

float vnoise(float2 q) {
  float2 i = floor(q);
  float2 f = fract(q);
  f = f * f * (3.0 - 2.0 * f);
  float a = h21(i);
  float b = h21(i + float2(1.0, 0.0));
  float c = h21(i + float2(0.0, 1.0));
  float d = h21(i + float2(1.0, 1.0));
  return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(float2 q) {
  float v = 0.0;
  float a = 0.5;
  for (int i = 0; i < 4; i++) {
    v += a * vnoise(q);
    q = q * 2.03 + float2(17.1, 9.3);
    a *= 0.5;
  }
  return v;
}

float3 toP3(float3 c) {
  float3 l = pow(clamp(c, 0.0, 1.0), float3(2.2));
  float3 m = float3(
    dot(l, float3(0.8225, 0.1774, 0.0)),
    dot(l, float3(0.0332, 0.9669, 0.0)),
    dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(m, float3(1.0 / 2.2));
}

// The beam's light at p for azimuth phi: x = the cone (0..1, soft edge), y = with falloff and
// facing, for the glow in the fog.
float2 beamAt(float2 p, float phi) {
  float c = cos(phi);
  float s = sin(phi);
  float2 dir = float2(c, E * s + TILT);
  float lam = length(dir);
  dir /= lam;
  float2 d = p - lamp;
  float along = dot(d, dir);
  float across = abs(d.x * dir.y - d.y * dir.x);
  float spreadK = TAN_BETA / max(lam, 0.28);
  float halfW = 14.0 * unit + max(along, 0.0) * spreadK;
  float k = across / halfW;
  float cone = (1.0 - smoothstep(0.5, 1.08, k)) * smoothstep(-6.0 * unit, 10.0 * unit, along);
  float face = s > 0.0 ? 1.0 + 1.3 * pow(s, 6.0) : 1.0 + 0.5 * s;
  float reach = exp(-max(along, 0.0) / (560.0 * unit * max(lam, 0.32)));
  return float2(cone * reach * clamp(face, 0.5, 1.0), cone * reach * face);
}

float3 sky(float2 p) {
  float y = clamp((p.y) / horizon, 0.0, 1.0);
  float3 top = float3(0.039, 0.078, 0.188);
  float3 mid = float3(0.137, 0.192, 0.369);
  float3 low = float3(0.325, 0.251, 0.478);
  float3 col = y < 0.62 ? mix(top, mid, y / 0.62) : mix(mid, low, (y - 0.62) / 0.38);
  // A thin warm band where the sun went down.
  col += float3(0.30, 0.16, 0.20) * exp(-pow((horizon - p.y) / (18.0 * unit), 2.0)) * 0.45;
  return col;
}

half4 main(float2 p) {
  float u = unit;
  float w = TAU * clk.x / clk.y;
  float2 orbit = float2(cos(w), sin(w));
  float3 col;
  float fogBase;

  float2 now = beamAt(p, phiA.x);

  if (p.y < horizon) {
    col = sky(p);
    // Stars in the upper sky, twinkling at whole multiples of the loop.
    float2 cellId = floor(p / (26.0 * u));
    float hs = h21(cellId);
    if (hs > 0.78) {
      float2 sp = (cellId + 0.2 + 0.6 * float2(h21(cellId + 4.0), h21(cellId + 9.0))) * 26.0 * u;
      float2 ds = p - sp;
      float tw = 0.55 + 0.45 * sin(w * (2.0 + floor(hs * 5.0)) + hs * 40.0);
      float fadeLow = 1.0 - smoothstep(horizon * 0.35, horizon * 0.85, p.y);
      col += float3(0.85, 0.88, 1.0) * exp(-dot(ds, ds) / (2.0 * pow(0.7 * u, 2.0))) * tw * fadeLow * 0.9;
    }
    fogBase = 0.35 + 0.65 * smoothstep(horizon * 0.15, horizon, p.y);
  } else {
    // The sea: darker with depth, wave crests in layers, finer near the horizon.
    float depth = (p.y - horizon) / (size.y - horizon);
    col = mix(float3(0.165, 0.161, 0.329), float3(0.031, 0.055, 0.137), pow(depth, 0.6));
    float persp = 0.04 + depth;
    float crest = 0.0;
    for (int i = 0; i < 4; i++) {
      float fi = float(i);
      float freq = (0.045 + 0.02 * fi) / persp;
      float wave = sin(p.x * freq * 0.12 / unit + w * (2.0 + fi) + fi * 1.7) * 0.5
                 + sin(p.x * freq * 0.05 / unit - w * (1.0 + fi) + fi * 4.1) * 0.5;
      float rows = p.y / (persp * 22.0 * u) + wave * 0.6 + fi * 0.25;
      float breakUp = smoothstep(0.35, 0.75, vnoise(float2(p.x / (persp * 40.0 * u), rows * 0.5) + orbit * (1.0 + fi)));
      crest += pow(abs(sin(rows * 3.14159)), 14.0) * (0.30 - 0.05 * fi) * breakUp;
    }
    col += float3(0.42, 0.40, 0.66) * crest * (0.35 + 0.65 * (1.0 - depth));
    // The beam's reflection: glitter under where the beam lies over the water.
    float2 mirror = float2(p.x, 2.0 * horizon - p.y);
    float2 refl = beamAt(mirror, phiA.x);
    float glint = pow(vnoise(float2(p.x * 0.35 / u, p.y * 1.4 / u) + orbit * 3.0), 6.0);
    col += float3(1.0, 0.86, 0.58) * refl.y * (0.10 + glint * 2.2) * (1.0 - depth * 0.7) * crest * 2.0;
    col += float3(1.0, 0.86, 0.58) * refl.y * 0.05 * (1.0 - depth);
    // Foam where the rocks meet the sea.
    float2 rock = (p - float2(lamp.x, horizon + 24.0 * u)) / float2(70.0 * u, 9.0 * u);
    float ring = abs(length(rock) - 1.0);
    float foam = exp(-ring * 6.0) * smoothstep(0.35, 0.75, vnoise(p * 0.2 / u + orbit * 2.0));
    col += float3(0.55, 0.56, 0.75) * foam * 0.35;
    fogBase = 0.55 * (1.0 - smoothstep(0.0, 0.35, depth));
  }

  // Fog: looping noise, densest near the horizon.
  float2 q = p / (150.0 * u) + orbit * 0.7;
  float fog = fbm(q + float2(fbm(q * 0.7 + 3.1), 0.0) * 0.8);
  float density = clamp(fogBase * smoothstep(0.15, 0.95, fog) * 1.2, 0.0, 1.0);
  col = mix(col, float3(0.30, 0.31, 0.50), density * 0.30);

  // The beam in the fog.
  float3 warm = float3(1.0, 0.90, 0.68);
  float lit = now.y * (0.30 + 0.70 * density);
  col += warm * lit * 0.5;

  // The text: faint in the fog, lit by the beam and its afterglow.
  float2 tp = float2(p.x + text.x, p.y - text.y) * text.z;
  float ink = content.eval(tp).a;
  if (ink > 0.002) {
    float trail = beamAt(p, phiA.y).x * 0.9;
    trail = max(trail, beamAt(p, phiA.z).x * 0.66);
    trail = max(trail, beamAt(p, phiA.w).x * 0.46);
    trail = max(trail, beamAt(p, phiB.x).x * 0.26);
    trail = max(trail, beamAt(p, phiB.y).x * 0.10);
    float reveal = smoothstep(0.0, 0.55, max(now.x, trail));
    float3 inkCol = mix(float3(0.62, 0.64, 0.82), float3(1.0, 0.95, 0.84), reveal);
    col = mix(col, inkCol, ink * (0.035 + 0.965 * reveal));
  }

  // Vignette, grain, display.
  float2 vg = p / size - 0.5;
  col *= 1.0 - 0.6 * dot(vg * float2(1.0, 0.8), vg * float2(1.0, 0.8));
  col += (h21(floor(p * 1.7)) - 0.5) * 0.03;
  col = clamp(col, 0.0, 1.0);
  if (p3 > 0.5) col = toP3(col);
  return half4(half3(col), 1.0);
}
`;
