// The whole hero in one runtime shader: the bronze plaque, the thin paper laid over it, the wax
// that the crayon leaves on the paper, and the sheet peeling away.
//
// Frottage: wax only reaches the paper where something pushes it up. The deposit is
// rub (how much the crayon has worked this spot, from the rubbed-field image) times the relief's
// height above a threshold (lowered a little by the paper's tooth, so edges break up), plus a faint
// film on the flat paper; streaks along the stroke direction give it the crayon's grain. Before the
// crayon passes, the relief only shows as a faint emboss in the paper's shading.
//
// The peel is a flat fold of the top-right corner travelling toward the bottom-left: past the
// crease the old sheet is gone and a fresh one lies under it; the folded flap shows the paper's
// back with the rubbing faint through it. The plaque casts a soft shadow; outside it the output is
// transparent (premultiplied). Colours are converted from sRGB to Display P3.
//
// Keep comments free of backticks: this is a JS template string.

export const RUB_SKSL = `
uniform shader relief;
uniform shader rubbed;
uniform float4 plaque;
uniform float4 paper;
uniform float peel;
uniform float3 wax;
uniform float grainAngle;

float3 toP3(float3 c) {
  float3 l = pow(clamp(c, 0.0, 1.0), float3(2.2));
  float3 m = float3(
    dot(l, float3(0.8225, 0.1774, 0.0)),
    dot(l, float3(0.0332, 0.9669, 0.0)),
    dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(m, float3(1.0 / 2.2));
}

float hash21(float2 p) {
  float3 q = fract(float3(p.xyx) * 0.1031);
  q += dot(q, q.yzx + 33.33);
  return fract((q.x + q.y) * q.z);
}

float vnoise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash21(i), hash21(i + float2(1.0, 0.0)), u.x), mix(hash21(i + float2(0.0, 1.0)), hash21(i + float2(1.0, 1.0)), u.x), u.y);
}

float fbm(float2 p) {
  float v = 0.0;
  float a = 0.5;
  for (int i = 0; i < 3; i++) {
    v += a * vnoise(p);
    p = p * 2.07 + float2(13.1, 7.7);
    a *= 0.5;
  }
  return v;
}

float boxDist(float2 p, float4 r) {
  float2 c = r.xy + r.zw * 0.5;
  float2 d = abs(p - c) - r.zw * 0.5;
  return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0);
}

bool inside(float2 p, float4 r) {
  return p.x >= r.x && p.y >= r.y && p.x <= r.x + r.z && p.y <= r.y + r.w;
}

float height(float2 p) {
  return relief.eval(p).r;
}

// The paper at canvas point p: its fibres, the relief's faint emboss and, if rubbed, the wax.
float3 paperAt(float2 p, float rubAmount) {
  float h = height(p);
  float hx = height(p + float2(1.2, 0.0)) - height(p - float2(1.2, 0.0));
  float hy = height(p + float2(0.0, 1.2)) - height(p - float2(0.0, 1.2));
  float3 n = normalize(float3(-hx * 2.2, -hy * 2.2, 1.0));
  float lit = dot(n, normalize(float3(-0.5, -0.7, 0.8)));
  float fibre = fbm(float2(p.x * 0.3, p.y * 0.75));
  float3 base = float3(0.937, 0.910, 0.858) * (0.965 + 0.05 * fibre);
  // The relief shows through the thin sheet from the start: its shading plus a faint outline where
  // the paper bends over each edge (about 12-15 % darker).
  float bend = clamp(length(float2(hx, hy)) * 3.2, 0.0, 1.0);
  base *= (0.9 + 0.15 * lit) * (1.0 - 0.13 * bend);
  // Wax: the relief's high points first, streaked along the stroke.
  float tooth = vnoise(p * 1.9) * 0.6 + vnoise(p * 4.3) * 0.4;
  float ca = cos(grainAngle);
  float sa = sin(grainAngle);
  float2 q = float2(ca * p.x + sa * p.y, -sa * p.x + ca * p.y);
  float streak = vnoise(float2(q.x * 0.07, q.y * 1.3)) * 0.6 + vnoise(float2(q.x * 0.15, q.y * 2.9)) * 0.4;
  float raised = smoothstep(0.26, 0.58, h + (tooth - 0.5) * 0.34 + rubAmount * 0.1);
  float deposit = rubAmount * (0.14 * streak + 0.86 * raised);
  deposit *= 0.72 + 0.28 * smoothstep(0.2, 0.55, streak);
  deposit *= 0.78 + 0.22 * smoothstep(0.15, 0.45, tooth);
  deposit = clamp(deposit * 1.12, 0.0, 1.0);
  // Waxy sheen where it is thick, catching the same light as the paper.
  float3 waxCol = wax * (0.86 + 0.22 * lit) + 0.05 * deposit * deposit;
  return mix(base, waxCol, deposit * 0.94);
}

float3 bronzeAt(float2 p) {
  float d = -boxDist(p, plaque);
  float bevel = 1.0 - smoothstep(0.0, 7.0, d);
  float patina = fbm(p * 0.05);
  float speck = smoothstep(0.62, 0.8, fbm(p * 0.21 + 4.0));
  float3 c = mix(float3(0.42, 0.27, 0.13), float3(0.64, 0.45, 0.24), smoothstep(0.2, 0.8, patina));
  c = mix(c, float3(0.24, 0.42, 0.36), speck * 0.55);
  // The bevel catches light along the top and left edges, falls into shade at the bottom and right.
  float2 g = float2(p.x - (plaque.x + plaque.z * 0.5), p.y - (plaque.y + plaque.w * 0.5));
  float side = -(g.x / plaque.z + g.y / plaque.w);
  c *= 1.0 + bevel * side * 0.9;
  c += float3(0.35, 0.28, 0.16) * bevel * max(side, 0.0) * 0.6;
  return c;
}

// Everything but the flap: the plaque, the old sheet or the fresh one, the shadows. Premultiplied.
half4 sheetAt(float2 p, float4 pa, float s, float crease, float f) {
  if (inside(p, plaque)) {
    float3 col;
    if (inside(p, pa)) {
      if (s >= crease) {
        col = paperAt(p, rubbed.eval(p).r);
        // The flap's shadow on the old sheet beyond its edge.
        float edge = s - f;
        if (peel > 0.0 && edge > 0.0 && edge < 14.0) col *= 1.0 - 0.22 * (1.0 - edge / 14.0) * (1.0 - smoothstep(0.78, 1.0, peel));
      } else {
        col = paperAt(p, 0.0);
        // Under the lifted flap the fresh sheet is in its shadow, deepest near the crease.
        float lift = crease - s;
        col *= 1.0 - 0.18 * exp(-lift / 18.0) * (1.0 - smoothstep(0.78, 1.0, peel));
      }
      // The paper's own edge: a thin shadow line where it lies on the bronze.
      float e = -boxDist(p, pa);
      col *= 0.9 + 0.1 * smoothstep(0.0, 1.5, e);
    } else {
      col = bronzeAt(p);
      // The paper's shadow on the bronze.
      float e = boxDist(p - float2(1.0, 2.5), pa);
      col *= 1.0 - 0.35 * (1.0 - smoothstep(0.0, 6.0, e));
    }
    return half4(half3(toP3(col)), 1.0);
  }
  // Outside the plaque: its soft shadow on the table, nothing else.
  float d = boxDist(p - float2(0.0, 7.0), plaque);
  float a = 0.5 * (1.0 - smoothstep(0.0, 22.0, d));
  return half4(0.0, 0.0, 0.0, half(a));
}

half4 main(float2 p) {
  float4 pa = paper;
  float2 corner = float2(pa.x + pa.z, pa.y);
  float2 v = normalize(float2(-pa.z, pa.w));
  float L = length(pa.zw);
  float f = peel * 2.05 * L;
  float crease = f * 0.5;
  float s = dot(p - corner, v);
  half4 under = sheetAt(p, pa, s, crease, f);

  // The flap: the part of the old sheet folded over, seen from its back, laid over the rest with
  // an opacity that fades as it leaves (so a finished peel is exactly the fresh sheet).
  float fadeOut = 1.0 - smoothstep(0.78, 1.0, peel);
  if (peel > 0.0 && fadeOut > 0.0 && s >= crease && s <= f) {
    float2 src = p - 2.0 * (s - crease) * v;
    if (inside(src, pa)) {
      float r = rubbed.eval(src).r;
      float3 back = float3(0.905, 0.878, 0.826) * (0.96 + 0.04 * fbm(src * 0.5));
      back = mix(back, wax, r * 0.12);
      float k = clamp((s - crease) / 26.0, 0.0, 1.0);
      back *= 0.88 + 0.16 * smoothstep(0.0, 1.0, k) - 0.06 * k * k;
      back = mix(back * 0.82, back, smoothstep(0.0, 3.0, s - crease));
      return half4(half3(toP3(back)) * half(fadeOut), half(fadeOut)) + under * half(1.0 - fadeOut);
    }
  }
  return under;
}
`;
