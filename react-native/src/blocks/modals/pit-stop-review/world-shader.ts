/**
 * Pit Stop Review — the track under the car, one SkSL runtime effect in the hero's design points (402 × 220).
 *
 * The world is asphalt grain + painted lines + the pit box, seen through a horizontal motion blur whose length
 * is the camera's speed: at racing speed the grain smears into the pin's streaks (x-invariant, so the rest
 * frame is still), parked in the box it resolves into sharp asphalt and paint. Heat haze bends the world
 * behind the rear wing; speed lines rush past during the whip pan and the blast-in.
 *
 * Uniforms
 *   res   hero size in design pt
 *   w0    x = world offset (pt), y = blur length (pt), z = haze 0…1, w = speed lines 0…1
 *   w1    x = haze centre x (car rear, pt), y = scene time (s), z = box paint 0…1, w = unused
 *
 * Output is premultiplied white light added over the page's charcoal gradient. Skia treats RuntimeEffect
 * output as Display P3 on iOS: the (neutral) colour is still passed through toP3 for the ports' parity.
 * Identifiers avoid WGSL builtin and reserved names (no `distance`, `smooth`, `select`, …).
 */
export const WORLD_SKSL = `
uniform float2 res;
uniform float4 w0;
uniform float4 w1;

float hs(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }
float h1(float n) { return fract(sin(n * 91.345 + 47.853) * 43758.5453); }
float vn(float2 q) {
  float2 i = floor(q);
  float2 f = fract(q);
  f = f * f * (3.0 - 2.0 * f);
  float a = hs(i);
  float b = hs(i + float2(1.0, 0.0));
  float c = hs(i + float2(0.0, 1.0));
  float d = hs(i + float2(1.0, 1.0));
  return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}
float3 toP3(float3 c) {
  float3 l = pow(clamp(c, 0.0, 1.0), float3(2.2));
  float3 m = float3(
    dot(l, float3(0.8225, 0.1774, 0.0)),
    dot(l, float3(0.0332, 0.9669, 0.0)),
    dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(m, float3(1.0 / 2.2));
}
// One painted / light band: centre y, half width, strength. Soft at speed, a crisp paint edge in the box.
float band(float y, float c, float hw, float k, float soft) {
  float e = abs(y - c) - hw;
  return k * (1.0 - smoothstep(-soft, soft + 0.6, e));
}
// Box-filtered vertical paint bar [x0, x0 + w] seen through a blur window of length L ending at wx.
float bar(float wx, float L, float x0, float w) {
  float lo = max(wx - L, x0);
  float hi = min(wx, x0 + w);
  return max(0.0, hi - lo) / max(L, 1.0);
}

half4 main(float2 xy) {
  float worldOff = w0.x;
  float L = max(w0.y, 0.0);
  float hz = w0.z;
  float lines = w0.w;
  float hazeX = w1.x;
  float t = w1.y;
  float paint = w1.z;
  float speedy = clamp(L / 40.0, 0.0, 1.0);

  // Heat haze: displace the lookup in a lens behind the rear wing.
  float2 p = xy;
  float2 hc = float2(hazeX - 34.0, 82.0);
  float2 hd = (p - hc) / float2(78.0, 38.0);
  float mask = (1.0 - smoothstep(0.35, 1.0, length(hd))) * hz;
  if (mask > 0.001) {
    float nx = vn(float2(p.x * 0.11 + t * 2.0, p.y * 0.16 - t * 9.0)) - 0.5;
    float ny = vn(float2(p.x * 0.13 - 7.3, p.y * 0.12 - t * 7.0)) - 0.5;
    p += float2(nx, ny) * 7.0 * mask;
  }
  float wx = p.x + worldOff;
  float y = p.y;

  // Asphalt grain: sharp in the box, smeared into y-only streaks at speed (x-invariant at full speed).
  float sharpGrain = vn(float2(wx * 1.1, y * 1.1)) * 0.55 + vn(float2(wx * 0.28, y * 0.28)) * 0.45;
  float streakGrain = vn(float2(3.1, y * 0.62)) * 0.55 + vn(float2(7.7, y * 0.17)) * 0.45;
  float grain = mix(sharpGrain, streakGrain, speedy);
  float lum = 0.012 + (grain - 0.5) * mix(0.085, 0.025, speedy);

  // The pin's light bands; in the box they read as the pit lane's painted lines.
  float soft = mix(0.35, 1.6, speedy);
  // Bands measured on the pin's hero (right of the car): centre y, half width, strength over the ~22 grey base.
  float lanes = band(y, 28.0, 2.6, 0.33, soft) + band(y, 58.0, 0.6, 0.04, soft) + band(y, 64.0, 0.8, 0.07, soft)
              + band(y, 117.5, 2.2, 0.15, soft) + band(y, 127.5, 1.8, 0.13, soft) + band(y, 140.0, 0.8, 0.11, soft)
              + band(y, 151.0, 3.2, 0.14, soft) + band(y, 160.0, 0.6, 0.08, soft);
  lum += lanes * mix(1.35, 1.0, speedy);

  // Pit box: two transverse bars and a stop line, around world x = -3000 + [50, 364].
  float boxRear = bar(wx, L, -2950.0, 3.0) * step(31.0, y) * (1.0 - step(149.0, y));
  float boxFront = bar(wx, L, -2636.0, 3.0) * step(31.0, y) * (1.0 - step(149.0, y));
  float stopT = bar(wx, L, -2650.0, 18.0) * band(y, 82.0, 1.2, 1.0, mix(0.3, 2.0, speedy));
  lum += (boxRear + boxFront) * 0.30 * paint + stopT * 0.18 * paint;

  // Speed lines: sparse bright 1 pt lines in 1.6 pt rows, rushing left.
  if (lines > 0.001) {
    float row = floor(y / 1.6);
    float pick = step(0.9, h1(row));
    float seg = vn(float2(xy.x * 0.0045 + t * 16.0 * (0.7 + h1(row + 3.0)), row * 3.7));
    lum += pick * smoothstep(0.55, 0.8, seg) * lines * (0.25 + 0.45 * h1(row + 9.0));
  }

  // Fade into the page above and below the hero.
  float env = smoothstep(0.0, 16.0, xy.y) * (1.0 - smoothstep(172.0, 214.0, xy.y));
  float a = clamp(lum, 0.0, 1.0) * env;
  float3 c = toP3(float3(1.0));
  return half4(c * a, a);
}
`;
