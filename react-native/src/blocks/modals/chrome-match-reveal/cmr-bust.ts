// Chrome Match Reveal — a liquid-chrome bust with a pearl tiara, ray-marched in one SkSL shader.
//
// Signed distances: an egg-shaped head, a neck and rounded shoulders cut flat at the bottom, smooth-
// unioned (with a nose, a chin and soft eye sockets), and a ring of sixteen pearls round the brow. The camera looks straight on; the bust turns
// by yaw and nods by pitch. Chrome: the reflected ray samples a procedural studio (a bright softbox
// overhead, a strip light at each side, a dark floor), tinted by the bust's colour, with a white
// fresnel edge; pearls are a soft milky white. Missed rays give the silhouette its antialiasing.
import { Skia } from '@shopify/react-native-skia';

const SKSL = `
uniform float2 c;      // centre, px
uniform float S;       // px per unit
uniform float yaw;
uniform float pitch;
uniform float3 tint;
uniform float t;

float smin(float a, float b, float k) {
  float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
  return mix(b, a, h) - k * h * (1.0 - h);
}
float ellip(float3 p, float3 r) {
  float k0 = length(p / r);
  float k1 = length(p / (r * r));
  return k0 * (k0 - 1.0) / max(k1, 1e-4);
}
float3 rotY(float3 v, float a) { float c0 = cos(a); float s0 = sin(a); return float3(c0 * v.x + s0 * v.z, v.y, -s0 * v.x + c0 * v.z); }
float3 rotX(float3 v, float a) { float c0 = cos(a); float s0 = sin(a); return float3(v.x, c0 * v.y - s0 * v.z, s0 * v.y + c0 * v.z); }

float2 scene(float3 p) {
  // the head turns and nods on the neck; the shoulders turn a little less
  float3 q = rotX(rotY(p - float3(0.0, 0.3, 0.0), yaw), pitch) + float3(0.0, 0.3, 0.0);
  float3 s = rotY(p, yaw * 0.35);
  float head = ellip(q - float3(0.0, 0.62, 0.0), float3(0.37, 0.48, 0.41));
  // a face: a nose, a chin, soft eye sockets
  head = smin(head, ellip(q - float3(0.0, 0.57, 0.38), float3(0.055, 0.1, 0.07)), 0.05);
  head = smin(head, ellip(q - float3(0.0, 0.32, 0.22), float3(0.16, 0.12, 0.14)), 0.1);
  float eyes = min(length(q - float3(-0.13, 0.64, 0.4)), length(q - float3(0.13, 0.64, 0.4))) - 0.06;
  head = -smin(-head, eyes, 0.08);
  float neck = length(float2(s.x, s.z)) - 0.15;
  neck = max(neck, max(s.y - 0.4, -s.y - 0.1));
  float sh = ellip(s - float3(0.0, -0.42, -0.02), float3(0.82, 0.42, 0.4));
  sh = max(sh, -(s.y + 0.62));
  float d = smin(head, neck, 0.12);
  d = smin(d, sh, 0.16);
  float2 res = float2(d, 0.0);
  // the tiara: sixteen pearls on a ring round the brow, tipped back a little
  float3 tq = rotX(q - float3(0.0, 0.86, -0.02), -0.22);
  float best = 9.0;
  for (int i = 0; i < 16; i++) {
    float a = float(i) / 16.0 * 6.2831;
    float fi = float(i);
    float3 pc = float3(cos(a) * 0.335, (mod(fi, 2.0) < 0.5 ? 0.05 : 0.0), sin(a) * 0.37);
    best = min(best, length(tq - pc) - (mod(fi, 2.0) < 0.5 ? 0.05 : 0.035));
  }
  if (best < res.x) res = float2(best, 1.0);
  return res;
}
float3 nrm(float3 p) {
  float e = 0.0015;
  return normalize(float3(
    scene(p + float3(e, 0, 0)).x - scene(p - float3(e, 0, 0)).x,
    scene(p + float3(0, e, 0)).x - scene(p - float3(0, e, 0)).x,
    scene(p + float3(0, 0, e)).x - scene(p - float3(0, 0, e)).x));
}
float3 studio(float3 r) {
  float e = 0.05;
  e += 1.5 * smoothstep(0.3, 0.85, r.y);
  e += 0.55 * smoothstep(0.1, 1.0, r.z) * (0.6 + 0.4 * r.y);
  e += 1.1 * exp(-pow((r.x - 0.8) * 4.0, 2.0)) * smoothstep(-0.6, 0.2, r.y + 0.3);
  e += 0.8 * exp(-pow((r.x + 0.85) * 4.5, 2.0));
  e += 0.3 * smoothstep(0.2, -0.6, r.y) * (0.5 + 0.5 * sin(r.x * 9.0 + t));
  return float3(e);
}

half4 main(float2 xy) {
  float2 uv = (xy - c) / S;
  uv.y = -uv.y;
  float3 ro = float3(0.0, 0.25, 4.0);
  float3 rd = normalize(float3(uv.x, uv.y, -3.4));
  float tt = 2.6;
  float dmin = 9.0;
  float id = -1.0;
  float3 p = ro;
  for (int i = 0; i < 56; i++) {
    p = ro + rd * tt;
    float2 h = scene(p);
    dmin = min(dmin, h.x);
    if (h.x < 0.0012) { id = h.y; break; }
    tt += h.x;
    if (tt > 6.0) break;
  }
  if (id < 0.0) {
    float a = 1.0 - smoothstep(0.0, 1.6 / S, dmin);
    if (a <= 0.0) return half4(0.0);
    return half4(half3(tint * 0.3) * a, a * 0.5);
  }
  float3 n = nrm(p);
  float3 rf = reflect(rd, n);
  float ndv = clamp(dot(n, -rd), 0.0, 1.0);
  float3 col;
  if (id < 0.5) {
    float3 env = studio(rf);
    col = tint * env * 1.05 + tint * 0.08;
    col = mix(col, float3(1.0), pow(1.0 - ndv, 3.0) * 0.55);
  } else {
    float dif = clamp(dot(n, normalize(float3(-0.4, 0.8, 0.5))), 0.0, 1.0);
    col = float3(0.95, 0.94, 0.98) * (0.45 + 0.6 * dif) + studio(rf) * 0.25;
  }
  col = float3(1.0) - exp(-col * 1.3);
  return half4(half3(col), 1.0);
}`;

export const bustFx = Skia.RuntimeEffect.Make(SKSL);
if (!bustFx && __DEV__) console.warn('Chrome Match Reveal: the bust shader did not compile');
