/**
 * Paint Pour Splash — the flow-map shader (a port of the designer's flow.frag.glsl to SkSL).
 *
 * The approved still fills the screen and never changes; only its pixels flow. Mixing is in linear
 * light (c^2.2). The masks come from how colourful a pixel is: saturated paint in the stream column
 * above the pool line flows down along the stream's lean with the classic two-phase flow-map
 * cross-fade (each phase lasts 2 s and is weighted 0 when it wraps, so the 4 s loop is seamless),
 * with a small wobble that moves with each phase; the pool and the splash ripple outward once a
 * second. Four samples per pixel: the base (for the masks), the two flow phases and the ripple.
 *
 * Uniforms (canvas points): fit = where the image is drawn (x, y, width, height); img = the image's
 * size in pixels; time = loop time in seconds (0…4); p3 = 1 would convert the sRGB result to Display
 * P3, which this block does not need (the image is already in the destination's colour space).
 */
export const PAINT_POUR_SKSL = `
uniform shader image;
uniform float4 fit;
uniform float2 img;
uniform float time;
uniform float p3;

float3 PPS_lin(float3 c) { return pow(max(c, float3(0.0)), float3(2.2)); }

float3 PPS_tex(float2 uv) {
  float2 q = clamp(uv, float2(0.0), float2(1.0)) * img;
  q = clamp(q, float2(0.5), img - float2(0.5));
  return PPS_lin(image.eval(q).rgb);
}

float PPS_hash(float2 p) {
  p = fract(p * float2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}

float PPS_noise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  f = f * f * (3.0 - 2.0 * f);
  return mix(mix(PPS_hash(i), PPS_hash(i + float2(1.0, 0.0)), f.x),
             mix(PPS_hash(i + float2(0.0, 1.0)), PPS_hash(i + float2(1.0, 1.0)), f.x), f.y);
}

float3 PPS_p3(float3 s) {
  float3 l = mix(s / 12.92, pow((s + 0.055) / 1.055, float3(2.4)), step(float3(0.04045), s));
  l = float3(dot(l, float3(0.822592, 0.177534, 0.0)), dot(l, float3(0.033199, 0.966783, 0.0)), dot(l, float3(0.017085, 0.072396, 0.910302)));
  return mix(l * 12.92, 1.055 * pow(max(l, float3(0.0)), float3(1.0 / 2.4)) - 0.055, step(float3(0.0031308), l));
}

half4 main(float2 pos) {
  float2 uv = (pos - fit.xy) / fit.zw;
  float3 base = PPS_tex(uv);
  float sat = max(max(base.r, base.g), base.b) - min(min(base.r, base.g), base.b);
  float stream = smoothstep(0.12, 0.3, sat) * (1.0 - smoothstep(0.78, 0.83, uv.y)) * smoothstep(0.12, 0.2, uv.y);
  float pool = smoothstep(0.12, 0.3, sat) * smoothstep(0.78, 0.84, uv.y);

  float t = time / 4.0;
  float ph0 = fract(t * 2.0);
  float ph1 = fract(t * 2.0 + 0.5);
  float w0 = 1.0 - abs(2.0 * ph0 - 1.0);
  float2 dir = float2(0.08, -1.0);
  float amt = 0.045;
  float2 nuv = uv * float2(18.0, 6.0);
  float2 n0 = float2(PPS_noise(nuv + ph0 * 3.0) - 0.5, PPS_noise(nuv + 7.0 - ph0 * 3.0) - 0.5) * 0.004;
  float2 n1 = float2(PPS_noise(nuv + ph1 * 3.0 + 3.1) - 0.5, PPS_noise(nuv + 11.0 - ph1 * 3.0) - 0.5) * 0.004;
  float3 a = PPS_tex(uv + dir * ph0 * amt * stream + n0 * stream);
  float3 b = PPS_tex(uv + dir * ph1 * amt * stream + n1 * stream);
  float3 col = mix(b, a, w0);

  float2 d = uv - float2(0.52, 0.92);
  float r = length(d * float2(1.0, 3.0));
  float2 rip = normalize(d + 1e-4) * sin(r * 80.0 - 6.2832 * time) * 0.0025 * pool;
  col = mix(col, PPS_tex(uv + rip), pool);

  float3 s = pow(clamp(col, 0.0, 1.0), float3(1.0 / 2.2));
  return half4(half3(mix(s, PPS_p3(s), p3)), 1.0);
}
`;
