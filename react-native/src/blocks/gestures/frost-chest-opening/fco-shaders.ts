// Frost Chest Opening — SkSL runtime effects.
// Skia treats a runtime effect's output as Display P3 on iOS, so both convert their sRGB colours
// with toP3() before returning (without it the sky and the liquid come out oversaturated).

const P3 = `
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
float noise(float2 p) {
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

// The rest scene's sky: a vertical blue gradient, a sunburst of soft rays turning slowly round a
// point behind the chest, a white glow there and a pale snow floor. `bright` (0 … 1) is the charge:
// the rays whiten and widen and the glow grows while the orb fills.
export const RAYS_SKSL = `
uniform float2 size;
uniform float2 center;
uniform float rot;
uniform float bright;
${P3}
const float3 TOP = float3(0.555, 0.725, 0.93);
const float3 MID = float3(0.66, 0.79, 0.955);
const float3 LOW = float3(0.87, 0.91, 0.965);
const float3 FLOOR = float3(0.905, 0.925, 0.97);
const float3 RAY = float3(0.76, 0.87, 0.99);
const float3 DEEP = float3(0.38, 0.56, 0.88);

half4 main(float2 p) {
  float y = p.y / size.y;
  float3 col = mix(TOP, MID, smoothstep(0.0, 0.42, y));
  col = mix(col, LOW, smoothstep(0.38, 0.78, y));
  float2 d = p - center;
  float r = length(d) / size.y;
  float a = atan(d.y, d.x) + rot;
  float band = cos(a * 8.0);
  float soft = smoothstep(0.03, 0.16, r);
  float width = mix(0.38, 0.1, bright);
  float light = smoothstep(width, width + 0.32, band) * soft;
  float deep = smoothstep(0.35, 0.95, -band) * soft;
  float upper = 1.0 - smoothstep(0.45, 0.95, y);
  // At rest the rays are faint; the charge lifts only the light wedges (the dark ones stay), and
  // the floor's wedges are lit by RAYLIGHT_SKSL drawn over the snow.
  col = mix(col, DEEP, deep * (0.14 + 0.1 * upper));
  col = mix(col, RAY, light * 0.26 * (0.6 + 0.4 * upper));
  // Charged, the light wedges go white (the dark ones keep their blue: measured 178–185 of 255).
  col = mix(col, float3(1.0), light * bright * 0.92);
  float glow = exp(-r * r * mix(16.0, 12.0, bright));
  col = mix(col, float3(1.0), glow * (0.48 + 0.15 * bright));
  col = mix(col, FLOOR, smoothstep(0.66, 0.93, y) * 0.8);
  return half4(half3(toP3(col)), 1.0);
}
`;

// The charge's light wedges over the snow floor (premultiplied white): below and beside the chest
// the video's light rays turn pure white while the orb fills.
export const RAYLIGHT_SKSL = `
uniform float2 size;
uniform float2 center;
uniform float rot;
uniform float bright;

half4 main(float2 p) {
  float2 d = p - center;
  float r = length(d) / size.y;
  float a = atan(d.y, d.x) + rot;
  float band = cos(a * 8.0);
  float light = smoothstep(0.1, 0.45, band) * smoothstep(0.05, 0.2, r);
  float lower = smoothstep(-0.05, 0.2, d.y / size.y);
  float k = light * lower * bright * 0.9;
  return half4(k, k, k, k);
}
`;

// The glass orb of the hold button: a milky frosted sphere filling from the bottom with glowing
// blue frost. Colours sampled from the video: the liquid is bright aqua at the rim (100,255,255),
// deeper blue inside (50,200,255) with lighter mottling and specks, under a thin bright crest that
// waves while it rises; the empty glass is blue-white (232,248,255). From a third full, the inside
// top of the sphere mirrors the liquid as a dark slate shape that grows as the level comes up.
// Drawn over the rect (center ± radius); output is premultiplied.
export const ORB_SKSL = `
uniform float2 center;
uniform float radius;
uniform float fill;
uniform float time;
uniform float glow;
uniform float alpha;
${P3}
const float3 EDGE = float3(0.39, 1.0, 1.0);
const float3 CORE = float3(0.20, 0.78, 1.0);
const float3 MOTTLE = float3(0.5, 0.94, 1.0);
const float3 DEEP = float3(0.12, 0.6, 0.95);
const float3 GLASS_TOP = float3(0.91, 0.973, 1.0);
const float3 GLASS_LOW = float3(0.86, 0.933, 1.0);
const float3 MIRROR = float3(0.40, 0.47, 0.62);

