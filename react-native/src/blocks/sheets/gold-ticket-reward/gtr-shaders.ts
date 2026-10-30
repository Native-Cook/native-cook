// Gold Ticket Reward — SkSL runtime effects.
// Skia treats a runtime effect's output as Display P3 on iOS, so the colours written here (sRGB)
// go through toP3() before they are returned; colours sampled from the car image are left alone.

const COMMON = `
float3 toP3(float3 c) {
  float3 l = pow(clamp(c, 0.0, 1.0), float3(2.2));
  float3 m = float3(
    dot(l, float3(0.8225, 0.1774, 0.0)),
    dot(l, float3(0.0332, 0.9669, 0.0)),
    dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(m, float3(1.0 / 2.2));
}
float hash(float2 p) {
  return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}
float vnoise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  f = f * f * (3.0 - 2.0 * f);
  float a = hash(i);
  float b = hash(i + float2(1.0, 0.0));
  float c = hash(i + float2(0.0, 1.0));
  float d = hash(i + float2(1.0, 1.0));
  return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}
`;

// The ticket's gold foil, drawn over the white ticket (clipped to its shape by the canvas).
// `wipe` 0 … 1 moves the pour's leading edge across it, leaning 0.35 of the height; the edge burns
// pale gold for a moment. `sweep` (−1 off) runs one specular band across; `tilt` (−1 … 1) slides
// the broad sheen with the finger. The base is the pin's: pale gold top-left, deep orange in the
// middle, bronze at the right end, with a fine horizontal brush and a 5 pt bevel.
export const FOIL_SKSL = `
uniform float4 rect;
uniform float wipe;
uniform float sweep;
uniform float2 tilt;
${COMMON}
const float3 HI = float3(0.961, 0.839, 0.467);
const float3 MID = float3(0.866, 0.565, 0.176);
const float3 DEEP = float3(0.780, 0.510, 0.170);
const float3 BRONZE = float3(0.478, 0.290, 0.094);
const float3 BURN = float3(1.0, 0.95, 0.80);

half4 main(float2 p) {
  float2 uv = (p - rect.xy) / rect.zw;
  float edge = mix(-0.40, 1.40, wipe);
  float d = (uv.x + (uv.y - 0.5) * 0.35) - edge;
  float inside = 1.0 - smoothstep(-0.012, 0.012, d);
  float lead = (wipe > 0.001 && wipe < 0.999) ? exp(-d * d * 1400.0) : 0.0;
  float a = max(inside, lead);
  if (a <= 0.002) { return half4(0.0); }

  // pale gold at the top-left, saturated orange-gold over the body, bronze down the right end
  float3 col = mix(HI, MID, smoothstep(0.0, 0.62, uv.x * 0.55 + uv.y * 0.9));
  col = mix(col, DEEP, smoothstep(0.55, 1.0, uv.y) * 0.35);
  col = mix(col, BRONZE, smoothstep(0.6, 1.0, uv.x) * smoothstep(0.15, 0.85, uv.y) * 0.92);

  float brush = vnoise(float2(p.x * 0.035, p.y * 1.6)) - 0.5;
  col *= 1.0 + brush * 0.07;

  float sheen = uv.x * 0.85 + uv.y * 0.25 - (0.42 + tilt.x * 0.38 + tilt.y * 0.12);
  col += float3(1.0, 0.92, 0.7) * 0.06 * exp(-sheen * sheen * 16.0);
  if (sweep >= 0.0) {
    float q = uv.x + uv.y * 0.45 - mix(-0.3, 1.75, sweep);
    col += float3(1.0, 0.94, 0.78) * 0.55 * exp(-q * q * 140.0);
  }

  float2 dd = min(p - rect.xy, rect.xy + rect.zw - p);
  float e = min(dd.x, dd.y);
  col *= 0.84 + 0.16 * smoothstep(0.0, 5.0, e);
  col += 0.14 * (1.0 - smoothstep(0.0, 1.4, p.y - rect.y));

  col = mix(col, BURN, lead * (1.0 - inside * 0.4));
  float3 o = toP3(col);
  return half4(half3(o) * a, a);
}
`;

// The cars dissolving from the wheels up (prog 0 whole → 1 gone), the dissolve front burning gold.
// Reversed (prog 1 → 0) they re-materialise top first. At rest `glint` (−1 off) runs a showroom
// light across the paint, brighter on the light parts of the cars.
export const DISSOLVE_SKSL = `
uniform shader image;
uniform float4 rect;
uniform float prog;
uniform float glint;
${COMMON}
const float3 EMBER = float3(1.0, 0.82, 0.42);

half4 main(float2 p) {
  half4 c = image.eval(p);
  if (c.a <= 0.002) { return half4(0.0); }
  float2 uv = (p - rect.xy) / rect.zw;
  float n = vnoise(p * 0.11) * 0.62 + vnoise(p * 0.31) * 0.38;
  float f = (1.0 - uv.y) * 0.62 + n * 0.38;
  float th = mix(-0.08, 1.08, prog);
  float keep = smoothstep(th - 0.012, th + 0.012, f);
  float burn = exp(-pow((f - th) / 0.035, 2.0)) * step(0.001, prog) * step(prog, 0.999);
  float3 glow = toP3(EMBER) * burn * 1.25;
  float a = c.a * max(keep, burn * 0.9);
  half3 rgb = c.rgb * keep + half3(glow) * c.a;
  if (glint >= 0.0) {
    float q = uv.x * 0.9 + uv.y * 0.45 - mix(-0.35, 1.55, glint);
    float lum = dot(float3(c.rgb), float3(0.3, 0.59, 0.11)) / max(c.a, 0.001);
    rgb += half3(toP3(float3(1.0, 0.96, 0.86))) * c.a * exp(-q * q * 90.0) * (0.18 + 0.32 * lum);
  }
  return half4(rgb, a);
}
`;
