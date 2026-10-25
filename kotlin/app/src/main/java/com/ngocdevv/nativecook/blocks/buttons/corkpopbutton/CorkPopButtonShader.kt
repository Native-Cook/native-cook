package com.ngocdevv.nativecook.blocks.buttons.corkpopbutton

/*
 Cork Pop Button — the foam, ported from corkShader.ts (the maths is the same; the P3 conversion
 is dropped, Android draws sRGB). Sixteen balls make one metaball field (sum of r^2 / d^2); the
 surface is where it passes 1, antialiased by its distance estimate (f - 1) / |grad f|. Inside,
 the field is read as a dome: the normal tilts out towards the edges, lit from the upper left with
 a soft specular, cream where the foam is deep and champagne gold where it thins. Small bubbles
 come from a jittered cell grid. Output is premultiplied, so the canvas stays clear around the foam.
 The balls are in dp (the canvas is scaled by the density), so p is in dp too.
*/

internal const val CORK_FOAM_AGSL = """
uniform float3 balls[16];

float h21(float2 p) {
  return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}

half4 main(float2 p) {
  float f = 0.0;
  float2 grad = float2(0.0);
  for (int i = 0; i < 16; i++) {
    float3 b = balls[i];
    float2 d = p - b.xy;
    float d2 = dot(d, d) + 1.0;
    float k = b.z * b.z / d2;
    f += k;
    grad += (-2.0 * k / d2) * d;
  }
  if (f < 0.55) {
    return half4(0.0);
  }
  float g = max(length(grad), 1e-4);
  float a = clamp((f - 1.0) / g + 0.5, 0.0, 1.0);
  if (a <= 0.0) {
    return half4(0.0);
  }
  float depth = 1.0 - exp(-(f - 1.0) * 1.3);
  float2 outward = -grad / g;
  float3 n = normalize(float3(outward * (1.0 - depth) * 1.5, 1.0));
  float3 L = normalize(float3(-0.45, -0.75, 0.6));
  float diff = clamp(dot(n, L), 0.0, 1.0);
  float spec = pow(max(dot(reflect(-L, n), float3(0.0, 0.0, 1.0)), 0.0), 18.0);

  float3 cream = float3(0.975, 0.95, 0.885);
  float3 gold = float3(0.93, 0.80, 0.52);
  float3 col = mix(gold, cream, smoothstep(1.0, 2.4, f));
  col *= 0.82 + 0.28 * diff;
  col += float3(1.0, 0.98, 0.93) * spec * 0.45;
  // Rim light where the foam is thin (light through it).
  col += float3(0.25, 0.18, 0.05) * (1.0 - smoothstep(1.0, 1.35, f));

  // Bubbles: two jittered grids, only some cells hold one.
  float2 q = p / 7.0;
  float2 cell = floor(q);
  float2 lp = fract(q) - 0.5 - (float2(h21(cell), h21(cell + 17.3)) - 0.5) * 0.4;
  float rb = (0.12 + 0.18 * h21(cell + 5.1)) * step(0.45, h21(cell + 9.7));
  float d = length(lp);
  float inside = 1.0 - smoothstep(rb - 0.04, rb, d);
  float2 q2 = p / 3.6 + 11.0;
  float2 c2 = floor(q2);
  float2 l2 = fract(q2) - 0.5 - (float2(h21(c2 + 3.0), h21(c2 + 8.1)) - 0.5) * 0.4;
  float r2 = (0.14 + 0.14 * h21(c2 + 1.7)) * step(0.55, h21(c2 + 4.4));
  float inside2 = 1.0 - smoothstep(r2 - 0.06, r2, length(l2));
  float glint = 1.0 - smoothstep(0.0, 0.06, length(lp + float2(rb * 0.4, rb * 0.45)));
  col *= 1.0 - inside * 0.07 - inside2 * 0.04;
  col += glint * inside * 0.18;

  col = clamp(col, 0.0, 1.0);
  return half4(half3(col * a), half(a));
}
"""