half4 main(float2 p) {
  float2 q = (p - center) / radius;
  float r = length(q);
  float cover = 1.0 - smoothstep(1.0 - 1.5 / radius, 1.0, r);
  if (cover <= 0.0) return half4(0.0);
  float z = sqrt(max(0.0, 1.0 - r * r));

  // Glass: blue-white, a touch darker low and toward the far side, a faint cool rim line.
  float3 col = mix(GLASS_TOP, GLASS_LOW, smoothstep(-0.8, 0.6, q.y));
  float rimLine = smoothstep(0.86, 0.97, r) * (1.0 - smoothstep(0.97, 1.0, r));
  col = mix(col, float3(0.8, 0.86, 0.93), rimLine * 0.25);

  // The mirrored liquid in the top of the sphere.
  float m = smoothstep(0.36, 0.86, fill) * (1.0 - smoothstep(0.86, 0.95, fill));
  if (m > 0.0) {
    float2 mq = (q - float2(0.08, -0.7)) / float2(0.08 + 0.82 * m, 0.05 + 0.27 * m);
    float edge = length(mq) + 0.16 * sin(q.x * 9.0 + time * 6.0) * m + 0.12 * (noise(q * 5.0) - 0.5);
    float blob = 1.0 - smoothstep(0.82, 1.0, edge);
    float arc = smoothstep(0.55, 0.7, edge) * (1.0 - smoothstep(0.7, 0.82, edge)) * step(0.0, mq.y);
    col = mix(col, MIRROR, blob * 0.9);
    col = mix(col, float3(1.0), arc * blob * 0.7);
  }

  // Liquid.
  float calm = 1.0 - fill * fill * fill;
  float lvl = 1.0 - 2.0 * fill;
  float wave = (0.06 * sin(q.x * 4.2 + time * 6.5) + 0.03 * sin(q.x * 8.5 - time * 9.0)) * calm;
  float surf = q.y - (lvl + wave);
  float inLiquid = smoothstep(-0.02, 0.02, surf) * step(0.002, fill);
  float n = noise(q * 3.0 + float2(time * 0.2, -time * 0.35)) * 0.6 + noise(q * 6.5 - time * 0.25) * 0.4;
  float3 liq = mix(EDGE, CORE, smoothstep(0.25, 0.85, z));
  float deepK = smoothstep(0.3, 0.8, z) * smoothstep(0.25, 0.6, fill);
  liq = mix(liq, DEEP, smoothstep(0.42, 0.18, n) * 0.6 * deepK);
  liq = mix(liq, MOTTLE, smoothstep(0.58, 0.85, n) * 0.55 * smoothstep(0.3, 0.8, z));
  float crest = exp(-surf * surf * 1600.0) * calm;
  liq = mix(liq, float3(0.82, 1.0, 1.0), crest * 0.9);
  float2 g = q * 12.0 + float2(0.0, time * 0.7);
  float spark = step(0.93, hash(floor(g))) * smoothstep(0.28, 0.0, length(fract(g) - 0.5));
  liq += float3(spark * 0.6);
  liq = mix(liq, float3(1.0), glow * 0.2);

  col = mix(col, liq, inLiquid);
  float spec = exp(-dot(q - float2(-0.42, -0.48), q - float2(-0.42, -0.48)) * 18.0);
  col = mix(col, float3(1.0), spec * 0.6);
  float a = mix(0.96, 1.0, inLiquid) * cover * alpha;
  return half4(half3(toP3(col)) * a, a);
}
`;

// The reveal's light, drawn additively (BlendMode.Plus) over the scene. One pass does all of it:
//  - core: a tight hot spot plus a long inverse-square tail, so near the source everything clips
//    to white and the glow reaches the screen's corners, the way a light too bright for the lens does;
//  - rays: thin god rays from noise sampled round a circle (no seam at ±π), slowly turning and
//    flickering, their reach growing as the light breaks out;
//  - pillar: the vertical beam the card rides up;
//  - streak: a cinematic anamorphic flare across the source;
//  - ring: the shockwave, its red, green and blue a little apart (dispersion).
// Below the chest's mouth the light is hidden by the box (`below` lets it through while the screen
// is whited out). Output is premultiplied, converted to Display P3.
export const LIGHT_SKSL = `
uniform float2 size;
uniform float2 center;
uniform float time;
uniform float core;
uniform float rays;
uniform float reach;
uniform float streak;
uniform float ringR;
uniform float ringA;
uniform float pillar;
uniform float mouthY;
uniform float below;
${P3}
const float3 CYAN = float3(0.55, 0.86, 1.0);

