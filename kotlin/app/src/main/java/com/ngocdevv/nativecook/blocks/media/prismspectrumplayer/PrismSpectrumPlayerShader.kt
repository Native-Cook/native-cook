package com.ngocdevv.nativecook.blocks.media.prismspectrumplayer

// The whole hero in one full-screen pass (API 33+): a night glasshouse backdrop, a white beam
// rising from the lower left into a glass prism, and the rainbow fan that leaves it. The fan is
// cut into twelve spectral bands; each band is an equaliser bar whose length is the band's level,
// computed from the clock (a sum of sines with whole-number frequencies of the loop, plus a
// 120 BPM kick), so the bars loop exactly. Light is added on top of the backdrop and soft-clipped
// (1 − e^−x). The React Native SkSL unchanged but for the Display P3 conversion, which Android
// does not need. Coordinates are dp: the canvas is scaled by the density before it is drawn.

internal const val PRISM_AGSL = """
uniform float2 size;
uniform float unit;
uniform float4 prism;   // centroid x, y, circumradius, rotation
uniform float4 ports;   // entry x, y, exit x, y
uniform float4 fan;     // direction, spread, beam, play
uniform float2 clk;     // ambient clock, loop length
uniform float2 lens;    // shortest bar, longest bar

const float TAU = 6.2831853;
const float BANDS = 12.0;

float h11(float x) { return fract(sin(x * 78.233) * 43758.5453); }
float h21(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }

// Red (x = 0) to violet (x = 1), a little softened so neighbouring bands blend.
float3 spectral(float x) {
  float hue = x * 0.79;
  float3 c = clamp(abs(fract(hue + float3(0.0, 0.6666667, 0.3333333)) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
  c = c * c * (3.0 - 2.0 * c);
  return mix(c, float3(1.0), 0.14);
}

// A band's level 0..1 at clock t: three sines at whole multiples of the loop, and a kick that is
// stronger in the low (red) bands.
float level(float i, float t) {
  float a = h11(i * 7.13 + 1.0);
  float b = h11(i * 3.71 + 2.0);
  float c = h11(i * 5.31 + 3.0);
  float w = TAU * t / clk.y;
  float k1 = 2.0 + floor(a * 3.0);
  float k2 = 7.0 + floor(b * 6.0);
  float k3 = 19.0 + floor(c * 9.0);
  float kick = pow(0.5 + 0.5 * cos(w * 16.0), 6.0);
  float low = 1.0 - i / (BANDS - 1.0);
  float v = 0.40 + 0.20 * sin(w * k1 + b * TAU) + 0.14 * sin(w * k2 + c * TAU)
          + 0.09 * sin(w * k3 + a * TAU) + kick * (0.10 + 0.30 * low) - 0.08 * (1.0 - low);
  float idle = 0.13 + 0.10 * sin(3.14159 * (i + 0.5) / BANDS) + 0.05 * a;
  return mix(idle, clamp(v, 0.0, 1.0), fan.w);
}

// Equilateral triangle, centred on its centroid, half side r, apex towards +y.
float triSDF(float2 q, float r) {
  const float k = 1.7320508;
  q.x = abs(q.x) - r;
  q.y = q.y + r / k;
  if (q.x + k * q.y > 0.0) q = float2(q.x - k * q.y, -k * q.x - q.y) / 2.0;
  q.x -= clamp(q.x, -2.0 * r, 0.0);
  return -length(q) * sign(q.y);
}

float segDist(float2 p, float2 a, float2 b) {
  float2 pa = p - a;
  float2 ba = b - a;
  float hh = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
  return length(pa - ba * hh);
}

// The room: a deep indigo glasshouse at night, its arched window frame faintly lit by the beam.
float3 backdrop(float2 p) {
  float2 uv = p / size;
  float3 top = float3(0.050, 0.058, 0.122);
  float3 mid = float3(0.090, 0.090, 0.190);
  float3 low = float3(0.118, 0.078, 0.160);
  float3 col = uv.y < 0.5 ? mix(top, mid, uv.y * 2.0) : mix(mid, low, uv.y * 2.0 - 1.0);
  float2 dc = p - prism.xy;
  float glow = exp(-dot(dc, dc) / (2.0 * pow(170.0 * unit, 2.0)));
  col += float3(0.09, 0.10, 0.20) * glow * (0.35 + 0.65 * fan.z);
  // The window: an arch over the hero and its mullions.
  float archR = size.x * 0.62;
  float2 archC = float2(size.x * 0.5, size.y * 0.30);
  float ring = abs(length(p - archC) - archR);
  float frame = exp(-ring / (0.9 * unit)) * step(p.y, archC.y + 2.0);
  float lane = abs(fract(p.x / (size.x / 6.0) + 0.5) - 0.5) * (size.x / 6.0);
  float inside = step(length(p - archC), archR) + step(archC.y, p.y);
  frame += exp(-lane / (0.8 * unit)) * clamp(inside, 0.0, 1.0) * step(p.y, size.y * 0.56);
  float rail = abs(p.y - size.y * 0.56);
  frame += exp(-rail / (0.9 * unit));
  col += float3(0.32, 0.34, 0.52) * frame * (0.030 + 0.050 * glow * fan.z);
  return col;
}

half4 main(float2 p) {
  float u = unit;
  float beam = fan.z;
  float t = clk.x;
  float w = TAU * t / clk.y;
  float3 col = backdrop(p);
  float3 light = float3(0.0);

  float2 inP = ports.xy;
  float2 outP = ports.zw;

  // Prism-local coordinates (apex up on screen).
  float cr = cos(prism.w);
  float sr = sin(prism.w);
  float2 dp = p - prism.xy;
  float2 loc = float2(cr * dp.x + sr * dp.y, -sr * dp.x + cr * dp.y);
  float halfSide = prism.z * 0.8660254;
  float sd = triSDF(float2(loc.x, -loc.y), halfSide);
  float inside = 1.0 - smoothstep(-0.75, 0.75, sd);

  // The incoming beam: from the lower left, aimed at the entry point, stopping at the glass.
  float2 inDir = normalize(float2(1.0, -0.42));
  float2 rel = p - inP;
  float along = -dot(rel, inDir);
  float across = abs(rel.x * inDir.y - rel.y * inDir.x);
  if (along > 0.0) {
    float core = exp(-across * across / (2.0 * pow(2.2 * u, 2.0)));
    float halo = exp(-across * across / (2.0 * pow(11.0 * u, 2.0)));
    float fade = 0.75 + 0.25 * exp(-along / (260.0 * u));
    light += float3(1.0, 0.97, 0.92) * (core * 1.1 + halo * 0.22) * beam * fade * (1.0 - inside);
  }

  // The fan: twelve bars from the exit point.
  float2 d = p - outP;
  float rr = length(d);
  float ang = atan(d.y, d.x) - (fan.x - fan.y * 0.5);
  float fpos = ang / fan.y * BANDS;
  if (fpos > -1.0 && fpos < BANDS + 1.0 && rr > 0.5) {
    float bi = clamp(floor(fpos), 0.0, BANDS - 1.0);
    float fr = fpos - bi;
    float sep = smoothstep(8.0 * u, 70.0 * u, rr);
    float bar = 1.0 - smoothstep(0.34, 0.48, abs(fr - 0.5));
    float edge = 1.0 - smoothstep(0.0, 0.9, max(-fpos, fpos - BANDS));
    float lane = mix(1.0, bar, sep) * edge;
    float len = mix(lens.x, lens.y, level(bi, t));
    float body = (0.95 - 0.35 * clamp(rr / len, 0.0, 1.0)) * (1.0 - smoothstep(len - 1.5 * u, len + 1.5 * u, rr));
    float cap = exp(-pow((rr - len + 2.0 * u) / (2.4 * u), 2.0)) * 1.1;
    float tail = 0.09 * exp(-max(rr - len, 0.0) / (70.0 * u)) * step(len, rr);
    float3 hue = mix(float3(1.0, 0.98, 0.95), spectral(bi / (BANDS - 1.0)), sep);
    float glowBand = exp(-pow((fr - 0.5) / 0.9, 2.0)) * 0.10 * (1.0 - smoothstep(0.0, len * 1.4, rr));
    light += hue * ((body + cap) * lane + tail * lane + glowBand) * beam * (1.0 - inside);
  }

  // Glass.
  if (sd < 14.0 * u) {
    // Refraction: the room behind, shifted and darkened, with a cool body tint.
    float2 shift = loc * 0.18;
    float3 seen = backdrop(p - shift) * 0.55 + float3(0.035, 0.050, 0.085);
    seen += float3(0.05, 0.06, 0.09) * clamp(0.5 - loc.y / prism.z, 0.0, 1.0);
    // The beam inside the glass, faintly splitting near the exit.
    float ds = segDist(p, inP, outP);
    float k = clamp(dot(p - inP, outP - inP) / dot(outP - inP, outP - inP), 0.0, 1.0);
    float3 inner = mix(float3(1.0, 0.97, 0.92), spectral(clamp(0.5 + (p.y - mix(inP.y, outP.y, k)) / (8.0 * u), 0.0, 1.0)), k * k * 0.7);
    seen += inner * (exp(-ds * ds / (2.0 * pow(2.6 * u * (1.0 + k), 2.0))) * 0.9 + exp(-ds * ds / (2.0 * pow(14.0 * u, 2.0))) * 0.18) * beam;
    // Light caught along the inner edges (total internal reflection), brighter on the lit faces.
    float rim = exp(sd / (2.6 * u)) * (sd < 0.0 ? 1.0 : 0.0);
    seen += float3(0.70, 0.78, 1.0) * rim * (0.14 + 0.30 * beam);
    // A bevel a few points in from the outline: the thickness of the glass.
    float bevel = exp(-abs(sd + 6.0 * u) / (0.8 * u)) * (sd < 0.0 ? 1.0 : 0.0);
    seen += float3(0.62, 0.70, 0.95) * bevel * (0.10 + 0.14 * beam);
    // A sheen band across the faces that slides as the prism turns.
    float sheen = exp(-pow((dot(loc, float2(0.8, 0.6)) + 18.0 * u - prism.w * 70.0 * u) / (10.0 * u), 2.0));
    seen += float3(0.85, 0.90, 1.0) * sheen * 0.16;
    col = mix(col, seen, inside);
    // A one-point highlight on the outline, and a soft halo outside it.
    float outline = exp(-abs(sd) / (0.7 * u));
    float lit = 0.55 + 0.45 * clamp(-loc.y / prism.z + 0.3, 0.0, 1.0);
    light += float3(0.86, 0.90, 1.0) * outline * (0.30 + 0.35 * beam) * lit;
    light += float3(0.45, 0.50, 0.85) * exp(-max(sd, 0.0) / (12.0 * u)) * (1.0 - inside) * 0.06 * (0.4 + beam);
  }

  // Flares where the beam meets the glass, and glints on the three corners.
  float2 de = p - inP;
  light += float3(1.0, 0.97, 0.9) * (exp(-dot(de, de) / (2.0 * pow(3.4 * u, 2.0))) * 1.2 + exp(-dot(de, de) / (2.0 * pow(22.0 * u, 2.0))) * 0.22) * beam;
  float2 dx = p - outP;
  light += float3(1.0, 0.95, 0.98) * exp(-dot(dx, dx) / (2.0 * pow(5.0 * u, 2.0))) * 0.8 * beam;
  for (int i = 0; i < 3; i++) {
    float a = prism.w + TAU * float(i) / 3.0 - 1.5707963;
    float2 v = prism.xy + float2(cos(a), sin(a)) * prism.z * 0.96;
    float2 dv = p - v;
    float tw = 0.55 + 0.45 * sin(w * float(3 + 2 * i) + float(i) * 2.1);
    float star = exp(-abs(dv.x) / (0.7 * u)) * exp(-abs(dv.y) / (9.0 * u)) + exp(-abs(dv.y) / (0.7 * u)) * exp(-abs(dv.x) / (9.0 * u));
    light += float3(0.9, 0.93, 1.0) * (star * 0.35 + exp(-dot(dv, dv) / (2.0 * pow(3.0 * u, 2.0))) * 0.5) * tw * (0.25 + 0.75 * beam);
  }

  // Dust motes, lit only where the light already is.
  float cell = 22.0 * u;
  float2 id = floor(p / cell);
  float hm = h21(id);
  if (hm > 0.55) {
    float2 base = (id + 0.3 + 0.4 * float2(h21(id + 3.1), h21(id + 7.7))) * cell;
    float k1 = 1.0 + floor(h21(id + 1.3) * 2.0);
    base += 4.5 * u * float2(sin(w * k1 + hm * TAU), cos(w * (3.0 - k1) + hm * 9.0));
    float2 dm = p - base;
    float mote = exp(-dot(dm, dm) / (2.0 * pow(0.9 * u, 2.0)));
    light += min(light, float3(1.2)) * mote * 2.4 * (0.6 + 0.4 * sin(w * 4.0 + hm * 20.0));
  }

  col += 1.0 - exp(-light * 1.15);

  // Vignette, grain, then to the display.
  float2 vg = p / size - float2(0.5, 0.42);
  col *= 1.0 - 0.55 * dot(vg * float2(1.1, 0.8), vg * float2(1.1, 0.8));
  col += (h21(floor(p * 1.7)) - 0.5) * 0.035;
  col = clamp(col, 0.0, 1.0);
  return half4(half3(col), 1.0);
}
"""
