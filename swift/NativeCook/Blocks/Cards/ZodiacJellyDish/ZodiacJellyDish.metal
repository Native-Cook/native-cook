#include <metal_stdlib>
using namespace metal;
struct ZodiacJellyDishParams {
  float4 geometry, shape, win, grabPull, motion, image, printed, viewport;
};
struct ZodiacJellyDishVertex { float4 position [[position]]; };
vertex ZodiacJellyDishVertex zodiacJellyDishVertex(uint id [[vertex_id]]) {
  float2 p = float2((id << 1) & 2, id & 2);
  return {float4(p*2.0-1.0, 0.0, 1.0)};
}
constexpr sampler zodiacJellyDishClamp(coord::normalized, address::clamp_to_edge, filter::linear);
constexpr sampler zodiacJellyDishDecal(coord::normalized, address::clamp_to_zero, filter::linear);
#define center u.geometry.xy
#define hs u.geometry.zw
#define corner u.shape.x
#define bevel u.shape.y
#define winR u.shape.z
#define t u.shape.w
#define win u.win
#define grab u.grabPull.xy
#define pull u.grabPull.zw
#define squash u.motion.x
#define swirl u.motion.y
#define light u.motion.zw
#define dishSize u.image.xy
#define labelScale u.image.z
#define loop u.image.w
#define labelOrigin u.printed.xy
#define pulse u.printed.z
constant float zodiacJellyDishTAU = 6.2831853;

// SkNamedGamut's sRGB -> XYZ D50 -> Display P3 transform. Converting the sampled
// colour avoids an extra 8-bit quantisation and preserves Skia's interpolation order.
float3 zodiacJellyDishImageP3(float3 c) {
  float3 linear = select(c / 12.92, pow(c / 1.055 + 0.055 / 1.055, float3(2.4)), c >= 0.04045);
  float3 p3 = float3(
    dot(linear, float3(0.822547143, 0.177678533, -0.000004838)),
    dot(linear, float3(0.033179818, 0.966723752, -0.000010082)),
    dot(linear, float3(0.017070309, 0.072381865, 0.910398413)));
  return select(p3 * 12.92, 1.055 * pow(max(p3, 0.0), float3(1.0 / 2.4)) - 0.055, p3 >= 0.0031308);
}

