package com.ngocdevv.nativecook.blocks.charts.treeringchart

// The cut slice in one AGSL pass (API 33+), the React Native SkSL unchanged but for the Display P3
// conversion, which Android does not need. See treeRingShader.ts for the description: rings warped
// by low-frequency noise, earlywood to latewood, grain, rays, three cracks, a fissured bark, a
// two-layer drop shadow and a faint full-size ghost, premultiplied over the screen. Coordinates
// are dp: the canvas is scaled by the density before the shader is drawn.

internal const val TREE_RING_AGSL = """
uniform float2 center;
uniform float4 rA;    // outer radius of rings 0..3 (pt)
uniform float4 rB;    // outer radius of rings 4..6, pith radius
uniform float4 look;  // selected ring, glow amount, bark width, unit scale (pt per 161 pt slice)
uniform float2 full;  // full-grown wood radius and bark width (the faint ghost slice)

float hash(float2 p) {
  p = fract(p * float2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}

float vnoise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  float2 u = f * f * (3.0 - 2.0 * f);
  float a = hash(i);
  float b = hash(i + float2(1.0, 0.0));
  float c = hash(i + float2(0.0, 1.0));
  float d = hash(i + float2(1.0, 1.0));
  return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(float2 p) {
  float s = 0.0;
  float a = 0.5;
  for (int i = 0; i < 4; i++) {
    s += a * vnoise(p);
    p = p * 2.03 + float2(17.1, 9.2);
    a *= 0.5;
  }
  return s;
}

float ringR(int i) {
  if (i == 0) { return rA.x; }
  if (i == 1) { return rA.y; }
  if (i == 2) { return rA.z; }
  if (i == 3) { return rA.w; }
  if (i == 4) { return rB.x; }
  if (i == 5) { return rB.y; }
  return rB.z;
}

// Distance (pt) from a radial crack that starts at the bark and runs in to 'reach' of the radius.
float crack(float2 d, float r, float2 cd, float outer, float reach, float u, float seed) {
  float along = dot(d, cd);
  if (along < outer * reach) { return 99.0; }
  float side = d.x * cd.y - d.y * cd.x + (vnoise(float2(along * 0.07 / u, seed)) - 0.5) * 5.0 * u;
  float width = 1.9 * u * smoothstep(outer * reach, outer, along) * smoothstep(40.0 * u, 120.0 * u, outer);
  if (width < 0.05) { return 99.0; }
  return abs(side) - width;
}


half4 main(float2 p) {
  float u = look.w;
  float2 d = p - center;
  float r = length(d);
  float2 dir = d / max(r, 0.001);
  float outer = rB.z;
  float pith = rB.w;
  float barkW = look.z;

  // Organic wander of the rings and the outline.
  float wob = (fbm(dir * 1.6 + 3.1) - 0.5) * 7.0 * u + (fbm(d * (0.018 / u)) - 0.5) * 3.0 * u;
  float near = smoothstep(0.0, 26.0 * u, r);
  float rr = r + wob * near * clamp(outer / (110.0 * u), 0.25, 1.0);
  float edgeNoise = 0.66 + 0.45 * fbm(dir * 5.0 + 7.0) + 0.25 * vnoise(dir * 40.0);
  float barkOuter = outer + barkW * edgeNoise;

  // Drop shadow of the full-size slice (it never changes): a tight contact shadow and a soft
  // ambient one, both offset down.
  float edge = full.x + full.y;
  float s1 = length(p - center - float2(0.0, 2.0 * u)) + wob;
  float s2 = length(p - center - float2(0.0, 12.0 * u)) + wob;
  float shadowA = 0.34 * (1.0 - smoothstep(edge - 3.0 * u, edge + 4.0 * u, s1))
                + 0.30 * (1.0 - smoothstep(edge - 14.0 * u, edge + 30.0 * u, s2));
  float3 shadowC = float3(0.045, 0.028, 0.02);

  // The ghost: the full-size slice at low strength (bark silhouette, pale end grain), so the hero
  // is never empty while the rings regrow from the pith.
  float rg = r + wob * near;
  float ghostCover = 1.0 - smoothstep(full.x + full.y * edgeNoise - 0.7, full.x + full.y * edgeNoise + 0.7, rg);
  float3 ghostC = float3(0.72, 0.58, 0.42) * (0.92 + 0.08 * sin(rg * (2.3 / u) + 6.0 * fbm(d * (0.03 / u))));
  float ghostA = 0.35;
  if (rg > full.x) {
    ghostC = float3(0.24, 0.15, 0.09) * (0.75 + 0.5 * vnoise(dir * 48.0 + rg * 0.18));
    ghostA = 0.6;
  }
  ghostA *= ghostCover;
  float3 under = ghostC * ghostA + shadowC * shadowA * (1.0 - ghostA);
  float underA = ghostA + shadowA * (1.0 - ghostA);

  float cover = 1.0 - smoothstep(barkOuter - 0.7, barkOuter + 0.7, rr);
  if (cover <= 0.0) {
    return half4(half3(under), half(underA));
  }

  float3 col;
  float ringIndex = -1.0;
  float lo = pith;
  float hi = outer;

  if (rr > outer) {
    // Bark: fissures run across the ring (noise that varies with the angle), plates between them.
    float b = (rr - outer) / max(barkW, 0.001);
    float fis = fbm(dir * 17.0 + float2(rr * 0.03, fbm(dir * 6.0) * 2.0));
    float plate = fbm(dir * 48.0 + rr * 0.18);
    col = mix(float3(0.29, 0.18, 0.11), float3(0.20, 0.12, 0.075), b);
    col *= 0.72 + 0.5 * plate;
    col *= mix(0.45, 1.0, smoothstep(0.32, 0.5, fis));
    // Inner bark (cinnamon) and the bright cambium line against the wood.
    col = mix(col, float3(0.64, 0.42, 0.27), (1.0 - smoothstep(0.0, 3.0 * u, rr - outer)) * 0.85);
    col = mix(col, float3(0.92, 0.77, 0.55), 1.0 - smoothstep(0.0, 1.0 * u, rr - outer));
    // A rounded top edge catches the light, the bottom edge falls into shade.
    col *= 0.92 + 0.22 * (-dir.y) * (1.0 - b);
  } else {
    bool found = false;
    float prev = pith;
    for (int i = 0; i < 7; i++) {
      float ri = ringR(i);
      if (!found && rr < ri) {
        found = true;
        ringIndex = float(i);
        lo = prev;
        hi = ri;
      }
      prev = ri;
    }
    if (!found) { ringIndex = 6.0; lo = prev; hi = outer; }
    float f = clamp((rr - lo) / max(hi - lo, 0.001), 0.0, 1.0);

    // Heartwood inside, sapwood outside.
    float heart = 1.0 - smoothstep(0.52, 0.7, rr / max(outer, 1.0));
    float3 sap = mix(float3(0.89, 0.74, 0.53), float3(0.84, 0.66, 0.44), f);
    float3 hw = mix(float3(0.69, 0.40, 0.22), float3(0.60, 0.33, 0.18), f);
    col = mix(sap, hw, heart);

    float late = smoothstep(0.62, 0.98, f);
    float line = 1.0 - smoothstep(0.0, 1.1 * u, hi - rr);
    float warp = fbm(d * (0.03 / u));
    float grain = 0.5 + 0.5 * sin(rr * (2.3 / u) + 6.0 * warp);
    float grain2 = 0.5 + 0.5 * sin(rr * (0.9 / u) + 3.0 * warp + ringIndex * 1.7);
    float fine = smoothstep(0.78, 1.0, grain) * 0.13 + smoothstep(0.7, 1.0, grain2) * 0.08;
    col *= 0.95 + 0.1 * hash(float2(ringIndex, 3.7));
    float ray = smoothstep(0.9, 1.0, vnoise(dir * 95.0)) * 0.07;
    col *= 1.0 - 0.26 * late;
    col *= 1.0 - 0.5 * line;
    col *= 1.0 - fine;
    col += ray;

    // The pith: a small dark knot.
    float pithMask = 1.0 - smoothstep(pith - 1.2 * u, pith + 0.6 * u, rr);
    col = mix(col, float3(0.36, 0.2, 0.1), pithMask);

    // Selection: the chosen year lights up amber, its two boundaries glow, the others dim.
    float w = clamp(1.0 - abs(ringIndex - look.x), 0.0, 1.0) * look.y;
    float loLine = 1.0 - smoothstep(0.0, 1.3 * u, rr - lo);
    float hiLine = 1.0 - smoothstep(0.0, 1.3 * u, hi - rr);
    col = mix(col, col * 1.22 + float3(0.2, 0.11, 0.02), 0.6 * w);
    col += float3(1.0, 0.74, 0.36) * (loLine + hiLine) * w * 0.55;
    col *= 1.0 - 0.2 * look.y * (1.0 - w);
  }

  // Drying cracks (through wood and bark).
  float c = min(crack(d, r, float2(0.866, -0.5), outer, 0.48, u, 1.0),
            min(crack(d, r, float2(-0.94, 0.34), outer, 0.62, u, 4.0),
                crack(d, r, float2(0.17, 0.985), outer, 0.7, u, 9.0)));
  col = mix(col, float3(0.17, 0.09, 0.05), 1.0 - smoothstep(-0.3 * u, 0.5 * u, c));
  col *= 1.0 - 0.18 * (1.0 - smoothstep(0.0, 1.6 * u, c));

  // Saw marks, grain noise and a broad oiled sheen from the top left.
  col *= 1.0 - 0.035 * (0.5 + 0.5 * sin(dot(p, float2(0.8, 0.6)) * (0.9 / u) + 2.0 * vnoise(p * 0.05)));
  col *= 0.965 + 0.07 * vnoise(p * (0.9 / u));
  float2 q = (d - float2(-0.38, -0.46) * outer) / max(outer * 1.9, 1.0);
  col *= 0.88 + 0.2 * (1.0 - clamp(length(q), 0.0, 1.0));
  col *= 0.86 + 0.14 * smoothstep(outer + barkW * 0.9, outer * 0.75, rr);

  // Wood over the ghost and the shadow, premultiplied.
  float a = cover + underA * (1.0 - cover);
  float3 outc = col * cover + under * (1.0 - cover);
  return half4(half3(outc), half(a));
}
"""
