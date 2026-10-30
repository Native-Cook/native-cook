// Frost Card Controls — the ice, as one SkSL runtime effect in the card's own space (pt).
//
// Frost spreads from a point as a ragged front (fbm-displaced distance), filling the card with
// Voronoi crystal plates, fern-like feathers along their edges and bright growing tips; melting eats
// it away in patches (a noise threshold rising) with wet, darker edges. The effect returns
// premultiplied colour; with `maskOnly` it returns the coverage alone, used to fade in the frosted
// (blurred) copy of the card under the ice. Skia treats a runtime effect's output as Display P3 on
// iOS, so the white-blue ice goes through toP3().

export const FROST_SKSL = `
uniform float2 size;
uniform float2 center;
uniform float grow;     // 0 … 1.2
uniform float melt;     // 0 … 1
uniform float time;
uniform float maskOnly;

float3 toP3(float3 c) {
  float3 l = pow(clamp(c, 0.0, 1.0), float3(2.2));
  float3 m = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(m, float3(1.0 / 2.2));
}
float h21(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float2 h22(float2 p) { return fract(sin(float2(dot(p, float2(127.1, 311.7)), dot(p, float2(269.5, 183.3)))) * 43758.5453); }
float vnoise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  f = f * f * (3.0 - 2.0 * f);
  return mix(mix(h21(i), h21(i + float2(1, 0)), f.x), mix(h21(i + float2(0, 1)), h21(i + float2(1, 1)), f.x), f.y);
}
float fbm(float2 p) {
  float v = 0.0;
  float a = 0.5;
  for (int k = 0; k < 4; k++) { v += a * vnoise(p); p = p * 2.03 + float2(17.0, 9.0); a *= 0.5; }
  return v;
}
// distance to the nearest Voronoi edge (crystal plate borders)
float vedge(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  float d1 = 8.0;
  float d2 = 8.0;
  for (int y = -1; y <= 1; y++) {
    for (int x = -1; x <= 1; x++) {
      float2 g = float2(float(x), float(y));
      float2 o = h22(i + g);
      float d = length(g + o - f);
      if (d < d1) { d2 = d1; d1 = d; } else if (d < d2) { d2 = d; }
    }
  }
  return d2 - d1;
}

half4 main(float2 p) {
  float reach = length(size) * 1.15;
  float n = fbm(p * 0.035);
  float d = length(p - center) + (n - 0.5) * 70.0;
  float front = grow * reach - d;
  float cover = smoothstep(0.0, 22.0, front);
  // melting: patches clear first where the noise is low, edges stay wet
  float m2 = fbm(p * 0.05 + float2(3.1, 7.7));
  float keep = smoothstep(melt * 1.25 - 0.18, melt * 1.25, m2);
  cover *= mix(1.0, keep, step(0.0001, melt));
  if (maskOnly > 0.5) return half4(0.0, 0.0, 0.0, cover);
  float e = vedge(p * 0.045);
  float plates = smoothstep(0.0, 0.08, e);
  float feathers = fbm(p * 0.22 + float2(e * 6.0, 0.0));
  float lines = 1.0 - smoothstep(0.0, 0.05, e);
  // bright growing tips along the front, and a few glints that drift
  float tip = smoothstep(26.0, 0.0, abs(front - 11.0)) * step(0.0, front) * (1.0 - melt);
  float glint = pow(max(0.0, sin((p.x * 0.07 + p.y * 0.05) - time * 1.3)), 40.0) * 0.6;
  float a = cover * (0.34 + 0.2 * feathers + 0.46 * lines) + tip * 0.55 + glint * cover * 0.5;
  float wet = (1.0 - keep) * step(0.0001, melt) * smoothstep(0.0, 0.25, cover);
  float3 icy = mix(float3(0.80, 0.90, 1.0), float3(1.0, 1.0, 1.0), 0.45 * plates + 0.4 * feathers + tip);
  icy = mix(icy, float3(0.62, 0.78, 0.95), wet * 0.6);
  a = clamp(a, 0.0, 0.92);
  return half4(half3(toP3(icy) * a), half(a));
}
`;
