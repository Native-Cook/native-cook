package com.ngocdevv.nativecook.blocks.cards.filmnegativecard

/*
 The negative strip in one pass, in the strip's own coordinates (top-left = 0, 0). A port of
 filmNegativeShader.ts with the same maths; the Display P3 conversion of the iOS version is gone
 (Android draws in sRGB).

 A 35 mm strip: an orange-masked base with two rows of perforations (transparent, so the desk or
 the light table shows through them) and three frames — frame 7 in the middle, the ends of 6 and
 8 at the sides. Transmission = orange mask × (0.07 + 0.93 × (1 − scene)^1.15). Off the light the
 strip shows 55 % of that plus a gloss band that slides with the strip; over the light it is
 inverted back into the positive (1 − transmission / mask, a 40 % S-curve), with a warm halo on
 the turning line. Inside the loupe the positive is magnified 1.8× with a slight barrel, a 1 px
 colour split and a darker rim.

 Uniforms (dp, strip space): strip (0, 0, w, h); frame (x, y, w, h) of frame 7; gap; light (the
 panel's rect); loupe (cx, cy, radius, opacity); perf (pitch, hole w, hole h, x phase); rows
 (top / bottom hole centre y); sheen (the strip's screen y); imgSize (photo pixels); roll (1 for
 the dimmed strip of other frames behind it).
 */
internal const val FILM_NEGATIVE_AGSL = """
uniform shader photo;
uniform float2 imgSize;
uniform float4 strip;
uniform float4 frame;
uniform float gap;
uniform float4 light;
uniform float4 loupe;
uniform float4 perf;
uniform float2 rows;
uniform float sheen;
uniform float roll;

float sdBox(float2 p, float2 c, float2 b, float r) {
  float2 q = abs(p - c) - b + r;
  return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}

float hash(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }

float3 scene(float2 uv, float i) {
  float2 q = uv * imgSize;
  if (i < -0.5) q = uv * imgSize * 0.78 + float2(150.0, 90.0);
  if (i > 0.5) q = uv * imgSize * 0.82 + float2(10.0, 70.0);
  if (roll > 0.5) q = uv * imgSize * 0.62 + float2(60.0 + (i + 1.0) * 110.0, 40.0 + (i + 1.0) * 30.0);
  return photo.eval(q).rgb;
}

float3 negative(float2 p, float3 base) {
  float pitchF = frame.z + gap;
  float i = floor((p.x - frame.x + gap * 0.5) / pitchF);
  i = clamp(i, -1.0, 1.0);
  float2 o = float2(frame.x + i * pitchF, frame.y);
  float2 l = p - o;
  float inFrame = 1.0 - smoothstep(-0.6, 0.6, sdBox(l, frame.zw * 0.5, frame.zw * 0.5, 2.5));
  float3 s = scene(clamp(l / frame.zw, 0.0, 1.0), i);
  float3 dens = pow(clamp(1.0 - s, 0.0, 1.0), float3(1.15));
  float3 exposed = base * (0.07 + 0.93 * dens);
  float3 clear = base * (0.95 + 0.04 * hash(floor(p * 0.5)));
  return mix(clear, exposed, inFrame);
}

float3 positive(float3 neg, float3 base) {
  float3 c = clamp(1.0 - neg / base, 0.0, 1.0);
  c = clamp((c - 0.03) / 0.95, 0.0, 1.0);
  c = mix(c, c * c * (3.0 - 2.0 * c), 0.4);
  return c * float3(1.03, 1.0, 0.965);
}

half4 main(float2 p) {
  float2 sc = strip.zw * 0.5;
  float a = 1.0 - smoothstep(-0.6, 0.6, sdBox(p, sc, sc, 4.0));
  if (a <= 0.0) return half4(0.0);
  float hx = mod(p.x - perf.w, perf.x) - perf.x * 0.5;
  float hTop = sdBox(float2(hx, p.y), float2(0.0, rows.x), perf.yz * 0.5, 2.4);
  float hBot = sdBox(float2(hx, p.y), float2(0.0, rows.y), perf.yz * 0.5, 2.4);
  float hole = min(hTop, hBot);
  a *= smoothstep(-0.6, 0.6, hole);
  if (a <= 0.0) return half4(0.0);

  float3 base = float3(0.90, 0.54, 0.30);
  float3 neg = negative(p, base);

  float band = p.x * 0.55 + p.y - (sheen * 0.9 - 120.0);
  float gloss = exp(-band * band / 1800.0) * 0.16 + exp(-band * band / 160.0) * 0.07;
  float3 off = neg * 0.55 + float3(1.0, 0.94, 0.86) * gloss;
  off *= 0.86 + 0.14 * smoothstep(0.0, 2.5, hole);

  float2 lc = light.xy + light.zw * 0.5;
  float sd = sdBox(p, lc, light.zw * 0.5, 8.0);
  float lit = 1.0 - smoothstep(-18.0, 6.0, sd);
  float2 rel = (p - lc) / (light.zw * 0.5);
  float fall = 0.9 + 0.1 * (1.0 - clamp(dot(rel, rel) * 0.5, 0.0, 1.0));
  float3 pos = positive(neg, base) * fall;

  float3 col = mix(off, pos, lit);
  col += float3(1.0, 0.72, 0.42) * (lit * (1.0 - lit) * 4.0) * 0.20;

  if (loupe.w > 0.001) {
    float2 d = p - loupe.xy;
    float r = length(d) / loupe.z;
    if (r < 1.0) {
      float2 q = loupe.xy + d * (1.0 + 0.14 * r * r) / 1.8;
      float2 e = d / max(length(d), 0.001) * r * r * 1.2;
      float3 cr = positive(negative(q + e, base), base);
      float3 cg = positive(negative(q, base), base);
      float3 cb = positive(negative(q - e, base), base);
      float3 mag = float3(cr.r, cg.g, cb.b) * 1.04;
      mag *= 1.0 - 0.32 * smoothstep(0.72, 1.0, r);
      float spec = smoothstep(0.82, 0.95, r) * (1.0 - smoothstep(0.95, 1.0, r)) * clamp(-d.y / max(length(d), 0.001), 0.0, 1.0);
      mag += float3(spec * 0.25);
      float edge = 1.0 - smoothstep(0.97, 1.0, r);
      col = mix(col, mag, loupe.w * edge);
    }
  }
  return half4(half3(col * a), half(a));
}
"""
