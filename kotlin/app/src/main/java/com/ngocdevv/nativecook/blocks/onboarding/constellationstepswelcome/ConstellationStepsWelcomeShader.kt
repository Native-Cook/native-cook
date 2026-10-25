package com.ngocdevv.nativecook.blocks.onboarding.constellationstepswelcome

// The night sky in one AGSL pass (API 33+), the React Native SkSL unchanged but for the Display P3
// conversion, which Android does not need. See constellationShader.ts: a navy gradient with a violet
// horizon glow and a vignette in screen space; in sky space (turned round the pole by -theta) the
// Milky Way as fbm dust with a dark rift, and two layers of hashed, twinkling stars. Coordinates are
// dp: the canvas is scaled by the density before the shader is drawn.

internal const val CONSTELLATION_SKY_AGSL = """
uniform float2 res;
uniform float2 pole;
uniform float theta;
uniform float phase;  // 2 pi t / LOOP

float hash(float2 p) {
  p = fract(p * float2(127.31, 311.71));
  p += dot(p, p + 34.17);
  return fract(p.x * p.y);
}

float vnoise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x), mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
}

float fbm(float2 p) {
  float s = 0.0;
  float a = 0.5;
  for (int i = 0; i < 3; i++) {
    s += a * vnoise(p);
    p = p * 2.07 + float2(13.7, 5.1);
    a *= 0.5;
  }
  return s;
}

// One star per cell of 'size' pt: returns its light at q.
float3 starLayer(float2 q, float size, float density, float gain) {
  float2 cell = floor(q / size);
  float2 f = fract(q / size);
  float on = step(1.0 - density, hash(cell + 1.13));
  float2 pos = 0.22 + 0.56 * float2(hash(cell + 2.71), hash(cell + 3.97));
  float r = length(f - pos) * size;
  float mag = pow(hash(cell + 7.31), 5.0);
  float k = floor(1.0 + hash(cell + 4.19) * 4.0);
  float tw = 0.72 + 0.28 * sin(phase * k + hash(cell + 5.53) * 6.2832);
  float rad = 0.45 + 1.1 * mag;
  float core = exp(-(r * r) / (rad * rad));
  float halo = mag * 0.35 * exp(-r / (rad * 3.5));
  float3 tint = mix(float3(0.72, 0.82, 1.0), float3(1.0, 0.88, 0.72), hash(cell + 9.07));
  return tint * on * (core * (0.35 + 1.5 * mag) + halo) * tw * gain;
}


half4 main(float2 p) {
  float v = p.y / res.y;
  float3 col = mix(float3(0.022, 0.032, 0.09), float3(0.045, 0.066, 0.16), smoothstep(0.0, 0.75, v));
  col = mix(col, float3(0.15, 0.11, 0.24), smoothstep(0.7, 1.05, v));

  float2 d = p - pole;
  float c = cos(-theta);
  float s = sin(-theta);
  float2 q = float2(c * d.x - s * d.y, s * d.x + c * d.y);

  // The Milky Way: a band across the sky, dust, a dark rift down its middle.
  float2 bd = normalize(float2(0.62, 0.78));
  float2 bn = float2(-bd.y, bd.x);
  float along = dot(q, bd);
  float dist = dot(q - float2(-30.0, 470.0), bn) + 26.0 * (vnoise(float2(along * 0.006, 2.3)) - 0.5);
  float width = 118.0;
  float band = exp(-(dist * dist) / (width * width));
  float dust = fbm(q * 0.0065 + 1.7);
  float fine = fbm(q * 0.021 + 6.1);
  float rift = exp(-pow((dist + 8.0) / (width * 0.22), 2.0)) * smoothstep(0.38, 0.62, fbm(q * 0.011 + 9.0));
  float milky = band * (0.5 * dust + 0.5 * fine) * (1.0 - 0.7 * rift);
  col += float3(0.34, 0.38, 0.58) * milky * 0.5;
  col += float3(0.5, 0.36, 0.34) * pow(dust, 3.0) * band * 0.22;

  col += starLayer(q, 21.0, 0.62, 1.0);
  col += starLayer(q + float2(7.3, 3.1), 8.5, 0.5, 0.55 * band + 0.08);

  float2 vq = (p / res - float2(0.5, 0.42)) * float2(1.0, 0.8);
  col *= 1.0 - 0.55 * smoothstep(0.32, 0.8, length(vq));

  return half4(half3(col), 1.0);
}
"""