float zodiacJellyDishBox(float2 p, float2 b, float r) {
  float2 q = abs(p) - b + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

// the jelly's deformation: undo the pull and the squash to find where a point came from
float2 zodiacJellyDishUndeform(float2 p, constant ZodiacJellyDishParams& u) {
  float s = squash;
  p.y = p.y / (1.0 - s) - s * hs.y * 0.5;
  p.x = p.x * (1.0 - s * 0.55);
  float2 d = p - grab;
  float w = exp(-dot(d, d) / (2.0 * 150.0 * 150.0));
  return p - pull * w * 1.15;
}

float zodiacJellyDishWobble(float2 p, constant ZodiacJellyDishParams& u) {
  float a = atan2(p.y, p.x);
  float ph = zodiacJellyDishTAU * t / loop;
  return 10.0 * sin(3.0 * a + ph + 0.6) + 7.0 * sin(5.0 * a - 2.0 * ph + 1.3) + 5.0 * sin(2.0 * a + 3.0 * ph + 0.4) + 3.0 * sin(7.0 * a + ph * 4.0);
}

float zodiacJellyDishSlab(float2 p, constant ZodiacJellyDishParams& u) {
  float2 q = zodiacJellyDishUndeform(p, u);
  return zodiacJellyDishBox(q, hs, corner) - zodiacJellyDishWobble(q, u);
}

float zodiacJellyDishHole(float2 p, constant ZodiacJellyDishParams& u) {
  float2 q = zodiacJellyDishUndeform(p, u);
  float2 c = win.xy + win.zw * 0.5;
  return zodiacJellyDishBox(q - c, win.zw * 0.5, winR) + 2.0 * sin(3.0 * atan2(q.y - c.y, q.x - c.x) + zodiacJellyDishTAU * t / loop);
}

float3 zodiacJellyDishJelly(float2 q, constant ZodiacJellyDishParams& u) {
  // gummy pieces of colour in a milky, silvery glass
  float ph = zodiacJellyDishTAU * (t / loop + swirl);
  float3 col = float3(0.80, 0.81, 0.88) + 0.06 * sin(q.y * 0.03 + ph);
  float3 cs[4];
  cs[0] = float3(1.0, 0.45, 0.74);
  cs[1] = float3(0.40, 0.70, 1.0);
  cs[2] = float3(1.0, 0.62, 0.30);
  cs[3] = float3(0.50, 0.88, 0.62);
  for (int i = 0; i < 8; i++) {
    float fi = float(i);
    float2 c = float2(cos(ph * (1.0 + fmod(fi, 2.0)) + fi * 2.1) * (60.0 + 9.0 * fi),
                  sin(ph * (1.0 + fmod(fi, 3.0) * 0.0) + fi * 1.37) * (150.0 - 6.0 * fi));
    float2 w = q + float2(sin(q.y * 0.045 + ph + fi), cos(q.x * 0.05 - ph + fi * 0.7)) * 22.0;
    float2 dd = (w - c) / float2(74.0 + 8.0 * fmod(fi, 3.0), 92.0 - 6.0 * fmod(fi, 4.0));
    float f = exp(-dot(dd, dd));
    float body = smoothstep(0.08, 0.75, f);
    float3 cc = i == 0 || i == 4 ? cs[0] : i == 1 || i == 5 ? cs[1] : i == 2 || i == 6 ? cs[2] : cs[3];
    col = mix(col, cc, body * 0.72);
    col += float3(1.0) * smoothstep(0.8, 0.97, f) * 0.18;
  }
  return col;
}

fragment half4 zodiacJellyDishFragment(ZodiacJellyDishVertex in [[stage_in]], constant ZodiacJellyDishParams& u [[buffer(0)]], texture2d<half> dish [[texture(0)]], texture2d<half> label [[texture(1)]]) {
  float2 xy = in.position.xy / u.viewport.z;
  float2 p = xy - center;
  float d = zodiacJellyDishSlab(p, u);
  if (d > 1.5) return half4(0.0);
  // gradient of the outline: the bevel's normal in the plane
  float e = 0.75;
  float2 g = float2(zodiacJellyDishSlab(p + float2(e, 0.0), u) - zodiacJellyDishSlab(p - float2(e, 0.0), u), zodiacJellyDishSlab(p + float2(0.0, e), u) - zodiacJellyDishSlab(p - float2(0.0, e), u)) / (2.0 * e);
  float gl = max(length(g), 1e-4);
  g /= gl;
  // bevel profile: a quarter circle, flat inside
  float k = clamp(-d / bevel, 0.0, 1.0);
  float slope = (1.0 - k) / sqrt(max(1.0 - (1.0 - k) * (1.0 - k), 0.02));
  slope = min(slope, 4.0);
  float3 n = normalize(float3(-g * slope, 1.0));

  float2 q = zodiacJellyDishUndeform(p, u);
  // the window: the dish behind its own rim
  float dh = zodiacJellyDishHole(p, u);
  float3 col;
  float edgeH = 0.0;
  if (dh < 0.0) {
    float2 c = win.xy + win.zw * 0.5;
    float kh = clamp(-dh / 12.0, 0.0, 1.0);
    float2 gh = float2(zodiacJellyDishHole(p + float2(e, 0.0), u) - zodiacJellyDishHole(p - float2(e, 0.0), u), zodiacJellyDishHole(p + float2(0.0, e), u) - zodiacJellyDishHole(p - float2(0.0, e), u)) / (2.0 * e);
    float2 refr = -normalize(gh + 1e-4) * (1.0 - kh) * 7.0;
    float2 uv = (q + refr - win.xy) / win.zw;
    // cover-fit the photo into the window
    float ia = dishSize.x / dishSize.y;
    float wa = win.z / win.w;
    float2 s = ia > wa ? float2(wa / ia, 1.0) : float2(1.0, ia / wa);
    float2 iuv = (uv - 0.5) * s * 0.74 + float2(0.5, 0.47);
    col = float3(half3(zodiacJellyDishImageP3(float3(dish.sample(zodiacJellyDishClamp, iuv).rgb))));
    col *= 0.86 + 0.14 * kh;
    edgeH = (1.0 - kh) * (1.0 - kh);
  } else {
    // refracted jelly under the bevel
    float2 r = q - g * slope * 16.0;
    col = zodiacJellyDishJelly(r, u);
    // the window's thick rim, seen from the glass side: bright and silvery
    float rim = 1.0 - smoothstep(0.0, 18.0, dh);
    col = mix(col, float3(0.95, 0.96, 1.0), rim * 0.7);
    col = mix(col, float3(0.2, 0.2, 0.3), exp(-pow((dh - 7.0) / 2.2, 2.0)) * 0.55);
    // chromatic fringe along the outer bevel
    float fr = (1.0 - k);
    col.r += fr * 0.06 * g.x;
    col.b -= fr * 0.06 * g.x;
  }
  // fresnel: grazing angles turn to silver-white
  float fres = pow(1.0 - n.z, 1.6);
  col = mix(col, float3(0.94, 0.95, 1.0), clamp(fres * 1.25, 0.0, 0.85));
  // thick glass: a dark refracted ring inside the bevel, a bright line just inside the edge
  float ring = exp(-pow((d + bevel * 0.5) / 4.5, 2.0));
  col = mix(col, float3(0.16, 0.16, 0.24), ring * 0.5);
  float edgeLine = exp(-pow((d + 3.5) / 2.2, 2.0));
  col = mix(col, float3(1.0), edgeLine * 0.75);
  // studio strips reflected in the bevel, sliding with the light
  float strips = smoothstep(0.62, 0.92, sin(n.x * 6.0 + n.y * 3.5 + light.x * 3.0) * 0.5 + 0.5);
  col = mix(col, float3(1.0), strips * (1.0 - k) * 0.8);
  // specular highlights from a moving light, and a second soft one
  float3 L = normalize(float3(light, 0.9));
  float3 H = normalize(L + float3(0.0, 0.0, 1.0));
  float spec = pow(max(dot(n, H), 0.0), 60.0) * (1.0 - k * 0.6);
  float spec2 = pow(max(dot(n, normalize(float3(-light * 0.6, 1.0))), 0.0), 8.0) * (1.0 - k) * 0.25;
  col += float3(1.0) * (spec * 0.9 + spec2);
  col += float3(1.0) * edgeH * 0.5;
  // a soft inner shadow line just inside the outline, so the slab has a thickness
  float lip = smoothstep(-3.0, -1.0, d) * (1.0 - smoothstep(-1.0, 1.0, d));
  col = mix(col, float3(0.42, 0.42, 0.55), lip * 0.6);
  col += float3(pulse * 0.25 * (1.0 - k * 0.5));
  // the label on the surface
  half4 lab = label.sample(zodiacJellyDishDecal, (q - labelOrigin) * labelScale / float2(label.get_width(), label.get_height()));
  if (lab.a > 0.0h) {
    lab.rgb = half3(zodiacJellyDishImageP3(float3(lab.rgb) / float(lab.a)) * float(lab.a));
  }
  col = col * (1.0 - float(lab.a)) + float3(lab.rgb);
  float a = 1.0 - smoothstep(-0.5, 1.5, d);
  // the glass lets a little of the dark page through at the very edge
  a *= mix(0.86, 1.0, smoothstep(0.0, 0.25, k));
  return half4(half3(clamp(col, 0.0, 1.0) * a), a);
}
