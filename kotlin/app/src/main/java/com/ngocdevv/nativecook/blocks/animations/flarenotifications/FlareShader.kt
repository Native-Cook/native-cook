package com.ngocdevv.nativecook.blocks.animations.flarenotifications

// The flame inside one pill, as the reference draws it: a few dozen soft
// elliptical blobs of color, each drifting on its own smooth path. AGSL port
// of flare-shader.ts, line for line; the only addition
// is `density`, because Compose draws in pixels and the RN Skia canvas in dp.
//
// The blobs are evaluated exactly as in the fit: colors are blended in OKLab
// by weight, coverage is 1 - exp(-total weight), and the result is mixed over
// the pill body in sRGB. Smooth by construction: every blob moves on a C1
// spline, nothing thresholds, nothing pops.
//
// Pill-local coordinates: u is the distance in from the trailing edge and v
// the distance down, both in pill heights. `stretch` scales the flame out of
// the cap: 0 at ignition, 1 at rest, ~1.6 when stoked. `ember` is how far in
// from the cap (pill heights) a read note's flame still reaches.

const val BLOB_COUNT = 32

val FLARE_AGSL = """
uniform float density;
uniform float2 size;
uniform float stretch;
uniform float heat;
uniform float cool;
uniform float ember;
uniform float guard;
uniform float3 body;
uniform float4 geo[$BLOB_COUNT];
uniform float4 col[$BLOB_COUNT];
uniform float2 rot[$BLOB_COUNT];

float hash(float2 p) {
  p = fract(p * float2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}

float3 oklabToSrgb(float3 c) {
  float l_ = c.x + 0.3963377774 * c.y + 0.2158037573 * c.z;
  float m_ = c.x - 0.1055613458 * c.y - 0.0638541728 * c.z;
  float s_ = c.x - 0.0894841775 * c.y - 1.2914855480 * c.z;
  float l = l_ * l_ * l_;
  float m = m_ * m_ * m_;
  float s = s_ * s_ * s_;
  float3 lin = float3(
     4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
    -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
    -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s);
  lin = clamp(lin, 0.0, 1.0);
  float3 lo = lin * 12.92;
  float3 hi = 1.055 * pow(lin, float3(1.0 / 2.4)) - 0.055;
  return mix(lo, hi, step(float3(0.0031308), lin));
}

half4 main(float2 fragCoord) {
  // Compose hands the shader pixels; the RN Skia canvas works in dp.
  float2 xy = fragCoord / density;
  float h = size.y;
  float u = (size.x - xy.x) / h;
  float v = xy.y / h;
  // Half-LSB dither: long fades into the body otherwise step in 8-bit bands.
  float dither = (hash(xy) - 0.5) / 255.0;
  float k = max(stretch, 0.0);
  if (u > 2.9 * max(k, 1.0) + 0.4) return half4(half3(body + dither), 1.0);

  float total = 0.0;
  float3 acc = float3(0.0);
  float spread = mix(1.0, k, 0.5);
  for (int i = 0; i < $BLOB_COUNT; i++) {
    float4 g = geo[i];
    float2 d = float2(u - g.x * k, v - g.y);
    float2 r = rot[i];
    float x = (d.x * r.x - d.y * r.y) / (g.z * spread);
    float y = (d.x * r.y + d.y * r.x) / g.w;
    float w = col[i].w * exp(-0.5 * (x * x + y * y));
    total += w;
    acc += w * col[i].xyz;
  }
  float3 lab = acc / (total + 1e-4);
  float cover = (1.0 - exp(-total)) * clamp(k * 2.5, 0.0, 1.0);

  // Ignition flash: the cap burns towards white as it catches.
  float rim = 1.0 - smoothstep(0.0, 0.9, u);
  float wh = clamp(max(heat - 1.0, 0.0) * 0.45 * rim * clamp(k, 0.0, 1.0), 0.0, 1.0);
  lab.x += wh * (0.995 - lab.x);
  lab.yz *= 1.0 - wh * 0.85;
  // Read: the fire cools to an ember, the brightest part at the cap. The
  // flame is cut back softly rather than squeezed: squeezing piles the dark
  // trailing blobs onto the cap and the blend turns to mauve mud, and so
  // does draining chroma or coverage on the dark pill.
  float reachU = mix(6.0, ember, cool);
  cover *= 1.0 - smoothstep(reachU - 0.4, reachU + 0.4, u);
  lab.x *= 1.0 - 0.05 * cool;
  // Keep the dye faint under the copy; the reference leaves ~0.45 h clear.
  cover *= mix(0.12, 1.0, smoothstep(guard - 0.15 * h, guard + 0.35 * h, xy.x));

  float3 c = mix(body, oklabToSrgb(lab), cover);
  return half4(half3(c + dither), 1.0);
}
"""
