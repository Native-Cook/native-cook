import { Skia } from '@shopify/react-native-skia';

// Night sky with three aurora curtains at three depths. Each curtain is a sheet of light hanging
// from a wavy hem: crisp and green-white at the hem, folding along its length, combed into vertical
// rays, turning teal and then violet (red on the farthest, highest one) as it fades upwards. The far
// curtains are higher, dimmer, softer and move less with the finger, which gives the sky depth.
// Every flow term moves on circles in noise space, so the sky returns to the same frame each LOOP;
// a dither of one 8-bit step at the end keeps the long gradients free of banding.
export const AURORA_SKSL = `
uniform float2 size;
uniform float phase;
uniform float2 pointer;
uniform float pull;
uniform float reveal;

float hash(float2 p) {
  p = fract(p * float2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}

float noise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x), mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
}

float fbm(float2 p) {
  float v = 0.0;
  float a = 0.5;
  for (int i = 0; i < 4; i++) {
    v += a * noise(p);
    p = p * 2.03 + float2(13.1, 7.7);
    a *= 0.5;
  }
  return v;
}

float3 curtain(float2 uv, float2 p, float hem, float sway, float2 drift, float2 shimmer, float3 low, float3 mid, float3 high, float tall, float soft, float seed) {
  float wave = fbm(float2(p.x * 1.15 + seed, seed * 0.37) + drift);
  float line = hem + (wave - 0.5) * sway + 0.028 * sin(p.x * 2.7 + seed + drift.x * 2.0);
  float d = line - uv.y;
  float edge = smoothstep(-soft, soft * 0.35, d);
  float body = 0.55 * exp(-max(d, 0.0) / tall) + 0.9 * exp(-max(d, 0.0) / (tall * 0.22));
  float fold = 0.5 + 0.5 * sin(p.x * 6.5 + wave * 8.0 + seed + drift.y * 1.5);
  fold = 0.45 + 0.55 * fold * fold;
  float rays = noise(float2(p.x * 34.0 + shimmer.x * 2.5 + seed, uv.y * 1.4 + shimmer.y));
  rays = 0.18 + 1.5 * rays * rays;
  float strength = smoothstep(0.22, 0.78, fbm(float2(p.x * 1.5 - seed, 2.0) + drift * 0.6));
  float hemGlow = exp(-abs(d) / (soft * 2.2 + 0.004)) * 0.65;
  float glow = (edge * body * fold * rays * 1.9 + hemGlow * edge) * (0.25 + 1.0 * strength);
  float h = clamp(max(d, 0.0) / (tall * 2.6), 0.0, 1.0);
  float3 tone = mix(low, mid, smoothstep(0.0, 0.32, h));
  tone = mix(tone, high, smoothstep(0.3, 1.0, h));
  return tone * glow;
}

half4 main(float2 xy) {
  float2 uv = xy / size;
  float aspect = size.x / size.y;
  float2 p = float2(uv.x * aspect, uv.y);
  float2 toward = float2(pointer.x * aspect, pointer.y) - p;
  float near = exp(-dot(toward, toward) * 2.2);
  float2 bend = toward * near * pull;

  float2 drift = float2(cos(phase), sin(phase)) * 0.55;
  float2 shimmer = float2(cos(phase * 2.0 + 1.3), sin(phase * 2.0 + 1.3)) * 0.4;

  // Sky: deep navy overhead, a little lighter and greener towards the horizon, with a faint airglow.
  float3 sky = mix(float3(0.012, 0.02, 0.06), float3(0.03, 0.07, 0.15), smoothstep(0.0, 0.5, uv.y));
  sky += float3(0.02, 0.07, 0.08) * exp(-abs(uv.y - 0.47) * 10.0);

  // Stars, fewer and dimmer low down, twinkling on whole turns of the loop.
  float2 cell = floor(xy / 22.0);
  float hs = hash(cell);
  float2 at = (cell + 0.2 + 0.6 * float2(hash(cell + 3.1), hash(cell + 7.7))) * 22.0;
  float star = step(0.88, hs) * smoothstep(1.3, 0.15, length(xy - at));
  star *= (0.5 + 0.5 * sin(phase * 3.0 + hs * 40.0)) * smoothstep(0.55, 0.12, uv.y) * (0.4 + 0.6 * hash(cell + 1.7));

  float3 light = float3(0.0);
  // Far: high, faint, soft, red-violet crowned.
  float2 pf = p + bend * 0.12;
  light += 0.42 * curtain(uv + float2(0.0, bend.y * 0.04), pf, 0.3, 0.16, drift * 0.7, shimmer * 0.7,
    float3(0.18, 0.75, 0.55), float3(0.45, 0.3, 0.85), float3(0.75, 0.18, 0.35), 0.24, 0.03, 11.7);
  // Middle.
  float2 pm = p + bend * 0.22;
  light += 0.7 * curtain(uv + float2(0.0, bend.y * 0.08), pm, 0.37, 0.18, -drift.yx, shimmer.yx,
    float3(0.25, 0.95, 0.7), float3(0.2, 0.6, 0.85), float3(0.55, 0.3, 0.95), 0.2, 0.016, 5.3);
  // Near: low, bright, crisp hem, green-white turning violet.
  float2 pn = p + bend * 0.34;
  light += curtain(uv + float2(0.0, bend.y * 0.12), pn, 0.45, 0.16, drift, shimmer,
    float3(0.55, 1.0, 0.72), float3(0.2, 0.92, 0.62), float3(0.5, 0.32, 1.0), 0.15, 0.008, 0.0);

  light *= reveal;
  float3 c = sky + light * 1.5 + float3(star) * (1.0 - clamp(length(light) * 1.5, 0.0, 1.0)) * (0.35 + 0.65 * reveal);
  c = 1.0 - exp(-c * 1.4);
  c += (hash(xy + phase) - 0.5) / 255.0;
  return half4(half3(c), 1.0);
}
`;

export const AURORA = Skia.RuntimeEffect.Make(AURORA_SKSL);
