/**
 * Pit Stop Review — the two per-frame effects that are drawn by SkSL instead of per-frame Skia objects.
 *
 * SMOKE_SKSL (hero, design pt): up to 32 tyre-smoke puffs `puffs[i] = (x, y, radius, alpha)`, soft discs with a
 * wispy edge, composited as one light-grey layer (alpha = 1 − e^(−Σ)). A puff with alpha 0 is skipped.
 *
 * LAMPS_SKSL (window pt): up to 20 lamp sprites `lamp[i] = (x, y, radius, morph)` with `look[i] = (alpha, glow,
 * stroke px, 0)`. morph 0 = a lit red start light (bloom, red lens, white-hot centre); morph 1 = a white outline
 * five-point star; between them the shape is a signed-distance blend of circle and star (iq's sdStar5), so a light
 * becomes a star without a path being rebuilt. The comet trail is the same sprite at earlier positions.
 *
 * Both return premultiplied colour converted from sRGB to Display P3 (Skia treats RuntimeEffect output as P3 on iOS).
 * Identifiers avoid WGSL builtin and reserved names.
 */
const TO_P3 = `
float3 toP3(float3 c) {
  float3 l = pow(clamp(c, 0.0, 1.0), float3(2.2));
  float3 m = float3(
    dot(l, float3(0.8225, 0.1774, 0.0)),
    dot(l, float3(0.0332, 0.9669, 0.0)),
    dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(m, float3(1.0 / 2.2));
}`;

export const SMOKE_SKSL = `
uniform float4 puffs[32];
${TO_P3}
float hs(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }
float vn(float2 q) {
  float2 i = floor(q);
  float2 f = fract(q);
  f = f * f * (3.0 - 2.0 * f);
  return mix(mix(hs(i), hs(i + float2(1.0, 0.0)), f.x), mix(hs(i + float2(0.0, 1.0)), hs(i + float2(1.0, 1.0)), f.x), f.y);
}
half4 main(float2 p) {
  float sum = 0.0;
  for (int i = 0; i < 32; i++) {
    float4 q = puffs[i];
    if (q.w > 0.002) {
      float d = length(p - q.xy) / max(q.z, 0.5);
      if (d < 1.0) {
        float wisp = 0.75 + 0.5 * vn(p * 0.09 + q.xy * 0.013);
        float f = 1.0 - d;
        sum += q.w * f * f * (3.0 - 2.0 * f) * wisp;
      }
    }
  }
  float a = 1.0 - exp(-sum * 1.6);
  return half4(toP3(float3(0.91, 0.90, 0.93)) * a, a);
}
`;

export const LAMPS_SKSL = `
uniform float4 lamp[20];
uniform float4 look[20];
${TO_P3}
float starDist(float2 p, float r, float rf) {
  const float2 k1 = float2(0.809016994375, -0.587785252292);
  const float2 k2 = float2(-0.809016994375, -0.587785252292);
  p.x = abs(p.x);
  p -= 2.0 * max(dot(k1, p), 0.0) * k1;
  p -= 2.0 * max(dot(k2, p), 0.0) * k2;
  p.x = abs(p.x);
  p.y -= r;
  float2 ba = rf * float2(-k1.y, k1.x) - float2(0.0, 1.0);
  float h = clamp(dot(p, ba) / dot(ba, ba), 0.0, r);
  return length(p - ba * h) * sign(p.y * ba.x - p.x * ba.y);
}
half4 main(float2 xy) {
  float3 col = float3(0.0);
  float alpha = 0.0;
  for (int i = 0; i < 20; i++) {
    float4 L = lamp[i];
    float4 K = look[i];
    if (K.x > 0.002) {
      float2 p = xy - L.xy;
      float r = L.z;
      float reach = r * 3.2;
      if (abs(p.x) < reach && abs(p.y) < reach) {
        float m = L.w;
        float dc = length(p) - r;
        float ds = starDist(float2(p.x, -p.y), r, 0.42);
        float d = mix(dc, ds, m);
        // Red lamp: lens + white-hot core + bloom, fading out as it becomes a star.
        float lens = 1.0 - smoothstep(-0.8, 0.8, d);
        float core = exp(-dot(p, p) / (r * r * 0.18));
        float bloom = exp(-max(d, 0.0) / (r * 0.9)) * K.y;
        float lampA = (1.0 - m) * K.x;
        float3 lampC = mix(float3(0.77, 0.09, 0.05), float3(1.0, 0.23, 0.19), core) + float3(0.0, 0.6, 0.55) * core * core;
        // White outline star.
        float stroke = (1.0 - smoothstep(K.z * 0.5 - 0.6, K.z * 0.5 + 0.6, abs(d))) * m * K.x;
        float a1 = clamp(lens * lampA, 0.0, 1.0);
        float a2 = clamp(bloom * lampA * 0.55, 0.0, 1.0) * (1.0 - lens);
        col = col * (1.0 - a1) + lampC * a1;
        alpha = alpha + a1 * (1.0 - alpha);
        col = col + float3(1.0, 0.19, 0.12) * a2;
        alpha = alpha + a2 * (1.0 - alpha);
        col = col * (1.0 - stroke) + float3(1.0) * stroke;
        alpha = alpha + stroke * (1.0 - alpha);
      }
    }
  }
  float3 c = alpha > 0.0001 ? col / alpha : float3(0.0);
  return half4(toP3(min(c, float3(1.0))) * alpha, alpha);
}
`;