half4 main(float2 p) {
  float2 d = p - center;
  float r = length(d);
  float R = r / size.y;
  float2 dir = d / max(r, 0.001);

  float c = core * (exp(-R * R * 120.0) * 1.4 + 0.012 / (R * R + 0.01));

  float ca = cos(time * 0.15);
  float sa = sin(time * 0.15);
  float2 q = float2(dir.x * ca - dir.y * sa, dir.x * sa + dir.y * ca);
  float n = noise(q * 4.0 + float2(time * 0.35, 0.0)) * 0.5
    + noise(q * 11.0 - float2(0.0, time * 0.6)) * 0.33
    + noise(q * 27.0 + float2(time * 0.9, time * 0.4)) * 0.17;
  float shaft = smoothstep(0.52, 0.88, n);
  shaft = shaft * shaft;
  float along = exp(-R / max(reach, 0.01)) * smoothstep(0.0, 0.04, R);
  float ray = rays * (shaft * 2.6 + 0.04) * along;

  float up = center.y - p.y;
  float px = d.x / (size.x * 0.075);
  float pil = pillar * exp(-px * px) * smoothstep(-20.0, 30.0, up) * exp(-max(up, 0.0) / (size.y * 0.7));

  float sy1 = d.y / 5.0;
  float sy2 = d.y / 1.4;
  float sk = streak * (0.9 * exp(-sy1 * sy1) * exp(-abs(d.x) / (size.x * 0.55)) + exp(-sy2 * sy2) * exp(-abs(d.x) / (size.x * 0.3)));

  float hide = mix(below, 1.0, 1.0 - smoothstep(mouthY - 12.0, mouthY + 36.0, p.y));
  float3 col = mix(CYAN, float3(1.0), clamp(c * 0.6, 0.0, 1.0)) * c;
  col += mix(CYAN, float3(1.0), shaft * 0.6) * ray;
  col += mix(CYAN, float3(1.0), 0.6) * pil * 0.8;
  col *= hide;
  col += float3(0.6, 0.85, 1.0) * sk * (0.6 + 0.4 * hide);

  if (ringA > 0.001) {
    float w = 5.0 + ringR * 0.025;
    float er = (r - ringR * 1.012) / w;
    float eg = (r - ringR) / w;
    float eb = (r - ringR * 0.988) / w;
    col += float3(exp(-er * er), exp(-eg * eg), exp(-eb * eb)) * ringA;
  }
  col = clamp(col, 0.0, 1.0);
  float a = max(col.r, max(col.g, col.b));
  return half4(half3(toP3(col)), a);
}
`;
