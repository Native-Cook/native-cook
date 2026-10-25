/*
 Rocket Launch Progress — exhaust in one pass: the engine's flame, the smoke it throws sideways
 along the pad, and the light it casts.

 - Flame (nozzle-local, pointing along `dir`): a teardrop whose length follows `throttle` and grows
   with speed (`lift`), shaped by a two-octave noise that streams away from the nozzle; white-hot
   core → yellow → orange → a red fringe, added as light.
 - Smoke: twelve puffs born 0.11 s apart from ignition, half thrown left, half right along the pad,
   each rolling outward and rising as it ages, growing and thinning; their sum is broken up by
   fbm and lit warm near the flame, cool and grey away from it. `fade` clears it all after lift-off.
 - Glow: a warm pool of light on the ground and the tower around the nozzle.
 Smoke and flame only exist between ignition and the next rocket, so `age` need not loop.
 Colours are sRGB converted to Display P3 (Skia treats a runtime effect's output as P3 on iOS).
 Output is premultiplied: smoke covers what is behind it, flame light adds over it.
*/
export const ROCKET_EXHAUST_SKSL = `
uniform float2 nozzle;
uniform float2 dir;
uniform float throttle;
uniform float lift;
uniform float age;
uniform float fade;
uniform float2 pad;
uniform float k;

float3 toP3(float3 s) {
  float3 l = pow(max(s, float3(0.0)), float3(2.2));
  float3 p = float3(0.8225 * l.r + 0.1774 * l.g, 0.0332 * l.r + 0.9669 * l.g, 0.0171 * l.r + 0.0724 * l.g + 0.9108 * l.b);
  return pow(max(p, float3(0.0)), float3(1.0 / 2.2));
}
float hash(float2 p) {
  float3 q = fract(float3(p.xyx) * 0.1031);
  q += dot(q, q.yzx + 33.33);
  return fract((q.x + q.y) * q.z);
}
float noise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  f = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), f.x), mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), f.x), f.y);
}
float fbm(float2 p) {
  float v = 0.0;
  float a = 0.5;
  for (int i = 0; i < 3; i++) {
    v += a * noise(p);
    p = p * 2.03 + float2(17.1, 9.2);
    a *= 0.5;
  }
  return v;
}

half4 main(float2 p) {
  float3 light = float3(0.0);
  float smokeA = 0.0;
  float3 smokeC = float3(0.0);

  // Smoke along the pad.
  float2 sp = p - pad;
  if (fade > 0.001 && abs(sp.x) < 300.0 * k && sp.y > -240.0 * k && sp.y < 50.0 * k) {
    float d = 0.0;
    float warm = 0.0;
    for (int i = 0; i < 12; i++) {
      float fi = float(i);
      float a = age - fi * 0.11;
      if (a > 0.0) {
        float side = mod(fi, 2.0) < 0.5 ? -1.0 : 1.0;
        float h = hash(float2(fi, 3.7));
        float reach = (40.0 + 150.0 * h) * (1.0 - exp(-a * 1.5));
        float2 c = float2(side * (16.0 + reach), -10.0 - 22.0 * a - 14.0 * h) * k;
        float r = (16.0 + 34.0 * sqrt(a) + 10.0 * h) * k;
        float2 q = (sp - c) / r;
        float g = exp(-dot(q, q) * 1.6);
        d += g * (0.85 / (1.0 + a * 0.6));
        warm += g * exp(-a * 2.0);
      }
    }
    // Billows: the density broken up by fbm, and lit from above by comparing it with a sample a
    // little lower (a bulge facing up is bright, its underside shaded).
    float2 np = sp / (46.0 * k) + float2(age * 0.6, -age * 0.9);
    float n = fbm(np);
    float nb = fbm(np + float2(0.0, 0.22));
    d *= 0.35 + 1.25 * n;
    smokeA = clamp(smoothstep(0.08, 0.7, d) * 0.96, 0.0, 0.96) * fade;
    float relief = clamp(0.8 + 1.4 * (n - nb), 0.45, 1.12);
    float3 grey = mix(float3(0.5, 0.48, 0.56), float3(0.93, 0.91, 0.94), clamp(n * 1.3 - 0.1, 0.0, 1.0)) * relief;
    smokeC = mix(grey, float3(1.0, 0.7, 0.42), clamp(warm * 0.45 * throttle, 0.0, 0.55));
  }

  // The flame, in the nozzle's frame: x across, y away from the rocket.
  if (throttle > 0.001) {
    float2 q = p - nozzle;
    float2 ax = float2(-dir.y, dir.x);
    float y = dot(q, -dir) / k;
    float x = dot(q, ax) / k;
    float len = (34.0 + 46.0 * lift) * throttle;
    if (y > -6.0 && y < len * 1.6 && abs(x) < 40.0) {
      float u = clamp(y / len, 0.0, 2.0);
      float n2 = noise(float2(x * 0.18, y * 0.09 - age * 26.0)) - 0.5;
      float w = (6.5 + 7.0 * u) * (1.0 - smoothstep(0.75, 1.35, u)) * (0.9 + 0.2 * throttle);
      float xd = abs(x + n2 * 5.0 * u) / max(w, 0.001);
      float body = (1.0 - smoothstep(0.55, 1.0, xd)) * smoothstep(-6.0, 0.0, y) * (1.0 - smoothstep(0.85, 1.5, u + n2 * 0.4));
      float core = (1.0 - smoothstep(0.0, 0.45, xd)) * (1.0 - smoothstep(0.1, 0.55, u));
      float3 c = mix(float3(1.0, 0.35, 0.12), float3(1.0, 0.72, 0.3), smoothstep(0.9, 0.3, u));
      c = mix(c, float3(1.0, 0.97, 0.86), core);
      light += c * body * (1.3 + core) * throttle;
    }
    // Light thrown on the ground and the smoke around the nozzle.
    float r = length((p - nozzle) / k);
    light += float3(1.0, 0.55, 0.25) * exp(-r * r / 9000.0) * 0.5 * throttle * (1.0 - lift * 0.8);
  }

  float3 col = smokeC * smokeA + light * (1.0 - smokeA * 0.55);
  return half4(half3(toP3(col)), half(smokeA));
}
`;
