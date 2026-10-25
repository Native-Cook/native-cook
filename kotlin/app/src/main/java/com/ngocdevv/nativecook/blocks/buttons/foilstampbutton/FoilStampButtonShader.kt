package com.ngocdevv.nativecook.blocks.buttons.foilstampbutton

// The React Native SkSL (foilStampShader.ts) in AGSL, the same dialect (API 33+), without the
// Display P3 conversion. FOIL_PAPER_AGSL is drawn once into a bitmap at layout time: the linen
// table, the cotton card with its two shadows and fibres, a soft vignette. FOIL_SEAL_AGSL is drawn
// every frame over a square round the seal and returns a premultiplied overlay (shading and foil).
//
// The seal's height comes from a mask bitmap (FoilStampButtonArt.kt): red = the crisp monogram,
// green = the same blurred, used as a height map. The normal is the height's gradient; a raking
// light gives the blind emboss its lit and shaded walls, and where the foil is laid the surface is a
// gold mirror with thin iridescent bands that slide with the light and the surface angle.

private const val COMMON = """
float hash21(float2 p) {
  p = fract(p * float2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}

float vnoise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  float2 u = f * f * (3.0 - 2.0 * f);
  float a = hash21(i);
  float b = hash21(i + float2(1.0, 0.0));
  float c = hash21(i + float2(0.0, 1.0));
  float d = hash21(i + float2(1.0, 1.0));
  return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(float2 p) {
  float v = 0.0;
  float a = 0.5;
  for (int i = 0; i < 4; i++) {
    v += a * vnoise(p);
    p = p * 2.03 + float2(17.1, 9.2);
    a *= 0.5;
  }
  return v;
}

float sdRoundRect(float2 p, float2 c, float2 halfSize, float r) {
  float2 q = abs(p - c) - halfSize + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}
"""

internal const val FOIL_PAPER_AGSL = """
uniform float2 size;
uniform float4 card;
uniform float radius;
$COMMON

// Cotton fibres: a few long, thin, randomly turned streaks per cell.
float fibres(float2 p) {
  float v = 0.0;
  for (int k = 0; k < 3; k++) {
    float a = float(k) * 2.1 + 0.4;
    float2 d = float2(cos(a), sin(a));
    float2 q = float2(dot(p, d), dot(p, float2(-d.y, d.x)));
    float n = vnoise(q * float2(0.045, 0.9) + float(k) * 13.0);
    v += smoothstep(0.78, 0.92, n) * 0.5;
  }
  return v;
}

half4 main(float2 p) {
  float2 uv = p / size;
  // Linen table: warm, three stops, darker toward the bottom.
  float3 top = float3(0.925, 0.894, 0.843);
  float3 mid = float3(0.902, 0.861, 0.792);
  float3 bot = float3(0.846, 0.796, 0.722);
  float3 col = uv.y < 0.5 ? mix(top, mid, uv.y * 2.0) : mix(mid, bot, (uv.y - 0.5) * 2.0);
  // A fine woven texture.
  float weave = sin(p.x * 2.1) * sin(p.y * 2.1);
  col *= 1.0 + weave * 0.012 + (hash21(floor(p * 1.5)) - 0.5) * 0.035;

  float2 c = card.xy + card.zw * 0.5;
  float d = sdRoundRect(p, c, card.zw * 0.5, radius);
  // Two shadows: a tight contact one and a soft ambient one, both falling down.
  float contact = 1.0 - smoothstep(-1.0, 3.5, sdRoundRect(p, c + float2(0.0, 1.5), card.zw * 0.5, radius));
  float ambient = 1.0 - smoothstep(-14.0, 30.0, sdRoundRect(p, c + float2(0.0, 14.0), card.zw * 0.5 - 6.0, radius));
  col *= 1.0 - contact * 0.22 - ambient * 0.16;

  // The card: cream cotton with fibres, a soft fold of light across it.
  float inside = 1.0 - smoothstep(-0.6, 0.6, d);
  float3 paper = float3(0.953, 0.926, 0.878);
  float fib = fibres(p);
  float cloud = fbm(p * 0.035);
  float3 cardCol = paper * (0.975 + cloud * 0.045) - fib * 0.022;
  cardCol += (hash21(floor(p * 2.0)) - 0.5) * 0.025;
  float sheen = 0.5 + 0.5 * (p.x - card.x) / card.z;
  cardCol *= 0.985 + sheen * 0.025;
  // The edge: a hair of light on the top, a darker line at the bottom.
  float edge = 1.0 - smoothstep(0.0, 1.4, abs(d + 0.7));
  float topSide = clamp((c.y - p.y) / (card.w * 0.5), -1.0, 1.0);
  cardCol += edge * topSide * 0.05;
  col = mix(col, cardCol, inside);

  // Vignette.
  float2 v = uv - 0.5;
  col *= 1.0 - dot(v, v) * 0.32;
  return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

internal const val FOIL_SEAL_AGSL = """
uniform shader mask;
uniform float2 origin;
uniform float scale;
uniform float R;
uniform float depth;
uniform float foil;
uniform float dissolve;
uniform float ring;
uniform float light;
uniform float elevation;
$COMMON

