package com.ngocdevv.nativecook.blocks.progress.laundryspinprogress

// The washer's front in one full-screen AGSL pass (API 33+): an enamel panel, the door (white
// bezel, chrome ring, handle) and, through its thick convex glass, the drum: brushed perforated
// steel and lifters turning with the drum's angle, six cloth shapes (smeared round the centre while
// the spin is fast), sloshing water with bubbles and suds, the rubber seal, then the glass's tint,
// reflections and the condensation fog that hides the reset. The React Native SkSL unchanged (see
// laundrySpinShader.ts) except that Android draws in sRGB, so there is no P3 conversion.
// Coordinates are dp: the canvas is scaled by the density before the shader is drawn.

internal const val LAUNDRY_AGSL = """
uniform float2 size;
uniform float4 door;
uniform float4 scene;
uniform float4 extra;
uniform float4 g0;
uniform float4 g1;
uniform float4 g2;
uniform float4 g3;
uniform float4 g4;
uniform float4 g5;
uniform float4 st;
uniform float4 st2;

const float TAU = 6.2831853;

float hs(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }

float vn(float2 q) {
  float2 i = floor(q);
  float2 f = fract(q);
  f = f * f * (3.0 - 2.0 * f);
  return mix(mix(hs(i), hs(i + float2(1.0, 0.0)), f.x), mix(hs(i + float2(0.0, 1.0)), hs(i + float2(1.0, 1.0)), f.x), f.y);
}

float3 toP3(float3 c) {
  // Android draws the shader in sRGB: no Display P3 conversion.
  return clamp(c, 0.0, 1.0);
}

float2 rot(float2 v, float a) {
  float c = cos(a);
  float s = sin(a);
  return float2(c * v.x - s * v.y, s * v.x + c * v.y);
}

float3 clothColor(int k) {
  if (k == 0) return float3(0.24, 0.36, 0.56);
  if (k == 1) return float3(0.86, 0.38, 0.33);
  if (k == 2) return float3(0.91, 0.71, 0.30);
  if (k == 3) return float3(0.47, 0.63, 0.50);
  if (k == 4) return float3(0.93, 0.92, 0.89);
  return float3(0.64, 0.57, 0.78);
}

// One garment at drum point d: rgb and coverage.
float4 cloth(float2 d, float4 g, float stretch, int k) {
  float fk = float(k);
  float2 l = rot(d - g.xy, -g.z);
  l.x /= 1.0 + stretch;
  l.y *= 1.0 + stretch * 0.6;
  float a = 0.33 + 0.07 * hs(float2(fk, 1.0));
  float b = 0.21 + 0.05 * hs(float2(fk, 2.0));
  float ang = atan(l.y, l.x);
  float wob = 1.0 + 0.12 * sin(3.0 * ang + fk * 1.7) + 0.06 * sin(7.0 * ang + fk * 2.9);
  float rr = pow(pow(abs(l.x) / a, 2.6) + pow(abs(l.y) / b, 2.6), 1.0 / 2.6) / wob;
  float cov = 1.0 - smoothstep(0.9, 1.0, rr);
  if (cov <= 0.0) return float4(0.0);
  float3 c = clothColor(k);
  if (k == 4) c = mix(c, float3(0.20, 0.28, 0.46), step(0.62, fract(l.y * 11.0 + l.x * 2.0)));
  float folds = sin(l.x * 26.0 + l.y * 9.0 + fk) * 0.5 + sin(l.x * 7.0 - l.y * 21.0 + fk * 3.0) * 0.5;
  float knit = vn(l * 160.0) - 0.5;
  c *= 0.86 + 0.12 * folds + knit * 0.05;
  c *= 1.0 - 0.32 * smoothstep(0.55, 1.0, rr);
  c *= 1.0 + 0.12 * clamp(-l.y / b, -1.0, 1.0);
  return float4(c, cov);
}

float4 garment(float2 d, float4 g, float stretch, int k) {
  if (g.w < 0.01) return cloth(d, g, stretch, k);
  // smear round the drum's centre: four samples across the blur angle
  float4 acc = float4(0.0);
  for (int j = 0; j < 4; j++) {
    float o = (float(j) / 3.0 - 0.5) * g.w;
    float4 c = cloth(rot(d, o), g, stretch, k);
    acc += float4(c.rgb * c.a, c.a);
  }
  acc /= 4.0;
  return float4(acc.a > 0.0001 ? acc.rgb / acc.a : float3(0.0), acc.a);
}

float3 drumAt(float2 d) {
  float r = length(d);
  float A = scene.x;
  float2 dr = rot(d, A);
  // brushed steel, darker towards the back (the centre), lit from the top left
  float3 c = mix(float3(0.28, 0.29, 0.31), float3(0.66, 0.67, 0.69), smoothstep(0.05, 1.0, r));
  c *= 0.85 + 0.15 * vn(float2(atan(dr.y, dr.x) * 40.0, r * 3.0));
  c *= 0.9 + 0.2 * clamp(dot(normalize(d + 0.0001), float2(-0.6, -0.8)), -1.0, 1.0) * smoothstep(0.4, 1.0, r);
  // perforations on a hex grid that turns with the drum
  float2 hq = dr / 0.075;
  float2 hr = float2(1.0, 1.732);
  float2 h1 = mod(hq, hr) - hr * 0.5;
  float2 h2 = mod(hq - hr * 0.5, hr) - hr * 0.5;
  float hd = min(length(h1), length(h2));
  float hole = (1.0 - smoothstep(0.2, 0.28, hd)) * smoothstep(0.25, 0.45, r);
  c = mix(c, float3(0.05, 0.05, 0.06), hole * 0.85);
  c += float3(0.08) * (1.0 - smoothstep(0.28, 0.36, hd)) * smoothstep(0.24, 0.28, hd) * smoothstep(0.25, 0.45, r);
  // three lifters on the wall
  float la = atan(-dr.y, dr.x);
  float ld = abs(mod(la + TAU / 6.0, TAU / 3.0) - TAU / 6.0);
  float lift = (1.0 - smoothstep(0.12, 0.16, ld)) * smoothstep(0.78, 0.82, r);
  c = mix(c, mix(float3(0.55, 0.56, 0.57), float3(0.78, 0.79, 0.8), smoothstep(0.82, 1.0, r)), lift);
  return c;
}

half4 main(float2 p) {
  float2 c0 = door.xy;
  float Rg = door.z;
  float u = door.w;
  float2 q = p - c0;
  float r = length(q);

  // the enamel front panel
  float3 col = mix(float3(0.955, 0.955, 0.95), float3(0.86, 0.865, 0.87), smoothstep(0.0, 1.0, p.y / size.y));
  col += (hs(p * 0.83) - 0.5) * 0.012;
  // the door's shadow on the panel
  float bez = Rg + 36.0 * u;
  col *= 1.0 - 0.22 * (1.0 - smoothstep(bez - 6.0 * u, bez + 22.0 * u, length(q - float2(0.0, 10.0 * u))));

  // white bezel, then the chrome ring
  if (r < bez) {
    float k = (r - Rg - 20.0 * u) / (16.0 * u);
    float3 bc = mix(float3(0.98, 0.98, 0.975), float3(0.84, 0.85, 0.86), smoothstep(-1.0, 1.0, q.y / bez));
    bc *= 1.0 - 0.12 * smoothstep(0.7, 1.0, k);
    col = mix(col, bc, 1.0 - smoothstep(bez - 1.0, bez, r));
  }
  if (r < Rg + 20.0 * u) {
    float s = clamp((r - Rg) / (20.0 * u), 0.0, 1.0);
    float nz = sin(s * 3.14159);
    float ny = (q.y / max(r, 0.001)) * cos(s * 3.14159);
    float env = smoothstep(-0.2, 0.6, -ny * 0.8 + nz * 0.5);
    float3 chrome = mix(float3(0.25, 0.27, 0.30), float3(0.97, 0.98, 1.0), env);
    chrome += float3(0.25) * (1.0 - smoothstep(0.0, 0.08, abs(s - 0.32))) * step(q.y, 0.0);
    chrome *= 0.8 + 0.2 * smoothstep(-1.0, 0.5, -q.y / r);
    col = mix(col, chrome, 1.0 - smoothstep(Rg + 19.0 * u, Rg + 20.0 * u, r));
  }

  // through the glass
  if (r < Rg) {
    float rn = r / Rg;
    // the rubber seal, then the drum beyond it, magnified by the convex glass
    float2 d = q / (Rg * 0.9) * (0.9 + 0.1 * rn * rn);
    float dr = length(d);
    float3 inside = drumAt(d);
    float A = scene.x;
    // garments: four in front of the water's surface band and two behind, all drawn in order
    for (int k = 0; k < 6; k++) {
      float4 g = k == 0 ? g0 : (k == 1 ? g1 : (k == 2 ? g2 : (k == 3 ? g3 : (k == 4 ? g4 : g5))));
      float s = k == 0 ? st.x : (k == 1 ? st.y : (k == 2 ? st.z : (k == 3 ? st.w : (k == 4 ? st2.x : st2.y))));
      float4 cl = garment(d, g, s, k);
      // the drum's shade falls on the clothes too
      cl.rgb *= 0.55 + 0.45 * smoothstep(0.0, 0.9, length(g.xy) + 0.3);
      inside = mix(inside, cl.rgb, cl.a);
    }
    // water
    float level = scene.z;
    float slosh = extra.y;
    float ph = extra.z * TAU;
    float surf = 1.0 - 2.0 * level - 0.2 * slosh * d.x + slosh * (0.035 * sin(d.x * 5.0 + ph * 9.0) + 0.018 * sin(d.x * 13.0 - ph * 14.0));
    float under = smoothstep(surf - 0.01, surf + 0.01, d.y) * step(0.002, level);
    float depth = clamp((d.y - surf) / 0.5, 0.0, 1.0);
    float3 waterC = mix(float3(0.82, 0.92, 0.97), float3(0.56, 0.70, 0.80), depth);
    inside = mix(inside, inside * waterC * 1.12 + float3(0.05, 0.08, 0.10), under * 0.9);
    inside += float3(0.10, 0.14, 0.16) * under * (1.0 - depth) * 0.5;
    // rising bubbles (cells move up a whole number of rows per loop)
    float2 bq = d / 0.06 + float2(0.0, extra.z * 30.0);
    float2 bc = fract(bq) - 0.5;
    float bh = hs(floor(bq));
    float bub = step(0.82, bh) * (1.0 - smoothstep(0.08, 0.14, length(bc - (float2(hs(floor(bq) + 2.0), 0.5) - 0.5) * 0.5)));
    inside += float3(0.35) * bub * under * clamp(scene.y * 2.0 + 0.2, 0.0, 1.0);
    // the surface line and the suds riding on it
    float line = 1.0 - smoothstep(0.0, 0.012, abs(d.y - surf));
    inside += float3(0.25, 0.3, 0.32) * line * step(0.002, level);
    float foamH = scene.w * 0.22 * (0.6 + 0.8 * vn(float2(d.x * 6.0 + A * 0.3, 1.0)));
    float foamTop = surf - foamH;
    float inFoam = smoothstep(foamTop - 0.01, foamTop + 0.01, d.y) * (1.0 - smoothstep(surf + 0.02, surf + 0.05, d.y)) * step(0.01, scene.w);
    float2 fq = float2(d.x + A * 0.05, d.y) / 0.03;
    float fb = vn(fq) * 0.6 + vn(fq * 2.3) * 0.4;
    float3 foamC = mix(float3(0.82, 0.86, 0.88), float3(0.99, 0.99, 1.0), smoothstep(0.3, 0.7, fb));
    inside = mix(inside, foamC, inFoam * smoothstep(0.25, 0.45, fb + 0.2) * step(0.002, level));
    // the rubber seal
    float seal = smoothstep(0.86, 0.9, rn);
    float3 sealC = mix(float3(0.10, 0.10, 0.11), float3(0.30, 0.31, 0.33), smoothstep(0.9, 1.0, rn) * smoothstep(-1.0, 0.3, -q.y / max(r, 0.001)));
    inside = mix(inside, sealC, seal);
    inside *= 1.0 - 0.5 * smoothstep(0.5, 0.86, rn) * (1.0 - seal);
    // the glass: tint, thick rim, reflections, fog
    float3 gl = inside * mix(float3(0.96, 1.0, 0.98), float3(0.78, 0.9, 0.86), smoothstep(0.8, 1.0, rn));
    float ang = atan(q.y, q.x);
    float cres = (1.0 - smoothstep(0.06, 0.12, abs(rn - 0.78))) * smoothstep(-0.2, 0.6, -sin(ang + 0.6)) * (1.0 - smoothstep(-0.3, 0.3, q.x / Rg + 0.2));
    gl += float3(0.55) * cres * 0.45;
    float2 wq = q / Rg - float2(0.22, -0.42);
    float win = (1.0 - smoothstep(0.08, 0.11, abs(wq.x + wq.y * 0.3))) * (1.0 - smoothstep(0.12, 0.16, abs(wq.y)));
    gl += float3(0.6) * win * 0.18;
    gl += float3(1.0) * exp(-dot(q / Rg + float2(0.45, 0.5), q / Rg + float2(0.45, 0.5)) * 120.0) * 0.6;
    float fog = extra.x;
    float drops = smoothstep(0.55, 0.75, vn(q / (4.0 * u)));
    float3 fogC = mix(float3(0.86, 0.88, 0.88), float3(0.95, 0.96, 0.96), vn(q / (30.0 * u)));
    gl = mix(gl, fogC, fog * (0.88 - drops * 0.3));
    gl += float3(0.12) * drops * fog;
    col = mix(col, gl, 1.0 - smoothstep(Rg - 1.0, Rg, r));
  }

  // the door handle on the right
  float2 hq = p - (c0 + float2(Rg + 28.0 * u, 0.0));
  float hd = length(float2(hq.x, max(abs(hq.y) - 36.0 * u, 0.0))) - 6.0 * u;
  float3 hc = mix(float3(0.45, 0.47, 0.5), float3(0.96, 0.97, 0.98), smoothstep(6.0 * u, -6.0 * u, hq.x));
  col *= 1.0 - 0.2 * (1.0 - smoothstep(0.0, 8.0 * u, hd - 2.0 * u + hq.x * 0.2)) * step(0.0, hd);
  col = mix(col, hc, 1.0 - smoothstep(-0.6, 0.6, hd));
  return half4(half3(toP3(col)), 1.0);
}
"""

