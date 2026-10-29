package com.ngocdevv.nativecook.blocks.media.petalglassscenes

/**
 * The AGSL port of PetalGlassScenes.metal (API 33+): the whole picture in one shader, in layout
 * units (393 wide). Two petal scenes, each an image in its own drifting rectangle; scene B grows
 * out of the card as a rounded-rect front with a lens bulge along it. Inside the card, a thick
 * glass slab: 1.06× magnification, a slow liquid warp, a 20-unit bevel that pulls the image
 * inward, an R/G/B split growing towards the rim, rim light, sheen and a soft shadow.
 */
const val PETAL_GLASS_SCENES_AGSL = """
uniform shader imageA;
uniform shader imageB;
uniform float k;
uniform float2 size;
uniform float4 rectA;
uniform float2 pixelsA;
uniform float4 rectB;
uniform float2 pixelsB;
uniform float4 card;
uniform float4 change;
uniform float4 lightU;
uniform float4 extra;

float pgBox(float2 p, float2 b, float r) {
  float2 q = abs(p) - b;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

float pgCard(float2 p, float2 h, float r) {
  return pgBox(p, h - r, r);
}

float2 pgCardNormal(float2 p, float2 h, float r) {
  float2 q = abs(p) - (h - r);
  float2 s = float2(p.x < 0.0 ? -1.0 : 1.0, p.y < 0.0 ? -1.0 : 1.0);
  if (q.x > 0.0 && q.y > 0.0) return normalize(q) * s;
  return q.x > q.y ? float2(s.x, 0.0) : float2(0.0, s.y);
}

float pgFront(float2 p, float2 h, float r, float fr) {
  float2 b = h - r + fr;
  float rr = r + min(min(b.x, b.y), 0.0);
  return pgBox(p, max(b, 0.0), rr);
}

float3 pgA(float2 q) {
  return imageA.eval((q - rectA.xy) / rectA.zw * pixelsA).rgb;
}

float3 pgB(float2 q) {
  return imageB.eval((q - rectB.xy) / rectB.zw * pixelsB).rgb;
}

float3 pgWorld(float2 q) {
  float2 c = card.xy;
  float2 h = card.zw;
  float r = extra.x;
  float fr = change.x;
  float bulge = change.y;
  float fade = change.w;
  if (fade >= 0.0) return mix(pgA(q), pgB(q), fade);
  float2 local = q - c;
  float x = pgFront(local, h, r, fr);
  float2 shift = float2(0.0);
  float glow = 0.0;
  if (bulge > 0.001) {
    float2 b = max(h - r + fr, 1.0) + r;
    float2 n = normalize(local / (b * b) + 1e-6);
    float g = exp(-(x / 32.0) * (x / 32.0)) * bulge;
    shift = n * (-x / 32.0) * g * 30.0;
    glow = g * exp(-(x / 7.0) * (x / 7.0));
  }
  float m = 1.0 - smoothstep(-1.2, 1.2, x);
  float3 col;
  if (length(shift) < 0.02) {
    if (m < 0.001) col = pgA(q);
    else if (m > 0.999) col = pgB(q);
    else col = mix(pgA(q), pgB(q), m);
  } else {
    float2 qr = q + shift * 1.18;
    float2 qb = q + shift * 0.82;
    float2 qg = q + shift;
    float3 a = float3(0.0);
    float3 bb = float3(0.0);
    if (m < 0.999) a = float3(pgA(qr).r, pgA(qg).g, pgA(qb).b);
    if (m > 0.001) bb = float3(pgB(qr).r, pgB(qg).g, pgB(qb).b);
    col = mix(a, bb, m);
  }
  return col + glow * 0.30;
}

half4 main(float2 coord) {
  float2 q = coord / k;
  float2 c = card.xy;
  float2 h = card.zw;
  float r = extra.x;
  float2 local = q - c;
  float d = pgCard(local, h, r);

  float3 col = float3(0.0);
  if (d > 0.0) {
    col = pgWorld(q);
    float contact = pgCard(local - float2(0.0, 3.0), h, r);
    float ambient = pgCard(local - float2(0.0, 18.0), h, r);
    float shade = 0.14 * (1.0 - smoothstep(-2.0, 7.0, contact)) + 0.16 * (1.0 - smoothstep(-12.0, 34.0, ambient));
    col *= 1.0 - shade;
  }
  if (d < 1.0) {
    float theta = lightU.x;
    float dp = lightU.y;
    float2 L = normalize(lightU.zw);
    float edge = clamp(1.0 + d / 20.0, 0.0, 1.0);
    float2 n = pgCardNormal(local, h, r);
    float bend = pow(edge, 2.5) * 30.0 * extra.z;
    float amp = change.z * extra.z;
    float2 warp = amp * float2(
      sin(local.y * 0.034 + theta + dp + 1.3 * sin(local.x * 0.021 + 2.0 * theta)),
      cos(local.x * 0.030 - theta + dp * 0.7 + 1.1 * sin(local.y * 0.026 - theta))
    );
    float2 disp = -local * (1.0 - 1.0 / 1.06) * extra.z + warp * (1.0 - 0.6 * edge) - n * bend;
    float ch = 0.08 + 0.12 * edge;
    float3 cr = pgWorld(q + disp * (1.0 + ch));
    float3 cg = pgWorld(q + disp);
    float3 cb = pgWorld(q + disp * (1.0 - ch));
    float3 glass = float3(cr.r, cg.g, cb.b);

    float facing = dot(n, L);
    float rim = 1.0 - smoothstep(0.0, 1.8, -d);
    glass += rim * (0.10 + 0.50 * max(facing, 0.0) + 0.14 * max(-facing, 0.0));
    float e3 = edge * edge * edge;
    glass += e3 * 0.16 * max(facing, 0.0);
    glass *= 1.0 - e3 * 0.10 * max(-facing, 0.0);
    glass = mix(glass, float3(1.0), 0.045);
    float band = dot(local, normalize(float2(0.82, -0.57))) - extra.y;
    glass += 0.07 * exp(-band * band / (38.0 * 38.0));

    float a = clamp(0.5 - d * k, 0.0, 1.0);
    col = d > 0.0 ? mix(col, glass, a) : glass;
  }

  col *= 1.0 - 0.12 * (1.0 - smoothstep(0.0, 150.0, q.y));
  col *= 1.0 - 0.26 * smoothstep(size.y - 260.0, size.y, q.y);
  return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""