float heightAt(float2 q) {
  return mask.eval(q).g;
}

half4 main(float2 p) {
  float2 rel = p - origin;
  float2 q = rel * scale + float2(R + 14.0) * scale;
  float r = length(rel);

  // Dissolve: a noise threshold eats the seal from many small islands; a thin warm edge glows.
  float n = fbm(p * 0.09) * 0.75 + vnoise(p * 0.6) * 0.25;
  float th = dissolve * 1.15 - 0.08;
  float keep = smoothstep(th - 0.03, th + 0.03, n);
  float burnEdge = (1.0 - smoothstep(0.0, 0.05, abs(n - th))) * step(0.001, dissolve) * step(dissolve, 0.999);

  float amount = depth * keep;

  // The paper's dip at contact: a soft ring just outside the seal.
  float dr = (r - R * 1.04) / (R * 0.08);
  float dip = ring * exp(-dr * dr) * 0.06;

  if (amount < 0.002 && dip < 0.002) return half4(0.0);

  float4 m = float4(mask.eval(q));
  float crisp = m.r;
  float e = scale;
  float hx = heightAt(q + float2(e, 0.0)) - heightAt(q - float2(e, 0.0));
  float hy = heightAt(q + float2(0.0, e)) - heightAt(q - float2(0.0, e));
  // Height in dp: 1.6 dp of relief at full depth; the taps are one dp apart.
  float relief = 1.6 * amount;
  float3 nrm = normalize(float3(-hx * relief * 0.5, -hy * relief * 0.5, 1.0));
  float3 L = normalize(float3(cos(light) * cos(elevation), sin(light) * cos(elevation), sin(elevation)));
  float shade = dot(nrm, L) - L.z;

  // Blind emboss on the cotton: light on the walls facing the lamp, a soft brown shade on the rest.
  float lit = max(shade, 0.0);
  float dark = max(-shade, 0.0);
  float3 overlay = float3(1.0, 0.985, 0.95);
  float aLit = clamp(lit * 1.5, 0.0, 0.85);
  float aDark = clamp(dark * 1.6 + dip, 0.0, 0.7);
  float3 col = mix(float3(0.36, 0.27, 0.17), overlay, aLit / max(aLit + aDark, 0.0001));
  float alpha = max(aLit, aDark);

  // Foil: laid by a diagonal sweep, only where the stamp was pressed hard enough.
  float along = dot(rel, normalize(float2(0.8, 0.6))) / (R * 1.1);
  float front = foil * 2.4 - 1.2;
  float laid = smoothstep(front + 0.12, front - 0.12, along);
  float foilA = crisp * laid * smoothstep(0.55, 0.85, depth) * keep;
  if (foilA > 0.001) {
    float3 V = float3(0.0, 0.0, 1.0);
    float3 H = normalize(L + V);
    float spec = pow(max(dot(nrm, H), 0.0), 40.0);
    float ndl = clamp(dot(nrm, L) * 0.5 + 0.5, 0.0, 1.0);
    // A gold mirror: what it reflects is a broad studio band that slides with the light.
    float env = 0.5 + 0.5 * sin(light * 2.2 + rel.x * 0.035 - rel.y * 0.02 + nrm.x * 2.5 + nrm.y * 1.5);
    float3 g0 = float3(0.42, 0.29, 0.11);
    float3 g1 = float3(0.78, 0.58, 0.27);
    float3 g2 = float3(0.96, 0.86, 0.58);
    float3 g3 = float3(1.0, 0.97, 0.88);
    float k = clamp(env * 0.7 + ndl * 0.45 - 0.1, 0.0, 1.0);
    float3 gold = k < 0.5 ? mix(g0, g1, k * 2.0) : mix(g1, g2, (k - 0.5) * 2.0);
    // Thin-film bands: hue follows the surface angle and the light, mixed in lightly.
    float band = fract(dot(nrm.xy, float2(1.6, 1.1)) + light * 0.3 + (rel.x + rel.y) * 0.004);
    float3 film = 0.5 + 0.5 * cos(6.2831 * (band + float3(0.0, 0.33, 0.67)));
    gold = mix(gold, gold * (0.7 + film * 0.55), 0.22);
    // Brushed grain in the foil, along one direction.
    float2 bq = float2(dot(rel, float2(0.94, 0.34)), dot(rel, float2(-0.34, 0.94)));
    gold *= 0.95 + 0.1 * vnoise(bq * float2(0.25, 3.5));
    gold += g3 * spec * 1.1;
    // The sweep's front: a bright gleam that passes once.
    float gd = (along - front) / 0.09;
    float gleam = exp(-gd * gd) * step(0.001, foil) * step(foil, 0.999);
    gold += float3(1.0, 0.93, 0.75) * gleam * 0.6 * crisp;
    col = mix(col, gold, foilA);
    alpha = mix(alpha, 1.0, foilA);
  }
  // The dissolving edge glints warm for a moment.
  float glint = burnEdge * crisp * depth * 0.5;
  col = mix(col, float3(0.98, 0.84, 0.5), glint);
  alpha = max(alpha, glint);
  return half4(half3(clamp(col, 0.0, 1.0)) * alpha, alpha);
}
"""
