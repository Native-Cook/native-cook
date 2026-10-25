/*
 The negative strip in one pass, in the strip's own coordinates (top-left = 0, 0).

 A 35 mm strip: an orange-masked base with two rows of perforations (transparent, so the desk or
 the light table shows through them) and three frames — frame 7 in the middle, the ends of 6 and
 8 at the sides. Each frame is a photo turned into a colour negative: the transmission is the
 orange mask times (1 − scene)^1.15, so bright parts of the scene are dense and dark, shadows are
 clear orange.

 Off the light the strip is seen by the room's light: a dim, warm negative with a glossy sheen
 that slides along it as the strip moves. Where it lies over the light table the negative is
 inverted back into the positive: scene = 1 − transmission / mask (the mask cancels), then a
 contrast curve. The light's edge is soft (a rounded-box falloff), and a warm halo runs along the
 line where negative turns into positive. Inside the loupe the positive is magnified 1.8× with a
 slight barrel, a 1 px colour split at the rim and a darker rim.

 Uniforms (pt, strip space; `roll` 1 draws the strip of other frames behind, with other crops):
   strip   (0, 0, width, height)        frame  (x, y, w, h) of frame 7
   light   the light panel's rect        loupe  (cx, cy, radius, opacity)
   perf    (pitch, hole w, hole h, x phase)   rows  (top hole centre y, bottom hole centre y)
   sheen   the strip's screen y (moves the gloss)   imgSize  photo pixels   p3  1 on iOS
 Skia treats RuntimeEffect output as Display P3 on iOS: constant colours are converted from sRGB
 (photo samples arrive in the output space already, and the inversion divides the mask out).
 */
export const FILM_SKSL = `
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
uniform float p3;
// 0 for this roll's strip, 1 for the dimmed strip of other frames lying behind it.
uniform float roll;

float sdBox(float2 p, float2 c, float2 b, float r) {
  float2 q = abs(p - c) - b + r;
  return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}

float3 toP3(float3 c) {
  if (p3 < 0.5) return c;
  float3 l = pow(clamp(c, 0.0, 1.0), float3(2.2));
  float3 m = float3(
    dot(l, float3(0.8225, 0.1774, 0.0)),
    dot(l, float3(0.0332, 0.9669, 0.0)),
    dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(m, float3(1.0 / 2.2));
}

float hash(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }

// The scene in frame i (-1, 0, 1) at frame-local uv. Frames 6 and 8 are other crops of the roll.
float3 scene(float2 uv, float i) {
  float2 q = uv * imgSize;
  if (i < -0.5) q = uv * imgSize * 0.78 + float2(150.0, 90.0);
  if (i > 0.5) q = uv * imgSize * 0.82 + float2(10.0, 70.0);
  // Frames 2 – 4 of the roll: tighter crops of the same sitting.
  if (roll > 0.5) q = uv * imgSize * 0.62 + float2(60.0 + (i + 1.0) * 110.0, 40.0 + (i + 1.0) * 30.0);
  return photo.eval(q).rgb;
}

// Transmission of the film at p (the negative), with the mask colour 'base'.
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
  // Unexposed film between and around the frames: clear base with a whisper of fog.
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
  // Perforations: rounded holes, two rows, cut right through.
  float hx = mod(p.x - perf.w, perf.x) - perf.x * 0.5;
  float hTop = sdBox(float2(hx, p.y), float2(0.0, rows.x), perf.yz * 0.5, 2.4);
  float hBot = sdBox(float2(hx, p.y), float2(0.0, rows.y), perf.yz * 0.5, 2.4);
  float hole = min(hTop, hBot);
  a *= smoothstep(-0.6, 0.6, hole);
  if (a <= 0.0) return half4(0.0);

  float3 base = toP3(float3(0.90, 0.54, 0.30));
  float3 neg = negative(p, base);

  // Off the light: the room's light on the film, plus a gloss band that slides with the strip.
  float band = p.x * 0.55 + p.y - (sheen * 0.9 - 120.0);
  float gloss = exp(-band * band / 1800.0) * 0.16 + exp(-band * band / 160.0) * 0.07;
  float3 off = neg * 0.55 + toP3(float3(1.0, 0.94, 0.86)) * gloss;
  // A thin darker lip around each perforation.
  off *= 0.86 + 0.14 * smoothstep(0.0, 2.5, hole);

  // On the light: the inverted positive, brighter in the middle of the panel.
  float2 lc = light.xy + light.zw * 0.5;
  float sd = sdBox(p, lc, light.zw * 0.5, 8.0);
  float lit = 1.0 - smoothstep(-18.0, 6.0, sd);
  float2 rel = (p - lc) / (light.zw * 0.5);
  float fall = 0.9 + 0.1 * (1.0 - clamp(dot(rel, rel) * 0.5, 0.0, 1.0));
  float3 pos = positive(neg, base) * fall;

  float3 col = mix(off, pos, lit);
  // Warm halo on the line where the negative turns over.
  col += toP3(float3(1.0, 0.72, 0.42)) * (lit * (1.0 - lit) * 4.0) * 0.20;

  // The loupe: a magnified positive.
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
`;
